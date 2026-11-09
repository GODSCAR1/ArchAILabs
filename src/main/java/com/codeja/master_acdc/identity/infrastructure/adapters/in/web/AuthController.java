package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final RegisterUseCase register;

  public AuthController(RegisterUseCase register) {
    this.register = register;
  }

  @PostMapping("/api/auth/register")
  public ResponseEntity<Map<String, UUID>> register(@RequestBody RegisterRequest request) {
    UUID id = register.register(IdentityWebMapper.toRegistration(request));
    return ResponseEntity.status(201).body(IdentityWebMapper.registrationResponse(id));
  }
}
