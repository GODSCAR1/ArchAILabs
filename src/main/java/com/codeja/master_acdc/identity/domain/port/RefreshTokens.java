package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;

public interface RefreshTokens {
  void save(RefreshToken token);

  java.util.Optional<java.util.UUID> findUserIdByHash(String hash);

  java.util.Optional<RefreshToken> findForUpdateByHash(String hash);

  void revokeFamily(java.util.UUID familyId, java.time.Instant now);

  void revokeUser(java.util.UUID userId, java.time.Instant now);
}
