package com.midas.goldproxy.common.messaging;

import java.io.Serializable;

public class PMMessage implements Serializable {
    private String from;
    private String to;
    private String message;
    private long timestamp;

    public PMMessage() {}

    public PMMessage(String from, String to, String message, long timestamp) {
        this.from = from;
        this.to = to;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }

    public void setFrom(String from) { this.from = from; }
    public void setTo(String to) { this.to = to; }
    public void setMessage(String message) { this.message = message; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
