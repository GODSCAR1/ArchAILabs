package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.catalog.application.port.ProductReviewsUseCase;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ProductReviewQueryIT {

  private static final UUID PRODUCT_ID =
      UUID.nameUUIDFromBytes("review-product".getBytes(StandardCharsets.UTF_8));
  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductReviewsUseCase productReviewsUseCase;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
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
  }

  @Test
  @DisplayName("Primera página de reseñas")
  void should_return_page_metadata_when_reviews_are_requested() {
    ProductReviewPage result = productReviewsUseCase.findBySku("SKU-REVIEWS", 0, 20);

    assertThat(result.page()).isZero();
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.totalElements()).isEqualTo(2);
    assertThat(result.totalPages()).isEqualTo(1);
  }

  @Test
  @DisplayName("Página vacía de reseñas")
  void should_return_empty_content_when_requested_page_is_after_last_page() {
    assertThat(productReviewsUseCase.findBySku("SKU-REVIEWS", 1, 20).content()).isEmpty();
  }

  @Test
  @DisplayName("Desempate de reseñas")
  void should_order_reviews_by_created_at_and_id_when_dates_are_equal() {
    ProductReviewPage result = productReviewsUseCase.findBySku("SKU-REVIEWS", 0, 20);

    assertThat(result.content())
        .extracting(review -> review.id())
        .containsExactly(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000002"));
  }

  @Test
  @DisplayName("Tamaño superior al máximo")
  void should_normalize_size_to_one_hundred_when_size_is_too_large() {
    assertThat(productReviewsUseCase.findBySku("SKU-REVIEWS", 0, 1000).size()).isEqualTo(100);
  }

  @Test
  @DisplayName("SKU inexistente en reseñas")
  void should_reject_missing_product_when_reviews_are_requested() {
    jdbcTemplate.update("delete from reviews where product_id = ?", PRODUCT_ID);
    jdbcTemplate.update("delete from products where id = ?", PRODUCT_ID);

    assertThatThrownBy(() -> productReviewsUseCase.findBySku("SKU-REVIEWS", 0, 20))
        .isInstanceOf(ProductNotFoundException.class);
  }

  private void insertReview(UUID id, Instant createdAt) {
    jdbcTemplate.update(
        "insert into reviews (id, product_id, author, rating, comment, created_at) values (?, ?,"
            + " 'Ana', 5, 'Good', ?)",
        id,
        PRODUCT_ID,
        Timestamp.from(createdAt));
  }
}
