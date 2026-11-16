package com.gathin.homefieldtv;

import android.content.Context;

/**
 * The app's colour themes (Settings, Theme): Homefield's own dark look first, then Midnight, Emerald, Crimson and
 * Graphite. Each gives the dark screens their ground, rail, panels and cards, the accent (with its ink, its lighter
 * shade for the part of a programme already shown, and the New badge), the focus rings and the guide's programme
 * colours, and tints the blue screens (Home, Search, Settings) to match. Kept on the TV and applied as the app starts;
 * choosing another starts the screens again. A light theme was left out: the words over pictures, the fades into the
 * ground and a TV's brightness all want a dark one.
 */
final class Theme {
    final String name, about;
    final int ground, rail, panel, card, cardHi, accent, accentInk, accentSoft, badge, ring;
    /** The blue screens' tint: the hue they move to (below 0: Sky Q's own blues), their saturation and brightness as shares. */
    final float hue, sat, val;
    /** The guide's programme colours: the phone's, mixed this far towards tint. */
    final int tint;
    final float tintShare;

    private Theme(String name, String about, int ground, int rail, int panel, int card, int cardHi, int accent, int accentInk,
                  int accentSoft, int badge, int ring, float hue, float sat, float val, int tint, float tintShare) {
        this.name = name;
        this.about = about;
        this.ground = ground;
        this.rail = rail;
        this.panel = panel;
        this.card = card;
        this.cardHi = cardHi;
        this.accent = accent;
        this.accentInk = accentInk;
        this.accentSoft = accentSoft;
        this.badge = badge;
        this.ring = ring;
        this.hue = hue;
        this.sat = sat;
        this.val = val;
        this.tint = tint;
        this.tintShare = tintShare;
    }

    static final Theme[] ALL = {
            new Theme("Homefield", "Near-black with Homefield's amber (as it came)", 0xFF0E0E11, 0xFF141417, 0xFF131316, 0xFF1E1E23,
                    0xFF2A2A31, 0xFFFFB547, 0xFF1E1606, 0xFFFFD48A, 0xFFFFD25A, 0xFFFFFFFF, -1f, 1f, 1f, 0, 0f),
            new Theme("Midnight blue", "Deep navy with a sky-blue accent", 0xFF0A0F1C, 0xFF0E1426, 0xFF0D1324, 0xFF18213A,
                    0xFF223050, 0xFF5AB4FF, 0xFF06192B, 0xFFA9D8FF, 0xFF8FD0FF, 0xFFEAF4FF, 222f, 1f, 0.62f, 0xFF1B2A55, 0.4f),
            new Theme("Emerald", "Dark green with a mint accent", 0xFF08130F, 0xFF0C1A15, 0xFF0B1813, 0xFF15281F,
                    0xFF1E392D, 0xFF34D399, 0xFF04211A, 0xFF9BEBC9, 0xFF7EE7B8, 0xFFE9FFF6, 158f, 0.9f, 0.62f, 0xFF1B4A3A, 0.4f),
            new Theme("Crimson", "Dark wine with a coral-red accent", 0xFF150A0C, 0xFF1C0D10, 0xFF1A0C0F, 0xFF2A1418,
                    0xFF3A1C22, 0xFFFF5A67, 0xFF2B0609, 0xFFFFA9B0, 0xFFFF8A94, 0xFFFFEEF0, 352f, 0.85f, 0.66f, 0xFF4A1B24, 0.4f),
            new Theme("Graphite", "Soft charcoal greys with a silver accent", 0xFF16171A, 0xFF1C1D21, 0xFF1B1C20, 0xFF2A2C31,
                    0xFF383B42, 0xFFC9CED6, 0xFF15161A, 0xFFF1F3F6, 0xFFE6E8EC, 0xFFFFFFFF, 215f, 0.14f, 0.7f, 0xFF2A2C31, 0.6f),
    };

    /** The guide's programme colours as the phone's guide has them. */
    static final int[] DEEP = {0xFF213B32, 0xFF4A2323, 0xFF3B2550, 0xFF45421F, 0xFF233552, 0xFF4E3220, 0xFF1E3F4A, 0xFF4A2340};

    private static int current;

    static int current() {
        return current;
    }

    /** The theme kept on this TV (the first when none was chosen). */
    static int saved(Context c) {
        int i = c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).getInt("theme", 0);
        return i >= 0 && i < ALL.length ? i : 0;
    }

    static void save(Context c, int i) {
        c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).edit().putInt("theme", i).apply();
    }

    /** The kept theme into the colours every screen draws with (before any is built). */
    static void apply(Context c) {
        apply(saved(c));
    }

    static void apply(int i) {
        current = i >= 0 && i < ALL.length ? i : 0;
        Theme t = ALL[current];
        Dark.GROUND = t.ground;
        Dark.RAIL = t.rail;
        Dark.PANEL = t.panel;
        Dark.CARD = t.card;
        Dark.CARD_HI = t.cardHi;
        Dark.ACCENT = t.accent;
        Dark.ACCENT_INK = t.accentInk;
        Dark.ACCENT_SOFT = t.accentSoft;
        Dark.BADGE = t.badge;
        Dark.RING = t.ring;
        for (int k = 0; k < DEEP.length; k++) Dark.DEEP[k] = t.tintShare > 0f ? mix(DEEP[k], t.tint, t.tintShare) : DEEP[k];
        Ui.tint(t.hue, t.sat, t.val);
    }

    /** Pure: a + (b - a) × t in each of red, green and blue; a's alpha kept. */
    static int mix(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }

    /** Pure: a colour moved to a hue (below 0: its own), its saturation and brightness scaled; alpha kept. */
    static int shift(int c, float hue, float sat, float val) {
        if (hue < 0f && sat == 1f && val == 1f) return c;
        float[] h = PosterArt.hsv(c & 0xFFFFFF);
        float nh = hue < 0f ? h[0] : hue;
        float ns = Math.max(0f, Math.min(1f, h[1] * sat)), nv = Math.max(0f, Math.min(1f, h[2] * val));
        return (c & 0xFF000000) | PosterArt.rgb(nh, ns, nv);
    }
}
