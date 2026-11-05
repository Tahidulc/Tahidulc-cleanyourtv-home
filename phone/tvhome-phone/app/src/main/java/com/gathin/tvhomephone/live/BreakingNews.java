package com.gathin.tvhomephone.live;

import com.gathin.tvhomephone.Net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Breaking news for the scoreboard over the picture, as the TV pops it up: BBC News's and BBC Sport's lead stories,
 * read every few minutes while the scoreboard is up; a new lead story published in the last 45 minutes that is news,
 * not a feature (a quiz, a video, "in pictures"), drops from the scoreboard. The first read only learns what is there.
 */
public final class BreakingNews {
    /** {feed, its name on the alert}. */
    static final String[][] FEEDS = {
            {"https://feeds.bbci.co.uk/news/rss.xml", "BBC News"},
            {"https://feeds.bbci.co.uk/sport/rss.xml", "BBC Sport"},
    };
    static final long EVERY_MS = 5 * 60_000L;
    private static final long FRESH_MS = 45 * 60_000L;

    /** A story: its headline, its one line, its link, when it was published (0 when not given). */
    static final class Story {
        final String title, line, link;
        final long at;

        Story(String title, String line, String link, long at) {
            this.title = title;
            this.line = line;
            this.link = link;
            this.at = at;
        }
    }

    private final Set<String> seen = new HashSet<>();
    private boolean primed;
    private long readAt;
    private static final BreakingNews SHARED = new BreakingNews();

    /** The one reader for the whole app (the scoreboard's and the alerts' watcher share what was seen). */
    static BreakingNews shared() {
        return SHARED;
    }

    /** From the alerts' watcher (off the main thread): the feeds read when due, each new breaking story sent. */
    public static void check(android.content.Context c) {
        if (!com.gathin.tvhomephone.sports.Kinds.on(c, "news")) return;
        BreakingNews n = SHARED;
        long now = System.currentTimeMillis();
        List<String[]> stories;
        synchronized (n) {
            if (!n.due(now)) return;
            stories = n.read(now);
        }
        post(c, stories);
    }

    /** Each story as an alert ({source, headline, line, link}): sent once, by its link. Off the main thread. */
    static void post(android.content.Context c, List<String[]> stories) {
        if (stories == null) return;
        for (String[] st : stories) {
            com.gathin.tvhomephone.sports.Events.Alert a = new com.gathin.tvhomephone.sports.Events.Alert("news",
                    "N|" + (st.length > 3 && !st[3].isEmpty() ? st[3] : st[1]), st[1], st[2], null);
            a.channel = st[0];
            a.slug = st.length > 3 ? st[3] : null;
            com.gathin.tvhomephone.App.io.execute(() -> com.gathin.tvhomephone.sports.Notifier.post(c, a));
        }
    }

    /** Whether it is time to read again. */
    synchronized boolean due(long now) {
        return now - readAt >= EVERY_MS;
    }

    /** The feeds read (off the main thread): each new breaking lead story, as {source, headline, line, link}. */
    synchronized List<String[]> read(long now) {
        readAt = now;
        List<String[]> out = new ArrayList<>();
        for (String[] f : FEEDS) {
            try {
                List<Story> stories = parse(Net.get(f[0]));
                if (stories.isEmpty()) continue;
                Story lead = stories.get(0);
                if (seen.add(lead.link.isEmpty() ? lead.title : lead.link) && primed && breaking(lead, now)) {
                    out.add(new String[]{f[1], lead.title, lead.line, lead.link});
                }
            } catch (Exception ignored) {
                // that feed another time
            }
        }
        primed = true;
        return out;
    }

    private static final Pattern ITEM = Pattern.compile("<item\\b[^>]*>(.*?)</item>", Pattern.DOTALL);

    /** Pure: an RSS feed's stories, in its order. */
    static List<Story> parse(String xml) {
        List<Story> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = ITEM.matcher(xml);
        while (m.find()) {
            String item = m.group(1);
            String title = tag(item, "title");
            if (title.isEmpty()) continue;
            out.add(new Story(title, tag(item, "description"), tag(item, "link"), date(tag(item, "pubDate"))));
        }
        return out;
    }

    /** A tag's words, CDATA unwrapped and the common entities read. */
    static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + name + "\\b[^>]*>(.*?)</" + name + ">", Pattern.DOTALL).matcher(xml);
        if (!m.find()) return "";
        String s = m.group(1).trim();
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) s = s.substring(9, s.length() - 3).trim();
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    static long date(String rfc1123) {
        try {
            return java.time.ZonedDateTime.parse(rfc1123, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Pure: news, not a feature, and published in the last 45 minutes. */
    static boolean breaking(Story s, long now) {
        String t = s.title.toLowerCase(Locale.ROOT);
        for (String f : new String[]{"watch:", "quiz", "in pictures", "listen:", "podcast", "week in", "your questions",
                "what we know", "the papers", "newspaper headlines", "how to ", "explained", "analysis:", "opinion"}) {
            if (t.startsWith(f) || t.contains(" " + f)) return false;
        }
        return s.at > 0 && now - s.at >= 0 && now - s.at < FRESH_MS;
    }
}
