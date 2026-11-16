package com.gathin.tvhomephone.live;

import com.gathin.tvhomephone.Net;
import com.gathin.tvhomephone.sports.Game;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What fans are saying before the game, for the ad-break screen: Bluesky's newest public posts naming either club, and
 * the comments in the club's pre-match thread on Reddit (its newest posts when it has none). Read only while the
 * ad-break screen is up (at most once a minute); every post through FanFilter. Nothing is logged: not the words, not
 * who wrote them. A source that fails gives none.
 */
final class FanPosts {
    private FanPosts() {}

    static final String BLUESKY = "Bluesky", REDDIT = "Reddit";
    private static final Map<String, String> UA = new HashMap<>();

    static {
        UA.put("User-Agent", "HomefieldPhone/1.0 (personal app)");
    }

    /** One post: where from, who (name, handle), their picture (Bluesky's; "" for Reddit), the words, when, its page. */
    static final class Post {
        final String source, name, handle, avatar, text, link;
        final long at;
        /** Which community it is in ("r/reddevils"; "" for Bluesky's). */
        String where = "";

        Post(String source, String name, String handle, String avatar, String text, long at) {
            this(source, name, handle, avatar, text, at, "");
        }

        Post(String source, String name, String handle, String avatar, String text, long at, String link) {
            this.source = source;
            this.name = name;
            this.handle = handle;
            this.avatar = avatar == null ? "" : avatar;
            this.text = text;
            this.at = at;
            this.link = link == null ? "" : link;
        }
    }

    /** Each club's own subreddit, by the names ESPN gives it (its full name or its short one). */
    private static final String[][] SUBS = {
            {"manchester united", "reddevils"}, {"man united", "reddevils"}, {"everton", "Everton"}, {"liverpool", "LiverpoolFC"},
            {"arsenal", "Gunners"}, {"chelsea", "chelseafc"}, {"manchester city", "MCFC"}, {"man city", "MCFC"},
            {"tottenham hotspur", "coys"}, {"spurs", "coys"}, {"tottenham", "coys"}, {"newcastle united", "NUFC"}, {"newcastle", "NUFC"},
            {"aston villa", "avfc"}, {"brighton & hove albion", "BrightonHoveAlbion"}, {"brighton", "BrightonHoveAlbion"},
            {"west ham united", "Hammers"}, {"west ham", "Hammers"}, {"leeds united", "LeedsUnited"}, {"leeds", "LeedsUnited"},
            {"fulham", "fulhamfc"}, {"brentford", "Brentford"}, {"crystal palace", "crystalpalace"},
            {"wolverhampton wanderers", "WWFC"}, {"wolves", "WWFC"}, {"nottingham forest", "nffc"}, {"afc bournemouth", "AFCBournemouth"},
            {"bournemouth", "AFCBournemouth"}, {"sunderland", "safc"}, {"burnley", "Burnley"}, {"barcelona", "Barca"},
            {"real madrid", "realmadrid"}, {"bayern munich", "fcbayern"}, {"paris saint-germain", "psg"}, {"psg", "psg"},
            {"juventus", "Juve"}, {"internazionale", "FCInternazionale"}, {"inter milan", "FCInternazionale"}, {"ac milan", "ACMilan"},
            {"england", "England"},
    };

    /** The club's subreddit (r/soccer when it has none we know). */
    static String sub(String... names) {
        for (String n : names) {
            if (n == null) continue;
            String k = n.toLowerCase(Locale.ROOT).trim();
            for (String[] s : SUBS) if (s[0].equals(k)) return s[1];
        }
        return "soccer";
    }

    /** Off the main thread: the newest posts for a game, both sources mixed, newest first, at most max. */
    static List<Post> load(Game g, int max) {
        List<Post> all = new ArrayList<>();
        if (g == null) return all;
        for (String club : new String[]{name(g.homeName, g.home), name(g.awayName, g.away)}) {
            if (club.isEmpty()) continue;
            try {
                String q = "\"" + club + "\" lang:en";
                String url = "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts?limit=25&sort=latest&q=" + java.net.URLEncoder.encode(q, "UTF-8");
                all.addAll(bluesky(new JSONObject(Net.get(url, UA, 8000))));
            } catch (Exception ignored) {
                // none from Bluesky this time
            }
        }
        for (String[] club : new String[][]{{g.homeFull, g.homeName}, {g.awayFull, g.awayName}}) {
            try {
                all.addAll(reddit(sub(club)));
            } catch (Exception ignored) {
                // none from Reddit this time
            }
        }
        return mix(all, max);
    }

    /**
     * Off the main thread: what fans of these clubs (by their names) are saying now, for Home: Bluesky's newest posts
     * naming each and each club's own Reddit community's, through FanFilter, no bots, none older than maxAgeMs; both
     * sources mixed, newest first, at most max. A source that fails gives none.
     */
    static List<Post> clubs(List<String> names, int max, long maxAgeMs) {
        List<Post> all = new ArrayList<>();
        Set<String> subs = new HashSet<>();
        for (String club : names) {
            if (club == null || club.trim().isEmpty()) continue;
            try {
                String q = "\"" + club.trim() + "\" lang:en";
                String url = "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts?limit=25&sort=latest&q=" + java.net.URLEncoder.encode(q, "UTF-8");
                all.addAll(bluesky(new JSONObject(Net.get(url, UA, 8000))));
            } catch (Exception ignored) {
                // none from Bluesky this time
            }
            String sub = sub(club);
            if ("soccer".equals(sub) || !subs.add(sub)) continue;
            try {
                for (Post p : reddit(sub)) {
                    p.where = "r/" + sub;
                    all.add(p);
                }
            } catch (Exception ignored) {
                // none from Reddit this time
            }
        }
        long now = System.currentTimeMillis();
        List<Post> kept = new ArrayList<>();
        for (Post p : all) if (!bot(p) && (p.at <= 0 || now - p.at <= maxAgeMs)) kept.add(p);
        return mix(kept, max);
    }

    /**
     * "bot" as a word of its own ("NUFC Bot", "@nufc-bot.bsky.social", "bot2"), or after the words feeds are named with
     * ("@clubnewsbot…", "goalbot"); never inside a name (Talbot, Abbot, Cabot, robotics).
     */
    private static final Pattern BOT = Pattern.compile("(?i)(^|[^a-z])bot([^a-z]|$)"
            + "|(news|goal|goals|score|scores|feed|alert|alerts|transfer|transfers|rss|auto|post|update|updates|match|live|stats|club|fc|headline|headlines)bot");

    /** Pure: a post by a bot account (its name or handle says so: "NUFC Twitter Bot", "@clubnewsbot.bsky.social"). */
    static boolean bot(Post p) {
        return BOT.matcher(p.name == null ? "" : p.name).find() || BOT.matcher(p.handle == null ? "" : p.handle).find();
    }

    private static String name(String a, String b) {
        return a != null && !a.isEmpty() ? a : b == null ? "" : b;
    }

    /** A club's pre-match thread's comments (Reddit's feeds), else its newest posts. */
    private static List<Post> reddit(String sub) throws Exception {
        String feed = Net.get("https://www.reddit.com/r/" + sub + "/new/.rss", UA, 8000);
        List<String[]> entries = atom(feed);
        String thread = thread(entries);
        if (thread != null) {
            List<String[]> comments = atom(Net.get("https://www.reddit.com/r/" + sub + "/comments/" + thread + "/.rss", UA, 8000));
            if (!comments.isEmpty()) comments.remove(0);   // the post itself; the comments follow
            List<Post> out = redditPosts(comments, true);
            if (!out.isEmpty()) return out;
        }
        return redditPosts(entries, false);
    }

    // ---- reading the answers (pure: the tests feed them) --------------------------------------------------------------

    /** Bluesky's search answer: the posts fit to show. */
    static List<Post> bluesky(JSONObject root) {
        List<Post> out = new ArrayList<>();
        JSONArray posts = root.optJSONArray("posts");
        for (int i = 0; posts != null && i < posts.length(); i++) {
            JSONObject p = posts.optJSONObject(i);
            if (p == null) continue;
            JSONObject author = p.optJSONObject("author"), record = p.optJSONObject("record");
            if (author == null || record == null) continue;
            if (FanFilter.badLabels(labels(p.optJSONArray("labels"))) || FanFilter.badLabels(labels(author.optJSONArray("labels")))) continue;
            JSONArray langs = record.optJSONArray("langs");
            if (langs != null && langs.length() > 0) {
                boolean en = false;
                for (int k = 0; k < langs.length(); k++) if (langs.optString(k).toLowerCase(Locale.ROOT).startsWith("en")) en = true;
                if (!en) continue;
            }
            String text = FanFilter.show(record.optString("text"));
            String handle = author.optString("handle");
            String name = author.optString("displayName", "");
            if (name.trim().isEmpty()) name = handle;
            if (text == null || handle.isEmpty() || !FanFilter.nameOk(name) || !FanFilter.nameOk(handle)) continue;
            String uri = p.optString("uri", "");
            String rkey = uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : "";
            String link = rkey.isEmpty() ? "" : "https://bsky.app/profile/" + handle + "/post/" + rkey;
            out.add(new Post(BLUESKY, name.trim(), "@" + handle, author.optString("avatar", ""), text, AdBreak.when(record.optString("createdAt")), link));
        }
        return out;
    }

    private static List<String> labels(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject l = a.optJSONObject(i);
            if (l != null) out.add(l.optString("val"));
        }
        return out;
    }

    private static final Pattern ENTRY = Pattern.compile("<entry\\b[^>]*>(.*?)</entry>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("/comments/([a-z0-9]+)/");

    /** Atom entries: {title, author, updated, content (its HTML, as given), link}. */
    static List<String[]> atom(String xml) {
        List<String[]> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = ENTRY.matcher(xml);
        while (m.find()) {
            String e = m.group(1);
            String author = BreakingNews.tag(e, "name");
            Matcher link = Pattern.compile("<link\\b[^>]*href=\"([^\"]+)\"").matcher(e);
            out.add(new String[]{BreakingNews.tag(e, "title"), author == null ? "" : author.trim(), BreakingNews.tag(e, "updated"),
                    BreakingNews.tag(e, "content"), link.find() ? link.group(1) : ""});
        }
        return out;
    }

    /** The newest entry that is the pre-match (or match) thread: its id, or null. */
    static String thread(List<String[]> entries) {
        for (String[] e : entries) {
            String t = e[0].toLowerCase(Locale.ROOT);
            if (t.contains("pre-match") || t.contains("pre match") || t.contains("prematch") || t.contains("match thread")) {
                if (t.contains("post-match") || t.contains("post match")) continue;
                Matcher m = ID.matcher(e[4]);
                if (m.find()) return m.group(1);
            }
        }
        return null;
    }

    /** Reddit's entries as posts: a comment's words, or a post's title (and its words); none marked adult or a spoiler. */
    static List<Post> redditPosts(List<String[]> entries, boolean comments) {
        List<Post> out = new ArrayList<>();
        for (String[] e : entries) {
            String all = (e[0] + " " + e[3]).toLowerCase(Locale.ROOT);
            if (all.contains("nsfw") || all.contains("spoiler") || all.contains("over 18") || all.contains("over_18")) continue;
            String words = comments ? e[3] : e[0];
            // A post's own words after its title, where it has some; its title alone otherwise.
            if (!comments) {
                String body = FanFilter.clean(e[3]).replace("submitted by", "").trim();
                if (body.length() > 20 && !body.startsWith("/u/")) words = e[0] + ". " + e[3];
            }
            String text = FanFilter.show(words);
            String user = e[1].startsWith("/u/") ? e[1].substring(3) : e[1];
            if (text == null || user.isEmpty() || "AutoModerator".equalsIgnoreCase(user) || !FanFilter.nameOk(user)) continue;
            out.add(new Post(REDDIT, user, "u/" + user, "", text, AdBreak.when(e[2]), e.length > 4 ? e[4] : ""));
        }
        return out;
    }

    /** Both sources together: the same words once, newest first, at most max, neither source more than two in a row. */
    static List<Post> mix(List<Post> posts, int max) {
        List<Post> sorted = new ArrayList<>(posts);
        sorted.sort((a, b) -> Long.compare(b.at, a.at));
        Set<String> seen = new HashSet<>();
        List<Post> out = new ArrayList<>(), held = new ArrayList<>();
        for (Post p : sorted) {
            String key = p.text.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            int n = out.size();
            if (n >= 2 && out.get(n - 1).source.equals(p.source) && out.get(n - 2).source.equals(p.source)) {
                held.add(p);
                continue;
            }
            out.add(p);
            // One held back goes in as soon as it would not make three in a row.
            for (int i = 0; i < held.size(); i++) {
                Post h = held.get(i);
                int m = out.size();
                if (m < 2 || !(out.get(m - 1).source.equals(h.source) && out.get(m - 2).source.equals(h.source))) {
                    out.add(h);
                    held.remove(i);
                    break;
                }
            }
            if (out.size() >= max) break;
        }
        for (Post h : held) if (out.size() < max) out.add(h);
        while (out.size() > max) out.remove(out.size() - 1);
        return out;
    }

    /** "now", "3m", "2h", "1d": how long ago. */
    static String ago(long at, long now) {
        long s = Math.max(0, (now - at) / 1000);
        if (at <= 0) return "";
        if (s < 60) return "now";
        if (s < 3600) return (s / 60) + "m";
        if (s < 86400) return (s / 3600) + "h";
        return (s / 86400) + "d";
    }
}
