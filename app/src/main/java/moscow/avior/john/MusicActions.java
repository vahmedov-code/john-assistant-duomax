package moscow.avior.john;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

/** Same AudioManager/ru.yandex.music transport route used by the 0.2 buttons. */
final class MusicActions {
    static String execute(Context context, String command) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if ("louder".equals(command) || "quieter".equals(command) || "mute".equals(command)) {
            int direction = "mute".equals(command) ? AudioManager.ADJUST_MUTE
                    : "louder".equals(command) ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            return label(command) + ": " + audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    + (audio.isStreamMute(AudioManager.STREAM_MUSIC) ? " (звук выключен)" : "");
        }
        MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        for (MediaController controller : manager.getActiveSessions(new ComponentName(context, JohnListener.class))) {
            if (!"ru.yandex.music".equals(controller.getPackageName())) continue;
            long action = "pause".equals(command) ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_SKIP_TO_NEXT;
            PlaybackState state = controller.getPlaybackState();
            if (state != null && (state.getActions() & action) == 0)
                throw new IllegalStateException("Яндекс Музыка не объявляет поддержку: " + label(command));
            if ("pause".equals(command)) controller.getTransportControls().pause();
            else if ("next".equals(command)) controller.getTransportControls().skipToNext();
            else throw new IllegalArgumentException("Неизвестная команда");
            return label(command) + " — отправлено Яндекс Музыке; выполнение проверьте по звуку";
        }
        throw new IllegalStateException("Нет активной сессии ru.yandex.music. Включите музыку.");
    }
    static String label(String command) {
        switch (command) {
            case "louder": return "Громче";
            case "quieter": return "Тише";
            case "mute": return "Выключить звук";
            case "next": return "Следующий трек";
            case "pause": return "Пауза";
            default: return command;
        }
    }
}
