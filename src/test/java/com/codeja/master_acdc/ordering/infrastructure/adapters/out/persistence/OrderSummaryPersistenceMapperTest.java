package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderSummaryPersistenceMapperTest {
  @Test
  void should_map_native_aggregates_when_reading_summary_row() {
    UUID id = UUID.randomUUID();
    assertThat(OrderSummaryPersistenceMapper.toDomain(new OrderSummaryRow(id, null, 2L, 5L)))
        .isEqualTo(new OrderSummary(id, null, 2, 5));
  }

  @Test
  void should_preserve_null_name_and_zero_metrics_when_mapping_summary() {
    UUID id = UUID.randomUUID();
    assertThat(OrderSummaryPersistenceMapper.toDomain(new OrderSummaryRow(id, null, 0L, 0L)))
        .isEqualTo(new OrderSummary(id, null, 0, 0));
    assertThatThrownBy(
            () ->
                OrderSummaryPersistenceMapper.toDomain(
                    new OrderSummaryRow(id, "name", Long.MAX_VALUE, 1L)))
        .isInstanceOf(ArithmeticException.class);
  }
}
