package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.CatalogManagerToken;
import com.codeja.master_acdc.GlobalMongoClientTestConfiguration;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CategoryRef;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.RatingSummary;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(GlobalMongoClientTestConfiguration.class)
class ProductSearchMongoIT {

  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MongoTemplate mongoTemplate;

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenIssuer tokenIssuer;

  @BeforeEach
  void clear_catalog_products() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    mongoTemplate.insert(product(UUID.randomUUID(), "HTTP-LAPTOP", "Laptop", 120));
  }

  @Test
  @DisplayName("Consulta sin filtros")
  void should_return_products_when_search_has_no_filters() throws Exception {
    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Laptop"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("Filtro de texto categoría precio y estados")
  void should_filter_products_when_search_filters_are_present() throws Exception {
    mockMvc
        .perform(
            get("/api/products")
                .queryParam("text", "lap")
                .queryParam("categoryId", CATEGORY_ID.toString())
                .queryParam("minPrice", "100")
                .queryParam("maxPrice", "200")
                .queryParam("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Laptop"));
  }

  @Test
  @DisplayName("Estado repetido")
  void should_accept_repeated_status_filters_when_searching() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("status", "ACTIVE").queryParam("status", "DRAFT"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Producto recién creado visible en el catálogo")
  void should_show_created_product_when_product_is_created() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .header("Authorization", "Bearer " + CatalogManagerToken.issue(tokenIssuer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Creado en Mongo\",\"price\":50,\"sku\":\"HTTP-NEW\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/products").queryParam("text", "Creado"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Creado en Mongo"));
  }

  @Test
  @DisplayName("Texto sin distinguir mayúsculas")
  void should_match_text_without_case_sensitivity_when_searching() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("text", "LAPTOP"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Laptop"));
  }

  @Test
  @DisplayName("Ordenación por fecha de creación")
  void should_sort_by_creation_time_when_requested() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("sort", "createdAt,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Laptop"));
  }

  private CatalogProduct product(UUID id, String sku, String name, long price) {
    return new CatalogProduct(
        id,
        sku,
        name,
        price,
        ProductStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        0,
        new CategoryRef(CATEGORY_ID, "Sin categoría"),
        List.of(),
        Map.of(),
        3,
        new RatingSummary(BigDecimal.ZERO.setScale(2), 0),
        List.of());
  }
}
