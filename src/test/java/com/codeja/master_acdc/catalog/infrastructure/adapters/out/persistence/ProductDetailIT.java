package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

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
abstract class ProductDetailIT {

  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PRODUCT_ID =
      UUID.nameUUIDFromBytes("detail-it".getBytes(StandardCharsets.UTF_8));

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired MockMvc mockMvc;

  @BeforeEach
  void cleanDatabase() {
    prepareReadStore();
    jdbcTemplate.update("delete from reviews");
    jdbcTemplate.update("delete from product_attributes");
    jdbcTemplate.update("delete from product_variants");
    jdbcTemplate.update("delete from products where id = ?", PRODUCT_ID);
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, 'Cafe', 'SKU-DETAIL-IT', 100, ?, 'ACTIVE', current_timestamp, 0)",
        PRODUCT_ID,
        CATEGORY_ID);
    afterWrite();
  }

  @Test
  @DisplayName("Respuesta HTTP equivalente a PostgreSQL")
  void should_return_complete_product_detail_when_all_data_exists() throws Exception {
    insertOptionalData(3);
    afterWrite();

    mockMvc
        .perform(get("/api/products/SKU-DETAIL-IT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category.id").value(CATEGORY_ID.toString()))
        .andExpect(jsonPath("$.stock").value(0))
        .andExpect(jsonPath("$.variants").isArray())
        .andExpect(jsonPath("$.attributes.origin").value("Colombia"))
        .andExpect(jsonPath("$.rating.average").value(4.33));
  }

  @Test
  @DisplayName("Producto sin stock ni datos opcionales")
  void should_return_empty_optional_data_when_no_optional_rows_exist() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-DETAIL-IT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(0))
        .andExpect(jsonPath("$.variants").isEmpty())
        .andExpect(jsonPath("$.attributes").isEmpty())
        .andExpect(jsonPath("$.rating.average").value(0))
        .andExpect(jsonPath("$.rating.count").value(0));
  }

  @Test
  @DisplayName("Promedio con decimales")
  void should_round_average_to_two_decimal_places_when_ratings_have_decimals() throws Exception {
    insertOptionalData(3);
    afterWrite();

    mockMvc
        .perform(get("/api/products/SKU-DETAIL-IT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating.average").value(4.33));
  }

  @Test
  @DisplayName("Variantes y atributos pertenecen al producto")
  void should_return_variants_and_attributes_when_product_has_optional_data() throws Exception {
    insertOptionalData(3);
    afterWrite();

    mockMvc
        .perform(get("/api/products/SKU-DETAIL-IT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.variants.length()").value(3))
        .andExpect(jsonPath("$.attributes.origin").value("Colombia"));
  }

  @Test
  @DisplayName("Límite de reseñas recientes")
  void should_return_at_most_five_recent_reviews_when_product_has_many_reviews() throws Exception {
    insertOptionalData(7);
    afterWrite();

    mockMvc
        .perform(get("/api/products/SKU-DETAIL-IT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentReviews.length()").value(5));
  }

  @Test
  @DisplayName("Producto inexistente en ambos modos")
  void should_return_product_not_found_when_detail_sku_is_missing() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-MISSING"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
  }

  protected void prepareReadStore() {}

  protected void afterWrite() {}

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
