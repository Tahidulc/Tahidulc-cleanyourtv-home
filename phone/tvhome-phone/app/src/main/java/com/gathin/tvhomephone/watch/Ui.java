package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.google.android.material.button.MaterialButton;

/** The launcher's look in small pieces: its navy, white text, pill buttons, glass cards and section accents. */
public final class Ui {
    public static final int DISCOVER = 0xFFC9A7FF, MOVIES = 0xFFFF4FA3, SHOWS = 0xFF4F8BFF;
    public static final int GLASS = 0x1FFFFFFF, GLASS_LINE = 0x38FFFFFF, SOFT = 0xCCE4E6F5, DIM = 0x99C9CBE0;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    /** A section's small capital label ("MOVIES") in its accent. */
    public static TextView label(Context c, String s, int accent) {
        TextView t = text(c, s, 12, accent, true);
        t.setLetterSpacing(0.18f);
        return t;
    }

    public static GradientDrawable round(int fill, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** The glass card the launcher's boxes use: a faint white wash with a fine white line. */
    public static GradientDrawable glass(Context c) {
        GradientDrawable d = round(GLASS, 16, c);
        d.setStroke(dp(c, 1), GLASS_LINE);
        return d;
    }

    /** A pill button: filled white for the main action, glass for the rest. */
    public static MaterialButton pill(Context c, String words, boolean main, View.OnClickListener click) {
        MaterialButton b = new MaterialButton(c);
        b.setText(words);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c, 24));
        b.setMinHeight(dp(c, 48));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setBackgroundTintList(ColorStateList.valueOf(main ? 0xFFFFFFFF : GLASS));
        b.setTextColor(main ? 0xFF10123A : 0xFFFFFFFF);
        if (!main) {
            b.setStrokeColor(ColorStateList.valueOf(GLASS_LINE));
            b.setStrokeWidth(dp(c, 1));
        }
        b.setOnClickListener(click);
        return b;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, int l, int t, int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** "Pair with a TV in Settings": shown in place of what needs TMDB (its key comes from a TV). */
    public static View pairCard(Context c, Runnable openSettings) {
        LinearLayout card = column(c);
        int p = dp(c, 20);
        card.setPadding(p, p, p, p);
        card.setBackground(glass(c));
        card.addView(text(c, "Pair with a TV in Settings", 18, 0xFFFFFFFF, true));
        TextView why = text(c, "Films, series and their pictures come from TMDB through your TVs. Pair once on the home "
                + "network and everything here, My list and your ratings included, stays in step with them.", 14, SOFT, false);
        why.setPadding(0, dp(c, 8), 0, dp(c, 14));
        card.addView(why);
        card.addView(pill(c, "Open Settings", true, v -> openSettings.run()), wrap());
        return card;
    }

    public static int accent(int section) {
        return section == 1 ? MOVIES : section == 2 ? SHOWS : DISCOVER;
    }

    public static int color(Context c, int res) {
        return c.getColor(res);
    }

    public static int ground(Context c) {
        return c.getColor(R.color.ground);
    }
}
