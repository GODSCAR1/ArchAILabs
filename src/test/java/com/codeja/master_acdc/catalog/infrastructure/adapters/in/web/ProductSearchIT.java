package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ProductSearchIT {

  private static final UUID LAPTOP = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID PHONE = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID LAMP = UUID.fromString("20000000-0000-0000-0000-000000000003");
  private static final UUID DEFAULT_CATEGORY =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("delete from products");
    insertProduct(LAPTOP, "Laptop", 200, "ACTIVE");
    insertProduct(PHONE, "Phone", 100, "DRAFT");
    insertProduct(LAMP, "Lamp", 400, "DISCONTINUED");
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", LAPTOP, 4);
  }

  @Test
  @DisplayName("Consulta sin filtros")
  void should_return_default_page_when_search_has_no_filters() throws Exception {
    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @DisplayName("Tamaño por encima del máximo")
  void should_cap_size_when_search_size_is_above_maximum() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("size", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  @DisplayName("Texto sin distinguir mayúsculas")
  void should_find_laptop_when_text_is_uppercase() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("text", "LAP"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Laptop"));
  }

  @Test
  @DisplayName("Filtro de disponibilidad con stock positivo")
  void should_include_laptop_when_stock_is_positive() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("inStock", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(LAPTOP.toString()))
        .andExpect(jsonPath("$.content[0].stock").value(4));
  }

  @Test
  @DisplayName("Los seis filtros combinados")
  void should_apply_all_filters_when_all_six_are_present() throws Exception {
    mockMvc
        .perform(
            get("/api/products")
                .queryParam("text", "LAP")
                .queryParam("categoryId", DEFAULT_CATEGORY.toString())
                .queryParam("minPrice", "100")
                .queryParam("maxPrice", "500")
                .queryParam("status", "ACTIVE")
                .queryParam("inStock", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(LAPTOP.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("Ordenación por fecha de creación")
  void should_sort_by_created_at_when_created_at_is_requested() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("sort", "createdAt,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  @DisplayName("Estado repetido")
  void should_combine_repeated_status_values_when_status_is_repeated() throws Exception {
    mockMvc
        .perform(
            get("/api/products")
                .queryParam("status", "ACTIVE")
                .queryParam("status", "DISCONTINUED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("Producto recién creado visible en el catálogo")
  void should_show_created_product_with_defaults_when_product_is_created() throws Exception {
    String sku = "NEW-500";
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Laptop\",\"price\":500,\"sku\":\"" + sku + "\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/products").queryParam("status", "ACTIVE").queryParam("text", "New"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].categoryName").value("Sin categoría"));
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from products where sku = ?", String.class, sku))
        .isEqualTo("ACTIVE");
  }

  @Test
  void should_return_empty_page_when_search_matches_no_product() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("text", "missing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  private void insertProduct(UUID id, String name, long price, String status) {
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, ?, ?, ?, ?, ?, current_timestamp, 0)",
        id,
        name,
        "SKU-" + id,
        price,
        DEFAULT_CATEGORY,
        status);
  }
}
