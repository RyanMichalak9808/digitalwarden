package com.digitalwarden.app;

/**
 * Daily usage quota for a single app. The app is allowed up to {@code dailyLimitMinutes}
 * minutes of actual foreground usage per day (tracked via UsageStatsManager), then it is
 * suspended (cannot be launched) until the next reset.
 *
 * Resets happen at a fixed time each day (2am, see UsageResetReceiver) rather than on a
 * rolling 24h window, so "today's usage" always means "usage since the most recent 2am".
 */
public class TimedAppQuota {

    public String packageName;

    /** How many minutes per day this app is allowed before it gets suspended. User-adjustable. */
    public int dailyLimitMinutes;

    /** Minutes used since the last reset. Accumulated by UsageTrackerService. */
    public long usedMillisToday;

    /**
     * Epoch millis of the last time usedMillisToday was reset to 0. Used so that if the device
     * was off through a scheduled 2am reset, we can detect on next boot/check that a reset is
     * overdue and apply it immediately instead of waiting for the next exact alarm.
     */
    public long lastResetEpochMillis;

    /** True once dailyLimitMinutes has been hit today and the app has been suspended for it. */
    public boolean blockedForToday;

    public TimedAppQuota() {
    }

    public TimedAppQuota(String packageName, int dailyLimitMinutes) {
        this.packageName = packageName;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.usedMillisToday = 0;
        this.lastResetEpochMillis = System.currentTimeMillis();
        this.blockedForToday = false;
    }

    public long dailyLimitMillis() {
        return dailyLimitMinutes * 60_000L;
    }

    public long remainingMillis() {
        return Math.max(0, dailyLimitMillis() - usedMillisToday);
    }
}
