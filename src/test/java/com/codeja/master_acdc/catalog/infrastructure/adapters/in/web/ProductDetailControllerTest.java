package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductReviewsUseCase;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.identity.infrastructure.config.HttpSecurityConfiguration;
import com.codeja.master_acdc.identity.infrastructure.config.JwtConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductDetailController.class)
@Import({HttpSecurityConfiguration.class, JwtConfiguration.class})
class ProductDetailControllerTest {
  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private com.codeja.master_acdc.identity.domain.port.SessionVersions versions;

  @Autowired MockMvc mockMvc;

  @MockitoBean ProductDetailUseCase productDetailUseCase;
  @MockitoBean ProductReviewsUseCase productReviewsUseCase;

  @Test
  @DisplayName("Ficha existente con todos sus datos")
  void should_return_complete_detail_when_product_exists() throws Exception {
    given(productDetailUseCase.findBySku("SKU-CAFE")).willReturn(detail());

    mockMvc
        .perform(get("/api/products/SKU-CAFE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("SKU-CAFE"))
        .andExpect(jsonPath("$.rating.count").value(3));
  }

  @Test
  @DisplayName("Producto sin stock ni datos opcionales")
  void should_return_empty_collections_and_zero_rating_when_detail_has_no_optional_data()
      throws Exception {
    given(productDetailUseCase.findBySku("SKU-EMPTY")).willReturn(emptyDetail());

    mockMvc
        .perform(get("/api/products/SKU-EMPTY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(0))
        .andExpect(jsonPath("$.rating.average").value(0))
        .andExpect(jsonPath("$.rating.count").value(0));
  }

  @Test
  @DisplayName("Límite de reseñas recientes")
  void should_return_recent_review_limit_when_detail_has_many_reviews() throws Exception {
    given(productDetailUseCase.findBySku("SKU-CAFE")).willReturn(detail());

    mockMvc
        .perform(get("/api/products/SKU-CAFE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentReviews.length()").value(0));
  }

  @Test
  @DisplayName("Paginación de reseñas inválida")
  void should_return_bad_request_when_review_pagination_is_invalid() throws Exception {
    mockMvc
        .perform(get("/api/products/SKU-CAFE/reviews").queryParam("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/invalid-search-parameter"));

    mockMvc
        .perform(get("/api/products/SKU-CAFE/reviews").queryParam("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/invalid-search-parameter"));
  }

  @Test
  @DisplayName("Primera página de reseñas")
  void should_return_reviews_page_when_product_exists() throws Exception {
    given(productReviewsUseCase.findBySku("SKU-CAFE", 0, 20))
        .willReturn(
            new com.codeja.master_acdc.catalog.domain.model.ProductReviewPage(
                List.of(), 0, 20, 0, 0));

    mockMvc.perform(get("/api/products/SKU-CAFE/reviews")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Reseñas paginadas")
  void should_return_requested_reviews_page_when_page_number_is_supplied() throws Exception {
    given(productReviewsUseCase.findBySku("SKU-CAFE", 1, 10))
        .willReturn(new ProductReviewPage(List.of(), 1, 10, 11, 2));

    mockMvc
        .perform(
            get("/api/products/SKU-CAFE/reviews").queryParam("page", "1").queryParam("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10));
  }

  @Test
  @DisplayName("Tamaño superior al máximo")
  void should_return_reviews_page_when_size_exceeds_maximum() throws Exception {
    given(productReviewsUseCase.findBySku("SKU-CAFE", 0, 1000))
        .willReturn(new ProductReviewPage(List.of(), 0, 100, 0, 0));

    mockMvc
        .perform(get("/api/products/SKU-CAFE/reviews").queryParam("size", "1000"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Desempate de reseñas")
  void should_return_reviews_in_stable_order_when_dates_are_equal() throws Exception {
    given(productReviewsUseCase.findBySku("SKU-CAFE", 0, 20))
        .willReturn(new ProductReviewPage(List.of(), 0, 20, 2, 1));

    mockMvc.perform(get("/api/products/SKU-CAFE/reviews")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("SKU inexistente en ficha")
  void should_return_not_found_problem_when_detail_sku_does_not_exist() throws Exception {
    given(productDetailUseCase.findBySku("SKU-MISSING"))
        .willThrow(new ProductNotFoundException("SKU-MISSING"));

    mockMvc
        .perform(get("/api/products/SKU-MISSING"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
  }

  @Test
  @DisplayName("SKU inexistente en reseñas")
  void should_return_not_found_problem_when_review_sku_does_not_exist() throws Exception {
    given(productReviewsUseCase.findBySku("SKU-MISSING", 0, 20))
        .willThrow(new ProductNotFoundException("SKU-MISSING"));

    mockMvc
        .perform(get("/api/products/SKU-MISSING/reviews"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/product-not-found"));
  }

  private ProductDetail detail() {
    return new ProductDetail(
        UUID.randomUUID(),
        "SKU-CAFE",
        "Cafe",
        100,
        ProductStatus.ACTIVE,
        new Category(UUID.randomUUID(), "Food"),
        3,
        List.of(),
        Map.of("origin", "Colombia"),
        new ProductRating(new BigDecimal("4.33"), 3),
        List.of());
  }

  private ProductDetail emptyDetail() {
    return new ProductDetail(
        UUID.randomUUID(),
        "SKU-EMPTY",
        "Empty",
        100,
        ProductStatus.ACTIVE,
        new Category(UUID.randomUUID(), "Food"),
        0,
        List.of(),
        Map.of(),
        ProductRating.empty(),
        List.of());
  }
}
