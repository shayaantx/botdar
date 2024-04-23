package com.botdarr.clients.slack;

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
import com.github.seratch.jslack.api.model.block.*;
import com.github.seratch.jslack.api.model.block.composition.MarkdownTextObject;
import com.github.seratch.jslack.api.model.block.composition.PlainTextObject;
import com.github.seratch.jslack.api.model.block.element.ButtonElement;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.util.*;

import static com.botdarr.api.lidarr.LidarrApi.ADD_ARTIST_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.radarr.RadarrApi.ADD_MOVIE_COMMAND_FIELD_PREFIX;
import static com.botdarr.api.sonarr.SonarrApi.ADD_SHOW_COMMAND_FIELD_PREFIX;
import static com.botdarr.commands.StatusCommand.STATUS_COMMAND;
import static com.botdarr.commands.StatusCommand.STATUS_COMMAND_DESCRIPTION;

public class SlackResponseBuilder implements ChatClientResponseBuilder<SlackResponse> {
  @Override
  public SlackResponse build(HelpResponse helpResponse) {
    SlackResponse slackResponse = new SlackResponse();
    try {
      slackResponse.addBlock(SectionBlock.builder()
        .fields(Collections.singletonList(PlainTextObject.builder().emoji(true).text("Version: " + ChatClientResponseBuilder.getVersion()).build()))
        .text(MarkdownTextObject.builder().text("*Commands*").build())
        .build());

      slackResponse.addBlock(
              ActionsBlock.builder().elements(
                      Collections.singletonList(
                              ButtonElement.builder().text(
                                      PlainTextObject.builder().text("hello").build())
                                      .build()))
                      .build());
      boolean radarrEnabled = Config.isRadarrEnabled();
      boolean sonarrEnabled = Config.isSonarrEnabled();
      boolean lidarrEnabled = Config.isLidarrEnabled();
      if (radarrEnabled) {
        slackResponse.addBlock(SectionBlock.builder()
          .text(MarkdownTextObject.builder().text(RadarrCommands.getHelpMovieCommandStr() + " - Shows all the commands for movies").build())
          .build());
      }

      if (sonarrEnabled) {
        slackResponse.addBlock(SectionBlock.builder()
          .text(MarkdownTextObject.builder().text(SonarrCommands.getHelpShowCommandStr() + " - Shows all the commands for shows").build())
          .build());
      }

      if (lidarrEnabled) {
        slackResponse.addBlock(SectionBlock.builder()
          .text(MarkdownTextObject.builder().text(LidarrCommands.getHelpCommandStr() + " - Shows all the commands for music").build())
          .build());
      }

      if (!radarrEnabled && !sonarrEnabled && !lidarrEnabled) {
        slackResponse.addBlock(SectionBlock.builder()
          .text(MarkdownTextObject.builder().text("*No radarr or sonarr or lidarr commands configured, check your properties file and logs*").build())
          .build());
      }
      if (!Config.getStatusEndpoints().isEmpty()) {
        slackResponse.addBlock(SectionBlock.builder()
          .text(MarkdownTextObject.builder().text(CommandContext.getConfig().getPrefix() + STATUS_COMMAND + " - " + STATUS_COMMAND_DESCRIPTION).build())
          .build());
      }
    } catch (IOException e) {
      throw new RuntimeException("Error getting botdarr version", e);
    }
    return slackResponse;
  }

  @Override
  public SlackResponse build(MusicHelpResponse musicHelpResponse) {
    return getListOfCommands(musicHelpResponse.getLidarrCommands());
  }

  @Override
  public SlackResponse build(MoviesHelpResponse moviesHelpResponse) {
    return getListOfCommands(moviesHelpResponse.getRadarrCommands());
  }

  @Override
  public SlackResponse build(ShowsHelpResponse showsHelpResponse) {
    return getListOfCommands(showsHelpResponse.getSonarrCommands());
  }

  @Override
  public SlackResponse build(ShowResponse showResponse) {
    SonarrShow show = showResponse.getShow();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Title* - " + show.getTitle()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(PlainTextObject.builder().text("TvdbId - " + show.getTvdbId()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(PlainTextObject.builder().text(ADD_SHOW_COMMAND_FIELD_PREFIX + " - " + SonarrCommands.getAddShowCommandStr(show.getTvdbId())).build())
      .build());
    if (!Strings.isBlank(show.getRemoteImage())) {
      //if there is no poster to display, slack will fail to render all the blocks
      //so make sure there is one before trying to render
      slackResponse.addBlock(ImageBlock.builder()
        .imageUrl(show.getRemoteImage())
        .altText(show.getTitle() + " poster")
        .build());
    }
    return slackResponse;
  }

  @Override
  public SlackResponse build(MusicArtistResponse musicArtistResponse) {
    LidarrArtist lidarrArtist = musicArtistResponse.getArtist();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Artist Name* - " + lidarrArtist.getArtistName()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(PlainTextObject.builder().text("Id - " + lidarrArtist.getForeignArtistId()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(PlainTextObject.builder().text(ADD_ARTIST_COMMAND_FIELD_PREFIX + " - " + LidarrCommands.getAddArtistCommandStr(lidarrArtist.getArtistName(), lidarrArtist.getForeignArtistId())).build())
      .build());
    if (!Strings.isBlank(lidarrArtist.getRemoteImage())) {
      //if there is no poster to display, slack will fail to render all the blocks
      //so make sure there is one before trying to render
      slackResponse.addBlock(ImageBlock.builder()
        .imageUrl(lidarrArtist.getRemoteImage())
        .altText(lidarrArtist.getArtistName() + " poster")
        .build());
    }
    return slackResponse;
  }

  @Override
  public SlackResponse build(ShowDownloadResponse showDownloadResponse) {
    SonarrDownloadActivity showQueue = showDownloadResponse.getShowQueue();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Title* - " + showQueue.getTitle()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Season/Episode - " + "S" + showQueue.getSeasonNumber() + "E" + showQueue.getEpisodeNumber()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Quality - " + showQueue.getQualityProfileName()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Status - " + showQueue.getStatus()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Time Left - *" + (showQueue.getTimeleft() == null ? "unknown" : showQueue.getTimeleft()) + "*").build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("Overview - " + showQueue.getOverview()).build())
            .build());
    if (showQueue.getStatusMessages() != null) {
      List<ContextBlockElement> contextBlockElements = new ArrayList<>();
      for (String statusMessage : showQueue.getStatusMessages()) {
        contextBlockElements.add(PlainTextObject.builder().text(statusMessage).build());
      }
      if (showQueue.getStatusMessages() != null && showQueue.getStatusMessages().length > 0) {
        slackResponse.addBlock(ContextBlock.builder()
          .elements(contextBlockElements)
          .build());
      }
    }
    return slackResponse;
  }

  @Override
  public SlackResponse build(MovieDownloadResponse movieDownloadResponse) {
    RadarrQueue radarrQueue = movieDownloadResponse.getRadarrQueue();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Title* - " + radarrQueue.getTitle()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Quality - " + radarrQueue.getQuality().getQuality().getName()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Status - " + radarrQueue.getStatus()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Time Left - *" + (radarrQueue.getTimeleft() == null ? "unknown" : radarrQueue.getTimeleft()) + "*").build())
      .build());
    if (radarrQueue.getStatusMessages() != null) {
      List<ContextBlockElement> contextBlockElements = new ArrayList<>();
      for (RadarrQueueStatusMessages statusMessage : radarrQueue.getStatusMessages()) {
        for (String message : statusMessage.getMessages()) {
          contextBlockElements.add(PlainTextObject.builder().text(message).build());
        }
      }
      if (radarrQueue.getStatusMessages() != null && radarrQueue.getStatusMessages().length > 0) {
        slackResponse.addBlock(ContextBlock.builder()
          .elements(contextBlockElements)
          .build());
      }
    }
    return slackResponse;
  }

  @Override
  public SlackResponse build(MusicArtistDownloadResponse musicArtistDownloadResponse) {
    LidarrQueueRecord lidarrQueueRecord = musicArtistDownloadResponse.getQueueRecord();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Title* - " + lidarrQueueRecord.getTitle()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Time Left - *" + (lidarrQueueRecord.getTimeleft() == null ? "unknown" : lidarrQueueRecord.getTimeleft()) + "*").build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Status - " + lidarrQueueRecord.getStatus()).build())
      .build());
    if (lidarrQueueRecord.getStatusMessages() != null) {
      List<ContextBlockElement> contextBlockElements = new ArrayList<>();
      for (LidarrQueueStatusMessage statusMessage : ListUtils.subList(lidarrQueueRecord.getStatusMessages(), 5)) {
        for (String message : statusMessage.getMessages()) {
          contextBlockElements.add(PlainTextObject.builder().text(message).build());
        }
      }
      if (contextBlockElements.size() > 0) {
        slackResponse.addBlock(ContextBlock.builder()
          .elements(contextBlockElements)
          .build());
      }
    }
    return slackResponse;
  }

  @Override
  public SlackResponse build(ErrorResponse errorResponse) {
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Error* - " + errorResponse.getErrorMessage()).build())
      .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(InfoResponse infoResponse) {
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Info* - " + infoResponse.getInfoMessage()).build())
      .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(SuccessResponse successResponse) {
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Success* - " + successResponse.getSuccessMessage()).build())
      .build());
    return slackResponse;
  }

  private String addQualityItem(SonarrProfileQualityItem sonarrProfileQualityItem) {
    return "Quality - name=" + sonarrProfileQualityItem.getQuality().getName() + ", resolution=" + sonarrProfileQualityItem.getQuality().getResolution();
  }

  @Override
  public SlackResponse build(ShowProfileResponse showProfileResponse) {
    SonarrProfile sonarrProfile = showProfileResponse.getShowProfile();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Profile*").build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Name - " + sonarrProfile.getName()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Cutoff - " + sonarrProfile.getCutOffDisplayStr()).build())
      .build());

    List<ContextBlockElement> contextBlockElements = new ArrayList<>();
    for (int k = 0; k < sonarrProfile.getItems().size(); k++) {
      SonarrProfileQualityItem sonarrProfileQualityItem = sonarrProfile.getItems().get(k);
      if (sonarrProfileQualityItem.isAllowed()) {
        if (sonarrProfileQualityItem.getQuality() == null) {
          for(SonarrProfileQualityItem qualityItem : sonarrProfileQualityItem.getItems()) {
            contextBlockElements.add(PlainTextObject.builder()
                    .text(addQualityItem(qualityItem))
                    .build());
          }
        } else {
          contextBlockElements.add(PlainTextObject.builder()
                  .text(addQualityItem(sonarrProfileQualityItem))
                  .build());
        }
      }
    }
    slackResponse.addBlock(ContextBlock.builder()
      .elements(contextBlockElements)
      .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(MovieProfileResponse movieProfileResponse) {
    RadarrProfile radarrProfile = movieProfileResponse.getRadarrProfile();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Profile*").build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Name - " + radarrProfile.getName()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("Cutoff - " + radarrProfile.getCutoff()).build())
      .build());

    List<ContextBlockElement> contextBlockElements = new ArrayList<>();
    for (int k = 0; k < radarrProfile.getItems().size(); k++) {
      RadarrProfileQualityItem radarrProfileQualityItem = radarrProfile.getItems().get(k);
      if (radarrProfileQualityItem.isAllowed() && radarrProfileQualityItem.getQuality() != null) {
        contextBlockElements.add(PlainTextObject.builder()
          .text("Quality - name=" + radarrProfileQualityItem.getQuality().getName() + ", resolution=" + radarrProfileQualityItem.getQuality().getResolution())
          .build());
      }
    }
    slackResponse.addBlock(ContextBlock.builder()
      .elements(contextBlockElements)
      .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(NewShowResponse newShowResponse) {
    SonarrShow sonarrShow = newShowResponse.getNewShow();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Title* - " + sonarrShow.getTitle()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("TvdbId - " + sonarrShow.getTvdbId()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text(ADD_SHOW_COMMAND_FIELD_PREFIX + " - " + SonarrCommands.getAddShowCommandStr(sonarrShow.getTvdbId())).build())
            .build());
    slackResponse.addBlock(ImageBlock.builder()
      .imageUrl(sonarrShow.getRemoteImage())
      .altText(sonarrShow.getTitle() + " poster")
      .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(ExistingShowResponse existingShowResponse) {
    SonarrShow sonarrShow = existingShowResponse.getExistingShow();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("*Title* - " + sonarrShow.getTitle()).build())
            .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("TvdbId - " + sonarrShow.getTvdbId()).build())
            .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("Id - " + sonarrShow.getId()).build())
            .build());
    if (sonarrShow.getSeasons() != null) {
      List<ContextBlockElement> contextBlockElements = new ArrayList<>();
      contextBlockElements.add(PlainTextObject.builder()
              .text("Number of seasons - " + sonarrShow.getSeasons().size())
              .build());
      for (SonarrSeason sonarrSeason : sonarrShow.getSeasons()) {
        contextBlockElements.add(PlainTextObject.builder()
                .text("Season#" + sonarrSeason.getSeasonNumber() +
                        ",Available Epsiodes=" + sonarrSeason.getStatistics().getEpisodeCount() + ",Total Epsiodes=" + sonarrSeason.getStatistics().getTotalEpisodeCount())
                .build());
      }
      slackResponse.addBlock(ContextBlock.builder()
              .elements(contextBlockElements)
              .build());
    }
    slackResponse.addBlock(ImageBlock.builder()
            .imageUrl(sonarrShow.getRemoteImage())
            .altText(sonarrShow.getTitle() + " poster")
            .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(NewMovieResponse newMovieResponse) {
    RadarrMovie lookupMovie = newMovieResponse.getRadarrMovie();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Title* - " + lookupMovie.getTitle()).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("TmdbId - " + lookupMovie.getTmdbId()).build())
      .build());
    slackResponse.addBlock(ActionsBlock.builder().elements(Collections.singletonList(ButtonElement.builder().text(PlainTextObject.builder().text("hello").build()).build())).build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text(ADD_MOVIE_COMMAND_FIELD_PREFIX + " - " + RadarrCommands.getAddMovieCommandStr(lookupMovie.getTitle(), lookupMovie.getTmdbId())).build())
            .build());
    slackResponse.addBlock(ImageBlock.builder()
      .imageUrl(lookupMovie.getRemoteImage())
      .altText(lookupMovie.getTitle() + " poster")
      .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(ExistingMovieResponse existingMovieResponse) {
    RadarrMovie lookupMovie = existingMovieResponse.getRadarrMovie();
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("*Title* - " + lookupMovie.getTitle()).build())
            .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("TmdbId - " + lookupMovie.getTmdbId()).build())
            .build());
    slackResponse.addBlock(ActionsBlock.builder().elements(Collections.singletonList(ButtonElement.builder().text(PlainTextObject.builder().text("hello").build()).build())).build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("Id - " + lookupMovie.getId()).build())
            .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("Downloaded - " + (lookupMovie.getSizeOnDisk() > 0)).build())
            .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("Has File - " + lookupMovie.isHasFile()).build())
            .build());
    slackResponse.addBlock(ImageBlock.builder()
            .imageUrl(lookupMovie.getRemoteImage())
            .altText(lookupMovie.getTitle() + " poster")
            .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(NewMusicArtistResponse newMusicArtistResponse) {
    LidarrArtist lookupArtist = newMusicArtistResponse.getLidarrArtist();
    SlackResponse slackResponse = new SlackResponse();
    String artistDetail = " (" + lookupArtist.getDisambiguation() + ")";
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Artist Name* - " + lookupArtist.getArtistName() + (Strings.isEmpty(lookupArtist.getDisambiguation()) ? "" :  artistDetail)).build())
      .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text(ADD_ARTIST_COMMAND_FIELD_PREFIX + " - " +
                    LidarrCommands.getAddArtistCommandStr(lookupArtist.getArtistName(), lookupArtist.getForeignArtistId())).build())
            .build());
    slackResponse.addBlock(ImageBlock.builder()
      .imageUrl(lookupArtist.getRemoteImage())
      .altText(lookupArtist.getArtistName() + " poster")
      .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(ExistingMusicArtistResponse existingMusicArtistResponse) {
    LidarrArtist lookupArtist = existingMusicArtistResponse.getLidarrArtist();
    SlackResponse slackResponse = new SlackResponse();
    String artistDetail = " (" + lookupArtist.getDisambiguation() + ")";
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("*Artist Name* - " + lookupArtist.getArtistName() + (Strings.isEmpty(lookupArtist.getDisambiguation()) ? "" :  artistDetail)).build())
            .build());
    slackResponse.addBlock(ImageBlock.builder()
            .imageUrl(lookupArtist.getRemoteImage())
            .altText(lookupArtist.getArtistName() + " poster")
            .build());
    return slackResponse;
  }

  @Override
  public SlackResponse build(MovieResponse movieResponse) {
    return getMovieResponse(movieResponse.getRadarrMovie());
  }

  @Override
  public SlackResponse build(DiscoverMovieResponse discoverMovieResponse) {
    return getMovieResponse(discoverMovieResponse.getRadarrMovie());
  }

  @Override
  public SlackResponse build(StatusCommandResponse statusCommandResponse) {
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Endpoint Statuses*").build())
      .build());
    for (Map.Entry<String, Boolean> endpointStatusEntry : statusCommandResponse.getEndpoints().entrySet()) {
      slackResponse.addBlock(SectionBlock.builder()
        .text(MarkdownTextObject.builder().text(endpointStatusEntry.getKey() + " - " + (endpointStatusEntry.getValue() ? "\uD83D\uDFE2" : "\uD83D\uDD34")).build())
        .build());
    }
    return slackResponse;
  }

  private SlackResponse getMovieResponse(RadarrMovie radarrMovie) {
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("*Title* - " + radarrMovie.getTitle()).build())
            .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text("TmdbId - " + radarrMovie.getTmdbId()).build())
            .build());
    slackResponse.addBlock(SectionBlock.builder()
            .text(MarkdownTextObject.builder().text(ADD_MOVIE_COMMAND_FIELD_PREFIX + " - " + RadarrCommands.getAddMovieCommandStr(radarrMovie.getTitle(), radarrMovie.getTmdbId())).build())
            .build());
    if (!Strings.isBlank(radarrMovie.getRemoteImage())) {
      //if there is no poster to display, slack will fail to render all the blocks
      //so make sure there is one before trying to render
      slackResponse.addBlock(ImageBlock.builder()
              .imageUrl(radarrMovie.getRemoteImage())
              .altText(radarrMovie.getTitle() + " poster")
              .build());
    }
    return slackResponse;
  }

  private SlackResponse getListOfCommands(List<Command> commands) {
    SlackResponse slackResponse = new SlackResponse();
    slackResponse.addBlock(SectionBlock.builder()
      .text(MarkdownTextObject.builder().text("*Commands*").build())
      .build());
    for (Command command : commands) {
      slackResponse.addBlock(SectionBlock.builder()
        .text(MarkdownTextObject.builder().text(CommandContext.getConfig().getPrefix() + command.getCommandUsage() + " - " + command.getDescription()).build())
        .build());
    }
    return slackResponse;
  }
}
