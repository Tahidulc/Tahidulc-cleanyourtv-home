package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Each channel's own way with the scoreboard and Homefield's mark over its logo (the player's options, "This channel"):
 * as the rest (the general settings), always up (as up by hand, whenever the channel plays: the logo's mark where its
 * logo is, or top right, the scoreboard for the game on it) or never on it; and its own logo look, else the general
 * one. Kept on this phone by the channel's key.
 */
final class ChannelOverlays {
    private ChannelOverlays() {}

    /** As the rest, always up, never on this channel. */
    static final String SAME = "", ALWAYS = "on", NEVER = "off";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("channel_overlays", Context.MODE_PRIVATE);
    }

    static String mode(Context c, String key) {
        if (key == null || key.isEmpty()) return SAME;
        String m = prefs(c).getString("m." + key, SAME);
        return ALWAYS.equals(m) || NEVER.equals(m) ? m : SAME;
    }

    static void setMode(Context c, String key, String m) {
        if (key == null || key.isEmpty()) return;
        if (ALWAYS.equals(m) || NEVER.equals(m)) prefs(c).edit().putString("m." + key, m).apply();
        else prefs(c).edit().remove("m." + key).apply();
    }

    /** The channel's own logo look; null for the general one. */
    static LogoCover.Look look(Context c, String key) {
        String s = key == null || key.isEmpty() ? null : prefs(c).getString("l." + key, null);
        return s == null ? null : LogoCover.Look.of(s);
    }

    static void setLook(Context c, String key, LogoCover.Look l) {
        if (key == null || key.isEmpty()) return;
        if (l == null) prefs(c).edit().remove("l." + key).remove("s." + key).apply();
        else prefs(c).edit().putString("l." + key, l.name()).apply();
    }

    /** The channel's own one of the Whitebrook Road styles (its own look being those); -1 for the general one. */
    static int style(Context c, String key) {
        return key == null || key.isEmpty() ? -1 : prefs(c).getInt("s." + key, -1);
    }

    static void setStyle(Context c, String key, int n) {
        if (key == null || key.isEmpty()) return;
        prefs(c).edit().putInt("s." + key, BrandStyles.clamp(n)).apply();
    }

    /** Pure: the look shown on a channel, its own or the general one. */
    static LogoCover.Look lookOf(LogoCover.Look own, LogoCover.Look general) {
        return own != null ? own : general;
    }
}
