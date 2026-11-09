package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import static com.codeja.master_acdc.AuthorizedCalls.withAuthorities;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.MasterAcdcApplication;
import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;
import com.codeja.master_acdc.catalog.infrastructure.config.CatalogMongoTestConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ProductCacheMultiInstanceIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Container @ServiceConnection
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  private static ConfigurableApplicationContext instanceA;
  private static ConfigurableApplicationContext instanceB;

  @BeforeAll
  static void start_two_instances_with_shared_stores() {
    instanceA = startInstance("cache-a");
    instanceB = startInstance("cache-b");
    awaitListenersReady();
  }

  @AfterAll
  static void close_two_instances() {
    instanceB.close();
    instanceA.close();
  }

  @Test
  @DisplayName("Edición propagada entre instancias")
  void should_propagate_product_edit_when_both_instances_are_subscribed() {
    String sku = "SKU-CACHE-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
    Product product = createProduct(instanceA, sku);
    warmDetailCaches(sku);

    withAuthorities(
        () ->
            instanceA
                .getBean(EditProductUseCase.class)
                .edit(product.id(), "Changed", new BigDecimal("12000"), 0L),
        "product:write");

    Awaitility.await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(detail(instanceB, sku).price()).isEqualTo(12_000));
    assertThat(detail(instanceA, sku).price()).isEqualTo(12_000);
  }

  @Test
  @Tag("slow")
  @DisplayName("Mensaje de invalidación perdido")
  void should_refresh_after_local_ttl_when_peer_misses_invalidation() {
    String sku = "SKU-CACHE-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
    Product product = createProduct(instanceA, sku);
    warmDetailCaches(sku);
    RedisMessageListenerContainer listenerB = invalidationContainer(instanceB);
    listenerB.stop();

    withAuthorities(
        () ->
            instanceA
                .getBean(EditProductUseCase.class)
                .edit(product.id(), "Changed", new BigDecimal("12000"), 0L),
        "product:write");

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertThat(detail(instanceB, sku).price()).isEqualTo(12_000));
  }

  @Test
  @DisplayName("Concurrencia sobre una ficha ausente de cache")
  void should_load_at_most_once_per_instance_when_200_virtual_threads_miss() throws Exception {
    String sku = "SKU-CACHE-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
    createProduct(instanceA, sku);
    CatalogMongoTestConfiguration.resetReadCommands();
    CountDownLatch ready = new CountDownLatch(400);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ProductDetail>> results = new ArrayList<>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int index = 0; index < 200; index++) {
        results.add(submitDetail(executor, instanceA, sku, ready, start));
        results.add(submitDetail(executor, instanceB, sku, ready, start));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (Future<ProductDetail> result : results) {
        assertThat(result.get().price()).isEqualTo(10_000);
      }
    }

    assertThat(CatalogMongoTestConfiguration.readCommands()).isBetween(1, 2);
  }

  private static ConfigurableApplicationContext startInstance(String instanceId) {
    return new SpringApplicationBuilder(
            MasterAcdcApplication.class, CatalogMongoTestConfiguration.class)
        .run(
            "--catalog.cache.enabled=true",
            "--catalog.store=mongo",
            "--catalog.stock-sync.enabled=false",
            "--cache.instance-id=" + instanceId,
            "--server.port=0",
            "--spring.data.mongodb.uri=" + MONGO.getReplicaSetUrl(),
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.data.redis.host=" + REDIS.getHost(),
            "--spring.data.redis.port=" + REDIS.getMappedPort(6379));
  }

  private static void awaitListenersReady() {
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                invalidationContainer(instanceA).isListening()
                    && invalidationContainer(instanceB).isListening());
  }

  private static RedisMessageListenerContainer invalidationContainer(
      ConfigurableApplicationContext context) {
    return context.getBean(
        "productCacheInvalidationContainer", RedisMessageListenerContainer.class);
  }

  private static Product createProduct(ConfigurableApplicationContext context, String sku) {
    Product product =
        withAuthorities(
            () ->
                context
                    .getBean(CreateProductUseCase.class)
                    .create("Cached product", sku, new BigDecimal("10000")),
            "product:write");
    assertThat(context.getBean(ProductDetailRepository.class).findBySku(sku))
        .as("product should be visible from the creating context")
        .isPresent();
    return product;
  }

  private static void warmDetailCaches(String sku) {
    assertThat(instanceA.getBean(CacheManager.class).getCache("products"))
        .isInstanceOf(ProductDetailCache.class);
    assertThat(instanceB.getBean(CacheManager.class).getCache("products"))
        .isInstanceOf(ProductDetailCache.class);
    detail(instanceA, sku);
    detail(instanceB, sku);
  }

  private static ProductDetail detail(ConfigurableApplicationContext context, String sku) {
    return context.getBean(ProductDetailUseCase.class).findBySku(sku);
  }

  private static Future<ProductDetail> submitDetail(
      java.util.concurrent.ExecutorService executor,
      ConfigurableApplicationContext context,
      String sku,
      CountDownLatch ready,
      CountDownLatch start) {
    return executor.submit(
        () -> {
          ready.countDown();
          start.await();
          return detail(context, sku);
        });
  }
}
