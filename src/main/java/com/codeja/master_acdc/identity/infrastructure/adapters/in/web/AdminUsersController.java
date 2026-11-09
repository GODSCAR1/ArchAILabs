package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.ListUsersUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminUsersController {
  private final ListUsersUseCase users;

  public AdminUsersController(ListUsersUseCase users) {
    this.users = users;
  }

  @GetMapping("/api/admin/users")
  @PreAuthorize("hasAuthority('user:read')")
  public AdminUsersResponse list(@RequestParam MultiValueMap<String, String> params) {
    return AdminUserWebMapper.response(users.list(AdminUserWebMapper.request(params)));
  }
}
