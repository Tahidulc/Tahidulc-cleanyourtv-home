package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The look the Movies and TV mock-ups share, for the full-screen pages built in code (Previously on, Surprise me, Your
 * month): round pill buttons, white when focused and outlined when not, the main one carrying the remote's round "OK"
 * badge; a thin white ring drawn over a picture; a softened (blurred) backdrop; and a band that fades a row out at
 * both ends into the page.
 */
final class MockKit {
    private MockKit() { }

    /** The mock-ups' deep blue: the ink on white pills and the OK badge's disc. */
    static final int NAVY = 0xFF141C92;

    /**
     * A pill button: an OK badge first when main (the one OK presses when nothing else is chosen), an icon if given,
     * then the words. paint() sets its focused or resting look.
     */
    static LinearLayout pill(HomeV2 h, String words, float sp, Typeface face, int tall, boolean main, Drawable icon) {
        LinearLayout p = new LinearLayout(h.a);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER);
        TextView badge = null;
        if (main) {
            badge = h.text(Math.max(12, tall * 0.21f), face, 0xFFFFFFFF);
            badge.setText("OK");
            badge.setGravity(Gravity.CENTER);
            int d = Math.round(tall * 0.74f);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(h.px(d), h.px(d));
            bl.rightMargin = h.px(12);
            p.addView(badge, bl);
            p.setPadding(h.px((tall - d) / 2f), 0, h.px(26), 0);
        } else {
            p.setPadding(h.px(24), 0, h.px(24), 0);
        }
        if (icon != null) {
            ImageView i = new ImageView(h.a);
            i.setImageDrawable(icon);
            i.setScaleType(ImageView.ScaleType.FIT_CENTER);
            i.setClipToOutline(true);
            i.setOutlineProvider(h.round(6));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(h.px(28), h.px(28));
            il.rightMargin = h.px(10);
            p.addView(i, il);
        }
        TextView label = h.text(sp, face, 0xFFFFFFFF);
        label.setText(words);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        p.addView(label, new LinearLayout.LayoutParams(-2, -2));
        p.setTag(new TextView[]{badge, label});
        p.setMinimumHeight(h.px(tall));
        return p;
    }

    static void setWords(LinearLayout pill, String words) {
        ((TextView[]) pill.getTag())[1].setText(words);
    }

    /** Focused: white, with the ink in navy (the badge a navy disc). Resting: a see-through pill with a white edge. */
    static void paint(HomeV2 h, LinearLayout pill, boolean focused, int tall) {
        TextView[] parts = (TextView[]) pill.getTag();
        GradientDrawable g = h.fill(tall / 2f, focused ? 0xFFFFFFFF : 0x14FFFFFF);
        if (!focused) g.setStroke(Math.max(1, h.px(2)), 0x8CFFFFFF);
        pill.setBackground(g);
        parts[1].setTextColor(focused ? NAVY : 0xFFFFFFFF);
        if (parts[0] != null) {
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(focused ? NAVY : 0xFFFFFFFF);
            parts[0].setBackground(disc);
            parts[0].setTextColor(focused ? 0xFFFFFFFF : NAVY);
        }
    }

    /** A ring of the given width drawn over v (a picture's white edge), following its rounded corners. */
    static void ring(HomeV2 h, View v, float radius, float width, int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(h.px(radius));
        g.setColor(0);
        g.setStroke(Math.max(1, h.px(width)), colour);
        v.setForeground(g);
    }

    /** A cheap blur: the picture made tiny and drawn back up smoothly (enough for a softened backdrop). */
    static Bitmap soft(Bitmap b, int across) {
        if (b == null || b.getWidth() <= 0) return null;
        try {
            int w = Math.max(8, across), hh = Math.max(8, Math.round(across * b.getHeight() / (float) b.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(b, w, hh, true);
            Bitmap mid = Bitmap.createScaledBitmap(small, w * 8, hh * 8, true);
            if (small != b) small.recycle();
            return mid;
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    /**
     * A band across the page that is the page's colour at both ends and clear in the middle (clear from `in` to
     * 1 - `in` of the width): a row of posters fades into the page at the sides.
     */
    static View sideFade(Context c, int colour, float in) {
        int clear = colour & 0x00FFFFFF;
        return new View(c) {
            private final Paint paint = new Paint();
            @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
                paint.setShader(new LinearGradient(0, 0, w, 0, new int[]{colour, clear, clear, colour},
                        new float[]{0f, in, 1f - in, 1f}, Shader.TileMode.CLAMP));
            }
            @Override protected void onDraw(Canvas cv) {
                cv.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
        };
    }
}
