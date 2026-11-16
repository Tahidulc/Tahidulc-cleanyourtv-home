package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;

/**
 * The big picture over Films' and TV shows' rows: whatever the remote is on, its backdrop on the right fading into the
 * dark, and at the left its name and year small, its title large, a line of facts (★ rating · genre · running time and
 * the age rating), a few lines of its story, and who is in it (their initials in circles, then their names). The
 * picture is faded once as it loads, so moving along a row costs nothing.
 */
final class Billboard extends View {
    /** Where the writing goes, and the picture (design pixels). */
    float textX = 160f, textW = 860f, picLeft = 640f, picBottom = 760f;

    private Catalog.Title title;
    private String kicker = "", meta = "", age = "", plot = "", picture = "", note = "";
    private List<String> cast = new ArrayList<>();
    private float rating;
    private long shownAt;
    private StaticLayout titleL, plotL;

    private final TextPaint kickerP = Dark.text(24, 500, Dark.MUTED), titleP = Dark.text(82, 700, Dark.TEXT);
    private final TextPaint metaP = Dark.text(25, 500, Dark.SOFT), plotP = Dark.text(27, 400, 0xE6FFFFFF);
    private final TextPaint castP = Dark.text(23, 500, Dark.SOFT), initialsP = Dark.text(18, 600, Dark.TEXT);
    private final TextPaint ageP = Dark.text(19, 700, Dark.SOFT), noteP = Dark.text(24, 600, Dark.ACCENT);
    private final Paint star = Dark.fill(Dark.ACCENT), disc = Dark.fill(0), discRim = Dark.line(Dark.GROUND, 3f);
    private final Paint ageBox = Dark.line(0x99FFFFFF, 1.5f), fade = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Runnable redraw = this::invalidate;

    Billboard(Context c) {
        super(c);
        titleP.setLetterSpacing(-0.01f);
    }

    /** What to show: a title, its details when read (null until then), and a line about it ("S2 E3 · 21m left"). */
    void show(Catalog.Title t, Catalog.Details d, String noteText) {
        if (t == null) {
            clear();
            return;
        }
        String pic = d != null && !d.backdrop.isEmpty() ? d.backdrop : t.backdrop;
        if (!pic.equals(picture) || t != title) shownAt = SystemClock.uptimeMillis();
        title = t;
        picture = pic;
        kicker = t.name + (t.year.isEmpty() ? "" : " (" + t.year + ")");
        rating = rating(d != null && !d.rating.isEmpty() ? d.rating : t.rating);
        meta = facts(t, d);
        age = d == null ? "" : d.age;
        plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        cast = names(d == null ? "" : d.cast, 3);
        note = noteText == null ? "" : noteText;
        titleL = Ui.layout(t.name, titleP, Ui.px(textW), 2);
        plotL = plot.isEmpty() ? null : Ui.layout(plot, plotP, Ui.px(textW), 3);
        invalidate();
    }

    void clear() {
        title = null;
        picture = "";
        titleL = plotL = null;
        invalidate();
    }

    /** Pure: a rating out of ten from the provider's words ("7.4", "0"), 0 when none. */
    static float rating(String r) {
        try {
            float v = r == null || r.isEmpty() ? 0f : Float.parseFloat(r);
            return v >= 0.5f ? Math.min(10f, v) : 0f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    /** Pure: the facts after the stars: the genre (else the category's), the running time or the series' count. */
    static String facts(Catalog.Title t, Catalog.Details d) {
        List<String> parts = new ArrayList<>();
        String g = d != null ? PosterArt.firstGenre(d.genre) : "";
        if (g.isEmpty()) g = PosterArt.genreOf(t.category);
        if (!g.isEmpty()) parts.add(g);
        if (d != null && d.minutes > 0 && !t.series) parts.add(Ui.duration(d.minutes));
        if (d != null && !d.seasons.isEmpty()) parts.add(d.seasons.size() == 1 ? "1 season" : d.seasons.size() + " seasons");
        return String.join(" · ", parts);
    }

    /** Pure: the first few names of a cast list ("A, B, C, D" → [A, B, C]). */
    static List<String> names(String cast, int max) {
        List<String> out = new ArrayList<>();
        if (cast == null) return out;
        for (String n : cast.split(",")) {
            String s = n.trim();
            if (!s.isEmpty() && out.size() < max) out.add(s);
        }
        return out;
    }

    @Override protected void onDraw(Canvas c) {
        if (title == null) return;
        drawPicture(c);
        float x = Ui.f(textX), y = Ui.f(104);
        c.drawText(Ui.fit(kicker, kickerP, Ui.f(textW)), x, y, kickerP);
        y += Ui.f(24);
        if (titleL != null) {
            Ui.draw(c, titleL, x, y);
            y += titleL.getHeight() + Ui.f(22);
        }
        float base = y + Ui.f(10);
        float mx = x;
        if (rating > 0) {
            Dark.icon(c, Dark.STAR, mx + Ui.f(11), base - Ui.f(8), Ui.f(22), star, star);
            String r = String.format(java.util.Locale.UK, "%.1f", rating);
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
        if (!age.isEmpty()) {
            String a = age.length() > 4 ? age.substring(0, 4) : age;
            float aw = ageP.measureText(a) + Ui.f(14);
            c.drawRoundRect(mx, base - Ui.f(22), mx + aw, base + Ui.f(5), Ui.f(4), Ui.f(4), ageBox);
            c.drawText(a, mx + Ui.f(7), base - Ui.f(1), ageP);
        }
        y = base + Ui.f(24);
        if (!note.isEmpty()) {
            c.drawText(Ui.fit(note, noteP, Ui.f(textW)), x, y + Ui.f(10), noteP);
            y += Ui.f(36);
        }
        if (plotL != null) {
            Ui.draw(c, plotL, x, y);
            y += plotL.getHeight() + Ui.f(26);
        }
        if (!cast.isEmpty()) {
            float r = Ui.f(22), cx = x + r, cy = y + r;
            for (int i = 0; i < cast.size(); i++) {
                float px = cx + i * Ui.f(32);
                c.drawCircle(px, cy, r + Ui.f(1.5f), discRim);
                Dark.avatar(c, px, cy, r, cast.get(i), disc, initialsP);
            }
            float tx = cx + (cast.size() - 1) * Ui.f(32) + r + Ui.f(16);
            c.drawText(Ui.fit(String.join(", ", cast), castP, Ui.f(textX + textW) - tx), tx, cy + Ui.f(8), castP);
        }
    }

    private void drawPicture(Canvas c) {
        if (picture.isEmpty()) return;
        int pw = Ui.px(1920 - picLeft), ph = Ui.px(picBottom);
        Bitmap b = Pics.get(picture, pw, ph, Pics.DARK, redraw);
        if (b == null) return;
        long age = SystemClock.uptimeMillis() - shownAt;
        int alpha = Exec.inline ? 255 : (int) Math.min(255, age * 255 / 260);
        fade.setAlpha(alpha);
        c.drawBitmap(b, Ui.f(picLeft), 0, fade);
        fade.setAlpha(255);
        if (alpha < 255) postInvalidateOnAnimation();
    }
}
