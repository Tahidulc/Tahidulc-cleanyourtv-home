package com.gathin.homefieldtv;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.FrameMetrics;
import android.view.Window;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How long each frame took on this TV, by the screen on top: Android's own measure of every frame the app draws (its
 * FrameMetrics: from the display's tick to the frame handed over, the UI thread's and the render thread's work, and the
 * graphics chip's own time where Android says it), kept as a histogram per screen and written to the memory log once a
 * minute (MemLog), so the family's TV can be read without the remote:
 *
 * <pre>
 *   2026-11-18 20:31 frames motion=auto-lite Home n=812 p50=11.2 p95=19.8 over20=3.1% gpu95=9.0 | Guide n=140 ...
 * </pre>
 *
 * n: frames drawn that minute (the app draws only while something moves or changes); p50 and p95: the median and the
 * 95th percentile frame time, ms; over20: the share of frames over 20 ms (a frame missed at 50 a second); gpu95: the
 * graphics chip's 95th percentile (Android 12 and later). The first frames of moving also tell Motion whether Automatic
 * should be Lite on this TV. Frames are recorded off the main thread; nothing is made per frame on it.
 */
final class FrameStats {
    private FrameStats() {}

    /** Half-millisecond buckets up to 100 ms (the last holds everything slower). */
    static final int BUCKETS = 200;
    /** The first frames of moving Motion is told about (after the start's first seconds), and when they count from. */
    static final int FIRST = 120;
    static final long SETTLE_MS = 8_000L;

    /** One screen's minute. */
    static final class Stats {
        final int[] total = new int[BUCKETS], gpu = new int[BUCKETS];
        int n, gpuN, over;

        void add(float ms, float gpuMs) {
            total[bucket(ms)]++;
            n++;
            if (ms > 20f) over++;
            if (gpuMs >= 0f) {
                gpu[bucket(gpuMs)]++;
                gpuN++;
            }
        }

        void clear() {
            java.util.Arrays.fill(total, 0);
            java.util.Arrays.fill(gpu, 0);
            n = gpuN = over = 0;
        }
    }

    private static final Object LOCK = new Object();
    private static final Map<String, Stats> SCREENS = new LinkedHashMap<>();
    /** The screen on top's name (the main thread sets it as screens change). */
    private static volatile String screen = "Home";
    private static HandlerThread thread;
    private static long startedAt;
    /** The first frames of moving, for Motion: their times, how many so far. */
    private static final float[] first = new float[FIRST];
    private static int firstN;
    private static Window.OnFrameMetricsAvailableListener listener;

    static int bucket(float ms) {
        return Math.max(0, Math.min(BUCKETS - 1, (int) (ms * 2f)));
    }

    /** Pure: the frame time at share q (0 to 1) of a histogram of n frames (the bucket's middle, ms). */
    static float percentile(int[] h, int n, float q) {
        if (n <= 0) return 0f;
        int want = Math.max(1, (int) Math.ceil(q * n)), seen = 0;
        for (int i = 0; i < h.length; i++) {
            seen += h[i];
            if (seen >= want) return (i + 0.5f) / 2f;
        }
        return BUCKETS / 2f;
    }

    /** The screen on top now (TvActivity, as screens change). */
    static void on(Screen s) {
        screen = nameOf(s);
    }

    /** The screen's name in the log: a tab's own name, else its class's without "Screen" (kept by the build's rules). */
    static String nameOf(Screen s) {
        if (s == null) return "-";
        int t = s.tab();
        if (t >= 0 && t < TabBar.NAMES.length) return TabBar.NAMES[t].replace(" ", "");
        String n = s.getClass().getSimpleName();
        return n.endsWith("Screen") && n.length() > 6 ? n.substring(0, n.length() - 6) : n;
    }

    /** Frames measured from now on, for this activity's window. */
    static void start(Activity a) {
        if (listener != null) return;
        if (thread == null) {
            thread = new HandlerThread("frame-stats", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
        }
        startedAt = android.os.SystemClock.uptimeMillis();
        android.content.Context app = a.getApplicationContext();
        listener = (w, m, dropped) -> frame(app, m);
        try {
            a.getWindow().addOnFrameMetricsAvailableListener(listener, new Handler(thread.getLooper()));
        } catch (RuntimeException e) {
            listener = null;
        }
    }

    static void stop(Activity a) {
        if (listener == null) return;
        try {
            a.getWindow().removeOnFrameMetricsAvailableListener(listener);
        } catch (RuntimeException ignored) {
            // gone already
        }
        listener = null;
    }

    private static void frame(android.content.Context app, FrameMetrics m) {
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
        float ms = m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6f;
        float gpuMs = Build.VERSION.SDK_INT >= 31 ? m.getMetric(FrameMetrics.GPU_DURATION) / 1e6f : -1f;
        // A frame that waited for the chip shows in the next one's total; the slower of the two is the frame's time.
        float t = Math.max(ms, gpuMs);
        add(screen, t, gpuMs);
        if (Motion.measuring() && android.os.SystemClock.uptimeMillis() - startedAt > SETTLE_MS) {
            first[firstN++] = t;
            if (firstN == FIRST) {
                float[] s = first.clone();
                java.util.Arrays.sort(s);
                float median = s[FIRST / 2];
                firstN = 0;
                com.gathin.homefieldtv.engine.Exec.main(() -> Motion.measured(app, median));
            }
        }
    }

    /** A frame of ms (and the chip's gpuMs, below 0 unknown) on a screen (the frames' thread; tests). */
    static void add(String name, float ms, float gpuMs) {
        synchronized (LOCK) {
            Stats s = SCREENS.get(name);
            if (s == null) {
                s = new Stats();
                SCREENS.put(name, s);
            }
            s.add(ms, gpuMs);
        }
    }

    /** The minute's line (without the time) and the counts started again; null when no frame was drawn. */
    static String line() {
        StringBuilder b = new StringBuilder();
        synchronized (LOCK) {
            for (Map.Entry<String, Stats> e : SCREENS.entrySet()) {
                Stats s = e.getValue();
                if (s.n == 0) continue;
                if (b.length() > 0) b.append(" | ");
                b.append(e.getKey()).append(String.format(Locale.ROOT, " n=%d p50=%.1f p95=%.1f over20=%.1f%%", s.n,
                        percentile(s.total, s.n, 0.5f), percentile(s.total, s.n, 0.95f), 100f * s.over / s.n));
                if (s.gpuN > 0) b.append(String.format(Locale.ROOT, " gpu95=%.1f", percentile(s.gpu, s.gpuN, 0.95f)));
                s.clear();
            }
        }
        return b.length() == 0 ? null : "frames motion=" + Motion.logWord() + " " + b;
    }
}
