package com.gathin.tvhomephone.live;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The game's stats folding down out from under the scoreboard, as broadcasters show them: a strip as wide as the board,
 * one stat at a time (its name in the middle, each side's number at its end, and a bar under them split in the clubs'
 * colours by their share), each for a few seconds, the next sliding up into its place, then the strip folding back up
 * under the board. In the board's own look (OverlayBug.StripLook): its ground and words, and under a pill board a pill
 * of its own, a little apart.
 */
final class BoardStats extends View {
    /** How long each stat shows. */
    static final long EACH_MS = 4000L;
    private static final long FOLD_MS = 320L, TURN_MS = 300L;

    private final List<String[]> items = new ArrayList<>();
    private int at;
    /** 0 folded up under the board, 1 all the way down; the page turning (0 the last stat, 1 this one). */
    private float down, turn = 1f;
    private int homeColour = 0xFFE5333B, awayColour = 0xFF4F8BFF;
    private OverlayBug.StripLook look = new OverlayBug.StripLook();
    private ValueAnimator folding, turning;
    private Runnable after;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path ground = new Path();
    /** Its corners: square at the top (under the board), round at the foot. */
    private final float[] corners = new float[8];
    private final float dp;
    private final Runnable nextOne = this::next;

    BoardStats(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setFontFeatureSettings("tnum");
        setVisibility(GONE);
    }

    /** The board's look, and the clubs' colours for the bar (a very dark one lightened, to show on a dark ground). */
    void look(OverlayBug.StripLook s, int home, int away) {
        int h = s.light ? home : visible(home), a = s.light ? away : visible(away);
        if (h == homeColour && a == awayColour && s.ground == look.ground && s.ink == look.ink && s.apart == look.apart) return;
        look = s;
        homeColour = h;
        awayColour = a;
        if (isOut()) invalidate();
    }

    /** Pure: a colour too dark for the navy strip mixed a third of the way to white; any other as it is. */
    static int visible(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if ((r * 299 + g * 587 + b * 114) / 1000 >= 80) return c;
        r += (255 - r) * 2 / 5;
        g += (255 - g) * 2 / 5;
        b += (255 - b) * 2 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Whether it is down (or on its way down). */
    boolean isOut() {
        return getVisibility() == VISIBLE;
    }

    /** Down with these {name, home, away}, one after another, then back up; after() once it is away. */
    void show(List<String[]> list, Runnable then) {
        if (list == null || list.isEmpty()) return;
        removeCallbacks(nextOne);
        items.clear();
        items.addAll(list);
        at = 0;
        turn = 1f;
        after = then;
        setVisibility(VISIBLE);
        fold(1f, null);
        postDelayed(nextOne, FOLD_MS + EACH_MS);
    }

    /** Back up under the board now (the board going); then() once it is away, at once if it is. */
    void fold(Runnable then) {
        if (!isOut()) {
            removeCallbacks(nextOne);
            if (then != null) then.run();
            return;
        }
        Runnable was = after;
        after = then == null ? was : was == null ? then : () -> {
            was.run();
            then.run();
        };
        fold();
    }

    /** Back up under the board now (an alert dropping, the board going). */
    void fold() {
        removeCallbacks(nextOne);
        if (!isOut()) return;
        fold(0f, () -> {
            setVisibility(GONE);
            Runnable a = after;
            after = null;
            if (a != null) a.run();
        });
    }

    /** Previews and tests: shown at once, this far down, on this stat. */
    void showNow(List<String[]> list, int index, float howFar) {
        removeCallbacks(nextOne);
        items.clear();
        items.addAll(list);
        at = Math.max(0, Math.min(index, items.size() - 1));
        turn = 1f;
        down = howFar;
        setVisibility(VISIBLE);
        invalidate();
    }

    private void next() {
        if (!isOut()) return;
        if (at + 1 >= items.size()) {
            fold();
            return;
        }
        at++;
        if (turning != null) turning.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(TURN_MS);
        va.addUpdateListener(a -> {
            turn = (float) a.getAnimatedValue();
            invalidate();
        });
        turning = va;
        va.start();
        postDelayed(nextOne, TURN_MS + EACH_MS);
    }

    private void fold(float target, Runnable then) {
        if (folding != null) folding.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(down, target);
        va.setDuration(Math.max(1, Math.round(FOLD_MS * Math.abs(target - down))));
        va.addUpdateListener(a -> {
            down = (float) a.getAnimatedValue();
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
        folding = va;
        va.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(nextOne);
        if (folding != null) folding.cancel();
        if (turning != null) turning.cancel();
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    /** Pure: the home side's share of a stat, from its two numbers ("58%" and "42%", "7" and "3"); a half when neither has any. */
    static float share(String home, String away) {
        float h = number(home), a = number(away);
        return h + a <= 0 ? 0.5f : h / (h + a);
    }

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?");

    private static float number(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = NUMBER.matcher(s);
        return m.find() ? Float.parseFloat(m.group()) : 0;
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || down <= 0f || items.isEmpty()) return;
        float e = 1f - (1f - down) * (1f - down) * (1f - down);
        float seen = h * e;
        cv.save();
        cv.clipRect(0, 0, w, seen);
        // Down out from under the board: its foot first.
        cv.translate(0, seen - h);
        // Square where it meets the board, round at its foot; under a pill, a pill of its own.
        float rad = look.apart ? h / 2f : Math.min(8 * dp, h * 0.3f);
        ground.reset();
        r.set(0, 0, w, h);
        for (int i = 0; i < 8; i++) corners[i] = i < 4 && !look.apart ? 0 : rad;
        ground.addRoundRect(r, corners, Path.Direction.CW);
        fill.setColor(look.ground);
        cv.drawPath(ground, fill);
        if (!look.apart) {
            fill.setColor(look.light ? 0x220B1640 : 0x26FFFFFF);
            cv.drawRect(0, 0, w, Math.max(1, dp), fill);
        }
        cv.save();
        cv.clipPath(ground);
        if (turn < 1f && at > 0) item(cv, items.get(at - 1), w, h, -h * 0.55f * turn, 1f - turn);
        item(cv, items.get(at), w, h, h * 0.55f * (1f - turn), turn);
        cv.restore();
        cv.restore();
    }

    /** One stat at height offset dy, faded to alpha: its name, each side's number at its end, the bar of shares. */
    private void item(Canvas cv, String[] s, int w, int h, float dy, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        int ink = look.ink, dim = look.dim;
        float side = Math.max(look.apart ? h * 0.6f : 10 * dp, w * 0.05f);
        // The name, small and spaced, in the middle.
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(0.1f);
        text.setTextSize(h * 0.24f);
        text.setColor(dim);
        text.setAlpha(a);
        cv.drawText(s[0].toUpperCase(Locale.UK), w / 2f, dy + h * 0.38f, text);
        // Each side's number at its end.
        text.setLetterSpacing(0);
        text.setTextSize(h * 0.42f);
        text.setColor(ink);
        text.setAlpha(a);
        Paint.FontMetrics fm = text.getFontMetrics();
        float base = dy + h * 0.46f - (fm.ascent + fm.descent) / 2f;
        text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(s[1], side, base, text);
        text.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(s[2], w - side, base, text);
        // The bar: each club's share in its colour, a hair apart.
        float left = w * 0.28f, right = w * 0.72f, top = dy + h * 0.66f, bottom = top + Math.max(3 * dp, h * 0.11f);
        float split = left + (right - left) * share(s[1], s[2]), gap = Math.max(1f, dp);
        float br = (bottom - top) / 2f;
        fill.setColor(homeColour);
        fill.setAlpha(a);
        r.set(left, top, Math.max(left + br * 2, split - gap), bottom);
        cv.drawRoundRect(r, br, br, fill);
        fill.setColor(awayColour);
        fill.setAlpha(a);
        r.set(Math.min(right - br * 2, split + gap), top, right, bottom);
        cv.drawRoundRect(r, br, br, fill);
        fill.setAlpha(255);
    }
}
