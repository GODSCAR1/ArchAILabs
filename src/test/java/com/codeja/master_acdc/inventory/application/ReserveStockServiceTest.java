package com.codeja.master_acdc.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import com.codeja.master_acdc.inventory.domain.port.StockRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReserveStockServiceTest {

  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID X = UUID.fromString("99999999-9999-9999-9999-999999999999");

  private final StockRepository stockRepository = mock(StockRepository.class);
  private final ReserveStockService service = new ReserveStockService(stockRepository);

  @Test
  void should_discount_each_product_when_all_lines_have_stock() {
    given(stockRepository.findExistingProductIds(List.of(A, B))).willReturn(Set.of(A, B));
    given(stockRepository.discountIfAvailable(A, 3)).willReturn(true);
    given(stockRepository.discountIfAvailable(B, 2)).willReturn(true);

    ReservationResult result = service.reserve(List.of(new StockLine(A, 3), new StockLine(B, 2)));

    assertThat(result).isEqualTo(ReservationResult.RESERVED);
    then(stockRepository).should().discountIfAvailable(A, 3);
    then(stockRepository).should().discountIfAvailable(B, 2);
  }

  @Test
  void should_reject_complete_reservation_when_any_line_lacks_stock() {
    given(stockRepository.findExistingProductIds(List.of(A, B))).willReturn(Set.of(A, B));
    given(stockRepository.discountIfAvailable(A, 3)).willReturn(true);
    given(stockRepository.discountIfAvailable(B, 2)).willReturn(false);

    ReservationResult result = service.reserve(List.of(new StockLine(A, 3), new StockLine(B, 2)));

    assertThat(result).isEqualTo(ReservationResult.INSUFFICIENT_STOCK);
  }

  @Test
  void should_report_unknown_product_when_stock_record_missing() {
    given(stockRepository.findExistingProductIds(List.of(A, X))).willReturn(Set.of(A));

    ReservationResult result = service.reserve(List.of(new StockLine(A, 3), new StockLine(X, 1)));

    assertThat(result).isEqualTo(ReservationResult.UNKNOWN_PRODUCT);
    then(stockRepository).should(never()).discountIfAvailable(any(), anyInt());
  }
}
