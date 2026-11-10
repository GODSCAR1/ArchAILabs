package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;
import com.codeja.master_acdc.identity.domain.port.PasswordResetTokens;
import java.util.Optional;
import java.util.UUID;

public final class PasswordResetTokenRepositoryAdapter implements PasswordResetTokens {
  private final PasswordResetTokenJpaRepository repository;

  public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public void save(PasswordResetToken token) {
    repository.saveAndFlush(PasswordResetTokenPersistenceMapper.toEntity(token));
  }

  @Override
  public Optional<PasswordResetToken> findByUserId(UUID userId) {
    return repository.findById(userId).map(PasswordResetTokenPersistenceMapper::toDomain);
  }

  @Override
  public void deleteByUserId(UUID userId) {
    repository.deleteById(userId);
  }
}
