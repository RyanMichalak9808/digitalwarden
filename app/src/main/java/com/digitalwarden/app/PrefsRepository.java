package com.digitalwarden.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

public class PrefsRepository {
    private static final String PREFS = "lockdown_prefs";
    private static final String KEY_CONFIG = "config_json";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public PrefsRepository(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized LockdownConfig load() {
        String json = prefs.getString(KEY_CONFIG, null);
        if (json == null) {
            return new LockdownConfig();
        }
        try {
            LockdownConfig config = gson.fromJson(json, LockdownConfig.class);
            return config != null ? config : new LockdownConfig();
        } catch (Exception e) {
            return new LockdownConfig();
        }
    }

    public synchronized void save(LockdownConfig config) {
        prefs.edit().putString(KEY_CONFIG, gson.toJson(config)).apply();
    }
}
