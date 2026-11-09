package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.codeja.master_acdc.identity.AdminUsersQueryCountTest$SqlCapture"
    })
public class AdminUsersQueryCountTest extends AdminUsersSupport {
  @Autowired EntityManager entityManager;
  @Autowired EntityManagerFactory factory;

  @ParameterizedTest
  @CsvSource({"0,20,20,6", "5,20,20,6", "6,20,0,6", "1,100,20,2", "2147483647,100,0,2"})
  void should_use_at_most_two_minimal_selects_when_listing_120_accounts(
      int page, int size, int count, int totalPages) throws Exception {
    String token = bearer("ADMIN");
    entityManager.flush();
    entityManager.clear();
    var statistics = factory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    SqlCapture.SQL.get().clear();
    mvc.perform(
            get("/api/admin/users")
                .param("page", Integer.toString(page))
                .param("size", Integer.toString(size))
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(count))
        .andExpect(jsonPath("$.page").value(page))
        .andExpect(jsonPath("$.size").value(size))
        .andExpect(jsonPath("$.totalElements").value(120))
        .andExpect(jsonPath("$.totalPages").value(totalPages));
    assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
    assertThat(statistics.getEntityLoadCount()).isZero();
    assertThat(SqlCapture.SQL.get())
        .hasSize(count == 0 ? 1 : 2)
        .allSatisfy(
            sql ->
                assertThat(sql)
                    .doesNotContain(
                        "password_hash", "session_version", "customer_id", "refresh_token"));
    if (count > 0) {
      assertThat(SqlCapture.SQL.get().get(1))
          .containsPattern(
              "select \\w+\\.id,\\w+\\.email,\\w+\\.roles,\\w+\\.enabled from user_account")
          .containsPattern("order by \\w+\\.id");
    }
  }

  public static class SqlCapture implements StatementInspector {
    static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public String inspect(String sql) {
      SQL.get().add(sql);
      return sql;
    }
  }
}
