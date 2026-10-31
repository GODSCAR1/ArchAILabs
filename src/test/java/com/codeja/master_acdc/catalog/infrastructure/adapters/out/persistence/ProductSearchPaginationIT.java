package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ProductSearchPaginationIT {

  private static final int PRODUCTS = 60;
  private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductSearchUseCase productSearchUseCase;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("delete from products");
    for (int index = 0; index < PRODUCTS; index++) {
      insertProduct(index);
    }
  }

  @Test
  void should_return_each_product_once_when_pages_are_seven_and_prices_repeat() {
    List<UUID> seenIds = new ArrayList<>();

    for (int pageNumber = 0; ; pageNumber++) {
      ProductSearchPage page = search(pageNumber);
      seenIds.addAll(page.content().stream().map(row -> row.id()).toList());
      if (pageNumber + 1 >= page.totalPages()) {
        break;
      }
    }

    Map<UUID, Long> occurrences = new HashMap<>();
    seenIds.forEach(id -> occurrences.merge(id, 1L, Long::sum));
    assertThat(seenIds).hasSize(PRODUCTS);
    assertThat(occurrences)
        .hasSize(PRODUCTS)
        .allSatisfy((id, count) -> assertThat(count).isEqualTo(1L));
  }

  private ProductSearchPage search(int page) {
    return productSearchUseCase.search(
        new ProductSearchCriteria(
            null,
            null,
            null,
            null,
            java.util.Set.of(),
            false,
            page,
            7,
            new ProductSearchOrder("price", ProductSearchOrder.Direction.ASC)));
  }

  private void insertProduct(int index) {
    UUID id = UUID.nameUUIDFromBytes(("pagination-" + index).getBytes(StandardCharsets.UTF_8));
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, ?, ?, ?, ?, 'ACTIVE', current_timestamp, 0)",
        id,
        "Product " + index,
        "PAG-" + index,
        100 + index % 2,
        CATEGORY_ID);
  }
}
