package moscow.avior.john;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.*;
import android.media.session.*;
import android.media.session.MediaController;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity implements RecognitionListener {
    private TextView status;
    private AudioManager audio;
    private MediaSessionManager sessions;
    private MediaController observed;
    private SpeechRecognizer recognizer;
    private String pending;
    private volatile boolean recording;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { show("Обновление плеера: " + stateText(state)); }
        @Override public void onSessionDestroyed() { observed = null; show("Сессия закрыта. Включите Яндекс Музыку и обновите проверку."); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        sessions = (MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(24));
        layout.setBackgroundColor(Color.rgb(18,23,34));
        scroll.addView(layout);
        TextView title = new TextView(this);
        title.setText("JOHN ASSISTANT  0.2-test"); title.setTextSize(24); title.setTextColor(Color.WHITE); layout.addView(title);
        status = new TextView(this); status.setTextSize(17); status.setTextColor(Color.rgb(160,217,255));
        status.setPadding(0,dp(16),0,dp(16)); status.setTextIsSelectable(true); layout.addView(status);
        add(layout,"1. Доступ к уведомлениям",()->openSettings());
        add(layout,"2. Проверить музыку и автостарт",()->checkSession());
        add(layout,"Воспроизведение",()->transport("play"));
        add(layout,"Пауза",()->transport("pause"));
        add(layout,"Следующий трек",()->transport("next"));
        add(layout,"Предыдущий трек",()->transport("previous"));
        add(layout,"Громче",()->volume(AudioManager.ADJUST_RAISE));
        add(layout,"Тише",()->volume(AudioManager.ADJUST_LOWER));
        add(layout,"Выключить / включить звук",()->volume(audio.isStreamMute(3)?AudioManager.ADJUST_UNMUTE:AudioManager.ADJUST_MUTE));
        add(layout,"Проверить микрофон: 3 секунды",()->permission("mic"));
        add(layout,"Голосовая команда по нажатию",()->permission("speech"));
        TextView foot = new TextView(this); foot.setTextColor(Color.LTGRAY); foot.setTextSize(15);
        foot.setText("Команды: пауза, играй, дальше, назад, громче, тише.\nМикрофон включается только кнопкой. Проверка уровня не сохраняет и не передаёт звук. Системное распознавание может использовать сеть.\nАвтостарт восстанавливает доступ к медиасессиям. Постоянное прослушивание слова «Джон» не реализовано.");
        layout.addView(foot); setContentView(scroll); checkSession();
    }
    private int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private void add(LinearLayout p,String text,Runnable run){
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setMinHeight(dp(48));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(8); p.addView(b,lp);
        b.setOnClickListener(v->{try{run.run();}catch(Exception e){show("Ошибка: "+e.getClass().getSimpleName()+": "+e.getMessage());}});
    }
    private void show(String text){status.setText(text);}
    private void openSettings(){
        Intent i=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        try {startActivity(i);} catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));show("YaOS не открыл раздел доступа. См. инструкцию ADB.");}
    }
    private MediaController music(){
        for(MediaController c:sessions.getActiveSessions(new ComponentName(this,JohnListener.class))){
            if("ru.yandex.music".equals(c.getPackageName())){
                if(observed==null || !observed.getSessionToken().equals(c.getSessionToken())){
                    if(observed!=null)observed.unregisterCallback(callback); observed=c; observed.registerCallback(callback,handler);
                } return c;
            }
        } return null;
    }
    private String stamp(String key){long t=getSharedPreferences("john",MODE_PRIVATE).getLong(key,0);return t==0?"ещё не зафиксировано":java.text.DateFormat.getDateTimeInstance().format(new Date(t));}
    private String stateText(PlaybackState p){return p==null?"нет данных":"state="+p.getState()+", position="+p.getPosition()+", actions="+p.getActions();}
    private void checkSession(){
        String base="Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\nМодель: "+Build.MODEL+
            "\nСлужба распознавания: "+(SpeechRecognizer.isRecognitionAvailable(this)?"есть":"не найдена")+
            "\nПодключение слушателя: "+stamp("listenerConnected")+"\nСобытие загрузки/обновления: "+stamp("bootReceived");
        try {MediaController c=music();show(base+(c==null?"\nСессия ru.yandex.music не найдена. Включите трек.":"\nru.yandex.music: "+stateText(c.getPlaybackState())));}
        catch(SecurityException e){show(base+"\nРазрешите John Assistant доступ к уведомлениям.");}
    }
    private void transport(String command){
        MediaController c=music(); if(c==null){show("Активная сессия ru.yandex.music не найдена.");return;}
        PlaybackState p=c.getPlaybackState(); long needed="play".equals(command)?PlaybackState.ACTION_PLAY:"pause".equals(command)?PlaybackState.ACTION_PAUSE:"next".equals(command)?PlaybackState.ACTION_SKIP_TO_NEXT:PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if(p!=null && (p.getActions()&needed)==0){show("Плеер не объявляет поддержку команды: "+command);return;}
        MediaController.TransportControls t=c.getTransportControls();
        if("play".equals(command))t.play();else if("pause".equals(command))t.pause();else if("next".equals(command))t.skipToNext();else t.skipToPrevious();
        show("Отправлено: "+command+". Ожидаем обновление плеера; отправка не подтверждает выполнение.");
    }
    private void volume(int direction){audio.adjustStreamVolume(3,direction,AudioManager.FLAG_SHOW_UI);show("Громкость: "+audio.getStreamVolume(3)+"/"+audio.getStreamMaxVolume(3)+"; без звука: "+audio.isStreamMute(3));}
    private void permission(String action){
        if(recording || recognizer!=null){show("Проверка уже идёт.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pending=action;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7);return;}
        if("mic".equals(action))testMic();else speech();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);if(request==7){String a=pending;pending=null;if(result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED)permission(a);else show("Микрофон не разрешён. Управление кнопками доступно.");}}
    private void testMic(){
        recording=true;show("Скажите несколько слов. Проверяем уровень 3 секунды без сохранения записи.");
        new Thread(()->{
            AudioRecord rec=null;String result;
            try {
                int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(min<=0)throw new IllegalStateException("PCM 16 kHz не поддерживается: "+min);
                rec=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));
                if(rec.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("AudioRecord не инициализирован");
                rec.startRecording();short[] data=new short[1024];long count=0;double sum=0;int peak=0;
                long end=SystemClock.elapsedRealtime()+3000;
                while(recording&&SystemClock.elapsedRealtime()<end){int n=rec.read(data,0,data.length,AudioRecord.READ_NON_BLOCKING);if(n<0)throw new IllegalStateException("read="+n);for(int j=0;j<n;j++){int v=Math.abs((int)data[j]);peak=Math.max(peak,v);sum+=(double)v*v;}count+=n;if(n==0)SystemClock.sleep(10);}
                result="Микрофон: samples="+count+", peak="+peak+", RMS="+(count==0?0:(int)Math.sqrt(sum/count))+". "+(peak==0?"Звук не получен: возможны mute, конкуренция с Алисой или ограничения YaOS.":"Сигнал получен. Это не подтверждает доступ в фоне.");
            }catch(Exception e){result="Микрофон: "+e.getClass().getSimpleName()+": "+e.getMessage();}
            finally{if(rec!=null){try{rec.stop();}catch(Exception ignored){}rec.release();}recording=false;}
            final String text=result;runOnUiThread(()->show(text));
        },"john-mic-test").start();
    }
    private void speech(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){show("В YaOS нет доступной службы распознавания. Для слова «Джон» потребуется отдельный локальный движок.");return;}
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(this);
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
        recognizer.startListening(i);show("Слушаю одну команду. Распознавание выполняет системная служба.");
    }
    private void stopSpeech(){if(recognizer!=null){SpeechRecognizer old=recognizer;recognizer=null;old.cancel();old.destroy();}}
    @Override public void onResults(Bundle b){ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);stopSpeech();if(r==null||r.isEmpty()){show("Речь не распознана.");return;}String s=r.get(0).toLowerCase(new Locale("ru","RU"));try{String c=CommandParser.parse(s);if("louder".equals(c))volume(1);else if("quieter".equals(c))volume(-1);else if(c!=null)transport(c);else show("Неизвестная команда: "+s);}catch(Exception e){show("Команда: "+e.getMessage());}}
    @Override public void onError(int error){stopSpeech();show("Распознавание: код ошибки "+error+". Проверьте службу речи, сеть и доступ к микрофону.");}
    @Override protected void onPause(){recording=false;stopSpeech();super.onPause();}
    @Override protected void onDestroy(){recording=false;stopSpeech();if(observed!=null)observed.unregisterCallback(callback);super.onDestroy();}
    @Override public void onReadyForSpeech(Bundle b){} @Override public void onBeginningOfSpeech(){} @Override public void onRmsChanged(float r){} @Override public void onBufferReceived(byte[] b){} @Override public void onEndOfSpeech(){} @Override public void onPartialResults(Bundle b){} @Override public void onEvent(int e,Bundle b){}
}
