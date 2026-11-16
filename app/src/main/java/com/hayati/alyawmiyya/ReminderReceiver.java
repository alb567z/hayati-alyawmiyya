package com.hayati.alyawmiyya;

import android.app.*;
import android.content.*;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String title=intent.getStringExtra("title");
        if(title==null||title.trim().isEmpty())title="لديك تذكير";
        Intent open=new Intent(context,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,"hayati_reminders"):new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("حياتي اليومية").setContentText(title).setStyle(new Notification.BigTextStyle().bigText(title)).setAutoCancel(true).setContentIntent(content);
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.notify(intent.getIntExtra("id",1),b.build());
    }
}
