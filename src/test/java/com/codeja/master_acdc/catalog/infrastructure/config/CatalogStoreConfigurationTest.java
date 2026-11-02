package com.codeja.master_acdc.catalog.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogDetailMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogIndexInitializer;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogMigrationJob;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProductMigrationService;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductEditingMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.ProductSearchMongoRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductDetailRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductEditingRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductJpaRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductSearchRepositoryAdapter;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.MongoDatabase;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

class CatalogStoreConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              ProductConfiguration.class,
              CatalogMigrationJobConfiguration.class,
              ConfigurationMocks.class);

  @Test
  @DisplayName("Modo PostgreSQL durante coexistencia")
  void should_select_postgres_when_store_is_unspecified() {
    assertConfiguration(new PropertyCombination(null, false));
  }

  @Test
  @DisplayName("Migración Mongo habilitada en modo PostgreSQL")
  void should_keep_postgres_adapters_when_migration_is_enabled() {
    assertConfiguration(new PropertyCombination("postgres", true));
  }

  @Test
  @DisplayName("Modo Mongo con migración desactivada")
  void should_select_mongo_adapters_when_migration_is_disabled() {
    assertConfiguration(new PropertyCombination("mongo", false));
  }

  @Test
  @DisplayName("Infraestructura Mongo activada por lectura o migración")
  void should_activate_mongo_infrastructure_when_store_is_mongo_or_migration_is_enabled() {
    assertConfiguration(new PropertyCombination("mongo", false));
    assertConfiguration(new PropertyCombination("postgres", true));
  }

  private void assertConfiguration(PropertyCombination properties) {
    contextRunner
        .withPropertyValues(properties.asPropertyValues())
        .run(context -> assertContext(context, properties));
  }

  private void assertContext(
      org.springframework.context.ApplicationContext context, PropertyCombination properties) {
    ProductSearchRepository searchRepository = context.getBean(ProductSearchRepository.class);
    ProductDetailRepository detailRepository = context.getBean(ProductDetailRepository.class);
    boolean mongoSelected = "mongo".equals(properties.store());
    ProductRepository productRepository = context.getBean(ProductRepository.class);
    ProductEditingRepository editingRepository = context.getBean(ProductEditingRepository.class);
    assertThat(productRepository)
        .isInstanceOf(
            mongoSelected ? ProductMongoRepositoryAdapter.class : ProductRepositoryAdapter.class);
    assertThat(editingRepository)
        .isInstanceOf(
            mongoSelected
                ? ProductEditingMongoRepositoryAdapter.class
                : ProductEditingRepositoryAdapter.class);
    assertThat(searchRepository)
        .isInstanceOf(
            mongoSelected
                ? ProductSearchMongoRepositoryAdapter.class
                : ProductSearchRepositoryAdapter.class);
    assertThat(detailRepository)
        .isInstanceOf(
            mongoSelected
                ? CatalogDetailMongoRepositoryAdapter.class
                : ProductDetailRepositoryAdapter.class);
    assertThat(context.getBeansOfType(ProductSearchRepository.class)).hasSize(1);
    assertThat(context.getBeansOfType(ProductDetailRepository.class)).hasSize(1);
    assertThat(context.getBeansOfType(ProductRepository.class)).hasSize(1);
    assertThat(context.getBeansOfType(ProductEditingRepository.class)).hasSize(1);

    boolean mongoInfrastructure = mongoSelected || properties.migrationEnabled();
    assertThat(context.getBeansOfType(CatalogProductMigrationService.class))
        .hasSize(mongoInfrastructure ? 1 : 0);
    assertThat(context.getBeansOfType(CatalogIndexInitializer.class))
        .hasSize(mongoInfrastructure ? 1 : 0);
    assertThat(context.getBeansOfType(CatalogMigrationJob.class))
        .hasSize(mongoInfrastructure ? 1 : 0);
  }

  private record PropertyCombination(String store, boolean migrationEnabled) {

    private String[] asPropertyValues() {
      List<String> values = new ArrayList<>();
      if (store != null) {
        values.add("catalog.store=" + store);
      }
      values.add("catalog.migration.enabled=" + migrationEnabled);
      return values.toArray(String[]::new);
    }
  }

  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  static class ConfigurationMocks {

    @org.springframework.context.annotation.Bean
    ProductJpaRepository productJpaRepository() {
      return mock(ProductJpaRepository.class);
    }

    @org.springframework.context.annotation.Bean
    EntityManager entityManager() {
      return mock(EntityManager.class);
    }

    @org.springframework.context.annotation.Bean
    PlatformTransactionManager transactionManager() {
      return mock(PlatformTransactionManager.class);
    }

    @org.springframework.context.annotation.Bean
    MongoTemplate mongoTemplate() {
      MongoTemplate template = mock(MongoTemplate.class);
      MongoDatabase database = mock(MongoDatabase.class);
      ListCollectionNamesIterable collectionNames = mock(ListCollectionNamesIterable.class);
      IndexOperations indexes = mock(IndexOperations.class);
      when(template.getDb()).thenReturn(database);
      when(database.listCollectionNames()).thenReturn(collectionNames);
      when(collectionNames.into(anyCollection())).thenAnswer(call -> call.getArgument(0));
      when(database.runCommand(any(Document.class))).thenReturn(new Document("ok", 1));
      when(template.indexOps(any(Class.class))).thenReturn(indexes);
      when(indexes.ensureIndex(any(Index.class))).thenReturn("index");
      return template;
    }

    @org.springframework.context.annotation.Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
      return mock(NamedParameterJdbcTemplate.class);
    }

    @org.springframework.context.annotation.Bean
    TaskScheduler taskScheduler() {
      return mock(TaskScheduler.class);
    }
  }
}
