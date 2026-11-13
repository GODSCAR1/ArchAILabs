package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.event.ProductChanged;
import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public final class CatalogStockProjectionJob {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final MongoTemplate mongoTemplate;
  private final int batchSize;
  private final ProductChangedPublisher productChangedPublisher;

  public CatalogStockProjectionJob(
      NamedParameterJdbcTemplate jdbcTemplate,
      MongoTemplate mongoTemplate,
      int batchSize,
      ProductChangedPublisher productChangedPublisher) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.jdbcTemplate = jdbcTemplate;
    this.mongoTemplate = mongoTemplate;
    this.batchSize = batchSize;
    this.productChangedPublisher = productChangedPublisher;
  }

  @Scheduled(fixedDelayString = "${catalog.stock-sync.interval:PT1M}")
  public void synchronizeStock() {
    UUID lastId = null;
    List<CatalogProductIdView> products;
    do {
      Query query =
          new Query().limit(batchSize).with(org.springframework.data.domain.Sort.by("_id"));
      query.fields().include("_id").include("sku").include("availableStock");
      if (lastId != null) {
        query.addCriteria(Criteria.where("_id").gt(lastId));
      }
      products =
          mongoTemplate
              .query(CatalogProduct.class)
              .as(CatalogProductIdView.class)
              .matching(query)
              .all();
      if (!products.isEmpty()) {
        synchronizeBatch(products);
        lastId = products.getLast().id();
      }
    } while (products.size() == batchSize);
  }

  private void synchronizeBatch(List<CatalogProductIdView> products) {
    List<UUID> ids = products.stream().map(CatalogProductIdView::id).toList();
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
    List<String> changedSkus = new ArrayList<>();
    for (CatalogProductIdView product : products) {
      int currentStock = availability.getOrDefault(product.id(), 0);
      if (product.availableStock() == currentStock) {
        continue;
      }
      bulk.updateOne(
          Query.query(Criteria.where("_id").is(product.id())),
          new Update().set("availableStock", currentStock));
      changedSkus.add(product.sku());
    }
    if (changedSkus.isEmpty()) {
      return;
    }
    bulk.execute();
    changedSkus.forEach(sku -> productChangedPublisher.publish(new ProductChanged(sku)));
  }
}
