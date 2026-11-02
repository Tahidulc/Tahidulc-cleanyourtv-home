package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.util.Random;

/**
 * The Kids page's YouTube cards, drawn by the launcher (YouTube has no posters to give): a bright gradient with soft
 * confetti, YouTube's own icon from the TV, the card's name and "on YouTube". YouTube Kids is not offered on every TV
 * (not on this TCL), so each card opens YouTube itself on a search made for children (its results page, which YouTube's
 * TV app opens from a link). Asked for as a picture address, "ytkids:p:3" (topic 3 as a poster), "ytkids:w:3" (wide,
 * for the widened card) or "ytkids:b:3" (the picture at the top of the page, without its words).
 */
final class KidsArt {
    static final String YOUTUBE = "com.google.android.youtube.tv";

    /** {name on the card, what YouTube is asked for, top colour, bottom colour}. 0 leads the Kids page's Top picks. */
    static final Object[][] TOPICS = {
            {"YouTube for kids", "cartoons for kids", 0xFFFF5E5B, 0xFFFFC93C},
            {"Cartoons", "cartoons for kids", 0xFFFF7A45, 0xFFFFD23F},
            {"Nursery rhymes", "nursery rhymes for kids", 0xFF4CC9F0, 0xFF7B2FF7},
            {"Learning", "learning videos for kids", 0xFF3DDC84, 0xFF1E9A86},
            {"Kids songs", "kids songs", 0xFFFF4FA3, 0xFF9B5CFF},
            {"Bluey", "Bluey official", 0xFF5AB2FF, 0xFF1F5FBF},
            {"Peppa Pig", "Peppa Pig official", 0xFFFF8AC2, 0xFFE0457B},
            {"PAW Patrol", "PAW Patrol official", 0xFF2F80ED, 0xFFE53935},
            {"Hey Duggee", "Hey Duggee official", 0xFFFFB238, 0xFF7A4A1F},
            {"Cocomelon", "Cocomelon nursery rhymes", 0xFF8BD450, 0xFF2E9E5B},
            // The Bangla tab (from BANGLA on): rhymes, the alphabet, stories and cartoons in Bangla, asked for in Bangla.
            {"Bangla for kids", "বাংলা ছড়া ও কার্টুন for kids", 0xFF006A4E, 0xFFF42A41},
            {"Bangla rhymes", "বাংলা ছড়া bangla rhymes for kids", 0xFF0F9D58, 0xFFFFC93C},
            {"Bangla alphabet", "স্বরবর্ণ ব্যঞ্জনবর্ণ bangla alphabet for kids", 0xFFF42A41, 0xFFFF9F43},
            {"Bangla stories", "বাংলা গল্প bangla cartoon golpo for kids", 0xFF7B2FF7, 0xFF006A4E},
            {"Bangla numbers", "বাংলা সংখ্যা ১ থেকে ১০ for kids", 0xFF2F80ED, 0xFF3DDC84},
            {"Thakurmar Jhuli", "ঠাকুরমার ঝুলি cartoon", 0xFFE0457B, 0xFF7A4A1F},
            {"Meena", "Meena cartoon bangla", 0xFFFF8AC2, 0xFF1E9A86},
            {"Sisimpur", "Sisimpur সিসিমপুর", 0xFFFFB238, 0xFF0F9D58},
            {"Bangla songs", "বাংলা শিশুতোষ গান kids songs", 0xFFFF4FA3, 0xFF006A4E}};

    /** Where the Bangla topics start in TOPICS: BANGLA leads the Bangla tab (and Top picks); the rest follow it. */
    static final int BANGLA = 10;

    private KidsArt() { }

    /** YouTube on topic n's search; YouTube's own home if the search cannot be opened. */
    static void open(HomeV2 h, int n) {
        String q = (String) TOPICS[Math.max(0, Math.min(n, TOPICS.length - 1))][1];
        Intent search = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
                .setPackage(YOUTUBE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            h.a.startActivity(search);
            GoalWatchService.onAppOpened(h.a, YOUTUBE);
            KidsNight.opened(h.a);
        } catch (Exception e) {
            h.openApp(YOUTUBE);
        }
    }

    static Bitmap draw(Context c, String url, int width) {
        String[] parts = url.split(":");
        String shape = parts.length > 1 ? parts[1] : "p";
        int n = 0;
        try { n = parts.length > 2 ? Integer.parseInt(parts[2]) : 0; } catch (NumberFormatException ignored) { }
        n = Math.max(0, Math.min(n, TOPICS.length - 1));
        boolean poster = shape.startsWith("p"), bare = shape.startsWith("b");
        int w = Math.max(64, width), h = poster ? Math.round(w * 1.5f) : Math.round(w * 9f / 16f);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int top = (Integer) TOPICS[n][2], bottom = (Integer) TOPICS[n][3];
        p.setShader(new LinearGradient(0, 0, w, h, top, bottom, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.75f, h * 0.2f, Math.max(w, h) * 0.6f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // Confetti: soft dots and rings, each card its own.
        Random r = new Random(7 + n);
        int[] colours = {0x66FFFFFF, 0x44FFFFFF, 0x33FFFFFF, 0x55FFFFFF};
        for (int i = 0; i < 24; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = (0.008f + r.nextFloat() * 0.022f) * Math.max(w, h);
            p.setColor(colours[i % colours.length]);
            p.setStyle(i % 3 == 0 ? Paint.Style.STROKE : Paint.Style.FILL);
            p.setStrokeWidth(rad * 0.35f);
            cv.drawCircle(x, y, rad, p);
        }
        p.setStyle(Paint.Style.FILL);
        float unit = Math.min(w, h);
        // YouTube's own icon from the TV (a white badge with a red play sign if it cannot be read).
        float badge = unit * (poster ? 0.38f : bare ? 0.38f : 0.32f);
        float bx = (w - badge) / 2f, by = poster ? h * 0.22f : bare ? (h - badge) / 2f : h * 0.14f;
        RectF box = new RectF(bx, by, bx + badge, by + badge);
        p.setColor(0x33000000);
        cv.drawRoundRect(new RectF(box.left, box.top + unit * 0.02f, box.right, box.bottom + unit * 0.02f), badge * 0.24f, badge * 0.24f, p);
        Drawable icon = null;
        try {
            icon = HomeV2.fullSquare(c, c.getPackageManager().getApplicationIcon(YOUTUBE), Math.round(badge));
        } catch (Exception ignored) {
            icon = null;
        }
        if (icon != null) {
            int save = cv.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(box, badge * 0.24f, badge * 0.24f, android.graphics.Path.Direction.CW);
            cv.clipPath(clip);
            icon.setBounds(Math.round(box.left), Math.round(box.top), Math.round(box.right), Math.round(box.bottom));
            icon.draw(cv);
            cv.restoreToCount(save);
        } else {
            p.setColor(0xFFFFFFFF);
            cv.drawRoundRect(box, badge * 0.24f, badge * 0.24f, p);
            float pw = badge * 0.52f, ph = badge * 0.36f, px = box.centerX() - pw / 2f, py = box.centerY() - ph / 2f;
            p.setColor(0xFFFF2D2D);
            cv.drawRoundRect(new RectF(px, py, px + pw, py + ph), ph * 0.3f, ph * 0.3f, p);
            android.graphics.Path tri = new android.graphics.Path();
            float tx = box.centerX() - ph * 0.18f, ts = ph * 0.42f;
            tri.moveTo(tx, box.centerY() - ts / 2f);
            tri.lineTo(tx + ts * 0.9f, box.centerY());
            tri.lineTo(tx, box.centerY() + ts / 2f);
            tri.close();
            p.setColor(0xFFFFFFFF);
            cv.drawPath(tri, p);
        }
        if (bare) return b;
        // The card's name (on two lines on a poster when it is two words), and "on YouTube".
        Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold);
        p.setTypeface(heavy);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(unit * 0.02f, 0, unit * 0.008f, 0x40000000);
        String name = (String) TOPICS[n][0];
        float size = unit * (poster ? 0.14f : 0.11f);
        p.setTextSize(size);
        while (p.measureText(name.contains(" ") && poster ? longest(name) : name) > w * 0.86f && size > unit * 0.06f) {
            size *= 0.92f;
            p.setTextSize(size);
        }
        float ty = box.bottom + unit * (poster ? 0.19f : 0.16f);
        if (poster && name.contains(" ")) {
            int cut = name.indexOf(' ');
            cv.drawText(name.substring(0, cut), w / 2f, ty, p);
            ty += size * 1.05f;
            cv.drawText(name.substring(cut + 1), w / 2f, ty, p);
        } else {
            cv.drawText(name, w / 2f, ty, p);
        }
        p.clearShadowLayer();
        p.setTypeface(c.getResources().getFont(R.font.inter_semibold));
        p.setTextSize(unit * (poster ? 0.062f : 0.05f));
        p.setColor(0xF2FFFFFF);
        cv.drawText("on YouTube", w / 2f, ty + unit * (poster ? 0.11f : 0.085f), p);
        return b;
    }

    /**
     * The Kids page's two shows the launcher draws for the top of the page (KidsPlay), at the hero's shape (1400 x 720):
     * "space", a night of stars and coloured nebulae with a ringed planet, a moon and a little rocket; "dance", a party
     * with string lights, a glitter ball, spotlights and a shining floor.
     */
    static Bitmap scene(Context c, String kind, int width) {
        int w = Math.max(200, width), h = Math.round(w * 720f / 1400f);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(kind.hashCode());
        if ("dance".equals(kind)) dance(cv, p, r, w, h); else if ("night".equals(kind)) night(cv, p, r, w, h); else space(cv, p, r, w, h);
        return b;
    }

    /** Bedtime: a deep blue night, a big glowing moon, stars, soft clouds, rolling hills and a little house lit up. */
    private static void night(Canvas cv, Paint p, Random r, int w, int h) {
        float k = w / 1400f;
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF050A2E, 0xFF13205E, 0xFF2D3C86}, null, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        glow(cv, p, w * 0.30f, h * 0.30f, w * 0.30f, 0x334C6FFF);
        for (int i = 0; i < 260; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.72f, rad = (0.5f + r.nextFloat() * r.nextFloat() * 1.8f) * k;
            int a = 80 + r.nextInt(170);
            if (rad > 1.6f * k) glow(cv, p, x, y, rad * 5f, (a / 3) << 24 | 0xFFFFFF);
            p.setColor(a << 24 | (r.nextInt(5) == 0 ? 0xFFF1C2 : 0xFFFFFF));
            cv.drawCircle(x, y, rad, p);
        }
        // The moon, top right: a crescent with its glow (the dark part the sky's own colour).
        float mx = w * 0.80f, my = h * 0.26f, mr = 78 * k;
        glow(cv, p, mx, my, mr * 3.2f, 0x55FFE9A8);
        p.setColor(0xFFFFF1C4);
        cv.drawCircle(mx, my, mr, p);
        p.setColor(0x22D9B86A);
        cv.drawCircle(mx - mr * 0.25f, my + mr * 0.2f, mr * 0.18f, p);
        cv.drawCircle(mx + mr * 0.2f, my - mr * 0.3f, mr * 0.12f, p);
        p.setColor(0xFF0E1A52);
        cv.drawCircle(mx + mr * 0.42f, my - mr * 0.18f, mr * 0.86f, p);
        // Soft clouds drifting across, see-through.
        float[][] clouds = {{0.18f, 0.22f, 1f}, {0.55f, 0.14f, 0.8f}, {0.92f, 0.48f, 0.7f}, {0.40f, 0.42f, 0.6f}};
        for (float[] cl : clouds) {
            float cx = w * cl[0], cy = h * cl[1], s = 70 * k * cl[2];
            p.setColor(0x2EFFFFFF);
            cv.drawCircle(cx, cy, s, p);
            cv.drawCircle(cx - s * 1.1f, cy + s * 0.25f, s * 0.7f, p);
            cv.drawCircle(cx + s * 1.1f, cy + s * 0.2f, s * 0.75f, p);
            cv.drawRoundRect(new RectF(cx - s * 1.8f, cy + s * 0.1f, cx + s * 1.8f, cy + s * 0.75f), s * 0.35f, s * 0.35f, p);
        }
        // Two rolling hills, the far one paler.
        android.graphics.Path hill = new android.graphics.Path();
        hill.moveTo(0, h * 0.80f);
        hill.cubicTo(w * 0.25f, h * 0.66f, w * 0.45f, h * 0.84f, w * 0.70f, h * 0.70f);
        hill.cubicTo(w * 0.85f, h * 0.62f, w * 0.95f, h * 0.70f, w, h * 0.68f);
        hill.lineTo(w, h);
        hill.lineTo(0, h);
        hill.close();
        p.setColor(0xFF1E2E6E);
        cv.drawPath(hill, p);
        hill.reset();
        hill.moveTo(0, h * 0.90f);
        hill.cubicTo(w * 0.30f, h * 0.80f, w * 0.60f, h * 0.95f, w, h * 0.84f);
        hill.lineTo(w, h);
        hill.lineTo(0, h);
        hill.close();
        p.setColor(0xFF121D4E);
        cv.drawPath(hill, p);
        // The little house on the far hill, its windows warm.
        float hx = w * 0.70f, hy = h * 0.70f, hw = 92 * k, hh = 66 * k;
        p.setColor(0xFF0B1440);
        cv.drawRect(hx - hw / 2f, hy - hh, hx + hw / 2f, hy + 4 * k, p);
        android.graphics.Path roof = new android.graphics.Path();
        roof.moveTo(hx - hw * 0.62f, hy - hh);
        roof.lineTo(hx, hy - hh * 1.75f);
        roof.lineTo(hx + hw * 0.62f, hy - hh);
        roof.close();
        cv.drawPath(roof, p);
        glow(cv, p, hx, hy - hh * 0.5f, hw * 1.3f, 0x40FFC163);
        p.setColor(0xFFFFC870);
        cv.drawRect(hx - hw * 0.34f, hy - hh * 0.78f, hx - hw * 0.08f, hy - hh * 0.42f, p);
        cv.drawRect(hx + hw * 0.08f, hy - hh * 0.78f, hx + hw * 0.34f, hy - hh * 0.42f, p);
        // A round tree beside it.
        p.setColor(0xFF0B1440);
        cv.drawRect(hx - hw * 1.25f, hy - hh * 0.6f, hx - hw * 1.15f, hy + 4 * k, p);
        cv.drawCircle(hx - hw * 1.2f, hy - hh * 0.95f, 34 * k, p);
    }

    private static void glow(Canvas cv, Paint p, float x, float y, float rad, int colour) {
        p.setShader(new RadialGradient(x, y, rad, colour, colour & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(x, y, rad, p);
        p.setShader(null);
    }

    private static void space(Canvas cv, Paint p, Random r, int w, int h) {
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF070726, 0xFF15103F, 0xFF2A1050}, null, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // Nebulae, soft and coloured.
        glow(cv, p, w * 0.68f, h * 0.38f, w * 0.42f, 0x669B5CFF);
        glow(cv, p, w * 0.48f, h * 0.78f, w * 0.30f, 0x4D20C9C9);
        glow(cv, p, w * 0.93f, h * 0.82f, w * 0.26f, 0x47FF5EC8);
        glow(cv, p, w * 0.30f, h * 0.20f, w * 0.22f, 0x334CC9F0);
        // Stars, a few with a glow.
        for (int i = 0; i < 320; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = (0.4f + r.nextFloat() * r.nextFloat() * 1.9f) * w / 1400f;
            int a = 90 + r.nextInt(165);
            if (rad > 1.5f * w / 1400f) glow(cv, p, x, y, rad * 5f, (a / 3) << 24 | 0xFFFFFF);
            p.setColor(a << 24 | (r.nextInt(5) == 0 ? 0xFFE7B5 : r.nextInt(4) == 0 ? 0xBFD9FF : 0xFFFFFF));
            cv.drawCircle(x, y, rad, p);
        }
        float k = w / 1400f;
        // A ringed planet, top right: the ring's far side behind it, the near side in front.
        float px = w * 0.83f, py = h * 0.27f, pr = 92 * k;
        RectF ring = new RectF(px - pr * 1.9f, py - pr * 0.5f, px + pr * 1.9f, py + pr * 0.5f);
        cv.save();
        cv.rotate(-14, px, py);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(16 * k);
        p.setColor(0xCCFFD9A0);
        cv.drawArc(ring, 180, 180, false, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(px - pr, py - pr, px + pr, py + pr, new int[]{0xFFFFC07A, 0xFFFF7A6B, 0xFFB0408F}, null, Shader.TileMode.CLAMP));
        cv.drawCircle(px, py, pr, p);
        p.setShader(null);
        cv.save();
        android.graphics.Path clip = new android.graphics.Path();
        clip.addCircle(px, py, pr, android.graphics.Path.Direction.CW);
        cv.clipPath(clip);
        p.setColor(0x33FFFFFF);
        for (int i = -2; i <= 2; i++) cv.drawRect(px - pr, py + i * pr * 0.36f - pr * 0.07f, px + pr, py + i * pr * 0.36f + pr * 0.07f, p);
        p.setColor(0x55000000);
        cv.drawCircle(px + pr * 0.45f, py + pr * 0.45f, pr * 1.05f, p);
        cv.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xE6FFE2B0);
        cv.drawArc(ring, 0, 180, false, p);
        p.setStyle(Paint.Style.FILL);
        cv.restore();
        // A moon with its craters.
        float mx = w * 0.57f, my = h * 0.17f, mr = 38 * k;
        glow(cv, p, mx, my, mr * 2.4f, 0x33FFFFFF);
        p.setColor(0xFFE8E6F0);
        cv.drawCircle(mx, my, mr, p);
        p.setColor(0xFFC9C5D8);
        cv.drawCircle(mx - mr * 0.35f, my - mr * 0.2f, mr * 0.22f, p);
        cv.drawCircle(mx + mr * 0.3f, my + mr * 0.35f, mr * 0.16f, p);
        cv.drawCircle(mx + mr * 0.4f, my - mr * 0.35f, mr * 0.1f, p);
        // A small blue-green world, low on the right.
        float ex = w * 0.97f, ey = h * 0.86f, er = 120 * k;
        glow(cv, p, ex, ey, er * 1.35f, 0x5540A0FF);
        p.setShader(new LinearGradient(ex - er, ey - er, ex + er, ey + er, 0xFF3C8DFF, 0xFF123A8A, Shader.TileMode.CLAMP));
        cv.drawCircle(ex, ey, er, p);
        p.setShader(null);
        p.setColor(0xFF3DBE6E);
        cv.drawOval(new RectF(ex - er * 0.7f, ey - er * 0.55f, ex - er * 0.1f, ey - er * 0.05f), p);
        cv.drawOval(new RectF(ex - er * 0.2f, ey + er * 0.1f, ex + er * 0.4f, ey + er * 0.6f), p);
        // A little rocket on its way up, flame and all.
        rocket(cv, p, w * 0.70f, h * 0.60f, 1.0f * k, -32);
    }

    private static void rocket(Canvas cv, Paint p, float x, float y, float k, float tilt) {
        cv.save();
        cv.translate(x, y);
        cv.rotate(tilt);
        float bw = 26 * k, bh = 74 * k;
        android.graphics.Path flame = new android.graphics.Path();
        flame.moveTo(-bw * 0.55f, bh * 0.5f);
        flame.quadTo(0, bh * 1.55f, bw * 0.55f, bh * 0.5f);
        flame.close();
        p.setShader(new LinearGradient(0, bh * 0.5f, 0, bh * 1.4f, 0xFFFFE066, 0x00FF5E2B, Shader.TileMode.CLAMP));
        cv.drawPath(flame, p);
        p.setShader(null);
        p.setColor(0xFFE53935);
        android.graphics.Path fins = new android.graphics.Path();
        fins.moveTo(-bw * 0.5f, bh * 0.1f);
        fins.lineTo(-bw * 1.05f, bh * 0.55f);
        fins.lineTo(-bw * 0.5f, bh * 0.45f);
        fins.close();
        fins.moveTo(bw * 0.5f, bh * 0.1f);
        fins.lineTo(bw * 1.05f, bh * 0.55f);
        fins.lineTo(bw * 0.5f, bh * 0.45f);
        fins.close();
        cv.drawPath(fins, p);
        p.setColor(0xFFF4F4FA);
        cv.drawRoundRect(new RectF(-bw / 2f, -bh / 2f, bw / 2f, bh / 2f), bw / 2f, bw / 2f, p);
        p.setColor(0xFFE53935);
        android.graphics.Path nose = new android.graphics.Path();
        nose.moveTo(-bw / 2f, -bh * 0.3f);
        nose.quadTo(0, -bh * 0.85f, bw / 2f, -bh * 0.3f);
        nose.close();
        cv.drawPath(nose, p);
        p.setColor(0xFF4CC9F0);
        cv.drawCircle(0, -bh * 0.05f, bw * 0.24f, p);
        p.setColor(0x99FFFFFF);
        cv.drawCircle(-bw * 0.07f, -bh * 0.09f, bw * 0.08f, p);
        cv.restore();
    }

    private static void dance(Canvas cv, Paint p, Random r, int w, int h) {
        float k = w / 1400f;
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF140A33, 0xFF3A0F5C, 0xFF1A0730}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // Soft coloured lights out of focus.
        int[] bokeh = {0x40FF5EC8, 0x404CC9F0, 0x40FFC93C, 0x403DDC84, 0x409B5CFF};
        for (int i = 0; i < 46; i++) glow(cv, p, r.nextFloat() * w, r.nextFloat() * h * 0.7f, (10 + r.nextFloat() * 34) * k, bokeh[i % bokeh.length]);
        // Spotlights from above.
        int[] beams = {0x30FF5EC8, 0x304CC9F0, 0x30FFC93C, 0x309B5CFF};
        for (int i = 0; i < 4; i++) {
            float x = w * (0.38f + 0.18f * i);
            android.graphics.Path cone = new android.graphics.Path();
            cone.moveTo(x - 10 * k, 0);
            cone.lineTo(x + 10 * k, 0);
            cone.lineTo(x + 170 * k + (i - 1.5f) * 80 * k, h * 0.8f);
            cone.lineTo(x - 170 * k + (i - 1.5f) * 80 * k, h * 0.8f);
            cone.close();
            p.setShader(new LinearGradient(0, 0, 0, h * 0.8f, beams[i], beams[i] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawPath(cone, p);
        }
        p.setShader(null);
        // The floor, shining, with pools of coloured light.
        float floor = h * 0.74f;
        p.setShader(new LinearGradient(0, floor, 0, h, 0xFF2B0E4A, 0xFF0E0418, Shader.TileMode.CLAMP));
        cv.drawRect(0, floor, w, h, p);
        p.setShader(null);
        glow(cv, p, w * 0.55f, floor + 40 * k, 220 * k, 0x55FF5EC8);
        glow(cv, p, w * 0.80f, floor + 50 * k, 200 * k, 0x554CC9F0);
        glow(cv, p, w * 0.98f, floor + 30 * k, 160 * k, 0x45FFC93C);
        // String lights along the top.
        android.graphics.Path wire = new android.graphics.Path();
        wire.moveTo(w * 0.3f, h * 0.05f);
        wire.quadTo(w * 0.62f, h * 0.24f, w * 1.02f, h * 0.03f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * k);
        p.setColor(0x99FFFFFF);
        cv.drawPath(wire, p);
        p.setStyle(Paint.Style.FILL);
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(wire, false);
        float[] at = new float[2];
        int[] bulbs = {0xFFFF5E5B, 0xFFFFC93C, 0xFF3DDC84, 0xFF4CC9F0, 0xFFFF8AC2, 0xFF9B5CFF};
        for (int i = 0; i <= 18; i++) {
            pm.getPosTan(pm.getLength() * i / 18f, at, null);
            glow(cv, p, at[0], at[1] + 8 * k, 20 * k, (bulbs[i % bulbs.length] & 0x00FFFFFF) | 0x66000000);
            p.setColor(bulbs[i % bulbs.length]);
            cv.drawCircle(at[0], at[1] + 8 * k, 6 * k, p);
        }
        // A glitter ball, hanging.
        float gx = w * 0.62f, gy = h * 0.17f, gr = 44 * k;
        p.setColor(0x99FFFFFF);
        cv.drawRect(gx - k, 0, gx + k, gy - gr, p);
        glow(cv, p, gx, gy, gr * 2.6f, 0x40FFFFFF);
        p.setShader(new RadialGradient(gx - gr * 0.3f, gy - gr * 0.3f, gr * 1.3f, 0xFFF2F2F8, 0xFF6F6F86, Shader.TileMode.CLAMP));
        cv.drawCircle(gx, gy, gr, p);
        p.setShader(null);
        cv.save();
        android.graphics.Path ball = new android.graphics.Path();
        ball.addCircle(gx, gy, gr, android.graphics.Path.Direction.CW);
        cv.clipPath(ball);
        p.setColor(0x55303045);
        float cell = gr / 4.5f;
        for (float y = gy - gr; y <= gy + gr; y += cell) cv.drawRect(gx - gr, y, gx + gr, y + 1.2f * k, p);
        for (float x = gx - gr; x <= gx + gr; x += cell) cv.drawRect(x, gy - gr, x + 1.2f * k, gy + gr, p);
        cv.restore();
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 7; i++) cv.drawCircle(gx + (r.nextFloat() - 0.5f) * gr * 1.4f, gy + (r.nextFloat() - 0.5f) * gr * 1.4f, 1.8f * k, p);
    }

    private static String longest(String s) {
        String best = "";
        for (String part : s.split(" ")) if (part.length() > best.length()) best = part;
        return best;
    }
}
