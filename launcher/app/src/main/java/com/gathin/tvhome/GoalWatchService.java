package com.gathin.tvhome;

import org.json.JSONObject;

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
    private static final int PILL_DP = 32, CREST_DP = 18, LOGO_DP = 48;
    private static final float TEXT_SP = 12.5f, SMALL_SP = 10f;
    /** The F1 result card over an app, smaller than the other alerts: the whole field takes less of the picture. */
    private static final float RACE_K = 0.8f;
    /** Larger words and crests for the alert being built: a goal on v2's Home (1 otherwise). */
    private float boost = 1f;
    /** The colour of the alert being built, for its glass on v2's Home (0: plain, as football's). */
    private int laneTint;

    /** A line's colour on Home's glass, put first among its parts. */
    private static final class Tint {
        final int colour;
        Tint(int colour) { this.colour = colour; }
    }
    private static final long WEATHER_MS = 15 * 60_000L;

    /**
     * A short sound for each kind of alert (a theme's "alert_sounds"), quiet, and played as a UI sound without asking
     * for audio focus, so whatever is playing carries on at its own volume.
     */
    private android.media.SoundPool sounds;
    private final Map<String, Integer> soundIds = new HashMap<>();
    private static final float SOUND_VOLUME = 0.35f;

    private void loadSounds() {
        sounds = new android.media.SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
                new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        int[] raw = {R.raw.alert_goal, R.raw.alert_red, R.raw.alert_fulltime, R.raw.alert_incident, R.raw.alert_f1,
                R.raw.alert_news, R.raw.alert_weather, R.raw.alert_tennis, R.raw.alert_fight, R.raw.alert_reminder};
        String[] names = {"goal", "red", "fulltime", "incident", "f1", "news", "weather", "tennis", "fight", "reminder"};
        for (int i = 0; i < raw.length; i++) soundIds.put(names[i], sounds.load(this, raw[i], 1));
    }

    private void play(String sound) {
        if (replaying) return;   // an alert moved to its new place has already sounded
        Integer id = sound == null || sounds == null ? null : soundIds.get(sound);
        if (id != null) sounds.play(id, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1f);
    }

    /** The screensaver is on (its own clock sits bottom-left, where the alerts go). */
    private boolean dreaming;
    private final android.content.BroadcastReceiver dreams = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dreaming = Intent.ACTION_DREAMING_STARTED.equals(i.getAction());
            releaseHeld();
            replace();
        }
    };

    /** Something started or stopped playing: held line-ups come out once nothing plays. */
    private final android.media.AudioManager.AudioPlaybackCallback playback = new android.media.AudioManager.AudioPlaybackCallback() {
        @Override public void onPlaybackConfigChanged(List<android.media.AudioPlaybackConfiguration> configs) {
            // The stream reads as active for a moment after a pause.
            main.postDelayed(GoalWatchService.this::releaseHeld, 1_500);
        }
    };

    /** The running service, for Back from the key helper. */
    private static volatile GoalWatchService self;

    /**
     * Back while an alert shows (InputKeyService, before any app): the alert closes at once, and the next waiting one
     * shows. True when it took the key.
     */
    static boolean back(android.view.KeyEvent e) {
        GoalWatchService s = self;
        if (e.getKeyCode() != android.view.KeyEvent.KEYCODE_BACK || s == null || s.host == null || s.shown == null) return false;
        if (e.getAction() == android.view.KeyEvent.ACTION_UP && !e.isCanceled()) s.main.post(s::dismiss);
        return true;
    }

    // ---- the score cards (ScoreCard): football --------------------------------------------------------------

    /**
     * A goal, red card or full time as a score card: the scorer's (or sent-off player's) photo, both crests and the
     * score, and while the game is on where it is and OK to watch it. Off the main thread for the pictures.
     */
    private void skyFootball(ScoresTicker.Game g, String type, String who, String minute, String side, String kind) {
        io.execute(() -> {
            try {
                skyFootballNow(g, type, who, minute, side, kind);
            } catch (RuntimeException e) {
                // A card that could not be built (a picture, a name) is noted, not lost without a word.
                AlertLog.note(this, "card failed to build: " + type + " " + AlertLog.game(g) + " " + who + " " + minute + ": " + e);
            }
        });
    }

    private void skyFootballNow(ScoresTicker.Game g, String type, String who, String minute, String side, String kind) {
        {
            ScoreCard.Data d = footballData(g);
            boolean home = !"away".equals(side);
            int kit = home ? g.homeColor : g.awayColor;
            d.kind = type;
            if ("ft".equals(type)) {
                d.label = "FULL";
                d.labelSmall = "TIME";
                d.labelColour = 0xFF151A63;
                d.minute = "FT";
                int hs = parse(g.homeScore), as = parse(g.awayScore);
                // The winner by name (else by its three letters, where the name is not known); a draw only when level.
                String winner = hs > as ? (g.homeName != null ? g.homeName : g.home) : as > hs ? (g.awayName != null ? g.awayName : g.away) : null;
                d.last = winner != null ? winner + " win" : hs >= 0 && hs == as ? "Draw" : "Full time";
                StringBuilder scorers = new StringBuilder();
                for (String[] goal : g.goals) {
                    if (scorers.length() > 0) scorers.append(" · ");
                    scorers.append(surname(goal[1])).append(' ').append(goal[2]).append("pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (og)" : "");
                }
                d.first = scorers.toString();
                StringBuilder off = new StringBuilder();
                for (String[] r : g.reds) off.append(off.length() > 0 ? " · " : "").append(surname(r[1])).append(" sent off ").append(r[2]);
                d.line = off.toString();
                d.crest = null;
            } else {
                boolean red = "red".equals(type);
                d.label = red ? "RED" : "GOAL";
                d.labelSmall = red ? "CARD" : null;
                d.labelColour = red ? 0xFFC8102E : kitLabel(kit);
                String[] name = split(who);
                d.first = name[0];
                d.last = name[1];
                d.line = minute + ("pen".equals(kind) ? " · penalty" : "og".equals(kind) ? " · own goal" : red ? " · sent off" : "");
                d.crest = home ? d.homeCrest : d.awayCrest;
                d.photo = playerPhoto(who, home ? g.homeName : g.awayName, kit);
                // A goal: the score before it, to turn over to the new one.
                int hs = parse(g.homeScore), as = parse(g.awayScore);
                if (!red && hs >= 0 && as >= 0) {
                    boolean forHome = home != "og".equals(kind);
                    d.scoreBefore = (forHome ? Math.max(0, hs - 1) : hs) + "–" + (forHome ? as : Math.max(0, as - 1));
                }
            }
            AlertLog.note(this, "card queued: " + type + " " + AlertLog.game(g) + (who != null && !who.isEmpty() ? ", " + who + " " + minute : ""));
            main.post(() -> ScoreCard.show(this, d));
        }
    }

    /** A penalty missed or saved, or a VAR decision, as a score card. */
    private void skyIncident(ScoresTicker.Game g, String[] inc) {
        if (homeOnly(g) && !homeInFront) return;
        io.execute(() -> {
            ScoreCard.Data d = footballData(g);
            String what = inc[1].replaceAll("^[^A-Za-z]+", "").trim();
            String lower = what.toLowerCase(java.util.Locale.ROOT);
            boolean pen = lower.startsWith("pen");
            d.kind = pen ? "pen" : "var";
            d.label = pen ? "PEN" : "VAR";
            d.labelColour = pen ? 0xFF151A63 : 0xFF111214;
            String[] name = split(inc[2]);
            if (lower.contains("ruled out")) {
                d.first = "Check complete";
                d.last = "No goal";
                d.line = inc[3];
            } else if (pen) {
                d.first = name[0];
                d.last = name[1].isEmpty() ? what : name[1];
                d.line = inc[3] + (lower.contains("saved") ? " · saved" : " · missed");
            } else {
                d.first = "VAR check";
                d.last = name[1].isEmpty() ? "Review" : name[1];
                d.line = inc[3];
            }
            if (!inc[2].isEmpty()) d.photo = playerPhoto(inc[2], null, 0);
            main.post(() -> ScoreCard.show(this, d));
        });
    }

    /** What every football card has: the competition, both crests and names, the score, and where to watch it. */
    private ScoreCard.Data footballData(ScoresTicker.Game g) {
        ScoreCard.Data d = new ScoreCard.Data();
        int px = Math.round(96 * getResources().getDisplayMetrics().density);
        d.leagueLogo = bandLogo(g.leagueLogo != null ? g.leagueLogo : leagueLogoFor(g), px);
        d.homeCrest = g.homeLogo == null ? null : ScoreCard.onNavy(ScoresTicker.cachedLogo(this, g.homeLogo, px));
        d.awayCrest = g.awayLogo == null ? null : ScoreCard.onNavy(ScoresTicker.cachedLogo(this, g.awayLogo, px));
        d.homeName = g.homeName != null ? g.homeName : g.home;
        d.awayName = g.awayName != null ? g.awayName : g.away;
        d.score = (g.homeScore == null ? "0" : g.homeScore) + "–" + (g.awayScore == null ? "0" : g.awayScore);
        Object[] w = g.state == 0 ? watchFor(g) : null;
        if (w != null) {
            @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
            android.graphics.Bitmap logo = (android.graphics.Bitmap) w[1];
            d.channelLogo = logo;
            d.channel = names.isEmpty() ? null : names.get(0);
            d.watch = w.length > 2 && w[2] instanceof Runnable ? (Runnable) w[2]
                    : () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
        }
        return d;
    }

    /**
     * A competition's logo for the score card's navy band: the white version (the Premier League's lion in white,
     * ESPN's logos for dark grounds), so it shows on the navy; the logo as it is when there is no such version.
     */
    private android.graphics.Bitmap bandLogo(String url, int px) {
        if (url == null) return null;
        if (premier(url)) {
            android.graphics.Bitmap full = ScoresTicker.cachedLogo(this, url.replace("/500-dark/", "/500/"), px * 2);
            return full == null ? null : lionOnly(full, true);
        }
        android.graphics.Bitmap dark = url.contains("/500/") ? ScoresTicker.cachedLogo(this, url.replace("/500/", "/500-dark/"), px) : null;
        return dark != null ? dark : ScoresTicker.cachedLogo(this, url, px);
    }

    /** The goal label in the scoring side's colour, navy where that is too pale for white letters. */
    private static int kitLabel(int kit) {
        if (kit == 0) return 0xFF151A63;
        double lum = (0.299 * ((kit >> 16) & 0xFF) + 0.587 * ((kit >> 8) & 0xFF) + 0.114 * (kit & 0xFF)) / 255.0;
        return lum > 0.6 ? 0xFF151A63 : kit;
    }

    /** {first names, surname} of a full name. */
    private static String[] split(String full) {
        String f = full == null ? "" : full.trim();
        int sp = f.lastIndexOf(' ');
        return sp > 0 ? new String[]{f.substring(0, sp), f.substring(sp + 1)} : new String[]{"", f};
    }

    /** A player's head and shoulders: his cut-out, else the blank figure in his side's colour. */
    private android.graphics.Bitmap playerPhoto(String who, String team, int kit) {
        if (who == null || who.isEmpty()) return null;
        PlayerPhotos.Cutout cut = premierLeague(who, team);
        if (cut == null) cut = PlayerPhotos.cutout(this, who, team);
        if (cut.url == null || cut.url.isEmpty()) {
            PlayerPhotos.Cutout older = olderPremierLeague(who, team);
            if (older != null) cut = older;
        }
        android.graphics.Bitmap b = PlayerPhotos.photo(this, cut);
        return b != null ? b : LineupCard.avatar(kit, kit, "");
    }

    // ---- the score cards: F1 ----------------------------------------------------------------------------------

    /** As f1Updates, as score cards: a session starting, a new leader, every ten laps, the end. */
    private void skyF1Updates(F1Results.Weekend w) {
        long nowMs = System.currentTimeMillis();
        // Once a weekend, every driver's photo fetched ahead (on the photo thread), for the grid and the result.
        List<String> drivers = new ArrayList<>();
        for (F1Results.Session x : w.sessions) for (String n : x.top) if (!drivers.contains(n)) drivers.add(n);
        if (!drivers.isEmpty() && f1Warmed.add(w.name == null ? "" : w.name)) {
            photoFetch.execute(() -> { for (String n : drivers) PlayerPhotos.driver(this, n); });
        }
        for (F1Results.Session s : w.sessions) {
            if (!s.counts()) continue;
            // Fifteen minutes before a race or sprint: its starting grid, from the qualifying that set it.
            if (s.isRace() && "pre".equals(s.state) && s.start - nowMs > 0 && s.start - nowMs <= 15 * 60_000L && f1Grids.add(s.id)) {
                F1Results.Session q = null;
                for (F1Results.Session x : w.sessions) if (("Race".equals(s.abbr) ? "Qual" : "SS").equals(x.abbr)) q = x;
                if (q != null && !q.top.isEmpty()) f1Board(true, w.name, "Race".equals(s.abbr) ? "RACE" : "SPRINT", q.top, q.logos, s.start);
            }
            String before = f1States.put(s.id, s.state);
            String leader = s.top.isEmpty() ? null : s.top.get(0);
            String lastLeader = leader == null ? f1Leaders.get(s.id) : f1Leaders.put(s.id, leader);
            Integer lastMark = f1Laps.put(s.id, s.lap / 10);
            if (!f1Primed || before == null) continue;
            String gp = F1Results.shortName(w.name == null ? "" : w.name).replace("Grand Prix", "GP");
            if ("pre".equals(before) && "in".equals(s.state)) {
                skyF1(s.isRace() ? "LIGHTS OUT" : s.label.toUpperCase(java.util.Locale.UK), s.isRace() && !"Race".equals(s.abbr) ? "SPRINT" : null,
                        null, null, gp, "Live now", null);
            } else if ("in".equals(before) && "post".equals(s.state)) {
                String what = "Race".equals(s.abbr) ? "RESULT" : "Qual".equals(s.abbr) ? "POLE" : "SS".equals(s.abbr) ? "SPRINT POLE"
                        : "SR".equals(s.abbr) ? "SPRINT" : s.label.toUpperCase(java.util.Locale.UK);
                // A race or sprint: its full result, page by page; qualifying: the top three, as before.
                if (s.isRace()) f1Board(false, w.name, "Race".equals(s.abbr) ? "RACE" : "SPRINT", s.top, s.logos, 0);
                else skyPodium(what, gp, s.top, s.logos, "Race".equals(s.abbr) || "SR".equals(s.abbr) ? "Winner" : "Pole");
            } else if ("in".equals(s.state)) {
                boolean newLeader = leader != null && lastLeader != null && !leader.equals(lastLeader);
                boolean lapMark = s.isRace() && lastMark != null && s.lap / 10 > lastMark && s.lap > 0;
                if (newLeader || lapMark) skyLeader(s, 0, s.lap);
            }
        }
        if (!w.sessions.isEmpty()) f1Primed = true;
    }

    /**
     * "Norris leads": the driver at index (the leader, or for the test hook another) with his photo and team logo, the
     * lap (or the session), and P2 and P3 behind him with their teams' logos.
     */
    private void skyLeader(F1Results.Session s, int index, int lap) {
        if (index >= s.top.size()) return;
        String who = s.top.get(index);
        List<String> rest = new ArrayList<>(), restLogos = new ArrayList<>();
        for (int i = 0; i < s.top.size() && rest.size() < 2; i++) {
            if (i == index) continue;
            rest.add(s.top.get(i));
            restLogos.add(i < s.logos.size() ? s.logos.get(i) : null);
        }
        String others = (rest.size() > 0 ? "P2 " + rest.get(0) : "") + (rest.size() > 1 ? " · P3 " + rest.get(1) : "");
        skyF1(s.isRace() && lap > 0 ? "LAP " + lap : s.label.toUpperCase(java.util.Locale.UK), null, who,
                index < s.logos.size() ? s.logos.get(index) : null, who + (s.isRace() ? " leads" : " goes P1"), others, "P1",
                rest, restLogos);
    }

    /** One F1 card: the label, the driver (photo and team logo) or the weekend's name, a line, and OK to watch. */
    private void skyF1(String label, String small, String driver, String teamLogo, String big, String line, String position) {
        skyF1(label, small, driver, teamLogo, big, line, position, null, null);
    }

    /**
     * As above, with the drivers behind the leader (P2, P3: surnames and their teams' logo addresses), each shown with
     * his team's logo in place of the plain line.
     */
    private void skyF1(String label, String small, String driver, String teamLogo, String big, String line, String position,
                       List<String> behind, List<String> behindLogos) {
        io.execute(() -> {
            ScoreCard.Data d = f1Data();
            d.label = label;
            d.labelSmall = small;
            d.last = big;
            d.line = line == null ? "" : line;
            d.position = position;
            if (driver != null) d.photo = PlayerPhotos.driver(this, driver);
            if (teamLogo != null) d.teamLogo = ScoresTicker.cachedLogo(this, teamLogo, 96);
            for (int i = 0; behind != null && i < behind.size(); i++) {
                String logo = behindLogos != null && i < behindLogos.size() ? behindLogos.get(i) : null;
                d.others.add(new Object[]{"P" + (i + 2), behind.get(i), logo == null ? null : ScoresTicker.cachedLogo(this, logo, 96)});
            }
            main.post(() -> ScoreCard.show(this, d));
        });
    }

    /** An F1 result's top three, each with his photo and team logo. */
    private void skyPodium(String label, String small, List<String> top, List<String> logos, String winnerLine) {
        List<String> names = new ArrayList<>(top), teams = new ArrayList<>(logos);
        io.execute(() -> {
            ScoreCard.Data d = f1Data();
            d.label = label;
            d.labelSmall = small;
            for (int i = 0; i < Math.min(3, names.size()); i++) {
                String team = i < teams.size() ? teams.get(i) : null;
                d.podium.add(new Object[]{String.valueOf(i + 1), PlayerPhotos.driver(this, names.get(i)),
                        team == null ? null : ScoresTicker.cachedLogo(this, team, 96), names.get(i), i == 0 ? winnerLine : null});
            }
            main.post(() -> ScoreCard.show(this, d));
        });
    }

    /** What every F1 card has: the F1 mark in white, and Sky Sports F1 (UHD where TiviMate has it) with OK. */
    private ScoreCard.Data f1Data() {
        ScoreCard.Data d = new ScoreCard.Data();
        d.f1 = true;
        d.kind = "f1";
        d.labelColour = 0xFFE10600;
        android.graphics.Bitmap mark = ScoresTicker.cachedLogo(this, F1Results.F1_LOGO, 200);
        // Trimmed: ESPN's mark sits small in the middle of a square, so untrimmed it showed tiny in its band.
        d.leagueLogo = mark == null ? null : white(HomeV2.trim(mark));
        ScoresTicker.Game f1 = new ScoresTicker.Game();
        f1.leagueLogo = F1Results.F1_LOGO;
        Object[] w = watchOn(TvGuide.watch(this, new String[0], f1));
        if (w != null) {
            @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
            android.graphics.Bitmap logo = (android.graphics.Bitmap) w[1];
            d.channelLogo = logo;
            d.channel = names.isEmpty() ? null : names.get(0);
            d.watch = () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
        }
        return d;
    }

    /** The races whose starting grid has shown (once each), and the weekends whose drivers' photos are fetched. */
    private final java.util.Set<String> f1Grids = new java.util.HashSet<>(), f1Warmed = new java.util.HashSet<>();

    /**
     * An F1 board (LineupCard.showF1): a race's or sprint's full result (grid false) or its starting grid, from
     * Jolpica when it has this Grand Prix (times, gaps, points, who retired; the grid's qualifying times), else from
     * ESPN's order (surnames and team logos only). The podium's or the front rows' photos, every team's logo, the F1
     * mark and the channel showing F1, fetched first. Off the main thread.
     */
    private void f1Board(boolean grid, String gpName, String session, List<String> order, List<String> orderLogos, long startAt) {
        List<String> names = order == null ? new ArrayList<>() : new ArrayList<>(order);
        List<String> logos = orderLogos == null ? new ArrayList<>() : new ArrayList<>(orderLogos);
        // On the photo thread, not io: the first time, every driver's photo is paced out over most of a minute, and the
        // goal alerts must not wait behind it.
        photoFetch.execute(() -> {
            LineupCard.F1 f = new LineupCard.F1();
            f.grid = grid;
            f.session = session;
            f.startAt = startAt;
            F1Results.Board rich = grid ? F1Results.jolpicaQualifying(gpName) : F1Results.jolpicaResult(gpName, "SPRINT".equals(session));
            f.gp = F1Results.shortName(gpName != null ? gpName : rich != null ? rich.name : "");
            if (rich != null) {
                f.rows.addAll(rich.rows);
            } else {
                for (int i = 0; i < names.size(); i++) {
                    F1Results.Row r = new F1Results.Row();
                    r.pos = i + 1;
                    r.last = names.get(i);
                    r.code = F1Results.code(r.last);
                    r.logo = i < logos.size() ? logos.get(i) : null;
                    String team = F1Results.teamsCached().get(F1Results.key(r.last));
                    r.teamId = team == null ? "" : team;
                    f.rows.add(r);
                }
            }
            if (f.rows.isEmpty()) return;
            android.graphics.Bitmap mark = ScoresTicker.cachedLogo(this, F1Results.F1_LOGO, 200);
            f.mark = mark == null ? null : white(HomeV2.trim(mark));
            for (F1Results.Row r : f.rows) {
                if (r.logo != null && !f.logos.containsKey(r.logo)) f.logos.put(r.logo, ScoresTicker.cachedLogo(this, r.logo, 96));
            }
            // Every driver's photo (kept once found, so after the first time this is quick); the plain figure without one.
            for (F1Results.Row r : f.rows) {
                android.graphics.Bitmap ph = PlayerPhotos.driver(this, r.last);
                if (ph != null) f.photos.put(r.pos, ph);
            }
            ScoreCard.Data d = f1Data();
            f.channel = d.channel;
            f.channelLogo = d.channelLogo;
            f.watch = d.watch;
            main.post(() -> LineupCard.showF1(this, f));
        });
    }

    /** A logo in plain white (its shape kept), for the F1 mark on Sky Sports F1's red. */
    static android.graphics.Bitmap white(android.graphics.Bitmap b) {
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(b.getWidth(), b.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
        c.drawBitmap(b, 0, 0, p);
        return out;
    }

    // ---- the line-up card's photos, ahead of it -----------------------------------------------------------------

    /** test_fixture's elevens, by its made-up game id, taken by enqueueReminder in place of reading them. */
    private final Map<String, ScoresTicker.Side[]> testLineups = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A Premier League player's own photo from the league (no nationality: ESPN's gives his flag), this season's, once
     * it has loaded (a player just signed may have none yet); null otherwise.
     */
    private PlayerPhotos.Cutout premierLeague(String fullName, String club) {
        return loaded(PremierLeague.photo(this, fullName, club));
    }

    /** Last season's photo from the league, for a player with none this season and none at TheSportsDB; or null. */
    private PlayerPhotos.Cutout olderPremierLeague(String fullName, String club) {
        return loaded(PremierLeague.older(PremierLeague.photo(this, fullName, club)));
    }

    private PlayerPhotos.Cutout loaded(String url) {
        if (url == null) return null;
        PlayerPhotos.Cutout cut = new PlayerPhotos.Cutout();
        cut.url = url;
        cut.nation = "";
        return PlayerPhotos.photo(this, cut) != null ? cut : null;
    }

    /** Games whose line-ups' photos are fetched (or being fetched), and when a game without line-ups is asked again. */
    private final Set<String> photosFetched = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lineupsAskAgain = new java.util.concurrent.ConcurrentHashMap<>();
    private final ExecutorService photoFetch = Executors.newSingleThreadExecutor(LOW);

    /**
     * The players' photos, fetched as soon as a game's line-ups are out (about an hour before kick-off), a lookup at a
     * time at TheSportsDB's pace, so the line-up card 15 minutes before has them all at once. Off the main thread.
     */
    private void prefetchLineups(List<ScoresTicker.Game> games, long now) {
        for (ScoresTicker.Game g : games) {
            if (g.id == null || g.state != 1 || g.kickoff - now <= 0 || g.kickoff - now > 80 * 60_000L) continue;
            if (photosFetched.contains(g.id) || now < lineupsAskAgain.getOrDefault(g.id, 0L) || !lineupGame(g)) continue;
            photosFetched.add(g.id);
            photoFetch.execute(() -> {
                ScoresTicker.Side[] sides = ScoresTicker.lineups(g);
                if (sides == null) {
                    // Not out yet: asked again in five minutes.
                    lineupsAskAgain.put(g.id, System.currentTimeMillis() + 5 * 60_000L);
                    photosFetched.remove(g.id);
                    return;
                }
                for (ScoresTicker.Side side : sides) {
                    for (String[] p : side.players) {
                        String full = p.length > 4 && p[4] != null ? p[4] : p[0];
                        PlayerPhotos.Cutout pl = premierLeague(full, side.name);
                        if (pl != null) { PlayerPhotos.photo(this, pl); continue; }
                        PlayerPhotos.Cutout cut = PlayerPhotos.cutout(this, full, side.name);
                        // Refused once (TheSportsDB's limit, not a long refusal): a minute's rest, then once more.
                        if (cut.url == null && !PlayerPhotos.cooling()) {
                            try { Thread.sleep(60_000L); } catch (InterruptedException e) { return; }
                            cut = PlayerPhotos.cutout(this, full, side.name);
                        }
                        if (cut.url != null && !cut.url.isEmpty()) {
                            PlayerPhotos.photo(this, cut);   // the picture itself into the cache too
                        } else {
                            olderPremierLeague(full, side.name);
                        }
                    }
                }
            });
        }
    }

    // ---- the score cards: kick-off and reminders ---------------------------------------------------------------

    /**
     * A game about to start as a score card: KICK-OFF over the minutes to go, the clubs, the competition and ground,
     * both crests in the end block with the kick-off time, and where it is on only where a guide lists it.
     */
    /** A club's name as people say it: "Tottenham Hotspur" is Tottenham, "Manchester United" Man Utd. */
    private static String shortClub(String name) {
        if (name == null) return "";
        switch (name) {
            case "Manchester United": return "Man Utd";
            case "Manchester City": return "Man City";
            case "Wolverhampton Wanderers": return "Wolves";
            case "Nottingham Forest": return "Nott'm Forest";
            case "Brighton & Hove Albion": return "Brighton";
            case "Paris Saint-Germain": return "PSG";
            case "Internazionale": return "Inter";
            case "Borussia Dortmund": return "Dortmund";
            case "Bayer Leverkusen": return "Leverkusen";
            case "Atletico Madrid": case "Atlético Madrid": return "Atlético";
            default:
                return name.replaceFirst(" (Hotspur|Wanderers|Albion|United|Athletic)$", "").replaceFirst("^AFC ", "");
        }
    }

    private ScoreCard.Data kickoffCard(ScoresTicker.Game g) {
        ScoreCard.Data d = footballData(g);
        d.kind = "kickoff";
        d.kickoffAt = g.kickoff;
        long mins = Math.max(0, Math.round((g.kickoff - System.currentTimeMillis()) / 60_000.0));
        d.label = "KICK-OFF";
        d.labelSmall = mins > 0 ? "IN " + mins + " MIN" : "NOW";
        d.labelColour = 0xFF151A63;
        // The clubs' full names when they fit; else as people say them (Tottenham, Man Utd, Wolves); else ESPN's codes.
        String full = d.homeName + " v " + d.awayName, said = shortClub(d.homeName) + " v " + shortClub(d.awayName);
        d.last = full.length() <= 26 ? full : said.length() <= 26 ? said : g.home + " v " + g.away;
        String comp = ScoresTicker.competition(g);
        d.first = comp == null ? "" : comp;
        String time = g.kickoff > 0 ? new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(new java.util.Date(g.kickoff)) : null;
        d.line = (g.venue == null || g.venue.isEmpty() ? "" : g.venue) + (time == null ? "" : (g.venue == null || g.venue.isEmpty() ? "" : " · ") + "Kick-off " + time);
        d.score = "v";
        d.minute = null;
        d.channelLogo = null;
        d.channel = null;
        d.watch = null;
        Object[] w = watchFor(g);
        if (w != null) {
            @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
            android.graphics.Bitmap logo = (android.graphics.Bitmap) w[1];
            d.channelLogo = logo;
            d.channel = names.isEmpty() ? null : names.get(0);
            d.watch = w.length > 2 && w[2] instanceof Runnable ? (Runnable) w[2]
                    : () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
        }
        return d;
    }

    /** An event asked to be reminded of (v2's Sport page) as a score card: STARTING, its title, and its channel. */
    private void skyReminder(JSONObject r, long mins) {
        String title = r.optString("t"), channel = r.optString("c"), logoUrl = r.optString("l");
        long start = r.optLong("s");
        io.execute(() -> {
            // A football match ("Spurs v C. Palace"): found in the day's fixtures and shown as the kick-off card, both
            // crests and all, with the reminder's own channel where the guides list none.
            ScoresTicker.Game game = reminderGame(title, start);
            // Not in any day's scores (a replay, a game already played, a league no feed has): the two clubs by name,
            // so it still shows both crests.
            if (game == null) game = titleFixture(title, start);
            if (game != null) {
                if (game.kickoff == 0) game.kickoff = start;
                ScoreCard.Data k = kickoffCard(game);
                k.labelSmall = mins > 0 ? "IN " + mins + " MIN" : "NOW";
                if (k.watch == null && !channel.isEmpty()) {
                    int px = Math.round(96 * getResources().getDisplayMetrics().density);
                    android.graphics.Bitmap logo = logoUrl.isEmpty() ? null : HomeV2.trim(ScoresTicker.cachedLogo(this, logoUrl, px));
                    List<String> names = new ArrayList<>(Arrays.asList(channel));
                    k.channelLogo = logo;
                    k.channel = channel;
                    k.watch = () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
                }
                main.post(() -> ScoreCard.show(this, k));
                return;
            }
            ScoreCard.Data d = new ScoreCard.Data();
            d.kind = "reminder";
            d.leagueLogo = emoji("⏰", 120);
            d.label = "STARTING";
            d.labelSmall = mins > 0 ? "IN " + mins + " MIN" : "NOW";
            d.labelColour = 0xFF151A63;
            d.headline = title;
            if (!channel.isEmpty()) {
                int px = Math.round(96 * getResources().getDisplayMetrics().density);
                android.graphics.Bitmap logo = logoUrl.isEmpty() ? null : HomeV2.trim(ScoresTicker.cachedLogo(this, logoUrl, px));
                List<String> names = new ArrayList<>(Arrays.asList(channel));
                d.channelLogo = logo;
                d.channel = channel;
                d.watch = () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
            }
            main.post(() -> ScoreCard.show(this, d));
        });
    }

    /**
     * The match a reminder's title names ("SNF: Spurs v C. Palace"), from that day's fixtures in the alert leagues,
     * the clubs' leagues and cups and the line-up competitions; null for anything else. Off the main thread.
     */
    private ScoresTicker.Game reminderGame(String title, long start) {
        if (title == null || start <= 0) return null;
        String[] p = sides(title);
        if (p == null) return null;
        List<String> leagues = clubLeagues();
        java.time.LocalDate day = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        for (ScoresTicker.Game g : ScoresTicker.load(leagues.toArray(new String[0]), day)) {
            if (TvGuide.sameClub(p[0], g.homeName) && TvGuide.sameClub(p[1], g.awayName)
                    || TvGuide.sameClub(p[0], g.awayName) && TvGuide.sameClub(p[1], g.homeName)) return g;
        }
        return null;
    }

    /** The two sides a title names ("SNF: Spurs v C. Palace (Replay)" gives Spurs, C. Palace); null for anything else. */
    private static String[] sides(String title) {
        if (title == null) return null;
        String t = title.contains(": ") ? title.substring(title.lastIndexOf(": ") + 2) : title;
        t = t.replaceAll("\\s*\\(.*?\\)\\s*$", "").trim();
        String[] p = t.split("\\s+(?:v|vs|V|VS)\\.?\\s+", 2);
        if (p.length != 2 || p[0].trim().isEmpty() || p[1].trim().isEmpty()) return null;
        return new String[]{p[0].trim(), p[1].trim()};
    }

    /** Every league and cup the launcher follows: the Sport page's, the alert leagues and the line-up competitions. */
    private List<String> clubLeagues() {
        List<String> all = new ArrayList<>();
        java.util.Set<String> slugs = new java.util.HashSet<>();
        List<String> in = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.score_leagues)));
        in.addAll(Arrays.asList(getResources().getStringArray(R.array.goal_alert_leagues)));
        in.addAll(Arrays.asList(getResources().getStringArray(R.array.team_alert_leagues)));
        in.addAll(Arrays.asList(LINEUP_LEAGUES));
        for (String e : in) if (slugs.add(e.split("\\|", 2)[0].trim())) all.add(e);
        return all;
    }

    /** Each club in those competitions: {slug, tag, name, short name, abbreviation, crest}; read once a day. */
    private static List<String[]> clubs;
    private static long clubsAt;

    private synchronized List<String[]> clubs() {
        long now = System.currentTimeMillis();
        if (clubs != null && now - clubsAt < 86_400_000L) return clubs;
        java.io.File file = new java.io.File(getCacheDir(), "clubs.tsv");
        List<String[]> read = new ArrayList<>();
        if (file.isFile() && now - file.lastModified() < 86_400_000L) {
            try {
                for (String line : java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    String[] c = line.split("\t", -1);
                    if (c.length == 6) read.add(c);
                }
            } catch (Exception ignored) {
                read.clear();
            }
        }
        if (read.isEmpty()) {
            for (String e : clubLeagues()) {
                String[] parts = e.split("\\|", 2);
                String slug = parts[0].trim(), tag = parts.length > 1 ? parts[1].trim() : slug;
                try {
                    JSONObject root = new JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/teams"));
                    org.json.JSONArray teams = root.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues")
                            .getJSONObject(0).getJSONArray("teams");
                    for (int i = 0; i < teams.length(); i++) {
                        JSONObject t = teams.getJSONObject(i).getJSONObject("team");
                        org.json.JSONArray logos = t.optJSONArray("logos");
                        String crest = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href", "") : "";
                        if (crest.isEmpty()) continue;
                        read.add(new String[]{slug, tag, t.optString("displayName", ""), t.optString("shortDisplayName", ""),
                                t.optString("abbreviation", ""), crest});
                    }
                } catch (Exception ignored) {
                    // that competition is left out
                }
            }
            if (!read.isEmpty()) {
                try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                    for (String[] c : read) w.write(String.join("\t", c).replace("\n", " ") + "\n");
                } catch (Exception ignored) {
                    // kept in memory only
                }
            }
        }
        if (!read.isEmpty()) { clubs = read; clubsAt = now; }
        return clubs == null ? new ArrayList<>() : clubs;
    }

    private static String plainClub(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("\\b(fc|afc|cf)\\b", "").replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** The clubs a name could be: those named exactly so first ("Rangers" is Rangers, not Queens Park Rangers). */
    private static List<String[]> clubsNamed(List<String[]> all, String name) {
        String n = plainClub(name);
        List<String[]> exact = new ArrayList<>(), near = new ArrayList<>();
        for (String[] c : all) {
            if (n.equals(plainClub(c[2])) || n.equals(plainClub(c[3])) || n.equalsIgnoreCase(c[4])) exact.add(c);
            else if (TvGuide.sameClub(name, c[2]) || TvGuide.sameClub(name, c[3])) near.add(c);
        }
        return exact.isEmpty() ? near : exact;
    }

    /** A cup or a European or world competition, not a league ("uefa.champions", "eng.fa", "eng.league_cup"). */
    private static boolean cup(String slug) {
        return slug.startsWith("uefa.") || slug.startsWith("fifa.") || slug.startsWith("conmebol.") || slug.endsWith(".fa")
                || slug.contains("cup") || slug.contains("copa") || slug.contains("pokal") || slug.contains("coupe");
    }

    /**
     * A match by its title alone, for the crests: both clubs found by name in the competitions followed, from the
     * same competition where they share one (its logo and name on the card too). Null when either is not found.
     */
    private ScoresTicker.Game titleFixture(String title, long start) {
        String[] p = sides(title);
        if (p == null) return null;
        List<String[]> all = clubs();
        List<String[]> hs = clubsNamed(all, p[0]), as = clubsNamed(all, p[1]);
        if (hs.isEmpty() || as.isEmpty()) return null;
        String[] h = hs.get(0), a = as.get(0);
        // A competition both are in: their league before a cup or a European one (Barcelona v Real Madrid is LaLiga's,
        // though both are in the Champions League too).
        boolean shared = false;
        int best = Integer.MAX_VALUE;
        for (String[] x : hs) {
            for (String[] y : as) {
                if (!x[0].equals(y[0])) continue;
                int rank = cup(x[0]) ? 1 : 0;
                if (rank < best) { best = rank; h = x; a = y; shared = true; }
            }
        }
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.home = h[4]; g.away = a[4];
        g.homeName = h[2]; g.awayName = a[2];
        g.homeLogo = h[5]; g.awayLogo = a[5];
        g.slug = shared ? h[0] : null;
        g.league = shared ? h[1] : "";
        g.kickoff = start;
        g.state = 1;
        return g;
    }

    /** An emoji drawn as a picture (the band's mark where there is no logo). */
    private static android.graphics.Bitmap emoji(String e, int size) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size * 0.8f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        new android.graphics.Canvas(b).drawText(e, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    // ---- the score cards: fights ------------------------------------------------------------------------------

    /**
     * A UFC fight as a score card, in the UFC's black and red: a result (the winner head and shoulders, whom he beat,
     * and how and when in the end block), or a main event starting (both fighters, a "v" between them). Where it is on
     * with OK while the card still has fights to come and a guide lists it. Off the main thread for the pictures.
     */
    private void skyFight(MoreSports.Result r, boolean starting) {
        io.execute(() -> {
            ScoreCard.Data d = fightData();
            d.labelColour = 0xFFD20A11;
            boolean over = !starting && r.mainEvent;
            if (starting) {
                d.label = "LIVE";
                d.labelSmall = "MAIN EVENT";
                d.versus = true;
                d.podium.add(fighter(r.winnerId, r.winnerFlag, r.winner));
                d.podium.add(fighter(r.loserId, r.loserFlag, r.loser));
            } else {
                d.label = "RESULT";
                d.labelSmall = r.mainEvent ? "MAIN EVENT" : r.round == null ? null : r.round.toUpperCase(java.util.Locale.UK);
                String[] name = split(r.winnerFull != null && !r.winnerFull.isEmpty() ? r.winnerFull : r.winner);
                d.first = name[0];
                d.last = name[1];
                d.line = "def. " + r.loser + (r.event == null || r.event.isEmpty() ? "" : " · " + r.event);
                d.photo = fighterPhoto(r.winnerId);
                String[] how = (r.score == null ? "Win" : r.score).split("\\s*·\\s*", 2);
                d.method = how[0];
                d.methodSmall = how.length > 1 ? how[1] : null;
            }
            // Where it is on only where the playlist's guides list the fight, never assumed; nothing shown otherwise.
            Object[] w = over ? null : playlistWatch(r.winnerFull, r.loserFull, null);
            if (w != null) {
                @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
                d.channelLogo = (android.graphics.Bitmap) w[1];
                d.channel = names.get(0);
                d.watch = (Runnable) w[2];
            }
            main.post(() -> ScoreCard.show(this, d));
        });
    }

    /** A boxing headline (BBC Sport) as a score card: its picture, the headline, in black and gold. */
    private void skyBoxing(String[] item) {
        io.execute(() -> {
            ScoreCard.Data d = new ScoreCard.Data();
            d.dark = true;
            d.kind = "fight";
            d.band = 0xFF3A2C0E;
            d.bandDeep = 0xFF0E0B05;
            d.wideMark = true;
            d.wideMark = false;
            d.leagueLogo = emoji("🥊", 120);
            d.label = "BOXING";
            d.labelSmall = "BBC SPORT";
            d.labelColour = 0xFFB8892B;
            d.headline = item[1];
            String pic = item.length > 4 ? item[4] : null;
            if (pic != null) d.picture = ScoresTicker.cachedLogo(this, pic.replace("/standard/800/", "/standard/240/"), 240);
            main.post(() -> ScoreCard.show(this, d));
        });
    }

    /** What every UFC card has: the UFC's mark in white on black. */
    private ScoreCard.Data fightData() {
        ScoreCard.Data d = new ScoreCard.Data();
        d.dark = true;
        d.kind = "fight";
        d.band = 0xFF1C1C1F;
        d.bandDeep = 0xFF000000;
        d.wideMark = true;
        d.block = 0xFFD20A11;
        android.graphics.Bitmap mark = ScoresTicker.cachedLogo(this, MoreSports.UFC_LOGO, 200);
        d.leagueLogo = mark == null ? null : white(HomeV2.trim(mark));
        return d;
    }

    /** One fighter for a main event's card: {"", head and shoulders, flag, surname, null}. */
    private Object[] fighter(String id, String flag, String surname) {
        return new Object[]{"", fighterPhoto(id), flag == null ? null : ScoresTicker.cachedLogo(this, flag, 96), surname, null};
    }

    /** ESPN's cut-out of a UFC fighter, cropped square to his head and shoulders. */
    private android.graphics.Bitmap fighterPhoto(String id) {
        if (id == null || id.isEmpty()) return null;
        android.graphics.Bitmap sq = ScoresTicker.cachedLogo(this, "https://a.espncdn.com/i/headshots/mma/players/full/" + id + ".png", 200);
        if (sq == null || sq.getWidth() < 40) return sq;
        // ESPN's square has him small in a wide frame: cut to head and shoulders, upright, as the Premier League frames
        // its players (the cards fit an upright photo to its width, the face at the top).
        float u = sq.getWidth() / 400f;
        int x = Math.round(48 * u), y = Math.round(18 * u), w = Math.round(304 * u), h = Math.min(sq.getHeight() - y, Math.round(362 * u));
        return android.graphics.Bitmap.createBitmap(sq, x, y, Math.min(w, sq.getWidth() - x), h);
    }

    /** adb test: one of each score card, with today's Chelsea v Man United and an F1 race, to see them. */
    private void testSky() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "test"; g.slug = "eng.1"; g.league = "PL"; g.state = 0;
        g.home = "CHE"; g.away = "MUN"; g.homeName = "Chelsea"; g.awayName = "Manchester United";
        g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/363.png";
        g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/360.png";
        g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png";
        g.homeColor = 0xFF14349B; g.awayColor = 0xFFDA020E;
        g.homeScore = "2"; g.awayScore = "0";
        g.kickoff = 1_793_449_800_000L; g.tvAt = 1_793_449_800_000L;
        g.goals.add(new String[]{"t1", "Morgan Rogers", "50'", "home", ""});
        g.goals.add(new String[]{"t2", "Cole Palmer", "64'", "home", "pen"});
        g.reds.add(new String[]{"t3", "Lisandro Martínez", "63'", "away", ""});
        skyFootball(g, "goal", "Cole Palmer", "64'", "home", "pen");
        skyFootball(g, "red", "Lisandro Martínez", "63'", "away", "");
        skyIncident(g, new String[]{"t4", "📺  VAR", "", "62'"});
        // Full time on a copy: the cards above are still being made from g, live (with its channel), on the io thread.
        ScoresTicker.Game ft = new ScoresTicker.Game();
        ft.id = g.id; ft.slug = g.slug; ft.league = g.league; ft.state = 2;
        ft.home = g.home; ft.away = g.away; ft.homeName = g.homeName; ft.awayName = g.awayName;
        ft.homeLogo = g.homeLogo; ft.awayLogo = g.awayLogo; ft.leagueLogo = g.leagueLogo;
        ft.homeColor = g.homeColor; ft.awayColor = g.awayColor; ft.homeScore = g.homeScore; ft.awayScore = g.awayScore;
        ft.kickoff = g.kickoff; ft.tvAt = g.tvAt;
        ft.goals.addAll(g.goals);
        ft.reds.addAll(g.reds);
        main.postDelayed(() -> skyFootball(ft, "ft", "", "", "", "ft"), 500);
        List<String> top = Arrays.asList("Norris", "Verstappen", "Piastri");
        List<String> logos = Arrays.asList(F1Results.logo("mclaren"), F1Results.logo("red_bull"), F1Results.logo("mclaren"));
        skyF1("LAP 34", null, "Norris", logos.get(0), "Norris leads", "P2 Verstappen · P3 Piastri", "P1",
                top.subList(1, 3), logos.subList(1, 3));
        skyPodium("RESULT", "Mexico City GP", top, logos, "Winner");
    }

    /** Whether one of the service's own alerts is on screen (the score cards wait for it). */
    static boolean alertShowing() {
        GoalWatchService s = self;
        return s != null && s.host != null;
    }

    /** An alert's words, for the pop-up history. */
    private static String describe(Object[] entry) {
        StringBuilder b = new StringBuilder();
        if (entry != null) for (Object o : entry) {
            if (o instanceof CharSequence) b.append(o).append(' ');
            else if (o instanceof List) { for (Object p : (List<?>) o) if (p instanceof CharSequence) b.append(p).append(' '); }
            else if (o instanceof F1Results.Race) b.append("F1 result ");
        }
        String s = b.toString().replaceAll("\\s+", " ").trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    /** The alert showing away now, its timers with it. */
    private void dismiss() {
        if (host == null) return;
        if (watchClose != null) main.removeCallbacks(watchClose);
        watchClose = null;
        showingLineup = null;
        host.setOnKeyListener(null);
        // Back: the alert shrinks a little and fades, then goes.
        android.widget.FrameLayout v = host;
        PopupMotion.backOut(v, v.getWidth() / 2f, v.getHeight() / 2f, () -> { if (host == v) removeHost(); });
    }

    @Override public void onCreate() {
        super.onCreate();
        self = this;
        android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_DREAMING_STARTED);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dreams, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dreams, f);
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        if (am != null) am.registerAudioPlaybackCallback(playback, main);
        deliveries = new DeliveryLink(this, new DeliveryLink.Sink() {
            @Override public void pairing(String phone, String code) { main.post(() -> showPairing(phone, code)); }
            @Override public void delivery(JSONObject update) { main.post(() -> showDelivery(update)); }
            @Override public void paired() { main.post(() -> { if (pairingClose != null) pairingClose.run(); }); }
        });
        deliveries.start();
        relane = () -> main.postDelayed(this::replace, 250);
    }

    // ---- Home in front or not: the alert on screen moves to its place there -----------------------------------

    /** Home (MainActivity, v2) says it came to the front or went: run on any thread. */
    private static volatile Runnable relane;

    static void relane() {
        Runnable r = relane;
        if (r != null) r.run();
    }

    /** The alert showing: its queue entry, to show it again in its new place. Null once it has started closing. */
    private Object[] current;
    private boolean replaying;

    /**
     * Home came to the front or went while an alert showed: drawn for an app (silver, in the corner) it would sit
     * wrongly on v2's Home, whose alerts take the score bar's place in the pills' glass, and the other way round. It
     * is taken down at once and shown again from the start in the new place, without its sound.
     */
    private void replace() {
        if (host == null || shown == null || hostOnLane == onLane()) return;
        Object[] again = current;
        if (watchClose != null) main.removeCallbacks(watchClose);
        watchClose = null;
        showingLineup = null;
        host.setOnKeyListener(null);
        host.removeAllViews();
        try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
        host = null;
        laneShown(false);
        shown = null;
        current = null;
        if (again != null) queue.addFirst(again);
        replaying = again != null;
        try {
            showNext();
        } finally {
            replaying = false;
        }
    }

    // ---- delivery updates from the phone (DeliveryLink) ----------------------------------------------------

    private DeliveryLink deliveries;

    /** The code a phone asks to pair with, for the person to type there: ahead of any other alert waiting. */
    private void showPairing(String phone, String code) {
        float d = getResources().getDisplayMetrics().density;
        queue.removeIf(e -> e.length == 2 && e[0] instanceof DeliveryCard.Card && ((DeliveryCard.Card) e[0]).pairing);
        queue.removeIf(e -> e.length == 2 && e[0] instanceof DeliveryCard.Card && ((DeliveryCard.Card) e[0]).pairing);
        queue.addFirst(new Object[]{DeliveryCard.pairing(this, phone, code, glass(20 * d)), "reminder"});
        if (pairingClose != null) pairingClose.run();   // an older code on screen gives way to the new one
        if (pairingClose != null) pairingClose.run();   // an older code on screen gives way to the new one
        showNext();
    }

    /**
     * A delivery app's update, as a card in that app's colours: its logo, the restaurant, the stage with a look of
     * its own, the bar of stages and what was ordered (DeliveryCard).
     */
    private void showDelivery(JSONObject u) {
        String kind = u.optString("kind");
        if ("delivery".equals(kind) || kind.isEmpty()) { SmartPanel.delivery = u; SmartPanel.deliveryAt = System.currentTimeMillis(); }
        if ("car".equals(kind)) { showCar(u); return; }
        if ("home_eta".equals(kind)) {
            SmartPanel.trip = u;
            SmartPanel.tripAt = System.currentTimeMillis();
            AmbientScene.carNews();
            if (Config.themeFlag(this, "home_eta", false)) {
                queue.addFirst(new Object[]{DeliveryCard.homeEta(this, u, glass(CORNER_DP * getResources().getDisplayMetrics().density)), "reminder"});
                showNext();
            }
            return;
        }
        if (!kind.isEmpty() && !"delivery".equals(kind)) return;   // something newer than this TV knows
        float d = getResources().getDisplayMetrics().density;
        // Ahead of the sports alerts waiting: an order's news is now or never.
        queue.addFirst(new Object[]{DeliveryCard.food(this, u, glass(CORNER_DP * d)), u.optInt("stage") == 4 ? "fulltime" : "reminder"});
        showNext();
    }

    /**
     * The car app's notification (Tesla), passed on by the phone: a card with the car, the Tesla mark and the battery,
     * over any app and on Home.
     */
    private void showCar(JSONObject u) {
        String title = u.optString("title", "").trim(), status = u.optString("status", "").trim();
        if (title.isEmpty() && status.isEmpty()) return;
        SmartPanel.car = u;
        SmartPanel.carAt = System.currentTimeMillis();
        AmbientScene.carNews();
        float d = getResources().getDisplayMetrics().density;
        queue.addFirst(new Object[]{DeliveryCard.car(this, u, glass(CORNER_DP * d)), "reminder"});
        showNext();
    }

    /** Closes the pairing code's card once the phone has typed it in. */
    private Runnable pairingClose;

    /** A card (DeliveryCard) rises into place, plays its reveal, stays a while and fades. */
    private void showCard(DeliveryCard.Card given, String sound) {
        float d = getResources().getDisplayMetrics().density;
        // On v2's Home a card becomes one line across the score bar's place, in the pills' clear glass.
        boolean bar = onLane() && given.lane != null;
        DeliveryCard.Card card = bar ? given.lane.get() : given;
        ensureHost(card.widthDp, card.heightDp);
        if (host == null) { showNext(); return; }
        View panel = card.view;
        host.addView(panel, new android.widget.FrameLayout.LayoutParams(
                bar ? android.widget.FrameLayout.LayoutParams.MATCH_PARENT : android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                bar ? Gravity.TOP : onLane() ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.BOTTOM | Gravity.START));
        shown = panel;
        play(sound);
        Runnable[] closing = new Runnable[1];
        LinearLayout box = (LinearLayout) panel;
        final float[] visible = {Float.MAX_VALUE};
        Runnable close = () -> {
            main.removeCallbacks(closing[0]);
            if (pairingClose == closing[0]) pairingClose = null;
            if (shown != panel) return;
            // Closed as a goal alert closes: the words fade, the pill draws back to a dot, the dot goes.
            visible[0] = box.getWidth();
            outline(box, box.getHeight(), visible);
            closePill(box, box.getHeight(), visible);
        };
        closing[0] = close;
        if (card.pairing) pairingClose = close;
        if (calm()) {
            card.reveal.accept(stillOnly() ? DeliveryCard.STILL : DeliveryCard.CALM);
            main.postDelayed(close, card.showMs);
            return;
        }
        // Opened as a goal alert opens: a glass dot pops in at the corner, the pill stretches out to the right with
        // its round ends kept, then the words fade in, and the card's own reveal (its bar filling) plays.
        for (int c = 0; c < box.getChildCount(); c++) box.getChildAt(c).setAlpha(0f);
        box.setAlpha(0f);
        box.setScaleX(0.3f);
        box.setScaleY(0.3f);
        box.post(() -> {
            int h = box.getHeight();
            visible[0] = h;
            outline(box, h, visible);
            box.setPivotX(h / 2f);
            box.setPivotY(h / 2f);
            box.setLayerType(animLayer(), null);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).withEndAction(() -> {
                ((RevealBox) box).setFrozen(true);
                android.animation.ValueAnimator grow = android.animation.ValueAnimator.ofFloat(h, box.getWidth());
                grow.setDuration(850);
                grow.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                grow.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); box.invalidate(); });
                grow.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        ((RevealBox) box).setFrozen(false);
                        visible[0] = Float.MAX_VALUE;
                        box.invalidateOutline();
                        for (int c = 0; c < box.getChildCount(); c++) {
                            box.getChildAt(c).animate().alpha(1f).setDuration(300).setStartDelay(c * 40L).start();
                        }
                        box.postDelayed(() -> {
                            box.setLayerType(View.LAYER_TYPE_NONE, null);
                            card.reveal.accept(DeliveryCard.MOVE);
                        }, 450);
                    }
                });
                grow.start();
            }).start();
        });
        main.postDelayed(close, card.showMs);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    /**
     * Polls, downloads and decodes run at background priority, so they never take a core from the app being watched
     * (its player and its UI) on a quad-core TV.
     */
    private static final java.util.concurrent.ThreadFactory LOW = r -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        r.run();
    }, "alerts");
    private final ExecutorService io = Executors.newSingleThreadExecutor(LOW);
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
    /** Weather pop-ups (a theme's "weather_alerts"): warnings already shown, the last rain alert, the last morning. */
    private boolean weatherAlerts;
    private final Set<String> seenWarnings = new HashSet<>();
    private long weatherAt, rainAlertAt;
    private String morningShown = "";
    private boolean teamAlerts, incidentAlerts, kickoffReminders, tennisAlerts, ufcAlerts, boxingAlerts, everywhere;
    /** sky_alerts: goals, cards, VAR, full time and F1 as the line-up card's small cards (ScoreCard). */
    private boolean skyAlerts;
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

    /** A player on a line-up page: his photo (or null) and shirt number. */
    private static final class Face {
        final android.graphics.Bitmap photo;
        final String number;
        Face(android.graphics.Bitmap photo, String number) { this.photo = photo; this.number = number; }
    }

    /** A channel logo to fetch, shown on a dark capsule; ChipImg once fetched. */
    private static final class Chip {
        final String url, name;
        Chip(String url, String name) { this.url = url; this.name = name; }
    }

    private static final class ChipImg {
        final android.graphics.Bitmap bitmap;
        ChipImg(android.graphics.Bitmap bitmap) { this.bitmap = bitmap; }
    }

    /** A fetched Pic. */
    private static final class Img {
        final android.graphics.Bitmap bitmap;
        final boolean wide;
        Img(android.graphics.Bitmap bitmap, boolean wide) { this.bitmap = bitmap; this.wide = wide; }
    }

    /** A goal's score: the side that scored ("home" or "away", or null for none) has its number roll up to it. */
    private static final class Score {
        final int home, away;
        final String side;
        Score(int home, int away, String side) { this.home = home; this.away = away; this.side = side; }
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

    /** New episodes of the series watched here and new films on the TV's apps, every two hours (ReleaseAlerts). */
    private final Runnable releases = new Runnable() {
        @Override public void run() {
            io.execute(() -> ReleaseAlerts.check(GoalWatchService.this, false));
            main.postDelayed(this, 2 * 3600_000L);
        }
    };

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
            stillOverApps = Config.themeFlag(this, "still_over_apps", false);
            animateOverApps = Config.themeFlag(this, "animate_over_apps", false);
            liteOverApps = Config.themeFlag(this, "lite_over_apps", false);
            teamAlerts = Config.themeFlag(this, "team_alerts", false);
            incidentAlerts = Config.themeFlag(this, "incident_alerts", false);
            kickoffReminders = Config.themeFlag(this, "kickoff_reminders", false);
            tennisAlerts = Config.themeFlag(this, "tennis_alerts", false);
            ufcAlerts = Config.themeFlag(this, "ufc_alerts", false);
            boxingAlerts = Config.themeFlag(this, "boxing_alerts", false);
            everywhere = Config.themeFlag(this, "alerts_everywhere", false);
            skyAlerts = Config.themeFlag(this, "sky_alerts", false);
            weatherAlerts = Config.themeFlag(this, "weather_alerts", false);
            if (Config.themeFlag(this, "alert_sounds", false)) loadSounds();
            // New episodes and films (ReleaseAlerts): first a few minutes after the start, then every two hours.
            main.removeCallbacks(releases);
            main.postDelayed(releases, 3 * 60_000L);
        }
        if (intent != null && intent.hasExtra("test_release")) io.execute(() -> ReleaseAlerts.check(this, true));
        if (intent != null && intent.hasExtra("dump_ui")) AppSearchService.dump();
        // adb only: a series opened in Debrid Stream through the helper, as Play does ("--es test_dsshow 'The Simpsons'").
        if (intent != null && intent.hasExtra("test_dsshow")) {
            String[] spec = intent.getStringExtra("test_dsshow").split("\\|");
            if (spec.length >= 3) AppSearchService.wantEpisode(Integer.parseInt(spec[1].trim()), Integer.parseInt(spec[2].trim()), spec.length > 3 ? spec[3] : null);
            main.post(() -> AppSearchService.search(this, ReleaseAlerts.DEBRID, spec[0], true));
        }
        if (intent != null && intent.hasExtra("test_tune")) AppSearchService.testTune(this, intent.getStringExtra("test_tune"));
        if (intent != null && intent.hasExtra("test_ambient")) {
            startActivity(new Intent(this, AmbientActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        if (intent != null && intent.hasExtra("test_search")) {
            AppSearchService.search(this, getString(R.string.trending_package), intent.getStringExtra("test_search"),
                    intent.getBooleanExtra("tv", false));
        }
        if (intent != null && intent.hasExtra("test_more")) testMore(intent.getStringExtra("test_more"));
        if (intent != null && intent.hasExtra("test_photo")) {
            // adb test: a player's Wikipedia photo, saved to the app's files to look at.
            String who = intent.getStringExtra("test_photo");
            io.execute(() -> {
                android.graphics.Bitmap b = PlayerPhotos.wikiPhoto(this, who);
                android.util.Log.i("LineupCard", "wiki photo " + who + ": " + (b == null ? "none" : b.getWidth() + "x" + b.getHeight()));
                if (b == null || getExternalFilesDir(null) == null) return;
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null), "photo-" + who.replace(' ', '_') + ".png"))) {
                    b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                } catch (java.io.IOException ignored) { }
            });
        }
        if (intent != null && intent.hasExtra("test_sky")) testSky();
        if (intent != null && intent.hasExtra("test_kickoff_card")) {
            // adb only: the kick-off as a score card (as when the line-ups are not out yet), Liverpool v Arsenal.
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.id = "kickoff-card-test"; g.league = "PL"; g.slug = "eng.1"; g.home = "LIV"; g.away = "ARS";
            g.homeName = "Liverpool"; g.awayName = "Arsenal"; g.venue = "Anfield"; g.state = 1;
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png";
            g.kickoff = System.currentTimeMillis() + 15 * 60_000L;
            io.execute(() -> {
                ScoreCard.Data d = kickoffCard(g);
                // A made-up match is in no guide: on Sky Sports Main Event, as the reminder test is, to show its channel.
                if (d.watch == null) {
                    String logoUrl = ScoresTicker.skyLogo("1301", null);
                    int px = Math.round(96 * getResources().getDisplayMetrics().density);
                    android.graphics.Bitmap logo = logoUrl == null ? null : HomeV2.trim(ScoresTicker.cachedLogo(this, logoUrl, px));
                    List<String> names = new ArrayList<>(Arrays.asList("Sky Sports Main Event"));
                    d.channelLogo = logo;
                    d.channel = "Sky Sports Main Event";
                    d.watch = () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
                }
                main.post(() -> ScoreCard.show(this, d));
            });
        }
        if (intent != null && intent.hasExtra("test_reminder")) {
            // adb only: an event reminder from the Sport page, as a STARTING card.
            try {
                skyReminder(new JSONObject().put("t", intent.getStringExtra("test_reminder"))
                        .put("c", "Sky Sports Main Event").put("l", ScoresTicker.skyLogo("1301", null))
                        .put("s", System.currentTimeMillis() + 5 * 60_000L), 5);
            } catch (org.json.JSONException ignored) { }
        }
        if (intent != null && intent.hasExtra("test_fixture")) {
            // adb only: "slug|tag|HOME|AWAY|ground": the line-up card for two clubs as if kicking off in 15 minutes, each
            // in its latest eleven in that competition (the last 30 days), e.g. "esp.1|LALIGA|BAR|RMA|Spotify Camp Nou".
            String[] f = intent.getStringExtra("test_fixture").split("\\|", -1);
            if (f.length >= 4) io.execute(() -> {
                ScoresTicker.Side[] sides = ScoresTicker.latestLineups(f[0], f[1], f[2], f[3], 30);
                android.util.Log.i("Sport", "test fixture " + f[2] + " v " + f[3] + (sides == null ? ": no line-ups" : ""));
                if (sides == null) return;
                ScoresTicker.Game g = new ScoresTicker.Game();
                g.id = "fixture-test-" + f[2] + f[3];
                g.slug = f[0]; g.league = f[1]; g.home = f[2]; g.away = f[3];
                g.homeName = sides[0].name; g.awayName = sides[1].name;
                g.homeLogo = sides[0].crest; g.awayLogo = sides[1].crest;
                g.venue = f.length > 4 && !f[4].isEmpty() ? f[4] : null;
                g.state = 1;
                g.kickoff = System.currentTimeMillis() + 15 * 60_000L;
                testLineups.put(g.id, sides);
                main.post(() -> enqueueReminder(g));
            });
        }
        if (intent != null && intent.hasExtra("test_render_live")) {
            // adb only: the Live Centre with its scoreboard folded out on the line-ups, photographed off screen.
            LiveCentre.renderScreen(this);
        }
        if (intent != null && intent.hasExtra("test_render_lineup")) {
            // adb only: the line-up card of a real game today (by a club's name), photographed page by page off screen.
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "shots");
            dir.mkdirs();
            LineupCard.renderTo = dir;
            String who = intent.getStringExtra("test_render_lineup").toLowerCase(java.util.Locale.ROOT);
            List<String> leagues = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.goal_alert_leagues)));
            leagues.addAll(Arrays.asList(LINEUP_LEAGUES));
            io.execute(() -> {
                for (ScoresTicker.Game g : ScoresTicker.load(leagues.toArray(new String[0]))) {
                    String names = ((g.homeName == null ? "" : g.homeName) + " " + (g.awayName == null ? "" : g.awayName)).toLowerCase(java.util.Locale.ROOT);
                    if (!names.contains(who)) continue;
                    main.post(() -> enqueueReminder(g));
                    return;
                }
            });
        }
        if (intent != null && intent.hasExtra("test_lineup")) {
            // adb only: the line-up card (or the kick-off card) for a real game today, found by a club's name.
            String who = intent.getStringExtra("test_lineup").toLowerCase(java.util.Locale.ROOT);
            List<String> leagues = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.goal_alert_leagues)));
            leagues.addAll(Arrays.asList(LINEUP_LEAGUES));
            io.execute(() -> {
                for (ScoresTicker.Game g : ScoresTicker.load(leagues.toArray(new String[0]))) {
                    String names = ((g.homeName == null ? "" : g.homeName) + " " + (g.awayName == null ? "" : g.awayName)).toLowerCase(java.util.Locale.ROOT);
                    if (!names.contains(who)) continue;
                    android.util.Log.i("Sport", "test line-up " + g.homeName + " v " + g.awayName);
                    main.post(() -> enqueueReminder(g));
                    return;
                }
            });
        }
        if (intent != null && intent.hasExtra("test_goal")) {
            // adb only (the service needs DUMP from outside): one sample alert now, to check the look over an app.
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "LIV"; g.away = "BHA"; g.homeScore = "2"; g.awayScore = "1";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/331.png";
            // Live, on Sky Sports Premier League, so its "Watch on" step shows too.
            g.state = 0;
            g.slug = "eng.1";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png";
            g.broadcasts.add("Sky Sports Premier League");
            enqueue(g, new String[]{"test", "Mohamed Salah", "67'", "home", ""}, false);
        }
        if (intent != null && intent.hasExtra("test_live_event")) {
            // adb only: with the Live Centre open, its game's latest substitution (else goal) folds out as if new;
            // "lineups": its line-ups, as if just announced.
            if ("lineups".equals(intent.getStringExtra("test_live_event"))) LiveCentre.testLineups(); else LiveCentre.testEvent();
        }
        if (intent != null && intent.hasExtra("test_render_highlights")) {
            // adb only: a finished game's highlights offered and played in the Live Centre, photographed off screen.
            LiveCentre.renderHighlights(this, intent.getStringExtra("test_render_highlights"));
        }
        if (intent != null && intent.hasExtra("test_highlights")) {
            // adb only: with the Live Centre open, a finished game's official highlights offered (a club's name, or "").
            LiveCentre.testHighlights(intent.getStringExtra("test_highlights"));
        }
        if (intent != null && intent.hasExtra("test_yt")) {
            // adb only: whether YouTube plays these videos here (comma separated ids), to files/yt.txt; nothing on screen.
            YouTubeBox.test(this, intent.getStringExtra("test_yt").split(","));
        }
        if (intent != null && intent.hasExtra("test_tuya")) {
            // adb only: what the Tuya cloud project sees, and whether its cameras stream, to files/tuya.txt.
            TuyaCloud.test(this);
        }
        if (intent != null && intent.hasExtra("test_render_video")) {
            // adb only: the scoreboard's animations, frame by frame, to files/video/ (nothing on screen).
            LiveCentre.renderVideo(this);
        }
        if (intent != null && intent.hasExtra("test_render_boards")) {
            // adb only: a Live Centre scoreboard for each competition, drawn to files/boards.png (nothing on screen);
            // "all": every page of stats and facts, a game under way or over and one to come, each folded out.
            LiveCentre.renderBoards(this, "all".equals(intent.getStringExtra("test_render_boards")));
        }
        if (intent != null && intent.hasExtra("test_render_bar")) {
            // adb only: the Sports page's Live Centre bar drawn to a file (nothing on screen): files/bar.png.
            try {
                float d = getResources().getDisplayMetrics().widthPixels / 1920f;
                android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Press  ");
                int at = b.length();
                b.append("  ");
                int ok = Math.round(36 * d);
                android.graphics.drawable.BitmapDrawable okd = new android.graphics.drawable.BitmapDrawable(getResources(),
                        PagesV2.SportPage.okKey(Config.themeString(this, "ok_button", "tcl"), ok));
                okd.setBounds(0, 0, ok, ok);
                b.setSpan(new PagesV2.SportPage.MidSpan(okd), at, at + 2, 0);
                b.append("   for   ");
                int st = b.length();
                b.append("  ");
                android.graphics.Bitmap sb = LiveCentre.stampPicture(this, Math.round(34 * d));
                android.graphics.drawable.BitmapDrawable sd = new android.graphics.drawable.BitmapDrawable(getResources(), sb);
                sd.setBounds(0, 0, sb.getWidth(), sb.getHeight());
                b.setSpan(new PagesV2.SportPage.MidSpan(sd), st, st + 2, 0);
                android.widget.TextView v = new android.widget.TextView(this);
                v.setText(b);
                v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 20 * d);
                v.setTextColor(0xFF1A1D24);
                v.setGravity(android.view.Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xEBFFFFFF);
                bg.setCornerRadius(30 * d);
                v.setBackground(bg);
                int w = Math.round(536 * d), hgt = Math.round(60 * d);
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(hgt, android.view.View.MeasureSpec.EXACTLY));
                v.layout(0, 0, w, hgt);
                android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, hgt, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(out);
                cv.drawColor(0xFF13196A);
                v.draw(cv);
                java.io.File f = new java.io.File(getExternalFilesDir(null), "bar.png");
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); }
            } catch (Exception ignored) {
            }
        }
        if (intent != null && intent.hasExtra("test_goal_other")) {
            // adb only: a goal in a game no club or competition of the alerts is followed in (one of today's live games
            // from Home's score pills, else a Serie A sample): on Home the new-style bar, over an app nothing.
            io.execute(() -> {
                ScoresTicker.Game pick = null;
                for (ScoresTicker.Game x : ScoresTicker.load(homeLeagues(this))) {
                    if (followsAnywhere(this, x)) continue;
                    if (pick == null || x.state == 0 && pick.state != 0) pick = x;
                }
                if (pick == null) {
                    pick = new ScoresTicker.Game();
                    pick.league = "SERIE A"; pick.slug = "ita.1"; pick.home = "COMO"; pick.away = "VEN";
                    pick.homeName = "Como"; pick.awayName = "Venezia";
                }
                ScoresTicker.Game g = pick;
                g.state = 0;
                g.homeScore = "1";
                g.awayScore = "0";
                main.post(() -> enqueue(g, new String[]{"test-other", "Nico Paz", "34'", "home", ""}, false));
            });
        }
        if (intent != null && intent.hasExtra("test_ft")) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "MAN"; g.away = "BOU"; g.homeScore = "3"; g.awayScore = "1";
            g.homeName = "Manchester United"; g.awayName = "Bournemouth";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/360.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/349.png";
            enqueue(g, new String[]{"test-ft", "", "", "", "ft"}, false);
        }
        if (intent != null && intent.hasExtra("test_f1_result")) {
            // adb only: the last race's full result (Jolpica's, whichever Grand Prix it was).
            f1Board(false, null, "RACE", null, null, 0);
        }
        if (intent != null && intent.hasExtra("test_f1_grid")) {
            // adb only: this weekend's grid from its qualifying, as if lights out were in 15 minutes.
            io.execute(() -> {
                F1Results.Weekend w = F1Results.weekend(null);
                F1Results.Session q = null;
                for (F1Results.Session x : w == null ? new ArrayList<F1Results.Session>() : w.sessions) if ("Qual".equals(x.abbr) && !x.top.isEmpty()) q = x;
                if (q == null) return;
                F1Results.Session found = q;
                main.post(() -> f1Board(true, w.name, "RACE", found.top, found.logos, System.currentTimeMillis() + 15 * 60_000L));
            });
        }
        if (intent != null && intent.hasExtra("test_f1")) {
            io.execute(() -> {
                F1Results.Race r = F1Results.load(intent.getStringExtra("test_f1"));
                if (r != null) enqueueRace(r);
            });
        }
        if (intent != null && intent.hasExtra("test_f1_live")) {
            // The live session alerts, from a finished weekend's real order ("yyyyMMdd" of its race day).
            io.execute(() -> {
                F1Results.Weekend w = F1Results.weekend(intent.getStringExtra("test_f1_live"));
                if (w == null) return;
                main.post(() -> {
                    // As skyF1Updates shows them: pole, lights out, a new leader, a lap update.
                    String gp = F1Results.shortName(w.name == null ? "" : w.name).replace("Grand Prix", "GP");
                    for (F1Results.Session s : w.sessions) {
                        if ("Qual".equals(s.abbr) && !s.top.isEmpty()) skyPodium("POLE", gp, s.top, s.logos, "Pole");
                    }
                    for (F1Results.Session s : w.sessions) {
                        if (!"Race".equals(s.abbr) || s.top.size() < 3) continue;
                        skyF1("LIGHTS OUT", null, null, null, gp, "Live now", null);
                        skyLeader(s, 1, 23);
                        skyLeader(s, 0, 30);
                    }
                });
            });
        }
        if (intent != null && intent.hasExtra("test_live_centre")) {
            // adb only: the Live Centre, on the live game (a followed club's first), as OK on the Sports page opens it.
            String id = intent.getStringExtra("test_live_centre");
            if ("here".equals(id)) LiveCentre.openHere(this);
            else LiveCentre.open(this, id == null || id.isEmpty() ? null : id);
        }
        if (intent != null && intent.hasExtra("test_live_events")) {
            // adb only: a goal, a red card, a VAR decision, a penalty and full time in the open Live Centre.
            LiveCentre.testEvents();
        }
        if (intent != null && intent.hasExtra("test_popup")) {
            // adb only (adb's keys never reach the key helper): pop-up mode's "boxes", "pills", "app" or "leave".
            PopupFrame.test(intent.getStringExtra("test_popup"));
        }
        if (intent != null && intent.hasExtra("test_goal_clip")) {
            // adb only: a finished game's goal clip in the Live Centre, if it is open ("esp.1|<ESPN event id>").
            GoalClips.test(intent.getStringExtra("test_goal_clip"));
        }
        if (intent != null && intent.hasExtra("test_close")) {
            // adb only: closes the Live Centre and the F1 timing (adb's own Back never reaches the key helper).
            LiveCentre.close();
            F1Tower.close();
        }
        if (intent != null && intent.hasExtra("test_live_card")) {
            // adb only: the Sports page's Live Centre card with today's games, as if they were under way.
            LiveCentre.testCard = !"off".equals(intent.getStringExtra("test_live_card"));
        }
        if (intent != null && intent.hasExtra("test_f1_tower")) {
            // adb only: the F1 timing tower, over Sky Sports F1 as on a race weekend.
            F1Tower.open(this, !"stay".equals(intent.getStringExtra("test_f1_tower")));
        }
        if (intent != null && intent.hasExtra("test_breaking")) {
            // adb only: the first BBC News story as a breaking alert, exactly as one would pop up.
            io.execute(() -> {
                List<String[]> items = readNews(true, false);
                for (String[] item : items) {
                    if (!item[0].contains("NEWS")) continue;
                    main.post(() -> enqueueParts(breakingLine(item), newsSound(item)));
                    break;
                }
            });
        }
        if (intent != null && intent.hasExtra("test_news")) {
            io.execute(() -> {
                List<String[]> items = readNews(true, boxingAlerts);
                main.post(() -> {
                    Set<String> labels = new HashSet<>();
                    for (String[] item : items) {
                        if (!labels.add(item[0])) continue;
                        if (skyAlerts && "BOXING".equals(item[0])) skyBoxing(item); else enqueueParts(newsLine(item), newsSound(item));
                    }
                });
            });
        }
        if (intent != null && intent.hasExtra("test_delivery")) {
            // A phone's update read from a file in the app's folder (a food order or the car), shown as if it came in.
            String path = intent.getStringExtra("test_delivery");
            io.execute(() -> {
                try {
                    java.io.File f = new java.io.File(Config.dir(this), new java.io.File(path).getName());
                    JSONObject u = new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
                    main.post(() -> showDelivery(u));
                } catch (Exception ignored) {
                }
            });
        }
        if (intent != null && intent.hasExtra("test_moodart")) {
            // adb only: Surprise me's mood cards drawn into the app's folder (moodart-tv-0-p.png …), to look at them
            // without the screen on.
            io.execute(() -> {
                for (String kind : new String[]{"tv", "mv"}) {
                    for (int i = 0; i < (kind.equals("tv") ? EpisodeDice.MOODS.length : Surprise.MOODS.length); i++) {
                        for (String shape : new String[]{"p", "w"}) {
                            android.graphics.Bitmap b = MoodArt.draw(this, "mood:" + kind + ":" + i + ":" + shape, shape.equals("p") ? 420 : 960);
                            if (b == null) continue;
                            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(Config.dir(this),
                                    "moodart-" + kind + "-" + i + "-" + shape + ".png"))) {
                                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                            } catch (Exception ignored) {
                                // that one left out
                            }
                        }
                    }
                }
            });
        }
        if (intent != null && intent.hasExtra("test_watch")) {
            // Continue Watching samples: a film part-watched in Netflix, an episode in Debrid Stream ("clear" empties it).
            if ("clear".equals(intent.getStringExtra("test_watch"))) WatchHistory.test(this, null, null, null, 0, 0);
            else {
                WatchHistory.test(this, "com.netflix.ninja", "The Dark Knight", "", 0.42f, 152 * 60_000L);
                WatchHistory.test(this, "com.debridstream.tv", "Slow Horses", "S4:E2 Hello Goodbye", 0.65f, 48 * 60_000L);
                WatchHistory.test(this, "com.bskyb.skysports", "Arsenal v Liverpool · Premier League highlights", "", 0.3f, 22 * 60_000L);
            }
        }
        if (intent != null && intent.hasExtra("test_red")) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "UCL"; g.home = "ARS"; g.away = "PSG"; g.homeScore = "1"; g.awayScore = "1";
            g.homeName = "Arsenal"; g.awayName = "Paris Saint-Germain";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/160.png";
            enqueue(g, new String[]{"test-red", "Declan Rice", "81'", "home", ""}, true);
        }
        if (startedAt == 0) {
            startedAt = System.currentTimeMillis();
            main.post(poll);
        }
        // Home has come up: line-ups held over a video can be shown now.
        main.post(this::releaseHeld);
        // Running everywhere: started again by Android if it is ever stopped to free memory.
        return everywhere ? START_STICKY : START_NOT_STICKY;
    }

    /** The screen-off pause noted in the pop-up history (once each time). */
    private boolean offNoted;

    private void poll() {
        if (!everywhere && System.currentTimeMillis() - startedAt > MAX_RUN_MS) { stopSelf(); return; }
        // Events a person asked to be reminded of (v2's Sport page): "⏰ STARTING IN 5 MIN · title · channel".
        for (JSONObject r : Reminders.due(this, System.currentTimeMillis())) {
            long mins = Math.max(0, Math.round((r.optLong("s") - System.currentTimeMillis()) / 60_000.0));
            if (skyAlerts) { skyReminder(r, mins); continue; }
            List<Object> parts = new ArrayList<>();
            parts.add(bold(mins > 0 ? "⏰  STARTING IN " + mins + " MIN" : "⏰  STARTING NOW"));
            parts.add(10);
            parts.add(r.optString("t"));
            if (!r.optString("c").isEmpty()) {
                parts.add(12);
                parts.add(new Chip(r.optString("l").isEmpty() ? null : r.optString("l"), r.optString("c")));
            }
            enqueueParts(parts, "reminder");
        }
        android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
        if (power != null && !power.isInteractive()) {
            // Screen off: read nothing, look again in a minute (the screensaver counts as on).
            if (!offNoted) AlertLog.note(this, "screen off: scores not read until it is on");
            offNoted = true;
            main.postDelayed(poll, 60_000L);
            return;
        }
        if (offNoted) AlertLog.note(this, "screen on: reading the scores again");
        offNoted = false;
        long now = System.currentTimeMillis();
        // Only the competitions worth interrupting a programme for (goal_alert_leagues), and with team_alerts the
        // leagues and cups the goal_alert_teams clubs play in, for those clubs' games only.
        String[] alertLeagues = getResources().getStringArray(R.array.goal_alert_leagues);
        Set<String> alertTags = new HashSet<>();
        for (String l : alertLeagues) alertTags.add(l.contains("|") ? l.split("\\|", 2)[1].trim() : l.trim());
        Set<String> clubs = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.goal_alert_teams)));
        List<String> all = new ArrayList<>(Arrays.asList(alertLeagues));
        if (teamAlerts) all.addAll(Arrays.asList(getResources().getStringArray(R.array.team_alert_leagues)));
        // For the line-ups too: England's games and Real Madrid's and Barcelona's Supercopa.
        if (kickoffReminders) all.addAll(Arrays.asList(LINEUP_LEAGUES));
        // Every other competition on Home's score pills, every game: announced on Home only (homeOnly()).
        all.addAll(Arrays.asList(homeLeagues(this)));
        String[] leagues = all.toArray(new String[0]);
        boolean incidentsNow = incidentAlerts && polls++ % 3 == 0;
        boolean tennisNow = tennisAlerts && now - tennisAt >= TENNIS_MS;
        boolean ufcNow = ufcAlerts && now - ufcAt >= (ufcLive ? 2 * 60_000L : 15 * 60_000L);
        if (tennisNow) tennisAt = now;
        boolean weatherNow = weatherAlerts && now - weatherAt >= WEATHER_MS;
        if (weatherNow) weatherAt = now;
        String lat = getString(R.string.weather_latitude), lon = getString(R.string.weather_longitude);
        String warnFeed = getString(R.string.weather_warnings_feed), warnArea = getString(R.string.weather_warnings_area);
        if (ufcNow) ufcAt = now;
        // The live F1 alerts include the result panel, so the race alone is read only without them.
        boolean f1 = getResources().getBoolean(R.bool.f1_results_popup) && !f1Live;
        boolean news = (newsAlerts || boxingAlerts) && now - newsAt >= NEWS_MS;
        if (news) newsAt = now;
        io.execute(() -> {
            // One entry per game: a club's Champions League game is in both lists.
            Map<String, ScoresTicker.Game> byId = new java.util.LinkedHashMap<>();
            for (ScoresTicker.Game g : ScoresTicker.load(leagues)) {
                boolean wanted = alertTags.contains(g.league) || clubs.contains(g.homeId) || clubs.contains(g.awayId)
                        || LINEUP_TEAMS.contains(g.homeId) || LINEUP_TEAMS.contains(g.awayId) || homeSlugs(this).contains(g.slug);
                if (wanted && g.id != null && !byId.containsKey(g.id)) byId.put(g.id, g);
            }
            List<ScoresTicker.Game> games = new ArrayList<>(byId.values());
            // The Premier League's clubs, for the Champions League games with line-ups (read here, off the main thread).
            if (kickoffReminders) englishClubs = ScoresTicker.englishClubs();
            if (kickoffReminders && Config.themeFlag(this, "lineup_card", false)) prefetchLineups(games, now);
            Map<String, List<String[]>> incidents = new HashMap<>();
            // A Home-only game's incidents are read only while Home is up (no need to ask otherwise).
            if (incidentsNow) for (ScoresTicker.Game g : games) {
                if (g.state == 0 && (homeInFront || !homeOnly(g))) incidents.put(g.id, ScoresTicker.incidents(g));
            }
            List<MoreSports.Result> tennis = tennisNow ? MoreSports.tennis() : null;
            List<MoreSports.Result> ufc = ufcNow ? MoreSports.ufc() : null;
            List<String[]> warnings = weatherNow && !warnFeed.isEmpty() ? WeatherAlerts.warnings(warnFeed, warnArea) : null;
            WeatherAlerts.Outlook outlook = weatherNow && !lat.isEmpty() ? WeatherAlerts.outlook(lat, lon) : null;
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
                    if (finished) main.postDelayed(() -> enqueueRace(race), DELAY_MS);
                }
                if (weekend != null) f1Updates(weekend);
                if (headlines != null) newsUpdates(headlines);
                if (warnings != null) for (String[] w : warnings) if (seenWarnings.add(w[0])) enqueueParts(warningLine(w), "weather");
                if (outlook != null) weatherUpdates(outlook);
                if (tennis != null) { otherUpdates(tennis, tennisPrimed); if (!tennis.isEmpty()) tennisPrimed = true; }
                if (ufc != null) {
                    ufcLive = false;
                    for (MoreSports.Result r : ufc) ufcLive |= "in".equals(r.state);
                    otherUpdates(ufc, ufcPrimed);
                    if (!ufc.isEmpty()) ufcPrimed = true;
                }
                for (ScoresTicker.Game g : games) {
                    if (kickoffReminders && lineupGame(g) && g.state == 1 && g.kickoff - now > 0
                            && g.kickoff - now <= REMIND_MS && reminded.add(g.id)) {
                        enqueueReminder(g);
                    }
                    List<String[]> found = incidents.get(g.id);
                    if (found == null) continue;
                    boolean first = incidentGames.add(g.id);
                    for (String[] inc : found) {
                        if (seen.add(inc[0]) && !first) main.postDelayed(() -> {
                            if (skyAlerts) skyIncident(g, inc); else enqueueFootball(g, incidentHead(inc), "incident");
                        }, DELAY_MS);
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
                        AlertLog.note(this, "full time seen: " + AlertLog.game(g) + " (card in a minute)");
                        main.postDelayed(() -> enqueue(g, new String[]{"FT|" + g.id, "", "", "", "ft"}, false), DELAY_MS);
                    }
                    for (String[] goal : g.goals) {
                        if (!seen.add(goal[0])) continue;
                        if (primed) {
                            AlertLog.note(this, "goal seen: " + AlertLog.game(g) + ", " + goal[1] + " " + goal[2] + " (card in a minute)");
                            main.postDelayed(() -> enqueue(g, goal, false), DELAY_MS);
                        } else if (g.state == 0) {
                            AlertLog.note(this, "goal already in on the first read, not announced: " + AlertLog.game(g) + ", " + goal[1] + " " + goal[2]);
                        }
                    }
                    for (String[] card : g.reds) {
                        if (!seen.add(card[0])) continue;
                        if (primed) {
                            AlertLog.note(this, "red card seen: " + AlertLog.game(g) + ", " + card[1] + " " + card[2] + " (card in a minute)");
                            main.postDelayed(() -> enqueue(g, card, true), DELAY_MS);
                        }
                    }
                }
                if (!games.isEmpty()) primed = true;
                main.postDelayed(poll, live ? POLL_MS : 5 * POLL_MS);
            });
        });
    }

    /** Competitions read for the line-ups alone: England's internationals, and the Spanish Supercopa. */
    private static final String[] LINEUP_LEAGUES = {"fifa.friendly|FRIENDLY", "uefa.nations|UNL", "fifa.worldq.uefa|WCQ",
            "uefa.euroq|EUROQ", "fifa.world|WORLD CUP", "uefa.euro|EURO", "esp.super_cup|SUPERCOPA"};
    /** Barcelona, Real Madrid and England (ESPN's ids): every game of theirs has its line-ups. */
    private static final Set<String> LINEUP_TEAMS = new HashSet<>(Arrays.asList("83", "86", "448"));
    private static final Set<String> LINEUP_CUPS = new HashSet<>(Arrays.asList("PL", "FA CUP", "EFL CUP"));
    private volatile Set<String> englishClubs = new HashSet<>();

    /**
     * Which games get the kick-off reminder with the line-ups: every Premier League, FA Cup and EFL Cup game, every
     * game of Real Madrid, Barcelona and England, and the Champions League games an English club plays in.
     */
    private boolean lineupGame(ScoresTicker.Game g) {
        if (LINEUP_CUPS.contains(g.league)) return true;
        if (LINEUP_TEAMS.contains(g.homeId) || LINEUP_TEAMS.contains(g.awayId)) return true;
        if ("UCL".equals(g.league)) return englishClubs.contains(g.homeId) || englishClubs.contains(g.awayId);
        return false;
    }

    // ---- F1 ----------------------------------------------------------------------------------------

    /**
     * Compares the weekend with the last read: a session starting, a new leader, every ten laps of a race, and the
     * end (the race's top ten, else the top three). The first read only learns where things stand.
     */
    private void f1Updates(F1Results.Weekend w) {
        // Always the score cards' style: the older pills sat among the football scores on Home and read as one of them.
        skyF1Updates(w);
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
            boolean fight = entry.equals(BOXING_FEED);
            // Breaking news only: the local feed is left out, and only a feed's lead story counts.
            if (!fight && parts[0].contains("/manchester/")) continue;
            List<String[]> stories = NewsPanel.read(parts[0].trim());
            for (int i = 0; i < Math.min(fight ? 5 : 1, stories.size()); i++) {
                String[] st = stories.get(i);
                // Every lead story is learnt (so a restart does not replay it); only a breaking one pops up.
                out.add(new String[]{parts.length > 1 ? parts[1].trim() : "NEWS", st[0], st[2] == null ? st[0] : st[2],
                        fight || breaking(st) ? "pop" : "", st[1]});
            }
        }
        return out;
    }

    /**
     * Breaking news: a feed's new lead story, published within the last 45 minutes, that is news rather than a
     * feature (a quiz, a video, a live page's daily round-up, "in pictures").
     */
    private static boolean breaking(String[] st) {
        String t = st[0].toLowerCase(java.util.Locale.ROOT);
        for (String f : new String[]{"watch:", "quiz", "in pictures", "listen:", "podcast", "week in", "your questions",
                "what we know", "the papers", "newspaper headlines", "how to ", "explained", "analysis:", "opinion"}) {
            if (t.startsWith(f) || t.contains(" " + f)) return false;
        }
        if (st.length < 4 || st[3] == null) return false;
        try {
            long at = java.time.ZonedDateTime.parse(st[3], java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
            return System.currentTimeMillis() - at < 45 * 60_000L;
        } catch (Exception e) {
            return false;
        }
    }

    /** A story new to the top five of its feed pops up; at most one per feed each read, the first read only learns. */
    private void newsUpdates(List<String[]> items) {
        Set<String> shownFeeds = new HashSet<>();
        for (String[] item : items) {
            if (seenNews.add(item[2]) && newsPrimed && "pop".equals(item[3]) && shownFeeds.add(item[0])) {
                if (skyAlerts && "BOXING".equals(item[0])) { skyBoxing(item); continue; }
                enqueueParts(breakingLine(item), newsSound(item));
            }
        }
        if (!items.isEmpty()) newsPrimed = true;
    }

    private static String newsSound(String[] item) {
        return "BOXING".equals(item[0]) ? "fight" : "news";
    }

    private static String otherSound(MoreSports.Result r) {
        return "UFC".equals(r.tour) ? "fight" : "tennis";
    }

    /**
     * A breaking story's line: the BBC's blocks with NEWS or SPORT, BREAKING after them (with room on both sides, so
     * it never runs into the logo: it was put between the colour and the logo, touching it), then the headline.
     */
    private List<Object> breakingLine(String[] item) {
        List<Object> line = newsLine(item);
        if ("BOXING".equals(item[0])) return line;
        int at = 0;
        while (at < line.size() && !(line.get(at) instanceof android.graphics.drawable.Drawable)) at++;
        if (at >= line.size()) at = 0;
        line.add(at + 1, 12);
        line.add(at + 2, bold("BREAKING"));
        return line;
    }

    /** The BBC's blocks with NEWS or SPORT, then the headline; boxing's with a glove. */
    private List<Object> newsLine(String[] item) {
        float d = getResources().getDisplayMetrics().density;
        List<Object> parts = new ArrayList<>();
        boolean sport = item[0].contains("SPORT") || "BOXING".equals(item[0]);
        parts.add(new Tint(sport ? 0xFFFFC21A : 0xFFD42020));
        parts.add(Badges.bbc(d, sport ? "SPORT" : item[0].replace("BBC ", ""), sport ? 0xFF8A6100 : 0xFFB80000));
        if ("BOXING".equals(item[0])) { parts.add(8); parts.add(bold("🥊  BOXING")); }
        parts.add(8);
        parts.add(item[1]);
        return parts;
    }

    // ---- weather -----------------------------------------------------------------------------------

    /** Rain on its way (at most every three hours), and the day's forecast once each morning. */
    private void weatherUpdates(WeatherAlerts.Outlook o) {
        long now = System.currentTimeMillis();
        String place = getString(R.string.weather_place);
        if (o.rainFrom > 0 && now - rainAlertAt > 3 * 60 * 60_000L) {
            rainAlertAt = now;
            List<Object> parts = new ArrayList<>();
            parts.add(weatherIcon(61));
            parts.add(8);
            parts.add(bold("Rain from about " + java.time.Instant.ofEpochMilli(o.rainFrom).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
            parts.add(8);
            parts.add(new Small(place.toUpperCase(java.util.Locale.getDefault())));
            enqueueParts(parts, "weather");
        }
        java.time.LocalDateTime local = java.time.LocalDateTime.now();
        String today = local.toLocalDate().toString();
        if (!today.equals(morningShown) && local.getHour() >= 6 && local.getHour() < 11) {
            morningShown = today;
            enqueueParts(forecastLine(o, place), "weather");
        }
    }

    /** "[icon] Today 12° / 6°  Showers  ☂ 70%  MANCHESTER". */
    private List<Object> forecastLine(WeatherAlerts.Outlook o, String place) {
        List<Object> parts = new ArrayList<>();
        parts.add(weatherIcon(o.code));
        parts.add(8);
        parts.add(bold("Today  " + o.high + "° / " + o.low + "°"));
        parts.add(8);
        parts.add(WeatherLine.describe(o.code));
        if (o.rainChance >= 0) { parts.add(8); parts.add("☂ " + o.rainChance + "%"); }
        parts.add(8);
        parts.add(new Small(place.toUpperCase(java.util.Locale.getDefault())));
        return parts;
    }

    /** The weather line's own icon, as a picture in the pill. */
    private android.graphics.drawable.Drawable weatherIcon(int code) {
        // The light-background version: its own colours, with grey clouds that read on the silver glass.
        android.graphics.drawable.Drawable d = getDrawable(WeatherLine.lightIcon(code, true));
        return d == null ? null : d.mutate();
    }

    /** "[Met Office icon] YELLOW WIND WARNING  Greater Manchester  from 0300 Tue 27 Oct to 2359 Tue 27 Oct". */
    private List<Object> warningLine(String[] w) {
        String title = w[1];
        String colour = title.contains(" ") ? title.substring(0, title.indexOf(' ')) : "";
        int of = title.indexOf("warning of "), aff = title.indexOf(" affecting");
        String kind = of >= 0 && aff > of ? title.substring(of + "warning of ".length(), aff) : "";
        String head = (colour + " " + kind + " warning").trim().toUpperCase(java.util.Locale.ROOT);
        SpannableStringBuilder s = new SpannableStringBuilder("⚠️  " + head);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int tint = colour.equalsIgnoreCase("red") ? 0xFFC81E1E : colour.equalsIgnoreCase("amber") ? 0xFFC2410C : 0xFF8A6100;
        s.setSpan(new ForegroundColorSpan(tint), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        List<Object> parts = new ArrayList<>();
        if (w[3] != null) { parts.add(new Pic(w[3])); parts.add(8); }
        parts.add(s);
        parts.add(8);
        parts.add(getString(R.string.weather_warnings_area));
        int valid = w[2].indexOf("valid ");
        if (valid >= 0) { parts.add(8); parts.add(new Small(w[2].substring(valid + "valid ".length()))); }
        return parts;
    }

    // ---- football extras, tennis and UFC -----------------------------------------------------------

    /** "⏰ 15 MIN · crest v crest · 15:00 · league". */
    private List<Object> kickoffLine(ScoresTicker.Game g) {
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

    static boolean premier(String url) {
        return url != null && url.matches(".*/leaguelogos/soccer/500(-dark)?/23\\.png");
    }

    /**
     * A competition's logo for the alerts. The Premier League's is its lion alone, as on v1: ESPN's carries the
     * words under it, so its light version is cut off above them, and drawn white on v2's Home glass.
     */
    private android.graphics.Bitmap leagueBitmap(String url, int px) {
        if (url == null) return null;
        if (!premier(url)) {
            // White lettering and stars (the Champions League's ball) in the pill's ink on the light glass.
            android.graphics.Bitmap b = ScoresTicker.cachedLogo(this, lightLogo(url), px);
            return b == null || onLane() ? b : inked(b);
        }
        android.graphics.Bitmap full = ScoresTicker.cachedLogo(this, url.replace("/500-dark/", "/500/"), px * 2);
        return full == null ? null : lionOnly(full, onLane());
    }

    /** The picture down to where its first block ends (the lion, above the gap before the words), white if asked. */
    static android.graphics.Bitmap lionOnly(android.graphics.Bitmap b, boolean white) {
        int w = b.getWidth(), h = b.getHeight();
        int[] row = new int[w];
        int top = -1, bottom = h - 1, left = w, right = -1;
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            boolean any = false;
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) <= 24) continue;
                any = true;
                if (x < left) left = x;
                if (x > right) right = x;
            }
            if (any && top < 0) top = y;
            if (!any && top >= 0) { bottom = y - 1; break; }
        }
        if (top < 0 || right < left) return b;
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1)
                .copy(android.graphics.Bitmap.Config.ARGB_8888, true);
        if (white) {
            int[] px = new int[out.getWidth() * out.getHeight()];
            out.getPixels(px, 0, out.getWidth(), 0, 0, out.getWidth(), out.getHeight());
            for (int i = 0; i < px.length; i++) px[i] = (px[i] & 0xFF000000) | 0xFFFFFF;
            out.setPixels(px, 0, out.getWidth(), 0, 0, out.getWidth(), out.getHeight());
        }
        return out;
    }

    /** ESPN's league logos come in a white version for dark backgrounds; the alerts' glass is light (not on v2's Home). */
    private String lightLogo(String url) {
        return url == null || onLane() ? url : url.replace("/500-dark/", "/500/");
    }

    /** A match or fight that ended (any state to post) or a final or main event that began (pre to in). */
    private void otherUpdates(List<MoreSports.Result> results, boolean primedYet) {
        for (MoreSports.Result r : results) {
            String before = otherStates.put(r.id, r.state);
            if (!primedYet || before == null || before.equals(r.state)) continue;
            boolean card = skyAlerts && "UFC".equals(r.tour);
            if ("post".equals(r.state)) {
                if (card) skyFight(r, false); else enqueueParts(resultLine(r), otherSound(r), false, otherChannel(r));
            } else if ("pre".equals(before) && "in".equals(r.state) && (r.mainEvent || "Final".equals(r.round))) {
                if (card) skyFight(r, true); else enqueueParts(startLine(r), otherSound(r), false, otherChannel(r));
            }
        }
    }

    /** The tour's mark: the UFC logo, or ATP / WTA on their colours. */
    private Object mark(MoreSports.Result r) {
        float d = getResources().getDisplayMetrics().density;
        if ("UFC".equals(r.tour)) return new Pic(MoreSports.UFC_LOGO, true);
        return "WTA".equals(r.tour) ? Badges.tile(d, "WTA", 0xFF4B2683, 0xFFFFFFFF) : Badges.tile(d, "ATP", 0xFF0B2A5B, 0xFFFFFFFF);
    }

    /** Where tennis and the UFC are on in Britain: Sky Sports Tennis, and TNT Sports. */
    private static TvGuide.Watch otherChannel(MoreSports.Result r) {
        return "UFC".equals(r.tour)
                ? new TvGuide.Watch(new ArrayList<>(Arrays.asList("TNT Sports 1", "TNT Sports 2")), ScoresTicker.skyLogo("3625", null))
                : new TvGuide.Watch(new ArrayList<>(Arrays.asList("Sky Sports Tennis")), ScoresTicker.skyLogo("1284", null));
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
        parts.add(new Small(ufc ? "def." : "beats"));
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

    /** A finished match or fight for testMore, flags from ESPN's country set. */
    private static MoreSports.Result sample(String tour, String winner, String wFlag, String loser, String lFlag, String score, String round, String event) {
        MoreSports.Result r = new MoreSports.Result();
        r.id = "test-" + tour; r.tour = tour; r.state = "post"; r.round = round; r.event = event; r.mainEvent = "UFC".equals(tour);
        r.winner = winner; r.loser = loser; r.score = score;
        if ("Moicano".equals(winner)) { r.winnerId = "3028863"; r.winnerFull = "Renato Moicano"; r.loserId = "5144007"; r.loserFull = "Tom Nolan"; }
        r.winnerFlag = "https://a.espncdn.com/i/teamlogos/countries/500/" + wFlag + ".png";
        r.loserFlag = "https://a.espncdn.com/i/teamlogos/countries/500/" + lFlag + ".png";
        return r;
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
            enqueueFootball(g, incidentHead(new String[]{"t", "🧤  Pen saved", "Manuel Neuer", "88'"}), "incident");
        }
        if (all || "weather".equals(what)) {
            String lat = getString(R.string.weather_latitude), lon = getString(R.string.weather_longitude);
            io.execute(() -> {
                WeatherAlerts.Outlook o = WeatherAlerts.outlook(lat, lon);
                main.post(() -> {
                    if (o != null) enqueueParts(forecastLine(o, getString(R.string.weather_place)), "weather");
                    List<Object> rain = new ArrayList<>();
                    rain.add(weatherIcon(61)); rain.add(8); rain.add(bold("Rain from about 15:30")); rain.add(8);
                    rain.add(new Small(getString(R.string.weather_place).toUpperCase(java.util.Locale.getDefault())));
                    enqueueParts(rain, "weather");
                    enqueueParts(warningLine(new String[]{"t", "Yellow warning of wind affecting North West England",
                            "Yellow warning of wind affecting North West England: Greater Manchester valid from 0300 Tue 27 Oct to 2359 Tue 27 Oct",
                            "https://data.consumer-digital.api.metoffice.gov.uk/v1/warnings/rss/image/yellow-wind.png"}), "weather");
                });
            });
        }
        if (all || "kickoff".equals(what)) {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.league = "PL"; g.home = "ARS"; g.away = "CHE";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/363.png";
            g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png";
            g.kickoff = System.currentTimeMillis() + REMIND_MS;
            g.id = "test";
            // The guide part of the test looks up a real listing: Liverpool v Arsenal, 1 November 2026, 16:30 UK.
            g.home = "LIV"; g.away = "ARS"; g.homeName = "Liverpool"; g.awayName = "Arsenal";
            g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
            g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/359.png";
            g.tvAt = 1_793_554_200_000L;
            // Both in red: Arsenal's second colour for the card's "Away kit".
            g.homeColor = 0xFFC8102E; g.awayColor = 0xFFEF0107; g.awayAlt = 0xFF0B1E4B;
            g.venue = "Anfield"; g.slug = "eng.1";
            enqueueReminder(g);
        }
        io.execute(() -> {
            List<MoreSports.Result> tennis = all || "tennis".equals(what) ? MoreSports.tennis() : new ArrayList<>();
            List<MoreSports.Result> ufc = all || "ufc".equals(what) ? MoreSports.ufc() : new ArrayList<>();
            List<String[]> boxing = all || "boxing".equals(what) ? readNews(false, true) : new ArrayList<>();
            main.post(() -> {
                MoreSports.Result final1 = null, main1 = null;
                for (MoreSports.Result r : tennis) if ("post".equals(r.state) && "Final".equals(r.round)) { final1 = r; break; }
                // The latest finished main event: next weekend's card has no result yet.
                for (MoreSports.Result r : ufc) if (r.mainEvent && "post".equals(r.state)) { main1 = r; break; }
                // No final or main event in ESPN's feeds this week: made-up ones, to see the look.
                if (final1 == null && (all || "tennis".equals(what))) final1 = sample("ATP", "Djokovic", "srb", "Humbert", "fra", "4-6 6-3 6-4", "Final", "Paris Masters");
                if (main1 == null && (all || "ufc".equals(what))) main1 = sample("UFC", "Moicano", "bra", "Nolan", "irl", "KO/TKO · R2 3:12", "Main card", "UFC Fight Night");
                if (final1 != null) enqueueParts(resultLine(final1), otherSound(final1), false, otherChannel(final1));
                if (main1 != null && skyAlerts) { skyFight(main1, true); skyFight(main1, false); }
                else if (main1 != null) { enqueueParts(startLine(main1), otherSound(main1), false, otherChannel(main1)); enqueueParts(resultLine(main1), otherSound(main1), false, otherChannel(main1)); }
                if (!boxing.isEmpty() && skyAlerts) skyBoxing(boxing.get(0));
                else if (!boxing.isEmpty()) enqueueParts(newsLine(boxing.get(0)), "fight");
            });
        });
    }

    // ---- the alert ---------------------------------------------------------------------------------

    /**
     * Whether this service announces a game itself (its competition is a goal_alert_leagues one, or one of its clubs
     * is a goal_alert_teams club): then its goals and full time are the new-style bar; Home's score line announces
     * any other game's goal in its own pill.
     */
    static boolean follows(Context c, ScoresTicker.Game g) {
        if (g == null) return false;
        // Every competition on Home's score pills: the long bar on Home too (homeOnly()).
        if (g.slug != null && homeSlugs(c).contains(g.slug)) return true;
        return followsAnywhere(c, g);
    }

    /**
     * Every competition on Home's score pills (score_leagues) that is not a goal_alert_leagues one: the Championship,
     * the SPFL, the Europa League, LaLiga, Serie A, the Bundesliga, Ligue 1. On Home their goals, red cards, incidents
     * and full times are the long new-style bar, as the Premier League's are, so the score pills never drop their own
     * older alert; over an app they make no alert at all (only the followed competitions and clubs do).
     */
    private static String[] homeLeagues;
    private static Set<String> homeSlugs;

    static synchronized String[] homeLeagues(Context c) {
        if (homeLeagues != null) return homeLeagues;
        Set<String> followed = new HashSet<>();
        for (String l : c.getResources().getStringArray(R.array.goal_alert_leagues)) followed.add(l.split("\\|", 2)[0].trim());
        List<String> out = new ArrayList<>();
        Set<String> slugs = new HashSet<>();
        for (String l : c.getResources().getStringArray(R.array.score_leagues)) {
            String slug = l.split("\\|", 2)[0].trim();
            if (followed.contains(slug) || !slugs.add(slug)) continue;
            out.add(l);
        }
        homeSlugs = slugs;
        homeLeagues = out.toArray(new String[0]);
        return homeLeagues;
    }

    static Set<String> homeSlugs(Context c) {
        if (homeSlugs == null) homeLeagues(c);
        return homeSlugs;
    }

    /** A game announced only while Home is in front: a Championship or LaLiga game no followed club plays in. */
    private boolean homeOnly(ScoresTicker.Game g) {
        return g != null && g.slug != null && homeSlugs(this).contains(g.slug) && !followsAnywhere(this, g);
    }

    /** A game this service announces everywhere: its competition is a goal_alert_leagues one, or a club is followed. */
    private static boolean followsAnywhere(Context c, ScoresTicker.Game g) {
        for (String l : c.getResources().getStringArray(R.array.goal_alert_leagues)) {
            String tag = l.contains("|") ? l.split("\\|", 2)[1].trim() : l.trim();
            if (tag.equals(g.league)) return true;
        }
        Set<String> clubs = new HashSet<>(Arrays.asList(c.getResources().getStringArray(R.array.goal_alert_teams)));
        clubs.addAll(LINEUP_TEAMS);
        return clubs.contains(g.homeId) || clubs.contains(g.awayId);
    }

    private void enqueue(ScoresTicker.Game g, String[] goal, boolean red) {
        // A Championship or LaLiga game: only while Home is in front.
        if (homeOnly(g) && !homeInFront) {
            AlertLog.note(this, "not shown (a Home-only game, Home not in front): " + AlertLog.game(g) + " " + goal[1] + " " + goal[2]);
            return;
        }
        if (skyAlerts) {
            skyFootball(g, red ? "red" : "ft".equals(goal[4]) ? "ft" : "goal", goal[1], goal[2], goal[3], goal[4]);
            return;
        }
        boolean scored = !red && !"ft".equals(goal[4]) && ("home".equals(goal[3]) || "away".equals(goal[3]));
        enqueueFootball(g, headline(goal, red), red ? "red" : "ft".equals(goal[4]) ? "fulltime" : "goal", scored ? goal[3] : null);
    }

    private void enqueueFootball(ScoresTicker.Game g, CharSequence head, String sound) {
        enqueueFootball(g, head, sound, null);
    }

    /**
     * Fetches both crests and the competition's logo (cached on disk by the scores line), then queues the alert;
     * scoredBy is the side whose goal it is, for the score's roll.
     */
    private void enqueueFootball(ScoresTicker.Game g, CharSequence head, String sound, String scoredBy) {
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            android.graphics.Bitmap home = g.homeLogo == null ? null : ScoresTicker.cachedLogo(this, g.homeLogo, px);
            android.graphics.Bitmap away = g.awayLogo == null ? null : ScoresTicker.cachedLogo(this, g.awayLogo, px);
            android.graphics.Bitmap league = leagueBitmap(g.leagueLogo != null ? g.leagueLogo : leagueLogoFor(g), px);
            // While the game is on: where to watch it, for the pill's second step.
            Object[] watch = g.state == 0 ? watchFor(g) : null;
            main.post(() -> {
                queue.add(new Object[]{head, g, home, away, league, sound, scoredBy, watch});
                showNext();
            });
        });
    }

    /** ESPN's competition logos by league (slug, or the short name the scores line uses), when a board gave none. */
    private static final Map<String, Integer> LEAGUE_IDS = new HashMap<>();
    static {
        String[][] ids = {{"eng.1", "23"}, {"PL", "23"}, {"EPL", "23"}, {"eng.2", "24"}, {"CHAMP", "24"}, {"eng.fa", "40"},
                {"FA CUP", "40"}, {"FAC", "40"}, {"eng.league_cup", "41"}, {"EFL", "41"}, {"CARABAO", "41"},
                {"uefa.champions", "2"}, {"UCL", "2"}, {"uefa.europa", "2310"}, {"UEL", "2310"},
                {"uefa.europa.conf", "20296"}, {"UECL", "20296"}, {"esp.1", "15"}, {"LALIGA", "15"}, {"ita.1", "12"},
                {"SERIE A", "12"}, {"ger.1", "10"}, {"BUNDESLIGA", "10"}, {"fra.1", "9"}, {"LIGUE 1", "9"},
                {"sco.1", "45"}, {"SPFL", "45"}};
        for (String[] e : ids) LEAGUE_IDS.put(e[0], Integer.parseInt(e[1]));
    }

    static String leagueLogoFor(ScoresTicker.Game g) {
        Integer id = g.slug != null ? LEAGUE_IDS.get(g.slug) : null;
        if (id == null && g.league != null) id = LEAGUE_IDS.get(g.league.toUpperCase(java.util.Locale.ROOT));
        return id == null ? null : "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/" + id + ".png";
    }

    /**
     * The kick-off reminder, with the two line-ups as its second page when they are out: the pill shows first, then
     * flips up to both starting elevens with each player's picture.
     */
    private void enqueueReminder(ScoresTicker.Game g) {
        List<Object> parts = kickoffLine(g);
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        String[] channels = getResources().getStringArray(R.array.football_tv_channels);
        io.execute(() -> {
            ScoresTicker.Side[] preset = g.id == null ? null : testLineups.remove(g.id);
            ScoresTicker.Side[] sides = preset != null ? preset : "test".equals(g.id) ? testSides(g) : ScoresTicker.lineups(g);
            // "Live on" and the channel, when a British channel lists the game.
            // The British channel (its UHD one first, where TiviMate has it); else the game's channel in the playlist,
            // with its logo; else where it is on abroad.
            TvGuide.Channel tv = TvGuide.uhd(this, TvGuide.find(channels, g.homeName, g.awayName, g.tvAt != 0 ? g.tvAt : g.kickoff));
            PlaylistSport.Where inPlaylist = tv == null ? PlaylistSport.where(this, g.homeName, g.awayName, g.slug) : null;
            if (inPlaylist != null) tv = new TvGuide.Channel(inPlaylist.name, inPlaylist.logo);
            if (tv == null) tv = TvGuide.orAbroad(null, g);
            android.util.Log.i("Sport", "kick-off " + g.home + " v " + g.away + " on " + (tv == null ? "-" : tv.name + " " + tv.logo)
                    + (inPlaylist != null ? " (playlist)" : ""));
            // The last page, after the line-ups: the two clubs, "LIVE ON" and the channel.
            List<Object> live = null;
            if (tv != null) {
                live = new ArrayList<>();
                live.add(new Pic(g.homeLogo)); live.add(4); live.add(bold(g.home)); live.add(6); live.add("v"); live.add(6);
                live.add(new Pic(g.awayLogo)); live.add(4); live.add(bold(g.away)); live.add(14);
                live.add(bold("📺  LIVE ON")); live.add(8); live.add(new Chip(tv.logo, tv.name));
            }
            // The line-ups out, and the theme asks for it ("lineup_card"): the line-up card in place of the pill.
            if (sides != null && Config.themeFlag(this, "lineup_card", false)) {
                LineupCard.Data card = lineupCard(g, sides, tv, channels);
                main.post(() -> LineupCard.show(this, card));
                return;
            }
            // No line-ups yet (or no line-up card): the kick-off as a score card.
            if (skyAlerts) {
                ScoreCard.Data d = kickoffCard(g);
                main.post(() -> ScoreCard.show(this, d));
                return;
            }
            List<Object> ready = resolve(parts, px);
            List<Object> livePage = live == null ? null : resolve(live, px);
            // Every picture at once, and no more than fifteen seconds for all of them: on a slow connection a player
            // whose photo has not come shows his shirt number instead.
            Map<String, android.graphics.Bitmap> pictures = new java.util.concurrent.ConcurrentHashMap<>();
            if (sides != null) {
                List<String> urls = new ArrayList<>();
                for (ScoresTicker.Side side : sides) {
                    if (side.crest != null) urls.add(side.crest);
                    for (String[] p : side.players) if (p[1] != null && !urls.contains(p[1])) urls.add(p[1]);
                }
                ExecutorService pool = Executors.newFixedThreadPool(6, LOW);
                List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
                for (String url : urls) jobs.add(() -> {
                    android.graphics.Bitmap b = ScoresTicker.cachedLogo(this, url, px);
                    if (b != null) pictures.put(url, b);
                    return null;
                });
                // ESPN has photos for only some players: the rest from TheSportsDB, filed under the same key.
                for (ScoresTicker.Side side : sides) for (String[] p : side.players) {
                    if (p[1] == null || p.length < 5) continue;
                    jobs.add(() -> {
                        if (pictures.containsKey(p[1])) return null;
                        android.graphics.Bitmap b = PlayerPhotos.find(this, p[4], side.name, px);
                        if (b != null) pictures.putIfAbsent(p[1], b);
                        return null;
                    });
                }
                try { pool.invokeAll(jobs, 15, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
                pool.shutdownNow();
            }
            main.post(() -> {
                queue.add(new Object[]{ready, "reminder", sides, pictures, livePage});
                showNext();
            });
        });
    }

    /**
     * Everything the line-up card shows, fetched here off the main thread: each player's whole cut-out and flag
     * (TheSportsDB; ESPN's headshot, then a figure in the club's colours, when it has none), both crests, the
     * competition, the channel and what OK watches. The kits: each side in its first colour, unless the away side's
     * clashes with the home side's, when it wears its second ("Away kit"). No more than twenty seconds for it all.
     */
    private LineupCard.Data lineupCard(ScoresTicker.Game g, ScoresTicker.Side[] sides, TvGuide.Channel tv, String[] channels) {
        LineupCard.Data d = new LineupCard.Data();
        int logoPx = Math.round(48 * getResources().getDisplayMetrics().density);
        String leagueLogo = g.leagueLogo != null ? g.leagueLogo : leagueLogoFor(g);
        if (leagueLogo != null) d.leagueLogo = ScoresTicker.cachedLogo(this, leagueLogo, logoPx);
        if (g.kickoff > 0) d.kickoff = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(new java.util.Date(g.kickoff));
        d.kickoffAt = g.kickoff;
        d.venue = g.venue;
        d.competition = g.league == null && g.slug == null ? null : ScoresTicker.competition(g);
        // Where it is on only where a guide lists the game (watchFor), never guessed; no channel at all otherwise.
        Object[] w = watchFor(g);
        if (w != null) {
            @SuppressWarnings("unchecked") List<String> on = (List<String>) w[0];
            d.channel = on.isEmpty() ? null : on.get(0);
            d.channelLogo = (android.graphics.Bitmap) w[1];
        }
        if (w != null && w.length > 2) {
            d.watch = (Runnable) w[2];
        } else if (w != null) {
            @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
            android.graphics.Bitmap logo = (android.graphics.Bitmap) w[1];
            d.watch = () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
        }
        if (w == null && g.id != null && g.id.startsWith("fixture-test-")) {
            // adb's made-up fixture is in no guide: on Sky Sports Main Event, as the other tests are, to show the channel.
            String logoUrl = ScoresTicker.skyLogo("1301", null);
            android.graphics.Bitmap logo = logoUrl == null ? null : HomeV2.trim(ScoresTicker.cachedLogo(this, logoUrl, logoPx));
            List<String> names = new ArrayList<>(Arrays.asList("Sky Sports Main Event"));
            d.channel = "Sky Sports Main Event";
            d.channelLogo = logo;
            d.watch = () -> AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
        }
        d.remote = Config.themeString(this, "ok_button", "");
        // The kits they wear today, from their Wikipedia articles (this season's): the away side in its second or third
        // kit when its first is too close to the home side's. ESPN's club colours when the articles cannot be read.
        Kits.Kit[] homeKits = Kits.of(this, sides[0].name), awayKits = Kits.of(this, sides.length > 1 ? sides[1].name : null);
        Kits.Kit homeWears = Kits.wearing(homeKits, awayKits, false), awayWears = Kits.wearing(awayKits, homeKits, true);
        int homeKit = g.homeColor, awayKit = g.awayColor;
        boolean clash = homeKit != 0 && awayKit != 0 && LineupCard.distance(homeKit, awayKit) < 120;
        ExecutorService pool = Executors.newFixedThreadPool(6, LOW);
        List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
        // The players are looked up in TheSportsDB one at a time (its free service turns away a burst of them), each
        // once more after a failure; their pictures then come in parallel. At most forty-five seconds for it all.
        List<Runnable> lookups = new ArrayList<>();
        for (int i = 0; i < sides.length; i++) {
            ScoresTicker.Side side = sides[i];
            LineupCard.Team t = new LineupCard.Team();
            boolean away = i == 1;
            t.name = side.name != null ? side.name : side.abbr;
            t.formation = side.formation == null ? "" : side.formation;
            Kits.Kit wears = away ? awayWears : homeWears;
            t.kit = wears != null ? wears.body : away ? (clash && g.awayAlt != 0 ? g.awayAlt : awayKit) : homeKit;
            t.sleeves = wears != null ? wears.sleeves : t.kit;
            Kits.Kit home = (away ? awayKits : homeKits).length > 0 ? (away ? awayKits : homeKits)[0] : null;
            t.photoKit = home != null && home.known() ? home.body : (away ? awayKit : homeKit);
            t.photoSleeves = home != null && home.known() ? home.sleeves : t.photoKit;
            String crest = side.crest != null ? side.crest : away ? g.awayLogo : g.homeLogo;
            if (crest != null) jobs.add(() -> { t.crest = ScoreCard.onNavy(ScoresTicker.cachedLogo(this, crest, logoPx * 2)); return null; });
            for (String[] p : side.players) {
                LineupCard.Player pl = new LineupCard.Player();
                String full = p.length > 4 && p[4] != null ? p[4] : p[0];
                int sp = full.lastIndexOf(' ');
                pl.first = sp > 0 ? full.substring(0, sp) : "";
                pl.last = sp > 0 ? full.substring(sp + 1) : full;
                pl.number = p[2] == null ? "" : p[2];
                pl.position = p.length > 3 && p[3] != null ? p[3] : "";
                t.players.add(pl);
                lookups.add(() -> {
                    // The Premier League's own photo first (this season's, in this season's kit); else TheSportsDB's,
                    // mostly already fetched when the line-ups came out (prefetchLineups), a refused lookup asked once more.
                    PlayerPhotos.Cutout cut = premierLeague(full, t.name);
                    if (cut == null) cut = PlayerPhotos.cutout(this, full, t.name);
                    if (cut.url == null && !PlayerPhotos.cooling()) {
                        try { Thread.sleep(2_000); } catch (InterruptedException e) { return; }
                        cut = PlayerPhotos.cutout(this, full, t.name);
                    }
                    if (cut.url == null || cut.url.isEmpty()) {
                        PlayerPhotos.Cutout older = olderPremierLeague(full, t.name);
                        if (older != null) cut = older;
                    }
                    PlayerPhotos.Cutout found = cut;
                    pool.execute(() -> {
                        pl.photo = PlayerPhotos.photo(this, found);
                        if (pl.photo == null && p[1] != null) pl.photo = ScoresTicker.cachedLogo(this, p[1], 300);
                        String flag = LineupCard.flagUrl(found.nation);
                        // No nationality in TheSportsDB (or one its flags don't cover): ESPN's, so every player has a flag.
                        if (flag == null) {
                            String[] espn = PlayerPhotos.espnNation(this, g.slug, p.length > 5 ? p[5] : null);
                            if (espn != null) {
                                flag = LineupCard.flagUrl(espn[0]);
                                if (flag == null && !espn[1].isEmpty()) flag = espn[1];
                            }
                        }
                        if (flag != null) pl.flag = ScoresTicker.cachedLogo(this, flag, 80);
                    });
                });
            }
            if (away) d.away = t; else d.home = t;
        }
        long until = System.currentTimeMillis() + 60_000L;
        for (java.util.concurrent.Callable<Void> job : jobs) pool.submit(job);
        // Home and away in turn, so one side's slow lookups cannot leave the other with no photos at all.
        List<Runnable> turns = new ArrayList<>();
        int half = lookups.size() / 2;
        for (int i = 0; i < half || half + i < lookups.size(); i++) {
            if (i < half) turns.add(lookups.get(i));
            if (half + i < lookups.size()) turns.add(lookups.get(half + i));
        }
        for (Runnable lookup : turns) {
            if (System.currentTimeMillis() > until) break;
            lookup.run();
        }
        pool.shutdown();
        try { pool.awaitTermination(Math.max(1, until - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) { }
        pool.shutdownNow();
        return d;
    }

    /** adb test only: the elevens of the last Premier League game, so the page can be seen on any day. */
    private static ScoresTicker.Side[] testSides(ScoresTicker.Game g) {
        // The two clubs' own latest elevens, under their crests; any recent game's when they cannot be found.
        ScoresTicker.Side[] own = ScoresTicker.latestLineups("eng.1", "PL", g.home, g.away, 21);
        if (own != null) {
            own[0].abbr = g.home; own[0].crest = g.homeLogo;
            own[1].abbr = g.away; own[1].crest = g.awayLogo;
            return own;
        }
        ScoresTicker.Game last = ScoresTicker.lastFinished("eng.1", "PL", 7);
        return last == null ? null : ScoresTicker.lineups(last);
    }

    /** The line's pictures fetched: Pics to Imgs, Chips to channel logos drawn for the light pill. Off the main thread. */
    private List<Object> resolve(List<Object> parts, int px) {
        List<Object> ready = new ArrayList<>();
        for (Object o : parts) {
            if (o instanceof Chip) {
                android.graphics.Bitmap logo = ((Chip) o).url == null ? null : ScoresTicker.cachedLogo(this, ((Chip) o).url, px * 4);
                ready.add(logo != null ? new ChipImg(onLane() ? logo : inked(logo)) : bold(((Chip) o).name));
            } else if (o instanceof Pic) {
                Pic pic = (Pic) o;
                ready.add(new Img(picture(pic, px), pic.wide));
            } else ready.add(o);
        }
        return ready;
    }

    /**
     * A channel logo made for dark screens, for the light pill: its white and grey lettering that stands on the
     * transparent background drawn in the pill's dark ink; lettering inside a coloured box (Sky's blue and red) stays
     * white, and the colours and transparent parts stay as they are. A white shape counts as standing on the
     * background when any of its pixels touches a transparent one.
     */
    private static android.graphics.Bitmap inked(android.graphics.Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        boolean[] light = new boolean[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            light[i] = a > 40 && max - min < 40 && max > 120;
        }
        boolean[] seen = new boolean[px.length];
        int[] stack = new int[px.length];
        for (int start = 0; start < px.length; start++) {
            if (!light[start] || seen[start]) continue;
            // One white shape, and whether it borders the transparent background.
            int top = 0, count = 0;
            boolean onBackground = false;
            stack[top++] = start;
            seen[start] = true;
            int[] members = new int[64];
            while (top > 0) {
                int i = stack[--top];
                if (count == members.length) members = java.util.Arrays.copyOf(members, count * 2);
                members[count++] = i;
                int x = i % w, y = i / w;
                int[] around = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                for (int n : around) {
                    if (n < 0) { onBackground = true; continue; }
                    if ((px[n] >>> 24) <= 40) onBackground = true;
                    else if (light[n] && !seen[n]) { seen[n] = true; stack[top++] = n; }
                }
            }
            if (!onBackground) continue;
            for (int k = 0; k < count; k++) px[members[k]] = (px[members[k]] & 0xFF000000) | 0x1A1D24;
        }
        return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888);
    }

    /**
     * A line's picture, fetched. A wordmark (wide) loses the empty margin around it: F1's and the UFC's logos come as
     * squares with the mark in a thin band across the middle, which drew them tiny.
     */
    private android.graphics.Bitmap picture(Pic pic, int px) {
        if (pic.url == null) return null;
        if (premier(pic.url)) return leagueBitmap(pic.url, px);
        android.graphics.Bitmap b = ScoresTicker.cachedLogo(this, pic.url, px * (pic.wide ? 4 : 1));
        return pic.wide && b != null ? HomeV2.trim(b) : b;
    }

    /** A team's black mark (Mercedes' star) drawn light, so it shows on the dark F1 glass; colours stay. */
    private static android.graphics.Bitmap lifted(android.graphics.Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (a > 40 && max < 90 && max - min < 40) px[i] = (c & 0xFF000000) | 0xE6E6E6;
        }
        return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888);
    }

    /** Fetches the line's pictures, then queues it with its sound. */
    private void enqueueParts(List<Object> parts, String sound) {
        enqueueParts(parts, sound, false);
    }

    /** As enqueueParts; f1Live adds the "Watch on" step (Sky Sports F1 and the rest) after the line. */
    private void enqueueParts(List<Object> parts, String sound, boolean f1Live) {
        enqueueParts(parts, sound, f1Live, null);
    }

    /** As enqueueParts, with the channel it is on (on), whose logo and OK close the line. */
    private void enqueueParts(List<Object> parts, String sound, boolean f1Live, TvGuide.Watch on) {
        int px = Math.round(LOGO_DP * getResources().getDisplayMetrics().density);
        io.execute(() -> {
            Object[] watch = on != null ? watchOn(on) : null;
            if (f1Live) {
                ScoresTicker.Game f1 = new ScoresTicker.Game();
                f1.leagueLogo = F1Results.F1_LOGO;
                watch = watchOn(TvGuide.watch(this, new String[0], f1));
            }
            final Object[] watching = watch;
            List<Object> ready = new ArrayList<>();
            for (Object o : parts) {
                if (o instanceof Pic) {
                    Pic pic = (Pic) o;
                    ready.add(new Img(picture(pic, px), pic.wide));
                }
                else ready.add(o);
            }
            main.post(() -> {
                queue.add(watching == null ? new Object[]{ready, sound} : new Object[]{ready, sound, watching});
                showNext();
            });
        });
    }

    /**
     * Where to watch a football game: the British channel (UHD first where TiviMate has it), tuned in TiviMate; when
     * no British channel lists it, its channel in the playlist (TiviMate's and Kleva TV's), played in Kleva TV:
     * {names, logo} or {names, logo, what OK does}. Off the main thread.
     */
    /**
     * Where a game is on, only where a guide says so: Sky's (the British channel, its UHD form first where TiviMate
     * has one), else a broadcaster's own guide in the IPTV playlist. Never guessed from who holds the league's rights
     * (a 3pm Premier League game is on no British channel): null, and the card shows no channel at all.
     */
    private Object[] watchFor(ScoresTicker.Game g) {
        String[] channels = getResources().getStringArray(R.array.football_tv_channels);
        TvGuide.Channel uk = TvGuide.find(channels, g.homeName, g.awayName, g.tvAt != 0 ? g.tvAt : g.kickoff);
        if (uk != null) {
            TvGuide.Channel up = TvGuide.uhd(this, uk);
            List<String> names = new ArrayList<>();
            names.add(up.name);
            if (uk.name != null && !uk.name.equals(up.name)) names.add(uk.name);
            return watchOn(new TvGuide.Watch(names, up.logo));
        }
        return playlistWatch(g.homeName, g.awayName, g.slug);
    }

    /** A game or fight found in the playlist's broadcasters' guides: {names, logo, OK}; null when not found there. */
    private Object[] playlistWatch(String home, String away, String slug) {
        PlaylistSport.Where p = PlaylistSport.where(this, home, away, slug);
        if (p == null || !p.sure || p.name == null) return null;
        int px = Math.round(96 * getResources().getDisplayMetrics().density);
        android.graphics.Bitmap logo = p.logo == null ? null : HomeV2.trim(ScoresTicker.cachedLogo(this, p.logo, px));
        List<String> names = new ArrayList<>();
        names.add(p.name);
        return new Object[]{names, logo, p.ok};
    }

    /** Where a game is on, for the Live Centre: {names, logo} or {names, logo, OK}; null when nowhere (or not running). */
    static Object[] watchForGame(Context c, ScoresTicker.Game g) {
        GoalWatchService s = running(c);
        return s == null || g == null ? null : s.watchFor(g);
    }

    /** The service, started first when it is not running (off the main thread: waits up to three seconds for it). */
    private static GoalWatchService running(Context c) {
        GoalWatchService s = self;
        if (s != null || c == null) return s;
        startEverywhere(c.getApplicationContext());
        for (int i = 0; i < 15 && self == null; i++) {
            try { Thread.sleep(200); } catch (InterruptedException e) { return self; }
        }
        return self;
    }

    /** Sky Sports F1 (UHD where TiviMate has it), for the timing tower: {names, logo}; null when not in the guide. */
    static Object[] f1Watch(Context c) {
        GoalWatchService s = running(c);
        if (s == null) return null;
        ScoresTicker.Game f1 = new ScoresTicker.Game();
        f1.leagueLogo = F1Results.F1_LOGO;
        return s.watchOn(TvGuide.watch(s, new String[0], f1));
    }

    /** {names, logo} for the "Watch on" step, the channel's logo fetched; null without anywhere to watch. */
    private Object[] watchOn(TvGuide.Watch w) {
        if (w == null || w.names.isEmpty()) return null;
        int px = Math.round(96 * getResources().getDisplayMetrics().density);
        // Sky's logos come on a square with room around them: cut to the logo, so it shows at its size and sharp.
        android.graphics.Bitmap logo = w.logo == null ? null : HomeV2.trim(ScoresTicker.cachedLogo(this, w.logo, px));
        return new Object[]{w.names, logo};
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
        boolean software = calm() || lite();
        host = new android.widget.FrameLayout(software ? new SoftwareWindow(this) : this);
        // A fixed size, set once: a window that changes size over a playing video is reallocated and re-planned
        // by the display, which shows as a hitch; this one only ever changes what it draws.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(Math.round(widthDp * d), Math.round(heightDp * d),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not FLAG_NOT_TOUCHABLE: Android 12+ caps such an overlay at 80% opacity, which let the picture
                // through the glass. A TV has no touch screen, and not focusable keeps every key with the app.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | (software ? 0 : WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = Math.round(32 * d);
        // Above the screensaver's clock and caption while it runs.
        lp.y = Math.round((dreaming ? 92 : 30) * d);
        if (onLane()) {
            // v2's Home: over the score bar, its width (1728 of 1920), with the bar's top edge.
            int sw = getResources().getDisplayMetrics().widthPixels;
            lp.width = Math.round(sw * 1728f / 1920f);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.x = 0;
            // Centred on the score bar's line (23 dp down), so the alert sits exactly where the pills were.
            lp.y = Math.round((23 - PILL_DP / 2f) * d);
        }
        try {
            getSystemService(WindowManager.class).addView(host, lp);
            laneShown(onLane());
            // Over an app the alerts have no opening movement of their own: the window rises a little and fades in.
            if (calm()) PopupMotion.in(host, 10 * d);
        } catch (Exception e) {
            host = null;
        }
    }

    /**
     * Over another app the alert is drawn by the CPU. A window the graphics chip draws gets compressed buffers that
     * the TCL's spare display planes cannot show, so the TV composes the app's layer and the alert on the GPU while
     * it is up, and the video underneath misses frames whenever that starts and stops. A plain buffer goes on a plane
     * of its own, and the app keeps its hardware composition. Android picks software drawing for a window with no
     * parent from the application's hardware-acceleration flag, read through the root view's context: this one says no.
     */
    private static final class SoftwareWindow extends android.content.ContextWrapper {
        private final android.content.pm.ApplicationInfo info;

        SoftwareWindow(android.content.Context base) {
            super(base);
            info = new android.content.pm.ApplicationInfo(base.getApplicationInfo());
            info.flags &= ~android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED;
        }

        @Override public android.content.pm.ApplicationInfo getApplicationInfo() { return info; }
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
    /**
     * The alerts' glass, every kind of it, with no rim or edge line: over a picture an outline round the pill reads as a
     * border, and the alerts are meant to sit on it bare.
     */
    private android.graphics.drawable.Drawable glass(float radius) {
        return rimless(glassWithRim(radius));
    }

    /** A drawable (a layer list of shapes) with every shape's stroke taken off; the same drawable, for chaining. */
    static android.graphics.drawable.Drawable rimless(android.graphics.drawable.Drawable dr) {
        if (dr instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable l = (android.graphics.drawable.LayerDrawable) dr;
            for (int i = 0; i < l.getNumberOfLayers(); i++) rimless(l.getDrawable(i));
        } else if (dr instanceof GradientDrawable) {
            ((GradientDrawable) dr).setStroke(0, 0);
        }
        return dr;
    }

    private android.graphics.drawable.Drawable glassWithRim(float radius) {
        // On v2's Home: the score pills' own clear glass, as the alert stands in their place.
        if (darkGlass) {
            GradientDrawable dark = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF22A2C31, 0xF2141518});
            dark.setCornerRadius(radius);
            return dark;
        }
        if (onLane()) return ScoresTicker.laneGlass(this, laneTint);
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
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base, grain, sheen});
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

    /** Since when the next alert has waited for a card to go (a stale one is not waited for past WAIT_MAX_MS). */
    private long waitingSince;
    static final long WAIT_MAX_MS = 90_000L;

    @SuppressWarnings("unchecked")
    private void showNext() {
        if (shown != null || queue.isEmpty()) return;
        if (ScoreCard.busy() || LineupCard.isShowing()) {
            long now = android.os.SystemClock.uptimeMillis();
            if (waitingSince == 0) {
                waitingSince = now;
                AlertLog.note(this, "alert waiting: " + (ScoreCard.busy() ? "a score card" : "the line-up card") + " is up");
            }
            if (now - waitingSince < WAIT_MAX_MS) { main.postDelayed(this::showNext, 1_500L); return; }
            AlertLog.note(this, "alert waited 90 s for a card to go: shown all the same");
        }
        waitingSince = 0;
        Object[] polled = queue.poll();
        AlertLog.note(this, "alert shown: " + describe(polled));
        current = polled;
        // A race result on v2's Home runs through the score bar's place as pills, a page at a time.
        Object[] next = polled.length == 1 && polled[0] instanceof F1Results.Race && onLane()
                ? raceLane((F1Results.Race) polled[0]) : polled;
        boost = 1f;
        laneTint = 0;
        darkGlass = false;
        if (next.length == 1 && next[0] instanceof F1Results.Race) {
            showRace((F1Results.Race) next[0]);
            return;
        }
        if (next.length == 2 && next[0] instanceof DeliveryCard.Card) {
            showCard((DeliveryCard.Card) next[0], (String) next[1]);
            return;
        }
        List<Object> parts;
        boolean wide;
        String sound;
        ScoresTicker.Side[] sides = null;
        Map<String, android.graphics.Bitmap> pictures = null;
        List<Object> livePage = null;
        Object[] watch = null;
        List<List<Object>> more = null;
        if (next.length == 2 || next.length == 3 || next.length == 5 || next.length == 6) {
            parts = (List<Object>) next[0];
            sound = (String) next[1];
            wide = true;
            if ("f1".equals(sound)) {
                laneTint = 0xFFE10600;
                // Over an app F1 has Sky Sports F1's look: dark glass, white lettering, the lap in red. On Home it
                // stands in the score bar's place in the pills' glass, as the football does.
                darkGlass = !onLane();
            }
            if (next.length == 3) watch = (Object[]) next[2];
            if (next.length == 6) more = (List<List<Object>>) next[5];
            if (next.length >= 5) {
                sides = (ScoresTicker.Side[]) next[2];
                pictures = (Map<String, android.graphics.Bitmap>) next[3];
                livePage = (List<Object>) next[4];
                if (stillOnly() && (sides != null || livePage != null)) {
                    // Over a playing video: one still pill, the kick-off and the channel together; the line-ups
                    // wait for a pause or Home, under the two clubs and "LINE-UPS".
                    if (sides != null) {
                        List<Object> head = new ArrayList<>(livePage != null && livePage.size() > 9 ? livePage.subList(0, 9) : parts);
                        head.add(14);
                        head.add(bold("📋  LINE-UPS"));
                        held = new Object[]{head, sides, pictures, System.currentTimeMillis()};
                    }
                    if (livePage != null && livePage.size() >= 3) {
                        parts = new ArrayList<>(parts);
                        parts.add(14);
                        parts.addAll(livePage.subList(livePage.size() - 3, livePage.size()));
                    }
                    sides = null;
                    livePage = null;
                }
            }
        } else {
            sound = (String) next[5];
            ScoresTicker.Game g = (ScoresTicker.Game) next[1];
            ScoresTicker.Game now = g.id == null ? null : latest.get(g.id);
            if (now != null) g = now;
            // Crests stand in for the team names: crest, score, crest.
            Object score;
            String side = next.length > 6 ? (String) next[6] : null;
            int hs = parse(g.homeScore), as = parse(g.awayScore);
            // A goal on v2's Home is the bar's news: bigger words and crests.
            if (side != null && onLane()) boost = 1.3f;
            if (side != null && hs >= 0 && as >= 0) {
                score = new Score(hs, as, side);
            } else {
                SpannableStringBuilder plain = new SpannableStringBuilder(g.homeScore + "–" + g.awayScore);
                plain.setSpan(new StyleSpan(Typeface.BOLD), 0, plain.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                score = plain;
            }
            // The competition's logo closes the line; its short name when ESPN has no logo.
            Object league = next.length > 4 && next[4] != null ? next[4] : new Small(g.league);
            // The competition opens the line, as on Sky: [competition] ⚽ Scorer 43' [crest] 1–1 [crest].
            parts = Arrays.asList(league, 10, next[0], 10, next[2], 6, score, 6, next[3]);
            wide = false;
            if (next.length > 7) watch = (Object[]) next[7];
        }
        // Where to watch it closes the line itself: the channel's logo and the remote's OK, which opens it.
        Object[] inline = watch != null && sides == null && livePage == null && !stillOnly() ? watch : null;
        if (inline != null) {
            parts = new ArrayList<>(parts);
            parts.addAll(watchParts(inline));
        }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = pillBox(parts, d);
        int h = Math.round(PILL_DP * d);

        // With line-ups to show, the window is sized for their page from the start: it never changes size.
        // Line-up pages are pills too: the same window as any wide line.
        ensureHost(sides != null || livePage != null || wide || inline != null ? 940 : 520, 46);
        if (host == null) return;
        boolean lane = onLane();
        if (lane) box.setGravity(Gravity.CENTER);
        host.addView(box, new android.widget.FrameLayout.LayoutParams(
                lane ? android.widget.FrameLayout.LayoutParams.MATCH_PARENT : android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h,
                lane ? Gravity.TOP : Gravity.BOTTOM | Gravity.START));
        shown = box;
        play(sound);
        // A goal's number rolls up once the pill has opened and its words are in (at once when it does not animate).
        if (scoreRoll != null) {
            Runnable roll = scoreRoll;
            scoreRoll = null;
            main.postDelayed(roll, calm() ? 0 : 1_700);
        }
        // A small glass dot pops in at the corner, the pill then stretches out to the right with its round ends kept
        // (an animated rounded outline, clipped in hardware), and only then does the text fade in.
        final float radius = corner(h);
        final float[] visible = {h};
        ((RevealBox) box).visible = visible;
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                // Anchored at the left: the pill grows out to the right from the dot.
                int right = Math.min(v.getWidth(), Math.round(visible[0]));
                o.setRoundRect(0, 0, right, v.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
        if (calm()) {
            // Over a playing app: no animation at all (see calm()), the pill is simply there.
            visible[0] = Float.MAX_VALUE;
        } else {
        for (int c = 0; c < box.getChildCount(); c++) box.getChildAt(c).setAlpha(0f);
        box.setAlpha(0f);
        box.setScaleX(0.3f);
        box.setScaleY(0.3f);
        box.post(() -> {
            box.setPivotX(radius);
            box.setPivotY(box.getHeight() / 2f);
            box.setLayerType(animLayer(), null);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).withEndAction(() -> {
                ((RevealBox) box).setFrozen(true);
                android.animation.ValueAnimator grow = android.animation.ValueAnimator.ofFloat(h, box.getWidth());
                grow.setDuration(850);
                grow.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                grow.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); box.invalidate(); });
                grow.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        ((RevealBox) box).setFrozen(false);
                        for (int c = 0; c < box.getChildCount(); c++) {
                            box.getChildAt(c).animate().alpha(1f).setDuration(300).setStartDelay(c * 40L).start();
                        }
                        box.postDelayed(() -> box.setLayerType(View.LAYER_TYPE_NONE, null), 600);
                    }
                });
                grow.start();
            }).start();
        });
        }
        if (sides != null || livePage != null || more != null) {
            // Then the line-ups, a page at a time flipping up in the same pill (each side's goalkeeper, defence,
            // midfield and attack), and last which channel has it; or a race result's pages.
            List<List<Object>> pages = sides != null ? lineupPages(sides, pictures) : new ArrayList<>();
            if (livePage != null) pages.add(livePage);
            if (more != null) pages.addAll(more);
            if (sides != null) {
                List<Object> head = parts;
                if (livePage != null && livePage.size() > 9) {
                    head = new ArrayList<>(livePage.subList(0, 9));
                    head.add(14);
                    head.add(bold("📋  LINE-UPS"));
                }
                showingLineup = new Object[]{head, sides, pictures, System.currentTimeMillis()};
            }
            main.postDelayed(() -> flipPage(box, pages, 0, h), 6_500);
            return;
        }
        if (inline != null) {
            offerWatch(box, h, inline);
            return;
        }
        main.postDelayed(() -> closePill(box, h, visible), SHOW_MS + 1_400);
    }

    // ---- "Watch on": the channel and OK at the end of the line, OK to watch it in TiviMate ------------------------

    private Runnable watchClose;

    /**
     * The end of a line with somewhere to watch: the channel's logo (its white lettering in the pill's ink on the
     * light glass) and this TV's own remote's OK button (a theme's "ok_button": tcl, streamer).
     */
    @SuppressWarnings("unchecked")
    private List<Object> watchParts(Object[] w) {
        List<String> names = (List<String>) w[0];
        android.graphics.Bitmap logo = (android.graphics.Bitmap) w[1];
        List<Object> parts = new ArrayList<>();
        parts.add(14);
        boolean ink = !onLane() && !darkGlass;
        parts.add(logo != null ? new ChipImg(ink ? inked(logo) : logo) : bold(names.get(0)));
        parts.add(10);
        String remote = Config.themeString(this, "ok_button", "");
        parts.add(new android.graphics.drawable.BitmapDrawable(getResources(),
                okButton(remote.isEmpty() ? "tcl" : remote, getResources().getDisplayMetrics().density, darkGlass)));
        return parts;
    }

    /**
     * While a line with a channel shows, OK on the remote opens TiviMate on that channel (the first of w's names the
     * playlist has), the steps hidden under the helper's cover; any other key closes the alert. The window takes the
     * keys only while it shows.
     */
    @SuppressWarnings("unchecked")
    private void offerWatch(LinearLayout box, int h, Object[] w) {
        List<String> names = (List<String>) w[0];
        android.graphics.Bitmap logo = (android.graphics.Bitmap) w[1];
        // The remote's keys taken after a moment: a press already on its way when the alert came was meant for the app.
        View was = host;
        main.postDelayed(() -> { if (host == was && host != null) watchKeys(box, h, w, names, logo); }, PopupMotion.FRESH_MS);
        watchClose = () -> endWatch(box, h);
        main.postDelayed(watchClose, SHOW_MS + 4_000);
    }

    private void watchKeys(LinearLayout box, int h, Object[] w, List<String> names, android.graphics.Bitmap logo) {
        takeKeys(true);
        host.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return true;
            boolean ok = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A;
            endWatch(box, h);
            if (ok && w.length > 2 && w[2] instanceof Runnable) ((Runnable) w[2]).run();
            else if (ok) AppSearchService.tune(this, getString(R.string.scores_open_package), names, logo);
            return true;
        });
    }

    /**
     * The remote's OK button, drawn small for "Press [OK] to watch": the TCL's black round button with OK in white in
     * its dark ring; the Google TV Streamer's plain pale button in its pale ring.
     */
    static android.graphics.Bitmap okButton(String remote, float d, boolean onDark) {
        int s = Math.round(22 * d);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float r = s / 2f;
        boolean streamer = "streamer".equalsIgnoreCase(remote);
        // The ring of the direction keys, then the button in it.
        // On dark glass the ring is lighter, so the black button still stands out.
        p.setColor(streamer ? 0xFFD9D6CF : onDark ? 0xFF6B6E75 : 0xFF2B2D31);
        c.drawCircle(r, r, r, p);
        p.setColor(streamer ? 0xFFBDB9B0 : 0xFF55585E);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, d * 0.8f));
        c.drawCircle(r, r, r * 0.62f, p);
        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setColor(streamer ? 0xFFEDEAE4 : 0xFF0E0F11);
        c.drawCircle(r, r, r * 0.56f, p);
        if (!streamer) {
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(r * 0.5f);
            android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText("OK", r, r - (fm.ascent + fm.descent) / 2f, p);
        }
        return b;
    }

    /**
     * The remote's Back key, drawn for the pop-ups' corner (Back closes them): on a TCL remote a black round key with
     * a white return arrow (pointing left, turning back round on the right); on the Streamer's remote the same arrow
     * on its pale key. Drawn at the size it shows, so it is sharp.
     */
    static android.graphics.Bitmap backButton(String remote, int s) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        boolean streamer = "streamer".equalsIgnoreCase(remote);
        float r = s / 2f;
        p.setColor(streamer ? 0xFFEDEAE4 : 0xFF17181B);
        c.drawCircle(r, r, r, p);
        p.setColor(streamer ? 0xFF2B2D31 : 0xFFFFFFFF);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.085f);
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        // The shaft along the top, the turn down the right side, and the tail back along the bottom.
        android.graphics.Path arrow = new android.graphics.Path();
        float top = s * 0.39f, bottom = s * 0.67f, turnX = s * 0.585f, rad = (bottom - top) / 2f;
        arrow.moveTo(s * 0.40f, bottom);
        arrow.lineTo(turnX, bottom);
        arrow.arcTo(new android.graphics.RectF(turnX - rad, top, turnX + rad, bottom), 90, -180);
        arrow.lineTo(s * 0.30f, top);
        c.drawPath(arrow, p);
        // The head, pointing left.
        android.graphics.Path head = new android.graphics.Path();
        head.moveTo(s * 0.40f, top - s * 0.11f);
        head.lineTo(s * 0.285f, top);
        head.lineTo(s * 0.40f, top + s * 0.11f);
        c.drawPath(head, p);
        return b;
    }

    /** The "Watch on" step over: the keys back with the app, and the pill closes. */
    private void endWatch(LinearLayout page, int h) {
        if (watchClose != null) main.removeCallbacks(watchClose);
        watchClose = null;
        if (host == null || shown != page) return;
        host.setOnKeyListener(null);
        takeKeys(false);
        final float[] full = {page.getWidth()};
        outline(page, h, full);
        closePill(page, h, full);
    }

    /** The alert's window takes the remote's keys (for "Watch on"), or leaves them with the app again. */
    private void takeKeys(boolean take) {
        if (host == null) return;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) host.getLayoutParams();
        if (take) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            getSystemService(WindowManager.class).updateViewLayout(host, lp);
        } catch (Exception ignored) {
            return;
        }
        host.setFocusable(take);
        host.setFocusableInTouchMode(take);
        if (take) host.requestFocus();
    }

    private static final long PAGE_MS = 3_200L;

    /** Home is in front (MainActivity says so in onResume and onPause). */
    static volatile boolean homeInFront;

    /** Launcher v2's Home is the one in front: alerts take the score bar's place, its full width, centred. */
    static volatile boolean laneHome;

    /**
     * Over an app the alerts are Sky Glass's shape: a rounded rectangle, not a pill. On v2's Home they keep the
     * round ends of the score pills whose place they take.
     */
    private static final float CORNER_DP = 12f;

    private float corner(int h) {
        return onLane() ? h / 2f : Math.min(h / 2f, CORNER_DP * getResources().getDisplayMetrics().density);
    }

    /** Sky Sports F1's dark glass, for an F1 line over an app. */
    private boolean darkGlass;

    private boolean onLane() {
        return (laneHome && homeInFront || PopupFrame.isShowing()) && !dreaming;
    }

    /** The alerts' dark ink on their silver glass; white, at the same strength, on the Home pills' clear glass. */
    private int ink(int c) {
        return onLane() || darkGlass ? (c & 0xFF000000) | 0xFFFFFF : c;
    }

    /** v2's Home listens: true while an alert holds the score bar's place (the pills step aside), false after. */
    static volatile java.util.function.Consumer<Boolean> laneListener;
    private boolean hostOnLane;

    private void laneShown(boolean on) {
        if (hostOnLane == on) return;
        hostOnLane = on;
        main.post(() -> ScoreCard.lane("pill", on));
    }

    /**
     * Over another app the alerts do not animate. Their window makes the TV compose the app's own layer and the alert
     * on the graphics chip instead of in hardware, for every frame that changes; on a slower set (the TCL) the steady
     * stream of animation frames over a 4K picture showed as stutter in the video underneath. A still alert costs that
     * once when it appears and once when it goes. Home and the screensaver keep the animations.
     */
    private boolean calm() {
        return !animateOverApps && !homeInFront && !dreaming;
    }

    /** animate_over_apps (theme): the full animations over other apps too, at the cost of the odd video frame. */
    private boolean animateOverApps;

    /**
     * lite_over_apps (theme): over another app the alerts still animate, but lightly: their window is always drawn by
     * the CPU (a plain buffer on a display plane of its own, so the video keeps its hardware composition), and each
     * alert is drawn once into a cached picture that only slides and fades, instead of the glass being redrawn for
     * every frame of a stretch and grow. For a TV (the TCL) whose video stuttered under the full animations.
     */
    private boolean liteOverApps;

    private boolean lite() {
        return liteOverApps && !homeInFront && !dreaming;
    }

    /** The layer an animating alert is drawn into: a cached picture (software in a CPU window, else the GPU's). */
    private int animLayer() {
        return lite() ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_HARDWARE;
    }

    /** still_over_apps (theme): turn off pop-up page changes over a playing video. */
    private boolean stillOverApps;
    /** A kick-off reminder's line-ups put off while a video played: {header, sides, pictures, when}. */
    private Object[] held, showingLineup;
    private static final long HELD_MS = 3 * 60 * 60_000L;

    /**
     * Over a playing video on the TCL every change in an alert's window makes the TV compose the whole screen on the
     * graphics chip, and the video loses frames each time; one still pill costs nothing measurable. With
     * still_over_apps an alert there is a single still pill, and paged line-ups wait.
     */
    private boolean stillOnly() {
        if (!stillOverApps || !calm()) return false;
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        return am != null && am.isMusicActive();
    }

    /** The held line-ups, once nothing is playing or Home or the screensaver is up; a stale one is dropped. */
    private void releaseHeld() {
        if (held == null || stillOnly()) return;
        Object[] h = held;
        held = null;
        if (System.currentTimeMillis() - (Long) h[3] > HELD_MS) return;
        queue.add(new Object[]{h[0], "reminder", h[1], h[2], null});
        showNext();
    }

    /** The alert's window gone, and the next one in the queue. */
    /** The alert's window fading out (over an app, where the alert has no closing movement of its own). */
    private boolean leaving;

    private void removeHost() {
        android.widget.FrameLayout v = host;
        // Over an app the alerts have no closing movement of their own: the window fades down and out, then goes.
        if (v != null && calm() && !leaving && v.getAlpha() > 0.01f && v.isAttachedToWindow()) {
            leaving = true;
            PopupMotion.out(v, 10 * getResources().getDisplayMetrics().density, () -> {
                leaving = false;
                if (host == v) removeHostNow();
            });
            return;
        }
        if (leaving) return;   // the fade's end takes it away
        removeHostNow();
    }

    private void removeHostNow() {
        if (host != null) {
            host.removeAllViews();
            try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
        }
        host = null;
        laneShown(false);
        shown = null;
        current = null;
        showNext();
    }

    /** The next line-up page slides up into the pill's place; after the last, the pill closes as usual. */
    private void flipPage(LinearLayout old, List<List<Object>> pages, int index, int h) {
        if (host == null || shown != old) return;
        if (stillOnly() && showingLineup != null) {
            // Play was pressed while the pages turned: they stop, and the line-ups wait again.
            held = showingLineup;
            showingLineup = null;
            removeHost();
            return;
        }
        if (index >= pages.size()) {
            showingLineup = null;
            final float[] full = {old.getWidth()};
            outline(old, h, full);
            closePill(old, h, full);
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout page = pillBox(pages.get(index), d);
        // On v2's Home the pages fill the lane and centre their words, like the pill they follow.
        boolean lane = onLane();
        if (lane) page.setGravity(Gravity.CENTER);
        host.addView(page, new android.widget.FrameLayout.LayoutParams(
                lane ? android.widget.FrameLayout.LayoutParams.MATCH_PARENT : android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h,
                lane ? Gravity.TOP : Gravity.BOTTOM | Gravity.START));
        shown = page;
        if (calm()) {
            host.removeView(old);
            main.postDelayed(() -> flipPage(page, pages, index + 1, h), PAGE_MS);
            return;
        }
        page.setTranslationY(h);
        page.setAlpha(0f);
        old.setLayerType(animLayer(), null);
        old.animate().translationY(-h).alpha(0f).setDuration(380).withEndAction(() -> { if (host != null) host.removeView(old); }).start();
        page.animate().translationY(0f).alpha(1f).setDuration(380).start();
        main.postDelayed(() -> flipPage(page, pages, index + 1, h), PAGE_MS);
    }

    /** The pages: for each side, its crest and name, the line ("DEFENCE"), then each player's picture and name. */
    private List<List<Object>> lineupPages(ScoresTicker.Side[] sides, Map<String, android.graphics.Bitmap> pics) {
        List<List<Object>> pages = new ArrayList<>();
        String[] names = {"GOALKEEPER", "DEFENCE", "MIDFIELD", "ATTACK"};
        for (ScoresTicker.Side side : sides) {
            List<List<String[]>> groups = new ArrayList<>();
            for (int i = 0; i < 4; i++) groups.add(new ArrayList<>());
            for (String[] p : side.players) groups.get(line(p.length > 3 ? p[3] : "")).add(p);
            for (int i = 0; i < 4; i++) {
                if (groups.get(i).isEmpty()) continue;
                List<Object> parts = new ArrayList<>();
                parts.add(new Img(pics.get(side.crest), false));
                parts.add(6);
                parts.add(bold(side.abbr));
                parts.add(8);
                parts.add(new Small(names[i] + (i == 0 && side.formation != null && !side.formation.isEmpty() ? "  ·  " + side.formation : "")));
                for (String[] p : groups.get(i)) {
                    parts.add(12);
                    parts.add(new Face(pics.get(p[1]), p[2]));
                    parts.add(5);
                    parts.add(p[0]);
                }
                pages.add(parts);
            }
        }
        return pages;
    }

    /** ESPN's position ("G", "CD-L", "RB", "DM", "AM", "CF", "LW") to 0 keeper, 1 defence, 2 midfield, 3 attack. */
    private static int line(String pos) {
        String p = pos.toUpperCase(java.util.Locale.ROOT);
        if (p.startsWith("G")) return 0;
        if (p.contains("M")) return 2;
        if (p.contains("D") || p.endsWith("B") || p.contains("WB")) return 1;
        return 3;
    }

    /** A player's round photo, or his shirt number on the same disc when there is none. */
    private View face(Face f, int size, float d) {
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFFC9D0D9);
        if (f.photo != null) {
            android.widget.ImageView v = crest(round(f.photo), d);
            v.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            v.setBackground(disc);
            v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline o) { o.setOval(0, 0, view.getWidth(), view.getHeight()); }
            });
            v.setClipToOutline(true);
            return v;
        }
        TextView n = text(bold(f.number), 9.5f, 0xFF1A1D24);   // on its grey disc
        n.setGravity(Gravity.CENTER);
        n.setBackground(disc);
        return n;
    }

    /** The photo cut to a disc over the face's grey, in the bitmap itself: a window drawn in software does not clip. */
    private static android.graphics.Bitmap round(android.graphics.Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFC9D0D9);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setShader(new android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setTranslate(-(src.getWidth() - size) / 2f, -(src.getHeight() - size) / 2f);
        p.getShader().setLocalMatrix(m);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return out;
    }

    private void outline(LinearLayout box, int h, float[] visible) {
        final float radius = corner(h);
        if (box instanceof RevealBox) ((RevealBox) box).visible = visible;
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, Math.min(v.getWidth(), Math.round(visible[0])), v.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
    }

    /**
     * Closing is the opening backwards: the pill draws back in to the dot with the text going as it passes (the
     * shrinking outline clips it from the right while it fades), then the dot pops away.
     */
    private void closePill(LinearLayout box, int h, float[] visible) {
        // Moved to Home or away meanwhile: this one has gone, and its copy closes on its own.
        if (shown != box) return;
        current = null;
        if (calm()) {
            removeHost();
            return;
        }
        {
            box.setLayerType(animLayer(), null);
            for (int c = 0; c < box.getChildCount(); c++) {
                box.getChildAt(c).animate().alpha(0f).setStartDelay(0).setDuration(lite() ? 300 : 600).start();
            }
            // Drawn by the CPU, the words fade first and the pill then draws back as one picture, not redrawn each frame.
            box.postDelayed(() -> {
                if (lite() && box instanceof RevealBox) ((RevealBox) box).setFrozen(true);
                android.animation.ValueAnimator shrink = android.animation.ValueAnimator.ofFloat(visible[0], h);
                shrink.setDuration(700);
                shrink.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                shrink.addUpdateListener(a -> { visible[0] = (float) a.getAnimatedValue(); box.invalidateOutline(); box.invalidate(); });
                shrink.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        box.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(260)
                                .setInterpolator(new android.view.animation.AnticipateInterpolator(1.5f)).withEndAction(() -> {
                            if (host == null || shown != box) return;
                            host.removeView(box);
                            // Remove the window itself too: even an empty one over the video can show as a faint
                            // box where the TV processes the picture differently under it.
                            try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
                            host = null;
                            laneShown(false);
                            shown = null;
                            showNext();
                        }).start();
                    }
                });
                shrink.start();
            }, lite() ? 320 : 0);
        }
    }

    /** One slim line in the Home screen's frosted pill style, dark text on the silver glass, from its parts. */
    private LinearLayout pillBox(List<Object> parts, float d) {
        LinearLayout box = new RevealBox(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int h = Math.round(PILL_DP * d);
        for (Object part : parts) if (part instanceof Tint) laneTint = ((Tint) part).colour;
        box.setBackground(glass(corner(h)));
        int pad = Math.round(14 * d);
        box.setPadding(pad, 0, pad, 0);
        int crestPx = Math.round(CREST_DP * boost * d);
        int gap = 0;
        for (Object part : parts) {
            if (part instanceof Tint) continue;
            if (part instanceof Integer) { gap = Math.round((Integer) part * d); continue; }
            if (part == null || part instanceof android.graphics.Bitmap) {
                box.addView(crest((android.graphics.Bitmap) part, d), sized(crestPx, gap));
            } else if (part instanceof Img) {
                Img img = (Img) part;
                if (darkGlass && img.bitmap != null) img = new Img(lifted(img.bitmap), img.wide);
                LinearLayout.LayoutParams lp = sized(crestPx, gap);
                if (img.wide) lp.width = crestPx * 2;
                // A long, low wordmark (F1's) is drawn as wide as it needs to stand nearly as tall as the crests.
                if (img.wide && img.bitmap != null && img.bitmap.getWidth() >= 2 * img.bitmap.getHeight()) {
                    float aspect = img.bitmap.getWidth() / (float) img.bitmap.getHeight();
                    lp.width = Math.round(Math.min(crestPx * 4.2f, crestPx * 0.9f * aspect));
                    lp.rightMargin = Math.round(4 * d);
                }
                box.addView(crest(img.bitmap, d), lp);
            } else if (part instanceof android.graphics.drawable.Drawable) {
                android.graphics.drawable.Drawable mark = (android.graphics.drawable.Drawable) part;
                android.widget.ImageView v = new android.widget.ImageView(this);
                v.setImageDrawable(mark);
                // Drawn marks and vector icons scale cleanly: fit them to the crests' height.
                int mh = Math.max(1, mark.getIntrinsicHeight());
                int th = Math.round(crestPx * 0.95f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, mark.getIntrinsicWidth() * th / mh), th);
                lp.leftMargin = gap;
                box.addView(v, lp);
            } else if (part instanceof Face) {
                box.addView(face((Face) part, crestPx, d), sized(crestPx, gap));
            } else if (part instanceof ChipImg) {
                // A channel's logo straight on the glass, its lettering already in the pill's ink (inked).
                android.graphics.Bitmap logo = ((ChipImg) part).bitmap;
                android.widget.ImageView v = new android.widget.ImageView(this);
                v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                v.setImageBitmap(logo);
                int ch = crestPx;
                int cw = Math.min(Math.round(180 * d), Math.round(ch * logo.getWidth() / (float) Math.max(1, logo.getHeight())));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cw, ch);
                lp.leftMargin = gap;
                box.addView(v, lp);
            } else if (part instanceof Score) {
                box.addView(scoreView((Score) part, d), gap(gap));
            } else if (part instanceof Small) {
                box.addView(text(((Small) part).text, SMALL_SP * boost, ink(0xB31A1D24)), gap(gap));
            } else {
                TextView t = text((CharSequence) part, TEXT_SP * boost, ink(0xFF1A1D24));
                t.setMaxWidth(Math.round(700 * d));   // a long headline ends in an ellipsis
                t.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(t, gap(gap));
            }
            gap = 0;
        }
        box.setMinimumHeight(h);
        return box;
    }

    private static int parse(String n) {
        try { return Integer.parseInt(n.trim()); } catch (Exception e) { return -1; }
    }

    /** The roll of the score pillBox last built, run by showNext once the pill is up. */
    private Runnable scoreRoll;

    /**
     * "1–0" as three views; the scoring side's number sits in a window that starts on the score before the goal and
     * rolls the old number up and out and the new one up into place, which then flashes green and settles.
     */
    private View scoreView(Score sc, float d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        boolean home = "home".equals(sc.side);
        View[] rollers = new View[2];
        for (int i = 0; i < 2; i++) {
            int now = i == 0 ? sc.home : sc.away;
            boolean scorer = (i == 0) == home;
            if (i == 1) row.addView(text(bold("–"), TEXT_SP * boost, ink(0xFF1A1D24)));
            if (!scorer) { row.addView(text(bold(String.valueOf(now)), TEXT_SP * boost, ink(0xFF1A1D24))); continue; }
            android.widget.FrameLayout window = new android.widget.FrameLayout(this);
            window.setClipChildren(true);
            TextView was = text(bold(String.valueOf(Math.max(0, now - 1))), TEXT_SP * boost, ink(0xFF1A1D24));
            TextView is = text(bold(String.valueOf(now)), TEXT_SP * boost, 0xFF16A34A);
            was.setGravity(Gravity.CENTER);
            is.setGravity(Gravity.CENTER);
            window.addView(was, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            window.addView(is, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            is.setAlpha(0f);
            row.addView(window, new LinearLayout.LayoutParams(-2, Math.round(22 * d)));
            rollers[0] = was;
            rollers[1] = is;
        }
        if (rollers[0] != null) {
            TextView was = (TextView) rollers[0], is = (TextView) rollers[1];
            scoreRoll = () -> {
                float h = 22 * d;
                if (calm()) {
                    was.setAlpha(0f);
                    is.setAlpha(1f);
                    is.setTextColor(ink(0xFF1A1D24));
                    return;
                }
                is.setTranslationY(h);
                is.setAlpha(1f);
                was.animate().translationY(-h).alpha(0f).setDuration(420)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f)).start();
                is.animate().translationY(0f).setDuration(520).setStartDelay(80)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).withEndAction(() -> {
                    is.setPivotY(is.getHeight() / 2f);
                    is.animate().scaleX(1.3f).scaleY(1.3f).setDuration(160).withEndAction(() ->
                            is.animate().scaleX(1f).scaleY(1f).setDuration(220).withEndAction(() -> {
                                // Back to the pill's ink after a moment in green.
                                main.postDelayed(() -> is.setTextColor(ink(0xFF1A1D24)), 900);
                            }).start()).start();
                }).start();
            };
        }
        return row;
    }

    /**
     * The race result: the Grand Prix's name, then the top ten in two rows of five, podium numbers in gold, silver
     * and bronze, on the same glass as the football alerts. It stays a little longer than they do.
     */
    private void showRace(F1Results.Race race) {
        if (shown != null || race.top.isEmpty()) { showNext(); return; }
        float d = getResources().getDisplayMetrics().density;
        // Sky Sports F1's dark glass, as every F1 alert has.
        darkGlass = true;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(glass(CORNER_DP * d));
        float k = RACE_K;
        int pad = Math.round(16 * k * d);
        panel.setPadding(pad, Math.round(10 * k * d), pad, Math.round(12 * k * d));
        SpannableStringBuilder head = new SpannableStringBuilder("🏁  " + race.name.toUpperCase() + "  ·  RESULT");
        head.setSpan(new StyleSpan(Typeface.BOLD), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // The F1 logo leads the heading, full size, as on the other F1 alerts.
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView f1Logo = crest(raceLogos.get(F1Results.F1_LOGO), d);
        LinearLayout.LayoutParams llp = sized(Math.round(CREST_DP * 1.15f * k * d), 0);
        llp.width = Math.round(CREST_DP * 4.4f * k * d);
        heading.addView(f1Logo, llp);
        heading.addView(text(head, TEXT_SP * k, ink(0xFF1A1D24)), gap(Math.round(10 * k * d)));
        panel.addView(heading);
        // The whole field: P11 to the last first, then P1 to P10.
        int n = race.top.size();
        List<List<Integer>> pages = new ArrayList<>();
        if (n > 10) {
            List<Integer> back = new ArrayList<>();
            for (int i = 10; i < n; i++) back.add(i);
            pages.add(back);
        }
        List<Integer> top = new ArrayList<>();
        for (int i = 0; i < Math.min(10, n); i++) top.add(i);
        pages.add(top);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        panel.addView(body);
        raceLines(body, race, pages.get(0), d);
        ensureHost(Math.round(940 * k), Math.round(150 * k));
        if (host == null) return;
        host.addView(panel, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                onLane() ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.BOTTOM | Gravity.START));
        shown = panel;
        play("f1");
        long pageMs = 6_000L, showMs = pages.size() * pageMs + 3_000L;
        for (int p = 1; p < pages.size(); p++) {
            List<Integer> page = pages.get(p);
            main.postDelayed(() -> {
                if (shown != panel) return;
                if (calm()) { raceLines(body, race, page, d); return; }
                body.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                    raceLines(body, race, page, d);
                    body.animate().alpha(1f).setDuration(280).start();
                }).start();
            }, p * pageMs);
        }
        if (calm()) {
            main.postDelayed(() -> { if (shown == panel) removeHost(); }, showMs);
            return;
        }
        panel.setAlpha(0f);
        panel.setTranslationY(14 * d);
        panel.setLayerType(animLayer(), null);
        panel.animate().alpha(1f).translationY(0f).setDuration(600)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction(() -> panel.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        main.postDelayed(() -> {
            if (shown != panel) return;
            current = null;
            panel.setLayerType(animLayer(), null);
            panel.animate().alpha(0f).setDuration(450).withEndAction(() -> {
                if (host == null || shown != panel) return;
                host.removeView(panel);
                try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
                host = null;
                laneShown(false);
                shown = null;
                showNext();
            }).start();
        }, showMs);
    }

    /** Team logos (dark ones lifted for the dark glass) and F1's, fetched before a result card shows. */
    private final Map<String, android.graphics.Bitmap> raceLogos = new HashMap<>();

    private void enqueueRace(F1Results.Race r) {
        f1Board(false, r.name, "RACE", r.top, r.logos, 0);
    }

    /**
     * A race result as v2's Home shows it, in the score bar's place: the F1 logo and the Grand Prix, then pills of
     * finishers, P11 to the last first and P1 to P10 after, at most five to a pill and the pills even.
     */
    private Object[] raceLane(F1Results.Race race) {
        android.graphics.Bitmap f1 = raceLogos.get(F1Results.F1_LOGO);
        List<Object> head = new ArrayList<>();
        head.add(new Img(f1, true));
        head.add(12);
        head.add(bold("🏁  " + race.name.toUpperCase(java.util.Locale.ROOT)));
        head.add(10);
        head.add(new Small("RESULT"));
        int n = race.top.size();
        List<Integer> back = new ArrayList<>(), top = new ArrayList<>();
        for (int i = 10; i < n; i++) back.add(i);
        for (int i = 0; i < Math.min(10, n); i++) top.add(i);
        int[] medal = {0xFFE8B931, 0xFFC9CDD4, 0xFFD08A4F};
        List<List<Object>> pages = new ArrayList<>();
        for (List<Integer> group : Arrays.asList(back, top)) {
            if (group.isEmpty()) continue;
            int count = (group.size() + 4) / 5, per = (group.size() + count - 1) / count;
            for (int k = 0; k < group.size(); k += per) {
                List<Object> page = new ArrayList<>();
                page.add(new Img(f1, true));
                page.add(14);
                for (int j = k; j < Math.min(group.size(), k + per); j++) {
                    int i = group.get(j);
                    if (j > k) page.add(18);
                    SpannableStringBuilder pos = new SpannableStringBuilder(String.valueOf(i + 1));
                    pos.setSpan(new StyleSpan(Typeface.BOLD), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (i < 3) pos.setSpan(new ForegroundColorSpan(medal[i]), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    page.add(pos);
                    page.add(5);
                    String url = i < race.logos.size() ? race.logos.get(i) : null;
                    page.add(new Img(url == null ? null : raceLogos.get(url), false));
                    page.add(5);
                    page.add(i == 0 ? bold(race.top.get(i)) : race.top.get(i));
                }
                pages.add(page);
            }
        }
        return new Object[]{head, "f1", null, null, null, pages};
    }

    /** One page of the result, in the order given: at most five to a line, the lines even. */
    private void raceLines(LinearLayout body, F1Results.Race race, List<Integer> page, float d) {
        body.removeAllViews();
        int[] medal = {0xFFE8B931, 0xFFC9CDD4, 0xFFD08A4F};
        int lines = (page.size() + 4) / 5, per = (page.size() + lines - 1) / lines;
        for (int row = 0; row * per < page.size(); row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            for (int k = row * per; k < Math.min(page.size(), row * per + per); k++) {
                int i = page.get(k);
                // Position, the team's logo, the surname (the winner's in bold).
                SpannableStringBuilder pos = new SpannableStringBuilder(String.valueOf(i + 1));
                pos.setSpan(new StyleSpan(Typeface.BOLD), 0, pos.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                line.addView(text(pos, TEXT_SP * RACE_K, i < 3 ? medal[i] : ink(0xA61A1D24)), gap(k == row * per ? 0 : Math.round(18 * RACE_K * d)));
                String url = i < race.logos.size() ? race.logos.get(i) : null;
                line.addView(crest(url == null ? null : raceLogos.get(url), d), sized(Math.round(CREST_DP * RACE_K * d), Math.round(5 * RACE_K * d)));
                line.addView(text(i == 0 ? bold(race.top.get(i)) : race.top.get(i), TEXT_SP * RACE_K, ink(0xFF1A1D24)), gap(Math.round(5 * RACE_K * d)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(6 * RACE_K * d);
            body.addView(line, lp);
        }
    }

    private TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        if (onLane()) {
            // On v2's Home the alert speaks in Home's own face (Inter; its bold is the real semibold).
            if (homeFace == null) homeFace = getResources().getFont(R.font.inter_ui);
            t.setTypeface(homeFace);
            t.getPaint().setSubpixelText(true);
        }
        return t;
    }

    private android.graphics.Typeface homeFace;

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
        if (self == this) self = null;
        if (deliveries != null) deliveries.stop();
        try { unregisterReceiver(dreams); } catch (Exception ignored) { }
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        if (am != null) am.unregisterAudioPlaybackCallback(playback);
        if (sounds != null) sounds.release();
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (host != null) {
            try { getSystemService(WindowManager.class).removeView(host); } catch (Exception ignored) { }
            host = null;
            laneShown(false);
            shown = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
