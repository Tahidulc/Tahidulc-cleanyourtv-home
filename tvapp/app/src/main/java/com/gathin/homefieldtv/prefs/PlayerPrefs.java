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

    /**
     * Pure: the most memory a buffer size may hold, in bytes, whatever its seconds say: the player keeps its buffer in
     * the app's own memory, and a 4K stream's 50 seconds would be some 150 MB of it on a TV with 1–2 GB in all. Live TV
     * 12, 20, 32, 48 or 64 MB (Normal's 32 is about 10 seconds of 4K, 30 of HD); films and episodes 24, 40, 56 or 72 MB.
     * The seconds still decide first; this only stops a fast stream filling the TV's memory.
     */
    public static int bufferBytes(boolean live, int size) {
        int s = Math.max(live ? MINIMAL : SMALL, Math.min(VERY_LARGE, size));
        int[] mb = live ? new int[]{12, 20, 32, 48, 64} : new int[]{24, 24, 40, 56, 72};
        return mb[s] << 20;
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

    /**
     * The picture's shapes, named as the player's Display mode names them: Normal (the whole picture, bars where its shape
     * differs), Full (stretched), Zoom (fills the screen, its edges cut off), 16:9 and 4:3 (shown as that shape), and since
     * the player's Display mode: Zoom - 2.35:1 (a wide film inside a 16:9 picture filling the screen's height), Reduced
     * (the whole picture a little smaller) and 2.35:1 (shown as that shape). The numbers are kept: 0 to 4 as they were.
     */
    public static final int FIT = 0, FILL = 1, ZOOM = 2, WIDE = 3, STANDARD = 4, ZOOM_235 = 5, REDUCED = 6, CINEMA = 7;
    public static final String[] ASPECT_NAMES = {"Normal", "Full", "Zoom", "16:9", "4:3", "Zoom - 2.35:1", "Reduced", "2.35:1"};
    public static final String[] ASPECT_ABOUT = {"The whole picture, bars where its shape differs", "Stretched to the whole screen",
            "Fills the screen, its edges cut off", "Shown as 16:9", "Shown as 4:3",
            "A wide film inside a 16:9 picture, filling the screen's height", "The whole picture, a little smaller",
            "Shown as 2.35:1"};
    /** The shapes in the order the player's Display mode lists them. */
    public static final int[] DISPLAY_ORDER = {FIT, FILL, ZOOM, ZOOM_235, REDUCED, STANDARD, WIDE, CINEMA};
    private static final int LAST_SHAPE = 7;

    /** The shape every channel starts with (Settings → Player → Picture). */
    public static int aspect(Context c) {
        return clamp(prefs(c).getInt("aspect", FIT), 0, LAST_SHAPE);
    }

    public static void setAspect(Context c, int mode) {
        prefs(c).edit().putInt("aspect", mode).apply();
    }

    /** A channel's own shape (chosen in the player's menu while it was on), else the one every channel starts with. */
    public static int aspectFor(Context c, String channelKey) {
        if (channelKey == null || channelKey.isEmpty()) return aspect(c);
        try {
            JSONObject o = new JSONObject(prefs(c).getString("aspect.channels", "{}"));
            if (o.has(channelKey)) return clamp(o.optInt(channelKey, FIT), 0, LAST_SHAPE);
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

    /** The player's Display mode, Apply to all channels: the shape for every channel, none keeping its own. */
    public static void setAspectAll(Context c, int mode) {
        prefs(c).edit().putInt("aspect", clamp(mode, 0, LAST_SHAPE)).remove("aspect.channels").apply();
    }

    /** Films' and episodes' shape (one for all of them). */
    public static int vodAspect(Context c) {
        return clamp(prefs(c).getInt("aspect.vod", FIT), 0, LAST_SHAPE);
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

    /** A channel's own sound offset (the player's Audio offset), else every channel's (delayMs). */
    public static int delayFor(Context c, String channelKey) {
        if (channelKey == null || channelKey.isEmpty()) return delayMs(c);
        try {
            JSONObject o = new JSONObject(prefs(c).getString("delay.channels", "{}"));
            if (o.has(channelKey)) return clamp(o.optInt(channelKey, 0), -DELAY_MAX, DELAY_MAX);
        } catch (Exception ignored) {
            // every channel's
        }
        return delayMs(c);
    }

    /** A channel's own sound offset kept (none of its own when it is every channel's), and used now. */
    public static void setDelayFor(Context c, String channelKey, int ms) {
        if (channelKey == null || channelKey.isEmpty()) return;
        int v = clamp(Math.round(ms / (float) DELAY_STEP) * DELAY_STEP, -DELAY_MAX, DELAY_MAX);
        try {
            JSONObject o = new JSONObject(prefs(c).getString("delay.channels", "{}"));
            if (v == delayMs(c)) o.remove(channelKey);
            else o.put(channelKey, v);
            prefs(c).edit().putString("delay.channels", o.toString()).apply();
        } catch (Exception ignored) {
            // not kept
        }
        PlayerSetup.delayUs = v * 1000L;
    }

    /** Audio offset, Apply to all channels: every channel's offset, none keeping its own; used now. */
    public static void setDelayAll(Context c, int ms) {
        prefs(c).edit().remove("delay.channels").apply();
        setDelayMs(c, ms);
    }

    /** The sound's offset for what plays now: a channel's own (key), or every channel's (null: films, nothing). */
    public static void useDelay(Context c, String channelKey) {
        PlayerSetup.delayUs = delayFor(c, channelKey) * 1000L;
    }

    /** Pure: an offset as the player's Audio offset writes it: "0 ms", "+150 ms", "-100 ms". */
    public static String offsetWords(int ms) {
        return ms == 0 ? "0 ms" : (ms > 0 ? "+" : "-") + Math.abs(ms) + " ms";
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

    // ---- picture quality ------------------------------------------------------------------------------------------

    /**
     * Settings → Player → Picture quality, the one control for it (as the phone's, Homefield 5.22). Adaptive (the
     * default): the channel picked stays on screen while its picture follows the connection through the channel's own
     * versions (its FHD, HD and SD, each version's copies in the other playlists), stepping down before a freeze and back
     * up once steady (Ladder, Abr). Data saver: the same, never above 720p. A fixed quality (4K, 1080p, 720p, SD): that
     * version (the nearest under it when the channel has none), only connected again when it drops, never another copy.
     * The values are Names' levels (4 UHD, 3 FHD, 2 HD, 0 SD), ADAPTIVE and SAVER.
     */
    public static final int QUALITY_ADAPTIVE = -1, QUALITY_SAVER = -2;
    public static final int[] QUALITY_CHOICES = {QUALITY_ADAPTIVE, 4, 3, 2, 0, QUALITY_SAVER};
    public static final String[] QUALITY_NAMES = {"Adaptive", "4K", "1080p", "720p", "SD", "Data saver"};
    public static final String[] QUALITY_ABOUT = {
            "The channel stays on screen while its picture follows the connection: down before it freezes, back up once steady",
            "The channel's 4K version, else its best; when it drops, the same stream again",
            "The channel's 1080p version, or the nearest under it; when it drops, the same stream again",
            "The channel's 720p version, or the nearest under it; when it drops, the same stream again",
            "The channel's SD version, or its lowest; when it drops, the same stream again",
            "Adaptive, never above 720p"};

    public static int quality(Context c) {
        int q = prefs(c).getInt("quality", QUALITY_ADAPTIVE);
        for (int v : QUALITY_CHOICES) if (v == q) return q;
        return QUALITY_ADAPTIVE;
    }

    public static void setQuality(Context c, int q) {
        prefs(c).edit().putInt("quality", q).apply();
    }

    /** Pure: whether a choice follows the connection (Adaptive, Data saver) rather than holding one version. */
    public static boolean adaptive(int quality) {
        return quality == QUALITY_ADAPTIVE || quality == QUALITY_SAVER;
    }

    /** Pure: the highest level a choice lets the picture go to (Names' levels, 4 UHD … 0 SD). */
    public static int cap(int quality) {
        if (quality == QUALITY_ADAPTIVE) return 4;
        if (quality == QUALITY_SAVER) return 2;
        return Math.max(0, Math.min(4, quality));
    }

    /** Pure: the tallest picture an HLS stream may pick at a level (its own variants, for a stream that has them). */
    public static int maxHeight(int level) {
        switch (level) {
            case 4: return Integer.MAX_VALUE;
            case 3: return 1080;
            case 2: case 1: return 720;
            default: return 576;
        }
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
