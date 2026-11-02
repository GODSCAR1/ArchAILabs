package com.codeja.master_acdc;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class GlobalMongoTestContainerExtension implements BeforeAllCallback {

  private static final MongoDBContainer MONGO =
      new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Override
  public void beforeAll(ExtensionContext context) {
    start_mongo_container();
  }

  private static synchronized void start_mongo_container() {
    if (!MONGO.isRunning()) {
      MONGO.start();
    }
    System.setProperty(
        "spring.mongodb.uri", MONGO.getReplicaSetUrl() + "&uuidRepresentation=standard");
    System.setProperty("spring.mongodb.uuid-representation", "standard");
    System.setProperty("spring.data.mongodb.uuid-representation", "standard");
  }
}
