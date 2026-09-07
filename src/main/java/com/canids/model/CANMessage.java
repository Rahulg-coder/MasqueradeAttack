package com.canids.model;
import java.time.LocalTime;
public class CANMessage {
    private int messageId;
    private String sourceEcu;
    private String destinationEcu;
    private String data;
    private LocalTime timestamp;
    public CANMessage(int messageId, String sourceEcu, String destinationEcu, String data) {
        this.messageId = messageId;
        this.sourceEcu = sourceEcu;
        this.destinationEcu = destinationEcu;
        this.data = data;
        this.timestamp = LocalTime.now();
    }
    public int getMessageId() {
        return messageId;
    }
    public String getSourceEcu() {
        return sourceEcu;
    }
    public String getDestinationEcu() {
        return destinationEcu;
    }
    public String getData() {
        return data;
    }
    public LocalTime getTimestamp() {
        return timestamp;
    }
    public String serialize() {
        return messageId + "|" +
                sourceEcu + "|" +
                destinationEcu + "|" +
                data + "|" +
                timestamp;
    }
    @Override
    public String toString() {
        return serialize();
    }
}