package com.gathin.tvhomephone.live;

import com.gathin.tvhomephone.sports.OnAir;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a programme is, for the full-screen player's info tile, from the channel and the guide alone (pure, so it can
 * be tested): a game on a sports channel (Match night), a film (Cinematic), an episode of a series (Card), or anything
 * else, news included (Glass ribbon). The tile style itself (Style) is the family's choice in the player's options:
 * Auto picks by this, or one style for everything.
 */
public final class TileKind {
    private TileKind() {}

    public enum Kind { MATCH, FILM, SERIES, OTHER }

    /** The styles in the options' order: Auto, then the four tiles, then the guide panel as it was. */
    public enum Style {
        AUTO, RIBBON, CARD, MATCH, CINEMA, PANEL;

        static Style of(String name) {
            for (Style s : values()) if (s.name().equals(name)) return s;
            return AUTO;
        }
    }

    /** The tile Auto picks for a kind: D for a game, E for a film, B for a series' episode, A for the rest. */
    public static Style auto(Kind k) {
        switch (k) {
            case MATCH: return Style.MATCH;
            case FILM: return Style.CINEMA;
            case SERIES: return Style.CARD;
            default: return Style.RIBBON;
        }
    }

    private static final String[] SPORTS = {"sport", "tnt sp", "bt sp", "premier sp", "eurosport", "dazn", "viaplay",
            "laliga", "la liga", "bein", "espn", "skysp", "sky sp", "football", "racing tv", "golf", "f1", "mutv", "lfc tv",
            "lfctv", "chelsea tv", "nba", "nfl", "boxnation", "ufc", "premier league", "match of the day"};
    private static final String[] FILMS = {"movie", "film", "cinema", "premiere", "hollywood", "blockbuster", "flix",
            "great movies", "tcm", "talking pictures"};
    private static final String[] NEWS = {"news", "bbc parliament", "al jazeera", "cnn", "euronews", "bloomberg", "cnbc"};

    /** A sports channel, by its name or its playlist group. */
    public static boolean sportsChannel(String name, String group) {
        return any(lower(name) + " | " + lower(group), SPORTS);
    }

    /** A film channel ("Sky Cinema Premiere", a "Movies" group). */
    public static boolean filmChannel(String name, String group) {
        return any(lower(name) + " | " + lower(group), FILMS);
    }

    public static boolean newsChannel(String name, String group) {
        return any(lower(name) + " | " + lower(group), NEWS);
    }

    /** A film's year in the guide: "(2019)" in the title, or a description starting with it ("(2019) Thriller. ..."). */
    private static final Pattern YEAR_TITLE = Pattern.compile("\\((19[2-9]\\d|20[0-4]\\d)\\)\\s*$");
    private static final Pattern YEAR_DESC = Pattern.compile("^\\s*\\(?(19[2-9]\\d|20[0-4]\\d)\\)?[\\s.:,-]");
    /** "Film: ...", "Movie: ...", "Premiere: ..." ahead of a title. */
    private static final Pattern FILM_PREFIX = Pattern.compile("^\\s*(film|movie|premiere|sky cinema premiere)\\s*[:\\-]", Pattern.CASE_INSENSITIVE);
    /** "S2 E4", "S02E04", "Series 2, Episode 4", "Season 2 Ep 4", "Episode 4", "Ep. 4", "(4/6)". */
    private static final Pattern SE = Pattern.compile("\\bS(?:eries|eason)?\\s*\\.?\\s*(\\d{1,2})\\s*[,:/-]?\\s*E(?:p(?:isode)?)?\\s*\\.?\\s*(\\d{1,3})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EP = Pattern.compile("\\b(?:episode|ep\\.?)\\s*(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PART = Pattern.compile("\\((\\d{1,2})/(\\d{1,2})\\)");
    private static final Pattern SEASON_ONLY = Pattern.compile("\\b(?:series|season)\\s*(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);

    /**
     * The programme's kind. matched: one of today's games was found for it (OnAir.match), which makes it a game on any
     * channel; on a sports channel a title that reads as a fixture is one too.
     */
    public static Kind of(String channel, String group, String title, String desc, boolean matched) {
        String t = title == null ? "" : title, d = desc == null ? "" : desc;
        if (matched) return Kind.MATCH;
        if (sportsChannel(channel, group) && (OnAir.looksLikeFixture(t) || OnAir.looksLikeFixture(d))) return Kind.MATCH;
        if (film(channel, group, t, d)) return Kind.FILM;
        if (!newsChannel(channel, group) && episode(t, d) != null) return Kind.SERIES;
        return Kind.OTHER;
    }

    /** Whether it is a film: on a film channel, or the guide says so (its year, "Film:"). */
    static boolean film(String channel, String group, String title, String desc) {
        if (FILM_PREFIX.matcher(title).find() || YEAR_TITLE.matcher(title).find()) return true;
        if (episode(title, desc) != null) return false;
        if (filmChannel(channel, group)) return true;
        return YEAR_DESC.matcher(desc).find() && !sportsChannel(channel, group) && !newsChannel(channel, group);
    }

    /** The episode as the card shows it ("S2 · E4", "E4", "Part 4 of 6", "Series 2"), or null. */
    public static String episode(String title, String desc) {
        for (String s : new String[]{title == null ? "" : title, desc == null ? "" : desc}) {
            Matcher m = SE.matcher(s);
            if (m.find()) return "S" + Integer.parseInt(m.group(1)) + " · E" + Integer.parseInt(m.group(2));
        }
        for (String s : new String[]{title == null ? "" : title, desc == null ? "" : desc}) {
            Matcher m = EP.matcher(s);
            if (m.find()) return "E" + Integer.parseInt(m.group(1));
            m = PART.matcher(s);
            if (m.find()) return "Part " + Integer.parseInt(m.group(1)) + " of " + Integer.parseInt(m.group(2));
        }
        for (String s : new String[]{title == null ? "" : title, desc == null ? "" : desc}) {
            Matcher m = SEASON_ONLY.matcher(s);
            if (m.find()) return "Series " + Integer.parseInt(m.group(1));
        }
        return null;
    }

    /**
     * The title to look up a film or series by: "Film: Verity (2025)" → "Verity"; "Nocturne: Episode 1" → "Nocturne";
     * "New: The Repair Shop" → "The Repair Shop".
     */
    public static String lookupTitle(String title) {
        if (title == null) return "";
        String t = FILM_PREFIX.matcher(title).replaceFirst("");
        t = t.replaceFirst("(?i)^\\s*(new|live|premiere|brand new)\\s*[:\\-]\\s*", "");
        t = YEAR_TITLE.matcher(t).replaceFirst("");
        t = SE.matcher(t).replaceAll("");
        t = EP.matcher(t).replaceAll("");
        t = PART.matcher(t).replaceAll("");
        t = t.replaceAll("[\\s:,.\\-–]+$", "").replaceAll("\\s*[:\\-–]\\s*$", "");
        int colon = t.indexOf(": ");
        if (colon > 2 && colon < t.length() - 2 && episodeish(t.substring(colon + 2))) t = t.substring(0, colon);
        return t.replaceAll("\\s+", " ").trim();
    }

    /** A film's year from the guide, or "". */
    public static String year(String title, String desc) {
        Matcher m = YEAR_TITLE.matcher(title == null ? "" : title);
        if (m.find()) return m.group(1);
        m = YEAR_DESC.matcher(desc == null ? "" : desc);
        return m.find() ? m.group(1) : "";
    }

    private static boolean episodeish(String s) {
        return s.isEmpty() || SE.matcher(s).find() || EP.matcher(s).find();
    }

    private static boolean any(String s, String[] words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
