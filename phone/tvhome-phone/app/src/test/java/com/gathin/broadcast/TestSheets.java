package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Tests: a sheet of kit frames from a picture (one the page made, or one drawn here with flat shirts). */
public final class TestSheets {
    private TestSheets() { }

    /** The page's picture as the app gets it: cols by rows frames of size px, in the order of angles. */
    public static KitSheets.Sheet of(String key, Bitmap b, int cols, int rows, int size, float[] angles) {
        return new KitSheets.Sheet(key, b, cols, rows, size, angles);
    }

    /** Frames drawn here, each a flat shirt in body and trim turned to its angle (no page to make real ones). */
    public static KitSheets.Sheet flat(String key, int size, float[] angles, int body, int trim) {
        int n = angles.length, cols = (int) Math.ceil(Math.sqrt(n)), rows = (n + cols - 1) / cols;
        Bitmap b = Bitmap.createBitmap(cols * size, rows * size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < n; i++) {
            float cx = (i % cols) * size + size / 2f, cy = (i / cols) * size + size / 2f;
            KitSheets.flatShirt(c, cx, cy, size * 0.8f, body, trim, angles[i], p);
        }
        return of(key, b, cols, rows, size, angles);
    }
}
