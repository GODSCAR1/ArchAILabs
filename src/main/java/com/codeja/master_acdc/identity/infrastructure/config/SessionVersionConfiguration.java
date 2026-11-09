package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.domain.port.SessionVersions;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SessionVersionConfiguration {
  @Bean
  @ConditionalOnProperty(name = "catalog.cache.enabled", havingValue = "false")
  public LettuceClientConfigurationBuilderCustomizer identityRejectDisconnectedCommands() {
    return builder ->
        builder.clientOptions(
            ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
  }

  @Bean
  public RedisSessionVersions redisSessionVersions(StringRedisTemplate redis) {
    return new RedisSessionVersions(redis);
  }

  @Bean
  public SessionVersions sessionVersions(
      RedisSessionVersions redis, UserAccounts accounts, IdentityTransactions transactions) {
    return new LockedSessionVersions(redis, accounts, transactions);
  }
}
