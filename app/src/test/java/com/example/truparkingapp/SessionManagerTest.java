package com.example.truparkingapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionManagerTest {

    private static final long MINUTE = 60 * 1000L;
    private static final long DAY = 24 * 60 * MINUTE;
    private static final long LOGIN = 1_000_000L;

    @Test
    public void activeUserStaysLoggedIn() {
        assertFalse(SessionManager.isExpired(LOGIN, LOGIN + 10 * MINUTE, false, LOGIN + 20 * MINUTE));
    }

    @Test
    public void expiresAfter30IdleMinutes() {
        assertTrue(SessionManager.isExpired(LOGIN, LOGIN, false, LOGIN + 31 * MINUTE));
    }

    @Test
    public void rememberMeSkipsIdleLimit() {
        assertFalse(SessionManager.isExpired(LOGIN, LOGIN, true, LOGIN + 31 * MINUTE));
    }

    @Test
    public void alwaysExpiresAfter7Days() {
        long now = LOGIN + 7 * DAY + MINUTE;
        assertTrue(SessionManager.isExpired(LOGIN, now - MINUTE, true, now));
    }
}
