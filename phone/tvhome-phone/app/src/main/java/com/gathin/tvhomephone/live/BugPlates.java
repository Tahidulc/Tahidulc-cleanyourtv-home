package com.gathin.tvhomephone.live;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * The plates over what grows out of the channel's own scoreboard (BugCover): each in your board's look (its ground, its
 * corners, a line of its accent along the side it meets your board), growing out of your board the way the channel's
 * came (down from under it, out from its side), what the channel's graphic said written again in your board's letters,
 * and folding back into your board when the channel's goes. Under your board in the player, so what slides out
 * of your board is always over it.
 */
final class BugPlates extends View {
    /** Which side of a plate meets your board: it grows out from there. */
    static final int FROM_TOP = 0, FROM_BOTTOM = 1, FROM_LEFT = 2, FROM_RIGHT = 3, FROM_MIDDLE = 4;
    static final long IN_MS = 320L, OUT_MS = 220L;

    private static final class P {
        RectF r;
        int from;
        String words;
        float shown;
        boolean going;
        ValueAnimator anim;
    }

    private final List<P> ps = new ArrayList<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), accent = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private OverlayBug.StripLook look = new OverlayBug.StripLook();
    private float boardH = 40f;
    private Runnable settled;

    BugPlates(Context c) {
        super(c);
        text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        setWillNotDraw(false);
    }

    /** Called when the last one has folded away (its window can shrink). */
    void onSettled(Runnable r) {
        settled = r;
    }

    /** Whether any is showing, coming or going. */
    boolean busy() {
        return !ps.isEmpty();
    }

    /**
     * The plates now: rects in this view's pixels, the words for each (null: none read), your board's box here (for the
     * side each grows from), its look and height. One about where one was keeps going (its words updated); one gone
     * folds away; a new one grows out.
     */
    void set(List<RectF> rects, List<String> words, RectF board, OverlayBug.StripLook look, float boardH) {
        if (look != null) this.look = look;
        this.boardH = Math.max(8f, boardH);
        List<P> keep = new ArrayList<>();
        for (int i = 0; i < rects.size(); i++) {
            RectF r = rects.get(i);
            P was = null;
            for (P p : ps) if (!p.going && !keep.contains(p) && RectF.intersects(p.r, r)) was = p;
            P p = was != null ? was : new P();
            p.r = new RectF(r);
            p.words = words.get(i);
            p.from = from(r, board);
            keep.add(p);
            if (was == null) {
                ps.add(p);
                animate(p, 1f, IN_MS);
            }
        }
        for (P p : new ArrayList<>(ps)) {
            if (!keep.contains(p) && !p.going) {
                p.going = true;
                animate(p, 0f, OUT_MS);
            }
        }
        invalidate();
    }

    /** All gone at once (the board's windows going). */
    void clear() {
        for (P p : ps) if (p.anim != null) p.anim.cancel();
        ps.clear();
        invalidate();
    }

    /** Pure: the side of r that meets your board's box b (r under it: from its top; beside it on the right: from its left). */
    static int from(RectF r, RectF b) {
        if (b == null) return FROM_MIDDLE;
        float slack = Math.max(2f, b.height() * 0.15f);
        if (r.top >= b.bottom - slack) return FROM_TOP;
        if (r.bottom <= b.top + slack) return FROM_BOTTOM;
        if (r.left >= b.right - slack) return FROM_LEFT;
        if (r.right <= b.left + slack) return FROM_RIGHT;
        return FROM_MIDDLE;
    }

    private void animate(P p, float to, long ms) {
        if (p.anim != null) p.anim.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(p.shown, to);
        a.setDuration(Math.max(1L, Math.round(ms * Math.abs(to - p.shown))));
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(v -> {
            p.shown = (float) v.getAnimatedValue();
            if (p.going && p.shown <= 0.001f) {
                ps.remove(p);
                if (ps.isEmpty() && settled != null) settled.run();
            }
            invalidate();
        });
        p.anim = a;
        a.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (P p : ps) if (p.anim != null) p.anim.cancel();
    }

    @Override protected void onDraw(Canvas c) {
        for (P p : ps) {
            if (p.shown <= 0f) continue;
            RectF r = p.r;
            float e = p.shown;
            c.save();
            switch (p.from) {
                case FROM_TOP:
                    c.clipRect(r.left, r.top, r.right, r.top + r.height() * e);
                    break;
                case FROM_BOTTOM:
                    c.clipRect(r.left, r.bottom - r.height() * e, r.right, r.bottom);
                    break;
                case FROM_LEFT:
                    c.clipRect(r.left, r.top, r.left + r.width() * e, r.bottom);
                    break;
                case FROM_RIGHT:
                    c.clipRect(r.right - r.width() * e, r.top, r.right, r.bottom);
                    break;
                default:
                    float cx = r.centerX(), cy = r.centerY();
                    c.scale(0.85f + 0.15f * e, 0.85f + 0.15f * e, cx, cy);
                    break;
            }
            int alpha = p.from == FROM_MIDDLE ? Math.round(255 * e) : 255;
            float round = Math.min(r.height() / 2f, boardH * look.round);
            fill.setColor(look.ground);
            fill.setAlpha(Math.round(((look.ground >>> 24) & 0xFF) * alpha / 255f));
            c.drawRoundRect(r, round, round, fill);
            // A line of the board's accent along the side that meets your board.
            float line = Math.max(2f, boardH * 0.07f);
            int ac = (look.block >>> 24) == 0xFF ? look.block : look.ink;
            accent.setColor(ac);
            accent.setAlpha(Math.round(((ac >>> 24) & 0xFF) * alpha / 255f * ((look.block >>> 24) == 0xFF ? 1f : 0.55f)));
            switch (p.from) {
                case FROM_TOP:
                    c.drawRect(r.left + round, r.top, r.right - round, r.top + line, accent);
                    break;
                case FROM_BOTTOM:
                    c.drawRect(r.left + round, r.bottom - line, r.right - round, r.bottom, accent);
                    break;
                case FROM_LEFT:
                    c.drawRect(r.left, r.top + round, r.left + line, r.bottom - round, accent);
                    break;
                case FROM_RIGHT:
                    c.drawRect(r.right - line, r.top + round, r.right, r.bottom - round, accent);
                    break;
                default:
                    break;
            }
            if (p.words != null) drawWords(c, p, alpha);
            c.restore();
        }
    }

    /** Its words in your board's letters: one line, or two where it is tall enough; made smaller to fit, then cut short. */
    private void drawWords(Canvas c, P p, int alpha) {
        RectF r = p.r;
        String[] lines = p.words.split("\n");
        float pad = Math.max(4f, Math.min(r.height(), boardH) * 0.32f);
        float size = Math.min(boardH * 0.46f, r.height() * 0.56f);
        int n = lines.length > 1 && r.height() >= size * 2.3f ? 2 : 1;
        if (n == 2) size = Math.min(size, r.height() * 0.38f);
        float room = r.width() - 2 * pad;
        if (room < size) return;
        text.setColor(look.ink);
        text.setAlpha(Math.round(((look.ink >>> 24) & 0xFF) * alpha / 255f));
        text.setTextSize(size);
        text.setLetterSpacing(0.04f);
        // Made smaller to fit (down to two thirds), then cut short.
        float widest = 0;
        for (int i = 0; i < n; i++) widest = Math.max(widest, text.measureText(lines[i]));
        if (widest > room) text.setTextSize(Math.max(size * 0.66f, size * room / widest));
        Paint.FontMetrics fm = text.getFontMetrics();
        float lineH = fm.descent - fm.ascent, top = r.centerY() - lineH * n / 2f;
        for (int i = 0; i < n; i++) {
            String s = TextUtils.ellipsize(lines[i], new android.text.TextPaint(text), room, TextUtils.TruncateAt.END).toString();
            c.drawText(s, r.left + pad, top + i * lineH - fm.ascent, text);
        }
    }
}
