package com.gathin.tvhomephone.watch;

import android.content.Context;

import com.gathin.tvhomephone.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The titles' logos for their posters, read after the posters show: TMDB's images for each title (English first, then
 * one without words), kept on the phone for two weeks and here while the app runs. "" means it has none.
 */
public final class Logos {
    private Logos() {}

    static final long KEEP_MS = 14L * 24 * 3600_000L;
    private static final Map<String, String> KNOWN = Collections.synchronizedMap(new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 600;
        }
    });
    private static final Set<String> ASKING = Collections.synchronizedSet(new HashSet<>());

    private static String key(boolean tv, int id) {
        return (tv ? "tv:" : "mv:") + id;
    }

    /** The logo when it is known ("" for none); null while it isn't. */
    public static String known(boolean tv, int id) {
        return id <= 0 ? "" : KNOWN.get(key(tv, id));
    }

    /** A logo learned elsewhere (a title's details read for Home), so the posters needn't ask. */
    public static void put(boolean tv, int id, String logo) {
        if (id > 0) KNOWN.put(key(tv, id), logo == null ? "" : logo);
    }

    /** Off the main thread: the title's logo ("" for none); null when TMDB couldn't be asked (asked again later). */
    public static String read(Context c, boolean tv, int id) {
        if (id <= 0) return "";
        String had = known(tv, id);
        if (had != null) return had;
        if (!Tmdb.ready(c)) return "";
        try {
            JSONObject j = Tmdb.getKept(c, (tv ? "tv/" : "movie/") + id + "/images?include_image_language=en,null", KEEP_MS);
            String logo = pick(j.optJSONArray("logos"));
            put(tv, id, logo);
            return logo == null ? "" : logo;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The logo read in the background (one read a title at a time); done is given it on the main thread ("" for none). */
    public static void ask(Context c, boolean tv, int id, Consumer<String> done) {
        String had = known(tv, id);
        if (had != null) {
            done.accept(had);
            return;
        }
        String k = key(tv, id);
        if (!ASKING.add(k)) return;
        Context app = c.getApplicationContext();
        App.io.execute(() -> {
            String logo;
            try {
                logo = read(app, tv, id);
            } finally {
                ASKING.remove(k);
            }
            if (logo != null) App.main.post(() -> done.accept(logo));
        });
    }

    /** Pure: the logo to show from TMDB's list (best first): English, else one without words; null for none. */
    static String pick(JSONArray logos) {
        String none = null;
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject l = logos.optJSONObject(i);
            if (l == null || l.optString("file_path").isEmpty()) continue;
            String lang = l.optString("iso_639_1", "");
            if (lang.equals("en")) return Tmdb.image(l.optString("file_path"), "w500");
            if ((lang.isEmpty() || lang.equals("null")) && none == null) none = Tmdb.image(l.optString("file_path"), "w500");
        }
        return none;
    }
}
