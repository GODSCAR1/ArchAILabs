package com.codeja.master_acdc.ordering;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class OrderCancellationIT extends IntegrationTest {

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
  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired AccessTokenIssuer tokenIssuer;

  @Test
  @DisplayName("Pedido PENDING cancelado libera stock de todas sus líneas")
  void should_release_all_stock_lines_when_pending_order_is_cancelled() throws Exception {
    stock(A, 7);
    stock(B, 8);
    order(PENDING_ORDER, CUSTOMER, "PENDING");
    line(PENDING_ORDER, A, 3);
    line(PENDING_ORDER, B, 2);
    cancel(PENDING_ORDER, CUSTOMER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").doesNotExist())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.lines.length()").value(2));

    assertThat(statusOf(PENDING_ORDER)).isEqualTo("CANCELLED");
    assertThat(availableOf(A)).isEqualTo(10);
    assertThat(availableOf(B)).isEqualTo(10);
  }

  @Test
  @DisplayName("Frontera: pedido PAID cancelable")
  void should_cancel_order_when_status_is_paid() throws Exception {
    stock(A, 9);
    order(PAID_ORDER, CUSTOMER, "PAID");
    line(PAID_ORDER, A, 1);
    cancel(PAID_ORDER, CUSTOMER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    assertThat(statusOf(PAID_ORDER)).isEqualTo("CANCELLED");
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Frontera: pedido SHIPPED no cancelable")
  void should_reject_cancellation_when_status_is_shipped() throws Exception {
    stock(A, 9);
    order(SHIPPED_ORDER, CUSTOMER, "SHIPPED");
    line(SHIPPED_ORDER, A, 1);
    cancel(SHIPPED_ORDER, CUSTOMER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-cancellable"));

    assertThat(statusOf(SHIPPED_ORDER)).isEqualTo("SHIPPED");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Pedido ya cancelado")
  void should_reject_cancellation_when_order_is_already_cancelled() throws Exception {
    stock(A, 10);
    order(CANCELLED_ORDER, CUSTOMER, "CANCELLED");
    line(CANCELLED_ORDER, A, 3);
    cancel(CANCELLED_ORDER, CUSTOMER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-cancellable"));

    assertThat(statusOf(CANCELLED_ORDER)).isEqualTo("CANCELLED");
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Pedido inexistente")
  void should_return_not_found_when_order_does_not_exist() throws Exception {
    stock(A, 10);
    cancel("00000000-0000-0000-0000-000000000000", CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Cancelación con id de pedido que no es UUID")
  void should_return_not_found_when_order_id_is_not_uuid() throws Exception {
    stock(A, 10);
    cancel("no-uuid", CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Pedido de otro cliente")
  void should_return_not_found_when_order_belongs_to_another_customer() throws Exception {
    stock(A, 9);
    order(OTHER_ORDER, OTHER_CUSTOMER, "PENDING");
    line(OTHER_ORDER, A, 1);
    cancel(OTHER_ORDER, CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(statusOf(OTHER_ORDER)).isEqualTo("PENDING");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Lectura global no concede cancelación")
  void should_reject_cancellation_when_support_has_no_cancel_permission() throws Exception {
    pendingFixture();
    cancel(PENDING_ORDER, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "forbidden"));

    assertThat(statusOf(PENDING_ORDER)).isEqualTo("PENDING");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Cliente ausente no abre el alcance")
  void should_hide_order_when_account_has_no_customer() throws Exception {
    pendingFixture();
    cancel(PENDING_ORDER, "no-es-uuid")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(statusOf(PENDING_ORDER)).isEqualTo("PENDING");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Precedencia: cabecera antes que existencia")
  void should_report_forbidden_when_support_cancels_missing_order() throws Exception {
    cancel("00000000-0000-0000-0000-000000000000", null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "forbidden"));
  }

  @Test
  @DisplayName("Precedencia: propiedad antes que estado")
  void should_report_not_found_when_shipped_order_belongs_to_another_customer() throws Exception {
    stock(A, 9);
    order(SHIPPED_ORDER, OTHER_CUSTOMER, "SHIPPED");
    line(SHIPPED_ORDER, A, 1);
    cancel(SHIPPED_ORDER, CUSTOMER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));

    assertThat(statusOf(SHIPPED_ORDER)).isEqualTo("SHIPPED");
    assertThat(availableOf(A)).isEqualTo(9);
  }

  @Test
  @DisplayName("Dos cancelaciones concurrentes del mismo pedido")
  void should_release_stock_only_once_when_two_cancellations_are_concurrent() throws Exception {
    pendingFixture();
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
    var request =
        post("/api/orders/{id}/cancellation", orderId)
            .header(
                "Authorization",
                "Bearer "
                    + OrderBearer.issue(
                        jdbcTemplate,
                        tokenIssuer,
                        CUSTOMER.equals(customerId) ? UUID.fromString(CUSTOMER) : null,
                        customerId == null ? "SUPPORT" : "CUSTOMER"));
    if (customerId != null) {
      request.header("X-Customer-Id", customerId);
    }
    return mockMvc.perform(request);
  }

  @Test
  @DisplayName("Pedido reembolsado no cancelable")
  void should_preserve_order_and_stock_when_cancelling_refunded_order() throws Exception {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("insert into stock(product_id,available) values (?,7)", UUID.fromString(A));
    jdbcTemplate.update(
        "insert into orders(id,customer_id,status) values (?,?,'REFUNDED')",
        id,
        UUID.fromString(CUSTOMER));
    jdbcTemplate.update(
        "insert into order_lines(order_id,product_id,quantity) values (?,?,3)",
        id,
        UUID.fromString(A));
    cancel(id.toString(), CUSTOMER)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-cancellable"));
    assertThat(statusOf(id.toString())).isEqualTo("REFUNDED");
    assertThat(availableOf(A)).isEqualTo(7);
  }

  private String statusOf(String orderId) {
    return jdbcTemplate.queryForObject(
        "select status from orders where id = ?", String.class, UUID.fromString(orderId));
  }

  private void pendingFixture() {
    stock(A, 9);
    order(PENDING_ORDER, CUSTOMER, "PENDING");
    line(PENDING_ORDER, A, 1);
  }

  private void stock(String productId, int available) {
    jdbcTemplate.update(
        "insert into stock (product_id, available) values (?, ?)",
        UUID.fromString(productId),
        available);
  }

  private void order(String orderId, String customerId, String status) {
    jdbcTemplate.update(
        "insert into orders (id, customer_id, status) values (?, ?, ?)",
        UUID.fromString(orderId),
        UUID.fromString(customerId),
        status);
  }

  private void line(String orderId, String productId, int quantity) {
    jdbcTemplate.update(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)",
        UUID.fromString(orderId),
        UUID.fromString(productId),
        quantity);
  }

  private int availableOf(String productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?",
        Integer.class,
        UUID.fromString(productId));
  }

  private record CancellationResponse(int status, String body) {}
}
