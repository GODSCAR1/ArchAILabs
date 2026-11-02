package com.codeja.master_acdc.catalog.infrastructure.config;

import com.codeja.master_acdc.catalog.application.CreateProductService;
import com.codeja.master_acdc.catalog.application.EditProductService;
import com.codeja.master_acdc.catalog.application.ProductDetailService;
import com.codeja.master_acdc.catalog.application.ProductSearchService;
import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductReviewsUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogDetailMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductEditingMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductSearchMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductDetailRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductEditingRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductJpaRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductSearchRepositoryAdapter;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ProductConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "catalog",
      name = "store",
      havingValue = "postgres",
      matchIfMissing = true)
  public ProductRepository productRepository(ProductJpaRepository productJpaRepository) {
    return new ProductRepositoryAdapter(productJpaRepository);
  }

  @Bean
  public CreateProductUseCase createProductUseCase(ProductRepository productRepository) {
    return new CreateProductService(productRepository);
  }

  @Bean
  @ConditionalOnProperty(prefix = "catalog", name = "store", havingValue = "mongo")
  public ProductRepository mongoProductRepository(MongoTemplate mongoTemplate) {
    return new ProductMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "catalog",
      name = "store",
      havingValue = "postgres",
      matchIfMissing = true)
  public ProductEditingRepository productEditingRepository(
      ProductJpaRepository productJpaRepository, EntityManager entityManager) {
    return new ProductEditingRepositoryAdapter(productJpaRepository, entityManager);
  }

  @Bean
  @ConditionalOnProperty(prefix = "catalog", name = "store", havingValue = "mongo")
  public ProductEditingRepository mongoProductEditingRepository(MongoTemplate mongoTemplate) {
    return new ProductEditingMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  public EditProductUseCase editProductUseCase(
      ProductEditingRepository productEditingRepository,
      PlatformTransactionManager transactionManager) {
    EditProductService delegate = new EditProductService(productEditingRepository);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return (productId, name, price, version) ->
        transactionTemplate.execute(status -> delegate.edit(productId, name, price, version));
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "catalog",
      name = "store",
      havingValue = "postgres",
      matchIfMissing = true)
  public ProductSearchRepository productSearchRepository(
      ProductJpaRepository productJpaRepository) {
    return new ProductSearchRepositoryAdapter(productJpaRepository);
  }

  @Bean
  @ConditionalOnProperty(prefix = "catalog", name = "store", havingValue = "mongo")
  public ProductSearchRepository mongoProductSearchRepository(MongoTemplate mongoTemplate) {
    return new ProductSearchMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  public ProductSearchUseCase productSearchUseCase(
      ProductSearchRepository productSearchRepository) {
    return new ProductSearchService(productSearchRepository);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "catalog",
      name = "store",
      havingValue = "postgres",
      matchIfMissing = true)
  public ProductDetailRepository productDetailRepository(EntityManager entityManager) {
    return new ProductDetailRepositoryAdapter(entityManager);
  }

  @Bean
  @ConditionalOnProperty(prefix = "catalog", name = "store", havingValue = "mongo")
  public ProductDetailRepository mongoProductDetailRepository(MongoTemplate mongoTemplate) {
    return new CatalogDetailMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  public ProductDetailUseCase productDetailUseCase(
      ProductDetailRepository repository, PlatformTransactionManager transactionManager) {
    ProductDetailService delegate = new ProductDetailService(repository);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return sku -> transactionTemplate.execute(status -> delegate.findBySku(sku));
  }

  @Bean
  public ProductReviewsUseCase productReviewsUseCase(
      ProductDetailRepository repository, PlatformTransactionManager transactionManager) {
    ProductDetailService delegate = new ProductDetailService(repository);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return (sku, page, size) ->
        transactionTemplate.execute(status -> delegate.findBySku(sku, page, size));
  }
}
