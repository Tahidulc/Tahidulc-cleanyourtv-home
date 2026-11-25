package com.gathin.broadcast;

/**
 * Where a programme's picture goes in a place of any shape (a guide's cell, a panel, a card, a band), the same in Homefield
 * TV and the phone app, as the family asked ("make sure you can see the whole picture properly in line with the tv guide
 * panels and information panels when scrolling"): pure sums, worked out once when the picture is made at the size it is
 * drawn, never while drawing.
 *
 * <pre>
 *   COVER   a place close to the picture's own shape (at least KEEP of the picture shows when it covers it): the
 *           picture covers it, scaled no further than covering needs, its faces whole (Faces' box) toward the free part
 *           (beside the words), else its middle a little above the middle
 *   SHOW    a place much wider (a long guide cell, a short band) or much narrower than the picture: the whole picture,
 *           or nearly (SHOW_KEEP of it), at the place's height (or width), its faces (else its middle) at the middle of
 *           the free part, inside the place; the place's own colour from the picture continuing smoothly behind it,
 *           the picture's inner edges feathered into it (never a hard edge)
 * </pre>
 *
 * The free part is where the words aren't: from room (a share of the width, where the words end at the left) to end. A
 * picture is never enlarged past MAX_UP (tooSmall: the next source or the fallback instead). place() gives out[KIND]
 * (COVER or SHOW), out[K] (place px a picture px), out[X], out[Y] (its top left), out[PW], out[PH] (its size there),
 * out[FL..FB] (where its faces land; with none, the part of the picture in the place), out[FEATHER_L..FEATHER_B] (its feathered edges,
 * 0 where it meets the place's edge) and out[SUBJECT] (its faces' middle across from its left, else its own middle).
 *
 * <p>slide(): where a picture made for a whole guide cell goes when the cell is cut by the guide's edges as it scrolls:
 * its subject at the middle of the free part in sight, a continuous move (never a jump, never cropped again).
 */
public final class PicPlace {
    private PicPlace() {}

    public static final int KIND = 0, K = 1, X = 2, Y = 3, PW = 4, PH = 5, FL = 6, FT = 7, FR = 8, FB = 9, FEATHER_L = 10,
            FEATHER_R = 11, FEATHER_T = 12, FEATHER_B = 13, SUBJECT = 14, SIZE = 15;
    public static final float COVER = 0f, SHOW = 1f;
    /** Covering only while at least this share of the picture (its height or its width) shows. */
    public static final float KEEP = 0.7f;
    /** Shown at the place's height (or width): this much of it seen, a little cut top and bottom so no seam shows. */
    public static final float SHOW_KEEP = 0.94f;
    /** The most a picture is enlarged (place px a picture px); past it, the next source or the fallback. */
    public static final float MAX_UP = 1.5f;
    /** Room kept round the faces: a share of the place's height above and below, either side at most that too. */
    public static final float ROOM_H = 0.07f, ROOM_W = 0.03f;
    /**
     * A guide cell's words (the name or time, and the title) take about ROW_WORDS of its height across at its left, as a
     * share of it between ROW_ROOM_MIN and ROW_ROOM_MAX.
     */
    public static final float ROW_WORDS = 2.7f, ROW_ROOM_MIN = 0.3f, ROW_ROOM_MAX = 0.62f;
    /** The feathered edge: this share of the picture's width, at most FEATHER_MAX of the place's height, at least FEATHER_MIN of it. */
    public static final float FEATHER = 0.22f, FEATHER_MAX = 0.9f, FEATHER_MIN = 0.16f, FEATHER_MIN_PX = 6f;

    /** Faces' stand-in box when none was found (the upper middle): no faces to keep whole then. */
    public static final float[] NO_FACES = {0.30f, 0.12f, 0.70f, 0.58f};

    /** True when there are no faces to keep whole (no box, or the stand-in box). */
    public static boolean none(float[] box) {
        return box == null || box.length < 4 || box[0] == NO_FACES[0] && box[1] == NO_FACES[1] && box[2] == NO_FACES[2] && box[3] == NO_FACES[3];
    }

    /** Pure: the share of a guide cell w x h its words take at its left. */
    public static float room(float w, float h) {
        return Math.max(ROW_ROOM_MIN, Math.min(ROW_ROOM_MAX, ROW_WORDS * h / Math.max(1f, w)));
    }

    /**
     * Pure: a picture sw x sh (its own pixels; faces box in shares, or null) in a place w x h whose free part runs from
     * room to end (shares of w), into out (SIZE).
     */
    public static void place(float sw, float sh, float[] box, float w, float h, float room, float end, float[] out) {
        for (int i = 0; i < SIZE; i++) out[i] = 0f;
        sw = Math.max(1f, sw);
        sh = Math.max(1f, sh);
        w = Math.max(1f, w);
        h = Math.max(1f, h);
        float a = sw / sh, kc = Math.max(w / sw, h / sh);
        float keep = Math.min(w / (sw * kc), h / (sh * kc));
        boolean faces = !none(box);
        float fl = faces ? box[0] : 0f, ft = faces ? box[1] : 0f, fr = faces ? box[2] : 1f, fb = faces ? box[3] : 1f;
        float my = ROOM_H * h, mx = Math.min(ROOM_W * w, my);
        float lo = Math.max(0f, Math.min(1f, room)) * w, hi = Math.max(lo + 1f, Math.min(1f, end) * w);
        float bias = (lo + hi) / 2f;
        if (keep >= KEEP) {
            // Covering: no further than it needs, its faces (else its middle) toward the free part, whole where they fit.
            float pw = sw * kc, ph = sh * kc, sx = faces ? (fl + fr) / 2f * pw : pw / 2f;
            float x = clamp(bias - sx, w - pw, 0f);
            if (faces && (fr - fl) * pw + 2 * mx <= w) {
                x = Math.max(x, mx - fl * pw);
                x = Math.min(x, w - mx - fr * pw);
                x = clamp(x, w - pw, 0f);
            }
            float y = faces ? top(ph, h, ft, fb, my) : (h - ph) * 0.38f;
            set(out, COVER, kc, x, y, pw, ph, faces, fl, ft, fr, fb, w, h);
            if (!faces || whole(out, w, h)) return;
            // Its faces can't be whole covering: shown whole instead.
        }
        if (w / h >= a) {
            // Wider than the picture: at the place's height (a sliver cut top and bottom), its faces whole within it.
            float k = h / (sh * SHOW_KEEP);
            if (faces) k = Math.max(Math.min(k, (h - 2 * my) / ((fb - ft) * sh)), h / sh);
            k = Math.min(k, kc);
            float pw = sw * k, ph = sh * k, sx = faces ? (fl + fr) / 2f * pw : pw / 2f;
            float x = pw <= w ? clamp(bias - sx, 0f, w - pw) : clamp(bias - sx, w - pw, 0f);
            float y = faces ? top(ph, h, ft, fb, my) : (h - ph) * 0.45f;
            set(out, SHOW, k, x, y, pw, ph, faces, fl, ft, fr, fb, w, h);
            float f = Math.min(FEATHER * pw, FEATHER_MAX * h), least = Math.min(f, Math.max(FEATHER_MIN_PX, FEATHER_MIN * h));
            out[FEATHER_L] = x > 0.5f ? Math.max(least, Math.min(f, out[FL] - x - 2f)) : 0f;
            out[FEATHER_R] = x + pw < w - 0.5f ? Math.max(least, Math.min(f, x + pw - out[FR] - 2f)) : 0f;
        } else {
            // Narrower than the picture: at the place's width, its faces (else its middle) in the middle across, a
            // little above the middle down.
            float k = w / (sw * SHOW_KEEP);
            if (faces) k = Math.max(Math.min(k, (w - 2 * mx) / ((fr - fl) * sw)), w / sw);
            k = Math.min(k, kc);
            float pw = sw * k, ph = sh * k, sx = faces ? (fl + fr) / 2f * pw : pw / 2f;
            float x = clamp(w / 2f - sx, w - pw, 0f);
            if (faces) {
                x = Math.max(x, mx - fl * pw);
                x = Math.min(x, w - mx - fr * pw);
                x = clamp(x, w - pw, 0f);
            }
            float y = ph <= h ? (h - ph) * 0.4f : clamp(h * 0.4f - (faces ? (ft + fb) / 2f : 0.5f) * ph, h - ph, 0f);
            set(out, SHOW, k, x, y, pw, ph, faces, fl, ft, fr, fb, w, h);
            float f = Math.min(FEATHER * ph, FEATHER_MAX * w), least = Math.min(f, Math.max(FEATHER_MIN_PX, FEATHER_MIN * w));
            out[FEATHER_T] = y > 0.5f ? Math.max(least, Math.min(f, out[FT] - y - 2f)) : 0f;
            out[FEATHER_B] = y + ph < h - 0.5f ? Math.max(least, Math.min(f, y + ph - out[FB] - 2f)) : 0f;
        }
    }

    private static void set(float[] out, float kind, float k, float x, float y, float pw, float ph, boolean faces, float fl, float ft,
                            float fr, float fb, float w, float h) {
        out[KIND] = kind;
        out[K] = k;
        out[X] = x;
        out[Y] = y;
        out[PW] = pw;
        out[PH] = ph;
        // Its faces; with none, the part of it in the place.
        out[FL] = faces ? x + fl * pw : Math.max(0f, x);
        out[FR] = faces ? x + fr * pw : Math.min(w, x + pw);
        out[FT] = faces ? y + ft * ph : Math.max(0f, y);
        out[FB] = faces ? y + fb * ph : Math.min(h, y + ph);
        out[SUBJECT] = faces ? (fl + fr) / 2f * pw : pw / 2f;
        out[FEATHER_L] = out[FEATHER_R] = out[FEATHER_T] = out[FEATHER_B] = 0f;
    }

    /** Where the picture's top goes (place px): its faces whole where they fit, a little above the middle. */
    private static float top(float ph, float h, float ft, float fb, float my) {
        float y = h * 0.46f - (ft + fb) / 2f * ph;
        if ((fb - ft) * ph + 2 * my <= h) {
            // Their top at least my down, their foot at least my up.
            y = Math.max(y, my - ft * ph);
            y = Math.min(y, h - my - fb * ph);
        } else {
            y = my - ft * ph;
        }
        return ph >= h ? clamp(y, h - ph, 0f) : clamp(y, 0f, h - ph);
    }

    /** Pure: whether a layout's faces (or the picture's own extent with none) are whole in w x h. */
    public static boolean whole(float[] out, float w, float h) {
        return out[FL] >= -1f && out[FT] >= -1f && out[FR] <= w + 1f && out[FB] <= h + 1f;
    }

    /** Pure: whether the picture would be enlarged past MAX_UP here (out[K] is place px a picture px). */
    public static boolean tooSmall(float[] out) {
        return out[K] > MAX_UP + 0.01f;
    }

    /** Pure: whether a picture sw x sh is too small for a place w x h (free part room to end): place() then tooSmall(). */
    public static boolean tooSmall(float sw, float sh, float[] box, float w, float h, float room, float end) {
        float[] out = new float[SIZE];
        place(sw, sh, box, w, h, room, end, out);
        return tooSmall(out);
    }

    /**
     * Pure: where a picture pw wide (its subject subject across from its left), made for a guide cell full wide, goes
     * across the cell (from the cell's own left) while only inL..inR of the cell is in sight and its words end at
     * wordsEnd (cell px): its subject at the middle of the free part in sight, inside the part in sight where it fits
     * (else against its right), never past the cell's ends. Continuous in all of them: as the guide scrolls the picture
     * glides along its cell, never jumps and is never cropped again. A picture as wide as its cell stays put.
     */
    public static float slide(float full, float pw, float subject, float inL, float inR, float wordsEnd) {
        if (pw >= full - 0.5f) return 0f;
        float l = Math.max(0f, inL), r = Math.min(full, Math.max(l, inR));
        float freeL = Math.min(r, Math.max(l, wordsEnd));
        float x = (freeL + r) / 2f - subject;
        x = l <= r - pw ? clamp(x, l, r - pw) : r - pw;
        return clamp(x, 0f, full - pw);
    }

    private static float clamp(float v, float lo, float hi) {
        return lo > hi ? (lo + hi) / 2f : Math.max(lo, Math.min(hi, v));
    }
}
