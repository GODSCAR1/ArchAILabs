package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.ConfirmPasswordResetUseCase;
import com.codeja.master_acdc.identity.application.port.RequestPasswordResetUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordResetController {
  private final RequestPasswordResetUseCase request;
  private final ConfirmPasswordResetUseCase confirm;

  public PasswordResetController(
      RequestPasswordResetUseCase request, ConfirmPasswordResetUseCase confirm) {
    this.request = request;
    this.confirm = confirm;
  }

  @PostMapping("/api/auth/password-reset/request")
  public ResponseEntity<Void> request(@RequestBody PasswordResetRequestBody body) {
    request.request(PasswordResetWebMapper.request(body));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/api/auth/password-reset/confirm")
  public ResponseEntity<Void> confirm(@RequestBody PasswordResetConfirmBody body) {
    confirm.confirm(PasswordResetWebMapper.confirmation(body));
    return ResponseEntity.noContent().build();
  }
}
