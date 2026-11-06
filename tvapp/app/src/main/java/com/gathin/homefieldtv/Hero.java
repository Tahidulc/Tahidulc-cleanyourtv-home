package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

/**
 * The big picture at the top of Home, Movies and TV shows, with what the remote is on written over its faded left side:
 * a label or logo, the title, stars and running time, a few lines of synopsis. The picture is faded once when it arrives
 * (kept, so moving back and forth costs nothing) rather than blended every frame.
 */
final class Hero extends View {
    /** Where the writing goes (design pixels). */
    float textX = 262f, textY = 85f, textW = 780f;
    /** Where the picture goes (design pixels). */
    final RectF pic = new RectF(880f, 0f, 1920f, 560f);
    int titleLines = 1, plotLines = 4;
    float titleSize = 56f;

    private String chip = "", logo = "", title = "", meta = "", plot = "", picture = "";
    /** An age rating ("12", "PG"), shown in an outlined box after the line under the title. */
    String badge = "";
    private float rating;
    private boolean tall;
    private long shownAt;

    private final TextPaint chipP = Ui.paint(22, true, Ui.WHITE), metaP = Ui.paint(30, false, Ui.WHITE);
    private final TextPaint plotP = Ui.paint(30, false, Ui.DIM);
    private TextPaint titleP = Ui.paint(56, false, Ui.WHITE);
    private final Paint chipBack = Ui.fill(0x38FFFFFF), starOn = Ui.fill(Ui.WHITE), starOff = Ui.fill(0x55FFFFFF);
    private final Paint badgeLine = Ui.stroke(Ui.WHITE, 1.5f);
    private final TextPaint badgeP = Ui.paint(22, true, Ui.WHITE);
    private final Paint fade = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private StaticLayout titleL, plotL;
    private final Runnable redraw = this::invalidate;

    Hero(Context c) {
        super(c);
    }

    void setTitleSize(float size) {
        titleSize = size;
        titleP = Ui.paint(size, false, Ui.WHITE);
    }

    /** What to show; picture "" for none, tall when it is a poster rather than a wide backdrop. */
    void show(String chipText, String logoUrl, String titleText, String metaText, float outOfTen, String plotText, String pictureUrl,
              boolean isTall) {
        String pu = pictureUrl == null ? "" : pictureUrl;
        if (!pu.equals(picture)) shownAt = SystemClock.uptimeMillis();
        chip = chipText == null ? "" : chipText;
        logo = logoUrl == null ? "" : logoUrl;
        title = titleText == null ? "" : titleText;
        meta = metaText == null ? "" : metaText;
        rating = outOfTen;
        plot = plotText == null ? "" : plotText;
        picture = pu;
        tall = isTall;
        titleL = Ui.layout(title, titleP, Ui.px(textW), titleLines);
        plotL = plot.isEmpty() ? null : Ui.layout(plot, plotP, Ui.px(textW), plotLines);
        invalidate();
    }

    void clear() {
        show("", "", "", "", 0, "", "", false);
    }

    @Override protected void onDraw(Canvas c) {
        drawPicture(c);
        float x = Ui.f(textX), y = Ui.f(textY);
        if (!logo.isEmpty()) {
            Bitmap b = Pics.get(logo, Ui.px(300), Ui.px(70), Pics.FIT, redraw);
            if (b != null) c.drawBitmap(b, x, y + (Ui.f(70) - b.getHeight()) / 2f, fade);
            y += Ui.f(84);
        } else if (!chip.isEmpty()) {
            float w = chipP.measureText(chip) + Ui.f(24);
            r.set(x, y, x + w, y + Ui.f(36));
            c.drawRoundRect(r, Ui.f(4), Ui.f(4), chipBack);
            c.drawText(chip, x + Ui.f(12), y + Ui.f(26), chipP);
            y += Ui.f(48);
        }
        if (titleL != null) {
            Ui.draw(c, titleL, x, y);
            y += titleL.getHeight() + Ui.f(10);
        }
        if (rating > 0 || !meta.isEmpty() || !badge.isEmpty()) {
            float base = y + Ui.f(30);
            float mx = x;
            if (rating > 0) mx += Ui.stars(c, x, base, Ui.f(28), rating, starOn, starOff) + Ui.f(14);
            String m = Ui.fit(meta, metaP, Ui.f(textW) - (mx - x) - Ui.f(80));
            c.drawText(m, mx, base, metaP);
            mx += metaP.measureText(m) + Ui.f(16);
            if (!badge.isEmpty()) {
                String b = badge.length() > 5 ? badge.substring(0, 5) : badge;
                float bw = badgeP.measureText(b) + Ui.f(14);
                r.set(mx, base - Ui.f(25), mx + bw, base + Ui.f(5));
                c.drawRoundRect(r, Ui.f(3), Ui.f(3), badgeLine);
                c.drawText(b, mx + Ui.f(7), base - Ui.f(2), badgeP);
            }
            y = base + Ui.f(16);
        }
        if (plotL != null) Ui.draw(c, plotL, x, y + Ui.f(4));
    }

    private void drawPicture(Canvas c) {
        if (picture.isEmpty()) return;
        int ph = Ui.px(pic.height());
        int pw = tall ? Math.round(ph * 2f / 3f) : Ui.px(pic.width());
        // Faded into the blue as it is loaded, off the main thread.
        Bitmap f = Pics.get(picture, pw, ph, tall ? Pics.FADE_TALL : Pics.FADE, redraw);
        if (f == null) return;
        float left = Ui.f(pic.right) - pw, top = Ui.f(pic.top);
        long age = SystemClock.uptimeMillis() - shownAt;
        int alpha = Exec.inline ? 255 : (int) Math.min(255, age * 255 / 220);
        fade.setAlpha(alpha);
        c.drawBitmap(f, left, top, fade);
        fade.setAlpha(255);
        if (alpha < 255) postInvalidateOnAnimation();
    }
}
