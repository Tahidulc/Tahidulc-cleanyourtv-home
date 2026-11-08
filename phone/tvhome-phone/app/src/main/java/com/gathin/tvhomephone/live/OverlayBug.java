package com.gathin.tvhomephone.live;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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
 * their teams' colours. A goal is shown as broadcasters show one: a white hit, the scorers' colour sweeping across it
 * from their side with their crest and GOAL, then on out the far side, the new score popping up and settling.
 * What folds out under it (the stats, the alerts) takes its look (StripLook). Card sets it as the clubs' own results
 * cards are: each crest on white with the club's colour down the board's end, the score over the clock on red between,
 * lighter slants drifting across the red and a shine sweeping over it now and then. In added time every look's score
 * comes alive the same way (Minimal, with no block, has the shine across it all), and the minutes added stand in a red
 * tab of their own beside the board, sliding out from behind it; back beside the clock when the board is kept too narrow.
 */
final class OverlayBug extends View implements BurnGuard.Dims {
    /** What it shows. */
    static final class Look {
        boolean f1, live;
        /** In added time: the clock past its period's end, as the board's controller (ScoreBoard) decides it. */
        boolean stoppage;
        /** The last minutes of a close game (one goal or level, from the 85th): its clock beats red. */
        boolean late;
        String home = "", away = "", score = "", clock = "", added = "", tag = "";
        int homeColour = 0xFFFFFFFF, awayColour = 0xFFFFFFFF;
        /** F1: the session, the corner ({"LAP", "23"}, {"Q2", "08:31"}), the first drivers' codes and colours. */
        String label = "", cornerA = "", cornerB = "";
        final List<String> codes = new ArrayList<>();
        final List<Integer> colours = new ArrayList<>();

        @Override public boolean equals(Object o) {
            if (!(o instanceof Look)) return false;
            Look l = (Look) o;
            return f1 == l.f1 && live == l.live && stoppage == l.stoppage && late == l.late && home.equals(l.home) && away.equals(l.away)
                    && score.equals(l.score)
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
    /**
     * Card's white (a soft one, shaded to a light grey at its foot: it stays up all game), its red middle (darker at its
     * foot), the kick-off's time on the red before kick-off, and Half-Time on it.
     */
    static final int CARD_TOP = 0xFFF1F3F7, CARD_FOOT = 0xFFD3D8E1, CARD_RED = 0xFFD7192F, CARD_RED_FOOT = 0xFF8E0B1D,
            CARD_SOFT = 0xFFF6D3D8, CARD_HT = 0xFFFFE7A3;
    /**
     * Card's red, and every look's score in added time, alive: its slants drift a slant's period every DRIFT_MS; a shine
     * sweeps it over SHINE_MS every SHINE_EVERY_MS (in added time the first as it begins).
     */
    static final long DRIFT_MS = 6_000L, SHINE_MS = 1_200L, SHINE_EVERY_MS = 9_000L;
    /** The added time's tab beside the board (Card's in its own red): out over TAB_IN_MS, lit white at first over TAB_FLASH_MS. */
    static final int TAB_RED = 0xFFC81E34;
    static final long TAB_IN_MS = 400L, TAB_FLASH_MS = 600L;
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
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient shineShader = new LinearGradient(0, 0, 1, 0,
            new int[]{0x00FFFFFF, 0x5CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    private final Matrix shineAt = new Matrix();

    OverlayBug(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        blueTop = c.getColor(R.color.bug_blue_top);
        blueBottom = c.getColor(R.color.bug_blue_bottom);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setFontFeatureSettings("tnum");
        setLayerType(LAYER_TYPE_HARDWARE, null);
        p = palette(style, blueTop, blueBottom);
        shine.setShader(shineShader);
    }

    /** How it looks: the choices in Settings and the player's options. */
    enum Style {
        CLASSIC("Classic"), GLASS("Glass"), LIGHT("Light"), CLUB("Club colours"), MINIMAL("Minimal"),
        CRESTS("Crests and time"), STACKED("Stacked", 1.7f), CARD("Card", 1.6f), FROSTED("Frosted glass");

        final String label;
        /** How much taller than the others it is drawn in its own corner (Stacked: the clubs one above the other; Card: big crests). */
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
        /** Its ground falling to this colour at its foot (0: one colour); a sheen across its top and a fine light edge (Frosted glass). */
        int groundFoot;
        boolean sheen, edgeLine;
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
            case CARD:
                // Each crest on white, the score over the clock on red between them, each club's colour at its end.
                p.ground = CARD_TOP;
                p.comp = 0;
                p.score0 = CARD_RED;
                p.score1 = CARD_RED_FOOT;
                p.clock = 0;
                p.ink = NAVY;
                p.dim = 0xFF4E5778;
                p.round = 0.08f;
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
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        int h = lp != null && lp.height > 0 ? lp.height : getHeight() > 0 ? getHeight() : Math.round(30 * dp);
        int before = natural(h);
        long now = android.os.SystemClock.uptimeMillis();
        if (look.added.isEmpty() && !l.added.isEmpty()) tabAt = now;
        if (!look.stoppage && l.stoppage) stoppageAt = now;
        // Half time and full time as they come: the board turns over, FULL TIME is stamped on it.
        long wall = System.currentTimeMillis();
        if ("HT".equals(l.clock) && !"HT".equals(look.clock) && !look.clock.isEmpty()) htAt = wall;
        if ("FT".equals(l.clock) && !"FT".equals(look.clock) && !look.clock.isEmpty()) ftAt = wall;
        look = l;
        // A board of a width set (over the channel's own, or as the family sized it): grown by the tab beside it while
        // there is one, its own part as it was.
        if (lp != null && lp.width > 0) {
            int w = withTab(Math.max(1, lp.width - lpTab), h);
            if (w != lp.width) {
                lp.width = w;
                setLayoutParams(lp);
            }
        }
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

    /** Pure: a heartbeat's strength at a moment, 0 to 1: two quick beats a second, as a pulse is. */
    static float heartbeat(long t) {
        float x = (t % 1000L) / 1000f;
        float a = (float) Math.exp(-Math.pow((x - 0.12f) / 0.06f, 2)), b = 0.7f * (float) Math.exp(-Math.pow((x - 0.34f) / 0.07f, 2));
        return Math.min(1f, a + b);
    }

    /** Rings running out from the scorer's end of the board, t ms in, as the net's shiver: light, thin, fading as they spread. */
    private void netRipple(Canvas cv, int w, int h, long t) {
        float x = Math.max(0f, Math.min(1f, t / (float) RIPPLE_MS));
        float cx = flashSide == 2 ? w * 0.62f : w * 0.16f, cy = h / 2f, far = w * 0.75f;
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0xFFFFFFFF);
        for (int k = 0; k < 3; k++) {
            float p = x - k * 0.16f;
            if (p <= 0f || p >= 1f) continue;
            float e = 1f - (1f - p) * (1f - p);
            edge.setStrokeWidth(Math.max(1f, h * (0.05f - 0.03f * e)));
            edge.setAlpha(Math.round(110 * (1f - p) * (k == 0 ? 1f : 0.6f)));
            cv.drawCircle(cx, cy, h * 0.3f + far * e, edge);
        }
        edge.setAlpha(255);
        edge.setStyle(Paint.Style.FILL);
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
        fill.setColor(0xFF000000);
        fill.setAlpha(Math.round(170 * a));
        cv.drawRect(0, 0, w, h, fill);
        fill.setAlpha(255);
        cv.save();
        cv.scale(scale, scale, w / 2f, h / 2f);
        text.setLetterSpacing(0.18f);
        String s = "FULL TIME";
        float size = Math.min(h * 0.5f, w * 0.8f / Math.max(1f, measure(s, 1f) * 1.2f));
        float sw = measure(s, size), pad = h * 0.18f;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1.5f, h * 0.05f));
        edge.setColor(0xFFFFFFFF);
        edge.setAlpha(Math.round(255 * a));
        r.set(w / 2f - sw / 2f - pad, h * 0.16f, w / 2f + sw / 2f + pad, h * 0.84f);
        cv.drawRoundRect(r, h * 0.1f, h * 0.1f, edge);
        edge.setAlpha(255);
        edge.setStyle(Paint.Style.FILL);
        words(cv, s, w / 2f, 0, h, size, (Math.round(255 * a) << 24) | 0xFFFFFF);
        text.setLetterSpacing(0f);
        cv.restore();
    }

    /** Frosted glass's sheen: light across its top, gone by its foot. */
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

    private LinearGradient sheenShade;
    private int sheenH;

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

    // ---- what folds out under it ------------------------------------------------------------------------------------

    /** What folds out under it (the stats, the alerts), in its look. */
    static final class StripLook {
        /** Its ground (the board's, made solid enough for words), its words, its quieter words, a chip's block. */
        int ground = 0xF20B1033, ink = 0xFFFFFFFF, dim = DIM, block = 0xFF1F3AA8;
        /** A light ground (Light): dark words, the clubs' colours as they are. */
        boolean light;
        /** Under a pill (Glass, Minimal): it stands a little apart, round all round, as a pill of its own. */
        boolean apart;
        /** Its corners' radius as a share of its height (the board's own), never more than 8 dp. */
        float round = 0.2f;
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
        s.ground = style == Style.GLASS ? 0xEB0B0F24 : style == Style.MINIMAL ? 0xF0000000 : 0xFF000000 | p.ground;
        s.ink = p.ink;
        s.dim = p.dim;
        s.light = style == Style.LIGHT || style == Style.CARD;
        s.apart = p.round >= 0.5f;
        s.round = p.round;
        s.block = style == Style.CLUB ? 0xFFFFFFFF : style == Style.LIGHT ? NAVY : style == Style.CARD ? CARD_RED
                : p.score0 != 0 && (p.score0 >>> 24) == 0xFF ? p.score0 : 0x40FFFFFF;
        return s;
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

    /** Its width on its own at height h: its own part, and the added time's tab beside it while there is one. */
    int natural(int h) {
        boolean in = addedIn;
        addedIn = false;
        try {
            return ownNatural(h) + tabRoom(h);
        } finally {
            addedIn = in;
        }
    }

    /** Its own part's width at height h, the minutes added inside beside the clock only when addedIn. */
    private int ownNatural(int h) {
        text.setTextSize(h * 0.46f);
        if (style == Style.MINIMAL) return Math.round(minimalWidth(h));
        if (look.f1) {
            float w = h * 1.3f + label(h) + corner(h) + h * 0.3f;
            for (String c : look.codes) w += h * 0.16f + measure(c, h * 0.44f) + h * 0.5f;
            return Math.round(w);
        }
        if (style == Style.CRESTS || style == Style.FROSTED) return Math.round(crestsWidth(h));
        if (style == Style.STACKED) return Math.round(stackedWidth(h));
        if (style == Style.CARD) return Math.round(cardWidth(h));
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
                + (addedInside() ? measure(look.added, h * 0.34f) + h * 0.45f : 0);
    }

    /** The minutes added drawn inside beside the clock: only when the board is too narrow for their tab beside it. */
    private boolean addedIn;

    private boolean addedInside() {
        return addedIn && !look.added.isEmpty();
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
            float first = look.f1 ? h * 1.3f : style == Style.CRESTS || style == Style.FROSTED ? h * 1.25f : style == Style.STACKED ? h * 0.9f
                    : style == Style.CARD ? h * 1.29f
                    : style == Style.MINIMAL ? h * 1.2f : h;
            in = shownPart(shown, w, h, Math.min(first, w / 3f));
            cv.save();
            cv.clipRect(in[0], in[1], in[2], in[3]);
        }
        long now = System.currentTimeMillis();
        long gt = goalFrozen >= 0 ? goalFrozen : goalAt > 0 ? now - goalAt : -1;
        boolean goalOn = gt >= 0 && gt < GOAL_MS;
        flash = goalOn ? glow(gt) : 0f;
        // The new score pops up big as the band goes, and settles.
        scoreZoom = 1f + 0.38f * flash * flash;
        clip.reset();
        float rad = p.round >= 0.5f ? h / 2f : Math.min(h * p.round, 8 * dp);
        // The added time stands beside it in a tab of its own: the board's ground, its corners and a goal's band are
        // its own part's alone, the gap between see-through. Too narrow for it: the minutes back beside the clock.
        int full = w, tab = tabIn(w, h);
        addedIn = tab == 0 && tabRoom(h) > 0;
        w -= tab;
        r.set(0, 0, w, h);
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
        boolean turning = ht >= 0 && ht < HT_MS && !look.f1;
        cv.save();
        if (turning) {
            float f = Math.abs((float) Math.cos(Math.PI * ht / HT_MS));
            cv.scale(1f, Math.max(0.04f, f), 0, h / 2f);
        }
        if (style == Style.MINIMAL) {
            minimal(cv, w, h);
        } else if (look.f1) {
            if (w / (float) h >= 3.6f) f1Row(cv, 0, 0, w, h); else f1Two(cv, w, h);
        } else if (style == Style.CRESTS || style == Style.FROSTED) {
            if (w / (float) h >= 3.2f) crestsRow(cv, w, h); else crestsTwo(cv, w, h);
        } else if (style == Style.STACKED) {
            stacked(cv, w, h);
        } else if (style == Style.CARD) {
            card(cv, w, h);
        } else if (w / (float) h >= 3.6f) {
            oneRow(cv, 0, 0, w, h, true);
        } else {
            twoRows(cv, w, h);
        }
        cv.restore();
        if (goalOn && !look.f1) goalMoment(cv, w, h, gt);
        // The net shivering after the ball goes in, once the scorers' band has gone; VAR checking; FULL TIME stamped.
        boolean rippling = !look.f1 && gt >= RIPPLE_AT && gt < RIPPLE_AT + RIPPLE_MS;
        if (rippling) netRipple(cv, w, h, gt - RIPPLE_AT);
        boolean scanning = varUntil > now && !look.f1;
        if (scanning) varScan(cv, w, h, now);
        long ft = ftAt > 0 && !look.f1 ? now - ftAt : -1;
        boolean stamping = ft >= 0 && ft < FT_MS;
        if (stamping) fullTimeStamp(cv, w, h, ft);
        if (p.edgeLine) {
            float e = Math.max(1f, h * 0.025f);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(e);
            edge.setColor(0x8CFFFFFF);
            r.set(e / 2, e / 2, w - e / 2, h - e / 2);
            cv.drawRoundRect(r, rad, rad, edge);
            edge.setStyle(Paint.Style.FILL);
        }
        cv.restore();
        if (tab > 0) addedTab(cv, w, full, h);
        if (part) {
            cv.restore();
            // The wipe's leading edge, a bright line fading as it lands.
            if (shown > OPEN_AT && in[2] < full - 1) {
                float lw = Math.max(2f, 2 * dp), a = 1f - (shown - OPEN_AT) / (1f - OPEN_AT);
                edge.setColor(0xFFFFFFFF);
                edge.setAlpha(Math.round(230 * Math.max(0.25f, a)));
                cv.drawRect(in[2] - lw, 0, in[2], h, edge);
            }
        }
        if ((goalOn && goalFrozen < 0) || ((turning || rippling || scanning || stamping) && !part)) postInvalidateOnAnimation();
        // Alive (Card's red always, every look's score in added time): drawn again about 30 times a second while it
        // shows (Minimal, with only its shine, as that comes), not while a goal's band is over it; else not at all.
        else if (aliveNow() && !part && animFrozen < 0 && isShown()) {
            removeCallbacks(tick);
            postDelayed(tick, nextFrame());
        }
    }

    /** The next frame while alive: one at a time, however often it is drawn in between. */
    private final Runnable tick = this::invalidate;

    /** Whether its score is alive now: Card's red always, every look in added time; never F1. */
    private boolean aliveNow() {
        return !look.f1 && (style == Style.CARD || look.stoppage || look.late && look.live);
    }

    /** When to draw it again while alive: about 30 times a second; Minimal (its shine alone) not till the next shine. */
    private long nextFrame() {
        if (style != Style.MINIMAL) return 33;
        long st = shineTime(animTime()), in = ((st % SHINE_EVERY_MS) + SHINE_EVERY_MS) % SHINE_EVERY_MS;
        return in < SHINE_MS ? 33 : Math.max(33, SHINE_EVERY_MS - in);
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
            float size = Math.min(h * 0.5f, 28 * dp), word = measure("GOAL", size), cs = crest != null ? h * 0.72f : 0f, gap = cs > 0 ? h * 0.24f : 0f;
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
            fill.setAlpha(Math.round(170 * (float) Math.sin(Math.PI * t / HIT_MS)));
            cv.drawRect(0, 0, w, h, fill);
            fill.setAlpha(255);
        }
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
        float k = need > room ? Math.max(0.05f, w / need) : 1f;
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
        words(cv, look.score, cx + score / 2, y, h, h * 0.56f * k * scoreZoom, p.scoreInk);
        cx += score;
        cx = side(cv, cx, y, h, strip, crests ? crestW : 0, codeW, k, false);
        if (withClock && clock > 0) clock(cv, cx, y, x + w - cx, h, k);
    }

    /** The score's block in the look's colours (none in Minimal's), alive in added time (Card's always). */
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
        alive(cv, x, y, w, h);
    }

    private LinearGradient scoreShade, bandShade;
    private float shadeY, shadeH;
    private int shadeA, shadeB, bandShadeH;

    /** The words' colour on a club's side as it glows in its colour after a goal: dark on a light glow, else as ever. */
    private int litInk(int colour, boolean lit) {
        if (!lit || flash <= 0f) return p.ink;
        float a = 200 * flash / 255f;
        int g = p.ground;
        int r = Math.round(((g >> 16) & 0xFF) * (1 - a) + ((colour >> 16) & 0xFF) * a);
        int gr = Math.round(((g >> 8) & 0xFF) * (1 - a) + ((colour >> 8) & 0xFF) * a);
        int b = Math.round((g & 0xFF) * (1 - a) + (colour & 0xFF) * a);
        return ink(0xFF000000 | r << 16 | gr << 8 | b);
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
        int ink = litInk(colour, flashSide == (home ? 1 : 2));
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
        if (look.late && look.live) {
            // The last minutes of a close game: the clock's box beats red, about once a second.
            fill.setColor(0xFFE3061B);
            fill.setAlpha(Math.round(60 + 150 * heartbeat(System.currentTimeMillis())));
            cv.drawRect(x, y, x + w, y + h, fill);
            fill.setAlpha(255);
        }
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? p.clockInk : p.dim;
        if (!addedInside()) {
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
        float k = need > w + 1.5f ? Math.max(0.05f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        float cx = side(cv, 0, 0, h, strip * k, crests ? crestW : 0, codeW * k + extra / 2, k, true);
        scoreBlock(cv, cx, 0, score * k, h);
        words(cv, look.score, cx + score * k / 2, 0, h, h * 0.56f * k * scoreZoom, p.scoreInk);
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
        return new String[]{look.home + "  " + look.score + "  " + look.away, look.clock, addedInside() ? look.added : ""};
    }

    private float minimalWidth(int h) {
        String[] m = minimalParts();
        float w = h * 0.9f + measure(m[0], h * 0.48f) + measure(m[1], h * 0.44f) + h * 0.7f;
        if (!m[2].isEmpty()) w += measure(m[2], h * 0.38f) + h * 0.5f;
        return w;
    }

    private void minimal(Canvas cv, int w, int h) {
        String[] m = minimalParts();
        // No block of its own: in added time its shine sweeps across it all, under the words.
        if (aliveNow()) aliveShine(cv, 0, 0, w, h, shineTime(animTime()));
        if (w / (float) h < 3.6f) {
            // A squarer place: the clubs and score over the clock.
            float top = h * 0.55f;
            float k1 = Math.min(1f, w * 0.9f / Math.max(1f, measure(m[0], top * 0.62f)));
            words(cv, m[0], w / 2f, h * 0.04f, top, top * 0.62f * k1, look.f1 ? F1_RED : p.ink);
            String under = (m[1] + (m[2].isEmpty() ? "" : "  " + m[2])).trim();
            float bh = h - top;
            float k2 = Math.min(1f, w * 0.9f / Math.max(1f, measure(under, bh * 0.62f)));
            int ink = "HT".equals(look.clock) ? AMBER : p.clockInk;
            words(cv, under, w / 2f, top - h * 0.04f, bh, bh * 0.62f * k2, ink);
            return;
        }
        float natural = minimalWidth(h), k = natural > w + 1.5f ? Math.max(0.05f, w / natural) : 1f;
        float x = (w - natural * k) / 2 + h * 0.45f * k;
        float a = measure(m[0], h * 0.48f * k);
        words(cv, m[0], x + a / 2, 0, h, h * 0.48f * k, look.f1 ? F1_RED : p.ink);
        x += a + h * 0.7f * k;
        float b = measure(m[1], h * 0.44f * k);
        int clockInk = "HT".equals(look.clock) ? AMBER : flash > 0 ? (flashSide == 1 ? look.homeColour : look.awayColour) : p.clockInk;
        words(cv, m[1], x + b / 2, 0, h, h * 0.44f * k, clockInk);
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
        float k = need > w + 1.5f ? Math.max(0.05f, w / need) : 1f;
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
        float need = hc + score + ac, k = need > w + 1.5f ? Math.max(0.05f, w / need) : 1f;
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
        else words(cv, home ? look.home : look.away, x + w / 2, y - h * 0.04f, h, h * 0.42f, litInk(colour, flashSide == (home ? 1 : 2)));
        fill.setColor(colour);
        cv.drawRect(x + w * 0.2f, y + h * 0.86f, x + w * 0.8f, y + h * 0.93f, fill);
    }

    // ---- Stacked ---------------------------------------------------------------------------------------------------

    /** The clock's column beside the two clubs: minutes and seconds over the minutes added. */
    private float stackedClockW(int h) {
        if (look.clock.isEmpty()) return 0;
        float c = measure("88:88".length() >= look.clock.length() ? "88:88" : look.clock, h * 0.3f);
        float a = addedInside() ? measure(look.added, h * 0.22f) + h * 0.3f : 0;
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
        float k = need > w + 1.5f ? Math.max(0.05f, w / need) : 1f;
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
        alive(cv, gx, y, goalsW, r);
        words(cv, goals, gx + goalsW / 2, y, r, r * 0.62f * k * (scored ? scoreZoom : 1f), p.scoreInk);
    }

    /** The clock's column: minutes and seconds, the minutes added on red under them. */
    private void stackedClock(Canvas cv, float x, float w, float h, float k) {
        fill.setColor(p.clock != 0 ? p.clock : p.ground);
        cv.drawRect(x, 0, x + w, h, fill);
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? p.clockInk : p.dim;
        if (!addedInside()) {
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

    // ---- Card ------------------------------------------------------------------------------------------------------

    private LinearGradient cardShade;
    private float cardShadeH = -1;
    private final Path slant = new Path();

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
        if (c.isEmpty()) return 0;
        float w = measure(c, cardClockSize(h));
        if (addedInside()) w += h * 0.1f + measure(look.added, h * 0.24f) + h * 0.22f;
        return w;
    }

    /** The middle: as wide as the score or the clock needs, never narrower than a crest's panel. */
    private float cardMid(int h) {
        return Math.max(h * 1.35f, Math.max(measure(cardScore(), h * 0.46f), cardClockW(h)) + h * 0.42f);
    }

    private float cardWidth(int h) {
        return 2 * (h * 0.09f + h * 1.2f) + cardMid(h);
    }

    // ---- added time: its tab beside the board, the score alive ----------------------------------------------------

    /** The room the added time's tab takes beside the board at height h: the gap and the tab (0 when there is none, or F1). */
    int tabRoom(int h) {
        if (h <= 0 || look.f1 || look.added.isEmpty()) return 0;
        return Math.round(h * 0.06f + tabW(h));
    }

    /** The tab's room beside a board w wide in all, h high: none when its own part would be narrower than twice its height. */
    int tabIn(int w, int h) {
        int room = tabRoom(h);
        return outside(w - room, room, h) ? room : 0;
    }

    /** The tab's room beside a board whose own part is own wide: none when that is too narrow for it. */
    int tabFor(int own, int h) {
        int room = tabRoom(h);
        return outside(own, room, h) ? room : 0;
    }

    /** Pure: the minutes added go beside the board (not inside, by the clock) when its own part is at least twice its height. */
    static boolean outside(int own, int room, int h) {
        return room > 0 && own >= 2 * h;
    }

    /**
     * A board of a width set to own (over the channel's own, or as the family sized it), as wide again as the tab beside
     * it needs; that width remembered, so the board keeps its own part as the tab comes and goes.
     */
    int withTab(int own, int h) {
        lpTab = own > 0 ? tabFor(own, h) : 0;
        return own > 0 ? own + lpTab : own;
    }

    /** What withTab added to the width set. */
    private int lpTab;

    /** Its own part's width as drawn now, the added time's tab beside it aside. */
    int ownWidth() {
        return getWidth() - tabIn(getWidth(), getHeight());
    }

    /** One-row looks have a tab 0.8 of the board's height, in its middle; the taller (Stacked, Card) half, on its foot. */
    private boolean tallTab() {
        return style == Style.STACKED || style == Style.CARD;
    }

    private float tabH(int h) {
        return h * (tallTab() ? 0.5f : 0.8f);
    }

    private float tabW(int h) {
        float th = tabH(h);
        return tabWidth(th, measure(look.added, th * 0.6f));
    }

    /** Pure: the tab's width for its height th and its words' width: wide enough for "+10" with room round it. */
    static float tabWidth(float th, float textW) {
        return Math.max(th * 1.15f, textW + th * 0.5f);
    }

    /**
     * When the added time's tab came (it slides out from behind the board), when added time began (its first shine), and
     * for previews the animation's time and the tab's frozen (-1: as the time goes).
     */
    private long tabAt, stoppageAt, animFrozen = -1, tabFrozen = -1;

    /** Previews and tests: the score's life drawn t ms into its animation (in added time t ms after it began), the tab all there. */
    void animAt(long t) {
        animFrozen = t;
        invalidate();
    }

    /** Previews: the tab drawn t ms after it came (sliding out, lit white at first). */
    void tabNow(long t) {
        tabFrozen = t;
        invalidate();
    }

    private long animTime() {
        return animFrozen >= 0 ? animFrozen : android.os.SystemClock.uptimeMillis();
    }

    /** The shine's time: in added time from when it began, so the first sweeps across as it does; Card's from any time. */
    private long shineTime(long t) {
        return animFrozen < 0 && look.stoppage && stoppageAt > 0 ? t - stoppageAt : t;
    }

    /** A score block (x, y, w, h) alive: lighter slants drifting across it, a shine sweeping it now and then. */
    private void alive(Canvas cv, float x, float y, float w, float h) {
        if (!aliveNow() || w < 1f || h < 1f) return;
        long t = animTime();
        slants(cv, x, y, w, h, t);
        aliveShine(cv, x, y, w, h, shineTime(t));
    }

    /**
     * Card: each club's crest on a white panel with its colour down the board's end, and between them a red block with
     * the score over the clock (lighter slants drifting across it, a shine sweeping over it now and then), as the clubs'
     * own results cards are set.
     */
    private void card(Canvas cv, int w, int h) {
        float strip = h * 0.09f, panel = h * 1.2f, mid = cardMid(h);
        float need = 2 * (strip + panel) + mid;
        float k = need > w + 1.5f ? Math.max(0.05f, w / need) : 1f;
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
        // The red, alive (scoreBlock): its slants drifting, a shine now and then.
        scoreBlock(cv, mx, 0, mid, h);
        // The score, popping up big after a goal but never past its block.
        String sc = cardScore();
        float ss = h * 0.46f * k * scoreZoom, sw = measure(sc, ss);
        if (sw > mid * 0.94f) ss *= mid * 0.94f / sw;
        words(cv, sc, mx + mid / 2f, h * 0.04f, h * 0.5f, ss, p.scoreInk);
        cardClockLine(cv, mx, mid, h, k);
    }

    /** One club's end (x: where its part starts): its colour down the board's edge, its crest on the white (its letters until the crest comes); lit as it scores. */
    private void cardSide(Canvas cv, float x, float strip, float panel, int h, boolean home) {
        int colour = home ? look.homeColour : look.awayColour;
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

    /** Pure: how far the slants have drifted along their period t ms in, 0 to 1 (a period every DRIFT_MS, round again). */
    static float drift(long t) {
        return (((t % DRIFT_MS) + DRIFT_MS) % DRIFT_MS) / (float) DRIFT_MS;
    }

    /** Pure: where the shine is across a score t ms in, 0 to 1 while it sweeps (SHINE_MS every SHINE_EVERY_MS); -1 between. */
    static float shineAt(long t) {
        long in = ((t % SHINE_EVERY_MS) + SHINE_EVERY_MS) % SHINE_EVERY_MS;
        return in < SHINE_MS ? in / (float) SHINE_MS : -1f;
    }

    /** Lighter slants across a score block (x, y, w, h), drifting slowly to the right, a period (its width) every DRIFT_MS, seamless. */
    private void slants(Canvas cv, float x, float y, float w, float h, long t) {
        cv.save();
        cv.clipRect(x, y, x + w, y + h);
        fill.setShader(null);
        fill.setColor(0x22FFFFFF);
        float o = drift(t) * w, lean = h * 0.7f;
        // Enough copies a period apart to cover it however far they lean across it.
        int last = (int) Math.ceil(lean / w + 0.5f);
        slant.reset();
        for (int i = -2; i <= last; i++) {
            float bx = x + o + i * w;
            slant.moveTo(bx + w * 0.5f, y);
            slant.lineTo(bx + w * 0.78f, y);
            slant.lineTo(bx + w * 0.78f - lean, y + h);
            slant.lineTo(bx + w * 0.5f - lean, y + h);
            slant.close();
            slant.moveTo(bx + w * 0.9f, y);
            slant.lineTo(bx + w * 1.1f, y);
            slant.lineTo(bx + w * 1.1f - lean, y + h);
            slant.lineTo(bx + w * 0.9f - lean, y + h);
            slant.close();
        }
        cv.drawPath(slant, fill);
        cv.restore();
    }

    private final Paint alivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient aliveShader = new LinearGradient(0, 0, 1, 0,
            new int[]{0x00FFFFFF, 0x4CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    private final Matrix aliveMatrix = new Matrix();

    /** A soft white shine sweeping across (x, y, w, h) from left to right, now and then (t: the shine's time). */
    private void aliveShine(Canvas cv, float x, float y, float w, float h, long t) {
        float s = shineAt(t);
        if (s < 0f) return;
        // Leaning as the slants do: from wholly before its left edge to wholly past its right.
        float sw = h * 0.6f, from = x - sw - h * 0.5f, to = x + w + h * 0.5f;
        cv.save();
        cv.clipRect(x, y, x + w, y + h);
        aliveMatrix.setScale(sw, 1f);
        aliveMatrix.postSkew(-0.5f, 0f, 0f, y);
        aliveMatrix.postTranslate(from + (to - from) * s, 0f);
        aliveShader.setLocalMatrix(aliveMatrix);
        alivePaint.setShader(aliveShader);
        cv.drawRect(x, y, x + w, y + h, alivePaint);
        cv.restore();
    }

    /**
     * The clock under the score: white while the game runs (Half-Time and Full-Time too); its added time in the tab
     * beside the board, or, the board too narrow for that, on white beside the clock.
     */
    private void cardClockLine(Canvas cv, float x, float w, int h, float k) {
        String c = cardClock();
        if (c.isEmpty()) return;
        float y = h * 0.5f, lh = h * 0.44f, size = cardClockSize(h) * k;
        int colour = "HT".equals(look.clock) ? CARD_HT : look.live || "FT".equals(look.clock) ? p.clockInk : CARD_SOFT;
        if (!addedInside()) {
            float s = Math.min(1f, w * 0.9f / Math.max(1f, measure(c, size)));
            words(cv, c, x + w / 2f, y, lh, size * s, colour);
            return;
        }
        float as = h * 0.24f * k, cw = measure(c, size), aw = measure(look.added, as) + h * 0.22f * k, gap = h * 0.1f * k;
        float s = Math.min(1f, w * 0.92f / Math.max(1f, cw + gap + aw));
        cw *= s;
        aw *= s;
        gap *= s;
        float start = x + (w - cw - gap - aw) / 2f;
        words(cv, c, start + cw / 2f, y, lh, size * s, colour);
        float ax = start + cw + gap;
        fill.setShader(null);
        fill.setColor(0xFFFFFFFF);
        r.set(ax, y + lh * 0.2f, ax + aw, y + lh * 0.8f);
        cv.drawRoundRect(r, h * 0.05f, h * 0.05f, fill);
        words(cv, look.added, ax + aw / 2f, y, lh, as * s, CARD_RED);
    }

    /**
     * The minutes added in a red tab of their own beside the board (bodyW: where its own part ends, full: the view's
     * width): 0.8 of its height in its middle (Stacked and Card: half of it, on its foot), a pill beside Glass; sliding
     * out from behind its right edge when they first come, lit white and fading to red.
     */
    private void addedTab(Canvas cv, int bodyW, int full, int h) {
        float gap = h * 0.06f, th = tabH(h), tw = Math.min(tabW(h), full - bodyW - gap);
        if (tw <= 0 || th <= 0) return;
        long since = tabFrozen >= 0 ? tabFrozen : animFrozen >= 0 || tabAt == 0 ? Long.MAX_VALUE / 2
                : android.os.SystemClock.uptimeMillis() - tabAt;
        float f = Math.min(1f, since / (float) TAB_IN_MS), lit = since < TAB_FLASH_MS ? 1f - since / (float) TAB_FLASH_MS : 0f;
        float x = bodyW + gap - (1f - ease(f)) * (tw + gap), y = tallTab() ? h - th : (h - th) / 2f;
        cv.save();
        // Behind the board until it has slid out.
        cv.clipRect(bodyW, 0, full, h);
        int red = style == Style.CARD ? CARD_RED : TAB_RED;
        fill.setShader(null);
        fill.setColor(red);
        r.set(x, y, x + tw, y + th);
        float rad = style == Style.GLASS ? th / 2f : th * 0.12f;
        cv.drawRoundRect(r, rad, rad, fill);
        float white = 0.92f * lit * lit;
        if (white > 0f) {
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(Math.round(255 * white));
            cv.drawRoundRect(r, rad, rad, fill);
            fill.setAlpha(255);
        }
        // Its words white on the red; red while it is lit white.
        float most = th * 0.6f, ts = Math.min(most, (tw - th * 0.3f) * most / Math.max(1f, measure(look.added, most)));
        words(cv, look.added, x + tw / 2f, y, th, ts, blend(0xFFFFFFFF, red, white));
        cv.restore();
        if (tabFrozen < 0 && (f < 1f || lit > 0f)) postInvalidateOnAnimation();
    }

    /** Pure: b laid over a at this share. */
    static int blend(int a, int b, float share) {
        float s = Math.max(0f, Math.min(1f, share));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - s) + ((b >> 16) & 0xFF) * s);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - s) + ((b >> 8) & 0xFF) * s);
        int bl = Math.round((a & 0xFF) * (1 - s) + (b & 0xFF) * s);
        return 0xFF000000 | r << 16 | g << 8 | bl;
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
