package com.codeja.master_acdc.identity.application.port;

import com.codeja.master_acdc.identity.domain.model.RefreshResult;

public interface RefreshUseCase {
  RefreshResult refresh(String secret);
}
