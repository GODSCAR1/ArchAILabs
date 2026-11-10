package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokens {
  void save(PasswordResetToken token);

  Optional<PasswordResetToken> findByUserId(UUID userId);

  void deleteByUserId(UUID userId);
}
