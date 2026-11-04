package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class WatchRulesTest {
    private static final long MIN = 60_000L;

    @Test
    public void aFilmIsInItsCreditsInTheLastSixPerCentOrFourMinutes() {
        // Two hours: 6% is 7.2 minutes, more than four.
        assertEquals(120 * MIN - (long) (120 * MIN * 0.06), WatchRules.creditsAt(120 * MIN));
        // An hour and ten: 6% is 4.2 minutes.
        assertEquals(70 * MIN - (long) (70 * MIN * 0.06), WatchRules.creditsAt(70 * MIN));
        long dur = 132 * MIN, credits = WatchRules.creditsAt(dur);
        assertTrue(WatchRules.filmFinished(dur, credits, 10 * MIN));
        assertFalse("just before the credits", WatchRules.filmFinished(dur, credits - 1_000L, 10 * MIN));
        assertFalse("opened at the end", WatchRules.filmFinished(dur, credits + MIN, credits - 5 * MIN));
        assertFalse("never seen playing", WatchRules.filmFinished(dur, credits, -1));
        assertFalse("too short for a film", WatchRules.filmFinished(60 * MIN, 59 * MIN, 0));
    }

    @Test
    public void anEpisodeEndsInItsLastFivePerCentOrMinuteAndAHalf() {
        long dur = 58 * MIN, end = WatchRules.episodeEndAt(dur);
        assertEquals(dur - (long) (dur * 0.05), end);
        assertEquals(20 * MIN - 90_000L, WatchRules.episodeEndAt(20 * MIN));
        assertTrue(WatchRules.episodeFinished(dur, end, 2 * MIN));
        assertFalse(WatchRules.episodeFinished(dur, end - 1_000L, 2 * MIN));
        assertFalse("a film is not an episode", WatchRules.episodeFinished(90 * MIN, 89 * MIN, 0));
        assertFalse("a clip", WatchRules.episodeFinished(10 * MIN, 9 * MIN, 0));
    }

    @Test
    public void theCardsLeaveOutMusicLiveTvAndShortClips() {
        assertTrue(WatchRules.watchable("com.netflix.ninja", 119 * MIN, false));
        assertFalse(WatchRules.watchable("ar.tvplayer.tv", 119 * MIN, true));
        assertFalse(WatchRules.watchable("com.spotify.tv.android", 40 * MIN, false));
        assertFalse(WatchRules.watchable("com.google.android.youtube.tv", 12 * MIN, false));
        assertTrue(WatchRules.watchable("com.google.android.youtube.tv", 25 * MIN, false));
        assertFalse(WatchRules.watchable("com.disney.disneyplus", 10 * MIN, false));
        assertFalse(WatchRules.watchable("com.gathin.tvhome", 100 * MIN, false));
    }

    @Test
    public void aPauseNeedsTenSeconds() {
        assertFalse(WatchRules.pauseDue(0, 50_000L));
        assertFalse(WatchRules.pauseDue(40_000L, 49_999L));
        assertTrue(WatchRules.pauseDue(40_000L, 50_000L));
    }

    @Test
    public void theWordsOnThePauseCard() {
        long dur = 119 * MIN, pos = 72 * MIN + 30_000L;
        assertEquals("1h 12m in", WatchRules.inLine(pos));
        assertEquals("47 min left", WatchRules.leftLine(pos, dur));
        assertEquals("8 min in", WatchRules.inLine(8 * MIN + 59_000L));
        assertEquals("1h 05m left", WatchRules.leftLine(0, 65 * MIN));
        // 21:20 plus 46.5 minutes, rounded up: 22:07.
        int end = WatchRules.endMinute(21 * 60 + 20, dur - pos);
        assertEquals("22:07", WatchRules.clock(end));
        assertEquals("53 min before bed", WatchRules.bedLine(WatchRules.beforeBed(end, 23 * 60)));
        assertEquals("20 min past bed", WatchRules.bedLine(WatchRules.beforeBed(23 * 60 + 20, 23 * 60)));
        // Past midnight is the same night, before or after a bed at half past twelve.
        assertEquals(-30, WatchRules.beforeBed(60, 30));
        assertEquals(90, WatchRules.beforeBed(23 * 60, 30));
        assertEquals("1h 30m before bed", WatchRules.bedLine(90));
        assertEquals("2h 12m", WatchRules.length(132 * MIN));
        assertEquals("47 min", WatchRules.length(47 * MIN));
        assertEquals(23 * 60, WatchRules.minuteOf("23:00", 0));
        assertEquals(7, WatchRules.minuteOf("nonsense", 7));
    }

    @Test
    public void thePosterSaysWhatItShares() {
        assertEquals("Crime · Mystery", WatchRules.sharedGenres(Arrays.asList("Crime", "Mystery"), Arrays.asList("Mystery", "Crime", "Drama")));
        assertEquals("Drama", WatchRules.sharedGenres(Arrays.asList("Comedy", "Drama"), Arrays.asList("Drama")));
        assertEquals("Action", WatchRules.sharedGenres(Arrays.asList("Action", "Thriller"), Arrays.asList("Action & Adventure")));
        assertEquals("Science Fiction", WatchRules.sharedGenres(Arrays.asList("Science Fiction"), Arrays.asList("Sci-Fi & Fantasy")));
        // Nothing shared: its own first genre.
        assertEquals("Horror", WatchRules.sharedGenres(Arrays.asList("Horror", "Thriller"), Arrays.asList("Animation")));
        assertEquals("", WatchRules.sharedGenres(Collections.<String>emptyList(), Arrays.asList("Drama")));
    }

    @Test
    public void aRatingIsKeptAndReadBack() {
        String s = WatchRules.encode(WatchRules.LOVED, 1_700_000_000_000L, "Animation|Family", "Wildwood");
        Object[] r = WatchRules.decode(s);
        assertEquals(WatchRules.LOVED, r[0]);
        assertEquals(1_700_000_000_000L, r[1]);
        assertEquals("Animation/Family", r[2]);
        assertEquals("Wildwood", r[3]);
        // A name with the separator in it survives.
        assertEquals("Crime | Punishment", WatchRules.decode(WatchRules.encode(WatchRules.NOT_FOR_ME, 1L, "", "Crime | Punishment"))[3]);
        assertNull(WatchRules.decode("5|1|x|y"));
        assertNull(WatchRules.decode("nonsense"));
        assertNull(WatchRules.decode(null));
        assertEquals("Not for me", WatchRules.word(WatchRules.NOT_FOR_ME));
        assertEquals("Liked it", WatchRules.word(WatchRules.LIKED));
    }
}
