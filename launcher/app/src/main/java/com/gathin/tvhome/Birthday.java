package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Birthday mode (the theme's kids_birthdays: "Name|2021-05-14", or "Name|05-14" without the year): on a child's
 * birthday, the first time Home comes up that day, the jungle-party scene fills the screen, the three children
 * (KidsPlay's cartoons) bounce in along the bottom under falling confetti and balloons, "Happy birthday, Name!" with
 * their age, and Happy Birthday's first notes; any key closes it (it goes by itself after 15 s). The Kids page opens on
 * the party for them too (KidsPlay).
 */
final class Birthday {
    private Birthday() { }

    /** Today's birthday: {name, age or ""}; null when none. */
    static String[] today(Context c) {
        LocalDate now = LocalDate.now();
        for (String entry : Config.themeStrings(c, "kids_birthdays", new ArrayList<>())) {
            String[] p = entry.split("\\|", 2);
            if (p.length < 2) continue;
            String d = p[1].trim();
            try {
                int month, day, year = 0;
                String[] parts = d.split("-");
                if (parts.length == 3) { year = Integer.parseInt(parts[0]); month = Integer.parseInt(parts[1]); day = Integer.parseInt(parts[2]); }
                else { month = Integer.parseInt(parts[0]); day = Integer.parseInt(parts[1]); }
                if (now.getMonthValue() == month && now.getDayOfMonth() == day) {
                    return new String[]{p[0].trim(), year > 0 && year < now.getYear() ? String.valueOf(now.getYear() - year) : ""};
                }
            } catch (RuntimeException ignored) {
                // a date that cannot be read: skipped
            }
        }
        return null;
    }

    /** Home came up: on a birthday, the greeting the first time that day. */
    static void maybeGreet(HomeV2 h) {
        String[] b = today(h.a);
        if (b == null || shown != null || !NotifyPrefs.allowed(h.a, "birthday", true)) return;
        SharedPreferences p = h.a.getSharedPreferences("birthday", Context.MODE_PRIVATE);
        String key = LocalDate.now() + "|" + b[0];
        if (p.getBoolean(key, false)) return;
        p.edit().putBoolean(key, true).apply();
        greet(h, b[0], b[1]);
    }

    private static Party shown;

    static boolean isOpen() {
        return shown != null;
    }

    static boolean key(KeyEvent e) {
        if (shown == null) return false;
        if (e.getAction() == KeyEvent.ACTION_UP) shown.close();
        return true;
    }

    static void close() {
        if (shown != null) shown.close();
    }

    /** The greeting, now (adb: --es test_birthday "Name|5"). */
    static void greet(HomeV2 h, String name, String age) {
        close();
        shown = new Party(h, name, age);
    }

    /** "Happy birthday": G G A G C B, as soft bells. */
    private static final double[] SONG = {392.0, 392.0, 440.0, 392.0, 523.25, 493.88};

    private static final class Party {
        final HomeV2 h;
        final FrameLayout overlay;
        final Confetti fun;
        final Runnable end = this::close;

        Party(HomeV2 h, String name, String age) {
            this.h = h;
            Context c = h.a;
            overlay = new FrameLayout(c);
            overlay.setBackgroundColor(0xFF0B3B2A);
            overlay.setClickable(true);
            // The jungle party (the children's own scene, when the TV has it), slowly zooming.
            File scene = null;
            for (File f : KidsPlay.scenes(c)) {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.contains("jungle") || n.contains("party")) { scene = f; break; }
            }
            if (scene != null) {
                ImageView bg = new ImageView(c);
                bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap b = BitmapFactory.decodeFile(scene.getAbsolutePath());
                if (b != null) bg.setImageBitmap(b);
                overlay.addView(bg, new FrameLayout.LayoutParams(-1, -1));
                bg.setScaleX(1.0f);
                bg.setScaleY(1.0f);
                bg.animate().scaleX(1.08f).scaleY(1.08f).setDuration(15_000).start();
            }
            View shade = new View(c);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000, 0x00000000, 0xB3000000}));
            overlay.addView(shade, new FrameLayout.LayoutParams(-1, -1));
            List<Bitmap> kids = new ArrayList<>();
            for (File f : KidsPlay.cast(c)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 1;
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (b == null) continue;
                int hgt = h.px(f.getName().contains("ride") ? 330 : f.getName().contains("sit") ? 270 : 360);
                kids.add(Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * hgt / (float) b.getHeight())), hgt, true));
                if (kids.size() == 3) break;
            }
            fun = new Confetti(c, kids, h.px(1000) / 1000f);
            overlay.addView(fun, new FrameLayout.LayoutParams(-1, -1));
            TextView title = h.text(92, Fonts.get(c, R.font.outfit_extrabold), Color.WHITE);
            title.setText("Happy birthday, " + name + "!");
            title.setGravity(Gravity.CENTER);
            title.setShadowLayer(h.px(18), 0, h.px(4), 0xAA000000);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            tlp.topMargin = h.px(110);
            overlay.addView(title, tlp);
            TextView sub = h.text(40, Fonts.get(c, R.font.inter_semibold), 0xFFFFE27A);
            sub.setText((age.isEmpty() ? "" : age + " today  🎂   ") + "Have the best day!");
            sub.setGravity(Gravity.CENTER);
            sub.setShadowLayer(h.px(12), 0, h.px(3), 0xAA000000);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            slp.topMargin = h.px(240);
            overlay.addView(sub, slp);
            h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(400).start();
            title.setScaleX(0.4f);
            title.setScaleY(0.4f);
            title.setAlpha(0f);
            title.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(350).setDuration(700).setInterpolator(new OvershootInterpolator(1.6f)).start();
            sub.setAlpha(0f);
            sub.setTranslationY(h.px(30));
            sub.animate().alpha(1f).translationY(0).setStartDelay(900).setDuration(600).start();
            DoorbellAlert.tones(c, SONG, 0.34f);
            overlay.postDelayed(end, 15_000L);
        }

        void close() {
            if (shown == this) shown = null;
            overlay.removeCallbacks(end);
            fun.stop();
            overlay.animate().alpha(0f).setDuration(350).withEndAction(() -> h.root.removeView(overlay)).start();
            h.paint();
        }
    }

    /** The children bouncing in along the bottom, confetti falling and balloons rising. */
    private static final class Confetti extends View {
        final List<Bitmap> kids;
        final float u;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random random = new Random();
        final long start = SystemClock.uptimeMillis();
        final List<float[]> bits = new ArrayList<>();   // {x, y, vx, vy, spin, colour, size, kind (0 confetti, 1 balloon)}
        final RectF oval = new RectF();
        boolean running = true;
        long last = start;
        static final int[] COLOURS = {0xFFFF5E5B, 0xFFFFC93C, 0xFF3DDC84, 0xFF4CC9F0, 0xFFFF8AC2, 0xFF9B5CFF, 0xFFFFFFFF};

        Confetti(Context c, List<Bitmap> kids, float u) {
            super(c);
            this.kids = kids;
            this.u = u;
        }

        void stop() {
            running = false;
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - last) / 1000f), t = (now - start) / 1000f;
            last = now;
            float W = getWidth(), H = getHeight();
            // New pieces: a burst at the start, then a steady fall; a balloon now and then.
            int want = t < 1.2f ? 12 : 2;
            for (int i = 0; i < want && bits.size() < 220; i++) {
                bits.add(new float[]{random.nextFloat() * W, -20 * u, (random.nextFloat() - 0.5f) * 80 * u, (160 + random.nextFloat() * 140) * u,
                        random.nextFloat() * 360, COLOURS[random.nextInt(COLOURS.length)], (8 + random.nextFloat() * 6) * u, 0});
            }
            if (random.nextFloat() < dt * 1.6f) {
                bits.add(new float[]{(0.05f + random.nextFloat() * 0.9f) * W, H + 60 * u, 0, -(90 + random.nextFloat() * 50) * u, 0,
                        COLOURS[random.nextInt(COLOURS.length - 1)], (34 + random.nextFloat() * 12) * u, 1});
            }
            for (int i = bits.size() - 1; i >= 0; i--) {
                float[] b = bits.get(i);
                b[0] += (b[2] + (float) Math.sin(t * 2 + i) * 20 * u) * dt;
                b[1] += b[3] * dt;
                b[4] += 240 * dt;
                if (b[1] > H + 40 * u || b[1] < -160 * u) { bits.remove(i); continue; }
                fill.setColor((int) b[5]);
                if (b[7] == 1) {
                    float s = b[6];
                    oval.set(b[0] - s, b[1] - s * 1.15f, b[0] + s, b[1] + s * 1.15f);
                    c.drawOval(oval, fill);
                    fill.setColor(0x66FFFFFF);
                    oval.set(b[0] - s * 0.58f, b[1] - s * 0.82f, b[0] - s * 0.18f, b[1] - s * 0.22f);
                    c.drawOval(oval, fill);
                    fill.setColor(0x99FFFFFF);
                    c.drawRect(b[0] - u, b[1] + s * 1.15f, b[0] + u, b[1] + s * 2.6f, fill);
                } else {
                    int save = c.save();
                    c.translate(b[0], b[1]);
                    c.rotate(b[4]);
                    c.scale((float) Math.cos(t * 7 + i), 1f);
                    c.drawRect(-b[6] / 2f, -b[6] * 0.8f, b[6] / 2f, b[6] * 0.8f, fill);
                    c.restoreToCount(save);
                }
            }
            // The children: in from below one after another, then hopping on the spot, a little out of step.
            int n = kids.size();
            for (int i = 0; i < n; i++) {
                Bitmap k = kids.get(i);
                float in = Math.max(0f, Math.min(1f, (t - 0.6f - i * 0.35f) / 0.7f));
                if (in <= 0f) continue;
                float rise = (1f - (float) Math.pow(1f - in, 3)) ;
                float x = W * (n == 1 ? 0.5f : 0.22f + 0.56f * i / (n - 1));
                float hop = Math.abs((float) Math.sin(t * 3.2f + i * 1.1f)) * 34 * u * in;
                float y = H + k.getHeight() * (1f - rise) - 30 * u - hop;
                float rot = (float) Math.sin(t * 3.2f + i * 1.1f) * 5f;
                fill.setColor(Color.argb(Math.round(70 * in), 0, 0, 0));
                oval.set(x - k.getWidth() * 0.32f, H - 38 * u, x + k.getWidth() * 0.32f, H - 22 * u);
                c.drawOval(oval, fill);
                int save = c.save();
                c.translate(x, y);
                c.rotate(rot);
                c.drawBitmap(k, -k.getWidth() / 2f, -k.getHeight(), paint);
                c.restoreToCount(save);
            }
            if (running) postInvalidateOnAnimation();
        }
    }
}
