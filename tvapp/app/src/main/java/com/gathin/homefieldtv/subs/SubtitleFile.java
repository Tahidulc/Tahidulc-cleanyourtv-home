package com.gathin.homefieldtv.subs;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A subtitle file for a film or an episode, as OpenSubtitles sends one: SubRip (.srt, most of them) or WebVTT (.vtt),
 * read into its lines (each with its start and end in ms, its words, and whether the file puts it at the top of the
 * picture), sorted by start. The words keep their italics, bold, underlining and colours (SubRip's &lt;i&gt; &lt;b&gt;
 * &lt;u&gt; &lt;font color&gt;, the SubStation tags some SubRip files carry, {\i1} {\an8}, and WebVTT's classes) and lose
 * every other tag (a speaker's &lt;v&gt;, timestamps inside a line, &lt;ruby&gt;). The writing is turned to UTF-8 first
 * (a file in its language's old Windows code page, or UTF-16, read as it was written).
 *
 * <p>The timing is moved when the lines are looked up (at(): later is +), so the file is read once. Pure and made off the
 * main thread; a frame only asks which lines are on (no allocation: into an array the caller keeps).
 */
public final class SubtitleFile {
    /** A file's lines on at once, at most (more overlapping lines than this are never on a picture). */
    public static final int MAX_ON = 8;
    /** No line lasts longer than this (a file with a broken end time would keep it on for ever). */
    static final long LONGEST_MS = 30_000L;

    /** One line of the file: when it shows (ms), its words (with their styles), and whether it goes at the top. */
    public static final class Line {
        public final long start, end;
        public final CharSequence text;
        public final boolean top;

        Line(long start, long end, CharSequence text, boolean top) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.top = top;
        }
    }

    public final List<Line> lines;
    private final long[] starts, ends;

    private SubtitleFile(List<Line> sorted) {
        lines = java.util.Collections.unmodifiableList(sorted);
        starts = new long[sorted.size()];
        ends = new long[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            starts[i] = sorted.get(i).start;
            ends[i] = sorted.get(i).end;
        }
    }

    public int size() {
        return lines.size();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    /** A file's bytes (its language's code helps read an old one), read into its lines. */
    public static SubtitleFile parse(byte[] data, String lang) {
        return parse(new String(utf8(data, lang), StandardCharsets.UTF_8));
    }

    /** A file's words, SubRip or WebVTT (by what it starts with), read into its lines. */
    public static SubtitleFile parse(String text) {
        String s = text == null ? "" : text;
        if (!s.isEmpty() && s.charAt(0) == (char) 0xFEFF) s = s.substring(1);
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        boolean vtt = s.trim().startsWith("WEBVTT");
        List<Line> out = new ArrayList<>();
        String[] all = s.split("\n", -1);
        int i = 0;
        if (vtt) {
            // The header and anything under it, up to the first blank line.
            while (i < all.length && !all[i].trim().isEmpty()) i++;
        }
        while (i < all.length) {
            // A block: up to the next blank line.
            while (i < all.length && all[i].trim().isEmpty()) i++;
            int from = i;
            while (i < all.length && !all[i].trim().isEmpty()) i++;
            if (from < i) block(all, from, i, vtt, out);
        }
        // In the order they start (a stable sort keeps the file's order for lines that start together).
        out.sort((a, b) -> Long.compare(a.start, b.start));
        return new SubtitleFile(out);
    }

    /** One block of lines from..to: its timing line, then its words (a VTT note, style or region is passed over). */
    private static void block(String[] all, int from, int to, boolean vtt, List<Line> out) {
        String first = all[from].trim();
        if (vtt && (first.startsWith("NOTE") || first.equals("STYLE") || first.equals("REGION") || first.startsWith("STYLE ")
                || first.startsWith("REGION "))) {
            return;
        }
        int timing = -1;
        for (int k = from; k < to && k < from + 3; k++) {
            if (all[k].contains("-->")) {
                timing = k;
                break;
            }
        }
        if (timing < 0) return;
        String t = all[timing].trim();
        int arrow = t.indexOf("-->");
        long start = time(t.substring(0, arrow).trim());
        String rest = t.substring(arrow + 3).trim();
        int space = firstSpace(rest);
        String endWord = space < 0 ? rest : rest.substring(0, space);
        long end = time(endWord);
        if (start < 0 || end < 0) return;
        boolean top = vtt && space >= 0 && vttTop(rest.substring(space + 1));
        if (end <= start) end = start + 2000L;
        if (end - start > LONGEST_MS) end = start + LONGEST_MS;
        StringBuilder words = new StringBuilder();
        for (int k = timing + 1; k < to; k++) {
            if (words.length() > 0) words.append('\n');
            words.append(all[k].trim());
        }
        if (words.length() == 0) return;
        boolean[] atTop = {top};
        CharSequence text = styled(words.toString(), atTop);
        if (text.length() == 0 || text.toString().trim().isEmpty()) return;
        out.add(new Line(start, end, text, atTop[0]));
    }

    private static int firstSpace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    /** Pure: whether a WebVTT cue's settings put it in the top half ("line:0", "line:10%", "line:2"). */
    static boolean vttTop(String settings) {
        for (String part : settings.trim().split("\\s+")) {
            if (!part.startsWith("line:")) continue;
            String v = part.substring(5);
            int comma = v.indexOf(',');
            if (comma >= 0) v = v.substring(0, comma);
            try {
                if (v.endsWith("%")) return Float.parseFloat(v.substring(0, v.length() - 1)) < 50f;
                float n = Float.parseFloat(v);
                // A line number counts from the top when it is 0 or more, from the foot when less.
                return n >= 0f && n < 6f;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Pure: a time as the files write it, in ms ("00:01:02,345", "00:01:02.345", "01:02.345", "1:02:03.5"), or -1 when
     * it isn't one.
     */
    static long time(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.isEmpty()) return -1;
        int dot = Math.max(t.lastIndexOf(','), t.lastIndexOf('.'));
        String whole = dot < 0 ? t : t.substring(0, dot), frac = dot < 0 ? "" : t.substring(dot + 1);
        String[] parts = whole.split(":");
        if (parts.length < 2 || parts.length > 3) return -1;
        long total = 0;
        for (String p : parts) {
            if (p.isEmpty() || !digits(p)) return -1;
            total = total * 60 + Long.parseLong(p);
        }
        long ms = 0;
        if (!frac.isEmpty()) {
            if (!digits(frac)) return -1;
            String f = (frac + "00").substring(0, 3);
            ms = Long.parseLong(f);
        }
        return total * 1000 + ms;
    }

    private static boolean digits(String s) {
        if (s.length() > 9) return false;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return true;
    }

    // ---- the words' styles ------------------------------------------------------------------------------------------

    private static final int ITALIC = 0, BOLD = 1, UNDERLINE = 2, COLOUR = 3;

    /**
     * Pure: a line's words with their styles as spans: italics, bold, underlining and colours kept; every other tag gone;
     * the entities written out (&amp;amp; &amp;lt; &amp;nbsp;…); SubStation's {\an8} (and 7, 9) set top[0].
     */
    static CharSequence styled(String raw, boolean[] top) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        // Open styles: their kind, where they started, their colour.
        int[] kind = new int[16], at = new int[16], colour = new int[16];
        int open = 0;
        String s = raw.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ");
        int i = 0, n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '{' && i + 1 < n && s.charAt(i + 1) == '\\') {
                int close = s.indexOf('}', i);
                if (close < 0) break;
                // SubStation's overrides: italics and bold on and off, the top of the picture; the rest dropped.
                for (String o : s.substring(i + 2, close).split("\\\\")) {
                    String v = o.trim();
                    if (v.equals("an7") || v.equals("an8") || v.equals("an9")) top[0] = true;
                    else if (v.equals("i1")) open = push(kind, at, colour, open, ITALIC, out.length(), 0);
                    else if (v.equals("i0")) open = pop(kind, at, colour, open, ITALIC, out);
                    else if (v.equals("b1")) open = push(kind, at, colour, open, BOLD, out.length(), 0);
                    else if (v.equals("b0")) open = pop(kind, at, colour, open, BOLD, out);
                    else if (v.equals("u1")) open = push(kind, at, colour, open, UNDERLINE, out.length(), 0);
                    else if (v.equals("u0")) open = pop(kind, at, colour, open, UNDERLINE, out);
                }
                i = close + 1;
                continue;
            }
            if (ch == '<') {
                int close = s.indexOf('>', i);
                if (close > i + 1 && close - i < 120) {
                    String tag = s.substring(i + 1, close).trim();
                    open = tag(tag, kind, at, colour, open, out);
                    i = close + 1;
                    continue;
                }
            }
            if (ch == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i + 1 && semi - i <= 8) {
                    String e = entity(s.substring(i + 1, semi));
                    if (e != null) {
                        out.append(e);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            out.append(ch);
            i++;
        }
        while (open > 0) open = pop(kind, at, colour, open, kind[open - 1], out);
        // No blank lines inside, nor at either end (a tag alone on a line leaves one).
        trim(out);
        return out;
    }

    /** A tag: a style opened or closed, or nothing (every other tag is dropped, its words kept). */
    private static int tag(String tag, int[] kind, int[] at, int[] colour, int open, SpannableStringBuilder out) {
        boolean closing = tag.startsWith("/");
        String body = closing ? tag.substring(1).trim() : tag;
        String name = body;
        int cut = 0;
        while (cut < name.length() && (Character.isLetterOrDigit(name.charAt(cut)))) cut++;
        String rest = name.substring(cut);
        name = name.substring(0, cut).toLowerCase(Locale.ROOT);
        int k;
        int col = 0;
        switch (name) {
            case "i": k = ITALIC; break;
            case "b": k = BOLD; break;
            case "u": k = UNDERLINE; break;
            case "font": {
                k = COLOUR;
                col = closing ? 0 : fontColour(rest);
                if (!closing && col == 0) return push(kind, at, colour, open, COLOUR, out.length(), 0);
                break;
            }
            case "c": {
                // WebVTT's class: a colour's name among them ("c.yellow", "c.bg_black.white").
                k = COLOUR;
                if (!closing) {
                    for (String cls : rest.split("\\.")) {
                        int named = named(cls.trim());
                        if (named != 0) col = named;
                    }
                }
                break;
            }
            default:
                return open;
        }
        if (closing) return pop(kind, at, colour, open, k, out);
        return push(kind, at, colour, open, k, out.length(), col);
    }

    private static int push(int[] kind, int[] at, int[] colour, int open, int k, int where, int col) {
        if (open >= kind.length) return open;
        kind[open] = k;
        at[open] = where;
        colour[open] = col;
        return open + 1;
    }

    /** The newest open style of kind k closed (its span set), and any opened after it with it. */
    private static int pop(int[] kind, int[] at, int[] colour, int open, int k, SpannableStringBuilder out) {
        int found = -1;
        for (int j = open - 1; j >= 0; j--) {
            if (kind[j] == k) {
                found = j;
                break;
            }
        }
        if (found < 0) return open;
        for (int j = open - 1; j >= found; j--) span(out, kind[j], at[j], colour[j]);
        return found;
    }

    private static void span(SpannableStringBuilder out, int k, int from, int col) {
        int to = out.length();
        if (to <= from) return;
        Object what;
        switch (k) {
            case ITALIC: what = new StyleSpan(Typeface.ITALIC); break;
            case BOLD: what = new StyleSpan(Typeface.BOLD); break;
            case UNDERLINE: what = new UnderlineSpan(); break;
            default:
                if (col == 0) return;
                what = new ForegroundColorSpan(col);
        }
        out.setSpan(what, from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Pure: a &lt;font&gt; tag's colour (color="#ff0" or "#ffff00" or a name), or 0. */
    static int fontColour(String attrs) {
        String a = attrs.toLowerCase(Locale.ROOT);
        int c = a.indexOf("color");
        if (c < 0) return 0;
        int eq = a.indexOf('=', c);
        if (eq < 0) return 0;
        String v = a.substring(eq + 1).trim();
        if (v.startsWith("\"") || v.startsWith("'")) {
            char q = v.charAt(0);
            int end = v.indexOf(q, 1);
            v = end < 0 ? v.substring(1) : v.substring(1, end);
        } else {
            int end = firstSpace(v);
            if (end >= 0) v = v.substring(0, end);
        }
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() == 3 && hex(v)) {
            v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
        }
        if (v.length() == 6 && hex(v)) return 0xFF000000 | Integer.parseInt(v, 16);
        if (v.length() == 8 && hex(v)) return 0xFF000000 | (int) (Long.parseLong(v, 16) & 0xFFFFFF);
        return named(v);
    }

    private static boolean hex(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    /** Pure: a colour's name as subtitle files use them, or 0. */
    static int named(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "white": return 0xFFFFFFFF;
            case "yellow": return 0xFFFFFF00;
            case "red": return 0xFFFF0000;
            case "lime": case "green": return 0xFF00FF00;
            case "cyan": case "aqua": return 0xFF00FFFF;
            case "blue": return 0xFF0000FF;
            case "magenta": case "fuchsia": return 0xFFFF00FF;
            case "black": return 0xFF000000;
            case "grey": case "gray": case "silver": return 0xFFC0C0C0;
            default: return 0;
        }
    }

    /** Pure: an entity's words (amp, lt, gt, quot, apos, nbsp, lrm, rlm, #39, #x27), or null. */
    static String entity(String name) {
        switch (name) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            case "lrm": return "‎";
            case "rlm": return "‏";
            default:
                try {
                    if (name.startsWith("#x") || name.startsWith("#X")) return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
                    if (name.startsWith("#")) return new String(Character.toChars(Integer.parseInt(name.substring(1))));
                } catch (IllegalArgumentException e) {
                    return null;
                }
                return null;
        }
    }

    /** Blank lines taken out of the words, and spaces at either end (the spans kept on the words). */
    private static void trim(SpannableStringBuilder b) {
        int i = 0;
        while (i < b.length()) {
            char ch = b.charAt(i);
            boolean lineStart = i == 0 || b.charAt(i - 1) == '\n';
            if (ch == '\n' && lineStart) {
                b.delete(i, i + 1);
                continue;
            }
            if ((ch == ' ' || ch == '\t') && lineStart) {
                b.delete(i, i + 1);
                continue;
            }
            i++;
        }
        int end = b.length();
        while (end > 0 && Character.isWhitespace(b.charAt(end - 1))) end--;
        if (end < b.length()) b.delete(end, b.length());
    }

    // ---- what shows when --------------------------------------------------------------------------------------------

    /**
     * The lines on at a moment, ms into the film, with the timing moved offsetMs (+ shows them later): their places in
     * lines, in the order they start, into out (MAX_ON at most); how many. Allocates nothing.
     */
    public int at(long ms, int offsetMs, int[] out) {
        long t = ms - offsetMs;
        int n = starts.length, lo = 0, hi = n - 1, last = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (starts[mid] <= t) {
                last = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (last < 0) return 0;
        // Lines overlap only a little: a few back is enough (none lasts longer than LONGEST_MS).
        int first = last;
        while (first > 0 && t - starts[first - 1] < LONGEST_MS) first--;
        int count = 0;
        for (int i = first; i <= last && count < out.length; i++) if (t < ends[i]) out[count++] = i;
        return count;
    }

    // ---- the file's writing -----------------------------------------------------------------------------------------

    /**
     * Pure: the file's writing as UTF-8: as it is when it already is (its mark taken off), UTF-16 with its mark, else read
     * in the Windows code page its language used (Arabic, Cyrillic, Greek, Turkish, Hebrew, central Europe's, the West's
     * for the rest).
     */
    public static byte[] utf8(byte[] data, String lang) {
        if (data == null) return new byte[0];
        int start = data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF ? 3 : 0;
        if (data.length >= 2 && ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE || (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF)) {
            return new String(data, StandardCharsets.UTF_16).getBytes(StandardCharsets.UTF_8);
        }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, start, data.length - start));
            return start == 0 ? data : Arrays.copyOfRange(data, start, data.length);
        } catch (CharacterCodingException notUtf8) {
            Charset cs;
            try {
                cs = Charset.forName(codePage(lang));
            } catch (IllegalArgumentException e) {
                cs = StandardCharsets.ISO_8859_1;
            }
            CharBuffer chars = cs.decode(ByteBuffer.wrap(data));
            return chars.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Pure: the old Windows code page a language's files were written in. */
    static String codePage(String lang) {
        String b = Langs.base(lang == null ? "" : lang);
        switch (b) {
            case "ar": case "fa": case "ur": return "windows-1256";
            case "ru": case "bg": case "uk": case "sr": case "mk": case "be": return "windows-1251";
            case "el": return "windows-1253";
            case "tr": return "windows-1254";
            case "he": return "windows-1255";
            case "pl": case "cs": case "hu": case "ro": case "hr": case "sk": case "sl": return "windows-1250";
            default: return "windows-1252";
        }
    }
}
