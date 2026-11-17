package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class CatalogPricesMongoAdapterTest {
  @Test
  @DisplayName("Una lista vacía no consulta MongoDB")
  void should_return_no_prices_when_ids_are_empty() {
    var mongo = mock(MongoTemplate.class);
    assertThat(new CatalogPricesMongoAdapter(mongo).findByIds(List.of())).isEmpty();
    verifyNoInteractions(mongo);
  }
}
