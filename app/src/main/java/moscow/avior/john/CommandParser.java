package moscow.avior.john;
import java.util.Locale;
public final class CommandParser {
    public static String parse(String value) {
        if(value==null)return null;
        String s=value.toLowerCase(new Locale("ru","RU")).replaceAll("[^\\p{L} ]"," ").replaceAll("\\s+"," ").trim();
        s=s.replaceFirst("^(джон|john)\\s+","");
        if(s.equals("пауза")||s.equals("стоп"))return "pause";
        if(s.equals("играй")||s.equals("продолжи")||s.equals("воспроизведение"))return "play";
        if(s.equals("дальше")||s.equals("следующий")||s.equals("следующий трек"))return "next";
        if(s.equals("назад")||s.equals("предыдущий")||s.equals("предыдущий трек"))return "previous";
        if(s.equals("громче"))return "louder";
        if(s.equals("тише"))return "quieter";
        return null;
    }
}
