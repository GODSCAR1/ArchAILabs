package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.BlockUserUseCase;
import com.codeja.master_acdc.identity.application.port.UnblockUserUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminUserManagementController {
  private final BlockUserUseCase block;
  private final UnblockUserUseCase unblock;

  public AdminUserManagementController(BlockUserUseCase block, UnblockUserUseCase unblock) {
    this.block = block;
    this.unblock = unblock;
  }

  @PostMapping("/api/admin/users/{id}/block")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:manage')")
  public void block(@PathVariable String id) {
    block.block(AdminUserWebMapper.targetId(id));
  }

  @PostMapping("/api/admin/users/{id}/unblock")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:manage')")
  public void unblock(@PathVariable String id) {
    unblock.unblock(AdminUserWebMapper.targetId(id));
  }
}
