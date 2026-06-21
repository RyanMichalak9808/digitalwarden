package com.digitalwarden.app;

import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * Polls UsageStatsManager for the current foreground app on a short interval and accumulates
 * usage time against any configured TimedAppQuota. The moment a quota's remaining time hits
 * zero, the app is suspended via DevicePolicyManager immediately (same poll tick), not on a
 * separate slower "watchdog" cycle, so the worst-case delay is bounded by POLL_INTERVAL_MS,
 * not by some longer enforcement sweep.
 *
 * This intentionally does NOT use a fixed "check every N minutes and subtract N minutes"
 * model, because that overcounts/undercounts depending on whether the app was actually in the
 * foreground for the whole interval. Instead we look at the actual last-foreground-app event
 * timestamp from UsageEvents on each tick and only add elapsed time if the timed app was the
 * one in the foreground for that slice.
 */
public class UsageTrackerService {

    private static final long POLL_INTERVAL_MS = 5_000L; // 5s granularity; bounds worst-case overshoot

    private final Context appContext;
    private final PrefsRepository repo;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;
    private final UsageStatsManager usm;

    private HandlerThread thread;
    private Handler handler;
    private Runnable pollRunnable;

    private String lastForegroundPackage = null;
    private long lastTickAt = 0L;

    public UsageTrackerService(Context context) {
        this.appContext = context.getApplicationContext();
        this.repo = new PrefsRepository(appContext);
        this.dpm = (DevicePolicyManager) appContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(appContext, AdminReceiver.class);
        this.usm = (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    public void start() {
        if (thread != null) return;
        ensureUsageAccessGranted();
        thread = new HandlerThread("UsageTrackerThread");
        thread.start();
        handler = new Handler(thread.getLooper());
        lastTickAt = System.currentTimeMillis();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                tick();
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        handler.post(pollRunnable);
    }

    public void stop() {
        if (handler != null && pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
    }

    /**
     * Device owners can silently grant themselves the PACKAGE_USAGE_STATS app-op without the
     * user manually visiting the "Usage access" settings screen. Doing this means there's no
     * gap between install and enforcement working.
     */
    private void ensureUsageAccessGranted() {
        if (dpm == null || !dpm.isDeviceOwnerApp(appContext.getPackageName())) return;
        try {
            dpm.setPermissionGrantState(admin, appContext.getPackageName(),
                    "android.permission.PACKAGE_USAGE_STATS",
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        } catch (Exception ignored) {
            // Some OEM/Android versions don't model this as a runtime permission via this API;
            // if it fails, the user can still grant Usage Access manually as a fallback.
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        String currentForeground = queryCurrentForegroundPackage(now);

        LockdownConfig config = repo.load();
        if (config.timedApps.isEmpty()) {
            lastForegroundPackage = currentForeground;
            lastTickAt = now;
            return;
        }

        stripQuotasForPermanentlyBlockedPackages(config);
        applyOverdueResetIfNeeded(config, now);

        long elapsed = now - lastTickAt;
        if (lastForegroundPackage != null && elapsed > 0) {
            TimedAppQuota quota = config.timedApps.get(lastForegroundPackage);
            if (quota != null && !quota.blockedForToday) {
                quota.usedMillisToday += elapsed;
                if (quota.usedMillisToday >= quota.dailyLimitMillis()) {
                    quota.usedMillisToday = quota.dailyLimitMillis();
                    quota.blockedForToday = true;
                    suspendImmediately(quota.packageName);
                }
            }
        }

        repo.save(config);
        lastForegroundPackage = currentForeground;
        lastTickAt = now;
    }

    /**
     * Block always wins over a time limit. This should never actually find anything in
     * practice since AppsFragment enforces the two stores are mutually exclusive whenever a
     * mode is changed through the UI, but this is the enforcement-layer backstop: if
     * blockedPackages and timedApps ever disagree (e.g. stale data from an older version of
     * this app, or a future bug), the permanent block is authoritative and the stale quota
     * entry is dropped rather than tracked or used to un-suspend anything.
     */
    private void stripQuotasForPermanentlyBlockedPackages(LockdownConfig config) {
        if (config.blockedPackages.isEmpty() || config.timedApps.isEmpty()) return;
        java.util.Iterator<String> it = config.timedApps.keySet().iterator();
        while (it.hasNext()) {
            String pkg = it.next();
            if (config.blockedPackages.contains(pkg)) {
                it.remove();
            }
        }
    }

    /**
     * Suspends a single package the instant its quota is exhausted, independent of the next
     * full applyAll() refresh cycle, so there's no extra delay layered on top of POLL_INTERVAL_MS.
     */
    private void suspendImmediately(String packageName) {
        if (dpm == null || !dpm.isDeviceOwnerApp(appContext.getPackageName())) return;
        try {
            dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
        } catch (Exception ignored) {
        }
    }

    /**
     * If the device was asleep/off across a scheduled 2am reset (alarm didn't fire, e.g. phone
     * was powered off), catch up here: any quota whose lastResetEpochMillis is from before the
     * most recent 2am boundary gets reset on the next tick after boot, rather than silently
     * staying blocked until the following night's alarm.
     */
    private void applyOverdueResetIfNeeded(LockdownConfig config, long now) {
        long mostRecentBoundary = UsageResetReceiver.mostRecentTwoAmBoundary(now);
        boolean changed = false;
        for (TimedAppQuota quota : config.timedApps.values()) {
            if (quota.lastResetEpochMillis < mostRecentBoundary) {
                quota.usedMillisToday = 0;
                quota.blockedForToday = false;
                quota.lastResetEpochMillis = now;
                changed = true;
                unsuspendImmediately(quota.packageName);
            }
        }
        if (changed) repo.save(config);
    }

    private void unsuspendImmediately(String packageName) {
        if (dpm == null || !dpm.isDeviceOwnerApp(appContext.getPackageName())) return;
        try {
            dpm.setPackagesSuspended(admin, new String[]{packageName}, false);
        } catch (Exception ignored) {
        }
    }

    /**
     * Looks back over the last few minutes of UsageEvents to find the most recent
     * MOVE_TO_FOREGROUND event and returns that package, or null if nothing recent (screen off,
     * launcher, etc).
     */
    private String queryCurrentForegroundPackage(long now) {
        if (usm == null) return null;
        long lookback = now - (POLL_INTERVAL_MS * 4); // small buffer beyond one poll interval
        UsageEvents events = usm.queryEvents(lookback, now);
        if (events == null) return null;

        String latestForegroundPkg = null;
        long latestTimestamp = -1;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            boolean isForegroundEvent =
                    event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED);
            boolean isBackgroundEvent =
                    event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED);

            if (isForegroundEvent && event.getTimeStamp() >= latestTimestamp) {
                latestTimestamp = event.getTimeStamp();
                latestForegroundPkg = event.getPackageName();
            } else if (isBackgroundEvent && event.getTimeStamp() >= latestTimestamp
                    && event.getPackageName().equals(latestForegroundPkg)) {
                // The app that was foreground went to background more recently than it came
                // forward - nothing is confidently in the foreground from our events window.
                latestTimestamp = event.getTimeStamp();
                latestForegroundPkg = null;
            }
        }
        return latestForegroundPkg;
    }
}
