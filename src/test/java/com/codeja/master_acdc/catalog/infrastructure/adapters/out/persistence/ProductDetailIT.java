package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
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
class ProductDetailIT {

  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PRODUCT_ID =
      UUID.nameUUIDFromBytes("detail-it".getBytes(StandardCharsets.UTF_8));

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductDetailUseCase productDetailUseCase;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from reviews");
    jdbcTemplate.update("delete from product_attributes");
    jdbcTemplate.update("delete from product_variants");
    jdbcTemplate.update("delete from products where id = ?", PRODUCT_ID);
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, 'Cafe', 'SKU-DETAIL-IT', 100, ?, 'ACTIVE', current_timestamp, 0)",
        PRODUCT_ID,
        CATEGORY_ID);
  }

  @Test
  @DisplayName("Ficha existente con todos sus datos")
  void should_return_complete_product_detail_when_all_data_exists() {
    insertOptionalData(3);

    ProductDetail result = productDetailUseCase.findBySku("SKU-DETAIL-IT");

    assertThat(result.category().id()).isEqualTo(CATEGORY_ID);
    assertThat(result.stock()).isZero();
    assertThat(result.variants()).hasSize(3);
    assertThat(result.attributes()).containsEntry("origin", "Colombia");
    assertThat(result.rating().average()).isEqualByComparingTo("4.33");
  }

  @Test
  @DisplayName("Producto sin stock ni datos opcionales")
  void should_return_empty_optional_data_when_no_optional_rows_exist() {
    ProductDetail result = productDetailUseCase.findBySku("SKU-DETAIL-IT");

    assertThat(result.stock()).isZero();
    assertThat(result.variants()).isEmpty();
    assertThat(result.attributes()).isEmpty();
    assertThat(result.rating().average()).isEqualByComparingTo("0");
    assertThat(result.rating().count()).isZero();
  }

  @Test
  @DisplayName("Promedio con decimales")
  void should_round_average_to_two_decimal_places_when_ratings_have_decimals() {
    insertOptionalData(3);

    assertThat(productDetailUseCase.findBySku("SKU-DETAIL-IT").rating().average())
        .isEqualByComparingTo("4.33");
  }

  @Test
  @DisplayName("Variantes y atributos pertenecen al producto")
  void should_return_variants_and_attributes_when_product_has_optional_data() {
    insertOptionalData(3);

    ProductDetail result = productDetailUseCase.findBySku("SKU-DETAIL-IT");

    assertThat(result.variants()).allMatch(variant -> variant.skuSuffix().startsWith("-"));
    assertThat(result.attributes()).containsOnlyKeys("origin");
  }

  @Test
  @DisplayName("Límite de reseñas recientes")
  void should_return_at_most_five_recent_reviews_when_product_has_many_reviews() {
    insertOptionalData(7);

    assertThat(productDetailUseCase.findBySku("SKU-DETAIL-IT").recentReviews()).hasSize(5);
  }

  private void insertOptionalData(int reviewCount) {
    jdbcTemplate.update(
        "insert into product_attributes (product_id, name, value) values (?, 'origin', 'Colombia')",
        PRODUCT_ID);
    for (int index = 0; index < 3; index++) {
      jdbcTemplate.update(
          "insert into product_variants (id, product_id, sku_suffix, color, size, additional_price)"
              + " values (?, ?, ?, 'Red', 'M', 1)",
          UUID.nameUUIDFromBytes(("variant-it-" + index).getBytes(StandardCharsets.UTF_8)),
          PRODUCT_ID,
          "-" + index);
    }
    for (int index = 0; index < reviewCount; index++) {
      jdbcTemplate.update(
          "insert into reviews (id, product_id, author, rating, comment, created_at) values (?, ?,"
              + " 'Ana', ?, 'Good', ?)",
          UUID.nameUUIDFromBytes(("review-it-" + index).getBytes(StandardCharsets.UTF_8)),
          PRODUCT_ID,
          index == 0 ? 5 : 4,
          Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index)));
    }
  }
}
