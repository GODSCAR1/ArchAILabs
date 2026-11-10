package com.codeja.master_acdc.exports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.IdentityIntegrationSupport;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Import(AdminExportDownloadIT.TempExports.class)
class AdminExportDownloadIT extends IdentityIntegrationSupport {
  private static Path directory;
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenIssuer issuer;

  @TestConfiguration
  static class TempExports {
    @Bean
    @Primary
    Path testExportDirectory() throws IOException {
      directory = Files.createTempDirectory("exports-it-");
      return directory;
    }
  }

  @AfterAll
  static void cleanup() throws IOException {
    Files.deleteIfExists(directory.resolve("pedidos.csv"));
    Files.deleteIfExists(directory);
  }

  @Test
  @DisplayName("Administrador descarga un export existente")
  void should_download_export_when_admin_is_authorized() throws Exception {
    byte[] bytes = {1, 2, 3};
    Files.write(directory.resolve("pedidos.csv"), bytes);
    String token = OrderBearer.issue(jdbc, issuer, null, "ADMIN");
    mvc.perform(
            get("/api/admin/exports")
                .param("name", "pedidos.csv")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytes));
  }
}
