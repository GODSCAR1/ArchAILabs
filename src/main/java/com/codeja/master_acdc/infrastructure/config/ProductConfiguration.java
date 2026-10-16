package com.codeja.master_acdc.infrastructure.config;

import com.codeja.master_acdc.application.CreateProductService;
import com.codeja.master_acdc.application.port.CreateProductUseCase;
import com.codeja.master_acdc.domain.port.ProductRepository;
import com.codeja.master_acdc.infrastructure.adapters.out.persistence.ProductJpaRepository;
import com.codeja.master_acdc.infrastructure.adapters.out.persistence.ProductRepositoryAdapter;
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
}
