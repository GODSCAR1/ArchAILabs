package com.codeja.master_acdc.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class CustomerNameMigrationIT extends IntegrationTest {
  @Autowired private PostgreSQLContainer<?> postgres;
  @Autowired private JdbcTemplate admin;
  private JdbcTemplate jdbc;

  @BeforeEach
  void create_database() {
    admin.execute("DROP DATABASE IF EXISTS customer_name_migration WITH (FORCE)");
    admin.execute("CREATE DATABASE customer_name_migration");
    jdbc = new JdbcTemplate(dataSource());
    migrate("10");
  }

  @AfterEach
  void drop_database() {
    admin.execute("DROP DATABASE IF EXISTS customer_name_migration WITH (FORCE)");
  }

  @Test
  @DisplayName("Nombre histórico de una palabra")
  void should_preserve_original_and_leave_last_name_null_when_name_has_one_word() {
    assertMigration("  Ana  ", "Ana", null);
  }

  @Test
  @DisplayName("Nombre histórico con varias palabras")
  void should_preserve_remaining_words_when_name_has_multiple_words() {
    assertMigration("  María   del Mar  ", "María", "del Mar");
  }

  @Test
  @DisplayName("Caracteres y longitud límite se conservan")
  void should_preserve_non_space_characters_when_name_has_tabs_and_maximum_length() {
    String first = "Ñ\tO'Connor-";
    String last = "a".repeat(120 - first.length() - 1);
    assertMigration(first + " " + last, first, last);
  }

  @Test
  @DisplayName("Nombre histórico vacío detiene la migración")
  void should_rollback_migration_when_historical_names_are_empty() {
    UUID customer = UUID.randomUUID();
    UUID order = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    jdbc.update(
        "insert into customers (id, name) values (?, ?), (?, ?)",
        customer,
        "",
        UUID.randomUUID(),
        "  ");
    jdbc.update(
        "insert into orders (id, customer_id, status) values (?, ?, ?)",
        order,
        customer,
        "PENDING");
    jdbc.update("insert into stock (product_id, available) values (?, ?)", product, 7);

    assertThatThrownBy(() -> migrate("11"))
        .isInstanceOf(FlywayException.class)
        .hasStackTraceContaining("Customer names requiring human correction: 2");
    assertMigrationRolledBack();
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, order))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id = ?", Integer.class, product))
        .isEqualTo(7);
  }

  private void assertMigrationRolledBack() {
    assertThat(
            jdbc.queryForObject(
                "select version from flyway_schema_history where success order by installed_rank"
                    + " desc limit 1",
                String.class))
        .isEqualTo("10");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '11'", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'customers' and"
                    + " column_name in ('first_name', 'last_name')",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from customers where name in ('', '  ')", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Reintento tras corrección")
  void should_migrate_corrected_name_when_retried_after_failure() {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into customers (id, name) values (?, ?)", id, "");
    assertThatThrownBy(() -> migrate("11")).isInstanceOf(FlywayException.class);
    jdbc.update("update customers set name = ? where id = ?", "Ana María", id);
    migrate("11");
    assertThat(
            jdbc.queryForMap("select name, first_name, last_name from customers where id = ?", id))
        .containsEntry("name", "Ana María")
        .containsEntry("first_name", "Ana")
        .containsEntry("last_name", "María");
  }

  @Test
  @DisplayName("Comprobaciones de clientes coherentes")
  void should_find_no_inconsistencies_when_customers_are_migrated() throws IOException {
    jdbc.update(
        "insert into customers (id, name) values (?, ?), (?, ?)",
        UUID.randomUUID(),
        "  Ana  ",
        UUID.randomUUID(),
        " María   del Mar ");
    migrate("11");
    for (String check : checks()) {
      assertThat(jdbc.queryForList(checkSql(check), UUID.class)).isEmpty();
    }
  }

  @Test
  @DisplayName("Comprobaciones detectan infracciones")
  void should_find_each_inconsistency_when_columns_are_corrupted() throws IOException {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into customers (id, name) values (?, ?)", id, "Ana María");
    migrate("11");
    assertCheckDetects(id, "customer_first_name", null, "María");
    assertCheckDetects(id, "customer_first_name", "", "María");
    assertCheckDetects(id, "customer_name_reconstruction", "Ana", null);
    assertCheckDetects(id, "customer_name_trim", " Ana", "María");
    assertCheckDetects(id, "customer_name_trim", "Ana", "María ");
    assertCheckDetects(id, "customer_last_name", "Ana", null);
    jdbc.update(
        "update customers set name = ?, first_name = ?, last_name = ? where id = ?",
        "Ana",
        "Ana",
        "María",
        id);
    assertThat(jdbc.queryForList(checkSql("customer_last_name"), UUID.class)).contains(id);
  }

  private void assertCheckDetects(UUID id, String check, String first, String last)
      throws IOException {
    jdbc.update("update customers set first_name = ?, last_name = ? where id = ?", first, last, id);
    assertThat(jdbc.queryForList(checkSql(check), UUID.class)).contains(id);
  }

  private String[] checks() {
    return new String[] {
      "customer_first_name",
      "customer_name_reconstruction",
      "customer_name_trim",
      "customer_last_name"
    };
  }

  private String checkSql(String name) throws IOException {
    return new ClassPathResource("db/checks/" + name + ".sql")
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private void assertMigration(String original, String first, String last) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into customers (id, name) values (?, ?)", id, original);
    migrate("11");
    var row =
        jdbc.queryForMap("select name, first_name, last_name from customers where id = ?", id);
    assertThat(row).containsEntry("name", original).containsEntry("first_name", first);
    assertThat(row).containsEntry("last_name", last);
  }

  private DataSource dataSource() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(
        "jdbc:postgresql://"
            + postgres.getHost()
            + ":"
            + postgres.getMappedPort(5432)
            + "/customer_name_migration");
    source.setUsername(postgres.getUsername());
    source.setPassword(postgres.getPassword());
    return source;
  }

  private void migrate(String target) {
    Flyway.configure()
        .dataSource(jdbc.getDataSource())
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }
}
