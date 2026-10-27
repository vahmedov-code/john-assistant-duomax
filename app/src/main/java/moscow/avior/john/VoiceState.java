package moscow.avior.john;

/** Process-local UI state. Audio and recognized text are never written to disk. */
final class VoiceState {
    static volatile boolean active;
    static volatile boolean microphone;
    static volatile String status = "Остановлен. Запуск микрофона — только кнопкой.";
    static volatile String phrase = "—";
    static volatile String command = "—";
    static volatile String error = "";
    static volatile int peak;
    private VoiceState() {}
}
