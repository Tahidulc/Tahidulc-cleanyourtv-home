package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The scoreboard drawn as each competition's broadcasts lay theirs out, measured against the family's pictures of them:
 * the same blocks in the same places and colours, with free typefaces close to the broadcasters' own lettering, the
 * competition's logo from the scores' feed (its emblem cut from its wordmark where the picture shows only the emblem)
 * and the clubs' colours and crests. No broadcaster's or sponsor's mark is drawn. The same on the TV's board (BoardBug)
 * and the phone's (OverlayBug), which draw everything else over it (the goal's band, VAR's scan, half time's turn, full
 * time's stamp, the minutes added in their tab). One look a competition; By competition picks the look of the game's own
 * competition, and with it its logo; any look picked by hand shows the logo of the competition that is on.
 *
 * Every look is laid out from its height: inline ones are one row; the stacked ones (LaLiga, Liga Portugal, Bundesliga,
 * Ligue 1) two; some have a tab or an emblem standing above or below the row (the competition's name, the clock, the
 * trophy, the lion). tall() is how much taller than its row a look is drawn; bar() is where the row is.
 */
public final class CompBug {
    /** The looks, in the order the pickers show them. */
    public enum Kind {
        PL("Premier League (world feed)", 2.25f), PL_US("Premier League (US)", 1.55f), FACUP("FA Cup", 3.1f), EFL("EFL", 1f),
        UCL("Champions League", 1f), UEL("Europa League", 1f), UECL("Conference League", 1f), SPFL("Scottish Premiership", 1f),
        LALIGA("LaLiga", 2f), SERIEA("Serie A", 1.63f), BUNDESLIGA("Bundesliga", 2f), LIGUE1("Ligue 1", 2f),
        EREDIVISIE("Eredivisie", 1f), LIGAPORTUGAL("Liga Portugal", 2f), MLS("MLS", 1f), LIGAMX("Liga MX", 1.95f),
        /** The Premier League's own boards of years gone by, with the logo of whatever competition is on. */
        R2004("Retro 2004", 1.9f), R2008("Retro 2008", 1.95f), R2012("Retro 2012", 1.95f), R2016("Retro 2016", 3.25f);

        public final String label;
        /** How much taller than one row it is drawn. */
        public final float tall;

        Kind(String label, float tall) {
            this.label = label;
            this.tall = tall;
        }
    }

    /** What a board shows, as both boards give it. */
    public static final class Data {
        public String home = "", away = "", score = "", clock = "", comp = "";
        /** The clubs' names (the retro boards name them: "MAN UTD", "NEWCASTLE"); empty: their codes. */
        public String homeName = "", awayName = "";
        /** Each club's colour (ARGB; their own, as on a dark ground). */
        public int homeColour = 0xFFFFFFFF, awayColour = 0xFFFFFFFF;
        public Bitmap homeCrest, awayCrest;
        /** The competition's logo for a dark ground, and in its own colours for a light one (null: as for dark). */
        public Bitmap logo, logoLight;
        /** In a half's added time: the clock red and staying red. */
        public boolean stoppage;
        /** The new score's glow after a goal (1 down to 0) and the side that scored (1 home, 2 away). */
        public float glow;
        public int glowSide;
    }

    /**
     * Pure: the look of a game's competition (ESPN's slug: "eng.1", "uefa.champions"...; else its name), or null for one
     * with no look of its own (international games, cups abroad: the board's own look then).
     */
    public static Kind forGame(String slug, String name) {
        String s = slug == null ? "" : slug.toLowerCase(Locale.ROOT).trim();
        switch (s) {
            case "eng.1": return Kind.PL;
            case "eng.fa": return Kind.FACUP;
            case "eng.2": case "eng.3": case "eng.4": case "eng.league_cup": case "eng.trophy": return Kind.EFL;
            case "uefa.champions": return Kind.UCL;
            case "uefa.europa": return Kind.UEL;
            case "uefa.europa.conf": return Kind.UECL;
            case "sco.1": case "sco.2": case "sco.cis": case "sco.tennents": return Kind.SPFL;
            case "esp.1": return Kind.LALIGA;
            case "ita.1": return Kind.SERIEA;
            case "ger.1": return Kind.BUNDESLIGA;
            case "fra.1": return Kind.LIGUE1;
            case "ned.1": return Kind.EREDIVISIE;
            case "por.1": return Kind.LIGAPORTUGAL;
            case "usa.1": return Kind.MLS;
            case "mex.1": return Kind.LIGAMX;
            default: break;
        }
        String n = name == null ? "" : " " + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        if (n.contains(" champions league ") && !n.contains(" afc ") && !n.contains(" caf ") && !n.contains(" concacaf ")) return Kind.UCL;
        if (n.contains(" conference league ")) return Kind.UECL;
        if (n.contains(" europa league ")) return Kind.UEL;
        if (n.contains(" premier league ") && !n.contains(" scottish ") && n.contains(" english ")) return Kind.PL;
        if (n.equals(" premier league ")) return Kind.PL;
        if (n.contains(" fa cup ")) return Kind.FACUP;
        if (n.contains(" championship ") && !n.contains(" scottish ") || n.contains(" league one ") || n.contains(" league two ")
                || n.contains(" carabao ") || n.contains(" efl ")) return Kind.EFL;
        if (n.contains(" scottish ") || n.contains(" spfl ")) return Kind.SPFL;
        if (n.contains(" laliga ") || n.contains(" la liga ")) return Kind.LALIGA;
        if (n.contains(" serie a ")) return Kind.SERIEA;
        if (n.contains(" bundesliga ") && !n.contains(" 2 ")) return Kind.BUNDESLIGA;
        if (n.contains(" ligue 1 ")) return Kind.LIGUE1;
        if (n.contains(" eredivisie ")) return Kind.EREDIVISIE;
        if (n.contains(" liga portugal ") || n.contains(" primeira liga ")) return Kind.LIGAPORTUGAL;
        if (n.contains(" mls ") || n.contains(" major league soccer ")) return Kind.MLS;
        if (n.contains(" liga mx ")) return Kind.LIGAMX;
        return null;
    }

    /** Pure: the two scores in a board's score ("2–1", "0 - 0"); null when it has not two (before kick-off: its time). */
    public static String[] scores(String score) {
        // A time (before kick-off: "19:45") is no score.
        if (score == null || score.indexOf(':') >= 0) return null;
        Matcher m = DIGITS.matcher(score);
        String a = m.find() ? m.group() : null, b = a != null && m.find() ? m.group() : null;
        return a == null || b == null ? null : new String[]{a, b};
    }

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /**
     * Pure: what folds out of the board in each look (its alerts, the stats): {ground, ink, quieter ink, a chip's block}.
     */
    public static int[] strip(Kind k) {
        switch (k) {
            case UCL: return new int[]{0xFF0B1A5E, INK, 0xFFB7C2EE, 0xFF2E5BFF};
            case UEL: return new int[]{0xFF141517, INK, 0xFFB9BCC2, 0xFFFF6A13};
            case UECL: return new int[]{0xFF141517, INK, 0xFFB9BCC2, 0xFF00BE4C};
            case SERIEA: return new int[]{0xFF0C1F4F, INK, 0xFFB5C3E6, 0xFF1F8FFF};
            case SPFL: return new int[]{0xFF0B1B26, INK, 0xFFB0C0C8, 0xFF2E9E5B};
            case EFL: return new int[]{0xFF12163A, INK, 0xFFB4B9D6, 0xFFD8262E};
            case MLS: return new int[]{0xFF22262D, INK, 0xFFB8BEC8, 0xFF3D8BFF};
            case PL_US: return new int[]{0xFF1B1C21, INK, 0xFFB9BCC4, 0xFF4B2A7B};
            case PL: return new int[]{0xFF38003C, INK, 0xFFD9B8E0, 0xFF00C46A};
            case FACUP: return new int[]{0xFF0F1630, INK, 0xFFB3B9D2, 0xFFC8102E};
            case EREDIVISIE: return new int[]{0xFF1C1F25, INK, 0xFFB9BDC5, 0xFFE5512D};
            case LALIGA: return new int[]{0xFF111111, INK, 0xFFBDBDBD, 0xFFFF4B44};
            case LIGAPORTUGAL: return new int[]{0xFF0A1E4D, INK, 0xFFB3C0E0, 0xFFE30613};
            case LIGAMX: return new int[]{0xFF16181D, INK, 0xFFB9BCC4, 0xFF1E8C4E};
            case BUNDESLIGA: return new int[]{0xFF1A1A1A, INK, 0xFFBDBDBD, 0xFFD20515};
            case R2004: return new int[]{0xFF14244F, INK, 0xFFB8C2D6, 0xFF9AA6BC};
            case R2008: return new int[]{0xFF0B2A6F, INK, 0xFFB5C2E2, 0xFFD21F2B};
            case R2012: return new int[]{0xFF1E4FB0, INK, 0xFFC4D2F0, 0xFFD21F2B};
            case R2016: return new int[]{0xFF38003C, INK, 0xFFD9B8E0, 0xFF00C46A};
            case LIGUE1: default: return new int[]{0xFF121418, INK, 0xFFB9BDC5, 0xFF2654D8};
        }
    }

    /**
     * Pure: a club's name as the retro boards set it: in capitals, short ("Manchester United" MAN UTD, "Newcastle United"
     * NEWCASTLE, "Wolverhampton Wanderers" WOLVES), its code when there is no name or it stays too long.
     */
    public static String shortName(String name, String code) {
        String c = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (name == null || name.trim().isEmpty()) return c;
        String n = name.trim().replaceAll("(?i)^(afc|fc|cf|ac|ssc|as|sc)\\s+", "").replaceAll("(?i)\\s+(fc|afc|cf)$", "");
        String low = n.toLowerCase(Locale.ROOT);
        if (low.startsWith("manchester united") || low.equals("man united") || low.equals("man utd")) return "MAN UTD";
        if (low.startsWith("manchester city") || low.equals("man city")) return "MAN CITY";
        if (low.startsWith("wolverhampton")) return "WOLVES";
        if (low.startsWith("tottenham")) return "SPURS";
        if (low.startsWith("brighton")) return "BRIGHTON";
        if (low.startsWith("nottingham forest") || low.equals("nott'm forest")) return "NOTT'M FOREST";
        if (low.startsWith("sheffield united")) return "SHEFF UTD";
        if (low.startsWith("sheffield wednesday")) return "SHEFF WED";
        if (low.startsWith("west bromwich")) return "WEST BROM";
        if (low.startsWith("queens park rangers")) return "QPR";
        if (low.startsWith("paris saint")) return "PARIS SG";
        if (low.startsWith("bayern")) return "BAYERN";
        if (low.startsWith("borussia dortmund")) return "DORTMUND";
        if (low.startsWith("atletico madrid") || low.startsWith("atlético madrid")) return "ATLÉTICO";
        String t = n.replaceAll("(?i)\\s+(united|city|town|rovers|albion|wanderers|hotspur|athletic|county)$", "");
        String u = t.toUpperCase(Locale.ROOT);
        return u.length() <= 13 ? u : (c.isEmpty() ? u.substring(0, 13).trim() : c);
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    private static final int INK = 0xFFF5F6FA, DARK = 0xFF111418, RED = 0xFFD7192F;
    /** Where the row sits in the looks with something standing over it, in rows from the top. */
    private static final float PL_TOP = 0.4f, FA_TOP = 1.6f, SA_TOP = 0.17f, MX_TOP = 0.88f, R8_TOP = 0.08f, R16_TOP = 1.5f;
    private static Typeface barlow, khand, outfit, mont5, mont6, mont7, mont8, bcond8, bsemi7, rubik6, inter5, inter6;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG),
            img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF(), r2 = new RectF();
    private final Rect bounds = new Rect();
    private final Path path = new Path();
    private final PorterDuffColorFilter white = new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN),
            black = new PorterDuffColorFilter(0xFF151515, PorterDuff.Mode.SRC_IN),
            shadow = new PorterDuffColorFilter(0x59000000, PorterDuff.Mode.SRC_IN);

    public CompBug(Context c) {
        synchronized (CompBug.class) {
            if (barlow == null) barlow = font(c, "broadcast/fonts/BarlowCondensed-700.ttf", Typeface.create("sans-serif-condensed", Typeface.BOLD));
            if (khand == null) khand = font(c, "broadcast/fonts/Khand-700.ttf", barlow);
            if (outfit == null) outfit = font(c, "broadcast/fonts/outfit.ttf", Typeface.DEFAULT_BOLD);
            if (mont5 == null) mont5 = font(c, "broadcast/fonts/Montserrat-500.ttf", Typeface.DEFAULT);
            if (mont6 == null) mont6 = font(c, "broadcast/fonts/Montserrat-600.ttf", Typeface.DEFAULT_BOLD);
            if (mont7 == null) mont7 = font(c, "broadcast/fonts/Montserrat-700.ttf", Typeface.DEFAULT_BOLD);
            if (mont8 == null) mont8 = font(c, "broadcast/fonts/Montserrat-800.ttf", Typeface.DEFAULT_BOLD);
            if (bcond8 == null) bcond8 = font(c, "broadcast/fonts/BarlowCondensed-800.ttf", barlow);
            if (bsemi7 == null) bsemi7 = font(c, "broadcast/fonts/BarlowSemiCondensed-700.ttf", barlow);
            if (rubik6 == null) rubik6 = font(c, "broadcast/fonts/Rubik-600.ttf", Typeface.DEFAULT_BOLD);
            if (inter5 == null) inter5 = font(c, "broadcast/fonts/Inter-500.ttf", Typeface.DEFAULT);
            if (inter6 == null) inter6 = font(c, "broadcast/fonts/Inter-600.ttf", Typeface.DEFAULT_BOLD);
        }
        text.setFontFeatureSettings("tnum");
    }

    private static Typeface font(Context c, String path, Typeface or) {
        try {
            return Typeface.createFromAsset(c.getAssets(), path);
        } catch (RuntimeException e) {
            return or;
        }
    }

    /** Its height in rows, for a look. */
    public static float tall(Kind k) {
        return k == null ? 1f : k.tall;
    }

    /** One row's height for a look drawn h high. */
    private static float row(Kind k, float h) {
        return h / tall(k);
    }

    /** Its width for a height (h: the whole look's), as its words need. */
    public float natural(Kind k, Data d, float h) {
        float[] s = segs(k, d, row(k, h));
        float w = 0;
        for (float v : s) w += v;
        return w;
    }

    /** The width of its first block (the board opens out of it). */
    public float first(Kind k, Data d, float h) {
        float[] s = segs(k, d, row(k, h));
        return s.length == 0 ? h : s[0];
    }

    /** Its main row in a board drawn h high ({top, bottom}): the goal's band and VAR's scan run along it. */
    public static float[] bar(Kind k, float h) {
        float u = row(k, h);
        switch (k) {
            case PL: return new float[]{u * PL_TOP, u * (PL_TOP + 1f)};
            case PL_US: return new float[]{h - u, h};
            case FACUP: return new float[]{u * FA_TOP, u * (FA_TOP + 1f)};
            case SERIEA: return new float[]{u * SA_TOP, u * (SA_TOP + 1f)};
            case LIGAMX: return new float[]{u * MX_TOP, u * (MX_TOP + 1f)};
            case R2004: return new float[]{u * 0.2f, u * 1.22f};
            case R2008: case R2012: return new float[]{u * R8_TOP, u * (R8_TOP + 1f)};
            case R2016: return new float[]{u * R16_TOP, u * (R16_TOP + 1f)};
            default: return new float[]{0, h};
        }
    }

    /**
     * Pure: a competition's name as a board's tab or title shows it, in capitals, without the country in front of it
     * ("English Premier League" PREMIER LEAGUE, "Mexican Liga BBVA MX" LIGA MX); or as given when it is none of the
     * looks' own.
     */
    public static String title(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String low = " " + name.toLowerCase(Locale.ROOT) + " ";
        if (low.contains(" liga ") && low.contains(" mx ")) return "LIGA MX";
        Kind k = forGame(null, name);
        if (k != null) {
            switch (k) {
                case PL: return "PREMIER LEAGUE";
                case UCL: return "CHAMPIONS LEAGUE";
                case UEL: return "EUROPA LEAGUE";
                case UECL: return "CONFERENCE LEAGUE";
                case FACUP: return "FA CUP";
                case LALIGA: return "LALIGA";
                case SERIEA: return "SERIE A";
                case BUNDESLIGA: return "BUNDESLIGA";
                case LIGUE1: return "LIGUE 1";
                case EREDIVISIE: return "EREDIVISIE";
                case LIGAPORTUGAL: return "LIGA PORTUGAL";
                case MLS: return "MLS";
                case LIGAMX: return "LIGA MX";
                default: break;
            }
        }
        String t = name.trim().replaceAll("(?i)^(english|spanish|italian|german|french|dutch|portuguese|mexican|scottish|american|"
                + "brazilian|argentine|belgian|turkish|saudi|uefa|fifa)\\s+", "");
        return t.toUpperCase(Locale.ROOT);
    }

    /** Liga MX's title: its year and half ("2026 LIGA MX APERTURA") when it is Liga MX that is on, else the competition's. */
    private static String mxTitle(Data d) {
        String t = title(d, "LIGA MX");
        if (!"LIGA MX".equals(t)) return t;
        java.util.Calendar now = java.util.Calendar.getInstance();
        return mxSeason(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1);
    }

    /** Pure: Liga MX's season in a month: Apertura from July, Clausura to June. */
    static String mxSeason(int year, int month) {
        return year + " LIGA MX " + (month >= 7 ? "APERTURA" : "CLAUSURA");
    }

    private static String title(Data d, String or) {
        String t = title(d.comp);
        return t.isEmpty() ? or : t;
    }

    /** The clock's width is kept as the longest it shows (so the board does not jump as it ticks). */
    private static String clockSample(Data d) {
        return d.clock == null || d.clock.length() <= 5 ? "88:88" : d.clock;
    }

    /** The blocks along a look, in pixels, for a row u high: the same sums for its width and its drawing. */
    private float[] segs(Kind k, Data d, float u) {
        String c = clockSample(d);
        boolean crests = d.homeCrest != null || d.awayCrest != null;
        switch (k) {
            case UCL: {
                float code = codes(d, u * 0.661f, mont5) + u * 1.0f;
                return new float[]{u, Math.max(u * 2.6f, tw(c, u * 0.661f, mont5) + u * 1.35f), u * 0.16f, u * 0.26f, code, u * 1.15f,
                        u * 1.15f, code, u * 0.06f, u * 0.26f};
            }
            case UEL: case UECL: {
                float code = codes(d, u * 0.672f, mont7) + u * 1.1f;
                return new float[]{u * 1.05f, u * 0.14f, Math.max(u * 2.6f, tw(c, u * 0.672f, mont7) + u * 1.2f), code, u * 1.45f, u * 0.07f,
                        u * 1.45f, code};
            }
            case SERIEA: {
                float code = codes(d, u * 0.642f, mont6) + u * 1.0f;
                return new float[]{u * 1.35f, Math.max(u * 2.4f, tw(c, u * 0.642f, mont6) + u * 0.85f), code,
                        Math.max(u * 1.7f, tw(scoreSample(d), u * 0.642f, mont6) + u * 0.8f), code, u * 0.34f};
            }
            case SPFL: {
                float code = Math.max(u * 2.3f, codes(d, u * 0.763f, inter6) + u * 1.0f);
                return new float[]{u * 1.65f, code, u * 2.3f, code, Math.max(u * 2.6f, tw(c, u * 0.713f, inter6) + u * 0.8f)};
            }
            case EFL: {
                float code = Math.max(u * 3f, codes(d, u * 0.732f, rubik6) + u * 1.6f);
                return new float[]{code, Math.max(u * 3f, tw(scoreSample(d), u * 0.756f, rubik6) + u * 1.6f), code,
                        Math.max(u * 3f, tw(c, u * 0.683f, rubik6) + u * 1.4f)};
            }
            case MLS: {
                float code = codes(d, u * 0.725f, inter5) + u * 1.2f;
                return new float[]{Math.max(u * 2.6f, tw(c, u * 0.7f, inter5) + u * 1.0f), u * 1.05f, code, u * 0.95f, u * 0.95f, code, u};
            }
            case PL_US: {
                float code = Math.max(u * 3f, (crests ? u : 0f) + codes(d, u * 0.775f, mont6) + u * 1.0f);
                return new float[]{code, u * 3f, code, Math.max(u * 2.9f, tw(c, u * 0.725f, mont5) + u * 1.3f)};
            }
            case PL: {
                float code = Math.max(u * 3f, codes(d, u * 0.88f, mont8) + u * 1.3f);
                return new float[]{code, u * 0.45f, u * 1.05f, u * 1.3f, u * 1.05f, u * 0.45f, code};
            }
            case FACUP: {
                float code = Math.max(u * 2f, codes(d, u * 0.744f, mont7) + u * 0.9f);
                return new float[]{u * 0.16f, code, u * 1.05f, Math.max(u * 3.1f, tw(c, u * 0.72f, mont7) + u * 1.3f), u * 1.05f, code, u * 0.16f};
            }
            case EREDIVISIE: {
                float code = codes(d, u * 0.762f, inter6);
                return new float[]{Math.max(u * 3.2f, code + u * 1.9f), u * 1.35f, u * 1.3f, u * 0.32f, Math.max(u * 2.7f, code + u * 1.6f),
                        u * 0.32f, Math.max(u * 2.9f, tw(c, u * 0.762f, inter6) + u * 1.2f)};
            }
            case LIGAMX: {
                float block = Math.max(u * 4.6f, (crests ? u * 0.95f : 0f) + codes(d, u * 0.717f, mont5) + u * 2.2f);
                return new float[]{u * 0.15f, logoSlot(emblem(d.logo, E_MARK_LEFT), u), block, u * 0.12f, block, Math.max(u * 2.3f, tw(c, u * 0.768f, mont7) + u * 0.9f),
                        u * 0.15f};
            }
            case LALIGA: return new float[]{Math.max(u * 1.5f, tw(c, u * 0.577f, bcond8) + u * 0.5f),
                    Math.max(u, codes(d, u * 0.521f, bcond8) + u * 0.45f), u * 0.13f, u * 1.1f};
            case LIGAPORTUGAL: return new float[]{u * 1.55f, Math.max(u * 1.55f, codes(d, u * 0.34f, mont8) + u * 0.6f), u * 0.8f, u * 0.16f};
            case BUNDESLIGA: {
                float col = Math.max(u * 1.6f, codes(d, u * 0.42f, bsemi7) + u * 0.5f);
                return new float[]{Math.max(u * 1.65f, tw(c, u * 0.42f, bsemi7) + u * 0.5f), col, col};
            }
            case R2004: return new float[]{u * 1.28f, Math.max(u * 2f, names(d, u * 0.45f, bsemi7) + u * 0.55f), u * 0.62f};
            case R2008: case R2012: {
                float name = names(d, u * 0.775f, bsemi7);
                return new float[]{u * 2.75f, name + u * 0.6f, u * 0.7f, Math.max(u * 1.9f, tw(scoreSample(d), u * 0.868f, bsemi7) + u),
                        u * 0.55f, name + u * 0.6f, u * 0.45f};
            }
            case R2016: {
                float code = Math.max(u * 2.3f, codes(d, u * 0.78f, rubik6) + u * 1.2f);
                return new float[]{u * 0.14f, code, u * 2.35f, code, u * 0.14f};
            }
            case LIGUE1: default: return new float[]{Math.max(u * 1.4f, tw(c, u * 0.42f, mont7) + u * 0.45f),
                    Math.max(u * 2.2f, codes(d, u * 0.46f, mont7) + u * 1.3f), u * 0.05f, u * 0.1f};
        }
    }

    private static String scoreSample(Data d) {
        String[] sc = scores(d.score);
        return sc == null ? "0-0" : sc[0] + "-" + sc[1];
    }

    /** The wider of the two clubs' codes. */
    private float codes(Data d, float size, Typeface tf) {
        return Math.max(tw(d.home, size, tf), tw(d.away, size, tf));
    }

    /** The wider of the two clubs' short names (the retro boards). */
    private float names(Data d, float size, Typeface tf) {
        return Math.max(tw(shortName(d.homeName, d.home), size, tf), tw(shortName(d.awayName, d.away), size, tf));
    }

    /** Room for the competition's logo in a row u high, as wide as its shape needs (Liga MX's slot). */
    private static float logoSlot(Bitmap b, float u) {
        if (b == null || b.isRecycled() || b.getHeight() == 0) return u * 0.9f;
        float aspect = b.getWidth() / (float) b.getHeight();
        return Math.max(u * 0.9f, Math.min(u * 2.2f, u * 0.62f * aspect + u * 0.3f));
    }

    private float tw(String s, float size, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        if (tf == outfit) text.setFontVariationSettings("'wght' 760");
        float w = text.measureText(s == null ? "" : s);
        if (tf == outfit) text.setFontVariationSettings(null);
        return w;
    }

    /** The look drawn into w by h (its own grounds: the board draws none under it). */
    public void draw(Canvas cv, Kind k, Data d, float w, float h) {
        if (k == null || w <= 0 || h <= 0) return;
        float u = row(k, h);
        float[] s = segs(k, d, u);
        float sum = 0;
        for (float v : s) sum += v;
        float kx = sum > 0 ? w / sum : 1f;
        float[] x = new float[s.length + 1];
        for (int i = 0; i < s.length; i++) x[i + 1] = x[i] + s[i] * kx;
        String[] sc = scores(d.score);
        switch (k) {
            case UCL: ucl(cv, d, x, u, sc); break;
            case UEL: case UECL: uefa(cv, d, x, u, sc, k == Kind.UEL ? 0xFFF26A1B : 0xFF27B34B); break;
            case SERIEA: serieA(cv, d, x, u, h, sc); break;
            case SPFL: spfl(cv, d, x, u, sc); break;
            case EFL: efl(cv, d, x, u, sc); break;
            case MLS: mls(cv, d, x, u, sc); break;
            case PL_US: plUs(cv, d, x, w, h, u, sc); break;
            case PL: pl(cv, d, x, u, sc); break;
            case FACUP: faCup(cv, d, x, u, sc); break;
            case EREDIVISIE: eredivisie(cv, d, x, u, sc); break;
            case LIGAMX: ligaMx(cv, d, x, h, u, sc); break;
            case LALIGA: laLiga(cv, d, x, h, sc); break;
            case LIGAPORTUGAL: ligaPortugal(cv, d, x, h, sc); break;
            case BUNDESLIGA: bundesliga(cv, d, x, h, sc); break;
            case R2004: r2004(cv, d, x, u, h, sc); break;
            case R2008: r2008(cv, d, x, u, sc, false); break;
            case R2012: r2008(cv, d, x, u, sc, true); break;
            case R2016: r2016(cv, d, x, u, sc); break;
            case LIGUE1: default: ligue1(cv, d, x, h, sc); break;
        }
    }

    // ---- the looks ------------------------------------------------------------------------------------------------

    /** Champions League: the starball on bright blue, the clock on white, the clubs and score on the deep blue bar. */
    private void ucl(Canvas cv, Data d, float[] x, float u, String[] sc) {
        vgrad(cv, x[0], 0, x[1], u, 0xFF3448EC, 0xFF2133CF);
        logoIn(cv, emblem(d.logo, E_MARK), x[0], 0, x[1], u, u * 0.17f, white);
        vgrad(cv, x[1], 0, x[2], u, 0xFFFFFFFF, 0xFFE7EAF3);
        clock(cv, d, x[1], 0, x[2], u, 0, 0xFF0D1854, u * 0.661f, mont5);
        rect(cv, x[3], 0, x[4], u, solid(d.homeColour));
        fill.setShader(new LinearGradient(x[4], 0, x[8], 0, new int[]{0xFF141B8C, 0xFF2A31C0, 0xFF141B8C}, null, Shader.TileMode.CLAMP));
        cv.drawRect(x[4], 0, x[8], u, fill);
        fill.setShader(null);
        words(cv, d.home, (x[4] + x[5]) / 2f, u / 2f, u * 0.661f, x[5] - x[4] - u * 0.3f, INK, mont5, 0);
        chevron(cv, x[5], u, 1, 0x40FFFFFF);
        chevron(cv, x[7], u, -1, 0x40FFFFFF);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[5] + x[6]) / 2f, u / 2f, u * 0.661f, INK, mont5);
            scoreOne(cv, d, sc[1], 2, (x[6] + x[7]) / 2f, u / 2f, u * 0.661f, INK, mont5);
        } else {
            words(cv, d.score, (x[5] + x[7]) / 2f, u / 2f, u * 0.566f, x[7] - x[5], INK, mont5, 0);
        }
        words(cv, d.away, (x[7] + x[8]) / 2f, u / 2f, u * 0.661f, x[8] - x[7] - u * 0.3f, INK, mont5, 0);
        rect(cv, x[9], 0, x[10], u, solid(d.awayColour));
    }

    /**
     * Europa and Conference League: the trophy in its ring standing free, the clock on black over the competition's line,
     * the clubs on dark grey, the scores in white boxes edged in the clubs' colours.
     */
    private void uefa(Canvas cv, Data d, float[] x, float u, String[] sc, int accent) {
        logoIn(cv, emblem(d.logo, E_MARK), x[0], 0, x[1], u, u * 0.03f, null);
        float right = x[x.length - 1], rad = u * 0.07f;
        block(cv, x[2], 0, x[3], u, 0xF20B0B0B, rad, true, false);
        block(cv, x[3], 0, x[4], u, 0xF2262626, rad, false, false);
        block(cv, x[5], 0, x[6], u, 0xF21A1A1A, rad, false, false);
        block(cv, x[7], 0, right, u, 0xF2262626, rad, false, true);
        rect(cv, x[2] + rad * 0.3f, u * 0.925f, x[3], u, d.stoppage ? RED : accent);
        clock(cv, d, x[2], 0, x[3], u * 0.93f, 0, INK, u * 0.672f, mont7);
        words(cv, d.home, (x[3] + x[4]) / 2f, u / 2f, u * 0.672f, x[4] - x[3] - u * 0.3f, INK, mont7, 0);
        words(cv, d.away, (x[7] + right) / 2f, u / 2f, u * 0.672f, right - x[7] - u * 0.3f, INK, mont7, 0);
        float edge = u * 0.12f;
        vgrad(cv, x[4], 0, x[5], u, 0xFFF7F7F7, 0xFFE4E4E4);
        vgrad(cv, x[6], 0, x[7], u, 0xFFF7F7F7, 0xFFE4E4E4);
        rect(cv, x[4], 0, x[4] + edge, u, solid(d.homeColour));
        rect(cv, x[7] - edge, 0, x[7], u, solid(d.awayColour));
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[4] + edge + x[5]) / 2f, u / 2f, u * 0.72f, DARK, mont7);
            scoreOne(cv, d, sc[1], 2, (x[6] + x[7] - edge) / 2f, u / 2f, u * 0.72f, DARK, mont7);
        } else {
            words(cv, d.score, (x[4] + x[7]) / 2f, u / 2f, u * 0.528f, x[7] - x[4], DARK, mont7, 0);
        }
    }

    /**
     * Serie A: the logo large, edged in white, standing over the row's start; the clock on white, then the clubs on navy
     * and the score on white, each a slanted block.
     */
    private void serieA(Canvas cv, Data d, float[] x, float u, float h, String[] sc) {
        float bt = u * SA_TOP, bb = bt + u, cy = (bt + bb) / 2f, s = u * 0.3f;
        vgrad(cv, x[0] + u * 0.6f, bt, x[4] + s, bb, 0xFFFFFFFF, 0xFFE4E9F1);
        para(cv, x[2], bt, x[3], bb, s, 0xFF15348A, 0xFF0A1E59);
        para(cv, x[4], bt, x[5], bb, s, 0xFF15348A, 0xFF0A1E59);
        clock(cv, d, x[1], bt, x[2] + s * 0.5f, bb, 0, 0xFF0B2A6C, u * 0.642f, mont6);
        words(cv, d.home, (x[2] + x[3] + s) / 2f, cy, u * 0.642f, x[3] - x[2] - u * 0.3f, INK, mont6, 0);
        float mid = (x[3] + x[4] + s) / 2f;
        if (sc != null) {
            float half = tw(sc[0] + "-", u * 0.642f, mont6) * 0.5f + u * 0.06f;
            scoreOne(cv, d, sc[0], 1, mid - half, cy, u * 0.642f, 0xFF0B2A6C, mont6);
            words(cv, "-", mid, cy, u * 0.642f, 0, 0xFF0B2A6C, mont6, 0);
            scoreOne(cv, d, sc[1], 2, mid + half, cy, u * 0.642f, 0xFF0B2A6C, mont6);
        } else {
            words(cv, d.score, mid, cy, u * 0.535f, x[4] - x[3], 0xFF0B2A6C, mont6, 0);
        }
        words(cv, d.away, (x[4] + x[5] + s) / 2f, cy, u * 0.642f, x[5] - x[4] - u * 0.3f, INK, mont6, 0);
        Bitmap mark = emblem(d.logoLight != null ? d.logoLight : d.logo, E_COLOUR_MARK);
        outlined(cv, mark, x[0], 0, x[1] + u * 0.05f, h, u * 0.04f, u * 0.055f);
    }

    /** Scottish Premiership: the logo on a pale block, the clubs on their colours, the score and clock on dark. */
    private void spfl(Canvas cv, Data d, float[] x, float u, String[] sc) {
        rect(cv, x[0], 0, x[1], u, 0xF2ECEDEE);
        logoIn(cv, d.logoLight != null ? d.logoLight : d.logo, x[0], 0, x[1], u, u * 0.1f, null);
        team(cv, d.home, x[1], 0, x[2], u, solid(d.homeColour), u * 0.62f, inter6);
        rect(cv, x[2], 0, x[3], u, 0xEB1A1F1C);
        score(cv, d, sc, x[2], x[3], 0, u, u * 0.62f, INK, inter6);
        team(cv, d.away, x[3], 0, x[4], u, solid(d.awayColour), u * 0.62f, inter6);
        rect(cv, x[4], 0, x[5], u, 0xEB151917);
        clock(cv, d, x[4], 0, x[5], u, 0, INK, u * 0.713f, inter6);
    }

    /** EFL: four even blocks, no logo: the home club on its colour, the score on white, the away club, the clock on white. */
    private void efl(Canvas cv, Data d, float[] x, float u, String[] sc) {
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        rect(cv, x[0], 0, x[1], u, hc);
        words(cv, d.home, (x[0] + x[1]) / 2f, u / 2f, u * 0.732f, x[1] - x[0] - u * 0.4f, ink(hc), rubik6, 0);
        rect(cv, x[1], 0, x[2], u, 0xFFFFFFFF);
        float mid = (x[1] + x[2]) / 2f;
        if (sc != null) {
            float half = tw(sc[0], u * 0.756f, rubik6) * 0.5f + u * 0.42f;
            scoreOne(cv, d, sc[0], 1, mid - half, u / 2f, u * 0.756f, 0xFF141B5E, rubik6);
            words(cv, "-", mid, u / 2f, u * 0.756f, 0, 0xFF141B5E, rubik6, 0);
            scoreOne(cv, d, sc[1], 2, mid + half, u / 2f, u * 0.756f, 0xFF141B5E, rubik6);
        } else {
            words(cv, d.score, mid, u / 2f, u * 0.61f, x[2] - x[1], 0xFF141B5E, rubik6, 0);
        }
        rect(cv, x[2], 0, x[3], u, ac);
        words(cv, d.away, (x[2] + x[3]) / 2f, u / 2f, u * 0.732f, x[3] - x[2] - u * 0.4f, ink(ac), rubik6, 0);
        rect(cv, x[3], 0, x[4], u, 0xFFFFFFFF);
        clock(cv, d, x[3], 0, x[4], u, 0, 0xFF161616, u * 0.683f, rubik6);
    }

    /**
     * MLS: the clock on charcoal, then one pale bar between the clubs' colours: each club's code with its score beside
     * the line between them. No logo.
     */
    private void mls(Canvas cv, Data d, float[] x, float u, String[] sc) {
        rect(cv, x[0], 0, x[1], u, 0xF22B2D31);
        clock(cv, d, x[0], 0, x[1], u, 0, INK, u * 0.7f, inter5);
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        vgrad(cv, x[1], 0, x[2], u, hc, darker(hc, 0.1f));
        vgrad(cv, x[2], 0, x[6], u, 0xFFF8F8F8, 0xFFD9DADC);
        vgrad(cv, x[6], 0, x[7], u, ac, darker(ac, 0.1f));
        words(cv, d.home, (x[2] + x[3]) / 2f, u / 2f, u * 0.725f, x[3] - x[2] - u * 0.3f, 0xFF141414, inter5, 0);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[3] + x[4]) / 2f, u / 2f, u * 0.75f, 0xFF141414, inter5);
            scoreOne(cv, d, sc[1], 2, (x[4] + x[5]) / 2f, u / 2f, u * 0.75f, 0xFF141414, inter5);
        } else {
            words(cv, d.score, x[4], u / 2f, u * 0.6f, x[5] - x[3], 0xFF141414, inter5, 0);
        }
        rect(cv, x[4] - u * 0.012f, u * 0.1f, x[4] + u * 0.012f, u * 0.9f, 0xFFA2A4A8);
        words(cv, d.away, (x[5] + x[6]) / 2f, u / 2f, u * 0.725f, x[6] - x[5] - u * 0.3f, 0xFF141414, inter5, 0);
    }

    /**
     * Premier League, US style: the competition's name in a black tab over the row; each club's crest and code on its
     * colour, the score on both clubs' colours darkened, the clock on light grey.
     */
    private void plUs(Canvas cv, Data d, float[] x, float w, float h, float u, String[] sc) {
        float top = h - u, tab = u * 0.47f, cy = top + u / 2f;
        String name = title(d, "PREMIER LEAGUE");
        float ts = u * 0.38f, tabW = Math.min(w, tw(name, ts, mont6) + u * 0.5f);
        rect(cv, 0, 0, tabW, tab, 0xF0121212);
        words(cv, name, u * 0.25f, tab / 2f, ts, tabW - u * 0.4f, INK, mont6, -1);
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        boolean crests = d.homeCrest != null || d.awayCrest != null;
        vgrad(cv, x[0], top, x[1], h, hc, darker(hc, 0.12f));
        float hx = x[0];
        if (crests) {
            crest(cv, d.homeCrest, x[0] + u * 0.06f, top + u * 0.06f, u * 0.88f);
            hx += u;
        }
        words(cv, d.home, (hx + x[1]) / 2f, cy, u * 0.775f, x[1] - hx - u * 0.2f, ink(hc), mont6, 0);
        float mid = (x[1] + x[2]) / 2f;
        vgrad(cv, x[1], top, mid, h, darker(hc, 0.2f), darker(hc, 0.3f));
        vgrad(cv, mid, top, x[2], h, darker(ac, 0.32f), darker(ac, 0.42f));
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[1] + mid) / 2f, cy, u * 0.8f, INK, mont6);
            words(cv, "-", mid, cy, u * 0.8f, 0, INK, mont6, 0);
            scoreOne(cv, d, sc[1], 2, (mid + x[2]) / 2f, cy, u * 0.8f, INK, mont6);
        } else {
            words(cv, d.score, mid, cy, u * 0.625f, x[2] - x[1], INK, mont6, 0);
        }
        vgrad(cv, x[2], top, x[3], h, ac, darker(ac, 0.12f));
        float ae = x[3];
        if (crests) {
            crest(cv, d.awayCrest, x[3] - u * 0.94f, top + u * 0.06f, u * 0.88f);
            ae -= u;
        }
        words(cv, d.away, (x[2] + ae) / 2f, cy, u * 0.775f, ae - x[2] - u * 0.2f, ink(ac), mont6, 0);
        vgrad(cv, x[3], top, x[4], h, 0xFFF3F3F3, 0xFFDCDCDC);
        clock(cv, d, x[3], top, x[4], h, 0, 0xFF1E1E1E, u * 0.725f, mont5);
    }

    /**
     * Premier League world feed: each club's code on its colour darkened, pointing in to the score; the two scores large,
     * standing over the row either side of the lion; the clock in purple under the middle.
     */
    private void pl(Canvas cv, Data d, float[] x, float u, String[] sc) {
        float bt = u * PL_TOP, bb = bt + u, cy = (bt + bb) / 2f, tip = u * 0.3f, right = x[x.length - 1];
        int hc = darker(solid(d.homeColour), 0.35f), ac = darker(solid(d.awayColour), 0.35f);
        path.reset();
        path.moveTo(x[0], bt);
        path.lineTo(x[1], bt);
        path.lineTo(x[1] + tip, cy);
        path.lineTo(x[1], bb);
        path.lineTo(x[0], bb);
        path.close();
        fillPath(cv, hc, bt, bb);
        path.reset();
        path.moveTo(right, bt);
        path.lineTo(x[6], bt);
        path.lineTo(x[6] - tip, cy);
        path.lineTo(x[6], bb);
        path.lineTo(right, bb);
        path.close();
        fillPath(cv, ac, bt, bb);
        chevronBand(cv, x[1] + u * 0.1f, bt, bb, tip, u * 0.13f, 1, 0xFFF6F8FA, 0xFFAFB9C6);
        chevronBand(cv, x[1] + u * 0.27f, bt, bb, tip, u * 0.08f, 1, 0xB3DCE5EF, 0x80AFC0D4);
        chevronBand(cv, x[6] - u * 0.1f, bt, bb, tip, u * 0.13f, -1, 0xFFF6F8FA, 0xFFAFB9C6);
        chevronBand(cv, x[6] - u * 0.27f, bt, bb, tip, u * 0.08f, -1, 0xB3DCE5EF, 0x80AFC0D4);
        words(cv, d.home, (x[0] + x[1]) / 2f, cy, u * 0.88f, x[1] - x[0] - u * 0.35f, INK, mont8, 0);
        words(cv, d.away, (x[6] + right) / 2f, cy, u * 0.88f, right - x[6] - u * 0.35f, INK, mont8, 0);
        // The clock's tab under the middle.
        r.set(x[2] - u * 0.1f, bb - u * 0.02f, x[5] + u * 0.1f, bb + u * 0.82f);
        fill.setColor(d.stoppage ? RED : 0xFF37003C);
        cv.drawRect(r, fill);
        words(cv, d.clock, (x[2] + x[5]) / 2f, bb + u * 0.4f, u * 0.66f, x[5] - x[2] - u * 0.3f, INK, mont7, 0);
        // The scores large, then the lion between them.
        if (sc != null) {
            bigDigit(cv, d, sc[0], 1, (x[2] + x[3]) / 2f, cy - u * 0.06f, u * 1.85f);
            bigDigit(cv, d, sc[1], 2, (x[4] + x[5]) / 2f, cy - u * 0.06f, u * 1.85f);
        } else {
            words(cv, d.score, (x[2] + x[5]) / 2f, cy, u * 0.5f, x[5] - x[2], INK, mont8, 0);
        }
        logoIn(cv, emblem(d.logoLight != null ? d.logoLight : d.logo, E_MARK), x[3] - u * 0.22f, 0, x[4] + u * 0.22f, bb + u * 0.12f, 0f, null);
    }

    /**
     * FA Cup: each club's code on light grey with its colour at the end, its score on charcoal; the trophy standing over
     * the middle, the clock under it in red.
     */
    private void faCup(Canvas cv, Data d, float[] x, float u, String[] sc) {
        float bt = u * FA_TOP, bb = bt + u, cy = (bt + bb) / 2f, mid = (x[3] + x[4]) / 2f;
        rect(cv, x[0], bt, x[1], bb, solid(d.homeColour));
        vgrad(cv, x[1], bt, x[2], bb, 0xFFF0F0F0, 0xFFD8D8D8);
        words(cv, d.home, (x[1] + x[2]) / 2f, cy, u * 0.744f, x[2] - x[1] - u * 0.3f, 0xFF3A3D41, mont7, 0);
        vgrad(cv, x[2], bt, x[3], bb, 0xFF383B3E, 0xFF25282A);
        vgrad(cv, x[4], bt, x[5], bb, 0xFF383B3E, 0xFF25282A);
        vgrad(cv, x[5], bt, x[6], bb, 0xFFF0F0F0, 0xFFD8D8D8);
        words(cv, d.away, (x[5] + x[6]) / 2f, cy, u * 0.744f, x[6] - x[5] - u * 0.3f, 0xFF3A3D41, mont7, 0);
        rect(cv, x[6], bt, x[7], bb, solid(d.awayColour));
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[2] + x[3]) / 2f, cy, u * 0.768f, INK, mont7);
            scoreOne(cv, d, sc[1], 2, (x[4] + x[5]) / 2f, cy, u * 0.768f, INK, mont7);
        }
        // The trophy (the competition's logo: its white trophy cut out, else the logo), standing on the clock's tab.
        float tt = bt + u * 0.55f;
        Bitmap trophy = emblem(d.logo, E_WHITE_MARK);
        if (trophy != null && trophy != d.logo) {
            fit(trophy, mid - u * 0.9f, 0, mid + u * 0.9f, tt + u * 0.02f, 0f, 1f);
            r2.set(r);
            r.offset(u * 0.03f, u * 0.03f);
            img.setColorFilter(shadow);
            cv.drawBitmap(trophy, null, r, img);
            img.setColorFilter(null);
            cv.drawBitmap(trophy, null, r2, img);
        } else {
            logoIn(cv, d.logo, mid - u * 0.9f, 0, mid + u * 0.9f, tt, 0f, null);
        }
        r.set(x[3] - u * 0.05f, tt, x[4] + u * 0.05f, bb + u * 0.5f);
        fill.setColor(d.stoppage ? RED : 0xFFC8102E);
        cv.drawRoundRect(r, u * 0.05f, u * 0.05f, fill);
        String c = d.clock == null || d.clock.isEmpty() || sc != null ? d.clock : d.score;
        words(cv, c, mid, (tt + bb + u * 0.5f) / 2f, u * 0.744f, x[4] - x[3], INK, mont7, 0);
    }

    /**
     * Eredivisie: each club's code on its colour fading out from the score, the scores on two greys, the clock apart on
     * white. No logo.
     */
    private void eredivisie(Canvas cv, Data d, float[] x, float u, String[] sc) {
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        hgrad(cv, x[0], 0, x[1], u, (darker(hc, 0.55f) & 0xFFFFFF) | 0x8C000000, hc);
        rect(cv, x[1] - u * 0.28f, 0, x[1], u, lighter(hc, 0.15f));
        words(cv, d.home, (x[0] + x[1] - u * 0.28f) / 2f, u / 2f, u * 0.762f, x[1] - x[0] - u * 0.8f, INK, inter6, 0);
        vgrad(cv, x[1], 0, x[2], u, 0xFFE3E3E3, 0xFFC9C9C9);
        vgrad(cv, x[2], 0, x[3], u, 0xFFD4D4D4, 0xFFBBBBBB);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[1] + x[2]) / 2f, u / 2f, u * 0.813f, 0xFF121212, inter6);
            scoreOne(cv, d, sc[1], 2, (x[2] + x[3]) / 2f, u / 2f, u * 0.813f, 0xFF121212, inter6);
        } else {
            words(cv, d.score, x[2], u / 2f, u * 0.61f, x[3] - x[1], 0xFF121212, inter6, 0);
        }
        rect(cv, x[3], 0, x[4], u, lighter(ac, 0.15f));
        hgrad(cv, x[4], 0, x[5], u, ac, (darker(ac, 0.55f) & 0xFFFFFF) | 0x8C000000);
        words(cv, d.away, (x[4] + x[5]) / 2f, u / 2f, u * 0.762f, x[5] - x[4] - u * 0.6f, INK, inter6, 0);
        vgrad(cv, x[6], 0, x[7], u, 0xFFF7F7F7, 0xFFDEDEDE);
        clock(cv, d, x[6], 0, x[7], u, 0, 0xFF121212, u * 0.762f, inter6);
    }

    /**
     * Liga MX: one dark panel, the competition's title along its top under a light line; the logo, then each club's crest,
     * code and score on its colour, then the clock.
     */
    private void ligaMx(Canvas cv, Data d, float[] x, float h, float u, String[] sc) {
        float bt = u * MX_TOP, bb = bt + u, cy = (bt + bb) / 2f, right = x[x.length - 1], in = u * 0.07f;
        r.set(0, 0, right, h);
        fill.setColor(0xF0232427);
        cv.drawRoundRect(r, u * 0.12f, u * 0.12f, fill);
        rect(cv, u * 0.12f, u * 0.03f, right - u * 0.12f, u * 0.075f, 0xFF8E9196);
        words(cv, mxTitle(d), right / 2f, u * 0.47f, u * 0.538f, right - u * 0.6f, 0xFFE3E4E7, mont6, 0);
        logoIn(cv, emblem(d.logo, E_MARK_LEFT), x[1], bt + u * 0.1f, x[2], bb - u * 0.1f, 0f, null);
        boolean crests = d.homeCrest != null || d.awayCrest != null;
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        for (int side = 0; side < 2; side++) {
            float l = side == 0 ? x[2] : x[4], rr = side == 0 ? x[3] : x[5];
            int colour = side == 0 ? hc : ac;
            rect(cv, l, bt + in, rr, bb - in, colour);
            float tx = l + u * 0.25f;
            if (crests) {
                crest(cv, side == 0 ? d.homeCrest : d.awayCrest, l + u * 0.08f, bt + in + u * 0.04f, u - 2 * in - u * 0.08f);
                tx = l + u * 0.98f;
            }
            int ink = inkHi(colour);
            words(cv, side == 0 ? d.home : d.away, tx, cy, u * 0.717f, rr - tx - u * 1.1f, ink, mont5, -1);
            if (sc != null) scoreOne(cv, d, sc[side], side + 1, rr - u * 0.5f, cy, u * 0.768f, ink, mont6);
        }
        if (sc == null) words(cv, d.score, x[4], cy, u * 0.563f, u * 1.5f, INK, mont6, 0);
        clock(cv, d, x[5], bt + in, x[6], bb - in, 0, INK, u * 0.768f, mont7);
    }

    /** LaLiga: the logo on coral over the clock on white; the clubs on near black, each edged in its colour; the scores on white. */
    private void laLiga(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float u = h / 2f;
        rect(cv, x[0], 0, x[1], u, 0xFFEE4B3B);
        logoIn(cv, emblem(d.logo, E_MARK), x[0], 0, x[1], u, u * 0.17f, white);
        rect(cv, x[0], u, x[1], h, 0xFFF4F4F4);
        clock(cv, d, x[0], u, x[1], h, 0, 0xFF111111, u * 0.577f, bcond8);
        rect(cv, x[1], 0, x[2], h, 0xFF0E0F1F);
        words(cv, d.home, (x[1] + x[2]) / 2f, u / 2f, u * 0.521f, x[2] - x[1] - u * 0.15f, INK, bcond8, 0);
        words(cv, d.away, (x[1] + x[2]) / 2f, u * 1.5f, u * 0.521f, x[2] - x[1] - u * 0.15f, INK, bcond8, 0);
        rect(cv, x[2], 0, x[3], u, solid(d.homeColour));
        rect(cv, x[2], u, x[3], h, solid(d.awayColour));
        rect(cv, x[3], 0, x[4], h, 0xFFEFEFEF);
        rect(cv, x[3] + u * 0.14f, u - u * 0.012f, x[4] - u * 0.14f, u + u * 0.012f, 0xFF3C3C3C);
        stackScores(cv, d, sc, (x[3] + x[4]) / 2f, u, u * 0.67f, 0xFF111111, bcond8);
    }

    /**
     * Liga Portugal: the logo on white down its start, the clubs on navy and the scores on white beside it, each row's end
     * in its club's colour; under them the competition's name on red and the clock on white.
     */
    private void ligaPortugal(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float u = h / 2f, rh = u * 0.8f, low = rh * 2f;
        rect(cv, x[0], 0, x[1], low, 0xFFFFFFFF);
        logoIn(cv, emblem(d.logoLight != null ? d.logoLight : d.logo, E_MARK), x[0], 0, x[1], low, u * 0.14f, null);
        rect(cv, x[0], low, x[1], h, 0xFFE30613);
        words(cv, title(d, "LIGA PORTUGAL"), (x[0] + x[1]) / 2f, (low + h) / 2f, (h - low) * 0.5f, x[1] - x[0] - u * 0.14f, INK, mont8, 0);
        vgrad(cv, x[1], 0, x[2], low, 0xFF0D2E73, 0xFF08225A);
        rect(cv, x[1] + u * 0.12f, rh - u * 0.01f, x[2] - u * 0.12f, rh + u * 0.01f, 0xFF3F5DA6);
        words(cv, d.home, (x[1] + x[2]) / 2f, rh / 2f, u * 0.34f, x[2] - x[1] - u * 0.2f, INK, mont8, 0);
        words(cv, d.away, (x[1] + x[2]) / 2f, rh * 1.5f, u * 0.34f, x[2] - x[1] - u * 0.2f, INK, mont8, 0);
        rect(cv, x[1], low, x[2], h, 0xFFFFFFFF);
        clock(cv, d, x[1], low, x[2], h, 0, 0xFF0D2E73, (h - low) * 0.72f, mont8);
        rect(cv, x[2], 0, x[3], low, 0xFFFFFFFF);
        rect(cv, x[2] + u * 0.1f, rh - u * 0.01f, x[3] - u * 0.1f, rh + u * 0.01f, 0xFFB9C3D6);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[2] + x[3]) / 2f, rh / 2f, u * 0.5f, 0xFF0D2E73, mont7);
            scoreOne(cv, d, sc[1], 2, (x[2] + x[3]) / 2f, rh * 1.5f, u * 0.5f, 0xFF0D2E73, mont7);
        } else {
            words(cv, d.score, (x[2] + x[3]) / 2f, rh, u * 0.3f, x[3] - x[2], 0xFF0D2E73, mont7, 0);
        }
        rect(cv, x[3], 0, x[4], rh, solid(d.homeColour));
        rect(cv, x[3], rh, x[4], low, solid(d.awayColour));
    }

    /** Bundesliga: the clock over the league's player (in black on grey); each club's column in its colour, its code over its score. */
    private void bundesliga(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float u = h / 2f, split = u * 0.62f;
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        rect(cv, x[0], 0, x[1], split, 0xEB2C2C2C);
        clock(cv, d, x[0], 0, x[1], split, 0, INK, split * 0.68f, bsemi7);
        rect(cv, x[0], split, x[1], h, 0xFFE3E3E3);
        Bitmap player = emblem(d.logo, E_MARK_WHITE);
        logoIn(cv, player, x[0], split, x[1], h, u * 0.14f, player != d.logo ? black : null);
        rect(cv, x[1], 0, x[2], h, hc);
        rect(cv, x[2], 0, x[3], h, ac);
        rect(cv, x[1], h - u * 0.06f, x[2], h, darker(hc, 0.3f));
        rect(cv, x[2], h - u * 0.06f, x[3], h, darker(ac, 0.25f));
        words(cv, d.home, (x[1] + x[2]) / 2f, split / 2f, split * 0.66f, x[2] - x[1] - u * 0.2f, (ink(hc) & 0xFFFFFF) | 0xE6000000, bsemi7, 0);
        words(cv, d.away, (x[2] + x[3]) / 2f, split / 2f, split * 0.66f, x[3] - x[2] - u * 0.2f, (ink(ac) & 0xFFFFFF) | 0xE6000000, bsemi7, 0);
        float cy = split + (h - split) / 2f - u * 0.03f, size = (h - split) * 0.95f;
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[1] + x[2]) / 2f, cy, size, ink(hc), bsemi7);
            scoreOne(cv, d, sc[1], 2, (x[2] + x[3]) / 2f, cy, size, ink(ac), bsemi7);
        } else {
            words(cv, d.score, (x[1] + x[3]) / 2f, cy, size * 0.45f, x[3] - x[1], ink(hc), bsemi7, 0);
        }
    }

    /** Ligue 1: the logo on a blue square over the clock on white; the clubs and scores on dark, each row's end in its colour. */
    private void ligue1(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float u = h / 2f, rh = u * 0.7f, low = rh * 2f;
        vgrad(cv, x[0], 0, x[1], low, 0xFF2766FF, 0xFF1A50DD);
        logoIn(cv, emblem(d.logo, E_MARK), x[0], 0, x[1], low, u * 0.24f, white);
        rect(cv, x[0], low, x[1], h, 0xFFF4F4F4);
        clock(cv, d, x[0], low, x[1], h, 0, 0xFF111111, (h - low) * 0.72f, mont7);
        rect(cv, x[1], 0, x[2], low, 0xF2222428);
        rect(cv, x[1] + u * 0.15f, rh - u * 0.008f, x[2] - u * 0.1f, rh + u * 0.008f, 0xFF3D4046);
        words(cv, d.home, x[1] + u * 0.22f, rh / 2f, u * 0.46f, x[2] - x[1] - u, INK, mont7, -1);
        words(cv, d.away, x[1] + u * 0.22f, rh * 1.5f, u * 0.46f, x[2] - x[1] - u, INK, mont7, -1);
        float sx = x[2] - u * 0.38f;
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, sx, rh / 2f, u * 0.48f, INK, mont7);
            scoreOne(cv, d, sc[1], 2, sx, rh * 1.5f, u * 0.48f, INK, mont7);
        } else {
            words(cv, d.score, sx, rh, u * 0.3f, u * 0.7f, INK, mont7, 0);
        }
        rect(cv, x[3], 0, x[4], rh, solid(d.homeColour));
        rect(cv, x[3], rh, x[4], low, solid(d.awayColour));
    }

    // ---- the retro boards --------------------------------------------------------------------------------------------

    /**
     * 2004: the silver and gold arrow with its navy inner arrow down its start, the clubs' names one above the other on
     * dark grey (in small capitals) with their scores in white boxes, the clock in a pale tab under them.
     */
    private void r2004(Canvas cv, Data d, float[] x, float u, float h, String[] sc) {
        float[] rows = {u * 0.2f, u * 0.68f, u * 0.74f, u * 1.22f};
        float nl = x[0] + u * 0.9f;
        for (int i = 0; i < 2; i++) {
            float t = rows[i * 2], b = rows[i * 2 + 1], cy = (t + b) / 2f;
            vgrad(cv, nl, t, x[2], b, 0xFF5E636A, 0xFF2B2E33);
            rect(cv, nl, t, x[2], t + u * 0.02f, 0x59FFFFFF);
            String name = i == 0 ? shortName(d.homeName, d.home) : shortName(d.awayName, d.away);
            smallCaps(cv, name, x[1] + u * 0.1f, cy, u * 0.45f, x[2] - x[1] - u * 0.2f, INK, bsemi7);
            rect(cv, x[2] + u * 0.04f, t, x[3], b, 0xFF2B2E33);
            vgrad(cv, x[2] + u * 0.07f, t + u * 0.03f, x[3] - u * 0.03f, b - u * 0.03f, 0xFFFFFFFF, 0xFFE2E4E8);
            if (sc != null) scoreOne(cv, d, sc[i], i + 1, (x[2] + x[3]) / 2f + u * 0.02f, cy, u * 0.475f, 0xFF111111, bsemi7);
        }
        if (sc == null) words(cv, d.score, (x[2] + x[3]) / 2f, (rows[0] + rows[3]) / 2f, u * 0.3f, x[3] - x[2], INK, bsemi7, 0);
        float cw = Math.max(u * 1.34f, tw(d.clock, u * 0.475f, bsemi7) + u * 0.5f), cl = x[0] + u * 0.85f, ct = u * 1.3f, cb = u * 1.84f;
        vgrad(cv, cl, ct, cl + cw, cb, d.stoppage ? RED : 0xFFF4F5F7, d.stoppage ? darker(RED, 0.2f) : 0xFFC8CCD2);
        words(cv, d.clock, cl + cw / 2f, (ct + cb) / 2f, u * 0.475f, cw - u * 0.2f, d.stoppage ? INK : 0xFF1E1E1E, bsemi7, 0);
        // The arrows over the names' start: silver and gold outside, navy inside.
        arrow(cv, x[0], h, 0f, u * 0.4f, u * 1.02f, u * 0.62f);
        fill.setShader(new LinearGradient(x[0], 0, x[0] + u * 1.02f, 0, new int[]{0xFFFFFFFF, 0xFFEAD9A6, 0xFFA3ABB7}, null, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
        arrow(cv, x[0], h, u * 0.42f, u * 0.66f, u * 1.28f, u * 1.04f);
        fill.setShader(new LinearGradient(x[0] + u * 0.42f, 0, x[0] + u * 1.28f, 0, 0xFF1D2A69, 0xFF2F45A3, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
    }

    /** An arrow pointing right down the board's height: its back's corners at a and b, its point at tip, its inner corner at in. */
    private void arrow(Canvas cv, float x0, float h, float a, float b, float tip, float in) {
        float t = h * 0.03f, bt = h * 0.97f, cy = h / 2f;
        path.reset();
        path.moveTo(x0 + a, t);
        path.lineTo(x0 + b, t);
        path.lineTo(x0 + tip, cy);
        path.lineTo(x0 + b, bt);
        path.lineTo(x0 + a, bt);
        path.lineTo(x0 + in, cy);
        path.close();
    }

    /**
     * 2008 (and 2012, light): the logo in its ball on a navy blade at its start; the clubs' names either side of the score,
     * set off by slanted stripes in their colours (2008: white names on navy, the end cut; 2012: navy names on silver, the
     * score in a red pill, swoops at both ends); the clock in its navy tab under the home name.
     */
    private void r2008(Canvas cv, Data d, float[] x, float u, String[] sc, boolean y2012) {
        float bt = u * R8_TOP, bb = bt + u, cy = (bt + bb) / 2f, right = x[x.length - 1];
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        path.reset();
        path.moveTo(x[0] + u * 1.7f, bt);
        path.lineTo(right, bt);
        path.lineTo(right - u * 0.4f, bb);
        path.lineTo(x[0] + u * 1.7f, bb);
        path.close();
        if (y2012) {
            fill.setShader(new LinearGradient(0, bt, 0, bb, new int[]{0xFFFCFDFE, 0xFFE9ECF1, 0xFFC9CFD9}, null, Shader.TileMode.CLAMP));
        } else {
            fill.setShader(new LinearGradient(0, bt, 0, bb, 0xFF28377A, 0xFF101A48, Shader.TileMode.CLAMP));
        }
        cv.drawPath(path, fill);
        fill.setShader(null);
        int ink = y2012 ? 0xFF16245C : INK;
        words(cv, shortName(d.homeName, d.home), (x[1] + x[2]) / 2f, cy, u * 0.775f, x[2] - x[1] - u * 0.3f, ink, bsemi7, 0);
        words(cv, shortName(d.awayName, d.away), (x[5] + x[6]) / 2f, cy, u * 0.775f, x[6] - x[5] - u * 0.3f, ink, bsemi7, 0);
        slashes(cv, x[2] + u * 0.08f, bt, bb, hc, y2012);
        slashes(cv, x[4] + u * 0.04f, bt, bb, ac, y2012);
        r.set(x[3], bt + u * 0.1f, x[4], bb - u * 0.1f);
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFD42A38, 0xFF931521, Shader.TileMode.CLAMP));
        float rad = y2012 ? u * 0.16f : u * 0.03f;
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        float mid = (x[3] + x[4]) / 2f;
        if (sc != null) {
            float half = tw(sc[0] + "-", u * 0.868f, bsemi7) * 0.5f + u * 0.05f;
            scoreOne(cv, d, sc[0], 1, mid - half, cy, u * 0.868f, INK, bsemi7);
            words(cv, "-", mid, cy, u * 0.868f, 0, INK, bsemi7, 0);
            scoreOne(cv, d, sc[1], 2, mid + half, cy, u * 0.868f, INK, bsemi7);
        } else {
            words(cv, d.score, mid, cy, u * 0.62f, x[4] - x[3], INK, bsemi7, 0);
        }
        if (y2012) {
            // The red swoop at the names' start and the blue one at the end.
            swoop(cv, x[1] - u * 0.05f, bt, bb, 0xFFD42A38, 1);
            swoop(cv, right - u * 0.28f, bt, bb, 0xFF2E5FC9, -1);
        }
        // The blade and the ball with the logo.
        path.reset();
        path.moveTo(x[0], bt);
        path.lineTo(x[0] + u * 1.95f, bt);
        path.lineTo(x[0] + u * 1.95f, bb + u * 0.6f);
        path.lineTo(x[0] + u * 0.95f, bb + u * 0.6f);
        path.close();
        fill.setShader(new LinearGradient(x[0], 0, x[0] + u * 1.95f, 0, 0xFF2D3E8F, 0xFF121C4E, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
        float bx = x[0] + u * 1.78f, by = (bt + bb + u * 0.6f) / 2f, br = u * 0.84f;
        fill.setShader(new android.graphics.RadialGradient(bx - br * 0.3f, by - br * 0.3f, br * 1.4f, new int[]{0xFFFFFFFF, 0xFFE3EDF8, 0xFF9DB8DF},
                null, Shader.TileMode.CLAMP));
        cv.drawCircle(bx, by, br, fill);
        fill.setShader(null);
        logoIn(cv, emblem(d.logoLight != null ? d.logoLight : d.logo, E_MARK), bx - br * 0.68f, by - br * 0.68f, bx + br * 0.68f, by + br * 0.68f, 0f, null);
        // The clock's tab under the home name.
        float cw = Math.max(u * 1.9f, tw(d.clock, u * 0.775f, bsemi7) + u * 0.7f), cl = x[1] - u * 0.05f;
        r.set(cl, bb - u * 0.08f, cl + cw, bb + u * 0.78f);
        fill.setColor(d.stoppage ? RED : 0xFF16224F);
        cv.drawRoundRect(r, u * 0.3f, u * 0.3f, fill);
        rect(cv, cl, bb - u * 0.08f, cl + cw, bb + u * 0.2f, d.stoppage ? RED : 0xFF16224F);
        words(cv, d.clock, cl + cw / 2f, bb + u * 0.36f, u * 0.775f, cw - u * 0.3f, INK, bsemi7, 0);
    }

    /** Two slanted stripes (a club's colour, then white), leaning back as the 2008 board's did. */
    private void slashes(Canvas cv, float l, float t, float b, int colour, boolean light) {
        float sl = (b - t) * 0.35f, sw = (b - t) * 0.12f, gap = (b - t) * 0.07f;
        for (int i = 0; i < 2; i++) {
            float a = l + i * (sw + gap);
            path.reset();
            path.moveTo(a, t);
            path.lineTo(a + sw, t);
            path.lineTo(a + sw + sl, b);
            path.lineTo(a + sl, b);
            path.close();
            fill.setColor(i == 0 ? colour : light ? 0xFF9AA6BC : 0xFFFFFFFF);
            cv.drawPath(path, fill);
        }
    }

    /** A curved swoop across the row's height (dir 1: bowing left, as at the 2012 board's start; -1: at its end). */
    private void swoop(Canvas cv, float x0, float t, float b, int colour, int dir) {
        float hgt = b - t, wdt = hgt * 0.28f;
        path.reset();
        path.moveTo(x0 + wdt * (dir > 0 ? 1.2f : 0f), t);
        path.quadTo(x0 - wdt * dir * 0.6f, (t + b) / 2f, x0 + wdt * (dir > 0 ? 1.2f : 0f), b);
        path.lineTo(x0 + wdt * (dir > 0 ? 1.7f : 0.5f), b);
        path.quadTo(x0 + wdt * dir * -0.05f, (t + b) / 2f, x0 + wdt * (dir > 0 ? 1.7f : 0.5f), t);
        path.close();
        fill.setColor(colour);
        cv.drawPath(path, fill);
    }

    /** 2016: the clubs' codes on purple either side of the score on green, the lion standing over it, the clock in white under it. */
    private void r2016(Canvas cv, Data d, float[] x, float u, String[] sc) {
        float bt = u * R16_TOP, bb = bt + u, cy = (bt + bb) / 2f, right = x[x.length - 1], edge = u * 0.06f;
        rect(cv, x[0], bt, x[0] + edge, bb, solid(d.homeColour));
        rect(cv, x[0] + edge, bt, x[1], bb, 0xFFFFFFFF);
        rect(cv, x[1], bt, x[2], bb, 0xFF38003C);
        words(cv, d.home, (x[1] + x[2]) / 2f, cy, u * 0.78f, x[2] - x[1] - u * 0.3f, INK, rubik6, 0);
        rect(cv, x[2], bt, x[3], bb, 0xFF00FF85);
        float mid = (x[2] + x[3]) / 2f;
        if (sc != null) {
            float half = tw(sc[0] + "-", u * 0.84f, rubik6) * 0.5f + u * 0.04f;
            scoreOne(cv, d, sc[0], 1, mid - half, cy, u * 0.84f, 0xFF136B36, rubik6);
            words(cv, "-", mid, cy, u * 0.84f, 0, 0xFF136B36, rubik6, 0);
            scoreOne(cv, d, sc[1], 2, mid + half, cy, u * 0.84f, 0xFF136B36, rubik6);
        } else {
            words(cv, d.score, mid, cy, u * 0.63f, x[3] - x[2], 0xFF136B36, rubik6, 0);
        }
        rect(cv, x[3], bt, x[4], bb, 0xFF38003C);
        words(cv, d.away, (x[3] + x[4]) / 2f, cy, u * 0.78f, x[4] - x[3] - u * 0.3f, INK, rubik6, 0);
        rect(cv, x[4], bt, right - edge, bb, 0xFFFFFFFF);
        rect(cv, right - edge, bt, right, bb, solid(d.awayColour));
        logoIn(cv, emblem(d.logoLight != null ? d.logoLight : d.logo, E_MARK), x[2], 0, x[3], bt + u * 0.04f, 0f, null);
        rect(cv, x[2], bb, x[3], bb + u * 0.6f, d.stoppage ? RED : 0xFFFFFFFF);
        words(cv, d.clock, mid, bb + u * 0.3f, u * 0.66f, x[3] - x[2] - u * 0.3f, d.stoppage ? INK : 0xFF1A1A1A, rubik6, 0);
    }

    // ---- small parts ------------------------------------------------------------------------------------------------

    private void rect(Canvas cv, float l, float t, float rr, float b, int colour) {
        fill.setShader(null);
        fill.setColor(colour);
        cv.drawRect(l, t, rr, b, fill);
    }

    /** A block lit from its top: top colour to foot colour. */
    private void vgrad(Canvas cv, float l, float t, float rr, float b, int top, int foot) {
        fill.setShader(new LinearGradient(0, t, 0, b, top, foot, Shader.TileMode.CLAMP));
        cv.drawRect(l, t, rr, b, fill);
        fill.setShader(null);
    }

    /** A block from one colour on its left to another on its right. */
    private void hgrad(Canvas cv, float l, float t, float rr, float b, int left, int right) {
        fill.setShader(new LinearGradient(l, 0, rr, 0, left, right, Shader.TileMode.CLAMP));
        cv.drawRect(l, t, rr, b, fill);
        fill.setShader(null);
    }

    /** A block with its left and/or right corners rounded. */
    private void block(Canvas cv, float l, float t, float rr, float b, int colour, float rad, boolean roundLeft, boolean roundRight) {
        fill.setShader(null);
        fill.setColor(colour);
        if (!roundLeft && !roundRight) {
            cv.drawRect(l, t, rr, b, fill);
            return;
        }
        r.set(l, t, rr, b);
        cv.drawRoundRect(r, rad, rad, fill);
        if (!roundLeft) cv.drawRect(l, t, Math.min(rr, l + rad), b, fill);
        if (!roundRight) cv.drawRect(Math.max(l, rr - rad), t, rr, b, fill);
    }

    /** A block slanted at both ends (its top s further right than its foot), lit from its top. */
    private void para(Canvas cv, float l, float t, float rr, float b, float s, int top, int foot) {
        path.reset();
        path.moveTo(l + s, t);
        path.lineTo(rr + s, t);
        path.lineTo(rr, b);
        path.lineTo(l, b);
        path.close();
        fill.setShader(new LinearGradient(0, t, 0, b, top, foot, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
    }

    /** The path drawn in a colour lit from its top. */
    private void fillPath(Canvas cv, int colour, float t, float b) {
        fill.setShader(new LinearGradient(0, t, 0, b, lighter(colour, 0.12f), colour, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
    }

    /** A thin chevron about x (dir 1 pointing right, -1 left) down a row u high. */
    private void chevron(Canvas cv, float x, float u, int dir, int colour) {
        float a = u * 0.12f * dir, tip = u * 0.08f * dir, th = u * 0.07f * dir;
        path.reset();
        path.moveTo(x - a, u * 0.2f);
        path.lineTo(x + tip, u * 0.5f);
        path.lineTo(x - a, u * 0.8f);
        path.lineTo(x - a - th, u * 0.8f);
        path.lineTo(x + tip - th, u * 0.5f);
        path.lineTo(x - a - th, u * 0.2f);
        path.close();
        fill.setShader(null);
        fill.setColor(colour);
        cv.drawPath(path, fill);
    }

    /** A chevron band following a block's point (dir 1: its point right), lit from its top. */
    private void chevronBand(Canvas cv, float x, float t, float b, float tip, float thick, int dir, int top, int foot) {
        float cy = (t + b) / 2f;
        path.reset();
        path.moveTo(x, t);
        path.lineTo(x + thick * dir, t);
        path.lineTo(x + (thick + tip) * dir, cy);
        path.lineTo(x + thick * dir, b);
        path.lineTo(x, b);
        path.lineTo(x + tip * dir, cy);
        path.close();
        fill.setShader(new LinearGradient(0, t, 0, b, top, foot, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
    }

    /** A club's block in its colour (lit from its top), its code on it in whichever of white or dark reads. */
    private void team(Canvas cv, String code, float l, float t, float rr, float b, int colour, float size, Typeface tf) {
        vgrad(cv, l, t, rr, b, lighter(colour, 0.1f), darker(colour, 0.12f));
        words(cv, code, (l + rr) / 2f, (t + b) / 2f, size, rr - l - (b - t) * 0.3f, ink(colour), tf, 0);
    }

    /** The clock in its box (ground 0: none), red and staying red in added time. */
    private void clock(Canvas cv, Data d, float l, float t, float rr, float b, int ground, int ink, float size, Typeface tf) {
        int g = d.stoppage ? RED : ground;
        if (g != 0) rect(cv, l, t, rr, b, g);
        words(cv, d.clock, (l + rr) / 2f, (t + b) / 2f, size, (rr - l) * 0.88f, d.stoppage ? INK : ink, tf, 0);
    }

    /** Both scores side by side between l and rr (or the score as it is, before kick-off). */
    private void score(Canvas cv, Data d, String[] sc, float l, float rr, float t, float b, float size, int ink, Typeface tf) {
        float cy = (t + b) / 2f;
        if (sc == null) {
            words(cv, d.score, (l + rr) / 2f, cy, size * 0.8f, rr - l, ink, tf, 0);
            return;
        }
        float m = (l + rr) / 2f;
        scoreOne(cv, d, sc[0], 1, (l + m) / 2f, cy, size, ink, tf);
        scoreOne(cv, d, sc[1], 2, (m + rr) / 2f, cy, size, ink, tf);
    }

    /** The two scores one above the other, about cx, each row half high. */
    private void stackScores(Canvas cv, Data d, String[] sc, float cx, float half, float size, int ink, Typeface tf) {
        if (sc == null) {
            words(cv, d.score, cx, half, size * 0.5f, half * 1.8f, ink, tf, 0);
            return;
        }
        scoreOne(cv, d, sc[0], 1, cx, half / 2f, size, ink, tf);
        scoreOne(cv, d, sc[1], 2, cx, half * 1.5f, size, ink, tf);
    }

    /** One side's score; the scorers' grows and glows as a goal's new score comes out. */
    private void scoreOne(Canvas cv, Data d, String s, int side, float cx, float cy, float size, int ink, Typeface tf) {
        float g = d.glowSide == side ? Math.max(0f, Math.min(1f, d.glow)) : 0f;
        if (g > 0f) {
            // A soft light behind it, fading out from its middle.
            fill.setShader(new android.graphics.RadialGradient(cx, cy, size * 0.9f, new int[]{(Math.round(110 * g) << 24) | 0xFFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, size * 0.9f, fill);
            fill.setShader(null);
        }
        words(cv, s, cx, cy, size * (1f + 0.38f * g * g), 0, ink, tf, 0);
    }

    /** The world feed's large score: white with a soft shadow under it. */
    private void bigDigit(Canvas cv, Data d, String s, int side, float cx, float cy, float size) {
        words(cv, s, cx + size * 0.02f, cy + size * 0.025f, size, 0, 0x66000000, mont8, 0);
        scoreOne(cv, d, s, side, cx, cy, size, INK, mont8);
    }

    /** A crest fitted in a square at (l, t). */
    private void crest(Canvas cv, Bitmap b, float l, float t, float size) {
        if (b == null || b.isRecycled()) return;
        float k = Math.min(size / b.getWidth(), size / b.getHeight()), bw = b.getWidth() * k, bh = b.getHeight() * k;
        r.set(l + (size - bw) / 2f, t + (size - bh) / 2f, l + (size + bw) / 2f, t + (size + bh) / 2f);
        img.setColorFilter(null);
        cv.drawBitmap(b, null, r, img);
    }

    /** Sets r to the picture fitted in its box (pad all round), centred across, down: 0 at the box's top to 1 its foot. */
    private boolean fit(Bitmap b, float l, float t, float rr, float bt, float pad, float down) {
        if (b == null || b.isRecycled() || b.getWidth() < 1 || b.getHeight() < 1) return false;
        float bw = rr - l - 2 * pad, bh = bt - t - 2 * pad;
        if (bw <= 1 || bh <= 1) return false;
        float k = Math.min(bw / b.getWidth(), bh / b.getHeight()), w = b.getWidth() * k, h = b.getHeight() * k;
        float x0 = l + pad + (bw - w) / 2f, y0 = t + pad + (bh - h) * down;
        r.set(x0, y0, x0 + w, y0 + h);
        return true;
    }

    /** The competition's logo (or its emblem) fitted in its box, as it is or through a colour filter (white, black). */
    private void logoIn(Canvas cv, Bitmap b, float l, float t, float rr, float bt, float pad, PorterDuffColorFilter tint) {
        if (!fit(b, l, t, rr, bt, pad, 0.5f)) return;
        img.setColorFilter(tint);
        cv.drawBitmap(b, null, r, img);
        img.setColorFilter(null);
    }

    /** An emblem edged in white all round (o thick): Serie A's way with its logo over the row. */
    private void outlined(Canvas cv, Bitmap b, float l, float t, float rr, float bt, float pad, float o) {
        if (!fit(b, l, t, rr, bt, pad + o, 0.5f)) return;
        r2.set(r);
        img.setColorFilter(white);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            r.set(r2);
            r.offset((float) Math.cos(a) * o, (float) Math.sin(a) * o);
            cv.drawBitmap(b, null, r, img);
        }
        img.setColorFilter(null);
        cv.drawBitmap(b, null, r2, img);
    }

    /**
     * Words about (x, cy): centred (align 0), from x (-1) or up to x (1), at size or smaller to fit maxW (0: any width),
     * their capitals' middle on cy.
     */
    private void words(Canvas cv, String s, float x, float cy, float size, float maxW, int colour, Typeface tf, int align) {
        if (s == null || s.isEmpty() || size <= 1f) return;
        text.setTypeface(tf);
        text.setTextSize(size);
        if (tf == outfit) text.setFontVariationSettings("'wght' 760");
        float tw = text.measureText(s);
        if (maxW > 1f && tw > maxW) {
            text.setTextSize(size * maxW / tw);
            tw = maxW;
        }
        text.getTextBounds("H8", 0, 2, bounds);
        float base = cy + (-bounds.top) / 2f;
        text.setColor(colour);
        float left = align < 0 ? x : align > 0 ? x - tw : x - tw / 2f;
        cv.drawText(s, left, base, text);
        if (tf == outfit) text.setFontVariationSettings(null);
    }

    /** Words in small capitals (each word's first letter full height, the rest smaller), from x: the 2004 board's names. */
    private void smallCaps(Canvas cv, String s, float x, float cy, float size, float maxW, int colour, Typeface tf) {
        if (s == null || s.isEmpty()) return;
        String up = s.toUpperCase(Locale.ROOT);
        float small = size * 0.8f, w = 0;
        boolean start = true;
        for (int i = 0; i < up.length(); i++) {
            char ch = up.charAt(i);
            w += tw(String.valueOf(ch), start ? size : small, tf);
            start = !Character.isLetter(ch);
        }
        float k = maxW > 1f && w > maxW ? maxW / w : 1f;
        text.setTypeface(tf);
        text.setTextSize(size * k);
        text.getTextBounds("H", 0, 1, bounds);
        float base = cy + (-bounds.top) / 2f, at = x;
        text.setColor(colour);
        start = true;
        for (int i = 0; i < up.length(); i++) {
            char ch = up.charAt(i);
            text.setTextSize((start ? size : small) * k);
            String one = String.valueOf(ch);
            cv.drawText(one, at, base, text);
            at += text.measureText(one);
            start = !Character.isLetter(ch);
        }
    }

    // ---- the logos' emblems -----------------------------------------------------------------------------------------

    /** How a logo's emblem is cut: its top part alone; then only its white; white first, then its top; colour first, then its top. */
    static final int E_MARK = 1, E_MARK_WHITE = 2, E_WHITE_MARK = 3, E_COLOUR_MARK = 4, E_MARK_LEFT = 5;
    /** A logo's emblems as cut (null where nothing came of it), and which were tried. */
    private static final class Cuts {
        final Bitmap[] e = new Bitmap[6];
        final boolean[] tried = new boolean[6];
    }

    private final java.util.WeakHashMap<Bitmap, Cuts> emblems = new java.util.WeakHashMap<>();

    /**
     * A logo's emblem, cut once and kept: the logo itself when nothing comes of the cutting (a logo with no wordmark under
     * it, or none of the colour asked for).
     */
    private Bitmap emblem(Bitmap b, int how) {
        if (b == null || b.isRecycled()) return null;
        Cuts got = emblems.get(b);
        if (got == null) {
            got = new Cuts();
            emblems.put(b, got);
        }
        if (!got.tried[how] || (got.e[how] != null && got.e[how].isRecycled())) {
            got.tried[how] = true;
            got.e[how] = cut(b, how);
        }
        return got.e[how] != null ? got.e[how] : b;
    }

    private static Bitmap cut(Bitmap b, int how) {
        try {
            Bitmap src = b;
            float k = Math.min(1f, 320f / Math.max(b.getWidth(), b.getHeight()));
            if (k < 1f) src = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)), Math.max(1, Math.round(b.getHeight() * k)), true);
            if (src.getConfig() == Bitmap.Config.HARDWARE) src = src.copy(Bitmap.Config.ARGB_8888, false);
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            int[] px = new int[w * h];
            src.getPixels(px, 0, w, 0, 0, w, h);
            if (src != b) src.recycle();
            int[] out = emblemPixels(px, w, h, how);
            int[] bb = out == null ? null : LogoTile.bounds(out, w, h);
            if (bb == null) return null;
            int cw = bb[2] - bb[0], ch = bb[3] - bb[1];
            int[] part = new int[cw * ch];
            for (int y = 0; y < ch; y++) System.arraycopy(out, (bb[1] + y) * w + bb[0], part, y * cw, cw);
            return Bitmap.createBitmap(part, cw, ch, Bitmap.Config.ARGB_8888);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Pure: a logo's pixels cut to its emblem (how: E_*), or null when nothing of it is left. */
    static int[] emblemPixels(int[] px, int w, int h, int how) {
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return null;
        int[] p = java.util.Arrays.copyOf(px, w * h);
        if (how == E_WHITE_MARK) whiteOnly(p);
        if (how == E_COLOUR_MARK) colourOnly(p);
        if (count(p) < w * h * 0.01f) return null;
        int[] band = topBand(p, w, h);
        if (band != null) {
            for (int y = 0; y < h; y++) {
                if (y >= band[0] && y < band[1]) continue;
                java.util.Arrays.fill(p, y * w, y * w + w, 0);
            }
        }
        if (how == E_MARK_WHITE) whiteOnly(p);
        if (how == E_MARK_LEFT) {
            // Then its left part, where a clear gap parts it from a wordmark beside it (Liga MX's ball).
            int[] cols = leftBand(p, w, h);
            if (cols != null) {
                for (int y = 0; y < h; y++) java.util.Arrays.fill(p, y * w + cols[1], y * w + w, 0);
                // and that part's top, where a wordmark runs on under it
                int[] rows = topBand(p, w, h, 1);
                if (rows != null) {
                    for (int y = 0; y < h; y++) if (y < rows[0] || y >= rows[1]) java.util.Arrays.fill(p, y * w, y * w + w, 0);
                }
            }
        }
        return count(p) < w * h * 0.004f ? null : p;
    }

    /**
     * Pure: the rows of a logo's top part ({first, past its last}) when a clear gap parts it from what is under it (a
     * wordmark), the top part being at least a third of the whole; null when nothing parts it.
     */
    static int[] topBand(int[] p, int w, int h) {
        return topBand(p, w, h, Math.max(2, Math.round(h * 0.02f)));
    }

    /** As topBand, the gap at least need rows. */
    static int[] topBand(int[] p, int w, int h, int need) {
        boolean[] full = new boolean[h];
        int first = -1, last = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((p[y * w + x] >>> 24) > 40) {
                    full[y] = true;
                    break;
                }
            }
            if (full[y]) {
                if (first < 0) first = y;
                last = y;
            }
        }
        if (first < 0) return null;
        int total = last - first + 1, run = 0;
        for (int y = first; y <= last; y++) {
            if (!full[y]) {
                run++;
                int gapStart = y - run + 1;
                if (run >= need && gapStart - first >= total / 3f) return new int[]{first, gapStart};
            } else {
                run = 0;
            }
        }
        return null;
    }

    /** Pure: the columns of a logo's left part ({first, past its last}) when a clear gap parts it from what is beside it. */
    static int[] leftBand(int[] p, int w, int h) {
        boolean[] full = new boolean[w];
        int first = -1, last = -1;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if ((p[y * w + x] >>> 24) > 40) {
                    full[x] = true;
                    break;
                }
            }
            if (full[x]) {
                if (first < 0) first = x;
                last = x;
            }
        }
        if (first < 0) return null;
        int total = last - first + 1, need = Math.max(2, Math.round(w * 0.02f)), run = 0;
        for (int x = first; x <= last; x++) {
            if (!full[x]) {
                run++;
                int gapStart = x - run + 1;
                if (run >= need && gapStart - first >= total * 0.12f) return new int[]{first, gapStart};
            } else {
                run = 0;
            }
        }
        return null;
    }

    /** Pure: only its white kept (light and grey), as white; the rest clear. */
    static void whiteOnly(int[] p) {
        for (int i = 0; i < p.length; i++) {
            int c = p[i], a = c >>> 24;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            int lum = (r * 299 + g * 587 + b * 114) / 1000;
            if (a == 0 || lum < 170 || mx == 0 || (mx - mn) * 255 / mx > 70) {
                p[i] = 0;
                continue;
            }
            int keep = Math.min(255, a * Math.min(50, lum - 170) / 50);
            p[i] = keep << 24 | 0xFFFFFF;
        }
    }

    /** Pure: only its coloured parts kept (as they are); its whites, greys and blacks clear. */
    static void colourOnly(int[] p) {
        for (int i = 0; i < p.length; i++) {
            int c = p[i], a = c >>> 24;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            if (a < 40 || mx < 40 || (mx - mn) * 255 / mx < 90) p[i] = 0;
        }
    }

    private static int count(int[] p) {
        int n = 0;
        for (int c : p) if ((c >>> 24) > 40) n++;
        return n;
    }

    // ---- colours ------------------------------------------------------------------------------------------------------

    /** A colour with no see-through. */
    static int solid(int c) {
        return 0xFF000000 | (c & 0xFFFFFF);
    }

    /** Pure: the words' colour on a ground: dark on a light one, white on a dark one. */
    static int ink(int ground) {
        int r = (ground >> 16) & 0xFF, g = (ground >> 8) & 0xFF, b = ground & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 160 ? DARK : INK;
    }

    /** As ink, white on all but the palest grounds (the looks whose words stay white on a club's bright colour). */
    static int inkHi(int ground) {
        int r = (ground >> 16) & 0xFF, g = (ground >> 8) & 0xFF, b = ground & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 205 ? DARK : INK;
    }

    /** A colour this much darker (0: as it is, 1: black). */
    static int darker(int c, float by) {
        float k = 1f - Math.max(0f, Math.min(1f, by));
        return 0xFF000000 | Math.round(((c >> 16) & 0xFF) * k) << 16 | Math.round(((c >> 8) & 0xFF) * k) << 8 | Math.round((c & 0xFF) * k);
    }

    /** A colour this much nearer white (0: as it is, 1: white). */
    static int lighter(int c, float by) {
        float k = Math.max(0f, Math.min(1f, by));
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | Math.round(r + (255 - r) * k) << 16 | Math.round(g + (255 - g) * k) << 8 | Math.round(b + (255 - b) * k);
    }

    /** A colour as words on its own darkened block: itself if light enough, else white. */
    static int light(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 110 ? solid(c) : INK;
    }
}
