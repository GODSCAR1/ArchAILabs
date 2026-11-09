package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;

public final class RefreshTokenRepositoryAdapter implements RefreshTokens {
  @Override
  public java.util.Optional<java.util.UUID> findUserIdByHash(String hash) {
    return tokens.findUserIdByHash(hash);
  }

  @Override
  public java.util.Optional<RefreshToken> findForUpdateByHash(String hash) {
    return tokens.findForUpdateByHash(hash).map(RefreshTokenPersistenceMapper::toDomain);
  }

  @Override
  public void revokeFamily(java.util.UUID familyId, java.time.Instant now) {
    tokens.revokeFamily(familyId, now);
  }

  @Override
  public void revokeUser(java.util.UUID userId, java.time.Instant now) {
    tokens.revokeUser(userId, now);
  }

  private final RefreshTokenJpaRepository tokens;

  public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository tokens) {
    this.tokens = tokens;
  }

  @Override
  public void save(RefreshToken token) {
    tokens.saveAndFlush(RefreshTokenPersistenceMapper.toEntity(token));
  }
}
