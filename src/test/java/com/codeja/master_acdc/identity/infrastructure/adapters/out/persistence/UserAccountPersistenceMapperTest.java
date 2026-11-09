package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountPersistenceMapperTest {
  @Test
  void should_preserve_account_when_mapping_both_directions() {
    var account =
        new UserAccount(
            UUID.randomUUID(),
            "user@example.com",
            "{bcrypt}hash",
            new UserAccount.Profile(Set.of("CUSTOMER", "ADMIN"), false, 3, UUID.randomUUID()));
    var entity = UserAccountPersistenceMapper.toEntity(account);
    assertThat(UserAccountPersistenceMapper.toDomain(entity)).isEqualTo(account);
    entity.roles[0] = "OTHER";
    assertThat(account.profile().roles()).containsExactlyInAnyOrder("CUSTOMER", "ADMIN");
  }

  @Test
  void should_preserve_nullable_customer_when_account_is_not_customer() {
    var account =
        new UserAccount(
            UUID.randomUUID(),
            "admin@example.com",
            "hash",
            new UserAccount.Profile(Set.of("ADMIN"), true, 0, null));
    assertThat(
            UserAccountPersistenceMapper.toDomain(UserAccountPersistenceMapper.toEntity(account)))
        .isEqualTo(account);
  }
}
