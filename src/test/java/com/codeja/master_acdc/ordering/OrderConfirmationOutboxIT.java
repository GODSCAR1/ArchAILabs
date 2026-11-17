package com.codeja.master_acdc.ordering;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class OrderConfirmationOutboxIT extends IntegrationTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MongoTemplate mongo;
  @Autowired private MockMvc mvc;
  @Autowired private AccessTokenIssuer issuer;

  @Test
  @DisplayName("Pago autorizado confirma pedido y proyecta stock")
  void should_project_stock_when_authorized_payment_is_confirmed() throws Exception {
    UUID customer = UUID.randomUUID();
    UUID order = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    String bearer = OrderBearer.issue(jdbc, issuer, customer, "CUSTOMER");
    seed(order, customer, product);
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));

    mvc.perform(
            post("/api/orders/{id}/confirmation", order)
                .header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.customerId").doesNotExist());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from order_confirmation_outbox where order_id = ?",
                Integer.class,
                order))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(7);
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(mongo.findById(product, CatalogProduct.class).availableStock())
                    .isEqualTo(7));
  }

  @Test
  @DisplayName("Fallo al registrar confirmación revierte ambos cambios")
  void should_leave_order_paying_when_outbox_fails_after_authorization() {
    UUID customer = UUID.randomUUID();
    UUID order = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    String bearer = OrderBearer.issue(jdbc, issuer, customer, "CUSTOMER");
    seed(order, customer, product);
    wiremock()
        .register(
            post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));
    doThrow(new DataIntegrityViolationException("test failure"))
        .when(spiedConfirmations)
        .append(any());

    assertThatThrownBy(
            () ->
                mvc.perform(
                    post("/api/orders/{id}/confirmation", order)
                        .header("Authorization", "Bearer " + bearer)))
        .hasRootCauseInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, order))
        .isEqualTo("PAYING");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from order_confirmation_outbox where order_id = ?",
                Integer.class,
                order))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(7);
  }

  private void seed(UUID order, UUID customer, UUID product) {
    jdbc.update(
        "insert into orders (id, customer_id, status, payment_amount) values (?, ?, 'PENDING',"
            + " 100)",
        order,
        customer);
    jdbc.update(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, 1)",
        order,
        product);
    jdbc.update("insert into stock (product_id, available) values (?, 7)", product);
    mongo.insert(
        new CatalogProduct(
            product,
            "SCHEDULED-SKU",
            "Producto",
            100,
            ProductStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            new CategoryRef(UUID.randomUUID(), "Categoria"),
            List.of(),
            Map.of(),
            0,
            new RatingSummary(BigDecimal.ZERO, 0),
            List.of()));
  }
}
