package com.botdarr.clients.slack;

import com.botdarr.Config;
import com.botdarr.clients.ChatClientBootstrap;
import com.botdarr.clients.ChatClientResponseBuilder;
import com.botdarr.commands.CommandContext;
import com.botdarr.scheduling.Scheduler;
import com.slack.api.Slack;
import com.slack.api.model.Message;
import com.slack.api.model.User;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.rtm.RTMMessageHandler;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slack.api.socket_mode.listener.EnvelopeListener;
import com.slack.api.socket_mode.request.EventsApiEnvelope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.botdarr.api.radarr.RadarrApi.ADD_MOVIE_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.sonarr.SonarrApi.ADD_SHOW_COMMAND_FIELD_PREFIX;

public class SlackBootstrap extends ChatClientBootstrap {

    @Override
    public void init() throws Exception {
        JsonParser jsonParser = new JsonParser();
        SlackChatClient slackChatClient = new SlackChatClient(Slack.getInstance().socketMode(Config.getProperty(Config.Constants.SLACK_APP_TOKEN)));

        ChatClientResponseBuilder<SlackResponse> responseChatClientResponseBuilder = new SlackResponseBuilder();
        ChatClientBootstrap.ApisAndCommandConfig config = buildConfig();

        slackChatClient.addMessageHandler(new EnvelopeListener<EventsApiEnvelope>() {
            @Override
            public void handle(EventsApiEnvelope eventsApiEnvelope) {
                JsonObject json = jsonParser.parse(eventsApiEnvelope.getPayload().toString()).getAsJsonObject();
                SlackEventContainer slackEventContainer = new Gson().fromJson(json, SlackEventContainer.class);
                SlackMessage slackMessage = slackEventContainer.getEvent();
                if (slackMessage.getType() != null) {
                    if (slackMessage.getType().equalsIgnoreCase("message")) {
                        User user = slackChatClient.getUser(slackMessage.getUserId());
                        if (user == null) {
                            LOGGER.debug("Could not find user for slack message " + slackMessage);
                            return;
                        }
                        handleCommand(slackMessage.getText(), user.getName(), slackMessage.getChannel());
                    } else if (slackMessage.getType().equalsIgnoreCase("reaction_added") && slackMessage.getReaction().equalsIgnoreCase("+1")) {
                        //thumbsup = +1 in slack for some reason
                        try {
                            //search public channels first
                            List<Message> conversationMessages = slackChatClient.getPublicMessages(slackMessage);
                            if (conversationMessages == null || conversationMessages.isEmpty()) {
                                //check private channels if necessary
                                conversationMessages = slackChatClient.getPrivateMessages(slackMessage);
                            }
                            if (conversationMessages != null) {
                                conversationMessageLoop:
                                for (com.slack.api.model.Message conversationMessage : conversationMessages) {
                                    for (LayoutBlock layoutBlock : conversationMessage.getBlocks()) {
                                        if (layoutBlock.getType().equals("section")) {
                                            SectionBlock sectionBlock = (SectionBlock) layoutBlock;
                                            if (sectionBlock.getText() instanceof MarkdownTextObject) {
                                                String markdownText = ((MarkdownTextObject) sectionBlock.getText()).getText();
                                                if (markdownText != null &&
                                                        (markdownText.startsWith(ADD_MOVIE_COMMAND_FIELD_PREFIX) || markdownText.startsWith(ADD_SHOW_COMMAND_FIELD_PREFIX))) {
                                                    String postProcessedCommand = markdownText
                                                            .replaceAll(ADD_MOVIE_COMMAND_FIELD_PREFIX + " - ", "")
                                                            .replaceAll(ADD_SHOW_COMMAND_FIELD_PREFIX + " - ", "");
                                                    handleCommand(postProcessedCommand, slackChatClient.getUser(slackMessage.getUserId()).getName(), slackMessage.getItem().getChannel());
                                                    break conversationMessageLoop;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LogManager.getLogger("com.botdarr.clients.slack").error("Error fetching conversation history", e);
                        }
                    }
                }
                LogManager.getLogger("com.botdarr.clients.slack").debug(json);
            }

            private void handleCommand(String text, String userId, String channel) {
                Scheduler.getScheduler().executeCommand(() -> {
                    SlackBootstrap.this.runAndProcessCommands(CommandContext.getConfig().getPrefix(), text, userId, responseChatClientResponseBuilder, chatClientResponse -> {
                        slackChatClient.sendMessage(chatClientResponse, channel);
                    });
                    return null;
                });
            }
        });
        //start the scheduler threads that send notifications and cache data periodically
        initScheduling(slackChatClient, responseChatClientResponseBuilder, config.getApis());

        slackChatClient.connect();
    }

    @Override
    public boolean isConfigured(Properties properties) {
        String slackChannels = properties.getProperty(Config.Constants.SLACK_CHANNELS);
        if (!Strings.isBlank(properties.getProperty(Config.Constants.SLACK_BOT_TOKEN)) &&
            !Strings.isBlank(slackChannels)) {
            Set<String> supportedSlackChannels =
                    Sets.newHashSet(Splitter.on(',').trimResults().split(slackChannels));
            String regex = "^[a-zA-Z0-9-]+$";
            for (String slackChannel : supportedSlackChannels) {
                if (!slackChannel.matches(regex)) {
                    LOGGER.warn("Slack channel {} cannot have special characters", slackChannel);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void validatePrefix(String configuredPrefix) {
        super.validatePrefix(configuredPrefix);
        if (configuredPrefix.equals("/")) {
            throw new RuntimeException("Cannot use / command prefix in slack since /help command was deprecated by slack");
        }
    }
    private static final Logger LOGGER = LogManager.getLogger();
}
