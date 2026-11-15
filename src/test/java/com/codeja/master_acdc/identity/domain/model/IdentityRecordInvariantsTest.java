package com.codeja.master_acdc.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IdentityRecordInvariantsTest {
  private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRecords")
  @DisplayName("Los records rechazan valores que infringen sus invariantes")
  void should_reject_record_when_invariant_is_violated(
      String description, Runnable constructor, Class<? extends Throwable> type, String message) {
    assertThatThrownBy(constructor::run).isInstanceOf(type).hasMessage(message);
  }

  private static Stream<Arguments> invalidRecords() {
    return Stream.concat(invalidAccountsAndRefresh(), invalidCredentials());
  }

  private static Stream<Arguments> invalidAccountsAndRefresh() {
    return Stream.of(
        Arguments.of(
            "account email",
            (Runnable)
                () ->
                    new UserAccount(
                        UUID.randomUUID(),
                        "UPPER@example.com",
                        "hash",
                        new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, null)),
            IllegalArgumentException.class,
            "Invalid account"),
        Arguments.of(
            "refresh hash",
            (Runnable)
                () ->
                    new RefreshToken(
                        UUID.randomUUID(),
                        new RefreshToken.Family(UUID.randomUUID(), UUID.randomUUID()),
                        "bad",
                        new RefreshToken.Lifetime(TIME, TIME.plusSeconds(1), TIME.plusSeconds(1)),
                        new RefreshToken.State(null, null)),
            IllegalArgumentException.class,
            "Invalid refresh hash"),
        Arguments.of(
            "family expiry",
            (Runnable)
                () -> new RefreshToken.Lifetime(TIME, TIME.plusSeconds(2), TIME.plusSeconds(1)),
            IllegalArgumentException.class,
            "Invalid refresh lifetime"));
  }

  private static Stream<Arguments> invalidCredentials() {
    return Stream.of(
        Arguments.of(
            "equal creation and expiry",
            (Runnable) () -> new RefreshToken.Lifetime(TIME, TIME, TIME),
            IllegalArgumentException.class,
            "Invalid refresh lifetime"),
        Arguments.of(
            "refresh secret",
            (Runnable) () -> new RefreshSecret("value", "bad"),
            IllegalArgumentException.class,
            "Invalid token hash"),
        Arguments.of(
            "access lifetime",
            (Runnable) () -> new AccessToken("value", 0),
            IllegalArgumentException.class,
            "Invalid token lifetime"),
        Arguments.of(
            "reset hash",
            (Runnable) () -> new PasswordResetToken(UUID.randomUUID(), " ", TIME),
            IllegalArgumentException.class,
            "Invalid token hash"),
        Arguments.of(
            "registration",
            (Runnable) () -> new Registration("bad", "password", null),
            IdentityException.class,
            "invalid-registration"),
        Arguments.of(
            "reset confirmation",
            (Runnable) () -> new PasswordResetConfirmation("user@example.com", " ", "password"),
            IdentityException.class,
            "invalid-password-reset-request"));
  }
}
