package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class CatalogIndexInitializer {

  private final MongoTemplate mongoTemplate;

  public CatalogIndexInitializer(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public void initialize() {
    ensureIndexes();
  }

  private void ensureIndexes() {
    var indexOperations = mongoTemplate.indexOps(CatalogProduct.class);
    indexOperations.ensureIndex(
        new Index().on("sku", Sort.Direction.ASC).unique().named("sku_unique"));
    indexOperations.ensureIndex(
        new Index()
            .on("category._id", Sort.Direction.ASC)
            .on("price", Sort.Direction.ASC)
            .named("category_price"));
  }
}
