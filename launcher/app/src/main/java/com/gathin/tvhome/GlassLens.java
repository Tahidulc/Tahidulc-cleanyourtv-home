package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

/**
 * Home's focus as one piece of liquid glass (launcher v2, the dock layout): it flows from whatever had the focus to
 * whatever has it now on springs that overshoot a touch, stretching along the way it travels and narrowing across it,
 * as the glass in Apple's newest systems does. Over a tile it is a glowing ring with a rainbow edge; in the dock a
 * frosted pill that sits behind the icon. As it lands after a real move a shine sweeps across it. Drawn on its own
 * (no blur, which costs too much on a TV), only while it moves; hidden where the focus is something else's to show.
 */
final class GlassLens extends View {
    private final float unit;
    private final float[] cur = new float[5], vel = new float[5], tgt = new float[5];   // x, y, w, h, radius
    private boolean placed, moving, travelled;
    private float alpha, alphaTo, fill, fillTo, angle, shine = -1f;
    private long last;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path clip = new Path();
    private final Matrix turn = new Matrix();
    private final SweepGradient rainbow;
    private final int[] rainbowColours = {0xD9FF8CD2, 0xF28CD2FF, 0xFFFFFFFF, 0xD9FFE28C, 0xE6AA8CFF, 0xD9FF8CD2};

    GlassLens(Context c, float unit) {
        super(c);
        this.unit = unit;
        rainbow = new SweepGradient(0, 0, rainbowColours, null);
    }

    /** To this box (screen px): a ring round a tile (ring) or a pill behind a dock item. */
    void to(float x, float y, float w, float h, float radius, boolean ring) {
        float[] t = {x, y, w, h, radius};
        travelled = !placed || Math.hypot(x - tgt[0], y - tgt[1]) > 40 * unit;
        System.arraycopy(t, 0, tgt, 0, 5);
        fillTo = ring ? 0f : 1f;
        if (!placed || alpha < 0.05f) {
            // Coming back from hidden: it appears where it is wanted, no journey from an old place.
            System.arraycopy(t, 0, cur, 0, 5);
            java.util.Arrays.fill(vel, 0f);
            fill = fillTo;
            placed = true;
            travelled = false;
        }
        alphaTo = 1f;
        kick();
    }

    void hide() {
        alphaTo = 0f;
        kick();
    }

    private void kick() {
        if (!moving) {
            moving = true;
            last = SystemClock.uptimeMillis();
        }
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(2.5f, Math.max(0.5f, (now - last) / 16.67f));
        last = now;
        // Springs: pulled to the target, damped, a little bounce at the end.
        float still = 0f;
        for (int i = 0; i < 5; i++) {
            vel[i] = vel[i] * (float) Math.pow(0.70, dt) + (tgt[i] - cur[i]) * 0.15f * dt;
            cur[i] += vel[i] * dt;
            still += Math.abs(vel[i]) + Math.abs(tgt[i] - cur[i]) * 0.1f;
        }
        alpha += (alphaTo - alpha) * Math.min(1f, 0.22f * dt);
        fill += (fillTo - fill) * Math.min(1f, 0.2f * dt);
        float speed = Math.abs(vel[0]) + Math.abs(vel[1]);
        angle = (angle + speed / unit * 0.35f * dt) % 360f;
        boolean settled = still < 0.6f * unit;
        if (settled && travelled) {
            travelled = false;
            shine = 0f;
        }
        if (shine >= 0f) {
            shine += 0.045f * dt;
            if (shine > 1f) shine = -1f;
        }
        if (alpha > 0.01f && placed) drawGlass(c);
        boolean done = settled && Math.abs(alphaTo - alpha) < 0.01f && Math.abs(fillTo - fill) < 0.01f && shine < 0f;
        if (done) {
            // At rest it is not drawn again until it moves: nothing on Home redraws for it.
            alpha = alphaTo;
            moving = false;
        } else {
            postInvalidateOnAnimation();
        }
    }

    private void drawGlass(Canvas c) {
        float x = cur[0], y = cur[1], w = cur[2], h = cur[3], r = Math.max(0, Math.min(cur[4], Math.min(w, h) / 2f));
        float cx = x + w / 2f, cy = y + h / 2f;
        // Stretched along the way it is going, narrowed across it: the liquid in it.
        float sx = 1f + Math.min(0.30f, Math.abs(vel[0]) / (unit * 190f)), sy = 1f + Math.min(0.30f, Math.abs(vel[1]) / (unit * 190f));
        float stretchX = sx / (float) Math.sqrt(sy), stretchY = sy / (float) Math.sqrt(sx);
        int save = c.save();
        c.scale(stretchX, stretchY, cx, cy);
        box.set(x, y, x + w, y + h);
        int a = Math.round(255 * alpha);

        // The frosted body (the pill), fading out as it becomes a ring round a picture.
        if (fill > 0.01f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, y, 0, y + h, 0x52FFFFFF, 0x17FFFFFF, Shader.TileMode.CLAMP));
            paint.setAlpha(Math.round(a * fill));
            paint.setShadowLayer(14 * unit, 0, 6 * unit, 0x59040628);
            c.drawRoundRect(box, r, r, paint);
            paint.clearShadowLayer();
            // Light along its top edge.
            paint.setShader(new LinearGradient(0, y, 0, y + h * 0.4f, 0xE6FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f * unit);
            paint.setAlpha(Math.round(a * fill));
            c.drawRoundRect(box, r, r, paint);
        }
        // The ring: white, glowing a cool blue round the picture.
        if (fill < 0.99f) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.2f * unit);
            paint.setColor(0xF2FFFFFF);
            paint.setAlpha(Math.round(a * (1f - fill)));
            paint.setShadowLayer(20 * unit, 0, 0, 0x9982A0FF);
            float in = 1.6f * unit;
            c.drawRoundRect(x + in, y + in, x + w - in, y + h - in, Math.max(0, r - in), Math.max(0, r - in), paint);
            paint.clearShadowLayer();
        }
        // The rainbow edge, turning slowly, faster as it flies.
        turn.setRotate(angle);
        turn.postTranslate(cx, cy);
        rainbow.setLocalMatrix(turn);
        paint.setShader(rainbow);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f * unit);
        paint.setAlpha(Math.round(a * 0.85f));
        float e = 0.6f * unit;
        c.drawRoundRect(x - e, y - e, x + w + e, y + h + e, r + e, r + e, paint);
        // The shine sweeping across as it lands.
        if (shine >= 0f) {
            clip.reset();
            clip.addRoundRect(box, r, r, Path.Direction.CW);
            int s2 = c.save();
            c.clipPath(clip);
            float band = w * 0.36f, bx = x - band + (w + band * 2) * shine;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(bx - band / 2, y, bx + band / 2, y + h * 0.35f,
                    new int[]{0x00FFFFFF, 0x80FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            paint.setAlpha(Math.round(a * (float) Math.sin(Math.PI * shine)));
            c.drawRect(bx - band, y - h, bx + band, y + h * 2, paint);
            c.restoreToCount(s2);
        }
        paint.setShader(null);
        c.restoreToCount(save);
    }
}
