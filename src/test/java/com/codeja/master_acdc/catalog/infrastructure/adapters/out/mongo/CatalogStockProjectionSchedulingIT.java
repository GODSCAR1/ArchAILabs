package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static com.codeja.master_acdc.AuthorizedCalls.withAuthorities;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import java.math.BigDecimal;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.web.servlet.MockMvc;

class CatalogStockProjectionSchedulingIT extends IntegrationTest {

  private static final String SKU = "SKU-SCHEDULED-STOCK";

  @Autowired private CatalogStockProjectionJob job;
  @Autowired private ScheduledTaskHolder scheduledTasks;
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
    assertThat(scheduledTasks.getScheduledTasks().stream().map(task -> task.getTask()))
        .filteredOn(task -> task.toString().endsWith("CatalogStockProjectionJob.synchronizeStock"))
        .singleElement()
        .isInstanceOfSatisfying(
            FixedDelayTask.class,
            task ->
                assertThat(task.getIntervalDuration())
                    .isEqualTo(
                        Duration.parse(
                            environment.getRequiredProperty("catalog.stock-sync.interval"))));
    assertThat(environment.getProperty("spring.data.mongodb.auto-index-creation"))
        .isEqualTo("false");
    var product =
        withAuthorities(
            () -> createProductUseCase.create("Scheduled", SKU, new BigDecimal("10000")),
            "product:write");
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", product.id(), 5);
    job.synchronizeStock();

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
