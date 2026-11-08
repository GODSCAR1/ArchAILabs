package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.PasswordChange;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChangePasswordServiceTest extends SessionServiceTestSupport {
  private final Passwords passwords = mock(Passwords.class);
  private final ChangePasswordService service =
      new ChangePasswordService(
          accounts,
          passwords,
          new PasswordPolicy(value -> value.equals("password1234")),
          tokens,
          clock);

  @Test
  @DisplayName("Cambio de contraseña cierra renovación de otro dispositivo")
  void should_lock_verify_update_and_revoke_when_current_password_is_correct() {
    when(passwords.matches("current", "hash")).thenReturn(true);
    when(passwords.encode("Unique!67890Z")).thenReturn("new-hash");
    service.changePassword(new PasswordChange(account.id(), "current", "Unique!67890Z"));
    var order = inOrder(accounts, passwords, tokens);
    order.verify(accounts).findForUpdate(account.id());
    order.verify(passwords).matches("current", "hash");
    order.verify(passwords).encode("Unique!67890Z");
    order
        .verify(accounts)
        .save(new UserAccount(account.id(), account.email(), "new-hash", account.profile()));
    order.verify(tokens).revokeUser(account.id(), now);
  }

  @Test
  @DisplayName("Contraseña actual incorrecta")
  void should_preserve_account_when_current_password_is_incorrect() {
    assertThatThrownBy(
            () ->
                service.changePassword(new PasswordChange(account.id(), "wrong", "Unique!67890Z")))
        .hasMessage("invalid-credentials");
    verify(accounts, never()).save(any());
    verify(tokens, never()).revokeUser(any(), any());
  }

  @Test
  void should_preserve_account_when_new_password_violates_policy() {
    when(passwords.matches("current", "hash")).thenReturn(true);
    for (String value : new String[] {"short", "password1234", "é".repeat(37)}) {
      assertThatThrownBy(
              () -> service.changePassword(new PasswordChange(account.id(), "current", value)))
          .hasMessage("invalid-password");
    }
    verify(accounts, never()).save(any());
    verify(tokens, never()).revokeUser(any(), any());
  }
}
