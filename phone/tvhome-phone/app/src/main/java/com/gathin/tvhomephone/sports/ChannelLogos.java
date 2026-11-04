package com.gathin.tvhomephone.sports;

import android.content.Context;

import com.gathin.tvhomephone.live.LiveLinks;
import com.gathin.tvhomephone.watch.Tmdb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A channel's or a streaming service's logo, for "Live on …": best first, Sky's guide logo for a British channel (the
 * TV's own, by Sky's service id), a service's logo from TMDB's UK providers, the family's playlist's own logo for that
 * channel; null when none is known, and the name is written out instead.
 */
public final class ChannelLogos {
    private ChannelLogos() {}

    private static final String SKY = "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb";

    /**
     * {Sky guide service id, a name the channel goes by}: the TV's (its config.xml; 1301 is the Main Event's logo
     * without the HD mark, as the TV's cards use it), then more from Sky's public services list, matched by name.
     */
    static final String[][] SKY_IDS = {
            {"1301", "Sky Sports Main Event"}, {"4010", "Sky Sports Premier League"}, {"3939", "Sky Sports Football"},
            {"3835", "Sky Sports F1"}, {"3940", "Sky Sports+"}, {"3940", "Sky Sports Plus"}, {"4090", "Sky Sports Mix"},
            {"4049", "Sky Sports News"}, {"1284", "Sky Sports Tennis"}, {"4081", "Sky Sports Cricket"},
            {"4026", "Sky Sports Golf"}, {"4022", "Sky Sports NFL"}, {"4032", "Sky Sports Racing"},
            {"3625", "TNT Sports 1"}, {"3627", "TNT Sports 2"}, {"3629", "TNT Sports 3"}, {"4040", "TNT Sports 4"},
            {"1289", "Premier Sports 1"}, {"1290", "Premier Sports 2"}, {"3007", "BBC One"}, {"3007", "BBC 1"},
            {"2075", "BBC Two"}, {"2075", "BBC 2"}, {"6504", "ITV1"}, {"6504", "ITV"}, {"6532", "ITV2"}, {"6534", "ITV4"},
            {"4075", "Channel 4"}, {"4058", "5"}, {"4058", "Channel 5"}, {"2296", "S4C"}, {"1003", "MUTV"},
            {"3845", "LFCTV"}};

    /** Streaming services: {a name a listing may give it, TMDB's name for the provider}. */
    static final String[][] SERVICES = {
            {"Amazon Prime Video", "Amazon Prime Video"}, {"Prime Video", "Amazon Prime Video"}, {"Amazon Prime", "Amazon Prime Video"},
            {"Netflix", "Netflix"}, {"Discovery+", "Discovery+"}, {"Discovery Plus", "Discovery+"}, {"TNT Sports App", "Discovery+"},
            {"HBO Max", "HBO Max"}, {"DAZN", "DAZN"}, {"BBC iPlayer", "BBC iPlayer"}, {"iPlayer", "BBC iPlayer"},
            {"ITVX", "ITVX"}};

    private static final Map<String, String> BY_NAME = new HashMap<>();
    private static final Map<String, String> SERVICE = new HashMap<>();
    static {
        for (String[] s : SKY_IDS) BY_NAME.put(key(s[1]), s[0]);
        for (String[] s : SERVICES) SERVICE.put(key(s[0]), s[1]);
    }

    /**
     * "Sky Sports Main Event HD", "UK: SKY SPORTS MAIN EVENT UHD", "|UK| Sky Sports Main Event 4K ᴴᴰ" and "Sky Sports Main
     * Event" alike: a playlist's country tag at either end and its picture's words (HD, FHD, UHD, 4K, HDR, HEVC…) dropped
     * wherever they are, so a 4K channel finds its channel's logo. Pure.
     */
    static String key(String name) {
        String n = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace("&", " and ").replace("ᴴᴰ", " hd ").trim();
        n = n.replaceAll("^[\\[(|]?\\s*(uk|gb|eng|ie)\\s*[\\])|:\\-]+\\s*", "");
        n = n.replaceAll("\\s*[\\[(|:\\-]\\s*(uk|gb|eng|ie)\\s*[\\])|]?$", "");
        n = n.replaceAll("(^|[^a-z0-9])(uhd|fhd|hd|sd|4k|8k|uhd4k|hdr|hdr10|hevc|h265|1080p|1080i|2160p|50fps|60fps)(?=$|[^a-z0-9])", "$1");
        return n.replaceAll("[^a-z0-9+]", "");
    }

    /** Sky's guide logo for a British channel by its name, or null. Pure. */
    public static String sky(String name) {
        String sid = BY_NAME.get(key(name));
        return sid == null ? null : SKY + sid + ".png";
    }

    /** TMDB's name for a streaming service named so, or null when it is not one. Pure. */
    static String service(String name) {
        return SERVICE.get(key(name));
    }

    public static boolean isService(String name) {
        return service(name) != null;
    }

    // ---- TMDB's providers -------------------------------------------------------------------------------------------

    /** TMDB's provider (by key) to its logo, from the UK's movie and TV provider lists, read once in a while. */
    private static final Map<String, String> PROVIDERS = new ConcurrentHashMap<>();
    private static long providersAt;

    /** Each provider in a TMDB watch/providers reply: key(provider_name) → its logo (w154). Pure. */
    static void learn(JSONObject reply, Map<String, String> into) {
        JSONArray r = reply == null ? null : reply.optJSONArray("results");
        for (int i = 0; r != null && i < r.length(); i++) {
            JSONObject p = r.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("provider_name", ""), path = p.optString("logo_path", "");
            if (name.isEmpty() || path.isEmpty() || "null".equals(path)) continue;
            String url = Tmdb.image(path, "w154");
            if (url != null && !into.containsKey(key(name))) into.put(key(name), url);
        }
    }

    /** A service's logo among TMDB's providers: its own name, else the shortest that starts with it ("… with Ads"). Pure. */
    static String match(Map<String, String> providers, String tmdbName) {
        String k = key(tmdbName);
        if (k.isEmpty()) return null;
        String hit = providers.get(k);
        if (hit != null) return hit;
        String best = null;
        for (String p : providers.keySet()) if (p.startsWith(k) && (best == null || p.length() < best.length())) best = p;
        return best == null ? null : providers.get(best);
    }

    private static synchronized void readProviders(Context c) {
        long now = System.currentTimeMillis();
        if (!PROVIDERS.isEmpty() || now - providersAt < 30 * 60_000L || !Tmdb.ready(c)) return;
        providersAt = now;
        for (String kind : new String[]{"movie", "tv"}) {
            try {
                learn(Tmdb.get(c, "watch/providers/" + kind + "?watch_region=GB"), PROVIDERS);
            } catch (Exception ignored) {
                // TMDB did not answer (or no TV has given the key yet): the name is written out
            }
        }
    }

    // ---- the logo for a name ----------------------------------------------------------------------------------------

    /** The logo known now, without reading anything: Sky's, a service's once TMDB's list is in, the playlist's. */
    public static String known(Context c, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String s = sky(name);
        if (s != null) return s;
        String svc = service(name);
        if (svc != null) return match(PROVIDERS, svc);
        return LiveLinks.logo(c, name);
    }

    /** known(), with TMDB's providers read first when a service's logo is not in yet. Off the main thread. */
    public static String find(Context c, String name) {
        String k = known(c, name);
        if (k != null || name == null) return k;
        String svc = service(name);
        if (svc == null) return null;
        readProviders(c.getApplicationContext());
        return match(PROVIDERS, svc);
    }
}
