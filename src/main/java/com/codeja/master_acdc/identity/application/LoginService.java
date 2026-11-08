package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.LoginUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.Login;
import com.codeja.master_acdc.identity.domain.model.LoginResult;
import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class LoginService implements LoginUseCase {
  private final UserAccounts accounts;
  private final Passwords passwords;
  private final TokenDependencies tokens;

  public record TokenDependencies(
      RefreshTokens repository,
      RefreshSecretGenerator secrets,
      AccessTokenIssuer issuer,
      Clock clock) {}

  public LoginService(UserAccounts accounts, Passwords passwords, TokenDependencies tokens) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.tokens = tokens;
  }

  @Override
  public LoginResult login(Login login) {
    var found = accounts.findByEmail(login.email());
    if (found.isEmpty()) {
      passwords.matchesDummy(login.password());
      throw new IdentityException("invalid-credentials");
    }
    var candidate = found.orElseThrow();
    boolean matches = passwords.matches(login.password(), candidate.passwordHash());
    if (!matches) {
      throw new IdentityException("invalid-credentials");
    }
    var current =
        accounts
            .findForUpdate(candidate.id())
            .orElseThrow(() -> new IdentityException("invalid-credentials"));
    if (!current.profile().enabled() || !current.passwordHash().equals(candidate.passwordHash())) {
      throw new IdentityException("invalid-credentials");
    }
    var secret = tokens.secrets().generate();
    var now = tokens.clock().instant();
    var lifetime =
        new RefreshToken.Lifetime(now, now.plus(7, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
    tokens
        .repository()
        .save(
            new RefreshToken(
                UUID.randomUUID(),
                new RefreshToken.Family(UUID.randomUUID(), current.id()),
                secret.hash(),
                lifetime,
                new RefreshToken.State(null, null)));
    return new LoginResult(tokens.issuer().issue(current), secret.value(), lifetime.expiresAt());
  }
}
