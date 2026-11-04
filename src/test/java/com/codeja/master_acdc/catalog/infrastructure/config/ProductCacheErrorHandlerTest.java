package com.codeja.master_acdc.catalog.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.dao.DataAccessResourceFailureException;

class ProductCacheErrorHandlerTest {

  @Test
  @DisplayName("Los errores de cache se registran sin fallar la petición")
  void should_absorb_cache_operation_failures_when_the_aspect_calls_error_handler() {
    ProductCacheErrorHandler handler = new ProductCacheErrorHandler();
    Cache cache = new ConcurrentMapCache("products");
    RuntimeException failure = new DataAccessResourceFailureException("Redis unavailable");

    assertThatCode(
            () -> {
              handler.handleCacheGetError(failure, cache, "SKU");
              handler.handleCachePutError(failure, cache, "SKU", "value");
              handler.handleCacheEvictError(failure, cache, "SKU");
              handler.handleCacheClearError(failure, cache);
            })
        .doesNotThrowAnyException();
  }
}
