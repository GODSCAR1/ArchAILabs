package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshServiceTest extends SessionServiceTestSupport {
  private final RefreshService service = new RefreshService(access, tokens, secrets, issuer, clock);

  @Test
  @DisplayName("Refresh válido emite nuevo par")
  void should_rotate_with_account_before_token_when_refresh_is_valid() {
    var result = service.refresh(SECRET);
    assertThat(result.accepted()).isTrue();
    var order = inOrder(tokens, accounts);
    order.verify(tokens).findUserIdByHash(token.tokenHash());
    order.verify(accounts).findForUpdate(account.id());
    order.verify(tokens).findForUpdateByHash(token.tokenHash());
    var captured = ArgumentCaptor.forClass(RefreshToken.class);
    verify(tokens, times(2)).save(captured.capture());
    var previous = captured.getAllValues().getFirst();
    var next = captured.getAllValues().getLast();
    assertThat(previous.state().rotatedAt()).isEqualTo(now);
    assertThat(next.family()).isEqualTo(token.family());
    assertThat(next.lifetime().expiresAt()).isEqualTo(now.plusSeconds(604800));
    assertThat(next.lifetime().familyExpiresAt()).isEqualTo(token.lifetime().familyExpiresAt());
    assertThat(next.tokenHash()).isNotEqualTo(result.tokens().orElseThrow().refreshToken());
  }

  @Test
  @DisplayName("Rotación no prolonga la familia")
  void should_cap_expiry_when_family_has_one_day_left() {
    var capped =
        new RefreshToken(
            token.id(),
            token.family(),
            token.tokenHash(),
            new RefreshToken.Lifetime(
                now.minusSeconds(86400), now.plusSeconds(86400), now.plusSeconds(86400)),
            token.state());
    when(tokens.findForUpdateByHash(token.tokenHash())).thenReturn(Optional.of(capped));
    var result = service.refresh(SECRET).tokens().orElseThrow();
    assertThat(result.refreshExpiresAt()).isEqualTo(capped.lifetime().familyExpiresAt());
  }

  @Test
  @DisplayName("Refresh vencido o desconocido")
  void should_reject_without_writes_when_refresh_is_missing_expired_revoked_or_disabled() {
    assertThat(service.refresh(null).accepted()).isFalse();
    assertThat(service.refresh("invalid").accepted()).isFalse();
    when(tokens.findUserIdByHash(token.tokenHash())).thenReturn(Optional.empty());
    assertThat(service.refresh(SECRET).accepted()).isFalse();
    when(tokens.findUserIdByHash(token.tokenHash())).thenReturn(Optional.of(account.id()));
    for (var rejected :
        new RefreshToken[] {
          new RefreshToken(
              token.id(),
              token.family(),
              token.tokenHash(),
              new RefreshToken.Lifetime(now.minusSeconds(604800), now, now),
              token.state()),
          new RefreshToken(
              token.id(),
              token.family(),
              token.tokenHash(),
              token.lifetime(),
              new RefreshToken.State(null, now))
        }) {
      when(tokens.findForUpdateByHash(token.tokenHash())).thenReturn(Optional.of(rejected));
      assertThat(service.refresh(SECRET).accepted()).isFalse();
    }
    when(tokens.findForUpdateByHash(token.tokenHash())).thenReturn(Optional.of(token));
    when(accounts.findForUpdate(account.id()))
        .thenReturn(
            Optional.of(
                new com.codeja.master_acdc.identity.domain.model.UserAccount(
                    account.id(),
                    account.email(),
                    account.passwordHash(),
                    new com.codeja.master_acdc.identity.domain.model.UserAccount.Profile(
                        account.profile().roles(), false, 0, null))));
    assertThat(service.refresh(SECRET).accepted()).isFalse();
    verify(tokens, never()).save(any());
    verify(issuer, never()).issue(any());
  }

  @Test
  @DisplayName("Reutilización invalida todos los sucesores")
  void should_return_rejected_without_exception_when_token_was_rotated() {
    when(tokens.findForUpdateByHash(token.tokenHash()))
        .thenReturn(
            Optional.of(
                new RefreshToken(
                    token.id(),
                    token.family(),
                    token.tokenHash(),
                    token.lifetime(),
                    new RefreshToken.State(now.minusSeconds(1), null))));
    assertThat(service.refresh(SECRET).accepted()).isFalse();
    verify(tokens).revokeFamily(token.family().id(), now);
    verify(tokens, never()).save(any());
    verify(issuer, never()).issue(any());
  }
}
