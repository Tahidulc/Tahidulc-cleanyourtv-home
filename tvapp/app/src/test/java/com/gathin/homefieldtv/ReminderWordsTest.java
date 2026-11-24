package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.homefieldtv.engine.Reminders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The reminder's panel keeps all its words (3.9): measured as the TV measures them. Invented programmes and channels. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ReminderWordsTest {
    @Test public void itsWordsAreNeverCutTheyTakeMoreLinesAndThePanelGrows() {
        // "The reminders was cut off" (3.9): a long title and channel take more lines (the title up to three), the panel taller for them.
        Context app = RuntimeEnvironment.getApplication();
        ReminderCard.Looks.ready(app);
        long start = System.currentTimeMillis() + 10 * 60_000L;
        ReminderCard.Face.Answer none = new ReminderCard.Face.Answer() {
            @Override public void ok() {}

            @Override public void close() {}

            @Override public void again() {}
        };
        ReminderCard.Face plain = new ReminderCard.Face(app, new Reminders.Reminder("x:1", "Harbour TV", "", "The Long Tide",
                start, start + 3_600_000L), none);
        assertEquals(1, plain.titleLines());
        assertEquals(1, plain.messageLines());
        assertEquals("one line each: the panel as it was", Ui.px(ReminderCard.Face.H), plain.heightPx(), 2);
        ReminderCard.Face lng = new ReminderCard.Face(app, new Reminders.Reminder("x:2", "Kestrel Sports Premier Extra FHD", "",
                "Riverside Rovers v Harbour Athletic: Cup Final Live", start, start + 3_600_000L), none);
        assertEquals(3, lng.titleLines());
        assertEquals(2, lng.messageLines());
        assertTrue("taller for the second lines", lng.heightPx() > plain.heightPx() + Ui.px(60));
        assertTrue("still on the screen", Ui.px(56) + lng.heightPx() < Ui.px(1080 - 56));
    }

}
