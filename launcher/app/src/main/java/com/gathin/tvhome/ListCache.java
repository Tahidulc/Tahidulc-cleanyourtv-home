package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TMDB's lists for the poster pages kept on disk (the app's own files, "lists/"), so a page opened after the launcher
 * has restarted draws its posters at once from the last reading while the list is read again behind them. One small
 * text file per list: its key on the first line, the time it was read on the second, then one title a line, its fields
 * (FIELDS of them) separated by tabs, tabs, new lines and backslashes in them escaped. Plain Java, no Android, so the
 * format is checked by the unit tests. Never sent anywhere.
 */
final class ListCache {
    /** A title's fields, in order: tv ("1" or "0"), TMDB id, name, overview, year, vote, genres, poster, backdrop. */
    static final int FIELDS = 9;

    private ListCache() {
    }

    /** A list read back: when it was read and its titles' fields. */
    static final class Entry {
        final long at;
        final List<String[]> rows;

        Entry(long at, List<String[]> rows) {
            this.at = at;
            this.rows = rows;
        }
    }

    static String encode(String key, long at, List<String[]> rows) {
        StringBuilder b = new StringBuilder();
        b.append(escape(key)).append('\n').append(at).append('\n');
        for (String[] r : rows) {
            for (int i = 0; i < FIELDS; i++) {
                if (i > 0) b.append('\t');
                b.append(escape(i < r.length && r[i] != null ? r[i] : ""));
            }
            b.append('\n');
        }
        return b.toString();
    }

    /** The list in text, or null when it is not the list with this key or is not whole. */
    static Entry decode(String key, String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length < 2 || !key.equals(unescape(lines[0]))) return null;
        long at;
        try {
            at = Long.parseLong(lines[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        List<String[]> rows = new ArrayList<>();
        for (int n = 2; n < lines.length; n++) {
            if (lines[n].isEmpty()) continue;
            String[] f = lines[n].split("\t", -1);
            if (f.length != FIELDS) return null;
            for (int i = 0; i < FIELDS; i++) f[i] = unescape(f[i]);
            rows.add(f);
        }
        return new Entry(at, rows);
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\t') b.append("\\t");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else b.append(c);
        }
        return b.toString();
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** The same titles in the same order (by kind and TMDB id): an unchanged list is not drawn again. */
    static boolean sameTitles(List<String[]> a, List<String[]> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i)[0].equals(b.get(i)[0]) || !a.get(i)[1].equals(b.get(i)[1])) return false;
        }
        return true;
    }

    /** The file a list is kept in: a name from its key (the key itself is checked on reading). */
    static String fileName(String key) {
        return "l" + Integer.toHexString(key.hashCode()) + "_" + key.length() + ".txt";
    }

    static Entry read(File dir, String key) {
        if (dir == null) return null;
        File f = new File(dir, fileName(key));
        if (!f.isFile() || f.length() > 512 * 1024) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int got = 0;
            while (got < data.length) {
                int n = in.read(data, got, data.length - got);
                if (n < 0) break;
                got += n;
            }
            return decode(key, new String(data, 0, got, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Written whole to a file beside it, then put in its place, so a list half written is never read. */
    static void write(File dir, String key, long at, List<String[]> rows) {
        if (dir == null) return;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File f = new File(dir, fileName(key)), tmp = new File(dir, fileName(key) + ".new");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(encode(key, at, rows).getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /** Lists not read again for longer than keepMs (a dated list's key changes each day) are let go. */
    static void prune(File dir, long keepMs, long now) {
        File[] all = dir == null ? null : dir.listFiles();
        if (all == null) return;
        for (File f : all) {
            //noinspection ResultOfMethodCallIgnored
            if (now - f.lastModified() > keepMs) f.delete();
        }
    }
}
