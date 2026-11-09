package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.SessionVersionBarrier;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlockUserServiceTest {
  private final UserAccounts accounts = mock(UserAccounts.class);
  private final RefreshTokens tokens = mock(RefreshTokens.class);
  private final SessionVersionBarrier barrier = mock(SessionVersionBarrier.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
  private final BlockUserService service = new BlockUserService(accounts, tokens, barrier, clock);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void should_increment_disable_revoke_and_publish_in_order_when_account_exists(boolean enabled) {
    UUID id = UUID.randomUUID();
    var user =
        new UserAccount(
            id,
            "unit@example.com",
            "hash",
            new UserAccount.Profile(Set.of("CUSTOMER"), enabled, 7, null));
    when(accounts.findForUpdate(id)).thenReturn(Optional.of(user));
    service.block(id);
    var order = inOrder(accounts, tokens, barrier);
    order.verify(accounts).findForUpdate(id);
    order
        .verify(accounts)
        .save(
            new UserAccount(
                id,
                user.email(),
                "hash",
                new UserAccount.Profile(Set.of("CUSTOMER"), false, 8, null)));
    order.verify(tokens).revokeUser(id, clock.instant());
    order.verify(barrier).publish(id, new SessionVersion(8));
  }

  @Test
  void should_preserve_state_when_account_is_missing_or_version_overflows() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> service.block(id)).hasMessage("user-not-found");
    when(accounts.findForUpdate(id))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    id,
                    "unit@example.com",
                    "hash",
                    new UserAccount.Profile(Set.of("CUSTOMER"), true, Long.MAX_VALUE, null))));
    assertThatThrownBy(() -> service.block(id)).hasMessage("session-version-unavailable");
    org.mockito.Mockito.verify(accounts, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(tokens, barrier);
  }
}
