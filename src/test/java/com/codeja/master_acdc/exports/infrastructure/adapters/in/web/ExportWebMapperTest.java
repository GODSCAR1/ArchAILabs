package com.codeja.master_acdc.exports.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.exports.domain.model.ExportContent;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ExportWebMapperTest {
  @Test
  void should_map_bytes_when_export_is_downloaded() {
    var response = ExportWebMapper.response(new ExportContent(new byte[] {1, 2}));
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    assertThat(response.getBody()).containsExactly(1, 2);
  }
}
