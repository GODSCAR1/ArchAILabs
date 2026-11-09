package com.codeja.master_acdc.identity.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.AccessToken;
import com.codeja.master_acdc.identity.domain.model.RefreshSecret;
import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class SessionServiceTestSupport {
  static final String SECRET = "a".repeat(43);
  final Instant now = Instant.parse("2026-11-08T12:00:00Z");
  final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  final UserAccounts accounts = mock(UserAccounts.class);
  final RefreshTokens tokens = mock(RefreshTokens.class);
  final RefreshSecretGenerator secrets = mock(RefreshSecretGenerator.class);
  final AccessTokenIssuer issuer = mock(AccessTokenIssuer.class);
  final UserAccount account =
      new UserAccount(
          UUID.randomUUID(),
          "a@example.com",
          "hash",
          new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, UUID.randomUUID()));
  final RefreshToken token =
      new RefreshToken(
          UUID.randomUUID(),
          new RefreshToken.Family(UUID.randomUUID(), account.id()),
          "a".repeat(64),
          new RefreshToken.Lifetime(
              now.minusSeconds(86400), now.plusSeconds(604800), now.plusSeconds(2592000)),
          new RefreshToken.State(null, null));
  final RefreshTokenAccess access = new RefreshTokenAccess(accounts, tokens, secrets);

  SessionServiceTestSupport() {
    when(secrets.hash(SECRET)).thenReturn(token.tokenHash());
    when(tokens.findUserIdByHash(token.tokenHash())).thenReturn(Optional.of(account.id()));
    when(accounts.findForUpdate(account.id())).thenReturn(Optional.of(account));
    when(tokens.findForUpdateByHash(token.tokenHash())).thenReturn(Optional.of(token));
    when(secrets.generate()).thenReturn(new RefreshSecret("b".repeat(43), "b".repeat(64)));
    when(issuer.issue(account)).thenReturn(new AccessToken("jwt", 900));
  }
}
