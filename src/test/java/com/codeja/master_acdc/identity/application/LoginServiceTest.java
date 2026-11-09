package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.AccessToken;
import com.codeja.master_acdc.identity.domain.model.Login;
import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.SecureRefreshSecretGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoginServiceTest {
  private final UserAccounts accounts = mock(UserAccounts.class);
  private final Passwords passwords = mock(Passwords.class);
  private final RefreshTokens refreshes = mock(RefreshTokens.class);
  private final AccessTokenIssuer issuer = mock(AccessTokenIssuer.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
  private final LoginService service =
      new LoginService(
          accounts,
          passwords,
          new LoginService.TokenDependencies(
              refreshes, new SecureRefreshSecretGenerator(), issuer, clock));
  private final UserAccount account =
      new UserAccount(
          UUID.randomUUID(),
          "a@example.com",
          "hash",
          new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, UUID.randomUUID()));

  @Test
  void should_create_family_when_enabled_account_authenticates() {
    when(accounts.findByEmail("a@example.com")).thenReturn(Optional.of(account));
    when(passwords.matches("secret", "hash")).thenReturn(true);
    when(accounts.findForUpdate(account.id())).thenReturn(Optional.of(account));
    when(issuer.issue(account)).thenReturn(new AccessToken("jwt", 900));
    var result = service.login(new Login("A@Example.com", "secret"));
    var captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshes).save(captor.capture());
    var token = captor.getValue();
    assertThat(token.family().userId()).isEqualTo(account.id());
    assertThat(token.lifetime().expiresAt()).isEqualTo(clock.instant().plusSeconds(604800));
    assertThat(token.lifetime().familyExpiresAt()).isEqualTo(clock.instant().plusSeconds(2592000));
    assertThat(token.tokenHash()).hasSize(64).isNotEqualTo(result.refreshToken());
    assertThat(result.refreshToken()).hasSize(43);
    assertThat(result.accessToken().expiresIn()).isEqualTo(900);
  }

  @Test
  void should_compare_dummy_when_email_is_unknown() {
    when(accounts.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.login(new Login("unknown@example.com", "wrong")))
        .hasMessage("invalid-credentials");
    verify(passwords).matchesDummy("wrong");
    verifyNoInteractions(refreshes, issuer);
  }

  @Test
  void should_reject_without_family_when_password_is_wrong() {
    when(accounts.findByEmail("a@example.com")).thenReturn(Optional.of(account));
    assertThatThrownBy(() -> service.login(new Login(account.email(), "wrong")))
        .hasMessage("invalid-credentials");
    verify(passwords).matches("wrong", "hash");
    verifyNoInteractions(refreshes, issuer);
  }

  @Test
  void should_reject_without_family_when_account_is_disabled_or_password_changed() {
    when(accounts.findByEmail("a@example.com")).thenReturn(Optional.of(account));
    when(passwords.matches("secret", "hash")).thenReturn(true);
    var disabled =
        new UserAccount(
            account.id(),
            account.email(),
            "hash",
            new UserAccount.Profile(Set.of("CUSTOMER"), false, 0, null));
    var changed = new UserAccount(account.id(), account.email(), "new-hash", account.profile());
    for (var current : new UserAccount[] {disabled, changed}) {
      when(accounts.findForUpdate(account.id())).thenReturn(Optional.of(current));
      assertThatThrownBy(() -> service.login(new Login(account.email(), "secret")))
          .hasMessage("invalid-credentials");
    }
    verifyNoInteractions(refreshes, issuer);
  }
}
