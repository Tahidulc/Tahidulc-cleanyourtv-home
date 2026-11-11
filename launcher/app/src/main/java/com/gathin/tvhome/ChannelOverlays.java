package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Each channel's own way with the scoreboard and the cover over its logo (BoardCover): as the rest (the general
 * settings), always up (as up by hand, whenever the channel is on: the logo's cover, the scoreboard for the game found
 * on it) or never on it; and its own logo look, or the channel's own logo left uncovered, else the general look. Kept
 * by the channel's name without its picture's tag (InStep.base), so its HD and 4K forms share it. Set from the menu OK
 * held in the player opens, and the Scoreboard card's "This channel" rows (the channel last on).
 */
final class ChannelOverlays {
    private ChannelOverlays() { }

    /** As the rest, always up, never on this channel. */
    static final String SAME = "", ALWAYS = "on", NEVER = "off";
    /** Its own look: none, the channel's own logo left as it is. */
    static final String LEAVE = "LEAVE";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("channel_overlays", Context.MODE_PRIVATE);
    }

    /** Pure: the key a channel's settings are kept under ("" when not known). */
    static String key(String channel) {
        return InStep.base(channel);
    }

    static String mode(Context c, String channel) {
        String k = key(channel);
        if (k.isEmpty()) return SAME;
        String m = prefs(c).getString("m." + k, SAME);
        return ALWAYS.equals(m) || NEVER.equals(m) ? m : SAME;
    }

    static void setMode(Context c, String channel, String m) {
        String k = key(channel);
        if (k.isEmpty()) return;
        if (ALWAYS.equals(m) || NEVER.equals(m)) prefs(c).edit().putString("m." + k, m).apply();
        else prefs(c).edit().remove("m." + k).apply();
    }

    /** The channel's own look: a LogoMark.Look's name, LEAVE, or null for the general one. */
    static String look(Context c, String channel) {
        String k = key(channel);
        return k.isEmpty() ? null : prefs(c).getString("l." + k, null);
    }

    static void setLook(Context c, String channel, String look) {
        String k = key(channel);
        if (k.isEmpty()) return;
        if (look == null || look.isEmpty()) prefs(c).edit().remove("l." + k).remove("s." + k).apply();
        else prefs(c).edit().putString("l." + k, look).apply();
    }

    /** The channel's own one of the Whitebrook Road styles (its own look being those); -1 for the general one. */
    static int style(Context c, String channel) {
        String k = key(channel);
        return k.isEmpty() ? -1 : prefs(c).getInt("s." + k, -1);
    }

    static void setStyle(Context c, String channel, int n) {
        String k = key(channel);
        if (k.isEmpty()) return;
        prefs(c).edit().putInt("s." + k, BrandStyles.clamp(n)).apply();
    }

    /** Pure: the next mode, step on (1) or back (-1): as the rest, always up, never, round. */
    static String nextMode(String now, int step) {
        String[] all = {SAME, ALWAYS, NEVER};
        int at = 0;
        for (int i = 0; i < all.length; i++) if (all[i].equals(now)) at = i;
        return all[((at + step) % all.length + all.length) % all.length];
    }

    /** Pure: the next own look, step on or back: the general one (null), the channel's own left, then each look, round. */
    static String nextLook(String now, int step) {
        List<String> all = new ArrayList<>();
        all.add(null);
        all.add(LEAVE);
        for (LogoMark.Look l : LogoMark.Look.values()) all.add(l.name());
        int at = all.indexOf(now);
        if (at < 0) at = 0;
        return all.get(((at + step) % all.size() + all.size()) % all.size());
    }

    /** The keys of the channels with settings of their own. */
    static java.util.Set<String> keys(Context c) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String k : prefs(c).getAll().keySet()) if (k.length() > 2 && (k.startsWith("m.") || k.startsWith("l."))) out.add(k.substring(2));
        return out;
    }

    /**
     * Pure: the one channel with settings of its own that the player's screen names (its banner as the channel
     * changes: the name, after any number), as it is written there; null when none, or more than one (a list).
     */
    static String named(List<String> texts, java.util.Set<String> keys) {
        if (texts == null || keys == null || keys.isEmpty()) return null;
        String found = null, foundKey = null;
        for (String t : texts) {
            if (t == null) continue;
            String name = t.trim().replaceFirst("^\\d+\\s*[.:|-]?\\s*", "");
            String k = key(name);
            if (k.isEmpty() || !keys.contains(k)) continue;
            if (foundKey != null && !foundKey.equals(k)) return null;
            foundKey = k;
            found = name;
        }
        return found;
    }

    /** Pure: a mode in words. */
    static String modeWords(String m) {
        return ALWAYS.equals(m) ? "Always up" : NEVER.equals(m) ? "Never" : "As the rest";
    }

    /** Pure: an own look in words (general: what the rest have). */
    static String lookWords(String own, String general) {
        if (own == null) return "As the rest (" + general + ")";
        if (LEAVE.equals(own)) return "Leave the channel's own";
        return LogoMark.Look.of(own).label;
    }
}
