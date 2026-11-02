package com.gathin.tvhome;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * How long each app has been open on this TV, from Android's own record of it (usage access): every app with a way in
 * from a launcher, Debrid Stream, TiviMate and the players included, whether or not it tells Android what it is
 * playing. Your viewing's "Where" and the Your apps tab read it. Usage access is given once over the TV's own
 * debugging connection (LocalAdb), or with `appops set com.gathin.tvhome GET_USAGE_STATS allow` from a computer;
 * without it, Your viewing falls back to what played (ViewingLog). Read on the TV, never sent anywhere.
 */
final class AppUsage {
    private AppUsage() { }

    /** Left out: this launcher, the TV's settings and its own launchers. */
    private static final Set<String> SKIP = new HashSet<>(java.util.Arrays.asList(
            "com.android.tv.settings", "com.android.settings", "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx", "com.amazon.tv.launcher"));

    private static boolean asked;

    /** Whether the launcher may read the usage record. */
    static boolean allowed(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = android.os.Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : legacyCheck(ops, c);
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static int legacyCheck(AppOpsManager ops, Context c) {
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
    }

    /** The activity events read (their numbers, the same on every Android version this launcher runs on). */
    private static final int RESUMED = 1, PAUSED = 2, SCREEN_OFF = 16, STOPPED = 23, SHUTDOWN = 26;

    /** Usage access, given once through the TV's own debugging connection when it is not yet allowed. */
    static void ensure(Context c) {
        if (asked || allowed(c)) return;
        asked = true;
        LocalAdb.shell(c, "appops set " + c.getPackageName() + " GET_USAGE_STATS allow");
    }

    /** The last few days of use: each app's seconds, each day's (oldest first, today last), and each day's hours. */
    static final class Span {
        final Map<String, Integer> apps = new HashMap<>();
        final int[] days;
        final int[][] hours;
        /** Each day's seconds in each app. */
        final java.util.List<Map<String, Integer>> dayApps = new java.util.ArrayList<>();
        /** Each app's seconds in each hour of the day, all the days together. */
        final Map<String, int[]> appHours = new HashMap<>();
        /**
         * Each app's stretches in front (each time it was opened and used for a minute or more), its longest
         * (seconds), when that began, and when it was last in front.
         */
        final Map<String, Integer> opens = new HashMap<>(), longest = new HashMap<>();
        final Map<String, Long> longestAt = new HashMap<>(), last = new HashMap<>();

        Span(int n) {
            days = new int[n];
            hours = new int[n][24];
            for (int i = 0; i < n; i++) dayApps.add(new HashMap<>());
        }
    }

    /**
     * Seconds each app was in front from the start of the day {@code days - 1} days ago until now, from the activity
     * events (exact, kept by Android for about a week and a half). Empty without usage access.
     */
    static Map<String, Integer> recent(Context c, int days) {
        return span(c, days).apps;
    }

    /** As recent, with the time split by day and by hour (Your viewing's bars and its evenings' heat map). */
    static Span span(Context c, int days) {
        Span out = new Span(days);
        if (!allowed(c)) return out;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate first = LocalDate.now().minusDays(days - 1);
        long end = System.currentTimeMillis();
        long start = first.atStartOfDay(zone).toInstant().toEpochMilli();
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents ev;
        try {
            ev = usm.queryEvents(start, end);
        } catch (RuntimeException e) {
            return out;
        }
        // The stretches each app was in front: {package, from, to}.
        java.util.List<Object[]> runs = new java.util.ArrayList<>();
        Map<String, Long> since = new HashMap<>();
        UsageEvents.Event e = new UsageEvents.Event();
        String front = null;
        while (ev != null && ev.hasNextEvent()) {
            ev.getNextEvent(e);
            String p = e.getPackageName();
            int type = e.getEventType();
            long at = e.getTimeStamp();
            if (type == RESUMED) {
                // Another app in front: the one before it stops counting (its pause can come just after).
                if (front != null && !front.equals(p)) stop(runs, since, front, at);
                if (!since.containsKey(p)) since.put(p, at);
                front = p;
            } else if (type == SCREEN_OFF || type == SHUTDOWN) {
                // The TV went to sleep: everything stops counting.
                for (String q : new java.util.ArrayList<>(since.keySet())) stop(runs, since, q, at);
                front = null;
            } else if (type == PAUSED || type == STOPPED) {
                stop(runs, since, p, at);
                if (p.equals(front)) front = null;
            }
        }
        // Still open now.
        for (String q : new java.util.ArrayList<>(since.keySet())) stop(runs, since, q, end);
        Map<String, Integer> raw = new HashMap<>();
        for (Object[] r : runs) raw.put((String) r[0], raw.getOrDefault((String) r[0], 0) + (int) (((Long) r[2] - (Long) r[1]) / 1000));
        out.apps.putAll(keep(c, raw));
        // Each kept stretch split at the hours it crosses.
        for (Object[] r : runs) {
            String pkg = (String) r[0];
            if (!out.apps.containsKey(pkg)) continue;
            long t = (Long) r[1], to = (Long) r[2];
            int secsAll = (int) ((to - t) / 1000);
            if (secsAll >= 60) out.opens.merge(pkg, 1, Integer::sum);
            if (secsAll > out.longest.getOrDefault(pkg, 0)) { out.longest.put(pkg, secsAll); out.longestAt.put(pkg, t); }
            if (to > out.last.getOrDefault(pkg, 0L)) out.last.put(pkg, to);
            while (t < to) {
                java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(t).atZone(zone);
                long next = z.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
                long cut = Math.min(to, next);
                int day = (int) java.time.temporal.ChronoUnit.DAYS.between(first, z.toLocalDate());
                if (day >= 0 && day < days) {
                    int secs = (int) ((cut - t) / 1000);
                    out.days[day] += secs;
                    out.hours[day][z.getHour()] += secs;
                    out.dayApps.get(day).merge(pkg, secs, Integer::sum);
                    out.appHours.computeIfAbsent(pkg, k -> new int[24])[z.getHour()] += secs;
                }
                t = cut;
            }
        }
        return out;
    }

    private static void stop(java.util.List<Object[]> runs, Map<String, Long> since, String p, long at) {
        Long from = since.remove(p);
        if (from == null || at <= from) return;
        // A single stretch longer than a day is a missed event, not viewing.
        runs.add(new Object[]{p, from, Math.min(at, from + 24 * 3_600_000L)});
    }

    /**
     * Seconds each app was in front over the last {@code days} days, from Android's daily, weekly and monthly totals
     * (whole periods, so the edges are approximate); never less than the exact count of the last week.
     */
    static Map<String, Integer> lastDays(Context c, int days) {
        Map<String, Integer> out = new HashMap<>();
        if (!allowed(c)) return out;
        long end = System.currentTimeMillis();
        long start = LocalDate.now().minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            Map<String, UsageStats> all = usm.queryAndAggregateUsageStats(start, end);
            for (Map.Entry<String, UsageStats> s : all.entrySet()) {
                long ms = s.getValue().getTotalTimeInForeground();
                if (ms > 0) out.put(s.getKey(), (int) Math.min(Integer.MAX_VALUE, ms / 1000));
            }
        } catch (RuntimeException ignored) {
            // the week's count below
        }
        for (Map.Entry<String, Integer> w : recent(c, Math.min(days, 7)).entrySet()) {
            if (out.getOrDefault(w.getKey(), 0) < w.getValue()) out.put(w.getKey(), w.getValue());
        }
        return keep(c, out);
    }

    /** When each app was last opened, from Android's totals over the last six months (an app not used lately too). */
    static Map<String, Long> lastUsed(Context c) {
        Map<String, Long> out = new HashMap<>();
        if (!allowed(c)) return out;
        long end = System.currentTimeMillis();
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            for (Map.Entry<String, UsageStats> s : usm.queryAndAggregateUsageStats(end - 183L * 24 * 3_600_000L, end).entrySet()) {
                // When it was last on screen (Android 10 on), not its last use of any kind (a service, a widget).
                long at = android.os.Build.VERSION.SDK_INT >= 29 ? s.getValue().getLastTimeVisible() : s.getValue().getLastTimeUsed();
                if (at > 0 && s.getValue().getTotalTimeInForeground() > 0) out.put(s.getKey(), at);
            }
        } catch (RuntimeException ignored) {
            // not known
        }
        return out;
    }

    /**
     * Only apps someone opens (with a way in from a launcher) and over a minute: not this launcher or another (Home
     * apps), and not a screensaver (its time is the TV left alone, not watched).
     */
    private static Map<String, Integer> keep(Context c, Map<String, Integer> in) {
        Map<String, Integer> out = new HashMap<>();
        PackageManager pm = c.getPackageManager();
        Set<String> skip = new HashSet<>(SKIP);
        try {
            for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME), 0)) skip.add(r.activityInfo.packageName);
            for (android.content.pm.ResolveInfo r : pm.queryIntentServices(new android.content.Intent("android.service.dreams.DreamService"), 0)) {
                skip.add(r.serviceInfo.packageName);
            }
        } catch (RuntimeException ignored) {
            // the fixed list alone
        }
        // The launcher's own screensaver lives in this package, which is left out anyway.
        for (Map.Entry<String, Integer> e : in.entrySet()) {
            String p = e.getKey();
            if (e.getValue() < 60 || p.equals(c.getPackageName()) || skip.contains(p)) continue;
            if (pm.getLeanbackLaunchIntentForPackage(p) == null && pm.getLaunchIntentForPackage(p) == null) continue;
            out.put(p, e.getValue());
        }
        return out;
    }
}
