package com.digitalwarden.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String windowId = intent.getStringExtra(ScheduleManager.EXTRA_WINDOW_ID);
        ServiceStarter.refresh(context);

        // Re-arm this window's alarms for next week.
        if (windowId != null) {
            PrefsRepository repo = new PrefsRepository(context);
            LockdownConfig config = repo.load();
            List<TimeWindow> schedules = config.schedules;
            for (TimeWindow w : schedules) {
                if (w.id.equals(windowId) && w.enabled) {
                    ScheduleManager.arm(context, w);
                    break;
                }
            }
        }
    }
}
