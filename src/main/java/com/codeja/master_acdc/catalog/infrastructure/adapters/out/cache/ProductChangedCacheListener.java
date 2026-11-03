package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import com.codeja.master_acdc.catalog.domain.event.ProductChanged;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Invalidates product detail after commit and distributes L1 evictions to peer instances. */
public final class ProductChangedCacheListener implements MessageListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductChangedCacheListener.class);
  private static final String TOPIC = "shop:cache:evictions";
  private final Cache local;
  private final Cache shared;
  private final StringRedisTemplate redis;
  private final MeterRegistry meterRegistry;
  private final String instanceId;

  public ProductChangedCacheListener(
      Cache local,
      Cache shared,
      StringRedisTemplate redis,
      MeterRegistry meterRegistry,
      String instanceId) {
    this.local = local;
    this.shared = shared;
    this.redis = redis;
    this.meterRegistry = meterRegistry;
    this.instanceId = instanceId.isBlank() ? UUID.randomUUID().toString() : instanceId;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onProductChanged(ProductChanged event) {
    if (!evictSharedWithRetry(event.sku())) {
      LOGGER.error("Shared product cache eviction failed after retries");
      meterRegistry.counter("cache.evictions.failed", "cache", "products.l2").increment();
    }
    local.evict(event.sku());
    try {
      redis.convertAndSend(TOPIC, "products|" + event.sku() + "|" + instanceId);
    } catch (DataAccessException exception) {
      LOGGER.error("Product cache invalidation could not be published", exception);
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String[] parts = new String(message.getBody(), StandardCharsets.UTF_8).split("\\|", -1);
    if (parts.length == 3 && "products".equals(parts[0]) && !instanceId.equals(parts[2])) {
      local.evict(parts[1]);
    }
  }

  private boolean evictSharedWithRetry(String sku) {
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        shared.evict(sku);
        return true;
      } catch (DataAccessException exception) {
        LOGGER.warn("Shared product cache eviction attempt {} failed", attempt, exception);
        if (attempt < 3) {
          LockSupport.parkNanos(Duration.ofMillis(attempt * 25L).toNanos());
        }
      }
    }
    return false;
  }
}
