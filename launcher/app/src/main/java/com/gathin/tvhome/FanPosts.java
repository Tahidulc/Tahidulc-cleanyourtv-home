package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fans' posts about a game for the ad-break screen: Bluesky's newest posts naming either club (its public search, no
 * login), and Reddit's newest comments in the clubs' match or pre-match threads (their feeds; else the clubs' newest
 * posts). Each through FanFilter (English, no adult labels, no swearing or slurs), newest first, at most a few dozen.
 * A site that cannot be reached gives none: never a made-up post. What fans wrote is never written to the log.
 */
final class FanPosts {
    private FanPosts() { }

    static final String BLUESKY = "Bluesky", REDDIT = "Reddit";
    /** A plain name for the TV's requests: nothing about the family. */
    static final String AGENT = "HomefieldTV/1.0 (personal TV app)";

    /** One post: where from, who (name and handle), their picture, the words, when (ms). */
    static final class Post {
        final String source, name, handle, avatar, text, key;
        final long at;

        Post(String source, String name, String handle, String avatar, String text, long at, String key) {
            this.source = source;
            this.name = name;
            this.handle = handle;
            this.avatar = avatar;
            this.text = text;
            this.at = at;
            this.key = key;
        }
    }

    /** Each club's own subreddit (the clubs the app follows and the big ones); r/soccer for any other. */
    static final Map<String, String> SUBS = new java.util.HashMap<>();
    /** The tag its fans use on Bluesky. */
    static final Map<String, String> TAGS = new java.util.HashMap<>();

    static {
        String[][] subs = {
                {"manchester united", "reddevils", "MUFC"}, {"man united", "reddevils", "MUFC"}, {"everton", "Everton", "EFC"},
                {"liverpool", "LiverpoolFC", "LFC"}, {"arsenal", "Gunners", "AFC"}, {"chelsea", "chelseafc", "CFC"},
                {"manchester city", "MCFC", "MCFC"}, {"man city", "MCFC", "MCFC"}, {"tottenham hotspur", "coys", "COYS"},
                {"tottenham", "coys", "COYS"}, {"spurs", "coys", "COYS"}, {"newcastle united", "NUFC", "NUFC"},
                {"newcastle", "NUFC", "NUFC"}, {"aston villa", "avfc", "AVFC"}, {"west ham united", "Hammers", "WHUFC"},
                {"west ham", "Hammers", "WHUFC"}, {"brighton & hove albion", "BrightonHoveAlbion", "BHAFC"},
                {"brighton", "BrightonHoveAlbion", "BHAFC"}, {"crystal palace", "crystalpalace", "CPFC"},
                {"fulham", "FulhamFC", "FFC"}, {"brentford", "Brentford", "BrentfordFC"}, {"nottingham forest", "nffc", "NFFC"},
                {"leeds united", "LeedsUnited", "LUFC"}, {"leeds", "LeedsUnited", "LUFC"}, {"afc bournemouth", "AFCBournemouth", "AFCB"},
                {"bournemouth", "AFCBournemouth", "AFCB"}, {"wolverhampton wanderers", "WWFC", "Wolves"}, {"wolves", "WWFC", "Wolves"},
                {"barcelona", "Barca", "FCBarcelona"}, {"real madrid", "realmadrid", "RealMadrid"}, {"bayern munich", "fcbayern", "FCBayern"},
                {"paris saint-germain", "psg", "PSG"}, {"ac milan", "ACMilan", "ACMilan"}, {"internazionale", "FCInterMilan", "Inter"},
                {"juventus", "Juve", "Juventus"}, {"england", "England", "ThreeLions"}
        };
        for (String[] s : subs) {
            SUBS.put(s[0], s[1]);
            TAGS.put(s[0], s[2]);
        }
    }

    /** A club's subreddit, or r/soccer. */
    static String sub(String club) {
        String s = club == null ? null : SUBS.get(club.toLowerCase(Locale.ROOT).trim());
        return s != null ? s : "soccer";
    }

    // ---- Bluesky ---------------------------------------------------------------------------------------------------------

    /** Bluesky's search for one query (its public read-only service, newest first, English). */
    static String blueskyUrl(String query) {
        try {
            return "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts?q=" + URLEncoder.encode(query + " lang:en", "UTF-8")
                    + "&limit=25&sort=latest";
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }

    /** Pure: the posts in a Bluesky search's answer that pass FanFilter (none with a label FanFilter turns away). */
    static List<Post> bluesky(String json) {
        List<Post> out = new ArrayList<>();
        try {
            JSONArray posts = new JSONObject(json).optJSONArray("posts");
            for (int i = 0; posts != null && i < posts.length(); i++) {
                JSONObject p = posts.optJSONObject(i);
                if (p == null || labelled(p.optJSONArray("labels"))) continue;
                JSONObject author = p.optJSONObject("author"), record = p.optJSONObject("record");
                if (author == null || record == null || labelled(author.optJSONArray("labels"))) continue;
                JSONArray langs = record.optJSONArray("langs");
                if (langs != null && langs.length() > 0) {
                    boolean en = false;
                    for (int k = 0; k < langs.length(); k++) if (langs.optString(k).toLowerCase(Locale.ROOT).startsWith("en")) en = true;
                    if (!en) continue;
                }
                // A reply is half a conversation: only posts of their own.
                if (record.has("reply")) continue;
                String text = FanFilter.clean(record.optString("text"));
                if (!FanFilter.ok(text)) continue;
                String handle = author.optString("handle");
                String name = FanFilter.clean(author.optString("displayName"));
                if (name.isEmpty() || FanFilter.blocked(name) || FanFilter.blocked(handle)) name = handle;
                if (FanFilter.blocked(name) || FanFilter.blocked(handle)) continue;
                long at = AdBreakLogic.time(trimMillis(record.optString("createdAt")));
                out.add(new Post(BLUESKY, name, "@" + handle, author.optString("avatar", null), FanFilter.clip(text, FanFilter.MAX), at,
                        p.optString("uri", handle + at)));
            }
        } catch (Exception e) {
            // none, then
        }
        return out;
    }

    private static boolean labelled(JSONArray labels) {
        for (int i = 0; labels != null && i < labels.length(); i++) {
            JSONObject l = labels.optJSONObject(i);
            if (l != null && FanFilter.badLabel(l.optString("val"))) return true;
        }
        return false;
    }

    /** "2026-11-12T14:03:22.123Z" without its parts of a second, for AdBreakLogic.time. */
    static String trimMillis(String iso) {
        if (iso == null) return null;
        int dot = iso.indexOf('.');
        return dot > 0 ? iso.substring(0, dot) + "Z" : iso;
    }

    // ---- Reddit ----------------------------------------------------------------------------------------------------------

    private static final Pattern ENTRY = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
    private static final Pattern NAME = Pattern.compile("<author>\\s*<name>([^<]*)</name>", Pattern.DOTALL);
    private static final Pattern UPDATED = Pattern.compile("<updated>([^<]*)</updated>");
    private static final Pattern TITLE = Pattern.compile("<title>([^<]*)</title>");
    private static final Pattern CONTENT = Pattern.compile("<content[^>]*>(.*?)</content>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("<id>([^<]*)</id>");
    private static final Pattern LINK = Pattern.compile("<link href=\"([^\"]*)\"");

    /** Pure: a subreddit's newest posts in its feed: {title, link, id}, newest first. */
    static List<String[]> redditPosts(String atom) {
        List<String[]> out = new ArrayList<>();
        Matcher e = ENTRY.matcher(atom == null ? "" : atom);
        while (e.find()) {
            String body = e.group(1);
            String title = FanFilter.entities(group(TITLE, body));
            out.add(new String[]{title, group(LINK, body), group(ID, body)});
        }
        return out;
    }

    /** Pure: the newest of a club's posts that is its match thread or pre-match thread: its link; null when none. */
    static String threadLink(List<String[]> posts) {
        for (String[] p : posts) {
            String t = p[0].toLowerCase(Locale.ROOT);
            if (t.contains("match thread") || t.contains("pre-match") || t.contains("pre match") || t.contains("prematch")) {
                if (t.contains("post match") || t.contains("post-match")) continue;
                return p[1];
            }
        }
        return null;
    }

    /**
     * Pure: the comments (or posts) in a Reddit feed that pass FanFilter, as posts: the commenter's name, their words
     * (a post's title when it has no words of its own), when. Spoilers and anything marked NSFW left out.
     */
    static List<Post> reddit(String atom, boolean titles) {
        List<Post> out = new ArrayList<>();
        Matcher e = ENTRY.matcher(atom == null ? "" : atom);
        while (e.find()) {
            String body = e.group(1);
            String who = group(NAME, body).replace("/u/", "").trim();
            if (who.isEmpty() || who.equalsIgnoreCase("AutoModerator") || who.equalsIgnoreCase("[deleted]")) continue;
            String raw = FanFilter.entities(group(CONTENT, body));
            String low = raw.toLowerCase(Locale.ROOT);
            if (low.contains("nsfw") || low.contains("spoiler") || low.contains("over_18")) continue;
            String text = FanFilter.clean(raw.replaceAll("(?s)<table.*?</table>", " ").replaceAll("submitted by .*$", " "));
            text = text.replaceAll("\\[link\\]|\\[comments\\]|submitted by\\s+/u/\\S+", " ").replaceAll("\\s+", " ").trim();
            if (titles || text.length() < 12) text = FanFilter.clean(FanFilter.entities(group(TITLE, body)));
            if (!FanFilter.ok(text) || FanFilter.blocked(who)) continue;
            long at = AdBreakLogic.time(group(UPDATED, body).replaceAll("\\+00:00$", "Z"));
            out.add(new Post(REDDIT, who, "u/" + who, null, FanFilter.clip(text, FanFilter.MAX), at, group(ID, body)));
        }
        return out;
    }

    private static String group(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    // ---- fetching (off the main thread) -----------------------------------------------------------------------------

    /** The newest posts about the game from both sites, newest first, each once; empty when neither can be read. */
    static List<Post> fetch(String homeName, String awayName) {
        List<Post> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String club : new String[]{homeName, awayName}) {
            if (club == null || club.isEmpty()) continue;
            String tag = TAGS.get(club.toLowerCase(Locale.ROOT).trim());
            for (String q : tag == null ? new String[]{"\"" + club + "\""} : new String[]{"\"" + club + "\"", "#" + tag}) {
                String body = get(blueskyUrl(q));
                if (body != null) for (Post p : bluesky(body)) if (seen.add(p.key)) all.add(p);
            }
            String sub = sub(club);
            String list = get("https://www.reddit.com/r/" + sub + "/new/.rss");
            if (list != null) {
                String thread = threadLink(redditPosts(list));
                String comments = thread == null ? null : get(thread.replaceAll("/?$", "/") + ".rss");
                List<Post> got = comments != null ? reddit(comments, false) : new ArrayList<>();
                // No thread yet: the club's newest posts by their titles.
                if (got.isEmpty() && !"soccer".equals(sub)) got = reddit(list, true);
                for (Post p : got) if (seen.add(p.key)) all.add(p);
            }
        }
        Collections.sort(all, (a, b) -> Long.compare(b.at, a.at));
        return all.size() > 30 ? new ArrayList<>(all.subList(0, 30)) : all;
    }

    /** A page's text, or null (any failure: none from that site this time). */
    static String get(String url) {
        if (url == null) return null;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8_000);
            con.setReadTimeout(10_000);
            con.setRequestProperty("User-Agent", AGENT);
            con.setRequestProperty("Accept", "application/json, application/atom+xml, */*");
            if (con.getResponseCode() != 200) return null;
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n, total = 0;
                while ((n = in.read(buf)) > 0 && total < 3_000_000) {
                    b.write(buf, 0, n);
                    total += n;
                }
                return new String(b.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** "now", "2m", "1h", "3d": how long ago, as the cards say it. */
    static String ago(long at, long now) {
        long s = Math.max(0, (now - at) / 1000);
        if (s < 60) return "now";
        if (s < 3600) return (s / 60) + "m";
        if (s < 86400) return (s / 3600) + "h";
        return (s / 86400) + "d";
    }
}
