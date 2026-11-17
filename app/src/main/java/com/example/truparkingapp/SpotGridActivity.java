package com.example.truparkingapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.firestore.ListenerRegistration;

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

        adapter = new SpotGridAdapter();
        layoutManager = new GridLayoutManager(this, Math.max(rows, 1),
                RecyclerView.HORIZONTAL, false);
        RecyclerView recycler = findViewById(R.id.recycler_spots);
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
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
    }

    private void showSpots(List<Spot> spots) {
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
