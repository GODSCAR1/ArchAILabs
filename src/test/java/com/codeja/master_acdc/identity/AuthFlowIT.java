package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.codeja.master_acdc.identity.domain.model.Registration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

class AuthFlowIT extends IdentityIntegrationSupport {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RegisterUseCase register;
  @Autowired private JwtDecoder decoder;
  @Autowired private ObjectMapper json;

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
        mvc.perform(get("/.well-known/jwks.json").with(user("identity-fixture")))
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

  private MockHttpServletRequestBuilder registration(String email, String password, String name)
      throws Exception {
    var fields = new java.util.HashMap<String, Object>();
    fields.put("email", email);
    fields.put("password", password);
    if (name != null) {
      fields.put("name", name);
    }
    return authorizedPost("/api/auth/register").content(json.writeValueAsString(fields));
  }

  private org.springframework.test.web.servlet.ResultActions login(String email, String password)
      throws Exception {
    return mvc.perform(
        authorizedPost("/api/auth/login")
            .content(
                json.writeValueAsString(java.util.Map.of("email", email, "password", password))));
  }

  private MockHttpServletRequestBuilder authorizedPost(String path) {
    // Section 5 will replace this fixture authentication with the public-route and real-bearer
    // scenarios.
    return post(path)
        .secure(true)
        .with(user("identity-fixture"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON);
  }

  private long count(String table) {
    return switch (table) {
      case "customers" -> jdbc.queryForObject("select count(*) from customers", Long.class);
      case "user_account" -> jdbc.queryForObject("select count(*) from user_account", Long.class);
      case "refresh_token" -> jdbc.queryForObject("select count(*) from refresh_token", Long.class);
      default -> throw new IllegalArgumentException("Unknown table");
    };
  }

  private static String email() {
    return UUID.randomUUID() + "@example.com";
  }
}
