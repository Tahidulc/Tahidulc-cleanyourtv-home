package com.gathin.tvhome;

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
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An alert folding down out from under the scoreboard over the channel (BoardCover), as the phone's player drops its
 * alerts and broadcasters' captions come out of their score bug, in the board's own look (BoardBug's StripLook): as
 * wide as the board at least, on its ground in its words, square where it meets the board and round at its foot (under
 * the Glass pill, a pill of its own a little apart); the kind's chip, the title and its line, and down its edge the
 * colours of the clubs it is about (the scorers' alone for a goal, the club's for a red card). The line-ups: each
 * club's eleven, led by its name and shape in its colour, that colour down the edge beside it. It folds back up under
 * the board when it is done. Sized from the board's height, so it reads from the sofa at any size the board is.
 */
final class BoardDrop extends LinearLayout {
    /** One alert as it drops: its chip, title, line (and the away side's eleven), and the game's clubs it is about. */
    static final class Drop {
        String chip = "", title = "", line = "", line2 = "";
        /** The kind's colour (PLAIN: none of its own, the board's block). */
        int colour = PLAIN;
        /** The game's clubs' colours (0 when it is no game's), and the side it is about: 1 home, 2 away, 0 both or neither. */
        int homeColour, awayColour, side;
        /** Both starting elevens, the home side's in line and the away side's in line2, each led by its club and shape. */
        boolean lineups;
        /** The game it is about (null: none), what Today's alerts and the log call it. */
        String gameId, what = "";
    }

    /** The colour of the kinds with none of their own (half time, kick-off, the line-ups): the board's block instead. */
    static final int PLAIN = 0xFFDDE3FF;
    private static final long FOLD_MS = 340L;

    private final TextView chip, title, line, line2;
    private final LinearLayout head;
    private final GradientDrawable chipGround = new GradientDrawable();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ground = new Path();
    private final RectF r = new RectF();
    /** Its corners, each {x, y}: top left, top right, bottom right, bottom left. */
    private final float[] corners = new float[8];
    private BoardBug.StripLook look = new BoardBug.StripLook();
    private Drop drop;
    private int chipColour;
    /** The board's height in pixels: what its words and room are sized from. */
    private float unit = 48f;
    /** Under the board (square where it meets it), and no wider than it (square on the right too). */
    private boolean under, flush;
    /** 0 folded up under the board, 1 all the way down. */
    private float down;
    private ValueAnimator folding;

    BoardDrop(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        chip = words(c, ScoreCard.outfit(c, 800), 1);
        chip.setLetterSpacing(0.08f);
        chip.setBackground(chipGround);
        head.addView(chip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        title = words(c, ScoreCard.outfit(c, 700), 2);
        head.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(head, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        line = words(c, ScoreCard.outfit(c, 500), 2);
        addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        line2 = words(c, ScoreCard.outfit(c, 500), 2);
        addView(line2, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setVisibility(GONE);
    }

    private static TextView words(Context c, Typeface face, int lines) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (lines == 1) t.setSingleLine(true);
        return t;
    }

    /** Down with this alert in the board's look, sized for a board unit pixels high: under it (flush: no wider), or alone. */
    void show(Drop d, BoardBug.StripLook s, float unit, boolean underBoard, boolean flushRight) {
        set(d, s, unit, underBoard, flushRight);
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

    /** Away at once (the board's window going). */
    void gone() {
        if (folding != null) folding.cancel();
        down = 0f;
        drop = null;
        setVisibility(GONE);
    }

    boolean isOut() {
        return getVisibility() == VISIBLE;
    }

    /** Previews: shown at once, this far down. */
    void showNow(Drop d, BoardBug.StripLook s, float unit, boolean underBoard, boolean flushRight, float howFar) {
        if (folding != null) folding.cancel();
        set(d, s, unit, underBoard, flushRight);
        down = howFar;
        setVisibility(VISIBLE);
        invalidate();
    }

    private void set(Drop d, BoardBug.StripLook s, float u, boolean underBoard, boolean flushRight) {
        drop = d;
        look = s;
        unit = Math.max(16f, u);
        under = underBoard;
        flush = flushRight;
        // Room down the left for the clubs' colours; the words sized from the board's height.
        setPadding(Math.round(unit * 0.38f), Math.round(unit * 0.2f), Math.round(unit * 0.3f), Math.round(unit * 0.24f));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.3f);
        chip.setPadding(Math.round(unit * 0.14f), Math.round(unit * 0.06f), Math.round(unit * 0.14f), Math.round(unit * 0.05f));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.44f);
        ((LayoutParams) title.getLayoutParams()).leftMargin = Math.round(unit * 0.2f);
        // The kind's own colour (a goal's green, a red card's red), else the board's block.
        chipColour = d.colour == PLAIN ? s.block : d.colour;
        chipGround.setColor(chipColour);
        chipGround.setCornerRadius(s.apart ? unit : Math.max(2f, unit * 0.34f * s.round * 2f));
        chip.setTextColor((chipColour >>> 24) < 0xC0 ? s.ink : BoardBug.ink(chipColour));
        chip.setText(d.chip);
        chip.setVisibility(d.chip.isEmpty() ? GONE : VISIBLE);
        title.setTextColor(s.ink);
        title.setText(d.title);
        int most = d.lineups ? 3 : 2;
        for (TextView t : new TextView[]{line, line2}) {
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.36f);
            t.setTextColor(s.dim);
            t.setMaxLines(most);
            t.setPadding(0, Math.round(unit * 0.1f), 0, 0);
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
        out.setSpan(new ForegroundColorSpan(legible(colour == 0 ? BoardBug.INK : colour, light)), 0, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        // The board's corners, square where it meets the board; under a pill, round all round.
        float rad = look.apart ? Math.min(h / 2f, unit * 0.5f) : Math.max(3f, unit * look.round);
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
            cv.drawRect(0, 0, w, Math.max(1f, unit * 0.03f), fill);
        }
        // Down its edge, as the board's strips: the clubs it is about, else the kind's colour.
        float sw = Math.max(3f, unit * 0.09f);
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
