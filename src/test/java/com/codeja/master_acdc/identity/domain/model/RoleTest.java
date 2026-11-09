package com.codeja.master_acdc.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RoleTest {
  @ParameterizedTest
  @MethodSource("permissionTable")
  @DisplayName("Permisos explícitos por rol")
  void should_expose_only_immutable_permissions_when_role_is_known(
      Role role, Set<String> expected) {
    assertThat(role.permissions()).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(role.permissions()).doesNotContain("unknown:permission");
    assertThatThrownBy(() -> role.permissions().add("unknown:permission"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("ADMIN no puede crear pedidos por su rol")
  void should_not_inherit_customer_permissions_when_role_is_admin() {
    assertThat(Role.ADMIN.permissions()).doesNotContainAnyElementsOf(Role.CUSTOMER.permissions());
  }

  @Test
  @DisplayName("Roles combinados suman permisos sin perder restricciones")
  void should_combine_permissions_without_mutating_roles_when_account_has_multiple_roles() {
    var union = new HashSet<>(Role.CUSTOMER.permissions());
    union.addAll(Role.CATALOG_MANAGER.permissions());
    assertThat(union)
        .containsExactlyInAnyOrder(
            "order:create", "order:read-own", "order:cancel-own", "product:write", "stock:reserve");
    assertThat(Role.CUSTOMER.permissions()).hasSize(3);
    assertThat(Role.CATALOG_MANAGER.permissions()).hasSize(2);
  }

  private static Stream<Arguments> permissionTable() {
    return Stream.of(
        Arguments.of(Role.CUSTOMER, Set.of("order:create", "order:read-own", "order:cancel-own")),
        Arguments.of(Role.CATALOG_MANAGER, Set.of("product:write", "stock:reserve")),
        Arguments.of(Role.SUPPORT, Set.of("order:read-any", "order:refund")),
        Arguments.of(
            Role.ADMIN,
            Set.of(
                "product:write",
                "stock:reserve",
                "order:read-any",
                "order:refund",
                "user:read",
                "user:manage")));
  }
}
