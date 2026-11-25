package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The family's logo's extras: the marks a channel's name carries, Up next's title, when each is due. */
public class LogoExtrasTest {

    @Test
    public void onlyTheMarksTheChannelsNameSays() {
        assertEquals(Arrays.asList("4K", "HDR10+", "DOLBY ATMOS"), LogoExtras.marks("UK: Sky Sports Main Event 4K HDR10+ Atmos"));
        assertEquals(Arrays.asList("UHD", "DOLBY VISION"), LogoExtras.marks("Sky Sports F1 UHD DV"));
        assertEquals(Arrays.asList("HD"), LogoExtras.marks("TNT Sports 1 HD"));
        assertEquals(Arrays.asList("UHD", "HDR"), LogoExtras.marks("Sky Sports Tennis UHD HDR"));
        assertEquals(Arrays.asList("HD", "5.1"), LogoExtras.marks("BBC One HD 5.1"));
        assertEquals(Arrays.asList("4K", "HDR10"), LogoExtras.marks("DAZN 1 2160p HDR10"));
        // Nothing said, nothing shown; a name merely containing the letters is not the mark.
        assertEquals(Collections.emptyList(), LogoExtras.marks("Sky Sports Main Event"));
        assertEquals(Collections.emptyList(), LogoExtras.marks("Shadow Hunters"));
        assertEquals(Collections.emptyList(), LogoExtras.marks(null));
    }

    @Test
    public void upNextsTitleWithoutTheGuidesOwnMarks() {
        assertEquals("Live Boxing: Archer v Mendes", LogoExtras.tidy("Live Boxing: Archer v Mendes"));
        assertEquals("The Football Show", LogoExtras.tidy("New: The Football Show (S)"));
        assertEquals("Match of the Day", LogoExtras.tidy("Match of the Day [HD]"));
        assertEquals("", LogoExtras.tidy(null));
    }

    @Test
    public void eachComesFiveMinutesBeforeAndOnce() {
        long now = 1_000_000L;
        assertTrue(LogoExtras.due(now + LogoExtras.AHEAD_MS, now));
        assertTrue(LogoExtras.due(now + 1_000L, now));
        assertFalse(LogoExtras.due(now + LogoExtras.AHEAD_MS + 1_000L, now));
        assertFalse(LogoExtras.due(now, now));
        assertEquals(5, LogoExtras.minutesTo(now + 4 * 60_000L + 20_000L, now));
        assertEquals(5, LogoExtras.minutesTo(now + LogoExtras.AHEAD_MS, now));
        assertEquals(1, LogoExtras.minutesTo(now + 5_000L, now));
        // The clock every five minutes, waiting while Up next or the prayer time is out.
        assertTrue(LogoExtras.clockDue(now - LogoExtras.CLOCK_EVERY_MS, now, false, false));
        assertFalse(LogoExtras.clockDue(now - 60_000L, now, false, false));
        assertFalse(LogoExtras.clockDue(now - LogoExtras.CLOCK_EVERY_MS, now, true, false));
        assertFalse(LogoExtras.clockDue(now - LogoExtras.CLOCK_EVERY_MS, now, false, true));
    }

    @Test
    public void neverWhereTheScoreboardIs() {
        assertTrue(LogoExtras.meet(new float[]{0, 0, 100, 40}, new float[]{90, 10, 300, 60}));
        assertFalse(LogoExtras.meet(new float[]{0, 0, 100, 40}, new float[]{100, 10, 300, 60}));
        assertFalse(LogoExtras.meet(new float[]{0, 0, 100, 40}, null));
        assertEquals(0f, LogoExtras.ease(0f), 0f);
        assertEquals(1f, LogoExtras.ease(1f), 0f);
    }
}
