package com.digitalwarden.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.UserManager;

import androidx.core.app.NotificationCompat;

public class LockdownService extends Service {

    public static final String ACTION_REFRESH = "com.digitalwarden.app.action.REFRESH";
    private static final String CHANNEL_ID = "lockdown_channel";
    private static final int NOTIF_ID = 1;

    private DevicePolicyManager dpm;
    private ComponentName admin;
    private PrefsRepository repo;
    private UsageTrackerService usageTracker;

    @Override
    public void onCreate() {
        super.onCreate();
        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(this, AdminReceiver.class);
        repo = new PrefsRepository(this);

        startForeground(NOTIF_ID, buildNotification());
        applyAll();

        usageTracker = new UsageTrackerService(this);
        usageTracker.start();

        UsageResetReceiver.rearm(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            applyAll();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (usageTracker != null) {
            usageTracker.stop();
        }
        super.onDestroy();
    }

    // --- Enforcement ---

    private void applyAll() {
        if (!isDeviceOwner()) return; // safety: never call DPM APIs without device owner
        applyAppSuspension();
        applyUserRestrictions();
        enforcePrivateDnsLock();
    }

    private boolean isDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(getPackageName());
    }

    private void applyAppSuspension() {
        LockdownConfig config = repo.load();
        if (config.blockedPackages.isEmpty()) return;

        // Schedule, if any windows are configured, takes precedence over the static list:
        // if at least one schedule exists, only suspend during active windows.
        boolean shouldSuspend = config.schedules.isEmpty() || ScheduleManager.isWithinActiveWindow(config);

        String[] pkgs = config.blockedPackages.toArray(new String[0]);
        try {
            // setPackagesSuspended returns the subset of package names it could NOT apply to
            // (e.g. not installed, or not allowed to be suspended) rather than throwing for that case.
            String[] failed = dpm.setPackagesSuspended(admin, pkgs, shouldSuspend);
            if (failed != null && failed.length > 0) {
                // Leave these in the stored blocklist (the app may be (re)installed later),
                // just note that they didn't apply this round.
            }
        } catch (SecurityException notOwnerYet) {
            // Not device owner (yet) - nothing we can do until provisioning is completed.
        }
    }

    private void applyUserRestrictions() {
        LockdownConfig config = repo.load();
        for (String restriction : config.activeRestrictions) {
            try {
                dpm.addUserRestriction(admin, restriction);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * DNS lock, take 2: instead of polling for changes and reverting them after the fact (which
     * always has a detection-to-revert gap, however small), this removes the user's ability to
     * open or modify the Private DNS settings screen at all via
     * UserManager.DISALLOW_CONFIG_PRIVATE_DNS. There is nothing to revert because there is no
     * write path left for the user. The hostname itself is still set the normal way via
     * setGlobalPrivateDns(); the restriction is what makes it unchangeable rather than just
     * "currently correct."
     *
     * Note: DISALLOW_CONFIG_PRIVATE_DNS only blocks the Settings UI path. A user could still
     * try to override DNS via a VPN app routing traffic elsewhere - if you want that closed
     * too, also enable "Block VPN configuration changes" (DISALLOW_CONFIG_VPN) in the
     * Restrictions tab.
     */
    private void enforcePrivateDnsLock() {
        LockdownConfig config = repo.load();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;

        try {
            if (config.privateDnsEnforced && !config.privateDnsHostname.isEmpty()) {
                dpm.setGlobalPrivateDns(admin,
                        DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME,
                        config.privateDnsHostname);
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
            } else {
                // Enforcement turned off: give the settings screen back to the user.
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
            }
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Digital Warden Active", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Digital Warden active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }
}
