package com.gathin.tvhomephone.live;

import android.content.Context;

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
 * A programme's picture for the guide's cells and its panel: the guide's own (its {@code <icon src>}), else TMDB's by
 * its title (ArtQuery: a film as a film with its year, a series as a series, nothing on a sports or news channel),
 * looked for off the main thread, one search at a time and a little apart (SPACING_MS), the newest asked first and only
 * a screenful waiting, so scrolling the guide never floods TMDB. What is found, or that nothing was, is kept in memory
 * and in the app's files (art.tsv), so a title is looked for once. "" for none: the channel's own picture then (its
 * logo on its colour).
 *
 * <pre>
 *   String url = ProgArt.of(c, ch, schedule, i);   main thread: the address, "" for none, null while it is looked for
 *   ProgArt.listen(redraw) / unlisten(redraw)      told (main thread) as searches finish, a few together
 * </pre>
 */
final class ProgArt {
    private ProgArt() {}

    /** Between two TMDB searches, the most waiting (the rest let go, asked again when drawn), the most kept. */
    static final long SPACING_MS = 350;
    static final int WAITING_MAX = 30, KEEP_MAX = 6000, ASKS_MAX = 3000;
    /** TMDB's picture size: enough for the panel across a phone, read smaller for a cell. */
    static final String SIZE = "w780";

    /** One title's search (shared by every programme of it): its picture, null until known ("" for none). */
    static final class Ask {
        final ArtQuery q;
        final String key;
        volatile String url;
        boolean queued;

        Ask(ArtQuery q, String key) {
            this.q = q;
            this.key = key;
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
    private static final ArrayDeque<Ask> QUEUE = new ArrayDeque<>();
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
     * Programme i's picture: its address, "" for none, or null while it is looked for. Main thread; works a programme
     * out the first time it is asked about, then only reads.
     */
    static String of(Context c, Channel ch, Guide.Schedule s, int i) {
        if (s == null || i < 0 || i >= s.start.length) return "";
        Object[] art = s.art;
        if (art == null || art.length != s.start.length) s.art = art = new Object[s.start.length];
        Object a = art[i];
        if (a == null) art[i] = a = resolve(c, ch, s, i);
        if (a instanceof String) return (String) a;
        Ask k = (Ask) a;
        String u = k.url;
        if (u != null) {
            art[i] = u;
            return u;
        }
        ask(k);
        return null;
    }

    private static Object resolve(Context c, Channel ch, Guide.Schedule s, int i) {
        String own = s.pic(i);
        if (!own.isEmpty()) return own;
        if (ch == null) return "";
        ArtQuery q = ArtQuery.of(ch.name, ch.group, s.title[i], s.desc == null ? "" : s.desc[i]);
        if (q == null) return "";
        if (app == null) app = c.getApplicationContext();
        String key = q.key();
        Ask k;
        synchronized (LOCK) {
            k = ASKS.get(key);
            if (k == null) {
                k = new Ask(q, key);
                String had = loaded ? FOUND.get(key) : null;
                if (had != null) k.url = url(had);
                ASKS.put(key, k);
            }
        }
        String u = k.url;
        if (u != null) return u;
        ask(k);
        return k;
    }

    private static String url(String path) {
        String u = path == null || path.isEmpty() ? null : Tmdb.image(path, SIZE);
        return u == null ? "" : u;
    }

    private static void ask(Ask k) {
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

    /** The searches waiting, newest first, a little apart. */
    private static void work() {
        Context c = app;
        if (!loaded) read(c);
        long last = 0;
        while (true) {
            Ask k;
            String had;
            synchronized (LOCK) {
                k = QUEUE.pollFirst();
                if (k == null) {
                    running = false;
                    break;
                }
                had = FOUND.get(k.key);
            }
            String path = had;
            if (path == null) {
                if (c == null || !Tmdb.ready(c)) {
                    // No TMDB key (no TV paired yet): the channel's picture, not kept.
                    done(k, "");
                    continue;
                }
                long wait = last + SPACING_MS - System.currentTimeMillis();
                if (wait > 0) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                last = System.currentTimeMillis();
                try {
                    path = k.q.pick(Tmdb.once(c, k.q.path()));
                } catch (IOException | RuntimeException e) {
                    // Away from the network or TMDB busy: the channel's picture for now, looked for again next time.
                    done(k, "");
                    continue;
                }
                synchronized (LOCK) {
                    FOUND.put(k.key, path);
                    unsaved++;
                }
            }
            done(k, url(path));
            if (unsaved >= 20) write(c);
        }
        if (unsaved > 0) write(c);
    }

    private static void done(Ask k, String url) {
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
