package com.codeja.master_acdc.ordering.infrastructure.adapters.out.events;

import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher;
import org.springframework.context.ApplicationEventPublisher;

/** Publica el evento con el bus de eventos de Spring, en el mismo hilo y transacción. */
public class SpringOrderCancelledPublisher implements OrderCancelledPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public SpringOrderCancelledPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void publish(OrderCancelled event) {
    applicationEventPublisher.publishEvent(event);
  }
}
