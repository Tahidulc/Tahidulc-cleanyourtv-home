package com.gathin.homefieldtv;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A club's substitution, or its double, triple or more at once, as the scoreboard's pop-out shows it (BoardDrop): a page
 * for who goes off (OFF, in red), then a page for who comes on (ON, in green), every player on it with their picture
 * and name: the whole name for one, the surname for more. Pure but for the pictures: the tests check it.
 */
final class SubsPage {
    private SubsPage() { }

    static final int OFF_COLOUR = 0xFFE5484D, ON_COLOUR = 0xFF2FBF5F;
    /** At most this many players on a page; more at once (rare) come in a second alert. */
    static final int MOST = 5;

    /** One club's change at once: who goes off and who comes on, their names as shown, their ids, their pictures. */
    static final class Subs {
        String minute = "";
        String[] off = new String[0], on = new String[0];
        /** The whole names and FotMob ids, for their pictures. */
        String[] offFull = new String[0], onFull = new String[0], offIds = new String[0], onIds = new String[0];
        Bitmap[] offFaces = new Bitmap[0], onFaces = new Bitmap[0];
        /** The club's colour, for a player with no picture (their initials on it). */
        int colour;

        /** Its pages: the OFF page (when anyone is known to go off), then the ON page. */
        int pages() {
            return off.length > 0 ? 2 : 1;
        }

        /** Whether page p is who goes off. */
        boolean offPage(int p) {
            return off.length > 0 && p == 0;
        }
    }

    /** Pure: new substitutions ({type, minute, home, on, on id, off, off id}) put together: a club's in the same minute, in the order they came. */
    static List<List<String[]>> group(List<String[]> events) {
        Map<String, List<String[]>> by = new LinkedHashMap<>();
        for (String[] e : events) {
            String k = e[2] + "|" + e[1];
            List<String[]> l = by.get(k);
            if (l == null) {
                l = new ArrayList<>();
                by.put(k, l);
            }
            l.add(e);
        }
        List<List<String[]>> out = new ArrayList<>();
        for (List<String[]> l : by.values()) {
            for (int i = 0; i < l.size(); i += MOST) out.add(new ArrayList<>(l.subList(i, Math.min(l.size(), i + MOST))));
        }
        return out;
    }

    /** Pure: the pages for one group: who goes off (those known) and who comes on, whole names for one, surnames for more. */
    static Subs of(List<String[]> group) {
        Subs s = new Subs();
        s.minute = nz(group.get(0)[1]);
        List<String[]> off = new ArrayList<>();
        for (String[] e : group) if (!nz(e[5]).isEmpty()) off.add(e);
        int n = group.size(), m = off.size();
        boolean shortOn = n > 1, shortOff = m > 1;
        s.on = new String[n];
        s.onFull = new String[n];
        s.onIds = new String[n];
        for (int i = 0; i < n; i++) {
            String[] e = group.get(i);
            s.onFull[i] = nz(e[3]);
            s.on[i] = shortOn ? surname(s.onFull[i]) : s.onFull[i];
            s.onIds[i] = nz(e[4]);
        }
        s.off = new String[m];
        s.offFull = new String[m];
        s.offIds = new String[m];
        for (int i = 0; i < m; i++) {
            String[] e = off.get(i);
            s.offFull[i] = nz(e[5]);
            s.off[i] = shortOff ? surname(s.offFull[i]) : s.offFull[i];
            s.offIds[i] = e.length > 6 ? nz(e[6]) : "";
        }
        s.onFaces = new Bitmap[n];
        s.offFaces = new Bitmap[m];
        return s;
    }

    private static final Set<String> PARTICLES = new HashSet<>(Arrays.asList(
            "van", "von", "de", "da", "di", "del", "della", "der", "den", "dos", "das", "du", "le", "la", "el", "al", "ten", "ter"));

    /** Pure: a player's surname as shown: the last word, with its van, de, da ... ("van Dijk", "De Bruyne"). */
    static String surname(String full) {
        String[] w = nz(full).trim().split("\\s+");
        if (w.length <= 1) return nz(full).trim();
        int i = w.length - 1;
        while (i > 0 && PARTICLES.contains(w[i - 1].toLowerCase(Locale.ROOT))) i--;
        StringBuilder b = new StringBuilder();
        for (int k = i; k < w.length; k++) b.append(k > i ? " " : "").append(w[k]);
        return b.toString();
    }

    /** Pure: the first name's and the last name's initials, for a player with no picture ("VD" for Virgil van Dijk). */
    static String initials(String name) {
        List<String> w = new ArrayList<>();
        for (String x : nz(name).trim().split("[\\s.\\-]+")) if (!x.isEmpty() && Character.isLetter(x.charAt(0))) w.add(x);
        if (w.isEmpty()) return "";
        String first = String.valueOf(Character.toUpperCase(w.get(0).charAt(0)));
        return w.size() == 1 ? first : first + Character.toUpperCase(w.get(w.size() - 1).charAt(0));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
