
package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Locale;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ParkingSessionActivity extends BaseActivity {

    private String bookingId;

    private TextView titleText;
    private TextView detailsText;
    private TextView timerText;
    private TextView endTimeText;

    private Button extendButton;

    private CountDownTimer countDownTimer;

    private ListenerRegistration bookingRegistration;

    private Button endButton;
    private boolean extensionPending = false;

    private int currentDurationHours = 0;

    private boolean endingPending = false;
    private boolean sessionActive = false;

    private final LotRepository lotRepository =
            new LotRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parking_session);

        WindowInsetsHelper.padForSystemBarsAndKeyboard(
                findViewById(R.id.session_root)
        );

        bookingId = getIntent().getStringExtra("bookingId");

        if (bookingId == null) {
            finish();
            return;
        }

        titleText = findViewById(R.id.text_session_title);

        detailsText =
                findViewById(R.id.text_session_details);

        timerText = findViewById(R.id.text_session_timer);

        endTimeText =
                findViewById(R.id.text_session_end_time);

        extendButton =
                findViewById(R.id.button_extend_session);
        extendButton.setEnabled(false);

        extendButton.setOnClickListener(
                v -> showExtensionDialog()
        );
        endButton = findViewById(R.id.button_end_session);

        endButton.setEnabled(false);

        endButton.setOnClickListener(v -> confirmEndSession());

        Button doneButton =
                findViewById(R.id.button_session_done);

        doneButton.setOnClickListener(v -> {

            Intent intent = new Intent(
                    ParkingSessionActivity.this,
                    ActiveBookingsActivity.class
            );

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onStart() {

        super.onStart();

        if (isFinishing()) return;
        sessionActive = false;
        endButton.setEnabled(false);
        extendButton.setEnabled(false);
        FirebaseUser user =
                new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            finish();
            return;
        }

        bookingRegistration =
                lotRepository.listenToBooking(
                        bookingId,
                        (snapshot, error) -> {

                            if (isFinishing()) return;

                            if (error != null || snapshot == null) {
                                sessionActive = false;
                                endButton.setEnabled(false);
                                extendButton.setEnabled(false);
                                titleText.setText(
                                        "Unable to verify parking session"
                                );

                                return;
                            }

                            if (snapshot.getMetadata().isFromCache()
                                    || snapshot.getMetadata().hasPendingWrites()) {
                                sessionActive = false;
                                endButton.setEnabled(false);
                                extendButton.setEnabled(false);
                                return;
                            }

                            if (!snapshot.exists()
                                    || !user.getUid().equals(
                                    snapshot.getString("userId"))) {

                                Toast.makeText(this,
                                        "Booking not found",
                                        Toast.LENGTH_SHORT).show();

                                finish();
                                return;
                            }
                            if (snapshot.getTimestamp("endedAt") != null) {
                                sessionEnded();
                                return;
                            }
                            String lotId = snapshot.getString("lotId");

                            String spotNumber = String.valueOf(
                                    snapshot.getLong("spotNumber")
                            );

                            Long amount = snapshot.getLong("amountCents");
                            Long duration = snapshot.getLong("durationHours");

                            currentDurationHours =
                                    duration != null ? duration.intValue() : 0;

                            Timestamp endsAt =
                                    snapshot.getTimestamp("endsAt");

                            if (amount == null || endsAt == null
                                    || duration == null || duration <= 0) {

                                sessionActive = false;
                                endButton.setEnabled(false);
                                extendButton.setEnabled(false);

                                if (countDownTimer != null) {
                                    countDownTimer.cancel();
                                }

                                titleText.setText("Invalid booking information");

                                return;
                            }

                            detailsText.setText(
                                    "Parking Lot: " + lotId
                                            + "\nParking Spot: " + spotNumber
                                            + "\nDemo payment: "
                                            + String.format(
                                            Locale.CANADA,
                                            "$%.2f",
                                            amount / 100.0
                                    )
                            );

                            SimpleDateFormat formatter =
                                    new SimpleDateFormat(
                                            "MMM d, h:mm a",
                                            Locale.CANADA
                                    );

                            endTimeText.setText(
                                    "Parking expires at: "
                                            + formatter.format(endsAt.toDate())
                            );

                            startCountdown(endsAt.toDate().getTime());

                        });
    }

    private void startCountdown(long endTime) {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        long remaining =
                endTime - System.currentTimeMillis();

        if (remaining <= 0) {
            sessionExpired();
            return;
        }

        titleText.setText("Parking Active!");

        sessionActive = true;

        endButton.setEnabled(
                !endingPending && !extensionPending
        );

        extendButton.setEnabled(
                !endingPending
                        && !extensionPending
                        && currentDurationHours > 0
                        && currentDurationHours < 24
        );

        countDownTimer = new CountDownTimer(
                remaining, 1000
        ) {

            @Override
            public void onTick(long millisUntilFinished) {

                long totalSeconds =
                        (millisUntilFinished + 999) / 1000;

                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long seconds = totalSeconds % 60;

                timerText.setText(String.format(
                        Locale.getDefault(),
                        "%02d:%02d:%02d",
                        hours,
                        minutes,
                        seconds
                ));
            }

            @Override
            public void onFinish() {
                sessionExpired();
            }
        }.start();
    }

    private void sessionExpired() {
        sessionActive = false;
        endButton.setEnabled(false);

        titleText.setText("Parking Session Ended");
        timerText.setText("00:00:00");

        extendButton.setEnabled(false);

        Toast.makeText(this,
                "Your parking session has expired",
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStop() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        LotRepository.stopListening(bookingRegistration);
        bookingRegistration = null;

        super.onStop();
    }
    private void confirmEndSession() {

        if (!sessionActive
                || endingPending
                || extensionPending) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("End Parking Session?")
                .setMessage(
                        "Your parking spot will become available "
                                + "immediately.\n\n"
                                + "Unused parking time will not be refunded."
                )
                .setNegativeButton("Keep Parking", null)
                .setPositiveButton(
                        "End Session",
                        (dialog, which) -> endParkingSession()
                )
                .show();
    }

    private void endParkingSession() {

        if (!sessionActive
                || endingPending
                || extensionPending) return;
        FirebaseUser user =
                new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            return;
        }

        endingPending = true;

        endButton.setEnabled(false);
        extendButton.setEnabled(false);

        lotRepository.endParkingSession(
                bookingId,
                user.getUid()

        ).addOnSuccessListener(unused -> {

            endingPending = false;

            if (isFinishing() || isDestroyed()) return;

            sessionEnded();

            Toast.makeText(
                    this,
                    "Parking session ended successfully!",
                    Toast.LENGTH_SHORT
            ).show();
            Intent intent = new Intent(
                    ParkingSessionActivity.this,
                    ActiveBookingsActivity.class
            );

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);
            finish();

        }).addOnFailureListener(error -> {

            endingPending = false;

            if (isFinishing() || isDestroyed()) return;

            endButton.setEnabled(sessionActive);
            extendButton.setEnabled(
                    sessionActive
                            && !extensionPending
                            && currentDurationHours > 0
                            && currentDurationHours < 24
            );

            Toast.makeText(
                    this,
                    "Unable to end parking: "
                            + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        });


    }
    private void sessionEnded() {

        sessionActive = false;

        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        titleText.setText("Parking Session Ended");

        timerText.setText("00:00:00");

        endTimeText.setText(
                "Your parking spot has been released."
        );

        extendButton.setEnabled(false);
        endButton.setEnabled(false);
    }


    private void showExtensionDialog() {

        if (!sessionActive
                || extensionPending
                || endingPending) return;

        int maximumExtension =
                Math.min(4, 24 - currentDurationHours);

        if (maximumExtension <= 0) {

            Toast.makeText(
                    this,
                    "Maximum parking duration reached",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String[] options = new String[maximumExtension];

        for (int i = 0; i < maximumExtension; i++) {

            int hours = i + 1;

            options[i] = String.format(
                    Locale.CANADA,
                    "%d hour(s) - $%.2f",
                    hours,
                    hours * 2.0
            );
        }

        final int[] selectedHours = {1};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Extend Parking")

                .setSingleChoiceItems(
                        options,
                        0,
                        (dialog, which) ->
                                selectedHours[0] = which + 1
                )

                .setNegativeButton("Cancel", null)

                .setPositiveButton(
                        "Continue to Payment",
                        (dialog, which) ->
                                confirmExtensionPayment(
                                        selectedHours[0]
                                )
                )

                .show();
    }

    private void confirmExtensionPayment(
            int additionalHours) {

        if (!sessionActive
                || extensionPending
                || endingPending) return;

        String message = String.format(
                Locale.CANADA,
                "Additional parking time: %d hour(s)"
                        + "\n\nAdditional cost: $%.2f"
                        + "\n\nThis is a simulated payment."
                        + " No real money will be charged.",
                additionalHours,
                additionalHours * 2.0
        );

        new MaterialAlertDialogBuilder(this)

                .setTitle("Confirm Payment")

                .setMessage(message)

                .setNegativeButton("Cancel", null)

                .setPositiveButton(
                        "Pay Now",
                        (dialog, which) ->
                                extendParkingSession(
                                        additionalHours
                                )
                )

                .show();
    }

    private void extendParkingSession(
            int additionalHours) {

        if (!sessionActive
                || extensionPending
                || endingPending) return;

        FirebaseUser user =
                new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            return;
        }

        extensionPending = true;

        extendButton.setEnabled(false);
        endButton.setEnabled(false);

        lotRepository.extendParkingSession(
                bookingId,
                user.getUid(),
                additionalHours

        ).addOnSuccessListener(unused -> {

            extensionPending = false;

            if (isFinishing() || isDestroyed()) return;

            endButton.setEnabled(
                    sessionActive && !endingPending
            );

            extendButton.setEnabled(
                    sessionActive
                            && !endingPending
                            && currentDurationHours > 0
                            && currentDurationHours < 24
            );

            Toast.makeText(
                    this,
                    "Parking successfully extended!",
                    Toast.LENGTH_SHORT
            ).show();

        }).addOnFailureListener(error -> {

            extensionPending = false;

            if (isFinishing() || isDestroyed()) return;

            endButton.setEnabled(
                    sessionActive && !endingPending
            );

            extendButton.setEnabled(
                    sessionActive
                            && !endingPending
                            && currentDurationHours > 0
                            && currentDurationHours < 24
            );

            Toast.makeText(
                    this,
                    "Unable to extend parking: "
                            + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        });
    }


}
