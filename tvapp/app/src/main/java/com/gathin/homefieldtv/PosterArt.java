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

import java.util.AbstractList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A film's or a series' poster card as the family's other app draws one, everywhere one shows (Movies and TV shows'
 * grids, Search, My list, Home's rows, More like this): the poster (2:3, 244 x 366 on their TV) with its corners
 * rounded, the rating in the accent's chip at its foot on the left ("5.8"), and under it a caption strip of the panel's
 * colour with the title and its year in one line cut short with "..". Where the remote is the strip takes the focus
 * colour and a 2 px edge in the light accent goes round the card; the card does not grow. Homefield's own marks stay
 * on the picture: TorBox's green cloud on one of the family's own TorBox titles, "New" for a title the playlist added
 * in the last fortnight, what the family's playlists have of it at the top right (4K, 1080p or 720p in a white chip,
 * the Dolby Vision or Atmos mark), and how far through it is along the picture's foot in the accent. Every size comes
 * from the card's width, so a row's posters and the grid's look alike.
 *
 * <p>Settings → Appearance → Posters: Landscape makes each card 16:9: its picture the title's wide backdrop (at w780;
 * the poster where there is none), the same caption strip under it. The chips and the writing stay the size a portrait
 * card's are (its height is a portrait card's width). The pure helpers below (the fade's colour, genres, the rating's
 * line) are also Pics' and the phone-style posters'.
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
        /** Its page's wide picture where that has been read ("" otherwise): a landscape card's picture before the list's own. */
        String backdrop = "";
        private StaticLayout over;
        private float overFor = -1f;
        private String underTitle, line, badge;
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
        return fadeShader(colour, h, false);
    }

    /** The fade over a card h high: a portrait one's as above; a landscape one's (as the phone's) rises higher. */
    static LinearGradient fadeShader(int colour, float h, boolean wide) {
        int c = colour & 0x00FFFFFF;
        float[] at = fadeStops(wide);
        int[] alpha = wide ? WIDE_ALPHA : TALL_ALPHA;
        int[] colours = new int[alpha.length];
        for (int i = 0; i < alpha.length; i++) colours[i] = alpha[i] << 24 | c;
        return new LinearGradient(0, 0, 0, h, colours, at, Shader.TileMode.CLAMP);
    }

    private static final int[] TALL_ALPHA = {0, 0, 0x8C, 0xEB, 0xFF}, WIDE_ALPHA = {0, 0, 0x73, 0xE0, 0xFF};

    /** Pure: where the fade's steps lie down a card: a landscape card's at 0.24, 0.52, 0.8 and its foot, as the phone's. */
    static float[] fadeStops(boolean wide) {
        return wide ? new float[]{0f, 0.24f, 0.52f, 0.8f, 1f} : new float[]{0f, 0.42f, 0.64f, 0.82f, 1f};
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

    /** Each category's genre once worked out: a catalogue has a few hundred categories and tens of thousands of titles. */
    private static final Map<String, String> CATEGORY_GENRE = new ConcurrentHashMap<>();

    /** Pure: the genre a category's name gives ("UK | Action Movies" → "Action"), else "". */
    static String genreOf(String category) {
        if (category == null) return "";
        String had = CATEGORY_GENRE.get(category);
        if (had != null) return had;
        String g = genreFrom(category);
        if (CATEGORY_GENRE.size() > 4096) CATEGORY_GENRE.clear();
        CATEGORY_GENRE.put(category, g);
        return g;
    }

    /** The first of a name's words (split at anything but a to z) that names a genre; no regular expression made. */
    private static String genreFrom(String category) {
        String low = category.toLowerCase(Locale.ROOT).replace("sci-fi", "scifi").replace("sci fi", "scifi");
        int n = low.length();
        for (int i = 0; i < n; ) {
            while (i < n && (low.charAt(i) < 'a' || low.charAt(i) > 'z')) i++;
            int start = i;
            while (i < n && low.charAt(i) >= 'a' && low.charAt(i) <= 'z') i++;
            if (i == start) continue;
            String w = low.substring(start, i);
            for (String[] g : GENRES) if (g[0].equals(w)) return g[1];
        }
        return "";
    }

    /** Pure: the first of a details page's genres ("Science Fiction, Action" → "Science Fiction"), else "". */
    static String firstGenre(String genres) {
        if (genres == null) return "";
        int end = genres.length();
        for (int i = 0; i < genres.length(); i++) {
            char ch = genres.charAt(i);
            if (ch == ',' || ch == '/' || ch == '|' || ch == '&') {
                end = i;
                break;
            }
        }
        String g = genres.substring(0, end).trim();
        return g.length() > 18 ? "" : g;
    }

    /**
     * A list's posters, each made the first time it is drawn or reached: a category or a grid of thousands of titles
     * costs nothing to open, and a poster asks for its genre and its place in Continue watching only then.
     */
    static final class Items extends AbstractList<Item> {
        private final List<Catalog.Title> titles;
        private final Item[] made;
        private final Function<Catalog.Title, Item> make;

        Items(List<Catalog.Title> titles, Function<Catalog.Title, Item> make) {
            this.titles = titles;
            this.made = new Item[titles.size()];
            this.make = make;
        }

        @Override public Item get(int i) {
            Item it = made[i];
            if (it == null) made[i] = it = make.apply(titles.get(i));
            return it;
        }

        @Override public int size() {
            return made.length;
        }
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

    /** Settings, Posters: Portrait (2:3 posters) or Landscape (16:9 backdrops with the title over them). */
    static boolean landscape;

    static void setLandscape(Context c, boolean on) {
        landscape = on;
        c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).edit().putBoolean("posters_wide", on).apply();
    }

    /** A card's width for a portrait poster's width: wider for a landscape one, the same height as that is wide. */
    static float cardWidth(float portraitWidth) {
        return landscape ? portraitWidth * 16f / 9f : portraitWidth;
    }

    /** Pure: a TMDB picture's address at w780 (a w1280 or original one is far more than a card needs). */
    static String w780(String url) {
        if (url == null) return "";
        return url.contains("image.tmdb.org/t/p/") ? url.replaceFirst("/t/p/(?:w\\d+|original)/", "/t/p/w780/") : url;
    }

    static void init(Context c) {
        landscape = c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).getBoolean("posters_wide", false);
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
    /**
     * Its picture's height, its caption strip's, its corners, and the size every badge and word is a share of (a
     * portrait card's width: 244 on the family's TV, its strip 52, its words 28 px).
     */
    private final float h, cap, r, u;
    private final boolean wide;
    private final TextPaint captionP, ratingP, badgeP, chipP, blankP;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = Dark.fill(0), white = Dark.fill(0xFFFFFFFF), bar = Dark.fill(0), plate = Dark.fill(0xB3111114);
    private final RectF rect = new RectF(), tmp = new RectF();
    private final android.graphics.Path clip = new android.graphics.Path();
    private final int pw, ph;

    PosterArt(float widthPx) {
        w = widthPx;
        wide = landscape;
        h = wide ? w * 9f / 16f : w * 1.5f;
        u = wide ? h : w;
        cap = u * CAPTION;
        r = u * 0.035f;
        pw = Math.round(w);
        ph = Math.round(h);
        captionP = text(0.115f, Type.REGULAR, 0xFFFFFFFF);
        ratingP = text(0.103f, Type.REGULAR, 0xFFFFFFFF);
        badgeP = text(0.066f, Type.BOLD, 0xFFFFFFFF);
        chipP = text(0.064f, Type.BOLD, 0xFF111114);
        blankP = text(0.1f, Type.MEDIUM, 0xFFFFFFFF);
    }

    /** The caption strip's height, as a share of a portrait card's width (52 of 244). */
    static final float CAPTION = 0.213f;

    private TextPaint text(float share, int weight, int color) {
        TextPaint p = Type.paint(1, weight, color);
        p.setTextSize(u * share);
        return p;
    }

    /** The picture a card shows: a landscape one the backdrop (at w780; the poster when there is none). */
    private String picture(Item it) {
        if (!wide) return it.title.poster;
        String b = !it.backdrop.isEmpty() ? it.backdrop : it.title.backdrop;
        return b.isEmpty() ? it.title.poster : w780(b);
    }

    /** The picture's height (the caption strip under it). */
    float cardH() {
        return h;
    }

    /** The caption strip under the picture. */
    float under() {
        return cap;
    }

    /** The whole card: the picture and its caption strip. */
    float tileH() {
        return h + cap;
    }

    /** Asks for the poster's picture early, so it is there when the row slides on to it. */
    void prefetch(Item it, Runnable redraw) {
        if (it != null && it.title != null) Pics.get(picture(it), pw, ph, Pics.CROP, redraw);
    }

    /** One card at (x, y): its picture, its caption under it; where the remote is, the strip lit and the edge drawn. */
    void draw(Canvas c, Item it, float x, float y, boolean focused, int alpha, Runnable redraw) {
        draw(c, it, x, y, focused ? 1f : 0f, alpha, redraw);
    }

    /**
     * lift: 0 resting, 1 where the remote is. The other app doesn't grow the poster the remote is on: past halfway it
     * is drawn as the remote's (the strip in the focus colour, a 2 px edge in the light accent round the card), at once.
     */
    void draw(Canvas c, Item it, float x, float y, float lift, int alpha, Runnable redraw) {
        Catalog.Title t = it.title;
        boolean focused = lift > 0.5f;
        rect.set(x, y, x + w, y + h);
        // The picture with its top corners rounded, the strip under it with its foot's.
        c.save();
        clip.reset();
        tmp.set(x, y, x + w, y + h + cap);
        clip.addRoundRect(tmp, r, r, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        String pic = picture(it);
        Bitmap b = pic.isEmpty() ? null : Pics.get(pic, pw, ph, Pics.CROP, redraw);
        bmp.setAlpha(alpha);
        if (b != null) {
            c.drawBitmap(b, null, rect, bmp);
        } else {
            // No picture (yet): the title's own deep colour with its name on it.
            card.setColor(blank(t.name));
            card.setAlpha(alpha);
            c.drawRect(rect, card);
            if (it.over == null || it.overFor != w) {
                int room = Math.round(w * 0.8f);
                it.over = StaticLayout.Builder.obtain(t.name, 0, t.name.length(), blankP, room)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(4)
                        .setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
                it.overFor = w;
            }
            blankP.setAlpha(alpha);
            c.save();
            c.translate(x + w * 0.1f, y + (h - it.over.getHeight()) / 2f);
            it.over.draw(c);
            c.restore();
        }
        // The caption strip: the panel's colour, the focus colour where the remote is.
        card.setColor(focused ? Palette.over(Palette.GROUND, Palette.FOCUS) : Palette.PANEL);
        card.setAlpha(alpha);
        c.drawRect(x, y + h, x + w, y + h + cap, card);
        if (it.progress >= 0f) {
            // How far through: the accent along the picture's foot.
            float bh = Math.max(2f, u * 0.018f);
            bar.setColor(0x66000000);
            bar.setAlpha(alpha * 0x66 / 255);
            c.drawRect(x, y + h - bh, x + w, y + h, bar);
            bar.setColor(Palette.ACCENT);
            bar.setAlpha(alpha);
            c.drawRect(x, y + h - bh, x + w * Math.max(0.03f, Math.min(1f, it.progress)), y + h, bar);
        }
        c.restore();
        rating(c, it, x, y, alpha);
        badges(c, it, x, y, alpha);
        caption(c, it, x, y + h, alpha);
        if (focused) {
            tmp.set(x, y, x + w, y + h + cap);
            Focus.border(c, tmp, r);
        }
    }

    /** The rating in the accent's chip at the picture's foot on the left ("5.8"), when the playlist gives one. */
    private void rating(Canvas c, Item it, float x, float y, int alpha) {
        if (it.line == null) {
            float v = Billboard.rating(it.title.rating);
            it.line = v > 0 ? String.format(Locale.UK, "%.1f", v) : "";
        }
        if (it.line.isEmpty()) return;
        float ch = u * 0.143f, pad = u * 0.045f, m = u * 0.05f;
        float tw = ratingP.measureText(it.line);
        float bottom = y + h - m - (it.progress >= 0f ? u * 0.02f : 0f);
        tmp.set(x + m, bottom - ch, x + m + tw + pad * 2, bottom);
        pill.setColor(Palette.ACCENT);
        pill.setAlpha(alpha);
        c.drawRoundRect(tmp, u * 0.025f, u * 0.025f, pill);
        ratingP.setColor(Palette.ACCENT_INK);
        ratingP.setAlpha(alpha);
        c.drawText(it.line, tmp.left + pad, tmp.centerY() + ratingP.getTextSize() * 0.36f, ratingP);
    }

    /** TorBox's cloud and New at the top left; the picture's chip and the Dolby mark at the top right. */
    private void badges(Canvas c, Item it, float x, float y, int alpha) {
        Catalog.Title t = it.title;
        float m = u * 0.05f, bh = u * 0.12f, rad = u * 0.025f;
        if (it.badge == null) it.badge = badge(t.added, System.currentTimeMillis());
        float left = x + m;
        if (t.torbox()) {
            // One of the family's own TorBox titles (no playlist has it): TorBox's green cloud first.
            SourcePicker.badge(c, left + bh / 2f, y + m + bh / 2f, bh, alpha);
            left += bh + u * 0.03f;
        }
        if (!it.badge.isEmpty()) {
            float tw = badgeP.measureText(it.badge);
            tmp.set(left, y + m, left + tw + u * 0.1f, y + m + bh);
            pill.setColor(Palette.ACCENT);
            pill.setAlpha(alpha);
            c.drawRoundRect(tmp, rad, rad, pill);
            badgeP.setColor(Palette.ACCENT_INK);
            badgeP.setAlpha(alpha);
            c.drawText(it.badge, tmp.left + u * 0.05f, tmp.centerY() + badgeP.getTextSize() * 0.36f, badgeP);
        }
        String q = chip(t.bestPicture());
        float right = x + w - m;
        float ch = u * 0.115f;
        if (!q.isEmpty()) {
            float tw = chipP.measureText(q);
            tmp.set(right - tw - u * 0.07f, y + m, right, y + m + ch);
            white.setAlpha(alpha);
            c.drawRoundRect(tmp, rad, rad, white);
            chipP.setAlpha(alpha);
            c.drawText(q, tmp.left + u * 0.035f, tmp.centerY() + chipP.getTextSize() * 0.36f, chipP);
            right = tmp.left - u * 0.035f;
        }
        Bitmap mark = t.anyDolbyVision() ? dolbyVision : t.anyAtmos() ? atmos : null;
        if (mark != null) {
            float mh = u * 0.06f, mw = mh * mark.getWidth() / mark.getHeight();
            float markLeft = right - mw;
            // Never under the New badge: left out where the poster is too narrow for both.
            if (it.badge.isEmpty() || markLeft > left + badgeP.measureText(it.badge) + u * 0.14f) {
                // On a dark plate of its own, so it reads over a bright poster too.
                tmp.set(markLeft - u * 0.025f, y + m, right + u * 0.025f, y + m + ch);
                plate.setAlpha(alpha * 0xB3 / 255);
                c.drawRoundRect(tmp, rad, rad, plate);
                tmp.set(markLeft, y + m + (ch - mh) / 2f, right, y + m + (ch + mh) / 2f);
                bmp.setAlpha(alpha);
                c.drawBitmap(mark, null, tmp, bmp);
            }
        }
    }

    /** The caption: the title and its year ("Other Mommy (2026)"), one line, cut short with "..". */
    private void caption(Canvas c, Item it, float x, float y, int alpha) {
        Catalog.Title t = it.title;
        if (it.underFor != w || it.underTitle == null) {
            String s = t.name + (t.year.isEmpty() || t.name.endsWith(")") ? "" : " (" + t.year + ")");
            it.underTitle = fitDots(s, captionP, w - u * 0.1f);
            it.underFor = w;
        }
        captionP.setColor(Palette.TEXT1);
        captionP.setAlpha(alpha);
        c.drawText(it.underTitle, x + u * 0.05f, y + cap / 2f + captionP.getTextSize() * 0.36f, captionP);
    }

    /** Pure-ish: words cut to fit a width with ".." as the other app cuts its captions. */
    static String fitDots(String s, TextPaint p, float room) {
        if (s == null) return "";
        if (p.measureText(s) <= room) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("..") > room) n--;
        return s.substring(0, n).trim() + "..";
    }

    private static final int[] BLANKS = {0xFF2B3A55, 0xFF4A2B33, 0xFF2E4A3C, 0xFF46392A, 0xFF3A2D52, 0xFF27434A};

    /** Pure: a picture-less card's colour, the same for the same title. */
    static int blank(String name) {
        int hsh = name == null ? 0 : name.toLowerCase(Locale.ROOT).hashCode();
        return BLANKS[Math.floorMod(hsh, BLANKS.length)];
    }
}
