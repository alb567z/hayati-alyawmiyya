package com.hayati.alyawmiyya;

import android.app.*;
import android.media.MediaRecorder;
import java.io.File;
import android.content.*;
import android.net.Uri;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
    private String pendingCallNumber = "";
    private boolean awaitingCallConfirmation = false;
    private MediaRecorder enrollmentRecorder;
    private AlertDialog enrollmentDialog;
    private TextView enrollmentStatus;
    private Button enrollmentButton;
    private int enrollmentSample = 1;
    private boolean enrollmentRecording = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(0, 105, 92));
        getWindow().setNavigationBarColor(Color.rgb(250, 250, 250));
        loadData();
        tts = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("ar", "LY")); applyVoiceSettings(tts); } });
        createNotificationChannel();
        requestNotificationPermission();
        buildShell();
        showTab("today");
        checkForUpdates();
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
        TextView brand = new TextView(this); brand.setText("صاحبي  ♥"); brand.setTextColor(Color.WHITE); brand.setTextSize(26); brand.setTypeface(null, android.graphics.Typeface.BOLD); brand.setGravity(Gravity.RIGHT);
        TextView sub = new TextView(this); sub.setText("مساحتك لتنظيم يومك بكل سهولة"); sub.setTextColor(Color.rgb(220,245,240)); sub.setTextSize(14); sub.setGravity(Gravity.RIGHT);
        header.addView(brand); header.addView(sub); Button voice = new Button(this); voice.setText("🎤 تحدث إلى صاحبي"); voice.setAllCaps(false); voice.setTextSize(15); voice.setTextColor(Color.rgb(0,105,92)); voice.setBackground(roundDrawable(Color.WHITE,18)); voice.setOnClickListener(v -> startVoiceInput()); LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(-1, -2); voiceLp.topMargin = 12; header.addView(voice, voiceLp); root.addView(header);
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
        heading("الإعدادات", "تحكم في صلاحيات وميزات صاحبي");
        android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        Switch voice=new Switch(this); voice.setText("الردود الصوتية"); voice.setTextSize(17); voice.setChecked(p.getBoolean("voice_enabled",true));
        voice.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("voice_enabled",on).apply()); card(voice);

        card(text("نبرة الصوت",17,ink));
        RadioGroup pitchGroup = new RadioGroup(this);
        pitchGroup.setOrientation(RadioGroup.VERTICAL);
        String[] pitchLabels = {"حاد","متوسط","غليظ"};
        float[] pitchValues = {1.15f,1.0f,0.85f};
        int selectedPitch = Math.round(p.getFloat("voice_pitch",1.0f)*100);
        int selectedId = 2;
        for (int i=0;i<pitchLabels.length;i++) {
            RadioButton option = new RadioButton(this);
            option.setId(i+1);
            option.setText(pitchLabels[i]);
            option.setTextSize(16);
            pitchGroup.addView(option);
            if (Math.abs(selectedPitch-Math.round(pitchValues[i]*100))<2) selectedId=i+1;
        }
        pitchGroup.check(selectedId);
        pitchGroup.setOnCheckedChangeListener((group,checkedId)->{
            if(checkedId>=1 && checkedId<=3) {
                float chosen = pitchValues[checkedId-1];
                p.edit().putFloat("voice_pitch",chosen).apply();
                applyVoiceSettings(tts);
            }
        });
        card(pitchGroup);
        content.addView(button("👨 اختيار الصوت الرجالي",()->chooseArabicVoice()));
        content.addView(button("🔊 تجربة الصوت",()->speak("نعم يا صاحبي، هذا اختبار للصوت.")));
        card(text("اخترنا النبرة المتوسطة افتراضياً. درجة الصوت الفعلية تعتمد أيضاً على الأصوات العربية المثبتة في الهاتف؛ تغيير النبرة لا يضمن تحويل صوت المحرك إلى صوت رجالي إذا لم يتوفر صوت رجالي.",13,Color.GRAY));
        Switch assistant=new Switch(this); assistant.setText("تشغيل الأوامر الصوتية بالميكروفون"); assistant.setTextSize(17); assistant.setChecked(p.getBoolean("assistant_enabled",true));
        assistant.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("assistant_enabled",on).apply()); card(assistant);
        Switch wake=new Switch(this);
        wake.setText("الاستماع في الخلفية لعبارة: يا صاحبي");
        wake.setTextSize(17);
        wake.setChecked(p.getBoolean("wake_listening_enabled",false));
        wake.setOnCheckedChangeListener((v,on)->{
            if(on && checkSelfPermission("android.permission.RECORD_AUDIO")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.RECORD_AUDIO"},705);
                wake.setChecked(false);
                return;
            }
            p.edit().putBoolean("wake_listening_enabled",on).apply();
            if(on) { startWakeWordService(); toast("بدأ الاستماع وسيظهر إشعار دائم."); }
            else { stopWakeWordService(); toast("تم إيقاف الاستماع."); }
        });
        card(wake);
        card(text("قل يا صاحبي، وبعد الرد نعم يا صاحبي اسأله عن الساعة أو الإشعارات. يعتمد التعرف على خدمة الصوت في الهاتف وقد يحتاج إنترنت، وقد لا يستمر على كل الأجهزة عند قفل الشاشة.",14,Color.GRAY));
        Switch calls=new Switch(this); calls.setText("السماح بميزة الاتصال"); calls.setTextSize(17); calls.setChecked(p.getBoolean("calls_enabled",true));
        calls.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("calls_enabled",on).apply()); card(calls);
        Switch sms=new Switch(this); sms.setText("السماح بميزة الرسائل"); sms.setTextSize(17); sms.setChecked(p.getBoolean("sms_enabled",true));
        sms.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("sms_enabled",on).apply()); card(sms);
        Switch notifications=new Switch(this); notifications.setText("تفعيل تنبيهات التطبيق"); notifications.setTextSize(17); notifications.setChecked(p.getBoolean("notifications_enabled",true));
        notifications.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("notifications_enabled",on).apply()); card(notifications);
        content.addView(button("🎙️ إذن الميكروفون",()->openPermissionSettings("android.permission.RECORD_AUDIO")));
        content.addView(button("📇 إذن جهات الاتصال",()->openPermissionSettings("android.permission.READ_CONTACTS")));
        content.addView(button("📞 إذن إجراء المكالمات",()->openPermissionSettings("android.permission.CALL_PHONE")));
        content.addView(button("🔔 السماح لصاحبي بمعرفة الإشعارات",()->{
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch(Exception e) { toast("افتح إعدادات الوصول إلى الإشعارات من إعدادات الهاتف."); }
        }));
        Switch motion=new Switch(this); motion.setText("استشعار حركة الهاتف أثناء فتح التطبيق"); motion.setTextSize(16); motion.setChecked(p.getBoolean("motion_enabled",false));
        motion.setOnCheckedChangeListener((v,on)->p.edit().putBoolean("motion_enabled",on).apply()); card(motion);
        content.addView(button("🎙️ إعداد إذن الميكروفون",()->openAppSettings()));
        content.addView(button("🔔 إعدادات الإشعارات",()->{ Intent i=new Intent(); if(Build.VERSION.SDK_INT>=26){i.setAction("android.settings.APP_NOTIFICATION_SETTINGS");i.putExtra("android.provider.extra.APP_PACKAGE",getPackageName());}else{i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");i.setData(Uri.parse("package:"+getPackageName()));} startActivity(i); }));
        content.addView(button("🔐 الخصوصية والصلاحيات",()->new AlertDialog.Builder(this).setTitle("الخصوصية").setMessage("المهام والملاحظات والتذكيرات محفوظة محلياً على هاتفك. جهات الاتصال لا تُستخدم إلا عند طلب الاتصال أو الرسالة. يمكنك سحب أي إذن من إعدادات الهاتف في أي وقت.").setPositiveButton("حسناً",null).show()));
        content.addView(button("⚙️ إعدادات التطبيق في الهاتف",()->openAppSettings()));
        card(text("الاتصال: عند قول «اتصل بـ + اسم الشخص» سيبحث التطبيق في جهات الاتصال، ثم يبدأ الاتصال بعد منح الإذن. الرسائل تفتح شاشة الرسائل لتراجعها وترسلها بنفسك.",14,Color.GRAY));
        card(text("استشعار الحركة يعمل كخيار داخل التطبيق فقط حالياً. تشغيل التطبيق تلقائياً وهو مغلق بسبب الحركة يحتاج خدمة خلفية دائمة وقد يستهلك البطارية، لذلك لم أفعّله دون موافقتك.",14,Color.GRAY));
    }
    private void startWakeWordService() {
        Intent i=new Intent(this,WakeWordService.class);
        i.setAction(WakeWordService.ACTION_START);
        try { if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); }
        catch(Exception e) { toast("تعذر تشغيل الاستماع. افتح التطبيق وحاول مجدداً."); }
    }
    private void stopWakeWordService() {
        Intent i=new Intent(this,WakeWordService.class);
        i.setAction(WakeWordService.ACTION_STOP);
        try { startService(i); } catch(Exception ignored) {}
    }
    private void openPermissionSettings(String permission) {
        openAppSettings();
        toast("من صفحة أذونات التطبيق اختَر الإذن المطلوب ثم اسمح أو ارفض");
    }
    private void openAppSettings() {
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
        if(number==null) { speak("ما لقيتش الاسم هذا في الأسماء. تأكد من الاسم أو سجّل رقمه في تلفونك."); return; }
        try {
            if(pendingSms) {
                Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number)));
                i.putExtra("sms_body",pendingSmsBody); startActivity(i);
                speak("فتحتلك الرسائل. راجعها، وبعدها اضغط إرسال.");
            } else {
                pendingCallNumber = number;
                awaitingCallConfirmation = true;
                speak("هل تريد الاتصال بـ "+pendingContactName+"؟ للتأكيد قل إي، أو تمام، أو اتصل. وللإلغاء قل لا.");
                // Open speech recognition after the spoken confirmation prompt finishes.
                new Handler(getMainLooper()).postDelayed(() -> {
                    if (awaitingCallConfirmation && !isFinishing()) startVoiceInput();
                }, 2300);
            }
        } catch(Exception e) { toast("تعذر تنفيذ الطلب. تحقق من صلاحيات الهاتف."); }
    }
    private void initiateCall(String number) {
        pendingCallNumber=number;
        if(checkSelfPermission("android.permission.CALL_PHONE")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.CALL_PHONE"},CALL_PERMISSION);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(number))));
        } catch(Exception e) {
            toast("تعذر إجراء المكالمة. تحقق من صلاحيات الهاتف.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==CONTACT_PERMISSION) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) performContactAction();
            else toast("لم يتم منح إذن جهات الاتصال.");
        } else if(requestCode==CALL_PERMISSION) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) initiateCall(pendingCallNumber);
            else { pendingCallNumber=""; toast("لم يتم منح إذن الاتصال. يمكنك تفعيله من إعدادات الهاتف."); }
        } else if(requestCode==705) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("wake_listening_enabled",true).apply();
                startWakeWordService();
                toast("تم السماح بالميكروفون وتشغيل الاستماع.");
            } else {
                toast("يلزم السماح بالميكروفون لتشغيل الاستماع.");
            }
            showTab("settings");
        } else if(requestCode==72) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                toast("تم تفعيل الميكروفون. استمع الآن إلى أمرك.");
                startVoiceInput();
            } else {
                toast("لم تسمح باستخدام الميكروفون. فعّل الإذن من إعدادات التطبيق.");
            }
        }
    }
    // First-use voice sample setup. Audio remains in this app's private files directory.
    // These samples are NOT speaker authentication; the current speech recognizer
    // still accepts anyone who can speak into the microphone.
    private void showVoiceEnrollment() {
        enrollmentSample = 1;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 12, 24, 8);
        TextView instruction = text("اقرأ العبارة التالية بصوتك في مكان هادئ. سنحفظ ثلاث عينات داخل مساحة التطبيق الخاصة على هاتفك.\n\n«أنا صاحب الهاتف، وهذا صوتي. يا صاحبي، اسمعني وتعرّف على صوتي، واستجب لأوامري أنا».", 17, ink);
        panel.addView(instruction);
        enrollmentStatus = text("العينة 1 من 3: اضغط ابدأ التسجيل، ثم اقرأ العبارة واضغط إيقاف وحفظ.", 14, teal);
        panel.addView(enrollmentStatus);
        enrollmentButton = new Button(this);
        enrollmentButton.setAllCaps(false);
        enrollmentButton.setText("ابدأ التسجيل 1/3");
        panel.addView(enrollmentButton);
        enrollmentDialog = new AlertDialog.Builder(this)            .setTitle("التعرّف الأولي على صوتك")
            .setView(panel)
            .setNegativeButton("لاحقاً", (d, w) -> cancelEnrollment())
            .setOnCancelListener(d -> cancelEnrollment())
            .create();
        enrollmentButton.setOnClickListener(v -> {
            if (!enrollmentRecording) beginEnrollmentRecording();
            else saveEnrollmentRecording();
        });
        enrollmentDialog.show();
    }

    private File enrollmentDirectory() {
        File dir = new File(getFilesDir(), "voice_samples");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void beginEnrollmentRecording() {
        try {
            File output = new File(enrollmentDirectory(), "voice_sample_" + enrollmentSample + ".m4a");
            if (output.exists()) output.delete();
            if (Build.VERSION.SDK_INT >= 31) enrollmentRecorder = new MediaRecorder(this);
            else enrollmentRecorder = new MediaRecorder();
            enrollmentRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            enrollmentRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            enrollmentRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            enrollmentRecorder.setAudioEncodingBitRate(96000);
            enrollmentRecorder.setAudioSamplingRate(44100);
            enrollmentRecorder.setOutputFile(output.getAbsolutePath());
            enrollmentRecorder.prepare();
            enrollmentRecorder.start();
            enrollmentRecording = true;
            enrollmentButton.setText("إيقاف وحفظ العينة " + enrollmentSample);
            enrollmentStatus.setText("جاري التسجيل… اقرأ العبارة كاملة ثم اضغط إيقاف وحفظ.");
        } catch (Exception e) {
            releaseEnrollmentRecorder();
            enrollmentRecording = false;
            toast("تعذر بدء التسجيل. تأكد من إذن الميكروفون ثم حاول مرة أخرى.");
        }
    }

    private void saveEnrollmentRecording() {
        File saved = new File(enrollmentDirectory(), "voice_sample_" + enrollmentSample + ".m4a");
        try {
            if (enrollmentRecorder != null) enrollmentRecorder.stop();
            if (saved.length() < 1024) {
                saved.delete();
                toast("التسجيل قصير جداً. أعد العينة من فضلك.");
                enrollmentButton.setText("أعد تسجيل العينة " + enrollmentSample);
                enrollmentStatus.setText("لم تُحفظ العينة. اضغط لإعادة التسجيل.");
                return;
            }
        } catch (Exception e) {
            saved.delete();
            toast("لم يكتمل التسجيل. أعد المحاولة.");
            releaseEnrollmentRecorder();
            enrollmentRecording = false;
            enrollmentButton.setText("أعد تسجيل العينة " + enrollmentSample);
            return;
        } finally {
            releaseEnrollmentRecorder();
            enrollmentRecording = false;
        }
        if (enrollmentSample < 3) {
            enrollmentSample++;
            enrollmentButton.setText("ابدأ التسجيل " + enrollmentSample + "/3");
            enrollmentStatus.setText("تم حفظ العينة. خذ نفساً، ثم اقرأ العبارة مرة أخرى.");
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("voice_setup_completed", true)
                .putInt("voice_sample_count", 3)
                .apply();
            if (enrollmentDialog != null) enrollmentDialog.dismiss();
            toast("تم حفظ عينات صوتك على الهاتف. هذه تهيئة أولية وليست تحققاً بيومترياً من الهوية.");
            startVoiceInput();
        }
    }

    private void releaseEnrollmentRecorder() {
        if (enrollmentRecorder != null) {
            try { enrollmentRecorder.reset(); } catch (Exception ignored) {}
            try { enrollmentRecorder.release(); } catch (Exception ignored) {}
            enrollmentRecorder = null;
        }
    }

    private void cancelEnrollment() {
        if (enrollmentRecording) {
            try { if (enrollmentRecorder != null) enrollmentRecorder.stop(); } catch (Exception ignored) {}
        }
        releaseEnrollmentRecorder();
        enrollmentRecording = false;
    }

    private void startVoiceInput() {
        if (!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("assistant_enabled",true)) { toast("الأوامر الصوتية متوقفة من الإعدادات"); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission("android.permission.RECORD_AUDIO") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 72);
            toast("اسمح باستخدام الميكروفون، ثم اضغط زر التحدث مرة أخرى");
            return;
        }
        if (!getPackageManager().hasSystemFeature("android.hardware.microphone")) { toast("الميكروفون غير متاح على هذا الهاتف"); return; }
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("voice_setup_completed", false)) {
            showVoiceEnrollment();
            return;
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "قولي أمرك...");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (Exception e) { speak("خدمة الصوت مش متوفرة. تأكد إن خدمة الصوت موجودة في تلفونك."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    String recognized = results.get(0);
                    if (awaitingCallConfirmation) handleCallConfirmationVoice(recognized);
                    else handleVoiceCommand(recognized);
                } else if (awaitingCallConfirmation) {
                    awaitingCallConfirmation = false;
                    pendingCallNumber = "";
                    speak("ما سمعتش التأكيد، ما درتش الاتصال.");
                } else speak("ما سمعتكش كويس، عاود قولّي.");
            } else if (resultCode == RESULT_CANCELED) {
                toast("لم يتم تسجيل أمر. اضغط الميكروفون وتحدث بعد سماع النغمة.");
            }
        }
    }

    private void chooseArabicVoice() {
        if (tts == null || tts.getVoices() == null) {
            toast("محرك الصوت ما زال يجهز. حاول بعد لحظة.");
            return;
        }
        ArrayList<android.speech.tts.Voice> voices = new ArrayList<>();
        for (android.speech.tts.Voice voice : tts.getVoices()) {
            if (voice != null && voice.getLocale() != null
                && "ar".equalsIgnoreCase(voice.getLocale().getLanguage())
                && !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                voices.add(voice);
            }
        }
        if (voices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("لا يوجد صوت عربي إضافي")
                .setMessage("الهاتف لا يعرض أصواتاً عربية متاحة للاختيار. افتح إعدادات تحويل النص إلى كلام وثبّت صوتاً عربياً رجاليّاً إن كان متوفراً، ثم ارجع للتطبيق.")
                .setPositiveButton("إعدادات الصوت",(d,w)->{
                    try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
                    catch(Exception e) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                }).setNegativeButton("إغلاق",null).show();
            return;
        }
        Collections.sort(voices, (a,b)->a.getName().compareToIgnoreCase(b.getName()));
        String[] names = new String[voices.size()];
        String selected = getSharedPreferences(PREFS,MODE_PRIVATE).getString("voice_name","");
        int checked = -1;
        for (int i=0;i<voices.size();i++) {
            names[i] = voices.get(i).getName() + " (" + voices.get(i).getLocale().toLanguageTag() + ")";
            if (voices.get(i).getName().equals(selected)) checked=i;
        }
        new AlertDialog.Builder(this).setTitle("اختبر الأصوات العربية واختر صوتاً رجاليّاً")
            .setSingleChoiceItems(names,checked,(dialog,which)->{
                android.speech.tts.Voice chosen=voices.get(which);
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("voice_name",chosen.getName()).apply();
                applyVoiceSettings(tts);
                tts.speak("نعم يا صاحبي، هذا صوتي الجديد.",TextToSpeech.QUEUE_FLUSH,null,"voice-choice");
                dialog.dismiss();
                toast("تم اختيار الصوت. جرّبه وتأكد أنه رجالي.");
            }).setNegativeButton("إغلاق",null).show();
    }

    private void applyVoiceSettings(TextToSpeech engine) {
        if (engine == null) return;
        android.content.SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        String voiceName=prefs.getString("voice_name","");
        if (!voiceName.isEmpty() && engine.getVoices()!=null) {
            for (android.speech.tts.Voice voice : engine.getVoices()) {
                if (voice!=null && voiceName.equals(voice.getName())) {
                    try { engine.setVoice(voice); } catch(Exception ignored) {}
                    break;
                }
            }
        }
        float pitch = prefs.getFloat("voice_pitch",1.0f);
        engine.setPitch(pitch);
        engine.setSpeechRate(1.0f);
    }

    private void speak(String message) {
        toast(message);
        if (tts != null && getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("voice_enabled",true)) {
            applyVoiceSettings(tts);
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "hayati_voice");
        }
    }

    private String cleanVoice(String value) {
        if (value == null) return "";
        return value.trim().replace("،", " ").replace(".", "").replace("؟", "")
            .replace("ـ", "").replaceAll("[\\u064B-\\u065F\\u0670]", "");
    }

    private String normalizeVoiceText(String value) {
        if (value == null) return "";
        String s = cleanVoice(value).toLowerCase(Locale.ROOT)
            .replace("أ","ا").replace("إ","ا").replace("آ","ا")
            .replace("ى","ي").replace("ة","ه");
        s = s.replaceAll("[؟?!,.،؛:]", " ").replaceAll("\\s+", " ").trim();
        String[][] words = {
            {"تسعه","9"},{"تسعة","9"},{"تسع","9"},{"ثمانيه","8"},{"ثمانية","8"},
            {"ثمان","8"},{"سبعه","7"},{"سبعة","7"},{"سته","6"},{"ستة","6"},
            {"خمسه","5"},{"خمسة","5"},{"اربعه","4"},{"أربعة","4"},{"ثلاثه","3"},
            {"ثلاثة","3"},{"اثنين","2"},{"اتنين","2"},{"واحد","1"},{"عشره","10"},
            {"عشرة","10"},{"حداش","11"},{"احدعش","11"},{"اثناش","12"},{"اثنعش","12"}
        };
        for (String[] pair : words) s = s.replaceAll("(?<!\\S)" + pair[0] + "(?!\\S)", pair[1]);
        return s.replaceAll("\\s+", " ").trim();
    }

    private int findVoiceItem(JSONArray array, String query, String... fields) {
        String wanted = normalizeVoiceText(query);
        if (wanted.isEmpty() || wanted.equals("اخر") || wanted.equals("الاخير") || wanted.equals("الاخيره")) return array.length() - 1;
        for (int i = array.length() - 1; i >= 0; i--) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            for (String field : fields) {
                String value = normalizeVoiceText(item.optString(field, ""));
                if (!value.isEmpty() && (value.contains(wanted) || wanted.contains(value))) return i;
            }
        }
        return -1;
    }

    private boolean handleVoiceDelete(String lower) {
        if (!lower.matches(".*(?:احذف|امسح|الغ|الغي|الغاء|كنسل|بطل).*")) return false;
        boolean task = lower.contains("مهمه") || lower.contains("مهام");
        boolean note = lower.contains("ملاحظه") || lower.contains("ملاحظات");
        boolean reminder = lower.contains("تذكير") || lower.contains("موعد");
        if (!task && !note && !reminder) return false;

        String kind = task ? "مهمه" : note ? "ملاحظه" : "تذكير";
        String query = lower.replaceAll(".*?(?:احذف|امسح|الغ|الغي|الغاء|كنسل|بطل)\\s*", "");
        query = query.replaceFirst("^(?:لي\\s*)?(?:اخر|الاخير|الاخيره|كل|هذا|هذي)?\\s*", "");
        query = query.replaceFirst("^(?:ال)?" + kind + "(?:\\s*رقم\\s*\\d+)?\\s*", "").trim();
        query = query.replaceFirst("^(?:اللي\\s*)", "").trim();

        if (task) {
            int index = findVoiceItem(tasks, query, "title");
            if (index >= 0) { tasks.remove(index); saveData(); showTab("tasks"); speak("تمام، حذفت المهمة."); }
            else speak("ما لقيتش المهمة المطلوبة.");
        } else if (note) {
            int index = findVoiceItem(notes, query, "title", "body");
            if (index >= 0) { notes.remove(index); saveData(); showTab("notes"); speak("تمام، حذفت الملاحظة."); }
            else speak("ما لقيتش الملاحظة المطلوبة.");
        } else {
            int index = findVoiceItem(reminders, query, "title");
            if (index >= 0) {
                JSONObject item = reminders.optJSONObject(index);
                if (item != null) cancelReminder(item.optInt("id"));
                reminders.remove(index); saveData(); showTab("reminders"); speak("تمام، ألغيت التذكير.");
            } else speak("ما لقيتش التذكير المطلوب.");
        }
        return true;
    }

    private void handleCallConfirmationVoice(String raw) {
        String answer = normalizeVoiceText(cleanVoice(raw));
        boolean yes = answer.matches(".*(?:^|\\s)(?:نعم|اي|ايوه|أيوه|ايوا|تمام|اتصل)(?:\\s|$).*");
        boolean no = answer.matches(".*(?:^|\\s)(?:لا|الغاء|الغي|إلغاء)(?:\\s|$).*");
        if (yes && !pendingCallNumber.isEmpty()) {
            String number = pendingCallNumber;
            awaitingCallConfirmation = false;
            pendingCallNumber = "";
            speak("تمام، جاري الاتصال.");
            initiateCall(number);
        } else if (no) {
            awaitingCallConfirmation = false;
            pendingCallNumber = "";
            speak("تمام، ألغيت الاتصال.");
        } else {
            awaitingCallConfirmation = false;
            pendingCallNumber = "";
            speak("ما وصلنيش تأكيد واضح، ألغيت الاتصال حفاظاً على أمانك.");
        }
    }

    private void handleVoiceCommand(String raw) {
        String command = cleanVoice(raw);
        String normalized = normalizeVoiceText(command);
        // A wake phrase on its own is a greeting, not an unknown command.
        if (normalized.matches("^(?:يا\\s+)?صاحبي$") || normalized.equals("يا صاح")) {
            speak("نعم يا صاحبي");
            return;
        }
        String lower = normalized.replaceFirst("^(?:يا\\s+)?صاحبي\\s*", "").trim();
        if (lower.isEmpty()) { speak("ما فهمتكش، عاود قولّي."); return; }

        String mediaAction = null;
        if (lower.matches(".*(?:ارفع|علي|زيد).*صوت.*")) mediaAction = "volume_up";
        else if (lower.matches(".*(?:وطي|نقص|خفض).*صوت.*")) mediaAction = "volume_down";
        else if (lower.matches(".*(?:التالي|الاغنيه الجايه|الفيديو الجاي|المقطع التالي).*")) mediaAction = "next";
        else if (lower.matches(".*(?:السابق|اللي قبل|الاغنيه السابقه|الفيديو السابق).*")) mediaAction = "previous";
        else if (lower.matches(".*(?:وقف|اوقف|ايقاف|حبس|توقف).*")) mediaAction = "play_pause";
        else if (lower.matches(".*(?:كمل|استمر|شغل الموسيقى|شغل الفيديو|شغل الفيلم|واصل).*")) mediaAction = "play_pause";
        if (mediaAction != null) {
            if (!NotificationReaderService.hasAccess(this)) {
                speak("باش نتحكم في الفيديو والموسيقى، فعّل إذن الإشعارات لصاحبي من الإعدادات.");
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); } catch(Exception ignored) {}
            } else if (NotificationReaderService.controlMedia(this, mediaAction)) {
                if ("volume_up".equals(mediaAction)) speak("عليت الصوت.");
                else if ("volume_down".equals(mediaAction)) speak("وطيّت الصوت.");
                else if ("next".equals(mediaAction)) speak("التالي.");
                else if ("previous".equals(mediaAction)) speak("السابق.");
                else speak("حاضر.");
            } else speak("ما لقيتش مشغل وسائط نشط أو التطبيق ما يدعمش الأمر.");
            return;
        }

        if (lower.contains("كم الساعه") || lower.equals("الساعه") || lower.contains("الوقت توا") || lower.contains("الوقت الان")) {
            String currentTime = new SimpleDateFormat("HH:mm", new Locale("ar", "LY")).format(new Date());
            speak("الساعة الآن " + currentTime);
            return;
        }

        if (lower.contains("اشعار") || lower.contains("اشعارات")) {
            if (!NotificationReaderService.hasAccess(this)) {
                speak("باش نقدر نعرف إشعاراتك، فعّل إذن الوصول للإشعارات من الإعدادات.");
                try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); } catch(Exception ignored) {}
                return;
            }
            int activeCount = NotificationReaderService.getActiveCount();
            if (activeCount < 0) speak("صلاحية الإشعارات مفعلة، لكن الخدمة مش جاهزة توا. عاود بعد شوية.");
            else if (activeCount == 0) speak("لا، ما عندكش إشعارات حالياً.");
            else speak("إيه، عندك " + activeCount + " إشعارات حالياً.");
            return;
        }

        // الرد على التحية بدلاً من اعتبارها أمراً غير معروف
        if (lower.matches(".*(?:السلام عليكم|سلام عليكم|وعليكم السلام|مرحبا|اهلا|أهلا|هلا).*")) {
            speak("وعليكم السلام ورحمة الله، أهلاً بيك! كيف نقدر نساعدك؟");
            return;
        }

        // أوامر حذف المهام والملاحظات وإلغاء التذكيرات بالاسم أو بآخر عنصر
        if (handleVoiceDelete(lower)) return;

        if (lower.matches(".*(?:اتصل|كلم|رن على|دق على).*")) {
            if (!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("calls_enabled",true)) { speak("الاتصال مقفول من الإعدادات."); return; }
            String person = lower.replaceFirst(".*?(?:اتصل|كلم|رن على|دق على)\\s*(?:ب|على|مع|ل)?\\s*", "").trim();
            if (person.isEmpty()) speak("قولي اسم الشخص، مثلاً: اتصل بعبد الباري.");
            else requestContactAction(person,false);
            return;
        }

        if (lower.matches(".*(?:ابعث|ارسل|أرسل|دز).*رساله.*")) {
            if (!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("sms_enabled",true)) { speak("الرسائل مقفولة من الإعدادات."); return; }
            String rest = lower.replaceFirst(".*?(?:ابعث|ارسل|أرسل|دز)\\s*رساله\\s*(?:الى|ل)?\\s*", "");            int split = rest.indexOf(" نصها ");
            if (split < 1) split = rest.indexOf(" نص ");
            if (split < 1) speak("قولي مثلاً: ابعث رسالة لعبد الباري، واكتب فيها السلام عليكم.");
            else {
                String person = rest.substring(0,split).trim();
                String body = rest.substring(split).replaceFirst("^\\s*نصها?\\s*", "").trim();
                pendingContactName=person; pendingSms=true; pendingSmsBody=body;
                if (checkSelfPermission("android.permission.READ_CONTACTS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{"android.permission.READ_CONTACTS"},CONTACT_PERMISSION);
                else performContactAction();
            }
            return;
        }

        // الإجابة عن أسئلة مواعيد اليوم بصيغها الطبيعية باللهجة الليبية
        if (lower.contains("اليوم") && (lower.contains("موعد") || lower.contains("مواعيد"))) {
            String today = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
            ArrayList<String> todays = new ArrayList<>();
            for (int i = 0; i < reminders.length(); i++) {
                JSONObject item = reminders.optJSONObject(i);
                if (item == null) continue;
                String whenText = item.optString("when", "");
                if (whenText.startsWith(today)) {
                    String timeText = whenText.length() >= 18 ? whenText.substring(12).trim() : "";
                    String entry = item.optString("title", "موعد");
                    if (!timeText.isEmpty()) entry += " الساعة " + timeText;
                    todays.add(entry);
                }
            }
            if (todays.isEmpty()) {
                speak("ما عندكش مواعيد مسجلة لليوم.");
            } else {
                StringBuilder answer = new StringBuilder("عندك اليوم " + todays.size() + (todays.size() == 1 ? " موعد: " : " مواعيد: "));
                for (int i = 0; i < todays.size(); i++) {
                    if (i > 0) answer.append("، ");
                    answer.append(todays.get(i));
                }
                speak(answer.toString());
            }
            showTab("reminders");
            return;
        }

        if (lower.matches(".*(?:افتح|اعرض|وريني|ورني|شن عندي|شنو عندي).*")) {
            if (lower.contains("مهم")) { showTab("tasks"); speak("هذي مهامك."); return; }
            if (lower.contains("ملاحظ")) { showTab("notes"); speak("هذي ملاحظاتك."); return; }
            if (lower.contains("تذكير") || lower.contains("موعد")) { showTab("reminders"); speak("هذم تذكيراتك."); return; }
            if (lower.contains("اليوم")) { showTab("today"); speak("هذي لمحة على يومك."); return; }
        }
        if ((lower.contains("كم") || lower.contains("عدد")) && (lower.contains("مهمه") || lower.contains("مهام"))) {
            int done=0;
            for (int i=0;i<tasks.length();i++) if (tasks.optJSONObject(i)!=null && tasks.optJSONObject(i).optBoolean("done")) done++;
            speak("عندك " + tasks.length() + " مهام، كملت منهن " + done); return;
        }

        if (lower.matches(".*(?:احذف|امسح).*") && lower.contains("اخر") && lower.contains("مهم")) {
            if (tasks.length()>0) { tasks.remove(tasks.length()-1); saveData(); showTab("tasks"); speak("مسحت آخر مهمة."); }
            else speak("ما عندكش مهام تمسحها.");
            return;
        }
        if (lower.matches(".*(?:احذف|امسح).*") && lower.contains("اخر") && lower.contains("ملاحظ")) {
            if (notes.length()>0) { notes.remove(notes.length()-1); saveData(); showTab("notes"); speak("مسحت آخر ملاحظة."); }
            else speak("ما عندكش ملاحظات تمسحها.");
            return;
        }

        if (lower.matches(".*(?:اضف|ضيف|ديرلي|دير|نبي نضيف|نبي ندير|سجللي|سجل|اكتب).*مهمه.*")
                || lower.startsWith("مهمه جديده")) {
            String title = lower.replaceFirst(".*?(?:اضف|ضيف|ديرلي|دير|نبي نضيف|نبي ندير|سجللي|سجل|اكتب)?\\s*مهمه(?: جديده)?\\s*", "").trim();
            if (title.isEmpty()) { speak("شن هي المهمة اللي تبي نزيدهالك؟ مثلاً: زيدلي مهمة نشري أغراض البيت."); return; }
            JSONObject o=new JSONObject(); put(o,"title",title); put(o,"done",false); tasks.put(o); saveData(); showTab("tasks");
            speak("تمام، زدتلك المهمة: " + title); return;
        }
        if (lower.matches(".*(?:اضف|ضيف|ديرلي|دير|نبي نضيف|نبي ندير|سجللي|سجل|اكتب|دون|خزن).*ملاحظه.*")) {
            String body=lower.replaceFirst(".*?(?:اضف|ضيف|ديرلي|دير|نبي نضيف|نبي ندير|سجللي|سجل|اكتب|دون|خزن)?\\s*ملاحظه\\s*", "").trim();
            boolean wantsReminder = body.matches(".*(?:ذكرني|فكرني|دكرني).*");
            Calendar noteReminderTime = null;
            String reminderTitle = body;
            if (wantsReminder) {
                Matcher noteTime=Pattern.compile("(?:ال)?ساعه\\s*(\\d{1,2})(?::(\\d{1,2}))?").matcher(body);
                if (noteTime.find()) {
                    int hour=Integer.parseInt(noteTime.group(1));
                    int minute=noteTime.group(2)==null?0:Integer.parseInt(noteTime.group(2));
                    if(hour<=23 && minute<=59) {
                        noteReminderTime=Calendar.getInstance();
                        boolean tomorrow=body.contains("غدوة") || body.contains("بكره") || body.contains("بكرة") || body.contains("غدا");
                        if(tomorrow) noteReminderTime.add(Calendar.DAY_OF_YEAR,1);
                        noteReminderTime.set(Calendar.HOUR_OF_DAY,hour);
                        noteReminderTime.set(Calendar.MINUTE,minute);
                        noteReminderTime.set(Calendar.SECOND,0);
                        if(noteReminderTime.getTimeInMillis()<=System.currentTimeMillis() && !tomorrow) noteReminderTime.add(Calendar.DAY_OF_YEAR,1);
                    }
                    reminderTitle=body.substring(0,noteTime.start()).trim();
                }
                int marker=reminderTitle.indexOf("ذكرني");
                if(marker<0) marker=reminderTitle.indexOf("فكرني");
                if(marker<0) marker=reminderTitle.indexOf("دكرني");
                if(marker>=0) reminderTitle=reminderTitle.substring(0,marker).trim();
                reminderTitle=reminderTitle.replaceFirst("^(?:اني|ان|عندي)\\s*","").trim();
            }
            if (reminderTitle.isEmpty() && wantsReminder) reminderTitle="ملاحظة صوتية";
            if (reminderTitle.isEmpty()) { speak("شن تبي نكتبلك في الملاحظة؟"); return; }
            JSONObject o=new JSONObject(); put(o,"title","ملاحظة صوتية"); put(o,"body",reminderTitle);
            if(noteReminderTime!=null) put(o,"reminderWhen",new SimpleDateFormat("yyyy/MM/dd  HH:mm",Locale.getDefault()).format(noteReminderTime.getTime()));
            notes.put(o); saveData(); showTab("notes");
            if(wantsReminder && noteReminderTime!=null) {
                scheduleReminder("تذكير بالملاحظة: "+reminderTitle,noteReminderTime);
                speak("تمام، حفظت الملاحظة وضبطتلك تذكير في الوقت اللي طلبته.");
            } else if(wantsReminder) {
                speak("حفظت الملاحظة، لكن ما سمعتش وقت التذكير. قولي مثلاً: الساعة خمسة.");
            } else speak("تمام، خزّنتلك الملاحظة.");
            return;
        }

        if (lower.matches(".*(?:ذكرني|فكرني|اضف تذكير|ضيف تذكير|ديرلي تذكير).*")) {
            Matcher time=Pattern.compile("(?:الساعة\\s*)?(\\d{1,2})(?::(\\d{1,2}))?").matcher(lower);
            if (!time.find()) { speak("ما سمعتش الوقت، قولي مثلاً: فكرني غدوة الساعة تسعة نتصل."); return; }
            int hour=Integer.parseInt(time.group(1)), minute=time.group(2)==null?0:Integer.parseInt(time.group(2));
            if (hour>23 || minute>59) { speak("الوقت هذا مش صحيح."); return; }
            Calendar when=Calendar.getInstance();
            boolean tomorrow=lower.contains("غدوة") || lower.contains("بكره") || lower.contains("بكرة") || lower.contains("غدا");
            if (tomorrow) when.add(Calendar.DAY_OF_YEAR,1);
            when.set(Calendar.HOUR_OF_DAY,hour); when.set(Calendar.MINUTE,minute); when.set(Calendar.SECOND,0);
            if (when.getTimeInMillis()<=System.currentTimeMillis() && !tomorrow) when.add(Calendar.DAY_OF_YEAR,1);
            String title=lower.substring(time.end()).replaceFirst("^\\s*(?:بال|ب|في)\\s*", "").trim();
            title=title.replaceFirst("^(?:الاتصال|اتصال|انك|اني)\\s*", "").trim();
            if (title.isEmpty()) title="تذكير";
            scheduleReminder(title,when); speak("تمام، درتلك تذكير: " + title); return;
        }

        speak("ما فهمتكش، عاود قولّي.");
    }

    private void showTab(String tab) {
        currentTab=tab; content.removeAllViews();
        if ("today".equals(tab)) showToday();
        else if ("tasks".equals(tab)) showTasks();
        else if ("reminders".equals(tab)) showReminders();
        else if ("settings".equals(tab)) showSettings();
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
            if(!o.optString("reminderWhen","").isEmpty()) box.addView(text("تذكير: "+o.optString("reminderWhen"),13,teal));
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
            },c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
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

    // Check the latest main-branch commit in the background and notify if this APK is outdated.
    private void checkForUpdates() {
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL url = new java.net.URL("https://api.github.com/repos/alb567z/hayati-alyawmiyya/commits/main");
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Sahbi-Android-App");
                if (connection.getResponseCode() != 200) return;
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
                String latestSha = new JSONObject(body.toString()).optString("sha", "");
                String installedSha = BuildConfig.SOURCE_COMMIT;
                if (!latestSha.isEmpty() && !"local".equals(installedSha) && !latestSha.startsWith(installedSha) && !installedSha.startsWith(latestSha)) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("تحديث جديد لصاحبي")
                            .setMessage("فيه إصدار أحدث من صاحبي. تبي تفتح صفحة تنزيل آخر نسخة؟")
                            .setNegativeButton("لاحقاً", null)
                            .setPositiveButton("تنزيل التحديث", (dialog, which) -> {
                                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alb567z/hayati-alyawmiyya/actions/workflows/android.yml"))); }
                                catch (Exception e) { toast("تعذر فتح صفحة التحديث."); }
                            }).show();
                    });
                }
            } catch (Exception ignored) {
                // No internet or GitHub temporarily unavailable: keep the app usable.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
}