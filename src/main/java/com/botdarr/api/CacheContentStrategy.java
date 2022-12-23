package com.botdarr.api;

import com.botdarr.connections.ConnectionHelper;
import com.botdarr.connections.RequestBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public abstract class CacheContentStrategy<T, Z> {
  public CacheContentStrategy(RequestBuilder requestBuilder) {
    this.requestBuilder = requestBuilder;
  }

  public abstract void deleteFromCache(List<Z> itemsAddedUpdated);
  public abstract Z addToCache(JsonElement cacheItem);

  public void cacheData() {
    List<Z> itemsAddedUpdated = new ArrayList<>();
    ConnectionHelper.makeGetRequest(
            this.requestBuilder,
            new ConnectionHelper.SimpleEntityResponseHandler<List<T>>() {
      @Override
      public List<T> onSuccess(String response) throws Exception {
        JsonParser parser = new JsonParser();
        JsonArray json = parser.parse(response).getAsJsonArray();
        for (int i = 0; i < json.size(); i++) {
          itemsAddedUpdated.add(addToCache(json.get(i)));
        }
        return null;
      }
    });
    deleteFromCache(itemsAddedUpdated);
    LOGGER.debug("Finished caching content data for host " + this.requestBuilder.getHost());
  }

  private final RequestBuilder requestBuilder;
  private static final Logger LOGGER = LogManager.getLogger();
}
