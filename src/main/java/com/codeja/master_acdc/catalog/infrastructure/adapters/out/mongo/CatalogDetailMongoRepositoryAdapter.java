package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;
import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class CatalogDetailMongoRepositoryAdapter implements ProductDetailRepository {

  private final MongoTemplate mongoTemplate;

  public CatalogDetailMongoRepositoryAdapter(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Optional<ProductDetail> findBySku(String sku) {
    CatalogProduct product = mongoTemplate.findOne(productQuery(sku), CatalogProduct.class);
    return Optional.ofNullable(product).map(CatalogProductMongoMapper::toDomain);
  }

  @Override
  public Optional<ProductReviewPage> findReviewsBySku(String sku, int page, int size) {
    CatalogProduct product = mongoTemplate.findOne(productQuery(sku), CatalogProduct.class);
    if (product == null) {
      return Optional.empty();
    }
    return Optional.of(reviewPage(product.id(), page, size));
  }

  private ProductReviewPage reviewPage(java.util.UUID productId, int page, int size) {
    Query query =
        Query.query(Criteria.where("productId").is(productId))
            .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
            .skip((long) page * size)
            .limit(size);
    var reviews =
        mongoTemplate.find(query, CatalogReview.class).stream().map(this::toDomain).toList();
    long totalElements =
        mongoTemplate.count(
            Query.query(Criteria.where("productId").is(productId)), CatalogReview.class);
    return new ProductReviewPage(
        reviews, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
  }

  private Query productQuery(String sku) {
    return Query.query(Criteria.where("sku").is(sku));
  }

  private ProductReview toDomain(CatalogReview review) {
    return new ProductReview(
        review.id(), review.author(), review.rating(), review.comment(), review.createdAt());
  }
}
