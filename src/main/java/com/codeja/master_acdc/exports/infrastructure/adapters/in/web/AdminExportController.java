package com.codeja.master_acdc.exports.infrastructure.adapters.in.web;

import com.codeja.master_acdc.exports.application.port.DownloadExportUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminExportController {
  private final DownloadExportUseCase exports;

  public AdminExportController(DownloadExportUseCase exports) {
    this.exports = exports;
  }

  @GetMapping("/api/admin/exports")
  public ResponseEntity<byte[]> download(@RequestParam("name") String name) {
    return ExportWebMapper.response(exports.download(name));
  }
}
