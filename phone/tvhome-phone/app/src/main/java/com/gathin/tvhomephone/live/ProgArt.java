package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.broadcast.PicFinder;
import com.gathin.broadcast.PicQuery;
import com.gathin.broadcast.PicSources;
import com.gathin.tvhomephone.watch.Tmdb;

import org.json.JSONObject;

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
 * A programme's picture for the guide's cards and its panel, found as Homefield TV finds it (PicFinder, shared):
 *
 * <pre>
 *   1 the guide's own        its {@code <icon src>} or {@code <image>} (not a placeholder, not the channel's logo)
 *   2 the UK catalogues      the programme found by its channel and time, its title the same: Sky's episode picture,
 *                            Freeview's, Virgin Media's (pay channels too), then Sky's season or series picture
 *   3 a game                 TheSportsDB's picture of the game itself
 *   4 by its title           TVMaze's episode that day, TMDB's episode still, then the series' picture (TMDB's
 *                            backdrop, TVMaze's poster); ArtQuery and PicQuery say what to look for and match with care
 *   5 the last resort        its game's crests, else its kind's picture (football, news, film, kids...: PicArt), else
 *                            "" (the channel's own: its logo on its colour, drawn by the guide)
 * </pre>
 *
 * What is found comes as one chain of addresses, best first (PicSources.chain): each place takes the first that is
 * large enough for it and is no placeholder (ProgPics). Looked for off the main thread, one programme at a time, the
 * newest asked first and only a screenful waiting, so scrolling the guide never floods anyone; every source kept a
 * little apart and its answers kept (PicFetch: the app's cache folder). What TMDB found for a title, or that it found
 * nothing, is kept in memory and in the app's files (art.tsv), so a title is looked for once.
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
    static final int WAITING_MAX = 30, KEEP_MAX = 6000;
    /** TMDB's picture size: enough for the panel across a phone, read smaller for a card. */
    static final String SIZE = "w780";
    /** Sky's and Virgin Media's pictures: a phone's whole panel across. */
    static final int WIDTH = 1280;
    static final long SEASON_KEEP_MS = 14L * 24 * 3600_000L;

    /** One programme's look: the programme as the finder takes it, and the one always there. */
    static final class Look {
        final PicFinder.Programme p;
        final String always;
        volatile String url;
        boolean queued;

        Look(PicFinder.Programme p, String always) {
            this.p = p;
            this.always = always;
        }
    }

    private static final Object LOCK = new Object();
    /** What each TMDB search found ("path id kind", "" for nothing), kept on disk. */
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
    private static volatile PicFinder finder;
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
        if (ch == null) return s.pic(i);
        if (app == null) app = c.getApplicationContext();
        PicFinder.Programme p = new PicFinder.Programme(ch.name, ch.group, s.title[i], s.desc == null ? "" : s.desc[i], s.category(i),
                s.start[i], s.stop[i], s.pic(i), ch.logo);
        Look k = new Look(p, fallback(ch, s, i));
        ask(k);
        return k;
    }

    /** The shared finder, its pages in the app's cache, TMDB through this app's own key (made the first time). */
    static PicFinder finder(Context c) {
        PicFinder f = finder;
        if (f != null) return f;
        synchronized (LOCK) {
            if (finder == null) {
                File dir = c == null ? null : new File(c.getCacheDir(), "guide-pictures");
                finder = new PicFinder(dir, ProgArt::tmdb, WIDTH, WIDTH);
            }
            return finder;
        }
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

    /** The looks waiting, newest first. */
    private static void work() {
        Context c = app;
        if (!loaded) read(c);
        PicFinder f = finder(c);
        while (true) {
            Look k;
            synchronized (LOCK) {
                k = QUEUE.pollFirst();
                if (k == null) {
                    running = false;
                    break;
                }
            }
            String found;
            try {
                found = f.find(k.p).chain;
            } catch (RuntimeException e) {
                found = k.p.epg;
            }
            done(k, PicSources.chain(found, k.always));
            if (unsaved >= 20) write(c);
        }
        if (unsaved > 0) write(c);
    }

    /**
     * TMDB for the finder (its worker's thread): {the episode's still, the series' picture} for a programme ("" for none),
     * null when TMDB can't be asked now. ArtQuery says what to look for; a search a little apart; what it found kept.
     */
    static String[] tmdb(PicFinder.Programme p, int season, int episode) {
        Context c = app;
        ArtQuery q = ArtQuery.of(p.channel, p.group, p.title, p.desc);
        if (q == null) return new String[]{"", ""};
        String key = q.key(), had;
        synchronized (LOCK) {
            had = FOUND.get(key);
        }
        if (had == null) {
            // No TMDB key (no TV paired yet): nothing, not kept.
            if (c == null || !Tmdb.ready(c)) return new String[]{"", ""};
            long wait = lastSearch + SPACING_MS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            lastSearch = System.currentTimeMillis();
            String[] hit;
            try {
                hit = q.pickWithId(Tmdb.once(c, q.path()));
            } catch (IOException | RuntimeException e) {
                // Away from the network or TMDB busy: looked for again later.
                return null;
            }
            had = hit == null ? "" : hit[0] + " " + hit[1] + " " + hit[2];
            synchronized (LOCK) {
                FOUND.put(key, had);
                unsaved++;
            }
        }
        String[] f = had.isEmpty() ? new String[0] : had.split(" ");
        String series = f.length > 0 ? url(f[0]) : "", still = "";
        if (f.length > 2 && "tv".equals(f[2]) && season > 0 && episode > 0 && c != null) {
            try {
                JSONObject s = Tmdb.getKept(c, "tv/" + f[1] + "/season/" + season, SEASON_KEEP_MS);
                still = url(PicQuery.still(s, episode));
            } catch (IOException | RuntimeException e) {
                still = "";
            }
        }
        return new String[]{still, series};
    }

    private static String url(String path) {
        String u = path == null || path.isEmpty() ? null : Tmdb.image(path, SIZE);
        return u == null ? "" : u;
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
