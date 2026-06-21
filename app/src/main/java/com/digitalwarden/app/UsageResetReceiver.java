package com.digitalwarden.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/**
 * Fires daily at 2:00am local time. Resets every TimedAppQuota's used time back to zero and
 * un-suspends any app that had been blocked for hitting its quota, then re-arms itself for the
 * next day. This is the only place "today's usage" gets cleared - UsageTrackerService just
 * accumulates and suspends, it never resets on its own (other than the overdue-catch-up case
 * for when a device was off across the boundary).
 */
public class UsageResetReceiver extends BroadcastReceiver {

    public static final String ACTION_RESET = "com.digitalwarden.app.action.USAGE_RESET";
    private static final int RESET_HOUR = 2; // 2am
    private static final int RESET_MINUTE = 0;
    private static final int REQUEST_CODE = 9001;

    @Override
    public void onReceive(Context context, Intent intent) {
        performReset(context);
        rearm(context);
    }

    public static void performReset(Context context) {
        PrefsRepository repo = new PrefsRepository(context);
        LockdownConfig config = repo.load();
        if (config.timedApps.isEmpty()) return;

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, AdminReceiver.class);
        boolean isOwner = dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());

        long now = System.currentTimeMillis();
        for (TimedAppQuota quota : config.timedApps.values()) {
            boolean wasBlocked = quota.blockedForToday;
            quota.usedMillisToday = 0;
            quota.blockedForToday = false;
            quota.lastResetEpochMillis = now;

            // BLOCK always wins: if this package is also in the permanent blocklist (shouldn't
            // happen given how the Apps tab enforces exclusivity, but this is the
            // enforcement-layer backstop), never un-suspend it just because its quota reset -
            // it should stay suspended for the permanent-block reason instead.
            boolean permanentlyBlocked = config.blockedPackages.contains(quota.packageName);
            if (wasBlocked && isOwner && !permanentlyBlocked) {
                try {
                    dpm.setPackagesSuspended(admin, new String[]{quota.packageName}, false);
                } catch (Exception ignored) {
                }
            }
        }
        repo.save(config);
    }

    /** Schedules (or re-schedules) the next 2am firing. Call on boot and after config changes. */
    public static void rearm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long nextReset = nextTwoAmEpochMillis(System.currentTimeMillis());
        PendingIntent pi = buildPendingIntent(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReset, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReset, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReset, pi);
        }
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, UsageResetReceiver.class);
        intent.setAction(ACTION_RESET);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static long nextTwoAmEpochMillis(long fromMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fromMillis);
        Calendar candidate = (Calendar) cal.clone();
        candidate.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
        candidate.set(Calendar.MINUTE, RESET_MINUTE);
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);
        if (!candidate.after(cal)) {
            candidate.add(Calendar.DAY_OF_YEAR, 1);
        }
        return candidate.getTimeInMillis();
    }

    /**
     * Returns the epoch millis of the most recent 2am boundary at or before `now`. Used by
     * UsageTrackerService to detect "this quota hasn't been reset since the most recent 2am
     * boundary" even if the scheduled alarm itself didn't fire (e.g. device was powered off).
     */
    public static long mostRecentTwoAmBoundary(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        Calendar candidate = (Calendar) cal.clone();
        candidate.set(Calendar.HOUR_OF_DAY, RESET_HOUR);
        candidate.set(Calendar.MINUTE, RESET_MINUTE);
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);
        if (candidate.after(cal)) {
            candidate.add(Calendar.DAY_OF_YEAR, -1);
        }
        return candidate.getTimeInMillis();
    }
}
