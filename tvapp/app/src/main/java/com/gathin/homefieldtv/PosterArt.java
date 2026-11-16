package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import com.gathin.homefieldtv.engine.Catalog;

import java.util.Locale;

/**
 * A film's or a series' poster as the dark screens draw it, everywhere one shows (the category rows, the grid, the side
 * panel's lists, More like this), the same as the phone's: a 2:3 card with its corners rounded, its foot fading into the
 * poster's own colour; over the fade the title in bold white (there are no title logos here) and "Genre • ★ 8.2"; at
 * the top left "New" for a title the playlist added in the last fortnight; at the top right what the family's
 * playlists have of it (4K, 1080p or 720p in a white chip, and the Dolby Vision or Atmos mark where a name says so);
 * under the card the title and the year. Where the remote is, the card grows a little inside a white ring, its badges
 * still in sight. Every size comes from the card's width, so a row's posters and the grid's look alike.
 */
final class PosterArt {
    /** The corners' radius, as a share of the card's width. */
    static final float RADIUS = 0.08f;
    /** The badges' colours: the rank's (unused here: no trending lists), Trending's (the same), New's. */
    static final int RANK = 0xD9161618, TREND = 0xFFC9EBEE, TREND_INK = 0xFF0F2A2E, NEW = 0xFFFFD25A, NEW_INK = 0xFF1E1606;
    static final int YEAR = 0xFF8E8E93;
    /** A title the playlist added within this long ago is New. */
    static final long NEW_MS = 14L * 24 * 3600_000L;
    /** How much a poster grows where the remote is. */
    static final float LIFT = 1.07f;

    /** What one poster shows. */
    static final class Item {
        final Catalog.Title title;
        /** Its genre ("Mystery"), "" for none; how far through it is (0 to 1), below 0 for none. */
        String genre = "";
        float progress = -1f;
        private StaticLayout over;
        private float overFor = -1f;
        private String underTitle, underYear, line, badge;
        private float underFor = -1f;

        Item(Catalog.Title t) {
            this.title = t;
        }
    }

    // ---- pure: the fade, the badges, the lines --------------------------------------------------------------------

    /** Pure: the average colour (0xRRGGBB) of the opaque pixels given; mid grey for none. */
    static int average(int[] px) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int c : px) {
            if ((c >>> 24) < 128) continue;
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
            n++;
        }
        if (n == 0) return 0x808080;
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /**
     * Pure: a poster's foot colour from the average of its lowest third, made deep as the phone makes it: value
     * 0.14 + 0.14 V, saturation lifted by 15% (at most 0.85), a grey left grey (saturation under 0.08). Opaque.
     */
    static int fadeColour(int rgb) {
        float[] hsv = hsv(rgb);
        float s = hsv[1] < 0.08f ? hsv[1] : Math.min(0.85f, hsv[1] * 1.15f);
        float v = 0.14f + 0.14f * hsv[2];
        return 0xFF000000 | rgb(hsv[0], s, v);
    }

    /** Pure: {hue 0-360, saturation 0-1, value 0-1} of 0xRRGGBB. */
    static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0) h += 360f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** Pure: 0xRRGGBB of a hue, saturation and value. */
    static int rgb(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    /** The fade over a poster h high: clear to 42% of the way down, 55% at 64%, 92% at 82%, solid at the foot. */
    static LinearGradient fadeShader(int colour, float h) {
        int c = colour & 0x00FFFFFF;
        return new LinearGradient(0, 0, 0, h, new int[]{c, c, 0x8C000000 | c, 0xEB000000 | c, 0xFF000000 | c},
                new float[]{0f, 0.42f, 0.64f, 0.82f, 1f}, Shader.TileMode.CLAMP);
    }

    /** Pure: "New" for a title added in the last fortnight (added in ms; 0 not known), else "". */
    static String badge(long added, long now) {
        return added > 0 && added <= now + 86_400_000L && now - added < NEW_MS ? "New" : "";
    }

    private static final String[][] GENRES = {
            {"action", "Action"}, {"adventure", "Adventure"}, {"animation", "Animation"}, {"animated", "Animation"},
            {"anime", "Anime"}, {"comedy", "Comedy"}, {"comedies", "Comedy"}, {"crime", "Crime"}, {"documentary", "Documentary"},
            {"documentaries", "Documentary"}, {"drama", "Drama"}, {"family", "Family"}, {"fantasy", "Fantasy"},
            {"history", "History"}, {"horror", "Horror"}, {"kids", "Kids"}, {"music", "Music"}, {"musical", "Musical"},
            {"mystery", "Mystery"}, {"romance", "Romance"}, {"romantic", "Romance"}, {"scifi", "Sci-Fi"}, {"sci", "Sci-Fi"},
            {"thriller", "Thriller"}, {"thrillers", "Thriller"}, {"war", "War"}, {"western", "Western"}, {"sport", "Sport"},
            {"reality", "Reality"}, {"christmas", "Christmas"}};

    /** Pure: the genre a category's name gives ("UK | Action Movies" → "Action"), else "". */
    static String genreOf(String category) {
        if (category == null) return "";
        String low = category.toLowerCase(Locale.ROOT).replace("sci-fi", "scifi").replace("sci fi", "scifi");
        for (String w : low.split("[^a-z]+")) {
            for (String[] g : GENRES) if (g[0].equals(w)) return g[1];
        }
        return "";
    }

    /** Pure: the first of a details page's genres ("Science Fiction, Action" → "Science Fiction"), else "". */
    static String firstGenre(String genres) {
        if (genres == null) return "";
        String g = genres.split("[,/|&]")[0].trim();
        return g.length() > 18 ? "" : g;
    }

    /** Pure: the line over a poster's foot: "Mystery • ★ 8.2", either half alone, or "". */
    static String line(String genre, String rating) {
        String g = genre == null ? "" : genre.trim();
        String r = "";
        try {
            float v = rating == null || rating.isEmpty() ? 0f : Float.parseFloat(rating);
            if (v >= 0.5f) r = "★ " + String.format(Locale.UK, "%.1f", Math.min(10f, v));
        } catch (NumberFormatException ignored) {
            // no rating
        }
        if (g.isEmpty()) return r;
        return r.isEmpty() ? g : g + " • " + r;
    }

    /** Pure: the picture's chip: "4K", "1080p", "720p", or "" (SD and not known get none). */
    static String chip(int picture) {
        switch (picture) {
            case Catalog.PICTURE_4K: return "4K";
            case Catalog.PICTURE_1080: return "1080p";
            case Catalog.PICTURE_720: return "720p";
            default: return "";
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private static Bitmap dolbyVision, atmos;

    static void init(Context c) {
        if (dolbyVision != null) return;
        try {
            dolbyVision = BitmapFactory.decodeResource(c.getResources(), R.drawable.fmt_dolby_vision);
            atmos = BitmapFactory.decodeResource(c.getResources(), R.drawable.fmt_dolby_atmos);
        } catch (RuntimeException ignored) {
            // the chips alone
        }
    }

    /** The card's width (px) this was made for. */
    final float w;
    private final float h, r;
    private final TextPaint overP, lineP, badgeP, chipP, nameP, nameOn, yearP;
    private final Paint card = Dark.fill(Dark.CARD), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = Dark.fill(0), white = Dark.fill(0xFFFFFFFF), track = Dark.fill(0x59FFFFFF), bar = Dark.fill(0xFFFFFFFF);
    private final Paint ring = Dark.line(0xFFFFFFFF, 3f), fade = new Paint(), plate = Dark.fill(0xB3111114);
    private final RectF rect = new RectF(), tmp = new RectF();
    private int fadeFor;
    private final int pw, ph;

    PosterArt(float widthPx) {
        w = widthPx;
        h = w * 1.5f;
        r = w * RADIUS;
        pw = Math.round(w);
        ph = Math.round(h);
        overP = text(0.105f, 700, 0xFFFFFFFF);
        lineP = text(0.068f, 600, 0xBFFFFFFF);
        badgeP = text(0.066f, 700, 0xFFFFFFFF);
        chipP = text(0.064f, 800, 0xFF111114);
        nameP = text(0.084f, 500, 0xFFFFFFFF);
        nameOn = text(0.084f, 600, 0xFFFFFFFF);
        yearP = text(0.07f, 400, YEAR);
        overP.setShadowLayer(w * 0.03f, 0, w * 0.006f, 0x99000000);
    }

    private TextPaint text(float share, int weight, int color) {
        TextPaint p = Dark.text(1, weight, color);
        p.setTextSize(w * share);
        return p;
    }

    /** The card's height. */
    float cardH() {
        return h;
    }

    /** The room under the card for the title and the year. */
    float under() {
        return w * 0.27f;
    }

    /** The whole tile's height: the card and the writing under it. */
    float tileH() {
        return h + under();
    }

    /** Asks for the poster's picture early, so it is there when the row slides on to it. */
    void prefetch(Item it, Runnable redraw) {
        if (it != null && it.title != null) Pics.get(it.title.poster, pw, ph, Pics.POSTER, redraw);
    }

    /** One poster at (x, y): its card, then the writing under it; lifted inside a ring where the remote is. */
    void draw(Canvas c, Item it, float x, float y, boolean focused, int alpha, Runnable redraw) {
        Catalog.Title t = it.title;
        c.save();
        if (focused) c.scale(LIFT, LIFT, x + w / 2f, y + h / 2f);
        rect.set(x, y, x + w, y + h);
        Bitmap b = t.poster.isEmpty() ? null : Pics.get(t.poster, pw, ph, Pics.POSTER, redraw);
        bmp.setAlpha(alpha);
        if (b != null) {
            c.drawBitmap(b, x, y, bmp);
        } else {
            // No picture (yet): a card in the title's own deep colour, faded as a poster's foot is.
            int colour = blank(t.name);
            card.setColor(colour);
            card.setAlpha(alpha);
            c.drawRoundRect(rect, r, r, card);
            if (fadeFor != colour) {
                fade.setShader(fadeShader(fadeColour(colour & 0xFFFFFF), h));
                fadeFor = colour;
            }
            fade.setAlpha(alpha);
            c.save();
            c.translate(x, y);
            tmp.set(0, 0, w, h);
            c.drawRoundRect(tmp, r, r, fade);
            c.restore();
        }
        overWriting(c, it, x, y, alpha);
        badges(c, it, x, y, alpha);
        if (it.progress >= 0f) {
            float bx = x + w * 0.07f, bw = w * 0.86f, by = y + h - w * 0.05f, bh = Math.max(2f, w * 0.022f);
            track.setAlpha(alpha * 0x59 / 255);
            bar.setAlpha(alpha);
            tmp.set(bx, by - bh, bx + bw, by);
            c.drawRoundRect(tmp, bh / 2, bh / 2, track);
            tmp.set(bx, by - bh, bx + bw * Math.max(0.03f, Math.min(1f, it.progress)), by);
            c.drawRoundRect(tmp, bh / 2, bh / 2, bar);
        }
        if (focused) {
            ring.setStrokeWidth(Math.max(2f, w * 0.016f));
            ring.setAlpha(alpha);
            Dark.ring(c, rect, r, ring);
        }
        c.restore();
        underWriting(c, it, x, y + h + (focused ? h * (LIFT - 1f) / 2f : 0f), focused, alpha);
    }

    /** The title and the line under it, over the poster's fade. */
    private void overWriting(Canvas c, Item it, float x, float y, int alpha) {
        Catalog.Title t = it.title;
        if (it.line == null) it.line = line(it.genre, t.rating);
        float lineBase = y + h - w * (it.progress >= 0f ? 0.11f : 0.075f);
        if (!it.line.isEmpty()) {
            lineP.setAlpha(alpha * 0xBF / 255);
            String s = Ui.fit(it.line, lineP, w * 0.88f);
            c.drawText(s, x + (w - lineP.measureText(s)) / 2f, lineBase, lineP);
        }
        if (it.over == null || it.overFor != w) {
            it.over = StaticLayout.Builder.obtain(t.name, 0, t.name.length(), overP, Math.round(w * 0.86f))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false).setLineSpacing(0f, 0.95f).build();
            it.overFor = w;
        }
        float top = (it.line.isEmpty() ? lineBase : lineBase - lineP.getTextSize() * 1.1f) - w * 0.03f - it.over.getHeight();
        overP.setAlpha(alpha);
        c.save();
        c.translate(x + w * 0.07f, top);
        it.over.draw(c);
        c.restore();
    }

    /** New at the top left; the picture's chip and the Dolby mark at the top right. */
    private void badges(Canvas c, Item it, float x, float y, int alpha) {
        Catalog.Title t = it.title;
        float m = w * 0.055f, bh = w * 0.13f, rad = w * 0.035f;
        if (it.badge == null) it.badge = badge(t.added, System.currentTimeMillis());
        if (!it.badge.isEmpty()) {
            float tw = badgeP.measureText(it.badge);
            tmp.set(x + m, y + m, x + m + tw + w * 0.1f, y + m + bh);
            pill.setColor(NEW);
            pill.setAlpha(alpha);
            c.drawRoundRect(tmp, rad, rad, pill);
            badgeP.setColor(NEW_INK);
            badgeP.setAlpha(alpha);
            c.drawText(it.badge, tmp.left + w * 0.05f, tmp.centerY() + badgeP.getTextSize() * 0.36f, badgeP);
        }
        String q = chip(t.bestPicture());
        float right = x + w - m;
        float ch = w * 0.115f;
        if (!q.isEmpty()) {
            float tw = chipP.measureText(q);
            tmp.set(right - tw - w * 0.07f, y + m, right, y + m + ch);
            white.setAlpha(alpha);
            c.drawRoundRect(tmp, w * 0.025f, w * 0.025f, white);
            chipP.setAlpha(alpha);
            c.drawText(q, tmp.left + w * 0.035f, tmp.centerY() + chipP.getTextSize() * 0.36f, chipP);
            right = tmp.left - w * 0.035f;
        }
        Bitmap mark = t.anyDolbyVision() ? dolbyVision : t.anyAtmos() ? atmos : null;
        if (mark != null) {
            float mh = w * 0.06f, mw = mh * mark.getWidth() / mark.getHeight();
            float left = right - mw;
            // Never under the New badge: left out where the poster is too narrow for both.
            if (it.badge.isEmpty() || left > x + m + badgeP.measureText(it.badge) + w * 0.14f) {
                // On a dark plate of its own, so it reads over a bright poster too.
                tmp.set(left - w * 0.025f, y + m, right + w * 0.025f, y + m + ch);
                plate.setAlpha(alpha * 0xB3 / 255);
                c.drawRoundRect(tmp, w * 0.025f, w * 0.025f, plate);
                tmp.set(left, y + m + (ch - mh) / 2f, right, y + m + (ch + mh) / 2f);
                bmp.setAlpha(alpha);
                c.drawBitmap(mark, null, tmp, bmp);
            }
        }
    }

    /** The title in white and the year in grey, under the card. */
    private void underWriting(Canvas c, Item it, float x, float y, boolean focused, int alpha) {
        Catalog.Title t = it.title;
        if (it.underFor != w || it.underTitle == null) {
            it.underTitle = Ui.fit(t.name, nameP, w);
            it.underYear = t.year;
            it.underFor = w;
        }
        TextPaint p = focused ? nameOn : nameP;
        p.setAlpha(alpha);
        c.drawText(it.underTitle, x, y + w * 0.125f, p);
        if (!it.underYear.isEmpty()) {
            yearP.setAlpha(alpha);
            c.drawText(it.underYear, x, y + w * 0.225f, yearP);
        }
    }

    private static final int[] BLANKS = {0xFF2B3A55, 0xFF4A2B33, 0xFF2E4A3C, 0xFF46392A, 0xFF3A2D52, 0xFF27434A};

    /** Pure: a picture-less card's colour, the same for the same title. */
    static int blank(String name) {
        int hsh = name == null ? 0 : name.toLowerCase(Locale.ROOT).hashCode();
        return BLANKS[Math.floorMod(hsh, BLANKS.length)];
    }
}
