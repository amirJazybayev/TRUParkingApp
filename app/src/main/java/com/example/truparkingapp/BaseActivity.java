package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Parent of every screen used after logging in (or continuing as a guest).
 * Checks the timed session whenever the screen comes back and records activity
 * on every touch, so the 30-minute idle limit counts from the last use.
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (session.isExpired(now)) {
            boolean wasActive = session.isActive();
            new UserRepository().signOut();
            session.end();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, wasActive);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            session.touch(now);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        session.touch(System.currentTimeMillis());
    }
}
