package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The highlight round whatever the remote is on: a deep navy box with a thin light-blue edge, and a streak of light
 * at the middle of its top and bottom edges, as the guide draws it.
 */
final class Glow {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float shaderFor = -1f;

    Glow() {
        fill.setColor(0xF00B2C6C);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, Ui.f(2f)));
        line.setColor(0xFF5E9FE8);
    }

    /** A box only (no streaks): the guide's cells. */
    void box(Canvas c, RectF r) {
        c.drawRect(r, fill);
        c.drawRect(r, line);
    }

    void draw(Canvas c, RectF r) {
        draw(c, r, true);
    }

    void draw(Canvas c, RectF r, boolean filled) {
        if (filled) c.drawRect(r, fill);
        c.drawRect(r, line);
        float sw = Math.min(r.width() * 0.62f, Ui.f(300f));
        if (sw != shaderFor) {
            streak.setShader(new LinearGradient(-sw / 2, 0, sw / 2, 0, new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, null,
                    Shader.TileMode.CLAMP));
            halo.setShader(new RadialGradient(0, 0, sw / 2, new int[]{0xB0CFEAFF, 0x40A8D8FF, 0x00A8D8FF},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            shaderFor = sw;
        }
        streak(c, r.centerX(), r.top, sw);
        streak(c, r.centerX(), r.bottom, sw);
    }

    private void streak(Canvas c, float cx, float y, float sw) {
        c.save();
        c.translate(cx, y);
        c.save();
        c.scale(1f, Ui.f(13f) / (sw / 2));
        c.drawCircle(0, 0, sw / 2, halo);
        c.restore();
        float t = Math.max(1f, Ui.f(1.5f));
        c.drawRect(-sw / 2, -t, sw / 2, t, streak);
        c.restore();
    }
}
