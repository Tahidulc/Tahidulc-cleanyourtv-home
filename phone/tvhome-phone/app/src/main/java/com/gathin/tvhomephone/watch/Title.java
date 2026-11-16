package com.gathin.tvhomephone.watch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One film or series, as TMDB's lists give it, with what its poster says under it on this tab. */
public final class Title {
    public int id;
    public boolean tv;
    public String title = "", poster, backdrop, year = "", overview = "";
    public double vote;
    public int[] genres = new int[0];
    /** Along the poster's foot: "Ends 22:41", "NEW · S4 E1", "Crime · Mystery"; null for none. */
    public String foot;
    /** On the poster's top corner: "THU 5 · TOMORROW", "3 LEFT", "Netflix"; null for none. */
    public String badge;
    public int badgeColor = 0xCC080A1E;
    /** How far through (0 to 1), a bar along the foot; negative for none. */
    public float progress = -1;
    /** Top 10's place; 0 for none. */
    public int rank;
    /** Coming up: when (ms) it's out; 0 otherwise. */
    public long when;
    /** When it came out ("2026-10-31"; "" when not known). */
    public String date = "";
    /** Its logo once read (w500); null while not known. */
    public String logo;
    /** What the family's own sources have of it (SourceQuality's bits); 0 when they say nothing. */
    public int quality;

    public String key() {
        return (tv ? "tv:" : "mv:") + id;
    }

    /** From a TMDB list's result (movie, tv or multi); null for a person or anything without an id. */
    public static Title from(JSONObject r, Boolean tvHint) {
        if (r == null || r.optInt("id") <= 0) return null;
        String media = r.optString("media_type", "");
        if (media.equals("person")) return null;
        Title t = new Title();
        t.id = r.optInt("id");
        t.tv = media.isEmpty() ? (tvHint != null ? tvHint : r.has("first_air_date")) : media.equals("tv");
        t.title = r.optString(t.tv ? "name" : "title", r.optString("title", r.optString("name", "")));
        t.poster = Tmdb.image(r.optString("poster_path", null), "w342");
        t.backdrop = Tmdb.image(r.optString("backdrop_path", null), "w780");
        String date = r.optString(t.tv ? "first_air_date" : "release_date", "");
        t.year = date.length() >= 4 ? date.substring(0, 4) : "";
        t.date = date;
        t.vote = r.optDouble("vote_average", 0);
        t.overview = r.optString("overview", "");
        JSONArray g = r.optJSONArray("genre_ids");
        if (g == null && r.optJSONArray("genres") != null) {
            JSONArray gs = r.optJSONArray("genres");
            t.genres = new int[gs.length()];
            for (int i = 0; i < gs.length(); i++) t.genres[i] = gs.optJSONObject(i) == null ? 0 : gs.optJSONObject(i).optInt("id");
        } else if (g != null) {
            t.genres = new int[g.length()];
            for (int i = 0; i < g.length(); i++) t.genres[i] = g.optInt(i);
        }
        return t;
    }

    /** From an item the TVs or this phone keep (My list's data, /sync/watching's titles): lenient about fields. */
    public static Title fromItem(JSONObject d) {
        if (d == null) return null;
        Title t = new Title();
        t.id = d.optInt("id");
        t.tv = d.optBoolean("tv", d.has("season"));
        t.title = d.optString("title", d.optString("name", ""));
        t.poster = Tmdb.image(d.optString("poster", null), "w342");
        t.backdrop = Tmdb.image(d.optString("backdrop", null), "w780");
        return t.id <= 0 && t.title.isEmpty() ? null : t;
    }

    /** What My list keeps for it (SYNC.md's mylist data). */
    public JSONObject listData() {
        JSONObject d = new JSONObject();
        try {
            d.put("id", id).put("tv", tv).put("title", title).put("poster", poster == null ? "" : poster)
                    .put("backdrop", backdrop == null ? "" : backdrop).put("added", System.currentTimeMillis());
        } catch (JSONException ignored) {
            // plain values
        }
        return d;
    }
}
