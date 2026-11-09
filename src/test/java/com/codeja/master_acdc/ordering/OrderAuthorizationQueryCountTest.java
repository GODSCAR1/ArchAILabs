package com.codeja.master_acdc.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.IdentityIntegrationSupport;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class OrderAuthorizationQueryCountTest extends IdentityIntegrationSupport {
  private static final int ORDER_COUNT = 120;
  private static final int OWN_COUNT = 60;
  private final UUID owner = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;
  @Autowired private AccessTokenIssuer issuer;
  @Autowired private EntityManager entityManager;
  @Autowired private EntityManagerFactory factory;

  @BeforeEach
  void seed_more_orders_than_batch_size() {
    jdbc.update("delete from order_lines");
    jdbc.update("delete from orders");
    jdbc.update("insert into customers(id,name) values (?, 'Owner'), (?, 'Other')", owner, other);
    List<Object[]> orders =
        IntStream.range(0, ORDER_COUNT)
            .mapToObj(
                index -> new Object[] {idFor(index), index < OWN_COUNT ? owner : other, "PAID"})
            .toList();
    List<Object[]> lines =
        IntStream.range(0, ORDER_COUNT)
            .mapToObj(index -> new Object[] {idFor(index), idFor(ORDER_COUNT), 3})
            .toList();
    jdbc.batchUpdate("insert into orders(id,customer_id,status) values (?,?,?)", orders);
    jdbc.batchUpdate("insert into order_lines(order_id,product_id,quantity) values (?,?,?)", lines);
  }

  @ParameterizedTest(name = "{0}: PK de cuenta + proyección")
  @ValueSource(strings = {"CUSTOMER", "SUPPORT"})
  void should_use_at_most_two_statements_when_http_lists_120_own_and_foreign_orders(String role)
      throws Exception {
    boolean own = role.equals("CUSTOMER");
    String token = OrderBearer.issue(jdbc, issuer, own ? owner : null, role);
    Statistics statistics = clearStatistics();
    mvc.perform(
            get("/api/orders").param("status", "PAID").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(own ? OWN_COUNT : ORDER_COUNT))
        .andExpect(jsonPath("$[0].lineCount").value(1))
        .andExpect(jsonPath("$[0].totalUnits").value(3));
    assertThat(statistics.getPrepareStatementCount())
        .as("PK de cuenta y proyección; coste independiente de filas")
        .isLessThanOrEqualTo(2);
    assertThat(statistics.getEntityLoadCount()).isZero();
  }

  @Test
  void should_skip_identity_and_orders_when_http_actor_lacks_read_permission() throws Exception {
    String token = OrderBearer.issue(jdbc, issuer, owner, "CATALOG_MANAGER");
    Statistics statistics = clearStatistics();
    mvc.perform(
            get("/api/orders")
                .param("status", "invalid")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("https://api.master-acdc.dev/problems/forbidden"));
    assertThat(statistics.getPrepareStatementCount()).isZero();
  }

  @Test
  void should_only_resolve_account_when_customer_has_no_association() throws Exception {
    String token = OrderBearer.issue(jdbc, issuer, null, "CUSTOMER");
    Statistics statistics = clearStatistics();
    mvc.perform(
            get("/api/orders").param("status", "PAID").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  private Statistics clearStatistics() {
    entityManager.flush();
    entityManager.clear();
    Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    return statistics;
  }

  private UUID idFor(int index) {
    return UUID.nameUUIDFromBytes(("order-auth-count-" + index).getBytes(StandardCharsets.UTF_8));
  }
}
