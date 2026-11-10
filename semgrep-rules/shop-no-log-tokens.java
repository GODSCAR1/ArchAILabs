// Fixtures detectan cada patrón y aceptan su remediación
import org.slf4j.Logger;
class LoggingFixture {
  private Logger logger;
  void log(String token, String accessToken, String refreshToken, String password, int count) {
    // ruleid: shop-no-log-tokens
    logger.trace("token {}", token);
    String value = accessToken;
    // ruleid: shop-no-log-tokens
    logger.debug("access {}", value);
    // ruleid: shop-no-log-tokens
    logger.info("refresh " + refreshToken);
    // ruleid: shop-no-log-tokens
    logger.warn("password {}", password);
    // ruleid: shop-no-log-tokens
    logger.error("token " + token);
    // ok: shop-no-log-tokens
    logger.info("processed {}", count);
    // ok: shop-no-log-tokens
    logger.warn("authentication failed");
  }
}
