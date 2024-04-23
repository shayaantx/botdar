package com.botdarr.api;

import com.botdarr.commands.responses.CommandResponse;
import com.botdarr.commands.responses.ErrorResponse;
import com.botdarr.commands.responses.InfoResponse;
import com.botdarr.utilities.ListUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AddStrategy<T> {
  public AddStrategy(ContentType contentType) {
    this.contentDisplayName = contentType.getDisplayName();
  }

  public abstract List<T> lookupContent(String search) throws Exception;
  public abstract List<T> lookupItemById(String id) throws Exception;
  public abstract boolean doesItemExist(T content);
  public abstract String getItemId(T item);
  public abstract CommandResponse addContent(T content);
  public abstract CommandResponse getNewItemResponse(T item);

  public CommandResponse addWithSearchId(String searchText, String id) {
    try {
      List<T> items = lookupContent(searchText);
      if (items.isEmpty()) {
        LOGGER.warn("Search text " + searchText + "yielded no " + this.contentDisplayName + "s, trying id");
        items = lookupItemById(id);
      }
      if (items.isEmpty()) {
        LOGGER.warn("Search id " + id + "yielded no " + this.contentDisplayName + "s, stopping");
        return new ErrorResponse("No " + this.contentDisplayName + "s found");
      }
      for (T item : items) {
        if (getItemId(item).equalsIgnoreCase(id)) {
          if (doesItemExist(item)) {
            return new ErrorResponse(this.contentDisplayName + " already exists");
          }
          return addContent(item);
        }
      }
      return new ErrorResponse("Could not find " + contentDisplayName + " with search text=" + searchText + " and id=" + id);
    } catch (Throwable e) {
      LOGGER.error("Error trying to add " + contentDisplayName, e);
      return new ErrorResponse("Error adding content, e=" + e.getMessage());
    }
  }

  public CommandResponse addWithId(String id) {
    try {
      List<T> items = lookupItemById(id);

      if (items.isEmpty()) {
        LOGGER.warn("Search id " + id + "yielded no " + this.contentDisplayName + "s, stopping");
        return new ErrorResponse("No " + this.contentDisplayName + "s found");
      }
      for (T item : items) {
        if (getItemId(item).equalsIgnoreCase(id)) {
          if (doesItemExist(item)) {
            return new ErrorResponse(this.contentDisplayName + " already exists");
          }
          return addContent(item);
        }
      }
      return new ErrorResponse("Could not find " + contentDisplayName + " with id=" + id);
    } catch (Throwable e) {
      LOGGER.error("Error trying to add " + contentDisplayName, e);
      return new ErrorResponse("Error adding content, e=" + e.getMessage());
    }
  }

  public List<CommandResponse> addWithSearchTitle(String searchText) {
    try {
      List<T> items = lookupContent(searchText);
      if (items.size() == 0) {
        return Collections.singletonList(new InfoResponse("No " + contentDisplayName + "s found"));
      }

      if (items.size() == 1) {
        T item = items.get(0);
        if (doesItemExist(item)) {
          return Collections.singletonList(new ErrorResponse(contentDisplayName + " already exists"));
        }
        return Collections.singletonList(addContent(items.get(0)));
      }
      List<CommandResponse> restOfItems = new ArrayList<>();
      for (T item : items) {
        if (doesItemExist(item)) {
          //skip existing items
          continue;
        }
        restOfItems.add(getNewItemResponse(item));
      }
      if (restOfItems.size() > 1) {
        restOfItems = ListUtils.subList(restOfItems, MAX_RESULTS_TO_SHOW);
        restOfItems.add(0, new InfoResponse("Too many " + contentDisplayName + "s found, please narrow search or increase max results to show"));
      }
      if (restOfItems.size() == 0) {
        return Collections.singletonList(new InfoResponse("No new " + contentDisplayName + "s found, check existing " + contentDisplayName + "s"));
      }
      return restOfItems;
    } catch (Throwable e) {
      LOGGER.error("Error trying to add " + contentDisplayName, e);
      return Collections.singletonList(new ErrorResponse("Error trying to add " + contentDisplayName + ", for search text=" + searchText + ", e=" + e.getMessage()));
    }
  }

  private final String contentDisplayName;
  private static Logger LOGGER = LogManager.getLogger();
  private final int MAX_RESULTS_TO_SHOW = new ApiRequests().getMaxResultsToShow();
}
