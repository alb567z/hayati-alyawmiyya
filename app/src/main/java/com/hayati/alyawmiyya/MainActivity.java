package com.hayati.alyawmiyya;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private TextToSpeech tts;
    private static final int VOICE_REQUEST = 702;
    private static final int CONTACT_PERMISSION = 703, CALL_PERMISSION = 704;
    private String pendingContactName = "";
    private boolean pendingSms = false;
    private String pendingSmsBody = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(0, 105, 92));
        getWindow().setNavigationBarColor(Color.rgb(250, 250, 250));
        loadData();
        tts = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ar")); });
        createNotificationChannel();
        requestNotificationPermission();
        buildShell();
        showTab("today");
    }

    private void put(JSONObject object, String key, Object value) { try { object.put(key, value); } catch (Exception ignored) {} }

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
        LinearLayout header = new LinearLayout(this); header.setPadding(22,22,22,20); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(0,105,92), Color.rgb(0,150,136)})); header.setOrientation(LinearLayout.VERTICAL);
        TextView brand = new TextView(this); brand.setText("حياتي اليومية  ♥"); brand.setTextColor(Color.WHITE); brand.setTextSize(26); brand.setTypeface(null, android.graphics.Typeface.BOLD); brand.setGravity(Gravity.RIGHT);
        TextView sub = new TextView(this); sub.setText("مساحتك لتنظيم يومك بكل سهولة"); sub.setTextColor(Color.rgb(220,245,240)); sub.setTextSize(14); sub.setGravity(Gravity.RIGHT);
        header.addView(brand); header.addView(sub); Button voice = new Button(this); voice.setText("🎤 تحدث إلى حياتي اليومية"); voice.setAllCaps(false); voice.setTextSize(15); voice.setTextColor(Color.rgb(0,105,92)); voice.setBackground(roundDrawable(Color.WHITE,18)); voice.setOnClickListener(v -> startVoiceInput()); LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(-1, -2); voiceLp.topMargin = 12; header.addView(voice, voiceLp); root.addView(header);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(16,16,16,22);
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        nav = new LinearLayout(this); nav.setPadding(4,8,4,8); nav.setBackgroundColor(Color.WHITE); root.addView(nav);
        addNav("today","اليوم"); addNav("tasks","المهام"); addNav("reminders","التذكيرات"); addNav("notes","الملاحظات"); addNav("settings","الإعدادات");
    }
    private void addNav(String id, String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setAllCaps(false);
        b.setOnClickListener(v -> showTab(id)); nav.addView(b,new LinearLayout.LayoutParams(0,54,1));
    }
    private void showSettings() {
        heading("الإعدادات", "تحكم في صلاحيات وميزات حياتي اليومية");
        android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        Switch voice=new Switch(this); voice.setText("الردود الصوتية"); voice.setTextSize(17); voice.setChecked(p.getBoolean("voice_enabled",true));
        voice.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("voice_enabled",on).apply()); card(voice);
        Switch assistant=new Switch(this); assistant.setText("تشغيل الأوامر الصوتية بالميكروفون"); assistant.setTextSize(17); assistant.setChecked(p.getBoolean("assistant_enabled",true));
        assistant.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("assistant_enabled",on).apply()); card(assistant);
        Switch calls=new Switch(this); calls.setText("السماح بميزة الاتصال"); calls.setTextSize(17); calls.setChecked(p.getBoolean("calls_enabled",true));
        calls.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("calls_enabled",on).apply()); card(calls);
        Switch sms=new Switch(this); sms.setText("السماح بميزة الرسائل"); sms.setTextSize(17); sms.setChecked(p.getBoolean("sms_enabled",true));
        sms.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("sms_enabled",on).apply()); card(sms);
        Switch notifications=new Switch(this); notifications.setText("تفعيل تنبيهات التطبيق"); notifications.setTextSize(17); notifications.setChecked(p.getBoolean("notifications_enabled",true));
        notifications.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("notifications_enabled",on).apply()); card(notifications);
        content.addView(button("🎙️ إذن الميكروفون",()->openPermissionSettings("android.permission.RECORD_AUDIO")));
        content.addView(button("📇 إذن جهات الاتصال",()->openPermissionSettings("android.permission.READ_CONTACTS")));
        content.addView(button("📞 إذن إجراء المكالمات",()->openPermissionSettings("android.permission.CALL_PHONE")));
        content.addView(button("🔔 إذن الإشعارات",()->openAppSettings()));
        Switch motion=new Switch(this); motion.setText("استشعار حركة الهاتف أثناء فتح التطبيق"); motion.setTextSize(16); motion.setChecked(p.getBoolean("motion_enabled",false));
        motion.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("motion_enabled",on).apply()); card(motion);
        content.addView(button("🎙️ إعداد إذن الميكروفون",()->openAppSettings()));
        content.addView(button("🔔 إعدادات الإشعارات",()->{ Intent i=new Intent(); if(Build.VERSION.SDK_INT>=26){i.setAction("android.settings.APP_NOTIFICATION_SETTINGS");i.putExtra("android.provider.extra.APP_PACKAGE",getPackageName());}else{i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");i.setData(Uri.parse("package:"+getPackageName()));} startActivity(i); }));
        content.addView(button("🔐 الخصوصية والصلاحيات",()->new AlertDialog.Builder(this).setTitle("الخصوصية").setMessage("المهام والملاحظات والتذكيرات محفوظة محلياً على هاتفك. جهات الاتصال لا تُستخدم إلا عند طلب الاتصال أو الرسالة. يمكنك سحب أي إذن من إعدادات الهاتف في أي وقت.").setPositiveButton("حسناً",null).show()));
        content.addView(button("⚙️ إعدادات التطبيق في الهاتف",()->openAppSettings()));
        card(text("الاتصال: عند قول «اتصل بـ + اسم الشخص» سيبحث التطبيق في جهات الاتصال، ثم يبدأ الاتصال بعد منح الإذن. الرسائل تفتح شاشة الرسائل لتراجعها وترسلها بنفسك.",14,Color.GRAY));
        card(text("استشعار الحركة يعمل كخيار داخل التطبيق فقط حالياً. تشغيل التطبيق تلقائياً وهو مغلق بسبب الحركة يحتاج خدمة خلفية دائمة وقد يستهلك البطارية، لذلك لم أفعّله دون موافقتك.",14,Color.GRAY));
    }
    private void openPermissionSettings(String permission) {\n        openAppSettings();\n        toast("من صفحة أذونات التطبيق اختَر الإذن المطلوب ثم اسمح أو ارفض");\n    }\n    private void openAppSettings() {
        Intent i=new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",Uri.parse("package:"+getPackageName())); startActivity(i);
    }
    private String findPhone(String wanted) {
        if(checkSelfPermission("android.permission.READ_CONTACTS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
        Cursor cur=null;
        try {
            cur=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);
            if(cur!=null) while(cur.moveToNext()) {
                String name=cur.getString(0), number=cur.getString(1);
                if(name!=null && normalizeName(name).contains(normalizeName(wanted))) return number;
            }
        } catch(Exception ignored) {} finally { if(cur!=null)cur.close(); }
        return null;
    }
    private String normalizeName(String s) { return s==null?"":s.replace("أ","ا").replace("إ","ا").replace("آ","ا").replace("ـ","").replace(" ","").toLowerCase(Locale.ROOT); }
    private void requestContactAction(String name, boolean sms) {
        pendingContactName=name; pendingSms=sms;
        if(checkSelfPermission("android.permission.READ_CONTACTS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.READ_CONTACTS"},CONTACT_PERMISSION); return;
        }
        performContactAction();
    }
    private void performContactAction() {
        String number=findPhone(pendingContactName);
        if(number==null) { speak("لم أجد هذا الاسم في جهات الاتصال. تأكد من الاسم أو أضف الرقم إلى الهاتف."); return; }
        try {
            if(pendingSms) {
                Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number)));
                i.putExtra("sms_body",pendingSmsBody); startActivity(i);
                speak("فتحت الرسائل. اكتب رسالتك وراجعها ثم اضغط إرسال.");
            } else {
                if(checkSelfPermission("android.permission.CALL_PHONE")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.CALL_PHONE"},CALL_PERMISSION); return;
                }
                startActivity(new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(number))));
            }
        } catch(Exception e) { toast("تعذر تنفيذ الطلب. تحقق من صلاحيات الهاتف."); }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==CONTACT_PERMISSION) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) performContactAction();
            else toast("لم يتم منح إذن جهات الاتصال.");
        } else if(requestCode==CALL_PERMISSION) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) performContactAction();
            else toast("لم يتم منح إذن الاتصال. يمكنك تفعيله من إعدادات الهاتف.");
        }
    }
    private void startVoiceInput() {\n        if (!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("assistant_enabled",true)) { toast("الأوامر الصوتية متوقفة من الإعدادات"); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission("android.permission.RECORD_AUDIO") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 72);
            toast("اسمح باستخدام الميكروفون، ثم اضغط زر التحدث مرة أخرى");
            return;
        }
        if (!getPackageManager().hasSystemFeature("android.hardware.microphone")) { toast("الميكروفون غير متاح على هذا الهاتف"); return; }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-LY");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "قولي أمرك...");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (Exception e) { speak("خدمة التعرف على الصوت غير متوفرة. تأكد من وجود تطبيق مساعد صوتي."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) handleVoiceCommand(results.get(0));
        }
    }

    private void speak(String message) {
        toast(message);
        if (tts != null && getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("voice_enabled",true)) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "hayati_voice");
    }

    private String cleanVoice(String value) {
        return value == null ? "" : value.trim().replace("،", " ").replace(".", "").replace("؟", "");
    }

    private void handleVoiceCommand(String raw) {
        String command = cleanVoice(raw);
        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.startsWith("اتصل") || lower.startsWith("كلم")) {\n            if (!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("calls_enabled",true)) { speak("ميزة الاتصال متوقفة من الإعدادات"); return; }
            String person=command.replaceFirst("^(اتصل|كلم)\\s*(ب|على|مع)?\\s*","").trim();
            if(person.isEmpty()) speak("قولي اسم الشخص، مثلاً اتصل بعبد الباري");
            else requestContactAction(person,false);
            return;
        }
        if (lower.startsWith("ابعث رسالة") || lower.startsWith("أرسل رسالة") || lower.startsWith("ارسل رسالة")) {\n            if (!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("sms_enabled",true)) { speak("ميزة الرسائل متوقفة من الإعدادات"); return; }
            String rest=command.replaceFirst("^(ابعث|أرسل|ارسل)\\s*رسالة\\s*(إلى|الى|ل)?\\s*","").trim();
            int split=rest.indexOf(" نصها ");
            if(split<1) split=rest.indexOf(" نص ");
            if(split<1) speak("قولي: ابعث رسالة إلى عبد الباري نصها السلام عليكم");
            else {
                String person=rest.substring(0,split).trim();
                String body=rest.substring(split).replaceFirst("^\\s*نصها?\\s*","").trim();
                pendingContactName=person; pendingSms=true; pendingSmsBody=body;
                if(checkSelfPermission("android.permission.READ_CONTACTS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{"android.permission.READ_CONTACTS"},CONTACT_PERMISSION);
                else performContactAction();
            }
            return;
        }
        if (lower.startsWith("افتح ") || lower.startsWith("اعرض ") || lower.startsWith("وريني ")) {
            if (lower.contains("مهم")) { showTab("tasks"); speak("هذه قائمة المهام"); return; }
            if (lower.contains("ملاحظ")) { showTab("notes"); speak("هذه ملاحظاتك"); return; }
            if (lower.contains("تذكير") || lower.contains("موعد")) { showTab("reminders"); speak("هذه تذكيراتك"); return; }
            if (lower.contains("اليوم")) { showTab("today"); speak("هذا ملخص يومك"); return; }
        }
        if (lower.contains("كم") && (lower.contains("مهمة") || lower.contains("مهام"))) {
            int done = 0; for (int i=0;i<tasks.length();i++) if (tasks.optJSONObject(i)!=null && tasks.optJSONObject(i).optBoolean("done")) done++;
            speak("عندك " + tasks.length() + " مهام، أنجزت منها " + done); return;
        }
        if (lower.startsWith("احذف") && lower.contains("آخر") && lower.contains("مهم")) {
            if (tasks.length()>0) { tasks.remove(tasks.length()-1); saveData(); showTab("tasks"); speak("حذفت آخر مهمة"); }
            else speak("ما عندكش مهام للحذف");
            return;
        }
        if (lower.startsWith("احذف") && lower.contains("آخر") && lower.contains("ملاحظ")) {
            if (notes.length()>0) { notes.remove(notes.length()-1); saveData(); showTab("notes"); speak("حذفت آخر ملاحظة"); }
            else speak("ما عندكش ملاحظات للحذف");
            return;
        }
        if (lower.startsWith("أضف مهمة") || lower.startsWith("ضيف مهمة") || lower.startsWith("سجل مهمة") || lower.startsWith("مهمة جديدة")) {
            String title = command.replaceFirst("^(أضف|ضيف|سجل)\\s*مهمة( جديدة)?\\s*", "").replaceFirst("^مهمة جديدة\\s*", "").trim();
            if (title.isEmpty()) { speak("قولي اسم المهمة بعد كلمة أضف مهمة"); return; }
            JSONObject o = new JSONObject(); put(o,"title",title); put(o,"done",false); tasks.put(o); saveData(); showTab("tasks"); speak("تمت إضافة المهمة: " + title); return;
        }
        if (lower.startsWith("أضف ملاحظة") || lower.startsWith("ضيف ملاحظة") || lower.startsWith("سجل ملاحظة")) {
            String body = command.replaceFirst("^(أضف|ضيف|سجل)\\s*ملاحظة\\s*", "").trim();
            if (body.isEmpty()) { speak("قولي نص الملاحظة بعد كلمة أضف ملاحظة"); return; }
            JSONObject o = new JSONObject(); put(o,"title","ملاحظة صوتية"); put(o,"body",body); notes.put(o); saveData(); showTab("notes"); speak("حفظت الملاحظة"); return;
        }
        if (lower.startsWith("ذكرني") || lower.startsWith("ذكّرني") || lower.startsWith("أضف تذكير") || lower.startsWith("ضيف تذكير")) {
            Matcher time = Pattern.compile("(?:الساعة\\s*)?(\\d{1,2})(?::(\\d{1,2}))?").matcher(command);
            if (!time.find()) { speak("قولي وقت التذكير، مثلاً ذكرني غدوة الساعة تسعة بالاتصال"); return; }
            int hour = Integer.parseInt(time.group(1)); int minute = time.group(2)==null?0:Integer.parseInt(time.group(2));
            if (hour>23 || minute>59) { speak("الوقت غير صحيح"); return; }
            Calendar when = Calendar.getInstance();
            if (lower.contains("غدوة") || lower.contains("بكرة") || lower.contains("غدا")) when.add(Calendar.DAY_OF_YEAR,1);
            when.set(Calendar.HOUR_OF_DAY,hour); when.set(Calendar.MINUTE,minute); when.set(Calendar.SECOND,0);
            if (when.getTimeInMillis()<=System.currentTimeMillis() && !(lower.contains("غدوة")||lower.contains("بكرة")||lower.contains("غدا"))) when.add(Calendar.DAY_OF_YEAR,1);
            String title = command.substring(0,time.start()).replaceAll("^(ذكرني|ذكّرني|أضف تذكير|ضيف تذكير)\\s*", "").trim();
            title = title.replaceAll("^(غدوة|بكرة|غدا)\\s*", "").trim();
            String tail = command.substring(time.end()).replaceFirst("^\\s*(بال|ب|في)\\s*", "").trim();
            if (!tail.isEmpty()) title = tail;
            if (title.isEmpty()) title = "تذكير";
            scheduleReminder(title,when); speak("تم ضبط التذكير " + title); return;
        }
        speak("ما فهمتش الأمر. جرب: أضف مهمة، أضف ملاحظة، اعرض المهام، أو ذكرني غدوة الساعة تسعة");
    }

    private void showTab(String tab) {
        currentTab=tab; content.removeAllViews();
        if ("today".equals(tab)) showToday();
        else if ("tasks".equals(tab)) showTasks();
        else if ("reminders".equals(tab)) showReminders();
        else if ("settings".equals(tab)) showSettings();
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
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(null, android.graphics.Typeface.BOLD); b.setBackground(roundDrawable(teal,16));
        b.setOnClickListener(v->action.run()); return b;
    }
    private GradientDrawable roundDrawable(int color, int radiusDp) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radiusDp*getResources().getDisplayMetrics().density); return d; }
    private void card(View v) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(12,8,12,8);
        box.setBackground(roundDrawable(Color.WHITE,18)); box.setElevation(3f); box.addView(v); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,7,0,7); content.addView(box,lp);
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
            cb.setOnCheckedChangeListener((v,checked)->{put(o,"done",checked);saveData();});
            line.addView(cb,new LinearLayout.LayoutParams(0,-2,1));
            Button del=new Button(this); del.setText("حذف"); del.setTextSize(11); del.setOnClickListener(v->{tasks.remove(index);saveData();showTab("tasks");});
            line.addView(del); card(line);
        }
        if(tasks.length()==0) card(text("قائمة المهام فارغة حالياً.",15,Color.GRAY));
    }
    private void addTask() { inputDialog("مهمة جديدة","اكتب المهمة",value->{JSONObject o=new JSONObject();put(o,"title",value);put(o,"done",false);tasks.put(o);saveData();showTab(currentTab);}); }
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
            JSONObject o=new JSONObject();put(o,"title",t.isEmpty()?"ملاحظة":t);put(o,"body",b);notes.put(o);saveData();showTab("notes");
        }).show();
    }
    private void editNote(int index) {
        JSONObject old=notes.optJSONObject(index); if(old==null)return;
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
        EditText title=new EditText(this);title.setText(old.optString("title"));fields.addView(title);
        EditText body=new EditText(this);body.setText(old.optString("body"));body.setMinLines(3);body.setGravity(Gravity.TOP);fields.addView(body);
        new AlertDialog.Builder(this).setTitle("تعديل الملاحظة").setView(fields).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{
            put(old,"title",title.getText().toString());put(old,"body",body.getText().toString());saveData();showTab("notes");
        }).show();
    }
    private interface TextAction { void accept(String value); }
    private void inputDialog(String title,String hint, TextAction done) {
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
        JSONObject o=new JSONObject();put(o,"id",id);put(o,"title",title);put(o,"when",label);reminders.put(o);saveData();showTab("reminders");toast("تم حفظ التذكير");
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
    @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
}
