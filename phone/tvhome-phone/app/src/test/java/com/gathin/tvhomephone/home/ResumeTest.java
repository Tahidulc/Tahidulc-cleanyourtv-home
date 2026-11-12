package com.gathin.tvhomephone.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.live.VodHistory;
import com.gathin.tvhomephone.live.Watched;
import com.gathin.tvhomephone.watch.Title;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Continue watching's choice (Resume): what is part-way, a series' next episode, then the TVs'. */
public class ResumeTest {
    private static Watched.Entry vod(String name, boolean tv, int tmdb, long pos, long dur, long at, boolean finished) {
        VodHistory.Entry v = new VodHistory.Entry();
        v.key = name + pos;
        v.name = name;
        v.title = name;
        v.tv = tv;
        v.tmdb = tmdb;
        v.position = pos;
        v.duration = dur;
        v.at = at;
        v.plays.add(new String[]{"x:movie:1.mp4", "", "", ""});
        Watched.Entry e = new Watched.Entry();
        e.kind = tv ? Watched.EPISODE : Watched.FILM;
        e.at = at;
        e.vod = v;
        e.finished = finished;
        return e;
    }

    private static final String EPS = "[{\"s\":2,\"n\":4,\"t\":\"\",\"p\":[[\"a\",\"\",\"\",\"\"]]},"
            + "{\"s\":2,\"n\":5,\"t\":\"\",\"p\":[[\"b\",\"\",\"\",\"\"]]}]";

    @Test public void partWayFilmsAndEpisodesNewestFirst() {
        List<Watched.Entry> all = Arrays.asList(
                vod("Older film", false, 1, 30 * 60_000L, 120 * 60_000L, 1000, false),
                vod("Newer show", true, 2, 10 * 60_000L, 50 * 60_000L, 5000, false));
        List<Resume.Card> out = Resume.pick(all, new ArrayList<>(), 10);
        assertEquals(2, out.size());
        assertEquals("Newer show", out.get(0).title);
        assertEquals("Older film", out.get(1).title);
        assertEquals(0.25f, out.get(1).progress, 1e-6);
    }

    @Test public void barelyStartedAndFinishedFilmsLeftOut() {
        Watched.Entry looked = vod("Looked at", false, 3, 20_000L, 100 * 60_000L, 10, false);
        Watched.Entry done = vod("Done", false, 4, 99 * 60_000L, 100 * 60_000L, 20, true);
        Watched.Entry noLength = vod("Stream", false, 5, 5 * 60_000L, 0, 30, false);
        assertFalse(Resume.keep(looked));
        assertFalse(Resume.keep(done));
        assertTrue(Resume.keep(noLength));
        assertEquals(1, Resume.pick(Arrays.asList(looked, done, noLength), new ArrayList<>(), 10).size());
    }

    @Test public void finishedEpisodeOffersTheNext() {
        Watched.Entry e = vod("Show", true, 6, 49 * 60_000L, 50 * 60_000L, 40, true);
        e.vod.season = 2;
        e.vod.episode = 4;
        e.vod.episodes = EPS;
        assertTrue(Resume.keep(e));
        Resume.Card k = Resume.pick(Collections.singletonList(e), new ArrayList<>(), 10).get(0);
        assertEquals("NEXT · S2 E5", k.badge);
        assertEquals("Next: S2 E5", k.line);
        assertEquals(-1f, k.progress, 0);
        // The last episode watched to its end: nothing next, not in the row.
        e.vod.episode = 5;
        assertFalse(Resume.keep(e));
    }

    @Test public void episodeBadgeAndTvsAfterThePhonesOnce() {
        Watched.Entry e = vod("Show", true, 7, 20 * 60_000L, 50 * 60_000L, 50, false);
        e.vod.season = 1;
        e.vod.episode = 3;
        Title same = new Title();
        same.id = 7;
        same.tv = true;
        same.title = "Show";
        Title other = new Title();
        other.id = 8;
        other.tv = false;
        other.title = "Film on TV";
        other.progress = 0.4f;
        List<Resume.Card> out = Resume.pick(Collections.singletonList(e), Arrays.asList(same, other), 10);
        assertEquals(2, out.size());
        assertEquals("S1 E3", out.get(0).badge);
        assertEquals("Film on TV", out.get(1).title);
        assertEquals("ON TV", out.get(1).badge);
        assertNull(out.get(1).entry);
        assertEquals(1, Resume.pick(Collections.singletonList(e), Arrays.asList(same, other), 1).size());
    }
}
