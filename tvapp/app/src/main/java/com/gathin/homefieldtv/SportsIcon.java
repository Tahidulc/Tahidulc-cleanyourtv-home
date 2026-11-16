package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** The rail's Sports icon: a ball, drawn in the rail's line (a circle, its middle panel and the seams out of it). */
final class SportsIcon {
    private SportsIcon() {}

    private static final Path P = new Path();

    static void draw(Canvas c, float cx, float cy, float s, Paint stroke, Paint solid) {
        float r = s * 0.46f;
        c.drawCircle(cx, cy, r, stroke);
        // The middle panel, a small pentagon, filled.
        Path p = P;
        p.reset();
        float pr = r * 0.36f;
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(-90 + k * 72);
            float x = cx + (float) Math.cos(a) * pr, y = cy + (float) Math.sin(a) * pr;
            if (k == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        c.drawPath(p, solid);
        // A seam from each corner out to the rim.
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(-90 + k * 72);
            float x0 = cx + (float) Math.cos(a) * pr, y0 = cy + (float) Math.sin(a) * pr;
            float x1 = cx + (float) Math.cos(a) * r, y1 = cy + (float) Math.sin(a) * r;
            c.drawLine(x0, y0, x1, y1, stroke);
        }
    }
}
