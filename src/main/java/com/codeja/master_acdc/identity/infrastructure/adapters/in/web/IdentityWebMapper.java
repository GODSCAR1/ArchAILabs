package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.domain.model.Registration;
import java.util.Map;
import java.util.UUID;

public final class IdentityWebMapper {
  private IdentityWebMapper() {}

  public static com.codeja.master_acdc.identity.domain.model.Login toLogin(LoginRequest request) {
    return new com.codeja.master_acdc.identity.domain.model.Login(
        request.email(), request.password());
  }

  public static Map<String, Object> loginResponse(
      com.codeja.master_acdc.identity.domain.model.LoginResult result) {
    return Map.of(
        "accessToken", result.accessToken().value(), "expiresIn", result.accessToken().expiresIn());
  }

  public static Registration toRegistration(RegisterRequest request) {
    return new Registration(request.email(), request.password(), request.name());
  }

  public static Map<String, UUID> registrationResponse(UUID id) {
    return Map.of("id", id);
  }
}
