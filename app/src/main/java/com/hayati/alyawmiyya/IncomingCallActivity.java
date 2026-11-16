package com.hayati.alyawmiyya;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import java.util.*;

public class IncomingCallActivity extends Activity {
    private TextToSpeech tts;
    private Ringtone ringtone;
    private Vibrator vibrator;
    private TextView status;
    private String title = "المهمة أو الموعد";
    private boolean answered = false;
    private static final int SPEECH = 441;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        title = getIntent().getStringExtra("title");
        if (title == null || title.trim().isEmpty()) title = "المهمة أو الموعد";
        getWindow().setStatusBarColor(Color.rgb(0, 105, 92));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 32, 28, 32);
        root.setBackgroundColor(Color.rgb(0, 105, 92));

        TextView avatar = new TextView(this);
        avatar.setText("♥");
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(54);
        avatar.setGravity(Gravity.CENTER);
        root.addView(avatar);

        TextView name = new TextView(this);
        name.setText("نسرين");
        name.setTextColor(Color.WHITE);
        name.setTextSize(30);
        name.setGravity(Gravity.CENTER);
        root.addView(name);

        TextView detail = new TextView(this);
        detail.setText("تتصل بك لتذكيرك بالمهمة أو الموعد\n" + title);
        detail.setTextColor(Color.WHITE);
        detail.setTextSize(18);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, 18, 0, 35);
        root.addView(detail);

        status = new TextView(this);
        status.setText("اتصال وارد");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        Button answer = new Button(this);
        answer.setText("📞 الرد على نسرين");
        answer.setAllCaps(false);
        root.addView(answer);

        Button decline = new Button(this);
        decline.setText("إنهاء الاتصال");
        decline.setAllCaps(false);
        root.addView(decline);

        setContentView(root);
        startAlert();

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ar", "LY"));
                android.content.SharedPreferences prefs = getSharedPreferences("hayati_data", MODE_PRIVATE);
                String voiceName = prefs.getString("nesreen_voice_name", "");
                if (!voiceName.isEmpty() && tts.getVoices() != null) {
                    for (android.speech.tts.Voice voice : tts.getVoices()) {
                        if (voice != null && voiceName.equals(voice.getName())) {
                            try { tts.setVoice(voice); } catch (Exception ignored) {}
                            break;
                        }
                    }
                }
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.0f);
            }
        });

        answer.setOnClickListener(v -> answerCall(root));
        decline.setOnClickListener(v -> finish());
    }

    private void startAlert() {
        try {
            android.net.Uri tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (tone == null) tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, tone);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } else {
                    ringtone.setStreamType(android.media.AudioManager.STREAM_ALARM);
                }
                ringtone.play();
            }
        } catch (Exception ignored) {}

        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 700, 450, 700, 450, 1100};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopAlert() {
        if (ringtone != null) {
            try { ringtone.stop(); } catch (Exception ignored) {}
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
        }
    }

    private void answerCall(LinearLayout root) {
        if (answered) return;
        answered = true;
        stopAlert();
        status.setText("المكالمة متصلة — اضغط تحدث ورد عليّ");
        say("صباح الخير يا صاحبي. نذكرك بالمهمة أو الموعد: " + title
                + ". كيف أمورك؟ هل أنت جاهز؟");
        Button talk = new Button(this);
        talk.setText("🎙️ تحدث مع نسرين");
        talk.setAllCaps(false);
        root.addView(talk);
        talk.setOnClickListener(v -> listen());
    }

    private void listen() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 72);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("خدمة التعرف على الصوت غير متوفرة في هاتفك.");
            return;
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-LY");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            startActivityForResult(intent, SPEECH);
        } catch (Exception e) {
            say("ما قدرتش نفتح الميكروفون. تأكد من إذن الميكروفون.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            say("ما سمعتكش كويس، عاود احكي معاي.");
            return;
        }
        String heard = results.get(0).toLowerCase(new Locale("ar"));
        String response;
        if (heard.contains("جاهز") || heard.contains("اي") || heard.contains("تمام") || heard.contains("حاضر")) {
            response = "يعطيك الصحة. حاول تكون جاهز قبل الموعد بشوية. تبي نراجع معاك تفاصيله؟";
        } else if (heard.contains("لا") || heard.contains("مش جاهز") || heard.contains("متأخر")) {
            response = "ما فيش مشكلة. حاول ترتب وقتك. تبي نذكرك مرة ثانية؟";
        } else if (heard.contains("شن") || heard.contains("متى") || heard.contains("الساعة")) {
            response = "التذكير المسجل هو: " + title + ". تقدر تشوف وقته في قائمة التذكيرات.";
        } else {
            response = "سمعتك يا صاحبي. نقدر نساعدك في الاستعداد. تبي نراجع تفاصيل التذكير؟";
        }
        status.setText("متصلة — آخر كلامك: " + heard);
        say(response);
    }

    private void say(String message) {
        if (tts != null) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reminder-call-" + System.currentTimeMillis());
        } else if (status != null) {
            status.setText(message);
        }
    }

    @Override
    protected void onDestroy() {
        stopAlert();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}