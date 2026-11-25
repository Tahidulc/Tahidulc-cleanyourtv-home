package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The words of a title's hero in the 07 look, shared by Home's card, the Movies and TV Shows card and a title's page (the
 * mock-ups' .hm-facts, .mv-facts and .tp-facts, their logos and buttons). Every piece draws at an opacity, so a card's
 * words can fade and rise in with nothing made a frame.
 *
 * <pre>
 *   Facts facts = new Facts();  facts.set(title, details, extras, 2)      made when the title changes
 *   facts.draw(c, x, cy, alpha, p)    "[15] ★ 8.4 · 2023– · 2 seasons · Drama [4K]": the certificate in its filled chip,
 *                                     the words, the picture's and sound's outlined chips, TorBox's cloud
 *   Mark mark = new Mark(66f);  mark.set(name, logoUrl, w, h)             TMDB's logo where it has one, else the name
 *   mark.height(ready) / mark.draw(c, x, top, alpha, ready)               (Inter, bold) on one line
 *   Hero07.button(c, x, cy, words, after, icon, lit, alpha)               07's button: white where it is the main one,
 *                                                                         a quieter word after its name ("21 min left")
 *   Hero07.circle(c, cx, cy, icon, lit, on, alpha)                        a round one (My List, the trailer)
 *   Hero07.baseline(top, lineHeight, size)                                where the mock-ups' CSS puts a line's words
 * </pre>
 */
final class Hero07 {
    private Hero07() {}

    /** Inter's ascent and descent (of its size), for laying words out as the mock-ups' CSS does. */
    static final float ASCENT = 0.96875f, DESCENT = 0.2421875f;

    /** Pure: the baseline of a line of words of a size (design or screen px) in a line box from top, lineHeight tall. */
    static float baseline(float top, float lineHeight, float size) {
        return top + lineHeight / 2f + (ASCENT - DESCENT) / 2f * size;
    }

    /** Pure: CSS's "normal" line height for Inter at a size. */
    static float normal(float size) {
        return (ASCENT + DESCENT) * size;
    }

    /**
     * Words broken into at most maxLines lines lineHeight apart (screen px), the last cut with an ellipsis: what the
     * mock-ups' -webkit-line-clamp does. Made when the words change, never while drawing; drawn from (lineHeight minus
     * the words' own height) / 2 above where CSS puts the line's top.
     */
    static android.text.StaticLayout lines(CharSequence text, TextPaint p, int width, int maxLines, float lineHeight) {
        float natural = p.descent() - p.ascent();
        return android.text.StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, width))
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, Math.max(0.5f, lineHeight / Math.max(1f, natural)))
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build();
    }

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG), LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static TextPaint chipP, certP, buttonP, afterP;
    private static float madeFor = -1f;

    static {
        LINE.setStyle(Paint.Style.STROKE);
    }

    private static void paints() {
        float k = Ui.s * com.gathin.homefieldtv.prefs.Look.text;
        if (chipP != null && k == madeFor) return;
        madeFor = k;
        chipP = Type.paint(Type.CHIP07, Type.BOLD, Look07.L2);
        chipP.setLetterSpacing(0.02f);
        certP = Type.paint(Type.CHIP07, Type.BOLD, Look07.L1);
        buttonP = Type.paint(Type.BUTTON, Type.SEMIBOLD, Look07.L1);
        afterP = Type.paint(18f, Type.SEMIBOLD, Look07.INK2);
    }

    // ---- chips -------------------------------------------------------------------------------------------------------

    /** An outlined chip (4K, DOLBY VISION), 26 high, from x centred on cy, at an opacity; its width. */
    static float chip(Canvas c, float x, float cy, String text, int alpha) {
        paints();
        float h = Ui.f(26f), w = chipP.measureText(text) + Ui.f(14f), sw = Math.max(1f, Ui.f(1.5f));
        R.set(x + sw / 2f, cy - h / 2f + sw / 2f, x + w - sw / 2f, cy + h / 2f - sw / 2f);
        LINE.setStrokeWidth(sw);
        LINE.setColor(Palette.alpha(0x8CEBEBF5, 0x8C * alpha / 255));
        c.drawRoundRect(R, Ui.f(Look07.R_CHIP), Ui.f(Look07.R_CHIP), LINE);
        chipP.setColor(Look07.L2);
        chipP.setAlpha((Look07.L2 >>> 24) * alpha / 255);
        c.drawText(text, x + Ui.f(7f), Type.centred(chipP, cy), chipP);
        return w;
    }

    /** A certificate ("15", "12A"): white at 20 %, 26 high, white letters; its width. */
    static float certificate(Canvas c, float x, float cy, String text, int alpha) {
        paints();
        float h = Ui.f(26f), w = certP.measureText(text) + Ui.f(16f);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(Palette.alpha(0x33FFFFFF, 0x33 * alpha / 255));
        c.drawRoundRect(R, Ui.f(Look07.R_CHIP), Ui.f(Look07.R_CHIP), FILL);
        certP.setAlpha(alpha);
        c.drawText(text, x + Ui.f(8f), Type.centred(certP, cy), certP);
        return w;
    }

    /** TorBox's green cloud, d across, centred on (cx, cy). */
    static void torbox(Canvas c, float cx, float cy, float d, int alpha) {
        FILL.setColor(Palette.alpha(0xFF1FAE5B, alpha));
        c.drawCircle(cx, cy, d / 2f, FILL);
        Icons07.draw(c, Icons07.CLOUD, cx, cy, d * 0.64f, Palette.alpha(Look07.L1, alpha));
    }

    // ---- buttons -----------------------------------------------------------------------------------------------------

    /** A button's width: its icon (or none: -1), its words and the quieter words after them. */
    static float buttonWidth(String words, String after, int icon) {
        paints();
        float w = Ui.f(34f) * 2 + buttonP.measureText(words);
        if (icon >= 0) w += Ui.f(26f) + Ui.f(14f);
        if (after != null && !after.isEmpty()) w += Ui.f(14f) + afterP.measureText(after);
        return w;
    }

    /**
     * 07's button from x centred on cy (70 high): the main one white with dark words and its shadow (lit), the others
     * white at 16 % with white words; after: quieter words after the name ("21 min left"); its width.
     */
    static float button(Canvas c, float x, float cy, String words, String after, int icon, boolean lit, int alpha) {
        paints();
        float h = Ui.f(70f), w = buttonWidth(words, after, icon);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        if (lit) Shadows.draw(c, R, h / 2f, Look07.SH_BUTTON, alpha / 255f);
        FILL.setColor(lit ? Palette.alpha(Look07.L1, alpha) : Palette.alpha(Look07.BUTTON, (Look07.BUTTON >>> 24) * alpha / 255));
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        int ink = lit ? Look07.INK : Look07.L1;
        float tx = x + Ui.f(34f);
        if (icon >= 0) {
            float is = Ui.f(icon == Icons07.PLAY ? 26f : 24f);
            Icons07.draw(c, icon, tx + Ui.f(13f), cy, is, Palette.alpha(ink, alpha));
            tx += Ui.f(26f) + Ui.f(14f);
        }
        buttonP.setColor(ink);
        buttonP.setAlpha(alpha);
        c.drawText(words, tx, Type.centred(buttonP, cy), buttonP);
        if (after != null && !after.isEmpty()) {
            tx += buttonP.measureText(words) + Ui.f(14f);
            afterP.setColor(lit ? Look07.INK2 : Look07.L2);
            afterP.setAlpha(((lit ? Look07.INK2 : Look07.L2) >>> 24) * alpha / 255);
            c.drawText(after, tx, Type.centred(afterP, cy), afterP);
        }
        return w;
    }

    /** A round button (70 across) centred on (cx, cy): white where the remote is (lit), lighter when on (a tick shown). */
    static void circle(Canvas c, float cx, float cy, int icon, boolean lit, boolean on, int alpha) {
        float d = Ui.f(70f);
        R.set(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f);
        if (lit) Shadows.draw(c, R, d / 2f, Look07.SH_BUTTON, alpha / 255f);
        int fill = lit ? Look07.L1 : on ? 0x4DFFFFFF : Look07.BUTTON;
        FILL.setColor(Palette.alpha(fill, (fill >>> 24) * alpha / 255));
        c.drawCircle(cx, cy, d / 2f, FILL);
        Icons07.draw(c, icon, cx, cy, Ui.f(icon == Icons07.X ? 26f : 28f), Palette.alpha(lit ? Look07.INK : Look07.L1, alpha));
    }

    // ---- the facts line ----------------------------------------------------------------------------------------------

    /**
     * A title's (or a programme's) facts on one line: certificate, words, chips, TorBox's cloud. Only words: the view
     * drawing it keeps the paint (Type.paint(22f, Type.MEDIUM, Look07.L2)), so a card holds no paint of its own.
     */
    static final class Facts {
        private String cert = "", words = "";
        private final List<String> chips = new ArrayList<>();
        private boolean torbox;

        /**
         * A title's facts: its certificate (TMDB's, else the provider's age), its rating, years (or year), length or
         * seasons, its first genres (at most genres of them), then 4K or HD, Dolby Vision or HDR, Atmos.
         */
        Facts set(Catalog.Title t, Catalog.Details d, Tmdb.Extras x, int genres) {
            chips.clear();
            torbox = false;
            cert = words = "";
            if (t == null) return this;
            String age = x != null && !x.age.isEmpty() ? x.age : d == null ? "" : d.age.trim();
            cert = age;
            List<String> w = new ArrayList<>();
            float r = Billboard.rating(d != null && !d.rating.isEmpty() ? d.rating : t.rating);
            if (r > 0f) w.add(String.format(Locale.UK, "★ %.1f", r));
            String years = x != null && !x.years.isEmpty() ? x.years : t.year;
            if (!years.isEmpty()) w.add(years);
            if (t.series && d != null && !d.seasons.isEmpty()) w.add(d.seasons.size() == 1 ? "1 season" : d.seasons.size() + " seasons");
            else if (!t.series && d != null && d.minutes > 0) w.add(Ui.duration(d.minutes));
            String g = genres(d == null ? "" : d.genre, genres);
            if (g.isEmpty()) g = PosterArt.genreOf(t.category);
            if (!g.isEmpty()) w.add(g);
            words = join(w);
            int pic = t.bestPicture();
            if (pic == Catalog.PICTURE_4K) chips.add("4K");
            else if (pic == Catalog.PICTURE_1080) chips.add("HD");
            if (t.anyDolbyVision()) chips.add("DOLBY VISION");
            else if (!t.hdr.isEmpty()) chips.add(t.hdr.toUpperCase(Locale.UK));
            if (t.anyAtmos()) chips.add("ATMOS");
            torbox = t.torbox();
            return this;
        }

        /** Words of their own (a programme's), a certificate before them and chips after ("HD"). */
        Facts words(String certificate, String text, String... chip) {
            cert = certificate == null ? "" : certificate;
            words = text == null ? "" : text;
            chips.clear();
            torbox = false;
            if (chip != null) for (String s : chip) if (s != null && !s.isEmpty()) chips.add(s);
            return this;
        }

        boolean isEmpty() {
            return cert.isEmpty() && words.isEmpty() && chips.isEmpty() && !torbox;
        }

        /** Drawn from x, its middle on cy, at an opacity, its words in p; where it ends. */
        float draw(Canvas c, float x, float cy, int alpha, TextPaint p) {
            float gap = Ui.f(12f);
            if (!cert.isEmpty()) x += certificate(c, x, cy, cert, alpha) + gap;
            if (!words.isEmpty()) {
                p.setColor(Look07.L2);
                p.setAlpha((Look07.L2 >>> 24) * alpha / 255);
                c.drawText(words, x, Type.centred(p, cy), p);
                x += p.measureText(words) + gap;
            }
            for (int i = 0; i < chips.size(); i++) x += chip(c, x, cy, chips.get(i), alpha) + Ui.f(10f);
            if (torbox) {
                float d = Ui.f(26f);
                torbox(c, x + d / 2f, cy, d, alpha);
                x += d + gap;
            }
            return x;
        }

        /** Pure: the first n genres of a provider's list ("Action, Drama, Thriller" → "Action, Drama"). */
        static String genres(String list, int n) {
            if (list == null || list.trim().isEmpty() || n <= 0) return "";
            StringBuilder b = new StringBuilder();
            int k = 0;
            for (String s : list.split("\\s*[,/|]\\s*")) {
                String g = s.trim();
                if (g.isEmpty()) continue;
                if (k > 0) b.append(", ");
                b.append(g);
                if (++k >= n) break;
            }
            return b.toString();
        }

        private static String join(List<String> parts) {
            StringBuilder b = new StringBuilder();
            for (String s : parts) {
                if (b.length() > 0) b.append(" · ");
                b.append(s);
            }
            return b.toString();
        }
    }

    // ---- the title's mark ------------------------------------------------------------------------------------------------

    /** TMDB's logo for a title where it has one, at its own shape on the foot of its box; else its name in bold Inter. */
    static final class Mark {
        private final float maxSize;
        private final TextPaint p;
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private String name = "", logo = "", shown = "";
        private float w, h, room;

        /** maxSize: the name's size (design px) when it has no logo; a long one is made smaller to fit. */
        Mark(float maxSize) {
            this.maxSize = maxSize;
            p = Type.paint(maxSize, Type.BOLD, Look07.L1);
            p.setShadowLayer(Ui.f(24f), 0, Ui.f(4f), 0x66000000);
        }

        /** The name, TMDB's logo ("" for none), the box a logo may take and the width the name may (screen px). */
        void set(String title, String logoUrl, float boxW, float boxH, float nameW) {
            String t = title == null ? "" : title;
            String l = logoUrl == null ? "" : logoUrl;
            if (t.equals(name) && l.equals(logo) && boxW == w && boxH == h && nameW == room) return;
            name = t;
            logo = l;
            w = boxW;
            h = boxH;
            room = nameW;
            float size = Ui.f(maxSize) * com.gathin.homefieldtv.prefs.Look.text;
            p.setTextSize(size);
            while (size > Ui.f(maxSize * 0.62f) && p.measureText(name) > nameW) {
                size *= 0.94f;
                p.setTextSize(size);
            }
            p.setLetterSpacing(Type.spacing(size / Math.max(0.01f, Ui.s)));
            shown = Ui.fit(name, p, nameW);
        }

        /** The logo's picture once read (null while it is, or without one). */
        private Bitmap logo(Runnable ready) {
            return logo.isEmpty() ? null : Pics.get(logo, Math.round(w), Math.round(h), Pics.FIT, ready);
        }

        /** Whether the logo is still being read (nothing is drawn meanwhile, so the name never flashes first). */
        boolean waiting(Runnable ready) {
            return !logo.isEmpty() && logo(ready) == null && !Pics.failed(logo);
        }

        /** Whether it is the logo (true) or the name in words. */
        boolean isLogo(Runnable ready) {
            return logo(ready) != null;
        }

        /** How tall it is as drawn now: the logo's own height, else the name's line (CSS line-height 1.02). */
        float height(Runnable ready) {
            Bitmap b = logo(ready);
            if (b != null) return b.getHeight();
            if (waiting(ready)) return h;
            return p.getTextSize() * 1.02f;
        }

        /** How wide it is as drawn now: the logo's own width, else the name's (the box's while the logo is read). */
        float width(Runnable ready) {
            Bitmap b = logo(ready);
            if (b != null) return b.getWidth();
            if (waiting(ready)) return w;
            return p.measureText(shown);
        }

        /** Where the logo was last drawn (the canvas's px as it was then; empty for none, or the name in words). */
        android.graphics.RectF drawn() {
            return drawn;
        }

        private final android.graphics.RectF drawn = new android.graphics.RectF();

        /**
         * Drawn with its top left at (x, top), at an opacity: a logo whole, fitted inside its box at its own shape (read
         * so, Pics.FIT) and at its own pixels, so it is crisp and never cropped.
         */
        void draw(Canvas c, float x, float top, int alpha, Runnable ready) {
            Bitmap b = logo(ready);
            if (b != null) {
                bmp.setAlpha(alpha);
                c.drawBitmap(b, x, top, bmp);
                drawn.set(x, top, x + b.getWidth(), top + b.getHeight());
                return;
            }
            drawn.setEmpty();
            if (waiting(ready) || shown.isEmpty()) return;
            p.setAlpha(alpha);
            float lh = p.getTextSize() * 1.02f;
            c.drawText(shown, x, baseline(top, lh, p.getTextSize()), p);
        }
    }
}
