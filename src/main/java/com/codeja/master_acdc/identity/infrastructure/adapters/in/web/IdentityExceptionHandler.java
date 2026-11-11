package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    assignableTypes = {
      AuthController.class,
      LoginController.class,
      SessionController.class,
      PasswordResetController.class,
      AdminUsersController.class,
      AdminUserManagementController.class
    })
public class IdentityExceptionHandler {
  @ExceptionHandler(IdentityException.class)
  public ProblemDetail handle(IdentityException exception, HttpServletResponse response) {
    int status =
        switch (exception.code()) {
          case "email-already-registered" -> 409;
          case "user-not-found" -> 404;
          case "invalid-credentials", "invalid-refresh-token" -> 401;
          case "authentication-busy", "session-version-unavailable" -> 503;
          default -> 400;
        };
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatusCode.valueOf(status), exception.code());
    problem.setType(URI.create("https://api.master-acdc.dev/problems/" + exception.code()));
    if (status == 503) {
      response.setHeader("Retry-After", "1");
    }
    return problem;
  }
}
