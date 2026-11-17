package com.gathin.homefieldtv;

/**
 * The colours every screen draws with. The first group comes from the colour theme (Theme.apply sets them when the
 * app starts; Indigo with Blue until then); the rest are white, black or the accent at a set opacity, drawn over
 * whatever is beneath, so one value serves the ground, a panel and the video alike. The values are the family's set
 * of the other app, measured on their TV (Indigo • Blue):
 *
 * <pre>
 *   GROUND        the screens' background: guide, search, films, Home            #151B3F
 *   PANEL         the rail and drawer, menus, Settings' list, cards               #1F295F
 *   HEADER        a panel's title band (Settings' 144 px band)                     #2A377F
 *   PLAYBACK      panels over the video (banner, pickers, menus)                   #1C2759 at 82.7 %
 *   SCRIM         behind Settings and menus over a screen                          black at 60 %
 *   ACCENT        what plays or is chosen: the playing channel, ▶, switches, section names, the seek line   #2196F3
 *   ACCENT_LIGHT  the guide's date and clock, the now dot, programmes on now, a focused tile's edge         #90CAF9
 *   ACCENT_INK    words on a solid accent fill: white, or dark on the light accents (Lime, Yellow…)
 *   FOCUS         where the remote is: the accent at 37.5 % (pills, circles; #1F5296 on a panel)
 *   SELECTED      chosen while the remote is elsewhere: white at 8.5 % (#323B6D on a panel)
 *   TEXT1/2/3     white at 93 / 60 / 37.5 %: titles and names; times, values, descriptions; switched off
 *   ICON_OFF      the rail's icons for the sections not on screen: white at 46 %
 *   DIVIDER       a menu's 2 px rule (white at 8.5 %); GUIDE_DIVIDER under the guide's times (white at 16 %)
 *   CELL          a guide cell (white at 3.5 %); CELL_AIRED the part already shown (7 %); CELL_ROW the focused row's (20 %)
 *   TRACK / FILL  a thin progress bar: white at 18 % under white at 64 %
 *   SEEK_LIVE / SEEK_AIRED / KNOB  the seek line: still to come (#666), what catch-up can go back to (#AAA), its knob
 *   SWITCH_OFF / THUMB_OFF / SWITCH_OFF_FOCUS  a switch that is off: its track (white 31 %) and thumb (#C0C0C0); on the
 *                 remote's row the track is black at 20 % and the thumb white
 *   CHIP          a media chip (FHD, 50 FPS, STEREO): solid #999, its letters in the panel's colour
 *   SHADOW        the drawer's soft edge
 * </pre>
 *
 * over() works out the solid colour a token makes on a background; the screens normally just draw the token itself.
 */
final class Palette {
    private Palette() {}

    // ---- the theme's (Theme.apply) -------------------------------------------------------------------------------
    static int GROUND = 0xFF151B3F, PANEL = 0xFF1F295F, HEADER = 0xFF2A377F;
    static int PLAYBACK = 0xD31C2759;
    static int ACCENT = 0xFF2196F3, ACCENT_LIGHT = 0xFF90CAF9, ACCENT_INK = 0xFFFFFFFF;
    static int FOCUS = 0x602196F3;

    /** The opacity of FOCUS: 37.5 %. */
    static final int FOCUS_ALPHA = 0x60;
    /** The opacity of PLAYBACK: 82.7 %. */
    static final int PLAYBACK_ALPHA = 0xD3;

    // ---- the same in every theme ---------------------------------------------------------------------------------
    static final int SELECTED = 0x16FFFFFF;
    static final int TEXT1 = 0xEEFFFFFF, TEXT2 = 0x99FFFFFF, TEXT3 = 0x60FFFFFF, ICON_OFF = 0x76FFFFFF;
    static final int DIVIDER = 0x16FFFFFF, GUIDE_DIVIDER = 0x28FFFFFF;
    static final int SCRIM = 0x99000000;
    static final int CELL = 0x09FFFFFF, CELL_AIRED = 0x11FFFFFF, CELL_ROW = 0x33FFFFFF;
    static final int TRACK = 0x2EFFFFFF, FILL = 0xA4FFFFFF;
    static final int SEEK_LIVE = 0xFF666666, SEEK_AIRED = 0xFFAAAAAA, KNOB = 0xFFFFFFFF;
    static final int SWITCH_OFF = 0x50FFFFFF, THUMB_OFF = 0xFFC0C0C0, SWITCH_OFF_FOCUS = 0x32000000;
    static final int CHIP = 0xFF999999;
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
