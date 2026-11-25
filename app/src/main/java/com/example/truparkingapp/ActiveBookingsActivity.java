
package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ActiveBookingsActivity extends BaseActivity {

    private final LotRepository lotRepository =
            new LotRepository();

    private ListenerRegistration bookingsRegistration;

    private ActiveBookingsAdapter adapter;

    private ProgressBar progress;

    private TextView emptyText;
    private TextView errorText;
    private TextView countText;

    private List<Booking> allBookings = new ArrayList<>();

    private boolean bookingsLoaded = false;

    private final Handler timerHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable refreshCountdowns = new Runnable() {

        @Override
        public void run() {

            if (bookingsLoaded) {

                List<Booking> activeBookings =
                        getActiveBookings();

                if (activeBookings.size()
                        != adapter.getItemCount()) {

                    showActiveBookings();

                } else {

                    adapter.refreshTimers();
                }
            }

            timerHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_active_bookings
        );

        WindowInsetsHelper.padForSystemBarsAndKeyboard(
                findViewById(R.id.active_bookings_root)
        );

        MaterialToolbar toolbar = findViewById(
                R.id.toolbar_active_bookings
        );

        toolbar.setNavigationOnClickListener(
                v -> finish()
        );

        progress = findViewById(
                R.id.progress_active_bookings
        );

        emptyText = findViewById(
                R.id.text_no_active_bookings
        );

        errorText = findViewById(
                R.id.text_active_bookings_error
        );

        countText = findViewById(
                R.id.text_active_booking_count
        );

        RecyclerView recycler = findViewById(
                R.id.recycler_active_bookings
        );

        adapter = new ActiveBookingsAdapter(
                this::openParkingSession
        );

        recycler.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recycler.setAdapter(adapter);

        recycler.setItemAnimator(null);
    }

    @Override
    protected void onStart() {

        super.onStart();

        FirebaseUser user =
                new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()) {

            Toast.makeText(this,
                    "Please sign in to view your bookings",
                    Toast.LENGTH_SHORT).show();

            finish();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);

        bookingsLoaded = false;

        allBookings.clear();
        adapter.setBookings(new ArrayList<>());
        countText.setText("");

        bookingsRegistration =
                lotRepository.listenToUserBookings(
                        user.getUid(),

                        new LotRepository.Listener<List<Booking>>() {

                            @Override
                            public void onChanged(List<Booking> result) {

                                allBookings = result;

                                bookingsLoaded = true;

                                progress.setVisibility(View.GONE);
                                errorText.setVisibility(View.GONE);

                                showActiveBookings();
                            }

                            @Override
                            public void onError(Exception e) {

                                bookingsLoaded = false;

                                progress.setVisibility(View.GONE);
                                emptyText.setVisibility(View.GONE);

                                allBookings.clear();
                                adapter.setBookings(new ArrayList<>());
                                countText.setText("");

                                errorText.setText(
                                        "Unable to load bookings. "
                                                + "Please check your internet connection."
                                );

                                errorText.setVisibility(View.VISIBLE);
                            }
                        });

        timerHandler.post(refreshCountdowns);
    }

    private List<Booking> getActiveBookings() {

        List<Booking> activeBookings = new ArrayList<>();

        for (Booking booking : allBookings) {

            if (booking.isActive()) {
                activeBookings.add(booking);
            }
        }

        // Display bookings expiring soonest first.
        activeBookings.sort((first, second) ->
                first.getEndsAt().compareTo(
                        second.getEndsAt()
                )
        );

        return activeBookings;
    }

    private void showActiveBookings() {

        List<Booking> activeBookings =
                getActiveBookings();

        adapter.setBookings(activeBookings);

        int count = activeBookings.size();

        countText.setText(
                count == 1
                        ? "1 active booking"
                        : count + " active bookings"
        );

        emptyText.setVisibility(
                activeBookings.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void openParkingSession(Booking booking) {

        if (!booking.isActive()
                || booking.getId() == null) {

            showActiveBookings();
            return;
        }

        Intent intent = new Intent(
                ActiveBookingsActivity.this,
                ParkingSessionActivity.class
        );

        intent.putExtra(
                "bookingId",
                booking.getId()
        );

        startActivity(intent);
    }

    @Override
    protected void onStop() {

        timerHandler.removeCallbacks(
                refreshCountdowns
        );

        LotRepository.stopListening(
                bookingsRegistration
        );

        bookingsRegistration = null;

        super.onStop();
    }
}
