package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CachedProductDetailUseCaseTest.TestConfig.class)
class CachedProductDetailUseCaseTest {

  private static final String SKU = "SKU-NEW";

  @Autowired
  @Qualifier("cachedProductDetailUseCase") private ProductDetailUseCase cachedUseCase;

  @Autowired private SourceUseCase source;

  @Test
  @DisplayName("SKU inexistente se crea")
  void should_return_404_then_load_created_product_when_missing_sku_is_retried() {
    assertThatThrownBy(() -> cachedUseCase.findBySku(SKU))
        .isInstanceOf(ProductNotFoundException.class);

    ProductDetail detail = cachedUseCase.findBySku(SKU);

    assertThat(detail.sku()).isEqualTo(SKU);
    assertThat(source.calls).hasValue(2);
  }

  @Configuration
  @EnableCaching
  static class TestConfig {

    @Bean
    CacheManager cacheManager() {
      ProductDetailCache products =
          new ProductDetailCache(
              new CaffeineCache(
                  "products.l1",
                  com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build(),
                  false),
              new ConcurrentMapCache("products.l2"),
              new SimpleMeterRegistry());
      SimpleCacheManager manager = new SimpleCacheManager();
      manager.setCaches(List.of(products));
      return manager;
    }

    @Bean
    SourceUseCase sourceProductDetailUseCase() {
      return new SourceUseCase();
    }

    @Bean
    ProductDetailUseCase cachedProductDetailUseCase(
        @Qualifier("sourceProductDetailUseCase") SourceUseCase source) {
      return new CachedProductDetailUseCase(source);
    }
  }

  static class SourceUseCase implements ProductDetailUseCase {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public ProductDetail findBySku(String sku) {
      if (calls.getAndIncrement() == 0) {
        throw new ProductNotFoundException(sku);
      }
      return new ProductDetail(
          UUID.randomUUID(),
          sku,
          "Created",
          100,
          ProductStatus.ACTIVE,
          new Category(UUID.randomUUID(), "Category"),
          0,
          List.of(),
          Map.of(),
          new ProductRating(BigDecimal.ZERO.setScale(2), 0),
          List.of());
    }
  }
}
