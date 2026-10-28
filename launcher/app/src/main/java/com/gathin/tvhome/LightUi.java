package com.gathin.tvhome;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A theme's "light_ui": Home on a light wallpaper. What is drawn straight on the wallpaper (the clock, the date,
 * names under tiles, tabs, the sports section) turns from white to a dark ink of the same strength; what is drawn on
 * a picture or a pill keeps its own colours.
 */
final class LightUi {
    static final int INK = 0xFF1D2128;

    private static boolean on;

    private LightUi() {
    }

    static void set(boolean light) {
        on = light;
    }

    static boolean on() {
        return on;
    }

    /** A white (or near white) colour as ink with the same alpha when the UI is light; any other colour as it is. */
    static int ink(int color) {
        if (!on) return color;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        if (r < 0xD0 || g < 0xD0 || b < 0xD0) return color;
        return (color & 0xFF000000) | (INK & 0x00FFFFFF);
    }

    /** Every white text in the tree as ink, but for text that follows its own state (focused pills). */
    static void inkTree(View v) {
        if (!on || v == null) return;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (!t.getTextColors().isStateful()) t.setTextColor(ink(t.getCurrentTextColor()));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) inkTree(g.getChildAt(i));
        }
    }
}
