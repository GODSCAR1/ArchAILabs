package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.CatalogManagerToken;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StockReservationIT {

  private static final String TYPE_BASE = "https://api.master-acdc.dev/problems/";
  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired AccessTokenIssuer tokenIssuer;

  @BeforeEach
  void cleanStock() {
    jdbcTemplate.update("delete from stock");
  }

  @Test
  @DisplayName("Reserva directa exitosa")
  void should_return_created_and_remaining_stock_when_reservation_is_valid() throws Exception {
    insertStock(10);

    mockMvc
        .perform(reserve("{\"units\":3}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
        .andExpect(jsonPath("$.units").value(3))
        .andExpect(jsonPath("$.available").value(7));

    assertThat(available()).isEqualTo(7);
  }

  @Test
  @DisplayName("Stock insuficiente")
  void should_return_conflict_and_keep_stock_when_units_exceed_available() throws Exception {
    insertStock(2);

    mockMvc
        .perform(reserve("{\"units\":3}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Insufficient stock"))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "insufficient-stock"))
        .andExpect(jsonPath("$.available").value(2));

    assertThat(available()).isEqualTo(2);
  }

  @Test
  @DisplayName("Stock inexistente")
  void should_return_not_found_and_keep_stock_when_record_is_missing() throws Exception {
    mockMvc
        .perform(reserve("{\"units\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Stock not found"))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "stock-not-found"));

    assertThat(stockCount()).isZero();
  }

  @Test
  @DisplayName("Unidades ausentes")
  void should_return_bad_request_and_keep_stock_when_units_are_missing() throws Exception {
    insertStock(10);

    mockMvc
        .perform(reserve("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(available()).isEqualTo(10);
  }

  @Test
  @DisplayName("Unidades no positivas")
  void should_return_bad_request_and_keep_stock_when_units_are_not_positive() throws Exception {
    insertStock(10);

    mockMvc
        .perform(reserve("{\"units\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(available()).isEqualTo(10);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reserve(
      String body) {
    return post("/api/products/{id}/reservations", PRODUCT_ID)
        .header("Authorization", "Bearer " + CatalogManagerToken.issue(tokenIssuer))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private void insertStock(int units) {
    jdbcTemplate.update(
        "insert into stock (product_id, available) values (?, ?)", PRODUCT_ID, units);
  }

  private int available() {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?", Integer.class, PRODUCT_ID);
  }

  private int stockCount() {
    return jdbcTemplate.queryForObject("select count(*) from stock", Integer.class);
  }
}
