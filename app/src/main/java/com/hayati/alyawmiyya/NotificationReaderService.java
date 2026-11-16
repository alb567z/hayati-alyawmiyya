package com.hayati.alyawmiyya;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.util.List;
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


    public static boolean controlMedia(Context context, String action) {
        NotificationReaderService service = instance;
        if (service == null || !hasAccess(context)) return false;
        try {
            if ("volume_up".equals(action) || "volume_down".equals(action)) {
                AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (audio == null) return false;
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    "volume_up".equals(action) ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                return true;
            }
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return false;
            List<MediaController> sessions = manager.getActiveSessions(new ComponentName(context, NotificationReaderService.class));
            if (sessions == null || sessions.isEmpty()) return false;
            MediaController controller = sessions.get(0);
            MediaController.TransportControls controls = controller.getTransportControls();
            if ("play_pause".equals(action)) {
                PlaybackState state = controller.getPlaybackState();
                int current = state == null ? PlaybackState.STATE_NONE : state.getState();
                if (current == PlaybackState.STATE_PLAYING || current == PlaybackState.STATE_BUFFERING) controls.pause();
                else controls.play();
            } else if ("next".equals(action)) controls.skipToNext();
            else if ("previous".equals(action)) controls.skipToPrevious();
            else return false;
            return true;
        } catch (SecurityException | IllegalStateException error) {
            return false;
        }
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
