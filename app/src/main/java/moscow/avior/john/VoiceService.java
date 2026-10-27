package moscow.avior.john;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/** User-started foreground microphone service; never started by boot or a sticky restart. */
public class VoiceService extends Service {
    static final String START = "moscow.avior.john.START_VOICE";
    static final String STOP = "moscow.avior.john.STOP_VOICE";
    private static final String CHANNEL = "john-voice";
    private static final int NOTIFICATION = 30;
    private volatile boolean stopping;
    private Thread worker;

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Микрофон Джона", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, VoiceService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("John Assistant · локальное распознавание")
                .setContentText("Микрофон включён пользователем. Скажите: «Джон, пауза».")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Остановить", stop).build()).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !START.equals(intent.getAction())) {
            if (intent != null && STOP.equals(intent.getAction()))
                getSharedPreferences("john", MODE_PRIVATE).edit().putBoolean("voiceRequested", false).apply();
            stopping = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (worker != null) return START_NOT_STICKY;
        if (VoiceState.active) { stopSelf(); return START_NOT_STICKY; }
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Разрешите доступ к микрофону в приложении");
            if (ModelStore.get(this).busy || !ModelStore.get(this).isReady())
                throw new IllegalStateException("Сначала скачайте русскую модель");
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIFICATION, notification());
            getSharedPreferences("john", MODE_PRIVATE).edit().putBoolean("voiceRequested", true).apply();
            VoiceState.error = "";
            VoiceState.active = true;
            VoiceState.status = "Проверка файлов и загрузка Vosk в память…";
            stopping = false;
            worker = new Thread(this::listen, "john-vosk");
            worker.start();
        } catch (Exception | LinkageError e) {
            VoiceState.error = "Запуск: " + e.getMessage();
            VoiceState.status = "Не запущен. Откройте приложение и повторите запуск.";
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private String grammar() {
        JSONArray phrases = new JSONArray();
        phrases.put("джон");
        for (String phrase : VoiceCommands.PHRASES.keySet()) {
            phrases.put(phrase);
            phrases.put("джон " + phrase);
            phrases.put("не " + phrase);
            phrases.put("джон не " + phrase);
        }
        phrases.put("отмена");
        phrases.put("нет");
        phrases.put("[unk]");
        return phrases.toString();
    }

    private void listen() {
        Model model = null;
        Recognizer recognizer = null;
        AudioRecord recorder = null;
        AudioManager manager = (AudioManager)getSystemService(AUDIO_SERVICE);
        AudioManager.AudioRecordingCallback recordingCallback = null;
        PowerManager.WakeLock wakeLock = null;
        try {
            ModelStore store = ModelStore.get(this);
            ModelArchive.verify(store.directory);
            if (stopping) return;
            model = new Model(store.directory.getAbsolutePath());
            if (stopping) return;
            recognizer = new Recognizer(model, 16000f, grammar());
            int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IllegalStateException("PCM 16 кГц не поддерживается: " + min);
            // MIC matches the successful 0.2 diagnostic route on the station.
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Доступ к микрофону отозван");
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 12800));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Микрофон занят или недоступен");
            if (Build.VERSION.SDK_INT >= 29) {
                final int session = recorder.getAudioSessionId();
                recordingCallback = new AudioManager.AudioRecordingCallback() {
                    @Override public void onRecordingConfigChanged(java.util.List<AudioRecordingConfiguration> configs) {
                        if (stopping) return;
                        for (AudioRecordingConfiguration config : configs) {
                            if (config.getClientAudioSessionId() == session && config.isClientSilenced())
                                VoiceState.error = "Android заглушил вход. Возможна конкуренция с Алисой или отключение микрофона.";
                        }
                    }
                };
                manager.registerAudioRecordingCallback(recordingCallback, new Handler(Looper.getMainLooper()));
            }
            if (stopping) return;
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Не удалось включить микрофон");
            VoiceState.microphone = true;
            wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "john:recognition");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(600000); // Renew while listening; OS expiry bounds a stalled worker.
            VoiceState.status = "Слушаю локально. Скажите: «Джон, громче».";
            VoiceCommands gate = new VoiceCommands();
            short[] buffer = new short[3200];
            long lastSignal = SystemClock.elapsedRealtime();
            long lastUi = 0;
            long lastCommand = -1000;
            long lastWakeRenew = SystemClock.elapsedRealtime();
            while (!stopping) {
                int n = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_NON_BLOCKING);
                if (n < 0) throw new IllegalStateException("Ошибка AudioRecord.read: " + n);
                if (n == 0) { SystemClock.sleep(20); continue; }
                long now = SystemClock.elapsedRealtime();
                if (now - lastWakeRenew >= 300000) { wakeLock.acquire(600000); lastWakeRenew = now; }
                int peak = 0;
                for (int i = 0; i < n; i++) peak = Math.max(peak, Math.abs((int)buffer[i]));
                VoiceState.peak = peak;
                if (peak > 0) lastSignal = now;
                if (now - lastSignal > 15000) VoiceState.error = "Нет сигнала 15 секунд: проверьте аппаратный mute и доступ микрофона рядом с Алисой.";
                if (recognizer.acceptWaveForm(buffer, n)) {
                    String text = new JSONObject(recognizer.getResult()).optString("text", "");
                    if (stopping) break; // Stopping must never execute the buffered final phrase.
                    if (!text.isEmpty()) VoiceState.phrase = text;
                    String command = gate.accept(text, now);
                    VoiceState.status = gate.isArmed(now) ? "«Джон» услышан. Ожидаю команду до 5 секунд."
                            : "Слушаю локально. Ожидаю обращение «Джон».";
                    if (command != null && now - lastCommand >= 700) {
                        lastCommand = now;
                        try {
                            VoiceState.command = MusicActions.execute(this, command);
                            VoiceState.error = "";
                        } catch (Exception e) {
                            VoiceState.error = "Команда «" + MusicActions.label(command) + "» не выполнена: " + e.getMessage();
                        }
                    }
                } else if (now - lastUi >= 500) {
                    lastUi = now;
                    String partial = new JSONObject(recognizer.getPartialResult()).optString("partial", "");
                    if (!partial.isEmpty()) VoiceState.phrase = partial + " …";
                    if (!gate.isArmed(now)) VoiceState.status = "Слушаю локально. Ожидаю обращение «Джон».";
                }
            }
        } catch (Exception | LinkageError | OutOfMemoryError e) {
            if (!stopping) VoiceState.error = "Распознавание: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (recordingCallback != null) try { manager.unregisterAudioRecordingCallback(recordingCallback); } catch (RuntimeException ignored) {}
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                try { recorder.release(); } catch (RuntimeException ignored) {}
            }
            if (recognizer != null) try { recognizer.close(); } catch (Exception ignored) {}
            if (model != null) try { model.close(); } catch (Exception ignored) {}
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            VoiceState.microphone = false;
            VoiceState.peak = 0;
            VoiceState.status = "Остановлен. Для включения нажмите «Запустить Джона».";
            VoiceState.active = false;
            stopSelf();
        }
    }
    @Override public void onDestroy() {
        stopping = true; // Worker owns, stops and releases all native resources in finally.
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    static void bootReminder(Context context) {
        if (!context.getSharedPreferences("john", MODE_PRIVATE).getBoolean("voiceRequested", false)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String channel = "john-resume";
        manager.createNotificationChannel(new NotificationChannel(channel, "Запуск после перезагрузки", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(context, 3, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.notify(31, new Notification.Builder(context, channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("John Assistant готов к запуску")
                    .setContentText("Откройте приложение и нажмите «Запустить Джона». Микрофон выключен.")
                    .setContentIntent(open).setAutoCancel(true).build());
        } catch (SecurityException ignored) { /* Visible app also explains manual restart. */ }
    }
}
