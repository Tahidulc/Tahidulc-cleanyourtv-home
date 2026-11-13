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
 * The scoreboard drawn in the way each competition's own broadcasts lay theirs out (the family's own take on each: free
 * typefaces, the competition's logo from the scores' feed, the clubs' colours and crests), the same on the TV's board
 * (BoardBug) and the phone's (OverlayBug), which draw everything else over it (the goal's band, VAR's scan, half time's
 * turn, full time's stamp, the minutes added in their tab). One look a competition; By competition picks the look of the
 * game's own competition, and with it its logo.
 *
 * Every look is laid out from its height: inline ones are one row; the stacked ones (LaLiga, Liga Portugal, Bundesliga,
 * Ligue 1) two; some have a tab above or below the row (the competition's name, the clock, the trophy). tall() is how
 * much taller than one row a look is drawn.
 */
public final class CompBug {
    /** The looks, in the order the pickers show them. */
    public enum Kind {
        PL("Premier League (world feed)", 1.5f), PL_US("Premier League (US)", 1.42f), FACUP("FA Cup", 1.85f), EFL("EFL", 1f),
        UCL("Champions League", 1f), UEL("Europa League", 1f), UECL("Conference League", 1f), SPFL("Scottish Premiership", 1f),
        LALIGA("LaLiga", 2f), SERIEA("Serie A", 1f), BUNDESLIGA("Bundesliga", 2f), LIGUE1("Ligue 1", 2f),
        EREDIVISIE("Eredivisie", 1f), LIGAPORTUGAL("Liga Portugal", 2f), MLS("MLS", 1f), LIGAMX("Liga MX", 1.45f),
        /** The Premier League's own boards of years gone by, with the logo of whatever competition is on. */
        R2004("Retro 2004", 1.9f), R2008("Retro 2008", 1.45f), R2012("Retro 2012", 1.45f), R2016("Retro 2016", 2f);

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
    private static Typeface barlow, khand, outfit;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG),
            img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Rect bounds = new Rect();
    private final Path path = new Path();
    private final PorterDuffColorFilter white = new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);

    public CompBug(Context c) {
        synchronized (CompBug.class) {
            if (barlow == null) barlow = font(c, "broadcast/fonts/BarlowCondensed-700.ttf", Typeface.create("sans-serif-condensed", Typeface.BOLD));
            if (khand == null) khand = font(c, "broadcast/fonts/Khand-700.ttf", barlow);
            if (outfit == null) outfit = font(c, "broadcast/fonts/outfit.ttf", Typeface.DEFAULT_BOLD);
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

    /** Its main row in a board w by h ({top, bottom}): the goal's band and VAR's scan run along it. */
    public static float[] bar(Kind k, float h) {
        float u = row(k, h);
        switch (k) {
            case PL: return new float[]{0, u};
            case PL_US: case LIGAMX: return new float[]{h - u, h};
            case FACUP: return new float[]{u * 0.45f, u * 1.45f};
            case R2004: return new float[]{0, h * 0.76f};
            case R2008: case R2012: return new float[]{0, u};
            case R2016: return new float[]{u * 0.6f, u * 1.6f};
            default: return new float[]{0, h};
        }
    }

    /**
     * The blocks along it, in pixels, for a row u high (the stacked looks: their columns): the same sums for its width
     * and for its drawing, stretched to the width it is given.
     */
    private float[] segs(Kind k, Data d, float u) {
        float code = codeW(d, u, k == Kind.PL || k == Kind.LALIGA || k == Kind.LIGAMX ? outfit : barlow, codeSize(k, u)), clock = clockW(d, u, k);
        boolean crests = d.homeCrest != null || d.awayCrest != null;
        switch (k) {
            case UCL: return new float[]{u * 1.05f, clock, u * 0.14f, code, u * 0.72f, u * 0.72f, code, u * 0.14f};
            case UEL: case UECL: return new float[]{u * 1.05f, clock, code, u * 0.12f, u * 0.7f, u * 0.08f, u * 0.7f, u * 0.12f, code};
            case SERIEA: return new float[]{u * 1.15f, clock, u * 0.35f + code, u * 1.25f, code + u * 0.2f};
            case SPFL: return new float[]{u * 1.05f, code, u * 0.6f, u * 0.6f, code, clock};
            case EFL: return new float[]{u * 0.95f, code, u * 1.55f, code, clock};
            case MLS: return new float[]{u * 0.95f, clock, u * 0.12f, code, u * 0.55f, u * 0.55f, code, u * 0.16f};
            case PL_US: return new float[]{(crests ? u * 0.85f : 0) + code, u * 1.6f, code + (crests ? u * 0.85f : 0), clock};
            case PL: return new float[]{u * 0.35f + code, u * 0.8f, u * 1.3f, u * 0.8f, code + u * 0.35f};
            case FACUP: return new float[]{u * 0.1f, code, u * 0.62f, u * 1.1f, u * 0.62f, code, u * 0.1f};
            case EREDIVISIE: return new float[]{u * 0.95f, code, u * 0.62f, u * 0.62f, code, clock};
            case LIGAMX: return new float[]{u * 0.95f, (crests ? u * 0.8f : 0) + code, u * 0.62f, (crests ? u * 0.8f : 0) + code, u * 0.62f, clock};
            case LALIGA: case LIGAPORTUGAL: return new float[]{Math.max(u * 1.3f, clock), code, u * 1.05f};
            case BUNDESLIGA: return new float[]{Math.max(u * 1.5f, clock), Math.max(u * 1.3f, code), Math.max(u * 1.3f, code)};
            case R2004: {
                float rowH = u * 1.9f * 0.76f / 2f, names = nameW(d, rowH * 0.6f) + rowH * 0.6f;
                return new float[]{u * 1.9f * 0.76f * 0.95f, names, rowH * 1.05f};
            }
            case R2008: case R2012: return new float[]{u * 1.2f, nameW(d, u * 0.42f) + u * 0.5f, u * 1.3f, nameW(d, u * 0.42f) + u * 0.5f + u * 0.3f};
            case R2016: return new float[]{code + u * 0.3f, u * 1.5f, code + u * 0.3f};
            case LIGUE1: default: return new float[]{Math.max(u * 1.3f, clock), code + u * 0.85f + u * 0.12f};
        }
    }

    private float nameW(Data d, float size) {
        return Math.max(measure(shortName(d.homeName, d.home), size, barlow), measure(shortName(d.awayName, d.away), size, barlow));
    }

    private float codeSize(Kind k, float u) {
        switch (k) {
            case LALIGA: case LIGAPORTUGAL: case LIGUE1: return u * 0.5f;
            case BUNDESLIGA: return u * 0.4f;
            case PL: return u * 0.5f;
            default: return u * 0.52f;
        }
    }

    private float codeW(Data d, float u, Typeface tf, float size) {
        return Math.max(measure(d.home, size, tf), measure(d.away, size, tf)) + u * 0.5f;
    }

    private float clockW(Data d, float u, Kind k) {
        float size = (k == Kind.LALIGA || k == Kind.LIGAPORTUGAL || k == Kind.BUNDESLIGA || k == Kind.LIGUE1) ? u * 0.44f : u * 0.46f;
        String s = d.clock == null || d.clock.length() <= 5 ? "88:88" : d.clock;
        return measure(s, size, barlow) + u * 0.5f;
    }

    private float measure(String s, float size, Typeface tf) {
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
            case UEL: case UECL: uefa(cv, d, x, u, sc, k == Kind.UEL ? 0xFFFF6A13 : 0xFF00BE4C); break;
            case SERIEA: serieA(cv, d, x, u, sc); break;
            case SPFL: spfl(cv, d, x, u, sc); break;
            case EFL: efl(cv, d, x, u, sc); break;
            case MLS: mls(cv, d, x, u, sc); break;
            case PL_US: plUs(cv, d, x, w, h, u, sc); break;
            case PL: pl(cv, d, x, w, u, sc); break;
            case FACUP: faCup(cv, d, x, w, u, sc); break;
            case EREDIVISIE: eredivisie(cv, d, x, u, sc); break;
            case LIGAMX: ligaMx(cv, d, x, w, h, u, sc); break;
            case LALIGA: laLiga(cv, d, x, h, sc); break;
            case LIGAPORTUGAL: ligaPortugal(cv, d, x, h, sc); break;
            case BUNDESLIGA: bundesliga(cv, d, x, h, sc); break;
            case R2004: r2004(cv, d, x, h, sc); break;
            case R2008: r2008(cv, d, x, u, h, sc, false); break;
            case R2012: r2008(cv, d, x, u, h, sc, true); break;
            case R2016: r2016(cv, d, x, u, h, sc); break;
            case LIGUE1: default: ligue1(cv, d, x, h, sc); break;
        }
    }

    // ---- the retro boards --------------------------------------------------------------------------------------------

    /** 2004: a silver arrow with the logo, the two clubs' names one above the other with their scores, the clock under it. */
    private void r2004(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float area = h * 0.76f, row = area / 2f, tip = area * 0.25f;
        // The names' rows, tucked under the arrow's point, a navy line round them and between them.
        shade(cv, x[1] - tip, 0, x[3], row, 0xFFFFFFFF, 0xFFDDE2EA, 0);
        shade(cv, x[1] - tip, row, x[3], area, 0xFFF4F6F9, 0xFFD3D9E3, 0);
        rect(cv, x[2], 0, x[3], area, 0x14000000);
        fill.setColor(0xFF1A2A55);
        float line = Math.max(1f, h * 0.018f);
        cv.drawRect(x[1] - tip, row - line / 2f, x[3], row + line / 2f, fill);
        cv.drawRect(x[2] - line / 2f, 0, x[2] + line / 2f, area, fill);
        cv.drawRect(x[1] - tip, 0, x[3], line, fill);
        cv.drawRect(x[1] - tip, area - line, x[3], area, fill);
        cv.drawRect(x[3] - line, 0, x[3], area, fill);
        words(cv, shortName(d.homeName, d.home), x[1] + row * 0.3f, row / 2f, row * 0.6f, x[2] - x[1] - row * 0.4f, 0xFF1A2A55, barlow, -1);
        words(cv, shortName(d.awayName, d.away), x[1] + row * 0.3f, row * 1.5f, row * 0.6f, x[2] - x[1] - row * 0.4f, 0xFF1A2A55, barlow, -1);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[2] + x[3]) / 2f, row / 2f, row * 0.62f, 0xFF1A2A55, barlow);
            scoreOne(cv, d, sc[1], 2, (x[2] + x[3]) / 2f, row * 1.5f, row * 0.62f, 0xFF1A2A55, barlow);
        }
        // The arrow: silver, lighter at its top, its point toward the names, the logo on it.
        path.reset();
        path.moveTo(x[0], 0);
        path.lineTo(x[1] - tip, 0);
        path.lineTo(x[1], area / 2f);
        path.lineTo(x[1] - tip, area);
        path.lineTo(x[0], area);
        path.close();
        fill.setShader(new LinearGradient(0, 0, 0, area, new int[]{0xFFF4F6FA, 0xFFC3CBD8, 0xFF8C97AB}, null, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
        logo(cv, d.logoLight != null ? d.logoLight : d.logo, x[0], 0, x[1] - tip * 0.6f, area, area * 0.08f, false);
        // The clock in its black tab under the arrow.
        float cw = Math.min(x[1] - x[0], clockW(d, row * 1.2f, Kind.R2004));
        clock(cv, d, x[0] + row * 0.3f, area, x[0] + row * 0.3f + cw, h, 0xFF111111, INK, (h - area) * 0.7f, barlow);
    }

    /**
     * 2008 (and 2012, lighter): the logo in a silver ring at its start, each club's name on a white panel, the score on
     * red between them, in a blue frame (2008 deep blue, its end cut at a slant; 2012 lighter, its end rounded, a gloss
     * over its top); the clock in a tab under it.
     */
    private void r2008(Canvas cv, Data d, float[] x, float u, float h, String[] sc, boolean y2012) {
        float right = x[x.length - 1], start = x[0] + u * 0.5f, in = u * 0.09f, tab = h - u;
        // The frame, its end cut (2008) or round (2012).
        path.reset();
        path.moveTo(start, 0);
        if (y2012) {
            r.set(start, 0, right, u);
            path.addRoundRect(r, u * 0.5f, u * 0.5f, Path.Direction.CW);
        } else {
            path.lineTo(right, 0);
            path.lineTo(right - u * 0.3f, u);
            path.lineTo(start, u);
            path.close();
        }
        fill.setShader(new LinearGradient(0, 0, 0, u, y2012 ? new int[]{0xFF7FA6EE, 0xFF2D5FC4, 0xFF1A3F94} : new int[]{0xFF3A64C8, 0xFF143A92, 0xFF0A2463},
                null, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
        // The white panels with the names, the red one with the score.
        shade(cv, x[1], in, x[2], u - in, 0xFFFFFFFF, y2012 ? 0xFFE3E9F3 : 0xFFD8DEE8, 0);
        shade(cv, x[3], in, x[4] - u * (y2012 ? 0.45f : 0.42f), u - in, 0xFFFFFFFF, y2012 ? 0xFFE3E9F3 : 0xFFD8DEE8, 0);
        shade(cv, x[2] + in * 0.5f, in, x[3] - in * 0.5f, u - in, 0xFFE2343F, 0xFF9E1018, 0);
        words(cv, shortName(d.homeName, d.home), (x[1] + x[2]) / 2f, u / 2f, u * 0.42f, x[2] - x[1] - u * 0.2f, 0xFF0E2357, barlow, 0);
        words(cv, shortName(d.awayName, d.away), (x[3] + x[4] - u * 0.4f) / 2f, u / 2f, u * 0.42f, x[4] - x[3] - u * 0.6f, 0xFF0E2357, barlow, 0);
        if (sc != null) {
            float mid = (x[2] + x[3]) / 2f;
            scoreOne(cv, d, sc[0], 1, mid - u * 0.36f, u / 2f, u * 0.5f, INK, barlow);
            words(cv, "-", mid, u / 2f, u * 0.44f, 0, INK, barlow, 0);
            scoreOne(cv, d, sc[1], 2, mid + u * 0.36f, u / 2f, u * 0.5f, INK, barlow);
        } else {
            words(cv, d.score, (x[2] + x[3]) / 2f, u / 2f, u * 0.36f, x[3] - x[2], INK, barlow, 0);
        }
        if (y2012) {
            fill.setShader(new LinearGradient(0, 0, 0, u * 0.5f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            r.set(start, 0, right, u * 0.5f);
            cv.drawRoundRect(r, u * 0.5f, u * 0.25f, fill);
            fill.setShader(null);
        }
        // The logo in its silver ring, standing a little over the frame's start.
        float cx = x[0] + u * 0.6f, cy = u / 2f, ring = u * 0.6f;
        fill.setShader(new LinearGradient(0, cy - ring, 0, cy + ring, new int[]{0xFFF7F9FC, 0xFFB9C3D3, 0xFF7E8AA2}, null, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, ring, fill);
        fill.setShader(null);
        fill.setColor(0xFFFFFFFF);
        cv.drawCircle(cx, cy, ring * 0.82f, fill);
        logo(cv, d.logoLight != null ? d.logoLight : d.logo, cx - ring * 0.8f, cy - ring * 0.8f, cx + ring * 0.8f, cy + ring * 0.8f, ring * 0.14f, false);
        // The clock in its tab under the names.
        float cw = clockW(d, u, Kind.R2008), tl = x[1] + u * 0.1f;
        int tabColour = d.stoppage ? RED : y2012 ? 0xFF2156C6 : 0xFF0B2A6F;
        r.set(tl, u - u * 0.05f, tl + cw, u + tab);
        fill.setColor(tabColour);
        cv.drawRoundRect(r, tab * 0.25f, tab * 0.25f, fill);
        words(cv, d.clock, tl + cw / 2f, u + tab / 2f - u * 0.02f, tab * 0.72f, cw * 0.9f, INK, barlow, 0);
    }

    /** 2016: the logo over the middle, each club's code on purple, the score on green between them, the clock in white under it. */
    private void r2016(Canvas cv, Data d, float[] x, float u, float h, String[] sc) {
        float top = u * 0.6f, bottom = top + u, mid = (x[1] + x[2]) / 2f;
        rect(cv, x[0], top, x[1], bottom, 0xFF38003C);
        rect(cv, x[2], top, x[3], bottom, 0xFF38003C);
        rect(cv, x[1], top, x[2], bottom, 0xFF00FF85);
        words(cv, d.home, (x[0] + x[1]) / 2f, top + u / 2f, u * 0.5f, x[1] - x[0] - u * 0.2f, INK, barlow, 0);
        words(cv, d.away, (x[2] + x[3]) / 2f, top + u / 2f, u * 0.5f, x[3] - x[2] - u * 0.2f, INK, barlow, 0);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, mid - u * 0.38f, top + u / 2f, u * 0.56f, 0xFF38003C, barlow);
            words(cv, "-", mid, top + u / 2f, u * 0.5f, 0, 0xFF38003C, barlow, 0);
            scoreOne(cv, d, sc[1], 2, mid + u * 0.38f, top + u / 2f, u * 0.56f, 0xFF38003C, barlow);
        } else {
            words(cv, d.score, mid, top + u / 2f, u * 0.4f, x[2] - x[1], 0xFF38003C, barlow, 0);
        }
        // The logo over the score, in its own colours as it stood then.
        logo(cv, d.logoLight != null ? d.logoLight : d.logo, mid - u * 0.7f, 0, mid + u * 0.7f, top + u * 0.24f, 0f, false);
        float cw = clockW(d, u, Kind.R2016), tb = h - bottom;
        rect(cv, mid - cw / 2f, bottom, mid + cw / 2f, h, d.stoppage ? RED : 0xFFFFFFFF);
        words(cv, d.clock, mid, bottom + tb / 2f, tb * 0.72f, cw * 0.9f, d.stoppage ? INK : 0xFF1A1A1A, barlow, 0);
    }

    // ---- the looks ------------------------------------------------------------------------------------------------

    private void ucl(Canvas cv, Data d, float[] x, float u, String[] sc) {
        float right = x[x.length - 1];
        shade(cv, 0, 0, right, u, 0xFF102476, 0xFF081452, u * 0.06f);
        rect(cv, x[0], 0, x[1], u, 0xFF14298C);
        logo(cv, d.logo, x[0], 0, x[1], u, u * 0.16f, true);
        clock(cv, d, x[1], 0, x[2], u, 0xFFF3F5FB, 0xFF0D1E6E, u * 0.46f, barlow);
        rect(cv, x[2], 0, x[3], u, solid(d.homeColour));
        words(cv, d.home, (x[3] + x[4]) / 2f, u / 2f, u * 0.52f, x[4] - x[3], INK, outfit, 0);
        score(cv, d, sc, x[4], x[6], 0, u, u * 0.56f, INK, outfit);
        fill.setColor(0x40FFFFFF);
        cv.drawRect(x[5] - u * 0.015f, u * 0.28f, x[5] + u * 0.015f, u * 0.72f, fill);
        words(cv, d.away, (x[6] + x[7]) / 2f, u / 2f, u * 0.52f, x[7] - x[6], INK, outfit, 0);
        rect(cv, x[7], 0, x[8], u, solid(d.awayColour));
    }

    private void uefa(Canvas cv, Data d, float[] x, float u, String[] sc, int accent) {
        float right = x[x.length - 1];
        shade(cv, 0, 0, right, u, 0xFF1B1D21, 0xFF0D0E10, u * 0.05f);
        logo(cv, d.logo, x[0], 0, x[1], u, u * 0.14f, false);
        clock(cv, d, x[1], 0, x[2], u, 0, INK, u * 0.46f, barlow);
        rect(cv, x[1] + u * 0.1f, u * 0.84f, x[2] - u * 0.1f, u * 0.92f, d.stoppage ? RED : accent);
        words(cv, d.home, (x[2] + x[3]) / 2f, u / 2f, u * 0.52f, x[3] - x[2], INK, barlow, 0);
        rect(cv, x[3] + u * 0.03f, u * 0.2f, x[4] - u * 0.03f, u * 0.8f, solid(d.homeColour));
        if (sc != null) {
            rect(cv, x[4], u * 0.14f, x[5], u * 0.86f, 0xFFF2F2F2);
            rect(cv, x[6], u * 0.14f, x[7], u * 0.86f, 0xFFF2F2F2);
            scoreOne(cv, d, sc[0], 1, (x[4] + x[5]) / 2f, u / 2f, u * 0.56f, DARK, barlow);
            scoreOne(cv, d, sc[1], 2, (x[6] + x[7]) / 2f, u / 2f, u * 0.56f, DARK, barlow);
        } else {
            words(cv, d.score, (x[4] + x[7]) / 2f, u / 2f, u * 0.46f, x[7] - x[4], INK, barlow, 0);
        }
        rect(cv, x[7] + u * 0.03f, u * 0.2f, x[8] - u * 0.03f, u * 0.8f, solid(d.awayColour));
        words(cv, d.away, (x[8] + x[9]) / 2f, u / 2f, u * 0.52f, x[9] - x[8], INK, barlow, 0);
    }

    private void serieA(Canvas cv, Data d, float[] x, float u, String[] sc) {
        float right = x[x.length - 1], slant = u * 0.35f;
        // The light pill under the logo and the clock, the navy block over its right with a slanted edge.
        r.set(x[0] + u * 0.1f, u * 0.1f, x[3] + u * 0.2f, u * 0.9f);
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFFFFFFF, 0xFFDDE4F0, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, u * 0.4f, u * 0.4f, fill);
        fill.setShader(null);
        path.reset();
        path.moveTo(x[2] + slant, u * 0.1f);
        path.lineTo(right - u * 0.08f, u * 0.1f);
        path.lineTo(right - u * 0.08f - slant * 0.4f, u * 0.9f);
        path.lineTo(x[2], u * 0.9f);
        path.close();
        fill.setShader(new LinearGradient(0, u * 0.1f, 0, u * 0.9f, 0xFF15307A, 0xFF0A1A48, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
        // The logo in its own white square, a little over the pill.
        r.set(x[0], 0, x[1], u);
        fill.setColor(0xFFFFFFFF);
        cv.drawRoundRect(r, u * 0.18f, u * 0.18f, fill);
        logo(cv, d.logoLight != null ? d.logoLight : d.logo, x[0], 0, x[1], u, u * 0.12f, false);
        clock(cv, d, x[1], u * 0.1f, x[2], u * 0.9f, 0, 0xFF0E2A6B, u * 0.46f, barlow);
        words(cv, d.home, x[2] + slant + (x[3] - x[2] - slant) / 2f, u / 2f, u * 0.48f, x[3] - x[2] - slant, INK, barlow, 0);
        if (sc != null) {
            float mid = (x[3] + x[4]) / 2f;
            scoreOne(cv, d, sc[0], 1, mid - u * 0.36f, u / 2f, u * 0.5f, INK, barlow);
            words(cv, "-", mid, u / 2f, u * 0.46f, 0, INK, barlow, 0);
            scoreOne(cv, d, sc[1], 2, mid + u * 0.36f, u / 2f, u * 0.5f, INK, barlow);
        } else {
            words(cv, d.score, (x[3] + x[4]) / 2f, u / 2f, u * 0.42f, x[4] - x[3], INK, barlow, 0);
        }
        words(cv, d.away, (x[4] + x[5] - u * 0.2f) / 2f, u / 2f, u * 0.48f, x[5] - x[4] - u * 0.2f, INK, barlow, 0);
    }

    private void spfl(Canvas cv, Data d, float[] x, float u, String[] sc) {
        rect(cv, x[0], 0, x[1], u, 0xFFFFFFFF);
        logo(cv, d.logoLight != null ? d.logoLight : d.logo, x[0], 0, x[1], u, u * 0.14f, false);
        team(cv, d.home, x[1], 0, x[2], u, solid(d.homeColour), u * 0.52f, barlow);
        shade(cv, x[2], 0, x[4], u, 0xFF12293A, 0xFF08161F, 0);
        score(cv, d, sc, x[2], x[4], 0, u, u * 0.56f, INK, barlow);
        team(cv, d.away, x[4], 0, x[5], u, solid(d.awayColour), u * 0.52f, barlow);
        clock(cv, d, x[5], 0, x[6], u, 0xFF0A0F14, INK, u * 0.46f, barlow);
    }

    private void efl(Canvas cv, Data d, float[] x, float u, String[] sc) {
        rect(cv, x[0], 0, x[1], u, 0xFF12163A);
        logo(cv, d.logo, x[0], 0, x[1], u, u * 0.16f, false);
        team(cv, d.home, x[1], 0, x[2], u, solid(d.homeColour), u * 0.5f, outfit);
        rect(cv, x[2], 0, x[3], u, 0xFFFFFFFF);
        if (sc != null) {
            float mid = (x[2] + x[3]) / 2f;
            scoreOne(cv, d, sc[0], 1, mid - u * 0.42f, u / 2f, u * 0.52f, 0xFF12163A, outfit);
            words(cv, "-", mid, u / 2f, u * 0.5f, 0, 0xFF12163A, outfit, 0);
            scoreOne(cv, d, sc[1], 2, mid + u * 0.42f, u / 2f, u * 0.52f, 0xFF12163A, outfit);
        } else {
            words(cv, d.score, (x[2] + x[3]) / 2f, u / 2f, u * 0.44f, x[3] - x[2], 0xFF12163A, outfit, 0);
        }
        team(cv, d.away, x[3], 0, x[4], u, solid(d.awayColour), u * 0.5f, outfit);
        clock(cv, d, x[4], 0, x[5], u, 0xFFF4F5F8, 0xFF12163A, u * 0.46f, outfit);
    }

    private void mls(Canvas cv, Data d, float[] x, float u, String[] sc) {
        float right = x[x.length - 1];
        shade(cv, 0, 0, right, u, 0xFFF6F7F9, 0xFFD6DBE2, 0);
        rect(cv, x[0], 0, x[1], u, 0xFF1E2229);
        logo(cv, d.logo, x[0], 0, x[1], u, u * 0.16f, false);
        clock(cv, d, x[1], 0, x[2], u, 0xFF2B2F36, INK, u * 0.44f, outfit);
        rect(cv, x[2], 0, x[3], u, solid(d.homeColour));
        words(cv, d.home, (x[3] + x[4]) / 2f, u / 2f, u * 0.46f, x[4] - x[3], 0xFF22262D, outfit, 0);
        score(cv, d, sc, x[4], x[6], 0, u, u * 0.5f, 0xFF22262D, outfit);
        words(cv, d.away, (x[6] + x[7]) / 2f, u / 2f, u * 0.46f, x[7] - x[6], 0xFF22262D, outfit, 0);
        rect(cv, x[7], 0, x[8], u, solid(d.awayColour));
    }

    private void plUs(Canvas cv, Data d, float[] x, float w, float h, float u, String[] sc) {
        float top = h - u, tab = h - u;
        // Its tab over the row: the competition's logo and name.
        String name = (d.comp == null || d.comp.isEmpty() ? Kind.PL_US.label.replace(" (US)", "") : d.comp).toUpperCase(Locale.ROOT);
        float ts = tab * 0.62f, lw = d.logo != null ? tab * 1.1f : 0f, tw = measure(name, ts, barlow) + tab * 0.7f + lw;
        rect(cv, 0, 0, Math.min(w * 0.75f, tw), tab, 0xFF1B1C21);
        if (d.logo != null) logo(cv, d.logo, tab * 0.15f, 0, tab * 0.15f + lw, tab, tab * 0.1f, true);
        words(cv, name, lw + tab * 0.35f, tab / 2f, ts, Math.min(w * 0.75f, tw) - lw - tab * 0.5f, INK, barlow, -1);
        boolean crests = d.homeCrest != null || d.awayCrest != null;
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        rect(cv, x[0], top, x[1], h, hc);
        float cx = x[0];
        if (crests) {
            crest(cv, d.homeCrest, x[0] + u * 0.08f, top + u * 0.12f, u * 0.76f);
            cx += u * 0.85f;
        }
        words(cv, d.home, (cx + x[1]) / 2f, top + u / 2f, u * 0.52f, x[1] - cx, ink(hc), barlow, 0);
        rect(cv, x[1], top, x[2], h, 0xFF1B1C21);
        if (sc != null) {
            float mid = (x[1] + x[2]) / 2f;
            scoreOne(cv, d, sc[0], 1, mid - u * 0.42f, top + u / 2f, u * 0.58f, INK, barlow);
            words(cv, "-", mid, top + u / 2f, u * 0.5f, 0, INK, barlow, 0);
            scoreOne(cv, d, sc[1], 2, mid + u * 0.42f, top + u / 2f, u * 0.58f, INK, barlow);
        } else {
            words(cv, d.score, (x[1] + x[2]) / 2f, top + u / 2f, u * 0.44f, x[2] - x[1], INK, barlow, 0);
        }
        rect(cv, x[2], top, x[3], h, ac);
        float ce = x[3];
        if (crests) {
            crest(cv, d.awayCrest, x[3] - u * 0.84f, top + u * 0.12f, u * 0.76f);
            ce -= u * 0.85f;
        }
        words(cv, d.away, (x[2] + ce) / 2f, top + u / 2f, u * 0.52f, ce - x[2], ink(ac), barlow, 0);
        clock(cv, d, x[3], top, x[4], h, 0xFFF4F5F8, 0xFF1B1C21, u * 0.46f, barlow);
    }

    private void pl(Canvas cv, Data d, float[] x, float w, float u, String[] sc) {
        float right = x[x.length - 1], arrow = u * 0.3f;
        // The row, its ends pointed.
        path.reset();
        path.moveTo(arrow, 0);
        path.lineTo(right - arrow, 0);
        path.lineTo(right, u / 2f);
        path.lineTo(right - arrow, u);
        path.lineTo(arrow, u);
        path.lineTo(0, u / 2f);
        path.close();
        fill.setShader(new LinearGradient(0, 0, right, 0, new int[]{0xFF4A0A55, 0xFF38003C, 0xFF4A0A55}, null, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
        words(cv, d.home, (x[0] + arrow + x[1]) / 2f, u / 2f, u * 0.5f, x[1] - x[0] - arrow, INK, outfit, 0);
        float mid = (x[2] + x[3]) / 2f, ring = u * 0.62f;
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[1] + x[2]) / 2f, u / 2f, u * 0.74f, INK, outfit);
            scoreOne(cv, d, sc[1], 2, (x[3] + x[4]) / 2f, u / 2f, u * 0.74f, INK, outfit);
        } else {
            words(cv, d.score, mid, u * 1.25f, u * 0.32f, 0, INK, outfit, 0);
        }
        words(cv, d.away, (x[4] + x[5] - arrow) / 2f, u / 2f, u * 0.5f, x[5] - x[4] - arrow, INK, outfit, 0);
        // The logo in a ring in the middle, standing a little over the row.
        fill.setColor(0xFF38003C);
        cv.drawCircle(mid, u / 2f, ring, fill);
        fill.setColor(0x33FFFFFF);
        cv.drawCircle(mid, u / 2f, ring * 0.92f, fill);
        logo(cv, d.logo, mid - ring, u / 2f - ring, mid + ring, u / 2f + ring, ring * 0.32f, true);
        // The clock in its tab under the middle.
        float cw = clockW(d, u, Kind.PL) * 0.85f, tt = u * 0.5f;
        r.set(mid - cw / 2f, u - u * 0.04f, mid + cw / 2f, u + tt);
        fill.setColor(d.stoppage ? RED : 0xFF38003C);
        cv.drawRoundRect(r, tt * 0.3f, tt * 0.3f, fill);
        words(cv, d.clock, mid, u + tt / 2f - u * 0.02f, tt * 0.7f, cw, INK, outfit, 0);
    }

    private void faCup(Canvas cv, Data d, float[] x, float w, float u, String[] sc) {
        float top = u * 0.45f, bottom = top + u, right = x[x.length - 1], mid = (x[3] + x[4]) / 2f;
        shade(cv, 0, top, right, bottom, 0xFF16224A, 0xFF0A1027, 0);
        rect(cv, x[0], top, x[1], bottom, solid(d.homeColour));
        rect(cv, x[6], top, x[7], bottom, solid(d.awayColour));
        words(cv, d.home, (x[1] + x[2]) / 2f, top + u / 2f, u * 0.48f, x[2] - x[1], INK, barlow, 0);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[2] + x[3]) / 2f, top + u / 2f, u * 0.56f, INK, barlow);
            scoreOne(cv, d, sc[1], 2, (x[4] + x[5]) / 2f, top + u / 2f, u * 0.56f, INK, barlow);
        }
        words(cv, d.away, (x[5] + x[6]) / 2f, top + u / 2f, u * 0.48f, x[6] - x[5], INK, barlow, 0);
        // The trophy (the competition's logo) standing over the middle, the clock in its red tab under it.
        logo(cv, d.logo, mid - u * 0.55f, 0, mid + u * 0.55f, top + u * 0.62f, u * 0.02f, false);
        float cw = clockW(d, u, Kind.FACUP) * 0.9f, tt = u * 0.4f;
        rect(cv, mid - cw / 2f, bottom, mid + cw / 2f, bottom + tt, d.stoppage ? RED : 0xFFC8102E);
        words(cv, d.clock, mid, bottom + tt / 2f, tt * 0.78f, cw, INK, barlow, 0);
        if (sc == null) words(cv, d.score, mid, top + u * 0.78f, u * 0.3f, u * 1.1f, INK, barlow, 0);
    }

    private void eredivisie(Canvas cv, Data d, float[] x, float u, String[] sc) {
        float right = x[x.length - 1];
        shade(cv, 0, 0, right, u, 0xFF262A31, 0xFF16191E, 0);
        logo(cv, d.logo, x[0], 0, x[1], u, u * 0.16f, false);
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        rect(cv, x[1], 0, x[2], u, darker(hc, 0.55f));
        words(cv, d.home, (x[1] + x[2]) / 2f, u / 2f, u * 0.5f, x[2] - x[1], light(hc), barlow, 0);
        if (sc != null) {
            rect(cv, x[2] + u * 0.04f, u * 0.12f, x[3] - u * 0.02f, u * 0.88f, 0xFFE9EAEC);
            rect(cv, x[3] + u * 0.02f, u * 0.12f, x[4] - u * 0.04f, u * 0.88f, 0xFFD5D7DB);
            scoreOne(cv, d, sc[0], 1, (x[2] + x[3]) / 2f, u / 2f, u * 0.56f, DARK, barlow);
            scoreOne(cv, d, sc[1], 2, (x[3] + x[4]) / 2f, u / 2f, u * 0.56f, DARK, barlow);
        } else {
            words(cv, d.score, (x[2] + x[4]) / 2f, u / 2f, u * 0.44f, x[4] - x[2], INK, barlow, 0);
        }
        rect(cv, x[4], 0, x[5], u, darker(ac, 0.55f));
        words(cv, d.away, (x[4] + x[5]) / 2f, u / 2f, u * 0.5f, x[5] - x[4], light(ac), barlow, 0);
        clock(cv, d, x[5] + u * 0.06f, u * 0.12f, x[6] - u * 0.06f, u * 0.88f, 0xFFF4F5F7, DARK, u * 0.44f, barlow);
    }

    private void ligaMx(Canvas cv, Data d, float[] x, float w, float h, float u, String[] sc) {
        float top = h - u, head = h - u, right = x[x.length - 1];
        String name = (d.comp == null || d.comp.isEmpty() ? Kind.LIGAMX.label : d.comp).toUpperCase(Locale.ROOT);
        rect(cv, 0, 0, right, head, 0xE6181A1F);
        words(cv, name, right / 2f, head / 2f, head * 0.6f, right - head, 0xFFE4E6EA, outfit, 0);
        shade(cv, 0, top, right, h, 0xFF2A2D35, 0xFF1C1E24, 0);
        logo(cv, d.logo, x[0], top, x[1], h, u * 0.18f, false);
        boolean crests = d.homeCrest != null || d.awayCrest != null;
        float hx = x[1];
        if (crests) {
            crest(cv, d.homeCrest, hx + u * 0.08f, top + u * 0.15f, u * 0.7f);
            hx += u * 0.8f;
        }
        words(cv, d.home, (hx + x[2]) / 2f, top + u / 2f, u * 0.46f, x[2] - hx, INK, outfit, 0);
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        rect(cv, x[2], top + u * 0.14f, x[3], h - u * 0.14f, hc);
        float ax = x[3];
        if (crests) {
            crest(cv, d.awayCrest, ax + u * 0.08f, top + u * 0.15f, u * 0.7f);
            ax += u * 0.8f;
        }
        words(cv, d.away, (ax + x[4]) / 2f, top + u / 2f, u * 0.46f, x[4] - ax, INK, outfit, 0);
        rect(cv, x[4], top + u * 0.14f, x[5], h - u * 0.14f, ac);
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[2] + x[3]) / 2f, top + u / 2f, u * 0.5f, ink(hc), outfit);
            scoreOne(cv, d, sc[1], 2, (x[4] + x[5]) / 2f, top + u / 2f, u * 0.5f, ink(ac), outfit);
        }
        clock(cv, d, x[5], top, x[6], h, 0, INK, u * 0.44f, outfit);
    }

    private void laLiga(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float half = h / 2f;
        rect(cv, x[0], 0, x[1], half, 0xFFFF4B44);
        logo(cv, d.logo, x[0], 0, x[1], half, half * 0.16f, true);
        clock(cv, d, x[0], half, x[1], h, 0xFFF4F5F7, DARK, half * 0.46f, outfit);
        rect(cv, x[1], 0, x[2], h, 0xFF121212);
        words(cv, d.home, (x[1] + x[2]) / 2f, half / 2f, half * 0.5f, x[2] - x[1], INK, outfit, 0);
        words(cv, d.away, (x[1] + x[2]) / 2f, half * 1.5f, half * 0.5f, x[2] - x[1], INK, outfit, 0);
        rect(cv, x[2], 0, x[3], h, 0xFFF7F7F8);
        fill.setColor(0x33000000);
        cv.drawRect(x[2] + half * 0.25f, half - half * 0.015f, x[3] - half * 0.25f, half + half * 0.015f, fill);
        stackScores(cv, d, sc, (x[2] + x[3]) / 2f, half, half * 0.7f, DARK, outfit);
    }

    private void ligaPortugal(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float half = h / 2f;
        rect(cv, x[0], 0, x[1], half * 1.25f, 0xFFFFFFFF);
        logo(cv, d.logoLight != null ? d.logoLight : d.logo, x[0], 0, x[1], half * 1.25f, half * 0.16f, false);
        clock(cv, d, x[0], half * 1.25f, x[1], h, 0xFFE30613, INK, half * 0.46f, barlow);
        rect(cv, x[1], 0, x[2], h, 0xFF0A1E4D);
        words(cv, d.home, (x[1] + x[2]) / 2f, half / 2f, half * 0.5f, x[2] - x[1], INK, barlow, 0);
        words(cv, d.away, (x[1] + x[2]) / 2f, half * 1.5f, half * 0.5f, x[2] - x[1], INK, barlow, 0);
        rect(cv, x[2], 0, x[3], h, 0xFFFFFFFF);
        fill.setColor(0x220A1E4D);
        cv.drawRect(x[2] + half * 0.25f, half - half * 0.015f, x[3] - half * 0.25f, half + half * 0.015f, fill);
        stackScores(cv, d, sc, (x[2] + x[3]) / 2f, half, half * 0.66f, 0xFF0A1E4D, barlow);
    }

    private void bundesliga(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float split = h * 0.42f;
        int hc = solid(d.homeColour), ac = solid(d.awayColour);
        clock(cv, d, x[0], 0, x[1], split, 0xFF1A1A1A, INK, split * 0.6f, barlow);
        rect(cv, x[0], split, x[1], h, 0xFFF4F4F4);
        logo(cv, d.logoLight != null ? d.logoLight : d.logo, x[0], split, x[1], h, (h - split) * 0.14f, false);
        rect(cv, x[1], 0, x[2], h, hc);
        rect(cv, x[2], 0, x[3], h, ac);
        fill.setColor(0x26000000);
        cv.drawRect(x[1], split - Math.max(1f, h * 0.012f), x[3], split + Math.max(1f, h * 0.012f), fill);
        words(cv, d.home, (x[1] + x[2]) / 2f, split / 2f, split * 0.62f, x[2] - x[1], ink(hc), barlow, 0);
        words(cv, d.away, (x[2] + x[3]) / 2f, split / 2f, split * 0.62f, x[3] - x[2], ink(ac), barlow, 0);
        float cy = split + (h - split) / 2f, size = (h - split) * 0.82f;
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, (x[1] + x[2]) / 2f, cy, size, ink(hc), barlow);
            scoreOne(cv, d, sc[1], 2, (x[2] + x[3]) / 2f, cy, size, ink(ac), barlow);
        } else {
            words(cv, d.score, (x[1] + x[3]) / 2f, cy, size * 0.5f, x[3] - x[1], ink(hc), barlow, 0);
        }
    }

    private void ligue1(Canvas cv, Data d, float[] x, float h, String[] sc) {
        float half = h / 2f, logoB = h * 0.62f;
        rect(cv, x[0], 0, x[1], logoB, 0xFF2654D8);
        logo(cv, d.logo, x[0], 0, x[1], logoB, logoB * 0.14f, true);
        clock(cv, d, x[0], logoB, x[1], h, 0xFFF4F5F7, DARK, (h - logoB) * 0.62f, khand);
        rect(cv, x[1], 0, x[2], h, 0xFF121418);
        float bar = half * 0.12f, codeR = x[2] - bar - half * 0.85f;
        rect(cv, x[2] - bar, 0, x[2], half - half * 0.03f, solid(d.homeColour));
        rect(cv, x[2] - bar, half + half * 0.03f, x[2], h, solid(d.awayColour));
        words(cv, d.home, x[1] + half * 0.22f, half / 2f, half * 0.5f, codeR - x[1] - half * 0.22f, INK, khand, -1);
        words(cv, d.away, x[1] + half * 0.22f, half * 1.5f, half * 0.5f, codeR - x[1] - half * 0.22f, INK, khand, -1);
        float sx = x[2] - bar - half * 0.42f;
        if (sc != null) {
            scoreOne(cv, d, sc[0], 1, sx, half / 2f, half * 0.56f, INK, khand);
            scoreOne(cv, d, sc[1], 2, sx, half * 1.5f, half * 0.56f, INK, khand);
        }
        fill.setColor(0x22FFFFFF);
        cv.drawRect(x[1] + half * 0.15f, half - Math.max(1f, h * 0.008f), x[2] - bar, half + Math.max(1f, h * 0.008f), fill);
    }

    // ---- small parts ------------------------------------------------------------------------------------------------

    private void rect(Canvas cv, float l, float t, float rr, float b, int colour) {
        fill.setShader(null);
        fill.setColor(colour);
        cv.drawRect(l, t, rr, b, fill);
    }

    /** A block lit from its top, darker at its foot (round: its corners, 0 square). */
    private void shade(Canvas cv, float l, float t, float rr, float b, int top, int foot, float round) {
        fill.setShader(new LinearGradient(0, t, 0, b, top, foot, Shader.TileMode.CLAMP));
        r.set(l, t, rr, b);
        if (round > 0) cv.drawRoundRect(r, round, round, fill);
        else cv.drawRect(r, fill);
        fill.setShader(null);
    }

    /** A club's block in its colour, its code on it in whichever of white or dark reads. */
    private void team(Canvas cv, String code, float l, float t, float rr, float b, int colour, float size, Typeface tf) {
        rect(cv, l, t, rr, b, colour);
        words(cv, code, (l + rr) / 2f, (t + b) / 2f, size, rr - l - (b - t) * 0.2f, ink(colour), tf, 0);
    }

    /** The clock in its box (ground 0: none), red and staying red in added time. */
    private void clock(Canvas cv, Data d, float l, float t, float rr, float b, int ground, int ink, float size, Typeface tf) {
        int g = d.stoppage ? RED : ground;
        if (g != 0) rect(cv, l, t, rr, b, g);
        words(cv, d.clock, (l + rr) / 2f, (t + b) / 2f, size, (rr - l) * 0.9f, d.stoppage ? INK : ink, tf, 0);
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

    /** A crest fitted in a square at (l, t). */
    private void crest(Canvas cv, Bitmap b, float l, float t, float size) {
        if (b == null || b.isRecycled()) return;
        float k = Math.min(size / b.getWidth(), size / b.getHeight()), bw = b.getWidth() * k, bh = b.getHeight() * k;
        r.set(l + (size - bw) / 2f, t + (size - bh) / 2f, l + (size + bw) / 2f, t + (size + bh) / 2f);
        img.setColorFilter(null);
        cv.drawBitmap(b, null, r, img);
    }

    /** The competition's logo fitted in its box (pad all round), as it is or in white throughout. */
    private void logo(Canvas cv, Bitmap b, float l, float t, float rr, float bt, float pad, boolean inWhite) {
        if (b == null || b.isRecycled()) return;
        float bw = rr - l - 2 * pad, bh = bt - t - 2 * pad;
        if (bw <= 1 || bh <= 1) return;
        float k = Math.min(bw / b.getWidth(), bh / b.getHeight()), w = b.getWidth() * k, h = b.getHeight() * k;
        float cx = (l + rr) / 2f, cy = (t + bt) / 2f;
        r.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        img.setColorFilter(inWhite ? white : null);
        cv.drawBitmap(b, null, r, img);
        img.setColorFilter(null);
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

    /** A colour with no see-through. */
    static int solid(int c) {
        return 0xFF000000 | (c & 0xFFFFFF);
    }

    /** Pure: the words' colour on a ground: dark on a light one, white on a dark one. */
    static int ink(int ground) {
        int r = (ground >> 16) & 0xFF, g = (ground >> 8) & 0xFF, b = ground & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 160 ? DARK : INK;
    }

    /** A colour this much darker (0: as it is, 1: black). */
    static int darker(int c, float by) {
        float k = 1f - Math.max(0f, Math.min(1f, by));
        return 0xFF000000 | Math.round(((c >> 16) & 0xFF) * k) << 16 | Math.round(((c >> 8) & 0xFF) * k) << 8 | Math.round((c & 0xFF) * k);
    }

    /** A colour as words on its own darkened block: itself if light enough, else white. */
    static int light(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 110 ? solid(c) : INK;
    }
}
