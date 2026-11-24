package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Top 10 films and Top 10 shows, for Home's rows (and Movies' and TV Shows'): the ten most watched this week (TMDB's
 * trending lists) that the family's own playlists have, in TMDB's order; where TMDB can't be asked (the launcher gives
 * no key) or the playlists have fewer than ten of them, the rest from the playlists themselves, their best rated among
 * the newest. Only the family's own titles ever show: nothing here plays or is offered that their playlists don't have.
 *
 * <p>For a screen: {@link #films(Context)} and {@link #shows(Context)} give the row now (never wait, never read a file or
 * the network; an empty row at first), {@link #addListener(Runnable)} is told on the main thread when they change, and
 * {@link #empty(Context, boolean)} is the row's line while it has nothing. Made again off the main thread when the
 * catalogue changes and every six hours (TMDB's lists are kept that long).
 */
public final class TopTen {
    private TopTen() {}

    public static final int SIZE = 10;
    /** The empty rows' words. */
    public static final String FILMS_EMPTY = "Your playlists' films come up here", SHOWS_EMPTY = "Your playlists' TV shows come up here",
            NO_PLAYLIST = "Add your family's playlist in Settings";
    /** The rows' names. */
    public static final String FILMS = "Top 10 films", SHOWS = "Top 10 shows";
    static final long AGAIN_MS = Tmdb.TREND_MS;

    /** One row: its titles, best first, and for each whether TMDB ranked it (false: from the playlists themselves). */
    public static final class Row {
        public final List<Catalog.Title> titles;
        public final boolean[] ranked;

        Row(List<Catalog.Title> titles, boolean[] ranked) {
            this.titles = Collections.unmodifiableList(titles);
            this.ranked = ranked;
        }

        static final Row NONE = new Row(new ArrayList<>(), new boolean[0]);
    }

    private static volatile Row films = Row.NONE, shows = Row.NONE;
    private static volatile long madeAt;
    private static volatile int madeFor = -1;
    private static final AtomicBoolean making = new AtomicBoolean();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static Runnable catalogListener;

    /** The Top 10 films now (made again in the background when due). */
    public static Row films(Context c) {
        ensure(c);
        return films;
    }

    /** The Top 10 shows now. */
    public static Row shows(Context c) {
        ensure(c);
        return shows;
    }

    /** The row's line while it has nothing: no playlist yet, or its titles still to come. */
    public static String empty(Context c, boolean series) {
        if (Sources.list(c).isEmpty()) return NO_PLAYLIST;
        return series ? SHOWS_EMPTY : FILMS_EMPTY;
    }

    /** Told on the main thread when the rows change. */
    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    /** Made again off the main thread if the catalogue changed or six hours have gone by. */
    public static synchronized void ensure(Context c) {
        Context app = c.getApplicationContext();
        Catalog cat = Catalog.get(app);
        if (catalogListener == null) {
            catalogListener = () -> ensure(app);
            cat.addListener(catalogListener);
        }
        int stamp = System.identityHashCode(cat.films()) * 31 + System.identityHashCode(cat.series());
        if (stamp == madeFor && System.currentTimeMillis() - madeAt < AGAIN_MS) return;
        if (!cat.ready() || !making.compareAndSet(false, true)) return;
        Exec.io(() -> {
            try {
                List<Catalog.Title> f = cat.films(), s = cat.series();
                Row rf = make(f, Tmdb.trending(app, false)), rs = make(s, Tmdb.trending(app, true));
                films = rf;
                shows = rs;
                madeFor = stamp;
                madeAt = System.currentTimeMillis();
            } finally {
                making.set(false);
            }
            Exec.main(() -> {
                for (Runnable r : LISTENERS) r.run();
            });
        });
    }

    /**
     * Pure: a row from the playlists' titles and what is trending ({name, year, …}, most watched first): each trending
     * title the playlists have (by its name, its year a year either side where both give one), in that order; then, to make
     * ten, the playlists' own best rated among their newest.
     */
    static Row make(List<Catalog.Title> all, List<String[]> trending) {
        Map<String, List<Catalog.Title>> byName = new HashMap<>();
        if (!trending.isEmpty()) {
            Set<String> wanted = new HashSet<>();
            for (String[] t : trending) wanted.add(Catalog.key(t[0]));
            for (Catalog.Title t : all) {
                String k = Catalog.key(t.name);
                if (!wanted.contains(k)) continue;
                List<Catalog.Title> l = byName.get(k);
                if (l == null) byName.put(k, l = new ArrayList<>(2));
                l.add(t);
            }
        }
        List<Catalog.Title> out = new ArrayList<>();
        List<Boolean> ranked = new ArrayList<>();
        Set<String> had = new HashSet<>();
        for (String[] t : trending) {
            if (out.size() >= SIZE) break;
            List<Catalog.Title> l = byName.get(Catalog.key(t[0]));
            if (l == null) continue;
            Catalog.Title pick = null;
            int want = year(t[1]);
            for (Catalog.Title c : l) {
                int y = year(c.year);
                if (want == 0 || y == 0 || Math.abs(y - want) <= 1) {
                    pick = c;
                    break;
                }
            }
            if (pick == null || !had.add(pick.key)) continue;
            out.add(pick);
            ranked.add(true);
        }
        if (out.size() < SIZE) {
            for (Catalog.Title t : ownBest(all, SIZE * 3)) {
                if (out.size() >= SIZE) break;
                if (!had.add(t.key)) continue;
                out.add(t);
                ranked.add(false);
            }
        }
        boolean[] r = new boolean[ranked.size()];
        for (int i = 0; i < r.length; i++) r[i] = ranked.get(i);
        return new Row(out, r);
    }

    /** Pure: the playlists' best rated among their 300 newest (the newest first where none is rated), at most max. */
    static List<Catalog.Title> ownBest(List<Catalog.Title> all, int max) {
        List<Catalog.Title> recent = Catalog.newest(all, 300);
        List<Catalog.Title> rated = new ArrayList<>(recent);
        rated.sort((a, b) -> Double.compare(score(b), score(a)));
        return rated.subList(0, Math.min(max, rated.size()));
    }

    private static double score(Catalog.Title t) {
        try {
            return t.rating.isEmpty() ? 0 : Double.parseDouble(t.rating);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int year(String y) {
        try {
            return y == null || y.length() < 4 ? 0 : Integer.parseInt(y.substring(0, 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Previews and tests: the rows given straight in. */
    public static void useForPreview(List<Catalog.Title> f, List<Catalog.Title> s) {
        boolean[] a = new boolean[f.size()], b = new boolean[s.size()];
        java.util.Arrays.fill(a, true);
        java.util.Arrays.fill(b, true);
        films = new Row(new ArrayList<>(f), a);
        shows = new Row(new ArrayList<>(s), b);
        madeAt = System.currentTimeMillis();
    }
}
