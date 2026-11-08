package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogoutServiceTest extends SessionServiceTestSupport {
  private final LogoutService service = new LogoutService(access, tokens, clock);

  @Test
  @DisplayName("Logout impide renovar esa familia")
  void should_revoke_family_idempotently_when_token_is_rotated_or_already_revoked() {
    for (var state :
        new RefreshToken.State[] {
          new RefreshToken.State(now, null), new RefreshToken.State(now, now)
        }) {
      when(tokens.findForUpdateByHash(token.tokenHash()))
          .thenReturn(
              Optional.of(
                  new RefreshToken(
                      token.id(), token.family(), token.tokenHash(), token.lifetime(), state)));
      assertThat(service.logout(SECRET)).isTrue();
    }
    verify(tokens, times(2)).revokeFamily(token.family().id(), now);
  }

  @Test
  @DisplayName("Logout sin refresh conocido")
  void should_reject_when_refresh_is_absent_or_unknown() {
    assertThat(service.logout(null)).isFalse();
    when(tokens.findUserIdByHash(token.tokenHash())).thenReturn(Optional.empty());
    assertThat(service.logout(SECRET)).isFalse();
  }
}
