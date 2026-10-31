package com.codeja.master_acdc.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.catalog.domain.exception.InvalidPriceRangeException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidSortFieldException;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSearchServiceTest {

  @Test
  @DisplayName("delega los criterios de búsqueda al repositorio")
  void should_delegate_search_criteria_when_search_is_requested() {
    ProductSearchRepository repository = mock(ProductSearchRepository.class);
    ProductSearchService service = new ProductSearchService(repository);
    ProductSearchCriteria criteria =
        new ProductSearchCriteria(
            "cafe",
            null,
            100L,
            500L,
            Set.of(),
            false,
            2,
            20,
            new ProductSearchOrder("price", ProductSearchOrder.Direction.DESC));
    ProductSearchPage expected = new ProductSearchPage(List.of(), 2, 20, 0, 0);
    when(repository.search(criteria)).thenReturn(expected);

    assertThat(service.search(criteria)).isSameAs(expected);
    verify(repository).search(criteria);
  }

  @Test
  void should_reject_price_range_when_minimum_exceeds_maximum() {
    assertThatThrownBy(
            () ->
                criteria(
                    5001L,
                    5000L,
                    new ProductSearchOrder("price", ProductSearchOrder.Direction.ASC)))
        .isInstanceOf(InvalidPriceRangeException.class);
  }

  @Test
  void should_reject_sort_field_when_field_is_not_allowed() {
    assertThatThrownBy(
            () ->
                criteria(
                    null, null, new ProductSearchOrder("sku", ProductSearchOrder.Direction.ASC)))
        .isInstanceOf(InvalidSortFieldException.class);
  }

  private ProductSearchCriteria criteria(Long minPrice, Long maxPrice, ProductSearchOrder order) {
    return new ProductSearchCriteria(null, null, minPrice, maxPrice, Set.of(), false, 0, 20, order);
  }
}
