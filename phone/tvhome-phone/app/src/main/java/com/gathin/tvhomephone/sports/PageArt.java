package com.gathin.tvhomephone.sports;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * What the club, player and driver pages draw for themselves: the ground in the club's colour with a soft light at
 * its middle, fine grain and a vignette (Ground); the cards' tall panel with its corners cut (Panel); a neat head and
 * shoulders in the club's colour where there is no photo (Bust); and an F1 car side on in the team's colour where
 * formula1.com has no picture of it (Car). Each builds its shaders when its size changes, never while it draws.
 */
final class PageArt {
    private PageArt() {}

    private static Bitmap noise;

    /** The grain: a small tile of light and dark specks, made once and shared (never recycled). */
    static synchronized Bitmap noise() {
        if (noise == null) {
            int n = 128;
            int[] px = new int[n * n];
            Random r = new Random(23);
            for (int i = 0; i < px.length; i++) {
                int a = r.nextInt(24);
                px[i] = (r.nextBoolean() ? 0x00FFFFFF : 0x00000000) | a << 24;
            }
            noise = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        return noise;
    }

    private abstract static class Art extends Drawable {
        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** The page's ground: the colour deep, lit softly at its middle, grained, darker towards the corners. */
    static final class Ground extends Art {
        private final int ground, glow;
        private final Paint base = new Paint(), light = new Paint(), grain = new Paint(), shade = new Paint();
        private final float grainScale;
        /** Where the light sits, as fractions of the width and height. */
        private final float lightX, lightY;

        Ground(int colour, float density, float lightX, float lightY) {
            ground = ClubLook.ground(colour);
            glow = ClubLook.glow(ground);
            base.setColor(ground);
            grainScale = Math.max(1f, density / 1.8f);
            this.lightX = lightX;
            this.lightY = lightY;
            BitmapShader s = new BitmapShader(noise(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Matrix m = new Matrix();
            m.setScale(grainScale, grainScale);
            s.setLocalMatrix(m);
            grain.setShader(s);
        }

        int ground() {
            return ground;
        }

        @Override protected void onBoundsChange(Rect b) {
            float w = Math.max(1, b.width()), h = Math.max(1, b.height());
            float r = Math.max(w, h) * 0.7f;
            light.setShader(new RadialGradient(b.left + w * lightX, b.top + h * lightY, r,
                    new int[]{glow, ClubLook.alpha(glow, 90), ClubLook.alpha(glow, 0)}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            float far = (float) Math.hypot(w, h) * 0.62f;
            shade.setShader(new RadialGradient(b.exactCenterX(), b.exactCenterY(), far,
                    new int[]{0, 0, 0x33000000, 0x99000000}, new float[]{0f, 0.5f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRect(b, base);
            c.drawRect(b, light);
            c.drawRect(b, grain);
            c.drawRect(b, shade);
        }

        /**
         * This ground turning into another (t: 0 this, 1 the other), as a page swipes to the next driver's colour: the
         * other's colour and light over this one's at t, the grain and vignette (the same for both) once. Nothing made.
         */
        void drawFaded(Canvas c, Ground other, float t) {
            Rect b = getBounds();
            c.drawRect(b, base);
            c.drawRect(b, light);
            if (other != null && t > 0.004f) {
                int a = Math.round(255 * Math.min(1f, t));
                other.base.setAlpha(a);
                other.light.setAlpha(a);
                c.drawRect(b, other.base);
                c.drawRect(b, other.light);
                other.base.setAlpha(255);
                other.light.setAlpha(255);
            }
            c.drawRect(b, grain);
            c.drawRect(b, shade);
        }
    }

    /** Two grounds, one fading into the other (the driver pages' as they swipe): set what they are and how far. */
    static final class Fade extends Art {
        private Ground from, to;
        private float t;

        void set(Ground from, Ground to, float t) {
            if (from != this.from && from != null) from.setBounds(getBounds());
            if (to != this.to && to != null) to.setBounds(getBounds());
            this.from = from;
            this.to = to;
            this.t = t;
            invalidateSelf();
        }

        @Override protected void onBoundsChange(Rect b) {
            if (from != null) from.setBounds(b);
            if (to != null) to.setBounds(b);
        }

        @Override public void draw(Canvas c) {
            if (from != null) from.drawFaded(c, to, t);
        }
    }

    /** A card's tall panel: its corners cut and softened, lighter at the top, a fine bright edge along it. */
    static final class Panel extends Art {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int top, bottom;

        Panel(int top, int bottom, float radiusPx) {
            this.top = top;
            this.bottom = bottom;
            fill.setPathEffect(new CornerPathEffect(radiusPx));
            rim.setPathEffect(new CornerPathEffect(radiusPx));
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, radiusPx / 10f));
        }

        @Override protected void onBoundsChange(Rect b) {
            float l = b.left, t = b.top, r = b.right, bt = b.bottom, w = b.width();
            float cut = w * 0.085f;
            path.reset();
            path.moveTo(l + cut, t);
            path.lineTo(r - cut, t);
            path.lineTo(r, t + cut);
            path.lineTo(r, bt - cut);
            path.lineTo(r - cut, bt);
            path.lineTo(l + cut, bt);
            path.lineTo(l, bt - cut);
            path.lineTo(l, t + cut);
            path.close();
            fill.setShader(new LinearGradient(0, t, 0, bt, new int[]{top, ClubLook.alpha(top, 0xB0), bottom},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(0, t, 0, t + b.height() * 0.6f, 0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            c.drawPath(path, fill);
            c.drawPath(path, rim);
        }
    }

    /** A head and shoulders in the club's colour, lit from above: in place of a photo there is none of. */
    static final class Bust extends Art {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shoulders = new Path();
        private final RectF head = new RectF();
        private final int light, dark;
        private float neckL, neckT, neckR, neckB;

        Bust(int colour) {
            int g = ClubLook.ground(colour);
            light = ClubLook.lighter(g, 0.30);
            dark = ClubLook.lighter(g, 0.12);
        }

        /** The share of its height above the head's crown (to line it up with the photos). */
        static final float CROWN = 0.13f;

        @Override protected void onBoundsChange(Rect b) {
            float w = b.width(), h = b.height(), x = b.left, y = b.top;
            head.set(x + w * 0.33f, y + h * CROWN, x + w * 0.67f, y + h * 0.53f);
            neckL = x + w * 0.43f;
            neckR = x + w * 0.57f;
            neckT = y + h * 0.46f;
            neckB = y + h * 0.66f;
            shoulders.reset();
            shoulders.moveTo(x + w * 0.04f, y + h);
            shoulders.cubicTo(x + w * 0.04f, y + h * 0.78f, x + w * 0.18f, y + h * 0.66f, x + w * 0.40f, y + h * 0.63f);
            shoulders.lineTo(x + w * 0.60f, y + h * 0.63f);
            shoulders.cubicTo(x + w * 0.82f, y + h * 0.66f, x + w * 0.96f, y + h * 0.78f, x + w * 0.96f, y + h);
            shoulders.close();
            paint.setShader(new LinearGradient(0, y, 0, y + h, light, dark, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            c.drawOval(head, paint);
            c.drawRect(neckL, neckT, neckR, neckB, paint);
            c.drawPath(shoulders, paint);
        }
    }

    /**
     * A driver standing, in the team's colour, lit from above: in place of his picture where there is none. Drawn on a
     * box about 2.6 times as tall as it is wide (head at the top, the legs to the box's foot).
     */
    static final class Figure extends Art {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path body = new Path();
        private final RectF head = new RectF();
        private final int light, dark;

        Figure(int colour) {
            int g = ClubLook.ground(colour);
            light = ClubLook.lighter(g, 0.26);
            dark = ClubLook.lighter(g, 0.08);
        }

        @Override protected void onBoundsChange(Rect b) {
            // A 100 by 260 drawing fitted to the box, kept in shape, its middle on the box's.
            float s = Math.min(b.width() / 100f, b.height() / 260f);
            float ox = b.exactCenterX() - 50 * s, oy = b.top;
            head.set(ox + 38 * s, oy + 2 * s, ox + 62 * s, oy + 32 * s);
            body.reset();
            float[][] pts = {{44, 30}, {56, 30}, {57, 38}, {74, 43}, {82, 52}, {86, 108}, {84, 132}, {77, 132}, {75, 110},
                    {72, 70}, {71, 128}, {68, 250}, {54, 252}, {50, 150}, {46, 252}, {32, 250}, {29, 128}, {28, 70}, {25, 110},
                    {23, 132}, {16, 132}, {14, 108}, {18, 52}, {26, 43}, {43, 38}};
            for (int i = 0; i < pts.length; i++) {
                float x = ox + pts[i][0] * s, y = oy + pts[i][1] * s;
                if (i == 0) body.moveTo(x, y);
                else body.lineTo(x, y);
            }
            body.close();
            paint.setPathEffect(new CornerPathEffect(6 * s));
            paint.setShader(new LinearGradient(0, oy, 0, oy + 260 * s, light, dark, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            c.drawOval(head, paint);
            c.drawPath(body, paint);
        }
    }

    /**
     * An F1 car side on, nose to the right, in the team's colour: the body with its engine cover and airbox, the halo,
     * the wings, the floor and four dark tyres (two seen). Drawn on a box about 4.3 times as wide as it is tall.
     */
    static final class Car extends Art {
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), dark = new Paint(Paint.ANTI_ALIAS_FLAG),
                tyre = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shell = new Path(), halo = new Path(), wingRear = new Path(), wingFront = new Path(), floor = new Path();
        private float rearX, frontX, wheelY, wheelR;
        private final int colour;

        Car(int colour) {
            this.colour = colour;
            dark.setColor(0xFF15161B);
            tyre.setColor(0xFF101114);
            rim.setColor(0xFF2B2D35);
            halo.setFillType(Path.FillType.EVEN_ODD);
        }

        @Override protected void onBoundsChange(Rect b) {
            // A 1000 by 232 drawing fitted to the box, kept in shape and set on its foot.
            float s = Math.min(b.width() / 1000f, b.height() / 232f);
            float ox = b.left + (b.width() - 1000 * s) / 2f, oy = b.bottom - 232 * s;
            shell.reset();
            pt(shell, true, ox, oy, s, 70, 168);
            pt(shell, false, ox, oy, s, 112, 128);
            pt(shell, false, ox, oy, s, 300, 112);
            pt(shell, false, ox, oy, s, 360, 66);
            pt(shell, false, ox, oy, s, 410, 58);
            pt(shell, false, ox, oy, s, 440, 92);
            pt(shell, false, ox, oy, s, 560, 104);
            pt(shell, false, ox, oy, s, 640, 112);
            pt(shell, false, ox, oy, s, 760, 140);
            pt(shell, false, ox, oy, s, 905, 166);
            pt(shell, false, ox, oy, s, 975, 178);
            pt(shell, false, ox, oy, s, 978, 190);
            pt(shell, false, ox, oy, s, 860, 196);
            pt(shell, false, ox, oy, s, 120, 196);
            shell.close();
            halo.reset();
            pt(halo, true, ox, oy, s, 470, 100);
            pt(halo, false, ox, oy, s, 500, 62);
            pt(halo, false, ox, oy, s, 610, 70);
            pt(halo, false, ox, oy, s, 620, 108);
            pt(halo, false, ox, oy, s, 604, 108);
            pt(halo, false, ox, oy, s, 598, 80);
            pt(halo, false, ox, oy, s, 508, 74);
            pt(halo, false, ox, oy, s, 486, 102);
            halo.close();
            wingRear.reset();
            pt(wingRear, true, ox, oy, s, 18, 36);
            pt(wingRear, false, ox, oy, s, 120, 36);
            pt(wingRear, false, ox, oy, s, 120, 58);
            pt(wingRear, false, ox, oy, s, 96, 58);
            pt(wingRear, false, ox, oy, s, 96, 150);
            pt(wingRear, false, ox, oy, s, 40, 150);
            pt(wingRear, false, ox, oy, s, 40, 58);
            pt(wingRear, false, ox, oy, s, 18, 58);
            wingRear.close();
            wingFront.reset();
            pt(wingFront, true, ox, oy, s, 870, 192);
            pt(wingFront, false, ox, oy, s, 996, 192);
            pt(wingFront, false, ox, oy, s, 996, 214);
            pt(wingFront, false, ox, oy, s, 880, 214);
            wingFront.close();
            floor.reset();
            pt(floor, true, ox, oy, s, 140, 194);
            pt(floor, false, ox, oy, s, 860, 194);
            pt(floor, false, ox, oy, s, 850, 206);
            pt(floor, false, ox, oy, s, 150, 206);
            floor.close();
            rearX = ox + 205 * s;
            frontX = ox + 790 * s;
            wheelY = oy + 168 * s;
            wheelR = 62 * s;
            body.setShader(new LinearGradient(0, oy + 50 * s, 0, oy + 200 * s, ClubLook.lighter(colour, 0.08), ClubLook.lighter(colour, -0.12),
                    Shader.TileMode.CLAMP));
            shine.setShader(new LinearGradient(0, oy + 60 * s, 0, oy + 130 * s, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        private static void pt(Path p, boolean first, float ox, float oy, float s, float x, float y) {
            if (first) p.moveTo(ox + x * s, oy + y * s);
            else p.lineTo(ox + x * s, oy + y * s);
        }

        @Override public void draw(Canvas c) {
            c.drawPath(wingRear, dark);
            c.drawPath(floor, dark);
            c.drawPath(shell, body);
            c.drawPath(shell, shine);
            c.drawPath(halo, dark);
            c.drawPath(wingFront, dark);
            c.drawCircle(rearX, wheelY, wheelR, tyre);
            c.drawCircle(rearX, wheelY, wheelR * 0.55f, rim);
            c.drawCircle(frontX, wheelY, wheelR, tyre);
            c.drawCircle(frontX, wheelY, wheelR * 0.55f, rim);
        }
    }
}
