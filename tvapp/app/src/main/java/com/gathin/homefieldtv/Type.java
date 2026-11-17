package com.gathin.homefieldtv;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;

/**
 * The words: the TV's own sans-serif (Roboto), regular or medium, at the sizes the screens use. Sizes are design pixels of
 * a 1920 x 1080 screen (Ui.f), the font's size as measured on the family's TV; Settings → Appearance → Text size scales
 * them all (prefs.Look.text).
 *
 * <pre>
 *   HERO    41  medium   a programme's or a film's title at the top of the guide and of Movies
 *   TITLE   40  medium   a panel's title in its band (Settings)
 *   PICKER  36  medium   a picker's title
 *   HINT    34           the search field's hint
 *   ITEM    31.5         the drawer's items, tile labels, search chips, empty-state titles (32)
 *   ROW     28.5         every list's rows: settings, groups, channel names, guide cells, times
 *   SMALL   24.5         a row's value, section names, notes, descriptions
 *   CHIP    23.5         labels under round buttons; CHIP_BOLD 20 bold: the letters on a media chip (FHD, STEREO)
 * </pre>
 *
 * paint(size, weight, colour) gives a TextPaint (REGULAR 400, MEDIUM 500, BOLD 700); face(weight) the typeface;
 * condensed() Roboto Condensed, for the guide's cells.
 */
final class Type {
    private Type() {}

    static final float HERO = 41f, TITLE = 40f, PICKER = 36f, HINT = 34f, ITEM = 31.5f, ROW = 28.5f, SMALL = 24.5f, CHIP = 23.5f,
            CHIP_BOLD = 20f;
    static final int REGULAR = 400, MEDIUM = 500, BOLD = 700;

    private static Typeface regular, medium, bold, condensed;

    /** The typeface for a weight: up to 450 regular, up to 650 medium, then bold. */
    static Typeface face(int weight) {
        if (weight >= 650) {
            if (bold == null) bold = make("sans-serif", Typeface.BOLD, 700);
            return bold;
        }
        if (weight >= 450) {
            if (medium == null) medium = make("sans-serif-medium", Typeface.NORMAL, 500);
            return medium;
        }
        if (regular == null) regular = make("sans-serif", Typeface.NORMAL, 400);
        return regular;
    }

    /** Roboto Condensed (the guide's programme cells). */
    static Typeface condensed() {
        if (condensed == null) condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        return condensed;
    }

    private static Typeface make(String family, int style, int weight) {
        Typeface base = Typeface.create(family, style);
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false);
            } catch (RuntimeException e) {
                return base;
            }
        }
        return base;
    }

    /** Words at a size (design pixels) and weight, in a colour; scaled by Settings → Appearance → Text size. */
    static TextPaint paint(float size, int weight, int colour) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face(weight));
        p.setTextSize(Ui.f(size) * com.gathin.homefieldtv.prefs.Look.text);
        p.setColor(colour);
        return p;
    }

    /** Where text sits in a box: the baseline that centres a line's capitals on cy (screen pixels). */
    static float centred(TextPaint p, float cy) {
        // Roboto's capitals are 0.711 of the font's size.
        return cy + p.getTextSize() * 0.711f / 2f;
    }
}
