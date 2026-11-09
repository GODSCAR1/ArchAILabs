package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import java.util.UUID;

public interface SessionVersionBarrier {
  void publish(UUID accountId, SessionVersion version);
}
