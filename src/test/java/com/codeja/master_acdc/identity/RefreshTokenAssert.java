package com.codeja.master_acdc.identity;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import java.util.UUID;
import org.assertj.core.api.AbstractAssert;

public final class RefreshTokenAssert extends AbstractAssert<RefreshTokenAssert, RefreshToken> {
  private RefreshTokenAssert(RefreshToken actual) {
    super(actual, RefreshTokenAssert.class);
  }

  public static RefreshTokenAssert assertThat(RefreshToken actual) {
    return new RefreshTokenAssert(actual);
  }

  public RefreshTokenAssert isRevoked() {
    isNotNull();
    if (actual.state().revokedAt() == null) {
      failWithMessage("Expected refresh token <%s> to be revoked", actual.id());
    }
    return this;
  }

  public RefreshTokenAssert isUsed() {
    isNotNull();
    if (actual.state().rotatedAt() == null) {
      failWithMessage("Expected refresh token <%s> to be used", actual.id());
    }
    return this;
  }

  public RefreshTokenAssert belongsToFamily(UUID family) {
    isNotNull();
    if (!actual.family().id().equals(family)) {
      failWithMessage("Expected refresh token <%s> to belong to family <%s>", actual.id(), family);
    }
    return this;
  }
}
