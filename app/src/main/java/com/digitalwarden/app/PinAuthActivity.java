package com.digitalwarden.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Handles three flows depending on how it's launched:
 *  - First run (no PIN set yet): set a new PIN.
 *  - Normal unlock (app reopened/resumed): verify the existing PIN, then go to MainActivity.
 *  - PIN reset (launched from inside the app, MODE_RESET extra set): verify the CURRENT pin
 *    first, then prompt for and save a new one. Does not require re-entering MainActivity's
 *    PIN gate again afterward since the user is already inside an authenticated session.
 */
public class PinAuthActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_RESET = "reset";

    private PrefsRepository repo;
    private boolean isResetFlow = false;
    private boolean settingNewPin = false;
    private boolean verifiedCurrentPinForReset = false;
    private String firstEntry = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_auth);
        repo = new PrefsRepository(this);

        TextView title = findViewById(R.id.titleText);
        EditText pinField = findViewById(R.id.pinField);
        Button submit = findViewById(R.id.submitBtn);

        isResetFlow = MODE_RESET.equals(getIntent().getStringExtra(EXTRA_MODE));
        LockdownConfig config = repo.load();

        if (isResetFlow) {
            title.setText("Enter current PIN");
        } else if (config.pinHash.isEmpty()) {
            settingNewPin = true;
            title.setText("Set a PIN (4+ digits)");
        } else {
            title.setText("Enter PIN");
        }

        submit.setOnClickListener(v -> {
            String entered = pinField.getText().toString();
            if (entered.length() < 4) {
                pinField.setError("PIN must be at least 4 digits");
                return;
            }

            if (isResetFlow && !verifiedCurrentPinForReset) {
                LockdownConfig c = repo.load();
                if (PinUtil.verify(entered, c.pinSalt, c.pinHash)) {
                    verifiedCurrentPinForReset = true;
                    settingNewPin = true;
                    pinField.setText("");
                    title.setText("Set a new PIN (4+ digits)");
                } else {
                    pinField.setError("Incorrect current PIN");
                    pinField.setText("");
                }
                return;
            }

            if (settingNewPin) {
                if (firstEntry == null) {
                    firstEntry = entered;
                    pinField.setText("");
                    title.setText("Confirm PIN");
                } else if (firstEntry.equals(entered)) {
                    String salt = PinUtil.generateSalt();
                    LockdownConfig c = repo.load();
                    c.pinSalt = salt;
                    c.pinHash = PinUtil.hash(entered, salt);
                    repo.save(c);
                    if (isResetFlow) {
                        Toast.makeText(this, "PIN updated", Toast.LENGTH_SHORT).show();
                        SessionState.markAuthenticated();
                        finish();
                    } else {
                        goToMain();
                    }
                } else {
                    pinField.setError("PINs didn't match, try again");
                    firstEntry = null;
                    pinField.setText("");
                    title.setText(isResetFlow ? "Set a new PIN (4+ digits)" : "Set a PIN (4+ digits)");
                }
            } else {
                LockdownConfig c = repo.load();
                if (PinUtil.verify(entered, c.pinSalt, c.pinHash)) {
                    goToMain();
                } else {
                    pinField.setError("Incorrect PIN");
                    pinField.setText("");
                }
            }
        });
    }

    private void goToMain() {
        SessionState.markAuthenticated();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
