package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.LoginUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {
  private final LoginUseCase login;
  private final IdentityCookies cookies;

  public LoginController(LoginUseCase login, IdentityCookies cookies) {
    this.login = login;
    this.cookies = cookies;
  }

  @PostMapping("/api/auth/login")
  public Map<String, Object> login(
      @RequestBody LoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse response) {
    var result = login.login(IdentityWebMapper.toLogin(request));
    cookies.writeLogin(result, servletRequest, response);
    return IdentityWebMapper.loginResponse(result);
  }
}
