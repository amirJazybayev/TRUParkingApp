package com.example.truparkingapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * One lot's spots as a live grid: green tiles are free, grey tiles are taken.
 * Opened by tapping a lot card on the parking lots screen.
 */
public class SpotGridActivity extends BaseActivity {

    private static final String EXTRA_LOT_ID = "com.example.truparkingapp.LOT_ID";
    private static final String EXTRA_LOT_NAME = "com.example.truparkingapp.LOT_NAME";
    private static final String EXTRA_ROWS = "com.example.truparkingapp.ROWS";
    private static final String EXTRA_COLUMNS = "com.example.truparkingapp.COLUMNS";

    /** Builds the intent that opens the grid for one lot. */
    public static Intent newIntent(Context context, Lot lot) {
        return new Intent(context, SpotGridActivity.class)
                .putExtra(EXTRA_LOT_ID, lot.getId())
                .putExtra(EXTRA_LOT_NAME, lot.getName())
                .putExtra(EXTRA_ROWS, lot.getRows())
                .putExtra(EXTRA_COLUMNS, lot.getColumns());
    }

    private final LotRepository lotRepository = new LotRepository();
    private ListenerRegistration spotsRegistration;

    private String lotId;
    private int rows;
    private int columns;

    private SpotGridAdapter adapter;
    private GridLayoutManager layoutManager;
    private TextView available;
    private TextView errorBanner;
    private TextView emptyText;

    private CircularProgressIndicator progress;
    private boolean reservationPending;
    private List<Spot> lastSpots = new ArrayList<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshExpiredHolds = new Runnable() {
        @Override
        public void run() {
            if(!lastSpots.isEmpty()){
                showSpots(lastSpots);
            }
            refreshHandler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lotId = getIntent().getStringExtra(EXTRA_LOT_ID);
        if (lotId == null) {
            finish();
            return;
        }
        rows = getIntent().getIntExtra(EXTRA_ROWS, 0);
        columns = getIntent().getIntExtra(EXTRA_COLUMNS, 0);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_spot_grid);
        WindowInsetsHelper.padForSystemBarsAndKeyboard(findViewById(R.id.spot_grid_root));

        MaterialToolbar toolbar = findViewById(R.id.toolbar_spot_grid);
        toolbar.setNavigationOnClickListener(v -> finish());

        String lotName = getIntent().getStringExtra(EXTRA_LOT_NAME);
        ((TextView) findViewById(R.id.text_grid_lot_name)).setText(lotName);

        available = findViewById(R.id.text_grid_available);
        errorBanner = findViewById(R.id.text_grid_error);
        emptyText = findViewById(R.id.text_spots_empty);
        progress = findViewById(R.id.progress_spots);

        adapter = new SpotGridAdapter(this::onSpotClicked);
        layoutManager = new GridLayoutManager(this, Math.max(rows, 1),
                RecyclerView.HORIZONTAL, false);
        RecyclerView recycler = findViewById(R.id.recycler_spots);
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshHandler.postDelayed(refreshExpiredHolds,60_000L);
        spotsRegistration = lotRepository.listenToSpots(lotId,
                new LotRepository.Listener<List<Spot>>() {
                    @Override
                    public void onChanged(List<Spot> spots) {
                        showSpots(spots);
                    }

                    @Override
                    public void onError(Exception e) {
                        progress.setVisibility(View.GONE);
                        errorBanner.setText(AuthErrors.isNetworkError(e)
                                ? R.string.error_no_internet
                                : R.string.error_generic);
                        errorBanner.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        LotRepository.stopListening(spotsRegistration);
        spotsRegistration = null;
        refreshHandler.removeCallbacks(refreshExpiredHolds);
    }
    
    public void onSpotClicked(Spot spot){
        if(reservationPending) return;
        if(session.isGuest()){
            Toast.makeText(this, "Please sign in to reserve a parking spot.", Toast.LENGTH_SHORT).show();
            return;
        }
        if(!spot.isAvailable()){
            Toast.makeText(this, "This spot is no longer available, or your reservation was not permitted.", Toast.LENGTH_SHORT).show();
            return;
        }
        String type = spot.getType() == null ? "REGULAR" : spot.getType();
        new MaterialAlertDialogBuilder(this).setTitle("Reserve spot "+spot.getNumber())
                .setMessage("Type: "+type+"\nHold duration: 15 minutes\n\nThis spot will be temporarily reserved for you.")
                .setNegativeButton("Cancel Spot",null)
                .setPositiveButton("Reserve Spot",(dialog,which)->reserveSpot(spot))
                .show();
    }
    
    public void reserveSpot(Spot spot){
        FirebaseUser user = new UserRepository().getCurrentUser();
        if(user == null || user.isAnonymous()){
            Toast.makeText(this, "Please sign in to reserve a parking spot", Toast.LENGTH_SHORT).show();
            return;
        }
        if(reservationPending || !spot.isAvailable() || spot.getId()==null){
            return;
        }
        reservationPending = true;
        lotRepository.reserveSpot(lotId, spot.getId(),user.getUid(), new LotRepository.ReservationCallback(){
            @Override
            public void onSuccess(Timestamp reservedUntil){
                reservationPending = false;
                Intent intent = new Intent(SpotGridActivity.this, ReservationActivity.class);
                intent.putExtra("lotId",lotId);
                intent.putExtra("spotId",spot.getId());
                intent.putExtra("spotNumber", String.valueOf(spot.getNumber()));
                intent.putExtra("reservedUntil",reservedUntil.toDate().getTime());

                startActivity(intent);
            }

            @Override
            public void onError(Exception error) {
                reservationPending = false;
                boolean rejected = error instanceof FirebaseFirestoreException
                        &&((FirebaseFirestoreException)error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED;
                Toast.makeText(SpotGridActivity.this, rejected?"This spot is no longer available, or your reservation was not permitted":"Could not reserve this spot. Please check your connection and try again", Toast.LENGTH_SHORT).show();
            }


        });
    }

    private void showSpots(List<Spot> spots) {
        lastSpots = spots;
        progress.setVisibility(View.GONE);
        errorBanner.setVisibility(View.GONE);
        emptyText.setVisibility(spots.isEmpty() ? View.VISIBLE : View.GONE);

        // If the lot document has no size yet, size the grid from the spots.
        int gridRows = rows;
        int gridColumns = columns;
        if (gridRows <= 0 || gridColumns <= 0) {
            for (Spot spot : spots) {
                gridRows = Math.max(gridRows, spot.getRow() + 1);
                gridColumns = Math.max(gridColumns, spot.getColumn() + 1);
            }
        }
        layoutManager.setSpanCount(Math.max(gridRows, 1));
        adapter.setSpots(spots, gridRows, gridColumns);

        SpotCounts counts = SpotCounts.of(spots);
        available.setText(getString(R.string.grid_available, counts.free, counts.total));
        available.setTextColor(getColor(counts.free > 0
                ? R.color.spot_available
                : R.color.spot_occupied));
    }
}
