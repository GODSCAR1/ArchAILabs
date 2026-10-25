package com.codeja.master_acdc.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.SQL=debug"
    })
@Testcontainers
class OrderSummaryProfilingIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderSummaryProfilingIT.class);
  private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ListOrdersByStatusUseCase listOrdersByStatusUseCase;
  @Autowired EntityManagerFactory entityManagerFactory;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");
    jdbcTemplate.update("delete from customers");
  }

  @Test
  void should_measure_seven_order_summary_loads_when_listing_paid_orders() {
    assertMeasuredLoad(7);
  }

  @Test
  void should_measure_five_hundred_order_summary_loads_when_listing_paid_orders() {
    assertMeasuredLoad(500);
  }

  private void assertMeasuredLoad(int orderCount) {
    insertData(orderCount);
    listOrdersByStatusUseCase.list(OrderStatus.PAID);

    Statistics statistics = statistics();
    statistics.clear();
    long startedAt = System.nanoTime();
    var summaries = listOrdersByStatusUseCase.list(OrderStatus.PAID);
    long elapsedNanos = System.nanoTime() - startedAt;

    LOGGER
        .atInfo()
        .setMessage(
            "Order summary profiling: N={}, preparedStatements={}, entityLoads={},"
                + " collectionFetches={}, timeMs={}")
        .addArgument(orderCount)
        .addArgument(statistics.getPrepareStatementCount())
        .addArgument(statistics.getEntityLoadCount())
        .addArgument(statistics.getCollectionFetchCount())
        .addArgument(elapsedNanos / 1_000_000.0)
        .log();
    assertThat(summaries).hasSize(orderCount);
  }

  private void insertData(int orderCount) {
    List<Object[]> customers =
        java.util.stream.IntStream.range(0, orderCount)
            .mapToObj(index -> new Object[] {idFor(index), "Customer " + index})
            .toList();
    jdbcTemplate.batchUpdate("insert into customers (id, name) values (?, ?)", customers);

    List<Object[]> orders =
        java.util.stream.IntStream.range(0, orderCount)
            .mapToObj(index -> new Object[] {idFor(index + orderCount), idFor(index), "PAID"})
            .toList();
    jdbcTemplate.batchUpdate(
        "insert into orders (id, customer_id, status) values (?, ?, ?)", orders);

    List<Object[]> lines =
        java.util.stream.IntStream.range(0, orderCount)
            .boxed()
            .flatMap(
                index ->
                    java.util.stream.Stream.of(
                        new Object[] {idFor(index + orderCount), PRODUCT_A, 1},
                        new Object[] {idFor(index + orderCount), PRODUCT_B, 2}))
            .toList();
    jdbcTemplate.batchUpdate(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)", lines);
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private static UUID idFor(int index) {
    return UUID.nameUUIDFromBytes(("order-summary-profiling-" + index).getBytes());
  }
}
