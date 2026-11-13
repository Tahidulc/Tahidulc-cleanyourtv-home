package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * The scoreboard for tennis, boxing and UFC in every look the football board has, the same on the TV and the phone:
 * each look's own grounds, blocks, corners, sheen and lettering (Skin), laid out as the sport wants it.
 *
 * <ul>
 *   <li>Tennis, two rows, a player each: their country's colour, flag and name (and seed), the ball by the server,
 *   then a column a set (the winner's games bright, a lost tiebreak's points small beside them), the set in play in the
 *   look's score block, the points in its clock block where they are known.</li>
 *   <li>A fight, one row as the football board's: the red corner, the round in the middle (VS before, how it ended
 *   after), the blue corner, the time left in the round in the clock block. Stacked puts the corners one above the
 *   other with their records; Card is the white card with the round on its blue; Crests and time and Frosted glass
 *   show the fighters' photos (their flags where there are none).</li>
 * </ul>
 *
 * <p>Under it, in a tab of the look, the event and where in it ("WTA FINALS · GROUP STAGE", "UFC 334 · HEAVYWEIGHT").
 * A moment (a set won, the match, a round beginning, the winner) sweeps across it in the side's colour (band). By
 * competition and the competition looks keep their colours, lettering and square or slanted ends; By competition
 * itself, with no look of the sport's own, is Classic.
 */
public final class SportBug {
    static final int INK = 0xFFE6E9F2, SOFT = 0xFFA9B1D6, NAVY = 0xFF0B1640, NAVY_DEEP = 0xFF070C26, CLOCK = 0xFF050816,
            SCORE_TOP = 0xFF2443B4, SCORE_BOTTOM = 0xFF142A78, MINIMAL_GROUND = 0xFF070912, CRESTS_GROUND = 0xFF0A1238,
            STACKED_GROUND = 0xFF0E1330, GOALS = 0xFF1E2A66, CARD_TOP = 0xFFF1F3F7, CARD_FOOT = 0xFFD3D8E1,
            CARD_BLUE = 0xFF2A3DC9, CARD_BLUE_FOOT = 0xFF0F1B7E, BALL = 0xFFD7F23A;

    /** A look's colours and shapes, for these boards. */
    public static final class Skin {
        /** The look it is (the football board's Style name, or a competition look's Kind name). */
        public String name = "CLASSIC";
        int ground = NAVY, groundFoot, ink = INK, dim = SOFT, logo = NAVY_DEEP, mid0 = SCORE_TOP, mid1 = SCORE_BOTTOM,
                midInk = INK, clock = CLOCK, clockInk = INK;
        boolean strips = true, fill, sheen, edge, light, slant, minimal, photos, stacked, card;
        float round = 0.14f;
        Typeface face;
        CompBug.Kind kind;

        /** For what folds out of the board or sits beside it: {ground, ink, quieter ink, block}. */
        public int[] colours() {
            return new int[]{ground, ink, dim, mid0 != 0 && (mid0 >>> 24) == 0xFF ? mid0 : light ? NAVY : 0x40FFFFFF};
        }

        public boolean light() {
            return light;
        }
    }

    private static Typeface house, mont5, mont6, mont7, mont8, inter5, inter6, rubik6, bcond8, bsemi7;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG),
            img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path(), clip = new Path();
    private final PorterDuffColorFilter white = new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN),
            navy = new PorterDuffColorFilter(NAVY, PorterDuff.Mode.SRC_IN);

    /** houseFace: the lettering the app's football board uses for its own looks (null: Outfit from the shared fonts). */
    public SportBug(Context c, Typeface houseFace) {
        synchronized (SportBug.class) {
            if (house == null) house = houseFace != null ? houseFace : heavy(font(c, "broadcast/fonts/outfit.ttf", Typeface.DEFAULT_BOLD));
            if (mont5 == null) mont5 = font(c, "broadcast/fonts/Montserrat-500.ttf", Typeface.DEFAULT);
            if (mont6 == null) mont6 = font(c, "broadcast/fonts/Montserrat-600.ttf", Typeface.DEFAULT_BOLD);
            if (mont7 == null) mont7 = font(c, "broadcast/fonts/Montserrat-700.ttf", Typeface.DEFAULT_BOLD);
            if (mont8 == null) mont8 = font(c, "broadcast/fonts/Montserrat-800.ttf", Typeface.DEFAULT_BOLD);
            if (inter5 == null) inter5 = font(c, "broadcast/fonts/Inter-500.ttf", Typeface.DEFAULT);
            if (inter6 == null) inter6 = font(c, "broadcast/fonts/Inter-600.ttf", Typeface.DEFAULT_BOLD);
            if (rubik6 == null) rubik6 = font(c, "broadcast/fonts/Rubik-600.ttf", Typeface.DEFAULT_BOLD);
            if (bcond8 == null) bcond8 = font(c, "broadcast/fonts/BarlowCondensed-800.ttf", Typeface.create("sans-serif-condensed", Typeface.BOLD));
            if (bsemi7 == null) bsemi7 = font(c, "broadcast/fonts/BarlowSemiCondensed-700.ttf", bcond8);
        }
        text.setFontFeatureSettings("tnum");
        line.setStyle(Paint.Style.STROKE);
    }

    /** The shared Outfit at the football board's weight (800) where the system can set a weight. */
    private static Typeface heavy(Typeface t) {
        if (android.os.Build.VERSION.SDK_INT >= 28 && t != null) return Typeface.create(t, 800, false);
        return t;
    }

    private static Typeface font(Context c, String path, Typeface or) {
        try {
            return Typeface.createFromAsset(c.getAssets(), path);
        } catch (RuntimeException e) {
            return or;
        }
    }

    // ---- the looks ----------------------------------------------------------------------------------------------------

    /**
     * A look's skin: style, the football board's look by its name (CLASSIC, GLASS, LIGHT, CLUB, MINIMAL, CRESTS, STACKED,
     * CARD, FROSTED, COMPETITION, or a competition look's); kind, the competition look it is (null for the board's own).
     */
    public static Skin skin(String style, CompBug.Kind kind) {
        Skin s = new Skin();
        String st = style == null ? "CLASSIC" : style;
        s.name = kind != null ? kind.name() : st;
        if (kind != null) {
            int[] c = CompBug.strip(kind);
            s.kind = kind;
            s.ground = c[0];
            s.ink = c[1];
            s.dim = c[2];
            s.mid0 = s.mid1 = c[3];
            s.logo = mix(c[0], 0xFF000000, 0.35f);
            s.clock = mix(c[0], 0xFF000000, 0.45f);
            s.round = 0.06f;
            s.slant = kind == CompBug.Kind.PL || kind == CompBug.Kind.R2008 || kind == CompBug.Kind.R2012 || kind == CompBug.Kind.UEL
                    || kind == CompBug.Kind.UECL || kind == CompBug.Kind.LIGAMX;
            s.face = faceOf(kind);
            if (kind == CompBug.Kind.R2004) s.mid0 = s.mid1 = 0xFF2B3E73;
            return s;
        }
        s.face = house;
        switch (st) {
            case "GLASS":
                s.ground = 0xFF151B36;
                s.logo = 0;
                s.mid0 = 0x40FFFFFF;
                s.mid1 = 0x1CFFFFFF;
                s.clock = 0x47000000;
                s.dim = 0xFFC3CAE6;
                s.round = 0.5f;
                s.sheen = true;
                break;
            case "LIGHT":
                s.ground = 0xFFE3E7EE;
                s.logo = NAVY;
                s.mid0 = s.mid1 = NAVY;
                s.clock = 0xFFD0D6E2;
                s.ink = s.clockInk = NAVY;
                s.dim = 0xFF5A6280;
                s.light = true;
                break;
            case "CLUB":
                s.ground = 0xFF0E0E10;
                s.logo = 0xFF000000;
                s.mid0 = 0xFF1A1A1C;
                s.mid1 = 0xFF000000;
                s.clock = 0xFF000000;
                s.dim = 0xFFB0B0B8;
                s.strips = false;
                s.fill = true;
                break;
            case "MINIMAL":
                s.ground = MINIMAL_GROUND;
                s.logo = 0;
                s.mid0 = s.mid1 = 0;
                s.clock = 0;
                s.round = 0.25f;
                s.minimal = true;
                break;
            case "CRESTS":
                s.ground = CRESTS_GROUND;
                s.photos = true;
                break;
            case "STACKED":
                s.ground = STACKED_GROUND;
                s.mid0 = s.mid1 = GOALS;
                s.round = 0.08f;
                s.stacked = true;
                break;
            case "CARD":
                s.ground = CARD_TOP;
                s.groundFoot = CARD_FOOT;
                s.logo = 0;
                s.mid0 = CARD_BLUE;
                s.mid1 = CARD_BLUE_FOOT;
                s.clock = 0;
                s.ink = s.clockInk = NAVY;
                s.dim = 0xFF4E5778;
                s.round = 0.08f;
                s.light = true;
                s.card = true;
                break;
            case "FROSTED":
                s.ground = 0xFF9AA5BA;
                s.groundFoot = 0xFF5D677D;
                s.logo = 0;
                s.mid0 = 0x3DFFFFFF;
                s.mid1 = 0x1FFFFFFF;
                s.clock = 0x24000000;
                s.dim = 0xFFE3E8F2;
                s.round = 0.3f;
                s.sheen = true;
                s.edge = true;
                s.photos = true;
                break;
            default:
                // Classic, and By competition (no look of these sports' own).
                s.name = "CLASSIC".equals(st) ? st : "COMPETITION".equals(st) ? st : "CLASSIC";
        }
        return s;
    }

    private static Typeface faceOf(CompBug.Kind k) {
        switch (k) {
            case PL: case LIGAPORTUGAL: case R2016: return mont8;
            case PL_US: case SERIEA: return mont6;
            case FACUP: case UEL: case UECL: case LIGUE1: case LIGAMX: case UCL: return mont7;
            case EFL: return rubik6;
            case SPFL: case EREDIVISIE: case MLS: return inter6;
            case LALIGA: return bcond8;
            case BUNDESLIGA: case R2004: case R2008: case R2012: return bsemi7;
            default: return house;
        }
    }

    /** Pure: a colour mixed toward another by f (0: a, 1: b). */
    static int mix(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | Math.round(ar + (br - ar) * f) << 16 | Math.round(ag + (bg - ag) * f) << 8 | Math.round(ab + (bb - ab) * f);
    }

    /** Pure: white or navy, whichever reads on a colour. */
    static int inkOn(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 165 ? NAVY : 0xFFFFFFFF;
    }

    // ---- size ---------------------------------------------------------------------------------------------------------

    /** The tab's height under the board, for rows u high. */
    public static float tabHeight(float u) {
        return u * 0.62f;
    }

    /** Its size for rows u high: {width, height}, the room for its tab under it included. */
    public float[] size(Skin s, SportBoard b, float u) {
        float w, h;
        if (b.sport == SportBoard.TENNIS) {
            w = tennisWidth(s, b, u);
            h = 2 * u;
        } else if (s.stacked) {
            w = stackedFightWidth(s, b, u);
            h = 2 * u;
        } else {
            w = fightWidth(s, b, u);
            h = u * (s.card ? 1.25f : 1f);
        }
        return new float[]{(float) Math.ceil(w), (float) Math.ceil(h + tabHeight(u) + u * 0.08f)};
    }

    private float measure(String s, float size, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        return text.measureText(s == null ? "" : s);
    }

    private String nameOf(Skin s, SportBoard.Side side) {
        String n = side.shortName == null || side.shortName.isEmpty() ? side.name : side.shortName;
        if (n == null || n.isEmpty()) n = "TBC";
        return s.kind == CompBug.Kind.MLS || s.kind == CompBug.Kind.PL_US ? n : n.toUpperCase(Locale.ROOT);
    }

    private float namesW(Skin s, SportBoard b, float size) {
        return Math.max(measure(nameOf(s, b.a), size, s.face), measure(nameOf(s, b.b), size, s.face));
    }

    private float logoW(Skin s, float u) {
        return s.minimal ? u * 0.82f : u * 1.02f;
    }

    // ---- tennis -------------------------------------------------------------------------------------------------------

    private float tennisWidth(Skin s, SportBoard b, float u) {
        float strip = s.strips ? u * 0.12f : 0f, flag = u * 0.86f;
        float names = Math.max(u * 2.6f, namesW(s, b, u * 0.5f) + u * 0.4f + seedRoom(s, b, u));
        float cols = b.setColumns() * u * 0.78f + (pointsKnown(b) ? u * 1.28f : u * 0.16f) + u * 0.16f;
        return logoW(s, 2 * u) * 0.5f + strip + flag + names + u * 0.42f + cols;
    }

    private float seedRoom(Skin s, SportBoard b, float u) {
        return Math.max(measure(b.a.seed, u * 0.3f, s.face), measure(b.b.seed, u * 0.3f, s.face)) + (b.a.seed.isEmpty() && b.b.seed.isEmpty() ? 0 : u * 0.18f);
    }

    static boolean pointsKnown(SportBoard b) {
        return b.live() && (!b.a.points.isEmpty() || !b.b.points.isEmpty());
    }

    private void tennis(Canvas cv, Skin s, SportBoard b, Bitmap logo, float w, float u) {
        float h = 2 * u;
        ground(cv, s, w, h, u);
        float x = 0;
        // The tour's or the event's mark, the height of both rows.
        float lw = logoW(s, 2 * u) * 0.5f;
        if (s.logo != 0) rect(cv, 0, 0, lw, h, s.logo);
        drawLogo(cv, s, logo, b, 0, 0, lw, h);
        x = lw;
        float cols = b.setColumns() * u * 0.78f, pts = pointsKnown(b) ? u * 1.28f : 0f;
        float colsX = w - (pts > 0 ? 0f : u * 0.16f) - pts - cols;
        for (int row = 0; row < 2; row++) {
            SportBoard.Side side = row == 0 ? b.a : b.b, other = row == 0 ? b.b : b.a;
            float top = row * u;
            boolean lost = b.over() && other.winner;
            playerCell(cv, s, b, side, x, top, colsX - u * 0.12f, u, lost);
            // The sets.
            int n = b.setColumns();
            for (int i = 0; i < n; i++) {
                float l = colsX + i * u * 0.78f, rr = l + u * 0.78f;
                boolean current = b.live() && i == n - 1;
                if (current && s.mid0 != 0) {
                    vgrad(cv, l, top, rr, top + u, s.mid0, s.mid1);
                } else if (current && s.minimal) {
                    rect(cv, l + u * 0.08f, top + u * 0.86f, rr - u * 0.08f, top + u * 0.9f, 0x66FFFFFF);
                }
                String g = SportBoard.gamesIn(side, i);
                boolean done = SportBoard.setDone(b.a.games, b.b.games, i, b.over() ? n + 1 : n);
                int mine = i < side.games.length ? side.games[i] : 0, theirs = i < other.games.length ? other.games[i] : 0;
                int colour = current ? (s.mid0 != 0 ? s.midInk : s.ink) : done && mine < theirs ? s.dim : s.ink;
                if (s.light && current && s.mid0 != 0) colour = 0xFFFFFFFF;
                float size = u * 0.56f;
                centred(cv, g, (l + rr) / 2f - (tieOf(side, i) >= 0 ? u * 0.08f : 0), top + u / 2f, size, colour, s.face);
                int tb = tieOf(side, i);
                if (tb >= 0 && done && mine < theirs) {
                    centred(cv, String.valueOf(tb), rr - u * 0.16f, top + u * 0.3f, u * 0.26f, s.dim, s.face);
                }
            }
            // The points of the game in play.
            if (pts > 0) {
                float l = w - pts;
                if (s.clock != 0) rect(cv, l, top, l + pts, top + u, s.clock);
                else if (s.card) vgrad(cv, l, top, l + pts, top + u, CARD_BLUE, CARD_BLUE_FOOT);
                int ink = s.card ? 0xFFFFFFFF : s.clockInk;
                String p = side.points.isEmpty() ? "" : side.points.toUpperCase(Locale.ROOT);
                centred(cv, p, l + pts / 2f, top + u / 2f, u * 0.56f, ink, s.face);
            }
        }
        // A fine line between the rows.
        float hair = Math.max(1f, u * 0.02f);
        rect(cv, x, u - hair / 2f, s.card ? colsX : w, u + hair / 2f, s.light ? 0x220B1640 : 0x26FFFFFF);
    }

    private static int tieOf(SportBoard.Side s, int i) {
        return i < s.tiebreak.length ? s.tiebreak[i] : -1;
    }

    /** A player's or a fighter's cell: their colour, flag (or photo), name, seed or record, and the server's ball. */
    private void playerCell(Canvas cv, Skin s, SportBoard b, SportBoard.Side side, float l, float t, float rr, float u, boolean lost) {
        float x = l;
        if (s.fill && side.colour != 0) {
            vgrad(cv, l, t, rr, t + u, side.colour, mix(side.colour, 0xFF000000, 0.35f));
        }
        if (s.strips && side.colour != 0) {
            rect(cv, x, t, x + u * 0.12f, t + u, side.colour);
            x += u * 0.12f;
        }
        x += u * 0.12f;
        float fs = u * 0.62f;
        Bitmap pic = s.photos && side.photo != null ? side.photo : side.flag;
        if (pic != null) {
            picture(cv, pic, x, t + (u - fs) / 2f, fs * (pic == side.photo ? 1f : 1.3f), fs, pic == side.photo);
            x += fs * (pic == side.photo ? 1f : 1.3f) + u * 0.16f;
        } else if (!side.country.isEmpty()) {
            float cw = measure(side.country, u * 0.28f, s.face) + u * 0.16f;
            r.set(x, t + u * 0.28f, x + cw, t + u * 0.72f);
            fill.setShader(null);
            fill.setColor(s.light ? 0x1F0B1640 : 0x26FFFFFF);
            cv.drawRoundRect(r, u * 0.06f, u * 0.06f, fill);
            centred(cv, side.country, x + cw / 2f, t + u / 2f, u * 0.28f, s.fill ? inkOn(side.colour) : s.dim, s.face);
            x += cw + u * 0.16f;
        }
        int ink = s.fill && side.colour != 0 ? inkOn(side.colour) : s.ink;
        if (lost) ink = s.fill ? (ink & 0x00FFFFFF) | 0xA0000000 : s.dim;
        String name = nameOf(s, side);
        float size = fitIn(name, u * 0.5f, rr - x - u * 0.5f, s.face);
        left(cv, name, x, t + u / 2f, size, ink, s.face);
        x += measure(name, size, s.face) + u * 0.14f;
        String small = b.sport == SportBoard.TENNIS ? side.seed : "";
        if (!small.isEmpty()) {
            left(cv, small, x, t + u * 0.42f, u * 0.3f, s.fill ? (ink & 0x00FFFFFF) | 0xB0000000 : s.dim, s.face);
            x += measure(small, u * 0.3f, s.face) + u * 0.12f;
        }
        if (side.serving && b.live()) {
            fill.setShader(null);
            fill.setColor(BALL);
            cv.drawCircle(x + u * 0.1f, t + u / 2f, u * 0.1f, fill);
        }
        if (side.winner && b.over()) {
            tick(cv, Math.min(rr - u * 0.36f, x + u * 0.26f), t + u / 2f, u * 0.2f, s.fill ? inkOn(side.colour) : s.light ? 0xFF1B8A4B : 0xFF3DDC84);
        }
    }

    private void tick(Canvas cv, float cx, float cy, float r0, int colour) {
        line.setColor(colour);
        line.setStrokeWidth(r0 * 0.38f);
        line.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        path.moveTo(cx - r0, cy);
        path.lineTo(cx - r0 * 0.25f, cy + r0 * 0.7f);
        path.lineTo(cx + r0, cy - r0 * 0.7f);
        cv.drawPath(path, line);
    }

    // ---- a fight ------------------------------------------------------------------------------------------------------

    private float cornerW(Skin s, SportBoard b, float u) {
        float pic = s.photos ? u * 0.86f : (b.a.flag != null || b.b.flag != null || !b.a.country.isEmpty() ? u * 0.98f : 0f);
        return u * 0.36f + pic + Math.max(u * 1.8f, namesW(s, b, u * 0.48f)) + u * 0.3f;
    }

    private float midW(Skin s, SportBoard b, float u) {
        return Math.max(u * 1.35f, measure(midWords(b), u * 0.52f, s.face) + u * 0.6f);
    }

    private float clockW(Skin s, SportBoard b, float u) {
        String c = clockWords(b);
        return c.isEmpty() ? 0f : Math.max(measure("8:88", u * 0.46f, s.face), measure(c, u * 0.46f, s.face)) + u * 0.6f;
    }

    private float fightWidth(Skin s, SportBoard b, float u) {
        if (s.card) return u * 0.4f + 2 * cornerW(s, b, u) + midW(s, b, u) * 1.15f;
        return logoW(s, u) + 2 * cornerW(s, b, u) + midW(s, b, u) + clockW(s, b, u) + (s.round >= 0.5f ? u * 0.36f : 0f);
    }

    private float stackedFightWidth(Skin s, SportBoard b, float u) {
        float rec = Math.max(measure(b.a.record, u * 0.3f, s.face), measure(b.b.record, u * 0.3f, s.face));
        return logoW(s, 2 * u) * 0.5f + cornerW(s, b, u) + rec + u * 0.3f + midW(s, b, u) + clockW(s, b, u);
    }

    /** Pure: the middle's words: the round on, VS before, how it ended after (the round between rounds too). */
    static String midWords(SportBoard b) {
        if (b.over()) {
            String m = SportBoard.methodShort(b.method);
            return m.isEmpty() ? "FINAL" : m;
        }
        if (b.live() && b.round > 0) return "R" + b.round;
        return "VS";
    }

    /** Pure: the clock block's words: the time left in the round, the rest's count, the finish's time, the start. */
    static String clockWords(SportBoard b) {
        if (b.over()) return b.finish == null ? "" : b.finish;
        if (b.live()) {
            if (b.resting) return "END";
            return b.clockLeft();
        }
        if (b.start > 0) {
            java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(b.start).atZone(java.time.ZoneId.systemDefault());
            return String.format(Locale.ROOT, "%d:%02d", z.getHour(), z.getMinute());
        }
        return "";
    }

    private void fight(Canvas cv, Skin s, SportBoard b, Bitmap logo, float w, float u) {
        if (s.card) {
            cardFight(cv, s, b, w, u);
            return;
        }
        if (s.stacked) {
            stackedFight(cv, s, b, logo, w, u);
            return;
        }
        ground(cv, s, w, u, u);
        float x = 0, lw = logoW(s, u);
        if (s.logo != 0) rect(cv, 0, 0, lw, u, s.logo);
        drawLogo(cv, s, logo, b, 0, 0, lw, u);
        x = lw;
        float mw = midW(s, b, u), kw = clockW(s, b, u);
        float right = w - kw - (s.round >= 0.5f ? u * 0.18f : 0f);
        float each = (right - x - mw) / 2f;
        playerCell(cv, s, b, b.a, x, 0, x + each, u, b.over() && b.b.winner);
        float mx = x + each;
        if (s.mid0 != 0) vgrad(cv, mx, 0, mx + mw, u, s.mid0, s.mid1);
        int midInk = s.mid0 != 0 ? (s.light ? 0xFFFFFFFF : s.midInk) : s.ink;
        String mid = midWords(b);
        centred(cv, mid, mx + mw / 2f, u / 2f, fitIn(mid, u * 0.52f, mw - u * 0.3f, s.face), midInk, s.face);
        cornerRight(cv, s, b, b.b, mx + mw, 0, right, u, b.over() && b.a.winner);
        if (kw > 0) {
            float l = right;
            if (s.clock != 0) rect(cv, l, 0, w, u, s.clock);
            String c = clockWords(b);
            int ink = b.live() && b.resting ? s.dim : s.clockInk;
            if (b.live() && !b.resting && b.secsIn >= 0 && b.roundSecs - b.secsIn <= 10) ink = 0xFFFF5A5F;
            centred(cv, c, l + (w - l - (s.round >= 0.5f ? u * 0.18f : 0f)) / 2f, u / 2f, u * 0.46f, ink, s.face);
        }
    }

    /** The blue corner, mirrored: the name in towards the middle, the flag, the colour at its outer edge. */
    private void cornerRight(Canvas cv, Skin s, SportBoard b, SportBoard.Side side, float l, float t, float rr, float u, boolean lost) {
        if (s.fill && side.colour != 0) vgrad(cv, l, t, rr, t + u, side.colour, mix(side.colour, 0xFF000000, 0.35f));
        float x = rr;
        if (s.strips && side.colour != 0) {
            rect(cv, x - u * 0.12f, t, x, t + u, side.colour);
            x -= u * 0.12f;
        }
        x -= u * 0.12f;
        float fs = u * 0.62f;
        Bitmap pic = s.photos && side.photo != null ? side.photo : side.flag;
        if (pic != null) {
            float pw = fs * (pic == side.photo ? 1f : 1.3f);
            picture(cv, pic, x - pw, t + (u - fs) / 2f, pw, fs, pic == side.photo);
            x -= pw + u * 0.16f;
        } else if (!side.country.isEmpty()) {
            float cw = measure(side.country, u * 0.28f, s.face) + u * 0.16f;
            r.set(x - cw, t + u * 0.28f, x, t + u * 0.72f);
            fill.setShader(null);
            fill.setColor(s.light ? 0x1F0B1640 : 0x26FFFFFF);
            cv.drawRoundRect(r, u * 0.06f, u * 0.06f, fill);
            centred(cv, side.country, x - cw / 2f, t + u / 2f, u * 0.28f, s.fill ? inkOn(side.colour) : s.dim, s.face);
            x -= cw + u * 0.16f;
        }
        int ink = s.fill && side.colour != 0 ? inkOn(side.colour) : s.ink;
        if (lost) ink = s.fill ? (ink & 0x00FFFFFF) | 0xA0000000 : s.dim;
        String name = nameOf(s, side);
        float size = fitIn(name, u * 0.5f, x - l - u * 0.4f, s.face);
        float nw = measure(name, size, s.face);
        left(cv, name, x - nw, t + u / 2f, size, ink, s.face);
        if (side.winner && b.over()) tick(cv, Math.max(l + u * 0.3f, x - nw - u * 0.3f), t + u / 2f, u * 0.2f,
                s.fill ? inkOn(side.colour) : s.light ? 0xFF1B8A4B : 0xFF3DDC84);
    }

    private void stackedFight(Canvas cv, Skin s, SportBoard b, Bitmap logo, float w, float u) {
        float h = 2 * u;
        ground(cv, s, w, h, u);
        float lw = logoW(s, 2 * u) * 0.5f;
        if (s.logo != 0) rect(cv, 0, 0, lw, h, s.logo);
        drawLogo(cv, s, logo, b, 0, 0, lw, h);
        float mw = midW(s, b, u), kw = clockW(s, b, u);
        float right = w - mw - kw;
        for (int row = 0; row < 2; row++) {
            SportBoard.Side side = row == 0 ? b.a : b.b, other = row == 0 ? b.b : b.a;
            playerCell(cv, s, b, side, lw, row * u, right - u * 0.2f, u, b.over() && other.winner);
            if (!side.record.isEmpty()) {
                float rw = measure(side.record, u * 0.3f, s.face);
                left(cv, side.record, right - u * 0.2f - rw, row * u + u / 2f, u * 0.3f, s.dim, s.face);
            }
        }
        rect(cv, lw, u - Math.max(1f, u * 0.02f), right, u, 0x26FFFFFF);
        if (s.mid0 != 0) vgrad(cv, right, 0, right + mw, h, s.mid0, s.mid1);
        String mid = midWords(b);
        centred(cv, mid, right + mw / 2f, u, fitIn(mid, u * 0.62f, mw - u * 0.3f, s.face), s.midInk, s.face);
        if (kw > 0) {
            if (s.clock != 0) rect(cv, right + mw, 0, w, h, s.clock);
            String c = clockWords(b);
            centred(cv, c, right + mw + kw / 2f, u, u * 0.5f, b.live() && b.resting ? s.dim : s.clockInk, s.face);
            if (b.live() && b.rounds > 0 && b.round > 0) {
                centred(cv, "OF " + b.rounds, right + mw + kw / 2f, u * 1.62f, u * 0.26f, s.dim, s.face);
            }
        }
    }

    /** Card: the white card, the corners at its ends with their flags, the round on its blue in the middle. */
    private void cardFight(Canvas cv, Skin s, SportBoard b, float w, float u) {
        float h = u * 1.25f;
        ground(cv, s, w, h, u);
        float mw = midW(s, b, u) * 1.15f, mx = (w - mw) / 2f;
        vgrad(cv, mx, 0, mx + mw, h, b.live() && !b.resting && b.secsIn >= 0 && b.roundSecs - b.secsIn <= 10 ? 0xFFD7192F : CARD_BLUE,
                b.live() && !b.resting && b.secsIn >= 0 && b.roundSecs - b.secsIn <= 10 ? 0xFF8E0B1D : CARD_BLUE_FOOT);
        String mid = midWords(b), clk = clockWords(b);
        centred(cv, mid, mx + mw / 2f, h * (clk.isEmpty() ? 0.5f : 0.38f), fitIn(mid, u * 0.56f, mw - u * 0.3f, s.face), 0xFFFFFFFF, s.face);
        if (!clk.isEmpty()) centred(cv, clk, mx + mw / 2f, h * 0.76f, u * 0.32f, 0xFFC3CBF4, s.face);
        float t = (h - u) / 2f;
        playerCell(cv, s, b, b.a, u * 0.2f, t, mx - u * 0.1f, u, b.over() && b.b.winner);
        cornerRight(cv, s, b, b.b, mx + mw + u * 0.1f, t, w - u * 0.2f, u, b.over() && b.a.winner);
        // The corners' colours as two fine bars along the card's foot.
        rect(cv, 0, h - u * 0.07f, mx, h, SportBoard.RED_CORNER);
        rect(cv, mx + mw, h - u * 0.07f, w, h, SportBoard.BLUE_CORNER);
    }

    // ---- the whole of it ----------------------------------------------------------------------------------------------

    /**
     * Draws the board w by h (as size() gave it, the tab's room under it), its rows u high: the logo (the tour's, UFC's;
     * null for boxing's glove), the tab with the event under it when tab.
     */
    public void draw(Canvas cv, Skin s, SportBoard b, Bitmap logo, float w, float h, float u, boolean tab) {
        if (cv == null || s == null || b == null || w <= 0 || h <= 0 || u <= 0) return;
        float bodyH = b.sport == SportBoard.TENNIS || s.stacked ? 2 * u : u * (s.card ? 1.25f : 1f);
        cv.save();
        shape(s, w, bodyH, u);
        cv.clipPath(clip);
        if (b.sport == SportBoard.TENNIS) tennis(cv, s, b, logo, w, u);
        else fight(cv, s, b, logo, w, u);
        if (s.sheen) sheen(cv, w, bodyH);
        cv.restore();
        if (s.edge) {
            line.setColor(0x66FFFFFF);
            line.setStrokeWidth(Math.max(1f, u * 0.03f));
            cv.drawPath(clip, line);
        }
        if (tab) tab(cv, s, b, w, bodyH + u * 0.08f, u);
    }

    /** The board's outline: its corners' round, a pill for Glass, the slanted end of the looks that have one. */
    private void shape(Skin s, float w, float h, float u) {
        clip.reset();
        if (s.slant) {
            float k = u * 0.3f;
            clip.moveTo(k, 0);
            clip.lineTo(w, 0);
            clip.lineTo(w - k, h);
            clip.lineTo(0, h);
            clip.close();
            return;
        }
        float rad = s.round >= 0.5f ? Math.min(h, 2 * u) * 0.5f : s.round * u;
        r.set(0, 0, w, h);
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
    }

    private void ground(Canvas cv, Skin s, float w, float h, float u) {
        if (s.groundFoot != 0) vgrad(cv, 0, 0, w, h, s.ground, s.groundFoot);
        else rect(cv, 0, 0, w, h, s.ground);
    }

    private void sheen(Canvas cv, float w, float h) {
        fill.setColor(0xFF000000);
        fill.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h * 0.5f, fill);
        fill.setShader(null);
    }

    /** The tab under the board: the event and where in it, in the look's ground, its words quieter. */
    private void tab(Canvas cv, Skin s, SportBoard b, float w, float top, float u) {
        String words = tabWords(b);
        if (words.isEmpty()) return;
        float th = tabHeight(u), size = u * 0.3f;
        float tw = Math.min(w, measure(words, size, s.face) + u * 0.7f);
        float l = s.slant ? u * 0.3f : 0f;
        r.set(l, top, l + tw, top + th);
        fill.setShader(null);
        fill.setColor(s.card ? CARD_BLUE_FOOT : s.light ? NAVY : s.logo != 0 ? s.logo : mix(s.ground | 0xFF000000, 0xFF000000, 0.3f));
        cv.drawRoundRect(r, Math.min(th / 2f, s.round * u * 1.4f + u * 0.04f), Math.min(th / 2f, s.round * u * 1.4f + u * 0.04f), fill);
        if (s.mid0 != 0 && (s.mid0 >>> 24) == 0xFF && !s.light) rect(cv, l, top, l + u * 0.08f, top + th, s.mid0);
        centred(cv, words, l + tw / 2f, top + th / 2f, fitIn(words, size, tw - u * 0.5f, s.face), s.card || s.light ? 0xFFE6E9F2 : s.dim, s.face);
    }

    /** Pure: the tab's words, in capitals: the event, then its stage ("WTA FINALS · GROUP STAGE"); a fight's rounds. */
    public static String tabWords(SportBoard b) {
        StringBuilder sb = new StringBuilder();
        if (b.event != null && !b.event.isEmpty()) sb.append(b.event);
        if (b.stage != null && !b.stage.isEmpty()) sb.append(sb.length() > 0 ? " · " : "").append(b.stage);
        if (b.fight()) {
            if (b.mainEvent) sb.append(sb.length() > 0 ? " · " : "").append("Main event");
            if (b.live() && b.round > 0 && b.rounds > 0) sb.append(sb.length() > 0 ? " · " : "").append("Round ").append(b.round).append(" of ").append(b.rounds);
            else if (!b.live() && !b.over() && b.rounds > 0) sb.append(sb.length() > 0 ? " · " : "").append(b.rounds).append(" rounds");
        } else if (b.tiebreakNow()) {
            sb.append(sb.length() > 0 ? " · " : "").append("Tiebreak");
        } else if (b.over() && b.method != null && !b.method.isEmpty()) {
            sb.append(sb.length() > 0 ? " · " : "").append(b.method);
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    // ---- a moment -----------------------------------------------------------------------------------------------------

    /**
     * A moment across the board, t from 0 to 1: a band of the side's colour sweeping in from the left, its words big
     * ("SET", "GAME, SET AND MATCH", "ROUND 3", "WINNER"), held with a shine running over it, then out to the right.
     */
    public void band(Canvas cv, Skin s, SportBoard b, String words, int colour, float w, float u, float t) {
        if (cv == null || words == null || t <= 0f || t >= 1f) return;
        float bodyH = b.sport == SportBoard.TENNIS || s.stacked ? 2 * u : u * (s.card ? 1.25f : 1f);
        float in = Math.min(1f, t / 0.16f), out = t > 0.84f ? (t - 0.84f) / 0.16f : 0f;
        float l = w * ease(out), rr = w * ease(in);
        if (rr <= l) return;
        cv.save();
        shape(s, w, bodyH, u);
        cv.clipPath(clip);
        cv.clipRect(l, 0, rr, bodyH);
        int c = colour == 0 ? (s.mid0 != 0 && (s.mid0 >>> 24) == 0xFF ? s.mid0 : CARD_BLUE) : colour;
        vgrad(cv, 0, 0, w, bodyH, mix(c, 0xFFFFFFFF, 0.08f), mix(c, 0xFF000000, 0.3f));
        // The shine: a pale slant crossing it once while it holds.
        float hold = Math.max(0f, Math.min(1f, (t - 0.2f) / 0.6f));
        if (hold > 0f && hold < 1f) {
            float sx = -w * 0.3f + hold * w * 1.6f;
            path.reset();
            path.moveTo(sx, 0);
            path.lineTo(sx + bodyH * 0.6f, 0);
            path.lineTo(sx + bodyH * 0.2f, bodyH);
            path.lineTo(sx - bodyH * 0.4f, bodyH);
            path.close();
            fill.setShader(null);
            fill.setColor(0x33FFFFFF);
            cv.drawPath(path, fill);
        }
        float size = fitIn(words, Math.min(bodyH * 0.5f, u * 0.62f), w - u, s.face);
        centred(cv, words, w / 2f, bodyH / 2f, size, inkOn(c), s.face);
        cv.restore();
    }

    static float ease(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }

    // ---- drawing bits -------------------------------------------------------------------------------------------------

    private void drawLogo(Canvas cv, Skin s, Bitmap logo, SportBoard b, float l, float t, float rr, float bt) {
        float bw = rr - l, bh = bt - t, side = Math.min(bw, bh) * 0.62f;
        float cx = (l + rr) / 2f, cy = (t + bt) / 2f;
        boolean onLight = s.logo == 0 ? s.light : (inkOn(s.logo) == NAVY);
        if (logo != null && logo.getWidth() > 0) {
            float k = Math.min(side * 1.5f / logo.getWidth(), side / logo.getHeight());
            float dw = logo.getWidth() * k, dh = logo.getHeight() * k;
            img.setColorFilter(onLight ? navy : white);
            r.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
            cv.drawBitmap(logo, null, r, img);
            img.setColorFilter(null);
            return;
        }
        if (b.sport == SportBoard.TENNIS) {
            // A tennis ball: the yellow-green round with its seam.
            fill.setShader(null);
            fill.setColor(BALL);
            cv.drawCircle(cx, cy, side * 0.42f, fill);
            line.setColor(0xCCFFFFFF);
            line.setStrokeWidth(side * 0.06f);
            r.set(cx - side * 0.75f, cy - side * 0.42f, cx - side * 0.12f, cy + side * 0.42f);
            cv.drawArc(r, -60, 120, false, line);
            r.set(cx + side * 0.12f, cy - side * 0.42f, cx + side * 0.75f, cy + side * 0.42f);
            cv.drawArc(r, 120, 120, false, line);
            return;
        }
        glove(cv, cx, cy, side, onLight ? NAVY : 0xFFFFFFFF);
    }

    /** A boxing glove, drawn: the fist's round, the thumb, the cuff. */
    private void glove(Canvas cv, float cx, float cy, float side, int colour) {
        fill.setShader(null);
        fill.setColor(colour);
        float u = side / 2f;
        r.set(cx - u * 0.78f, cy - u * 0.9f, cx + u * 0.82f, cy + u * 0.42f);
        cv.drawRoundRect(r, u * 0.62f, u * 0.62f, fill);
        r.set(cx - u * 1.0f, cy - u * 0.2f, cx - u * 0.22f, cy + u * 0.46f);
        cv.drawOval(r, fill);
        r.set(cx - u * 0.6f, cy + u * 0.34f, cx + u * 0.66f, cy + u * 0.92f);
        cv.drawRoundRect(r, u * 0.12f, u * 0.12f, fill);
        fill.setColor(colour == NAVY ? 0x66FFFFFF : 0x66000000);
        cv.drawRect(cx - u * 0.6f, cy + u * 0.44f, cx + u * 0.66f, cy + u * 0.5f, fill);
    }

    private void picture(Canvas cv, Bitmap b, float l, float t, float w, float h, boolean photo) {
        cv.save();
        r.set(l, t, l + w, t + h);
        path.reset();
        path.addRoundRect(r, h * (photo ? 0.5f : 0.1f), h * (photo ? 0.5f : 0.1f), Path.Direction.CW);
        cv.clipPath(path);
        float k = Math.max(w / b.getWidth(), h / b.getHeight());
        float dw = b.getWidth() * k, dh = b.getHeight() * k;
        float dx = l + (w - dw) / 2f, dy = photo ? t : t + (h - dh) / 2f;
        r.set(dx, dy, dx + dw, dy + dh);
        cv.drawBitmap(b, null, r, img);
        cv.restore();
    }

    private void rect(Canvas cv, float l, float t, float rr, float b, int colour) {
        if (colour == 0 || rr <= l || b <= t) return;
        fill.setShader(null);
        fill.setColor(colour);
        cv.drawRect(l, t, rr, b, fill);
    }

    private void vgrad(Canvas cv, float l, float t, float rr, float b, int top, int foot) {
        if (rr <= l || b <= t) return;
        if (top == foot || foot == 0) {
            rect(cv, l, t, rr, b, top);
            return;
        }
        // A shader keeps the paint's own alpha: opaque, whatever was drawn last.
        fill.setColor(0xFF000000);
        fill.setShader(new LinearGradient(0, t, 0, b, top, foot, Shader.TileMode.CLAMP));
        cv.drawRect(l, t, rr, b, fill);
        fill.setShader(null);
    }

    private float fitIn(String s, float size, float room, Typeface tf) {
        if (room <= 0) return size * 0.6f;
        float w = measure(s, size, tf);
        return w <= room ? size : Math.max(size * 0.6f, size * room / w);
    }

    private void centred(Canvas cv, String s, float cx, float cy, float size, int colour, Typeface tf) {
        if (s == null || s.isEmpty()) return;
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void left(Canvas cv, String s, float x, float cy, float size, int colour, Typeface tf) {
        if (s == null || s.isEmpty()) return;
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, text);
    }
}
