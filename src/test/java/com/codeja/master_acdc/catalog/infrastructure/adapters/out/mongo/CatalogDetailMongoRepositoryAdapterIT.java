package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.application.ProductDetailService;
import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.ProductVariant;
import com.codeja.master_acdc.catalog.infrastructure.adapters.in.web.ApiExceptionHandler;
import com.codeja.master_acdc.catalog.infrastructure.adapters.in.web.ProductDetailController;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CatalogDetailMongoRepositoryAdapterIT {

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static MongoClient client;
  private static MongoTemplate template;
  private CatalogDetailMongoRepositoryAdapter adapter;
  private MockMvc mockMvc;

  @BeforeAll
  static void initialize_mongo_template() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
    template = new MongoTemplate(client, "catalog_detail_test");
  }

  @AfterAll
  static void close_client() {
    client.close();
  }

  @BeforeEach
  void initialize_adapter() {
    template.getDb().drop();
    new CatalogIndexInitializer(template).initialize();
    adapter = new CatalogDetailMongoRepositoryAdapter(template);
    ProductDetailService service = new ProductDetailService(adapter);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProductDetailController(service, service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("Producto inexistente en ambos modos")
  void should_return_not_found_problem_when_detail_or_reviews_sku_is_missing() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-MISSING"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
    mockMvc
        .perform(get("/api/products/SKU-MISSING/reviews"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
  }

  @Test
  @DisplayName("Ficha agregada disponible")
  void should_return_mapped_detail_when_catalog_product_exists() {
    ProductDetail expected = productDetail();
    template.insert(
        CatalogProductMongoMapper.toDocument(expected, Instant.parse("2026-01-01T00:00:00Z"), 2));

    assertThat(adapter.findBySku(expected.sku())).contains(expected);
  }

  @Test
  @DisplayName("Reseñas completas paginadas")
  void should_return_requested_review_page_when_product_has_reviews() {
    ProductDetail product = productDetail();
    template.insert(CatalogProductMongoMapper.toDocument(product, Instant.now(), 0));
    List<CatalogReview> reviews =
        List.of(review(product.id(), 1), review(product.id(), 2), review(product.id(), 3));
    template.insertAll(reviews);

    var result = adapter.findReviewsBySku(product.sku(), 1, 1).orElseThrow();

    assertThat(result.content())
        .containsExactly(
            new ProductReview(
                reviews.get(1).id(), "Ana", 5, "Review 2", reviews.get(1).createdAt()));
    assertThat(result.totalElements()).isEqualTo(3);
    assertThat(result.totalPages()).isEqualTo(3);
  }

  private ProductDetail productDetail() {
    UUID productId = UUID.randomUUID();
    return new ProductDetail(
        productId,
        "SKU-" + productId,
        "Camisa",
        189900,
        ProductStatus.ACTIVE,
        new Category(UUID.randomUUID(), "Camisas"),
        18,
        List.of(new ProductVariant(UUID.randomUUID(), "-M", "Azul", "M", 0)),
        Map.of("material", "Lino"),
        new ProductRating(new BigDecimal("4.67"), 1),
        List.of());
  }

  private CatalogReview review(UUID productId, int index) {
    return new CatalogReview(
        UUID.randomUUID(),
        productId,
        "Ana",
        5,
        "Review " + index,
        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index));
  }
}
