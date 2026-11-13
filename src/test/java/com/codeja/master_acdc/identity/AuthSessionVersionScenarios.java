package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.SessionVersionValidator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;

abstract class AuthSessionVersionScenarios extends AuthHttpConcurrencyScenarios {
  @Autowired StringRedisTemplate redis;
  @Autowired GenericContainer<?> identityRedis;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  RedisSessionVersions sharedVersions;

  @Test
  @DisplayName("Fallo compartido durante escritura impide confirmar contraseña")
  void should_return_retryable_problem_when_barrier_write_fails_after_authentication()
      throws Exception {
    var browser = session();
    decoder.decode(browser.access());
    var before = persistentState();
    org.mockito.Mockito.doThrow(
            new com.codeja.master_acdc.identity.domain.model.IdentityException(
                "session-version-unavailable"))
        .when(sharedVersions)
        .publish(
            org.mockito.ArgumentMatchers.eq(browser.user()), org.mockito.ArgumentMatchers.any());
    problem(password(browser, PASSWORD, "Changed!12345X"), 503, "session-version-unavailable")
        .andExpect(header().string("Retry-After", "1"));
    assertThat(persistentState()).isEqualTo(before);
    mvc.perform(orders(browser.access())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Rollback posterior a la barrera no restaura sesiones")
  void should_reject_old_bearer_when_remote_write_succeeds_but_confirmation_is_lost()
      throws Exception {
    var browser = session();
    decoder.decode(browser.access());
    var before = persistentState();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.callRealMethod();
              var factory =
                  (org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
                      redis.getConnectionFactory();
              factory.resetConnection();
              throw new com.codeja.master_acdc.identity.domain.model.IdentityException(
                  "session-version-unavailable");
            })
        .when(sharedVersions)
        .publish(
            org.mockito.ArgumentMatchers.eq(browser.user()), org.mockito.ArgumentMatchers.any());
    problem(password(browser, PASSWORD, "Changed!12345X"), 503, "session-version-unavailable")
        .andExpect(header().string("Retry-After", "1"));
    assertThat(persistentState()).isEqualTo(before);
    assertRejected(browser.access());
  }

  @Test
  @DisplayName("Token sin versión no autentica")
  void should_reject_missing_version_when_other_claims_are_valid() throws Exception {
    var overrides = new java.util.HashMap<String, Object>();
    overrides.put("sv", null);
    assertRejected(signedToken(overrides, key.getKeyID(), key, JWSAlgorithm.RS256));
  }

  @Test
  @DisplayName("Versión no numérica no autentica")
  void should_reject_malformed_version_when_signed_by_real_key() throws Exception {
    for (Object value :
        List.of(
            "0",
            "text",
            true,
            Map.of("v", 0),
            List.of(0),
            -1,
            new java.math.BigDecimal("0.5"),
            new java.math.BigInteger("9223372036854775808"),
            new java.math.BigDecimal("1e-400"))) {
      assertRejected(signedToken(Map.of("sv", value), key.getKeyID(), key, JWSAlgorithm.RS256));
    }
  }

  @Test
  @DisplayName("Versión distinta o cuenta inexistente no autentica")
  void should_reject_mismatch_or_missing_account_when_other_claims_are_valid() throws Exception {
    String token = signedToken(Map.of(), key.getKeyID(), key, JWSAlgorithm.RS256);
    UUID id = UUID.fromString(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
    jdbc.update("update user_account set session_version=1 where id=?", id);
    assertRejected(token);
    assertRejected(signedToken(Map.of("sv", 1), key.getKeyID(), key, JWSAlgorithm.RS256));
    assertRejected(
        signedToken(
            Map.of("sub", UUID.randomUUID().toString()), key.getKeyID(), key, JWSAlgorithm.RS256));
  }

  @Test
  @DisplayName("Login posterior usa la nueva versión")
  void should_issue_usable_new_version_when_login_and_refresh_follow_password_change()
      throws Exception {
    var browser = session();
    String email =
        jdbc.queryForObject(
            "select email from user_account where id=?", String.class, browser.user());
    mvc.perform(password(browser, PASSWORD, "Changed!12345X")).andExpect(status().isNoContent());
    assertRejected(browser.access());
    var next =
        browser(
            browser.user(), login(email, "Changed!12345X").andExpect(status().isOk()).andReturn());
    for (Browser current : List.of(next, rotate(next))) {
      var jwt = decoder.decode(current.access());
      assertThat(jwt.getClaims())
          .containsOnlyKeys("iss", "sub", "aud", "iat", "exp", "jti", "roles", "sv");
      assertThat(((Number) jwt.getClaims().get("sv")).longValue()).isEqualTo(1);
      assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond())
          .isEqualTo(900);
      mvc.perform(orders(current.access())).andExpect(status().isOk());
    }
    long families = count("refresh_token");
    login(email, PASSWORD).andExpect(status().isUnauthorized());
    assertThat(count("refresh_token")).isEqualTo(families);
  }

  @Test
  @DisplayName("Almacén compartido caído rechaza y registra")
  void should_reject_and_log_when_redis_read_times_out_or_connection_is_closed() throws Exception {
    var browser = session();
    decoder.decode(browser.access());
    withWarning(
        browser,
        () -> {
          pauseRedis("ALL");
          try {
            assertRejected(browser.access());
          } finally {
            unpauseRedis();
          }
        });
    var client =
        org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
            .commandTimeout(java.time.Duration.ofMillis(200))
            .build();
    var factory =
        new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
            new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                java.net.InetAddress.getLoopbackAddress().getHostAddress(), closedPort()),
            client);
    factory.afterPropertiesSet();
    try {
      var disconnected = new RedisSessionVersions(new StringRedisTemplate(factory));
      org.mockito.Mockito.doAnswer(invocation -> disconnected.find(browser.user()))
          .when(sharedVersions)
          .find(browser.user());
      withWarning(browser, () -> assertRejected(browser.access()));
    } finally {
      org.mockito.Mockito.reset(sharedVersions);
      factory.destroy();
    }
    mvc.perform(orders(browser.access())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Fallo al poblar clave no autentica")
  void should_reject_and_log_when_reconstruction_cannot_write() throws Exception {
    var browser = session();
    withWarning(
        browser,
        () -> {
          pauseRedis("WRITE");
          try {
            assertRejected(browser.access());
          } finally {
            unpauseRedis();
          }
        });
    mvc.perform(orders(browser.access())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Valor compartido corrupto no autentica")
  void should_reject_and_log_when_shared_value_is_corrupt() throws Exception {
    var browser = session();
    String versionKey = RedisSessionVersions.PREFIX + browser.user();
    redis.opsForValue().set(versionKey, "corrupt");
    try {
      withWarning(browser, () -> assertRejected(browser.access()));
    } finally {
      redis.delete(versionKey);
    }
    mvc.perform(orders(browser.access())).andExpect(status().isOk());
  }

  private void withWarning(Browser browser, CheckedAction action) throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(SessionVersionValidator.class);
    Level previous = logger.getLevel();
    var capture = new ListAppender<ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    logger.setLevel(Level.WARN);
    try {
      action.run();
      assertThat(capture.list)
          .isNotEmpty()
          .allSatisfy(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                    .doesNotContain(
                        browser.access(),
                        browser.refresh().getValue(),
                        PASSWORD,
                        "@",
                        browser.user().toString(),
                        RedisSessionVersions.PREFIX);
                assertThat(event.getThrowableProxy()).isNull();
              });
    } finally {
      logger.detachAppender(capture);
      logger.setLevel(previous);
      capture.stop();
    }
  }

  private void assertRejected(String token) throws Exception {
    var before = persistentState();
    problem(orders(token), 401, "unauthorized")
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    assertThat(persistentState()).isEqualTo(before);
  }

  private List<List<Map<String, Object>>> persistentState() {
    return List.of(
        jdbc.queryForList(
            "select id,email,password_hash,roles::text as roles,enabled,session_version,customer_id"
                + " from user_account order by id"),
        jdbc.queryForList("select * from refresh_token order by id"),
        jdbc.queryForList("select * from orders order by id"),
        jdbc.queryForList("select * from stock order by product_id"));
  }

  private MockHttpServletRequestBuilder orders(String token) {
    return get("/api/orders")
        .secure(true)
        .param("status", "PAID")
        .header("Authorization", "Bearer " + token);
  }

  private void pauseRedis(String mode) {
    redis.execute(
        (RedisCallback<Object>)
            connection ->
                connection.execute(
                    "CLIENT",
                    "PAUSE".getBytes(StandardCharsets.UTF_8),
                    "2000".getBytes(StandardCharsets.UTF_8),
                    mode.getBytes(StandardCharsets.UTF_8)));
  }

  private void unpauseRedis() throws java.io.IOException, InterruptedException {
    assertThat(identityRedis.execInContainer("redis-cli", "CLIENT", "UNPAUSE").getExitCode())
        .isZero();
  }

  @FunctionalInterface
  private interface CheckedAction {
    void run() throws Exception;
  }

  private static int closedPort() throws java.io.IOException {
    try (var socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
