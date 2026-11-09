package com.codeja.master_acdc.identity.application.port;

import java.util.UUID;

public interface UnblockUserUseCase {
  void unblock(UUID userId);
}
