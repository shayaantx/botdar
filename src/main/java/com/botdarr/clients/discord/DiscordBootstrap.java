package com.botdarr.clients.discord;

import com.botdarr.Config;
import com.botdarr.api.lidarr.LidarrCommands;
import com.botdarr.api.radarr.RadarrCommands;
import com.botdarr.api.sonarr.SonarrCommands;
import com.botdarr.clients.ChatClient;
import com.botdarr.clients.ChatClientBootstrap;
import com.botdarr.clients.ChatClientResponseBuilder;
import com.botdarr.commands.Command;
import com.botdarr.commands.CommandContext;
import com.botdarr.scheduling.Scheduler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static com.botdarr.api.lidarr.LidarrApi.ADD_ARTIST_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.lidarr.LidarrApi.ARTIST_LOOKUP_KEY_FIELD;
import static com.botdarr.api.radarr.RadarrApi.ADD_MOVIE_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.radarr.RadarrApi.MOVIE_LOOKUP_FIELD;
import static com.botdarr.api.sonarr.SonarrApi.ADD_SHOW_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.sonarr.SonarrApi.SHOW_LOOKUP_FIELD;

public class DiscordBootstrap extends ChatClientBootstrap {
    @Override
    public void init() throws Exception {
        try {
            ChatClientBootstrap.ApisAndCommandConfig config = buildConfig();
            ChatClientResponseBuilder<DiscordResponse> responseBuilder = new DiscordResponseBuilder();

            JDA jda = JDABuilder.createDefault((Config.getProperty(Config.Constants.DISCORD_TOKEN))).addEventListeners(new ListenerAdapter() {
                @Override
                public void onGenericEvent(@Nonnull GenericEvent event) {
                    super.onGenericEvent(event);
                }

                @Override
                public void onReady(@Nonnull ReadyEvent event) {
                    LogManager.getLogger("com.botdarr.clients.discord").info("Connected to discord");
                    ChatClient<DiscordResponse> chatClient = new DiscordChatClient(event.getJDA());
                    //start the scheduler threads that send notifications and cache data periodically
                    initScheduling(chatClient, responseBuilder, config.getApis());
                    super.onReady(event);
                }

                @Override
                public void onSlashCommand(@NotNull SlashCommandEvent event) {
                    // let discord know we received the message as quick as we can
                    event.deferReply().queue();

                    String discordSlashCommandPrefix = "/";
                    StringBuilder eventCommand = new StringBuilder(discordSlashCommandPrefix + event.getName().replace('-', ' '));
                    for (OptionMapping option : event.getOptions()) {
                        eventCommand.append(" ").append(option.getAsString());
                    }
                    ChatClientResponseBuilder<DiscordResponse> slashCommandResponseBuilder = new DiscordResponseBuilder().usesSlashCommands();
                    //capture/process command
                    Scheduler.getScheduler().executeCommand(() -> {
                        runAndProcessCommands(discordSlashCommandPrefix, eventCommand.toString(), event.getUser().getName(), slashCommandResponseBuilder, chatClientResponse -> {
                            // then send the rest of the messages
                            WebhookMessageAction<Message> action = event.getHook().sendMessageEmbeds(Collections.singletonList(chatClientResponse.getMessage()));
                            if (!chatClientResponse.getActionComponents().isEmpty()) {
                                action = action.addActionRow(chatClientResponse.getActionComponents());
                            }
                            action.queue();
                        });
                        return null;
                    });
                    LogManager.getLogger("com.botdarr.clients.discord").debug(eventCommand);
                }

                @Override
                public void onButtonClick(@NotNull ButtonClickEvent event) {
                    Message message = event.getInteraction().getMessage();
                    message.getEmbeds().forEach(embed -> {
                        String command = getCommandFromEmbed(embed);
                        if (!Strings.isEmpty(command)) {
                            //build chat client
                            DiscordChatClient discordChatClient = new DiscordChatClient(event.getJDA());

                            //capture/process command
                            Scheduler.getScheduler().executeCommand(() -> {
                                DiscordBootstrap.this.runAndProcessCommands(
                                        CommandContext.getConfig().getPrefix(),
                                        command, event.getUser().getName(),
                                        responseBuilder, chatClientResponse -> {
                                    // If we don't reply in 3 seconds or less, we get "This interaction failed" errors in discord
                                    // so we use a special reply for embeds on the button event
                                    // https://jda.wiki/using-jda/troubleshooting/#this-interaction-failed-unknown-interaction
                                    discordChatClient.sendButtonReply(chatClientResponse, event);
                                });
                                return null;
                            });
                        }
                    });
                }

                @Override
                public void onGuildMessageReactionAdd(@Nonnull GuildMessageReactionAddEvent event) {
                    if (event.getReactionEmote().getName().equalsIgnoreCase(THUMBS_UP_EMOTE)) {
                        MessageHistory.MessageRetrieveAction me = event.getChannel().getHistoryAround(event.getMessageId(), 1);
                        me.queue(messageHistory -> {
                            List<Message> messageHistories = messageHistory.getRetrievedHistory();
                            messageLoop:
                            for (Message message : messageHistories) {
                                List<MessageEmbed> embeds = message.getEmbeds();
                                for (MessageEmbed.Field field : embeds.get(0).getFields()) {
                                    String name = field.getName();
                                    if (Strings.isEmpty(name)) {
                                        continue;
                                    }
                                    if (name.equals(ADD_MOVIE_COMMAND_FIELD_PREFIX) ||
                                            name.equals(ADD_SHOW_COMMAND_FIELD_PREFIX) ||
                                            name.equals(ADD_ARTIST_COMMAND_FIELD_PREFIX)) {
                                        //capture/process the command
                                        handleCommand(event.getJDA(), field.getValue(), event.getUser().getName(), event.getChannel().getName());
                                        break messageLoop;
                                    }
                                }
                            }
                        });
                    }
                    super.onGuildMessageReactionAdd(event);
                }

                @Override
                public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
                    if (event.getMessage().getType() != MessageType.APPLICATION_COMMAND && event.getMessage().getEmbeds().isEmpty()) {
                        handleCommand(event.getJDA(), event.getMessage().getContentStripped(), event.getAuthor().getName(), event.getChannel().getName());
                        LogManager.getLogger("com.botdarr.clients.discord").debug(event.getMessage().getContentRaw());
                    }
                    super.onMessageReceived(event);
                }

                private void handleCommand(JDA jda, String message, String author, String channelName) {
                    //build chat client
                    DiscordChatClient discordChatClient = new DiscordChatClient(jda);

                    //capture/process command
                    Scheduler.getScheduler().executeCommand(() -> {
                        DiscordBootstrap.this.runAndProcessCommands(CommandContext.getConfig().getPrefix(), message, author, responseBuilder, chatClientResponse -> {
                            discordChatClient.sendMessage(chatClientResponse, channelName);
                        });
                        return null;
                    });
                }

                private static final String THUMBS_UP_EMOTE = "\uD83D\uDC4D";
            }).build();
            config.getCommands().forEach(command -> jda.upsertCommand(convertCommandToCommandData(command)).queue());
            jda.awaitReady();
        } catch (Throwable e) {
            LogManager.getLogger("com.botdarr.clients.discord").error("Error caught during main", e);
            throw e;
        }
    }

    @Override
    public boolean isConfigured(Properties properties) {
        return !Strings.isBlank(properties.getProperty(Config.Constants.DISCORD_TOKEN)) &&
               !Strings.isBlank(properties.getProperty(Config.Constants.DISCORD_CHANNELS));
    }

    @Override
    public void validatePrefix(String configuredPrefix) {
        super.validatePrefix(configuredPrefix);
        if (configuredPrefix.equals("/")) {
            throw new RuntimeException("Cannot use / command prefix in discord since / command is used by discord slash commands");
        }
    }

    public String getCommandFromEmbed(MessageEmbed embed) {
        for(MessageEmbed.Field field : embed.getFields()) {
            if (field.getName() != null) {
                if (field.getName().equals(MOVIE_LOOKUP_FIELD)) {
                    return RadarrCommands.getAddMovieCommandStr(embed.getTitle(), Long.parseLong(field.getValue()));
                }
                if (field.getName().equals(SHOW_LOOKUP_FIELD)) {
                    return SonarrCommands.getAddShowCommandStr(Long.parseLong(field.getValue()));
                }
                if (field.getName().equals(ARTIST_LOOKUP_KEY_FIELD)) {
                    return LidarrCommands.getAddArtistCommandStr(embed.getTitle(), field.getValue());
                }
            }
        }
        return null;
    }

    public CommandData convertCommandToCommandData(Command command) {
        String description = command.getDescription();
        if (description.length() > 100) {
            description = description.substring(0, 97);
            description += "...";
        }
        CommandData commandData = new CommandData(DiscordResponseBuilder.formatForSlashCommand(command.getCommandText()), description);
        command.getInput().forEach(input -> {
            // all input is required by default
            commandData.addOption(OptionType.STRING, input, input, true);
        });
        return commandData;
    }
}
