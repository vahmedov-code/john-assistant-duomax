package moscow.avior.john;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded, traversal-safe installer. No Android dependency, so archive handling can be unit tested. */
final class ModelArchive {
    static final String INVENTORY = ".integrity";
    static final String[] REQUIRED = {"am/final.mdl", "conf/mfcc.conf", "conf/model.conf", "graph/HCLr.fst", "graph/Gr.fst"};
    static final long MAX_EXPANDED = 256L * 1024 * 1024;
    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }
    static String hash(File file) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[32768];
        try (InputStream in = new FileInputStream(file)) {
            for (int n; (n = in.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Отменено");
                digest.update(buffer, 0, n);
            }
        }
        return hex(digest.digest());
    }
    static File child(File directory, String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":"))
            throw new IOException("Недопустимое имя в модели");
        for (String part : name.split("/")) if (part.equals("..") || part.equals("."))
            throw new IOException("Выход за каталог модели");
        File result = new File(directory, name).getCanonicalFile();
        if (!result.getPath().startsWith(directory.getCanonicalPath() + File.separator))
            throw new IOException("Выход за каталог модели");
        return result;
    }
    static void extract(File zip, File directory, String rootName) throws IOException {
        if (!directory.mkdirs()) throw new IOException("Невозможно создать временную папку модели");
        Properties inventory = new Properties();
        Set<String> seen = new HashSet<>();
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[32768];
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Отменено");
                if (++entries > 2000) throw new IOException("Слишком много файлов в архиве");
                String name = entry.getName();
                if (!name.startsWith(rootName + "/")) throw new IOException("Неожиданная структура архива");
                name = name.substring(rootName.length() + 1);
                if (name.isEmpty() && entry.isDirectory()) continue;
                File file = child(directory, name);
                if (!seen.add(file.getPath()) || name.equals(INVENTORY)) throw new IOException("Повторное имя файла");
                if (entry.isDirectory()) {
                    if (!file.isDirectory() && !file.mkdirs()) throw new IOException("Не удалось создать папку");
                } else {
                    if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) throw new IOException("Нет места для модели");
                    MessageDigest digest = sha256();
                    long size = 0;
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                        for (int n; (n = in.read(buffer)) != -1;) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Отменено");
                            size += n;
                            expanded += n;
                            if (expanded > MAX_EXPANDED || size > 128L * 1024 * 1024) throw new IOException("Архив превышает лимит размера");
                            digest.update(buffer, 0, n);
                            out.write(buffer, 0, n);
                        }
                    }
                    if (size == 0) throw new IOException("Пустой файл модели");
                    inventory.setProperty(name, hex(digest.digest()));
                }
                in.closeEntry();
            }
        }
        for (String path : REQUIRED) if (!inventory.containsKey(path)) throw new IOException("В модели отсутствует " + path);
        try (OutputStream out = new FileOutputStream(new File(directory, INVENTORY))) {
            inventory.store(out, "Per-file SHA-256 from verified official model archive");
        }
    }
    static boolean quickReady(File directory) {
        if (!new File(directory, INVENTORY).isFile()) return false;
        for (String path : REQUIRED) if (!new File(directory, path).isFile()) return false;
        return true;
    }
    static void verify(File directory) throws IOException {
        if (!quickReady(directory)) throw new IOException("Модель не установлена или повреждена. Скачайте модель заново.");
        File manifest = new File(directory, INVENTORY);
        if (manifest.length() > 1024 * 1024) throw new IOException("Повреждён список файлов модели");
        Properties inventory = new Properties();
        try (InputStream in = new FileInputStream(manifest)) { inventory.load(in); }
        for (String required : REQUIRED) if (!inventory.containsKey(required)) throw new IOException("Повреждён список модели");
        for (String name : inventory.stringPropertyNames()) {
            File file = child(directory, name);
            if (!file.isFile() || !hash(file).equals(inventory.getProperty(name)))
                throw new IOException("Повреждён файл модели: " + name + ". Скачайте модель заново.");
        }
    }
    static void remove(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Не удалось прочитать временную папку");
            for (File child : children) remove(child);
        }
        if (!file.delete()) throw new IOException("Не удалось удалить временный файл модели");
    }
}
