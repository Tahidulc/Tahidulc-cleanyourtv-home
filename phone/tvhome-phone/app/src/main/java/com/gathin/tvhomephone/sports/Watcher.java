package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.App;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.gathin.tvhomephone.SyncStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The alerts' work, for AlertService (every 30 s while a followed game is on) and AlertWorker (every 15 minutes): read
 * the followed clubs' games, send what changed (Events), the line-ups before kick-off, VAR checks, the live table,
 * reminders, F1 and the rain (nothing from a competition switched off, or from F1 when it is); then plan the next
 * wake (an exact alarm fifteen minutes before the next kick-off, race or reminder, where the phone allows exact alarms).
 */
public final class Watcher {
    private Watcher() {}

    private static final long SCAN_MS = 30 * 60_000L, LIVE_MS = 20_000L;

    /** What a poll found: a followed game live, the next kick-off of one, and the live scores for the ongoing line. */
    public static final class State {
        public boolean live;
        public long nextKickoff;
        public String ongoing = "";
        public final List<Game> followed = new ArrayList<>();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("sports_watch", Context.MODE_PRIVATE);
    }

    /** Every competition with a followed club's game today or tomorrow, read again every half hour (all of them). */
    static synchronized Set<String> leagues(Context c, Set<String> teams) {
        SharedPreferences p = prefs(c);
        long at = p.getLong("scan_at", 0);
        String day = LocalDate.now().toString();
        if (System.currentTimeMillis() - at < SCAN_MS && day.equals(p.getString("scan_day", "")) && teams.toString().equals(p.getString("scan_teams", ""))) {
            return new LinkedHashSet<>(p.getStringSet("scan_leagues", new HashSet<>()));
        }
        Set<String> out = new LinkedHashSet<>();
        Set<String> fixtures = new HashSet<>();
        for (String[] l : Espn.LEAGUES) {
            for (int d = 0; d <= 1; d++) {
                for (Game g : Espn.scoreboard(l[0], LocalDate.now().plusDays(d), SCAN_MS)) {
                    if (!g.involves(teams)) continue;
                    if (d == 0) out.add(l[0]);
                    if (g.state != Game.POST) fixtures.add(String.valueOf(g.kickoff));
                    Clubs.know(c, g.homeId, g.homeName, g.homeLogo);
                    Clubs.know(c, g.awayId, g.awayName, g.awayLogo);
                }
            }
        }
        p.edit().putLong("scan_at", System.currentTimeMillis()).putString("scan_day", day).putString("scan_teams", teams.toString())
                .putStringSet("scan_leagues", out).putStringSet("fixtures", fixtures).apply();
        return out;
    }

    /** One poll from the 15-minute check: what changed is sent at once (it is late enough already). */
    public static State poll(Context c) {
        return poll(c, false);
    }

    /** Alerts held back (Kinds.delayMs) and not sent yet: the live watcher stays on until they have gone. */
    private static final java.util.concurrent.atomic.AtomicInteger HELD = new java.util.concurrent.atomic.AtomicInteger();

    public static boolean holding() {
        return HELD.get() > 0;
    }

    /**
     * One poll: the followed games now, what changed sent, the state for the service. live: from the live watcher,
     * which holds a game's moments back as long as the settings say (as the TV does), so the phone doesn't tell of a
     * goal before the picture shows it. Off the main thread.
     */
    public static synchronized State poll(Context c, boolean live) {
        State s = new State();
        Set<String> teams = Clubs.starred(c);
        long now = System.currentTimeMillis();
        Map<String, ?> switches = Competitions.saved(c);
        for (String slug : leagues(c, teams)) {
            boolean allowed = Competitions.allowed(switches, slug);
            List<Game> all = Espn.scoreboard(slug, LocalDate.now(), LIVE_MS);
            for (Game g : all) {
                if (!g.involves(teams)) continue;
                if (!allowed) {
                    // Its competition is switched off: nothing sent, and no live watching for it. The game is still
                    // kept as it is now, so switching it back on later tells only what happens from then.
                    save(c, g);
                    continue;
                }
                s.followed.add(g);
                handle(c, g, all, now, live);
                if (g.state == Game.LIVE) {
                    s.live = true;
                    if (s.ongoing.length() > 0) s.ongoing += "   ";
                    s.ongoing += g.home + " " + g.score() + " " + g.away + " " + (g.halftime ? "HT" : g.detail);
                } else if (g.state == Game.PRE && g.kickoff > now && (s.nextKickoff == 0 || g.kickoff < s.nextKickoff)) {
                    s.nextKickoff = g.kickoff;
                }
            }
        }
        // "Kick-off in 2 min" for a followed or reminded game, and the home-screen widget drawn again.
        KickOff.check(c, s.followed, now);
        reminders(c, now);
        f1(c, now);
        ClubWidget.update(c, s);
        return s;
    }

    /** Longer than this since a game was last read (the phone off, or asleep for long): what happened is missed, not sent. */
    static final long STALE_MS = 25 * 60_000L;

    private static void handle(Context c, Game g, List<Game> league, long now, boolean live) {
        Game prev = snapshot(c, g.id);
        long readAt = prefs(c).getLong("t:" + g.id, 0);
        List<Events.Alert> alerts = Events.diff(prev, g);
        if (prev != null && readAt > 0 && now - readAt > STALE_MS) {
            // Back after a long gap: the goals, cards and full times meanwhile are kept as missed (in the app's alerts),
            // not sent now one after another.
            for (Events.Alert a : alerts) Notifier.missed(c, a);
            alerts.clear();
        }
        if (g.state == Game.PRE && g.kickoff - now <= 65 * 60_000L && g.kickoff - now > -5 * 60_000L
                && Kinds.on(c, "kickoff") && !AlertLog.sent(c, "L|" + g.id)) {
            Events.Alert l = Events.lineups(g, Espn.summary(g.slug, g.id, 60_000L));
            if (l != null) alerts.add(0, l);
        }
        if (g.state == Game.LIVE && Kinds.on(c, "var")) {
            List<Events.Alert> inc = Events.incidents(g, Espn.summary(g.slug, g.id, 25_000L), AlertLog.sentKeys(c));
            if (prev != null && readAt > 0 && now - readAt > STALE_MS) {
                for (Events.Alert a : inc) Notifier.missed(c, a);
            } else {
                alerts.addAll(inc);
            }
        }
        if (Kinds.on(c, "table") && isLeague(g.slug)) {
            for (Events.Alert a : new ArrayList<>(alerts)) {
                if (!"goal".equals(a.kind)) continue;
                Map<String, int[]> pos = LiveTable.positions(Espn.table(g.slug), league);
                for (String team : new String[]{g.homeId, g.awayId}) {
                    int[] p = pos.get(team);
                    if (p == null || p[0] == p[1] || !Clubs.starred(c).contains(team)) continue;
                    String club = team.equals(g.homeId) ? g.homeName : g.awayName;
                    alerts.add(new Events.Alert("table", "T|" + a.key + "|" + team,
                            "LIVE TABLE · " + club + (p[1] < p[0] ? " up to " : " down to ") + LiveTable.ordinal(p[1]),
                            "As it stands · was " + LiveTable.ordinal(p[0]) + " · " + g.homeName + " " + g.score() + " " + g.awayName, g));
                }
            }
        }
        long wait = live ? Kinds.delayMs(c) : 0;
        Context app = c.getApplicationContext();
        for (Events.Alert a : alerts) {
            if (wait > 0 && Kinds.waits(a.kind)) {
                HELD.incrementAndGet();
                App.main.postDelayed(() -> App.io.execute(() -> {
                    try {
                        Notifier.post(app, a);
                    } finally {
                        HELD.decrementAndGet();
                    }
                }), wait);
            } else {
                Notifier.post(c, a);
            }
        }
        save(c, g);
    }

    static boolean isLeague(String slug) {
        return slug != null && slug.matches("[a-z]{3}\\.[0-9]");
    }

    private static void reminders(Context c, long now) {
        if (!Kinds.on(c, "reminder")) return;
        for (JSONObject r : SyncStore.list(c, "reminder")) {
            long at = r.optLong("at");
            String title = r.optString("title");
            if (at == 0 || title.isEmpty()) continue;
            if (now >= at - Schedule.LEAD_MS && now < at + 5 * 60_000L) {
                Events.Alert a = new Events.Alert("reminder", "RM|" + title + "|" + at, "STARTS SOON · " + title,
                        "At " + java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + (r.optString("channel").isEmpty() ? "" : " on " + r.optString("channel")), null);
                a.slug = r.optString("slug", "");
                a.channel = r.optString("channel", "");
                Notifier.post(c, a);
            }
        }
    }

    private static void f1(Context c, long now) {
        if (!Competitions.on(c, Competitions.F1)) return;
        F1.Weekend w = F1.next();
        if (w != null && w.race > 0) {
            if (now >= w.race - Schedule.LEAD_MS && now < w.race && Kinds.on(c, "f1grid") && !AlertLog.sent(c, "F1G|" + w.round)) {
                String grid = F1.grid(w.round);
                if (grid != null) Notifier.post(c, new Events.Alert("f1grid", "F1G|" + w.round, "F1 GRID · " + w.name, grid, null));
            }
            if (now >= w.race && now < w.race + 15 * 60_000L) {
                Notifier.post(c, new Events.Alert("f1live", "F1L|" + w.round, "LIGHTS OUT · " + w.name, "The race is under way at " + w.circuit, null));
            }
        }
        if (Kinds.on(c, "f1result")) {
            String[] last = F1.lastResult();
            if (last != null && !AlertLog.sent(c, "F1R|" + last[0])) {
                try {
                    LocalDate raced = LocalDate.parse(last[3]);
                    if (!raced.isBefore(LocalDate.now().minusDays(1))) {
                        Notifier.post(c, new Events.Alert("f1result", "F1R|" + last[0], "F1 RESULT · " + last[1], last[2], null));
                    }
                } catch (RuntimeException ignored) {
                    // no date
                }
            }
        }
    }

    /** The rain alert: rain likely within the hour in Manchester, once a day. From the 15-minute check. */
    public static void weather(Context c) {
        if (!Kinds.on(c, "weather")) return;
        Object[] w = Weather.now();
        if (w == null) return;
        int[] chance = (int[]) w[2];
        if (chance.length >= 2 && chance[0] < 50 && chance[1] >= 70) {
            Notifier.post(c, new Events.Alert("weather", "W|" + LocalDate.now(), "RAIN ON THE WAY · Manchester",
                    chance[1] + "% chance within the hour · " + w[0] + "° now", null));
        }
    }

    // ---- snapshots ------------------------------------------------------------------------------------------------

    static Game snapshot(Context c, String id) {
        String s = prefs(c).getString("g:" + id, null);
        if (s == null) return null;
        try {
            return Game.from(new JSONObject(s));
        } catch (JSONException e) {
            return null;
        }
    }

    private static void save(Context c, Game g) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putString("g:" + g.id, g.json().toString()).putLong("t:" + g.id, System.currentTimeMillis());
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
            if (en.getKey().startsWith("t:") && en.getValue() instanceof Long && now - (Long) en.getValue() > 2L * 24 * 3600_000L) {
                e.remove(en.getKey()).remove("g:" + en.getKey().substring(2));
            }
        }
        e.apply();
    }

    // ---- the next wake --------------------------------------------------------------------------------------------

    /** Every time the alerts must wake for: followed kick-offs, reminders, the race (its grid, lights out, result). */
    static List<Long> events(Context c) {
        List<Long> out = new ArrayList<>();
        for (String k : prefs(c).getStringSet("fixtures", new HashSet<>())) {
            try {
                out.add(Long.parseLong(k));
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        for (JSONObject r : SyncStore.list(c, "reminder")) if (r.optLong("at") > 0) out.add(r.optLong("at"));
        F1.Weekend w = Competitions.on(c, Competitions.F1) ? F1.next() : null;
        if (w != null && w.race > 0) {
            out.add(w.race);
            out.add(w.race + Schedule.LEAD_MS);
            out.add(w.race + 2 * 3600_000L + Schedule.LEAD_MS);
        }
        return out;
    }

    /**
     * Plans the next wake: the live watcher now when a followed game is on or about to be, else an alarm (exact where the
     * phone allows it, which also lets the watcher start from the background) for the next one.
     */
    public static void plan(Context c, State s) {
        KickOff.plan(c);
        long now = System.currentTimeMillis();
        if (s != null && Schedule.watchNow(s.followed, now)) {
            AlertService.start(c);
            return;
        }
        long next = Schedule.next(events(c), now);
        if (next < 0) return;
        if (next <= now) next = now + 60_000L;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 41, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    /** Whether the phone lets this app set exact alarms (Settings offers to allow it when not). */
    public static boolean exactAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }
}
