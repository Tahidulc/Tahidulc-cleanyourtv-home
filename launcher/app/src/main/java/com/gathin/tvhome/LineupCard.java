package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The starting elevens before kick-off, after Sky Sports' Premier League line-ups, small in the bottom left corner
 * over whatever is on. A navy band down the left (the competition, the club's crest, its name up the side); beside it
 * the players a page at a time on a pale streaked ground, head and shoulders, each with his number and flag above
 * and first name and surname below; then both elevens together on one pitch in their shapes, faces, numbers and
 * names; last the two clubs, the kick-off and the channel. The pages slide by themselves, a line along the foot
 * filling as each runs; OK at any time watches the game (InputKeyService passes it here; on a TV without that helper,
 * on the last page). Sized from the screen's width, so it is the same on every TV.
 */
final class LineupCard {
    /** Everything it shows, fetched before it opens (GoalWatchService, off the main thread). */
    static final class Data {
        Bitmap leagueLogo;
        Team home, away;
        String kickoff, channel, venue, competition;
        long kickoffAt;
        Bitmap channelLogo;
        /** OK: the game's channel opened (null without one). */
        Runnable watch;
        String remote = "";
    }

    static final class Team {
        String name, formation = "", kitName = "Home kit";
        Bitmap crest;
        /** The kit it wears today: shirt body and sleeves (0 when not known), for its colours on the pitch. */
        int kit, sleeves;
        /** The kit its players' photos show (its home kit): a player without a photo wears it too, like the rest. */
        int photoKit, photoSleeves;
        final List<Player> players = new ArrayList<>();
    }

    static final class Player {
        String first = "", last = "", number = "", position = "";
        Bitmap photo, flag;
        /** A photo rather than a cut-out (Wikipedia's, cut to the face): shown framed on shoulders in the kit. */
        boolean framed;
    }

    private static final int NAVY = 0xFF151A63;
    /** The card's size in the design's pixels on a 1920-wide screen, and its scale there. */
    private static final int W = 700, H = 232, BAND = 84;
    private static final float SCALE = 0.88f;
    /** How long each page stays: the fixture, each half eleven, and the pitch (long, for time to press OK). */
    private static final long FIRST_MS = 11_000L, PLAYERS_MS = 8_000L, PITCH_MS = 16_000L;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static FrameLayout host;
    private static Data showing;
    private static float k;
    /** Whether the one showing is Home's bar (it gives the score pills their place back when it goes). */
    private static boolean bar;
    /** The bar's streaks drifting, stopped when it goes. */
    private static final List<android.animation.Animator> drifting = new ArrayList<>();

    private LineupCard() { }

    /** Whether the card is on screen (the score cards wait for it). */
    static boolean isShowing() {
        return host != null;
    }

    /** Drawn smaller than it came: with mip-maps it scales down smoothly instead of jagged (see ScoreCard). */
    private static Bitmap sharp(Bitmap b) {
        if (b != null) b.setHasMipMap(true);
        return b;
    }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    /**
     * The remote's OK while the card shows (InputKeyService, before any app): the game's channel opened. True when
     * the card took the key.
     */
    static boolean key(KeyEvent e) {
        Data d = showing;
        if (host == null || d == null) return false;
        int code = e.getKeyCode();
        // Back: its key on the card dips as the button goes down, and the card shrinks away towards it as it comes up.
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) main.post(() -> PopupMotion.press(host));
            else if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> close(true, true));
            return true;
        }
        if (d.watch == null) return false;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (!ok) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> {
            close(true);
            d.watch.run();
        });
        return true;
    }

    /** The card's height while it shows (the camera card goes above it); 0 otherwise. */
    static int shownHeight() {
        return host != null ? host.getHeight() : 0;
    }

    /** Opens the card (on the main thread), in place of one already showing; after the camera card, if that is up. */
    static void show(Context ctx, Data d) {
        Context c = ctx.getApplicationContext();
        if (CameraAlert.isShowing()) {
            main.postDelayed(() -> show(c, d), 2_000L);
            return;
        }
        close(false);
        wipeBody = 0xFF151A63;
        wipeEdge = 0xFF3D4BD6;
        java.io.File shots = renderTo;
        renderTo = null;
        // On Home: the same line-ups as a wide bar across the top, in the style of Home's score bars.
        if (shots == null && (GoalWatchService.laneHome && GoalWatchService.homeInFront || PopupFrame.isShowing())) { showBar(c, d); return; }
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f * SCALE;
        int w = px(W), h = px(H), margin = px(10);

        FrameLayout win = new FrameLayout(c);
        FrameLayout card = new FrameLayout(c);
        float r = px(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF0D1360);

        // The band down the left, its contents changed with each page; the stage beside it, where pages slide.
        FrameLayout band = new FrameLayout(c);
        band.setBackground(new Streaks(0xFF1B2591, 0xFF0D1360, 0x12FFFFFF, px(21), px(3)));
        card.addView(band, new FrameLayout.LayoutParams(px(BAND), h));
        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(true);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(w - px(BAND), h);
        sl.leftMargin = px(BAND);
        card.addView(stage, sl);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, h);
        cl.setMargins(margin, margin, margin, margin);
        win.addView(card, cl);
        // The remote's Back key on the top right corner: Back closes the card.
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), 2 * margin));
        back.setTag(PopupMotion.BACK_KEY);
        win.addView(back, new FrameLayout.LayoutParams(2 * margin, 2 * margin, Gravity.TOP | Gravity.END));

        if (shots != null) {
            photograph(c, d, win, band, stage, w, h, margin, shots);
            return;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w + 2 * margin, h + 2 * margin,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = px(34);
        lp.y = px(34);
        lp.setTitle("LineupCard");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        host = win;
        showing = d;

        // The pages: the fixture and its channel (OK to watch), each eleven in two (the keeper and defence first), then
        // both on one pitch. Each comes in behind a slanted wipe across the stage, as Sky's line-ups change.
        List<Runnable> pages = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        pages.add(() -> turn(c, stage, () -> { band.removeAllViews(); league(c, band, d, true); stage.addView(first(c, d), full()); }));
        times.add(FIRST_MS);
        for (Team t : new Team[]{d.home, d.away}) {
            if (t == null || t.players.isEmpty()) continue;
            // A page a line: the keeper and the defence, the midfield, the attack (a striker or two with the midfield).
            List<String> captions = new ArrayList<>();
            List<List<Player>> lines = byLines(t, captions);
            for (int i = 0; i < lines.size(); i++) {
                List<Player> ps = lines.get(i);
                String caption = captions.get(i);
                pages.add(() -> turn(c, stage, () -> { teamBand(c, band, t, caption); stage.addView(players(c, t, ps), full()); }));
                // The keeper's page is short; with three lines after it, each a little shorter than with two.
                times.add("GOALKEEPER".equals(caption) ? 5_000L : lines.size() > 3 ? PLAYERS_MS - 1_500L : PLAYERS_MS);
            }
        }
        if (d.home != null && d.away != null && !d.home.players.isEmpty() && !d.away.players.isEmpty()) {
            pages.add(() -> turn(c, stage, () -> { pitchBand(c, band, d); stage.addView(pitch(c, d), full()); }));
            times.add(PITCH_MS);
        }
        // Without the key helper (a TV that has only the screen helper), the first page takes the keys: OK watches.
        boolean helper = InputKeyService.running;
        int[] at = {0};
        Runnable step = new Runnable() {
            @Override public void run() {
                if (host != win) return;
                int i = at[0];
                pages.get(i).run();
                // Without the key helper (the TCL): the card takes the remote's keys while it shows, OK watching
                // (when there is a channel), Back or any other key closing it.
                if (i == 0 && !helper) offerWatch(win, d);
                if (i == pages.size() - 1) {
                    main.postDelayed(() -> { if (host == win) close(true); }, times.get(i));
                    return;
                }
                at[0]++;
                main.postDelayed(this, times.get(i));
            }
        };
        card.setAlpha(0f);
        card.setTranslationX(-px(40));
        card.animate().alpha(1f).translationX(0f).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
        step.run();
    }

    /** adb only: where the next card is photographed, page by page, instead of shown (lineup_*.png); or null. */
    static java.io.File renderTo;

    /**
     * adb only: the card over a pitch (standing in for an app), photographed on a display of its own as the screen would
     * draw it, one picture a page (the fixture, each side's lines, both on the pitch); nothing shown.
     */
    private static void photograph(Context c, Data d, FrameLayout win, FrameLayout band, FrameLayout stage, int w, int h, int margin, java.io.File dir) {
        List<Runnable> swaps = new ArrayList<>();
        swaps.add(() -> { band.removeAllViews(); league(c, band, d, true); stage.addView(first(c, d), full()); });
        for (Team t : new Team[]{d.home, d.away}) {
            if (t == null || t.players.isEmpty()) continue;
            List<String> captions = new ArrayList<>();
            List<List<Player>> lines = byLines(t, captions);
            for (int i = 0; i < lines.size(); i++) {
                List<Player> ps = lines.get(i);
                String caption = captions.get(i);
                swaps.add(() -> { teamBand(c, band, t, caption); stage.addView(players(c, t, ps), full()); });
            }
        }
        if (d.home != null && d.away != null && !d.home.players.isEmpty() && !d.away.players.isEmpty())
            swaps.add(() -> { pitchBand(c, band, d); stage.addView(pitch(c, d), full()); });
        int sw = c.getResources().getDisplayMetrics().widthPixels, sh = c.getResources().getDisplayMetrics().heightPixels;
        FrameLayout root = new FrameLayout(c);
        ImageView ground = new ImageView(c);
        ground.setScaleType(ImageView.ScaleType.FIT_XY);
        ground.setImageBitmap(Snap.pitch(sw, sh));
        root.addView(ground, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(w + 2 * margin, h + 2 * margin, Gravity.BOTTOM | Gravity.START);
        wl.leftMargin = px(34);
        wl.bottomMargin = px(34);
        root.addView(win, wl);
        List<Runnable> steps = new ArrayList<>();
        List<java.io.File> out = new ArrayList<>();
        for (int i = 0; i < swaps.size(); i++) {
            Runnable swap = swaps.get(i);
            steps.add(() -> { stage.removeAllViews(); swap.run(); });
            out.add(new java.io.File(dir, "lineup_" + i + ".png"));
        }
        // Its pages fill and move only while the card is up: up for the photographs, let go of after.
        host = win;
        showing = d;
        Snap.series(c, root, sw, sh, steps, out, 2_400L, () -> {
            if (host == win) { host = null; showing = null; }
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(dir, "lineup_done.txt"))) { o.write('1'); } catch (Exception ignored) { }
        });
    }

    // ---- on Home: the bar across the top ---------------------------------------------------------------------------

    private static final int INK = 0xFF0A0D33, INK_SOFT = 0xFF2B3170;
    /** The bar's pages: the fixture, then each eleven (all of it at once, by its lines). */
    private static final long BAR_FIRST_MS = 9_000L, BAR_TEAM_MS = 13_000L;

    /**
     * The line-ups on Home, in the style of Home's score bars and no taller than fits above the clock: a long pill
     * across the top on the pale streaked ground, the navy band at its left end (the competition, then the club's
     * crest and shape) with its stripes drifting, the LINE-UPS label, and the pages turning behind the card's wipe:
     * the fixture with the kick-off and the channel (OK watches), then each eleven across the bar by its lines, each
     * player's head and shoulders, number and surname. The streaks drift on layers of their own. Back closes it.
     */
    private static void showBar(Context c, Data d) {
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        int w = px(1728), h = px(80), bandW = px(130);
        float r = h / 2f;
        FrameLayout win = new FrameLayout(c);
        FrameLayout box = new FrameLayout(c);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        box.setClipToOutline(true);
        box.setBackground(new Streaks(0xFFF6F7FD, 0xFFD3D6F3, 0, px(50), px(30)));
        box.addView(driftingLines(c, 0x4DFFFFFF, px(50), px(30), h), driftParams(px(50) + px(30), h));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // The band: its navy, its fine stripes drifting along on a layer of their own, the mark over them.
        FrameLayout band = new FrameLayout(c);
        band.setBackground(new Streaks(0xFF1B2591, 0xFF0D1360, 0, px(14), px(3)));
        band.setClipChildren(true);
        band.addView(driftingLines(c, 0x17FFFFFF, px(14), px(3), h), driftParams(px(14) + px(3), h));
        FrameLayout mark = new FrameLayout(c);
        band.addView(mark, new FrameLayout.LayoutParams(-1, -1));
        row.addView(band, new LinearLayout.LayoutParams(bandW, h));
        row.addView(barLabel(c, d, h), new LinearLayout.LayoutParams(-2, h));
        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(true);
        row.addView(stage, new LinearLayout.LayoutParams(0, h, 1f));
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), px(24)));
        back.setTag(PopupMotion.BACK_KEY);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(24), px(24));
        bl.leftMargin = px(10);
        bl.rightMargin = px(16);
        row.addView(back, bl);
        box.addView(row, new FrameLayout.LayoutParams(w, h));
        win.addView(box, new FrameLayout.LayoutParams(w, h));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = px(12);
        lp.setTitle("LineupCard");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            for (android.animation.Animator a : drifting) a.cancel();
            drifting.clear();
            return;
        }
        host = win;
        showing = d;
        bar = true;
        ScoreCard.lane("lineup", true);
        for (android.animation.Animator a : drifting) a.start();

        List<Runnable> pages = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        pages.add(() -> turn(c, stage, () -> { barBand(c, mark, d.leagueLogo, null); stage.addView(barFirst(c, d), full()); }));
        times.add(BAR_FIRST_MS);
        for (Team t : new Team[]{d.home, d.away}) {
            if (t == null || t.players.isEmpty()) continue;
            pages.add(() -> turn(c, stage, () -> { barBand(c, mark, t.crest, t.formation); stage.addView(barTeam(c, t), full()); }));
            times.add(BAR_TEAM_MS);
        }
        boolean helper = InputKeyService.running;
        int[] at = {0};
        Runnable step = new Runnable() {
            @Override public void run() {
                if (host != win) return;
                int i = at[0];
                pages.get(i).run();
                if (i == 0 && !helper) offerWatch(win, d);
                if (i == pages.size() - 1) {
                    main.postDelayed(() -> { if (host == win) close(true); }, times.get(i));
                    return;
                }
                at[0]++;
                main.postDelayed(this, times.get(i));
            }
        };
        box.setAlpha(0f);
        box.setTranslationY(-px(24));
        box.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
        step.run();
    }

    /** Streaks alone on a layer, a period wider than their place, drifting one period to the right and again. */
    private static View driftingLines(Context c, int colour, int gap, int width, int h) {
        View lines = new View(c);
        lines.setBackground(new Streaks(0, 0, colour, gap, width));
        lines.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        int period = Math.max(4, gap) + Math.max(1, width);
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(lines, View.TRANSLATION_X, 0f, period);
        a.setDuration(Math.max(900, Math.round(period / (float) px(30) * 1_000)));
        a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.setStartDelay(300);
        drifting.add(a);
        return lines;
    }

    private static FrameLayout.LayoutParams driftParams(int period, int h) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, h);
        p.leftMargin = -period;
        return p;
    }

    /** The band's mark: big in the middle (the competition's), or the club's crest with its shape under it. */
    private static void barBand(Context c, FrameLayout band, Bitmap mark, String formation) {
        band.removeAllViews();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean shape = formation != null && !formation.isEmpty();
        col.addView(crest(c, mark), new LinearLayout.LayoutParams(px(shape ? 44 : 52), px(shape ? 44 : 52)));
        if (shape) {
            TextView f = text(c, formation, 13, 0xFFFFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            f.setLetterSpacing(0.1f);
            f.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
            fl.topMargin = px(3);
            col.addView(f, fl);
        }
        band.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        col.setAlpha(0f);
        col.animate().alpha(1f).setDuration(380).withLayer().start();
    }

    /** LINE-UPS on the slanted navy label, the kick-off under it. */
    private static View barLabel(Context c, Data d, int h) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(px(16), 0, px(26), 0);
        v.setBackground(new ScoreCard.Slant(NAVY, px(16), false));
        v.addView(text(c, "LINE-UPS", 23, 0xFFFFFFFF, ScoreCard.outfit(c, 900)), new LinearLayout.LayoutParams(-2, -2));
        if (d.kickoff != null) {
            TextView s = text(c, "KICK-OFF " + d.kickoff, 10, 0xD9FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            s.setLetterSpacing(0.16f);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.topMargin = px(3);
            v.addView(s, sl);
        }
        return v;
    }

    /**
     * The fixture: the home crest, "Tottenham Hotspur v Crystal Palace" with the ground, "Starts in 15 mins" and the
     * kick-off under it (fading in slowly), the away crest; at the right end the channel and "Press OK to watch".
     */
    private static View barFirst(Context c, Data d) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(px(22), 0, 0, 0);
        View home = crest(c, d.home == null ? null : d.home.crest);
        page.addView(home, new LinearLayout.LayoutParams(px(56), px(56)));
        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        String names = (d.home == null ? "" : d.home.name) + " v " + (d.away == null ? "" : d.away.name);
        mid.addView(text(c, names, 25, INK, ScoreCard.outfit(c, 800)), new LinearLayout.LayoutParams(-2, -2));
        StringBuilder under = new StringBuilder();
        if (d.venue != null && !d.venue.isEmpty()) under.append(d.venue);
        String starts = startsIn(d.kickoffAt);
        if (starts != null) under.append(under.length() > 0 ? "  ·  " : "").append(starts);
        if (d.kickoff != null) under.append(under.length() > 0 ? "  ·  " : "").append("Kick-off ").append(d.kickoff);
        if (under.length() > 0) {
            TextView when = text(c, under.toString(), 16, INK_SOFT, ScoreCard.outfit(c, 600));
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
            kl.topMargin = px(3);
            mid.addView(when, kl);
            when.setAlpha(0f);
            when.animate().alpha(1f).setStartDelay(950).setDuration(1_400).setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
        }
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.leftMargin = ml.rightMargin = px(20);
        page.addView(mid, ml);
        View away = crest(c, d.away == null ? null : d.away.crest);
        page.addView(away, new LinearLayout.LayoutParams(px(56), px(56)));
        page.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        // The channel on the darkest ground, as on the score bars: its logo and "Press OK to watch".
        View ch = channel(c, d, 20);
        if (ch != null || d.watch != null) {
            LinearLayout foot = new LinearLayout(c);
            foot.setOrientation(LinearLayout.HORIZONTAL);
            foot.setGravity(Gravity.CENTER_VERTICAL);
            foot.setPadding(px(30), 0, px(18), 0);
            foot.setBackground(new ScoreCard.Slant(0xFF0E0F14, px(16), true));
            if (ch != null) foot.addView(ch, new LinearLayout.LayoutParams(-2, px(20)));
            if (d.watch != null) {
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(26));
                ol.leftMargin = px(ch != null ? 14 : 0);
                foot.addView(okPill(c, 12, 19), ol);
            }
            page.addView(foot, new LinearLayout.LayoutParams(-2, -1));
        }
        home.setTranslationX(-px(40)); away.setTranslationX(px(40));
        home.setAlpha(0f); away.setAlpha(0f); mid.setAlpha(0f);
        home.animate().translationX(0).alpha(1f).setStartDelay(200).setDuration(460).setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        away.animate().translationX(0).alpha(1f).setStartDelay(200).setDuration(460).setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        mid.animate().alpha(1f).setStartDelay(400).setDuration(400).withLayer().start();
        return page;
    }

    /** One eleven across the bar, its lines (keeper, defence, midfield, attack) apart by a thin rule. */
    private static View barTeam(Context c, Team t) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(px(10), px(3), px(4), px(3));
        List<String> captions = new ArrayList<>();
        List<List<Player>> lines = byLines(t, captions);
        int n = 0;
        for (int i = 0; i < lines.size(); i++) {
            List<Player> ps = lines.get(i);
            if (i > 0) {
                View rule = new View(c);
                rule.setBackgroundColor(0x33151A63);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Math.max(1, px(2)), -1);
                rl.leftMargin = rl.rightMargin = px(5);
                rl.topMargin = rl.bottomMargin = px(8);
                page.addView(rule, rl);
            }
            LinearLayout group = new LinearLayout(c);
            group.setOrientation(LinearLayout.HORIZONTAL);
            for (Player p : ps) {
                View cell = barPlayer(c, t, p);
                group.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
                cell.setAlpha(0f);
                cell.setTranslationX(px(30));
                cell.animate().alpha(1f).translationX(0f).setStartDelay(120L + 55L * n++).setDuration(360)
                        .setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
            }
            page.addView(group, new LinearLayout.LayoutParams(0, -1, ps.size()));
        }
        return page;
    }

    /** One player on the bar: head and shoulders, his number at the corner, his surname under him. */
    private static View barPlayer(Context c, Team t, Player p) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout frame = new FrameLayout(c);
        frame.setClipChildren(true);
        Bitmap shown = p.photo == null ? avatar(t.photoKit != 0 ? t.photoKit : t.kit, t.photoKit != 0 ? t.photoSleeves : t.sleeves, p.number)
                : p.framed ? framed(p.photo, t.kit) : p.photo;
        ImageView photo = new ImageView(c);
        photo.setImageBitmap(sharp(shown));
        boolean upright = shown != null && shown.getHeight() > shown.getWidth() * 1.05f;
        int pw = upright ? px(48) : px(58), ph = upright && shown != null ? Math.round(pw * shown.getHeight() / (float) shown.getWidth()) : px(58);
        photo.setScaleType(upright ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_START);
        frame.addView(photo, new FrameLayout.LayoutParams(pw, ph, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        TextView num = text(c, p.number, 13, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        nl.leftMargin = px(4);
        nl.topMargin = px(2);
        frame.addView(num, nl);
        col.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView last = text(c, p.last, 13, INK, ScoreCard.outfit(c, 700));
        last.setGravity(Gravity.CENTER);
        last.setSingleLine(false);
        last.setMaxLines(1);
        last.setEllipsize(null);
        last.setAutoSizeTextTypeUniformWithConfiguration(px(8), px(13), 1, TypedValue.COMPLEX_UNIT_PX);
        col.addView(last, new LinearLayout.LayoutParams(-1, px(16)));
        return col;
    }

    // ---- F1: a race's full result, and the starting grid before it -------------------------------------------------

    /** An F1 board: a race's (or sprint's) full result, or its starting grid before lights out. */
    static final class F1 {
        boolean grid;
        /** "Mexico City Grand Prix"; RACE or SPRINT. */
        String gp = "", session = "RACE";
        /** The grid's lights out; 0 for a result. */
        long startAt;
        /** The F1 mark in white. */
        Bitmap mark;
        final List<F1Results.Row> rows = new ArrayList<>();
        /** Team logos by their address; photos by position (the podium's, the front rows'). */
        final java.util.Map<String, Bitmap> logos = new java.util.HashMap<>();
        final java.util.Map<Integer, Bitmap> photos = new java.util.HashMap<>();
        String channel;
        Bitmap channelLogo;
        Runnable watch;
    }

    /** Sky Sports F1's colours: F1's red, and a plain black ground (nothing on it moves). */
    private static final int F1_RED = 0xFFE10600, F1_RED_DEEP = 0xFF8E0400, F1_BLACK = 0xFF141519, F1_BLACK_DEEP = 0xFF0B0C0F,
            F1_SLOT = 0xFF24252C;
    private static final Handler retry = new Handler(Looper.getMainLooper());

    /** Opens an F1 board (on the main thread): the card in the corner over an app, or on Home the bar across the top. */
    static void showF1(Context ctx, F1 f) {
        Context c = ctx.getApplicationContext();
        // One at a time: after a score card, a camera alarm or an older alert (news, weather) has gone.
        if (CameraAlert.isShowing() || ScoreCard.busy() || GoalWatchService.alertShowing()) {
            retry.postDelayed(() -> showF1(c, f), 2_000L);
            return;
        }
        close(false);
        wipeBody = F1_BLACK;
        wipeEdge = F1_RED;
        Data carrier = new Data();
        carrier.watch = f.watch;
        carrier.channel = f.channel;
        carrier.channelLogo = f.channelLogo;
        if (GoalWatchService.laneHome && GoalWatchService.homeInFront || PopupFrame.isShowing()) f1Bar(c, f, carrier); else f1Card(c, f, carrier);
    }

    private static boolean sprint(F1 f) {
        return "SPRINT".equals(f.session);
    }

    private static String lightsOut(long at) {
        if (at <= 0) return null;
        long min = Math.round((at - System.currentTimeMillis()) / 60_000.0);
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(new java.util.Date(at));
        if (min <= 0) return "Lights out now  ·  " + time;
        return "Lights out in " + min + (min == 1 ? " min" : " mins") + "  ·  " + time;
    }

    private static View f1Channel(Context c, F1 f, int height) {
        Data cd = new Data();
        cd.channel = f.channel;
        cd.channelLogo = f.channelLogo;
        return channel(c, cd, height);
    }

    /** A page's steps: each page put in behind the wipe, the next after its time, the board closing after the last. */
    private static void run(FrameLayout win, Data carrier, List<Runnable> pages, List<Long> times) {
        boolean helper = InputKeyService.running;
        int[] at = {0};
        Runnable step = new Runnable() {
            @Override public void run() {
                if (host != win) return;
                int i = at[0];
                pages.get(i).run();
                if (i == 0 && !helper) offerWatch(win, carrier);
                if (i == pages.size() - 1) {
                    main.postDelayed(() -> { if (host == win) close(true); }, times.get(i));
                    return;
                }
                at[0]++;
                main.postDelayed(this, times.get(i));
            }
        };
        step.run();
    }

    // -- over an app: the card -----------------------------------------------------------------------------------------

    private static void f1Card(Context c, F1 f, Data carrier) {
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f * SCALE;
        int w = px(W), h = px(H), margin = px(10);
        FrameLayout win = new FrameLayout(c);
        FrameLayout card = new FrameLayout(c);
        float r = px(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        card.setBackground(new Streaks(F1_BLACK, F1_BLACK_DEEP, 0, px(60), px(4)));
        FrameLayout band = new FrameLayout(c);
        band.setBackground(new Streaks(F1_RED, F1_RED_DEEP, 0x1AFFFFFF, px(14), px(3)));
        card.addView(band, new FrameLayout.LayoutParams(px(BAND), h));
        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(true);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(w - px(BAND), h);
        sl.leftMargin = px(BAND);
        card.addView(stage, sl);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, h);
        cl.setMargins(margin, margin, margin, margin);
        win.addView(card, cl);
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), 2 * margin));
        back.setTag(PopupMotion.BACK_KEY);
        win.addView(back, new FrameLayout.LayoutParams(2 * margin, 2 * margin, Gravity.TOP | Gravity.END));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w + 2 * margin, h + 2 * margin,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = px(34);
        lp.y = px(34);
        lp.setTitle("LineupCard");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        host = win;
        showing = carrier;
        bar = false;

        List<Runnable> pages = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        int n = f.rows.size();
        if (f.grid) {
            pages.add(() -> turn(c, stage, () -> { f1Band(c, band, f, "STARTING\nGRID"); stage.addView(f1Title(c, f), full()); }));
            times.add(9_000L);
            int[][] spans = {{0, 4}, {4, 12}, {12, 22}};
            for (int[] sp : spans) {
                final int a = sp[0], b = Math.min(sp[1], n);
                if (a >= b) continue;
                pages.add(() -> turn(c, stage, () -> { f1Band(c, band, f, "P" + (a + 1) + "–P" + b); stage.addView(f1Slots(c, f, a, b), full()); }));
                times.add(a == 0 ? 9_000L : 10_000L);
            }
        } else {
            pages.add(() -> turn(c, stage, () -> { f1Band(c, band, f, "PODIUM"); stage.addView(f1Podium(c, f), full()); }));
            times.add(10_000L);
            if (n > 3) {
                final int b = Math.min(10, n);
                pages.add(() -> turn(c, stage, () -> { f1Band(c, band, f, "P4–P" + b + "\nPOINTS"); stage.addView(f1List(c, f, 3, b, 1), full()); }));
                times.add(10_000L);
            }
            if (n > 10) {
                pages.add(() -> turn(c, stage, () -> { f1Band(c, band, f, "P11–P" + n); stage.addView(f1List(c, f, 10, n, 2), full()); }));
                times.add(11_000L);
            }
        }
        card.setAlpha(0f);
        card.setTranslationX(-px(40));
        card.animate().alpha(1f).translationX(0f).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
        run(win, carrier, pages, times);
    }

    /** The red band: the F1 mark, RACE or SPRINT, and which part of the board this page is. */
    private static void f1Band(Context c, FrameLayout band, F1 f, String caption) {
        band.removeAllViews();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView mark = new ImageView(c);
        mark.setImageBitmap(sharp(f.mark));
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        col.addView(mark, new LinearLayout.LayoutParams(px(64), px(17)));
        TextView ses = text(c, f.session, 10, 0xE6FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        ses.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = px(6);
        col.addView(ses, sl);
        TextView cap = text(c, caption, 12.5f, 0xFFFFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        cap.setLetterSpacing(0.08f);
        cap.setSingleLine(false);
        cap.setMaxLines(2);
        cap.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cpl = new LinearLayout.LayoutParams(px(BAND) - px(6), -2);
        cpl.topMargin = px(16);
        col.addView(cap, cpl);
        band.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        col.setAlpha(0f);
        col.animate().alpha(1f).setDuration(360).withLayer().start();
    }

    /** The grid's first page: STARTING GRID, the Grand Prix, "Lights out in 15 mins · 20:00", the channel and OK. */
    private static View f1Title(Context c, F1 f) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.addView(text(c, sprint(f) ? "SPRINT GRID" : "STARTING GRID", 32, 0xFFFFFFFF, ScoreCard.outfit(c, 900)), new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.topMargin = px(4);
        page.addView(text(c, f.gp, 15, 0xCCFFFFFF, inter(c, R.font.inter_semibold)), gl);
        String lights = lightsOut(f.startAt);
        if (lights != null) {
            TextView when = text(c, lights, 19, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
            wl.topMargin = px(10);
            page.addView(when, wl);
            when.setAlpha(0f);
            when.animate().alpha(1f).setStartDelay(900).setDuration(1_300).setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
        }
        View ch = f1Channel(c, f, 22);
        if (ch != null || f.watch != null) {
            LinearLayout foot = new LinearLayout(c);
            foot.setGravity(Gravity.CENTER_VERTICAL);
            if (ch != null) foot.addView(ch, new LinearLayout.LayoutParams(-2, px(22)));
            if (f.watch != null) {
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(26));
                ol.leftMargin = px(ch != null ? 12 : 0);
                foot.addView(okPill(c, 12, 19), ol);
            }
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
            fl.topMargin = px(14);
            page.addView(foot, fl);
        }
        return page;
    }

    /** The podium: P2, P1 and P3 side by side, each his photo, position and surname, team logo and time or gap. */
    private static View f1Podium(Context c, F1 f) {
        FrameLayout page = new FrameLayout(c);
        TextView head = text(c, (f.gp + "  ·  " + (sprint(f) ? "Sprint" : "Race") + " result").toUpperCase(java.util.Locale.UK), 11,
                0xB3FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        head.setLetterSpacing(0.12f);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hl.leftMargin = px(14);
        hl.topMargin = px(10);
        page.addView(head, hl);
        if (f.watch != null || f.channelLogo != null) {
            LinearLayout chip = new LinearLayout(c);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            View ch = f1Channel(c, f, 13);
            if (ch != null) chip.addView(ch, new LinearLayout.LayoutParams(-2, px(13)));
            if (f.watch != null) {
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(18));
                ol.leftMargin = px(8);
                chip.addView(okPill(c, 9.5f, 14), ol);
            }
            FrameLayout.LayoutParams cpl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            cpl.rightMargin = px(18);
            cpl.topMargin = px(6);
            page.addView(chip, cpl);
        }
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(6), 0, px(6), px(6));
        int[] order = {1, 0, 2};
        int n = 0;
        for (int idx : order) {
            if (idx >= f.rows.size()) continue;
            View e = f1PodiumPlace(c, f, f.rows.get(idx), idx == 0);
            row.addView(e, new LinearLayout.LayoutParams(0, -1, idx == 0 ? 1.15f : 1f));
            e.setAlpha(0f);
            e.setTranslationY(px(18));
            e.animate().alpha(1f).translationY(0f).setStartDelay(idx == 0 ? 420L : 180L + 90L * n++).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
        }
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, -1);
        rl.topMargin = px(28);
        page.addView(row, rl);
        return page;
    }

    private static View f1PodiumPlace(Context c, F1 f, F1Results.Row r, boolean winner) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        // The same size for all three, their tops level: the photos are framed alike, so the faces line up.
        col.addView(f1Photo(c, f, r, px(112), px(112)), new LinearLayout.LayoutParams(px(112), px(112)));
        LinearLayout name = new LinearLayout(c);
        name.setGravity(Gravity.CENTER_VERTICAL);
        TextView pos = text(c, String.valueOf(r.pos), 15, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        pos.setGravity(Gravity.CENTER);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(winner ? F1_RED : 0xFF3A3C46);
        pb.setCornerRadius(px(4));
        pos.setBackground(pb);
        name.addView(pos, new LinearLayout.LayoutParams(px(22), px(20)));
        TextView last = text(c, r.last.toUpperCase(java.util.Locale.UK), 16, 0xFFFFFFFF, ScoreCard.outfit(c, 800));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.leftMargin = px(7);
        name.addView(last, ll);
        LinearLayout.LayoutParams nml = new LinearLayout.LayoutParams(-2, -2);
        nml.topMargin = px(4);
        col.addView(name, nml);
        LinearLayout sub = new LinearLayout(c);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        sub.addView(f1Logo(c, f, r, px(19)), new LinearLayout.LayoutParams(px(19), px(19)));
        String gap = r.gap.isEmpty() ? (winner ? "Winner" : "") : r.gap;
        TextView g = text(c, gap, 12, 0xCCFFFFFF, ScoreCard.outfit(c, 600));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.leftMargin = px(6);
        sub.addView(g, gl);
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(-2, -2);
        sbl.topMargin = px(3);
        col.addView(sub, sbl);
        return col;
    }

    /**
     * A driver's head and shoulders in a box of its own size (w by h): his photo (they are all framed alike, his head
     * at the top) drawn w wide from the top of the box, or a plain figure where there is none, so every box in a row
     * or a column is the same and the faces and everything beside them line up.
     */
    private static View f1Photo(Context c, F1 f, F1Results.Row r, int w, int h) {
        FrameLayout frame = new FrameLayout(c);
        frame.setClipChildren(true);
        Bitmap ph = f.photos.get(r.pos);
        ImageView photo = new ImageView(c);
        photo.setImageBitmap(sharp(ph != null ? ph : driverBlank(256)));
        photo.setScaleType(ImageView.ScaleType.FIT_XY);
        frame.addView(photo, new FrameLayout.LayoutParams(w, w, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        return frame;
    }

    /**
     * His team's logo on a small white tile, so every one shows on the black: Mercedes', Audi's, Aston Martin's and
     * Cadillac's are dark, and vanished on it. An empty space of the same size where there is no logo.
     */
    private static View f1Logo(Context c, F1 f, F1Results.Row r, int size) {
        Bitmap logo = r.logo == null ? null : f.logos.get(r.logo);
        if (logo == null) return new View(c);
        // Straight on the black, no tile: a dark logo's dark parts lifted to white so it shows (ScoreCard.onBlack).
        ImageView l = new ImageView(c);
        l.setImageBitmap(sharp(ScoreCard.onBlack(logo)));
        l.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return l;
    }

    private static Bitmap blank;

    /** A plain figure, framed as the photos are (head at the top, shoulders to the foot), for a driver without one. */
    private static Bitmap driverBlank(int s) {
        if (blank != null) return blank;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF3A3C46);
        cv.drawRoundRect(new RectF(s * 0.12f, s * 0.58f, s * 0.88f, s * 1.2f), s * 0.2f, s * 0.2f, p);
        cv.drawRect(s * 0.42f, s * 0.42f, s * 0.58f, s * 0.62f, p);
        cv.drawOval(new RectF(s * 0.33f, s * 0.08f, s * 0.67f, s * 0.48f), p);
        blank = b;
        return b;
    }

    /** Positions from..to-1 as lines, in one column (with first names and points) or two. */
    private static View f1List(Context c, F1 f, int from, int to, int columns) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(px(10), px(10), px(14), px(10));
        int per = (int) Math.ceil((to - from) / (double) columns);
        int n = 0;
        for (int col = 0; col < columns; col++) {
            if (col > 0) {
                View rule = new View(c);
                rule.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Math.max(1, px(1.5f)), -1);
                rl.leftMargin = rl.rightMargin = px(10);
                page.addView(rule, rl);
            }
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < per; i++) {
                int idx = from + col * per + i;
                View line = idx < to ? f1Line(c, f, f.rows.get(idx), columns == 1) : new View(c);
                list.addView(line, new LinearLayout.LayoutParams(-1, 0, 1f));
                if (idx < to) {
                    line.setAlpha(0f);
                    line.setTranslationX(px(24));
                    line.animate().alpha(1f).translationX(0f).setStartDelay(140L + 45L * n++).setDuration(320)
                            .setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
                }
            }
            page.addView(list, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return page;
    }

    private static View f1Line(Context c, F1 f, F1Results.Row r, boolean wide) {
        LinearLayout v = new LinearLayout(c);
        v.setGravity(Gravity.CENTER_VERTICAL);
        TextView pos = text(c, String.valueOf(r.pos), wide ? 16 : 14, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        pos.setGravity(Gravity.CENTER);
        v.addView(pos, new LinearLayout.LayoutParams(px(26), -2));
        int ps = px(wide ? 26 : 24);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ps, -1);
        fl.leftMargin = px(2);
        v.addView(f1Photo(c, f, r, ps, ps), fl);
        int ls = px(wide ? 20 : 17);
        LinearLayout.LayoutParams lgl = new LinearLayout.LayoutParams(ls, ls);
        lgl.leftMargin = px(6);
        v.addView(f1Logo(c, f, r, ls), lgl);
        LinearLayout name = new LinearLayout(c);
        name.setGravity(Gravity.CENTER_VERTICAL);
        if (wide && !r.first.isEmpty()) {
            name.addView(text(c, r.first, 13, 0xB3FFFFFF, ScoreCard.outfit(c, 500)));
            name.addView(new View(c), new LinearLayout.LayoutParams(px(5), 1));
        }
        name.addView(text(c, wide ? r.last.toUpperCase(java.util.Locale.UK) : r.last, wide ? 15 : 13.5f, 0xFFFFFFFF, ScoreCard.outfit(c, wide ? 800 : 700)));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = px(8);
        v.addView(name, nl);
        if (!r.gap.isEmpty()) {
            boolean out = "DNF".equals(r.gap);
            TextView g = text(c, r.gap, wide ? 13 : 11.5f, out ? 0xFFFF5A52 : 0xCCFFFFFF, ScoreCard.outfit(c, 600));
            g.setGravity(Gravity.END);
            v.addView(g, new LinearLayout.LayoutParams(-2, -2));
        }
        if (wide) {
            // The points in a column of their own, so they stand level down the page (empty for none).
            TextView pts = text(c, r.points > 0 ? "+" + r.points : "", 11, 0xFFFFFFFF, ScoreCard.outfit(c, 800));
            pts.setGravity(Gravity.CENTER);
            if (r.points > 0) {
                GradientDrawable pb = new GradientDrawable();
                pb.setColor(F1_RED);
                pb.setCornerRadius(px(8));
                pts.setBackground(pb);
            }
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(34), px(17));
            pl.leftMargin = px(10);
            v.addView(pts, pl);
        }
        return v;
    }

    /** Grid slots from..to-1, two by two and staggered as on the grid: the odd places a half-slot ahead of the even. */
    private static View f1Slots(Context c, F1 f, int from, int to) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(px(10), px(8), px(14), px(8));
        int count = to - from, per = (count + 1) / 2;
        boolean big = from == 0;
        int n = 0;
        for (int side = 0; side < 2; side++) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            if (side == 1) col.addView(new View(c), new LinearLayout.LayoutParams(-1, 0, 0.5f));
            for (int j = 0; j < per; j++) {
                int idx = from + j * 2 + side;
                View slot = idx < to ? f1Slot(c, f, f.rows.get(idx), big) : new View(c);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 0, 1f);
                sl.topMargin = sl.bottomMargin = px(3);
                col.addView(slot, sl);
                if (idx < to) {
                    slot.setAlpha(0f);
                    slot.setTranslationY(px(14));
                    slot.animate().alpha(1f).translationY(0f).setStartDelay(140L + 55L * n++).setDuration(340)
                            .setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
                }
            }
            if (side == 0) col.addView(new View(c), new LinearLayout.LayoutParams(-1, 0, 0.5f));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
            if (side == 1) cl.leftMargin = px(12);
            page.addView(col, cl);
        }
        return page;
    }

    private static View f1Slot(Context c, F1 f, F1Results.Row r, boolean big) {
        LinearLayout slot = new LinearLayout(c);
        slot.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(F1_SLOT);
        bg.setCornerRadius(px(6));
        slot.setBackground(bg);
        slot.setClipToOutline(true);
        slot.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        // The edge in his team's colour (McLaren's papaya, Ferrari's red, Mercedes' teal), as on F1's grid graphics.
        View edge = new View(c);
        edge.setBackgroundColor(F1Results.teamColour(r.teamId));
        slot.addView(edge, new LinearLayout.LayoutParams(px(5), -1));
        TextView pos = text(c, String.valueOf(r.pos), big ? 22 : 15, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        pos.setGravity(Gravity.CENTER);
        slot.addView(pos, new LinearLayout.LayoutParams(px(big ? 38 : 30), -2));
        // Every slot its photo box, the same size down the page: his photo from the top, or the plain figure.
        int ps = px(big ? 70 : 34);
        slot.addView(f1Photo(c, f, r, ps, ps), new LinearLayout.LayoutParams(ps, -1));
        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.VERTICAL);
        if (big && !r.first.isEmpty()) names.addView(text(c, r.first, 12, 0xB3FFFFFF, ScoreCard.outfit(c, 500)));
        names.addView(text(c, r.last.toUpperCase(java.util.Locale.UK), big ? 17 : 13.5f, 0xFFFFFFFF, ScoreCard.outfit(c, 800)));
        if (!r.gap.isEmpty()) names.addView(text(c, r.gap, big ? 12 : 10.5f, 0xB3FFFFFF, ScoreCard.outfit(c, 600)));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = px(8);
        slot.addView(names, nl);
        int ls = px(big ? 28 : 20);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ls, ls);
        ll.rightMargin = px(10);
        slot.addView(f1Logo(c, f, r, ls), ll);
        return slot;
    }

    // -- on Home: the bar -----------------------------------------------------------------------------------------------

    private static void f1Bar(Context c, F1 f, Data carrier) {
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        int w = px(1728), h = px(80), bandW = px(150);
        float r = h / 2f;
        FrameLayout win = new FrameLayout(c);
        FrameLayout box = new FrameLayout(c);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        box.setClipToOutline(true);
        box.setBackground(new Streaks(F1_BLACK, F1_BLACK_DEEP, 0, px(50), px(30)));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout band = new FrameLayout(c);
        band.setBackground(new Streaks(F1_RED, F1_RED_DEEP, 0x1AFFFFFF, px(14), px(3)));
        ImageView mark = new ImageView(c);
        mark.setImageBitmap(sharp(f.mark));
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        band.addView(mark, new FrameLayout.LayoutParams(px(118), px(40), Gravity.CENTER));
        row.addView(band, new LinearLayout.LayoutParams(bandW, h));
        LinearLayout label = new LinearLayout(c);
        label.setOrientation(LinearLayout.VERTICAL);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(px(16), 0, px(26), 0);
        label.setBackground(new ScoreCard.Slant(F1_RED, px(16), false));
        label.addView(text(c, f.grid ? (sprint(f) ? "SPRINT GRID" : "GRID") : sprint(f) ? "SPRINT" : "RESULT", 23, 0xFFFFFFFF, ScoreCard.outfit(c, 900)),
                new LinearLayout.LayoutParams(-2, -2));
        TextView small = text(c, f.gp.replace("Grand Prix", "GP").toUpperCase(java.util.Locale.UK), 10, 0xD9FFFFFF,
                Typeface.create("sans-serif-condensed", Typeface.BOLD));
        small.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams sml = new LinearLayout.LayoutParams(-2, -2);
        sml.topMargin = px(3);
        label.addView(small, sml);
        row.addView(label, new LinearLayout.LayoutParams(-2, h));
        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(true);
        row.addView(stage, new LinearLayout.LayoutParams(0, h, 1f));
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), px(24)));
        back.setTag(PopupMotion.BACK_KEY);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(24), px(24));
        bl.leftMargin = px(10);
        bl.rightMargin = px(16);
        row.addView(back, bl);
        box.addView(row, new FrameLayout.LayoutParams(w, h));
        win.addView(box, new FrameLayout.LayoutParams(w, h));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = px(12);
        lp.setTitle("LineupCard");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        host = win;
        showing = carrier;
        bar = true;
        ScoreCard.lane("lineup", true);

        List<Runnable> pages = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        int n = f.rows.size();
        if (f.grid) {
            pages.add(() -> turn(c, stage, () -> stage.addView(f1BarTitle(c, f), full())));
            times.add(9_000L);
            int[][] spans = {{0, 6}, {6, 14}, {14, 22}};
            for (int[] sp : spans) {
                final int a = sp[0], b = Math.min(sp[1], n);
                if (a >= b) continue;
                pages.add(() -> turn(c, stage, () -> stage.addView(f1BarRow(c, f, a, b, a == 0), full())));
                times.add(a == 0 ? 10_000L : 9_000L);
            }
        } else {
            pages.add(() -> turn(c, stage, () -> stage.addView(f1BarRow(c, f, 0, Math.min(3, n), true), full())));
            times.add(10_000L);
            int[][] spans = {{3, 10}, {10, 16}, {16, 22}};
            for (int[] sp : spans) {
                final int a = sp[0], b = Math.min(sp[1], n);
                if (a >= b) continue;
                pages.add(() -> turn(c, stage, () -> stage.addView(f1BarRow(c, f, a, b, false), full())));
                times.add(9_000L);
            }
        }
        box.setAlpha(0f);
        box.setTranslationY(-px(24));
        box.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
        run(win, carrier, pages, times);
    }

    /** The grid's first page on the bar: STARTING GRID, the Grand Prix and lights out; the channel and OK at the end. */
    private static View f1BarTitle(Context c, F1 f) {
        LinearLayout page = new LinearLayout(c);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(px(22), 0, 0, 0);
        page.addView(text(c, sprint(f) ? "Sprint grid" : "Starting grid", 26, 0xFFFFFFFF, ScoreCard.outfit(c, 800)), new LinearLayout.LayoutParams(-2, -2));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, f.gp, 16, 0xCCFFFFFF, ScoreCard.outfit(c, 600)), new LinearLayout.LayoutParams(-2, -2));
        String lights = lightsOut(f.startAt);
        if (lights != null) {
            TextView when = text(c, lights, 17, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
            col.addView(when, new LinearLayout.LayoutParams(-2, -2));
            when.setAlpha(0f);
            when.animate().alpha(1f).setStartDelay(800).setDuration(1_300).setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = px(22);
        page.addView(col, cl);
        page.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        View ch = f1Channel(c, f, 20);
        if (ch != null || f.watch != null) {
            LinearLayout foot = new LinearLayout(c);
            foot.setGravity(Gravity.CENTER_VERTICAL);
            foot.setPadding(px(30), 0, px(18), 0);
            foot.setBackground(new ScoreCard.Slant(0xFF000000, px(16), true));
            if (ch != null) foot.addView(ch, new LinearLayout.LayoutParams(-2, px(20)));
            if (f.watch != null) {
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(26));
                ol.leftMargin = px(ch != null ? 14 : 0);
                foot.addView(okPill(c, 12, 19), ol);
            }
            page.addView(foot, new LinearLayout.LayoutParams(-2, -1));
        }
        return page;
    }

    /**
     * Positions from..to-1 across the bar, every entry the same width with its parts in fixed places (number, photo,
     * then the surname with the logo and gap under it), so numbers and faces line up from one entry and page to the
     * next; the podium's and the front rows' photos a size up.
     */
    private static View f1BarRow(Context c, F1 f, int from, int to, boolean big) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(px(12), 0, px(4), 0);
        for (int i = from; i < to; i++) {
            F1Results.Row r = f.rows.get(i);
            if (i > from) {
                View rule = new View(c);
                rule.setBackgroundColor(0x2EFFFFFF);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(Math.max(1, px(1.5f)), -1);
                rl.topMargin = rl.bottomMargin = px(16);
                page.addView(rule, rl);
            }
            LinearLayout e = new LinearLayout(c);
            e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            e.setPadding(px(8), 0, px(4), 0);
            TextView pos = text(c, String.valueOf(r.pos), big ? 22 : 18, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
            pos.setGravity(Gravity.CENTER);
            e.addView(pos, new LinearLayout.LayoutParams(px(big ? 30 : 26), -2));
            int ps = px(big ? 68 : 50);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ps, -1);
            fl.leftMargin = px(4);
            fl.topMargin = px(big ? 8 : 14);
            e.addView(f1Photo(c, f, r, ps, ps), fl);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(c, big ? r.last.toUpperCase(java.util.Locale.UK) : r.last, big ? 19 : 16, 0xFFFFFFFF, ScoreCard.outfit(c, 800)));
            LinearLayout sub = new LinearLayout(c);
            sub.setGravity(Gravity.CENTER_VERTICAL);
            sub.addView(f1Logo(c, f, r, px(19)), new LinearLayout.LayoutParams(px(19), px(19)));
            String gap = r.gap.isEmpty() && big && !f.grid && r.pos == 1 ? "Winner" : r.gap;
            if (!gap.isEmpty()) {
                boolean out = "DNF".equals(gap);
                TextView g = text(c, gap + (!f.grid && r.points > 0 && big ? "  ·  +" + r.points : ""), big ? 14 : 12.5f,
                        out ? 0xFFFF5A52 : 0xCCFFFFFF, ScoreCard.outfit(c, 600));
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
                gl.leftMargin = px(5);
                sub.addView(g, gl);
            }
            LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(-2, -2);
            sbl.topMargin = px(2);
            col.addView(sub, sbl);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            cl.leftMargin = px(8);
            e.addView(col, cl);
            page.addView(e, new LinearLayout.LayoutParams(0, -1, 1f));
            e.setAlpha(0f);
            e.setTranslationX(px(26));
            e.animate().alpha(1f).translationX(0f).setStartDelay(120L + 60L * (i - from)).setDuration(340)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        }
        // A short last page keeps its entries the width of a full one, so they stand where those above them did.
        int full = big ? (f.grid ? 6 : 3) : (f.grid ? 8 : (from == 3 ? 7 : 6));
        for (int i = to - from; i < full; i++) page.addView(new View(c), new LinearLayout.LayoutParams(0, -1, 1f));
        return page;
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    /**
     * The next page: the first at once; the rest behind a wipe, slanted bands in the competition's navy and a lighter
     * blue sweeping across the stage, the new page put in while it is covered and uncovered as the bands go on.
     */
    private static void turn(Context c, FrameLayout stage, Runnable swap) {
        if (stage.getChildCount() == 0) { swap.run(); return; }
        // The panel is drawn once (a layer on the graphics chip) and slid across: no page is redrawn while it moves.
        int w = stage.getWidth(), h = stage.getHeight();
        float slant = h * 0.42f;
        int pw = Math.round(w + 2 * slant);
        Wipe wipe = new Wipe(c);
        wipe.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        stage.addView(wipe, new FrameLayout.LayoutParams(pw, h));
        wipe.setTranslationX(-pw);
        boolean[] swapped = {false};
        // Half way it covers the whole stage: the new page goes in under it then, and its back uncovers it.
        wipe.animate().translationX(w).setDuration(900).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .setUpdateListener(a -> {
                    if (swapped[0] || a.getAnimatedFraction() < 0.5f) return;
                    swapped[0] = true;
                    for (int i = stage.getChildCount() - 1; i >= 0; i--) if (stage.getChildAt(i) != wipe) stage.removeViewAt(i);
                    swap.run();
                    wipe.bringToFront();
                })
                .withEndAction(() -> stage.removeView(wipe)).start();
    }

    /** The wipe's colours: the line-ups' navy and lighter blue; F1's black and red. */
    private static int wipeBody = 0xFF151A63, wipeEdge = 0xFF3D4BD6;

    /** The wipe: a slanted navy panel edged in lighter blue and white, its front then its back crossing the stage. */
    private static final class Wipe extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Wipe(Context c) { super(c); }

        /** Drawn once: the navy panel, slanted, its front edge (right) and back edge (left) in light blue and white. */
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float slant = h * 0.42f, body = w - slant, edge = px(10);
            panel(cv, 0, body, h, slant, wipeBody);
            panel(cv, body - edge, body, h, slant, wipeEdge);
            panel(cv, body - edge * 0.35f, body, h, slant, 0xFFFFFFFF);
            panel(cv, 0, edge, h, slant, wipeEdge);
            panel(cv, 0, edge * 0.35f, h, slant, 0xE6FFFFFF);
        }

        private void panel(Canvas cv, float from, float to, int h, float slant, int colour) {
            if (to <= from) return;
            Path path = new Path();
            path.moveTo(from, h);
            path.lineTo(to, h);
            path.lineTo(to + slant, 0);
            path.lineTo(from + slant, 0);
            path.close();
            p.setColor(colour);
            cv.drawPath(path, p);
        }
    }

    // ---- the band ------------------------------------------------------------------------------------------------

    /**
     * The competition's mark in the band: big and in the middle on the fixture page (big = true), where the band has
     * nothing else; small at the top above the crests on the players' and the pitch's pages, clear of them.
     */
    private static void league(Context c, FrameLayout band, Data d, boolean big) {
        ImageView league = new ImageView(c);
        league.setImageBitmap(sharp(d.leagueLogo));
        league.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (big) {
            band.addView(league, new FrameLayout.LayoutParams(px(58), px(58), Gravity.CENTER));
            return;
        }
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(px(28), px(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ll.topMargin = px(12);
        band.addView(league, ll);
    }

    /** A crest as it is (no disc), with a soft shadow so a dark one still shows on the navy. */
    private static ImageView crest(Context c, Bitmap b) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(sharp(b));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setOutlineProvider(null);
        return v;
    }

    /** The competition, the club's crest, and its name up the side, fitted to the room under the crest. */
    /**
     * The eleven by its lines, from each player's position (G, a defender's CD/LB/RB…, a forward's F/LF/RF…, else
     * midfield): the keeper on his own; the defence; the midfield; the attack, or the midfield and attack together
     * when the attack is one or two. Without positions, the formation's numbers in ESPN's order; without those, two
     * halves.
     */
    static List<List<Player>> byLines(Team t, List<String> captions) {
        List<Player> back = new ArrayList<>(), mid = new ArrayList<>(), front = new ArrayList<>();
        boolean known = true;
        for (Player p : t.players) {
            String pos = p.position == null ? "" : p.position.toUpperCase(java.util.Locale.ROOT);
            if (pos.isEmpty()) { known = false; break; }
            if (pos.equals("G") || pos.equals("GK")) back.add(0, p);
            else if (pos.startsWith("CD") || pos.startsWith("CB") || pos.equals("LB") || pos.equals("RB") || pos.endsWith("WB")
                    || pos.equals("D") || pos.equals("SW") || pos.startsWith("D-")) back.add(p);
            else if (pos.equals("F") || pos.startsWith("CF") || pos.equals("LF") || pos.equals("RF") || pos.equals("ST")
                    || pos.equals("SS") || pos.equals("LW") || pos.equals("RW") || pos.startsWith("F-")) front.add(p);
            else mid.add(p);
        }
        if (!known || back.size() < 2 || mid.isEmpty() && front.isEmpty()) {
            back.clear(); mid.clear(); front.clear();
            int[] n = formationLines(t.formation);
            if (n != null && 1 + sum(n) == t.players.size()) {
                back.addAll(t.players.subList(0, 1 + n[0]));
                mid.addAll(t.players.subList(1 + n[0], t.players.size() - n[n.length - 1]));
                front.addAll(t.players.subList(t.players.size() - n[n.length - 1], t.players.size()));
            } else {
                int split = (t.players.size() + 1) / 2;
                List<List<Player>> halves = new ArrayList<>();
                halves.add(new ArrayList<>(t.players.subList(0, split)));
                if (split < t.players.size()) halves.add(new ArrayList<>(t.players.subList(split, t.players.size())));
                for (int i = 0; i < halves.size(); i++) captions.add("");
                return halves;
            }
        }
        List<List<Player>> out = new ArrayList<>();
        // The keeper on a page of his own, then the defence.
        Player keeper = back.get(0);
        String kp = keeper.position == null ? "" : keeper.position.toUpperCase(java.util.Locale.ROOT);
        if (kp.equals("G") || kp.equals("GK") || !known) {
            List<Player> alone = new ArrayList<>();
            alone.add(keeper);
            out.add(alone);
            captions.add("GOALKEEPER");
            back = new ArrayList<>(back.subList(1, back.size()));
        }
        out.add(back);
        captions.add("DEFENCE");
        if (front.size() <= 2 && mid.size() + front.size() <= 7 || mid.isEmpty() || front.isEmpty()) {
            List<Player> rest = new ArrayList<>(mid);
            rest.addAll(front);
            if (!rest.isEmpty()) {
                out.add(rest);
                captions.add(mid.isEmpty() ? "ATTACK" : front.isEmpty() ? "MIDFIELD" : "MIDFIELD\n& ATTACK");
            }
        } else {
            out.add(mid);
            captions.add("MIDFIELD");
            out.add(front);
            captions.add("ATTACK");
        }
        return out;
    }

    /**
     * A little upright pitch (the side attacking upwards) with the line's part lit: GOALKEEPER the goal area at its
     * foot, DEFENCE the back third, MIDFIELD the middle, ATTACK the front, both for the midfield and attack together.
     * Drawn at the size it shows.
     */
    static Bitmap lineIcon(String caption, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = Math.max(1f, w / 16f), in = s / 2f;
        RectF field = new RectF(in, in, w - in, h - in);
        float third = field.height() / 3f;
        // The lit part.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xE6FFFFFF);
        float r = s * 1.5f;
        switch (caption) {
            case "GOALKEEPER": {
                float bw = field.width() * 0.5f, bh = field.height() * 0.16f;
                cv.drawRoundRect(new RectF(w / 2f - bw / 2, field.bottom - bh, w / 2f + bw / 2, field.bottom), r, r, p);
                break;
            }
            case "DEFENCE":
                cv.drawRoundRect(new RectF(field.left, field.bottom - third, field.right, field.bottom), r, r, p);
                break;
            case "MIDFIELD":
                cv.drawRect(new RectF(field.left, field.top + third, field.right, field.bottom - third), p);
                break;
            case "ATTACK":
                cv.drawRoundRect(new RectF(field.left, field.top, field.right, field.top + third), r, r, p);
                break;
            default:   // the midfield and the attack together
                cv.drawRoundRect(new RectF(field.left, field.top, field.right, field.bottom - third), r, r, p);
        }
        // The pitch's lines over it: its edge, halfway, the centre circle and both boxes.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s);
        p.setColor(0x99FFFFFF);
        cv.drawRoundRect(field, r, r, p);
        cv.drawLine(field.left, h / 2f, field.right, h / 2f, p);
        cv.drawCircle(w / 2f, h / 2f, field.width() * 0.18f, p);
        float boxW = field.width() * 0.5f, boxH = field.height() * 0.16f;
        cv.drawRect(w / 2f - boxW / 2, field.top, w / 2f + boxW / 2, field.top + boxH, p);
        cv.drawRect(w / 2f - boxW / 2, field.bottom - boxH, w / 2f + boxW / 2, field.bottom, p);
        return b;
    }

    private static int[] formationLines(String formation) {
        if (formation == null || !formation.matches("\\d+(-\\d+)+")) return null;
        String[] parts = formation.split("-");
        int[] n = new int[parts.length];
        for (int i = 0; i < parts.length; i++) n[i] = Integer.parseInt(parts[i]);
        return n;
    }

    private static int sum(int[] n) {
        int s = 0;
        for (int x : n) s += x;
        return s;
    }

    private static void teamBand(Context c, FrameLayout band, Team t, String caption) {
        band.removeAllViews();
        league(c, band, showing, false);
        // As on the fixture page: the club's crest big (no name), and its formation under it.
        LinearLayout club = new LinearLayout(c);
        club.setOrientation(LinearLayout.VERTICAL);
        club.setGravity(Gravity.CENTER_HORIZONTAL);
        club.addView(crest(c, t.crest), new LinearLayout.LayoutParams(px(58), px(58)));
        if (t.formation != null && !t.formation.isEmpty()) {
            TextView f = text(c, t.formation, 14, 0xFFFFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            f.setLetterSpacing(0.1f);
            f.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
            fl.topMargin = px(10);
            club.addView(f, fl);
        }
        // Which line this page is: a little pitch with that part lit (the goal, the back, the middle, the front),
        // and its name under it.
        if (caption != null && !caption.isEmpty()) {
            int iw = px(26), ih = px(36);
            ImageView line = new ImageView(c);
            line.setImageBitmap(lineIcon(caption, iw, ih));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(iw, ih);
            ll.topMargin = px(8);
            club.addView(line, ll);
            TextView name = text(c, caption, caption.length() > 8 && !caption.contains("\n") ? 8.5f : 9.5f, 0xD9FFFFFF,
                    Typeface.create("sans-serif-condensed", Typeface.BOLD));
            name.setLetterSpacing(caption.length() > 8 && !caption.contains("\n") ? 0.02f : 0.1f);
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setEllipsize(null);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(BAND) - px(6), -2);
            nl.topMargin = px(5);
            club.addView(name, nl);
        }
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cl.topMargin = px(20);
        band.addView(club, cl);
    }

    /** For the pitch: both crests, each with its eleven's shape under it. */
    private static void pitchBand(Context c, FrameLayout band, Data d) {
        band.removeAllViews();
        league(c, band, d, false);
        // Both crests a size up, each over its formation, in the room under the competition's mark (12 to 40).
        int y = 50;
        for (Team t : new Team[]{d.home, d.away}) {
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(54), px(54), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            cl.topMargin = px(y);
            band.addView(crest(c, t.crest), cl);
            TextView f = text(c, t.formation, 12.5f, 0xFFFFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            f.setLetterSpacing(0.1f);
            f.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            fl.topMargin = px(y + 57);
            band.addView(f, fl);
            y += 88;
        }
    }

    // ---- a page of players ---------------------------------------------------------------------------------------

    private static View players(Context c, Team t, List<Player> ps) {
        FrameLayout page = new FrameLayout(c);
        page.setBackground(new Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(86), px(36)));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(6), 0, px(6), 0);
        for (int i = 0; i < ps.size(); i++) {
            View col = player(c, t, ps.get(i));
            row.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
            // In from the right one after another, as the wipe uncovers them; each drawn once into a layer that moves.
            col.setAlpha(0f);
            col.setTranslationX(px(46));
            col.animate().alpha(1f).translationX(0f).setStartDelay(120L + 70L * i).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        }
        page.addView(row, new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    /** One player: his number and flag on a line above him, head and shoulders, first name and surname below. */
    private static View player(Context c, Team t, Player p) {
        FrameLayout col = new FrameLayout(c);
        col.setClipChildren(true);
        FrameLayout frame = new FrameLayout(c);
        frame.setClipChildren(true);
        ImageView photo = new ImageView(c);
        Bitmap shown = p.photo == null ? avatar(t.photoKit != 0 ? t.photoKit : t.kit, t.photoKit != 0 ? t.photoSleeves : t.sleeves, p.number)
                : p.framed ? framed(p.photo, t.kit) : p.photo;
        photo.setImageBitmap(sharp(shown));
        // A square cut-out (TheSportsDB's) 150 wide; the Premier League's own, upright and framed closer, 128 wide at
        // its own height, so the faces come out the same size.
        boolean upright = shown != null && shown.getHeight() > shown.getWidth() * 1.05f;
        int pw = upright ? px(128) : px(150), ph = upright ? Math.round(pw * shown.getHeight() / (float) shown.getWidth()) : px(150);
        photo.setScaleType(upright ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_START);
        frame.addView(photo, new FrameLayout.LayoutParams(pw, ph, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -1);
        fl.topMargin = px(36);
        fl.bottomMargin = px(46);
        col.addView(frame, fl);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(c, p.number, 24, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL)));
        if (p.flag != null) {
            ImageView flag = new ImageView(c);
            flag.setImageBitmap(sharp(p.flag));
            flag.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(23), px(15));
            gl.leftMargin = px(7);
            top.addView(flag, gl);
        }
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tl.leftMargin = px(8);
        tl.topMargin = px(8);
        col.addView(top, tl);

        LinearLayout nm = new LinearLayout(c);
        nm.setOrientation(LinearLayout.VERTICAL);
        nm.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView first = text(c, p.first.isEmpty() ? " " : p.first, 11, NAVY, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        first.setGravity(Gravity.CENTER);
        nm.addView(first, new LinearLayout.LayoutParams(-1, -2));
        TextView last = text(c, p.last, 17, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL));
        last.setGravity(Gravity.CENTER);
        // A long surname fits by getting smaller, never cut short.
        last.setSingleLine(false);
        last.setMaxLines(1);
        last.setEllipsize(null);
        last.setAutoSizeTextTypeUniformWithConfiguration(px(9), px(17), 1, TypedValue.COMPLEX_UNIT_PX);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, px(22));
        ll.topMargin = px(1);
        nm.addView(last, ll);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        nl.bottomMargin = px(8);
        nl.leftMargin = nl.rightMargin = px(2);
        col.addView(nm, nl);
        return col;
    }

    // ---- both elevens on one pitch -------------------------------------------------------------------------------

    private static View pitch(Context c, Data d) {
        FrameLayout page = new FrameLayout(c);
        page.setBackground(new Streaks(0xFF1B2591, 0xFF0D1360, 0x10FFFFFF, px(60), px(4)));
        Pitch field = new Pitch(c, d);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -1);
        fl.setMargins(px(8), px(8), px(8), px(30));
        page.addView(field, fl);
        // Under the pitch, clear of every name, in one line: the kick-off ("Kick-off 01:43 · Starts in 14 mins") and,
        // where a guide lists the game, its channel's logo and "Press OK to watch".
        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        String starts = startsIn(d.kickoffAt);
        StringBuilder when = new StringBuilder();
        if (d.kickoff != null) when.append("Kick-off ").append(d.kickoff);
        if (starts != null && !starts.startsWith("Kick-off")) when.append(when.length() > 0 ? "  ·  " : "").append(starts);
        else if (starts != null && when.length() == 0) when.append(starts);
        if (when.length() > 0) foot.addView(text(c, when.toString(), 12.5f, 0xFFFFFFFF, inter(c, R.font.inter_semibold)), new LinearLayout.LayoutParams(-2, -2));
        View logo = channel(c, d, 15);
        if (logo != null) {
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, px(15));
            ll.leftMargin = px(when.length() > 0 ? 16 : 0);
            foot.addView(logo, ll);
        }
        if (d.watch != null) {
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(20));
            ol.leftMargin = px(10);
            foot.addView(okPill(c, 10.5f, 15), ol);
        }
        if (foot.getChildCount() > 0) {
            FrameLayout.LayoutParams fl2 = new FrameLayout.LayoutParams(-2, px(22), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            fl2.bottomMargin = px(4);
            page.addView(foot, fl2);
            foot.setAlpha(0f);
            foot.animate().alpha(1f).setStartDelay(900).setDuration(900).withLayer().start();
        }
        field.reveal();
        return page;
    }

    /** Each player's place: x along the pitch (0 his own goal, 1 the other), y across it (0 top), for one team. */
    private static float[][] places(Team t, boolean names) {
        int n = t.players.size();
        float[][] at = new float[n][2];
        if (n == 0) return at;
        // The lines from the shape ("4-2-3-1"), the keeper first; the outfield players in the order of their
        // positions from defence to attack, each line across from the left side to the right.
        List<Integer> lines = new ArrayList<>();
        lines.add(1);
        int total = 1;
        for (String part : t.formation.split("-")) {
            try { int v = Integer.parseInt(part.trim()); lines.add(v); total += v; } catch (NumberFormatException ignored) { }
        }
        if (total != n) {
            lines.clear();
            lines.add(1);
            int[] count = new int[4];
            for (int i = 1; i < n; i++) {
                int dp = depth(t.players.get(i).position);
                count[dp <= 1 ? 1 : dp <= 4 ? 2 : 3]++;
            }
            for (int g = 1; g < 4; g++) if (count[g] > 0) lines.add(count[g]);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        int keeper = 0;
        for (int i = 0; i < n; i++) if ("G".equalsIgnoreCase(t.players.get(i).position)) { keeper = i; break; }
        order.remove(Integer.valueOf(keeper));
        List<Integer> outfield = new ArrayList<>(order);
        outfield.sort((a, b) -> Integer.compare(depth(t.players.get(a).position), depth(t.players.get(b).position)));
        at[keeper] = new float[]{0.06f, 0.5f};
        int next = 0;
        float[] xs = xFor(lines.size() - 1);
        for (int li = 1; li < lines.size(); li++) {
            int k = lines.get(li);
            List<Integer> line = new ArrayList<>();
            for (int j = 0; j < k && next < outfield.size(); j++) line.add(outfield.get(next++));
            line.sort((a, b) -> Integer.compare(side(t.players.get(a).position), side(t.players.get(b).position)));
            float span = names ? (k == 4 ? 0.92f : 0.88f) : 0.80f;
            float top0 = (1 - span) / 2;
            if (names) { span -= 0.08f; top0 += 0.08f; }
            for (int j = 0; j < line.size(); j++) {
                float x = xs[li - 1];
                // A line of four: the two wide players a step forward, so no name hides another.
                if (names && line.size() == 4) x += (j == 0 || j == 3) ? 0.06f : -0.02f;
                at[line.get(j)] = new float[]{x, top0 + (j + 0.5f) / line.size() * span};
            }
        }
        while (next < outfield.size()) at[outfield.get(next++)] = new float[]{0.5f, 0.5f};
        return at;
    }

    /** The lines' places along the pitch, for three, four or five lines. */
    private static float[] xFor(int lines) {
        if (lines <= 3) return new float[]{0.30f, 0.60f, 0.90f};
        if (lines == 4) return new float[]{0.27f, 0.47f, 0.70f, 0.92f};
        return new float[]{0.26f, 0.42f, 0.58f, 0.75f, 0.92f};
    }

    /** How far forward a position plays (ESPN's: G, CD-L, LB, DM, CM-R, AM-L, F and the rest). */
    private static int depth(String pos) {
        String p = pos == null ? "" : pos.toUpperCase(java.util.Locale.ROOT);
        if (p.equals("G")) return 0;
        if (p.startsWith("CD") || p.equals("LB") || p.equals("RB") || p.equals("D") || p.startsWith("SW") || p.endsWith("WB")) return 1;
        if (p.startsWith("DM") || p.equals("CDM")) return 2;
        if (p.startsWith("CM") || p.equals("LM") || p.equals("RM") || p.equals("M")) return 3;
        if (p.startsWith("AM")) return 4;
        if (p.equals("LW") || p.equals("RW")) return 5;
        return 6;
    }

    /** Where across the pitch a position plays: 0 wide left (LB, LM, LW), 1 left of centre (CD-L, AM-L), 2 centre, 3, 4. */
    private static int side(String pos) {
        String p = pos == null ? "" : pos.toUpperCase(java.util.Locale.ROOT);
        if (p.startsWith("L") && p.length() <= 3) return 0;
        if (p.endsWith("-L")) return 1;
        if (p.endsWith("-R")) return 3;
        if (p.startsWith("R") && p.length() <= 3) return 4;
        return 2;
    }

    /**
     * The pitch, drawn: stripes, lines, every player as a face in a ring with his number and name, the channel's logo
     * at the foot and "Press OK to watch" in the top corner. Players whose names would touch (or touch the logo or the
     * pill) are eased apart up or down the pitch, so every name reads.
     */
    private static final class Pitch extends View {
        private final Data d;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Team> teams = new ArrayList<>();
        private final List<Player> who = new ArrayList<>();
        private float[] xs = new float[0], ys = new float[0], tws = new float[0], sizes = new float[0];
        private final RectF logoAt = new RectF(), pillAt = new RectF();
        /** The players coming on, left to right (0 none yet, 1 all). */
        private float shown = 1f;

        void reveal() {
            shown = 0f;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(1300);
            a.setStartDelay(150);
            a.setInterpolator(new DecelerateInterpolator(1.4f));
            a.addUpdateListener(v -> { shown = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }

        Pitch(Context c, Data d) {
            super(c);
            this.d = d;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float[][] home = places(d.home, true), away = places(d.away, true);
            teams.clear();
            who.clear();
            int n = d.home.players.size() + d.away.players.size();
            xs = new float[n]; ys = new float[n]; tws = new float[n]; sizes = new float[n];
            int i = 0;
            for (int j = 0; j < d.home.players.size(); j++, i++) {
                teams.add(d.home); who.add(d.home.players.get(j));
                xs[i] = w * (0.04f + (0.02f + home[j][0] * 0.46f) * 0.92f);
                ys[i] = h * home[j][1];
            }
            for (int j = 0; j < d.away.players.size(); j++, i++) {
                // Attacking the other way: its left side at the bottom.
                teams.add(d.away); who.add(d.away.players.get(j));
                xs[i] = w * (0.04f + (0.52f + (1 - away[j][0]) * 0.46f) * 0.92f);
                ys[i] = h * (1 - away[j][1]);
            }
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            for (i = 0; i < n; i++) {
                String name = who.get(i).last;
                p.setTextSize(px(name.length() > 8 ? 8.5f : 9.5f));
                float tw = p.measureText(name), maxW = px(62);
                sizes[i] = tw > maxW ? p.getTextSize() * maxW / tw : p.getTextSize();
                tws[i] = Math.min(tw, maxW);
            }
            logoAt.setEmpty();
            pillAt.setEmpty();
            settle(w, h);
        }

        /** The space a player takes: his ring and number above, his name below. */
        private RectF box(int i) {
            float half = Math.max(px(19), tws[i] / 2 + px(5));
            return new RectF(xs[i] - half, ys[i] - px(17), xs[i] + half, ys[i] + px(29));
        }

        /** Players eased apart up or down until none touches another, the logo or the pill. */
        private void settle(int w, int h) {
            float top = px(18), bottom = h - px(30);
            for (int round = 0; round < 60; round++) {
                boolean moved = false;
                for (int i = 0; i < xs.length; i++) {
                    for (RectF block : new RectF[]{logoAt, pillAt}) {
                        if (block.isEmpty()) continue;
                        RectF b = box(i);
                        if (!RectF.intersects(b, block)) continue;
                        // Away from it: down from the pill at the top, up from the logo at the foot.
                        ys[i] += block == pillAt ? block.bottom - b.top + 1 : -(b.bottom - block.top + 1);
                        moved = true;
                    }
                    for (int j = i + 1; j < xs.length; j++) {
                        RectF a = box(i), b = box(j);
                        if (!RectF.intersects(a, b)) continue;
                        float overlap = Math.min(a.bottom - b.top, b.bottom - a.top) / 2f + 1;
                        if (ys[i] <= ys[j]) { ys[i] -= overlap; ys[j] += overlap; }
                        else { ys[i] += overlap; ys[j] -= overlap; }
                        moved = true;
                    }
                }
                for (int i = 0; i < ys.length; i++) ys[i] = Math.max(top, Math.min(bottom, ys[i]));
                if (!moved) break;
            }
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            Path round = new Path();
            round.addRoundRect(new RectF(0, 0, w, h), px(8), px(8), Path.Direction.CW);
            cv.save();
            cv.clipPath(round);
            int stripes = 12;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < stripes; i++) {
                p.setColor(i % 2 == 0 ? 0xFF2F8D3C : 0xFF36994A);
                cv.drawRect(w * i / (float) stripes, 0, w * (i + 1) / (float) stripes, h, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, px(1.6f)));
            p.setColor(0x99FFFFFF);
            float in = px(6);
            cv.drawRect(in, in, w - in, h - in, p);
            cv.drawLine(w / 2f, in, w / 2f, h - in, p);
            cv.drawCircle(w / 2f, h / 2f, px(26), p);
            cv.drawRect(in, h / 2f - px(48), in + px(46), h / 2f + px(48), p);
            cv.drawRect(w - in - px(46), h / 2f - px(48), w - in, h / 2f + px(48), p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < xs.length; i++) {
                // Each player pops up in his place as the reveal crosses the pitch.
                float t = Math.max(0f, Math.min(1f, (shown * 1.35f - xs[i] / w) / 0.3f));
                if (t <= 0f) continue;
                float k = 0.55f + 0.45f * (1f - (1f - t) * (1f - t));
                cv.save();
                cv.scale(k, k, xs[i], ys[i]);
                draw(cv, teams.get(i), who.get(i), xs[i], ys[i], tws[i], sizes[i]);
                cv.restore();
            }
            if (d.channelLogo != null && !logoAt.isEmpty()) {
                cv.drawBitmap(d.channelLogo, null, logoAt, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            }
            if (!pillAt.isEmpty()) pill(cv);
            cv.restore();
        }

        private void pill(Canvas cv) {
            p.setColor(0xF2FFFFFF);
            cv.drawRoundRect(pillAt, pillAt.height() / 2, pillAt.height() / 2, p);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(px(10.5f));
            p.setTextAlign(Paint.Align.LEFT);
            Paint.FontMetrics fm = p.getFontMetrics();
            float mid = pillAt.centerY() - (fm.ascent + fm.descent) / 2f, x = pillAt.left + px(9);
            p.setColor(0xFF111214);
            cv.drawText("Press", x, mid, p);
            x += p.measureText("Press") + px(3);
            float kr = px(8);
            cv.drawCircle(x + kr, pillAt.centerY(), kr, p);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextSize(px(6.5f));
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics km = p.getFontMetrics();
            cv.drawText("OK", x + kr, pillAt.centerY() - (km.ascent + km.descent) / 2f, p);
            x += kr * 2 + px(3);
            p.setColor(0xFF111214);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(px(10.5f));
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText("to watch", x, mid, p);
        }

        private void draw(Canvas cv, Team t, Player pl, float cx, float cy, float tw, float size) {
            float r = px(13);
            // The face in its ring: a cut-out zoomed to the head, a framed photo whole, else the kit's avatar.
            Bitmap face = pl.photo != null ? pl.photo : avatar(t.photoKit != 0 ? t.photoKit : t.kit, t.photoKit != 0 ? t.photoSleeves : t.sleeves, "");
            boolean cut = pl.photo != null && !pl.framed;
            float zoom = cut ? 1.9f : 1f;
            Matrix m = new Matrix();
            float s = 2 * r * zoom / face.getWidth();
            m.setScale(s, s);
            m.postTranslate(cx - face.getWidth() * s / 2f, cut ? cy - r - 2 * r * 0.04f : cy - r);
            BitmapShader shader = new BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setLocalMatrix(m);
            p.setColor(t.kit != 0 ? t.kit : NAVY);
            cv.drawCircle(cx, cy, r, p);
            p.setShader(shader);
            cv.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, px(1.6f)));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.FILL);
            float nx = cx + px(12), ny = cy - px(9), nr = px(7);
            p.setColor(t.kit != 0 ? t.kit : NAVY);
            cv.drawCircle(nx, ny, nr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, px(1.2f)));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(nx, ny, nr, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(px(8.5f));
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(pl.number, nx, ny - (fm.ascent + fm.descent) / 2f, p);
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            p.setTextSize(size);
            fm = p.getFontMetrics();
            float ly = cy + r + px(2), lh = px(13);
            p.setColor(0xA0000000);
            cv.drawRoundRect(new RectF(cx - tw / 2 - px(4), ly, cx + tw / 2 + px(4), ly + lh), px(6), px(6), p);
            p.setColor(0xFFFFFFFF);
            cv.drawText(pl.last, cx, ly + lh / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    // ---- the last page -------------------------------------------------------------------------------------------

    /** "Starts in 15 min", "Starts in 1 hr 5 min", or "Kick-off now", from the kick-off and now. */
    static String startsIn(long kickoff) {
        if (kickoff <= 0) return null;
        long min = Math.round((kickoff - System.currentTimeMillis()) / 60_000.0);
        if (min <= 0) return "Kick-off now";
        if (min < 60) return "Starts in " + min + (min == 1 ? " min" : " mins");
        long h = min / 60, m = min % 60;
        return "Starts in " + h + (h == 1 ? " hr" : " hrs") + (m > 0 ? " " + m + (m == 1 ? " min" : " mins") : "");
    }

    /**
     * The first page: the two crests big on either side, and between them, centred as one block, the competition and
     * the ground, "Starts in …" and the kick-off, and the channel showing it with "Press OK to watch" (when a guide
     * lists it). Little empty ground: the crests fill the height.
     */
    private static View first(Context c, Data d) {
        FrameLayout page = new FrameLayout(c);
        page.setBackground(new Streaks(0xFF2A35B8, 0xFF080B3A, 0x0EFFFFFF, px(60), px(4)));
        int side = px(150);
        View home = club(c, d.home, px(116));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(side, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        hl.leftMargin = px(8);
        page.addView(home, hl);
        View away = club(c, d.away, px(116));
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(side, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        al.rightMargin = px(8);
        page.addView(away, al);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        StringBuilder where = new StringBuilder();
        if (d.competition != null && !d.competition.isEmpty()) where.append(d.competition);
        if (d.venue != null && !d.venue.isEmpty()) where.append(where.length() > 0 ? "  ·  " : "").append(d.venue);
        // In Home's own type (Inter), quietly: "Premier League · Anfield".
        TextView top = text(c, where.toString(), 13.5f, 0xC7FFFFFF, inter(c, R.font.inter_semibold));
        top.setGravity(Gravity.CENTER);
        mid.addView(top, new LinearLayout.LayoutParams(-1, -2));
        String starts = startsIn(d.kickoffAt);
        TextView when = text(c, starts != null ? starts : "v", starts != null ? 25 : 34, 0xFFFFFFFF,
                inter(c, R.font.inter_display_semibold));
        when.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = px(10);
        mid.addView(when, wl);
        // "Starts in 15 mins" fades in slowly once the crests are in, then the kick-off time under it.
        when.setAlpha(0f);
        when.animate().alpha(1f).setStartDelay(950).setDuration(1_400).setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
        if (d.kickoff != null) {
            TextView ko = text(c, "Kick-off " + d.kickoff, 13.5f, 0xB3FFFFFF, inter(c, R.font.inter_regular));
            ko.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams kol = new LinearLayout.LayoutParams(-1, -2);
            kol.topMargin = px(4);
            mid.addView(ko, kol);
            ko.setAlpha(0f);
            ko.animate().alpha(1f).setStartDelay(1_650).setDuration(1_200).setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
        }
        View ch = channel(c, d, 26);
        if (ch != null || d.watch != null) {
            LinearLayout foot = new LinearLayout(c);
            foot.setOrientation(LinearLayout.VERTICAL);
            foot.setGravity(Gravity.CENTER_HORIZONTAL);
            if (ch != null) foot.addView(ch, new LinearLayout.LayoutParams(-2, px(26)));
            if (d.watch != null) {
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(30));
                ol.topMargin = px(ch != null ? 9 : 0);
                foot.addView(okPill(c, 13, 22), ol);
            }
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
            fl.topMargin = px(14);
            mid.addView(foot, fl);
        }
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(W - BAND) - 2 * side - px(16), -2, Gravity.CENTER);
        page.addView(mid, ml);

        // The crests come in from either side, then the block between them, each drawn once into a layer that moves.
        home.setTranslationX(-px(60)); away.setTranslationX(px(60));
        home.setAlpha(0f); away.setAlpha(0f); mid.setAlpha(0f);
        mid.setScaleX(0.9f); mid.setScaleY(0.9f);
        home.animate().translationX(0).alpha(1f).setStartDelay(250).setDuration(520).setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        away.animate().translationX(0).alpha(1f).setStartDelay(250).setDuration(520).setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        mid.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(520).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
        return page;
    }

    /** A club: its crest big, its name under it (on two lines when long). */
    private static View club(Context c, Team t, int crestPx) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.addView(crest(c, t == null ? null : t.crest), new LinearLayout.LayoutParams(crestPx, crestPx));
        TextView n = text(c, t == null ? "" : t.name, 14.5f, 0xFFFFFFFF, inter(c, R.font.inter_semibold));
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(false);
        n.setMaxLines(2);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = px(6);
        v.addView(n, nl);
        return v;
    }

    /** One of Home's own typefaces (Inter); the system's when it cannot be read. */
    private static Typeface inter(Context c, int font) {
        try {
            return c.getResources().getFont(font);
        } catch (RuntimeException e) {
            return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    /** "Press [OK] to watch", on a white pill. */
    private static View okPill(Context c, float textPx, int keyPx) {
        LinearLayout pill = new LinearLayout(c);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2FFFFFF);
        bg.setCornerRadius(px(keyPx));
        pill.setBackground(bg);
        pill.setPadding(px(keyPx * 0.55f), 0, px(keyPx * 0.55f), 0);
        Typeface bold = inter(c, R.font.inter_semibold);
        pill.addView(text(c, "Press", textPx, 0xFF111214, bold));
        TextView key = text(c, "OK", textPx * 0.62f, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        key.setGravity(Gravity.CENTER);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFF111214);
        key.setBackground(disc);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(keyPx), px(keyPx));
        kl.leftMargin = kl.rightMargin = px(keyPx * 0.22f);
        pill.addView(key, kl);
        pill.addView(text(c, "to watch", textPx, 0xFF111214, bold));
        return pill;
    }

    /** The channel's logo, transparent, as it is; its name in white when there is no logo; null without either. */
    private static View channel(Context c, Data d, int height) {
        if (d.channelLogo != null) {
            ImageView logo = new ImageView(c);
            logo.setImageBitmap(sharp(d.channelLogo));
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setAdjustViewBounds(true);
            logo.setMaxWidth(px(height * 11f));
            return logo;
        }
        if (d.channel == null) return null;
        TextView name = text(c, d.channel, height * 0.62f, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        name.setShadowLayer(px(3), 0, px(1), 0x99000000);
        name.setGravity(Gravity.CENTER_VERTICAL);
        return name;
    }

    /** On a TV with no helper to pass OK here: the window takes the keys on the last page; OK watches, any other closes. */
    private static void offerWatch(FrameLayout win, Data d) {
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) win.getLayoutParams();
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            win.getContext().getSystemService(WindowManager.class).updateViewLayout(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        win.setFocusable(true);
        win.setFocusableInTouchMode(true);
        win.requestFocus();
        final boolean[] down = {false};
        win.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN) { down[0] = true; return true; }
            if (e.getAction() != KeyEvent.ACTION_UP || !down[0]) return true;
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
            close(true, code == KeyEvent.KEYCODE_BACK);
            if (ok && d.watch != null) d.watch.run();
            return true;
        });
    }

    /** The card away: faded, or at once when another takes its place. */
    static void close(boolean fade) {
        close(fade, false);
    }

    /** As close(fade); byBack: closed by Back, so it shrinks away towards its Back key instead of sliding out. */
    private static void close(boolean fade, boolean byBack) {
        main.removeCallbacksAndMessages(null);
        for (android.animation.Animator a : drifting) a.cancel();
        drifting.clear();
        FrameLayout v = host;
        boolean wasBar = bar;
        host = null;
        showing = null;
        bar = false;
        if (v == null) return;
        v.setOnKeyListener(null);
        Runnable gone = () -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
            if (wasBar) ScoreCard.lane("lineup", false);
        };
        if (!fade) { gone.run(); return; }
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            v.getContext().getSystemService(WindowManager.class).updateViewLayout(v, lp);
        } catch (RuntimeException ignored) { }
        if (byBack) {
            PopupMotion.backOut(v, v.getWidth(), wasBar ? v.getHeight() / 2f : 0f, gone);
            return;
        }
        if (wasBar) v.animate().alpha(0f).translationY(-px(18)).setDuration(240).withLayer().withEndAction(gone).start();
        else v.animate().alpha(0f).translationX(-px(30)).setDuration(260).withLayer().withEndAction(gone).start();
    }

    private static TextView text(Context c, String s, float sizePx, int colour, Typeface face) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * k);
        v.setTextColor(colour);
        v.setTypeface(face);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    // ---- pictures for a player without a cut-out ------------------------------------------------------------------

    private static int trim(int kit) {
        return lum(kit) > 0.62 ? NAVY : 0xFFFFFFFF;
    }

    /**
     * No photo anywhere: a blank figure, head and shoulders, in the team's kit as it is today (the shirt's body and
     * sleeve colours), lit from the top left so it has some depth, his number on the chest.
     */
    static Bitmap avatar(int kit, int sleeves, String number) {
        int body = kit != 0 ? kit : NAVY, arms = sleeves != 0 ? sleeves : body;
        int s = 300;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        // Drawn higher in the frame (the head near the top, as in the players' photos); the shirt runs to the foot.
        cv.translate(0, -26);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // The neck, shadowed under the chin.
        p.setShader(new LinearGradient(0, 140, 0, 182, 0xFF9AA0B2, 0xFFC4C9D6, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(127, 136, 173, 186), 14, 14, p);
        // The head, a lit sphere.
        p.setShader(new android.graphics.RadialGradient(128, 72, 92, new int[]{0xFFF7F8FB, 0xFFDDE0E8, 0xFFB4BAC9},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawOval(new RectF(98, 38, 202, 160), p);
        p.setShader(null);
        // The shirt: the body, the sleeves over the shoulders, shaded at the sides and lit along the top.
        Path shirt = shoulders();
        cv.save();
        cv.clipPath(shirt);
        p.setShader(new LinearGradient(0, 166, 0, 300, lighter(body), darker(body), Shader.TileMode.CLAMP));
        cv.drawRect(0, 150, s, s + 40, p);
        if (arms != body) {
            p.setShader(new LinearGradient(0, 166, 0, 300, lighter(arms), darker(arms), Shader.TileMode.CLAMP));
            Path left = new Path();
            left.moveTo(0, 340);
            left.lineTo(0, 196);
            left.quadTo(58, 180, 84, 206);
            left.lineTo(70, 340);
            left.close();
            cv.drawPath(left, p);
            Matrix mirror = new Matrix();
            mirror.setScale(-1, 1, 150, 0);
            Path right = new Path(left);
            right.transform(mirror);
            cv.drawPath(right, p);
        }
        p.setShader(new LinearGradient(0, 0, s, 0, new int[]{0x55000000, 0x00000000, 0x00000000, 0x55000000},
                new float[]{0.05f, 0.36f, 0.64f, 0.95f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 150, s, s + 40, p);
        p.setShader(new LinearGradient(0, 166, 0, 236, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 150, s, s + 40, p);
        cv.restore();
        p.setShader(null);
        // The collar, in the sleeves' colour where they differ, else white or navy against the shirt.
        int trim = arms != body ? arms : (lum(body) > 0.62 ? NAVY : 0xFFFFFFFF);
        p.setColor(trim);
        Path collar = new Path();
        collar.moveTo(116, 170);
        collar.quadTo(150, 210, 184, 170);
        collar.lineTo(175, 165);
        collar.quadTo(150, 196, 125, 165);
        collar.close();
        cv.drawPath(collar, p);
        // The number, with a soft shadow.
        if (number != null && !number.isEmpty()) {
            p.setColor(lum(body) > 0.62 ? NAVY : 0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(62);
            p.setShadowLayer(5, 0, 3, 0x66000000);
            cv.drawText(number, 150, 268, p);
            p.clearShadowLayer();
        }
        return b;
    }

    private static int lighter(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
        return 0xFF000000 | ((r + (255 - r) / 5) << 16) | ((g + (255 - g) / 5) << 8) | (bl + (255 - bl) / 5);
    }

    /** A photo of his face, framed in a white ring on shoulders in the team's kit (an old club's shirt never shows). */
    static Bitmap framed(Bitmap face, int kit) {
        int colour = kit != 0 ? kit : NAVY;
        int s = 300;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawPath(shoulders(), shirtPaint(colour));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Matrix m = new Matrix();
        float sc = 160f / Math.min(face.getWidth(), face.getHeight());
        m.setScale(sc, sc);
        m.postTranslate(150 - face.getWidth() * sc / 2f, 104 - face.getHeight() * sc / 2f);
        BitmapShader shader = new BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(m);
        p.setShader(shader);
        cv.drawCircle(150, 104, 80, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(150, 104, 80, p);
        return b;
    }

    private static Path shoulders() {
        Path body = new Path();
        body.moveTo(18, 340);
        body.lineTo(34, 222);
        body.quadTo(44, 186, 96, 172);
        body.lineTo(122, 164);
        body.quadTo(150, 186, 178, 164);
        body.lineTo(204, 172);
        body.quadTo(256, 186, 266, 222);
        body.lineTo(282, 340);
        body.close();
        return body;
    }

    private static Paint shirtPaint(int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 164, 0, 300, colour, darker(colour), Shader.TileMode.CLAMP));
        return p;
    }

    private static int darker(int c) {
        return 0xFF000000 | (((c >> 16) & 0xFF) * 8 / 10 << 16) | (((c >> 8) & 0xFF) * 8 / 10 << 8) | ((c & 0xFF) * 8 / 10);
    }

    private static double lum(int c) {
        return (0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF)) / 255.0;
    }

    /** How far apart two colours are (0 the same, about 440 black from white): a clash when close. */
    static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    // ---- drawing -------------------------------------------------------------------------------------------------

    /** A vertical gradient with diagonal bands across it, as Sky's line-up ground and band have. */
    private static final class Streaks extends Drawable {
        private final Paint fill = new Paint(), band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int top, bottom, gap, width;

        Streaks(int top, int bottom, int bandColour, int gap, int width) {
            this.top = top;
            this.bottom = bottom;
            this.gap = Math.max(4, gap);
            this.width = Math.max(1, width);
            band.setColor(bandColour);
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(r, fill);
            float slant = r.height() * 0.36f;
            Path p = new Path();
            for (float x = r.left - slant; x < r.right + slant; x += gap + width) {
                p.reset();
                p.moveTo(x, r.bottom);
                p.lineTo(x + width, r.bottom);
                p.lineTo(x + width + slant, r.top);
                p.lineTo(x + slant, r.top);
                p.close();
                c.drawPath(p, band);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }


    // ---- flags ---------------------------------------------------------------------------------------------------

    private static final java.util.Map<String, String> NATIONS = new java.util.HashMap<>();

    static {
        String[] pairs = {"England:gb-eng", "Scotland:gb-sct", "Wales:gb-wls", "Northern Ireland:gb-nir",
                "Republic of Ireland:ie", "Ireland:ie", "France:fr", "Spain:es", "Portugal:pt", "Italy:it", "Germany:de",
                "Netherlands:nl", "Belgium:be", "Brazil:br", "Argentina:ar", "Uruguay:uy", "Colombia:co", "Ecuador:ec",
                "Paraguay:py", "Chile:cl", "Peru:pe", "Venezuela:ve", "Mexico:mx", "United States:us", "USA:us",
                "Canada:ca", "Jamaica:jm", "Croatia:hr", "Serbia:rs", "Slovenia:si", "Slovakia:sk", "Czech Republic:cz",
                "Czechia:cz", "Poland:pl", "Hungary:hu", "Austria:at", "Switzerland:ch", "Denmark:dk", "Sweden:se",
                "Norway:no", "Finland:fi", "Iceland:is", "Ukraine:ua", "Russia:ru", "Georgia:ge", "Armenia:am",
                "Greece:gr", "Turkey:tr", "Türkiye:tr", "Albania:al", "Kosovo:xk", "North Macedonia:mk",
                "Bosnia and Herzegovina:ba", "Montenegro:me", "Romania:ro", "Bulgaria:bg", "Cyprus:cy", "Israel:il",
                "Morocco:ma", "Algeria:dz", "Tunisia:tn", "Egypt:eg", "Senegal:sn", "Mali:ml", "Ivory Coast:ci",
                "Côte d'Ivoire:ci", "Ghana:gh", "Nigeria:ng", "Cameroon:cm", "Gabon:ga", "Guinea:gn", "Burkina Faso:bf",
                "DR Congo:cd", "Congo:cg", "Zambia:zm", "Zimbabwe:zw", "South Africa:za", "Kenya:ke", "Angola:ao",
                "Cape Verde:cv", "Gambia:gm", "Benin:bj", "Togo:tg", "Mozambique:mz", "Japan:jp", "South Korea:kr",
                "Korea Republic:kr", "China:cn", "Australia:au", "New Zealand:nz", "Iran:ir", "Saudi Arabia:sa",
                "Qatar:qa", "Uzbekistan:uz", "Costa Rica:cr", "Honduras:hn", "Panama:pa", "Haiti:ht", "Suriname:sr",
                "Curaçao:cw", "Luxembourg:lu", "Estonia:ee", "Latvia:lv", "Lithuania:lt", "Belarus:by", "Moldova:md",
                "Malta:mt", "Faroe Islands:fo", "Grenada:gd", "Trinidad and Tobago:tt", "Bolivia:bo", "Guinea-Bissau:gw",
                "Equatorial Guinea:gq", "Sierra Leone:sl", "Liberia:lr", "Libya:ly", "Sudan:sd", "Uganda:ug",
                "Tanzania:tz", "Madagascar:mg", "Comoros:km", "Mauritania:mr", "Burundi:bi", "Rwanda:rw", "Iraq:iq",
                "Jordan:jo", "Syria:sy", "Lebanon:lb", "Philippines:ph", "Indonesia:id", "Thailand:th", "Vietnam:vn",
                "India:in"};
        for (String pair : pairs) {
            int at = pair.lastIndexOf(':');
            NATIONS.put(pair.substring(0, at).toLowerCase(java.util.Locale.ROOT), pair.substring(at + 1));
        }
    }

    /** A country's flag, small (flagcdn's 80-pixel PNG); null for a country it does not know. */
    static String flagUrl(String nation) {
        if (nation == null || nation.isEmpty()) return null;
        String code = NATIONS.get(nation.trim().toLowerCase(java.util.Locale.ROOT));
        return code == null ? null : "https://flagcdn.com/w80/" + code + ".png";
    }
}
