package com.codeja.master_acdc.exports.infrastructure.adapters.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalExportFilesTest {
  @TempDir Path base;

  @Test
  void should_return_bytes_when_export_exists() throws Exception {
    byte[] expected = {1, 2, 3};
    var file = base.resolve("pedidos.csv");
    Files.write(file, expected);
    assertThat(new LocalExportFiles(base).read("pedidos.csv").bytes()).containsExactly(expected);
    assertThat(Files.readAllBytes(file)).containsExactly(expected);
  }
}
