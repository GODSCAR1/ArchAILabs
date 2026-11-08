package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

public final class SecurityProblems {
  private final ObjectMapper json;

  public SecurityProblems(ObjectMapper json) {
    this.json = json;
  }

  public AuthenticationEntryPoint entryPoint(AuthenticationEntryPoint challenge) {
    return (request, response, failure) -> {
      challenge.commence(request, response, failure);
      write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized");
    };
  }

  public AccessDeniedHandler deniedHandler() {
    return (request, response, failure) ->
        write(request, response, HttpStatus.FORBIDDEN, "forbidden");
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
      throws IOException {
    var problem = ProblemDetail.forStatusAndDetail(status, code);
    problem.setType(URI.create("https://api.master-acdc.dev/problems/" + code));
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }
}
