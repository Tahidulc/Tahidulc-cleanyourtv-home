package com.gathin.homefieldtv.engine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A title's videos as TMDB lists them (its trailers, teasers, clips, featurettes and the like), YouTube's only: for a
 * series' Videos row on its page, and the trailer that plays in a poster the remote rests on. Only what they need is
 * kept: the video's YouTube key, its name, its kind, its language, whether it is the official one and when it was
 * put up. Its picture is YouTube's own still of it (thumb). Pure: the reading is Tmdb's.
 */
public final class Videos {
    private Videos() {}

    /** At most this many of a title's videos are kept (its row's length). */
    public static final int MOST = 12;

    /** One of a title's videos on YouTube. */
    public static final class Video {
        public final String key, name, type, lang, published;
        public final boolean official;

        public Video(String key, String name, String type, String lang, boolean official, String published) {
            this.key = key == null ? "" : key;
            this.name = name == null ? "" : name;
            this.type = type == null ? "" : type;
            this.lang = lang == null ? "" : lang;
            this.official = official;
            this.published = published == null ? "" : published;
        }

        /** Its kind as the row's badge says it: "Trailer", "Teaser", "Clip", "Featurette", "Behind the scenes". */
        public String badge() {
            return type.equals("Behind the Scenes") ? "Behind the scenes" : type.isEmpty() ? "Video" : type;
        }
    }

    /** Pure: YouTube's own still of a video, 320 x 180 (16:9, no bars), "" for no key. */
    public static String thumb(String key) {
        return key == null || !key.matches("[A-Za-z0-9_-]{11}") ? "" : "https://i.ytimg.com/vi/" + key + "/mqdefault.jpg";
    }

    /**
     * Pure: a title's videos from TMDB's answer (its "videos" object as a page appends it, or a /videos answer itself):
     * YouTube's with a proper key only, in TMDB's order, none twice, at most MOST.
     */
    public static List<Video> parse(JSONObject videos) {
        List<Video> out = new ArrayList<>();
        JSONArray rs = videos == null ? null : videos.optJSONArray("results");
        for (int i = 0; rs != null && i < rs.length() && out.size() < MOST * 2; i++) {
            JSONObject o = rs.optJSONObject(i);
            if (o == null || !"YouTube".equalsIgnoreCase(o.optString("site", ""))) continue;
            String key = o.optString("key", "").trim();
            if (!key.matches("[A-Za-z0-9_-]{11}") || has(out, key)) continue;
            String lang = o.isNull("iso_639_1") ? "" : o.optString("iso_639_1", "");
            out.add(new Video(key, o.optString("name", "").trim(), o.optString("type", "").trim(), lang.toLowerCase(Locale.ROOT),
                    o.optBoolean("official", false), o.optString("published_at", "")));
        }
        return out;
    }

    private static boolean has(List<Video> list, String key) {
        for (Video v : list) if (v.key.equals(key)) return true;
        return false;
    }

    /**
     * Pure: a series' Videos row: its trailers first, then its teasers, then the rest (clips, featurettes, behind the
     * scenes), the family's language before others and the newest first within each, at most MOST.
     */
    public static List<Video> row(List<Video> all, String lang) {
        List<Video> out = new ArrayList<>(all == null ? Collections.emptyList() : all);
        String l = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        out.sort((a, b) -> {
            int k = Integer.compare(kind(a.type), kind(b.type));
            if (k != 0) return k;
            k = Integer.compare(language(a.lang, l), language(b.lang, l));
            if (k != 0) return k;
            return b.published.compareTo(a.published);
        });
        return out.size() > MOST ? new ArrayList<>(out.subList(0, MOST)) : out;
    }

    /** At most this many trailers are tried in a poster. */
    public static final int TRIES = 3;

    /**
     * Pure: the trailers to try in a poster the remote rests on, best first (their YouTube keys): TMDB's trailers and
     * teasers only, the family's language first (then English, then none given, then the rest), within each the trailer
     * before the teaser and the official one before the others, the newest first; then the playlist's own trailer (its
     * youtube_trailer, as a key or an address) where TMDB has none such; at most TRIES.
     */
    public static List<String> trailers(List<Video> all, String lang, String playlist) {
        String l = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        List<Video> ts = new ArrayList<>();
        if (all != null) for (Video v : all) if (kind(v.type) < 2) ts.add(v);
        ts.sort((a, b) -> {
            int k = Integer.compare(language(a.lang, l), language(b.lang, l));
            if (k != 0) return k;
            k = Integer.compare(kind(a.type), kind(b.type));
            if (k != 0) return k;
            k = Boolean.compare(b.official, a.official);
            if (k != 0) return k;
            return b.published.compareTo(a.published);
        });
        List<String> out = new ArrayList<>();
        for (Video v : ts) if (out.size() < TRIES && !out.contains(v.key)) out.add(v.key);
        String own = key(playlist);
        if (!own.isEmpty() && !out.contains(own) && out.size() < TRIES) out.add(own);
        return out;
    }

    /** Pure: a YouTube key from what a playlist gives ("dQw4w9WgXcQ", a watch, youtu.be or embed address), else "". */
    public static String key(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.matches("[A-Za-z0-9_-]{11}")) return t;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})").matcher(t);
        return m.find() ? m.group(1) : "";
    }

    /**
     * A title's videos by TMDB's id (a Coming soon card, which no playlist has yet): asked with the launcher's key and
     * kept a few days (Tmdb); empty when TMDB can't be asked. Off the main thread.
     */
    public static List<Video> of(android.content.Context c, boolean tv, int id) {
        if (id <= 0) return new ArrayList<>();
        return parse(Tmdb.videos(c, tv, id));
    }

    /** A video's kind's place: trailers, teasers, then the rest. */
    static int kind(String type) {
        if ("Trailer".equalsIgnoreCase(type)) return 0;
        if ("Teaser".equalsIgnoreCase(type)) return 1;
        return 2;
    }

    /** A language's place: the family's, then English, then a video with none given, then the rest. */
    static int language(String lang, String family) {
        if (!family.isEmpty() && family.equals(lang)) return 0;
        if ("en".equals(lang)) return 1;
        if (lang.isEmpty()) return 2;
        return 3;
    }

    // ---- kept on this TV with a title's extras ---------------------------------------------------------------------

    /** Pure: videos as they are kept (each its key, name, kind, language, official and when). */
    static JSONArray kept(List<Video> list) throws JSONException {
        JSONArray a = new JSONArray();
        for (Video v : list) a.put(new JSONArray().put(v.key).put(v.name).put(v.type).put(v.lang).put(v.official).put(v.published));
        return a;
    }

    /** Pure: videos as kept (kept()); empty for none. */
    static List<Video> fromKept(JSONArray a) {
        List<Video> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONArray v = a.optJSONArray(i);
            if (v == null || v.optString(0, "").isEmpty()) continue;
            out.add(new Video(v.optString(0, ""), v.optString(1, ""), v.optString(2, ""), v.optString(3, ""), v.optBoolean(4, false),
                    v.optString(5, "")));
        }
        return out;
    }
}
