package com.example.truparkingapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsernameValidatorTest {

    @Test
    public void acceptsLettersNumbersAndUnderscore() {
        assertTrue(UsernameValidator.isValid("tru_driver_7"));
    }

    @Test
    public void enforcesLengthLimits() {
        assertFalse(UsernameValidator.isValid("ab"));
        assertTrue(UsernameValidator.isValid("abc"));
        assertTrue(UsernameValidator.isValid("a2345678901234567890"));
        assertFalse(UsernameValidator.isValid("a23456789012345678901"));
    }

    @Test
    public void rejectsOtherCharacters() {
        assertFalse(UsernameValidator.isValid("amir.j"));
        assertFalse(UsernameValidator.isValid("amir j"));
        assertFalse(UsernameValidator.isValid("amír"));
    }

    @Test
    public void keyIgnoresCase() {
        assertEquals(UsernameValidator.toKey("AMIR"), UsernameValidator.toKey("amir"));
    }
}
