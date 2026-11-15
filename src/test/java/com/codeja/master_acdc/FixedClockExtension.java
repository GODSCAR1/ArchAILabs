package com.codeja.master_acdc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public final class FixedClockExtension implements ParameterResolver {
  private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

  @Override
  public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
    return parameter.getParameter().getType() == Clock.class;
  }

  @Override
  public Clock resolveParameter(ParameterContext parameter, ExtensionContext context) {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}
