package com.codeja.master_acdc.exports.infrastructure.adapters.in.web;

import com.codeja.master_acdc.exports.domain.model.ExportContent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ExportWebMapper {
  private ExportWebMapper() {}

  public static ResponseEntity<byte[]> response(ExportContent content) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(content.bytes());
  }
}
