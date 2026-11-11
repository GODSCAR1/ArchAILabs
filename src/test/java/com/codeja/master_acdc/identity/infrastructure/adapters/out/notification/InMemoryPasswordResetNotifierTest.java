package com.codeja.master_acdc.identity.infrastructure.adapters.out.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.PasswordResetMessage;
import org.junit.jupiter.api.Test;

class InMemoryPasswordResetNotifierTest {
  @Test
  void should_return_latest_message_when_recipient_matches() {
    var notifier = new InMemoryPasswordResetNotifier();
    var message = new PasswordResetMessage("user@example.com", "secret");
    notifier.notify(message);
    assertThat(notifier.lastMessageFor("USER@example.com")).contains(message);
    assertThat(notifier.lastMessageFor("other@example.com")).isEmpty();
  }
}
