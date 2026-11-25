package com.example.truparkingapp;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Lays a lot's spots out cell by cell, using each spot's row and column.
 *
 * The grid scrolls sideways, so the RecyclerView uses a horizontal
 * GridLayoutManager with one span per row. That layout fills one column top to
 * bottom before moving right, so cell (row, column) lives at
 * position column * rows + row. Cells without a spot (like the corners of
 * Lot C) stay empty and invisible.
 */
public class SpotGridAdapter extends RecyclerView.Adapter<SpotGridAdapter.SpotViewHolder> {

    public interface OnSpotClickListener{
        void onSpotClicked(Spot spot);
    }
    private final OnSpotClickListener clickListener;
    public SpotGridAdapter(OnSpotClickListener clickListener){
        this.clickListener = clickListener;
    }
    private int rows;
    private int columns;
    private Spot[] cells = new Spot[0];

    /** Rebuilds the grid. Spots outside rows x columns are ignored. */
    public void setSpots(List<Spot> spots, int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
        cells = new Spot[rows * columns];
        for (Spot spot : spots) {
            int row = spot.getRow();
            int column = spot.getColumn();
            if (row >= 0 && row < rows && column >= 0 && column < columns) {
                cells[column * rows + row] = spot;
            }
        }
        // At most a few hundred small cells, so redrawing all of them is cheap.
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return cells.length;
    }

    @NonNull
    @Override
    public SpotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_spot, parent, false);
        return new SpotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SpotViewHolder holder, int position) {
        holder.bind(cells[position]);
    }

    class SpotViewHolder extends RecyclerView.ViewHolder {

        private final TextView tile;

        SpotViewHolder(@NonNull View itemView) {
            super(itemView);
            tile = (TextView) itemView;
        }

        void bind(Spot spot) {
            if (spot == null) {
                // Keep the space so the other spots stay in their real positions.
                tile.setVisibility(View.INVISIBLE);
                tile.setOnClickListener(null);
                tile.setClickable(false);
                tile.setFocusable(false);
                tile.setText(null);
                tile.setContentDescription(null);
                tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                return;
            }
            boolean free = spot.isAvailable();
            tile.setVisibility(View.VISIBLE);
            tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            tile.setText(String.valueOf(spot.getNumber()));
            if(free){
                tile.setOnClickListener(v -> clickListener.onSpotClicked(spot));
                tile.setClickable(true);
                tile.setFocusable(true);
            }else{
                tile.setOnClickListener(null);
                tile.setClickable(false);
                tile.setFocusable(false);
            }
            ViewCompat.setBackgroundTintList(tile, ColorStateList.valueOf(
                    ContextCompat.getColor(tile.getContext(),
                            free ? R.color.spot_available : R.color.spot_occupied)));
            tile.setContentDescription(tile.getContext().getString(
                    free ? R.string.spot_free_description : R.string.spot_taken_description,
                    spot.getNumber()));
        }
    }
}
