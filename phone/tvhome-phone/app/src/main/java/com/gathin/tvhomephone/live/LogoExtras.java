package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.Prayer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The extras on the family's channel logo in the player (LogoCover), as the family's approved picture has them, each
 * with its own switch (Settings, the channel's logo; on until switched off):
 *
 * <ul>
 *   <li>Up next: five minutes before the next programme in the channel's own guide, a panel slides out of the logo to
 *   the left of its name box: a red block "IN 5 MIN", "Up next · 21:00" and the programme's title. Thirty seconds,
 *   once a programme.</li>
 *   <li>Prayer time: five minutes before each prayer (the times the app already has), one pill under the logo: a gold
 *   crescent, "Maghrib in 5 min" and its time. Thirty seconds.</li>
 *   <li>Clock and weather: every five minutes the channel's name turns over to the time and the weather at home for
 *   thirty seconds, then back (drawn in the name box itself: LogoCover, ChannelMark).</li>
 * </ul>
 *
 * One at a time: Up next first, then the prayer, the clock waiting its turn. Drawn with the logo (its place, its size,
 * its fading and its moving: it reads the cover as it is each frame), never over the scoreboard or what comes out of
 * it, nor over a pop-up: they wait.
 */
final class LogoExtras extends View {
    /** What the extras need from the player's overlay. */
    interface Host {
        /** The logo's cover is up and the extras may come: not the small window, nothing being moved, no pop-up. */
        boolean ready();

        /** Whether a rect on the screen (the player's root) is clear of the scoreboard and what comes out of it. */
        boolean clear(RectF onRoot);

        /** What the stream carries: {resolution, HDR, sound} (MediaTags), null while not known. */
        String[] streamTags();
    }

    static final int NONE = 0, NEXT = 1, PRAYER = 2, CLOCK = 3;
    static final long SHOW_MS = 30_000L, AHEAD_MS = 5 * 60_000L, CLOCK_EVERY_MS = 5 * 60_000L, IN_MS = 550L, OUT_MS = 400L;
    /** The weather's pictures. */
    static final int SKY_CLEAR = 0, SKY_PARTLY = 1, SKY_CLOUD = 2, SKY_RAIN = 3, SKY_SNOW = 4, SKY_THUNDER = 5, SKY_FOG = 6;
    private static final int RED = 0xFFD3262F, GOLD = 0xFFE8C66B;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);

    private final LogoCover cover;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF r = new RectF(), onRoot = new RectF();
    private final Path path = new Path();
    private final Typeface bold, semi, medium;
    private boolean started;

    /** The programme next on this channel and when it begins (0: none known), the channel. */
    private String nextTitle = "", channel = "";
    private long nextAt;
    /** Shown already: each programme's and each prayer's key. */
    private final Set<String> done = new HashSet<>();
    /** What shows now, since when (uptime), until when; the panel's or the pill's words. */
    private int showing = NONE;
    private long since, until, lastClock;
    private String line1 = "", line2 = "", big = "";
    /** The prayers today and tomorrow, read again each quarter hour. */
    private List<Prayer.Slot> today, tomorrow;
    private long prayersAt;

    LogoExtras(Context c, LogoCover cover, Host host) {
        super(c);
        this.cover = cover;
        this.host = host;
        bold = ChannelMark.outfit(c, 700);
        semi = ChannelMark.outfit(c, 600);
        medium = ChannelMark.outfit(c, 500);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    // ---- the switches -------------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("score_overlay", Context.MODE_PRIVATE);
    }

    /** Each extra's switch: "next", "prayer", "clock", "marks" (on until switched off). */
    static boolean on(Context c, String which) {
        return prefs(c).getBoolean("logo_x." + which, true);
    }

    static void setOn(Context c, String which, boolean on) {
        prefs(c).edit().putBoolean("logo_x." + which, on).apply();
    }

    // ---- what is coming -----------------------------------------------------------------------------------------------

    void start() {
        if (started) return;
        started = true;
        lastClock = System.currentTimeMillis();
        h.post(tick);
    }

    void stop() {
        started = false;
        h.removeCallbacks(tick);
        end(false);
    }

    /** The programmes on the channel now and next (its own guide), each half minute and as the channel changes. */
    void guide(String channelKey, List<OverMatches.Programme> guide) {
        String ch = channelKey == null ? "" : channelKey;
        if (!ch.equals(channel)) {
            channel = ch;
            if (showing == NEXT) end(false);
        }
        OverMatches.Programme next = upNext(guide, System.currentTimeMillis(), Long.MAX_VALUE);
        nextTitle = next == null ? "" : next.title;
        nextAt = next == null ? 0 : next.start;
    }

    /**
     * Pure: the programme in the guide that starts after now and within ahead (the first such), or null: Up next's
     * programme (ahead: five minutes).
     */
    static OverMatches.Programme upNext(List<OverMatches.Programme> guide, long now, long ahead) {
        if (guide == null) return null;
        for (OverMatches.Programme p : guide) {
            if (p.start > now && p.start - now <= ahead && p.title != null && !p.title.trim().isEmpty()) return p;
        }
        return null;
    }

    /** Pure: the prayer beginning after now and within ahead (the first such), or null. */
    static Prayer.Slot prayerDue(List<Prayer.Slot> slots, LocalDateTime now, long ahead) {
        if (slots == null) return null;
        for (Prayer.Slot s : slots) {
            if (s == null || s.begins == null || !s.begins.isAfter(now)) continue;
            long ms = java.time.Duration.between(now, s.begins).toMillis();
            if (ms <= ahead) return s;
        }
        return null;
    }

    /** Pure: whole minutes until at, rounded up ("IN 5 MIN" at four and a half), at least one. */
    static int minutesTo(long now, long at) {
        return (int) Math.max(1, (at - now + 59_999L) / 60_000L);
    }

    /**
     * Pure: what comes next when nothing shows: Up next first, then the prayer, then the clock (when its five minutes
     * since the last are up); NONE for nothing.
     */
    static int pick(boolean next, boolean prayer, boolean clock) {
        if (next) return NEXT;
        if (prayer) return PRAYER;
        if (clock) return CLOCK;
        return NONE;
    }

    /** Pure: the clock is due: its switch on, five minutes since it last turned over (or since the logo came up). */
    static boolean clockDue(boolean on, long now, long last) {
        return on && now - last >= CLOCK_EVERY_MS;
    }

    // ---- each second ------------------------------------------------------------------------------------------------

    private void tick() {
        h.removeCallbacks(tick);
        if (!started) return;
        Context c = getContext();
        long now = System.currentTimeMillis(), up = SystemClock.uptimeMillis();
        boolean ready = host.ready();
        // The stream's own marks in the name box (only what it carries), and room for the clock there.
        String[] tags = on(c, "marks") ? host.streamTags() : null;
        cover.marks(tags == null ? null : Marks.logo(tags[0], tags[1], tags[2]));
        cover.clockRoom(on(c, "clock"));
        if (showing != NONE) {
            // Gone with the logo, or its time up, or the scoreboard needs its room.
            if (!ready || up >= until || showing != CLOCK && !host.clear(rootRect())) end(true);
        } else if (ready) {
            String nextKey = channel + "|" + nextAt;
            boolean next = on(c, "next") && nextAt > now && nextAt - now <= AHEAD_MS && !done.contains(nextKey);
            Prayer.Slot p = on(c, "prayer") ? prayerNow(c, now) : null;
            String prayerKey = p == null ? "" : "p|" + p.begins;
            boolean prayer = p != null && !done.contains(prayerKey);
            boolean clock = clockDue(on(c, "clock") && cover.nameBox(), now, lastClock);
            switch (pick(next, prayer, clock)) {
                case NEXT:
                    begin(NEXT, String.valueOf(minutesTo(now, nextAt)), "Up next · " + hm(nextAt), nextTitle);
                    if (showing == NEXT) done.add(nextKey);
                    break;
                case PRAYER:
                    begin(PRAYER, "", p.label() + " in " + minutesTo(now, toMs(p.begins)) + " min", Prayer.hm(p.begins));
                    if (showing == PRAYER) done.add(prayerKey);
                    break;
                case CLOCK:
                    clock();
                    break;
                default:
                    break;
            }
        }
        h.postDelayed(tick, 1_000L);
    }

    private Prayer.Slot prayerNow(Context c, long now) {
        if (today == null || now - prayersAt > 15 * 60_000L) {
            prayersAt = now;
            try {
                LocalDate d = LocalDate.now();
                today = Prayer.slots(c, d);
                tomorrow = Prayer.slots(c, d.plusDays(1));
            } catch (RuntimeException e) {
                today = null;
                tomorrow = null;
            }
        }
        LocalDateTime t = LocalDateTime.now();
        Prayer.Slot s = prayerDue(today, t, AHEAD_MS);
        return s != null ? s : prayerDue(tomorrow, t, AHEAD_MS);
    }

    private static long toMs(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String hm(long ms) {
        return HM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    /** Shown at once as given, fully out (a preview's): Up next or the prayer, its words. */
    void showNow(int what, String bigWords, String first, String second) {
        showing = what;
        big = bigWords;
        line1 = first;
        line2 = second;
        since = SystemClock.uptimeMillis() - IN_MS;
        until = since + IN_MS + SHOW_MS;
        invalidate();
    }

    /** The panel or the pill comes out, if there is room for it clear of the scoreboard. */
    private void begin(int what, String bigWords, String first, String second) {
        showing = what;
        big = bigWords;
        line1 = first;
        line2 = second;
        if (!host.clear(rootRect())) {
            // No room now: tried again next second (the scoreboard's moment passes).
            showing = NONE;
            return;
        }
        since = SystemClock.uptimeMillis();
        until = since + SHOW_MS;
        invalidate();
    }

    /** The clock and the weather turn over in the name box (the weather read off the main thread). */
    private void clock() {
        showing = CLOCK;
        since = SystemClock.uptimeMillis();
        until = since + SHOW_MS;
        cover.clock(true, hm(System.currentTimeMillis()), -1, "");
        App.io.execute(() -> {
            Object[] w = com.gathin.tvhomephone.sports.Weather.now();
            int sky = w == null ? SKY_PARTLY : skyOf((String) w[1]);
            String temp = w == null ? "" : w[0] + "°";
            App.main.post(() -> {
                if (showing == CLOCK) cover.clock(true, hm(System.currentTimeMillis()), sky, temp);
            });
        });
    }

    /** What shows goes (tucked away, or at once). */
    private void end(boolean animate) {
        if (showing == CLOCK) {
            cover.clock(false, null, -1, null);
            lastClock = System.currentTimeMillis();
        }
        if (showing != NONE && showing != CLOCK && animate) {
            // Tucked away over OUT_MS: drawn going while until is behind now.
            until = Math.min(until, SystemClock.uptimeMillis());
            leaving = showing;
            leftAt = SystemClock.uptimeMillis();
            invalidate();
        }
        showing = NONE;
    }

    private int leaving = NONE;
    private long leftAt;

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** The logo's anchor (its name box, or the whole logo) on this view, from the cover as it is now; null when not up. */
    private RectF anchor(boolean box) {
        if (cover.getVisibility() != VISIBLE || cover.getAlpha() <= 0.01f || cover.getWidth() <= 0) return null;
        RectF a = box ? cover.anchor() : cover.whole();
        if (a == null || a.isEmpty()) return null;
        float sx = cover.getScaleX(), sy = cover.getScaleY(), px = cover.getPivotX(), py = cover.getPivotY();
        float ox = cover.getLeft() + cover.getTranslationX() - getLeft(), oy = cover.getTop() + cover.getTranslationY() - getTop();
        RectF out = new RectF(ox + px + (a.left - px) * sx, oy + py + (a.top - py) * sy, ox + px + (a.right - px) * sx,
                oy + py + (a.bottom - py) * sy);
        return out;
    }

    /** Where what shows (or would show) sits on the screen, for the scoreboard's room. */
    private RectF rootRect() {
        RectF box = anchor(true);
        if (box == null) return onRoot;
        float u = box.height() / 38f;
        if (showing == NEXT) {
            float w = panelW(u);
            onRoot.set(box.left - 8f * u - w, box.top, box.left - 8f * u, box.bottom);
        } else if (showing == PRAYER) {
            RectF all = anchor(false);
            float right = all == null ? box.right : all.right, w = pillW(u);
            onRoot.set(right - w, box.bottom + 7f * u, right, box.bottom + 33f * u);
        } else {
            onRoot.set(box);
        }
        onRoot.offset(getLeft(), getTop());
        return onRoot;
    }

    @Override protected void onDraw(Canvas cv) {
        long up = SystemClock.uptimeMillis();
        int what = showing != NONE && showing != CLOCK ? showing : leaving;
        if (what == NONE || what == CLOCK) return;
        float in, out = 0f;
        if (showing == what) {
            in = Math.min(1f, (up - since) / (float) IN_MS);
        } else {
            in = 1f;
            out = Math.min(1f, (up - leftAt) / (float) OUT_MS);
            if (out >= 1f) {
                leaving = NONE;
                return;
            }
        }
        RectF box = anchor(true);
        if (box == null) {
            postInvalidateOnAnimation();
            return;
        }
        float alpha = cover.getAlpha() * ease(in) * (1f - ease(out));
        float u = box.height() / 38f;
        if (what == NEXT) panel(cv, box, u, in, out, alpha);
        else pill(cv, box, u, in, out, alpha);
        // Each frame while up: it goes with the logo (moved, faded, sized), and in or out.
        postInvalidateOnAnimation();
    }

    private static float ease(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    /** Up next's panel's width at u (a pixel of the picture's, its name box 38 high). */
    private float panelW(float u) {
        float block = blockW(u);
        text.setTypeface(medium);
        text.setTextSize(11f * u);
        float a = text.measureText(line1);
        text.setTypeface(semi);
        text.setTextSize(15f * u);
        float b = Math.min(text.measureText(line2), 260f * u);
        return block + 24f * u + Math.max(a, b);
    }

    private float blockW(float u) {
        text.setTypeface(bold);
        text.setTextSize(20f * u);
        return Math.max(text.measureText(big), 26f * u) + 18f * u;
    }

    /**
     * Up next: to the left of the name box, as tall as it, sliding out of it (from 24 along and three fifths as wide)
     * and back; the red block's IN, the minutes, MIN; "Up next · 21:00" and the title, cut short.
     */
    private void panel(Canvas cv, RectF box, float u, float in, float out, float alpha) {
        float w = panelW(u), right = box.left - 8f * u, top = box.top, bottom = box.bottom;
        float e = ease(in), shift = (1f - e) * 24f * u, sx = 0.6f + 0.4f * e;
        if (out > 0f) {
            float o = ease(out);
            shift = o * 24f * u;
            sx = 1f - 0.4f * o;
        }
        cv.save();
        cv.translate(shift, 0f);
        cv.scale(sx, 1f, right, (top + bottom) / 2f);
        int a = Math.round(255 * alpha);
        float rad = 6f * u, left = right - w;
        r.set(left, top, right, bottom);
        path.reset();
        path.addRoundRect(r, rad, rad, Path.Direction.CW);
        fill.setColor(0xDB0C0E12);
        fill.setAlpha(Math.round(0xDB * alpha));
        cv.drawPath(path, fill);
        cv.save();
        cv.clipPath(path);
        float bw = blockW(u);
        fill.setColor(RED);
        fill.setAlpha(a);
        cv.drawRect(left, top, left + bw, bottom, fill);
        cv.restore();
        line.setColor(0x59FFFFFF);
        line.setAlpha(Math.round(0x59 * alpha));
        line.setStrokeWidth(1.5f * u);
        cv.drawRoundRect(r, rad, rad, line);
        // The red block: IN over the minutes over MIN, centred.
        float cx = left + bw / 2f, cy = (top + bottom) / 2f;
        centred(cv, "IN", bold, 10f * u, 0.1f, 0xFFFFFFFF, a, cx, cy - 13f * u);
        centred(cv, big, bold, 20f * u, 0f, 0xFFFFFFFF, a, cx, cy + 0.5f * u);
        centred(cv, "MIN", bold, 10f * u, 0.1f, 0xFFFFFFFF, a, cx, cy + 13f * u);
        // The words: Up next and the time over the title.
        float tx = left + bw + 12f * u, room = right - tx - 12f * u;
        leftText(cv, line1, medium, 11f * u, 0xFFC4CAD4, a, tx, cy - 7f * u, room);
        leftText(cv, line2, semi, 15f * u, 0xFFFFFFFF, a, tx, cy + 8f * u, room);
        cv.restore();
    }

    private float pillW(float u) {
        text.setTypeface(semi);
        text.setTextSize(12.5f * u);
        float a = text.measureText(line1);
        text.setTypeface(medium);
        text.setTextSize(11.5f * u);
        float b = text.measureText(line2);
        return 11f * u + 13f * u + 7f * u + a + 7f * u + b + 11f * u;
    }

    /** The prayer's pill under the logo, its right end with the logo's, dropping in from just above. */
    private void pill(Canvas cv, RectF box, float u, float in, float out, float alpha) {
        RectF all = anchor(false);
        float right = all == null ? box.right : all.right, w = pillW(u), ph = 26f * u;
        float top = box.bottom + 7f * u - (1f - ease(in)) * 8f * u - ease(out) * 8f * u;
        int a = Math.round(255 * alpha);
        r.set(right - w, top, right, top + ph);
        fill.setColor(0xD10C0E12);
        fill.setAlpha(Math.round(0xD1 * alpha));
        cv.drawRoundRect(r, ph / 2f, ph / 2f, fill);
        line.setColor(0x8CE8C66B);
        line.setAlpha(Math.round(0x8C * alpha));
        line.setStrokeWidth(Math.max(1f, u));
        cv.drawRoundRect(r, ph / 2f, ph / 2f, line);
        float x = right - w + 11f * u, cy = top + ph / 2f, s = 13f * u;
        crescent(cv, x, cy - s / 2f, s, a);
        x += s + 7f * u;
        text.setTypeface(semi);
        text.setTextSize(12.5f * u);
        leftText(cv, line1, semi, 12.5f * u, 0xFFF3E3B5, a, x, cy, Float.MAX_VALUE);
        x += text.measureText(line1) + 7f * u;
        leftText(cv, line2, medium, 11.5f * u, 0xFFB9B29C, a, x, cy, Float.MAX_VALUE);
    }

    /** The gold crescent (the picture's, 13 square). */
    private void crescent(Canvas cv, float x, float y, float s, int a) {
        float k = s / 13f;
        path.reset();
        path.addCircle(x + 5.6f * k + 0.4f * k, y + 6.8f * k, 5.6f * k, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(x + 9.2f * k, y + 4.9f * k, 4.6f * k, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        fill.setColor(GOLD);
        fill.setAlpha(a);
        cv.drawPath(path, fill);
    }

    private void centred(Canvas cv, String s, Typeface t, float size, float spacing, int colour, int a, float cx, float cy) {
        text.setTypeface(t);
        text.setTextSize(size);
        text.setLetterSpacing(spacing);
        text.setColor(colour);
        text.setAlpha(a);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setLetterSpacing(0f);
    }

    private void leftText(Canvas cv, String s, Typeface t, float size, int colour, int a, float x, float cy, float room) {
        text.setTypeface(t);
        text.setTextSize(size);
        text.setColor(colour);
        text.setAlpha(a);
        text.setTextAlign(Paint.Align.LEFT);
        CharSequence fit = room < Float.MAX_VALUE ? TextUtils.ellipsize(s, new android.text.TextPaint(text), Math.max(1f, room),
                TextUtils.TruncateAt.END) : s;
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(fit.toString(), x, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    // ---- the weather's pictures -------------------------------------------------------------------------------------

    /** Pure: the weather's picture for the app's weather words ("Clear", "Partly cloudy", "Rain"...). */
    static int skyOf(String words) {
        String w = words == null ? "" : words.toLowerCase(Locale.ROOT);
        if (w.startsWith("clear")) return SKY_CLEAR;
        if (w.startsWith("partly")) return SKY_PARTLY;
        if (w.startsWith("cloudy")) return SKY_CLOUD;
        if (w.contains("fog")) return SKY_FOG;
        if (w.contains("snow")) return SKY_SNOW;
        if (w.contains("thunder")) return SKY_THUNDER;
        if (w.contains("rain") || w.contains("drizzle") || w.contains("shower")) return SKY_RAIN;
        return SKY_PARTLY;
    }

    /**
     * The weather's picture h high (22 by 16 as the family's picture draws it), from (x, y), in fine lines of the ink:
     * the sun, the sun behind a cloud, a cloud, rain, snow, thunder, fog. The cloud filled dark, as in the picture.
     */
    static void sky(Canvas cv, Paint line, Paint fill, int kind, float x, float y, float h, int ink) {
        if (kind < 0) kind = SKY_PARTLY;
        float k = h / 16f;
        line.setShader(null);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(ink);
        line.setStrokeWidth(Math.max(1f, 1.6f * k));
        line.setStrokeCap(Paint.Cap.ROUND);
        Paint.Style was = fill.getStyle();
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        if (kind == SKY_CLEAR) {
            sun(cv, line, x + 11f * k, y + 8f * k, 3.6f * k, true);
            fill.setStyle(was);
            return;
        }
        if (kind == SKY_FOG) {
            for (int i = 0; i < 3; i++) cv.drawLine(x + (3f + i % 2 * 2f) * k, y + (4f + i * 4f) * k, x + (19f - i % 2 * 2f) * k, y + (4f + i * 4f) * k, line);
            fill.setStyle(was);
            return;
        }
        boolean low = kind == SKY_PARTLY;
        if (low) sun(cv, line, x + 6.5f * k, y + 5.5f * k, 3f * k, false);
        float dy = low || kind == SKY_CLOUD ? 0f : -3f * k;
        Path c = cloud(x, y + dy, k, low);
        fill.setColor(0xFF2A3240);
        cv.drawPath(c, fill);
        cv.drawPath(c, line);
        if (kind == SKY_RAIN) {
            for (int i = 0; i < 3; i++) cv.drawLine(x + (8f + i * 3.5f) * k, y + 13.2f * k, x + (7f + i * 3.5f) * k, y + 15.4f * k, line);
        } else if (kind == SKY_SNOW) {
            for (int i = 0; i < 3; i++) cv.drawPoint(x + (8f + i * 3.5f) * k, y + 14.4f * k, line);
        } else if (kind == SKY_THUNDER) {
            Path bolt = new Path();
            bolt.moveTo(x + 12.5f * k, y + 11.5f * k);
            bolt.lineTo(x + 10.5f * k, y + 14f * k);
            bolt.lineTo(x + 12.5f * k, y + 14f * k);
            bolt.lineTo(x + 11f * k, y + 16f * k);
            cv.drawPath(bolt, line);
        }
        fill.setStyle(was);
    }

    /** The sun: its ring and its rays, all round alone, above and to the left behind a cloud. */
    private static void sun(Canvas cv, Paint line, float cx, float cy, float rad, boolean allRound) {
        cv.drawCircle(cx, cy, rad, line);
        float in = rad * 1.55f, outR = rad * 1.85f;
        int[] degrees = allRound ? new int[]{0, 45, 90, 135, 180, 225, 270, 315} : new int[]{180, 225, 270, 315};
        for (int d : degrees) {
            double ang = Math.toRadians(d);
            cv.drawLine(cx + (float) Math.cos(ang) * in, cy + (float) Math.sin(ang) * in, cx + (float) Math.cos(ang) * outR,
                    cy + (float) Math.sin(ang) * outR, line);
        }
    }

    /** The cloud (the picture's: 8 to 20.5 across, its foot at 14.5), in from x, y at k a unit; wider alone. */
    private static Path cloud(float x, float y, float k, boolean behindSun) {
        float l = behindSun ? 8f : 4.5f, rgt = 20.5f;
        Path p = new Path();
        p.addCircle(x + (rgt - 3.5f) * k, y + 11f * k, 3.5f * k, Path.Direction.CW);
        Path a = new Path();
        a.addCircle(x + (l + 4.9f) * k, y + 9.7f * k, 4.6f * k, Path.Direction.CW);
        Path b = new Path();
        b.addCircle(x + (l + 0.2f) * k, y + 11.7f * k, 2.8f * k, Path.Direction.CW);
        Path base = new Path();
        base.addRect(x + l * k, y + 11f * k, x + (rgt - 3.5f) * k, y + 14.5f * k, Path.Direction.CW);
        p.op(a, Path.Op.UNION);
        p.op(b, Path.Op.UNION);
        p.op(base, Path.Op.UNION);
        return p;
    }
}
