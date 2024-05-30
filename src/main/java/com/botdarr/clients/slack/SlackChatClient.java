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
import com.slack.api.rtm.RTMClient;
import com.slack.api.rtm.RTMMessageHandler;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlackChatClient implements ChatClient<SlackResponse> {
  public SlackChatClient(RTMClient rtmClient) {
    this.rtm = rtmClient;
    rtm.addCloseHandler(reason -> {
      connected.set(false);
      LOGGER.error("Error caught during slack close handler, reason=" +  reason.toString());
    });
    rtm.addErrorHandler(reason -> {
      LOGGER.error("Error caught from slack error handler", reason);
    });
  }

  public void addMessageHandler(RTMMessageHandler messageHandler) {
    rtm.addMessageHandler(messageHandler);
  }

  public void connect() throws Exception {
    // must connect within 30 seconds after establishing wss endpoint
    this.rtm.connect();
    while(true) {
      //set state of whether we are connected or not (jslack doesn't expose session in rtm client so we need our own state)
      connected.set(true);
      while (connected.get()) {
        Thread.sleep(1000);
      }
      //if we for some reason stop being connected, reconnect and retry
      this.rtm.reconnect();
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
      .token(Config.getProperty(Config.Constants.SLACK_USER_TOKEN))
      .channel(slackMessage.getItem().getChannel())
      .oldest(slackMessage.getItem().getTs())
      .inclusive(true)
      .limit(1)
      .build()).getMessages();
  }

  public List<Message> getPrivateMessages(SlackMessage slackMessage) throws IOException, SlackApiException {
    return Slack.getInstance().methods().groupsHistory(GroupsHistoryRequest.builder()
      .token(Config.getProperty(Config.Constants.SLACK_USER_TOKEN))
      .channel(slackMessage.getItem().getChannel())
      .oldest(slackMessage.getItem().getTs())
      .inclusive(true)
      .count(1)
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

  private final RTMClient rtm;
  private static final Logger LOGGER = LogManager.getLogger("SlackLog");
}
