package com.codeja.master_acdc.exports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.exports.domain.model.ExportContent;
import com.codeja.master_acdc.exports.domain.port.ExportFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DownloadExportServiceTest {
  @Test
  @DisplayName("Administrador descarga un export existente")
  void should_delegate_when_downloading_export() {
    var files = mock(ExportFiles.class);
    var content = new ExportContent(new byte[] {1, 2});
    when(files.read("pedidos.csv")).thenReturn(content);
    assertThat(new DownloadExportService(files).download("pedidos.csv")).isEqualTo(content);
    verify(files).read("pedidos.csv");
    byte[] copy = content.bytes();
    copy[0] = 0;
    assertThat(content.bytes()).containsExactly(1, 2);
  }
}
