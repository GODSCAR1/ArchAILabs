package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserSummaryPersistenceMapperTest {
  @Test
  void should_copy_only_public_fields_when_mapping_projection() {
    UUID id = UUID.randomUUID();
    String[] roles = {"CUSTOMER", "SUPPORT", "SUPPORT"};
    var result =
        UserSummaryPersistenceMapper.toSummary(
            new UserSummaryRow(id, "fixture@example.com", roles, false));
    roles[0] = "ADMIN";
    assertThat(result.id()).isEqualTo(id);
    assertThat(result.email()).isEqualTo("fixture@example.com");
    assertThat(result.roles()).containsExactlyInAnyOrder("CUSTOMER", "SUPPORT");
    assertThat(result.enabled()).isFalse();
    assertThatThrownBy(() -> result.roles().add("ADMIN"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
