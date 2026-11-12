package com.gathin.tvhomephone.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

/**
 * Home's drawn pieces: rounded fills, the soft gradients a title without a picture shows (from its name, so the same
 * title always looks the same), the shimmer while rows load, scrims over pictures, and the play and info glyphs.
 */
final class Art {
    /** The launcher's grounds and its glass. */
    static final int GROUND = 0xFF0D0F2C, GROUND_DEEP = 0xFF0B0C21, NAVY = 0xFF10157E, GLASS = 0x1FFFFFFF, GLASS_LINE = 0x2EFFFFFF;
    static final int TEXT = 0xFFFFFFFF, SOFT = 0xCCE4E6F5, DIM = 0x99C9CBE0;
    static final int FILM = 0xFFFF4FA3, SHOW = 0xFF4F8BFF, NEWS = 0xFFFFB238, SPORT = 0xFF3DDC84, RESUME = 0xFFC9A7FF;
    /** Pairs of quiet colours for a title with no picture: navy and plum, teal, rust, slate. */
    private static final int[][] PAIRS = {
            {0xFF1E2A78, 0xFF4B2C7A}, {0xFF123B5C, 0xFF1F6F78}, {0xFF3A1E4F, 0xFF7A2F5B}, {0xFF22304A, 0xFF4C5A7A},
            {0xFF2B1F5C, 0xFF1F4E8C}, {0xFF4A2A1E, 0xFF8A4A2A}, {0xFF173D3A, 0xFF2E6B4F}, {0xFF2A2350, 0xFF6A3D8C}};

    private Art() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable d = round(GLASS, dp(c, radiusDp));
        d.setStroke(Math.max(1, dp(c, 1)), GLASS_LINE);
        return d;
    }

    /** A title's own soft gradient, from its name. */
    static GradientDrawable placeholder(String seed, float radiusPx) {
        int[] pair = PAIRS[Math.floorMod(seed == null ? 0 : seed.hashCode(), PAIRS.length)];
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, pair);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable vertical(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    static GradientDrawable vertical(int top, int middle, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, middle, bottom});
    }

    static GradientDrawable horizontal(int left, int right) {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{left, right});
    }

    /** Rounded corners that clip the view's children (the picture in a card). */
    static void clip(View v, float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    /** The shimmer a card shows while its row loads: a faint fill with a light sweeping across it. */
    static final class Shimmer extends Drawable {
        private static final long PERIOD = 1400;
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG), light = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final float radius;

        Shimmer(float radiusPx) {
            radius = radiusPx;
            base.setColor(0x16FFFFFF);
        }

        @Override protected void onBoundsChange(@NonNull Rect b) {
            // One gradient for the sweep, moved along by its matrix each frame.
            light.setShader(b.isEmpty() ? null : new LinearGradient(0, 0, b.width() * 0.7f, b.height() * 0.25f,
                    new int[]{0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        }

        @Override public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            box.set(b);
            canvas.drawRoundRect(box, radius, radius, base);
            long now = SystemClock.uptimeMillis();
            float t = (now % PERIOD) / (float) PERIOD;
            float w = b.width();
            Shader s = light.getShader();
            if (s != null) {
                move.setTranslate(b.left - w + t * w * 2.2f, b.top);
                s.setLocalMatrix(move);
                canvas.drawRoundRect(box, radius, radius, light);
            }
            scheduleSelf(this::invalidateSelf, now + 33);
        }

        private final android.graphics.Matrix move = new android.graphics.Matrix();

        @Override public void setAlpha(int alpha) {
            base.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            base.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A small drawn glyph: play's triangle, or info's i in a ring. */
    static final class Glyph extends Drawable {
        static final int PLAY = 0, INFO = 1;
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        Glyph(int kind, int color, int sizePx) {
            this.kind = kind;
            this.size = sizePx;
            paint.setColor(color);
        }

        @Override public int getIntrinsicWidth() {
            return size;
        }

        @Override public int getIntrinsicHeight() {
            return size;
        }

        @Override public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), x = b.left, y = b.top;
            if (kind == PLAY) {
                Path p = new Path();
                p.moveTo(x + w * 0.22f, y + h * 0.12f);
                p.lineTo(x + w * 0.88f, y + h * 0.5f);
                p.lineTo(x + w * 0.22f, y + h * 0.88f);
                p.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(p, paint);
            } else {
                float r = Math.min(w, h) / 2f, cx = x + w / 2f, cy = y + h / 2f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * 0.16f);
                canvas.drawCircle(cx, cy, r * 0.88f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy - r * 0.38f, r * 0.12f, paint);
                canvas.drawRoundRect(new RectF(cx - r * 0.1f, cy - r * 0.14f, cx + r * 0.1f, cy + r * 0.5f), r * 0.08f, r * 0.08f, paint);
            }
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
