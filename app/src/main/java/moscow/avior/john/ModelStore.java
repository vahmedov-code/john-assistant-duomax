package moscow.avior.john;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;

/** Application-scoped download survives activity recreation; interrupted installs never become ready. */
final class ModelStore {
    static final String NAME = "vosk-model-small-ru-0.22";
    static final String URL = "https://alphacephei.com/vosk/models/" + NAME + ".zip";
    static final String SHA256 = "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4";
    static final long MAX_DOWNLOAD = 60L * 1024 * 1024;
    private static ModelStore instance;
    final File directory;
    private final File base;
    volatile boolean busy;
    volatile int progress = -1;
    volatile String status = "Модель не скачана";
    volatile String error = "";
    private volatile Thread task;

    static synchronized ModelStore get(Context context) {
        if (instance == null) instance = new ModelStore(context.getApplicationContext());
        return instance;
    }
    private ModelStore(Context context) {
        base = new File(context.getNoBackupFilesDir(), "vosk");
        directory = new File(base, NAME);
        File previous = new File(base, "previous");
        if (!directory.exists() && previous.exists()) previous.renameTo(directory);
        if (isReady()) status = "Русская модель установлена";
    }
    boolean isReady() { return ModelArchive.quickReady(directory); }
    synchronized void download() {
        if (busy || VoiceState.active) return;
        busy = true;
        error = "";
        progress = 0;
        status = "Подключение к официальному серверу Vosk…";
        task = new Thread(this::install, "john-model-download");
        task.start();
    }
    void cancel() { Thread current = task; if (current != null) current.interrupt(); }
    private void install() {
        File archive = new File(base, "download.part");
        File stage = new File(base, "unpacking");
        File previous = new File(base, "previous");
        HttpsURLConnection connection = null;
        try {
            if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Не удалось создать каталог модели");
            // Recover a previously installed model if the process died between the two renames.
            if (!directory.exists() && previous.exists() && !previous.renameTo(directory)) throw new IOException("Не удалось восстановить модель");
            ModelArchive.remove(stage);
            ModelArchive.remove(previous);
            if (base.getUsableSpace() < 220L * 1024 * 1024) throw new IOException("Для установки нужно не менее 220 МБ свободного места");
            connection = (HttpsURLConnection) new URL(URL).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IOException("Сервер Vosk: HTTP " + connection.getResponseCode());
            long expected = connection.getContentLengthLong();
            if (expected > MAX_DOWNLOAD) throw new IOException("Слишком большой архив модели");
            MessageDigest digest = ModelArchive.sha256();
            long received = 0;
            long deadline = android.os.SystemClock.elapsedRealtime() + 20 * 60 * 1000;
            byte[] buffer = new byte[32768];
            try (InputStream in = connection.getInputStream(); OutputStream out = new BufferedOutputStream(new FileOutputStream(archive))) {
                for (int n; (n = in.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Загрузка отменена");
                    if (android.os.SystemClock.elapsedRealtime() > deadline) throw new IOException("Время загрузки истекло. Повторите попытку.");
                    received += n;
                    if (received > MAX_DOWNLOAD) throw new IOException("Архив превышает лимит размера");
                    out.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                    progress = expected > 0 ? (int)(100 * received / expected) : -1;
                    status = "Скачано " + received / 1048576 + " МБ" + (progress >= 0 ? " · " + progress + "%" : "");
                }
            }
            if (expected > 0 && received != expected) throw new IOException("Архив скачан не полностью");
            if (!ModelArchive.hex(digest.digest()).equals(SHA256)) throw new IOException("SHA-256 архива не совпал. Повторите загрузку.");
            status = "Архив проверен. Распаковка…";
            progress = -1;
            ModelArchive.extract(archive, stage, NAME);
            ModelArchive.verify(stage);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Загрузка отменена");
            if (directory.exists() && !directory.renameTo(previous)) throw new IOException("Не удалось заменить модель");
            if (!stage.renameTo(directory)) {
                if (previous.exists()) previous.renameTo(directory);
                throw new IOException("Не удалось завершить установку модели");
            }
            status = "Русская модель установлена и проверена. Интернет для команд больше не нужен.";
            progress = 100;
        } catch (Exception e) {
            error = e instanceof InterruptedIOException ? "Загрузка прервана. Можно повторить."
                    : "Модель: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            status = isReady() ? "Сохранена предыдущая модель" : "Модель не установлена. Повторите загрузку.";
        } finally {
            if (connection != null) connection.disconnect();
            for (File file : new File[]{archive, stage}) {
                try { ModelArchive.remove(file); } catch (IOException ignored) { /* Retry cleans temporary files. */ }
            }
            if (isReady()) try { ModelArchive.remove(previous); } catch (IOException ignored) {}
            synchronized (this) { task = null; busy = false; }
        }
    }
}
