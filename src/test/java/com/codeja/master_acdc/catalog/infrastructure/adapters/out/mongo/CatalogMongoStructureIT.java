package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.IntegrationTest;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class CatalogMongoStructureIT extends IntegrationTest {

  @Autowired private MongoClient client;
  private MongoTemplate template;

  @BeforeEach
  void reset_database() {
    template = new MongoTemplate(client, "catalog_structure_test");
    template.getDb().drop();
  }

  @AfterEach
  void drop_isolated_database() {
    template.getDb().drop();
  }

  @Test
  @DisplayName("Arranque con estructura válida")
  void should_create_validator_and_required_indexes_when_initialized() {
    new CatalogSchemaValidator(template).initialize();
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
    new CatalogSchemaValidator(template).initialize();
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
