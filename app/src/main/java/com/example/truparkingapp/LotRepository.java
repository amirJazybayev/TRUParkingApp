package com.example.truparkingapp;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Every Firebase call for parking lots goes through here, so activities never
 * talk to Firestore directly.
 *
 * Firestore layout:
 *   lots/{code}                 -> Lot
 *   lots/{code}/spots/{spotId}  -> Spot
 *
 * The listen methods keep running and call back on every change. Each one
 * returns a ListenerRegistration: call remove() on it (or pass it to
 * stopListening) in onStop, otherwise the listener keeps the screen alive.
 */
public class LotRepository {

    /** Called once with the current data, then again after every change. */
    public interface Listener<T> {
        void onChanged(T result);

        void onError(Exception e);
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

    /** Safe to call with null, so onStop does not need its own null checks. */
    public static void stopListening(ListenerRegistration registration) {
        if (registration != null) {
            registration.remove();
        }
    }
}
