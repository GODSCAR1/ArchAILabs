package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.GlobalMongoClientTestConfiguration;
import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CategoryRef;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.RatingSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Import(GlobalMongoClientTestConfiguration.class)
class ProductSearchQueryIT {

  private static final UUID PRODUCT_WITHOUT_STOCK =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID PRODUCT_WITH_STOCK =
      UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final UUID PRODUCT_CAFE = UUID.fromString("10000000-0000-0000-0000-000000000003");
  private static final UUID PRODUCT_LAMP = UUID.fromString("10000000-0000-0000-0000-000000000004");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductSearchUseCase productSearchUseCase;
  @Autowired MongoTemplate mongoTemplate;

  @BeforeEach
  void cleanDatabase() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    mongoTemplate.insert(product(PRODUCT_WITHOUT_STOCK, "No stock", ProductStatus.DRAFT, 100, 0));
    mongoTemplate.insert(product(PRODUCT_WITH_STOCK, "Laptop", ProductStatus.ACTIVE, 200, 4));
    mongoTemplate.insert(product(PRODUCT_CAFE, "Cafe", ProductStatus.ACTIVE, 300, 0));
    mongoTemplate.insert(product(PRODUCT_LAMP, "Lamp", ProductStatus.DISCONTINUED, 400, 0));
  }

  @Test
  @DisplayName("Filtro de disponibilidad sin registro")
  void should_exclude_product_without_stock_when_in_stock_filter_is_true() {
    ProductSearchPage result = search(true);

    assertThat(result.content()).extracting("id").containsExactly(PRODUCT_WITH_STOCK);
  }

  @Test
  @DisplayName("Filtro de disponibilidad con stock positivo")
  void should_include_product_with_positive_stock_when_in_stock_filter_is_true() {
    ProductSearchPage result = search(true);

    assertThat(result.content().getFirst().stock()).isEqualTo(4);
  }

  @ParameterizedTest(name = "{0}")
  @DisplayName("combinaciones de filtros")
  @MethodSource("searchCases")
  void should_return_expected_ids_when_filter_combination_is_applied(SearchCase searchCase) {
    ProductSearchPage result = productSearchUseCase.search(searchCase.criteria());

    assertThat(result.content())
        .extracting("id")
        .containsExactlyElementsOf(searchCase.expectedIds());
  }

  static List<SearchCase> searchCases() {
    return List.of(
        new SearchCase(
            "filtro vacío",
            criteria(new SearchFilters("name", null, null, null, Set.of(), false)),
            List.of(PRODUCT_CAFE, PRODUCT_LAMP, PRODUCT_WITH_STOCK, PRODUCT_WITHOUT_STOCK)),
        new SearchCase(
            "texto LAP",
            criteria(new SearchFilters("name", "LAP", null, null, Set.of(), false)),
            List.of(PRODUCT_WITH_STOCK)),
        new SearchCase(
            "categoría",
            criteria(new SearchFilters("price", null, null, null, Set.of(), false)),
            List.of(PRODUCT_WITHOUT_STOCK, PRODUCT_WITH_STOCK, PRODUCT_CAFE, PRODUCT_LAMP)),
        new SearchCase(
            "precio mínimo",
            criteria(new SearchFilters("price", null, 300L, null, Set.of(), false)),
            List.of(PRODUCT_CAFE, PRODUCT_LAMP)),
        new SearchCase(
            "precio máximo",
            criteria(new SearchFilters("price", null, null, 200L, Set.of(), false)),
            List.of(PRODUCT_WITHOUT_STOCK, PRODUCT_WITH_STOCK)),
        new SearchCase(
            "estado",
            criteria(
                new SearchFilters("price", null, null, null, Set.of(ProductStatus.ACTIVE), false)),
            List.of(PRODUCT_WITH_STOCK, PRODUCT_CAFE)),
        new SearchCase(
            "stock positivo",
            criteria(new SearchFilters("price", null, null, null, Set.of(), true)),
            List.of(PRODUCT_WITH_STOCK)),
        new SearchCase(
            "Los seis filtros combinados",
            criteria(
                new SearchFilters("price", "LAP", 100L, 500L, Set.of(ProductStatus.ACTIVE), true)),
            List.of(PRODUCT_WITH_STOCK)));
  }

  record SearchCase(String name, ProductSearchCriteria criteria, List<UUID> expectedIds) {}

  record SearchFilters(
      String order,
      String text,
      Long minPrice,
      Long maxPrice,
      Set<ProductStatus> statuses,
      boolean inStock) {}

  private ProductSearchPage search(boolean inStock) {
    return productSearchUseCase.search(
        new ProductSearchCriteria(
            null,
            null,
            null,
            null,
            Set.of(),
            inStock,
            0,
            20,
            new ProductSearchOrder("createdAt", ProductSearchOrder.Direction.ASC)));
  }

  private static ProductSearchCriteria criteria(SearchFilters filters) {
    return new ProductSearchCriteria(
        filters.text(),
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        filters.minPrice(),
        filters.maxPrice(),
        filters.statuses(),
        filters.inStock(),
        0,
        20,
        new ProductSearchOrder(filters.order(), ProductSearchOrder.Direction.ASC));
  }

  private CatalogProduct product(
      UUID id, String name, ProductStatus status, long price, int stock) {
    return new CatalogProduct(
        id,
        "SKU-" + id,
        name,
        price,
        status,
        Instant.parse("2026-01-01T00:00:00Z"),
        0,
        new CategoryRef(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Sin categoría"),
        List.of(),
        Map.of(),
        stock,
        new RatingSummary(BigDecimal.ZERO.setScale(2), 0),
        List.of());
  }
}
