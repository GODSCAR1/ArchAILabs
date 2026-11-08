package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

abstract class AuthHttpSupport extends IdentityIntegrationSupport {
  static final String PASSWORD = "Unique!12345X";
  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected RegisterUseCase register;
  @Autowired protected JwtDecoder decoder;
  @Autowired protected ObjectMapper json;
  @Autowired protected RSAKey key;

  protected Browser session() throws Exception {
    String email = email();
    var registration =
        mvc.perform(registration(email, PASSWORD, null))
            .andExpect(status().isCreated())
            .andReturn();
    UUID id =
        UUID.fromString(
            json.readTree(registration.getResponse().getContentAsString()).get("id").asString());
    return browser(id, login(email, PASSWORD).andExpect(status().isOk()).andReturn());
  }

  protected Browser browser(UUID user, MvcResult result) throws Exception {
    return new Browser(
        user,
        result.getResponse().getCookie("refresh_token"),
        result.getResponse().getCookie("XSRF-TOKEN"),
        json.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
            .get("accessToken")
            .asString());
  }

  protected MockHttpServletRequestBuilder cookiePost(String path, Browser browser) {
    return post(path)
        .secure(true)
        .cookie(browser.refresh(), browser.csrf())
        .header("X-XSRF-TOKEN", browser.csrf().getValue());
  }

  protected Browser rotate(Browser browser) throws Exception {
    var result =
        mvc.perform(cookiePost("/api/auth/refresh", browser))
            .andExpect(status().isOk())
            .andReturn();
    return new Browser(
        browser.user(),
        result.getResponse().getCookie("refresh_token"),
        browser.csrf(),
        json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString());
  }

  protected ResultActions problem(MockHttpServletRequestBuilder request, int status, String code)
      throws Exception {
    var result =
        mvc.perform(request)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                    .is(status));
    var response = result.andReturn().getResponse();
    assertThat(response.getContentType()).startsWith("application/problem+json");
    var body = json.readTree(response.getContentAsString());
    assertThat(body.get("type").asString())
        .isEqualTo("https://api.master-acdc.dev/problems/" + code);
    assertThat(body.get("status").asInt()).isEqualTo(status);
    assertThat(body.get("title").asString()).isNotBlank();
    assertThat(body.get("detail").asString()).isNotBlank();
    assertThat(body.get("instance").asString())
        .isEqualTo(
            request
                .buildRequest(new org.springframework.mock.web.MockServletContext())
                .getRequestURI());
    assertThat(response.getContentAsString()).doesNotContain(PASSWORD, "@example.com");
    return result;
  }

  protected List<Map<String, Object>> tokens(UUID user) {
    return jdbc.queryForList(
        "select * from refresh_token where user_id=? order by created_at", user);
  }

  protected MockHttpServletRequestBuilder password(
      Browser browser, String current, String replacement) {
    return authorizedPost("/api/users/me/password")
        .header("Authorization", "Bearer " + browser.access())
        .content(
            json.writeValueAsString(
                Map.of("currentPassword", current, "newPassword", replacement)));
  }

  protected String signedToken(
      Map<String, Object> overrides, String kid, RSAKey signingKey, JWSAlgorithm algorithm)
      throws JOSEException {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer("https://api.master-acdc.dev")
            .audience("master-acdc-api")
            .subject(UUID.randomUUID().toString())
            .issueTime(java.util.Date.from(Instant.now()))
            .expirationTime(java.util.Date.from(Instant.now().plusSeconds(900)))
            .jwtID(UUID.randomUUID().toString())
            .claim("roles", List.of("ADMIN"));
    overrides.forEach(claims::claim);
    var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(kid).build(), claims.build());
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  protected MockHttpServletRequestBuilder registration(String email, String password, String name) {
    var fields = new java.util.HashMap<String, Object>();
    fields.put("email", email);
    fields.put("password", password);
    if (name != null) {
      fields.put("name", name);
    }
    return authorizedPost("/api/auth/register").content(json.writeValueAsString(fields));
  }

  protected ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        authorizedPost("/api/auth/login")
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  protected MockHttpServletRequestBuilder authorizedPost(String path) {
    return post(path).secure(true).contentType(MediaType.APPLICATION_JSON);
  }

  protected long count(String table) {
    return switch (table) {
      case "customers" -> jdbc.queryForObject("select count(*) from customers", Long.class);
      case "user_account" -> jdbc.queryForObject("select count(*) from user_account", Long.class);
      case "refresh_token" -> jdbc.queryForObject("select count(*) from refresh_token", Long.class);
      default -> throw new IllegalArgumentException("Unknown table");
    };
  }

  protected static String email() {
    return UUID.randomUUID() + "@example.com";
  }

  protected record Browser(UUID user, Cookie refresh, Cookie csrf, String access) {
    @Override
    public String toString() {
      return "Browser[redacted]";
    }
  }
}
