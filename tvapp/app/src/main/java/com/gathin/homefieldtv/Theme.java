package com.gathin.homefieldtv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The colour themes: a primary colour (the screens' ground, the panels, a panel's title band and the panels over the
 * video) with an accent (the focus, what plays or is chosen, switches, section names), named as a pair: "Indigo •
 * Blue". Indigo with Blue is first and is what every TV starts in: the family's choice in their other app, its colours
 * measured on their TV. The other primaries keep their own Material hue at Indigo's three brightness steps (so every
 * one is as dark, and white reads as well on it); Black is the near-black of that app's pictures. The accents are the
 * Material 500 colours, with their 200 shade as the light accent and white or dark writing on them, whichever reads.
 *
 * Settings → Colour theme keeps a choice on the TV (the "look" preferences: primary, accent) and the screens are made
 * again in it (TvActivity.restartLook). apply() sets Palette's colours, and Dark's for the screens still drawn in it.
 * The five themes before these are gone: a TV that had one of them comes up in Indigo • Blue.
 */
final class Theme {
    private Theme() {}

    /** A primary: the ground, the panels, a panel's title band and (at 82.7 %) the panels over the video. */
    static final class Primary {
        final String name;
        /** Its Material 500 colour: the disc it is shown by in Settings. */
        final int swatch;
        final int ground, panel, header, playback;

        Primary(String name, int swatch, int ground, int panel, int header, int playback) {
            this.name = name;
            this.swatch = swatch;
            this.ground = ground;
            this.panel = panel;
            this.header = header;
            this.playback = playback;
        }
    }

    /** An accent: its colour, its light shade, and the writing that reads on it. */
    static final class Accent {
        final String name;
        final int colour, light, ink;

        Accent(String name, int colour, int light, int ink) {
            this.name = name;
            this.colour = colour;
            this.light = light;
            this.ink = ink;
        }
    }

    private static final int WHITE = 0xFFFFFFFF, DARK = 0xFF1A1A1A;

    static final Primary[] PRIMARIES = {
            new Primary("Indigo", 0xFF3F51B5, 0xFF151B3F, 0xFF1F295F, 0xFF2A377F, 0xFF1C2759),
            new Primary("Blue", 0xFF2196F3, 0xFF0F202D, 0xFF173044, 0xFF1E415C, 0xFF162D41),
            new Primary("Deep purple", 0xFF673AB7, 0xFF251540, 0xFF372060, 0xFF4A2B80, 0xFF341E5B),
            new Primary("Purple", 0xFF9C27B0, 0xFF301235, 0xFF481B50, 0xFF60236B, 0xFF43194B),
            new Primary("Teal", 0xFF009688, 0xFF0B211F, 0xFF113430, 0xFF174540, 0xFF10302D),
            new Primary("Green", 0xFF4CAF50, 0xFF0F2310, 0xFF163418, 0xFF1E4520, 0xFF153117),
            new Primary("Red", 0xFFF44336, 0xFF351412, 0xFF501E1A, 0xFF6B2823, 0xFF4B1C19),
            new Primary("Brown", 0xFF795548, 0xFF271B17, 0xFF3B2923, 0xFF503930, 0xFF382721),
            new Primary("Blue grey", 0xFF607D8B, 0xFF182023, 0xFF243035, 0xFF314047, 0xFF222C31),
            new Primary("Grey", 0xFF9E9E9E, 0xFF1F1F1F, 0xFF2D2D2D, 0xFF3D3D3D, 0xFF2C2C2C),
            new Primary("Black", 0xFF000000, 0xFF0A0F13, 0xFF1B2026, 0xFF272D34, 0xFF181D22),
    };

    static final Accent[] ACCENTS = {
            new Accent("Red", 0xFFF44336, 0xFFEF9A9A, WHITE),
            new Accent("Pink", 0xFFE91E63, 0xFFF48FB1, WHITE),
            new Accent("Purple", 0xFF9C27B0, 0xFFCE93D8, WHITE),
            new Accent("Indigo", 0xFF3F51B5, 0xFF9FA8DA, WHITE),
            new Accent("Blue", 0xFF2196F3, 0xFF90CAF9, WHITE),
            new Accent("Cyan", 0xFF00BCD4, 0xFF80DEEA, DARK),
            new Accent("Teal", 0xFF009688, 0xFF80CBC4, WHITE),
            new Accent("Green", 0xFF4CAF50, 0xFFA5D6A7, DARK),
            new Accent("Lime", 0xFFCDDC39, 0xFFE6EE9C, DARK),
            new Accent("Yellow", 0xFFFFEB3B, 0xFFFFF59D, DARK),
            new Accent("Amber", 0xFFFFC107, 0xFFFFE082, DARK),
            new Accent("Orange", 0xFFFF9800, 0xFFFFCC80, DARK),
            new Accent("Deep orange", 0xFFFF5722, 0xFFFFAB91, WHITE),
    };

    /** Indigo • Blue: where every TV starts. */
    static final int INDIGO = 0, BLUE = 4;

    /** The guide's programme colours as the phone's guide has them (the guide's own look, until it is redrawn). */
    static final int[] DEEP = {0xFF213B32, 0xFF4A2323, 0xFF3B2550, 0xFF45421F, 0xFF233552, 0xFF4E3220, 0xFF1E3F4A, 0xFF4A2340};

    private static int primary = INDIGO, accent = BLUE;

    static int primary() {
        return primary;
    }

    static int accent() {
        return accent;
    }

    /** "Indigo • Blue". */
    static String name(int p, int a) {
        return PRIMARIES[clampP(p)].name + " • " + ACCENTS[clampA(a)].name;
    }

    /** The theme in use, as Settings shows it. */
    static String name() {
        return name(primary, accent);
    }

    private static int clampP(int p) {
        return p >= 0 && p < PRIMARIES.length ? p : INDIGO;
    }

    private static int clampA(int a) {
        return a >= 0 && a < ACCENTS.length ? a : BLUE;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE);
    }

    /** The primary kept on this TV (Indigo when none was chosen, or the TV had one of the old themes). */
    static int savedPrimary(Context c) {
        return clampP(prefs(c).getInt("primary", INDIGO));
    }

    static int savedAccent(Context c) {
        return clampA(prefs(c).getInt("accent", BLUE));
    }

    /** Kept on this TV (the old theme's number let go); the screens take it when they are made again. */
    static void save(Context c, int p, int a) {
        prefs(c).edit().putInt("primary", clampP(p)).putInt("accent", clampA(a)).remove("theme").apply();
    }

    /** The kept theme into the colours every screen draws with (before any is built). */
    static void apply(Context c) {
        apply(savedPrimary(c), savedAccent(c));
    }

    static void apply(int p, int a) {
        primary = clampP(p);
        accent = clampA(a);
        Primary pr = PRIMARIES[primary];
        Accent ac = ACCENTS[accent];
        Palette.GROUND = pr.ground;
        Palette.PANEL = pr.panel;
        Palette.HEADER = pr.header;
        Palette.PLAYBACK = Palette.alpha(pr.playback, Palette.PLAYBACK_ALPHA);
        Palette.ACCENT = ac.colour;
        Palette.ACCENT_LIGHT = ac.light;
        Palette.ACCENT_INK = ac.ink;
        Palette.FOCUS = Palette.alpha(ac.colour, Palette.FOCUS_ALPHA);
        // The screens still drawn in Dark's colours take the theme's too.
        Dark.GROUND = pr.ground;
        Dark.RAIL = pr.panel;
        Dark.PANEL = pr.panel;
        Dark.CARD = pr.panel;
        Dark.CARD_HI = pr.header;
        Dark.ACCENT = ac.colour;
        Dark.ACCENT_INK = ac.ink;
        Dark.ACCENT_SOFT = ac.light;
        Dark.BADGE = ac.colour;
        Dark.RING = ac.light;
        Dark.SOFT = Palette.over(pr.ground, 0xCCFFFFFF);
        Dark.MUTED = Palette.over(pr.ground, Palette.TEXT2);
        Dark.FAINT = Palette.over(pr.ground, Palette.TEXT3);
        System.arraycopy(DEEP, 0, Dark.DEEP, 0, DEEP.length);
    }

    /** Pure: a + (b - a) × t in each of red, green and blue; a's alpha kept. */
    static int mix(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }
}
