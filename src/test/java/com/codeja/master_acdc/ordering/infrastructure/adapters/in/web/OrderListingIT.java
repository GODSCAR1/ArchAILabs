package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderListingIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final UUID CUSTOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID PAID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID PENDING = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
  private static final UUID ORPHAN = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
  private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");
    jdbcTemplate.update("delete from customers");
  }

  @Test
  @DisplayName("Pedidos PAID listados con nombre y métricas")
  void should_list_paid_orders_when_paid_orders_exist() throws Exception {
    insertCustomer();
    insertOrder(PAID, "PAID", 3, 2);

    mockMvc
        .perform(get("/api/orders").param("status", "PAID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(PAID.toString()))
        .andExpect(jsonPath("$[0].customerName").value("Ada Lovelace"))
        .andExpect(jsonPath("$[0].lineCount").value(2))
        .andExpect(jsonPath("$[0].totalUnits").value(5))
        .andExpect(jsonPath("$[0].productIds").isArray())
        .andExpect(jsonPath("$[0].productIds.length()").value(2));
  }

  @Test
  @DisplayName("Un pedido PENDING no aparece al solicitar PAID")
  void should_exclude_pending_orders_when_status_is_paid() throws Exception {
    insertOrder(PAID, "PAID", 3);
    insertOrder(PENDING, "PENDING", 2);

    mockMvc
        .perform(get("/api/orders").param("status", "PAID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(PAID.toString()))
        .andExpect(jsonPath("$[1]").doesNotExist());
  }

  @Test
  @DisplayName("Sin pedidos PAID")
  void should_return_empty_list_when_no_paid_orders_exist() throws Exception {
    mockMvc
        .perform(get("/api/orders").param("status", "PAID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  @DisplayName("Status inválido")
  void should_reject_unknown_status_when_status_is_unknown() throws Exception {
    mockMvc
        .perform(get("/api/orders").param("status", "FOO"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-order-status"));
  }

  @Test
  @DisplayName("Status en minúsculas")
  void should_reject_lowercase_status_when_status_is_lowercase() throws Exception {
    mockMvc
        .perform(get("/api/orders").param("status", "paid"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-order-status"));
  }

  @Test
  @DisplayName("Sin parámetro status")
  void should_reject_missing_status_when_parameter_is_absent() throws Exception {
    mockMvc
        .perform(get("/api/orders"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/missing-order-status"));
  }

  @Test
  @DisplayName("Pedido cuyo cliente no existe")
  void should_return_null_customer_name_when_customer_is_missing() throws Exception {
    insertOrder(ORPHAN, "PAID", 3);

    mockMvc
        .perform(get("/api/orders").param("status", "PAID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(ORPHAN.toString()))
        .andExpect(jsonPath("$[0].customerName").value(nullValue()));
  }

  private void insertCustomer() {
    jdbcTemplate.update("insert into customers (id, name) values (?, ?)", CUSTOMER, "Ada Lovelace");
  }

  private void insertOrder(UUID orderId, String status, int... quantities) {
    jdbcTemplate.update(
        "insert into orders (id, customer_id, status) values (?, ?, ?)", orderId, CUSTOMER, status);
    UUID[] products = {PRODUCT_A, PRODUCT_B};
    for (int index = 0; index < quantities.length; index++) {
      jdbcTemplate.update(
          "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)",
          orderId,
          products[index],
          quantities[index]);
    }
  }
}
