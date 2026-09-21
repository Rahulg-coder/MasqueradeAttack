package com.canids.model;
import java.time.LocalTime;
public class CANMessage {
    private final int messageId;
    private final String sourceEcu;
    private final String destinationEcu;
    private final String data;
    private final LocalTime timestamp;
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
    public static CANMessage parse(String serializedMessage) {
        String[] parts = serializedMessage.split("\\|", 5);
        if (parts.length < 4) {
            throw new IllegalArgumentException("Expected MSG_ID|SOURCE_ECU|DESTINATION_ECU|DATA");
        }

        int messageId = Integer.parseInt(parts[0].trim());
        String sourceEcu = parts[1].trim();
        String destinationEcu = parts[2].trim();
        String data = parts[3].trim();
        return new CANMessage(messageId, sourceEcu, destinationEcu, data);
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
