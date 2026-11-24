package com.gathin.tvhomephone.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settings' search: a setting found by its words, the names and the lines under them on every section's page, read
 * from the pages as they are made (so it says what the pages say, whatever each part puts on them), never from a list
 * kept here. Each word looked for must start a word of the setting's ("rew" finds "Rewind"; "on" alone doesn't find
 * every "On" switch, which are left out). The section names come first, then the settings that start with what was
 * typed, then the rest, each in the page's order. What the family types into a field (a playlist's address, a
 * password) is never read: only a field's hint.
 */
final class SettingsSearch {
    private SettingsSearch() {}

    /** The most matches listed. */
    static final int MOST = 60;

    /** A section's page as made so far (null before it is made). */
    interface Pages {
        View page(Sections.Section s);
    }

    /** A piece of writing on a section's page: the view that shows it, and its words. */
    static final class Found {
        final View view;
        final String text;

        Found(View view, String text) {
            this.view = view;
            this.text = text;
        }
    }

    /**
     * A match: its section; the view that shows it (null for the section itself); its words; where the words looked
     * for are in them ({start, end} pairs); how well it matches (lower first); and its place in the page's order.
     */
    static final class Hit {
        final Sections.Section section;
        final View view;
        final String text;
        final int[] spans;
        final int score, order;

        Hit(Sections.Section section, View view, String text, int[] spans, int score, int order) {
            this.section = section;
            this.view = view;
            this.text = text;
            this.spans = spans;
            this.score = score;
            this.order = order;
        }
    }

    /** Pure: words as they are matched (lower case, accents off, anything not a letter or a digit a space) and where each came from. */
    static final class Folded {
        final String text;
        final int[] from;

        Folded(String text, int[] from) {
            this.text = text;
            this.from = from;
        }
    }

    static Folded fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int[] from = new int[s.length() * 2 + 2];
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int len = Character.charCount(cp);
            String part;
            // An apostrophe joins its word ("don't" is found by "dont").
            if (cp == '\'' || cp == '’') {
                i += len;
                continue;
            }
            if (Character.isLetterOrDigit(cp)) {
                part = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
            } else {
                part = " ";
            }
            for (int k = 0; k < part.length(); k++) {
                char ch = part.charAt(k);
                // The accents NFD set apart, and any other mark, dropped.
                if (ch >= '̀' && ch <= 'ͯ' || Character.getType(ch) == Character.NON_SPACING_MARK) continue;
                if (n == from.length) from = java.util.Arrays.copyOf(from, from.length * 2);
                b.append(ch);
                from[n++] = i;
            }
            i += len;
        }
        return new Folded(b.toString(), java.util.Arrays.copyOf(from, n));
    }

    /** Pure: the words of what was typed, folded. */
    static String[] words(String query) {
        if (query == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (String w : fold(query).text.split(" ")) if (!w.isEmpty()) out.add(w);
        return out.toArray(new String[0]);
    }

    /** Pure: where in s each word looked for starts one of s's words, as {start, end} pairs in s; null when one doesn't. */
    static int[] match(String s, String[] q) {
        if (q.length == 0) return null;
        Folded f = fold(s);
        String t = f.text;
        int[] spans = new int[q.length * 2];
        for (int w = 0; w < q.length; w++) {
            int at = -1;
            for (int i = t.indexOf(q[w]); i >= 0; i = t.indexOf(q[w], i + 1)) {
                if (i == 0 || t.charAt(i - 1) == ' ') {
                    at = i;
                    break;
                }
            }
            if (at < 0) return null;
            spans[w * 2] = f.from[at];
            int last = f.from[at + q[w].length() - 1];
            spans[w * 2 + 1] = last + Character.charCount(s.codePointAt(last));
        }
        return spans;
    }

    /** Pure: 0 when s starts with what was typed, 1 when it is there together at a word's start, 2 when its words are apart. */
    static int score(String s, String[] q) {
        String t = squash(fold(s).text), all = String.join(" ", q);
        if (t.startsWith(all)) return 0;
        return t.contains(" " + all) ? 1 : 2;
    }

    private static String squash(String t) {
        StringBuilder b = new StringBuilder(t.length());
        boolean space = true;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == ' ') {
                if (!space) b.append(' ');
                space = true;
            } else {
                b.append(ch);
                space = false;
            }
        }
        return b.toString().trim();
    }

    /** Pure: a line as it is shown: its breaks and runs of spaces as single spaces. */
    static String plain(CharSequence s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                space = b.length() > 0;
            } else {
                if (space) b.append(' ');
                b.append(ch);
                space = false;
            }
        }
        return b.toString();
    }

    /** Pure: a switch's own word, which says nothing of what it switches (left out). */
    static boolean onOff(String folded) {
        return folded.equals("on") || folded.equals("off");
    }

    /**
     * The writing on a section's page as the family sees it: what shows (nothing hidden, nothing under a part that is
     * hidden), each line once, in the page's order; a field's hint, never what is typed in it.
     */
    static List<Found> collect(View root) {
        List<Found> out = new ArrayList<>();
        walk(root, out, new HashSet<>());
        return out;
    }

    private static void walk(View v, List<Found> out, Set<String> seen) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof EditText) {
            add(v, ((EditText) v).getHint(), out, seen);
            return;
        }
        if (v instanceof TextView) add(v, ((TextView) v).getText(), out, seen);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), out, seen);
        }
    }

    private static void add(View v, CharSequence words, List<Found> out, Set<String> seen) {
        String text = plain(words);
        if (text.length() < 2) return;
        String key = squash(fold(text).text);
        if (key.isEmpty() || onOff(key) || !seen.add(key)) return;
        out.add(new Found(v, text));
    }

    /** What was typed, found: the sections' names, then the settings on the pages made so far, best first. */
    static List<Hit> find(Context c, String query, Pages pages) {
        String[] q = words(query);
        if (q.length == 0) return Collections.emptyList();
        List<Hit> out = new ArrayList<>();
        int order = 0;
        for (Sections.Section s : Sections.ALL) {
            String name = c.getString(s.title);
            String own = squash(fold(name).text);
            int[] spans = match(name, q);
            if (spans != null) out.add(new Hit(s, null, name, spans, -1, order++));
            View page = pages.page(s);
            if (page == null) continue;
            for (Found f : collect(page)) {
                // The page's own heading is the section itself.
                if (squash(fold(f.text).text).equals(own)) continue;
                int[] m = match(f.text, q);
                if (m != null) out.add(new Hit(s, f.view, f.text, m, score(f.text, q), order++));
            }
        }
        out.sort((a, b) -> a.score != b.score ? Integer.compare(a.score, b.score) : Integer.compare(a.order, b.order));
        return out.size() > MOST ? new ArrayList<>(out.subList(0, MOST)) : out;
    }

    /** Where a piece of writing is now on its section's page: the view itself while it is still there, else one saying the same. */
    static View locate(View page, View view, String text) {
        if (view != null && within(view, page) && view.getVisibility() == View.VISIBLE) return view;
        String key = squash(fold(text).text);
        for (Found f : collect(page)) if (squash(fold(f.text).text).equals(key)) return f.view;
        return null;
    }

    /** Whether v is page or lies in it. */
    static boolean within(View v, View page) {
        for (View at = v; at != null; at = at.getParent() instanceof View ? (View) at.getParent() : null) if (at == page) return true;
        return false;
    }
}
