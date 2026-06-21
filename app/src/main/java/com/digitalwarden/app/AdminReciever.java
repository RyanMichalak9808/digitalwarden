package com.digitalwarden.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        ServiceStarter.start(context);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        // Device admin was removed externally; nothing to clean up here since
        // suspensions/restrictions are tied to device owner state itself.
    }
}
