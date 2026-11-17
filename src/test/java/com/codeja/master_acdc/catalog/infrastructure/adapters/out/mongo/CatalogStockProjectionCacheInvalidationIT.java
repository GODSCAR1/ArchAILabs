package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static com.codeja.master_acdc.AuthorizedCalls.withAuthorities;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.IntegrationTestBeans;
import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class CatalogStockProjectionCacheInvalidationIT extends IntegrationTest {

  private static final String CHANGED_SKU = "SKU-STOCK-CHANGED";
  private static final String STABLE_SKU = "SKU-STOCK-STABLE";

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
        withAuthorities(
            () ->
                createProductUseCase.create("Changed stock", CHANGED_SKU, new BigDecimal("10000")),
            "product:write");
    var stable =
        withAuthorities(
            () -> createProductUseCase.create("Stable stock", STABLE_SKU, new BigDecimal("12000")),
            "product:write");
    setProjectedStock(changed.id(), 2);
    setProjectedStock(stable.id(), 6);
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", changed.id(), 5);
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", stable.id(), 6);
    getStock(CHANGED_SKU, 2);
    getStock(STABLE_SKU, 6);
    IntegrationTestBeans.resetReadCommands();

    new CatalogStockProjectionJob(
            namedParameterJdbcTemplate, mongoTemplate, 10, productChangedPublisher)
        .synchronizeStock();

    assertThat(IntegrationTestBeans.readCommands()).isEqualTo(1);
    getStock(CHANGED_SKU, 5);
    assertThat(IntegrationTestBeans.readCommands()).isEqualTo(2);
    getStock(STABLE_SKU, 6);
    assertThat(IntegrationTestBeans.readCommands()).isEqualTo(2);
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
