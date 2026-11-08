package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthController.class, LoginController.class})
public class IdentityExceptionHandler {
  @ExceptionHandler(IdentityException.class)
  public ProblemDetail handle(IdentityException exception) {
    int status =
        switch (exception.code()) {
          case "email-already-registered" -> 409;
          case "invalid-credentials" -> 401;
          default -> 400;
        };
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatusCode.valueOf(status), exception.code());
    problem.setType(URI.create("https://api.master-acdc.dev/problems/" + exception.code()));
    return problem;
  }
}
