package com.botdarr;

import com.botdarr.clients.ChatClientBootstrap;
import com.botdarr.clients.discord.DiscordBootstrap;
import com.botdarr.clients.matrix.MatrixBootstrap;
import com.botdarr.clients.slack.SlackBootstrap;
import com.botdarr.clients.telegram.TelegramBootstrap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.util.Strings;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.BiConsumer;

import static com.botdarr.commands.StatusCommand.*;

public class Config {
  private static volatile Config instance;

  private static Config getConfig() {
    if (instance == null) {
      synchronized (Config.class) {
        if (instance == null) {
          instance = new Config();
        }
      }
    }
    return instance;
  }

  private Config() {
    try (InputStream input = new FileInputStream(propertiesPath)) {
      properties = new Properties();
      properties.load(input);

      String logLevel = properties.getProperty(Constants.LOG_LEVEL);
      if (!Strings.isEmpty(logLevel)) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.getLoggers().forEach((s, loggerConfig) -> loggerConfig.setLevel(Level.valueOf(logLevel)));
        ctx.updateLoggers();
      }

      List<ChatClientBootstrap> availableBootstraps = Arrays.asList(
              new DiscordBootstrap(),
              new MatrixBootstrap(),
              new SlackBootstrap(),
              new TelegramBootstrap());
      for (ChatClientBootstrap possibleChatClientType : availableBootstraps) {
        if (possibleChatClientType.isConfigured(properties) && chatClientBootstrap != null) {
          throw new RuntimeException("You cannot configure more than one chat client");
        }
        if (possibleChatClientType.isConfigured(properties)) {
          chatClientBootstrap = possibleChatClientType;
        }
      }

      if (chatClientBootstrap == null) {
        throw new RuntimeException("You don't have any chat clients configured, please configure one");
      }

      this.isRaddarrEnabled =
        !Strings.isBlank(properties.getProperty(Constants.RADARR_URL)) &&
        !Strings.isBlank(properties.getProperty(Constants.RADARR_PATH)) &&
        !Strings.isBlank(properties.getProperty(Constants.RADARR_TOKEN)) &&
        !Strings.isBlank(properties.getProperty(Constants.RADARR_DEFAULT_PROFILE));

      if (!this.isRaddarrEnabled) {
        LOGGER.warn("Radarr commands are not enabled, make sure you set the radarr url, path, token, default profile");
      }

      this.isSonarrEnabled =
        !Strings.isBlank(properties.getProperty(Constants.SONARR_URL)) &&
        !Strings.isBlank(properties.getProperty(Constants.SONARR_PATH)) &&
        !Strings.isBlank(properties.getProperty(Constants.SONARR_TOKEN)) &&
        !Strings.isBlank(properties.getProperty(Constants.SONARR_DEFAULT_PROFILE));

      if (!this.isSonarrEnabled) {
        LOGGER.warn("Sonarr commands are not enabled, make sure you set the sonarr url, path, token, default profile");
      }

      this.isLidarrEnabled =
        !Strings.isBlank(properties.getProperty(Constants.LIDARR_URL)) &&
        !Strings.isBlank(properties.getProperty(Constants.LIDARR_PATH)) &&
        !Strings.isBlank(properties.getProperty(Constants.LIDARR_TOKEN)) &&
        !Strings.isBlank(properties.getProperty(Constants.LIDARR_DEFAULT_QUALITY_PROFILE)) &&
        !Strings.isBlank(properties.getProperty(Constants.LIDARR_DEFAULT_METADATA_PROFILE));

      if (!this.isLidarrEnabled) {
        LOGGER.warn("Lidarr commands are not enabled, make sure you set the lidarr url, path, token, default profile");
      }

      String configuredPrefix = properties.getProperty(Constants.COMMAND_PREFIX);
      if (!Strings.isEmpty(configuredPrefix)) {
        chatClientBootstrap.validatePrefix(configuredPrefix);
      }
    } catch (Exception ex) {
      LOGGER.error("Error loading properties file", ex);
      throw new RuntimeException(ex);
    }
  }

  public static String getProperty(String key) {
    return getConfig().properties.getProperty(key);
  }

  public static boolean isRadarrEnabled() {
    return getConfig().isRaddarrEnabled;
  }

  public static boolean isSonarrEnabled() {
    return getConfig().isSonarrEnabled;
  }

  public static boolean isSonarrV4Enabled() {
    String isSonarrV4 = Config.getProperty(Config.Constants.SONARR_V4);
    if (!Strings.isEmpty(isSonarrV4) &&
            !(isSonarrV4.equalsIgnoreCase("true") ||
             isSonarrV4.equalsIgnoreCase("false"))) {
      throw new RuntimeException("Sonarr V4 flag is not set to true/false");
    }
    return !Strings.isEmpty(isSonarrV4) && Boolean.parseBoolean(isSonarrV4);
  }

  public static boolean isLidarrEnabled() {
    return getConfig().isLidarrEnabled;
  }

  public static ChatClientBootstrap getChatClientBootstrap() {
    return getConfig().chatClientBootstrap;
  }

  public static List<String> getExistingItemBlacklistPaths() {
    return getCommaDelimitedList(getProperty(Constants.EXISTING_ITEMS_PATHS_BLACKLIST));
  }

  public static List<StatusEndPoint> getStatusEndpoints() {
    List<String> endpoints = getCommaDelimitedList(getProperty(Constants.STATUS_ENDPOINTS));
    List<StatusEndPoint> statusEndPoints = new ArrayList<>();
    if (!endpoints.isEmpty()) {
      for (String endpoint : endpoints) {
        String[] splitEndpoint = endpoint.split(":");
        if (splitEndpoint.length != 3) {
          LOGGER.warn("Status endpoint not formatted correctly, usage - name:hostname:port, endpoint=" + endpoint);
        }
        statusEndPoints.add(new StatusEndPoint(splitEndpoint[0], splitEndpoint[1], Integer.parseInt(splitEndpoint[2])));
      }
    }
    StatusEndPoint endpoint;
    if (isLidarrEnabled()) {
      endpoint = getDomain(Constants.LIDARR_URL, "lidarr");
      if (endpoint != null) {
        statusEndPoints.add(endpoint);
      }
    }
    if (isRadarrEnabled()) {
      endpoint = getDomain(Constants.RADARR_URL, "radarr");
      if (endpoint != null) {
        statusEndPoints.add(endpoint);
      }
    }
    if (isSonarrEnabled()) {
      endpoint = getDomain(Constants.SONARR_URL, "sonarr");
      if (endpoint != null) {
        statusEndPoints.add(endpoint);
      }
    }
    return statusEndPoints;
  }

  /**
   * @return The connection/read timeouts for all outbound requests (in ms)
   */
  public static int getTimeout() {
    try {
      return Integer.parseInt(Config.getProperty(Config.Constants.TIMEOUT));
    } catch (NumberFormatException e) {
      LOGGER.trace("Error parsing timeout, using default timeout", e);
    }
    return 5000;
  }

  /**
   * @return The command prefix
   */
  public static String getPrefix() {
    String configuredPrefix = Config.getProperty(Config.Constants.COMMAND_PREFIX);
    if (!Strings.isEmpty(configuredPrefix)) {
      return configuredPrefix;
    }
    return "!";
  }

  private static StatusEndPoint getDomain(String constant, String name) {
    try {
      URI uri = new URI(getProperty(constant));
      int port = uri.getPort();
      if (port == -1) {
        port = uri.getHost().startsWith("https") ? 443 : 80;
      }
      return new StatusEndPoint(name, uri.getHost(), port);
    } catch (URISyntaxException e) {
      LOGGER.error("Error trying to get uri for " + constant, e);
    }
    return null;
  }

  protected static List<String> getCommaDelimitedList(String list) {
    if (!Strings.isEmpty(list)) {
      if (list.contains(",")) {
        return Arrays.asList(list.split(","));
      } else {
        return new ArrayList<String>() {{add(list);}};
      }
    }
    return new ArrayList<>();
  }

  public static final class Constants {
    /**
     * The matrix bot username
     */
    public static final String MATRIX_USERNAME = "matrix-username";

    /**
     * The matrix bot password
     */
    public static final String MATRIX_PASSWORD = "matrix-password";

    /**
     * The matrix room for the bot
     */
    public static final String MATRIX_ROOM = "matrix-room";

    /**
     * The matrix home server for the bot
     */
    public static final String MATRIX_HOME_SERVER = "matrix-home-server-url";

    /**
     * The telegram auth token
     */
    public static final String TELEGRAM_TOKEN = "telegram-token";

    /**
     * The telegram private channel(s) to send notifications to
     */
    public static final String TELEGRAM_PRIVATE_CHANNELS = "telegram-private-channels";

    /**
     * The telegram private group(s) to send notifications to
     */
    public static final String TELEGRAM_PRIVATE_GROUPS = "telegram-private-groups";

    /**
     * The discord bot token
     */
    public static final String DISCORD_TOKEN = "discord-token";

    /**
     * The discord channel(s) to send notifications to
     */
    public static final String DISCORD_CHANNELS = "discord-channels";

    /**
     * The slack bot oauth token
     */
    public static final String SLACK_BOT_TOKEN = "slack-bot-token";

    /**
     * The slack app token for socket mode
     */
    public static final String SLACK_APP_TOKEN = "slack-app-token";

    /**
     * The slack channel(s) to send notifications to
     */
    public static final String SLACK_CHANNELS = "slack-channels";

    /**
     * The url to your radarr instance
     * (i.e., http://SOME_IP:PORT)
     */
    public static final String RADARR_URL = "radarr-url";

    /**
     * The api token for radarr
     */
    public static final String RADARR_TOKEN = "radarr-token";

    /**
     * The root file path to where you want radarr to download your movies
     */
    public static final String RADARR_PATH = "radarr-path";

    /**
     * The default quality profile you want radarr to use when adding movies
     */
    public static final String RADARR_DEFAULT_PROFILE = "radarr-default-profile";

    /**
     * The url base for radarr
     */
    public static final String RADARR_URL_BASE = "radarr-url-base";

    /**
     * The url to your sonarr instance
     * (i.e., http://SOME_IP:PORT)
     */
    public static final String SONARR_URL = "sonarr-url";

    /**
     * The api token for sonarr
     */
    public static final String SONARR_TOKEN = "sonarr-token";

    /**
     * The root file path to where you want sonarr to download your movies
     */
    public static final String SONARR_PATH = "sonarr-path";

    /**
     * The default quality profile you want sonarr to use when adding movies
     */
    public static final String SONARR_DEFAULT_PROFILE = "sonarr-default-profile";

    /**
     * The url base for sonarr
     */
    public static final String SONARR_URL_BASE = "sonarr-url-base";

    /**
     * Whether to turn on sonarr v4 support or not
     */
    public static final String SONARR_V4 = "sonarr-v4";

    /**
     * The url to your lidarr instance
     */
    public static final String LIDARR_URL = "lidarr-url";

    /**
     * The path to set on all added music in lidarr
     */
    public static final String LIDARR_PATH = "lidarr-path";

    /**
     * The api token for accessing lidarr
     */
    public static final String LIDARR_TOKEN = "lidarr-token";

    /**
     * The default quality profile you want lidarr to use when adding artists
     */
    public static final String LIDARR_DEFAULT_QUALITY_PROFILE = "lidarr-default-quality-profile";

    /**
     * The default metadata profile you want lidarr to use when adding artists
     */
    public static final String LIDARR_DEFAULT_METADATA_PROFILE = "lidarr-default-metadata-profile";

    /**
     * The url base for lidarr
     */
    public static final String LIDARR_URL_BASE = "lidarr-url-base";

    /**
     * The database name
     */
    public static final String DATABASE_NAME = "botdarr.db";

    /**
     * The max amount of movie requests per user per configured threshold
     */
    public static final String MAX_MOVIE_REQUESTS_PER_USER = "max-movie-requests-per-user";

    /**
     * The max amount of show requests per user per configured threshold
     */
    public static final String MAX_SHOW_REQUESTS_PER_USER = "max-show-requests-per-user";

    /**
     * The max amount of artist requests per user per configured threshold
     */
    public static final String MAX_ARTIST_REQUESTS_PER_USER = "max-artist-requests-per-user";

    /**
     * The type of threshold to enforce around request maximums (i.e., {@link com.botdarr.api.ApiRequestThreshold}
     */
    public static final String MAX_REQUESTS_THRESHOLD = "max-requests-threshold";

    /**
     * The max number of downloads to show to the user(s)
     */
    public static final String MAX_DOWNLOADS_TO_SHOW = "max-downloads-to-show";
    
    /**
     * The max number of results to show per search command
     */
    public static final String MAX_RESULTS_TO_SHOW = "max-results-to-show";

    /**
     * Interval of notifications in minutes.
     */
    public static final String NOTIFICATION_INTERVAL = "notification-interval";

    /**
     * The prefix for all commands
     */
    public static final String COMMAND_PREFIX = "command-prefix";

    /**
     * The paths of items to blacklist from searches
     */
    public static final String EXISTING_ITEMS_PATHS_BLACKLIST = "existing-item-paths-blacklist";

    /**
     * The additional status endpoints to check
     */
    public static final String STATUS_ENDPOINTS = "status-endpoints";

    /**
     * Connection/read timeouts for all outbound requests
     */
    public static final String TIMEOUT = "timeout";

    /**
     * Config for the log level
     */
    public static final String LOG_LEVEL = "log-level";
    public static final int VALUE_MAX_LENGTH = 1024;
  }

  private static String propertiesPath = "config/properties";
  private final Properties properties;
  private final boolean isRaddarrEnabled;
  private final boolean isSonarrEnabled;
  private final boolean isLidarrEnabled;
  private ChatClientBootstrap chatClientBootstrap = null;
  private static final Logger LOGGER = LogManager.getLogger();
}
