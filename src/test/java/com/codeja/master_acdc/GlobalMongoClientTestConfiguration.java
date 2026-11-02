package com.codeja.master_acdc;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class GlobalMongoClientTestConfiguration {

  @Bean
  MongoClientSettingsBuilderCustomizer standard_uuid_representation() {
    return settings -> settings.uuidRepresentation(UuidRepresentation.STANDARD);
  }
}
