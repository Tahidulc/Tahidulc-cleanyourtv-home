package com.gathin.homefieldtv.subs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/**
 * What the film player remembers of subtitles: each title's own choice (Off, a stream's track, a file from OpenSubtitles
 * with its timing), a series' file being one episode's, the oldest titles let go; the family's languages in order with
 * English first and never none; the timing in words.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SubsPrefsTest {
    private Context c;

    @Before public void setUp() {
        c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences(SubsPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void eachTitlesChoiceIsRemembered() throws Exception {
        assertEquals(SubsPrefs.Choice.NONE, SubsPrefs.choice(c, "p0|m|1").kind);
        SubsPrefs.remember(c, "p0|m|1", SubsPrefs.Choice.off());
        SubsPrefs.remember(c, "p0|m|2", SubsPrefs.Choice.own("eng", "English SDH"));
        OpenSubtitles.Result r = new OpenSubtitles.Result();
        r.fileId = 77;
        r.lang = "fr";
        r.release = "Glass.Harbour.2024.1080p";
        File f = new File(SubsPrefs.folder(c), "77.srt");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write("1\n00:00:01,000 --> 00:00:02,000\nBonjour\n".getBytes("UTF-8"));
        }
        SubsPrefs.Choice found = SubsPrefs.Choice.found(r, f.getPath(), "");
        found.offsetMs = -1500;
        SubsPrefs.remember(c, "p0|m|3", found);

        assertEquals(SubsPrefs.Choice.OFF, SubsPrefs.choice(c, "p0|m|1").kind);
        SubsPrefs.Choice own = SubsPrefs.choice(c, "p0|m|2");
        assertEquals(SubsPrefs.Choice.OWN, own.kind);
        assertEquals("eng", own.lang);
        assertEquals("English SDH", own.label);
        SubsPrefs.Choice back = SubsPrefs.choice(c, "p0|m|3");
        assertEquals(SubsPrefs.Choice.FOUND, back.kind);
        assertEquals(77, back.fileId);
        assertEquals("fr", back.lang);
        assertEquals(-1500, back.offsetMs);
        assertTrue(back.fileFor(""));
        // A series keeps one choice: its file is the episode's it was found for.
        SubsPrefs.Choice series = SubsPrefs.Choice.found(r, f.getPath(), "ep-1");
        SubsPrefs.remember(c, "p0|s|9", series);
        assertTrue(SubsPrefs.choice(c, "p0|s|9").fileFor("ep-1"));
        assertFalse(SubsPrefs.choice(c, "p0|s|9").fileFor("ep-2"));
        // Gone from the TV: not its file any more.
        assertTrue(f.delete());
        assertFalse(SubsPrefs.choice(c, "p0|m|3").fileFor(""));
    }

    @Test public void theOldestTitlesLetGo() {
        for (int i = 0; i < SubsPrefs.KEEP_TITLES + 5; i++) SubsPrefs.remember(c, "t" + i, SubsPrefs.Choice.off());
        assertEquals(SubsPrefs.Choice.OFF, SubsPrefs.choice(c, "t" + (SubsPrefs.KEEP_TITLES + 4)).kind);
        int kept = 0;
        for (int i = 0; i < SubsPrefs.KEEP_TITLES + 5; i++) if (SubsPrefs.choice(c, "t" + i).kind == SubsPrefs.Choice.OFF) kept++;
        assertEquals(SubsPrefs.KEEP_TITLES, kept);
    }

    @Test public void languagesEnglishFirstNeverNone() {
        assertEquals(Arrays.asList("en"), SubsPrefs.languages(c));
        SubsPrefs.setLanguage(c, "fr", true);
        SubsPrefs.setLanguage(c, "bn", true);
        assertEquals(Arrays.asList("en", "bn", "fr"), SubsPrefs.languages(c));
        SubsPrefs.setLanguage(c, "en", false);
        SubsPrefs.setLanguage(c, "bn", false);
        assertEquals(Arrays.asList("fr"), SubsPrefs.languages(c));
        SubsPrefs.setLanguage(c, "fr", false);
        assertEquals(Arrays.asList("fr"), SubsPrefs.languages(c));
        assertEquals("French", SubsPrefs.languageWords(c));
    }

    @Test public void timingAndStyleInWords() {
        assertEquals("+1.5 s", SubsPrefs.offsetWords(1500));
        assertEquals("−0.5 s", SubsPrefs.offsetWords(-500));
        assertEquals("0.0 s", SubsPrefs.offsetWords(0));
        assertEquals(SubsPrefs.MAX_OFFSET_MS, SubsPrefs.clampOffset(99_000));
        SubsPrefs.setSize(c, 9);
        SubsPrefs.setColour(c, 1);
        SubsPrefs.setBox(c, 1);
        assertEquals("Biggest  ·  Yellow  ·  Black box", SubsPrefs.styleWords(c));
    }
}
