package com.botdarr.clients.slack;

public class SlackEventContainer {
    public SlackMessage getEvent() {
        return event;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "SlackEventContainer{" +
                "event=" + event +
                ", type='" + type + '\'' +
                '}';
    }

    private SlackMessage event;
    private String type;
}
