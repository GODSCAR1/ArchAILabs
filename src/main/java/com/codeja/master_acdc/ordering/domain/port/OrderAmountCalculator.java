package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import java.math.BigDecimal;
import java.util.List;

/** Calcula el importe de un pedido con los precios actuales. */
public interface OrderAmountCalculator {
  BigDecimal calculate(List<OrderLine> lines);
}
