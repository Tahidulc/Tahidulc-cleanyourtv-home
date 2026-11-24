package com.gathin.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The scoreboard editor's Generate, offline: stylish, broadcast-style boards made from the match's two clubs, its
 * competition or a mood (a night match, retro, minimal, bold), several at once. Each starts from a designed palette and
 * a typeface chosen for it (a heavy condensed face for retro, a clean sans for minimal, a geometric one for a night
 * match), takes the clubs' colours where they fit, and is then held to the rules a board needs on a TV seen from three
 * metres (rules()): every word at least 4.5:1 on what it sits on, the score's cell standing off the panel, the clubs'
 * colour strips only where they show, the accent seen, white on added time's red. Pure and deterministic: the same
 * brief and round give the same boards, another round others.
 */
public final class BoardDesigner {
    private BoardDesigner() {
    }

    /** The moods Generate offers (CLUBS from the two clubs, COMPETITION from the competition's own colours). */
    public enum Mood {
        CLUBS("The clubs' colours"), COMPETITION("The competition"), NIGHT("Night match"), RETRO("Retro"), MINIMAL("Minimal"),
        BOLD("Bold");

        public final String label;

        Mood(String label) {
            this.label = label;
        }
    }

    /** What a board is made for: the two clubs (their colours, 0 unknown; their names), the competition, a mood or none. */
    public static final class Brief {
        public int home, away;
        public String homeName = "", awayName = "", comp = "", compSlug = "";
        /** null: one of each mood. */
        public Mood mood;

        public Brief() {
        }

        public Brief(int home, int away, String comp, Mood mood) {
            this.home = home;
            this.away = away;
            this.comp = comp == null ? "" : comp;
            this.mood = mood;
        }
    }

    /** The least contrast for words (WCAG AA), the score cell against the panel, a strip or the accent against it. */
    public static final double WORDS = 4.5, CELL = 1.3, STRIP = 1.6, ACCENT = 2.0;

    /** Up to count boards for the brief; round 0, 1, 2… gives fresh ones. */
    public static List<BoardSpec> suggest(Brief b, int count, int round) {
        Brief brief = b == null ? new Brief() : b;
        List<BoardSpec> out = new ArrayList<>();
        int n = Math.max(1, Math.min(12, count));
        for (int i = 0; out.size() < n && i < n * 3; i++) {
            Mood m = brief.mood != null ? brief.mood : Mood.values()[(i + round) % Mood.values().length];
            int variant = brief.mood != null ? i + round * 5 : round + i / Mood.values().length;
            BoardSpec s = make(brief, m, variant);
            if (!dupe(out, s)) out.add(s);
        }
        return out;
    }

    /** The mood the family's own words ask for, if they name one (offline Ask AI's guess), else null. */
    public static Mood moodOf(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        if (w.matches(".*\\b(night|floodlight|evening|midnight|dark|neon)\\b.*")) return Mood.NIGHT;
        if (w.matches(".*\\b(retro|old school|vintage|classic 90s|90s|80s|70s|nostalgic)\\b.*")) return Mood.RETRO;
        if (w.matches(".*\\b(minimal|simple|clean|subtle|quiet|plain)\\b.*")) return Mood.MINIMAL;
        if (w.matches(".*\\b(bold|loud|big|strong|punchy|vivid|bright)\\b.*")) return Mood.BOLD;
        if (w.matches(".*\\b(club|clubs|team|teams|kit|kits)\\b.*")) return Mood.CLUBS;
        if (w.matches(".*\\b(competition|league|cup|tournament)\\b.*")) return Mood.COMPETITION;
        return null;
    }

    private static boolean dupe(List<BoardSpec> l, BoardSpec s) {
        String j = s.toJson().replaceAll("\"name\":\"[^\"]*\",?", "");
        for (BoardSpec o : l) if (o.toJson().replaceAll("\"name\":\"[^\"]*\",?", "").equals(j)) return true;
        return false;
    }

    // ---- the recipes ----------------------------------------------------------------------------------------------------

    private static BoardSpec make(Brief b, Mood m, int variant) {
        BoardSpec s;
        switch (m) {
            case COMPETITION: s = competition(b, variant); break;
            case NIGHT: s = night(b, variant); break;
            case RETRO: s = retro(b, variant); break;
            case MINIMAL: s = minimal(b, variant); break;
            case BOLD: s = bold(b, variant); break;
            default: s = clubs(b, variant);
        }
        return rules(s, b);
    }

    private static int pick(int[] options, int variant) {
        return options[Math.floorMod(variant, options.length)];
    }

    /** A club's colour, or a fallback when it is not known (0) or has no real colour to give. */
    private static int club(int c, int fallback) {
        return c == 0 ? fallback : BoardColours.opaque(c);
    }

    /** The two clubs: the panel a deep neutral leaning to the home side, the score cell in the home colour, the strips. */
    private static BoardSpec clubs(Brief b, int v) {
        int home = club(b.home, 0xFFC8102E), away = club(b.away, 0xFF1B3F8F);
        BoardSpec.Base[] bases = {BoardSpec.Base.CLASSIC, BoardSpec.Base.CRESTS, BoardSpec.Base.STACKED, BoardSpec.Base.CLUB};
        BoardSpec s = BoardSpec.of(bases[Math.floorMod(v, bases.length)]);
        float[] h = BoardColours.hsv(home);
        boolean colourful = BoardColours.coloured(home);
        s.panel = colourful ? BoardColours.fromHsv(h[0], Math.min(0.55f, h[1]), 0.16f) : 0xFF14161C;
        s.panelFoot = BoardColours.value(s.panel, 0.7f);
        s.comp = BoardColours.value(s.panel, 0.6f);
        int cell = colourful ? BoardColours.fromHsv(h[0], Math.min(0.9f, h[1]), Math.max(0.42f, Math.min(0.78f, h[2])))
                : BoardColours.coloured(away) ? BoardColours.atValue(away, 0.6f) : 0xFF2A2D36;
        s.score = cell;
        s.scoreFoot = BoardColours.value(cell, 0.72f);
        s.clock = BoardColours.value(s.panel, 0.55f);
        s.text = BoardColours.INK;
        s.scoreText = BoardColours.inkFor(cell);
        s.clockText = BoardColours.INK;
        s.accent = BoardColours.coloured(away) ? BoardColours.legible(away, s.panel, ACCENT) : 0xFFF5C518;
        s.radius = pick(new int[]{12, 20, 8, 14}, v) / 100f;
        s.finish = v % 2 == 0 ? BoardSpec.Finish.GLOSS : BoardSpec.Finish.FLAT;
        s.font = v % 3 == 2 ? BoardSpec.Font.BARLOW_SEMI : BoardSpec.Font.OUTFIT;
        s.crest = BoardSpec.Crest.PLAIN;
        s.strips = s.base != BoardSpec.Base.CLUB;
        s.accentLine = v % 2 == 1;
        s.name = (b.homeName.isEmpty() ? "Club" : shortName(b.homeName)) + " colours";
        return s;
    }

    /** Known competitions' own two colours (a deep ground and a bright second), by ESPN's slug or name; else a slate and teal. */
    static int[] competitionColours(String slug, String name) {
        String k = ((slug == null ? "" : slug) + " " + (name == null ? "" : name)).toLowerCase(Locale.ROOT);
        if (k.contains("eng.1") || k.contains("premier league")) return new int[]{0xFF37003C, 0xFF00FF85};
        if (k.contains("champions")) return new int[]{0xFF0A1A3F, 0xFF4FB2FF};
        if (k.contains("europa conference") || k.contains("conference league") || k.contains("uefa.europa.conf")) return new int[]{0xFF0B2E1E, 0xFF2FE07A};
        if (k.contains("europa")) return new int[]{0xFF1A1A1A, 0xFFFF6A13};
        if (k.contains("fa cup") || k.contains("eng.fa")) return new int[]{0xFF0F1E4A, 0xFFE4002B};
        if (k.contains("esp.1") || k.contains("laliga") || k.contains("la liga")) return new int[]{0xFF101820, 0xFFFF4B44};
        if (k.contains("ita.1") || k.contains("serie a")) return new int[]{0xFF0A1E3C, 0xFF00A3E0};
        if (k.contains("ger.1") || k.contains("bundesliga")) return new int[]{0xFF141414, 0xFFD20515};
        if (k.contains("fra.1") || k.contains("ligue 1")) return new int[]{0xFF091C3E, 0xFFDAE025};
        if (k.contains("sco.1") || k.contains("scottish")) return new int[]{0xFF14213D, 0xFF8E44AD};
        if (k.contains("usa.1") || k.contains("mls")) return new int[]{0xFF0B1A2E, 0xFFE5E8EC};
        if (k.contains("eng.2") || k.contains("championship") || k.contains("efl")) return new int[]{0xFF0F2B46, 0xFF00B2A9};
        return new int[]{0xFF101826, 0xFF2EC4B6};
    }

    /** The competition's own colours: its deep ground for the panel, its bright second for the score's cell or the accent. */
    private static BoardSpec competition(Brief b, int v) {
        int[] cc = competitionColours(b.compSlug, b.comp);
        BoardSpec.Base[] bases = {BoardSpec.Base.CLASSIC, BoardSpec.Base.GLASS, BoardSpec.Base.CRESTS, BoardSpec.Base.CARD};
        BoardSpec s = BoardSpec.of(bases[Math.floorMod(v, bases.length)]);
        int deep = cc[0], bright = cc[1];
        if (s.base == BoardSpec.Base.CARD) {
            s.panel = 0xFFF2F3F6;
            s.panelFoot = 0xFFD9DDE4;
            s.score = deep;
            s.scoreFoot = BoardColours.value(deep, 0.7f);
            s.clock = s.scoreFoot;
            s.text = BoardColours.legible(deep, s.panel, WORDS);
        } else if (s.base == BoardSpec.Base.GLASS) {
            s.panel = deep;
            s.panelFoot = BoardColours.value(deep, 0.75f);
            s.score = 0x38FFFFFF;
            s.scoreFoot = 0x1CFFFFFF;
            s.clock = 0x40000000;
            s.text = BoardColours.INK;
        } else {
            s.panel = deep;
            s.panelFoot = BoardColours.value(deep, 0.72f);
            s.score = bright;
            s.scoreFoot = BoardColours.value(bright, 0.8f);
            s.clock = BoardColours.value(deep, 0.6f);
            s.text = BoardColours.INK;
        }
        s.comp = s.base == BoardSpec.Base.CARD ? s.panel : BoardColours.value(deep, 0.7f);
        s.scoreText = BoardColours.inkFor(BoardColours.over(BoardColours.mix(s.score, s.scoreFoot, 0.5f), s.panelMid()));
        s.clockText = BoardColours.INK;
        s.accent = bright;
        s.radius = pick(new int[]{10, 50, 16, 6}, v) / 100f;
        s.finish = s.base == BoardSpec.Base.GLASS ? BoardSpec.Finish.GLOSS : BoardSpec.Finish.FLAT;
        s.font = v % 2 == 0 ? BoardSpec.Font.OUTFIT : BoardSpec.Font.RUBIK;
        s.crest = s.base == BoardSpec.Base.GLASS ? BoardSpec.Crest.DISC : BoardSpec.Crest.PLAIN;
        s.accentLine = s.base != BoardSpec.Base.GLASS;
        s.strips = true;
        s.name = (b.comp.isEmpty() ? "Competition" : shortName(b.comp)) + (v % 2 == 0 ? " night" : " edition");
        return s;
    }

    /** A night match: deep indigo under the floodlights, the score lit in cyan or gold, frosted or glossy, Outfit. */
    private static BoardSpec night(Brief b, int v) {
        BoardSpec.Base[] bases = {BoardSpec.Base.FROSTED, BoardSpec.Base.CLASSIC, BoardSpec.Base.GLASS, BoardSpec.Base.CRESTS};
        BoardSpec s = BoardSpec.of(bases[Math.floorMod(v, bases.length)]);
        int[] lights = {0xFF2EC5FF, 0xFFF5C518, 0xFF7C5CFF, 0xFF3DF2B0};
        int light = pick(lights, v);
        s.panel = 0xFF0A0F24;
        s.panelFoot = 0xFF050816;
        s.comp = 0xFF060A1A;
        if (s.base == BoardSpec.Base.FROSTED || s.base == BoardSpec.Base.GLASS) {
            s.panel = 0xFF1A2342;
            s.panelFoot = 0xFF0C1226;
            s.score = 0x33FFFFFF;
            s.scoreFoot = 0x14FFFFFF;
            s.clock = 0x47000000;
            s.finish = s.base == BoardSpec.Base.FROSTED ? BoardSpec.Finish.FROSTED : BoardSpec.Finish.GLOSS;
        } else {
            s.score = light;
            s.scoreFoot = BoardColours.value(light, 0.78f);
            s.clock = 0xFF04060F;
            s.finish = BoardSpec.Finish.GLOSS;
        }
        s.text = 0xFFEEF2FF;
        s.scoreText = BoardColours.inkFor(BoardColours.over(BoardColours.mix(s.score, s.scoreFoot, 0.5f), s.panelMid()));
        s.clockText = BoardColours.legible(light, BoardColours.over(s.clock, s.panelMid()), WORDS);
        s.accent = light;
        s.radius = s.base == BoardSpec.Base.GLASS ? 0.5f : pick(new int[]{30, 14, 50, 18}, v) / 100f;
        s.font = BoardSpec.Font.OUTFIT;
        s.crest = v % 2 == 0 ? BoardSpec.Crest.RING : BoardSpec.Crest.PLAIN;
        s.accentLine = true;
        s.strips = true;
        s.name = pick(new String[]{"Floodlights", "Night match", "After dark", "Late kick-off"}, v);
        return s;
    }

    private static String pick(String[] options, int v) {
        return options[Math.floorMod(v, options.length)];
    }

    /** Retro: square corners, flat colour, a heavy condensed face, pitch green and gold, or black and orange, or cream. */
    private static BoardSpec retro(Brief b, int v) {
        int[][] palettes = {
                {0xFF12301F, 0xFFE8C547, 0xFF0B1F14, 0xFFD7192F},   // pitch green, gold
                {0xFF161616, 0xFFF28C28, 0xFF0A0A0A, 0xFFF5E6C8},   // black, orange
                {0xFFF1E7D0, 0xFF1E3A5F, 0xFFE2D5B8, 0xFFB22222},   // cream, navy
                {0xFF2B1A3D, 0xFFFFD23F, 0xFF1A1026, 0xFF3DDC97},   // purple, yellow
        };
        int[] p = palettes[Math.floorMod(v, palettes.length)];
        BoardSpec.Base[] bases = {BoardSpec.Base.CLASSIC, BoardSpec.Base.STACKED, BoardSpec.Base.CARD, BoardSpec.Base.CLASSIC};
        BoardSpec s = BoardSpec.of(bases[Math.floorMod(v, bases.length)]);
        s.panel = p[0];
        s.panelFoot = 0;
        s.comp = p[2];
        s.score = p[1];
        s.scoreFoot = p[1];
        s.clock = p[2];
        boolean light = BoardColours.luminance(p[0]) > 0.4;
        s.text = light ? BoardColours.legible(p[1], p[0], WORDS) : 0xFFF6F1E3;
        s.scoreText = BoardColours.inkFor(p[1]);
        s.clockText = light ? BoardColours.legible(p[1], p[2], WORDS) : 0xFFF6F1E3;
        s.accent = p[3];
        s.radius = v % 2 == 0 ? 0f : 0.04f;
        s.finish = BoardSpec.Finish.FLAT;
        s.font = v % 2 == 0 ? BoardSpec.Font.KHAND : BoardSpec.Font.BARLOW_HEAVY;
        s.crest = BoardSpec.Crest.PLAIN;
        s.borderWidth = v % 3 == 0 ? 0.04f : 0f;
        s.border = p[1];
        s.accentLine = v % 3 != 0;
        s.strips = true;
        s.name = pick(new String[]{"Retro pitch", "Retro orange", "Retro cream", "Retro arcade"}, v);
        return s;
    }

    /** Minimal: one quiet ground (near black, or soft white), the figures alone, a fine accent, Inter. */
    private static BoardSpec minimal(Brief b, int v) {
        boolean light = v % 3 == 1;
        BoardSpec.Base[] bases = {BoardSpec.Base.MINIMAL, BoardSpec.Base.CLASSIC, BoardSpec.Base.CRESTS};
        BoardSpec s = BoardSpec.of(bases[Math.floorMod(v, bases.length)]);
        if (light) {
            s.panel = 0xFFF4F5F7;
            s.comp = 0xFFE6E8EC;
            s.score = 0xFF111318;
            s.scoreFoot = 0xFF111318;
            s.clock = 0xFFE6E8EC;
            s.text = 0xFF111318;
            s.clockText = 0xFF2A2E38;
        } else {
            s.panel = 0xFF0C0D10;
            s.comp = 0xFF0C0D10;
            s.score = 0xFF1C1E24;
            s.scoreFoot = 0xFF16181D;
            s.clock = 0xFF0C0D10;
            s.text = 0xFFF2F3F5;
            s.clockText = 0xFFC9CDD6;
        }
        s.panelFoot = 0;
        s.scoreText = BoardColours.inkFor(s.score);
        s.accent = BoardColours.coloured(b.home) ? BoardColours.legible(BoardColours.opaque(b.home), s.panel, ACCENT) : 0xFF3D7BFF;
        s.radius = pick(new int[]{25, 12, 50}, v) / 100f;
        s.finish = BoardSpec.Finish.FLAT;
        s.font = BoardSpec.Font.INTER;
        s.crest = BoardSpec.Crest.PLAIN;
        s.strips = false;
        s.accentLine = v % 2 == 0;
        s.name = light ? "Minimal light" : pick(new String[]{"Minimal", "Quiet", "Clean"}, v);
        return s;
    }

    /** Bold: the home club's colour across the panel, a white score cell, a heavy geometric face, a border. */
    private static BoardSpec bold(Brief b, int v) {
        int home = club(b.home, 0xFFD7192F), away = club(b.away, 0xFF111111);
        BoardSpec.Base[] bases = {BoardSpec.Base.CLASSIC, BoardSpec.Base.CLUB, BoardSpec.Base.STACKED, BoardSpec.Base.CARD};
        BoardSpec s = BoardSpec.of(bases[Math.floorMod(v, bases.length)]);
        int ground = BoardColours.coloured(home) ? BoardColours.fromHsv(BoardColours.hsv(home)[0], Math.max(0.6f, BoardColours.hsv(home)[1]),
                0.5f) : 0xFF101010;
        if (s.base == BoardSpec.Base.CARD) {
            s.panel = 0xFFFFFFFF;
            s.panelFoot = 0xFFE9ECF1;
            s.score = ground;
            s.scoreFoot = BoardColours.value(ground, 0.75f);
            s.clock = s.scoreFoot;
            s.text = 0xFF111318;
        } else {
            s.panel = ground;
            s.panelFoot = BoardColours.value(ground, 0.78f);
            s.score = 0xFFFFFFFF;
            s.scoreFoot = 0xFFE6E8EE;
            s.clock = BoardColours.value(ground, 0.55f);
            s.text = 0xFFFFFFFF;
        }
        s.comp = BoardColours.value(s.base == BoardSpec.Base.CARD ? ground : s.panel, 0.65f);
        s.scoreText = BoardColours.inkFor(BoardColours.mix(s.score, s.scoreFoot, 0.5f));
        s.clockText = 0xFFFFFFFF;
        s.accent = BoardColours.coloured(away) ? away : 0xFFFFD21E;
        s.radius = pick(new int[]{8, 0, 16, 10}, v) / 100f;
        s.finish = v % 2 == 0 ? BoardSpec.Finish.GLOSS : BoardSpec.Finish.FLAT;
        s.font = v % 2 == 0 ? BoardSpec.Font.MONTSERRAT : BoardSpec.Font.BARLOW_HEAVY;
        s.crest = BoardSpec.Crest.DISC;
        s.borderWidth = 0.035f;
        s.border = 0xCCFFFFFF;
        s.strips = s.base != BoardSpec.Base.CLUB;
        s.accentLine = false;
        s.name = pick(new String[]{"Bold", "Big match", "Loud and proud", "Headline"}, v);
        return s;
    }

    private static String shortName(String n) {
        String s = n.trim();
        int sp = s.indexOf(' ');
        return sp > 0 && s.length() > 14 ? s.substring(0, sp) : s;
    }

    // ---- the rules ------------------------------------------------------------------------------------------------------

    /**
     * A board held to the rules for a TV at three metres: each word colour at least WORDS on what it sits on (moved to
     * the light or dark side when not), the score's cell standing off the panel (CELL, else deepened or lit), the
     * clubs' strips kept only where both show (STRIP; else off, the crests on discs instead), the accent seen (ACCENT),
     * white readable on added time's red and its tab. The clubs' colours are taken from the brief.
     */
    public static BoardSpec rules(BoardSpec in, Brief b) {
        BoardSpec s = in.validated();
        int ground = s.panelMid();
        // The score's cell: a solid one must stand off the panel.
        if (BoardColours.alpha(s.score) == 0xFF && BoardColours.contrast(s.score, ground) < CELL) {
            boolean lightPanel = BoardColours.luminance(ground) > 0.4;
            s.score = BoardColours.value(s.score, lightPanel ? 0.55f : 1.6f);
            if (BoardColours.contrast(s.score, ground) < CELL) s.score = lightPanel ? 0xFF111318 : 0xFFF2F3F5;
            s.scoreFoot = BoardColours.value(s.score, 0.85f);
        }
        s.text = BoardColours.legible(s.text, ground, WORDS);
        s.dim = BoardColours.legible(BoardColours.mix(s.text, ground, 0.3f), ground, WORDS);
        s.scoreText = BoardColours.legible(s.scoreText, s.scoreGround(), WORDS);
        if (s.base != BoardSpec.Base.MINIMAL) s.clockText = BoardColours.legible(s.clockText, s.clockGround(), WORDS);
        else s.clockText = BoardColours.legible(s.clockText, ground, WORDS);
        s.accent = BoardColours.legible(s.accent, ground, ACCENT);
        if (s.strips && b != null) {
            int h = club(b.home, 0xFFC8102E), a = club(b.away, 0xFF1B3F8F);
            if (BoardColours.contrast(h, ground) < STRIP || BoardColours.contrast(a, ground) < STRIP) {
                s.strips = false;
                if (s.crest == BoardSpec.Crest.PLAIN) s.crest = BoardSpec.Crest.DISC;
            }
        }
        if (BoardColours.contrast(0xFFFFFFFF, s.stoppage) < WORDS) s.stoppage = AddedTime.RED;
        if (BoardColours.contrast(0xFFFFFFFF, s.added) < WORDS) s.added = s.stoppage;
        return s.validated();
    }

    /** What rules() checks, as a list of what fails (empty: it passes), for the tests and the editor's warning. */
    public static List<String> problems(BoardSpec s, Brief b) {
        List<String> out = new ArrayList<>();
        int ground = s.panelMid();
        if (BoardColours.contrast(s.text, ground) < WORDS) out.add("names");
        if (BoardColours.contrast(s.scoreText, s.scoreGround()) < WORDS) out.add("score");
        int clockGround = s.base == BoardSpec.Base.MINIMAL ? ground : s.clockGround();
        if (BoardColours.contrast(s.clockText, clockGround) < WORDS) out.add("clock");
        if (BoardColours.alpha(s.score) == 0xFF && BoardColours.contrast(s.score, ground) < CELL) out.add("score cell");
        if (BoardColours.contrast(s.accent, ground) < ACCENT) out.add("accent");
        if (BoardColours.contrast(0xFFFFFFFF, s.stoppage) < WORDS) out.add("added time");
        if (BoardColours.contrast(0xFFFFFFFF, s.added) < WORDS) out.add("added time tab");
        if (s.strips && b != null) {
            int h = club(b.home, 0xFFC8102E), a = club(b.away, 0xFF1B3F8F);
            if (BoardColours.contrast(h, ground) < STRIP || BoardColours.contrast(a, ground) < STRIP) out.add("strips");
        }
        return out;
    }
}
