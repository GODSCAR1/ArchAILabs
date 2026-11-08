package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.RefreshToken;

public interface RefreshTokens {
  void save(RefreshToken token);
}
