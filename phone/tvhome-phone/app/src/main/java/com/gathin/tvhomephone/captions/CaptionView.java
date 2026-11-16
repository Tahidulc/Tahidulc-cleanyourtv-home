package com.gathin.tvhomephone.captions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;

/**
 * The automatic subtitles on the picture: two lines at most, centred near the foot, the newest words rolling up under
 * the last smoothly (as broadcast roll-up captions do), gone a few seconds after the talking stops. In the looks the app's
 * subtitles have: white with an outline, white on a black box, or yellow with an outline, at the family's size.
 */
public final class CaptionView extends View {
    public static final int LOOK_OUTLINE = 0, LOOK_BOX = 1, LOOK_YELLOW = 2;
    /** Gone this long after the last new words. */
    private static final long LINGER_MS = 6_000;
    private static final long FADE_MS = 500;

    private final TextPaint fill = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stroke = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private String text = "";
    private StaticLayout fillLayout, strokeLayout;
    private int look = LOOK_OUTLINE;
    private float scale = 1f;
    /** How far the text is rolled up (px), and where it is going. */
    private float scroll, target;
    private long lastWords;

    public CaptionView(Context c) {
        super(c);
        fill.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        stroke.setTypeface(fill.getTypeface());
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFF000000);
        box.setColor(0xC8000000);
        setLook(LOOK_OUTLINE, 1f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The look (LOOK_…) and the size against the usual (1.0). */
    public void setLook(int look, float scale) {
        this.look = look;
        this.scale = scale;
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 19f * scale, getResources().getDisplayMetrics());
        fill.setTextSize(px);
        stroke.setTextSize(px);
        stroke.setStrokeWidth(Math.max(2f, px * 0.14f));
        fill.setColor(look == LOOK_YELLOW ? 0xFFFFE14D : 0xFFFFFFFF);
        relayout(true);
        invalidate();
    }

    public void setText(String words) {
        String t = words == null ? "" : words;
        if (t.equals(text)) return;
        boolean fresh = text.isEmpty() || !t.startsWith(text.substring(0, Math.min(text.length(), 12)));
        text = t;
        if (!t.isEmpty()) lastWords = SystemClock.uptimeMillis();
        relayout(fresh);
        postInvalidateOnAnimation();
    }

    public void clear() {
        setText("");
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        relayout(true);
    }

    private int lineWidth() {
        float dp = getResources().getDisplayMetrics().density;
        int room = getWidth() - getPaddingLeft() - getPaddingRight();
        return Math.max(1, Math.min(Math.round(room * 0.88f), Math.round(820 * dp * scale)));
    }

    private void relayout(boolean jump) {
        if (getWidth() == 0 || text.isEmpty()) {
            fillLayout = strokeLayout = null;
            scroll = target = 0;
            return;
        }
        int w = lineWidth();
        fillLayout = StaticLayout.Builder.obtain(text, 0, text.length(), fill, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0f, 1.04f).build();
        strokeLayout = look == LOOK_BOX ? null : StaticLayout.Builder.obtain(text, 0, text.length(), stroke, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0f, 1.04f).build();
        int lines = fillLayout.getLineCount();
        target = lines > 2 ? fillLayout.getLineTop(lines - 2) : 0;
        if (jump) scroll = target;
    }

    @Override protected void onDraw(Canvas c) {
        StaticLayout l = fillLayout;
        if (l == null) return;
        long quiet = SystemClock.uptimeMillis() - lastWords;
        if (quiet > LINGER_MS + FADE_MS) return;
        float alpha = quiet <= LINGER_MS ? 1f : 1f - (quiet - LINGER_MS) / (float) FADE_MS;
        int lines = l.getLineCount();
        int first = lines > 2 ? lines - 2 : 0;
        float window = l.getLineBottom(lines - 1) - l.getLineTop(first);
        float pad = fill.getTextSize() * 0.28f;
        float top = getHeight() - getPaddingBottom() - window - pad;
        float left = (getWidth() - l.getWidth()) / 2f;
        c.save();
        c.clipRect(0, top - pad, getWidth(), top + window + pad);
        c.translate(left, top - scroll);
        int a = Math.round(alpha * 255);
        if (look == LOOK_BOX) {
            box.setAlpha(Math.round(alpha * 200));
            for (int i = Math.max(0, first - 1); i < lines; i++) {
                if (l.getLineEnd(i) <= l.getLineStart(i)) continue;
                r.set(l.getLineLeft(i) - pad, l.getLineTop(i) - pad * 0.2f, l.getLineRight(i) + pad, l.getLineBottom(i) + pad * 0.2f);
                c.drawRoundRect(r, pad * 0.5f, pad * 0.5f, box);
            }
        } else if (strokeLayout != null) {
            stroke.setAlpha(a);
            strokeLayout.draw(c);
        }
        fill.setAlpha(a);
        l.draw(c);
        c.restore();
        // Rolling up to the newest line, a little of the way each frame; then the fade, then nothing.
        if (Math.abs(target - scroll) > 0.5f) {
            scroll += (target - scroll) * 0.2f;
            postInvalidateOnAnimation();
        } else {
            scroll = target;
            if (quiet <= LINGER_MS) postInvalidateDelayed(LINGER_MS - quiet + 16);
            else postInvalidateOnAnimation();
        }
    }
}
