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
 * The panel at the right of the screen that Settings and every side menu open in, in the 07 look: over the screen dimmed
 * by the scrim (black at 58 %), a frosted panel floating 36 px in from the top, the right and the foot, rounded 48 (Frost,
 * mat-dark: the page's own blurred picture under a deep tint), from x (1200 as it comes, 720 wide); its title at the top
 * (34 px, bold) and under it its rows (a RowList: the remote's row white and lifted).
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
    static final float X = 1200f, W = 720f, HEAD = 150f, SLIDE = 22f, PAGE_SLIDE = 96f;
    /** The floating panel: its margin from the screen's top, right and foot, its corners; its title's baseline. */
    static final float MARGIN = 36f, RADIUS = 48f, TITLE_Y = 112f;
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
    private final Paint scrim = new Paint();
    private final Frost frost = new Frost(Look07.MAT_DARK);
    private final android.graphics.RectF box = new android.graphics.RectF();

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

    /**
     * The lists laid out over the panel's page only (not the whole screen), so a frame of the rows moving redraws that and
     * not the dimmed screen under it.
     */
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int left = Ui.px(x), right = Math.min(r - l, Ui.px(x + w - MARGIN) + Ui.px(SLIDE)), top = Ui.px(MARGIN) / 2,
                bottom = b - t - Ui.px(MARGIN) / 2;
        for (RowList list : lists) list.layout(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
        laidX = x;
        laidW = w;
    }

    private float laidX = -1f, laidW = -1f;

    private void place() {
        if (x != laidX || w != laidW) requestLayout();
        for (RowList l : lists) {
            // Inside the floating panel: 14 px in from its sides, under its title, above its foot.
            l.x = x + 14f;
            l.w = Math.max(100f, w - MARGIN - 28f);
            l.top = HEAD + 8f;
            l.bottom = 1080f - MARGIN - 16f;
            l.headingY = TITLE_Y;
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
        scrim.setColor(Palette.alpha(Look07.SCRIM, Math.round(((Look07.SCRIM >>> 24) & 0xFF) * open)));
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
        float off = Ui.f(SLIDE) * (1f - open);
        box.set(Ui.f(x) + off, Ui.f(MARGIN), Ui.f(x + w - MARGIN) + off, getHeight() - Ui.f(MARGIN));
        frost.draw(c, box, Ui.f(RADIUS), open);
    }

    /** The pages are kept inside the panel as they slide. */
    @Override protected boolean drawChild(Canvas c, View child, long time) {
        c.save();
        c.clipRect(Ui.f(x) + Ui.f(SLIDE) * (1f - open), Ui.f(MARGIN), Ui.f(x + w - MARGIN) + Ui.f(SLIDE) * (1f - open),
                getHeight() - Ui.f(MARGIN));
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
