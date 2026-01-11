package com.rithik.collegego;

public class RideModel {
    public String rideId;
    public String riderName;
    public String pickupAddress;
    public String dropAddress;
    public String time;
    public int amount;
    public double overlapPercent;  // will be filled later

    public RideModel() {}

    public RideModel(String rideId, String riderName, String pickupAddress,
                     String dropAddress, String time, int amount, double overlapPercent) {
        this.rideId = rideId;
        this.riderName = riderName;
        this.pickupAddress = pickupAddress;
        this.dropAddress = dropAddress;
        this.time = time;
        this.amount = amount;
        this.overlapPercent = overlapPercent;
    }
}
