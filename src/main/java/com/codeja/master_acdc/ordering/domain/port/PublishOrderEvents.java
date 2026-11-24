package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.event.OrderEvent;
import java.util.List;

public interface PublishOrderEvents {
  void publish(List<OrderEvent> events);
}
