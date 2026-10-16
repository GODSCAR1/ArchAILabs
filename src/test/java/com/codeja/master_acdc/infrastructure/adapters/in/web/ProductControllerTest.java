package com.codeja.master_acdc.infrastructure.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.application.port.CreateProductUseCase;
import com.codeja.master_acdc.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.domain.exception.InvalidProductException;
import com.codeja.master_acdc.domain.model.Price;
import com.codeja.master_acdc.domain.model.Product;
import com.codeja.master_acdc.domain.model.ProductName;
import com.codeja.master_acdc.domain.model.Sku;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateProductUseCase createProductUseCase;

  @Test
  void should_return_201_with_location_when_valid_request() throws Exception {
    given(createProductUseCase.create(any(), any(), any()))
        .willReturn(
            Product.create(
                new ProductName("Cafe"), new Sku("CAF-500"), Price.of(new BigDecimal("100"))));

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cafe\",\"price\":100,\"sku\":\"caf-500\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("/api/products/")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.sku").value("CAF-500"));
  }

  @Test
  void should_return_400_problem_when_price_is_missing() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cafe\",\"sku\":\"CAF-500\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void should_return_400_problem_when_body_is_malformed() throws Exception {
    mockMvc
        .perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void should_return_400_problem_when_domain_rejects_input() throws Exception {
    given(createProductUseCase.create(any(), any(), any()))
        .willThrow(new InvalidProductException("sku must match ^[A-Z0-9-]{3,64}$"));

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cafe\",\"price\":100,\"sku\":\"AB\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void should_return_409_problem_when_sku_already_exists() throws Exception {
    given(createProductUseCase.create(any(), any(), any()))
        .willThrow(new DuplicateSkuException("CAF-500"));

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cafe\",\"price\":100,\"sku\":\"CAF-500\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
