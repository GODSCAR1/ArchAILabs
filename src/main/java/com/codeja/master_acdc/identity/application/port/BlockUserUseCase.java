package com.codeja.master_acdc.identity.application.port;

import java.util.UUID;

public interface BlockUserUseCase {
  void block(UUID userId);
}
