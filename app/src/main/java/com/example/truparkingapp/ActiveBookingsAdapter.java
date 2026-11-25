
package com.example.truparkingapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActiveBookingsAdapter extends
        RecyclerView.Adapter<ActiveBookingsAdapter.BookingViewHolder> {

    public interface OnBookingClickListener {
        void onBookingClick(Booking booking);
    }

    private final List<Booking> bookings = new ArrayList<>();

    private final OnBookingClickListener listener;

    public ActiveBookingsAdapter(
            OnBookingClickListener listener) {

        this.listener = listener;
    }

    public void setBookings(List<Booking> newBookings) {

        bookings.clear();
        bookings.addAll(newBookings);

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_active_booking,
                        parent,
                        false
                );

        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull BookingViewHolder holder,
            int position) {

        Booking booking = bookings.get(position);

        holder.lotText.setText(
                "Parking Lot " + booking.getLotId()
        );

        holder.spotText.setText(
                "Spot " + booking.getSpotNumber()
        );

        holder.durationText.setText(
                "Duration: "
                        + booking.getDurationHours()
                        + " hour(s)"
        );

        holder.amountText.setText(String.format(
                Locale.CANADA,
                "Amount paid: $%.2f",
                booking.getAmountCents() / 100.0
        ));

        if (booking.getEndsAt() != null) {

            SimpleDateFormat formatter =
                    new SimpleDateFormat(
                            "MMM d, h:mm a",
                            Locale.CANADA
                    );

            holder.expiryText.setText(
                    "Expires: "
                            + formatter.format(
                            booking.getEndsAt().toDate()
                    )
            );
        }

        updateTimer(holder, booking);

        holder.viewButton.setOnClickListener(v -> {

            if (booking.isActive()) {
                listener.onBookingClick(booking);
            }
        });
    }

    @Override
    public void onBindViewHolder(
            @NonNull BookingViewHolder holder,
            int position,
            @NonNull List<Object> payloads) {

        if (payloads.isEmpty()) {

            onBindViewHolder(holder, position);

        } else {

            updateTimer(holder, bookings.get(position));
        }
    }

    private void updateTimer(
            BookingViewHolder holder,
            Booking booking) {

        if (booking.getEndsAt() == null) {
            holder.timerText.setText("--:--:--");
            holder.viewButton.setEnabled(false);
            return;
        }

        long remaining =
                booking.getEndsAt().toDate().getTime()
                        - System.currentTimeMillis();

        remaining = Math.max(0, remaining);

        long totalSeconds = (remaining + 999) / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        holder.timerText.setText(String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
        ));

        holder.viewButton.setEnabled(booking.isActive());
    }

    public void refreshTimers() {

        if (!bookings.isEmpty()) {

            notifyItemRangeChanged(
                    0,
                    bookings.size(),
                    "TIMER"
            );
        }
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    static class BookingViewHolder
            extends RecyclerView.ViewHolder {

        TextView lotText;
        TextView spotText;
        TextView timerText;
        TextView durationText;
        TextView amountText;
        TextView expiryText;

        Button viewButton;

        public BookingViewHolder(
                @NonNull View itemView) {

            super(itemView);

            lotText = itemView.findViewById(
                    R.id.text_card_lot
            );

            spotText = itemView.findViewById(
                    R.id.text_card_spot
            );

            timerText = itemView.findViewById(
                    R.id.text_card_timer
            );

            durationText = itemView.findViewById(
                    R.id.text_card_duration
            );

            amountText = itemView.findViewById(
                    R.id.text_card_amount
            );

            expiryText = itemView.findViewById(
                    R.id.text_card_expiry
            );

            viewButton = itemView.findViewById(
                    R.id.button_view_session
            );
        }
    }
}
