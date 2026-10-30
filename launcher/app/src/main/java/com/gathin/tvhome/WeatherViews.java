package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** The weather card's moving pictures: its sky, the week's ranges, the pressure dial, the sun's path and the moon. */
final class WeatherViews {
    private WeatherViews() {
    }

    /** The sky's family: 0 clear, 1 cloud, 2 fog, 3 rain, 4 snow, 5 storm. */
    static int kind(int code) {
        if (code <= 1) return 0;
        if (code <= 3) return 1;
        if (code == 45 || code == 48) return 2;
        if (code >= 95) return 5;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return 4;
        if (code >= 51) return 3;
        return 1;
    }

    /** Draws its moving parts, then asks for the next frame: each one on a quick TV, every other on a slower one. */
    abstract static class Moving extends View {
        final boolean lite;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final long born = SystemClock.uptimeMillis();

        Moving(Context c, boolean lite) {
            super(c);
            this.lite = lite;
        }

        float seconds() {
            return (SystemClock.uptimeMillis() - born) / 1000f;
        }

        void next() {
            if (lite) postInvalidateDelayed(33); else postInvalidateOnAnimation();
        }
    }

    // ---- the sky ---------------------------------------------------------------------------------------------------

    /**
     * The weather, alive: the sun turning its rays with dust in the light; the moon in its real phase among twinkling
     * stars, a shooting star now and then; two layers of cloud drifting at their own speeds; rain falling, splashing
     * at the foot and running down the glass; snowflakes turning as they fall; fog rolling; lightning forking down
     * with a flash; and in a wind, gusts and leaves blowing across.
     */
    static final class Sky extends Moving {
        private final int kind;
        private final boolean day, windy;
        private final double moonAge;
        private final Random rnd = new Random(11);
        private float[][] drops, stars, far, near, beads, splashes, dust, gusts, leaves;
        private long flashAt = -1, shotAt = -1;
        private float shotX, shotY;
        private Path bolt;
        private Bitmap puff;
        private final RectF box = new RectF();

        Sky(Context c, int code, boolean day, boolean windy, double moonAge, boolean lite) {
            super(c, lite);
            this.kind = kind(code);
            this.day = day;
            this.windy = windy;
            this.moonAge = moonAge;
        }

        private int n(int quick) {
            return lite ? quick / 2 : quick;
        }

        private float[][] scatter(int count, int fields, int w, int hgt) {
            float[][] out = new float[count][fields];
            for (float[] p : out) {
                p[0] = rnd.nextFloat() * w;
                p[1] = rnd.nextFloat() * hgt;
                for (int i = 2; i < fields; i++) p[i] = rnd.nextFloat();
            }
            return out;
        }

        private void seed(int w, int hgt) {
            boolean wet = kind == 3 || kind == 5;
            drops = scatter(wet ? n(150) : kind == 4 ? n(110) : 0, 4, w, hgt);
            stars = scatter(!day && kind <= 1 ? n(90) : 0, 3, w, Math.round(hgt * 0.65f));
            far = scatter(kind == 0 ? 1 : kind == 1 ? 3 : 4, 3, w, Math.round(hgt * 0.3f));
            near = scatter(kind == 0 ? 0 : kind == 1 ? 3 : 4, 3, w, Math.round(hgt * 0.35f));
            beads = scatter(wet ? n(36) : 0, 4, w, hgt);
            splashes = new float[wet ? n(24) : 0][3];
            for (float[] s : splashes) s[2] = -10;
            dust = scatter(kind == 0 && day ? n(40) : 0, 4, w, hgt);
            gusts = scatter(windy ? n(10) : 0, 3, w, hgt);
            leaves = scatter(windy && kind <= 1 ? n(8) : 0, 4, w, hgt);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hgt = getHeight();
            if (w == 0) return;
            if (drops == null) seed(w, hgt);
            float t = seconds();
            long ms = SystemClock.uptimeMillis();
            if (kind <= 1) {
                if (day) sun(c, w * 0.95f, -hgt * 0.03f, w, t);
                else nightSky(c, w, hgt, t, ms);
            }
            for (float[] cl : far) cloud(c, (cl[0] + t * 6f * (0.6f + cl[2])) % (w + 500f) - 400f, cl[1], 0.6f + 0.3f * cl[2], 0x66);
            if (kind == 2) fog(c, w, hgt, t);
            for (float[] cl : near) cloud(c, (cl[0] + t * 16f * (0.7f + cl[2])) % (w + 800f) - 650f, cl[1] + hgt * 0.05f, 1.0f + 0.5f * cl[2], 0xA6);
            if (kind == 3 || kind == 5) rain(c, w, hgt, t);
            if (kind == 4) snow(c, w, hgt, t);
            if (kind == 5) lightning(c, w, hgt, ms);
            if (gusts.length > 0) wind(c, w, hgt, t);
            dust(c, hgt, t);
            next();
        }

        private void sun(Canvas c, float x, float y, int w, float t) {
            float r = w * 0.06f * (1f + 0.03f * (float) Math.sin(t * 1.3f));
            // Rays turning slowly about it, faint, as light through haze.
            c.save();
            c.rotate(t * 6f, x, y);
            paint.setShader(null);
            Path ray = new Path();
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2 / 9;
                ray.reset();
                ray.moveTo(x, y);
                ray.lineTo(x + (float) Math.cos(a - 0.09) * r * 9, y + (float) Math.sin(a - 0.09) * r * 9);
                ray.lineTo(x + (float) Math.cos(a + 0.09) * r * 9, y + (float) Math.sin(a + 0.09) * r * 9);
                ray.close();
                paint.setColor(Color.argb(Math.round(18 + 10 * (float) Math.sin(t * 0.8f + i)), 255, 248, 220));
                c.drawPath(ray, paint);
            }
            c.restore();
            paint.setColor(Color.WHITE);
            paint.setShader(new RadialGradient(x, y, r * 4.5f, new int[]{0x80FFF3C4, 0x26FFE9A0, 0x00FFE9A0}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 4.5f, paint);
            paint.setShader(new RadialGradient(x, y, r, new int[]{0xFFFFFDF0, 0xFFFFE08A}, null, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, paint);
            paint.setShader(null);
        }

        private void dust(Canvas c, int hgt, float t) {
            for (float[] d : dust) {
                float y = hgt - ((hgt - d[1] + t * 12f * (0.4f + d[2])) % hgt);
                float x = d[0] + (float) Math.sin(t * 0.7f + d[3] * 6f) * 20f;
                paint.setColor(Color.argb(Math.round(40 + 70 * (0.5f + 0.5f * (float) Math.sin(t * 2f + d[2] * 9f))), 255, 250, 225));
                c.drawCircle(x, y, 1.5f + d[2] * 2f, paint);
            }
        }

        private void nightSky(Canvas c, int w, int hgt, float t, long ms) {
            for (float[] s : stars) {
                paint.setColor(Color.argb(Math.round(100 + 120 * (0.5f + 0.5f * (float) Math.sin(t * (1f + s[2]) + s[2] * 9f))), 255, 255, 255));
                c.drawCircle(s[0], s[1], 1.2f + s[2] * 1.3f, paint);
            }
            // A shooting star every several seconds, its tail fading behind it.
            if (shotAt < 0 || ms - shotAt > 7000 + rnd.nextInt(5000)) {
                shotAt = ms;
                shotX = w * (0.15f + rnd.nextFloat() * 0.5f);
                shotY = hgt * (0.05f + rnd.nextFloat() * 0.2f);
            }
            float since = (ms - shotAt) / 1000f;
            if (since < 0.9f) {
                float hx = shotX + since * 700f, hy = shotY + since * 260f;
                float a = since < 0.7f ? 1f : (0.9f - since) / 0.2f;
                paint.setStrokeWidth(2.5f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setShader(new LinearGradient(hx, hy, hx - 160, hy - 60, Color.argb(Math.round(255 * a), 255, 255, 255), 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawLine(hx, hy, hx - 160, hy - 60, paint);
                paint.setShader(null);
            }
            moon(c, w * 0.86f, hgt * 0.14f, 46f, moonAge, paint, t);
        }

        private Bitmap puff() {
            if (puff != null) return puff;
            puff = Bitmap.createBitmap(560, 260, Bitmap.Config.ARGB_8888);
            Canvas pc = new Canvas(puff);
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            float[][] puffs = {{120, 160, 110}, {230, 110, 130}, {350, 140, 115}, {440, 170, 85}, {270, 180, 110}};
            boolean grey = kind >= 3;
            for (float[] p : puffs) {
                int core = grey ? 0xB3E4E9F0 : 0xB3FFFFFF, mid = grey ? 0x66D5DCE6 : 0x66FFFFFF;
                pp.setShader(new RadialGradient(p[0], p[1], p[2], new int[]{core, mid, 0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                pc.drawCircle(p[0], p[1], p[2], pp);
            }
            return puff;
        }

        private void cloud(Canvas c, float x, float y, float k, int alpha) {
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setAlpha(kind >= 3 ? Math.round(alpha * 0.85f) : alpha);
            paint.setFilterBitmap(true);
            box.set(x, y, x + 560 * k, y + 260 * k);
            c.drawBitmap(puff(), null, box, paint);
            paint.setAlpha(255);
        }

        private void fog(Canvas c, int w, int hgt, float t) {
            for (int i = 0; i < 4; i++) {
                float y = hgt * (0.35f + 0.17f * i);
                float x = (float) Math.sin(t * 0.22f + i * 1.7f) * 120f;
                paint.setColor(Color.WHITE);
                paint.setShader(new LinearGradient(0, y - 70, 0, y + 70, new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                c.drawRect(x - 150, y - 70, w + 150 + x, y + 70, paint);
            }
            paint.setShader(null);
        }

        private void rain(Canvas c, int w, int hgt, float t) {
            boolean heavy = kind == 5;
            paint.setShader(null);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(2f);
            int si = 0;
            for (float[] d : drops) {
                float fall = (d[1] + t * (heavy ? 1150f : 900f) * (0.6f + d[2] * 0.8f)) % (hgt + 60f) - 30f;
                float x = (d[0] - fall * 0.14f) % w;
                if (x < 0) x += w;
                paint.setColor(Color.argb(Math.round(70 + 90 * d[3]), 205, 228, 255));
                c.drawLine(x, fall, x - 5f, fall + 18f + 18f * d[2], paint);
                // Reaching the foot: a splash where it lands.
                if (fall > hgt - 40 && si < splashes.length && splashes[si][2] < t - 0.45f) {
                    splashes[si][0] = x;
                    splashes[si][1] = hgt - 8 - rnd.nextFloat() * 22;
                    splashes[si][2] = t;
                }
                si = (si + 1) % Math.max(1, splashes.length);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f);
            for (float[] s : splashes) {
                float age = t - s[2];
                if (age < 0 || age > 0.45f) continue;
                float k = age / 0.45f;
                paint.setColor(Color.argb(Math.round(150 * (1 - k)), 220, 235, 255));
                box.set(s[0] - 4 - 16 * k, s[1] - 2 - 4 * k, s[0] + 4 + 16 * k, s[1] + 2 + 4 * k);
                c.drawOval(box, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            // Drops on the glass: still, then one runs down, then another.
            for (float[] b : beads) {
                float run = b[2] > 0.7f ? ((t * 40f * b[3] + b[1]) % (hgt + 40f)) : b[1];
                float r = 3f + b[3] * 5f;
                paint.setColor(0x33FFFFFF);
                c.drawCircle(b[0], run, r, paint);
                paint.setColor(0x80FFFFFF);
                c.drawCircle(b[0] - r * 0.35f, run - r * 0.35f, r * 0.3f, paint);
                if (b[2] > 0.7f) {
                    paint.setColor(0x14FFFFFF);
                    c.drawRect(b[0] - r * 0.4f, run - 120, b[0] + r * 0.4f, run - r, paint);
                }
            }
        }

        private void snow(Canvas c, int w, int hgt, float t) {
            paint.setShader(null);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (float[] d : drops) {
                float y = (d[1] + t * 60f * (0.5f + d[2])) % (hgt + 20f) - 10f;
                float x = d[0] + (float) Math.sin(t * 1.1f + d[3] * 6.28f) * 22f;
                float size = 2.5f + 4f * d[2];
                paint.setColor(Color.argb(Math.round(170 + 80 * d[3]), 255, 255, 255));
                if (size < 5f) { c.drawCircle(x, y, size, paint); continue; }
                // The bigger flakes: six arms, turning.
                paint.setStrokeWidth(1.6f);
                c.save();
                c.rotate(t * 40f * (d[3] - 0.5f) * 2f, x, y);
                for (int i = 0; i < 3; i++) {
                    c.drawLine(x - size * 1.4f, y, x + size * 1.4f, y, paint);
                    c.rotate(60, x, y);
                }
                c.restore();
            }
        }

        private void lightning(Canvas c, int w, int hgt, long ms) {
            if (flashAt < 0 || ms - flashAt > 4500 + rnd.nextInt(3500)) {
                flashAt = ms;
                bolt = new Path();
                float x = w * (0.25f + rnd.nextFloat() * 0.5f), y = 0;
                bolt.moveTo(x, y);
                while (y < hgt * 0.62f) {
                    y += 30 + rnd.nextFloat() * 40;
                    x += (rnd.nextFloat() - 0.5f) * 70;
                    bolt.lineTo(x, y);
                    if (rnd.nextFloat() < 0.18f) {
                        // A fork off to one side.
                        bolt.lineTo(x + (rnd.nextFloat() - 0.5f) * 140, y + 50 + rnd.nextFloat() * 60);
                        bolt.moveTo(x, y);
                    }
                }
            }
            float since = (ms - flashAt) / 1000f;
            float a = since < 0.08f ? 0.5f : since < 0.16f ? 0.12f : since < 0.24f ? 0.38f : Math.max(0f, 0.38f - (since - 0.24f) * 1.5f);
            if (a <= 0) return;
            paint.setShader(null);
            paint.setColor(Color.argb(Math.round(a * 255), 220, 225, 255));
            c.drawRect(0, 0, w, hgt, paint);
            if (since < 0.3f && bolt != null) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStrokeWidth(12f);
                paint.setColor(Color.argb(Math.round(a * 180), 170, 190, 255));
                c.drawPath(bolt, paint);
                paint.setStrokeWidth(3.5f);
                paint.setColor(Color.argb(Math.round(Math.min(1f, a * 2.5f) * 255), 255, 255, 255));
                c.drawPath(bolt, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        private void wind(Canvas c, int w, int hgt, float t) {
            paint.setShader(null);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(2f);
            for (float[] g : gusts) {
                float x = (g[0] + t * 520f * (0.6f + g[2])) % (w + 400f) - 200f;
                float y = g[1] + (float) Math.sin(t * 2f + g[2] * 6f) * 10f;
                paint.setColor(Color.argb(Math.round(40 + 40 * g[2]), 255, 255, 255));
                c.drawLine(x, y, x + 120 + 80 * g[2], y - 6, paint);
            }
            for (float[] l : leaves) {
                float x = (l[0] + t * 260f * (0.6f + l[2])) % (w + 100f) - 50f;
                float y = l[1] + (float) Math.sin(t * 2.4f + l[3] * 6f) * 40f;
                c.save();
                c.rotate(t * 220f * (l[3] - 0.5f), x, y);
                paint.setColor(l[2] > 0.5f ? 0xCCD98A3D : 0xCCB5652A);
                box.set(x - 9, y - 4, x + 9, y + 4);
                c.drawOval(box, paint);
                c.restore();
            }
        }
    }

    // ---- the moon --------------------------------------------------------------------------------------------------

    static final double SYNODIC = 29.530588853;

    /** Days since the new moon, now. */
    static double moonAge(long millis) {
        double jd = millis / 86_400_000.0 + 2440587.5;
        double age = (jd - 2451550.1) % SYNODIC;
        return age < 0 ? age + SYNODIC : age;
    }

    static String moonName(double age) {
        if (age < 1.85) return "New moon";
        if (age < 5.54) return "Waxing crescent";
        if (age < 9.23) return "First quarter";
        if (age < 12.92) return "Waxing gibbous";
        if (age < 16.61) return "Full moon";
        if (age < 20.30) return "Waning gibbous";
        if (age < 23.99) return "Last quarter";
        if (age < 27.68) return "Waning crescent";
        return "New moon";
    }

    static double illumination(double age) {
        return (1 - Math.cos(2 * Math.PI * age / SYNODIC)) / 2;
    }

    /** The moon at (x, y), lit as it is tonight (from the right as it waxes, the left as it wanes), glowing softly. */
    static void moon(Canvas c, float x, float y, float r, double age, Paint p, float t) {
        moon(c, x, y, r, r * 3.2f, age, p, t);
    }

    static void moon(Canvas c, float x, float y, float r, float glow, double age, Paint p, float t) {
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setShader(new RadialGradient(x, y, glow, new int[]{Color.argb(Math.round(50 + 14 * (float) Math.sin(t)), 220, 230, 255), 0x00DDE6FF},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, glow, p);
        p.setShader(null);
        p.setColor(0x40202A40);
        c.drawCircle(x, y, r, p);
        double phase = 2 * Math.PI * age / SYNODIC;
        float k = (float) Math.cos(phase);   // 1 new, 0 half, -1 full
        boolean waxing = age < SYNODIC / 2;
        Path lit = new Path();
        RectF disc = new RectF(x - r, y - r, x + r, y + r);
        RectF term = new RectF(x - r * Math.abs(k), y - r, x + r * Math.abs(k), y + r);
        lit.arcTo(disc, -90, waxing ? 180 : -180);
        // Back up the terminator: inside the lit half for a crescent, out across the dark half for a gibbous moon.
        boolean crescent = k > 0;
        lit.arcTo(term, 90, (waxing == crescent) ? -180 : 180);
        lit.close();
        p.setColor(Color.WHITE);
        p.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.4f, 0xFFF6F3EA, 0xFFB9C2D2, Shader.TileMode.CLAMP));
        c.drawPath(lit, p);
        p.setShader(null);
        // Its seas, faintly.
        c.save();
        c.clipPath(lit);
        p.setColor(0x268A93A8);
        c.drawCircle(x - r * 0.3f, y - r * 0.2f, r * 0.28f, p);
        c.drawCircle(x + r * 0.25f, y + r * 0.3f, r * 0.2f, p);
        c.drawCircle(x + r * 0.35f, y - r * 0.35f, r * 0.14f, p);
        c.drawCircle(x - r * 0.1f, y + r * 0.45f, r * 0.12f, p);
        c.restore();
    }

    /** The moon on its own, for the moon card: turning very slowly into view. */
    static final class Moon extends Moving {
        private final double age;

        Moon(Context c, double age, boolean lite) {
            super(c, lite);
            this.age = age;
        }

        @Override protected void onDraw(Canvas c) {
            float half = Math.min(getWidth(), getHeight()) / 2f, r = half * 0.62f;
            // The glow kept inside the view, fading out before its edge.
            moon(c, getWidth() / 2f, getHeight() / 2f, r, half, age, paint, seconds());
            next();
        }
    }

    // ---- bars and dials --------------------------------------------------------------------------------------------

    /** A value from 0 to 1, animated in: draw(c, shown) paints it at that much of the way. */
    abstract static class Growing extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float grown;

        Growing(Context c) {
            super(c);
        }

        void grow(long delay, long duration) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setStartDelay(delay);
            a.setDuration(duration);
            a.setInterpolator(HomeV2.SKY);
            a.addUpdateListener(an -> { grown = (float) an.getAnimatedValue(); invalidate(); });
            a.start();
        }
    }

    static int tempColour(double t) {
        if (t < 2) return 0xFF7DD3FC;
        if (t < 7) return 0xFF60A5FA;
        if (t < 12) return 0xFF34D399;
        if (t < 17) return 0xFFFACC15;
        if (t < 22) return 0xFFFB923C;
        return 0xFFF87171;
    }

    /** A day's low-to-high on the week's scale, cool to warm; today's with a dot where it is now. */
    static final class Range extends Growing {
        private final double min, max, low, high, now;

        Range(Context c, double min, double max, double low, double high, double now) {
            super(c);
            this.min = min;
            this.max = max;
            this.low = low;
            this.high = high;
            this.now = now;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hgt = getHeight(), cy = hgt / 2f, th = hgt * 0.5f, rad = th / 2;
            paint.setShader(null);
            paint.setColor(0x33000000);
            c.drawRoundRect(0, cy - th / 2, w, cy + th / 2, rad, rad, paint);
            double span = Math.max(1, max - min);
            float x0 = (float) ((low - min) / span) * w, x1 = (float) ((high - min) / span) * w;
            float end = x0 + Math.max(th, x1 - x0) * grown;
            paint.setColor(Color.WHITE);
            paint.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, x1), 0, tempColour(low), tempColour(high), Shader.TileMode.CLAMP));
            c.drawRoundRect(x0, cy - th / 2, end, cy + th / 2, rad, rad, paint);
            paint.setShader(null);
            if (!Double.isNaN(now) && grown >= 1f) {
                float nx = (float) ((now - min) / span) * w;
                paint.setColor(0xFF1A1D24);
                c.drawCircle(nx, cy, hgt * 0.5f, paint);
                paint.setColor(Color.WHITE);
                c.drawCircle(nx, cy, hgt * 0.36f, paint);
            }
        }
    }

    /** A low-to-high scale with a dot sliding along to where the value sits (visibility, air quality). */
    static final class Slider extends Growing {
        private final float at;
        private final int[] colours;

        Slider(Context c, float at, int[] colours) {
            super(c);
            this.at = Math.max(0f, Math.min(1f, at));
            this.colours = colours;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hgt = getHeight(), cy = hgt / 2f, th = Math.max(4f, hgt * 0.32f), dot = hgt * 0.42f;
            paint.setColor(Color.WHITE);
            paint.setShader(new LinearGradient(dot, 0, w - dot, 0, colours, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(dot, cy - th / 2, w - dot, cy + th / 2, th / 2, th / 2, paint);
            paint.setShader(null);
            float x = dot + (w - 2 * dot) * at * grown;
            paint.setColor(0x40000000);
            c.drawCircle(x, cy + 1.5f, dot + 1, paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(x, cy, dot, paint);
        }
    }

    /** The barometer: ticks round three-quarters of a circle, 970 to 1040 hPa, the needle sweeping to the reading. */
    static final class Gauge extends Growing {
        private final double hpa;

        Gauge(Context c, double hpa) {
            super(c);
            this.hpa = hpa;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hgt = getHeight(), cx = w / 2f, cy = hgt * 0.58f, r = Math.min(w / 2f, hgt * 0.55f) - 4;
            float frac = (float) Math.max(0, Math.min(1, (hpa - 970) / 70.0));
            float shown = frac * grown;
            paint.setStrokeCap(Paint.Cap.ROUND);
            int ticks = 36;
            for (int i = 0; i <= ticks; i++) {
                float f = i / (float) ticks;
                double a = Math.toRadians(135 + 270 * f);
                boolean lit = Math.abs(f - shown) < 0.05f;
                paint.setStrokeWidth(lit ? 3.5f : 2.5f);
                paint.setColor(lit ? Color.WHITE : 0x59FFFFFF);
                float in = r * (i % 6 == 0 ? 0.78f : 0.84f);
                c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, paint);
            }
            double a = Math.toRadians(135 + 270 * shown);
            // The pointer: on the ring of ticks, clear of the reading in the middle.
            paint.setStrokeWidth(5f);
            paint.setColor(Color.WHITE);
            c.drawLine(cx + (float) Math.cos(a) * r * 0.7f, cy + (float) Math.sin(a) * r * 0.7f,
                    cx + (float) Math.cos(a) * r * 1.04f, cy + (float) Math.sin(a) * r * 1.04f, paint);
        }
    }

    /**
     * The sun's path today, horizon to horizon: dashed where it has still to go, gold where it has been, and the sun
     * travelling along to where it is now (resting at the horizon before sunrise or after sunset).
     */
    static final class SunPath extends Growing {
        private final float at;

        SunPath(Context c, float at) {
            super(c);
            this.at = Math.max(0f, Math.min(1f, at));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hgt = getHeight(), base = hgt * 0.82f, pad = 10;
            RectF arc = new RectF(pad, base - (hgt * 0.78f), w - pad, base + (hgt * 0.78f));
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(0x73FFFFFF);
            paint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));
            c.drawArc(arc, 180, 180, false, paint);
            paint.setPathEffect(null);
            float shown = at * grown;
            paint.setColor(0xFFFFD86B);
            paint.setStrokeWidth(3.5f);
            c.drawArc(arc, 180, 180 * shown, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x4DFFFFFF);
            c.drawRect(0, base, w, base + 2, paint);
            double a = Math.toRadians(180 + 180 * shown);
            float sx = arc.centerX() + (float) Math.cos(a) * arc.width() / 2, sy = arc.centerY() + (float) Math.sin(a) * arc.height() / 2;
            paint.setShader(new RadialGradient(sx, sy, 26, 0x99FFE08A, 0x00FFE08A, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, 26, paint);
            paint.setShader(null);
            paint.setColor(0xFFFFE9A8);
            c.drawCircle(sx, sy, 9, paint);
        }
    }

    /** The big icon beside the temperature: bobbing gently, the sun's turning slowly too. */
    static void alive(View icon, boolean spin, java.util.List<android.animation.Animator> running) {
        android.animation.ObjectAnimator bob = android.animation.ObjectAnimator.ofFloat(icon, "translationY", -8f, 8f);
        bob.setDuration(2400);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        bob.start();
        running.add(bob);
        if (spin) {
            android.animation.ObjectAnimator turn = android.animation.ObjectAnimator.ofFloat(icon, "rotation", 0f, 360f);
            turn.setDuration(24_000);
            turn.setRepeatCount(ValueAnimator.INFINITE);
            turn.setInterpolator(new android.view.animation.LinearInterpolator());
            turn.start();
            running.add(turn);
        }
    }
}
