package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

/**
 * Entry screen. Sends a user with a live session straight to the lot map,
 * otherwise offers Log in, Sign up and Continue without registration.
 */
public class LoginActivity extends AppCompatActivity {

    /** Set by any screen that finds the session expired. */
    public static final String EXTRA_SESSION_EXPIRED = "com.example.truparkingapp.SESSION_EXPIRED";

    private SessionManager session;
    private UserRepository users;

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialCheckBox rememberMe;
    private TextView errorBanner;
    private MaterialButton logInButton;
    private MaterialButton guestButton;
    private MaterialButton forgotButton;
    private MaterialButton signUpButton;
    private CircularProgressIndicator progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        users = new UserRepository();

        boolean expired = getIntent().getBooleanExtra(EXTRA_SESSION_EXPIRED, false);
        FirebaseUser current = users.getCurrentUser();
        long now = System.currentTimeMillis();

        if (current != null && session.isActive() && !session.isExpired(now)) {
            session.touch(now);
            openMap();
            return;
        }
        if (current != null || session.isActive()) {
            // A login is left over but its session ran out (or was never started).
            expired = expired || session.isActive();
            users.signOut();
            session.end();
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        WindowInsetsHelper.padForSystemBarsAndKeyboard(findViewById(R.id.login_root));
        bindViews();

        findViewById(R.id.text_session_expired).setVisibility(expired ? View.VISIBLE : View.GONE);

        logInButton.setOnClickListener(v -> attemptLogIn());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogIn();
                return true;
            }
            return false;
        });
        forgotButton.setOnClickListener(v -> showForgotPasswordDialog());
        guestButton.setOnClickListener(v -> continueAsGuest());
        signUpButton.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }

    private void bindViews() {
        emailLayout = findViewById(R.id.layout_login_email);
        passwordLayout = findViewById(R.id.layout_login_password);
        emailInput = findViewById(R.id.input_login_email);
        passwordInput = findViewById(R.id.input_login_password);
        rememberMe = findViewById(R.id.check_remember_me);
        errorBanner = findViewById(R.id.text_login_error);
        logInButton = findViewById(R.id.button_log_in);
        guestButton = findViewById(R.id.button_continue_guest);
        forgotButton = findViewById(R.id.button_forgot_password);
        signUpButton = findViewById(R.id.button_go_to_sign_up);
        progress = findViewById(R.id.progress_log_in);
    }

    private void attemptLogIn() {
        hideError();
        String email = text(emailInput);
        String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString();

        boolean ok = true;
        if (email.isEmpty()) {
            emailLayout.setError(getString(R.string.error_required));
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_invalid_email));
            ok = false;
        } else {
            emailLayout.setError(null);
        }
        if (password.isEmpty()) {
            passwordLayout.setError(getString(R.string.error_required));
            ok = false;
        } else {
            passwordLayout.setError(null);
        }
        if (!ok) return;

        setLoading(true);
        users.logIn(email, password, new UserRepository.Callback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser user) {
                session.start(rememberMe.isChecked(), false, System.currentTimeMillis());
                openMap();
            }

            @Override
            public void onError(Exception e) {
                setLoading(false);
                // One message for wrong email and wrong password, so we never reveal
                // which emails have accounts.
                showError(AuthErrors.isNetworkError(e)
                        ? R.string.error_no_internet
                        : R.string.error_login_failed);
            }
        });
    }

    private void continueAsGuest() {
        hideError();
        setLoading(true);
        users.continueAsGuest(new UserRepository.Callback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser user) {
                session.start(false, true, System.currentTimeMillis());
                openMap();
            }

            @Override
            public void onError(Exception e) {
                setLoading(false);
                showError(AuthErrors.isNetworkError(e)
                        ? R.string.error_no_internet
                        : R.string.error_generic);
            }
        });
    }

    private void showForgotPasswordDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        TextInputLayout layout = content.findViewById(R.id.layout_reset_email);
        TextInputEditText input = content.findViewById(R.id.input_reset_email);
        input.setText(text(emailInput));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.forgot_title)
                .setMessage(R.string.forgot_message)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_send_link, null)
                .create();

        // Set the click listener after show() so an invalid email keeps the dialog open.
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String email = text(input);
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        layout.setError(getString(R.string.error_invalid_email));
                        return;
                    }
                    dialog.dismiss();
                    users.sendPasswordReset(email, new UserRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            toast(R.string.reset_sent);
                        }

                        @Override
                        public void onError(Exception e) {
                            // Same answer for unknown emails, so the dialog can't be used
                            // to find out who has an account.
                            toast(AuthErrors.isNetworkError(e)
                                    ? R.string.error_no_internet
                                    : R.string.reset_sent);
                        }
                    });
                }));
        dialog.show();
    }

    private void openMap() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        logInButton.setText(loading ? "" : getString(R.string.action_log_in));
        logInButton.setEnabled(!loading);
        guestButton.setEnabled(!loading);
        forgotButton.setEnabled(!loading);
        signUpButton.setEnabled(!loading);
    }

    private void showError(int messageRes) {
        errorBanner.setText(messageRes);
        errorBanner.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorBanner.setVisibility(View.GONE);
    }

    private void toast(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
