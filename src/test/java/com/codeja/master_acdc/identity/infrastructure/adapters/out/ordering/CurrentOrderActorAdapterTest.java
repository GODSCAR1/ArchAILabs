package com.codeja.master_acdc.identity.infrastructure.adapters.out.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.UserAccountJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentOrderActorAdapterTest {
  private final UserAccountJpaRepository accounts = mock(UserAccountJpaRepository.class);
  private final CurrentOrderActorAdapter adapter = new CurrentOrderActorAdapter(accounts);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("No hay actor sin autenticación autenticada")
  void should_return_empty_when_authentication_is_absent_or_unauthenticated() {
    SecurityContextHolder.clearContext();
    assertThat(adapter.current()).isEmpty();
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.unauthenticated("user", "credential"));
    assertThat(adapter.current()).isEmpty();
    verifyNoInteractions(accounts);
  }
}
