package com.codeja.master_acdc.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Beans de test comunes a {@link IdentityIntegrationSupport} (lab S14: un solo contexto). */
@TestConfiguration(proxyBeanMethods = false)
public class IdentityTestBeans {
  /**
   * Directorio de exports temporal: los tests escriben y leen exports sin tocar {@code exports/}
   * del repo. Se obtiene con {@code @Autowired Path exportDirectory}.
   */
  @Bean
  @Primary
  Path testExportDirectory() throws IOException {
    Path directory = Files.createTempDirectory("exports-test-");
    directory.toFile().deleteOnExit();
    return directory;
  }
}
