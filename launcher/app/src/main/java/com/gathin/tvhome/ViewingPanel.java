package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Your viewing (launcher v2), from the chart button in Home's top bar: what has played on this TV, from ViewingLog.
 * This week: the hours against the week before and day by day, which apps, when (evenings, by day and hour), what
 * (the films and series, with their posters), and a few habits. Left and Right change tab, Back closes.
 */
final class ViewingPanel {
    private static final int NAVY = 0xFF10157E, YELLOW = 0xFFFFD04D, CARD = 0x1AFFFFFF, EDGE = 0x2EFFFFFF;
    static final String[] PROVIDERS = {"8", "9", "119", "337", "350", "38", "41", "54", "103", "333", "39", "29", "73", "531", "1899"};

    private final HomeV2 h;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Typeface heavy, semi, regular, bold;
    private boolean open;
    private FrameLayout overlay, body;
    private final List<TextView> tabViews = new ArrayList<>();
    private final String[] tabs;
    private int tab, gen;

    ViewingPanel(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = Fonts.get(c, R.font.outfit_extrabold);
        semi = Fonts.get(c, R.font.inter_semibold);
        regular = Fonts.get(c, R.font.inter_regular);
        // The mock-ups' 800-weight labels: Inter's semibold made bold.
        bold = Typeface.create(semi, Typeface.BOLD);
        // Your apps as the mock-up has it (are they worth it?), and the time in every app on a tab of its own.
        tabs = new String[]{"This week", monthName(LocalDate.now().minusMonths(1)), "Your apps", "App time"};
    }

    boolean isOpen() {
        return open;
    }

    void show() {
        show(0);
    }

    /** Opened on a tab (1: last month, from the card on the 1st of the month). */
    void show(int first) {
        if (open) { load(first); return; }
        open = true;
        AppUsage.ensure(c);
        overlay = new FrameLayout(c);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF10157E, 0xFF1B279F, 0xFF2838C0}));
        overlay.setClickable(true);
        TextView crumb = h.text(16, semi, YELLOW);
        crumb.setText("YOUR VIEWING");
        crumb.setLetterSpacing(0.25f);
        place(overlay, crumb, 96, 72, 300, 30);
        LinearLayout bar = new LinearLayout(c);
        tabViews.clear();
        for (String t : tabs) {
            TextView v = h.text(22, regular, Color.WHITE);
            v.setText(t);
            v.setGravity(Gravity.CENTER);
            v.setPadding(h.px(24), 0, h.px(24), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(50));
            lp.rightMargin = h.px(8);
            bar.addView(v, lp);
            tabViews.add(v);
        }
        place(overlay, bar, 400, 58, 1400, 50);
        body = new FrameLayout(c);
        place(overlay, body, 0, 0, 1920, 1080);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        // Out of the Control Centre's button, on a spring.
        Fluid.in(overlay, h.controlButton());
        load(Math.max(0, Math.min(first, tabs.length - 1)));
    }

    void hide() {
        if (!open) return;
        open = false;
        detail = null;
        gen++;
        View o = overlay;
        overlay = null;
        Fluid.out(o, h.controlButton(), () -> h.root.removeView(o));
        h.paint();
    }

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        // An app's page: Back or OK closes it, Left and Right step through the apps.
        if (detail != null) {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (back || ok) {
                if (e.getAction() == KeyEvent.ACTION_UP) closeDetail();
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && detailAt > 0) openDetail(detailApps, detailAt - 1);
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && detailAt < detailApps.size() - 1) openDetail(detailApps, detailAt + 1);
            return true;
        }
        // On a row or card: Back returns to the tabs.
        if (back && pick >= 0) {
            if (e.getAction() == KeyEvent.ACTION_UP) { pick = -1; paintPicks(); }
            return true;
        }
        if (back) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        // OK on a row or card opens its app's page as the button comes up (on the way down, the page would open and
        // its own OK close it again with the same press).
        boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (okKey && pick >= 0) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                // Left and Right on the app's page step through every app on the tab, in the order they show.
                List<String> order = new ArrayList<>();
                for (int i = 0; i < picks.size(); i++) for (String a : pickApps.get(i)) if (!order.contains(a)) order.add(a);
                openDetail(order, order.indexOf(pickApps.get(pick).get(0)));
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (pickKey(code)) return true;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && tab > 0) load(tab - 1);
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && tab < tabs.length - 1) load(tab + 1);
        return true;
    }

    private void load(int t) {
        tab = t;
        int my = ++gen;
        picks.clear();
        pickCol.clear();
        pickApps.clear();
        pick = -1;
        if (detail != null) { overlay.removeView(detail); detail = null; }
        for (int i = 0; i < tabViews.size(); i++) {
            TextView v = tabViews.get(i);
            boolean on = i == t;
            GradientDrawable g = h.fill(25, on ? 0x29FFFFFF : 0);
            if (on) g.setStroke(h.px(2), Color.WHITE);
            v.setBackground(g);
            v.setTypeface(on ? semi : regular);
        }
        body.removeAllViews();
        // The posters fetched off the main thread; the views made on it.
        io.execute(() -> {
            Map<Integer, Bitmap> pics = new HashMap<>();
            Map<Integer, List<String>> kinds = new HashMap<>();
            boolean apps = t >= 2;
            Map<String, String> notes = apps ? appNotes() : new HashMap<>();
            Map<String, Integer> week = t == 1 ? new HashMap<>() : AppUsage.recent(c, 7);
            AppUsage.Span span = t == 1 ? null : AppUsage.span(c, 14);
            Map<String, Long> lastOpen = apps ? AppUsage.lastUsed(c) : new HashMap<>();
            Map<String, Integer> today = apps ? AppUsage.recent(c, 1) : new HashMap<>();
            Map<String, Integer> month = apps ? AppUsage.lastDays(c, 30) : new HashMap<>();
            try {
                List<JSONObject> want = prefetch(t);
                for (int i = 0; i < want.size(); i++) {
                    JSONObject x = want.get(i);
                    Object[] d = details(x.optInt("id"), x.optBoolean("tv"), i < 5);
                    if (d[0] != null) pics.put(x.optInt("id"), (Bitmap) d[0]);
                    kinds.put(x.optInt("id"), (List<String>) d[1]);
                }
            } catch (Exception ignored) {
                // no posters this time
            }
            main.post(() -> {
                if (my != gen || !open) return;
                posters = pics;
                genres = kinds;
                tips = notes;
                usedWeek = week;
                usage14 = span;
                lastUsed = lastOpen;
                usedToday = today;
                used30 = month;
                View v;
                try {
                    v = build(t);
                } catch (Exception ex) {
                    return;
                }
                body.addView(v, new FrameLayout.LayoutParams(-1, -1));
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(200).start();
            });
        });
    }

    /** How long each app was open (AppUsage): the last 7 days, today, the last 30 days. Empty without usage access. */
    private Map<String, Integer> usedWeek = new HashMap<>(), usedToday = new HashMap<>(), used30 = new HashMap<>();
    /** The last fourteen days of app time, by day and hour (This week's hours, bars, heat map and habits). */
    private AppUsage.Span usage14;
    /** When each app was last opened (the last six months). */
    private Map<String, Long> lastUsed = new HashMap<>();

    // ---- Your apps: picking an app, and its page of numbers -------------------------------------------------------

    /** What can be picked on Your apps and App time: each card and row, its column (the cards three across) and its apps. */
    private final List<View> picks = new ArrayList<>();
    private final List<Integer> pickCol = new ArrayList<>();
    private final List<List<String>> pickApps = new ArrayList<>();
    /** The one picked (-1: the focus is on the tabs). */
    private int pick = -1;
    /** The app page open over the tab, the apps it steps through and which one it shows. */
    private FrameLayout detail;
    private List<String> detailApps;
    private int detailAt;
    /** Your apps' numbers, kept for the app pages: the plans, what played in each app, all apps' time together. */
    private Map<String, List<Plan>> plansNow = new HashMap<>();
    private Map<String, Map<String, Integer>> playedOn = new HashMap<>();
    private int allSecs;

    private void addPick(View v, int col, List<String> apps) {
        picks.add(v);
        pickCol.add(col);
        pickApps.add(apps);
    }

    /** The picked row or card ringed in white; the others plain. */
    private void paintPicks() {
        for (int i = 0; i < picks.size(); i++) {
            View v = picks.get(i);
            if (i == pick) {
                GradientDrawable ring = new GradientDrawable();
                boolean row = tab == 3;
                ring.setCornerRadius(h.px(row ? 14 : 24));
                ring.setColor(row ? 0x1FFFFFFF : 0x00000000);
                ring.setStroke(h.px(3), Color.WHITE);
                v.setForeground(ring);
            } else {
                v.setForeground(null);
            }
        }
        // The tab's ring only while the focus is on the tabs.
        for (int i = 0; i < tabViews.size(); i++) {
            boolean on = i == tab;
            GradientDrawable g = h.fill(25, on ? 0x29FFFFFF : 0);
            if (on && pick < 0) g.setStroke(h.px(2), Color.WHITE);
            tabViews.get(i).setBackground(g);
        }
    }

    /** Up and Down along a column, Left and Right across to the nearest in the other; OK opens its app's page. */
    private boolean pickKey(int code) {
        if (picks.isEmpty()) return false;
        if (pick < 0) {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            pick = 0;
            paintPicks();
            return true;
        }
        int col = pickCol.get(pick);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: {
                int to = -1;
                for (int i = pick - 1; i >= 0; i--) if (pickCol.get(i) == col) { to = i; break; }
                pick = to;
                break;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:
                for (int i = pick + 1; i < picks.size(); i++) if (pickCol.get(i) == col) { pick = i; break; }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                // The column to the left or right (Your apps' cards stand three across).
                int want = col + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (!pickCol.contains(want)) return true;
                int[] at = new int[2];
                picks.get(pick).getLocationOnScreen(at);
                int y = at[1], best = -1, bestD = Integer.MAX_VALUE;
                for (int i = 0; i < picks.size(); i++) {
                    if (pickCol.get(i) != want) continue;
                    picks.get(i).getLocationOnScreen(at);
                    int d = Math.abs(at[1] - y);
                    if (d < bestD) { bestD = d; best = i; }
                }
                if (best >= 0) pick = best;
                break;
            }
            default:
                return true;
        }
        paintPicks();
        return true;
    }

    private void openDetail(List<String> apps, int at) {
        detailApps = apps;
        detailAt = Math.max(0, at);
        if (detail == null) {
            detail = new FrameLayout(c);
            detail.setBackgroundColor(0xD905081F);
            detail.setClickable(true);
            overlay.addView(detail, new FrameLayout.LayoutParams(-1, -1));
            detail.setAlpha(0f);
            detail.animate().alpha(1f).setDuration(160).start();
        }
        detail.removeAllViews();
        View page = appPage(detailApps.get(detailAt));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(1300), -2, Gravity.CENTER);
        detail.addView(page, lp);
    }

    private void closeDetail() {
        if (detail == null) return;
        View d = detail;
        detail = null;
        d.animate().alpha(0f).setDuration(140).withEndAction(() -> { if (overlay != null) overlay.removeView(d); }).start();
    }

    /** "today 10:52", "yesterday 21:14", "Sat 31 Oct", "12 Sep". */
    private static String ago(long at) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault());
        LocalDate d = z.toLocalDate(), today = LocalDate.now();
        String hmm = z.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (d.equals(today)) return "today " + hmm;
        if (d.equals(today.minusDays(1))) return "yesterday " + hmm;
        if (d.isAfter(today.minusDays(7))) return z.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK));
        return z.format(DateTimeFormatter.ofPattern(d.getYear() == today.getYear() ? "d MMM" : "d MMM yyyy", Locale.UK));
    }

    private LinearLayout statTile(String label, String value, String under) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(h.px(20), h.px(14), h.px(20), h.px(14));
        t.setBackground(cardGround());
        t.addView(label(label));
        TextView v = h.text(34, heavy, Color.WHITE);
        v.setText(value);
        v.setSingleLine(true);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.topMargin = h.px(4);
        t.addView(v, vl);
        if (under != null) {
            TextView u = h.text(14, regular, 0xB3FFFFFF);
            u.setText(under);
            u.setSingleLine(true);
            t.addView(u);
        }
        return t;
    }

    /** Bars, the largest in yellow, with small labels under each and its time over it. */
    private LinearLayout bars(int[] values, String[] names, int height, boolean times) {
        LinearLayout bars = new LinearLayout(c);
        bars.setGravity(Gravity.BOTTOM);
        int most = 1;
        for (int v : values) most = Math.max(most, v);
        for (int i = 0; i < values.length; i++) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            if (times) {
                TextView n = h.text(11, semi, 0xCCFFFFFF);
                n.setText(values[i] >= 60 ? hmShort(values[i]) : "");
                n.setSingleLine(true);
                col.addView(n);
            }
            View b = new View(c);
            b.setBackground(h.fill(5, values[i] == most && most > 1 ? YELLOW : 0x8CFFFFFF));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Math.max(h.px(3), Math.round(h.px(height) * values[i] / (float) most)));
            bl.leftMargin = bl.rightMargin = h.px(3);
            bl.topMargin = h.px(3);
            col.addView(b, bl);
            TextView d = h.text(11, semi, 0xB3FFFFFF);
            d.setText(names[i]);
            d.setSingleLine(true);
            col.addView(d);
            bars.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return bars;
    }

    /**
     * One app's page: how long it was open (30 days, this week, today, a day), its share of all the TV's time and its
     * place among the apps, the last fourteen days and the hours of the day it is used, how often it is opened and its
     * longest stretch; for a paid one what it costs (a month, an hour, a day, a year at this rate), each plan with
     * when it renews or ends, what was paid in the last year, and what £1 an hour would take; and what played there.
     */
    private View appPage(String pkg) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(h.px(44), h.px(34), h.px(44), h.px(28));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A2296, 0xFF121980, 0xFF0B0F5A});
        g.setCornerRadius(h.px(32));
        g.setStroke(h.px(1.5f), 0x40FFFFFF);
        box.setBackground(g);
        int s30 = used30.getOrDefault(pkg, 0), sWeek = usedWeek.getOrDefault(pkg, 0), sToday = usedToday.getOrDefault(pkg, 0);
        List<Plan> plans = plansNow.get(pkg);
        boolean paid = plans != null && !plans.isEmpty() && ReleaseAlerts.installed(c, pkg);
        double cost = paid ? monthly(plans) : 0, hour = per(cost, s30);

        // The app, and its verdict when it is paid for.
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(appMark(pkg), new LinearLayout.LayoutParams(0, h.px(44), 1f));
        if (paid) {
            String word;
            int colour;
            if (s30 < 600) { word = "NOT USED"; colour = RED; }
            else if (hour < 1) { word = "BEST VALUE"; colour = GREEN; }
            else if (hour < 2) { word = "GOOD VALUE"; colour = GREEN; }
            else if (hour < 4) { word = "OK"; colour = YELLOW; }
            else { word = "HARDLY USED"; colour = RED; }
            head.addView(chip(word, colour));
        }
        box.addView(head);
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(used30.entrySet());
        ranked.sort((x, y) -> y.getValue() - x.getValue());
        int place = 0;
        for (int i = 0; i < ranked.size(); i++) if (ranked.get(i).getKey().equals(pkg)) place = i + 1;
        AppUsage.Span sp = usage14;
        int opens = sp == null ? 0 : sp.opens.getOrDefault(pkg, 0);
        Long last = sp != null && sp.last.containsKey(pkg) ? sp.last.get(pkg) : lastUsed.get(pkg);
        List<String> about = new ArrayList<>();
        about.add(last == null ? "Not opened in the last six months" : "Last opened " + ago(last));
        if (opens > 0) about.add("opened " + opens + (opens == 1 ? " time" : " times") + " in 14 days");
        if (place > 0) about.add("#" + place + " of " + ranked.size() + " apps on this TV");
        TextView sub = h.text(18, regular, 0xD9FFFFFF);
        sub.setText(String.join("  ·  ", about));
        box.addView(sub, under(8));

        // The numbers.
        LinearLayout tiles = new LinearLayout(c);
        int share = allSecs > 0 ? Math.round(s30 * 100f / allSecs) : 0;
        String[][] t = {{"30 DAYS", hm(s30), null}, {"THIS WEEK", hm(sWeek), null}, {"TODAY", hm(sToday), null},
                {"A DAY", hm(s30 / 30), "on average"}, {"SHARE", share + "%", "of all the TV's time"}};
        for (int i = 0; i < t.length; i++) {
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) tl.leftMargin = h.px(14);
            tiles.addView(statTile(t[i][0], t[i][1], t[i][2]), tl);
        }
        box.addView(tiles, under(20));

        // The last fourteen days, and the hours of the day.
        LinearLayout charts = new LinearLayout(c);
        LinearLayout days = new LinearLayout(c);
        days.setOrientation(LinearLayout.VERTICAL);
        days.setPadding(h.px(22), h.px(16), h.px(22), h.px(12));
        days.setBackground(cardGround());
        days.addView(label("LAST 14 DAYS"));
        int[] perDay = new int[14];
        String[] dayNames = new String[14];
        for (int i = 0; i < 14; i++) {
            perDay[i] = sp == null ? 0 : sp.dayApps.get(i).getOrDefault(pkg, 0);
            dayNames[i] = LocalDate.now().minusDays(13 - i).getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.UK);
        }
        days.addView(bars(perDay, dayNames, 90, true), under(8));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, h.px(200), 1.15f);
        charts.addView(days, dl);
        LinearLayout hours = new LinearLayout(c);
        hours.setOrientation(LinearLayout.VERTICAL);
        hours.setPadding(h.px(22), h.px(16), h.px(22), h.px(12));
        hours.setBackground(cardGround());
        int[] perHour = sp == null || !sp.appHours.containsKey(pkg) ? new int[24] : sp.appHours.get(pkg);
        int bestH = -1, bestS = 0;
        for (int k = 0; k < 24; k++) {
            int three = perHour[k] + perHour[(k + 1) % 24] + perHour[(k + 2) % 24];
            if (three > bestS) { bestS = three; bestH = k; }
        }
        LinearLayout hh = new LinearLayout(c);
        hh.addView(label("TIME OF DAY"), new LinearLayout.LayoutParams(0, -2, 1f));
        if (bestH >= 0) {
            TextView b = h.text(14, semi, YELLOW);
            b.setText(String.format(Locale.UK, "Mostly %02d:00–%02d:00", bestH, (bestH + 3) % 24));
            hh.addView(b);
        }
        hours.addView(hh);
        String[] hourNames = new String[24];
        for (int k = 0; k < 24; k++) hourNames[k] = k % 6 == 0 ? String.format(Locale.UK, "%02d", k) : "";
        hours.addView(bars(perHour, hourNames, 104, false), under(8));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, h.px(200), 1f);
        hl.leftMargin = h.px(14);
        charts.addView(hours, hl);
        int any = 0;
        for (int v : perDay) any += v;
        if (any >= 60) box.addView(charts, under(14));
        else {
            TextView none = h.text(20, regular, 0xD9FFFFFF);
            boolean brief = last != null && last > System.currentTimeMillis() - 14L * 24 * 3_600_000L;
            none.setText(brief ? "Only opened briefly in the last 14 days (under a minute each time)." : "Not opened in the last 14 days.");
            box.addView(none, under(18));
        }

        // How it is used.
        if (sp != null && sp.longest.containsKey(pkg)) {
            int longest = sp.longest.get(pkg);
            int typical = opens > 0 ? sp.apps.getOrDefault(pkg, 0) / opens : 0;
            TextView use = h.text(17, regular, 0xD9FFFFFF);
            use.setText("Longest stretch " + hm(longest) + " (" + ago(sp.longestAt.get(pkg)) + ")"
                    + (typical >= 60 ? "  ·  about " + hm(typical) + " each time it is opened" : ""));
            box.addView(use, under(14));
        }

        // What it costs.
        if (paid) {
            box.addView(label("WHAT IT COSTS"), under(18));
            List<String> money = new ArrayList<>();
            money.add("£" + money(cost) + " a month");
            if (s30 >= 600) money.add((hour < 1 ? Math.round(hour * 100) + "p" : "£" + money(hour)) + " an hour");
            int daysUsed = 0;
            for (int v : perDay) if (v >= 60) daysUsed++;
            if (daysUsed > 0) {
                double perUseDay = cost * 12 / 365 * 14 / daysUsed;
                money.add((perUseDay < 1 ? Math.round(perUseDay * 100) + "p" : "£" + money(perUseDay)) + " a day it is used");
            }
            money.add("£" + money(cost * 12) + " a year at this rate");
            TextView m = h.text(20, semi, Color.WHITE);
            m.setText(String.join("  ·  ", money));
            box.addView(m, under(6));
            for (Plan pl : plans) {
                LocalDate d = pl.next();
                long left = d == null ? -1 : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d);
                String state = !pl.active() ? "ended " + day(d) : d == null ? (pl.renews ? "renews monthly" : "")
                        : (pl.renews ? "renews " : "ends ") + day(d) + " (" + in(left) + ")";
                TextView pt = small("•  " + (pl.name == null ? appName(pkg) : pl.name) + " · " + pl.price() + (state.isEmpty() ? "" : " · " + state),
                        false, pl.active() ? 0xE6FFFFFF : 0x80FFFFFF);
                pt.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(16));
                box.addView(pt, under(4));
            }
            List<String> more = new ArrayList<>();
            double year = paidInYear(plans);
            boolean bought = false;
            for (Plan pl : plans) if (!pl.renews) bought = true;
            if (bought && year > 0) more.add("£" + money(year) + " paid in the last 12 months");
            if (s30 < 600) more.add("pausing it saves £" + money(cost) + " a month, £" + money(cost * 12) + " a year");
            else if (hour >= 1) {
                int need = (int) Math.ceil(cost * 3600);
                more.add("£1 an hour would take " + hm(need) + " a month (now " + hm(s30) + ")");
            } else {
                more.add("under £1 an hour: well used");
            }
            TextView mt = h.text(16, semi, YELLOW);
            mt.setText(String.join("  ·  ", more));
            box.addView(mt, under(8));
        }

        // What played there.
        Map<String, Integer> on = playedOn.get(pkg);
        if (on != null && !on.isEmpty()) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(on.entrySet());
            list.sort((x, y) -> y.getValue() - x.getValue());
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < Math.min(4, list.size()); k++) b.append(k == 0 ? "" : ",  ").append(list.get(k).getKey()).append(" ").append(hm(list.get(k).getValue()));
            box.addView(label("WATCHED THERE LAST MONTH"), under(18));
            TextView w = h.text(17, regular, 0xD9FFFFFF);
            w.setText(b.toString());
            w.setSingleLine(true);
            w.setEllipsize(android.text.TextUtils.TruncateAt.END);
            box.addView(w, under(6));
        }

        TextView foot = h.text(14, regular, 0x99FFFFFF);
        Keys.set(foot, (detailApps.size() > 1 ? "◀ ▶  other apps (" + (detailAt + 1) + " of " + detailApps.size() + ")   ·   " : "") + "Back to close");
        foot.setGravity(Gravity.END);
        box.addView(foot, under(16));
        return box;
    }

    /**
     * The log's days with Android's app time in place of what played: each day's seconds in each app ("a") and in
     * each hour ("h"); the titles ("t") stay the log's. from: the span's first day to use.
     */
    private static List<JSONObject> withUse(List<JSONObject> log, AppUsage.Span use, int from) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < log.size(); i++) {
            try {
                JSONObject d = new JSONObject(log.get(i).toString());
                d.put("a", new JSONObject(use.dayApps.get(from + i)));
                JSONArray hh = new JSONArray();
                for (int k = 0; k < 24; k++) hh.put(use.hours[from + i][k]);
                d.put("h", hh);
                out.add(d);
            } catch (Exception e) {
                out.add(log.get(i));
            }
        }
        return out;
    }

    /** The posters each tab shows, fetched before it is drawn. */
    private Map<Integer, Bitmap> posters = new HashMap<>();

    /** Each title's kinds (TMDB's genres), for last month's personality. */
    private Map<Integer, List<String>> genres = new HashMap<>();

    /** The titles whose posters (the first five) and kinds a tab shows. Off the main thread. */
    private List<JSONObject> prefetch(int t) {
        if (t >= 2) return new ArrayList<>();
        List<JSONObject> all = t == 1 ? titles(month(LocalDate.now().minusMonths(1))) : titles(days(LocalDate.now(), 7));
        return all.subList(0, Math.min(t == 1 ? 10 : 5, all.size()));
    }

    private View build(int t) {
        return t == 3 ? appTimeView() : t == 2 ? appsView() : t == 1 ? monthView() : week();
    }

    // ---- your apps: what they cost an hour ----------------------------------------------------------------------

    /**
     * The paid apps: package, name, typical UK price, and whether it is paid by the month or the year (the theme's
     * app_prices replace the price, and can give the next renewal).
     */
    private static final String[][] PAID = {
            {"com.netflix.ninja", "Netflix", "12.99", "month"}, {"com.amazon.amazonvideo.livingroom", "Prime Video", "8.99", "month"},
            {"com.disney.disneyplus", "Disney+", "9.99", "month"}, {"com.apple.atve.androidtv.appletv", "Apple TV", "9.99", "month"},
            {"com.nowtv.androidtv", "NOW", "9.99", "month"}, {"com.cbs.ca", "Paramount+", "6.99", "month"},
            {"com.wbd.stream", "HBO Max", "9.99", "month"}, {"ar.tvplayer.tv", "TiviMate", "10.99", "year"},
            {ReleaseAlerts.DEBRID, "Debrid Stream", "2.29", "month"}};

    /**
     * One thing paid for in an app: the app's own subscription (name null) or an in-app purchase (TiviMate's
     * playlists, Debrid Stream's Torbox). The amount, how many months it covers, and the date that matters: the next
     * renewal when it renews by itself, else the day it ends.
     */
    static final class Plan {
        String name;
        double amount;
        int months = 1;
        LocalDate date;
        boolean renews = true;

        /** The cost of a month: a year's or three months' price spread over its months. */
        double monthly() {
            return amount / months;
        }

        /** Still paid for today: it renews, or it has not yet ended. */
        boolean active() {
            return renews || date == null || !date.isBefore(LocalDate.now());
        }

        /** The next renewal (a date given in the past moves on a period at a time), or the end; null when not known. */
        LocalDate next() {
            if (date == null || !renews) return date;
            LocalDate d = date, today = LocalDate.now();
            for (int i = 0; d.isBefore(today) && i < 1200; i++) d = d.plusMonths(months);
            return d;
        }

        /** "£10.99 a year", "£2.27 a month", "£23.33 for 3 months". */
        String price() {
            return "£" + money(amount) + (months == 1 ? " a month" : months == 12 ? " a year" : " for " + months + " months");
        }
    }

    /** "month", "year", "3 months", "2 years": in months. */
    private static int months(String period) {
        String t = period.trim().toLowerCase(Locale.UK);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(t);
        int n = m.find() ? Integer.parseInt(m.group(1)) : 1;
        return t.contains("year") ? 12 * n : Math.max(1, n);
    }

    private static LocalDate date(String s) {
        try {
            return s == null || s.trim().isEmpty() ? null : LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** The package a name in the theme means ("Debrid Stream" or "DebridStream"), or the name itself. */
    private static String pkgFor(String who) {
        for (String[] p : PAID) if (p[1].replace(" ", "").equalsIgnoreCase(who.trim().replace(" ", ""))) return p[0];
        return who.trim();
    }

    /**
     * What each app costs, as its plans: the usual UK price, else the theme's "app_prices" ("Netflix|12.99", or with
     * the period and the next renewal, "TiviMate|10.99|year|2027-06-15"); an app named in "app_plans" has exactly the
     * plans listed there, its own and its in-app purchases: "App|Plan|amount|month, year or 3 months|date|renews or
     * ends" (the date the next renewal, or the end).
     */
    private Map<String, List<Plan>> prices() {
        Map<String, List<Plan>> out = new LinkedHashMap<>();
        for (String[] p : PAID) {
            Plan plan = new Plan();
            plan.amount = Double.parseDouble(p[2]);
            plan.months = "year".equals(p[3]) ? 12 : 1;
            out.put(p[0], new ArrayList<>(java.util.Collections.singletonList(plan)));
        }
        for (String line : Config.themeStrings(c, "app_prices", new ArrayList<>())) {
            String[] kv = line.split("\\|");
            if (kv.length < 2) continue;
            String who = pkgFor(kv[0]);
            List<Plan> list = out.computeIfAbsent(who, k -> new ArrayList<>(java.util.Collections.singletonList(new Plan())));
            Plan plan = list.get(0);
            try {
                plan.amount = Double.parseDouble(kv[1].trim().replace("£", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (kv.length > 2 && !kv[2].trim().isEmpty()) plan.months = months(kv[2]);
            if (kv.length > 3) plan.date = date(kv[3]);
        }
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (String line : Config.themeStrings(c, "app_plans", new ArrayList<>())) {
            String[] kv = line.split("\\|", -1);
            if (kv.length < 3) continue;
            String who = pkgFor(kv[0]);
            if (listed.add(who)) out.put(who, new ArrayList<>());
            Plan plan = new Plan();
            String name = kv[1].trim();
            plan.name = name.isEmpty() || pkgFor(name).equals(who) ? null : name;
            try {
                plan.amount = Double.parseDouble(kv[2].trim().replace("£", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (kv.length > 3 && !kv[3].trim().isEmpty()) plan.months = months(kv[3]);
            if (kv.length > 4) plan.date = date(kv[4]);
            if (kv.length > 5) plan.renews = !kv[5].trim().toLowerCase(Locale.UK).startsWith("end");
            out.get(who).add(plan);
        }
        return out;
    }

    /** What an app costs a month now: every plan still paid for. */
    private static double monthly(List<Plan> plans) {
        double m = 0;
        for (Plan p : plans) if (p.active()) m += p.monthly();
        return m;
    }

    /**
     * What was paid for an app in the last twelve months, where the theme says when: each purchase that does not
     * renew (paid its period before it ends), and the last payment of each yearly or longer plan that does. Monthly
     * renewals are left out, their start not being known. -1 when nothing is known.
     */
    private static double paidInYear(List<Plan> plans) {
        LocalDate today = LocalDate.now(), from = today.minusYears(1);
        double sum = 0;
        boolean known = false;
        for (Plan p : plans) {
            LocalDate paid;
            if (!p.renews && p.date != null) paid = p.date.minusMonths(p.months);
            else if (p.renews && p.date != null && p.months >= 12) paid = p.next().minusMonths(p.months);
            else continue;
            if (paid.isAfter(from) && !paid.isAfter(today)) { sum += p.amount; known = true; }
        }
        return known ? sum : -1;
    }

    /** Tips for each app, from the series watched here (Your shows): what is coming on it, and when it ends. */
    private Map<String, String> tips = new HashMap<>();

    private Map<String, String> appNotes() {
        Map<String, String> out = new HashMap<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        LocalDate today = LocalDate.now();
        for (WatchHistory.Entry e : WatchHistory.load(c)) {
            if (!e.tv || e.tmdbId <= 0 || !seen.add(e.tmdbId) || seen.size() > 15) continue;
            JSONObject d = YourShows.details(h.tmdbKey, e.tmdbId);
            if (d == null) continue;
            JSONObject gb = YourShows.gb(d), next = d.optJSONObject("next_episode_to_air");
            JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
            String app = null;
            for (int i = 0; flat != null && i < flat.length() && app == null; i++) {
                String a = HomeV2.serviceApp(String.valueOf(flat.optJSONObject(i).optInt("provider_id")));
                if (a != null && ReleaseAlerts.installed(c, a)) app = a;
            }
            if (app == null || next == null || out.containsKey(app)) continue;
            try {
                LocalDate nd = LocalDate.parse(next.optString("air_date"));
                int season = next.optInt("season_number"), ep = next.optInt("episode_number");
                int count = 0;
                JSONArray ss = d.optJSONArray("seasons");
                for (int i = 0; ss != null && i < ss.length(); i++) if (ss.getJSONObject(i).optInt("season_number") == season) count = ss.getJSONObject(i).optInt("episode_count");
                // Weekly from the next one: the season's last episode.
                LocalDate end = count > ep ? nd.plusWeeks(count - ep) : nd;
                out.put(app, d.optString("name") + (ep == 1 ? " is back " : " has new episodes until ")
                        + (ep == 1 ? nd : end).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + (ep == 1 ? "" : " (weekly)"));
            } catch (Exception ignored) {
                // no note for it
            }
        }
        return out;
    }

    /** Your apps' numbers for the month: what played in each app (ViewingLog), the titles on each, the time that counts. */
    private static final class Month {
        Map<String, Integer> played = new HashMap<>(), secs = new HashMap<>();
        Map<String, Map<String, Integer>> what = new HashMap<>();
        boolean byUse, counting;
    }

    /** Last month's play in each app and its titles, and the time each app counts for (also kept for the app pages). */
    private Month appMonth() {
        Month m = new Month();
        m.byUse = !used30.isEmpty();
        LocalDate any = LocalDate.now().minusMonths(1);
        for (JSONObject d : month(any)) {
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                m.played.put(p, m.played.getOrDefault(p, 0) + a.optInt(p));
            }
            JSONObject t = d.optJSONObject("t");
            for (Iterator<String> it = t == null ? null : t.keys(); it != null && it.hasNext(); ) {
                JSONObject one = t.optJSONObject(it.next());
                String p = one.optString("p", "");
                if (p.isEmpty() || one.optString("n").isEmpty()) continue;
                Map<String, Integer> w = m.what.computeIfAbsent(p, k -> new HashMap<>());
                w.put(one.optString("n"), w.getOrDefault(one.optString("n"), 0) + one.optInt("s"));
            }
        }
        // Without usage access, fewer than four weeks logged: no rating yet, only the hours so far.
        LocalDate first = ViewingLog.firstDay(c);
        m.counting = !m.byUse && (first == null || first.isAfter(LocalDate.now().minusDays(28)));
        m.secs = m.byUse ? used30 : m.played;
        if (m.counting) {
            m.secs = new HashMap<>();
            for (LocalDate d = first == null ? LocalDate.now() : first; !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
                JSONObject a = ViewingLog.day(c, d).optJSONObject("a");
                for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                    String p = it.next();
                    m.secs.put(p, m.secs.getOrDefault(p, 0) + a.optInt(p));
                }
            }
        }
        playedOn = m.what;
        int all = 0;
        for (int s : used30.values()) all += s;
        allSecs = all;
        plansNow = prices();
        return m;
    }

    /**
     * Your apps, as the mock-up has it: "Are your apps worth it?" with the month's cost and hours, then each paid app
     * a card three across, best value first: its logo and verdict, what an hour of it costs, a bar for its value, what
     * was watched on it, and a tip. Down picks a card, OK opens its numbers.
     */
    private View appsView() {
        FrameLayout v = new FrameLayout(c);
        Month m = appMonth();
        Map<String, List<Plan>> price = plansNow;
        List<String> apps = new ArrayList<>();
        for (String p : price.keySet()) if (ReleaseAlerts.installed(c, p) && !price.get(p).isEmpty()) apps.add(p);
        Map<String, Integer> use = m.secs;
        apps.sort((x, y) -> Double.compare(per(monthly(price.get(x)), use.getOrDefault(x, 0)), per(monthly(price.get(y)), use.getOrDefault(y, 0))));

        // The question, and the month in one line: what they cost together, for how many hours, and so an hour.
        LinearLayout head = new LinearLayout(c);
        TextView big = h.text(44, heavy, Color.WHITE);
        big.setText("Are your apps worth it?");
        big.setSingleLine(true);
        head.addView(big);
        double cost = 0;
        int secs = 0;
        for (String p : apps) { cost += monthly(price.get(p)); secs += use.getOrDefault(p, 0); }
        TextView sum = h.text(19, regular, 0xCCFFFFFF);
        int hrs = Math.round(secs / 3600f);
        String hours = secs < 3600 ? hm(secs) : hrs + (hrs == 1 ? " hour" : " hours");
        sum.setText(apps.isEmpty() ? "No paid streaming apps on this TV."
                : m.counting ? "Still counting: " + hours + " so far · £" + money(cost) + " a month"
                : "Last 30 days · £" + money(cost) + " for " + hours + (secs >= 3600 ? " · £" + money(cost / (secs / 3600.0)) + " an hour" : ""));
        sum.setSingleLine(true);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = h.px(18);
        head.addView(sum, sl);
        place(v, head, 96, 120, 1728, 70);

        // Apps not opened in 30 days (with nothing coming on them) share one card at the end.
        List<String> unused = new ArrayList<>();
        if (!m.counting) for (String p : apps) if (use.getOrDefault(p, 0) < 600 && tips.get(p) == null) unused.add(p);
        if (unused.size() < 2) unused.clear();
        int cards = Math.min(9, apps.size() - unused.size() + (unused.isEmpty() ? 0 : 1));
        int rows = Math.max(1, (cards + 2) / 3);
        boolean compact = rows > 2;
        int top = 204, gap = 24, w = (1728 - 2 * gap) / 3, vgap = compact ? 18 : 24;
        int hgt = compact ? (980 - top - 2 * vgap) / 3 : 300;
        int i = 0;
        for (String p : apps) {
            if (unused.contains(p)) continue;
            if (i >= cards - (unused.isEmpty() ? 0 : 1)) break;
            View card = valueCard(p, use.getOrDefault(p, 0), price.get(p), m.counting, m.what.get(p), compact);
            place(v, card, 96 + i % 3 * (w + gap), top + i / 3 * (hgt + vgap), w, hgt);
            addPick(card, i % 3, java.util.Collections.singletonList(p));
            i++;
        }
        if (!unused.isEmpty()) {
            View card = unusedCard(unused, price, compact);
            place(v, card, 96 + i % 3 * (w + gap), top + i / 3 * (hgt + vgap), w, hgt);
            addPick(card, i % 3, unused);
        }
        TextView foot = h.text(15, regular, 0x99FFFFFF);
        foot.setText("Prices from the theme's app_prices and app_plans, or the usual UK price. When a show ends comes from TMDB. "
                + "Nothing is cancelled for you: it only tells you.");
        place(v, foot, 96, 1000, 1300, 30);
        TextView hint = h.text(15, semi, 0xB3FFFFFF);
        hint.setText("▼ to pick an app, OK for its numbers");
        hint.setGravity(Gravity.END);
        place(v, hint, 1424, 1000, 400, 30);
        return v;
    }

    /** App time: every app opened on the TV, longest first, with the last 30 days, this week and today. */
    private View appTimeView() {
        FrameLayout v = new FrameLayout(c);
        Month m = appMonth();
        TextView head = h.text(44, heavy, Color.WHITE);
        head.setText("Time in every app");
        place(v, head, 96, 120, 1200, 64);
        TextView sum = h.text(19, regular, 0xD9FFFFFF);
        sum.setText(m.byUse ? "Every app opened on this TV in the last 30 days: " + hm(allSecs) + " in all, " + hm(allSecs / 30) + " a day. "
                + "Counted by Android on the TV, never sent anywhere."
                : "Waiting for usage access: Android's record of how long each app is open. Until then, only what played in apps shows.");
        place(v, sum, 96, 186, 1728, 30);
        LinearLayout list = card(v, 96, 236, 1728, 744);
        LinearLayout cols = new LinearLayout(c);
        cols.setGravity(Gravity.CENTER_VERTICAL);
        cols.setPadding(h.px(10), 0, h.px(10), 0);
        cols.addView(label("EVERY APP YOU USE"), new LinearLayout.LayoutParams(0, -2, 1f));
        for (String col : new String[]{"30 DAYS", "THIS WEEK", "TODAY"}) {
            TextView l = label(col);
            l.setGravity(Gravity.END);
            cols.addView(l, new LinearLayout.LayoutParams(h.px(col.equals("30 DAYS") ? 140 : 150), -2));
        }
        list.addView(cols);
        List<Map.Entry<String, Integer>> rows = new ArrayList<>((m.byUse ? used30 : usedWeek).entrySet());
        rows.sort((x, y) -> y.getValue() - x.getValue());
        int top = rows.isEmpty() ? 1 : rows.get(0).getValue(), most = 11;
        for (int i = 0; i < Math.min(most, rows.size()); i++) {
            View r = usageRow(rows.get(i).getKey(), rows.get(i).getValue(), top, i == 0);
            list.addView(r);
            addPick(r, 0, java.util.Collections.singletonList(rows.get(i).getKey()));
        }
        if (rows.isEmpty()) {
            TextView n = h.text(20, regular, 0xBFFFFFFF);
            n.setText(m.byUse ? "Nothing opened in the last 30 days." : "Usage access is being turned on. Open this tab again in a moment.");
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
            nl.topMargin = h.px(20);
            list.addView(n, nl);
        } else if (rows.size() > most) {
            int rest = 0;
            for (int i = most; i < rows.size(); i++) rest += rows.get(i).getValue();
            TextView more = h.text(15, regular, 0x99FFFFFF);
            more.setText("and " + (rows.size() - most) + " more, " + hm(rest) + " between them");
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
            ml.topMargin = h.px(8);
            ml.leftMargin = h.px(10);
            list.addView(more, ml);
        }
        TextView hint = h.text(15, semi, 0xB3FFFFFF);
        hint.setText("▼ to pick an app, OK for its numbers");
        hint.setGravity(Gravity.END);
        place(v, hint, 1424, 1000, 400, 30);
        return v;
    }

    /** One app in the list: its mark, a bar against the most used, then the last 30 days, this week and today. */
    private View usageRow(String pkg, int secs, int top, boolean first) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // A little room each side for the ring when it is picked.
        row.setPadding(h.px(10), h.px(9), h.px(10), h.px(9));
        FrameLayout mark = new FrameLayout(c);
        mark.addView(appMark(pkg), new FrameLayout.LayoutParams(-2, h.px(32), Gravity.CENTER_VERTICAL));
        row.addView(mark, new LinearLayout.LayoutParams(h.px(300), h.px(36)));
        FrameLayout track = new FrameLayout(c);
        track.setBackground(h.fill(7, 0x24FFFFFF));
        View fill = new View(c);
        fill.setBackground(h.fill(7, first ? brand(pkg) : 0xBFFFFFFF));
        track.addView(fill, new FrameLayout.LayoutParams(Math.max(h.px(6), Math.round(h.px(760) * secs / (float) top)), -1));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(h.px(760), h.px(14));
        tp.rightMargin = h.px(20);
        row.addView(track, tp);
        row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        int[] times = {secs, usedWeek.getOrDefault(pkg, 0), usedToday.getOrDefault(pkg, 0)};
        for (int k = 0; k < 3; k++) {
            TextView t = h.text(k == 0 ? 21 : 18, k == 0 ? semi : regular, k == 0 ? Color.WHITE : 0xCCFFFFFF);
            t.setText(times[k] < 60 ? "—" : hm(times[k]));
            t.setGravity(Gravity.END);
            t.setSingleLine(true);
            row.addView(t, new LinearLayout.LayoutParams(h.px(k == 0 ? 140 : 150), -2));
        }
        return row;
    }

    /** A paid app: its mark and verdict, what it costs an hour of use, and a tip or what was watched on it. */
    private static String day(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern(d.getYear() == LocalDate.now().getYear() ? "d MMM" : "d MMM yyyy", Locale.UK));
    }

    /** "in 5 days", "tomorrow", "today". */
    private static String in(long days) {
        return days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
    }

    private static final int RED = 0xFFFF5A5A, GREEN = 0xFF3DDC84;

    /** The verdict's chip: its words on its colour. */
    private TextView chip(String word, int colour) {
        TextView chip = h.text(14, bold, colour == YELLOW ? NAVY : colour == GREEN ? 0xFF06331B : Color.WHITE);
        chip.setText(word);
        chip.setLetterSpacing(0.07f);
        chip.setSingleLine(true);
        chip.setPadding(h.px(10), h.px(5), h.px(10), h.px(5));
        chip.setBackground(h.fill(6, colour));
        return chip;
    }

    private TextView small(String text, boolean bold, int colour) {
        TextView t = h.text(14, bold ? semi : regular, colour);
        t.setText(text);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout.LayoutParams under(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = h.px(top);
        return l;
    }

    /** An app's value as a bar's share: 75p an hour nearly full, £1.50 two thirds, £12 an eighth. */
    private static float value(double hour) {
        return (float) Math.max(0.06, Math.min(1.0, 1.0 / (1.0 + Math.max(0.0, hour - 0.6) * 0.6)));
    }

    /** The card's tip, in a rounded box: its first part ("Tip", "Coming", "Netflix renews tomorrow") bold in the colour. */
    private View tipBox(String advice, int colour, boolean poor, boolean compact) {
        TextView t = h.text(16, regular, Color.WHITE);
        int cut = advice.indexOf(" · ");
        android.text.SpannableString sp = new android.text.SpannableString(advice);
        if (cut > 0) {
            sp.setSpan(new android.text.style.ForegroundColorSpan(colour), 0, cut + 3, 0);
            sp.setSpan(new FitsTonight.Face(bold), 0, cut + 3, 0);
        }
        t.setText(sp);
        t.setMaxLines(compact ? 1 : 2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setLineSpacing(0, 1.1f);
        t.setPadding(h.px(14), h.px(compact ? 9 : 12), h.px(14), h.px(compact ? 9 : 12));
        t.setBackground(h.fill(14, poor ? 0x2EFF5A5A : 0x1AFFFFFF));
        return t;
    }

    private LinearLayout.LayoutParams barParams(boolean compact) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, h.px(10));
        l.topMargin = h.px(compact ? 10 : 12);
        return l;
    }

    /** The bar under the price: its share of the width in the verdict's colour. */
    private View valueBar(float share, int colour) {
        LinearLayout track = new LinearLayout(c);
        track.setBackground(h.fill(5, 0x24FFFFFF));
        View fill = new View(c);
        fill.setBackground(h.fill(5, colour));
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, share));
        track.addView(new View(c), new LinearLayout.LayoutParams(0, -1, 1f - share));
        return track;
    }

    /**
     * A paid app as the mock-up's card: its logo and verdict, what an hour of it costs ("75p an hour · 17h 20m for
     * £12.99"), a bar for its value, what was watched on it (or its plans, when several are paid for), and a tip: a
     * renewal or an end coming up, a series coming, or pausing it.
     */
    private View valueCard(String p, int s, List<Plan> plans, boolean counting, Map<String, Integer> on, boolean compact) {
        double cost = monthly(plans);
        double hour = per(cost, s);
        List<Plan> live = new ArrayList<>();
        for (Plan pl : plans) if (pl.active()) live.add(pl);
        String word;
        int colour;
        if (counting) { word = "COUNTING"; colour = 0x59FFFFFF; }
        else if (s < 600) { word = "NOT USED"; colour = RED; }
        else if (hour < 1) { word = "BEST VALUE"; colour = GREEN; }
        else if (hour < 2) { word = "GOOD VALUE"; colour = GREEN; }
        else if (hour < 4) { word = "OK"; colour = YELLOW; }
        else { word = "HARDLY USED"; colour = RED; }
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(h.px(26), h.px(compact ? 16 : 22), h.px(26), h.px(compact ? 14 : 20));
        GradientDrawable g = h.fill(24, CARD);
        g.setStroke(h.px(colour == RED ? 2 : 1), colour == RED ? colour : EDGE);
        card.setBackground(g);

        // The app's logo, its verdict on the right.
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout mark = new FrameLayout(c);
        View logo = appMark(p);
        if (logo instanceof ImageView) ((ImageView) logo).setMaxWidth(h.px(170));
        mark.addView(logo, new FrameLayout.LayoutParams(-2, h.px(compact ? 30 : 36), Gravity.CENTER_VERTICAL));
        top.addView(mark, new LinearLayout.LayoutParams(0, -1, 1f));
        top.addView(chip(word, colour));
        card.addView(top, new LinearLayout.LayoutParams(-1, h.px(40)));

        // What an hour costs, large, then the hours and the price.
        boolean several = live.size() > 1;
        Plan one = live.isEmpty() ? plans.get(0) : live.get(0);
        // "for £12.99" (a month's plan), "for £10.99 a year", "for £24.97 a month" (several).
        String paying = several ? "£" + money(cost) + " a month" : one.months == 1 ? "£" + money(one.amount) : one.price();
        String monthly = several || one.months != 1 ? "" : " a month";
        LinearLayout price = new LinearLayout(c);
        TextView big = h.text(compact ? 44 : 50, heavy, Color.WHITE);
        big.setText(counting ? hm(s) : s < 600 ? "—" : hour < 1 ? Math.round(hour * 100) + "p" : "£" + money(hour));
        big.setLetterSpacing(-0.02f);
        big.setIncludeFontPadding(false);
        price.addView(big);
        TextView sm = h.text(18, regular, 0xCCFFFFFF);
        sm.setText(counting ? "so far · " + paying + monthly
                : s < 600 ? "not opened in 30 days · " + paying + monthly
                : "an hour · " + hm(s) + " for " + paying);
        sm.setSingleLine(true);
        sm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sml = new LinearLayout.LayoutParams(0, -2, 1f);
        sml.leftMargin = h.px(12);
        price.addView(sm, sml);
        card.addView(price, under(compact ? 8 : 14));
        card.addView(valueBar(counting ? Math.min(1f, s / (20 * 3600f)) : s < 600 ? 0.04f : value(hour),
                colour == 0x59FFFFFF ? 0xBFFFFFFF : colour), barParams(compact));

        // What was watched on it ("Lupin, Dead-End Job and 2 more."), or its plans when several are paid for.
        String line = null;
        if (several) {
            StringBuilder b = new StringBuilder();
            for (Plan pl : live) {
                LocalDate d = pl.next();
                b.append(b.length() == 0 ? "" : " · ").append(pl.name == null ? appName(p) : pl.name).append(" ").append(pl.price())
                        .append(d == null ? "" : (pl.renews ? ", renews " : ", ends ") + day(d));
            }
            line = b.toString();
        } else if (on != null && !on.isEmpty()) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(on.entrySet());
            list.sort((x, y) -> y.getValue() - x.getValue());
            StringBuilder b = new StringBuilder();
            int shown = Math.min(2, list.size());
            for (int k = 0; k < shown; k++) b.append(k == 0 ? "" : list.size() == 2 ? " and " : ", ").append(list.get(k).getKey());
            if (list.size() > 2) b.append(" and ").append(list.size() - 2).append(" more");
            line = b.append(".").toString();
        }
        double year = paidInYear(plans);
        boolean bought = false;
        for (Plan pl : plans) if (!pl.renews) bought = true;
        if (line == null && bought && year > 0) line = "£" + money(year) + " paid in the last 12 months.";
        if (line != null) {
            TextView w = h.text(16, regular, 0xCCFFFFFF);
            w.setText(line);
            w.setSingleLine(true);
            w.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(w, under(compact ? 8 : 10));
        }

        // The tip: the soonest renewal or end in the next fortnight comes first.
        String tip = counting ? null : tips.get(p);
        boolean poor = !counting && (hour >= 4 || s < 600);
        Plan soon = null;
        long soonDays = Long.MAX_VALUE;
        for (Plan pl : live) {
            LocalDate d = pl.next();
            if (d == null) continue;
            long dd = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d);
            if (dd >= 0 && dd <= (pl.renews ? 10 : 14) && dd < soonDays) { soon = pl; soonDays = dd; }
        }
        String what = soon == null ? null : soon.name == null ? appName(p) : soon.name;
        String advice;
        if (soon != null && !soon.renews) {
            advice = what + " ends " + in(soonDays) + (poor ? " · let it go, it is hardly used" : " · renew it if you still watch it");
        } else if (soon != null && poor && tip == null) {
            // Little used and renewing soon: the moment to stop it.
            advice = what + " renews " + in(soonDays) + " · cancel before then to save " + soon.price().replace(" a ", " this ");
        } else if (tip != null) {
            advice = poor ? "Tip · " + tip + ": keep it till then, pause after" : "Coming · " + tip;
        } else if (poor) {
            advice = "Tip · Nothing you watch there is coming soon: pausing it saves £" + money(cost) + " a month";
        } else if (soon != null) {
            advice = what + " renews " + in(soonDays) + " · £" + money(soon.amount) + ", worth keeping";
        } else {
            advice = null;
        }
        if (advice != null) {
            int ink = advice.startsWith("Coming") ? GREEN : colour == 0x59FFFFFF ? YELLOW : colour;
            card.addView(tipBox(advice, ink, poor, compact), under(compact ? 8 : 12));
        }
        return card;
    }

    /** The apps not opened in 30 days, together: their logos, what they cost between them, and what pausing saves. */
    private View unusedCard(List<String> apps, Map<String, List<Plan>> price, boolean compact) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(h.px(26), h.px(compact ? 16 : 22), h.px(26), h.px(compact ? 14 : 20));
        GradientDrawable g = h.fill(24, CARD);
        g.setStroke(h.px(2), RED);
        card.setBackground(g);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        double sum = 0;
        StringBuilder each = new StringBuilder();
        for (String p : apps) {
            View mark = appMark(p);
            if (mark instanceof ImageView) ((ImageView) mark).setMaxWidth(h.px(120));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, h.px(28));
            ml.rightMargin = h.px(16);
            top.addView(mark, ml);
            double m = monthly(price.get(p));
            sum += m;
            each.append(each.length() == 0 ? "" : " · ").append(appName(p)).append(" £").append(money(m));
        }
        top.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(chip("NOT USED", RED));
        card.addView(top, new LinearLayout.LayoutParams(-1, h.px(40)));
        LinearLayout cost = new LinearLayout(c);
        TextView big = h.text(compact ? 44 : 50, heavy, Color.WHITE);
        big.setText("£" + money(sum));
        big.setLetterSpacing(-0.02f);
        big.setIncludeFontPadding(false);
        cost.addView(big);
        TextView sm = h.text(18, regular, 0xCCFFFFFF);
        sm.setText("a month · none opened in 30 days");
        sm.setSingleLine(true);
        sm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sml = new LinearLayout.LayoutParams(0, -2, 1f);
        sml.leftMargin = h.px(12);
        cost.addView(sm, sml);
        card.addView(cost, under(compact ? 8 : 14));
        card.addView(valueBar(0.04f, RED), barParams(compact));
        TextView line = h.text(16, regular, 0xCCFFFFFF);
        line.setText(each + " a month.");
        line.setSingleLine(true);
        line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(line, under(compact ? 8 : 10));
        String n = apps.size() == 2 ? "both" : apps.size() == 3 ? "all three" : "all " + apps.size();
        card.addView(tipBox("Tip · Nothing you watch on them is coming soon: pausing " + n + " saves £" + money(sum) + " a month",
                RED, true, compact), under(compact ? 8 : 12));
        return card;
    }

    /** An app's name ("Debrid Stream" for DebridStream). */
    private String appName(String pkg) {
        for (String[] p : PAID) if (p[0].equals(pkg)) return p[1];
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static double per(double price, int secs) {
        return secs < 600 ? 999 : price / (secs / 3600.0);
    }

    private static String money(double v) {
        return String.format(Locale.UK, "%.2f", v);
    }

    /** A film's or series' poster (when asked) and genres, from TMDB: {Bitmap or null, List<String>}. Off the main thread. */
    private Object[] details(int id, boolean tv, boolean picture) {
        List<String> kinds = new ArrayList<>();
        if (id <= 0 || h.tmdbKey.isEmpty()) return new Object[]{null, kinds};
        try {
            JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id + "?api_key=" + h.tmdbKey));
            JSONArray g = d.optJSONArray("genres");
            for (int i = 0; g != null && i < g.length(); i++) kinds.add(g.getJSONObject(i).optString("name"));
            String p = d.optString("poster_path", "");
            Bitmap b = !picture || p.isEmpty() || "null".equals(p) ? null : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w342" + p, h.px(260));
            return new Object[]{b, kinds};
        } catch (Exception e) {
            return new Object[]{picture ? poster(id, tv) : null, kinds};
        }
    }

    static String monthName(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
    }

    /** A calendar month's records, its first day first. */
    private List<JSONObject> month(LocalDate any) {
        LocalDate first = any.withDayOfMonth(1);
        List<JSONObject> out = new ArrayList<>();
        for (LocalDate d = first; d.getMonthValue() == first.getMonthValue(); d = d.plusDays(1)) out.add(ViewingLog.day(c, d));
        return out;
    }

    // ---- last month ---------------------------------------------------------------------------------------------

    private View monthView() {
        LocalDate any = LocalDate.now().minusMonths(1), first = any.withDayOfMonth(1);
        String name = monthName(any);
        List<JSONObject> days = month(any), before = month(any.minusMonths(1));
        int sum = 0, was = 0, live = 0, late = 0, weekend = 0, withTv = 0, bestDay = 0, bestSecs = 0;
        for (int i = 0; i < days.size(); i++) {
            JSONObject d = days.get(i);
            int s = total(d);
            sum += s;
            if (s >= 15 * 60) withTv++;
            if (s > bestSecs) { bestSecs = s; bestDay = i; }
            DayOfWeek dow = first.plusDays(i).getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) weekend += s;
            JSONArray hh = d.optJSONArray("h");
            for (int k = 21; k < 24; k++) late += hh == null ? 0 : hh.optInt(k);
            for (int k = 0; k < 3; k++) late += hh == null ? 0 : hh.optInt(k);
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                if (ViewingLog.live(p)) live += a.optInt(p);
            }
        }
        for (JSONObject d : before) was += total(d);
        FrameLayout v = new FrameLayout(c);
        if (sum < 30 * 60) {
            TextView none = h.text(26, regular, 0xD9FFFFFF);
            none.setText("Not enough watched on this TV in " + name + " for a round-up. Next month's is on the 1st.");
            place(v, none, 96, 200, 1300, 120);
            return v;
        }
        // The month's hours, big.
        TextView kicker = h.text(18, semi, YELLOW);
        kicker.setText("YOUR " + name.toUpperCase(Locale.UK) + " ON TV");
        kicker.setLetterSpacing(0.28f);
        place(v, kicker, 120, 140, 900, 30);
        int hours = Math.round(sum / 3600f);
        TextView big = h.text(150, heavy, Color.WHITE);
        big.setText(hours + (hours == 1 ? " hour" : " hours"));
        big.setLetterSpacing(-0.03f);
        place(v, big, 108, 168, 1200, 200);
        TextView sub = h.text(26, regular, 0xEBFFFFFF);
        int days24 = sum / 86400, rest = (sum % 86400) / 3600;
        String compare = was <= 0 ? "" : ", " + Math.abs(hours - Math.round(was / 3600f)) + (hours >= Math.round(was / 3600f) ? " more" : " fewer")
                + " than " + monthName(any.minusMonths(1));
        sub.setText((days24 > 0 ? "That's " + days24 + (days24 == 1 ? " day and " : " days and ") + rest + " hours" : "That's " + hm(sum)) + compare + ".");
        place(v, sub, 120, 372, 1200, 40);

        // The top show, then the next four.
        List<JSONObject> titles = titles(days);
        JSONObject top = null;
        for (JSONObject t : titles) if (t.optBoolean("tv")) { top = t; break; }
        if (top == null && !titles.isEmpty()) top = titles.get(0);
        if (top != null) {
            FrameLayout pic = new FrameLayout(c);
            pic.setBackground(h.fill(20, 0xFF0B0F55));
            pic.setClipToOutline(true);
            pic.setOutlineProvider(h.round(20));
            ImageView pi = new ImageView(c);
            pi.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = posters.get(top.optInt("id"));
            if (b != null) pi.setImageBitmap(b);
            pic.addView(pi, new FrameLayout.LayoutParams(-1, -1));
            TextView one = h.text(36, heavy, YELLOW);
            one.setText("#1");
            one.setShadowLayer(h.px(10), 0, h.px(2), 0xB3000000);
            FrameLayout.LayoutParams ol = new FrameLayout.LayoutParams(-2, -2);
            ol.leftMargin = h.px(12);
            ol.topMargin = h.px(6);
            pic.addView(one, ol);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(h.px(20));
            ring.setStroke(h.px(5), YELLOW);
            pic.setForeground(ring);
            place(v, pic, 120, 440, 230, 345);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView head = h.text(16, semi, 0xD9FFFFFF);
            head.setText(top.optBoolean("tv") ? "YOUR TOP SHOW" : "YOUR TOP FILM");
            head.setLetterSpacing(0.2f);
            col.addView(head);
            TextView n = h.text(48, heavy, Color.WHITE);
            n.setText(top.optString("n"));
            n.setMaxLines(2);
            col.addView(n);
            int eps = top.optJSONArray("eps") == null ? 0 : top.optJSONArray("eps").length();
            int span = 0;
            for (JSONObject d : days) if (d.optJSONObject("t") != null && d.optJSONObject("t").has(keyOf(top))) span++;
            TextView line = h.text(22, regular, 0xEBFFFFFF);
            line.setText((eps > 1 ? eps + " episodes in " + span + (span == 1 ? " day" : " days") + " · " : "") + hm(top.optInt("s")));
            col.addView(line);
            String app = top.optString("p", "");
            if (!app.isEmpty()) {
                LinearLayout on = new LinearLayout(c);
                on.setGravity(Gravity.CENTER_VERTICAL);
                TextView w = h.text(18, regular, 0xD9FFFFFF);
                w.setText("on  ");
                on.addView(w);
                on.addView(appMark(app), new LinearLayout.LayoutParams(-2, h.px(26)));
                LinearLayout.LayoutParams onl = new LinearLayout.LayoutParams(-2, -2);
                onl.topMargin = h.px(8);
                col.addView(on, onl);
            }
            place(v, col, 380, 600, 420, 200);
        }
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        int n = 2;
        for (JSONObject t : titles) {
            if (t == top) continue;
            if (n > 5) break;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = h.text(34, heavy, 0xD9FFFFFF);
            num.setText(String.valueOf(n++));
            row.addView(num, new LinearLayout.LayoutParams(h.px(50), -2));
            ImageView p = new ImageView(c);
            p.setScaleType(ImageView.ScaleType.CENTER_CROP);
            p.setBackground(h.fill(8, 0xFF0B0F55));
            p.setClipToOutline(true);
            p.setOutlineProvider(h.round(8));
            Bitmap b = posters.get(t.optInt("id"));
            if (b != null) p.setImageBitmap(b);
            row.addView(p, new LinearLayout.LayoutParams(h.px(60), h.px(90)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView tn = h.text(21, semi, Color.WHITE);
            tn.setText(t.optString("n"));
            tn.setSingleLine(true);
            words.addView(tn);
            TextView tt = h.text(17, regular, 0xCCFFFFFF);
            tt.setText(hm(t.optInt("s")));
            words.addView(tt);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(h.px(380), -2);
            wl.leftMargin = h.px(16);
            row.addView(words, wl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
            rl.bottomMargin = h.px(14);
            list.addView(row, rl);
        }
        place(v, list, 860, 440, 520, 440);

        // Your TV personality.
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(h.px(30), h.px(26), h.px(30), h.px(26));
        card.setBackground(h.fill(26, 0xF2FFFFFF));
        TextView ph = h.text(15, semi, 0xFFFF2D7A);
        ph.setText("YOUR TV PERSONALITY");
        ph.setLetterSpacing(0.2f);
        card.addView(ph);
        String[] who = personality(titles, live, late, weekend, sum);
        TextView pn = h.text(42, heavy, NAVY);
        pn.setText(who[0]);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = h.px(8);
        card.addView(pn, pl);
        TextView pt = h.text(19, regular, 0xD910157E);
        pt.setText(who[1]);
        pt.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = h.px(10);
        card.addView(pt, tl);
        place(v, card, 1380, 120, 440, 330);

        // The month in four facts.
        LinearLayout facts = new LinearLayout(c);
        int films = 0;
        JSONObject longest = null;
        for (JSONObject t : titles) {
            if (t.optBoolean("tv") || !keyOf(t).startsWith("mv:")) continue;
            films++;
            if (longest == null || t.optInt("s") > longest.optInt("s")) longest = t;
        }
        LocalDate big1 = first.plusDays(bestDay);
        String[][] four = {
                {"BIGGEST DAY", big1.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + " " + big1.getDayOfMonth() + " · " + hm(bestSecs)},
                {"LIVE TV & SPORT", hm(live)},
                {"FILMS", films == 0 ? "None" : films + (longest != null ? " · most, " + longest.optString("n") : "")},
                {"DAYS WITH TV", withTv + " of " + days.size()}};
        for (String[] f : four) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView a = h.text(14, semi, 0xB3FFFFFF);
            a.setText(f[0]);
            a.setLetterSpacing(0.18f);
            col.addView(a);
            TextView b = h.text(21, semi, Color.WHITE);
            b.setText(f[1]);
            b.setSingleLine(true);
            col.addView(b);
            facts.addView(col, new LinearLayout.LayoutParams(h.px(420), -2));
        }
        place(v, facts, 120, 900, 1700, 80);
        return v;
    }

    /** The key a title was logged under. */
    private static String keyOf(JSONObject t) {
        int id = t.optInt("id");
        return id > 0 ? (t.optBoolean("tv") ? "tv:" : "mv:") + id : "x:" + t.optString("n");
    }

    /** {name, line}: the month's TV personality, from the kinds watched most (by time) and when. */
    private String[] personality(List<JSONObject> titles, int live, int late, int weekend, int sum) {
        Map<String, Integer> kinds = new HashMap<>();
        for (JSONObject t : titles) {
            List<String> g = genres.get(t.optInt("id"));
            if (g == null) continue;
            for (String k : g) {
                String group = group(k);
                if (group != null) kinds.put(group, kinds.getOrDefault(group, 0) + t.optInt("s"));
            }
        }
        String best = null;
        int bestS = 0;
        for (Map.Entry<String, Integer> e : kinds.entrySet()) if (e.getValue() > bestS) { best = e.getKey(); bestS = e.getValue(); }
        int latePct = Math.round(late * 100f / Math.max(1, sum));
        String when = latePct >= 50 ? "Late-Night" : weekend * 100f / Math.max(1, sum) >= 50 ? "Weekend" : "Evening";
        if (live * 100f / Math.max(1, sum) >= 40 || best == null) {
            return new String[]{"The " + when + " Matchday Regular", "Live TV and sport were " + Math.round(live * 100f / Math.max(1, sum))
                    + "% of your month, and " + latePct + "% of your hours were after 9pm."};
        }
        String noun;
        switch (best) {
            case "Crime and thrillers": noun = "Detective"; break;
            case "Comedy": noun = "Comedy Fan"; break;
            case "Sci-fi and fantasy": noun = "Explorer"; break;
            case "Drama": noun = "Drama Lover"; break;
            case "Animation and family": noun = "Family Screener"; break;
            case "Documentaries": noun = "Fact Finder"; break;
            case "Reality": noun = "Reality Watcher"; break;
            case "Action": noun = "Action Hero"; break;
            case "Horror": noun = "Fright Fan"; break;
            default: noun = "Romantic";
        }
        return new String[]{"The " + when + " " + noun, best + " made up " + Math.round(bestS * 100f / Math.max(1, sum))
                + "% of your month, and " + latePct + "% of your hours were after 9pm."};
    }

    private static String group(String genre) {
        switch (genre) {
            case "Crime": case "Thriller": case "Mystery": return "Crime and thrillers";
            case "Comedy": return "Comedy";
            case "Science Fiction": case "Sci-Fi & Fantasy": case "Fantasy": return "Sci-fi and fantasy";
            case "Drama": return "Drama";
            case "Animation": case "Family": case "Kids": return "Animation and family";
            case "Documentary": return "Documentaries";
            case "Reality": return "Reality";
            case "Action": case "Action & Adventure": case "Adventure": case "War": case "War & Politics": return "Action";
            case "Horror": return "Horror";
            case "Romance": return "Romance";
            default: return null;
        }
    }

    // ---- This week -------------------------------------------------------------------------------------------------

    /** The last seven days' records (today last), and the seven before them. */
    private List<JSONObject> days(LocalDate last, int n) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) out.add(ViewingLog.day(c, last.minusDays(i)));
        return out;
    }

    static int total(JSONObject d) {
        JSONObject a = d.optJSONObject("a");
        int s = 0;
        for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) s += a.optInt(it.next());
        return s;
    }

    private View week() {
        LocalDate today = LocalDate.now();
        List<JSONObject> week = days(today, 7), before = days(today.minusDays(7), 7);
        // Android's record of how long each app was open, where it can be read: every app, whether or not it says what
        // it plays. The week before only when the record reaches back that far.
        AppUsage.Span use = usage14;
        int used = 0;
        for (int i = 7; use != null && i < 14; i++) used += use.days[i];
        boolean byTime = use != null && used >= 60;
        if (byTime) {
            week = withUse(week, use, 7);
            before = use.days[0] > 0 ? withUse(before, use, 0) : new ArrayList<>();
            while (before.size() < 7) before.add(new JSONObject());
        }
        FrameLayout v = new FrameLayout(c);
        int sum = 0, was = 0;
        int[] perDay = new int[7];
        for (int i = 0; i < 7; i++) { perDay[i] = total(week.get(i)); sum += perDay[i]; was += total(before.get(i)); }
        if (sum < 60 && usedWeek.isEmpty()) {
            TextView none = h.text(26, regular, 0xD9FFFFFF);
            none.setText("Nothing watched on this TV in the last week yet. What plays in any app (and on live TV) shows here, "
                    + "counted on the TV and never sent anywhere.");
            place(v, none, 96, 200, 1300, 120);
            return v;
        }

        // The hours, against the week before, and day by day.
        LinearLayout first = card(v, 96, 150, 560, 300);
        first.addView(label(today.minusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK)
                + " – " + today.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK)));
        LinearLayout big = new LinearLayout(c);
        big.setGravity(Gravity.BOTTOM);
        TextView hours = h.text(76, heavy, Color.WHITE);
        hours.setText(hm(sum));
        hours.setLetterSpacing(-0.026f);
        hours.setIncludeFontPadding(false);
        big.addView(hours);
        if (was > 0) {
            TextView delta = h.text(18, semi, YELLOW);
            int diff = sum - was;
            delta.setText((diff >= 0 ? "▲ " : "▼ ") + hm(Math.abs(diff)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
            dl.leftMargin = h.px(14);
            dl.bottomMargin = h.px(12);
            big.addView(delta, dl);
        }
        first.addView(big);
        TextView avg = h.text(16, regular, 0xBFFFFFFF);
        avg.setText("on the TV this week · " + hm(sum / 7) + " a night");
        first.addView(avg);
        LinearLayout bars = new LinearLayout(c);
        bars.setGravity(Gravity.BOTTOM);
        int most = 1;
        for (int s : perDay) most = Math.max(most, s);
        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            TextView n = h.text(12, bold, 0xCCFFFFFF);
            n.setText(perDay[i] >= 60 ? hmShort(perDay[i]) : "");
            col.addView(n);
            View b = new View(c);
            b.setBackground(h.fill(8, perDay[i] == most ? YELLOW : 0x8CFFFFFF));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(h.px(34), Math.max(h.px(4), Math.round(h.px(96) * perDay[i] / (float) most)));
            bl.topMargin = h.px(4);
            col.addView(b, bl);
            TextView d = h.text(15, bold, 0xCCFFFFFF);
            d.setText(today.minusDays(6 - i).getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.UK));
            col.addView(d);
            bars.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bp.topMargin = h.px(10);
        first.addView(bars, bp);

        // Where: the apps.
        Map<String, Integer> apps = new HashMap<>();
        for (JSONObject d : week) {
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                String k = ViewingLog.live(p) ? ViewingLog.LIVE : p;
                apps.put(k, apps.getOrDefault(k, 0) + a.optInt(p));
            }
        }
        // Every app by name (TiviMate and Debrid Stream too), by how long it was open, where Android's record is readable.
        boolean byUse = !usedWeek.isEmpty();
        List<Map.Entry<String, Integer>> appList = new ArrayList<>((byUse ? usedWeek : apps).entrySet());
        appList.sort((x, y) -> y.getValue() - x.getValue());
        LinearLayout where = card(v, 676, 150, 560, 300);
        where.addView(label(byUse ? "WHERE · TIME IN EACH APP" : "WHERE"));
        int top = appList.isEmpty() ? 1 : appList.get(0).getValue();
        for (int i = 0; i < Math.min(6, appList.size()); i++) where.addView(appRow(appList.get(i).getKey(), appList.get(i).getValue(), top, i == 0));

        // When: evenings, 17:00 to 01:00, by day of the week.
        int[][] grid = new int[7][9];
        for (int i = 0; i < 7; i++) {
            JSONObject d = week.get(i);
            int row = today.minusDays(6 - i).getDayOfWeek().getValue() - 1;
            JSONArray hh = d.optJSONArray("h");
            for (int k = 0; k < 7; k++) grid[row][k] += hh == null ? 0 : hh.optInt(17 + k);
            JSONArray next = i < 6 ? week.get(i + 1).optJSONArray("h") : null;
            for (int k = 0; k < 2; k++) grid[row][7 + k] += next == null ? 0 : next.optInt(k);
        }
        LinearLayout when = card(v, 1256, 150, 568, 300);
        int[] busy = busiest(grid);
        LinearLayout head = new LinearLayout(c);
        head.addView(label("WHEN"), new LinearLayout.LayoutParams(0, -2, 1f));
        if (busy != null) {
            TextView b = h.text(14, semi, YELLOW);
            b.setText("Busiest: " + DayOfWeek.of(busy[0] + 1).getDisplayName(TextStyle.FULL, Locale.UK) + " "
                    + String.format(Locale.UK, "%02d:00–%02d:00", (17 + busy[1]) % 24, (19 + busy[1]) % 24));
            head.addView(b);
        }
        when.addView(head);
        when.addView(heatmap(grid), new LinearLayout.LayoutParams(-1, -1));

        // What: the films and series.
        List<JSONObject> titles = titles(week);
        FrameLayout whatBox = new FrameLayout(c);
        whatBox.setBackground(cardGround());
        place(v, whatBox, 96, 470, 1728, 236);
        TextView wl = label("WHAT");
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(-2, -2);
        wlp.leftMargin = h.px(28);
        wlp.topMargin = h.px(20);
        whatBox.addView(wl, wlp);
        LinearLayout what = new LinearLayout(c);
        FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(-1, -2);
        wp.leftMargin = h.px(28);
        wp.topMargin = h.px(58);
        whatBox.addView(what, wp);
        for (int i = 0; i < Math.min(5, titles.size()); i++) what.addView(titleTile(titles.get(i), week, today), new LinearLayout.LayoutParams(h.px(316), -2));
        if (titles.isEmpty()) {
            TextView n = h.text(18, regular, 0xBFFFFFFF);
            n.setText("Live TV and clips only this week.");
            what.addView(n);
        }

        // Habits.
        LinearLayout chips = new LinearLayout(c);
        place(v, chips, 96, 730, 1728, 56);
        for (String s : habits(today, week, perDay, sum, titles)) chips.addView(chip(s), chipParams());
        TextView foot = h.text(15, regular, 0x99FFFFFF);
        foot.setText(byTime ? "Time in each app, counted by Android on this TV; the titles from what played. It never leaves the TV."
                : "Counted on this TV from what plays in your apps and on live TV. It never leaves the TV.");
        place(v, foot, 96, 1000, 1500, 30);
        return v;
    }

    /** The seven days' films and series, most watched first: {name, s, id, tv, eps}. */
    private List<JSONObject> titles(List<JSONObject> days) {
        Map<String, JSONObject> all = new LinkedHashMap<>();
        for (JSONObject d : days) {
            JSONObject t = d.optJSONObject("t");
            for (Iterator<String> it = t == null ? null : t.keys(); it != null && it.hasNext(); ) {
                String k = it.next();
                if (k.startsWith(ViewingLog.LIVE) || k.startsWith("app:")) continue;
                JSONObject one = t.optJSONObject(k);
                try {
                    JSONObject sum = all.get(k);
                    if (sum == null) all.put(k, sum = new JSONObject(one.toString()).put("eps", new JSONArray()).put("s", 0));
                    sum.put("s", sum.optInt("s") + one.optInt("s"));
                    JSONArray e = one.optJSONArray("e"), into = sum.getJSONArray("eps");
                    for (int i = 0; e != null && i < e.length(); i++) {
                        boolean has = false;
                        for (int j = 0; j < into.length(); j++) if (into.optString(j).equals(e.optString(i))) { has = true; break; }
                        if (!has) into.put(e.optString(i));
                    }
                } catch (Exception ignored) {
                    // that one left out
                }
            }
        }
        List<JSONObject> out = new ArrayList<>(all.values());
        out.sort((x, y) -> y.optInt("s") - x.optInt("s"));
        return out;
    }

    private View titleTile(JSONObject t, List<JSONObject> week, LocalDate today) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView poster = new ImageView(c);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(h.fill(10, 0xFF0B0F55));
        poster.setClipToOutline(true);
        poster.setOutlineProvider(h.round(10));
        Bitmap p = posters.get(t.optInt("id"));
        if (p != null) poster.setImageBitmap(p);
        row.addView(poster, new LinearLayout.LayoutParams(h.px(100), h.px(150)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = h.text(17, bold, Color.WHITE);
        name.setText(t.optString("n"));
        name.setMaxLines(2);
        name.setLineSpacing(0, 1.05f);
        col.addView(name);
        TextView time = h.text(22, heavy, YELLOW);
        time.setText(hm(t.optInt("s")));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = h.px(4);
        col.addView(time, tl);
        int eps = t.optJSONArray("eps") == null ? 0 : t.optJSONArray("eps").length();
        if (eps > 0) {
            TextView e = h.text(13, regular, 0xBFFFFFFF);
            // Three or more of them on one day: that day's binge ("5 episodes · Sat binge").
            String binge = null;
            int most = 0;
            for (int i = 0; i < week.size(); i++) {
                JSONObject day = week.get(i).optJSONObject("t");
                JSONObject one = day == null ? null : day.optJSONObject(keyOf(t));
                JSONArray es = one == null ? null : one.optJSONArray("e");
                int n = es == null ? 0 : es.length();
                if (n >= 3 && n > most) { most = n; binge = today.minusDays(week.size() - 1 - i).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK); }
            }
            e.setText(eps + (eps == 1 ? " episode" : " episodes") + (binge != null ? " · " + binge + " binge" : ""));
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-2, -2);
            el.topMargin = h.px(4);
            col.addView(e, el);
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(h.px(190), -2);
        cl.leftMargin = h.px(14);
        row.addView(col, cl);
        return row;
    }

    /** A film's or series' poster (TMDB), at poster size; null when unknown. Off the main thread. */
    Bitmap poster(int id, boolean tv) {
        if (id <= 0 || h.tmdbKey.isEmpty()) return null;
        for (WatchHistory.Entry e : WatchHistory.load(c)) {
            if (e.tmdbId == id && e.poster != null) return ScoresTicker.cachedLogo(c, e.poster.replace("/w342/", "/w185/"), h.px(200));
        }
        try {
            String p = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id + "?api_key=" + h.tmdbKey))
                    .optString("poster_path", "");
            return p.isEmpty() || "null".equals(p) ? null : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w185" + p, h.px(200));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> habits(LocalDate today, List<JSONObject> week, int[] perDay, int sum, List<JSONObject> titles) {
        List<String> out = new ArrayList<>();
        // Nights in a row with a quarter of an hour or more, up to today (or yesterday, early in the day).
        int streak = 0;
        AppUsage.Span use = usage14;
        int used = 0;
        for (int i = 0; use != null && i < use.days.length; i++) used += use.days[i];
        for (int i = 0; i < (used >= 60 ? 14 : 60); i++) {
            int s = used >= 60 ? use.days[13 - i] : total(ViewingLog.day(c, today.minusDays(i)));
            if (s >= 15 * 60) streak++;
            else if (i > 0) break;
        }
        if (streak >= 2) out.add("🔥  " + streak + " nights in a row");
        int late = 0;
        for (int i = 0; i < 7; i++) {
            JSONArray hh = week.get(i).optJSONArray("h");
            for (int k = 21; k < 24; k++) late += hh == null ? 0 : hh.optInt(k);
            for (int k = 0; k < 3; k++) late += hh == null ? 0 : hh.optInt(k);
        }
        if (sum > 0) out.add("☾  " + Math.round(late * 100f / sum) + "% after 21:00");
        int best = 0;
        for (int i = 1; i < 7; i++) if (perDay[i] > perDay[best]) best = i;
        out.add("★  Biggest day: " + today.minusDays(6 - best).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + ", " + hm(perDay[best]));
        int live = 0;
        for (JSONObject d : week) {
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                if (ViewingLog.live(p)) live += a.optInt(p);
            }
        }
        if (live > 0) out.add("📺  Live TV and sport: " + hm(live));
        // The kind of thing watched most (TMDB's genres of the week's top titles, by time), when it is a good share.
        Map<String, Integer> kinds = new HashMap<>();
        for (JSONObject t : titles) {
            List<String> g = genres.get(t.optInt("id"));
            if (g == null) continue;
            java.util.Set<String> once = new java.util.HashSet<>();
            for (String k : g) {
                String group = group(k);
                if (group != null && once.add(group)) kinds.put(group, kinds.getOrDefault(group, 0) + t.optInt("s"));
            }
        }
        String top = null;
        int topS = 0;
        for (Map.Entry<String, Integer> e : kinds.entrySet()) if (e.getValue() > topS) { top = e.getKey(); topS = e.getValue(); }
        int pct = sum > 0 ? Math.round(topS * 100f / sum) : 0;
        if (top != null && pct >= 30) {
            out.add(Math.min(2, out.size()), "★  " + top.replace(" and ", " & ") + ": " + (pct >= 45 && pct <= 55 ? "half of it" : pct + "% of it"));
            // Five chips run past the screen's edge: the biggest day makes way.
            if (out.size() > 4) for (int i = 0; i < out.size(); i++) if (out.get(i).startsWith("★  Biggest day")) { out.remove(i); break; }
        }
        return out;
    }

    // ---- pieces ------------------------------------------------------------------------------------------------

    private LinearLayout card(FrameLayout into, int x, int y, int w, int hgt) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(h.px(28), h.px(20), h.px(28), h.px(18));
        v.setBackground(cardGround());
        place(into, v, x, y, w, hgt);
        return v;
    }

    private GradientDrawable cardGround() {
        GradientDrawable g = h.fill(24, CARD);
        g.setStroke(Math.max(1, h.px(1)), EDGE);
        return g;
    }

    private TextView label(String s) {
        TextView t = h.text(15, bold, 0xCCFFFFFF);
        t.setText(s);
        t.setLetterSpacing(0.16f);
        return t;
    }

    private View appRow(String pkg, int secs, int top, boolean first) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, h.px(5), 0, h.px(5));
        FrameLayout mark = new FrameLayout(c);
        View logo = appMark(pkg);
        mark.addView(logo, new FrameLayout.LayoutParams(-2, h.px(26), Gravity.CENTER_VERTICAL));
        row.addView(mark, new LinearLayout.LayoutParams(h.px(150), h.px(30)));
        FrameLayout track = new FrameLayout(c);
        track.setBackground(h.fill(7, 0x24FFFFFF));
        View fill = new View(c);
        fill.setBackground(h.fill(7, first ? brand(pkg) : 0xBFFFFFFF));
        track.addView(fill, new FrameLayout.LayoutParams(Math.max(h.px(6), Math.round(h.px(230) * secs / (float) top)), -1));
        row.addView(track, new LinearLayout.LayoutParams(h.px(230), h.px(14)));
        TextView t = h.text(17, semi, Color.WHITE);
        t.setText(hm(secs));
        t.setGravity(Gravity.END);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    /** The most watched app's bar in the service's own colour (Netflix red, as the mock-up has it); else yellow. */
    private static int brand(String pkg) {
        switch (pkg) {
            case "com.netflix.ninja": return 0xFFE50914;
            case "com.amazon.amazonvideo.livingroom": return 0xFF00A8E1;
            case "com.disney.disneyplus": return 0xFF1F6BFF;
            case "com.google.android.youtube.tv": return 0xFFFF0033;
            default: return YELLOW;
        }
    }

    /** An app's mark: the service's own logo (Netflix, Prime Video …), LIVE TV, else its icon and name. */
    View appMark(String pkg) {
        if (ViewingLog.LIVE.equals(pkg)) {
            TextView t = h.text(13, semi, Color.WHITE);
            t.setText("LIVE TV");
            t.setLetterSpacing(0.08f);
            t.setPadding(h.px(8), h.px(3), h.px(8), h.px(3));
            t.setBackground(h.fill(5, 0xFFE8112D));
            return t;
        }
        for (String pid : PROVIDERS) {
            if (pkg.equals(HomeV2.serviceApp(pid)) && HomeV2.serviceLogo(pid) != 0) {
                ImageView i = new ImageView(c);
                i.setImageResource(HomeV2.serviceLogo(pid));
                i.setAdjustViewBounds(true);
                i.setScaleType(ImageView.ScaleType.FIT_START);
                return i;
            }
        }
        // The app's full logo from its TV banner (TiviMate, YouTube, Debrid Stream …), as Netflix's is shown.
        Object[] full = AppLogos.logo(c, pkg);
        if (full != null) {
            ImageView i = new ImageView(c);
            i.setImageBitmap((Bitmap) full[0]);
            i.setAdjustViewBounds(true);
            i.setScaleType(ImageView.ScaleType.FIT_START);
            if ((Boolean) full[1]) {
                // A banner on a picture, as a small card.
                i.setClipToOutline(true);
                i.setOutlineProvider(h.round(6));
            } else {
                Bitmap b = (Bitmap) full[0];
                if (b.getWidth() < b.getHeight() * 2.6f) {
                    // A compact logo (the mark over the name, as Debrid Stream's): a third larger than a wordmark,
                    // from its left end, so its name can be read too.
                    i.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, orr, ob) -> { v.setPivotX(0); v.setPivotY((bt - t) / 2f); });
                    i.setScaleX(1.35f);
                    i.setScaleY(1.35f);
                }
            }
            return i;
        }
        LinearLayout v = new LinearLayout(c);
        v.setGravity(Gravity.CENTER_VERTICAL);
        PackageManager pm = c.getPackageManager();
        try {
            Drawable icon = pm.getApplicationIcon(pkg);
            ImageView i = new ImageView(c);
            i.setImageDrawable(icon);
            v.addView(i, new LinearLayout.LayoutParams(h.px(24), h.px(24)));
        } catch (Exception ignored) {
            // no icon
        }
        TextView t = h.text(15, semi, Color.WHITE);
        try {
            CharSequence name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            // Debrid Stream calls itself "DebridStream".
            t.setText(ReleaseAlerts.DEBRID.equals(pkg) ? "Debrid Stream" : name);
        } catch (Exception e) {
            t.setText(pkg);
        }
        t.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = h.px(8);
        v.addView(t, tl);
        return v;
    }

    private View heatmap(int[][] grid) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        int most = 1;
        for (int[] r : grid) for (int x : r) most = Math.max(most, x);
        for (int r = 0; r < 7; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView d = h.text(13, bold, 0xBFFFFFFF);
            d.setText(DayOfWeek.of(r + 1).getDisplayName(TextStyle.SHORT, Locale.UK));
            row.addView(d, new LinearLayout.LayoutParams(h.px(40), -2));
            for (int k = 0; k < 9; k++) {
                float f = grid[r][k] / (float) most;
                View cell = new View(c);
                int colour = f <= 0.01f ? 0x12FFFFFF : f < 0.25f ? 0x33FFFFFF : f < 0.5f ? 0x73FFD04D : f < 0.75f ? 0xBFFFD04D : YELLOW;
                cell.setBackground(h.fill(5, colour));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, h.px(22), 1f);
                cl.leftMargin = h.px(5);
                row.addView(cell, cl);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = h.px(r == 0 ? 12 : 5);
            v.addView(row, rl);
        }
        LinearLayout hours = new LinearLayout(c);
        View pad = new View(c);
        hours.addView(pad, new LinearLayout.LayoutParams(h.px(40), 1));
        for (int k = 0; k < 9; k++) {
            TextView t = h.text(12, regular, 0xA6FFFFFF);
            t.setText(String.format(Locale.UK, "%02d", (17 + k) % 24));
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = h.px(5);
            hours.addView(t, tl);
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = h.px(6);
        v.addView(hours, hl);
        return v;
    }

    /** {day row, first hour column} of the busiest two hours; null when nothing. */
    private static int[] busiest(int[][] grid) {
        int best = 0;
        int[] at = null;
        for (int r = 0; r < 7; r++) for (int k = 0; k < 8; k++) {
            int s = grid[r][k] + grid[r][k + 1];
            if (s > best) { best = s; at = new int[]{r, k}; }
        }
        return at;
    }

    TextView chip(String s) {
        TextView t = h.text(18, semi, Color.WHITE);
        // The sign before the words in yellow, as the mock-up has it ("☾  64% after 21:00").
        int gap = s.indexOf("  ");
        if (gap > 0 && gap <= 3) {
            android.text.SpannableString sp = new android.text.SpannableString(s);
            sp.setSpan(new android.text.style.ForegroundColorSpan(YELLOW), 0, gap, 0);
            t.setText(sp);
        } else {
            t.setText(s);
        }
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(h.px(20), 0, h.px(20), 0);
        GradientDrawable g = h.fill(25, 0x1FFFFFFF);
        g.setStroke(Math.max(1, h.px(1)), 0x38FFFFFF);
        t.setBackground(g);
        return t;
    }

    LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(50));
        lp.rightMargin = h.px(12);
        return lp;
    }

    void place(FrameLayout parent, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        parent.addView(v, p);
    }

    /** "14h 20m", "45m". */
    static String hm(int secs) {
        int m = Math.round(secs / 60f);
        return m >= 60 ? m / 60 + "h " + String.format(Locale.UK, "%02d", m % 60) + "m" : m + "m";
    }

    /** "3:05", "45m" for the bars. */
    private static String hmShort(int secs) {
        int m = Math.round(secs / 60f);
        return m >= 60 ? m / 60 + ":" + String.format(Locale.UK, "%02d", m % 60) : m + "m";
    }
}
