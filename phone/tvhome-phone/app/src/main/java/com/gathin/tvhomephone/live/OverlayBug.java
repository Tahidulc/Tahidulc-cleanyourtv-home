package com.gathin.tvhomephone.live;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import com.gathin.tvhomephone.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The scoreboard over the picture, drawn to fill whatever box it is given: on its own, a broadcast strip 30 dp high;
 * over the channel's own scoreboard, that box's size exactly. Wide boxes take one row (the competition, each club's
 * colour, crest and three letters, the score, the clock in minutes and seconds); boxes nearer square take two (the
 * clubs and score over the clock). F1: F1's red block, the session, the lap or the time left, and the first three with
 * their teams' colours. A goal lights the scoring club's side in its colour for a few seconds.
 */
final class OverlayBug extends View implements BurnGuard.Dims {
    /** What it shows. */
    static final class Look {
        boolean f1, live;
        String home = "", away = "", score = "", clock = "", added = "", tag = "";
        int homeColour = 0xFFFFFFFF, awayColour = 0xFFFFFFFF;
        /** F1: the session, the corner ({"LAP", "23"}, {"Q2", "08:31"}), the first drivers' codes and colours. */
        String label = "", cornerA = "", cornerB = "";
        final List<String> codes = new ArrayList<>();
        final List<Integer> colours = new ArrayList<>();

        @Override public boolean equals(Object o) {
            if (!(o instanceof Look)) return false;
            Look l = (Look) o;
            return f1 == l.f1 && live == l.live && home.equals(l.home) && away.equals(l.away) && score.equals(l.score)
                    && clock.equals(l.clock) && added.equals(l.added) && tag.equals(l.tag) && homeColour == l.homeColour
                    && awayColour == l.awayColour && label.equals(l.label) && cornerA.equals(l.cornerA)
                    && cornerB.equals(l.cornerB) && codes.equals(l.codes) && colours.equals(l.colours);
        }

        @Override public int hashCode() {
            return Objects.hash(f1, home, away, score, clock, label, cornerB);
        }
    }

    private static final int NAVY = 0xFF0B1640, NAVY_DEEP = 0xFF070C26, CLOCK = 0xFF050816, ADDED = 0xFFE0213A,
            AMBER = 0xFFFFB547, F1_RED = 0xFFE10600, DIM = 0xFFB9C1E6;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final int blueTop, blueBottom;
    private final float dp;
    private Look look = new Look();
    private Style style = Style.CLASSIC;
    private Palette p;
    Bitmap homeCrest, awayCrest, comp;
    /** The goal's light: 1 to 0 over its seconds, on side 1 (home) or 2 (away). */
    private float flash;
    private int flashSide;
    private long flashUntil;

    OverlayBug(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        blueTop = c.getColor(R.color.bug_blue_top);
        blueBottom = c.getColor(R.color.bug_blue_bottom);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setFontFeatureSettings("tnum");
        setLayerType(LAYER_TYPE_HARDWARE, null);
        p = palette(style, blueTop, blueBottom);
    }

    /** How it looks: the choices in Settings and the player's options. */
    enum Style {
        CLASSIC("Classic"), GLASS("Glass"), LIGHT("Light"), CLUB("Club colours"), MINIMAL("Minimal"),
        CRESTS("Crests and time"), STACKED("Stacked", 1.7f);

        final String label;
        /** How much taller than the others it is drawn in its own corner (Stacked: the clubs one above the other). */
        final float tall;

        Style(String label) {
            this(label, 1f);
        }

        Style(String label, float tall) {
            this.label = label;
            this.tall = tall;
        }

        static Style of(String name) {
            for (Style s : values()) if (s.name().equals(name)) return s;
            return CLASSIC;
        }
    }

    /** One look's colours. */
    private static final class Palette {
        int ground, comp, score0, score1, clock, ink, scoreInk, clockInk, dim;
        boolean strips = true, clubFill;
        /** The corners' radius as a share of the height (0.5: a pill). */
        float round = 0.2f;
    }

    private static Palette palette(Style s, int blueTop, int blueBottom) {
        Palette p = new Palette();
        p.ink = p.scoreInk = p.clockInk = 0xFFFFFFFF;
        p.dim = DIM;
        switch (s) {
            case GLASS:
                // See-through smoked glass, a pill, the picture showing faintly behind.
                p.ground = 0xB80B0F24;
                p.comp = 0;
                p.score0 = 0x40FFFFFF;
                p.score1 = 0x24FFFFFF;
                p.clock = 0x38000000;
                p.dim = 0xCCDDE3FF;
                p.round = 0.5f;
                break;
            case LIGHT:
                // The competition's block stays navy: its logo is drawn for dark grounds.
                p.ground = 0xFFF5F6FA;
                p.comp = NAVY;
                p.score0 = p.score1 = NAVY;
                p.clock = 0xFFE4E7F0;
                p.ink = p.clockInk = NAVY;
                p.dim = 0xFF5A6280;
                break;
            case CLUB:
                // Each side in its club's own colour, on black.
                p.ground = 0xFF0E0E10;
                p.comp = 0xFF000000;
                p.score0 = 0xFF1A1A1C;
                p.score1 = 0xFF000000;
                p.clock = 0xFF000000;
                p.dim = 0xFFB0B0B8;
                p.strips = false;
                p.clubFill = true;
                break;
            case MINIMAL:
                p.ground = 0xD9000000;
                p.comp = 0;
                p.score0 = p.score1 = 0;
                p.clock = 0;
                p.clockInk = 0xFFDDE3FF;
                p.round = 0.5f;
                break;
            case CRESTS:
                // The two crests either side of the score on deep navy, the clock in its own dark box.
                p.ground = 0xFF0A1238;
                p.comp = NAVY_DEEP;
                p.score0 = 0xFF1F3AA8;
                p.score1 = 0xFF12246E;
                p.clock = CLOCK;
                p.round = 0.3f;
                break;
            case STACKED:
                // The clubs one above the other, each with its goals in a box of its own.
                p.ground = 0xFF0E1330;
                p.comp = NAVY_DEEP;
                p.score0 = p.score1 = 0xFF1E2A66;
                p.clock = CLOCK;
                p.round = 0.16f;
                break;
            default:
                p.ground = NAVY;
                p.comp = NAVY_DEEP;
                p.score0 = blueTop;
                p.score1 = blueBottom;
                p.clock = CLOCK;
        }
        return p;
    }

    /** Its look changed (Settings, Options). */
    void style(Style s) {
        if (s == style) return;
        style = s;
        p = palette(s, blueTop, blueBottom);
        requestLayout();
        invalidate();
    }

    Style style() {
        return style;
    }

    Look look() {
        return look;
    }

    /** What to show; drawn again only when it changed. */
    void set(Look l) {
        if (l.equals(look)) return;
        int h = getHeight() > 0 ? getHeight() : Math.round(30 * dp);
        int before = natural(h);
        look = l;
        boolean size = natural(h) != before;
        setContentDescription(l.f1 ? "F1 " + l.label + " " + l.cornerA + " " + l.cornerB
                : l.home + " " + l.score + " " + l.away + (l.clock.isEmpty() ? "" : ", " + l.clock + " " + l.added));
        if (size) requestLayout();
        invalidate();
    }

    /** Dimmed against burn-in (BurnGuard): solid, a little darker. */
    @Override public void dim(boolean on) {
        BurnGuard.dimLayer(this, on);
    }

    /** A goal for side 1 (home) or 2 (away): its half lit in its colour for four seconds. */
    void goal(int side) {
        flashSide = side;
        flashUntil = System.currentTimeMillis() + 4000;
        postInvalidateOnAnimation();
    }

    void crests(Bitmap home, Bitmap away, Bitmap competition) {
        int h = getHeight() > 0 ? getHeight() : Math.round(30 * dp);
        int before = natural(h);
        homeCrest = home;
        awayCrest = away;
        comp = competition;
        if (natural(h) != before) requestLayout();
        invalidate();
    }

    // ---- size ------------------------------------------------------------------------------------------------------

    /** Its width on its own at height h. */
    int natural(int h) {
        text.setTextSize(h * 0.46f);
        if (style == Style.MINIMAL) return Math.round(minimalWidth(h));
        if (look.f1) {
            float w = h * 1.3f + label(h) + corner(h) + h * 0.3f;
            for (String c : look.codes) w += h * 0.16f + measure(c, h * 0.44f) + h * 0.5f;
            return Math.round(w);
        }
        if (style == Style.CRESTS) return Math.round(crestsWidth(h));
        if (style == Style.STACKED) return Math.round(stackedWidth(h));
        return Math.round(h + 2 * (h * 0.1f + crestW(h) + code(h)) + scoreW(h) + clockW(h));
    }

    @Override protected void onMeasure(int ws, int hs) {
        int h = MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hs) : Math.round(30 * dp);
        int w = MeasureSpec.getMode(ws) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(ws) : natural(h);
        if (MeasureSpec.getMode(ws) == MeasureSpec.AT_MOST) w = Math.min(w, MeasureSpec.getSize(ws));
        setMeasuredDimension(w, h);
    }

    private float measure(String s, float size) {
        text.setTextSize(size);
        return text.measureText(s == null ? "" : s);
    }

    /** Room for the crests: none until they have loaded. */
    private float crestW(float h) {
        return homeCrest != null || awayCrest != null ? h * 0.86f : 0;
    }

    private float code(int h) {
        return Math.max(measure(look.home, h * 0.46f), measure(look.away, h * 0.46f)) + h * 0.5f;
    }

    private float scoreW(int h) {
        return Math.max(h * 1.4f, measure(look.score, h * 0.56f) + h * 0.6f);
    }

    private float clockW(int h) {
        if (look.clock.isEmpty()) return 0;
        return measure("88:88".length() >= look.clock.length() ? "88:88" : look.clock, h * 0.44f) + h * 0.6f
                + (look.added.isEmpty() ? 0 : measure(look.added, h * 0.34f) + h * 0.45f);
    }

    private float label(int h) {
        return look.label.isEmpty() ? 0 : measure(look.label, h * 0.36f) + h * 0.6f;
    }

    private float corner(int h) {
        return measure(look.cornerA, h * 0.32f) + measure(look.cornerB, h * 0.46f) + h * 0.9f;
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    // ---- coming and going, as a broadcaster's ---------------------------------------------------------------------

    /** How far it is shown: 0 gone, 1 all there. Up to OPEN_AT its first block opens out of a line; then the rest wipes out of it. */
    private float shown = 1f;
    private static final float OPEN_AT = 0.3f;
    private ValueAnimator showing;
    private boolean closing;
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** In as the broadcasters' come in: its first block opens out of a thin line, then the rest wipes out to the right. */
    void open() {
        closing = false;
        to(1f, 720, null);
    }

    /** Out the same way, backwards: the rest wipes back into the first block, which closes to a line; after() once gone. */
    void close(Runnable after) {
        if (closing) return;
        closing = true;
        to(0f, 460, after);
    }

    boolean closing() {
        return closing;
    }

    /** Previews and tests: shown this far at once. */
    void shownAt(float f) {
        if (showing != null) showing.cancel();
        shown = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    private void to(float target, long ms, Runnable after) {
        if (showing != null) showing.cancel();
        float from = shown;
        if (from == target) {
            if (after != null) after.run();
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(from, target);
        va.setDuration(Math.max(1, Math.round(ms * Math.abs(target - from))));
        va.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (cancelled) return;
                if (target <= 0f) closing = false;
                if (after != null) after.run();
            }
        });
        showing = va;
        va.start();
    }

    /** Pure: the part shown at f of the way in, {left, top, right, bottom}, for a board w by h whose first block is cap wide. */
    static float[] shownPart(float f, float w, float h, float cap) {
        if (f >= 1f) return new float[]{0, 0, w, h};
        if (f <= 0f) return new float[]{0, 0, 0, 0};
        if (f < OPEN_AT) {
            float e = ease(f / OPEN_AT), half = h / 2f * e;
            return new float[]{0, h / 2f - half, cap, h / 2f + half};
        }
        float e = ease((f - OPEN_AT) / (1f - OPEN_AT));
        return new float[]{0, 0, cap + (w - cap) * e, h};
    }

    /** Fast out, settling in: a cubic. */
    private static float ease(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || shown <= 0f) return;
        boolean part = shown < 1f;
        float[] in = null;
        if (part) {
            // Its first block (the competition's square, Crests' home crest, F1's red), never more than a third of it.
            float first = look.f1 ? h * 1.3f : style == Style.CRESTS ? h * 1.25f : style == Style.STACKED ? h * 0.9f
                    : style == Style.MINIMAL ? h * 1.2f : h;
            in = shownPart(shown, w, h, Math.min(first, w / 3f));
            cv.save();
            cv.clipRect(in[0], in[1], in[2], in[3]);
        }
        long now = System.currentTimeMillis();
        flash = now < flashUntil ? (flashUntil - now) / 4000f : 0f;
        clip.reset();
        float rad = p.round >= 0.5f ? h / 2f : Math.min(h * p.round, 8 * dp);
        r.set(0, 0, w, h);
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        fill.setShader(null);
        fill.setColor(p.ground);
        cv.drawRect(r, fill);
        if (style == Style.MINIMAL) {
            minimal(cv, w, h);
        } else if (look.f1) {
            if (w / (float) h >= 3.6f) f1Row(cv, 0, 0, w, h); else f1Two(cv, w, h);
        } else if (style == Style.CRESTS) {
            if (w / (float) h >= 3.2f) crestsRow(cv, w, h); else crestsTwo(cv, w, h);
        } else if (style == Style.STACKED) {
            stacked(cv, w, h);
        } else if (w / (float) h >= 3.6f) {
            oneRow(cv, 0, 0, w, h, true);
        } else {
            twoRows(cv, w, h);
        }
        cv.restore();
        if (part) {
            cv.restore();
            // The wipe's leading edge, a bright line fading as it lands.
            if (shown > OPEN_AT && in[2] < w - 1) {
                float lw = Math.max(2f, 2 * dp), a = 1f - (shown - OPEN_AT) / (1f - OPEN_AT);
                edge.setColor(0xFFFFFFFF);
                edge.setAlpha(Math.round(230 * Math.max(0.25f, a)));
                cv.drawRect(in[2] - lw, 0, in[2], h, edge);
            }
        }
        if (flash > 0) postInvalidateOnAnimation();
    }

    /** The clubs, the score and the clock along one row in (x, y, w, h). */
    private void oneRow(Canvas cv, float x, float y, float w, float h, boolean withClock) {
        int hi = Math.round(h);
        boolean crests = true, compBlock = true;
        float codeW = code(hi), strip = h * 0.1f, crestW = crestW(h), score = scoreW(hi), clock = withClock ? clockW(hi) : 0;
        // A pixel's grace: its own width is the sum rounded.
        float room = w + 1.5f;
        float need = h + 2 * (strip + crestW + codeW) + score + clock;
        if (p.round >= 0.5f) need += h * 0.36f;
        if (need > room && crestW > 0) {
            crests = false;
            need -= 2 * crestW;
        }
        if (need > room) {
            compBlock = false;
            need -= h;
        }
        float k = need > room ? Math.max(0.6f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        codeW = codeW * k + extra / 2;
        strip *= k;
        score *= k;
        clock *= k;
        float cx = x;
        if (p.round >= 0.5f) cx += h * 0.18f;
        if (compBlock) {
            if (p.comp != 0) {
                fill.setColor(p.comp);
                cv.drawRect(cx, y, cx + h, y + h, fill);
            }
            if (comp != null) drawImage(cv, comp, cx + h * 0.16f, y + h * 0.16f, h * 0.68f);
            else words(cv, look.tag, cx + h / 2, y, h, h * 0.3f, p.dim);
            cx += h;
        }
        cx = side(cv, cx, y, h, strip, crests ? crestW : 0, codeW, k, true);
        // The score on its own block.
        scoreBlock(cv, cx, y, score, h);
        words(cv, look.score, cx + score / 2, y, h, h * 0.56f * k, p.scoreInk);
        cx += score;
        cx = side(cv, cx, y, h, strip, crests ? crestW : 0, codeW, k, false);
        if (withClock && clock > 0) clock(cv, cx, y, x + w - cx, h, k);
    }

    private void scoreBlock(Canvas cv, float x, float y, float w, float h) {
        if (p.score0 == 0 && p.score1 == 0) return;
        fill.setShader(new LinearGradient(0, y, 0, y + h, p.score0, p.score1, Shader.TileMode.CLAMP));
        cv.drawRect(x, y, x + w, y + h, fill);
        fill.setShader(null);
    }

    /** One club's part: (home) strip, crest, code; (away) code, crest, strip. Returns where it ends. */
    private float side(Canvas cv, float cx, float y, float h, float strip, float crestW, float codeW, float k, boolean home) {
        int colour = home ? look.homeColour : look.awayColour;
        float width = strip + crestW + codeW;
        if (flash > 0 && flashSide == (home ? 1 : 2)) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(cx, y, cx + width, y + h, fill);
            fill.setAlpha(255);
        }
        Bitmap crest = home ? homeCrest : awayCrest;
        String code = home ? look.home : look.away;
        int ink = p.ink;
        if (p.clubFill) {
            // The whole side in the club's colour, its letters dark or light to suit.
            fill.setColor(colour);
            cv.drawRect(cx, y, cx + width, y + h, fill);
            ink = ink(colour);
        }
        if (home) {
            if (p.strips) {
                fill.setColor(colour);
                cv.drawRect(cx, y, cx + strip, y + h, fill);
            }
            if (crestW > 0 && crest != null) drawImage(cv, crest, cx + strip + crestW * 0.14f, y + (h - crestW * 0.72f) / 2, crestW * 0.72f);
            words(cv, code, cx + strip + crestW + codeW / 2, y, h, h * 0.46f * k, ink);
        } else {
            words(cv, code, cx + codeW / 2, y, h, h * 0.46f * k, ink);
            if (crestW > 0 && crest != null) drawImage(cv, crest, cx + codeW + crestW * 0.14f, y + (h - crestW * 0.72f) / 2, crestW * 0.72f);
            if (p.strips) {
                fill.setColor(colour);
                cv.drawRect(cx + codeW + crestW, y, cx + width, y + h, fill);
            }
        }
        return cx + width;
    }

    /** The clock's box: minutes and seconds, the minutes added on red; HT in amber; GOAL as one goes in. */
    private void clock(Canvas cv, float x, float y, float w, float h, float k) {
        if (p.clock != 0) {
            fill.setColor(p.clock);
            cv.drawRect(x, y, x + w, y + h, fill);
        }
        if (flash > 0) {
            fill.setColor(flashSide == 1 ? look.homeColour : look.awayColour);
            cv.drawRect(x, y, x + w, y + h, fill);
            words(cv, "GOAL", x + w / 2, y, h, h * 0.42f * k, ink(flashSide == 1 ? look.homeColour : look.awayColour));
            return;
        }
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? p.clockInk : p.dim;
        if (look.added.isEmpty()) {
            words(cv, look.clock, x + w / 2, y, h, h * 0.44f * k, colour);
            return;
        }
        float cw = measure(look.clock, h * 0.44f * k), aw = measure(look.added, h * 0.34f * k) + h * 0.3f;
        float start = x + (w - cw - aw - h * 0.15f) / 2;
        words(cv, look.clock, start + cw / 2, y, h, h * 0.44f * k, colour);
        float ax = start + cw + h * 0.15f;
        fill.setColor(ADDED);
        r.set(ax, y + h * 0.24f, ax + aw, y + h * 0.76f);
        cv.drawRoundRect(r, h * 0.08f, h * 0.08f, fill);
        words(cv, look.added, ax + aw / 2, y, h, h * 0.34f * k, 0xFFFFFFFF);
    }

    /** Nearer square: the clubs and the score above, the competition and the clock below. */
    private void twoRows(Canvas cv, int w, int h) {
        float top = h * 0.58f;
        clubsRow(cv, w, top);
        float y = top, bh = h - top;
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(0, y, w, h, fill);
        if (comp != null) drawImage(cv, comp, bh * 0.18f, y + bh * 0.12f, bh * 0.76f);
        else if (!look.tag.isEmpty()) words(cv, look.tag, bh * 1.1f, y, bh, bh * 0.42f, p.dim);
        clock(cv, w * 0.3f, y, w * 0.7f, bh, 1.25f);
    }

    private void clubsRow(Canvas cv, int w, float h) {
        // Without the competition's block (it moves below): the clubs and the score across the width.
        float codeW = code(Math.round(h)), strip = h * 0.1f, crestW = crestW(h), score = scoreW(Math.round(h));
        float need = 2 * (strip + crestW + codeW) + score;
        boolean crests = need <= w + 1.5f;
        if (!crests) need -= 2 * crestW;
        float k = need > w + 1.5f ? Math.max(0.6f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        float cx = side(cv, 0, 0, h, strip * k, crests ? crestW : 0, codeW * k + extra / 2, k, true);
        scoreBlock(cv, cx, 0, score * k, h);
        words(cv, look.score, cx + score * k / 2, 0, h, h * 0.56f * k, p.scoreInk);
        side(cv, cx + score * k, 0, h, strip * k, crests ? crestW : 0, codeW * k + extra / 2, k, false);
    }

    /** F1 along one row: the red block, the session, the corner, the first three. */
    private void f1Row(Canvas cv, float x, float y, float w, float h) {
        float cx = x;
        fill.setColor(F1_RED);
        cv.drawRect(cx, y, cx + h * 1.3f, y + h, fill);
        text.setTextSkewX(-0.2f);
        words(cv, "F1", cx + h * 0.65f, y, h, h * 0.5f, 0xFFFFFFFF);
        text.setTextSkewX(0);
        cx += h * 1.3f;
        float lw = label(Math.round(h));
        if (lw > 0) {
            words(cv, look.label, cx + lw / 2, y, h, h * 0.36f, p.ink);
            cx += lw;
        }
        float cw = corner(Math.round(h));
        if (p.clock != 0) {
            fill.setColor(p.clock);
            cv.drawRect(cx, y, cx + cw, y + h, fill);
        }
        float aw = measure(look.cornerA, h * 0.32f);
        words(cv, look.cornerA, cx + h * 0.35f + aw / 2, y, h, h * 0.32f, p.dim);
        words(cv, look.cornerB, cx + h * 0.55f + aw + measure(look.cornerB, h * 0.46f) / 2, y, h, h * 0.46f, p.clockInk);
        cx += cw + h * 0.15f;
        drivers(cv, cx, y, x + w - cx, h);
    }

    private void f1Two(Canvas cv, int w, int h) {
        float top = h * 0.5f;
        f1Row(cv, 0, 0, w, top);
        fill.setColor(p.comp != 0 ? p.comp : p.ground);
        cv.drawRect(0, top, w, h, fill);
        drivers(cv, h * 0.15f, top, w - h * 0.15f, h - top);
    }

    private void drivers(Canvas cv, float x, float y, float w, float h) {
        float cx = x;
        for (int i = 0; i < look.codes.size(); i++) {
            float cw = h * 0.16f + measure(look.codes.get(i), h * 0.44f) + h * 0.5f;
            if (cx + cw > x + w + 1) break;
            words(cv, String.valueOf(i + 1), cx + h * 0.12f, y, h, h * 0.3f, p.dim);
            fill.setColor(i < look.colours.size() ? look.colours.get(i) : F1_RED);
            cv.drawRect(cx + h * 0.26f, y + h * 0.24f, cx + h * 0.26f + h * 0.08f, y + h * 0.76f, fill);
            words(cv, look.codes.get(i), cx + h * 0.42f + measure(look.codes.get(i), h * 0.44f) / 2, y, h, h * 0.44f, p.ink);
            cx += cw;
        }
    }

    /** Minimal: one line on a dark pill, "LIV 2–1 MUN   67:23" (F1: "F1  LAP 23   NOR VER LEC"). */
    private String[] minimalParts() {
        if (look.f1) {
            StringBuilder d = new StringBuilder();
            for (String c : look.codes) d.append(d.length() == 0 ? "" : " ").append(c);
            return new String[]{"F1", (look.cornerA + " " + look.cornerB).trim(), d.toString()};
        }
        return new String[]{look.home + "  " + look.score + "  " + look.away, look.clock, look.added};
    }

    private float minimalWidth(int h) {
        String[] m = minimalParts();
        float w = h * 0.9f + measure(m[0], h * 0.48f) + measure(m[1], h * 0.44f) + h * 0.7f;
        if (!m[2].isEmpty()) w += measure(m[2], h * 0.38f) + h * 0.5f;
        return w;
    }

    private void minimal(Canvas cv, int w, int h) {
        String[] m = minimalParts();
        if (w / (float) h < 3.6f) {
            // A squarer place: the clubs and score over the clock.
            float top = h * 0.55f;
            float k1 = Math.min(1f, w * 0.9f / Math.max(1f, measure(m[0], top * 0.62f)));
            words(cv, m[0], w / 2f, h * 0.04f, top, top * 0.62f * k1, look.f1 ? F1_RED : p.ink);
            String under = flash > 0 && !look.f1 ? "GOAL" : (m[1] + (m[2].isEmpty() ? "" : "  " + m[2])).trim();
            float bh = h - top;
            float k2 = Math.min(1f, w * 0.9f / Math.max(1f, measure(under, bh * 0.62f)));
            int ink = "HT".equals(look.clock) ? AMBER : p.clockInk;
            words(cv, under, w / 2f, top - h * 0.04f, bh, bh * 0.62f * k2, ink);
            return;
        }
        float natural = minimalWidth(h), k = natural > w + 1.5f ? Math.max(0.4f, w / natural) : 1f;
        float x = (w - natural * k) / 2 + h * 0.45f * k;
        float a = measure(m[0], h * 0.48f * k);
        words(cv, m[0], x + a / 2, 0, h, h * 0.48f * k, look.f1 ? F1_RED : p.ink);
        x += a + h * 0.7f * k;
        float b = measure(m[1], h * 0.44f * k);
        int clockInk = "HT".equals(look.clock) ? AMBER : flash > 0 ? (flashSide == 1 ? look.homeColour : look.awayColour) : p.clockInk;
        words(cv, flash > 0 && !look.f1 ? "GOAL" : m[1], x + b / 2, 0, h, h * 0.44f * k, clockInk);
        x += b + h * 0.5f * k;
        if (!m[2].isEmpty()) {
            float c = measure(m[2], h * 0.38f * k);
            words(cv, m[2], x + c / 2, 0, h, h * 0.38f * k, look.f1 ? p.dim : ADDED);
        }
    }

    // ---- Crests and time --------------------------------------------------------------------------------------------

    /** One club's cell: its crest, or its three letters while the crest has not come. */
    private float crestCell(float h, boolean home) {
        if ((home ? homeCrest : awayCrest) != null) return h * 1.05f;
        return Math.max(h * 1.05f, measure(home ? look.home : look.away, h * 0.42f) + h * 0.4f);
    }

    private float crestsWidth(int h) {
        return h * 0.2f + crestCell(h, true) + scoreW(h) + crestCell(h, false) + h * 0.2f + clockW(h);
    }

    /** Crests and time along one row: each club's crest either side of the score, its colour at the crest's foot, the clock after. */
    private void crestsRow(Canvas cv, int w, int h) {
        float pad = h * 0.2f, hc = crestCell(h, true), ac = crestCell(h, false), score = scoreW(h), clock = clockW(h);
        float need = pad + hc + score + ac + pad + clock;
        float k = need > w + 1.5f ? Math.max(0.6f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        pad *= k;
        hc *= k;
        ac *= k;
        clock *= k;
        // Any room over goes to the score.
        score = score * k + extra;
        float cx = pad;
        crestSide(cv, cx, 0, hc, h, true);
        cx += hc;
        scoreBlock(cv, cx, 0, score, h);
        words(cv, look.score, cx + score / 2, 0, h, h * 0.56f * k, p.scoreInk);
        cx += score;
        crestSide(cv, cx, 0, ac, h, false);
        cx += ac + pad;
        if (clock > 0) clock(cv, cx, 0, w - cx, h, k);
    }

    /** Nearer square: the crests and the score above, the clock across the foot. */
    private void crestsTwo(Canvas cv, int w, int h) {
        float top = h * 0.62f, hc = crestCell(top, true), ac = crestCell(top, false), score = scoreW(Math.round(top));
        float need = hc + score + ac, k = need > w + 1.5f ? Math.max(0.6f, w / need) : 1f;
        float cx = Math.max(0, w - need * k) / 2;
        crestSide(cv, cx, 0, hc * k, top, true);
        cx += hc * k;
        scoreBlock(cv, cx, 0, score * k, top);
        words(cv, look.score, cx + score * k / 2, 0, top, top * 0.56f * k, p.scoreInk);
        cx += score * k;
        crestSide(cv, cx, 0, ac * k, top, false);
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(0, top, w, h, fill);
        if (!look.clock.isEmpty()) clock(cv, 0, top, w, h - top, 1.25f);
    }

    /** One club's crest (its letters until the crest comes) with its colour as a line at the foot; lit as it scores. */
    private void crestSide(Canvas cv, float x, float y, float w, float h, boolean home) {
        int colour = home ? look.homeColour : look.awayColour;
        if (flash > 0 && flashSide == (home ? 1 : 2)) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(x, y, x + w, y + h, fill);
            fill.setAlpha(255);
        }
        Bitmap crest = home ? homeCrest : awayCrest;
        float size = Math.min(w, h) * 0.7f;
        if (crest != null) drawImage(cv, crest, x + (w - size) / 2, y + h * 0.1f, size);
        else words(cv, home ? look.home : look.away, x + w / 2, y - h * 0.04f, h, h * 0.42f, p.ink);
        fill.setColor(colour);
        cv.drawRect(x + w * 0.2f, y + h * 0.86f, x + w * 0.8f, y + h * 0.93f, fill);
    }

    // ---- Stacked ---------------------------------------------------------------------------------------------------

    /** The clock's column beside the two clubs: minutes and seconds over the minutes added. */
    private float stackedClockW(int h) {
        if (look.clock.isEmpty()) return 0;
        float c = measure("88:88".length() >= look.clock.length() ? "88:88" : look.clock, h * 0.3f);
        float a = look.added.isEmpty() ? 0 : measure(look.added, h * 0.22f) + h * 0.3f;
        return Math.max(c, a) + h * 0.5f;
    }

    private float stackedRowsW(float r) {
        return r * 0.12f + (crestW(r) > 0 ? r * 0.95f : 0) + code(Math.round(r)) + r * 1.15f;
    }

    private float stackedWidth(int h) {
        return h * 0.9f + stackedRowsW(h / 2f) + stackedClockW(h);
    }

    /** "2–1" as {"2", "1"}; none when it is not a score (before kick-off). */
    private String[] goals() {
        String[] g = look.score.split("\\s*[–-]\\s*");
        return g.length == 2 ? g : new String[]{"", ""};
    }

    /** Stacked: the competition, the two clubs one above the other (colour, crest, letters, goals), the clock beside. */
    private void stacked(Canvas cv, int w, int h) {
        float r = h / 2f, comp = h * 0.9f, strip = r * 0.12f, crest = crestW(r) > 0 ? r * 0.95f : 0, codeW = code(Math.round(r));
        float goalsW = r * 1.15f, clock = stackedClockW(h);
        float need = comp + strip + crest + codeW + goalsW + clock;
        boolean compBlock = true;
        if (need > w + 1.5f) {
            compBlock = false;
            need -= comp;
        }
        float k = need > w + 1.5f ? Math.max(0.6f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        strip *= k;
        crest *= k;
        goalsW *= k;
        clock *= k;
        codeW = codeW * k + extra;
        float cx = 0;
        if (compBlock) {
            float cw = comp * k;
            fill.setColor(p.comp != 0 ? p.comp : p.ground);
            cv.drawRect(0, 0, cw, h, fill);
            if (this.comp != null) drawImage(cv, this.comp, cw * 0.14f, (h - cw * 0.72f) / 2, cw * 0.72f);
            else words(cv, look.tag, cw / 2, 0, h, h * 0.22f, p.dim);
            cx = cw;
        }
        float rw = strip + crest + codeW + goalsW;
        String[] g = goals();
        stackedRow(cv, cx, 0, rw, r, strip, crest, goalsW, true, g[0], k);
        stackedRow(cv, cx, r, rw, h - r, strip, crest, goalsW, false, g[1], k);
        fill.setColor(0x26FFFFFF);
        cv.drawRect(cx + strip, r - Math.max(1f, h * 0.008f), cx + rw, r + Math.max(1f, h * 0.008f), fill);
        cx += rw;
        if (clock > 0) stackedClock(cv, cx, w - cx, h, k);
    }

    private void stackedRow(Canvas cv, float x, float y, float w, float r, float strip, float crestW, float goalsW,
            boolean home, String goals, float k) {
        int colour = home ? look.homeColour : look.awayColour;
        if (flash > 0 && flashSide == (home ? 1 : 2)) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(x, y, x + w, y + r, fill);
            fill.setAlpha(255);
        }
        fill.setColor(colour);
        cv.drawRect(x, y, x + strip, y + r, fill);
        Bitmap crest = home ? homeCrest : awayCrest;
        if (crestW > 0 && crest != null) drawImage(cv, crest, x + strip + crestW * 0.12f, y + (r - crestW * 0.76f) / 2, crestW * 0.76f);
        wordsFrom(cv, home ? look.home : look.away, x + strip + crestW + r * 0.22f, y, r, r * 0.5f * k, p.ink);
        float gx = x + w - goalsW;
        fill.setColor(p.score0 != 0 ? p.score0 : p.ground);
        cv.drawRect(gx, y, x + w, y + r, fill);
        words(cv, goals, gx + goalsW / 2, y, r, r * 0.62f * k, p.scoreInk);
    }

    /** The clock's column: minutes and seconds, the minutes added on red under them; GOAL as one goes in. */
    private void stackedClock(Canvas cv, float x, float w, float h, float k) {
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(x, 0, x + w, h, fill);
        if (flash > 0) {
            int colour = flashSide == 1 ? look.homeColour : look.awayColour;
            fill.setColor(colour);
            cv.drawRect(x, 0, x + w, h, fill);
            words(cv, "GOAL", x + w / 2, 0, h, h * 0.28f * k, ink(colour));
            return;
        }
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? p.clockInk : p.dim;
        if (look.added.isEmpty()) {
            words(cv, look.clock, x + w / 2, 0, h, h * 0.3f * k, colour);
            return;
        }
        words(cv, look.clock, x + w / 2, h * 0.08f, h * 0.5f, h * 0.3f * k, colour);
        float aw = measure(look.added, h * 0.22f * k) + h * 0.3f;
        fill.setColor(ADDED);
        r.set(x + (w - aw) / 2, h * 0.6f, x + (w + aw) / 2, h * 0.86f);
        cv.drawRoundRect(r, h * 0.05f, h * 0.05f, fill);
        words(cv, look.added, x + w / 2, h * 0.6f, h * 0.26f, h * 0.22f * k, 0xFFFFFFFF);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** Words from x along, in the middle of (y, h). */
    private void wordsFrom(Canvas cv, String s, float x, float y, float h, float size, int colour) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, x, y + h / 2 - (fm.ascent + fm.descent) / 2, text);
    }

    /** Words centred on cx, in the middle of (y, h). */
    private void words(Canvas cv, String s, float cx, float y, float h, float size, int colour) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, cx, y + h / 2 - (fm.ascent + fm.descent) / 2, text);
    }

    private void drawImage(Canvas cv, Bitmap b, float x, float y, float size) {
        float s = size / Math.max(b.getWidth(), b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(x + (size - bw) / 2, y + (size - bh) / 2, x + (size + bw) / 2, y + (size + bh) / 2);
        cv.drawBitmap(b, null, r, image);
    }

    /** Dark words on a light colour, white on a dark one. */
    static int ink(int colour) {
        int rr = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF0A0D33 : 0xFFFFFFFF;
    }

    /** A club's colour from ESPN's hex ("ffff00"); white when none, its other colour when too dark to see on navy. */
    static int colour(String hex, String alt) {
        int c = parse(hex);
        if (c == 0 || dark(c)) {
            int a = parse(alt);
            if (a != 0 && !dark(a)) return a;
        }
        return c == 0 ? 0xFFFFFFFF : dark(c) ? 0xFFDDE3FF : c;
    }

    private static boolean dark(int c) {
        int rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 < 50;
    }

    private static int parse(String hex) {
        try {
            return hex == null || hex.length() != 6 ? 0 : 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
