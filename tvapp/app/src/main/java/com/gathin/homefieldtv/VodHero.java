package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The words at the top of Movies, TV shows and a title's page, as the family's other app has them: the title and its
 * year large (54 px, medium, up to two lines), under it a grey chip with the rating ("5.8") then the year, the running
 * time or the seasons, and the genres ("2026  •  1h 34m  •  Horror"), then "Cast:" and "Director:" in grey with the
 * names after them, then the story in grey for as many lines as there is room down to bottom. Its left edge (x) moves
 * with the categories' column. The title's picture is a Backdrop under it (the screen adds it).
 */
final class VodHero extends View {
    /** Where it goes (design pixels): its left, its top and how far down it may write; its width. */
    float x = 56f, top = 40f, bottom = 520f, w = 860f;
    private Catalog.Title title;
    private String meta = "", rating = "", cast = "", director = "", plot = "", tag = "";
    private StaticLayout titleL, plotL;
    private float laidFor = -1f;

    private final TextPaint titleP = Type.paint(54f, Type.MEDIUM, Palette.TEXT1), metaP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint labelP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2), nameP = Type.paint(Type.ROW, Type.REGULAR, 0xD9FFFFFF);
    private final TextPaint plotP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);

    VodHero(Context c) {
        super(c);
    }

    /** What to show: a title and its details when read (null until then); a word of where it comes from ("" none). */
    void show(Catalog.Title t, Catalog.Details d, String from) {
        if (t == null) {
            clear();
            return;
        }
        title = t;
        float r = Billboard.rating(d != null && !d.rating.isEmpty() ? d.rating : t.rating);
        rating = r > 0 ? String.format(Locale.UK, "%.1f", r) : "";
        meta = meta(t, d);
        cast = String.join(", ", Billboard.names(d == null ? "" : d.cast, 8));
        director = d == null ? "" : d.director.trim();
        plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        tag = from == null ? "" : from;
        laidFor = -1f;
        invalidate();
    }

    void clear() {
        title = null;
        invalidate();
    }

    /** Pure: the facts after the rating: the year, the running time (or the seasons), the genres. */
    static String meta(Catalog.Title t, Catalog.Details d) {
        List<String> parts = new ArrayList<>();
        if (!t.year.isEmpty()) parts.add(t.year);
        if (!t.series && d != null && d.minutes > 0) parts.add(Ui.duration(d.minutes));
        if (t.series && d != null && !d.seasons.isEmpty()) parts.add(d.seasons.size() == 1 ? "1 season" : d.seasons.size() + " seasons");
        String g = d == null ? "" : genres(d.genre, 2);
        if (g.isEmpty()) g = PosterArt.genreOf(t.category);
        if (!g.isEmpty()) parts.add(g);
        return String.join("  •  ", parts);
    }

    /** Pure: the first few of a details page's genres, as the other app lists them ("Action, Fantasy"). */
    static String genres(String all, int max) {
        if (all == null) return "";
        List<String> out = new ArrayList<>();
        for (String g : all.split("[,/|&]")) {
            String s = g.trim();
            if (!s.isEmpty() && s.length() <= 24 && out.size() < max) out.add(s);
        }
        return String.join(", ", out);
    }

    private void layOut() {
        if (laidFor == Ui.s) return;
        laidFor = Ui.s;
        String name = title.name + (title.year.isEmpty() || title.name.endsWith(")") ? "" : " (" + title.year + ")");
        titleL = Ui.layout(name, titleP, Ui.px(Math.max(w, 1300f)), 2);
        plotL = null;
    }

    @Override protected void onDraw(Canvas c) {
        if (title == null) return;
        layOut();
        float x0 = Ui.f(x);
        titleP.setColor(Palette.TEXT1);
        Ui.draw(c, titleL, x0, Ui.f(top));
        float y = top + titleL.getHeight() / Ui.s + 46f;
        // The rating's grey chip, then the facts.
        float mx = x0;
        if (!rating.isEmpty()) mx += Chips.grey(c, mx, Ui.f(y - 10f), rating) + Ui.f(32);
        metaP.setColor(Palette.TEXT2);
        String m = meta + (tag.isEmpty() ? "" : (meta.isEmpty() ? "" : "  •  ") + tag);
        c.drawText(Ui.fit(m, metaP, Ui.f(x + w) - mx), mx, Ui.f(y), metaP);
        y += 54f;
        // Cast and Director: their labels in grey, the names lined up after the longer label.
        labelP.setColor(Palette.TEXT2);
        float labelW = Math.max(cast.isEmpty() ? 0f : labelP.measureText("Cast:"), director.isEmpty() ? 0f : labelP.measureText("Director:"));
        float vx = x0 + labelW + Ui.f(14);
        if (!cast.isEmpty()) {
            c.drawText("Cast:", x0, Ui.f(y), labelP);
            c.drawText(Ui.fit(cast, nameP, Ui.f(x + w) - vx), vx, Ui.f(y), nameP);
            y += 38f;
        }
        if (!director.isEmpty()) {
            c.drawText("Director:", x0, Ui.f(y), labelP);
            c.drawText(Ui.fit(director, nameP, Ui.f(x + w) - vx), vx, Ui.f(y), nameP);
            y += 38f;
        }
        if (!cast.isEmpty() || !director.isEmpty()) y += 16f;
        // The story, as many lines as fit above bottom.
        if (!plot.isEmpty()) {
            int lines = (int) Math.floor((bottom - (y - 24f)) / 33f);
            if (lines > 0) {
                if (plotL == null || plotL.getLineCount() > lines || plotL.getWidth() != Ui.px(w)) plotL = Ui.layout(plot, plotP, Ui.px(w), lines);
                plotP.setColor(Palette.TEXT2);
                Ui.draw(c, plotL, x0, Ui.f(y - 24f));
            }
        }
    }

    /** The words' place moved (the categories' column in or out). */
    void moveTo(float left) {
        if (x == left) return;
        x = left;
        invalidate();
    }
}
