package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.domain.model.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.DeferredCsrfToken;

public final class IdentityCookies {
  private final Clock clock;

  public IdentityCookies(Clock clock) {
    this.clock = clock;
  }

  public void writeLogin(
      LoginResult result, HttpServletRequest request, HttpServletResponse response) {
    // La cookie vive lo mismo que el refresh: min(7 días, tope absoluto de la familia).
    Duration maxAge = Duration.between(clock.instant(), result.refreshExpiresAt());
    writeRefresh(result.refreshToken(), maxAge.isNegative() ? Duration.ZERO : maxAge, response);
    var deferred = (DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName());
    deferred.get();
  }

  public void clear(HttpServletResponse response) {
    writeRefresh("", Duration.ZERO, response);
  }

  private void writeRefresh(String secret, Duration maxAge, HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from("refresh_token", secret)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(maxAge)
            .build()
            .toString());
  }
}
