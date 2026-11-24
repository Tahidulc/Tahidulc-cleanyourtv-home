package com.gathin.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The logo picker's Make my own, offline: wordmarks and monograms for a channel from its name (and its group, for
 * colours that fit: sports, news, films, children's), several at once, each a LogoSpec LogoArt draws. Pure and
 * deterministic for a name and round.
 */
public final class LogoDesigner {
    private LogoDesigner() {
    }

    /** A channel's name without its playlist's tags ("UK: Sky Sports Main Event FHD" to "Sky Sports Main Event"). */
    public static String plain(String name) {
        String s = name == null ? "" : name;
        s = s.replaceAll("^\\s*[\\[(|]?\\s*[A-Za-z]{2,3}\\s*[\\])|:]+\\s*", "");
        s = s.replaceAll("(?i)\\b(fhd|uhd|hd|sd|4k|hevc|h265|raw|backup|vip|50fps|60fps)\\b", " ");
        s = s.replaceAll("[\\[(][^\\])]*[\\])]", " ");
        s = s.replaceAll("[|]+", " ").trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? (name == null ? "" : name.trim()) : s;
    }

    /** Its initials, at most three ("Sky Sports Main Event" to "SSM"; "Channel 4" to "C4"). */
    public static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : plain(name).split("\\s+")) {
            if (w.isEmpty()) continue;
            char ch = w.charAt(0);
            if (Character.isLetterOrDigit(ch)) b.append(Character.toUpperCase(ch));
            else if (w.length() > 1 && Character.isLetterOrDigit(w.charAt(1))) b.append(Character.toUpperCase(w.charAt(1)));
            if (b.length() >= 3) break;
        }
        if (b.length() == 0) return "TV";
        return b.toString();
    }

    /** The colours for a kind of channel: {ground, accent} pairs that read well with white words. */
    static int[][] palette(String group, String name) {
        String g = ((group == null ? "" : group) + " " + (name == null ? "" : name)).toLowerCase(Locale.ROOT);
        if (g.matches(".*\\b(sport|sports|football|soccer|f1|racing|golf|cricket|tennis|boxing|ufc|nfl|nba)\\b.*")) {
            return new int[][]{{0xFF0B3D2E, 0xFF3DDC84}, {0xFF0A1A3F, 0xFF2EC5FF}, {0xFF1A1A1A, 0xFFF5C518}, {0xFF3A0B14, 0xFFFF4B44}};
        }
        if (g.matches(".*\\b(news|weather|parliament|politics)\\b.*")) {
            return new int[][]{{0xFF8B0F1F, 0xFFFFFFFF}, {0xFF0B1640, 0xFFD7192F}, {0xFF1A1A1A, 0xFFE8E8E8}, {0xFF0F2B46, 0xFF4FB2FF}};
        }
        if (g.matches(".*\\b(movie|movies|film|films|cinema|premiere)\\b.*")) {
            return new int[][]{{0xFF1A1026, 0xFFF5C518}, {0xFF0C0D10, 0xFFE0B03A}, {0xFF2B1A3D, 0xFFFF6B6B}, {0xFF101826, 0xFFB98CFF}};
        }
        if (g.matches(".*\\b(kids|children|cartoon|junior|jr)\\b.*")) {
            return new int[][]{{0xFF1E5BD6, 0xFFFFD23F}, {0xFF7A1FD6, 0xFF3DDC97}, {0xFFE0407B, 0xFFFFE66D}, {0xFF0F8B8D, 0xFFFFB547}};
        }
        if (g.matches(".*\\b(music|hits|radio)\\b.*")) {
            return new int[][]{{0xFF2B0F3A, 0xFFFF4FA3}, {0xFF0C0D10, 0xFF3DF2B0}, {0xFF14213D, 0xFFFCA311}, {0xFF1A1A1A, 0xFF7C5CFF}};
        }
        return new int[][]{{0xFF0B1640, 0xFFF5C518}, {0xFF14161C, 0xFF2EC5FF}, {0xFF3A1078, 0xFFFF6B6B}, {0xFF0F2B46, 0xFF00B2A9}};
    }

    /** A second line for a channel that names a kind ("Sky Sports Main Event" keeps "MAIN EVENT" under "SKY SPORTS"). */
    static String[] split(String plain) {
        String[] w = plain.split("\\s+");
        if (w.length <= 2) return new String[]{plain, ""};
        int cut = w.length / 2;
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 0; i < w.length; i++) (i < cut ? a : b).append(i == 0 || i == cut ? "" : " ").append(w[i]);
        return new String[]{a.toString(), b.toString()};
    }

    /** Up to count logos for a channel (its name and group), round 0, 1, 2… for fresh ones. */
    public static List<LogoSpec> suggest(String name, String group, int count, int round) {
        String plain = plain(name);
        if (plain.isEmpty()) plain = "TV";
        String ini = initials(plain);
        int[][] pal = palette(group, plain);
        String[] two = split(plain);
        List<LogoSpec> out = new ArrayList<>();
        int n = Math.max(1, Math.min(12, count));
        for (int i = 0; out.size() < n && i < n * 2; i++) {
            int v = i + round * 7;
            int[] p = pal[Math.floorMod(v + round, pal.length)];
            LogoSpec s = new LogoSpec();
            switch (Math.floorMod(v, 6)) {
                case 0:
                    // The name as a wordmark on its own, a rule of the accent beside it.
                    s.text = plain.length() <= LogoSpec.TEXT_MAX ? plain.toUpperCase(Locale.ROOT) : two[0].toUpperCase(Locale.ROOT);
                    s.sub = plain.length() <= LogoSpec.TEXT_MAX ? "" : two[1].toUpperCase(Locale.ROOT);
                    s.layout = LogoSpec.Layout.WORDMARK;
                    s.ground = 0;
                    s.shape = LogoSpec.Shape.NONE;
                    s.mark = LogoSpec.Mark.PLAY;
                    s.font = BoardSpec.Font.OUTFIT;
                    break;
                case 1:
                    // A monogram in a circle.
                    s.text = ini;
                    s.layout = LogoSpec.Layout.MONOGRAM;
                    s.shape = LogoSpec.Shape.CIRCLE;
                    s.ground = p[0];
                    s.font = BoardSpec.Font.MONTSERRAT;
                    break;
                case 2:
                    // A badge: the name on a rounded plate, a star beside it.
                    s.text = two[0].toUpperCase(Locale.ROOT);
                    s.sub = two[1].toUpperCase(Locale.ROOT);
                    s.layout = LogoSpec.Layout.BADGE;
                    s.shape = LogoSpec.Shape.ROUNDED;
                    s.ground = p[0];
                    s.mark = LogoSpec.Mark.STAR;
                    s.font = BoardSpec.Font.BARLOW_HEAVY;
                    break;
                case 3:
                    // Stacked on a slant, leaning forward.
                    s.text = two[0].toUpperCase(Locale.ROOT);
                    s.sub = two[1].toUpperCase(Locale.ROOT);
                    s.layout = LogoSpec.Layout.STACKED;
                    s.shape = LogoSpec.Shape.SLANT;
                    s.ground = p[0];
                    s.italic = true;
                    s.font = BoardSpec.Font.KHAND;
                    break;
                case 4:
                    // A roundel: the initials in a ring.
                    s.text = ini;
                    s.sub = "";
                    s.layout = LogoSpec.Layout.ROUNDEL;
                    s.shape = LogoSpec.Shape.CIRCLE;
                    s.ground = 0;
                    s.mark = LogoSpec.Mark.RINGS;
                    s.font = BoardSpec.Font.RUBIK;
                    break;
                default:
                    // A pill with a bolt: quick and bold.
                    s.text = (plain.length() <= 10 ? plain : ini).toUpperCase(Locale.ROOT);
                    s.layout = LogoSpec.Layout.BADGE;
                    s.shape = LogoSpec.Shape.PILL;
                    s.ground = p[0];
                    s.mark = LogoSpec.Mark.BOLT;
                    s.side = LogoSpec.Side.LEFT;
                    s.font = BoardSpec.Font.INTER;
            }
            s.ink = 0xFFFFFFFF;
            s.accent = p[1];
            s.subInk = BoardColours.mix(0xFFFFFFFF, p[1], 0.35f);
            s.validated();
            boolean dupe = false;
            for (LogoSpec o : out) dupe |= o.toJson().equals(s.toJson());
            if (!dupe) out.add(s);
        }
        return out;
    }
}
