package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.domain.model.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

public final class IdentityCookies {
  private final CookieCsrfTokenRepository csrf;

  public IdentityCookies() {
    csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieCustomizer(cookie -> cookie.path("/").secure(true).sameSite("Strict"));
  }

  public void writeLogin(
      LoginResult result, HttpServletRequest request, HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from("refresh_token", result.refreshToken())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(java.time.Duration.ofDays(7))
            .build()
            .toString());
    var token = csrf.generateToken(request);
    csrf.saveToken(token, request, response);
  }
}
