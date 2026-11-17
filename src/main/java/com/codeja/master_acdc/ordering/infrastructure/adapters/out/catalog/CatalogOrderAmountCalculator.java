package com.codeja.master_acdc.ordering.infrastructure.adapters.out.catalog;

import com.codeja.master_acdc.catalog.api.CatalogPrices;
import com.codeja.master_acdc.catalog.api.CatalogPrices.ProductPrice;
import com.codeja.master_acdc.ordering.domain.exception.PaymentPriceUnavailableException;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.port.OrderAmountCalculator;
import com.codeja.master_acdc.pricing.domain.model.OrderPricing;
import com.codeja.master_acdc.pricing.domain.model.PricingLine;
import com.codeja.master_acdc.pricing.domain.model.PricingOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CatalogOrderAmountCalculator implements OrderAmountCalculator {
  private final CatalogPrices catalog;
  private final OrderPricing pricing;

  public CatalogOrderAmountCalculator(CatalogPrices catalog, OrderPricing pricing) {
    this.catalog = catalog;
    this.pricing = pricing;
  }

  @Override
  public BigDecimal calculate(List<OrderLine> lines) {
    List<UUID> ids = lines.stream().map(OrderLine::productId).distinct().toList();
    Map<UUID, ProductPrice> prices =
        catalog.findByIds(ids).stream().collect(Collectors.toMap(ProductPrice::id, p -> p));
    List<PricingLine> pricingLines =
        lines.stream().map(line -> toPricingLine(line, prices)).toList();
    return pricing.subtotal(new PricingOrder(pricingLines));
  }

  private PricingLine toPricingLine(OrderLine line, Map<UUID, ProductPrice> prices) {
    ProductPrice price = prices.get(line.productId());
    if (price == null) {
      throw new PaymentPriceUnavailableException();
    }
    return new PricingLine(price.sku(), line.quantity(), BigDecimal.valueOf(price.price()));
  }
}
