package com.example.truparkingapp;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps content clear of the status bar, navigation bar and keyboard in edge-to-edge mode. */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    public static void padForSystemBarsAndKeyboard(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
}
