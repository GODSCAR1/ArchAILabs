package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class CatalogIndexInitializer {

  private static final String COLLECTION = "catalog_products";
  private final MongoTemplate mongoTemplate;

  public CatalogIndexInitializer(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public void initialize() {
    applyValidator();
    ensureIndexes();
  }

  private void applyValidator() {
    Document command = collectionExists() ? collModCommand() : createCommand();
    mongoTemplate.getDb().runCommand(command);
  }

  private boolean collectionExists() {
    return mongoTemplate
        .getDb()
        .listCollectionNames()
        .into(new java.util.ArrayList<>())
        .contains(COLLECTION);
  }

  private Document createCommand() {
    return new Document("create", COLLECTION).append("validator", validator());
  }

  private Document collModCommand() {
    return new Document("collMod", COLLECTION).append("validator", validator());
  }

  private Document validator() {
    return new Document("$jsonSchema", schema());
  }

  private Document schema() {
    return new Document("bsonType", "object")
        .append(
            "required",
            List.of(
                "sku",
                "name",
                "price",
                "status",
                "createdAt",
                "version",
                "category",
                "variants",
                "attributes",
                "availableStock",
                "rating",
                "latestReviews"))
        .append("properties", properties());
  }

  private Document properties() {
    return new Document("sku", stringSchema())
        .append("name", stringSchema())
        .append("price", numericSchema(1))
        .append("status", new Document("enum", List.of("DRAFT", "ACTIVE", "DISCONTINUED")))
        .append("createdAt", new Document("bsonType", "date"))
        .append("version", numericSchema(0))
        .append("category", categorySchema())
        .append("variants", arraySchema(variantSchema(), 100))
        .append("attributes", new Document("bsonType", "object"))
        .append("availableStock", numericSchema(0))
        .append("rating", ratingSchema())
        .append("latestReviews", arraySchema(reviewSchema(), 5));
  }

  private Document categorySchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("_id", "name"))
        .append(
            "properties",
            new Document("_id", new Document("bsonType", "binData"))
                .append("name", stringSchema()));
  }

  private Document variantSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("id", "skuSuffix", "color", "size", "additionalPrice"))
        .append(
            "properties",
            new Document("id", new Document("bsonType", "binData"))
                .append("skuSuffix", stringSchema())
                .append("color", stringSchema())
                .append("size", stringSchema())
                .append("additionalPrice", numericSchema(0)));
  }

  private Document ratingSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("average", "count"))
        .append(
            "properties",
            new Document(
                    "average",
                    new Document("bsonType", "decimal").append("minimum", 0).append("maximum", 5))
                .append("count", numericSchema(0)));
  }

  private Document reviewSchema() {
    return new Document("bsonType", "object")
        .append("required", List.of("id", "author", "rating", "comment", "createdAt"))
        .append(
            "properties",
            new Document("id", new Document("bsonType", "binData"))
                .append("author", stringSchema())
                .append(
                    "rating",
                    new Document("bsonType", "int").append("minimum", 1).append("maximum", 5))
                .append("comment", stringSchema())
                .append("createdAt", new Document("bsonType", "date")));
  }

  private Document arraySchema(Document itemSchema, int maxItems) {
    return new Document("bsonType", "array")
        .append("maxItems", maxItems)
        .append("items", itemSchema);
  }

  private Document numericSchema(long minimum) {
    return new Document("bsonType", List.of("int", "long")).append("minimum", minimum);
  }

  private Document stringSchema() {
    return new Document("bsonType", "string");
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
