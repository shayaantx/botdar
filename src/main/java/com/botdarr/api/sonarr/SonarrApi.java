package com.botdarr.api.sonarr;

import com.botdarr.Config;
import com.botdarr.api.*;
import com.botdarr.commands.CommandContext;
import com.botdarr.commands.responses.*;
import com.botdarr.connections.ConnectionHelper;
import com.google.gson.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.util.Strings;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.botdarr.Config.Constants.VALUE_MAX_LENGTH;

public class SonarrApi implements Api {
  @Override
  public List<CommandResponse> downloads() {
    return getDownloadsStrategy().downloads();
  }

  public CommandResponse addWithId(String id) {
    return getAddStrategy().addWithId(id);
  }

  public List<CommandResponse> addWithTitle(String searchText) {
    return getAddStrategy().addWithSearchTitle(searchText);
  }

  public List<CommandResponse> lookup(String search, boolean findNew) {
    return new LookupStrategy<SonarrShow>(ContentType.SHOW) {

      @Override
      public SonarrShow lookupExistingItem(SonarrShow lookupItem) {
        return SONARR_CACHE.getExistingShowFromTvdbId(lookupItem.getTvdbId());
      }

      @Override
      public List<SonarrShow> lookup(String searchTerm) throws Exception {
        return lookupShows(searchTerm);
      }

      @Override
      public CommandResponse getExistingItem(SonarrShow existingItem) {
        return new ExistingShowResponse(existingItem);
      }

      @Override
      public CommandResponse getNewItem(SonarrShow lookupItem) {
        return new NewShowResponse(lookupItem);
      }

      @Override
      public boolean isPathBlacklisted(SonarrShow item) {
        return SonarrApi.this.isPathBlacklisted(item);
      }
    }.lookup(search, findNew);
  }

  public List<CommandResponse> getProfiles() {
    Collection<SonarrProfile> profiles = SONARR_CACHE.getQualityProfiles();
    if (profiles == null || profiles.isEmpty()) {
      return Collections.singletonList(new ErrorResponse("Found 0 profiles, please setup Sonarr with at least one profile"));
    }

    List<CommandResponse> profileMessages = new ArrayList<>();
    for (SonarrProfile sonarrProfile : profiles) {
      profileMessages.add(new ShowProfileResponse(sonarrProfile));
    }
    return profileMessages;
  }

  @Override
  public void cacheData() {
    new CacheProfileStrategy<SonarrProfile, String>() {
      @Override
      public void deleteFromCache(List<String> profilesAddUpdated) {
        SONARR_CACHE.removeDeletedProfiles(profilesAddUpdated);
      }

      @Override
      public List<SonarrProfile> getProfiles() {
        return ConnectionHelper.makeGetRequest(
                new SonarrUrls.SonarrRequestBuilder().buildGet(SonarrUrls.PROFILE),
                new ConnectionHelper.SimpleEntityResponseHandler<List<SonarrProfile>>() {
          @Override
          public List<SonarrProfile> onSuccess(String response) {
            List<SonarrProfile> sonarrProfiles = new ArrayList<>();
            JsonParser parser = new JsonParser();
            JsonArray json = parser.parse(response).getAsJsonArray();
            for (int i = 0; i < json.size(); i++) {
              if (Config.isSonarrV4Enabled()) {
                SonarrV4Profile sonarrProfile = new Gson().fromJson(json.get(i), SonarrV4Profile.class);
                sonarrProfiles.add(sonarrProfile);
                continue;
              }
              SonarrV3Profile sonarrProfile = new Gson().fromJson(json.get(i), SonarrV3Profile.class);
              sonarrProfiles.add(sonarrProfile);
            }
            return sonarrProfiles;
          }
        });
      }

      @Override
      public void addProfile(SonarrProfile profile) {
        SONARR_CACHE.addProfile(profile);
      }
    }.cacheData();

    new CacheContentStrategy<SonarrShow, Long>(new SonarrUrls.SonarrRequestBuilder().buildGet(SonarrUrls.SERIES_BASE)) {
      @Override
      public void deleteFromCache(List<Long> itemsAddedUpdated) {
        SONARR_CACHE.removeDeletedShows(itemsAddedUpdated);
      }

      @Override
      public Long addToCache(JsonElement cacheItem) {
        SonarrShow sonarrShow = new Gson().fromJson(cacheItem, SonarrShow.class);
        SONARR_CACHE.add(sonarrShow);
        return sonarrShow.getKey();
      }
    }.cacheData();
  }

  private AddStrategy<SonarrShow> getAddStrategy() {
    return new AddStrategy<SonarrShow>(ContentType.SHOW) {
      @Override
      public List<SonarrShow> lookupContent(String search) throws Exception {
        return lookupShows(search);
      }

      @Override
      public List<SonarrShow> lookupItemById(String id) throws Exception {
        return lookupShows("tvdb:" + id);
      }

      @Override
      public boolean doesItemExist(SonarrShow content) {
        return SONARR_CACHE.doesShowExist(content.getTitle());
      }

      @Override
      public String getItemId(SonarrShow item) {
        return String.valueOf(item.getTvdbId());
      }

      @Override
      public CommandResponse addContent(SonarrShow content) {
        return addShow(content);
      }

      @Override
      public CommandResponse getNewItemResponse(SonarrShow item) {
        return new NewShowResponse(item);
      }
    };
  }

  private SonarrEpisodeInformation getEpisode(long seriesId, long episodeId) {
    return ConnectionHelper.makeGetRequest(
      new SonarrUrls.SonarrRequestBuilder().buildGet(SonarrUrls.EPISODES_LOOKUP, new HashMap<String, String>() {{
        put("seriesId", String.valueOf(seriesId));
        put("episodeIds", String.valueOf(episodeId));
      }}),
      new ConnectionHelper.SimpleEntityResponseHandler<SonarrEpisodeInformation>() {
        @Override
        public SonarrEpisodeInformation onSuccess(String response) {
          if (response == null || response.isEmpty() || response.equals("{}")) {
            return null;
          }
          JsonArray array = JsonParser.parseString(response).getAsJsonArray();
          for (int i = 0; i < array.size(); i++) {
            SonarrQueueEpisode episode = new Gson().fromJson(array.get(i), SonarrQueueEpisode.class);
            if (episode.getId() != episodeId) {
              continue;
            }
            return new SonarrEpisodeInformation(episode.getSeasonNumber(), episode.getEpisodeNumber(), episode.getTitle(), episode.getOverview());
          }
          return null;
        }
      }
    );
  }

  private DownloadsStrategy getDownloadsStrategy() {
    return new DownloadsStrategy() {
      private ShowDownloadResponse getDownloadBasedApiV3Queue(SonarrQueue showQueue) {
        SonarrShow sonarrShow = SONARR_CACHE.getExistingShowFromSonarrId(showQueue.getSeriesId());
        if (sonarrShow == null) {
          LOGGER.warn("Could not load sonarr show from cache for id " + showQueue.getSeriesId());
          return null;
        }
        if (isPathBlacklisted(sonarrShow)) {
          LOGGER.warn("The following show is blacklisted: " + sonarrShow.getTitle() + " from being displayed in downloads");
          return null;
        }
        SonarrEpisodeInformation episodeInformation = SONARR_CACHE.getEpisode(showQueue.getSeriesId(), showQueue.getEpisodeId());
        if (episodeInformation == null) {
           episodeInformation = getEpisode(showQueue.getSeriesId(), showQueue.getEpisodeId());
           if (episodeInformation == null) {
             // if episode is still null, we can't display any download data
             LOGGER.error("Couldn't find download data in sonarr cache or sonarr api for " + showQueue.getSeriesId() + ", episode " + showQueue.getEpisodeId());
             return null;
           }
           // cache information for faster lookups since download data is displayed periodically
           SONARR_CACHE.addEpisode(showQueue.getSeriesId(), showQueue.getEpisodeId(), episodeInformation);
        }
        List<String> statusMessages = new ArrayList<>();
        for (SonarrQueueStatusMessages sonarrQueueStatusMessages : showQueue.getStatusMessages()) {
          statusMessages.add(sonarrQueueStatusMessages.getTitle());
        }
        String overview = episodeInformation.getOverview();
        if (overview.length() > VALUE_MAX_LENGTH) {
          overview = overview.substring(0, VALUE_MAX_LENGTH);
        }
        return new ShowDownloadResponse(new SonarrDownloadActivity(
          sonarrShow.getTitle() + ": " + episodeInformation.getTitle(),
          episodeInformation.getSeasonNumber(),
          episodeInformation.getEpisodeNumber(),
          showQueue.getQuality().getQuality().getName(),
          showQueue.getStatus(),
          showQueue.getTimeleft(),
          overview,
          statusMessages.toArray(new String[]{})
        ));
      }

      private ShowDownloadResponse getDownloadBasedApiQueue(SonarrQueue showQueue) {
        SonarrQueueEpisode episode = showQueue.getEpisode();
        SonarrShow sonarrShow = SONARR_CACHE.getExistingShowFromSonarrId(showQueue.getEpisode().getSeriesId());
        if (sonarrShow == null) {
          LOGGER.warn("Could not load sonarr show from cache for id " + showQueue.getEpisode().getSeriesId() + " title=" + showQueue.getSonarrQueueShow().getTitle());
          return null;
        }
        if (isPathBlacklisted(sonarrShow)) {
          LOGGER.warn("The following show is blacklisted: " + sonarrShow.getTitle() + " from being displayed in downloads");
          return null;
        }
        List<String> statusMessages = new ArrayList<>();
        for (SonarrQueueStatusMessages sonarrQueueStatusMessages : showQueue.getStatusMessages()) {
          statusMessages.add(sonarrQueueStatusMessages.getTitle());
        }
        String overview = episode.getOverview();
        if (!Strings.isEmpty(overview) && overview.length() > VALUE_MAX_LENGTH) {
          overview = overview.substring(0, VALUE_MAX_LENGTH);
        }
        return new ShowDownloadResponse(new SonarrDownloadActivity(
            sonarrShow.getTitle() + ": " + episode.getTitle(),
            episode.getSeasonNumber(),
            episode.getEpisodeNumber(),
            showQueue.getQuality().getQuality().getName(),
            showQueue.getStatus(),
            showQueue.getTimeleft(),
            overview,
            statusMessages.toArray(new String[]{})
          )
        );
      }

      @Override
      public CommandResponse getResponse(JsonElement rawElement) {
        SonarrQueue showQueue = new Gson().fromJson(rawElement, SonarrQueue.class);
        SonarrQueueEpisode episode = showQueue.getEpisode();
        // api/queue vs /api/v3/queue differ in results which makes the episode object not exist,
        // so we support both cause I have no idea why I originally used api/queue and if its still in use and will stay in use...
        //TODO: eventually get rid of the api/queue one when you're sure its not anything of value
        if (episode == null) {
          return getDownloadBasedApiV3Queue(showQueue);
        }
        return getDownloadBasedApiQueue(showQueue);
      }

      @Override
      public List<CommandResponse> getContentDownloads() {
        return ConnectionHelper.makeGetRequest(
          new SonarrUrls.SonarrRequestBuilder().buildGet(SonarrUrls.DOWNLOAD_BASE),
          new ConnectionHelper.SimpleCommandResponseHandler() {
            @Override
            public List<CommandResponse> onSuccess(String response) {
              if (response == null || response.isEmpty() || response.equals("{}") || response.equals("[]")) {
                return new ArrayList<>();
              }
              if (Config.isSonarrV4Enabled()) {
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                return parseContent(json.get("records").toString());
              }
              // my usage of sonarr v3 is goofy and doesn't use the actual /api/*/queue endpoint
              // so just let parseContent take the raw array
              return parseContent(response);
            }
          });
      }
    };
  }

  private CommandResponse addShow(SonarrShow sonarrShow) {
    String title = sonarrShow.getTitle();
    //make sure we specify where the show should get downloaded
    sonarrShow.setPath(Config.getProperty(Config.Constants.SONARR_PATH) + File.separator + title);
    //make sure the show is monitored
    sonarrShow.setMonitored(true);
    //make sure to have seasons stored in separate folders
    sonarrShow.setSeasonFolder(true);

    String sonarrProfileName = Config.getProperty(Config.Constants.SONARR_DEFAULT_PROFILE);
    SonarrProfile sonarrProfile = SONARR_CACHE.getProfile(sonarrProfileName.toLowerCase());
    if (sonarrProfile == null) {
      return new ErrorResponse("Could not find sonarr profile for default " + sonarrProfile);
    }
    sonarrShow.setQualityProfileId((int) sonarrProfile.getId());
    String username = CommandContext.getConfig().getUsername();
    ApiRequests apiRequests = new ApiRequests();
    ApiRequestType apiRequestType = ApiRequestType.SHOW;
    if (apiRequests.checkRequestLimits(apiRequestType) && !apiRequests.canMakeRequests(apiRequestType, username)) {
      ApiRequestThreshold requestThreshold = ApiRequestThreshold.valueOf(Config.getProperty(Config.Constants.MAX_REQUESTS_THRESHOLD));
      return new ErrorResponse("Could not add show, user " + username + " has exceeded max show requests for " + requestThreshold.getReadableName());
    }
    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      String json = new GsonBuilder().addSerializationExclusionStrategy(excludeUnnecessaryFields).create().toJson(sonarrShow, SonarrShow.class);
      HttpRequestBase post = new SonarrUrls.SonarrRequestBuilder().buildPost(SonarrUrls.SERIES_BASE, json).build();

      post.addHeader("content-type", "application/json");

      try (CloseableHttpResponse response = client.execute(post)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200 && statusCode != 201) {
          return new ErrorResponse("Could not add show, status-code=" + statusCode + ", reason=" + response.getStatusLine().getReasonPhrase());
        }
        //cache show after successful request
        SONARR_CACHE.add(sonarrShow);
        LogManager.getLogger("AuditLog").info("User " + username + " added " + title);
        apiRequests.auditRequest(apiRequestType, username, title);
        return new SuccessResponse("Show " + title + " was added successfully!");
      }
    } catch (IOException e) {
      LOGGER.error("Error trying to add show=" + title, e);
      return new ErrorResponse("Error adding show=" + title + ", error=" + e.getMessage());
    }
  }

  private List<SonarrShow> lookupShows(String search) throws Exception {
    return ConnectionHelper.makeGetRequest(
            new SonarrUrls.SonarrRequestBuilder().buildGet(SonarrUrls.LOOKUP_SERIES, new HashMap<String, String>() {{
              put("term", search);
            }}),
            new ConnectionHelper.SimpleEntityResponseHandler<List<SonarrShow>>() {
      @Override
      public List<SonarrShow> onSuccess(String response) {
        List<SonarrShow> shows = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonArray json = parser.parse(response).getAsJsonArray();
        for (int i = 0; i < json.size(); i++) {
          shows.add(new Gson().fromJson(json.get(i), SonarrShow.class));
        }
        return shows;
      }
    });
  }

  private final ExclusionStrategy excludeUnnecessaryFields = new ExclusionStrategy() {
    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {
      //profileId breaks the post request to /series for some reason and I don't believe its a required field
      return fieldAttributes.getName().equalsIgnoreCase("profileId");
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
      return false;
    }
  };

  private boolean isPathBlacklisted(SonarrShow item) {
    for (String path : Config.getExistingItemBlacklistPaths()) {
      if (item.getPath() != null && item.getPath().startsWith(path)) {
        return true;
      }
    }
    return false;
  }

  private static final SonarrCache SONARR_CACHE = new SonarrCache();
  public static final String ADD_SHOW_COMMAND_FIELD_PREFIX = "Add show command";
  public static final String SHOW_LOOKUP_FIELD = "TvdbId";
}
