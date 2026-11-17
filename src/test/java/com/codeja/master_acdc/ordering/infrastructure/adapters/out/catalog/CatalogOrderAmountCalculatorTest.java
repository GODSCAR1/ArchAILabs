package com.codeja.master_acdc.ordering.infrastructure.adapters.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.catalog.api.CatalogPrices.ProductPrice;
import com.codeja.master_acdc.ordering.domain.exception.PaymentPriceUnavailableException;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.pricing.domain.model.OrderPricing;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogOrderAmountCalculatorTest {
  @Test
  @DisplayName("El importe usa los precios actuales y el descuento por volumen")
  void should_calculate_discounted_subtotal_when_prices_exist() {
    UUID id = UUID.randomUUID();
    var calculator =
        new CatalogOrderAmountCalculator(
            ids -> List.of(new ProductPrice(id, "SKU", 100)),
            new OrderPricing(Clock.systemUTC(), BigDecimal.ZERO));

    assertThat(calculator.calculate(List.of(new OrderLine(id, 10)))).isEqualByComparingTo("900.00");
  }

  @Test
  @DisplayName("Un producto sin precio impide calcular el importe")
  void should_reject_calculation_when_price_is_missing() {
    var calculator =
        new CatalogOrderAmountCalculator(
            ids -> List.of(), new OrderPricing(Clock.systemUTC(), BigDecimal.ZERO));

    assertThatThrownBy(() -> calculator.calculate(List.of(new OrderLine(UUID.randomUUID(), 1))))
        .isInstanceOf(PaymentPriceUnavailableException.class);
  }
}
