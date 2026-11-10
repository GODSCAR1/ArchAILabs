package com.codeja.master_acdc.exports.infrastructure.adapters.in.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.exports.application.port.DownloadExportUseCase;
import com.codeja.master_acdc.exports.domain.model.ExportContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminExportControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private DownloadExportUseCase exports;

  @Test
  @DisplayName("Administrador descarga un export existente")
  void should_return_export_when_name_is_supplied() throws Exception {
    when(exports.download("pedidos.csv")).thenReturn(new ExportContent(new byte[] {1, 2}));
    mvc.perform(get("/api/admin/exports").param("name", "pedidos.csv"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(new byte[] {1, 2}));
    verify(exports).download("pedidos.csv");
  }
}
