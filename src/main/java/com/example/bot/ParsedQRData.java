package com.example.bot;

public class ParsedQRData {

    private String appName;
    private String shipmentId;

    public ParsedQRData(String appName, String shipmentId) {
        this.appName = appName;
        this.shipmentId = shipmentId;
    }

    public String getAppName() {
        return appName;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
}
