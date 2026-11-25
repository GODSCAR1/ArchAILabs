package com.codeja.master_acdc.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cada tabla tiene un contexto dueño y solo él la nombra en SQL (lab S19, ADR 0014 §3). ArchUnit no
 * ve los strings SQL, así que este test lee el código fuente. Limitaciones aceptadas en el ADR
 * 0014: el SQL construido dinámicamente se le escapa, y un nombre de tabla tras
 * FROM/JOIN/UPDATE/INTO en un comentario da un falso positivo.
 */
class TableOwnershipTest {

  static final Path MAIN = Paths.get("src/main/java/com/codeja/master_acdc");
  static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");

  /** Dueño de cada tabla creada por Flyway. Una tabla nueva sin dueño hace fallar el test. */
  static final Map<String, String> OWNER =
      Map.ofEntries(
          Map.entry("stock", "inventory"),
          Map.entry("orders", "ordering"),
          Map.entry("order_lines", "ordering"),
          Map.entry("customers", "ordering"),
          Map.entry("order_confirmation_outbox", "ordering"),
          Map.entry("user_account", "identity"),
          Map.entry("refresh_token", "identity"),
          Map.entry("password_reset_token", "identity"),
          // Catálogo relacional, borrado en V7 (el catálogo vive en MongoDB)
          Map.entry("products", "catalog"),
          Map.entry("categories", "catalog"),
          Map.entry("product_variants", "catalog"),
          Map.entry("product_attributes", "catalog"),
          Map.entry("reviews", "catalog"));

  /**
   * Única excepción (deuda, ADR 0014 §4): CatalogStockProjectionJob lee stock. Se elimina cuando
   * Catálogo reaccione a eventos de Inventario (Sección 39). No añadas entradas sin un ADR.
   */
  static final Set<String> DEBT = Set.of("catalog->stock");

  static final Pattern SQL_TABLE =
      Pattern.compile("(?i)\\b(?:from|join|update|into|table)\\s+([a-z_][a-z0-9_]*)");

  @Test
  @DisplayName("Cada tabla creada por Flyway tiene un contexto dueño")
  void should_have_an_owner_context_when_a_migration_creates_a_table() throws IOException {
    Set<String> created = new TreeSet<>();
    Pattern create =
        Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z_][a-z0-9_]*)");
    try (Stream<Path> files = Files.list(MIGRATIONS)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
        Matcher m = create.matcher(Files.readString(file));
        while (m.find()) {
          created.add(m.group(1).toLowerCase());
        }
      }
    }
    assertThat(OWNER.keySet())
        .as(
            "asigna un contexto dueño a cada tabla nueva en TableOwnershipTest.OWNER"
                + " (docs/domain/context-map.md)")
        .containsAll(created);
  }

  @Test
  @DisplayName("Ningún contexto nombra en SQL una tabla de otro contexto")
  void should_not_name_a_foreign_table_when_a_context_writes_sql() throws IOException {
    List<String> violations = new ArrayList<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String module = MAIN.relativize(file).getName(0).toString();
        Matcher m = SQL_TABLE.matcher(Files.readString(file));
        while (m.find()) {
          String table = m.group(1).toLowerCase();
          String owner = OWNER.get(table);
          if (owner != null && !owner.equals(module) && !DEBT.contains(module + "->" + table)) {
            violations.add(
                MAIN.relativize(file)
                    + ": "
                    + module
                    + " nombra la tabla "
                    + table
                    + " de "
                    + owner);
          }
        }
      }
    }
    assertThat(violations)
        .as(
            "un contexto no lee ni escribe tablas de otro: pide los datos por su puerto publicado"
                + " o reacciona a sus eventos (ADR 0014 §3, docs/domain/context-map.md)")
        .isEmpty();
  }

  @Test
  @DisplayName("La deuda catalog->stock sigue existiendo mientras esté en DEBT")
  void should_still_read_stock_when_the_debt_is_listed() throws IOException {
    // Si este test falla, la deuda ya se pagó: borra la entrada de DEBT y la fila 6b del mapa.
    String job =
        Files.readString(
            MAIN.resolve(
                "catalog/infrastructure/adapters/out/mongo/CatalogStockProjectionJob.java"));
    assertThat(SQL_TABLE.matcher(job).results().map(r -> r.group(1).toLowerCase()))
        .contains("stock");
  }
}
