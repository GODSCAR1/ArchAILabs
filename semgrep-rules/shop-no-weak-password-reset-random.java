// Fixtures detectan cada patrón y aceptan su remediación
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

final class WeakRandomPasswordResetSecretGenerator implements PasswordResetSecrets {
  // ruleid: shop-no-weak-password-reset-random
  private final Random random = new Random();

  public String generate() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}

final class SecurePasswordResetSecretGenerator implements PasswordResetSecrets {
  // ok: shop-no-weak-password-reset-random
  private final SecureRandom random = new SecureRandom();

  public String generate() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}

class Simulation {
  // ok: shop-no-weak-password-reset-random
  private final Random random = new Random(42);
}

class EmailVerificationCodeGenerator {
  int generate() {
    // ruleid: shop-no-weak-password-reset-random
    return ThreadLocalRandom.current().nextInt(1_000_000);
  }
  int secure() {
    // ok: shop-no-weak-password-reset-random
    return new SecureRandom().nextInt(1_000_000);
  }
}

class Examples {
  // ruleid: shop-no-weak-password-reset-random
  private final Random saltSource = new java.util.Random();

  int generateOtp() {
    // ruleid: shop-no-weak-password-reset-random
    return new Random().nextInt();
  }
  int generateSecureOtp() {
    // ok: shop-no-weak-password-reset-random
    return new SecureRandom().nextInt();
  }
  void generate() {
    // ruleid: shop-no-weak-password-reset-random
    double resetToken = Math.random();
    // ok: shop-no-weak-password-reset-random
    int resetCode = new SecureRandom().nextInt();
    // ruleid: shop-no-weak-password-reset-random
    int verificationCode = RandomGenerator.getDefault().nextInt();
    // ruleid: shop-no-weak-password-reset-random
    int otp = RandomGenerator.of("L64X128MixRandom").nextInt();
    double nonce;
    // ruleid: shop-no-weak-password-reset-random
    nonce = Math.random();
  }
}

class CacheTtlPolicy {
  long jitter() {
    // ok: shop-no-weak-password-reset-random
    return ThreadLocalRandom.current().nextLong(10);
  }
}
