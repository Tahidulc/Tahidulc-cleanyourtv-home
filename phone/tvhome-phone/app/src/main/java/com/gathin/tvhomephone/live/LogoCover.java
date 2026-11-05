package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.gathin.tvhomephone.R;

/**
 * Homefield's own mark over the channel's logo (found on the picture by BugFinder), filling the logo's place, in the
 * look chosen in Settings: Badge (the app's violet-to-coral tile with its house and play mark, the name beside it when
 * there is room), Glass (dark smoked glass with the white mark and the name), Mark (the tile alone, on a dark ground as
 * wide as the logo was), or Wordmark (HOMEFIELD in letters, a coral line under it). Each ground is all but solid, so
 * none of the channel's logo shows through it.
 */
final class LogoCover extends View {
    /** The looks, as Settings names them. */
    enum Look {
        OFF("Leave the channel's"), BADGE("Badge"), GLASS("Glass"), MARK("Mark only"), WORD("Wordmark");

        final String label;

        Look(String label) {
            this.label = label;
        }

        static Look of(String name) {
            for (Look l : values()) if (l.name().equals(name)) return l;
            return BADGE;
        }
    }

    private static final int VIOLET = 0xFF6D28F2, CORAL = 0xFFFF6B6B;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), words = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Drawable mark, markOnly;
    private Look look = Look.BADGE;

    LogoCover(Context c) {
        super(c);
        mark = c.getDrawable(R.drawable.ic_app_front);
        markOnly = c.getDrawable(R.drawable.ic_app_mono);
        words.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        words.setColor(0xFFFFFFFF);
        setContentDescription("Homefield");
    }

    void look(Look l) {
        look = l;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || look == Look.OFF) return;
        float rad = Math.min(w, h) * 0.22f;
        r.set(0, 0, w, h);
        switch (look) {
            case GLASS:
                fill.setShader(null);
                fill.setColor(0xFA0B0F24);
                cv.drawRoundRect(r, rad, rad, fill);
                named(cv, w, h, 0xFFFFFFFF, false);
                break;
            case MARK:
                fill.setShader(null);
                fill.setColor(0xFA101014);
                cv.drawRoundRect(r, rad, rad, fill);
                float s = Math.min(h, w) * 0.86f;
                tile(cv, (w - s) / 2, (h - s) / 2, s);
                break;
            case WORD: {
                fill.setShader(null);
                fill.setColor(0xFA000000);
                cv.drawRoundRect(r, rad, rad, fill);
                float size = fit("HOMEFIELD", w * 0.84f, h * 0.42f, 0.12f);
                words.setTextSize(size);
                words.setLetterSpacing(0.12f);
                words.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = words.getFontMetrics();
                float base = h / 2 - (fm.ascent + fm.descent) / 2 - h * 0.06f;
                cv.drawText("HOMEFIELD", w / 2, base, words);
                words.setLetterSpacing(0);
                float lw = words.measureText("HOMEFIELD") * 0.5f;
                fill.setShader(new LinearGradient(w / 2 - lw / 2, 0, w / 2 + lw / 2, 0, VIOLET, CORAL, Shader.TileMode.CLAMP));
                cv.drawRoundRect(w / 2 - lw / 2, base + h * 0.1f, w / 2 + lw / 2, base + h * 0.1f + Math.max(2, h * 0.07f), h * 0.04f, h * 0.04f, fill);
                fill.setShader(null);
                break;
            }
            default:
                fill.setShader(new LinearGradient(0, 0, w, h, VIOLET, CORAL, Shader.TileMode.CLAMP));
                cv.drawRoundRect(r, rad, rad, fill);
                fill.setShader(null);
                named(cv, w, h, 0xFFFFFFFF, true);
        }
    }

    /** The white house mark at the left (or alone in the middle when narrow), the name beside it when it fits. */
    private void named(Canvas cv, float w, float h, int ink, boolean onTile) {
        float s = h * 0.9f;
        boolean name = w / h >= 2.2f;
        float x = name ? h * 0.12f : (w - s) / 2;
        drawMark(cv, onTile ? mark : markOnly, x, (h - s) / 2, s, onTile ? 0 : ink);
        if (!name) return;
        float left = x + s * 0.92f, room = w - left - h * 0.22f;
        float size = fit("Homefield", room, h * 0.46f, 0f);
        words.setTextSize(size);
        words.setLetterSpacing(0);
        words.setTextAlign(Paint.Align.LEFT);
        words.setColor(ink);
        Paint.FontMetrics fm = words.getFontMetrics();
        cv.drawText("Homefield", left, h / 2 - (fm.ascent + fm.descent) / 2, words);
    }

    /** The app's own tile: violet to coral, rounded, the house and play mark on it. */
    private void tile(Canvas cv, float x, float y, float s) {
        fill.setShader(new LinearGradient(x, y, x + s, y + s, VIOLET, CORAL, Shader.TileMode.CLAMP));
        cv.drawRoundRect(x, y, x + s, y + s, s * 0.24f, s * 0.24f, fill);
        fill.setShader(null);
        drawMark(cv, mark, x, y, s, 0);
    }

    /**
     * The house mark filling a square of side s at (x, y): the drawable's 108 units hold the house in about 46 of them,
     * so it is drawn larger than the square, centred on it. tint 0: its own colours.
     */
    private void drawMark(Canvas cv, Drawable d, float x, float y, float s, int tint) {
        if (d == null) return;
        float big = s * 108f / 62f;
        int l = Math.round(x + s / 2 - big / 2), t = Math.round(y + s / 2 - big / 2);
        d.setBounds(l, t, l + Math.round(big), t + Math.round(big));
        if (tint != 0) d.setTint(tint);
        d.draw(cv);
    }

    /** The biggest text size up to most at which words fit in room. */
    private float fit(String s, float room, float most, float spacing) {
        words.setLetterSpacing(spacing);
        words.setTextSize(most);
        float wd = words.measureText(s);
        return wd <= room ? most : Math.max(6, most * room / wd);
    }
}
