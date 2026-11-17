package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerRegistrationAdapterTest {
  private final CustomerJpaRepository repository = mock(CustomerJpaRepository.class);
  private final CustomerRegistrationAdapter adapter = new CustomerRegistrationAdapter(repository);

  @Test
  @DisplayName("El registro conserva el original y escribe sus componentes")
  void should_save_original_and_components_when_registering_customer() {
    var id = adapter.register("  Ana   María  ");
    var captor = ArgumentCaptor.forClass(CustomerJpaEntity.class);
    verify(repository).save(captor.capture());
    var customer = captor.getValue();
    assertThat(customer.id()).isEqualTo(id);
    assertThat(customer.name()).isEqualTo("  Ana   María  ");
    assertThat(customer.firstName()).isEqualTo("Ana");
    assertThat(customer.lastName()).isEqualTo("María");
  }

  @Test
  @DisplayName("El registro rechaza nombres inválidos")
  void should_reject_name_when_blank_or_too_long() {
    assertThatThrownBy(() -> adapter.register("  ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.register("a".repeat(121)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
