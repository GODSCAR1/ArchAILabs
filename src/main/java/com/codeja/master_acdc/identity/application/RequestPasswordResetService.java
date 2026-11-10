package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.RequestPasswordResetUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordResetMessage;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;
import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;
import com.codeja.master_acdc.identity.domain.port.PasswordResetSecrets;
import com.codeja.master_acdc.identity.domain.port.PasswordResetTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

public final class RequestPasswordResetService implements RequestPasswordResetUseCase {
  private final UserAccounts accounts;
  private final PasswordResetTokens tokens;
  private final PasswordResetSecrets secrets;
  private final Clock clock;

  public RequestPasswordResetService(
      UserAccounts accounts,
      PasswordResetTokens tokens,
      PasswordResetSecrets secrets,
      Clock clock) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.secrets = secrets;
    this.clock = clock;
  }

  @Override
  public Optional<PasswordResetMessage> request(PasswordResetRequest request) {
    var account =
        accounts.findByEmail(request.email()).flatMap(a -> accounts.findForUpdate(a.id()));
    if (account.isEmpty() || !account.get().profile().enabled()) {
      return Optional.empty();
    }
    String token = secrets.generate();
    tokens.save(
        new PasswordResetToken(
            account.get().id(), secrets.hash(token), clock.instant().plus(Duration.ofMinutes(15))));
    return Optional.of(new PasswordResetMessage(account.get().email(), token));
  }
}
