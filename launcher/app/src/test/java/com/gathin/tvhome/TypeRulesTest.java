package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TypeRulesTest {
    @Test public void aCapitalAtTheStartAndAfterAStop() {
        assertTrue(TypeRules.upperNext("", false));
        assertTrue(TypeRules.upperNext("   ", false));
        assertTrue(TypeRules.upperNext("On my way. ", false));
        assertTrue(TypeRules.upperNext("Really? ", false));
        assertTrue(TypeRules.upperNext("Yes! ", false));
    }

    @Test public void noCapitalInTheMiddle() {
        assertFalse(TypeRules.upperNext("On my", false));
        assertFalse(TypeRules.upperNext("On my ", false));
        // Straight after the stop, before the space: still the same sentence's end.
        assertFalse(TypeRules.upperNext("Done.", false));
        assertFalse(TypeRules.upperNext("e.g. ok,", false));
    }

    @Test public void shiftMakesTheNextOneACapital() {
        assertTrue(TypeRules.upperNext("on my ", true));
    }

    @Test public void lettersAreCasedOtherKeysAreNot() {
        assertEquals("O", TypeRules.cased("", "o", false));
        assertEquals("n", TypeRules.cased("O", "n", false));
        assertEquals("W", TypeRules.cased("on my ", "w", true));
        assertEquals("5", TypeRules.cased("", "5", false));
        assertEquals("👍", TypeRules.cased("", "👍", false));
        assertEquals("ok", TypeRules.cased("", "ok", false));
        assertTrue(TypeRules.letter("a"));
        assertFalse(TypeRules.letter("1"));
        assertFalse(TypeRules.letter("ab"));
        assertFalse(TypeRules.letter(null));
    }

    @Test public void aMessageStopsAtItsLength() {
        String nine = "123456789";
        assertTrue(TypeRules.fits(nine, "0", 10));
        assertFalse(TypeRules.fits(nine, "01", 10));
        assertFalse(TypeRules.fits(nine + "0", "!", 10));
    }

    @Test public void deleteTakesOneCharacterOff() {
        assertEquals(0, TypeRules.deleted(""));
        assertEquals(0, TypeRules.deleted("a"));
        assertEquals(2, TypeRules.deleted("Hi!"));
    }

    @Test public void deleteTakesAnEmojiOffWhole() {
        // 👍 is two chars (a surrogate pair); ❤️ the heart and a variation sign.
        assertEquals(2, TypeRules.deleted("OK👍"));
        assertEquals(2, TypeRules.deleted("OK❤️"));
        assertEquals(0, TypeRules.deleted("😂"));
        assertEquals(0, TypeRules.deleted("️"));
    }
}
