package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.LogoutUseCase;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import java.time.Clock;

public final class LogoutService implements LogoutUseCase {
  private final RefreshTokenAccess access;
  private final RefreshTokens tokens;
  private final Clock clock;

  public LogoutService(RefreshTokenAccess access, RefreshTokens tokens, Clock clock) {
    this.access = access;
    this.tokens = tokens;
    this.clock = clock;
  }

  @Override
  public boolean logout(String secret) {
    var locked = access.lock(secret);
    if (locked.isEmpty()) {
      return false;
    }
    tokens.revokeFamily(locked.orElseThrow().token().family().id(), clock.instant());
    return true;
  }
}
