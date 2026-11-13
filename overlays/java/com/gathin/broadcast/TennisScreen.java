package com.gathin.broadcast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The channel's own tennis scoreboard read off the picture, the same on the TV and the phone: the games and the points as
 * the picture has them, the moment it has them (ESPN's scores come later and have no points). From the words the text
 * reader found, each with its box: each player's row found by their surname (a letter misread allowed), the numbers
 * along it lined up with the other row's in columns (a seed in brackets, or smaller, or by the name only, is not a
 * column), then the columns read against what ESPN already has: each finished set as ESPN has it, the set in play the
 * same or a few games on, then the points (0, 15, 30, 40, AD; a tiebreak's count). Some boards show the sets won instead
 * of each set's games: read too. Where the board sits (its box) comes back with it, so ours can go over it.
 *
 * <p>Pure: the apps read the picture and pass the words.
 */
public final class TennisScreen {
    private TennisScreen() { }

    /** A word read off the picture, with its box (any units, the same for all). */
    public static final class Word {
        public final String text;
        public final float l, t, r, b;

        public Word(String text, float l, float t, float r, float b) {
            this.text = text == null ? "" : text;
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
        }

        float cx() {
            return (l + r) / 2f;
        }

        float cy() {
            return (t + b) / 2f;
        }

        float h() {
            return Math.max(1f, b - t);
        }
    }

    /** What the picture said. games: each set's games for each player, the set in play last (null: not read). */
    public static final class Reading {
        public boolean found;
        public int[][] games;
        public final String[] points = {"", ""};
        /** The player the picture marks as serving (0 the first, 1 the second), -1 when it marks none. */
        public int server = -1;
        /** The board's box in the picture, and its rows' pitch. */
        public float l, t, r, b, row;
    }

    private static final Pattern POINT = Pattern.compile("^(0|15|30|40|AD)$");

    /** Pure: the channel's tennis scoreboard for this match among the words read, or a reading not found. */
    public static Reading read(List<Word> words, SportBoard m) {
        Reading out = new Reading();
        if (words == null || words.isEmpty() || m == null || m.sport != SportBoard.TENNIS) return out;
        Word na = name(words, m.a, null), nb = name(words, m.b, na);
        if (na == null || nb == null) return out;
        float h = (na.h() + nb.h()) / 2f, pitch = Math.abs(nb.cy() - na.cy());
        // Two rows of one board: one above the other, near each other, their names starting about level.
        if (pitch < h * 0.6f || pitch > h * 3.2f || Math.abs(na.l - nb.l) > h * 5f) return out;
        List<Word> ra = numbers(words, na, h), rb = numbers(words, nb, h);
        List<String[]> cols = new ArrayList<>();
        List<float[]> spans = new ArrayList<>();
        columns(ra, rb, na, nb, h, cols, spans);
        if (cols.isEmpty()) return out;
        int[] serve = serving(words, na, nb, h);
        if (!interpret(cols, m, out)) return out;
        out.server = serve[0];
        out.found = true;
        // The board's box: the names, the numbers, and a little round them (its flags, its server's ball, its edge).
        float l = Math.min(na.l, nb.l), t = Math.min(na.t, nb.t), r = Math.max(na.r, nb.r), b = Math.max(na.b, nb.b);
        for (float[] s : spans) {
            r = Math.max(r, s[1]);
            t = Math.min(t, s[2]);
            b = Math.max(b, s[3]);
        }
        out.l = l - h * 1.6f;
        out.t = t - h * 0.35f;
        out.r = r + h * 0.45f;
        out.b = b + h * 0.35f;
        out.row = pitch;
        return out;
    }

    // ---- the names ----------------------------------------------------------------------------------------------------

    /** Pure: the word that is this player's surname (its last word; a letter misread allowed in a long one); not other. */
    static Word name(List<Word> words, SportBoard.Side side, Word other) {
        String sur = key(side.shortName == null || side.shortName.isEmpty() ? side.name : side.shortName);
        String[] parts = sur.split("[\\s-]+");
        String last = parts.length == 0 ? "" : letters(parts[parts.length - 1]);
        if (last.length() < 3) return null;
        Word best = null;
        int bestCost = Integer.MAX_VALUE;
        for (Word w : words) {
            if (w == other) continue;
            String k = letters(key(w.text));
            if (k.length() < 3) continue;
            int cost;
            if (k.equals(last)) cost = 0;
            // An initial joined to it ("A.SABALENKA"), or a bracketed seed joined after it.
            else if (k.endsWith(last) && k.length() - last.length() <= 2 || k.startsWith(last) && k.length() - last.length() <= 2) cost = 1;
            else if (last.length() >= 5 && Math.abs(k.length() - last.length()) <= 1 && distance(k, last) <= (last.length() >= 9 ? 2 : 1)) cost = 2;
            else continue;
            if (cost < bestCost) {
                bestCost = cost;
                best = w;
            }
        }
        return best;
    }

    /** Pure: the edit distance between two words (letters added, dropped or changed). */
    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] x = prev;
            prev = cur;
            cur = x;
        }
        return prev[b.length()];
    }

    // ---- the numbers --------------------------------------------------------------------------------------------------

    /**
     * Pure: the numbers along a row to the name's right, as the picture has them: a seed in brackets left out, letters
     * read for figures put right ("4O" 40, "I5" 15), the small ones (a seed, a tiebreak's count beside a set) left out.
     */
    static List<Word> numbers(List<Word> words, Word name, float h) {
        List<Word> out = new ArrayList<>();
        for (Word w : words) {
            if (w == name) continue;
            if (Math.abs(w.cy() - name.cy()) > h * 0.55f || w.l < name.r - h * 0.3f || w.l > name.r + h * 16f) continue;
            String raw = w.text.trim();
            if (raw.startsWith("[") || raw.startsWith("(") || raw.endsWith("]") || raw.endsWith(")")) continue;
            String v = figure(raw);
            if (v == null) continue;
            out.add(new Word(v, w.l, w.t, w.r, w.b));
        }
        // The small ones out: a seed, a tiebreak's count (under seven tenths of the row's usual figures).
        float[] hs = new float[out.size()];
        for (int i = 0; i < hs.length; i++) hs[i] = out.get(i).h();
        Arrays.sort(hs);
        float usual = hs.length == 0 ? h : hs[hs.length / 2];
        List<Word> kept = new ArrayList<>();
        for (Word w : out) if (w.h() >= usual * 0.7f) kept.add(w);
        kept.sort((x, y) -> Float.compare(x.cx(), y.cx()));
        return kept;
    }

    /** Pure: a word as a score's figure ("6", "15", "AD"), or null: letters misread for figures put right. */
    static String figure(String raw) {
        String s = key(raw).replaceAll("[^A-Z0-9|]", "");
        if (s.isEmpty() || s.length() > 2) return null;
        if (s.equals("AD") || s.equals("A") || s.equals("AV")) return "AD";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            char d = c;
            if (c == 'O' || c == 'Q' || c == 'D') d = '0';
            else if (c == 'I' || c == 'L' || c == '|' || c == 'T') d = '1';
            else if (c == 'S') d = '5';
            else if (c == 'B') d = '8';
            else if (c == 'Z') d = '2';
            else if (c == 'G') d = '6';
            if (d < '0' || d > '9') return null;
            sb.append(d);
        }
        String v = sb.toString();
        if (v.length() == 2 && v.charAt(0) == '0') return null;
        return v;
    }

    /**
     * Pure: the two rows' numbers lined up in columns (each a figure in both rows, level across), left to right; a seed
     * column by the names left out. One row's AD with nothing beside it in the other: the other's 40.
     */
    static void columns(List<Word> ra, List<Word> rb, Word na, Word nb, float h, List<String[]> cols, List<float[]> spans) {
        boolean[] used = new boolean[rb.size()];
        List<Word[]> pairs = new ArrayList<>();
        for (Word a : ra) {
            int best = -1;
            float bestD = h * 0.85f;
            for (int j = 0; j < rb.size(); j++) {
                if (used[j]) continue;
                float d = Math.abs(rb.get(j).cx() - a.cx());
                if (d <= bestD) {
                    bestD = d;
                    best = j;
                }
            }
            if (best >= 0) {
                used[best] = true;
                pairs.add(new Word[]{a, rb.get(best)});
            } else if (a.text.equals("AD")) {
                pairs.add(new Word[]{a, new Word("40", a.l, nb.t, a.r, nb.b)});
            }
        }
        for (int j = 0; j < rb.size(); j++) {
            Word b = rb.get(j);
            if (!used[j] && b.text.equals("AD")) pairs.add(new Word[]{new Word("40", b.l, na.t, b.r, na.b), b});
        }
        pairs.sort((x, y) -> Float.compare(x[0].cx(), y[0].cx()));
        // A seed right by both names (names of a length): not a column.
        if (!pairs.isEmpty()) {
            Word[] f = pairs.get(0);
            if (f[0].l - na.r < h * 0.55f && f[1].l - nb.r < h * 0.55f && pairs.size() > 1) pairs.remove(0);
        }
        for (Word[] p : pairs) {
            cols.add(new String[]{p[0].text, p[1].text});
            spans.add(new float[]{Math.min(p[0].l, p[1].l), Math.max(p[0].r, p[1].r), Math.min(p[0].t, p[1].t), Math.max(p[0].b, p[1].b)});
        }
    }

    /** Pure: the server's mark beside a name (a dot or a ball read as one): {row (0, 1; -1 none)}. */
    static int[] serving(List<Word> words, Word na, Word nb, float h) {
        int mark = -1, marks = 0;
        for (Word w : words) {
            String s = w.text.trim();
            if (!(s.equals("•") || s.equals("●") || s.equals("◦") || s.equals("*") || s.equals("►") || s.equals("▶") || s.equals(">"))) continue;
            for (int row = 0; row < 2; row++) {
                Word n = row == 0 ? na : nb;
                boolean level = Math.abs(w.cy() - n.cy()) <= h * 0.5f;
                boolean near = w.r <= n.l + h * 0.2f && w.r >= n.l - h * 3f || w.l >= n.r - h * 0.2f && w.l <= n.r + h * 2.5f;
                if (level && near) {
                    if (mark != row) marks++;
                    mark = row;
                }
            }
        }
        return new int[]{marks == 1 ? mark : -1};
    }

    // ---- what the columns are ----------------------------------------------------------------------------------------

    /**
     * Pure: the columns read against ESPN's match: each set's games (the points last, or none), or the sets won then the
     * games (then the points). False when no reading fits.
     */
    static boolean interpret(List<String[]> cols, SportBoard m, Reading out) {
        int[] ea = m.a.games, eb = m.b.games;
        int n = Math.max(ea.length, eb.length);
        int done = 0;
        for (int i = 0; i < n; i++) if (SportBoard.setDone(ea, eb, i, n)) done = i + 1;
        // Each set's games, the points last (never after a set the picture has over: that is the next set's games).
        if (cols.size() >= 2 && points(cols.get(cols.size() - 1), cols.subList(0, cols.size() - 1))
                && !overBefore(cols.subList(0, cols.size() - 1)) && sets(cols.subList(0, cols.size() - 1), ea, eb, n, done, out)) {
            String[] p = cols.get(cols.size() - 1);
            out.points[0] = p[0];
            out.points[1] = p[1];
            return true;
        }
        // Each set's games, no points shown (between games, or a board that shows none).
        if (sets(cols, ea, eb, n, done, out)) return true;
        // The sets won, the games in the set in play (then the points).
        return setsWon(cols, m, out);
    }

    /** Pure: the last of these columns is a set over by its games (6-4, 7-5, 7-6). */
    static boolean overBefore(List<String[]> before) {
        if (before.isEmpty()) return false;
        String[] c = before.get(before.size() - 1);
        int a = num(c[0]), b = num(c[1]);
        return a >= 0 && b >= 0 && SportBoard.setDone(new int[]{a}, new int[]{b}, 0, 1);
    }

    /** Pure: a column is the points (both a point's, or a tiebreak's count when the set in play is six all). */
    static boolean points(String[] col, List<String[]> before) {
        if (POINT.matcher(col[0]).matches() && POINT.matcher(col[1]).matches()) return !(col[0].equals("AD") && col[1].equals("AD"));
        if (before.isEmpty()) return false;
        String[] cur = before.get(before.size() - 1);
        return "6".equals(cur[0]) && "6".equals(cur[1]) && num(col[0]) >= 0 && num(col[0]) <= 30 && num(col[1]) >= 0 && num(col[1]) <= 30;
    }

    /**
     * Pure: columns as each set's games, against ESPN's (ea, eb; n sets begun, done of them over): the sets ESPN has over
     * just as it has them, the set in play the same or a few games on (ESPN comes later), or that set over and the next
     * begun. out.games set when they fit.
     */
    static boolean sets(List<String[]> cols, int[] ea, int[] eb, int n, int done, Reading out) {
        int k = cols.size();
        if (k == 0 || k < n || k > n + 1 || k > 5) return false;
        int[] a = new int[k], b = new int[k];
        for (int i = 0; i < k; i++) {
            a[i] = num(cols.get(i)[0]);
            b[i] = num(cols.get(i)[1]);
            if (a[i] < 0 || b[i] < 0 || a[i] > 7 || b[i] > 7) return false;
        }
        for (int i = 0; i < k; i++) {
            if (i < done) {
                if (a[i] != ea[i] || b[i] != eb[i]) return false;
            } else if (i < n) {
                int pa = i < ea.length ? ea[i] : 0, pb = i < eb.length ? eb[i] : 0;
                if (a[i] < pa || b[i] < pb || a[i] - pa + b[i] - pb > 4) return false;
                if (i < k - 1 && !SportBoard.setDone(a, b, i, i + 1)) return false;
            } else {
                // A set begun on the picture before ESPN has it: the one before over there, this one at its start.
                if (!SportBoard.setDone(a, b, i - 1, i) || a[i] + b[i] > 2) return false;
            }
        }
        out.games = new int[][]{a, b};
        return true;
    }

    /** Pure: columns as the sets won, then the set in play's games (then the points); the sets' games kept from ESPN's. */
    static boolean setsWon(List<String[]> cols, SportBoard m, Reading out) {
        if (cols.size() < 2 || cols.size() > 3) return false;
        int[] won = m.sets();
        int wa = num(cols.get(0)[0]), wb = num(cols.get(0)[1]), ga = num(cols.get(1)[0]), gb = num(cols.get(1)[1]);
        if (wa < 0 || wb < 0 || ga < 0 || gb < 0 || ga > 7 || gb > 7) return false;
        if (wa < won[0] || wb < won[1] || wa + wb - won[0] - won[1] > 1) return false;
        int[] ea = m.a.games, eb = m.b.games;
        int n = Math.max(ea.length, eb.length);
        boolean newSet = wa + wb > won[0] + won[1];
        int[] a = Arrays.copyOf(ea, newSet ? n + 1 : Math.max(n, 1)), b = Arrays.copyOf(eb, newSet ? n + 1 : Math.max(n, 1));
        int cur = a.length - 1;
        if (!newSet && n > 0 && (ga < ea[n - 1] || gb < eb[n - 1])) return false;
        a[cur] = ga;
        b[cur] = gb;
        if (cols.size() == 3) {
            String[] p = cols.get(2);
            if (!points(p, cols.subList(1, 2))) return false;
            out.points[0] = p[0];
            out.points[1] = p[1];
        }
        out.games = new int[][]{a, b};
        return true;
    }

    // ---- bits ---------------------------------------------------------------------------------------------------------

    private static int num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String letters(String s) {
        return s.replaceAll("[^A-Z]", "");
    }

    /** Pure: words as compared: capitals, without accents. */
    static String key(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Pure: the match as the picture has it, on ESPN's: each set's games and the points from a reading (found), the
     * server where it marks one; ESPN's alone otherwise.
     */
    public static SportBoard apply(SportBoard m, Reading r) {
        if (m == null || r == null || !r.found || r.games == null) return m;
        SportBoard c = m.copy();
        c.a.games = r.games[0].clone();
        c.b.games = r.games[1].clone();
        int n = Math.max(c.a.games.length, c.b.games.length);
        c.a.tiebreak = Arrays.copyOf(m.a.tiebreak, n);
        c.b.tiebreak = Arrays.copyOf(m.b.tiebreak, n);
        for (int i = m.a.tiebreak.length; i < n; i++) c.a.tiebreak[i] = -1;
        for (int i = m.b.tiebreak.length; i < n; i++) c.b.tiebreak[i] = -1;
        c.a.points = r.points[0];
        c.b.points = r.points[1];
        if (r.server >= 0) {
            c.a.serving = r.server == 0;
            c.b.serving = r.server == 1;
        }
        return c;
    }
}
