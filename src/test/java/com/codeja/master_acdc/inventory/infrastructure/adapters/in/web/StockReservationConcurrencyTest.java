package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StockReservationConcurrencyTest {

  private static final String STOCK_BUSY_TYPE = "https://api.master-acdc.dev/problems/stock-busy";
  private static final String INSUFFICIENT_STOCK_TYPE =
      "https://api.master-acdc.dev/problems/insufficient-stock";
  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired DataSource dataSource;
  @Autowired AccessTokenIssuer accessTokenIssuer;

  // Lab S12: la reserva HTTP exige JWT. Bearer real firmado por la misma clave que valida la API
  // (HttpClient contra RANDOM_PORT no admite jwt() de MockMvc). Las cantidades no cambian.
  private String bearer;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", PRODUCT_ID, 30);
    bearer =
        accessTokenIssuer
            .issue(
                new UserAccount(
                    UUID.randomUUID(),
                    "stock-guardian@example.com",
                    "{noop}unused",
                    new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, null)))
            .value();
  }

  @Test
  @DisplayName("Reservas concurrentes en venta flash")
  void should_reserve_exactly_available_units_when_fifty_http_requests_compete() throws Exception {
    List<HttpResponse<String>> responses = concurrentReservations(50);

    assertThat(responses).filteredOn(response -> response.statusCode() == 201).hasSize(30);
    assertThat(responses).filteredOn(response -> response.statusCode() == 503).isEmpty();
    assertThat(responses)
        .filteredOn(response -> response.statusCode() == 409)
        .hasSize(20)
        .allMatch(response -> response.body().contains(INSUFFICIENT_STOCK_TYPE));
    assertThat(available()).isZero();
  }

  @Test
  @DisplayName("Stock ocupado")
  void should_return_stock_busy_when_http_reservation_cannot_acquire_lock() throws Exception {
    CountDownLatch lockAcquired = new CountDownLatch(1);
    CountDownLatch releaseLock = new CountDownLatch(1);
    ExecutorService holderExecutor = Executors.newSingleThreadExecutor();
    Future<?> holder = holderExecutor.submit(() -> holdLock(lockAcquired, releaseLock));
    assertThat(lockAcquired.await(10, SECONDS)).isTrue();

    long started = System.nanoTime();
    try {
      HttpResponse<String> response = sendReservation(1);
      long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(response.headers().firstValue("Retry-After")).contains("1");
      assertThat(response.body()).contains(STOCK_BUSY_TYPE, "\"title\":\"Stock busy\"");
      assertThat(elapsedMillis).isBetween(1_500L, 3_500L);
      assertThat(available()).isEqualTo(30);
    } finally {
      releaseLock.countDown();
      holder.get(10, SECONDS);
      holderExecutor.shutdownNow();
    }
  }

  private List<HttpResponse<String>> concurrentReservations(int requests) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(requests);
    Callable<HttpResponse<String>> attempt =
        () -> {
          if (!start.await(10, SECONDS)) {
            throw new IllegalStateException("workers did not start");
          }
          return sendReservation(1);
        };
    try {
      List<Future<HttpResponse<String>>> futures =
          java.util.stream.IntStream.range(0, requests)
              .mapToObj(index -> executor.submit(attempt))
              .toList();
      start.countDown();
      return futures.stream().map(future -> get(future, 30)).toList();
    } finally {
      executor.shutdownNow();
    }
  }

  private HttpResponse<String> sendReservation(int units) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://localhost:" + port + "/api/products/" + PRODUCT_ID + "/reservations"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + bearer)
            .POST(HttpRequest.BodyPublishers.ofString("{\"units\":" + units + "}"))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static <T> T get(Future<T> future, int timeoutSeconds) {
    try {
      return future.get(timeoutSeconds, SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("worker was interrupted", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new IllegalStateException("worker did not finish", exception);
    }
  }

  private void holdLock(CountDownLatch lockAcquired, CountDownLatch releaseLock) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select product_id from stock where product_id = ? for update")) {
      connection.setAutoCommit(false);
      statement.setObject(1, PRODUCT_ID);
      statement.executeQuery().close();
      lockAcquired.countDown();
      if (!releaseLock.await(10, SECONDS)) {
        throw new IllegalStateException("lock release was not requested");
      }
      connection.rollback();
    } catch (SQLException | InterruptedException exception) {
      throw new IllegalStateException("could not hold stock lock", exception);
    }
  }

  private int available() {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?", Integer.class, PRODUCT_ID);
  }
}
