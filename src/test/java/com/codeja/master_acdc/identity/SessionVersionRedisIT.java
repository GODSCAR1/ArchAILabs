package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.port.SessionVersions;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

@org.springframework.test.context.TestPropertySource(properties = "catalog.cache.enabled=false")
class SessionVersionRedisIT extends SessionIntegrationSupport {
  @Autowired SessionVersions versions;
  @Autowired RedisSessionVersions barrier;
  @Autowired StringRedisTemplate redis;
  @Autowired org.testcontainers.containers.GenericContainer<?> identityRedis;

  @Test
  void should_reconstruct_from_committed_database_when_redis_is_replaced_empty() {
    var user = registered();
    change.changePassword(
        new com.codeja.master_acdc.identity.domain.model.PasswordChange(
            user.id(), PASSWORD, NEW_PASSWORD));
    try (var empty =
        new org.testcontainers.containers.GenericContainer<>(
                org.testcontainers.utility.DockerImageName.parse("redis:7"))
            .withExposedPorts(6379)) {
      empty.start();
      var factory =
          new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
              empty.getHost(), empty.getMappedPort(6379));
      factory.afterPropertiesSet();
      try {
        var store = new RedisSessionVersions(new StringRedisTemplate(factory));
        assertThat(store.find(user.id())).isEmpty();
        var restored =
            new com.codeja.master_acdc.identity.infrastructure.config.LockedSessionVersions(
                store, accounts, transactions);
        assertThat(restored.current(user.id())).isEqualTo(new SessionVersion(1));
        assertThat(store.find(user.id())).contains(new SessionVersion(1));
      } finally {
        factory.destroy();
      }
    }
  }

  @Test
  void should_keep_bounded_redis_commands_when_catalog_cache_is_disabled() {
    var factory =
        (org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
            redis.getConnectionFactory();
    assertThat(factory.getClientConfiguration().getCommandTimeout())
        .isEqualTo(java.time.Duration.ofMillis(200));
    assertThat(
            factory
                .getClientConfiguration()
                .getClientOptions()
                .orElseThrow()
                .getDisconnectedBehavior())
        .isEqualTo(io.lettuce.core.ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
  }

  @Test
  @DisplayName("Fallo al poblar clave no autentica")
  void should_reject_reconstruction_when_redis_write_times_out() throws Exception {
    var user = registered();
    pause("WRITE");
    try {
      assertThatThrownBy(() -> versions.current(user.id()))
          .hasMessage("session-version-unavailable");
    } finally {
      unpause();
    }
  }

  private void pause(String mode) {
    redis.execute(
        (org.springframework.data.redis.core.RedisCallback<Object>)
            connection ->
                connection.execute(
                    "CLIENT",
                    "PAUSE".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "2000".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    mode.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private void unpause() throws java.io.IOException, InterruptedException {
    // A paused write blocks its connection's queue; recovery needs an independent connection.
    assertThat(identityRedis.execInContainer("redis-cli", "CLIENT", "UNPAUSE").getExitCode())
        .isZero();
  }

  @Test
  @DisplayName("Clave ausente se reconstruye sin resucitar sesiones")
  void should_rebuild_committed_version_when_namespace_is_empty() {
    var user = registered();
    jdbc.update("update user_account set session_version=7 where id=?", user.id());
    String key = RedisSessionVersions.PREFIX + user.id();
    assertThat(versions.current(user.id())).isEqualTo(new SessionVersion(7));
    assertThat(redis.getExpire(key)).isEqualTo(-1L);
    redis.delete(key);
    assertThat(versions.current(user.id())).isEqualTo(new SessionVersion(7));
    assertThat(versions.current(user.id())).isEqualTo(new SessionVersion(7));
    assertThatThrownBy(() -> versions.current(UUID.randomUUID()))
        .hasMessage("session-account-absent");
  }

  @Test
  @DisplayName("Valor compartido corrupto no autentica")
  void should_reject_corruption_when_reading_or_advancing_version() {
    var user = registered();
    String key = RedisSessionVersions.PREFIX + user.id();
    for (String invalid : new String[] {"-1", "01", "1.5", "9223372036854775808", "text"}) {
      redis.opsForValue().set(key, invalid);
      assertThatThrownBy(() -> versions.current(user.id())).hasMessage("session-version-corrupt");
      assertThatThrownBy(() -> barrier.publish(user.id(), new SessionVersion(2)))
          .hasMessage("session-version-unavailable");
      assertThat(redis.opsForValue().get(key)).isEqualTo(invalid);
    }
  }

  @Test
  void should_preserve_maximum_exactly_when_writes_arrive_late() {
    UUID id = UUID.randomUUID();
    barrier.publish(id, new SessionVersion(0));
    barrier.publish(id, new SessionVersion(0));
    barrier.publish(id, new SessionVersion(2));
    barrier.publish(id, new SessionVersion(0));
    barrier.publish(id, new SessionVersion(1));
    assertThat(barrier.populate(id, new SessionVersion(0))).isEqualTo(new SessionVersion(2));
    barrier.publish(id, new SessionVersion(Long.MAX_VALUE));
    barrier.publish(id, new SessionVersion(Long.MAX_VALUE - 1));
    assertThat(barrier.find(id)).contains(new SessionVersion(Long.MAX_VALUE));
    assertThat(redis.getExpire(RedisSessionVersions.PREFIX + id)).isEqualTo(-1L);
  }

  @Test
  @DisplayName("Carga concurrente no sobrescribe la revocación")
  void should_recheck_shared_barrier_when_miss_waits_for_account() throws Exception {
    var user = registered();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var writer =
          executor.submit(
              () ->
                  transactions.execute(
                      () -> {
                        accounts.findForUpdate(user.id()).orElseThrow();
                        locked.countDown();
                        await(release);
                        jdbc.update(
                            "update user_account set session_version=1 where id=?", user.id());
                        barrier.publish(user.id(), new SessionVersion(1));
                        return null;
                      }));
      await(locked);
      var reader = executor.submit(() -> versions.current(user.id()));
      try {
        awaitAccountWaiter();
      } finally {
        release.countDown();
      }
      writer.get(10, TimeUnit.SECONDS);
      assertThat(reader.get(10, TimeUnit.SECONDS)).isEqualTo(new SessionVersion(1));
    }
  }
}
