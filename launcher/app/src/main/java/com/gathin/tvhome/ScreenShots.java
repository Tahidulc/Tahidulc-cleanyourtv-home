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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures of the screen (Android 11 and later), taken through the search helper (AppSearchService), for ChannelSight
 * (the channel's own scoreboard and clock) and LogoPatch (what is round the channel's logo): on Android 14 and later of
 * TiviMate's window alone, so what the launcher puts over the picture is never in them; before that of the whole
 * screen, the launcher's own windows and all. Only the parts asked for come back, each made small by the TV's graphics
 * chip and read back small: a 4K screen is never copied whole by the CPU. Android allows one only every so often (a
 * third of a second, or a second on older TVs): asked sooner, it says no and the gap here grows to fit. Nothing is ever
 * kept or sent anywhere: each picture is let go as soon as its parts are made.
 */
final class ScreenShots {
    private ScreenShots() {
    }

    /** What came back, on the main thread: the parts asked for (null where one could not be made), all null when none was taken. */
    interface Got {
        void got(Bitmap[] parts, long at, boolean ownWindow);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Paint SCALE = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static ExecutorService work;
    private static long lastAt;
    /** The shortest gap between two pictures (it grows when Android says one came too soon). */
    private static long gap = 350L;
    private static boolean busy;
    /** TiviMate's window alone came back black this many times running: the whole screen is taken from then on. */
    private static int blackWindows;
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

    /** How far apart pictures can be taken, in ms. */
    static long gap() {
        return gap;
    }

    /** Whether a picture now leaves the launcher's own windows out (Android 14, TiviMate's window found, not black). */
    static boolean windowOnly() {
        AccessibilityService s = AppSearchService.helper();
        return Build.VERSION.SDK_INT >= 34 && s != null && blackWindows < 3 && tivimateWindow(s) >= 0;
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
        AccessibilityService s = AppSearchService.helper();
        if (Build.VERSION.SDK_INT < 30 || s == null) return false;
        lastAt = SystemClock.uptimeMillis();
        busy = true;
        long at = System.currentTimeMillis();
        int window = Build.VERSION.SDK_INT >= 34 && blackWindows < 3 ? tivimateWindow(s) : -1;
        boolean own = window >= 0;
        AccessibilityService.TakeScreenshotCallback cb = new AccessibilityService.TakeScreenshotCallback() {
            @Override public void onSuccess(AccessibilityService.ScreenshotResult r) {
                Bitmap[] out = new Bitmap[parts.length];
                HardwareBuffer hb = null;
                Bitmap hw = null;
                try {
                    hb = r.getHardwareBuffer();
                    hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                    if (hw != null) for (int i = 0; i < parts.length; i++) out[i] = part(hw, parts[i], sizes[i][0], sizes[i][1]);
                } catch (RuntimeException | OutOfMemoryError e) {
                    // left as none: the next one may do
                } finally {
                    if (hw != null) hw.recycle();
                    if (hb != null) hb.close();
                }
                boolean black = own && out.length > 0 && out[0] != null && black(out[0]);
                MAIN.post(() -> {
                    busy = false;
                    if (own) blackWindows = black ? blackWindows + 1 : 0;
                    got.got(out, at, own);
                });
            }

            @Override public void onFailure(int code) {
                MAIN.post(() -> {
                    busy = false;
                    if (code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        gap = Math.min(1_100L, gap + 250L);
                    } else if (code == AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
                        refused = true;
                        refusedBy = AppSearchService.helper();
                    } else if (own) {
                        // TiviMate's window could not be taken (secure, gone): the whole screen from now on.
                        blackWindows = 3;
                    }
                    got.got(new Bitmap[parts.length], at, own);
                });
            }
        };
        try {
            if (own) s.takeScreenshotOfWindow(window, work(), cb);
            else s.takeScreenshot(Display.DEFAULT_DISPLAY, work(), cb);
            return true;
        } catch (RuntimeException e) {
            busy = false;
            return false;
        }
    }

    /** The part f (fractions) of hw, ow by oh (ow 0: oh high in its own shape), scaled by the graphics chip; null if too small. */
    @android.annotation.TargetApi(30)
    private static Bitmap part(Bitmap hw, RectF f, int ow, int oh) {
        int w = hw.getWidth(), h = hw.getHeight();
        Rect src = new Rect(Math.max(0, Math.round(f.left * w)), Math.max(0, Math.round(f.top * h)),
                Math.min(w, Math.round(f.right * w)), Math.min(h, Math.round(f.bottom * h)));
        if (src.width() < 2 || src.height() < 2 || oh < 2) return null;
        if (ow <= 0) ow = Math.max(2, Math.round(oh * src.width() / (float) src.height()));
        ow = Math.min(ow, 2048);
        oh = Math.min(oh, 2048);
        Picture p = new Picture();
        Canvas pc = p.beginRecording(ow, oh);
        pc.drawBitmap(hw, src, new Rect(0, 0, ow, oh), SCALE);
        p.endRecording();
        Bitmap gpu = Bitmap.createBitmap(p, ow, oh, Bitmap.Config.HARDWARE);
        Bitmap soft = gpu.copy(Bitmap.Config.ARGB_8888, false);
        gpu.recycle();
        return soft;
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
