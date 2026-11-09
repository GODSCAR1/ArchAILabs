package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static com.codeja.master_acdc.AuthorizedCalls.withAuthorities;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.infrastructure.config.CatalogMongoTestConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "catalog.store=mongo",
      "catalog.cache.enabled=false",
      "catalog.stock-sync.enabled=true",
      "catalog.stock-sync.interval=PT1S"
    })
@AutoConfigureMockMvc
@Testcontainers
@Import(CatalogMongoTestConfiguration.class)
class CatalogStockProjectionSchedulingIT {

  private static final String SKU = "SKU-SCHEDULED-STOCK";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Autowired private ApplicationContext applicationContext;
  @Autowired private CacheManager cacheManager;
  @Autowired private CreateProductUseCase createProductUseCase;
  @Autowired private Environment environment;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void clear_stock_and_products() {
    jdbcTemplate.execute("truncate table stock");
    mongoTemplate.remove(new Query(), CatalogProduct.class);
  }

  @Test
  @DisplayName("El job ejecuta la sincronización periódica")
  void should_project_stock_on_scheduled_cycle_when_sync_is_enabled() throws Exception {
    assertThat(applicationContext.getBeansOfType(CatalogStockProjectionJob.class)).hasSize(1);
    assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
    assertThat(environment.getProperty("spring.data.mongodb.auto-index-creation"))
        .isEqualTo("false");
    assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("localhost");
    assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6379");
    var product =
        withAuthorities(
            () -> createProductUseCase.create("Scheduled", SKU, new BigDecimal("10000")),
            "product:write");
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", product.id(), 5);

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                mockMvc
                    .perform(get("/api/products/{sku}", SKU))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(10000))
                    .andExpect(jsonPath("$.stock").value(5)));
  }
}
