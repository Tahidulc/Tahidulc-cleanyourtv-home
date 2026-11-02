package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Match predictions (the theme's predictions, the players its predict_players): before kick-off, Match Day's
 * Predict button on Home opens a card where each player picks a score (OK adds a goal, the remote's digits type it,
 * the arrows move between them); at full time a card says who was closest, and the family leaderboard keeps the
 * points: 3 for the exact score, 1 for the right result. Kept in the app's settings on this TV.
 */
final class Predictions {
    private Predictions() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static boolean on(Context c) {
        return Config.themeFlag(c, "predictions", false);
    }

    static List<String> players(Context c) {
        List<String> p = Config.themeStrings(c, "predict_players", new ArrayList<>());
        return p.isEmpty() ? Arrays.asList("Player 1", "Player 2", "Player 3") : p;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("predictions", Context.MODE_PRIVATE);
    }

    private static synchronized JSONObject games(Context c) {
        try {
            return new JSONObject(prefs(c).getString("games", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static synchronized void saveGames(Context c, JSONObject g) {
        // The last 30 games are plenty.
        if (g.length() > 30) {
            List<String> keys = new ArrayList<>();
            for (Iterator<String> it = g.keys(); it.hasNext(); ) keys.add(it.next());
            keys.sort((a, b) -> Long.compare(g.optJSONObject(a).optLong("k"), g.optJSONObject(b).optLong("k")));
            for (int i = 0; i < keys.size() - 30; i++) g.remove(keys.get(i));
        }
        prefs(c).edit().putString("games", g.toString()).apply();
    }

    static synchronized JSONObject points(Context c) {
        try {
            return new JSONObject(prefs(c).getString("points", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** "Dad 7 · Mum 5 · Ayaan 3", the leader first. */
    static String leaderboard(Context c) {
        JSONObject p = points(c);
        List<String> names = new ArrayList<>(players(c));
        names.sort((a, b) -> p.optInt(b) - p.optInt(a));
        StringBuilder s = new StringBuilder();
        for (String n : names) s.append(s.length() > 0 ? "  ·  " : "").append(n).append(' ').append(p.optInt(n));
        return s.toString();
    }

    /** Match Day's button: "Predict the score", or how many have picked. */
    static String label(Context c, ScoresTicker.Game g) {
        JSONObject one = games(c).optJSONObject(g.id);
        JSONObject picks = one == null ? null : one.optJSONObject("p");
        int n = picks == null ? 0 : picks.length();
        if (g.state != 1) return "Predictions";
        return n == 0 ? "Predict the score" : "Predictions (" + n + ")";
    }

    static boolean has(Context c, String id) {
        JSONObject one = games(c).optJSONObject(id);
        return one != null && one.optJSONObject("p") != null && one.optJSONObject("p").length() > 0;
    }

    private static void pick(Context c, ScoresTicker.Game g, String player, int home, int away) {
        JSONObject all = games(c);
        try {
            JSONObject one = all.optJSONObject(g.id);
            if (one == null) all.put(g.id, one = new JSONObject().put("h", g.home).put("a", g.away).put("k", g.kickoff).put("p", new JSONObject()));
            JSONObject picks = one.optJSONObject("p");
            if (picks == null) one.put("p", picks = new JSONObject());
            if (home < 0 && away < 0) picks.remove(player);
            else picks.put(player, new JSONArray().put(home).put(away));
            saveGames(c, all);
        } catch (Exception ignored) {
            // not kept
        }
    }

    private static int[] picked(Context c, String id, String player) {
        JSONObject one = games(c).optJSONObject(id);
        JSONArray p = one == null || one.optJSONObject("p") == null ? null : one.optJSONObject("p").optJSONArray(player);
        return p == null ? new int[]{-1, -1} : new int[]{p.optInt(0, -1), p.optInt(1, -1)};
    }

    private static int sign(int x) {
        return Integer.compare(x, 0);
    }

    /** The points for a pick: 3 exact, 1 the right result, 0 otherwise. */
    private static int score(int ph, int pa, int h, int a) {
        if (ph == h && pa == a) return 3;
        return sign(ph - pa) == sign(h - a) ? 1 : 0;
    }

    /**
     * A finished game (the alert service, each read): the picks for it settled once: the points added, and a card
     * saying who was closest and the leaderboard. On the main thread.
     */
    static void settle(Context c, ScoresTicker.Game g) {
        if (g == null || g.state != 2 || g.id == null) return;
        JSONObject all = games(c);
        JSONObject one = all.optJSONObject(g.id);
        if (one == null || one.optBoolean("done") || one.optJSONObject("p") == null || one.optJSONObject("p").length() == 0) return;
        int h, a;
        try {
            h = Integer.parseInt(g.homeScore);
            a = Integer.parseInt(g.awayScore);
        } catch (Exception e) {
            return;
        }
        JSONObject picks = one.optJSONObject("p");
        JSONObject pts = points(c);
        String best = null;
        int bestPts = -1, bestErr = Integer.MAX_VALUE;
        List<String> exact = new ArrayList<>();
        try {
            for (Iterator<String> it = picks.keys(); it.hasNext(); ) {
                String who = it.next();
                JSONArray p = picks.optJSONArray(who);
                if (p == null) continue;
                int ph = p.optInt(0), pa = p.optInt(1), s = score(ph, pa, h, a), err = Math.abs(ph - h) + Math.abs(pa - a);
                pts.put(who, pts.optInt(who) + s);
                if (s == 3) exact.add(who);
                if (s > bestPts || s == bestPts && err < bestErr) { best = who; bestPts = s; bestErr = err; }
            }
            one.put("done", true);
            saveGames(c, all);
            prefs(c).edit().putString("points", pts.toString()).apply();
        } catch (Exception e) {
            return;
        }
        JSONArray bp = picks.optJSONArray(best);
        ScoreCard.Data d = new ScoreCard.Data();
        d.dark = true;
        d.kind = "release";
        d.band = 0xFF10157E;
        d.bandDeep = 0xFF060928;
        d.labelColour = 0xFFFFD04D;
        d.label = "PREDICTIONS";
        String ft = g.home + " " + h + "–" + a + " " + g.away;
        d.headline = exact.size() > 1 ? String.join(" and ", exact) + " got it spot on! " + ft
                : exact.size() == 1 ? exact.get(0) + " got it spot on! " + ft
                : best + " was closest (" + (bp == null ? "" : bp.optInt(0) + "–" + bp.optInt(1)) + ")  ·  FT " + ft;
        d.line = "Leaderboard: " + leaderboard(c);
        AlertLog.note(c, "predictions settled: " + ft + ", " + d.headline);
        MAIN.post(() -> ScoreCard.show(c, d));
    }

    // ---- the card on Home -------------------------------------------------------------------------------------------

    private static Panel panel;

    static boolean isOpen() {
        return panel != null;
    }

    static boolean key(KeyEvent e) {
        return panel != null && panel.onKey(e);
    }

    static void close() {
        if (panel != null) panel.hide();
    }

    static void open(HomeV2 h, ScoresTicker.Game g) {
        close();
        panel = new Panel(h, g);
    }

    private static final class Panel {
        final HomeV2 h;
        final ScoresTicker.Game g;
        final FrameLayout overlay;
        final List<String> who;
        final int[][] values;
        final TextView[][] cells;
        final boolean locked;
        int row, col;

        Panel(HomeV2 h, ScoresTicker.Game g) {
            this.h = h;
            this.g = g;
            Context c = h.a;
            Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold), semi = c.getResources().getFont(R.font.inter_semibold),
                    regular = c.getResources().getFont(R.font.inter_regular);
            locked = g.state != 1;
            who = players(c);
            values = new int[who.size()][];
            cells = new TextView[who.size()][2];
            overlay = new FrameLayout(c);
            overlay.setBackgroundColor(0x99000000);
            overlay.setClickable(true);
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(h.px(44), h.px(34), h.px(44), h.px(34));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF10157E, 0xFF1B279F});
            bg.setCornerRadius(h.px(28));
            bg.setStroke(h.px(1), 0x40FFFFFF);
            box.setBackground(bg);
            TextView crumb = h.text(15, semi, 0xFFFFD04D);
            crumb.setText(locked ? "PREDICTIONS" : "PREDICT THE SCORE");
            crumb.setLetterSpacing(0.22f);
            box.addView(crumb);
            TextView title = h.text(34, heavy, Color.WHITE);
            String home = g.homeName != null ? g.homeName : g.home, away = g.awayName != null ? g.awayName : g.away;
            title.setText(home + " v " + away);
            title.setSingleLine(true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = h.px(6);
            tl.bottomMargin = h.px(18);
            box.addView(title, tl);
            for (int i = 0; i < who.size(); i++) {
                values[i] = picked(c, g.id, who.get(i));
                LinearLayout r = new LinearLayout(c);
                r.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = h.text(28, semi, Color.WHITE);
                name.setText(who.get(i));
                r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                for (int k = 0; k < 2; k++) {
                    TextView cell = h.text(40, heavy, Color.WHITE);
                    cell.setGravity(Gravity.CENTER);
                    r.addView(cell, new LinearLayout.LayoutParams(h.px(86), h.px(72)));
                    cells[i][k] = cell;
                    if (k == 0) {
                        TextView dash = h.text(34, heavy, 0x99FFFFFF);
                        dash.setText("–");
                        dash.setGravity(Gravity.CENTER);
                        r.addView(dash, new LinearLayout.LayoutParams(h.px(44), -2));
                    }
                }
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, h.px(84));
                box.addView(r, rl);
            }
            TextView hint = h.text(19, regular, 0xB3FFFFFF);
            hint.setText(locked ? (g.state == 0 ? "Kicked off: the picks are in" : "Full time") : "OK adds a goal  ·  the number keys type it  ·  Back when done");
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
            hl.topMargin = h.px(16);
            box.addView(hint, hl);
            TextView board = h.text(21, semi, 0xFFFFE27A);
            board.setText("Leaderboard:  " + leaderboard(c));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = h.px(10);
            box.addView(board, bl);
            overlay.addView(box, new FrameLayout.LayoutParams(h.px(760), -2, Gravity.CENTER));
            h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(200).start();
            paint();
        }

        void paint() {
            for (int i = 0; i < cells.length; i++) {
                for (int k = 0; k < 2; k++) {
                    TextView cell = cells[i][k];
                    int v = values[i][k];
                    cell.setText(v < 0 ? "·" : String.valueOf(v));
                    GradientDrawable g = new GradientDrawable();
                    g.setCornerRadius(h.px(16));
                    boolean focus = !locked && i == row && k == col;
                    g.setColor(focus ? 0xFFFFFFFF : 0x22FFFFFF);
                    cell.setBackground(g);
                    cell.setTextColor(focus ? 0xFF10157E : Color.WHITE);
                }
            }
        }

        void set(int v) {
            values[row][col] = v;
            // The other half of the score starts at nought once one is given.
            if (values[row][1 - col] < 0) values[row][1 - col] = 0;
            pick(h.a, g, who.get(row), values[row][0], values[row][1]);
            paint();
        }

        void hide() {
            if (panel == this) panel = null;
            overlay.animate().alpha(0f).setDuration(160).withEndAction(() -> h.root.removeView(overlay)).start();
            h.paint();
        }

        boolean onKey(KeyEvent e) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (e.getAction() == KeyEvent.ACTION_UP) hide();
                return true;
            }
            if (locked) return true;
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (ok) {
                if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                    int v = values[row][col];
                    set(v < 0 ? 0 : (v + 1) % 10);
                }
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                set(code - KeyEvent.KEYCODE_0);
                if (col == 0) col = 1;
                paint();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) col--;
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < 1) col++;
            else if (code == KeyEvent.KEYCODE_DPAD_UP && row > 0) row--;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN && row < who.size() - 1) row++;
            paint();
            return true;
        }
    }
}
