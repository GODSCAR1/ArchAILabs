package com.codeja.master_acdc.inventory.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import com.codeja.master_acdc.inventory.domain.port.StockRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ReleaseStockServiceTest {

  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final StockRepository stockRepository = mock(StockRepository.class);
  private final ReleaseStockService service = new ReleaseStockService(stockRepository);

  @Test
  void should_increase_available_stock_when_release_lines() {
    service.release(List.of(new StockLine(A, 3), new StockLine(B, 2)));

    then(stockRepository).should().release(A, 3);
    then(stockRepository).should().release(B, 2);
  }

  @Test
  void should_ignore_ordering_of_lines_when_release() {
    service.release(List.of(new StockLine(B, 2), new StockLine(A, 3)));

    InOrder inOrder = inOrder(stockRepository);
    inOrder.verify(stockRepository).release(A, 3);
    inOrder.verify(stockRepository).release(B, 2);
  }

  @Test
  void should_propagate_when_release_finds_no_stock_record() {
    willThrow(new StockRecordNotFoundException(A)).given(stockRepository).release(A, 3);

    assertThatThrownBy(() -> service.release(List.of(new StockLine(A, 3))))
        .isInstanceOf(StockRecordNotFoundException.class);
  }
}
