package moscow.avior.john;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact phrases only: never execute substrings or partial recognition results. */
public final class VoiceCommands {
    public static final Map<String, String> PHRASES;
    static {
        Map<String, String> phrases = new LinkedHashMap<>();
        add(phrases, "louder", "громче", "сделай громче", "увеличь громкость", "добавь громкость");
        add(phrases, "quieter", "тише", "сделай тише", "уменьши громкость", "убавь громкость");
        add(phrases, "mute", "выключи звук", "отключи звук", "без звука", "убери звук");
        add(phrases, "next", "следующий трек", "следующая песня", "следующая композиция", "переключи трек", "дальше");
        add(phrases, "pause", "пауза", "останови музыку", "поставь на паузу", "приостанови музыку", "стоп");
        PHRASES = Collections.unmodifiableMap(phrases);
    }
    private static void add(Map<String, String> map, String command, String... phrases) {
        for (String phrase : phrases) map.put(phrase, command);
    }
    private long armedUntil;

    public String accept(String text, long nowMs) {
        if (text == null || text.trim().isEmpty()) return null;
        String normalized = text.toLowerCase(java.util.Locale.ROOT)
                .replace('ё', 'е').replaceAll("[^\\p{L} ]", " ").replaceAll("\\s+", " ").trim();
        if (text.contains("[unk]") || normalized.matches(".*\\b(не|нет|отмена)\\b.*")) {
            armedUntil = 0;
            return null;
        }
        if (normalized.equals("джон") || normalized.equals("john")) {
            armedUntil = nowMs + 5000;
            return null;
        }
        boolean addressed = normalized.startsWith("джон ") || normalized.startsWith("john ");
        if (addressed) normalized = normalized.substring(normalized.indexOf(' ') + 1);
        boolean allowed = addressed || (armedUntil > 0 && nowMs <= armedUntil);
        armedUntil = 0; // One wake word authorizes at most one final phrase.
        return allowed ? PHRASES.get(normalized) : null;
    }
    public boolean isArmed(long nowMs) { return armedUntil > 0 && nowMs <= armedUntil; }
}
