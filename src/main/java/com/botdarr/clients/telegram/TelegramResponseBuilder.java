package com.botdarr.clients.telegram;

import com.botdarr.Config;
import com.botdarr.api.lidarr.LidarrArtist;
import com.botdarr.api.lidarr.LidarrCommands;
import com.botdarr.api.lidarr.LidarrQueueRecord;
import com.botdarr.api.lidarr.LidarrQueueStatusMessage;
import com.botdarr.api.radarr.*;
import com.botdarr.api.sonarr.*;
import com.botdarr.clients.ChatClientResponseBuilder;
import com.botdarr.commands.*;
import com.botdarr.commands.responses.*;
import com.botdarr.utilities.ListUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import j2html.tags.DomContent;
import org.apache.logging.log4j.util.Strings;

import static com.botdarr.api.lidarr.LidarrApi.ADD_ARTIST_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.radarr.RadarrApi.ADD_MOVIE_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.sonarr.SonarrApi.ADD_SHOW_COMMAND_FIELD_PREFIX;
import static com.botdarr.commands.StatusCommand.STATUS_COMMAND;
import static com.botdarr.commands.StatusCommand.STATUS_COMMAND_DESCRIPTION;
import static j2html.TagCreator.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TelegramResponseBuilder implements ChatClientResponseBuilder<TelegramResponse> {
  @Override
  public TelegramResponse build(HelpResponse helpResponse) {
    try {
      List<DomContent> domContents = new ArrayList<>();
      domContents.add(code("Version: " + ChatClientResponseBuilder.getVersion()));
      domContents.add(u(b("*Commands*")));
      boolean radarrEnabled = Config.isRadarrEnabled();
      boolean sonarrEnabled = Config.isSonarrEnabled();
      boolean lidarrEnabled = Config.isLidarrEnabled();
      if (radarrEnabled) {
        domContents.add(text(RadarrCommands.getHelpMovieCommandStr() + " - Shows all the commands for movies"));
      }
      if (sonarrEnabled) {
        domContents.add(text(SonarrCommands.getHelpShowCommandStr() + " - Shows all the commands for shows"));
      }
      if (lidarrEnabled) {
        domContents.add(text(LidarrCommands.getHelpCommandStr() + " - Shows all the commands for music"));
      }
      if (!radarrEnabled && !sonarrEnabled) {
        domContents.add(b("*No radarr or sonarr or lidarr commands configured, check your properties file and logs*"));
      }
      if (!Config.getStatusEndpoints().isEmpty()) {
        domContents.add(text(CommandContext.getConfig().getPrefix() + STATUS_COMMAND + " - " + STATUS_COMMAND_DESCRIPTION));
      }
      return new TelegramResponse(domContents);
    } catch (IOException e) {
      throw new RuntimeException("Error getting help response", e);
    }
  }

  @Override
  public TelegramResponse build(MusicHelpResponse musicHelpResponse) {
    return new TelegramResponse(getListOfCommands(musicHelpResponse.getLidarrCommands()));
  }

  @Override
  public TelegramResponse build(MoviesHelpResponse moviesHelpResponse) {
    return new TelegramResponse(getListOfCommands(moviesHelpResponse.getRadarrCommands()));
  }

  @Override
  public TelegramResponse build(ShowsHelpResponse showsHelpResponse) {
    return new TelegramResponse(getListOfCommands(showsHelpResponse.getSonarrCommands()));
  }

  @Override
  public TelegramResponse build(ShowResponse showResponse) {
    SonarrShow show = showResponse.getShow();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("*Title* - " + show.getTitle()));
    domContents.add(code("TvdbId - " + show.getTvdbId()));
    domContents.add(u(ADD_SHOW_COMMAND_FIELD_PREFIX + " - " +  SonarrCommands.getAddShowCommandStr(show.getTvdbId())));
    domContents.add(a(show.getRemoteImage()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(MusicArtistResponse musicArtistResponse) {
    LidarrArtist lidarrArtist = musicArtistResponse.getArtist();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("*Artist Name* - " + lidarrArtist.getArtistName()));
    domContents.add(code("Id - " + lidarrArtist.getForeignArtistId()));
    domContents.add(u(ADD_ARTIST_COMMAND_FIELD_PREFIX + " - " +  LidarrCommands.getAddArtistCommandStr(lidarrArtist.getArtistName(), lidarrArtist.getForeignArtistId())));
    domContents.add(a(lidarrArtist.getRemoteImage()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(ShowDownloadResponse showDownloadResponse) {
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b(showDownloadResponse.getShowQueue().getTitle()));
    String queueDetails = "Season/Episode - " + "S" + showDownloadResponse.getShowQueue().getSeasonNumber() + "E" + showDownloadResponse.getShowQueue().getEpisodeNumber() + "\n" +
            "Quality - " + showDownloadResponse.getShowQueue().getQualityProfileName() + "\n" +
            "Status - " + showDownloadResponse.getShowQueue().getStatus() + "\n" +
            "Time Left - " + (showDownloadResponse.getShowQueue().getTimeleft() == null ? "unknown" : showDownloadResponse.getShowQueue().getTimeleft()) + "\n" +
            "Overview - " + showDownloadResponse.getShowQueue().getOverview() + "\n";
    domContents.add(code(queueDetails));

    if (showDownloadResponse.getShowQueue().getStatusMessages() != null) {
      StringBuilder statusMessageBuilder = new StringBuilder();
      for (String statusMessage : showDownloadResponse.getShowQueue().getStatusMessages()) {
        statusMessageBuilder.append("Download Message - ").append(statusMessage).append("\n");
      }
      domContents.add(code(statusMessageBuilder.toString()));
    }
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(MovieDownloadResponse movieDownloadResponse) {
    RadarrQueue radarrQueue = movieDownloadResponse.getRadarrQueue();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b(radarrQueue.getTitle()));
    StringBuilder details = new StringBuilder();
    details.append("Quality - ").append(radarrQueue.getQuality().getQuality().getName()).append("\n");
    details.append("Status - ").append(radarrQueue.getStatus()).append("\n");
    details.append("Time Left - ").append(radarrQueue.getTimeleft() == null ? "unknown" : radarrQueue.getTimeleft() + "\n");
    if (radarrQueue.getStatusMessages() != null) {
      for (RadarrQueueStatusMessages statusMessage : radarrQueue.getStatusMessages()) {
        for (String message : statusMessage.getMessages()) {
          details.append("Download message - ").append(message).append("\n");
        }
      }
    }
    domContents.add(code(details.toString()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(MusicArtistDownloadResponse musicArtistDownloadResponse) {
    LidarrQueueRecord lidarrQueueRecord = musicArtistDownloadResponse.getQueueRecord();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b(lidarrQueueRecord.getTitle()));
    StringBuilder details = new StringBuilder();
    details.append("Status - ").append(lidarrQueueRecord.getStatus()).append("\n");
    details.append("Time Left - ").append(lidarrQueueRecord.getTimeleft() == null ? "unknown" : lidarrQueueRecord.getTimeleft() + "\n");
    if (lidarrQueueRecord.getStatusMessages() != null) {
      //limit messages to 5, since lidarr can really throw a ton of status messages
      for (LidarrQueueStatusMessage statusMessage : ListUtils.subList(lidarrQueueRecord.getStatusMessages(), 5)) {
        for (String message : statusMessage.getMessages()) {
          details.append("Download message - ").append(message).append("\n");
        }
      }
    }
    domContents.add(code(details.toString()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(ErrorResponse errorResponse) {
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("Error! - " + errorResponse.getErrorMessage()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(InfoResponse infoResponse) {
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("Info! - " + infoResponse.getInfoMessage()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(SuccessResponse successResponse) {
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(u(b("Success! - " + successResponse.getSuccessMessage())));
    return new TelegramResponse(domContents);
  }

  private StringBuilder appendQualityItem(SonarrProfileQualityItem qualityItem) {
    StringBuilder qualityItems = new StringBuilder();
    qualityItems.append("Quality - ")
            .append("id=")
            .append(qualityItem.getQuality().getId())
            .append(", name=")
            .append(qualityItem.getQuality().getName())
            .append(", resolution=")
            .append(qualityItem.getQuality().getResolution())
            .append("\n");
    return qualityItems;
  }

  @Override
  public TelegramResponse build(ShowProfileResponse showProfileResponse) {
    SonarrProfile sonarrProfile = showProfileResponse.getShowProfile();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("Profile"));
    domContents.add(text("Name - " + sonarrProfile.getName()));
    domContents.add(text("Cutoff - " + sonarrProfile.getCutOffDisplayStr()));
    if (sonarrProfile.getItems() != null) {
      StringBuilder qualityItems = new StringBuilder();
      for (int k = 0; k < sonarrProfile.getItems().size(); k++) {
        SonarrProfileQualityItem sonarrProfileQualityItem = sonarrProfile.getItems().get(k);
        if (sonarrProfileQualityItem.isAllowed()) {
          if (sonarrProfileQualityItem.getQuality() == null) {
             for(SonarrProfileQualityItem qualityItem : sonarrProfileQualityItem.getItems()) {
               qualityItems.append(appendQualityItem(qualityItem));
             }
          } else {
            qualityItems.append(appendQualityItem(sonarrProfileQualityItem));
          }
        }
      }
      domContents.add(code(qualityItems.toString()));
    }
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(MovieProfileResponse movieProfileResponse) {
    RadarrProfile radarrProfile = movieProfileResponse.getRadarrProfile();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("Profile"));
    domContents.add(text("Name - " + radarrProfile.getName()));
    domContents.add(text("Cutoff - " + radarrProfile.getCutoff()));
    if (radarrProfile.getItems() != null) {
      StringBuilder qualityItems = new StringBuilder();
      for (int k = 0; k < radarrProfile.getItems().size(); k++) {
        RadarrProfileQualityItem radarrProfileQualityItem = radarrProfile.getItems().get(k);
        if (radarrProfileQualityItem.isAllowed() && radarrProfileQualityItem.getQuality() != null) {
          qualityItems.append("Quality - name=").append(radarrProfileQualityItem.getQuality().getName()).append(", resolution=").append(radarrProfileQualityItem.getQuality().getResolution()).append("\n");
        }
      }
      domContents.add(code(qualityItems.toString()));
    }
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(NewShowResponse newShowResponse) {
    SonarrShow sonarrShow = newShowResponse.getNewShow();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("*Title* - " + sonarrShow.getTitle()));
    domContents.add(code("TvdbId - " + sonarrShow.getTvdbId()));
    domContents.add(a(sonarrShow.getRemoteImage()));
    return getAddResponse(domContents, SonarrCommands.getAddShowCommandStr(sonarrShow.getTvdbId()));
  }

  @Override
  public TelegramResponse build(ExistingShowResponse existingShowResponse) {
    SonarrShow sonarrShow = existingShowResponse.getExistingShow();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b("*Title* - " + sonarrShow.getTitle()));
    domContents.add(code("TvdbId - " + sonarrShow.getTvdbId()));
    StringBuilder existingShowDetails = new StringBuilder();
    existingShowDetails.append("Number of seasons - ").append(sonarrShow.getSeasons().size()).append("\n");
    for (SonarrSeason sonarrSeason : sonarrShow.getSeasons()) {
      existingShowDetails
              .append("Season#")
              .append(sonarrSeason.getSeasonNumber())
              .append(",Available Epsiodes=")
              .append(sonarrSeason.getStatistics().getEpisodeCount())
              .append(",Total Epsiodes=")
              .append(sonarrSeason.getStatistics().getTotalEpisodeCount())
              .append("\n");
    }
    domContents.add(code(existingShowDetails.toString()));
    domContents.add(a(sonarrShow.getRemoteImage()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(NewMovieResponse newMovieResponse) {
    RadarrMovie lookupMovie = newMovieResponse.getRadarrMovie();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b(lookupMovie.getTitle()));
    domContents.add(a(lookupMovie.getRemoteImage()));
    return getAddResponse(domContents, RadarrCommands.getAddMovieCommandStr(lookupMovie.getTitle(), lookupMovie.getTmdbId()));
  }

  @Override
  public TelegramResponse build(ExistingMovieResponse existingMovieResponse) {
    RadarrMovie lookupMovie = existingMovieResponse.getRadarrMovie();
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b(lookupMovie.getTitle()));
    String existingDetails = "Id - " + lookupMovie.getId() + "\n" +
            "Downloaded - " + (lookupMovie.getSizeOnDisk() > 0) + "\n" +
            "Has File - " + lookupMovie.isHasFile() + "\n";
    domContents.add(code(existingDetails));
    domContents.add(a(lookupMovie.getRemoteImage()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(NewMusicArtistResponse newMusicArtistResponse) {
    LidarrArtist lookupArtist = newMusicArtistResponse.getLidarrArtist();
    List<DomContent> domContents = new ArrayList<>();
    String artistDetail = " (" + lookupArtist.getDisambiguation() + ")";
    domContents.add(b(lookupArtist.getArtistName() + (Strings.isEmpty(lookupArtist.getDisambiguation()) ? "" :  artistDetail)));
    domContents.add(Strings.isEmpty(lookupArtist.getRemoteImage()) ? code("No image found!") : a(lookupArtist.getRemoteImage()));
    return getAddResponse(domContents, LidarrCommands.getAddArtistCommandStr(lookupArtist.getArtistName(), lookupArtist.getForeignArtistId()));
  }

  @Override
  public TelegramResponse build(ExistingMusicArtistResponse existingMusicArtistResponse) {
    LidarrArtist lookupArtist = existingMusicArtistResponse.getLidarrArtist();
    List<DomContent> domContents = new ArrayList<>();
    String artistDetail = " (" + lookupArtist.getDisambiguation() + ")";
    domContents.add(b(lookupArtist.getArtistName() + (Strings.isEmpty(lookupArtist.getDisambiguation()) ? "" :  artistDetail)));
    domContents.add(a(lookupArtist.getRemoteImage()));
    return new TelegramResponse(domContents);
  }

  @Override
  public TelegramResponse build(MovieResponse movieResponse) {
    return getMovieResponse(movieResponse.getRadarrMovie());
  }

  @Override
  public TelegramResponse build(DiscoverMovieResponse discoverMovieResponse) {
    return getMovieResponse(discoverMovieResponse.getRadarrMovie());
  }

  @Override
  public TelegramResponse build(StatusCommandResponse statusCommandResponse) {
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(u(b("*Endpoint Statuses*")));
    for (Map.Entry<String, Boolean> endpointStatusEntry : statusCommandResponse.getEndpoints().entrySet()) {
      domContents.add(text(endpointStatusEntry.getKey() + " - " + (endpointStatusEntry.getValue() ? "\uD83D\uDFE2" : "\uD83D\uDD34")));
    }
    return new TelegramResponse(domContents);
  }

  private TelegramResponse getAddResponse(List<DomContent> domContents, String command) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      String json = objectMapper.writeValueAsString(new TelegramCallbackData(command));
      TelegramCallbackManager telegramCallbackManager = new TelegramCallbackManager();
      int id = telegramCallbackManager.saveCallback(json);
      return new TelegramResponse(domContents,
              // callback data can never be larger than 64 bytes
              new InlineKeyboardMarkup(new InlineKeyboardButton("Add").callbackData(String.valueOf(id))));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private TelegramResponse getMovieResponse(RadarrMovie radarrMovie) {
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(b(radarrMovie.getTitle()));
    domContents.add(text("TmdbId - " + radarrMovie.getTmdbId()));
    domContents.add(u(b(ADD_MOVIE_COMMAND_FIELD_PREFIX + " - " + RadarrCommands.getAddMovieCommandStr(radarrMovie.getTitle(), radarrMovie.getTmdbId()))));
    domContents.add(a(radarrMovie.getRemoteImage()));
    return new TelegramResponse(domContents);
  }

  private List<DomContent> getListOfCommands(List<Command> commands) {
    List<DomContent> domContents = new ArrayList<>();
    domContents.add(u(b("*Commands*")));
    for (Command command : commands) {
      domContents.add(b(text(CommandContext.getConfig().getPrefix() + command.getCommandUsage())));
      domContents.add(text(command.getDescription()));
      domContents.add(text(" "));
    }
    return domContents;
  }
}
