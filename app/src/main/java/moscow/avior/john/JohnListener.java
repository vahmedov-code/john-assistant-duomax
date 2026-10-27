package moscow.avior.john;

import android.service.notification.NotificationListenerService;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Build;
import android.media.session.MediaSessionManager;

public class JohnListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        getSharedPreferences("john", MODE_PRIVATE).edit().putLong("listenerConnected", System.currentTimeMillis()).apply();
        // Android binds the service again after reboot if notification access remains enabled.
    }
    public static void rebind(android.content.Context c) {
        if (Build.VERSION.SDK_INT >= 24) {
            try { requestRebind(new ComponentName(c, JohnListener.class)); }
            catch (Exception ignored) {}
        }
    }
}
