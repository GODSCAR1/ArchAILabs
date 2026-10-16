package com.codeja.master_acdc.application;

import com.codeja.master_acdc.application.port.CreateProductUseCase;
import com.codeja.master_acdc.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.domain.model.Price;
import com.codeja.master_acdc.domain.model.Product;
import com.codeja.master_acdc.domain.model.ProductName;
import com.codeja.master_acdc.domain.model.Sku;
import com.codeja.master_acdc.domain.port.ProductRepository;
import java.math.BigDecimal;

public class CreateProductService implements CreateProductUseCase {

  private final ProductRepository productRepository;

  public CreateProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Override
  public Product create(String name, String sku, BigDecimal price) {
    ProductName productName = new ProductName(name);
    Sku productSku = new Sku(sku);
    Price productPrice = Price.of(price);
    if (productRepository.existsBySku(productSku)) {
      throw new DuplicateSkuException(productSku.value());
    }
    Product product = Product.create(productName, productSku, productPrice);
    productRepository.save(product);
    return product;
  }
}
