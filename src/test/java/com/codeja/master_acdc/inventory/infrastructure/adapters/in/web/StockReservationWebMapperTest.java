package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.inventory.domain.model.ProductReservation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockReservationWebMapperTest {

  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void should_map_product_reservation_to_response_when_reservation_is_successful() {
    StockReservationResponse response =
        StockReservationWebMapper.toResponse(new ProductReservation(PRODUCT_ID, 3, 7));

    assertThat(response).isEqualTo(new StockReservationResponse(PRODUCT_ID, 3, 7));
  }
}
