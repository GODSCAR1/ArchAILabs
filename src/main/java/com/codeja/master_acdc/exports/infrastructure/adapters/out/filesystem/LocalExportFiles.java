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
    this.base = base.toAbsolutePath().normalize();
  }

  @Override
  public ExportContent read(String name) {
    try {
      Path candidate = base.resolve(name).normalize();
      if (!candidate.startsWith(base)) {
        throw new ExportException("invalid-export-name");
      }
      return new ExportContent(Files.readAllBytes(candidate));
    } catch (NoSuchFileException exception) {
      throw new ExportException("export-not-found");
    } catch (InvalidPathException exception) {
      throw new ExportException("invalid-export-name");
    } catch (IOException exception) {
      throw new ExportException("export-read-failed");
    }
  }
}
