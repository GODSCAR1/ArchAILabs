package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public class CatalogStockProjectionJob {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final MongoTemplate mongoTemplate;
  private final int batchSize;

  public CatalogStockProjectionJob(
      NamedParameterJdbcTemplate jdbcTemplate, MongoTemplate mongoTemplate, int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.jdbcTemplate = jdbcTemplate;
    this.mongoTemplate = mongoTemplate;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${catalog.stock-sync.interval:PT1M}")
  public void synchronizeStock() {
    UUID lastId = null;
    List<Document> products;
    do {
      Query query =
          new Query().limit(batchSize).with(org.springframework.data.domain.Sort.by("_id"));
      query.fields().include("_id");
      if (lastId != null) {
        query.addCriteria(Criteria.where("_id").gt(lastId));
      }
      products = mongoTemplate.find(query, Document.class, "catalog_products");
      if (!products.isEmpty()) {
        synchronizeBatch(products.stream().map(product -> product.get("_id", UUID.class)).toList());
        lastId = products.getLast().get("_id", UUID.class);
      }
    } while (products.size() == batchSize);
  }

  private void synchronizeBatch(List<UUID> ids) {
    Map<UUID, Integer> availability =
        jdbcTemplate.query(
            "select product_id, available from stock where product_id in (:ids)",
            Map.of("ids", ids),
            result -> {
              Map<UUID, Integer> values = new java.util.HashMap<>();
              while (result.next()) {
                values.put(result.getObject("product_id", UUID.class), result.getInt("available"));
              }
              return values;
            });
    BulkOperations bulk =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, CatalogProduct.class);
    for (UUID id : ids) {
      bulk.updateOne(
          Query.query(Criteria.where("_id").is(id)),
          new Update().set("availableStock", availability.getOrDefault(id, 0)));
    }
    bulk.execute();
  }
}
