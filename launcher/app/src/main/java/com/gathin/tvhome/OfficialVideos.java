package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A finished game's highlights from official sources, as the Live Centre offers them at full time: ESPN's own for
 * LaLiga, else the YouTube channels that hold the rights where the TV is (checked to play in the UK): Sky Sports
 * Premier League for the Premier League, Sky Sports Football for the Championship (and LaLiga, Serie A and the
 * Bundesliga later in the evening), the Serie A's and the Bundesliga's own. Each channel's public list of uploads
 * (its feed) is read, at most once a minute and a half. Off the main thread.
 */
final class OfficialVideos {
    /** One video: a YouTube video's id, or a clip's address (ESPN's), its title, picture and when it went up. */
    static final class Video {
        String youtube, mp4, title, thumb;
        /** What the channel wrote under it (its feed's media:description); null when it says nothing. */
        String description;
        long published;
        /** A YouTube Short (its feed link goes to /shorts/): no use as a highlight on a TV. */
        boolean shorts;
    }

    private static final String SKY_PL = "UCNAf1k0yIjyGu3k9BwAg3lg", SKY_FOOTBALL = "UCZ7wY7MRDSygp63HIEfdQZA",
            SERIE_A = "UCBJeMCIeLQos7wacox4hmLQ", BUNDESLIGA = "UC6UL29enLNe4mqwTfAyeNuw";

    private static final Map<String, String[]> CHANNELS = new HashMap<>();
    static {
        CHANNELS.put("eng.1", new String[]{SKY_PL});
        CHANNELS.put("eng.2", new String[]{SKY_FOOTBALL});
        CHANNELS.put("esp.1", new String[]{SKY_FOOTBALL});
        CHANNELS.put("ita.1", new String[]{SERIE_A, SKY_FOOTBALL});
        CHANNELS.put("ger.1", new String[]{BUNDESLIGA, SKY_FOOTBALL});
    }

    /** Clubs as the channels name them where their full names are not used. */
    private static final String[][] ALIASES = {
            {"manchester united", "man utd", "man united"}, {"manchester city", "man city"},
            {"tottenham hotspur", "spurs", "tottenham"}, {"wolverhampton wanderers", "wolves"},
            {"brighton hove albion", "brighton"}, {"nottingham forest", "forest", "nott m forest"},
            {"west ham united", "west ham"}, {"newcastle united", "newcastle"}, {"internazionale", "inter"},
            {"bayern munich", "bayern"}, {"fc cologne", "koln", "cologne"}, {"paris saint germain", "psg", "paris"},
            {"atletico madrid", "atletico", "atleti"}, {"sheffield wednesday", "sheff wed"}, {"sheffield united", "sheff utd"},
            {"queens park rangers", "qpr"}, {"west bromwich albion", "west brom"}, {"afc bournemouth", "bournemouth"}};
    private static final String[] STOP = {"united", "city", "real", "club", "football", "sporting", "athletic", "calcio",
            "olympique", "borussia", "hotspur", "albion", "wanderers", "rovers", "town", "county", "deportivo", "union"};

    private static final Map<String, Object[]> feeds = new HashMap<>();   // channel: {read at, its videos}
    private static final Pattern ENTRY = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);

    private OfficialVideos() { }

    /** Whether there is an official source for a league's highlights. */
    static boolean covered(String slug) {
        return slug != null && CHANNELS.containsKey(slug);
    }

    /** A finished game's highlights, the likeliest first (ESPN's for LaLiga); empty while none is up yet. */
    static List<Video> highlights(ScoresTicker.Game g) {
        List<Video> out = new ArrayList<>();
        if (g == null || g.slug == null) return out;
        if ("esp.1".equals(g.slug) && g.id != null) {
            for (String[] c : GoalClips.clips(g.slug, g.id)) {
                if (!c[1].toLowerCase(Locale.ROOT).contains("highlight")) continue;
                Video v = new Video();
                v.mp4 = c[2];
                v.title = c[1];
                v.thumb = c.length > 3 ? c[3] : null;
                out.add(v);
            }
        }
        String[] chans = CHANNELS.get(g.slug);
        for (int i = 0; chans != null && i < chans.length; i++) {
            for (Video v : feed(chans[i])) {
                String t = plain(v.title);
                if (!t.contains("highlight")) continue;
                if (g.kickoff > 0 && v.published > 0 && v.published < g.kickoff) continue;
                if (named(t, g.homeName) && named(t, g.awayName)) out.add(v);
            }
        }
        return out;
    }

    /** A channel's latest uploads (its public feed), read again at most every 90 seconds. */
    static synchronized List<Video> feed(String channel) {
        Object[] kept = feeds.get(channel);
        if (kept != null && System.currentTimeMillis() - (long) kept[0] < 90_000L) {
            @SuppressWarnings("unchecked") List<Video> v = (List<Video>) kept[1];
            return v;
        }
        List<Video> out = new ArrayList<>();
        try {
            String x = ScoresTicker.get("https://www.youtube.com/feeds/videos.xml?channel_id=" + channel);
            Matcher m = ENTRY.matcher(x);
            while (m.find()) {
                String e = m.group(1);
                Video v = new Video();
                v.youtube = tag(e, "yt:videoId");
                v.title = unescape(tag(e, "title"));
                v.thumb = attr(e, "media:thumbnail", "url");
                v.description = unescape(tag(e, "media:description"));
                v.shorts = e.contains("/shorts/");
                try { v.published = java.time.OffsetDateTime.parse(tag(e, "published")).toInstant().toEpochMilli(); } catch (Exception ignored) { }
                if (v.youtube != null && !v.youtube.isEmpty()) out.add(v);
            }
        } catch (Exception ignored) {
        }
        feeds.put(channel, new Object[]{System.currentTimeMillis(), out});
        return out;
    }

    /** Whether a title names a club: its full name, a name it goes by, or its own word ("Liverpool", "Frankfurt"). */
    static boolean named(String plainTitle, String club) {
        if (club == null || club.isEmpty()) return false;
        String c = plain(club);
        if (plainTitle.contains(c)) return true;
        for (String[] a : ALIASES) {
            if (!c.contains(a[0]) && !a[0].contains(c)) continue;
            for (int i = 1; i < a.length; i++) if (plainTitle.contains(a[i])) return true;
        }
        for (String w : c.split(" ")) {
            if (w.length() < 4) continue;
            boolean stop = false;
            for (String s : STOP) stop |= s.equals(w);
            if (!stop && plainTitle.contains(w)) return true;
        }
        return false;
    }

    /** Lower case, without accents or punctuation, single spaces. */
    static String plain(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replace("&", " ").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String tag(String x, String name) {
        int a = x.indexOf("<" + name + ">");
        if (a < 0) return null;
        int b = x.indexOf("</" + name + ">", a);
        return b < 0 ? null : x.substring(a + name.length() + 2, b).trim();
    }

    private static String attr(String x, String name, String key) {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + "[^>]*\\s" + key + "=\"([^\"]*)\"").matcher(x);
        return m.find() ? m.group(1) : null;
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }
}
