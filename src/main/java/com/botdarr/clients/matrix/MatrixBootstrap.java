package com.botdarr.clients.matrix;

import com.botdarr.Config;
import com.botdarr.clients.ChatClientResponseBuilder;
import com.botdarr.clients.ChatClientBootstrap;
import com.botdarr.commands.CommandContext;
import com.botdarr.scheduling.Scheduler;
import org.apache.logging.log4j.util.Strings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

public class MatrixBootstrap extends ChatClientBootstrap {

    @Override
    public void init() throws Exception {
        MatrixChatClient chatClient = new MatrixChatClient();
        ChatClientResponseBuilder<MatrixResponse> responseChatClientResponseBuilder = new MatrixResponseBuilder();
        ChatClientBootstrap.ApisAndCommandConfig config = buildConfig();
        initScheduling(chatClient, responseChatClientResponseBuilder, config.getApis());
        chatClient.addListener((roomId, sender, content) -> {
            Scheduler.getScheduler().executeCommand(() -> {
                MatrixBootstrap.this.runAndProcessCommands(CommandContext.getConfig().getPrefix(), content, sender, responseChatClientResponseBuilder, chatClientResponse -> {
                    chatClient.sendMessage(chatClientResponse, roomId);
                });
                return null;
            });
        });
        chatClient.listen();
    }

    private boolean isValidURL(String urlString) {
        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    @Override
    public boolean isConfigured(Properties properties) {
        final boolean isConfigured =
                !Strings.isBlank(properties.getProperty(Config.Constants.MATRIX_USERNAME)) &&
                        !Strings.isBlank(properties.getProperty(Config.Constants.MATRIX_PASSWORD)) &&
                        !Strings.isBlank(properties.getProperty(Config.Constants.MATRIX_ROOM)) &&
                        !Strings.isBlank(properties.getProperty(Config.Constants.MATRIX_HOME_SERVER));

        if (isConfigured && !isValidURL(properties.getProperty(Config.Constants.MATRIX_HOME_SERVER))) {
            throw new RuntimeException("Matrix home server must be a url");
        }

        return isConfigured;
    }

    @Override
    public void validatePrefix(String configuredPrefix) {
        super.validatePrefix(configuredPrefix);
        if (configuredPrefix.equals("/")) {
            throw new RuntimeException("Cannot use / command prefix in matrix since /help command is used by element by default");
        }
    }
}
