package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A slim strip that slides down from under the scoreboard over the channel (BoardCover), in the board's own look
 * (BoardBug's StripLook: its ground, its words, its corners; under the Glass pill a pill of its own a little apart):
 * either the game's win chance as it stands (a bar in three parts, each club's share in its colour and the draw's in a
 * quiet grey, each with its figure, the parts growing and shrinking as the game goes), or a penalty shootout's kicks
 * (a row a club: its crest, its three letters, a dot a kick, green with a tick for one in, red with a cross for one
 * missed, an empty ring for one to come, the tally at the end). A kick scored lands with the family's swoosh: its dot
 * pops in from small past its size, its green sweeping round, its tick drawn in, a soft ring fading out from it, the
 * swoosh's loudest moment on the pop; one missed or saved has its red cross drawn in with a small shake, and no sound.
 * Small, so the picture stays the picture: no taller than the board.
 */
final class BoardUnder extends View {
    static final int NONE = 0, WIN = 1, PENS = 2;
    /** Its height for each, in board heights (the room under the board for it: ROOM). */
    static final float WIN_H = 0.62f, PENS_H = 0.95f, ROOM = 1.1f;
    private static final long SHARE_MS = 900L;
    /** A kick scored's landing, its pop that far in (the swoosh's loudest moment on it); a kick missed's. */
    static final long SCORED_MS = 760L, SCORED_POP_MS = 300L, MISSED_MS = 560L;
    /** The swoosh a kick scored lands with (the family's goal sound). */
    static final String SWOOSH = "goal-" + com.gathin.broadcast.Sfx.GOAL_DEFAULT;
    static final int IN = 0xFF2DB560, MISSED = 0xFFE04848, DRAW_DARK = 0xFF5A6278, DRAW_LIGHT = 0xFFB8BECC;

    private BoardBug.StripLook look = new BoardBug.StripLook();
    /** The board's height; how far its top is under the board; the gap under a pill. */
    private float unit = 48f;
    private int hide, gap;
    private int mode = NONE;
    /** 0 under the board, 1 all the way down. */
    private float out;
    private ValueAnimator sliding, sharing, popping;
    private Runnable after;
    private boolean goingIn;

    // The win chance: each part's share drawn now {home, draw, away}, the figures, the clubs' colours.
    private final float[] shares = {1 / 3f, 1 / 3f, 1 / 3f};
    private int[] pct = {33, 34, 33};
    private int homeColour = 0xFFD8323B, awayColour = 0xFF4F8BFF;

    // The shootout: each side's kicks shown, the clubs' letters and crests, the newest kick's place and its pop.
    private final List<Boolean> homeKicks = new ArrayList<>(), awayKicks = new ArrayList<>();
    private String homeCode = "", awayCode = "";
    private Bitmap homeCrest, awayCrest;
    private boolean newHome, newScored;
    private int newAt = -1;
    /** The newest kick's landing, 0 to 1 (below 0: still to land, its empty ring showing). */
    private float pop = 1f;
    private final Runnable swooshRun = this::swoosh;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            stroke = new Paint(Paint.ANTI_ALIAS_FLAG), image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path shape = new Path(), mark = new Path();
    private final RectF r = new RectF();
    private final float[] corners = new float[8];

    BoardUnder(Context c) {
        super(c);
        text.setTypeface(ScoreCard.outfit(c, 700));
        text.setFontFeatureSettings("tnum");
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setVisibility(GONE);
        com.gathin.broadcast.Sfx.warm(c, SWOOSH);
    }

    /** Set for a board unit pixels high in this look: hide pixels of its top under the board, gap between them (a pill). */
    void place(BoardBug.StripLook s, float unit, int hide, int gap) {
        look = s;
        this.unit = Math.max(16f, unit);
        this.hide = Math.max(0, hide);
        this.gap = Math.max(0, gap);
        if (isOut()) invalidate();
    }

    int mode() {
        return mode;
    }

    boolean isOut() {
        return getVisibility() == VISIBLE;
    }

    /** Pure: the strip's own height (under what is hidden) for a mode, a board unit high. */
    static int heightFor(int mode, float unit) {
        return Math.round(unit * (mode == PENS ? PENS_H : WIN_H));
    }

    // ---- the win chance --------------------------------------------------------------------------------------------

    /**
     * The win chance {home, draw, away} in whole percentages, the clubs' colours (as the board has them): down from under
     * the board in its parts, or, already down, its parts growing and shrinking to these.
     */
    void win(int[] p, int home, int away) {
        if (p == null || p.length < 3) return;
        homeColour = 0xFF000000 | home;
        awayColour = 0xFF000000 | away;
        boolean same = mode == WIN && isOut() && pct[0] == p[0] && pct[1] == p[1] && pct[2] == p[2];
        pct = p.clone();
        if (same) return;
        float[] to = {p[0] / 100f, p[1] / 100f, p[2] / 100f};
        if (mode != WIN || !isOut()) {
            mode = WIN;
            // Coming down even, then each part to its share.
            shares[0] = shares[1] = shares[2] = 1 / 3f;
            slideDown();
            shareTo(to, BoardSide.SLIDE_MS);
        } else {
            shareTo(to, 0);
        }
    }

    private void shareTo(float[] to, long delay) {
        if (sharing != null) sharing.cancel();
        float[] from = shares.clone();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(SHARE_MS);
        va.setStartDelay(delay);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        va.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            for (int i = 0; i < 3; i++) shares[i] = from[i] + (to[i] - from[i]) * t;
            invalidate();
        });
        sharing = va;
        va.start();
    }

    // ---- the shootout ------------------------------------------------------------------------------------------------

    /** The clubs' letters, crests and colours (a disc of it where there is no crest) for the shootout's rows. */
    void clubs(String home, String away, Bitmap homeC, Bitmap awayC, int homeKit, int awayKit) {
        homeCode = home == null ? "" : home;
        awayCode = away == null ? "" : away;
        homeCrest = homeC;
        awayCrest = awayC;
        homeColour = 0xFF000000 | homeKit;
        awayColour = 0xFF000000 | awayKit;
    }

    /**
     * The shootout's kicks shown so far, each side's in order: down from under the board, or (down already) the newest
     * one landing (home: whose it is): one scored with the swoosh, its loudest moment on the dot's pop; one missed quietly.
     */
    void pens(List<Boolean> home, List<Boolean> away, boolean popNewest, boolean newestHome) {
        boolean first = mode != PENS || !isOut();
        homeKicks.clear();
        homeKicks.addAll(home);
        awayKicks.clear();
        awayKicks.addAll(away);
        mode = PENS;
        if (first) slideDown();
        List<Boolean> side = newestHome ? homeKicks : awayKicks;
        if (popNewest && !side.isEmpty()) {
            newHome = newestHome;
            newAt = side.size() - 1;
            newScored = Boolean.TRUE.equals(side.get(newAt));
            if (popping != null) popping.cancel();
            removeCallbacks(swooshRun);
            // After the strip is down; a kick scored: the swoosh first, the dot landing as it peaks.
            long lead = first ? BoardSide.SLIDE_MS : 0;
            if (newScored) {
                postDelayed(swooshRun, lead);
                lead += swooshLead();
            }
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(newScored ? SCORED_MS : MISSED_MS);
            va.setStartDelay(lead);
            va.setInterpolator(new android.view.animation.LinearInterpolator());
            va.addUpdateListener(a -> {
                pop = (float) a.getAnimatedValue();
                invalidate();
            });
            pop = -1f;
            popping = va;
            va.start();
        }
        invalidate();
    }

    /** Pure: how long after the swoosh starts a kick scored's landing begins, so its loudest moment is on the pop. */
    static long swooshLead() {
        return Math.max(0L, com.gathin.broadcast.Sfx.SWOOSH_PEAK_MS - SCORED_POP_MS);
    }

    /** Pure: where a kick scored's pop is in its landing (0 to 1). */
    static float popAt() {
        return SCORED_POP_MS / (float) SCORED_MS;
    }

    /** Pure: a kick scored's size as its landing goes from 0 to 1: in from small, past its size at the pop, settling back. */
    static float scoredScale(float t) {
        if (t >= 1f) return 1f;
        float pa = popAt();
        if (t <= 0f) return 0.3f;
        if (t < pa) return 0.3f + 0.95f * BoardBug.ease(t / pa);
        float settle = Math.min(1f, (t - pa) / 0.3f);
        return 1.25f - 0.25f * BoardBug.ease(settle);
    }

    /** Pure: how far round its green has swept (0 to 1): all the way by the pop. */
    static float sweepOf(float t) {
        return BoardBug.ease(Math.max(0f, Math.min(1f, t / popAt())));
    }

    /** Pure: how far a part drawn in between from and to (shares of the landing) has come at t (0 to 1). */
    static float drawnOf(float t, float from, float to) {
        if (to <= from) return t >= to ? 1f : 0f;
        return Math.max(0f, Math.min(1f, (t - from) / (to - from)));
    }

    /** Pure: the soft ring's spread from the pop (0 none yet, then out to 1 as it fades). */
    static float glowOf(float t) {
        float pa = popAt();
        return t <= pa ? 0f : Math.min(1f, (t - pa) / (1f - pa));
    }

    /** Pure: a kick missed's small shake sideways (-1 to 1 of its most), after its cross is in, dying away to nothing. */
    static float shakeOf(float t) {
        if (t <= 0.35f || t >= 1f) return 0f;
        float u = (t - 0.35f) / 0.65f;
        return (float) Math.sin(u * Math.PI * 6) * (1f - u);
    }

    /** A kick scored's swoosh, panned to where the strip is: not with Goal sound off, nor while the board is hidden. */
    private void swoosh() {
        if (!isAttachedToWindow() || !isShown() || getAlpha() < 0.05f || "off".equals(BoardCover.goalSound(getContext()))) return;
        int[] at = new int[2];
        getLocationOnScreen(at);
        int sw = getResources().getDisplayMetrics().widthPixels;
        float pan = sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
        com.gathin.broadcast.Sfx.play(getContext(), SWOOSH, pan, 0.85f);
    }

    /** Its width for the shootout's rows with this many dots each (after place() and clubs()). */
    int pensWidth(int slots) {
        return Math.round(pensLayoutW(Math.max(5, slots)));
    }

    private float pensLayoutW(int slots) {
        float h = heightFor(PENS, unit), row = h / 2f, d = dot(row);
        text.setTextSize(codeSize());
        float codes = Math.max(text.measureText(homeCode), text.measureText(awayCode));
        text.setTextSize(tallySize());
        float tally = text.measureText("10");
        return pad() + chipW() + unit * 0.2f + row * 0.78f + unit * 0.12f + codes + unit * 0.22f
                + slots * d + (slots - 1) * d * 0.38f + unit * 0.3f + tally + pad();
    }

    private float pad() {
        return look.apart ? unit * 0.3f : unit * 0.22f;
    }

    private float chipW() {
        return unit * 0.4f;
    }

    private float codeSize() {
        return unit * 0.3f;
    }

    private float tallySize() {
        return unit * 0.36f;
    }

    private static float dot(float row) {
        return row * 0.66f;
    }

    // ---- down and up -------------------------------------------------------------------------------------------------

    private void slideDown() {
        if (!isOut()) {
            out = 0f;
            swish();
        }
        goingIn = false;
        after = null;
        setVisibility(VISIBLE);
        to(1f, null);
    }

    /** Back up under the board; then() once it is away (at once if it is). */
    void fold(Runnable then) {
        if (!isOut()) {
            stopAll();
            mode = NONE;
            if (then != null) then.run();
            return;
        }
        Runnable was = after;
        after = then == null ? was : was == null ? then : () -> {
            was.run();
            then.run();
        };
        if (!goingIn) swish();
        goingIn = true;
        to(0f, () -> {
            goingIn = false;
            setVisibility(GONE);
            stopAll();
            mode = NONE;
            Runnable a = after;
            after = null;
            if (a != null) a.run();
        });
    }

    /** Away at once (the board's window going). */
    void gone() {
        if (sliding != null) sliding.cancel();
        stopAll();
        goingIn = false;
        after = null;
        out = 0f;
        mode = NONE;
        setVisibility(GONE);
    }

    /** Previews and checks: down at once in this mode. */
    void downNow(int m) {
        if (sliding != null) sliding.cancel();
        mode = m;
        out = 1f;
        pop = 1f;
        setVisibility(VISIBLE);
        invalidate();
    }

    private void stopAll() {
        if (sharing != null) sharing.cancel();
        if (popping != null) popping.cancel();
        removeCallbacks(swooshRun);
        pop = 1f;
    }

    private void swish() {
        if (!BoardCover.swishOn(getContext())) return;
        int[] at = new int[2];
        getLocationOnScreen(at);
        int sw = getResources().getDisplayMetrics().widthPixels;
        float pan = sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
        com.gathin.broadcast.Sfx.play(getContext(), com.gathin.broadcast.Sfx.SWISH, pan, 0.6f);
    }

    private void to(float target, Runnable then) {
        if (sliding != null) sliding.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(out, target);
        va.setDuration(Math.max(1, Math.round(BoardSide.SLIDE_MS * Math.abs(target - out))));
        va.addUpdateListener(a -> {
            out = (float) a.getAnimatedValue();
            invalidate();
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (!cancelled && then != null) then.run();
            }
        });
        sliding = va;
        va.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sliding != null) sliding.cancel();
        stopAll();
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), all = getHeight();
        if (w <= 0 || all <= 0 || out <= 0f || mode == NONE) return;
        float top = look.apart ? hide + gap : 0f, contentTop = hide + gap, h = all - contentTop;
        if (h <= 2) return;
        cv.save();
        // Down from under the board: at the start its foot is level with the board's.
        cv.translate(0, -(1f - BoardBug.ease(out)) * (all - hide));
        float rad = look.apart ? Math.min(h / 2f, unit * 0.5f) : Math.max(3f, Math.min(h / 2f, unit * look.round));
        float tr = look.apart ? rad : 0f;
        corners[0] = corners[1] = corners[2] = corners[3] = tr;
        corners[4] = corners[5] = corners[6] = corners[7] = rad;
        r.set(0, top, w, all);
        shape.reset();
        shape.addRoundRect(r, corners, Path.Direction.CW);
        fill.setColor(look.ground);
        cv.drawPath(shape, fill);
        cv.save();
        cv.clipPath(shape);
        if (mode == WIN) drawWin(cv, w, contentTop, h);
        else drawPens(cv, w, contentTop, h);
        cv.restore();
        cv.restore();
    }

    private void drawWin(Canvas cv, int w, float y, float h) {
        float x = pad(), end = w - pad();
        // Its name, small and quiet, then the bar.
        text.setTextSize(unit * 0.22f);
        text.setLetterSpacing(0.12f);
        String name = "WIN CHANCE";
        float nw = text.measureText(name);
        boolean named = end - x - nw > unit * 3.2f;
        if (named) {
            words(cv, name, x, y, h, unit * 0.22f, 0.12f, look.dim, Paint.Align.LEFT);
            x += nw + unit * 0.24f;
        }
        float bh = Math.min(h * 0.66f, unit * 0.44f), by = y + (h - bh) / 2f, gapX = Math.max(1.5f, unit * 0.04f);
        float room = end - x - 2 * gapX, br = Math.min(bh / 2f, look.apart ? bh / 2f : Math.max(2f, bh * look.round * 1.6f));
        int[] colours = {barColour(homeColour), look.light ? DRAW_LIGHT : DRAW_DARK, barColour(awayColour)};
        float sum = Math.max(0.001f, shares[0] + shares[1] + shares[2]);
        float px = x;
        for (int i = 0; i < 3; i++) {
            float pw = room * shares[i] / sum;
            if (pw <= 0.5f) {
                px += gapX;
                continue;
            }
            fill.setColor(colours[i]);
            r.set(px, by, px + pw, by + bh);
            // The outer ends round, the joins square.
            float lr = i == 0 ? br : 0f, rr = i == 2 ? br : 0f;
            corners[0] = corners[1] = corners[6] = corners[7] = lr;
            corners[2] = corners[3] = corners[4] = corners[5] = rr;
            shape.reset();
            shape.addRoundRect(r, corners, Path.Direction.CW);
            cv.drawPath(shape, fill);
            String fig = pct[i] + "%";
            float fs = Math.min(bh * 0.74f, unit * 0.3f);
            text.setTextSize(fs);
            text.setLetterSpacing(0f);
            if (text.measureText(fig) + unit * 0.16f <= pw) {
                words(cv, fig, px + pw / 2f, by, bh, fs, 0f, BoardBug.ink(colours[i]), Paint.Align.CENTER);
            }
            px += pw + gapX;
        }
    }

    /** A club's colour on the bar: as on the board for a dark ground (a very dark one lightened), darkened for a light one. */
    private int barColour(int c) {
        return look.light ? BoardDrop.legible(c, true) : BoardStats.visible(c);
    }

    private void drawPens(Canvas cv, int w, float y, float h) {
        int slots = Shootout.slots(homeKicks.size(), awayKicks.size(), Shootout.decided(homeKicks, awayKicks));
        float need = pensLayoutW(slots), k = Math.min(1f, w / Math.max(1f, need));
        cv.save();
        if (k < 1f) cv.scale(k, k, 0, y + h / 2f);
        float x = pad();
        // PENS, small, down a block of the board's colour.
        float cw = chipW(), ch = h - unit * 0.2f, cy = y + unit * 0.1f;
        fill.setColor(look.block);
        r.set(x, cy, x + cw, cy + ch);
        float crr = look.apart ? cw / 2f : Math.max(2f, cw * 0.22f);
        cv.drawRoundRect(r, crr, crr, fill);
        cv.save();
        cv.rotate(-90f, x + cw / 2f, cy + ch / 2f);
        int chipInk = (look.block >>> 24) < 0xC0 ? look.ink : BoardBug.ink(look.block);
        words(cv, "PENS", x + cw / 2f, cy + ch / 2f - cw / 2f, cw, unit * 0.2f, 0.14f, chipInk, Paint.Align.CENTER);
        cv.restore();
        x += cw + unit * 0.2f;
        float row = h / 2f;
        // A hairline between the rows.
        fill.setColor(look.dim);
        fill.setAlpha(50);
        cv.drawRect(x, y + row - Math.max(0.5f, unit * 0.012f), w / k - pad(), y + row + Math.max(0.5f, unit * 0.012f), fill);
        fill.setAlpha(255);
        // The tallies (and who won) as the dots stand: a kick still to land not yet counted.
        List<Boolean> hs = landed(homeKicks, true), as = landed(awayKicks, false);
        boolean done = Shootout.decided(hs, as);
        int th = Shootout.tally(hs), ta = Shootout.tally(as);
        pensRow(cv, x, y, row, homeCrest, homeCode, homeKicks, true, slots, th, done && th > ta);
        pensRow(cv, x, y + row, row, awayCrest, awayCode, awayKicks, false, slots, ta, done && ta > th);
        cv.restore();
    }

    /** A side's kicks that have landed: the newest left out while it is still to land. */
    private List<Boolean> landed(List<Boolean> kicks, boolean home) {
        if (pop >= 0f || home != newHome || newAt != kicks.size() - 1 || kicks.isEmpty()) return kicks;
        return kicks.subList(0, kicks.size() - 1);
    }

    private void pensRow(Canvas cv, float x, float y, float row, Bitmap crest, String code, List<Boolean> kicks, boolean home,
                         int slots, int tally, boolean won) {
        float cs = row * 0.78f;
        if (crest != null) {
            image(cv, crest, x, y + (row - cs) / 2f, cs);
        } else {
            fill.setColor(barColour(home ? homeColour : awayColour));
            cv.drawCircle(x + cs / 2f, y + row / 2f, cs * 0.4f, fill);
        }
        x += cs + unit * 0.12f;
        text.setTextSize(codeSize());
        float codes = Math.max(text.measureText(homeCode), text.measureText(awayCode));
        words(cv, code, x, y, row, codeSize(), 0.02f, look.ink, Paint.Align.LEFT);
        x += codes + unit * 0.22f;
        float d = dot(row), step = d * 1.38f;
        for (int i = 0; i < slots; i++) {
            float cx = x + d / 2f + i * step, cy = y + row / 2f;
            boolean newest = home == newHome && i == newAt;
            if (i < kicks.size() && !(newest && pop < 0f)) {
                kick(cv, cx, cy, d / 2f, Boolean.TRUE.equals(kicks.get(i)), newest ? pop : 1f);
            } else {
                stroke.setColor(look.dim);
                stroke.setAlpha(150);
                stroke.setStrokeWidth(Math.max(1f, d * 0.11f));
                cv.drawCircle(cx, cy, d / 2f - stroke.getStrokeWidth() / 2f, stroke);
                stroke.setAlpha(255);
            }
        }
        x += slots * d + (slots - 1) * d * 0.38f + unit * 0.3f;
        // The tally: the winners' in green once it is decided.
        String t = String.valueOf(tally);
        if (won) {
            text.setTextSize(tallySize());
            float tw = Math.max(text.measureText(t), row * 0.5f) + unit * 0.16f, tbh = row * 0.8f;
            fill.setColor(IN);
            r.set(x - unit * 0.08f, y + (row - tbh) / 2f, x - unit * 0.08f + tw, y + (row + tbh) / 2f);
            cv.drawRoundRect(r, tbh * 0.3f, tbh * 0.3f, fill);
            words(cv, t, r.centerX(), y, row, tallySize(), 0f, 0xFFFFFFFF, Paint.Align.CENTER);
        } else {
            words(cv, t, x, y, row, tallySize(), 0f, look.ink, Paint.Align.LEFT);
        }
    }

    /**
     * One kick's dot as it lands (t 0 to 1; 1 landed): one scored green with a tick, popping in from small past its size,
     * its green sweeping round and its tick drawn in by the pop, then a soft ring fading out from it; one missed red with
     * a cross, its strokes drawn in one after the other, then a small shake.
     */
    private void kick(Canvas cv, float cx, float cy, float rad, boolean in, float t) {
        float d = Math.max(0f, Math.min(1f, t));
        if (in) {
            float rr = rad * scoredScale(d), sweep = sweepOf(d), glow = glowOf(d);
            if (glow > 0f && glow < 1f) {
                stroke.setColor(IN);
                stroke.setAlpha(Math.round(150 * (1f - glow) * (1f - glow)));
                stroke.setStrokeWidth(Math.max(1f, rad * 0.22f * (1f - glow * 0.5f)));
                cv.drawCircle(cx, cy, rad * (1.05f + 0.85f * glow), stroke);
                stroke.setAlpha(255);
            }
            fill.setColor(IN);
            if (sweep >= 1f) {
                cv.drawCircle(cx, cy, rr, fill);
            } else {
                fill.setAlpha(64);
                cv.drawCircle(cx, cy, rr, fill);
                fill.setAlpha(255);
                r.set(cx - rr, cy - rr, cx + rr, cy + rr);
                cv.drawArc(r, -90f, 360f * sweep, true, fill);
            }
            // The tick, drawn in along its two strokes.
            float p = d >= 1f ? 1f : drawnOf(d, popAt() * 0.7f, popAt() + 0.18f);
            if (p > 0f) {
                stroke.setColor(0xFFFFFFFF);
                stroke.setStrokeWidth(Math.max(1.2f, rr * 0.26f));
                line(cv, new float[]{cx - rr * 0.45f, cy + rr * 0.02f, cx - rr * 0.1f, cy + rr * 0.36f, cx + rr * 0.48f, cy - rr * 0.32f}, p);
            }
            return;
        }
        // Missed: in at its size, quietly; the cross's strokes one after the other, then a small shake.
        float rr = rad * (0.7f + 0.3f * BoardBug.ease(drawnOf(d, 0f, 0.3f))), dx = shakeOf(d) * rad * 0.22f;
        fill.setColor(MISSED);
        fill.setAlpha(Math.round(255 * Math.min(1f, 0.35f + drawnOf(d, 0f, 0.25f))));
        cv.drawCircle(cx + dx, cy, rr, fill);
        fill.setAlpha(255);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(Math.max(1.2f, rr * 0.26f));
        float a = rr * 0.36f, x = cx + dx;
        float one = d >= 1f ? 1f : drawnOf(d, 0.12f, 0.36f), two = d >= 1f ? 1f : drawnOf(d, 0.3f, 0.55f);
        if (one > 0f) line(cv, new float[]{x - a, cy - a, x + a, cy + a}, one);
        if (two > 0f) line(cv, new float[]{x + a, cy - a, x - a, cy + a}, two);
    }

    /** A line through these points {x, y, ...}, drawn this share of the way along. */
    private void line(Canvas cv, float[] pts, float share) {
        float total = 0;
        for (int i = 2; i < pts.length; i += 2) total += (float) Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
        float left = total * Math.max(0f, Math.min(1f, share));
        mark.reset();
        mark.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length && left > 0f; i += 2) {
            float seg = (float) Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]), k = seg <= 0 ? 1f : Math.min(1f, left / seg);
            mark.lineTo(pts[i - 2] + (pts[i] - pts[i - 2]) * k, pts[i - 1] + (pts[i + 1] - pts[i - 1]) * k);
            left -= seg;
        }
        cv.drawPath(mark, stroke);
    }

    private void image(Canvas cv, Bitmap b, float x, float y, float size) {
        float s = size / Math.max(b.getWidth(), b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(x + (size - bw) / 2f, y + (size - bh) / 2f, x + (size + bw) / 2f, y + (size + bh) / 2f);
        cv.drawBitmap(b, null, r, image);
    }

    /** Words at x (from it, or centred on it), in the middle of (y, rowH). */
    private void words(Canvas cv, String s, float x, float y, float rowH, float size, float spacing, int colour, Paint.Align align) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setLetterSpacing(spacing);
        text.setColor(colour);
        text.setTextAlign(align);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, x, y + rowH / 2f - (fm.ascent + fm.descent) / 2f, text);
        text.setLetterSpacing(0f);
    }
}
