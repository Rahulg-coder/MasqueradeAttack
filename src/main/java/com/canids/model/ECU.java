package com.canids.model;
public class ECU {
    private String ecuId;
    private String ecuName;
    public ECU(String ecuId, String ecuName) {
        this.ecuId = ecuId;
        this.ecuName = ecuName;
    }
    public String getEcuId() {
        return ecuId;
    }
    public String getEcuName() {
        return ecuName;
    }
    @Override
    public String toString() {
        return ecuId + " - " + ecuName;
    }
}