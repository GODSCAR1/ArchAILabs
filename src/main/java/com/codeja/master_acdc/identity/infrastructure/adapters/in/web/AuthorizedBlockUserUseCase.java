package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.BlockUserUseCase;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedBlockUserUseCase implements BlockUserUseCase {
  private final BlockUserUseCase delegate;

  public AuthorizedBlockUserUseCase(BlockUserUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:manage')")
  public void block(UUID userId) {
    delegate.block(userId);
  }
}
