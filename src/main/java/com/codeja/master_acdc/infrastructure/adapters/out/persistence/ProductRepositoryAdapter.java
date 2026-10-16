package com.codeja.master_acdc.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.domain.model.Product;
import com.codeja.master_acdc.domain.model.Sku;
import com.codeja.master_acdc.domain.port.ProductRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public class ProductRepositoryAdapter implements ProductRepository {

  private static final String UNIQUE_SKU_CONSTRAINT = "uq_products_sku";

  private final ProductJpaRepository productJpaRepository;

  public ProductRepositoryAdapter(ProductJpaRepository productJpaRepository) {
    this.productJpaRepository = productJpaRepository;
  }

  @Override
  public boolean existsBySku(Sku sku) {
    return productJpaRepository.existsBySku(sku.value());
  }

  @Override
  public void save(Product product) {
    try {
      productJpaRepository.saveAndFlush(ProductJpaEntity.from(product));
    } catch (DataIntegrityViolationException exception) {
      if (violatesUniqueSkuConstraint(exception)) {
        throw new DuplicateSkuException(product.sku().value(), exception);
      }
      throw exception;
    }
  }

  private boolean violatesUniqueSkuConstraint(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraintViolation) {
        return UNIQUE_SKU_CONSTRAINT.equals(constraintViolation.getConstraintName());
      }
    }
    return false;
  }
}
