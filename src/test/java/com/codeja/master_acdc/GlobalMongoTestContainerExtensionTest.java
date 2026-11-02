package com.codeja.master_acdc;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GlobalMongoTestContainerExtensionTest {

  @Test
  @DisplayName("El contenedor Mongo global está disponible para los tests")
  void should_publish_live_mongodb_uri_when_global_extension_runs() {
    String uri = System.getProperty("spring.mongodb.uri");
    assertThat(uri).isNotBlank();

    try (MongoClient client = MongoClients.create(uri)) {
      assertThat(client.getDatabase("admin").runCommand(new Document("ping", 1)).getDouble("ok"))
          .isEqualTo(1.0);
    }
  }
}
