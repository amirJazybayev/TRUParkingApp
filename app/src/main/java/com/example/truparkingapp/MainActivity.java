package com.example.truparkingapp;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class MainActivity extends BaseActivity {

    // TEMP: Logcat check for the lots data. Remove once the lots screen exists.
    private static final String TAG = "LotCheck";

    private final LotRepository lotRepository = new LotRepository();
    private ListenerRegistration lotsRegistration;
    private ListenerRegistration spotsRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        logLotsForTesting();
    }

    @Override
    protected void onStop() {
        super.onStop();
        LotRepository.stopListening(lotsRegistration);
        LotRepository.stopListening(spotsRegistration);
        lotsRegistration = null;
        spotsRegistration = null;
    }

    // TEMP: prints every lot, then the spots of the first lot.
    private void logLotsForTesting() {
        lotsRegistration = lotRepository.listenToLots(new LotRepository.Listener<List<Lot>>() {
            @Override
            public void onChanged(List<Lot> lots) {
                Log.d(TAG, "Got " + lots.size() + " active lot(s)");
                for (Lot lot : lots) {
                    Log.d(TAG, "Lot " + lot.getId() + ": " + lot.getName()
                            + ", free " + lot.getFreeSpots() + "/" + lot.getTotalSpots()
                            + ", sortOrder " + lot.getSortOrder());
                }
                if (!lots.isEmpty() && spotsRegistration == null) {
                    logSpotsForTesting(lots.get(0).getId());
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Could not load lots", e);
            }
        });
    }

    private void logSpotsForTesting(String lotId) {
        spotsRegistration = lotRepository.listenToSpots(lotId, new LotRepository.Listener<List<Spot>>() {
            @Override
            public void onChanged(List<Spot> spots) {
                Log.d(TAG, "Lot " + lotId + " has " + spots.size() + " spot(s)");
                for (Spot spot : spots) {
                    Log.d(TAG, "  Spot " + spot.getId() + " #" + spot.getNumber()
                            + " (" + spot.getRow() + "," + spot.getColumn() + "): "
                            + spot.getStatus());
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Could not load spots for lot " + lotId, e);
            }
        });
    }
}
