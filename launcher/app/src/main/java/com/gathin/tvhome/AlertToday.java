package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Today's alerts (the Control Centre): every goal, red card and full time today in the games the pop-ups follow, game
 * by game, the newest game first, each marked with when its pop-up came up, or "Missed" and why (the TV was off, Home
 * wasn't open). Up and Down along them, OK shows that one's score card again (at the score it made), Back closes.
 * What came up is kept for the day in the app's settings ("alerts_today"); the games are the alert service's own.
 */
final class AlertToday {
    private AlertToday() { }

    private static final int YELLOW = 0xFFFFD04D, GREEN = 0xFF3DDC84, AMBER = 0xFFFFB238;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String PREFS = "alerts_today";
    /** What the Control Centre's tile says under its name ("2 missed today"); read when it opens. */
    private static volatile String summary = "Goals, red cards, full time";

    // ---- what came up today ------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String day = LocalDate.now().toString();
        if (!day.equals(p.getString("day", ""))) p.edit().clear().putString("day", day).apply();
        return p;
    }

    private static synchronized void put(Context c, String field, String key, Object value) {
        if (c == null || key == null) return;
        SharedPreferences p = prefs(c);
        try {
            JSONObject o = new JSONObject(p.getString(field, "{}"));
            if (o.has(key) && "shown".equals(field)) return;   // the first time it came up
            o.put(key, value);
            p.edit().putString(field, o.toString()).apply();
        } catch (Exception ignored) {
            // a history only
        }
    }

    private static synchronized JSONObject read(Context c, String field) {
        try {
            return new JSONObject(prefs(c).getString(field, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** The event with this key came up as a pop-up (ScoreCard). */
    static void shown(Context c, String key) {
        put(c, "shown", key, System.currentTimeMillis());
    }

    /** Why the event with this key did not come up ("before the TV was on", "Home wasn't open"). */
    static void why(Context c, String key, String reason) {
        put(c, "why", key, reason);
    }

    /** A pop-up came up today for game g (a Home-only game is in the list only then). */
    static boolean anyShown(Context c, ScoresTicker.Game g) {
        JSONObject shown = read(c, "shown");
        java.util.Iterator<String> keys = shown.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (k.startsWith(g.id + "|") || k.startsWith("R|" + g.id + "|") || k.equals("FT|" + g.id)) return true;
        }
        return false;
    }

    static String summary() {
        return summary;
    }

    // ---- the panel ---------------------------------------------------------------------------------------------

    /** One line of the list: a goal, red card or full time, with its game and the score it made. */
    private static final class Event {
        ScoresTicker.Game g;
        String type;
        String[] ev;
        int[] at;
        String key;
    }

    private static AlertToday shown;
    private HomeV2 h;
    private FrameLayout overlay;
    private ScrollView scroll;
    private LinearLayout list;
    private final List<View> rows = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private int at;
    private Typeface heavy, semi, regular;

    static boolean isOpen() {
        return shown != null;
    }

    static boolean key(KeyEvent e) {
        return shown != null && shown.onKey(e);
    }

    static void close() {
        if (shown != null) shown.hide();
    }

    static void open(HomeV2 h) {
        close();
        AlertToday t = new AlertToday();
        t.h = h;
        shown = t;
        t.heavy = h.a.getResources().getFont(R.font.outfit_extrabold);
        t.semi = h.a.getResources().getFont(R.font.inter_semibold);
        t.regular = h.a.getResources().getFont(R.font.inter_regular);
        t.overlay = new FrameLayout(h.a);
        t.overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF10157E, 0xFF1B279F, 0xFF2838C0}));
        t.overlay.setClickable(true);
        TextView crumb = h.text(16, t.semi, YELLOW);
        crumb.setText("TODAY'S ALERTS");
        crumb.setLetterSpacing(0.25f);
        t.place(crumb, 96, 72, 600, 30);
        TextView loading = h.text(26, t.regular, 0xB3FFFFFF);
        loading.setText("Reading today's games…");
        t.place(loading, 96, 130, 1200, 40);
        h.root.addView(t.overlay, new FrameLayout.LayoutParams(-1, -1));
        // Out of the Control Centre's button, on a spring.
        Fluid.in(t.overlay, h.controlButton());
        Context c = h.a;
        IO.execute(() -> {
            List<ScoresTicker.Game> games = GoalWatchService.today(c);
            int px = h.px(56);
            List<Bitmap[]> crests = new ArrayList<>();
            for (ScoresTicker.Game g : games) {
                crests.add(new Bitmap[]{g.homeLogo == null ? null : ScoresTicker.cachedLogo(c, g.homeLogo, px),
                        g.awayLogo == null ? null : ScoresTicker.cachedLogo(c, g.awayLogo, px)});
            }
            JSONObject when = read(c, "shown"), why = read(c, "why");
            MAIN.post(() -> {
                if (shown != t) return;
                t.overlay.removeView(loading);
                t.build(games, crests, when, why);
            });
        });
    }

    private void hide() {
        if (shown == this) shown = null;
        View o = overlay;
        Fluid.out(o, h.controlButton(), () -> h.root.removeView(o));
        h.paint();
    }

    /** "45'+2'" as a number to sort by: 4502. */
    private static int minute(String m) {
        if (m == null) return 0;
        String[] parts = m.split("\\+");
        return digits(parts[0]) * 100 + (parts.length > 1 ? digits(parts[1]) : 0);
    }

    private static int digits(String s) {
        String d = s.replaceAll("[^0-9]", "");
        try { return d.isEmpty() ? 0 : Integer.parseInt(d); } catch (NumberFormatException e) { return 0; }
    }

    private void build(List<ScoresTicker.Game> games, List<Bitmap[]> crests, JSONObject when, JSONObject why) {
        Context c = h.a;
        int goals = 0, reds = 0, fts = 0, missed = 0;
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.UK);
        for (int gi = 0; gi < games.size(); gi++) {
            ScoresTicker.Game g = games.get(gi);
            List<Event> mine = new ArrayList<>();
            List<String[]> all = new ArrayList<>(g.goals);
            all.addAll(g.reds);
            all.sort((x, y) -> Integer.compare(minute(x[2]), minute(y[2])));
            int hs = 0, as = 0;
            for (String[] x : all) {
                Event e = new Event();
                e.g = g;
                e.ev = x;
                e.key = x[0];
                if (x[0].startsWith("R|")) {
                    e.type = "red";
                    e.at = new int[]{hs, as};
                } else {
                    e.type = "goal";
                    boolean forHome = "home".equals(x[3]) != "og".equals(x[4]);
                    if (forHome) hs++; else as++;
                    e.at = new int[]{hs, as};
                }
                mine.add(e);
            }
            if (g.state == 2) {
                Event e = new Event();
                e.g = g;
                e.type = "ft";
                e.ev = new String[]{"FT|" + g.id, "", "", "", "ft"};
                e.key = e.ev[0];
                mine.add(e);
            }
            // The game's line: both crests, the names and the score, and FT or the minute.
            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(crest(crests.get(gi)[0]), new LinearLayout.LayoutParams(h.px(44), h.px(44)));
            TextView names = h.text(28, heavy, Color.WHITE);
            names.setText((g.homeName != null ? g.homeName : g.home) + "  " + (g.homeScore == null ? "0" : g.homeScore)
                    + "–" + (g.awayScore == null ? "0" : g.awayScore) + "  " + (g.awayName != null ? g.awayName : g.away));
            names.setSingleLine(true);
            names.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
            nlp.leftMargin = h.px(14);
            nlp.rightMargin = h.px(14);
            head.addView(names, nlp);
            head.addView(crest(crests.get(gi)[1]), new LinearLayout.LayoutParams(h.px(44), h.px(44)));
            TextView state = h.text(18, semi, g.state == 0 ? 0xFF0A0D33 : 0xCCFFFFFF);
            state.setText(g.state == 0 ? "LIVE" : "FT");
            state.setLetterSpacing(0.12f);
            state.setPadding(h.px(12), h.px(3), h.px(12), h.px(3));
            GradientDrawable chip = new GradientDrawable();
            chip.setCornerRadius(h.px(8));
            chip.setColor(g.state == 0 ? GREEN : 0x26FFFFFF);
            state.setBackground(chip);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.leftMargin = h.px(18);
            head.addView(state, slp);
            TextView league = h.text(18, regular, 0x99FFFFFF);
            league.setText(g.league == null ? "" : "  " + g.league);
            head.addView(league);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, h.px(64));
            hlp.topMargin = gi == 0 ? 0 : h.px(28);
            list.addView(head, hlp);
            if (mine.isEmpty()) {
                TextView none = h.text(22, regular, 0x99FFFFFF);
                none.setText("No goals yet");
                none.setPadding(h.px(58), 0, 0, 0);
                list.addView(none, new LinearLayout.LayoutParams(-1, h.px(44)));
            }
            for (Event e : mine) {
                long t = when.optLong(e.key, 0);
                String reason = why.optString(e.key, "");
                boolean live = e.g.state == 0 && t == 0 && reason.isEmpty() && !"ft".equals(e.type);
                if ("goal".equals(e.type)) goals++; else if ("red".equals(e.type)) reds++; else fts++;
                if (t == 0 && !live) missed++;
                View row = row(e, t > 0 ? "Popped up " + clock.format(new Date(t)) : live ? "Just in" : reason.isEmpty() ? "Missed" : "Missed · " + reason,
                        t > 0 ? GREEN : live ? YELLOW : AMBER);
                rows.add(row);
                events.add(e);
                list.addView(row, new LinearLayout.LayoutParams(-1, h.px(76)));
            }
        }
        TextView title = h.text(42, heavy, Color.WHITE);
        title.setText(games.isEmpty() ? "No games today yet" : "Every goal, red card and full time today");
        place(title, 96, 112, 1700, 60);
        TextView sum = h.text(24, regular, 0xCCFFFFFF);
        List<String> bits = new ArrayList<>();
        if (goals > 0) bits.add(goals + (goals == 1 ? " goal" : " goals"));
        if (reds > 0) bits.add(reds + (reds == 1 ? " red card" : " red cards"));
        if (fts > 0) bits.add(fts + (fts == 1 ? " full time" : " full times"));
        if (missed > 0) bits.add(missed + " missed");
        sum.setText(games.isEmpty() ? "Goals, red cards and full times in the games the pop-ups follow will be here as they happen."
                : TextUtils.join("  ·  ", bits));
        place(sum, 96, 178, 1700, 36);
        summary = missed > 0 ? missed + " missed today" : goals + fts > 0 ? (goals + reds + fts) + " today" : "Goals, red cards, full time";
        place(scroll, 96, 240, 1240, 740);
        TextView hint = h.text(20, regular, 0x99FFFFFF);
        Keys.set(hint, events.isEmpty() ? "Back closes" : "OK shows the score card again  ·  Back closes");
        place(hint, 96, 1000, 1200, 32);
        at = 0;
        paint();
    }

    private ImageView crest(Bitmap b) {
        ImageView v = new ImageView(h.a);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (b != null) v.setImageBitmap(b);
        return v;
    }

    /** An event's row: the minute, its mark (a ball, a red card, a whistle), who and the score, and whether it came up. */
    private View row(Event e, String status, int statusColour) {
        Context c = h.a;
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(h.px(14), 0, h.px(18), 0);
        TextView min = h.text(24, heavy, Color.WHITE);
        min.setText("ft".equals(e.type) ? "FT" : e.ev[2]);
        min.setGravity(Gravity.CENTER);
        row.addView(min, new LinearLayout.LayoutParams(h.px(96), -1));
        View mark;
        if ("red".equals(e.type)) {
            View card = new View(c);
            GradientDrawable r = new GradientDrawable();
            r.setColor(0xFFE0243A);
            r.setCornerRadius(h.px(3));
            card.setBackground(r);
            card.setRotation(8f);
            mark = card;
        } else {
            TextView m = h.text(24, regular, Color.WHITE);
            m.setText("goal".equals(e.type) ? "⚽" : "🏁");
            m.setGravity(Gravity.CENTER);
            mark = m;
        }
        LinearLayout.LayoutParams mlp = "red".equals(e.type) ? new LinearLayout.LayoutParams(h.px(20), h.px(28))
                : new LinearLayout.LayoutParams(h.px(36), h.px(36));
        mlp.leftMargin = h.px(6);
        mlp.rightMargin = h.px(22);
        row.addView(mark, mlp);
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView who = h.text(26, semi, Color.WHITE);
        TextView under = h.text(19, regular, 0xB3FFFFFF);
        ScoresTicker.Game g = e.g;
        String home = g.homeName != null ? g.homeName : g.home, away = g.awayName != null ? g.awayName : g.away;
        if ("ft".equals(e.type)) {
            int hs = digits(g.homeScore == null ? "0" : g.homeScore), as = digits(g.awayScore == null ? "0" : g.awayScore);
            who.setText(hs > as ? home + " win" : as > hs ? away + " win" : "Draw");
            under.setText("Full time  ·  " + hs + "–" + as);
        } else {
            who.setText(e.ev[1].isEmpty() ? ("red".equals(e.type) ? "Red card" : "Goal") : e.ev[1]);
            boolean forHome = "home".equals(e.ev[3]);
            String club = forHome ? home : away;
            String what = "red".equals(e.type) ? "sent off" : "pen".equals(e.ev[4]) ? "penalty" : "og".equals(e.ev[4]) ? "own goal" : "goal";
            under.setText(club + "  ·  " + what + ("goal".equals(e.type) ? "  ·  " + e.at[0] + "–" + e.at[1] : ""));
        }
        who.setSingleLine(true);
        who.setEllipsize(TextUtils.TruncateAt.END);
        under.setSingleLine(true);
        words.addView(who);
        words.addView(under);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView tag = h.text(19, semi, statusColour);
        tag.setText(status);
        tag.setSingleLine(true);
        tag.setPadding(h.px(14), h.px(5), h.px(14), h.px(5));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(h.px(10));
        chip.setColor((statusColour & 0x00FFFFFF) | 0x24000000);
        tag.setBackground(chip);
        row.addView(tag, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private void paint() {
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            if (i == at) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(h.px(16));
                g.setColor(0x2EFFFFFF);
                g.setStroke(h.px(2), 0x80FFFFFF);
                r.setBackground(g);
            } else {
                r.setBackground(null);
            }
        }
        if (at < rows.size()) {
            View r = rows.get(at);
            r.post(() -> {
                int top = r.getTop(), bottom = r.getBottom(), y = scroll.getScrollY(), hgt = scroll.getHeight();
                // The game's own line above its first event stays in view with it.
                if (top - h.px(80) < y) scroll.smoothScrollTo(0, Math.max(0, top - h.px(80)));
                else if (bottom > y + hgt) scroll.smoothScrollTo(0, bottom - hgt + h.px(12));
            });
        }
    }

    private boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled() && at < events.size()) {
                Event ev = events.get(at);
                Context c = h.a.getApplicationContext();
                IO.execute(() -> GoalWatchService.replay(c, ev.g, ev.type, ev.ev, "ft".equals(ev.type) ? null : ev.at));
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && at < rows.size() - 1) { at++; paint(); }
        else if (code == KeyEvent.KEYCODE_DPAD_UP && at > 0) { at--; paint(); }
        return true;
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        overlay.addView(v, p);
    }
}
