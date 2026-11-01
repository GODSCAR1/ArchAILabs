package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.ProductVariant;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class CatalogProductMigrationService {

  private static final int MAX_VARIANTS = 100;
  private static final String CHECKPOINT_ID = "catalog-products";
  private static final String FIRST_PRODUCT_BATCH_QUERY =
      "select p.id, p.sku, p.name, p.price, p.status, p.created_at, p.version, "
          + "c.id category_id, c.name category_name, coalesce(s.available, 0) available_stock "
          + "from products p join categories c on c.id = p.category_id "
          + "left join stock s on s.product_id = p.id "
          + "order by p.id limit :batchSize";
  private static final String NEXT_PRODUCT_BATCH_QUERY =
      "select p.id, p.sku, p.name, p.price, p.status, p.created_at, p.version, "
          + "c.id category_id, c.name category_name, coalesce(s.available, 0) available_stock "
          + "from products p join categories c on c.id = p.category_id "
          + "left join stock s on s.product_id = p.id "
          + "where p.id > :lastProcessedId order by p.id limit :batchSize";

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final MongoTemplate mongoTemplate;

  public CatalogProductMigrationService(
      NamedParameterJdbcTemplate jdbcTemplate, MongoTemplate mongoTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.mongoTemplate = mongoTemplate;
  }

  public CatalogProductMigrationBatch migrateBatch(UUID lastProcessedId, int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    List<ProductSource> products = readProducts(lastProcessedId, batchSize);
    if (products.isEmpty()) {
      return new CatalogProductMigrationBatch(lastProcessedId, true, 0, 0, 0, List.of());
    }
    UUID nextId = products.getLast().id();
    List<UUID> ids = products.stream().map(ProductSource::id).toList();
    Map<UUID, List<ProductVariant>> variants = readVariants(ids);
    List<ProductSource> accepted = acceptedProducts(products, variants);
    List<String> rejectedSkus = rejectedSkus(products, accepted);
    deleteRejectedProducts(products, accepted);
    int variantsProcessed =
        accepted.stream()
            .mapToInt(product -> variants.getOrDefault(product.id(), List.of()).size())
            .sum();
    int reviewsProcessed = saveAcceptedProducts(accepted, variants);
    return new CatalogProductMigrationBatch(
        nextId,
        products.size() < batchSize,
        accepted.size(),
        variantsProcessed,
        reviewsProcessed,
        rejectedSkus);
  }

  public CatalogProductMigrationBatch migrateNextBatch(int batchSize) {
    CatalogMigrationCheckpoint checkpoint = checkpointForNextCycle();
    CatalogProductMigrationBatch batch = migrateBatch(checkpoint.lastProcessedId(), batchSize);
    MigrationState state = batch.complete() ? MigrationState.COMPLETADO : MigrationState.EN_CURSO;
    mongoTemplate.save(
        new CatalogMigrationCheckpoint(CHECKPOINT_ID, batch.lastProcessedId(), state));
    return batch;
  }

  public CatalogMigrationReport migrateAll(int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    CatalogMigrationCheckpoint checkpoint = checkpointForNextCycle();
    int productsProcessed = 0;
    int variantsProcessed = 0;
    int reviewsProcessed = 0;
    List<String> rejectedSkus = new ArrayList<>();
    while (checkpoint.state() == MigrationState.EN_CURSO) {
      CatalogProductMigrationBatch batch = migrateNextBatch(batchSize);
      productsProcessed += batch.migratedProducts();
      variantsProcessed += batch.variantsProcessed();
      reviewsProcessed += batch.reviewsProcessed();
      rejectedSkus.addAll(batch.rejectedSkus());
      checkpoint = mongoTemplate.findById(CHECKPOINT_ID, CatalogMigrationCheckpoint.class);
    }
    return new CatalogMigrationReport(
        productsProcessed,
        variantsProcessed,
        reviewsProcessed,
        rejectedSkus,
        checkpoint.lastProcessedId(),
        checkpoint.state());
  }

  private CatalogMigrationCheckpoint checkpointForNextCycle() {
    CatalogMigrationCheckpoint current =
        mongoTemplate.findById(CHECKPOINT_ID, CatalogMigrationCheckpoint.class);
    if (current == null || current.state() == MigrationState.COMPLETADO) {
      current = new CatalogMigrationCheckpoint(CHECKPOINT_ID, null, MigrationState.EN_CURSO);
      mongoTemplate.save(current);
    }
    return current;
  }

  private List<ProductSource> readProducts(UUID lastProcessedId, int batchSize) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("lastProcessedId", lastProcessedId)
            .addValue("batchSize", batchSize);
    String sql = lastProcessedId == null ? FIRST_PRODUCT_BATCH_QUERY : NEXT_PRODUCT_BATCH_QUERY;
    return jdbcTemplate.query(sql, parameters, new ProductSourceRowMapper());
  }

  private void deleteRejectedProducts(List<ProductSource> products, List<ProductSource> accepted) {
    products.stream()
        .filter(product -> !accepted.contains(product))
        .forEach(
            product ->
                mongoTemplate.remove(
                    Query.query(Criteria.where("_id").is(product.id())), CatalogProduct.class));
  }

  private Map<UUID, List<ProductVariant>> readVariants(List<UUID> productIds) {
    String sql =
        "select id, product_id, sku_suffix, color, size, additional_price "
            + "from product_variants where product_id in (:productIds) "
            + "order by product_id, id";
    Map<UUID, List<ProductVariant>> variants = new HashMap<>();
    jdbcTemplate.query(
        sql,
        Map.of("productIds", productIds),
        (RowCallbackHandler)
            row ->
                variants
                    .computeIfAbsent(
                        row.getObject("product_id", UUID.class), ignored -> new ArrayList<>())
                    .add(
                        new ProductVariant(
                            row.getObject("id", UUID.class),
                            row.getString("sku_suffix"),
                            row.getString("color"),
                            row.getString("size"),
                            row.getLong("additional_price"))));
    return variants;
  }

  private List<ProductSource> acceptedProducts(
      List<ProductSource> products, Map<UUID, List<ProductVariant>> variants) {
    return products.stream()
        .filter(product -> variants.getOrDefault(product.id(), List.of()).size() <= MAX_VARIANTS)
        .toList();
  }

  private List<String> rejectedSkus(List<ProductSource> products, List<ProductSource> accepted) {
    return products.stream()
        .filter(product -> !accepted.contains(product))
        .map(ProductSource::sku)
        .toList();
  }

  private int saveAcceptedProducts(
      List<ProductSource> products, Map<UUID, List<ProductVariant>> variants) {
    if (products.isEmpty()) {
      return 0;
    }
    List<UUID> ids = products.stream().map(ProductSource::id).toList();
    Map<UUID, Map<String, String>> attributes = readAttributes(ids);
    Map<UUID, ProductRating> ratings = readRatings(ids);
    Map<UUID, List<ProductReview>> reviews = readLatestReviews(ids);
    for (ProductSource product : products) {
      CatalogProduct document =
          CatalogProductMongoMapper.toDocument(
              product.toDomain(
                  variants.getOrDefault(product.id(), List.of()),
                  attributes.getOrDefault(product.id(), Map.of()),
                  ratings.getOrDefault(product.id(), ProductRating.empty()),
                  reviews.getOrDefault(product.id(), List.of())),
              product.createdAt(),
              product.version());
      mongoTemplate.findAndReplace(
          Query.query(Criteria.where("sku").is(document.sku())),
          document,
          FindAndReplaceOptions.options().upsert());
    }
    return migrateFullReviews(ids);
  }

  private int migrateFullReviews(List<UUID> productIds) {
    String sql =
        "select id, product_id, author, rating, comment, created_at from reviews "
            + "where product_id in (:productIds)";
    BulkOperations operations = mongoTemplate.bulkOps(BulkMode.UNORDERED, CatalogReview.class);
    int[] reviewCount = {0};
    jdbcTemplate.query(
        sql,
        Map.of("productIds", productIds),
        (RowCallbackHandler)
            row -> {
              UUID id = row.getObject("id", UUID.class);
              operations.upsert(
                  Query.query(Criteria.where("id").is(id)),
                  new Update()
                      .set("productId", row.getObject("product_id", UUID.class))
                      .set("author", row.getString("author"))
                      .set("rating", row.getInt("rating"))
                      .set("comment", row.getString("comment"))
                      .set("createdAt", row.getTimestamp("created_at").toInstant()));
              reviewCount[0]++;
            });
    if (reviewCount[0] > 0) {
      operations.execute();
    }
    return reviewCount[0];
  }

  private Map<UUID, Map<String, String>> readAttributes(List<UUID> productIds) {
    Map<UUID, Map<String, String>> attributes = new HashMap<>();
    jdbcTemplate.query(
        "select product_id, name, value from product_attributes "
            + "where product_id in (:productIds) order by product_id, name",
        Map.of("productIds", productIds),
        (RowCallbackHandler)
            row ->
                attributes
                    .computeIfAbsent(
                        row.getObject("product_id", UUID.class), ignored -> new TreeMap<>())
                    .put(row.getString("name"), row.getString("value")));
    return attributes;
  }

  private Map<UUID, ProductRating> readRatings(List<UUID> productIds) {
    Map<UUID, ProductRating> ratings = new HashMap<>();
    jdbcTemplate.query(
        "select product_id, round(avg(rating)::numeric, 2) average, count(*) total "
            + "from reviews where product_id in (:productIds) group by product_id",
        Map.of("productIds", productIds),
        (RowCallbackHandler)
            row ->
                ratings.put(
                    row.getObject("product_id", UUID.class),
                    new ProductRating(
                        row.getBigDecimal("average").setScale(2, RoundingMode.HALF_UP),
                        row.getLong("total"))));
    return ratings;
  }

  private Map<UUID, List<ProductReview>> readLatestReviews(List<UUID> productIds) {
    String sql =
        "select product_id, id, author, rating, comment, created_at from (select product_id, id,"
            + " author, rating, comment, created_at, row_number() over (partition by product_id"
            + " order by created_at desc, id asc) position from reviews where product_id in"
            + " (:productIds)) recent where position <= 5 order by product_id, position";
    Map<UUID, List<ProductReview>> reviews = new HashMap<>();
    jdbcTemplate.query(
        sql,
        Map.of("productIds", productIds),
        (RowCallbackHandler)
            row ->
                reviews
                    .computeIfAbsent(
                        row.getObject("product_id", UUID.class), ignored -> new ArrayList<>())
                    .add(toReview(row)));
    return reviews;
  }

  private ProductReview toReview(ResultSet row) throws SQLException {
    return new ProductReview(
        row.getObject("id", UUID.class),
        row.getString("author"),
        row.getInt("rating"),
        row.getString("comment"),
        row.getTimestamp("created_at").toInstant());
  }

  private record ProductSource(
      UUID id,
      String sku,
      String name,
      long price,
      ProductStatus status,
      Instant createdAt,
      long version,
      UUID categoryId,
      String categoryName,
      int stock) {

    private ProductDetail toDomain(
        List<ProductVariant> variants,
        Map<String, String> attributes,
        ProductRating rating,
        List<ProductReview> reviews) {
      return new ProductDetail(
          id,
          sku,
          name,
          price,
          status,
          new Category(categoryId, categoryName),
          stock,
          variants,
          attributes,
          rating,
          reviews);
    }
  }

  private static final class ProductSourceRowMapper implements RowMapper<ProductSource> {

    @Override
    public ProductSource mapRow(ResultSet row, int rowNumber) throws SQLException {
      return new ProductSource(
          row.getObject("id", UUID.class),
          row.getString("sku"),
          row.getString("name"),
          row.getLong("price"),
          ProductStatus.valueOf(row.getString("status")),
          row.getTimestamp("created_at").toInstant(),
          row.getLong("version"),
          row.getObject("category_id", UUID.class),
          row.getString("category_name"),
          row.getInt("available_stock"));
    }
  }
}
