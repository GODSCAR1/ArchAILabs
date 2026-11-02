package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogMigrationCheckpoint;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProductMigrationService;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogReview;
import com.codeja.master_acdc.catalog.infrastructure.config.CatalogMongoTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(CatalogMongoTestConfiguration.class)
@TestPropertySource(properties = "catalog.store=mongo")
class ProductReviewMongoIT extends ProductReviewQueryIT {

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Autowired MongoTemplate mongoTemplate;
  @Autowired CatalogProductMigrationService migrationService;

  @Override
  protected void prepareReadStore() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    mongoTemplate.remove(new Query(), CatalogReview.class);
    mongoTemplate.remove(new Query(), CatalogMigrationCheckpoint.class);
  }

  @Override
  protected void afterWrite() {
    migrationService.migrateAll(100);
  }
}
