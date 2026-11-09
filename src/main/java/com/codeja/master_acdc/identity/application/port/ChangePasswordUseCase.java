package com.codeja.master_acdc.identity.application.port;

import com.codeja.master_acdc.identity.domain.model.PasswordChange;

public interface ChangePasswordUseCase {
  void changePassword(PasswordChange command);
}
