package com.digitalwarden.app;

/**
 * Tracks whether the user has unlocked the app in the current foreground session. Deliberately
 * in-memory only (not persisted to SharedPreferences/disk): if the process is killed (app fully
 * closed, swiped from recents, phone rebooted) this resets to false automatically just by
 * virtue of no longer existing, so there's no "remember me" leak across app restarts.
 *
 * MainActivity clears this in onPause() (backgrounded) and checks it in onResume(), which means
 * the PIN is required both on a cold launch AND every time you return to the app after leaving
 * it - not just on first open.
 */
public class SessionState {

    private static volatile boolean authenticated = false;

    public static void markAuthenticated() {
        authenticated = true;
    }

    public static void clear() {
        authenticated = false;
    }

    public static boolean isAuthenticated() {
        return authenticated;
    }
}
