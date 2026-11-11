package com.codeja.master_acdc.exports.infrastructure.adapters.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.exports.domain.model.ExportException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalExportFilesTest {
  @TempDir Path base;

  @Test
  void should_confine_paths_when_base_is_relative_and_sibling_shares_prefix() throws Exception {
    Path exports = Files.createDirectory(base.resolve("exports"));
    Path sibling = Files.createDirectory(base.resolve("exports-old"));
    Files.writeString(exports.resolve("valid.csv"), "valid");
    Files.writeString(sibling.resolve("outside.csv"), "outside");
    Path relative = Path.of("").toAbsolutePath().relativize(exports.resolve(".").normalize());
    var files = new LocalExportFiles(relative);
    assertThat(files.read("nested/../valid.csv").bytes())
        .isEqualTo("valid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(() -> files.read("../exports-old/outside.csv"))
        .isInstanceOf(ExportException.class)
        .hasMessage("invalid-export-name");
    assertThatThrownBy(() -> files.read(sibling.resolve("outside.csv").toString()))
        .isInstanceOf(ExportException.class)
        .hasMessage("invalid-export-name");
  }

  @Test
  void should_report_errors_when_path_is_invalid_or_file_is_missing() {
    var files = new LocalExportFiles(base);
    assertThatThrownBy(() -> files.read("bad\0name"))
        .isInstanceOf(ExportException.class)
        .hasMessage("invalid-export-name");
    assertThatThrownBy(() -> files.read("missing.csv"))
        .isInstanceOf(ExportException.class)
        .hasMessage("export-not-found");
  }

  @Test
  void should_return_bytes_when_export_exists() throws Exception {
    byte[] expected = {1, 2, 3};
    var file = base.resolve("pedidos.csv");
    Files.write(file, expected);
    assertThat(new LocalExportFiles(base).read("pedidos.csv").bytes()).containsExactly(expected);
    assertThat(Files.readAllBytes(file)).containsExactly(expected);
  }
}
