package com.codeja.master_acdc.catalog.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/** Logs cache failures that escape a cache adapter without failing the caller. */
public final class ProductCacheErrorHandler implements CacheErrorHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductCacheErrorHandler.class);

  @Override
  public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
    log("get", exception, cache);
  }

  @Override
  public void handleCachePutError(
      RuntimeException exception, Cache cache, Object key, Object value) {
    log("put", exception, cache);
  }

  @Override
  public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
    log("evict", exception, cache);
  }

  @Override
  public void handleCacheClearError(RuntimeException exception, Cache cache) {
    log("clear", exception, cache);
  }

  private static void log(String operation, RuntimeException exception, Cache cache) {
    LOGGER.warn("Cache {} failed during {}", cache.getName(), operation, exception);
  }
}
