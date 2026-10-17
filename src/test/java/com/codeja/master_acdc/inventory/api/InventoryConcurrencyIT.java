package com.codeja.master_acdc.inventory.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class InventoryConcurrencyIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final String A_TEXT = "11111111-1111-1111-1111-111111111111";

  @Autowired InventoryFacade facade;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @Sql(
      statements = {
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 1)"
      })
  void should_allow_exactly_one_reservation_when_last_unit_contended() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<ReservationResult> attempt =
        () -> {
          start.await();
          return facade.reserve(List.of(line(A_TEXT, 1)));
        };

    try {
      Future<ReservationResult> first = executor.submit(attempt);
      Future<ReservationResult> second = executor.submit(attempt);
      start.countDown();

      List<ReservationResult> results = List.of(first.get(30, SECONDS), second.get(30, SECONDS));

      assertThat(results)
          .containsExactlyInAnyOrder(
              ReservationResult.RESERVED, ReservationResult.INSUFFICIENT_STOCK);
      assertThat(availableOf(A_TEXT)).isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  private static StockLine line(String productId, int quantity) {
    return new StockLine(UUID.fromString(productId), quantity);
  }

  private int availableOf(String productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?",
        Integer.class,
        UUID.fromString(productId));
  }
}
