package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
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
abstract class ProductReviewQueryIT {

  private static final UUID PRODUCT_ID =
      UUID.nameUUIDFromBytes("review-product".getBytes(StandardCharsets.UTF_8));
  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired MockMvc mockMvc;

  @BeforeEach
  void cleanDatabase() {
    prepareReadStore();
    jdbcTemplate.update("delete from reviews");
    jdbcTemplate.update("delete from products where id = ?", PRODUCT_ID);
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, 'Cafe', 'SKU-REVIEWS', 100, ?, 'ACTIVE', current_timestamp, 0)",
        PRODUCT_ID,
        CATEGORY_ID);
    insertReview(
        UUID.fromString("10000000-0000-0000-0000-000000000002"),
        Instant.parse("2026-01-01T00:00:00Z"));
    insertReview(
        UUID.fromString("10000000-0000-0000-0000-000000000001"),
        Instant.parse("2026-01-01T00:00:00Z"));
    afterWrite();
  }

  @Test
  @DisplayName("Reseñas completas paginadas")
  void should_return_page_metadata_when_reviews_are_requested() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-REVIEWS/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  @DisplayName("Página vacía de reseñas")
  void should_return_empty_content_when_requested_page_is_after_last_page() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-REVIEWS/reviews").queryParam("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("Desempate de reseñas")
  void should_order_reviews_by_created_at_and_id_when_dates_are_equal() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-REVIEWS/reviews"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[*].id")
                .value(
                    contains(
                        "10000000-0000-0000-0000-000000000001",
                        "10000000-0000-0000-0000-000000000002")));
  }

  @Test
  @DisplayName("Tamaño superior al máximo")
  void should_normalize_size_to_one_hundred_when_size_is_too_large() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-REVIEWS/reviews").queryParam("size", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  @DisplayName("Producto inexistente en ambos modos")
  void should_reject_missing_product_when_reviews_are_requested() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-NOT-THERE/reviews"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
  }

  protected void prepareReadStore() {}

  protected void afterWrite() {}

  private void insertReview(UUID id, Instant createdAt) {
    jdbcTemplate.update(
        "insert into reviews (id, product_id, author, rating, comment, created_at) values (?, ?,"
            + " 'Ana', 5, 'Good', ?)",
        id,
        PRODUCT_ID,
        Timestamp.from(createdAt));
  }
}
