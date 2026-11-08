package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActuatorSecurityIT extends AuthHttpSupport {
  @Test
  @DisplayName("Health público")
  void should_allow_health_when_credentials_are_absent() throws Exception {
    mvc.perform(get("/actuator/health").secure(true)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Bearer ADMIN no autentica operaciones")
  void should_challenge_basic_when_valid_admin_bearer_requests_metrics() throws Exception {
    var token = signedToken(Map.of(), key.getKeyID(), key, JWSAlgorithm.RS256);
    assertThat(decoder.decode(token).getClaimAsStringList("roles")).containsExactly("ADMIN");
    var before = jdbc.queryForList("select * from stock order by product_id");
    problem(
            get("/actuator/metrics").secure(true).header("Authorization", "Bearer " + token),
            401,
            "unauthorized")
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic ")));
    assertThat(jdbc.queryForList("select * from stock order by product_id")).isEqualTo(before);
  }

  @Test
  @DisplayName("Operador accede con Basic")
  void should_allow_metrics_when_ops_basic_credentials_are_valid() throws Exception {
    mvc.perform(
            get("/actuator/metrics")
                .secure(true)
                .with(httpBasic("identity-test-ops", "test-ops-password")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Basic de operaciones no autentica API")
  void should_challenge_bearer_when_ops_basic_requests_api() throws Exception {
    problem(
            get("/api/orders")
                .secure(true)
                .param("status", "PAID")
                .with(httpBasic("identity-test-ops", "test-ops-password")),
            401,
            "unauthorized")
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    problem(
            get("/actuator/metrics").secure(true).with(httpBasic("identity-test-ops", "wrong")),
            401,
            "unauthorized")
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic ")));
  }
}
