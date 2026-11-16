package com.hayati.alyawmiyya;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "sahbi_reminder_calls_v2";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        if (!context.getSharedPreferences("hayati_data", Context.MODE_PRIVATE)
                .getBoolean("notifications_enabled", true)) return;
        if (title == null || title.trim().isEmpty()) title = "لديك تذكير";
        int id = intent.getIntExtra("id", 1);

        // Present every reminder as the requested incoming call screen.
        Intent open = new Intent(context, IncomingCallActivity.class);
        open.putExtra("title", title);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "اتصالات تذكير صاحبي", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("اتصال وارد بصوت واهتزاز عند حلول المهام والمواعيد");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 700, 450, 700, 450, 1100});
            Uri tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (tone == null) tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            AudioAttributes audio = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(tone, audio);
            nm.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("مكالمة واردة — نسرين")
                .setContentText("نسرين تذكّرك: " + title)
                .setStyle(new Notification.BigTextStyle()
                        .bigText("نسرين تتصل بك لتذكيرك بالمهمة أو الموعد: " + title))
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(content)
                .setVibrate(new long[]{0, 700, 450, 700, 450, 1100});
        builder.setFullScreenIntent(content, true);
        nm.notify(id, builder.build());
    }
}