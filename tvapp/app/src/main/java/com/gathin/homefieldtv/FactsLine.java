package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayList;
import java.util.List;

/**
 * The line of facts under a title's name: its rating (bold, with a star), its years, its age in a thin box, its length
 * or seasons, its best picture and sound in boxes (4K, HD, Dolby Vision or HDR, Atmos), and the family's own TorBox
 * titles marked with its cloud. Made once for a title (set), drawn as often as needed.
 */
final class FactsLine {
    private static final int BOLD = 0, PLAIN = 1, CHIP = 2, TORBOX = 3;
    private final List<String> words = new ArrayList<>();
    private final List<Integer> kinds = new ArrayList<>();
    private final TextPaint bold, plain, chip;
    private final Paint line = Ui.stroke(Cine.LINE, 1.6f);

    FactsLine(float size) {
        bold = Cine.text(size, Cine.interSemi(), Cine.TEXT);
        plain = Cine.text(size, Cine.inter(), Cine.SOFT);
        chip = Cine.text(size * 0.85f, Cine.interSemi(), Cine.SOFT);
    }

    void set(Catalog.Title t, Catalog.Details d, Tmdb.Extras x) {
        words.clear();
        kinds.clear();
        if (t == null) return;
        String rating = d != null && !d.rating.isEmpty() ? d.rating : t.rating;
        if (!rating.isEmpty()) add("★ " + rating, BOLD);
        String years = x != null && !x.years.isEmpty() ? x.years : t.year;
        if (!years.isEmpty()) add(years, PLAIN);
        String age = x != null && !x.age.isEmpty() ? x.age : d == null ? "" : d.age;
        if (!age.isEmpty()) add(age, CHIP);
        if (t.series && d != null && !d.seasons.isEmpty()) add(d.seasons.size() == 1 ? "1 season" : d.seasons.size() + " seasons", PLAIN);
        else if (!t.series && d != null && d.minutes > 0) add(Ui.duration(d.minutes), PLAIN);
        int pic = t.bestPicture();
        if (pic == Catalog.PICTURE_4K) add("4K", CHIP);
        else if (pic == Catalog.PICTURE_1080) add("HD", CHIP);
        if (t.anyDolbyVision()) add("Dolby Vision", CHIP);
        else if (!t.hdr.isEmpty()) add("HDR", CHIP);
        if (t.anyAtmos()) add("Atmos", CHIP);
        if (t.torbox()) add("In your TorBox", TORBOX);
    }

    private void add(String w, int kind) {
        words.add(w);
        kinds.add(kind);
    }

    boolean isEmpty() {
        return words.isEmpty();
    }

    /** Drawn from x on the baseline, at an alpha (0-255). */
    void draw(Canvas c, float x, float base, int alpha) {
        bold.setAlpha(alpha);
        plain.setAlpha(alpha * 0xE6 / 255);
        chip.setAlpha(alpha * 0xE6 / 255);
        line.setAlpha(alpha * 0x66 / 255);
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            switch (kinds.get(i)) {
                case BOLD:
                    c.drawText(w, x, base, bold);
                    x += bold.measureText(w);
                    break;
                case CHIP:
                    x += Cine.chip(c, w, x, base, chip, line);
                    break;
                case TORBOX:
                    SourcePicker.badge(c, x + Ui.f(16f), base - Ui.f(9f), Ui.f(32f), alpha);
                    x += Ui.f(40f);
                    c.drawText(w, x, base, plain);
                    x += plain.measureText(w);
                    break;
                default:
                    c.drawText(w, x, base, plain);
                    x += plain.measureText(w);
                    break;
            }
            x += Ui.f(26f);
        }
    }
}
