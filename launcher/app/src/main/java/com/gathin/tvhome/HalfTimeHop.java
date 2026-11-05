package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Half-time hop: at half time in the game being watched (as it shows on screen), a card offers the other games on now
 * (the followed ones, still playing) and Stay. Up and Down choose, OK hops: that game's channel goes on, and as the
 * first game's second half kicks off on its channel (the scores see it, then its channel's wait, InStep), its channel
 * goes back on by itself. Back, or a minute with nothing chosen, stays. Off in Match mode's card when not wanted.
 */
final class HalfTimeHop {
    private HalfTimeHop() { }

    private static final long WAIT_MS = 60_000L;
    /** The tune takes a few seconds: back on this long before the second half shows. */
    private static final long TUNE_MS = 6_000L;
    private static Handler main;

    /** The main thread's handler, made when first wanted (the tests use the sums without Android's looper). */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static FrameLayout card;
    private static final List<TextView> options = new ArrayList<>();
    private static final List<ScoresTicker.Game> others = new ArrayList<>();
    private static int chosen;
    private static Context app;
    private static ScoresTicker.Game from;
    private static final Runnable away = HalfTimeHop::close;

    /** Where it hopped from: the game, its channel's names, and that channel's wait. */
    private static String awayGame;
    private static List<String> backNames = new ArrayList<>();
    private static long backHold;
    private static long awayAt;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("half_time_hop", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) { return prefs(c).getBoolean("on", true); }

    static void setOn(Context c, boolean v) { prefs(c).edit().putBoolean("on", v).apply(); }

    /** Pure: the games to offer: on now, not at half time themselves, not the game itself, at most three. */
    static List<ScoresTicker.Game> choices(ScoresTicker.Game g, List<ScoresTicker.Game> all) {
        List<ScoresTicker.Game> out = new ArrayList<>();
        for (ScoresTicker.Game x : all) {
            if (x == null || x.id == null || x.id.equals(g.id) || x.state != 0 || x.halftime) continue;
            out.add(x);
            if (out.size() == 3) break;
        }
        return out;
    }

    /** Half time in the game watched, as it shows (main thread): the card, when another game is on. */
    static void offer(Context c, ScoresTicker.Game g, List<ScoresTicker.Game> all) {
        if (!on(c) || card != null) return;
        List<ScoresTicker.Game> l = choices(g, all);
        if (l.isEmpty()) return;
        app = c.getApplicationContext();
        from = g;
        others.clear();
        others.addAll(l);
        chosen = 0;
        show();
    }

    private static void show() {
        Context sw = new GoalWatchService.SoftwareWindow(app);
        float d = app.getResources().getDisplayMetrics().density;
        FrameLayout win = new FrameLayout(sw);
        LinearLayout box = new LinearLayout(sw);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * d);
        box.setPadding(pad, Math.round(20 * d), pad, Math.round(20 * d));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF70E1426, 0xF7080B18});
        glass.setCornerRadius(22 * d);
        glass.setStroke(Math.max(1, Math.round(d)), 0x40FFFFFF);
        box.setBackground(glass);
        box.addView(line(sw, "HALF TIME  ·  " + score(from), 14, 0xCCFFFFFF, true));
        TextView ask = line(sw, "Hop to another game for the break?", 22, 0xFFFFFFFF, true);
        ask.setPadding(0, Math.round(6 * d), 0, Math.round(4 * d));
        box.addView(ask);
        box.addView(line(sw, "Back on " + name(from) + " as the second half kicks off on your screen", 14, 0xB3FFFFFF, false));
        options.clear();
        for (int i = 0; i <= others.size(); i++) {
            String words = i < others.size() ? score(others.get(i)) + "   " + minute(others.get(i)) : "Stay here";
            TextView o = line(sw, words, 18, 0xFFFFFFFF, true);
            o.setPadding(Math.round(18 * d), Math.round(12 * d), Math.round(18 * d), Math.round(12 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(460 * d), -2);
            lp.topMargin = Math.round(10 * d);
            box.addView(o, lp);
            options.add(o);
        }
        paint();
        win.addView(box, new FrameLayout.LayoutParams(-2, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(40 * d);
        lp.y = Math.round(40 * d);
        lp.setTitle("HalfTimeHop");
        try {
            app.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(app, "the half-time card could not open: " + e);
            return;
        }
        card = win;
        PopupMotion.in(box, 10 * d);
        QuietHold.shown(app, win, HalfTimeHop::close);
        if (!InputKeyService.running) AppSearchService.keysFor("hop", true);
        main().postDelayed(away, WAIT_MS);
    }

    private static void paint() {
        float d = app.getResources().getDisplayMetrics().density;
        for (int i = 0; i < options.size(); i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * d);
            bg.setColor(i == chosen ? 0x33FFFFFF : 0x14FFFFFF);
            if (i == chosen) bg.setStroke(Math.round(2 * d), 0xFFFFFFFF);
            options.get(i).setBackground(bg);
        }
    }

    /** Up, Down, OK and Back while the card is up; other keys go on to the app. */
    static boolean key(KeyEvent e) {
        if (card == null) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(HalfTimeHop::close);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) main().post(() -> {
                chosen = Math.max(0, Math.min(options.size() - 1, chosen + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
                paint();
            });
            return true;
        }
        if (!QuietHold.isOk(code)) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(HalfTimeHop::choose);
        return true;
    }

    private static void choose() {
        if (card == null) return;
        int i = chosen;
        close();
        if (i >= others.size() || from == null) return;
        ScoresTicker.Game to = others.get(i);
        TvGuide.Watch w = TvGuide.watch(app.getResources().getStringArray(R.array.football_tv_channels), to);
        if (w == null || w.names.isEmpty()) return;
        awayGame = from.id;
        backNames = InStep.tunedNames(app);
        long h = InStep.current(app);
        backHold = h >= 0 ? h : 30_000L;
        awayAt = System.currentTimeMillis();
        AlertLog.note(app, "half-time hop to " + AlertLog.game(to) + ", back for " + AlertLog.game(from));
        AppSearchService.tune(app, "ar.tvplayer.tv", w.names, null);
    }

    /** Whether the watched game was hopped away from (for its second half). */
    static boolean away(String gameId) {
        if (awayGame == null || !awayGame.equals(gameId)) return false;
        if (System.currentTimeMillis() - awayAt > 2 * 3_600_000L) { awayGame = null; return false; }
        return true;
    }

    /** Its second half seen by the scores (main thread): its channel back on as the second half shows there. */
    static void secondHalf(Context c, ScoresTicker.Game g) {
        if (!away(g.id)) return;
        awayGame = null;
        List<String> names = new ArrayList<>(backNames);
        if (names.isEmpty()) {
            TvGuide.Watch w = TvGuide.watch(c.getResources().getStringArray(R.array.football_tv_channels), g);
            if (w != null) names.addAll(w.names);
        }
        if (names.isEmpty()) return;
        Context a = c.getApplicationContext();
        main().postDelayed(() -> {
            AlertLog.note(a, "half-time hop: back for the second half of " + AlertLog.game(g));
            AppSearchService.tune(a, "ar.tvplayer.tv", names, null);
        }, Math.max(0, backHold - TUNE_MS));
    }

    static void close() {
        main().removeCallbacks(away);
        FrameLayout v = card;
        card = null;
        if (v == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("hop", false);
        try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
    }

    private static String name(ScoresTicker.Game g) {
        return MatchMode.label(g);
    }

    /** "Arsenal 2–1 Chelsea". */
    static String score(ScoresTicker.Game g) {
        String h = g.homeName != null ? g.homeName : g.home, a = g.awayName != null ? g.awayName : g.away;
        return h + " " + (g.homeScore == null ? "0" : g.homeScore) + "–" + (g.awayScore == null ? "0" : g.awayScore) + " " + a;
    }

    private static String minute(ScoresTicker.Game g) {
        return g.detail == null ? "Live" : g.detail;
    }

    private static TextView line(Context c, String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colour);
        v.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return v;
    }
}
