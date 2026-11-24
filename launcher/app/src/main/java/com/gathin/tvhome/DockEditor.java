package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Edit dock (launcher v2's dock layout), with the remote: hold OK on a dock item, or the Control Centre's Edit dock.
 * Home dims under a shade and the dock stays bright above it, every item in its own colours. Left and Right go along
 * the dock; OK picks the item up (it lifts and rocks, its name between arrows above it) and Left and Right then move it
 * along its own part of the dock (the apps, All apps among them, or the sections), OK putting it down. Up goes to the
 * buttons over the dock: Remove (any app but All apps, any section but TV Guide and Apps, which navigation needs), Add
 * app (the TV's apps not on the dock, with their banners or icons, and the sections taken off; the one chosen goes in
 * after the focused app, picked up ready to move), Reset to default (OK twice) and Done. Back, Done or Home saves
 * (DockOrder, in the launcher's prefs) and the dock goes on as edited, the focus on the item it was on.
 *
 * <p>The dock changes live as it is edited: the items are Home's own views (HomeV2.railViews, the sections by
 * HomeV2.secOrder), moved and laid out again by HomeV2.placeDock, the liquid glass following the focus.
 */
final class DockEditor {
    private static final int BUTTON_Y = 772, BUTTON_H = 66, NAME_Y = 870, SHADE = 0xFF060818;
    private static final String[] BUTTONS = {"Remove", "Add app", "Reset to default", "Done"};
    private static final int[] BUTTON_ICONS = {R.drawable.ic_dock_remove, R.drawable.ic_dock_add, R.drawable.ic_dock_reset, R.drawable.ic_dock_done};
    private static final int REMOVE = 0, ADD = 1, RESET = 2, DONE = 3;

    private final HomeV2 h;
    private final Context c;
    private final Typeface heavy;
    /** The order saved when editing began: the apps not installed now keep their places from it. */
    private final DockOrder before;
    /** What the dock shows as it is edited, in step with h.railViews (ALL for All apps' view) and h.secOrder. */
    private DockOrder work;
    /** The dock item with the focus: the apps first, then the sections. */
    private int at;
    private boolean holding;
    /** The button with the focus, or -1 on the dock. */
    private int bar = -1;
    /** The OK that opened Edit dock is still held: its repeats and release are not presses. */
    private boolean waitUp;
    private boolean resetArmed;
    /** The app whose tiles show on Home (its view), found again afterwards. */
    private final View selected;
    private final Map<String, String> labels = new HashMap<>();
    /** Apps taken off the dock, fading away before they leave its layer. */
    private final List<View> leaving = new ArrayList<>();

    private FrameLayout layer;
    private TextView hint;
    /** A word from the last edit ("… is off the dock") in the hint's place for a few seconds. */
    private boolean saying;
    private final List<LinearLayout> buttons = new ArrayList<>();
    private final List<TextView> buttonWords = new ArrayList<>();
    private FrameLayout nameBox;
    private TextView name;
    private View rocking;
    private android.animation.ObjectAnimator rock;
    private CharSequence hintChoose, hintHolding, hintBar;
    private Picker picker;
    private boolean closed;

    DockEditor(HomeV2 home, int at, boolean held) {
        h = home;
        c = home.a;
        heavy = Fonts.get(c, R.font.outfit_extrabold);
        before = home.dockOrder.copy();
        List<String> apps = new ArrayList<>();
        for (View v : home.railViews) apps.add(v.getTag() == null ? DockOrder.ALL : (String) v.getTag());
        List<Integer> secs = new ArrayList<>();
        for (int s : home.secOrder) secs.add(s);
        work = new DockOrder(apps, secs);
        this.at = Math.max(0, Math.min(at, total() - 1));
        holding = held;
        waitUp = held;
        selected = home.dockSelectedView();
    }

    private int px(float v) {
        return h.px(v);
    }

    private int apps() {
        return work.apps.size();
    }

    private int total() {
        return work.apps.size() + work.sections.size();
    }

    private boolean onApp() {
        return at < apps();
    }

    /** The view of dock item i (apps, then sections). */
    private View item(int i) {
        if (i < apps()) return h.railViews.get(i);
        return h.catViews.get(work.sections.get(i - apps()));
    }

    /** What item i is called on screen. */
    private String nameOf(int i) {
        if (i >= apps()) return HomeV2.CAT_NAMES[work.sections.get(i - apps())];
        String p = work.apps.get(i);
        if (DockOrder.ALL.equals(p)) return "All apps";
        String l = labels.get(p);
        return l != null ? l : p;
    }

    // ---- open and close ---------------------------------------------------------------------------------------------

    void open() {
        FrameLayout dock = h.dockLayer();
        layer = new FrameLayout(c);
        layer.setClickable(true);
        layer.setOutlineProvider(null);
        layer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000 | (SHADE & 0xFFFFFF), 0xC7000000 | (SHADE & 0xFFFFFF), 0xEB000000 | (SHADE & 0xFFFFFF)}));
        // Over everything on Home but the dock, which is lifted over it while editing.
        layer.setTranslationZ(px(1));
        h.root.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dock.setTranslationZ(px(2));
        dock.animate().cancel();
        dock.setVisibility(View.VISIBLE);
        dock.setAlpha(1f);
        dock.setTranslationY(0f);

        TextView title = h.text(44, heavy, Color.WHITE);
        title.setText("Edit dock");
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        place(title, 0, 612, 1920, 60);
        hint = h.text(22, h.regular, 0xD9FFFFFF);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        hint.setMaxLines(1);   // not setSingleLine: scrolling sideways, a centred line is drawn off its box
        place(hint, 0, 682, 1920, 40);
        hintChoose = Keys.icons(c, "Left and Right choose  ·  OK picks it up to move it  ·  Up for Remove and Add app  ·  Back saves", px(22), true);
        hintHolding = Keys.icons(c, "Left and Right move it  ·  OK puts it down  ·  Back saves", px(22), true);
        hintBar = Keys.icons(c, "Left and Right choose  ·  OK does it  ·  Down goes back to the dock  ·  Back saves", px(22), true);

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        for (int i = 0; i < BUTTONS.length; i++) {
            LinearLayout b = new LinearLayout(c);
            b.setGravity(Gravity.CENTER);
            b.setPadding(px(30), 0, px(34), 0);
            ImageView ic = new ImageView(c);
            ic.setImageResource(BUTTON_ICONS[i]);
            b.addView(ic, new LinearLayout.LayoutParams(px(28), px(28)));
            TextView t = h.text(24, h.semi, Color.WHITE);
            t.setText(BUTTONS[i]);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setMaxWidth(px(420));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.leftMargin = px(12);
            b.addView(t, tl);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(BUTTON_H));
            if (i > 0) bl.leftMargin = px(18);
            row.addView(b, bl);
            buttons.add(b);
            buttonWords.add(t);
        }
        place(row, 0, BUTTON_Y, 1920, BUTTON_H);

        // The focused item's name over it, between arrows while it is picked up.
        nameBox = new FrameLayout(c);
        name = h.text(20, h.semi, Color.WHITE);
        name.setSingleLine(true);
        name.setPadding(px(18), 0, px(18), 0);
        name.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable ng = h.fill(20, 0x33FFFFFF);
        ng.setStroke(Math.max(1, px(1.5f)), 0x4DFFFFFF);
        name.setBackground(ng);
        nameBox.addView(name, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40), Gravity.CENTER_HORIZONTAL));
        place(nameBox, 0, NAME_Y, 600, 40);

        // Every item at rest in its own colours (the dock's swell and slide are Home's; here the edit's look).
        for (View v : h.railViews) v.animate().translationX(0f).rotation(0f).setDuration(200).start();
        for (View v : h.catViews) v.animate().translationX(0f).rotation(0f).setDuration(200).start();
        h.dockMark(false);
        layer.setAlpha(0f);
        layer.animate().alpha(1f).setDuration(220).start();
        readLabels();
        look();
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(hgt));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        layer.addView(v, lp);
    }

    /** The dock's apps' names (Android's labels), read off the main thread. */
    private void readLabels() {
        List<String> pkgs = new ArrayList<>(work.packages());
        h.io.execute(() -> {
            PackageManager pm = c.getPackageManager();
            Map<String, String> got = new HashMap<>();
            for (String p : pkgs) {
                try {
                    got.put(p, pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString());
                } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                    // its package name
                }
            }
            h.main.post(() -> {
                for (Map.Entry<String, String> e : got.entrySet()) labels.putIfAbsent(e.getKey(), e.getValue());
                if (!closed) look();
            });
        });
    }

    /** Saved and closed: Back, Done, Home, or Home going away. */
    void finish() {
        if (closed) return;
        closed = true;
        if (picker != null) picker.close();
        h.main.removeCallbacks(fadeSaid);
        stopRock();
        FrameLayout dock = h.dockLayer();
        for (View v : leaving) if (v.getParent() == dock) dock.removeView(v);
        leaving.clear();
        for (View v : h.railViews) settle(v);
        for (View v : h.catViews) settle(v);
        DockOrder kept = DockOrder.keepMissing(work, before, h::installed);
        DockOrder.save(c, kept, h.dockDefault);
        h.placeDock(false);
        View l = layer;
        l.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            h.root.removeView(l);
            // Back under Home's panels, unless Edit dock was opened again meanwhile.
            if (h.dockLayer() != null && !h.editingDock()) h.dockLayer().setTranslationZ(0f);
        }).start();
        h.dockSelected(selected);
        h.dockDone(kept, Math.max(0, Math.min(at, total() - 1)));
    }

    /** An item back as Home paints it from (Home's paint then gives it its swell and slide). */
    private static void settle(View v) {
        v.animate().cancel();
        v.setRotation(0f);
        v.setAlpha(1f);
        v.setTranslationX(0f);
    }

    // ---- keys -------------------------------------------------------------------------------------------------------

    boolean key(KeyEvent e) {
        if (closed) return false;
        if (picker != null) return picker.key(e);
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok && waitUp) {
            if (e.getAction() == KeyEvent.ACTION_UP) waitUp = false;
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) press();
            return true;
        }
        boolean nav = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!nav) return code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SEARCH;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        if (bar >= 0) {
            if (dir != 0) bar = Math.max(0, Math.min(BUTTONS.length - 1, bar + dir));
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) bar = -1;
            if (bar != RESET) resetArmed = false;
            look();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            holding = false;
            bar = REMOVE;
            look();
            return true;
        }
        if (dir == 0) return true;
        resetArmed = false;
        if (holding) move(dir);
        else at = Math.max(0, Math.min(total() - 1, at + dir));
        look();
        return true;
    }

    private void press() {
        if (bar < 0) {
            holding = !holding;
            look();
            return;
        }
        switch (bar) {
            case REMOVE: remove(); break;
            case ADD: add(); break;
            case RESET: reset(); break;
            default: finish(); return;
        }
        look();
    }

    // ---- the edits --------------------------------------------------------------------------------------------------

    /** The picked-up item one place along its own part of the dock. */
    private void move(int dir) {
        if (onApp()) {
            int to = work.moveApp(at, dir);
            if (to == at) return;
            Collections.swap(h.railViews, at, to);
            at = to;
        } else {
            int s = at - apps(), to = work.moveSection(s, dir);
            if (to == s) return;
            h.secOrder = work.sectionArray();
            at = apps() + to;
        }
        h.placeDock(true);
    }

    private void remove() {
        resetArmed = false;
        String why = removeWhy(at);
        if (why != null) { say(why); return; }
        String was = nameOf(at);
        if (onApp()) {
            View v = h.railViews.remove(at);
            work.removeApp(at);
            leaving.add(v);
            v.animate().cancel();
            v.animate().scaleX(0.3f).scaleY(0.3f).alpha(0f).translationY(px(20)).setDuration(170).withEndAction(() -> {
                leaving.remove(v);
                if (v.getParent() == h.dockLayer()) h.dockLayer().removeView(v);
                if (!closed) { h.placeDock(true); look(); }
            }).start();
            at = Math.min(at, apps() - 1);
        } else {
            int s = at - apps();
            View v = item(at);
            work.removeSection(s);
            h.secOrder = work.sectionArray();
            v.animate().cancel();
            v.animate().scaleX(0.3f).scaleY(0.3f).alpha(0f).setDuration(170).withEndAction(() -> {
                if (!closed) { h.placeDock(true); look(); }
            }).start();
            at = apps() + Math.min(s, work.sections.size() - 1);
        }
        say(was + " is off the dock  ·  Add app puts it back");
    }

    /** Why item i cannot be taken off the dock, or null when it can. */
    private String removeWhy(int i) {
        if (i < apps()) return work.appRemovable(i) ? null : "All apps stays: it's the way to every app on the TV";
        int s = work.sections.get(i - apps());
        if (DockOrder.sectionRemovable(s)) return null;
        return s == DockOrder.GUIDE ? "TV Guide stays: it's the way into the channels" : "Apps stays: it's the way to every app on the TV";
    }

    private boolean canAdd() {
        return work.roomForApp() || !work.offSections().isEmpty();
    }

    private void add() {
        resetArmed = false;
        if (!canAdd()) { say("The dock is full: remove an app first"); return; }
        picker = new Picker();
        picker.open();
    }

    /** An app chosen in Add app: on the dock after the focused app (else before All apps), picked up to be moved. */
    private void addApp(String pkg, String label) {
        int idx = work.addApp(pkg, onApp() ? at : Integer.MAX_VALUE);
        if (idx < 0) { say(work.apps.contains(pkg) ? label + " is on the dock already" : "The dock is full: remove an app first"); return; }
        if (label != null) labels.put(pkg, label);
        FrameLayout v = h.railView(pkg);
        v.getChildAt(0).setAlpha(1f);
        v.getChildAt(1).setAlpha(0f);
        h.railViews.add(idx, v);
        h.io.execute(() -> {
            Drawable d = h.dockIcon(pkg);
            h.main.post(() -> {
                if (d != null) ((ImageView) v.getChildAt(0)).setImageDrawable(d);
                else { v.getChildAt(0).setAlpha(0f); v.getChildAt(1).setAlpha(0.9f); }
            });
        });
        // Sections come after the apps: one more app moves their places along.
        at = idx;
        holding = true;
        bar = -1;
        h.placeDock(true);
        say(nameOf(at) + " added  ·  Left and Right move it, OK puts it down");
    }

    private void addSection(int which) {
        int s = work.addSection(which);
        if (s < 0) return;
        h.secOrder = work.sectionArray();
        at = apps() + s;
        holding = true;
        bar = -1;
        h.placeDock(true);
        say(HomeV2.CAT_NAMES[which] + " is back on the dock");
    }

    /** Reset to default, OK twice: the theme's dock_apps (those on this TV), All apps, the sections as they come. */
    private void reset() {
        if (!resetArmed) {
            resetArmed = true;
            say("Press OK again to put the dock back as it came");
            return;
        }
        resetArmed = false;
        DockOrder d = DockOrder.defaults(h.dockDefault);
        List<String> shown = d.shown(h::installed);
        Map<String, FrameLayout> have = new HashMap<>();
        for (FrameLayout v : h.railViews) have.put(v.getTag() == null ? DockOrder.ALL : (String) v.getTag(), v);
        List<FrameLayout> next = new ArrayList<>();
        for (String p : shown) {
            FrameLayout v = have.remove(p);
            if (v == null) {
                v = h.railView(DockOrder.ALL.equals(p) ? null : p);
                FrameLayout nv = v;
                if (!DockOrder.ALL.equals(p)) {
                    nv.getChildAt(0).setAlpha(1f);
                    nv.getChildAt(1).setAlpha(0f);
                    h.io.execute(() -> {
                        Drawable ic = h.dockIcon(p);
                        h.main.post(() -> { if (ic != null) ((ImageView) nv.getChildAt(0)).setImageDrawable(ic); });
                    });
                }
            }
            next.add(v);
        }
        for (FrameLayout v : have.values()) if (v.getParent() == h.dockLayer()) h.dockLayer().removeView(v);
        h.railViews.clear();
        h.railViews.addAll(next);
        work = new DockOrder(shown, d.sections);
        h.secOrder = work.sectionArray();
        at = 0;
        holding = false;
        h.placeDock(true);
        readLabels();
        say("The dock is back as it came");
    }

    /** A word on what was just done (or why it cannot be), in the hint's place in yellow, for a few seconds. */
    private void say(String words) {
        saying = true;
        hint.setText(words);
        hint.setTextColor(0xFFFFD04D);
        h.main.removeCallbacks(fadeSaid);
        h.main.postDelayed(fadeSaid, 3200);
    }

    /** The hint back after say(). */
    private final Runnable fadeSaid = () -> {
        saying = false;
        if (closed) return;
        hint.setTextColor(0xD9FFFFFF);
        look();
    };

    // ---- the look ---------------------------------------------------------------------------------------------------

    private void look() {
        if (closed) return;
        at = Math.max(0, Math.min(at, total() - 1));
        View focused = item(at);
        for (int i = 0; i < total(); i++) {
            View v = item(i);
            boolean on = i == at, held = on && holding, app = i < apps();
            float sc = held ? 1.2f : on ? 1.1f : 1f;
            float ty = held ? -px(16) : on ? -px(4) : 0f;
            float al = holding && !held ? 0.55f : 1f;
            // The apps either side of the focused one stand aside from its glass, as on Home (DockSwell).
            float aside = app && onApp() ? px(DockSwell.aside(i, at, apps())) : 0f;
            v.animate().scaleX(sc).scaleY(sc).translationX(aside).translationY(ty).alpha(al).setDuration(200).setInterpolator(HomeV2.SKY).start();
            if (app) {
                FrameLayout f = (FrameLayout) v;
                boolean isApp = f.getTag() != null;
                if (isApp && ((ImageView) f.getChildAt(0)).getDrawable() != null) {
                    f.getChildAt(0).setAlpha(1f);
                    f.getChildAt(1).setAlpha(0f);
                } else if (!isApp) {
                    f.getChildAt(0).setAlpha(0f);
                    f.getChildAt(1).setAlpha(0.9f);
                }
            } else {
                int s = work.sections.get(i - apps());
                LinearLayout in = (LinearLayout) ((FrameLayout) v).getChildAt(1);
                ((ImageView) in.getChildAt(0)).setColorFilter(on ? Color.WHITE : HomeV2.CAT_COLOURS[s]);
                ((TextView) in.getChildAt(1)).setTypeface(on ? h.semi : h.regular);
            }
        }
        float sc = holding ? 1.2f : 1.1f;
        h.lensAt(focused, onApp(), sc, holding ? px(16) : px(4));
        if (holding) startRock(focused); else stopRock();

        // The name over it: arrows either side while it is picked up.
        name.setText(holding ? "‹   " + nameOf(at) + "   ›" : nameOf(at));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) focused.getLayoutParams();
        if (lp != null) {
            float cx = lp.leftMargin + lp.width / 2f, half = px(300);
            float x = Math.max(0, Math.min(h.root.getWidth() > 0 ? h.root.getWidth() - 2 * half : px(1320), cx - half));
            nameBox.animate().translationX(x).translationY(holding ? -px(14) : 0).setDuration(220).setInterpolator(HomeV2.SKY).start();
        }

        if (!saying) hint.setText(bar >= 0 ? hintBar : holding ? hintHolding : hintChoose);
        buttonWords.get(REMOVE).setText(removeWhy(at) == null ? "Remove " + nameOf(at) : "Remove");
        buttonWords.get(RESET).setText(resetArmed ? "OK again to reset" : BUTTONS[RESET]);
        for (int i = 0; i < buttons.size(); i++) {
            boolean on = i == bar;
            boolean off = (i == REMOVE && removeWhy(at) != null) || (i == ADD && !canAdd());
            LinearLayout b = buttons.get(i);
            GradientDrawable g = h.fill(BUTTON_H / 2f, on ? 0xEBFFFFFF : 0x26FFFFFF);
            g.setStroke(Math.max(1, px(1.5f)), on ? Color.WHITE : 0x47FFFFFF);
            b.setBackground(g);
            int ink = on ? 0xFF1A1D24 : Color.WHITE;
            ((ImageView) b.getChildAt(0)).setColorFilter(ink);
            buttonWords.get(i).setTextColor(ink);
            b.setAlpha(off && !on ? 0.45f : 1f);
            b.animate().scaleX(on ? 1.06f : 1f).scaleY(on ? 1.06f : 1f).setDuration(150).setInterpolator(HomeV2.SKY).start();
        }
    }

    /** The picked-up item rocks gently either way, as a phone's icons do while they are moved. */
    private void startRock(View v) {
        if (rocking == v && rock != null) return;
        stopRock();
        rocking = v;
        rock = android.animation.ObjectAnimator.ofFloat(v, View.ROTATION, -2.5f, 2.5f);
        rock.setDuration(260);
        rock.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        rock.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        rock.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        rock.start();
    }

    private void stopRock() {
        if (rock != null) rock.cancel();
        rock = null;
        if (rocking != null) rocking.animate().rotation(0f).setDuration(120).start();
        rocking = null;
    }

    // ---- Add app ----------------------------------------------------------------------------------------------------

    /**
     * Add app: a card over Home with the sections taken off the dock first, then every app on the TV that opens (TV
     * apps and the others), not on the dock already, A to Z, each by its banner (or its icon and name). OK puts it on
     * the dock; Back goes back to the dock.
     */
    private final class Picker {
        private static final int COLS = 5, TW = 256, TH = 144, GAP = 26, GRID_X = 68, GRID_Y = 176, SHOWN = 4;
        private FrameLayout card, grid;
        private TextView status;
        /** {kind ("app" or "section"), package or section number, label} for each tile, in order. */
        private final List<Object[]> entries = new ArrayList<>();
        private final List<FrameLayout> tiles = new ArrayList<>();
        private int idx;
        private boolean loaded, waitPickUp = true;

        void open() {
            card = new FrameLayout(c);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A2296, 0xFF121980, 0xFF0B0F5A});
            g.setCornerRadius(px(44));
            g.setStroke(Math.max(1, px(1.5f)), 0x40FFFFFF);
            card.setBackground(g);
            card.setClipToOutline(true);
            card.setOutlineProvider(h.round(44));
            card.setTranslationZ(px(3));
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(1520), px(820));
            cl.leftMargin = px(200);
            cl.topMargin = px(70);
            h.root.addView(card, cl);
            TextView title = h.text(40, heavy, Color.WHITE);
            title.setText("Add to the dock");
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
            tl.leftMargin = px(56);
            tl.topMargin = px(36);
            card.addView(title, tl);
            TextView keys = h.text(17, h.regular, 0x99FFFFFF);
            Keys.set(keys, "OK puts it on the dock  ·  Back to go back");
            FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            kl.rightMargin = px(56);
            kl.topMargin = px(52);
            card.addView(keys, kl);
            status = h.text(20, h.regular, 0xCCFFFFFF);
            status.setText(work.roomForApp() ? "Finding the apps on this TV…" : "The dock is full: take an app off it to add another");
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2);
            sl.leftMargin = px(58);
            sl.topMargin = px(106);
            card.addView(status, sl);
            FrameLayout view = new FrameLayout(c);
            view.setClipChildren(false);
            view.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
            view.setClipToOutline(true);
            grid = new FrameLayout(c);
            grid.setClipChildren(false);
            view.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(px(COLS * (TW + GAP) + 24), px(SHOWN * (TH + GAP) - 8));
            vl.leftMargin = px(GRID_X - 12);
            vl.topMargin = px(GRID_Y - 12);
            card.addView(view, vl);
            card.setAlpha(0f);
            card.setScaleX(0.96f);
            card.setScaleY(0.96f);
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(HomeV2.SKY).start();
            nameBox.animate().alpha(0f).setDuration(160).start();

            for (int s : work.offSections()) entries.add(new Object[]{"section", s, HomeV2.CAT_NAMES[s], null, null});
            Set<String> onDock = new HashSet<>(work.apps);
            h.io.execute(() -> {
                List<Object[]> found = installedApps(onDock, Config.load(c).hiddenApps);
                h.main.post(() -> {
                    if (card == null) return;
                    loaded = true;
                    entries.addAll(found);
                    build();
                });
            });
            if (!entries.isEmpty()) build();
        }

        /** The TV's apps that open (TV apps first found, then the others), not the launcher, hidden ones or the dock's. */
        private List<Object[]> installedApps(Set<String> onDock, List<String> hidden) {
            PackageManager pm = c.getPackageManager();
            Map<String, Object[]> found = new HashMap<>();
            String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
            for (String cat : cats) {
                List<ResolveInfo> list;
                try {
                    list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
                } catch (RuntimeException e) {
                    continue;
                }
                for (ResolveInfo r : list) {
                    String p = r.activityInfo.packageName;
                    if (found.containsKey(p) || p.equals(c.getPackageName()) || hidden.contains(p) || onDock.contains(p)) continue;
                    try {
                        Drawable banner = r.activityInfo.loadBanner(pm);
                        if (banner == null) try { banner = pm.getApplicationBanner(p); } catch (Exception ignored) { }
                        // Without a banner, the icon as the dock will show it.
                        Drawable icon = banner != null ? null : h.dockIcon(p);
                        if (banner == null && icon == null) icon = r.loadIcon(pm);
                        found.put(p, new Object[]{"app", p, r.loadLabel(pm).toString(), banner, icon});
                    } catch (RuntimeException | OutOfMemoryError ignored) {
                        // left out
                    }
                }
            }
            List<Object[]> out = new ArrayList<>(found.values());
            out.sort((x, y) -> ((String) x[2]).compareToIgnoreCase((String) y[2]));
            return out;
        }

        private void build() {
            grid.removeAllViews();
            tiles.clear();
            boolean room = work.roomForApp();
            for (int i = 0; i < entries.size(); i++) {
                Object[] e = entries.get(i);
                FrameLayout f = new FrameLayout(c);
                f.setClipToOutline(true);
                f.setOutlineProvider(h.round(14));
                boolean section = "section".equals(e[0]);
                if (section) {
                    int s = (Integer) e[1], col = HomeV2.CAT_COLOURS[s];
                    f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            new int[]{(col & 0xFFFFFF) | 0x8C000000, (col & 0xFFFFFF) | 0x33000000}));
                    f.addView(iconAndName(HomeV2.CAT_ICONS[s], null, (String) e[2] + " section", true),
                            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                } else {
                    f.setBackground(h.fill(14, 0x26FFFFFF));
                    if (e[3] != null) {
                        ImageView b = new ImageView(c);
                        b.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        b.setImageDrawable((Drawable) e[3]);
                        f.addView(b, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    } else {
                        f.addView(iconAndName(0, (Drawable) e[4], (String) e[2], false),
                                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                    if (!room) f.setAlpha(0.4f);
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(TW), px(TH));
                lp.leftMargin = px(12 + (i % COLS) * (TW + GAP));
                lp.topMargin = px(12 + (i / COLS) * (TH + GAP));
                grid.addView(f, lp);
                tiles.add(f);
            }
            if (loaded) {
                int apps = 0;
                for (Object[] e : entries) if ("app".equals(e[0])) apps++;
                status.setText(!room ? "The dock is full: take an app off it to add another"
                        : entries.isEmpty() ? "Every app on this TV is on the dock already"
                        : apps + (apps == 1 ? " app" : " apps") + " not on the dock" + (onApp() ? "  ·  it goes in after " + nameOf(at) : ""));
            }
            idx = Math.min(idx, Math.max(0, tiles.size() - 1));
            paint();
        }

        private LinearLayout iconAndName(int res, Drawable icon, String words, boolean white) {
            LinearLayout in = new LinearLayout(c);
            in.setOrientation(LinearLayout.VERTICAL);
            in.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(c);
            if (res != 0) ic.setImageResource(res); else ic.setImageDrawable(icon);
            if (white) ic.setColorFilter(Color.WHITE);
            ic.setClipToOutline(true);
            ic.setOutlineProvider(h.round(16));
            in.addView(ic, new LinearLayout.LayoutParams(px(white ? 52 : 68), px(white ? 52 : 68)));
            TextView n = h.text(19, h.regular, Color.WHITE);
            n.setText(words);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setMaxWidth(px(TW - 24));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = px(10);
            in.addView(n, nl);
            return in;
        }

        private void paint() {
            for (int i = 0; i < tiles.size(); i++) {
                boolean on = i == idx;
                FrameLayout f = tiles.get(i);
                f.animate().scaleX(on ? 1.07f : 1f).scaleY(on ? 1.07f : 1f).setDuration(150).setInterpolator(HomeV2.SKY).start();
                f.setForeground(on ? h.ring(14) : null);
            }
            int firstRow = Math.max(0, idx / COLS - (SHOWN - 2));
            grid.animate().translationY(-px(firstRow * (TH + GAP))).setDuration(240).setInterpolator(HomeV2.SKY).start();
        }

        boolean key(KeyEvent e) {
            int code = e.getKeyCode();
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (e.getAction() == KeyEvent.ACTION_UP) close();
                return true;
            }
            if (ok) {
                // The OK that opened it is let go first.
                if (e.getAction() == KeyEvent.ACTION_DOWN) waitPickUp = false;
                if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled() && !waitPickUp) choose();
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            int n = tiles.size();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: if (idx % COLS > 0) idx--; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: if (idx % COLS < COLS - 1 && idx + 1 < n) idx++; break;
                case KeyEvent.KEYCODE_DPAD_UP: if (idx >= COLS) idx -= COLS; break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (idx + COLS < n) idx += COLS;
                    else if (idx / COLS < (n - 1) / COLS) idx = n - 1;
                    break;
                default: return code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SEARCH;
            }
            paint();
            return true;
        }

        private void choose() {
            if (idx >= entries.size()) return;
            Object[] e = entries.get(idx);
            if ("app".equals(e[0]) && !work.roomForApp()) {
                status.setText("The dock is full: take an app off it to add another");
                return;
            }
            close();
            if ("section".equals(e[0])) addSection((Integer) e[1]);
            else addApp((String) e[1], (String) e[2]);
            look();
        }

        void close() {
            if (picker == this) picker = null;
            nameBox.animate().alpha(1f).setDuration(200).start();
            FrameLayout cd = card;
            card = null;
            if (cd == null) return;
            cd.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(180).withEndAction(() -> h.root.removeView(cd)).start();
        }
    }

    /** For a quick look in a test or a log: the dock's apps and sections as edited. */
    @Override public String toString() {
        return String.format(Locale.ROOT, "DockEditor[%s | %s, at %d%s]", work.apps, work.sections, at, holding ? ", held" : "");
    }
}
