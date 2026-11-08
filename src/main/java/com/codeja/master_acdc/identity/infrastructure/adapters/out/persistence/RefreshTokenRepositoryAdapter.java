package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;

public final class RefreshTokenRepositoryAdapter implements RefreshTokens {
  private final RefreshTokenJpaRepository tokens;

  public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository tokens) {
    this.tokens = tokens;
  }

  @Override
  public void save(RefreshToken token) {
    tokens.saveAndFlush(RefreshTokenPersistenceMapper.toEntity(token));
  }
}
