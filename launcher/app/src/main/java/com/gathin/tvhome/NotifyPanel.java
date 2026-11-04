package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Control Centre's Notifications (its tile opens it): a card the Control Centre's size and look. At the top the
 * two main switches, On Home (Home, Ambient mode, pop-up mode) and Over apps; under them every kind of notification
 * (NotifyKinds) with a switch of its own, under its group's heading, two across, the list scrolling with the focus;
 * last, Add, the kinds the theme leaves off, each with a switch that puts it on the list. OK switches, the arrows move,
 * Back goes back from Add and closes the card. What is switched is kept at once (NotifyPrefs).
 */
final class NotifyPanel {
    private final HomeV2 h;
    private final Context c;
    private final Typeface heavy, semi, regular;
    private FrameLayout overlay, card, body;
    private ScrollView scroll;
    private LinearLayout list;
    private boolean open;
    /** 0: the list; 1: Add. */
    private int page;

    /** One switchable row: its view, its switch's parts, whether it is on now, and what OK does. */
    private static final class Row {
        View view;
        View track, knob;
        BooleanSupplier on;
        Runnable ok;
        /** In the scrolling list (not the main switches at the top). */
        boolean inList;
    }

    /** The rows by line (one or two to a line), as the arrows move through them. */
    private final List<List<Row>> lines = new ArrayList<>();
    private int line, col;

    private static final int CARD_X = 200, CARD_Y = 110, CARD_W = 1520, CARD_H = 860, SIDE = 56;
    private static final int ON = 0xFF34C759, OFF = 0x40FFFFFF, ROW = 0x33050838, ROW_LINE = 0x26FFFFFF;

    NotifyPanel(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
    }

    boolean isOpen() {
        return open;
    }

    void show() {
        if (open) return;
        open = true;
        page = 0;
        overlay = new FrameLayout(c);
        overlay.setClickable(true);
        overlay.setBackgroundColor(0xB3050816);
        card = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A2296, 0xFF121980, 0xFF0B0F5A});
        g.setCornerRadius(h.px(44));
        g.setStroke(Math.max(1, h.px(1.5f)), 0x40FFFFFF);
        card.setBackground(g);
        card.setClipToOutline(true);
        card.setOutlineProvider(h.round(44));
        body = new FrameLayout(c);
        // The focused main switch grows a little past its row.
        body.setClipChildren(false);
        card.addView(body, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(h.px(CARD_W), h.px(CARD_H));
        bl.leftMargin = h.px(CARD_X);
        bl.topMargin = h.px(CARD_Y);
        overlay.addView(card, bl);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        build(0);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).setInterpolator(Fluid.EASE).start();
        Fluid.in(card, h.controlButton());
    }

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

    // ---- the pages ---------------------------------------------------------------------------------------------------

    /** Draws a page afresh (0: the list, 1: Add), the focus on its first row (the list back from Add: on Add). */
    private void build(int to) {
        boolean back = page == 1 && to == 0;
        page = to;
        body.removeAllViews();
        lines.clear();
        TextView title = h.text(40, heavy, Color.WHITE);
        title.setText(to == 0 ? "Notifications" : "Add notifications");
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.leftMargin = h.px(SIDE);
        tl.topMargin = h.px(36);
        body.addView(title, tl);
        TextView hint = h.text(17, regular, 0x99FFFFFF);
        Keys.set(hint, to == 0 ? "OK to switch  ·  Back to close" : "OK to add  ·  Back to go back");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        hl.rightMargin = h.px(SIDE);
        hl.topMargin = h.px(50);
        body.addView(hint, hl);

        int top = 108;
        if (to == 0) {
            LinearLayout masters = new LinearLayout(c);
            masters.setClipChildren(false);
            List<Row> mine = new ArrayList<>();
            for (boolean home : new boolean[]{true, false}) {
                Row r = row(home ? "🏠" : "📺", home ? "On Home" : "Over apps",
                        home ? "Home, Ambient mode and pop-up mode" : "Pop-ups over anything you're watching", 26, 18);
                r.on = () -> NotifyPrefs.master(c, home);
                r.ok = () -> NotifyPrefs.setMaster(c, home, !NotifyPrefs.master(c, home));
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -1, 1f);
                if (!home) ml.leftMargin = h.px(20);
                masters.addView(r.view, ml);
                mine.add(r);
            }
            lines.add(mine);
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1, h.px(112));
            mp.leftMargin = h.px(SIDE);
            mp.rightMargin = h.px(SIDE);
            mp.topMargin = h.px(top);
            body.addView(masters, mp);
            top += 112 + 10;
        }

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        // The rows in line with the title and the main switches, with room round them for the focused one's growing.
        list.setPadding(h.px(SIDE), h.px(4), h.px(SIDE), h.px(36));
        list.setClipChildren(false);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
        sp.topMargin = h.px(top);
        body.addView(scroll, sp);

        JSONObject theme = Config.themeJson(c);
        List<NotifyKinds.Kind> extra = new ArrayList<>();
        for (NotifyKinds.Kind k : NotifyKinds.ALL) if (!NotifyPrefs.listed(c, k, theme)) extra.add(k);
        if (to == 0) {
            for (String group : NotifyKinds.GROUPS) {
                List<NotifyKinds.Kind> in = new ArrayList<>();
                for (NotifyKinds.Kind k : NotifyKinds.ALL) if (group.equals(k.group) && NotifyPrefs.listed(c, k, theme)) in.add(k);
                kinds(group, in, false);
            }
            addRow(extra);
        } else if (extra.isEmpty()) {
            TextView none = h.text(22, regular, 0xD9FFFFFF);
            none.setText("Every kind of notification this TV has is on your list already.");
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = h.px(40);
            list.addView(none, nl);
        } else {
            for (String group : NotifyKinds.GROUPS) {
                List<NotifyKinds.Kind> in = new ArrayList<>();
                for (NotifyKinds.Kind k : extra) if (group.equals(k.group)) in.add(k);
                kinds(group, in, true);
            }
        }
        line = back ? lines.size() - 1 : 0;
        col = 0;
        body.setAlpha(0f);
        body.animate().alpha(1f).setDuration(180).setInterpolator(Fluid.EASE).start();
        paint(true);
        // Once laid out: back from Add, the list at its foot, where Add is.
        scroll.post(() -> {
            if (back) scroll.scrollTo(0, list.getHeight());
            Row f = focused();
            if (f != null && f.inList) into(f.view);
        });
    }

    /** A group's heading and its kinds two across; adding (on Add): each switch puts its kind on the list. */
    private void kinds(String group, List<NotifyKinds.Kind> in, boolean adding) {
        if (in.isEmpty()) return;
        TextView head = h.text(15, semi, 0xB3FFFFFF);
        head.setText(group);
        head.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.leftMargin = h.px(4);
        hp.topMargin = h.px(list.getChildCount() == 0 ? 10 : 26);
        hp.bottomMargin = h.px(12);
        list.addView(head, hp);
        LinearLayout across = null;
        List<Row> two = null;
        for (int i = 0; i < in.size(); i++) {
            NotifyKinds.Kind k = in.get(i);
            if (i % 2 == 0) {
                across = new LinearLayout(c);
                across.setClipChildren(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h.px(92));
                if (i > 0) lp.topMargin = h.px(14);
                list.addView(across, lp);
                two = new ArrayList<>();
                lines.add(two);
            }
            Row r = row(k.icon, k.name, k.line, 22, 15);
            r.inList = true;
            if (adding) {
                r.on = () -> NotifyPrefs.added(c, k.id);
                r.ok = () -> NotifyPrefs.add(c, k, !NotifyPrefs.added(c, k.id));
            } else {
                r.on = () -> NotifyPrefs.on(c, k.id);
                r.ok = () -> NotifyPrefs.set(c, k, !NotifyPrefs.on(c, k.id));
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % 2 == 1) rp.leftMargin = h.px(20);
            across.addView(r.view, rp);
            two.add(r);
        }
        if (in.size() % 2 == 1 && across != null) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
            rp.leftMargin = h.px(20);
            across.addView(new View(c), rp);
        }
    }

    /** The last row of the list: Add, naming what it would bring (or that there is nothing left to add). */
    private void addRow(List<NotifyKinds.Kind> extra) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < extra.size() && i < 4; i++) names.append(i == 0 ? "" : ", ").append(extra.get(i).name);
        if (extra.size() > 4) names.append(" and ").append(extra.size() - 4).append(" more");
        Row r = row("+", "Add notifications", extra.isEmpty() ? "Everything this TV can show is on the list" : names.toString(), 22, 15);
        r.inList = true;
        r.ok = () -> build(1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, h.px(92));
        lp.topMargin = h.px(30);
        list.addView(r.view, lp);
        List<Row> one = new ArrayList<>();
        one.add(r);
        lines.add(one);
    }

    /** A row: its mark, its name and line, and (with on set later) a switch at its end. */
    private Row row(String icon, String name, String sub, float nameSize, float subSize) {
        Row r = new Row();
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(h.px(20), 0, h.px(20), 0);
        TextView mark = h.text(nameSize + 6, regular, Color.WHITE);
        mark.setText(icon);
        mark.setGravity(Gravity.CENTER);
        t.addView(mark, new LinearLayout.LayoutParams(h.px(44), -2));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = h.text(nameSize, semi, Color.WHITE);
        n.setText(name);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(n);
        TextView s = h.text(subSize, regular, 0xB3FFFFFF);
        s.setText(sub);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = h.px(4);
        words.addView(s, sl);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = h.px(14);
        wl.rightMargin = h.px(14);
        t.addView(words, wl);
        if (!"+".equals(icon)) {
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
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                if (page == 1) build(0); else hide();
            }
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                Row r = focused();
                if (r != null && r.ok != null) {
                    r.ok.run();
                    if (open && lines.contains(lineOf(r))) paint(false);
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

    private Row focused() {
        if (line >= lines.size()) return null;
        List<Row> l = lines.get(line);
        return l.isEmpty() ? null : l.get(Math.min(col, l.size() - 1));
    }

    private List<Row> lineOf(Row r) {
        for (List<Row> l : lines) if (l.contains(r)) return l;
        return null;
    }

    /**
     * Each row as it is now (the focused one ringed in white and a little larger), its switch on or off: the knob
     * sliding across, or at once (jump) as the page is drawn.
     */
    private void paint(boolean jump) {
        Row f = focused();
        for (List<Row> l : lines) {
            for (Row r : l) {
                boolean on = r == f;
                GradientDrawable bg = h.fill(22, on ? 0x2EFFFFFF : ROW);
                bg.setStroke(on ? h.px(3) : Math.max(1, h.px(1)), on ? Color.WHITE : ROW_LINE);
                r.view.setBackground(bg);
                Fluid.spring(r.view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, on ? 1.03f : 1f, Fluid.STIFF_SMALL, 0.8f);
                Fluid.spring(r.view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, on ? 1.03f : 1f, Fluid.STIFF_SMALL, 0.8f);
                if (r.track != null && r.on != null) {
                    boolean lit = r.on.getAsBoolean();
                    r.track.setBackground(h.fill(19, lit ? ON : OFF));
                    float x = lit ? h.px(28) : 0f;
                    if (jump) r.knob.setTranslationX(x);
                    else if (r.knob.getTranslationX() != x) r.knob.animate().translationX(x).setDuration(160).setInterpolator(Fluid.EASE).start();
                }
            }
        }
        if (f != null && f.inList) into(f.view);
    }

    /** The list scrolled so the focused row is in sight, with room for its group's heading over it. */
    private void into(View v) {
        if (scroll == null || scroll.getHeight() == 0) return;
        int y = 0;
        for (View p = v; p != null && p != list; p = (View) p.getParent()) y += p.getTop();
        int room = h.px(56), at = scroll.getScrollY(), seen = scroll.getHeight();
        if (y - room < at) scroll.smoothScrollTo(0, Math.max(0, y - room));
        else if (y + v.getHeight() + room > at + seen) scroll.smoothScrollTo(0, y + v.getHeight() + room - seen);
    }
}
