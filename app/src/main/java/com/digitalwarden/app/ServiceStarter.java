package com.digitalwarden.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ServiceStarter {
    public static void start(Context context) {
        Intent intent = new Intent(context, LockdownService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void refresh(Context context) {
        Intent intent = new Intent(context, LockdownService.class);
        intent.setAction(LockdownService.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
