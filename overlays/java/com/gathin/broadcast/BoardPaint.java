package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a custom board (BoardSpec) adds to the boards' own drawing, the same on every app: its typeface (read once from
 * the bundled fonts and kept), what sits behind each crest (a light disc, or a ring of the accent), the fine accent line
 * along its foot and its border. Each draws with the board's own Paint, so nothing is made while drawing.
 */
public final class BoardPaint {
    private BoardPaint() {
    }

    private static final Map<BoardSpec.Font, Typeface> FONTS = new EnumMap<>(BoardSpec.Font.class);

    /** The spec's typeface (the system's bold when its file can't be read); read once, off the drawing. */
    public static Typeface typeface(Context c, BoardSpec.Font f) {
        if (f == null || f.asset == null) return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        synchronized (FONTS) {
            Typeface t = FONTS.get(f);
            if (t != null) return t;
            try {
                t = Typeface.createFromAsset(c.getAssets(), f.asset);
            } catch (RuntimeException e) {
                t = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
            }
            FONTS.put(f, t);
            return t;
        }
    }

    /**
     * Behind a crest drawn in the size-wide square at (x, y): a light disc (DISC), or a disc of the panel ringed in the
     * accent (RING); nothing for PLAIN. fill is the board's paint; it is left as a plain fill.
     */
    public static void crestBack(Canvas cv, Paint fill, BoardSpec s, float x, float y, float size) {
        if (s == null || s.crest == BoardSpec.Crest.PLAIN || size <= 0) return;
        float cx = x + size / 2f, cy = y + size / 2f, r = size * 0.62f;
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        if (s.crest == BoardSpec.Crest.DISC) {
            fill.setColor(0xF2F6F7FA);
            cv.drawCircle(cx, cy, r, fill);
        } else {
            fill.setColor(BoardColours.value(s.panelMid(), 0.8f));
            cv.drawCircle(cx, cy, r, fill);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Math.max(1.5f, size * 0.08f));
            fill.setColor(s.accent);
            cv.drawCircle(cx, cy, r - size * 0.04f, fill);
            fill.setStyle(Paint.Style.FILL);
        }
    }

    /** The fine accent line along the board's foot (accentLine), w wide, h high. */
    public static void accentLine(Canvas cv, Paint fill, BoardSpec s, float w, float h) {
        if (s == null || !s.accentLine) return;
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(s.accent);
        float t = Math.max(2f, h * 0.06f);
        cv.drawRect(0, h - t, w, h, fill);
    }

    /** The border round the board (its width a share of h) in the rect's corners; nothing when it has none. */
    public static void border(Canvas cv, Paint edge, BoardSpec s, float w, float h, float rad, android.graphics.RectF r) {
        if (s == null) return;
        float e = s.edgeWidth() * h;
        if (e <= 0) return;
        e = Math.max(1f, e);
        edge.setShader(null);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(e);
        edge.setColor(s.edgeColour());
        r.set(e / 2, e / 2, w - e / 2, h - e / 2);
        cv.drawRoundRect(r, Math.max(0, rad - e / 2), Math.max(0, rad - e / 2), edge);
        edge.setStyle(Paint.Style.FILL);
    }
}
