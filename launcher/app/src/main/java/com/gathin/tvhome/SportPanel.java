package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The Control Centre's sports cards (Follow players, Your club's season, the weekend planner): a card the Control
 * Centre's size and look, as Notifications' and Match mode's, with its title, its hint, and rows one or two across in
 * a list that scrolls with the focus. A row has its name, a line under it, and a switch or a ›. The arrows move, OK
 * does the row's action, Back goes back a page or closes. A card that draws something of its own (the season's chart,
 * the planner's day) puts it in the list as a view that takes no focus.
 */
abstract class SportPanel {
    protected final HomeV2 h;
    protected final Context c;
    protected final Typeface heavy, semi, regular;
    protected FrameLayout overlay, card, body;
    protected ScrollView scroll;
    protected LinearLayout list;
    protected boolean open;

    /** One row: its view, its lines, its switch (or none), its words now, and what OK does. */
    static final class Row {
        View view;
        TextView name, sub;
        View track, knob;
        BooleanSupplier on;
        Supplier<String> words;
        Runnable ok;
    }

    protected final List<List<Row>> lines = new ArrayList<>();
    protected int line, col;

    private static final int CARD_X = 200, CARD_Y = 80, CARD_W = 1520, CARD_H = 920;
    protected static final int SIDE = 56;
    private static final int ON = 0xFF34C759, OFF = 0x40FFFFFF, ROW = 0x33050838, ROW_LINE = 0x26FFFFFF;

    SportPanel(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
    }

    /** The card's colours, top left to bottom right. */
    protected abstract int[] ground();

    /** Draws the page now showing: title(...), then rows (add) and views (view) into the list. */
    protected abstract void build();

    /** Back on a page that is not the first: true when it went back (the card stays open). */
    protected boolean back() {
        return false;
    }

    boolean isOpen() {
        return open;
    }

    void show() {
        if (open) return;
        open = true;
        overlay = new FrameLayout(c);
        overlay.setClickable(true);
        overlay.setBackgroundColor(0xB3050816);
        card = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, ground());
        g.setCornerRadius(h.px(44));
        g.setStroke(Math.max(1, h.px(1.5f)), 0x40FFFFFF);
        card.setBackground(g);
        card.setClipToOutline(true);
        card.setOutlineProvider(h.round(44));
        body = new FrameLayout(c);
        body.setClipChildren(false);
        card.addView(body, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(h.px(CARD_W), h.px(CARD_H));
        bl.leftMargin = h.px(CARD_X);
        bl.topMargin = h.px(CARD_Y);
        overlay.addView(card, bl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        line = 0;
        col = 0;
        opened();
        redraw();
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).setInterpolator(Fluid.EASE).start();
        Fluid.in(card, h.controlButton());
    }

    /** As it opens, before its first page is drawn: its first page, and what to read (off the main thread). */
    protected void opened() { }

    void hide() {
        if (!open) return;
        open = false;
        View o = overlay;
        View k = card;
        overlay = null;
        card = null;
        Fluid.out(k, h.controlButton(), null);
        o.animate().alpha(0f).setDuration(220).setInterpolator(Fluid.EASE_IN).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    /** The page drawn afresh, the focus where it was when it still can be. */
    protected void redraw() {
        if (!open || body == null) return;
        int keepLine = line, keepCol = col;
        body.removeAllViews();
        lines.clear();
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(h.px(SIDE), h.px(4), h.px(SIDE), h.px(40));
        list.setClipChildren(false);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
        sp.topMargin = h.px(112);
        body.addView(scroll, sp);
        build();
        line = Math.max(0, Math.min(keepLine, lines.size() - 1));
        col = lines.isEmpty() ? 0 : Math.max(0, Math.min(keepCol, lines.get(line).size() - 1));
        paint(true);
        scroll.post(() -> { Row f = focused(); if (f != null) into(f.view); });
    }

    /** The page's title and the hint at the top right. */
    protected void title(String words, String hint) {
        TextView title = h.text(40, heavy, Color.WHITE);
        title.setText(words);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(h.px(1000), -2);
        tl.leftMargin = h.px(SIDE);
        tl.topMargin = h.px(36);
        body.addView(title, tl);
        TextView ht = h.text(17, regular, 0x99FFFFFF);
        Keys.set(ht, hint);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        hl.rightMargin = h.px(SIDE);
        hl.topMargin = h.px(50);
        body.addView(ht, hl);
    }

    /** A heading over the rows that follow. */
    protected void heading(String words) {
        TextView head = h.text(15, semi, 0xB3FFFFFF);
        head.setText(words);
        head.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.leftMargin = h.px(4);
        hp.topMargin = h.px(list.getChildCount() == 0 ? 6 : 26);
        hp.bottomMargin = h.px(10);
        list.addView(head, hp);
    }

    /** A plain line of words in the list (no focus). */
    protected TextView words(String text, float size, int colour) {
        TextView t = h.text(size, regular, colour);
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = h.px(8);
        list.addView(t, lp);
        return t;
    }

    /** Something drawn of the card's own in the list, height tall (no focus). */
    protected void view(View v, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h.px(height));
        lp.topMargin = h.px(6);
        list.addView(v, lp);
    }

    /** A line of one or two rows across. */
    protected void add(Row... rows) {
        LinearLayout across = new LinearLayout(c);
        across.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h.px(92));
        if (!lines.isEmpty()) lp.topMargin = h.px(14);
        list.addView(across, lp);
        List<Row> mine = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) rp.leftMargin = h.px(20);
            across.addView(rows[i].view, rp);
            mine.add(rows[i]);
        }
        if (rows.length == 1) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
            rp.leftMargin = h.px(20);
            across.addView(new View(c), rp);
        }
        lines.add(mine);
    }

    /** A line of up to across rows (a picker's tiles), height tall, the rest of the line left empty so tiles keep one width. */
    protected void addLine(int height, int across, List<Row> rows) {
        LinearLayout line = new LinearLayout(c);
        line.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h.px(height));
        if (!lines.isEmpty()) lp.topMargin = h.px(14);
        list.addView(line, lp);
        List<Row> mine = new ArrayList<>();
        for (int i = 0; i < across; i++) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) rp.leftMargin = h.px(16);
            if (i < rows.size()) {
                line.addView(rows.get(i).view, rp);
                mine.add(rows.get(i));
            } else {
                line.addView(new View(c), rp);
            }
        }
        lines.add(mine);
    }

    /** Rows two across, as many lines as they take. */
    protected void addAll(List<Row> rows) {
        for (int i = 0; i < rows.size(); i += 2) {
            if (i + 1 < rows.size()) add(rows.get(i), rows.get(i + 1));
            else add(rows.get(i));
        }
    }

    /** A row: its name, the line under it, and a switch at its end (switched) or a ›. */
    protected Row row(String name, boolean switched) {
        Row r = new Row();
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(h.px(24), 0, h.px(20), 0);
        LinearLayout ws = new LinearLayout(c);
        ws.setOrientation(LinearLayout.VERTICAL);
        TextView n = h.text(22, semi, Color.WHITE);
        n.setText(name);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ws.addView(n);
        TextView s = h.text(15, regular, 0xB3FFFFFF);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = h.px(4);
        ws.addView(s, sl);
        r.name = n;
        r.sub = s;
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.rightMargin = h.px(14);
        t.addView(ws, wl);
        if (switched) {
            FrameLayout track = new FrameLayout(c);
            View knob = new View(c);
            knob.setBackground(h.fill(15, Color.WHITE));
            FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(h.px(30), h.px(30), Gravity.CENTER_VERTICAL | Gravity.START);
            kl.leftMargin = h.px(4);
            track.addView(knob, kl);
            t.addView(track, new LinearLayout.LayoutParams(h.px(66), h.px(38)));
            r.track = track;
            r.knob = knob;
        } else {
            TextView go = h.text(30, regular, 0xB3FFFFFF);
            go.setText("›");
            t.addView(go);
        }
        r.view = t;
        return r;
    }

    // ---- keys and focus ----------------------------------------------------------------------------------------------

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled() && !back()) hide();
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                Row r = focused();
                if (r != null && r.ok != null) {
                    r.ok.run();
                    if (open) paint(false);
                }
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN || lines.isEmpty()) return true;
        if (code == KeyEvent.KEYCODE_DPAD_UP && line > 0) { line--; col = Math.min(col, lines.get(line).size() - 1); }
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && line < lines.size() - 1) { line++; col = Math.min(col, lines.get(line).size() - 1); }
        else if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) col--;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < lines.get(line).size() - 1) col++;
        else return true;
        paint(false);
        return true;
    }

    protected Row focused() {
        if (line >= lines.size() || line < 0) return null;
        List<Row> l = lines.get(line);
        return l.isEmpty() ? null : l.get(Math.min(col, l.size() - 1));
    }

    /** Each row as it is now: the focused one ringed in white and a little larger, its words and switch as they are. */
    protected void paint(boolean jump) {
        Row f = focused();
        for (List<Row> l : lines) {
            for (Row r : l) {
                boolean on = r == f;
                GradientDrawable bg = h.fill(22, on ? 0x2EFFFFFF : ROW);
                bg.setStroke(on ? h.px(3) : Math.max(1, h.px(1)), on ? Color.WHITE : ROW_LINE);
                r.view.setBackground(bg);
                Fluid.spring(r.view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, on ? 1.02f : 1f, Fluid.STIFF_SMALL, 0.8f);
                Fluid.spring(r.view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, on ? 1.02f : 1f, Fluid.STIFF_SMALL, 0.8f);
                if (r.words != null) r.sub.setText(r.words.get());
                if (r.track != null && r.on != null) {
                    boolean lit = r.on.getAsBoolean();
                    r.track.setBackground(h.fill(19, lit ? ON : OFF));
                    float x = lit ? h.px(28) : 0f;
                    if (jump) r.knob.setTranslationX(x);
                    else if (r.knob.getTranslationX() != x) r.knob.animate().translationX(x).setDuration(160).setInterpolator(Fluid.EASE).start();
                }
            }
        }
        if (f != null) into(f.view);
    }

    /** The list scrolled so the focused row is in sight, with room for a heading over it. */
    protected void into(View v) {
        if (scroll == null || scroll.getHeight() == 0 || v == null) return;
        int y = 0;
        for (View p = v; p != null && p != list; p = (View) p.getParent()) y += p.getTop();
        int room = h.px(60), at = scroll.getScrollY(), seen = scroll.getHeight();
        if (line == 0) { scroll.smoothScrollTo(0, 0); return; }
        if (y - room < at) scroll.smoothScrollTo(0, Math.max(0, y - room));
        else if (y + v.getHeight() + room > at + seen) scroll.smoothScrollTo(0, y + v.getHeight() + room - seen);
    }
}
