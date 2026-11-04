package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import java.util.List;

/**
 * Both line-ups on one pitch, as they stand: the home side at the top attacking down, the away side at the bottom
 * attacking up, each player a shirt in his side's colour with his number and his surname under it, and what he has
 * done so far on it (a ball for each goal, his cards, a red arrow when he has gone off).
 */
public final class Pitch extends View {
    private static final class Side {
        final List<String[]> lineup;
        final List<List<Integer>> rows;
        final int kit, ink;

        Side(List<String[]> lineup, String formation, int kit) {
            this.lineup = lineup;
            this.rows = Formation.rows(formation, lineup);
            this.kit = kit;
            this.ink = Glyphs.ink(kit);
        }
    }

    private Side home, away;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint number = new TextPaint(Paint.ANTI_ALIAS_FLAG), name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private LinearGradient grass;
    private final android.graphics.Path clip = new android.graphics.Path();
    private final float d;

    public Pitch(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        number.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        number.setTextAlign(Paint.Align.CENTER);
        number.setTextSize(11.5f * c.getResources().getDisplayMetrics().scaledDensity);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextAlign(Paint.Align.CENTER);
        name.setColor(0xFFFFFFFF);
        name.setTextSize(10.5f * c.getResources().getDisplayMetrics().scaledDensity);
        name.setShadowLayer(3 * d, 0, d, 0xB0000000);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** The two line-ups and their kits (Glyphs.kits). */
    public void show(List<String[]> homeLineup, String homeFormation, int homeKit, List<String[]> awayLineup, String awayFormation,
                     int awayKit) {
        home = new Side(homeLineup, homeFormation, homeKit);
        away = new Side(awayLineup, awayFormation, awayKit);
        invalidate();
    }

    /** Whether there is a starting eleven to show on either side. */
    public static boolean has(List<String[]> a, List<String[]> b) {
        for (String[] p : a) if ("1".equals(p[Espn.P_STARTER])) return true;
        for (String[] p : b) if ("1".equals(p[Espn.P_STARTER])) return true;
        return false;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int pw = Math.min(w, Math.round(460 * d));
        setMeasuredDimension(w, Math.round(pw * 1.5f));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        grass = new LinearGradient(0, 0, 0, h, new int[]{0xFF1C6A44, 0xFF175A3A, 0xFF1C6A44}, null, Shader.TileMode.CLAMP);
        float pw = Math.min(w, 460 * d), left = (w - pw) / 2f;
        clip.reset();
        clip.addRoundRect(new RectF(left, 0, left + pw, h), 20 * d, 20 * d, android.graphics.Path.Direction.CW);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pw = Math.min(w, 460 * d), left = (w - pw) / 2f, right = left + pw;
        drawPitch(c, left, right, h);
        if (home != null) drawSide(c, home, true, left, pw, h);
        if (away != null) drawSide(c, away, false, left, pw, h);
    }

    private void drawPitch(Canvas c, float left, float right, float h) {
        float radius = 20 * d;
        rect.set(left, 0, right, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(grass);
        c.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
        // The mown stripes.
        c.save();
        c.clipPath(clip);
        paint.setColor(0x0FFFFFFF);
        int bands = 12;
        for (int i = 0; i < bands; i += 2) c.drawRect(left, h * i / bands, right, h * (i + 1) / bands, paint);
        c.restore();
        float pw = right - left, in = 12 * d;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f * d);
        paint.setColor(0x47FFFFFF);
        rect.set(left + in, in, right - in, h - in);
        c.drawRoundRect(rect, 4 * d, 4 * d, paint);
        float mid = h / 2f, cx = (left + right) / 2f;
        c.drawLine(left + in, mid, right - in, mid, paint);
        c.drawCircle(cx, mid, pw * 0.13f, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, mid, 2.2f * d, paint);
        paint.setStyle(Paint.Style.STROKE);
        float boxW = (pw - 2 * in) * 0.6f, boxD = h * 0.115f, smallW = (pw - 2 * in) * 0.28f, smallD = h * 0.04f;
        for (int end = 0; end < 2; end++) {
            float edge = end == 0 ? in : h - in, dir = end == 0 ? 1 : -1;
            c.drawRect(cx - boxW / 2, Math.min(edge, edge + dir * boxD), cx + boxW / 2, Math.max(edge, edge + dir * boxD), paint);
            c.drawRect(cx - smallW / 2, Math.min(edge, edge + dir * smallD), cx + smallW / 2, Math.max(edge, edge + dir * smallD), paint);
            float arcR = pw * 0.1f, spot = edge + dir * boxD * 0.72f;
            rect.set(cx - arcR, spot - arcR, cx + arcR, spot + arcR);
            if (end == 0) c.drawArc(rect, 37, 106, false, paint); else c.drawArc(rect, 217, 106, false, paint);
        }
    }

    private void drawSide(Canvas c, Side s, boolean top, float left, float pw, float h) {
        int n = s.rows.size();
        if (n == 0) return;
        float mid = h / 2f;
        float keeperY = top ? 26 * d : h - 38 * d;
        float lastY = top ? mid - 36 * d : mid + 24 * d;
        float step = n > 1 ? (lastY - keeperY) / (n - 1) : 0;
        for (int r = 0; r < n; r++) {
            List<Integer> row = s.rows.get(r);
            float y = keeperY + step * r;
            int k = row.size();
            float gap = pw / (k + 1);
            for (int j = 0; j < k; j++) {
                // The side's left to its right: on the screen, right to left for the side attacking down.
                int slot = top ? k - 1 - j : j;
                float x = left + gap * (slot + 1);
                drawPlayer(c, s, s.lineup.get(row.get(j)), x, y, Math.min(gap - 4 * d, 88 * d));
            }
        }
    }

    private void drawPlayer(Canvas c, Side s, String[] p, float x, float y, float labelWidth) {
        float r = 14 * d;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x59000000);
        c.drawCircle(x, y + 1.5f * d, r + 0.5f * d, paint);
        paint.setColor(s.kit);
        c.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * d);
        paint.setColor(Glyphs.luminance(s.kit) > 0.6 ? 0x66000000 : 0xD9FFFFFF);
        c.drawCircle(x, y, r - 0.8f * d, paint);
        number.setColor(s.ink);
        Paint.FontMetrics fm = number.getFontMetrics();
        c.drawText(p[Espn.P_NUMBER], x, y - (fm.ascent + fm.descent) / 2f, number);
        String label = Glyphs.fit(p[Espn.P_SHORT], name, labelWidth);
        c.drawText(label, x, y + r + 12.5f * d, name);
        int goals = Espn.parseInt(p[Espn.P_GOALS]), own = Espn.parseInt(p[Espn.P_OWN]);
        float bx = x + r * 0.82f, by = y - r * 0.78f;
        for (int g = 0; g < Math.min(goals, 3); g++) {
            Glyphs.draw(c, Glyphs.GOAL, bx + g * 7 * d, by, 12 * d);
        }
        if (own > 0) Glyphs.draw(c, Glyphs.OWN_GOAL, bx + Math.min(goals, 3) * 7 * d, by, 12 * d);
        String cards = p[Espn.P_CARDS];
        if (!cards.isEmpty()) {
            int kind = cards.equals("YR") ? Glyphs.SECOND_YELLOW : cards.contains("R") ? Glyphs.RED : Glyphs.YELLOW;
            Glyphs.draw(c, kind, x - r * 0.9f, y - r * 0.72f, 11 * d);
        }
        if (!p[Espn.P_OFF].isEmpty()) Glyphs.draw(c, Glyphs.OFF, x - r * 0.88f, y + r * 0.74f, 11 * d);
    }
}
