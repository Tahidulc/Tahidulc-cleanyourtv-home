package com.gathin.tvhomephone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** An unexpected close in words to pass on: its kind and where, never its message. */
public class CrashNoteTest {
    @Test public void theKindAndWhereAreKeptButNotTheWords() {
        RuntimeException cause = new IllegalStateException("http://host/live/user/secret/123.ts");
        RuntimeException e = new RuntimeException("login=secret", cause);
        String note = CrashNote.describe("frames", e, "4.9");
        assertTrue(note.startsWith("Homefield 4.9, thread frames"));
        assertTrue(note.contains("java.lang.RuntimeException"));
        assertTrue(note.contains("caused by java.lang.IllegalStateException"));
        assertTrue(note.contains("at com.gathin.tvhomephone.CrashNoteTest.theKindAndWhereAreKeptButNotTheWords"));
        assertFalse(note.contains("secret"));
        assertFalse(note.contains("http"));
    }
}
