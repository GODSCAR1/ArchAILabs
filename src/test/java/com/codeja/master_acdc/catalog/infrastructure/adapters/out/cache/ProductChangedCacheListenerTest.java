package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codeja.master_acdc.catalog.domain.event.ProductChanged;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class ProductChangedCacheListenerTest {

  private static final String SKU = "SKU-CAFE";

  @Test
  @DisplayName("Edición válida invalida la ficha")
  void should_evict_both_levels_when_product_change_commits() {
    ConcurrentMapCache local = new ConcurrentMapCache("products.l1");
    ConcurrentMapCache shared = new ConcurrentMapCache("products.l2");
    local.put(SKU, "old");
    shared.put(SKU, "old");
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ProductChangedCacheListener listener = listener(local, shared, redis, "node-a");

    listener.onProductChanged(new ProductChanged(SKU));

    assertThat(local.get(SKU)).isNull();
    assertThat(shared.get(SKU)).isNull();
    verify(redis).convertAndSend("shop:cache:evictions", "products|" + SKU + "|node-a");
  }

  @Test
  @DisplayName("Fallo del almacén compartido al invalidar no bloquea las copias locales")
  void should_invalidate_local_and_publish_when_shared_eviction_fails() {
    ConcurrentMapCache local = new ConcurrentMapCache("products.l1");
    Cache shared = mock(Cache.class);
    local.put(SKU, "old");
    given(shared.getName()).willReturn("products.l2");
    org.mockito.BDDMockito.willThrow(new DataAccessResourceFailureException("Redis offline"))
        .given(shared)
        .evict(SKU);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ProductChangedCacheListener listener =
        new ProductChangedCacheListener(local, shared, redis, registry, "node-a");

    listener.onProductChanged(new ProductChanged(SKU));

    assertThat(local.get(SKU)).isNull();
    verify(shared, times(3)).evict(SKU);
    verify(redis).convertAndSend("shop:cache:evictions", "products|" + SKU + "|node-a");
    assertThat(registry.get("cache.evictions.failed").tag("cache", "products.l2").counter().count())
        .isEqualTo(1);
  }

  private static ProductChangedCacheListener listener(
      Cache local, Cache shared, StringRedisTemplate redis, String instanceId) {
    return new ProductChangedCacheListener(
        local, shared, redis, new SimpleMeterRegistry(), instanceId);
  }
}
