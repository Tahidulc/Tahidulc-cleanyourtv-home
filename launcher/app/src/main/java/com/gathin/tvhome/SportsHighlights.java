package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Highlights for the Sports page, a row for each sport, from the official YouTube channels that put them up where the
 * TV is (each channel's public list of uploads, its feed; no key needed): highlights, extended highlights, full fights,
 * every goal or touchdown, condensed matches and the like first, newest first; the channel's other full-length
 * videos after them when there are few; never Shorts, live streams or anything older than three weeks. Each sport is
 * read again at most every 15 minutes, off the main thread. OK on one plays it in YouTube.
 */
final class SportsHighlights {
    private SportsHighlights() { }

    /** One video: its YouTube id, title, the channel's name, the sport's mark and when it went up. */
    static final class Clip {
        String id, title, channel, mark;
        long published;
        boolean highlight;
    }

    /** A sport: its row's name, the mark on its cards and its channels {id, name}. */
    static final class Sport {
        final String name, mark;
        final String[][] channels;

        Sport(String name, String mark, String[][] channels) {
            this.name = name;
            this.mark = mark;
            this.channels = channels;
        }
    }

    /** The sports, in the order of their rows; every channel's feed checked to answer, with highlights in it. */
    static final Sport[] SPORTS = {
            new Sport("FOOTBALL", "FOOTBALL", new String[][]{
                    {"UCNAf1k0yIjyGu3k9BwAg3lg", "Sky Sports Premier League"}, {"UCZ7wY7MRDSygp63HIEfdQZA", "Sky Sports Football"}}),
            new Sport("FORMULA 1", "F1", new String[][]{
                    {"UC3kxJQ9RfaS5CKeYbbFMi4Q", "Sky Sports F1"}, {"UCB_qr75-ydFVKSF9Dmo6izg", "Formula 1"}}),
            new Sport("BOXING", "BOXING", new String[][]{
                    {"UC7LReVje9aPB4B6XAsXX8WQ", "Matchroom Boxing"}, {"UCurvRE5fGcdUgCYWgh-BDsg", "DAZN Boxing"},
                    {"UC_JQGBtA7P0RwkRxd7xpJcA", "Sky Sports Boxing"}}),
            new Sport("MMA · UFC", "UFC", new String[][]{{"UCvgfXK4nTYKudb0rFR6noLA", "UFC"}}),
            new Sport("TENNIS", "TENNIS", new String[][]{
                    {"UCbcxFkd6B9xUU54InHv4Tig", "Tennis TV"}, {"UCaBIVVpHjq6j3tSyxwTE-8Q", "WTA"}}),
            new Sport("CRICKET", "CRICKET", new String[][]{{"UCkd4takjjF1EGD1TKIK2QiA", "Sky Sports Cricket"}}),
            new Sport("RUGBY", "RUGBY", new String[][]{
                    {"UCE28rwYoaV7jvU6GVzdu_GQ", "World Rugby"}, {"UCL5bAcVfbxSAs-UM5f5ncWg", "Six Nations"}}),
            new Sport("NFL", "NFL", new String[][]{{"UCDVYQ4Zhbm3S2dlz7P1GBDg", "NFL"}}),
            new Sport("NBA", "NBA", new String[][]{{"UCWJ2lWNubArHWmf3FIHbfcQ", "NBA"}}),
    };

    static final int PER_SPORT = 10;
    private static final long KEEP_MS = 15 * 60_000L, OLDEST_MS = 21L * 24 * 3600_000L;
    /** A highlight by its title ("highlights" used as a verb, "Carragher highlights the…", left out). */
    private static final Pattern HIGHLIGHT = Pattern.compile("highlights?\\b(?!\\s+(the|how|why|what|a|an|his|her|their|its)\\b)"
            + "|full (fight|match|game|race)|every (goal|touchdown|try|wicket|point)|top \\d+ plays|extended|condensed"
            + "|recap|knockouts|best (shots|points)\\b", Pattern.CASE_INSENSITIVE);
    /** Live streams, press conferences and tactical feeds: not highlights, and often not playable afterwards. */
    private static final Pattern LIVE = Pattern.compile("^\\s*live\\b|\\blive (stream|press|weigh|undercard|match|coverage)"
            + "|tactical feed|press conference", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Object[]> kept = new HashMap<>();

    /** A sport's videos, highlights first (newest first), then its channels' other full-length ones; off the main thread. */
    static List<Clip> load(Sport s) {
        synchronized (kept) {
            Object[] k = kept.get(s.name);
            if (k != null && System.currentTimeMillis() - (long) k[0] < KEEP_MS) {
                @SuppressWarnings("unchecked") List<Clip> c = (List<Clip>) k[1];
                return c;
            }
        }
        List<Clip> high = new ArrayList<>(), other = new ArrayList<>();
        long now = System.currentTimeMillis();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String[] ch : s.channels) {
            for (OfficialVideos.Video v : OfficialVideos.feed(ch[0])) {
                if (v.youtube == null || v.shorts || v.title == null || v.title.contains("#") || LIVE.matcher(v.title).find()) continue;
                if (v.published > 0 && now - v.published > OLDEST_MS) continue;
                if (!seen.add(v.youtube)) continue;
                Clip c = new Clip();
                c.id = v.youtube;
                c.title = v.title.trim();
                c.channel = ch[1];
                c.mark = s.mark;
                c.published = v.published;
                c.highlight = HIGHLIGHT.matcher(c.title).find();
                (c.highlight ? high : other).add(c);
            }
        }
        high.sort((a, b) -> Long.compare(b.published, a.published));
        other.sort((a, b) -> Long.compare(b.published, a.published));
        List<Clip> out = new ArrayList<>();
        for (Clip c : high) if (out.size() < PER_SPORT) out.add(c);
        for (Clip c : other) if (out.size() < PER_SPORT) out.add(c);
        synchronized (kept) {
            kept.put(s.name, new Object[]{now, out});
        }
        return out;
    }

    /** The chip on a card: what kind of video it is, read from its title. */
    static String kind(Clip c) {
        String t = c.title.toLowerCase(Locale.UK);
        if (t.contains("full fight")) return "FULL FIGHT";
        if (t.contains("full match") || t.contains("full game highlights")) return "FULL GAME";
        if (t.contains("extended")) return "EXTENDED";
        if (t.contains("condensed")) return "CONDENSED";
        if (t.contains("every goal") || t.contains("every touchdown") || t.contains("every try")) return "EVERY SCORE";
        if (t.matches(".*top \\d+ plays.*")) return "TOP PLAYS";
        if (t.contains("recap")) return "RECAP";
        return c.highlight ? "HIGHLIGHTS" : null;
    }

    /** "12 min ago", "3h ago", "Yesterday", "4 days ago". */
    static String age(long published) {
        if (published <= 0) return "";
        long mins = Math.max(1, (System.currentTimeMillis() - published) / 60_000L);
        if (mins < 60) return mins + " min ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days == 1 ? "Yesterday" : days + " days ago";
    }
}
