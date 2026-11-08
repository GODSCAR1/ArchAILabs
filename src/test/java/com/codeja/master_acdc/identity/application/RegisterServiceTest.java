package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.model.Registration;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.CustomerCreator;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegisterServiceTest {
  private final UserAccounts accounts = mock(UserAccounts.class);
  private final CustomerCreator customers = mock(CustomerCreator.class);
  private final Passwords passwords = mock(Passwords.class);
  private final RegisterService service =
      new RegisterService(accounts, customers, passwords, new PasswordPolicy(value -> false));

  @Test
  void should_create_customer_account_when_registration_is_valid() {
    UUID customer = UUID.randomUUID();
    when(customers.create("cliente")).thenReturn(customer);
    when(passwords.encode("Unique!12345X")).thenReturn("{bcrypt}hash");
    UUID id = service.register(new Registration("Cliente@Example.com", "Unique!12345X", null));
    var captor = ArgumentCaptor.forClass(UserAccount.class);
    verify(accounts).save(captor.capture());
    var account = captor.getValue();
    assertThat(account.id()).isEqualTo(id);
    assertThat(account.email()).isEqualTo("cliente@example.com");
    assertThat(account.profile().customerId()).isEqualTo(customer);
    assertThat(account.profile().roles()).containsExactly("CUSTOMER");
    assertThat(account.profile().enabled()).isTrue();
    assertThat(account.profile().sessionVersion()).isZero();
    assertThat(account.toString()).doesNotContain("hash", "cliente");
  }

  @Test
  void should_preserve_storage_when_password_is_invalid() {
    assertThatThrownBy(() -> service.register(new Registration("a@example.com", "short", "Name")))
        .hasMessage("invalid-password");
    verifyNoInteractions(accounts, customers, passwords);
  }
}
