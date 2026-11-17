package com.example.truparkingapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * One parking spot, stored at lots/{lotId}/spots/{spotId} (for example lots/A/spots/A7).
 * Field names match the "Lots and spots schema" note exactly.
 */
@IgnoreExtraProperties
public class Spot {

    public static final String STATUS_FREE = "FREE";
    public static final String STATUS_TAKEN = "TAKEN";
    public static final String STATUS_RESERVED = "RESERVED";

    public static final String TYPE_REGULAR = "REGULAR";
    public static final String TYPE_ACCESSIBLE = "ACCESSIBLE";
    public static final String TYPE_EV = "EV";
    public static final String TYPE_MOTORCYCLE = "MOTORCYCLE";

    /** Filled in from the document id (A1, A2, ...), not stored as a field. */
    @DocumentId
    private String id;
    private int number;
    private int row;
    private int column;
    private String type;
    private String status;
    private String occupiedBy;
    private Timestamp reservedUntil;
    private Timestamp updatedAt;

    /** Needed by Firestore to rebuild the object from a document. */
    public Spot() {
    }

    /**
     * True when the spot can be taken: it is FREE, or its 15 minute hold has
     * already run out. @Exclude keeps this out of the stored document.
     */
    @Exclude
    public boolean isAvailable() {
        if (STATUS_FREE.equals(status)) {
            return true;
        }
        return STATUS_RESERVED.equals(status)
                && reservedUntil != null
                && reservedUntil.compareTo(Timestamp.now()) < 0;
    }

    public String getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public String getOccupiedBy() {
        return occupiedBy;
    }

    public Timestamp getReservedUntil() {
        return reservedUntil;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setOccupiedBy(String occupiedBy) {
        this.occupiedBy = occupiedBy;
    }

    public void setReservedUntil(Timestamp reservedUntil) {
        this.reservedUntil = reservedUntil;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
