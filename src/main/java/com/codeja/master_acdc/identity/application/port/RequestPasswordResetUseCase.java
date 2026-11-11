package com.codeja.master_acdc.identity.application.port;

import com.codeja.master_acdc.identity.domain.model.PasswordResetMessage;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;
import java.util.Optional;

public interface RequestPasswordResetUseCase {
  Optional<PasswordResetMessage> request(PasswordResetRequest request);
}
