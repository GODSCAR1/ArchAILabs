package com.codeja.master_acdc.inventory.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.inventory.application.port.ReserveProductStockUseCase;
import com.codeja.master_acdc.inventory.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest
@Testcontainers
class InventoryReservationComparisonIT {

  private static final Logger LOG = LoggerFactory.getLogger(InventoryReservationComparisonIT.class);
  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final StockLine LINE = new StockLine(PRODUCT_ID, 1);

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ReserveProductStockUseCase reserveProductStockUseCase;
  @Autowired InventoryFacade inventoryFacade;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Comparación de reserva pesimista y UPDATE atómico")
  void should_compare_both_reservation_routes_when_the_same_concurrent_load_is_used()
      throws Exception {
    List<Long> pessimistic = measure(this::reservePessimistically);
    List<Long> atomic = measure(this::reserveAtomically);

    logMeasurements("pesimista", pessimistic);
    logMeasurements("UPDATE atómico", atomic);
  }

  private List<Long> measure(Callable<Outcome> route) throws Exception {
    assertRound(runRound(route));
    List<Long> measurements = new ArrayList<>();
    for (int repetition = 0; repetition < 5; repetition++) {
      Round round = runRound(route);
      assertRound(round);
      measurements.add(round.elapsedMillis());
    }
    return measurements;
  }

  private Round runRound(Callable<Outcome> route)
      throws InterruptedException, ExecutionException, TimeoutException {
    resetStock();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(50);
    long started = System.nanoTime();
    try {
      List<Future<Outcome>> futures =
          java.util.stream.IntStream.range(0, 50)
              .mapToObj(index -> executor.submit(awaitStart(start, route)))
              .toList();
      start.countDown();
      List<Outcome> outcomes = new ArrayList<>();
      for (Future<Outcome> future : futures) {
        outcomes.add(future.get(30, SECONDS));
      }
      long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
      return new Round(outcomes, elapsedMillis);
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<Outcome> awaitStart(CountDownLatch start, Callable<Outcome> route) {
    return () -> {
      if (!start.await(10, SECONDS)) {
        throw new IllegalStateException("workers did not start");
      }
      return route.call();
    };
  }

  private Outcome reservePessimistically() {
    try {
      reserveProductStockUseCase.reserve(PRODUCT_ID, 1);
      return Outcome.RESERVED;
    } catch (InsufficientStockException exception) {
      return Outcome.INSUFFICIENT;
    }
  }

  private Outcome reserveAtomically() {
    return inventoryFacade.reserve(List.of(LINE)) == ReservationResult.RESERVED
        ? Outcome.RESERVED
        : Outcome.INSUFFICIENT;
  }

  private void assertRound(Round round) {
    assertThat(round.outcomes()).filteredOn(outcome -> outcome == Outcome.RESERVED).hasSize(30);
    assertThat(round.outcomes()).filteredOn(outcome -> outcome == Outcome.INSUFFICIENT).hasSize(20);
    assertThat(available()).isZero();
  }

  private void logMeasurements(String route, List<Long> measurements) {
    List<Long> sorted = measurements.stream().sorted().toList();
    LOG.info(
        "ADR 0007 {}: mediana={} ms, mínimo={} ms, máximo={} ms, mediciones={} ",
        route,
        sorted.get(2),
        sorted.getFirst(),
        sorted.getLast(),
        measurements);
  }

  private void resetStock() {
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", PRODUCT_ID, 30);
  }

  private int available() {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?", Integer.class, PRODUCT_ID);
  }

  private enum Outcome {
    RESERVED,
    INSUFFICIENT
  }

  private record Round(List<Outcome> outcomes, long elapsedMillis) {}
}
