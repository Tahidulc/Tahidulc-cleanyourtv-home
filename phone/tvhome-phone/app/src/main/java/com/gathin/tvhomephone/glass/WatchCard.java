package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

/**
 * Continue watching's card, as the reference has it: the picture filling a rounded landscape card under a fine glass
 * rim, darkened towards its foot, where its logo (or its name in spaced capitals) sits over a play mark, the line of
 * how far in with a dot where it was left, and the time still to go.
 */
public class WatchCard extends FrameLayout {
    private final ImageView picture, logo;
    private final TextView name, time;
    private final Line line;

    public WatchCard(Context c) {
        super(c);
        float radius = Glass.dp(c, 20);
        Glass.round(this, radius);
        setBackground(Glass.placeholder("watch", radius));
        picture = new ImageView(c);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(picture, new LayoutParams(-1, -1));
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1A000000, 0x00000000, 0x99000000, 0xE6000000}));
        addView(scrim, new LayoutParams(-1, -1));
        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.VERTICAL);
        int p = Glass.dp(c, 14);
        foot.setPadding(p, 0, p, Glass.dp(c, 12));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setAdjustViewBounds(true);
        logo.setMaxHeight(Glass.dp(c, 34));
        logo.setMaxWidth(Glass.dp(c, 150));
        logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        logo.setVisibility(GONE);
        foot.addView(logo, new LinearLayout.LayoutParams(-2, -2));
        name = Glass.text(c, "", 14, Glass.TEXT, Glass.heading(c, 600));
        name.setLetterSpacing(0.22f);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setShadowLayer(Glass.dp(c, 4), 0, 0, 0x99000000);
        foot.addView(name, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView play = new ImageView(c);
        play.setImageDrawable(new Icons(c, Icons.PLAY, Glass.TEXT));
        play.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(play, new LinearLayout.LayoutParams(Glass.dp(c, 17), Glass.dp(c, 17)));
        line = new Line(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Glass.dp(c, 44), Glass.dp(c, 8));
        lp.leftMargin = Glass.dp(c, 8);
        lp.rightMargin = Glass.dp(c, 10);
        row.addView(line, lp);
        time = Glass.text(c, "", 13, Glass.TEXT, Glass.body(c));
        time.setSingleLine(true);
        row.addView(time, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Glass.dp(c, 8);
        foot.addView(row, rp);
        addView(foot, new LayoutParams(-1, -2, Gravity.BOTTOM));
        setForeground(Glass.rim(c, 20));
        setClickable(true);
        setFocusable(true);
    }

    /**
     * What it shows: its name, its picture (a backdrop, or failing that a poster), its logo when there is one (else the
     * name in spaced capitals), how far in (0 to 1) and the time to go ("2hrs 29mins", "34 min left").
     */
    public void set(String title, String image, String logoUrl, float progress, String left) {
        String t = title == null ? "" : title;
        name.setText(t.toUpperCase(java.util.Locale.UK));
        time.setText(left == null ? "" : left);
        line.set(progress);
        setContentDescription(t + (left == null || left.isEmpty() ? "" : ", " + left));
        try {
            if (image != null && !image.isEmpty()) Glide.with(picture).load(image).centerCrop().into(picture);
            else Glide.with(picture).clear(picture);
            if (logoUrl != null && !logoUrl.isEmpty()) {
                logo.setVisibility(VISIBLE);
                name.setVisibility(GONE);
                Glide.with(logo).load(logoUrl).fitCenter().into(logo);
            } else {
                Glide.with(logo).clear(logo);
                logo.setVisibility(GONE);
                name.setVisibility(VISIBLE);
            }
        } catch (RuntimeException ignored) {
            // the page went away
        }
    }

    /** The line of how far in: a faint track, the part watched in white, a dot where it was left. */
    static final class Line extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float progress;

        Line(Context c) {
            super(c);
            track.setColor(0x4DFFFFFF);
            done.setColor(0xFFFFFFFF);
        }

        void set(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            float h = getHeight(), w = getWidth(), lh = Math.max(2f, h * 0.3f), dot = h / 2f;
            float y = h / 2f, x0 = dot, x1 = w - dot;
            r.set(x0, y - lh / 2f, x1, y + lh / 2f);
            cv.drawRoundRect(r, lh / 2f, lh / 2f, track);
            float x = x0 + (x1 - x0) * progress;
            r.set(x0, y - lh / 2f, Math.max(x0 + lh, x), y + lh / 2f);
            cv.drawRoundRect(r, lh / 2f, lh / 2f, done);
            cv.drawCircle(x, y, dot, done);
        }
    }
}
