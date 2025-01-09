package com.botdarr.clients.slack;

import com.botdarr.Config;
import com.botdarr.clients.ChatClient;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.groups.GroupsHistoryRequest;
import com.slack.api.methods.request.users.UsersInfoRequest;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import com.slack.api.model.Message;
import com.slack.api.model.User;
import com.slack.api.model.block.DividerBlock;
import com.slack.api.model.block.LayoutBlock;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.slack.api.socket_mode.SocketModeClient;
import com.slack.api.socket_mode.listener.EnvelopeListener;
import com.slack.api.socket_mode.listener.WebSocketMessageListener;
import com.slack.api.socket_mode.request.EventsApiEnvelope;
import com.slack.api.socket_mode.request.InteractiveEnvelope;
import com.slack.api.socket_mode.request.SlashCommandsEnvelope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlackChatClient implements ChatClient<SlackResponse> {
  public SlackChatClient(SocketModeClient socketClient) {
    this.socketClient = socketClient;
    this.socketClient.addWebSocketCloseListener((code, s) -> {
        connected.set(false);
        LOGGER.error("Error caught during slack close handler, reason=" + s + ",code=" + code);
    });
    this.socketClient.addWebSocketErrorListener(throwable -> {
      LOGGER.error("Error caught from slack error handler", throwable);
    });
  }

  public void addMessageHandler(EnvelopeListener<EventsApiEnvelope> messageHandler) {
    socketClient.addEventsApiEnvelopeListener(messageHandler);
    socketClient.addWebSocketMessageListener(new WebSocketMessageListener() {
        @Override
        public void handle(String ee) {
          int i = 0;
        }
    });
    socketClient.addInteractiveEnvelopeListener(new EnvelopeListener<InteractiveEnvelope>() {
        @Override
        public void handle(InteractiveEnvelope ff) {
          int i = 0;
        }
    });
    socketClient.addSlashCommandsEnvelopeListener(new EnvelopeListener<SlashCommandsEnvelope>() {
        @Override
        public void handle(SlashCommandsEnvelope fff) {
          int i = 0;
        }
    });
  }

  public void connect() throws Exception {
    this.socketClient.setAutoReconnectEnabled(true);
    this.socketClient.connect();
    while(true) {
      Thread.sleep(1000);
    }
  }

  public void sendMessage(SlackResponse chatClientResponse, String channel) {
    sendMessages(channelId -> {
      try {
        Slack.getInstance().methods().chatPostMessage(ChatPostMessageRequest.builder()
          .token(Config.getProperty(Config.Constants.SLACK_BOT_TOKEN))
          .blocks(chatClientResponse.getBlocks())
          .channel(channelId).build());
      } catch (Exception e) {
        LOGGER.error("Error sending slack message", e);
      }
    }, channel);
  }

  public void sendMessage(List<SlackResponse> chatClientResponses, String channel) {
    sendMessages(channelId -> {
      for (SlackResponse slackResponse : chatClientResponses) {
        try {
          List<LayoutBlock> blocks = slackResponse.getBlocks();
          blocks.add(DividerBlock.builder().build());
          Slack.getInstance().methods().chatPostMessage(ChatPostMessageRequest.builder()
            .token(Config.getProperty(Config.Constants.SLACK_BOT_TOKEN))
            .blocks(blocks)
            .channel(channelId).build());
          Thread.sleep(1000); //slack is rate limited
        } catch (Exception e) {
          LOGGER.error("Error sending slack message", e);
        }
      }
    }, channel);
  }

  public List<Message> getPublicMessages(SlackMessage slackMessage) throws IOException, SlackApiException {
    return Slack.getInstance().methods().conversationsHistory(ConversationsHistoryRequest.builder()
      .token(Config.getProperty(Config.Constants.SLACK_BOT_TOKEN))
      .channel(slackMessage.getItem().getChannel())
      .oldest(slackMessage.getItem().getTs())
      .inclusive(true)
      .limit(Integer.valueOf(1))
      .build()).getMessages();
  }

  public List<Message> getPrivateMessages(SlackMessage slackMessage) throws IOException, SlackApiException {
    return Slack.getInstance().methods().groupsHistory(GroupsHistoryRequest.builder()
      .token(Config.getProperty(Config.Constants.SLACK_BOT_TOKEN))
      .channel(slackMessage.getItem().getChannel())
      .oldest(slackMessage.getItem().getTs())
      .inclusive(true)
      .count(Integer.valueOf(1))
      .build()).getMessages();
  }

  public User getUser(String userId) {
    try {
      return Slack.getInstance().methods().usersInfo(UsersInfoRequest.builder()
        .user(userId)
        .token(Config.getProperty(Config.Constants.SLACK_BOT_TOKEN)).build()).getUser();
    } catch (Exception e) {
      LOGGER.error("Error getting user", e);
      throw new RuntimeException("Error getting user");
    }
  }

  private void sendMessages(MessageSender messageSender, String targetChannel) {
    try {
      Map<String, String> conversationNamesToIds = new HashMap<>();
      ConversationsListResponse conversationsListResponse =
        Slack.getInstance().methods().conversationsList(ConversationsListRequest.builder()
          .token(Config.getProperty(Config.Constants.SLACK_BOT_TOKEN))
          .types(Arrays.asList(ConversationType.PRIVATE_CHANNEL, ConversationType.PUBLIC_CHANNEL)).build());
      for (Conversation conversation : conversationsListResponse.getChannels()) {
        conversationNamesToIds.put(conversation.getName(), conversation.getId());
      }

      Set<String> supportedSlackChannels = Sets.newHashSet(Splitter.on(',').trimResults().split(Config.getProperty(Config.Constants.SLACK_CHANNELS)));
      for (String slackChannel : supportedSlackChannels) {
        String channelId = conversationNamesToIds.get(slackChannel);
        if (Strings.isBlank(channelId)) {
          continue;
        }
        if (targetChannel != null && !channelId.equalsIgnoreCase(targetChannel)) {
          continue;
        }
        messageSender.send(channelId);
      }
    } catch (Exception e) {
      LOGGER.error("Error sending slack messages", e);
    }
  }

  @Override
  public void sendToConfiguredChannels(List<SlackResponse> chatClientResponses) {
    sendMessage(chatClientResponses, null);
  }

  @Override
  public void cleanup() {
    // nothing to cleanup
  }

  private interface MessageSender {
    void send(String channel);
  }

  private AtomicBoolean connected = new AtomicBoolean(false);

  private final SocketModeClient socketClient;
  private static final Logger LOGGER = LogManager.getLogger("SlackLog");
}
