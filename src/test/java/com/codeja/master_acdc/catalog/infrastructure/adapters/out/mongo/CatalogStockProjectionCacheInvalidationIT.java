package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import com.codeja.master_acdc.catalog.infrastructure.config.CatalogMongoTestConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "catalog.store=mongo",
      "catalog.cache.enabled=true",
      "catalog.stock-sync.enabled=false",
      "cache.instance-id=stock-cache-it"
    })
@AutoConfigureMockMvc
@Testcontainers
@Import(CatalogMongoTestConfiguration.class)
class CatalogStockProjectionCacheInvalidationIT {

  private static final String CHANGED_SKU = "SKU-STOCK-CHANGED";
  private static final String STABLE_SKU = "SKU-STOCK-STABLE";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Container @ServiceConnection
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  @Autowired private CacheManager cacheManager;
  @Autowired private CreateProductUseCase createProductUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private MockMvc mockMvc;
  @Autowired private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Autowired private ProductChangedPublisher productChangedPublisher;

  @Autowired
  @Qualifier("productCacheInvalidationContainer") private RedisMessageListenerContainer invalidationContainer;

  @BeforeEach
  void clear_projection_data_and_cache() {
    jdbcTemplate.execute("truncate table stock");
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    cacheManager.getCache("products").clear();
    Awaitility.await().atMost(Duration.ofSeconds(5)).until(invalidationContainer::isListening);
  }

  @Test
  @DisplayName("Cambio de stock en la proyección")
  void should_invalidate_only_changed_sku_when_projection_updates_stock() throws Exception {
    var changed =
        createProductUseCase.create("Changed stock", CHANGED_SKU, new BigDecimal("10000"));
    var stable = createProductUseCase.create("Stable stock", STABLE_SKU, new BigDecimal("12000"));
    setProjectedStock(changed.id(), 2);
    setProjectedStock(stable.id(), 6);
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", changed.id(), 5);
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", stable.id(), 6);
    getStock(CHANGED_SKU, 2);
    getStock(STABLE_SKU, 6);
    CatalogMongoTestConfiguration.resetReadCommands();

    new CatalogStockProjectionJob(
            namedParameterJdbcTemplate, mongoTemplate, 10, productChangedPublisher)
        .synchronizeStock();

    assertThat(CatalogMongoTestConfiguration.readCommands()).isEqualTo(1);
    getStock(CHANGED_SKU, 5);
    assertThat(CatalogMongoTestConfiguration.readCommands()).isEqualTo(2);
    getStock(STABLE_SKU, 6);
    assertThat(CatalogMongoTestConfiguration.readCommands()).isEqualTo(2);
  }

  private void setProjectedStock(UUID productId, int stock) {
    mongoTemplate.updateFirst(
        Query.query(where("_id").is(productId)),
        new Update().set("availableStock", stock),
        CatalogProduct.class);
  }

  private void getStock(String sku, int stock) throws Exception {
    mockMvc
        .perform(get("/api/products/{sku}", sku).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(stock));
  }
}
