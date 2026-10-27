package moscow.avior.john;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))) return;
        context.getSharedPreferences("john", Context.MODE_PRIVATE).edit().putLong("bootReceived", System.currentTimeMillis()).apply();
        // Do not open an activity or start a microphone foreground service from background.
        // Ask the OS to rebind the authorized notification listener.
        JohnListener.rebind(context);
        VoiceService.bootReminder(context);
    }
}
