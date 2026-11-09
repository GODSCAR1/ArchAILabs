package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.application.RefreshService;
import com.codeja.master_acdc.identity.application.RefreshTokenAccess;
import com.codeja.master_acdc.identity.domain.model.PasswordChange;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessionLifecycleIT extends SessionIntegrationSupport {
  @Test
  void should_roll_back_rotation_when_failure_follows_successor_creation() {
    var user = registered();
    var session = login(user);
    var before = rows(user.id());
    assertThatThrownBy(
            () ->
                transactions.execute(
                    () -> {
                      refresh.refresh(session.refreshToken());
                      jdbc.execute("select 1/0");
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(rows(user.id())).isEqualTo(before);
    assertThat(refresh.refresh(session.refreshToken()).accepted()).isTrue();
  }

  @Test
  void should_roll_back_hash_and_revocations_when_transaction_fails_after_password_change() {
    var user = registered();
    login(user);
    login(user);
    var before = rows(user.id());
    assertThatThrownBy(
            () ->
                transactions.execute(
                    () -> {
                      change.changePassword(new PasswordChange(user.id(), PASSWORD, NEW_PASSWORD));
                      jdbc.execute("select 1/0");
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(rows(user.id())).isEqualTo(before);
    assertThat(accounts.findByEmail(user.email()).orElseThrow()).isEqualTo(user);
  }

  @Autowired private RefreshTokens tokens;
  @Autowired private AccessTokenIssuer issuer;
  @Autowired private RefreshTokenAccess access;

  @Test
  @DisplayName("Reutilización invalida todos los sucesores")
  void should_commit_replay_revocation_when_decorated_service_returns_rejected() {
    var user = registered();
    var first = login(user);
    var other = login(user);
    var successor = refresh.refresh(first.refreshToken()).tokens().orElseThrow();
    assertThat(refresh.refresh(first.refreshToken()).accepted()).isFalse();
    assertThat(rows(user.id())).filteredOn(row -> row.get("revoked_at") != null).hasSize(2);
    assertThat(refresh.refresh(successor.refreshToken()).accepted()).isFalse();
    assertThat(refresh.refresh(other.refreshToken()).accepted()).isTrue();
    assertThat(rows(user.id()))
        .allSatisfy(
            row ->
                assertThat(row.get("token_hash"))
                    .asString()
                    .hasSize(64)
                    .isNotEqualTo(first.refreshToken())
                    .isNotEqualTo(successor.refreshToken()));
  }

  @Test
  @DisplayName("Rotación no prolonga la familia")
  void should_preserve_absolute_expiry_when_rotating_on_day_twenty_nine() {
    var user = registered();
    var session = login(user);
    Instant start = Instant.parse("2026-10-01T12:00:00Z");
    Instant end = start.plus(30, ChronoUnit.DAYS);
    jdbc.update(
        "update refresh_token set created_at=?, expires_at=?, family_expires_at=? where"
            + " token_hash=?",
        java.sql.Timestamp.from(start.plus(28, ChronoUnit.DAYS)),
        java.sql.Timestamp.from(end),
        java.sql.Timestamp.from(end),
        secrets.hash(session.refreshToken()));
    var service =
        new RefreshService(
            access,
            tokens,
            secrets,
            issuer,
            Clock.fixed(start.plus(29, ChronoUnit.DAYS), ZoneOffset.UTC));
    var rotated =
        transactions.execute(() -> service.refresh(session.refreshToken())).tokens().orElseThrow();
    assertThat(rotated.refreshExpiresAt()).isEqualTo(end);
    assertThat(rows(user.id()))
        .hasSize(2)
        .allSatisfy(
            row ->
                assertThat(((java.sql.Timestamp) row.get("family_expires_at")).toInstant())
                    .isEqualTo(end));
    var expired =
        new RefreshService(access, tokens, secrets, issuer, Clock.fixed(end, ZoneOffset.UTC));
    assertThat(transactions.execute(() -> expired.refresh(rotated.refreshToken())).accepted())
        .isFalse();
  }

  @Test
  @DisplayName("Refresh vencido o desconocido")
  void should_preserve_rows_when_refresh_is_unknown_disabled_revoked_or_expired() {
    var user = registered();
    var session = login(user);
    var before = rows(user.id());
    assertThat(refresh.refresh(null).accepted()).isFalse();
    assertThat(refresh.refresh(secrets.generate().value()).accepted()).isFalse();
    assertThat(rows(user.id())).isEqualTo(before);
    jdbc.update("update user_account set enabled=false where id=?", user.id());
    assertThat(refresh.refresh(session.refreshToken()).accepted()).isFalse();
    jdbc.update("update user_account set enabled=true where id=?", user.id());
    jdbc.update(
        "update refresh_token set created_at=now()-interval '8 days', expires_at=now()-interval '1"
            + " day' where user_id=?",
        user.id());
    before = rows(user.id());
    assertThat(refresh.refresh(session.refreshToken()).accepted()).isFalse();
    assertThat(rows(user.id())).isEqualTo(before);
  }

  @Test
  @DisplayName("Logout impide renovar esa familia")
  void should_revoke_only_presented_family_when_logout_is_repeated_with_rotated_token() {
    var user = registered();
    var session = login(user);
    var other = login(user);
    var successor = refresh.refresh(session.refreshToken()).tokens().orElseThrow();
    assertThat(logout.logout(session.refreshToken())).isTrue();
    var after = rows(user.id());
    assertThat(logout.logout(session.refreshToken())).isTrue();
    assertThat(rows(user.id())).isEqualTo(after);
    assertThat(refresh.refresh(successor.refreshToken()).accepted()).isFalse();
    assertThat(refresh.refresh(other.refreshToken()).accepted()).isTrue();
  }

  @Test
  @DisplayName("Logout sin refresh conocido")
  void should_preserve_families_when_logout_is_unknown() {
    var user = registered();
    login(user);
    var before = rows(user.id());
    assertThat(logout.logout(null)).isFalse();
    assertThat(logout.logout(secrets.generate().value())).isFalse();
    assertThat(rows(user.id())).isEqualTo(before);
  }

  @Test
  @DisplayName("Cambio de contraseña cierra renovación de otro dispositivo")
  void should_revoke_all_user_families_when_password_changes() {
    var user = registered();
    var first = login(user);
    var second = login(user);
    var other = registered();
    var otherSession = login(other);
    change.changePassword(new PasswordChange(user.id(), PASSWORD, NEW_PASSWORD));
    assertThat(refresh.refresh(first.refreshToken()).accepted()).isFalse();
    assertThat(refresh.refresh(second.refreshToken()).accepted()).isFalse();
    assertThat(refresh.refresh(otherSession.refreshToken()).accepted()).isTrue();
    assertThatThrownBy(() -> login(user)).hasMessage("invalid-credentials");
    assertThat(
            login.login(
                new com.codeja.master_acdc.identity.domain.model.Login(user.email(), NEW_PASSWORD)))
        .isNotNull();
  }

  @Test
  @DisplayName("Contraseña actual incorrecta")
  void should_roll_back_when_current_or_new_password_is_invalid() {
    var user = registered();
    login(user);
    var before = rows(user.id());
    assertThatThrownBy(
            () -> change.changePassword(new PasswordChange(user.id(), "wrong", NEW_PASSWORD)))
        .hasMessage("invalid-credentials");
    for (String password : new String[] {"short", "password1234", "é".repeat(37)}) {
      assertThatThrownBy(
              () -> change.changePassword(new PasswordChange(user.id(), PASSWORD, password)))
          .hasMessage("invalid-password");
    }
    assertThat(rows(user.id())).isEqualTo(before);
    assertThat(accounts.findByEmail(user.email()).orElseThrow()).isEqualTo(user);
  }
}
