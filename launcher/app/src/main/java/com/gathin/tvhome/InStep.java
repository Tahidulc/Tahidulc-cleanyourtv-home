package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alerts in step with the stream: a channel in the channel app runs behind the real game (30 s to a minute or more,
 * a playlist's more than broadcast TV), and every football pop-up waited a fixed DEFAULT_MS after the scores' poll saw
 * it. For the game being watched (its channel tuned from Home, Watch on, the phone, or the game chosen in Match mode)
 * the wait is that channel's own: lined up once by OK at kick-off (InStepCard: the poll saw kick-off at one moment,
 * OK says when the picture showed it), nudged by 5 s in Match mode, kept for each channel, the last one lined up used
 * for any channel not lined up yet. Other games' pop-ups wait DEFAULT_MS as before.
 *
 * <p>The sums are pure, for the tests; the channel tuned and the waits are kept in "in_step".
 */
final class InStep {
    private InStep() { }

    /** What every football pop-up waited before, and still does when nothing is lined up. */
    static final long DEFAULT_MS = 60_000L;
    /** The longest wait a line-up can set (a stream further behind than this is someone's pause). */
    static final long MAX_MS = 180_000L;
    /** A channel tuned counts as the one watched for this long. */
    static final long WATCH_FOR_MS = 4 * 3_600_000L;
    /** OK lines up with a kick-off the poll saw at most this long before. */
    static final long REFERENCE_MS = 5 * 60_000L;
    /** Each nudge in Match mode. */
    static final long STEP_MS = 5_000L;

    private static final String FILE = "in_step";
    /** When the poll saw each game kick off (or start its second half): what OK at kick-off lines up with. */
    private static final Map<String, Long> restarts = new HashMap<>();

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- the channel watched ----------------------------------------------------------------------------------------

    /** A channel tuned from the TV (AppSearchService.tune): the names tried, the first the one wanted. */
    static void tuned(Context c, List<String> names) {
        if (names == null || names.isEmpty()) return;
        prefs(c).edit().putString("tuned", String.join("\n", names)).putLong("tuned.at", System.currentTimeMillis()).apply();
    }

    /** The names last tuned, while they still count as what is on (else none). */
    static List<String> tunedNames(Context c) {
        SharedPreferences p = prefs(c);
        List<String> out = new ArrayList<>();
        if (System.currentTimeMillis() - p.getLong("tuned.at", 0) > WATCH_FOR_MS) return out;
        for (String n : p.getString("tuned", "").split("\n")) if (!n.trim().isEmpty()) out.add(n.trim());
        return out;
    }

    /** Pure: a channel's name to keep its wait under: letters and digits, lower case ("skysportsmaineventuhd"). */
    static String key(String channel) {
        return channel == null ? "" : channel.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Pure: a channel's name without its picture's tag (HD, FHD, UHD, 4K), for matching a game's channels. */
    static String base(String channel) {
        if (channel == null) return "";
        String s = channel.trim().replaceAll("(?i)^\\s*(uk|gb)\\s*[:|-]\\s*", "");
        s = s.replaceAll("(?i)\\s*\\b(f?hd|uhd|4k|sd|hevc)\\b\\s*$", "");
        return key(s);
    }

    /** Pure: whether one of the names tuned is one of a game's channels (by name, its HD or 4K tag aside). */
    static boolean onChannel(List<String> tuned, List<String> gameChannels) {
        if (tuned == null || gameChannels == null) return false;
        for (String t : tuned) {
            String bt = base(t);
            if (bt.isEmpty()) continue;
            for (String g : gameChannels) if (bt.equals(base(g))) return true;
        }
        return false;
    }

    // ---- the wait --------------------------------------------------------------------------------------------------

    /** Pure: the wait OK at pressed sets for a kick-off the poll saw at seen; -1 when that kick-off is too long ago. */
    static long hold(long seen, long pressed) {
        if (seen <= 0 || pressed - seen > REFERENCE_MS) return -1;
        return Math.max(0, Math.min(MAX_MS, pressed - seen));
    }

    /** Pure: a wait kept within 0 and MAX_MS. */
    static long clamp(long ms) {
        return Math.max(0, Math.min(MAX_MS, ms));
    }

    /** The channel a wait is kept for now: the first name tuned ("" when none: the last line-up then). */
    static String channel(Context c) {
        List<String> t = tunedNames(c);
        return t.isEmpty() ? "" : t.get(0);
    }

    /** This channel's own wait, or -1 when it has not been lined up. */
    static long own(Context c, String channel) {
        String k = key(channel);
        return k.isEmpty() ? -1 : prefs(c).getLong("h." + k, -1);
    }

    /** The wait for the channel watched now: its own, else the last lined up anywhere, else -1. */
    static long current(Context c) {
        long o = own(c, channel(c));
        return o >= 0 ? o : prefs(c).getLong("h.last", -1);
    }

    /**
     * How long a game's pop-up waits after the poll saw it: the watched channel's wait for the game being watched
     * (watched true), DEFAULT_MS for any other game or when nothing is lined up.
     */
    static long holdMs(Context c, boolean watched) {
        if (!watched) return DEFAULT_MS;
        long h = current(c);
        return h >= 0 ? h : DEFAULT_MS;
    }

    private static void save(Context c, long ms) {
        String k = key(channel(c));
        SharedPreferences.Editor e = prefs(c).edit().putLong("h.last", ms).putBoolean("again", false);
        if (!k.isEmpty()) e.putLong("h." + k, ms);
        e.apply();
    }

    // ---- lining up -------------------------------------------------------------------------------------------------

    /** The poll saw this game kick off or start its second half (main thread). */
    static void restarted(String gameId, long at) {
        if (gameId != null) restarts.put(gameId, at);
    }

    /** OK at the kick-off on screen: the wait for the channel watched, from that game's kick-off; -1 when none to go by. */
    static long lineUp(Context c, String gameId, long pressed) {
        Long seen = gameId == null ? null : restarts.get(gameId);
        long h = hold(seen == null ? 0 : seen, pressed);
        if (h >= 0) {
            save(c, h);
            AlertLog.note(c, "alerts lined up with the channel: " + words(h) + " (" + channel(c) + ")");
        }
        return h;
    }

    /** Match mode's nudge: the wait for the channel watched moved by delta (from DEFAULT_MS when not lined up). */
    static long nudge(Context c, long delta) {
        long now = current(c);
        long h = clamp((now >= 0 ? now : DEFAULT_MS) + delta);
        save(c, h);
        return h;
    }

    /** Asked to line up again at the next kick-off (Match mode). */
    static void askAgain(Context c) {
        prefs(c).edit().putBoolean("again", true).apply();
    }

    /** Whether to ask (with the card) at a kick-off: the channel has no wait of its own yet, or asked to again. */
    static boolean wantsLineUp(Context c) {
        if (!prefs(c).getBoolean("ask", true)) return false;
        return prefs(c).getBoolean("again", false) || own(c, channel(c)) < 0;
    }

    static boolean asking(Context c) {
        return prefs(c).getBoolean("ask", true);
    }

    static void setAsking(Context c, boolean on) {
        prefs(c).edit().putBoolean("ask", on).apply();
    }

    /** Pure: "38 s", "1 min 5 s". */
    static String words(long ms) {
        long s = Math.round(ms / 1000.0);
        return s >= 60 ? s / 60 + " min" + (s % 60 > 0 ? " " + s % 60 + " s" : "") : s + " s";
    }

    /** Match mode's line: "Held 38 s for Sky Sports Main Event", or that nothing is lined up yet. */
    static String describe(Context c) {
        long h = current(c);
        String ch = channel(c);
        if (h < 0) return "Not lined up yet: pop-ups wait " + words(DEFAULT_MS) + ". OK at kick-off when asked";
        return "Pop-ups held " + words(h) + (ch.isEmpty() ? "" : " for " + ch) + (own(c, ch) < 0 && !ch.isEmpty() ? " (another channel's)" : "");
    }
}
