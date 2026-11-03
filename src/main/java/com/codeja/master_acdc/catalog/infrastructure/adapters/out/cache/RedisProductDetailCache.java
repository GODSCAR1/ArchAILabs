package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

/** Redis-backed cache with typed values and a versioned key prefix. */
public final class RedisProductDetailCache extends AbstractValueAdaptingCache {

  private static final String PREFIX = "shop:cache:v1:products:";
  private final RedisTemplate<String, ProductDetail> redis;

  public RedisProductDetailCache(RedisTemplate<String, ProductDetail> redis) {
    super(false);
    this.redis = redis;
  }

  @Override
  public String getName() {
    return "products.l2";
  }

  @Override
  public Object getNativeCache() {
    return redis;
  }

  @Override
  protected Object lookup(Object key) {
    return redis.opsForValue().get(redisKey(key));
  }

  @Override
  public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
    throw new UnsupportedOperationException("Redis cache does not load values");
  }

  @Override
  public void put(Object key, Object value) {
    redis
        .opsForValue()
        .set(
            redisKey(key),
            (ProductDetail) value,
            CacheTtlPolicy.jitter(CacheTtlPolicy.PRODUCTS_L2_TTL));
  }

  @Override
  public void evict(Object key) {
    redis.delete(redisKey(key));
  }

  @Override
  public void clear() {
    RedisConnectionFactory factory = redis.getConnectionFactory();
    if (factory == null) {
      return;
    }
    try (RedisConnection connection = factory.getConnection();
        Cursor<byte[]> keys =
            connection.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(500).build())) {
      while (keys.hasNext()) {
        connection.keyCommands().del(keys.next());
      }
    }
  }

  private static String redisKey(Object key) {
    return PREFIX + key;
  }
}
