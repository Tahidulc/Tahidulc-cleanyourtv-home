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
 * ever, Back included (apps go back with it, often twice in a row). Back held down a moment brings the frame in with
 * the remote on the boxes (Up to the score pills, OK opens either); there Back once returns to the app, Back twice in
 * a row leaves pop-up mode for Home. Home coming back to the front ends it too.
 *
 * The frame hides itself: the app is never resized (only the system's own tools can put an app in a window), so the
 * frame shows only while it has the remote, the app whole on the screen the rest of the time and nothing left
 * standing on it (no screen burn-in); as pop-up mode opens, a small pill says how to bring it in. Home's alerts come
 * across the top all the same.
 */
final class PopupFrame {
    private static final int TOP = 104, FOOT = 150, SIDE = 28, RADIUS = 26;
    private static final long BACK_TWICE_MS = 1_500L;

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
    private static long backAt;
    private static final long HIDE_MS = 5_000L;
    /** The frame's parts that come and go, and whether they show now. */
    private static View frameView, boxRow;
    private static boolean chrome;
    private static int swallowUp;

    private PopupFrame() { }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    static boolean isShowing() {
        return host != null;
    }

    /**
     * Whether pop-up mode can work here: with the key helper, or (a TCL, which has none) with the channel helper
     * taking the keys while pop-up mode is open (AppSearchService.keysForPopup). Either keeps the remote with the app.
     */
    static boolean available() {
        return InputKeyService.running || AppSearchService.running();
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
        frameView = new Frame(c);
        frameView.setAlpha(0f);
        win.addView(frameView, new FrameLayout.LayoutParams(-1, -1));

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
        boxRow = row;
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
        if (!InputKeyService.running) AppSearchService.keysForPopup(true);
        // Home's alerts come in across the top, in the pills' place, as on Home.
        if (scores != null) {
            scores.start();
            // An alert across the top: the pills and the Back key make way (the alert has its own Back key).
            GoalWatchService.laneListener = on -> {
                if (scores != null && (on || chrome)) scores.cover(on);
                if (backBox != null && (on || chrome)) backBox.animate().alpha(on ? 0f : 1f).setDuration(on ? 160 : 320).start();
            };
        }
        // The app whole from the start: no frame over it, only a word on how to bring Home's boxes in.
        chrome = false;
        frameView.setVisibility(View.INVISIBLE);
        if (boxRow != null) { boxRow.setAlpha(0f); boxRow.setVisibility(View.INVISIBLE); }
        if (backBox != null) { backBox.setAlpha(0f); backBox.setVisibility(View.INVISIBLE); }
        if (scores != null) scores.cover(true);
        hint(c, win);
    }

    /** Pop-up mode over: the frame away (Home itself, coming back, also calls this). */
    static void close() {
        main.removeCallbacks(hide);
        if (host != null && !InputKeyService.running) AppSearchService.keysForPopup(false);
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
        // The Back this frame sent on to the app itself (a short press, below): on its way, untouched.
        if (passBack && code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) passBack = false;
            return false;
        }
        if (focused) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                    long now = SystemClock.uptimeMillis();
                    main.removeCallbacks(backToApp);
                    if (now - backAt < BACK_TWICE_MS) {
                        backAt = 0;
                        swallowUp = code;
                        main.post(PopupFrame::leave);
                    } else {
                        // Once: "x2" lights; again soon leaves pop-up mode, else the remote goes back to the app.
                        backAt = now;
                        main.post(PopupFrame::hintBack);
                        main.postDelayed(backToApp, BACK_TWICE_MS);
                    }
                }
                return true;
            }
            return false;   // on to the frame's own window, which has the focus
        }
        // With the app: every key is the app's but Back held down a moment, which brings the frame in. A short Back
        // is held back until it is let go, then sent on to the app as the one Back it was.
        if (code != KeyEvent.KEYCODE_BACK) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                held = false;
                main.removeCallbacks(holdBack);
                main.postDelayed(holdBack, HOLD_MS);
            }
            return true;
        }
        main.removeCallbacks(holdBack);
        if (!held && !e.isCanceled()) {
            passBack = true;
            main.postDelayed(() -> passBack = false, 800L);
            AppSearchService.globalBack();
        }
        held = false;
        return true;
    }

    private static final long HOLD_MS = 700L;
    private static boolean held, passBack;
    private static final Runnable holdBack = () -> { held = true; takeFocus(true); };
    private static final Runnable backToApp = PopupFrame::leaveFocus;

    /** The remote to the frame: its window takes the focus, on the first box (or the first score pill). */
    private static void takeFocus(boolean toBoxes) {
        if (host == null) return;
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { app.getSystemService(WindowManager.class).updateViewLayout(host, params); } catch (RuntimeException e) { return; }
        focused = true;
        main.removeCallbacks(hide);
        showChrome();
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
        main.removeCallbacks(backToApp);
        main.removeCallbacks(hide);
        main.postDelayed(hide, 400L);
    }

    /** The frame in: the navy, the pills, the boxes and the Back key fade and slide into place over the app. */
    private static void showChrome() {
        if (host == null) return;
        chrome = true;
        host.setVisibility(View.VISIBLE);
        if (hintView != null) { hintView.setVisibility(View.GONE); hintView = null; }
        for (View v : new View[]{frameView, boxRow, backBox}) {
            if (v == null) continue;
            v.setVisibility(View.VISIBLE);
            v.animate().cancel();
            v.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(HomeV2.SKY).start();
        }
        if (scores != null) scores.cover(false);
    }

    /** The frame away, the app whole again: nothing of it left on the screen (the alerts still come across the top). */
    private static final Runnable hide = () -> {
        if (host == null || focused) return;
        chrome = false;
        if (scores != null) scores.cover(true);
        if (boxRow != null) boxRow.animate().alpha(0f).translationY(px(40)).setDuration(420).start();
        for (View v : new View[]{frameView, backBox}) {
            if (v == null) continue;
            v.animate().alpha(0f).setDuration(420).withEndAction(() -> { if (!chrome) v.setVisibility(View.INVISIBLE); }).start();
        }
        // Once faded, the window is not drawn at all (its surface hidden), so nothing costs the TV anything over the app.
        if (boxRow != null) boxRow.postDelayed(() -> {
            if (chrome || host == null) return;
            if (boxRow != null) boxRow.setVisibility(View.INVISIBLE);
            if (backBox == null || backBox.getVisibility() != View.VISIBLE) host.setVisibility(View.GONE);
        }, 450L);
    };

    /** adb's test hook: the remote to the boxes or the pills, back to the app, or out. */
    static void test(String what) {
        if ("boxes".equals(what)) takeFocus(true);
        else if ("pills".equals(what)) takeFocus(false);
        else if ("app".equals(what)) leaveFocus();
        else if ("leave".equals(what)) leave();
        else if ("close".equals(what)) close();
    }

    private static View hintView;

    /** "Pop-up mode · hold Back for Home": a small pill at the top for four seconds as pop-up mode opens. */
    private static void hint(Context c, FrameLayout win) {
        LinearLayout pill = new LinearLayout(c);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(px(22), px(10), px(22), px(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6141A6B);
        bg.setCornerRadius(px(30));
        bg.setStroke(Math.max(1, px(1.5f)), 0x52FFFFFF);
        pill.setBackground(bg);
        pill.addView(text(c, "Pop-up mode  ·  hold", 20, 0xFFFFFFFF), new LinearLayout.LayoutParams(-2, -2));
        ImageView key = new ImageView(c);
        key.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), px(30)));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(30), px(30));
        kl.leftMargin = px(10);
        kl.rightMargin = px(10);
        pill.addView(key, kl);
        pill.addView(text(c, "for Home", 20, 0xFFFFFFFF), new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pl.topMargin = px(28);
        win.addView(pill, pl);
        hintView = pill;
        pill.setAlpha(0f);
        pill.setTranslationY(-px(20));
        pill.animate().alpha(1f).translationY(0).setDuration(300).start();
        main.postDelayed(() -> {
            if (hintView != pill) return;
            pill.animate().alpha(0f).translationY(-px(20)).setDuration(300).withEndAction(() -> {
                pill.setVisibility(View.GONE);
                hintView = null;
                if (!chrome && host != null) host.setVisibility(View.GONE);
            }).start();
        }, 4_000L);
    }

    /** After one Back: "×2" lights up a moment, so it is plain that one more leaves. */
    private static void hintBack() {
        if (backHint == null) return;
        if (!chrome && backBox != null) {
            host.setVisibility(View.VISIBLE);
            backBox.setVisibility(View.VISIBLE);
            backBox.animate().cancel();
            backBox.setAlpha(1f);
            backBox.postDelayed(() -> {
                if (!chrome && backBox != null) backBox.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    if (chrome || backBox == null) return;
                    backBox.setVisibility(View.INVISIBLE);
                    if (host != null) host.setVisibility(View.GONE);
                }).start();
            }, BACK_TWICE_MS);
        }
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
