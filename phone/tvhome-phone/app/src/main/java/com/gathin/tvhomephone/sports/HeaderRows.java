package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.home.Type;

/**
 * The club page's head: the title (Back, the crest, the club's name and standing), the Players and Management pills,
 * and the lines' pills. As the family's picture has them when there is room: the pills in one row under the title,
 * Players and Management at its left and the lines at its right; all three in one row on a wide, short screen (a phone
 * on its side); on a phone upright, a row each, the lines' row sliding sideways when it is wider than the screen.
 */
final class HeaderRows extends ViewGroup {
    final View title;
    final LinearLayout left;
    final HorizontalScrollView right;
    final LinearLayout rightRow;
    private int mode = 3;
    private final int gap, rowGap, titleMin;

    HeaderRows(Context c, View title) {
        super(c);
        this.title = title;
        addView(title);
        left = new LinearLayout(c);
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        addView(left);
        right = new HorizontalScrollView(c);
        right.setHorizontalScrollBarEnabled(false);
        right.setOverScrollMode(OVER_SCROLL_NEVER);
        right.setClipToPadding(false);
        rightRow = new LinearLayout(c);
        rightRow.setOrientation(LinearLayout.HORIZONTAL);
        rightRow.setGravity(Gravity.CENTER_VERTICAL);
        right.addView(rightRow, new LayoutParams(-2, -2));
        addView(right);
        float d = getResources().getDisplayMetrics().density;
        gap = Math.round(14 * d);
        rowGap = Math.round(10 * d);
        titleMin = Math.round(190 * d);
    }

    /** The pills' rows: one row, the title then one row, or a row each. */
    int mode() {
        return mode;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int inner = Math.max(0, w - getPaddingLeft() - getPaddingRight());
        int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        title.measure(free, free);
        left.measure(free, free);
        rightRow.measure(free, free);
        int tw = title.getMeasuredWidth(), lw = left.getMeasuredWidth(), rw = rightRow.getMeasuredWidth();
        int tallScreen = getResources().getConfiguration().screenHeightDp;
        boolean shortScreen = tallScreen < 520;
        if (shortScreen && Math.min(tw, titleMin) + lw + rw + 2 * gap <= inner) mode = 1;
        else if (lw + rw + gap <= inner) mode = 2;
        else mode = 3;
        int h = getPaddingTop() + getPaddingBottom();
        if (mode == 1) {
            int tWidth = Math.max(0, inner - lw - rw - 2 * gap);
            title.measure(MeasureSpec.makeMeasureSpec(Math.min(tw, tWidth), MeasureSpec.EXACTLY), free);
            right.measure(MeasureSpec.makeMeasureSpec(rw, MeasureSpec.EXACTLY), free);
            h += Math.max(title.getMeasuredHeight(), Math.max(left.getMeasuredHeight(), right.getMeasuredHeight()));
        } else {
            title.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), free);
            right.measure(MeasureSpec.makeMeasureSpec(mode == 2 ? rw : inner, MeasureSpec.EXACTLY), free);
            h += title.getMeasuredHeight() + rowGap;
            if (mode == 2) h += Math.max(left.getMeasuredHeight(), right.getMeasuredHeight());
            else h += left.getMeasuredHeight() + rowGap / 2 + right.getMeasuredHeight();
        }
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = getPaddingLeft(), y = getPaddingTop(), end = r - l - getPaddingRight();
        if (mode == 1) {
            int rowH = Math.max(title.getMeasuredHeight(), Math.max(left.getMeasuredHeight(), right.getMeasuredHeight()));
            place(title, x, y + (rowH - title.getMeasuredHeight()) / 2);
            int lx = x + title.getMeasuredWidth() + gap;
            place(left, lx, y + (rowH - left.getMeasuredHeight()) / 2);
            place(right, end - right.getMeasuredWidth(), y + (rowH - right.getMeasuredHeight()) / 2);
            return;
        }
        place(title, x, y);
        y += title.getMeasuredHeight() + rowGap;
        if (mode == 2) {
            int rowH = Math.max(left.getMeasuredHeight(), right.getMeasuredHeight());
            place(left, x, y + (rowH - left.getMeasuredHeight()) / 2);
            place(right, end - right.getMeasuredWidth(), y + (rowH - right.getMeasuredHeight()) / 2);
        } else {
            place(left, x, y);
            y += left.getMeasuredHeight() + rowGap / 2;
            place(right, x, y);
        }
    }

    private static void place(View v, int x, int y) {
        v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
    }

    // ---- pills ----------------------------------------------------------------------------------------------------

    /** A pill as the picture draws them: small spaced capitals, white when on, dark when off. */
    static TextView pill(Context c, String words, int ink) {
        TextView t = new TextView(c);
        t.setText(words.toUpperCase(java.util.Locale.ROOT));
        t.setTypeface(Type.outfit(c, 600));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        t.setLetterSpacing(0.1f);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        float d = c.getResources().getDisplayMetrics().density;
        t.setPadding(Math.round(15 * d), 0, Math.round(15 * d), 0);
        t.setMinHeight(Math.round(34 * d));
        t.setTag(ink);
        return t;
    }

    /** A pill lit (white, the ground's ink on it) or not (dark, white words). */
    static void light(TextView pill, boolean on) {
        float d = pill.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(17 * d);
        if (on) {
            g.setColor(0xFFFFFFFF);
        } else {
            g.setColor(0x3D000000);
            g.setStroke(Math.max(1, Math.round(d)), 0x24FFFFFF);
        }
        pill.setBackground(g);
        Object ink = pill.getTag();
        pill.setTextColor(on ? (ink instanceof Integer ? (Integer) ink : 0xFF141726) : 0xF2FFFFFF);
        pill.setSelected(on);
    }

    static LinearLayout.LayoutParams spaced(Context c, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!first) lp.leftMargin = Math.round(8 * c.getResources().getDisplayMetrics().density);
        return lp;
    }
}
