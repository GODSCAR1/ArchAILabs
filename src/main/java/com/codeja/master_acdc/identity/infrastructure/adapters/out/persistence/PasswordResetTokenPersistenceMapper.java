package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;

public final class PasswordResetTokenPersistenceMapper {
  private PasswordResetTokenPersistenceMapper() {}

  public static PasswordResetTokenJpaEntity toEntity(PasswordResetToken token) {
    var entity = new PasswordResetTokenJpaEntity();
    entity.userId = token.userId();
    entity.tokenHash = token.hash();
    entity.expiresAt = token.expiresAt();
    return entity;
  }

  public static PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
    return new PasswordResetToken(entity.userId, entity.tokenHash, entity.expiresAt);
  }
}
