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

/**
 * The colour theme kept on the TV: a TV with a choice from before the 07 look (an old theme, or a pair kept under the old
 * keys) comes up in Homefield • Amber; a choice is kept.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ThemeSavedTest {
    @Test public void aTvWithAnOlderChoiceComesUpInTheSevenLook() {
        Context c = RuntimeEnvironment.getApplication();
        SharedPreferences look = c.getSharedPreferences("look", Context.MODE_PRIVATE);
        try {
            look.edit().clear().putInt("theme", 3).putInt("primary", 2).putInt("accent", 9).commit();
            assertEquals(Theme.HOMEFIELD, Theme.savedPrimary(c));
            assertEquals(Theme.AMBER, Theme.savedAccent(c));
            Theme.apply(c);
            assertEquals("Homefield • Amber", Theme.name());
            assertEquals(0xFF0B0B0F, Palette.GROUND);

            Theme.save(c, 2, 9);
            assertEquals(2, Theme.savedPrimary(c));
            assertEquals(9, Theme.savedAccent(c));
            assertFalse(look.contains("theme"));
            assertFalse(look.contains("primary"));
            Theme.apply(c);
            assertEquals("Deep purple • Yellow", Theme.name());

            // Nonsense kept somehow: Homefield • Amber again.
            look.edit().putInt(Theme.PRIMARY_KEY, 40).putInt(Theme.ACCENT_KEY, -2).commit();
            Theme.apply(c);
            assertEquals("Homefield • Amber", Theme.name());
        } finally {
            look.edit().clear().commit();
            Theme.apply(Theme.INDIGO, Theme.BLUE);
        }
    }
}
