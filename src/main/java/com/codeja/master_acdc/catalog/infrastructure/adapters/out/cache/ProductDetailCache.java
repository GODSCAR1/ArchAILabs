package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueRetrievalException;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NoOpCache;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.serializer.SerializationException;

/** Cache-aside adapter that combines a local and a shared cache. */
public final class ProductDetailCache extends AbstractValueAdaptingCache {

  private final Cache local;
  private final Cache shared;
  private final MeterRegistry meterRegistry;
  private static final Logger LOGGER = LoggerFactory.getLogger(ProductDetailCache.class);

  public ProductDetailCache(Cache local, Cache shared, MeterRegistry meterRegistry) {
    super(false);
    this.local = local;
    this.shared = shared;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public String getName() {
    return "products";
  }

  @Override
  public Object getNativeCache() {
    return this;
  }

  @Override
  protected Object lookup(Object key) {
    Cache.ValueWrapper localValue = local.get(key);
    if (localValue != null) {
      record("products.l1", "hit");
      return localValue.get();
    }
    record("products.l1", "miss");
    try {
      Cache.ValueWrapper remoteValue = shared.get(key);
      if (remoteValue != null) {
        record("products.l2", "hit");
        local.put(key, remoteValue.get());
        return remoteValue.get();
      }
      record("products.l2", "miss");
    } catch (SerializationException exception) {
      logCorruptValue();
      evictCorruptValue(key);
      record("products.l2", "miss");
    } catch (DataAccessException exception) {
      LOGGER.warn("Shared product cache read failed; treating it as a miss", exception);
      record("products.l2", "miss");
      return null;
    }
    return null;
  }

  @Override
  public <T> T get(Object key, Callable<T> valueLoader) {
    Cache.ValueWrapper cached = local.get(key);
    if (cached != null) {
      record("products.l1", "hit");
      return (T) cached.get();
    }
    record("products.l1", "miss");
    try {
      return local.get(key, () -> loadThroughRemote(key, valueLoader));
    } catch (ValueRetrievalException exception) {
      ValueRetrievalException cause = exception;
      while (cause.getCause() instanceof ValueRetrievalException nested) {
        cause = nested;
      }
      throw cause;
    }
  }

  private <T> T loadThroughRemote(Object key, Callable<T> valueLoader) {
    try {
      Cache.ValueWrapper cached = shared.get(key);
      if (cached != null) {
        record("products.l2", "hit");
        return (T) cached.get();
      }
      record("products.l2", "miss");
    } catch (SerializationException exception) {
      logCorruptValue();
      evictCorruptValue(key);
      record("products.l2", "miss");
    } catch (DataAccessException exception) {
      LOGGER.warn("Shared product cache read failed; treating it as a miss", exception);
      record("products.l2", "miss");
      // Redis is an optional cache layer; load from the source of truth.
    }
    T value = new NoOpCache(getName()).get(key, valueLoader);
    try {
      shared.put(key, value);
    } catch (DataAccessException exception) {
      LOGGER.warn("Shared product cache write failed", exception);
      // The local cache and source read remain successful while Redis is unavailable.
    }
    return value;
  }

  @Override
  public void put(Object key, Object value) {
    local.put(key, value);
    try {
      shared.put(key, value);
    } catch (DataAccessException exception) {
      LOGGER.warn("Shared product cache write failed", exception);
      // Preserve the successful L1 write if Redis is unavailable.
    }
  }

  @Override
  public void evict(Object key) {
    local.evict(key);
    try {
      shared.evict(key);
    } catch (DataAccessException exception) {
      LOGGER.warn("Shared product cache eviction failed", exception);
      // Local invalidation must not depend on Redis availability.
    }
  }

  @Override
  public void clear() {
    local.clear();
    try {
      shared.clear();
    } catch (DataAccessException exception) {
      LOGGER.warn("Shared product cache clear failed", exception);
      // Local invalidation must not depend on Redis availability.
    }
  }

  @Override
  protected Object toStoreValue(Object value) {
    return value;
  }

  @Override
  protected Object fromStoreValue(Object storeValue) {
    return storeValue;
  }

  private void record(String cache, String result) {
    meterRegistry.counter("cache.gets", "cache", cache, "result", result).increment();
  }

  private void evictCorruptValue(Object key) {
    try {
      shared.evict(key);
    } catch (DataAccessException exception) {
      LOGGER.warn("Incompatible shared product cache value could not be evicted", exception);
    }
  }

  private static void logCorruptValue() {
    LOGGER.warn("Incompatible shared product cache value; treating it as a miss");
  }
}
