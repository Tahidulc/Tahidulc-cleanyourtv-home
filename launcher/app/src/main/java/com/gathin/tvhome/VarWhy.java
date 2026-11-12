package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * VAR explained's WHY: a panel folding down from under the decision beside the board (BoardDrop), in the board's look
 * (its ground, words and quieter words): a small drawing of the reason (a hand on the ball, the offside line, a tackle,
 * the ball over the line, two shirts, else the VAR screen), WHY in VAR's cyan, a sentence or two of the law behind the
 * decision, and the check's length and whether the referee's call stood. In a window of its own just under the alert,
 * for as long as the decision is out; it folds back up before the alert goes in.
 */
final class VarWhy extends View {
    private static final long FOLD_MS = 380L;

    private BoardBug.StripLook look = new BoardBug.StripLook();
    private VarExplained.Why why = new VarExplained.Why();
    private float unit = 48f, shown;
    /** Out to the board's left: its square corner on its right, under the alert's near end. */
    private boolean leftOf;
    private ValueAnimator folding;
    private StaticLayout body;
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG), text = new TextPaint(Paint.ANTI_ALIAS_FLAG),
            small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path(), p = new Path();
    private final RectF box = new RectF();
    private final float[] corners = new float[8];

    VarWhy(Context c) {
        super(c);
        label.setTypeface(ScoreCard.outfit(c, 800));
        label.setLetterSpacing(0.12f);
        text.setTypeface(ScoreCard.outfit(c, 500));
        small.setTypeface(ScoreCard.outfit(c, 500));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    private static int accent(BoardBug.StripLook s) {
        return s.light ? VarExplained.CYAN_ON_LIGHT : VarExplained.CYAN;
    }

    /** Pure: the panel's width for a board unit high beside an alert w wide: the alert's, kept between 10 and 12.5 units. */
    static int widthFor(float unit, int alertW) {
        return Math.round(Math.max(unit * 10f, Math.min(unit * 12.5f, alertW)));
    }

    /** Set for this decision, w wide: returns the height it needs. */
    int set(BoardBug.StripLook s, VarExplained.Why w, float unit, int width, boolean leftOf) {
        look = s;
        why = w == null ? new VarExplained.Why() : w;
        this.unit = Math.max(16f, unit);
        this.leftOf = leftOf;
        float pad = this.unit * 0.32f, iconW = this.unit * 1.25f;
        int textW = Math.max(1, Math.round(width - 2 * pad - iconW - this.unit * 0.3f));
        text.setTextSize(this.unit * 0.36f);
        body = StaticLayout.Builder.obtain(why.text, 0, why.text.length(), text, textW).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.12f).setIncludePad(false).build();
        label.setTextSize(this.unit * 0.27f);
        small.setTextSize(this.unit * 0.28f);
        float h = pad + this.unit * 0.34f + this.unit * 0.12f + body.getHeight() + this.unit * 0.14f + this.unit * 0.36f + pad;
        return Math.round(Math.max(h, iconW + 2 * pad));
    }

    /** Down from under the alert. */
    void open() {
        to(1f, null);
    }

    /** Back up under the alert; then() once it has. */
    void close(Runnable then) {
        if (shown <= 0f) {
            if (then != null) then.run();
            return;
        }
        to(0f, then);
    }

    private void to(float target, Runnable then) {
        if (folding != null) folding.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(shown, target);
        va.setDuration(Math.max(1, Math.round(FOLD_MS * Math.abs(target - shown))));
        va.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (!cancelled && then != null) then.run();
            }
        });
        folding = va;
        va.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (folding != null) folding.cancel();
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || shown <= 0f || body == null) return;
        float f = BoardBug.ease(shown), seen = h * f;
        cv.save();
        cv.clipRect(0, 0, w, seen);
        // Folding down: the whole panel comes down from under the alert, its foot first.
        cv.translate(0, seen - h);
        float rad = look.apart ? unit * 0.42f : Math.max(3f, unit * look.round * 1.4f);
        // Square where it meets the alert (its top, the near end), round elsewhere.
        float tl = leftOf ? rad : 0f, tr = leftOf ? 0f : rad;
        corners[0] = corners[1] = tl;
        corners[2] = corners[3] = tr;
        corners[4] = corners[5] = corners[6] = corners[7] = rad;
        box.set(0, 0, w, h);
        shape.reset();
        shape.addRoundRect(box, corners, Path.Direction.CW);
        fill.setColor(look.ground);
        cv.drawPath(shape, fill);
        float pad = unit * 0.32f, iconW = unit * 1.25f;
        icon(cv, pad, pad, iconW);
        float x = pad + iconW + unit * 0.3f, y = pad;
        label.setColor(accent(look));
        Paint.FontMetrics fm = label.getFontMetrics();
        cv.drawText("WHY", x, y - fm.ascent, label);
        y += unit * 0.34f + unit * 0.12f;
        text.setColor(look.ink);
        cv.save();
        cv.translate(x, y);
        body.draw(cv);
        cv.restore();
        y += body.getHeight() + unit * 0.14f;
        small.setColor(look.dim);
        Paint.FontMetrics sm = small.getFontMetrics();
        cv.drawText(why.foot, x, y - sm.ascent, small);
        cv.restore();
    }

    /** The reason's drawing, size square at (x, y): the player's shape in the words' colour, the point of it in cyan. */
    private void icon(Canvas cv, float x, float y, float size) {
        int ink = look.ink, hot = accent(look);
        float s = size / 64f;
        cv.save();
        cv.translate(x, y);
        cv.scale(s, s);
        fill.setColor(ink);
        line.setColor(ink);
        line.setStrokeWidth(5f);
        switch (why.icon) {
            case VarExplained.ICON_HANDBALL:
                body(cv);
                line.setColor(ink);
                cv.drawLine(22, 28, 17, 44, line);
                line.setColor(hot);
                cv.drawLine(42, 28, 47, 42, line);
                ball(cv, 51, 42, 6f);
                break;
            case VarExplained.ICON_OFFSIDE:
                // Two players, the offside line drawn through the back one, the attacker a step beyond it.
                player(cv, 20, ink);
                player(cv, 44, hot);
                line.setColor(hot);
                line.setStrokeWidth(3f);
                line.setPathEffect(new android.graphics.DashPathEffect(new float[]{5f, 4f}, 0f));
                cv.drawLine(30, 4, 30, 62, line);
                line.setPathEffect(null);
                break;
            case VarExplained.ICON_FOUL:
                // A sliding tackle into a standing player's legs.
                player(cv, 40, ink);
                line.setColor(hot);
                line.setStrokeWidth(6f);
                p.reset();
                p.moveTo(6, 56);
                p.lineTo(24, 50);
                p.lineTo(38, 54);
                cv.drawPath(p, line);
                fill.setColor(hot);
                cv.drawCircle(8, 50, 5f, fill);
                break;
            case VarExplained.ICON_OUT:
                // The ball over the line.
                line.setColor(ink);
                line.setStrokeWidth(4f);
                cv.drawLine(30, 6, 30, 58, line);
                ball(cv, 44, 32, 9f);
                break;
            case VarExplained.ICON_OTHER:
                // Two shirts: the wrong one and the right one.
                shirt(cv, 6, ink);
                shirt(cv, 34, hot);
                break;
            default:
                // The VAR screen.
                line.setColor(ink);
                line.setStrokeWidth(4f);
                box.set(6, 10, 58, 46);
                cv.drawRoundRect(box, 5f, 5f, line);
                cv.drawLine(24, 56, 40, 56, line);
                cv.drawLine(32, 46, 32, 56, line);
                label.setTextSize(13f);
                label.setColor(hot);
                label.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = label.getFontMetrics();
                cv.drawText("VAR", 32, 28 - (fm.ascent + fm.descent) / 2f, label);
                label.setTextAlign(Paint.Align.LEFT);
                label.setTextSize(unit * 0.27f);
                break;
        }
        cv.restore();
    }

    private void body(Canvas cv) {
        cv.drawCircle(32, 14, 8f, fill);
        p.reset();
        p.moveTo(22, 26);
        p.lineTo(42, 26);
        p.lineTo(44, 48);
        p.lineTo(36, 48);
        p.lineTo(34, 62);
        p.lineTo(30, 62);
        p.lineTo(28, 48);
        p.lineTo(20, 48);
        p.close();
        cv.drawPath(p, fill);
    }

    private void player(Canvas cv, float cx, int colour) {
        fill.setColor(colour);
        cv.drawCircle(cx, 14, 6.5f, fill);
        box.set(cx - 7, 23, cx + 7, 44);
        cv.drawRoundRect(box, 4f, 4f, fill);
        line.setColor(colour);
        line.setStrokeWidth(5f);
        cv.drawLine(cx - 3, 44, cx - 5, 60, line);
        cv.drawLine(cx + 3, 44, cx + 5, 60, line);
    }

    private void shirt(Canvas cv, float x, int colour) {
        fill.setColor(colour);
        p.reset();
        p.moveTo(x + 6, 14);
        p.lineTo(x + 18, 14);
        p.lineTo(x + 24, 22);
        p.lineTo(x + 20, 26);
        p.lineTo(x + 20, 50);
        p.lineTo(x + 4, 50);
        p.lineTo(x + 4, 26);
        p.lineTo(x, 22);
        p.close();
        cv.drawPath(p, fill);
    }

    private void ball(Canvas cv, float cx, float cy, float r) {
        fill.setColor(0xFFFFFFFF);
        cv.drawCircle(cx, cy, r, fill);
        line.setColor(look.light ? look.ink : 0xFF151A63);
        line.setStrokeWidth(2f);
        cv.drawCircle(cx, cy, r, line);
    }

    // ---- its own window, just under the alert ------------------------------------------------------------------------

    private static View win;
    private static VarWhy shownWhy;

    /** Up under the alert at (x, y) on the screen, w wide, folding down. Main thread. */
    static void show(Context c, BoardBug.StripLook s, VarExplained.Why w, float unit, int x, int y, int width, boolean leftOf) {
        away(c);
        VarWhy v = new VarWhy(c);
        int h = v.set(s, w, unit, width, leftOf);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        lp.setTitle("VarWhy");
        try {
            c.getSystemService(WindowManager.class).addView(v, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "VAR explained: the WHY could not show: " + e);
            return;
        }
        win = v;
        shownWhy = v;
        v.post(v::open);
    }

    /** Folding back up under the alert, then gone; then() after (at once when none is up). Main thread. */
    static void fold(Context c, Runnable then) {
        VarWhy v = shownWhy;
        if (v == null) {
            if (then != null) then.run();
            return;
        }
        shownWhy = null;
        v.close(() -> {
            remove(c, v);
            if (then != null) then.run();
        });
    }

    /** Gone at once (the board going). Main thread. */
    static void away(Context c) {
        View v = win;
        shownWhy = null;
        if (v != null) remove(c, v);
    }

    /** Whether one is up. */
    static boolean up() {
        return shownWhy != null;
    }

    private static void remove(Context c, View v) {
        if (win == v) win = null;
        try {
            c.getSystemService(WindowManager.class).removeViewImmediate(v);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }
}
