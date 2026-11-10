package com.example.truparkingapp;

/**
 * A pojo class that represents a spot in the parking lot
 */
public class Spot {

    private String id;
    private boolean taken;

    // Empty constructor
    public Spot() {
    }

    public Spot(String id, boolean taken) {
        this.id = id;
        this.taken = taken;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isTaken() {
        return taken;
    }

    public void setTaken(boolean taken) {
        this.taken = taken;
    }
}
