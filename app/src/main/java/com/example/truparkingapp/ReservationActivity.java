
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class ReservationActivity extends BaseActivity {

    private TextView timerText;
    private Button cancelButton;
    private Button continueButton;

    private String lotId;
    private String spotId;
    private String spotNumber;
    private long reservedUntil;

    private boolean cancellationPending = false;

    private CountDownTimer countDownTimer;
    private ListenerRegistration reservationRegistration;

    private final LotRepository lotRepository = new LotRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservation);

        lotId = getIntent().getStringExtra("lotId");
        spotId = getIntent().getStringExtra("spotId");
        spotNumber = getIntent().getStringExtra("spotNumber");
        reservedUntil = getIntent().getLongExtra("reservedUntil", 0);

        if (lotId == null || spotId == null || reservedUntil <= 0) {
            finish();
            return;
        }

        TextView spotText = findViewById(R.id.text_reserved_spot);
        timerText = findViewById(R.id.text_reservation_timer);
        cancelButton = findViewById(R.id.button_cancel_reservation);
        continueButton = findViewById(R.id.button_continue_booking);

        spotText.setText("Spot " + spotNumber);

        cancelButton.setEnabled(false);
        continueButton.setEnabled(false);

        cancelButton.setOnClickListener(v -> confirmCancellation());

        continueButton.setOnClickListener(v -> {

            if (reservedUntil <= System.currentTimeMillis()) {
                reservationExpired();
                return;
            }

            Intent intent = new Intent(
                    ReservationActivity.this,
                    BookingActivity.class
            );

            intent.putExtra("lotId", lotId);
            intent.putExtra("spotId", spotId);
            intent.putExtra("spotNumber", spotNumber);
            intent.putExtra("reservedUntil", reservedUntil);

            startActivityForResult(intent, 201);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (isFinishing()) return;

        FirebaseUser user = new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            finish();
            return;
        }

        startCountdown();

        reservationRegistration = lotRepository.listenToReservation(
                lotId,
                spotId,
                (snapshot, error) -> {

                    if (isFinishing() || cancellationPending) return;

                    if (error != null || snapshot == null) {
                        continueButton.setEnabled(false);
                        cancelButton.setEnabled(false);

                        Toast.makeText(this,
                                "Unable to verify reservation",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Timestamp expiry = snapshot.getTimestamp("reservedUntil");

                    boolean valid = snapshot.exists()
                            && Spot.STATUS_RESERVED.equals(
                            snapshot.getString("status"))
                            && user.getUid().equals(
                            snapshot.getString("occupiedBy"))
                            && expiry != null
                            && expiry.toDate().getTime() == reservedUntil
                            && reservedUntil > System.currentTimeMillis();

                    if (!valid) {
                        Toast.makeText(this,
                                "Reservation is no longer active",
                                Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    boolean verified = !snapshot.getMetadata().isFromCache()
                            && !snapshot.getMetadata().hasPendingWrites();

                    cancelButton.setEnabled(verified);
                    continueButton.setEnabled(verified);
                }
        );
    }

    private void startCountdown() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        long remainingTime = reservedUntil - System.currentTimeMillis();

        if (remainingTime <= 0) {
            reservationExpired();
            return;
        }

        countDownTimer = new CountDownTimer(remainingTime, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {

                long totalSeconds = (millisUntilFinished + 999) / 1000;
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;

                timerText.setText(String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        minutes,
                        seconds
                ));
            }

            @Override
            public void onFinish() {
                reservationExpired();
            }
        }.start();
    }

    private void reservationExpired() {

        timerText.setText("00:00");
        cancelButton.setEnabled(false);
        continueButton.setEnabled(false);

        Toast.makeText(this,
                "Your reservation has expired!",
                Toast.LENGTH_LONG).show();

        finish();
    }

    private void confirmCancellation() {

        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Reservation?")
                .setMessage("Your parking spot will become available to other users.")
                .setNegativeButton("Keep Reservation", null)
                .setPositiveButton("Cancel Reservation",
                        (dialog, which) -> cancelReservation())
                .show();
    }

    private void cancelReservation() {

        FirebaseUser user = new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()
                || cancellationPending) return;

        cancellationPending = true;

        cancelButton.setEnabled(false);
        continueButton.setEnabled(false);

        lotRepository.cancelReservation(
                lotId,
                spotId,
                user.getUid(),
                reservedUntil
        ).addOnSuccessListener(unused -> {

            Toast.makeText(this,
                    "Reservation cancelled successfully",
                    Toast.LENGTH_SHORT).show();

            finish();

        }).addOnFailureListener(error -> {

            cancellationPending = false;

            if (isFinishing()) return;

            Toast.makeText(this,
                    "Unable to cancel reservation: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();

            // Reverify the reservation before enabling buttons.
            LotRepository.stopListening(reservationRegistration);
            reservationRegistration = null;

            recreate();
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 201 && resultCode == RESULT_OK) {
            finish();
        }
    }

    @Override
    protected void onStop() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        LotRepository.stopListening(reservationRegistration);
        reservationRegistration = null;

        super.onStop();
    }
}
