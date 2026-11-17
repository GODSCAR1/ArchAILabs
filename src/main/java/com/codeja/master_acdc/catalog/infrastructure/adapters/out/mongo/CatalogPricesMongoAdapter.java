package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.api.CatalogPrices;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class CatalogPricesMongoAdapter implements CatalogPrices {
  private final MongoTemplate mongo;

  public CatalogPricesMongoAdapter(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @Override
  public List<ProductPrice> findByIds(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Query query = Query.query(Criteria.where("_id").in(ids));
    query.fields().include("_id").include("sku").include("price");
    return mongo.query(CatalogProduct.class).as(ProductPrice.class).matching(query).all();
  }
}
