package com.gathin.homefieldtv;

import android.content.Context;
import android.os.SystemClock;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TMDB's extras of the titles near the remote on the Editorial pages (their logo for the card's mark, and their videos
 * for its trailer), read ahead so a card shows its logo, and plays its trailer, the moment the remote gets there ("When
 * going over a poster, it should have the text as the logo of the movie or TV show"): looked for on THREADS
 * low-priority threads of their own, the nearest first (a new window's titles to the front, the furthest let go past
 * QUEUE), never while a key is held (paused until it is let go, or HOLD_MS after the last repeat), each title once. What
 * was found is kept (TMDB's answers on the TV, Tmdb's kept extras; the last KEEP in memory here), so a title seen again,
 * on any page, has its logo at once. The logos' pictures are then read at the sizes drawn (Pics.MARK, off the main
 * thread). Main thread only, but for the threads' own work.
 */
final class MarksAhead {
    private MarksAhead() {}

    /** What a page is told once a title's extras are known (null: TMDB has none, or can't be asked), on the main thread. */
    interface Got {
        void got(Catalog.Title t, Tmdb.Extras x);
    }

    /** How a title's extras are found (kept on the TV, else asked of TMDB); tests stand in for it. */
    interface Lookup {
        Tmdb.Extras extras(Context c, Catalog.Title t);
    }

    static final int THREADS = 2, QUEUE = 48, KEEP = 400;
    /** A key held: nothing is looked for until it is let go (or this long after its last repeat). */
    static final long HOLD_MS = 400;

    /** Tests: in place of TMDB (null: as it comes). */
    static Lookup lookup;
    /** Tests: nothing looked for while set (the titles stay still to be looked for). */
    static volatile boolean previewPaused;
    /** Tests: the titles looked for, in the order they were (the last 200). */
    static final List<Catalog.Title> asked = java.util.Collections.synchronizedList(new ArrayList<>());

    /** The titles looked for (by the title itself, so a new catalogue starts afresh) and what was found (null: none). */
    private static final Map<Catalog.Title, Tmdb.Extras> known = new LinkedHashMap<Catalog.Title, Tmdb.Extras>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Catalog.Title, Tmdb.Extras> e) {
            return size() > KEEP;
        }
    };

    private static final class Job {
        final Catalog.Title title;
        final List<Got> tell = new ArrayList<>(2);

        Job(Catalog.Title t) {
            title = t;
        }
    }

    private static final Object lock = new Object();
    private static final ArrayDeque<Job> queue = new ArrayDeque<>();
    /** Being looked for now. */
    private static final List<Job> running = new ArrayList<>();
    private static long heldUntil;
    private static boolean started;
    private static Context app;

    /** Whether a title has been looked for (found or not). */
    static boolean looked(Catalog.Title t) {
        return t != null && known.containsKey(t);
    }

    /** A title's extras as found (null: not looked for yet, or none). */
    static Tmdb.Extras known(Catalog.Title t) {
        return t == null ? null : known.get(t);
    }

    /** Previews and tests: a title's extras as if found. */
    static void keep(Catalog.Title t, Tmdb.Extras x) {
        if (t != null) known.put(t, x);
    }

    /** Tests: everything forgotten, nothing waiting, nothing held. */
    static void clear() {
        synchronized (lock) {
            queue.clear();
            heldUntil = 0;
        }
        previewPaused = false;
        known.clear();
        asked.clear();
    }

    /** A key is held (true: its repeats coming) or let go: nothing is looked for while it is. */
    static void hold(boolean on) {
        synchronized (lock) {
            heldUntil = on ? SystemClock.uptimeMillis() + HOLD_MS : 0;
            lock.notifyAll();
        }
        if (!on && Exec.inline) drain();
    }

    /** Whether looking is held now. */
    static boolean held() {
        synchronized (lock) {
            return heldFor(SystemClock.uptimeMillis()) > 0;
        }
    }

    /** How much longer it is held at now (ms; 0: not held; a clock that went back, as between tests: not held). */
    private static long heldFor(long now) {
        long left = heldUntil - now;
        return left > 0 && left <= HOLD_MS ? left : 0;
    }

    /** The remote rests on this title: looked for before anything else. */
    static void first(Context c, Catalog.Title t, Got tell) {
        List<Catalog.Title> one = new ArrayList<>(1);
        one.add(t);
        want(c, one, tell);
    }

    /**
     * These titles' extras wanted, the nearest first: those known already told at once; the rest to the front of the
     * queue in their order, the furthest waiting let go past QUEUE.
     */
    static void want(Context c, List<Catalog.Title> titles, Got tell) {
        if (app == null) app = c.getApplicationContext();
        List<Catalog.Title> now = new ArrayList<>();
        synchronized (lock) {
            for (int i = titles.size() - 1; i >= 0; i--) {
                Catalog.Title t = titles.get(i);
                if (t == null) continue;
                if (known.containsKey(t)) {
                    now.add(0, t);
                    continue;
                }
                Job j = find(t);
                if (j == null) {
                    j = running(t);
                    if (j != null) {
                        if (!j.tell.contains(tell)) j.tell.add(tell);
                        continue;
                    }
                    j = new Job(t);
                } else {
                    queue.remove(j);
                }
                if (!j.tell.contains(tell)) j.tell.add(tell);
                queue.addFirst(j);
            }
            while (queue.size() > QUEUE) queue.removeLast();
            if (!Exec.inline && !started) {
                started = true;
                for (int i = 0; i < THREADS; i++) {
                    Thread th = new Thread(MarksAhead::work, "marks-" + i);
                    th.setDaemon(true);
                    th.setPriority(Thread.MIN_PRIORITY + 1);
                    th.start();
                }
            }
            lock.notifyAll();
        }
        for (Catalog.Title t : now) tell.got(t, known.get(t));
        if (Exec.inline && !held()) drain();
    }

    private static Job find(Catalog.Title t) {
        for (Job j : queue) if (j.title == t) return j;
        return null;
    }

    private static Job running(Catalog.Title t) {
        for (Job j : running) if (j.title == t) return j;
        return null;
    }

    /** Previews and tests: the queue looked through at once, on the caller's thread. */
    static void drain() {
        while (true) {
            Job j;
            synchronized (lock) {
                if (queue.isEmpty() || previewPaused || heldFor(SystemClock.uptimeMillis()) > 0) return;
                j = queue.removeFirst();
            }
            done(j, look(j.title));
        }
    }

    private static void work() {
        while (true) {
            Job j;
            synchronized (lock) {
                while (true) {
                    // Previews and tests (inline, paused): the caller's thread looks, never these.
                    long wait = previewPaused || Exec.inline ? 50 : heldFor(SystemClock.uptimeMillis());
                    if (wait <= 0 && !queue.isEmpty()) break;
                    try {
                        lock.wait(wait > 0 ? wait : 0);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                j = queue.removeFirst();
                running.add(j);
            }
            Tmdb.Extras x = null;
            try {
                x = look(j.title);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // none, quietly
            }
            Tmdb.Extras got = x;
            Exec.main(() -> {
                synchronized (lock) {
                    running.remove(j);
                }
                done(j, got);
            });
        }
    }

    /** A title's extras: kept ones, else (none, or kept from before videos were) asked of TMDB. */
    private static Tmdb.Extras look(Catalog.Title t) {
        synchronized (asked) {
            if (asked.size() >= 200) asked.clear();
            asked.add(t);
        }
        Lookup l = lookup;
        if (l != null) return l.extras(app, t);
        Tmdb.Extras x = Tmdb.keptExtras(app, t);
        if (x == null || x.old) {
            Tmdb.Extras fresh = Tmdb.extras(app, t);
            if (fresh != null) x = fresh;
        }
        return x;
    }

    /** Found: kept, its logo's pictures read ahead at the sizes the cards draw them, the pages told. */
    private static void done(Job j, Tmdb.Extras x) {
        known.put(j.title, x);
        if (x != null && !x.mark.isEmpty()) {
            Pics.prefetch(x.mark, Math.round(Ui.f(Editorial.CARD_LOGO_W)), Math.round(Ui.f(Editorial.CARD_LOGO_H)), Pics.MARK);
            Pics.prefetch(x.mark, Math.round(Ui.f(Editorial.HERO_LOGO_W)), Math.round(Ui.f(Editorial.HERO_LOGO_H)), Pics.MARK);
        }
        for (Iterator<Got> it = j.tell.iterator(); it.hasNext(); ) it.next().got(j.title, x);
    }
}
