package com.gathin.homefieldtv;

/**
 * The colours every screen draws with, at the 07 look's values (Look07 has the rest of 07: its materials, radii,
 * shadows, spacing and motion). The first group comes from the colour theme (Theme.apply sets them when the app starts:
 * 07's own neutral ground with Homefield's amber, "Homefield • Amber", until then); the rest are white, black or the
 * accent at a set opacity, drawn over whatever is beneath, so one value serves the ground, a panel and the video alike.
 *
 * <pre>
 *   GROUND        the screens' ground under their blurred picture                 #0B0B0F (07's page)
 *   PANEL         a material as a solid colour: menus, cards, Settings' list        #1D1D22 (mat-dark over the ground)
 *   HEADER        a lighter material (a band, a raised card)                        #2A2A31
 *   PLAYBACK      panels over the video (banner, pickers, menus)                    #141418 at 82 % (mat-dark's foot)
 *   SCRIM         behind Settings and menus over a screen                           black at 58 %
 *   ACCENT        Homefield's amber: the guide's date and now dot, a chosen section's icon, section names in Settings,
 *                 the update dot                                                     #FFB547
 *   ACCENT_LIGHT  the accent's light shade (the guide's date and clock in the old look)  #FFD08A
 *   ACCENT_INK    words on a solid accent fill                                       #1E1606
 *   FOCUS         where the remote is, in the screens not yet redrawn: white at 28 % (their words stay white on it);
 *                 07's own focus is the white pill with dark ink (Look07.FOCUS_FILL, Look07.INK: Pills, RowList)
 *   SELECTED      chosen while the remote is elsewhere: white at 16 %
 *   TEXT1/2/3     07's labels: white; rgba(235,235,245) at 74 % and 52 %: titles; values and descriptions; quieter
 *   ICON_OFF      icons of what isn't chosen: TEXT3
 *   DIVIDER       a menu's rule (white at 12 %); GUIDE_DIVIDER the guide's ticks (white at 25 %)
 *   CELL          a guide cell (white at 8 %); CELL_AIRED the part already shown (11 %); CELL_ROW the remote's row (14 %)
 *   TRACK / FILL  a progress bar: white at 22 % under white
 *   SEEK_LIVE / SEEK_AIRED / KNOB  the seek line: still to come, what catch-up can go back to, its knob
 *   SWITCH_OFF / THUMB_OFF / SWITCH_OFF_FOCUS  a switch that is off: its track (white at 22 %) and thumb (white); on
 *                 the remote's white row the track is black at 18 %. SWITCH_ON: 07's green
 *   CHIP          a solid media chip in the old look (#8E8E93, its letters in the panel's colour)
 *   SHADOW        a soft edge's shadow
 * </pre>
 *
 * over() works out the solid colour a token makes on a background; the screens normally just draw the token itself.
 */
final class Palette {
    private Palette() {}

    // ---- the theme's (Theme.apply) -------------------------------------------------------------------------------
    static int GROUND = 0xFF0B0B0F, PANEL = 0xFF1D1D22, HEADER = 0xFF2A2A31;
    static int PLAYBACK = 0xD1141418;
    static int ACCENT = 0xFFFFB547, ACCENT_LIGHT = 0xFFFFD08A, ACCENT_INK = 0xFF1E1606;
    static int FOCUS = 0x47FFFFFF;

    /** The opacity of FOCUS in the classic colour themes (the accent at 37.5 %). */
    static final int FOCUS_ALPHA = 0x60;
    /** 07's own FOCUS for the screens not yet redrawn: white at 28 %. */
    static final int FOCUS_07 = 0x47FFFFFF;
    /** The opacity of PLAYBACK: 82 %. */
    static final int PLAYBACK_ALPHA = 0xD1;

    // ---- the same in every theme ---------------------------------------------------------------------------------
    static final int SELECTED = 0x29FFFFFF;
    static final int TEXT1 = 0xFFFFFFFF, TEXT2 = 0xBDEBEBF5, TEXT3 = 0x85EBEBF5, ICON_OFF = 0x85EBEBF5;
    static final int DIVIDER = 0x1FFFFFFF, GUIDE_DIVIDER = 0x40FFFFFF;
    static final int SCRIM = 0x94000000;
    static final int CELL = 0x14FFFFFF, CELL_AIRED = 0x1CFFFFFF, CELL_ROW = 0x24FFFFFF;
    static final int TRACK = 0x38FFFFFF, FILL = 0xFFFFFFFF;
    static final int SEEK_LIVE = 0xFF666666, SEEK_AIRED = 0xFFAAAAAA, KNOB = 0xFFFFFFFF;
    static final int SWITCH_OFF = 0x38FFFFFF, THUMB_OFF = 0xFFFFFFFF, SWITCH_OFF_FOCUS = 0x2E000000, SWITCH_ON = 0xFF32D74B;
    static final int CHIP = 0xFF8E8E93;
    static final int SHADOW = 0x59000000;

    /** Pure: the solid colour c (with its own opacity) makes over an opaque base. */
    static int over(int base, int c) {
        float a = ((c >>> 24) & 0xFF) / 255f;
        return Theme.mix(base | 0xFF000000, c | 0xFF000000, a);
    }

    /** Pure: a colour at an opacity (0 to 255). */
    static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }
}
