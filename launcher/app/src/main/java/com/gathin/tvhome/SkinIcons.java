package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.Map;

/**
 * The Home styles' icons, drawn from 24 x 24 outlines (round caps and joins, as the styles' mock-ups drew them) at
 * whatever size they are given, in one colour: no pictures of their own, so a style needs nothing from the APK's
 * resources and draws the same in a preview.
 */
final class SkinIcons {
    private SkinIcons() {
    }

    /** {outline, filled shapes} in a 24 x 24 box: the outline is stroked, the second part filled. */
    private static final Map<String, String[]> PATHS = new HashMap<>();

    /** A style's own icon, added to the set (an outline to stroke and/or shapes to fill, in a 24 x 24 box). */
    static void add(String name, String stroke, String fill) {
        put(name, stroke, fill);
    }

    private static void put(String name, String stroke, String fill) {
        PATHS.put(name, new String[]{stroke, fill});
    }

    /** A circle as path data. */
    static String circle(float cx, float cy, float r) {
        return "M" + (cx - r) + "," + cy + "a" + r + "," + r + " 0 1,0 " + (2 * r) + ",0a" + r + "," + r + " 0 1,0 " + (-2 * r) + ",0";
    }

    /** A rounded rectangle as path data. */
    static String rect(float x, float y, float w, float h, float r) {
        return "M" + (x + r) + "," + y + "h" + (w - 2 * r) + "a" + r + "," + r + " 0 0 1 " + r + "," + r + "v" + (h - 2 * r)
                + "a" + r + "," + r + " 0 0 1 " + (-r) + "," + r + "h" + (-(w - 2 * r)) + "a" + r + "," + r + " 0 0 1 " + (-r) + "," + (-r)
                + "v" + (-(h - 2 * r)) + "a" + r + "," + r + " 0 0 1 " + r + "," + (-r) + "z";
    }

    static {
        put("home", "M3 11l9-7 9 7M5 10v10h14V10", null);
        put("search", circle(10.5f, 10.5f, 6.5f) + "M15.5 15.5L21 21", null);
        put("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z", null);
        put("live", circle(12, 12, 2) + "M7.8 7.8a6 6 0 0 0 0 8.4M16.2 16.2a6 6 0 0 0 0-8.4M4.9 4.9a10 10 0 0 0 0 14.2M19.1 19.1a10 10 0 0 0 0-14.2", null);
        put("guide", rect(3, 5, 18, 14, 2) + "M3 10h18M9 10v9", null);
        put("movies", rect(3, 4, 18, 16, 2) + "M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4", null);
        put("shows", rect(3, 6, 18, 12, 2) + "M8 21h8M9 2l3 4 3-4", null);
        put("sports", circle(12, 12, 9) + "M12 7l3.5 2.5-1.3 4h-4.4l-1.3-4zM12 3v4M20.6 9.5h-5.1M17 19.5l-2.8-6M7 19.5l2.8-6M3.4 9.5h5.1", null);
        put("kids", circle(12, 13, 7) + circle(6.5f, 6.5f, 2.5f) + circle(17.5f, 6.5f, 2.5f) + "M9.5 14.5c1.5 1.3 3.5 1.3 5 0", null);
        put("apps", rect(4, 4, 6.5f, 6.5f, 1.5f) + rect(13.5f, 4, 6.5f, 6.5f, 1.5f) + rect(4, 13.5f, 6.5f, 6.5f, 1.5f) + rect(13.5f, 13.5f, 6.5f, 6.5f, 1.5f), null);
        put("sliders", "M4 7h10M18 7h2M4 17h4M12 17h8" + circle(16, 7, 2.2f) + circle(10, 17, 2.2f), null);
        put("play", null, "M7 4.5v15l12.5-7.5z");
        put("info", circle(12, 12, 9.5f) + "M12 11v6M12 7.5v0.01", null);
        put("plus", "M12 5v14M5 12h14", null);
        put("chev", "M9 5l7 7-7 7", null);
        put("chevdown", "M5 9l7 7 7-7", null);
        put("back", "M20 12H5M11 5l-7 7 7 7", null);
        put("star", null, "M12 3l2.6 5.6 6 0.7-4.5 4.1 1.2 6L12 16.6 6.7 19.4l1.2-6L3.4 9.3l6-0.7z");
        put("cloud", "M7 18h10a4 4 0 0 0 0.6-7.95A6 6 0 0 0 6.1 11.2 3.5 3.5 0 0 0 7 18z", null);
        put("dice", rect(3.5f, 3.5f, 17, 17, 3.5f), circle(8.5f, 8.5f, 1.3f) + circle(15.5f, 15.5f, 1.3f) + circle(12, 12, 1.3f) + circle(15.5f, 8.5f, 1.3f) + circle(8.5f, 15.5f, 1.3f));
        put("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0", null);
        put("style", "M6.5 4.5h11M4.5 7.5h15" + rect(3, 10.5f, 18, 9, 1.5f), null);
        put("more", null, circle(5, 12, 1.9f) + circle(12, 12, 1.9f) + circle(19, 12, 1.9f));
        put("record", circle(12, 12, 9), circle(12, 12, 4.5f));
        put("settings", circle(12, 12, 3) + "M19.4 15a1.65 1.65 0 0 0 0.33 1.82l0.06 0.06a2 2 0 1 1-2.83 2.83l-0.06-0.06a1.65 1.65 0 0 0-1.82-0.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-0.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82 0.33l-0.06 0.06a2 2 0 1 1-2.83-2.83l0.06-0.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h0.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-0.33-1.82l-0.06-0.06a2 2 0 1 1 2.83-2.83l0.06 0.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v0.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-0.33l0.06-0.06a2 2 0 1 1 2.83 2.83l-0.06 0.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-0.09a1.65 1.65 0 0 0-1.51 1z", null);
        // Catch-up: an arrow going back round a clock face (the guide's channels that can be watched again).
        put("catchup", "M3.5 12a8.5 8.5 0 1 0 2.5-6M3.5 4v4h4M12 8v4l2.8 1.8", null);
        put("sort", "M8 4v16M4 8l4-4 4 4M16 20V4M12 16l4 4 4-4", null);
        put("expand", "M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7", null);
        put("check", "M5 12.5l4.5 4.5L19 7.5", null);
        put("user", circle(12, 8, 4) + "M4 21a8 8 0 0 1 16 0", null);
        put("wifi", "M2 8.5a15 15 0 0 1 20 0M5 12a10.5 10.5 0 0 1 14 0M8.5 15.5a5.5 5.5 0 0 1 7 0M12 19h0.01", null);
        put("shield", "M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z", null);
        put("bulb", "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.6 10.8c0.6 0.5 0.9 1.2 0.9 2V17h5.4v-1.2c0-0.8 0.3-1.5 0.9-2A6 6 0 0 0 12 3z", null);
        put("moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z", null);
        put("message", "M4 5h16v11H8l-4 4z", null);
        put("cast", "M4 12h12M12 6l6 6-6 6", null);
        put("chart", "M4 20V10M10 20V4M16 20v-7M22 20H2", null);
        put("trophy", "M8 4h8v5a4 4 0 0 1-8 0zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8.5 20h7M10 17h4v3h-4z", null);
        put("flag", "M5 21V4M5 4h11l-2 4 2 4H5", null);
        put("news", rect(3, 5, 18, 14, 2) + "M7 9h6M7 13h10M7 16h8", null);
    }

    static boolean has(String name) {
        return PATHS.containsKey(name);
    }

    /** The icon `name` in `colour`; its stroke `stroke` units of the 24 (2 is the mock-ups' usual). */
    static Drawable get(String name, int colour, float stroke) {
        return new Icon(PATHS.get(name), colour, stroke);
    }

    static Drawable get(String name, int colour) {
        return get(name, colour, 2f);
    }

    private static final Map<String, Path> PARSED = new HashMap<>();

    private static synchronized Path parse(String data) {
        if (data == null) return null;
        Path p = PARSED.get(data);
        if (p == null) {
            p = PathParser.createPathFromPathData(data);
            PARSED.put(data, p);
        }
        return p;
    }

    /** One icon: its outline and fill, scaled to its bounds, tinted by setColorFilter (ImageView.setColorFilter). */
    static final class Icon extends Drawable {
        private final Path line, solid;
        private final Path drawn = new Path();
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final float stroke;
        private int colour;

        Icon(String[] paths, int colour, float stroke) {
            line = paths == null ? null : parse(paths[0]);
            solid = paths == null ? null : parse(paths[1]);
            this.colour = colour;
            this.stroke = stroke;
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
            ink.setStyle(Paint.Style.FILL);
            pen.setColor(colour);
            ink.setColor(colour);
        }

        /** Its colour now (a focused button's icon goes dark on white). */
        void colour(int c) {
            colour = c;
            pen.setColor(c);
            ink.setColor(c);
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float k = Math.min(b.width(), b.height()) / 24f;
            m.setScale(k, k);
            m.postTranslate(b.left + (b.width() - 24 * k) / 2f, b.top + (b.height() - 24 * k) / 2f);
            pen.setStrokeWidth(stroke * k);
            if (line != null) {
                drawn.reset();
                line.transform(m, drawn);
                canvas.drawPath(drawn, pen);
            }
            if (solid != null) {
                drawn.reset();
                solid.transform(m, drawn);
                canvas.drawPath(drawn, ink);
            }
        }

        @Override public void setAlpha(int alpha) {
            pen.setAlpha(alpha);
            ink.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter cf) {
            pen.setColorFilter(cf);
            ink.setColorFilter(cf);
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override public int getIntrinsicWidth() {
            return 48;
        }

        @Override public int getIntrinsicHeight() {
            return 48;
        }
    }
}
