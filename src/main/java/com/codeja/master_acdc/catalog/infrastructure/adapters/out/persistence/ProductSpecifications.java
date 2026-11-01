package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import jakarta.persistence.criteria.JoinType;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class ProductSpecifications {

  private ProductSpecifications() {}

  static Specification<ProductJpaEntity> textContains(String text) {
    return (root, query, builder) ->
        builder.like(
            builder.lower(root.get(ProductJpaEntity_.name)), "%" + text.toLowerCase() + "%");
  }

  static Specification<ProductJpaEntity> categoryIs(UUID categoryId) {
    return (root, query, builder) ->
        builder.equal(root.get(ProductJpaEntity_.categoryId), categoryId);
  }

  static Specification<ProductJpaEntity> priceAtLeast(long minPrice) {
    return (root, query, builder) ->
        builder.greaterThanOrEqualTo(root.get(ProductJpaEntity_.price), minPrice);
  }

  static Specification<ProductJpaEntity> priceAtMost(long maxPrice) {
    return (root, query, builder) ->
        builder.lessThanOrEqualTo(root.get(ProductJpaEntity_.price), maxPrice);
  }

  static Specification<ProductJpaEntity> statusIn(Collection<ProductStatus> statuses) {
    return (root, query, builder) ->
        root.get(ProductJpaEntity_.status).in(statuses.stream().map(Enum::name).toList());
  }

  static Specification<ProductJpaEntity> hasStock() {
    return (root, query, builder) ->
        builder.greaterThan(
            root.join(ProductJpaEntity_.stock).get(CatalogStockJpaEntity_.available), 0);
  }

  static Specification<ProductJpaEntity> fetchAssociations() {
    return (root, query, builder) -> {
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        root.fetch(ProductJpaEntity_.category, JoinType.LEFT);
        root.fetch(ProductJpaEntity_.stock, JoinType.LEFT);
      }
      return null;
    };
  }
}
