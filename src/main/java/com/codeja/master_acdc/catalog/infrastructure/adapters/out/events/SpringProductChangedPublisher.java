package com.codeja.master_acdc.catalog.infrastructure.adapters.out.events;

import com.codeja.master_acdc.catalog.domain.event.ProductChanged;
import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import org.springframework.context.ApplicationEventPublisher;

public final class SpringProductChangedPublisher implements ProductChangedPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public SpringProductChangedPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void publish(ProductChanged event) {
    applicationEventPublisher.publishEvent(event);
  }
}
