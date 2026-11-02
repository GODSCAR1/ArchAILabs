package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "catalog.store=postgres"
    })
@Testcontainers
class ProductDetailQueryCountTest {

  private static final UUID PRODUCT_ID =
      UUID.nameUUIDFromBytes("detail-product".getBytes(StandardCharsets.UTF_8));
  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductDetailUseCase productDetailUseCase;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManager entityManager;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from reviews");
    jdbcTemplate.update("delete from product_attributes");
    jdbcTemplate.update("delete from product_variants");
    jdbcTemplate.update("delete from products where id = ?", PRODUCT_ID);
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, 'Cafe', 'SKU-DETAIL', 100, ?, 'ACTIVE', current_timestamp, 0)",
        PRODUCT_ID,
        CATEGORY_ID);
    for (int index = 0; index < 60; index++) {
      UUID id = UUID.nameUUIDFromBytes(("variant-" + index).getBytes(StandardCharsets.UTF_8));
      jdbcTemplate.update(
          "insert into product_variants (id, product_id, sku_suffix, color, size, additional_price)"
              + " values (?, ?, ?, 'Red', 'M', 1)",
          id,
          PRODUCT_ID,
          "-" + index);
      jdbcTemplate.update(
          "insert into product_attributes (product_id, name, value) values (?, ?, ?)",
          PRODUCT_ID,
          "attribute-" + index,
          "value");
      jdbcTemplate.update(
          "insert into reviews (id, product_id, author, rating, comment, created_at) "
              + "values (?, ?, ?, 5, ?, ?)",
          UUID.nameUUIDFromBytes(("review-" + index).getBytes(StandardCharsets.UTF_8)),
          PRODUCT_ID,
          "Author " + index,
          "Review " + index,
          Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").minusSeconds(index)));
    }
  }

  @Test
  @DisplayName("Muchas colecciones asociadas")
  void should_keep_query_count_at_most_four_when_detail_has_many_associations() {
    new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              entityManager.flush();
              entityManager.clear();
              return null;
            });
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();

    productDetailUseCase.findBySku("SKU-DETAIL");

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
  }
}
