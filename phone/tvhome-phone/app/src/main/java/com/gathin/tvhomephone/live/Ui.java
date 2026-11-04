package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.TextView;

import com.gathin.tvhomephone.R;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Live TV's look, the launcher's: the navy ground, glass fills, white rounded selection and live green for "now"; and
 * the guide's deep blue (dark navy into blue, light-blue selection, white writing, a red line at now).
 */
final class Ui {
    private Ui() {}

    static final int GROUND = 0xFF0D0F2C, DEEP = 0xFF0B0C21, GLASS = 0x1FFFFFFF, LINE = 0x33FFFFFF, TEXT = 0xFFFFFFFF,
            SOFT = 0xCCE4E6F5, DIM = 0x99C9CBE0, GREEN = 0xFF3DDC84, PINK = 0xFFFF4FA3, NAVY_INK = 0xFF0A0D33;
    /** The guide: its ground (top to bottom), panels, the channel column, programme blocks, now, selection, the now line. */
    static final int G_TOP = 0xFF050B2C, G_BOTTOM = 0xFF0B2766, G_PANEL = 0xF20A1A4C, G_CELL = 0xFF0A1846,
            G_BLOCK = 0xFF132D6B, G_NOW = 0xFF1E4A9E, G_PAST = 0xFF183C86, G_SELECT = 0xFF9FD8FF, G_INK = 0xFF04113A,
            G_TIME = 0xB8D2E2FF, G_LINE = 0x24FFFFFF, RED = 0xFFFF3B55, STAR = 0xFFFFC93C;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

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

    /** "19:00 – 20:00". */
    static String range(long a, long b) {
        return clock(a) + " – " + clock(b);
    }

    /** "Today", "Tomorrow", or "Thu 5 Nov". */
    static String day(Context c, long ms) {
        ZoneId z = ZoneId.systemDefault();
        LocalDate d = Instant.ofEpochMilli(ms).atZone(z).toLocalDate(), today = LocalDate.now(z);
        if (d.equals(today)) return c.getString(R.string.live_today);
        if (d.equals(today.plusDays(1))) return c.getString(R.string.live_tomorrow);
        if (d.equals(today.minusDays(1))) return c.getString(R.string.live_yesterday);
        return DAY.format(d);
    }

    /** The guide's ground: dark navy at the top into deep blue. */
    static GradientDrawable guideGround() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{G_TOP, G_BOTTOM});
    }

    /** A round 48dp icon button (white icon, a ripple), with its label for TalkBack and a long press. */
    static ImageButton icon(Context c, int drawable, int label) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(drawable);
        b.setImageTintList(ColorStateList.valueOf(TEXT));
        b.setContentDescription(c.getString(label));
        b.setTooltipText(c.getString(label));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, round(c, TEXT, 24)));
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        return b;
    }

    /** The guide's chip: light blue with navy writing when chosen, glass otherwise; 48dp to touch, 36dp to see. */
    static TextView guideChip(Context c, String s, boolean on) {
        TextView t = text(c, s, 14, on ? G_INK : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 48));
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        t.setSingleLine(true);
        styleChip(c, t, on);
        return t;
    }

    static void styleChip(Context c, TextView t, boolean on) {
        t.setTextColor(on ? G_INK : TEXT);
        int inset = dp(c, 6);
        t.setBackground(new InsetDrawable(touch(c, false, on ? G_SELECT : GLASS, 18), 0, inset, 0, inset));
        t.setSelected(on);
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
