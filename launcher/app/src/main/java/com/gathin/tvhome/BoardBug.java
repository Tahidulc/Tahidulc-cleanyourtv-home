package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Objects;

/**
 * The scoreboard BoardCover puts over the channel's own, as the phone's player draws it (Homefield's OverlayBug): the
 * competition, each club's colour, crest and three letters, the score, and the clock in minutes and seconds with the
 * minutes added beside it, along one row, or in two in a box nearer square (its height set apart from its width). The
 * phone's seven looks: Classic (the launcher's navy, the score on blue), Glass (smoked glass, a pill), Light, Club
 * colours (each side in its club's colour), Minimal (one line of words), Crests and time, and Stacked (the clubs one
 * above the other).
 *
 * <p>It comes and goes as broadcasters' do (its first block opening out of a thin line, the rest wiping out of it
 * behind a bright edge), and a goal is shown as theirs are: a white hit, the scorers' colour sweeping across it from
 * their side with their crest and GOAL, held with a shine running over it, then on out the far side, and the new score
 * popping up big and settling while that side glows. What folds out under it (the stats, the alerts) takes its look
 * (StripLook).
 *
 * <p>Kind to the panel: the words a soft white rather than a pure one, a very light club colour toned down, Light's
 * ground a soft grey rather than white, and an edge of its ground (inset) round everything, so the pixel or two it
 * moves against burn-in never uncovers what it covers.
 */
final class BoardBug extends View {
    /** What it shows. */
    static final class Look {
        String home = "", away = "", score = "", clock = "", added = "", tag = "";
        int homeColour = INK, awayColour = INK;
        boolean live;

        @Override public boolean equals(Object o) {
            if (!(o instanceof Look)) return false;
            Look l = (Look) o;
            return live == l.live && home.equals(l.home) && away.equals(l.away) && score.equals(l.score) && clock.equals(l.clock)
                    && added.equals(l.added) && tag.equals(l.tag) && homeColour == l.homeColour && awayColour == l.awayColour;
        }

        @Override public int hashCode() {
            return Objects.hash(home, away, score, clock, added);
        }
    }

    /** Its looks, as the Scoreboard card names them (the phone's, in the phone's order). */
    enum Style {
        CLASSIC("Classic"), GLASS("Glass"), LIGHT("Light"), CLUB("Club colours"), MINIMAL("Minimal"),
        CRESTS("Crests and time"), STACKED("Stacked", 1.7f);

        final String label;
        /** How much taller than the others it is drawn (Stacked: the clubs one above the other). */
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

    /** The words' white, a little short of pure white, and the quieter words. */
    static final int INK = 0xFFE6E9F2, SOFT = 0xFFA9B1D6;
    private static final int NAVY = 0xFF0B1640, NAVY_DEEP = 0xFF070C26, CLOCK = 0xFF050816, ADDED = 0xFFC81E34,
            AMBER = 0xFFE8A33D, SCORE_TOP = 0xFF2443B4, SCORE_BOTTOM = 0xFF142A78, MINIMAL_GROUND = 0xFF070912,
            CRESTS_GROUND = 0xFF0A1238, STACKED_GROUND = 0xFF0E1330, GOALS = 0xFF1E2A66;

    /** One look's colours. */
    private static final class Palette {
        int ground, comp, score0, score1, clock, ink = INK, scoreInk = INK, clockInk = INK, dim = SOFT;
        boolean strips = true, clubFill, sheen;
        /** The corners' radius as a share of the height (0.5: a pill). */
        float round = 0.14f;
    }

    private static Palette palette(Style s) {
        Palette p = new Palette();
        switch (s) {
            case GLASS:
                // Smoked glass in look only: solid, so the channel's own scoreboard under it never shows; a sheen
                // across its top, the score and the clock in lighter and darker glass, a pill.
                p.ground = 0xFF151B36;
                p.comp = 0;
                p.score0 = 0x40FFFFFF;
                p.score1 = 0x1CFFFFFF;
                p.clock = 0x47000000;
                p.dim = 0xFFC3CAE6;
                p.round = 0.5f;
                p.sheen = true;
                break;
            case LIGHT:
                // A soft grey rather than white (it stays up all game), navy words; the competition's block stays
                // navy, as its logo is drawn for dark grounds.
                p.ground = 0xFFE3E7EE;
                p.comp = NAVY;
                p.score0 = p.score1 = NAVY;
                p.clock = 0xFFD0D6E2;
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
                p.ground = MINIMAL_GROUND;
                p.comp = 0;
                p.score0 = p.score1 = 0;
                p.clock = 0;
                p.round = 0.25f;
                break;
            case CRESTS:
                p.ground = CRESTS_GROUND;
                p.comp = NAVY_DEEP;
                p.score0 = SCORE_TOP;
                p.score1 = SCORE_BOTTOM;
                p.clock = CLOCK;
                break;
            case STACKED:
                p.ground = STACKED_GROUND;
                p.comp = NAVY_DEEP;
                p.score0 = p.score1 = GOALS;
                p.clock = CLOCK;
                p.round = 0.08f;
                break;
            default:
                p.ground = NAVY;
                p.comp = NAVY_DEEP;
                p.score0 = SCORE_TOP;
                p.score1 = SCORE_BOTTOM;
                p.clock = CLOCK;
        }
        return p;
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private Look look = new Look();
    private Style style = Style.CLASSIC;
    private Palette p = palette(Style.CLASSIC);
    private Bitmap homeCrest, awayCrest, comp;
    /** Room kept for the crests (the game has them), so the board's width holds as they arrive. */
    private boolean crests;
    /** Its ground's edge round everything, in pixels (the room for BoardCover's burn-in step). */
    private int inset;
    private boolean outlined;
    /** Made once for a size and kept: the score's shade, the band's, Glass's sheen, the ring's dashes. */
    private LinearGradient scoreShade, bandShade, sheenShade;
    private float shadeY = -1, shadeH = -1, bandShadeH = -1, sheenH = -1, dashFor = -1;
    private int shadeA, shadeB;

    BoardBug(Context c) {
        super(c);
        text.setTypeface(ScoreCard.outfit(c, 800));
        text.setFontFeatureSettings("tnum");
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(INK);
        shine.setShader(shineShader);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void inset(int px) {
        inset = px;
        invalidate();
    }

    void style(Style s) {
        if (s == style) return;
        style = s;
        p = palette(s);
        invalidate();
    }

    Style style() {
        return style;
    }

    /** Whether the game has crests: their room is kept from the start. */
    void crests(boolean expected) {
        crests = expected;
    }

    void images(Bitmap home, Bitmap away, Bitmap competition) {
        homeCrest = home;
        awayCrest = away;
        comp = competition;
        invalidate();
    }

    /** Ringed while Move and resize has it. */
    void outline(boolean on) {
        if (on == outlined) return;
        outlined = on;
        invalidate();
    }

    Look look() {
        return look;
    }

    /** What to show; drawn again only when it changed. */
    void set(Look l) {
        if (l.equals(look)) return;
        look = l;
        setContentDescription(l.home + " " + l.score + " " + l.away + (l.clock.isEmpty() ? "" : ", " + l.clock + " " + l.added));
        invalidate();
    }

    // ---- a goal, as broadcasters show one --------------------------------------------------------------------------

    /**
     * A goal's moment (goal()), in ms from its start: the white hit to HIT_MS; the scorers' band sweeping in from BAND_IN
     * over BAND_MS, held, then out from BAND_OUT; then the new score's glow over GLOW_MS, GOAL_MS in all.
     */
    static final long HIT_MS = 260, BAND_IN = 180, BAND_MS = 500, BAND_OUT = 3300, GLOW_MS = 1000,
            GOAL_MS = BAND_OUT + BAND_MS + GLOW_MS;
    /** When the goal's moment began (0: none), or, for previews, how far into it to draw it (-1: as the time goes). */
    private long goalAt, goalFrozen = -1;
    /** The side that scored, 1 (home) or 2 (away); the glow after the band, 1 to 0; the score drawn this much bigger. */
    private int flashSide;
    private float flash, scoreZoom = 1f;
    /** The shine across the band: a soft white streak, moved across it by its matrix. */
    private final LinearGradient shineShader = new LinearGradient(0, 0, 1, 0,
            new int[]{0x00FFFFFF, 0x52FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    private final Matrix shineAt = new Matrix();

    /** A goal for side 1 (home) or 2 (away), as broadcasters show one (GOAL_MS in all). */
    void goal(int side) {
        flashSide = side;
        goalAt = System.currentTimeMillis();
        goalFrozen = -1;
        postInvalidateOnAnimation();
    }

    /** Previews and tests: drawn t ms into a goal for this side (-1: no goal). */
    void goalNow(int side, long t) {
        flashSide = side;
        goalFrozen = t;
        goalAt = t < 0 ? 0 : System.currentTimeMillis() - t;
        invalidate();
    }

    /** How long until the goal's band has gone (an alert waits for it), in ms; 0 when there is none. */
    long bandLeft(long now) {
        if (goalAt <= 0 || goalFrozen >= 0) return 0;
        return Math.max(0, goalAt + BAND_OUT + BAND_MS - now);
    }

    /**
     * Pure: where the goal's band is t ms into the goal, {left, right} as shares of the board's width: in from the
     * scorers' side (home's the left), held across it all, then on out the far side; {0, 0} before and after.
     */
    static float[] goalBand(long t, boolean home) {
        if (t < BAND_IN || t >= BAND_OUT + BAND_MS) return new float[]{0f, 0f};
        float in = ease((t - BAND_IN) / (float) BAND_MS), out = t < BAND_OUT ? 0f : ease((t - BAND_OUT) / (float) BAND_MS);
        return home ? new float[]{out, in} : new float[]{1f - in, 1f - out};
    }

    /** Pure: the new score's glow t ms into the goal, from the band's going: 1 down to 0 over GLOW_MS; 0 otherwise. */
    static float glow(long t) {
        long from = BAND_OUT + BAND_MS;
        return t < from || t >= from + GLOW_MS ? 0f : 1f - (t - from) / (float) GLOW_MS;
    }

    // ---- coming and going, as a broadcaster's ----------------------------------------------------------------------

    /** How far it is shown: 0 gone, 1 all there. Up to OPEN_AT its first block opens out of a line; then the rest wipes out of it. */
    private float shown = 1f;
    static final float OPEN_AT = 0.3f;
    private ValueAnimator showing;
    private boolean closing;

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

    /** Shown this far at once (0 to begin its coming in; previews). */
    void shownAt(float f) {
        if (showing != null) showing.cancel();
        shown = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    private void to(float target, long ms, Runnable after) {
        if (showing != null) showing.cancel();
        float from = shown;
        if (from == target) {
            if (target <= 0f) closing = false;
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

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (showing != null) showing.cancel();
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
    static float ease(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    // ---- what folds out under it ------------------------------------------------------------------------------------

    /** What folds out under it (the stats, the alerts), in its look. */
    static final class StripLook {
        /** Its ground (the board's, solid enough for words), its words, its quieter words, a chip's block. */
        int ground = 0xF70B1033, ink = INK, dim = SOFT, block = SCORE_TOP;
        /** A light ground (Light): dark words, the clubs' colours as they are. */
        boolean light;
        /** Under a pill (Glass): it stands a little apart, round all round, as a pill of its own. */
        boolean apart;
        /** Its corners' radius as a share of the board's height (the board's own). */
        float round = 0.14f;
    }

    /** Its strips' look, made again only when its own look changes. */
    StripLook stripLook() {
        if (strip == null || stripFor != style) {
            strip = strip(style, p);
            stripFor = style;
        }
        return strip;
    }

    private StripLook strip;
    private Style stripFor;

    private static StripLook strip(Style style, Palette p) {
        StripLook s = new StripLook();
        s.ground = 0xFF000000 | p.ground;
        s.ink = p.ink;
        s.dim = p.dim;
        s.light = style == Style.LIGHT;
        s.apart = p.round >= 0.5f;
        s.round = p.round;
        s.block = style == Style.CLUB ? INK : style == Style.LIGHT ? NAVY
                : p.score0 != 0 && (p.score0 >>> 24) == 0xFF ? p.score0 : 0x40FFFFFF;
        return s;
    }

    // ---- size ------------------------------------------------------------------------------------------------------

    /** Its width for words h high (its edge aside). */
    int natural(int h) {
        if (style == Style.MINIMAL) return Math.round(minimalWidth(h));
        if (style == Style.CRESTS) return Math.round(h * 0.4f + crestCell(h, true) + scoreW(h) + crestCell(h, false) + clockW(h));
        if (style == Style.STACKED) return Math.round(h * 0.9f + stackedRowsW(h / 2f) + stackedClockW(h));
        float pill = p.round >= 0.5f ? h * 0.36f : 0f;
        return Math.round(h + 2 * (h * 0.1f + crestW(h) + code(h)) + scoreW(h) + clockW(h) + pill);
    }

    private float measure(String s, float size) {
        text.setTextSize(size);
        return text.measureText(s == null ? "" : s);
    }

    private float crestW(float h) {
        return crests ? h * 0.86f : 0;
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

    // ---- drawing ---------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || shown <= 0f) return;
        int cw = w - 2 * inset, ch = h - 2 * inset;
        boolean part = shown < 1f;
        float[] in = null;
        if (part) {
            // Its first block (the competition's square, Crests' first crest), never more than a third of it.
            float first = style == Style.CRESTS ? ch * 1.25f : style == Style.STACKED ? ch * 0.9f
                    : style == Style.MINIMAL ? ch * 1.2f : ch;
            in = shownPart(shown, w, h, Math.min(first + inset, w / 3f));
            cv.save();
            cv.clipRect(in[0], in[1], in[2], in[3]);
        }
        long now = System.currentTimeMillis();
        long gt = goalFrozen >= 0 ? goalFrozen : goalAt > 0 ? now - goalAt : -1;
        boolean goalOn = gt >= 0 && gt < GOAL_MS;
        flash = goalOn ? glow(gt) : 0f;
        // The new score pops up big as the band goes, and settles.
        scoreZoom = 1f + 0.38f * flash * flash;
        float rad = p.round >= 0.5f ? h / 2f : h * p.round;
        r.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        fill.setShader(null);
        fill.setColor(p.ground);
        cv.drawRect(r, fill);
        if (p.sheen) sheen(cv, w, h);
        cv.translate(inset, inset);
        if (cw > 0 && ch > 0) {
            if (style == Style.MINIMAL) minimal(cv, cw, ch);
            else if (style == Style.CRESTS) {
                if (cw / (float) ch >= 3.2f) crestsRow(cv, cw, ch);
                else crestsTwo(cv, cw, ch);
            } else if (style == Style.STACKED) stacked(cv, cw, ch);
            else if (cw / (float) ch >= 3.6f) oneRow(cv, 0, 0, cw, ch);
            else twoRows(cv, cw, ch);
        }
        cv.translate(-inset, -inset);
        if (goalOn) goalMoment(cv, w, h, gt);
        cv.restore();
        if (part) {
            cv.restore();
            // The wipe's leading edge, a bright line fading as it lands.
            if (shown > OPEN_AT && in[2] < w - 1) {
                float lw = Math.max(2f, h * 0.05f), a = 1f - (shown - OPEN_AT) / (1f - OPEN_AT);
                fill.setShader(null);
                fill.setColor(INK);
                fill.setAlpha(Math.round(220 * Math.max(0.25f, a)));
                cv.drawRect(in[2] - lw, 0, in[2], h, fill);
                fill.setAlpha(255);
            }
        }
        if (outlined && !part) {
            float sw = Math.max(2f, h * 0.05f);
            if (sw != dashFor) {
                dashFor = sw;
                edge.setStrokeWidth(sw);
                edge.setPathEffect(new DashPathEffect(new float[]{sw * 3, sw * 2}, 0));
            }
            r.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
            cv.drawRoundRect(r, rad, rad, edge);
        }
        if (goalOn && goalFrozen < 0) postInvalidateOnAnimation();
    }

    /** Glass's sheen: lighter across its top, fading by its middle. */
    private void sheen(Canvas cv, int w, int h) {
        if (sheenShade == null || sheenH != h) {
            sheenShade = new LinearGradient(0, 0, 0, h, new int[]{0x2EFFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            sheenH = h;
        }
        fill.setShader(sheenShade);
        cv.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
    }

    /** The goal's hit and band over the board, t ms in: the scorers' colour, their crest and GOAL, a shine across it. */
    private void goalMoment(Canvas cv, int w, int h, long t) {
        boolean home = flashSide != 2;
        float[] b = goalBand(t, home);
        float left = b[0] * w, right = b[1] * w;
        if (right - left >= 0.5f) {
            int colour = home ? look.homeColour : look.awayColour;
            cv.save();
            cv.clipRect(left, 0, right, h);
            fill.setShader(null);
            fill.setColor(colour);
            cv.drawRect(0, 0, w, h, fill);
            // Lit from above and darker at its foot, as a broadcaster's band is.
            if (bandShade == null || bandShadeH != h) {
                bandShade = new LinearGradient(0, 0, 0, h, new int[]{0x2EFFFFFF, 0x00FFFFFF, 0x00000000, 0x38000000},
                        new float[]{0f, 0.42f, 0.58f, 1f}, Shader.TileMode.CLAMP);
                bandShadeH = h;
            }
            fill.setShader(bandShade);
            cv.drawRect(0, 0, w, h, fill);
            fill.setShader(null);
            // The crest and GOAL in the middle, drifting in with the band and on out with it.
            float in = ease((t - BAND_IN) / (float) BAND_MS), out = t < BAND_OUT ? 0f : ease((t - BAND_OUT) / (float) BAND_MS);
            float drift = (home ? 1 : -1) * (in - 1f + out) * h * 1.2f;
            Bitmap crest = home ? homeCrest : awayCrest;
            text.setLetterSpacing(0.16f);
            float size = h * 0.5f, word = measure("GOAL", size), cs = crest != null ? h * 0.72f : 0f, gap = cs > 0 ? h * 0.24f : 0f;
            float need = cs + gap + word, k = Math.min(1f, w * 0.84f / Math.max(1f, need));
            float x = (w - need * k) / 2f + drift;
            if (crest != null) drawImage(cv, crest, x, (h - cs * k) / 2f, cs * k);
            wordsFrom(cv, "GOAL", x + (cs + gap) * k, 0, h, size * k, ink(colour));
            text.setLetterSpacing(0f);
            // A shine across it now and then while it is held.
            long held = t - BAND_IN - BAND_MS;
            if (held >= 0 && t < BAND_OUT) {
                float sw = Math.max(h * 1.4f, w * 0.2f), s = (held % 1300L) / 1300f;
                shineAt.setScale(sw, 1f);
                shineAt.postSkew(-0.5f, 0f);
                shineAt.postTranslate(-sw + (w + sw + h) * s, 0f);
                shineShader.setLocalMatrix(shineAt);
                cv.drawRect(left, 0, right, h, shine);
            }
            cv.restore();
        }
        if (t < HIT_MS) {
            // The hit: the whole board white for an instant.
            fill.setShader(null);
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(Math.round(160 * (float) Math.sin(Math.PI * t / HIT_MS)));
            cv.drawRect(0, 0, w, h, fill);
            fill.setAlpha(255);
        }
    }

    /** The clubs, the score and the clock along one row in (x, y, w, h). */
    private void oneRow(Canvas cv, float x, float y, float w, float h) {
        int hi = Math.round(h);
        boolean withCrests = true, compBlock = true;
        float codeW = code(hi), strip = h * 0.1f, crestW = crestW(h), score = scoreW(hi), clock = clockW(hi);
        // A pixel's grace: its own width is the sum rounded.
        float room = w + 1.5f;
        float need = h + 2 * (strip + crestW + codeW) + score + clock;
        if (p.round >= 0.5f) need += h * 0.36f;
        if (need > room && crestW > 0) {
            withCrests = false;
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
            else words(cv, look.tag, cx + h / 2, y, h, h * 0.3f, tagInk());
            cx += h;
        }
        float crest = withCrests ? crestW : 0;
        cx = side(cv, cx, y, h, strip, crest, codeW, k, true);
        scoreBlock(cv, cx, y, score, h);
        words(cv, look.score, cx + score / 2, y, h, h * 0.56f * k * scoreZoom, p.scoreInk);
        cx += score;
        cx = side(cv, cx, y, h, strip, crest, codeW, k, false);
        if (clock > 0) clock(cv, cx, y, x + w - cx, h, k);
    }

    private void scoreBlock(Canvas cv, float x, float y, float w, float h) {
        if (p.score0 == 0 && p.score1 == 0) return;
        if (scoreShade == null || shadeY != y || shadeH != h || shadeA != p.score0 || shadeB != p.score1) {
            scoreShade = new LinearGradient(0, y, 0, y + h, p.score0, p.score1, Shader.TileMode.CLAMP);
            shadeY = y;
            shadeH = h;
            shadeA = p.score0;
            shadeB = p.score1;
        }
        fill.setShader(scoreShade);
        cv.drawRect(x, y, x + w, y + h, fill);
        fill.setShader(null);
    }

    /** One club's part: (home) strip, crest, letters; (away) letters, crest, strip. Returns where it ends. */
    private float side(Canvas cv, float cx, float y, float h, float strip, float crestW, float codeW, float k, boolean home) {
        int colour = home ? look.homeColour : look.awayColour;
        float width = strip + crestW + codeW;
        boolean lit = flash > 0 && flashSide == (home ? 1 : 2);
        if (lit) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(cx, y, cx + width, y + h, fill);
            fill.setAlpha(255);
        }
        Bitmap crest = home ? homeCrest : awayCrest;
        String code = home ? look.home : look.away;
        int ink = litInk(colour, lit);
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

    /** The clock's box: minutes and seconds, the minutes added on red; HT in amber. */
    private void clock(Canvas cv, float x, float y, float w, float h, float k) {
        if (p.clock != 0) {
            fill.setColor(p.clock);
            cv.drawRect(x, y, x + w, y + h, fill);
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
        words(cv, look.added, ax + aw / 2, y, h, h * 0.34f * k, INK);
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
        boolean withCrests = need <= w + 1.5f;
        if (!withCrests) need -= 2 * crestW;
        float k = need > w + 1.5f ? Math.max(0.6f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        float cx = side(cv, 0, 0, h, strip * k, withCrests ? crestW : 0, codeW * k + extra / 2, k, true);
        scoreBlock(cv, cx, 0, score * k, h);
        words(cv, look.score, cx + score * k / 2, 0, h, h * 0.56f * k * scoreZoom, p.scoreInk);
        side(cv, cx + score * k, 0, h, strip * k, withCrests ? crestW : 0, codeW * k + extra / 2, k, false);
    }

    // ---- Crests and time --------------------------------------------------------------------------------------------

    /** One club's cell: its crest (room kept while it comes), else its three letters. */
    private float crestCell(float h, boolean home) {
        if (crests) return h * 1.05f;
        return Math.max(h * 1.05f, measure(home ? look.home : look.away, h * 0.42f) + h * 0.4f);
    }

    /** Crests and time: each club's crest either side of the score, its colour at the crest's foot, the clock after. */
    private void crestsRow(Canvas cv, int w, int h) {
        float pad = h * 0.2f, hc = crestCell(h, true), ac = crestCell(h, false), score = scoreW(h), clock = clockW(h);
        float need = 2 * pad + hc + score + ac + clock;
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
        words(cv, look.score, cx + score / 2, 0, h, h * 0.56f * k * scoreZoom, p.scoreInk);
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
        words(cv, look.score, cx + score * k / 2, 0, top, top * 0.56f * k * scoreZoom, p.scoreInk);
        cx += score * k;
        crestSide(cv, cx, 0, ac * k, top, false);
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(0, top, w, h, fill);
        if (!look.clock.isEmpty()) clock(cv, 0, top, w, h - top, 1.25f);
    }

    /** One club's crest (its letters until the crest comes) with its colour as a line at its foot; lit as it scores. */
    private void crestSide(Canvas cv, float x, float y, float w, float h, boolean home) {
        int colour = home ? look.homeColour : look.awayColour;
        boolean lit = flash > 0 && flashSide == (home ? 1 : 2);
        if (lit) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(x, y, x + w, y + h, fill);
            fill.setAlpha(255);
        }
        Bitmap crest = home ? homeCrest : awayCrest;
        float size = Math.min(w, h) * 0.7f;
        if (crest != null) drawImage(cv, crest, x + (w - size) / 2, y + h * 0.1f, size);
        else words(cv, home ? look.home : look.away, x + w / 2, y - h * 0.04f, h, h * 0.42f, litInk(colour, lit));
        fill.setColor(colour);
        cv.drawRect(x + w * 0.2f, y + h * 0.86f, x + w * 0.8f, y + h * 0.93f, fill);
    }

    // ---- Stacked ---------------------------------------------------------------------------------------------------

    private float stackedClockW(int h) {
        if (look.clock.isEmpty()) return 0;
        float c = measure("88:88".length() >= look.clock.length() ? "88:88" : look.clock, h * 0.3f);
        float a = look.added.isEmpty() ? 0 : measure(look.added, h * 0.22f) + h * 0.3f;
        return Math.max(c, a) + h * 0.5f;
    }

    private float stackedRowsW(float r) {
        return r * 0.12f + (crests ? r * 0.95f : 0) + code(Math.round(r)) + r * 1.15f;
    }

    /** "2–1" as {"2", "1"}; none when it is not a score. */
    private String[] goals() {
        String[] g = look.score.split("\\s*[–-]\\s*");
        return g.length == 2 ? g : new String[]{"", ""};
    }

    /** Stacked: the competition, the two clubs one above the other (colour, crest, letters, goals), the clock beside. */
    private void stacked(Canvas cv, int w, int h) {
        float r = h / 2f, comp = h * 0.9f, strip = r * 0.12f, crest = crests ? r * 0.95f : 0, codeW = code(Math.round(r));
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
            else words(cv, look.tag, cw / 2, 0, h, h * 0.22f, tagInk());
            cx = cw;
        }
        float rw = strip + crest + codeW + goalsW;
        String[] g = goals();
        stackedRow(cv, cx, 0, rw, r, strip, crest, goalsW, true, g[0], k);
        stackedRow(cv, cx, r, rw, h - r, strip, crest, goalsW, false, g[1], k);
        fill.setColor(0x26FFFFFF);
        float line = Math.max(1f, h * 0.008f);
        cv.drawRect(cx + strip, r - line, cx + rw, r + line, fill);
        cx += rw;
        if (clock > 0) stackedClock(cv, cx, w - cx, h, k);
    }

    private void stackedRow(Canvas cv, float x, float y, float w, float r, float strip, float crestW, float goalsW,
            boolean home, String goals, float k) {
        int colour = home ? look.homeColour : look.awayColour;
        boolean scored = flash > 0 && flashSide == (home ? 1 : 2);
        if (scored) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(x, y, x + w, y + r, fill);
            fill.setAlpha(255);
        }
        fill.setColor(colour);
        cv.drawRect(x, y, x + strip, y + r, fill);
        Bitmap crest = home ? homeCrest : awayCrest;
        if (crestW > 0 && crest != null) drawImage(cv, crest, x + strip + crestW * 0.12f, y + (r - crestW * 0.76f) / 2, crestW * 0.76f);
        wordsFrom(cv, home ? look.home : look.away, x + strip + crestW + r * 0.22f, y, r, r * 0.5f * k, litInk(colour, scored));
        float gx = x + w - goalsW;
        fill.setColor(p.score0 != 0 ? p.score0 : p.ground);
        cv.drawRect(gx, y, x + w, y + r, fill);
        words(cv, goals, gx + goalsW / 2, y, r, r * 0.62f * k * (scored ? scoreZoom : 1f), p.scoreInk);
    }

    /** The clock's column: minutes and seconds, the minutes added on red under them. */
    private void stackedClock(Canvas cv, float x, float w, float h, float k) {
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(x, 0, x + w, h, fill);
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
        words(cv, look.added, x + w / 2, h * 0.6f, h * 0.26f, h * 0.22f * k, INK);
    }

    // ---- Minimal ---------------------------------------------------------------------------------------------------

    /** Minimal: one line, "LIV  2–1  MUN   67:23 +2". */
    private String[] minimalParts() {
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
        int clockInk = "HT".equals(look.clock) ? AMBER : flash > 0 ? readable(flashSide == 1 ? look.homeColour : look.awayColour) : p.clockInk;
        if (w / (float) h < 3.6f) {
            // A squarer place: the clubs and score over the clock.
            float top = h * 0.55f;
            float k1 = Math.min(1f, w * 0.9f / Math.max(1f, measure(m[0], top * 0.62f)));
            words(cv, m[0], w / 2f, h * 0.04f, top, top * 0.62f * k1, p.ink);
            String under = (m[1] + (m[2].isEmpty() ? "" : "  " + m[2])).trim();
            float bh = h - top;
            float k2 = Math.min(1f, w * 0.9f / Math.max(1f, measure(under, bh * 0.62f)));
            words(cv, under, w / 2f, top - h * 0.04f, bh, bh * 0.62f * k2, clockInk);
            return;
        }
        float natural = minimalWidth(h), k = natural > w + 1.5f ? Math.max(0.4f, w / natural) : 1f;
        float x = (w - natural * k) / 2 + h * 0.45f * k;
        float a = measure(m[0], h * 0.48f * k);
        words(cv, m[0], x + a / 2, 0, h, h * 0.48f * k, p.ink);
        x += a + h * 0.7f * k;
        float b = measure(m[1], h * 0.44f * k);
        words(cv, m[1], x + b / 2, 0, h, h * 0.44f * k, clockInk);
        x += b + h * 0.5f * k;
        if (!m[2].isEmpty()) {
            float c = measure(m[2], h * 0.38f * k);
            words(cv, m[2], x + c / 2, 0, h, h * 0.38f * k, ADDED);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** Words centred on cx, in the middle of (y, h). */
    private void words(Canvas cv, String s, float cx, float y, float h, float size, int colour) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, cx, y + h / 2 - (fm.ascent + fm.descent) / 2, text);
    }

    /** Words from x along, in the middle of (y, h). */
    private void wordsFrom(Canvas cv, String s, float x, float y, float h, float size, int colour) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, x, y + h / 2 - (fm.ascent + fm.descent) / 2, text);
    }

    private void drawImage(Canvas cv, Bitmap b, float x, float y, float size) {
        float s = size / Math.max(b.getWidth(), b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(x + (size - bw) / 2, y + (size - bh) / 2, x + (size + bw) / 2, y + (size + bh) / 2);
        cv.drawBitmap(b, null, r, image);
    }

    /** The competition's letters on its block (while its logo has not come): soft white on a dark block, else the quieter words. */
    private int tagInk() {
        return p.comp == NAVY || p.comp == NAVY_DEEP || p.comp == 0xFF000000 ? SOFT : p.dim;
    }

    /** Pure: a club's colour bright enough to read on a dark ground (one too dark mixed halfway to white). */
    static int readable(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if ((r * 299 + g * 587 + b * 114) / 1000 >= 110) return 0xFF000000 | c;
        return blend(0xFF000000 | c, 0xFFFFFFFF, 0.5f);
    }

    /** The words' colour on a club's side as it glows in its colour after a goal: dark on a light glow, else as ever. */
    private int litInk(int colour, boolean lit) {
        if (!lit || flash <= 0f) return p.ink;
        return ink(blend(0xFF000000 | p.ground, colour, 200 * flash / 255f));
    }

    /** Pure: b laid over a at this share. */
    static int blend(int a, int b, float share) {
        float s = Math.max(0f, Math.min(1f, share));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - s) + ((b >> 16) & 0xFF) * s);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - s) + ((b >> 8) & 0xFF) * s);
        int bl = Math.round((a & 0xFF) * (1 - s) + (b & 0xFF) * s);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** Dark words on a light colour, the soft white on a dark one. Pure. */
    static int ink(int colour) {
        int rr = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF0A0D33 : INK;
    }

    /**
     * A club's colour for the navy: its own, else its second when the first is too dark to see, else a pale one; a
     * very light one (white, yellow) toned down a little, as it stays on the screen all game. Pure.
     */
    static int colour(int first, int second) {
        int c = first == 0 ? 0 : 0xFF000000 | first;
        if (c == 0 || dark(c)) {
            int a = second == 0 ? 0 : 0xFF000000 | second;
            if (a != 0 && !dark(a)) return softer(a);
        }
        return c == 0 ? INK : dark(c) ? 0xFFC9CEDD : softer(c);
    }

    /** Pure: a colour brighter than the words' white brought down to about their brightness. */
    static int softer(int c) {
        int rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if ((rr * 299 + g * 587 + b * 114) / 1000 <= 205) return c;
        return 0xFF000000 | Math.round(rr * 0.84f) << 16 | Math.round(g * 0.84f) << 8 | Math.round(b * 0.84f);
    }

    private static boolean dark(int c) {
        int rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 < 50;
    }
}
