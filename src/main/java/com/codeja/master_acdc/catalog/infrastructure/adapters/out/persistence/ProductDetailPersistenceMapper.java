package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductVariant;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ProductDetailPersistenceMapper {

  private ProductDetailPersistenceMapper() {}

  public static ProductVariant toVariant(Object[] row) {
    return new ProductVariant(
        (UUID) row[0],
        (String) row[1],
        (String) row[2],
        (String) row[3],
        ((Number) row[4]).longValue());
  }

  public static ProductReview toReview(Object[] row) {
    Object timestamp = row[4];
    Instant createdAt =
        timestamp instanceof Instant instant
            ? instant
            : timestamp instanceof OffsetDateTime offsetDateTime
                ? offsetDateTime.toInstant()
                : timestamp instanceof java.sql.Timestamp sqlTimestamp
                    ? sqlTimestamp.toInstant()
                    : ((java.util.Date) timestamp).toInstant();
    return new ProductReview(
        (UUID) row[0], (String) row[1], ((Number) row[2]).intValue(), (String) row[3], createdAt);
  }
}
