package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.config.CatalogMongoTestConfiguration;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CatalogMongoTestConfiguration.class)
@TestPropertySource(properties = "catalog.store=mongo")
class CatalogProductEditingMongoIT {

  private static final String SKU = "MONGO-101";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Autowired MockMvc mockMvc;
  @Autowired MongoTemplate mongoTemplate;

  @BeforeEach
  void clean_mongo_products() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
  }

  @Test
  @DisplayName("Producto creado")
  void should_create_product_with_initial_state_when_store_is_mongo() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(0));

    CatalogProduct product =
        mongoTemplate.findOne(
            Query.query(org.springframework.data.mongodb.core.query.Criteria.where("sku").is(SKU)),
            CatalogProduct.class);
    assertThat(product).isNotNull();
    assertThat(product.category().name()).isEqualTo("Sin categoría");
    assertThat(product.category().id())
        .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    assertThat(product.status().name()).isEqualTo("ACTIVE");
    assertThat(product.createdAt()).isNotNull();
    assertThat(product.version()).isZero();
  }

  @Test
  @DisplayName("SKU duplicado")
  void should_return_conflict_and_keep_existing_document_when_sku_is_duplicated() throws Exception {
    createProduct();
    CatalogProduct existing =
        mongoTemplate.findOne(
            Query.query(org.springframework.data.mongodb.core.query.Criteria.where("sku").is(SKU)),
            CatalogProduct.class);

    mockMvc
        .perform(
            post("/api/products")
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("about:blank"));

    assertThat(
            mongoTemplate.findOne(
                Query.query(
                    org.springframework.data.mongodb.core.query.Criteria.where("sku").is(SKU)),
                CatalogProduct.class))
        .isEqualTo(existing);
  }

  @Test
  @DisplayName("Edición válida del producto")
  void should_update_name_price_and_version_when_version_is_current() throws Exception {
    UUID id = createProduct();
    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("New name", 250, 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  @DisplayName("Versión del cliente obsoleta")
  void should_reject_stale_version_when_product_was_edited() throws Exception {
    UUID id = createProduct();
    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("First", 200, 0)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("Rejected", 300, 0)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/concurrent-modification"));
    CatalogProduct product = mongoTemplate.findById(id, CatalogProduct.class);
    assertThat(product.name()).isEqualTo("First");
    assertThat(product.price()).isEqualTo(200);
  }

  @Test
  @DisplayName("Nombre o precio inválido")
  void should_keep_product_unchanged_when_name_is_invalid() throws Exception {
    UUID id = createProduct();
    CatalogProduct before = mongoTemplate.findById(id, CatalogProduct.class);
    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(" ", 200, 0)))
        .andExpect(status().isBadRequest());
    assertThat(mongoTemplate.findById(id, CatalogProduct.class)).isEqualTo(before);
  }

  @Test
  @DisplayName("Versión ausente")
  void should_keep_product_unchanged_when_version_is_missing() throws Exception {
    UUID id = createProduct();
    CatalogProduct before = mongoTemplate.findById(id, CatalogProduct.class);
    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Changed\",\"price\":200}"))
        .andExpect(status().isBadRequest());
    assertThat(mongoTemplate.findById(id, CatalogProduct.class)).isEqualTo(before);
  }

  @Test
  @DisplayName("Producto inexistente")
  void should_return_not_found_when_editing_unknown_product() throws Exception {
    mockMvc
        .perform(
            put("/api/products/{id}", UUID.randomUUID())
                .with(catalogManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("Missing", 100, 0)))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
  }

  private UUID createProduct() throws Exception {
    MvcResult response =
        mockMvc
            .perform(
                post("/api/products")
                    .with(catalogManager())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody()))
            .andExpect(status().isCreated())
            .andReturn();
    String body = response.getResponse().getContentAsString();
    int idStart = body.indexOf("\"id\":\"") + 6;
    return UUID.fromString(body.substring(idStart, body.indexOf('"', idStart)));
  }

  private static String createBody() {
    return "{\"name\":\"Original\",\"price\":100,\"sku\":\"" + SKU + "\"}";
  }

  private static String editBody(String name, int price, int version) {
    return "{\"name\":\"" + name + "\",\"price\":" + price + ",\"version\":" + version + "}";
  }

  // Lab S12 (RBAC): editar catálogo exige product:write (rol CATALOG_MANAGER).
  private static RequestPostProcessor catalogManager() {
    return jwt()
        .authorities(
            new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"),
            new SimpleGrantedAuthority("product:write"));
  }
}
