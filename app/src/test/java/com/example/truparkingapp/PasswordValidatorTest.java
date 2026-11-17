package com.example.truparkingapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordValidatorTest {

    @Test
    public void acceptsPasswordMeetingAllRules() {
        assertTrue(PasswordValidator.isValid("Parking#2026"));
    }

    @Test
    public void rejectsShortPassword() {
        assertFalse(PasswordValidator.hasValidLength("Ab1!xyz"));
        assertTrue(PasswordValidator.hasValidLength("Ab1!xyzw"));
    }

    @Test
    public void rejectsPasswordOver64Characters() {
        StringBuilder longPassword = new StringBuilder("A1!");
        while (longPassword.length() <= PasswordValidator.MAX_LENGTH) longPassword.append('a');
        assertFalse(PasswordValidator.isValid(longPassword.toString()));
    }

    @Test
    public void reportsMissingCapitalAndSymbol() {
        String password = "password1";
        assertTrue(PasswordValidator.hasValidLength(password));
        assertTrue(PasswordValidator.hasDigit(password));
        assertFalse(PasswordValidator.hasUppercase(password));
        assertFalse(PasswordValidator.hasSymbol(password));
        assertFalse(PasswordValidator.isValid(password));
    }

    @Test
    public void spaceIsNotASymbol() {
        assertFalse(PasswordValidator.hasSymbol("Parking 2026"));
    }

    @Test
    public void handlesNull() {
        assertFalse(PasswordValidator.isValid(null));
    }
}
