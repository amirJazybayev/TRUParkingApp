
package com.example.truparkingapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Booking {

    @DocumentId
    private String id;

    private String userId;
    private String lotId;
    private String spotId;

    private int spotNumber;
    private int durationHours;
    private int amountCents;

    private String currency;
    private String paymentMethod;
    private String paymentStatus;

    private Timestamp startedAt;
    private Timestamp endsAt;

    private Timestamp endedAt;

    public Booking() {
        // Required by Firestore
    }

    @Exclude
    public boolean isActive() {
        return "SIMULATED_PAID".equals(paymentStatus)
                && endedAt == null
                && endsAt != null
                && endsAt.toDate().getTime()
                > System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getLotId() {
        return lotId;
    }

    public String getSpotId() {
        return spotId;
    }

    public int getSpotNumber() {
        return spotNumber;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public Timestamp getEndsAt() {
        return endsAt;
    }
    public Timestamp getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Timestamp endedAt) {
        this.endedAt = endedAt;
    }
}
