package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.GlobalMongoClientTestConfiguration;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogVariant;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CategoryRef;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.RatingSummary;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ReviewSnippet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(GlobalMongoClientTestConfiguration.class)
class ProductDetailMongoIT {

  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MongoTemplate mongoTemplate;

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void clear_catalog_products() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    mongoTemplate.insert(
        new CatalogProduct(
            UUID.randomUUID(),
            "HTTP-DETAIL",
            "Detalle HTTP",
            100,
            ProductStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            new CategoryRef(CATEGORY_ID, "Sin categoría"),
            List.of(),
            Map.of("origin", "Colombia"),
            0,
            new RatingSummary(BigDecimal.ZERO.setScale(2), 0),
            List.of()));
  }

  @Test
  @DisplayName("Respuesta HTTP equivalente a PostgreSQL")
  void should_return_product_detail_when_sku_exists() throws Exception {
    mockMvc
        .perform(get("/api/products/HTTP-DETAIL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Detalle HTTP"))
        .andExpect(jsonPath("$.attributes.origin").value("Colombia"));
  }

  @Test
  @DisplayName("Producto inexistente en Mongo")
  void should_return_product_not_found_when_sku_is_missing() throws Exception {
    mockMvc
        .perform(get("/api/products/MISSING-HTTP"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
  }

  @Test
  @DisplayName("Promedio con decimales")
  void should_return_decimal_rating_average_when_migrated_ratings_are_five_four_four()
      throws Exception {
    seed_migrated_detail_data();

    mockMvc
        .perform(get("/api/products/HTTP-DETAIL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating.average").value(4.33))
        .andExpect(jsonPath("$.rating.count").value(3))
        .andExpect(jsonPath("$.recentReviews[*].rating").value(contains(5, 4, 4)));
  }

  @Test
  @DisplayName("Variantes y atributos pertenecen al producto")
  void should_return_migrated_variants_and_attributes_when_product_has_detail_data()
      throws Exception {
    seed_migrated_detail_data();

    mockMvc
        .perform(get("/api/products/HTTP-DETAIL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.variants.length()").value(2))
        .andExpect(jsonPath("$.variants[0].skuSuffix").value("-RED-M"))
        .andExpect(jsonPath("$.variants[1].skuSuffix").value("-BLUE-L"))
        .andExpect(jsonPath("$.attributes.origin").value("Colombia"));
  }

  private void seed_migrated_detail_data() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    mongoTemplate.insert(
        new CatalogProduct(
            UUID.randomUUID(),
            "HTTP-DETAIL",
            "Detalle HTTP",
            100,
            ProductStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            new CategoryRef(CATEGORY_ID, "Sin categoría"),
            migrated_variants(),
            Map.of("origin", "Colombia", "material", "Cotton"),
            0,
            new RatingSummary(new BigDecimal("4.33"), 3),
            migrated_reviews()));
  }

  private List<CatalogVariant> migrated_variants() {
    return List.of(
        new CatalogVariant(UUID.randomUUID(), "-RED-M", "Red", "M", 10),
        new CatalogVariant(UUID.randomUUID(), "-BLUE-L", "Blue", "L", 15));
  }

  private List<ReviewSnippet> migrated_reviews() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    return List.of(
        new ReviewSnippet(UUID.randomUUID(), "Ana", 5, "Excellent", createdAt),
        new ReviewSnippet(UUID.randomUUID(), "Luis", 4, "Good", createdAt.plusSeconds(1)),
        new ReviewSnippet(UUID.randomUUID(), "Eva", 4, "Nice", createdAt.plusSeconds(2)));
  }
}
