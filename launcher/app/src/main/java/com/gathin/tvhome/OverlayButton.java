package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The overlays prompt's button (OverlayPrompt): small and solid, laid out as a sports channel's own logo is but as one
 * block: HOLD and the remote's OK button in its light top band, "for overlays" large in its dark middle, and what they
 * are spaced out along its foot. While OK is held a bright line fills along the foot of the band until they come on.
 */
final class OverlayButton extends View {
    private static final int BAND = 0xFFA4A9B0, BODY = 0xFF101216, INK = 0xFFFFFFFF, SOFT = 0xFFB8BDC6;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private String foot = "SCOREBOARD · LOGO";
    /** The Google TV Streamer's remote: its pale OK button rather than the TCL's black one. */
    private boolean streamer;
    /** How far the hold has gone, 0 to 1. */
    private float held;
    private ValueAnimator holding;

    OverlayButton(Context c) {
        super(c);
        text.setTypeface(ScoreCard.outfit(c, 700));
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setContentDescription("Hold OK for overlays");
    }

    /** What the overlays are, along its foot. */
    void foot(String s) {
        foot = s == null ? "" : s;
        invalidate();
    }

    void remote(boolean isStreamer) {
        streamer = isStreamer;
        invalidate();
    }

    /** OK held down (on: the line filling over ms) or let go (back to none). */
    void holding(boolean on, long ms) {
        if (holding != null) holding.cancel();
        if (!on) {
            held = 0f;
            invalidate();
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(Math.max(1, ms));
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(a -> {
            held = (float) a.getAnimatedValue();
            invalidate();
        });
        holding = va;
        va.start();
    }

    /** Previews: drawn this far into a hold. */
    void heldNow(float f) {
        if (holding != null) holding.cancel();
        held = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (holding != null) holding.cancel();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float rad = h * 0.1f;
        r.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        fill.setColor(BODY);
        cv.drawRect(r, fill);
        // The light band: HOLD, the remote's OK at its end, as a channel's logo has its name and its HD box.
        float band = h * 0.36f, pad = h * 0.12f;
        fill.setColor(BAND);
        cv.drawRect(0, 0, w, band, fill);
        words(cv, "HOLD", pad, 0, band, band * 0.58f, 0.06f, INK, Paint.Align.LEFT);
        float ok = band * 0.78f;
        okButton(cv, w - pad * 0.6f - ok, (band - ok) / 2f, ok);
        if (held > 0f) {
            fill.setColor(INK);
            cv.drawRect(0, band - Math.max(2f, h * 0.05f), w * held, band, fill);
        }
        // "for overlays" large in the middle, what they are along the foot.
        float mid = h * 0.4f;
        words(cv, "for overlays", w / 2f, band, mid, fit("for overlays", w - 2 * pad, mid * 0.62f, 0f), 0f, INK, Paint.Align.CENTER);
        float footH = h - band - mid;
        words(cv, foot, w / 2f, band + mid - footH * 0.1f, footH, fit(foot, w - 2 * pad, footH * 0.52f, 0.16f), 0.16f, SOFT,
                Paint.Align.CENTER);
        cv.restore();
    }

    /** The remote's OK button: on the TCL's remote a black button with OK in white in its dark ring; the Streamer's pale. */
    private void okButton(Canvas cv, float x, float y, float s) {
        float rr = s / 2f, cx = x + rr, cy = y + rr;
        fill.setColor(streamer ? 0xFFD9D6CF : 0xFF2B2D31);
        cv.drawCircle(cx, cy, rr, fill);
        fill.setColor(streamer ? 0xFFEDEAE4 : 0xFF0E0F11);
        cv.drawCircle(cx, cy, rr * 0.76f, fill);
        words(cv, "OK", cx, y, s, rr * 0.62f, 0f, streamer ? 0xFF2B2D31 : INK, Paint.Align.CENTER);
    }

    private float fit(String s, float room, float most, float spacing) {
        text.setTextSize(most);
        text.setLetterSpacing(spacing);
        float wd = text.measureText(s);
        text.setLetterSpacing(0f);
        return wd <= room ? most : Math.max(6f, most * room / Math.max(1f, wd));
    }

    private void words(Canvas cv, String s, float x, float y, float rowH, float size, float spacing, int colour, Paint.Align align) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setLetterSpacing(spacing);
        text.setColor(colour);
        text.setTextAlign(align);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, x, y + rowH / 2f - (fm.ascent + fm.descent) / 2f, text);
        text.setLetterSpacing(0f);
    }
}
