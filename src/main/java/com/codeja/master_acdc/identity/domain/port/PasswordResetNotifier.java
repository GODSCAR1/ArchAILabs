package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.PasswordResetMessage;

public interface PasswordResetNotifier {
  void notify(PasswordResetMessage message);
}
