package com.codeja.master_acdc.infrastructure.adapters.in.web;

import com.codeja.master_acdc.application.port.CreateProductUseCase;
import com.codeja.master_acdc.domain.model.Product;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

  private final CreateProductUseCase createProductUseCase;

  public ProductController(CreateProductUseCase createProductUseCase) {
    this.createProductUseCase = createProductUseCase;
  }

  @PostMapping("/api/products")
  public ResponseEntity<CreateProductResponse> create(
      @Valid @RequestBody CreateProductRequest request) {
    Product product = createProductUseCase.create(request.name(), request.sku(), request.price());
    URI location = URI.create("/api/products/" + product.id());
    return ResponseEntity.created(location).body(CreateProductResponse.from(product));
  }
}
