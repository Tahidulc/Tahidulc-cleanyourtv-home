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
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An alert folding down out from under the scoreboard, as broadcasters' captions come out of their score bug, in the
 * board's own look (OverlayBug.StripLook): as wide as the board, on its ground in its words, square where it meets the
 * board and round at its foot (under a pill board, a pill of its own a little apart); the kind's chip, the title and
 * its line, and down its edge the colours of the clubs it is about (the scorers' alone for a goal, the club's for a red
 * card). The line-ups: each club's eleven, led by its name and shape in its colour, its colour down the edge beside it.
 * It folds back up under the board when it is done.
 */
final class BoardDrop extends LinearLayout {
    private static final long FOLD_MS = 340L;
    private final TextView chip, title, line, line2;
    private final GradientDrawable chipGround = new GradientDrawable();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ground = new Path();
    private final RectF r = new RectF();
    /** Its corners, each {x, y}: top left, top right, bottom right, bottom left. */
    private final float[] corners = new float[8];
    private final float dp;
    private OverlayBug.StripLook look = new OverlayBug.StripLook();
    private ScoreBoard.Drop drop;
    private int chipColour;
    /** Under the board (square where it meets it), and no wider than it (square on the right too). */
    private boolean under, flush;
    /** 0 folded up under the board, 1 all the way down. */
    private float down;
    private ValueAnimator folding;

    BoardDrop(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        // Room down the left for the clubs' colours.
        setPadding(Math.round(14 * dp), Math.round(8 * dp), Math.round(12 * dp), Math.round(9 * dp));
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        chip = Ui.text(c, "", 10, 0xFF0A0D33, true);
        chip.setLetterSpacing(0.08f);
        chip.setSingleLine(true);
        chip.setPadding(Math.round(6 * dp), Math.round(2 * dp), Math.round(6 * dp), Math.round(2 * dp));
        chip.setBackground(chipGround);
        head.addView(chip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        title = Ui.text(c, "", 14, 0xFFFFFFFF, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams tp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Math.round(8 * dp);
        head.addView(title, tp);
        addView(head, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        line = words(c);
        line2 = words(c);
        setVisibility(GONE);
    }

    private TextView words(Context c) {
        TextView t = Ui.text(c, "", 12, 0xFFC9CFEA, false);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, Math.round(3 * dp), 0, 0);
        addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return t;
    }

    /** Down with this alert in the board's look: under the board (flush: no wider than it), or on its own. */
    void show(ScoreBoard.Drop d, OverlayBug.StripLook s, boolean underBoard, boolean flushRight) {
        set(d, s, underBoard, flushRight);
        if (getVisibility() != VISIBLE) down = 0f;
        setVisibility(VISIBLE);
        fold(1f, null);
    }

    /** Back up under the board; then() once it is away (at once if it is). */
    void fold(Runnable then) {
        if (getVisibility() != VISIBLE) {
            if (then != null) then.run();
            return;
        }
        fold(0f, () -> {
            setVisibility(GONE);
            drop = null;
            if (then != null) then.run();
        });
    }

    /** Away at once (the player going, the small window). */
    void gone() {
        if (folding != null) folding.cancel();
        down = 0f;
        drop = null;
        setVisibility(GONE);
    }

    /** The board went from above it: round all round now. */
    void alone() {
        if (!under) return;
        under = false;
        invalidate();
    }

    boolean isOut() {
        return getVisibility() == VISIBLE;
    }

    /** The alert it shows; null when none. */
    ScoreBoard.Drop drop() {
        return drop;
    }

    /** Previews and tests: shown at once, this far down. */
    void showNow(ScoreBoard.Drop d, OverlayBug.StripLook s, boolean underBoard, boolean flushRight, float howFar) {
        if (folding != null) folding.cancel();
        set(d, s, underBoard, flushRight);
        down = howFar;
        setVisibility(VISIBLE);
        invalidate();
    }

    private void set(ScoreBoard.Drop d, OverlayBug.StripLook s, boolean underBoard, boolean flushRight) {
        drop = d;
        look = s;
        under = underBoard;
        flush = flushRight;
        // The kind's own colour (a goal's green, a red card's red, breaking news' yellow), else the board's block.
        chipColour = d.colour == ScoreBoard.PLAIN ? s.block : d.colour;
        chipGround.setColor(chipColour);
        chipGround.setCornerRadius(s.apart ? 50 * dp : Math.max(2 * dp, 16 * dp * s.round));
        chip.setTextColor((chipColour >>> 24) < 0xC0 ? s.ink : OverlayBug.ink(chipColour));
        chip.setText(d.chip);
        chip.setVisibility(d.chip.isEmpty() ? GONE : VISIBLE);
        title.setTextColor(s.ink);
        title.setText(d.title);
        int most = d.lineups ? 3 : 2;
        for (TextView t : new TextView[]{line, line2}) {
            t.setTextColor(s.dim);
            t.setMaxLines(most);
        }
        line.setText(d.lineups ? team(d.line, d.homeColour, s.light) : d.line);
        line2.setText(d.lineups ? team(d.line2, d.awayColour, s.light) : d.line2);
        line.setVisibility(d.line.isEmpty() ? GONE : VISIBLE);
        line2.setVisibility(d.line2.isEmpty() ? GONE : VISIBLE);
        setContentDescription(d.chip + ". " + d.title + (d.line.isEmpty() ? "" : ". " + d.line) + (d.line2.isEmpty() ? "" : ". " + d.line2));
        requestLayout();
        invalidate();
    }

    /** A club's eleven, "Leeds (4-3-3): Meslier, …": its name and shape in its colour, bold. */
    private static CharSequence team(String s, int colour, boolean light) {
        int colon = s.indexOf(": ");
        if (colon <= 0) return s;
        SpannableString out = new SpannableString(s);
        out.setSpan(new ForegroundColorSpan(legible(colour == 0 ? 0xFFFFFFFF : colour, light)), 0, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new StyleSpan(Typeface.BOLD), 0, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    /** Pure: a club's colour as words on the strip: one too dark for a dark ground lightened, too light for a light one darkened. */
    static int legible(int c, boolean light) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF, lum = (r * 299 + g * 587 + b * 114) / 1000;
        if (!light && lum < 90) {
            r += (255 - r) * 11 / 20;
            g += (255 - g) * 11 / 20;
            b += (255 - b) * 11 / 20;
        } else if (light && lum > 150) {
            r = r * 2 / 5 + 0x0B * 3 / 5;
            g = g * 2 / 5 + 0x16 * 3 / 5;
            b = b * 2 / 5 + 0x40 * 3 / 5;
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
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
        if (folding != null) folding.cancel();
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    @Override protected void dispatchDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || down <= 0f || drop == null) return;
        float e = 1f - (1f - down) * (1f - down) * (1f - down);
        float seen = h * e;
        cv.save();
        cv.clipRect(0, 0, w, seen);
        // Down out from under the board: its foot first.
        cv.translate(0, seen - h);
        // The board's corners (a 30 dp board's), square where it meets the board; under a pill, round all round.
        float rad = look.apart ? Math.min(h / 2f, 16 * dp) : Math.min(8 * dp, Math.max(3 * dp, 30 * dp * look.round));
        boolean square = under && !look.apart;
        corners[0] = corners[1] = square ? 0 : rad;
        corners[2] = corners[3] = square && flush ? 0 : rad;
        for (int i = 4; i < 8; i++) corners[i] = rad;
        ground.reset();
        r.set(0, 0, w, h);
        ground.addRoundRect(r, corners, Path.Direction.CW);
        fill.setColor(look.ground);
        cv.drawPath(ground, fill);
        cv.save();
        cv.clipPath(ground);
        if (square) {
            fill.setColor(look.light ? 0x220B1640 : 0x26FFFFFF);
            cv.drawRect(0, 0, w, Math.max(1, dp), fill);
        }
        // Down its edge, as the board's strips: the clubs it is about, else the kind's colour.
        float sw = 4 * dp;
        if (drop.homeColour == 0 && drop.awayColour == 0) {
            fill.setColor(chipColour);
            cv.drawRect(0, 0, sw, h, fill);
        } else if (drop.side == 1 || drop.side == 2) {
            fill.setColor(drop.side == 1 ? drop.homeColour : drop.awayColour);
            cv.drawRect(0, 0, sw, h, fill);
        } else {
            // Both clubs: the home side's colour over the away side's (the line-ups: each beside its eleven).
            float split = drop.lineups && line2.getVisibility() == VISIBLE ? line2.getTop() : h / 2f;
            fill.setColor(drop.homeColour);
            cv.drawRect(0, 0, sw, split, fill);
            fill.setColor(drop.awayColour);
            cv.drawRect(0, split, sw, h, fill);
        }
        super.dispatchDraw(cv);
        cv.restore();
        cv.restore();
    }
}
