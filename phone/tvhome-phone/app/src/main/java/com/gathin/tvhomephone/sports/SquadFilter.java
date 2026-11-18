package com.gathin.tvhomephone.sports;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The club page's pills, pure for the tests: Players or Management, and which lines show (All, or any of Goalkeepers,
 * Defenders, Midfielders and Forwards, lit white when on); the players they leave in the carousel, in the squad's
 * order; which card keeps the middle when they change; and each name in the card's two parts.
 */
final class SquadFilter {
    private SquadFilter() {}

    /** The lines, as bits of the filter; none set means All. */
    static final int GOALKEEPERS = 1, DEFENDERS = 2, MIDFIELDERS = 4, FORWARDS = 8, EVERY_LINE = 15;
    /** The line pills in order: All, then each line. */
    static final int[] PILLS = {0, GOALKEEPERS, DEFENDERS, MIDFIELDERS, FORWARDS};
    static final String[] PILL_NAMES = {"All", "Goalkeepers", "Defenders", "Midfielders", "Forwards"};

    /** Pure: the filter after a pill is tapped: All clears it; a line is added or taken off (all four, or none, is All). */
    static int tap(int lines, int pill) {
        if (pill == 0) return 0;
        int next = lines == 0 ? pill : lines ^ pill;
        return next == 0 || next == EVERY_LINE ? 0 : next;
    }

    /** Pure: whether a pill is lit for the filter. */
    static boolean lit(int lines, int pill) {
        return pill == 0 ? lines == 0 : (lines & pill) != 0;
    }

    /** Pure: a line's bit (0 for the manager and anyone without a line). */
    static int bit(String line) {
        switch (line == null ? "" : line) {
            case "Goalkeepers": return GOALKEEPERS;
            case "Defenders": return DEFENDERS;
            case "Midfielders": return MIDFIELDERS;
            case "Forwards": return FORWARDS;
            default: return 0;
        }
    }

    /**
     * Pure: who the carousel shows: the manager (and any other staff) for Management; else the players, line by line as
     * the squad lists them, only the lines lit (those without a line show under All only).
     */
    static List<TeamData.Player> shown(List<TeamData.Group> squad, boolean management, int lines) {
        List<TeamData.Player> out = new ArrayList<>();
        for (TeamData.Group g : squad) {
            boolean staff = g.title.equals("Manager");
            if (staff != management) continue;
            if (!management && lines != 0 && (bit(g.title) & lines) == 0) continue;
            out.addAll(g.players);
        }
        return out;
    }

    /** Pure: whether the squad has anyone for Management. */
    static boolean hasStaff(List<TeamData.Group> squad) {
        for (TeamData.Group g : squad) if (g.title.equals("Manager") && !g.players.isEmpty()) return true;
        return false;
    }

    /**
     * Pure: the card to keep in the middle when the list changes: the same player when he is still shown; else the
     * nearest after him in the old order that is; else the first.
     */
    static int keep(List<TeamData.Player> before, int at, List<TeamData.Player> after) {
        if (after.isEmpty()) return 0;
        if (before == null || at < 0 || at >= before.size()) return 0;
        for (int i = at; i < before.size(); i++) {
            int k = indexOf(after, before.get(i));
            if (k >= 0) return k;
        }
        for (int i = at - 1; i >= 0; i--) {
            int k = indexOf(after, before.get(i));
            if (k >= 0) return k;
        }
        return 0;
    }

    /** Pure: where a player is in a list, by his name and number (the same player read again matches too). */
    static int indexOf(List<TeamData.Player> list, TeamData.Player p) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == p) return i;
        for (int i = 0; i < list.size(); i++) if (same(list.get(i), p)) return i;
        return -1;
    }

    /** Pure: a player's key between the pages: his name and number. */
    static String key(TeamData.Player p) {
        return p.name + "#" + p.number;
    }

    /** Pure: where the player with this key is; -1 when not there. */
    static int indexOf(List<TeamData.Player> list, String key) {
        for (int i = 0; i < list.size(); i++) if (key(list.get(i)).equals(key)) return i;
        return -1;
    }

    private static boolean same(TeamData.Player a, TeamData.Player b) {
        return a.name.equals(b.name) && a.number.equals(b.number);
    }

    // ---- names --------------------------------------------------------------------------------------------------------

    /** Words that start a surname ("van Dijk", "De Bruyne", "Mac Allister") rather than end a first name. */
    private static final Set<String> PARTICLES = new HashSet<>(Arrays.asList("van", "von", "de", "da", "di", "del", "della", "der",
            "den", "ter", "ten", "dos", "das", "du", "le", "la", "el", "al", "bin", "ibn", "mac", "st.", "st", "ben", "abu", "do"));

    /**
     * Pure: a name as the card sets it, {first name, surname}: ESPN's parts when they make up the name (in any order:
     * "Heung-Min" and "Son" for "Son Heung-Min"); else the last word with any particles before it as the surname
     * ("Virgil" and "van Dijk"); one word is all surname.
     */
    static String[] parts(String full, String first, String last) {
        String name = full == null ? "" : full.trim();
        if (first != null && last != null && !first.trim().isEmpty() && !last.trim().isEmpty()) {
            List<String> a = words(name), b = words(first.trim() + " " + last.trim());
            List<String> sa = new ArrayList<>(a), sb = new ArrayList<>(b);
            sa.sort(null);
            sb.sort(null);
            if (!a.isEmpty() && sa.equals(sb)) return new String[]{first.trim(), last.trim()};
        }
        String[] w = name.split("\\s+");
        if (name.isEmpty()) return new String[]{"", ""};
        if (w.length == 1) return new String[]{"", w[0]};
        int start = w.length - 1;
        while (start > 1 && PARTICLES.contains(w[start - 1].toLowerCase(Locale.ROOT))) start--;
        StringBuilder f = new StringBuilder(), l = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            StringBuilder b = i < start ? f : l;
            if (b.length() > 0) b.append(' ');
            b.append(w[i]);
        }
        return new String[]{f.toString(), l.toString()};
    }

    private static List<String> words(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String w : n.split("[\\s-]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** Pure: the photos to try for a card, best first: ESPN's head-and-shoulders cut-out, then the squad's own photo. */
    static String[] photos(TeamData.Player p) {
        List<String> out = new ArrayList<>();
        if (p.cutout != null && !p.cutout.isEmpty()) out.add(p.cutout);
        if (p.photo != null && !p.photo.isEmpty() && !out.contains(p.photo)) out.add(p.photo);
        return out.toArray(new String[0]);
    }

    /** The photo's kind, from where it is: ESPN's head and shoulders (wide), FotMob's head (square), or none. */
    static final int SHOULDERS = 0, HEAD = 1, DRAWN = 2;

    /** Pure: the kind of a photo by its address. */
    static int kind(String url) {
        if (url == null || url.isEmpty()) return DRAWN;
        return url.contains("espncdn.com") ? SHOULDERS : HEAD;
    }
}
