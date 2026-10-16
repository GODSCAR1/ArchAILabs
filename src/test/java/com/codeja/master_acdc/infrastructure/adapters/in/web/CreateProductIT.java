package com.codeja.master_acdc.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreateProductIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from products");
  }

  @Test
  void should_return_201_with_location_and_normalized_sku_when_valid_product() throws Exception {
    postProduct(body("Cafe Huila 500 g", "32000", " caf-500 "))
        .andExpect(status().isCreated())
        .andExpect(
            header().string(HttpHeaders.LOCATION, matchesPattern("/api/products/[0-9a-f-]{36}")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Cafe Huila 500 g"))
        .andExpect(jsonPath("$.price").value(32000))
        .andExpect(jsonPath("$.sku").value("CAF-500"));

    assertThat(rowsWithSku("CAF-500")).isEqualTo(1);
  }

  @Test
  void should_return_400_when_sku_has_two_characters() throws Exception {
    postProduct(body("Cafe", "100", "AB"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_return_201_when_sku_has_three_characters() throws Exception {
    postProduct(body("Cafe", "100", "ABC")).andExpect(status().isCreated());

    assertThat(rowsWithSku("ABC")).isEqualTo(1);
  }

  @Test
  void should_return_201_when_sku_has_sixty_four_characters() throws Exception {
    String sku = "A".repeat(64);

    postProduct(body("Cafe", "100", sku)).andExpect(status().isCreated());

    assertThat(rowsWithSku(sku)).isEqualTo(1);
  }

  @Test
  void should_return_400_when_sku_has_sixty_five_characters() throws Exception {
    postProduct(body("Cafe", "100", "A".repeat(65)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_return_400_when_sku_contains_underscore() throws Exception {
    postProduct(body("Cafe", "100", "CAF_500"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_return_409_when_sku_already_exists_ignoring_case() throws Exception {
    postProduct(body("Cafe", "100", "CAF-500")).andExpect(status().isCreated());

    postProduct(body("Cafe", "100", "caf-500"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(rowsWithSku("CAF-500")).isEqualTo(1);
  }

  @Test
  void should_return_201_when_name_has_one_hundred_twenty_characters() throws Exception {
    postProduct(body("a".repeat(120), "100", "ABC")).andExpect(status().isCreated());

    assertThat(productCount()).isEqualTo(1);
  }

  @Test
  void should_return_400_when_name_has_one_hundred_twenty_one_characters() throws Exception {
    postProduct(body("a".repeat(121), "100", "ABC"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_return_400_when_name_is_blank() throws Exception {
    postProduct(body("   ", "100", "ABC"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_trim_name_when_name_has_surrounding_spaces() throws Exception {
    postProduct(body("  Cafe  ", "100", "ABC")).andExpect(status().isCreated());

    assertThat(
            jdbcTemplate.queryForObject(
                "select name from products where sku = ?", String.class, "ABC"))
        .isEqualTo("Cafe");
  }

  @Test
  void should_return_201_when_price_is_one() throws Exception {
    postProduct(body("Cafe", "1", "ABC")).andExpect(status().isCreated());

    assertThat(
            jdbcTemplate.queryForObject(
                "select price from products where sku = ?", Long.class, "ABC"))
        .isEqualTo(1L);
  }

  @Test
  void should_return_400_when_price_is_zero() throws Exception {
    postProduct(body("Cafe", "0", "ABC"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_return_400_when_price_is_negative() throws Exception {
    postProduct(body("Cafe", "-5", "ABC"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_return_400_when_price_has_decimals() throws Exception {
    postProduct(body("Cafe", "1500.5", "ABC"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  @Test
  void should_return_400_when_price_is_missing() throws Exception {
    postProduct("{\"name\":\"Cafe\",\"sku\":\"ABC\"}")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    assertThat(productCount()).isZero();
  }

  private ResultActions postProduct(String json) throws Exception {
    return mockMvc.perform(
        post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private int productCount() {
    return jdbcTemplate.queryForObject("select count(*) from products", Integer.class);
  }

  private int rowsWithSku(String sku) {
    return jdbcTemplate.queryForObject(
        "select count(*) from products where sku = ?", Integer.class, sku);
  }

  private static String body(String name, String price, String sku) {
    return "{\"name\":\"" + name + "\",\"price\":" + price + ",\"sku\":\"" + sku + "\"}";
  }
}
