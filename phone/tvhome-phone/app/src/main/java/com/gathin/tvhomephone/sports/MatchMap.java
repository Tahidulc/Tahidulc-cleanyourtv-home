package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The game as it goes, on a pitch across the page: the home side attacking to the right and the away side to the
 * left, each shot where it was struck in its side's colour (filled on target, a ring off it, a small dot blocked),
 * each goal as a ball with its line into the net, each card where its foul was; the newest one pulses while the game
 * is on.
 */
public final class MatchMap extends View {
    private final List<String[]> plays = new ArrayList<>();
    private int homeKit = 0xFF2A4FD0, awayKit = 0xFFF4F5FA;
    private boolean live;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final DashPathEffect dash;
    private LinearGradient grass;
    private long start;

    public MatchMap(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        dash = new DashPathEffect(new float[]{4 * d, 3 * d}, 0);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** The plays (Espn.Summary.plays), both sides' colours (Glyphs.kits, made to show on grass) and whether it is on. */
    public void show(List<String[]> list, int home, int away, boolean isLive) {
        plays.clear();
        plays.addAll(list);
        homeKit = home;
        awayKit = away;
        live = isLive;
        start = System.currentTimeMillis();
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Math.round(w * 0.6f));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        grass = new LinearGradient(0, 0, w, 0, new int[]{0xFF1C6A44, 0xFF175A3A, 0xFF1C6A44}, null, Shader.TileMode.CLAMP);
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), 18 * d, 18 * d, Path.Direction.CW);
    }

    /** Where a play's point is on the screen: x and y in 0..100 from its own side's point of view. */
    private float sx(String side, float x, float in, float w) {
        float f = "home".equals(side) ? x / 100f : 1 - x / 100f;
        return in + f * (w - 2 * in);
    }

    private float sy(String side, float y, float in, float h) {
        float f = "home".equals(side) ? 1 - y / 100f : y / 100f;
        return in + f * (h - 2 * in);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), in = 10 * d;
        p.setStyle(Paint.Style.FILL);
        p.setShader(grass);
        c.save();
        c.clipPath(clip);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x0FFFFFFF);
        for (int i = 0; i < 12; i += 2) c.drawRect(w * i / 12f, 0, w * (i + 1) / 12f, h, p);
        c.restore();
        // The lines.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d);
        p.setColor(0x47FFFFFF);
        float pw = w - 2 * in, ph = h - 2 * in, cx = w / 2f, cy = h / 2f;
        r.set(in, in, w - in, h - in);
        c.drawRoundRect(r, 3 * d, 3 * d, p);
        c.drawLine(cx, in, cx, h - in, p);
        c.drawCircle(cx, cy, ph * 0.15f, p);
        float boxD = pw * 0.157f, boxW = ph * 0.6f, smallD = pw * 0.052f, smallW = ph * 0.27f;
        c.drawRect(in, cy - boxW / 2, in + boxD, cy + boxW / 2, p);
        c.drawRect(w - in - boxD, cy - boxW / 2, w - in, cy + boxW / 2, p);
        c.drawRect(in, cy - smallW / 2, in + smallD, cy + smallW / 2, p);
        c.drawRect(w - in - smallD, cy - smallW / 2, w - in, cy + smallW / 2, p);
        float goalW = ph * 0.11f;
        p.setStrokeWidth(2.4f * d);
        p.setColor(0xB3FFFFFF);
        c.drawLine(in, cy - goalW / 2, in, cy + goalW / 2, p);
        c.drawLine(w - in, cy - goalW / 2, w - in, cy + goalW / 2, p);
        p.setStrokeWidth(1.2f * d);
        p.setColor(0x47FFFFFF);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, 2 * d, p);

        // The plays: misses and blocks first, then on target, cards, and the goals on top.
        String[][] order = {{Espn.BLOCKED, Espn.OFF_TARGET}, {Espn.ON_TARGET}, {Espn.YELLOW, Espn.RED}, {Espn.GOAL}};
        for (String[] kinds : order) {
            for (String[] m : plays) {
                if (!in(m[Espn.M_KIND], kinds)) continue;
                draw(c, m, in, w, h);
            }
        }
        // The newest pulses while the game is on.
        if (live && !plays.isEmpty()) {
            String[] last = plays.get(plays.size() - 1);
            float x = sx(last[Espn.M_SIDE], num(last[Espn.M_X]), in, w), y = sy(last[Espn.M_SIDE], num(last[Espn.M_Y]), in, h);
            float t = ((System.currentTimeMillis() - start) % 1600) / 1600f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(((int) ((1 - t) * 230) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, (7 + 12 * t) * d, p);
            p.setStyle(Paint.Style.FILL);
            if (isAttachedToWindow()) postInvalidateDelayed(40);
        }
    }

    private void draw(Canvas c, String[] m, float in, float w, float h) {
        String side = m[Espn.M_SIDE], kind = m[Espn.M_KIND];
        int kit = "home".equals(side) ? homeKit : awayKit;
        float x = sx(side, num(m[Espn.M_X]), in, w), y = sy(side, num(m[Espn.M_Y]), in, h);
        switch (kind) {
            case Espn.GOAL: {
                String x2 = m[Espn.M_X2], y2 = m[Espn.M_Y2];
                if (!x2.isEmpty() && !y2.isEmpty()) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.6f * d);
                    p.setColor((kit & 0x00FFFFFF) | 0xCC000000);
                    p.setPathEffect(dash);
                    c.drawLine(x, y, sx(side, num(x2), in, w), sy(side, num(y2), in, h), p);
                    p.setPathEffect(null);
                    p.setStyle(Paint.Style.FILL);
                }
                p.setColor((kit & 0x00FFFFFF) | 0x80000000);
                c.drawCircle(x, y, 11 * d, p);
                Glyphs.draw(c, Glyphs.GOAL, x, y, 15 * d);
                break;
            }
            case Espn.ON_TARGET:
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x80000000);
                c.drawCircle(x, y + 0.8f * d, 6 * d, p);
                p.setColor(kit);
                c.drawCircle(x, y, 5.5f * d, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.4f * d);
                p.setColor(0xE6FFFFFF);
                c.drawCircle(x, y, 5.5f * d, p);
                p.setStyle(Paint.Style.FILL);
                break;
            case Espn.OFF_TARGET:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * d);
                p.setColor(kit);
                c.drawCircle(x, y, 5 * d, p);
                p.setStyle(Paint.Style.FILL);
                break;
            case Espn.BLOCKED:
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x66000000);
                c.drawCircle(x, y, 4.2f * d, p);
                p.setColor((kit & 0x00FFFFFF) | 0xCC000000);
                c.drawCircle(x, y, 3.3f * d, p);
                break;
            case Espn.YELLOW:
                Glyphs.draw(c, Glyphs.YELLOW, x, y, 13 * d);
                break;
            case Espn.RED:
                Glyphs.draw(c, Glyphs.RED, x, y, 13 * d);
                break;
            default:
                break;
        }
    }

    private static boolean in(String kind, String[] kinds) {
        for (String k : kinds) if (k.equals(kind)) return true;
        return false;
    }

    private static float num(String v) {
        try {
            return Math.max(0, Math.min(100, Float.parseFloat(v)));
        } catch (NumberFormatException | NullPointerException e) {
            return 50;
        }
    }
}
