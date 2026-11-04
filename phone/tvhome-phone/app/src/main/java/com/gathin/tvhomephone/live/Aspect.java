package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How the player fits the picture to the screen: Fit (all of it, bars where the shapes differ), Fill (zoomed to fill,
 * the edges cut), Stretch, or a forced 16:9 or 4:3 for a stream that says the wrong shape. A screen on its side (and the
 * guide's 16:9 picture) fills by default; upright, the picture is fitted to the width. A choice is remembered per
 * channel on this phone, for each way round; the default isn't stored.
 */
enum Aspect {
    FIT(0f), FILL(0f), STRETCH(0f), WIDE(16f / 9f), FOUR_THREE(4f / 3f);

    /** Choices on the side (and in the guide's picture); upright ones. */
    private static final String PREFIX = "aspect:", PORTRAIT = "aspect-portrait:";

    /** A forced width over height, or 0 for the stream's own shape. */
    final float ratio;

    Aspect(float ratio) {
        this.ratio = ratio;
    }

    /** Double tap: Fit and Fill in turn (anything else back to Fit). */
    Aspect toggled() {
        return this == FIT ? FILL : FIT;
    }

    /** After a pinch that scaled by this much: spread out to Fill, pinched in to Fit, otherwise as it was. */
    Aspect pinched(float scale) {
        if (scale > 1.08f) return FILL;
        if (scale < 0.92f) return FIT;
        return this;
    }

    /** Wider than tall (a screen on its side, the guide's picture): Fill; upright: Fit, the picture across the width. */
    static Aspect defaultFor(boolean landscape) {
        return landscape ? FILL : FIT;
    }

    /** A stored name, or null for none (or one this version doesn't know). */
    static Aspect parse(String name) {
        if (name == null) return null;
        for (Aspect a : values()) if (a.name().equals(name)) return a;
        return null;
    }

    /** Where the choices are kept: the phone's preferences, or a map in a test. */
    interface Store {
        String get(String key);

        void put(String key, String value);

        void remove(String key);
    }

    private static String key(String channelKey, boolean landscape) {
        return (landscape ? PREFIX : PORTRAIT) + channelKey;
    }

    /** The shape for a channel this way round: the one picked for it, else the default. */
    static Aspect get(Store s, String channelKey, boolean landscape) {
        Aspect picked = channelKey == null ? null : parse(s.get(key(channelKey, landscape)));
        return picked != null ? picked : defaultFor(landscape);
    }

    /** Remembers a pick for the channel this way round (the default by forgetting it). */
    static void set(Store s, String channelKey, boolean landscape, Aspect a) {
        if (channelKey == null) return;
        if (a == null || a == defaultFor(landscape)) s.remove(key(channelKey, landscape));
        else s.put(key(channelKey, landscape), a.name());
    }

    /** The player's own preferences on this phone (with the gestures hint's flag and the last channel). */
    static Store prefs(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE);
        return new Store() {
            @Override public String get(String key) { return p.getString(key, null); }

            @Override public void put(String key, String value) { p.edit().putString(key, value).apply(); }

            @Override public void remove(String key) { p.edit().remove(key).apply(); }
        };
    }
}
