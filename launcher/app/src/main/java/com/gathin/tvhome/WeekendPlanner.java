package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The weekend planner (PlannerPanel): this weekend's games of the followed competitions and clubs, each put on one of
 * the house's TVs (or none); the TV a game is on turns to its channel as it kicks off, when it is on. The plan is the
 * house's: kept here and sent to the other TVs (DeliveryLink /plan), the newest change of each game winning. Kept in
 * "planner"; the days and clashes are pure, for the tests.
 */
final class WeekendPlanner {
    private WeekendPlanner() { }

    /** A game put on a TV: the TV's name ("" none), when it kicks off, its channels, and when this was last changed. */
    static final class Plan {
        String game = "", label = "", tv = "";
        long at, changed;
        boolean done;
        final List<String> names = new ArrayList<>();

        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("game", game).put("label", label).put("tv", tv).put("at", at).put("changed", changed).put("done", done)
                        .put("names", new JSONArray(names));
            } catch (JSONException ignored) {
                // plain values
            }
            return o;
        }

        static Plan from(JSONObject o) {
            Plan p = new Plan();
            p.game = o.optString("game");
            p.label = o.optString("label");
            p.tv = o.optString("tv");
            p.at = o.optLong("at");
            p.changed = o.optLong("changed");
            p.done = o.optBoolean("done");
            JSONArray n = o.optJSONArray("names");
            for (int i = 0; n != null && i < n.length(); i++) p.names.add(n.optString(i));
            return p;
        }
    }

    /** A game takes this long, for clashes and the day's picture. */
    static final long GAME_MS = 2 * 3_600_000L;
    /** It is put on this long before kick-off. */
    static final long EARLY_MS = 90_000L;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("planner", Context.MODE_PRIVATE);
    }

    /** The plans by game, the ones over two days ago let go. */
    static synchronized Map<String, Plan> plans(Context c) {
        Map<String, Plan> out = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(prefs(c).getString("plans", "{}"));
            long old = System.currentTimeMillis() - 2 * 86_400_000L;
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                Plan p = Plan.from(o.getJSONObject(k));
                if (p.at > old) out.put(k, p);
            }
        } catch (JSONException ignored) {
            // none
        }
        return out;
    }

    private static synchronized void save(Context c, Map<String, Plan> plans) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, Plan> e : plans.entrySet()) o.put(e.getKey(), e.getValue().json());
        } catch (JSONException ignored) {
            return;
        }
        prefs(c).edit().putString("plans", o.toString()).apply();
    }

    /** This TV's name, as the plan says it. */
    static String me(Context c) {
        return TvLink.myName(c);
    }

    /** The TVs a game can go on, this one first: {name, address or null for this one}. */
    static List<String[]> tvs(Context c) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{me(c), null});
        for (String[] p : TvLink.known(c)) if (!p[0].equals(me(c))) out.add(p);
        return out;
    }

    /** A game put on a TV ("" for none), its channels as Watch on would tune them; sent to the other TVs. */
    static void assign(Context c, ScoresTicker.Game g, String tv) {
        Map<String, Plan> plans = plans(c);
        Plan p = plans.get(g.id);
        if (p == null) p = new Plan();
        p.game = g.id;
        p.label = MatchMode.label(g);
        p.at = g.kickoff;
        p.tv = tv == null ? "" : tv;
        p.done = false;
        p.changed = System.currentTimeMillis();
        p.names.clear();
        TvGuide.Watch w = TvGuide.watch(c.getResources().getStringArray(R.array.football_tv_channels), g);
        if (w != null) p.names.addAll(w.names.subList(0, Math.min(4, w.names.size())));
        plans.put(g.id, p);
        save(c, plans);
        share(c, plans);
        AlertLog.note(c, "planned: " + p.label + (p.tv.isEmpty() ? " on no TV" : " on " + p.tv));
    }

    /** The plan sent to each of the other TVs, on its own thread. */
    private static void share(Context c, Map<String, Plan> plans) {
        JSONObject all = new JSONObject();
        try {
            for (Map.Entry<String, Plan> e : plans.entrySet()) all.put(e.getKey(), e.getValue().json());
        } catch (JSONException ignored) {
            return;
        }
        String body = "{\"plans\":" + all + "}";
        List<String[]> peers = TvLink.known(c);
        if (peers.isEmpty()) return;
        new Thread(() -> {
            for (String[] p : peers) TvLink.http(p[1], "POST", "/plan", body, 1_500, 3_000);
        }, "planner-share").start();
    }

    /** The other TV's plan taken in (DeliveryLink): each game's newer change wins. */
    static void merge(Context c, JSONObject body) {
        JSONObject in = body == null ? null : body.optJSONObject("plans");
        if (in == null) return;
        Map<String, Plan> plans = plans(c);
        boolean changed = false;
        for (Iterator<String> it = in.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject o = in.optJSONObject(k);
            if (o == null) continue;
            Plan theirs = Plan.from(o), mine = plans.get(k);
            if (mine == null || theirs.changed > mine.changed) {
                // Done is this TV's own: a plan for here taken in again is not done here yet.
                if (mine != null && mine.tv.equals(theirs.tv)) theirs.done = mine.done;
                plans.put(k, theirs);
                changed = true;
            }
        }
        if (changed) save(c, plans);
    }

    /**
     * Looked at on each read of the scores (main thread): a game planned for this TV kicking off now is put on, once.
     */
    static void due(Context c) {
        long now = System.currentTimeMillis();
        String me = me(c);
        Map<String, Plan> plans = plans(c);
        for (Plan p : plans.values()) {
            if (p.done || !me.equals(p.tv) || !dueNow(p.at, now)) continue;
            p.done = true;
            save(c, plans);
            AlertLog.note(c, "planner: " + p.label + " on now");
            if (p.names.isEmpty()) return;
            GoalWatchService.onAppOpened(c, "ar.tvplayer.tv");
            ChannelOpen.open(c, ChannelOpen.TIVIMATE, new ArrayList<>(p.names), null);
            return;
        }
    }

    // ---- pure ------------------------------------------------------------------------------------------------------

    /** Pure: whether a game kicking off at at is to be put on now (from EARLY_MS before to half an hour in). */
    static boolean dueNow(long at, long now) {
        return at > 0 && now >= at - EARLY_MS && now < at + 30 * 60_000L;
    }

    /** Pure: the weekend's two days from today: Saturday and Sunday (on a Sunday, today and the next Saturday). */
    static List<LocalDate> weekend(LocalDate today) {
        List<LocalDate> out = new ArrayList<>();
        for (int i = 0; i < 8 && out.size() < 2; i++) {
            LocalDate d = today.plusDays(i);
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) out.add(d);
        }
        return out;
    }

    /** Pure: whether two games on the same TV overlap. */
    static boolean clash(Plan a, Plan b) {
        if (a == b || a.tv.isEmpty() || !a.tv.equals(b.tv)) return false;
        return Math.abs(a.at - b.at) < GAME_MS;
    }

    /** Pure: the next TV after tv in the list (by name; "" none comes after the last, the first after none). */
    static String next(List<String> names, String tv) {
        if (names.isEmpty()) return "";
        int i = tv == null || tv.isEmpty() ? -1 : names.indexOf(tv);
        return i + 1 >= names.size() ? "" : names.get(i + 1);
    }

    static ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
