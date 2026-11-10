package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.identity.domain.model.Registration;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.notification.InMemoryPasswordResetNotifier;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class PasswordResetFlowIT extends AuthHttpSupport {
  @Autowired private InMemoryPasswordResetNotifier notifier;
  @Autowired private Passwords passwords;
  @Autowired private StringRedisTemplate redis;

  @Test
  @DisplayName("Recuperación pública sin sesión ni cookie")
  void should_recover_without_authentication_when_account_exists() throws Exception {
    exerciseFlow();
  }

  @Test
  @DisplayName("Solicitud de recuperación notifica al destinatario")
  void should_notify_when_recovery_is_requested() throws Exception {
    exerciseFlow();
  }

  @Test
  @DisplayName("Recuperación permite iniciar sesión con la nueva contraseña")
  void should_allow_login_when_password_is_recovered() throws Exception {
    exerciseFlow();
  }

  @Test
  @DisplayName("Recuperación actualiza credenciales y estado de sesión conjuntamente")
  void should_revoke_sessions_when_password_is_recovered() throws Exception {
    exerciseFlow();
  }

  private void exerciseFlow() throws Exception {
    String email = email();
    var id = register.register(new Registration(email, PASSWORD, null));
    login(email, PASSWORD).andExpect(status().isOk());
    var response =
        mvc.perform(
                authorizedPost("/api/auth/password-reset/request")
                    .content(json.writeValueAsString(Map.of("email", email))))
            .andExpect(status().isAccepted())
            .andExpect(content().string(""))
            .andReturn()
            .getResponse();
    assertThat(response.getCookies()).isEmpty();
    String token = notifier.lastMessageFor(email).orElseThrow().token();
    var confirmation =
        mvc.perform(
                authorizedPost("/api/auth/password-reset/confirm")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email", email, "token", token, "newPassword", "Unique!67890Z"))))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andReturn()
            .getResponse();
    assertThat(confirmation.getCookies()).isEmpty();
    assertRecoveryState(id);
    login(email, "Unique!67890Z").andExpect(status().isOk());
  }

  private void assertRecoveryState(java.util.UUID id) {
    String hash =
        jdbc.queryForObject("select password_hash from user_account where id=?", String.class, id);
    assertThat(passwords.matches("Unique!67890Z", hash)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from password_reset_token where user_id=?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select session_version from user_account where id=?", Long.class, id))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from refresh_token where user_id=? and revoked_at is null",
                Long.class,
                id))
        .isZero();
    assertThat(redis.opsForValue().get("shop:identity:session-version:v1:" + id)).isEqualTo("1");
  }
}
