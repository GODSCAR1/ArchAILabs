package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.FixedClockExtension;
import com.codeja.master_acdc.identity.domain.model.PasswordResetMessage;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;
import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.PasswordResetSecrets;
import com.codeja.master_acdc.identity.domain.port.PasswordResetTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixedClockExtension.class)
class RequestPasswordResetServiceTest {
  @Test
  @DisplayName("Recuperación indistinguible para cuenta ausente o deshabilitada")
  void should_return_empty_without_writes_when_account_is_missing_or_disabled(Clock clock) {
    var accounts = mock(UserAccounts.class);
    var tokens = mock(PasswordResetTokens.class);
    var secrets = mock(PasswordResetSecrets.class);
    var account =
        new UserAccount(
            UUID.randomUUID(),
            "user@example.com",
            "hash",
            new UserAccount.Profile(Set.of("CUSTOMER"), false, 0, null));
    var service = new RequestPasswordResetService(accounts, tokens, secrets, clock);
    var request = new PasswordResetRequest(account.email());
    assertThat(service.request(request)).isEmpty();
    when(accounts.findByEmail(account.email())).thenReturn(Optional.of(account));
    when(accounts.findForUpdate(account.id())).thenReturn(Optional.of(account));
    assertThat(service.request(request)).isEmpty();
    verifyNoInteractions(tokens, secrets);
  }

  @Test
  @DisplayName("Solicitud de recuperación notifica al destinatario")
  void should_prepare_notification_when_enabled_account_requests_recovery() {
    var accounts = mock(UserAccounts.class);
    var tokens = mock(PasswordResetTokens.class);
    var secrets = mock(PasswordResetSecrets.class);
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var account =
        new UserAccount(
            UUID.randomUUID(),
            "user@example.com",
            "hash",
            new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, null));
    when(accounts.findByEmail(account.email())).thenReturn(Optional.of(account));
    when(accounts.findForUpdate(account.id())).thenReturn(Optional.of(account));
    when(secrets.generate()).thenReturn("secret");
    when(secrets.hash("secret")).thenReturn("digest");
    var service =
        new RequestPasswordResetService(
            accounts, tokens, secrets, Clock.fixed(now, ZoneOffset.UTC));
    assertThat(service.request(new PasswordResetRequest("USER@example.com")))
        .contains(new PasswordResetMessage(account.email(), "secret"));
    verify(tokens).save(new PasswordResetToken(account.id(), "digest", now.plusSeconds(900)));
  }
}
