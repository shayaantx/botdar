package com.botdarr.clients;

import com.botdarr.api.*;
import com.botdarr.Config;
import com.botdarr.api.lidarr.LidarrApi;
import com.botdarr.api.lidarr.LidarrCommands;
import com.botdarr.api.radarr.RadarrApi;
import com.botdarr.api.radarr.RadarrCommands;
import com.botdarr.api.sonarr.SonarrApi;
import com.botdarr.api.sonarr.SonarrCommands;
import com.botdarr.commands.*;
import com.botdarr.commands.responses.CommandResponse;
import com.botdarr.scheduling.Scheduler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public abstract class ChatClientBootstrap {
  public abstract void init() throws Exception;
  public abstract boolean isConfigured(Properties properties);
  public void validatePrefix(String configuredPrefix) {
    if (configuredPrefix.length() > 1) {
      throw new RuntimeException("Command prefix must be a single character");
    }
  }

  protected <T extends ChatClientResponse> ApisAndCommandConfig buildConfig() {
    RadarrApi radarrApi = new RadarrApi();
    SonarrApi sonarrApi = new SonarrApi();
    LidarrApi lidarrApi = new LidarrApi();

    List<Command> radarrCommands = RadarrCommands.getCommands(radarrApi);
    List<Command> sonarrCommands = SonarrCommands.getCommands(sonarrApi);
    List<Command> lidarrCommands = LidarrCommands.getCommands(lidarrApi);

    List<Command> commands = new ArrayList<>();
    List<Api> apis = new ArrayList<>();
    if (Config.isRadarrEnabled()) {
      commands.addAll(radarrCommands);
      apis.add(radarrApi);
    }
    if (Config.isSonarrEnabled()) {
      commands.addAll(sonarrCommands);
      apis.add(sonarrApi);
    }
    if (Config.isLidarrEnabled()) {
      commands.addAll(lidarrCommands);
      apis.add(lidarrApi);
    }
    if (!Config.getStatusEndpoints().isEmpty()) {
      commands.add(new StatusCommand());
    }
    commands.addAll(HelpCommands.getCommands(radarrCommands, sonarrCommands, lidarrCommands));
    return new ApisAndCommandConfig(apis, commands);
  }

  protected <T extends ChatClientResponse> void initScheduling(ChatClient<T> chatClient, ChatClientResponseBuilder<T> responseBuilder, List<Api> apis) {
    Scheduler scheduler = Scheduler.getScheduler();
    //make sure to always cache before doing any notifications
    scheduler.initApiCaching(apis);
    scheduler.initApiNotifications(apis, chatClient, responseBuilder);
    scheduler.initCleanup(chatClient);
  }

  protected <T extends ChatClientResponse> void runAndProcessCommands(String prefix,
                                                                      String message,
                                                                      String username,
                                                                      ChatClientResponseBuilder<T> responseBuilder,
                                                                      ChatSender<T> chatSender) {
    try {
      CommandContext
              .start()
              .setPrefix(prefix)
              .setUsername(username);
      LOGGER.debug("Processing command " + message + " for username " + username + " with prefix " + prefix);
      List<CommandResponse> commandResponses =
              commandProcessor.processCommand(prefix, buildConfig().getCommands(), message);
      if (commandResponses != null) {
        //if there is a response, format it for given response builder
        for (CommandResponse commandResponse : commandResponses) {
          LOGGER.debug("Processing command response " + commandResponse.toString());
          //convert command response into chat client specific response
          T clientResponse = commandResponse.convertToChatClientResponse(responseBuilder);
          //then send the response
          chatSender.send(clientResponse);
        }
      }
    } catch (Throwable throwable) {
      LOGGER.error("Error caught processing command", throwable);
    } finally {
      CommandContext.end();
    }
  }

  protected static class ApisAndCommandConfig {
    private ApisAndCommandConfig(List<Api> apis, List<Command> commands) {
      this.apis = apis;
      this.commands = commands;
    }

    public List<Api> getApis() {
      return apis;
    }

    public List<Command> getCommands() {
      return commands;
    }

    private final List<Api> apis;
    private final List<Command> commands;
  }

  protected CommandProcessor commandProcessor = new CommandProcessor();
  protected static final Logger LOGGER = LogManager.getLogger(ChatClientBootstrap.class);
}
