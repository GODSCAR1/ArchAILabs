package com.codeja.master_acdc.ordering;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class CheckoutTimeoutIT extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenIssuer issuer;

  @Test
  @DisplayName("Timeout de pasarela conserva reserva")
  void should_preserve_reservation_and_key_when_gateway_times_out() throws Exception {
    UUID customer = UUID.randomUUID();
    UUID order = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    String bearer = OrderBearer.issue(jdbc, issuer, customer, "CUSTOMER");
    prepareOrder(customer, order, product);
    registerSlowPayment();
    long start = System.nanoTime();
    mvc.perform(
            post("/api/orders/{id}/confirmation", order)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isGatewayTimeout())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/payment-timeout"));
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(3000);
    assertPending(order, product);
    retry(order, bearer);
  }

  private void prepareOrder(UUID customer, UUID order, UUID product) {
    jdbc.update(
        "insert into orders (id, customer_id, status, payment_amount) values (?, ?, 'PENDING',"
            + " 500)",
        order,
        customer);
    jdbc.update(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, 2)",
        order,
        product);
    jdbc.update("insert into stock (product_id, available) values (?, 8)", product);
  }

  private void registerSlowPayment() {
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withFixedDelay(4000)
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));
  }

  private void assertPending(UUID order, UUID product) {
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, order))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "select payment_attempt from orders where id = ?", Integer.class, order))
        .isZero();
  }

  private void retry(UUID order, String bearer) throws Exception {
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));
    mvc.perform(
            post("/api/orders/{id}/confirmation", order)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk());
    wiremock()
        .verifyThat(
            2,
            postRequestedFor(urlEqualTo("/authorizations"))
                .withHeader("Idempotency-Key", equalTo("order-" + order + "-0")));
  }
}
