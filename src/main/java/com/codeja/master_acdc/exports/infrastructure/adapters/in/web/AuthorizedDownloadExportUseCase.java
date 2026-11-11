package com.codeja.master_acdc.exports.infrastructure.adapters.in.web;

import com.codeja.master_acdc.exports.application.port.DownloadExportUseCase;
import com.codeja.master_acdc.exports.domain.model.ExportContent;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedDownloadExportUseCase implements DownloadExportUseCase {
  private final DownloadExportUseCase delegate;

  public AuthorizedDownloadExportUseCase(DownloadExportUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ExportContent download(String name) {
    return delegate.download(name);
  }
}
