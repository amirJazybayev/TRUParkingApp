package com.example.truparkingapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The app's own timed session. Firebase keeps a user signed in forever, so the
 * limits from the requirements are enforced here:
 * logged out after 30 minutes without using the app (unless "Remember me"),
 * and always after 7 days.
 */
public class SessionManager {

    public static final long IDLE_LIMIT_MS = 30L * 60 * 1000;
    public static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    private static final String PREFS = "truparking_session";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_LOGIN_AT = "login_at";
    private static final String KEY_LAST_ACTIVE_AT = "last_active_at";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_GUEST = "guest";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void start(boolean rememberMe, boolean guest, long now) {
        prefs.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putLong(KEY_LOGIN_AT, now)
                .putLong(KEY_LAST_ACTIVE_AT, now)
                .putBoolean(KEY_REMEMBER_ME, rememberMe)
                .putBoolean(KEY_GUEST, guest)
                .apply();
    }

    /** Call whenever the user interacts with the app. */
    public void touch(long now) {
        if (isActive()) {
            prefs.edit().putLong(KEY_LAST_ACTIVE_AT, now).apply();
        }
    }

    public boolean isActive() {
        return prefs.getBoolean(KEY_ACTIVE, false);
    }

    public boolean isGuest() {
        return prefs.getBoolean(KEY_GUEST, false);
    }

    public boolean isExpired(long now) {
        if (!isActive()) return true;
        return isExpired(
                prefs.getLong(KEY_LOGIN_AT, 0),
                prefs.getLong(KEY_LAST_ACTIVE_AT, 0),
                prefs.getBoolean(KEY_REMEMBER_ME, false),
                now);
    }

    public void end() {
        prefs.edit().clear().apply();
    }

    /** The expiry rule on its own, so unit tests can check it without waiting. */
    static boolean isExpired(long loginAt, long lastActiveAt, boolean rememberMe, long now) {
        if (now - loginAt > MAX_AGE_MS) return true;
        return !rememberMe && now - lastActiveAt > IDLE_LIMIT_MS;
    }
}
