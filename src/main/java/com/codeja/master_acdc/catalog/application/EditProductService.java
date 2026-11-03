package com.codeja.master_acdc.catalog.application;

import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.domain.event.ProductChanged;
import com.codeja.master_acdc.catalog.domain.exception.ConcurrentProductModificationException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidProductException;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import java.math.BigDecimal;
import java.util.UUID;

public class EditProductService implements EditProductUseCase {

  private final ProductEditingRepository productRepository;
  private final ProductChangedPublisher productChangedPublisher;

  public EditProductService(
      ProductEditingRepository productRepository, ProductChangedPublisher productChangedPublisher) {
    this.productRepository = productRepository;
    this.productChangedPublisher = productChangedPublisher;
  }

  @Override
  public Product edit(UUID productId, String name, BigDecimal price, Long version) {
    ProductName productName = new ProductName(name);
    Price productPrice = Price.of(price);
    if (version == null) {
      throw new InvalidProductException("version must not be null");
    }
    Product current =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    if (current.version() != version) {
      throw new ConcurrentProductModificationException(productId);
    }
    Product edited = current.edit(productName, productPrice);
    Product saved = productRepository.save(edited);
    productChangedPublisher.publish(new ProductChanged(saved.sku().value()));
    return saved;
  }
}
