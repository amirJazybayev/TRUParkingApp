
package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;

public class BookingActivity extends BaseActivity {

    private String lotId;
    private String spotId;
    private String spotNumber;
    private long reservedUntil;

    private static final int HOURLY_RATE_CENTS = 200;

    private int selectedHours = 1;
    private int totalPriceCents = HOURLY_RATE_CENTS;

    private TextView timerText;
    private TextView totalText;
    private RadioGroup durationGroup;
    private Button paymentButton;

    private boolean reservationVerified = false;

    private CountDownTimer countDownTimer;
    private ListenerRegistration reservationRegistration;

    private final LotRepository lotRepository = new LotRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        WindowInsetsHelper.padForSystemBarsAndKeyboard(
                findViewById(R.id.booking_root)
        );

        lotId = getIntent().getStringExtra("lotId");
        spotId = getIntent().getStringExtra("spotId");
        spotNumber = getIntent().getStringExtra("spotNumber");
        reservedUntil = getIntent().getLongExtra("reservedUntil", 0);

        if (lotId == null || spotId == null || reservedUntil <= 0) {
            finish();
            return;
        }

        TextView lotText = findViewById(R.id.text_booking_lot);
        TextView spotText = findViewById(R.id.text_booking_spot);

        timerText = findViewById(R.id.text_booking_timer);
        totalText = findViewById(R.id.text_booking_total);
        durationGroup = findViewById(R.id.radio_parking_duration);
        paymentButton = findViewById(R.id.button_proceed_payment);

        Button backButton =
                findViewById(R.id.button_back_reservation);

        lotText.setText("Parking Lot: " + lotId);
        spotText.setText("Parking Spot: " + spotNumber);

        paymentButton.setEnabled(false);

        durationGroup.setOnCheckedChangeListener(
                (group, checkedId) -> updateDuration(checkedId)
        );

        updateDuration(durationGroup.getCheckedRadioButtonId());

        paymentButton.setOnClickListener(v -> proceedToPayment());

        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (isFinishing()) return;

        reservationVerified = false;
        paymentButton.setEnabled(false);

        FirebaseUser user = new UserRepository().getCurrentUser();

        if (user == null || user.isAnonymous()) {
            finish();
            return;
        }

        startCountdown();

        if (isFinishing()) return;

        reservationRegistration = lotRepository.listenToReservation(
                lotId,
                spotId,
                (snapshot, error) -> {

                    if (isFinishing()) return;

                    reservationVerified = false;
                    paymentButton.setEnabled(false);

                    if (error != null || snapshot == null) {
                        Toast.makeText(this,
                                "Unable to verify reservation",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshot.getMetadata().isFromCache()
                            || snapshot.getMetadata().hasPendingWrites()) {
                        return;
                    }

                    Timestamp expiry =
                            snapshot.getTimestamp("reservedUntil");

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

                    reservationVerified = true;
                    paymentButton.setEnabled(true);
                }
        );
    }

    private void updateDuration(int checkedId) {

        if (checkedId == R.id.radio_one_hour) {
            selectedHours = 1;

        } else if (checkedId == R.id.radio_two_hours) {
            selectedHours = 2;

        } else if (checkedId == R.id.radio_three_hours) {
            selectedHours = 3;

        } else if (checkedId == R.id.radio_four_hours) {
            selectedHours = 4;

        } else if (checkedId == R.id.radio_eight_hours) {
            selectedHours = 8;
        }

        calculatePrice();
    }

    private void calculatePrice() {

        totalPriceCents = selectedHours * HOURLY_RATE_CENTS;

        totalText.setText(String.format(
                Locale.CANADA,
                "$%.2f",
                totalPriceCents / 100.0
        ));
    }

    private void startCountdown() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        long remainingTime =
                reservedUntil - System.currentTimeMillis();

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
                        "Reservation expires in: %02d:%02d",
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

        reservationVerified = false;
        paymentButton.setEnabled(false);

        timerText.setText("Reservation expired");

        Toast.makeText(this,
                "Your reservation has expired!",
                Toast.LENGTH_LONG).show();

        finish();
    }

    private void proceedToPayment() {

        if (reservedUntil <= System.currentTimeMillis()) {
            reservationExpired();
            return;
        }

        if (!reservationVerified) {
            Toast.makeText(this,
                    "Please wait for reservation verification",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(
                BookingActivity.this,
                PaymentActivity.class
        );

        intent.putExtra("lotId", lotId);
        intent.putExtra("spotId", spotId);
        intent.putExtra("spotNumber", spotNumber);
        intent.putExtra("reservedUntil", reservedUntil);
        intent.putExtra("selectedHours", selectedHours);

        startActivityForResult(intent, 200);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 200 && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            finish();
        }
    }
    @Override
    protected void onStop() {

        reservationVerified = false;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        LotRepository.stopListening(reservationRegistration);
        reservationRegistration = null;

        super.onStop();
    }
}
