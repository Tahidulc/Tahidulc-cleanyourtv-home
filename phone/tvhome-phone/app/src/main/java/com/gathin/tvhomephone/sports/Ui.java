package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;

/** Small view helpers in the launcher's look, for Home and Sports: text, cards, crests and the glass rows. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** A tablet (600 dp wide or more): two columns. */
    public static boolean wide(Context c) {
        return c.getResources().getConfiguration().screenWidthDp >= 600;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    /** A section's heading: small capitals in the section's colour. */
    public static TextView heading(Context c, String s, int color) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.ROOT), 13, color, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(c, 18), 0, dp(c, 8));
        return t;
    }

    /** The glass rows the launcher's panels use: a faint white wash with a hairline. */
    public static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c.getColor(R.color.glass));
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), c.getColor(R.color.surface_line));
        return d;
    }

    public static GradientDrawable fill(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable vertical(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** A crest or logo from a URL, fitted in a square. */
    public static ImageView image(Context c, String url, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        load(v, url);
        return v;
    }

    public static void load(ImageView v, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Glide.with(v).load(url).into(v);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams margins(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** A card: the glass row with padding, tappable when it has an action. */
    public static LinearLayout card(Context c, View.OnClickListener tap) {
        LinearLayout l = column(c);
        l.setBackground(glass(c, 14));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        if (tap != null) {
            l.setOnClickListener(tap);
            l.setClickable(true);
            l.setFocusable(true);
        }
        return l;
    }

    /** "Live on Sky Sports Main Event": a link in the Sports green when the playlist has that channel. */
    public static TextView liveOn(Context c, String channel) {
        boolean link = channel != null && com.gathin.tvhomephone.live.LiveLinks.has(c, channel);
        TextView t = text(c, (link ? "▶  " : "") + "Live on " + channel, 14, link ? c.getColor(R.color.sports) : c.getColor(R.color.text_soft), link);
        if (link) {
            t.setPadding(0, dp(c, 6), 0, dp(c, 6));
            t.setOnClickListener(v -> com.gathin.tvhomephone.live.LiveLinks.open(c, channel));
        }
        return t;
    }

    public static void empty(LinearLayout into, String words) {
        Context c = into.getContext();
        TextView t = text(c, words, 15, c.getColor(R.color.text_dim), false);
        t.setPadding(0, dp(c, 12), 0, dp(c, 12));
        into.addView(t);
    }
}
