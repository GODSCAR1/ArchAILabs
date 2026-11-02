package com.codeja.master_acdc.catalog.infrastructure.config;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class CatalogMongoTestConfiguration {

  private static final AtomicInteger READ_COMMANDS = new AtomicInteger();

  public static void resetReadCommands() {
    READ_COMMANDS.set(0);
  }

  public static int readCommands() {
    return READ_COMMANDS.get();
  }

  @Bean
  MongoClientSettingsBuilderCustomizer mongoUuidRepresentation() {
    return settings ->
        settings
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .addCommandListener(
                new CommandListener() {
                  @Override
                  public void commandStarted(CommandStartedEvent event) {
                    if (Set.of("find", "count", "aggregate").contains(event.getCommandName())) {
                      READ_COMMANDS.incrementAndGet();
                    }
                  }
                });
  }
}
