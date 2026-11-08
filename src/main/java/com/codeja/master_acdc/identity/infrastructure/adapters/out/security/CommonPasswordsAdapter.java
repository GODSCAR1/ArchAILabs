package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.codeja.master_acdc.identity.domain.port.CommonPasswords;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

public final class CommonPasswordsAdapter implements CommonPasswords {
  private final Set<String> passwords;

  public CommonPasswordsAdapter() {
    try (var reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource("common-passwords.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
      passwords =
          reader.lines().filter(line -> !line.isBlank()).collect(Collectors.toUnmodifiableSet());
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load common passwords", exception);
    }
  }

  @Override
  public boolean contains(String password) {
    return passwords.contains(password.toLowerCase(Locale.ROOT));
  }
}
