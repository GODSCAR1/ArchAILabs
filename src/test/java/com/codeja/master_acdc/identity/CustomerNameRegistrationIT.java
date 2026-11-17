package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CustomerNameRegistrationIT extends AuthHttpSupport {
  @Test
  @DisplayName("Registro con nombre y espacios")
  void should_preserve_name_and_components_when_registration_supplies_spaces() throws Exception {
    mvc.perform(registration("ana@example.com", PASSWORD, "  Ana   María  "))
        .andExpect(status().isCreated());
    assertCustomer("  Ana   María  ", "Ana", "María");
  }

  @Test
  @DisplayName("Registro sin nombre explícito conserva el valor derivado")
  void should_derive_name_when_registration_omits_name() throws Exception {
    mvc.perform(registration("Cliente@Example.com", PASSWORD, null))
        .andExpect(status().isCreated());
    assertCustomer("cliente", "cliente", null);
  }

  private void assertCustomer(String name, String first, String last) throws Exception {
    assertThat(jdbc.queryForMap("select name, first_name, last_name from customers"))
        .containsEntry("name", name)
        .containsEntry("first_name", first)
        .containsEntry("last_name", last);
    for (String check :
        new String[] {
          "customer_first_name",
          "customer_name_reconstruction",
          "customer_name_trim",
          "customer_last_name"
        }) {
      String sql =
          new ClassPathResource("db/checks/" + check + ".sql")
              .getContentAsString(StandardCharsets.UTF_8);
      assertThat(jdbc.queryForList(sql)).isEmpty();
    }
  }
}
