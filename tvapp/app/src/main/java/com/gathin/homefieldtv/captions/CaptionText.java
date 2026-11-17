package com.gathin.homefieldtv.captions;

import java.util.Locale;

/**
 * The words of the automatic subtitles as they come: what was said (kept for a few lines back, so the newest roll up
 * under it) and what is being said now. Made readable: a capital to start each line, "I" written so. Pure Java.
 */
public final class CaptionText {
    /** About four lines of subtitles: enough for the two shown to roll up smoothly. */
    static final int KEEP = 240;

    private final StringBuilder said = new StringBuilder();
    private String now = "";

    /** What is being said now (replacing the last of these). */
    public void partial(String words) {
        now = tidy(words);
    }

    /** What was just said, finished: kept, and the words now cleared. */
    public void line(String words) {
        String t = tidy(words);
        now = "";
        if (t.isEmpty()) return;
        if (said.length() > 0) said.append(' ');
        said.append(sentence(t));
        trim();
    }

    public void clear() {
        said.setLength(0);
        now = "";
    }

    /** Everything to show, oldest first. */
    public String shown() {
        String n = now.isEmpty() ? "" : sentence(now);
        if (said.length() == 0) return n;
        return n.isEmpty() ? said.toString() : said + " " + n;
    }

    private void trim() {
        if (said.length() <= KEEP) return;
        int cut = said.indexOf(" ", said.length() - KEEP);
        said.delete(0, cut < 0 ? said.length() - KEEP : cut + 1);
    }

    /** Pure: spaces made single, "i" made "I" (and "i'm", "i've", "i'll", "i'd"). */
    static String tidy(String s) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return t;
        StringBuilder b = new StringBuilder();
        for (String w : t.split(" ")) {
            if (b.length() > 0) b.append(' ');
            String lw = w.toLowerCase(Locale.ROOT);
            if (lw.equals("i") || lw.startsWith("i'") && lw.length() <= 4) b.append('I').append(w.substring(1));
            else b.append(w);
        }
        return b.toString();
    }

    /** Pure: the first letter a capital. */
    static String sentence(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
