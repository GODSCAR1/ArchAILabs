package com.codeja.master_acdc.identity.infrastructure.adapters.out.notification;

import com.codeja.master_acdc.identity.domain.model.PasswordResetMessage;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;
import com.codeja.master_acdc.identity.domain.port.PasswordResetNotifier;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryPasswordResetNotifier implements PasswordResetNotifier {
  private final AtomicReference<PasswordResetMessage> last = new AtomicReference<>();

  @Override
  public void notify(PasswordResetMessage message) {
    last.set(message);
  }

  public Optional<PasswordResetMessage> lastMessageFor(String email) {
    String normalized = new PasswordResetRequest(email).email();
    return Optional.ofNullable(last.get()).filter(message -> message.email().equals(normalized));
  }
}
