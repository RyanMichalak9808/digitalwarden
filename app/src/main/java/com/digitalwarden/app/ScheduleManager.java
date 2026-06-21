package com.digitalwarden.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public class ScheduleManager {

    public static final String EXTRA_WINDOW_ID = "window_id";
    public static final String ACTION_START = "com.digitalwarden.app.action.SCHEDULE_START";
    public static final String ACTION_END = "com.digitalwarden.app.action.SCHEDULE_END";

    /** Re-registers alarms for every enabled schedule. Call on boot and whenever schedules change. */
    public static void rearmAll(Context context) {
        PrefsRepository repo = new PrefsRepository(context);
        LockdownConfig config = repo.load();
        for (TimeWindow w : config.schedules) {
            if (w.enabled) {
                arm(context, w);
            } else {
                cancel(context, w);
            }
        }
    }

    public static void arm(Context context, TimeWindow w) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        for (int day : w.daysOfWeek) {
            long startMillis = nextOccurrence(day, w.startHour, w.startMinute);
            long endMillis = nextOccurrence(day, w.endHour, w.endMinute);
            // If end time is before start time same day, it means the window crosses midnight.
            if (endMillis <= startMillis) {
                endMillis = addDays(endMillis, 1);
            }

            PendingIntent startPi = buildPendingIntent(context, w.id, day, true);
            PendingIntent endPi = buildPendingIntent(context, w.id, day, false);

            setExact(am, startMillis, startPi);
            setExact(am, endMillis, endPi);
        }
    }

    public static void cancel(Context context, TimeWindow w) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int day : w.daysOfWeek) {
            am.cancel(buildPendingIntent(context, w.id, day, true));
            am.cancel(buildPendingIntent(context, w.id, day, false));
        }
    }

    private static void setExact(AlarmManager am, long triggerAt, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private static PendingIntent buildPendingIntent(Context context, String windowId, int day, boolean isStart) {
        Intent intent = new Intent(context, ScheduleReceiver.class);
        intent.setAction(isStart ? ACTION_START : ACTION_END);
        intent.putExtra(EXTRA_WINDOW_ID, windowId);
        // Unique request code per window+day+direction so alarms don't overwrite each other.
        int requestCode = (windowId.hashCode() & 0xFFFF) * 100 + day * 2 + (isStart ? 0 : 1);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static long nextOccurrence(int dayOfWeek, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        Calendar now = (Calendar) cal.clone();
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.before(now)) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private static long addDays(long millis, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.add(Calendar.DAY_OF_YEAR, days);
        return cal.getTimeInMillis();
    }

    /** Checks if "now" falls within any enabled schedule window. Used by LockdownService. */
    public static boolean isWithinActiveWindow(LockdownConfig config) {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK);
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        for (TimeWindow w : config.schedules) {
            if (!w.enabled) continue;
            int startMinutes = w.startHour * 60 + w.startMinute;
            int endMinutes = w.endHour * 60 + w.endMinute;

            if (endMinutes > startMinutes) {
                // same-day window
                if (w.daysOfWeek.contains(today) && nowMinutes >= startMinutes && nowMinutes < endMinutes) {
                    return true;
                }
            } else {
                // crosses midnight: active if (today, after start) OR (yesterday's window, before end)
                int yesterday = today == Calendar.SUNDAY ? Calendar.SATURDAY : today - 1;
                if (w.daysOfWeek.contains(today) && nowMinutes >= startMinutes) return true;
                if (w.daysOfWeek.contains(yesterday) && nowMinutes < endMinutes) return true;
            }
        }
        return false;
    }
}
