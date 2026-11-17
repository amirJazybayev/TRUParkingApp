package com.example.truparkingapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * One parking lot, stored at lots/{code} in Firestore.
 * Field names match the "Lots and spots schema" note exactly.
 * Unknown fields in a document (such as the old permitType) are ignored.
 */
@IgnoreExtraProperties
public class Lot {

    /** Filled in from the document id (the lot code), not stored as a field. */
    @DocumentId
    private String id;
    private String name;
    private String code;
    private String description;
    private double latitude;
    private double longitude;
    private int totalSpots;
    private int freeSpots;
    private int rows;
    private int columns;
    private int sortOrder;
    private boolean active;
    private Timestamp updatedAt;

    /** Needed by Firestore to rebuild the object from a document. */
    public Lot() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getTotalSpots() {
        return totalSpots;
    }

    public int getFreeSpots() {
        return freeSpots;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setTotalSpots(int totalSpots) {
        this.totalSpots = totalSpots;
    }

    public void setFreeSpots(int freeSpots) {
        this.freeSpots = freeSpots;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
