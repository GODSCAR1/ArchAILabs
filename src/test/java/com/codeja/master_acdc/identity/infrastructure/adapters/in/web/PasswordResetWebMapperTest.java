package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.PasswordResetConfirmation;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;
import org.junit.jupiter.api.Test;

class PasswordResetWebMapperTest {
  @Test
  void should_map_requests_when_recovery_bodies_are_valid() {
    assertThat(PasswordResetWebMapper.request(new PasswordResetRequestBody("USER@example.com")))
        .isEqualTo(new PasswordResetRequest("user@example.com"));
    assertThat(
            PasswordResetWebMapper.confirmation(
                new PasswordResetConfirmBody("USER@example.com", "secret", "Unique!12345X")))
        .isEqualTo(new PasswordResetConfirmation("user@example.com", "secret", "Unique!12345X"));
  }
}
