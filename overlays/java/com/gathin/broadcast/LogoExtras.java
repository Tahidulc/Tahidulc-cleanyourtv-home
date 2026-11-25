package com.gathin.broadcast;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the family's own logo over the channel's (Event: "whitebrook road", "sports" on red, the channel's name in its box)
 * shows besides, as the mock-up they chose has it, each with its own switch (on unless switched off): the launcher's
 * Scoreboard card over TiviMate, and Homefield TV's hold-OK menu over its own player, the same code in both apps:
 * <ul>
 *   <li>Up next: five minutes before the next programme on the channel watched (the app's own guide), a panel slides out
 *   to the left of the name's box, a red block counting the minutes, "Up next · 21:00" and the programme's title, for 30
 *   seconds, once a programme.</li>
 *   <li>Prayer time: five minutes before each prayer (the Darussalam times the TV launcher has), a small pill drops under
 *   the logo: a gold crescent, "Maghrib in 5 min" and the time, for 30 seconds, once a prayer.</li>
 *   <li>Clock and weather: every five minutes the channel's name turns over to the time, the weather's icon and the
 *   temperature (the launcher's Home's last reading, under three hours old), for 30 seconds, then back; it waits while
 *   either of the others is out.</li>
 *   <li>Picture and sound marks: inside the name's box after a fine line, the formats the channel carries by its own
 *   name in the playlist (4K, UHD, HD, HDR, HDR10, HDR10+, Dolby Vision, Dolby Atmos, DTS, 5.1), the marks alone in white;
 *   always up. Never guessed: a channel whose name says none has none.</li>
 * </ul>
 * Where the programme, the prayer and the weather come from is each app's own (Source); the cover the extras go with is
 * each app's own view (Cover). The panel and the pill are drawn in a view of their own beside the logo's (Extras);
 * never where the scoreboard is.
 */
public final class LogoExtras {
    private LogoExtras() { }

    /** How long each stays out; how long before a programme or a prayer it comes; how often the clock turns over. */
    public static final long SHOW_MS = 30_000L, AHEAD_MS = 5 * 60_000L, CLOCK_EVERY_MS = 5 * 60_000L;
    /** The guide looked at this often; the prayer times this often. */
    public static final long GUIDE_MS = 30_000L, PRAYER_MS = 15_000L;
    /** The last weather reading counts this long. */
    public static final long WEATHER_MS = 3 * 3_600_000L;
    public static final long SLIDE_MS = 550L, DROP_MS = 450L, OUT_MS = 320L;

    private static final String FILE = "logo_extras";
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static Handler main;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Where an app's extras come from (the launcher's: Sky's guide, the mosque's times, Home's weather; Homefield TV's own guide and the launcher's times and weather). */
    public interface Source {
        /** The programmes on the channel watched around now, {title, start (epoch ms)} each; off the main thread. */
        List<Object[]> around(Context c, long now, long within);

        /** Whether prayer times are on at all here. */
        boolean prayerOn(Context c);

        /** The next prayer: {its label ("Maghrib"), when it begins (epoch ms), its time as shown ("17:02")}; null for none. */
        Object[] nextPrayer(Context c);

        /** The weather: {temperature, code, 1 by day}, null when there is none under WEATHER_MS old. */
        long[] weather(Context c, long now);

        /** The weather's icon for its code, by day or not; null for none. */
        android.graphics.drawable.Drawable icon(Context c, int code, boolean day);
    }

    /** The cover the extras go with (the app's own view of it). */
    public interface Cover {
        /** The name turned over to the clock (on), or back; its time, the weather's icon (null: none), its temperature. */
        void clock(boolean on, String hm, android.graphics.drawable.Drawable icon, String temp);

        /** The name's box as Event last drew it (the cover's pixels) into out; false for none. */
        boolean nameBox(RectF out);

        /** The cover's view on the screen. */
        View view();
    }

    // ---- the switches -------------------------------------------------------------------------------------------------

    public static final String NEXT = "next", PRAYER = "prayer", CLOCK = "clock", MARKS = "marks";
    /** The four, in the order the switches are listed. */
    public static final String[] ALL = {NEXT, PRAYER, CLOCK, MARKS};

    public static boolean on(Context c, String which) {
        return prefs(c).getBoolean(which, true);
    }

    public static void set(Context c, String which, boolean on) {
        prefs(c).edit().putBoolean(which, on).apply();
        if (!on) {
            if (NEXT.equals(which)) panelUntil = Math.min(panelUntil, System.currentTimeMillis());
            if (PRAYER.equals(which)) pillUntil = Math.min(pillUntil, System.currentTimeMillis());
            if (CLOCK.equals(which)) clockUntil = 0;
        }
    }

    /** Pure: a switch's name, as the switches list it. */
    public static String words(String which) {
        switch (which == null ? "" : which) {
            case NEXT: return "Up next on the logo";
            case PRAYER: return "Prayer time on the logo";
            case CLOCK: return "Clock and weather on the logo";
            case MARKS: return "Picture and sound marks on the logo";
            default: return "";
        }
    }

    // ---- pure: what the channel carries ------------------------------------------------------------------------------

    /**
     * Pure: the picture and sound marks a channel's own name carries ("UK: Sky Sports Main Event UHD HDR10+ ATMOS" 4K's
     * UHD, HDR10+, DOLBY ATMOS), in order: the picture's size, its HDR, Dolby Vision, then the sound. Only what the name
     * says.
     */
    public static List<String> marks(String channel) {
        List<String> out = new ArrayList<>();
        if (channel == null) return out;
        String n = " " + channel.toUpperCase(Locale.ROOT).replace("HDR10+", " HDR10PLUS ").replace("HDR10 +", " HDR10PLUS ")
                .replaceAll("[^A-Z0-9.]+", " ") + " ";
        if (n.contains(" 4K ") || n.contains(" 2160P ")) out.add("4K");
        else if (n.contains(" UHD ")) out.add("UHD");
        else if (n.contains(" FHD ") || n.contains(" HD ") || n.contains(" 1080P ")) out.add("HD");
        boolean dv = n.contains(" DOLBY VISION ") || n.contains(" DV ") || n.contains(" DOVI ");
        if (n.contains(" HDR10PLUS ") || n.contains(" HDR10 PLUS ")) out.add("HDR10+");
        else if (n.contains(" HDR10 ")) out.add("HDR10");
        else if (n.contains(" HDR ") && !dv) out.add("HDR");
        if (dv) out.add("DOLBY VISION");
        if (n.contains(" ATMOS ")) out.add("DOLBY ATMOS");
        else if (n.contains(" DTS ")) out.add("DTS");
        else if (n.contains(" 5.1 ") || n.contains(" DD+ ") || n.contains(" DDP ") || n.contains(" EAC3 ")) out.add("5.1");
        return out;
    }

    /** Pure: a programme's title as the panel shows it: the guide's own marks off ("New: ", "(S)", "[HD]"), else as it is. */
    public static String tidy(String title) {
        if (title == null) return "";
        String t = title.trim().replaceAll("(?i)^new:\\s*", "").replaceAll("\\s*[\\[(](S|AD|HD|SL|R|NEW)[\\])]\\s*", " ").trim();
        return t.replaceAll("\\s{2,}", " ");
    }

    /** Pure: whole minutes to go till at, rounded up (4 min 20 s: 5). */
    public static int minutesTo(long at, long now) {
        return (int) Math.max(1, (at - now + 59_999L) / 60_000L);
    }

    /** Pure: something at at is due a reminder now: within AHEAD_MS and still to come. */
    public static boolean due(long at, long now) {
        return at > now && at - now <= AHEAD_MS;
    }

    /** Pure: the clock is due to turn over: its last time long enough ago, and neither of the others out. */
    public static boolean clockDue(long lastAt, long now, boolean panelOut, boolean pillOut) {
        return !panelOut && !pillOut && now - lastAt >= CLOCK_EVERY_MS;
    }

    /** Pure: two boxes {left, top, right, bottom} meet. */
    public static boolean meet(float[] a, float[] b) {
        return a != null && b != null && a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    }

    // ---- what is out ------------------------------------------------------------------------------------------------

    private static final Set<String> shown = new HashSet<>();
    private static String panelTitle = "", panelTime = "";
    private static long panelAt, panelStart, panelUntil;
    private static String pillWords = "", pillTime = "";
    private static long pillAt, pillUntil;
    private static long clockUntil, clockLast, guideAt, prayerAt;
    private static boolean guiding;
    private static Extras view;

    /** The logo came up (anew): nothing out, the clock's five minutes from now. */
    public static void reset(long now) {
        panelUntil = pillUntil = clockUntil = 0;
        clockLast = now;
        guideAt = prayerAt = 0;
    }

    public static void view(Extras v) {
        view = v;
    }

    /** Whether the name is turned over to the clock now. */
    public static boolean clockOut(long now) {
        return now < clockUntil;
    }

    /** Whether the Up next panel or the prayer pill is out now (the tests, the previews). */
    public static boolean panelOut(long now) {
        return now < panelUntil;
    }

    public static boolean pillOut(long now) {
        return now < pillUntil;
    }

    /**
     * Each second while the logo is up over the player (main thread): what comes out next. event: the logo is Event (the
     * look with the name's box); still: nothing of the scoreboard's moments out, the logo not aside; board: the
     * scoreboard on the screen, kept clear of.
     */
    public static void tick(Context c, Source src, Cover mark, boolean event, boolean still, float[] board) {
        long now = System.currentTimeMillis();
        if (mark == null || !event) {
            if (mark != null) mark.clock(false, null, null, "");
            return;
        }
        boolean panelOut = now < panelUntil, pillOut = now < pillUntil;
        if (on(c, NEXT) && still && !guiding && now - guideAt >= GUIDE_MS) guide(c, src, board);
        if (on(c, PRAYER) && still && src.prayerOn(c) && now - prayerAt >= PRAYER_MS) prayer(c, src, now, board);
        // The clock: every five minutes for 30 s; back at once when either of the others comes out.
        boolean clock = on(c, CLOCK) && now < clockUntil && !panelOut && !pillOut;
        if (!clock && clockUntil > now) clockUntil = 0;
        if (!clock && on(c, CLOCK) && still && clockDue(clockLast, now, panelOut, pillOut)) {
            clockLast = now;
            clockUntil = now + SHOW_MS;
            clock = true;
        }
        if (clock) {
            long[] w = src.weather(c, now);
            String hm = String.format(Locale.ROOT, "%1$tH:%1$tM", now);
            mark.clock(true, hm, w == null ? null : src.icon(c, (int) w[1], w[2] == 1), w == null ? "" : w[0] + "°");
        } else {
            mark.clock(false, null, null, "");
        }
        // Drawn again only while the panel or the pill is out (or going): its minutes count down; still, nothing to draw
        // (each comes out with a draw of its own, guide() and prayer(), and draws itself on while it moves).
        if (view != null && (now < panelUntil + OUT_MS || now < pillUntil + OUT_MS)) view.invalidate();
    }

    /** The next programme on the channel watched, off the main thread; out when it is due and not out before. */
    private static void guide(Context c, Source src, float[] board) {
        guiding = true;
        guideAt = System.currentTimeMillis();
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Object[] next = null;
            try {
                List<Object[]> around = src.around(a, System.currentTimeMillis(), AHEAD_MS + 30_000L);
                for (Object[] o : around) if ((Long) o[1] > System.currentTimeMillis()) next = o;
            } catch (RuntimeException ignored) {
                // looked at again in half a minute
            }
            Object[] n = next;
            main().post(() -> {
                guiding = false;
                if (n == null) return;
                long now = System.currentTimeMillis(), st = (Long) n[1];
                String key = "p|" + st + "|" + n[0];
                if (!due(st, now) || shown.contains(key) || view == null) return;
                String title = tidy((String) n[0]);
                if (title.isEmpty() || meet(view.panelBox(), board)) return;
                remember(key);
                panelTitle = title;
                panelTime = String.format(Locale.ROOT, "%1$tH:%1$tM", st);
                panelStart = st;
                panelAt = now;
                panelUntil = now + SHOW_MS;
                view.invalidate();
            });
        });
    }

    /** The next prayer: its reminder out when it is five minutes off, once. */
    private static void prayer(Context c, Source src, long now, float[] board) {
        prayerAt = now;
        Object[] s;
        try {
            s = src.nextPrayer(c);
        } catch (RuntimeException e) {
            return;
        }
        if (s == null || s.length < 3 || view == null) return;
        long at = (Long) s[1];
        String key = "s|" + s[0] + "|" + at;
        if (!due(at, now) || shown.contains(key) || meet(view.pillBox(), board)) return;
        remember(key);
        pillWords = s[0] + " in " + minutesTo(at, now) + " min";
        pillTime = String.valueOf(s[2]);
        pillAt = now;
        pillUntil = now + SHOW_MS;
        view.invalidate();
    }

    private static void remember(String key) {
        if (shown.size() > 200) shown.clear();
        shown.add(key);
    }

    /** Pure: a weather reading {temperature, code, 1 by day} from its parts as kept, null when there is none under WEATHER_MS old. */
    public static long[] weatherOf(long at, long temp, int code, boolean day, long now) {
        if (at <= 0 || now - at > WEATHER_MS) return null;
        return new long[]{temp, code, day ? 1 : 0};
    }

    /** Previews and tests only: the Up next panel and the prayer pill out now with these words. */
    public static void showNow(String title, String time, long start, String prayer, String prayerTime) {
        long now = System.currentTimeMillis();
        if (title != null) {
            panelTitle = title;
            panelTime = time;
            panelStart = start;
            panelAt = now - SLIDE_MS;
            panelUntil = now + SHOW_MS;
        }
        if (prayer != null) {
            pillWords = prayer;
            pillTime = prayerTime;
            pillAt = now - DROP_MS;
            pillUntil = now + SHOW_MS;
        }
        if (view != null) view.invalidate();
    }

    // ---- the panel and the pill, drawn ------------------------------------------------------------------------------

    /**
     * The window's view: the Up next panel to the left of the logo's name box and the prayer pill under the logo, sized by
     * the name box as the mock-up has them (its box 38 high: the panel as high, 8 from it; the pill 26 high, 7 under).
     */
    public static final class Extras extends View {
        private final Cover mark;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
                line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF(), box = new RectF();
        private final Path path = new Path();
        private final int[] me = new int[2], it = new int[2];
        private final Typeface bold, semi, medium;
        /** Where the panel and the pill are drawn last, on the screen (for keeping clear of the scoreboard). */
        private float[] panelAtScreen, pillAtScreen;

        public Extras(Context c, Cover mark) {
            super(c);
            this.mark = mark;
            bold = Outfit.at(c, 700);
            semi = Outfit.at(c, 600);
            medium = Outfit.at(c, 500);
            line.setStyle(Paint.Style.STROKE);
        }

        /** Where the panel would be on the screen, {left, top, right, bottom} (an estimate before it is ever drawn). */
        public float[] panelBox() {
            if (panelAtScreen != null) return panelAtScreen;
            float[] b = nameBox();
            return b == null ? null : new float[]{b[0] - (b[3] - b[1]) * 7f, b[1], b[0], b[3]};
        }

        public float[] pillBox() {
            if (pillAtScreen != null) return pillAtScreen;
            float[] l = logoBox();
            return l == null ? null : new float[]{l[0], l[3], l[2], l[3] + (l[3] - l[1]) * 0.45f};
        }

        /** The logo's name box on the screen; null when it has none drawn. */
        private float[] nameBox() {
            View v = mark == null ? null : mark.view();
            if (v == null || !mark.nameBox(box) || !v.isAttachedToWindow()) return null;
            v.getLocationOnScreen(it);
            return new float[]{it[0] + box.left, it[1] + box.top, it[0] + box.right, it[1] + box.bottom};
        }

        private float[] logoBox() {
            View v = mark == null ? null : mark.view();
            if (v == null || !v.isAttachedToWindow()) return null;
            v.getLocationOnScreen(it);
            return new float[]{it[0], it[1], it[0] + v.getWidth(), it[1] + v.getHeight()};
        }

        @Override protected void onDraw(Canvas cv) {
            long now = System.currentTimeMillis();
            float[] b = nameBox();
            if (b == null) return;
            getLocationOnScreen(me);
            float bh = b[3] - b[1], ox = -me[0], oy = -me[1];
            boolean animating = false;
            if (now < panelUntil + OUT_MS && panelAt > 0) {
                animating |= panel(cv, b[0] + ox, b[1] + oy, bh, now);
            } else {
                panelAtScreen = null;
            }
            float[] l = logoBox();
            if (l != null && now < pillUntil + OUT_MS && pillAt > 0) {
                animating |= pill(cv, l[2] + ox, l[3] + oy, bh, now);
            } else {
                pillAtScreen = null;
            }
            if (animating) postInvalidateOnAnimation();
            else if (now < Math.max(panelUntil, pillUntil) + OUT_MS) postInvalidateDelayed(1_000L);
        }

        /** Up next to the left of the name box (right edge at boxL, top at top, h its box's height). */
        private boolean panel(Canvas cv, float boxL, float top, float h, long now) {
            float k = h / 38f;
            float in = ease(Math.min(1f, (now - panelAt) / (float) SLIDE_MS));
            float out = now > panelUntil ? Math.min(1f, (now - panelUntil) / (float) OUT_MS) : 0f;
            float a = in * (1f - out), sx = 0.6f + 0.4f * a, dx = 24f * k * (1f - a);
            if (a <= 0f) return out < 1f;
            int mins = Math.max(1, Math.min(9, minutesTo(panelStart, now)));
            String small = "Up next · " + panelTime;
            text.setTypeface(semi);
            text.setTextSize(15f * k);
            float most = h * 6f, titleW = Math.min(most, text.measureText(panelTitle));
            text.setTypeface(medium);
            text.setTextSize(11f * k);
            float words = Math.max(titleW, text.measureText(small));
            text.setTypeface(bold);
            text.setTextSize(20f * k);
            float blockW = Math.max(text.measureText(String.valueOf(mins)), 22f * k) + 18f * k;
            float w = blockW + 24f * k + words, right = boxL - 8f * k, left = right - w;
            panelAtScreen = new float[]{left - getLocationX(), top - getLocationY(), right - getLocationX(), top + h - getLocationY()};
            int save = cv.save();
            cv.translate(dx, 0);
            cv.scale(sx, 1f, right, 0f);
            int alpha = Math.round(255 * a);
            float rad = 6f * k;
            r.set(left, top, right, top + h);
            path.reset();
            path.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.save();
            cv.clipPath(path);
            fill.setColor(0xDB0C0E12);
            fill.setAlpha(Math.round(0xDB * a));
            cv.drawRect(r, fill);
            fill.setColor(0xFFD3262F);
            fill.setAlpha(alpha);
            cv.drawRect(left, top, left + blockW, top + h, fill);
            cv.restore();
            line.setColor(0x59FFFFFF);
            line.setAlpha(Math.round(0x59 * a));
            line.setStrokeWidth(Math.max(1f, 1.5f * k));
            cv.drawRoundRect(r, rad, rad, line);
            float cx = left + blockW / 2f;
            centre(cv, "IN", bold, 10f * k, 0.1f, 0xFFFFFFFF, alpha, cx, top + h * 0.24f);
            centre(cv, String.valueOf(mins), bold, 20f * k, 0f, 0xFFFFFFFF, alpha, cx, top + h * 0.53f);
            centre(cv, "MIN", bold, 10f * k, 0.1f, 0xFFFFFFFF, alpha, cx, top + h * 0.82f);
            float tx = left + blockW + 12f * k;
            leftText(cv, small, medium, 11f * k, 0xFFC4CAD4, alpha, tx, top + h * 0.33f, words);
            leftText(cv, panelTitle, semi, 15f * k, 0xFFFFFFFF, alpha, tx, top + h * 0.68f, words);
            cv.restoreToCount(save);
            return in < 1f || (out > 0f && out < 1f);
        }

        /** The prayer pill under the logo, its right end at right, its top a little under top (the logo's foot). */
        private boolean pill(Canvas cv, float right, float logoFoot, float boxH, long now) {
            float k = boxH / 38f, h = 26f * k;
            float in = Math.min(1f, (now - pillAt) / (float) DROP_MS);
            float out = now > pillUntil ? Math.min(1f, (now - pillUntil) / (float) OUT_MS) : 0f;
            float a = ease(in) * (1f - out), dy = -8f * k * (1f - ease(in));
            if (a <= 0f) return out < 1f;
            text.setTypeface(semi);
            text.setTextSize(12.5f * k);
            float ww = text.measureText(pillWords);
            text.setTypeface(medium);
            text.setTextSize(11.5f * k);
            float tw = text.measureText(pillTime);
            float moon = 13f * k, w = 11f * k + moon + 7f * k + ww + 7f * k + tw + 11f * k;
            float top = logoFoot + 7f * k + dy, left = right - w;
            pillAtScreen = new float[]{left - getLocationX(), top - getLocationY(), right - getLocationX(), top + h - getLocationY()};
            int alpha = Math.round(255 * a);
            r.set(left, top, right, top + h);
            fill.setColor(0xD10C0E12);
            fill.setAlpha(Math.round(0xD1 * a));
            cv.drawRoundRect(r, h / 2f, h / 2f, fill);
            line.setColor(0x8CE8C66B);
            line.setAlpha(Math.round(0x8C * a));
            line.setStrokeWidth(Math.max(1f, k));
            cv.drawRoundRect(r, h / 2f, h / 2f, line);
            // The crescent: a gold round with a round taken out of its right.
            float mx = left + 11f * k + moon / 2f, my = top + h / 2f, mr = moon / 2f;
            path.reset();
            path.addCircle(mx, my, mr, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(mx + mr * 0.55f, my - mr * 0.3f, mr * 0.85f, Path.Direction.CW);
            path.op(bite, Path.Op.DIFFERENCE);
            fill.setColor(0xFFE8C66B);
            fill.setAlpha(alpha);
            cv.drawPath(path, fill);
            float x = left + 11f * k + moon + 7f * k;
            leftText(cv, pillWords, semi, 12.5f * k, 0xFFF3E3B5, alpha, x, top + h / 2f, ww + 1f);
            leftText(cv, pillTime, medium, 11.5f * k, 0xFFB9B29C, alpha, x + ww + 7f * k, top + h / 2f, tw + 1f);
            return in < 1f || (out > 0f && out < 1f);
        }

        private float getLocationX() {
            return -me[0];
        }

        private float getLocationY() {
            return -me[1];
        }

        private void centre(Canvas cv, String s, Typeface t, float size, float spacing, int colour, int alpha, float cx, float cy) {
            text.setTypeface(t);
            text.setTextSize(size);
            text.setLetterSpacing(spacing);
            text.setColor(colour);
            text.setAlpha(alpha);
            text.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = text.getFontMetrics();
            cv.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
            text.setTextAlign(Paint.Align.LEFT);
            text.setLetterSpacing(0f);
        }

        private void leftText(Canvas cv, String s, Typeface t, float size, int colour, int alpha, float x, float cy, float room) {
            text.setTypeface(t);
            text.setTextSize(size);
            text.setColor(colour);
            text.setAlpha(alpha);
            CharSequence fit = TextUtils.ellipsize(s, new android.text.TextPaint(text), room, TextUtils.TruncateAt.END);
            Paint.FontMetrics fm = text.getFontMetrics();
            cv.drawText(fit.toString(), x, cy - (fm.ascent + fm.descent) / 2f, text);
        }
    }

    /** Pure: eased out (the mock-up's cubic-bezier(.2,.8,.2,1), near enough). */
    public static float ease(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }
}
