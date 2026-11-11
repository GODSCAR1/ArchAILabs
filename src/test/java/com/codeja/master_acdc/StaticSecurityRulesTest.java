package com.codeja.master_acdc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lab S13: trazabilidad de las reglas Semgrep del proyecto. La ejecución real de cada regla contra
 * su fixture la hacen {@code semgrep --test} (verify --full, job security-layer-2); aquí se
 * comprueba la estructura que esa ejecución necesita, sin depender de que Semgrep esté instalado.
 */
class StaticSecurityRulesTest {
  private static final Path RULES = Path.of("semgrep-rules");
  private static final List<String> PROJECT_RULES =
      List.of(
          "shop-order-sort-injection",
          "shop-no-weak-password-reset-random",
          "shop-export-path-traversal",
          "shop-sql-concatenation",
          "shop-sql-injection-taint",
          "shop-no-customer-id-param",
          "shop-no-log-tokens");

  @Test
  @DisplayName("Fixtures detectan cada patrón y aceptan su remediación")
  void should_pair_each_rule_with_positive_and_negative_cases_when_rules_are_listed()
      throws IOException {
    for (String id : PROJECT_RULES) {
      Path rule = RULES.resolve(id + ".yaml");
      Path fixture = RULES.resolve(id + ".java");
      assertThat(rule).as("regla %s", id).isRegularFile();
      assertThat(fixture).as("fixture de %s", id).isRegularFile();
      assertThat(Files.readString(rule, StandardCharsets.UTF_8)).contains("id: " + id);
      assertThat(Files.readString(fixture, StandardCharsets.UTF_8))
          .as("el fixture de %s necesita un caso vulnerable y uno remediado", id)
          .contains("// ruleid: " + id)
          .contains("// ok: " + id);
    }
  }

  @Test
  @DisplayName("Exclusión de fixtures conserva tests y auditoría limpia")
  void should_exclude_fixtures_and_keep_default_ignores_when_semgrepignore_is_read()
      throws IOException {
    List<String> ignored =
        Files.readAllLines(Path.of(".semgrepignore"), StandardCharsets.UTF_8).stream()
            .map(String::strip)
            .toList();
    assertThat(ignored)
        .as("los fixtures son vulnerables a propósito: fuera de la auditoría del proyecto")
        .contains("semgrep-rules/");
    assertThat(ignored)
        .as("la lista por defecto de Semgrep se conserva para que la auditoría sea comparable")
        .contains("test/", "tests/", "build/", "node_modules/");
    try (var files = Files.list(RULES)) {
      assertThat(files.filter(file -> file.toString().endsWith(".yaml")))
          .as("cada regla del proyecto, y solo ellas")
          .hasSize(PROJECT_RULES.size());
    }
  }
}
