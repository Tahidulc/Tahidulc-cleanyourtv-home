package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The colour theme kept on the TV: a TV that had one of the old themes comes up in Indigo • Blue; a choice is kept. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ThemeSavedTest {
    @Test public void aTvWithAnOldThemeComesUpInIndigoBlue() {
        Context c = RuntimeEnvironment.getApplication();
        SharedPreferences look = c.getSharedPreferences("look", Context.MODE_PRIVATE);
        try {
            look.edit().clear().putInt("theme", 3).commit();
            assertEquals(Theme.INDIGO, Theme.savedPrimary(c));
            assertEquals(Theme.BLUE, Theme.savedAccent(c));
            Theme.apply(c);
            assertEquals("Indigo • Blue", Theme.name());
            assertEquals(0xFF151B3F, Palette.GROUND);

            Theme.save(c, 2, 9);
            assertEquals(2, Theme.savedPrimary(c));
            assertEquals(9, Theme.savedAccent(c));
            assertFalse(look.contains("theme"));
            Theme.apply(c);
            assertEquals("Deep purple • Yellow", Theme.name());

            // Nonsense kept somehow: Indigo • Blue again.
            look.edit().putInt("primary", 40).putInt("accent", -2).commit();
            Theme.apply(c);
            assertEquals("Indigo • Blue", Theme.name());
        } finally {
            look.edit().clear().commit();
            Theme.apply(Theme.INDIGO, Theme.BLUE);
        }
    }
}
