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
        String kickoff, channel;
        Bitmap channelLogo;
        /** OK: the game's channel opened (null without one). */
        Runnable watch;
        String remote = "";
    }

    static final class Team {
        String name, formation = "", kitName = "Home kit";
        Bitmap crest;
        int kit;
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
    private static final long PLAYERS_MS = 4_200L, PITCH_MS = 7_000L, LAST_MS = 7_000L, LAST_WATCH_MS = 10_000L;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static FrameLayout host;
    private static Data showing;
    private static float k;

    private LineupCard() { }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    /**
     * The remote's OK while the card shows (InputKeyService, before any app): the game's channel opened. True when
     * the card took the key.
     */
    static boolean key(KeyEvent e) {
        Data d = showing;
        if (host == null || d == null || d.watch == null) return false;
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (!ok) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> {
            close(true);
            d.watch.run();
        });
        return true;
    }

    /** Opens the card (on the main thread), in place of one already showing. */
    static void show(Context ctx, Data d) {
        Context c = ctx.getApplicationContext();
        close(false);
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f * SCALE;
        int w = px(W), h = px(H), margin = px(10);

        FrameLayout win = new FrameLayout(c);
        FrameLayout card = new FrameLayout(c);
        float r = px(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        card.setElevation(px(10));
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
        // The time the page has left: a line along the stage's foot that fills.
        View bar = new View(c);
        bar.setPivotX(0);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(w - px(BAND), px(3), Gravity.BOTTOM);
        bl.leftMargin = px(BAND);
        card.addView(bar, bl);

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, h);
        cl.setMargins(margin, margin, margin, margin);
        win.addView(card, cl);

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

        // The pages: each eleven in two (the keeper and defence first), both on one pitch, then the channel.
        List<Runnable> pages = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Team t : new Team[]{d.home, d.away}) {
            if (t == null || t.players.isEmpty()) continue;
            int split = Math.min(t.players.size(), (t.players.size() + 1) / 2);
            List<Player> first = t.players.subList(0, split), second = t.players.subList(split, t.players.size());
            pages.add(() -> { teamBand(c, band, t); slide(stage, players(c, t, first)); });
            times.add(PLAYERS_MS);
            if (!second.isEmpty()) {
                pages.add(() -> { teamBand(c, band, t); slide(stage, players(c, t, second)); });
                times.add(PLAYERS_MS);
            }
        }
        if (d.home != null && d.away != null && !d.home.players.isEmpty() && !d.away.players.isEmpty()) {
            pages.add(() -> { pitchBand(c, band, d); slide(stage, pitch(c, d)); });
            times.add(PITCH_MS);
        }
        pages.add(() -> { band.removeAllViews(); league(c, band, d); slide(stage, last(c, d)); });
        times.add(d.watch != null ? LAST_WATCH_MS : LAST_MS);
        int[] at = {0};
        Runnable step = new Runnable() {
            @Override public void run() {
                if (host != win) return;
                int i = at[0];
                pages.get(i).run();
                // Navy along the pale pages' foot, white along the last page's navy.
                bar.setBackgroundColor(i < pages.size() - 1 ? NAVY : 0xFFFFFFFF);
                bar.animate().cancel();
                bar.setScaleX(0f);
                bar.animate().scaleX(1f).setDuration(times.get(i)).setInterpolator(new LinearInterpolator()).start();
                if (i == pages.size() - 1) {
                    // The last page also takes the keys where no helper passes OK here (OK watches, anything else closes).
                    if (d.watch != null) offerWatch(win, d);
                    main.postDelayed(() -> { if (host == win) close(true); }, times.get(i));
                    return;
                }
                at[0]++;
                main.postDelayed(this, times.get(i));
            }
        };
        card.setAlpha(0f);
        card.setTranslationX(-px(40));
        card.animate().alpha(1f).translationX(0f).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).start();
        step.run();
    }

    /** A new page slides in from the right as the one before slides away to the left. */
    private static void slide(FrameLayout stage, View page) {
        int w = stage.getLayoutParams().width;
        for (int i = 0; i < stage.getChildCount(); i++) {
            View old = stage.getChildAt(i);
            old.animate().translationX(-w).setDuration(420).setInterpolator(new DecelerateInterpolator(1.6f))
                    .withEndAction(() -> stage.removeView(old)).start();
        }
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (stage.getChildCount() > 1) {
            page.setTranslationX(w);
            page.animate().translationX(0).setDuration(420).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    // ---- the band ------------------------------------------------------------------------------------------------

    private static void league(Context c, FrameLayout band, Data d) {
        ImageView league = new ImageView(c);
        league.setImageBitmap(d.leagueLogo);
        league.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(px(28), px(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ll.topMargin = px(12);
        band.addView(league, ll);
    }

    /** A crest as it is (no disc), with a soft shadow so a dark one still shows on the navy. */
    private static ImageView crest(Context c, Bitmap b) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(b);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setElevation(px(3));
        v.setOutlineProvider(null);
        return v;
    }

    /** The competition, the club's crest, and its name up the side, fitted to the room under the crest. */
    private static void teamBand(Context c, FrameLayout band, Team t) {
        band.removeAllViews();
        league(c, band, showing);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(50), px(50), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cl.topMargin = px(48);
        band.addView(crest(c, t.crest), cl);
        Upright name = new Upright(c, px(23));
        name.setText(t.name);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(px(40), px(108), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nl.bottomMargin = px(14);
        band.addView(name, nl);
    }

    /** For the pitch: both crests, each with its eleven's shape under it. */
    private static void pitchBand(Context c, FrameLayout band, Data d) {
        band.removeAllViews();
        league(c, band, d);
        int y = 48;
        for (Team t : new Team[]{d.home, d.away}) {
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(46), px(46), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            cl.topMargin = px(y);
            band.addView(crest(c, t.crest), cl);
            TextView f = text(c, t.formation, 12, 0xFFFFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            f.setLetterSpacing(0.1f);
            f.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            fl.topMargin = px(y + 49);
            band.addView(f, fl);
            y += 76;
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
            col.setAlpha(0f);
            col.setTranslationY(px(12));
            col.animate().alpha(1f).translationY(0f).setStartDelay(200L + 55L * i).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
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
        photo.setImageBitmap(p.photo == null ? avatar(t.kit, p.number) : p.framed ? framed(p.photo, t.kit) : p.photo);
        photo.setScaleType(ImageView.ScaleType.FIT_START);
        frame.addView(photo, new FrameLayout.LayoutParams(px(150), px(150), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
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
            flag.setImageBitmap(p.flag);
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
        page.setBackground(new Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(86), px(36)));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -1);
        fl.setMargins(px(8), px(8), px(8), px(8));
        page.addView(new Pitch(c, d), fl);
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
            // The logo at the foot, the pill in the top right corner.
            if (d.channelLogo != null) {
                float lh = px(16), lw = lh * d.channelLogo.getWidth() / Math.max(1f, d.channelLogo.getHeight());
                logoAt.set(w / 2f - lw / 2, h - px(11) - lh, w / 2f + lw / 2, h - px(11));
            } else {
                logoAt.setEmpty();
            }
            if (d.watch != null) {
                p.setTextSize(px(10.5f));
                float pw = p.measureText("Press") + p.measureText("to watch") + px(16) + px(6) * 2 + px(18);
                pillAt.set(w - px(10) - pw, px(8), w - px(10), px(8) + px(20));
            } else {
                pillAt.setEmpty();
            }
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
            for (int i = 0; i < xs.length; i++) draw(cv, teams.get(i), who.get(i), xs[i], ys[i], tws[i], sizes[i]);
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
            Bitmap face = pl.photo != null ? pl.photo : avatar(t.kit, "");
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

    private static View last(Context c, Data d) {
        FrameLayout page = new FrameLayout(c);
        page.setBackground(new Streaks(0xFF2A35B8, 0xFF080B3A, 0x0EFFFFFF, px(60), px(4)));
        LinearLayout vs = new LinearLayout(c);
        vs.setGravity(Gravity.CENTER);
        vs.addView(club(c, d.home));
        TextView ko = text(c, d.kickoff != null ? d.kickoff : "v", 32, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
        kl.leftMargin = kl.rightMargin = px(28);
        vs.addView(ko, kl);
        vs.addView(club(c, d.away));
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        vl.topMargin = px(24);
        page.addView(vs, vl);
        LinearLayout foot = new LinearLayout(c);
        foot.setGravity(Gravity.CENTER);
        View ch = channel(c, d, 26);
        if (ch != null) foot.addView(ch, new LinearLayout.LayoutParams(-2, px(26)));
        if (d.watch != null) {
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(34));
            ol.leftMargin = px(16);
            foot.addView(okPill(c, 14, 26), ol);
        }
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fl.bottomMargin = px(22);
        page.addView(foot, fl);
        return page;
    }

    private static View club(Context c, Team t) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.addView(crest(c, t == null ? null : t.crest), new LinearLayout.LayoutParams(px(70), px(70)));
        TextView n = text(c, t == null ? "" : t.name, 16, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        n.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(160), -2);
        nl.topMargin = px(6);
        v.addView(n, nl);
        return v;
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
        pill.setElevation(px(2));
        Typeface bold = Typeface.create("sans-serif-medium", Typeface.BOLD);
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
            logo.setImageBitmap(d.channelLogo);
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
            close(true);
            if (ok) d.watch.run();
            return true;
        });
    }

    /** The card away: faded, or at once when another takes its place. */
    static void close(boolean fade) {
        main.removeCallbacksAndMessages(null);
        FrameLayout v = host;
        host = null;
        showing = null;
        if (v == null) return;
        v.setOnKeyListener(null);
        Runnable gone = () -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
        };
        if (!fade) { gone.run(); return; }
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            v.getContext().getSystemService(WindowManager.class).updateViewLayout(v, lp);
        } catch (RuntimeException ignored) { }
        v.animate().alpha(0f).translationX(-px(30)).setDuration(260).withEndAction(gone).start();
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

    /** No photo anywhere: a blank avatar, head and shoulders in the team's kit, his number on the chest. */
    static Bitmap avatar(int kit, String number) {
        int colour = kit != 0 ? kit : NAVY;
        int s = 300;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 36, 0, 156, 0xFFE9EBF3, 0xFFC9CDDB, Shader.TileMode.CLAMP));
        cv.drawOval(new RectF(98, 36, 202, 156), p);
        p.setShader(null);
        p.setColor(0xFFC9CDDB);
        cv.drawRoundRect(new RectF(132, 146, 168, 172), 8, 8, p);
        cv.drawPath(shoulders(), shirtPaint(colour));
        p.setColor(trim(colour));
        Path collar = new Path();
        collar.moveTo(122, 164);
        collar.quadTo(150, 192, 178, 164);
        collar.lineTo(172, 160);
        collar.quadTo(150, 180, 128, 160);
        collar.close();
        cv.drawPath(collar, p);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(64);
        if (number != null && !number.isEmpty()) cv.drawText(number, 150, 262, p);
        return b;
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
        body.moveTo(22, 300);
        body.lineTo(34, 222);
        body.quadTo(44, 186, 96, 172);
        body.lineTo(122, 164);
        body.quadTo(150, 186, 178, 164);
        body.lineTo(204, 172);
        body.quadTo(256, 186, 266, 222);
        body.lineTo(278, 300);
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

    /** The club's name up the band, bottom to top, made smaller to fit the room it has. */
    private static final class Upright extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";

        Upright(Context c, float size) {
            super(c);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextSize(size);
        }

        void setText(String t) {
            text = t == null ? "" : t;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (text.isEmpty()) return;
            float size = p.getTextSize();
            float w = p.measureText(text);
            if (w > getHeight()) p.setTextSize(size * getHeight() / w);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.save();
            c.rotate(-90);
            c.drawText(text, -getHeight(), getWidth() / 2f - (fm.ascent + fm.descent) / 2f, p);
            c.restore();
            p.setTextSize(size);
        }
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
