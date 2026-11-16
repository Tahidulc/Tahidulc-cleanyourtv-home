package com.gathin.tvhomephone.sports;

import android.content.Context;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A fixture's or an event's highlights, whatever is found for it: ESPN's videos of the game (played in the app, by
 * ClipPlayerActivity), FotMob's highlights link for a football match (often YouTube's: its title and still by YouTube's
 * oEmbed only, opened in the YouTube app or the browser), and for the other sports ESPN's clips that name the event
 * (its fighters, its teams, its Grand Prix) around its day. Read off the main thread, kept a few minutes; nothing about
 * the family is sent: ESPN's, FotMob's and YouTube's addresses with a plain app name for agent.
 */
public final class Highlights {
    private Highlights() {}

    /** One clip: its title, its still, what plays it here (an mp4 or HLS address) or the page that has it. */
    public static final class Clip {
        public String title = "", still = "", play = "", link = "", source = "";
        public int seconds;

        /** Plays in the app (ESPN's own files) rather than opening elsewhere. */
        public boolean here() {
            return !play.isEmpty();
        }
    }

    private static final Map<String, Object[]> KEPT = new HashMap<>();
    private static final long KEEP_MS = 10 * 60_000L;

    private static List<Clip> kept(String key) {
        synchronized (KEPT) {
            Object[] k = KEPT.get(key);
            if (k == null || System.currentTimeMillis() - (Long) k[0] > KEEP_MS) return null;
            @SuppressWarnings("unchecked") List<Clip> l = (List<Clip>) k[1];
            return l;
        }
    }

    private static List<Clip> keep(String key, List<Clip> clips) {
        synchronized (KEPT) {
            if (KEPT.size() > 40) KEPT.clear();
            KEPT.put(key, new Object[]{System.currentTimeMillis(), clips});
        }
        return clips;
    }

    /** A football game's: FotMob's link to its highlights first (once it is under way), then ESPN's videos of it. */
    public static List<Clip> forGame(Context c, Game g) {
        if (g == null || g.id == null) return new ArrayList<>();
        String key = "g|" + g.id + "|" + g.state;
        List<Clip> had = kept(key);
        if (had != null) return had;
        List<Clip> out = new ArrayList<>();
        if (g.state != Game.PRE) {
            try {
                String[] h = com.gathin.tvhomephone.live.FotMob.highlights(g.id, g.homeName, g.awayName, g.kickoff);
                Clip f = fotmob(h);
                if (f != null) {
                    youtube(f);
                    if (f.title.isEmpty()) f.title = g.homeName + " v " + g.awayName + ": highlights";
                    out.add(f);
                }
            } catch (RuntimeException ignored) {
                // without FotMob's
            }
        }
        JSONObject root = Espn.summaryRoot(g.slug, g.id, 5 * 60_000L);
        out.addAll(summaryClips(root, 6));
        return keep(key, out);
    }

    /**
     * Another sport's event: ESPN's videos of the game itself (the NFL's, the NBA's), then ESPN's clips of the sport
     * that name the event, from a day and a half before it to three days after.
     */
    public static List<Clip> forEvent(Context c, SportEvent e) {
        if (e == null || e.espn.isEmpty()) return new ArrayList<>();
        String key = "e|" + e.espn + "|" + e.espnId + "|" + e.title + "|" + e.state;
        List<Clip> had = kept(key);
        if (had != null) return had;
        List<Clip> out = new ArrayList<>();
        if (!e.espnId.isEmpty() && (e.espn.startsWith("football/") || e.espn.startsWith("basketball/"))) {
            out.addAll(summaryClips(read(SITE + e.espn + "/summary?event=" + e.espnId), 6));
        }
        if (out.size() < 2 && !e.finds.isEmpty()) {
            long from = e.start > 0 ? e.start - 36 * 3_600_000L : 0, to = e.endOr() + 72 * 3_600_000L;
            for (String[] m : newsMedia(read(SITE + e.espn + "/news?limit=50"), e.finds, from, to, 4)) {
                Clip clip = clipFrom(read(m[0]));
                if (clip == null) continue;
                if (clip.still.isEmpty()) clip.still = m[2];
                boolean dup = false;
                for (Clip x : out) dup |= x.play.equals(clip.play);
                if (!dup) out.add(clip);
            }
        }
        return keep(key, out);
    }

    static final String SITE = "https://site.api.espn.com/apis/site/v2/sports/";

    private static JSONObject read(String url) {
        try {
            return new JSONObject(Net.get(url, null, 8_000));
        } catch (Exception e) {
            return null;
        }
    }

    // ---- pure -------------------------------------------------------------------------------------------------------

    /** ESPN's videos in a summary that play here, at most max. */
    static List<Clip> summaryClips(JSONObject root, int max) {
        List<Clip> out = new ArrayList<>();
        JSONArray vs = root == null ? null : root.optJSONArray("videos");
        for (int i = 0; vs != null && i < vs.length() && out.size() < max; i++) {
            Clip c = espnVideo(vs.optJSONObject(i));
            if (c != null) out.add(c);
        }
        return out;
    }

    /** One of ESPN's video objects (a summary's, a clip's): its mp4 (its HLS when it has none), still, headline, length. */
    static Clip espnVideo(JSONObject v) {
        if (v == null) return null;
        JSONObject links = v.optJSONObject("links");
        JSONObject source = links == null ? null : links.optJSONObject("source");
        if (source == null) return null;
        String play = "";
        for (String k : new String[]{"HD", "full", "href", "mezzanine"}) {
            String u = address(source.opt(k));
            if (u.startsWith("https://") && u.contains(".mp4")) {
                play = u;
                break;
            }
        }
        if (play.isEmpty()) {
            String hls = address(source.opt("HLS"));
            if (hls.startsWith("https://")) play = hls;
        }
        if (play.isEmpty()) return null;
        Clip c = new Clip();
        c.play = play;
        c.title = v.optString("headline", v.optString("title", "")).trim();
        c.still = v.optString("thumbnail", "");
        c.seconds = v.optInt("duration", 0);
        c.source = "ESPN";
        return c;
    }

    private static String address(Object o) {
        if (o instanceof JSONObject) return ((JSONObject) o).optString("href", "");
        if (o instanceof String) return (String) o;
        return "";
    }

    /** A clip's own record as ESPN's clip address gives it ({"videos": [...]}), as a Clip; null without one. */
    static Clip clipFrom(JSONObject root) {
        JSONArray vs = root == null ? null : root.optJSONArray("videos");
        return vs == null || vs.length() == 0 ? null : espnVideo(vs.optJSONObject(0));
    }

    /**
     * ESPN's news for a sport: its video stories that name the event (in the headline, the description or the tags:
     * the event, its athletes, its teams) and came out between from and to (0 for any), as {the clip's address,
     * headline, picture}, newest first as ESPN lists them, at most max.
     */
    static List<String[]> newsMedia(JSONObject news, List<String[][]> finds, long from, long to, int max) {
        List<String[]> out = new ArrayList<>();
        JSONArray arts = news == null ? null : news.optJSONArray("articles");
        for (int i = 0; arts != null && i < arts.length() && out.size() < max; i++) {
            JSONObject a = arts.optJSONObject(i);
            if (a == null || !"Media".equals(a.optString("type"))) continue;
            long at = time(a.optString("published"));
            if (at > 0 && (from > 0 && at < from || to > 0 && at > to)) continue;
            StringBuilder words = new StringBuilder(a.optString("headline")).append(' ').append(a.optString("description"));
            JSONArray cats = a.optJSONArray("categories");
            for (int k = 0; cats != null && k < cats.length(); k++) {
                JSONObject cat = cats.optJSONObject(k);
                if (cat != null) words.append(" · ").append(cat.optString("description"));
            }
            if (!com.gathin.tvhomephone.live.EventChannels.mentions(words.toString(), finds)) continue;
            JSONObject links = a.optJSONObject("links");
            JSONObject api = links == null ? null : links.optJSONObject("api");
            JSONObject self = api == null ? null : api.optJSONObject("self");
            String url = self == null ? "" : self.optString("href", "");
            if (!url.startsWith("https://")) continue;
            JSONArray imgs = a.optJSONArray("images");
            String pic = imgs != null && imgs.length() > 0 && imgs.optJSONObject(0) != null ? imgs.optJSONObject(0).optString("url", "") : "";
            out.add(new String[]{url, a.optString("headline", ""), pic});
        }
        return out;
    }

    /** FotMob's highlights link as a clip that opens elsewhere: YouTube's or the league's page. Null without one. */
    static Clip fotmob(String[] h) {
        if (h == null || h.length < 1 || h[0] == null || h[0].isEmpty()) return null;
        Clip c = new Clip();
        c.link = h[0];
        c.still = h.length > 1 && h[1] != null ? h[1] : "";
        String id = youtubeId(h[0]);
        c.source = id != null ? "YouTube" : h.length > 2 && h[2] != null && !h[2].isEmpty() ? h[2].replaceFirst("^www\\.", "") : "Highlights";
        if (c.still.isEmpty() && id != null) c.still = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        return c;
    }

    private static final Pattern YOUTUBE = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    /** A YouTube video's id from its address; null when it isn't YouTube's. */
    static String youtubeId(String url) {
        Matcher m = YOUTUBE.matcher(url == null ? "" : url.trim());
        return m.find() ? m.group(1) : null;
    }

    static long time(String iso) {
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- YouTube: oEmbed only -------------------------------------------------------------------------------------

    /** A YouTube clip's title and still by YouTube's oEmbed (nothing else of YouTube's is read); left as it is if it won't say. */
    private static void youtube(Clip c) {
        if (youtubeId(c.link) == null) return;
        try {
            JSONObject o = new JSONObject(Net.get("https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(c.link, "UTF-8"),
                    null, 6_000));
            String title = o.optString("title", "").trim();
            if (!title.isEmpty()) c.title = title;
            String thumb = o.optString("thumbnail_url", "").trim();
            if (thumb.startsWith("https://") && c.still.isEmpty()) c.still = thumb;
            String by = o.optString("author_name", "").trim();
            if (!by.isEmpty()) c.source = "YouTube · " + by;
        } catch (Exception ignored) {
            // embedding off for it (or no network): FotMob's still and a plain title
        }
    }
}
