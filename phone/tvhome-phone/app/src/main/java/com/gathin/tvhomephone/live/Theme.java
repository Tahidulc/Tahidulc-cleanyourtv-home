package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.glass.Glass;

/**
 * Settings › Appearance › Colour theme, Homefield TV's: a primary colour (the pages' ground and the panels) with an
 * accent (what is on now: the guide's dot and time, the corner's day, the bar of how far through), named as a pair,
 * "Homefield • Amber". Homefield with Amber is the phone's own look as it always was (its near-black grounds, the 07
 * panels, amber for now); the other primaries tint every page's ground and the panels with their own hue at the TV's
 * values, the accents are the TV's Material colours. Kept on this phone only (as each TV keeps its own), under Homefield
 * TV's keys ("look": "primary7", "accent7"); the app is made again in it when one is chosen.
 */
public final class Theme {
    private Theme() {}

    /** A primary: the ground, a panel, a lighter panel and the panels over the picture. */
    static final class Primary {
        final String name;
        final int swatch, ground, panel, header, playback;

        Primary(String name, int swatch, int ground, int panel, int header, int playback) {
            this.name = name;
            this.swatch = swatch;
            this.ground = ground;
            this.panel = panel;
            this.header = header;
            this.playback = playback;
        }
    }

    /** An accent: its colour and its light shade. */
    static final class Accent {
        final String name;
        final int colour, light;

        Accent(String name, int colour, int light) {
            this.name = name;
            this.colour = colour;
            this.light = light;
        }
    }

    /** Homefield TV's primaries, in its order (Homefield, 07's neutral, last). */
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
            new Primary("Homefield", 0xFF2C2C33, 0xFF0B0B0F, 0xFF1D1D22, 0xFF2A2A31, 0xFF141418),
    };

    static final Accent[] ACCENTS = {
            new Accent("Red", 0xFFF44336, 0xFFEF9A9A),
            new Accent("Pink", 0xFFE91E63, 0xFFF48FB1),
            new Accent("Purple", 0xFF9C27B0, 0xFFCE93D8),
            new Accent("Indigo", 0xFF3F51B5, 0xFF9FA8DA),
            new Accent("Blue", 0xFF2196F3, 0xFF90CAF9),
            new Accent("Cyan", 0xFF00BCD4, 0xFF80DEEA),
            new Accent("Teal", 0xFF009688, 0xFF80CBC4),
            new Accent("Green", 0xFF4CAF50, 0xFFA5D6A7),
            new Accent("Lime", 0xFFCDDC39, 0xFFE6EE9C),
            new Accent("Yellow", 0xFFFFEB3B, 0xFFFFF59D),
            new Accent("Amber", 0xFFFFB547, 0xFFFFD08A),
            new Accent("Orange", 0xFFFF9800, 0xFFFFCC80),
            new Accent("Deep orange", 0xFFFF5722, 0xFFFFAB91),
    };

    static final int HOMEFIELD = 11, AMBER = 10;
    static final String PREFS = "look", PRIMARY_KEY = "primary7", ACCENT_KEY = "accent7";

    /** The phone's own grounds, as Homefield • Amber draws them: the glass pages', Live TV's and Settings' navy. */
    static final int GLASS_GROUND = 0xFF0E0F14, TV_GROUND = 0xFF0B0B0F, PAGE_GROUND = 0xFF0D0F2C;

    private static volatile int primary = HOMEFIELD, accent = AMBER;
    /** The ground of the pages that are neither glass nor Live TV (Settings): its navy, or the theme's ground. */
    private static volatile int page = PAGE_GROUND;

    static int primary() {
        return primary;
    }

    static int accent() {
        return accent;
    }

    /** Pure: "Homefield • Amber". */
    static String name(int p, int a) {
        return PRIMARIES[clampP(p)].name + " • " + ACCENTS[clampA(a)].name;
    }

    /** The pair in use, as Settings shows it. */
    public static String name() {
        return name(primary, accent);
    }

    static int clampP(int p) {
        return p >= 0 && p < PRIMARIES.length ? p : HOMEFIELD;
    }

    static int clampA(int a) {
        return a >= 0 && a < ACCENTS.length ? a : AMBER;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Kept on this phone; the screens take it when they are made again. */
    static void save(Context c, int p, int a) {
        prefs(c).edit().putInt(PRIMARY_KEY, clampP(p)).putInt(ACCENT_KEY, clampA(a)).apply();
        apply(clampP(p), clampA(a));
    }

    /** The kept theme into the colours the screens draw with (the app starting, before any screen is made). */
    public static void apply(Context c) {
        SharedPreferences p = prefs(c);
        apply(clampP(p.getInt(PRIMARY_KEY, HOMEFIELD)), clampA(p.getInt(ACCENT_KEY, AMBER)));
    }

    static void apply(int p, int a) {
        primary = clampP(p);
        accent = clampA(a);
        int[] g = grounds(primary);
        Glass.GROUND = g[0];
        TvLook.GROUND = g[1];
        page = g[2];
        int[] m = panels(primary);
        TvLook.MAT_TOP = m[0];
        TvLook.MAT_FOOT = m[1];
        TvLook.MAT_DEEP_TOP = m[2];
        TvLook.MAT_DEEP_FOOT = m[3];
        TvLook.AMBER = ACCENTS[accent].colour;
    }

    /** The ground of Settings (and any other page that isn't glass or Live TV). */
    public static int page() {
        return page;
    }

    /** A card on that ground (a tablet's list of Settings' sections): the navy's deeper shade, or the theme's panel. */
    public static int card() {
        return primary == HOMEFIELD ? 0xFF0B0C21 : PRIMARIES[primary].panel;
    }

    /** Pure: a primary's grounds {glass pages, Live TV, Settings}: the phone's own for Homefield, the hue's for the rest. */
    static int[] grounds(int p) {
        if (clampP(p) == HOMEFIELD) return new int[]{GLASS_GROUND, TV_GROUND, PAGE_GROUND};
        int g = PRIMARIES[clampP(p)].ground;
        return new int[]{g, g, g};
    }

    /**
     * Pure: a primary's panels over the picture and the ground {top, foot, deep top, deep foot}: 07's mat-dark for
     * Homefield; the hue's lighter panel over its panel-over-the-picture for the rest, at the same see-through.
     */
    static int[] panels(int p) {
        if (clampP(p) == HOMEFIELD) return new int[]{0xBD24242A, 0xD6121216, 0xEB24242A, 0xF2121216};
        Primary pr = PRIMARIES[clampP(p)];
        int top = pr.header & 0xFFFFFF, foot = pr.playback & 0xFFFFFF;
        return new int[]{0xBD000000 | top, 0xD6000000 | foot, 0xEB000000 | top, 0xF2000000 | foot};
    }

    /** Pure: the pair a choice makes, from the pair in use: {primary, accent} (a primary's i, or an accent's). */
    static int[] pick(boolean isPrimary, int i, int primary, int accent) {
        return isPrimary ? new int[]{clampP(i), clampA(accent)} : new int[]{clampP(primary), clampA(i)};
    }
}
