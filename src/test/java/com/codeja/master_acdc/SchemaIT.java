package com.codeja.master_acdc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;

class SchemaIT extends IntegrationTest {
  @Autowired private PostgreSQLContainer<?> postgres;
  @Autowired private JdbcTemplate admin;

  @BeforeEach
  void create_database() {
    admin.execute("DROP DATABASE IF EXISTS schema_validation_test WITH (FORCE)");
    admin.execute("CREATE DATABASE schema_validation_test");
  }

  @AfterEach
  void drop_database() {
    admin.execute("DROP DATABASE IF EXISTS schema_validation_test WITH (FORCE)");
  }

  @Test
  @DisplayName("Esquema nuevo coherente")
  void should_validate_all_entities_when_schema_is_migrated_from_scratch() {
    DataSource source = dataSource();
    Flyway flyway =
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
    flyway.migrate();
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(flyway.info().all())
        .allSatisfy(info -> assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("11");
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(source);
    factory.setPackagesToScan("com.codeja.master_acdc");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
    try {
      factory.afterPropertiesSet();
      assertThat(factory.getObject()).isNotNull();
      assertThat(factory.getObject().getMetamodel().getEntities()).isNotEmpty();
    } finally {
      factory.destroy();
    }
  }

  private DataSource dataSource() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(
        "jdbc:postgresql://"
            + postgres.getHost()
            + ":"
            + postgres.getMappedPort(5432)
            + "/schema_validation_test");
    source.setUsername(postgres.getUsername());
    source.setPassword(postgres.getPassword());
    return source;
  }
}
