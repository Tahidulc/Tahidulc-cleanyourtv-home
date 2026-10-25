package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Goal alerts over another app (config.xml goal_watch_apps, TiviMate): started when Home opens one of those apps,
 * stopped when Home comes back. Reads the scores every POLL_MS and shows each new goal DELAY_MS after it was seen,
 * as a small glass box in the bottom-left corner for SHOW_MS. The box never takes focus or keys. Needs "display over
 * other apps" (appops SYSTEM_ALERT_WINDOW); without it the service stops at once.
 * <p>
 * A theme's config.json can add more: "f1_live_alerts" (qualifying, the sprint and the race as they run),
 * "news_alerts" (new headlines from config.xml news_alert_feeds), "frost_alerts" (a lighter, grained silver),
 * "team_alerts" (every game of the goal_alert_teams clubs), "incident_alerts" (penalties missed or saved, VAR),
 * "kickoff_reminders" (15 minutes before a goal_alert_leagues game), "tennis_alerts", "ufc_alerts", "boxing_alerts"
 * (BBC Sport's boxing headlines: ESPN has no boxing) and "alerts_everywhere" (Home and the screensaver too: the
 * service then runs for as long as the TV is on, and reads nothing while the screen is off).
 */
public class GoalWatchService extends Service {
    private static final long POLL_MS = 20_000L;
    private static final long DELAY_MS = 60_000L;
    private static final long SHOW_MS = 8_000L;
    private static final long NEWS_MS = 5 * 60_000L;
    private static final long REMIND_MS = 15 * 60_000L;
    /** The tennis feeds are large (all of a week's draws), so they are read less often than the others. */
    private static final long TENNIS_MS = 10 * 60_000L;
    private static final String BOXING_FEED = "https://feeds.bbci.co.uk/sport/boxing/rss.xml|BOXING";
    private static final long MAX_RUN_MS = 4 * 60 * 60_000L;
    private static final String CHANNEL = "goal_watch";
    /** The alerts' size: a pill PILL_DP tall, TEXT_SP text, CREST_DP crests and logos fetched at LOGO_DP. */
    private static final int PILL_DP = 36, CREST_DP = 20, LOGO_DP = 48;
    private static final float TEXT_SP = 13.5f, SMALL_SP = 11f;

    /** The screensaver is on (its own clock sits bottom-left, where the alerts go). */
    private boolean dreaming;
    private final android.content.BroadcastReceiver dreams = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dreaming = Intent.ACTION_DREAMING_STARTED.equals(i.getAction());
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_DREAMING_STARTED);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dreams, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dreams, f);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Set<String> seen = new HashSet<>();
    private boolean primed;
    private long startedAt;
    private View shown;
    /** The overlay window, added for one alert at a time and removed after it. */
    private android.widget.FrameLayout host;
    /**
     * Alerts waiting their turn: a goal {first line, game, home crest, away crest}, a ready line {parts} or a race
     * result {race}.
     */
    private final java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
    private final Runnable poll = this::poll;
    /** The newest read of each game, so an alert shown a minute later carries the score as it is by then. */
    private final Map<String, ScoresTicker.Game> latest = new HashMap<>();
    /** Each game's state at the last read (0 live, 1 upcoming, 2 finished), to see the moment one finishes. */
    private final Map<String, Integer> lastState = new HashMap<>();
    /** The F1 race's state at the last read, to see the moment it finishes. */
    private String f1Id, f1State;

    /** The theme's extras, read when the service starts. */
    private boolean f1Live, newsAlerts, frost;
    /** Each F1 session's state, leader and ten-lap mark at the last read. */
    private final Map<String, String> f1States = new HashMap<>(), f1Leaders = new HashMap<>();
    private final Map<String, Integer> f1Laps = new HashMap<>();
    private boolean f1Primed, newsPrimed;
    private long newsAt;
    private final Set<String> seenNews = new HashSet<>();
    private boolean teamAlerts, incidentAlerts, kickoffReminders, tennisAlerts, ufcAlerts, boxingAlerts, everywhere;
    /** Games already reminded of, and games whose incidents have been read once (the first read only learns). */
    private final Set<String> reminded = new HashSet<>(), incidentGames = new HashSet<>();
    /** Each tennis match's and UFC fight's state at the last read. */
    private final Map<String, String> otherStates = new HashMap<>();
    private boolean tennisPrimed, ufcPrimed, ufcLive;
    private long tennisAt, ufcAt;
    private int polls;

    /** A picture in a ready line, fetched (from the logo cache) before the line is queued; wide for a wordmark. */
    private static final class Pic {
        final String url;
        final boolean wide;
        Pic(String url) { this(url, false); }
        Pic(String url, boolean wide) { this.url = url; this.wide = wide; }
    }

    /** A fetched Pic. */
    private static final class Img {
        final android.graphics.Bitmap bitmap;
        final boolean wide;
        Img(android.graphics.Bitmap bitmap, boolean wide) { this.bitmap = bitmap; this.wide = wide; }
    }

    /** The dim tag at the end of a line, like the competition. */
    private static final class Small {
        final CharSequence text;
        Small(CharSequence text) { this.text = text; }
    }

    /** Home opened this package: watch for goals while it is in front, if it is one of goal_watch_apps. */
    static void onAppOpened(Context ctx, String pkg) {
        List<String> apps = Arrays.asList(ctx.getResources().getStringArray(R.array.goal_watch_apps));
        if (pkg == null || pkg.equals(ctx.getPackageName()) || !(apps.contains("*") || apps.contains(pkg))) return;
        if (!Settings.canDrawOverlays(ctx)) return;
        try {
            Intent i = new Intent(ctx, GoalWatchService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    /** Home is in front and the alerts are wanted everywhere (alerts_everywhere): keep them running. */
    static void startEverywhere(Context ctx) {
        if (!Settings.canDrawOverlays(ctx)) return;
        try {
            Intent i = new Intent(ctx, GoalWatchService.class).putExtra("everywhere", true);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    /** Home is back in front: no more alerts over other apps. */
    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, GoalWatchService.class));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Goal alerts", NotificationManager.IMPORTANCE_MIN));
        }
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Goal alerts").setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, n);
        if (startedAt == 0) {
            f1Live = Config.themeFlag(this, "f1_live_alerts", false);
            newsAlerts = Config.themeFlag(this, "news_alerts", false);
            frost = Config.themeFlag(this, "frost_alerts", false);
            teamAlerts = Config.themeFlag(this, "team_alerts", false);
            incidentAlerts = Config.themeFlag(this, "incident_alerts", false);
            kickoffReminders = Config.themeFlag(this, "kickoff_reminders", false);
            tennisAlerts = Config.themeFlag(this, "tennis_alerts", false);
            ufcAlerts = Config.themeFlag(this, "ufc_alerts", false);
            boxingAlerts = Config.themeFlag(this, "boxing_alerts", false);
            everywhere = Config.themeFlag(this, "alerts_everywhere", false);
        }
        if (intent != null && intent.hasExtra("test_more")) testMore(intent.getStringExtra("test_more"));
        if (intent != null && intent.hasExtra("test_goal")) {
            // adb only (the service needs DUMP from outside): one sample alert now, to check the look over an app.
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "LIV"; g.away = "BHA"; g.homeScore = "2"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/331.png";
            enqueue(g, new String[]{"test", "Mohamed Salah", "67'", "home", ""}, false);
        }
        if (intent != null && intent.hasExtra("test_ft")) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "MAN"; g.away = "BOU"; g.homeScore = "3"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/360.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/349.png";
            enqueue(g, new String[]{"test-ft", "", "", "", "ft"}, false);
        }
        if (intent != null && intent.hasExtra("test_f1")) {
            io.execute(() -> {
                F1Results.Race r = F1Results.load(intent.getStringExtra("test_f1"));
                if (r != null) main.post(() -> { queue.add(new Object[]{r}); showNext(); });
            });
        }
        if (intent != null && intent.hasExtra("test_f1_live")) {
            // The live session alerts, from a finished weekend's real order ("yyyyMMdd" of its race day).
            io.execute(() -> {
                F1Results.Weekend w = F1Results.weekend(intent.getStringExtra("test_f1_live"));
                if (w == null) return;
                main.post(() -> {
                    for (F1Results.Session s : w.sessions) {
                        if ("Qual".equals(s.abbr)) enqueueParts(sessionOver(w, s));
                    }
                    for (F1Results.Session s : w.sessions) {
                        if (!"Race".equals(s.abbr)) continue;
                        enqueueParts(sessionStart(w, s));
                        if (s.top.size() > 1) enqueueParts(leads(s, 1, 23));
                        enqueueParts(lapUpdate(s, 30));
                    }
                });
            });
        }
        if (intent != null && intent.hasExtra("test_news")) {
            io.execute(() -> {
                List<String[]> items = readNews(true, boxingAlerts);
                main.post(() -> {
                    Set<String> labels = new HashSet<>();
                    for (String[] item : items) if (labels.add(item[0])) enqueueParts(newsLine(item));
                });
            });
        }
        if (intent != null && intent.hasExtra("test_red")) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "UCL"; g.home = "ARS"; g.away = "PSG"; g.homeScore = "1"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/160.png";
            enqueue(g, new String[]{"test-red", "Declan Rice", "81'", "home", ""}, true);
        }
        if (startedAt == 0) {
            startedAt = System.currentTimeMillis();
            main.post(poll);
        }
        return START_NOT_STICKY;
    }

    private void poll() {
        if (!everywhere && System.currentTimeMillis() - startedAt > MAX_RUN_MS) { stopSelf(); return; }
        android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
        if (power != null && !power.isInteractive()) {
            // Screen off: read nothing, look again in a minute (the screensaver counts as on).
            main.postDelayed(poll, 60_000L);
            return;
        }
        long now = System.currentTimeMillis();
        // Only the competitions worth interrupting a programme for (goal_alert_leagues), and with team_alerts the
        // leagues and cups the goal_alert_teams clubs play in, for those clubs' games only.
        String[] alertLeagues = getResources().getStringArray(R.array.goal_alert_leagues);
        Set<String> alertTags = new HashSet<>();
        for (String l : alertLeagues) alertTags.add(l.contains("|") ? l.split("\\|", 2)[1].trim() : l.trim());
        Set<String> clubs = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.goal_alert_teams)));
        List<String> all = new ArrayList<>(Arrays.asList(alertLeagues));
        if (teamAlerts) all.addAll(Arrays.asList(getResources().getStringArray(R.array.team_alert_leagues)));
        String[] leagues = all.toArray(new String[0]);
        boolean incidentsNow = incidentAlerts && polls++ % 3 == 0;
        boolean tennisNow = tennisAlerts && now - tennisAt >= TENNIS_MS;
        boolean ufcNow = ufcAlerts && now - ufcAt >= (ufcLive ? 2 * 60_000L : 15 * 60_000L);
        if (tennisNow) tennisAt = now;
        if (ufcNow) ufcAt = now;
        // The live F1 alerts include the result panel, so the race alone is read only without them.
        boolean f1 = getResources().getBoolean(R.bool.f1_results_popup) && !f1Live;
        boolean news = (newsAlerts || boxingAlerts) && now - newsAt >= NEWS_MS;
        if (news) newsAt = now;
        io.execute(() -> {
            // One entry per game: a club's Champions League game is in both lists.
            Map<String, ScoresTicker.Game> byId = new java.util.LinkedHashMap<>();
            for (ScoresTicker.Game g : ScoresTicker.load(leagues)) {
                boolean wanted = alertTags.contains(g.league) || clubs.contains(g.homeId) || clubs.contains(g.awayId);
                if (wanted && g.id != null && !byId.containsKey(g.id)) byId.put(g.id, g);
            }
            List<ScoresTicker.Game> games = new ArrayList<>(byId.values());
            Map<String, List<String[]>> incidents = new HashMap<>();
            if (incidentsNow) for (ScoresTicker.Game g : games) if (g.state == 0) incidents.put(g.id, ScoresTicker.incidents(g));
            List<MoreSports.Result> tennis = tennisNow ? MoreSports.tennis() : null;
            List<MoreSports.Result> ufc = ufcNow ? MoreSports.ufc() : null;
            F1Results.Race race = f1 ? F1Results.load(null) : null;
            F1Results.Weekend weekend = f1Live ? F1Results.weekend(null) : null;
            List<String[]> headlines = news ? readNews(newsAlerts, boxingAlerts) : null;
            boolean f1On = false;
            for (F1Results.Session s : weekend == null ? new ArrayList<F1Results.Session>() : weekend.sessions) {
                f1On |= "in".equals(s.state);
            }
            final boolean racing = f1On;
            main.post(() -> {
                if (race != null) {
                    boolean finished = race.id.equals(f1Id) && "in".equals(f1State) && "post".equals(race.state);
                    f1Id = race.id;
                    f1State = race.state;
                    if (finished) main.postDelayed(() -> { queue.add(new Object[]{race}); showNext(); }, DELAY_MS);
                }
                if (weekend != null) f1Updates(weekend);
                if (headlines != null) newsUpdates(headlines);
                if (tennis != null) { otherUpdates(tennis, tennisPrimed); if (!tennis.isEmpty()) tennisPrimed = true; }
                if (ufc != null) {
                    ufcLive = false;
                    for (MoreSports.Result r : ufc) ufcLive |= "in".equals(r.state);
                    otherUpdates(ufc, ufcPrimed);
                    if (!ufc.isEmpty()) ufcPrimed = true;
                }
                for (ScoresTicker.Game g : games) {
                    if (kickoffReminders && alertTags.contains(g.league) && g.state == 1 && g.kickoff - now > 0
                            && g.kickoff - now <= REMIND_MS && reminded.add(g.id)) {
                        enqueueParts(kickoffLine(g));
                    }
                    List<String[]> found = incidents.get(g.id);
                    if (found == null) continue;
                    boolean first = incidentGames.add(g.id);
                    for (String[] inc : found) {
                        if (seen.add(inc[0]) && !first) main.postDelayed(() -> enqueueFootball(g, incidentHead(inc)), DELAY_MS);
                    }
                }
            });
            main.post(() -> {
                boolean live = racing;
                for (ScoresTicker.Game g : games) if (g.id != null) latest.put(g.id, g);
                for (ScoresTicker.Game g : games) {
                    live |= g.state == 0;
                    Integer before = g.id == null ? null : lastState.put(g.id, g.state);
                    if (primed && before != null && before == 0 && g.state == 2) {
                        main.postDelayed(() -> enqueue(g, new String[]{"FT|" + g.id, "", "", "", "ft"}, false), DELAY_MS);
                    }
                    for (String[] goal : g.goals) {
                        if (seen.add(goal[0]) && primed) main.postDelayed(() -> enqueue(g, goal, false), DELAY_MS);
                    }
                    for (String[] card : g.reds) {
                        if (seen.add(card[0]) && primed) main.postDelayed(() -> enqueue(g, card, true), DELAY_MS);
                    }
                }
                if (!games.isEmpty()) primed = true;
                main.postDelayed(poll, live ? POLL_MS : 5 * POLL_MS);
            });
        });
    }

    // ---- F1 ----------------------------------------------------------------------------------------

    /**
     * Compares the weekend with the last read: a session starting, a new leader, every ten laps of a race, and the
     * end (the race's top ten, else the top three). The first read only learns where things stand.
     */
    private void f1Updates(F1Results.Weekend w) {
        for (F1Results.Session s : w.sessions) {
            if (!s.counts()) continue;
            String before = f1States.put(s.id, s.state);
            String leader = s.top.isEmpty() ? null : s.top.get(0);
            String lastLeader = leader == null ? f1Leaders.get(s.id) : f1Leaders.put(s.id, leader);
            Integer lastMark = f1Laps.put(s.id, s.lap / 10);
            if (!f1Primed || before == null) continue;
            if ("pre".equals(before) && "in".equals(s.state)) {
                enqueueParts(sessionStart(w, s));
            } else if ("in".equals(before) && "post".equals(s.state)) {
                if ("Race".equals(s.abbr)) {
                    F1Results.Race r = new F1Results.Race();
                    r.id = s.id; r.name = w.name; r.state = s.state;
                    r.top.addAll(s.top);
                    r.logos.addAll(s.logos);
                    queue.add(new Object[]{r});
                    showNext();
                } else {
                    enqueueParts(sessionOver(w, s));
                }
            } else if ("in".equals(s.state)) {
                if (leader != null && lastLeader != null && !leader.equals(lastLeader)) enqueueParts(leads(s, 0, s.lap));
                if (s.isRace() && lastMark != null && s.lap / 10 > lastMark && s.lap > 0) enqueueParts(lapUpdate(s, s.lap));
            }
        }
        if (!w.sessions.isEmpty()) f1Primed = true;
    }

    /** "[F1] 🚦 LIGHTS OUT · United States GP" for a race, "⏱ QUALIFYING · ..." for the others. */
    private static List<Object> sessionStart(F1Results.Weekend w, F1Results.Session s) {
        List<Object> parts = f1Parts();
        parts.add(bold((s.isRace() ? "🚦  LIGHTS OUT" : "⏱  " + s.label) + (s.isRace() && !"Race".equals(s.abbr) ? " · SPRINT" : "")));
        parts.add(8);
        parts.add(w.name.replace("Grand Prix", "GP"));
        return parts;
    }

    /** "🏎 [logo] ANT leads · LAP 23"; in qualifying "⏱ [logo] ANT goes P1". */
    private static List<Object> leads(F1Results.Session s, int index, int lap) {
        List<Object> parts = f1Parts();
        parts.add(bold(s.isRace() ? "🏎" : "⏱"));
        parts.add(8);
        parts.add(new Pic(s.logos.get(index)));
        parts.add(4);
        parts.add(bold(F1Results.code(s.top.get(index))));
        parts.add(4);
        parts.add(s.isRace() ? "leads" : "goes P1");
        if (s.isRace() && lap > 0) { parts.add(8); parts.add(new Small("LAP " + lap)); }
        else { parts.add(8); parts.add(new Small(s.label)); }
        return parts;
    }

    /** "LAP 30 · 1 [logo] ANT  2 [logo] VER  3 [logo] HAM". */
    private static List<Object> lapUpdate(F1Results.Session s, int lap) {
        List<Object> parts = f1Parts();
        parts.add(bold("🏎  LAP " + lap));
        podium(parts, s);
        parts.add(8);
        parts.add(new Small(s.label));
        return parts;
    }

    /** "🏁 POLE · 1 ANT 2 VER 3 HAM" after qualifying; the sprint and sprint qualifying likewise. */
    private static List<Object> sessionOver(F1Results.Weekend w, F1Results.Session s) {
        List<Object> parts = f1Parts();
        String what = "Qual".equals(s.abbr) ? "POLE" : "SS".equals(s.abbr) ? "SPRINT POLE" : "SR".equals(s.abbr) ? "SPRINT" : s.label;
        parts.add(bold("🏁  " + what));
        podium(parts, s);
        parts.add(8);
        parts.add(new Small(w.name.replace("Grand Prix", "GP")));
        return parts;
    }

    /** Every F1 line opens with the F1 logo. */
    private static List<Object> f1Parts() {
        List<Object> parts = new ArrayList<>();
        parts.add(new Pic(F1Results.F1_LOGO, true));
        parts.add(8);
        return parts;
    }

    /** The top three: position, team logo, code. */
    private static void podium(List<Object> parts, F1Results.Session s) {
        int[] medal = {0xFFB8860B, 0xFF6B7280, 0xFF9A5B2E};
        for (int i = 0; i < Math.min(3, s.top.size()); i++) {
            parts.add(i == 0 ? 10 : 12);
            SpannableStringBuilder pos = new SpannableStringBuilder(String.valueOf(i + 1));
            pos.setSpan(new StyleSpan(Typeface.BOLD), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos.setSpan(new ForegroundColorSpan(medal[i]), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            parts.add(pos);
            parts.add(4);
            parts.add(new Pic(s.logos.get(i)));
            parts.add(3);
            parts.add(F1Results.code(s.top.get(i)));
        }
    }

    // ---- news --------------------------------------------------------------------------------------

    /** The newest few stories of each news_alert_feeds feed (and boxing's): {label, headline, link}. Off the main thread. */
    private List<String[]> readNews(boolean news, boolean boxing) {
        List<String[]> out = new ArrayList<>();
        List<String> feeds = new ArrayList<>();
        if (news) feeds.addAll(Arrays.asList(getResources().getStringArray(R.array.news_alert_feeds)));
        if (boxing) feeds.add(BOXING_FEED);
        for (String entry : feeds) {
            String[] parts = entry.split("\\|", 2);
            List<String[]> stories = NewsPanel.read(parts[0].trim());
            for (int i = 0; i < Math.min(5, stories.size()); i++) {
                String[] st = stories.get(i);
                out.add(new String[]{parts.length > 1 ? parts[1].trim() : "NEWS", st[0], st[2] == null ? st[0] : st[2]});
            }
        }
        return out;
    }

    /** A story new to the top five of its feed pops up; at most one per feed each read, the first read only learns. */
    private void newsUpdates(List<String[]> items) {
        Set<String> shownFeeds = new HashSet<>();
        for (String[] item : items) {
            if (seenNews.add(item[2]) && newsPrimed && shownFeeds.add(item[0])) enqueueParts(newsLine(item));
        }
        if (!items.isEmpty()) newsPrimed = true;
    }

    /** The BBC's blocks with NEWS or SPORT, then the headline; boxing's with a glove. */
    private List<Object> newsLine(String[] item) {
        float d = getResources().getDisplayMetrics().density;
        List<Object> parts = new ArrayList<>();
        boolean sport = item[0].contains("SPORT") || "BOXING".equals(item[0]);
        parts.add(Badges.bbc(d, sport ? "SPORT" : item[0].replace("BBC ", ""), sport ? 0xFF8A6100 : 0xFFB80000));
        if ("BOXING".equals(item[0])) { parts.add(8); parts.add(bold("🥊  BOXING")); }
        parts.add(8);
        parts.add(item[1]);
        return parts;
    }

    // ---- football extras, tennis and UFC -----------------------------------------------------------

    /** "⏰ 15 MIN · crest v crest · 15:00 · league". */
    private static List<Object> kickoffLine(ScoresTicker.Game g) {
        List<Object> parts = new ArrayList<>();
        long mins = Math.max(1, Math.round((g.kickoff - System.currentTimeMillis()) / 60_000.0));
        parts.add(bold("⏰  KICK-OFF IN " + mins + " MIN"));
        parts.add(10);
        parts.add(new Pic(g.homeLogo));
        parts.add(4);
        parts.add(bold(g.home));
        parts.add(6);
        parts.add("v");
        parts.add(6);
        parts.add(new Pic(g.awayLogo));
        parts.add(4);
        parts.add(bold(g.away));
        parts.add(8);
        parts.add(new Pic(lightLogo(g.leagueLogo)));
        return parts;
    }

    /** "❌ Pen missed · Salah 67'" before the game's crests and score. */
    private static CharSequence incidentHead(String[] inc) {
        SpannableStringBuilder s = new SpannableStringBuilder(inc[1]);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!inc[2].isEmpty()) {
            s.append("  ");
            int at = s.length();
            s.append(surname(inc[2]));
            s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!inc[3].isEmpty()) s.append("  ").append(inc[3]);
        return s;
    }

    /** ESPN's league logos come in a white version for dark backgrounds; the alerts' glass is light. */
    private static String lightLogo(String url) {
        return url == null ? null : url.replace("/500-dark/", "/500/");
    }

    /** A match or fight that ended (any state to post) or a final or main event that began (pre to in). */
    private void otherUpdates(List<MoreSports.Result> results, boolean primedYet) {
        for (MoreSports.Result r : results) {
            String before = otherStates.put(r.id, r.state);
            if (!primedYet || before == null || before.equals(r.state)) continue;
            if ("post".equals(r.state)) enqueueParts(resultLine(r));
            else if ("pre".equals(before) && "in".equals(r.state) && (r.mainEvent || "Final".equals(r.round))) {
                enqueueParts(startLine(r));
            }
        }
    }

    /** The tour's mark: the UFC logo, or ATP / WTA on their colours. */
    private Object mark(MoreSports.Result r) {
        float d = getResources().getDisplayMetrics().density;
        if ("UFC".equals(r.tour)) return new Pic(MoreSports.UFC_LOGO, true);
        return "WTA".equals(r.tour) ? Badges.tile(d, "WTA", 0xFF4B2683, 0xFFFFFFFF) : Badges.tile(d, "ATP", 0xFF0B2A5B, 0xFFFFFFFF);
    }

    /** "ATP 🎾 [flag] Sinner bt [flag] Alcaraz 6-4 7-6 · SF"; "UFC [flag] Steele def. [flag] Gugnon KO/TKO · R1 3:54". */
    private List<Object> resultLine(MoreSports.Result r) {
        boolean ufc = "UFC".equals(r.tour);
        List<Object> parts = new ArrayList<>();
        parts.add(mark(r));
        parts.add(8);
        parts.add(bold(ufc ? "🥊" : "🎾"));
        parts.add(8);
        parts.add(new Pic(r.winnerFlag));
        parts.add(4);
        parts.add(bold(r.winner));
        parts.add(5);
        parts.add(ufc ? "def." : "bt");
        parts.add(5);
        parts.add(new Pic(r.loserFlag));
        parts.add(4);
        parts.add(r.loser);
        parts.add(8);
        parts.add(bold(r.score));
        parts.add(8);
        parts.add(new Small(ufc ? (r.mainEvent ? "MAIN EVENT" : r.round.toUpperCase(java.util.Locale.ROOT)) : roundTag(r.round) + " · " + r.event));
        return parts;
    }

    /** "ATP 🎾 FINAL under way · [flag] Sinner v [flag] Alcaraz"; UFC's main event likewise. */
    private List<Object> startLine(MoreSports.Result r) {
        boolean ufc = "UFC".equals(r.tour);
        List<Object> parts = new ArrayList<>();
        parts.add(mark(r));
        parts.add(8);
        parts.add(bold(ufc ? "🥊  MAIN EVENT" : "🎾  FINAL"));
        parts.add(5);
        parts.add("under way");
        parts.add(8);
        parts.add(new Pic(r.winnerFlag));
        parts.add(4);
        parts.add(bold(r.winner));
        parts.add(5);
        parts.add("v");
        parts.add(5);
        parts.add(new Pic(r.loserFlag));
        parts.add(4);
        parts.add(bold(r.loser));
        parts.add(8);
        parts.add(new Small(r.event));
        return parts;
    }

    private static String roundTag(String round) {
        switch (round) {
            case "Quarterfinal": return "QF";
            case "Semifinal": return "SF";
            case "Final": return "FINAL";
            default: return "R16";
        }
    }

    /** adb only: sample alerts from real data ("team", "incident", "kickoff", "tennis", "ufc", "boxing" or "all"). */
    private void testMore(String what) {
        boolean all = "all".equals(what);
        if (all || "team".equals(what)) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "LALIGA"; g.home = "BAR"; g.away = "RMA"; g.homeScore = "1"; g.awayScore = "0";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/83.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/86.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/15.png";
            enqueue(g, new String[]{"test-team", "Lamine Yamal", "34'", "home", ""}, false);
        }
        if (all || "incident".equals(what)) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "UCL"; g.home = "PSG"; g.away = "MUN"; g.homeScore = "2"; g.awayScore = "2";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/160.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/132.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/2.png";
            enqueueFootball(g, incidentHead(new String[]{"t", "🧤  Pen saved", "Manuel Neuer", "88'"}));
        }
        if (all || "kickoff".equals(what)) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "ARS"; g.away = "CHE";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/363.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png";
            g.kickoff = System.currentTimeMillis() + REMIND_MS;
            enqueueParts(kickoffLine(g));
        }
        io.execute(() -> {
            List<MoreSports.Result> tennis = all || "tennis".equals(what) ? MoreSports.tennis() : new ArrayList<>();
            List<MoreSports.Result> ufc = all || "ufc".equals(what) ? MoreSports.ufc() : new ArrayList<>();
            List<String[]> boxing = all || "boxing".equals(what) ? readNews(false, true) : new ArrayList<>();
            main.post(() -> {
                for (MoreSports.Result r : tennis) if ("post".equals(r.state) && "Final".equals(r.round)) { enqueueParts(resultLine(r)); break; }
                for (MoreSports.Result r : ufc) if (r.mainEvent) { enqueueParts(startLine(r)); enqueueParts(resultLine(r)); break; }
                if (!boxing.isEmpty()) enqueueParts(newsLine(boxing.get(0)));
            });
        });
    }

    // ---- the alert ---------------------------------------------------------------------------------

    private void enqueue(ScoresTicker.Game g, String[] goal, boolean red) {
        enqueueFootball(g, headline(goal, red));
    }

    /** Fetches both crests and the competition's logo (cached on disk by the scores line), then queues the alert. */
    private void enqueueFootball(ScoresTicker.Game g, CharSequence head) {
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            android.graphics.Bitmap home = g.homeLogo == null ? null : ScoresTicker.cachedLogo(this, g.homeLogo, px);
            android.graphics.Bitmap away = g.awayLogo == null ? null : ScoresTicker.cachedLogo(this, g.awayLogo, px);
            String light = lightLogo(g.leagueLogo);
            android.graphics.Bitmap league = light == null ? null : ScoresTicker.cachedLogo(this, light, px);
            main.post(() -> {
                queue.add(new Object[]{head, g, home, away, league});
                showNext();
            });
        });
    }

    /** Fetches the line's pictures, then queues it. */
    private void enqueueParts(List<Object> parts) {
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            List<Object> ready = new ArrayList<>();
            for (Object o : parts) {
                if (o instanceof Pic) {
                    Pic pic = (Pic) o;
                    ready.add(new Img(pic.url == null ? null : ScoresTicker.cachedLogo(this, pic.url, px * (pic.wide ? 2 : 1)), pic.wide));
                }
                else ready.add(o);
            }
            main.post(() -> {
                queue.add(new Object[]{ready});
                showNext();
            });
        });
    }

    private static CharSequence bold(String text) {
        SpannableStringBuilder s = new SpannableStringBuilder(text);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static CharSequence headline(String[] goal, boolean red) {
        if ("ft".equals(goal[4])) {
            SpannableStringBuilder ft = new SpannableStringBuilder("🏁  FT");
            ft.setSpan(new StyleSpan(Typeface.BOLD), 0, ft.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ft;
        }
        SpannableStringBuilder s = new SpannableStringBuilder(red ? "🟥  " : "⚽  ");
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(red ? 0xFFC81E1E : 0xFF15803D), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int at = s.length();
        s.append(surname(goal[1]));
        s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append("  ").append(goal[2]);
        if (!goal[4].isEmpty()) s.append(goal[4].equals("pen") ? " (pen)" : " (og)");
        return s;
    }

    private void ensureHost(int widthDp, int heightDp) {
        if (host != null) return;
        float d = getResources().getDisplayMetrics().density;
        host = new android.widget.FrameLayout(this);
        // A fixed size, set once: a window that changes size over a playing video is reallocated and re-planned
        // by the display, which shows as a hitch; this one only ever changes what it draws.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(Math.round(widthDp * d), Math.round(heightDp * d),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = Math.round(32 * d);
        // Above the screensaver's clock and caption while it runs.
        lp.y = Math.round((dreaming ? 92 : 30) * d);
        try {
            getSystemService(WindowManager.class).addView(host, lp);
        } catch (Exception e) {
            host = null;
        }
    }

    /** "Mohamed Salah" to "Salah", "Virgil van Dijk" to "van Dijk": everything after the first name. */
    static String surname(String name) {
        String n = name == null ? "" : name.trim();
        int space = n.indexOf(' ');
        return space > 0 ? n.substring(space + 1) : n;
    }

    /**
     * The alerts' glass. Silver: a light metallic gradient, brighter at the top like brushed aluminium, under the
     * Home pills' rim and sheen. Frosted (frost_alerts): paler and milkier, a fine grain like etched glass, a bright
     * rim and a soft highlight over the top half.
     */
    private android.graphics.drawable.Drawable glass(float radius) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                frost ? new int[]{0xFFFBFCFE, 0xFFE6EAEF, 0xFFD3D9E1} : new int[]{0xFFE9ECF1, 0xFFB9BFC8});
        base.setCornerRadius(radius);
        if (!frost) {
            return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    base, getDrawable(R.drawable.glass_box).mutate()});
        }
        float d = getResources().getDisplayMetrics().density;
        float[] radii = new float[8];
        Arrays.fill(radii, radius);
        android.graphics.drawable.ShapeDrawable grain = new android.graphics.drawable.ShapeDrawable(
                new android.graphics.drawable.shapes.RoundRectShape(radii, null, null));
        grain.getPaint().setShader(new android.graphics.BitmapShader(grain(),
                android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT));
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80FFFFFF, 0x1AFFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(radius);
        GradientDrawable rim = new GradientDrawable();
        rim.setCornerRadius(radius);
        rim.setStroke(Math.max(1, Math.round(1 * d)), 0xF2FFFFFF);
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(radius);
        edge.setStroke(Math.max(1, Math.round(0.5f * d)), 0x33707884);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                base, grain, sheen, edge, rim});
    }

    private static android.graphics.Bitmap grainTile;

    /** A 64 px tile of faint light and dark specks, the frosted look's grain. */
    private static android.graphics.Bitmap grain() {
        if (grainTile != null) return grainTile;
        int size = 64;
        int[] px = new int[size * size];
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) {
            int a = r.nextInt(18);
            px[i] = r.nextBoolean() ? (a << 24) | 0xFFFFFF : ((a / 2) << 24) | 0x404650;
        }
        grainTile = android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888);
        return grainTile;
    }

    @SuppressWarnings("unchecked")
    private void showNext() {
        if (shown != null || queue.isEmpty()) return;
        Object[] next = queue.poll();
        if (next.length == 1 && next[0] instanceof F1Results.Race) {
            showRace((F1Results.Race) next[0]);
            return;
        }
        List<Object> parts;
        boolean wide;
        if (next.length == 1) {
            parts = (List<Object>) next[0];
            wide = true;
        } else {
            ScoresTicker.Game g = (ScoresTicker.Game) next[1];
            ScoresTicker.Game now = g.id == null ? null : latest.get(g.id);
            if (now != null) g = now;
            // Crests stand in for the team names: crest, score, crest.
            SpannableStringBuilder score = new SpannableStringBuilder(g.homeScore + "–" + g.awayScore);
            score.setSpan(new StyleSpan(Typeface.BOLD), 0, score.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // The competition's logo closes the line; its short name when ESPN has no logo.
            Object league = next.length > 4 && next[4] != null ? next[4] : new Small(g.league);
            parts = Arrays.asList(next[0], 10, next[2], 6, score, 6, next[3], 8, league);
            wide = false;
        }
        float d = getResources().getDisplayMetrics().density;
        // One slim line in the Home screen's frosted pill style, dark text on the silver glass.
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int h = Math.round(PILL_DP * d);
        box.setBackground(glass(h / 2f));
        int pad = Math.round(15 * d);
        box.setPadding(pad, 0, pad, 0);
        int crestPx = Math.round(CREST_DP * d);
        int gap = 0;
        for (Object part : parts) {
            if (part instanceof Integer) { gap = Math.round((Integer) part * d); continue; }
            if (part == null || part instanceof android.graphics.Bitmap) {
                box.addView(crest((android.graphics.Bitmap) part, d), sized(crestPx, gap));
            } else if (part instanceof Img) {
                Img img = (Img) part;
                LinearLayout.LayoutParams lp = sized(crestPx, gap);
                if (img.wide) lp.width = crestPx * 2;
                box.addView(crest(img.bitmap, d), lp);
            } else if (part instanceof android.graphics.drawable.Drawable) {
                android.graphics.drawable.Drawable mark = (android.graphics.drawable.Drawable) part;
                android.widget.ImageView v = new android.widget.ImageView(this);
                v.setImageDrawable(mark);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mark.getIntrinsicWidth(), mark.getIntrinsicHeight());
                lp.leftMargin = gap;
                box.addView(v, lp);
            } else if (part instanceof Small) {
                box.addView(text(((Small) part).text, SMALL_SP, 0xB31A1D24), gap(gap));
            } else {
                TextView t = text((CharSequence) part, TEXT_SP, 0xFF1A1D24);
                t.setMaxWidth(Math.round(700 * d));   // a long headline ends in an ellipsis
                t.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(t, gap(gap));
            }
            gap = 0;
        }
        box.setMinimumHeight(h);

        ensureHost(wide ? 1000 : 560, 50);
        if (host == null) return;
        host.addView(box, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h, Gravity.BOTTOM | Gravity.START));
        shown = box;
        // A small glass dot pops in at the corner, the pill then stretches out to the right with its round ends kept
        // (an animated rounded outline, clipped in hardware), and only then does the text fade in.
        final float radius = h / 2f;
        final float[] visible = {h};
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                // Anchored at the left: the pill grows out to the right from the dot.
                int right = Math.min(v.getWidth(), Math.round(visible[0]));
                o.setRoundRect(0, 0, right, v.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
        for (int c = 0; c < box.getChildCount(); c++) box.getChildAt(c).setAlpha(0f);
        box.setAlpha(0f);
        box.setScaleX(0.3f);
        box.setScaleY(0.3f);
        box.post(() -> {
            box.setPivotX(radius);
            box.setPivotY(box.getHeight() / 2f);
            box.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).withEndAction(() -> {
                android.animation.ValueAnimator grow = android.animation.ValueAnimator.ofFloat(h, box.getWidth());
                grow.setDuration(850);
                grow.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                grow.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); });
                grow.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        for (int c = 0; c < box.getChildCount(); c++) {
                            box.getChildAt(c).animate().alpha(1f).setDuration(300).setStartDelay(c * 40L).start();
                        }
                        box.postDelayed(() -> box.setLayerType(View.LAYER_TYPE_NONE, null), 600);
                    }
                });
                grow.start();
            }).start();
        });
        main.postDelayed(() -> {
            box.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            box.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(450)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
                host.removeView(box);
                // Remove the window itself too: even an empty one over the video can show as a faint box
                // where the TV processes the picture differently under it.
                try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
                host = null;
                shown = null;
                showNext();
            }).start();
        }, SHOW_MS + 1_400);
    }

    /**
     * The race result: the Grand Prix's name, then the top ten in two rows of five, podium numbers in gold, silver
     * and bronze, on the same glass as the football alerts. It stays a little longer than they do.
     */
    private void showRace(F1Results.Race race) {
        if (shown != null || race.top.isEmpty()) { showNext(); return; }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(glass(16 * d));
        int pad = Math.round(14 * d);
        panel.setPadding(pad, Math.round(9 * d), pad, Math.round(10 * d));
        SpannableStringBuilder head = new SpannableStringBuilder("🏁  " + race.name.toUpperCase() + "  ·  RESULT");
        head.setSpan(new StyleSpan(Typeface.BOLD), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        panel.addView(text(head, TEXT_SP, 0xFF1A1D24));
        int[] medal = {0xFFB8860B, 0xFF6B7280, 0xFF9A5B2E};
        List<android.widget.ImageView> logoViews = new ArrayList<>();
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = row * 5; i < Math.min(race.top.size(), row * 5 + 5); i++) {
                // Position, the team's logo, the surname.
                SpannableStringBuilder pos = new SpannableStringBuilder(String.valueOf(i + 1));
                pos.setSpan(new StyleSpan(Typeface.BOLD), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                line.addView(text(pos, TEXT_SP, i < 3 ? medal[i] : 0xA61A1D24), gap(i == row * 5 ? 0 : Math.round(16 * d)));
                android.widget.ImageView logo = crest(null, d);
                logo.setTag(i < race.logos.size() ? race.logos.get(i) : null);
                line.addView(logo, sized(Math.round(CREST_DP * d), Math.round(5 * d)));
                logoViews.add(logo);
                line.addView(text(race.top.get(i), TEXT_SP, 0xFF1A1D24), gap(Math.round(5 * d)));
            }
            if (line.getChildCount() == 0) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(5 * d);
            panel.addView(line, lp);
        }
        int px = Math.round(LOGO_DP * d);
        for (android.widget.ImageView v : logoViews) {
            String url = (String) v.getTag();
            if (url == null) continue;
            io.execute(() -> {
                android.graphics.Bitmap b = ScoresTicker.cachedLogo(this, url, px);
                if (b != null) main.post(() -> v.setImageBitmap(b));
            });
        }
        ensureHost(900, 124);
        if (host == null) return;
        host.addView(panel, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        shown = panel;
        panel.setAlpha(0f);
        panel.setTranslationY(14 * d);
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        panel.animate().alpha(1f).translationY(0f).setDuration(600)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(() -> panel.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        main.postDelayed(() -> {
            panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            panel.animate().alpha(0f).setDuration(450).withEndAction(() -> {
                host.removeView(panel);
                try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
                host = null;
                shown = null;
                showNext();
            }).start();
        }, 15_000);
    }

    private TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        return t;
    }

    private static LinearLayout.LayoutParams sized(int size, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = left;
        return lp;
    }

    private static LinearLayout.LayoutParams gap(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        return lp;
    }

    private android.widget.ImageView crest(android.graphics.Bitmap b, float d) {
        android.widget.ImageView v = new android.widget.ImageView(this);
        v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if (b != null) v.setImageBitmap(b);
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.round(18 * d), Math.round(18 * d)));
        return v;
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(dreams); } catch (Exception ignored) { }
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (host != null) {
            try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
            host = null;
            shown = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
