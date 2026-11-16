package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Settings → Player, Sound and Stream problems: how much the player keeps ahead (live TV, and films and episodes, each
 * its own), the decoders, the picture's shape (each channel's remembered), matching the TV's refresh rate, the sound
 * (passed through to an amplifier or decoded, two speakers, languages, its delay), tunnelled playback, reconnecting and
 * backup copies, a playlist's own user agent, and how long the channel banner stays. Kept on this TV.
 */
public final class PlayerPrefs {
    private PlayerPrefs() {}

    static final String FILE = "player";

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- buffer ---------------------------------------------------------------------------------------------------

    public static final int MINIMAL = 0, SMALL = 1, NORMAL = 2, LARGE = 3, VERY_LARGE = 4;
    public static final String[] BUFFER_NAMES = {"Minimal", "Small", "Normal", "Large", "Very large"};
    public static final String[] BUFFER_ABOUT = {"Starts at once; stalls on a weak connection", "Quicker to start",
            "As Homefield came", "Fewer stalls", "Fewest stalls; slowest to start"};
    /** The one-line hint under the buffer's choices. */
    public static final String BUFFER_HINT = "More buffer means fewer stalls, but a channel takes a little longer to start.";

    /**
     * Pure: a buffer size's {least kept, most kept, kept before it starts, kept before it starts again after a stall},
     * in milliseconds. Live TV keeps less (it is close to live); films and episodes more (Minimal is live TV's only).
     */
    public static int[] bufferMs(boolean live, int size) {
        int s = Math.max(live ? MINIMAL : SMALL, Math.min(VERY_LARGE, size));
        if (live) {
            switch (s) {
                case MINIMAL: return new int[]{2_000, 8_000, 500, 1_000};
                case SMALL: return new int[]{8_000, 25_000, 1_000, 2_000};
                case LARGE: return new int[]{30_000, 90_000, 2_500, 5_000};
                case VERY_LARGE: return new int[]{60_000, 180_000, 5_000, 10_000};
                default: return new int[]{15_000, 50_000, 1_000, 2_000};
            }
        }
        switch (s) {
            case SMALL: return new int[]{10_000, 30_000, 1_000, 2_000};
            case LARGE: return new int[]{40_000, 120_000, 2_500, 5_000};
            case VERY_LARGE: return new int[]{90_000, 300_000, 5_000, 10_000};
            default: return new int[]{20_000, 60_000, 1_500, 3_000};
        }
    }

    public static int buffer(Context c, boolean live) {
        int v = prefs(c).getInt(live ? "buffer.live" : "buffer.vod", NORMAL);
        return Math.max(live ? MINIMAL : SMALL, Math.min(VERY_LARGE, v));
    }

    public static void setBuffer(Context c, boolean live, int size) {
        prefs(c).edit().putInt(live ? "buffer.live" : "buffer.vod", size).apply();
    }

    // ---- decoder --------------------------------------------------------------------------------------------------

    public static final int DECODER_AUTO = 0, DECODER_HARDWARE = 1, DECODER_SOFTWARE = 2;
    public static final String[] DECODER_NAMES = {"Auto", "Hardware", "Software"};
    public static final String[] DECODER_ABOUT = {"The TV's own decoders first, the app's for sound it can't play",
            "The TV's hardware decoders only (the app's still for sound it can't play)",
            "Software decoders first: for a channel whose picture breaks up; harder work for the TV"};

    public static int decoder(Context c) {
        return clamp(prefs(c).getInt("decoder", DECODER_AUTO), 0, 2);
    }

    public static void setDecoder(Context c, int d) {
        prefs(c).edit().putInt("decoder", d).apply();
    }

    // ---- the picture's shape --------------------------------------------------------------------------------------

    public static final int FIT = 0, FILL = 1, ZOOM = 2, WIDE = 3, STANDARD = 4;
    public static final String[] ASPECT_NAMES = {"Fit", "Fill", "Zoom", "16:9", "4:3"};
    public static final String[] ASPECT_ABOUT = {"The whole picture, bars where its shape differs", "Stretched to the whole screen",
            "Fills the screen, its edges cut off", "Shown as 16:9", "Shown as 4:3"};

    /** The shape every channel starts with (Settings → Player → Picture). */
    public static int aspect(Context c) {
        return clamp(prefs(c).getInt("aspect", FIT), 0, 4);
    }

    public static void setAspect(Context c, int mode) {
        prefs(c).edit().putInt("aspect", mode).apply();
    }

    /** A channel's own shape (chosen in the player's menu while it was on), else the one every channel starts with. */
    public static int aspectFor(Context c, String channelKey) {
        if (channelKey == null || channelKey.isEmpty()) return aspect(c);
        try {
            JSONObject o = new JSONObject(prefs(c).getString("aspect.channels", "{}"));
            if (o.has(channelKey)) return clamp(o.optInt(channelKey, FIT), 0, 4);
        } catch (Exception ignored) {
            // the default
        }
        return aspect(c);
    }

    /** A channel's own shape kept (its default again when it is the same as every channel's). */
    public static void setAspectFor(Context c, String channelKey, int mode) {
        if (channelKey == null || channelKey.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(prefs(c).getString("aspect.channels", "{}"));
            if (mode == aspect(c)) o.remove(channelKey);
            else o.put(channelKey, mode);
            prefs(c).edit().putString("aspect.channels", o.toString()).apply();
        } catch (Exception ignored) {
            // not kept
        }
    }

    /** Films' and episodes' shape (one for all of them). */
    public static int vodAspect(Context c) {
        return clamp(prefs(c).getInt("aspect.vod", FIT), 0, 4);
    }

    public static void setVodAspect(Context c, int mode) {
        prefs(c).edit().putInt("aspect.vod", mode).apply();
    }

    // ---- the TV's refresh rate, tunnelling ------------------------------------------------------------------------

    /** Match the TV's refresh rate to the programme's frames a second (a 50 Hz channel on a 60 Hz TV judders). */
    public static boolean matchFrameRate(Context c) {
        return prefs(c).getBoolean("afr", false);
    }

    public static void setMatchFrameRate(Context c, boolean on) {
        prefs(c).edit().putBoolean("afr", on).apply();
    }

    public static boolean tunnelled(Context c) {
        return prefs(c).getBoolean("tunnel", false);
    }

    public static void setTunnelled(Context c, boolean on) {
        prefs(c).edit().putBoolean("tunnel", on).apply();
    }

    // ---- sound ----------------------------------------------------------------------------------------------------

    /** Dolby Digital, Dolby Digital Plus and DTS sent as they are to an amplifier or soundbar that can play them. */
    public static boolean passthrough(Context c) {
        return prefs(c).getBoolean("passthrough", true);
    }

    public static void setPassthrough(Context c, boolean on) {
        prefs(c).edit().putBoolean("passthrough", on).apply();
    }

    /** Surround sound mixed down to two speakers (then decoded here, never passed through). */
    public static boolean downmix(Context c) {
        return prefs(c).getBoolean("downmix", false);
    }

    public static void setDownmix(Context c, boolean on) {
        prefs(c).edit().putBoolean("downmix", on).apply();
    }

    /** The sound's languages, most wanted first (ISO 639-1 codes); the TV's own language until chosen. */
    public static List<String> languages(Context c) {
        String s = prefs(c).getString("languages", "");
        List<String> out = new ArrayList<>();
        for (String code : s.split(",")) {
            String t = code.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        if (out.isEmpty()) out.add(Locale.getDefault().getLanguage().isEmpty() ? "en" : Locale.getDefault().getLanguage());
        return out;
    }

    public static void setLanguages(Context c, List<String> codes) {
        prefs(c).edit().putString("languages", String.join(",", codes)).apply();
    }

    /** The languages offered, with their names (in the order Settings shows them). */
    public static final String[] LANGUAGE_CODES = {"en", "ar", "bn", "de", "es", "fr", "hi", "it", "nl", "pl", "pt", "ro", "tr", "ur"};

    public static String languageName(String code) {
        String n = new Locale(code).getDisplayLanguage(Locale.UK);
        return n.isEmpty() ? code : n;
    }

    /** Pure: the order kept after a language is ticked (to the end) or unticked; never empty. */
    public static List<String> toggleLanguage(List<String> order, String code) {
        List<String> out = new ArrayList<>(order);
        if (out.contains(code)) {
            if (out.size() > 1) out.remove(code);
        } else {
            out.add(code);
        }
        return out;
    }

    /** How much later than the picture the sound comes, in ms (negative: earlier); -1000 to +1000, 50 at a time. */
    public static int delayMs(Context c) {
        return clamp(prefs(c).getInt("delay", 0), -DELAY_MAX, DELAY_MAX);
    }

    public static final int DELAY_MAX = 1000, DELAY_STEP = 50;

    public static void setDelayMs(Context c, int ms) {
        int v = clamp(Math.round(ms / (float) DELAY_STEP) * DELAY_STEP, -DELAY_MAX, DELAY_MAX);
        prefs(c).edit().putInt("delay", v).apply();
        PlayerSetup.delayUs = v * 1000L;
    }

    /** Pure: the delay in words ("In step", "Sound 150 ms later", "Sound 100 ms earlier"). */
    public static String delayWords(int ms) {
        if (ms == 0) return "In step";
        return "Sound " + Math.abs(ms) + " ms " + (ms > 0 ? "later" : "earlier");
    }

    // ---- stream problems ------------------------------------------------------------------------------------------

    /** How often a dropped channel's stream is connected again: -1 for as long as it is watched (the default). */
    public static final int[] RETRY_COUNTS = {0, 2, 4, 8, -1};
    public static final String[] RETRY_NAMES = {"Don't try again", "2 times", "4 times", "8 times", "Keep trying"};

    public static int retries(Context c) {
        return prefs(c).getInt("retries", -1);
    }

    public static void setRetries(Context c, int n) {
        prefs(c).edit().putInt("retries", n).apply();
    }

    /**
     * When a stream fails: Off (the default) stays on the channel, reconnecting the same stream and letting the picture
     * adapt to the connection; On switches to the channel's backup copy in the family's other playlists and groups (the
     * best picture first) once this one has been tried again a little, as Homefield did before.
     */
    public static boolean switchToBackup(Context c) {
        return prefs(c).getBoolean("backup.switch", false);
    }

    public static void setSwitchToBackup(Context c, boolean on) {
        prefs(c).edit().putBoolean("backup.switch", on).apply();
    }

    /** Pure: how long before connecting again, attempt 1, 2, 3…: 1, 2, 4, then 8 s each time. */
    public static long backoffMs(int attempt) {
        return 1000L << Math.max(0, Math.min(3, attempt - 1));
    }

    // ---- picture quality ------------------------------------------------------------------------------------------

    public static final int QUALITY_AUTO = 0, QUALITY_HIGHEST = 1, QUALITY_SAVER = 2;
    public static final String[] QUALITY_NAMES = {"Auto", "Highest", "Data saver"};
    public static final String[] QUALITY_ABOUT = {"Adapts to the connection: down at once when it slows, up once it's steady",
            "Always the stream's best picture", "At most 576p and about 2 Mbit/s"};

    /** For streams that come in several pictures (most HLS channels): which the player picks. */
    public static int quality(Context c) {
        return clamp(prefs(c).getInt("quality", QUALITY_AUTO), 0, 2);
    }

    public static void setQuality(Context c, int q) {
        prefs(c).edit().putInt("quality", q).apply();
    }

    /** The provider's adaptive (HLS) stream tried first for a channel given as MPEG-TS, kept only where it plays. */
    public static boolean hlsFirst(Context c) {
        return prefs(c).getBoolean("hls.first", true);
    }

    public static void setHlsFirst(Context c, boolean on) {
        prefs(c).edit().putBoolean("hls.first", on).apply();
    }

    /** A playlist's own user agent for its streams ("" for the app's, or the channel's own). */
    public static String agentFor(Context c, String playlistId) {
        return prefs(c).getString("agent." + playlistId, "");
    }

    public static void setAgentFor(Context c, String playlistId, String agent) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (agent == null || agent.trim().isEmpty()) e.remove("agent." + playlistId);
        else e.putString("agent." + playlistId, agent.trim());
        e.apply();
    }

    /** The user agent a channel's stream is asked for with: its playlist's own when set, else the channel's own. */
    public static String agent(Context c, String channelKey, String own) {
        String set = agentFor(c, playlistOf(channelKey));
        return set.isEmpty() ? own : set;
    }

    /** Pure: which playlist a channel's key is from: "p0" for the first (no prefix), else its prefix ("p2"). */
    public static String playlistOf(String key) {
        if (key != null && key.length() > 2 && key.charAt(0) == 'p') {
            int colon = key.indexOf(':');
            if (colon > 1 && key.substring(1, colon).matches("\\d+")) return key.substring(0, colon);
        }
        return "p0";
    }

    // ---- the channel banner ---------------------------------------------------------------------------------------

    public static final int[] BANNER_SECONDS = {0, 3, 6, 10};
    public static final String[] BANNER_NAMES = {"Off", "3 seconds", "6 seconds", "10 seconds"};

    /** How long the banner stays after changing channel (0: it doesn't come up then; OK still brings it). */
    public static int bannerSeconds(Context c) {
        return prefs(c).getInt("banner", 6);
    }

    public static void setBannerSeconds(Context c, int s) {
        prefs(c).edit().putInt("banner", s).apply();
    }

    /** How long the banner stays in ms, the app's own when it is off (OK brought it up). */
    public static long bannerMs(Context c, long fallback) {
        int s = bannerSeconds(c);
        return s <= 0 ? fallback : s * 1000L;
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Pure: the index of a value in a list of them (0 when it isn't there). */
    public static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    /** Every key Settings keeps here, for Backup (Settings → Backup). */
    public static List<String> keys(Context c) {
        return new ArrayList<>(prefs(c).getAll().keySet());
    }

    static final List<String> FILES = Arrays.asList(FILE);
}
