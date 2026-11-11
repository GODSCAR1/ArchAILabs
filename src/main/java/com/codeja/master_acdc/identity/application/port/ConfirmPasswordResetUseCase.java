package com.codeja.master_acdc.identity.application.port;

import com.codeja.master_acdc.identity.domain.model.PasswordResetConfirmation;

public interface ConfirmPasswordResetUseCase {
  void confirm(PasswordResetConfirmation confirmation);
}
