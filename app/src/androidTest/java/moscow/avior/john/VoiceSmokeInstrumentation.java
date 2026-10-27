package moscow.avior.john;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.File;

/** Explicit developer smoke test on an emulator. Requires the official archive in external files/model.zip. */
public class VoiceSmokeInstrumentation extends Instrumentation {
    private volatile Activity resumedActivity;
    @Override public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        resumedActivity = activity;
    }
    @Override public void callActivityOnPause(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
        super.callActivityOnPause(activity);
    }
    private Activity open(Context context) {
        runOnMainSync(() -> context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (resumedActivity == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        require(resumedActivity != null, "Activity did not resume");
        return resumedActivity;
    }
    private void stage(String text) { Bundle data = new Bundle(); data.putString("stage",text); sendStatus(0,data); }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail + " / " + VoiceState.error);
    }
    private void await(boolean active, long timeout) {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (active ? VoiceState.microphone : !VoiceState.active) return;
            SystemClock.sleep(100);
        }
        require(false, active ? "Microphone did not start" : "Resources were not released");
    }
    @Override public void onStart() {
        Context context = getTargetContext();
        Bundle result = new Bundle();
        Activity activity = null;
        try {
            File archive = new File(context.getExternalFilesDir(null), "model.zip");
            require(ModelArchive.hash(archive).equals(ModelStore.SHA256), "Official model SHA-256");
            ModelStore store = ModelStore.get(context);
            ModelArchive.remove(store.directory);
            ModelArchive.extract(archive, store.directory, ModelStore.NAME);
            ModelArchive.verify(store.directory);
            stage("model verified");
            activity = open(context);
            stage("activity resumed");
            runOnMainSync(() -> context.startForegroundService(new Intent(context, VoiceService.class).setAction(VoiceService.START)));
            await(true, 30000);
            stage("native Vosk and microphone started");
            Activity foreground = activity;
            runOnMainSync(() -> foreground.moveTaskToBack(true));
            SystemClock.sleep(2500);
            require(VoiceState.active && VoiceState.microphone, "Listening continues with activity in background");
            context.stopService(new Intent(context, VoiceService.class));
            await(false, 10000);
            require(!VoiceState.microphone && VoiceState.peak == 0, "Microphone was released");
            activity = open(context);
            runOnMainSync(() -> context.startForegroundService(new Intent(context, VoiceService.class).setAction(VoiceService.START)));
            await(true, 30000);
            context.stopService(new Intent(context, VoiceService.class));
            await(false, 10000);
            new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
            SystemClock.sleep(500);
            require(!VoiceState.active && !VoiceState.microphone, "Boot must not start the microphone");
            require(VoiceState.error.isEmpty() || VoiceState.error.contains("Нет сигнала"), "Unexpected voice error");
            result.putString("result", "PASS: SHA-256, safe extraction, native Vosk load, AudioRecord, background service, stop/restart cleanup, boot does not start mic");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("error", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            context.stopService(new Intent(context, VoiceService.class));
            if (activity != null) { Activity current = activity; runOnMainSync(current::finish); }
        }
    }
}
