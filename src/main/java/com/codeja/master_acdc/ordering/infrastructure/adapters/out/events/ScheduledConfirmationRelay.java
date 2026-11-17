package com.codeja.master_acdc.ordering.infrastructure.adapters.out.events;

import org.springframework.scheduling.annotation.Scheduled;

public final class ScheduledConfirmationRelay {
  private final Runnable process;

  public ScheduledConfirmationRelay(Runnable process) {
    this.process = process;
  }

  @Scheduled(fixedDelayString = "${shop.order-confirmation.relay-interval:PT1S}")
  public void relay() {
    process.run();
  }
}
