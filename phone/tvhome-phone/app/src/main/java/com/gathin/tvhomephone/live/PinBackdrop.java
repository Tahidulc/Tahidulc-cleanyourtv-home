package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The programme's own picture behind the folded band over the guide, held upright (the family's ask, 24 Nov: "So when I
 * go down the tv guide and it goes onto this view, the background should be programme background. If there's no
 * background for the programme, just have the colour of the panel instead."). Once the head has folded away and the
 * picture shrinks (Pin), the band under the top bar, behind the channel line, the small preview and the categories' row,
 * shows the picture of what is on now on the channel playing: the same picture the programme's panel would show
 * (ProgArt finds it, ProgPics makes it at the band's size with its faces whole right of the channel line's words: under
 * the small picture on a phone, never under the words; beside the channel line on a tablet), edge to edge, under a shade
 * so the words read (strongest behind the channel line's words, lighter over the rest, a little more along the top bar),
 * fading softly into the guide at its foot (solid halfway down the categories' row, nothing FADE_DP into the guide: no
 * edge). With no picture, the band in the panel's own colour (ProgGround, as the panel: the picture's own colour, else
 * the channel's logo colour), plain, under the same shade and fade (the logo is in the channel line already). While one
 * is looked for, nothing (the page's ground, as before), then the picture as it arrives (ProgArt.listen, ProgPics').
 * <p>
 * It is as tall as the band folded (the pin shrunk, the categories' row under it while it shows, and the fade), fixed at
 * the top, and comes in as the picture shrinks (alpha, FROM to FULL_AT) and goes as the head opens out again, so the open
 * page is as it was. It follows the channel playing and the programme on it (Pin.refresh: a new channel, each minute,
 * and each programme's end). Fold keeps it at the top under everything else; only its place and alpha change as the
 * guide scrolls: its picture, shade and fade are drawn once into its own layer (again only when the picture, its colour,
 * the words' place or its size changes, never per frame), with its shaders made once per size, and the picture made at
 * its whole-pixel size and drawn at it (no edge falling between pixels). Sideways there is no Fold and none of this.
 */
@SuppressLint("ViewConstructor")
final class PinBackdrop extends View {
    /** How far the picture has shrunk when the band starts to come in, and when it is all there. */
    static final float FROM = 0.1f, FULL_AT = 0.8f;
    /** Its foot: solid to this share down the categories' row, fading to nothing FADE_DP into the guide under it. */
    static final float UNDER_SOLID = 0.5f;
    static final int FADE_DP = 30;
    /**
     * The shade (PicDraw's colour): its alpha behind the start of the words, at their end and over the rest (the small
     * picture's side, the categories' count), easing between them over SIDE_DP; a little more along the top, TOP_DP down.
     */
    static final int AT_WORDS = 0xB8, WORDS_END = 0x9E, REST = 0x7A, TOP = 0x40;
    static final int SIDE_DP = 96, TOP_DP = 26;

    private final Fold.Shrinking pin;
    private final int fadeIn, side, topH;
    /** What is under the small picture in the band (the categories' row): the band goes on behind it. */
    private View under;
    /** The words beside the small picture (px across): the shade is strongest there. */
    private float wordsL = -1f, wordsR = -1f;
    /** As last measured: the pin shrunk and the row under it (px), so where its foot starts to fade. */
    private int compact, underH;

    private Channel ch;
    private Guide.Schedule s;
    private int at = -1;
    /** The picture's address: "" for none, null while it is looked for. */
    private String url = "";
    /** As shown: which (ProgGround), the picture or the plain colour. */
    private int kind = ProgGround.WAIT, colour;
    private Bitmap pic;
    private final int[] cols = new int[2];
    private Runnable onFilled;

    private final Paint picPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG),
            plain = new Paint(), shade = new Paint(Paint.DITHER_FLAG), top = new Paint(Paint.DITHER_FLAG),
            erase = new Paint(Paint.DITHER_FLAG);
    private final RectF whole = new RectF();
    private int builtW = -1, builtH = -1;
    private float builtL, builtR, fadeFrom;
    private final float[] pos = new float[6];
    private final int[] alphas = new int[6];
    private final Runnable redraw = this::resolve;
    private final Runnable found = () -> {
        if (ch != null) look();
    };

    PinBackdrop(Context c, Fold.Shrinking pin) {
        super(c);
        this.pin = pin;
        fadeIn = Ui.dp(c, FADE_DP);
        side = Ui.dp(c, SIDE_DP);
        topH = Ui.dp(c, TOP_DP);
        erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        // Drawn once into its own layer: as it fades in and out only the layer's alpha changes, and its foot's fade
        // takes the layer's own pixels away (never the page's under it).
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setAlpha(0f);
        setVisibility(INVISIBLE);
    }

    // ---- the pure parts ------------------------------------------------------------------------------------------

    /** Pure: how much of the band shows as the picture has shrunk by (0 open, 1 small): none open, all of it by FULL_AT. */
    static float alpha(float by) {
        float t = Math.max(0f, Math.min(1f, (by - FROM) / (FULL_AT - FROM)));
        // Eased, so it starts and ends softly.
        return t * t * (3f - 2f * t);
    }

    /** Pure: when what is on next at now begins: the end of the programme on now, else the start of the next; -1 for none. */
    static long nextChange(Guide.Schedule s, long now) {
        if (s == null) return -1L;
        int i = s.at(now);
        if (i >= 0) return s.stop[i];
        int j = s.after(now);
        return j >= 0 ? s.start[j] : -1L;
    }

    /**
     * Pure: the plain colour for a band with no picture: kind COLOUR, the picture's own (made); CHANNEL, the channel's
     * logo colour (top) as the panel has it; 0 (nothing: the page's ground) for any other, or with Behind logos None.
     */
    static int plainColour(int kind, int made, int top, int foot) {
        if (kind == ProgGround.COLOUR) return made | 0xFF000000;
        if (kind == ProgGround.CHANNEL && (top != 0 || foot != 0)) return top | 0xFF000000;
        return 0;
    }

    /** Pure: the share of a band w wide its words take at its left (ending at r); the panel's with none known. */
    static float room(int w, float r) {
        if (w <= 0 || r <= 0f) return PicFill.PANEL_ROOM;
        return Math.max(0.05f, Math.min(0.95f, r / w));
    }

    /**
     * Pure: the shade's stops across a band w wide whose words run from l to r (px): strongest at the words' start, a
     * little less at their end, easing to the rest over side either way. Fills pos (0 to 1) and alpha; how many.
     */
    static int stops(float w, float l, float r, float side, float[] pos, int[] alpha) {
        int n = 0;
        if (w <= 0f || r <= l) {
            pos[0] = 0f;
            alpha[0] = REST;
            pos[1] = 1f;
            alpha[1] = REST;
            return 2;
        }
        l = Math.max(0f, Math.min(w, l));
        r = Math.max(l, Math.min(w, r));
        if (l - side > 0f) {
            pos[n] = 0f;
            alpha[n++] = REST;
            pos[n] = (l - side) / w;
            alpha[n++] = REST;
        } else if (l > 0f) {
            pos[n] = 0f;
            alpha[n++] = AT_WORDS;
        }
        pos[n] = l / w;
        alpha[n++] = AT_WORDS;
        pos[n] = r / w;
        alpha[n++] = WORDS_END;
        if (r + side < w) {
            pos[n] = (r + side) / w;
            alpha[n++] = REST;
            pos[n] = 1f;
            alpha[n++] = REST;
        } else if (r < w) {
            pos[n] = 1f;
            alpha[n++] = WORDS_END;
        }
        return n;
    }

    // ---- what it shows -------------------------------------------------------------------------------------------

    /** The categories' row under the small picture: the band goes on behind it while it shows. */
    void under(View v) {
        under = v;
    }

    /** Where the words beside the small picture run across (px): the shade is strongest there. */
    void words(float l, float r) {
        if (l == wordsL && r == wordsR) return;
        wordsL = l;
        wordsR = r;
        build();
        // Its faces right of the words: made again for where they now end.
        resolve();
        invalidate();
    }

    /** Told when it has something to show, or no longer (a tablet's glass gives way to it). */
    void setOnFilled(Runnable r) {
        onFilled = r;
    }

    /** Whether it shows anything (a picture or a colour); not while one is looked for. */
    boolean filled() {
        return kind != ProgGround.WAIT && (pic != null || colour != 0);
    }

    /** The picture's address as last asked for ("" for none, null while looked for). */
    String showing() {
        return url;
    }

    /** The programme on now on ch (s its guide; null for none): its picture, or its colour. */
    void show(Channel ch, Guide.Schedule s, long now) {
        this.ch = ch;
        this.s = s;
        at = ch == null || s == null ? -1 : s.at(now);
        look();
    }

    /** As much as shows of it: alpha (0 to 1), not drawn at all at none. */
    void fade(float a) {
        if (getAlpha() != a) setAlpha(a);
        int v = a > 0f ? VISIBLE : INVISIBLE;
        if (getVisibility() != v) setVisibility(v);
    }

    private void look() {
        url = ch == null || at < 0 ? "" : ProgArt.of(getContext(), ch, s, at);
        resolve();
    }

    /** What to draw, as the picture (or its colour) is now known; drawn again only when that changed. */
    private void resolve() {
        int w = getWidth(), h = getHeight();
        String u = url;
        // Not laid out yet: asked for once it has its size.
        ProgPics.Made m = ch == null || u == null || u.isEmpty() || w < 2 || h < 2 ? null
                : ProgPics.get(getContext(), u, w, h, ProgPics.band(room(w, wordsR)), redraw);
        int k = ch == null ? ProgGround.WAIT : ProgGround.of(u, m);
        if (k == ProgGround.CHANNEL) ProgGround.channel(getContext(), ch, cols);
        else cols[0] = cols[1] = 0;
        Bitmap b = k == ProgGround.PICTURE ? m.b : null;
        int col = plainColour(k, k == ProgGround.COLOUR ? m.colour : 0, cols[0], cols[1]);
        if (k == kind && b == pic && col == colour) return;
        boolean was = filled();
        kind = k;
        pic = b;
        colour = col;
        plain.setColor(col);
        invalidate();
        if (was != filled() && onFilled != null) onFilled.run();
    }

    // ---- measuring and drawing -----------------------------------------------------------------------------------

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        compact = pin.compactHeight();
        underH = under == null || under.getVisibility() == GONE ? 0 : under.getMeasuredHeight();
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.max(0, compact + underH + fadeIn));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        build();
        resolve();
    }

    /** The shaders for this size and these words (made once each, never while drawing). */
    private void build() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (w == builtW && h == builtH && wordsL == builtL && wordsR == builtR) return;
        builtW = w;
        builtH = h;
        builtL = wordsL;
        builtR = wordsR;
        whole.set(0, 0, w, h);
        int n = stops(w, wordsL, wordsR, side, pos, alphas);
        int[] c = new int[n];
        float[] p = new float[n];
        for (int i = 0; i < n; i++) {
            c[i] = alphas[i] << 24 | PicDraw.SHADE;
            p[i] = pos[i];
        }
        shade.setShader(new LinearGradient(0, 0, w, 0, c, p, Shader.TileMode.CLAMP));
        top.setShader(new LinearGradient(0, 0, 0, topH, TOP << 24 | PicDraw.SHADE, PicDraw.SHADE, Shader.TileMode.CLAMP));
        // The foot taken away softly (eased): solid halfway down the categories' row, nothing FADE_DP into the guide.
        fadeFrom = Math.max(0f, Math.min(h - 1f, compact + underH * UNDER_SOLID));
        erase.setShader(new LinearGradient(0, fadeFrom, 0, h, new int[]{0x00000000, 0x28000000, 0x80000000, 0xD7000000, 0xFF000000},
                new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas cv) {
        if (!filled()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // Its own layer (setLayerType) holds the fade; were it ever drawn without one, a layer here instead.
        int saved = getLayerType() == LAYER_TYPE_NONE ? cv.saveLayer(whole, null) : -1;
        if (pic != null) cv.drawBitmap(pic, null, whole, picPaint);
        else cv.drawRect(whole, plain);
        cv.drawRect(whole, shade);
        cv.drawRect(0, 0, w, Math.min(h, topH), top);
        cv.drawRect(0, fadeFrom, w, h, erase);
        if (saved >= 0) cv.restoreToCount(saved);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ProgArt.listen(found);
    }

    @Override protected void onDetachedFromWindow() {
        ProgArt.unlisten(found);
        super.onDetachedFromWindow();
    }
}
