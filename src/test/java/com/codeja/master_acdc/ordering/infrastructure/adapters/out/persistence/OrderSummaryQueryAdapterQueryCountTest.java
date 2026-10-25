package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    showSql = false,
    properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderSummaryQueryAdapter.class)
@Testcontainers
class OrderSummaryQueryAdapterQueryCountTest {

  private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final int ORDER_COUNT = 120;

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired OrderSummaryQueryAdapter adapter;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");
    jdbcTemplate.update("delete from customers");
  }

  @Test
  void should_use_at_most_two_queries_when_listing_one_hundred_twenty_paid_orders() {
    insertData();
    entityManager.flush();
    entityManager.clear();
    Statistics statistics = statistics();
    statistics.clear();

    var summaries = adapter.findByStatus(OrderStatus.PAID);

    assertThat(summaries).hasSize(ORDER_COUNT);
    assertThat(statistics.getPrepareStatementCount())
        .as("findByStatus() no debe depender del número de pedidos; ¿hay un N+1?")
        .isLessThanOrEqualTo(2);
  }

  private void insertData() {
    List<Object[]> customers =
        java.util.stream.IntStream.range(0, ORDER_COUNT)
            .mapToObj(index -> new Object[] {idFor(index), "Customer " + index})
            .toList();
    jdbcTemplate.batchUpdate("insert into customers (id, name) values (?, ?)", customers);

    List<Object[]> orders =
        java.util.stream.IntStream.range(0, ORDER_COUNT)
            .mapToObj(index -> new Object[] {idFor(index + ORDER_COUNT), idFor(index), "PAID"})
            .toList();
    jdbcTemplate.batchUpdate(
        "insert into orders (id, customer_id, status) values (?, ?, ?)", orders);

    List<Object[]> lines =
        java.util.stream.IntStream.range(0, ORDER_COUNT)
            .boxed()
            .flatMap(
                index ->
                    java.util.stream.Stream.of(
                        new Object[] {idFor(index + ORDER_COUNT), PRODUCT_A, 1},
                        new Object[] {idFor(index + ORDER_COUNT), PRODUCT_B, 2}))
            .toList();
    jdbcTemplate.batchUpdate(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)", lines);
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private static UUID idFor(int index) {
    return UUID.nameUUIDFromBytes(("query-count-" + index).getBytes());
  }
}
