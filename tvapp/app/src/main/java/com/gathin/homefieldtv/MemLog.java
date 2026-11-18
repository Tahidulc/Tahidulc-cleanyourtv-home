package com.gathin.homefieldtv;

import android.content.Context;
import android.os.Debug;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * The memory log: once a minute, a line about how much memory Homefield TV holds, kept for the last day in the app's own
 * external files folder (Android/data/com.gathin.homefieldtv/files/memlog.txt; files/memlog.txt where there is none),
 * so the family's TV can be checked after a release without a debugger. Numbers only: no channel, address or name.
 *
 * <pre>
 *   2026-11-17 15:35 heap=96.1/131.0/384MB native=88.4MB rss=301MB pics=18.2/40MB(hits=1210,reads=96) gc=41/3.1s
 *   blocking=0 channels=50000 guide=2000ch/96000p/5904kc catalog=40000 buffer=3.2s screen=GuideScreen
 * </pre>
 *
 * Then, for each minute the app drew anything, a second line: each screen's frame times (FrameStats), the tier in use
 * (Motion: auto-lite, auto-full, lite, full) and, per screen, the frames drawn, their median and 95th percentile (ms),
 * the share over 20 ms and the graphics chip's 95th percentile:
 *
 * <pre>
 *   2026-11-18 20:31 frames motion=auto-lite Home n=812 p50=11.2 p95=19.8 over20=3.1% gpu95=9.0 | Guide n=140 p50=13.0
 *   p95=22.4 over20=7.9% gpu95=12.1
 * </pre>
 *
 * heap: the Java heap in use, its size now and its most; native: the native heap in use (bitmaps' pixels among it); rss:
 * what the process holds in RAM (VmRSS); pics: the picture cache's bytes and its budget, the pictures found in it and
 * read since the start; gc: collections and their time since the start (blocking: those that held up a thread);
 * channels, guide (channels, programmes, thousands of characters of descriptions) and catalogue sizes; the player's
 * buffer ahead; the screen on top. Written off the main thread; a line costs well under a millisecond there.
 */
final class MemLog {
    private MemLog() {}

    /** A day of lines: one a minute, and a frames line for each minute the app drew. */
    static final int KEEP = 2 * 24 * 60;
    private static final String NAME = "memlog.txt";

    /** The line for now: the main thread's part (the player, the screen) given, the rest read here. */
    static void write(Context c, long bufferedMs, String screen) {
        Context app = c.getApplicationContext();
        Library lib = Library.get(app);
        int channels = lib.count();
        int catalog = Catalog.get(app).films().size() + Catalog.get(app).series().size();
        long now = System.currentTimeMillis();
        String frames = FrameStats.line();
        Exec.io(() -> {
            try {
                File f = file(app);
                append(f, line(now, lib, channels, catalog, bufferedMs, screen));
                if (frames != null) append(f, String.format(Locale.ROOT, "%1$tF %1$tR ", now) + frames);
            } catch (IOException | RuntimeException ignored) {
                // no log this minute
            }
        });
    }

    static File file(Context app) {
        File dir = app.getExternalFilesDir(null);
        if (dir == null) dir = app.getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, NAME);
    }

    private static String line(long now, Library lib, int channels, int catalog, long bufferedMs, String screen) {
        Runtime r = Runtime.getRuntime();
        long used = r.totalMemory() - r.freeMemory();
        long[] g = lib.guideSize();
        return String.format(Locale.ROOT,
                "%1$tF %1$tR heap=%2$.1f/%3$.1f/%4$.0fMB native=%5$.1fMB rss=%6$dMB pics=%7$.1f/%8$.0fMB(hits=%9$d,reads=%10$d) "
                        + "gc=%11$s/%12$.1fs blocking=%13$s channels=%14$d guide=%15$dch/%16$dp/%17$dkc catalog=%18$d buffer=%19$.1fs screen=%20$s",
                now, mb(used), mb(r.totalMemory()), mb(r.maxMemory()), mb(Debug.getNativeHeapAllocatedSize()), rssMb(),
                mb(Pics.bytes()), mb(Pics.BUDGET), Pics.hits, Pics.loads, stat("art.gc.gc-count"),
                parse(stat("art.gc.gc-time")) / 1000.0, stat("art.gc.blocking-gc-count"), channels, g[0], g[1], g[2] / 1000,
                catalog, bufferedMs / 1000.0, screen);
    }

    private static double mb(long bytes) {
        return bytes / 1048576.0;
    }

    private static String stat(String name) {
        try {
            String v = Debug.getRuntimeStat(name);
            return v == null ? "?" : v;
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static long parse(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** What the process holds in RAM (VmRSS in /proc/self/status), MB; -1 when it can't be read. */
    static long rssMb() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/self/status"), StandardCharsets.US_ASCII))) {
            for (String l; (l = r.readLine()) != null; ) {
                if (!l.startsWith("VmRSS:")) continue;
                String kb = l.substring(6).trim();
                int sp = kb.indexOf(' ');
                return Long.parseLong(sp > 0 ? kb.substring(0, sp) : kb) / 1024L;
            }
        } catch (IOException | RuntimeException ignored) {
            // not on this TV
        }
        return -1;
    }

    /** A line added; past a day's worth plus a few, the oldest are let go (the file rewritten with the last KEEP). */
    static void append(File f, String line) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
            w.write(line);
            w.write('\n');
        }
        if (f.length() < (long) (KEEP + 60) * 260) return;
        ArrayDeque<String> last = new ArrayDeque<>(KEEP + 1);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                last.addLast(l);
                if (last.size() > KEEP) last.removeFirst();
            }
        }
        if (last.size() < KEEP) return;
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (String l : last) {
                w.write(l);
                w.write('\n');
            }
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }
}
