package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Split screen (two at once, picture and picture): two apps on the screen together, TiviMate beside YouTube say, each
 * in its own window: side by side, the same size and as big as the screen's width allows, or one big with the other
 * small beside it. Android shrinks an app into a
 * window only for its own tools, so the launcher asks the TV's debugging connection (LocalAdb) for them: its
 * freeform-windows setting on, each app opened in a window of its own and placed. The remote works the app in front;
 * Home brings the options: the remote to the other one, swap them round, the layout, which one is heard (the other
 * muted), one of them on its own (full size, the other closed) or back to Home. Behind the pictures Home shows a plain
 * backdrop with each one's name under it. Ended, the windows are left full size and every app's sound is back, also
 * after the launcher restarts mid-way. An app closed or another app opened over them ends it too.
 */
final class TwoUp {
    static final int SIDE = 0, BIG = 1;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final String PREFS = "two_up";

    /** The two apps: [0] on the left (the big one), [1] on the right (the small one). */
    private static final String[] pkg = new String[2];
    private static final String[] comp = new String[2];
    private static final int[] task = {-1, -1};
    private static int layout = SIDE, control = 0, sound = 0;
    private static boolean active, busy;

    private static HomeV2 h;
    private static FrameLayout layer;
    /** What the layer shows: nothing, the pictures' backdrop, the options, or the apps to choose from. */
    private static final int NONE = 0, BACKDROP = 1, OPTIONS = 2, PICK = 3, MESSAGE = 4;
    private static int mode = NONE;
    private static final List<TextView> rows = new ArrayList<>();
    private static final List<Runnable> acts = new ArrayList<>();
    private static final List<View> picks = new ArrayList<>();
    private static final List<String> pickPkgs = new ArrayList<>();
    private static int focus;
    private static String firstPick;
    /** Each attempt's number: one given up (Back or Home while checking) is not carried on when its answer comes. */
    private static int gen;
    /** Why it could not be done, for the message card (null: the TV's system has multi-window off). */
    private static String failWhy;

    private TwoUp() { }

    static boolean active() {
        return active;
    }

    /** Whether the options or the apps to choose from are up (Home's keys come here). */
    static boolean open() {
        return mode == OPTIONS || mode == PICK || mode == MESSAGE;
    }

    // ---- starting ----------------------------------------------------------------------------------------------------

    /** The apps to choose from, Home's dock first (Control Centre's "Two at once"). */
    static void choose(HomeV2 home, List<String> apps) {
        h = home;
        if (active) { options(); return; }
        if (support(home.a) < 0) { build(MESSAGE); return; }
        pickPkgs.clear();
        PackageManager pm = home.a.getPackageManager();
        for (String p : apps) if (p != null && !pickPkgs.contains(p) && launch(pm, p) != null) pickPkgs.add(p);
        if (pickPkgs.size() < 2) return;
        firstPick = null;
        focus = 0;
        build(PICK);
    }

    /** Whether this TV puts apps in windows: 1 yes, -1 no (its system has multi-window off), 0 not yet known. */
    static int support(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("support", 0);
    }

    /** From TwoUpProbe: whether Android really gave it a window of its own. */
    static void probed(Context c, boolean inWindow) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("support", inWindow ? 1 : -1).apply();
    }

    /**
     * a on the left (or big), b on the right (or small), the remote with b. The first time, the launcher's own see-through
     * check (TwoUpProbe) is opened in a window first: on a TV whose system will not (the TCL) it says so, and nothing that
     * is open is closed.
     */
    static void start(HomeV2 home, String a, String b) {
        h = home;
        if (busy || a == null || b == null || a.equals(b)) return;
        Context c0 = home.a;
        if (support(c0) == 0) {
            busy = true;
            failWhy = null;
            int my = ++gen;
            build(MESSAGE);
            // While the TV asks whether to allow the launcher's own connection, the card says so.
            main.postDelayed(() -> { if (my == gen && busy && mode == MESSAGE) build(MESSAGE); }, 9_000L);
            LocalAdb.shellOut(c0, "settings put global enable_freeform_support 1; settings put global force_resizable_activities 1; "
                    + "am start --windowingMode 5 -n " + c0.getPackageName() + "/.TwoUpProbe >/dev/null 2>&1; sleep 2; echo done", out -> {
                if (my != gen) return;   // given up meanwhile
                busy = false;
                if (out == null) { failed(c0); return; }
                if (support(c0) == 0) probed(c0, false);   // it never opened: as good as no
                start(home, a, b);
            });
            return;
        }
        if (support(c0) < 0) {
            // Android's multi-window is off on this TV: its settings put back as they were, and why.
            LocalAdb.shell(c0, "settings delete global enable_freeform_support; settings delete global force_resizable_activities");
            build(MESSAGE);
            return;
        }
        PackageManager pm = home.a.getPackageManager();
        ComponentName ca = launch(pm, a), cb = launch(pm, b);
        if (ca == null || cb == null) return;
        pkg[0] = a;
        pkg[1] = b;
        comp[0] = ca.flattenToShortString();
        comp[1] = cb.flattenToShortString();
        task[0] = task[1] = -1;
        layout = SIDE;
        control = 1;
        sound = 0;
        active = true;
        busy = true;
        save(home.a);
        build(BACKDROP);
        GoalWatchService.homeInFront = false;
        Context c = home.a;
        // Freeform windows on (Android's developer setting: on at once, no restart), then each app in a window of
        // its own: one already open full screen is closed first, as Android brings it back as it was otherwise.
        LocalAdb.shellOut(c, "settings put global enable_freeform_support 1; settings put global force_resizable_activities 1; " + LIST, out -> {
            if (out == null) { failed(c); return; }
            StringBuilder cmd = new StringBuilder();
            for (int i = 0; i < 2; i++) {
                int[] t = taskOf(out, pkg[i]);
                if (t != null && t[1] == 1) continue;   // already in a window: kept
                if (t != null) cmd.append("am stack remove ").append(t[0]).append("; ");
            }
            for (int i = 0; i < 2; i++) cmd.append("am start --windowingMode 5 -n ").append(comp[i]).append(" >/dev/null 2>&1; sleep 1; ");
            cmd.append("sleep 1; ").append(LIST);
            LocalAdb.shellOut(c, cmd.toString(), got -> {
                if (got == null) { failed(c); return; }
                boolean windows = true;
                for (int i = 0; i < 2; i++) {
                    int[] t = taskOf(got, pkg[i]);
                    task[i] = t == null ? -1 : t[0];
                    if (t != null && t[1] == 0) windows = false;
                }
                if (task[0] < 0 || task[1] < 0) { failed(c); return; }
                if (!windows) {
                    // Opened full screen all the same: this TV will not; noted, both left as they are.
                    probed(c, false);
                    busy = false;
                    end(c, 1);
                    return;
                }
                busy = false;
                for (int i = 0; i < 2; i++) GoalWatchService.onAppOpened(c, pkg[i]);
                place(c, true);
                watch();
            });
        });
    }

    /** It could not be done (no debugging connection, or an app would not open in a window): back as it was, and why. */
    private static void failed(Context c) {
        busy = false;
        LocalAdb.State st = LocalAdb.state();
        end(c, -1);
        failWhy = st == LocalAdb.State.NO_DEBUGGING
                ? "Split screen needs the TV's network debugging switched on (Settings, System, Developer options)."
                : st == LocalAdb.State.WAITING_FOR_ALLOW
                ? "The TV didn't let the launcher's own connection in. The first time, a box asks to allow it: choose Always allow "
                + "and Allow. On some TVs (the Google TV Streamer) that box never appears, so split screen can't be set up there."
                : "The apps couldn't be opened side by side. Try again in a moment.";
        build(MESSAGE);
    }

    // ---- the windows ---------------------------------------------------------------------------------------------------

    /** Each window's place now: {left, top, right, bottom} on the screen. */
    private static int[][] bounds() {
        int w = h.a.getResources().getDisplayMetrics().widthPixels, ht = h.a.getResources().getDisplayMetrics().heightPixels;
        float k = w / 1920f;
        int[][] b = new int[2][];
        if (layout == SIDE) {
            // Split screen: the two as big as the screen's width allows, edge to edge with a thin gap between.
            int edge = Math.round(4 * k), gap = Math.round(8 * k);
            int pw = (w - 2 * edge - gap) / 2, ph = Math.round(pw * 9f / 16f), top = (ht - ph) / 2;
            b[0] = new int[]{edge, top, edge + pw, top + ph};
            b[1] = new int[]{w - edge - pw, top, w - edge, top + ph};
        } else {
            int bw = Math.round(1376 * k), bh = Math.round(774 * k), sw = Math.round(472 * k), sh = Math.round(266 * k), top = Math.round(150 * k);
            b[0] = new int[]{Math.round(24 * k), top, Math.round(24 * k) + bw, top + bh};
            b[1] = new int[]{w - Math.round(24 * k) - sw, top, w - Math.round(24 * k), top + sh};
        }
        return b;
    }

    /** Both windows sized and placed, the one with the remote in front, the sound set; inFront: shown over Home. */
    private static void place(Context c, boolean inFront) {
        int[][] b = bounds();
        int other = 1 - control;
        StringBuilder cmd = new StringBuilder();
        for (int i = 0; i < 2; i++)
            cmd.append("am task resize ").append(task[i]).append(' ').append(b[i][0]).append(' ').append(b[i][1]).append(' ')
                    .append(b[i][2]).append(' ').append(b[i][3]).append("; ");
        cmd.append("appops set ").append(pkg[sound]).append(" PLAY_AUDIO allow; appops set ").append(pkg[1 - sound]).append(" PLAY_AUDIO ignore; ");
        if (inFront) {
            cmd.append("am start -n ").append(comp[other]).append(" >/dev/null 2>&1; ");
            cmd.append("am start -n ").append(comp[control]).append(" >/dev/null 2>&1");
        }
        LocalAdb.shell(c, cmd.toString());
        if (inFront) {
            GoalWatchService.homeInFront = false;
            build(BACKDROP);
        }
    }

    /** Back (or Home) on the check or a message: given up, nothing left half done, Home as it was. */
    private static void giveUp() {
        if (busy && !active) { gen++; busy = false; }
        failWhy = null;
        build(NONE);
    }

    /** Home pressed while both are up: the options, over Home. (On the check, the choice or a message: they go.) */
    static boolean home(HomeV2 home) {
        if (!active) {
            if (mode != NONE) giveUp();
            return false;
        }
        h = home;
        options();
        return true;
    }

    private static void options() {
        focus = 0;
        build(OPTIONS);
    }

    /** Back to the two pictures, the remote with the one given. */
    private static void back(int to) {
        control = to;
        place(h.a, true);
    }

    /** One of them on its own, full size (the other closed); or which = -1: both left full size, Home stays. */
    static void end(Context c, int which) {
        if (!active && which >= 0) return;
        StringBuilder cmd = new StringBuilder();
        int w = c.getResources().getDisplayMetrics().widthPixels, ht = c.getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; i < 2; i++) {
            if (task[i] >= 0 && which >= 0 && i != which) cmd.append("am stack remove ").append(task[i]).append("; ");
            else if (task[i] >= 0) cmd.append("am task resize ").append(task[i]).append(" 0 0 ").append(w).append(' ').append(ht).append("; ");
            if (pkg[i] != null) cmd.append("appops set ").append(pkg[i]).append(" PLAY_AUDIO allow; ");
        }
        if (which >= 0 && comp[which] != null) cmd.append("am start -n ").append(comp[which]).append(" >/dev/null 2>&1");
        if (cmd.length() > 0) LocalAdb.shell(c, cmd.toString());
        active = false;
        busy = false;
        main.removeCallbacks(watcher);
        pkg[0] = pkg[1] = comp[0] = comp[1] = null;
        task[0] = task[1] = -1;
        save(c);
        build(NONE);
    }

    /** Started again after the launcher stopped mid-way: the windows it left full size, every app's sound back. */
    static void recover(Context c) {
        if (active) return;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("on", false)) return;
        pkg[0] = p.getString("a", null);
        pkg[1] = p.getString("b", null);
        task[0] = p.getInt("ta", -1);
        task[1] = p.getInt("tb", -1);
        comp[0] = comp[1] = null;
        active = true;
        end(c, -1);
    }

    private static void save(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", active)
                .putString("a", pkg[0]).putString("b", pkg[1]).putInt("ta", task[0]).putInt("tb", task[1]).apply();
    }

    /**
     * Every few seconds while both are up: one of them closed (Back out of it) leaves the other full size; another app
     * opened over them (a remote's app button) ends it, both left full size.
     */
    private static final Runnable watcher = new Runnable() {
        @Override public void run() {
            if (!active || h == null) return;
            if (busy || mode != BACKDROP) { main.postDelayed(this, 4_000); return; }
            Context c = h.a;
            LocalAdb.shellOut(c, "am stack list", out -> {
                if (!active || out == null) { if (active) main.postDelayed(this, 4_000); return; }
                boolean[] alive = {false, false};
                String top = null;
                Matcher m = Pattern.compile("taskId=(\\d+): ([^/ ]+)/\\S+ bounds=\\S+ userId=\\d+ visible=(true|false)").matcher(out);
                while (m.find()) {
                    int id = Integer.parseInt(m.group(1));
                    for (int i = 0; i < 2; i++) if (id == task[i]) alive[i] = true;
                    if (top == null && "true".equals(m.group(3))) top = m.group(2);
                }
                save(c);
                if (!alive[0] || !alive[1]) {
                    int keep = alive[0] ? 0 : alive[1] ? 1 : -1;
                    if (keep >= 0) task[1 - keep] = -1;
                    end(c, keep);
                    return;
                }
                if (top != null && !top.equals(pkg[0]) && !top.equals(pkg[1]) && !top.equals(c.getPackageName())) {
                    end(c, -1);
                    return;
                }
                main.postDelayed(this, 4_000);
            });
        }
    };

    private static void watch() {
        main.removeCallbacks(watcher);
        main.postDelayed(watcher, 4_000);
    }

    // ---- reading the TV's tasks ---------------------------------------------------------------------------------------

    /** The TV's tasks: each with its app (am stack list), then each one's windowing mode (dumpsys). */
    static final String LIST = "am stack list; echo @@@; dumpsys activity activities | grep -E '^ +\\* Task\\{'";
    private static final Pattern APP = Pattern.compile("taskId=(\\d+): ([^/ ]+)/");
    private static final Pattern MODE = Pattern.compile("\\* Task\\{\\S+ #(\\d+) .*?mode=(\\w+)");

    /**
     * The app's task (the top one when it has more): {id, 1 if in a window (freeform) else 0}; null when it has none.
     * By the app's package from the stack list: a task's affinity is not always its package (YouTube's is
     * com.google.android.youtube.tv.main).
     */
    static int[] taskOf(String out, String p) {
        int at = out.indexOf("@@@");
        String stacks = at < 0 ? out : out.substring(0, at), dump = at < 0 ? "" : out.substring(at);
        Matcher m = APP.matcher(stacks);
        while (m.find()) {
            if (!m.group(2).equals(p)) continue;
            String id = m.group(1);
            Matcher mode = MODE.matcher(dump);
            boolean free = false;
            while (mode.find()) if (mode.group(1).equals(id)) { free = "freeform".equals(mode.group(2)); break; }
            return new int[]{Integer.parseInt(id), free ? 1 : 0};
        }
        return null;
    }

    static ComponentName launch(PackageManager pm, String p) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(p);
        if (i == null) i = pm.getLaunchIntentForPackage(p);
        return i == null ? null : i.getComponent();
    }

    private static String name(String p) {
        if (p == null) return "";
        try {
            PackageManager pm = h.a.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString();
        } catch (Exception e) {
            return p;
        }
    }

    // ---- what Home shows ------------------------------------------------------------------------------------------------

    private static int px(float v) {
        return h.px(v);
    }

    private static void build(int what) {
        mode = what;
        if (h == null) return;
        if (layer != null) { h.root.removeView(layer); layer = null; }
        rows.clear();
        acts.clear();
        picks.clear();
        if (what == NONE) return;
        layer = new FrameLayout(h.a);
        layer.setClickable(true);
        // A plain backdrop behind the pictures: Home's navy, darkening to the foot.
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0B1150, 0xFF070A33, 0xFF04061F});
        layer.setBackground(bg);
        h.root.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.bringToFront();
        if (what == BACKDROP) backdrop();
        else if (what == OPTIONS) optionsView();
        else if (what == MESSAGE) messageView();
        else pickView();
    }

    private static TextView text(float size, Typeface face, int colour, String s) {
        TextView t = h.text(size, face, colour);
        t.setText(s);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private static Typeface heavy() {
        return Fonts.get(h.a, R.font.outfit_extrabold);
    }

    private static Typeface semi() {
        return Fonts.get(h.a, R.font.inter_semibold);
    }

    /** Under each picture its app's name (the one with the remote marked), and how to reach the options. */
    private static void backdrop() {
        int[][] b = bounds();
        for (int i = 0; i < 2; i++) {
            if (pkg[i] == null) continue;
            LinearLayout tag = new LinearLayout(h.a);
            tag.setOrientation(LinearLayout.HORIZONTAL);
            tag.setGravity(Gravity.CENTER_VERTICAL);
            Drawable icon = appIcon(pkg[i], px(30));
            if (icon != null) {
                ImageView iv = new ImageView(h.a);
                iv.setImageDrawable(icon);
                tag.addView(iv, new LinearLayout.LayoutParams(px(30), px(30)));
            }
            TextView n = text(20, semi(), 0xE6FFFFFF, name(pkg[i]));
            n.setPadding(px(12), 0, px(12), 0);
            tag.addView(n);
            if (i == control) tag.addView(chip("REMOTE", 0xFF3D4BD6));
            if (i == sound) tag.addView(chip("SOUND", 0x33FFFFFF));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40));
            lp.leftMargin = b[i][0];
            lp.topMargin = b[i][3] + px(16);
            layer.addView(tag, lp);
        }
        TextView title = text(16, heavy(), 0x99FFFFFF, layout == SIDE ? "SPLIT SCREEN" : "TWO AT ONCE");
        title.setLetterSpacing(0.16f);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = px(28);
        tl.topMargin = px(layout == SIDE ? 216 : 104);
        layer.addView(title, tl);
        TextView hint = text(18, semi(), 0x8CFFFFFF, busy ? "Opening both…" : "Home: switch the remote, swap, layout, sound");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = px(36);
        layer.addView(hint, hl);
    }

    private static TextView chip(String s, int colour) {
        TextView t = text(12, heavy(), Color.WHITE, s);
        t.setLetterSpacing(0.14f);
        t.setPadding(px(10), px(4), px(10), px(4));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(6));
        g.setColor(colour);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = px(8);
        t.setLayoutParams(lp);
        return t;
    }

    private static Drawable appIcon(String p, int size) {
        try {
            return HomeV2.fullSquare(h.a, h.a.getPackageManager().getApplicationIcon(p), size);
        } catch (Exception e) {
            return null;
        }
    }

    /** The options, a list down a glass card: the first goes back to both as they were. */
    private static void optionsView() {
        String a = name(pkg[0]), b = name(pkg[1]);
        LinearLayout card = card(layout == SIDE ? "Split screen" : "Two at once", a + "  ·  " + b);
        row(card, "Back to both", () -> back(control));
        row(card, "Remote to " + name(pkg[1 - control]), () -> back(1 - control));
        row(card, "Swap them round", () -> {
            String p = pkg[0]; pkg[0] = pkg[1]; pkg[1] = p;
            String q = comp[0]; comp[0] = comp[1]; comp[1] = q;
            int t = task[0]; task[0] = task[1]; task[1] = t;
            control = 1 - control;
            sound = 1 - sound;
            save(h.a);
            back(control);
        });
        row(card, layout == SIDE ? "One big, one small" : "Split screen, the same size", () -> {
            layout = layout == SIDE ? BIG : SIDE;
            back(control);
        });
        row(card, "Sound from " + name(pkg[1 - sound]), () -> {
            sound = 1 - sound;
            back(control);
        });
        row(card, "Just " + a + ", full screen", () -> end(h.a, 0));
        row(card, "Just " + b + ", full screen", () -> end(h.a, 1));
        row(card, "Stop, back to Home", () -> end(h.a, -1));
        paintRows();
    }

    private static LinearLayout card(String title, String sub) {
        LinearLayout card = new LinearLayout(h.a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(36), px(30), px(36), px(30));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(28));
        g.setColor(0xE6141A63);
        g.setStroke(px(2), 0x33FFFFFF);
        card.setBackground(g);
        TextView t = text(34, heavy(), Color.WHITE, title);
        card.addView(t);
        if (sub != null) {
            TextView s = text(19, semi(), 0xB3FFFFFF, sub);
            s.setPadding(0, px(6), 0, px(18));
            card.addView(s);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(720), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        layer.addView(card, lp);
        return card;
    }

    private static void row(LinearLayout card, String label, Runnable act) {
        TextView r = text(22, semi(), Color.WHITE, label);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(22), 0, px(22), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(58));
        lp.topMargin = px(6);
        card.addView(r, lp);
        rows.add(r);
        acts.add(act);
    }

    private static void paintRows() {
        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            boolean on = i == focus;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(16));
            g.setColor(on ? 0xFFFFFFFF : 0x14FFFFFF);
            r.setBackground(g);
            r.setTextColor(on ? 0xFF10157E : 0xE6FFFFFF);
        }
    }

    /** Checking this TV (the first time), or why it cannot: its system has Android's multi-window switched off. */
    private static void messageView() {
        if (busy) {
            boolean asking = LocalAdb.state() == LocalAdb.State.WAITING_FOR_ALLOW;
            LinearLayout card = card("Split screen", asking ? "Waiting for the TV to allow the launcher's own connection…"
                    : "Checking this TV can show two apps at once…");
            TextView how = text(19, semi(), 0xB3FFFFFF, "");
            how.setSingleLine(false);
            how.setEllipsize(null);
            // "Back stops.": the remote's Back key itself, not the word.
            Keys.set(how, asking ? "If a box asks \"Allow USB debugging?\", choose Always allow, then Allow. Back stops."
                    : "Back stops.");
            card.addView(how);
            return;
        }
        LinearLayout card = card(failWhy != null ? "Split screen couldn't start" : "Split screen isn't available on this TV", null);
        TextView why = text(21, semi(), 0xD9FFFFFF, "");
        why.setSingleLine(false);
        why.setEllipsize(null);
        why.setText(failWhy != null ? failWhy : "This TV's own software has Android's two-windows feature switched off, so two apps "
                + "can't share the screen here. It works on the Google TV Streamer.");
        why.setPadding(0, px(14), 0, px(18));
        card.addView(why);
        row(card, "OK", () -> { failWhy = null; build(NONE); });
        paintRows();
    }

    /** The apps to choose from, in a row: OK on one, then on the other. */
    private static void pickView() {
        LinearLayout card = card("Split screen", firstPick == null ? "Choose the first app, for the left"
                : "Now the second, for the right, beside " + name(firstPick));
        LinearLayout line = new LinearLayout(h.a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_HORIZONTAL);
        int size = px(96);
        for (String p : pickPkgs) {
            FrameLayout box = new FrameLayout(h.a);
            box.setPadding(px(8), px(8), px(8), px(8));
            ImageView iv = new ImageView(h.a);
            iv.setImageDrawable(appIcon(p, size));
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(20)); }
            });
            iv.setClipToOutline(true);
            box.addView(iv, new FrameLayout.LayoutParams(size, size));
            if (p.equals(firstPick)) {
                TextView one = text(16, heavy(), 0xFF10157E, "1");
                one.setGravity(Gravity.CENTER);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Color.WHITE);
                one.setBackground(g);
                box.addView(one, new FrameLayout.LayoutParams(px(28), px(28), Gravity.TOP | Gravity.END));
            }
            line.addView(box, new LinearLayout.LayoutParams(size + px(16), size + px(16)));
            picks.add(box);
        }
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(h.a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(line);
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size + px(24)));
        TextView n = text(20, semi(), Color.WHITE, "");
        n.setPadding(0, px(14), 0, 0);
        n.setTag("name");
        card.addView(n);
        paintPicks();
    }

    private static void paintPicks() {
        for (int i = 0; i < picks.size(); i++) {
            View v = picks.get(i);
            boolean on = i == focus;
            v.animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).setDuration(140).start();
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(26));
            g.setStroke(px(3), on ? Color.WHITE : 0x00FFFFFF);
            v.setBackground(g);
            if (on && v.getParent() instanceof View) {
                View line = (View) v.getParent();
                if (line.getParent() instanceof android.widget.HorizontalScrollView)
                    ((android.widget.HorizontalScrollView) line.getParent()).smoothScrollTo(Math.max(0, v.getLeft() - px(200)), 0);
            }
        }
        View n = layer == null ? null : layer.findViewWithTag("name");
        if (n instanceof TextView && focus < pickPkgs.size()) ((TextView) n).setText(name(pickPkgs.get(focus)));
    }

    /** Home's keys while the options or the apps to choose from are up. */
    static boolean key(KeyEvent e) {
        if (!open()) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (mode == MESSAGE) {
            if (code == KeyEvent.KEYCODE_BACK || !busy && ok) giveUp();
            return true;
        }
        if (mode == PICK) {
            if (code == KeyEvent.KEYCODE_BACK) { build(NONE); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && focus > 0) focus--;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && focus < pickPkgs.size() - 1) focus++;
            else if (ok) {
                String p = pickPkgs.get(focus);
                if (firstPick == null) { firstPick = p; focus = Math.min(focus + 1, pickPkgs.size() - 1); build(PICK); return true; }
                if (p.equals(firstPick)) { firstPick = null; build(PICK); return true; }
                start(h, firstPick, p);
                return true;
            }
            paintPicks();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) { back(control); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_UP && focus > 0) focus--;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus < rows.size() - 1) focus++;
        else if (ok && focus < acts.size()) { acts.get(focus).run(); return true; }
        paintRows();
        return true;
    }

    static String describe() {
        return active ? String.format(Locale.UK, "%s + %s", name(pkg[0]), name(pkg[1])) : "Two apps side by side";
    }
}
