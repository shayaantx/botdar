package com.botdarr.api.lidarr;

public class LidarrStatistics {
  public Integer getAlbumCount() {
    return albumCount;
  }

  public void setAlbumCount(Integer albumCount) {
    this.albumCount = albumCount;
  }

  public Integer getTrackFileCount() {
    return trackFileCount;
  }

  public void setTrackFileCount(Integer trackFileCount) {
    this.trackFileCount = trackFileCount;
  }

  public Integer getTrackCount() {
    return trackCount;
  }

  public void setTrackCount(Integer trackCount) {
    this.trackCount = trackCount;
  }

  public Integer getTotalTrackCount() {
    return totalTrackCount;
  }

  public void setTotalTrackCount(Integer totalTrackCount) {
    this.totalTrackCount = totalTrackCount;
  }

  public Integer getSizeOnDisk() {
    return sizeOnDisk;
  }

  public void setSizeOnDisk(Integer sizeOnDisk) {
    this.sizeOnDisk = sizeOnDisk;
  }

  public Double getPercentOfTracks() {
    return percentOfTracks;
  }

  public void setPercentOfTracks(Double percentOfTracks) {
    this.percentOfTracks = percentOfTracks;
  }

  private Integer albumCount = 0;
  private Integer trackFileCount = 0;
  private Integer trackCount = 0;
  private Integer totalTrackCount = 0;
  private Integer sizeOnDisk = 0;
  private Double percentOfTracks = 0.0;
}
