package com.botdarr.api.radarr;

import com.botdarr.Config;
import com.botdarr.api.*;
import com.botdarr.commands.CommandContext;
import com.botdarr.commands.responses.*;
import com.botdarr.connections.ConnectionHelper;
import com.google.gson.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

public class RadarrApi implements Api {
  public List<CommandResponse> lookup(String search, boolean findNew) {
    return new LookupStrategy<RadarrMovie>(ContentType.MOVIE) {

      @Override
      public RadarrMovie lookupExistingItem(RadarrMovie lookupItem) {
        return RADARR_CACHE.getExistingMovie(lookupItem.getTmdbId());
      }

      @Override
      public List<RadarrMovie> lookup(String searchTerm) throws Exception {
        return lookupMovies(searchTerm);
      }

      @Override
      public CommandResponse getExistingItem(RadarrMovie existingItem) {
        return new ExistingMovieResponse(existingItem);
      }

      @Override
      public CommandResponse getNewItem(RadarrMovie lookupItem) {
        return new NewMovieResponse(lookupItem);
      }

      @Override
      public boolean isPathBlacklisted(RadarrMovie item) {
        return RadarrApi.this.isPathBlacklisted(item);
      }
    }.lookup(search, findNew);
  }

  @Override
  public List<CommandResponse> downloads() {
    return getDownloadsStrategy().downloads();
  }

  public List<CommandResponse> addWithTitle(String searchText) {
    return getAddStrategy().addWithSearchTitle(searchText);
  }

  public CommandResponse addWithId(String searchText, String tmdbId) {
    return getAddStrategy().addWithSearchId(searchText, tmdbId);
  }

  public List<CommandResponse> getProfiles() {
    Collection<RadarrProfile> profiles = RADARR_CACHE.getQualityProfiles();
    if (profiles == null || profiles.isEmpty()) {
      return Collections.singletonList(new ErrorResponse("Found 0 profiles, please setup Radarr with at least one profile"));
    }

    List<CommandResponse> profileMessages = new ArrayList<>();
    for (RadarrProfile radarrProfile : profiles) {
      profileMessages.add(new MovieProfileResponse(radarrProfile));
    }
    return profileMessages;
  }

  @Override
  public void cacheData() {
    new CacheProfileStrategy<RadarrProfile, String>() {
      @Override
      public void deleteFromCache(List<String> profilesAddUpdated) {
        RADARR_CACHE.removeDeletedProfiles(profilesAddUpdated);
      }

      @Override
      public List<RadarrProfile> getProfiles() {
        return ConnectionHelper.makeGetRequest(
                new RadarrUrls.RadarrV3RequestBuilder().buildGet(RadarrUrls.PROFILE_BASE),
                new ConnectionHelper.SimpleEntityResponseHandler<List<RadarrProfile>>() {
          @Override
          public List<RadarrProfile> onSuccess(String response) {
            List<RadarrProfile> radarrProfiles = new ArrayList<>();
            JsonParser parser = new JsonParser();
            JsonArray json = parser.parse(response).getAsJsonArray();
            for (int i = 0; i < json.size(); i++) {
              RadarrProfile radarrProfile = new Gson().fromJson(json.get(i), RadarrProfile.class);
              radarrProfiles.add(radarrProfile);
            }
            return radarrProfiles;
          }
        });
      }

      @Override
      public void addProfile(RadarrProfile profile) {
        RADARR_CACHE.addProfile(profile);
      }
    }.cacheData();

    new CacheContentStrategy<RadarrMovie, Long>(new RadarrUrls.RadarrV3RequestBuilder().buildGet(RadarrUrls.MOVIE_BASE)) {
      @Override
      public void deleteFromCache(List<Long> itemsAddedUpdated) {
        RADARR_CACHE.removeDeletedMovies(itemsAddedUpdated);
      }

      @Override
      public Long addToCache(JsonElement cacheItem) {
        RadarrMovie radarrMovie = new Gson().fromJson(cacheItem, RadarrMovie.class);
        RADARR_CACHE.add(radarrMovie);
        return radarrMovie.getKey();
      }
    }.cacheData();
  }

  public List<CommandResponse> discover() {
    return ConnectionHelper.makeGetRequest(
            new RadarrUrls.RadarrV3RequestBuilder().buildGet(RadarrUrls.DISCOVER_MOVIES, new HashMap<String, String>() {{
              put("includeRecommendations", "true");
            }}),
            new ConnectionHelper.SimpleEntityResponseHandler<List<CommandResponse>>() {
      @Override
      public List<CommandResponse> onSuccess(String response) throws Exception {
        List<CommandResponse> recommendedMovies = new ArrayList<>();
        if (response == null || response.isEmpty() || response.equalsIgnoreCase("[]")) {
          LOGGER.warn("Found no response when looking for movie recommendations");
          return Collections.emptyList();
        }
        JsonParser parser = new JsonParser();
        JsonArray json = parser.parse(response).getAsJsonArray();

        for (int i = 0; i < json.size(); i++) {
          if (i == new ApiRequests().getMaxResultsToShow()) {
            //don't show more than configured max results to show
            break;
          }
          RadarrMovie radarrMovie = new Gson().fromJson(json.get(i), RadarrMovie.class);
          recommendedMovies.add(new DiscoverMovieResponse(radarrMovie));
        }
        return recommendedMovies;
      }
    });
  }

  private DownloadsStrategy getDownloadsStrategy() {
    return new DownloadsStrategy() {
      @Override
      public CommandResponse getResponse(JsonElement rawElement) {
        RadarrQueue radarrQueue = new Gson().fromJson(rawElement, RadarrQueue.class);
        RadarrMovie radarrMovie = RADARR_CACHE.getExistingMovieWithRadarrId(radarrQueue.getMovieId());
        if (radarrMovie == null) {
          LOGGER.warn("Could not load radarr movie from cache for id " + radarrQueue.getMovieId() + " title=" + radarrQueue.getTitle());
          return null;
        }
        //the radarr queue title is the title of the actual download, instead of movie title
        //so we get the real value here
        radarrQueue.setTitle(radarrMovie.getTitle());
        if (isPathBlacklisted(radarrMovie)) {
          //skip any radarr queue items tied to blacklisted content
          return null;
        }

        return new MovieDownloadResponse(radarrQueue);
      }

      @Override
      public List<CommandResponse> getContentDownloads() {
        return ConnectionHelper.makeGetRequest(
                new RadarrUrls.RadarrV3RequestBuilder().buildGet(RadarrUrls.DOWNLOAD_BASE),
                new ConnectionHelper.SimpleCommandResponseHandler() {
          @Override
          public List<CommandResponse> onSuccess(String response) {
            if (response == null || response.isEmpty() || response.equals("{}")) {
              return new ArrayList<>();
            }
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return parseContent(json.get("records").toString());
          }
        });
      }
    };
  }

  private AddStrategy<RadarrMovie> getAddStrategy() {
    return new AddStrategy<RadarrMovie>(ContentType.MOVIE) {
      @Override
      public List<RadarrMovie> lookupContent(String search)  throws Exception {
        return lookupMovies(search);
      }

      @Override
      public List<RadarrMovie> lookupItemById(String id) throws Exception {
        return lookupMoviesById(id);
      }

      @Override
      public boolean doesItemExist(RadarrMovie content) {
        return RADARR_CACHE.doesMovieExist(content.getTitle());
      }

      @Override
      public String getItemId(RadarrMovie item) {
        return String.valueOf(item.getTmdbId());
      }

      @Override
      public CommandResponse addContent(RadarrMovie content) {
        return addMovie(content);
      }

      @Override
      public CommandResponse getNewItemResponse(RadarrMovie item) {
        return new NewMovieResponse(item);
      }
    };
  }

  private CommandResponse addMovie(RadarrMovie radarrMovie) {
    //make sure we specify where the movie should get downloaded
    radarrMovie.setRootFolderPath(Config.getProperty(Config.Constants.RADARR_PATH));
    //make sure the movie is monitored
    radarrMovie.setMonitored(true);

    String radarrProfileName = Config.getProperty(Config.Constants.RADARR_DEFAULT_PROFILE);
    RadarrProfile radarrProfile = RADARR_CACHE.getProfile(radarrProfileName.toLowerCase());
    if (radarrProfile == null) {
      return new ErrorResponse("Could not find radarr profile for default " + radarrProfileName);
    }
    radarrMovie.setQualityProfileId((int) radarrProfile.getId());

    try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
      String json = new Gson().toJson(radarrMovie, RadarrMovie.class);
      HttpRequestBase post = new RadarrUrls.RadarrV3RequestBuilder().buildPost(RadarrUrls.MOVIE_BASE, json).build();

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Client request=" + post);
        LOGGER.debug("Client data=" + json);
      }

      String username = CommandContext.getConfig().getUsername();
      ApiRequests apiRequests = new ApiRequests();
      ApiRequestType apiRequestType = ApiRequestType.MOVIE;
      if (apiRequests.checkRequestLimits(apiRequestType) && !apiRequests.canMakeRequests(apiRequestType, username)) {
        ApiRequestThreshold requestThreshold = ApiRequestThreshold.valueOf(Config.getProperty(Config.Constants.MAX_REQUESTS_THRESHOLD));
        return new ErrorResponse("Could not add movie, user " + username + " has exceeded max movie requests for " + requestThreshold.getReadableName());
      }
      try (CloseableHttpResponse response = client.execute(post)) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Response=" + response.toString());
          LOGGER.debug("Response content=" + IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));
          LOGGER.debug("Reason=" + response.getStatusLine().toString());
        }
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200 && statusCode != 201) {
          return new ErrorResponse("Could not add movie, status-code=" + statusCode + ", reason=" + response.getStatusLine().getReasonPhrase());
        }
        //cache data after a successful request
        RADARR_CACHE.add(radarrMovie);
        LogManager.getLogger("AuditLog").info("User " + username + " added " + radarrMovie.getTitle());
        apiRequests.auditRequest(apiRequestType, username, radarrMovie.getTitle());
        return new SuccessResponse("Movie " + radarrMovie.getTitle() + " was added successfully!");
      }
    } catch (IOException e) {
      LOGGER.error("Error trying to add movie", e);
      return new ErrorResponse("Error adding movie, error=" + e.getMessage());
    }
  }

  private List<RadarrMovie> lookupMovies(String search) throws Exception {
    return ConnectionHelper.makeGetRequest(
            new RadarrUrls.RadarrV3RequestBuilder().buildGet(RadarrUrls.MOVIE_LOOKUP, new HashMap<String, String>() {{
              put("term", search);
            }}),
            new ConnectionHelper.SimpleEntityResponseHandler<List<RadarrMovie>>() {
      @Override
      public List<RadarrMovie> onSuccess(String response) {
        List<RadarrMovie> movies = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonArray json = parser.parse(response).getAsJsonArray();
        for (int i = 0; i < json.size(); i++) {
          movies.add(new Gson().fromJson(json.get(i), RadarrMovie.class));
        }
        return movies;
      }
    });
  }

  private List<RadarrMovie> lookupMoviesById(String tmdbid) throws Exception {
    return ConnectionHelper.makeGetRequest(
            new RadarrUrls.RadarrV3RequestBuilder().buildGet(RadarrUrls.MOVIE_LOOKUP_TMDB, new HashMap<String, String>() {{
              put("tmdbId", tmdbid);
            }}),
            new ConnectionHelper.SimpleEntityResponseHandler<List<RadarrMovie>>() {
      @Override
      public List<RadarrMovie> onSuccess(String response) {
        List<RadarrMovie> movies = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(response).getAsJsonObject();
        movies.add(new Gson().fromJson(json, RadarrMovie.class));
        return movies;
      }
    });
  }

  private boolean isPathBlacklisted(RadarrMovie item) {
    for (String path : Config.getExistingItemBlacklistPaths()) {
      if (item.getPath() != null && item.getPath().startsWith(path)) {
        return true;
      }
    }
    return false;
  }

  private static RadarrCache RADARR_CACHE = new RadarrCache();
  public static final String ADD_MOVIE_COMMAND_FIELD_PREFIX = "Add movie command";
  public static final String MOVIE_LOOKUP_FIELD = "TmdbId";
}
