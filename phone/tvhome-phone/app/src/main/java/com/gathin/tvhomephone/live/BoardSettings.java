package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Settings' "Scoreboard while you watch": a pointer now. Everything about the scoreboard and the channel logo (over
 * matches, the looks, My scoreboards and Ask AI's key, the timing, the pop-ups, the sounds, the ad-break screen, the
 * logo's looks and what is on it, their places and sizes, each channel's own, over other apps) is in their own pop-up
 * menus over the picture, as the family asked ("have all the options for it in the pop up menu when I click on the
 * scoreboard and channel logo": BoardMenus), so it isn't in two places. Its rows' makings stay here for the rest of
 * Settings (the intros) and the scoreboard editor.
 */
public final class BoardSettings {
    private BoardSettings() {}

    interface Flip {
        void set(boolean on);
    }

    /** The heading, its line, and where the options are now. */
    public static void add(Activity a, LinearLayout col) {
        Context c = a;
        col.addView(com.gathin.tvhomephone.sports.Ui.heading(c, c.getString(R.string.live4_settings_title), c.getColor(R.color.text_soft)));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live4_settings_line), 13, c.getColor(R.color.text_dim), false));
        TextView where = com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live_menu_settings), 15, c.getColor(R.color.text), false);
        where.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 6));
        col.addView(where);
    }

    private static TextView sub(Context c, String s) {
        TextView t = Ui.text(c, s.toUpperCase(java.util.Locale.UK), 12, c.getColor(R.color.text_soft), true);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 6));
        return t;
    }

    /** A heading and a row of choices, one picked (keys[i] shown as words[i]). */
    static void styles(Context c, LinearLayout col, String title, String[] keys, String[] words, String now,
                               java.util.function.Consumer<String> set) {
        col.addView(sub(c, title));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = new LinearLayout(c);
        hs.addView(line);
        java.util.List<android.view.View> frames = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            LinearLayout one = choice(c, words[i], k.equals(now));
            one.setOnClickListener(v -> {
                set.accept(k);
                for (android.view.View f : frames) f.setSelected(f == v);
            });
            frames.add(one);
            line.addView(one, spaced(c));
        }
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));
    }

    private static LinearLayout choice(Context c, String name, boolean on) {
        LinearLayout one = new LinearLayout(c);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        one.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8));
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_selected}, Ui.ring(c, 0x1FFFFFFF, 0xFFFFFFFF, 2, 14));
        bg.addState(new int[]{}, Ui.round(c, 0x14FFFFFF, 14));
        one.setBackground(bg);
        one.setSelected(on);
        TextView t = Ui.text(c, name, 13, 0xFFFFFFFF, false);
        t.setPadding(0, Ui.dp(c, 8), 0, 0);
        one.addView(t);
        one.setContentDescription(name);
        return one;
    }

    private static LinearLayout.LayoutParams spaced(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 10);
        return lp;
    }

    static MaterialSwitch row(Context c, LinearLayout col, String label, boolean on, Flip flip) {
        LinearLayout r = com.gathin.tvhomephone.sports.Ui.row(c);
        r.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        r.addView(com.gathin.tvhomephone.sports.Ui.text(c, label, 16, c.getColor(R.color.text), false), com.gathin.tvhomephone.sports.Ui.weight(1));
        MaterialSwitch sw = new MaterialSwitch(c);
        sw.setChecked(on);
        sw.setContentDescription(label);
        sw.setOnCheckedChangeListener((b, v) -> flip.set(v));
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        col.addView(r);
        return sw;
    }
}
