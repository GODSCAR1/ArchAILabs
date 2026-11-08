package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;

public final class RefreshTokenPersistenceMapper {
  private RefreshTokenPersistenceMapper() {}

  public static RefreshTokenJpaEntity toEntity(RefreshToken token) {
    var entity = new RefreshTokenJpaEntity();
    entity.id = token.id();
    entity.familyId = token.family().id();
    entity.userId = token.family().userId();
    entity.tokenHash = token.tokenHash();
    entity.createdAt = token.lifetime().createdAt();
    entity.expiresAt = token.lifetime().expiresAt();
    entity.familyExpiresAt = token.lifetime().familyExpiresAt();
    entity.rotatedAt = token.state().rotatedAt();
    entity.revokedAt = token.state().revokedAt();
    return entity;
  }

  public static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
    return new RefreshToken(
        entity.id,
        new RefreshToken.Family(entity.familyId, entity.userId),
        entity.tokenHash,
        new RefreshToken.Lifetime(entity.createdAt, entity.expiresAt, entity.familyExpiresAt),
        new RefreshToken.State(entity.rotatedAt, entity.revokedAt));
  }
}
