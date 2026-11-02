package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class ProductMongoRepositoryAdapter implements ProductRepository {

  private final MongoTemplate mongoTemplate;

  public ProductMongoRepositoryAdapter(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public boolean existsBySku(Sku sku) {
    return mongoTemplate.exists(
        Query.query(Criteria.where("sku").is(sku.value())), CatalogProduct.class);
  }

  @Override
  public void save(Product product) {
    try {
      mongoTemplate.insert(ProductMongoMapper.toDocument(product));
    } catch (DuplicateKeyException exception) {
      throw new DuplicateSkuException(product.sku().value(), exception);
    }
  }
}
