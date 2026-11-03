package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueRetrievalException;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.dao.DataAccessResourceFailureException;

class ProductDetailCacheTest {

  private static final String SKU = "SKU-CACHE";

  @Test
  @DisplayName("Ficha repetida")
  void should_load_once_when_detail_is_requested_twice() {
    ProductDetailCache cache = cache();
    AtomicInteger loads = new AtomicInteger();
    ProductDetail expected = detail();

    ProductDetail first = cache.get(SKU, () -> countLoad(loads, expected));
    ProductDetail second = cache.get(SKU, () -> countLoad(loads, expected));

    assertThat(first).isEqualTo(expected);
    assertThat(second).isEqualTo(expected);
    assertThat(loads).hasValue(1);
  }

  @Test
  @DisplayName("Lectura desde el almacén compartido")
  void should_populate_local_cache_when_shared_value_exists() {
    ConcurrentMapCache shared = new ConcurrentMapCache("products.l2");
    ProductDetail expected = detail();
    shared.put(SKU, expected);
    ProductDetailCache cache = cache(shared);
    AtomicInteger loads = new AtomicInteger();

    ProductDetail actual = cache.get(SKU, () -> countLoad(loads, detail()));

    assertThat(actual).isEqualTo(expected);
    assertThat(loads).hasValue(0);
    assertThat(cache.get(SKU).get()).isEqualTo(expected);
  }

  @Test
  @DisplayName("SKU inexistente conserva el 404")
  void should_rethrow_product_not_found_when_loader_fails() {
    ProductDetailCache cache = cache();
    ProductNotFoundException notFound = new ProductNotFoundException(SKU);

    assertThatThrownBy(() -> cache.get(SKU, () -> fail(notFound)))
        .isInstanceOf(ValueRetrievalException.class)
        .hasCause(notFound);
  }

  @Test
  @DisplayName("TTL aleatorios")
  void should_jitter_product_cache_ttl_when_ttl_is_generated() {
    List<Duration> values =
        java.util.stream.IntStream.range(0, 100)
            .mapToObj(index -> ProductCacheTtl.jitter(Duration.ofSeconds(15)))
            .toList();

    assertThat(values)
        .allSatisfy(
            value -> {
              assertThat(value).isGreaterThanOrEqualTo(Duration.ofMillis(13_500));
              assertThat(value).isLessThanOrEqualTo(Duration.ofMillis(16_500));
            });
    assertThat(new HashSet<>(values)).hasSizeGreaterThan(1);
  }

  @Test
  @DisplayName("Métricas por nivel")
  void should_record_local_and_shared_cache_accesses_when_both_levels_are_used() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ProductDetailCache cache = cache(new ConcurrentMapCache("products.l2"), registry);
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(List.of(cache));
    manager.afterPropertiesSet();

    cache.get(SKU, () -> detail());
    cache.get(SKU, () -> detail());

    assertThat(
            registry
                .get("cache.gets")
                .tag("cache", "products.l1")
                .tag("result", "miss")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("cache.gets")
                .tag("cache", "products.l1")
                .tag("result", "hit")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("cache.gets")
                .tag("cache", "products.l2")
                .tag("result", "miss")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(registry.find("cache.gets").tag("cache", "products.l1").meters()).hasSize(2);
    assertThat(registry.find("cache.gets").tag("cache", "products").counter()).isNull();
    assertThat(manager.getCache("products")).isSameAs(cache);
    assertThat(manager.getCache("products.l1")).isNull();
    assertThat(manager.getCache("products.l2")).isNull();
  }

  @Test
  @DisplayName("Almacén compartido no disponible durante una lectura")
  void should_load_from_origin_when_shared_cache_read_fails() {
    Cache shared = org.mockito.Mockito.mock(Cache.class);
    given(shared.get(SKU)).willThrow(new DataAccessResourceFailureException("Redis offline"));
    ProductDetailCache cache = cache(shared);
    AtomicInteger loads = new AtomicInteger();
    ProductDetail expected = detail();

    ProductDetail actual = cache.get(SKU, () -> countLoad(loads, expected));

    assertThat(actual).isEqualTo(expected);
    assertThat(loads).hasValue(1);
  }

  @Test
  @DisplayName("JSON incompatible se recupera desde origen")
  void should_load_from_origin_when_shared_value_cannot_be_deserialized() {
    Cache shared = org.mockito.Mockito.mock(Cache.class);
    given(shared.get(SKU))
        .willThrow(
            new org.springframework.data.redis.serializer.SerializationException(
                "Incompatible JSON"));
    ProductDetailCache cache = cache(shared);
    AtomicInteger loads = new AtomicInteger();
    ProductDetail expected = detail();

    ProductDetail actual = cache.get(SKU, () -> countLoad(loads, expected));

    assertThat(actual).isEqualTo(expected);
    assertThat(loads).hasValue(1);
    org.mockito.BDDMockito.then(shared).should().evict(SKU);
  }

  @Test
  @DisplayName("Miss de L2 se cuenta una vez cuando falla el put")
  void should_count_l2_miss_once_when_shared_write_fails() {
    Cache shared = org.mockito.Mockito.mock(Cache.class);
    ProductDetail expected = detail();
    given(shared.get(SKU)).willReturn(null);
    org.mockito.BDDMockito.willThrow(new DataAccessResourceFailureException("Redis offline"))
        .given(shared)
        .put(SKU, expected);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ProductDetailCache cache = cache(shared, registry);

    assertThat(cache.get(SKU, () -> expected)).isEqualTo(expected);

    assertThat(
            registry
                .get("cache.gets")
                .tag("cache", "products.l2")
                .tag("result", "miss")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Miss de L2 no se cuenta otra vez si falla put")
  void should_count_shared_miss_once_when_l2_write_fails() {
    Cache shared = org.mockito.Mockito.mock(Cache.class);
    ProductDetail expected = detail();
    given(shared.get(SKU)).willReturn(null);
    org.mockito.BDDMockito.willThrow(new DataAccessResourceFailureException("Redis offline"))
        .given(shared)
        .put(SKU, expected);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ProductDetailCache cache = cache(shared, registry);

    assertThat(cache.get(SKU, () -> expected)).isEqualTo(expected);

    assertThat(
            registry
                .get("cache.gets")
                .tag("cache", "products.l2")
                .tag("result", "miss")
                .counter()
                .count())
        .isEqualTo(1);
  }

  private static ProductDetailCache cache() {
    return cache(new ConcurrentMapCache("products.l2"));
  }

  private static ProductDetailCache cache(Cache shared) {
    return cache(shared, new SimpleMeterRegistry());
  }

  private static ProductDetailCache cache(Cache shared, SimpleMeterRegistry registry) {
    CaffeineCache local =
        new CaffeineCache(
            "products.l1", com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build(), false);
    return new ProductDetailCache(local, shared, registry);
  }

  private static ProductDetail detail() {
    return new ProductDetail(
        UUID.randomUUID(),
        SKU,
        "Cached product",
        100,
        ProductStatus.ACTIVE,
        new Category(UUID.randomUUID(), "Category"),
        0,
        List.of(),
        Map.of(),
        new ProductRating(BigDecimal.ZERO.setScale(2), 0),
        List.of());
  }

  private static ProductDetail countLoad(AtomicInteger loads, ProductDetail value) {
    loads.incrementAndGet();
    return value;
  }

  private static ProductDetail fail(ProductNotFoundException exception) {
    throw exception;
  }
}
