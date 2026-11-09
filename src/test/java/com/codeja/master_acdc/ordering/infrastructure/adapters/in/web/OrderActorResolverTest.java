package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import static com.codeja.master_acdc.AuthorizedCalls.withAuthorities;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.ordering.api.CurrentOrderActor;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OrderActorResolverTest {
  @Test
  void should_reject_missing_account_when_resolving_any_scope() {
    var resolver = new OrderActorResolver(Optional::empty);
    assertThatThrownBy(resolver::creationCustomer).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> resolver.cancellationCustomer(UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(resolver::readingScope).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void should_keep_missing_customer_closed_when_account_is_unlinked() {
    var resolver = resolver(null);
    assertThatThrownBy(resolver::creationCustomer).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> resolver.cancellationCustomer(UUID.randomUUID()))
        .isInstanceOf(OrderNotFoundException.class);
    assertThat(withAuthorities(resolver::readingScope, "order:read-own"))
        .isEqualTo(OrderReadScope.None.INSTANCE);
    assertThat(withAuthorities(resolver::readingScope, "order:read-any"))
        .isEqualTo(OrderReadScope.Global.INSTANCE);
  }

  @Test
  void should_expand_only_reading_when_actor_has_global_read_permission() {
    UUID customer = UUID.randomUUID();
    var resolver = resolver(customer);
    assertThat(resolver.creationCustomer()).isEqualTo(customer);
    assertThat(resolver.cancellationCustomer(UUID.randomUUID())).isEqualTo(customer);
    assertThat(withAuthorities(resolver::readingScope, "order:read-own"))
        .isEqualTo(new OrderReadScope.Own(customer));
    assertThat(withAuthorities(resolver::readingScope, "order:read-any", "order:cancel-own"))
        .isEqualTo(OrderReadScope.Global.INSTANCE);
  }

  private OrderActorResolver resolver(UUID customer) {
    return new OrderActorResolver(
        () -> Optional.of(new CurrentOrderActor.Actor(UUID.randomUUID(), customer)));
  }
}
