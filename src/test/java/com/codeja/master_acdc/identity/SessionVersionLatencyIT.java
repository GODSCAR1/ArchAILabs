package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtSettings;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@TestPropertySource(properties = "catalog.cache.enabled=false")
class SessionVersionLatencyIT extends IdentityIntegrationSupport {
  @Autowired JwtDecoder decoder;
  @Autowired AccessTokenIssuer issuer;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @Autowired GenericContainer<?> identityRedis;
  @Autowired PostgreSQLContainer<?> identityPostgres;
  @Autowired JWKSource<SecurityContext> source;
  @Autowired JwtSettings settings;
  @Autowired RSAKey key;

  enum Mode {
    HIT(200, 100),
    MISS(10, 10),
    TIMEOUT(3, 4);
    final int warmup;
    final int perRound;

    Mode(int warmup, int perRound) {
      this.warmup = warmup;
      this.perRound = perRound;
    }
  }

  record Sample(Mode mode, int round, int index, long baseline, long full) {
    long delta() {
      return full - baseline;
    }
  }

  @Test
  void should_measure_real_latency_when_comparing_crypto_and_shared_validation() throws Exception {
    String token = OrderBearer.issue(jdbc, issuer, null, "SUPPORT");
    var baseline = cryptographicBaseline();
    var samples = new ArrayList<Sample>();
    for (Mode mode : Mode.values()) {
      if (mode == Mode.TIMEOUT) {
        pauseRedis();
      }
      try {
        for (int warmup = 0; warmup < mode.warmup; warmup++) {
          measure(mode, token, baseline, 0, warmup);
        }
        for (int round = 1; round <= 5; round++) {
          for (int index = 0; index < mode.perRound; index++) {
            samples.add(measure(mode, token, baseline, round, index));
          }
        }
      } finally {
        if (mode == Mode.TIMEOUT) {
          assertThat(identityRedis.execInContainer("redis-cli", "CLIENT", "UNPAUSE").getExitCode())
              .isZero();
        }
      }
    }
    assertThat(samples).hasSize(570);
    assertThat(decoder.decode(token).getSubject())
        .isEqualTo(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
    writeEvidence(samples);
  }

  private Sample measure(Mode mode, String token, JwtDecoder baseline, int round, int index)
      throws java.text.ParseException {
    if (mode == Mode.MISS) {
      redis.delete(
          RedisSessionVersions.PREFIX + SignedJWT.parse(token).getJWTClaimsSet().getSubject());
    }
    long base;
    long full;
    if (index % 2 == 0) {
      base = timed(baseline, token, false);
      full = timed(decoder, token, mode == Mode.TIMEOUT);
    } else {
      full = timed(decoder, token, mode == Mode.TIMEOUT);
      base = timed(baseline, token, false);
    }
    return new Sample(mode, round, index, base, full);
  }

  private long timed(JwtDecoder candidate, String token, boolean rejectionExpected) {
    long start = System.nanoTime();
    try {
      candidate.decode(token);
      if (rejectionExpected) {
        throw new AssertionError("Shared validation accepted a request during Redis timeout");
      }
    } catch (JwtException exception) {
      if (!rejectionExpected) {
        throw exception;
      }
    }
    return System.nanoTime() - start;
  }

  private JwtDecoder cryptographicBaseline() {
    // Isolated benchmark harness: no bean and no alternative production decoder.
    var processor = new DefaultJWTProcessor<SecurityContext>();
    var selector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source);
    processor.setJWSKeySelector(
        (header, context) ->
            header.getKeyID() == null ? List.of() : selector.selectJWSKeys(header, context));
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    var baseline = new NimbusJwtDecoder(processor);
    baseline.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(settings.issuer()),
            new JwtTimestampValidator(Duration.ZERO),
            new JwtClaimValidator<List<String>>(
                "aud", audience -> audience.contains(settings.audience())),
            new JwtClaimValidator<Instant>(
                "iat", issued -> issued != null && !issued.isAfter(Instant.now()))));
    return baseline;
  }

  private void pauseRedis() {
    redis.execute(
        (RedisCallback<Object>)
            connection ->
                connection.execute(
                    "CLIENT",
                    "PAUSE".getBytes(StandardCharsets.UTF_8),
                    "100000".getBytes(StandardCharsets.UTF_8),
                    "ALL".getBytes(StandardCharsets.UTF_8)));
  }

  private void writeEvidence(List<Sample> samples) throws java.io.IOException {
    StringBuilder csv = new StringBuilder("mode,round,sample,baseline_ns,full_ns,delta_ns\n");
    samples.forEach(
        sample ->
            csv.append(
                String.format(
                    Locale.ROOT,
                    "%s,%d,%d,%d,%d,%d%n",
                    sample.mode(),
                    sample.round(),
                    sample.index(),
                    sample.baseline(),
                    sample.full(),
                    sample.delta())));
    Files.writeString(Path.of("target/session-version-latency.csv"), csv);
    var report = new StringBuilder(environment());
    report.append(
        "\n"
            + "| Mode | Samples | Baseline p50/p95 ms | Full p50/p95 ms | Paired delta p50/p95 ms"
            + " |\n"
            + "|---|---:|---:|---:|---:|\n");
    for (Mode mode : Mode.values()) {
      var measured = samples.stream().filter(sample -> sample.mode() == mode).toList();
      report.append(
          String.format(
              Locale.ROOT,
              "| %s | %d | %s | %s | %s |%n",
              mode,
              measured.size(),
              percentiles(measured.stream().mapToLong(Sample::baseline).toArray()),
              percentiles(measured.stream().mapToLong(Sample::full).toArray()),
              percentiles(measured.stream().mapToLong(Sample::delta).toArray())));
    }
    Files.writeString(Path.of("target/session-version-latency.md"), report);
  }

  private String environment() {
    return "Measured "
        + Instant.now()
        + "\nHost: "
        + System.getProperty("os.name")
        + " "
        + System.getProperty("os.arch")
        + "; Java "
        + System.getProperty("java.runtime.version")
        + "; processors "
        + Runtime.getRuntime().availableProcessors()
        + "; max heap bytes "
        + Runtime.getRuntime().maxMemory()
        + "; RSA bits "
        + key.size()
        + "; Docker "
        + identityRedis.getDockerClient().versionCmd().exec().getVersion()
        + "; Redis image "
        + identityRedis.getDockerImageName()
        + "; PostgreSQL image "
        + identityPostgres.getDockerImageName()
        + ".\n"
        + "5 rounds; warmup HIT=200, MISS=10, TIMEOUT=3; per-round samples 100/10/4. Redis timeout"
        + " 200 ms.\n"
        + "Sequential, same token/account/connection per mode; alternating pair order. MISS deletes"
        + " outside timing; TIMEOUT pauses commands on real Redis.\n"
        + "Nearest-rank percentiles of nanosecond samples; paired delta is full-baseline. No CI"
        + " latency threshold.\n";
  }

  private static String percentiles(long[] values) {
    return String.format(
        Locale.ROOT, "%.3f / %.3f", percentile(values, 0.50), percentile(values, 0.95));
  }

  static double percentile(long[] values, double fraction) {
    long[] sorted = values.clone();
    Arrays.sort(sorted);
    return sorted[(int) Math.ceil(sorted.length * fraction) - 1] / 1_000_000.0;
  }
}
