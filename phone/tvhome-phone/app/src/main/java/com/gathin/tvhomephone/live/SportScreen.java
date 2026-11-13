package com.gathin.tvhomephone.live;

import android.graphics.Rect;

import com.gathin.broadcast.SportBoard;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tennis as the channel's own scoreboard shows it, read off the picture (ChannelReader's lines, each with its box): each
 * player's row found by their surname, the numbers along it in order (each set's games, then the game's points), so
 * the board can show the points the scores' feed doesn't have, and the set in play's games as soon as the picture does.
 * Only taken when both rows read and agree with the feed's sets (the games in play a game apart at most): a misread or
 * another court's scoreboard leaves the board as the feed has it. The server is not read (the channel's ball or dot is
 * not a word). Pure.
 */
final class SportScreen {
    private SportScreen() { }

    /** What the picture said: each row's points ("" none shown) and its games in the set in play; found: both rows read. */
    static final class Reading {
        final String[] points = {"", ""};
        final int[] games = {-1, -1};
        boolean found;
    }

    private static final Pattern TOKEN = Pattern.compile("^(\\d{1,2}|AD|A)$");

    /** Pure: the two rows of the channel's tennis scoreboard among the lines read, for this match. */
    static Reading tennis(List<String> lines, List<Rect> boxes, SportBoard b) {
        Reading r = new Reading();
        if (lines == null || boxes == null || b == null || b.sport != SportBoard.TENNIS || !b.live()) return r;
        int n = b.setColumns();
        List<String> a = row(lines, boxes, b.a), c = row(lines, boxes, b.b);
        if (a == null || c == null) return r;
        String[] pa = parse(a, n, b.tiebreakNow()), pc = parse(c, n, b.tiebreakNow());
        if (pa == null || pc == null) return r;
        int ga = number(pa[1]), gc = number(pc[1]);
        int fa = n - 1 < b.a.games.length ? b.a.games[n - 1] : 0, fc = n - 1 < b.b.games.length ? b.b.games[n - 1] : 0;
        // The set in play as the picture shows it: never more than a game from the feed's.
        if (ga < 0 || gc < 0 || Math.abs(ga - fa) > 1 || Math.abs(gc - fc) > 1) return r;
        // Points only for both or neither (a misread on one row leaves both out).
        if (pa[0].isEmpty() != pc[0].isEmpty()) return r;
        r.points[0] = pa[0];
        r.points[1] = pc[0];
        r.games[0] = ga;
        r.games[1] = gc;
        r.found = true;
        return r;
    }

    /**
     * Pure: the numbers along a player's row: after their surname on its line, then the lines level with it to its
     * right, left to right. Null when the surname is not read.
     */
    static List<String> row(List<String> lines, List<Rect> boxes, SportBoard.Side side) {
        String sur = key(side.shortName == null || side.shortName.isEmpty() ? side.name : side.shortName);
        String[] words = sur.split("\\s+");
        String last = words.length == 0 ? "" : words[words.length - 1];
        if (last.length() < 3) return null;
        Pattern name = Pattern.compile("\\b" + Pattern.quote(last) + "\\b");
        int at = -1, end = -1;
        for (int i = 0; i < Math.min(lines.size(), boxes.size()); i++) {
            java.util.regex.Matcher m = name.matcher(key(lines.get(i)));
            if (m.find()) {
                at = i;
                end = m.end();
                break;
            }
        }
        if (at < 0) return null;
        List<String> out = new ArrayList<>();
        tokens(key(lines.get(at)).substring(end), out);
        Rect me = boxes.get(at);
        float h = Math.max(1, me.height());
        List<int[]> level = new ArrayList<>();
        for (int i = 0; i < Math.min(lines.size(), boxes.size()); i++) {
            if (i == at) continue;
            Rect q = boxes.get(i);
            float cy = (q.top + q.bottom) / 2f;
            if (cy < me.top - h * 0.35f || cy > me.bottom + h * 0.35f || q.left < me.right - h * 0.5f) continue;
            level.add(new int[]{q.left, i});
        }
        level.sort((x, y) -> Integer.compare(x[0], y[0]));
        for (int[] l : level) tokens(key(lines.get(l[1])), out);
        return out;
    }

    /**
     * Pure: a row's numbers as {points ("" when the picture shows none), the set in play's games}: as many as the sets
     * begun (no points shown), or one more (the points last: 0, 15, 30, 40, AD; a tiebreak's count). Null otherwise.
     */
    static String[] parse(List<String> t, int sets, boolean tiebreak) {
        if (t == null || sets <= 0) return null;
        if (t.size() == sets) return new String[]{"", t.get(sets - 1)};
        if (t.size() != sets + 1) return null;
        String p = t.get(sets);
        boolean ok = p.equals("0") || p.equals("15") || p.equals("30") || p.equals("40") || p.equals("AD") || p.equals("A")
                || tiebreak && number(p) >= 0;
        if (!ok) return null;
        return new String[]{p.equals("A") ? "AD" : p, t.get(sets - 1)};
    }

    private static void tokens(String s, List<String> out) {
        for (String w : s.trim().split("[\\s|·•]+")) {
            // A lone O read for a nought.
            String x = w.equals("O") ? "0" : w;
            if (TOKEN.matcher(x).matches()) out.add(x);
        }
    }

    private static int number(String s) {
        try {
            return s == null ? -1 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Pure: words as compared: capitals, without accents. */
    static String key(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }
}
