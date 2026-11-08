package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.config.CatalogMongoTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
      "cache.instance-id=cache-edit-it"
    })
@AutoConfigureMockMvc
@Testcontainers
@Import(CatalogMongoTestConfiguration.class)
class CatalogProductCacheEditingIT {

  private static final String SKU = "CACHE-EDIT-101";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Container @ServiceConnection
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  @Autowired private MockMvc mockMvc;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private CacheManager cacheManager;
  @Autowired private RedisConnectionFactory redisConnectionFactory;

  @BeforeEach
  void reset_product_and_cache() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    cacheManager.getCache("products").clear();
  }

  @Test
  @DisplayName("Edición válida invalida la ficha")
  void should_return_updated_detail_when_successful_edit_commits() throws Exception {
    UUID id = createProduct();
    getDetail(100);

    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("Changed", 12000, 0)))
        .andExpect(status().isOk());

    getDetail(12000);
  }

  @Test
  @DisplayName("Edición rechazada conserva la ficha cacheada")
  void should_keep_cached_detail_when_edit_uses_stale_version() throws Exception {
    UUID id = createProduct();
    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("Current", 11000, 0)))
        .andExpect(status().isOk());
    getDetail(11000);

    mockMvc
        .perform(
            put("/api/products/{id}", id)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody("Rejected", 12000, 0)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/concurrent-modification"));

    getDetail(11000);
  }

  @Test
  @DisplayName("JSON incompatible en Redis responde HTTP 200")
  void should_load_from_origin_when_shared_json_value_is_incompatible() throws Exception {
    createProduct();
    byte[] key = ("shop:cache:v1:products:" + SKU).getBytes(StandardCharsets.UTF_8);
    byte[] value = "not-json".getBytes(StandardCharsets.UTF_8);
    try (RedisConnection connection = redisConnectionFactory.getConnection()) {
      connection.stringCommands().set(key, value);
    }

    getDetail(100);
  }

  private UUID createProduct() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/products")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Original\",\"price\":100,\"sku\":\"" + SKU + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    int idStart = body.indexOf("\"id\":\"") + 6;
    return UUID.fromString(body.substring(idStart, body.indexOf('"', idStart)));
  }

  private void getDetail(long price) throws Exception {
    mockMvc
        .perform(get("/api/products/{sku}", SKU))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(price));
  }

  private static String editBody(String name, long price, int version) {
    return "{\"name\":\"" + name + "\",\"price\":" + price + ",\"version\":" + version + "}";
  }
}
