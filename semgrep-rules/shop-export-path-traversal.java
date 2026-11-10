// Fixtures detectan cada patrón y aceptan su remediación
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class ExportFixture {
  byte[] vulnerable(Path base, String name) throws IOException {
    // ruleid: shop-export-path-traversal
    return Files.readAllBytes(base.resolve(name));
  }

  String vulnerableReadString(Path base, String name) throws IOException {
    // ruleid: shop-export-path-traversal
    return Files.readString(base.resolve(name), StandardCharsets.UTF_8);
  }

  byte[] vulnerableIntermediate(Path base, String name) throws IOException {
    Path candidate = base.resolve(name);
    // ruleid: shop-export-path-traversal
    return Files.readAllBytes(candidate);
  }

  byte[] vulnerableNormalizedOnly(Path base, String name) throws IOException {
    Path candidate = base.resolve(name).normalize();
    // ruleid: shop-export-path-traversal
    return Files.readAllBytes(candidate);
  }

  byte[] confined(Path base, String name) throws IOException {
    base = base.toAbsolutePath().normalize();
    Path candidate = base.resolve(name).normalize();
    if (!candidate.startsWith(base)) {
      throw new IllegalArgumentException();
    }
    // ok: shop-export-path-traversal
    return Files.readAllBytes(candidate);
  }

  byte[] constantName(Path base) throws IOException {
    Path candidate = base.resolve("fixed.csv");
    if (!candidate.startsWith(base)) {
      throw new IllegalArgumentException();
    }
    // ok: shop-export-path-traversal
    return Files.readAllBytes(candidate);
  }
}
