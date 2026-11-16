package com.hayati.alyawmiyya;
import android.app.*;
import android.content.*;
import android.os.Build;
public class ReminderReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent) {
  String title=intent.getStringExtra("title"),type=intent.getStringExtra("type");
  if(!context.getSharedPreferences("hayati_data",Context.MODE_PRIVATE).getBoolean("notifications_enabled",true))return;
  if(title==null||title.trim().isEmpty())title="لديك تذكير";
  boolean work="work".equals(type); int id=intent.getIntExtra("id",1);
  Intent open=new Intent(context,work?IncomingCallActivity.class:MainActivity.class);
  open.putExtra("title",title);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent content=PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
  String channel=work?"sahbi_work_calls":"hayati_reminders";
  if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(channel,work?"اتصالات مواعيد العمل":"تذكيرات صاحبي",NotificationManager.IMPORTANCE_HIGH);ch.setDescription(work?"اتصال افتراضي لموعد العمل":"تنبيهات المواعيد");nm.createNotificationChannel(ch);}
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,channel):new Notification.Builder(context);
  b.setSmallIcon(work?android.R.drawable.sym_call_incoming:android.R.drawable.ic_dialog_info)
   .setContentTitle(work?"مكالمة واردة — نسرين":"صاحبي").setContentText(work?"موعد عملك: "+title:title)
   .setStyle(new Notification.BigTextStyle().bigText(work?"نسرين تتصل بك للتذكير بموعد العمل: "+title:title))
   .setCategory(work?Notification.CATEGORY_CALL:Notification.CATEGORY_REMINDER)
   .setPriority(work?Notification.PRIORITY_MAX:Notification.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(content);
  if(work)b.setFullScreenIntent(content,true);
  nm.notify(id,b.build());
 }
}