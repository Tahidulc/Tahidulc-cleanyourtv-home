package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings' panel in the 07 look (the approved still 28): over the screen it was opened from, dimmed by the scrim, one
 * frosted panel (Frost, mat; 954 x 1008 from (930, 36), rounded 48) in two panes: the sections down its left (Homefield's
 * mark and "Settings", then General, Playlists, EPG, Appearance, Channels &amp; categories, Playback, Remote control,
 * Parental controls, Other and About, each with its icon, the amber dot on About while an update waits), and beside them the page chosen: where it is
 * ("Appearance ›"), its name (34 px), a line about it, and its rows (RowList: the remote's white and lifted). While the
 * remote is on the sections, the page beside them is the section it is on; OK or Right goes into it, Left or Back comes
 * back. A sub-page slides in over its page as RightPanel's do. Appearance › Logos shows the family's own channels' logos
 * under its rows, as the guide will draw them with the Behind logos chosen.
 *
 * <pre>
 *   open(title, tag, sectionRows, focus, animate)   the sections (their rows: tags "page:general"…, names, dots)
 *   push(title, tag, rows, focus[, animate])        a page beside them (a section's, or one under it)
 *   pop(rows)                                        back a page; to the sections from a section's own page
 *   set(rows) / setFocus(i) / list() / depth() / pageTag()   the page on screen (list(): the sections' at the first)
 *   close(after) / key(code) / ok()
 * </pre>
 */
final class SettingsPanel extends FrameLayout implements RowList.Host {
    /** The panel (design px), its sections' column, the page's place. */
    static final float X = 930f, Y = 36f, W = 954f, H = 1008f, RADIUS = 48f, SECTIONS_W = 304f, ROW = 64f, ROWS_Y = 126f,
            PAGE_X = X + SECTIONS_W + 26f, PAGE_W = W - SECTIONS_W - 52f, PAGE_TOP = 176f, SLIDE = 22f, PAGE_SLIDE = 96f;
    static final long OPEN_MS = 130, CLOSE_MS = 100, PAGE_MS = 140, BACK_MS = 135;
    /** Each section's icon, in SettingsScreen.SECTIONS' order. */
    private static final int[] ICONS = {Icons07.COG, Icons07.LIST, Icons07.GRID, Icons07.STAR, Icons07.TV, Icons07.PLAY, Icons07.OK,
            Icons07.LOCK, Icons07.MENU, Icons07.HOME};

    interface Host {
        void chosen(SettingsPanel panel, RowList.Row row);

        void focused(SettingsPanel panel, RowList.Row row);

        /** Past the page's edge (Ui.LEFT, UP, RIGHT, DOWN): true when the host took it. */
        boolean edge(SettingsPanel panel, int dir);

        /** A section's page's rows, to show beside the sections while the remote is on them. */
        List<RowList.Row> preview(String page);

        /** A page's line about it ("" for none). */
        String about(String page);
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

    private final List<Page> pages = new ArrayList<>();
    /** The sections (not drawn as a RowList: their state and keys), and the two page lists that cross-slide. */
    private final RowList sections;
    private final RowList[] lists = new RowList[2];
    private int current;
    private Host host;
    private float open, slide = 1f;
    private int slideDir = 1;
    private ValueAnimator openAnim, pageAnim;
    private boolean closing;
    /** The section previewed beside the sections (its tag), while the remote is on them. */
    private String previewOf = "";
    private String title = "", crumb = "", about = "";

    private final Paint scrim = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG),
            amber = new Paint(Paint.ANTI_ALIAS_FLAG), amberInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Frost frost = new Frost(Look07.MAT);
    private final TextPaint brandP = Type.paint(Type.H3, Type.BOLD, Look07.L1), sectionP = Type.paint(Type.MENU, Type.MEDIUM, Look07.L2),
            sectionOnP = Type.paint(Type.MENU, Type.SEMIBOLD, Look07.L1), crumbP = Type.paint(19f, Type.SEMIBOLD, Look07.L3),
            titleP = Type.paint(34f, Type.BOLD, Look07.L1), aboutP = Type.paint(19f, Type.REGULAR, Look07.L3),
            labelP = Type.label(Type.LABEL, Look07.AMBER), numP = Type.paint(14f, Type.SEMIBOLD, Look07.L3);
    private final TextFit titles = new TextFit(titleP, 32), abouts = new TextFit(aboutP, 32), crumbs = new TextFit(crumbP, 32);
    private final Lift07 lift = new Lift07();
    private final RectF box = new RectF(), r = new RectF();
    private final Runnable redraw = this::invalidate;

    SettingsPanel(Context c) {
        super(c);
        setWillNotDraw(false);
        sections = new RowList(c);
        sections.setHost(this);
        sections.setVisibility(View.INVISIBLE);
        addView(sections, new LayoutParams(-1, -1));
        for (int i = 0; i < 2; i++) {
            RowList l = new RowList(c);
            l.setHost(this);
            lists[i] = l;
            addView(l, new LayoutParams(-1, -1));
            l.setVisibility(View.GONE);
        }
        dot.setColor(Look07.AMBER);
        amber.setColor(Look07.AMBER);
        amberInk.setColor(Look07.AMBER_INK);
        Shadows.prepare(Look07.SH_ROW, Look07.R_ROW);
        place();
    }

    void setHost(Host h) {
        host = h;
    }

    /** The page on screen's list (the sections' while the remote is on them). */
    RowList list() {
        return pages.size() <= 1 ? sections : lists[current];
    }

    Object pageTag() {
        return pages.isEmpty() ? null : pages.get(pages.size() - 1).tag;
    }

    int depth() {
        return pages.size();
    }

    boolean isOpen() {
        return open > 0f && !closing;
    }

    /** The page lists in the right-hand pane (Appearance › Logos leaves room under its rows for its logos). */
    private void place() {
        boolean logos = "appearance.logos".equals(shownTag());
        for (RowList l : lists) {
            l.x = PAGE_X;
            l.w = PAGE_W;
            l.top = PAGE_TOP;
            l.bottom = logos ? LOGOS_Y - 10f : Y + H - 18f;
            l.heading = null;
        }
        sections.x = X;
        sections.w = SECTIONS_W;
        sections.top = ROWS_Y;
        sections.bottom = Y + H;
    }

    /** The page shown beside the sections: the one open, or the section the remote is on. */
    private String shownTag() {
        if (pages.size() > 1) {
            Object t = pages.get(pages.size() - 1).tag;
            return t instanceof String ? (String) t : "";
        }
        return previewOf;
    }

    private void fill(RowList l, List<RowList.Row> rows, int focus, boolean active) {
        place();
        l.setRows(rows);
        if (focus >= 0) l.setFocus(focus);
        l.setActive(active);
    }

    /** The panel with its sections, sliding in (or there at once); the remote on section focus. */
    void open(String heading, Object tag, List<RowList.Row> sectionRows, int focus, boolean animate) {
        pages.clear();
        pages.add(new Page(heading, tag, sectionRows, focus));
        sections.setRows(sectionRows);
        sections.setFocus(Math.max(0, focus));
        sections.setActive(true);
        current = 0;
        closing = false;
        slide = 1f;
        previewSection();
        if (!animate || Exec.inline) {
            open = 1f;
            update();
            return;
        }
        open = 0f;
        update();
        animateOpen(1f, OPEN_MS, null);
    }

    /** The section the remote is on, shown beside the sections (its rows quiet). */
    private void previewSection() {
        RowList.Row row = sections.focused();
        String page = row != null && row.tag instanceof String && ((String) row.tag).startsWith("page:") ? ((String) row.tag).substring(5) : "";
        if (page.equals(previewOf) && lists[current].getVisibility() == View.VISIBLE) return;
        previewOf = page;
        title = row == null ? "" : row.title;
        crumb = "";
        about = host == null || page.isEmpty() ? "" : host.about(page);
        List<RowList.Row> rows = host == null || page.isEmpty() ? new ArrayList<>() : host.preview(page);
        fill(lists[current], rows, -1, false);
        lists[current].setVisibility(View.VISIBLE);
        lists[1 - current].setVisibility(View.GONE);
        invalidate();
    }

    void push(String heading, Object tag, List<RowList.Row> rows, int focus) {
        push(heading, tag, rows, focus, true);
    }

    /** A page beside the sections: a section's own (it takes the remote from them) or one under it (sliding in). */
    void push(String heading, Object tag, List<RowList.Row> rows, int focus, boolean animate) {
        if (!pages.isEmpty()) pages.get(pages.size() - 1).focus = list().focusIndex();
        boolean first = pages.size() == 1;
        pages.add(new Page(heading, tag, rows, focus));
        sections.setActive(false);
        headerFor(pages.size() - 1);
        if (first) {
            // The section's page: the one beside the sections, now with the remote.
            fill(lists[current], rows, focus, true);
            lists[current].setVisibility(View.VISIBLE);
            update();
            return;
        }
        int next = 1 - current;
        fill(lists[next], rows, focus, true);
        current = next;
        crossSlide(1, animate ? PAGE_MS : 0);
    }

    /** The words over page k: where it is (its parents' names), its name, its line. */
    private void headerFor(int k) {
        Page p = pages.get(k);
        title = p.title;
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < k; i++) {
            if (b.length() > 0) b.append("  ›  ");
            b.append(pages.get(i).title);
        }
        crumb = b.toString();
        about = host != null && p.tag instanceof String ? host.about((String) p.tag) : "";
    }

    /** Back a page: to the sections from a section's own page. False at the sections. */
    boolean pop(List<RowList.Row> rows) {
        if (pages.size() <= 1) return false;
        pages.remove(pages.size() - 1);
        Page p = pages.get(pages.size() - 1);
        if (rows != null) p.rows = new ArrayList<>(rows);
        if (pages.size() == 1) {
            // The sections again, the remote on the one it came from.
            sections.setRows(p.rows);
            sections.setActive(true);
            previewOf = "";
            previewSection();
            update();
            return true;
        }
        headerFor(pages.size() - 1);
        int next = 1 - current;
        fill(lists[next], p.rows, p.focus, true);
        current = next;
        crossSlide(-1, BACK_MS);
        return true;
    }

    void set(List<RowList.Row> rows) {
        if (!pages.isEmpty()) pages.get(pages.size() - 1).rows = new ArrayList<>(rows);
        list().setRows(rows);
        if (pages.size() <= 1) {
            previewOf = "";
            previewSection();
        }
    }

    void setFocus(int i) {
        list().setFocus(i);
        if (pages.size() <= 1) previewSection();
    }

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
        if (pages.size() <= 1 && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            sections.ok();
            return true;
        }
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

    // ---- drawing --------------------------------------------------------------------------------------------------

    /** The logos' preview on Appearance › Logos (design px): its label's baseline, its tiles' top. */
    static final float LOGOS_Y = 640f;

    @Override protected void onDraw(Canvas c) {
        scrim.setColor(Palette.alpha(0x8C000000, Math.round(0x8C * open)));
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
        float off = Ui.f(SLIDE) * (1f - open);
        box.set(Ui.f(X) + off, Ui.f(Y), Ui.f(X + W) + off, Ui.f(Y + H));
        frost.draw(c, box, Ui.f(RADIUS), open);
        if (open <= 0f) return;
        int a = Math.round(255 * open);
        c.saveLayerAlpha(box, a);
        c.translate(off, 0f);
        boolean moving = drawSections(c);
        // The line between the panes.
        fill.setColor(0x1AFFFFFF);
        c.drawRect(Ui.f(X + SECTIONS_W), Ui.f(Y), Ui.f(X + SECTIONS_W) + Math.max(1f, Ui.f(1f)), Ui.f(Y + H), fill);
        drawHeader(c);
        if ("appearance.logos".equals(shownTag())) drawLogos(c);
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    /** Homefield's mark and "Settings", then the sections: the remote's white, the one open white at 16 %. */
    private boolean drawSections(Canvas c) {
        float x0 = X + 14f;
        Dark.mark(c, Ui.f(x0 + 16f), Ui.f(Y + 34f), Ui.f(34f), amber, amberInk);
        c.drawText("Settings", Ui.f(x0 + 16f + 34f + 12f), Ui.f(Y + 34f + 29f), brandP);
        List<RowList.Row> rows = sections.rows();
        int focus = sections.focusIndex();
        boolean onSections = pages.size() <= 1 && sections.active();
        RowList.Row lit = onSections && focus >= 0 && focus < rows.size() ? rows.get(focus) : null;
        boolean moving = lift.step(lit);
        for (int i = 0; i < rows.size(); i++) {
            RowList.Row row = rows.get(i);
            float top = ROWS_Y + i * ROW;
            r.set(Ui.f(x0), Ui.f(top), Ui.f(X + SECTIONS_W - 14f), Ui.f(top + ROW));
            boolean here = i == focus;
            float rad = Ui.f(Look07.R_ROW);
            boolean white = here && onSections;
            c.save();
            if (white) {
                float k = Math.max(0f, lift.of(row));
                c.scale(1f + 0.025f * k, 1f + 0.025f * k, r.centerX(), r.centerY());
                Shadows.draw(c, r, rad, Look07.SH_ROW, Math.min(1f, k));
                fill.setColor(Look07.FOCUS_FILL);
                c.drawRoundRect(r, rad, rad, fill);
            } else if (here) {
                fill.setColor(Look07.SELECTED);
                c.drawRoundRect(r, rad, rad, fill);
            }
            int icon = i < ICONS.length ? ICONS[i] : Icons07.COG;
            Icons07.draw(c, icon, r.left + Ui.f(16f + 11f), r.centerY(), Ui.f(22f), white ? Look07.INK : here ? Look07.AMBER : Look07.L3);
            TextPaint p = here ? sectionOnP : sectionP;
            p.setColor(white ? Look07.INK : here ? Look07.L1 : Look07.L2);
            c.drawText(row.title, r.left + Ui.f(52f), Type.centred(p, r.centerY()), p);
            if (row.dot) c.drawCircle(r.right - Ui.f(16f + 5f), r.centerY(), Ui.f(5f), dot);
            c.restore();
        }
        return moving;
    }

    /** Over the page: where it is ("Appearance ›"), its name, its line. */
    private void drawHeader(Canvas c) {
        float x = Ui.f(PAGE_X + 12f), room = Ui.f(PAGE_W - 24f);
        float slideX = lists[current].getTranslationX() - Ui.f(SLIDE) * (1f - open);
        c.save();
        c.translate(slideX, 0f);
        if (!crumb.isEmpty()) {
            float w = crumbs.line(c, crumb, x, Ui.f(84f), room - Ui.f(30f));
            Icons07.draw(c, Icons07.RIGHT, x + w + Ui.f(14f), Ui.f(78f), Ui.f(16f), Look07.L3);
        }
        titles.line(c, title, x, Ui.f(126f), room);
        if (!about.isEmpty()) abouts.line(c, about, x, Ui.f(157f), room);
        c.restore();
    }

    /** Appearance › Logos: the family's own channels' logos as the guide will draw them (Behind logos as chosen). */
    private void drawLogos(Canvas c) {
        float x0 = PAGE_X + 12f, y = LOGOS_Y + 30f;
        c.drawText("PREVIEW · AS THE GUIDE WILL DRAW THEM", Ui.f(x0), Ui.f(y), labelP);
        List<Channel> all = Library.get(getContext()).all();
        int n = Math.min(12, all.size());
        float gap = 12f, tw = (PAGE_W - 24f - 3 * gap) / 4f, th = 84f;
        for (int i = 0; i < n; i++) {
            Channel ch = all.get(i);
            float tx = x0 + (i % 4) * (tw + gap), ty = y + 22f + (i / 4) * (th + 34f);
            r.set(Ui.f(tx), Ui.f(ty), Ui.f(tx + tw), Ui.f(ty + th));
            Logos.draw(c, ch, r, redraw);
            int num = Library.get(getContext()).number(ch);
            if (num > 0) {
                String s = Words.number(num);
                c.drawText(s, r.centerX() - numP.measureText(s) / 2f, Ui.f(ty + th + 22f), numP);
            }
        }
    }

    /** The pages are kept inside the panel as they slide. */
    @Override protected boolean drawChild(Canvas c, View child, long time) {
        if (child == sections) return false;
        float off = Ui.f(SLIDE) * (1f - open);
        c.save();
        c.clipRect(Ui.f(X + SECTIONS_W) + off, Ui.f(Y), Ui.f(X + W) + off, Ui.f(Y + H));
        boolean more = super.drawChild(c, child, time);
        c.restore();
        return more;
    }

    // ---- the lists ------------------------------------------------------------------------------------------------

    @Override public void chosen(RowList list, RowList.Row row) {
        if (list == list() && host != null && !closing) host.chosen(this, row);
    }

    @Override public void focused(RowList list, RowList.Row row) {
        if (list == sections && pages.size() <= 1) previewSection();
        if (list == list() && host != null) host.focused(this, row);
    }

    @Override public boolean edge(RowList list, int dir) {
        return host == null || host.edge(this, dir);
    }
}
