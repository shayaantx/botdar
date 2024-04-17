package com.botdarr.clients.matrix;

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
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;

import static com.botdarr.api.lidarr.LidarrApi.ADD_ARTIST_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.radarr.RadarrApi.ADD_MOVIE_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.sonarr.SonarrApi.ADD_SHOW_COMMAND_FIELD_PREFIX;
import static com.botdarr.commands.StatusCommand.STATUS_COMMAND;
import static com.botdarr.commands.StatusCommand.STATUS_COMMAND_DESCRIPTION;

public class MatrixResponseBuilder implements ChatClientResponseBuilder<MatrixResponse> {
  @Override
  public MatrixResponse build(HelpResponse helpResponse) {
    try {
      MatrixResponse matrixResponse = new MatrixResponse();
      matrixResponse.addContent("<b><u>Version: " + ChatClientResponseBuilder.getVersion() + "</u></b>");
      boolean radarrEnabled = Config.isRadarrEnabled();
      boolean sonarrEnabled = Config.isSonarrEnabled();
      boolean lidarrEnabled = Config.isLidarrEnabled();
      if (radarrEnabled) {
        matrixResponse.addContent("<b>" + RadarrCommands.getHelpMovieCommandStr() + "</b> - Shows all the commands for movies");
      }

      if (sonarrEnabled) {
        matrixResponse.addContent("<b>" + SonarrCommands.getHelpShowCommandStr() + "</b> - Shows all the commands for shows");
      }

      if (lidarrEnabled) {
        matrixResponse.addContent("<b>" + LidarrCommands.getHelpCommandStr() + "</b> - Shows all the commands for music");
      }

      if (!radarrEnabled && !sonarrEnabled && !lidarrEnabled) {
        matrixResponse.addContent("<b>No radarr or sonarr or lidarr commands configured, check your properties file and logs</b>");
      }

      if (!Config.getStatusEndpoints().isEmpty()) {
        matrixResponse.addContent("<b>" + CommandContext.getConfig().getPrefix() + STATUS_COMMAND + "</b> - " + STATUS_COMMAND_DESCRIPTION);
      }
      return matrixResponse;
    } catch (Exception e) {
      throw new RuntimeException("Error getting botdarr version", e);
    }
  }

  @Override
  public MatrixResponse build(MusicHelpResponse musicHelpResponse) {
    return getListOfCommands(musicHelpResponse.getLidarrCommands());
  }

  @Override
  public MatrixResponse build(MoviesHelpResponse moviesHelpResponse) {
    return getListOfCommands(moviesHelpResponse.getRadarrCommands());
  }

  @Override
  public MatrixResponse build(ShowsHelpResponse showsHelpResponse) {
    return getListOfCommands(showsHelpResponse.getSonarrCommands());
  }

  @Override
  public MatrixResponse build(ShowResponse showResponse) {
    SonarrShow show = showResponse.getShow();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + show.getTitle());
    matrixResponse.addContent("<b>TvdbId</b> - " + show.getTvdbId());
    matrixResponse.addContent("<b>" + ADD_SHOW_COMMAND_FIELD_PREFIX + "</b> - " + SonarrCommands.getAddShowCommandStr(show.getTvdbId()));
    matrixResponse.addImage(show.getRemoteImage());
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(MusicArtistResponse musicArtistResponse) {
    LidarrArtist lidarrArtist = musicArtistResponse.getArtist();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Artist Name</b> - " + lidarrArtist.getArtistName());
    matrixResponse.addContent("<b>Id</b> - " + lidarrArtist.getForeignArtistId());
    matrixResponse.addContent("<b><u>" + ADD_ARTIST_COMMAND_FIELD_PREFIX + "</u></b> - " + LidarrCommands.getAddArtistCommandStr(lidarrArtist.getArtistName(), lidarrArtist.getForeignArtistId()));
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(MovieResponse movieResponse) {
    RadarrMovie radarrMovie = movieResponse.getRadarrMovie();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Movie Title</b> - " + radarrMovie.getTitle());
    matrixResponse.addContent("<b>TmdbId</b> - " + radarrMovie.getTmdbId());
    matrixResponse.addContent("<b><u>" + ADD_MOVIE_COMMAND_FIELD_PREFIX + "</u></b> - " + RadarrCommands.getAddMovieCommandStr(radarrMovie.getTitle(), radarrMovie.getTmdbId()));
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(ShowDownloadResponse showDownloadResponse) {
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + showDownloadResponse.getShowQueue().getTitle());
    matrixResponse.addContent("<b>Season/Episode</b> - " + "S" + showDownloadResponse.getShowQueue().getSeasonNumber() + "E" + showDownloadResponse.getShowQueue().getEpisodeNumber());
    matrixResponse.addContent("<b>Quality</b> - " + showDownloadResponse.getShowQueue().getQualityProfileName());
    matrixResponse.addContent("<b>Status</b> - " + showDownloadResponse.getShowQueue().getStatus());
    matrixResponse.addContent("<b>Time Left</b> - <i>" + (showDownloadResponse.getShowQueue().getTimeleft() == null ? "unknown" : showDownloadResponse.getShowQueue().getTimeleft()) + "</i>");
    matrixResponse.addContent("<b>Overview</b> - " + showDownloadResponse.getShowQueue().getOverview());
    if (showDownloadResponse.getShowQueue().getStatusMessages() != null) {
      for (String statusMessage : showDownloadResponse.getShowQueue().getStatusMessages()) {
        matrixResponse.addContent("<b>Download message</b> - " + statusMessage);
      }
    }
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(MovieDownloadResponse movieDownloadResponse) {
    RadarrQueue radarrQueue = movieDownloadResponse.getRadarrQueue();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + radarrQueue.getTitle());
    matrixResponse.addContent("<b>Quality</b> - " + radarrQueue.getQuality().getQuality().getName());
    matrixResponse.addContent("<b>Status</b> - " + radarrQueue.getStatus());
    matrixResponse.addContent("<b>Time Left</b> - <i>" + (radarrQueue.getTimeleft() == null ? "unknown" : radarrQueue.getTimeleft()) + "</i>");
    if (radarrQueue.getStatusMessages() != null) {
      for (RadarrQueueStatusMessages statusMessage : radarrQueue.getStatusMessages()) {
        for (String message : statusMessage.getMessages()) {
          matrixResponse.addContent("<b>Download message</b> - " + message);
        }
      }
    }
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(MusicArtistDownloadResponse musicArtistDownloadResponse) {
    LidarrQueueRecord lidarrQueue = musicArtistDownloadResponse.getQueueRecord();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + lidarrQueue.getTitle());
    matrixResponse.addContent("<b>Time Left</b> - <i>" + (lidarrQueue.getTimeleft() == null ? "unknown" : lidarrQueue.getTimeleft()) + "</b>");
    matrixResponse.addContent("<b>Status</b> - " + lidarrQueue.getStatus());
    if (lidarrQueue.getStatusMessages() != null) {
      for (LidarrQueueStatusMessage statusMessage : ListUtils.subList(lidarrQueue.getStatusMessages(), 5)) {
        for (String message : statusMessage.getMessages()) {
          matrixResponse.addContent("<b>Download message</b> - " + message);
        }
      }
    }
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(ErrorResponse errorResponse) {
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<span data-mx-color=\"#ff0000\">" + errorResponse.getErrorMessage() + "</span>");
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(InfoResponse infoResponse) {
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<span>" + infoResponse.getInfoMessage() + "</span>");
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(SuccessResponse successResponse) {
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<span data-mx-color=\"#3aeb34\">" + successResponse.getSuccessMessage() + "</span>");
    return matrixResponse;
  }

  private String addQualityItem(SonarrProfileQualityItem sonarrProfileQualityItem) {
    return "<b>Quality</b> - Name: " + sonarrProfileQualityItem.getQuality().getName() + ", Resolution: " + sonarrProfileQualityItem.getQuality().getResolution();
  }

  @Override
  public MatrixResponse build(ShowProfileResponse showProfileResponse) {
    SonarrProfile sonarrProfile = showProfileResponse.getShowProfile();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<u><b>Profile</b></u>");
    matrixResponse.addContent("<b>Name</b> - " + sonarrProfile.getName());
    matrixResponse.addContent("<b>Cutoff</b> - " + sonarrProfile.getCutOffDisplayStr());
    for (int k = 0; k < sonarrProfile.getItems().size(); k++) {
      SonarrProfileQualityItem sonarrProfileQualityItem = sonarrProfile.getItems().get(k);
      if (sonarrProfileQualityItem.isAllowed()) {
        if (sonarrProfileQualityItem.getQuality() == null) {
          for(SonarrProfileQualityItem qualityItem : sonarrProfileQualityItem.getItems()) {
            matrixResponse.addContent(addQualityItem(qualityItem));
          }
        } else {
          matrixResponse.addContent(addQualityItem(sonarrProfileQualityItem));
        }
      }
    }
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(MovieProfileResponse movieProfileResponse) {
    RadarrProfile radarrProfile = movieProfileResponse.getRadarrProfile();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<u><b>Profile</b></u>");
    matrixResponse.addContent("<b>Name</b> - " + radarrProfile.getName());
    matrixResponse.addContent("<b>Cutoff</b> - " + radarrProfile.getCutoff());
    for (int k = 0; k < radarrProfile.getItems().size(); k++) {
      RadarrProfileQualityItem radarrProfileQualityItem = radarrProfile.getItems().get(k);
      if (radarrProfileQualityItem.isAllowed() && radarrProfileQualityItem.getQuality() != null) {
        matrixResponse.addContent("<b>Quality</b> - Name: " + radarrProfileQualityItem.getQuality().getName() + ", Resolution: " + radarrProfileQualityItem.getQuality().getResolution());
      }
    }
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(NewShowResponse newShowResponse) {
    SonarrShow sonarrShow = newShowResponse.getNewShow();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + sonarrShow.getTitle());
    matrixResponse.addContent("<b>TvdbId</b> - " + sonarrShow.getTvdbId());
    matrixResponse.addContent("<b>" + ADD_SHOW_COMMAND_FIELD_PREFIX + "</b> - " + SonarrCommands.getAddShowCommandStr(sonarrShow.getTvdbId()));
    matrixResponse.addImage(sonarrShow.getRemoteImage());
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(ExistingShowResponse existingShowResponse) {
    SonarrShow existingShow = existingShowResponse.getExistingShow();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + existingShow.getTitle());
    matrixResponse.addContent("<b>TvdbId</b> - " + existingShow.getTvdbId());
    matrixResponse.addContent("<b>Id</b> - " + existingShow.getId());
    if (existingShow.getSeasons() != null) {
      matrixResponse.addContent("Number of seasons - " + existingShow.getSeasons().size());
      for (SonarrSeason sonarrSeason : existingShow.getSeasons()) {
        matrixResponse.addContent(
                "Season#" + sonarrSeason.getSeasonNumber() +
                        ",Available Epsiodes=" + sonarrSeason.getStatistics().getEpisodeCount() +
                        ",Total Epsiodes=" + sonarrSeason.getStatistics().getTotalEpisodeCount());
      }
    }
    matrixResponse.addImage(existingShow.getRemoteImage());
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(NewMovieResponse newMovieResponse) {
    RadarrMovie radarrMovie = newMovieResponse.getRadarrMovie();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + radarrMovie.getTitle());
    matrixResponse.addContent("<b>TmdbId</b> - " + radarrMovie.getTmdbId());
    matrixResponse.addContent("<b>" + ADD_MOVIE_COMMAND_FIELD_PREFIX + "</b> - " + RadarrCommands.getAddMovieCommandStr(radarrMovie.getTitle(), radarrMovie.getTmdbId()));
    matrixResponse.addImage(radarrMovie.getRemoteImage());
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(ExistingMovieResponse existingMovieResponse) {
    RadarrMovie radarrMovie = existingMovieResponse.getRadarrMovie();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + radarrMovie.getTitle());
    matrixResponse.addContent("<b>TmdbId</b> - " + radarrMovie.getTmdbId());
    matrixResponse.addContent("<b>Id</b> - " + radarrMovie.getId());
    matrixResponse.addContent("<b>Downloaded</b> - " + (radarrMovie.getSizeOnDisk() > 0));
    matrixResponse.addContent("<b>Has File</b> - " + radarrMovie.isHasFile());
    matrixResponse.addImage(radarrMovie.getRemoteImage());
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(NewMusicArtistResponse newMusicArtistResponse) {
    LidarrArtist lidarrArtist = newMusicArtistResponse.getLidarrArtist();
    MatrixResponse matrixResponse = new MatrixResponse();
    String artistDetail = " (" + lidarrArtist.getDisambiguation() + ")";
    matrixResponse.addContent("<b>Title</b> - " + (lidarrArtist.getArtistName() + (Strings.isEmpty(lidarrArtist.getDisambiguation()) ? "" :  artistDetail)));
    matrixResponse.addContent("<b>" + ADD_ARTIST_COMMAND_FIELD_PREFIX + "</b> - " + LidarrCommands.getAddArtistCommandStr(lidarrArtist.getArtistName(), lidarrArtist.getForeignArtistId()));
    matrixResponse.addImage(lidarrArtist.getRemoteImage());
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(ExistingMusicArtistResponse existingMusicArtistResponse) {
    LidarrArtist lidarrArtist = existingMusicArtistResponse.getLidarrArtist();
    MatrixResponse matrixResponse = new MatrixResponse();
    String artistDetail = " (" + lidarrArtist.getDisambiguation() + ")";
    matrixResponse.addContent("<b>Title</b> - " + (lidarrArtist.getArtistName() + (Strings.isEmpty(lidarrArtist.getDisambiguation()) ? "" :  artistDetail)));
    matrixResponse.addImage(lidarrArtist.getRemoteImage());
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(DiscoverMovieResponse discoverMovieResponse) {
    RadarrMovie radarrMovie = discoverMovieResponse.getRadarrMovie();
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b>Title</b> - " + radarrMovie.getTitle());
    matrixResponse.addContent("<b>TmdbId</b> - " + radarrMovie.getTmdbId());
    matrixResponse.addContent("<b>" + ADD_MOVIE_COMMAND_FIELD_PREFIX + "</b> - " + RadarrCommands.getAddMovieCommandStr(radarrMovie.getTitle(), radarrMovie.getTmdbId()));
    if (radarrMovie.getRemoteImage() != null && !radarrMovie.getRemoteImage().isEmpty()) {
      matrixResponse.addImage(radarrMovie.getRemoteImage());
    }
    return matrixResponse;
  }

  @Override
  public MatrixResponse build(StatusCommandResponse statusCommandResponse) {
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addRawContent("<table>");
    matrixResponse.addRawContent("<thead><tr><td>Endpoint</td><td>Statuses</td></tr></thead>");
    for (Map.Entry<String, Boolean> endpointStatusEntry : statusCommandResponse.getEndpoints().entrySet()) {
      matrixResponse.addRawContent("<tr>");
      matrixResponse.addRawContent("<td>");
      matrixResponse.addRawContent(endpointStatusEntry.getKey());
      matrixResponse.addRawContent("</td>");
      matrixResponse.addRawContent("<td>");
      matrixResponse.addRawContent(endpointStatusEntry.getValue() ? "\uD83D\uDFE2" : "\uD83D\uDD34");
      matrixResponse.addRawContent("</td>");
      matrixResponse.addRawContent("</tr>");
    }
    matrixResponse.addRawContent("</table>");
    return matrixResponse;
  }

  private MatrixResponse getListOfCommands(List<Command> commands) {
    MatrixResponse matrixResponse = new MatrixResponse();
    matrixResponse.addContent("<b><u>Commands</u></b>");
    for (Command command : commands) {
      matrixResponse.addContent("<b>" + CommandContext.getConfig().getPrefix() + command.getCommandUsage() + "</b>" + " - " + command.getDescription());
    }
    return matrixResponse;
  }
}
