package com.codeja.master_acdc.catalog.infrastructure.config;

import com.codeja.master_acdc.catalog.application.CreateProductService;
import com.codeja.master_acdc.catalog.application.ProductSearchService;
import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductJpaRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductSearchRepositoryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductConfiguration {

  @Bean
  public ProductRepository productRepository(ProductJpaRepository productJpaRepository) {
    return new ProductRepositoryAdapter(productJpaRepository);
  }

  @Bean
  public CreateProductUseCase createProductUseCase(ProductRepository productRepository) {
    return new CreateProductService(productRepository);
  }

  @Bean
  public ProductSearchRepository productSearchRepository(
      ProductJpaRepository productJpaRepository) {
    return new ProductSearchRepositoryAdapter(productJpaRepository);
  }

  @Bean
  public ProductSearchUseCase productSearchUseCase(
      ProductSearchRepository productSearchRepository) {
    return new ProductSearchService(productSearchRepository);
  }
}
