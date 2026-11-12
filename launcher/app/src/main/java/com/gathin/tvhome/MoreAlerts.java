package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Four more football alerts, each as Home's bar (ScoreCard's, out of the scores box) while Home is in front and as a
 * pop-up over the apps where the goal alerts go: half time (the first half's scorers, possession and shots; over an
 * app LineupCard's card), a VAR check and its decision (PENALTY GIVEN, NO PENALTY, GOAL STANDS, NO GOAL), late drama
 * (an equaliser or winner from the 85th minute, a comeback from two down) and the family's prediction results at full
 * time (YOU CALLED IT, SO CLOSE). The real ones come from the alert service's reads (GoalWatchService); adb's tests
 * (test()) show each for a real game. Silent, as the other football cards.
 */
final class MoreAlerts {
    private MoreAlerts() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** adb's tests: finding a real game for them (several reads of ESPN), off the cards' thread. */
    private static final ExecutorService FIND = Executors.newSingleThreadExecutor();

    /** VAR: the check's charcoal label and its amber dot; a decision given in green, one refused in red. */
    private static final int CHARCOAL = 0xFF111214, AMBER = 0xFFFFB020, GIVEN = 0xFF1E9E4A, REFUSED = 0xFFB3261E;
    /** Late drama's orange-red: the band's two colours, and the label's. */
    private static final int DRAMA = 0xFFF0541E, DRAMA_DEEP = 0xFFA3290A, DRAMA_LABEL = 0xFFC2410C;
    /** The predictions' labels: a deep gold for the exact score (its trophy brighter), a lighter blue for the result. */
    private static final int GOLD = 0xFF8A5A00, CLOSE = 0xFF2B3BB8;
    private static final int NAVY = 0xFF151A63;

    // ---- adb's tests -----------------------------------------------------------------------------------------------

    private static final String[] TESTS = {"test_ht", "test_var", "test_var_decision", "test_drama", "test_comeback",
            "test_prediction", "test_prediction_close"};

    /**
     * adb only (am startservice -n com.gathin.tvhome/.GoalWatchService --es test_ht auto, or an ESPN event id; likewise
     * test_var, test_var_decision, test_drama, test_comeback, test_prediction and test_prediction_close): the alert for
     * a real game. "auto" takes one of today's in the followed competitions (the Champions League's first, then the
     * Premier League's), else of the last few days, with its real clubs, crests, score, scorers and statistics; late
     * drama and the comeback are the latest real ones of the last ten days. Made up are only the VAR check's reason
     * (var_reason, "Possible penalty" unless given) and the prediction (the first player's pick, never saved); a VAR
     * decision is ESPN's own where it wrote one, else var_decision (penalty, no_penalty, goal or no_goal). Home's bar
     * with Home in front, else the pop-up over the app.
     */
    static void test(GoalWatchService s, Intent i) {
        for (String t : TESTS) {
            if (!i.hasExtra(t)) continue;
            Object raw = i.getExtras() == null ? null : i.getExtras().get(t);
            String which = raw instanceof String ? ((String) raw).trim() : "";
            String id = which.isEmpty() || "auto".equalsIgnoreCase(which) || "true".equalsIgnoreCase(which) ? null : which;
            String reason = i.getStringExtra("var_reason"), decision = i.getStringExtra("var_decision");
            AlertLog.note(s, "test " + t + " (" + (id == null ? "auto" : "event " + id) + ")");
            FIND.execute(() -> {
                try {
                    run(s, t, id, reason, decision);
                } catch (RuntimeException e) {
                    AlertLog.note(s, "test " + t + " failed: " + e);
                }
            });
        }
    }

    private static void run(GoalWatchService s, String t, String id, String reason, String decision) {
        String[] leagues = s.getResources().getStringArray(R.array.goal_alert_leagues);
        switch (t) {
            case "test_ht": {
                ScoresTicker.Game g = id != null ? byId(leagues, id) : pick(leagues, x -> x.state != 1 && firstHalfGoals(x) > 0, x -> x.state != 1);
                if (g == null) { none(s, t, id); return; }
                halfTimeNow(s, g, true);
                return;
            }
            case "test_var": {
                ScoresTicker.Game g = id != null ? byId(leagues, id) : pick(leagues, x -> x.state == 0, x -> x.state != 1);
                if (g == null) { none(s, t, id); return; }
                String minute = g.state == 0 && g.detail != null && g.detail.contains("'") ? g.detail : "";
                check(s, g, reason != null && !reason.isEmpty() ? reason : "Possible penalty", minute, "test-var|" + g.id);
                return;
            }
            case "test_var_decision": {
                // ESPN's own decision where it wrote one: in this game, or the latest in the followed competitions.
                Object[] real = realDecision(leagues, id);
                ScoresTicker.Game g = real != null ? (ScoresTicker.Game) real[0] : id != null ? byId(leagues, id)
                        : pick(leagues, x -> x.state == 0, x -> x.state != 1);
                if (g == null) { none(s, t, id); return; }
                String kind, team = null, player = "", minute = "";
                if (real != null) {
                    String[] inc = (String[]) real[1];
                    kind = kindOf(inc[4]);
                    team = teamOf(inc[4]);
                    player = inc[2];
                    minute = inc[3];
                    AlertLog.note(s, "test VAR decision: ESPN's \"" + inc[4] + "\" " + minute + " in " + AlertLog.game(g));
                } else {
                    kind = decision != null && kindLabel(decision) != null ? decision : "penalty";
                    if (kind.contains("penalty")) team = g.homeName != null ? g.homeName : g.home;
                    AlertLog.note(s, "test VAR decision: none from ESPN for " + AlertLog.game(g) + ", a sample " + kind);
                }
                String k = kind, tm = team, pl = player, mn = minute;
                // The check first, its decision taking its place a few seconds later.
                check(s, g, reasonFor(kind), mn, "test-var|" + g.id);
                MAIN.postDelayed(() -> decision(s, g, k, tm, pl, mn, "test-var-decision|" + g.id), 6_000L);
                return;
            }
            case "test_drama":
            case "test_comeback": {
                boolean comeback = "test_comeback".equals(t);
                Object[] found = dramaGoal(leagues, id, comeback);
                ScoresTicker.Game g;
                String[] goal;
                if (found != null) {
                    g = (ScoresTicker.Game) found[0];
                    goal = (String[]) found[1];
                } else {
                    // None in the last ten days: the latest goal of a real game, in the same colours (a look only).
                    g = id != null ? byId(leagues, id) : pick(leagues, x -> x.state != 1 && !x.goals.isEmpty(), x -> !x.goals.isEmpty());
                    if (g == null || g.goals.isEmpty()) { none(s, t, id); return; }
                    goal = sorted(g).get(sorted(g).size() - 1);
                    AlertLog.note(s, "test " + t + ": no real one in the last ten days, " + AlertLog.game(g) + "'s last goal shown in its colours");
                }
                Moment m = moment(g, goal[0], goal[1], goal[2]);
                int[] at = m == null ? null : new int[]{m.h, m.a};
                s.skyFootball(g, "goal", goal[1], goal[2], goal[3], goal[4], (comeback ? "test-comeback|" : "test-drama|") + goal[0], at);
                return;
            }
            case "test_prediction":
            case "test_prediction_close": {
                ScoresTicker.Game g = id != null ? byId(leagues, id) : pick(leagues, x -> x.state == 2, x -> x.state == 2);
                if (g == null) { none(s, t, id); return; }
                int h = parse(g.homeScore), a = parse(g.awayScore);
                if (h < 0 || a < 0) { none(s, t, id); return; }
                List<String> players = Predictions.players(s);
                String who = players.get(0);
                boolean exact = "test_prediction".equals(t);
                // The same result, a goal off: still the right result, never the score.
                int[] near = h > a ? new int[]{h + 1, a} : h < a ? new int[]{h, a + 1} : new int[]{h + 1, a + 1};
                List<String> spotOn = new ArrayList<>();
                if (exact) spotOn.add(who);
                int week = Predictions.correctThisWeek(s, who) + 1;
                ScoreCard.Data d = predictionCard(s, g, spotOn, exact ? null : who, exact ? null : near, h, a, week, players.size() > 1);
                d.key = "test-prediction|" + g.id;
                AlertLog.note(s, "test prediction: " + AlertLog.game(g) + ", a sample pick for " + who);
                MAIN.post(() -> ScoreCard.show(s, d));
                return;
            }
            default:
        }
    }

    private static void none(Context c, String t, String id) {
        AlertLog.note(c, "test " + t + ": no game found" + (id != null ? " with event " + id : " today or in the last few days"));
    }

    /** One day's games in the followed competitions: the Champions League's first, then the Premier League's; live first. */
    private static List<ScoresTicker.Game> day(String[] leagues, int back) {
        List<ScoresTicker.Game> list = ScoresTicker.load(leagues, LocalDate.now(ZoneId.systemDefault()).minusDays(back));
        list.sort((x, y) -> rank(x) != rank(y) ? Integer.compare(rank(x), rank(y)) : Integer.compare(order(x.state), order(y.state)));
        return list;
    }

    private static int rank(ScoresTicker.Game g) {
        return "uefa.champions".equals(g.slug) ? 0 : "eng.1".equals(g.slug) ? 1 : 2;
    }

    private static int order(int state) {
        return state == 0 ? 0 : state == 2 ? 1 : 2;
    }

    /** Today's game that best fits (prefer), else any today that will do (accept); else the same over the last week. */
    private static ScoresTicker.Game pick(String[] leagues, Predicate<ScoresTicker.Game> prefer, Predicate<ScoresTicker.Game> accept) {
        for (int back = 0; back <= 7; back++) {
            List<ScoresTicker.Game> list = day(leagues, back);
            for (ScoresTicker.Game g : list) if (prefer.test(g)) return g;
            if (back == 0) for (ScoresTicker.Game g : list) if (accept.test(g)) return g;
        }
        return null;
    }

    /** The game with this ESPN event id, today or in the last ten days. */
    private static ScoresTicker.Game byId(String[] leagues, String id) {
        for (int back = 0; back <= 10; back++) {
            for (ScoresTicker.Game g : day(leagues, back)) if (id.equals(g.id)) return g;
        }
        return null;
    }

    private static int firstHalfGoals(ScoresTicker.Game g) {
        int n = 0;
        for (String[] goal : g.goals) if (base(goal[2]) <= 45) n++;
        return n;
    }

    // ---- A: half time ---------------------------------------------------------------------------------------------

    /** Half time in a followed game (the alert service's read, a minute on; main thread): once, as the goals go. */
    static void halfTime(GoalWatchService s, ScoresTicker.Game g) {
        if (homeOnly(s, g) && !GoalWatchService.homeInFront) {
            AlertLog.note(s, "not shown (a Home-only game, Home not in front): half time " + AlertLog.game(g));
            AlertToday.why(s, "HT|" + g.id, "Home wasn't open");
            return;
        }
        halfTimeNow(s, g, false);
    }

    /** The first half's numbers read (ESPN's match summary) and both forms built, on the cards' thread. */
    private static void halfTimeNow(GoalWatchService s, ScoresTicker.Game g, boolean test) {
        s.cardRun(() -> {
            try {
                Stats st = firstHalf(g, summary(g));
                String key = (test ? "test-ht|" : "HT|") + g.id;
                // Home's bar: HALF TIME, who leads big, the scorers and possession small; nothing to watch, no OK.
                ScoreCard.Data d = s.footballData(g);
                d.kind = "ht";
                d.key = key;
                d.label = "HALF";
                d.labelSmall = "TIME";
                d.labelColour = NAVY;
                d.minute = "HT";
                d.score = st.home + "–" + st.away;
                d.watch = null;
                d.channel = null;
                d.channelLogo = null;
                d.crest = null;
                String home = d.homeName == null ? "" : d.homeName, away = d.awayName == null ? "" : d.awayName;
                d.last = st.home > st.away ? home + " lead" : st.away > st.home ? away + " lead"
                        : st.home == 0 ? "Goalless at the break" : "Level at the break";
                d.first = String.join(" · ", st.inOrder);
                StringBuilder line = new StringBuilder();
                if (st.homePoss >= 0) line.append("Possession ").append(st.homePoss).append("–").append(st.awayPoss).append('%');
                if (st.homeShots >= 0) line.append(line.length() > 0 ? " · " : "").append("Shots ").append(st.homeShots).append("–").append(st.awayShots);
                // FotMob's expected goals at the break, when it has the game.
                String[] xg = null;
                if (BoardCover.fotmobOn(s)) {
                    for (String[] r : FotMob.rows(FotMob.forGame(g.id, g.homeName, g.awayName, g.kickoff))) {
                        if ("Expected goals".equals(r[0])) xg = r;
                    }
                }
                if (xg != null) line.append(line.length() > 0 ? " · " : "").append("xG ").append(xg[1]).append("–").append(xg[2]);
                d.line = line.toString();
                d.showMs = 11_000L;
                // Over an app: LineupCard's card, the scorers under each side and the numbers as thin bars.
                LineupCard.Half f = new LineupCard.Half();
                f.key = key;
                f.leagueLogo = d.leagueLogo;
                f.homeCrest = d.homeCrest;
                f.awayCrest = d.awayCrest;
                f.homeName = home;
                f.awayName = away;
                f.score = d.score;
                f.competition = ScoresTicker.competition(g);
                f.slug = g.slug;
                f.homeGoals.addAll(st.homeGoals);
                f.awayGoals.addAll(st.awayGoals);
                f.homePoss = st.homePoss;
                f.awayPoss = st.awayPoss;
                f.homeShots = st.homeShots;
                f.awayShots = st.awayShots;
                if (xg != null) {
                    f.homeXg = xg[1];
                    f.awayXg = xg[2];
                }
                f.homeColour = g.homeColor;
                f.awayColour = g.awayColor;
                f.onHome = () -> ScoreCard.show(s, d);
                AlertLog.note(s, "card queued: half time " + AlertLog.game(g) + " " + d.score + (st.wholeGame ? " (read after the break: possession is the game's so far)" : ""));
                MAIN.post(() -> LineupCard.showHalfTime(s, f));
            } catch (RuntimeException e) {
                AlertLog.note(s, "card failed to build: half time " + AlertLog.game(g) + ": " + e);
            }
        });
    }

    /** The score at the break, the first half's scorers, possession in percent and shots (-1 where not known). */
    static final class Stats {
        int home, away, homePoss = -1, awayPoss = -1, homeShots = -1, awayShots = -1;
        final List<String> homeGoals = new ArrayList<>(), awayGoals = new ArrayList<>(), inOrder = new ArrayList<>();
        /** Read after the break: possession is then the game's so far, not the first half's. */
        boolean wholeGame;
    }

    /**
     * The whole game at full time, for the ad-break screen's FULL TIME widget: the final score, every scorer in order,
     * the game's possession and shots from ESPN's match summary (-1 where not known).
     */
    static Stats fullTime(ScoresTicker.Game g, JSONObject sum) {
        Stats st = new Stats();
        int hg = 0, ag = 0;
        for (String[] goal : sorted(g)) {
            boolean forHome = "home".equals(goal[3]);
            String words = GoalWatchService.surname(goal[1]) + " " + goal[2] + ("pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (og)" : "");
            (forHome ? st.homeGoals : st.awayGoals).add(words);
            st.inOrder.add(words);
            if (forHome) hg++; else ag++;
        }
        st.home = parse(g.homeScore) >= 0 ? parse(g.homeScore) : hg;
        st.away = parse(g.awayScore) >= 0 ? parse(g.awayScore) : ag;
        st.wholeGame = true;
        JSONObject box = sum == null ? null : sum.optJSONObject("boxscore");
        JSONArray teams = box == null ? null : box.optJSONArray("teams");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            boolean home = "home".equals(t.optString("homeAway"));
            JSONArray stats = t.optJSONArray("statistics");
            for (int j = 0; stats != null && j < stats.length(); j++) {
                JSONObject x = stats.optJSONObject(j);
                if (x == null) continue;
                double v = parseNumber(x.optString("displayValue"));
                if (v < 0) continue;
                if ("possessionPct".equals(x.optString("name"))) { if (home) st.homePoss = (int) Math.round(v); else st.awayPoss = (int) Math.round(v); }
                if ("totalShots".equals(x.optString("name"))) { if (home) st.homeShots = (int) Math.round(v); else st.awayShots = (int) Math.round(v); }
            }
        }
        if (st.homePoss >= 0 && st.awayPoss >= 0) st.awayPoss = 100 - st.homePoss;
        else { st.homePoss = -1; st.awayPoss = -1; }
        if (st.homeShots < 0 || st.awayShots < 0) { st.homeShots = -1; st.awayShots = -1; }
        return st;
    }

    private static final Pattern BREAK = Pattern.compile("ends,\\s*(.+?)\\s+(\\d+),\\s*(.+?)\\s+(\\d+)\\.?\\s*$");

    /**
     * The first half from the scoreboard's goals and ESPN's match summary. At the break (live) its numbers are the first
     * half's; read later (a test, a game since over), the score at the break is ESPN's "First Half ends" line and the
     * shots are counted from its commentary's first period, possession being then the whole game's.
     */
    static Stats firstHalf(ScoresTicker.Game g, JSONObject sum) {
        Stats st = new Stats();
        int hg = 0, ag = 0;
        for (String[] goal : sorted(g)) {
            if (base(goal[2]) > 45) continue;
            boolean forHome = "home".equals(goal[3]);
            String words = GoalWatchService.surname(goal[1]) + " " + goal[2] + ("pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (og)" : "");
            (forHome ? st.homeGoals : st.awayGoals).add(words);
            st.inOrder.add(words);
            if (forHome) hg++; else ag++;
        }
        boolean atBreak = g.state == 0 && g.halftime;
        st.home = hg;
        st.away = ag;
        if (atBreak && parse(g.homeScore) >= 0 && parse(g.awayScore) >= 0) {
            st.home = parse(g.homeScore);
            st.away = parse(g.awayScore);
        }
        if (sum == null) return st;
        if (!atBreak) {
            JSONArray keys = sum.optJSONArray("keyEvents");
            for (int i = 0; keys != null && i < keys.length(); i++) {
                JSONObject k = keys.optJSONObject(i);
                String type = k == null || k.optJSONObject("type") == null ? "" : k.optJSONObject("type").optString("type");
                if (!"halftime".equals(type)) continue;
                Matcher m = BREAK.matcher(k.optString("text"));
                if (!m.find()) break;
                boolean swapped = g.awayName != null && m.group(1).equalsIgnoreCase(g.awayName);
                st.home = Integer.parseInt(swapped ? m.group(4) : m.group(2));
                st.away = Integer.parseInt(swapped ? m.group(2) : m.group(4));
                break;
            }
        }
        // The boxscore: each side's possession, and its shots (the first half's at the break).
        JSONObject box = sum.optJSONObject("boxscore");
        JSONArray teams = box == null ? null : box.optJSONArray("teams");
        java.util.Map<String, Boolean> isHome = new java.util.HashMap<>();
        int[] shots = {-1, -1};
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            boolean home = "home".equals(t.optString("homeAway"));
            JSONObject team = t.optJSONObject("team");
            if (team != null) isHome.put(team.optString("displayName"), home);
            JSONArray stats = t.optJSONArray("statistics");
            for (int j = 0; stats != null && j < stats.length(); j++) {
                JSONObject x = stats.optJSONObject(j);
                if (x == null) continue;
                String name = x.optString("name");
                double v = parseNumber(x.optString("displayValue"));
                if (v < 0) continue;
                if ("possessionPct".equals(name)) { if (home) st.homePoss = (int) Math.round(v); else st.awayPoss = (int) Math.round(v); }
                if ("totalShots".equals(name)) shots[home ? 0 : 1] = (int) Math.round(v);
            }
        }
        if (st.homePoss >= 0 && st.awayPoss >= 0) st.awayPoss = 100 - st.homePoss;
        else { st.homePoss = -1; st.awayPoss = -1; }
        if (atBreak) {
            st.homeShots = shots[0];
            st.awayShots = shots[1];
        } else {
            st.wholeGame = st.homePoss >= 0;
            // After the break: the first period's attempts counted from the commentary (each with its side's name).
            int[] first = {0, 0};
            boolean any = false;
            JSONArray com = sum.optJSONArray("commentary");
            for (int i = 0; com != null && i < com.length(); i++) {
                JSONObject e = com.optJSONObject(i);
                JSONObject play = e == null ? null : e.optJSONObject("play");
                if (play == null || play.optJSONObject("period") == null || play.optJSONObject("period").optInt("number") != 1) continue;
                String type = play.optJSONObject("type") == null ? "" : play.optJSONObject("type").optString("text");
                if (!shot(type)) continue;
                Boolean home = play.optJSONObject("team") == null ? null : isHome.get(play.optJSONObject("team").optString("displayName"));
                if (home == null) continue;
                first[home ? 0 : 1]++;
                any = true;
            }
            if (any) { st.homeShots = first[0]; st.awayShots = first[1]; }
        }
        if (st.homeShots < 0 || st.awayShots < 0) { st.homeShots = -1; st.awayShots = -1; }
        return st;
    }

    /** An attempt at goal in ESPN's commentary: on or off target, blocked, the woodwork, a goal or a penalty taken. */
    private static boolean shot(String type) {
        return type.startsWith("Shot") || "Goal".equals(type) || type.startsWith("Goal - ") || type.startsWith("Penalty - ");
    }

    /** ESPN's match summary for the game; null when it cannot be read. */
    private static JSONObject summary(ScoresTicker.Game g) {
        if (g.slug == null || g.id == null) return null;
        try {
            return new JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug + "/summary?event=" + g.id));
        } catch (Exception e) {
            return null;
        }
    }

    // ---- B: VAR ---------------------------------------------------------------------------------------------------

    private static final Pattern DECISION = Pattern.compile("VAR Decision:\\s*([^.]*)");
    private static final Pattern OVERTURNED = Pattern.compile("OVERTURNED BY VAR:\\s*(.+?)\\s*\\(");
    private static final Pattern IN_BRACKETS = Pattern.compile("\\(([^)]+)\\)\\s*\\.?\\s*$");

    /**
     * The VAR lines of ESPN's commentary as incidents (ScoresTicker.incidents): each decision it writes ("VAR Decision:
     * Penalty Chelsea.", "… No Goal Levante 0-0 Atletico Madrid.", "… Goal Getafe 2-1 Sevilla (Martín Satriano).") and a
     * check still under way ("Delay in match (Bologna). VAR checking.", only until its decision is in). A decision on a
     * card, or "Other Decision Cancelled", is left out; so is a goal ruled out that the key events already have.
     */
    static List<String[]> varLines(ScoresTicker.Game g, JSONObject summary, List<String[]> already) {
        List<String[]> out = new ArrayList<>();
        JSONArray com = summary == null ? null : summary.optJSONArray("commentary");
        if (com == null) return out;
        int lastDecision = -1;
        // The commentary as VAR explained reads it ({text, minute}), for each check's and decision's reason.
        List<String[]> lines = new ArrayList<>();
        for (int i = 0; i < com.length(); i++) {
            JSONObject e = com.optJSONObject(i);
            if (e != null && e.optString("text").contains("VAR Decision:")) lastDecision = i;
            JSONObject t = e == null ? null : e.optJSONObject("time");
            lines.add(new String[]{e == null ? "" : e.optString("text"), t == null ? "" : t.optString("displayValue")});
        }
        for (int i = 0; i < com.length(); i++) {
            JSONObject e = com.optJSONObject(i);
            if (e == null) continue;
            String text = e.optString("text");
            boolean decided = text.contains("VAR Decision:"), checking = text.contains("VAR checking");
            if (!decided && !checking || checking && i < lastDecision) continue;
            String minute = e.optJSONObject("time") == null ? "" : e.optJSONObject("time").optString("displayValue");
            JSONObject play = e.optJSONObject("play");
            String pid = play == null ? "" : play.optString("id", "");
            String key = "I|" + g.id + "|" + (pid.isEmpty() ? "c" + e.optInt("sequence") : pid);
            String words, player = "";
            if (decided) {
                Matcher m = DECISION.matcher(text);
                if (!m.find()) continue;
                words = "VAR Decision: " + m.group(1).trim();
                String kind = kindOf(words);
                if (kind == null) continue;
                VarExplained.Call call = VarExplained.decision(lines, i);
                if (call != null) VarExplained.remember(g.id, key, call);
                if ("red".equals(kind) && call != null) player = call.player;
                if ("goal".equals(kind)) {
                    Matcher who = IN_BRACKETS.matcher(m.group(1).trim());
                    if (who.find()) player = who.group(1).trim();
                }
                if ("no_goal".equals(kind)) {
                    if (ruledOutAlready(already, minute)) continue;
                    // Whose goal it was: the line just before ("GOAL OVERTURNED BY VAR: Matheus Cunha (Manchester United) …").
                    for (int j = i - 1; j >= Math.max(0, i - 4); j--) {
                        JSONObject p = com.optJSONObject(j);
                        Matcher who = OVERTURNED.matcher(p == null ? "" : p.optString("text"));
                        if (who.find()) { player = who.group(1).trim(); break; }
                    }
                }
            } else {
                words = "VAR checking: " + checkReason(com, i);
                VarExplained.rememberCheck(key, VarExplained.checkAbout(lines, i));
            }
            out.add(new String[]{key, "📺  VAR", player, minute, words});
        }
        return out;
    }

    /** What the check is likely about, from the lines just before it: a goal, a penalty, a red card. */
    private static String checkReason(JSONArray com, int at) {
        for (int j = at - 1; j >= Math.max(0, at - 4); j--) {
            JSONObject p = com.optJSONObject(j);
            String t = p == null ? "" : p.optString("text");
            String lower = t.toLowerCase(Locale.ROOT);
            if (t.startsWith("Goal!") || t.startsWith("Own Goal")) return "Checking goal";
            if (lower.contains("penalty")) return "Possible penalty";
            if (lower.contains("red card")) return "Possible red card";
        }
        return "Checking an incident";
    }

    private static boolean ruledOutAlready(List<String[]> already, String minute) {
        int at = base(minute);
        for (String[] inc : already == null ? new ArrayList<String[]>() : already) {
            if (inc.length > 1 && inc[1] != null && inc[1].contains("ruled out") && Math.abs(base(inc[3]) - at) <= 1) return true;
        }
        return false;
    }

    /** penalty, no_penalty, goal, no_goal or red (a card upgraded) from ESPN's words; null for the rest. */
    static String kindOf(String words) {
        if (words == null) return null;
        String w = words.replaceFirst("^.*?VAR Decision:\\s*", "").toLowerCase(Locale.ROOT);
        if (w.startsWith("no penalty")) return "no_penalty";
        if (w.startsWith("penalty")) return "penalty";
        if (w.startsWith("no goal")) return "no_goal";
        if (w.startsWith("goal")) return "goal";
        if (w.startsWith("card upgraded")) return "red";
        return null;
    }

    /** The side a penalty decision is about ("VAR Decision: Penalty Chelsea" → Chelsea); null for any other. */
    private static String teamOf(String words) {
        if (words == null) return null;
        String w = words.replaceFirst("^.*?VAR Decision:\\s*", "").trim();
        String lower = w.toLowerCase(Locale.ROOT);
        String rest = lower.startsWith("no penalty") ? w.substring(10) : lower.startsWith("penalty") ? w.substring(7) : null;
        rest = rest == null ? null : rest.replaceAll("[.\\s]+$", "").trim();
        return rest == null || rest.isEmpty() ? null : rest;
    }

    private static String reasonFor(String kind) {
        return kind.contains("goal") ? "Checking goal" : "Possible penalty";
    }

    /** The decision's label: {words, the small line under them}; null for a kind it does not know. */
    private static String[] kindLabel(String kind) {
        switch (kind) {
            case "penalty": return new String[]{"PENALTY", "GIVEN"};
            case "no_penalty": return new String[]{"NO PENALTY", "VAR"};
            case "goal": return new String[]{"GOAL", "STANDS"};
            case "no_goal": return new String[]{"NO GOAL", "VAR"};
            case "red": return new String[]{"RED CARD", "VAR"};
            default: return null;
        }
    }

    /**
     * A VAR incident from the alert service's read (skyIncident): a check under way, or a decision from ESPN's
     * commentary, or a goal ruled out from its key events. True when it was one of these (shown here).
     */
    static boolean var(GoalWatchService s, ScoresTicker.Game g, String[] inc) {
        if (inc.length > 4 && inc[4] != null) {
            if (inc[4].startsWith("VAR checking")) {
                int at = inc[4].indexOf(": ");
                check(s, g, at > 0 ? inc[4].substring(at + 2) : "Checking an incident", inc[3], inc[0]);
                return true;
            }
            String kind = kindOf(inc[4]);
            if (kind == null) return false;
            decision(s, g, kind, teamOf(inc[4]), inc[2], inc[3], inc[0]);
            return true;
        }
        if (inc.length > 3 && inc[1] != null && inc[1].contains("ruled out")) {
            decision(s, g, "no_goal", null, inc[2], inc[3], inc[0]);
            return true;
        }
        return false;
    }

    /** The VAR check showing or waiting, and its game: its decision takes its place. Main thread. */
    private static ScoreCard.Data checking;
    private static String checkingGame;

    /** VAR CHECK, its amber dot beating, the reason big; the fixture and score at the right. */
    private static void check(GoalWatchService s, ScoresTicker.Game g, String reason, String minute, String key) {
        s.cardRun(() -> {
            try {
                ScoreCard.Data d = s.footballData(g);
                d.kind = "var";
                d.key = key;
                d.label = "VAR CHECK";
                d.labelColour = CHARCOAL;
                d.pulse = AMBER;
                d.headline = reason;
                d.line = "Check under way";
                d.minute = minute == null ? "" : minute;
                // Up until its decision comes (twenty seconds at most).
                d.showMs = 20_000L;
                AlertLog.note(s, "card queued: VAR check " + AlertLog.game(g) + ", " + reason + " " + d.minute);
                MAIN.post(() -> {
                    checking = d;
                    checkingGame = g.id;
                    ScoreCard.show(s, d);
                });
            } catch (RuntimeException e) {
                AlertLog.note(s, "card failed to build: VAR check " + AlertLog.game(g) + ": " + e);
            }
        });
    }

    /** The decision: PENALTY GIVEN or GOAL STANDS in green, NO PENALTY or NO GOAL in red; in its check's place. */
    private static void decision(GoalWatchService s, ScoresTicker.Game g, String kind, String team, String player, String minute, String key) {
        String[] label = kindLabel(kind);
        if (label == null) return;
        s.cardRun(() -> {
            try {
                ScoreCard.Data d = s.footballData(g);
                d.kind = "var";
                d.key = key;
                d.label = label[0];
                d.labelSmall = label[1];
                boolean given = "penalty".equals(kind) || "goal".equals(kind);
                d.labelColour = given ? GIVEN : REFUSED;
                String who = player == null ? "" : player.trim();
                switch (kind) {
                    case "penalty": d.headline = team != null ? "Penalty to " + team : "Penalty given"; break;
                    case "no_penalty": d.headline = team != null ? "No penalty for " + team : "No penalty"; break;
                    case "goal": d.headline = who.isEmpty() ? "The goal stands" : who + "'s goal stands"; break;
                    case "red": d.headline = who.isEmpty() ? "Card upgraded to red" : who + " sent off"; break;
                    default: d.headline = who.isEmpty() ? "Goal ruled out" : who + "'s goal ruled out";
                }
                d.line = "VAR decision";
                d.minute = minute == null ? "" : minute;
                AlertLog.note(s, "card queued: VAR decision " + AlertLog.game(g) + ", " + label[0] + " " + label[1] + " " + d.minute);
                MAIN.post(() -> inCheckPlace(s, g, d));
            } catch (RuntimeException e) {
                AlertLog.note(s, "card failed to build: VAR decision " + AlertLog.game(g) + ": " + e);
            }
        });
    }

    /** The decision up in place of its check: the check closed (or taken out of the queue), the decision first. */
    private static void inCheckPlace(GoalWatchService s, ScoresTicker.Game g, ScoreCard.Data d) {
        ScoreCard.Data before = g.id != null && g.id.equals(checkingGame) ? checking : null;
        if (before != null) { checking = null; checkingGame = null; }
        if (before != null && ScoreCard.showingNow(before)) {
            ScoreCard.closeIf(before);
            // Once the check has folded away (and given Home's lane back, at most 0.9 s), the decision next.
            MAIN.postDelayed(() -> ScoreCard.showFirst(s, d), 950L);
            return;
        }
        if (before != null) ScoreCard.closeIf(before);
        ScoreCard.show(s, d);
    }

    // ---- E: late drama --------------------------------------------------------------------------------------------

    /** A goal's place in the game: whose (home), the score after it, the scorer's side before it, its worst deficit. */
    private static final class Moment {
        boolean forHome;
        int h, a, before, worst;
        /** The score at the scoring side's worst, the bigger number first ("2–0"). */
        String worstScore = "";
    }

    /** The goals in the order they were scored ("45'+2'" before "46'"). */
    private static List<String[]> sorted(ScoresTicker.Game g) {
        List<String[]> list = new ArrayList<>(g.goals);
        list.sort((x, y) -> Integer.compare(minuteValue(x[2]), minuteValue(y[2])));
        return list;
    }

    /** The goal (by its key, else its scorer and minute, else the latest) replayed through the game's goals. */
    private static Moment moment(ScoresTicker.Game g, String key, String who, String minute) {
        List<String[]> goals = sorted(g);
        int found = -1;
        for (int i = 0; i < goals.size() && found < 0; i++) if (key != null && key.equals(goals.get(i)[0])) found = i;
        for (int i = 0; i < goals.size() && found < 0; i++) {
            String[] x = goals.get(i);
            if (who != null && who.equals(x[1]) && minute != null && minute.equals(x[2])) found = i;
        }
        if (found < 0) found = goals.size() - 1;
        if (found < 0) return null;
        int h = 0, a = 0, worstHome = 0, worstAway = 0;
        String worstHomeAt = "", worstAwayAt = "";
        for (int i = 0; i <= found; i++) {
            String[] x = goals.get(i);
            boolean forHome = "home".equals(x[3]);
            if (i == found) {
                Moment m = new Moment();
                m.forHome = forHome;
                m.before = forHome ? h - a : a - h;
                m.worst = forHome ? worstHome : worstAway;
                m.worstScore = forHome ? worstHomeAt : worstAwayAt;
                if (forHome) h++; else a++;
                m.h = h;
                m.a = a;
                return m;
            }
            if (forHome) h++; else a++;
            if (a - h > worstHome) { worstHome = a - h; worstHomeAt = a + "–" + h; }
            if (h - a > worstAway) { worstAway = h - a; worstAwayAt = h + "–" + a; }
        }
        return null;
    }

    /**
     * A goal card (skyFootballNow) in late drama's orange-red: an equaliser or a winner from the 85th minute (LATE
     * DRAMA), else a side back level or ahead after being two or more down (COMEBACK). adb's tests (a key from test())
     * show the one asked for.
     */
    static void drama(ScoresTicker.Game g, ScoreCard.Data d, String who, String minute, String side, String kind, String key, int[] at) {
        String force = key == null ? null : key.startsWith("test-drama|") ? "late" : key.startsWith("test-comeback|") ? "comeback" : null;
        String goalKey = force == null ? key : key.substring(key.indexOf('|') + 1);
        Moment m = moment(g, goalKey, who, minute);
        if (m == null) return;
        boolean levels = m.before == -1, leads = m.before == 0;
        boolean late = minuteValue(minute) >= 85 && (levels || leads);
        boolean comeback = m.worst >= 2 && (levels || leads);
        if ("late".equals(force)) late = true;
        if ("comeback".equals(force)) { comeback = true; late = false; }
        if (!late && !comeback) return;
        String home = d.homeName == null ? "" : d.homeName, away = d.awayName == null ? "" : d.awayName;
        String what = levels ? "equaliser" : leads && late ? "winner" : "go-ahead goal";
        String fixture = home + " " + m.h + "–" + m.a + " " + away;
        String down = m.worst >= 1 ? "from " + m.worstScore + " down" : "from behind";
        d.notify = "drama";
        d.band = DRAMA;
        d.bandDeep = DRAMA_DEEP;
        d.labelColour = DRAMA_LABEL;
        if (late) {
            d.label = "LATE DRAMA";
            d.labelSmall = minute + " " + (levels ? "EQUALISER" : "WINNER");
            d.line = minute + " " + what + " · " + fixture + (m.worst >= 2 ? " · " + down : "");
        } else {
            d.label = "COMEBACK";
            d.labelSmall = down.toUpperCase(Locale.UK);
            d.line = minute + " " + what + " · " + fixture;
        }
    }

    // ---- G: the predictions at full time ----------------------------------------------------------------------------

    /**
     * The result card for a finished game's picks (Predictions.settle; off the main thread): YOU CALLED IT for the exact
     * score, SO CLOSE for the right result; null when no pick earned a point (the leaderboard's card then).
     */
    static ScoreCard.Data prediction(Context c, ScoresTicker.Game g, JSONObject one, int h, int a) {
        JSONObject picks = one == null ? null : one.optJSONObject("p");
        if (picks == null) return null;
        List<String> exact = new ArrayList<>();
        String best = null;
        int[] bestPick = null;
        int bestErr = Integer.MAX_VALUE;
        for (Iterator<String> it = picks.keys(); it.hasNext(); ) {
            String who = it.next();
            JSONArray p = picks.optJSONArray(who);
            if (p == null || p.optInt(0, -1) < 0) continue;
            int ph = p.optInt(0), pa = p.optInt(1), pts = Predictions.score(ph, pa, h, a), err = Math.abs(ph - h) + Math.abs(pa - a);
            if (pts == 3) exact.add(who);
            else if (pts == 1 && err < bestErr) { best = who; bestPick = new int[]{ph, pa}; bestErr = err; }
        }
        if (exact.isEmpty() && best == null) return null;
        int week = exact.isEmpty() ? 0 : Predictions.correctThisWeek(c, exact.get(0));
        ScoreCard.Data d = predictionCard(c, g, exact, best, bestPick, h, a, week, Predictions.players(c).size() > 1);
        d.key = "P|" + g.id;
        return d;
    }

    /**
     * The predictions' results board at full time (Predictions.settle; off the main thread): every pick with what it
     * earned, best first, and the season's table; YOU CALLED IT with the gold cup when someone had it spot on. Slid out
     * beside the scoreboard when it is up (its pages: one a player, then the table), else the card. Null with no picks.
     */
    static ScoreCard.Data predictionBoard(Context c, ScoresTicker.Game g, JSONObject one, int h, int a) {
        JSONObject picks = one == null ? null : one.optJSONObject("p");
        if (picks == null) return null;
        List<Object[]> all = new ArrayList<>();
        for (Iterator<String> it = picks.keys(); it.hasNext(); ) {
            String who = it.next();
            JSONArray p = picks.optJSONArray(who);
            if (p != null) all.add(new Object[]{who, p.optInt(0, -1), p.optInt(1, -1)});
        }
        List<String[]> rows = PredictionBoard.results(all, h, a);
        if (rows.isEmpty()) return null;
        ScoreCard.Data d = GoalWatchService.footballCard(g);
        if (d == null) {
            d = new ScoreCard.Data();
            d.gameId = g.id;
            d.homeName = g.homeName != null ? g.homeName : g.home;
            d.awayName = g.awayName != null ? g.awayName : g.away;
            d.slug = g.slug;
        }
        d.kind = "prediction";
        d.key = "P|" + g.id;
        d.score = h + "–" + a;
        d.minute = "FT";
        d.watch = null;
        d.channel = null;
        d.channelLogo = null;
        boolean spotOn = rows.get(0)[2].startsWith("Spot on");
        if (spotOn) {
            d.label = "YOU CALLED IT";
            d.labelColour = GOLD;
            d.labelIcon = new Trophy();
        } else {
            d.label = "PREDICTIONS";
            d.labelSmall = "FULL TIME";
            d.labelColour = CLOSE;
        }
        StringBuilder head = new StringBuilder();
        for (String[] r : rows) {
            if (head.length() > 0) head.append("  ·  ");
            head.append(r[0]).append(' ').append(r[1]).append(' ').append(r[2].replace("Spot on · ", "✓ ").replace("Right result · ", "").replace("No points", "0"));
        }
        d.headline = head.toString();
        String table = Predictions.leaderboard(c);
        d.line = "Season: " + table;
        d.boardPages = PredictionBoard.boardPages(rows, table);
        d.showMs = 14_000L;
        AlertLog.note(c, "predictions board: " + AlertLog.game(g) + ", " + d.headline + " · " + d.line);
        return d;
    }

    /** YOU CALLED IT with the gold trophy ("Spot on 2–1 · +3 points · 4 correct this week"), or SO CLOSE ("You said 2–0"). */
    private static ScoreCard.Data predictionCard(Context c, ScoresTicker.Game g, List<String> exact, String close, int[] closePick,
                                                 int h, int a, int week, boolean many) {
        ScoreCard.Data d = GoalWatchService.footballCard(g);
        if (d == null) {
            d = new ScoreCard.Data();
            d.homeName = g.homeName != null ? g.homeName : g.home;
            d.awayName = g.awayName != null ? g.awayName : g.away;
            d.slug = g.slug;
        }
        d.kind = "prediction";
        d.score = h + "–" + a;
        d.minute = "FT";
        d.watch = null;
        d.channel = null;
        d.channelLogo = null;
        if (!exact.isEmpty()) {
            d.label = "YOU CALLED IT";
            d.labelColour = GOLD;
            d.labelIcon = new Trophy();
            d.headline = (many ? String.join(" and ", exact) + " · " : "") + "Spot on " + h + "–" + a;
            d.line = "+3 points" + (exact.size() > 1 ? " each" : "") + " · " + week + " correct this week";
        } else {
            d.label = "SO CLOSE";
            d.labelColour = CLOSE;
            d.headline = (many ? close + " said " : "You said ") + closePick[0] + "–" + closePick[1];
            d.line = "+1 point · the right result";
        }
        d.showMs = 11_000L;
        AlertLog.note(c, "predictions settled: " + AlertLog.game(g) + ", " + d.label + " " + d.headline + " " + d.line);
        return d;
    }

    /** A gold cup: its two handles, the bowl, the stem and the base, drawn to its bounds. */
    static final class Trophy extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
                handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cup = new Path();
        private final RectF left = new RectF(), right = new RectF();

        Trophy() {
            edge.setStyle(Paint.Style.STROKE);
            edge.setColor(0xFF4A2F00);
            handle.setStyle(Paint.Style.STROKE);
            handle.setColor(0xFFF6C744);
            handle.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onBoundsChange(Rect r) {
            float w = r.width(), h = r.height(), x = r.left, y = r.top;
            fill.setShader(new LinearGradient(x, y, x + w, y + h, 0xFFFFEA9A, 0xFFE3A400, Shader.TileMode.CLAMP));
            edge.setStrokeWidth(Math.max(1f, w * 0.045f));
            handle.setStrokeWidth(Math.max(1.5f, w * 0.085f));
            left.set(x + w * 0.05f, y + h * 0.13f, x + w * 0.37f, y + h * 0.47f);
            right.set(x + w * 0.63f, y + h * 0.13f, x + w * 0.95f, y + h * 0.47f);
            cup.reset();
            cup.moveTo(x + w * 0.2f, y + h * 0.07f);
            cup.lineTo(x + w * 0.8f, y + h * 0.07f);
            cup.lineTo(x + w * 0.8f, y + h * 0.3f);
            cup.cubicTo(x + w * 0.8f, y + h * 0.54f, x + w * 0.63f, y + h * 0.63f, x + w * 0.56f, y + h * 0.64f);
            cup.lineTo(x + w * 0.56f, y + h * 0.77f);
            cup.lineTo(x + w * 0.74f, y + h * 0.8f);
            cup.lineTo(x + w * 0.74f, y + h * 0.93f);
            cup.lineTo(x + w * 0.26f, y + h * 0.93f);
            cup.lineTo(x + w * 0.26f, y + h * 0.8f);
            cup.lineTo(x + w * 0.44f, y + h * 0.77f);
            cup.lineTo(x + w * 0.44f, y + h * 0.64f);
            cup.cubicTo(x + w * 0.37f, y + h * 0.63f, x + w * 0.2f, y + h * 0.54f, x + w * 0.2f, y + h * 0.3f);
            cup.close();
        }

        @Override public void draw(Canvas c) {
            c.drawArc(left, 90, 180, false, handle);
            c.drawArc(right, 270, 180, false, handle);
            c.drawPath(cup, fill);
            c.drawPath(cup, edge);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---- the rest -------------------------------------------------------------------------------------------------

    /** A game announced only on Home: a competition on Home's score pills that no followed club plays in. */
    static boolean homeOnly(Context c, ScoresTicker.Game g) {
        return g != null && g.slug != null && GoalWatchService.homeSlugs(c).contains(g.slug) && !GoalWatchService.followsAnywhere(c, g);
    }

    /** ESPN's real VAR decision for the test: in this game, or the latest one in the followed competitions. */
    private static Object[] realDecision(String[] leagues, String id) {
        int read = 0;
        for (int back = 0; back <= 7 && read < 24; back++) {
            for (ScoresTicker.Game g : day(leagues, back)) {
                if (g.state == 1 || id != null && !id.equals(g.id)) continue;
                read++;
                for (String[] inc : varLines(g, summary(g), null)) if (kindOf(inc[4]) != null) return new Object[]{g, inc};
                if (id != null || read >= 24) return null;
            }
        }
        return null;
    }

    /** The latest real late equaliser or winner (comeback: a side back level or ahead from two down) for the test. */
    private static Object[] dramaGoal(String[] leagues, String id, boolean comeback) {
        for (int back = 0; back <= 10; back++) {
            for (ScoresTicker.Game g : day(leagues, back)) {
                if (id != null && !id.equals(g.id)) continue;
                // The latest that fits; for the comeback the goal that brought them level first, else the one ahead.
                List<String[]> goals = sorted(g);
                String[] ahead = null;
                for (int i = goals.size() - 1; i >= 0; i--) {
                    String[] goal = goals.get(i);
                    Moment m = moment(g, goal[0], goal[1], goal[2]);
                    if (m == null || m.before != -1 && m.before != 0) continue;
                    if (!comeback && minuteValue(goal[2]) >= 85) return new Object[]{g, goal};
                    if (comeback && m.worst >= 2 && m.before == -1) return new Object[]{g, goal};
                    if (comeback && m.worst >= 2 && ahead == null) ahead = goal;
                }
                if (ahead != null) return new Object[]{g, ahead};
                if (id != null) return null;
            }
        }
        return null;
    }

    /** "89'" as 89, "90'+3'" as 93; 0 when there is none. */
    private static int minuteValue(String m) {
        if (m == null) return 0;
        String[] parts = m.split("\\+");
        return digits(parts[0]) + (parts.length > 1 ? digits(parts[1]) : 0);
    }

    /** "45'+2'" as 45: the minute before any added time. */
    private static int base(String m) {
        return m == null ? 0 : digits(m.split("\\+")[0]);
    }

    private static int digits(String s) {
        String d = s == null ? "" : s.replaceAll("[^0-9]", "");
        try {
            return d.isEmpty() ? 0 : Integer.parseInt(d);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parse(String n) {
        try {
            return Integer.parseInt(n.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static double parseNumber(String n) {
        try {
            return Double.parseDouble(n.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
