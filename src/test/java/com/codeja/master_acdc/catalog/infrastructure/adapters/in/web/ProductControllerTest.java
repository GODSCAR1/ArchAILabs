package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductReviewsUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidProductException;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import com.codeja.master_acdc.identity.infrastructure.config.HttpSecurityConfiguration;
import com.codeja.master_acdc.identity.infrastructure.config.JwtConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ProductController.class, ProductDetailController.class})
@Import({HttpSecurityConfiguration.class, JwtConfiguration.class})
class ProductControllerTest {
  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private com.codeja.master_acdc.identity.domain.port.SessionVersions versions;

  private static final String INVALID_SEARCH_PARAMETER_TYPE =
      "https://api.master-acdc.dev/problems/invalid-search-parameter";

  @Autowired MockMvc mockMvc;

  @MockitoBean CreateProductUseCase createProductUseCase;
  @MockitoBean EditProductUseCase editProductUseCase;
  @MockitoBean ProductDetailUseCase productDetailUseCase;
  @MockitoBean ProductReviewsUseCase productReviewsUseCase;
  @MockitoBean ProductSearchUseCase productSearchUseCase;

  @Test
  void should_return_201_with_location_when_valid_request() throws Exception {
    given(createProductUseCase.create(any(), any(), any()))
        .willReturn(
            Product.create(
                new ProductName("Cafe"), new Sku("CAF-500"), Price.of(new BigDecimal("100"))));

    mockMvc
        .perform(
            post("/api/products")
                .with(jwt().authorities(new SimpleGrantedAuthority("product:write")))
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
                .with(jwt().authorities(new SimpleGrantedAuthority("product:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cafe\",\"sku\":\"CAF-500\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void should_return_400_problem_when_body_is_malformed() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .with(jwt().authorities(new SimpleGrantedAuthority("product:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
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
                .with(jwt().authorities(new SimpleGrantedAuthority("product:write")))
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
                .with(jwt().authorities(new SimpleGrantedAuthority("product:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cafe\",\"price\":100,\"sku\":\"CAF-500\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  @DisplayName("Edición UUID y consulta SKU coexistentes")
  void should_route_put_to_edition_and_get_to_detail_when_path_is_uuid_or_sku() throws Exception {
    UUID productId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    String uuidShapedSku = productId.toString();
    BigDecimal price = new BigDecimal("125");
    given(editProductUseCase.edit(productId, "Cafe nuevo", price, 0L))
        .willReturn(
            Product.create(new ProductName("Cafe nuevo"), new Sku("SKU-500"), Price.of(price)));
    given(productDetailUseCase.findBySku(uuidShapedSku))
        .willThrow(new ProductNotFoundException(uuidShapedSku));
    given(productSearchUseCase.search(any())).willReturn(page());

    mockMvc
        .perform(
            put("/api/products/{id}", productId)
                .with(jwt().authorities(new SimpleGrantedAuthority("product:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cafe nuevo\",\"price\":125,\"version\":0}"))
        .andExpect(status().isOk());
    verify(editProductUseCase).edit(productId, "Cafe nuevo", price, 0L);

    mockMvc.perform(get("/api/products/{sku}", uuidShapedSku)).andExpect(status().isNotFound());

    mockMvc.perform(get("/api/products")).andExpect(status().isOk());
    verify(productSearchUseCase).search(any());
  }

  @Test
  @DisplayName("Consulta sin filtros")
  void should_return_product_page_when_search_has_no_filters() throws Exception {
    given(productSearchUseCase.search(any())).willReturn(page());

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.content[0].name").value("Laptop"));
  }

  @Test
  @DisplayName("Tamaño por encima del máximo")
  void should_cap_page_size_when_search_size_exceeds_maximum() throws Exception {
    given(productSearchUseCase.search(any())).willReturn(page());

    mockMvc.perform(get("/api/products").queryParam("size", "1000")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Rango de precio invertido")
  void should_return_invalid_price_range_problem_when_range_is_inverted() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("minPrice", "5001").queryParam("maxPrice", "5000"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-price-range"));
  }

  @Test
  @DisplayName("Campo de orden no permitido")
  void should_return_invalid_sort_field_problem_when_sort_field_is_not_allowed() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("sort", "sku,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-sort-field"));
  }

  @Test
  void should_return_400_problem_when_status_is_not_recognized() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("status", "UNKNOWN"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  @Test
  void should_return_400_problem_when_category_id_is_not_uuid() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("categoryId", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  @Test
  void should_return_400_problem_when_min_price_is_not_numeric() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("minPrice", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  @Test
  void should_return_400_problem_when_max_price_is_not_numeric() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("maxPrice", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  @Test
  void should_return_400_problem_when_page_is_not_numeric() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  @Test
  void should_return_400_problem_when_size_is_not_numeric() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("size", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  @Test
  void should_return_400_problem_when_page_is_negative() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  @Test
  void should_return_400_problem_when_size_is_zero() throws Exception {
    mockMvc
        .perform(get("/api/products").queryParam("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(INVALID_SEARCH_PARAMETER_TYPE));
  }

  private static ProductSearchPage page() {
    return new ProductSearchPage(
        List.of(
            new ProductSearchRow(
                UUID.randomUUID(), "Laptop", 200, 4, "Sin categoría", ProductStatus.ACTIVE)),
        0,
        20,
        1,
        1);
  }
}
