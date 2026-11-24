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
    /**
     * Live TV in Sky's guide's look: its blue (lighter top left, deeper bottom right), panels, the channel column, the
     * programmes (flat: no fill, split by thin lines), what's on now a shade lighter, the chosen programme lighter still,
     * the accent (white) and the ink on it (Sky's deep blue), times, the lines, the now line, the favourite's star, and
     * the menu's chosen box.
     */
    static final int G_TOP = 0xFF2A86E0, G_MID = 0xFF1564C6, G_BOTTOM = 0xFF0B47A3, G_PANEL = 0xF2125CBB,
            G_CELL = 0xFF1460C2, G_BLOCK = 0x00000000, G_NOW = 0x1AFFFFFF, G_PAST = 0x12FFFFFF, G_CHOSEN = 0x52FFFFFF,
            G_SELECT = 0xFFFFFFFF, G_INK = 0xFF0C3483, G_TIME = 0xD9FFFFFF, G_LINE = 0x40FFFFFF, RED = 0xFFFF3B55,
            STAR = 0xFFFFC93C, G_MENU = 0xFF0C3483;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    /** sp in pixels (the phone's text size setting included). */
    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

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

    /** "19:00", or "7.00pm" as Sky's guide writes it: Settings › Appearance › Programme times (Times). */
    static String clock(long ms) {
        java.time.ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return Times.hm(t.getHour(), t.getMinute(), Times.h24);
    }

    /** "30m", "1h 05m": a programme's length as Sky gives it. */
    static String length(long ms) {
        long m = Math.max(1, Math.round(ms / 60_000.0));
        return m >= 60 ? m / 60 + "h " + (m % 60 < 10 ? "0" : "") + m % 60 + "m" : m + "m";
    }

    /** "19:00 – 20:00", or "7.00pm – 8.00pm". */
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

    /** The guide's ground: Sky's blue, lighter top left, deeper bottom right, with soft circles behind. */
    static android.graphics.drawable.Drawable guideGround() {
        return new SkyGround();
    }

    private static final class SkyGround extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.graphics.Shader shader;
        private final android.graphics.Rect laid = new android.graphics.Rect();

        @Override public void draw(@androidx.annotation.NonNull android.graphics.Canvas cv) {
            android.graphics.Rect r = getBounds();
            if (shader == null || !r.equals(laid)) {
                laid.set(r);
                shader = new android.graphics.LinearGradient(r.left, r.top, r.right, r.bottom, new int[]{G_TOP, G_MID, G_BOTTOM},
                        new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP);
            }
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setShader(shader);
            cv.drawRect(r, paint);
            paint.setShader(null);
            float w = r.width(), h = r.height(), u = Math.min(w, h);
            paint.setColor(0x0FFFFFFF);
            cv.drawCircle(w * 0.85f, h * 0.18f, u * 0.42f, paint);
            paint.setColor(0x0AFFFFFF);
            cv.drawCircle(w * 0.10f, h * 0.86f, u * 0.36f, paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(u * 0.006f + 1);
            paint.setColor(0x14FFFFFF);
            cv.drawCircle(w * 0.72f, h * 0.62f, u * 0.30f, paint);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    /** Sky's menu's chosen item: a deep blue box with a light line glowing along its top and bottom edges. */
    private static final class MenuBox extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        private final float radius, edge;

        MenuBox(Context c) {
            radius = dp(c, 8);
            edge = Math.max(2, dp(c, 1.5f));
        }

        @Override public void draw(@androidx.annotation.NonNull android.graphics.Canvas cv) {
            android.graphics.Rect r = getBounds();
            box.set(r);
            paint.setShader(null);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(G_MENU);
            cv.drawRoundRect(box, radius, radius, paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(edge / 1.5f);
            paint.setColor(0x73FFFFFF);
            cv.drawRoundRect(box, radius, radius, paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            float cx = r.exactCenterX(), half = r.width() * 0.34f;
            paint.setShader(new android.graphics.LinearGradient(cx - half, 0, cx + half, 0,
                    new int[]{0x00FFFFFF, 0xE6FFFFFF, 0x00FFFFFF}, null, android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(cx - half, r.top, cx + half, r.top + edge, paint);
            cv.drawRect(cx - half, r.bottom - edge, cx + half, r.bottom, paint);
            paint.setShader(null);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
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

    /** The guide's chip: Sky's menu box when chosen, plain words on the blue otherwise; 48dp to touch, 36dp to see. */
    static TextView guideChip(Context c, String s, boolean on) {
        TextView t = text(c, s, 14, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 48));
        t.setMaxLines(1);
        styleChip(c, t, on);
        return t;
    }

    static void styleChip(Context c, TextView t, boolean on) {
        t.setTextColor(on ? TEXT : 0xE6FFFFFF);
        int inset = dp(c, 6);
        android.graphics.drawable.Drawable look = on ? new MenuBox(c) : round(c, 0x14FFFFFF, 8);
        t.setBackground(new InsetDrawable(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), look, round(c, TEXT, 8)),
                0, inset, 0, inset));
        // After the background (an inset drawable sets the padding to its insets): room either side of the words.
        t.setPadding(dp(c, 16), inset, dp(c, 16), inset);
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

    /** The small "4K" mark on a 4K/UHD channel's logo: white on black in a white outline, as the TV's 4K UHD box. */
    static TextView uhdBadge(Context c) {
        TextView t = text(c, "4K", 9, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setPadding(dp(c, 4), dp(c, 1), dp(c, 4), dp(c, 1));
        t.setBackground(ring(c, 0xE6000000, TEXT, 1, 3));
        t.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    /** A channel's logo: its playlist's own, else Sky's for its name (a 4K channel's is its channel's); null if none. */
    static String logo(Channel ch) {
        if (ch == null) return null;
        if (!ch.logo.isEmpty()) return ch.logo;
        // By the playlist's name (the family's own may say nothing Sky knows), else the one it shows.
        String sky = com.gathin.tvhomephone.sports.ChannelLogos.sky(ch.playlistName);
        return sky != null ? sky : com.gathin.tvhomephone.sports.ChannelLogos.sky(ch.name);
    }
}
