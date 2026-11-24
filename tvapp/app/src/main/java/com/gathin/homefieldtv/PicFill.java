package com.gathin.homefieldtv;

/**
 * Where a programme's picture goes when it fills a whole cell, panel or card, whatever its size, as the family asked
 * ("I want the pictures in the programmes panel to take up the whole panel no matter what size it is"), with its faces
 * whole: pure sums, the same at decode (Pics.FILL, FILL_CARD: the picture made once at the size drawn) and while
 * drawing (where its faces landed, to place a narrower window on them and to keep the words off them).
 *
 * <pre>
 *   cover        the picture scaled to cover the cell, its faces (Faces' box, kept with it) whole with a little room,
 *                their middle toward the cell's free side (the words are on the left) where the picture allows,
 *                zooming a little further (at most ZOOM) when that takes them out from under the words; with no faces
 *                known, its middle, a little above
 *   ambient      a cell so wide and short that covering it would cut the faces (or leave them under the words): the
 *                picture blurred to cover the cell (its own colours behind everything), and over it the picture sharp,
 *                as tall as the faces need, its faces in the cell's free part (beside the words; the info card's:
 *                between its words and what's next), whole, its inner edges feathered into the blur
 * </pre>
 *
 * In units of the picture's height (its width is aspect): {@code layout(aspect, box, w, h, card, out)} gives out[KIND]
 * (COVER or AMBIENT), out[K] (cell px for one unit: the sharp picture's scale), out[X], out[Y] (where its top left
 * lands in the cell), out[FL..FB] (where its faces land, cell px; the whole cell for none) and out[FEATHER_L],
 * out[FEATHER_R] (the sharp picture's feathered edges in cell px, 0 for none).
 */
final class PicFill {
    private PicFill() {}

    static final int KIND = 0, K = 1, X = 2, Y = 3, FL = 4, FT = 5, FR = 6, FB = 7, FEATHER_L = 8, FEATHER_R = 9, SIZE = 10;
    static final float COVER = 0f, AMBIENT = 1f;
    /**
     * A row's cell (the guide's, the mini guide's): its words (a title and a line of times) take about ROW_WORDS of its
     * height across at its left, as a share of it between ROW_ROOM_MIN and ROW_ROOM_MAX; its faces go from there to its
     * right (ROW_END), their middle half way.
     */
    static final float ROW_WORDS = 2.7f, ROW_ROOM_MIN = 0.3f, ROW_ROOM_MAX = 0.62f, ROW_END = 1f;
    /**
     * The info card (1824 wide): its words take its left CARD_ROOM, what's next its right from CARD_END (NEXT_X less a
     * little), its faces between them.
     */
    static final float CARD_ROOM = 0.52f, CARD_END = (InfoCard.NEXT_X - InfoCard.X - 24f) / InfoCard.W;
    /** Room kept round the faces: a share of the cell's height above and below, either side at most that too. */
    static final float ROOM_H = 0.07f, ROOM_W = 0.03f;
    /** At most this much past covering, to give the faces room beside the words. */
    static final float ZOOM = 1.6f;
    /** A cell this many times as wide as tall may take the ambient fill. */
    static final float WIDE = 2.2f;
    /** The sharp picture's feathered edge: this share of its width, at most this many times the cell's height. */
    static final float FEATHER = 0.26f, FEATHER_MAX = 0.9f;

    /** True for Faces' stand-in box (no face found): then there are no faces to keep whole. */
    static boolean none(float[] box) {
        return box == null || box[0] == Faces.FALLBACK[0] && box[1] == Faces.FALLBACK[1] && box[2] == Faces.FALLBACK[2]
                && box[3] == Faces.FALLBACK[3];
    }

    /**
     * Pure: the layout of a picture (aspect: its width / height; box: its faces, shares, or null) filling w x h, a
     * row's cell or (card) the info card.
     */
    static void layout(float aspect, float[] box, float w, float h, boolean card, float[] out) {
        float a = Math.max(0.05f, aspect);
        float kc = Math.max(w / a, h);
        for (int i = 0; i < SIZE; i++) out[i] = 0f;
        out[KIND] = COVER;
        if (none(box)) {
            // No faces: covering, the middle across, a little above the middle down.
            out[K] = kc;
            out[X] = (w - a * kc) / 2f;
            out[Y] = (h - kc) * 0.38f;
            out[FL] = 0f;
            out[FT] = 0f;
            out[FR] = w;
            out[FB] = h;
            return;
        }
        boolean wide = w >= WIDE * h;
        // The free part (cell px) the faces go in, and where across it their middle goes. A narrow cell's words run
        // across most of it: its faces in the middle there.
        float room = wide ? room(w, h, card) * w : 0f, end = wide ? (card ? CARD_END : ROW_END) * w : w;
        float bias = wide ? (room + end) / (2f * w) : 0.5f;
        float fl = box[0] * a, fr = box[2] * a, ft = box[1], fb = box[3];
        float fw = Math.max(0.001f, fr - fl), fh = Math.max(0.001f, fb - ft), fcx = (fl + fr) / 2f, fcy = (ft + fb) / 2f;
        float my = ROOM_H * h, mx = Math.min(ROOM_W * w, my);
        // The most it may be scaled with its faces whole (and room round them), and in the free part.
        float kmax = Math.min((h - 2 * my) / fh, (w - 2 * mx) / fw);
        float kfree = Math.min(kmax, (end - room - 2 * mx) / fw);
        if (kmax >= kc) {
            // Covering holds the faces: as far toward bias as covering allows; zoomed a little (at most ZOOM) when that
            // takes them out from under the words.
            float k = kc, x = clamp(bias * w - fcx * k, w - a * k, 0f);
            if (wide && !free(x, k, fl, fr, room, end)) {
                float need = kc;
                if (a - fcx > 1e-4f) need = Math.max(need, w * (1f - bias) / (a - fcx));
                if (fcx > 1e-4f) need = Math.max(need, bias * w / fcx);
                k = Math.max(kc, Math.min(need, Math.min(kfree, kc * ZOOM)));
                x = clamp(bias * w - fcx * k, w - a * k, 0f);
            }
            if (!wide || free(x, k, fl, fr, room, end)) {
                cover(out, k, x, h, ft, fb, fcy, fl, fr, my);
                return;
            }
        } else if (!wide) {
            // A narrow cell: covering, centred on the faces (the outer ones may be cut).
            float x = clamp(w / 2f - fcx * kc, w - a * kc, 0f);
            cover(out, kc, x, h, ft, fb, fcy, fl, fr, my);
            return;
        }
        // Ambient: the sharp picture as tall as the faces need (covering the height), its faces at bias, whole, in
        // the free part where they fit there.
        float ks = Math.max(Math.min(Math.min(kmax, kfree), kc), h);
        float x = bias * w - fcx * ks;
        x = Math.max(x, mx - fl * ks);
        x = Math.min(x, w - mx - fr * ks);
        out[KIND] = AMBIENT;
        out[K] = ks;
        out[X] = x;
        out[Y] = top(ks, h, ft, fb, fcy, my);
        faces(out, ks, fl, fr, ft, fb);
        float pw = a * ks, f = Math.min(FEATHER * pw, FEATHER_MAX * h);
        out[FEATHER_L] = x > 0.5f ? Math.max(0f, Math.min(f, out[FL] - x - 2f)) : 0f;
        out[FEATHER_R] = x + pw < w - 0.5f ? Math.max(0f, Math.min(f, x + pw - out[FR] - 2f)) : 0f;
    }

    /** Pure: the share of a cell w x h its words take at its left (the info card's: CARD_ROOM). */
    static float room(float w, float h, boolean card) {
        if (card) return CARD_ROOM;
        return Math.max(ROW_ROOM_MIN, Math.min(ROW_ROOM_MAX, ROW_WORDS * h / Math.max(1f, w)));
    }

    /** Whether faces fl..fr (units), scaled by k from x, sit in the free part room..end (a little slack). */
    private static boolean free(float x, float k, float fl, float fr, float room, float end) {
        return x + fl * k >= room - 0.5f && x + fr * k <= end + 0.5f;
    }

    private static void cover(float[] out, float k, float x, float h, float ft, float fb, float fcy, float fl, float fr, float my) {
        out[KIND] = COVER;
        out[K] = k;
        out[X] = x;
        out[Y] = top(k, h, ft, fb, fcy, my);
        faces(out, k, fl, fr, ft, fb);
    }

    /** Where the picture's top goes (cell px, 0 or above): its faces whole where they fit, a little above the middle. */
    private static float top(float k, float h, float ft, float fb, float fcy, float my) {
        float y = h * 0.46f - fcy * k;
        if ((fb - ft) * k + 2 * my <= h) {
            // The faces whole: their top at least my down, their foot at least my up.
            y = Math.min(y, my - ft * k);
            y = Math.max(y, h - my - fb * k);
        } else {
            y = my - ft * k;
        }
        return clamp(y, h - k, 0f);
    }

    private static void faces(float[] out, float k, float fl, float fr, float ft, float fb) {
        out[FL] = out[X] + fl * k;
        out[FR] = out[X] + fr * k;
        out[FT] = out[Y] + ft * k;
        out[FB] = out[Y] + fb * k;
    }

    /**
     * Pure: where a window win wide goes across a picture made full wide (its faces at fl..fr), so its faces show:
     * beside the words (which end wordsEnd into the window) where there is room for them, else in its middle (a narrow
     * window centres on them); whole where they fit. The window's left, 0 to full - win.
     */
    static float window(float full, float win, float fl, float fr, float wordsEnd, float room) {
        if (win >= full - 0.5f) return 0f;
        float fw = fr - fl, fc = (fl + fr) / 2f;
        float side = win - wordsEnd;
        float at = side >= fw + 2 * room ? wordsEnd + side / 2f : win / 2f;
        float x = fc - at;
        if (fw + 2 * room <= win) {
            x = Math.min(x, fl - room);
            x = Math.max(x, fr + room - win);
        }
        return clamp(x, 0f, full - win);
    }

    private static float clamp(float v, float lo, float hi) {
        return lo > hi ? (lo + hi) / 2f : Math.max(lo, Math.min(hi, v));
    }
}
