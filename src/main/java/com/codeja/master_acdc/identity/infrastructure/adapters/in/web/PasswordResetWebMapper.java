package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.PasswordResetConfirmation;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;

public final class PasswordResetWebMapper {
  private PasswordResetWebMapper() {}

  public static PasswordResetRequest request(PasswordResetRequestBody body) {
    if (body == null) {
      throw new IdentityException("invalid-password-reset-request");
    }
    return new PasswordResetRequest(body.email());
  }

  public static PasswordResetConfirmation confirmation(PasswordResetConfirmBody body) {
    if (body == null) {
      throw new IdentityException("invalid-password-reset-request");
    }
    return new PasswordResetConfirmation(body.email(), body.token(), body.newPassword());
  }
}
