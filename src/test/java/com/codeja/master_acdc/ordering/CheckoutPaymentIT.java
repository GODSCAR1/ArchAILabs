package com.codeja.master_acdc.ordering;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CategoryRef;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.RatingSummary;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class CheckoutPaymentIT extends IntegrationTest {
  private final UUID id = UUID.randomUUID();
  private final UUID customer = UUID.randomUUID();
  private final UUID product = UUID.randomUUID();
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MongoTemplate mongo;
  @Autowired private AccessTokenIssuer issuer;
  @Autowired private PaymentGateway gateway;
  private String bearer;

  @BeforeEach
  void prepare_order() {
    bearer = OrderBearer.issue(jdbc, issuer, customer, "CUSTOMER");
    jdbc.update(
        "insert into orders (id, customer_id, status) values (?, ?, 'PENDING')", id, customer);
    jdbc.update(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, 2)", id, product);
    jdbc.update("insert into stock (product_id, available) values (?, 8)", product);
  }

  @Test
  @DisplayName("Precio cambia antes del primer intento")
  void should_charge_current_price_when_confirming_for_first_time() throws Exception {
    insert_product();
    changePrice(100);
    changePrice(250);
    doAnswer(
            invocation -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              return invocation.callRealMethod();
            })
        .when(spiedPaymentGateway)
        .authorize(any(), any());
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));

    mvc.perform(
            post("/api/orders/{id}/confirmation", id).header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.customerId").doesNotExist());
    assertThat(
            jdbc.queryForObject(
                "select payment_amount from orders where id = ?", BigDecimal.class, id))
        .isEqualByComparingTo("500");
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(8);
    wiremock().verifyThat(1, postRequestedFor(urlEqualTo("/authorizations")));
  }

  @Test
  @DisplayName("Pago rechazado conserva reserva")
  void should_keep_reservation_when_gateway_declines() throws Exception {
    insert_product();
    stubPayment(200, "{\"status\":\"DECLINED\"}");
    confirm()
        .andExpect(status().isPaymentRequired())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/payment-declined"));
    assertPendingAndStock();
    assertThat(
            jdbc.queryForObject(
                "select payment_attempt from orders where id = ?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Error de pasarela conserva reserva")
  void should_keep_reservation_when_gateway_fails() throws Exception {
    insert_product();
    stubPayment(500, "");
    confirm()
        .andExpect(status().isBadGateway())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/payment-unavailable"));
    assertPendingAndStock();
    assertThat(
            jdbc.queryForObject(
                "select payment_attempt from orders where id = ?", Integer.class, id))
        .isZero();
  }

  @Test
  @DisplayName("Reintento tras error conserva importe y clave")
  void should_reuse_key_and_amount_when_retrying_after_error() throws Exception {
    insert_product();
    stubPayment(500, "");
    confirm().andExpect(status().isBadGateway());
    changePrice(900);
    stubAuthorized();
    confirm().andExpect(status().isOk());
    wiremock()
        .verifyThat(
            2,
            postRequestedFor(urlEqualTo("/authorizations"))
                .withHeader("Idempotency-Key", equalTo("order-" + id + "-0"))
                .withRequestBody(equalToJson("{\"amount\":500,\"currency\":\"COP\"}")));
  }

  @Test
  @DisplayName("Nuevo intento tras rechazo usa otra clave")
  void should_change_key_when_retrying_after_decline() throws Exception {
    insert_product();
    stubPayment(200, "{\"status\":\"DECLINED\"}");
    confirm().andExpect(status().isPaymentRequired());
    changePrice(900);
    stubAuthorized();
    confirm().andExpect(status().isOk());
    wiremock()
        .verifyThat(
            1,
            postRequestedFor(urlEqualTo("/authorizations"))
                .withHeader("Idempotency-Key", equalTo("order-" + id + "-0")));
    wiremock()
        .verifyThat(
            1,
            postRequestedFor(urlEqualTo("/authorizations"))
                .withHeader("Idempotency-Key", equalTo("order-" + id + "-1"))
                .withRequestBody(equalToJson("{\"amount\":500,\"currency\":\"COP\"}")));
  }

  @Test
  @DisplayName("Pedido no pendiente no inicia pago")
  void should_reject_confirmation_when_order_is_not_pending() throws Exception {
    for (String state : List.of("PAYING", "PAID", "CANCELLED")) {
      jdbc.update("update orders set status = ? where id = ?", state, id);
      confirm()
          .andExpect(status().isConflict())
          .andExpect(
              jsonPath("$.type")
                  .value("https://api.master-acdc.dev/problems/order-not-confirmable"));
      assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, id))
          .isEqualTo(state);
    }
    wiremock().verifyThat(0, postRequestedFor(urlEqualTo("/authorizations")));
  }

  @Test
  @DisplayName("Pedido ajeno o inexistente no inicia pago")
  void should_return_not_found_when_order_is_foreign_or_missing() throws Exception {
    String other = OrderBearer.issue(jdbc, issuer, UUID.randomUUID(), "CUSTOMER");
    mvc.perform(
            post("/api/orders/{id}/confirmation", id).header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/order-not-found"));
    mvc.perform(
            post("/api/orders/{id}/confirmation", UUID.randomUUID())
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/order-not-found"));
    assertPendingAndStock();
    wiremock().verifyThat(0, postRequestedFor(urlEqualTo("/authorizations")));
  }

  @Test
  @DisplayName("Listado de pedidos en pago")
  void should_list_only_owned_orders_when_filtering_paying() throws Exception {
    jdbc.update("update orders set status = 'PAYING' where id = ?", id);
    UUID otherCustomer = UUID.randomUUID();
    OrderBearer.issue(jdbc, issuer, otherCustomer, "CUSTOMER");
    jdbc.update(
        "insert into orders (id, customer_id, status) values (?, ?, 'PAYING')",
        UUID.randomUUID(),
        otherCustomer);
    mvc.perform(
            get("/api/orders")
                .param("status", "PAYING")
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id.toString()));
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(8);
  }

  @Test
  @DisplayName("Cancelación mientras el pago está en curso")
  void should_reject_cancellation_when_gateway_is_authorizing() throws Exception {
    insert_product();
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withFixedDelay(350)
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));
    try (var executor = Executors.newSingleThreadExecutor()) {
      var payment = executor.submit(() -> confirm().andExpect(status().isOk()));
      Awaitility.await()
          .pollInterval(Duration.ofMillis(10))
          .atMost(Duration.ofSeconds(3))
          .untilAsserted(
              () ->
                  assertThat(
                          jdbc.queryForObject(
                              "select status from orders where id = ?", String.class, id))
                      .isEqualTo("PAYING"));
      mvc.perform(
              post("/api/orders/{id}/cancellation", id).header("Authorization", "Bearer " + bearer))
          .andExpect(status().isConflict())
          .andExpect(
              jsonPath("$.type")
                  .value("https://api.master-acdc.dev/problems/order-not-cancellable"));
      payment.get(5, TimeUnit.SECONDS);
    }
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, id))
        .isEqualTo("PAID");
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(8);
  }

  private org.springframework.test.web.servlet.ResultActions confirm() throws Exception {
    return mvc.perform(
        post("/api/orders/{id}/confirmation", id).header("Authorization", "Bearer " + bearer));
  }

  private void stubPayment(int code, String body) {
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withStatus(code)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
  }

  private void stubAuthorized() {
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));
  }

  private void changePrice(long price) {
    mongo.updateFirst(
        Query.query(Criteria.where("_id").is(product)),
        Update.update("price", price),
        CatalogProduct.class);
  }

  private void assertPendingAndStock() {
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, id))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(8);
  }

  @Test
  @DisplayName("Falta precio de un producto")
  void should_reject_confirmation_when_product_price_is_missing() throws Exception {
    mvc.perform(
            post("/api/orders/{id}/confirmation", id).header("Authorization", "Bearer " + bearer))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/payment-price-unavailable"));
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, id))
        .isEqualTo("PENDING");
    wiremock().verifyThat(0, postRequestedFor(urlEqualTo("/authorizations")));
  }

  private void insert_product() {
    mongo.insert(
        new CatalogProduct(
            product,
            "PAY-SKU",
            "Producto",
            250,
            ProductStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            new CategoryRef(UUID.randomUUID(), "Categoría"),
            List.of(),
            Map.of(),
            8,
            new RatingSummary(BigDecimal.ZERO, 0),
            List.of()));
  }

  @Test
  @DisplayName("La pasarela recibe una autorización real")
  void should_authorize_when_gateway_returns_authorized() {
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));
    assertThat(gateway.authorize(id, new PaymentAttempt(BigDecimal.ONE, 0))).isNotNull();
  }
}
