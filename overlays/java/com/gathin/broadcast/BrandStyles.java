package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Whitebrook Road's name in 54 styles, for the cover LogoMark puts over the channel's logo: each calls to mind one of the
 * channel logos the family picked (by colour, case, weight, spacing, boxes and blocks, outlines, gradients, plus signs,
 * slants, underlines, rounded tags and dot grids), always in the family's own words, never a channel's lettering or
 * symbol. Each lays itself out in its own units and is measured first, then scaled to fit the box and centred in it: on
 * its own plate (ground), solid, so the channel's logo under it never shows; or as the marks alone, with a soft shadow
 * under them, for when the channel's logo is hidden another way. SPORTS and the picture's tag (HD, UHD or 4K, with HDR)
 * show when given. Style numbers run from 0 to COUNT - 1 (shown to people as 1 to 54).
 */
public final class BrandStyles {
    public static final int COUNT = 54;

    /** What each is called on the Scoreboard card: what it looks like, never whose logo it calls to mind. */
    private static final String[] NAMES = {
            "Stacked blocks", "Red disc", "Red slant", "Black disc", "Playful colours", "Thin pill",
            "White box caps", "Yellow plus", "Light and silver", "Pink word", "White blocks on red", "Lined blocks",
            "Lime bars", "Spaced blue caps", "Big black initials", "Gold caps", "Nested squares", "Purple to blue",
            "Green initials", "Blue title case", "Plus in a circle", "Striped initial", "Glowing plus", "Tilted purple tag",
            "Red letterbox", "Italic red caps", "Wide block caps", "Orange dot", "Soft lowercase", "Black square",
            "Outline initials", "Violet blend", "Silver caps", "Framed initial", "Bright green", "Rainbow lowercase",
            "Yellow brackets", "Red condensed caps", "Dot grid", "Azure blend", "Colour dots", "Blue underline",
            "Purple heavy", "Ocean blue", "Magenta glow", "Sunset", "Slashed caps", "Full stop",
            "Neon outline", "Blue rounded tag", "Cut-through caps", "Yellow box ending", "Big gradient initial",
            "Three stripes"
    };

    /** Each one's plate: its top and foot colours, and its corners' radius as a share of its height. */
    private static final int[] PLATE = {
            0xFF151D24, 0xFF0B1015, 0xFF1C1C1C, 0xFF0E0E0E, 0xFFF4F4F2, 0xFFE4E4E1, 0xFFF2F2F0, 0xFFE2E2DF,
            0xFF16357A, 0xFF0E2152, 0xFF050505, 0xFF050505, 0xFF0A0A0A, 0xFF000000, 0xFF121212, 0xFF050505,
            0xFF0A0A0A, 0xFF000000, 0xFF161616, 0xFF0A0A0A, 0xFFD4153A, 0xFFAC0C27, 0xFF0F6F6A, 0xFF1C3FA8,
            0xFF0E0E0E, 0xFF050505, 0xFF07173A, 0xFF040C21, 0xFFF4F4F2, 0xFFE0E0DD, 0xFF0D0D0D, 0xFF050505,
            0xFF151515, 0xFF090909, 0xFF170F2E, 0xFF0B0718, 0xFF0A0A0A, 0xFF000000, 0xFFF5F7FA, 0xFFE1E6EE,
            0xFF0E2048, 0xFF07122C, 0xFF0B2342, 0xFF061326, 0xFF0B1B4D, 0xFF040714, 0xFF141414, 0xFF070707,
            0xFF0B0B0B, 0xFF000000, 0xFFF4F4F2, 0xFFE2E2DF, 0xFF0D3F98, 0xFF07286A, 0xFF1C1C1C, 0xFF0F0F0F,
            0xFF13202A, 0xFF0A1218, 0xFFF4F4F2, 0xFFDFDFDC, 0xFF0A0A0A, 0xFF000000, 0xFF2D0B5E, 0xFF12002B,
            0xFF0B0B0B, 0xFF000000, 0xFF1B1B1B, 0xFF0C0C0C, 0xFF0D1110, 0xFF050706, 0xFF141414, 0xFF070707,
            0xFF050505, 0xFF000000, 0xFF0B0B0B, 0xFF000000, 0xFF0F2C78, 0xFF081A4D, 0xFF1A73E8, 0xFF0A2F8C,
            0xFFF4F4F2, 0xFFE2E2DF, 0xFF16222C, 0xFF0D151C, 0xFFF4F2F7, 0xFFE2DFE8, 0xFF00122A, 0xFF000714,
            0xFF1E0030, 0xFF0C0014, 0xFFFF6A1F, 0xFFD8124F, 0xFF161616, 0xFF080808, 0xFF0F0F0F, 0xFF050505,
            0xFF0B0418, 0xFF04010B, 0xFF0F1626, 0xFF070B15, 0xFF0A0A0A, 0xFF000000, 0xFF0A0A0A, 0xFF000000,
            0xFF15152E, 0xFF09091A, 0xFF0F2346, 0xFF07142B
    };
    private static final float[] RADIUS = {
            0.16f, 0.14f, 0.14f, 0.5f, 0.32f, 0.18f, 0.06f, 0.18f, 0.24f, 0.16f, 0.08f, 0.16f, 0.12f, 0.2f, 0.08f, 0.1f,
            0.06f, 0.24f, 0.14f, 0.18f, 0.26f, 0.16f, 0.3f, 0.2f, 0.06f, 0.1f, 0.08f, 0.2f, 0.32f, 0.08f, 0.12f, 0.24f,
            0.12f, 0.08f, 0.22f, 0.26f, 0.04f, 0.1f, 0.2f, 0.26f, 0.2f, 0.2f, 0.18f, 0.22f, 0.3f, 0.24f, 0.08f, 0.2f,
            0.2f, 0.5f, 0.08f, 0.1f, 0.24f, 0.12f
    };
    /**
     * Each one's own width for its height, with SPORTS and an HD tag on its plate (measured from the drawing), for the
     * cover to be sized to it.
     */
    private static final float[] ASPECTS = {
            2.8f, 4.6f, 2.0f, 3.5f, 3.8f, 3.3f, 3.0f, 3.4f, 2.9f, 3.0f, 3.0f, 4.0f, 4.5f, 5.1f, 5.4f, 3.1f,
            4.6f, 2.8f, 3.9f, 3.1f, 2.9f, 4.9f, 3.6f, 4.2f, 3.9f, 4.1f, 3.5f, 3.1f, 2.7f, 4.7f, 4.7f, 2.9f,
            4.6f, 5.1f, 3.1f, 2.8f, 2.9f, 3.2f, 4.6f, 3.2f, 3.3f, 2.6f, 2.9f, 3.2f, 3.1f, 2.8f, 3.3f, 3.3f,
            3.7f, 2.6f, 4.0f, 3.3f, 4.4f, 4.6f
    };

    static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;
    private static final int SOLID = 0, LINED = 1, PLAIN = 2;
    private static final String U1 = "WHITEBROOK", U2 = "ROAD", UN = "WHITEBROOK ROAD", US = "SPORTS",
            L1 = "whitebrook", L2 = "road", LN = "whitebrook road", LS = "sports", T1 = "Whitebrook", T2 = "Road",
            TN = "Whitebrook Road", TS = "Sports", WR = "WR", LWR = "wr";

    // Colours more than one style shares.
    private static final int[] PINK = {0xFFFF4FA3, 0xFFF0368A, 0xFFFF7A45}, PURPLE_BLUE = {0xFFB25CF7, 0xFF6A6CF6, 0xFF2F8BF5},
            GOLD = {0xFFFFE08A, 0xFFE7B53C, 0xFFB98418}, VIOLET = {0xFFB89BFF, 0xFF8B5CF6, 0xFFD27BFF},
            SILVER = {0xFFFFFFFF, 0xFFB9BEC7, 0xFFEDEFF3}, CYAN_GREEN = {0xFF2BD4FF, 0xFF5CF2A6},
            YELLOW_PINK = {0xFFFFD84D, 0xFFFF5CA8}, OCEAN = {0xFF35C8FF, 0xFF1E6BFF}, MAGENTA = {0xFFFF2D95, 0xFFFF8C42},
            RAINBOW = {0xFFFF4D8D, 0xFFB14DFF, 0xFF2EA8FF}, PLAYFUL = {0xFFFF4D4D, 0xFFFF9F1C, 0xFFFFD23F, 0xFF3DDC97,
            0xFF4CC9F0, 0xFF9B5DE5}, DOTS = {0xFFFFC21A, 0xFFFF7A1A, 0xFFFF3D5A, 0xFFB44DFF, 0xFF2F8BFF, 0xFF20C997};

    /** The style's name as the Scoreboard card shows it. */
    public static String name(int style) {
        return NAMES[clamp(style)];
    }

    /** Its natural width for its height on its plate, with SPORTS and an HD tag. */
    public static float aspect(int style) {
        return ASPECTS[clamp(style)];
    }

    public static int clamp(int style) {
        return style < 0 ? 0 : Math.min(style, COUNT - 1);
    }

    private final Context context;
    private final Typeface[] faces = new Typeface[10];
    private final float[] capOf = new float[10];
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG), fp = new Paint(Paint.ANTI_ALIAS_FLAG),
            lp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Rect bounds = new Rect();
    /** Gradients made once and kept while the same one is asked for again. */
    private final Shader[] shaders = new Shader[8];
    private final float[][] shaderAt = new float[8][6];
    private final int[][] shaderCols = new int[8][];

    private Canvas cv;
    /** Measuring only (nothing drawn): where everything would go is gathered in min/max. */
    private boolean dry;
    private boolean ground, sports, light, boxed;
    private String tag = "";
    private int style;
    private float minX, minY, maxX, maxY, scale = 1f;
    /** The words' state: spacing between letters (ems), lean, width and outline. */
    private float spacing, skew, scaleX = 1f, stroke;

    public BrandStyles(Context c) {
        context = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        lp.setStyle(Paint.Style.STROKE);
        lp.setStrokeJoin(Paint.Join.ROUND);
        lp.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Outfit at a weight (200 to 900), each made once. */
    private Typeface face(int weight) {
        int i = Math.max(2, Math.min(9, weight / 100));
        if (faces[i] == null) {
            faces[i] = font(context, i * 100);
            tp.setTypeface(faces[i]);
            tp.setTextSize(100f);
            tp.setLetterSpacing(0f);
            tp.setTextScaleX(1f);
            tp.setTextSkewX(0f);
            tp.getTextBounds("H", 0, 1, bounds);
            capOf[i] = bounds.height() > 0 ? bounds.height() / 100f : 0.7f;
        }
        return faces[i];
    }

    /** Outfit at a weight, as the apps' outfit_extralight to outfit_black make it (Outfit: the one font, its weight set). */
    private static Typeface font(Context c, int weight) {
        return Outfit.at(c, weight);
    }

    /**
     * One style in the box (x, y, w, h): SPORTS after the name or not, the picture's tag ("" for none), on its own solid
     * plate filling the box (ground) or the marks alone.
     */
    public void draw(Canvas canvas, int which, float x, float y, float w, float h, boolean withSports, String pictureTag, boolean onGround) {
        if (canvas == null || w <= 1f || h <= 1f) return;
        set(which, withSports, pictureTag, onGround);
        if (!measure()) return;
        float pad = ground ? h * 0.15f : h * 0.05f, padX = ground ? Math.max(pad, h * 0.2f) : pad;
        float bw = maxX - minX, bh = maxY - minY;
        float k = Math.min((w - 2 * padX) / bw, (h - 2 * pad) / bh);
        if (k <= 0f) return;
        cv = canvas;
        if (ground) plate(x, y, w, h);
        canvas.save();
        canvas.translate(x + w / 2f - (minX + maxX) / 2f * k, y + h / 2f - (minY + maxY) / 2f * k);
        canvas.scale(k, k);
        scale = k;
        dry = false;
        begin();
        layout(style);
        canvas.restore();
        clear();
        cv = null;
    }

    /** Its width for its height as these words lay it out on its plate. */
    public float aspect(int which, boolean withSports, String pictureTag) {
        set(which, withSports, pictureTag, true);
        if (!measure()) return aspect(which);
        float bw = maxX - minX, bh = maxY - minY;
        // As draw() pads it: the words take 70% of the height and the sides keep 20% each.
        return (bw * 0.7f / bh) + 0.4f;
    }

    private void set(int which, boolean withSports, String pictureTag, boolean onGround) {
        style = clamp(which);
        sports = withSports;
        tag = pictureTag == null ? "" : pictureTag.trim();
        ground = onGround;
        light = lum(PLATE[style * 2]) > 0.6f;
    }

    private boolean measure() {
        dry = true;
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = -Float.MAX_VALUE;
        begin();
        layout(style);
        dry = false;
        return maxX > minX && maxY > minY;
    }

    private void begin() {
        spacing = 0f;
        skew = 0f;
        scaleX = 1f;
        stroke = 0f;
        boxed = false;
        tp.setShader(null);
        fp.setShader(null);
        lp.setShader(null);
        shade();
    }

    /** As the marks alone over the picture, a soft shadow under each; on a plate, none. */
    private void shade() {
        if (!dry && !ground) {
            float r = 3f / Math.max(0.05f, scale);
            tp.setShadowLayer(r, 0, r * 0.35f, 0xA0000000);
            fp.setShadowLayer(r, 0, r * 0.35f, 0x80000000);
            lp.setShadowLayer(r, 0, r * 0.35f, 0x80000000);
        } else {
            tp.clearShadowLayer();
            fp.clearShadowLayer();
            lp.clearShadowLayer();
        }
    }

    /** A glow of this colour round what is drawn next (until shade()). */
    private void glow(int colour, float r) {
        if (dry) return;
        tp.setShadowLayer(r, 0, 0, colour);
        fp.setShadowLayer(r, 0, 0, colour);
        lp.setShadowLayer(r, 0, 0, colour);
    }

    private void clear() {
        tp.setShader(null);
        fp.setShader(null);
        lp.setShader(null);
        tp.clearShadowLayer();
        fp.clearShadowLayer();
        lp.clearShadowLayer();
    }

    private void plate(float x, float y, float w, float h) {
        int top = PLATE[style * 2], foot = PLATE[style * 2 + 1];
        float r = Math.min(h, w) * RADIUS[style];
        fp.clearShadowLayer();
        if (top == foot) {
            fp.setShader(null);
            fp.setColor(top);
        } else {
            fp.setColor(WHITE);
            fp.setShader(lin(7, x, y, x, y + h, top, foot));
        }
        cv.drawRoundRect(x, y, x + w, y + h, r, r, fp);
        fp.setShader(null);
    }

    // ---- What everything is drawn with (in the style's own units) ----

    private void bound(float l, float t, float r, float b) {
        if (!dry) return;
        minX = Math.min(minX, Math.min(l, r));
        maxX = Math.max(maxX, Math.max(l, r));
        minY = Math.min(minY, Math.min(t, b));
        maxY = Math.max(maxY, Math.max(t, b));
    }

    /** On a light plate's colours drawn without it: its dark ink turns white, so it shows on the picture. */
    private int ink(int c) {
        if (!dry && !ground && light && !boxed && lum(c) < 0.2f) return (c & 0xFF000000) | 0xFFFFFF;
        return c;
    }

    public static float lum(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }

    private void type(float cap, int wt) {
        Typeface f = face(wt);
        int i = Math.max(2, Math.min(9, wt / 100));
        tp.setTypeface(f);
        tp.setTextSize(cap / capOf[i]);
        tp.setLetterSpacing(spacing);
        tp.setTextSkewX(skew);
        tp.setTextScaleX(scaleX);
    }

    /** How wide s is with its capitals cap high in weight wt (with the words' state now). */
    private float tw(String s, float cap, int wt) {
        if (s == null || s.isEmpty()) return 0f;
        type(cap, wt);
        float w = tp.measureText(s);
        if (spacing > 0f) w -= spacing * tp.getTextSize() * scaleX;
        return w;
    }

    /** s from x on the baseline base, its capitals cap high, weight wt, in col (or the shader): the x after it. */
    private float text(String s, float x, float base, float cap, int wt, int col, Shader sh) {
        if (s == null || s.isEmpty()) return x;
        float w = tw(s, cap, wt);
        float asc = cap, desc = 0f;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ("bdfhklt".indexOf(ch) >= 0) asc = Math.max(asc, cap * 1.08f);
            if ("gjpqy".indexOf(ch) >= 0) desc = cap * 0.33f;
        }
        float lean = skew < 0f ? -skew * asc : 0f;
        bound(x - stroke, base - asc - stroke, x + w + lean + stroke, base + desc + stroke);
        if (!dry) {
            tp.setColor(sh != null ? WHITE : ink(col));
            tp.setShader(sh);
            tp.setStyle(stroke > 0f ? Paint.Style.STROKE : Paint.Style.FILL);
            tp.setStrokeWidth(stroke);
            tp.setStrokeJoin(Paint.Join.ROUND);
            cv.drawText(s, x, base, tp);
            tp.setShader(null);
            tp.setStyle(Paint.Style.FILL);
        }
        return x + w;
    }

    /** s centred on cx. */
    private float textC(String s, float cx, float base, float cap, int wt, int col, Shader sh) {
        return text(s, cx - tw(s, cap, wt) / 2f, base, cap, wt, col, sh);
    }

    /** s with each letter in the next of cols, every other one a little higher: the x after it. */
    private float letters(String s, float x, float base, float cap, int wt, int[] cols, float bounce) {
        type(cap, wt);
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            type(cap, wt);
            float a = tp.measureText(s, i, i + 1);
            if (ch != ' ') {
                float b = base - ((n % 2 == 0) ? bounce : 0f);
                bound(x, b - cap, x + a, b);
                if (!dry) {
                    tp.setColor(ink(cols[n % cols.length]));
                    tp.setStyle(Paint.Style.FILL);
                    cv.drawText(s, i, i + 1, x, b, tp);
                }
                n++;
            }
            x += a;
        }
        return x;
    }

    /** A linear gradient from (x0, y0) to (x1, y1) through cols, made once for this slot while it stays the same. */
    private Shader lin(int slot, float x0, float y0, float x1, float y1, int... cols) {
        if (dry) return null;
        float[] at = shaderAt[slot];
        if (shaders[slot] != null && shaderCols[slot] == cols && at[0] == x0 && at[1] == y0 && at[2] == x1 && at[3] == y1
                && at[4] == style) {
            return shaders[slot];
        }
        at[0] = x0;
        at[1] = y0;
        at[2] = x1;
        at[3] = y1;
        at[4] = style;
        shaderCols[slot] = cols;
        shaders[slot] = new LinearGradient(x0, y0, x1, y1, cols, null, Shader.TileMode.CLAMP);
        return shaders[slot];
    }

    /** The two-colour form, its colours kept in a pair made once per slot. */
    private final int[][] pairs = new int[8][2];

    private Shader lin(int slot, float x0, float y0, float x1, float y1, int a, int b) {
        if (dry) return null;
        int[] p = pairs[slot];
        if (p[0] != a || p[1] != b) {
            pairs[slot] = new int[]{a, b};
            shaders[slot] = null;
        }
        return lin(slot, x0, y0, x1, y1, pairs[slot]);
    }

    private void rect(float l, float t, float r, float b, int col) {
        bound(l, t, r, b);
        if (dry) return;
        fp.setShader(null);
        fp.setColor(ink(col));
        cv.drawRect(l, t, r, b, fp);
    }

    private void rrect(float l, float t, float r, float b, float rad, int col, Shader sh) {
        bound(l, t, r, b);
        if (dry) return;
        fp.setColor(sh != null ? WHITE : ink(col));
        fp.setShader(sh);
        cv.drawRoundRect(l, t, r, b, rad, rad, fp);
        fp.setShader(null);
    }

    private void rline(float l, float t, float r, float b, float rad, float wd, int col) {
        bound(l - wd / 2f, t - wd / 2f, r + wd / 2f, b + wd / 2f);
        if (dry) return;
        lp.setColor(ink(col));
        lp.setStrokeWidth(wd);
        cv.drawRoundRect(l, t, r, b, rad, rad, lp);
    }

    private void disc(float cx, float cy, float rad, int col, Shader sh) {
        bound(cx - rad, cy - rad, cx + rad, cy + rad);
        if (dry) return;
        fp.setColor(sh != null ? WHITE : ink(col));
        fp.setShader(sh);
        cv.drawCircle(cx, cy, rad, fp);
        fp.setShader(null);
    }

    /** A plus sign centred on (cx, cy), half arms long, its bars wd thick. */
    private void plus(float cx, float cy, float half, float wd, int col) {
        rect(cx - half, cy - wd / 2f, cx + half, cy + wd / 2f, col);
        rect(cx - wd / 2f, cy - half, cx + wd / 2f, cy + half, col);
    }

    /** A four-cornered shape through the four points. */
    private void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int col) {
        bound(Math.min(Math.min(x1, x2), Math.min(x3, x4)), Math.min(Math.min(y1, y2), Math.min(y3, y4)),
                Math.max(Math.max(x1, x2), Math.max(x3, x4)), Math.max(Math.max(y1, y2), Math.max(y3, y4)));
        if (dry) return;
        path.rewind();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x4, y4);
        path.close();
        fp.setShader(null);
        fp.setColor(ink(col));
        cv.drawPath(path, fp);
    }

    /**
     * The picture's tag after x (a gap first), its middle at mid, sized to capitals cap high beside it: a box filled in
     * col with the letters in cut (SOLID), a box lined in col (LINED), or the letters alone (PLAIN). The x after it.
     */
    private float tag(float x, float mid, float cap, int kind, int col, int cut) {
        if (tag.isEmpty()) return x;
        float sv = spacing, sk = skew, sx = scaleX, st = stroke;
        spacing = 0.04f;
        skew = 0f;
        scaleX = 1f;
        stroke = 0f;
        float c = cap * 0.6f, l = x + cap * 0.4f, w = tw(tag, c, 700);
        float end;
        if (kind == PLAIN) {
            end = text(tag, l, mid + c / 2f, c, 700, col, null);
        } else {
            float padX = c * 0.42f, half = c * 0.9f, r = l + w + 2 * padX, rad = half * 0.36f;
            boolean was = boxed;
            if (kind == SOLID) {
                // The box and its letters keep their colours together over the picture.
                boxed = true;
                rrect(l, mid - half, r, mid + half, rad, col, null);
                text(tag, l + padX, mid + c / 2f, c, 700, cut, null);
            } else {
                rline(l, mid - half, r, mid + half, rad, c * 0.16f, col);
                text(tag, l + padX, mid + c / 2f, c, 700, col, null);
            }
            boxed = was;
            end = r;
        }
        spacing = sv;
        skew = sk;
        scaleX = sx;
        stroke = st;
        return end;
    }

    // ---- The styles: three lines are 30 high, their baselines at 30, 72 and 114 ----

    private static final float CAP = 30f, B1 = 30f, B2 = 72f, B3 = 114f;

    /** The height of the words: three lines with SPORTS, two without. */
    private float tall() {
        return sports ? B3 : B2;
    }

    /** The last line's baseline. */
    private float last() {
        return sports ? B3 : B2;
    }

    private void layout(int s) {
        switch (s) {
            case 0: stackedBlocks(); break;
            case 1: redDisc(); break;
            case 2: redSlant(); break;
            case 3: blackDisc(); break;
            case 4: playful(); break;
            case 5: thinPill(); break;
            case 6: whiteBoxCaps(); break;
            case 7: yellowPlus(); break;
            case 8: lightSilver(); break;
            case 9: pinkWord(); break;
            case 10: whiteBlocksOnRed(); break;
            case 11: linedBlocks(); break;
            case 12: limeBars(); break;
            case 13: spacedBlueCaps(); break;
            case 14: bigBlackInitials(); break;
            case 15: goldCaps(); break;
            case 16: nestedSquares(); break;
            case 17: purpleToBlue(); break;
            case 18: greenInitials(); break;
            case 19: blueTitleCase(); break;
            case 20: plusInCircle(); break;
            case 21: stripedInitial(); break;
            case 22: glowingPlus(); break;
            case 23: tiltedPurpleTag(); break;
            case 24: redLetterbox(); break;
            case 25: italicRedCaps(); break;
            case 26: wideBlockCaps(); break;
            case 27: orangeDot(); break;
            case 28: softLowercase(); break;
            case 29: blackSquare(); break;
            case 30: outlineInitials(); break;
            case 31: violetBlend(); break;
            case 32: silverCaps(); break;
            case 33: framedInitial(); break;
            case 34: brightGreen(); break;
            case 35: rainbowLowercase(); break;
            case 36: yellowBrackets(); break;
            case 37: redCondensedCaps(); break;
            case 38: dotGrid(); break;
            case 39: azureBlend(); break;
            case 40: colourDots(); break;
            case 41: blueUnderline(); break;
            case 42: purpleHeavy(); break;
            case 43: oceanBlue(); break;
            case 44: magentaGlow(); break;
            case 45: sunset(); break;
            case 46: slashedCaps(); break;
            case 47: fullStop(); break;
            case 48: neonOutline(); break;
            case 49: blueRoundedTag(); break;
            case 50: cutThroughCaps(); break;
            case 51: yellowBoxEnding(); break;
            case 52: bigGradientInitial(); break;
            default: threeStripes(); break;
        }
    }

    /** 1. Four blocks of teal and white stepped together, the name in three white lines beside them. */
    private void stackedBlocks() {
        float s = tall(), u = s / 4f;
        rect(0, 0, u * 2.05f, u * 1.55f, WHITE);
        rect(u * 2.3f, 0, u * 3.2f, u * 2.2f, 0xFF9BE3DB);
        rect(0, u * 1.8f, u * 1.25f, s, 0xFF27B3A6);
        rect(u * 1.5f, u * 2.45f, u * 3.2f, s, 0xFF0F7F75);
        float x = u * 3.2f + 16f;
        text(U1, x, B1, CAP, 800, WHITE, null);
        float e = text(U2, x, B2, CAP, 800, WHITE, null);
        if (sports) e = text(US, x, B3, CAP, 800, 0xFF6AD9CC, null);
        tag(e, last() - CAP / 2f, CAP, LINED, WHITE, 0);
    }

    /** 2. A red disc with W in it, the name in two lines, SPORTS in red, the tag in white with red letters. */
    private void redDisc() {
        float d = B2;
        disc(d / 2f, d / 2f, d / 2f, 0xFFE2231A, null);
        boxed = true;
        textC("W", d / 2f, d / 2f + 21f, 42f, 900, WHITE, null);
        boxed = false;
        float x = d + 16f;
        float e1 = text(U1, x, B1, CAP, 600, WHITE, null);
        float e = text(U2, x, B2, CAP, 600, WHITE, null);
        if (sports) e = text(US, e + 11f, B2, CAP, 600, 0xFFFF5146, null);
        if (sports) tag(e1, B1 - CAP / 2f, CAP, SOLID, WHITE, 0xFFE2231A);
        else tag(e, B2 - CAP / 2f, CAP, SOLID, WHITE, 0xFFE2231A);
    }

    /** 3. A red slanted bar, the name in grey lowercase lines, sports in red. */
    private void redSlant() {
        float s = tall();
        quad(0, s, 22f, s, 50f, 0, 28f, 0, 0xFFE1251B);
        float x = 62f;
        text(L1, x, B1, CAP, 800, 0xFF6E6E73, null);
        float e = text(L2, x, B2, CAP, 800, 0xFF6E6E73, null);
        if (sports) e = text(LS, x, B3, CAP, 800, 0xFFE1251B, null);
        tag(e, last() - CAP * 0.4f, CAP, PLAIN, 0xFFE1251B, 0);
    }

    /** 4. A black disc with wr in white lowercase, the name in black lowercase beside it on a light plate. */
    private void blackDisc() {
        float d = B2;
        boxed = true;
        disc(d / 2f, d / 2f, d / 2f, BLACK, null);
        textC(LWR, d / 2f, d / 2f + 11f, 30f, 700, WHITE, null);
        boxed = false;
        float x = d + 16f;
        text(L1, x, B1, CAP, 700, BLACK, null);
        float e = text(L2, x, B2, CAP, 700, BLACK, null);
        if (sports) e = text(LS, e + 9f, B2, CAP, 400, BLACK, null);
        tag(e, B2 - CAP * 0.4f, CAP, LINED, BLACK, 0);
    }

    /** 5. Every letter in a bright colour, bouncing, on a navy plate; SPORTS in white, the tag in yellow. */
    private void playful() {
        letters(U1, 0f, B1, CAP, 900, PLAYFUL, 3f);
        float e = letters(U2, 0f, B2, CAP, 900, PLAYFUL, 3f);
        if (sports) e = text(US, e + 12f, B2, CAP, 900, WHITE, null);
        tag(e, B2 - CAP / 2f, CAP, SOLID, 0xFFFFD23F, 0xFF16357A);
    }

    /** 6. The name in plain white, SPORTS in a thin white pill, on black. */
    private void thinPill() {
        text(U1, 0f, B1, CAP, 500, WHITE, null);
        float e = text(U2, 0f, B2, CAP, 500, WHITE, null);
        if (sports) {
            float l = e + 12f, w = tw(US, CAP * 0.8f, 600), pad = 12f;
            rline(l, B2 - CAP - 6f, l + w + 2 * pad, B2 + 6f, (CAP + 12f) / 2f, 2.5f, WHITE);
            text(US, l + pad, B2 - CAP * 0.1f, CAP * 0.8f, 600, WHITE, null);
            e = l + w + 2 * pad;
        }
        tag(e, B2 - CAP / 2f, CAP, PLAIN, WHITE, 0);
    }

    /** 7. WHITEBROOK in black heavy capitals in a white box, ROAD SPORTS in white under it. */
    private void whiteBoxCaps() {
        scaleX = 0.92f;
        float w = tw(U1, CAP, 900), pad = 9f;
        rect(0, B1 - CAP - pad, w + 2 * pad, B1 + pad, WHITE);
        boxed = true;
        text(U1, pad, B1, CAP, 900, BLACK, null);
        boxed = false;
        float e = text(U2, 0f, B2 + 8f, CAP, 900, WHITE, null);
        if (sports) e = text(US, e + 12f, B2 + 8f, CAP, 900, WHITE, null);
        scaleX = 1f;
        tag(e, B2 + 8f - CAP / 2f, CAP, LINED, WHITE, 0);
    }

    /** 8. White lowercase, sports lighter, a yellow tile with a black plus at the end. */
    private void yellowPlus() {
        float e1 = text(L1, 0f, B1, CAP, 800, WHITE, null);
        float e = text(L2, 0f, B2, CAP, 800, WHITE, null);
        if (sports) e = text(LS, e + 10f, B2, CAP, 400, WHITE, null);
        e = tag(e, B2 - CAP * 0.4f, CAP, LINED, WHITE, 0);
        float l = Math.max(e1, e) + 16f, side = B2;
        rrect(l, 0f - 4f, l + side, B2 + 4f, 10f, 0xFFFFD400, null);
        boxed = true;
        plus(l + side / 2f, B2 / 2f, side * 0.26f, side * 0.1f, BLACK);
        boxed = false;
    }

    /** 9. Light silver lowercase, sports with a plus, on black. */
    private void lightSilver() {
        text(sports ? LN : L1, 0f, B1, CAP * 0.9f, 300, 0xFFE5E5EA, null);
        float e = text(sports ? LS : L2, 0f, B2, CAP * 0.9f, 500, WHITE, null);
        if (sports) {
            plus(e + 13f, B2 - CAP * 0.36f, 8f, 3f, WHITE);
            e += 21f;
        }
        tag(e, B2 - CAP * 0.4f, CAP, LINED, 0xFFB0B3BA, 0);
    }

    /** 10. The name in white capitals, then sports (or road) large in pink lowercase. */
    private void pinkWord() {
        spacing = 0.06f;
        text(sports ? UN : U1, 0f, 22f, 22f, 800, WHITE, null);
        spacing = 0f;
        String big = sports ? LS : L2;
        float w = tw(big, 40f, 800);
        float e = text(big, 0f, 76f, 40f, 800, 0, lin(0, 0, 0, w, 0, PINK));
        tag(e, 76f - 15f, 34f, SOLID, 0xFFFF4FA3, 0xFF0A0A0A);
    }

    /** 11. WHITEBROOK and ROAD each in a white block with black letters, SPORTS in white, on red. */
    private void whiteBlocksOnRed() {
        float pad = 7f, c = 26f;
        float w1 = tw(U1, c, 800), w2 = tw(U2, c, 800);
        rect(0, 0, w1 + 2 * pad, c + 2 * pad, WHITE);
        boxed = true;
        text(U1, pad, c + pad, c, 800, BLACK, null);
        boxed = false;
        float t2 = c + 2 * pad + 8f;
        rect(0, t2, w2 + 2 * pad, t2 + c + 2 * pad, WHITE);
        boxed = true;
        text(U2, pad, t2 + c + pad, c, 800, BLACK, null);
        boxed = false;
        float e = w2 + 2 * pad;
        if (sports) e = text(US, e + 12f, t2 + c + pad, c, 800, WHITE, null);
        tag(e, t2 + c / 2f + pad, c, LINED, WHITE, 0);
    }

    /** 12. WHITEBROOK and ROAD in white-lined blocks side by side, SPORTS spaced out under them, on teal to blue. */
    private void linedBlocks() {
        float pad = 6f, c = 24f, gap = 7f, bh = c + 2 * pad;
        float w1 = tw(U1, c, 700), w2 = tw(U2, c, 700);
        rline(0, 0, w1 + 2 * pad, bh, 2f, 2.5f, WHITE);
        text(U1, pad, c + pad, c, 700, WHITE, null);
        // Side by side over SPORTS; one over the other for the name alone.
        float l2 = sports ? w1 + 2 * pad + gap : 0f, t2 = sports ? 0f : bh + gap;
        rline(l2, t2, l2 + w2 + 2 * pad, t2 + bh, 2f, 2.5f, WHITE);
        float e2 = text(U2, l2 + pad, t2 + c + pad, c, 700, WHITE, null) + pad;
        if (sports) {
            float base = bh + 12f + 20f;
            spacing = 0.32f;
            float e = text(US, 0f, base, 20f, 600, WHITE, null);
            spacing = 0f;
            tag(e + 10f, base - 10f, 20f, PLAIN, WHITE, 0);
        } else {
            tag(e2, t2 + bh / 2f, c, PLAIN, WHITE, 0);
        }
    }

    /** 13. Three slanted lime bars, WHITEBROOK in lime, ROAD SPORTS in white. */
    private void limeBars() {
        int lime = 0xFFB5F23D;
        for (int i = 0; i < 3; i++) {
            float l = i * 15f;
            quad(l, B2, l + 9f, B2, l + 19f, 0, l + 10f, 0, lime);
        }
        float x = 3 * 15f + 16f;
        text(U1, x, B1, CAP, 900, lime, null);
        float e = text(U2, x, B2, CAP, 900, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 900, WHITE, null);
        tag(e, B2 - CAP / 2f, CAP, SOLID, lime, 0xFF0A0A0A);
    }

    /** 14. The name spaced out in light blue capitals, SPORTS spaced out in white, centred, on navy. */
    private void spacedBlueCaps() {
        spacing = 0.3f;
        String a = sports ? UN : U1, b = sports ? US : U2;
        float w1 = tw(a, 20f, 300), w2 = tw(b, 20f, 800);
        text(a, 0f, 20f, 20f, 300, 0xFF6FB6FF, null);
        float e = text(b, (w1 - w2) / 2f, 56f, 20f, 800, WHITE, null);
        spacing = 0f;
        tag(e + 8f, 46f, 20f, LINED, 0xFF6FB6FF, 0);
    }

    /** 15. WR large in black, a thin rule, the name and SPORTS small beside it, on a light plate. */
    private void bigBlackInitials() {
        float e0 = text(WR, 0f, 64f, 64f, 900, BLACK, null);
        rect(e0 + 12f, 2f, e0 + 15f, 64f, BLACK);
        float x = e0 + 27f;
        text(sports ? UN : U1, x, 26f, 20f, 700, BLACK, null);
        float e = text(sports ? US : U2, x, 62f, 20f, 400, BLACK, null);
        tag(e, 52f, 20f, LINED, BLACK, 0);
    }

    /** 16. The name in gold capitals, a gold rule under them, SPORTS spaced out in white. */
    private void goldCaps() {
        float w1 = tw(U1, CAP, 900);
        text(U1, 0f, B1, CAP, 900, 0, lin(0, 0, B1 - CAP, 0, B1, GOLD));
        float e = text(U2, 0f, B2, CAP, 900, 0, lin(1, 0, B2 - CAP, 0, B2, GOLD));
        rect(0f, B2 + 7f, w1, B2 + 10f, 0xFFE7B53C);
        if (sports) {
            spacing = 0.2f;
            e = text(US, e + 14f, B2, CAP * 0.62f, 300, WHITE, null) + 8f;
            spacing = 0f;
        }
        tag(e, B2 - CAP * 0.31f, CAP * 0.7f, LINED, 0xFFE7B53C, 0);
    }

    /** 17. Two squares, one lined and one solid with W in it, the name beside them, SPORTS in yellow. */
    private void nestedSquares() {
        float s = B2;
        rline(0, 0, s * 0.82f, s * 0.82f, 3f, 4f, WHITE);
        rect(s * 0.3f, s * 0.3f, s, s, WHITE);
        boxed = true;
        textC("W", s * 0.65f, s * 0.65f + 13f, 26f, 900, BLACK, null);
        boxed = false;
        float x = s + 16f;
        text(U1, x, B1, CAP, 800, WHITE, null);
        float e = text(U2, x, B2, CAP, 800, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 800, 0xFFFFD100, null);
        tag(e, B2 - CAP / 2f, CAP, SOLID, 0xFFFFD100, BLACK);
    }

    /** 18. Heavy lowercase in a purple to blue blend. */
    private void purpleToBlue() {
        float w = Math.max(tw(L1, 34f, 900), tw(LS, 34f, 900) + tw(L2, 34f, 900) + 12f);
        text(L1, 0f, 34f, 34f, 900, 0, lin(0, 0, 0, w, 0, PURPLE_BLUE));
        float e = text(L2, 0f, 82f, 34f, 900, 0, lin(1, 0, 0, w, 0, PURPLE_BLUE));
        if (sports) e = text(LS, e + 12f, 82f, 34f, 900, 0, lin(2, 0, 0, w, 0, PURPLE_BLUE));
        tag(e, 82f - 13f, 30f, LINED, WHITE, 0);
    }

    /** 19. WR large in green, leaning, the name in three small lines beside it. */
    private void greenInitials() {
        int green = 0xFF19C25A;
        skew = -0.12f;
        float e0 = text(WR, 0f, 66f, 66f, 900, green, null);
        skew = 0f;
        float x = e0 + 18f;
        text(U1, x, 18f, 17f, 700, WHITE, null);
        float e = text(U2, x, 42f, 17f, 700, WHITE, null);
        if (sports) e = text(US, x, 66f, 17f, 700, green, null);
        tag(e, (sports ? 66f : 42f) - 8.5f, 17f, PLAIN, green, 0);
    }

    /** 20. Title case in blue on a light plate, Sports lighter under it. */
    private void blueTitleCase() {
        int blue = 0xFF0A5EC2;
        text(sports ? TN : T1, 0f, 28f, 28f, 600, blue, null);
        float e = text(sports ? TS : T2, 0f, 70f, 28f, 400, 0xFF3D86DA, null);
        tag(e, 70f - 14f, 28f, LINED, blue, 0);
    }

    /** 21. White lowercase on navy, sports and a white plus in a blue circle under it. */
    private void plusInCircle() {
        text(sports ? LN : L1, 0f, 28f, 28f, 700, WHITE, null);
        float e = text(sports ? LS : L2, 0f, 70f, 28f, 400, WHITE, null);
        float r = 16f, cy = 70f - 11f, cx = e + 12f + r;
        disc(cx, cy, r, 0xFF2175FF, null);
        boxed = true;
        plus(cx, cy, 8f, 3.4f, WHITE);
        boxed = false;
        tag(cx + r, cy, 26f, LINED, WHITE, 0);
    }

    /** A W cut across by stripes, from x on the baseline base. */
    private void stripedW(float x, float base, float cap, int col) {
        float w = tw("W", cap, 900);
        if (!dry) {
            cv.save();
            float band = cap / 9f;
            for (int i = 1; i < 9; i += 2) {
                float t = base - cap + i * band;
                cv.clipOutRect(x - 2f, t, x + w + 2f, t + band * 0.55f);
            }
        }
        text("W", x, base, cap, 900, col, null);
        if (!dry) cv.restore();
    }

    /** 22. A big blue W cut by stripes, the name in white beside it. */
    private void stripedInitial() {
        stripedW(0f, B2, B2, 0xFF2F80ED);
        float x = tw("W", B2, 900) + 16f;
        text(U1, x, B1, CAP, 800, WHITE, null);
        float e = text(U2, x, B2, CAP, 800, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 800, 0xFF7DB4FF, null);
        tag(e, B2 - CAP / 2f, CAP, LINED, WHITE, 0);
    }

    /** 23. Light title case on deep navy, a big plus glowing in cyan. */
    private void glowingPlus() {
        float e1 = text(sports ? TN : T1, 0f, 28f, 28f, 300, WHITE, null);
        float e2 = tag(text(sports ? TS : T2, 0f, 70f, 28f, 600, WHITE, null), 56f, 28f, LINED, WHITE, 0);
        float half = 22f, cx = Math.max(e1, e2) + half + 14f, cy = 42f;
        glow(0xFF60D9FA, 9f);
        plus(cx, cy, half, 4f, WHITE);
        shade();
    }

    /** 24. WR in a purple tile tilted back, the name beside it, SPORTS in lilac. */
    private void tiltedPurpleTag() {
        float s = B2, cx = s / 2f, cy = s / 2f;
        bound(-6f, -6f, s + 6f, s + 6f);
        if (!dry) {
            cv.save();
            cv.rotate(-10f, cx, cy);
        }
        rrect(4f, 8f, s - 4f, s - 8f, 12f, 0xFF7B2CBF, null);
        boxed = true;
        textC(WR, cx, cy + 13f, 26f, 900, WHITE, null);
        boxed = false;
        if (!dry) cv.restore();
        float x = s + 18f;
        text(U1, x, B1, CAP, 800, WHITE, null);
        float e = text(U2, x, B2, CAP, 800, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 800, 0xFFC9A3FF, null);
        tag(e, B2 - CAP / 2f, CAP, SOLID, 0xFF7B2CBF, WHITE);
    }

    /** 25. The name in white across a red bar, SPORTS spaced wide under it, on black. */
    private void redLetterbox() {
        int red = 0xFFD7261E;
        spacing = 0.12f;
        String a = sports ? UN : U1, b = sports ? US : U2;
        float c = 22f, w = tw(a, c, 800), padX = 14f;
        rect(0, 0, w + 2 * padX, c + 18f, red);
        rect(0, c + 21f, w + 2 * padX, c + 23f, red);
        boxed = true;
        text(a, padX, c + 9f, c, 800, WHITE, null);
        boxed = false;
        spacing = 0.45f;
        float e = text(b, padX, c + 23f + 30f, 18f, 300, WHITE, null);
        spacing = 0f;
        tag(e + 10f, c + 23f + 21f, 18f, LINED, WHITE, 0);
    }

    /** 26. Heavy leaning capitals, WHITEBROOK in red, ROAD SPORTS in black, a yellow plus, on a light plate. */
    private void italicRedCaps() {
        skew = -0.22f;
        text(U1, 0f, B1, CAP, 900, 0xFFD50A0A, null);
        float e = text(U2, 0f, B2, CAP, 900, BLACK, null);
        if (sports) {
            e = text(US, e + 12f, B2, CAP, 900, BLACK, null);
            skew = 0f;
            plus(e + 15f, B2 - CAP / 2f, 10f, 5f, 0xFFFFB800);
            e += 25f;
        }
        skew = 0f;
        tag(e, B2 - CAP / 2f, CAP, SOLID, BLACK, WHITE);
    }

    /** 27. Wide heavy white capitals on royal blue, the tag solid white. */
    private void wideBlockCaps() {
        scaleX = 1.14f;
        text(U1, 0f, B1, CAP, 900, WHITE, null);
        float e = text(U2, 0f, B2, 24f, 900, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, 24f, 900, WHITE, null);
        scaleX = 1f;
        tag(e, B2 - 12f, 26f, SOLID, WHITE, 0xFF0A3D91);
    }

    /** 28. Heavy white lowercase with an orange dot, sports in orange. */
    private void orangeDot() {
        int orange = 0xFFFF6A13;
        float e1 = text(sports ? LN : L1, 0f, 30f, 28f, 900, WHITE, null);
        if (sports) disc(e1 + 9f, 30f - 4.5f, 4.5f, orange, null);
        float e = text(sports ? LS : L2, 0f, 72f, 28f, 700, orange, null);
        if (!sports) {
            disc(e + 9f, 72f - 4.5f, 4.5f, orange, null);
            e += 14f;
        }
        tag(e, 72f - 11f, 28f, LINED, orange, 0);
    }

    /** 29. Soft lowercase in white, a coral dash under road, sports in coral. */
    private void softLowercase() {
        int coral = 0xFFFF7F6E;
        text(L1, 0f, B1, CAP, 500, WHITE, null);
        float e2 = text(L2, 0f, B2, CAP, 500, WHITE, null);
        rrect(2f, B2 + 8f, e2 - 2f, B2 + 13f, 2.5f, coral, null);
        float e = e2;
        if (sports) e = text(LS, e + 11f, B2, CAP, 500, coral, null);
        tag(e, B2 - CAP * 0.36f, CAP, LINED, coral, 0);
    }

    /** 30. WR in white in a black square, the name in black heavy capitals beside it, on a light plate. */
    private void blackSquare() {
        float s = B2;
        boxed = true;
        rect(0, 0, s, s, BLACK);
        textC(WR, s / 2f, s / 2f + 14f, 28f, 900, WHITE, null);
        boxed = false;
        float x = s + 16f;
        text(U1, x, B1, CAP, 800, BLACK, null);
        float e = text(U2, x, B2, CAP, 800, BLACK, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 800, BLACK, null);
        tag(e, B2 - CAP / 2f, CAP, SOLID, BLACK, WHITE);
    }

    /** 31. WR in outline inside a lined box, the name in white beside it. */
    private void outlineInitials() {
        float s = B2;
        rline(0, 0, s * 1.3f, s, 4f, 2.5f, WHITE);
        stroke = 2.2f;
        textC(WR, s * 0.65f, s / 2f + 17f, 34f, 900, WHITE, null);
        stroke = 0f;
        float x = s * 1.3f + 16f;
        text(U1, x, B1, CAP, 700, WHITE, null);
        float e = text(U2, x, B2, CAP, 700, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 300, WHITE, null);
        tag(e, B2 - CAP / 2f, CAP, LINED, WHITE, 0);
    }

    /** 32. WHITEBROOK in heavy white capitals, road and sports in violet lowercase, on deep violet. */
    private void violetBlend() {
        float w1 = text(U1, 0f, B1, CAP, 900, WHITE, null);
        float e = text(L2, 0f, B2, CAP * 1.05f, 500, 0, lin(0, 0, 0, w1, 0, VIOLET));
        if (sports) e = text(LS, e + 11f, B2, CAP * 1.05f, 500, 0, lin(1, 0, 0, w1, 0, VIOLET));
        tag(e, B2 - CAP * 0.4f, CAP, LINED, 0xFFC9B5FF, 0);
    }

    /** 33. The name in heavy silver capitals, SPORTS spaced out, centred, on black. */
    private void silverCaps() {
        float c = 26f;
        String a = sports ? UN : U1, b = sports ? US : U2;
        float w = tw(a, c, 900);
        text(a, 0f, c, c, 900, 0, lin(0, 0, 0, 0, c, SILVER));
        spacing = 0.5f;
        float ws = tw(b, 16f, 600), l = (w - ws) / 2f;
        float e = text(b, l, c + 34f, 16f, 600, 0xFFD5D9E0, null);
        spacing = 0f;
        tag(e + 10f, c + 26f, 16f, LINED, 0xFFD5D9E0, 0);
    }

    /** 34. W framed in a white square, the name in heavy white capitals, SPORTS spaced in amber. */
    private void framedInitial() {
        float s = 62f;
        rline(0, 0, s, s, 1f, 4f, WHITE);
        textC("W", s / 2f, s / 2f + 18f, 36f, 900, WHITE, null);
        float x = s + 14f;
        text(sports ? UN : U1, x, 28f, 26f, 900, WHITE, null);
        spacing = 0.42f;
        float e = text(sports ? US : U2, x, 62f, 18f, 700, 0xFFF2B33D, null);
        spacing = 0f;
        tag(e + 10f, 62f - 9f, 18f, PLAIN, 0xFFF2B33D, 0);
    }

    /** 35. The name in bright green lowercase, sports in white, the tag solid green. */
    private void brightGreen() {
        int green = 0xFF1CE783;
        text(sports ? LN : L1, 0f, 30f, 30f, 800, green, null);
        float e = text(sports ? LS : L2, 0f, 72f, 30f, 800, sports ? WHITE : green, null);
        tag(e, 72f - 12f, 30f, SOLID, green, 0xFF0B0C0F);
    }

    /** 36. Lowercase in blends: whitebrook cyan to green, road and sports yellow to pink. */
    private void rainbowLowercase() {
        float w1 = tw(L1, 32f, 700);
        text(L1, 0f, 32f, 32f, 700, 0, lin(0, 0, 0, w1, 0, CYAN_GREEN));
        float w2 = tw(L2, 32f, 700) + (sports ? tw(LS, 32f, 700) + 12f : 0f);
        float e = text(L2, 0f, 78f, 32f, 700, 0, lin(1, 0, 0, w2, 0, YELLOW_PINK));
        if (sports) e = text(LS, e + 12f, 78f, 32f, 700, 0, lin(2, 0, 0, w2, 0, YELLOW_PINK));
        tag(e, 78f - 13f, 30f, LINED, WHITE, 0);
    }

    /** 37. Thick yellow corners round the name in white, the tag in yellow. */
    private void yellowBrackets() {
        int y = 0xFFFFCE00;
        float c = 26f, pad = 12f, arm = 16f, wd = 5f;
        float w1 = tw(U1, c, 700), w2 = tw(U2, c, 700) + (sports ? tw(US, c, 700) + 11f : 0f), w = Math.max(w1, w2);
        float top = 0f, bottom = c * 2 + 14f + 2 * pad, right = w + 2 * pad;
        // The four corners, each two bars.
        rect(0, top, arm, top + wd, y);
        rect(0, top, wd, top + arm, y);
        rect(right - arm, top, right, top + wd, y);
        rect(right - wd, top, right, top + arm, y);
        rect(0, bottom - wd, arm, bottom, y);
        rect(0, bottom - arm, wd, bottom, y);
        rect(right - arm, bottom - wd, right, bottom, y);
        rect(right - wd, bottom - arm, right, bottom, y);
        text(U1, pad, pad + c, c, 700, WHITE, null);
        float e = text(U2, pad, pad + 2 * c + 14f, c, 700, WHITE, null);
        if (sports) text(US, e + 11f, pad + 2 * c + 14f, c, 700, WHITE, null);
        tag(right, bottom / 2f, c, PLAIN, y, 0);
    }

    /** 38. Red heavy capitals, narrow and tight, on black. */
    private void redCondensedCaps() {
        int red = 0xFFE50914;
        scaleX = 0.78f;
        spacing = -0.01f;
        text(U1, 0f, 36f, 36f, 900, red, null);
        float e = text(U2, 0f, 84f, 36f, 900, red, null);
        if (sports) e = text(US, e + 10f, 84f, 36f, 900, red, null);
        scaleX = 1f;
        spacing = 0f;
        tag(e, 84f - 18f, 32f, LINED, red, 0);
    }

    /** 39. A grid of nine light blue dots, the name beside it, SPORTS in light blue. */
    private void dotGrid() {
        int blue = 0xFF5BC2FF;
        float step = B2 / 3f, r = step * 0.3f;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) disc(step / 2f + i * step, step / 2f + j * step, r, blue, null);
        }
        float x = B2 + 16f;
        text(U1, x, B1, CAP, 700, WHITE, null);
        float e = text(U2, x, B2, CAP, 700, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 700, blue, null);
        tag(e, B2 - CAP / 2f, CAP, LINED, blue, 0);
    }

    /** 40. White title case on an azure blend, a small plus up by its end. */
    private void azureBlend() {
        float e1 = text(sports ? TN : T1, 0f, 30f, 28f, 500, WHITE, null);
        plus(e1 + 10f, 30f - 26f, 6f, 2.6f, WHITE);
        float e = text(sports ? TS : T2, 0f, 72f, 28f, 300, WHITE, null);
        tag(e, 72f - 13f, 28f, LINED, WHITE, 0);
    }

    /** 41. Heavy black lowercase on a light plate, a row of six coloured dots under it, sports beside the dots. */
    private void colourDots() {
        text(LN, 0f, 30f, 28f, 900, BLACK, null);
        float r = 5.5f, cy = 54f;
        for (int i = 0; i < 6; i++) disc(r + i * (2 * r + 6f), cy, r, DOTS[i], null);
        float x = 6 * (2 * r + 6f) + 8f;
        float e = sports ? text(LS, x, cy + 10f, 26f, 400, BLACK, null) : x - 8f;
        tag(e, cy, 22f, LINED, BLACK, 0);
    }

    /** 42. White lowercase, a light blue line under it, road and sports in light blue. */
    private void blueUnderline() {
        int blue = 0xFF00A8E1;
        float e1 = text(L1, 0f, B1, CAP, 700, WHITE, null);
        rrect(0f, B1 + 9f, e1, B1 + 13f, 2f, blue, null);
        float e = text(L2, 0f, B2 + 6f, CAP, 400, blue, null);
        if (sports) e = text(LS, e + 10f, B2 + 6f, CAP, 400, blue, null);
        tag(e, B2 + 6f - CAP * 0.36f, CAP, LINED, blue, 0);
    }

    /** 43. Heavy purple title case on a light plate, Sports lighter, the tag solid purple. */
    private void purpleHeavy() {
        int purple = 0xFF662D91;
        text(T1, 0f, B1, CAP, 900, purple, null);
        float e = text(T2, 0f, B2, CAP, 900, purple, null);
        if (sports) e = text(TS, e + 11f, B2, CAP, 400, purple, null);
        tag(e, B2 - CAP / 2f, CAP, SOLID, purple, WHITE);
    }

    /** 44. Lowercase in an ocean blue blend, sports spaced out in light white, on near black. */
    private void oceanBlue() {
        String a = sports ? LN : L1;
        float w = tw(a, 28f, 800);
        text(a, 0f, 30f, 28f, 800, 0, lin(0, 0, 0, w, 0, OCEAN));
        spacing = 0.35f;
        float e = text(sports ? LS : L2, 0f, 70f, 22f, 300, WHITE, null);
        spacing = 0f;
        tag(e + 8f, 70f - 10f, 22f, LINED, 0xFF35C8FF, 0);
    }

    /** 45. Lowercase glowing magenta to orange, sports in a white pill. */
    private void magentaGlow() {
        String a = sports ? LN : L1, b = sports ? LS : L2;
        float w = tw(a, 28f, 800);
        glow(0x99FF2D95, 8f);
        text(a, 0f, 30f, 28f, 800, 0, lin(0, 0, 0, w, 0, MAGENTA));
        shade();
        float pw = tw(b, 22f, 700), pad = 12f;
        rrect(0f, 44f, pw + 2 * pad, 80f, 18f, WHITE, null);
        boxed = true;
        text(b, pad, 69f, 22f, 700, 0xFFE0157A, null);
        boxed = false;
        tag(pw + 2 * pad, 62f, 28f, LINED, WHITE, 0);
    }

    /** 46. White lowercase on a plate of orange to pink, the tag in white with orange letters. */
    private void sunset() {
        text(L1, 0f, B1, CAP, 800, WHITE, null);
        float e = text(L2, 0f, B2, CAP, 800, WHITE, null);
        if (sports) e = text(LS, e + 11f, B2, CAP, 800, WHITE, null);
        tag(e, B2 - CAP * 0.4f, CAP, SOLID, WHITE, 0xFFF0412E);
    }

    /** 47. A red slash behind heavy leaning white capitals. */
    private void slashedCaps() {
        float w = tw(U1, CAP, 900);
        quad(w * 0.42f, B2 + 8f, w * 0.56f, B2 + 8f, w * 0.78f, -8f, w * 0.64f, -8f, 0xFFE3262E);
        skew = -0.2f;
        text(U1, 0f, B1, CAP, 900, WHITE, null);
        float e = text(U2, 0f, B2, CAP, 900, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 900, WHITE, null);
        skew = 0f;
        tag(e + 4f, B2 - CAP / 2f, CAP, LINED, WHITE, 0);
    }

    /** 48. Heavy blue title case ending in a bright full stop, Sports in white. */
    private void fullStop() {
        int blue = 0xFF1F7BFF, dot = 0xFF5EE2FF;
        if (sports) {
            float e1 = text(TN, 0f, 30f, 28f, 800, blue, null);
            disc(e1 + 7f, 30f - 4.5f, 4.5f, dot, null);
            float e = text(TS, 0f, 70f, 28f, 500, WHITE, null);
            tag(e, 70f - 13f, 28f, LINED, blue, 0);
        } else {
            text(T1, 0f, 30f, 28f, 800, blue, null);
            float e = text(T2, 0f, 70f, 28f, 800, blue, null);
            disc(e + 7f, 70f - 4.5f, 4.5f, dot, null);
            tag(e + 12f, 70f - 13f, 28f, LINED, blue, 0);
        }
    }

    /** 49. Outlined capitals glowing purple. */
    private void neonOutline() {
        int purple = 0xFFC86BFF;
        glow(0xCCB026FF, 7f);
        stroke = 2.4f;
        text(U1, 0f, B1, CAP, 800, purple, null);
        float e = text(U2, 0f, B2, CAP, 800, purple, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 800, purple, null);
        stroke = 0f;
        tag(e, B2 - CAP / 2f, CAP, LINED, purple, 0);
        shade();
    }

    /** 50. White lowercase in a blue rounded tag, sports beside it. */
    private void blueRoundedTag() {
        int blue = 0xFF1F4FD1;
        float c = 26f, pad = 16f, w = tw(LN, c, 800), h = c + 26f;
        rrect(0, 0, w + 2 * pad, h, h / 2f, blue, null);
        boxed = true;
        text(LN, pad, h / 2f + c * 0.36f, c, 800, WHITE, null);
        boxed = false;
        float e = w + 2 * pad;
        if (sports) e = text(LS, 0f, h + 38f, c, 500, WHITE, null);
        tag(e, sports ? h + 38f - 10f : h / 2f, c, LINED, WHITE, 0);
    }

    /** 51. Heavy white capitals with a band cut through them, a red dot before the tag. */
    private void cutThroughCaps() {
        if (!dry) {
            cv.save();
            cv.clipOutRect(-5f, B1 - CAP * 0.55f, 2000f, B1 - CAP * 0.44f);
            cv.clipOutRect(-5f, B2 - CAP * 0.55f, 2000f, B2 - CAP * 0.44f);
        }
        text(U1, 0f, B1, CAP, 900, WHITE, null);
        float e = text(U2, 0f, B2, CAP, 900, WHITE, null);
        if (sports) e = text(US, e + 12f, B2, CAP, 900, WHITE, null);
        if (!dry) cv.restore();
        disc(e + 12f, B2 - CAP / 2f, 5f, 0xFFE53935, null);
        tag(e + 17f, B2 - CAP / 2f, CAP, SOLID, 0xFFE53935, WHITE);
    }

    /** 52. White lowercase, then ROAD in black capitals in a yellow box, SPORTS spaced under it. */
    private void yellowBoxEnding() {
        int y = 0xFFFFE600;
        float e1 = text(L1, 0f, B1, CAP, 800, WHITE, null);
        // ROAD's box after whitebrook over SPORTS; under it for the name alone.
        float w = tw(U2, CAP * 0.9f, 900), pad = 8f, l = sports ? e1 + 10f : 0f, base = sports ? B1 : B2 + 6f;
        rect(l, base - CAP - pad, l + w + 2 * pad, base + pad, y);
        boxed = true;
        float e = text(U2, l + pad, base - 1f, CAP * 0.9f, 900, BLACK, null) + pad;
        boxed = false;
        if (sports) {
            spacing = 0.4f;
            float s2 = text(US, 0f, B2 + 4f, CAP * 0.66f, 600, WHITE, null);
            spacing = 0f;
            tag(s2 + 8f, B2 + 4f - CAP * 0.33f, CAP * 0.7f, LINED, y, 0);
        } else {
            tag(e, base - CAP / 2f, CAP, LINED, y, 0);
        }
    }

    /** 53. The name small in white, a big W in a pink to blue blend beside it. */
    private void bigGradientInitial() {
        float c = 22f;
        float e1 = text(U1, 0f, 24f, c, 700, WHITE, null);
        float e = text(U2, 0f, 56f, c, 700, WHITE, null);
        if (sports) e = text(US, e + 9f, 56f, c, 700, 0xFFFF7AC6, null);
        tag(e, 56f - c / 2f, c, LINED, WHITE, 0);
        float x = Math.max(e1, e + (tag.isEmpty() ? 0f : tw(tag, c * 0.6f, 700) + c * 1.3f)) + 14f;
        text("W", x, 64f, 64f, 900, 0, lin(0, x, 0f, x + 70f, 64f, RAINBOW));
    }

    /** 54. The name in heavy white capitals, three short stripes in red, white and blue under it, then SPORTS. */
    private void threeStripes() {
        float c = 24f;
        text(sports ? UN : U1, 0f, c, c, 900, WHITE, null);
        float t = c + 16f;
        rect(0, t, 26f, t + 6f, 0xFFE03A3E);
        rect(30f, t, 56f, t + 6f, WHITE);
        rect(60f, t, 86f, t + 6f, 0xFF2F6BFF);
        float e = text(sports ? US : U2, 98f, t + 13f, 22f, 700, WHITE, null);
        tag(e, t + 3f, 22f, LINED, WHITE, 0);
    }
}
