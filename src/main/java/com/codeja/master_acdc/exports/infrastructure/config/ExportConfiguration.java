package com.codeja.master_acdc.exports.infrastructure.config;

import com.codeja.master_acdc.exports.application.DownloadExportService;
import com.codeja.master_acdc.exports.application.port.DownloadExportUseCase;
import com.codeja.master_acdc.exports.domain.port.ExportFiles;
import com.codeja.master_acdc.exports.infrastructure.adapters.in.web.AuthorizedDownloadExportUseCase;
import com.codeja.master_acdc.exports.infrastructure.adapters.out.filesystem.LocalExportFiles;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExportConfiguration {
  @Bean
  public Path exportDirectory() {
    return Path.of("exports");
  }

  @Bean
  public ExportFiles exportFiles(Path exportDirectory) {
    return new LocalExportFiles(exportDirectory);
  }

  @Bean
  public DownloadExportUseCase downloadExportUseCase(ExportFiles files) {
    return new AuthorizedDownloadExportUseCase(new DownloadExportService(files));
  }
}
