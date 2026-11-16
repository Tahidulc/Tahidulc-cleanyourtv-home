package com.gathin.tvhomephone.watch;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.GlassDrawable;
import com.gathin.tvhomephone.glass.Icons;

import java.util.List;

/**
 * The cover page's own pieces in the glass look: the white main button, the frosted circles with a word under them,
 * the frosted chip ("Season 9 ⌄"), the thin line of how far in, a frame the shape of a widescreen picture, Film
 * night's moon, a little give when pressed, and the soft rise of what has just arrived.
 */
final class CoverViews {
    private CoverViews() {}

    /** The dark words on the white button and the white badges. */
    static final int INK = 0xFF101218;
    /** The white of the main button. */
    static final int WHITE = 0xF2FFFFFF;

    static int dp(Context c, float v) {
        return Glass.dp(c, v);
    }

    /** A fully rounded shape of one colour (a badge, a pill). */
    static GradientDrawable pill(Context c, int colour) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(dp(c, 100));
        return d;
    }

    /** Held down it gives a little (to scale), and springs back when let go. */
    static void press(View v, float scale) {
        StateListAnimator s = new StateListAnimator();
        s.addState(new int[]{android.R.attr.state_pressed}, scaleTo(v, scale, 90));
        s.addState(new int[0], scaleTo(v, 1f, 200));
        v.setStateListAnimator(s);
    }

    private static Animator scaleTo(View v, float to, long ms) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofFloat(View.SCALE_X, to),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, to));
        a.setDuration(ms);
        a.setInterpolator(new DecelerateInterpolator());
        return a;
    }

    /** What has just arrived fades in and rises a little, one after another. */
    static void rise(List<View> views) {
        int i = 0;
        for (View v : views) {
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(dp(v.getContext(), 22));
            v.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(i, 6) * 70L).setDuration(480)
                    .setInterpolator(new DecelerateInterpolator(1.8f)).start();
            i++;
        }
    }

    /** The white main button: its play mark and its words ("Play", "Continue S2 E5"). */
    static final class MainButton extends LinearLayout {
        private final TextView words;

        MainButton(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            float r = dp(c, 14);
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(WHITE);
            fill.setCornerRadius(r);
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFFFFFFFF);
            mask.setCornerRadius(r);
            setBackground(new RippleDrawable(ColorStateList.valueOf(0x24000000), fill, mask));
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(new Icons(c, Icons.PLAY, INK));
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LayoutParams(dp(c, 18), dp(c, 18)));
            words = Glass.text(c, "Play", 17, INK, Glass.heading(c, 600));
            words.setSingleLine(true);
            words.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams wp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            wp.leftMargin = dp(c, 9);
            addView(words, wp);
            setClickable(true);
            setFocusable(true);
            press(this, 0.97f);
            set("Play");
        }

        void set(String text) {
            words.setText(text);
            setContentDescription(text);
        }
    }

    /** A circle of frosted glass with its icon, and a word under it; on (in My list, rated, planned) it is brighter. */
    static final class Circle extends LinearLayout {
        private final FrameLayout disc;
        private final GlassDrawable glass;
        private final ImageView icon;
        private final TextView word;
        private boolean on;

        Circle(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            int size = dp(c, 54);
            disc = new FrameLayout(c);
            glass = Glass.card(c, 27);
            disc.setBackground(glass);
            icon = new ImageView(c);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            disc.addView(icon, new FrameLayout.LayoutParams(dp(c, 24), dp(c, 24), Gravity.CENTER));
            addView(disc, new LayoutParams(size, size));
            word = Glass.text(c, "", 12, Glass.SOFT, Glass.body(c));
            word.setGravity(Gravity.CENTER_HORIZONTAL);
            word.setMaxLines(2);
            word.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams wp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            wp.topMargin = dp(c, 8);
            addView(word, wp);
            setClickable(true);
            setFocusable(true);
        }

        void set(Drawable d, String words, boolean lit) {
            on = lit;
            icon.setImageDrawable(d);
            word.setText(words);
            word.setTextColor(lit ? Glass.TEXT : Glass.SOFT);
            glass.setLift(lit || isPressed() ? 1f : 0f);
            setContentDescription(words);
            setSelected(lit);
        }

        @Override public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            if (glass == null) return;
            glass.setLift(pressed || on ? 1f : 0f);
            disc.animate().scaleX(pressed ? 0.92f : 1f).scaleY(pressed ? 0.92f : 1f).setDuration(pressed ? 90 : 220).start();
        }
    }

    /** The thin line of how far in: a faint track, the part watched in white. */
    static final class Line extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;

        Line(Context c) {
            super(c);
            track.setColor(0x38FFFFFF);
            done.setColor(0xFFFFFFFF);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void set(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            if (w <= 0 || h <= 0) return;
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, rad, rad, track);
            if (progress <= 0) return;
            r.set(0, 0, Math.max(h, w * progress), h);
            cv.drawRoundRect(r, rad, rad, done);
        }
    }

    /** A frame nine sixteenths as tall as it is wide: a still, a trailer's picture. */
    static final class Wide extends FrameLayout {
        Wide(Context c) {
            super(c);
        }

        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    /** Film night's mark: a crescent moon in the glass icons' own line (a 24-unit square, round-capped strokes). */
    static final class Moon extends Drawable {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();
        private final int size;

        Moon(Context c, int colour) {
            size = dp(c, 24);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(1.8f);
            stroke.setColor(colour);
            // A full moon with a smaller one taken out of its upper right.
            shape.addCircle(11.2f, 12.6f, 8.3f, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(16.4f, 8.4f, 6.9f, Path.Direction.CW);
            shape.op(cut, Path.Op.DIFFERENCE);
        }

        @Override public int getIntrinsicWidth() {
            return size;
        }

        @Override public int getIntrinsicHeight() {
            return size;
        }

        @Override public void draw(@NonNull Canvas cv) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float u = Math.min(b.width(), b.height()) / 24f;
            int save = cv.save();
            cv.translate(b.exactCenterX() - 12 * u, b.exactCenterY() - 12 * u);
            cv.scale(u, u);
            cv.drawPath(shape, stroke);
            cv.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) {
            stroke.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter cf) {
            stroke.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
