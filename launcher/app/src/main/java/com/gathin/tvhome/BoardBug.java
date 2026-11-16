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

import com.gathin.broadcast.CompBug;

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
        CRESTS("Crests and time"), STACKED("Stacked", 1.7f), CARD("Card", 1.6f), FROSTED("Frosted glass"),
        /** Each side's kit in 3D in a cell of its own (KitSheets' frames), turning in and swaying; GOAL on gold. */
        KITS("3D kits", 1.25f),
        /** The Scoreboard Lab's five, drawn by CompBug as the competitions' looks are (every moment over them the same). */
        FLOODLIGHT(CompBug.Kind.FLOODLIGHT), SPLITKIT(CompBug.Kind.SPLITKIT), TICKER(CompBug.Kind.TICKER), DUO(CompBug.Kind.DUO),
        LED(CompBug.Kind.LED),
        /** Each game in its own competition's look, with its logo (CompBug); Classic for one with no look of its own. */
        COMPETITION("By competition"),
        PL_WORLD(CompBug.Kind.PL), PL_US(CompBug.Kind.PL_US), FA_CUP(CompBug.Kind.FACUP), EFL(CompBug.Kind.EFL),
        UCL(CompBug.Kind.UCL), UEL(CompBug.Kind.UEL), UECL(CompBug.Kind.UECL), SPFL(CompBug.Kind.SPFL),
        LALIGA(CompBug.Kind.LALIGA), SERIE_A(CompBug.Kind.SERIEA), BUNDESLIGA(CompBug.Kind.BUNDESLIGA), LIGUE_1(CompBug.Kind.LIGUE1),
        EREDIVISIE(CompBug.Kind.EREDIVISIE), LIGA_PORTUGAL(CompBug.Kind.LIGAPORTUGAL), MLS(CompBug.Kind.MLS), LIGA_MX(CompBug.Kind.LIGAMX),
        RETRO_2004(CompBug.Kind.R2004), RETRO_2008(CompBug.Kind.R2008), RETRO_2012(CompBug.Kind.R2012), RETRO_2016(CompBug.Kind.R2016);

        final String label;
        /** How much taller than the others it is drawn (Stacked: the clubs one above the other; Card: big crests). */
        final float tall;
        /** A competition's own look (CompBug), or null. */
        final CompBug.Kind kind;

        Style(String label) {
            this(label, 1f);
        }

        Style(String label, float tall) {
            this.label = label;
            this.tall = tall;
            this.kind = null;
        }

        Style(CompBug.Kind k) {
            this.label = k.label;
            this.tall = k.tall;
            this.kind = k;
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
    /** The 3D kits look's navy, its competition's and kits' cells (top, foot), the letters' white, its clock and its gold. */
    static final int KIT_NAVY = 0xFF10185A, KIT_COMP0 = 0xFF1B2A8F, KIT_COMP1 = 0xFF0A1250, KIT_CELL0 = 0xFF24359E,
            KIT_CELL1 = 0xFF0D1660, KIT_CODE = 0xFFF4F5FB, KIT_INK = 0xFF0E1550, KIT_CLOCK = 0xFF070B2C, KIT_GOLD = 0xFFF5C518;
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
            case KITS:
                // Navy, the competition's block and the kits' cells in a deeper blue, the letters on white, the clock darker.
                p.ground = KIT_NAVY;
                p.comp = 0;
                p.score0 = p.score1 = KIT_NAVY;
                p.clock = KIT_CLOCK;
                p.round = 0.08f;
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
    // ---- the Scoreboard Lab's moments (BoardFx), chosen for the game the board shows and kept all through it -------------

    private final com.gathin.broadcast.BoardFx fx = new com.gathin.broadcast.BoardFx();
    /** The game shown (its moments are picked by it), its goals' set, whether its half time and shootout have the Lab's. */
    private String gameId = "";
    private int goalSet = com.gathin.broadcast.BoardFx.goalSet(gameId);
    private boolean htFaceGame, pensClockGame;
    /** The score before the goal on now: its new figure rolls or flips in from it. */
    private String scoreWas = "";
    /** The ground's weather over the board (BoardFx.weather): its sky and when it began (0: none). */
    private com.gathin.broadcast.BoardFx.Sky weatherSky;
    private long weatherAt, weatherFrozen = -1;
    /** A shootout's kicks in the clock's place (pensInClock games): each side's so far; null while none is on. */
    private java.util.List<Boolean> pensHome, pensAway;
    private final android.graphics.Rect textBounds = new android.graphics.Rect();

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
        p = paletteNow();
        invalidate();
    }

    // ---- the competitions' own looks (CompBug) -----------------------------------------------------------------------

    /** The game's competition's look (By competition draws it), its name, and its logo in its own colours. */
    private CompBug.Kind gameKind;
    private String compName = "";
    private Bitmap compLight;
    private CompBug compBug;
    private final CompBug.Data compData = new CompBug.Data();

    /** The game's competition (ESPN's slug and its name): By competition takes its look, and its logo with it. */
    void competition(String slug, String name) {
        CompBug.Kind k = CompBug.forGame(slug, name);
        compName = name == null ? "" : name;
        if (k == gameKind) return;
        gameKind = k;
        if (style == Style.COMPETITION) {
            p = paletteNow();
            requestLayout();
        }
        invalidate();
    }

    /** The clubs' names (the retro looks name them), from the game. */
    private String homeName = "", awayName = "";

    void names(String home, String away) {
        String h = home == null ? "" : home, a = away == null ? "" : away;
        if (h.equals(homeName) && a.equals(awayName)) return;
        homeName = h;
        awayName = a;
        if (kind() != null) {
            requestLayout();
            invalidate();
        }
    }

    /** The competition's logo in its own colours, for the looks that set it on white. */
    void competitionLight(Bitmap b) {
        compLight = b;
        invalidate();
    }

    /** The competition look drawn now (its own, or By competition's for the game's), or null for the board's own looks. */
    CompBug.Kind kind() {
        return style.kind != null ? style.kind : style == Style.COMPETITION ? gameKind : null;
    }

    /** How much taller than a row it is drawn now (By competition: its competition's look's). */
    float tall() {
        CompBug.Kind k = kind();
        return k != null ? k.tall : style.tall;
    }

    private CompBug comp() {
        if (compBug == null) compBug = new CompBug(getContext());
        return compBug;
    }

    /** What the competition look draws: the board's look, crests, logos and the goal's glow. */
    private CompBug.Data compData() {
        CompBug.Data d = compData;
        d.home = look.home;
        d.away = look.away;
        d.homeName = homeName;
        d.awayName = awayName;
        d.score = look.score;
        d.clock = look.clock;
        d.comp = compName;
        d.homeColour = look.homeColour;
        d.awayColour = look.awayColour;
        d.homeCrest = homeCrest;
        d.awayCrest = awayCrest;
        d.logo = comp;
        d.logoLight = compLight;
        d.stoppage = pulses(look);
        d.glow = flash;
        d.glowSide = flashSide;
        float[] mv = moveNow();
        d.move = Math.round(mv[0]);
        d.moveAt = mv[1];
        d.before = CompBug.scores(scoreWas);
        d.htFace = htFaceNow();
        return d;
    }

    /** Its colours now: a competition look's own (it draws its grounds itself), else its look's. */
    private Palette paletteNow() {
        CompBug.Kind k = kind();
        if (k == null) return palette(style);
        Palette c = new Palette();
        int[] st = CompBug.strip(k);
        c.ground = 0;
        c.comp = 0;
        c.score0 = c.score1 = st[3];
        c.clock = st[0];
        c.ink = c.scoreInk = c.clockInk = st[1];
        c.dim = st[2];
        c.round = 0f;
        return c;
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

    /**
     * A goal for side 1 (home) or 2 (away), in the game's set (BoardFx.goalSet, the crest burst every game): the scorers'
     * crest popping with rings in their colour as the score rolls up, then the net shivering across the board, each with
     * its own quiet sound (the 3D kits their own gold GOAL, today's band, with the family's swish on its new score).
     */
    void goal(int side) {
        flashSide = side;
        scoreWas = look.score;
        goalAt = System.currentTimeMillis();
        goalFrozen = -1;
        removeCallbacks(scoreSwish);
        int set = goalSetNow();
        // Today's band: the family's swish on its new score, every look (its own switch); the Lab's sets their own sounds.
        if (set == com.gathin.broadcast.BoardFx.GOAL_BAND) postDelayed(scoreSwish, scoreSwishAt());
        labGoalSounds(set);
        postInvalidateOnAnimation();
    }

    /** The game shown: its goals' set (the burst), half time's face and the shootout's place, the same all game (BoardFx.pick). */
    void game(String id) {
        String g = id == null ? "" : id;
        if (g.equals(gameId)) return;
        gameId = g;
        goalSet = com.gathin.broadcast.BoardFx.goalSet(g);
        htFaceGame = com.gathin.broadcast.BoardFx.htFace(g);
        pensClockGame = com.gathin.broadcast.BoardFx.pensInClock(g);
        invalidate();
    }

    /** Whether the game's shootout dots stand in the clock's place (else the strip under the board, BoardUnder). */
    boolean pensInClock() {
        return pensClockGame;
    }

    /** The goal set drawn now (setFor). */
    int goalSetNow() {
        return setFor(style, goalSet);
    }

    /** Pure: the goal set a look draws for a game's set: the 3D kits look keeps its own gold GOAL (today's timing); the rest the game's. */
    static int setFor(Style style, int gameSet) {
        return style == Style.KITS ? com.gathin.broadcast.BoardFx.GOAL_BAND : gameSet;
    }

    /** Whether the look's score rolls up like an odometer (Floodlight, Stadium LED). */
    private boolean rolls() {
        CompBug.Kind k = kind();
        return k != null && k.rolls();
    }

    /** Pure: how long a set's own moment lasts, in ms from the goal (an alert beside the board waits for it). */
    static long setMs(int set) {
        switch (set) {
            case com.gathin.broadcast.BoardFx.GOAL_FLIP: return com.gathin.broadcast.BoardFx.FLIP_MS;
            case com.gathin.broadcast.BoardFx.GOAL_BURST: return com.gathin.broadcast.BoardFx.BURST_MS;
            default: return BAND_OUT + BAND_MS;
        }
    }

    /** Pure: when the net shivers in a set, in ms from the goal (after the band in today's). */
    static long meshAt(int set) {
        switch (set) {
            case com.gathin.broadcast.BoardFx.GOAL_FLIP: return com.gathin.broadcast.BoardFx.MESH_AFTER_FLIP;
            case com.gathin.broadcast.BoardFx.GOAL_BURST: return com.gathin.broadcast.BoardFx.MESH_AFTER_BURST;
            default: return RIPPLE_AT;
        }
    }

    /** Pure: the whole goal's length in a set: its moment and the net's shiver (and today's glow after the band). */
    static long goalMs(int set) {
        if (set == com.gathin.broadcast.BoardFx.GOAL_BAND) return GOAL_MS;
        return Math.max(setMs(set), meshAt(set) + com.gathin.broadcast.BoardFx.MESH_MS);
    }

    /** Pure: when today's band rolls its new score in on an odometer look: as the band goes, over 0.8 s. */
    static final long BAND_ROLL_FROM = BAND_OUT + 150, BAND_ROLL_TO = BAND_OUT + 950;

    /**
     * Pure: the score's move t ms into a goal of a set ({CompBug.MOVE_FLIP or MOVE_ROLL (0: none), how far, 0 to 1}): the
     * flip's figure turning in, the burst's rolling up; on an odometer look (rolls) always a roll, in today's band as the
     * band goes. {0, 1} before and after.
     */
    static float[] moveAt(int set, boolean rolls, long t) {
        if (t < 0 || t >= goalMs(set)) return new float[]{0f, 1f};
        switch (set) {
            case com.gathin.broadcast.BoardFx.GOAL_FLIP:
                return new float[]{rolls ? CompBug.MOVE_ROLL : CompBug.MOVE_FLIP, com.gathin.broadcast.BoardFx.flipMove(t)};
            case com.gathin.broadcast.BoardFx.GOAL_BURST:
                return new float[]{CompBug.MOVE_ROLL, com.gathin.broadcast.BoardFx.rollMove(t)};
            default:
                if (!rolls) return new float[]{0f, 1f};
                float k = t <= BAND_ROLL_FROM ? 0f : t >= BAND_ROLL_TO ? 1f : (t - BAND_ROLL_FROM) / (float) (BAND_ROLL_TO - BAND_ROLL_FROM);
                return new float[]{CompBug.MOVE_ROLL, k};
        }
    }

    /** The score's move now (the goal on, its set, the look). */
    private float[] moveNow() {
        long gt = goalFrozen >= 0 ? goalFrozen : goalAt > 0 ? System.currentTimeMillis() - goalAt : -1;
        if (style == Style.KITS || gt < 0) return new float[]{0f, 1f};
        return moveAt(goalSetNow(), rolls(), gt);
    }

    /**
     * Half time's face now: the game has it, the board shows HT and is past the middle of its turn (its other face). Card
     * keeps its own Half-Time, Minimal has no block of its own, the 3D kits their own.
     */
    private boolean htFaceNow() {
        if (!htFaceGame || !"HT".equals(look.clock) || !htFaceLook(style, kind())) return false;
        return htAt <= 0 || System.currentTimeMillis() - htAt >= HT_MS / 2;
    }

    /** Pure: whether a look shows half time's face (the competitions' and the Lab's looks, and the board's own but three). */
    static boolean htFaceLook(Style style, CompBug.Kind kind) {
        return kind != null || style != Style.CARD && style != Style.MINIMAL && style != Style.KITS;
    }

    private final Runnable netSound = () -> lab(com.gathin.broadcast.Sfx.NET), flipSound = () -> lab(com.gathin.broadcast.Sfx.FLIP),
            rollSound = () -> lab(com.gathin.broadcast.Sfx.ROLL), burstSound = () -> lab(com.gathin.broadcast.Sfx.BURST);

    /** The goal's quiet sounds in its set (goalSounds), each at its moment. */
    private void labGoalSounds(int set) {
        removeCallbacks(netSound);
        removeCallbacks(flipSound);
        removeCallbacks(rollSound);
        removeCallbacks(burstSound);
        for (java.util.Map.Entry<String, Long> e : goalSounds(set, rolls()).entrySet()) {
            String n = e.getKey();
            Runnable r = com.gathin.broadcast.Sfx.NET.equals(n) ? netSound : com.gathin.broadcast.Sfx.FLIP.equals(n) ? flipSound
                    : com.gathin.broadcast.Sfx.ROLL.equals(n) ? rollSound : burstSound;
            postDelayed(r, e.getValue());
        }
    }

    /**
     * Pure: a goal's quiet sounds in a set, each Sfx name with its ms from the goal, in order: the digit's flip (a roll on
     * an odometer look), or the crest's burst and the roll; then the net's. (Today's band has the family's swish on its
     * new score instead, on every look, and the net's after.)
     */
    static java.util.LinkedHashMap<String, Long> goalSounds(int set, boolean rolls) {
        java.util.LinkedHashMap<String, Long> out = new java.util.LinkedHashMap<>();
        if (set == com.gathin.broadcast.BoardFx.GOAL_FLIP) {
            out.put(rolls ? com.gathin.broadcast.Sfx.ROLL : com.gathin.broadcast.Sfx.FLIP, com.gathin.broadcast.BoardFx.FLIP_FROM);
        } else if (set == com.gathin.broadcast.BoardFx.GOAL_BURST) {
            out.put(com.gathin.broadcast.Sfx.BURST, 0L);
            out.put(com.gathin.broadcast.Sfx.ROLL, com.gathin.broadcast.BoardFx.ROLL_FROM);
        }
        out.put(com.gathin.broadcast.Sfx.NET, meshAt(set));
        return out;
    }

    /** A Lab sound now, quietly (Sfx.QUIET), panned to the board: only while it is up, and not with the swish switched off. */
    private void lab(String name) {
        if (!BoardLab.heard(isAttachedToWindow() && isShown(), getAlpha(), BoardCover.swishOn(getContext()))) return;
        com.gathin.broadcast.Sfx.play(getContext(), name, panNow(), com.gathin.broadcast.Sfx.QUIET);
    }

    /** Where the board is across the screen, as a sound's pan (-0.6 left to 0.6 right). */
    private float panNow() {
        int[] at = new int[2];
        getLocationOnScreen(at);
        int sw = getResources().getDisplayMetrics().widthPixels;
        return sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
    }

    /** The ground's weather over the board now, for WEATHER_MS (BoardFx.weather): light and short. */
    void weather(com.gathin.broadcast.BoardFx.Sky sky) {
        if (sky == null) return;
        weatherSky = sky;
        weatherAt = System.currentTimeMillis();
        weatherFrozen = -1;
        postInvalidateOnAnimation();
    }

    /** Previews: the weather this far into its touch (-1: none). */
    void weatherNow(com.gathin.broadcast.BoardFx.Sky sky, long t) {
        weatherSky = t < 0 ? null : sky;
        weatherFrozen = t;
        weatherAt = t < 0 ? 0 : System.currentTimeMillis() - t;
        invalidate();
    }

    /** A shootout's kicks so far in the clock's place (pensInClock games): each side's in order; null for none on. */
    void pens(java.util.List<Boolean> home, java.util.List<Boolean> away) {
        boolean was = pensHome != null, now = home != null && away != null;
        int before = was ? com.gathin.broadcast.BoardFx.pensCols(pensHome.size(), pensAway.size()) : 0;
        pensHome = now ? new java.util.ArrayList<>(home) : null;
        pensAway = now ? new java.util.ArrayList<>(away) : null;
        int after = now ? com.gathin.broadcast.BoardFx.pensCols(home.size(), away.size()) : 0;
        if (was != now || before != after) requestLayout();
        invalidate();
    }

    /** Pure: when the family's swish starts, in ms into the goal: its loudest moment on the new score's pop (the band gone). */
    static long scoreSwishAt() {
        return Math.max(0, BAND_OUT + BAND_MS - com.gathin.broadcast.Sfx.SCORE_PEAK_MS);
    }

    /** The family's swish as the new score comes out from under the band, panned to the board; only while it is up. */
    private final Runnable scoreSwish = () -> {
        if (!isAttachedToWindow() || !isShown() || getAlpha() < 0.05f || !BoardCover.scoreSwishOn(getContext())) return;
        com.gathin.broadcast.Sfx.play(getContext(), com.gathin.broadcast.Sfx.SCORE, panNow(), 0.85f);
    };

    /** The net's shiver after the band (from RIPPLE_AT, BoardFx.netMesh); half time turning over; FULL TIME stamped. */
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
        return goalFrozen >= 0 ? 0 : bandLeft(goalAt, goalSetNow(), now);
    }

    /** Pure: how much of a goal's own moment in a set (from goalAt; 0: no goal) is still to come at now; 0 once it is over. */
    static long bandLeft(long goalAt, int set, long now) {
        return goalAt <= 0 ? 0 : Math.max(0, goalAt + setMs(set) - now);
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
        kitsAt = System.currentTimeMillis();
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
        CompBug.Kind k = kind();
        if (strip == null || stripFor != style || stripKind != k) {
            strip = k != null ? compStrip(k) : strip(style, p);
            stripFor = style;
            stripKind = k;
        }
        return strip;
    }

    /** A competition look's strips: its own dark ground and its accent, square cornered. */
    private static StripLook compStrip(CompBug.Kind k) {
        int[] c = CompBug.strip(k);
        StripLook s = new StripLook();
        s.ground = c[0];
        s.ink = c[1];
        s.dim = c[2];
        s.block = c[3];
        s.round = 0.06f;
        return s;
    }

    private CompBug.Kind stripKind;
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
        float tab = !look.added.isEmpty() ? addedTab(h) : pensTab(h), w;
        CompBug.Kind kind = kind();
        if (kind != null) w = comp().natural(kind, compData(), h);
        else if (style == Style.MINIMAL) w = minimalWidth(h);
        else if (style == Style.CRESTS || style == Style.FROSTED) w = h * 0.4f + crestCell(h, true) + scoreW(h) + crestCell(h, false) + clockW(h);
        else if (style == Style.STACKED) w = h * 0.9f + stackedRowsW(h / 2f) + stackedClockW(h);
        else if (style == Style.CARD) w = cardWidth(h);
        else if (style == Style.KITS) w = kitsWidth(h);
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
            CompBug.Kind fk = kind();
            float first = fk != null ? comp().first(fk, compData(), ch) : style == Style.CRESTS || style == Style.FROSTED ? ch * 1.25f : style == Style.STACKED ? ch * 0.9f : style == Style.CARD ? ch * 1.29f
                    : style == Style.KITS ? ch
                    : style == Style.MINIMAL ? ch * 1.2f : ch;
            in = shownPart(shown, w, h, Math.min(first + inset, w / 3f));
            cv.save();
            cv.clipRect(in[0], in[1], in[2], in[3]);
        }
        long now = System.currentTimeMillis();
        long gt = goalFrozen >= 0 ? goalFrozen : goalAt > 0 ? now - goalAt : -1;
        int set = goalSetNow();
        boolean goalOn = gt >= 0 && gt < goalMs(set);
        // Today's band: the new score pops up big as the band goes, and settles (an odometer look rolls it in instead).
        flash = goalOn && set == com.gathin.broadcast.BoardFx.GOAL_BAND && !rolls() ? glow(gt) : 0f;
        scoreZoom = 1f + 0.38f * flash * flash;
        float rad = cornerOf(w, h);
        // The minutes added stand in a tab of their own beside it (inside it only on a board kept too narrow); a shootout's
        // dots the same in the games that have them in the clock's place.
        boolean addedTab = addedTabbed(cw, ch);
        int tab = addedTab ? Math.round(addedTab(ch)) : pensTabbed(cw, ch) ? Math.round(pensTab(ch)) : 0, body = Math.max(1, w - tab);
        addedOutside = addedTab;
        drawNow = now;
        r.set(0, 0, body, h);
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        // The net shivering after every goal (the Lab's net) at its own moment in each set; the ground's weather lightly
        // over it all two or three times a game.
        long mesh = gt < 0 ? -1 : gt - meshAt(set);
        boolean meshing = mesh >= 0 && mesh < com.gathin.broadcast.BoardFx.MESH_MS;
        long wt = weatherSky == null ? -1 : weatherFrozen >= 0 ? weatherFrozen : weatherAt > 0 ? now - weatherAt : -1;
        boolean weathering = wt >= 0 && wt < com.gathin.broadcast.BoardFx.WEATHER_MS;
        // The Lab's moments over a competition's look are kept to its own shapes (Duo's pills, Floodlight's tab under its
        // row), never the gaps between: the look drawn on a layer of its own while one is on, each moment laid only where
        // the look has drawn (as the phone's).
        boolean band = set == com.gathin.broadcast.BoardFx.GOAL_BAND;
        boolean layered = kind() != null && (goalOn && !band || meshing || weathering);
        int base = layered ? cv.saveLayer(0, 0, body, h, null) : -1;
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
        CompBug.Kind ck = kind();
        if (bw > 0 && ch > 0) {
            if (ck != null) comp().draw(cv, ck, compData(), bw, ch);
            else if (style == Style.MINIMAL) minimal(cv, bw, ch);
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
            else if (style == Style.KITS) kitsRow(cv, bw, ch, now, goalOn ? gt : -1);
            else if (bw / (float) ch >= 3.6f) oneRow(cv, 0, 0, bw, ch);
            else twoRows(cv, bw, ch);
            // In added time a board with no block of its own (Minimal) has the shine pass over it all.
        }
        cv.translate(-inset, -inset);
        // A competition look with a tab over or under its row: the goal's band and the rest run along its row.
        float[] row = ck != null ? CompBug.bar(ck, ch) : null;
        int mh = h;
        if (row != null && (row[0] > 0.5f || row[1] < ch - 0.5f)) {
            cv.save();
            cv.translate(0, inset + row[0]);
            cv.clipRect(0, 0, body, row[1] - row[0]);
            mh = Math.max(1, Math.round(row[1] - row[0]));
        } else {
            row = null;
        }
        if (goalOn && style != Style.KITS) {
            if (band) {
                goalMoment(cv, body, mh, gt);
            } else {
                int a = atop(cv, layered, body, mh);
                if (set == com.gathin.broadcast.BoardFx.GOAL_FLIP) flipMoment(cv, body, mh, gt);
                else burstMoment(cv, body, mh, gt);
                if (a >= 0) cv.restoreToCount(a);
            }
        }
        if (meshing) {
            int a = atop(cv, layered, body, mh);
            fx.netMesh(cv, body, mh, mesh);
            if (a >= 0) cv.restoreToCount(a);
        }
        if (varUntil > now) varScan(cv, body, mh, now);
        long ft = ftAt > 0 ? now - ftAt : -1;
        if (ft >= 0 && ft < FT_MS) fullTimeStamp(cv, body, mh, ft);
        if (row != null) cv.restore();
        if (weathering) {
            int a = atop(cv, layered, body, h);
            fx.weather(cv, body, h, weatherSky, wt);
            if (a >= 0) cv.restoreToCount(a);
        }
        if (base >= 0) cv.restoreToCount(base);
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
        boolean moving = turning || meshing || varUntil > now || (ft >= 0 && ft < FT_MS) || weathering && weatherFrozen < 0;
        if (moving && !part) postInvalidateOnAnimation();
        if (tab > 0) {
            if (addedTab) addedTabDraw(cv, body, inset, ch, tab, now);
            else pensTabDraw(cv, body, inset, ch, tab);
        }
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
        // The 3D kits: every frame while they turn in, else when the sway next shows another frame.
        if (style == Style.KITS && !part && animFrozen < 0) kitsNext(now);
    }

    /** Laying a moment only where the board has drawn (its look's own shapes, on their layer). */
    private final Paint atop = new Paint();

    {
        atop.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
    }

    /** A layer for a moment laid only where the look has drawn (on: the look is on its own layer); -1 for none. */
    private int atop(Canvas cv, boolean on, float w, float h) {
        return on ? cv.saveLayer(0, 0, w, h, atop) : -1;
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

    /**
     * The scorers' part of the board for a set's wash or glow: their end of a long board (their letters, not the score
     * between), their row of one nearer square (Stacked's, the stacked competitions').
     */
    private void sideOf(int w, int h, boolean home, RectF out) {
        if (w / (float) h < 2.2f) out.set(0, home ? 0 : h / 2f, w * 0.62f, home ? h / 2f : h);
        else if (home) out.set(0, 0, w * 0.36f, h);
        else out.set(w * 0.64f, 0, w, h);
    }

    /** The Lab's flip, t ms in: the scorers' side washing in their colour and a shine sweeping the board (their figure turns in). */
    private void flipMoment(Canvas cv, int w, int h, long t) {
        boolean home = flashSide != 2;
        sideOf(w, h, home, r);
        fx.sideWash(cv, r.left, r.top, r.right, r.bottom, home ? look.homeColour : look.awayColour, com.gathin.broadcast.BoardFx.washAlpha(t));
        fx.shineSweep(cv, w, h, t);
    }

    /** The Lab's burst, t ms in: the scorers' crest popping with rings in their colour, their side glowing (the score rolls up). */
    private void burstMoment(Canvas cv, int w, int h, long t) {
        boolean home = flashSide != 2;
        int colour = home ? look.homeColour : look.awayColour;
        sideOf(w, h, home, r);
        fx.sideWash(cv, r.left, r.top, r.right, r.bottom, colour, com.gathin.broadcast.BoardFx.glowAlpha(t));
        float cx = (r.left + r.right) / 2f, cy = (r.top + r.bottom) / 2f, rad = Math.min(r.height(), h) * 0.36f;
        fx.crestBurst(cv, cx, cy, rad, home ? homeCrest : awayCrest, colour, t);
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
        scoreWords(cv, look.score, scoreWas, cx + score / 2, y, h, h * 0.56f * k * scoreZoom, p.scoreInk);
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

    // ---- 3D kits: each side's shirt in a cell of its own ---------------------------------------------------------------

    /** Each side's frames (KitSheets.boardAngles), null until made: a flat shirt in its colours meanwhile. */
    private com.gathin.broadcast.KitSheets.Sheet kitHome, kitAway;
    private int homeBody = 0xC8102E, homeTrim = 0xFFFFFF, awayBody = 0x1B458F, awayTrim = 0xFFFFFF;
    /** When the shirts last turned in (the board opening); until when each side's is out of its cell (a goal's turn). */
    private long kitsAt;
    private final long[] shirtOut = new long[3];
    /** Where each side's shirt was last drawn: its middle and its size, in this view's pixels (home 1, away 2). */
    private final float[] kitX = new float[3], kitY = new float[3], kitSize = new float[3];
    private LinearGradient compShade, cellShade;
    private float kitShadeH = -1;
    private final Paint kitPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    /** Each side's frames (either may be null: its flat shirt then). */
    void kits(com.gathin.broadcast.KitSheets.Sheet home, com.gathin.broadcast.KitSheets.Sheet away) {
        kitHome = home;
        kitAway = away;
        invalidate();
    }

    /** Each side's kit colours, for its flat shirt until its frames come. */
    void kitColours(int hb, int ht, int ab, int at) {
        if (hb == homeBody && ht == homeTrim && ab == awayBody && at == awayTrim) return;
        homeBody = hb;
        homeTrim = ht;
        awayBody = ab;
        awayTrim = at;
        invalidate();
    }

    /** The shirts turning in again (a preview, a try). */
    void kitsIn() {
        kitsAt = System.currentTimeMillis();
        invalidate();
    }

    /** Side 1 (home) or 2's shirt out of its cell for ms (it is turning for a goal, drawn over the screen meanwhile). */
    void shirtOut(int side, long ms) {
        if (side < 1 || side > 2) return;
        shirtOut[side] = System.currentTimeMillis() + ms;
        invalidate();
        postInvalidateDelayed(ms + 20);
    }

    /** {x, y, size} of side 1 or 2's shirt as last drawn, in this view's pixels; null when this look has none. */
    float[] kitCell(int side) {
        if (style != Style.KITS || side < 1 || side > 2 || kitSize[side] <= 0) return null;
        return new float[]{kitX[side], kitY[side], kitSize[side]};
    }

    /** Pure: a side's shirt at rest: turned a little towards the middle (the home side one way, the away side the other). */
    static float kitRest(boolean home) {
        return home ? 0.35f : -0.35f;
    }

    private float kitCode(int h) {
        return Math.max(h * 1.5f, Math.max(measure(look.home, h * 0.5f), measure(look.away, h * 0.5f)) + h * 0.5f);
    }

    private float kitClock(int h) {
        return look.clock.isEmpty() ? h * 1.2f : Math.max(h * 1.75f, measure("88:88", h * 0.48f) + h * 0.6f);
    }

    private float kitsWidth(int h) {
        return h + 2 * h * 1.45f + 2 * kitCode(h) + h * 2.0f + kitClock(h);
    }

    private static float kprog(long t, long a, long b) {
        return Math.max(0f, Math.min(1f, (t - a) / (float) (b - a)));
    }

    private static float eBack(float k) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(k - 1, 3) + c1 * (float) Math.pow(k - 1, 2);
    }

    /** The competition, a shirt, the letters, the score (GOAL on gold as it goes in), the letters, a shirt, the clock. */
    private void kitsRow(Canvas cv, int w, int h, long now, long gt) {
        float need = kitsWidth(h), k = need > w + 1 ? w / need : 1f;
        float compW = h * k, kit = h * 1.45f * k, code = kitCode(h) * k, score = h * 2.0f * k;
        float clock = Math.max(0, w - (compW + 2 * kit + 2 * code + score));
        if (compShade == null || kitShadeH != h) {
            compShade = new LinearGradient(0, 0, 0, h, KIT_COMP0, KIT_COMP1, Shader.TileMode.CLAMP);
            cellShade = new LinearGradient(0, 0, 0, h, KIT_CELL0, KIT_CELL1, Shader.TileMode.CLAMP);
            kitShadeH = h;
        }
        float x = 0;
        fill.setShader(compShade);
        cv.drawRect(x, 0, x + compW, h, fill);
        fill.setShader(null);
        if (comp != null) drawImage(cv, comp, x + compW * 0.14f, h * 0.14f, Math.min(compW, h) * 0.72f);
        else words(cv, look.tag, x + compW / 2, 0, h, h * 0.3f, INK);
        x += compW;
        float homeAt = x;
        fill.setShader(cellShade);
        cv.drawRect(x, 0, x + kit, h, fill);
        fill.setShader(null);
        x += kit;
        boolean litHome = flash > 0 && flashSide == 1, litAway = flash > 0 && flashSide == 2;
        fill.setColor(KIT_CODE);
        cv.drawRect(x, 0, x + code, h, fill);
        if (litHome) { fill.setColor(look.homeColour); fill.setAlpha(Math.round(110 * flash)); cv.drawRect(x, 0, x + code, h, fill); fill.setAlpha(255); }
        words(cv, look.home, x + code / 2, 0, h, h * 0.5f * k, KIT_INK);
        x += code;
        // the score: GOAL on gold while it goes in, then the new score popping up as the others' does
        boolean gold = gt >= HIT_MS && gt < BAND_OUT + BAND_MS;
        fill.setColor(gold ? KIT_GOLD : KIT_NAVY);
        cv.drawRect(x, 0, x + score, h, fill);
        if (look.stoppage) slants(cv, x, 0, score, h, stoppageT());
        if (gold) {
            float pk = Math.max(0.05f, eBack(kprog(gt, HIT_MS, HIT_MS + 300)));
            words(cv, "GOAL", x + score / 2, 0, h, h * 0.56f * k * pk, KIT_INK);
        } else {
            words(cv, look.score, x + score / 2, 0, h, h * 0.56f * k * scoreZoom, p.scoreInk);
        }
        x += score;
        fill.setColor(KIT_CODE);
        cv.drawRect(x, 0, x + code, h, fill);
        if (litAway) { fill.setColor(look.awayColour); fill.setAlpha(Math.round(110 * flash)); cv.drawRect(x, 0, x + code, h, fill); fill.setAlpha(255); }
        words(cv, look.away, x + code / 2, 0, h, h * 0.5f * k, KIT_INK);
        x += code;
        float awayAt = x;
        fill.setShader(cellShade);
        cv.drawRect(x, 0, x + kit, h, fill);
        fill.setShader(null);
        x += kit;
        if (clock > 0) clock(cv, x, 0, clock, h, k);
        // the shirts, standing in their cells: in with a whole turn as the board opens, then swaying
        kitShirt(cv, true, homeAt + kit / 2, h / 2f, h, now);
        kitShirt(cv, false, awayAt + kit / 2, h / 2f, h, now);
    }

    private void kitShirt(Canvas cv, boolean home, float cx, float cy, int h, long now) {
        int side = home ? 1 : 2;
        float size = h * 1.22f;
        kitX[side] = cx + inset;
        kitY[side] = cy + inset;
        kitSize[side] = size;
        if (now < shirtOut[side]) return;
        long it = animFrozen >= 0 ? 100_000L : kitsAt > 0 ? now - kitsAt : 100_000L;
        float drop = eBack(kprog(it, 550, 1200));
        if (drop <= 0.01f) return;
        float spin = (1 - (1 - (float) Math.pow(1 - kprog(it, 550, 1600), 4))) * (float) Math.PI * 2;
        float sway = (float) Math.sin(now / 1000.0 * 1.1 + (home ? 0 : 1.7)) * 0.28f;
        float rest = kitRest(home), s = size * drop;
        com.gathin.broadcast.KitSheets.Sheet sh = home ? kitHome : kitAway;
        if (sh != null && !sh.bitmap.isRecycled()) {
            int i = spin > 0.02f ? sh.nearest(rest + spin, 0, com.gathin.broadcast.KitSheets.TURN_N)
                    : sh.nearest(rest + sway, com.gathin.broadcast.KitSheets.SWAY_AT, com.gathin.broadcast.KitSheets.SWAY_AT + com.gathin.broadcast.KitSheets.SWAY_N);
            r.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            sh.draw(cv, i, r, kitPaint);
        } else {
            com.gathin.broadcast.KitSheets.flatShirt(cv, cx, cy, s * 0.8f, home ? homeBody : awayBody, home ? homeTrim : awayTrim, rest + spin, kitPaint);
        }
    }

    /** Drawn again every frame while the shirts turn in, else when either's sway next shows another frame. */
    private void kitsNext(long now) {
        long it = kitsAt > 0 ? now - kitsAt : 100_000L;
        if (it < 1700) { postInvalidateOnAnimation(); return; }
        if (kitHome == null && kitAway == null) return;
        int[] was = {swayFrame(true, now), swayFrame(false, now)};
        for (long dt = 40; dt <= 1000; dt += 40) {
            if (swayFrame(true, now + dt) != was[0] || swayFrame(false, now + dt) != was[1]) {
                postInvalidateDelayed(dt);
                return;
            }
        }
        postInvalidateDelayed(1000);
    }

    private int swayFrame(boolean home, long t) {
        com.gathin.broadcast.KitSheets.Sheet sh = home ? kitHome : kitAway;
        if (sh == null) return -1;
        float sway = (float) Math.sin(t / 1000.0 * 1.1 + (home ? 0 : 1.7)) * 0.28f;
        return sh.nearest(kitRest(home) + sway, com.gathin.broadcast.KitSheets.SWAY_AT, com.gathin.broadcast.KitSheets.SWAY_AT + com.gathin.broadcast.KitSheets.SWAY_N);
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
        scoreWords(cv, look.score, scoreWas, cx + score * k / 2, 0, h, h * 0.56f * k * scoreZoom, p.scoreInk);
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
        scoreWords(cv, look.score, scoreWas, cx + score / 2, 0, h, h * 0.56f * k * scoreZoom, p.scoreInk);
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
        scoreWords(cv, look.score, scoreWas, cx + score * k / 2, 0, top, top * 0.56f * k * scoreZoom, p.scoreInk);
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
        return goalsOf(look.score);
    }

    /** Pure: a score's two figures ("2–1": {"2", "1"}); two empties when it has not two (before kick-off). */
    static String[] goalsOf(String score) {
        String[] g = (score == null ? "" : score).split("\\s*[–-]\\s*");
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
        String[] before = goalsOf(scoreWas);
        scoreWords(cv, goals, home ? before[0] : before[1], gx + goalsW / 2, y, r, r * 0.62f * k * (scored ? scoreZoom : 1f), p.scoreInk);
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
        return cardScoreOf(look.score);
    }

    /** Pure: a score as Card spaces it ("2 - 1"; as it is before kick-off). */
    static String cardScoreOf(String score) {
        String[] g = goalsOf(score);
        return g[0].isEmpty() ? (score == null ? "" : score) : g[0] + " - " + g[1];
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
        return tall() > 1.2f ? h * 0.5f : h * 0.8f;
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
        scoreWords(cv, sc, cardScoreOf(scoreWas), mx + mid / 2f, h * 0.04f, h * 0.5f, ss, p.scoreInk);
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
        scoreWords(cv, sc, cardScoreOf(scoreWas), w / 2f, top, rh * 0.56f, ss, p.scoreInk);
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
        float top = tall() > 1.2f ? edgePx + ch - th : edgePx + (ch - th) / 2f;
        float rad = p.round >= 0.5f ? th / 2f : th * 0.12f;
        cv.save();
        // Out from behind the board.
        cv.clipRect(body, 0, getWidth(), getHeight());
        fill.setShader(null);
        fill.setColor(style == Style.CARD ? CARD_RED : ADDED);
        r.set(left, top, left + tw, top + th);
        cv.drawRoundRect(r, rad, rad, fill);
        // The minutes added running: a thin white line along its foot filling as they go (empty at the half's end, full
        // once they are up), on a faint track; steady, moving only as the clock does.
        float run = addedRun(look.clock, look.added);
        float textTop = top;
        if (run >= 0f) {
            float lh = Math.max(2f, th * 0.08f), lx = left + th * 0.2f, lw = tw - th * 0.4f, ly = top + th - lh - th * 0.12f;
            fill.setColor(0x4DFFFFFF);
            cv.drawRect(lx, ly, lx + lw, ly + lh, fill);
            fill.setColor(0xF2FFFFFF);
            cv.drawRect(lx, ly, lx + lw * run, ly + lh, fill);
            textTop = top - th * 0.05f;
        }
        words(cv, look.added, left + tw / 2f, textTop, th, th * 0.6f, INK);
        cv.restore();
        // Out from behind the board, then still (no flash as it lands).
        if (since < TAB_IN_MS) postInvalidateOnAnimation();
    }

    /**
     * Pure: how far through its minutes added a clock is, 0 at the half's end to 1 once they are up; -1 when it cannot
     * tell (no minutes, no clock). The half's end is the last of 45, 90, 105 and 120 the clock has reached (or is a
     * minute short of).
     */
    static float addedRun(String clock, String added) {
        int n;
        try {
            n = Integer.parseInt(added == null ? "" : added.replace("+", "").trim());
        } catch (NumberFormatException e) {
            return -1f;
        }
        int colon = clock == null ? -1 : clock.indexOf(':');
        if (n <= 0 || colon <= 0) return -1f;
        int m, s;
        try {
            m = Integer.parseInt(clock.substring(0, colon).trim());
            s = Integer.parseInt(clock.substring(colon + 1).replaceAll("[^0-9].*$", "").trim());
        } catch (NumberFormatException e) {
            return -1f;
        }
        int end = -1;
        for (int e : new int[]{45, 90, 105, 120}) if (m >= e - 1) end = e;
        if (end < 0) return -1f;
        float run = (m * 60 + s - end * 60) / (n * 60f);
        return Math.max(0f, Math.min(1f, run));
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

    /**
     * A score's words about cx in the middle of (y, h), as words() draws them; while a goal's new figure moves in (the
     * flip's turn or the odometer's roll) it moves from the score before (was), the rest still; at half time in the games
     * that have the Lab's face, the figures on a small white block in dark ink.
     */
    private void scoreWords(Canvas cv, String now, String was, float cx, float y, float h, float size, int colour) {
        if (now == null || now.isEmpty()) return;
        if (htFaceNow()) {
            text.setTextSize(size);
            float tw = text.measureText(now);
            text.getTextBounds("8", 0, 1, textBounds);
            float capH = -textBounds.top, padX = size * 0.22f, padY = size * 0.16f, mid = y + h / 2f;
            fill.setShader(null);
            fill.setColor(0xF2FFFFFF);
            r.set(cx - tw / 2f - padX, mid - capH / 2f - padY, cx + tw / 2f + padX, mid + capH / 2f + padY);
            cv.drawRoundRect(r, size * 0.12f, size * 0.12f, fill);
            words(cv, now, cx, y, h, size, 0xFF0B0E16);
            return;
        }
        float[] mv = moveNow();
        if (mv[0] == 0f || mv[1] >= 1f || was == null || was.isEmpty() || was.equals(now)) {
            words(cv, now, cx, y, h, size, colour);
            return;
        }
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        float base = y + h / 2 - (fm.ascent + fm.descent) / 2;
        text.getTextBounds("H8", 0, 2, textBounds);
        fx.scoreMove(cv, text, was, now, cx, base + textBounds.top / 2f, Math.round(mv[0]), mv[1]);
        text.setAlpha(255);
    }

    /** Whether a shootout's dots stand beside the clock now (pensInClock games, while one is on), the board keeping its room. */
    private boolean pensTabbed(int w, int h) {
        return pensHome != null && pensAway != null && look.added.isEmpty() && w - pensTab(h) >= h * 2f;
    }

    /** The room the shootout's dots take beside the board (two rows of five, more in sudden death); none without them. */
    private float pensTab(int h) {
        if (pensHome == null || pensAway == null) return 0;
        float th = tabHeight(h);
        return h * 0.06f + th * 0.5f + com.gathin.broadcast.BoardFx.pensCols(pensHome.size(), pensAway.size()) * th * 0.46f;
    }

    /** The shootout's dots in their tab beside the clock: the home side's row over the away side's, on the clock's ground. */
    private void pensTabDraw(Canvas cv, int body, int edgePx, int ch, int tab) {
        float gap = ch * 0.06f, tw = tab - gap, th = tabHeight(ch);
        float left = body + gap, top = tall() > 1.2f ? edgePx + ch - th : edgePx + (ch - th) / 2f;
        float rad = p.round >= 0.5f ? th / 2f : th * 0.12f;
        int ground = p.clock != 0 ? p.clock : p.ground != 0 ? p.ground : CLOCK;
        fill.setShader(null);
        fill.setColor(0xFF000000 | ground);
        r.set(left, top, left + tw, top + th);
        cv.drawRoundRect(r, rad, rad, fill);
        fx.pensDots(cv, left + th * 0.25f, top + th * 0.08f, left + tw - th * 0.25f, top + th * 0.92f, pensHome, pensAway, INK);
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
