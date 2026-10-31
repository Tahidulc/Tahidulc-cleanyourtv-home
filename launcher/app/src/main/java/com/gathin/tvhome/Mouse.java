package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.Locale;

/**
 * An on-screen mouse for apps made for a phone's touch screen (Tuya Smart: its camera's buttons take only a touch):
 * while one of them is in front, the remote's arrows move a pointer, faster the longer they are held, and OK presses
 * on the screen where it points; OK held is a long press, and an arrow held at the screen's edge scrolls. The presses
 * go through the TV's own debugging connection (LocalAdb), the only way an app may touch a TV's screen. The apps: the
 * theme's "mouse_apps" (Tuya Smart without one). Keys come from InputKeyService; every other key is the app's.
 */
final class Mouse {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final long FRAME_MS = 16, HIDE_MS = 8_000, LONG_PRESS_MS = 550, SCROLL_MS = 380;

    private static FrameLayout window;
    private static WindowManager.LayoutParams lp;
    private static View arrow;
    private static Context ctx;
    private static float x = -1, y = -1;
    /** Arrows held now (-1, 0 or 1 each way), and since when. */
    private static int dx, dy;
    private static long heldAt, lastScroll, okAt, saidAt;
    /** Keys whose press went to the mouse, so their release does too. */
    private static final boolean[] mine = new boolean[KeyEvent.getMaxKeyCode() + 1];

    private Mouse() { }

    /** A key from the remote (InputKeyService): true when the mouse took it. */
    static boolean handle(Context c, KeyEvent e) {
        int code = e.getKeyCode();
        int ax = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int ay = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ax == 0 && ay == 0 && !ok) return false;
        if (code >= mine.length) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) mine[code] = on(c);
            if (!mine[code]) return false;
            if (e.getRepeatCount() > 0) return true;   // held: the mouse's own timer moves it
            ctx = c.getApplicationContext();
            show();
            if (ok) {
                okAt = System.currentTimeMillis();
                arrow.animate().scaleX(0.82f).scaleY(0.82f).setDuration(90).start();
                return true;
            }
            if (dx == 0 && dy == 0) heldAt = System.currentTimeMillis();
            if (ax != 0) dx = ax;
            if (ay != 0) dy = ay;
            // A tap of an arrow is a small step, for aiming; held, it glides.
            moveBy(ax * 14, ay * 14);
            main.removeCallbacks(glide);
            main.postDelayed(glide, 180);
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_UP || !mine[code]) return false;
        mine[code] = false;
        if (ok) {
            if (arrow != null) arrow.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            press(System.currentTimeMillis() - okAt >= LONG_PRESS_MS);
            return true;
        }
        if (ax != 0 && dx == ax) dx = 0;
        if (ay != 0 && dy == ay) dy = 0;
        if (dx == 0 && dy == 0) main.removeCallbacks(glide);
        return true;
    }

    /** Whether the app in front is one the mouse is for. */
    private static boolean on(Context c) {
        String front = AppSearchService.front();
        if (front == null) return false;
        // Only once the TV lets the launcher press on the screen; till then the remote works in the app as it was.
        if (!LocalAdb.trusted(c)) {
            if (front.equals(CameraAlert.TUYA)) LocalAdb.check(c);
            return false;
        }
        for (String app : Config.themeStrings(c, "mouse_apps", java.util.Collections.singletonList(CameraAlert.TUYA))) {
            if (front.equals(app.trim())) return true;
        }
        hide();
        return false;
    }

    /** The pointer glides while an arrow is held, faster the longer; held against an edge, the screen scrolls. */
    private static final Runnable glide = new Runnable() {
        @Override public void run() {
            if (dx == 0 && dy == 0) return;
            long held = System.currentTimeMillis() - heldAt;
            float speed = Math.min(34f, 5f + held / 28f);
            float bx = x, by = y;
            moveBy(dx * speed, dy * speed);
            if (bx == x && dx != 0 || by == y && dy != 0) scroll();
            main.postDelayed(this, FRAME_MS);
        }
    };

    private static void moveBy(float mx, float my) {
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        x = Math.max(0, Math.min(m.widthPixels - 1, x + mx));
        y = Math.max(0, Math.min(m.heightPixels - 1, y + my));
        place();
    }

    /** Held against an edge: a swipe the other way, under the pointer, as a finger would scroll. */
    private static void scroll() {
        long now = System.currentTimeMillis();
        if (now - lastScroll < SCROLL_MS) return;
        lastScroll = now;
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        int cx = Math.round(x), cy = Math.round(y);
        int fx = dx != 0 ? m.widthPixels / 2 + dx * m.widthPixels / 6 : Math.max(40, Math.min(m.widthPixels - 40, cx));
        int fy = dy != 0 ? m.heightPixels / 2 + dy * m.heightPixels / 6 : Math.max(40, Math.min(m.heightPixels - 40, cy));
        int tx = dx != 0 ? m.widthPixels / 2 - dx * m.widthPixels / 6 : fx;
        int ty = dy != 0 ? m.heightPixels / 2 - dy * m.heightPixels / 6 : fy;
        LocalAdb.shell(ctx, String.format(Locale.ROOT, "input swipe %d %d %d %d 220", fx, fy, tx, ty));
    }

    /** OK: a tap where the pointer is, or a long press, with a ring that spreads from it. */
    private static void press(boolean longPress) {
        int px = Math.round(x), py = Math.round(y);
        LocalAdb.shell(ctx, longPress ? String.format(Locale.ROOT, "input swipe %d %d %d %d 800", px, py, px, py)
                : String.format(Locale.ROOT, "input tap %d %d", px, py));
        ripple();
        // Why nothing happened, when the TV would not take it.
        main.postDelayed(() -> {
            LocalAdb.State s = LocalAdb.state();
            long now = System.currentTimeMillis();
            if (now - saidAt < 10_000 || s == LocalAdb.State.CONNECTED || s == LocalAdb.State.IDLE) return;
            saidAt = now;
            Toast.makeText(ctx, s == LocalAdb.State.NO_DEBUGGING
                    ? "The pointer needs network debugging on (Developer options)"
                    : "Choose Allow (Always allow) on the TV for the pointer to press", Toast.LENGTH_LONG).show();
        }, 2_500);
    }

    // ---- the pointer ---------------------------------------------------------------------------------------------

    /** The tip's place in its window: room for the outline and the ring. */
    private static final int PAD_DP = 22, SIZE_DP = 64;

    private static void show() {
        main.removeCallbacks(hide);
        main.postDelayed(hide, HIDE_MS);
        if (window != null) {
            window.setAlpha(1f);
            return;
        }
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        if (x < 0) { x = m.widthPixels / 2f; y = m.heightPixels / 2f; }
        float d = m.density;
        window = new FrameLayout(ctx);
        window.setClipChildren(false);
        arrow = new ArrowView(ctx, d);
        int pad = Math.round(PAD_DP * d);
        arrow.setPivotX(pad);
        arrow.setPivotY(pad);
        window.addView(arrow, new FrameLayout.LayoutParams(-1, -1));
        int size = Math.round(SIZE_DP * d);
        lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // At most 0.8: a touch passes a window of another app only when it is that see-through (Android 12+), and the
        // presses land right under the pointer.
        lp.alpha = 0.8f;
        lp.setTitle("Mouse");
        position();
        try {
            ctx.getSystemService(WindowManager.class).addView(window, lp);
        } catch (RuntimeException e) {
            window = null;
        }
    }

    private static void place() {
        if (window == null) return;
        position();
        try { ctx.getSystemService(WindowManager.class).updateViewLayout(window, lp); } catch (RuntimeException ignored) { }
    }

    private static void position() {
        int pad = Math.round(PAD_DP * ctx.getResources().getDisplayMetrics().density);
        lp.x = Math.round(x) - pad;
        lp.y = Math.round(y) - pad;
    }

    private static final Runnable hide = Mouse::hide;

    /** The pointer away (left alone a while, or another app in front); it comes back where it was. */
    static void hide() {
        main.removeCallbacks(hide);
        main.removeCallbacks(glide);
        dx = dy = 0;
        FrameLayout w = window;
        window = null;
        if (w == null) return;
        w.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            try { w.getContext().getSystemService(WindowManager.class).removeView(w); } catch (RuntimeException ignored) { }
        }).start();
    }

    /** A ring that spreads from the tip as it presses. */
    private static void ripple() {
        if (window == null) return;
        float d = ctx.getResources().getDisplayMetrics().density;
        View ring = new View(ctx);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setStroke(Math.round(2.5f * d), Color.WHITE);
        g.setColor(0x33FFFFFF);
        ring.setBackground(g);
        int r = Math.round(18 * d), pad = Math.round(PAD_DP * d);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(r * 2, r * 2);
        rl.leftMargin = pad - r;
        rl.topMargin = pad - r;
        window.addView(ring, 0, rl);
        ring.setScaleX(0.3f);
        ring.setScaleY(0.3f);
        FrameLayout w = window;
        ring.animate().scaleX(1.1f).scaleY(1.1f).alpha(0f).setDuration(380).withEndAction(() -> w.removeView(ring)).start();
    }

    /** The arrow: white with a dark outline and a soft shadow, its tip the point that presses. */
    private static final class ArrowView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ArrowView(Context c, float d) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);   // the shadow
            float k = 1.55f * d, pad = PAD_DP * d;
            float[][] pts = {{0, 0}, {0, 17}, {4.2f, 13.2f}, {7.2f, 20f}, {10.2f, 18.7f}, {7.3f, 12.2f}, {12.6f, 12.2f}};
            path.moveTo(pad + pts[0][0] * k, pad + pts[0][1] * k);
            for (int i = 1; i < pts.length; i++) path.lineTo(pad + pts[i][0] * k, pad + pts[i][1] * k);
            path.close();
            fill.setColor(Color.WHITE);
            fill.setShadowLayer(4 * d, 0, 1.5f * d, 0x99000000);
            edge.setColor(0xFF111214);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.4f * d);
            edge.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawPath(path, fill);
            c.drawPath(path, edge);
        }
    }

    /** For a test from adb (WatchHistory's stand-in notifications): moves to x,y and presses there. */
    static void test(Context c, String what) {
        String[] p = what.trim().split("\\s+");
        main.post(() -> {
            ctx = c.getApplicationContext();
            show();
            if (p.length >= 3) {
                x = Float.parseFloat(p[1]);
                y = Float.parseFloat(p[2]);
                place();
            }
            if ("tap".equals(p[0])) press(false);
        });
    }
}
