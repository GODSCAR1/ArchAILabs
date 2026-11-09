package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.identity.domain.model.Registration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIT extends AuthHttpConcurrencyScenarios {
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private java.time.Clock identityClock;

  @Test
  @DisplayName("Registro seguido de login y llamada autenticada")
  void should_call_orders_when_registered_user_presents_real_bearer() throws Exception {
    var browser = session();
    mvc.perform(
            get("/api/orders")
                .secure(true)
                .param("status", "PAID")
                .header("Authorization", "Bearer " + browser.access()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Token inválido no autentica")
  void should_reject_bearer_when_claims_key_algorithm_or_kid_are_invalid() throws Exception {
    var before = jdbc.queryForList("select * from orders");
    for (var claims :
        java.util.List.of(
            java.util.Map.<String, Object>of("aud", "other"),
            java.util.Map.<String, Object>of("iss", "https://other.example"),
            java.util.Map.<String, Object>of("iss", "https://api.master-acdc.dev/"),
            java.util.Map.<String, Object>of(
                "exp", java.util.Date.from(java.time.Instant.now().minusSeconds(1))))) {
      invalidBearer(signedToken(claims, key.getKeyID(), key, com.nimbusds.jose.JWSAlgorithm.RS256));
    }
    invalidBearer(
        signedToken(java.util.Map.of(), "wrong-kid", key, com.nimbusds.jose.JWSAlgorithm.RS256));
    invalidBearer(signedToken(java.util.Map.of(), null, key, com.nimbusds.jose.JWSAlgorithm.RS256));
    invalidBearer(
        signedToken(java.util.Map.of(), key.getKeyID(), key, com.nimbusds.jose.JWSAlgorithm.RS512));
    var other = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
    invalidBearer(
        signedToken(
            java.util.Map.of(), key.getKeyID(), other, com.nimbusds.jose.JWSAlgorithm.RS256));
    assertThat(jdbc.queryForList("select * from orders")).isEqualTo(before);
  }

  @Test
  @DisplayName("Refresh válido emite nuevo par")
  void should_rotate_secret_and_preserve_family_when_refresh_is_valid() throws Exception {
    var original = session();
    var before = tokens(original.user()).getFirst();
    var successor = rotate(original);
    assertThat(successor.refresh().getValue()).isNotEqualTo(original.refresh().getValue());
    assertThat(successor.access()).isNotEqualTo(original.access());
    assertThat(tokens(original.user()))
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.get("family_id")).isEqualTo(before.get("family_id"));
              assertThat(row.get("family_expires_at")).isEqualTo(before.get("family_expires_at"));
              assertThat(row.values())
                  .doesNotContain(original.refresh().getValue(), successor.refresh().getValue());
            });
  }

  @Test
  @DisplayName("Refresh vencido o desconocido")
  void should_reject_without_successor_when_refresh_is_missing_unknown_expired_or_revoked()
      throws Exception {
    var browser = session();
    var missing =
        post("/api/auth/refresh")
            .secure(true)
            .cookie(browser.csrf())
            .header("X-XSRF-TOKEN", browser.csrf().getValue());
    problem(missing, 401, "invalid-refresh-token");
    var unknown =
        new Browser(
            browser.user(),
            new jakarta.servlet.http.Cookie("refresh_token", "unknown"),
            browser.csrf(),
            browser.access());
    problem(cookiePost("/api/auth/refresh", unknown), 401, "invalid-refresh-token");
    var expiration =
        ((java.sql.Timestamp) tokens(browser.user()).getFirst().get("expires_at")).toInstant();
    org.mockito.Mockito.doReturn(expiration).when(identityClock).instant();
    problem(cookiePost("/api/auth/refresh", browser), 401, "invalid-refresh-token");
    assertThat(tokens(browser.user())).hasSize(1);
    assertThat(tokens(browser.user()).getFirst().get("rotated_at")).isNull();
  }

  @Test
  @DisplayName("Rotación no prolonga la familia")
  void should_expire_at_original_day_thirty_when_rotating_on_day_twenty_nine() throws Exception {
    var browser = session();
    var created =
        ((java.sql.Timestamp) tokens(browser.user()).getFirst().get("created_at")).toInstant();
    for (int day : new int[] {6, 12, 18, 24, 29}) {
      org.mockito.Mockito.doReturn(created.plus(java.time.Duration.ofDays(day)))
          .when(identityClock)
          .instant();
      browser = rotate(browser);
    }
    var last = tokens(browser.user()).getLast();
    assertThat(last.get("expires_at")).isEqualTo(last.get("family_expires_at"));
    org.mockito.Mockito.doReturn(created.plus(java.time.Duration.ofDays(30)))
        .when(identityClock)
        .instant();
    problem(cookiePost("/api/auth/refresh", browser), 401, "invalid-refresh-token");
  }

  @Test
  @DisplayName("Reutilización invalida todos los sucesores")
  void should_commit_family_revocation_when_rotated_secret_is_replayed() throws Exception {
    var browser = session();
    String email =
        jdbc.queryForObject(
            "select email from user_account where id=?", String.class, browser.user());
    var other = browser(browser.user(), login(email, PASSWORD).andReturn());
    var successor = rotate(browser);
    problem(cookiePost("/api/auth/refresh", browser), 401, "invalid-refresh-token");
    problem(cookiePost("/api/auth/refresh", successor), 401, "invalid-refresh-token");
    assertThat(tokens(browser.user())).filteredOn(row -> row.get("revoked_at") != null).hasSize(2);
    rotate(other);
  }

  @Test
  @DisplayName("Logout impide renovar esa familia")
  void should_clear_cookie_and_keep_other_device_when_logout_is_repeated() throws Exception {
    var browser = session();
    String email =
        jdbc.queryForObject(
            "select email from user_account where id=?", String.class, browser.user());
    var other = browser(browser.user(), login(email, PASSWORD).andReturn());
    var response =
        mvc.perform(cookiePost("/api/auth/logout", browser))
            .andExpect(status().isNoContent())
            .andReturn();
    var cookie = response.getResponse().getCookie("refresh_token");
    assertThat(cookie.getMaxAge()).isZero();
    assertThat(cookie.getPath()).isEqualTo("/api/auth");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    mvc.perform(cookiePost("/api/auth/logout", browser)).andExpect(status().isNoContent());
    problem(cookiePost("/api/auth/refresh", browser), 401, "invalid-refresh-token");
    rotate(other);
  }

  @Test
  @DisplayName("Logout sin refresh conocido")
  void should_reject_logout_when_refresh_is_missing_or_unknown() throws Exception {
    var browser = session();
    var before = tokens(browser.user());
    problem(
        post("/api/auth/logout")
            .secure(true)
            .cookie(browser.csrf())
            .header("X-XSRF-TOKEN", browser.csrf().getValue()),
        401,
        "invalid-refresh-token");
    var unknown =
        new Browser(
            browser.user(),
            new jakarta.servlet.http.Cookie("refresh_token", "unknown"),
            browser.csrf(),
            browser.access());
    problem(cookiePost("/api/auth/logout", unknown), 401, "invalid-refresh-token");
    assertThat(tokens(browser.user())).isEqualTo(before);
  }

  @Test
  @DisplayName("Cambio de contraseña cierra renovación de otro dispositivo")
  void should_revoke_all_user_families_when_password_changes() throws Exception {
    var browser = session();
    var outsider = session();
    String email =
        jdbc.queryForObject(
            "select email from user_account where id=?", String.class, browser.user());
    var other = browser(browser.user(), login(email, PASSWORD).andReturn());
    mvc.perform(password(browser, PASSWORD, "Changed!12345X")).andExpect(status().isNoContent());
    problem(cookiePost("/api/auth/refresh", browser), 401, "invalid-refresh-token");
    problem(cookiePost("/api/auth/refresh", other), 401, "invalid-refresh-token");
    login(email, "Changed!12345X").andExpect(status().isOk());
    rotate(outsider);
  }

  @Test
  @DisplayName("Contraseña actual incorrecta")
  void should_preserve_credentials_and_families_when_password_change_is_rejected()
      throws Exception {
    var browser = session();
    var before = tokens(browser.user());
    var hash =
        jdbc.queryForObject(
            "select password_hash from user_account where id=?", String.class, browser.user());
    problem(password(browser, "wrong", "Changed!12345X"), 401, "invalid-credentials");
    for (String invalid : new String[] {"short", "password1234", "é".repeat(37)}) {
      problem(password(browser, PASSWORD, invalid), 400, "invalid-password");
    }
    assertThat(tokens(browser.user())).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select password_hash from user_account where id=?", String.class, browser.user()))
        .isEqualTo(hash);
    rotate(browser);
  }

  @Test
  @DisplayName("Refresh sin cabecera CSRF")
  void should_not_consume_refresh_when_csrf_header_is_missing() throws Exception {
    var browser = session();
    var before = tokens(browser.user());
    problem(
        post("/api/auth/refresh").secure(true).cookie(browser.refresh(), browser.csrf()),
        403,
        "forbidden");
    assertThat(tokens(browser.user())).isEqualTo(before);
    rotate(browser);
  }

  @Test
  @DisplayName("Logout con CSRF incorrecto")
  void should_preserve_family_when_csrf_is_wrong_or_cookie_is_missing() throws Exception {
    var browser = session();
    var before = tokens(browser.user());
    problem(
        post("/api/auth/logout")
            .secure(true)
            .cookie(browser.refresh(), browser.csrf())
            .header("X-XSRF-TOKEN", "wrong"),
        403,
        "forbidden");
    problem(
        post("/api/auth/logout")
            .secure(true)
            .cookie(browser.refresh())
            .header("X-XSRF-TOKEN", browser.csrf().getValue()),
        403,
        "forbidden");
    assertThat(tokens(browser.user())).isEqualTo(before);
  }

  @Test
  @DisplayName("Cabecera CSRF acepta valor crudo")
  void should_accept_cookie_value_from_spa_path_when_csrf_header_is_raw() throws Exception {
    var browser = session();
    assertThat(browser.csrf().getPath()).isEqualTo("/");
    assertThat(browser.csrf().isHttpOnly()).isFalse();
    rotate(browser);
  }

  @Test
  @DisplayName("Registro y login no requieren CSRF")
  void should_materialize_csrf_when_public_login_has_no_previous_cookie() throws Exception {
    assertThat(session().csrf()).isNotNull();
  }

  private void invalidBearer(String token) throws Exception {
    problem(
            get("/api/orders")
                .secure(true)
                .param("status", "PAID")
                .header("Authorization", "Bearer " + token),
            401,
            "unauthorized")
        .andExpect(
            header()
                .string(
                    "WWW-Authenticate",
                    org.hamcrest.Matchers.startsWith("Bearer error=\"invalid_token\"")));
  }

  @Test
  @DisplayName("Registro con nombre explícito")
  void should_register_only_id_when_explicit_name_is_valid() throws Exception {
    String email = email();
    var result =
        mvc.perform(registration(email, "Unique!12345X", "identity-explicit"))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist("Location"))
            .andReturn();
    assertThat(json.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select c.name from customers c join user_account u on u.customer_id=c.id where"
                    + " u.email=?",
                String.class,
                email))
        .isEqualTo("identity-explicit");
  }

  @Test
  @DisplayName("Registro sin nombre")
  void should_derive_name_when_name_is_omitted() throws Exception {
    String email = email();
    mvc.perform(registration(email.toUpperCase(java.util.Locale.ROOT), "Unique!12345X", null))
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist("Location"));
    assertThat(
            jdbc.queryForObject(
                "select c.name from customers c join user_account u on u.customer_id=c.id where"
                    + " u.email=?",
                String.class,
                email))
        .isEqualTo(email.substring(0, email.indexOf('@')));
  }

  @Test
  @DisplayName("Registro duplicado no crea cliente huérfano")
  void should_return_conflict_without_customer_when_email_already_exists() throws Exception {
    String email = email();
    mvc.perform(registration(email, "Unique!12345X", null)).andExpect(status().isCreated());
    long customers = count("customers");
    long accounts = count("user_account");
    mvc.perform(registration(email.toUpperCase(java.util.Locale.ROOT), "Unique!12345X", null))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/email-already-registered"));
    assertThat(count("customers")).isEqualTo(customers);
    assertThat(count("user_account")).isEqualTo(accounts);
  }

  @Test
  @DisplayName("Contraseña corta o común")
  void should_return_invalid_password_when_password_is_short_or_common() throws Exception {
    for (String password : new String[] {"short", "password1234"}) {
      mvc.perform(registration(email(), password, null))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-password"));
    }
  }

  @Test
  @DisplayName("Contraseña no representable sin truncamiento")
  void should_reject_without_write_when_password_exceeds_encoder_limit() throws Exception {
    long customers = count("customers");
    long accounts = count("user_account");
    mvc.perform(registration(email(), "é".repeat(37), null))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-password"));
    assertThat(count("customers")).isEqualTo(customers);
    assertThat(count("user_account")).isEqualTo(accounts);
  }

  @Test
  @DisplayName("Datos de registro inválidos")
  void should_reject_registration_when_name_or_email_is_invalid() throws Exception {
    long before = count("user_account");
    mvc.perform(registration("not-email", "Unique!12345X", null))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-registration"));
    for (String name : new String[] {"", " ", "x".repeat(121)}) {
      mvc.perform(registration(email(), "Unique!12345X", name)).andExpect(status().isBadRequest());
    }
    assertThat(count("user_account")).isEqualTo(before);
  }

  @Test
  @DisplayName("Claims exactos del access token")
  void should_issue_verifiable_token_when_registered_account_logs_in() throws Exception {
    String email = email();
    UUID id = register.register(new Registration(email, "Unique!12345X", null));
    var response =
        login(email, "Unique!12345X")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn();
    var body = json.readTree(response.getResponse().getContentAsString());
    assertThat(body.size()).isEqualTo(2);
    var jwt = decoder.decode(body.get("accessToken").asString());
    assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "aud", "iat", "exp", "jti", "roles");
    assertThat(jwt.getSubject()).isEqualTo(id.toString());
    assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond())
        .isEqualTo(900);
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
  }

  @Test
  @DisplayName("Claims permanecen exactos tras login")
  void should_keep_exact_seven_claims_when_login_issues_access_token() throws Exception {
    var jwt = decoder.decode(session().access());
    assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "aud", "iat", "exp", "jti", "roles");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
  }

  @Test
  @DisplayName("Login no revela existencia del email")
  void should_return_identical_problem_when_user_is_unknown_or_password_is_wrong()
      throws Exception {
    String email = email();
    register.register(new Registration(email, "Unique!12345X", null));
    long families = count("refresh_token");
    var unknown = login(email(), "wrong").andExpect(status().isUnauthorized()).andReturn();
    var incorrect = login(email, "wrong").andExpect(status().isUnauthorized()).andReturn();
    assertThat(unknown.getResponse().getContentAsString())
        .isEqualTo(incorrect.getResponse().getContentAsString());
    assertThat(json.readTree(unknown.getResponse().getContentAsString()).get("type").asString())
        .isEqualTo("https://api.master-acdc.dev/problems/invalid-credentials");
    assertThat(count("refresh_token")).isEqualTo(families);
  }

  @Test
  @DisplayName("Cuenta deshabilitada no inicia sesión")
  void should_not_create_family_when_account_is_disabled() throws Exception {
    String email = email();
    UUID id = register.register(new Registration(email, "Unique!12345X", null));
    jdbc.update("update user_account set enabled=false where id=?", id);
    long before = count("refresh_token");
    login(email, "Unique!12345X").andExpect(status().isUnauthorized());
    assertThat(count("refresh_token")).isEqualTo(before);
  }

  @Test
  @DisplayName("Login emite cookies utilizables y seguras")
  void should_emit_secure_cookies_when_login_has_no_csrf_cookie() throws Exception {
    String email = email();
    register.register(new Registration(email, "Unique!12345X", null));
    var result = login(email, "Unique!12345X").andExpect(status().isOk()).andReturn();
    var refresh = result.getResponse().getCookie("refresh_token");
    var xsrf = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(refresh).isNotNull();
    assertThat(xsrf).isNotNull();
    assertThat(refresh.isHttpOnly()).isTrue();
    assertThat(refresh.getSecure()).isTrue();
    assertThat(refresh.getPath()).isEqualTo("/api/auth");
    assertThat(refresh.getAttribute("SameSite")).isEqualTo("Strict");
    assertThat(xsrf.isHttpOnly()).isFalse();
    assertThat(xsrf.getSecure()).isTrue();
    assertThat(xsrf.getPath()).isEqualTo("/");
    assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Strict");
    String hash =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(
                        refresh.getValue().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from refresh_token where token_hash=?", Long.class, hash))
        .isEqualTo(1);
    assertThat(result.getResponse().getContentAsString()).doesNotContain(refresh.getValue());
  }

  @Test
  @DisplayName("JWKS público verifica la firma")
  void should_publish_only_public_key_when_jwks_handler_is_called() throws Exception {
    var result =
        mvc.perform(get("/.well-known/jwks.json").secure(true))
            .andExpect(status().isOk())
            .andReturn();
    var keys = com.nimbusds.jose.jwk.JWKSet.parse(result.getResponse().getContentAsString());
    assertThat(keys.getKeys()).hasSize(1);
    assertThat(keys.getKeys().getFirst().isPrivate()).isFalse();
    String email = email();
    register.register(new Registration(email, "Unique!12345X", null));
    MvcResult response = login(email, "Unique!12345X").andReturn();
    var jwt =
        com.nimbusds.jwt.SignedJWT.parse(
            json.readTree(response.getResponse().getContentAsString())
                .get("accessToken")
                .asString());
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(keys.getKeys().getFirst().getKeyID());
    assertThat(
            jwt.verify(
                new com.nimbusds.jose.crypto.RSASSAVerifier(keys.getKeys().getFirst().toRSAKey())))
        .isTrue();
  }
}
