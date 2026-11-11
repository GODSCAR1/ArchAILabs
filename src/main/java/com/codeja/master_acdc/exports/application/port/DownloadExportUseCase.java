package com.codeja.master_acdc.exports.application.port;

import com.codeja.master_acdc.exports.domain.model.ExportContent;

public interface DownloadExportUseCase {
  ExportContent download(String name);
}
