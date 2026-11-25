
package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class PaymentActivity extends BaseActivity {

    private String lotId;
    private String spotId;
    private String spotNumber;

    private long reservedUntil;

    private int selectedHours;
    private int totalPriceCents;

    private boolean paymentPending = false;

    private TextView timerText;
    private Button payButton;
    private Button backButton;
    private RadioGroup paymentMethods;
    private ProgressBar progress;

    private CountDownTimer countDownTimer;

    private final LotRepository lotRepository =
            new LotRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        WindowInsetsHelper.padForSystemBarsAndKeyboard(
                findViewById(R.id.payment_root)
        );

        lotId = getIntent().getStringExtra("lotId");
        spotId = getIntent().getStringExtra("spotId");
        spotNumber = getIntent().getStringExtra("spotNumber");

        reservedUntil = getIntent().getLongExtra(
                "reservedUntil", 0
        );

        selectedHours = getIntent().getIntExtra(
                "selectedHours", 0
        );

        if (lotId == null || spotId == null
                || reservedUntil <= 0
                || !isValidDuration(selectedHours)) {
            finish();
            return;
        }

        totalPriceCents = selectedHours * 200;

        TextView detailsText =
                findViewById(R.id.text_payment_details);

        TextView amountText =
                findViewById(R.id.text_payment_amount);

        timerText = findViewById(R.id.text_payment_timer);

        payButton = findViewById(R.id.button_pay_now);
        backButton = findViewById(R.id.button_back_booking);

        paymentMethods =
                findViewById(R.id.radio_payment_method);

        progress = findViewById(R.id.progress_payment);

        detailsText.setText(
                "Parking Lot: " + lotId
                        + "\nParking Spot: " + spotNumber
                        + "\nDuration: " + selectedHours + " hour(s)"
        );

        String amount = String.format(
                Locale.CANADA,
                "$%.2f",
                totalPriceCents / 100.0
        );

        amountText.setText(amount);
        payButton.setText("Pay " + amount);

        payButton.setOnClickListener(v -> makePayment());

        backButton.setOnClickListener(v -> {
            if (!paymentPending) {
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!isFinishing()) {
            startCountdown();
        }
    }

    private boolean isValidDuration(int hours) {
        return hours == 1 || hours == 2
                || hours == 3 || hours == 4
                || hours == 8;
    }

    private void startCountdown() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        long remaining =
                reservedUntil - System.currentTimeMillis();

        if (remaining <= 0) {
            reservationExpired();
            return;
        }

        countDownTimer = new CountDownTimer(
                remaining, 1000
        ) {

            @Override
            public void onTick(long millisUntilFinished) {

                long seconds =
                        (millisUntilFinished + 999) / 1000;

                timerText.setText(String.format(
                        Locale.getDefault(),
                        "Reservation expires in: %02d:%02d",
                        seconds / 60,
                        seconds % 60
                ));
            }

            @Override
            public void onFinish() {
                reservationExpired();
            }
        }.start();
    }

    private void reservationExpired() {

        timerText.setText("Reservation expired");
        payButton.setEnabled(false);

        // Wait for the Firestore result if payment
        // is already being processed.
        if (paymentPending) return;

        Toast.makeText(this,
                "Your reservation has expired",
                Toast.LENGTH_LONG).show();

        finish();
    }

    private void makePayment() {

        if (paymentPending) return;

        if (reservedUntil <= System.currentTimeMillis()) {
            reservationExpired();
            return;
        }

        FirebaseUser user =
                new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            finish();
            return;
        }

        paymentPending = true;

        payButton.setEnabled(false);
        backButton.setEnabled(false);

        progress.setVisibility(View.VISIBLE);

        String paymentMethod =
                paymentMethods.getCheckedRadioButtonId()
                        == R.id.radio_demo_wallet
                        ? "DEMO_WALLET"
                        : "DEMO_CARD";

        lotRepository.confirmBooking(
                lotId,
                spotId,
                spotNumber,
                user.getUid(),
                reservedUntil,
                selectedHours,
                paymentMethod

        ).addOnSuccessListener(booking -> {

            paymentPending = false;

            if (countDownTimer != null) {
                countDownTimer.cancel();
            }

            if (isFinishing() || isDestroyed()) return;

            Toast.makeText(this,
                    "Payment successful!",
                    Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(
                    PaymentActivity.this,
                    ParkingSessionActivity.class
            );

            intent.putExtra("bookingId", booking.bookingId);

            startActivity(intent);

            setResult(RESULT_OK);
            finish();

        }).addOnFailureListener(error -> {

            paymentPending = false;

            if (isFinishing() || isDestroyed()) return;

            progress.setVisibility(View.GONE);
            backButton.setEnabled(true);

            if (reservedUntil <= System.currentTimeMillis()) {
                reservationExpired();
                return;
            }

            payButton.setEnabled(true);

            Toast.makeText(this,
                    "Booking failed: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (!paymentPending) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        super.onStop();
    }
}
