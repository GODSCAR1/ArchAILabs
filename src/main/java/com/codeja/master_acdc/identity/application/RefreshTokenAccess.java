package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.util.Optional;

/** Único orden de adquisición: lectura escalar, cuenta bloqueada y token bloqueado. */
public final class RefreshTokenAccess {
  private final UserAccounts accounts;
  private final RefreshTokens tokens;
  private final RefreshSecretGenerator secrets;

  public record Locked(UserAccount account, RefreshToken token) {}

  public RefreshTokenAccess(
      UserAccounts accounts, RefreshTokens tokens, RefreshSecretGenerator secrets) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.secrets = secrets;
  }

  public Optional<Locked> lock(String secret) {
    if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) {
      return Optional.empty();
    }
    String hash = secrets.hash(secret);
    return tokens
        .findUserIdByHash(hash)
        .flatMap(accounts::findForUpdate)
        .flatMap(
            account ->
                tokens
                    .findForUpdateByHash(hash)
                    .filter(token -> token.family().userId().equals(account.id()))
                    .map(token -> new Locked(account, token)));
  }
}
