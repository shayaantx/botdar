package com.botdarr.clients.discord;

import com.botdarr.Config;
import com.botdarr.commands.Command;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

public class DiscordBootstrapTests {
    @Before
    public void beforeEachTest() {
        writeFakePropertiesFile(getDefaultProperties());
    }

    @Test
    public void convertCommandToCommandData_commandDescriptionGreaterThan100Characters() {
        String longDescription = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffxd";
        new Expectations() {{
           mockedCommand.getDescription(); result = longDescription;
           mockedCommand.getCommandText(); result = "command1";
           mockedCommand.getInput(); result = Collections.emptyList();
        }};
        CommandData commandData = new DiscordBootstrap().convertCommandToCommandData(mockedCommand);
        Assert.assertEquals(longDescription.substring(0, 97) + "...", commandData.getDescription());
    }

    @Test
    public void convertCommandToCommandData_commandDescriptionLessThan100Characters() {
        String longDescription = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        new Expectations() {{
            mockedCommand.getDescription(); result = longDescription;
            mockedCommand.getCommandText(); result = "command1";
            mockedCommand.getInput(); result = Collections.emptyList();
        }};
        CommandData commandData = new DiscordBootstrap().convertCommandToCommandData(mockedCommand);
        Assert.assertEquals(longDescription, commandData.getDescription());
    }

    @Test
    public void convertCommandToCommandData_commandInputConvertedToSlashCommandFormat() {
        String description = "description";
        new Expectations() {{
            mockedCommand.getDescription(); result = description;
            mockedCommand.getCommandText(); result = "command input1 input2";
            mockedCommand.getInput(); result = Collections.emptyList();
        }};
        CommandData commandData = new DiscordBootstrap().convertCommandToCommandData(mockedCommand);
        Assert.assertEquals(description, commandData.getDescription());
        Assert.assertEquals("command-input1-input2", commandData.getName());
    }

    @Test
    public void getCommandFromEmbed_returnsNullDueMissingFieldsInEmbed() {
        new Expectations() {{
            mockedEmbed.getFields(); result = Collections.emptyList();
        }};
        String command = new DiscordBootstrap().getCommandFromEmbed(mockedEmbed);
        Assert.assertNull(command);
    }

    @Test
    public void getCommandFromEmbed_returnsNullDueFieldNotMatchingExpectedFieldNames() {
        new Expectations() {{
            mockedEmbed.getFields(); result = new ArrayList<MessageEmbed.Field>(){{
                add(new MessageEmbed.Field("unknown-field1", "", false));
            }};
        }};
        String command = new DiscordBootstrap().getCommandFromEmbed(mockedEmbed);
        Assert.assertNull(command);
    }

    @Test
    public void getCommandFromEmbed_returnsMovieCommand() {
        new Expectations() {{
            mockedEmbed.getFields(); result = new ArrayList<MessageEmbed.Field>(){{
                add(new MessageEmbed.Field("TmdbId", "43234", false));
            }};
            mockedEmbed.getTitle(); result = "MovieTitle1";
        }};
        String command = new DiscordBootstrap().getCommandFromEmbed(mockedEmbed);
        Assert.assertEquals("!movie id add MovieTitle1 43234", command);
    }

    @Test
    public void getCommandFromEmbed_returnsShowCommand() {
        new Expectations() {{
            mockedEmbed.getFields(); result = new ArrayList<MessageEmbed.Field>(){{
                add(new MessageEmbed.Field("TvdbId", "43234", false));
            }};
        }};
        String command = new DiscordBootstrap().getCommandFromEmbed(mockedEmbed);
        Assert.assertEquals("!show id add 43234", command);
    }

    @Test
    public void getCommandFromEmbed_returnsArtistCommand() {
        new Expectations() {{
            mockedEmbed.getFields(); result = new ArrayList<MessageEmbed.Field>(){{
                add(new MessageEmbed.Field("ForeignArtistId", "43234", false));
            }};
            mockedEmbed.getTitle(); result = "ArtistTitle1";
        }};
        String command = new DiscordBootstrap().getCommandFromEmbed(mockedEmbed);
        Assert.assertEquals("!music artist id add ArtistTitle1 43234", command);
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

    @Mocked
    private MessageEmbed mockedEmbed;

    @Mocked
    private Command mockedCommand;
}
