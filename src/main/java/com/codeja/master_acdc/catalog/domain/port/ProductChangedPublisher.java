package com.codeja.master_acdc.catalog.domain.port;

import com.codeja.master_acdc.catalog.domain.event.ProductChanged;

/** Output port for publishing product changes. */
public interface ProductChangedPublisher {

  void publish(ProductChanged event);
}
