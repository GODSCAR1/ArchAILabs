package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.List;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CatalogMongoStructureIT {

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static MongoClient client;
  private static MongoTemplate template;

  @BeforeAll
  static void initialize_mongo_template() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
    template = new MongoTemplate(client, "catalog_structure_test");
  }

  @AfterAll
  static void close_client() {
    client.close();
  }

  @BeforeEach
  void reset_database() {
    template.getDb().drop();
  }

  @Test
  @DisplayName("Arranque con estructura válida")
  void should_create_validator_and_required_indexes_when_initialized() {
    new CatalogIndexInitializer(template).initialize();

    Document collection =
        template.getDb().listCollections().filter(new Document("name", "catalog_products")).first();
    assertThat(collection).isNotNull();
    Document validator =
        collection.getEmbedded(List.of("options", "validator", "$jsonSchema"), Document.class);
    assertThat(validator.getList("required", String.class))
        .contains("sku", "name", "price", "status", "category", "variants", "attributes");
    assertThat(template.getCollection("catalog_products").listIndexes())
        .extracting(index -> index.getString("name"))
        .contains("sku_unique", "category_price");
  }

  @Test
  @DisplayName("Documento inválido rechazado")
  void should_reject_document_without_price_when_schema_validator_is_active() {
    new CatalogIndexInitializer(template).initialize();

    assertThatThrownBy(
            () ->
                template
                    .getCollection("catalog_products")
                    .insertOne(new Document("sku", "SKU-INVALID")))
        .isInstanceOf(MongoWriteException.class);
    assertThat(template.getCollection("catalog_products").countDocuments()).isZero();
  }

  @Test
  @DisplayName("Índices reproducibles")
  void should_preserve_indexes_when_initializer_runs_repeatedly() {
    CatalogIndexInitializer initializer = new CatalogIndexInitializer(template);

    initializer.initialize();
    List<String> firstIndexes = indexNames();
    initializer.initialize();

    assertThat(indexNames()).containsExactlyElementsOf(firstIndexes);
    assertThat(firstIndexes).containsExactlyInAnyOrder("_id_", "sku_unique", "category_price");
  }

  private List<String> indexNames() {
    return template
        .getCollection("catalog_products")
        .listIndexes()
        .map(index -> index.getString("name"))
        .into(new java.util.ArrayList<>());
  }
}
