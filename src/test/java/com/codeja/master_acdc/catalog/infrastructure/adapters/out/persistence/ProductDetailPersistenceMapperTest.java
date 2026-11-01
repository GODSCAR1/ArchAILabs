package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductDetailPersistenceMapperTest {

  @Test
  void should_map_native_variant_row_when_row_is_complete() {
    UUID id = UUID.randomUUID();

    var result =
        ProductDetailPersistenceMapper.toVariant(new Object[] {id, "-RED", "Red", "M", 250L});

    assertThat(result.id()).isEqualTo(id);
    assertThat(result.skuSuffix()).isEqualTo("-RED");
    assertThat(result.additionalPrice()).isEqualTo(250);
  }

  @Test
  void should_map_native_review_row_when_timestamp_is_sql_timestamp() {
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");

    var result =
        ProductDetailPersistenceMapper.toReview(
            new Object[] {id, "Ana", 5, "Great", Timestamp.from(createdAt)});

    assertThat(result.id()).isEqualTo(id);
    assertThat(result.createdAt()).isEqualTo(createdAt);
  }
}
