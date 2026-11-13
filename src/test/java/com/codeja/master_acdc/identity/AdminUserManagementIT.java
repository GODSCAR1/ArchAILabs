package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdminUserManagementIT extends AuthHttpSupport {
  @Autowired AccessTokenIssuer issuer;
  @Autowired DataSource dataSource;
  @MockitoSpyBean RedisSessionVersions barrier;

  @ParameterizedTest
  @ValueSource(strings = {"block", "unblock"})
  @DisplayName("Gestión de cuenta exige doble autorización")
  void should_preserve_state_when_actor_is_anonymous_or_has_another_role(String action)
      throws Exception {
    var target = session();
    var before = state();
    problem(post(path(target.user(), action)), 401, "unauthorized")
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    assertThat(state()).isEqualTo(before);
    for (String role : List.of("CUSTOMER", "CATALOG_MANAGER", "SUPPORT")) {
      String token = OrderBearer.issue(jdbc, issuer, null, role);
      before = state();
      problem(
          post(path(target.user(), action)).header("Authorization", "Bearer " + token),
          403,
          "forbidden");
      assertThat(state()).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"block", "unblock"})
  @DisplayName("Cuenta objetivo inexistente o inválida")
  void should_return_not_found_when_authorized_target_is_absent_or_invalid(String action)
      throws Exception {
    String admin = OrderBearer.issue(jdbc, issuer, null, "ADMIN");
    var before = state();
    for (String id : List.of(UUID.randomUUID().toString(), "not-uuid", "1-1-1-1-1")) {
      problem(
          post("/api/admin/users/" + id + "/" + action).header("Authorization", "Bearer " + admin),
          404,
          "user-not-found");
      assertThat(state()).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"block", "unblock"})
  @DisplayName("Cuenta objetivo ocupada no cambia parcialmente")
  void should_return_retryable_problem_when_target_lock_is_held(String action) throws Exception {
    var target = session();
    String admin = OrderBearer.issue(jdbc, issuer, null, "ADMIN");
    decoder.decode(admin);
    var before = state();
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement("select id from user_account where id=? for update")) {
      connection.setAutoCommit(false);
      statement.setObject(1, target.user());
      statement.executeQuery().close();
      problem(
              post(path(target.user(), action)).header("Authorization", "Bearer " + admin),
              503,
              "authentication-busy")
          .andExpect(header().string("Retry-After", "1"));
      connection.rollback();
    }
    assertThat(state()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"before", "lost", "commit"})
  @DisplayName("Fallo compartido durante bloqueo no confirma cambios")
  void should_roll_back_and_keep_conservative_barrier_when_block_fails(String failure)
      throws Exception {
    var target = session();
    String admin = OrderBearer.issue(jdbc, issuer, null, "ADMIN");
    decoder.decode(admin);
    var before = state();
    injectFailure(target.user(), failure);
    problem(
            post(path(target.user(), "block")).header("Authorization", "Bearer " + admin),
            503,
            "session-version-unavailable")
        .andExpect(header().string("Retry-After", "1"));
    assertThat(state()).isEqualTo(before);
    if ("before".equals(failure)) {
      mvc.perform(
              get("/api/orders")
                  .param("status", "PAID")
                  .header("Authorization", "Bearer " + target.access()))
          .andExpect(status().isOk());
    } else {
      problem(
          get("/api/orders")
              .param("status", "PAID")
              .header("Authorization", "Bearer " + target.access()),
          401,
          "unauthorized");
    }
    assertThat(state()).isEqualTo(before);
  }

  private void injectFailure(UUID id, String failure) {
    doAnswer(
            invocation -> {
              if (!"before".equals(failure)) {
                invocation.callRealMethod();
              }
              if ("commit".equals(failure)) {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void beforeCommit(boolean readOnly) {
                        throw new DataAccessResourceFailureException(
                            "Injected commit availability failure");
                      }
                    });
                return null;
              }
              throw new IdentityException("session-version-unavailable");
            })
        .when(barrier)
        .publish(eq(id), any());
  }

  private String path(UUID id, String action) {
    return "/api/admin/users/" + id + "/" + action;
  }

  private List<List<Map<String, Object>>> state() {
    return List.of(
        jdbc.queryForList(
            "select id,email,password_hash,roles::text as roles,enabled,session_version,customer_id"
                + " from user_account order by id"),
        jdbc.queryForList("select * from refresh_token order by id"),
        jdbc.queryForList("select * from orders order by id"),
        jdbc.queryForList("select * from order_lines order by order_id,product_id"),
        jdbc.queryForList("select * from stock order by product_id"));
  }
}
