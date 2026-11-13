package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;

import com.gathin.broadcast.LogoTile;

/**
 * Channel logos on black tiles in the launcher's guides and channel tiles (LogoTile), as the phone and Homefield TV draw
 * them: each tile black, its logo made ready for black (a dark logo turned light) and trimmed, in the middle with the
 * same room all round. On unless switched off in the Launcher style picker. A logo's address with MARK on its end asks
 * the picture loaders (HomeV2, GuideV2) for it made ready.
 */
final class ChannelTiles {
    private ChannelTiles() {
    }

    static final String MARK = "#tile";
    static final int GROUND = LogoTile.GROUND;
    private static final String PREFS = "channel_tiles", KEY = "on";

    static boolean on(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    static void set(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    /** A logo's address asking for it made ready for its tile (null stays null). */
    static String url(String logo) {
        return logo == null || logo.isEmpty() || marked(logo) ? logo : logo + MARK;
    }

    static boolean marked(String url) {
        return url != null && url.endsWith(MARK);
    }

    static String unmarked(String url) {
        return marked(url) ? url.substring(0, url.length() - MARK.length()) : url;
    }

    /** A loaded logo made ready for black and trimmed (it is let go); as it was when nothing comes of it. */
    static Bitmap ready(Bitmap b) {
        if (b == null) return null;
        Bitmap r = LogoTile.ready(b);
        if (r == null) return b;
        if (r != b) b.recycle();
        return r;
    }
}
