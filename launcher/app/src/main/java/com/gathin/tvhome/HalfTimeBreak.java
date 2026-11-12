package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Broadcast mode on the TV: the half-time break. The picture of the channel can't be read here (the TV draws the video
 * where a screenshot can't see it), so the adverts can't be told from the picture as the phone does; but when the game
 * being watched goes to half time, and when its second half starts, are known as they show on screen (the channel's
 * own wait, InStep). So a minute after half time shows (the whistle, the first replays), over the app (TiviMate), the
 * channel's sound goes and Homefield's own break fills the screen: the time to the second half counting down, fun facts
 * (Bangladesh first), a quiz question with its answer a few seconds on, today's prayer times from the mosque with the
 * next one counting down, the other games on now, the reminders, what to carry on watching, the headlines, and today
 * (the date, the Islamic date, the weather). A card every ten seconds or so; Left and Right go through them. OK or Back
 * goes back to the match at once; it goes by itself about forty seconds before the second half shows (as soon as the
 * scores see it start; fourteen minutes after half time showed if they haven't), and when Home is pressed. On until
 * switched off (Control Centre, Match: Broadcast mode), the sound left alone if that is switched off.
 */
final class HalfTimeBreak {
    private HalfTimeBreak() { }

    /** After half time shows: the whistle and the first replays, then the break. */
    static final long START_AFTER_MS = 60_000L;
    /** Half time's length, whistle to whistle. */
    static final long HALF_MS = 15 * 60_000L;
    /** Back on the channel this long before the second half shows. */
    static final long BACK_BEFORE_MS = 40_000L;
    /** The test's break ("See the break now"). */
    static final long TEST_MS = 2 * 60_000L;
    /** The longest a break can be, whatever happens. */
    static final long MAX_MS = 22 * 60_000L;
    private static final long CARD_MS = 10_000L, QUIZ_MS = 14_000L, ANSWER_MS = 7_000L;

    private static Handler main;
    private static ExecutorService io;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static synchronized ExecutorService io() {
        if (io == null) io = Executors.newSingleThreadExecutor();
        return io;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("half_time_break", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) { return prefs(c).getBoolean("on", true); }

    static void setOn(Context c, boolean v) {
        prefs(c).edit().putBoolean("on", v).apply();
        if (!v) main().post(HalfTimeBreak::close);
    }

    /** Whether the channel's sound goes while the break shows (on until switched off). */
    static boolean mutes(Context c) { return prefs(c).getBoolean("mute", true); }

    static void setMutes(Context c, boolean v) { prefs(c).edit().putBoolean("mute", v).apply(); }

    // ---- when ---------------------------------------------------------------------------------------------------

    /** Pure: when the break goes up, half time having been seen by the scores at seenAt, the channel's wait hold. */
    static long startsAt(long seenAt, long hold) {
        return seenAt + hold + START_AFTER_MS;
    }

    /**
     * Pure: when it ends: BACK_BEFORE_MS before the second half shows, once the scores have seen it start (at
     * secondSeenAt, 0 while not); until then, half time's length after half time showed, less the same.
     */
    static long endsAt(long htSeenAt, long hold, long secondSeenAt) {
        if (secondSeenAt > 0) return secondSeenAt + hold - BACK_BEFORE_MS;
        return htSeenAt + hold + HALF_MS - BACK_BEFORE_MS;
    }

    /** Pure: the line in the corner: "Second half in about 9 min" (estimated), "Back to the match in 0:42" (known). */
    static String countdown(long now, long ends, boolean known) {
        long left = Math.max(0, ends - now);
        if (known || left < 60_000L) {
            long s = (left + 999) / 1000;
            return "Back to the match in " + (s / 60) + ":" + String.format(Locale.UK, "%02d", s % 60);
        }
        long min = Math.max(1, Math.round(left / 60_000.0));
        return "Second half in about " + min + " min";
    }

    // ---- state ----------------------------------------------------------------------------------------------------

    private static Context app;
    private static String gameId;
    private static String gameLine = "";
    private static long htSeenAt, hold, secondSeenAt, ends, upAt;
    private static boolean test;
    private static List<ScoresTicker.Game> others = new ArrayList<>();
    private static final Runnable begin = HalfTimeBreak::begin;
    private static int tries;

    /**
     * Half time in the game watched, seen by the scores now (any thread): the break a minute after it shows, when an app
     * is in front then; all, the games as last read (for the others on now).
     */
    static void halfTime(Context c, ScoresTicker.Game g, List<ScoresTicker.Game> all, long holdMs) {
        if (g == null || g.id == null || !on(c)) return;
        long now = System.currentTimeMillis();
        Context a = c.getApplicationContext();
        String line = gameLine(g);
        List<ScoresTicker.Game> rest = new ArrayList<>();
        for (ScoresTicker.Game x : all) if (x != null && x.id != null && !x.id.equals(g.id) && x.state == 0) rest.add(x);
        main().post(() -> {
            close();
            app = a;
            gameId = g.id;
            gameLine = line;
            others = rest;
            htSeenAt = now;
            hold = holdMs;
            secondSeenAt = 0;
            test = false;
            tries = 0;
            main().removeCallbacks(begin);
            main().postDelayed(begin, Math.max(0, startsAt(now, holdMs) - System.currentTimeMillis()));
            AlertLog.note(a, "half-time break in " + (startsAt(now, holdMs) - now) / 1000 + " s for " + AlertLog.game(g));
        });
    }

    /** The second half of the game watched started, by the scores (any thread): the break ends just before it shows. */
    static void secondHalf(String id, long holdMs) {
        long now = System.currentTimeMillis();
        main().post(() -> {
            if (id == null || !id.equals(gameId) || test) return;
            secondSeenAt = now;
            hold = holdMs;
            ends = endsAt(htSeenAt, hold, secondSeenAt);
            if (window == null) {
                // Not up yet (or gone): it doesn't start now.
                main().removeCallbacks(begin);
                gameId = null;
            } else if (System.currentTimeMillis() >= ends) {
                close();
            }
        });
    }

    /** "See the break now" (Control Centre): the break for two minutes over whatever is on. */
    static void test(Context c) {
        main().post(() -> {
            close();
            app = c.getApplicationContext();
            gameId = "test";
            gameLine = "Your team 1–0 The other side · HT";
            others = new ArrayList<>();
            test = true;
            htSeenAt = System.currentTimeMillis();
            hold = 0;
            secondSeenAt = 0;
            load(true);
        });
    }

    /** A minute after half time showed: up, unless the game was hopped from, Home is in front, or a card is still up. */
    private static void begin() {
        if (app == null || gameId == null || !on(app)) return;
        if (HalfTimeHop.away(gameId)) {
            AlertLog.note(app, "half-time break: not shown (hopped to another game)");
            return;
        }
        if (GoalWatchService.homeInFront) {
            AlertLog.note(app, "half-time break: not shown (Home is in front)");
            return;
        }
        // The half-time card and the hop's card first: tried again every few seconds for a while.
        // The ad-break screen goes at half-time with broadcast mode on (AdBreakLogic.breaks): the break waits for it.
        if ((LineupCard.hopShowing() || InStepCard.isShowing() || QuickReply.isOpen() || AdBreak.isShowing()) && tries++ < 24) {
            main().postDelayed(begin, 5_000L);
            return;
        }
        load(false);
    }

    // ---- what it shows ------------------------------------------------------------------------------------------

    /** What the break shows, read off the main thread. */
    static final class Content {
        final List<String[]> facts = new ArrayList<>(), quiz = new ArrayList<>();
        /** Today's prayers, {name, begins, jamā'ah or ""}; whose times and the Islamic date. */
        final List<String[]> prayers = new ArrayList<>();
        String prayerHead;
        final List<String[]> games = new ArrayList<>(), reminders = new ArrayList<>(), watching = new ArrayList<>(), news = new ArrayList<>();
        String weather, tomorrow, sunset, hijri;
    }

    private static void load(boolean forTest) {
        Context a = app;
        if (a == null) return;
        List<ScoresTicker.Game> rest = others;
        io().execute(() -> {
            Content k = new Content();
            k.facts.addAll(BreakFacts.next(3));
            k.quiz.addAll(BreakFacts.questions(2));
            try {
                if (Prayer.on(a)) {
                    Prayer.refresh(a);
                    k.prayers.addAll(Prayer.today(a));
                    k.prayerHead = Prayer.heading(a);
                }
                k.hijri = Prayer.hijri(a);
            } catch (RuntimeException ignored) {
                // no prayer times this break
            }
            for (ScoresTicker.Game x : rest) {
                if (x.halftime || k.games.size() >= 5) continue;
                k.games.add(new String[]{x.detail == null ? "Live" : x.detail, gameLine(x)});
            }
            try {
                long now = System.currentTimeMillis();
                List<JSONObject> rs = new ArrayList<>(Reminders.all(a));
                rs.sort((p, q) -> Long.compare(p.optLong("s"), q.optLong("s")));
                for (JSONObject r : rs) {
                    long s = r.optLong("s");
                    if (s < now || s > now + 36 * 3_600_000L) continue;
                    k.reminders.add(new String[]{when(s), r.optString("t") + (r.optString("c").isEmpty() ? "" : "  ·  " + r.optString("c"))});
                    if (k.reminders.size() >= 5) break;
                }
            } catch (RuntimeException ignored) {
                // none
            }
            try {
                for (PagesV2.Title t : WatchHistory.titles(a, null)) {
                    k.watching.add(new String[]{t.title == null ? "" : t.title, t.what == null ? "" : t.what});
                    if (k.watching.size() >= 5) break;
                }
            } catch (RuntimeException ignored) {
                // none
            }
            try {
                String feeds = a.getString(R.string.news_panel_url);
                if (!feeds.isEmpty()) {
                    for (String[] st : NewsPanel.readAll(feeds)) {
                        k.news.add(new String[]{st.length > 3 && st[3] != null ? st[3] : "BBC NEWS", st[0]});
                        if (k.news.size() >= 5) break;
                    }
                }
            } catch (RuntimeException ignored) {
                // none
            }
            weather(a, k);
            main().post(() -> show(k, forTest));
        });
    }

    /** The weather now, tomorrow's and today's sunset, from Open-Meteo as Ambient mode reads it. */
    private static void weather(Context c, Content k) {
        String lat = c.getString(R.string.weather_latitude), lon = c.getString(R.string.weather_longitude);
        if (lat.isEmpty() || lon.isEmpty()) return;
        try {
            JSONObject json = new JSONObject(ScoresTicker.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon + "&current=temperature_2m,weather_code"
                    + "&daily=temperature_2m_max,precipitation_probability_max,sunset&forecast_days=2&timezone=auto"));
            JSONObject now = json.getJSONObject("current");
            String place = c.getString(R.string.weather_place);
            k.weather = Math.round(now.getDouble("temperature_2m")) + "°  ·  " + WeatherLine.describe(now.getInt("weather_code"))
                    + (place.isEmpty() ? "" : "  ·  " + place);
            JSONObject daily = json.optJSONObject("daily");
            if (daily != null && daily.getJSONArray("temperature_2m_max").length() > 1) {
                k.tomorrow = "Tomorrow " + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(1)) + "°";
                org.json.JSONArray rain = daily.optJSONArray("precipitation_probability_max");
                if (rain != null && !rain.isNull(1) && rain.getInt(1) >= 50) k.tomorrow += ", rain likely";
                org.json.JSONArray sets = daily.optJSONArray("sunset");
                if (sets != null && sets.length() > 0 && sets.getString(0).contains("T")) {
                    k.sunset = "Sunset " + sets.getString(0).substring(sets.getString(0).indexOf('T') + 1);
                }
            }
        } catch (Exception ignored) {
            // no weather this break
        }
    }

    private static String when(long at) {
        ZonedDateTime z = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
        LocalDate d = z.toLocalDate(), today = LocalDate.now();
        String day = d.equals(today) ? "Today" : d.equals(today.plusDays(1)) ? "Tomorrow" : z.format(DateTimeFormatter.ofPattern("EEE", Locale.UK));
        return day + " " + z.format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK));
    }

    /** "Arsenal 1–0 Chelsea". */
    static String gameLine(ScoresTicker.Game g) {
        String h = g.homeName != null ? g.homeName : g.home, a = g.awayName != null ? g.awayName : g.away;
        String hs = g.homeScore == null ? "" : g.homeScore, as = g.awayScore == null ? "" : g.awayScore;
        return (h == null ? "" : h) + "  " + hs + "–" + as + "  " + (a == null ? "" : a);
    }

    // ---- the screen -----------------------------------------------------------------------------------------------

    private static FrameLayout window;
    private static FrameLayout stage;
    private static LinearLayout dots;
    private static TextView status;
    private static final List<View> cards = new ArrayList<>();
    private static final List<Long> lengths = new ArrayList<>();
    private static final List<Runnable> onShow = new ArrayList<>();
    private static int at = -1;
    private static boolean mutedHere;
    /**
     * Pixels to the design's units: the screen's long side over 960, so the break fills a 1080p screen and a 4K one
     * alike (whatever density the TV reports), and where the TV draws its screen in 4K the words and shapes are drawn
     * at 4K's own sharpness (nothing in it is a picture to be stretched).
     */
    private static float d = 2f, fontScale = 1f;
    private static final Runnable turn = () -> step(1);
    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (window == null) return;
            long now = System.currentTimeMillis();
            // Home pressed (or the channel's app gone): the break goes with it.
            if (!test && GoalWatchService.homeInFront) {
                close();
                return;
            }
            if (now >= ends || now - upAt > MAX_MS) {
                close();
                return;
            }
            status.setText("●  " + (test ? "A test: back in " + countdown(now, ends, true).replace("Back to the match in ", "")
                    : countdown(now, ends, secondSeenAt > 0)));
            main().postDelayed(this, 1000L);
        }
    };

    private static void show(Content k, boolean forTest) {
        if (app == null || window != null) return;
        if (!forTest && (gameId == null || GoalWatchService.homeInFront)) return;
        long now = System.currentTimeMillis();
        upAt = now;
        ends = forTest ? now + TEST_MS : endsAt(htSeenAt, hold, secondSeenAt);
        if (now >= ends - 20_000L) return;
        Context sw = new GoalWatchService.SoftwareWindow(app);
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        d = unit(dm.widthPixels, dm.heightPixels);
        fontScale = Math.max(0.85f, Math.min(1.3f, app.getResources().getConfiguration().fontScale));
        FrameLayout win = new FrameLayout(sw);
        GradientDrawable ground = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF070A1E, 0xFF15103A, 0xFF2A0F2E});
        win.setBackground(ground);

        // Along the top: what this is and the game at its break; the time to the second half on the right.
        LinearLayout head = new LinearLayout(sw);
        head.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(sw, "YOUR HALF-TIME BREAK", 17, 0xFFFFFFFF, true);
        name.setLetterSpacing(0.16f);
        head.addView(name);
        TextView game = text(sw, gameLine + (forTest ? "" : "  ·  HT"), 15, 0xFFB8BED6, false);
        game.setPadding(0, px(4), 0, 0);
        head.addView(game);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hp.leftMargin = px(56);
        hp.topMargin = px(40);
        win.addView(head, hp);
        status = text(sw, "", 15, 0xFF0A0D33, true);
        status.setPadding(px(18), px(9), px(20), px(9));
        status.setBackground(round(0xFFFFFFFF, 22));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.rightMargin = px(56);
        sp.topMargin = px(42);
        win.addView(status, sp);

        // The cards in the middle.
        stage = new FrameLayout(sw);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-1, -1);
        gp.leftMargin = gp.rightMargin = px(56);
        gp.topMargin = px(130);
        gp.bottomMargin = px(96);
        win.addView(stage, gp);

        // Along the foot: where it is in the cards, and the keys.
        LinearLayout foot = new LinearLayout(sw);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        dots = new LinearLayout(sw);
        foot.addView(dots, new LinearLayout.LayoutParams(0, -2, 1f));
        foot.addView(text(sw, "◀ ▶  more     OK  back to the match", 14, 0xFFB8BED6, false));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fp.leftMargin = fp.rightMargin = px(56);
        fp.bottomMargin = px(40);
        win.addView(foot, fp);

        cards.clear();
        lengths.clear();
        onShow.clear();
        at = -1;
        build(sw, k);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // As the alerts: never the keys (the helpers bring them), touchable so it is drawn at full strength.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.setTitle("HalfTimeBreak");
        try {
            app.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(app, "the half-time break could not open: " + e);
            return;
        }
        window = win;
        win.setAlpha(0f);
        win.animate().alpha(1f).setDuration(400).start();
        if (!InputKeyService.running) AppSearchService.keysFor("break", true);
        mute(true);
        step(1);
        main().post(tick);
        AlertLog.note(app, "half-time break up" + (forTest ? " (test)" : "") + ", back in " + (ends - now) / 1000 + " s");
    }

    private static void build(Context c, Content k) {
        if (!k.facts.isEmpty()) card(fact(c, k.facts.get(0)), CARD_MS, null);
        if (k.prayers.size() >= 5) prayersCard(c, k);
        if (!k.quiz.isEmpty()) quizCard(c, k.quiz.get(0));
        if (!k.games.isEmpty()) card(rows(c, "ELSEWHERE NOW", 0xFF3DDC84, k.games), CARD_MS, null);
        if (k.facts.size() > 1) card(fact(c, k.facts.get(1)), CARD_MS, null);
        if (!k.reminders.isEmpty()) card(rows(c, "YOUR REMINDERS", 0xFFFFC93C, k.reminders), CARD_MS, null);
        if (!k.watching.isEmpty()) card(watching(c, k.watching), CARD_MS, null);
        if (k.quiz.size() > 1) quizCard(c, k.quiz.get(1));
        if (!k.news.isEmpty()) card(news(c, k.news), CARD_MS, null);
        card(today(c, k), 8_000L, null);
        if (k.facts.size() > 2) card(fact(c, k.facts.get(2)), CARD_MS, null);
        for (int i = 0; i < cards.size(); i++) {
            View dot = new View(c);
            dot.setBackground(round(0x55FFFFFF, 5));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(px(10), px(10));
            dp.rightMargin = px(8);
            dots.addView(dot, dp);
        }
    }

    private static void card(View v, long ms, Runnable shown) {
        v.setVisibility(View.GONE);
        stage.addView(v, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        cards.add(v);
        lengths.add(ms);
        onShow.add(shown);
    }

    /** The next card (by 1) or the one before (by -1); it turns by itself after its time. */
    private static void step(int by) {
        main().removeCallbacks(turn);
        if (window == null || cards.isEmpty()) return;
        if (at >= 0) {
            cards.get(at).setVisibility(View.GONE);
            dots.getChildAt(at).setBackground(round(0x55FFFFFF, 5));
        }
        at = ((at + by) % cards.size() + cards.size()) % cards.size();
        View v = cards.get(at);
        Runnable r = onShow.get(at);
        if (r != null) r.run();
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(300).start();
        dots.getChildAt(at).setBackground(round(0xFFFFFFFF, 5));
        main().postDelayed(turn, lengths.get(at));
    }

    // ---- the cards ------------------------------------------------------------------------------------------------

    private static LinearLayout heading(Context c, String label, int colour) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(c, label, 15, colour, true);
        l.setLetterSpacing(0.16f);
        col.addView(l);
        return col;
    }

    /** A fun fact, big: whose (Bangladesh's with its flag, the world's, football's). */
    private static View fact(Context c, String[] f) {
        boolean bd = BreakFacts.BANGLADESH.equals(f[0]), ball = BreakFacts.FOOTBALL.equals(f[0]);
        int colour = bd ? 0xFF3DDC84 : ball ? 0xFF8FC3FF : 0xFFFFC93C;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (bd) top.addView(new Flag(c), new LinearLayout.LayoutParams(px(36), px(22)));
        else top.addView(text(c, ball ? "⚽" : "💡", 18, 0xFFFFFFFF, false));
        TextView l = text(c, bd ? "FUN FACT · BANGLADESH" : ball ? "FUN FACT · FOOTBALL" : "DID YOU KNOW?", 15, colour, true);
        l.setLetterSpacing(0.16f);
        l.setPadding(px(12), 0, 0, 0);
        top.addView(l);
        col.addView(top);
        TextView t = wrap(c, f[1], 34, 0xFFFFFFFF, true);
        t.setPadding(0, px(16), px(120), 0);
        col.addView(t);
        View bar = new View(c);
        bar.setBackground(round(colour, 2));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(px(72), px(5));
        bp.topMargin = px(20);
        col.addView(bar, bp);
        return col;
    }

    /** A quiz question: the answer held back a few seconds, the time left drawn down as a bar, then shown. */
    private static void quizCard(Context c, String[] q) {
        LinearLayout col = heading(c, "QUIZ TIME", 0xFFFF7AC8);
        TextView t = wrap(c, q[0], 34, 0xFFFFFFFF, true);
        t.setPadding(0, px(14), px(120), 0);
        col.addView(t);
        FrameLayout track = new FrameLayout(c);
        track.setBackground(round(0x33FFFFFF, 3));
        View fill = new View(c);
        fill.setBackground(round(0xFFFF7AC8, 3));
        fill.setPivotX(0);
        track.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(px(320), px(6));
        tp.topMargin = px(22);
        col.addView(track, tp);
        TextView hint = text(c, "", 16, 0xFFB8BED6, false);
        hint.setPadding(0, px(10), 0, 0);
        col.addView(hint);
        TextView answer = text(c, q[1], 30, 0xFF0A0D33, true);
        answer.setPadding(px(22), px(12), px(22), px(12));
        answer.setBackground(round(0xFF3DDC84, 16));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.topMargin = px(12);
        col.addView(answer, ap);
        Runnable[] reveal = new Runnable[1];
        reveal[0] = () -> {
            track.setVisibility(View.INVISIBLE);
            hint.setText("The answer:");
            answer.setVisibility(View.VISIBLE);
        };
        card(col, QUIZ_MS, () -> {
            main().removeCallbacks(reveal[0]);
            answer.setVisibility(View.INVISIBLE);
            track.setVisibility(View.VISIBLE);
            hint.setText("Think you know? The answer's coming…");
            // The time left drawn down in steps (a few redraws a second, the window being drawn in software).
            long from = System.currentTimeMillis();
            Runnable drain = new Runnable() {
                @Override public void run() {
                    if (window == null) return;
                    long gone = System.currentTimeMillis() - from;
                    fill.setScaleX(Math.max(0f, 1f - gone / (float) ANSWER_MS));
                    if (gone < ANSWER_MS) main().postDelayed(this, 200L);
                }
            };
            drain.run();
            main().postDelayed(reveal[0], ANSWER_MS);
        });
    }

    /** The day's prayer times: whose and the Islamic date, each prayer's beginning with its jamā'ah under, the next. */
    private static void prayersCard(Context c, Content k) {
        LinearLayout col = heading(c, "PRAYER TIMES" + (k.prayerHead == null ? "" : " · " + k.prayerHead), 0xFF5EE0C8);
        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, px(22), 0, 0);
        List<LinearLayout> cells = new ArrayList<>();
        boolean jamaah = false;
        for (String[] p : k.prayers) {
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            one.setPadding(px(6), px(14), px(6), px(14));
            boolean rise = "Sunrise".equals(p[0]);
            one.addView(text(c, p[0], 16, rise ? 0xFF8A90AA : 0xFFDDE3FF, true));
            TextView time = text(c, p[1], 30, rise ? 0xFF8A90AA : 0xFFFFFFFF, true);
            time.setFontFeatureSettings("tnum");
            one.addView(time);
            String j = p.length > 2 && p[2] != null ? p[2] : "";
            if (!j.isEmpty()) jamaah = true;
            TextView jt = text(c, j.isEmpty() ? " " : j, 16, 0xFF5EE0C8, false);
            jt.setFontFeatureSettings("tnum");
            one.addView(jt);
            one.setTag(p[0]);
            cells.add(one);
            row.addView(one, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (jamaah) {
            TextView key = text(c, "When each prayer's time begins, the mosque's jamā'ah under it", 14, 0xFF8A90AA, false);
            key.setPadding(0, px(6), 0, 0);
            col.addView(key);
        }
        TextView next = text(c, "", 20, 0xFF5EE0C8, true);
        next.setPadding(0, px(18), 0, 0);
        col.addView(next);
        Context a = app;
        card(col, 11_000L, () -> {
            String line = a == null ? null : Prayer.nextLine(a);
            next.setText(line == null ? "" : "Next: " + line);
            Prayer.Slot n = a == null ? null : Prayer.next(a);
            for (LinearLayout one : cells) {
                boolean lit = n != null && n.label().equals(one.getTag()) && n.begins.toLocalDate().equals(LocalDate.now());
                one.setBackground(lit ? round(0x335EE0C8, 14) : null);
            }
        });
    }

    /** Lines: {when or minute, words}. */
    private static View rows(Context c, String label, int colour, List<String[]> lines) {
        LinearLayout col = heading(c, label, colour);
        for (String[] l : lines) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(16), 0, 0);
            TextView when = text(c, l[0], 20, colour, true);
            when.setFontFeatureSettings("tnum");
            r.addView(when, new LinearLayout.LayoutParams(px(170), -2));
            r.addView(text(c, l[1], 24, 0xFFFFFFFF, false), new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(r);
        }
        return col;
    }

    private static View watching(Context c, List<String[]> titles) {
        LinearLayout col = heading(c, "CARRY ON WATCHING AFTER THE MATCH", 0xFF8FC3FF);
        for (String[] t : titles) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(16), 0, 0);
            r.addView(text(c, t[0], 26, 0xFFFFFFFF, true));
            if (!t[1].isEmpty()) {
                TextView w = text(c, "   " + t[1], 20, 0xFFB8BED6, false);
                r.addView(w);
            }
            col.addView(r);
        }
        return col;
    }

    private static View news(Context c, List<String[]> stories) {
        LinearLayout col = heading(c, "THE HEADLINES", 0xFFFFC21A);
        for (String[] s : stories) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, px(14), 0, 0);
            String tag = s[0].replace("BBC ", "");
            TextView src = text(c, tag, 13, 0xFF0A0D33, true);
            src.setBackground(round(tag.contains("SPORT") ? 0xFFFFC21A : 0xFFFF6B6B, 5));
            src.setPadding(px(8), px(2), px(8), px(2));
            r.addView(src);
            TextView t = text(c, s[1], 22, 0xFFFFFFFF, false);
            t.setPadding(px(14), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(r);
        }
        return col;
    }

    /** Today: the date, the Islamic date, the weather now and tomorrow, sunset. */
    private static View today(Context c, Content k) {
        LinearLayout col = heading(c, "TODAY", 0xFF8FC3FF);
        TextView day = text(c, LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)), 40, 0xFFFFFFFF, true);
        day.setPadding(0, px(10), 0, 0);
        col.addView(day);
        if (k.hijri != null) {
            TextView h = text(c, k.hijri, 22, 0xFF5EE0C8, true);
            h.setPadding(0, px(6), 0, 0);
            col.addView(h);
        }
        if (k.weather != null) {
            TextView w = text(c, k.weather + (k.tomorrow == null ? "" : "   ·   " + k.tomorrow), 24, 0xFFFFFFFF, false);
            w.setPadding(0, px(18), 0, 0);
            col.addView(w);
        }
        if (k.sunset != null) {
            TextView s = text(c, k.sunset, 20, 0xFFFFC93C, false);
            s.setPadding(0, px(8), 0, 0);
            col.addView(s);
        }
        return col;
    }

    /** Bangladesh's flag: a green field, the red disc a little towards the pole. */
    private static final class Flag extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Flag(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), ht = getHeight();
            p.setColor(0xFF006A4E);
            cv.drawRoundRect(0, 0, w, ht, ht * 0.12f, ht * 0.12f, p);
            p.setColor(0xFFF42A41);
            cv.drawCircle(w * 0.45f, ht / 2f, ht * 0.4f, p);
        }
    }

    // ---- keys, sound, away ----------------------------------------------------------------------------------------

    /**
     * The remote while it is up: OK or Back back to the match, Left and Right through the cards, Up and Down kept from
     * the app (so the channel isn't changed behind it); the rest (the volume) go on.
     */
    static boolean key(KeyEvent e) {
        if (window == null) return false;
        int code = e.getKeyCode();
        boolean up = e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || QuietHold.isOk(code)) {
            if (up) main().post(HalfTimeBreak::close);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) main().post(() -> step(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
            return true;
        }
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN;
    }

    static boolean isShowing() {
        return window != null;
    }

    /** The channel's sound off while the break shows (on) and back after, unless it was off already. */
    private static void mute(boolean on) {
        if (app == null) return;
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am == null) return;
        try {
            if (on) {
                if (!mutes(app) || am.isStreamMute(AudioManager.STREAM_MUSIC)) return;
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                mutedHere = true;
            } else if (mutedHere) {
                mutedHere = false;
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            }
        } catch (RuntimeException ignored) {
            // the TV keeps its sound as it is
        }
    }

    /** The break away (OK, Back, its time up, Home), the sound back. */
    static void close() {
        main().removeCallbacks(turn);
        main().removeCallbacks(tick);
        FrameLayout v = window;
        window = null;
        mute(false);
        if (v == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("break", false);
        if (app != null) AlertLog.note(app, "half-time break away");
        try {
            v.getContext().getSystemService(WindowManager.class).removeView(v);
        } catch (RuntimeException ignored) {
            // gone already
        }
        cards.clear();
        lengths.clear();
        onShow.clear();
    }

    // ---- small things ---------------------------------------------------------------------------------------------

    /** Pure: pixels per design unit for a screen this size: its long side over 960 (2 at 1080p, 4 at 4K). */
    static float unit(int widthPx, int heightPx) {
        int longSide = Math.max(widthPx, heightPx);
        return longSide <= 0 ? 2f : longSide / 960f;
    }

    private static int px(float v) {
        return Math.round(v * d);
    }

    private static GradientDrawable round(int colour, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radiusDp * d);
        return g;
    }

    private static TextView text(Context c, String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * d * fontScale);
        v.setTextColor(colour);
        v.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static TextView wrap(Context c, String s, float sp, int colour, boolean bold) {
        TextView v = text(c, s, sp, colour, bold);
        v.setSingleLine(false);
        v.setMaxLines(4);
        v.setLineSpacing(0, 1.12f);
        return v;
    }
}
