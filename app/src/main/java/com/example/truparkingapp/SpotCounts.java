package com.example.truparkingapp;

import java.util.List;

/**
 * How many spots a lot has and how many are free, counted from the spot
 * documents themselves. Used by the lot cards and the grid header so both
 * always agree with what the grid shows.
 */
public final class SpotCounts {

    public final int free;
    public final int total;

    public SpotCounts(int free, int total) {
        this.free = free;
        this.total = total;
    }

    public static SpotCounts of(List<Spot> spots) {
        int free = 0;
        for (Spot spot : spots) {
            if (spot.isAvailable()) {
                free++;
            }
        }
        return new SpotCounts(free, spots.size());
    }
}
