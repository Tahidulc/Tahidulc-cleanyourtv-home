package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;

import com.gathin.tvhomephone.glass.BlurBehind;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.GlassDrawable;
import com.gathin.tvhomephone.glass.Icons;

import java.util.ArrayList;
import java.util.List;

/**
 * A small menu of frosted glass that drops from the button that opened it (the cover page's "…", the season chip): the
 * page dims a little behind it, its glass blurs what is under it, and each choice is a row with its icon (a tick on
 * the one in use). A tap outside it, or Back, closes it; a long list scrolls inside it.
 */
final class CoverMenu {
    /** One choice: its words, a quieter note after them (or null), its icon (or null), whether it is on, what it does. */
    static final class Entry {
        final String words, note;
        final Drawable icon;
        final boolean on;
        final Runnable run;

        Entry(String words, String note, Drawable icon, boolean on, Runnable run) {
            this.words = words;
            this.note = note;
            this.icon = icon;
            this.on = on;
            this.run = run;
        }
    }

    private final FrameLayout root, layer;
    private final View shade;
    private final BlurBehind card;
    private final OnBackPressedCallback back;
    /** What was under it, hidden from TalkBack while it is open, and how each was before. */
    private final List<View> hidden = new ArrayList<>();
    private final List<Integer> was = new ArrayList<>();
    private boolean open = true;

    /**
     * Opens over root, below the anchor (above it when there's no room), lined up with its right edge (fromEnd) or its
     * left; bars are the system bars' insets to stay clear of; sources are what its glass blurs.
     */
    static CoverMenu show(AppCompatActivity a, FrameLayout root, View anchor, boolean fromEnd, Insets bars, List<Entry> entries,
                          View... sources) {
        return new CoverMenu(a, root, anchor, fromEnd, bars, entries, sources);
    }

    private CoverMenu(AppCompatActivity a, FrameLayout root, View anchor, boolean fromEnd, Insets bars, List<Entry> entries,
                      View[] sources) {
        this.root = root;
        Context c = a;
        int edge = Glass.dp(c, 12), gap = Glass.dp(c, 8);
        int rw = root.getWidth(), rh = root.getHeight();
        layer = new FrameLayout(c);
        shade = new View(c);
        shade.setBackgroundColor(0x52000000);
        shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shade.setOnClickListener(v -> close(null));
        layer.addView(shade, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        card = new BlurBehind(c, 20, GlassDrawable.CARD);
        card.setTint(0xB314151C);
        card.setSources(sources);
        card.setElevation(Glass.dp(c, 12));
        int maxH = Math.max(Glass.dp(c, 120), Math.min(Glass.dp(c, 460), rh - bars.top - bars.bottom - 2 * edge));
        ScrollView list = new ScrollView(c) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        list.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, Glass.dp(c, 4), 0, Glass.dp(c, 4));
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) {
                View line = new View(c);
                line.setBackgroundColor(0x1AFFFFFF);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Glass.dp(c, 0.5f)));
                lp.leftMargin = Glass.dp(c, 18);
                rows.addView(line, lp);
            }
            rows.addView(row(c, e, () -> close(e.run)));
        }
        list.addView(rows);
        card.addView(list, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // Where it goes: by its anchor, inside the screen and clear of the bars.
        int width = Math.max(Glass.dp(c, 180), Math.min(Glass.dp(c, 264), rw - bars.left - bars.right - 2 * edge));
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.max(1, rh), View.MeasureSpec.AT_MOST));
        int h = card.getMeasuredHeight();
        int[] at = new int[2], base = new int[2];
        anchor.getLocationInWindow(at);
        root.getLocationInWindow(base);
        int ax = at[0] - base[0], ay = at[1] - base[1];
        int left = fromEnd ? ax + anchor.getWidth() - width : ax;
        left = Math.max(bars.left + edge, Math.min(left, rw - bars.right - edge - width));
        int below = ay + anchor.getHeight() + gap;
        boolean down = below + h <= rh - bars.bottom - edge;
        int top = down ? below : ay - gap - h;
        if (!down && top < bars.top + edge) {
            // Room neither under nor over it: as low as it fits.
            top = Math.max(bars.top + edge, rh - bars.bottom - edge - h);
            down = true;
        }
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        cp.leftMargin = left;
        cp.topMargin = top;
        layer.addView(card, cp);

        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            hidden.add(v);
            was.add(v.getImportantForAccessibility());
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        root.addView(layer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // It grows out of its button.
        card.setPivotX(fromEnd ? width : 0);
        card.setPivotY(down ? 0 : h);
        card.setAlpha(0f);
        card.setScaleX(0.88f);
        card.setScaleY(0.88f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        shade.setAlpha(0f);
        shade.animate().alpha(1f).setDuration(200).start();
        if (rows.getChildCount() > 0) {
            View first = rows.getChildAt(0);
            first.post(() -> first.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        }

        back = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                close(null);
            }
        };
        a.getOnBackPressedDispatcher().addCallback(a, back);
    }

    boolean isOpen() {
        return open;
    }

    /** Closes it (quickly), then runs then, if any. */
    void close(Runnable then) {
        if (!open) return;
        open = false;
        back.remove();
        for (int i = 0; i < hidden.size(); i++) hidden.get(i).setImportantForAccessibility(was.get(i));
        card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(130).start();
        shade.animate().alpha(0f).setDuration(150).withEndAction(() -> root.removeView(layer)).start();
        if (then != null) then.run();
    }

    private static View row(Context c, Entry e, Runnable click) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Glass.dp(c, 50));
        r.setPadding(Glass.dp(c, 18), Glass.dp(c, 10), Glass.dp(c, 16), Glass.dp(c, 10));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF), null, new ColorDrawable(0xFFFFFFFF)));
        TextView words = Glass.text(c, e.words, 15, Glass.TEXT, Glass.body(c));
        words.setSingleLine(true);
        words.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        boolean noted = e.note != null && !e.note.isEmpty();
        if (noted) {
            TextView note = Glass.text(c, e.note, 13, Glass.FAINT, Glass.body(c));
            note.setSingleLine(true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            np.leftMargin = Glass.dp(c, 10);
            r.addView(note, np);
        }
        Drawable icon = e.icon != null ? e.icon : e.on ? new Icons(c, Icons.CHECK, Glass.TEXT) : null;
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Glass.dp(c, 20), Glass.dp(c, 20));
            ip.leftMargin = Glass.dp(c, 12);
            r.addView(iv, ip);
        }
        r.setClickable(true);
        r.setFocusable(true);
        r.setSelected(e.on);
        r.setContentDescription(e.words + (noted ? ", " + e.note : ""));
        r.setOnClickListener(v -> click.run());
        return r;
    }
}
