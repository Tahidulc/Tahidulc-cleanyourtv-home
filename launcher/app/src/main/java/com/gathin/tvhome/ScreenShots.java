package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pictures of the screen (Android 11 and later), taken through the search helper (AppSearchService), for ChannelSight
 * (the channel's own scoreboard and clock) and LogoShape (the channel's logo, for see-through): on Android 14 and later of
 * TiviMate's window alone, so what the launcher puts over the picture is never in them; before that of the whole
 * screen, the launcher's own windows and all. Only the parts asked for come back, each made small by the TV's graphics
 * chip and read back small: a 4K screen is never copied whole by the CPU. Android allows one only every so often (a
 * third of a second, or a second on older TVs): asked sooner, it says no and the gap here grows to fit. Nothing is ever
 * sent anywhere or written down: each picture is let go as soon as its parts are made (see-through holds a few dozen small
 * ones of the logo's place in memory while it learns the logo's shape, and keeps only that shape).
 */
final class ScreenShots {
    private ScreenShots() {
    }

    /** What came back, on the main thread: the parts asked for (null where one could not be made), all null when none was taken. */
    interface Got {
        void got(Bitmap[] parts, long at, boolean ownWindow);
    }

    /**
     * One part made from every picture of TiviMate's window alone, whoever asked for it (see-through, the ad-break
     * screen, ChannelSight, or the rider itself): the channel's scoreboard watched (BugCover) without pictures of its
     * own most of the time. The part (fractions) made w by h; got on the main thread, which lets the bitmap go.
     */
    static final class Ride {
        final RectF part;
        final int w, h;
        final RideGot got;

        Ride(RectF part, int w, int h, RideGot got) {
            this.part = part;
            this.w = w;
            this.h = h;
            this.got = got;
        }
    }

    interface RideGot {
        void got(Bitmap part, long at);
    }

    private static volatile Ride ride, whole;

    /** The part made from every window picture from now on (null: none). */
    static void ride(Ride r) {
        ride = r;
    }

    /**
     * The whole picture, small, made from every picture from now on (null: none): LivePlay's, through BugCover. From
     * pictures of the whole screen too (before Android 14): what the launcher puts over the channel is small there.
     */
    static void whole(Ride r) {
        whole = r;
    }

    /** The main thread's handler and the scaling paint, made when first wanted (the pure parts load without them, for the tests). */
    private static final class Made {
        static final Handler MAIN = new Handler(Looper.getMainLooper());
        static final Paint SCALE = new Paint(Paint.FILTER_BITMAP_FLAG);
    }
    private static ExecutorService work;
    private static long lastAt;
    /** The shortest gap between two pictures (it grows when Android says one came too soon). */
    private static long gap = 350L;
    private static boolean busy;
    /** The shortest gap there is, and when Android last said a picture came too soon (the gap shrinks back a minute after). */
    private static final long LEAST_GAP = 350L;
    private static long soonAt;
    /** Whether TiviMate's window alone is taken now, or the whole screen for a while after its pictures missed. */
    private static final WindowPause WINDOWS = new WindowPause();
    /** The pictures asked for, the one whose answer is waited for (0: none), and window pictures given up on. */
    private static int asked, waitingFor, lost;
    /** How often a window picture not back yet is looked at, and how many looks before it is given up on (a second). */
    private static final long LOOK_MS = 250L;
    private static final int LOST_LOOKS = 4;
    /** TiviMate's window as last looked for (-1: none) and when: looked for again after a second, not for each picture. */
    private static int windowId = -1;
    private static long windowAt = Long.MIN_VALUE;
    /** Android refused the helper pictures at all (its permission not given): not asked again until it is bound again. */
    private static boolean refused;
    private static Object refusedBy;

    private static ExecutorService work() {
        if (work == null) work = Executors.newSingleThreadExecutor();
        return work;
    }

    /** Whether pictures can be taken now: Android 11 or later, the helper on and allowed to. */
    static boolean available() {
        AccessibilityService s = AppSearchService.helper();
        if (refused && s != refusedBy) refused = false;
        return Build.VERSION.SDK_INT >= 30 && s != null && !refused;
    }

    /** Its state in a few words, for the log: window pictures missed running (or paused), refused, the gap. */
    static String state() {
        return "window misses " + WINDOWS.misses() + (WINDOWS.on(SystemClock.uptimeMillis()) ? "" : ", window pictures paused")
                + ", lost " + lost + ", refused " + refused + ", gap " + gap + " ms, last failure " + lastFailure;
    }

    /**
     * Whether TiviMate's window alone is taken (Android 14). After three of its pictures running came back black or not at
     * all, the whole screen is taken instead for a while (30 s, twice as long each time the window misses again straight
     * after, up to five minutes), then the window is tried again: a hitch (TiviMate busy for a moment, so Android gives up
     * on the picture; a black frame between programmes) never turns window pictures off for good. A good one forgets the
     * misses. Plain Java, for its test.
     */
    static final class WindowPause {
        static final int MISSES = 3;
        static final long FIRST = 30_000L, LONGEST = 300_000L;
        private int misses;
        private long offUntil = Long.MIN_VALUE, next = FIRST;

        /** Whether the window may be taken at now (uptime ms). */
        boolean on(long now) {
            return now >= offUntil;
        }

        /** One of its pictures black, or not taken, at now. */
        void missed(long now) {
            if (++misses < MISSES) return;
            misses = 0;
            offUntil = now + next;
            next = Math.min(LONGEST, next * 2);
        }

        /** A good one: the misses and the longer pauses forgotten. */
        void good() {
            misses = 0;
            next = FIRST;
            seen = true;
        }

        private boolean seen;

        /**
         * Whether a good one has come yet: after that a black one is the picture itself (between channels, a fade),
         * not a window that cannot be taken, and is no miss.
         */
        boolean seen() {
            return seen;
        }

        int misses() {
            return misses;
        }
    }

    private static int lastFailure;

    /** How far apart pictures can be taken, in ms. */
    static long gap() {
        return gap;
    }

    /** Whether a picture now leaves the launcher's own windows out (Android 14, TiviMate's window found, not paused). */
    static boolean windowOnly() {
        AccessibilityService s = AppSearchService.helper();
        return Build.VERSION.SDK_INT >= 34 && s != null && WINDOWS.on(SystemClock.uptimeMillis()) && tivimateWindowNow(s) >= 0;
    }

    /** TiviMate's window (tivimateWindow), looked for at most once a second: asked for by each picture and each look. */
    private static int tivimateWindowNow(AccessibilityService s) {
        long now = SystemClock.uptimeMillis();
        if (now - windowAt >= 1_000L || now < windowAt) {
            windowId = tivimateWindow(s);
            windowAt = now;
        }
        return windowId;
    }

    /** Whether one is under way or the gap since the last is not over. */
    static boolean ready() {
        // An answer that never came (the helper unbound mid-way): not waited for longer than five seconds.
        if (busy && SystemClock.uptimeMillis() - lastAt > 5_000L) busy = false;
        return available() && !busy && SystemClock.uptimeMillis() - lastAt >= gap;
    }

    /**
     * One picture, its parts (each a box in fractions of the screen) made sizes[i] = {width, height} pixels big (a width
     * of 0 keeps the part's own shape at that height); got() on the main thread. False when none could be asked for now
     * (got() is not called then).
     */
    @android.annotation.TargetApi(34)
    static boolean take(RectF[] parts, int[][] sizes, Got got) {
        if (!ready() || parts == null || sizes == null || parts.length != sizes.length) return false;
        // The remote just pressed (a menu, the guide, a list moving): no picture now, so the TV's graphics are all the
        // app's while it moves (a slower TV's scrolling stuttered with pictures taken under it). Asked again shortly.
        if (keysMoving(System.currentTimeMillis(), InputKeyService.lastKeyAt)) return false;
        AccessibilityService s = AppSearchService.helper();
        if (Build.VERSION.SDK_INT < 30 || s == null) return false;
        lastAt = SystemClock.uptimeMillis();
        busy = true;
        shots++;
        long at = System.currentTimeMillis();
        int window = Build.VERSION.SDK_INT >= 34 && WINDOWS.on(lastAt) ? tivimateWindowNow(s) : -1;
        boolean own = window >= 0;
        // This one's answer is the one waited for: any other (one given up on) is let go when it comes.
        int id = ++asked;
        waitingFor = id;
        AtomicBoolean back = new AtomicBoolean();
        AccessibilityService.TakeScreenshotCallback cb = new AccessibilityService.TakeScreenshotCallback() {
            @Override public void onSuccess(AccessibilityService.ScreenshotResult r) {
                back.set(true);
                Bitmap[] out = new Bitmap[parts.length];
                HardwareBuffer hb = null;
                Bitmap hw = null;
                Ride rd = own ? ride : null, wd = whole;
                Bitmap rode = null, all = null;
                try {
                    hb = r.getHardwareBuffer();
                    hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                    if (hw != null) for (int i = 0; i < parts.length; i++) out[i] = part(hw, parts[i], sizes[i][0], sizes[i][1]);
                    if (hw != null && rd != null) rode = part(hw, rd.part, rd.w, rd.h);
                    if (hw != null && wd != null) all = part(hw, wd.part, wd.w, wd.h);
                } catch (RuntimeException | OutOfMemoryError e) {
                    // left as none: the next one may do
                } finally {
                    if (hw != null) hw.recycle();
                    if (hb != null) hb.close();
                }
                boolean black = own && out.length > 0 && out[0] != null && black(out[0]);
                if (rode != null) {
                    Bitmap give = rode;
                    Made.MAIN.post(() -> {
                        // Not for a black window picture (TiviMate's window not taken), nor once the rider has changed.
                        if (black || ride != rd) give.recycle();
                        else rd.got.got(give, at);
                    });
                }
                if (all != null) {
                    Bitmap give = all;
                    boolean dark = black(give);
                    Made.MAIN.post(() -> {
                        if (dark || whole != wd) give.recycle();
                        else wd.got.got(give, at);
                    });
                }
                Made.MAIN.post(() -> {
                    if (waitingFor != id) {
                        // Given up on meanwhile, and another asked for: let go.
                        for (Bitmap b : out) if (b != null) b.recycle();
                        return;
                    }
                    waitingFor = 0;
                    busy = false;
                    long now = SystemClock.uptimeMillis();
                    if (own && black) {
                        if (!WINDOWS.seen()) WINDOWS.missed(now);
                    } else if (own) {
                        WINDOWS.good();
                    }
                    // Told "too soon" once (a late answer, a busy moment): the gap back down bit by bit a minute after.
                    if (gap > LEAST_GAP && now - soonAt > 60_000L) gap = Math.max(LEAST_GAP, gap - 50L);
                    got.got(out, at, own);
                });
            }

            @Override public void onFailure(int code) {
                back.set(true);
                Made.MAIN.post(() -> {
                    // Android's own late word on one given up on (its five seconds over): nothing more to do.
                    if (waitingFor != id) return;
                    waitingFor = 0;
                    lastFailure = code;
                    busy = false;
                    if (code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        gap = Math.min(1_100L, gap + 250L);
                        soonAt = SystemClock.uptimeMillis();
                    } else if (code == AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
                        refused = true;
                        refusedBy = AppSearchService.helper();
                    } else if (own) {
                        // TiviMate's window could not be taken (secure, gone): looked for again, tried again, and after
                        // three running the whole screen for a while.
                        windowAt = Long.MIN_VALUE;
                        WINDOWS.missed(SystemClock.uptimeMillis());
                    }
                    got.got(new Bitmap[parts.length], at, own);
                });
            }
        };
        try {
            if (own) {
                // Android 14 holds its own listener for a window's picture only weakly: when the launcher's memory is
                // tidied before the picture is back, the picture is dropped and Android says so only five seconds later.
                // A mark made with it goes in the same tidy: seen gone with no answer, the picture is given up on at
                // once and the next asked for (a second at most in any case), not five seconds of a still fill.
                WeakReference<Object> mark = new WeakReference<>(new Object());
                s.takeScreenshotOfWindow(window, work(), cb);
                Made.MAIN.postDelayed(new Runnable() {
                    private int looks;

                    @Override public void run() {
                        if (waitingFor != id || back.get()) return;
                        if (mark.get() != null && ++looks < LOST_LOOKS) {
                            Made.MAIN.postDelayed(this, LOOK_MS);
                            return;
                        }
                        waitingFor = 0;
                        busy = false;
                        lost++;
                        got.got(new Bitmap[parts.length], at, true);
                    }
                }, LOOK_MS);
            } else {
                s.takeScreenshot(Display.DEFAULT_DISPLAY, work(), cb);
            }
            return true;
        } catch (RuntimeException e) {
            waitingFor = 0;
            busy = false;
            return false;
        }
    }

    /** Pure: a remote button pressed this recently: pictures wait. */
    static boolean keysMoving(long now, long lastKey) {
        return lastKey > 0 && now - lastKey >= 0 && now - lastKey < KEYS_REST_MS;
    }

    static final long KEYS_REST_MS = 1_500L;

    /**
     * The part f (fractions) of hw, ow by oh (ow 0: oh high in its own shape), scaled by the graphics chip; null if too
     * small. Made by a renderer kept for its size (GpuParts); the old way (a Picture and a renderer of its own for each
     * part) only where that fails.
     */
    @android.annotation.TargetApi(30)
    private static Bitmap part(Bitmap hw, RectF f, int ow, int oh) {
        int w = hw.getWidth(), h = hw.getHeight();
        Rect src = new Rect(Math.max(0, Math.round(f.left * w)), Math.max(0, Math.round(f.top * h)),
                Math.min(w, Math.round(f.right * w)), Math.min(h, Math.round(f.bottom * h)));
        if (src.width() < 2 || src.height() < 2 || oh < 2) return null;
        if (ow <= 0) ow = Math.max(2, Math.round(oh * src.width() / (float) src.height()));
        ow = Math.min(ow, 2048);
        oh = Math.min(oh, 2048);
        parts++;
        Bitmap made = GpuParts.part(hw, src, ow, oh, Made.SCALE);
        idleSoon();
        if (made != null) return made;
        Picture p = new Picture();
        Canvas pc = p.beginRecording(ow, oh);
        pc.drawBitmap(hw, src, new Rect(0, 0, ow, oh), Made.SCALE);
        p.endRecording();
        Bitmap gpu = Bitmap.createBitmap(p, ow, oh, Bitmap.Config.HARDWARE);
        Bitmap soft = gpu.copy(Bitmap.Config.ARGB_8888, false);
        gpu.recycle();
        return soft;
    }

    // ---- how many, and letting go -----------------------------------------------------------------------------------------

    /** Pictures asked for, and parts made from them, since the start (the self-check counts them a minute at a time). */
    static volatile long shots, parts;
    private static boolean idleAsked;

    /** The part renderers looked at again once they may have been idle long enough (GpuParts.IDLE_MS). Work thread. */
    private static void idleSoon() {
        if (idleAsked) return;
        idleAsked = true;
        Made.MAIN.postDelayed(ScreenShots::idleCheck, GpuParts.IDLE_MS + 1_000L);
    }

    private static void idleCheck() {
        work().execute(() -> {
            idleAsked = false;
            if (GpuParts.releaseIdle(SystemClock.uptimeMillis())) idleSoon();
        });
    }

    /** A memory warning (MemoryWatch): the part renderers let go now, made again for the next picture. */
    static void release() {
        if (work == null) return;
        work().execute(GpuParts::releaseAll);
    }

    /** Pure enough: a picture all but black (a window taken without its video). */
    static boolean black(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), lit = 0, n = 0;
        for (int y = 0; y < h; y += Math.max(1, h / 12)) {
            for (int x = 0; x < w; x += Math.max(1, w / 16)) {
                int c = b.getPixel(x, y);
                n++;
                if (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF) > 36) lit++;
            }
        }
        return n > 0 && lit * 50 < n;
    }

    /** TiviMate's window filling the screen (Android 14's window pictures), its id; -1 when there is none. */
    @android.annotation.TargetApi(30)
    private static int tivimateWindow(AccessibilityService s) {
        try {
            List<AccessibilityWindowInfo> ws = s.getWindows();
            int best = -1;
            long most = 0;
            Rect r = new Rect();
            for (AccessibilityWindowInfo w : ws) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = w.getRoot();
                CharSequence pkg = root == null ? null : root.getPackageName();
                if (pkg == null || !ChannelWatch.TIVIMATE.contentEquals(pkg)) continue;
                w.getBoundsInScreen(r);
                long area = (long) r.width() * r.height();
                if (area > most) {
                    most = area;
                    best = w.getId();
                }
            }
            // Only a window filling (nearly) the screen: its fractions are the screen's.
            android.util.DisplayMetrics dm = s.getResources().getDisplayMetrics();
            long screen = (long) dm.widthPixels * dm.heightPixels;
            return best >= 0 && most * 10 >= screen * 9 ? best : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Grey values of a small picture, row by row (for BugFinder). */
    static byte[] grey(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        byte[] out = new byte[w * h];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            out[i] = (byte) ((((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 150 + (c & 0xFF) * 29) >> 8);
        }
        return out;
    }
}
