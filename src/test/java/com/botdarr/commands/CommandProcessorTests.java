package com.botdarr.commands;

import com.botdarr.Config;
import com.botdarr.TestCommandResponse;
import com.botdarr.api.lidarr.LidarrApi;
import com.botdarr.api.lidarr.LidarrCommands;
import com.botdarr.api.radarr.RadarrApi;
import com.botdarr.api.radarr.RadarrCommands;
import com.botdarr.api.sonarr.SonarrApi;
import com.botdarr.api.sonarr.SonarrCommands;
import com.botdarr.commands.responses.CommandResponse;
import com.botdarr.commands.responses.ErrorResponse;
import com.botdarr.commands.responses.InfoResponse;
import mockit.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * These tests specifically test commands that take no arguments and commands that take arguments
 */
public class CommandProcessorTests {
  @Before
  public void beforeEachTest() {
    mockCommandPrefix("!");
    writeFakePropertiesFile(getDefaultProperties());
  }

  @Test
  public void processMessage_validateHelpCommand() {
    validateInvalidCommandIdentifier("!helpx");
  }

  @Test
  public void processMessage_validateMoviesHelpCommand() {
    validateNoArgCommands("!movies help");
  }

  @Test
  public void processMessage_validateShowsHelpCommand() {
    validateNoArgCommands("!shows help");
  }

  @Test
  public void processMessage_validateMoviesDiscoverCommand() {
    validateNoArgCommands("!movie discover");
  }

  @Test
  public void processMessage_validateMovieProfilesCommand() {
    validateNoArgCommands("!movie profiles");
  }

  @Test
  public void processMessage_validateMovieDownloadsCommand() {
    new Expectations() {{
      radarrApi.downloads(); result = new ArrayList<CommandResponse>(); minTimes = 1;
    }};
    validateNoArgCommands("!movie downloads");
    List<CommandResponse> responses = validateValidCommand("!movie downloads");
    Assert.assertEquals(1, responses.size());
    Assert.assertTrue(
            EqualsBuilder.reflectionEquals(new InfoResponse("No movies downloading"),
                    responses.get(0)));
  }

  @Test
  public void processMessage_validateShowDownloadsCommand() {
    new Expectations() {{
      sonarrApi.downloads(); result = new ArrayList<CommandResponse>(); minTimes = 1;
    }};
    validateNoArgCommands("!show downloads");
    List<CommandResponse> responses = validateValidCommand("!show downloads");
    Assert.assertEquals(1, responses.size());
    Assert.assertTrue(
            EqualsBuilder.reflectionEquals(new InfoResponse("No shows downloading"),
                    responses.get(0)));
  }

  @Test
  public void processMessage_validateMusicDownloadsCommand() {
    new Expectations() {{
      lidarrApi.downloads(); result = new ArrayList<CommandResponse>(); minTimes = 1;
    }};
    validateNoArgCommands("!music downloads");
    List<CommandResponse> responses = validateValidCommand("!music downloads");
    Assert.assertEquals(1, responses.size());
    Assert.assertTrue(
            EqualsBuilder.reflectionEquals(new InfoResponse("No artists downloading"),
                    responses.get(0)));
  }

  @Test
  public void processMessage_validateShowProfilesCommand() {
    validateNoArgCommands("!show profiles");
  }

  @Test
  public void processMessage_missingMovieTitleAndIdForAddCommand() {
    validateInvalidCommand("!movie id add",
      "Error trying to parse command !movie id add, " +
        "error=Missing expected arguments - usage: movie id add MOVIE_TITLE_HERE MOVIE_ID_HERE");
  }

  @Test
  public void processMessage_missingMovieTitleForAddCommand() {
    validateInvalidCommand("!movie id add 541515",
      "Error trying to parse command !movie id add 541515, " +
      "error=Missing expected arguments - usage: movie id add MOVIE_TITLE_HERE MOVIE_ID_HERE");
  }

  @Test
  public void processMessage_missingMovieIdForAddCommand() {
    validateInvalidCommand("!movie id add Princess5",
      "Error trying to parse command !movie id add Princess5, " +
        "error=Missing expected arguments - usage: movie id add MOVIE_TITLE_HERE MOVIE_ID_HERE");
  }

  @Test
  public void processMessage_invalidMovieIdForAddCommand() {
    validateInvalidCommand("!movie id add Princess5 4647x5",
      "Error trying to parse command !movie id add Princess5 4647x5, " +
        "error=Movie id is not a number");
  }

  @Test
  public void processMessage_validMovieTitleAndIdForAddCommand() {
    new Expectations() {{
      radarrApi.addWithId("princess5", "46475"); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie id add Princess5 46475");
  }

  @Test
  public void processMessage_validMovieWithSpacesInTitleForAddCommand() {
    new Expectations() {{
      radarrApi.addWithId("princess 5", "46475"); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie id add Princess 5 46475");
  }

  @Test
  public void processMessage_invalidMovieTitleForAddCommand() {
    validateInvalidCommand("!movie title add",
      "Error trying to parse command !movie title add, " +
        "error=Movie title is missing");
  }

  @Test
  public void processMessage_validMovieTitleForAddCommand() {
    new Expectations() {{
      radarrApi.addWithTitle("princess5"); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie title add Princess5");
  }

  @Test
  public void processMessage_validMovieTitleWithSpacesForAddCommand() {
    new Expectations() {{
      radarrApi.addWithTitle("princess 5"); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie title add Princess 5");
  }

  @Test
  public void processMessage_invalidMovieTitleForFindNewMovieCommand() {
    validateInvalidCommand("!movie find new",
      "Error trying to parse command !movie find new, " +
      "error=Movie title is missing");
  }

  @Test
  public void processMessage_validMovieTitleForFindNewMovieCommand() {
    new Expectations() {{
      radarrApi.lookup("princess5", true); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie find new Princess5");
  }

  @Test
  public void processMessage_validMovieTitleWithSpacesForFindNewMovieCommand() {
    new Expectations() {{
      radarrApi.lookup("princess 5", true); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie find new Princess 5");
  }

  @Test
  public void processMessage_invalidMovieTitleForFindExistingMovieCommand() {
    validateInvalidCommand("!movie find existing",
      "Error trying to parse command !movie find existing, " +
        "error=Movie title is missing");
  }

  @Test
  public void processMessage_validMovieTitleForFindExistingMovieCommand() {
    new Expectations() {{
      radarrApi.lookup("princess5", false); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie find existing Princess5");
  }

  @Test
  public void processMessage_validMovieTitleWithSpacesForFindExistingMovieCommand() {
    new Expectations() {{
      radarrApi.lookup("princess 5", false); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!movie find existing Princess 5");
  }

  @Test
  public void processMessage_missingShowIdForAddCommand() {
    validateInvalidCommand("!show id add",
      "Error trying to parse command !show id add, " +
        "error=Missing expected arguments - usage: show id add SHOW_ID_HERE");
  }

  @Test
  public void processMessage_invalidShowIdForAddCommand() {
    validateInvalidCommand("!show id add 4647x5",
      "Error trying to parse command !show id add 4647x5, " +
        "error=Show id is not a number");
  }

  @Test
  public void processMessage_validShowIdForAddCommand() {
    new Expectations() {{
      sonarrApi.addWithId("46475"); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!show id add 46475");
  }

  @Test
  public void processMessage_invalidShowTitleForAddCommand() {
    validateInvalidCommand("!show title add",
      "Error trying to parse command !show title add, " +
        "error=Show title is missing");
  }

  @Test
  public void processMessage_validShowTitleForAddCommand() {
    new Expectations() {{
      sonarrApi.addWithTitle("princess5"); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!show title add Princess5");
  }

  @Test
  public void processMessage_validShowTitleWithSpacesForAddCommand() {
    new Expectations() {{
      sonarrApi.addWithTitle("princess 5"); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!show title add Princess 5");
  }

  @Test
  public void processMessage_invalidShowTitleForFindNewShowCommand() {
    validateInvalidCommand("!show find new",
      "Error trying to parse command !show find new, " +
        "error=Show title is missing");
  }

  @Test
  public void processMessage_validShowTitleForFindNewShowCommand() {
    new Expectations() {{
      sonarrApi.lookup("princess5", true); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!show find new Princess5");
  }

  @Test
  public void processMessage_validShowTitleWithSpacesForFindNewShowCommand() {
    new Expectations() {{
      sonarrApi.lookup("princess 5", true); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!show find new Princess 5");
  }

  @Test
  public void processMessage_invalidShowTitleForFindExistingShowCommand() {
    validateInvalidCommand("!show find existing",
      "Error trying to parse command !show find existing, " +
        "error=Show title is missing");
  }

  @Test
  public void processMessage_validShowTitleForFindExistingShowCommand() {
    new Expectations() {{
      sonarrApi.lookup("princess5", false); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!show find existing Princess5");
  }

  @Test
  public void processMessage_validShowTitleWithSpacesForFindExistingShowCommand() {
    new Expectations() {{
      sonarrApi.lookup("princess 5", false); times = 1; result = new TestCommandResponse();
    }};
    validateValidCommand("!show find existing Princess 5");
  }

  private void validateNoArgCommands(String command) {
    //validate an extra character after the command is invalid
    validateInvalidCommandIdentifier(command + "x");
    //validate an extra space around the extra character after the command is invalid
    validateInvalidCommandIdentifier(command + " x ");
    //validate an extra space after the command is trimmed and the command is still valid
    validateValidCommand(command + " ");
    //validate the command itself is valid
    validateValidCommand(command);
  }

  private void validateInvalidCommandIdentifier(String invalidCommand) {
    validateInvalidCommand(invalidCommand, "Invalid command - type !help for command usage");
  }

  private List<CommandResponse> validateValidCommand(String validCommand) {
    CommandProcessor commandProcessor = new CommandProcessor();
    List<CommandResponse> commandResponses =
      commandProcessor.processCommand(CommandContext.getConfig().getPrefix(), getCommandsToTest(), validCommand);
    //we are just making sure no error responses are returned since they signify a failure with the command
    if (commandResponses != null) {
      for (CommandResponse response: commandResponses) {
        Assert.assertNotNull(response);
        Assert.assertNotEquals(ErrorResponse.class, response.getClass());
      }
    }
    return commandResponses;
  }

  private void validateInvalidCommand(String invalidCommand, String expectedErrorResponseString) {
    CommandProcessor commandProcessor = new CommandProcessor();
    List<CommandResponse> commandResponses =
            commandProcessor.processCommand(CommandContext.getConfig().getPrefix(), getCommandsToTest(), invalidCommand);
    if (commandResponses != null) {
      Assert.assertEquals(1, commandResponses.size());
      CommandResponse commandResponse = commandResponses.get(0);
      Assert.assertNotNull(commandResponse);
      Assert.assertTrue(
              EqualsBuilder.reflectionEquals(
                      new ErrorResponse(expectedErrorResponseString),
                      commandResponse, false, null, true));
    }
  }

  private void mockCommandPrefix(String prefix) {
    new MockUp<CommandProcessor>() {
      @Mock
      String getPrefix() {
        return prefix;
      }
    };
  }

  private List<Command> getCommandsToTest() {
    List<Command> radarrCommands = RadarrCommands.getCommands(radarrApi);
    List<Command> sonarrCommands = SonarrCommands.getCommands(sonarrApi);
    List<Command> lidarrCommands = LidarrCommands.getCommands(lidarrApi);
    List<Command> commands = new ArrayList<>(HelpCommands.getCommands(radarrCommands, sonarrCommands, lidarrCommands));
    commands.addAll(radarrCommands);
    commands.addAll(sonarrCommands);
    commands.addAll(lidarrCommands);
    return commands;
  }

  private void writeFakePropertiesFile(Properties properties) {
    File propertiesFile = null;
    try {
      propertiesFile = temporaryFolder.newFile("properties");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Deencapsulation.setField(Config.class, "propertiesPath", propertiesFile.getPath());
    try (FileOutputStream fos = new FileOutputStream(propertiesFile)) {
      properties.store(fos, "");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Properties getDefaultProperties() {
    Properties properties = new Properties();
    properties.setProperty("discord-token", "G$K$GK");
    properties.setProperty("discord-channels", "plex-testing2");
    properties.setProperty("radarr-url", "http://localhost:444");
    properties.setProperty("radarr-token", "FSJDkjmf#$Kf3");
    properties.setProperty("radarr-path", "/movies");
    properties.setProperty("radarr-default-profile", "any");
    return properties;
  }

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Injectable
  private RadarrApi radarrApi;

  @Injectable
  private SonarrApi sonarrApi;

  @Injectable
  private LidarrApi lidarrApi;
}
