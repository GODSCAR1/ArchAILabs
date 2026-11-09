package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RolesAndPermissionsConverterTest {
  private final RolesAndPermissionsConverter converter = new RolesAndPermissionsConverter();

  @ParameterizedTest
  @MethodSource({"knownRoles", "combinedRoles"})
  @DisplayName("Authorities derivadas exclusivamente de roles conocidos")
  void should_derive_only_role_and_permissions_when_claim_has_known_roles(
      List<String> roles, Set<String> expected) {
    assertThat(converter.convert(token(roles)))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @ParameterizedTest
  @MethodSource("invalidRoles")
  @DisplayName("Rol desconocido no concede acceso")
  void should_grant_no_authorities_when_roles_are_absent_unknown_empty_or_malformed(Object roles) {
    assertThat(converter.convert(token(roles))).isEmpty();
  }

  private static Stream<Arguments> knownRoles() {
    return Stream.of(
        Arguments.of(
            List.of("CUSTOMER"),
            Set.of("ROLE_CUSTOMER", "order:create", "order:read-own", "order:cancel-own")),
        Arguments.of(
            List.of("CATALOG_MANAGER"),
            Set.of("ROLE_CATALOG_MANAGER", "product:write", "stock:reserve")),
        Arguments.of(
            List.of("SUPPORT", "UNKNOWN"),
            Set.of("ROLE_SUPPORT", "order:read-any", "order:refund")),
        Arguments.of(
            List.of("ADMIN"),
            Set.of(
                "ROLE_ADMIN",
                "product:write",
                "stock:reserve",
                "order:read-any",
                "order:refund",
                "user:read",
                "user:manage")));
  }

  private static Stream<Arguments> combinedRoles() {
    return Stream.of(
        Arguments.of(
            List.of("CUSTOMER", "CATALOG_MANAGER", "CUSTOMER"),
            Set.of(
                "ROLE_CUSTOMER",
                "ROLE_CATALOG_MANAGER",
                "order:create",
                "order:read-own",
                "order:cancel-own",
                "product:write",
                "stock:reserve")),
        Arguments.of(
            List.of("ADMIN", "SUPPORT"),
            Set.of(
                "ROLE_ADMIN",
                "ROLE_SUPPORT",
                "product:write",
                "stock:reserve",
                "order:read-any",
                "order:refund",
                "user:read",
                "user:manage")));
  }

  private static Stream<Arguments> invalidRoles() {
    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(List.of()),
        Arguments.of(List.of("UNKNOWN")),
        Arguments.of("ADMIN"),
        Arguments.of(42),
        Arguments.of(Map.of("ADMIN", true)),
        Arguments.of(List.of("admin")),
        Arguments.of(List.of(" ADMIN")),
        Arguments.of(List.of("ADMIN", 42)),
        Arguments.of(Arrays.asList("ADMIN", null)));
  }

  private static Jwt token(Object roles) {
    var builder =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .subject("account")
            .claim("scope", "product:write user:read")
            .claim("scp", List.of("stock:reserve"));
    if (roles != null) {
      builder.claim("roles", roles);
    }
    return builder.build();
  }
}
