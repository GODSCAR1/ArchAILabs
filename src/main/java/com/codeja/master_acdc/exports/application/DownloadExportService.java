package com.codeja.master_acdc.exports.application;

import com.codeja.master_acdc.exports.application.port.DownloadExportUseCase;
import com.codeja.master_acdc.exports.domain.model.ExportContent;
import com.codeja.master_acdc.exports.domain.port.ExportFiles;

public final class DownloadExportService implements DownloadExportUseCase {
  private final ExportFiles files;

  public DownloadExportService(ExportFiles files) {
    this.files = files;
  }

  @Override
  public ExportContent download(String name) {
    return files.read(name);
  }
}
