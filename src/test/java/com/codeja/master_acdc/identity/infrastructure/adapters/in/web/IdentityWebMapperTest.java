package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.AccessToken;
import com.codeja.master_acdc.identity.domain.model.LoginResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityWebMapperTest {
  @Test
  void should_use_authenticated_subject_when_mapping_password_change() {
    UUID id = UUID.randomUUID();
    var change =
        IdentityWebMapper.toPasswordChange(
            id.toString(), new PasswordChangeRequest("current", "new"));
    assertThat(change.userId()).isEqualTo(id);
    assertThat(change.currentPassword()).isEqualTo("current");
    assertThat(change.newPassword()).isEqualTo("new");
    assertThat(change.toString()).doesNotContain("current", "new");
  }

  @Test
  void should_map_only_public_fields_when_registration_succeeds() {
    var registration =
        IdentityWebMapper.toRegistration(
            new RegisterRequest("Cliente@Example.com", "Unique!12345X", null));
    assertThat(registration.email()).isEqualTo("cliente@example.com");
    assertThat(registration.name()).isEqualTo("cliente");
    UUID id = UUID.randomUUID();
    assertThat(IdentityWebMapper.registrationResponse(id))
        .containsOnlyKeys("id")
        .containsEntry("id", id);
  }

  @Test
  void should_exclude_refresh_when_mapping_login_response() {
    var login = IdentityWebMapper.toLogin(new LoginRequest("Cliente@Example.com", "secret"));
    assertThat(login.email()).isEqualTo("cliente@example.com");
    assertThat(
            IdentityWebMapper.loginResponse(
                new LoginResult(new AccessToken("jwt", 900), "refresh-secret", Instant.now())))
        .containsOnlyKeys("accessToken", "expiresIn")
        .containsEntry("expiresIn", 900L);
    assertThat(login.toString()).doesNotContain("secret", "cliente");
  }
}
