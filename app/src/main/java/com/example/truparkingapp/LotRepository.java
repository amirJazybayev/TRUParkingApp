package com.example.truparkingapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.MetadataChanges;

public class LotRepository {

    /** Called once with the current data, then again after every change. */
    public interface Listener<T> {
        void onChanged(T result);

        void onError(Exception e);
    }
    public static class BookingResult {

        public final String bookingId;
        public final long endsAt;

        public BookingResult(String bookingId, long endsAt) {
            this.bookingId = bookingId;
            this.endsAt = endsAt;
        }
    }

    public Task<BookingResult> confirmBooking(
            String lotId,
            String spotId,
            String spotNumber,
            String userId,
            long expectedExpiry,
            int hours,
            String paymentMethod) {

        DocumentReference spotRef = db.collection(LOTS)
                .document(lotId)
                .collection(SPOTS)
                .document(spotId);

        DocumentReference bookingRef =
                db.collection("bookings").document();

        return db.runTransaction(transaction -> {

            if (hours != 1 && hours != 2
                    && hours != 3 && hours != 4
                    && hours != 8) {

                throw new IllegalArgumentException(
                        "Invalid parking duration"
                );
            }

            DocumentSnapshot snapshot =
                    transaction.get(spotRef);

            if (!snapshot.exists()) {
                throw new IllegalStateException(
                        "Parking spot does not exist"
                );
            }

            String status = snapshot.getString("status");
            String occupiedBy = snapshot.getString("occupiedBy");

            Timestamp expiry =
                    snapshot.getTimestamp("reservedUntil");

            long currentTime = System.currentTimeMillis();

            if (!Spot.STATUS_RESERVED.equals(status)
                    || !userId.equals(occupiedBy)
                    || expiry == null
                    || expiry.toDate().getTime() != expectedExpiry
                    || expiry.toDate().getTime() <= currentTime) {

                throw new IllegalStateException(
                        "Your reservation is no longer valid"
                );
            }

            long endTime = currentTime
                    + hours * 60L * 60L * 1000L;

            Timestamp endsAt =
                    new Timestamp(new Date(endTime));

            int totalPriceCents = hours * 200;

            // Create the booking record.
            Map<String, Object> booking = new HashMap<>();

            booking.put("userId", userId);
            booking.put("lotId", lotId);
            booking.put("spotId", spotId);
            booking.put("spotNumber", snapshot.getLong("number"));

            booking.put("durationHours", hours);
            booking.put("amountCents", totalPriceCents);
            booking.put("currency", "CAD");

            booking.put("paymentMethod", paymentMethod);
            booking.put("paymentStatus", "SIMULATED_PAID");

            booking.put("startedAt", FieldValue.serverTimestamp());
            booking.put("endsAt", endsAt);

            // Convert the temporary reservation
            // into a confirmed parking session.
            Map<String, Object> spotChanges = new HashMap<>();

            spotChanges.put("status", Spot.STATUS_TAKEN);
            spotChanges.put("occupiedBy", userId);
            spotChanges.put("reservedUntil", null);

            spotChanges.put("bookingId", bookingRef.getId());
            spotChanges.put("sessionEndsAt", endsAt);

            spotChanges.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );

            transaction.set(bookingRef, booking);

            transaction.update(spotRef, spotChanges);

            return new BookingResult(
                    bookingRef.getId(),
                    endTime
            );
        });
    }


    private static final String LOTS = "lots";
    private static final String SPOTS = "spots";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Live list of lots, lowest sortOrder first. Inactive lots are skipped here
     * in code, so Firestore does not need a composite index for
     * "active == true order by sortOrder".
     */
    public ListenerRegistration listenToLots(Listener<List<Lot>> listener) {
        return db.collection(LOTS)
                .orderBy("sortOrder", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        listener.onError(e);
                        return;
                    }
                    List<Lot> lots = new ArrayList<>();
                    if (snapshot != null) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            Lot lot = doc.toObject(Lot.class);
                            if (lot.isActive()) {
                                lots.add(lot);
                            }
                        }
                    }
                    listener.onChanged(lots);
                });
    }

    /** Live list of one lot's spots, ordered by the painted spot number. */
    public ListenerRegistration listenToSpots(String lotId, Listener<List<Spot>> listener) {
        return db.collection(LOTS)
                .document(lotId)
                .collection(SPOTS)
                .orderBy("number", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        listener.onError(e);
                        return;
                    }
                    List<Spot> spots = new ArrayList<>();
                    if (snapshot != null) {
                        for (QueryDocumentSnapshot doc : snapshot) {
                            spots.add(doc.toObject(Spot.class));
                        }
                    }
                    listener.onChanged(spots);
                });
    }

    public interface ReservationCallback{
        void onSuccess(Timestamp reservedUntil);
        void onError(Exception error);

    }

    public void reserveSpot(String lotId, String spotId, String userId, ReservationCallback callback){
        Timestamp end = new Timestamp(new Date(System.currentTimeMillis()+15 * 60L * 1000L));

        Map<String, Object> changes = new HashMap<>();
        changes.put("status", Spot.STATUS_RESERVED);
        changes.put("occupiedBy", userId);
        changes.put("bookingId", null);
        changes.put("sessionEndsAt", null);
        changes.put("reservedUntil", end);
        changes.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(LOTS).document(lotId).collection(SPOTS).document(spotId)
                .update(changes)
                .addOnSuccessListener(unused -> callback.onSuccess(end))
                .addOnFailureListener(callback::onError);
    }

    /** Safe to call with null, so onStop does not need its own null checks. */
    public static void stopListening(ListenerRegistration registration) {
        if (registration != null) {
            registration.remove();
        }
    }
    public ListenerRegistration listenToReservation(
            String lotId,
            String spotId,
            EventListener<DocumentSnapshot> listener) {

        return db.collection(LOTS)
                .document(lotId)
                .collection(SPOTS)
                .document(spotId)
                .addSnapshotListener(MetadataChanges.INCLUDE, listener);
    }

    public Task<Void> cancelReservation(
            String lotId,
            String spotId,
            String userId,
            long expectedExpiry) {

        DocumentReference spotRef = db.collection(LOTS)
                .document(lotId)
                .collection(SPOTS)
                .document(spotId);

        return db.<Void>runTransaction(transaction -> {

            DocumentSnapshot snapshot = transaction.get(spotRef);

            if (!snapshot.exists()) {
                throw new IllegalStateException("Spot does not exist");
            }

            String status = snapshot.getString("status");
            String occupiedBy = snapshot.getString("occupiedBy");
            Timestamp expiry = snapshot.getTimestamp("reservedUntil");

            if (!Spot.STATUS_RESERVED.equals(status)
                    || !userId.equals(occupiedBy)
                    || expiry == null
                    || expiry.toDate().getTime() != expectedExpiry) {

                throw new IllegalStateException(
                        "This reservation no longer belongs to you"
                );
            }

            Map<String, Object> changes = new HashMap<>();

            changes.put("status", Spot.STATUS_FREE);
            changes.put("occupiedBy", null);
            changes.put("reservedUntil", null);
            changes.put("updatedAt", FieldValue.serverTimestamp());

            transaction.update(spotRef, changes);

            return null;
        });


    }
    public ListenerRegistration listenToBooking(
            String bookingId,
            EventListener<DocumentSnapshot> listener) {

        return db.collection("bookings")
                .document(bookingId)
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        listener
                );
    }

    public ListenerRegistration listenToUserBookings(
            String userId,
            Listener<List<Booking>> listener) {

        return db.collection("bookings")
                .whereEqualTo("userId", userId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        listener.onError(error);
                        return;
                    }

                    List<Booking> bookings = new ArrayList<>();

                    if (snapshot != null) {

                        for (QueryDocumentSnapshot doc : snapshot) {

                            Booking booking =
                                    doc.toObject(Booking.class);

                            bookings.add(booking);
                        }
                    }

                    listener.onChanged(bookings);
                });
    }

    public Task<Void> endParkingSession(
            String bookingId,
            String userId) {

        DocumentReference bookingRef =
                db.collection("bookings")
                        .document(bookingId);

        return db.<Void>runTransaction(transaction -> {

            DocumentSnapshot booking =
                    transaction.get(bookingRef);

            if (!booking.exists()) {
                throw new IllegalStateException(
                        "Booking does not exist"
                );
            }

            if (!userId.equals(booking.getString("userId"))) {
                throw new IllegalStateException(
                        "This booking does not belong to you"
                );
            }

            if (booking.getTimestamp("endedAt") != null) {
                throw new IllegalStateException(
                        "Parking session has already ended"
                );
            }

            Timestamp endsAt = booking.getTimestamp("endsAt");

            if (endsAt == null
                    || endsAt.toDate().getTime()
                    <= System.currentTimeMillis()) {

                throw new IllegalStateException(
                        "Parking session has already expired"
                );
            }

            if (!"SIMULATED_PAID".equals(
                    booking.getString("paymentStatus"))) {

                throw new IllegalStateException(
                        "Invalid booking payment status"
                );
            }

            String lotId = booking.getString("lotId");
            String spotId = booking.getString("spotId");

            if (lotId == null || spotId == null) {
                throw new IllegalStateException(
                        "Invalid parking spot information"
                );
            }

            DocumentReference spotRef =
                    db.collection(LOTS)
                            .document(lotId)
                            .collection(SPOTS)
                            .document(spotId);

            DocumentSnapshot spot =
                    transaction.get(spotRef);

            if (!spot.exists()
                    || !Spot.STATUS_TAKEN.equals(
                    spot.getString("status"))
                    || !userId.equals(
                    spot.getString("occupiedBy"))
                    || !bookingId.equals(
                    spot.getString("bookingId"))) {

                throw new IllegalStateException(
                        "This spot is no longer assigned to your booking"
                );
            }

            // Record when the user ended the booking.
            Map<String, Object> bookingChanges =
                    new HashMap<>();

            bookingChanges.put(
                    "endedAt",
                    FieldValue.serverTimestamp()
            );

            // Release the parking spot.
            Map<String, Object> spotChanges =
                    new HashMap<>();

            spotChanges.put("status", Spot.STATUS_FREE);
            spotChanges.put("occupiedBy", null);
            spotChanges.put("reservedUntil", null);
            spotChanges.put("bookingId", null);
            spotChanges.put("sessionEndsAt", null);

            spotChanges.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );

            transaction.update(
                    bookingRef,
                    bookingChanges
            );

            transaction.update(
                    spotRef,
                    spotChanges
            );

            return null;
        });

    }

    public Task<Void> extendParkingSession(
            String bookingId,
            String userId,
            int additionalHours) {

        DocumentReference bookingRef =
                db.collection("bookings")
                        .document(bookingId);

        return db.<Void>runTransaction(transaction -> {

            if (additionalHours < 1 || additionalHours > 4) {
                throw new IllegalArgumentException(
                        "Invalid extension duration"
                );
            }

            DocumentSnapshot booking =
                    transaction.get(bookingRef);

            if (!booking.exists()) {
                throw new IllegalStateException(
                        "Booking does not exist"
                );
            }

            if (!userId.equals(booking.getString("userId"))) {
                throw new IllegalStateException(
                        "This booking does not belong to you"
                );
            }

            if (booking.getTimestamp("endedAt") != null) {
                throw new IllegalStateException(
                        "Parking session has already ended"
                );
            }

            if (!"SIMULATED_PAID".equals(
                    booking.getString("paymentStatus"))) {

                throw new IllegalStateException(
                        "Invalid payment status"
                );
            }

            Timestamp oldEndsAt =
                    booking.getTimestamp("endsAt");

            Long oldDuration =
                    booking.getLong("durationHours");

            Long oldAmount =
                    booking.getLong("amountCents");

            if (oldEndsAt == null
                    || oldDuration == null
                    || oldAmount == null) {

                throw new IllegalStateException(
                        "Invalid booking information"
                );
            }

            if (oldEndsAt.toDate().getTime()
                    <= System.currentTimeMillis()) {

                throw new IllegalStateException(
                        "Parking session has already expired"
                );
            }

            if (oldDuration + additionalHours > 24) {

                throw new IllegalStateException(
                        "Maximum total parking duration is 24 hours"
                );
            }

            String lotId = booking.getString("lotId");
            String spotId = booking.getString("spotId");

            if (lotId == null || spotId == null) {

                throw new IllegalStateException(
                        "Invalid parking spot information"
                );
            }

            DocumentReference spotRef =
                    db.collection(LOTS)
                            .document(lotId)
                            .collection(SPOTS)
                            .document(spotId);

            DocumentSnapshot spot =
                    transaction.get(spotRef);

            if (!spot.exists()
                    || !Spot.STATUS_TAKEN.equals(
                    spot.getString("status"))
                    || !userId.equals(
                    spot.getString("occupiedBy"))
                    || !bookingId.equals(
                    spot.getString("bookingId"))
                    || !oldEndsAt.equals(
                    spot.getTimestamp("sessionEndsAt"))) {

                throw new IllegalStateException(
                        "Parking spot is no longer assigned to you"
                );
            }

            // Calculate the new expiry time.
            Timestamp newEndsAt = new Timestamp(
                    oldEndsAt.getSeconds()
                            + additionalHours * 3600L,
                    oldEndsAt.getNanoseconds()
            );

            // Calculate the additional simulated payment.
            long additionalCost = additionalHours * 200L;

            // Update the existing booking.
            Map<String, Object> bookingChanges =
                    new HashMap<>();

            bookingChanges.put(
                    "durationHours",
                    oldDuration + additionalHours
            );

            bookingChanges.put(
                    "amountCents",
                    oldAmount + additionalCost
            );

            bookingChanges.put("endsAt", newEndsAt);

            bookingChanges.put(
                    "lastExtendedAt",
                    FieldValue.serverTimestamp()
            );

            // Update the parking spot's expiry.
            Map<String, Object> spotChanges =
                    new HashMap<>();

            spotChanges.put("sessionEndsAt", newEndsAt);

            spotChanges.put(
                    "updatedAt",
                    FieldValue.serverTimestamp()
            );

            transaction.update(bookingRef, bookingChanges);
            transaction.update(spotRef, spotChanges);

            return null;
        });
    }




}
