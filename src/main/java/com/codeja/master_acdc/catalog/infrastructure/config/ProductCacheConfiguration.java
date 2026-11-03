package com.codeja.master_acdc.catalog.infrastructure.config;

import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductCacheTtl;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListener;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductDetailCache;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.RedisProductDetailCache;
import io.lettuce.core.ClientOptions;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableCaching
public class ProductCacheConfiguration implements CachingConfigurer {

  @Bean
  @ConditionalOnProperty(
      name = "catalog.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LettuceClientConfigurationBuilderCustomizer rejectDisconnectedRedisCommands() {
    return builder ->
        builder.clientOptions(
            ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
  }

  @Bean
  @ConditionalOnProperty(
      name = "catalog.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RedisTemplate<String, ProductDetail> productDetailRedisTemplate(
      RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, ProductDetail> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(
        new JacksonJsonRedisSerializer<>(JsonMapper.builder().build(), ProductDetail.class));
    template.afterPropertiesSet();
    return template;
  }

  @Bean
  @ConditionalOnProperty(
      name = "catalog.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CaffeineCache productDetailLocalCache() {
    return new CaffeineCache("products.l1", localCache(), false);
  }

  @Bean
  @ConditionalOnProperty(
      name = "catalog.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RedisProductDetailCache productDetailSharedCache(
      RedisTemplate<String, ProductDetail> template) {
    return new RedisProductDetailCache(template);
  }

  @Bean
  @ConditionalOnProperty(
      name = "catalog.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CacheManager productCacheManager(
      @Qualifier("productDetailLocalCache") Cache local,
      @Qualifier("productDetailSharedCache") Cache shared,
      MeterRegistry meterRegistry) {
    ProductDetailCache products = new ProductDetailCache(local, shared, meterRegistry);
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(List.of(products));
    return manager;
  }

  @Bean
  @ConditionalOnProperty(name = "catalog.cache.enabled", havingValue = "false")
  public CacheManager noOpCacheManager() {
    return new NoOpCacheManager();
  }

  @Bean
  @ConditionalOnProperty(
      name = "catalog.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ProductChangedCacheListener productChangedCacheListener(
      @Qualifier("productDetailLocalCache") Cache local,
      @Qualifier("productDetailSharedCache") Cache shared,
      StringRedisTemplate redis,
      MeterRegistry meterRegistry,
      @Value("${cache.instance-id:}") String instanceId) {
    return new ProductChangedCacheListener(local, shared, redis, meterRegistry, instanceId);
  }

  @Bean
  @ConditionalOnProperty(
      name = "catalog.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RedisMessageListenerContainer productCacheInvalidationContainer(
      RedisConnectionFactory connectionFactory, ProductChangedCacheListener listener) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, new ChannelTopic("shop:cache:evictions"));
    return container;
  }

  private static com.github.benmanes.caffeine.cache.Cache<Object, Object> localCache() {
    return com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfter(
            new com.github.benmanes.caffeine.cache.Expiry<Object, Object>() {
              @Override
              public long expireAfterCreate(Object key, Object value, long currentTime) {
                return ProductCacheTtl.jitter(Duration.ofSeconds(15)).toNanos();
              }

              @Override
              public long expireAfterUpdate(
                  Object key, Object value, long currentTime, long currentDuration) {
                return ProductCacheTtl.jitter(Duration.ofSeconds(15)).toNanos();
              }

              @Override
              public long expireAfterRead(
                  Object key, Object value, long currentTime, long currentDuration) {
                return currentDuration;
              }
            })
        .recordStats()
        .build();
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new ProductCacheErrorHandler();
  }
}
