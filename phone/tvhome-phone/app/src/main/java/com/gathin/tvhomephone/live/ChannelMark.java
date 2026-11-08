package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Whitebrook Road's own sports channel, drawn as broadcasters draw theirs, for the cover LogoCover puts over the channel's
 * logo (the TV launcher's ChannelMark, drawn the same): Homefield's mark (the house with the play button cut through it) in its violet to coral, the family's name, the
 * picture's tag (HD, UHD or 4K, with HDR or not) as channels show theirs, and LIVE while the game is. Seven looks: the
 * mark in its tile beside the name (Signature), the name over a channel bar (Matchday, Grand Prix for Formula 1), the
 * house as a badge with WR in it (Roofline), leaning forward (Velocity), in a ring (Roundel), one solid block, and smoked
 * glass as channels' own logos sit on the picture. Solid but for Smoked glass, so the channel's logo under it never
 * shows. The family's own words and mark, never a channel's.
 */
final class ChannelMark {
    static final String NAME = "WHITEBROOK ROAD", SPORTS = "SPORTS", L1 = "WHITEBROOK", L2 = "ROAD ";
    static final int VIOLET = 0xFF6D28F2, CORAL = 0xFFFF6B6B, LILAC = 0xFFC2ADFF, PEACH = 0xFFFFA196, NIGHT = 0xFF07090F,
            DUSK = 0xFF1B1F31, WHITE = 0xFFFFFFFF, LIVE_RED = 0xFFFF3B4E;
    /** The frosted look's frost, as the frosted scoreboard's: its top and its foot. */
    static final int FROST_TOP = 0xFF9AA5BA, FROST_FOOT = 0xFF5D677D;
    /** The tag's boxes: white with the letters cut in the ground's colour, lined in white, or in the mark's colours. */
    private static final int SOLID = 0, LINED = 1, GRADED = 2, ON_COLOUR = 3;

    /** The picture tags the cover can show, as the Scoreboard card names them; AUTO reads it from the channel's name. */
    enum Tag {
        AUTO("Auto, from the channel", null), NONE("None", ""), HD("HD", "HD"), UHD("UHD", "UHD"), K4("4K", "4K"),
        UHD_HDR("UHD HDR", "UHD HDR"), K4_HDR("4K HDR", "4K HDR");

        final String label, shown;

        Tag(String label, String shown) {
            this.label = label;
            this.shown = shown;
        }

        /** What it shows on a channel of this name. */
        String on(String channel) {
            return shown != null ? shown : tagOf(channel);
        }

        static Tag of(String name) {
            for (Tag t : values()) if (t.name().equals(name)) return t;
            return AUTO;
        }
    }

    /**
     * Pure: the picture's tag from a channel's name: "4K" (4K, 2160p) or "UHD", with " HDR" when it says HDR (or HDR10,
     * Dolby Vision); "HD" for HD, FHD or 1080p; "" for none.
     */
    static String tagOf(String channel) {
        String n = channel == null ? "" : " " + channel.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ") + " ";
        boolean hdr = n.contains(" HDR ") || n.contains(" HDR10 ") || n.contains(" DOLBY VISION ") || n.contains(" DV ");
        String base = n.contains(" 4K ") || n.contains(" 2160P ") ? "4K" : n.contains(" UHD ") ? "UHD"
                : n.contains(" HD ") || n.contains(" FHD ") || n.contains(" 1080P ") ? "HD" : "";
        return hdr && !base.isEmpty() && !base.equals("HD") ? base + " HDR" : base;
    }

    /** The channel's own name for what is on: Grand Prix for Formula 1, Matchday for football. */
    static String channelFor(String sport) {
        return "f1".equals(sport) ? "GRAND PRIX" : "MATCHDAY";
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), cut = new Paint(Paint.ANTI_ALIAS_FLAG),
            line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path house = new Path(), play = new Path(), path = new Path(), clip = new Path();
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final Rect bounds = new Rect();
    private final Typeface black, heavy, bold, semi, medium;
    private final Map<Typeface, Float> caps = new HashMap<>();
    private String tag = "";
    private boolean live;
    /** How much further than the look's own width its ground runs (the competition's logo's room); 0 for none. */
    private float ext;
    /** How much further down than the look's own height its ground runs (LIVE's foot); 0 for none. */
    private float down;
    private String sport = "";

    ChannelMark(Context c) {
        black = outfit(c, 900);
        heavy = outfit(c, 800);
        bold = outfit(c, 700);
        semi = outfit(c, 600);
        medium = outfit(c, 500);
        // Homefield's mark, in its icon's 108 units: the house, and the play button cut through it.
        house.moveTo(31, 75);
        house.lineTo(31, 50);
        house.lineTo(54, 31);
        house.lineTo(77, 50);
        house.lineTo(77, 75);
        house.close();
        play.moveTo(49, 53);
        play.lineTo(49, 69);
        play.lineTo(63, 61);
        play.close();
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    private static final Map<Integer, Typeface> OUTFITS = new HashMap<>();

    /** Outfit (the TV launcher's letters, in the app's assets) at a weight, made once; the phone's bold if it cannot be read. */
    static synchronized Typeface outfit(Context c, int weight) {
        Typeface t = OUTFITS.get(weight);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(c.getAssets(), "fonts/outfit.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = Typeface.create(Typeface.DEFAULT, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        OUTFITS.put(weight, t);
        return t;
    }

    /** One look in the box (x, y, w, h): the tag ("" for none), live or not, the sport ("f1", else football). */
    void draw(Canvas cv, LogoCover.Look look, float x, float y, float w, float h, String tag, boolean live, String sport) {
        draw(cv, look, x, y, w, h, tag, live, sport, 0f);
    }

    /**
     * The same, its ground (and its bands and bars) running on extend further to the right than its words and mark, for
     * the competition's logo to stand in at the end of the one block.
     */
    void draw(Canvas cv, LogoCover.Look look, float x, float y, float w, float h, String tag, boolean live, String sport, float extend) {
        draw(cv, look, x, y, w, h, tag, live, sport, extend, 0f);
    }

    /** The same, its ground running on below too, by below (LIVE's foot under the look, part of the one block). */
    void draw(Canvas cv, LogoCover.Look look, float x, float y, float w, float h, String tag, boolean live, String sport, float extend,
              float below) {
        ext = Math.max(0f, extend);
        down = Math.max(0f, below);
        if (w <= 0 || h <= 0) return;
        this.tag = tag == null ? "" : tag.trim();
        this.live = live;
        this.sport = sport == null ? "" : sport;
        switch (look) {
            case MATCHDAY:
                matchday(cv, x, y, w, h);
                break;
            case ROOFLINE:
                roofline(cv, x, y, w, h);
                break;
            case VELOCITY:
                velocity(cv, x, y, w, h);
                break;
            case ROUNDEL:
                roundel(cv, x, y, w, h);
                break;
            case BLOCK:
                block(cv, x, y, w, h);
                break;
            case SMOKED:
                smoked(cv, x, y, w, h);
                break;
            case FROSTED:
                frosted(cv, x, y, w, h);
                break;
            default:
                signature(cv, x, y, w, h);
        }
        fill.setShader(null);
        cut.setShader(null);
        line.setShader(null);
        text.setShader(null);
    }

    // ---- The looks ----

    /** The mark in its tile, the name beside it in two heavy lines (SPORTS in the mark's colours), the tag after them. */
    private void signature(Canvas cv, float x, float y, float w, float h) {
        float rad = Math.min(w, h) * 0.16f;
        ground(cv, x, y, w, h, rad, DUSK, NIGHT);
        float pad = Math.min(h * 0.13f, w * 0.055f);
        float side = Math.min(h - 2 * pad, w * 0.27f);
        tile(cv, x + pad, y + (h - side) / 2f, side);
        float left = x + pad + side + pad * 0.85f;
        twoLines(cv, left, y, x + w - pad - left, h, side * 0.94f, heavy, heavy, true, SOLID, 0f);
    }

    /**
     * WHITEBROOK over ROAD SPORTS from left, both lines as wide, in room and no taller than tall, centred in (y, h); SPORTS
     * in the mark's colours (graded) or lighter; the tag after them (its boxes over each other when it has HDR); leaning
     * forward by lean.
     */
    private void twoLines(Canvas cv, float left, float y, float room, float h, float tall, Typeface t, Typeface sportsT,
                          boolean graded, int tagStyle, float lean) {
        float sp = 0.02f, w1 = widthAt(L1, t, 1f, sp), w2a = widthAt(L2, t, 1f, sp), w2b = widthAt(SPORTS, sportsT, 1f, sp);
        float lw = Math.max(w1, w2a + w2b), cap = capOf(t), gap = cap * 0.52f, lh = 2 * cap + gap;
        boolean stacked = hdr() && !main().isEmpty();
        float th = stacked ? lh * 0.47f : cap * 1.5f, tagU = tagW(th, stacked), tagGap = cap * 0.7f;
        float s = Math.min(room / (lw + lh * lean + (tagU > 0 ? tagGap + tagU : 0f)), tall / lh);
        float top = y + (h - lh * s) / 2f, base = top + lh * s;
        cv.save();
        if (lean > 0f) {
            // Leaning about the lockup's foot, so it stays where it is laid out.
            cv.translate(0, base);
            cv.skew(-lean, 0);
            cv.translate(0, -base);
        }
        words(cv, L1, t, s, sp, WHITE, null, left, top + cap * s, Paint.Align.LEFT);
        words(cv, L2, t, s, sp, WHITE, null, left, base, Paint.Align.LEFT);
        float sx = left + w2a * s;
        if (graded) words(cv, SPORTS, sportsT, s, sp, 0, grad(sx, 0, sx + w2b * s, 0, LILAC, PEACH), sx, base, Paint.Align.LEFT);
        else words(cv, SPORTS, sportsT, s, sp, 0xD9FFFFFF, null, sx, base, Paint.Align.LEFT);
        if (tagU > 0) {
            float tagH = stacked ? th * s * 2.18f : th * s;
            tagDraw(cv, left + (lw + tagGap) * s, top + (lh * s - tagH) / 2f, th * s, stacked, tagStyle, NIGHT);
        }
        cv.restore();
    }

    /** The name and the mark over a bar in the mark's colours: the channel's own name for what is on, the tag at its end. */
    private void matchday(Canvas cv, float x, float y, float w, float h) {
        float rad = Math.min(w, h) * 0.15f;
        ground(cv, x, y, w, h, rad, DUSK, NIGHT);
        float barH = h * 0.46f, rowH = h - barH;
        cv.save();
        clip.reset();
        r.set(x, y, x + w + ext, y + h + down);
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        fill.setShader(grad(x, 0, x + w + ext, 0, VIOLET, CORAL));
        cv.drawRect(x, y + rowH, x + w + ext, y + h, fill);
        fill.setShader(null);
        // A line of light where the bar meets the row, as a studio set's edge.
        fill.setColor(0x33FFFFFF);
        cv.drawRect(x, y + rowH, x + w + ext, y + rowH + Math.max(1f, h * 0.015f), fill);
        cv.restore();
        float pad = Math.min(rowH * 0.3f, w * 0.06f);
        // The row: the mark, the name, LIVE at its end while the game is.
        float mw = rowH * 0.58f, mh = mw * 49f / 51f;
        mark(cv, x + pad, y + (rowH - mh) / 2f + rowH * 0.02f, mw, WHITE, solid(DUSK));
        float left = x + pad + mw + pad * 0.6f;
        float room = x + w - pad - left, most = rowH * 0.36f / capOf(heavy), nameU = widthAt(NAME, heavy, 1f, 0.03f);
        float ns = Math.min(room / nameU, most);
        // LIVE at the row's end, as high as the name's capitals, only where the name keeps its full size.
        float lh = capOf(heavy) * most, liveW = live ? liveW(lh) : 0f;
        boolean liveFits = liveW > 0 && nameU * most + pad * 0.8f + liveW <= room;
        words(cv, NAME, heavy, ns, 0.03f, WHITE, null, left, y + (rowH + capOf(heavy) * ns) / 2f, Paint.Align.LEFT);
        if (liveFits) liveDraw(cv, x + w - pad - liveW, y + rowH / 2f, lh);
        // The bar: the channel's name spaced out, the tag in white at its end.
        String ch = channelFor(sport);
        // The name first: the tag as tall as its capitals and a half, and the name shrunk only as far as it must be.
        float chU = widthAt(ch, black, 1f, 0.16f), capC = capOf(black), cs = Math.min((w - 2 * pad) / chU, barH * 0.4f / capC);
        float th = Math.min(barH * 0.5f, capC * cs * 1.6f), tw = tagW(th, false);
        if (tw > 0 && chU * cs + pad * 0.6f + tw > w - 2 * pad) {
            // Both shrink together: the tag keeps to the name's size.
            float k = (w - 2 * pad - pad * 0.6f) / (chU * cs + tw);
            cs *= k;
            th *= k;
            tw = tagW(th, false);
        }
        words(cv, ch, black, cs, 0.16f, WHITE, null, x + pad, y + rowH + (barH + capOf(black) * cs) / 2f, Paint.Align.LEFT);
        if (tw > 0) tagDraw(cv, x + w - pad - tw, y + rowH + (barH - th) / 2f, th, false, ON_COLOUR, VIOLET);
    }

    /** The house as a badge in the mark's colours with WR cut in it; SPORTS large beside it, the name under, the tag. */
    private void roofline(Canvas cv, float x, float y, float w, float h) {
        float rad = Math.min(w, h) * 0.14f;
        ground(cv, x, y, w, h, rad, DUSK, NIGHT);
        float pad = Math.min(h * 0.13f, w * 0.06f);
        // The badge: the house's own outline, filled, as tall as the box leaves room for.
        float bh = h - 2 * pad, bw = Math.min(bh * 0.98f, w * 0.28f);
        bh = Math.min(bh, bw / 0.98f);
        float bx = x + pad, by = y + (h - bh) / 2f;
        houseBadge(cv, bx, by, bw, bh);
        float left = bx + bw + pad * 0.9f, room = x + w - pad - left;
        boolean hdr = tag.endsWith("HDR");
        // SPORTS in heavy capitals with the tag after it, the name spaced out under, as wide.
        float capS = capOf(black), sportsU = widthAt(SPORTS, black, 1f, 0f);
        float tagU = tagW(capS, false), line1 = sportsU + (tagU > 0 ? capS * 0.4f + tagU : 0f);
        float s = Math.min(room / line1, bh * 0.5f / capS);
        float sw = line1 * s;
        float nameS = sizeTo(NAME, semi, 0.12f, sw);
        nameS = Math.min(nameS, s * 0.42f);
        float capN = capOf(semi) * nameS, gap = capS * s * 0.42f;
        float top = y + (h - capS * s - gap - capN) / 2f;
        words(cv, SPORTS, black, s, 0f, WHITE, null, left, top + capS * s, Paint.Align.LEFT);
        if (tagU > 0) tagDraw(cv, left + sportsU * s + capS * s * 0.4f, top, capS * s, false, hdr ? SOLID : LINED, NIGHT);
        float sp = spacingTo(NAME, semi, nameS, sw);
        words(cv, NAME, semi, nameS, sp, 0xD9FFFFFF, null, left, top + capS * s + gap + capN, Paint.Align.LEFT);
    }

    /** Leaning forward: a blade in the mark's colours with the mark on it, two slashes, the name in heavy italic, the tag. */
    private void velocity(Canvas cv, float x, float y, float w, float h) {
        float rad = Math.min(w, h) * 0.12f;
        ground(cv, x, y, w, h, rad, DUSK, NIGHT);
        float lean = 0.26f, pad = Math.min(h * 0.13f, w * 0.055f);
        // The blade: from the left edge, its far side leaning forward; two thin slashes after it.
        float bladeW = Math.min(h * 0.6f, w * 0.21f), top = h * lean;
        cv.save();
        clip.reset();
        r.set(x, y, x + w + ext, y + h + down);
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        Shader blade = grad(x, y, x + bladeW + top, y + h, VIOLET, CORAL);
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x + bladeW + top, y);
        path.lineTo(x + bladeW, y + h);
        path.lineTo(x, y + h);
        path.close();
        fill.setShader(blade);
        cv.drawPath(path, fill);
        float sl = h * 0.05f, after = x + bladeW + h * 0.07f;
        for (int i = 0; i < 2; i++) {
            float sx = after + i * sl * 2.2f;
            path.reset();
            path.moveTo(sx + top, y);
            path.lineTo(sx + top + sl, y);
            path.lineTo(sx + sl, y + h);
            path.lineTo(sx, y + h);
            path.close();
            fill.setAlpha(i == 0 ? 0xE0 : 0x8C);
            cv.drawPath(path, fill);
        }
        fill.setAlpha(0xFF);
        fill.setShader(null);
        cv.restore();
        float mw = Math.min(h * 0.44f, bladeW * 0.74f), mh = mw * 49f / 51f;
        mark(cv, x + (bladeW + top * 0.5f - mw) / 2f, y + (h - mh) / 2f, mw, WHITE, gradPaint(x, y, x + bladeW + top, y + h));
        float left = after + sl * 2.2f + sl + pad * 0.9f;
        twoLines(cv, left, y, x + w - pad - left, h, (h - 2 * pad) * 0.92f, black, black, true, SOLID, 0.2f);
    }

    /** The mark in a ring of its colours; the name in its own letters over a fine rule and SPORTS spaced out; the tag lined. */
    private void roundel(Canvas cv, float x, float y, float w, float h) {
        ground(cv, x, y, w, h, Math.min(w, h) * 0.22f, DUSK, NIGHT);
        float pad = Math.min(h * 0.13f, w * 0.055f);
        float d = Math.min(h - 2 * pad, w * 0.25f), cx = x + pad + d / 2f, cy = y + h / 2f;
        line.setShader(grad(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f, VIOLET, CORAL));
        float ring = Math.max(1.5f, d * 0.08f);
        line.setStrokeWidth(ring);
        cv.drawCircle(cx, cy, d / 2f - ring / 2f, line);
        line.setShader(null);
        float mw = d * 0.5f, mh = mw * 49f / 51f;
        mark(cv, cx - mw / 2f, cy - mh / 2f - d * 0.01f, mw, WHITE, gradPaint(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f));
        float left = x + pad + d + pad * 0.85f, room = x + w - pad - left;
        // At size 1: the name, the rule and SPORTS under it (half as big, spaced to the name's width), the tag after.
        String name = "Whitebrook Road";
        float nameU = widthAt(name, bold, 1f, 0f), capN = capOf(bold), gap = capN * 0.42f, rule = capN * 0.1f;
        float capS = capOf(bold) * 0.6f, lh = capN + gap + rule + gap + capS;
        boolean stacked = hdr() && !main().isEmpty();
        float th = stacked ? lh * 0.47f : capN * 1.45f, tagU = tagW(th, stacked), tagGap = capN * 0.8f;
        float s = Math.min(room / (nameU + (tagU > 0 ? tagGap + tagU : 0f)), d * 0.8f / lh);
        float top = cy - lh * s / 2f;
        words(cv, name, bold, s, 0f, WHITE, null, left, top + capN * s, Paint.Align.LEFT);
        float nw = nameU * s, ry = top + (capN + gap) * s;
        fill.setShader(grad(left, 0, left + nw, 0, VIOLET, CORAL));
        cv.drawRect(left, ry, left + nw, ry + Math.max(1f, rule * s), fill);
        fill.setShader(null);
        float ss = s * 0.6f;
        words(cv, SPORTS, bold, ss, spacingTo(SPORTS, bold, ss, nw), 0xD9FFFFFF, null, left, top + lh * s, Paint.Align.LEFT);
        if (tagU > 0) {
            float tagH = stacked ? th * s * 2.18f : th * s;
            tagDraw(cv, left + (nameU + tagGap) * s, cy - tagH / 2f, th * s, stacked, LINED, NIGHT);
        }
    }

    /** One solid block: a band in the mark's colours with the mark and the name, SPORTS large, LIVE and the tag at its foot. */
    private void block(Canvas cv, float x, float y, float w, float h) {
        float rad = Math.min(w, h) * 0.1f;
        ground(cv, x, y, w, h, rad, DUSK, NIGHT);
        boolean wide = w / h >= 2.4f;
        float band = h * (wide ? 0.4f : 0.32f), pad = Math.min(w, h) * 0.09f;
        cv.save();
        clip.reset();
        r.set(x, y, x + w + ext, y + h + down);
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        fill.setShader(grad(x, y, x + w + ext, y + band, VIOLET, CORAL));
        cv.drawRect(x, y, x + w + ext, y + band, fill);
        fill.setShader(null);
        cv.restore();
        float mw = band * 0.56f, mh = mw * 49f / 51f;
        mark(cv, x + pad, y + (band - mh) / 2f, mw, WHITE, gradPaint(x, y, x + w, y + band));
        float left = x + pad + mw + pad * 0.55f;
        float ns = Math.min((x + w - pad - left) / widthAt(NAME, heavy, 1f, 0.04f), band * 0.36f / capOf(heavy));
        words(cv, NAME, heavy, ns, 0.04f, WHITE, null, left, y + (band + capOf(heavy) * ns) / 2f, Paint.Align.LEFT);
        float body = h - band;
        if (wide) {
            // SPORTS from the left, the tag and LIVE at the row's end.
            float th = body * 0.36f, tw = tagW(th, false), lw = live ? liveW(th * 0.8f) : 0f;
            float most = body * 0.5f / capOf(black), sportsU = widthAt(SPORTS, black, 1f, 0.02f);
            // LIVE only where SPORTS keeps its full size beside it and the tag.
            if (lw > 0 && sportsU * most + pad * 0.6f + lw + (tw > 0 ? tw + pad * 0.5f : 0f) > w - 2 * pad) lw = 0f;
            float end = (tw > 0 ? tw : 0f) + (lw > 0 ? lw + (tw > 0 ? pad * 0.5f : 0f) : 0f);
            float ss = Math.min((w - 2 * pad - (end > 0 ? end + pad * 0.6f : 0f)) / sportsU, most);
            words(cv, SPORTS, black, ss, 0.02f, WHITE, null, x + pad, y + band + (body + capOf(black) * ss) / 2f, Paint.Align.LEFT);
            float ex = x + w - pad;
            if (tw > 0) {
                tagDraw(cv, ex - tw, y + band + (body - th) / 2f, th, false, SOLID, NIGHT);
                ex -= tw + pad * 0.5f;
            }
            if (lw > 0) liveDraw(cv, ex - lw, y + band + body / 2f, th * 0.8f);
        } else {
            // SPORTS large in the middle; LIVE and the tag along the foot.
            float foot = body * 0.36f, mid = body - foot;
            float ss = Math.min((w - 2 * pad) / widthAt(SPORTS, black, 1f, 0.03f), mid * 0.62f / capOf(black));
            words(cv, SPORTS, black, ss, 0.03f, WHITE, null, x + pad, y + band + (mid + capOf(black) * ss) / 2f + mid * 0.06f, Paint.Align.LEFT);
            float th = foot * 0.62f, tw = tagW(th, false), fy = y + band + mid;
            if (tw > 0) tagDraw(cv, x + w - pad - tw, fy + (foot - th) / 2f - foot * 0.08f, th, false, SOLID, NIGHT);
            if (live) liveDraw(cv, x + pad, fy + foot / 2f - foot * 0.08f, th * 0.9f);
        }
    }

    /** Smoked glass, as channels' own logos sit on the picture: the mark, the name in two white lines, the tag lined. */
    private void smoked(Canvas cv, float x, float y, float w, float h) {
        float rad = Math.min(w, h) * 0.18f;
        r.set(x, y, x + w + ext, y + h + down);
        fill.setShader(grad(0, y, 0, y + h + down, 0xC8262A36, 0xC40D0F16));
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        // Its edge catching the light along the top.
        line.setStrokeWidth(Math.max(1f, h * 0.014f));
        line.setShader(grad(0, y, 0, y + h + down, 0x59FFFFFF, 0x14FFFFFF));
        r.inset(line.getStrokeWidth() / 2f, line.getStrokeWidth() / 2f);
        cv.drawRoundRect(r, rad, rad, line);
        line.setShader(null);
        float pad = Math.min(h * 0.15f, w * 0.06f);
        float mw = Math.min((h - 2 * pad) * 0.66f, w * 0.2f), mh = mw * 49f / 51f;
        mark(cv, x + pad * 1.1f, y + (h - mh) / 2f, mw, 0xF2FFFFFF, solid(0xFF1B1E28));
        float left = x + pad * 1.1f + mw + pad * 0.8f;
        twoLines(cv, left, y, x + w - pad - left, h, (h - 2 * pad) * 0.8f, heavy, medium, false, LINED, 0f);
    }

    /**
     * Frosted glass, as the frosted scoreboard: a silvery frost falling darker to its foot, a soft sheen over its top half
     * and a fine white edge; the mark and both lines in white.
     */
    private void frosted(Canvas cv, float x, float y, float w, float h) {
        float rad = Math.min(w, h) * 0.18f;
        r.set(x, y, x + w + ext, y + h + down);
        fill.setShader(grad(0, y, 0, y + h + down, FROST_TOP, FROST_FOOT));
        cv.drawRoundRect(r, rad, rad, fill);
        // The sheen: light across its top, gone by its middle.
        fill.setShader(grad(0, y, 0, y + h * 0.55f, 0x47FFFFFF, 0x00FFFFFF));
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        line.setStrokeWidth(Math.max(1f, h * 0.016f));
        line.setShader(grad(0, y, 0, y + h + down, 0xB3FFFFFF, 0x4DFFFFFF));
        r.inset(line.getStrokeWidth() / 2f, line.getStrokeWidth() / 2f);
        cv.drawRoundRect(r, rad, rad, line);
        line.setShader(null);
        float pad = Math.min(h * 0.15f, w * 0.06f);
        float mw = Math.min((h - 2 * pad) * 0.66f, w * 0.2f), mh = mw * 49f / 51f;
        mark(cv, x + pad * 1.1f, y + (h - mh) / 2f, mw, WHITE, solid(FROST_FOOT));
        float left = x + pad * 1.1f + mw + pad * 0.8f;
        twoLines(cv, left, y, x + w - pad - left, h, (h - 2 * pad) * 0.8f, heavy, medium, false, LINED, 0f);
    }

    // ---- Parts ----

    /** The block's ground: a soft fall of light from its top, and a fine light edge. */
    private void ground(Canvas cv, float x, float y, float w, float h, float rad, int top, int bottom) {
        r.set(x, y, x + w + ext, y + h + down);
        fill.setShader(grad(0, y, 0, y + h + down, top, bottom));
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        float e = Math.max(1f, h * 0.012f);
        line.setStrokeWidth(e);
        line.setShader(grad(0, y, 0, y + h + down, 0x30FFFFFF, 0x0AFFFFFF));
        r.inset(e / 2f, e / 2f);
        cv.drawRoundRect(r, rad, rad, line);
        line.setShader(null);
    }

    /** The phone's icon: the mark on a tile of violet to coral, a soft light in its top corner. */
    private void tile(Canvas cv, float x, float y, float s) {
        r.set(x, y, x + s, y + s);
        float tr = s * 0.24f;
        fill.setShader(grad(x, y, x + s, y + s, VIOLET, CORAL));
        cv.drawRoundRect(r, tr, tr, fill);
        fill.setShader(new RadialGradient(x + s * 0.28f, y + s * 0.18f, s * 0.6f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, tr, tr, fill);
        fill.setShader(null);
        float mw = s * 0.62f, mh = mw * 49f / 51f;
        mark(cv, x + (s - mw) / 2f, y + (s - mh) / 2f, mw, WHITE, gradPaint(x, y, x + s, y + s));
    }

    /** The house's outline as a badge in the mark's colours, WR in white in it. */
    private void houseBadge(Canvas cv, float x, float y, float w, float h) {
        // The house in w by h: walls from 46% down, the roof's peak at the top, its corners rounded.
        float rr = Math.min(w, h) * 0.08f;
        path.reset();
        path.moveTo(x + w / 2f, y);
        path.lineTo(x + w, y + h * 0.42f);
        path.lineTo(x + w, y + h);
        path.lineTo(x, y + h);
        path.lineTo(x, y + h * 0.42f);
        path.close();
        fill.setShader(grad(x, y, x + w, y + h, VIOLET, CORAL));
        fill.setPathEffect(new android.graphics.CornerPathEffect(rr));
        cv.drawPath(path, fill);
        fill.setPathEffect(null);
        fill.setShader(null);
        // WR in the house's body, under the roof.
        float body = h * 0.58f, s = Math.min(w * 0.7f / widthAt("WR", black, 1f, -0.02f), body * 0.62f / capOf(black));
        words(cv, "WR", black, s, -0.02f, WHITE, null, x + w / 2f, y + h - (body - capOf(black) * s) / 2f - body * 0.05f, Paint.Align.CENTER);
    }

    /** Homefield's mark wd wide at (x, y), the house in this colour, the play button painted (never a hole) with cutPaint. */
    private void mark(Canvas cv, float x, float y, float wd, int houseColour, Paint cutPaint) {
        // The house with its 5-unit round edge is 51 units across, 49 high, from (28.5, 28.5).
        float k = wd / 51f;
        m.reset();
        m.setScale(k, k);
        m.postTranslate(x - 28.5f * k, y - 28.5f * k);
        house.transform(m, path);
        fill.setShader(null);
        fill.setColor(houseColour);
        fill.setStyle(Paint.Style.FILL_AND_STROKE);
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setStrokeWidth(5 * k);
        cv.drawPath(path, fill);
        fill.setStyle(Paint.Style.FILL);
        play.transform(m, path);
        cutPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        cutPaint.setStrokeJoin(Paint.Join.ROUND);
        cutPaint.setStrokeWidth(2.6f * k);
        cv.drawPath(path, cutPaint);
    }

    private Paint solid(int colour) {
        cut.setShader(null);
        cut.setColor(colour);
        return cut;
    }

    private Paint gradPaint(float x0, float y0, float x1, float y1) {
        cut.setColor(WHITE);
        cut.setShader(grad(x0, y0, x1, y1, VIOLET, CORAL));
        return cut;
    }

    private static Shader grad(float x0, float y0, float x1, float y1, int a, int b) {
        return new LinearGradient(x0, y0, x1, y1, a, b, Shader.TileMode.CLAMP);
    }

    /** The tag's main part ("UHD" of "UHD HDR") and whether it has HDR. */
    private String main() {
        return tag.endsWith("HDR") ? tag.substring(0, tag.length() - 3).trim() : tag;
    }

    private boolean hdr() {
        return tag.endsWith("HDR");
    }

    /** The tag's width at box height th: its box and HDR's beside it, or (stacked) over each other. */
    private float tagW(float th, boolean stacked) {
        if (tag.isEmpty()) return 0f;
        String a = main();
        float wa = boxW(a, th), wb = hdr() ? boxW("HDR", th) : 0f;
        if (a.isEmpty()) return wb;
        if (wb == 0f) return wa;
        return stacked ? Math.max(wa, wb) : wa + th * 0.18f + wb;
    }

    private float boxW(String s, float th) {
        return s.isEmpty() ? 0f : widthAt(s, heavy, tagSize(th), 0.04f) + th * 0.62f;
    }

    private float tagSize(float th) {
        return th * 0.54f / capOf(heavy);
    }

    /**
     * The tag from (x, y), boxes th high: the main part in this style, HDR in the mark's colours; lined where the main part
     * is, and on a ground already in the mark's colours (ON_COLOUR: the main part white, HDR lined).
     */
    private void tagDraw(Canvas cv, float x, float y, float th, boolean stacked, int style, int groundInk) {
        String a = main();
        boolean b = hdr();
        float wa = boxW(a, th), wb = b ? boxW("HDR", th) : 0f, both = Math.max(wa, wb);
        if (!a.isEmpty()) box(cv, a, x, y, stacked && b ? both : wa, th, style == ON_COLOUR ? SOLID : style, groundInk);
        if (b) {
            float hx = a.isEmpty() || stacked ? x : x + wa + th * 0.18f, hy = stacked && !a.isEmpty() ? y + th * 1.18f : y;
            box(cv, "HDR", hx, hy, stacked ? both : wb, th, style == LINED || style == ON_COLOUR ? LINED : GRADED, groundInk);
        }
    }

    private void box(Canvas cv, String s, float x, float y, float w, float th, int style, int groundInk) {
        r.set(x, y, x + w, y + th);
        float br = th * 0.2f;
        int ink = WHITE;
        if (style == SOLID) {
            fill.setColor(WHITE);
            cv.drawRoundRect(r, br, br, fill);
            ink = groundInk;
        } else if (style == GRADED) {
            fill.setShader(grad(x, y, x + w, y + th, VIOLET, CORAL));
            cv.drawRoundRect(r, br, br, fill);
            fill.setShader(null);
        } else {
            float e = Math.max(1f, th * 0.09f);
            line.setStrokeWidth(e);
            line.setColor(0xF2FFFFFF);
            r.inset(e / 2f, e / 2f);
            cv.drawRoundRect(r, br, br, line);
        }
        float size = tagSize(th);
        words(cv, s, heavy, size, 0.04f, ink, null, x + w / 2f, y + (th + capOf(heavy) * size) / 2f, Paint.Align.CENTER);
    }

    /** LIVE with its red dot, its letters th high: its width. */
    private float liveW(float th) {
        float size = th / capOf(bold);
        return th * 1.3f + widthAt("LIVE", bold, size, 0.12f);
    }

    /** LIVE from x, centred on cy. */
    private void liveDraw(Canvas cv, float x, float cy, float th) {
        fill.setColor(LIVE_RED);
        cv.drawCircle(x + th * 0.42f, cy, th * 0.42f, fill);
        float size = th / capOf(bold);
        words(cv, "LIVE", bold, size, 0.12f, WHITE, null, x + th * 1.3f, cy + th / 2f, Paint.Align.LEFT);
    }

    // ---- Words ----

    /** Its capitals' height for a size of 1. */
    private float capOf(Typeface t) {
        Float k = caps.get(t);
        if (k == null) {
            text.setTypeface(t);
            text.setTextSize(100f);
            text.setLetterSpacing(0f);
            text.getTextBounds("H", 0, 1, bounds);
            k = bounds.height() / 100f;
            caps.put(t, k);
        }
        return k;
    }

    private float widthAt(String s, Typeface t, float size, float spacing) {
        text.setTypeface(t);
        text.setTextSize(100f);
        text.setLetterSpacing(spacing);
        float wd = text.measureText(s) * size / 100f;
        text.setLetterSpacing(0f);
        return wd;
    }

    /** The size at which s, so spaced, is wd wide. */
    private float sizeTo(String s, Typeface t, float spacing, float wd) {
        return wd / Math.max(0.01f, widthAt(s, t, 1f, spacing));
    }

    /** The letter spacing at which s, at this size, is wd wide (none if it is wider already). */
    private float spacingTo(String s, Typeface t, float size, float wd) {
        float plain = widthAt(s, t, size, 0f);
        return Math.max(0f, Math.min(1.2f, (wd - plain) / Math.max(1f, s.length() * size)));
    }

    private void words(Canvas cv, String s, Typeface t, float size, float spacing, int colour, Shader shader, float x, float base,
                       Paint.Align align) {
        if (size <= 0.5f) return;
        text.setTypeface(t);
        text.setTextSize(size);
        text.setLetterSpacing(spacing);
        text.setTextAlign(align);
        text.setShader(shader);
        if (shader == null) text.setColor(colour);
        else text.setColor(WHITE);
        cv.drawText(s, x, base, text);
        text.setShader(null);
        text.setLetterSpacing(0f);
    }
}
