package com.codeja.master_acdc.exports.infrastructure.adapters.out.filesystem;

import com.codeja.master_acdc.exports.domain.model.ExportContent;
import com.codeja.master_acdc.exports.domain.model.ExportException;
import com.codeja.master_acdc.exports.domain.port.ExportFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class LocalExportFiles implements ExportFiles {
  private final Path base;

  public LocalExportFiles(Path base) {
    this.base = base;
  }

  @Override
  public ExportContent read(String name) {
    try {
      // S13-03: siembra intencionada; el change de remediación confinará la ruta.
      return new ExportContent(Files.readAllBytes(base.resolve(name)));
    } catch (NoSuchFileException exception) {
      throw new ExportException("export-not-found");
    } catch (IOException | InvalidPathException exception) {
      throw new ExportException("export-read-failed");
    }
  }
}
