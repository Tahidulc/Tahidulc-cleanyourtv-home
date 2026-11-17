package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The launcher's own check on its memory, once a minute, and what it lets go when memory runs short.
 *
 * <p>The check (cheap: a few counters and /proc/self/status) writes one line to memlog.txt in the app's own folder on
 * the TV's storage, /sdcard/Android/data/com.gathin.tvhome/files/memlog.txt, the last day of it (about 1,500 lines, the
 * oldest dropped), to be read over ADB: adb shell cat /sdcard/Android/data/com.gathin.tvhome/files/memlog.txt. Nothing
 * personal is in it: no channel, programme, title, name, address or login, only sizes, counts and which kind of app is
 * in front. Each line is key=value pairs (see HEADER).
 *
 * <p>Memory let go (release): every picture cache (ByteLru) trimmed or emptied, the screenshot part renderers
 * (GpuParts), the text readers not reading (ChannelReader), a kick-off pop-up made ready out of sight (ClassicIntro) and
 * YouTube's player while Home is not on screen (HomeV2). On onTrimMemory and onLowMemory (TvHomeApp); and, as Android
 * 14 no longer tells an app targeting it about memory running low while it runs, also by the check itself when this
 * process's native memory passes SELF_TRIM_BYTES or the TV says it is low on memory, at most every SELF_TRIM_EVERY_MS.
 * The process is never killed or restarted for it: a force stop switches the remote's accessibility helpers off.
 */
final class MemoryWatch {
    private MemoryWatch() { }

    static final String FILE = "memlog.txt";
    static final long EVERY_MS = 60_000L;
    /** A day of minutes, with room for the events between. */
    static final int KEEP_LINES = 1_500;
    /** Lines written between two trims of the file to KEEP_LINES. */
    static final int COMPACT_EVERY = 60;
    /** This process's native memory past which the check lets go what it can (a 32-bit process has under 3 GB in all). */
    static final long SELF_TRIM_BYTES = 640L * 1024 * 1024;
    static final long SELF_TRIM_EVERY_MS = 10 * 60_000L;

    static final String HEADER = "# memlog (MemoryWatch): a line a minute. nat=native heap allocated/size and its change"
            + " since the last line; java=Java heap used/limit; rss, swap, thr=this process's resident and swapped memory and"
            + " threads; avail=memory the TV has free, low=1 when it says it is low; caches=picture caches' bytes/count, big=the"
            + " largest; fonts=fonts held; web=web views alive (launcher's own), intro=pop-up pages alive; gpu=screenshot part"
            + " renderers held; shots, parts, ocr=screenshots, parts and text reads in the minute; ocrOpen=text readers held;"
            + " front=what is in front; board=1 while the scoreboard or logo cover is up. Events: start, trim, self-trim.";

    private static HandlerThread thread;
    private static Handler bg;
    /** The application's context only (never an activity's), for the log's folder and the TV's memory figures. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Context app;
    private static long lastNative, lastShots, lastParts, lastReads, lastTrimAt = Long.MIN_VALUE;
    private static int linesSince;

    // ---- the check ------------------------------------------------------------------------------------------------------

    /** Started once, in the launcher's main process (TvHomeApp). */
    static synchronized void start(Context c) {
        if (thread != null) return;
        app = c.getApplicationContext();
        thread = new HandlerThread("memlog", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        bg = new Handler(thread.getLooper());
        bg.post(() -> {
            compact();
            write(event("start", "ver=" + version() + " pid=" + Process.myPid()));
        });
        bg.postDelayed(MemoryWatch::tick, EVERY_MS);
    }

    private static void tick() {
        try {
            check();
        } catch (RuntimeException ignored) {
            // the next minute
        }
        bg.postDelayed(MemoryWatch::tick, EVERY_MS);
    }

    private static void check() {
        long nat = Debug.getNativeHeapAllocatedSize(), natSize = Debug.getNativeHeapSize();
        Runtime rt = Runtime.getRuntime();
        long javaUsed = rt.totalMemory() - rt.freeMemory(), javaMax = rt.maxMemory();
        String status = readStatus();
        long avail = -1;
        boolean low = false;
        try {
            ActivityManager am = app.getSystemService(ActivityManager.class);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                avail = mi.availMem;
                low = mi.lowMemory;
            }
        } catch (RuntimeException ignored) {
            // not known this minute
        }
        long shots = ScreenShots.shots, parts = ScreenShots.parts, reads = ChannelReader.reads;
        Stats s = new Stats();
        s.time = LocalDateTime.now();
        s.upMin = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 60_000L;
        s.nativeKb = nat / 1024;
        s.nativeSizeKb = natSize / 1024;
        s.nativeDeltaKb = lastNative == 0 ? 0 : (nat - lastNative) / 1024;
        s.javaKb = javaUsed / 1024;
        s.javaMaxKb = javaMax / 1024;
        s.rssKb = statusKb(status, "VmRSS:");
        s.swapKb = statusKb(status, "VmSwap:");
        s.threads = statusKb(status, "Threads:");
        s.availMb = avail < 0 ? -1 : avail / (1024 * 1024);
        s.low = low;
        s.cachesKb = ByteLru.totalBytes() / 1024;
        s.caches = ByteLru.caches();
        s.big = ByteLru.biggest(3);
        s.fonts = Fonts.count();
        s.webViews = WebViews.alive();
        s.intro = ClassicIntro.alive();
        s.gpu = android.os.Build.VERSION.SDK_INT >= 30 ? GpuParts.held() : 0;
        s.shots = shots - lastShots;
        s.parts = parts - lastParts;
        s.ocr = reads - lastReads;
        s.ocrOpen = ChannelReader.openCount();
        s.front = front();
        s.board = BoardCover.bugNow() != null;
        lastNative = nat;
        lastShots = shots;
        lastParts = parts;
        lastReads = reads;
        write(line(s));
        long now = SystemClock.elapsedRealtime();
        boolean wasLow = low;
        int level = selfTrimLevel(nat, wasLow, lastTrimAt == Long.MIN_VALUE ? Long.MAX_VALUE : now - lastTrimAt);
        if (level != 0) {
            lastTrimAt = now;
            new Handler(Looper.getMainLooper()).post(() -> {
                long freed = release(level);
                note(event("self-trim", "nat=" + nat / 1024 + "K low=" + (wasLow ? 1 : 0) + " freed=" + freed / 1024 + "K"));
            });
        }
    }

    /** What is in front, in a word: home, dream, tivimate, kleva, homefield, other, or ? (never an app's name besides these). */
    private static String front() {
        if (GoalWatchService.homeInFront) return "home";
        if (GoalWatchService.dreamingNow()) return "dream";
        return kind(AppSearchService.front());
    }

    /** Pure: an app in front as the line names it. */
    static String kind(String pkg) {
        if (pkg == null) return "?";
        if (OverlayScope.TIVIMATE.equals(pkg)) return "tivimate";
        if (OverlayScope.KLEVA.equals(pkg)) return "kleva";
        if (OverlayScope.HOMEFIELD.equals(pkg)) return "homefield";
        if ("com.gathin.tvhome".equals(pkg)) return "launcher";
        return "other";
    }

    private static String version() {
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String readStatus() {
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            String l;
            while ((l = r.readLine()) != null) s.append(l).append('\n');
        } catch (IOException ignored) {
            // not this minute
        }
        return s.toString();
    }

    // ---- memory warnings --------------------------------------------------------------------------------------------------

    /**
     * Android's memory warning (TvHomeApp, main thread): what can be let go, and a line in the log. Not at UI_HIDDEN, the
     * ordinary trip from Home to an app (Home lets its own pictures go then, MainActivity, and YouTube's player a minute
     * later, HomeV2).
     */
    static void trimmed(Context c, int level) {
        if (thread == null || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return;   // not the main process
        long freed = release(level);
        note(event("trim", "level=" + level + " freed=" + freed / 1024 + "K"));
    }

    /** Let go for level (main thread); the cache bytes let go. */
    static long release(int level) {
        float share = trimShare(level);
        long freed = share >= 1f ? 0 : ByteLru.trimAll(share);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            ScreenShots.release();
            ChannelReader.closeIdle();
            ClassicIntro.releaseHeld();
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            HomeV2 h = HomeV2.current();
            if (h != null) h.releaseHidden();
        }
        return freed;
    }

    /**
     * Pure: how much of each picture cache is kept at a memory warning's level: all of it at UI_HIDDEN (Home's own are
     * let go by Home then) and MODERATE's three quarters, half at LOW, none at CRITICAL or once the process is in the
     * background (BACKGROUND, MODERATE, COMPLETE).
     */
    static float trimShare(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) return 0f;
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return 1f;
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) return 0f;
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return 0.5f;
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) return 0.75f;
        return 1f;
    }

    /**
     * Pure: the level the check lets go at by itself (0: none): CRITICAL's when the native memory is past
     * SELF_TRIM_BYTES or the TV says it is low, at most every SELF_TRIM_EVERY_MS.
     */
    static int selfTrimLevel(long nativeBytes, boolean systemLow, long sinceLastMs) {
        if (sinceLastMs < SELF_TRIM_EVERY_MS) return 0;
        return nativeBytes >= SELF_TRIM_BYTES || systemLow ? ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL : 0;
    }

    // ---- the log's lines --------------------------------------------------------------------------------------------------

    /** One minute's figures. */
    static final class Stats {
        LocalDateTime time;
        long upMin, nativeKb, nativeSizeKb, nativeDeltaKb, javaKb, javaMaxKb, rssKb, swapKb, threads, availMb, cachesKb;
        long shots, parts, ocr;
        int caches, fonts, webViews, intro, gpu, ocrOpen;
        boolean low, board;
        String big = "", front = "?";
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);

    /** Pure: a minute's line. */
    static String line(Stats s) {
        StringBuilder b = new StringBuilder(220);
        b.append("t=").append(s.time.format(TIME));
        b.append(" up=").append(s.upMin).append('m');
        b.append(" nat=").append(s.nativeKb).append("K/").append(s.nativeSizeKb).append('K');
        b.append('(').append(s.nativeDeltaKb >= 0 ? "+" : "").append(s.nativeDeltaKb).append("K)");
        b.append(" java=").append(s.javaKb).append("K/").append(s.javaMaxKb).append('K');
        b.append(" rss=").append(s.rssKb).append('K');
        b.append(" swap=").append(s.swapKb).append('K');
        b.append(" thr=").append(s.threads);
        b.append(" avail=").append(s.availMb).append('M');
        b.append(" low=").append(s.low ? 1 : 0);
        b.append(" caches=").append(s.cachesKb).append("K/").append(s.caches);
        if (s.big != null && !s.big.isEmpty()) b.append(" big=").append(s.big);
        b.append(" fonts=").append(s.fonts);
        b.append(" web=").append(s.webViews);
        b.append(" intro=").append(s.intro);
        b.append(" gpu=").append(s.gpu);
        b.append(" shots=").append(s.shots);
        b.append(" parts=").append(s.parts);
        b.append(" ocr=").append(s.ocr);
        b.append(" ocrOpen=").append(s.ocrOpen);
        b.append(" front=").append(s.front);
        b.append(" board=").append(s.board ? 1 : 0);
        return b.toString();
    }

    /** Pure: an event's line ("trim", "self-trim", "start"), with its details. */
    static String event(String what, String details) {
        return "t=" + LocalDateTime.now().format(TIME) + " event=" + what + (details == null || details.isEmpty() ? "" : " " + details);
    }

    /** Pure: the value of a /proc/self/status line ("VmRSS:  1234 kB" gives 1234); -1 when it is not there. */
    static long statusKb(String status, String key) {
        if (status == null) return -1;
        int at = status.indexOf(key);
        if (at < 0) return -1;
        int i = at + key.length(), n = status.length();
        while (i < n && (status.charAt(i) == ' ' || status.charAt(i) == '\t')) i++;
        long v = 0;
        boolean any = false;
        while (i < n && Character.isDigit(status.charAt(i))) {
            v = v * 10 + (status.charAt(i) - '0');
            any = true;
            i++;
        }
        return any ? v : -1;
    }

    /** Pure: the file's lines as kept: the header first, then the newest max lines of the rest. */
    static List<String> keep(List<String> lines, int max) {
        List<String> body = new ArrayList<>();
        for (String l : lines) if (l != null && !l.isEmpty() && !l.startsWith("#")) body.add(l);
        List<String> out = new ArrayList<>(Math.min(body.size(), max) + 1);
        out.add(HEADER);
        out.addAll(body.subList(Math.max(0, body.size() - max), body.size()));
        return out;
    }

    // ---- the file ---------------------------------------------------------------------------------------------------------

    /** A line from any thread (written on the check's own). */
    private static void note(String line) {
        Handler h = bg;
        if (h != null) h.post(() -> write(line));
    }

    private static File file() {
        File dir = app == null ? null : app.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, FILE);
    }

    /** One line added (the check's thread); the file trimmed to a day every COMPACT_EVERY lines. */
    private static void write(String line) {
        File f = file();
        if (f == null) return;
        boolean fresh = !f.exists();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
            if (fresh) w.write(HEADER + "\n");
            w.write(line);
            w.write('\n');
        } catch (IOException ignored) {
            return;
        }
        if (++linesSince >= COMPACT_EVERY) compact();
    }

    private static void compact() {
        linesSince = 0;
        File f = file();
        if (f == null || !f.isFile()) return;
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) lines.add(l);
        } catch (IOException e) {
            return;
        }
        if (lines.size() <= KEEP_LINES + 1) return;
        File tmp = new File(f.getPath() + ".new");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp, false), StandardCharsets.UTF_8)) {
            for (String l : keep(lines, KEEP_LINES)) {
                w.write(l);
                w.write('\n');
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
