package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.RefreshUseCase;
import com.codeja.master_acdc.identity.domain.model.LoginResult;
import com.codeja.master_acdc.identity.domain.model.RefreshResult;
import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class RefreshService implements RefreshUseCase {
  private final RefreshTokenAccess access;
  private final RefreshTokens tokens;
  private final RefreshSecretGenerator secrets;
  private final AccessTokenIssuer issuer;
  private final Clock clock;

  public RefreshService(
      RefreshTokenAccess access,
      RefreshTokens tokens,
      RefreshSecretGenerator secrets,
      AccessTokenIssuer issuer,
      Clock clock) {
    this.access = access;
    this.tokens = tokens;
    this.secrets = secrets;
    this.issuer = issuer;
    this.clock = clock;
  }

  @Override
  public RefreshResult refresh(String secret) {
    return access.lock(secret).map(this::rotate).orElseGet(RefreshResult::rejected);
  }

  private RefreshResult rotate(RefreshTokenAccess.Locked locked) {
    RefreshToken token = locked.token();
    Instant now = clock.instant();
    if (token.state().rotatedAt() != null) {
      tokens.revokeFamily(token.family().id(), now);
      return RefreshResult.rejected();
    }
    if (!locked.account().profile().enabled()
        || token.state().revokedAt() != null
        || !now.isBefore(token.lifetime().expiresAt())
        || !now.isBefore(token.lifetime().familyExpiresAt())) {
      return RefreshResult.rejected();
    }
    var nextSecret = secrets.generate();
    Instant sliding = now.plus(7, ChronoUnit.DAYS);
    Instant absolute = token.lifetime().familyExpiresAt();
    Instant expiry = sliding.isBefore(absolute) ? sliding : absolute;
    tokens.save(
        new RefreshToken(
            token.id(),
            token.family(),
            token.tokenHash(),
            token.lifetime(),
            new RefreshToken.State(now, token.state().revokedAt())));
    tokens.save(
        new RefreshToken(
            UUID.randomUUID(),
            token.family(),
            nextSecret.hash(),
            new RefreshToken.Lifetime(now, expiry, absolute),
            new RefreshToken.State(null, null)));
    return RefreshResult.issued(
        new LoginResult(issuer.issue(locked.account()), nextSecret.value(), expiry));
  }
}
