package com.codeja.master_acdc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
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
  // Lab S16 (paso 9): con withReuse(true), las suites de procesos distintos (el Verify del
  // plugin al terminar el turno, el del agente en segundo plano, otro worktree) comparten estos
  // contenedores y la limpieza de una borra los datos de la otra. @ResourceLock solo ordena dentro
  // de una JVM: cada JVM toma este bloqueo del sistema antes de crear los contenedores y lo
  // mantiene hasta salir, así una segunda suite espera en lugar de pisar a la primera. Si el
  // proceso muere, el sistema operativo libera el bloqueo.
  static final Path REUSED_CONTAINERS_LOCK_FILE =
      Path.of(System.getProperty("java.io.tmpdir"), "master-acdc-testcontainers.lock");
  private static final Logger LOG = LoggerFactory.getLogger(TestcontainersConfiguration.class);
  private static final FileChannel LOCK_CHANNEL = openLockChannel();
  static final FileLock REUSED_CONTAINERS_LOCK = lockReusedContainers(LOCK_CHANNEL);

  private static FileChannel openLockChannel() {
    try {
      return FileChannel.open(
          REUSED_CONTAINERS_LOCK_FILE, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new UncheckedIOException("No pude abrir " + REUSED_CONTAINERS_LOCK_FILE, e);
    }
  }

  private static FileLock lockReusedContainers(FileChannel channel) {
    try {
      FileLock lock = channel.tryLock();
      if (lock != null) {
        return lock;
      }
      LOG.warn(
          "Otra suite de integración usa los contenedores reutilizados; espero a que termine ({})",
          REUSED_CONTAINERS_LOCK_FILE);
      return channel.lock();
    } catch (IOException e) {
      throw new UncheckedIOException("No pude bloquear " + REUSED_CONTAINERS_LOCK_FILE, e);
    }
  }

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
