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
 * phone's eight looks: Classic (the launcher's navy, the score on blue), Glass (smoked glass, a pill), Light, Club
 * colours (each side in its club's colour), Minimal (one line of words), Crests and time, Stacked (the clubs one
 * above the other) and Card (each crest on white, the score over the clock on blue between them, red in added time (steady, its light slants
 * still, nothing flashing), the minutes added in a tab of their own beside it).
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
        /** Each club's own colour as it shows on white (Card's ends); 0 when not known (its colour above instead). */
        int homeOwn, awayOwn;
        boolean live;
        /** In its half's added time (BoardCover knows the half): every look's clock goes red and stays red, its minutes added beside it. */
        boolean stoppage;

        @Override public boolean equals(Object o) {
            if (!(o instanceof Look)) return false;
            Look l = (Look) o;
            return live == l.live && home.equals(l.home) && away.equals(l.away) && score.equals(l.score) && clock.equals(l.clock)
                    && added.equals(l.added) && tag.equals(l.tag) && homeColour == l.homeColour && awayColour == l.awayColour
                    && homeOwn == l.homeOwn && awayOwn == l.awayOwn && stoppage == l.stoppage;
        }

        @Override public int hashCode() {
            return Objects.hash(home, away, score, clock, added);
        }
    }

    /** Its looks, as the Scoreboard card names them (the phone's, in the phone's order). */
    enum Style {
        CLASSIC("Classic"), GLASS("Glass"), LIGHT("Light"), CLUB("Club colours"), MINIMAL("Minimal"),
        CRESTS("Crests and time"), STACKED("Stacked", 1.7f), CARD("Card", 1.6f), FROSTED("Frosted glass");

        final String label;
        /** How much taller than the others it is drawn (Stacked: the clubs one above the other; Card: big crests). */
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
    /** Card's white (a soft one, shaded to a light grey at its foot: it stays up all game) and its red. */
    static final int CARD_TOP = 0xFFF1F3F7, CARD_FOOT = 0xFFD3D8E1, CARD_RED = 0xFFD7192F, CARD_RED_FOOT = 0xFF8E0B1D,
            CARD_SOFT = 0xFFF6C9CF;
    /** Card's own blue in the middle (as the clubs' results cards have it); red only in added time. */
    static final int CARD_BLUE = 0xFF2A3DC9, CARD_BLUE_FOOT = 0xFF0F1B7E, CARD_BLUE_SOFT = 0xFFC3CBF4;
    /** Card's red moving: its slants drift across it once in SLANT_MS; a shine passes every SHINE_EVERY for SHINE_MS. */
    static final long SLANT_MS = 6_000L, SHINE_EVERY = 9_000L, SHINE_MS = 1_200L, TAB_IN_MS = 400L;

    /** One look's colours. */
    private static final class Palette {
        int ground, comp, score0, score1, clock, ink = INK, scoreInk = INK, clockInk = INK, dim = SOFT;
        boolean strips = true, clubFill, sheen;
        /** Its ground falling to this colour at its foot (0: one colour); a fine light edge round it (Frosted glass). */
        int groundFoot;
        boolean edgeLine;
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
            case CARD:
                // Each crest on white, the score over the clock on blue between them, each club's colour at its end.
                p.ground = CARD_TOP;
                p.comp = 0;
                p.score0 = CARD_BLUE;
                p.score1 = CARD_BLUE_FOOT;
                p.clock = 0;
                p.ink = NAVY;
                p.dim = 0xFF4E5778;
                p.round = 0.08f;
                break;
            case FROSTED:
                // Frosted glass in look: solid (the channel's own scoreboard under it never shows), a silvery frost
                // falling darker to its foot, a sheen across its top, a fine light edge; the crests, the score in
                // lighter frost, the clock: no names.
                p.ground = 0xFF9AA5BA;
                p.groundFoot = 0xFF5D677D;
                p.comp = 0;
                p.score0 = 0x3DFFFFFF;
                p.score1 = 0x1FFFFFFF;
                p.clock = 0x24000000;
                p.dim = 0xFFE3E8F2;
                p.round = 0.3f;
                p.sheen = true;
                p.edgeLine = true;
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
        if (l.stoppage && !look.stoppage) stoppageAt = System.currentTimeMillis();
        // Half time and full time as they come: the board turns over, FULL TIME is stamped on it.
        if ("HT".equals(l.clock) && !"HT".equals(look.clock) && !look.clock.isEmpty()) htAt = System.currentTimeMillis();
        if ("FT".equals(l.clock) && !"FT".equals(look.clock) && !look.clock.isEmpty()) ftAt = System.currentTimeMillis();
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

    /** The net's shiver after the band (from RIPPLE_AT, over RIPPLE_MS); half time turning over; FULL TIME stamped. */
    static final long RIPPLE_AT = BAND_OUT + BAND_MS - 150, RIPPLE_MS = 900, HT_MS = 700, FT_MS = 2600;
    private long htAt, ftAt, varUntil;

    /** VAR is checking: a white line scans back and forth across the board for ms. */
    void varCheck(long ms) {
        varUntil = System.currentTimeMillis() + Math.max(0, ms);
        postInvalidateOnAnimation();
    }

    /** Previews: half time this far into its turn, FULL TIME this far into its stamp, VAR scanning (-1: none). */
    void momentsNow(long ht, long ft, boolean var) {
        long now = System.currentTimeMillis();
        htAt = ht < 0 ? 0 : now - ht;
        ftAt = ft < 0 ? 0 : now - ft;
        varUntil = var ? now + 60_000L : 0;
        invalidate();
    }

    /** Pure: whether the clock's box is red: only in a half's added time, while the game is live (on every look). */
    static boolean pulses(Look l) {
        return l != null && l.live && l.stoppage;
    }

    /** The clock's box red in added time (pulses): a steady red over (x, y, w, h), never flashing. */
    private void pulse(Canvas cv, float x, float y, float w, float h, int red, int least, int most) {
        pulse(cv, x, y, w, h, red, least, most, 0f);
    }

    /** The same with round corners (Minimal's clock, which has no box of its own: a red pill behind it). */
    private void pulse(Canvas cv, float x, float y, float w, float h, int red, int least, int most, float round) {
        if (!pulses(look)) return;
        // Red and staying red: no beat, nothing flashing (least is kept for the looks' call sites, unused).
        fill.setShader(null);
        fill.setColor(red);
        fill.setAlpha(most);
        if (round > 0f) {
            r.set(x, y, x + w, y + h);
            cv.drawRoundRect(r, round, round, fill);
        } else {
            cv.drawRect(x, y, x + w, y + h, fill);
        }
        fill.setAlpha(255);
    }

    /** Pure: a heartbeat's strength at a moment, 0 to 1: two quick beats a second, as a pulse is. */
    static float heartbeat(long t) {
        float x = (t % 1000L) / 1000f;
        float a = (float) Math.exp(-Math.pow((x - 0.12f) / 0.06f, 2)), b = 0.7f * (float) Math.exp(-Math.pow((x - 0.34f) / 0.07f, 2));
        return Math.min(1f, a + b);
    }

    /** The net shivering across the board, t ms in: white mesh lines that bulge and fade, as the ball hits the net. */
    private void netRipple(Canvas cv, int w, int h, long t) {
        // Rings running out from the scorer's end of the board, as the net's shiver after the ball goes in: light, thin,
        // fading as they spread, the score always readable through them.
        float x = Math.max(0f, Math.min(1f, t / (float) RIPPLE_MS));
        float cx = flashSide == 2 ? w * 0.62f : w * 0.16f, cy = h / 2f, far = w * 0.75f;
        edge.setPathEffect(null);
        edge.setColor(0xFFFFFF);
        for (int k = 0; k < 3; k++) {
            float p = x - k * 0.16f;
            if (p <= 0f || p >= 1f) continue;
            float e = 1f - (1f - p) * (1f - p);
            edge.setStrokeWidth(Math.max(1f, h * (0.05f - 0.03f * e)));
            edge.setAlpha(Math.round(110 * (1f - p) * (k == 0 ? 1f : 0.6f)));
            cv.drawCircle(cx, cy, h * 0.3f + far * e, edge);
        }
        edge.setAlpha(255);
        edge.setColor(INK);
        dashFor = -1;
    }

    /** VAR checking: a bright white line sweeping back and forth across the board, a soft glow either side. */
    private void varScan(Canvas cv, int w, int h, long now) {
        float x = (float) ((now % 1600L) / 1600.0), along = x < 0.5f ? x * 2f : 2f - x * 2f;
        float lx = along * w, lw = Math.max(2f, h * 0.06f);
        fill.setShader(new LinearGradient(lx - h * 0.6f, 0, lx + h * 0.6f, 0, new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null,
                Shader.TileMode.CLAMP));
        cv.drawRect(lx - h * 0.6f, 0, lx + h * 0.6f, h, fill);
        fill.setShader(null);
        fill.setColor(0xFFFFFFFF);
        cv.drawRect(lx - lw / 2f, 0, lx + lw / 2f, h, fill);
    }

    /** FULL TIME stamped on the board, t ms in: it lands big and settles, holds, then lifts to show the final score. */
    private void fullTimeStamp(Canvas cv, int w, int h, long t) {
        float in = Math.min(1f, t / 260f), out = t > FT_MS - 400 ? (FT_MS - t) / 400f : 1f;
        float scale = 1f + 1.2f * (1f - in) * (1f - in), a = in * Math.max(0f, out);
        fill.setShader(null);
        fill.setColor(0x000000);
        fill.setAlpha(Math.round(170 * a));
        cv.drawRect(0, 0, w, h, fill);
        fill.setAlpha(255);
        cv.save();
        cv.scale(scale, scale, w / 2f, h / 2f);
        text.setLetterSpacing(0.18f);
        String s = "FULL TIME";
        float size = Math.min(h * 0.5f, w * 0.8f / Math.max(1f, measure(s, 1f) * 1.2f));
        float sw = measure(s, size), pad = h * 0.18f;
        edge.setPathEffect(null);
        edge.setStrokeWidth(Math.max(1.5f, h * 0.05f));
        edge.setColor(0xFFFFFF);
        edge.setAlpha(Math.round(255 * a));
        r.set(w / 2f - sw / 2f - pad, h * 0.16f, w / 2f + sw / 2f + pad, h * 0.84f);
        cv.drawRoundRect(r, h * 0.1f, h * 0.1f, edge);
        edge.setAlpha(255);
        edge.setColor(INK);
        dashFor = -1;
        text.setAlpha(255);
        words(cv, s, w / 2f, 0, h, size, (Math.round(255 * a) << 24) | 0xFFFFFF);
        text.setLetterSpacing(0f);
        cv.restore();
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
        s.light = style == Style.LIGHT || style == Style.CARD;
        s.apart = p.round >= 0.5f;
        s.round = p.round;
        s.block = style == Style.CLUB ? INK : style == Style.LIGHT ? NAVY : style == Style.CARD ? CARD_BLUE
                : p.score0 != 0 && (p.score0 >>> 24) == 0xFF ? p.score0 : 0x40FFFFFF;
        return s;
    }

    // ---- size ------------------------------------------------------------------------------------------------------

    /** Its width for words h high (its edge aside). */
    int natural(int h) {
        // The minutes added stand beside the board at its natural width, in a tab of their own.
        boolean was = addedOutside;
        addedOutside = !look.added.isEmpty();
        float tab = addedTab(h), w;
        if (style == Style.MINIMAL) w = minimalWidth(h);
        else if (style == Style.CRESTS || style == Style.FROSTED) w = h * 0.4f + crestCell(h, true) + scoreW(h) + crestCell(h, false) + clockW(h);
        else if (style == Style.STACKED) w = h * 0.9f + stackedRowsW(h / 2f) + stackedClockW(h);
        else if (style == Style.CARD) w = cardWidth(h);
        else w = h + 2 * (h * 0.1f + crestW(h) + code(h)) + scoreW(h) + clockW(h) + (p.round >= 0.5f ? h * 0.36f : 0f);
        addedOutside = was;
        return Math.round(w + tab);
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
        return measure("88:88".length() >= look.clock.length() ? "88:88" : look.clock, h * 0.44f) + h * 0.6f;
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
            float first = style == Style.CRESTS || style == Style.FROSTED ? ch * 1.25f : style == Style.STACKED ? ch * 0.9f : style == Style.CARD ? ch * 1.29f
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
        float rad = cornerOf(w, h);
        // The minutes added stand in a tab of their own beside it (inside it only on a board kept too narrow).
        int tab = addedTabbed(cw, ch) ? Math.round(addedTab(ch)) : 0, body = Math.max(1, w - tab);
        addedOutside = tab > 0;
        drawNow = now;
        r.set(0, 0, body, h);
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        fill.setShader(null);
        fill.setColor(p.ground);
        if (p.groundFoot != 0) fill.setShader(new LinearGradient(0, 0, 0, h, p.ground, p.groundFoot, Shader.TileMode.CLAMP));
        cv.drawRect(r, fill);
        fill.setShader(null);
        if (p.sheen) sheen(cv, w, h);
        // Half time comes in as the board turns over, a card shown its other face (HT_MS).
        long ht = htAt > 0 ? now - htAt : -1;
        boolean turning = ht >= 0 && ht < HT_MS;
        if (turning) {
            float f = Math.abs((float) Math.cos(Math.PI * ht / HT_MS));
            cv.scale(1f, Math.max(0.04f, f), 0, h / 2f);
        }
        cv.translate(inset, inset);
        int bw = cw - tab;
        if (bw > 0 && ch > 0) {
            if (style == Style.MINIMAL) minimal(cv, bw, ch);
            else if (style == Style.CRESTS || style == Style.FROSTED) {
                if (bw / (float) ch >= 3.2f) crestsRow(cv, bw, ch);
                else crestsTwo(cv, bw, ch);
            } else if (style == Style.STACKED) {
                if (bw / (float) ch >= 1.6f) stacked(cv, bw, ch);
                else stackedTall(cv, bw, ch);
            }
            else if (style == Style.CARD) {
                if (bw / (float) ch >= 2.0f) card(cv, bw, ch, now);
                else cardTall(cv, bw, ch, now);
            }
            else if (bw / (float) ch >= 3.6f) oneRow(cv, 0, 0, bw, ch);
            else twoRows(cv, bw, ch);
            // In added time a board with no block of its own (Minimal) has the shine pass over it all.
        }
        cv.translate(-inset, -inset);
        if (goalOn) goalMoment(cv, body, h, gt);
        // The net shivering across it as the ball goes in, once the scorers' band has gone.
        if (gt >= RIPPLE_AT && gt < RIPPLE_AT + RIPPLE_MS) netRipple(cv, body, h, gt - RIPPLE_AT);
        if (varUntil > now) varScan(cv, body, h, now);
        long ft = ftAt > 0 ? now - ftAt : -1;
        if (ft >= 0 && ft < FT_MS) fullTimeStamp(cv, body, h, ft);
        if (p.edgeLine) {
            float e = Math.max(1f, h * 0.025f);
            edge.setPathEffect(null);
            edge.setStrokeWidth(e);
            edge.setColor(0x8CFFFFFF);
            r.set(e / 2, e / 2, body - e / 2, h - e / 2);
            cv.drawRoundRect(r, rad, rad, edge);
            edge.setColor(INK);
            dashFor = -1;
        }
        cv.restore();
        boolean moving = turning || (gt >= RIPPLE_AT && gt < RIPPLE_AT + RIPPLE_MS) || varUntil > now || (ft >= 0 && ft < FT_MS);
        if (moving && !part) postInvalidateOnAnimation();
        if (tab > 0) addedTabDraw(cv, body, inset, ch, tab, now);
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
        // Added time is red and still: nothing moves on it then, so nothing to draw again but the goal's sweep.
        if (goalOn && goalFrozen < 0) postInvalidateOnAnimation();
    }

    /** Its corners' radius: a pill's half its height while it is long, else its look's own (Glass nearer square: round corners). */
    private float cornerOf(float w, float h) {
        if (p.round < 0.5f) return h * p.round;
        return w / h >= 2.4f ? h / 2f : Math.min(h * 0.22f, w * 0.12f);
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
        float k = need > room ? w / need : 1f;
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
        // Card: its blue, turning red in added time.
        boolean red = style == Style.CARD && look.stoppage;
        int a = red ? CARD_RED : p.score0, b = red ? CARD_RED_FOOT : p.score1;
        if (scoreShade == null || shadeY != y || shadeH != h || shadeA != a || shadeB != b) {
            scoreShade = new LinearGradient(0, y, 0, y + h, a, b, Shader.TileMode.CLAMP);
            shadeY = y;
            shadeH = h;
            shadeA = a;
            shadeB = b;
        }
        fill.setShader(scoreShade);
        cv.drawRect(x, y, x + w, y + h, fill);
        fill.setShader(null);
        // In added time it moves as Card's red does (Card's own always moves).
        if (look.stoppage && style != Style.CARD) slants(cv, x, y, w, Math.round(h), stoppageT());
    }

    /** How far into added time it is, for its movement (frozen for previews); its first shine as it begins. */
    private long stoppageT() {
        return animFrozen >= 0 ? animFrozen : Math.max(0, drawNow - stoppageAt);
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
        // In added time: the clock's box red, steady.
        pulse(cv, x, y, w, h, 0xFFE3061B, 60, 210);
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? p.clockInk : p.dim;
        if (look.added.isEmpty() || addedOutside) {
            float size = h * 0.44f * k;
            words(cv, look.clock, x + w / 2, y, h, size * fitIn(look.clock, size, w * 0.9f), colour);
            return;
        }
        float cs = h * 0.44f * k, as = h * 0.34f * k, pad = h * 0.3f * k, gap = h * 0.15f * k;
        float cw = measure(look.clock, cs), aw = measure(look.added, as) + pad;
        // Too wide for its box (a board kept narrow): both smaller together, never over each other or off it.
        float f = Math.min(1f, w * 0.92f / Math.max(1f, cw + gap + aw));
        cw *= f;
        aw *= f;
        gap *= f;
        float start = x + (w - cw - aw - gap) / 2;
        words(cv, look.clock, start + cw / 2, y, h, cs * f, colour);
        float ax = start + cw + gap;
        fill.setColor(ADDED);
        r.set(ax, y + h * 0.24f, ax + aw, y + h * 0.76f);
        cv.drawRoundRect(r, h * 0.08f, h * 0.08f, fill);
        words(cv, look.added, ax + aw / 2, y, h, as * f, INK);
    }

    /** How much smaller words at size must be to fit in room (1: they fit). */
    private float fitIn(String s, float size, float room) {
        float wd = measure(s, size);
        return wd <= room || wd <= 0 ? 1f : room / wd;
    }

    /** Nearer square: the clubs and the score above, the competition and the clock below. */
    private void twoRows(Canvas cv, int w, int h) {
        float top = h * 0.58f;
        clubsRow(cv, w, top);
        float y = top, bh = h - top;
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(0, y, w, h, fill);
        // The competition beside the clock only where there is room for both, else the clock across it all.
        boolean mark = bh * 1.05f <= w * 0.3f;
        if (mark && comp != null) drawImage(cv, comp, bh * 0.18f, y + bh * 0.12f, bh * 0.76f);
        else if (mark && !look.tag.isEmpty()) words(cv, look.tag, w * 0.15f, y, bh, Math.min(bh * 0.42f, w * 0.1f), p.dim);
        clock(cv, mark ? w * 0.3f : 0f, y, mark ? w * 0.7f : w, bh, 1.25f);
    }

    private void clubsRow(Canvas cv, int w, float h) {
        // Without the competition's block (it moves below): the clubs and the score across the width.
        float codeW = code(Math.round(h)), strip = h * 0.1f, crestW = crestW(h), score = scoreW(Math.round(h));
        float need = 2 * (strip + crestW + codeW) + score;
        boolean withCrests = need <= w + 1.5f;
        if (!withCrests) need -= 2 * crestW;
        float k = need > w + 1.5f ? w / need : 1f;
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
        float k = need > w + 1.5f ? w / need : 1f;
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
        float need = hc + score + ac, k = need > w + 1.5f ? w / need : 1f;
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
        return c + h * 0.5f;
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
    /**
     * Stacked in a box nearer square (kept narrow): the two clubs one above the other across it, the clock (and the
     * minutes added) along its foot, so nothing is squeezed past reading.
     */
    private void stackedTall(Canvas cv, int w, int h) {
        float rows = h * 0.64f, r = rows / 2f, strip = r * 0.12f, crest = crests ? r * 0.95f : 0, goalsW = r * 1.15f;
        float codeW = code(Math.round(r));
        float need = strip + crest + codeW + goalsW, k = need > w + 1.5f ? w / need : 1f;
        String[] g = goals();
        stackedRow(cv, 0, 0, w, r, strip * k, crest * k, goalsW * k, true, g[0], k);
        stackedRow(cv, 0, r, w, r, strip * k, crest * k, goalsW * k, false, g[1], k);
        fill.setColor(0x26FFFFFF);
        float line = Math.max(1f, h * 0.008f);
        cv.drawRect(strip * k, r - line, w, r + line, fill);
        float fy = rows, fh = h - rows;
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(0, fy, w, h, fill);
        if (!look.clock.isEmpty()) clock(cv, 0, fy, w, fh, 1.2f);
    }

    private void stacked(Canvas cv, int w, int h) {
        float r = h / 2f, comp = h * 0.9f, strip = r * 0.12f, crest = crests ? r * 0.95f : 0, codeW = code(Math.round(r));
        float goalsW = r * 1.15f, clock = stackedClockW(h);
        float need = comp + strip + crest + codeW + goalsW + clock;
        boolean compBlock = true;
        if (need > w + 1.5f) {
            compBlock = false;
            need -= comp;
        }
        float k = need > w + 1.5f ? w / need : 1f;
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
        if (look.stoppage) slants(cv, gx, y, x + w - gx, Math.round(r), stoppageT());
        words(cv, goals, gx + goalsW / 2, y, r, r * 0.62f * k * (scored ? scoreZoom : 1f), p.scoreInk);
    }

    /** The clock's column: minutes and seconds, the minutes added on red under them. */
    private void stackedClock(Canvas cv, float x, float w, float h, float k) {
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(x, 0, x + w, h, fill);
        pulse(cv, x, 0, w, h, 0xFFE3061B, 60, 210);
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? p.clockInk : p.dim;
        float cs = h * 0.3f * k;
        cs *= fitIn(look.clock, cs, w * 0.88f);
        if (look.added.isEmpty() || addedOutside) {
            words(cv, look.clock, x + w / 2, 0, h, cs, colour);
            return;
        }
        words(cv, look.clock, x + w / 2, h * 0.08f, h * 0.5f, cs, colour);
        float aw = Math.min(w * 0.88f, measure(look.added, h * 0.22f * k) + h * 0.3f * k);
        fill.setColor(ADDED);
        r.set(x + (w - aw) / 2, h * 0.6f, x + (w + aw) / 2, h * 0.86f);
        cv.drawRoundRect(r, h * 0.05f, h * 0.05f, fill);
        words(cv, look.added, x + w / 2, h * 0.6f, h * 0.26f, h * 0.22f * k, INK);
    }

    // ---- Card ------------------------------------------------------------------------------------------------------

    private LinearGradient cardShade, shineShade;
    private float cardShadeH = -1, shineH = -1;
    private final Path slant = new Path();
    private final Matrix shineMove = new Matrix();
    /** Previews: Card's red drawn this far into its movement (-1: as the time goes). */
    private long animFrozen = -1;
    /** When the minutes added first showed (its tab comes in), and what they were. */
    private long tabAt;
    private String tabFor = "";
    /** When added time began (its movement counts from then), the time this frame is drawn at, and whether the minutes added stand beside it this frame. */
    private long stoppageAt, drawNow;
    private boolean addedOutside;

    /** Previews and tests: Card's red drawn t ms into its movement (-1: as the time goes). */
    void animAt(long t) {
        animFrozen = t;
        invalidate();
    }

    /** "2 - 1", spaced as the results cards set it ("v" before kick-off). */
    private String cardScore() {
        String[] g = goals();
        return g[0].isEmpty() ? look.score : g[0] + " - " + g[1];
    }

    /** The clock under the score: minutes and seconds, Half-Time and Full-Time in words, the kick-off's time before it. */
    private String cardClock() {
        if ("HT".equals(look.clock)) return "Half-Time";
        if ("FT".equals(look.clock)) return "Full-Time";
        return look.clock;
    }

    /** The clock's size: words a little smaller than figures. */
    private float cardClockSize(int h) {
        return cardClock().length() > 6 ? h * 0.27f : h * 0.36f;
    }

    private float cardClockW(int h) {
        String c = cardClock();
        return c.isEmpty() ? 0 : measure(c, cardClockSize(h));
    }

    /** The middle: as wide as the score or the clock needs, never narrower than a crest's panel. */
    private float cardMid(int h) {
        return Math.max(h * 1.35f, Math.max(measure(cardScore(), h * 0.46f), cardClockW(h)) + h * 0.42f);
    }

    private float cardWidth(int h) {
        return 2 * (h * 0.09f + h * 1.2f) + cardMid(h);
    }

    /** Whether the minutes added stand beside the board: there are some, and the board keeps room for itself beside them. */
    private boolean addedTabbed(int w, int h) {
        return !look.added.isEmpty() && w - addedTab(h) >= h * (style == Style.CARD ? 2.6f : 2f);
    }

    /** The tab's height: half the board's beside a tall one (Stacked, Card), else most of it. */
    private float tabHeight(int h) {
        return style.tall > 1.2f ? h * 0.5f : h * 0.8f;
    }

    /** The room the minutes added take beside the board ("+4" in a tab of their own, a little apart); none without them. */
    private float addedTab(int h) {
        if (look.added.isEmpty()) return 0;
        float th = tabHeight(h);
        return h * 0.06f + Math.max(th * 1.15f, measure(look.added, th * 0.6f) + th * 0.5f);
    }

    /**
     * Card: each club's crest on a white panel with its colour down the board's end, and between them a red block with
     * the score over the clock, its light slants drifting across it and a shine passing now and then.
     */
    private void card(Canvas cv, int w, int h, long now) {
        float strip = h * 0.09f, panel = h * 1.2f, mid = cardMid(h);
        float need = 2 * (strip + panel) + mid;
        float k = need > w + 1.5f ? w / need : 1f;
        float extra = Math.max(0, w - need * k);
        strip *= k;
        panel = panel * k + extra / 4f;
        mid = mid * k + extra / 2f;
        if (cardShade == null || cardShadeH != h) {
            cardShade = new LinearGradient(0, 0, 0, h, CARD_TOP, CARD_FOOT, Shader.TileMode.CLAMP);
            cardShadeH = h;
        }
        fill.setShader(cardShade);
        cv.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        cardSide(cv, 0, strip, panel, h, true);
        cardSide(cv, w - strip - panel, strip, panel, h, false);
        float mx = strip + panel;
        scoreBlock(cv, mx, 0, mid, h);
        // Moving only in added time, as every look does then (red then too).
        if (look.stoppage) slants(cv, mx, 0, mid, h, stoppageT());
        // The score, popping up big after a goal but never past the red.
        String sc = cardScore();
        float ss = h * 0.46f * k * scoreZoom, sw = measure(sc, ss);
        if (sw > mid * 0.94f) ss *= mid * 0.94f / sw;
        words(cv, sc, mx + mid / 2f, h * 0.04f, h * 0.5f, ss, p.scoreInk);
        cardClockLine(cv, mx, h * 0.5f, mid, h * 0.44f, cardClockSize(h) * k, h * 0.24f * k);
    }

    /**
     * Card in a box nearer square (kept narrow): both crests on the white across its top, each club's colour along its
     * foot, and the score over the clock on the red below, so nothing is squeezed past reading.
     */
    private void cardTall(Canvas cv, int w, int h, long now) {
        float top = h * 0.46f, half = w / 2f, line = Math.max(2f, h * 0.035f);
        fill.setShader(null);
        fill.setColor(CARD_TOP);
        cv.drawRect(0, 0, w, top, fill);
        for (int i = 0; i < 2; i++) {
            boolean home = i == 0;
            int own = home ? look.homeOwn : look.awayOwn, colour = own != 0 ? own : home ? look.homeColour : look.awayColour;
            float x = home ? 0 : half;
            boolean lit = flash > 0 && flashSide == (home ? 1 : 2);
            if (lit) {
                fill.setColor(colour);
                fill.setAlpha(Math.round(200 * flash));
                cv.drawRect(x, 0, x + half, top, fill);
                fill.setAlpha(255);
            }
            fill.setColor(colour);
            cv.drawRect(x, top - line, x + half, top, fill);
            Bitmap crest = home ? homeCrest : awayCrest;
            float size = Math.min(top * 0.74f, half * 0.78f);
            if (crest != null) drawImage(cv, crest, x + (half - size) / 2f, (top - line - size) / 2f, size);
            else words(cv, home ? look.home : look.away, x + half / 2f, 0, top - line, Math.min(top * 0.4f, half * 0.28f), NAVY);
        }
        float rh = h - top;
        scoreBlock(cv, 0, top, w, rh);
        if (look.stoppage) slants(cv, 0, top, w, Math.round(rh), stoppageT());
        String sc = cardScore();
        float ss = rh * 0.5f * scoreZoom;
        ss *= Math.min(1f, w * 0.9f / Math.max(1f, measure(sc, ss)));
        words(cv, sc, w / 2f, top, rh * 0.56f, ss, p.scoreInk);
        float cs = rh * (cardClock().length() > 6 ? 0.28f : 0.34f);
        cardClockLine(cv, 0, top + rh * 0.52f, w, rh * 0.44f, cs, rh * 0.24f);
    }

    /** One club's end (x: where its part starts): its colour down the board's edge, its crest on the white (its letters until the crest comes); lit as it scores. */
    private void cardSide(Canvas cv, float x, float strip, float panel, int h, boolean home) {
        int own = home ? look.homeOwn : look.awayOwn, colour = own != 0 ? own : home ? look.homeColour : look.awayColour;
        float px = home ? x + strip : x, sx = home ? x : x + panel;
        boolean lit = flash > 0 && flashSide == (home ? 1 : 2);
        if (lit) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(px, 0, px + panel, h, fill);
            fill.setAlpha(255);
        }
        fill.setColor(colour);
        cv.drawRect(sx, 0, sx + strip, h, fill);
        Bitmap crest = home ? homeCrest : awayCrest;
        float size = Math.min(panel * 0.8f, h * 0.76f);
        if (crest != null) drawImage(cv, crest, px + (panel - size) / 2f, (h - size) / 2f, size);
        else words(cv, home ? look.home : look.away, px + panel / 2f, 0, h, Math.min(h * 0.42f, panel * 0.3f),
                lit ? ink(blend(CARD_TOP, colour, 200 * flash / 255f)) : NAVY);
    }

    /** Pure: how far the slants have drifted t ms in, as a share of their spacing (0 to 1, round again). */
    static float drift(long t) {
        return (((t % SLANT_MS) + SLANT_MS) % SLANT_MS) / (float) SLANT_MS;
    }

    /** Pure: where the shine is t ms in, 0 to 1 across the red while it passes; -1 between its passes. */
    static float shineAt(long t) {
        long in = ((t % SHINE_EVERY) + SHINE_EVERY) % SHINE_EVERY;
        return in >= SHINE_MS ? -1f : ease(in / (float) SHINE_MS);
    }

    /** Card's red (x, y: where the red starts): its lighter slants across it, standing still. */
    private void slants(Canvas cv, float x, float y, float w, int h, long t) {
        cv.save();
        cv.clipRect(x, y, x + w, y + h);
        cv.translate(0, y);
        float spacing = Math.max(h * 0.9f, w * 0.62f), band = spacing * 0.42f, lean = h * 0.7f;
        // Still: the slants stand where they are (they drifted and shone before; added time is steady red now).
        float shift = 0f;
        fill.setShader(null);
        fill.setColor(0x22FFFFFF);
        slant.reset();
        for (float x0 = x - spacing + shift; x0 < x + w + lean; x0 += spacing) {
            slant.moveTo(x0 + lean, 0);
            slant.lineTo(x0 + lean + band, 0);
            slant.lineTo(x0 + band, h);
            slant.lineTo(x0, h);
            slant.close();
        }
        cv.drawPath(slant, fill);
        cv.restore();
    }

    /**
     * The clock in its line (x, y, w, lh) at size: white while the game runs (Half-Time and Full-Time too); the minutes
     * added stand beside the board, or, a board kept too narrow for that, beside the clock at addedSize, both fitting.
     */
    private void cardClockLine(Canvas cv, float x, float y, float w, float lh, float size, float addedSize) {
        String c = cardClock();
        if (c.isEmpty()) return;
        // In added time the middle is red already: its clock's line beats a lighter red over it.
        pulse(cv, x, y, w, lh, 0xFFFF5A6A, 0, 150);
        // White on the blue (or red) while the game runs, at half time and at full time (amber would not read on it).
        int colour = look.live || "HT".equals(look.clock) || "FT".equals(look.clock) ? p.clockInk : look.stoppage ? CARD_SOFT : CARD_BLUE_SOFT;
        if (look.added.isEmpty() || addedOutside) {
            float s = Math.min(1f, w * 0.9f / Math.max(1f, measure(c, size)));
            words(cv, c, x + w / 2f, y, lh, size * s, colour);
            return;
        }
        float as = addedSize, cw = measure(c, size), aw = measure(look.added, as) + as * 0.9f, gap = as * 0.33f;
        float f = Math.min(1f, w * 0.9f / Math.max(1f, cw + gap + aw));
        cw *= f;
        aw *= f;
        gap *= f;
        float start = x + (w - cw - gap - aw) / 2f;
        words(cv, c, start + cw / 2f, y, lh, size * f, colour);
        float ax = start + cw + gap;
        fill.setShader(null);
        fill.setColor(0xFFFFFFFF);
        r.set(ax, y + lh * 0.22f, ax + aw, y + lh * 0.78f);
        cv.drawRoundRect(r, lh * 0.12f, lh * 0.12f, fill);
        words(cv, look.added, ax + aw / 2f, y, lh, as * f, CARD_RED);
    }

    /**
     * The minutes added in a tab of their own beside the board (body: where the board ends; ch: its height inside its
     * edge): red with "+4" in white, level with a tall board's foot or across the middle of a long one, a pill beside
     * Glass. The first time it shows it slides out from behind the board with a white flash.
     */
    private void addedTabDraw(Canvas cv, int body, int edgePx, int ch, int tab, long now) {
        if (!look.added.equals(tabFor)) {
            tabFor = look.added;
            tabAt = now;
        }
        long since = animFrozen >= 0 ? 10_000L : now - tabAt;
        float in = Math.min(1f, since / (float) TAB_IN_MS);
        float gap = ch * 0.06f, tw = tab - gap, th = tabHeight(ch);
        float left = body + gap - (1f - ease(in)) * (tw + gap);
        float top = style.tall > 1.2f ? edgePx + ch - th : edgePx + (ch - th) / 2f;
        float rad = p.round >= 0.5f ? th / 2f : th * 0.12f;
        cv.save();
        // Out from behind the board.
        cv.clipRect(body, 0, getWidth(), getHeight());
        fill.setShader(null);
        fill.setColor(style == Style.CARD ? CARD_RED : ADDED);
        r.set(left, top, left + tw, top + th);
        cv.drawRoundRect(r, rad, rad, fill);
        words(cv, look.added, left + tw / 2f, top, th, th * 0.6f, INK);
        cv.restore();
        // Out from behind the board, then still (no flash as it lands).
        if (since < TAB_IN_MS) postInvalidateOnAnimation();
    }

    /** A shine passing across (x, y, w, h), t ms into its round (none between its passes). */
    private void shine(Canvas cv, float x, float y, float w, float h, long t) {
        float s = shineAt(t);
        if (s < 0f) return;
        cv.save();
        cv.clipRect(x, y, x + w, y + h);
        cv.translate(0, y);
        float sw = h * 0.6f;
        if (shineShade == null || shineH != sw) {
            shineShade = new LinearGradient(0, 0, sw, 0, new int[]{0x00FFFFFF, 0x4CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            shineH = sw;
        }
        shineMove.setSkew(-0.45f, 0f);
        shineMove.postTranslate(x - sw + (w + sw + h * 0.7f) * s, 0f);
        shineShade.setLocalMatrix(shineMove);
        fill.setShader(shineShade);
        cv.drawRect(x, 0, x + w, h, fill);
        fill.setShader(null);
        cv.restore();
    }

    // ---- Minimal ---------------------------------------------------------------------------------------------------

    /** Minimal: one line, "LIV  2–1  MUN   67:23 +2". */
    private String[] minimalParts() {
        return new String[]{look.home + "  " + look.score + "  " + look.away, look.clock, addedOutside ? "" : look.added};
    }

    private float minimalWidth(int h) {
        String[] m = minimalParts();
        float w = h * 0.9f + measure(m[0], h * 0.48f) + measure(m[1], h * 0.44f) + h * 0.7f;
        if (!m[2].isEmpty() && !addedOutside) w += measure(m[2], h * 0.38f) + h * 0.5f;
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
            // In added time a red pill beats behind its clock.
            float uw = measure(under, bh * 0.62f * k2), pad = bh * 0.22f;
            pulse(cv, w / 2f - uw / 2f - pad, top - h * 0.04f + bh * 0.12f, uw + 2 * pad, bh * 0.76f, 0xFFE3061B, 70, 220, bh * 0.38f);
            words(cv, under, w / 2f, top - h * 0.04f, bh, bh * 0.62f * k2, clockInk);
            return;
        }
        float natural = minimalWidth(h), k = natural > w + 1.5f ? w / natural : 1f;
        float x = (w - natural * k) / 2 + h * 0.45f * k;
        float a = measure(m[0], h * 0.48f * k);
        words(cv, m[0], x + a / 2, 0, h, h * 0.48f * k, p.ink);
        x += a + h * 0.7f * k;
        float b = measure(m[1], h * 0.44f * k);
        // In added time a red pill beats behind its clock.
        pulse(cv, x - h * 0.2f * k, h * 0.2f, b + h * 0.4f * k, h * 0.6f, 0xFFE3061B, 70, 220, h * 0.3f);
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

    /**
     * Pure: a club's colour that shows on white (Card's ends, the white strips): its own, else its second when the first
     * is too light, else a grey. 0 when it has none.
     */
    static int onWhite(int first, int second) {
        if (first == 0 && second == 0) return 0;
        int c = 0xFF000000 | first, a = 0xFF000000 | second;
        if (first != 0 && !light(c)) return c;
        if (second != 0 && !light(a)) return a;
        return 0xFF8A93A8;
    }

    private static boolean light(int c) {
        int rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 > 200;
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
