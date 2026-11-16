package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.view.View;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.App;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The TV's line-up card's look for the phone's line-ups (its LineupCard, as BoardLineups has it): its navy, its pale
 * ground and its pitch, each with the slanted streaks; and a player drawn as the line-ups show him, his live rating on
 * top. The pictures (faces and flags) are read once and kept for the session; the views showing them are told when one
 * comes.
 */
final class SheetArt {
    private SheetArt() {
    }

    static final int NAVY = 0xFF151A63, INK = 0xFF0A0D33, SOFT_INK = 0xFF2B3170, BAND_TOP = 0xFF1B2591, BAND_FOOT = 0xFF0D1360,
            GROUND_TOP = 0xFFF4F5FD, GROUND_FOOT = 0xFFC2C5EE, PITCH_TOP = 0xFF2A35B8, PITCH_FOOT = 0xFF080B3A, ON_GREEN = 0xFF1E8F4E;

    /** A streaked ground: a gradient top to foot, slanted streaks across it, its corners rounded where asked. */
    static final class Ground extends Drawable {
        private final int top, foot, streak;
        private final float gap, width, radius;
        private final boolean roundTop, roundFoot;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), lines = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path(), path = new Path();
        private final RectF box = new RectF();

        Ground(int top, int foot, int streak, float gap, float width, float radius, boolean roundTop, boolean roundFoot) {
            this.top = top;
            this.foot = foot;
            this.streak = streak;
            this.gap = gap;
            this.width = width;
            this.radius = radius;
            this.roundTop = roundTop;
            this.roundFoot = roundFoot;
            lines.setColor(streak);
        }

        @Override protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            box.set(b);
            float t = roundTop ? radius : 0, f = roundFoot ? radius : 0;
            clip.reset();
            clip.addRoundRect(box, new float[]{t, t, t, t, f, f, f, f}, Path.Direction.CW);
            fill.setShader(b.isEmpty() ? null : new LinearGradient(0, b.top, 0, b.bottom, top, foot, Shader.TileMode.CLAMP));
            // The streaks, slanted as the TV card's (a third of the height across).
            path.reset();
            if (streak == 0 || b.isEmpty()) return;
            float slant = b.height() * 0.36f;
            for (float x = b.left - slant; x < b.right + slant; x += gap + width) {
                path.moveTo(x, b.bottom);
                path.lineTo(x + width, b.bottom);
                path.lineTo(x + width + slant, b.top);
                path.lineTo(x + slant, b.top);
                path.close();
            }
        }

        @Override public void draw(Canvas c) {
            if (getBounds().isEmpty()) return;
            c.save();
            c.clipPath(clip);
            c.drawRect(box, fill);
            if (streak != 0) c.drawPath(path, lines);
            c.restore();
        }

        @Override public void setAlpha(int alpha) {
        }

        @Override public void setColorFilter(ColorFilter cf) {
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** The navy band (a sheet's header), rounded at its top. */
    static Ground band(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return new Ground(BAND_TOP, BAND_FOOT, 0x12FFFFFF, 26 * d, 4 * d, 20 * d, true, false);
    }

    /** The pale streaked ground (the substitutes and the manager), rounded at its foot. */
    static Ground pale(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        return new Ground(GROUND_TOP, GROUND_FOOT, 0x8CFFFFFF, 60 * d, 24 * d, 20 * d, false, true);
    }

    // ---- the pictures -----------------------------------------------------------------------------------------------

    private static final LruCache<String, Bitmap> PICTURES = new LruCache<>(160);
    private static final Set<String> ASKING = ConcurrentHashMap.newKeySet(), MISSING = ConcurrentHashMap.newKeySet();
    private static final List<WeakReference<View>> WATCHERS = Collections.synchronizedList(new ArrayList<>());

    /** A picture read already (a face or a flag, by its address or "name:" and a player's name); null when not yet. */
    static Bitmap picture(String key) {
        return key == null || key.isEmpty() ? null : PICTURES.get(key);
    }

    /** A picture found another way (a player's photo by his name): kept, and the views told. */
    static void put(String key, Bitmap b) {
        if (key == null || b == null) return;
        PICTURES.put(key, b);
        told();
    }

    /** A view showing pictures: drawn again whenever one comes (while it is still on the screen). */
    static void watch(View v) {
        synchronized (WATCHERS) {
            for (WeakReference<View> w : WATCHERS) if (w.get() == v) return;
            WATCHERS.removeIf(w -> w.get() == null);
            WATCHERS.add(new WeakReference<>(v));
        }
    }

    /** These pictures read (off the main thread, a few at a time), each once; those that are not there not asked again. */
    static void want(Context c, List<String> urls) {
        Context app = c.getApplicationContext();
        for (String url : urls) {
            if (url == null || !url.startsWith("http") || PICTURES.get(url) != null || MISSING.contains(url) || !ASKING.add(url)) continue;
            App.io.execute(() -> {
                Bitmap b = null;
                try {
                    b = Glide.with(app).asBitmap().load(url).submit(200, 200).get();
                } catch (Exception | Error ignored) {
                    // not there (or not now)
                }
                Bitmap got = b;
                App.main.post(() -> {
                    ASKING.remove(url);
                    if (got == null) {
                        MISSING.add(url);
                        return;
                    }
                    PICTURES.put(url, got);
                    told();
                });
            });
        }
    }

    private static void told() {
        List<View> live = new ArrayList<>();
        synchronized (WATCHERS) {
            for (WeakReference<View> w : WATCHERS) if (w.get() != null) live.add(w.get());
        }
        for (View v : live) v.postInvalidateOnAnimation();
    }

    // ---- a player -----------------------------------------------------------------------------------------------------

    /** What a view needs to draw players: its paints, kept (none made while drawing). */
    static final class Painter {
        final float dp;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
                photo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix matrix = new Matrix();
        final RectF pill = new RectF();
        /** Each picture's shader, made once. */
        private final java.util.WeakHashMap<Bitmap, BitmapShader> shaders = new java.util.WeakHashMap<>();

        private BitmapShader shader(Bitmap b) {
            BitmapShader sh = shaders.get(b);
            if (sh == null) {
                sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                shaders.put(b, sh);
            }
            return sh;
        }

        Painter(Context c) {
            dp = c.getResources().getDisplayMetrics().density;
            stroke.setStyle(Paint.Style.STROKE);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
        }

        /**
         * A player at (cx, cy), r round: his picture (else his initials) in a disc on his kit's colour; his live rating on
         * top of it in its colour (a star on the best on the pitch); his number on a navy badge at the lower left, his
         * country's flag at the lower right; his goals and assists at the upper right, his cards at the upper left; an
         * arrow at his left when he went off (red) or came on (green, with sub true).
         */
        void face(Canvas c, TeamSheet.Man m, float cx, float cy, float r, int kit, boolean best, boolean sub) {
            fill.setShader(null);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x59000000);
            c.drawCircle(cx, cy + 1.5f * dp, r + 0.5f * dp, fill);
            fill.setColor(kit | 0xFF000000);
            c.drawCircle(cx, cy, r, fill);
            Bitmap pic = picture(m.photo);
            if (pic != null) {
                // The picture's top across the disc (a face sits in its top half), the rest cut off by the circle.
                float k = (2 * r) / Math.max(1, pic.getWidth());
                BitmapShader sh = shader(pic);
                matrix.setScale(k, k);
                matrix.postTranslate(cx - r, cy - r);
                sh.setLocalMatrix(matrix);
                photo.setShader(sh);
                c.drawCircle(cx, cy, r, photo);
                photo.setShader(null);
            } else {
                text.setColor(Glyphs.ink(kit | 0xFF000000));
                text.setTextSize(r * 0.78f);
                Paint.FontMetrics fm = text.getFontMetrics();
                c.drawText(TeamSheet.initials(m.name), cx, cy - (fm.ascent + fm.descent) / 2f, text);
            }
            stroke.setStrokeWidth(1.6f * dp);
            stroke.setColor(Glyphs.luminance(kit | 0xFF000000) > 0.6 && pic == null ? 0x66000000 : 0xE6FFFFFF);
            c.drawCircle(cx, cy, r - 0.8f * dp, stroke);

            float glyph = Math.max(11 * dp, r * 0.52f);
            // Goals (up to three, an own goal after them) and assists at the upper right.
            float gx = cx + r * 0.92f, gy = cy - r * 0.62f;
            int balls = Math.min(m.goals, 3);
            for (int i = 0; i < balls; i++, gx += glyph * 0.62f) Glyphs.draw(c, Glyphs.GOAL, gx, gy, glyph);
            if (m.own > 0) {
                Glyphs.draw(c, Glyphs.OWN_GOAL, gx, gy, glyph);
                gx += glyph * 0.62f;
            }
            for (int i = 0; i < Math.min(m.assists, 2); i++, gx += glyph * 0.62f) assist(c, gx, gy, glyph / 2f);
            // Cards at the upper left; off (or on) at the left.
            if (m.red > 0 || m.yellow > 0) {
                int kind = m.red > 0 && m.yellow > 0 ? Glyphs.SECOND_YELLOW : m.red > 0 ? Glyphs.RED : Glyphs.YELLOW;
                Glyphs.draw(c, kind, cx - r * 0.95f, cy - r * 0.62f, glyph);
            }
            if (sub ? !m.on.isEmpty() : !m.off.isEmpty()) Glyphs.draw(c, sub ? Glyphs.ON : Glyphs.OFF, cx - r * 1.02f, cy + r * 0.08f, glyph);
            // His number at the lower left, his flag at the lower right.
            if (!m.number.isEmpty()) {
                float br = Math.max(7.5f * dp, r * 0.36f), bx = cx - r * 0.74f, by = cy + r * 0.7f;
                fill.setColor(NAVY);
                c.drawCircle(bx, by, br, fill);
                stroke.setStrokeWidth(1.1f * dp);
                stroke.setColor(0xE6FFFFFF);
                c.drawCircle(bx, by, br, stroke);
                text.setColor(0xFFFFFFFF);
                text.setTextSize(br * (m.number.length() > 2 ? 0.86f : 1.06f));
                Paint.FontMetrics fm = text.getFontMetrics();
                c.drawText(m.number, bx, by - (fm.ascent + fm.descent) / 2f, text);
            }
            Bitmap flag = picture(m.flag);
            if (flag != null) {
                float fr = Math.max(6.5f * dp, r * 0.3f), fx = cx + r * 0.76f, fy = cy + r * 0.72f;
                float k = (2 * fr) / Math.max(1, Math.min(flag.getWidth(), flag.getHeight()));
                BitmapShader sh = shader(flag);
                matrix.setScale(k, k);
                matrix.postTranslate(fx - flag.getWidth() * k / 2f, fy - flag.getHeight() * k / 2f);
                sh.setLocalMatrix(matrix);
                photo.setShader(sh);
                c.drawCircle(fx, fy, fr, photo);
                photo.setShader(null);
                stroke.setStrokeWidth(1f * dp);
                stroke.setColor(0xE6FFFFFF);
                c.drawCircle(fx, fy, fr, stroke);
            }
            // His live rating on top, in its colour.
            if (!m.rating.isEmpty()) {
                String shown = best ? "★" + m.rating : m.rating;
                float h = Math.max(14 * dp, r * 0.66f);
                text.setTextSize(h * 0.7f);
                float w = text.measureText(shown) + h * 0.62f;
                pill.set(cx - w / 2f, cy - r - h * 0.62f, cx + w / 2f, cy - r + h * 0.38f);
                fill.setColor(Ratings.colour(m.rating) | 0xFF000000);
                c.drawRoundRect(pill, h / 2f, h / 2f, fill);
                stroke.setStrokeWidth(1.2f * dp);
                stroke.setColor(0xF2FFFFFF);
                c.drawRoundRect(pill, h / 2f, h / 2f, stroke);
                text.setColor(0xFFFFFFFF);
                Paint.FontMetrics fm = text.getFontMetrics();
                c.drawText(shown, pill.centerX(), pill.centerY() - (fm.ascent + fm.descent) / 2f, text);
            }
        }

        /** An assist: a white disc with an "A" in navy. */
        private void assist(Canvas c, float cx, float cy, float rr) {
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, rr, fill);
            stroke.setStrokeWidth(0.8f * dp);
            stroke.setColor(NAVY);
            c.drawCircle(cx, cy, rr, stroke);
            text.setColor(NAVY);
            text.setTextSize(rr * 1.3f);
            Paint.FontMetrics fm = text.getFontMetrics();
            c.drawText("A", cx, cy - (fm.ascent + fm.descent) / 2f, text);
        }
    }
}
