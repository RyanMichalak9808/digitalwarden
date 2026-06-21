package com.digitalwarden.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only helper for surfacing "how much screen time has each app gotten" - this is just
 * Android's own UsageStatsManager data, the same source the system Settings -> Screen time /
 * Digital Wellbeing screens use, queried directly so this app can show it without needing
 * Digital Wellbeing installed (GrapheneOS doesn't ship Google's Digital Wellbeing app).
 *
 * This is independent of TimedAppQuota / UsageTrackerService: those only track apps you've
 * explicitly given a daily quota. This shows usage for every app, quota or not.
 */
public class AppUsageStats {

    public static class Entry {
        public final String packageName;
        public final long totalForegroundMillis;

        Entry(String packageName, long totalForegroundMillis) {
            this.packageName = packageName;
            this.totalForegroundMillis = totalForegroundMillis;
        }
    }

    /** Usage since the most recent 2am boundary (matches this app's own "today" definition). */
    public static List<Entry> queryToday(Context context) {
        long now = System.currentTimeMillis();
        long start = UsageResetReceiver.mostRecentTwoAmBoundary(now);
        return query(context, start, now);
    }

    /** Usage over the last 7 days, for a longer-range view. */
    public static List<Entry> queryLastWeek(Context context) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.add(Calendar.DAY_OF_YEAR, -7);
        return query(context, cal.getTimeInMillis(), now);
    }

    private static List<Entry> query(Context context, long startMillis, long endMillis) {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return Collections.emptyList();

        // queryUsageStats with INTERVAL_BEST gives Android's own pre-aggregated per-app totals
        // for the range, which is both cheaper and more accurate than summing raw UsageEvents
        // ourselves for anything beyond "what's in the foreground right now."
        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startMillis, endMillis);
        if (statsMap == null) return Collections.emptyList();

        List<Entry> result = new ArrayList<>();
        for (UsageStats stats : statsMap.values()) {
            long totalTime = stats.getTotalTimeInForeground();
            if (totalTime <= 0) continue;
            result.add(new Entry(stats.getPackageName(), totalTime));
        }
        result.sort((a, b) -> Long.compare(b.totalForegroundMillis, a.totalForegroundMillis));
        return result;
    }

    /** Convenience: map package name -> millis, for quick lookups when rendering a full app list. */
    public static Map<String, Long> queryTodayAsMap(Context context) {
        Map<String, Long> map = new HashMap<>();
        for (Entry e : queryToday(context)) {
            map.put(e.packageName, e.totalForegroundMillis);
        }
        return map;
    }

    public static String formatDuration(long millis) {
        long totalMinutes = millis / 60_000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        long seconds = (millis / 1000) % 60;
        return seconds + "s";
    }
}
