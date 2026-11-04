package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How the player fits the picture to the screen: Fit (all of it, bars where the shapes differ), Fill (zoomed to fill,
 * the edges cut), Stretch, or a forced 16:9 or 4:3 for a stream that says the wrong shape. Remembered per channel on
 * this phone; Fit is the default and isn't stored.
 */
enum Aspect {
    FIT(0f), FILL(0f), STRETCH(0f), WIDE(16f / 9f), FOUR_THREE(4f / 3f);

    private static final String PREFIX = "aspect:";

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

    static Aspect parse(String name) {
        if (name == null) return FIT;
        for (Aspect a : values()) if (a.name().equals(name)) return a;
        return FIT;
    }

    /** Where the choices are kept: the phone's preferences, or a map in a test. */
    interface Store {
        String get(String key);

        void put(String key, String value);

        void remove(String key);
    }

    static Aspect get(Store s, String channelKey) {
        return channelKey == null ? FIT : parse(s.get(PREFIX + channelKey));
    }

    static void set(Store s, String channelKey, Aspect a) {
        if (channelKey == null) return;
        if (a == null || a == FIT) s.remove(PREFIX + channelKey);
        else s.put(PREFIX + channelKey, a.name());
    }

    /** The player's own preferences on this phone (with the gestures hint's flag). */
    static Store prefs(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE);
        return new Store() {
            @Override public String get(String key) { return p.getString(key, null); }

            @Override public void put(String key, String value) { p.edit().putString(key, value).apply(); }

            @Override public void remove(String key) { p.edit().remove(key).apply(); }
        };
    }
}
