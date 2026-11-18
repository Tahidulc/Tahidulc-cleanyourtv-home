package com.gathin.tvhomephone.sports;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.gathin.tvhomephone.home.Type;

/**
 * The parts the player's and the driver's pages share: their top row (Back, a crest or mark on a white disc, a title
 * and a line under it), the round arrows either side that go to the page before or after, and the window drawn edge to
 * edge on the page's own colour.
 */
final class PageBits {
    private PageBits() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        return t;
    }

    /** The top row's parts, to fill in. */
    static final class Top {
        LinearLayout row;
        TextView back, title, line;
        ImageView mark;
        FrameLayout disc;
    }

    /** Back, the mark on a white disc (hidden until it has a picture), the title and the line under it. */
    static Top top(Activity a) {
        Top t = new Top();
        t.row = new LinearLayout(a);
        t.row.setOrientation(LinearLayout.HORIZONTAL);
        t.row.setGravity(Gravity.CENTER_VERTICAL);
        t.back = text(a, "‹", 32, 0xFFFFFFFF, Type.outfit(a, 500));
        t.back.setGravity(Gravity.CENTER);
        t.back.setIncludeFontPadding(false);
        t.back.setOnClickListener(v -> a.finish());
        t.back.setContentDescription("Back");
        t.row.addView(t.back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 48)));
        t.disc = new FrameLayout(a);
        t.disc.setBackground(Ui.fill(a, 0xFFFFFFFF, 17));
        t.mark = new ImageView(a);
        t.mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        t.disc.addView(t.mark, new FrameLayout.LayoutParams(dp(a, 24), dp(a, 24), Gravity.CENTER));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(a, 34), dp(a, 34));
        dl.rightMargin = dp(a, 10);
        t.row.addView(t.disc, dl);
        t.disc.setVisibility(View.GONE);
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        t.title = text(a, "", 17, 0xFFFFFFFF, Type.outfit(a, 700));
        t.title.setSingleLine(true);
        t.title.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(t.title);
        t.line = text(a, "", 12, 0xB8FFFFFF, Type.inter(a));
        t.line.setSingleLine(true);
        t.line.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(t.line);
        t.row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return t;
    }

    /** Shows the mark when its picture comes; none, no disc. */
    static void mark(Top t, String url) {
        if (url == null || url.isEmpty()) {
            t.disc.setVisibility(View.GONE);
            return;
        }
        Ui.load(t.mark, url, () -> t.disc.setVisibility(View.VISIBLE), () -> t.disc.setVisibility(View.GONE));
    }

    /** A round-cornered arrow to the page before (left) or after, dark and see-through, as the picture's. */
    static TextView arrow(Context c, boolean left) {
        TextView t = text(c, left ? "‹" : "›", 30, 0xFFFFFFFF, Type.outfit(c, 400));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setBackground(Ui.fill(c, 0x38000000, 12));
        t.setContentDescription(left ? "Previous" : "Next");
        return t;
    }

    /** The window drawn under the bars, its icons light on the page's dark colour. */
    static void edgeToEdge(Activity a, View root) {
        Window w = a.getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, root);
        ic.setAppearanceLightStatusBars(false);
        ic.setAppearanceLightNavigationBars(false);
    }

    /** Pure: the wide layout (the card beside the words): a phone on its side, or any tablet. */
    static boolean wide(int widthDp, int heightDp) {
        return widthDp >= 600 || widthDp > heightDp;
    }
}
