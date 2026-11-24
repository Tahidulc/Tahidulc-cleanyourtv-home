package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the channel's own scoreboard says, from the words read off it (ChannelReader): its clock ("67:23"), the
 * minutes added ("+4"), the score where it matches one the scores have had (the digits alone can't say which is the
 * score), and F1's lap ("LAP 23/57") and session time. Pure: the tests give it lines of text.
 */
final class ScreenText {
    private ScreenText() {}

    /** What was read; -1 where nothing was. */
    static final class Reading {
        int clock = -1, added = -1, lap = -1, laps = -1, left = -1;

        boolean any() {
            return clock >= 0 || lap >= 0 || left >= 0;
        }
    }

    // "[.:]", never "[:.]": on a phone (ICU's regular expressions) a class opening "[:" with a ":]" later in the
    // pattern, as "(?![\\d:])" has, is read as a named class ("[:alpha:]") and the pattern fails to compile, which
    // stopped the app the first time the channel's scoreboard was read (4.10). Desktop Java takes either.
    private static final Pattern CLOCK = Pattern.compile("(?<![\\d:.])(\\d{1,3})\\s?[.:]\\s?([0-5]\\d)(?![\\d:])");
    private static final Pattern ADDED = Pattern.compile("\\+\\s?(\\d{1,2})(?!\\d)");
    private static final Pattern LAP = Pattern.compile("(?i)\\bLAP\\s*(\\d{1,2})\\s*(?:/|OF)\\s*(\\d{1,2})");
    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)");
    private static final Pattern SCORE = Pattern.compile("(?<![\\d:.])(\\d{1,2})\\s*[-\u2013\u2014]\\s*(\\d{1,2})(?![\\d:])");
    private static final Pattern HT = Pattern.compile("(?i)\\bHT\\b|HALF[ -]?TIME"), FT = Pattern.compile("(?i)\\bFT\\b|FULL[ -]?TIME");

    /**
     * The score the scoreboard shows, {home, away}: "2-1" (or "2 – 1") on a line, else the two numbers on it when there
     * are just two besides the clock (in boxes of their own); null when it can't be told.
     */
    static int[] score(List<String> lines) {
        for (String raw : lines) {
            String s = CLOCK.matcher(fix(raw)).replaceAll(" ");
            Matcher m = SCORE.matcher(s);
            if (m.find()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        List<Integer> n = numbers(lines);
        if (n.size() == 2 && n.get(0) <= 20 && n.get(1) <= 20) return new int[]{n.get(0), n.get(1)};
        return null;
    }

    /**
     * Whether the score the scoreboard shows is read as a score ("2-1", "2 – 1" on a line): the two numbers alone (score's
     * fallback) could be anything beside the clock (a stats graphic's, shirt numbers), so they count for less.
     */
    static boolean scoreSure(List<String> lines) {
        for (String raw : lines) {
            String s = CLOCK.matcher(fix(raw)).replaceAll(" ");
            if (SCORE.matcher(s).find()) return true;
        }
        return false;
    }

    /** "HT" or "FT" when the scoreboard says half time or full time; null otherwise. */
    static String state(List<String> lines) {
        for (String s : lines) {
            if (s == null) continue;
            if (FT.matcher(s).find()) return "FT";
            if (HT.matcher(s).find()) return "HT";
        }
        return null;
    }

    /** A football scoreboard's clock and minutes added, from its lines. */
    static Reading football(List<String> lines) {
        Reading r = new Reading();
        for (String raw : lines) {
            String s = fix(raw);
            Matcher m = CLOCK.matcher(s);
            while (m.find()) {
                int min = Integer.parseInt(m.group(1)), sec = Integer.parseInt(m.group(2));
                // A game's clock, not the time of day: up to two hours of play.
                if (min > 130) continue;
                r.clock = min * 60 + sec;
                Matcher a = ADDED.matcher(s.substring(m.end()));
                if (a.find() && a.start() < 6) r.added = Integer.parseInt(a.group(1));
            }
            if (r.added < 0) {
                Matcher a = ADDED.matcher(s);
                if (a.find() && !s.contains(":")) r.added = Integer.parseInt(a.group(1));
            }
        }
        return r;
    }

    /**
     * Whether the scoreboard's lines show the score home–away: the numbers on it, the clock's taken out, have home
     * then away next to each other (as "2-1", "2 1", or in two boxes read as two lines).
     */
    static boolean shows(List<String> lines, int home, int away) {
        List<Integer> nums = numbers(lines);
        for (int i = 0; i + 1 < nums.size(); i++) if (nums.get(i) == home && nums.get(i + 1) == away) return true;
        return false;
    }

    /** The scoreboard's small numbers in reading order, the clock and the minutes added left out. */
    static List<Integer> numbers(List<String> lines) {
        List<Integer> out = new ArrayList<>();
        for (String raw : lines) {
            String s = CLOCK.matcher(fix(raw)).replaceAll(" ");
            s = ADDED.matcher(s).replaceAll(" ");
            Matcher m = NUMBER.matcher(s);
            while (m.find()) out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    /** F1's tower: the lap ("LAP 23/57") or the session's time left ("18:42"). */
    static Reading f1(List<String> lines) {
        Reading r = new Reading();
        for (String raw : lines) {
            String s = fix(raw);
            Matcher l = LAP.matcher(s);
            if (l.find()) {
                r.lap = Integer.parseInt(l.group(1));
                r.laps = Integer.parseInt(l.group(2));
                if (r.lap > r.laps) {
                    r.lap = -1;
                    r.laps = -1;
                }
                continue;
            }
            Matcher m = CLOCK.matcher(s);
            if (m.find()) {
                int min = Integer.parseInt(m.group(1));
                if (min <= 90) r.left = min * 60 + Integer.parseInt(m.group(2));
            }
        }
        return r;
    }

    /** The letters a reader mistakes for digits, read as the digits next to digits ("6O:23" is "60:23"). */
    static String fix(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s);
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            char d = c == 'O' || c == 'o' ? '0' : c == 'l' || c == 'I' || c == '|' ? '1' : c == 'S' ? '5' : c == 'B' ? '8' : 0;
            if (d == 0) continue;
            boolean before = i > 0 && Character.isDigit(b.charAt(i - 1)), after = i + 1 < b.length() && Character.isDigit(b.charAt(i + 1));
            boolean colon = i + 1 < b.length() && b.charAt(i + 1) == ':' || i > 0 && b.charAt(i - 1) == ':';
            if (before || after || colon) b.setCharAt(i, d);
        }
        return b.toString();
    }
}
