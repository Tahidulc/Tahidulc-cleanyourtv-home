package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Pop-up mode (launcher v2, a TV with the key helper): an app opened from Home with Up pressed twice (on the rail or on
 * a tile) shows inside Home's frame. Home's navy round its edges with the app in a rounded window in the middle, the
 * score pills across the top (where Home's alerts come in as on Home) and the six boxes along the foot, and the
 * remote's Back key in the top corner, as the alerts have it. The app keeps the remote: everything in it works as
 * ever. Down twice takes the remote to the boxes, Up twice to the score pills (OK opens either, Back returns to the
 * app). Back twice in a row leaves pop-up mode for Home. Home coming back to the front ends it.
 */
final class PopupFrame {
    private static final int TOP = 104, FOOT = 150, SIDE = 28, RADIUS = 26;
    private static final long TWICE_MS = 450L, BACK_TWICE_MS = 1_500L;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Context app;
    private static FrameLayout host;
    private static WindowManager.LayoutParams params;
    private static ScoresTicker scores;
    private static final List<View> boxes = new ArrayList<>();
    private static TextView backHint;
    private static View backBox;
    private static float k;
    /** Whether the frame has the remote (its boxes or pills), not the app. */
    private static boolean focused;
    private static long downAt, upAt, backAt;
    private static int swallowUp;

    private PopupFrame() { }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    static boolean isShowing() {
        return host != null;
    }

    /** Whether pop-up mode can work here: v2 and the key helper (it keeps the remote with the app). */
    static boolean available() {
        return InputKeyService.running;
    }

    // ---- open and close -------------------------------------------------------------------------------------------

    static void show(Context ctx) {
        if (host != null) return;
        app = ctx.getApplicationContext();
        Context c = app;
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        FrameLayout win = new FrameLayout(c);
        // No box or pill takes Android's focus (or looks chosen) until the frame has the remote.
        win.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        win.addView(new Frame(c), new FrameLayout.LayoutParams(-1, -1));

        // The score pills across the top, as on Home (Home's alerts take their place there).
        String[] leagues = c.getResources().getStringArray(R.array.score_leagues);
        if (leagues.length > 0) {
            scores = new ScoresTicker(c, win, c.getResources().getDisplayMetrics().density, leagues, new ScoresTicker.Host() {
                @Override public void onScoresOpen(ScoresTicker.Game g) { leaveFocus(); watch(g); }
                @Override public void onScoresDown() { if (!boxes.isEmpty()) boxes.get(0).requestFocus(); }
            });
            scores.setTypeface(c.getResources().getFont(R.font.inter_ui));
            scores.equalWidths(true);
            scores.showF1(Config.themeFlag(c, "f1_scores_bar", false));
            scores.showAlerts(false);
            float dp = c.getResources().getDisplayMetrics().density;
            win.post(() -> { if (scores != null) scores.place(px(96), Math.round(23 * dp), win.getWidth() - px(170)); });
        }

        // The remote's Back key in the top right corner, "twice" beside it.
        LinearLayout back = new LinearLayout(c);
        back.setGravity(Gravity.CENTER_VERTICAL);
        backHint = text(c, "×2", 17, 0xCCFFFFFF);
        back.addView(backHint, new LinearLayout.LayoutParams(-2, -2));
        ImageView key = new ImageView(c);
        key.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), px(34)));
        key.setTag(PopupMotion.BACK_KEY);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(34), px(34));
        kl.leftMargin = px(8);
        back.addView(key, kl);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bl.rightMargin = px(40);
        bl.topMargin = px(28);
        win.addView(back, bl);
        backBox = back;

        // The six boxes along the foot, Home's own, a little lower.
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER);
        boxes.clear();
        for (int i = 0; i < 6; i++) {
            final int which = i;
            FrameLayout box = new FrameLayout(c);
            box.setClipToOutline(true);
            box.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(16)); }
            });
            box.setFocusable(true);
            View strip = new View(c);
            strip.setBackgroundColor(HomeV2.CAT_COLOURS[i]);
            box.addView(strip, new FrameLayout.LayoutParams(-1, px(5)));
            LinearLayout in = new LinearLayout(c);
            in.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(c);
            ic.setImageResource(HomeV2.CAT_ICONS[i]);
            ic.setColorFilter(Color.WHITE);
            in.addView(ic, new LinearLayout.LayoutParams(px(32), px(32)));
            TextView t = text(c, HomeV2.CAT_NAMES[i], 24, 0xFFFFFFFF);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.leftMargin = px(12);
            in.addView(t, tl);
            box.addView(in, new FrameLayout.LayoutParams(-1, -1));
            look(box, false);
            box.setOnFocusChangeListener((v, has) -> {
                look((FrameLayout) v, has);
                v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).setInterpolator(HomeV2.SKY).start();
            });
            box.setOnClickListener(v -> openBox(which));
            box.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_UP) { if (scores == null || !scores.focus()) leaveFocus(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && which == 0) return true;
                return code == KeyEvent.KEYCODE_DPAD_RIGHT && which == 5;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(256), px(96));
            if (i > 0) lp.leftMargin = px(14);
            row.addView(box, lp);
            boxes.add(box);
        }
        row.setClipChildren(false);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, px(96), Gravity.BOTTOM);
        rl.bottomMargin = px(27);
        win.addView(row, rl);
        win.setClipChildren(false);

        params = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not FLAG_NOT_TOUCHABLE: Android caps such an overlay at 80% opacity, and the app showed through the
                // frame. A TV has no touch screen to block.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.setTitle("PopupFrame");
        try {
            c.getSystemService(WindowManager.class).addView(win, params);
        } catch (RuntimeException e) {
            scores = null;
            return;
        }
        host = win;
        focused = false;
        backAt = 0;
        // Home's alerts come in across the top, in the pills' place, as on Home.
        if (scores != null) {
            scores.start();
            // An alert across the top: the pills and the Back key make way (the alert has its own Back key).
            GoalWatchService.laneListener = on -> {
                if (scores != null) scores.cover(on);
                if (backBox != null) backBox.animate().alpha(on ? 0f : 1f).setDuration(on ? 160 : 320).start();
            };
        }
        win.setAlpha(0f);
        win.animate().alpha(1f).setDuration(300).start();
    }

    /** Pop-up mode over: the frame away (Home itself, coming back, also calls this). */
    static void close() {
        FrameLayout v = host;
        host = null;
        focused = false;
        if (scores != null) {
            scores.stop();
            if (GoalWatchService.laneListener != null) GoalWatchService.laneListener = null;
            scores = null;
        }
        boxes.clear();
        if (v == null) return;
        v.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
        }).start();
    }

    /** Back twice: out of pop-up mode, to Home. */
    private static void leave() {
        close();
        try {
            app.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    /** A box chosen: out of pop-up mode, and Home opens on that box's section (0 the TV guide). */
    private static void openBox(int which) {
        close();
        try {
            app.startActivity(new Intent(app, MainActivity.class).putExtra("open_box", which)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (RuntimeException ignored) {
        }
    }

    /** A game's pill chosen: its channel put on (pop-up mode stays, now over TiviMate). */
    private static void watch(ScoresTicker.Game g) {
        new Thread(() -> {
            Object[] w = GoalWatchService.watchForGame(app, g);
            if (w == null) return;
            main.post(() -> {
                try {
                    if (w.length > 2 && w[2] instanceof Runnable) { ((Runnable) w[2]).run(); return; }
                    @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
                    String pkg = app.getString(R.string.scores_open_package);
                    GoalWatchService.onAppOpened(app, pkg);
                    AppSearchService.tune(app, pkg, names, (android.graphics.Bitmap) w[1]);
                } catch (RuntimeException ignored) {
                }
            });
        }).start();
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    /**
     * Every key first (InputKeyService). With the app: everything goes on to it but Back pressed twice (out), Down
     * pressed twice (to the boxes) and Up pressed twice (to the pills); the second press of each stays here. With
     * the frame: its own keys, Back back to the app.
     */
    static boolean key(KeyEvent e) {
        if (host == null) return false;
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_UP && swallowUp == code) { swallowUp = 0; return true; }
        if (focused) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) main.post(PopupFrame::leaveFocus);
                return true;
            }
            return false;   // on to the frame's own window, which has the focus
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() > 0) return false;
        long now = SystemClock.uptimeMillis();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (now - backAt < BACK_TWICE_MS) {
                swallowUp = code;
                backAt = 0;
                main.post(PopupFrame::leave);
                return true;
            }
            backAt = now;
            main.post(PopupFrame::hintBack);
            return false;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (now - downAt < TWICE_MS) { swallowUp = code; downAt = 0; main.post(() -> takeFocus(true)); return true; }
            downAt = now;
            return false;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (now - upAt < TWICE_MS) { swallowUp = code; upAt = 0; main.post(() -> takeFocus(false)); return true; }
            upAt = now;
            return false;
        }
        return false;
    }

    /** The remote to the frame: its window takes the focus, on the first box (or the first score pill). */
    private static void takeFocus(boolean toBoxes) {
        if (host == null) return;
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { app.getSystemService(WindowManager.class).updateViewLayout(host, params); } catch (RuntimeException e) { return; }
        focused = true;
        host.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        host.post(() -> {
            if (host == null) return;
            if (!toBoxes && scores != null && scores.focus()) return;
            if (!boxes.isEmpty()) boxes.get(0).requestFocus();
        });
    }

    /** The remote back to the app. */
    private static void leaveFocus() {
        if (host == null || !focused) return;
        View f = host.findFocus();
        if (f != null) f.clearFocus();
        host.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { app.getSystemService(WindowManager.class).updateViewLayout(host, params); } catch (RuntimeException ignored) { }
        focused = false;
    }

    /** adb's test hook: the remote to the boxes or the pills, back to the app, or out. */
    static void test(String what) {
        if ("boxes".equals(what)) takeFocus(true);
        else if ("pills".equals(what)) takeFocus(false);
        else if ("app".equals(what)) leaveFocus();
        else if ("leave".equals(what)) leave();
    }

    /** After one Back: "×2" lights up a moment, so it is plain that one more leaves. */
    private static void hintBack() {
        if (backHint == null) return;
        backHint.setTextColor(0xFFFFD166);
        backHint.animate().scaleX(1.3f).scaleY(1.3f).setDuration(160).withEndAction(() ->
                backHint.animate().scaleX(1f).scaleY(1f).setDuration(220).start()).start();
        main.postDelayed(() -> { if (backHint != null) backHint.setTextColor(0xCCFFFFFF); }, BACK_TWICE_MS);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private static void look(FrameLayout box, boolean on) {
        GradientDrawable b = new GradientDrawable();
        b.setCornerRadius(px(16));
        b.setColor(on ? 0x38FFFFFF : 0x14FFFFFF);
        b.setStroke(Math.max(1, px(on ? 3 : 1)), on ? 0xFFFFFFFF : 0x24FFFFFF);
        box.setBackground(b);
    }

    private static TextView text(Context c, String s, float sizePx, int colour) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * k);
        t.setTextColor(colour);
        t.setTypeface(c.getResources().getFont(R.font.inter_ui));
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    /** Home's navy all round, with a rounded window cut in the middle where the app shows, a fine rim round it. */
    private static final class Frame extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Frame(Context c) {
            super(c);
            rim.setStyle(Paint.Style.STROKE);
            rim.setColor(0x40FFFFFF);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF161C70, 0xFF10155A, 0xFF0C1048}, null, Shader.TileMode.CLAMP));
            rim.setStrokeWidth(Math.max(1, px(1.5f)));
            path.reset();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.addRect(0, 0, w, h, Path.Direction.CW);
            path.addRoundRect(new RectF(px(SIDE), px(TOP), w - px(SIDE), h - px(FOOT)), px(RADIUS), px(RADIUS), Path.Direction.CW);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawPath(path, fill);
            c.drawRoundRect(new RectF(px(SIDE), px(TOP), getWidth() - px(SIDE), getHeight() - px(FOOT)), px(RADIUS), px(RADIUS), rim);
        }
    }
}
