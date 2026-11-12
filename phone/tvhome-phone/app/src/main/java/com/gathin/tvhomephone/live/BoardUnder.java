package com.gathin.tvhomephone.live;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The slim strip under the scoreboard over the picture (ScoreBoard), in the board's own look (its StripLook: ground,
 * words, corners; tucked under the board, or a little apart under a pill), sliding down from under it and back up. It
 * shows one of two things. The win chance: each side's chance of winning and the draw's, a three-part bar in the clubs'
 * own colours with the percentages over it, the bar's parts moving as the chances do. A penalty shootout, in its place
 * while one is on: two small rows, each side's crest, its three letters, a dot a kick (scored green with a tick, missed
 * red with a cross, to come an empty ring), and its tally; the newest kick lands (PenMark): a goal pops in on the
 * family's swoosh with a green sweep and a fading glow ring, a miss has its cross drawn in with a small shake and no
 * sound. Small, so it never covers much of the picture. Drawn, not built of views.
 */
final class BoardUnder extends View {
    static final int NONE = 0, CHANCE = 1, PENS = 2;
    static final int SCORED_GREEN = 0xFF2FBF5F, MISSED_RED = 0xFFE5484D;
    static final long SLIDE_MS = 360L, BAR_MS = 700L;

    private OverlayBug.StripLook look = new OverlayBug.StripLook();
    private float unit = 48f;
    private int mode = NONE, want = NONE;
    /** 0 up under the board, 1 all the way down. */
    private float out;
    private ValueAnimator sliding, bar, pop;

    // The win chance: the percentages now, shown (moving towards them), and the clubs.
    private final float[] shown = {0, 0, 0}, from = {0, 0, 0};
    private final int[] pct = {0, 0, 0};
    private float barT = 1f;
    private String homeCode = "", awayCode = "";
    private int homeColour = 0xFF2A4FD0, awayColour = 0xFFD42B2B;
    private Bitmap homeCrest, awayCrest;

    // The shootout: each side's dots and tally; the newest kick landing (PenMark), ms into it (-1 waiting for its sound).
    private int[][] dots = new int[2][5];
    private int homeScored, awayScored, popSide = -1, popIndex = -1;
    private boolean popScored;
    private long popMs = Long.MAX_VALUE;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path shape = new Path(), mark = new Path();
    private final float[] corners = new float[8];

    BoardUnder(Context c) {
        super(c);
        setVisibility(GONE);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    /** Set for a board unit pixels high (its words' unit) in this look, under a board boardW wide. */
    void place(OverlayBug.StripLook s, float unit, int boardW) {
        float u = Math.max(12f, unit);
        if (look == s && this.unit == u && this.boardW == boardW) return;
        look = s;
        this.unit = u;
        this.boardW = boardW;
        invalidate();
    }

    private int boardW;

    /** Pure: its height for what it shows at a board unit high: the win chance slim, the shootout's two rows a board high. */
    static int heightFor(int mode, float unit) {
        return Math.round(mode == PENS ? unit * 0.96f : mode == CHANCE ? unit * 0.62f : 0);
    }

    /** Pure: the shootout's width at a board unit high with n dots a row: the label, crest, letters, dots, tally. */
    static int pensWidth(float unit, int n) {
        float w = unit * (0.18f + 0.34f + 0.1f + 0.36f + 0.12f + 0.95f + 0.08f) + n * unit * 0.36f + unit * (0.14f + 0.36f + 0.18f);
        return Math.round(w);
    }

    int mode() {
        return mode;
    }

    boolean isOut() {
        return getVisibility() == VISIBLE && want != NONE;
    }

    /** The clubs it is about: their three letters, colours (0 for none) and crests. Drawn again only when they change. */
    void clubs(String home, String away, int homeColour, int awayColour, Bitmap homeCrest, Bitmap awayCrest) {
        String h = home == null ? "" : home, a = away == null ? "" : away;
        if (h.equals(homeCode) && a.equals(awayCode) && homeColour == rawHome && awayColour == rawAway && homeCrest == this.homeCrest
                && awayCrest == this.awayCrest && lookFor == look) return;
        homeCode = h;
        awayCode = a;
        rawHome = homeColour;
        rawAway = awayColour;
        lookFor = look;
        this.homeColour = homeColour != 0 ? 0xFF000000 | homeColour : 0xFF2A4FD0;
        this.awayColour = awayColour != 0 ? 0xFF000000 | awayColour : 0xFFD42B2B;
        // Two clubs of much the same colour: the away side's made lighter (darker on a light ground) to tell them apart.
        if (near(this.homeColour, this.awayColour)) this.awayColour = OverlayBug.blend(this.awayColour, look.light ? 0xFF0B1033 : 0xFFFFFFFF, 0.55f);
        // A club's colour too near the ground it sits on: its bar part made to show there.
        this.homeColour = BoardDrop.legible(this.homeColour, look.light);
        this.awayColour = BoardDrop.legible(this.awayColour, look.light);
        this.homeCrest = homeCrest;
        this.awayCrest = awayCrest;
        invalidate();
    }

    private int rawHome, rawAway;
    private OverlayBug.StripLook lookFor;

    /** Pure: whether two colours are too alike to tell apart side by side. */
    static boolean near(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db < 70 * 70;
    }

    /** The win chance {home, draw, away} in whole percentages: down from under the board, or its bar moving to them. */
    void chance(int[] p) {
        boolean same = mode == CHANCE && pct[0] == p[0] && pct[1] == p[1] && pct[2] == p[2];
        if (same && want == CHANCE) return;
        boolean fresh = mode != CHANCE;
        System.arraycopy(p, 0, pct, 0, 3);
        if (fresh) {
            for (int i = 0; i < 3; i++) shown[i] = from[i] = p[i];
            barT = 1f;
        } else {
            System.arraycopy(shown, 0, from, 0, 3);
            if (bar != null) bar.cancel();
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(BAR_MS);
            va.setInterpolator(new DecelerateInterpolator(2f));
            va.addUpdateListener(an -> {
                barT = (float) an.getAnimatedValue();
                for (int i = 0; i < 3; i++) shown[i] = from[i] + (pct[i] - from[i]) * barT;
                invalidate();
            });
            bar = va;
            va.start();
        }
        show(CHANCE);
    }

    /** The shootout as it stands: each side's dots and tally, the newest kick's side and place (-1 none) popping in. */
    void pens(int[][] d, int homeScored, int awayScored, int newSide, int newIndex) {
        if (newSide < 0 && mode == PENS && java.util.Arrays.deepEquals(dots, d) && this.homeScored == homeScored
                && this.awayScored == awayScored) {
            show(PENS);
            return;
        }
        dots = d;
        this.homeScored = homeScored;
        this.awayScored = awayScored;
        if (newSide >= 0 && newIndex >= 0 && newIndex < d[newSide].length) land(newSide, newIndex, d[newSide][newIndex] == Shootout.SCORED);
        show(PENS);
        invalidate();
    }

    /**
     * The newest kick landing: scored, the family's swoosh first (not with the goal sound off) and the mark popping in on
     * its loudest moment, a green sweep, the tick, a glow ring fading; missed or saved, no sound, the cross drawn in with
     * a small shake. Only while it is on the screen.
     */
    private void land(int side, int index, boolean scored) {
        if (pop != null) pop.cancel();
        popSide = side;
        popIndex = index;
        popScored = scored;
        boolean sound = scored && isShown() && out > 0f && !"off".equals(PlayerIntro.goalSound(getContext()));
        if (sound) swoosh();
        long wait = sound ? PenMark.startAfterSound(com.gathin.broadcast.Sfx.SWOOSH_PEAK_MS) : 0L;
        long length = scored ? PenMark.SCORED_MS : PenMark.MISSED_MS;
        popMs = -1L;
        ValueAnimator va = ValueAnimator.ofFloat(0f, length);
        va.setStartDelay(wait);
        va.setDuration(length);
        va.setInterpolator(null);
        va.addUpdateListener(an -> {
            popMs = Math.round((float) an.getAnimatedValue());
            invalidate();
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                popMs = Long.MAX_VALUE;
                invalidate();
            }
        });
        pop = va;
        va.start();
    }

    /** The family's swoosh, panned to where the strip is on the screen. */
    private void swoosh() {
        int[] at = new int[2];
        getLocationOnScreen(at);
        int sw = getResources().getDisplayMetrics().widthPixels;
        float pan = sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
        com.gathin.broadcast.Sfx.play(getContext(), "goal-" + com.gathin.broadcast.Sfx.GOAL_DEFAULT, pan, 0.85f);
    }

    /** Back up under the board. */
    void away() {
        if (want == NONE) return;
        want = NONE;
        if (getVisibility() != VISIBLE || out <= 0f) {
            gone();
            return;
        }
        swish();
        to(0f, () -> {
            setVisibility(GONE);
            mode = NONE;
        });
    }

    /** Away at once (the board going). */
    void gone() {
        if (sliding != null) sliding.cancel();
        want = NONE;
        mode = NONE;
        out = 0f;
        setVisibility(GONE);
    }

    /** The scoreboard's swish, panned to where it is on the screen (not with the switch off). */
    private void swish() {
        if (!PlayerIntro.swishOn(getContext())) return;
        int[] at = new int[2];
        getLocationOnScreen(at);
        int sw = getResources().getDisplayMetrics().widthPixels;
        float pan = sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
        com.gathin.broadcast.Sfx.play(getContext(), com.gathin.broadcast.Sfx.SWISH, pan, 0.7f);
    }

    private void show(int m) {
        // Showing it, or on its way to (up under the board first): nothing more to do.
        if (want == m && getVisibility() == VISIBLE) return;
        if (mode != NONE && mode != m && out > 0f) {
            // Another thing to show: up under the board, then down with it.
            want = m;
            to(0f, () -> {
                mode = m;
                swish();
                to(1f, null);
            });
            return;
        }
        boolean from = getVisibility() != VISIBLE || out <= 0f;
        want = m;
        mode = m;
        setVisibility(VISIBLE);
        if (from) swish();
        to(1f, null);
    }

    private void to(float target, Runnable then) {
        if (sliding != null) sliding.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(out, target);
        va.setDuration(Math.max(1, Math.round(SLIDE_MS * Math.abs(target - out))));
        va.setInterpolator(new DecelerateInterpolator(2f));
        va.addUpdateListener(an -> {
            out = (float) an.getAnimatedValue();
            invalidate();
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(android.animation.Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!cancelled && then != null) then.run();
            }
        });
        sliding = va;
        va.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sliding != null) sliding.cancel();
        if (bar != null) bar.cancel();
        if (pop != null) pop.cancel();
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || out <= 0f || mode == NONE) return;
        cv.save();
        cv.clipRect(0, 0, w, h);
        // Down from under the board: its foot first.
        cv.translate(0, -(1f - out) * h);
        float rad = look.apart ? Math.min(h / 2f, unit * 0.5f) : Math.max(3f, Math.min(h * 0.45f, unit * look.round));
        // Square where it tucks under the board; round where it stands out past it, and all round under a pill.
        float topLeft = look.apart ? rad : 0f, topRight = look.apart || w > boardW + 1 ? rad : 0f;
        corners[0] = corners[1] = topLeft;
        corners[2] = corners[3] = topRight;
        corners[4] = corners[5] = corners[6] = corners[7] = rad;
        r.set(0, 0, w, h);
        shape.reset();
        shape.addRoundRect(r, corners, Path.Direction.CW);
        fill.setColor(look.ground);
        cv.drawPath(shape, fill);
        cv.clipPath(shape);
        if (mode == CHANCE) chanceDraw(cv, w, h);
        else pensDraw(cv, w, h);
        cv.restore();
    }

    private void chanceDraw(Canvas cv, int w, int h) {
        float pad = unit * 0.24f, ts = unit * 0.26f, barH = Math.max(3f, unit * 0.1f);
        float textMid = h * 0.38f, barY = h * 0.7f;
        text.setTextSize(ts);
        Paint.FontMetrics fm = text.getFontMetrics();
        float base = textMid - (fm.ascent + fm.descent) / 2f;
        // The three percentages: each side's at its end, the draw's in the middle.
        text.setColor(look.ink);
        text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(homeCode + " " + Math.round(shown[0]) + "%", pad, base, text);
        text.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(Math.round(shown[2]) + "% " + awayCode, w - pad, base, text);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(look.dim);
        text.setTextSize(ts * 0.88f);
        cv.drawText("DRAW " + Math.round(shown[1]) + "%", w / 2f, base, text);
        // The bar: home | draw | away, in the clubs' colours and a quiet middle.
        float bx = pad, bw = w - 2 * pad, all = Math.max(1f, shown[0] + shown[1] + shown[2]);
        float hw = bw * shown[0] / all, dw = bw * shown[1] / all;
        float br = barH / 2f;
        cv.save();
        r.set(bx, barY - barH / 2f, bx + bw, barY + barH / 2f);
        mark.reset();
        mark.addRoundRect(r, br, br, Path.Direction.CW);
        cv.clipPath(mark);
        fill.setColor(homeColour);
        cv.drawRect(bx, barY - barH / 2f, bx + hw, barY + barH / 2f, fill);
        fill.setColor(look.dim);
        fill.setAlpha(Math.round(0.55f * ((look.dim >>> 24))));
        cv.drawRect(bx + hw, barY - barH / 2f, bx + hw + dw, barY + barH / 2f, fill);
        fill.setAlpha(255);
        fill.setColor(awayColour);
        cv.drawRect(bx + hw + dw, barY - barH / 2f, bx + bw, barY + barH / 2f, fill);
        cv.restore();
    }

    private void pensDraw(Canvas cv, int w, int h) {
        float pad = unit * 0.18f, rowH = h / 2f;
        // PENS, small, standing up along its left.
        text.setTextSize(unit * 0.2f);
        text.setColor(look.dim);
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(0.12f);
        cv.save();
        float lx = pad + unit * 0.17f;
        cv.rotate(-90f, lx, h / 2f);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText("PENS", lx, h / 2f - (fm.ascent + fm.descent) / 2f, text);
        cv.restore();
        text.setLetterSpacing(0f);
        float x0 = pad + unit * 0.34f + unit * 0.1f;
        for (int side = 0; side < 2; side++) {
            float y = side * rowH, cy = y + rowH / 2f, x = x0;
            Bitmap crest = side == 0 ? homeCrest : awayCrest;
            float cs = Math.min(rowH * 0.78f, unit * 0.36f);
            if (crest != null && crest.getWidth() > 0) {
                float k = cs / Math.max(crest.getWidth(), crest.getHeight());
                float bw = crest.getWidth() * k, bh = crest.getHeight() * k;
                r.set(x + (cs - bw) / 2f, cy - bh / 2f, x + (cs + bw) / 2f, cy + bh / 2f);
                cv.drawBitmap(crest, null, r, image);
            }
            x += cs + unit * 0.12f;
            text.setTextSize(Math.min(rowH * 0.62f, unit * 0.3f));
            text.setColor(look.ink);
            text.setTextAlign(Paint.Align.LEFT);
            fm = text.getFontMetrics();
            cv.drawText(side == 0 ? homeCode : awayCode, x, cy - (fm.ascent + fm.descent) / 2f, text);
            x += unit * 0.95f + unit * 0.08f;
            int[] row = dots[side];
            float d = Math.min(rowH * 0.66f, unit * 0.28f), step = unit * 0.36f;
            for (int i = 0; i < row.length; i++) {
                float dx = x + i * step + d / 2f;
                boolean landing = side == popSide && i == popIndex && !PenMark.done(popScored, popMs) && row[i] != Shootout.TO_COME;
                if (!landing) dot(cv, dx, cy, d / 2f, row[i]);
                else if (popMs < 0) dot(cv, dx, cy, d / 2f, Shootout.TO_COME);
                else if (popScored) landScored(cv, dx, cy, d / 2f, popMs);
                else landMissed(cv, dx, cy, d / 2f, popMs);
            }
            x += row.length * step + unit * 0.14f;
            text.setTextSize(Math.min(rowH * 0.7f, unit * 0.34f));
            text.setColor(look.ink);
            text.setTextAlign(Paint.Align.RIGHT);
            fm = text.getFontMetrics();
            cv.drawText(String.valueOf(side == 0 ? homeScored : awayScored), Math.min(w - pad, x + unit * 0.36f), cy - (fm.ascent + fm.descent) / 2f, text);
        }
    }

    /** A scored kick's mark ms into landing: popping in past its size, the green sweeping round, the tick, a glow ring. */
    private void landScored(Canvas cv, float cx, float cy, float rad, long ms) {
        float r0 = rad * PenMark.scale(ms);
        if (r0 <= 0.5f) {
            dot(cv, cx, cy, rad, Shootout.TO_COME);
            return;
        }
        // The ring it fills, then the green sweeping round it from the top.
        dot(cv, cx, cy, r0, Shootout.TO_COME);
        float sweep = PenMark.sweep(ms);
        fill.setColor(SCORED_GREEN);
        if (sweep >= 1f) {
            cv.drawCircle(cx, cy, r0, fill);
        } else if (sweep > 0f) {
            r.set(cx - r0, cy - r0, cx + r0, cy + r0);
            cv.drawArc(r, -90f, 360f * sweep, true, fill);
        }
        float t = PenMark.tick(ms);
        if (t > 0f) mark(cv, cx, cy, r0, true, 1f, Math.round(255 * t));
        // The glow ring opening out and fading.
        float[] g = PenMark.glow(ms);
        if (g[1] > 0f) {
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Math.max(1.5f, rad * 0.18f));
            fill.setColor(SCORED_GREEN);
            fill.setAlpha(Math.round(255 * g[1]));
            cv.drawCircle(cx, cy, rad * g[0], fill);
            fill.setAlpha(255);
            fill.setStyle(Paint.Style.FILL);
        }
    }

    /** A missed kick's mark ms into landing: in a little smaller, the cross drawn stroke by stroke, a small shake. */
    private void landMissed(Canvas cv, float cx, float cy, float rad, long ms) {
        float x = cx + PenMark.shake(ms) * rad * 0.28f, r0 = rad * PenMark.missScale(ms);
        fill.setColor(MISSED_RED);
        cv.drawCircle(x, cy, r0, fill);
        float c = PenMark.cross(ms);
        if (c > 0f) mark(cv, x, cy, r0, false, c, 255);
    }

    /** One kick's dot: scored green with a tick, missed red with a cross, to come an empty ring. */
    private void dot(Canvas cv, float cx, float cy, float rad, int state) {
        if (rad <= 0.5f) return;
        if (state == Shootout.TO_COME) {
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Math.max(1.5f, rad * 0.22f));
            fill.setColor(look.dim);
            cv.drawCircle(cx, cy, rad - fill.getStrokeWidth() / 2f, fill);
            fill.setStyle(Paint.Style.FILL);
            return;
        }
        boolean in = state == Shootout.SCORED;
        fill.setColor(in ? SCORED_GREEN : MISSED_RED);
        cv.drawCircle(cx, cy, rad, fill);
        mark(cv, cx, cy, rad, in, 1f, 255);
    }

    /** The white tick (scored) or cross (missed) on a mark, its alpha a; the cross drawn this much (its first stroke, then its second). */
    private void mark(Canvas cv, float cx, float cy, float rad, boolean tick, float drawn, int a) {
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(1.5f, rad * 0.26f));
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(a);
        mark.reset();
        float k = rad * 0.45f;
        if (tick) {
            mark.moveTo(cx - k, cy + k * 0.05f);
            mark.lineTo(cx - k * 0.25f, cy + k * 0.7f);
            mark.lineTo(cx + k, cy - k * 0.6f);
        } else {
            float one = Math.min(1f, drawn * 2f), two = Math.max(0f, drawn * 2f - 1f), e = k * 0.8f;
            mark.moveTo(cx - e, cy - e);
            mark.lineTo(cx - e + 2 * e * one, cy - e + 2 * e * one);
            if (two > 0f) {
                mark.moveTo(cx + e, cy - e);
                mark.lineTo(cx + e - 2 * e * two, cy - e + 2 * e * two);
            }
        }
        cv.drawPath(mark, fill);
        fill.setAlpha(255);
        fill.setStyle(Paint.Style.FILL);
    }
}
