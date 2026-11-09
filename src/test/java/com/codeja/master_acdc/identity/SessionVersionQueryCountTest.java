package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(
    properties = {
      "catalog.cache.enabled=false",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.codeja.master_acdc.identity.SessionVersionQueryCountTest$SqlCapture"
    })
class SessionVersionQueryCountTest extends IdentityIntegrationSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired AccessTokenIssuer issuer;
  @Autowired JwtDecoder decoder;
  @Autowired MockMvc mvc;
  @Autowired StringRedisTemplate redis;
  @Autowired EntityManager entityManager;
  @Autowired EntityManagerFactory factory;

  @Test
  @DisplayName("Clave presente evita consultas repetidas de versión")
  void should_issue_one_real_get_and_no_version_select_when_authenticating_repeatedly()
      throws Exception {
    UUID owner = UUID.randomUUID();
    String token = OrderBearer.issue(jdbc, issuer, owner, "CUSTOMER");
    jdbc.batchUpdate(
        "insert into orders(id,customer_id,status) values (?,?,'PAID')",
        IntStream.range(0, 120)
            .mapToObj(index -> new Object[] {UUID.randomUUID(), owner})
            .toList());
    var before = jdbc.queryForList("select * from orders where customer_id=? order by id", owner);
    try (var counter =
        new SessionVersionCommandCounter((LettuceConnectionFactory) redis.getConnectionFactory())) {
      decoder.decode(token);
      counter.reset();
      for (int request = 0; request < 8; request++) {
        clearStatistics();
        mvc.perform(
                get("/api/orders")
                    .param("status", "PAID")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(120));
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(SqlCapture.SQL.get()).noneMatch(sql -> sql.contains("session_version"));
      }
      assertThat(counter.gets()).isEqualTo(8);
      assertThat(counter.writes()).isZero();
    }
    assertThat(jdbc.queryForList("select * from orders where customer_id=? order by id", owner))
        .isEqualTo(before);
  }

  @Test
  void should_load_origin_once_when_key_is_absent_then_use_only_shared_reads() {
    String token = OrderBearer.issue(jdbc, issuer, null, "SUPPORT");
    try (var counter =
        new SessionVersionCommandCounter((LettuceConnectionFactory) redis.getConnectionFactory())) {
      clearStatistics();
      decoder.decode(token);
      // Existing account locking refreshes the entity after the locked SELECT for login races.
      assertThat(SqlCapture.SQL.get().stream().filter(sql -> sql.contains("session_version")))
          .hasSize(2);
      assertThat(SqlCapture.SQL.get().stream().filter(sql -> sql.contains("for no key update")))
          .hasSize(1);
      assertThat(counter.gets()).isEqualTo(2);
      assertThat(counter.writes()).isEqualTo(1);
      counter.reset();
      clearStatistics();
      for (int request = 0; request < 8; request++) {
        decoder.decode(token);
      }
      assertThat(counter.gets()).isEqualTo(8);
      assertThat(counter.writes()).isZero();
      assertThat(SqlCapture.SQL.get()).isEmpty();
      assertThat(factory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount())
          .isZero();
    }
  }

  private void clearStatistics() {
    entityManager.flush();
    entityManager.clear();
    factory.unwrap(SessionFactory.class).getStatistics().clear();
    SqlCapture.SQL.get().clear();
  }

  public static class SqlCapture implements StatementInspector {
    static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String inspect(String sql) {
      SQL.get().add(sql);
      return sql;
    }
  }
}
