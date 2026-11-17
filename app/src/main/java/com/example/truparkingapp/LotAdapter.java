package com.example.truparkingapp;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shows one card per parking lot. Uses ListAdapter so that when Firestore sends
 * a new list, only the cards that changed redraw.
 *
 * The free count comes from the lot's spot documents when they have loaded
 * (see setSpotCounts), so it changes the moment a spot is taken or freed. Until
 * then the card falls back to the totalSpots / freeSpots fields on the lot.
 */
public class LotAdapter extends ListAdapter<Lot, LotAdapter.LotViewHolder> {

    /** What the screen does when a button on a card is pressed. */
    public interface OnLotActionListener {
        void onOpenLot(Lot lot);

        void onShowOnMap(Lot lot);
    }

    private final OnLotActionListener listener;
    private final Map<String, SpotCounts> spotCounts = new HashMap<>();

    public LotAdapter(OnLotActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public LotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lot, parent, false);
        return new LotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LotViewHolder holder, int position) {
        Lot lot = getItem(position);
        holder.bind(lot, countsFor(lot), listener);
    }

    /** Live counts for one lot, worked out from its spots. Redraws only that card. */
    public void setSpotCounts(String lotId, SpotCounts counts) {
        spotCounts.put(lotId, counts);
        List<Lot> lots = getCurrentList();
        for (int i = 0; i < lots.size(); i++) {
            if (Objects.equals(lots.get(i).getId(), lotId)) {
                notifyItemChanged(i);
            }
        }
    }

    /** Forget a lot's live counts (it was removed from the list). */
    public void clearSpotCounts(String lotId) {
        spotCounts.remove(lotId);
    }

    /** Counts from the spots if they have loaded, otherwise from the lot document. */
    public SpotCounts countsFor(Lot lot) {
        SpotCounts live = spotCounts.get(lot.getId());
        if (live != null) {
            return live;
        }
        int total = Math.max(lot.getTotalSpots(), 0);
        return new SpotCounts(Math.max(0, Math.min(lot.getFreeSpots(), total)), total);
    }

    static class LotViewHolder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView code;
        private final TextView description;
        private final TextView free;
        private final TextView total;
        private final LinearProgressIndicator occupancy;
        private final MaterialButton viewSpots;
        private final MaterialButton showOnMap;

        LotViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_lot_name);
            code = itemView.findViewById(R.id.text_lot_code);
            description = itemView.findViewById(R.id.text_lot_description);
            free = itemView.findViewById(R.id.text_lot_free);
            total = itemView.findViewById(R.id.text_lot_total);
            occupancy = itemView.findViewById(R.id.progress_lot_occupancy);
            viewSpots = itemView.findViewById(R.id.button_view_spots);
            showOnMap = itemView.findViewById(R.id.button_show_on_map);
        }

        void bind(Lot lot, SpotCounts counts, OnLotActionListener listener) {
            Context context = itemView.getContext();

            name.setText(lot.getName());
            String lotCode = lot.getCode() != null ? lot.getCode() : lot.getId();
            code.setText(lotCode);
            code.setVisibility(TextUtils.isEmpty(lotCode) ? View.GONE : View.VISIBLE);

            boolean hasDescription = !TextUtils.isEmpty(lot.getDescription());
            description.setText(lot.getDescription());
            description.setVisibility(hasDescription ? View.VISIBLE : View.GONE);

            int totalSpots = counts.total;
            int freeSpots = counts.free;
            boolean full = freeSpots == 0;

            free.setText(String.valueOf(freeSpots));
            free.setTextColor(ContextCompat.getColor(context,
                    full ? R.color.spot_occupied : R.color.spot_available));
            total.setText(context.getString(
                    full ? R.string.lot_full : R.string.lot_free_of_total, totalSpots));

            int percentTaken = totalSpots == 0 ? 0 : (totalSpots - freeSpots) * 100 / totalSpots;
            occupancy.setProgressCompat(percentTaken, false);

            // Read the count as one phrase with TalkBack instead of "12", "free of 40 spots".
            free.setContentDescription(
                    context.getString(R.string.lot_free_description, freeSpots, totalSpots));

            itemView.setOnClickListener(v -> listener.onOpenLot(lot));
            viewSpots.setOnClickListener(v -> listener.onOpenLot(lot));
            viewSpots.setContentDescription(
                    context.getString(R.string.lot_open_description, lot.getName()));
            showOnMap.setOnClickListener(v -> listener.onShowOnMap(lot));
        }
    }

    /** Same lot = same document id; same contents = every field shown on the card matches. */
    private static final DiffUtil.ItemCallback<Lot> DIFF = new DiffUtil.ItemCallback<Lot>() {
        @Override
        public boolean areItemsTheSame(@NonNull Lot oldLot, @NonNull Lot newLot) {
            return Objects.equals(oldLot.getId(), newLot.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Lot oldLot, @NonNull Lot newLot) {
            return Objects.equals(oldLot.getName(), newLot.getName())
                    && Objects.equals(oldLot.getCode(), newLot.getCode())
                    && Objects.equals(oldLot.getDescription(), newLot.getDescription())
                    && oldLot.getFreeSpots() == newLot.getFreeSpots()
                    && oldLot.getTotalSpots() == newLot.getTotalSpots();
        }
    };
}
