package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

class AdminUsersIT extends AdminUsersSupport {
  @Autowired ObjectMapper json;

  @Test
  @DisplayName("Administrador consulta cuentas sin secretos")
  void should_return_only_allowed_fields_when_admin_reads_default_page() throws Exception {
    var response =
        mvc.perform(get("/api/admin/users").header("Authorization", bearer("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(120))
            .andExpect(jsonPath("$.totalPages").value(6))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var root = json.readTree(response);
    assertThat(root.propertyNames())
        .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
    for (var user : root.get("content")) {
      assertThat(user.propertyNames()).containsExactlyInAnyOrder("id", "email", "roles", "enabled");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CATALOG_MANAGER", "SUPPORT"})
  @DisplayName("Usuario sin rol administrativo no obtiene cuentas")
  void should_deny_accounts_when_role_is_not_admin(String role) throws Exception {
    mvc.perform(get("/api/admin/users").header("Authorization", bearer(role)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("https://api.master-acdc.dev/problems/forbidden"))
        .andExpect(jsonPath("$.content").doesNotExist());
  }

  @Test
  @DisplayName("Consulta administrativa anónima")
  void should_require_authentication_when_bearer_is_absent() throws Exception {
    mvc.perform(get("/api/admin/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://api.master-acdc.dev/problems/unauthorized"));
  }

  @ParameterizedTest
  @CsvSource({"page,-1", "size,0", "size,101", "page,abc", "size,abc", "sort,password_hash"})
  @DisplayName("Paginación administrativa inválida")
  void should_return_invalid_page_when_pagination_or_sort_is_invalid(String key, String value)
      throws Exception {
    mvc.perform(get("/api/admin/users").param(key, value).header("Authorization", bearer("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-user-page"));
    assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class))
        .isEqualTo(120);
  }

  @Test
  @DisplayName("Páginas administrativas estables")
  void should_return_distinct_sorted_accounts_when_reading_all_pages() throws Exception {
    var ids = new java.util.ArrayList<String>();
    for (int page = 0; page < 6; page++) {
      var response =
          mvc.perform(
                  get("/api/admin/users")
                      .param("page", Integer.toString(page))
                      .header("Authorization", bearer("ADMIN")))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.totalElements").value(120))
              .andExpect(jsonPath("$.totalPages").value(6))
              .andReturn()
              .getResponse()
              .getContentAsString();
      json.readTree(response).get("content").forEach(user -> ids.add(user.get("id").asString()));
    }
    assertThat(ids)
        .containsExactlyElementsOf(
            java.util.stream.IntStream.range(0, 120)
                .mapToObj(index -> idFor(index).toString())
                .toList());
    mvc.perform(get("/api/admin/users").param("page", "6").header("Authorization", bearer("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(120));
  }
}
