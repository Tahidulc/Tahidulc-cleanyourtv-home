package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The helper's way through Debrid Stream to a title's page (DebridSteps), against a made-up Debrid Stream: its profile
 * picker, its home with its search, its search screen and keyboard, its results, the title's page; each step taken as
 * its screen changes. And the time from OK to the title typed and to it picked, before (a look every 220 ms, a cold
 * start every time, 1.6 s first with the helper off) and now (a step on each of its screen changes, brought to the
 * front when it was running, the app opened at once with the helper off), printed as TIMING. Invented titles.
 */
public class DebridStepsTest {
    // ---- a made-up screen -------------------------------------------------------------------------------------------

    static final class N implements DebridSteps.Node {
        String text, desc, id;
        boolean editable, focused;
        final List<N> kids = new ArrayList<>();

        N text(String t) {
            text = t;
            return this;
        }

        N desc(String d) {
            desc = d;
            return this;
        }

        N id(String i) {
            id = i;
            return this;
        }

        N edit() {
            editable = true;
            return this;
        }

        N focus() {
            focused = true;
            return this;
        }

        N add(N... ns) {
            kids.addAll(Arrays.asList(ns));
            return this;
        }

        @Override public CharSequence text() {
            return text;
        }

        @Override public CharSequence desc() {
            return desc;
        }

        @Override public String id() {
            return id;
        }

        @Override public boolean editable() {
            return editable;
        }

        @Override public boolean focused() {
            return focused;
        }

        @Override public int count() {
            return kids.size();
        }

        @Override public DebridSteps.Node child(int i) {
            return kids.get(i);
        }
    }

    static final String D = "com.debridstream.tv:id/";

    static N profiles() {
        return new N().add(new N().text("Who's watching?"), new N().desc("Family").focus(), new N().desc("Kids"));
    }

    static N home() {
        return new N().add(new N().id(D + "btn_search").desc("Search"), new N().text("Continue Watching"), new N().desc("Harbour Lights"));
    }

    static N titlePage(String title) {
        return new N().add(new N().text(title), new N().text("Play"), new N().text("More episodes"));
    }

    static N search(String typed, boolean results) {
        N root = new N().add(new N().edit().text(typed));
        if (results) {
            root.add(new N().text("Movies"), new N().desc("The Long Tide Returns"), new N().desc("Long Tide"),
                    new N().text("TV Shows"), new N().desc("The Long Tide"), new N().desc("Tide Pool"));
        }
        return root;
    }

    private static final long T = 1_000_000L;

    // ---- the steps -----------------------------------------------------------------------------------------------------

    @Test public void fromItsProfilesToTheTitlesPage() {
        DebridSteps s = new DebridSteps("The Long Tide", true, T, false);
        // Started afresh: its profile picker; OK on the one it highlights.
        DebridSteps.Step p = s.next(profiles(), false, T + 1_500);
        assertEquals(DebridSteps.PROFILE, p.what);
        assertEquals("Family", p.node.desc().toString());
        // The same picker again a moment later (its home on its way): not pressed twice.
        assertEquals(DebridSteps.WAIT, s.next(profiles(), false, T + 1_700).what);
        // Its home: its search, by its id.
        DebridSteps.Step o = s.next(home(), false, T + 2_200);
        assertEquals(DebridSteps.OPEN_SEARCH, o.what);
        assertEquals(D + "btn_search", o.node.id());
        assertEquals(DebridSteps.WAIT, s.next(home(), false, T + 2_400).what);
        // Its search: the title typed.
        DebridSteps.Step ty = s.next(search("", false), false, T + 2_600);
        assertEquals(DebridSteps.TYPE, ty.what);
        assertEquals("The Long Tide", ty.text);
        s.typed(T + 2_600);
        // The keyboard over the results: put away first.
        assertEquals(DebridSteps.HIDE_KEYBOARD, s.next(search("The Long Tide", false), true, T + 2_800).what);
        // No results yet: waits.
        assertEquals(DebridSteps.WAIT, s.next(search("The Long Tide", false), false, T + 2_900).what);
        // The series of that name, in TV Shows (not the film that begins the same, nor the film "Long Tide").
        DebridSteps.Step pick = s.next(search("The Long Tide", true), false, T + 3_300);
        assertEquals(DebridSteps.PICK, pick.what);
        assertEquals("The Long Tide", pick.node.desc().toString());
        // Its page once the search gives way.
        assertEquals(DebridSteps.WAIT, s.next(search("The Long Tide", true), false, T + 3_400).what);
        assertEquals(DebridSteps.PAGE, s.next(titlePage("The Long Tide"), false, T + 3_800).what);
    }

    @Test public void aFilmInMoviesAndOtherSpellingsWhileNothingMatches() {
        DebridSteps film = new DebridSteps("Long Tide", false, T, false);
        film.typed(T);
        DebridSteps.Step pick = film.next(search("Long Tide", true), false, T + 500);
        assertEquals("Long Tide", pick.node.desc().toString());
        // Nothing of that name: after RESPELL_MS its next spelling ("Dead-End Job" as "Dead End Job"), then none.
        DebridSteps s = new DebridSteps("Dead-End Job", false, T, false);
        assertEquals("Dead-End Job", s.next(search("", false), false, T).text);
        s.typed(T);
        assertEquals(DebridSteps.WAIT, s.next(search("Dead-End Job", true), false, T + 1_000).what);
        DebridSteps.Step again = s.next(search("Dead-End Job", true), false, T + DebridSteps.RESPELL_MS + 100);
        assertEquals(DebridSteps.TYPE, again.what);
        assertEquals("Dead End Job", again.text);
        s.typed(T + 4_000);
        assertEquals(DebridSteps.NOT_FOUND, s.next(search("Dead End Job", true), false, T + 4_000 + DebridSteps.NOT_FOUND_MS).what);
    }

    @Test public void broughtToTheFrontItsSearchFromWhereItWasElseAfresh() {
        // Running on its home: its search at once, no profiles, nothing started again.
        DebridSteps onHome = new DebridSteps("The Long Tide", true, T, false);
        assertEquals(DebridSteps.OPEN_SEARCH, onHome.next(home(), false, T + 250).what);
        assertTrue(onHome.warm);
        // Running on a title's page with no search: a moment for it to settle, then started afresh (never Back).
        DebridSteps onPage = new DebridSteps("The Long Tide", true, T, false);
        assertEquals(DebridSteps.WAIT, onPage.next(titlePage("Harbour Lights"), false, T + 250).what);
        // Looked at again just as its moment is up, not at the next safety look.
        assertEquals(DebridSteps.WARM_GIVE_MS + 1, onPage.lookAgainIn(T + 250));
        assertEquals(DebridSteps.WAIT, onPage.next(titlePage("Harbour Lights"), false, T + 500).what);
        assertEquals(DebridSteps.RESTART, onPage.next(titlePage("Harbour Lights"), false, T + 250 + DebridSteps.WARM_GIVE_MS + 1).what);
        // A cold start (or one started afresh on purpose) is never started again: its splash has no search either.
        DebridSteps fresh = new DebridSteps("The Long Tide", true, T, true);
        assertEquals(DebridSteps.WAIT, fresh.next(titlePage("Harbour Lights"), false, T + 250).what);
        assertEquals(DebridSteps.WAIT, fresh.next(titlePage("Harbour Lights"), false, T + 5_000).what);
        assertEquals(DebridSteps.SAFETY_MS, fresh.lookAgainIn(T + 5_000));
        onPage.restarted(T + 1_100);
        // Started afresh: its splash (nothing yet) is waited for, never started again.
        assertEquals(DebridSteps.WAIT, onPage.next(new N(), false, T + 3_000).what);
        assertEquals(DebridSteps.PROFILE, onPage.next(profiles(), false, T + 3_500).what);
        // Started cold (its first screen seconds after it was asked): its splash is waited for, never started again.
        DebridSteps cold = new DebridSteps("The Long Tide", true, T, false);
        assertEquals(DebridSteps.WAIT, cold.next(new N(), false, T + 1_200).what);
        assertEquals(DebridSteps.WAIT, cold.next(new N(), false, T + 5_000).what);
    }

    @Test public void findsWhatItLooksFor() {
        N root = new N().add(new N().add(new N().text("Speak to search")), new N().desc("Search"));
        assertEquals("Search", DebridSteps.find(root, DebridSteps.SEARCH, 0).desc().toString());
        assertNull(DebridSteps.find(home(), DebridSteps.EDIT, 0));
        assertNotNull(DebridSteps.find(profiles(), DebridSteps.PROFILE_PAGE, 0));
        assertEquals("Family", DebridSteps.find(profiles(), DebridSteps.FOCUSED, 0).desc().toString());
    }

    // ---- the time from OK to typed and picked ---------------------------------------------------------------------------

    /**
     * A made-up Debrid Stream on a TV: cold, its profile picker COLD_MS after it is asked to open (nothing of it before);
     * running, the screen it was left on WARM_FIRST_MS after; then each screen a moment after what brings it on.
     */
    static final class FakeDebrid {
        static final long COLD_MS = 1_800, WARM_FIRST_MS = 250, HOME_MS = 700, SEARCH_MS = 350, KEYBOARD_MS = 150, RESULTS_MS = 600,
                HIDE_MS = 100, PAGE_MS = 500;
        N screen;
        boolean keyboard;
        /** Changes to come: {at, screen, keyboard (1/0/-1 as it is)}. */
        final List<Object[]> coming = new ArrayList<>();
        final List<Long> changes = new ArrayList<>();

        FakeDebrid(long at, String leftOn) {
            if (leftOn == null) later(at + COLD_MS, profiles(), -1);
            else later(at + WARM_FIRST_MS, leftOn.equals("home") ? home() : titlePage(leftOn), -1);
        }

        void later(long at, N s, int kb) {
            coming.add(new Object[]{at, s, kb});
            changes.add(at);
        }

        void now(long t) {
            coming.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            while (!coming.isEmpty() && (Long) coming.get(0)[0] <= t) {
                Object[] c = coming.remove(0);
                if (c[1] != null) screen = (N) c[1];
                int kb = (Integer) c[2];
                if (kb >= 0) keyboard = kb == 1;
            }
        }

        /** The next change after t, or Long.MAX_VALUE. */
        long next(long t) {
            long n = Long.MAX_VALUE;
            for (Object[] c : coming) if ((Long) c[0] > t) n = Math.min(n, (Long) c[0]);
            return n;
        }

        void act(DebridSteps.Step s, String query, long t) {
            switch (s.what) {
                case DebridSteps.PROFILE: later(t + HOME_MS, home(), -1); break;
                case DebridSteps.OPEN_SEARCH: later(t + SEARCH_MS, search("", false), 0); break;
                case DebridSteps.TYPE: later(t + KEYBOARD_MS, null, 1); later(t + RESULTS_MS, search(query, true), -1); break;
                case DebridSteps.HIDE_KEYBOARD: later(t + HIDE_MS, null, 0); break;
                case DebridSteps.PICK: later(t + PAGE_MS, titlePage(query), 0); break;
                case DebridSteps.RESTART: coming.clear(); screen = null; later(t + COLD_MS, profiles(), 0); break;
                default: break;
            }
        }
    }

    /** {typed, picked} ms after OK. */
    static long[] run(boolean events, long beginAt, long launchAt, String leftOn) {
        String q = "The Long Tide";
        FakeDebrid app = new FakeDebrid(launchAt, leftOn);
        // The helper off when OK was pressed: Debrid Stream started afresh on purpose (now), as every search was (before).
        DebridSteps s = new DebridSteps(q, true, launchAt, !events || beginAt > launchAt);
        long typed = -1, picked = -1;
        // The first look: at once now (debridBegin), STEP_MS after the search started before (start).
        long t = beginAt + (events ? 0 : 220);
        for (int guard = 0; guard < 400 && t < 30_000; guard++) {
            app.now(t);
            DebridSteps.Step st = app.screen == null ? DebridSteps.WAITING : s.next(app.screen, app.keyboard, t);
            if (st.what == DebridSteps.TYPE) { s.typed(t); if (typed < 0) typed = t; }
            if (st.what == DebridSteps.PICK) picked = t;
            if (st.what == DebridSteps.RESTART) s.restarted(t);
            if (st.what == DebridSteps.PAGE || st.what == DebridSteps.NOT_FOUND) break;
            app.act(st, q, t);
            if (!events) {
                t += 220;
            } else {
                // The next step: as the screen next changes (told within the helper's 50 ms), no sooner than
                // KICK_GAP_MS after this one; else the safety net's look.
                long change = app.next(t);
                long told = change == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(change + 50, t + AppSearchService.KICK_GAP_MS);
                t = Math.min(told, t + s.lookAgainIn(t));
            }
        }
        return new long[]{typed, picked};
    }

    @Test public void fromOkToTypedAndPicked() {
        // Before: the helper on, the app started afresh (a cold start every time) and looked at every 220 ms; the helper
        // off, 1.6 s first, then the same. Now: the app brought to the front as it was, each screen looked at as it
        // changes; the helper off, the app opened at once and its search taken up as the helper comes back (1.2 s here).
        long[] beforeOn = run(false, 0, 0, null), beforeOff = run(false, 1_600, 1_600, null);
        long[] nowCold = run(true, 0, 0, null), nowHome = run(true, 0, 0, "home"), nowPage = run(true, 0, 0, "Harbour Lights"),
                nowOff = run(true, 1_200, 0, null);
        System.out.println("TIMING Debrid Stream, OK to typed / picked (ms): before, helper on " + beforeOn[0] + " / " + beforeOn[1]
                + ", helper off " + beforeOff[0] + " / " + beforeOff[1] + "; now, not running " + nowCold[0] + " / " + nowCold[1]
                + ", running on its home " + nowHome[0] + " / " + nowHome[1] + ", running on a title's page " + nowPage[0] + " / "
                + nowPage[1] + ", helper off " + nowOff[0] + " / " + nowOff[1]);
        for (long[] r : new long[][]{beforeOn, beforeOff, nowCold, nowHome, nowPage, nowOff}) {
            assertTrue(r[0] > 0);
            assertTrue(r[1] > r[0]);
        }
        // Each screen taken as it comes: sooner than the 220 ms looks, cold or not.
        assertTrue(nowCold[1] < beforeOn[1]);
        assertTrue(nowOff[1] < beforeOff[1]);
        // Running: its search from where it was, no cold start.
        assertTrue(nowHome[1] < nowCold[1] - 1_500);
        // Left on a title's page: started afresh after WARM_GIVE_MS, a little after a cold start, never much.
        assertTrue(nowPage[1] - nowCold[1] < 1_000);
        assertTrue(nowPage[1] - beforeOn[1] < 500);
    }

    @Test public void nothingWaitsASetTimeWhileTheScreenChanges() {
        // Each change is taken within the helper's 50 ms and KICK_GAP_MS: never a whole safety look later.
        FakeDebrid app = new FakeDebrid(0, "home");
        DebridSteps s = new DebridSteps("The Long Tide", true, 0, false);
        long t = 0;
        boolean page = false;
        List<long[]> lag = new ArrayList<>();
        for (int i = 0; i < 60 && t < 10_000; i++) {
            app.now(t);
            DebridSteps.Step st = app.screen == null ? DebridSteps.WAITING : s.next(app.screen, app.keyboard, t);
            if (st.what == DebridSteps.TYPE) s.typed(t);
            if (st.what == DebridSteps.PAGE) { page = true; break; }
            app.act(st, "The Long Tide", t);
            long change = app.next(t);
            long next = change == Long.MAX_VALUE ? t + DebridSteps.SAFETY_MS : Math.min(Math.max(change + 50, t + AppSearchService.KICK_GAP_MS), t + DebridSteps.SAFETY_MS);
            if (change != Long.MAX_VALUE) lag.add(new long[]{change, next});
            t = next;
        }
        assertTrue(page);
        for (long[] l : lag) assertTrue(l[1] - l[0] <= Math.max(50, AppSearchService.KICK_GAP_MS));
    }
}
