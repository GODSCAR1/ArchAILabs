package com.codeja.master_acdc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTestInfrastructureIT extends IntegrationTest {
  private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MongoTemplate mongo;
  @Autowired private StringRedisTemplate redis;

  @Autowired
  @Qualifier("wiremock") private GenericContainer<?> wiremock;

  @Test
  @Order(3)
  void should_match_cleanup_table_list_when_database_schema_changes() {
    var tables =
        jdbc.queryForList(
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            AND table_name <> 'flyway_schema_history'
            """,
            String.class);
    assertThat(tables)
        .as("Tablas faltantes o sobrantes: actualizar la lista de limpieza de IntegrationTest")
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(
                    TRUNCATE_DOMAIN
                        .substring(
                            TRUNCATE_DOMAIN.indexOf("TRUNCATE TABLE") + "TRUNCATE TABLE".length(),
                            TRUNCATE_DOMAIN.indexOf("RESTART"))
                        .split(","))
                .map(String::trim)
                .toList());
  }

  @Test
  @Order(1)
  void should_write_all_stores_when_test_starts_clean() {
    jdbc.update("insert into stock (product_id, available) values (?, ?)", ID, 1);
    mongo.getCollection("integration_cleanup").insertOne(new Document("value", "present"));
    redis.opsForValue().set("integration-cleanup", "present");
    wiremock().register(get(urlEqualTo("/cleanup-test")).willReturn(aResponse().withStatus(200)));
    RestClient.create("http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080))
        .get()
        .uri("/cleanup-test")
        .retrieve()
        .toBodilessEntity();
    assertThat(jdbc.queryForObject("select count(*) from stock", Long.class)).isEqualTo(1);
    assertThat(mongo.getCollection("integration_cleanup").countDocuments()).isEqualTo(1);
    assertThat(redis.opsForValue().get("integration-cleanup")).isEqualTo("present");
    assertThat(wiremock().getServeEvents()).hasSize(1);
  }

  @Test
  @Order(2)
  void should_clean_all_stores_when_next_test_starts() {
    assertThat(jdbc.queryForObject("select count(*) from stock", Long.class)).isZero();
    assertThat(mongo.getCollection("integration_cleanup").countDocuments()).isZero();
    assertThat(redis.hasKey("integration-cleanup")).isFalse();
    assertThat(wiremock().getServeEvents()).isEmpty();
    assertThat(wiremock().allStubMappings().getMappings()).isEmpty();
  }
}
