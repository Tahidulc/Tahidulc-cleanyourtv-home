package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.InputStream;

/**
 * Live TV's guide font (the family's pick, 25 Nov: Plus Jakarta Sans): the variable font is in the app's assets with its
 * licence beside the others, each weight is built once, and the guide's writing takes it: titles bold and a touch
 * tighter, names semibold, the rest regular.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GuideFontTest {
    private static Context c() {
        return RuntimeEnvironment.getApplication();
    }

    @Test public void theFontIsInTheAppsAssets() throws Exception {
        try (InputStream in = c().getAssets().open("fonts/plusjakartasans.ttf")) {
            byte[] head = new byte[4];
            assertEquals(4, in.read(head));
            // A TrueType font: 00 01 00 00.
            assertEquals(0, head[0]);
            assertEquals(1, head[1]);
        }
    }

    @Test public void itsLicenceIsListed() throws Exception {
        File f = new File("FONT-LICENSE-OFL.txt");
        if (!f.exists()) f = new File("../FONT-LICENSE-OFL.txt");
        String s = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(s.contains("Plus Jakarta Sans Project Authors"));
        assertTrue(s.contains("fonts/plusjakartasans.ttf"));
    }

    @Test public void eachWeightIsBuiltOnce() {
        assertNotNull(TvLook.guide(c(), TvLook.TITLE));
        assertSame(TvLook.guide(c(), TvLook.TITLE), TvLook.guide(c(), TvLook.TITLE));
        assertSame(TvLook.guide(c(), TvLook.WORDS), TvLook.guide(c(), TvLook.WORDS));
    }

    @Test public void titlesAreBoldAndTighterTheRestAsSet() {
        TextView title = TvLook.guideText(c(), "Super Sunday", 15.5f, 0xFFFFFFFF, TvLook.TITLE);
        assertSame(TvLook.guide(c(), TvLook.TITLE), title.getTypeface());
        assertEquals(TvLook.TITLE_TRACK, title.getLetterSpacing(), 1e-6f);
        TextView words = TvLook.guideText(c(), "20:00 – 22:30", 12, 0xFFFFFFFF, TvLook.WORDS);
        assertSame(TvLook.guide(c(), TvLook.WORDS), words.getTypeface());
        assertEquals(0f, words.getLetterSpacing(), 1e-6f);
    }
}
