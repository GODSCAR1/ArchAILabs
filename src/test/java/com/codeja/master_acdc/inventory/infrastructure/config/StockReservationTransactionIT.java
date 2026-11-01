package com.codeja.master_acdc.inventory.infrastructure.config;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codeja.master_acdc.inventory.application.port.ReserveProductStockUseCase;
import com.codeja.master_acdc.inventory.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.inventory.domain.exception.StockBusyException;
import com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException;
import com.codeja.master_acdc.inventory.domain.model.ProductReservation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class StockReservationTransactionIT {

  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ReserveProductStockUseCase useCase;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired DataSource dataSource;

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", PRODUCT_ID, 10);
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void should_commit_reservation_when_stock_is_available() {
    ProductReservation result = useCase.reserve(PRODUCT_ID, 3);

    assertThat(result.available()).isEqualTo(7);
    assertThat(available()).isEqualTo(7);
  }

  @Test
  void should_rollback_when_stock_is_insufficient() {
    assertThatThrownBy(() -> useCase.reserve(PRODUCT_ID, 11))
        .isInstanceOf(InsufficientStockException.class);

    assertThat(available()).isEqualTo(10);
  }

  @Test
  void should_throw_stock_not_found_when_record_is_missing() {
    jdbcTemplate.update("delete from stock where product_id = ?", PRODUCT_ID);

    assertThatThrownBy(() -> useCase.reserve(PRODUCT_ID, 1))
        .isInstanceOf(StockRecordNotFoundException.class);
  }

  @Test
  void should_translate_lock_timeout_when_database_rejects_lock() throws Exception {
    CountDownLatch lockAcquired = new CountDownLatch(1);
    CountDownLatch releaseLock = new CountDownLatch(1);
    Future<?> holder =
        executor.submit(
            () -> {
              holdLock(lockAcquired, releaseLock);
              return null;
            });
    assertThat(lockAcquired.await(10, SECONDS)).isTrue();

    long started = System.nanoTime();
    try {
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () ->
              assertThatThrownBy(() -> useCase.reserve(PRODUCT_ID, 1))
                  .isInstanceOf(StockBusyException.class));
    } finally {
      releaseLock.countDown();
      holder.get(10, SECONDS);
    }

    long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
    assertThat(elapsedMillis).isBetween(1_500L, 3_500L);
    assertThat(available()).isEqualTo(10);
  }

  private void holdLock(CountDownLatch lockAcquired, CountDownLatch releaseLock)
      throws SQLException, InterruptedException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select product_id from stock where product_id = ? for update")) {
      connection.setAutoCommit(false);
      statement.setObject(1, PRODUCT_ID);
      statement.executeQuery();
      lockAcquired.countDown();
      releaseLock.await(10, SECONDS);
      connection.rollback();
    }
  }

  private int available() {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?", Integer.class, PRODUCT_ID);
  }
}
