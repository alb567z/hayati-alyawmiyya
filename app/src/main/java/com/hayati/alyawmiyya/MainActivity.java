package com.hayati.alyawmiyya;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final String PREFS = "hayati_data";
    private static final String TASKS = "tasks", NOTES = "notes", REMINDERS = "reminders";
    private LinearLayout root, content, nav;
    private String currentTab = "today";
    private final int ink = Color.rgb(35, 48, 65), teal = Color.rgb(0, 137, 123);
    private JSONArray tasks, notes, reminders;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(0, 105, 92));
        getWindow().setNavigationBarColor(Color.rgb(250, 250, 250));
        loadData();
        createNotificationChannel();
        requestNotificationPermission();
        buildShell();
        showTab("today");
    }

    private void loadData() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        tasks = readArray(p.getString(TASKS, "[]"));
        notes = readArray(p.getString(NOTES, "[]"));
        reminders = readArray(p.getString(REMINDERS, "[]"));
    }
    private JSONArray readArray(String s) { try { return new JSONArray(s); } catch(Exception e) { return new JSONArray(); } }
    private void saveData() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TASKS, tasks.toString())
            .putString(NOTES, notes.toString()).putString(REMINDERS, reminders.toString()).apply();
    }

    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(246,248,250));
        setContentView(root);
        LinearLayout header = new LinearLayout(this); header.setPadding(22,20,22,18); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(teal); header.setOrientation(LinearLayout.VERTICAL);
        TextView brand = new TextView(this); brand.setText("حياتي اليومية  ♥"); brand.setTextColor(Color.WHITE); brand.setTextSize(24); brand.setGravity(Gravity.RIGHT);
        TextView sub = new TextView(this); sub.setText("رتّب يومك، خطوة بخطوة"); sub.setTextColor(Color.rgb(220,245,240)); sub.setTextSize(14); sub.setGravity(Gravity.RIGHT);
        header.addView(brand); header.addView(sub); root.addView(header);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(16,16,16,22);
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        nav = new LinearLayout(this); nav.setPadding(4,8,4,8); nav.setBackgroundColor(Color.WHITE); root.addView(nav);
        addNav("today","اليوم"); addNav("tasks","المهام"); addNav("reminders","التذكيرات"); addNav("notes","الملاحظات");
    }
    private void addNav(String id, String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setAllCaps(false);
        b.setOnClickListener(v -> showTab(id)); nav.addView(b,new LinearLayout.LayoutParams(0,54,1));
    }
    private void showTab(String tab) {
        currentTab=tab; content.removeAllViews();
        if ("today".equals(tab)) showToday();
        else if ("tasks".equals(tab)) showTasks();
        else if ("reminders".equals(tab)) showReminders();
        else showNotes();
    }
    private TextView text(String value, int size, int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.RIGHT); t.setPadding(4,8,4,8); return t;
    }
    private void heading(String title, String hint) {
        content.addView(text(title,22,ink));
        if(hint!=null) content.addView(text(hint,14,Color.GRAY));
    }
    private Button button(String label, Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(teal));
        b.setOnClickListener(v->action.run()); return b;
    }
    private void card(View v) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(12,8,12,8);
        box.setBackgroundColor(Color.WHITE); box.addView(v); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,6,0,6); content.addView(box,lp);
    }
    private void showToday() {
        String date=new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(new Date());
        heading("ملخص يومك",date);
        int done=0; for(int i=0;i<tasks.length();i++) if(tasks.optJSONObject(i)!=null && tasks.optJSONObject(i).optBoolean("done")) done++;
        card(text("المهام: "+done+" من "+tasks.length()+" مكتملة",17,ink));
        card(text("التذكيرات المحفوظة: "+reminders.length(),17,ink));
        card(text("الملاحظات: "+notes.length(),17,ink));
        content.addView(button("＋ إضافة مهمة",()->addTask()));
        content.addView(button("＋ إضافة تذكير",()->pickReminder()));
        content.addView(button("＋ كتابة ملاحظة",()->addNote()));
        content.addView(text("مهام اليوم",18,ink));
        boolean any=false;
        for(int i=0;i<tasks.length();i++){ JSONObject o=tasks.optJSONObject(i); if(o!=null){ any=true; card(text((o.optBoolean("done")?"✓ ":"○ ")+o.optString("title"),16,ink)); }}
        if(!any) card(text("لا توجد مهام بعد. أضف أول مهمة ليومك.",15,Color.GRAY));
    }
    private void showTasks() {
        heading("مهامي", "أضف مهامك وتابع إنجازها");
        content.addView(button("＋ مهمة جديدة",()->addTask()));
        for(int i=0;i<tasks.length();i++){
            final int index=i; JSONObject o=tasks.optJSONObject(i); if(o==null) continue;
            LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb=new CheckBox(this); cb.setText(o.optString("title")); cb.setTextSize(16); cb.setTextColor(ink); cb.setChecked(o.optBoolean("done"));
            cb.setOnCheckedChangeListener((v,checked)->{o.put("done",checked);saveData();});
            line.addView(cb,new LinearLayout.LayoutParams(0,-2,1));
            Button del=new Button(this); del.setText("حذف"); del.setTextSize(11); del.setOnClickListener(v->{tasks.remove(index);saveData();showTab("tasks");});
            line.addView(del); card(line);
        }
        if(tasks.length()==0) card(text("قائمة المهام فارغة حالياً.",15,Color.GRAY));
    }
    private void addTask() { inputDialog("مهمة جديدة","اكتب المهمة",value->{JSONObject o=new JSONObject();o.put("title",value);o.put("done",false);tasks.put(o);saveData();showTab(currentTab);}); }
    private void showNotes() {
        heading("ملاحظاتي", "أفكارك ومعلوماتك في مكان واحد");
        content.addView(button("＋ ملاحظة جديدة",()->addNote()));
        for(int i=0;i<notes.length();i++){
            final int index=i; JSONObject o=notes.optJSONObject(i); if(o==null)continue;
            LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
            box.addView(text(o.optString("title"),18,ink)); box.addView(text(o.optString("body"),15,Color.DKGRAY));
            LinearLayout actions=new LinearLayout(this);
            Button edit=new Button(this);edit.setText("تعديل");edit.setOnClickListener(v->editNote(index));
            Button del=new Button(this);del.setText("حذف");del.setOnClickListener(v->{notes.remove(index);saveData();showTab("notes");});
            actions.addView(edit);actions.addView(del);box.addView(actions);card(box);
        }
        if(notes.length()==0)card(text("لا توجد ملاحظات محفوظة.",15,Color.GRAY));
    }
    private void addNote() {
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
        EditText title=new EditText(this);title.setHint("عنوان الملاحظة");fields.addView(title);
        EditText body=new EditText(this);body.setHint("اكتب ملاحظتك هنا");body.setMinLines(3);body.setGravity(Gravity.TOP);fields.addView(body);
        new AlertDialog.Builder(this).setTitle("ملاحظة جديدة").setView(fields).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{
            String t=title.getText().toString().trim(), b=body.getText().toString().trim(); if(t.isEmpty()&&b.isEmpty())return;
            JSONObject o=new JSONObject();o.put("title",t.isEmpty()?"ملاحظة":t);o.put("body",b);notes.put(o);saveData();showTab("notes");
        }).show();
    }
    private void editNote(int index) {
        JSONObject old=notes.optJSONObject(index); if(old==null)return;
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
        EditText title=new EditText(this);title.setText(old.optString("title"));fields.addView(title);
        EditText body=new EditText(this);body.setText(old.optString("body"));body.setMinLines(3);body.setGravity(Gravity.TOP);fields.addView(body);
        new AlertDialog.Builder(this).setTitle("تعديل الملاحظة").setView(fields).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{
            old.put("title",title.getText().toString());old.put("body",body.getText().toString());saveData();showTab("notes");
        }).show();
    }
    private void inputDialog(String title,String hint, java.util.function.Consumer<String> done) {
        EditText input=new EditText(this);input.setHint(hint);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{
            String s=input.getText().toString().trim();if(!s.isEmpty())done.accept(s);
        }).show();
    }
    private void showReminders() {
        heading("تذكيراتي", "حدد التاريخ والوقت وسينبهك الهاتف");
        content.addView(button("＋ تذكير جديد",()->pickReminder()));
        for(int i=0;i<reminders.length();i++){
            final int index=i;JSONObject o=reminders.optJSONObject(i);if(o==null)continue;
            LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.VERTICAL);
            line.addView(text(o.optString("title"),17,ink));
            line.addView(text(o.optString("when"),14,Color.GRAY));
            Button del=new Button(this);del.setText("حذف التذكير");del.setOnClickListener(v->{cancelReminder(o.optInt("id"));reminders.remove(index);saveData();showTab("reminders");});
            line.addView(del);card(line);
        }
        if(reminders.length()==0)card(text("لا توجد تذكيرات مجدولة.",15,Color.GRAY));
    }
    private void pickReminder() {
        Calendar c=Calendar.getInstance();
        new DatePickerDialog(this,(view,y,m,day)->{
            Calendar chosen=Calendar.getInstance();chosen.set(y,m,day);
            new TimePickerDialog(this,(tv,hour,minute)->{
                chosen.set(Calendar.HOUR_OF_DAY,hour);chosen.set(Calendar.MINUTE,minute);chosen.set(Calendar.SECOND,0);
                if(chosen.getTimeInMillis()<=System.currentTimeMillis()){toast("اختَر وقتاً في المستقبل");return;}
                inputDialog("عنوان التذكير","مثلاً: الاتصال بعبدالباري",title->scheduleReminder(title,chosen));
            },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void scheduleReminder(String title,Calendar when) {
        int id=(int)(System.currentTimeMillis() & 0x7fffffff);
        Intent intent=new Intent(this,ReminderReceiver.class);intent.putExtra("title",title);intent.putExtra("id",id);
        PendingIntent pi=PendingIntent.getBroadcast(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
        if(am!=null){if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when.getTimeInMillis(),pi);else am.set(AlarmManager.RTC_WAKEUP,when.getTimeInMillis(),pi);}
        String label=new SimpleDateFormat("yyyy/MM/dd  HH:mm",Locale.getDefault()).format(when.getTime());
        JSONObject o=new JSONObject();o.put("id",id);o.put("title",title);o.put("when",label);reminders.put(o);saveData();showTab("reminders");toast("تم حفظ التذكير");
    }
    private void cancelReminder(int id) {
        Intent intent=new Intent(this,ReminderReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(am!=null)am.cancel(pi);pi.cancel();
    }
    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel("hayati_reminders","تذكيرات حياتي اليومية",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("تنبيهات المواعيد والمهام");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}
    }
    private void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},41);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
