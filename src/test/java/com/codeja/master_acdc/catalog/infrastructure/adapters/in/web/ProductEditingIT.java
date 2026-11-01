package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductEditingIT {

  private static final String CONCURRENT_MODIFICATION_TYPE =
      "https://api.master-acdc.dev/problems/concurrent-modification";
  private static final String PRODUCT_NOT_FOUND_TYPE =
      "https://api.master-acdc.dev/problems/product-not-found";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from products");
  }

  @Test
  @DisplayName("Edición válida del producto")
  void should_return_200_and_persist_edited_values_when_request_is_valid() throws Exception {
    ProductView product = createProduct();

    mockMvc
        .perform(
            put("/api/products/{id}", product.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(" Updated ", 250, product.version())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(product.id().toString()))
        .andExpect(jsonPath("$.name").value("Updated"))
        .andExpect(jsonPath("$.price").value(250))
        .andExpect(jsonPath("$.version").value(1));

    assertThat(row(product.id()).get("name")).isEqualTo("Updated");
    assertThat(row(product.id()).get("price")).isEqualTo(250L);
  }

  @Test
  @DisplayName("Versión del cliente obsoleta")
  void should_return_409_and_keep_first_values_when_client_version_is_stale() throws Exception {
    ProductView product = createProduct();
    String firstBody = editBody("First", 200, product.version());

    mockMvc
        .perform(
            put("/api/products/{id}", product.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/products/{id}", product.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("Second", 300, product.version())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(CONCURRENT_MODIFICATION_TYPE));

    assertThat(row(product.id()).get("name")).isEqualTo("First");
    assertThat(row(product.id()).get("price")).isEqualTo(200L);
  }

  @Test
  @DisplayName("Producto inexistente")
  void should_return_404_and_not_create_product_when_id_does_not_exist() throws Exception {
    UUID productId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/products/{id}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("Missing", 200, 0)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(PRODUCT_NOT_FOUND_TYPE));

    assertThat(productCount()).isZero();
  }

  @Test
  @DisplayName("Nombre o precio inválido")
  void should_return_400_and_keep_product_when_name_is_invalid() throws Exception {
    ProductView product = createProduct();
    Map<String, Object> before = row(product.id());

    mockMvc
        .perform(
            put("/api/products/{id}", product.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(" ", 200, product.version())))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(row(product.id())).isEqualTo(before);
  }

  @Test
  @DisplayName("Versión ausente")
  void should_return_400_and_keep_product_when_version_is_missing() throws Exception {
    ProductView product = createProduct();
    Map<String, Object> before = row(product.id());

    mockMvc
        .perform(
            put("/api/products/{id}", product.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated\",\"price\":200}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(row(product.id())).isEqualTo(before);
  }

  @Test
  @DisplayName("Producto creado")
  void should_return_initial_version_when_product_is_created() throws Exception {
    mockMvc
        .perform(
            post("/api/products").contentType(MediaType.APPLICATION_JSON).content(createBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(0));
  }

  private ProductView createProduct() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/products").contentType(MediaType.APPLICATION_JSON).content(createBody()))
            .andExpect(status().isCreated())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    return new ProductView(
        UUID.fromString(jsonValue(body, "id")), Long.parseLong(jsonValue(body, "version")));
  }

  private Map<String, Object> row(UUID productId) {
    return jdbcTemplate.queryForMap(
        "select name, price, sku, category_id, status, created_at from products where id = ?",
        productId);
  }

  private int productCount() {
    return jdbcTemplate.queryForObject("select count(*) from products", Integer.class);
  }

  private static String createBody() {
    return "{\"name\":\"Original\",\"price\":100,\"sku\":\"ABC-123\"}";
  }

  private static String editBody(String name, long price, long version) {
    return "{\"name\":\"" + name + "\",\"price\":" + price + ",\"version\":" + version + "}";
  }

  private static String jsonValue(String body, String field) {
    String marker = "\"" + field + "\":";
    int valueStart = body.indexOf(marker) + marker.length();
    int valueEnd = body.indexOf(',', valueStart);
    if (valueEnd < 0) {
      valueEnd = body.indexOf('}', valueStart);
    }
    return body.substring(valueStart, valueEnd).replace("\"", "").trim();
  }

  private record ProductView(UUID id, long version) {}
}
