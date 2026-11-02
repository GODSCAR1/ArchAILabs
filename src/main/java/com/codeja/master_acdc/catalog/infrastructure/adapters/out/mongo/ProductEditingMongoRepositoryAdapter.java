package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.exception.ConcurrentProductModificationException;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class ProductEditingMongoRepositoryAdapter implements ProductEditingRepository {

  private final MongoTemplate mongoTemplate;

  public ProductEditingMongoRepositoryAdapter(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Optional<Product> findById(java.util.UUID productId) {
    return Optional.ofNullable(mongoTemplate.findById(productId, CatalogProduct.class))
        .map(ProductMongoMapper::toDomain);
  }

  @Override
  public Product save(Product product) {
    Query query =
        Query.query(Criteria.where("_id").is(product.id()).and("version").is(product.version()));
    Update update =
        new Update()
            .set("name", product.name().value())
            .set("price", product.price().value())
            .inc("version", 1);
    CatalogProduct saved =
        mongoTemplate.findAndModify(
            query, update, FindAndModifyOptions.options().returnNew(true), CatalogProduct.class);
    if (saved != null) {
      return ProductMongoMapper.toDomain(saved);
    }
    if (mongoTemplate.exists(
        Query.query(Criteria.where("_id").is(product.id())), CatalogProduct.class)) {
      throw new ConcurrentProductModificationException(product.id());
    }
    throw new ProductNotFoundException(product.id());
  }
}
