package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import java.util.Locale;

/**
 * The big picture at the top of Home, in the dark look of Films and TV shows (Billboard): whatever the remote is on,
 * its wide picture on the right fading into the dark (a Backdrop under this view, cross-fading into the next one's as
 * the remote moves), and at the left a small line over its title (a film's category, or a channel's logo), the title
 * large, a line of facts (the amber star and its rating, the running time, year and genres, the age rating in its box)
 * and a few lines of its story. The writing fades and rises into place a moment after the remote moves to another one.
 */
final class Hero extends View {
    /** Where the writing goes (design pixels): the small line's baseline at textY. */
    float textX = Billboard.TEXT_X, textY = 104f, textW = Billboard.TEXT_W;
    int titleLines = 2, plotLines = 3;
    float titleSize = 72f;
    /** The picture, drawn under this view (the screen adds it first); a wide one only. */
    final Backdrop picture;

    private String chip = "", logo = "", title = "", meta = "", plot = "";
    /** An age rating ("12", "PG"), shown in an outlined box after the facts. */
    String badge = "";
    private float rating;
    private StaticLayout titleL, plotL;

    private final TextPaint chipP = Dark.text(24, 500, Dark.MUTED), metaP = Dark.text(25, 500, Dark.SOFT);
    private final TextPaint plotP = Dark.text(27, 400, 0xE6FFFFFF), ageP = Dark.text(19, 700, Dark.SOFT);
    private TextPaint titleP = title(72f);
    private final Paint star = Dark.fill(Dark.ACCENT), ageBox = Dark.line(0x99FFFFFF, 1.5f);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Runnable redraw = this::invalidate;

    Hero(Context c) {
        super(c);
        picture = new Backdrop(c, Billboard.PIC_LEFT, 0f, 1920f - Billboard.PIC_LEFT, Billboard.PIC_BOTTOM, Pics.DARK);
    }

    private static TextPaint title(float size) {
        TextPaint p = Dark.text(size, 700, Dark.TEXT);
        p.setLetterSpacing(-0.01f);
        return p;
    }

    void setTitleSize(float size) {
        titleSize = size;
        titleP = title(size);
    }

    /**
     * What to show: a small line (or a channel's logo), the title, the facts, a rating out of ten (0 for none), the
     * story, and its picture ("" for none). A tall picture (a poster) is not drawn: the dark screens show wide ones only.
     */
    void show(String chipText, String logoUrl, String titleText, String metaText, float outOfTen, String plotText, String pictureUrl,
              boolean isTall) {
        String t = titleText == null ? "" : titleText, l = logoUrl == null ? "" : logoUrl;
        boolean other = !t.equals(title) || !l.equals(logo);
        chip = chipText == null ? "" : chipText;
        logo = l;
        title = t;
        meta = metaText == null ? "" : metaText;
        rating = outOfTen;
        plot = plotText == null ? "" : plotText;
        picture.set(isTall || pictureUrl == null ? "" : pictureUrl);
        titleL = t.isEmpty() ? null : Ui.layout(t, titleP, Ui.px(textW), titleLines);
        plotL = plot.isEmpty() ? null : Ui.layout(plot, plotP, Ui.px(textW), plotLines);
        if (other) Fade.in(this);
        invalidate();
    }

    void clear() {
        show("", "", "", "", 0, "", "", false);
    }

    @Override protected void onDraw(Canvas c) {
        float x = Ui.f(textX), y;
        if (!logo.isEmpty()) {
            // A channel: its logo (made ready for black where Settings put the logos on tiles) over what's on.
            float top = Ui.f(textY - 44), h = Ui.f(72);
            Bitmap b = Pics.get(logo, Ui.px(220), Ui.px(72), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
            if (b != null) c.drawBitmap(b, x, top + (h - b.getHeight()) / 2f, bmp);
            y = top + h + Ui.f(16);
        } else {
            if (!chip.isEmpty()) c.drawText(Ui.fit(chip, chipP, Ui.f(textW)), x, Ui.f(textY), chipP);
            y = Ui.f(textY + 24);
        }
        if (titleL != null) {
            Ui.draw(c, titleL, x, y);
            y += titleL.getHeight() + Ui.f(22);
        }
        if (rating > 0 || !meta.isEmpty() || !badge.isEmpty()) {
            float base = y + Ui.f(10), mx = x;
            if (rating > 0) {
                Dark.icon(c, Dark.STAR, mx + Ui.f(11), base - Ui.f(8), Ui.f(22), star, star);
                String r = String.format(Locale.UK, "%.1f", Math.min(10f, rating));
                c.drawText(r, mx + Ui.f(28), base, metaP);
                mx += Ui.f(28) + metaP.measureText(r);
                if (!meta.isEmpty()) {
                    c.drawText(" · ", mx, base, metaP);
                    mx += metaP.measureText(" · ");
                }
            }
            if (!meta.isEmpty()) {
                String m = Ui.fit(meta, metaP, Ui.f(textW) - (mx - x) - Ui.f(80));
                c.drawText(m, mx, base, metaP);
                mx += metaP.measureText(m) + Ui.f(14);
            }
            if (!badge.isEmpty()) {
                String a = badge.length() > 5 ? badge.substring(0, 5) : badge;
                float aw = ageP.measureText(a) + Ui.f(14);
                c.drawRoundRect(mx, base - Ui.f(22), mx + aw, base + Ui.f(5), Ui.f(4), Ui.f(4), ageBox);
                c.drawText(a, mx + Ui.f(7), base - Ui.f(1), ageP);
            }
            y = base + Ui.f(24);
        }
        if (plotL != null) Ui.draw(c, plotL, x, y);
    }
}
