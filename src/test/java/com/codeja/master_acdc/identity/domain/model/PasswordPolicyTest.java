package com.codeja.master_acdc.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.CommonPasswordsAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
  private final PasswordPolicy policy = new PasswordPolicy(new CommonPasswordsAdapter());

  @Test
  @DisplayName("Contraseña corta o común")
  void should_reject_password_when_short_or_common() {
    for (String password : new String[] {"short", "password1234", "PASSWORD1234"}) {
      assertThatThrownBy(() -> policy.validate(password))
          .isInstanceOf(IdentityException.class)
          .hasMessage("invalid-password");
    }
    assertThatCode(() -> policy.validate("Unique!12345X")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Contraseña no representable sin truncamiento")
  void should_enforce_byte_boundary_when_password_is_multibyte() {
    assertThatCode(() -> policy.validate("é".repeat(36))).doesNotThrowAnyException();
    assertThatThrownBy(() -> policy.validate("é".repeat(37))).hasMessage("invalid-password");
    assertThatThrownBy(() -> policy.validate("x".repeat(73))).hasMessage("invalid-password");
    assertThatThrownBy(() -> policy.validate(null)).hasMessage("invalid-password");
  }

  @Test
  @DisplayName("Datos de registro inválidos")
  void should_reject_registration_when_email_or_name_is_invalid() {
    for (String email : new String[] {"invalid", "a@", "a b@example.com", "a@example..com"}) {
      assertThatThrownBy(() -> new Registration(email, "Unique!12345X", null))
          .hasMessage("invalid-registration");
    }
    for (String name : new String[] {"", " ", "x".repeat(121)}) {
      assertThatThrownBy(() -> new Registration("a@example.com", "Unique!12345X", name))
          .hasMessage("invalid-registration");
    }
    assertThat(new Registration("Cliente@Example.com", "Unique!12345X", null).name())
        .isEqualTo("cliente");
  }
}
