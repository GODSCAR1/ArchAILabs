package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.ProductVariant;
import com.codeja.master_acdc.catalog.infrastructure.config.ProductCacheConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ProductDetailCacheRedisIT {

  private static final String SKU = "SKU-REDIS";

  @Container @ServiceConnection
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static RedisTemplate<String, ProductDetail> redisTemplate;
  private static RedisProductDetailCache sharedCache;
  private static CacheManager cacheManager;

  @BeforeAll
  static void initialize_redis_cache() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    ProductCacheConfiguration configuration = new ProductCacheConfiguration();
    redisTemplate = configuration.productDetailRedisTemplate(connectionFactory);
    Cache local = configuration.productDetailLocalCache(10_000);
    sharedCache = configuration.productDetailSharedCache(redisTemplate);
    cacheManager = configuration.productCacheManager(local, sharedCache, new SimpleMeterRegistry());
    ((SimpleCacheManager) cacheManager).afterPropertiesSet();
  }

  @AfterAll
  static void close_redis_connection() {
    connectionFactory.destroy();
  }

  @Test
  @DisplayName("Valor recuperado del almacén compartido")
  void should_restore_full_detail_when_value_is_read_from_redis() {
    ProductDetail expected = detail();
    sharedCache.put(SKU, expected);
    AtomicInteger loads = new AtomicInteger();

    ProductDetail actual = cacheManager.getCache("products").get(SKU, () -> countLoad(loads));

    assertThat(actual).isEqualTo(expected);
    assertThat(loads).hasValue(0);
    assertThat(cacheManager.getCache("products.l1")).isNull();
    assertThat(cacheManager.getCache("products.l2")).isNull();
  }

  @Test
  @DisplayName("TTL aleatorios")
  void should_apply_randomized_shared_expiration_when_value_is_written() {
    sharedCache.put(SKU, detail());

    long ttlSeconds =
        redisTemplate.getExpire(
            "shop:cache:v1:products:" + SKU, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(ttlSeconds).isBetween(540L, 660L);
  }

  private static ProductDetail detail() {
    return new ProductDetail(
        UUID.randomUUID(),
        SKU,
        "Product with all detail fields",
        12_345,
        ProductStatus.ACTIVE,
        new Category(UUID.randomUUID(), "Category"),
        7,
        List.of(new ProductVariant(UUID.randomUUID(), "-M", "Blue", "M", 500)),
        Map.of("material", "cotton"),
        new ProductRating(new BigDecimal("4.50"), 2),
        List.of(
            new ProductReview(
                UUID.randomUUID(),
                "Guest",
                5,
                "Well made",
                Instant.parse("2026-11-03T10:00:00Z"))));
  }

  private static ProductDetail countLoad(AtomicInteger loads) {
    loads.incrementAndGet();
    return detail();
  }
}
