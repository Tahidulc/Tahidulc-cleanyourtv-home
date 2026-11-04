package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Live TV's look, the launcher's: the navy ground, glass fills, white rounded selection and live green for "now". */
final class Ui {
    private Ui() {}

    static final int GROUND = 0xFF0D0F2C, DEEP = 0xFF0B0C21, GLASS = 0x1FFFFFFF, LINE = 0x33FFFFFF, TEXT = 0xFFFFFFFF,
            SOFT = 0xCCE4E6F5, DIM = 0x99C9CBE0, GREEN = 0xFF3DDC84, PINK = 0xFFFF4FA3, NAVY_INK = 0xFF0A0D33;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(Context c, int fill, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    static GradientDrawable ring(Context c, int fill, int stroke, float width, float radius) {
        GradientDrawable d = round(c, fill, radius);
        d.setStroke(dp(c, width), stroke);
        return d;
    }

    /** A touchable surface: its fill (or a white rounded rim when selected), with a ripple. */
    static RippleDrawable touch(Context c, boolean selected, int fill, float radius) {
        GradientDrawable base = selected ? ring(c, 0x26FFFFFF, TEXT, 2, radius) : round(c, fill, radius);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, round(c, TEXT, radius));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** A group's chip: white with navy writing when chosen, glass otherwise. */
    static TextView chip(Context c, String s, boolean on) {
        TextView t = text(c, s, 14, on ? NAVY_INK : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 40));
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        t.setBackground(touch(c, false, on ? TEXT : GLASS, 20));
        t.setSingleLine(true);
        return t;
    }

    static String clock(long ms) {
        return HM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    /** A channel's initials for when it has no logo. */
    static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : Names.plain(name).split("[^A-Za-z0-9]+")) {
            if (w.isEmpty()) continue;
            if (w.length() == 2 && b.length() == 0 && w.equalsIgnoreCase("uk")) continue;
            b.append(Character.toUpperCase(w.charAt(0)));
            if (b.length() >= 3) break;
        }
        return b.length() == 0 ? "TV" : b.toString();
    }
}
