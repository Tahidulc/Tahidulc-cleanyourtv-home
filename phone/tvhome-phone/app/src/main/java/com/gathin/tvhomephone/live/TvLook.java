package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gathin.tvhomephone.home.Type;

/**
 * The TV section's look after Homefield TV 2.0's "07" (frosted materials over a deep, softly lit ground), made for a
 * finger: the ground is near-black with the picture or the channel's colour glowing through (TvGround); panels are
 * tinted translucent glass with a bright hairline along the top (never a live blur: there is none to be had over a
 * playing picture, and the TCL can't spare it); cards are rounded and lift when pressed or chosen; the writing is Inter,
 * white, with three softer greys under it; the pill chosen is white with dark writing. The sizes are the 07 mock-ups'
 * taken down to a phone (about half a TV pixel to a dp), the timings theirs.
 */
final class TvLook {
    private TvLook() {}

    /** The ground (07's #0b0b0f), the three greys for writing under white, amber for now, the live red. */
    static final int GROUND = 0xFF0B0B0F, L1 = 0xFFFFFFFF, L2 = 0xBDEBEBF5, L3 = 0x85EBEBF5, L4 = 0x4DEBEBF5,
            AMBER = 0xFFFFB547, RED = 0xFFFF453A, INK = 0xFF111111;
    /** Glass on the ground (a white wash), its rim, and the progress line's track. */
    static final int GLASS = 0x1FFFFFFF, GLASS_ON = 0x2EFFFFFF, RIM = 0x1AFFFFFF, TOP = 0x42FFFFFF, TRACK = 0x42FFFFFF;
    /** 07's "mat-dark": the panels over a picture, top to foot. */
    static final int MAT_TOP = 0xBD24242A, MAT_FOOT = 0xD6121216;
    /** The same, deeper, where it lies over busy writing (the categories over the guide). */
    static final int MAT_DEEP_TOP = 0xEB24242A, MAT_DEEP_FOOT = 0xF2121216;
    /** A card's lift when pressed or chosen (07: .4 s, cubic-bezier(.25,.9,.3,1.12)). */
    static final long LIFT_MS = 400, FADE_MS = 700;
    static final float PRESS_SCALE = 1.03f;

    /** 07's ease: quick out of the blocks, a touch past and back. */
    static Interpolator ease() {
        return new PathInterpolator(0.25f, 0.9f, 0.3f, 1.12f);
    }

    /** 07's second ease, without the overshoot. */
    static Interpolator ease2() {
        return new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    }

    static Typeface inter(Context c) {
        return Type.inter(c);
    }

    static Typeface semi(Context c) {
        return Type.interSemi(c);
    }

    /** Writing in Inter: semibold for titles and buttons, regular for the rest. */
    static TextView text(Context c, String s, float sp, int colour, boolean semi) {
        TextView t = Ui.text(c, s, sp, colour, false);
        t.setTypeface(semi ? semi(c) : inter(c));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A piece of glass (a white wash, brighter at its top, its hairline and rim), with the touch ripple. */
    static RippleDrawable glass(Context c, float radiusDp) {
        return touch(c, new Pane(c, radiusDp, GLASS + 0x0A000000, GLASS - 0x08000000, true), radiusDp);
    }

    /** The pill chosen: white, for dark writing. */
    static RippleDrawable white(Context c, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), Ui.round(c, 0xF0FFFFFF, radiusDp), Ui.round(c, L1, radiusDp));
    }

    /** A panel over a picture or the ground: 07's mat-dark, its hairline along the top. */
    static Pane mat(Context c, float radiusDp) {
        return new Pane(c, radiusDp, MAT_TOP, MAT_FOOT, true);
    }

    /** As mat, deeper (over writing that would show through). */
    static Pane deep(Context c, float radiusDp) {
        return new Pane(c, radiusDp, MAT_DEEP_TOP, MAT_DEEP_FOOT, true);
    }

    /** A round button of dark glass over the picture (the player's). */
    static RippleDrawable dark(Context c, float radiusDp) {
        return touch(c, new Pane(c, radiusDp, 0x8C2A2A32, 0xA6141418, true), radiusDp);
    }

    static RippleDrawable touch(Context c, Drawable base, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, Ui.round(c, L1, radiusDp));
    }

    /** A white pill (the chosen) or glass, as 07's pills, inset to 40dp to see within 48 to touch. */
    static void pill(Context c, TextView t, boolean on) {
        int inset = Ui.dp(c, 4);
        t.setBackground(new InsetDrawable(on ? white(c, 20) : glass(c, 20), 0, inset, 0, inset));
        t.setTextColor(on ? INK : L1);
        t.setTypeface(semi(c));
        t.setSelected(on);
    }

    /** A progress line's colours: white on a soft track. */
    static void line(android.widget.ProgressBar p) {
        p.setProgressTintList(ColorStateList.valueOf(L1));
        p.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
    }

    /** Pure: how far through a programme now is, 0 to 1. */
    static float progress(long start, long stop, long now) {
        if (stop <= start) return 0f;
        return Math.max(0f, Math.min(1f, (now - start) / (float) (stop - start)));
    }

    /**
     * A panel: a soft gradient, top to foot, a bright hairline along its top (the light catching its edge) and a faint
     * rim. Its outline is its rounded box, so a view it backs casts a soft shadow when raised.
     */
    static final class Pane extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
                top = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final float radius, one;
        private final int from, to;
        private final boolean hairline;
        private float[] radii;

        Pane(Context c, float radiusDp, int from, int to, boolean hairline) {
            float d = c.getResources().getDisplayMetrics().density;
            radius = radiusDp * d;
            one = Math.max(1f, d * 0.75f);
            this.from = from;
            this.to = to;
            this.hairline = hairline;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(one);
            rim.setColor(RIM);
            top.setStyle(Paint.Style.STROKE);
            top.setStrokeWidth(one);
        }

        /** Corners each their own (top left, top right, bottom right, bottom left), in px. */
        Pane corners(float tl, float tr, float br, float bl) {
            radii = new float[]{tl, tl, tr, tr, br, br, bl, bl};
            return this;
        }

        float radius() {
            return radius;
        }

        @Override protected void onBoundsChange(@NonNull Rect b) {
            if (b.isEmpty()) return;
            fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, from, to, Shader.TileMode.CLAMP));
            float h = Math.min(b.height(), radius * 2 + one * 4);
            top.setShader(new LinearGradient(0, b.top, 0, b.top + h, TOP, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        @Override public void draw(@NonNull Canvas cv) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            box.set(b);
            float r = Math.min(radius, Math.min(b.width(), b.height()) / 2f);
            if (radii != null) {
                android.graphics.Path p = path(b);
                cv.drawPath(p, fill);
                cv.drawPath(p, rim);
                if (hairline) cv.drawPath(p, top);
                return;
            }
            cv.drawRoundRect(box, r, r, fill);
            box.inset(one / 2, one / 2);
            cv.drawRoundRect(box, r, r, rim);
            if (hairline) cv.drawRoundRect(box, r, r, top);
        }

        private android.graphics.Path laid;
        private final Rect laidFor = new Rect();

        private android.graphics.Path path(Rect b) {
            if (laid == null || !laidFor.equals(b)) {
                laidFor.set(b);
                laid = new android.graphics.Path();
                laid.addRoundRect(new RectF(b), radii, android.graphics.Path.Direction.CW);
            }
            return laid;
        }

        @Override public void getOutline(@NonNull Outline o) {
            Rect b = getBounds();
            if (radii != null) {
                o.setRoundRect(b, Math.max(radii[0], radii[2]));
                return;
            }
            o.setRoundRect(b, Math.min(radius, Math.min(b.width(), b.height()) / 2f));
        }

        @Override public void setAlpha(int a) {
            fill.setAlpha(a);
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
