package com.hayati.alyawmiyya;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.*;
import java.util.*;
import java.text.*;

public class WakeWordService extends Service {
    public static final String ACTION_START="com.hayati.alyawmiyya.WAKE_START";
    public static final String ACTION_STOP="com.hayati.alyawmiyya.WAKE_STOP";
    private static final String CHANNEL="sahbi_listen";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean active=false, waiting=false, listening=false, ttsReady=false;
    private final Runnable restart=()->startListening();

    @Override public void onCreate() {
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(CHANNEL,"استماع صاحبي",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("إشعار ظاهر أثناء تشغيل الميكروفون");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ar","LY"));
                applyVoiceSettings();
                ttsReady=true;
            }
        });
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && ACTION_STOP.equals(intent.getAction())) {
            stopListeningService();
            return START_NOT_STICKY;
        }
        Notification.Builder builder=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        Notification notification=builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("صاحبي يستمع")
            .setContentText("قل: يا صاحبي — الميكروفون نشط")
            .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)
            startForeground(8102,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(8102,notification);
        active=true;
        if(!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("خدمة التعرف على الصوت غير متاحة في الهاتف.");
            return START_NOT_STICKY;
        }
        createRecognizer();
        handler.postDelayed(restart,800);
        return START_STICKY;
    }

    private void createRecognizer() {
        try {
            recognizer=SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { listening=true; }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float value) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { listening=false; }
                @Override public void onError(int error) {
                    listening=false;
                    if(active) handler.postDelayed(restart,1500);
                }
                @Override public void onResults(Bundle results) {
                    listening=false;
                    ArrayList<String> matches=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    process(matches==null||matches.isEmpty()?"":matches.get(0));
                }
                @Override public void onPartialResults(Bundle results) {}
                @Override public void onEvent(int event,Bundle params) {}
            });
        } catch(Exception error) { recognizer=null; }
    }

    private void startListening() {
        if(!active || listening) return;
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopListeningService(); return;
        }
        if(recognizer==null) createRecognizer();
        if(recognizer==null) { handler.postDelayed(restart,3000); return; }
        try {
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-LY");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,2);
            recognizer.startListening(intent);
        } catch(Exception error) { handler.postDelayed(restart,2500); }
    }

    private String normalize(String value) {
        if(value==null) return "";
        return value.replace("أ","ا").replace("إ","ا").replace("آ","ا")
            .replace("ة","ه").replace("ى","ي").toLowerCase(new Locale("ar")).trim();
    }

    private void process(String raw) {
        String heard=normalize(raw);
        if(heard.isEmpty()) { handler.postDelayed(restart,500); return; }
        if(!waiting) {
            if(heard.contains("يا صاحبي") || heard.contains("يا صاح") || heard.contains("صاحبي")) {
                waiting=true;
                say("نعم يا صاحبي");
            } else handler.postDelayed(restart,500);
            return;
        }
        waiting=false;
        if(heard.contains("الساعه") || heard.contains("كم ساعه") || heard.contains("الوقت")) {
            String time=new SimpleDateFormat("h:mm a",new Locale("ar","LY")).format(new Date());
            say("الساعة توا "+time);
        } else if(heard.contains("اشعار") || heard.contains("اشعارات")) {
            if(!NotificationReaderService.hasAccess(this)) {
                say("فعّل إذن قراءة الإشعارات لصاحبي من إعدادات الهاتف.");
            } else {
                int count=NotificationReaderService.getActiveCount();
                if(count<0) say("خدمة الإشعارات ما زالت تتصل.");
                else if(count==0) say("ما عندكش إشعارات ظاهرة توا.");
                else say("عندك "+count+" إشعار ظاهر توا.");
            }
        } else say("حالياً نقدر نجاوبك عن الساعة والإشعارات.");
    }

    private void say(String message) {
        if(!ttsReady || tts==null) { handler.postDelayed(restart,1800); return; }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { handler.post(()->{ if(active) handler.postDelayed(restart,500); }); }
            @Override public void onError(String id) { handler.postDelayed(restart,1000); }
        });
        applyVoiceSettings();
        tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"sahbi-"+System.currentTimeMillis());
    }

    private void applyVoiceSettings() {
        if (tts == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("hayati_data",MODE_PRIVATE);
        float pitch = prefs.getFloat("voice_pitch",1.0f);
        tts.setPitch(pitch);
        tts.setSpeechRate(1.0f);
    }

    private void stopListeningService() {
        active=false;
        handler.removeCallbacksAndMessages(null);
        if(recognizer!=null) { try { recognizer.cancel(); recognizer.destroy(); } catch(Exception ignored) {} recognizer=null; }
        if(tts!=null) { try { tts.stop(); tts.shutdown(); } catch(Exception ignored) {} tts=null; }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        active=false;
        handler.removeCallbacksAndMessages(null);
        if(recognizer!=null) { try { recognizer.destroy(); } catch(Exception ignored) {} }
        if(tts!=null) { try { tts.shutdown(); } catch(Exception ignored) {} }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
