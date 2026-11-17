package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;

import java.util.Locale;

/**
 * The dark look of Films, TV shows, a title's page and the TV guide: a near-black ground, Outfit throughout, white
 * writing with greys under it, Homefield's amber for what is chosen, white rings and pills for where the remote is,
 * line icons drawn as paths, and a person's initials in a circle where there is no photo. Sizes are design pixels of
 * a 1920 x 1080 screen (Ui.f).
 */
final class Dark {
    private Dark() {}

    // The theme's colours (Theme sets them as the app starts; Homefield's own until then).
    static int GROUND = 0xFF0E0E11, RAIL = 0xFF141417, PANEL = 0xFF131316, CARD = 0xFF1E1E23, CARD_HI = 0xFF2A2A31;
    static final int TEXT = 0xFFFFFFFF, SOFT = 0xFFD1D1D6, MUTED = 0xFF8E8E93, FAINT = 0xFF5E5E66, LINE = 0x1FFFFFFF;
    /** The accent (Homefield's amber): the page the rail is on, the button the remote is on, the stars; its ink on it. */
    static int ACCENT = 0xFFFFB547, ACCENT_INK = 0xFF1E1606;
    /** The accent's lighter shade (the part of a programme already shown), the New badge, the focus rings. */
    static int ACCENT_SOFT = 0xFFFFD48A, BADGE = 0xFFFFD25A, RING = 0xFFFFFFFF;
    /** The guide's programme colours, picked by title. */
    static final int[] DEEP = Theme.DEEP.clone();

    /** Pure: a colour at an opacity (0 to 255). */
    static int alpha(int colour, int a) {
        return (colour & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    static Typeface outfit = Typeface.DEFAULT;

    static void init(Context c) {
        try {
            outfit = c.getResources().getFont(R.font.outfit);
        } catch (RuntimeException e) {
            outfit = Typeface.DEFAULT;
        }
    }

    /** Writing in Outfit at a weight (400 regular, 500 medium, 600 semi-bold, 700 bold), size in design pixels. */
    static TextPaint text(float size, int weight, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(outfit);
        // Settings → Appearance → Text size.
        p.setTextSize(Ui.f(size) * com.gathin.homefieldtv.prefs.Look.text);
        p.setColor(color);
        try {
            p.setFontVariationSettings("'wght' " + weight);
        } catch (RuntimeException ignored) {
            // the font's own weight
        }
        // Where the variable font can't be set to a weight, a heavier one is drawn bolder.
        if (weight >= 600 && outfit == Typeface.DEFAULT) p.setFakeBoldText(true);
        return p;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    static Paint line(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, Ui.f(width)));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    /** The ring round where the remote is: white, just outside r, its corners following r's. */
    static void ring(Canvas c, RectF r, float radius, Paint ring) {
        float k = ring.getStrokeWidth() / 2f + Ui.f(3);
        c.drawRoundRect(r.left - k, r.top - k, r.right + k, r.bottom + k, radius + k, radius + k, ring);
    }

    private static final Path REPLAY = new Path();
    private static final RectF REPLAY_OVAL = new RectF();

    /**
     * Catch-up's mark: a circle arrow turning back (anticlockwise), open at the top, its head at half past one pointing
     * up and to the left, centred on (cx, cy) with radius r (screen pixels); the arc in stroke, the head in fill.
     */
    static void replay(Canvas c, float cx, float cy, float r, Paint stroke, Paint fill) {
        REPLAY_OVAL.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(REPLAY_OVAL, 315f, 270f, false, stroke);
        float px = cx + r * 0.707f, py = cy - r * 0.707f, dx = -0.707f, dy = -0.707f, a = r * 0.8f;
        float nx = -dy, ny = dx;
        REPLAY.reset();
        REPLAY.moveTo(px + dx * a * 1.2f, py + dy * a * 1.2f);
        REPLAY.lineTo(px - dx * a * 0.4f + nx * a * 0.65f, py - dy * a * 0.4f + ny * a * 0.65f);
        REPLAY.lineTo(px - dx * a * 0.4f - nx * a * 0.65f, py - dy * a * 0.4f - ny * a * 0.65f);
        REPLAY.close();
        c.drawPath(REPLAY, fill);
    }

    private static final Path BELL = new Path();
    private static final RectF BELL_DOME = new RectF();

    /**
     * A reminder's mark: a bell, filled, centred on (cx, cy) and about 2r tall (screen pixels): its dome and flared
     * body, the rim across its foot, the clapper under it and the little knob on top.
     */
    static void bell(Canvas c, float cx, float cy, float r, Paint fill) {
        BELL.reset();
        BELL_DOME.set(cx - r * 0.62f, cy - r * 0.86f, cx + r * 0.62f, cy + r * 0.36f);
        BELL.moveTo(cx - r * 0.86f, cy + r * 0.52f);
        BELL.lineTo(cx - r * 0.62f, cy + r * 0.22f);
        BELL.lineTo(cx - r * 0.62f, cy - r * 0.24f);
        BELL.arcTo(BELL_DOME, 180f, 180f, false);
        BELL.lineTo(cx + r * 0.62f, cy + r * 0.22f);
        BELL.lineTo(cx + r * 0.86f, cy + r * 0.52f);
        BELL.close();
        c.drawPath(BELL, fill);
        c.drawCircle(cx, cy + r * 0.74f, r * 0.2f, fill);
        c.drawCircle(cx, cy - r * 0.9f, r * 0.13f, fill);
    }

    // ---- people ---------------------------------------------------------------------------------------------------

    private static final int[] FACES = {0xFF5B6CFF, 0xFFE0703A, 0xFF2E9D72, 0xFF9B5DE5, 0xFF1F9AA8, 0xFFC0485A, 0xFFB08A1E, 0xFF5E7186};

    /** Pure: "Liam Neeson" as "LN", "Cher" as "C". */
    static String initials(String name) {
        if (name == null) return "";
        String[] w = name.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            String s = w[i];
            if (s.isEmpty()) continue;
            int cp = s.codePointAt(0);
            if (!Character.isLetter(cp)) continue;
            // The first name's letter, then the last name's.
            if (b.length() == 0 || i == w.length - 1) b.appendCodePoint(Character.toUpperCase(cp));
            if (b.length() >= 2) break;
        }
        return b.toString();
    }

    /** Pure: a person's circle colour, the same for the same name. */
    static int face(String name) {
        int h = name == null ? 0 : name.trim().toLowerCase(Locale.ROOT).hashCode();
        return FACES[Math.floorMod(h, FACES.length)];
    }

    /** A person with no photo: their initials in a circle of their own colour. */
    static void avatar(Canvas c, float cx, float cy, float r, String name, Paint disc, TextPaint ink) {
        disc.setColor(face(name));
        c.drawCircle(cx, cy, r, disc);
        String s = initials(name);
        float w = ink.measureText(s);
        Paint.FontMetrics fm = ink.getFontMetrics();
        c.drawText(s, cx - w / 2f, cy - (fm.ascent + fm.descent) / 2f, ink);
    }

    // ---- icons, drawn in a box of side s centred on (cx, cy) -----------------------------------------------------

    static final int HOME = 0, GUIDE = 1, FILMS = 2, SHOWS = 3, SEARCH = 4, SETTINGS = 5, HEART = 6, HEART_FULL = 7,
            RESTART = 8, PLAY = 9, INFO = 10, LIST = 11, GRID = 12, RESUME = 13, STAR = 14, FOLDER = 15, LIVE = 16;

    private static final Path P = new Path();
    private static final RectF ARC = new RectF();

    static void icon(Canvas c, int kind, float cx, float cy, float s, Paint stroke, Paint solid) {
        float h = s / 2f;
        Path p = P;
        p.reset();
        switch (kind) {
            case HOME:
                p.moveTo(cx - h * 0.78f, cy - h * 0.05f);
                p.lineTo(cx, cy - h * 0.8f);
                p.lineTo(cx + h * 0.78f, cy - h * 0.05f);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(cx - h * 0.58f, cy - h * 0.2f);
                p.lineTo(cx - h * 0.58f, cy + h * 0.78f);
                p.lineTo(cx + h * 0.58f, cy + h * 0.78f);
                p.lineTo(cx + h * 0.58f, cy - h * 0.2f);
                c.drawPath(p, stroke);
                c.drawLine(cx - h * 0.18f, cy + h * 0.78f, cx - h * 0.18f, cy + h * 0.3f, stroke);
                c.drawLine(cx - h * 0.18f, cy + h * 0.3f, cx + h * 0.18f, cy + h * 0.3f, stroke);
                c.drawLine(cx + h * 0.18f, cy + h * 0.3f, cx + h * 0.18f, cy + h * 0.78f, stroke);
                break;
            case GUIDE:
                // An open book of programmes: two pages.
                p.moveTo(cx, cy - h * 0.55f);
                p.quadTo(cx - h * 0.45f, cy - h * 0.8f, cx - h * 0.85f, cy - h * 0.62f);
                p.lineTo(cx - h * 0.85f, cy + h * 0.68f);
                p.quadTo(cx - h * 0.45f, cy + h * 0.52f, cx, cy + h * 0.78f);
                p.quadTo(cx + h * 0.45f, cy + h * 0.52f, cx + h * 0.85f, cy + h * 0.68f);
                p.lineTo(cx + h * 0.85f, cy - h * 0.62f);
                p.quadTo(cx + h * 0.45f, cy - h * 0.8f, cx, cy - h * 0.55f);
                c.drawPath(p, stroke);
                c.drawLine(cx, cy - h * 0.55f, cx, cy + h * 0.78f, stroke);
                break;
            case LIVE:
                // A mast sending out waves.
                c.drawCircle(cx, cy - h * 0.15f, h * 0.12f, solid);
                c.drawLine(cx, cy - h * 0.05f, cx - h * 0.35f, cy + h * 0.8f, stroke);
                c.drawLine(cx, cy - h * 0.05f, cx + h * 0.35f, cy + h * 0.8f, stroke);
                ARC.set(cx - h * 0.5f, cy - h * 0.65f, cx + h * 0.5f, cy + h * 0.35f);
                c.drawArc(ARC, 200, 50, false, stroke);
                c.drawArc(ARC, 290, 50, false, stroke);
                ARC.set(cx - h * 0.85f, cy - h * 1.0f, cx + h * 0.85f, cy + h * 0.7f);
                c.drawArc(ARC, 200, 45, false, stroke);
                c.drawArc(ARC, 295, 45, false, stroke);
                break;
            case FILMS:
                // A strip of film.
                c.drawRoundRect(cx - h * 0.7f, cy - h * 0.85f, cx + h * 0.7f, cy + h * 0.85f, h * 0.15f, h * 0.15f, stroke);
                c.drawLine(cx - h * 0.35f, cy - h * 0.85f, cx - h * 0.35f, cy + h * 0.85f, stroke);
                c.drawLine(cx + h * 0.35f, cy - h * 0.85f, cx + h * 0.35f, cy + h * 0.85f, stroke);
                c.drawLine(cx - h * 0.35f, cy, cx + h * 0.35f, cy, stroke);
                for (int i = -1; i <= 1; i += 2) {
                    c.drawLine(cx - h * 0.7f, cy + i * h * 0.42f, cx - h * 0.35f, cy + i * h * 0.42f, stroke);
                    c.drawLine(cx + h * 0.35f, cy + i * h * 0.42f, cx + h * 0.7f, cy + i * h * 0.42f, stroke);
                }
                break;
            case SHOWS:
                // A screen on a stand.
                c.drawRoundRect(cx - h * 0.85f, cy - h * 0.7f, cx + h * 0.85f, cy + h * 0.45f, h * 0.15f, h * 0.15f, stroke);
                c.drawLine(cx - h * 0.35f, cy + h * 0.82f, cx + h * 0.35f, cy + h * 0.82f, stroke);
                c.drawLine(cx, cy + h * 0.45f, cx, cy + h * 0.82f, stroke);
                break;
            case SEARCH:
                c.drawCircle(cx - h * 0.15f, cy - h * 0.15f, h * 0.55f, stroke);
                c.drawLine(cx + h * 0.25f, cy + h * 0.25f, cx + h * 0.75f, cy + h * 0.75f, stroke);
                break;
            case SETTINGS: {
                c.drawCircle(cx, cy, h * 0.28f, stroke);
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI * i / 4;
                    float x0 = cx + (float) Math.cos(ang) * h * 0.55f, y0 = cy + (float) Math.sin(ang) * h * 0.55f;
                    float x1 = cx + (float) Math.cos(ang) * h * 0.82f, y1 = cy + (float) Math.sin(ang) * h * 0.82f;
                    c.drawLine(x0, y0, x1, y1, stroke);
                }
                c.drawCircle(cx, cy, h * 0.58f, stroke);
                break;
            }
            case HEART:
            case HEART_FULL:
                p.moveTo(cx, cy + h * 0.72f);
                p.cubicTo(cx - h * 1.05f, cy, cx - h * 0.62f, cy - h * 0.95f, cx, cy - h * 0.38f);
                p.cubicTo(cx + h * 0.62f, cy - h * 0.95f, cx + h * 1.05f, cy, cx, cy + h * 0.72f);
                p.close();
                c.drawPath(p, kind == HEART_FULL ? solid : stroke);
                break;
            case RESTART: {
                ARC.set(cx - h * 0.62f, cy - h * 0.62f, cx + h * 0.62f, cy + h * 0.62f);
                c.drawArc(ARC, -60, 300, false, stroke);
                float tx = cx + (float) Math.cos(Math.toRadians(-60)) * h * 0.62f, ty = cy + (float) Math.sin(Math.toRadians(-60)) * h * 0.62f;
                p.moveTo(tx - h * 0.32f, ty - h * 0.12f);
                p.lineTo(tx + h * 0.06f, ty + h * 0.02f);
                p.lineTo(tx - h * 0.02f, ty + h * 0.4f);
                c.drawPath(p, stroke);
                break;
            }
            case PLAY:
                p.moveTo(cx - h * 0.45f, cy - h * 0.7f);
                p.lineTo(cx + h * 0.7f, cy);
                p.lineTo(cx - h * 0.45f, cy + h * 0.7f);
                p.close();
                c.drawPath(p, solid);
                break;
            case INFO:
                c.drawCircle(cx, cy, h * 0.78f, stroke);
                c.drawLine(cx, cy - h * 0.08f, cx, cy + h * 0.4f, stroke);
                c.drawCircle(cx, cy - h * 0.38f, Math.max(1f, stroke.getStrokeWidth() * 0.7f), solid);
                break;
            case LIST:
                for (int i = -1; i <= 1; i++) {
                    c.drawCircle(cx - h * 0.62f, cy + i * h * 0.5f, Math.max(1f, stroke.getStrokeWidth() * 0.7f), solid);
                    c.drawLine(cx - h * 0.3f, cy + i * h * 0.5f, cx + h * 0.75f, cy + i * h * 0.5f, stroke);
                }
                break;
            case GRID:
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        float x = cx + i * h * 0.55f, y = cy + j * h * 0.55f, q = h * 0.17f;
                        c.drawRect(x - q, y - q, x + q, y + q, stroke);
                    }
                }
                break;
            case RESUME:
                c.drawRoundRect(cx - h * 0.8f, cy - h * 0.62f, cx + h * 0.8f, cy + h * 0.62f, h * 0.14f, h * 0.14f, stroke);
                p.moveTo(cx - h * 0.2f, cy - h * 0.3f);
                p.lineTo(cx + h * 0.32f, cy);
                p.lineTo(cx - h * 0.2f, cy + h * 0.3f);
                p.close();
                c.drawPath(p, solid);
                break;
            case STAR:
                c.drawPath(Ui.star(cx, cy, h * 0.9f), solid);
                break;
            case FOLDER:
                p.moveTo(cx - h * 0.85f, cy - h * 0.55f);
                p.lineTo(cx - h * 0.25f, cy - h * 0.55f);
                p.lineTo(cx - h * 0.1f, cy - h * 0.38f);
                p.lineTo(cx + h * 0.85f, cy - h * 0.38f);
                p.lineTo(cx + h * 0.85f, cy + h * 0.62f);
                p.lineTo(cx - h * 0.85f, cy + h * 0.62f);
                p.close();
                c.drawPath(p, stroke);
                break;
            default:
                break;
        }
    }

    /** Homefield's own mark: an amber rounded square with a house and a play shape cut into it. */
    static void mark(Canvas c, float x, float y, float s, Paint amber, Paint ink) {
        c.drawRoundRect(x, y, x + s, y + s, s * 0.26f, s * 0.26f, amber);
        Path p = P;
        p.reset();
        float cx = x + s / 2f;
        p.moveTo(cx, y + s * 0.2f);
        p.lineTo(x + s * 0.8f, y + s * 0.46f);
        p.lineTo(x + s * 0.8f, y + s * 0.8f);
        p.lineTo(x + s * 0.2f, y + s * 0.8f);
        p.lineTo(x + s * 0.2f, y + s * 0.46f);
        p.close();
        c.drawPath(p, ink);
        p.reset();
        p.moveTo(cx - s * 0.08f, y + s * 0.47f);
        p.lineTo(cx + s * 0.13f, y + s * 0.6f);
        p.lineTo(cx - s * 0.08f, y + s * 0.73f);
        p.close();
        c.drawPath(p, amber);
    }
}
