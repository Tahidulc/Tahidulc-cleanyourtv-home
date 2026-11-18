package com.gathin.homefieldtv;

/**
 * The 07 look in one place: the colours, materials, radii, shadows, spacing and motion of the approved mock-ups
 * (homefield-07-real, their tv.css), as static fields the screens read when they draw. Sizes are design pixels of a
 * 1920 x 1080 screen (Ui.f); colours are ARGB. Palette carries the theme's share of the same values (its ground,
 * panels and accent) and Type the words. The drawing pieces that use them: Frost and Ground (materials and the blurred
 * backdrop), Card07 (cards that lift and tilt), Pills (pills, buttons, chips, switches, bars, headers), PicTile (a
 * programme's picture behind a tile), Logos (channel logos), Icons07 (the icons), Shadows, TabBar (the shell).
 *
 * <pre>
 *   colour                 value                          where
 *   L1 / L2 / L3 / L4      white; rgba(235,235,245) at 74, 52, 30 %   words: titles; second lines; quiet; faint
 *   AMBER / AMBER_INK      #FFB547 / #1E1606               Homefield's mark, the now dot, the date, the update dot
 *   RED / GREEN            #FF453A / #32D74B               LIVE; a switch that is on, a tick
 *   GROUND                 #0B0B0F                         the page under its picture
 *   INK / INK2 / INK3      #111; black at 55 %, 50 %       words on the white pill where the remote is
 *   FOCUS_FILL             white at 95 %                   the white pill, row or key where the remote is
 *   PILL_FILL              white at 96 %                   the tab bar's sliding pill
 *   SELECTED               white at 16 %                   chosen while the remote is elsewhere (a section, a tab's page)
 *   BUTTON                 white at 16 %                   a button that isn't the main one
 *   RULE                   white at 12 %                   a rule between groups of lines
 *   EDGE                   white at 14 %                   the 1 px ring round a lifted card, a picture
 *   SCRIM                  black at 58 %                   behind a menu or Settings
 *   CARD_FRAME             #1C1C22                         a card before its picture comes
 *   CELL                   #151A28                         a guide cell under its picture
 *   WARN                   #FF8A80                         a menu line that locks or takes away (Block channel)
 *
 *   material               fill (top → foot)                         highlight   ring    shadow
 *   GLASS                  white 12 %                                 white 28 %  8 %     0 14 40 black 25 %
 *   MAT                    rgba(58,58,66,.62) → rgba(30,30,36,.74)   white 22 %  -       0 24 60 black 35 %
 *   MAT_DARK               rgba(36,36,42,.72) → rgba(20,20,24,.82)   white 16 %  -       0 24 60 black 40 %
 *   (blur is faked: Frost draws the page's pre-blurred picture under the fill; Ground makes that picture)
 *
 *   radius   R_TAB 36 (bar) / 28 (tab, pill 56 high) · R_PILL 26 · R_CARD 22 · R_POSTER 18 · R_ROW 18 · R_MENU_ITEM 18
 *            R_MENU 40 · R_SHEET 48 · R_PANEL 34 · R_LOGO_CARD 36 · R_HERO 46 · R_TILE 16 · R_CELL 16 · R_CHIP 6
 *   shadow   SH_CARD 0 10 24 black 28 % · SH_CARD_UP 0 46 70 -18 black 78 % · SH_PANEL 0 24 60 black 40 %
 *            SH_GLASS 0 14 40 black 25 % · SH_BUTTON 0 12 30 black 30 % · SH_ROW 0 18 40 black 35 %
 *            SH_PILL 0 10 26 black 30 %  (each: {dy, blur, spread, alpha 0-255}; Shadows draws them)
 *   spacing  MARGIN 90 (the page's sides) · TOP 136 (under the tab bar) · GAP 22 (between tiles) · ROW_GAP 8
 *   motion   LIFT_MS 330 springy BOUNCE 0.78 / BOUNCE_KICK 0.6 (= cubic-bezier(.25,.9,.3,1.12) over 0.4 s, a 3 %
 *            overshoot) · SCROLL_MS 300 (rows) · FADE_MS 350 (a picture cross-fading) · GROUND_FADE_MS 700 (the page's
 *            picture) · TEXT_DELAY_MS 150
 * </pre>
 */
final class Look07 {
    private Look07() {}

    // ---- colours ----------------------------------------------------------------------------------------------------
    static final int L1 = 0xFFFFFFFF, L2 = 0xBDEBEBF5, L3 = 0x85EBEBF5, L4 = 0x4DEBEBF5;
    static final int AMBER = 0xFFFFB547, AMBER_INK = 0xFF1E1606, RED = 0xFFFF453A, GREEN = 0xFF32D74B, WARN = 0xFFFF8A80;
    static final int GROUND = 0xFF0B0B0F;
    static final int INK = 0xFF111111, INK2 = 0x8C000000, INK3 = 0x80000000;
    static final int FOCUS_FILL = 0xF2FFFFFF, PILL_FILL = 0xF5FFFFFF, SELECTED = 0x29FFFFFF, BUTTON = 0x29FFFFFF;
    static final int RULE = 0x1FFFFFFF, EDGE = 0x24FFFFFF, SCRIM = 0x94000000;
    static final int CARD_FRAME = 0xFF1C1C22, CELL = 0xFF151A28;
    /** A red card, a yellow card (the score lines). */
    static final int RED_CARD = 0xFFE8262C, YELLOW_CARD = 0xFFFFD60A;

    // ---- materials (Frost) -------------------------------------------------------------------------------------------
    static final int GLASS = 0, MAT = 1, MAT_DARK = 2;
    /** Each material's fill at its top and its foot. */
    static final int[] MAT_TOP = {0x1FFFFFFF, 0x9E3A3A42, 0xB824242A}, MAT_FOOT = {0x1FFFFFFF, 0xBD1E1E24, 0xD1141418};
    /** Each material's 1 px light along its top (inset 0 1px 0), and its hairline ring (glass only). */
    static final int[] MAT_HIGHLIGHT = {0x47FFFFFF, 0x38FFFFFF, 0x29FFFFFF}, MAT_RING = {0x14FFFFFF, 0, 0};
    /** The page's own dim over its blurred picture: top, 38 %, 70 %, foot. */
    static final int[] DIM = {0x9908080C, 0x5C08080C, 0x8508080C, 0xCC08080C};
    static final float[] DIM_AT = {0f, 0.38f, 0.70f, 1f};
    /** How the page's picture is made: downscaled to an eighth, blurred once, its colour pushed (saturate 1.75,
     *  brightness 0.86, as blur(50px) saturate(1.75) brightness(.86)). */
    static final int BLUR_DOWNSCALE = 8;
    static final float BLUR_RADIUS = 50f, BLUR_SATURATE = 1.75f, BLUR_BRIGHTNESS = 0.86f;

    // ---- radii --------------------------------------------------------------------------------------------------------
    static final float R_BAR = 36f, R_TAB = 28f, R_PILL = 26f, R_CARD = 22f, R_POSTER = 18f, R_ROW = 18f, R_MENU_ITEM = 18f,
            R_MENU = 40f, R_SHEET = 48f, R_PANEL = 34f, R_LOGO_CARD = 36f, R_HERO = 46f, R_TILE = 16f, R_CELL = 16f, R_CHIP = 6f,
            R_BUTTON = 35f, R_KEY = 20f;

    // ---- shadows: {dy, blur, spread, alpha} (design px; Shadows draws them) --------------------------------------------
    static final float[] SH_CARD = {10f, 24f, 0f, 71f}, SH_CARD_UP = {46f, 70f, -18f, 199f}, SH_PANEL = {24f, 60f, 0f, 102f},
            SH_GLASS = {14f, 40f, 0f, 64f}, SH_BUTTON = {12f, 30f, 0f, 77f}, SH_ROW = {18f, 40f, 0f, 89f}, SH_PILL = {10f, 26f, 0f, 77f},
            SH_HERO = {44f, 90f, -24f, 179f}, SH_TILE = {10f, 26f, 0f, 102f};

    // ---- spacing ------------------------------------------------------------------------------------------------------
    static final float MARGIN = 90f, TOP = 136f, GAP = 22f, ROW_GAP = 8f, PAGE_W = 1920f - 2 * MARGIN;

    // ---- the shell (TabBar) -------------------------------------------------------------------------------------------
    /** The tab bar: its top, height and inner padding; a tab's height, side padding and the gap between tabs; an icon
     *  tab's width and its icon; the update dot. Centred across the screen. */
    static final float BAR_TOP = 42f, BAR_H = 72f, BAR_PAD = 8f, TAB_H = 56f, TAB_PAD = 26f, TAB_GAP = 2f, TAB_ICON_W = 56f,
            TAB_ICON = 28f, DOT = 9f, DOT_IN = 10f;
    /** The sliding pill grows a little past its tab (scale 1.06). */
    static final float PILL_GROW = 1.06f;
    /** Homefield's mark and name at the top left; the clock at the top right; the "‹ back" line. */
    static final float BRAND_X = 90f, BRAND_Y = 50f, MARK = 46f, BRAND_GAP = 14f, CLOCK_RIGHT = 90f, CLOCK_TOP = 44f,
            BACK_X = 330f, BACK_Y = 60f, BACK_ICON = 20f, BACK_GAP = 6f;

    // ---- motion -------------------------------------------------------------------------------------------------------
    static final long SCROLL_MS = 300, FADE_MS = 350, GROUND_FADE_MS = 700, TEXT_DELAY_MS = 150;
    /**
     * 07's "lift" ease, cubic-bezier(.25,.9,.3,1.12) over 0.4 s (cards, the tab bar's pill, rows lifting), as a Glide:
     * new Glide(LIFT_MS, unit).springy(BOUNCE, BOUNCE_KICK) follows it to within 0.01 (a 2.8 % overshoot at 0.25 s,
     * settled by 0.4 s).
     */
    static final long LIFT_MS = 330;
    static final float BOUNCE = 0.78f, BOUNCE_KICK = 0.6f;

    // ---- the card's focus (Card07) -------------------------------------------------------------------------------------
    /** Lifted: scale 1.12, tilted 5° about its width and -7° about its height, seen from 1200 px. */
    static final float CARD_SCALE = 1.12f, CARD_TILT_X = 5f, CARD_TILT_Y = -7f, CARD_PERSPECTIVE = 1200f;
    /** The picture is 14 px larger all round and drifts (-10, -4) as it lifts; the marks on it (7, -3); its caption drops 22. */
    static final float CARD_BLEED = 14f, CARD_DRIFT_X = -10f, CARD_DRIFT_Y = -4f, MARKS_DRIFT_X = 7f, MARKS_DRIFT_Y = -3f,
            CAPTION_GAP = 16f, CAPTION_DROP = 22f;
}
