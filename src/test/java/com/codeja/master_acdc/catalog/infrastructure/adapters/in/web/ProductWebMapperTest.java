package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.catalog.domain.exception.InvalidSortFieldException;
import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class ProductWebMapperTest {

  private static final UUID CATEGORY_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID PRODUCT_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");

  @Test
  void should_map_edit_request_when_values_are_valid() {
    EditProductRequest request = new EditProductRequest("Cafe", new BigDecimal("250"), 3L);

    ProductWebMapper.EditProductArguments arguments =
        ProductWebMapper.toEditArguments(PRODUCT_ID, request);

    assertThat(arguments.productId()).isEqualTo(PRODUCT_ID);
    assertThat(arguments.name()).isEqualTo("Cafe");
    assertThat(arguments.price()).isEqualByComparingTo("250");
    assertThat(arguments.version()).isEqualTo(3L);
  }

  @Test
  void should_map_product_to_edit_response_when_product_is_valid() {
    Product product = product();

    EditProductResponse response = ProductWebMapper.toEditResponse(product);

    assertThat(response)
        .isEqualTo(new EditProductResponse(product.id(), "Cafe", 100, product.version()));
  }

  @Test
  void should_include_version_when_mapping_product_creation_response() {
    Product product = product();

    CreateProductResponse response = ProductWebMapper.toCreateResponse(product);

    assertThat(response)
        .isEqualTo(new CreateProductResponse(product.id(), "Cafe", 100, "CAF-500", 0));
  }

  @Test
  void should_map_search_parameters_when_values_are_valid() {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("text", "lap");
    parameters.add("categoryId", CATEGORY_ID.toString());
    parameters.add("minPrice", "100");
    parameters.add("maxPrice", "500");
    parameters.add("status", "ACTIVE");
    parameters.add("status", "DRAFT");
    parameters.add("inStock", "true");
    parameters.add("page", "2");
    parameters.add("size", "120");
    parameters.add("sort", "price,desc");

    ProductSearchCriteria criteria = ProductWebMapper.toCriteria(parameters);

    assertThat(criteria.text()).isEqualTo("lap");
    assertThat(criteria.categoryId()).isEqualTo(CATEGORY_ID);
    assertThat(criteria.minPrice()).isEqualTo(100L);
    assertThat(criteria.maxPrice()).isEqualTo(500L);
    assertThat(criteria.statuses())
        .containsExactlyInAnyOrder(ProductStatus.ACTIVE, ProductStatus.DRAFT);
    assertThat(criteria.inStock()).isTrue();
    assertThat(criteria.page()).isEqualTo(2);
    assertThat(criteria.size()).isEqualTo(100);
    assertThat(criteria.order())
        .isEqualTo(new ProductSearchOrder("price", ProductSearchOrder.Direction.DESC));
  }

  @Test
  void should_map_search_page_when_result_is_valid() {
    ProductSearchPage page =
        new ProductSearchPage(
            List.of(
                new ProductSearchRow(PRODUCT_ID, "Laptop", 200, 3, "Office", ProductStatus.ACTIVE)),
            1,
            20,
            21,
            2);

    ProductSearchResponse response = ProductWebMapper.toResponse(page);

    assertThat(response.content())
        .containsExactly(new ProductSummaryResponse(PRODUCT_ID, "Laptop", 200, 3, "Office"));
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(20);
    assertThat(response.totalElements()).isEqualTo(21);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void should_reject_status_when_status_is_unknown() {
    assertInvalidParameter("status", "UNKNOWN");
  }

  @Test
  void should_reject_category_id_when_category_id_is_not_uuid() {
    assertInvalidParameter("categoryId", "invalid");
  }

  @Test
  void should_reject_min_price_when_min_price_is_not_numeric() {
    assertInvalidParameter("minPrice", "invalid");
  }

  @Test
  void should_reject_max_price_when_max_price_is_not_numeric() {
    assertInvalidParameter("maxPrice", "invalid");
  }

  @Test
  void should_reject_page_when_page_is_not_numeric() {
    assertInvalidParameter("page", "invalid");
  }

  @Test
  void should_reject_size_when_size_is_not_numeric() {
    assertInvalidParameter("size", "invalid");
  }

  @Test
  void should_reject_page_when_page_is_negative() {
    assertInvalidParameter("page", "-1");
  }

  @Test
  void should_reject_size_when_size_is_zero() {
    assertInvalidParameter("size", "0");
  }

  @Test
  void should_reject_sort_when_sort_field_is_not_allowed() {
    MultiValueMap<String, String> parameters = parameters("sort", "sku,asc");

    assertThatThrownBy(() -> ProductWebMapper.toCriteria(parameters))
        .isInstanceOf(InvalidSortFieldException.class);
  }

  private void assertInvalidParameter(String name, String value) {
    assertThatThrownBy(() -> ProductWebMapper.toCriteria(parameters(name, value)))
        .isInstanceOf(InvalidSearchParameterException.class)
        .extracting(throwable -> ((InvalidSearchParameterException) throwable).parameter())
        .isEqualTo(name);
  }

  private MultiValueMap<String, String> parameters(String name, String value) {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add(name, value);
    return parameters;
  }

  private static Product product() {
    return Product.create(
        new ProductName("Cafe"), new Sku("CAF-500"), Price.of(new BigDecimal("100")));
  }
}
