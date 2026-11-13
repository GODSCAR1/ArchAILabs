package com.codeja.master_acdc.identity.infrastructure.adapters.out.redis;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.port.SessionVersionBarrier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisSessionVersions implements SessionVersionBarrier {
  public static final String PREFIX = "shop:identity:session-version:v1:";
  private static final DefaultRedisScript<String> ADVANCE =
      new DefaultRedisScript<>(
          """
          local function valid(v)
            return v and (v == '0' or string.match(v, '^[1-9][0-9]*$'))
              and (#v < 19 or (#v == 19 and v <= '9223372036854775807'))
          end
          local old = redis.call('GET', KEYS[1])
          local next = ARGV[1]
          if not valid(next) or (old and not valid(old)) then
            return redis.error_reply('invalid session version')
          end
          if not old or #old < #next or (#old == #next and old < next) then
            redis.call('SET', KEYS[1], next)
          end
          return 'OK'
          """,
          String.class);
  private final StringRedisTemplate redis;

  public RedisSessionVersions(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public Optional<SessionVersion> find(UUID accountId) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(PREFIX + accountId))
          .map(RedisSessionVersions::parse);
    } catch (DataAccessException exception) {
      throw new IdentityException("session-version-unavailable", exception);
    }
  }

  public SessionVersion populate(UUID accountId, SessionVersion version) {
    try {
      if (Boolean.TRUE.equals(
          redis.opsForValue().setIfAbsent(PREFIX + accountId, Long.toString(version.value())))) {
        return version;
      }
      return find(accountId)
          .orElseThrow(() -> new IdentityException("session-version-unavailable"));
    } catch (DataAccessException exception) {
      throw new IdentityException("session-version-unavailable", exception);
    }
  }

  @Override
  public void publish(UUID accountId, SessionVersion version) {
    try {
      String result =
          redis.execute(ADVANCE, List.of(PREFIX + accountId), Long.toString(version.value()));
      if (!"OK".equals(result)) {
        throw new IdentityException("session-version-unavailable");
      }
    } catch (DataAccessException exception) {
      throw new IdentityException("session-version-unavailable", exception);
    }
  }

  private static SessionVersion parse(String value) {
    if (!value.matches("0|[1-9][0-9]{0,18}")) {
      throw new IdentityException("session-version-corrupt");
    }
    try {
      return new SessionVersion(Long.parseLong(value));
    } catch (NumberFormatException exception) {
      throw new IdentityException("session-version-corrupt", exception);
    }
  }
}
