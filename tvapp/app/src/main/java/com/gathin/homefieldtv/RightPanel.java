package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel down the right of the screen that Settings and every side menu open in, over the screen dimmed by the scrim
 * (black at 60 %): 720 px of the panel's colour from x 1200, its title in a 144 px band of the header colour (TITLE size,
 * 48 px in, baseline 87), and under the band its rows (a RowList: the focus pill 16 px in, rows from y 160).
 *
 * <pre>
 *   open(title, tag, rows, focus, animate)  the panel fades and slides in from 22 px to the right (OPEN_MS, 130),
 *                                           the scrim fading up with it; animate false: there at once
 *   push(title, tag, rows, focus)           a sub-page: the page and its title slide out to the left as the new one comes
 *                                           in from the right (PAGE_MS, 140)
 *   pop(rows)                               back a page (BACK_MS, 135), the remote on the row it left, its rows made again
 *                                           from rows (null: as they were); false on the first
 *   close(after)                            fades and slides out (CLOSE_MS, 100), then runs after
 *   set(rows) / list() / pageTag() / depth()  the page on screen: new rows, its list, what it is, how deep
 *   key(code) / ok()                        the remote, for the page on screen
 * </pre>
 *
 * The host hears OK on a row (chosen), the remote moving (focused) and the remote going past a list's edge (edge).
 * Focus jumps between rows; only the panel and its pages move. x and w can be changed for a narrower menu.
 */
final class RightPanel extends FrameLayout implements RowList.Host {
    static final float X = 1200f, W = 720f, HEAD = 144f, SLIDE = 22f, PAGE_SLIDE = 96f;
    static final long OPEN_MS = 130, CLOSE_MS = 100, PAGE_MS = 140, BACK_MS = 135;

    interface Host {
        void chosen(RightPanel panel, RowList.Row row);

        void focused(RightPanel panel, RowList.Row row);

        /** Past the open page's edge (Ui.LEFT, UP, RIGHT, DOWN): true when the host took it. */
        boolean edge(RightPanel panel, int dir);
    }

    private static final class Page {
        final String title;
        final Object tag;
        List<RowList.Row> rows;
        int focus;

        Page(String title, Object tag, List<RowList.Row> rows, int focus) {
            this.title = title;
            this.tag = tag;
            this.rows = new ArrayList<>(rows);
            this.focus = focus;
        }
    }

    /** Where it lies (design pixels). */
    float x = X, w = W;
    private final List<Page> pages = new ArrayList<>();
    private final RowList[] lists = new RowList[2];
    private int current;
    private Host host;
    /** 0 closed, 1 open; and the cross-slide between pages (1 when settled), forward (1) or back (-1). */
    private float open, slide = 1f;
    private int slideDir = 1;
    private ValueAnimator openAnim, pageAnim;
    private boolean closing;
    private final Paint scrim = new Paint(), back = new Paint(), band = new Paint();

    RightPanel(Context c) {
        super(c);
        setWillNotDraw(false);
        for (int i = 0; i < 2; i++) {
            RowList l = new RowList(c);
            l.setHost(this);
            lists[i] = l;
            addView(l, new LayoutParams(-1, -1));
            l.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        }
        place();
    }

    void setHost(Host h) {
        host = h;
    }

    /** The page on screen's list. */
    RowList list() {
        return lists[current];
    }

    /** What the page on screen is (as given to open or push). */
    Object pageTag() {
        return pages.isEmpty() ? null : pages.get(pages.size() - 1).tag;
    }

    int depth() {
        return pages.size();
    }

    boolean isOpen() {
        return open > 0f && !closing;
    }

    private void place() {
        for (RowList l : lists) {
            l.x = x;
            l.w = w;
            l.top = HEAD + Focus.INSET_PANEL;
            l.bottom = 1080f;
            l.headingY = 87f;
        }
    }

    private void fill(RowList l, String title, List<RowList.Row> rows, int focus) {
        place();
        l.heading = title;
        l.setRows(rows);
        if (focus >= 0) l.setFocus(focus);
        l.setActive(true);
    }

    /** The panel with its first page, sliding in (or there at once). */
    void open(String title, Object tag, List<RowList.Row> rows, int focus, boolean animate) {
        pages.clear();
        pages.add(new Page(title, tag, rows, focus));
        current = 0;
        fill(lists[0], title, rows, focus);
        lists[1].setVisibility(View.GONE);
        lists[0].setVisibility(View.VISIBLE);
        closing = false;
        slide = 1f;
        if (!animate || Exec.inline) {
            open = 1f;
            update();
            return;
        }
        open = 0f;
        update();
        animateOpen(1f, OPEN_MS, null);
    }

    /** A sub-page, sliding in from the right as the page on screen slides out to the left. */
    void push(String title, Object tag, List<RowList.Row> rows, int focus) {
        push(title, tag, rows, focus, true);
    }

    /** A sub-page; animate false: there at once (Settings opened again where it was). */
    void push(String title, Object tag, List<RowList.Row> rows, int focus, boolean animate) {
        if (!pages.isEmpty()) pages.get(pages.size() - 1).focus = list().focusIndex();
        pages.add(new Page(title, tag, rows, focus));
        int next = 1 - current;
        fill(lists[next], title, rows, focus);
        current = next;
        crossSlide(1, animate ? PAGE_MS : 0);
    }

    /**
     * Back a page: the one under comes back from the left with the remote on the row it left, its rows made again from
     * rows (null: as they were when it was left). False on the first page.
     */
    boolean pop(List<RowList.Row> rows) {
        if (pages.size() <= 1) return false;
        pages.remove(pages.size() - 1);
        Page p = pages.get(pages.size() - 1);
        if (rows != null) p.rows = new ArrayList<>(rows);
        int next = 1 - current;
        fill(lists[next], p.title, p.rows, p.focus);
        current = next;
        crossSlide(-1, BACK_MS);
        return true;
    }

    /** New rows for the page on screen (the remote on the same line number when it can). */
    void set(List<RowList.Row> rows) {
        if (!pages.isEmpty()) pages.get(pages.size() - 1).rows = new ArrayList<>(rows);
        list().setRows(rows);
    }

    void setFocus(int i) {
        list().setFocus(i);
    }

    /** Fades and slides out, then after runs (the screen goes, or the menu is let go). */
    void close(Runnable after) {
        closing = true;
        if (Exec.inline) {
            open = 0f;
            update();
            if (after != null) after.run();
            return;
        }
        animateOpen(0f, CLOSE_MS, after);
    }

    boolean key(int code) {
        if (closing) return true;
        return list().key(code);
    }

    void ok() {
        if (!closing) list().ok();
    }

    // ---- motion ---------------------------------------------------------------------------------------------------

    private void animateOpen(float to, long ms, Runnable after) {
        if (openAnim != null) openAnim.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(open, to);
        a.setDuration(ms);
        a.setInterpolator(to > open ? Fade.EASE : new AccelerateInterpolator());
        a.addUpdateListener(v -> {
            open = (Float) v.getAnimatedValue();
            update();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!cancelled && after != null) after.run();
            }
        });
        openAnim = a;
        a.start();
    }

    private void crossSlide(int dir, long ms) {
        slideDir = dir;
        lists[current].setVisibility(View.VISIBLE);
        lists[1 - current].setVisibility(View.VISIBLE);
        if (pageAnim != null) pageAnim.cancel();
        if (Exec.inline || ms <= 0) {
            slide = 1f;
            update();
            return;
        }
        slide = 0f;
        update();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            slide = (Float) v.getAnimatedValue();
            update();
        });
        pageAnim = a;
        a.start();
    }

    /** The pages' places and opacities for where the panel and the cross-slide are. */
    private void update() {
        float base = Ui.f(SLIDE) * (1f - open);
        RowList in = lists[current], out = lists[1 - current];
        float d = Ui.f(PAGE_SLIDE);
        in.setTranslationX(base + slideDir * d * (1f - slide));
        in.setAlpha(open * slide);
        if (slide >= 1f) {
            out.setVisibility(View.GONE);
        } else {
            out.setTranslationX(base - slideDir * d * slide);
            out.setAlpha(open * (1f - slide));
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        scrim.setColor(Palette.alpha(Palette.SCRIM, Math.round(0x99 * open)));
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
        float off = Ui.f(SLIDE) * (1f - open), l = Ui.f(x) + off, rr = Ui.f(x + w) + off;
        back.setColor(Palette.alpha(Palette.PANEL, Math.round(255 * open)));
        band.setColor(Palette.alpha(Palette.HEADER, Math.round(255 * open)));
        c.drawRect(l, 0, rr, getHeight(), back);
        c.drawRect(l, 0, rr, Ui.f(HEAD), band);
    }

    /** The pages are kept inside the panel as they slide. */
    @Override protected boolean drawChild(Canvas c, View child, long time) {
        c.save();
        c.clipRect(Ui.f(x) + Ui.f(SLIDE) * (1f - open), 0, getWidth(), getHeight());
        boolean more = super.drawChild(c, child, time);
        c.restore();
        return more;
    }

    // ---- the pages' lists -----------------------------------------------------------------------------------------

    @Override public void chosen(RowList list, RowList.Row row) {
        if (list == list() && host != null && !closing) host.chosen(this, row);
    }

    @Override public void focused(RowList list, RowList.Row row) {
        if (list == list() && host != null) host.focused(this, row);
    }

    @Override public boolean edge(RowList list, int dir) {
        return host == null || host.edge(this, dir);
    }
}
