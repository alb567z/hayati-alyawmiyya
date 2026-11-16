package com.hayati.alyawmiyya;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationReaderService extends NotificationListenerService {
    private static volatile NotificationReaderService instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean hasAccess(Context context) {
        String enabled = Settings.Secure.getString(
            context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        String component = new ComponentName(context, NotificationReaderService.class).flattenToString();
        return enabled.contains(component);
    }

    public static int getActiveCount() {
        NotificationReaderService service = instance;
        if (service == null) return -1;
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            return active == null ? 0 : active.length;
        } catch (SecurityException e) {
            return -1;
        }
    }
}
