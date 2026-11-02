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
            {"Cocomelon", "Cocomelon nursery rhymes", 0xFF8BD450, 0xFF2E9E5B}};

    private KidsArt() { }

    /** YouTube on topic n's search; YouTube's own home if the search cannot be opened. */
    static void open(HomeV2 h, int n) {
        String q = (String) TOPICS[Math.max(0, Math.min(n, TOPICS.length - 1))][1];
        Intent search = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
                .setPackage(YOUTUBE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            h.a.startActivity(search);
            GoalWatchService.onAppOpened(h.a, YOUTUBE);
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

    private static String longest(String s) {
        String best = "";
        for (String part : s.split(" ")) if (part.length() > best.length()) best = part;
        return best;
    }
}
