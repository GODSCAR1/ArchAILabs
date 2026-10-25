package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/** Detalle de pedido: GET /api/orders/{id}. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderDetailIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final UUID CUSTOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID ORDER = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void insertOrderWithTwoLines() {
    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");
    jdbcTemplate.update(
        "insert into orders (id, customer_id, status) values (?, ?, ?)", ORDER, CUSTOMER, "PAID");
    jdbcTemplate.update(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)",
        ORDER,
        PRODUCT_A,
        3);
    jdbcTemplate.update(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)",
        ORDER,
        PRODUCT_B,
        2);
  }

  @Test
  void should_return_order_with_its_lines_when_customer_owns_it() throws Exception {
    mockMvc
        .perform(get("/api/orders/{id}", ORDER).header("X-Customer-Id", CUSTOMER.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ORDER.toString()))
        .andExpect(jsonPath("$.lines.length()").value(2));
  }
}
