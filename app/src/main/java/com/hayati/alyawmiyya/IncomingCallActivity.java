package com.hayati.alyawmiyya;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import java.util.*;
public class IncomingCallActivity extends Activity {
 private TextToSpeech tts; private Ringtone ringtone; private TextView status; private String title="موعد العمل"; private boolean answered=false;
 private static final int SPEECH=441;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(Build.VERSION.SDK_INT>=27){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setShowWhenLocked(true);setTurnScreenOn(true);}
  else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  title=getIntent().getStringExtra("title");if(title==null||title.trim().isEmpty())title="موعد العمل";
  getWindow().setStatusBarColor(Color.rgb(0,105,92));
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(28,32,28,32);root.setBackgroundColor(Color.rgb(0,105,92));
  TextView avatar=new TextView(this);avatar.setText("♥");avatar.setTextColor(Color.WHITE);avatar.setTextSize(54);avatar.setGravity(Gravity.CENTER);root.addView(avatar);
  TextView name=new TextView(this);name.setText("نسرين");name.setTextColor(Color.WHITE);name.setTextSize(30);name.setGravity(Gravity.CENTER);root.addView(name);
  TextView detail=new TextView(this);detail.setText("تتصل بك بخصوص موعد العمل\n"+title);detail.setTextColor(Color.WHITE);detail.setTextSize(18);detail.setGravity(Gravity.CENTER);detail.setPadding(0,18,0,35);root.addView(detail);
  status=new TextView(this);status.setText("اتصال وارد");status.setTextColor(Color.WHITE);status.setTextSize(16);status.setGravity(Gravity.CENTER);root.addView(status);
  Button answer=new Button(this);answer.setText("📞 الرد على نسرين");answer.setAllCaps(false);root.addView(answer);
  Button decline=new Button(this);decline.setText("إنهاء الاتصال");decline.setAllCaps(false);root.addView(decline);
  setContentView(root);try{android.net.Uri ring=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);if(ring!=null){ringtone=RingtoneManager.getRingtone(this,ring);if(ringtone!=null)ringtone.play();}}catch(Exception ignored){}tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS){tts.setLanguage(new Locale("ar","LY"));android.content.SharedPreferences p=getSharedPreferences("hayati_data",MODE_PRIVATE);String nesreenVoice=p.getString("nesreen_voice_name","");if(!nesreenVoice.isEmpty() && tts.getVoices()!=null){for(android.speech.tts.Voice voice:tts.getVoices()){if(voice!=null && nesreenVoice.equals(voice.getName())){try{tts.setVoice(voice);}catch(Exception ignored){}break;}}}tts.setPitch(1.0f);tts.setSpeechRate(1.0f);}});
  answer.setOnClickListener(v->answerCall(root));decline.setOnClickListener(v->finish());
 }
 private void answerCall(LinearLayout root){
  if(answered)return;answered=true;if(ringtone!=null){try{ringtone.stop();}catch(Exception ignored){}}status.setText("المكالمة متصلة — اضغط تحدث ورد عليّ");
  say("صباح الخير يا صاحبي. نذكرك بموعد العمل: "+title+". كيف أمورك؟ هل أنت جاهز؟");
  Button talk=new Button(this);talk.setText("🎙️ تحدث مع نسرين");talk.setAllCaps(false);root.addView(talk);talk.setOnClickListener(v->listen());
 }
 private void listen(){
  if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},72);return;}
  if(!SpeechRecognizer.isRecognitionAvailable(this)){say("خدمة التعرف على الصوت غير متوفرة في هاتفك.");return;}
  try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-LY");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);startActivityForResult(i,SPEECH);}
  catch(Exception e){say("ما قدرتش نفتح الميكروفون. تأكد من إذن الميكروفون.");}
 }
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);if(requestCode!=SPEECH||resultCode!=RESULT_OK||data==null)return;
  ArrayList<String> r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(r==null||r.isEmpty()){say("ما سمعتكش كويس، عاود احكي معاي.");return;}
  String heard=r.get(0).toLowerCase(new Locale("ar"));String response;
  if(heard.contains("جاهز")||heard.contains("اي")||heard.contains("تمام")||heard.contains("حاضر"))response="يعطيك الصحة. حاول تكون جاهز قبل الموعد بشوية. تبي نراجع معاك تفاصيل الموعد؟";
  else if(heard.contains("لا")||heard.contains("مش جاهز")||heard.contains("متأخر"))response="ما فيش مشكلة. حاول ترتب وقتك. تبي نذكرك مرة ثانية؟";
  else if(heard.contains("شن")||heard.contains("متى")||heard.contains("الساعة"))response="موعدك المسجل هو: "+title+". تقدر تشوف وقته في قائمة التذكيرات.";
  else response="سمعتك يا صاحبي. نقدر نساعدك في الاستعداد لموعدك. تبي نراجع تفاصيل الموعد؟";
  status.setText("متصلة — آخر كلامك: "+heard);say(response);
 }
 private void say(String message){if(tts!=null)tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"work-call-"+System.currentTimeMillis());else if(status!=null)status.setText(message);}
 @Override protected void onDestroy(){if(ringtone!=null){try{ringtone.stop();}catch(Exception ignored){}}if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}