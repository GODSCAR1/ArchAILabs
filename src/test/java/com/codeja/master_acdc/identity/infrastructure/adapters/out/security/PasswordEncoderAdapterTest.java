package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderAdapterTest {
  @Test
  @DisplayName("Contraseña con carácter nulo no se codifica ni autentica")
  void should_reject_password_when_it_contains_a_null_character() {
    var adapter =
        new PasswordEncoderAdapter(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    String hash = adapter.encode("valid-password");
    assertThatThrownBy(() -> adapter.encode("valid-password\0"))
        .isInstanceOf(IdentityException.class)
        .hasMessage("invalid-password");
    assertThat(adapter.matches("valid-password\0", hash)).isFalse();
  }

  @Test
  void should_encode_delegated_bcrypt_when_password_is_representable() {
    var adapter =
        new PasswordEncoderAdapter(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    String hash = adapter.encode("é".repeat(36));
    assertThat(hash).startsWith("{bcrypt}");
    assertThat(adapter.matches("é".repeat(36), hash)).isTrue();
    assertThat(adapter.matches("é".repeat(37), hash)).isFalse();
    assertThatThrownBy(() -> adapter.encode("é".repeat(37))).hasMessage("invalid-password");
    assertThatThrownBy(() -> adapter.encode(null)).hasMessage("invalid-password");
  }

  @Test
  void should_generate_dummy_once_when_multiple_unknown_logins_occur() {
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.encode(anyString())).thenReturn("dummy-hash");
    var adapter = new PasswordEncoderAdapter(encoder);
    adapter.matchesDummy("wrong");
    adapter.matchesDummy("wrong-again");
    adapter.matches("wrong", "real-hash");
    verify(encoder, times(1)).encode(anyString());
    verify(encoder, times(2)).matches(anyString(), eq("dummy-hash"));
    verify(encoder, times(1)).matches("wrong", "real-hash");
  }
}
