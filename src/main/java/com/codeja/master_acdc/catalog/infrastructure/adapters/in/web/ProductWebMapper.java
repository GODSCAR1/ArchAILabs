package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.domain.exception.InvalidSortFieldException;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.MultiValueMap;

final class ProductWebMapper {

  private ProductWebMapper() {}

  static EditProductArguments toEditArguments(UUID productId, EditProductRequest request) {
    return new EditProductArguments(productId, request.name(), request.price(), request.version());
  }

  static EditProductResponse toEditResponse(Product product) {
    return new EditProductResponse(
        product.id(), product.name().value(), product.price().value(), product.version());
  }

  static CreateProductResponse toCreateResponse(Product product) {
    return new CreateProductResponse(
        product.id(),
        product.name().value(),
        product.price().value(),
        product.sku().value(),
        product.version());
  }

  static ProductSearchCriteria toCriteria(MultiValueMap<String, String> parameters) {
    Long minPrice = longParameter(parameters, "minPrice");
    Long maxPrice = longParameter(parameters, "maxPrice");
    int page = integerParameter(parameters, "page", 0);
    int size = integerParameter(parameters, "size", 20);
    validatePagination(page, size);
    return new ProductSearchCriteria(
        parameters.getFirst("text"),
        uuidParameter(parameters, "categoryId"),
        minPrice,
        maxPrice,
        statuses(parameters),
        Boolean.parseBoolean(parameters.getFirst("inStock")),
        page,
        Math.min(size, 100),
        sort(parameters.getFirst("sort")));
  }

  static ProductSearchResponse toResponse(ProductSearchPage result) {
    List<ProductSummaryResponse> content =
        result.content().stream().map(ProductWebMapper::toResponse).toList();
    return new ProductSearchResponse(
        content, result.page(), result.size(), result.totalElements(), result.totalPages());
  }

  private static ProductSummaryResponse toResponse(ProductSearchRow row) {
    return new ProductSummaryResponse(
        row.id(), row.name(), row.price(), row.stock(), row.categoryName());
  }

  static void validatePagination(int page, int size) {
    if (page < 0) {
      throw new InvalidSearchParameterException("page");
    }
    if (size < 1) {
      throw new InvalidSearchParameterException("size");
    }
  }

  private static ProductSearchOrder sort(String rawSort) {
    if (rawSort == null || rawSort.isBlank()) {
      return new ProductSearchOrder("createdAt", ProductSearchOrder.Direction.ASC);
    }
    String[] parts = rawSort.split(",", -1);
    if (parts.length > 2 || !Set.of("name", "price", "createdAt").contains(parts[0])) {
      throw new InvalidSortFieldException(parts[0]);
    }
    ProductSearchOrder.Direction direction =
        parts.length == 1 || "asc".equalsIgnoreCase(parts[1])
            ? ProductSearchOrder.Direction.ASC
            : ProductSearchOrder.Direction.DESC;
    if (parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].toLowerCase(Locale.ROOT))) {
      throw new InvalidSortFieldException(parts[1]);
    }
    return new ProductSearchOrder(parts[0], direction);
  }

  private static Set<ProductStatus> statuses(MultiValueMap<String, String> parameters) {
    try {
      return parameters.getOrDefault("status", List.of()).stream()
          .map(ProductStatus::valueOf)
          .collect(Collectors.toSet());
    } catch (IllegalArgumentException exception) {
      throw new InvalidSearchParameterException("status", exception);
    }
  }

  private static Long longParameter(MultiValueMap<String, String> parameters, String name) {
    String value = parameters.getFirst(name);
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException exception) {
      throw new InvalidSearchParameterException(name, exception);
    }
  }

  private static UUID uuidParameter(MultiValueMap<String, String> parameters, String name) {
    String value = parameters.getFirst(name);
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidSearchParameterException(name, exception);
    }
  }

  private static int integerParameter(
      MultiValueMap<String, String> parameters, String name, int fallback) {
    String value = parameters.getFirst(name);
    if (value == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new InvalidSearchParameterException(name, exception);
    }
  }

  record EditProductArguments(
      UUID productId, String name, java.math.BigDecimal price, Long version) {}
}
