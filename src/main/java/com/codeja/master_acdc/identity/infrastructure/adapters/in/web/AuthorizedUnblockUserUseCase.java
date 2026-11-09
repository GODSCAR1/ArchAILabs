package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.UnblockUserUseCase;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedUnblockUserUseCase implements UnblockUserUseCase {
  private final UnblockUserUseCase delegate;

  public AuthorizedUnblockUserUseCase(UnblockUserUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:manage')")
  public void unblock(UUID userId) {
    delegate.unblock(userId);
  }
}
