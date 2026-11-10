package com.codeja.master_acdc.exports.domain.port;

import com.codeja.master_acdc.exports.domain.model.ExportContent;

public interface ExportFiles {
  ExportContent read(String name);
}
