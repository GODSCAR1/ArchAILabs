package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.RefreshSecret;

public interface RefreshSecretGenerator {
  RefreshSecret generate();
}
