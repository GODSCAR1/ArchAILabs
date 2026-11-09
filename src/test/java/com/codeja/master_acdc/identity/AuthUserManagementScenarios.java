package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

abstract class AuthUserManagementScenarios extends AuthSessionVersionScenarios {
  @Autowired AccessTokenIssuer issuer;

  @Test
  @DisplayName("Administrador bloquea y cierra acceso inmediatamente")
  void should_close_both_devices_when_admin_blocks_account() throws Exception {
    var target = session();
    var outsider = session();
    var other = browser(target.user(), login(emailOf(target), PASSWORD).andReturn());
    var outsiderTokens = tokens(outsider.user());
    manage(target.user(), "block");
    for (Browser browser : java.util.List.of(target, other)) {
      rejectedAccess(browser);
      problem(cookiePost("/api/auth/refresh", browser), 401, "invalid-refresh-token");
    }
    login(emailOf(target), PASSWORD).andExpect(status().isUnauthorized());
    assertThat(tokens(target.user()))
        .hasSize(2)
        .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    assertThat(tokens(outsider.user())).isEqualTo(outsiderTokens);
    rotate(outsider);
  }

  @Test
  @DisplayName("Bloqueo repetido no recupera sesiones")
  void should_increment_again_when_block_is_repeated() throws Exception {
    var target = session();
    manage(target.user(), "block");
    manage(target.user(), "block");
    assertThat(version(target.user())).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select enabled from user_account where id=?", Boolean.class, target.user()))
        .isFalse();
    rejectedAccess(target);
    problem(cookiePost("/api/auth/refresh", target), 401, "invalid-refresh-token");
  }

  @Test
  @DisplayName("Desbloqueo permite login nuevo pero no tokens anteriores")
  void should_require_new_session_when_admin_unblocks_account() throws Exception {
    var target = session();
    manage(target.user(), "block");
    var revoked = tokens(target.user());
    manage(target.user(), "unblock");
    assertThat(version(target.user())).isEqualTo(1);
    assertThat(tokens(target.user())).isEqualTo(revoked);
    rejectedAccess(target);
    problem(cookiePost("/api/auth/refresh", target), 401, "invalid-refresh-token");
    var next =
        browser(
            target.user(), login(emailOf(target), PASSWORD).andExpect(status().isOk()).andReturn());
    mvc.perform(
            get("/api/orders")
                .param("status", "PAID")
                .header("Authorization", "Bearer " + next.access()))
        .andExpect(status().isOk());
    assertThat(((Number) decoder.decode(rotate(next).access()).getClaims().get("sv")).longValue())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Desbloqueo repetido conserva versión y familias")
  void should_leave_version_and_active_family_unchanged_when_unblock_is_repeated()
      throws Exception {
    var target = session();
    var before = tokens(target.user());
    manage(target.user(), "unblock");
    manage(target.user(), "unblock");
    assertThat(version(target.user())).isZero();
    assertThat(tokens(target.user())).isEqualTo(before);
    rotate(target);
  }

  private void manage(UUID id, String action) throws Exception {
    String admin = OrderBearer.issue(jdbc, issuer, null, "ADMIN");
    mvc.perform(
            post("/api/admin/users/" + id + "/" + action)
                .header("Authorization", "Bearer " + admin))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  private void rejectedAccess(Browser browser) throws Exception {
    problem(
        get("/api/orders")
            .param("status", "PAID")
            .header("Authorization", "Bearer " + browser.access()),
        401,
        "unauthorized");
  }

  private String emailOf(Browser browser) {
    return jdbc.queryForObject(
        "select email from user_account where id=?", String.class, browser.user());
  }

  private long version(UUID id) {
    return jdbc.queryForObject(
        "select session_version from user_account where id=?", Long.class, id);
  }
}
