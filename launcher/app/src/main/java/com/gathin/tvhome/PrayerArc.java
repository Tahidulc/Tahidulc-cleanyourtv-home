package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The day's prayers on the sun's path: a curve that rises from the horizon at sunrise and sets at Maghrib (under the
 * horizon before and after, where Fajr and Isha sit), each prayer a dot where its time begins, the sun (or the moon)
 * where the time is now, the path gone by drawn solid and the rest dotted, and over it the next prayer with how long
 * until it (its jamā'ah, once its time has begun). The Control Centre's version has the five jamā'ah times under the
 * curve in boxes, the next one lit; Ambient mode's is the curve alone, each dot with its time.
 */
final class PrayerArc extends View {
    private static final int GOLD = 0xFFFFD04D, INK = 0xFF10157E;
    private final boolean compact;
    private final float u;
    private final Typeface heavy, semi, regular;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path past = new Path(), ahead = new Path(), day = new Path();
    private final RectF box = new RectF();
    private List<Prayer.Slot> slots = new ArrayList<>();
    private LocalDateTime rise;
    private Prayer.Slot next;

    PrayerArc(Context c, float unit, boolean compact, Typeface heavy, Typeface semi, Typeface regular) {
        super(c);
        this.u = unit;
        this.compact = compact;
        this.heavy = heavy;
        this.semi = semi;
        this.regular = regular;
        text.setFontFeatureSettings("tnum");
        update();
    }

    /** The day read again from what is kept (no network) and drawn again; each minute, so the sun moves on. */
    void update() {
        LocalDate d = LocalDate.now();
        slots = Prayer.slots(getContext(), d);
        rise = Prayer.sunrise(getContext(), d);
        next = Prayer.next(getContext());
        invalidate();
    }

    boolean empty() {
        return slots.size() < 5 || rise == null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (empty()) return;
        float w = getWidth(), h = getHeight();
        LocalDateTime now = LocalDateTime.now();
        boolean nextToday = next != null && next.begins.toLocalDate().equals(now.toLocalDate());

        // Over the curve: the next prayer, then when.
        if (next != null) {
            boolean begun = !next.begins.isAfter(now) && next.jamaah != null;
            LocalDateTime at = begun ? next.jamaah : next.begins;
            String name = next.label();
            String detail = (begun ? (next.jumuah ? "khutbah " : "jamā'ah ") : "") + Prayer.hm(at) + "  ·  in " + Prayer.until(Prayer.minutesTo(at));
            float base = compact ? 30 * u : 26 * u;
            text.setTypeface(heavy);
            text.setTextSize((compact ? 32 : 26) * u);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(name, 0, base, text);
            float nx = text.measureText(name) + 14 * u;
            text.setTypeface(semi);
            text.setTextSize((compact ? 22 : 18) * u);
            text.setColor(0xD9FFFFFF);
            canvas.drawText(detail, nx, base, text);
        }

        float pad = 28 * u;
        float top = compact ? 66 * u : 60 * u, horizon = compact ? 138 * u : 132 * u, below = compact ? 18 * u : 20 * u;
        LocalDateTime t0 = slots.get(0).begins.minusMinutes(30), t1 = slots.get(4).begins.plusMinutes(30);
        LocalDateTime sunset = slots.get(3).begins;
        double span = Math.max(1, Duration.between(t0, t1).toMinutes());
        double dayLen = Math.max(1, Duration.between(rise, sunset).toMinutes());
        double deep = Math.min(Math.sin(Math.PI * Duration.between(rise, t0).toMinutes() / dayLen),
                Math.sin(Math.PI * Duration.between(rise, t1).toMinutes() / dayLen));
        double belowK = below / Math.max(0.2, -deep);
        float up = horizon - top;

        // The horizon.
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setPathEffect(null);
        stroke.setStrokeWidth(1.5f * u);
        stroke.setColor(0x33FFFFFF);
        canvas.drawLine(pad - 10 * u, horizon, w - pad + 10 * u, horizon, stroke);

        // The path: gone by solid, still to come dotted, and the daylight under it softly lit.
        past.reset();
        ahead.reset();
        day.reset();
        int n = 120;
        boolean pastOpen = false, aheadOpen = false, dayOpen = false;
        float dx0 = 0;
        for (int i = 0; i <= n; i++) {
            double m = span * i / n;
            LocalDateTime t = t0.plusSeconds((long) (m * 60));
            float x = (float) (pad + (w - 2 * pad) * m / span);
            float y = yAt(t, horizon, up, belowK, dayLen);
            if (!t.isAfter(now)) {
                if (!pastOpen) { past.moveTo(x, y); pastOpen = true; } else past.lineTo(x, y);
            }
            if (!t.isBefore(now) || i == n) {
                if (!aheadOpen) {
                    if (pastOpen) {
                        float[] p = point(now, t0, span, w, pad, horizon, up, belowK, dayLen);
                        ahead.moveTo(p[0], p[1]);
                        past.lineTo(p[0], p[1]);
                        ahead.lineTo(x, y);
                    } else ahead.moveTo(x, y);
                    aheadOpen = true;
                } else ahead.lineTo(x, y);
            }
            if (!t.isBefore(rise) && !t.isAfter(sunset)) {
                if (!dayOpen) { day.moveTo(x, horizon); dx0 = x; dayOpen = true; }
                day.lineTo(x, y);
            }
        }
        if (dayOpen) {
            float[] end = point(sunset, t0, span, w, pad, horizon, up, belowK, dayLen);
            day.lineTo(end[0], horizon);
            day.lineTo(dx0, horizon);
            day.close();
            fill.setShader(new LinearGradient(0, top, 0, horizon, 0x3DFFD27A, 0x00FFD27A, Shader.TileMode.CLAMP));
            canvas.drawPath(day, fill);
            fill.setShader(null);
        }
        stroke.setStrokeWidth(3 * u);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(0xE6FFFFFF);
        canvas.drawPath(past, stroke);
        stroke.setStrokeWidth(2.5f * u);
        stroke.setColor(0x66FFFFFF);
        stroke.setPathEffect(new DashPathEffect(new float[]{2 * u, 7 * u}, 0));
        canvas.drawPath(ahead, stroke);
        stroke.setPathEffect(null);

        // Sunrise: a small mark on the horizon.
        float[] sr = point(rise, t0, span, w, pad, horizon, up, belowK, dayLen);
        stroke.setStrokeWidth(2 * u);
        stroke.setColor(0x80FFD04D);
        canvas.drawLine(sr[0], horizon - 6 * u, sr[0], horizon + 6 * u, stroke);
        text.setTypeface(regular);
        text.setTextSize(11 * u);
        text.setColor(0x99FFFFFF);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Sunrise " + Prayer.hm(rise), sr[0] + 6 * u, horizon + 16 * u, text);

        // The prayers: passed ones filled, those to come as rings, the next in gold.
        for (Prayer.Slot s : slots) {
            float[] p = point(s.begins, t0, span, w, pad, horizon, up, belowK, dayLen);
            boolean isNext = nextToday && s.name.equals(next.name);
            if (isNext) {
                fill.setColor(0x55FFD04D);
                canvas.drawCircle(p[0], p[1], 14 * u, fill);
                fill.setColor(GOLD);
                canvas.drawCircle(p[0], p[1], 8 * u, fill);
            } else if (!s.begins.isAfter(now)) {
                fill.setColor(0xE6FFFFFF);
                canvas.drawCircle(p[0], p[1], 5.5f * u, fill);
            } else {
                stroke.setStrokeWidth(2 * u);
                stroke.setColor(0xB3FFFFFF);
                canvas.drawCircle(p[0], p[1], 5.5f * u, stroke);
            }
            String label = compact ? s.label() + " " + Prayer.hm(s.jamaah != null ? s.jamaah : s.begins) : s.label();
            boolean above = p[1] <= horizon + 1;
            text.setTypeface(isNext ? semi : regular);
            text.setTextSize((compact ? 15 : 12.5f) * u);
            text.setColor(isNext ? GOLD : 0xCCFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            float lw = text.measureText(label) / 2;
            float lx = Math.max(lw, Math.min(w - lw, p[0]));
            canvas.drawText(label, lx, above ? p[1] - (isNext ? 20 : 14) * u : p[1] + (isNext ? 30 : 24) * u, text);
        }

        // Now: the sun by day, the moon by night.
        LocalDateTime at = now.isBefore(t0) ? t0 : now.isAfter(t1) ? t1 : now;
        float[] me = point(at, t0, span, w, pad, horizon, up, belowK, dayLen);
        if (!now.isBefore(rise) && now.isBefore(sunset)) {
            fill.setShader(new RadialGradient(me[0], me[1], 26 * u, 0x80FFD04D, 0x00FFD04D, Shader.TileMode.CLAMP));
            canvas.drawCircle(me[0], me[1], 26 * u, fill);
            fill.setShader(null);
            fill.setColor(GOLD);
            canvas.drawCircle(me[0], me[1], 9 * u, fill);
        } else {
            Path moon = new Path(), bite = new Path();
            moon.addCircle(me[0], me[1], 9 * u, Path.Direction.CW);
            bite.addCircle(me[0] + 4.5f * u, me[1] - 3.5f * u, 8 * u, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            fill.setColor(0xFFF2F4FF);
            canvas.drawPath(moon, fill);
        }

        if (compact) return;

        // Under the curve: the five, each with its jamā'ah large (the time it begins under it), the next one lit.
        float colTop = 182 * u, gap = 8 * u, cw = (w - 4 * gap) / 5;
        for (int i = 0; i < 5; i++) {
            Prayer.Slot s = slots.get(i);
            boolean isNext = nextToday && s.name.equals(next.name);
            boolean nowTime = !isNext && !s.begins.isAfter(now) && s.ends != null && s.ends.isAfter(now);
            float x = i * (cw + gap);
            box.set(x, colTop, x + cw, h);
            fill.setColor(isNext ? Color.WHITE : nowTime ? 0x38FFFFFF : 0x1AFFFFFF);
            canvas.drawRoundRect(box, 16 * u, 16 * u, fill);
            float cx = x + cw / 2;
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(semi);
            text.setTextSize(11.5f * u);
            text.setLetterSpacing(0.08f);
            text.setColor(isNext ? 0xCC10157E : 0xB3FFFFFF);
            canvas.drawText(s.label().toUpperCase(Locale.UK), cx, colTop + 22 * u, text);
            text.setLetterSpacing(0f);
            text.setTypeface(heavy);
            text.setTextSize(25 * u);
            text.setColor(isNext ? INK : Color.WHITE);
            canvas.drawText(Prayer.hm(s.jamaah != null ? s.jamaah : s.begins), cx, colTop + 51 * u, text);
            if (s.jamaah != null) {
                text.setTypeface(regular);
                text.setTextSize(11.5f * u);
                text.setColor(isNext ? 0x9910157E : 0x99FFFFFF);
                canvas.drawText("begins " + Prayer.hm(s.begins), cx, colTop + 68 * u, text);
            }
        }
    }

    private float[] point(LocalDateTime t, LocalDateTime t0, double span, float w, float pad, float horizon, float up, double belowK, double dayLen) {
        double m = Duration.between(t0, t).getSeconds() / 60.0;
        return new float[]{(float) (pad + (w - 2 * pad) * m / span), yAt(t, horizon, up, belowK, dayLen)};
    }

    private float yAt(LocalDateTime t, float horizon, float up, double belowK, double dayLen) {
        double s = Math.sin(Math.PI * (Duration.between(rise, t).getSeconds() / 60.0) / dayLen);
        return (float) (horizon - (s >= 0 ? up : belowK) * s);
    }
}
