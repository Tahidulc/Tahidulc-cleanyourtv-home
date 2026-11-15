package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * The channel logos to match the Scoreboard Lab's boards, drawn the same on the TV and the phone: Floodlight (a white
 * plate, the family's house dark, the name heavy over SPORTS spaced in grey), Split kit (an angled violet-to-coral block
 * with the white house, then the name in heavy italics on black over SPORTS in peach), Ticker (a slim dark plate, the
 * house, a thin violet-to-coral bar, WR SPORTS), Duo pills (a dark pill, the house on a violet-to-coral disc, the name)
 * and Stadium LED (black, the house and WR SPORTS in glowing amber under a fine dot matrix). The family's own words and
 * mark (the Homefield house with its play cut in), never a channel's name or logo.
 */
public final class LabMarks {
    /** The looks, as the logo pickers name them. */
    public enum Look {
        FLOODLIGHT("Floodlight"), SPLITKIT("Split kit"), TICKER("Ticker"), DUO("Duo pills"), LED("Stadium LED");

        public final String label;

        Look(String label) {
            this.label = label;
        }

        /** The look of a name as kept in the settings; null for none of these. */
        public static Look of(String name) {
            for (Look l : values()) if (l.name().equals(name)) return l;
            return null;
        }
    }

    /** The name when none is given. */
    public static final String NAME = "Whitebrook Road";
    static final int VIOLET = 0xFF6D28F2, CORAL = 0xFFFF6B6B, PEACH = 0xFFFFA196, CUT = 0xFFB04A8F, PLATE = 0xFFF6F7FA,
            INK = 0xFF0B0E16, GREY = 0xFF5A6275, AMBER = 0xFFFFB21E, BLACK = 0xFF050505, EDGE = 0xFF1A1A1A, WHITE = 0xFFFFFFFF;

    private static Typeface outfit, mono;
    private static final String W600 = "'wght' 620", W800 = "'wght' 800", W900 = "'wght' 900";
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path house = new Path(), play = new Path(), shape = new Path();
    private final RectF r = new RectF();
    private final Rect bounds = new Rect();
    private Bitmap dotTile;
    private BitmapShader dotShader;
    private int dotCell;

    public LabMarks(Context c) {
        synchronized (LabMarks.class) {
            if (outfit == null) {
                try {
                    outfit = Typeface.createFromAsset(c.getAssets(), "broadcast/fonts/outfit.ttf");
                } catch (RuntimeException e) {
                    outfit = Typeface.DEFAULT_BOLD;
                }
            }
            if (mono == null) mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        }
    }

    // ---- the words ----------------------------------------------------------------------------------------------------

    /** Pure: the name as given, or the family's when there is none. */
    static String name(String name) {
        return name == null || name.trim().isEmpty() ? NAME : name.trim();
    }

    /** Pure: Floodlight's heavy word: the name in capitals, its first word alone when the whole is long ("WHITEBROOK"). */
    public static String bigName(String name) {
        String n = name(name).toUpperCase(Locale.ROOT);
        if (n.length() <= 12) return n;
        int sp = n.indexOf(' ');
        return sp > 0 ? n.substring(0, sp) : n;
    }

    /** Pure: the name's initials ("Whitebrook Road" WR), three at most. */
    public static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name(name).split("\\s+")) {
            if (w.isEmpty() || !Character.isLetterOrDigit(w.charAt(0))) continue;
            b.append(Character.toUpperCase(w.charAt(0)));
            if (b.length() == 3) break;
        }
        return b.toString();
    }

    /** Pure: the slim looks' words: the initials and SPORTS ("WR SPORTS"); the whole name in capitals when sports is dropped. */
    public static String slimWords(String name, String sports) {
        String s = sports == null ? "" : sports.trim().toUpperCase(Locale.ROOT);
        return s.isEmpty() ? name(name).toUpperCase(Locale.ROOT) : initials(name) + " " + s;
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    // ---- size ---------------------------------------------------------------------------------------------------------

    /** Its width at height h for these words (sports: "SPORTS" or "" for a general channel; tag: HD, UHD or ""). */
    public float width(Look l, float h, String name, String sports, String tag) {
        if (l == null || h <= 0) return 0;
        float u = h, w;
        switch (l) {
            case FLOODLIGHT: {
                float top = tw(bigName(name), u * 0.26f, outfit, W900, 0.02f), low = tw(upper(sports), u * 0.161f, outfit, W600, 0.3f);
                w = u * 0.24f + u * 0.5f + u * 0.14f + Math.max(top, low) + u * 0.24f;
                break;
            }
            case SPLITKIT: {
                float top = tw(upper(name(name)), u * 0.24f, outfit, W900, 0f) * 1.04f, low = tw(upper(sports), u * 0.16f, outfit, W600, 0.3f);
                w = u * 0.8f + u * 0.3f + Math.max(top, low) + u * 0.3f;
                break;
            }
            case TICKER:
                w = u * 0.32f + u * 0.6f + u * 0.21f + u * 0.08f + u * 0.21f + tw(slimWords(name, sports), u * 0.42f, outfit, W800, 0.1f) + u * 0.32f;
                break;
            case DUO:
                w = u * 0.125f + u * 0.78f + u * 0.21f + tw(name(name), u * 0.3f, outfit, W800, 0.03f) + u * 0.33f;
                break;
            case LED: default:
                w = u * 0.27f + u * 0.55f + u * 0.19f + tw(slimWords(name, sports), u * 0.3f, mono, null, 0.14f) + u * 0.27f;
                break;
        }
        String t = upper(tag);
        if (!t.isEmpty()) w += u * 0.16f + tw(t, u * 0.2f, outfit, W800, 0.06f);
        return w;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** The look drawn into w by h at (0, 0), its words left from its start as the Lab lays them out. */
    public void draw(Canvas cv, Look l, float w, float h, String name, String sports, String tag) {
        if (l == null || w <= 0 || h <= 0) return;
        float u = h;
        String t = upper(tag);
        float tagW = t.isEmpty() ? 0f : u * 0.16f + tw(t, u * 0.2f, outfit, W800, 0.06f);
        switch (l) {
            case FLOODLIGHT: floodlight(cv, w, u, name, sports, t, tagW); break;
            case SPLITKIT: splitKit(cv, w, u, name, sports, t, tagW); break;
            case TICKER: ticker(cv, w, u, name, sports, t, tagW); break;
            case DUO: duo(cv, w, u, name, t, tagW); break;
            case LED: default: led(cv, w, u, name, sports, t, tagW); break;
        }
    }

    private void floodlight(Canvas cv, float w, float u, String name, String sports, String tag, float tagW) {
        r.set(0, 0, w, u);
        fill.setShader(null);
        fill.setColor(PLATE);
        cv.drawRoundRect(r, u * 0.074f, u * 0.074f, fill);
        float ms = u * 0.5f;
        mark(cv, u * 0.24f, (u - ms * 50f / 52f) / 2f, ms, INK, PLATE);
        float x = u * 0.24f + ms + u * 0.14f;
        String low = upper(sports);
        if (low.isEmpty()) {
            words(cv, bigName(name), x, u / 2f, u * 0.26f, INK, outfit, W900, 0.02f, -1);
        } else {
            words(cv, bigName(name), x, u * 0.41f, u * 0.26f, INK, outfit, W900, 0.02f, -1);
            words(cv, low, x, u * 0.65f, u * 0.161f, GREY, outfit, W600, 0.3f, -1);
        }
        tag(cv, tag, w - u * 0.24f, u, tagW, GREY);
    }

    private void splitKit(Canvas cv, float w, float u, String name, String sports, String tag, float tagW) {
        float rad = u * 0.11f;
        // The words' black block, starting under the mark's slant.
        r.set(u * 0.8f, 0, w, u);
        fill.setShader(null);
        fill.setColor(INK);
        cv.drawRoundRect(r, rad, rad, fill);
        cv.drawRect(u * 0.8f, 0, u * 0.8f + rad, u, fill);
        // The mark's block: violet to coral, its start rounded, its end slanted.
        shape.reset();
        shape.moveTo(rad, 0);
        shape.lineTo(u, 0);
        shape.lineTo(u * 0.8f, u);
        shape.lineTo(rad, u);
        shape.quadTo(0, u, 0, u - rad);
        shape.lineTo(0, rad);
        shape.quadTo(0, 0, rad, 0);
        shape.close();
        fill.setShader(new LinearGradient(0, 0, u, u, VIOLET, CORAL, Shader.TileMode.CLAMP));
        cv.drawPath(shape, fill);
        fill.setShader(null);
        float ms = u * 0.55f;
        mark(cv, (u * 0.9f - ms) / 2f, (u - ms * 50f / 52f) / 2f, ms, WHITE, CUT);
        float x = u * 1.1f;
        String low = upper(sports);
        text.setTextSkewX(-0.2f);
        if (low.isEmpty()) {
            words(cv, upper(name(name)), x, u / 2f, u * 0.24f, WHITE, outfit, W900, 0f, -1);
            text.setTextSkewX(0f);
        } else {
            words(cv, upper(name(name)), x, u * 0.42f, u * 0.24f, WHITE, outfit, W900, 0f, -1);
            text.setTextSkewX(0f);
            words(cv, low, x, u * 0.66f, u * 0.16f, PEACH, outfit, W600, 0.3f, -1);
        }
        tag(cv, tag, w - u * 0.3f, u, tagW, PEACH);
    }

    private void ticker(Canvas cv, float w, float u, String name, String sports, String tag, float tagW) {
        r.set(0, 0, w, u);
        fill.setShader(null);
        fill.setColor(0xDB080A10);
        cv.drawRoundRect(r, u * 0.08f, u * 0.08f, fill);
        float ms = u * 0.6f, x = u * 0.32f;
        mark(cv, x, (u - ms * 50f / 52f) / 2f, ms, WHITE, INK);
        x += ms + u * 0.21f;
        r.set(x, u * 0.16f, x + u * 0.08f, u * 0.84f);
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, VIOLET, CORAL, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, u * 0.05f, u * 0.05f, fill);
        fill.setShader(null);
        x += u * 0.08f + u * 0.21f;
        words(cv, slimWords(name, sports), x, u / 2f, u * 0.42f, WHITE, outfit, W800, 0.1f, -1);
        tag(cv, tag, w - u * 0.32f, u, tagW, 0xFFCFD5E4);
    }

    private void duo(Canvas cv, float w, float u, String name, String tag, float tagW) {
        r.set(0, 0, w, u);
        fill.setShader(null);
        fill.setColor(0xD1080A10);
        cv.drawRoundRect(r, u / 2f, u / 2f, fill);
        float d = u * 0.78f, cx = u * 0.125f + d / 2f, cy = u / 2f;
        fill.setShader(new LinearGradient(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f, VIOLET, CORAL, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, d / 2f, fill);
        fill.setShader(null);
        float ms = d * 0.58f;
        mark(cv, cx - ms / 2f, cy - ms * 50f / 52f / 2f, ms, WHITE, CUT);
        words(cv, name(name), u * 0.125f + d + u * 0.21f, cy, u * 0.3f, WHITE, outfit, W800, 0.03f, -1);
        tag(cv, tag, w - u * 0.33f, u, tagW, 0xFFCFD5E4);
    }

    private void led(Canvas cv, float w, float u, String name, String sports, String tag, float tagW) {
        float rad = u * 0.115f, edge = Math.max(1f, u * 0.04f);
        r.set(0, 0, w, u);
        fill.setShader(null);
        fill.setColor(EDGE);
        cv.drawRoundRect(r, rad, rad, fill);
        r.set(edge, edge, w - edge, u - edge);
        fill.setColor(BLACK);
        cv.drawRoundRect(r, rad - edge, rad - edge, fill);
        float ms = u * 0.55f, x = u * 0.27f;
        fill.setShadowLayer(u * 0.08f, 0, 0, 0xE6FFB21E);
        mark(cv, x, (u - ms * 50f / 52f) / 2f, ms, AMBER, BLACK);
        fill.clearShadowLayer();
        x += ms + u * 0.19f;
        text.setShadowLayer(u * 0.12f, 0, 0, 0xE6FFB21E);
        words(cv, slimWords(name, sports), x, u / 2f, u * 0.3f, AMBER, mono, null, 0.14f, -1);
        tag(cv, tag, w - u * 0.27f, u, tagW, AMBER);
        text.clearShadowLayer();
        dotMatrix(cv, 0, 0, w, u, u, rad);
    }

    /** The picture's tag (HD, UHD) small at the end, up to x. */
    private void tag(Canvas cv, String tag, float x, float u, float tagW, int colour) {
        if (tag == null || tag.isEmpty() || tagW <= 0f) return;
        words(cv, tag, x, u / 2f, u * 0.2f, colour, outfit, W800, 0.06f, 1);
    }

    /** A fine dark dot matrix over l..rr, t..b (round-cornered as the plate), its cell made once for its size. */
    private void dotMatrix(Canvas cv, float l, float t, float rr, float b, float u, float rad) {
        int cell = Math.max(3, Math.round(u / 15f));
        if (dotShader == null || dotCell != cell) {
            int[] px = new int[cell * cell];
            float c = (cell - 1) / 2f, far = (float) Math.hypot(c + 0.5f, c + 0.5f);
            for (int y = 0; y < cell; y++) {
                for (int x = 0; x < cell; x++) px[y * cell + x] = Math.hypot(x - c, y - c) / far > 0.555f ? 0x8C000000 : 0;
            }
            Bitmap tile = Bitmap.createBitmap(px, cell, cell, Bitmap.Config.ARGB_8888);
            if (dotTile != null && !dotTile.isRecycled()) dotTile.recycle();
            dotTile = tile;
            dotShader = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            dotCell = cell;
        }
        fill.setShader(dotShader);
        r.set(l, t, rr, b);
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
    }

    /** The family's house-and-play mark, size wide at (l, t): the house in ink with its corners round, the play cut in cut. */
    private void mark(Canvas cv, float l, float t, float size, int ink, int cut) {
        house(house, play, l, t, size);
        Paint.Style was = fill.getStyle();
        fill.setStyle(Paint.Style.FILL_AND_STROKE);
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setStrokeWidth(houseStroke(size));
        fill.setColor(ink);
        cv.drawPath(house, fill);
        fill.setStrokeWidth(playStroke(size));
        fill.setColor(cut);
        cv.drawPath(play, fill);
        fill.setStyle(was);
        fill.setStrokeWidth(0f);
    }

    /**
     * The family's mark as paths, size wide at (l, t) (its height 50/52 of that): the house and the play triangle cut into
     * it, laid out as the Lab's drawing of it (viewBox 28 28 52 50). Drawn filled and stroked (houseStroke, playStroke, round
     * joins) they have its round corners.
     */
    public static void house(Path houseOut, Path playOut, float l, float t, float size) {
        float k = size / 52f;
        houseOut.reset();
        houseOut.moveTo(l + (31 - 28) * k, t + (75 - 28) * k);
        houseOut.lineTo(l + (31 - 28) * k, t + (50 - 28) * k);
        houseOut.lineTo(l + (54 - 28) * k, t + (31 - 28) * k);
        houseOut.lineTo(l + (77 - 28) * k, t + (50 - 28) * k);
        houseOut.lineTo(l + (77 - 28) * k, t + (75 - 28) * k);
        houseOut.close();
        if (playOut == null) return;
        playOut.reset();
        playOut.moveTo(l + (49 - 28) * k, t + (53 - 28) * k);
        playOut.lineTo(l + (49 - 28) * k, t + (69 - 28) * k);
        playOut.lineTo(l + (63 - 28) * k, t + (61 - 28) * k);
        playOut.close();
    }

    /** The house's stroke for its round corners at a size (the Lab's 5 in 52). */
    public static float houseStroke(float size) {
        return size * 5f / 52f;
    }

    /** The play's stroke for its round corners at a size (the Lab's 2.6 in 52). */
    public static float playStroke(float size) {
        return size * 2.6f / 52f;
    }

    // ---- words ------------------------------------------------------------------------------------------------------

    private float tw(String s, float size, Typeface tf, String weight, float em) {
        if (s == null || s.isEmpty()) return 0f;
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setLetterSpacing(em);
        if (tf == outfit && weight != null) text.setFontVariationSettings(weight);
        float w = text.measureText(s);
        if (tf == outfit && weight != null) text.setFontVariationSettings(null);
        return w;
    }

    /** Words from x (align -1), up to x (1) or about it (0), their capitals' middle on cy. */
    private void words(Canvas cv, String s, float x, float cy, float size, int colour, Typeface tf, String weight, float em, int align) {
        if (s == null || s.isEmpty() || size <= 1f) return;
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setLetterSpacing(em);
        if (tf == outfit && weight != null) text.setFontVariationSettings(weight);
        float w = text.measureText(s);
        text.getTextBounds("H", 0, 1, bounds);
        text.setColor(colour);
        float left = align < 0 ? x : align > 0 ? x - w : x - w / 2f;
        cv.drawText(s, left, cy - bounds.top / 2f, text);
        if (tf == outfit && weight != null) text.setFontVariationSettings(null);
    }
}
