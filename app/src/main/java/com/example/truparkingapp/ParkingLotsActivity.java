package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map;

/**
 * First screen after logging in (or continuing as a guest). Lists every active
 * parking lot with how many spots are free, and updates live as spots change.
 *
 * Each lot's free count is worked out from its spot documents, with one live
 * spots listener per lot, so a spot turning FREE or TAKEN in Firestore updates
 * the card straight away.
 */
public class ParkingLotsActivity extends BaseActivity implements LotAdapter.OnLotActionListener {

    private final LotRepository lotRepository = new LotRepository();
    private ListenerRegistration lotsRegistration;
    private final Map<String, ListenerRegistration> spotRegistrations = new HashMap<>();
    private List<Lot> currentLots;

    private LotAdapter adapter;
    private TextView summary;
    private TextView errorBanner;
    private TextView emptyText;
    private CircularProgressIndicator progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_parking_lots);
        WindowInsetsHelper.padForSystemBarsAndKeyboard(findViewById(R.id.lots_root));

        MaterialToolbar toolbar = findViewById(R.id.toolbar_lots);
        setSupportActionBar(toolbar);

        summary = findViewById(R.id.text_lots_summary);
        errorBanner = findViewById(R.id.text_lots_error);
        emptyText = findViewById(R.id.text_lots_empty);
        progress = findViewById(R.id.progress_lots);
        findViewById(R.id.text_guest_note)
                .setVisibility(session.isGuest() ? View.VISIBLE : View.GONE);

        adapter = new LotAdapter(this);
        RecyclerView recycler = findViewById(R.id.recycler_lots);
        recycler.setAdapter(adapter);}

    @Override
    protected void onStart() {
        super.onStart();
        lotsRegistration = lotRepository.listenToLots(new LotRepository.Listener<List<Lot>>() {
            @Override
            public void onChanged(List<Lot> lots) {
                showLots(lots);
            }

            @Override
            public void onError(Exception e) {
                showLoadError(e);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        LotRepository.stopListening(lotsRegistration);
        lotsRegistration = null;
        for (ListenerRegistration registration : spotRegistrations.values()) {
            LotRepository.stopListening(registration);
        }
        spotRegistrations.clear();
    }

    private void showLots(List<Lot> lots) {
        progress.setVisibility(View.GONE);
        errorBanner.setVisibility(View.GONE);
        emptyText.setVisibility(lots.isEmpty() ? View.VISIBLE : View.GONE);
        currentLots = lots;
        adapter.submitList(lots);
        watchSpotsOf(lots);
        updateSummary();
    }

    /** Starts a spots listener for each new lot and stops the ones for removed lots. */
    private void watchSpotsOf(List<Lot> lots) {
        Set<String> ids = new HashSet<>();
        for (Lot lot : lots) {
            String lotId = lot.getId();
            ids.add(lotId);
            if (spotRegistrations.containsKey(lotId)) {
                continue;
            }
            spotRegistrations.put(lotId, lotRepository.listenToSpots(lotId,
                    new LotRepository.Listener<List<Spot>>() {
                        @Override
                        public void onChanged(List<Spot> spots) {
                            // A lot with no spot documents yet keeps the numbers on its lot document.
                            if (spots.isEmpty()) {
                                adapter.clearSpotCounts(lotId);
                            } else {
                                adapter.setSpotCounts(lotId, SpotCounts.of(spots));
                            }
                            updateSummary();
                        }

                        @Override
                        public void onError(Exception e) {
                            // The card keeps showing the lot document's numbers.
                        }
                    }));
        }
        Iterator<Map.Entry<String, ListenerRegistration>> it =
                spotRegistrations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ListenerRegistration> entry = it.next();
            if (!ids.contains(entry.getKey())) {
                LotRepository.stopListening(entry.getValue());
                adapter.clearSpotCounts(entry.getKey());
                it.remove();
            }
        }
    }

    private void updateSummary() {
        if (currentLots == null) return;
        int freeTotal = 0;
        for (Lot lot : currentLots) {
            freeTotal += adapter.countsFor(lot).free;
        }
        int lotCount = currentLots.size();
        summary.setText(getString(R.string.lots_summary,
                getResources().getQuantityString(R.plurals.lots_count, lotCount, lotCount),
                getResources().getQuantityString(R.plurals.free_spots_count, freeTotal, freeTotal)));
    }

    @Override
    public void onOpenLot(Lot lot) {
        startActivity(SpotGridActivity.newIntent(this, lot));
    }

    private void showLoadError(Exception e) {
        progress.setVisibility(View.GONE);
        boolean offline = e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.UNAVAILABLE;
        errorBanner.setText(offline ? R.string.error_no_internet : R.string.error_generic);
        errorBanner.setVisibility(View.VISIBLE);
        if (adapter.getItemCount() == 0) {
            summary.setText(null);
        }
    }

    @Override
    public void onShowOnMap(Lot lot) {
        // Placeholder until the lot location is added to the data.
        Toast.makeText(this, getString(R.string.show_on_map_coming_soon, lot.getName()),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_parking_lots, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_log_out) {
            logOut();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logOut() {
        new UserRepository().signOut();
        session.end();
        Toast.makeText(this, R.string.logged_out, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
