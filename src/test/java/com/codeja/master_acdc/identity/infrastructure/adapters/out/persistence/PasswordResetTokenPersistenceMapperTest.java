package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetTokenPersistenceMapperTest {
  @Test
  void should_preserve_token_when_mapping_both_directions() {
    var token = new PasswordResetToken(UUID.randomUUID(), "a".repeat(64), Instant.now());
    assertThat(
            PasswordResetTokenPersistenceMapper.toDomain(
                PasswordResetTokenPersistenceMapper.toEntity(token)))
        .isEqualTo(token);
  }
}
