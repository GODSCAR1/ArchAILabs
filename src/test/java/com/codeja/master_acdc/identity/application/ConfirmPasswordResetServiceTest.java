package com.codeja.master_acdc.identity.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.model.PasswordResetConfirmation;
import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.PasswordResetSecrets;
import com.codeja.master_acdc.identity.domain.port.PasswordResetTokens;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.SessionVersionBarrier;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmPasswordResetServiceTest {
  @Test
  @DisplayName("Recuperación permite iniciar sesión con la nueva contraseña")
  void should_encode_new_password_when_confirmation_succeeds() {
    var accounts = mock(UserAccounts.class);
    var tokens = mock(PasswordResetTokens.class);
    var secrets = mock(PasswordResetSecrets.class);
    var passwords = mock(Passwords.class);
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var account = account();
    when(accounts.findByEmail(account.email())).thenReturn(Optional.of(account));
    when(accounts.findForUpdate(account.id())).thenReturn(Optional.of(account));
    when(tokens.findByUserId(account.id()))
        .thenReturn(
            Optional.of(new PasswordResetToken(account.id(), "digest", now.plusSeconds(900))));
    when(secrets.hash("secret")).thenReturn("digest");
    when(passwords.encode("Unique!67890Z")).thenReturn("new-hash");
    var service =
        new ConfirmPasswordResetService(
            new ChangePasswordService.Revocation(
                accounts, mock(RefreshTokens.class), mock(SessionVersionBarrier.class)),
            new ConfirmPasswordResetService.Recovery(tokens, secrets),
            passwords,
            new PasswordPolicy(value -> false),
            Clock.fixed(now, ZoneOffset.UTC));
    service.confirm(new PasswordResetConfirmation(account.email(), "secret", "Unique!67890Z"));
    verify(accounts).save(updated(account));
  }

  @Test
  @DisplayName("Recuperación actualiza credenciales y estado de sesión conjuntamente")
  void should_update_password_and_revoke_sessions_when_token_is_valid() {
    var accounts = mock(UserAccounts.class);
    var refresh = mock(RefreshTokens.class);
    var barrier = mock(SessionVersionBarrier.class);
    var tokens = mock(PasswordResetTokens.class);
    var secrets = mock(PasswordResetSecrets.class);
    var passwords = mock(Passwords.class);
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var account = account();
    when(accounts.findByEmail(account.email())).thenReturn(Optional.of(account));
    when(accounts.findForUpdate(account.id())).thenReturn(Optional.of(account));
    when(tokens.findByUserId(account.id()))
        .thenReturn(
            Optional.of(new PasswordResetToken(account.id(), "digest", now.plusSeconds(900))));
    when(secrets.hash("secret")).thenReturn("digest");
    when(passwords.encode("Unique!67890Z")).thenReturn("new-hash");
    var service =
        new ConfirmPasswordResetService(
            new ChangePasswordService.Revocation(accounts, refresh, barrier),
            new ConfirmPasswordResetService.Recovery(tokens, secrets),
            passwords,
            new PasswordPolicy(value -> false),
            Clock.fixed(now, ZoneOffset.UTC));
    service.confirm(new PasswordResetConfirmation(account.email(), "secret", "Unique!67890Z"));
    var order = inOrder(accounts, refresh, tokens, barrier);
    order.verify(accounts).findByEmail(account.email());
    order.verify(accounts).findForUpdate(account.id());
    order.verify(tokens).findByUserId(account.id());
    order.verify(accounts).save(updated(account));
    order.verify(refresh).revokeUser(account.id(), now);
    order.verify(tokens).deleteByUserId(account.id());
    order.verify(barrier).publish(account.id(), new SessionVersion(1));
  }

  private UserAccount account() {
    return new UserAccount(
        UUID.randomUUID(),
        "user@example.com",
        "hash",
        new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, null));
  }

  private UserAccount updated(UserAccount account) {
    return new UserAccount(
        account.id(),
        account.email(),
        "new-hash",
        new UserAccount.Profile(Set.of("CUSTOMER"), true, 1, null));
  }
}
