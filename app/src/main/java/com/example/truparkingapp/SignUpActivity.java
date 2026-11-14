package com.example.truparkingapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Sign-up: any email address, a unique username, user type, and a password
 * that passes the live checklist. The button stays disabled until all fields are valid.
 */
public class SignUpActivity extends AppCompatActivity {

    private static final long USERNAME_CHECK_DELAY_MS = 500;

    private SessionManager session;
    private UserRepository users;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingUsernameCheck;

    /** null = not checked yet, true = free, false = taken. */
    private Boolean usernameAvailable;
    private boolean loading;

    private TextInputLayout emailLayout;
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
    private TextInputEditText emailInput;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmInput;
    private MaterialButtonToggleGroup userTypeGroup;
    private TextView ruleLength;
    private TextView ruleUppercase;
    private TextView ruleNumber;
    private TextView ruleSymbol;
    private TextView errorBanner;
    private MaterialButton createButton;
    private MaterialButton logInLink;
    private CircularProgressIndicator progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        WindowInsetsHelper.padForSystemBarsAndKeyboard(findViewById(R.id.sign_up_root));

        session = new SessionManager(this);
        users = new UserRepository();
        bindViews();

        onTextChanged(emailInput, () -> {
            emailLayout.setError(null);
            updateCreateButton();
        });
        onTextChanged(usernameInput, this::onUsernameChanged);
        onTextChanged(passwordInput, () -> {
            updatePasswordChecklist();
            updateConfirmError();
            updateCreateButton();
        });
        onTextChanged(confirmInput, () -> {
            updateConfirmError();
            updateCreateButton();
        });
        userTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> updateCreateButton());

        emailInput.setOnFocusChangeListener((v, hasFocus) -> {
            String email = text(emailInput);
            if (!hasFocus && !email.isEmpty() && !isEmailValid(email)) {
                emailLayout.setError(getString(R.string.error_invalid_email));
            }
        });

        createButton.setOnClickListener(v -> attemptSignUp());
        logInLink.setOnClickListener(v -> finish());

        updatePasswordChecklist();
        updateCreateButton();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void bindViews() {
        emailLayout = findViewById(R.id.layout_sign_up_email);
        usernameLayout = findViewById(R.id.layout_username);
        passwordLayout = findViewById(R.id.layout_sign_up_password);
        confirmLayout = findViewById(R.id.layout_confirm_password);
        emailInput = findViewById(R.id.input_sign_up_email);
        usernameInput = findViewById(R.id.input_username);
        passwordInput = findViewById(R.id.input_sign_up_password);
        confirmInput = findViewById(R.id.input_confirm_password);
        userTypeGroup = findViewById(R.id.toggle_user_type);
        ruleLength = findViewById(R.id.rule_length);
        ruleUppercase = findViewById(R.id.rule_uppercase);
        ruleNumber = findViewById(R.id.rule_number);
        ruleSymbol = findViewById(R.id.rule_symbol);
        errorBanner = findViewById(R.id.text_sign_up_error);
        createButton = findViewById(R.id.button_create_account);
        logInLink = findViewById(R.id.button_go_to_log_in);
        progress = findViewById(R.id.progress_sign_up);
    }

    // ---- Username: format check, then an availability check once typing pauses ----

    private void onUsernameChanged() {
        usernameAvailable = null;
        usernameLayout.setError(null);
        usernameLayout.setHelperText(getString(R.string.helper_username));
        if (pendingUsernameCheck != null) handler.removeCallbacks(pendingUsernameCheck);

        String name = text(usernameInput);
        if (UsernameValidator.isValid(name)) {
            pendingUsernameCheck = () -> checkUsername(name);
            handler.postDelayed(pendingUsernameCheck, USERNAME_CHECK_DELAY_MS);
        }
        updateCreateButton();
    }

    private void checkUsername(String name) {
        usernameLayout.setHelperText(getString(R.string.username_checking));
        users.isUsernameAvailable(name, new UserRepository.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean available) {
                if (!name.equals(text(usernameInput))) return; // user kept typing
                usernameAvailable = available;
                if (available) {
                    usernameLayout.setHelperText(getString(R.string.username_available));
                } else {
                    usernameLayout.setError(getString(R.string.error_username_taken));
                }
                updateCreateButton();
            }

            @Override
            public void onError(Exception e) {
                // Leave it unchecked; the final check happens again on submit.
                if (name.equals(text(usernameInput))) {
                    usernameLayout.setHelperText(getString(R.string.helper_username));
                }
            }
        });
    }

    // ---- Password checklist ----

    private void updatePasswordChecklist() {
        String password = rawText(passwordInput);
        markRule(ruleLength, PasswordValidator.hasValidLength(password));
        markRule(ruleUppercase, PasswordValidator.hasUppercase(password));
        markRule(ruleNumber, PasswordValidator.hasDigit(password));
        markRule(ruleSymbol, PasswordValidator.hasSymbol(password));
    }

    private void markRule(TextView rule, boolean met) {
        rule.setCompoundDrawablesRelativeWithIntrinsicBounds(
                met ? R.drawable.ic_rule_met : R.drawable.ic_rule_unmet, 0, 0, 0);
        rule.setTextColor(ContextCompat.getColor(this,
                met ? R.color.pme_navy : R.color.pme_text_secondary));
        rule.setContentDescription(getString(
                met ? R.string.rule_met_description : R.string.rule_unmet_description,
                rule.getText()));
    }

    private void updateConfirmError() {
        String confirm = rawText(confirmInput);
        boolean mismatch = !confirm.isEmpty() && !confirm.equals(rawText(passwordInput));
        confirmLayout.setError(mismatch ? getString(R.string.error_passwords_differ) : null);
    }

    // ---- Submit ----

    private void updateCreateButton() {
        String password = rawText(passwordInput);
        boolean ready = isEmailValid(text(emailInput))
                && UsernameValidator.isValid(text(usernameInput))
                && !Boolean.FALSE.equals(usernameAvailable)
                && userTypeGroup.getCheckedButtonId() != View.NO_ID
                && PasswordValidator.isValid(password)
                && password.equals(rawText(confirmInput));
        createButton.setEnabled(ready && !loading);
    }

    private void attemptSignUp() {
        errorBanner.setVisibility(View.GONE);
        String email = text(emailInput);
        String username = text(usernameInput);
        String password = rawText(passwordInput);

        setLoading(true);
        users.signUp(email, password, username, selectedUserType(),
                new UserRepository.Callback<FirebaseUser>() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        session.start(false, false, System.currentTimeMillis());
                        Toast.makeText(SignUpActivity.this, R.string.verify_sent,
                                Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(Exception e) {
                        setLoading(false);
                        showSignUpError(e);
                    }
                });
    }

    private void showSignUpError(Exception e) {
        if (e instanceof UserRepository.UsernameTakenException) {
            usernameAvailable = false;
            usernameLayout.setError(getString(R.string.error_username_taken));
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            emailLayout.setError(getString(R.string.error_email_in_use));
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            emailLayout.setError(getString(R.string.error_invalid_email));
        } else {
            errorBanner.setText(AuthErrors.isNetworkError(e)
                    ? R.string.error_no_internet
                    : R.string.error_generic);
            errorBanner.setVisibility(View.VISIBLE);
        }
        updateCreateButton();
    }

    private String selectedUserType() {
        int id = userTypeGroup.getCheckedButtonId();
        if (id == R.id.button_type_staff) return User.TYPE_STAFF;
        if (id == R.id.button_type_visitor) return User.TYPE_VISITOR;
        return User.TYPE_STUDENT;
    }

    private void setLoading(boolean isLoading) {
        loading = isLoading;
        progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        createButton.setText(isLoading ? "" : getString(R.string.action_create_account));
        logInLink.setEnabled(!isLoading);
        updateCreateButton();
    }

    // ---- Helpers ----

    private static boolean isEmailValid(String email) {
        return !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private static String text(TextInputEditText input) {
        return rawText(input).trim();
    }

    private static String rawText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private static void onTextChanged(TextInputEditText input, Runnable action) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                action.run();
            }
        });
    }
}
