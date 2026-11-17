package com.codeja.master_acdc;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestBeans {
  private static final AtomicInteger READ_COMMANDS = new AtomicInteger();

  public static void resetReadCommands() {
    READ_COMMANDS.set(0);
  }

  public static int readCommands() {
    return READ_COMMANDS.get();
  }

  @Bean
  @Primary
  Path testExportDirectory() throws IOException {
    Path directory = Files.createTempDirectory("exports-test-");
    directory.toFile().deleteOnExit();
    return directory;
  }

  @Bean
  MongoClientSettingsBuilderCustomizer mongoReadListener() {
    return settings ->
        settings.addCommandListener(
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
