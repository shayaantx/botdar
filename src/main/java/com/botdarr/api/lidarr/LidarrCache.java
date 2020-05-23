package com.botdarr.api.lidarr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LidarrCache {

  public boolean doesArtistExist(LidarrArtist content) {
    return existingArtistNamesToIds.containsKey(content.getArtistName().toLowerCase());
  }

  public void addArtist(LidarrArtist artist) {
    existingForeignArtistIdToArtist.put(Long.valueOf(artist.getForeignArtistId()), artist);
    existingArtistNamesToIds.put(artist.getArtistName(), Long.valueOf(artist.getForeignArtistId()));
  }

  public void addProfile(LidarrProfile lidarrProfile) {
    existingProfiles.put(lidarrProfile.getName().toLowerCase(), lidarrProfile);
  }

  public LidarrArtist getExistingArtist(LidarrArtist lidarrArtist) {
    return existingForeignArtistIdToArtist.get(Long.valueOf(lidarrArtist.getForeignArtistId()));
  }

  public LidarrProfile getProfile(String qualityProfileName) {
    return existingProfiles.get(qualityProfileName.toLowerCase());
  }

  public void resetProfiles() {
    existingProfiles.clear();
  }

  public void resetArtists() {
    existingArtistNamesToIds.clear();
    existingForeignArtistIdToArtist.clear();
  }
  private Map<String, Long> existingArtistNamesToIds = new ConcurrentHashMap<>();
  private Map<Long, LidarrArtist> existingForeignArtistIdToArtist = new ConcurrentHashMap<>();
  private Map<String, LidarrProfile> existingProfiles = new ConcurrentHashMap<>();
}
