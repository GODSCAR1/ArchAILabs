package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenPersistenceMapperTest {
  @Test
  void should_preserve_refresh_state_when_mapping_both_directions() {
    Instant now = Instant.parse("2026-11-08T12:00:00Z");
    var token =
        new RefreshToken(
            UUID.randomUUID(),
            new RefreshToken.Family(UUID.randomUUID(), UUID.randomUUID()),
            "a".repeat(64),
            new RefreshToken.Lifetime(now, now.plusSeconds(604800), now.plusSeconds(2592000)),
            new RefreshToken.State(now.plusSeconds(10), now.plusSeconds(20)));
    assertThat(
            RefreshTokenPersistenceMapper.toDomain(RefreshTokenPersistenceMapper.toEntity(token)))
        .isEqualTo(token);
    var active =
        new RefreshToken(
            token.id(),
            token.family(),
            token.tokenHash(),
            token.lifetime(),
            new RefreshToken.State(null, null));
    assertThat(
            RefreshTokenPersistenceMapper.toDomain(RefreshTokenPersistenceMapper.toEntity(active)))
        .isEqualTo(active);
  }
}
