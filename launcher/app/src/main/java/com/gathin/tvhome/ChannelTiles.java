package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;

import com.gathin.broadcast.LogoColour;
import com.gathin.broadcast.LogoHalo;
import com.gathin.broadcast.LogoTile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What is behind the channels' logos in the launcher's guides, channel tiles and What's on, chosen in the Launcher style
 * picker ("Behind logos"): Logo's colour (the default), None or Black tile. Logo's colour fills each logo's tile with a
 * rich, deeper shade of the logo's own colour (LogoColour: Channel 4's green on deep green; a white, black or grey mark
 * on a neutral dark), worked out once per logo as it is fetched and kept. None draws each logo as it is with nothing
 * behind it; only a logo too dark to see there gets a soft light halo behind its shape (LogoHalo). Black tile is 4.98's
 * look: each tile black, its logo made ready for black (a dark logo turned light) and trimmed, in the middle (LogoTile).
 * The playlist's logos and the logo sets' are drawn alike.
 *
 * <p>Black tiles were the default from 4.98; the family asked for each logo on its own colour, so every TV is switched to
 * Logo's colour once (the old switch, on unless turned off, cannot say whether a tile was chosen before or after that),
 * and whatever is chosen since is kept. A logo's address with MARK (made ready for its black tile), HALO (as it is, a halo
 * where it needs one) or COLOUR (as it is, its colour worked out) on its end asks the picture loaders (HomeV2, GuideV2,
 * WatchRows) for it so.
 */
final class ChannelTiles {
    private ChannelTiles() {
    }

    static final String MARK = "#tile", HALO = "#halo", COLOUR_MARK = "#colour";
    static final int GROUND = LogoTile.GROUND;
    /** The guides' dark ground, for the halo (every guide and channel tile is dark). */
    static final int DARK = 0xFF101114;
    /** Behind logos: nothing, a black tile, or the logo's own colour (the default). */
    static final int NONE = 0, TILE = 1, COLOUR = 2;
    private static final String PREFS = "channel_tiles";
    /** 4.98's switch (black tiles unless turned off), and the choice made since the default changed. */
    static final String OLD = "on", KEY = "behind";

    /** What is kept, as the switch reads it (the app's preferences; a map in the tests). */
    interface Kept {
        boolean has(String key);

        int get(String key, int otherwise);

        void put(String key, int value);
    }

    /**
     * Pure: what is behind the logos: the choice made since the default changed (chosen: NONE, TILE or COLOUR, null when
     * none was made yet), else Logo's colour, whatever 4.98's switch says (old: null when never touched): the old
     * default, black tiles, is switched once, and a tile set on the old switch cannot say whether it was chosen before or
     * after.
     */
    static int behind(Integer chosen, Boolean old) {
        return chosen != null && (chosen == NONE || chosen == TILE || chosen == COLOUR) ? chosen : COLOUR;
    }

    /** What is behind the logos, the one-time switch made on the first read (Logo's colour written, unless chosen since). */
    static int behind(Kept k) {
        Integer chosen = k.has(KEY) ? k.get(KEY, COLOUR) : null;
        Boolean old = k.has(OLD) ? k.get(OLD, 1) != 0 : null;
        int v = behind(chosen, old);
        if (chosen == null) k.put(KEY, v);
        return v;
    }

    static int behind(Context c) {
        return behind(kept(c));
    }

    /** Black tiles behind the logos. */
    static boolean on(Context c) {
        return behind(c) == TILE;
    }

    /** Each logo on its own colour (the default). */
    static boolean coloured(Context c) {
        return behind(c) == COLOUR;
    }

    /** The family's choice in the Launcher style picker. */
    static void set(Context c, int behind) {
        kept(c).put(KEY, behind(behind, null));
    }

    private static Kept kept(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Kept() {
            @Override public boolean has(String key) {
                return p.contains(key);
            }

            @Override public int get(String key, int otherwise) {
                Object v = p.getAll().get(key);
                if (v instanceof Integer) return (Integer) v;
                if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
                return otherwise;
            }

            @Override public void put(String key, int value) {
                p.edit().putInt(key, value).apply();
            }
        };
    }

    /** A logo's address asking for it made ready for its black tile (null stays null). */
    static String url(String logo) {
        return mark(logo, MARK);
    }

    /** A logo's address asking for it as it is, with a halo where it needs one on the dark (null stays null). */
    static String haloUrl(String logo) {
        return mark(logo, HALO);
    }

    /** A logo's address asking for it as it is, its colour worked out for its tile (null stays null). */
    static String colourUrl(String logo) {
        return mark(logo, COLOUR_MARK);
    }

    private static String mark(String logo, String m) {
        return logo == null || logo.isEmpty() || marked(logo) ? logo : logo + m;
    }

    /** A logo's address as the choice behind the logos asks for it. */
    static String shown(Context c, String logo) {
        int b = behind(c);
        return b == TILE ? url(logo) : b == NONE ? haloUrl(logo) : colourUrl(logo);
    }

    static boolean marked(String url) {
        return url != null && (url.endsWith(MARK) || url.endsWith(HALO) || url.endsWith(COLOUR_MARK));
    }

    static boolean haloed(String url) {
        return url != null && url.endsWith(HALO);
    }

    static boolean colourMarked(String url) {
        return url != null && url.endsWith(COLOUR_MARK);
    }

    static String unmarked(String url) {
        if (url == null) return null;
        for (String m : new String[]{MARK, HALO, COLOUR_MARK}) if (url.endsWith(m)) return url.substring(0, url.length() - m.length());
        return url;
    }

    /** A loaded logo made ready for black and trimmed (it is let go); as it was when nothing comes of it. */
    static Bitmap ready(Bitmap b) {
        if (b == null) return null;
        Bitmap r = LogoTile.ready(b);
        if (r == null) return b;
        if (r != b) b.recycle();
        return r;
    }

    /** A loaded logo as it is, with a soft light halo behind its shape when it is too dark for the guides' ground. */
    static Bitmap halo(Bitmap b) {
        if (b == null) return null;
        Bitmap h = LogoHalo.apply(b, DARK);
        if (h != b) b.recycle();
        return h;
    }

    /** Each logo's tile colours ({top, foot}), worked out once as it is fetched, by its address (unmarked). */
    private static final Map<String, int[]> COLOURS = new LinkedHashMap<String, int[]>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, int[]> e) {
            return size() > 1500;
        }
    };

    /** A loaded logo kept as it is, its tile's colours worked out (off the main thread) and kept for its address. */
    static Bitmap coloured(String url, Bitmap b) {
        if (b == null || url == null) return b;
        int[] t = LogoColour.tile(b);
        synchronized (COLOURS) {
            COLOURS.put(unmarked(url), t);
        }
        return b;
    }

    /** A logo's tile colours once worked out ({top, foot}), by its address (marked or not); null until then. */
    static int[] colourOf(String url) {
        if (url == null) return null;
        synchronized (COLOURS) {
            return COLOURS.get(unmarked(url));
        }
    }

    /** A tile in a logo's colours, a soft gradient top to foot, its corners round by radius px. */
    static GradientDrawable tile(int[] colours, float radius) {
        int[] c = colours != null && colours.length >= 2 ? colours : LogoColour.gradient(LogoColour.NEUTRAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c[0], c[1]});
        g.setCornerRadius(radius);
        return g;
    }

    /** The colours a logo's tile and row have while its own are worked out: the neutral dark. */
    static int[] waiting() {
        return LogoColour.waiting();
    }

    /**
     * A channel row's ground (its number, logo and name together) in the logo's row colours ({tile top, tile foot, row
     * top, row foot}): a touch lighter and richer than its tile, deep enough for the white writing on it; ringed in
     * white when it is the one chosen.
     */
    static GradientDrawable row(int[] colours, float radius, boolean chosen, int ring) {
        int[] c = colours != null && colours.length >= 4 ? colours : waiting();
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c[2], c[3]});
        g.setCornerRadius(radius);
        if (chosen) g.setStroke(Math.max(2, Math.round(radius / 4f)), ring);
        return g;
    }
}
