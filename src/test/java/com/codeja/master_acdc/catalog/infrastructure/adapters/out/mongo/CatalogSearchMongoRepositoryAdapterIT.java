package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.application.ProductSearchService;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.infrastructure.adapters.in.web.ProductController;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CatalogSearchMongoRepositoryAdapterIT {

  private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID LAPTOP_ID = UUID.fromString("20000000-0000-0000-0000-000000000010");
  private static final UUID DOTTED_ID = UUID.fromString("20000000-0000-0000-0000-000000000020");
  private static final UUID ALPHA_UPPER_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000030");
  private static final UUID ALPHA_LOWER_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000040");
  private static final UUID ALPHA_ACCENT_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000050");

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static MongoClient client;
  private static MongoTemplate template;
  private ProductSearchMongoRepositoryAdapter adapter;

  @BeforeAll
  static void initialize_mongo_template() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
    template = new MongoTemplate(client, "catalog_search_test");
  }

  @AfterAll
  static void close_client() {
    client.close();
  }

  @BeforeEach
  void initialize_adapter_and_products() {
    template.getDb().drop();
    new CatalogIndexInitializer(template).initialize();
    adapter = new ProductSearchMongoRepositoryAdapter(template);
    template.insertAll(products());
  }

  @Test
  @DisplayName("Consulta sin filtros")
  void should_return_all_products_when_no_filter_is_applied() {
    var result = adapter.search(criteria(null, SearchFilters.none(), "name"));

    assertThat(result.totalElements()).isEqualTo(5);
    assertThat(result.content())
        .extracting("name")
        .containsExactly("A.B Lamp", "Banana", "Laptop", "apple", "Éclair");
  }

  @Test
  @DisplayName("Filtros de texto categoría precio y estados")
  void should_apply_text_category_price_and_status_filters_when_matching_products() {
    var result =
        adapter.search(
            criteria(
                "lap",
                new SearchFilters(CATEGORY_ID, 100L, 200L, Set.of(ProductStatus.ACTIVE), false),
                "price"));

    assertThat(result.content()).extracting("id").containsExactly(LAPTOP_ID);
  }

  @Test
  @DisplayName("Los seis filtros combinados")
  void should_return_only_matching_product_when_all_filters_are_applied() {
    var result =
        adapter.search(
            criteria(
                "LAP",
                new SearchFilters(CATEGORY_ID, 100L, 200L, Set.of(ProductStatus.ACTIVE), true),
                "price"));

    assertThat(result.content()).extracting("id").containsExactly(LAPTOP_ID);
  }

  @Test
  @DisplayName("Ordenación por precio con desempate")
  void should_order_equal_prices_by_id_when_sorting_by_price() {
    var result = adapter.search(criteria(null, SearchFilters.none(), "price"));

    assertThat(result.content().getFirst().id()).isEqualTo(ALPHA_UPPER_ID);
  }

  @Test
  @DisplayName("Paginación estable con más de cincuenta productos")
  void should_page_products_when_result_set_exceeds_fifty_rows() {
    template.remove(new org.springframework.data.mongodb.core.query.Query(), CatalogProduct.class);
    List<CatalogProduct> products =
        java.util.stream.IntStream.range(1, 61)
            .mapToObj(
                index ->
                    product(
                        UUID.fromString("30000000-0000-0000-0000-%012d".formatted(index)),
                        "Duplicate",
                        100,
                        ProductStatus.ACTIVE,
                        0))
            .toList();
    template.insertAll(products);

    var firstPage = adapter.search(criteria(null, SearchFilters.none(), "name", 0, 50));
    var secondPage = adapter.search(criteria(null, SearchFilters.none(), "name", 1, 50));

    assertThat(firstPage.totalElements()).isEqualTo(60);
    assertThat(firstPage.content()).hasSize(50);
    assertThat(secondPage.content()).hasSize(10);
    assertThat(firstPage.content().getFirst().id()).isEqualTo(products.getFirst().id());
    assertThat(secondPage.content().getFirst().id()).isEqualTo(products.get(50).id());
  }

  @Test
  @DisplayName("Estado repetido")
  void should_apply_status_filter_when_status_is_repeated() {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("status", "ACTIVE");
    parameters.add("status", "ACTIVE");
    parameters.add("sort", "name,asc");
    ProductController controller =
        new ProductController(null, null, new ProductSearchService(adapter));

    var result = controller.search(parameters);

    assertThat(result.content())
        .extracting("id")
        .containsExactly(ALPHA_UPPER_ID, LAPTOP_ID, ALPHA_LOWER_ID, ALPHA_ACCENT_ID);
  }

  @Test
  @DisplayName("Buscador equivalente")
  void should_match_special_text_and_sort_names_when_searching() {
    var textResult = adapter.search(criteria("a.b", SearchFilters.none(), "name"));
    var nameResult = adapter.search(criteria(null, SearchFilters.none(), "name"));

    assertThat(textResult.content()).extracting("id").containsExactly(DOTTED_ID);
    assertThat(nameResult.content())
        .extracting("id")
        .containsExactly(DOTTED_ID, ALPHA_UPPER_ID, LAPTOP_ID, ALPHA_LOWER_ID, ALPHA_ACCENT_ID);
    assertThat(nameResult.content())
        .extracting("name")
        .containsExactly("A.B Lamp", "Banana", "Laptop", "apple", "Éclair");
  }

  private ProductSearchCriteria criteria(String text, SearchFilters filters, String order) {
    return criteria(text, filters, order, 0, 20);
  }

  private ProductSearchCriteria criteria(
      String text, SearchFilters filters, String order, int page, int size) {
    return new ProductSearchCriteria(
        text,
        filters.categoryId(),
        filters.minPrice(),
        filters.maxPrice(),
        filters.statuses(),
        filters.inStock(),
        page,
        size,
        new ProductSearchOrder(order, ProductSearchOrder.Direction.ASC));
  }

  private List<CatalogProduct> products() {
    return List.of(
        product(DOTTED_ID, "A.B Lamp", 200, ProductStatus.DRAFT, 0),
        product(ALPHA_UPPER_ID, "Banana", 100, ProductStatus.ACTIVE, 2),
        product(LAPTOP_ID, "Laptop", 150, ProductStatus.ACTIVE, 2),
        product(ALPHA_LOWER_ID, "apple", 100, ProductStatus.ACTIVE, 0),
        product(ALPHA_ACCENT_ID, "Éclair", 100, ProductStatus.ACTIVE, 4));
  }

  private CatalogProduct product(
      UUID id, String name, long price, ProductStatus status, int availableStock) {
    return new CatalogProduct(
        id,
        "SKU-" + id,
        name,
        price,
        status,
        Instant.parse("2026-01-01T00:00:00Z"),
        0,
        new CategoryRef(CATEGORY_ID, "Electronics"),
        List.of(),
        Map.of(),
        availableStock,
        new RatingSummary(BigDecimal.ZERO.setScale(2), 0),
        List.of());
  }

  private record SearchFilters(
      UUID categoryId, Long minPrice, Long maxPrice, Set<ProductStatus> statuses, boolean inStock) {

    private static SearchFilters none() {
      return new SearchFilters(null, null, null, Set.of(), false);
    }
  }
}
