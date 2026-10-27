package moscow.avior.john;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceCommandsTest {
    @Test public void acceptsEveryAliasWithWakeWordInSameUtterance() {
        for (java.util.Map.Entry<String,String> phrase : VoiceCommands.PHRASES.entrySet())
            assertEquals(phrase.getValue(), new VoiceCommands().accept("Джон, " + phrase.getKey() + "!", 100));
    }
    @Test public void bareCommandsNeverExecute() {
        for (String phrase : VoiceCommands.PHRASES.keySet()) assertNull(new VoiceCommands().accept(phrase, 100));
    }
    @Test public void wakeThenCommandAuthorizesOnlyOnePhrase() {
        VoiceCommands gate = new VoiceCommands();
        assertNull(gate.accept("Джон",100));
        assertTrue(gate.isArmed(200));
        assertEquals("pause",gate.accept("останови музыку",200));
        assertNull(gate.accept("следующий трек",300));
    }
    @Test public void wakeExpiresAfterFiveSeconds() {
        VoiceCommands gate = new VoiceCommands();
        gate.accept("джон", 100);
        assertNull(gate.accept("громче", 5101));
    }
    @Test public void noSubstringMatchingOrCombinedCommands() {
        VoiceCommands gate = new VoiceCommands();
        assertNull(gate.accept("джонни громче",100));
        assertNull(gate.accept("скажи джон громче",200));
        assertNull(gate.accept("джон громче и пауза",300));
    }
    @Test public void rejectsNegationsAndUnknownWords() {
        VoiceCommands gate = new VoiceCommands();
        assertNull(gate.accept("джон не выключи звук",100));
        assertNull(gate.accept("джон [unk] пауза",200));
        gate.accept("джон",300);
        assertNull(gate.accept("отмена",400));
        assertNull(gate.accept("пауза",500));
    }
    @Test public void unknownPhraseConsumesWakeWindow() {
        VoiceCommands gate = new VoiceCommands();
        gate.accept("джон",100);
        assertNull(gate.accept("какая погода",200));
        assertNull(gate.accept("громче",300));
    }
    @Test public void muteIsDistinctFromPause() {
        assertEquals("mute",new VoiceCommands().accept("Джон, отключи звук",100));
        assertEquals("pause",new VoiceCommands().accept("Джон, поставь на паузу",100));
    }
}
