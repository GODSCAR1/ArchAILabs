package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers
class ProductSearchQueryCountTest {

  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductSearchUseCase productSearchUseCase;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired EntityManagerFactory entityManagerFactory;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("delete from products");
    for (int index = 0; index < 120; index++) {
      insertProduct(index);
    }
  }

  @Test
  void should_keep_query_count_at_most_two_when_listing_many_products() {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();

    productSearchUseCase.search(
        new ProductSearchCriteria(
            null,
            null,
            null,
            null,
            Set.of(),
            false,
            0,
            20,
            new ProductSearchOrder("price", ProductSearchOrder.Direction.ASC)));

    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
  }

  private void insertProduct(int index) {
    UUID id = UUID.nameUUIDFromBytes(("product-" + index).getBytes(StandardCharsets.UTF_8));
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, ?, ?, ?, ?, 'ACTIVE', current_timestamp, 0)",
        id,
        "Product " + index,
        "SKU-" + index,
        100 + index,
        CATEGORY_ID);
  }
}
