package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * Pictures drawn into rounded shapes (a card, a tile) with smooth corners at any angle: each picture as a shader, made
 * the first time it is drawn at a size and kept for the next frames (the most recent COUNT), so a frame makes nothing
 * new. The shader places the picture covering w x h (its middle kept) from (dx, dy) in the shape's own coordinates;
 * draw the shape there with the paint. Main thread only.
 *
 * <pre>
 *   Paint p = shaders.paint(bitmap, w, h, bleed);   the picture covering (-bleed, -bleed) to (w + bleed, h + bleed)
 *   c.drawRoundRect(0, 0, w, h, r, r, p);           in the shape's coordinates (canvas translated to its corner)
 * </pre>
 */
final class Shaders {
    static final int COUNT = 32;

    private final Bitmap[] bitmaps = new Bitmap[COUNT];
    private final float[] sizes = new float[COUNT * 3];
    private final Paint[] paints = new Paint[COUNT];
    private final long[] used = new long[COUNT];
    private long clock;

    /** The picture as a paint covering the shape w x h and bleed past it all round (screen px). */
    Paint paint(Bitmap b, float w, float h, float bleed) {
        clock++;
        int free = 0;
        for (int i = 0; i < COUNT; i++) {
            if (bitmaps[i] == b && sizes[i * 3] == w && sizes[i * 3 + 1] == h && sizes[i * 3 + 2] == bleed) {
                used[i] = clock;
                return paints[i];
            }
            if (used[i] < used[free]) free = i;
        }
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float cw = w + 2 * bleed, ch = h + 2 * bleed;
        float k = Math.max(cw / b.getWidth(), ch / b.getHeight());
        Matrix m = new Matrix();
        m.setScale(k, k);
        m.postTranslate(-bleed + (cw - b.getWidth() * k) / 2f, -bleed + (ch - b.getHeight() * k) / 2f);
        s.setLocalMatrix(m);
        Paint p = paints[free] != null ? paints[free] : new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(s);
        p.setAlpha(255);
        paints[free] = p;
        bitmaps[free] = b;
        sizes[free * 3] = w;
        sizes[free * 3 + 1] = h;
        sizes[free * 3 + 2] = bleed;
        used[free] = clock;
        return p;
    }

    /** Forget every picture (they were let go). */
    void clear() {
        for (int i = 0; i < COUNT; i++) {
            bitmaps[i] = null;
            if (paints[i] != null) paints[i].setShader(null);
            used[i] = 0;
        }
    }
}
