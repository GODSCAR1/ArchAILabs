package com.codeja.master_acdc.identity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class IdentityTestContainers {
  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> identityPostgres() {
    return new PostgreSQLContainer<>("postgres:17-alpine");
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> identityRedis() {
    return new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
  }
}
