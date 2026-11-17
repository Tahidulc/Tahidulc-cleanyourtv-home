package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;

import com.gathin.homefieldtv.engine.Catalog;

import java.util.HashMap;
import java.util.Map;

/**
 * Posters and wide tiles as Movies, TV shows and their lists draw them: the artwork itself, edge to edge (its own name is
 * in it), TorBox's cloud on the family's own titles and a small 4K where a copy is 4K; a wide tile with its name and how
 * far it was watched (the red bar) over a dark foot. Where the remote is, it grows (GROW) and takes a white edge. Each
 * picture is read once at its grown size, so it stays sharp as it grows; nothing is made while drawing but a name cut
 * to its tile the first time it is drawn without its picture.
 */
final class TileArt {
    static final float GROW = 1.2f;

    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), place = Ui.fill(0xFF26282F),
            ring = Ui.stroke(Cine.WHITE, 3f), red = Ui.fill(Cine.RED), track = Ui.fill(0x59FFFFFF), chipFill = Ui.fill(0xCC000000),
            foot = new Paint(), shade = new Paint();
    private final TextPaint name, title, line, chip;
    private final RectF r = new RectF();
    private final Map<Object, String> cut = new HashMap<>();
    private float footH = -1, shadeH = -1;

    TileArt() {
        name = Cine.text(26f, Cine.outfit(700), Cine.TEXT);
        title = Cine.text(27f, Cine.outfit(700), Cine.TEXT);
        title.setShadowLayer(Ui.f(8f), 0, Ui.f(2f), 0xB3000000);
        line = Cine.text(22f, Cine.inter(), Cine.SOFT);
        line.setShadowLayer(Ui.f(6f), 0, Ui.f(2f), 0xB3000000);
        chip = Cine.text(17f, Cine.interSemi(), Cine.TEXT);
    }

    /** A title's poster (or wide picture, landscape) at (x, y), w x h (screen pixels), grown by up (0-1), at an alpha. */
    void poster(Canvas c, Catalog.Title t, String picture, float x, float y, float w, float h, float up, int alpha, Runnable redraw) {
        float k = 1f + (GROW - 1f) * up;
        c.save();
        c.translate(x + w / 2f, y + h / 2f);
        c.scale(k, k);
        c.translate(-w / 2f, -h / 2f);
        r.set(0, 0, w, h);
        Bitmap b = Pics.get(picture, Math.round(w * GROW), Math.round(h * GROW), Pics.CROP, redraw);
        if (b != null) {
            bmp.setAlpha(alpha);
            c.drawBitmap(b, null, r, bmp);
        } else {
            place.setAlpha(alpha);
            c.drawRect(r, place);
            shaded(c, w, h, alpha);
            String n = cut.get(t);
            if (n == null) {
                n = Ui.fit(t.name, name, w - Ui.f(28f));
                cut.put(t, n);
            }
            name.setAlpha(alpha);
            c.drawText(n, Ui.f(14f), h - Ui.f(20f), name);
        }
        marks(c, t, w, alpha);
        edge(c, w, h, up, alpha);
        c.restore();
    }

    /** A wide tile to carry on from: its picture, its name and line over a dark foot, the red bar of how far it was watched. */
    void wide(Canvas c, Catalog.Title t, String picture, String words, float progress, float x, float y, float w, float h, float up,
              int alpha, Runnable redraw) {
        float k = 1f + (GROW - 1f) * up;
        c.save();
        c.translate(x + w / 2f, y + h / 2f);
        c.scale(k, k);
        c.translate(-w / 2f, -h / 2f);
        r.set(0, 0, w, h);
        Bitmap b = Pics.get(picture, Math.round(w * GROW), Math.round(h * GROW), Pics.CROP, redraw);
        if (b != null) {
            bmp.setAlpha(alpha);
            c.drawBitmap(b, null, r, bmp);
        } else {
            place.setAlpha(alpha);
            c.drawRect(r, place);
        }
        if (footH != h) {
            footH = h;
            foot.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x00000000, 0xE0000000, Shader.TileMode.CLAMP));
        }
        foot.setAlpha(alpha);
        c.drawRect(r, foot);
        String n = cut.get(t);
        if (n == null) {
            n = Ui.fit(t.name, title, w - Ui.f(36f));
            cut.put(t, n);
        }
        title.setAlpha(alpha);
        line.setAlpha(alpha);
        float base = h - Ui.f(progress >= 0 ? 26f : 18f);
        if (!words.isEmpty()) {
            c.drawText(words, Ui.f(18f), base, line);
            base -= Ui.f(32f);
        }
        c.drawText(n, Ui.f(18f), base, title);
        if (progress >= 0f) {
            float bh = Ui.f(5f);
            track.setAlpha(alpha * 0x59 / 255);
            c.drawRect(0, h - bh, w, h, track);
            red.setAlpha(alpha);
            c.drawRect(0, h - bh, w * Math.max(0.02f, progress), h, red);
        }
        marks(c, t, w, alpha);
        edge(c, w, h, up, alpha);
        c.restore();
    }

    /** The end of a long row: "All 1,234" and a chevron on a dark tile, w x h at (x, y). */
    void all(Canvas c, String words, float x, float y, float w, float h, float up, int alpha) {
        float k = 1f + (GROW - 1f) * up;
        c.save();
        c.translate(x + w / 2f, y + h / 2f);
        c.scale(k, k);
        c.translate(-w / 2f, -h / 2f);
        r.set(0, 0, w, h);
        place.setAlpha(alpha);
        c.drawRect(r, place);
        name.setAlpha(alpha);
        float tw = name.measureText(words);
        c.drawText(words, (w - tw) / 2f, h / 2f - Ui.f(4f), name);
        ring.setAlpha(alpha);
        ring.setStrokeWidth(Ui.f(3.4f));
        Ui.chevron(c, w / 2f, h / 2f + Ui.f(40f), Ui.f(28f), Ui.RIGHT, ring);
        ring.setStrokeWidth(Math.max(1f, Ui.f(3f)));
        edge(c, w, h, up, alpha);
        c.restore();
    }

    private void shaded(Canvas c, float w, float h, int alpha) {
        if (shadeH != h) {
            shadeH = h;
            shade.setShader(new LinearGradient(0, h * 0.5f, 0, h, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
        }
        shade.setAlpha(alpha);
        c.drawRect(0, 0, w, h, shade);
    }

    /** TorBox's cloud at the top left of the family's own titles; 4K at the top right where a copy is. */
    private void marks(Canvas c, Catalog.Title t, float w, int alpha) {
        if (t == null) return;
        if (t.torbox()) SourcePicker.badge(c, Ui.f(28f), Ui.f(28f), Ui.f(32f), alpha);
        if (t.bestPicture() == Catalog.PICTURE_4K) {
            float cw = chip.measureText("4K") + Ui.f(14f);
            r.set(w - cw - Ui.f(10f), Ui.f(10f), w - Ui.f(10f), Ui.f(38f));
            chipFill.setAlpha(alpha * 0xCC / 255);
            c.drawRoundRect(r, Ui.f(5f), Ui.f(5f), chipFill);
            chip.setAlpha(alpha);
            c.drawText("4K", r.left + Ui.f(7f), Cine.mid(chip, r.centerY()), chip);
        }
    }

    /** Where the remote is: a white edge as it grows. */
    private void edge(Canvas c, float w, float h, float up, int alpha) {
        if (up <= 0.02f) return;
        ring.setAlpha(Math.round(alpha * Math.min(1f, up)));
        r.set(0, 0, w, h);
        c.drawRect(r, ring);
        ring.setAlpha(255);
    }
}
