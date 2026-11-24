package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * The scoreboard editor's stand-in picture, drawn once: a match under floodlights (the stands, the pitch's mown
 * stripes, its lines), for the board being made to sit on as it would over a game; and two invented clubs' crests
 * (Harbourside in teal, Millbrook in claret: shields with their letters), so the preview never needs a real club or a
 * download.
 */
public final class EditorStill {
    private EditorStill() {
    }

    /** The invented clubs: their codes, names, colours. */
    public static final String HOME = "HAR", AWAY = "MIL", HOME_NAME = "Harbourside", AWAY_NAME = "Millbrook";
    public static final int HOME_COLOUR = 0xFF0F8B8D, AWAY_COLOUR = 0xFF7A1F3D;

    /** A w by h picture of a match (no people, nothing real). */
    public static Bitmap pitch(int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(8, w), Math.max(8, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float horizon = h * 0.34f;
        // The stands: dark, with rows of seats catching the light.
        p.setShader(new LinearGradient(0, 0, 0, horizon, 0xFF0B0F1A, 0xFF1C2433, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon, p);
        p.setShader(null);
        p.setColor(0x22FFFFFF);
        for (float y = h * 0.06f; y < horizon - 4; y += Math.max(3f, h * 0.025f)) c.drawRect(0, y, w, y + Math.max(1f, h * 0.006f), p);
        // The pitch: mown stripes, lighter toward the camera.
        p.setShader(new LinearGradient(0, horizon, 0, h, 0xFF2C6B32, 0xFF3F8F45, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, h, p);
        p.setShader(null);
        int stripes = 9;
        for (int i = 0; i < stripes; i += 2) {
            Path s = new Path();
            float x0 = w * i / (float) stripes, x1 = w * (i + 1) / (float) stripes;
            s.moveTo(w / 2f + (x0 - w / 2f) * 0.55f, horizon);
            s.lineTo(w / 2f + (x1 - w / 2f) * 0.55f, horizon);
            s.lineTo(w / 2f + (x1 - w / 2f) * 1.6f, h);
            s.lineTo(w / 2f + (x0 - w / 2f) * 1.6f, h);
            s.close();
            p.setColor(0x14FFFFFF);
            c.drawPath(s, p);
        }
        // Its lines: the touchline and the centre circle seen from the side.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, h * 0.006f));
        p.setColor(0xB3FFFFFF);
        c.drawLine(0, horizon + h * 0.04f, w, horizon + h * 0.04f, p);
        c.drawLine(w / 2f, horizon + h * 0.04f, w / 2f, h, p);
        c.drawOval(w * 0.36f, h * 0.55f, w * 0.64f, h * 0.82f, p);
        p.setStyle(Paint.Style.FILL);
        // The floodlights' glow.
        p.setShader(new RadialGradient(w * 0.82f, h * 0.05f, w * 0.5f, 0x40FFF4D6, 0x00FFF4D6, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h, 0x33000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.3f, p);
        return b;
    }

    /** An invented club's crest: a shield in its colour with a white rim and its letter. */
    public static Bitmap crest(int colour, String letter, int px) {
        int s = Math.max(16, px);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path shield = new Path();
        float l = s * 0.14f, r = s * 0.86f, t = s * 0.08f, m = s * 0.6f, bot = s * 0.94f;
        shield.moveTo(l, t);
        shield.lineTo(r, t);
        shield.lineTo(r, m);
        shield.quadTo(r, s * 0.82f, s / 2f, bot);
        shield.quadTo(l, s * 0.82f, l, m);
        shield.close();
        p.setColor(0xFFFFFFFF);
        c.drawPath(shield, p);
        c.save();
        c.scale(0.84f, 0.86f, s / 2f, s * 0.48f);
        p.setColor(BoardColours.opaque(colour));
        c.drawPath(shield, p);
        c.restore();
        p.setColor(0x33FFFFFF);
        c.drawRect(s * 0.26f, s * 0.2f, s * 0.74f, s * 0.3f, p);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(s * 0.4f);
        c.drawText(letter == null || letter.isEmpty() ? "" : letter.substring(0, 1), s / 2f, s * 0.64f, p);
        return b;
    }
}
