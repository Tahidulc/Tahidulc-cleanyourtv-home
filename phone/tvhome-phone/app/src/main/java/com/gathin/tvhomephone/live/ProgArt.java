package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.broadcast.PicSources;
import com.gathin.broadcast.SkyListing;
import com.gathin.tvhomephone.watch.Tmdb;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A programme's picture for the guide's cells and its panel, in the order Homefield TV takes (PicSources, shared):
 *
 * <pre>
 *   1 the guide's own        its {@code <icon src>} or {@code <image>} (at once, nothing looked for)
 *   2 Sky's own              Sky's programme image for a channel in Sky's guide (SkyPics: Sky Sports, BBC One, Dave...),
 *                            by the channel and the time, as Homefield TV's guide has it
 *   3 TMDB's by its title    ArtQuery: a film as a film with its year, a series as a series, matched with care
 *                            (PicQuery); not for a game, a live event or a bulletin
 *   4 the one always there   its game's (its clubs' crests on their colours), else its kind's (football, news, film,
 *                            kids...: PicSources.kindOf from its words, the guide's categories and the channel; drawn by
 *                            PicArt), else "" (the channel's own: its logo on its colour, drawn by the guide)
 * </pre>
 *
 * What is found comes as one chain of addresses, best first (PicSources.chain): each place takes the first that is large
 * enough for it (ProgPics: never enlarged past 1.5 times). Sky and TMDB are looked for off the main thread, one programme
 * at a time, TMDB a little apart (SPACING_MS), the newest asked first and only a screenful waiting, so scrolling the guide
 * never floods them. What TMDB found, or that it found nothing, is kept in memory and in the app's files (art.tsv), so a
 * title is looked for once; Sky's days are kept in memory (SkyPics).
 *
 * <pre>
 *   String url = ProgArt.of(c, ch, schedule, i);   main thread: the address (a chain), "" for the channel's own, null
 *                                                  while it is looked for
 *   ProgArt.listen(redraw) / unlisten(redraw)      told (main thread) as looks finish, a few together
 * </pre>
 */
final class ProgArt {
    private ProgArt() {}

    /** Between two TMDB searches, the most waiting (the rest let go, asked again when drawn), the most kept. */
    static final long SPACING_MS = 350;
    static final int WAITING_MAX = 30, KEEP_MAX = 6000, ASKS_MAX = 3000;
    /** TMDB's picture size: enough for the panel across a phone, read smaller for a cell. */
    static final String SIZE = "w780";

    /** One title's TMDB search (shared by every programme of it): its picture, null until known ("" for none). */
    static final class Ask {
        final ArtQuery q;
        final String key;
        volatile String url;

        Ask(ArtQuery q, String key) {
            this.q = q;
            this.key = key;
        }
    }

    /** One programme's look: its channel and title, when it is on (for Sky), its title's search and the one always there. */
    static final class Look {
        final String channel, title, always;
        final long at;
        final Ask ask;
        volatile String url;
        boolean queued;

        Look(String channel, String title, long at, Ask ask, String always) {
            this.channel = channel;
            this.title = title;
            this.at = at;
            this.ask = ask;
            this.always = always;
        }
    }

    private static final Object LOCK = new Object();
    private static final Map<String, Ask> ASKS = new LinkedHashMap<String, Ask>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Ask> e) {
            return size() > ASKS_MAX;
        }
    };
    /** What each search found (TMDB's picture path, "" for nothing), kept on disk. */
    private static final Map<String, String> FOUND = new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > KEEP_MAX;
        }
    };
    private static final ArrayDeque<Look> QUEUE = new ArrayDeque<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "prog-art");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean running, loaded;
    private static int unsaved;
    private static volatile boolean told;
    private static long lastSearch;
    /** The application's context (never an activity's). */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static volatile Context app;
    private static final Runnable TELL = () -> {
        told = false;
        for (Runnable r : LISTENERS) r.run();
    };

    static void listen(Runnable r) {
        if (!LISTENERS.contains(r)) LISTENERS.add(r);
    }

    static void unlisten(Runnable r) {
        LISTENERS.remove(r);
    }

    /**
     * Programme i's picture: its address (a chain), "" for the channel's own, or null while it is looked for. Main
     * thread; works a programme out the first time it is asked about, then only reads.
     */
    static String of(Context c, Channel ch, Guide.Schedule s, int i) {
        if (s == null || i < 0 || i >= s.start.length) return "";
        Object[] art = s.art;
        if (art == null || art.length != s.start.length) s.art = art = new Object[s.start.length];
        Object a = art[i];
        if (a == null) art[i] = a = resolve(c, ch, s, i);
        if (a instanceof String) return (String) a;
        Look k = (Look) a;
        String u = k.url;
        if (u != null) {
            art[i] = u;
            return u;
        }
        ask(k);
        return null;
    }

    /**
     * Programme i's picture that is always there whatever the size it is drawn at (pure but for the clubs' table): its
     * game's (two clubs the apps know: their crests on their colours), else its kind's, else "" (the channel's own).
     */
    static String fallback(Channel ch, Guide.Schedule s, int i) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        String title = s.title[i], desc = s.desc == null ? "" : s.desc[i];
        String kind = PicSources.kindOf(ch.name, ch.group, title, desc, s.category(i));
        String game = PicSources.fixtureOf(title, PicSources.sporting(kind) || TileKind.sportsChannel(ch.name, ch.group));
        if (game != null) return game;
        return kind != null ? PicSources.genre(kind) : "";
    }

    private static Object resolve(Context c, Channel ch, Guide.Schedule s, int i) {
        String own = s.pic(i);
        if (ch == null) return own;
        String always = fallback(ch, s, i);
        // The guide's own picture: at once, the one always there after it (a place it is too small for takes that).
        if (!own.isEmpty()) return PicSources.chain(own, always);
        if (app == null) app = c.getApplicationContext();
        ArtQuery q = ArtQuery.of(ch.name, ch.group, s.title[i], s.desc == null ? "" : s.desc[i]);
        Ask ask = null;
        if (q != null) {
            String key = q.key();
            synchronized (LOCK) {
                ask = ASKS.get(key);
                if (ask == null) {
                    ask = new Ask(q, key);
                    String had = loaded ? FOUND.get(key) : null;
                    if (had != null) ask.url = url(had);
                    ASKS.put(key, ask);
                }
            }
        }
        Look k = new Look(ch.name, s.title[i], SkyListing.lookAt(s.start[i], s.stop[i], System.currentTimeMillis()), ask, always);
        ask(k);
        return k;
    }

    private static String url(String path) {
        String u = path == null || path.isEmpty() ? null : Tmdb.image(path, SIZE);
        return u == null ? "" : u;
    }

    private static void ask(Look k) {
        synchronized (LOCK) {
            if (k.queued || k.url != null) return;
            k.queued = true;
            QUEUE.addFirst(k);
            while (QUEUE.size() > WAITING_MAX) QUEUE.removeLast().queued = false;
            if (!running) {
                running = true;
                WORKER.execute(ProgArt::work);
            }
        }
    }

    /** The looks waiting, newest first: Sky's, then TMDB's a little apart, then the one always there. */
    private static void work() {
        Context c = app;
        if (!loaded) read(c);
        while (true) {
            Look k;
            synchronized (LOCK) {
                k = QUEUE.pollFirst();
                if (k == null) {
                    running = false;
                    break;
                }
            }
            String sky = "";
            try {
                sky = SkyPics.find(c, k.channel, k.at, k.title);
            } catch (RuntimeException e) {
                sky = "";
            }
            if (!sky.isEmpty()) {
                done(k, PicSources.chain(sky, k.always));
                continue;
            }
            String found = k.ask == null ? "" : search(c, k.ask);
            if (found == null) return;
            done(k, PicSources.chain(found, k.always));
            if (unsaved >= 20) write(c);
        }
        if (unsaved > 0) write(c);
    }

    /** A title's TMDB picture: kept, else looked for now (a little apart); "" for none or not now, null when stopped. */
    private static String search(Context c, Ask k) {
        String u = k.url;
        if (u != null) return u;
        String had;
        synchronized (LOCK) {
            had = FOUND.get(k.key);
        }
        if (had != null) return k.url = url(had);
        // No TMDB key (no TV paired yet): the one always there, not kept.
        if (c == null || !Tmdb.ready(c)) return "";
        long wait = lastSearch + SPACING_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (LOCK) {
                    running = false;
                }
                return null;
            }
        }
        lastSearch = System.currentTimeMillis();
        String path;
        try {
            path = k.q.pick(Tmdb.once(c, k.q.path()));
        } catch (IOException | RuntimeException e) {
            // Away from the network or TMDB busy: the one always there for now, looked for again next time.
            return "";
        }
        synchronized (LOCK) {
            FOUND.put(k.key, path);
            unsaved++;
        }
        return k.url = url(path);
    }

    private static void done(Look k, String url) {
        synchronized (LOCK) {
            k.url = url;
            k.queued = false;
        }
        if (!told) {
            told = true;
            ProgPics.main().postDelayed(TELL, 150);
        }
    }

    // ---- kept in the app's files --------------------------------------------------------------------------------

    private static File file(Context c) {
        File dir = new File(c.getFilesDir(), "live");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "art.tsv");
    }

    private static void read(Context c) {
        Map<String, String> got = new LinkedHashMap<>();
        if (c != null) {
            File f = file(c);
            if (f.isFile()) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 16 * 1024)) {
                    for (String line; (line = r.readLine()) != null; ) {
                        int tab = line.indexOf('\t');
                        if (tab > 0) got.put(line.substring(0, tab), line.substring(tab + 1));
                    }
                } catch (IOException ignored) {
                    // looked for again
                }
            }
        }
        synchronized (LOCK) {
            for (Map.Entry<String, String> e : got.entrySet()) if (!FOUND.containsKey(e.getKey())) FOUND.put(e.getKey(), e.getValue());
            loaded = true;
        }
    }

    private static void write(Context c) {
        if (c == null) return;
        List<String> lines;
        synchronized (LOCK) {
            lines = new ArrayList<>(FOUND.size());
            for (Map.Entry<String, String> e : FOUND.entrySet()) lines.add(e.getKey() + "\t" + e.getValue());
            unsaved = 0;
        }
        File f = file(c), tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 16 * 1024)) {
            for (String l : lines) {
                w.write(l.replace('\n', ' '));
                w.write('\n');
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }
}
