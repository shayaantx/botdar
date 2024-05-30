package com.botdarr.clients.slack;

import com.botdarr.clients.ChatClientResponse;
import com.slack.api.model.block.LayoutBlock;

import java.util.ArrayList;
import java.util.List;

public class SlackResponse implements ChatClientResponse {
  public List<LayoutBlock> getBlocks() {
    return blocks;
  }

  public void addBlock(LayoutBlock slackResponseBlock) {
    this.blocks.add(slackResponseBlock);
  }

  private List<LayoutBlock> blocks = new ArrayList<>();
}
