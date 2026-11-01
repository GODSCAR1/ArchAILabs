package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

  private final CreateProductUseCase createProductUseCase;
  private final EditProductUseCase editProductUseCase;
  private final ProductSearchUseCase productSearchUseCase;

  public ProductController(
      CreateProductUseCase createProductUseCase,
      EditProductUseCase editProductUseCase,
      ProductSearchUseCase productSearchUseCase) {
    this.createProductUseCase = createProductUseCase;
    this.editProductUseCase = editProductUseCase;
    this.productSearchUseCase = productSearchUseCase;
  }

  @PostMapping("/api/products")
  public ResponseEntity<CreateProductResponse> create(
      @Valid @RequestBody CreateProductRequest request) {
    Product product = createProductUseCase.create(request.name(), request.sku(), request.price());
    URI location = URI.create("/api/products/" + product.id());
    return ResponseEntity.created(location).body(ProductWebMapper.toCreateResponse(product));
  }

  @PutMapping("/api/products/{id}")
  public EditProductResponse edit(
      @PathVariable UUID id, @Valid @RequestBody EditProductRequest request) {
    ProductWebMapper.EditProductArguments arguments = ProductWebMapper.toEditArguments(id, request);
    Product product =
        editProductUseCase.edit(
            arguments.productId(), arguments.name(), arguments.price(), arguments.version());
    return ProductWebMapper.toEditResponse(product);
  }

  @GetMapping("/api/products")
  public ProductSearchResponse search(@RequestParam MultiValueMap<String, String> parameters) {
    ProductSearchPage result = productSearchUseCase.search(ProductWebMapper.toCriteria(parameters));
    return ProductWebMapper.toResponse(result);
  }
}
