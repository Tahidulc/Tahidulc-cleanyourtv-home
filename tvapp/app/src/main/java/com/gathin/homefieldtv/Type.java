package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

/**
 * The words, in the 07 look: Inter (OFL, res/font/inter_*.ttf, licence in assets/licences/inter-OFL.txt) at the sizes
 * and weights the approved mock-ups use. Sizes are design pixels of a 1920 x 1080 screen (Ui.f); Settings → Appearance
 * → Text size scales them all (prefs.Look.text). Words of 28 px and over at 600 and heavier are drawn in Inter's
 * display cut (its optical size 32, a little tighter), as the mock-ups' browser drew them; smaller ones in its text cut.
 * Every paint carries the mock-ups' letter spacing for its size (-0.01 em, tighter for the big titles).
 *
 * <pre>
 *   the 07 scale (size / weight: where)
 *   H1        64 / 700   a title over its picture (Home's card 66, a film's page)
 *   H2        46 / 700   a page's own title
 *   TITLE40   40 / 700   the guide's programme panel, a catch-up channel's name
 *   H3        30 / 700   a shelf's title, Settings' name, a menu card's heading
 *   CLOCK     32 / 600   the clock at the top right (tabular figures: clock())
 *   BRAND     28 / 700   "Homefield" beside the mark
 *   BUTTON    26 / 600   a button (BUTTON_SM 22)
 *   ROW07     25 / 500   a list row (Settings, results); its value ROW_VALUE 22 / 500
 *   TAB       24 / 500   a tab (600 on the white pill)
 *   SUB       23 / 500   the line under a title; CAPTION 23 / 600 a card's name, CAPTION_SUB 19 / 500 under it
 *   PILL      22 / 500   a pill (600 when on)
 *   BODY      21 / 400   what a programme is about (lines 1.42 apart)
 *   MENU      21 / 500   a menu's line; MENU_VALUE 17 / 500 its value at the right
 *   CAP       20 / 500   a small grey line; BACK 20 / 600 the "‹ back" line
 *   LABEL     15 / 700   a section's name in capitals, letter-spaced 0.1 em (label())
 *   CHIP07    15 / 700   a chip's letters (HD, 50 FPS); CHIP_SMALL 13 / 800 (4K on a poster)
 *
 *   the older names, now at their 07 sizes (the screens not yet redrawn use them)
 *   HERO 40 · TITLE 34 · PICKER 30 · HINT 34 · ITEM 25 · ROW 22 · SMALL 18 · CHIP 19 · CHIP_BOLD 15
 * </pre>
 *
 * paint(size, weight, colour) gives a TextPaint; face(weight) the text cut, face(weight, size) the cut for that size;
 * label(size, colour) the capitals' paint; clock(size, weight, colour) with tabular figures; condensed() Roboto
 * Condensed (the old guide cells). Type.init(context) once (Dark.init and Cine.init call it); before that, the TV's
 * own sans-serif stands in.
 */
final class Type {
    private Type() {}

    // ---- the 07 scale ---------------------------------------------------------------------------------------------
    static final float H1 = 64f, H2 = 46f, TITLE40 = 40f, H3 = 30f, CLOCK = 32f, BRAND = 28f, BUTTON = 26f, BUTTON_SM = 22f,
            ROW07 = 25f, ROW_VALUE = 22f, TAB = 24f, SUB = 23f, CAPTION = 23f, CAPTION_SUB = 19f, PILL = 22f, BODY = 21f,
            MENU = 21f, MENU_VALUE = 17f, CAP = 20f, BACK = 20f, LABEL = 15f, CHIP07 = 15f, CHIP_SMALL = 13f;
    /** BODY's lines, as a multiple of its size. */
    static final float BODY_LINES = 1.42f;
    /** A section name's letter spacing (em). */
    static final float LABEL_SPACING = 0.1f;

    // ---- the older names at 07 sizes ------------------------------------------------------------------------------
    static final float HERO = 40f, TITLE = 34f, PICKER = 30f, HINT = 34f, ITEM = 25f, ROW = 22f, SMALL = 18f, CHIP = 19f,
            CHIP_BOLD = 15f;
    static final int REGULAR = 400, MEDIUM = 500, SEMIBOLD = 600, BOLD = 700, EXTRABOLD = 800;

    /** Inter's capitals: 0.727 of its size (1490 / 2048). */
    static final float CAP_HEIGHT = 0.7275f;

    /** The application's resources, for the fonts (kept rather than a context). */
    private static android.content.res.Resources res;
    /** The text cut by weight step (400, 500, 600, 700, 800), the display cut (600, 700, 800). */
    private static final Typeface[] TEXT = new Typeface[5], DISPLAY = new Typeface[3];
    private static Typeface condensed;

    /** The application's context, for the fonts (once; Dark.init and Cine.init call it). */
    static void init(Context c) {
        if (res == null && c != null) res = c.getApplicationContext().getResources();
    }

    /** Pure: a weight's step in the text cut: up to 450 regular (0), 550 medium, 650 semibold, 750 bold, then extrabold. */
    static int step(int weight) {
        if (weight < 450) return 0;
        if (weight < 550) return 1;
        if (weight < 650) return 2;
        if (weight < 750) return 3;
        return 4;
    }

    /** Pure: whether words of this design size and weight are drawn in the display cut. */
    static boolean display(float size, int weight) {
        return size >= 28f && weight >= 550;
    }

    /** Pure: the mock-ups' letter spacing (em) for a design size. */
    static float spacing(float size) {
        if (size >= 60f) return -0.028f;
        if (size >= 36f) return -0.02f;
        if (size >= 28f) return -0.015f;
        return -0.01f;
    }

    private static final int[] TEXT_RES = {R.font.inter_regular, R.font.inter_medium, R.font.inter_semibold, R.font.inter_bold,
            R.font.inter_extrabold};
    private static final int[] DISPLAY_RES = {R.font.inter_display_semibold, R.font.inter_display_bold, R.font.inter_display_extrabold};

    /** Inter's text cut at a weight (up to 450 regular, 550 medium, 650 semibold, 750 bold, then extrabold). */
    static Typeface face(int weight) {
        int i = step(weight);
        Typeface t = TEXT[i];
        if (t == null) {
            t = load(TEXT_RES[i]);
            if (t == null) return system(weight);
            TEXT[i] = t;
        }
        return t;
    }

    /** Inter in the cut for words of a design size: the display cut at 28 px and over (600 and heavier). */
    static Typeface face(int weight, float size) {
        if (!display(size, weight)) return face(weight);
        int i = Math.max(0, step(weight) - 2);
        Typeface t = DISPLAY[i];
        if (t == null) {
            t = load(DISPLAY_RES[i]);
            if (t == null) return system(weight);
            DISPLAY[i] = t;
        }
        return t;
    }

    /** One of the fonts (null before init, kept null so it is tried again: the TV's sans-serif meanwhile). */
    private static Typeface load(int font) {
        if (res != null) {
            try {
                Typeface t = res.getFont(font);
                if (t != null) return t;
            } catch (RuntimeException ignored) {
                // the system's below
            }
        }
        return null;
    }

    /** The TV's own sans-serif at a weight, while Inter can't be read. */
    private static Typeface system(int weight) {
        return Typeface.create("sans-serif", weight >= 650 ? Typeface.BOLD : Typeface.NORMAL);
    }

    /** Roboto Condensed (the old guide's programme cells). */
    static Typeface condensed() {
        if (condensed == null) condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        return condensed;
    }

    /** Words at a size (design pixels) and weight, in a colour; scaled by Settings → Appearance → Text size. */
    static TextPaint paint(float size, int weight, int colour) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face(weight, size));
        p.setTextSize(Ui.f(size) * com.gathin.homefieldtv.prefs.Look.text);
        p.setLetterSpacing(spacing(size));
        p.setColor(colour);
        return p;
    }

    /** A section's name in capitals (the caller writes it in capitals): bold, letter-spaced 0.1 em. */
    static TextPaint label(float size, int colour) {
        TextPaint p = paint(size, BOLD, colour);
        p.setLetterSpacing(LABEL_SPACING);
        return p;
    }

    /** Figures that keep their places as they change (the clock, scores, times): tabular figures. */
    static TextPaint clock(float size, int weight, int colour) {
        TextPaint p = paint(size, weight, colour);
        p.setFontFeatureSettings("tnum");
        return p;
    }

    /** Where text sits in a box: the baseline that centres a line's capitals on cy (screen pixels). */
    static float centred(TextPaint p, float cy) {
        return cy + p.getTextSize() * CAP_HEIGHT / 2f;
    }
}
