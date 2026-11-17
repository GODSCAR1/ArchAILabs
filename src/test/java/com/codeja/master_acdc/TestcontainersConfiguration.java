package com.codeja.master_acdc;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:17-alpine")
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.postgres")))
        .withReuse(true);
  }

  @Bean
  @ServiceConnection
  MongoDBContainer mongoContainer() {
    return new MongoDBContainer(DockerImageName.parse("mongo:8"))
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.mongo")))
        .withReuse(true);
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redis() {
    return new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
        .withExposedPorts(6379)
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.redis")))
        .withReuse(true);
  }

  @Bean
  GenericContainer<?> wiremock() {
    return new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.1"))
        .withExposedPorts(8080)
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("wiremock/mappings"), "/home/wiremock/mappings")
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.wiremock")))
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
