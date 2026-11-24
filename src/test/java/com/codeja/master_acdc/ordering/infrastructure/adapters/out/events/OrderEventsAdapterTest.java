package com.codeja.master_acdc.ordering.infrastructure.adapters.out.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import com.codeja.master_acdc.ordering.domain.event.OrderConfirmed;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

class OrderEventsAdapterTest {
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final OrderEventsAdapter adapter = new OrderEventsAdapter(publisher, jdbc);

  @Test
  @DisplayName("La cancelación se envía al publicador de Spring")
  void should_publish_spring_event_when_order_is_cancelled() {
    var event = new OrderCancelled(UUID.randomUUID(), List.of());
    adapter.publish(List.of(event));
    verify(publisher).publishEvent(event);
    verifyNoInteractions(jdbc);
  }

  @Test
  @DisplayName("La confirmación se escribe en el outbox")
  void should_write_outbox_when_order_is_confirmed() {
    var event = new OrderConfirmed(UUID.randomUUID(), List.of(UUID.randomUUID()));
    adapter.publish(List.of(event));
    verify(jdbc).update(contains("order_confirmation_outbox"), any(PreparedStatementSetter.class));
    verifyNoInteractions(publisher);
  }
}
