package com.gathin.homefieldtv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import com.gathin.homefieldtv.engine.Exec;

import java.util.Locale;

/**
 * How much of 07's motion this TV draws (Settings › Appearance › Animations: Automatic, Full or Lite). Full is the
 * mock-ups' every touch; Lite keeps the look and the motion (cards lift, rows slide, pictures cross-fade) but leaves out
 * what costs a slow graphics chip most: the cards' tilt in perspective and the sheen of light across them, the picture
 * drifting under a lifted card's frame, the frosted panels taking a new picture while the remote moves, and the page's
 * big blurred picture swapped while the remote moves (its colour cross-fades instead; the picture comes when the remote
 * rests). The drawing pieces read lite() as they draw (Card07, Ground, Frost), so a change shows at once.
 *
 * <p>Automatic is Lite on a weaker TV: a Mali-G52-class graphics chip (the TCL's), under 3 GB of memory, or frames
 * measured over 18 ms in the first seconds of moving (FrameStats tells it, once a build); else Full (the Google TV
 * Streamer). The chip's name is read once per firmware, off the main thread, and kept.
 *
 * <pre>
 *   Motion.load(context)        when the app starts (TvActivity), before any screen draws
 *   Motion.lite()               while drawing: the Lite tier is on
 *   Motion.set(context, LITE)   Settings' choice (AUTO, FULL, LITE)
 *   Motion.words()              what Automatic chose and why ("Lite: a Mali-G52 graphics chip"), for Settings and the log
 * </pre>
 */
final class Motion {
    private Motion() {}

    static final int AUTO = 0, FULL = 1, LITE = 2;
    static final String[] NAMES = {"Automatic", "Full", "Lite"};
    /** Frames measured slower than this, the median of the first seconds of moving, make Automatic Lite. */
    static final float SLOW_MS = 18f;
    /** Less memory than this makes Automatic Lite. */
    static final long SMALL_RAM = 3L << 30;

    private static volatile boolean lite;
    private static int choice = AUTO;
    /** Why Automatic is Lite ("" while it is Full): the chip, the memory, the frames measured. */
    private static volatile String why = "";
    private static String gpu = "";
    private static long ram;
    private static boolean slow;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("motion", Context.MODE_PRIVATE);
    }

    /** Lite is on now. */
    static boolean lite() {
        return lite;
    }

    /** Settings' choice: AUTO, FULL or LITE. */
    static int choice() {
        return choice;
    }

    /** The app starting: the choice, the chip's name (read once per firmware), the memory, a slow verdict kept. */
    static void load(Context c) {
        SharedPreferences p = prefs(c);
        choice = Math.max(AUTO, Math.min(LITE, p.getInt("choice", AUTO)));
        String fp = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT;
        gpu = fp.equals(p.getString("gpu_for", null)) ? p.getString("gpu", "") : "";
        slow = p.getInt("slow_for", -1) == version(c);
        ram = totalRam(c);
        apply();
        if (gpu.isEmpty() && !Exec.inline) {
            Context app = c.getApplicationContext();
            Exec.io(() -> {
                String name = renderer();
                if (name == null) return;
                prefs(app).edit().putString("gpu", name).putString("gpu_for", fp).apply();
                Exec.main(() -> {
                    gpu = name;
                    apply();
                });
            });
        }
    }

    static void set(Context c, int v) {
        choice = Math.max(AUTO, Math.min(LITE, v));
        prefs(c).edit().putInt("choice", choice).apply();
        apply();
    }

    /**
     * FrameStats: the median frame time of the first seconds of moving (ms). Over SLOW_MS, Automatic turns Lite now and
     * for this build (a new one measures again); true when it did.
     */
    static boolean measured(Context c, float medianMs) {
        if (slow || medianMs <= SLOW_MS) return false;
        slow = true;
        prefs(c).edit().putInt("slow_for", version(c)).apply();
        boolean was = lite;
        apply();
        return lite && !was;
    }

    /** Whether the frames are still to be measured (Automatic, Full so far, not measured slow this build). */
    static boolean measuring() {
        return choice == AUTO && !lite && !slow;
    }

    private static void apply() {
        String w = autoWhy(gpu, ram, slow);
        why = w;
        lite = choice == LITE || choice == AUTO && !w.isEmpty();
    }

    /** Pure: why Automatic is Lite on a TV with this chip, this memory (bytes, 0 unknown) and frames measured slow ("" Full). */
    static String autoWhy(String renderer, long totalRam, boolean measuredSlow) {
        if (weakGpu(renderer)) return "a " + renderer.trim() + " graphics chip";
        if (totalRam > 0 && totalRam < SMALL_RAM) return String.format(Locale.UK, "%.1f GB of memory", totalRam / (double) (1L << 30));
        if (measuredSlow) return "frames measured slow";
        return "";
    }

    /** Pure: a Mali-G52-class graphics chip (or older or smaller Mali): Mali-G52, G51, the T series, the 400s. */
    static boolean weakGpu(String renderer) {
        if (renderer == null) return false;
        String r = renderer.toLowerCase(Locale.ROOT).replace(" ", "");
        return r.contains("mali-g52") || r.contains("mali-g51") || r.contains("mali-t") || r.contains("mali-4");
    }

    /** Settings' and the log's words: the tier in use and, for Automatic, why ("Lite: a Mali-G52 graphics chip"). */
    static String words() {
        String w = why;
        if (choice == LITE) return "Lite";
        if (choice == FULL) return "Full";
        return lite ? "Lite: " + w : "Full";
    }

    /** What Automatic is on this TV, whatever is chosen: "Full", or "Lite (a Mali-G52 graphics chip)". */
    static String autoWords() {
        String w = why;
        return w.isEmpty() ? "Full" : "Lite (" + w + ")";
    }

    /** The memory log's short word: full, lite, auto-full or auto-lite. */
    static String logWord() {
        return (choice == AUTO ? "auto-" : "") + (lite ? "lite" : "full");
    }

    private static long totalRam(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return 0;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int version(Context c) {
        try {
            android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return (int) (Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode);
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) {
            return 0;
        }
    }

    /** The graphics chip's name (GL_RENDERER) from a tiny off-screen GL context; null when it can't be read. */
    private static String renderer() {
        EGLDisplay d = null;
        EGLContext ctx = null;
        EGLSurface s = null;
        try {
            d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (d == null || d == EGL14.EGL_NO_DISPLAY) return null;
            int[] v = new int[2];
            if (!EGL14.eglInitialize(d, v, 0, v, 1)) return null;
            int[] attrs = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] n = new int[1];
            if (!EGL14.eglChooseConfig(d, attrs, 0, configs, 0, 1, n, 0) || n[0] < 1) return null;
            ctx = EGL14.eglCreateContext(d, configs[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            s = EGL14.eglCreatePbufferSurface(d, configs[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT || s == null || s == EGL14.EGL_NO_SURFACE) return null;
            if (!EGL14.eglMakeCurrent(d, s, s, ctx)) return null;
            String name = GLES20.glGetString(GLES20.GL_RENDERER);
            return name == null ? null : name.trim();
        } catch (RuntimeException | LinkageError e) {
            return null;
        } finally {
            try {
                if (d != null && d != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (s != null && s != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(d, s);
                    if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(d, ctx);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // gone already
            }
        }
    }

    // ---- tests -------------------------------------------------------------------------------------------------------

    /** Tests and previews: the tier at once (as Settings' choice, nothing kept). */
    static void force(boolean on) {
        choice = on ? LITE : FULL;
        lite = on;
    }

    /** Tests: as a fresh start would see this chip and memory. */
    static void pretend(String renderer, long totalRam, boolean measuredSlow) {
        choice = AUTO;
        gpu = renderer == null ? "" : renderer;
        ram = totalRam;
        slow = measuredSlow;
        apply();
    }
}
