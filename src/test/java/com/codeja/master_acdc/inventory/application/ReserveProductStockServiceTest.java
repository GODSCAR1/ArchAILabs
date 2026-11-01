package com.codeja.master_acdc.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.codeja.master_acdc.inventory.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException;
import com.codeja.master_acdc.inventory.domain.model.ProductReservation;
import com.codeja.master_acdc.inventory.domain.model.StockBalance;
import com.codeja.master_acdc.inventory.domain.port.StockReservationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReserveProductStockServiceTest {

  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private final StockReservationRepository stockRepository = mock(StockReservationRepository.class);
  private final ReserveProductStockService service =
      new ReserveProductStockService(stockRepository);

  @Test
  void should_return_remaining_stock_when_reservation_succeeds() {
    given(stockRepository.findForUpdateById(PRODUCT_ID))
        .willReturn(new StockBalance(PRODUCT_ID, 10));

    ProductReservation result = service.reserve(PRODUCT_ID, 3);

    assertThat(result).isEqualTo(new ProductReservation(PRODUCT_ID, 3, 7));
    then(stockRepository).should().save(new StockBalance(PRODUCT_ID, 7));
  }

  @Test
  void should_throw_stock_not_found_when_record_is_missing() {
    given(stockRepository.findForUpdateById(PRODUCT_ID))
        .willThrow(new StockRecordNotFoundException(PRODUCT_ID));

    assertThatThrownBy(() -> service.reserve(PRODUCT_ID, 3))
        .isInstanceOf(StockRecordNotFoundException.class);
  }

  @Test
  void should_throw_insufficient_stock_when_units_exceed_available() {
    given(stockRepository.findForUpdateById(PRODUCT_ID))
        .willReturn(new StockBalance(PRODUCT_ID, 2));

    assertThatThrownBy(() -> service.reserve(PRODUCT_ID, 3))
        .isInstanceOf(InsufficientStockException.class);
  }
}
