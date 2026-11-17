package com.codeja.master_acdc;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:17-alpine").withReuse(true);
  }

  @Bean
  @ServiceConnection
  MongoDBContainer mongoContainer() {
    return new MongoDBContainer(DockerImageName.parse("mongo:8")).withReuse(true);
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redis() {
    return new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);
  }

  @Bean
  GenericContainer<?> wiremock() {
    return new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.1"))
        .withExposedPorts(8080)
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("wiremock/mappings"), "/home/wiremock/mappings")
        .withReuse(true);
  }

  @Bean
  DynamicPropertyRegistrar paymentUrl(@Qualifier("wiremock") GenericContainer<?> wiremock) {
    return registry ->
        registry.add(
            "shop.payments.base-url",
            () -> "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080));
  }
}
