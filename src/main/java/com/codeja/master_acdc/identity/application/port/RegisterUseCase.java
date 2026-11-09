package com.codeja.master_acdc.identity.application.port;

import com.codeja.master_acdc.identity.domain.model.Registration;
import java.util.UUID;

public interface RegisterUseCase {
  UUID register(Registration registration);
}
