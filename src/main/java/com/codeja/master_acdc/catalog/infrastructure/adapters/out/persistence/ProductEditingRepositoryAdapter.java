package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.domain.exception.ConcurrentProductModificationException;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

public class ProductEditingRepositoryAdapter implements ProductEditingRepository {

  private final ProductJpaRepository productJpaRepository;
  private final EntityManager entityManager;

  public ProductEditingRepositoryAdapter(
      ProductJpaRepository productJpaRepository, EntityManager entityManager) {
    this.productJpaRepository = productJpaRepository;
    this.entityManager = entityManager;
  }

  @Override
  public Optional<Product> findById(UUID productId) {
    return productJpaRepository.findById(productId).map(this::detachAndMap);
  }

  @Override
  public Product save(Product product) {
    try {
      ProductJpaEntity entity = ProductPersistenceMapper.toEntity(product);
      ProductJpaEntity persisted = productJpaRepository.saveAndFlush(entity);
      return ProductPersistenceMapper.toDomain(persisted);
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new ConcurrentProductModificationException(product.id());
    }
  }

  private Product detachAndMap(ProductJpaEntity entity) {
    Product product = ProductPersistenceMapper.toDomain(entity);
    entityManager.detach(entity);
    return product;
  }
}
