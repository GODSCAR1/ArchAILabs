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
import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.in.web.AuthorizedCreateProductUseCase;
import com.codeja.master_acdc.catalog.infrastructure.adapters.in.web.AuthorizedEditProductUseCase;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.CachedProductDetailUseCase;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.events.SpringProductChangedPublisher;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogDetailMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogIndexInitializer;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogSchemaValidator;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductEditingMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductSearchMongoRepositoryAdapter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ProductConfiguration {

  @Bean(initMethod = "initialize")
  public CatalogSchemaValidator catalogSchemaValidator(MongoTemplate mongoTemplate) {
    return new CatalogSchemaValidator(mongoTemplate);
  }

  @Bean(initMethod = "initialize")
  public CatalogIndexInitializer catalogIndexInitializer(
      MongoTemplate mongoTemplate, CatalogSchemaValidator catalogSchemaValidator) {
    return new CatalogIndexInitializer(mongoTemplate);
  }

  @Bean
  public ProductRepository productRepository(MongoTemplate mongoTemplate) {
    return new ProductMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  public CreateProductUseCase createProductUseCase(ProductRepository productRepository) {
    return new AuthorizedCreateProductUseCase(new CreateProductService(productRepository));
  }

  @Bean
  public ProductEditingRepository productEditingRepository(MongoTemplate mongoTemplate) {
    return new ProductEditingMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  public ProductChangedPublisher productChangedPublisher(
      ApplicationEventPublisher applicationEventPublisher) {
    return new SpringProductChangedPublisher(applicationEventPublisher);
  }

  @Bean
  public EditProductUseCase editProductUseCase(
      ProductEditingRepository productEditingRepository,
      ProductChangedPublisher productChangedPublisher,
      PlatformTransactionManager transactionManager) {
    EditProductService delegate =
        new EditProductService(productEditingRepository, productChangedPublisher);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    EditProductUseCase transactional =
        (productId, name, price, version) ->
            transactionTemplate.execute(status -> delegate.edit(productId, name, price, version));
    return new AuthorizedEditProductUseCase(transactional);
  }

  @Bean
  public ProductSearchRepository productSearchRepository(MongoTemplate mongoTemplate) {
    return new ProductSearchMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  public ProductSearchUseCase productSearchUseCase(
      ProductSearchRepository productSearchRepository) {
    return new ProductSearchService(productSearchRepository);
  }

  @Bean
  public ProductDetailRepository productDetailRepository(MongoTemplate mongoTemplate) {
    return new CatalogDetailMongoRepositoryAdapter(mongoTemplate);
  }

  @Bean
  public ProductDetailUseCase productDetailUseCase(
      ProductDetailRepository repository, PlatformTransactionManager transactionManager) {
    ProductDetailService delegate = new ProductDetailService(repository);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    ProductDetailUseCase transactional =
        sku -> transactionTemplate.execute(status -> delegate.findBySku(sku));
    return new CachedProductDetailUseCase(transactional);
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
