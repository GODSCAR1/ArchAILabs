package com.codeja.master_acdc.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.IdentityIntegrationSupport;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AdminExportDownloadIT extends IdentityIntegrationSupport {
  private static final String SENTINEL = "synthetic-external-export-sentinel";
  @Autowired private Path exportDirectory;
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenIssuer issuer;

  @Test
  @DisplayName("Export rechaza ascenso al directorio padre")
  void should_reject_escape_when_name_ascends_to_parent() throws Exception {
    assertRejectedEscape("parent");
  }

  @Test
  @DisplayName("Export rechaza escape con separadores del sistema")
  void should_reject_escape_when_name_uses_native_separator() throws Exception {
    assertRejectedEscape("native");
  }

  @Test
  @DisplayName("Export rechaza ruta absoluta externa")
  void should_reject_escape_when_name_is_absolute() throws Exception {
    assertRejectedEscape("absolute");
  }

  private void assertRejectedEscape(String kind) throws Exception {
    Path external = Files.createTempFile(exportDirectory.getParent(), "export-outside-", ".txt");
    Path internal = Files.createTempFile(exportDirectory, "export-inside-", ".txt");
    try {
      Files.writeString(external, SENTINEL);
      Files.writeString(internal, "internal fixture");
      var orders = jdbc.queryForList("select * from orders order by id");
      var lines = jdbc.queryForList("select * from order_lines order by order_id, product_id");
      var stock = jdbc.queryForList("select * from stock order by product_id");
      String name = escapeName(kind, external);
      String token = OrderBearer.issue(jdbc, issuer, null, "ADMIN");
      var response =
          mvc.perform(
                  get("/api/admin/exports")
                      .param("name", name)
                      .header("Authorization", "Bearer " + token))
              .andReturn();
      assertAll(
          () -> assertSecureResponse(response, external),
          () -> assertThat(Files.readString(external)).isEqualTo(SENTINEL),
          () -> assertThat(Files.readString(internal)).isEqualTo("internal fixture"),
          () -> assertThat(jdbc.queryForList("select * from orders order by id")).isEqualTo(orders),
          () ->
              assertThat(
                      jdbc.queryForList("select * from order_lines order by order_id, product_id"))
                  .isEqualTo(lines),
          () ->
              assertThat(jdbc.queryForList("select * from stock order by product_id"))
                  .isEqualTo(stock));
    } finally {
      Files.deleteIfExists(external);
      Files.deleteIfExists(internal);
    }
  }

  private static String escapeName(String kind, Path external) {
    return switch (kind) {
      case "parent" -> "../" + external.getFileName();
      case "native" -> ".." + File.separator + external.getFileName();
      case "absolute" -> external.toAbsolutePath().toString();
      default -> throw new IllegalArgumentException("unknown scenario");
    };
  }

  private void assertSecureResponse(MvcResult response, Path external) {
    assertAll(
        () -> status().isBadRequest().match(response),
        () ->
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(response),
        () ->
            jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/invalid-export-name")
                .match(response),
        () ->
            assertThat(response.getResponse().getContentAsString())
                .doesNotContain(SENTINEL, external.toString(), exportDirectory.toString()));
  }

  @Test
  @DisplayName("Administrador descarga un export existente")
  void should_download_export_when_admin_is_authorized() throws Exception {
    byte[] bytes = {1, 2, 3};
    Files.write(exportDirectory.resolve("pedidos.csv"), bytes);
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
