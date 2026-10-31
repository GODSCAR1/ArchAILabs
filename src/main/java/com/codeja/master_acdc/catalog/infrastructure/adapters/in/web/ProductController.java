package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.exception.InvalidPriceRangeException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidSortFieldException;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

  private final CreateProductUseCase createProductUseCase;
  private final ProductSearchUseCase productSearchUseCase;

  public ProductController(
      CreateProductUseCase createProductUseCase, ProductSearchUseCase productSearchUseCase) {
    this.createProductUseCase = createProductUseCase;
    this.productSearchUseCase = productSearchUseCase;
  }

  @PostMapping("/api/products")
  public ResponseEntity<CreateProductResponse> create(
      @Valid @RequestBody CreateProductRequest request) {
    Product product = createProductUseCase.create(request.name(), request.sku(), request.price());
    URI location = URI.create("/api/products/" + product.id());
    return ResponseEntity.created(location).body(CreateProductResponse.from(product));
  }

  @GetMapping("/api/products")
  public ProductSearchResponse search(@RequestParam MultiValueMap<String, String> parameters) {
    ProductSearchPage result = productSearchUseCase.search(toCriteria(parameters));
    return ProductSearchResponse.from(result);
  }

  private ProductSearchCriteria toCriteria(MultiValueMap<String, String> parameters) {
    Long minPrice = longParameter(parameters, "minPrice");
    Long maxPrice = longParameter(parameters, "maxPrice");
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
      throw new InvalidPriceRangeException();
    }
    return new ProductSearchCriteria(
        parameters.getFirst("text"),
        uuidParameter(parameters, "categoryId"),
        minPrice,
        maxPrice,
        statuses(parameters),
        Boolean.parseBoolean(parameters.getFirst("inStock")),
        integerParameter(parameters, "page", 0),
        Math.min(integerParameter(parameters, "size", 20), 100),
        sort(parameters.getFirst("sort")));
  }

  private ProductSearchOrder sort(String rawSort) {
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
    if (parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].toLowerCase())) {
      throw new InvalidSortFieldException(parts[1]);
    }
    return new ProductSearchOrder(parts[0], direction);
  }

  private Set<ProductStatus> statuses(MultiValueMap<String, String> parameters) {
    return parameters.getOrDefault("status", List.of()).stream()
        .map(ProductStatus::valueOf)
        .collect(java.util.stream.Collectors.toSet());
  }

  private Long longParameter(MultiValueMap<String, String> parameters, String name) {
    String value = parameters.getFirst(name);
    return value == null ? null : Long.valueOf(value);
  }

  private UUID uuidParameter(MultiValueMap<String, String> parameters, String name) {
    String value = parameters.getFirst(name);
    return value == null ? null : UUID.fromString(value);
  }

  private int integerParameter(
      MultiValueMap<String, String> parameters, String name, int fallback) {
    String value = parameters.getFirst(name);
    return value == null ? fallback : Integer.parseInt(value);
  }
}
