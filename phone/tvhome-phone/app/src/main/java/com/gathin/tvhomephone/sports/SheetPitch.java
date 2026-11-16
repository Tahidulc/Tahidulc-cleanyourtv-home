package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * One side's eleven on the TV card's pitch (its blue with the faint streaks and white lines), attacking up: each man
 * where he plays, as SheetArt draws him (his live rating on top, his number, his flag, his goals, assists and cards),
 * his surname under him and his position (and when he went off) under that.
 */
final class SheetPitch extends View {
    private TeamSheet sheet;
    private int kit = 0xFF2A4FD0;
    private String best = "";
    private final SheetArt.Painter painter;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG), role = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path streaks = new Path();
    /** The men with a place on the pitch, gathered each time it is drawn (kept, not made while drawing). */
    private final List<TeamSheet.Man> placed = new ArrayList<>();
    private LinearGradient ground;
    private final float d;
    /** The widest it is drawn (a wide panel centres it). */
    private static final float MOST_DP = 460f;

    SheetPitch(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        painter = new SheetArt.Painter(c);
        float sp = c.getResources().getConfiguration().fontScale * d;
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        name.setTextAlign(Paint.Align.CENTER);
        name.setColor(0xFFFFFFFF);
        name.setTextSize(11.5f * sp);
        name.setShadowLayer(3 * d, 0, d, 0xB0000000);
        role.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        role.setTextAlign(Paint.Align.CENTER);
        role.setColor(0xC8E4E8FF);
        role.setTextSize(9.5f * sp);
        role.setLetterSpacing(0.06f);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.3f * d);
        line.setColor(0x47FFFFFF);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        SheetArt.watch(this);
    }

    /** The side to show, its kit's colour, and the best rating on either side (its star). */
    void show(TeamSheet s, int kit, String best) {
        sheet = s;
        this.kit = kit;
        this.best = best == null ? "" : best;
        setContentDescription(describe(s));
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        float pw = Math.min(w, MOST_DP * d);
        setMeasuredDimension(w, Math.round(pw * 1.24f));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        ground = new LinearGradient(0, 0, 0, h, SheetArt.PITCH_TOP, SheetArt.PITCH_FOOT, Shader.TileMode.CLAMP);
        streaks.reset();
        float slant = h * 0.36f, gap = 0.26f * h * 0.6f, width = 0.017f * h;
        for (float x = -slant; x < w + slant; x += gap + width) {
            streaks.moveTo(x, h);
            streaks.lineTo(x + width, h);
            streaks.lineTo(x + width + slant, 0);
            streaks.lineTo(x + slant, 0);
            streaks.close();
        }
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pw = Math.min(w, MOST_DP * d), left = (w - pw) / 2f, right = left + pw;
        fill.setShader(ground);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        fill.setColor(0x0EFFFFFF);
        c.drawPath(streaks, fill);
        lines(c, left, right, h);
        if (sheet == null) return;
        List<TeamSheet.Man> on = placed;
        on.clear();
        float lo = 1f, hi = 0f;
        for (TeamSheet.Man m : sheet.starters) {
            if (m.x < 0 || m.y < 0) continue;
            on.add(m);
            lo = Math.min(lo, m.y);
            hi = Math.max(hi, m.y);
        }
        int most = 1;
        for (TeamSheet.Man m : on) most = Math.max(most, inLine(on, m).size());
        float pad = 10 * d, cell = (pw - 2 * pad) / most;
        float r = Math.max(15 * d, Math.min(23 * d, cell * 0.3f));
        // The keeper above his goal at the foot, the front line under the top (room for the ratings above them).
        float top = r + 20 * d, foot = h - r - 42 * d;
        for (TeamSheet.Man m : on) {
            float y = hi > lo ? foot - (m.y - lo) / (hi - lo) * (foot - top) : (top + foot) / 2f;
            float x = left + pad + r * 0.6f + m.x * (pw - 2 * pad - r * 1.2f);
            painter.face(c, m, x, y, r, kit, !best.isEmpty() && best.equals(m.rating), false);
            // His surname and, under it, his position (and when he went off), as wide as his room in the line.
            float room = Math.max(44 * d, Math.min(96 * d, nearest(on, m, pw - 2 * pad) - 4 * d));
            c.drawText(Glyphs.fit(m.last, name, room), x, y + r + 14 * d, name);
            String below = m.role + (m.off.isEmpty() ? "" : "  ↓" + m.off);
            c.drawText(Glyphs.fit(below, role, room), x, y + r + 26 * d, role);
        }
    }

    /** The pitch's markings: the touchlines, the halfway line at the top with its circle, the box at the foot. */
    private void lines(Canvas c, float left, float right, float h) {
        float in = 10 * d, pw = right - left, cx = (left + right) / 2f;
        rect.set(left + in, in, right - in, h - in);
        c.drawRect(rect, line);
        // The halfway line along the top, its circle's lower half under it.
        float cr = pw * 0.14f;
        rect.set(cx - cr, in - cr, cx + cr, in + cr);
        c.drawArc(rect, 0, 180, false, line);
        // The penalty area, the six-yard box and the arc at the foot.
        float boxW = (pw - 2 * in) * 0.58f, boxD = h * 0.15f, smallW = (pw - 2 * in) * 0.27f, smallD = h * 0.055f;
        float foot = h - in;
        c.drawRect(cx - boxW / 2f, foot - boxD, cx + boxW / 2f, foot, line);
        c.drawRect(cx - smallW / 2f, foot - smallD, cx + smallW / 2f, foot, line);
        float arc = pw * 0.1f, spot = foot - boxD * 0.7f;
        rect.set(cx - arc, spot - arc, cx + arc, spot + arc);
        c.drawArc(rect, 217, 106, false, line);
    }

    /** The men in the same line as m (FotMob's places a line at nearly one height). */
    private static List<TeamSheet.Man> inLine(List<TeamSheet.Man> on, TeamSheet.Man m) {
        List<TeamSheet.Man> out = new ArrayList<>();
        for (TeamSheet.Man o : on) if (Math.abs(o.y - m.y) < 0.045f) out.add(o);
        return out;
    }

    /** The room m has across: to the nearest man in his line (the pitch's width when alone). */
    private static float nearest(List<TeamSheet.Man> on, TeamSheet.Man m, float width) {
        float best = width;
        for (TeamSheet.Man o : inLine(on, m)) if (o != m) best = Math.min(best, Math.abs(o.x - m.x) * width);
        return best;
    }

    /** The side in words: its shape, then each man with his number, position and rating. */
    static String describe(TeamSheet s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.name.isEmpty() ? "Line-up" : s.name);
        if (!s.formation.isEmpty()) b.append(", ").append(s.formation);
        b.append(':');
        for (TeamSheet.Man m : s.starters) {
            b.append(' ').append(m.name);
            if (!m.number.isEmpty()) b.append(", number ").append(m.number);
            if (!m.role.isEmpty()) b.append(", ").append(m.role);
            if (!m.rating.isEmpty()) b.append(", rated ").append(m.rating);
            if (m.goals > 0) b.append(", ").append(m.goals).append(m.goals == 1 ? " goal" : " goals");
            if (m.assists > 0) b.append(", ").append(m.assists).append(m.assists == 1 ? " assist" : " assists");
            if (m.red > 0) b.append(", sent off");
            else if (m.yellow > 0) b.append(", booked");
            if (!m.off.isEmpty()) b.append(", off at ").append(m.off);
            b.append(';');
        }
        return b.toString();
    }
}
