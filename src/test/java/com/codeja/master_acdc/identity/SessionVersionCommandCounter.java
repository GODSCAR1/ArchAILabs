package com.codeja.master_acdc.identity;

import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.event.command.CommandListener;
import io.lettuce.core.event.command.CommandStartedEvent;
import io.lettuce.core.protocol.CommandType;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

final class SessionVersionCommandCounter implements CommandListener, AutoCloseable {
  private final AbstractRedisClient client;
  private final LettuceConnectionFactory factory;
  private final AtomicInteger gets = new AtomicInteger();
  private final AtomicInteger writes = new AtomicInteger();

  SessionVersionCommandCounter(LettuceConnectionFactory factory) {
    this.factory = factory;
    client = factory.getNativeClient();
    client.addListener(this);
    factory.resetConnection();
  }

  @Override
  public void commandStarted(CommandStartedEvent event) {
    var command = event.getCommand();
    if (command.getArgs() == null || command.getArgs().getFirstEncodedKey() == null) {
      return;
    }
    String key =
        StandardCharsets.UTF_8
            .decode(command.getArgs().getFirstEncodedKey().duplicate())
            .toString();
    if (key.startsWith(RedisSessionVersions.PREFIX)) {
      if (command.getType() == CommandType.GET) {
        gets.incrementAndGet();
      } else if (command.getType() == CommandType.SET || command.getType() == CommandType.SETNX) {
        writes.incrementAndGet();
      }
    }
  }

  int gets() {
    return gets.get();
  }

  int writes() {
    return writes.get();
  }

  void reset() {
    gets.set(0);
    writes.set(0);
  }

  @Override
  public void close() {
    client.removeListener(this);
    factory.resetConnection();
  }
}
