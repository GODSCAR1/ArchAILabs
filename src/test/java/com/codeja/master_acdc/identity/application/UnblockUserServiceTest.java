package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnblockUserServiceTest {
  private final UserAccounts accounts = mock(UserAccounts.class);
  private final UnblockUserService service = new UnblockUserService(accounts);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void should_preserve_version_and_credentials_when_enabling_any_account(boolean enabled) {
    UUID id = UUID.randomUUID();
    var profile = new UserAccount.Profile(Set.of("CUSTOMER"), enabled, 9, UUID.randomUUID());
    when(accounts.findForUpdate(id))
        .thenReturn(Optional.of(new UserAccount(id, "unit@example.com", "hash", profile)));
    service.unblock(id);
    verify(accounts)
        .save(
            new UserAccount(
                id,
                "unit@example.com",
                "hash",
                new UserAccount.Profile(profile.roles(), true, 9, profile.customerId())));
  }

  @Test
  void should_report_not_found_when_account_does_not_exist() {
    assertThatThrownBy(() -> service.unblock(UUID.randomUUID())).hasMessage("user-not-found");
    org.mockito.Mockito.verify(accounts, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
  }
}
