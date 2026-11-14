package com.example.truparkingapp;

import com.google.firebase.FirebaseNetworkException;

/** Turns Firebase failures into the short messages from the requirements. */
final class AuthErrors {

    private AuthErrors() {
    }

    static boolean isNetworkError(Exception e) {
        return e instanceof FirebaseNetworkException;
    }
}
