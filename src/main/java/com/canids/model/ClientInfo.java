package com.canids.model;
public class ClientInfo {
    private String clientId;
    private String ipAddress;
    private int port;
    private boolean connected;
    private int messageCount;
    public ClientInfo(String clientId, String ipAddress, int port) {
        this.clientId = clientId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connected = true;
        this.messageCount = 0;
    }
    public void incrementMessageCount() {
        messageCount++;
    }
    public void disconnect() {
        connected = false;
    }
    public String getClientId() {
        return clientId;
    }
    public String getIpAddress() {
        return ipAddress;
    }
    public int getPort() {
        return port;
    }
    public boolean isConnected() {
        return connected;
    }
    public int getMessageCount() {
        return messageCount;
    }
}