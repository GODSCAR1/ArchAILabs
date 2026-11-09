package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.ChangePasswordUseCase;
import com.codeja.master_acdc.identity.application.port.LogoutUseCase;
import com.codeja.master_acdc.identity.application.port.RefreshUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
  private final RefreshUseCase refresh;
  private final LogoutUseCase logout;
  private final ChangePasswordUseCase passwords;
  private final IdentityCookies cookies;

  public SessionController(
      RefreshUseCase refresh,
      LogoutUseCase logout,
      ChangePasswordUseCase passwords,
      IdentityCookies cookies) {
    this.refresh = refresh;
    this.logout = logout;
    this.passwords = passwords;
    this.cookies = cookies;
  }

  @PostMapping("/api/auth/refresh")
  public Map<String, Object> refresh(
      @CookieValue(name = "refresh_token", required = false) String secret,
      HttpServletRequest request,
      HttpServletResponse response) {
    var result =
        refresh
            .refresh(secret)
            .tokens()
            .orElseThrow(() -> new IdentityException("invalid-refresh-token"));
    cookies.writeLogin(result, request, response);
    return IdentityWebMapper.loginResponse(result);
  }

  @PostMapping("/api/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @CookieValue(name = "refresh_token", required = false) String secret,
      HttpServletResponse response) {
    if (!logout.logout(secret)) {
      throw new IdentityException("invalid-refresh-token");
    }
    cookies.clear(response);
  }

  @PostMapping("/api/users/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal Jwt jwt, @RequestBody PasswordChangeRequest request) {
    passwords.changePassword(IdentityWebMapper.toPasswordChange(jwt.getSubject(), request));
  }
}
