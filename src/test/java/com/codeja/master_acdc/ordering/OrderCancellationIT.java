package com.codeja.master_acdc.ordering;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderCancellationIT {

  private static final String TYPE_BASE = "https://api.master-acdc.dev/problems/";
  private static final String CUSTOMER = "550e8400-e29b-41d4-a716-446655440000";
  private static final String OTHER_CUSTOMER = "660e8400-e29b-41d4-a716-446655440000";
  private static final String A = "11111111-1111-1111-1111-111111111111";
  private static final String B = "22222222-2222-2222-2222-222222222222";
  private static final String PENDING_ORDER = "550e8400-e29b-41d4-a716-446655440001";
  private static final String PAID_ORDER = "550e8400-e29b-41d4-a716-446655440002";
  private static final String SHIPPED_ORDER = "550e8400-e29b-41d4-a716-446655440003";
  private static final String CANCELLED_ORDER = "550e8400-e29b-41d4-a716-446655440004";
  private static final String OTHER_ORDER = "550e8400-e29b-41d4-a716-446655440005";
  private static final String CLEANUP =
      "delete from order_lines; delete from orders; delete from stock";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Pedido PENDING cancelado libera stock de todas sus líneas")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 7)",
        "insert into stock (product_id, available) values ('" + B + "', 8)",
        "insert into orders (id, customer_id, status) values ('"
            + PENDING_ORDER
            + "', '"
            + CUSTOMER
            + "', 'PENDING')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + PENDING_ORDER
            + "', '"
            + A
            + "', 3)",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + PENDING_ORDER
            + "', '"
            + B
            + "', 2)"
      })
  void should_cancel_pending_order_and_release_all_stock_lines() throws Exception {
    cancel(PENDING_ORDER, CUSTOMER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(CUSTOMER))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.lines.length()").value(2));

    assertThat(statusOf(PENDING_ORDER)).isEqualTo("CANCELLED");
    assertThat(availableOf(A)).isEqualTo(10);
    assertThat(availableOf(B)).isEqualTo(10);
  }

  @Test
  @DisplayName("Frontera: pedido PAID cancelable")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 9)",
        "insert into orders (id, customer_id, status) values ('"
            + PAID_ORDER
            + "', '"
            + CUSTOMER
            + "', 'PAID')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + PAID_ORDER
            + "', '"
            + A
            + "', 1)"
      })
  void should_cancel_paid_order() throws Exception {
    cancel(PAID_ORDER, CUSTOMER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    assertThat(statusOf(PAID_ORDER)).isEqualTo("CANCELLED");
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Frontera: pedido SHIPPED no cancelable")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 9)",
        "insert into orders (id, customer_id, status) values ('"
            + SHIPPED_ORDER
            + "', '"
            + CUSTOMER
            + "', 'SHIPPED')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + SHIPPED_ORDER
            + "', '"
            + A
            + "', 1)"
      })
  void should_reject_shipped_order() throws Exception {
    cancel(SHIPPED_ORDER, CUSTOMER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-cancellable"));

    assertThat(statusOf(SHIPPED_ORDER)).isEqualTo("SHIPPED");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Pedido ya cancelado")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 10)",
        "insert into orders (id, customer_id, status) values ('"
            + CANCELLED_ORDER
            + "', '"
            + CUSTOMER
            + "', 'CANCELLED')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + CANCELLED_ORDER
            + "', '"
            + A
            + "', 3)"
      })
  void should_reject_already_cancelled_order() throws Exception {
    cancel(CANCELLED_ORDER, CUSTOMER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-cancellable"));

    assertThat(statusOf(CANCELLED_ORDER)).isEqualTo("CANCELLED");
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Pedido inexistente")
  @Sql(statements = {CLEANUP, "insert into stock (product_id, available) values ('" + A + "', 10)"})
  void should_reject_missing_order() throws Exception {
    cancel("00000000-0000-0000-0000-000000000000", CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Cancelación con id de pedido que no es UUID")
  @Sql(statements = {CLEANUP, "insert into stock (product_id, available) values ('" + A + "', 10)"})
  void should_reject_non_uuid_order_id() throws Exception {
    cancel("no-uuid", CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Pedido de otro cliente")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 9)",
        "insert into orders (id, customer_id, status) values ('"
            + OTHER_ORDER
            + "', '"
            + OTHER_CUSTOMER
            + "', 'PENDING')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + OTHER_ORDER
            + "', '"
            + A
            + "', 1)"
      })
  void should_reject_order_owned_by_another_customer() throws Exception {
    cancel(OTHER_ORDER, CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(statusOf(OTHER_ORDER)).isEqualTo("PENDING");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Cancelación sin cabecera X-Customer-Id")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 9)",
        "insert into orders (id, customer_id, status) values ('"
            + PENDING_ORDER
            + "', '"
            + CUSTOMER
            + "', 'PENDING')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + PENDING_ORDER
            + "', '"
            + A
            + "', 1)"
      })
  void should_reject_missing_customer_header() throws Exception {
    cancel(PENDING_ORDER, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-customer-id"));

    assertThat(statusOf(PENDING_ORDER)).isEqualTo("PENDING");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Cancelación con X-Customer-Id que no es UUID")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 9)",
        "insert into orders (id, customer_id, status) values ('"
            + PENDING_ORDER
            + "', '"
            + CUSTOMER
            + "', 'PENDING')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + PENDING_ORDER
            + "', '"
            + A
            + "', 1)"
      })
  void should_reject_invalid_customer_header() throws Exception {
    cancel(PENDING_ORDER, "no-es-uuid")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-customer-id"));

    assertThat(statusOf(PENDING_ORDER)).isEqualTo("PENDING");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Precedencia: cabecera antes que existencia")
  @Sql(statements = {CLEANUP})
  void should_prioritize_customer_header_over_order_existence() throws Exception {
    cancel("00000000-0000-0000-0000-000000000000", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-customer-id"));
  }

  @Test
  @DisplayName("Precedencia: propiedad antes que estado")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 9)",
        "insert into orders (id, customer_id, status) values ('"
            + SHIPPED_ORDER
            + "', '"
            + OTHER_CUSTOMER
            + "', 'SHIPPED')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + SHIPPED_ORDER
            + "', '"
            + A
            + "', 1)"
      })
  void should_prioritize_order_ownership_over_order_status() throws Exception {
    cancel(SHIPPED_ORDER, CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(statusOf(SHIPPED_ORDER)).isEqualTo("SHIPPED");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Dos cancelaciones concurrentes del mismo pedido")
  @Sql(
      statements = {
        CLEANUP,
        "insert into stock (product_id, available) values ('" + A + "', 9)",
        "insert into orders (id, customer_id, status) values ('"
            + PENDING_ORDER
            + "', '"
            + CUSTOMER
            + "', 'PENDING')",
        "insert into order_lines (order_id, product_id, quantity) values ('"
            + PENDING_ORDER
            + "', '"
            + A
            + "', 1)"
      })
  void should_release_stock_only_once_when_two_cancellations_are_concurrent() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<CancellationResponse> attempt =
        () -> {
          start.await();
          var response = cancel(PENDING_ORDER, CUSTOMER).andReturn().getResponse();
          return new CancellationResponse(response.getStatus(), response.getContentAsString());
        };

    try {
      Future<CancellationResponse> first = executor.submit(attempt);
      Future<CancellationResponse> second = executor.submit(attempt);
      start.countDown();

      List<CancellationResponse> responses =
          List.of(first.get(30, SECONDS), second.get(30, SECONDS));

      assertThat(responses)
          .extracting(CancellationResponse::status)
          .containsExactlyInAnyOrder(200, 409);
      assertThat(responses)
          .filteredOn(response -> response.status() == 409)
          .extracting(CancellationResponse::body)
          .allSatisfy(body -> assertThat(body).contains(TYPE_BASE + "order-not-cancellable"));
      assertThat(statusOf(PENDING_ORDER)).isEqualTo("CANCELLED");
      assertThat(availableOf(A)).isEqualTo(10);
    } finally {
      executor.shutdownNow();
    }
  }

  private ResultActions cancel(String orderId, String customerId) throws Exception {
    var request = post("/api/orders/{id}/cancellation", orderId);
    if (customerId != null) {
      request.header("X-Customer-Id", customerId);
    }
    return mockMvc.perform(request);
  }

  private String statusOf(String orderId) {
    return jdbcTemplate.queryForObject(
        "select status from orders where id = ?", String.class, UUID.fromString(orderId));
  }

  private int availableOf(String productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?",
        Integer.class,
        UUID.fromString(productId));
  }

  private record CancellationResponse(int status, String body) {}
}
