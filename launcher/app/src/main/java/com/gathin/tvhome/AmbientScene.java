package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ambient mode: what the TV shows when nobody is using it. A large thin clock and the date, the weather now and
 * tomorrow's, and on the right a few glanceable cards: the next game (or the one on now, with its score), the next
 * Formula 1 session, the camera's last motion, and the car: on the way home, how far away it is and when it gets
 * back (the phone's own estimate), else its latest news and charge (the Tesla app's notifications, passed on by the
 * phone). Along the foot, a bar that stays put and turns its page every few seconds: the games on now, the next
 * hours' weather, the rest of today's games, today's results and the news. The clock and the cards stay in one place
 * (a theme's "ambient_drift" moves them a little every minute and swaps their sides every five, for a screen that
 * can burn in); the bar's words change, and everything dims late at night. It fades in when it starts, the clock
 * first, then the cards and the bar. It never keeps the screen on by itself, so the TV still goes
 * to sleep on its own timer. Shared by AmbientActivity (Home left idle) and AmbientDream (the system screensaver).
 *
 * Laid out on a 1920 x 1080 design, scaled to the screen, as the mock-up was drawn.
 */
final class AmbientScene {
    private static final long DATA_MS = 30 * 60_000L;

    private final Context c;
    private final float k;
    private final FrameLayout root, stage;
    private final TextView clock, date, weather, tomorrow;
    private final ImageView weatherIcon;
    /** Today's prayers on the sun's path, under the weather (when the theme asks for prayer times). */
    private final PrayerArc prayerArc;
    private final LinearLayout cards, strip, left, page;
    /** The bar's pages ({label, items}), the one showing, and how long each stays. */
    private final List<Strip> pages = new ArrayList<>();
    private int pageAt = -1;
    /** The facts, Bangladesh first and twice as often (AmbientFacts), and the next one to show. */
    private final List<String[]> facts = AmbientFacts.order();
    private int factAt;
    private static final long PAGE_MS = 12_000L, LIVE_MS = 3 * 60_000L;
    /** When the scene started, and whether the clock is on the right (it changes sides every few minutes). */
    private long startedAt;
    private boolean flipped;
    private static final long FLIP_MS = 5 * 60_000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private boolean running;
    /** The theme's "ambient_drift": the clock and cards move about (off: they stay where they are). */
    private final boolean drift;
    /** The cards from the last read (the game, F1, the camera); the car's is made fresh each time they are shown. */
    private final List<Card> others = new ArrayList<>();
    /** Ambient mode is on screen (Home left idle, or the screensaver): the alerts stand above its bar, not on it. */
    static boolean showing() {
        java.lang.ref.WeakReference<AmbientScene> l = live;
        return l != null && l.get() != null;
    }

    /** How high off the foot an alert's card stands while Ambient mode shows: clear of its bar (36 + 84), in design px. */
    static final int ALERT_FOOT = 140;

    /** The scene on screen now, told at once when the car's news comes in. */
    private static volatile java.lang.ref.WeakReference<AmbientScene> live;

    AmbientScene(Context context) {
        c = context;
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        drift = Config.themeFlag(c, "ambient_drift", false);
        root = new FrameLayout(c);
        GradientDrawable ground = new GradientDrawable();
        ground.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ground.setColors(new int[]{0xFF1C2A8A, 0xFF0B1146, 0xFF03051A});
        ground.setGradientCenter(0.72f, 0.18f);
        ground.setGradientRadius(1300 * k);
        root.setBackground(ground);
        stage = new FrameLayout(c);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = text(280, "sans-serif-thin", 0xE6FFFFFF);
        clock.setLetterSpacing(-0.03f);
        clock.setIncludeFontPadding(false);
        // One line, as wide as the widest time ("23:59" at this size is under 760): measured by the font, the box came
        // out narrower than the thin digits draw on some TVs, and the minutes were cut off.
        clock.setSingleLine(true);
        left.addView(clock, new LinearLayout.LayoutParams(px(780), -2));
        date = text(36, "sans-serif-light", 0xD9FFFFFF);
        left.addView(date, margins(10, 6, 0, 0));
        LinearLayout wx = new LinearLayout(c);
        wx.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(c);
        wx.addView(weatherIcon, new LinearLayout.LayoutParams(px(54), px(54)));
        weather = text(30, "sans-serif-light", 0xFFFFFFFF);
        wx.addView(weather, margins(18, 0, 0, 0));
        left.addView(wx, margins(10, 30, 0, 0));
        tomorrow = text(22, "sans-serif", 0xA6FFFFFF);
        left.addView(tomorrow, margins(10, 10, 0, 0));
        prayerArc = new PrayerArc(c, k, true, c.getResources().getFont(R.font.outfit_extrabold),
                c.getResources().getFont(R.font.inter_semibold), c.getResources().getFont(R.font.inter_regular));
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(px(640), px(210));
        pa.setMargins(px(10), px(36), 0, 0);
        left.addView(prayerArc, pa);
        showArc();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(140);
        lp.topMargin = px(150);
        stage.addView(left, lp);

        cards = new LinearLayout(c);
        cards.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(px(600), -2, Gravity.END | Gravity.TOP);
        cp.rightMargin = px(120);
        cp.topMargin = px(160);
        stage.addView(cards, cp);

        strip = new LinearLayout(c);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(px(140), 0, px(140), 0);
        strip.setBackgroundColor(0x0DFFFFFF);
        // Outside the drifting stage: the bar stays where it is (it moved with everything else, a band the width of
        // the screen sliding about every minute); its words change page every few seconds instead, so none stay put.
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, px(84), Gravity.BOTTOM);
        sp.bottomMargin = px(36);
        root.addView(strip, sp);
        page = new LinearLayout(c);
        page.setGravity(Gravity.CENTER_VERTICAL);
        strip.addView(page, new LinearLayout.LayoutParams(0, -1, 1f));
        // Facts at once, before the games, weather and news are read.
        showStrip(new ArrayList<>());
    }

    View view() {
        return root;
    }

    void start() {
        if (running) return;
        running = true;
        live = new java.lang.ref.WeakReference<>(this);
        startedAt = System.currentTimeMillis();
        tick.run();
        refresh.run();
        if (drift) main.postDelayed(flip, FLIP_MS);
        main.postDelayed(turn, PAGE_MS);
        intro();
    }

    private boolean introDone;

    /**
     * Coming in: the scene fades up from dark, the clock and the weather rising a little into place first, then the
     * cards on the right, then the bar along the foot.
     */
    private void intro() {
        introDone = false;
        android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);
        root.setAlpha(0f);
        root.animate().alpha(1f).setStartDelay(0).setDuration(900).setInterpolator(ease).start();
        View[] parts = {left, cards, strip};
        long[] delay = {250, 650, 1000};
        for (int i = 0; i < parts.length; i++) {
            View v = parts[i];
            boolean last = i == parts.length - 1;
            v.setAlpha(0f);
            v.setTranslationY(px(i == 2 ? 20 : 34));
            v.animate().alpha(last ? dim() : 1f).translationY(0).setStartDelay(delay[i]).setDuration(1_200).setInterpolator(ease)
                    .withEndAction(last ? () -> introDone = true : null).start();
        }
    }

    void stop() {
        running = false;
        if (live != null && live.get() == this) live = null;
        main.removeCallbacksAndMessages(null);
        stage.animate().cancel();
    }

    void close() {
        stop();
        io.shutdownNow();
    }

    /** Each minute, on the minute: the clock, a small drift of everything, and a dimmer screen late at night. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            ZonedDateTime now = ZonedDateTime.now();
            clock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
            date.setText(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)));
            if (drift) stage.animate().translationX((random.nextFloat() * 80 - 40) * k).translationY((random.nextFloat() * 50 - 25) * k)
                    .alpha(dim()).setDuration(4_000).start();
            else stage.animate().alpha(dim()).setDuration(4_000).start();
            if (introDone) strip.animate().alpha(dim()).setDuration(4_000).start();
            // The sun (or the moon) moves along the prayers' path.
            showArc();
            // On the way home, the minutes left and the time home move on each minute.
            if (tripActive()) showCards();
            long toNext = 60_000L - (System.currentTimeMillis() % 60_000L) + 50;
            main.postDelayed(this, toNext);
        }
    };

    /** Full brightness for the first five minutes, then a little lower; lower still from 23:00 to 06:00. */
    private float dim() {
        int h = ZonedDateTime.now().getHour();
        if (h >= 23 || h < 6) return 0.55f;
        return System.currentTimeMillis() - startedAt < 5 * 60_000L ? 1f : 0.8f;
    }

    /**
     * Every five minutes the clock and the cards change sides, through a fade: with the minute's drift, no part of the
     * screen shows the same thing for long.
     */
    private final Runnable flip = new Runnable() {
        @Override public void run() {
            if (!running) return;
            stage.animate().cancel();
            stage.animate().alpha(0f).setDuration(700).withEndAction(() -> {
                flipped = !flipped;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) left.getLayoutParams();
                lp.gravity = (flipped ? Gravity.END : Gravity.START) | Gravity.TOP;
                lp.leftMargin = flipped ? 0 : px(140);
                lp.rightMargin = flipped ? px(140) : 0;
                left.setLayoutParams(lp);
                left.setGravity(flipped ? Gravity.END : Gravity.START);
                clock.setGravity(flipped ? Gravity.END : Gravity.START);
                FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cards.getLayoutParams();
                cp.gravity = (flipped ? Gravity.START : Gravity.END) | Gravity.TOP;
                cp.leftMargin = flipped ? px(120) : 0;
                cp.rightMargin = flipped ? 0 : px(120);
                cards.setLayoutParams(cp);
                stage.animate().alpha(dim()).setDuration(900).start();
            }).start();
            main.postDelayed(this, FLIP_MS);
        }
    };

    /** The weather, games and the rest, read again every half hour. */
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!running) return;
            io.execute(AmbientScene.this::load);
            main.postDelayed(this, DATA_MS);
        }
    };

    private void load() {
        Object[] wx = weather();
        List<Card> found = new ArrayList<>();
        List<Object[]> results = new ArrayList<>(), onNow = new ArrayList<>(), later = new ArrayList<>();
        Card match = match(results, onNow, later);
        f1Later(later);
        if (match != null) found.add(match);
        Card f1 = f1();
        if (f1 != null) found.add(f1);
        Card cam = camera();
        if (cam != null) found.add(cam);
        List<String[]> news = new ArrayList<>();
        try {
            String feeds = c.getString(R.string.news_panel_url);
            if (!feeds.isEmpty()) news.addAll(NewsPanel.readAll(feeds));
        } catch (RuntimeException ignored) {
            // no news: the bar goes without
        }
        Prayer.refresh(c);
        List<Strip> made = strips(wx, onNow, later, results, news);
        main.post(() -> {
            if (!running) return;
            if (wx != null) {
                weather.setText((String) wx[0]);
                weatherIcon.setImageResource((Integer) wx[1]);
                tomorrow.setText((String) wx[2]);
            }
            showArc();
            others.clear();
            others.addAll(found);
            showCards();
            showStrip(made);
            // A game on: its score kept fresh on the bar (and its card) every few minutes, not every half hour.
            main.removeCallbacks(liveAgain);
            if (!onNow.isEmpty()) main.postDelayed(liveAgain, LIVE_MS);
        });
    }

    private final Runnable liveAgain = () -> { if (running) io.execute(this::load); };

    /** The car's news came in from the phone (GoalWatchService): the cards again, at once, if Ambient is on screen. */
    static void carNews() {
        java.lang.ref.WeakReference<AmbientScene> ref = live;
        AmbientScene scene = ref == null ? null : ref.get();
        if (scene != null) scene.main.post(() -> { if (scene.running) scene.showCards(); });
    }

    /** The car's card first (it is the most timely), then the game, F1 and the camera. */
    private void showCards() {
        cards.removeAllViews();
        List<Card> all = new ArrayList<>();
        Card car = car();
        if (car != null) all.add(car);
        all.addAll(others);
        for (Card card : all) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.bottomMargin = px(18);
            cards.addView(card.view(), p);
        }
    }

    // ---- what the cards say --------------------------------------------------------------------------------

    /** {"8° · Clear · Manchester", icon, "Tomorrow 11°, rain likely"}, from Open-Meteo as Home's weather line. */
    private Object[] weather() {
        String lat = c.getString(R.string.weather_latitude), lon = c.getString(R.string.weather_longitude);
        if (lat.isEmpty() || lon.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(ScoresTicker.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon + "&current=temperature_2m,weather_code,is_day"
                    + "&daily=temperature_2m_max,precipitation_probability_max,sunrise,sunset&forecast_days=2&timezone=auto"
                    + "&hourly=temperature_2m,weather_code,is_day&forecast_hours=6"));
            JSONObject now = json.getJSONObject("current");
            int code = now.getInt("weather_code");
            boolean day = now.optInt("is_day", 1) == 1;
            String place = c.getString(R.string.weather_place);
            String line = Math.round(now.getDouble("temperature_2m")) + "°  ·  " + WeatherLine.describe(code)
                    + (place.isEmpty() ? "" : "  ·  " + place);
            String next = "";
            JSONObject daily = json.optJSONObject("daily");
            if (daily != null && daily.getJSONArray("temperature_2m_max").length() > 1) {
                next = "Tomorrow " + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(1)) + "°";
                org.json.JSONArray rain = daily.optJSONArray("precipitation_probability_max");
                if (rain != null && !rain.isNull(1) && rain.getInt(1) >= 50) next += ", rain likely";
            }
            // The next few hours, on the hour, for the bar: {"18:00", "10°", icon}.
            List<Object[]> hours = new ArrayList<>();
            JSONObject hourly = json.optJSONObject("hourly");
            if (hourly != null) {
                org.json.JSONArray t = hourly.getJSONArray("time"), temp = hourly.getJSONArray("temperature_2m"), wc = hourly.getJSONArray("weather_code");
                org.json.JSONArray isDay = hourly.optJSONArray("is_day");
                for (int i = 1; i < t.length() && hours.size() < 4; i++) {
                    String at = t.getString(i);
                    hours.add(new Object[]{at.substring(at.indexOf('T') + 1), Math.round(temp.getDouble(i)) + "°",
                            WeatherLine.icon(wc.getInt(i), isDay == null || isDay.optInt(i, 1) == 1)});
                }
            }
            // Today's sunset, or once it has gone, tomorrow's sunrise (the place's own clock, as the hours are).
            String sunset = null;
            org.json.JSONArray sets = daily == null ? null : daily.optJSONArray("sunset"), rises = daily == null ? null : daily.optJSONArray("sunrise");
            String nowAt = now.optString("time", "");
            if (sets != null && sets.length() > 0) {
                String ss = sets.getString(0);
                if (ss.contains("T") && LocalDate.now().toString().equals(ss.substring(0, ss.indexOf('T')))) {
                    if (nowAt.isEmpty() || nowAt.compareTo(ss) < 0) sunset = "Sunset " + ss.substring(ss.indexOf('T') + 1);
                    else if (rises != null && rises.length() > 1 && rises.getString(1).contains("T"))
                        sunset = "Sunrise " + rises.getString(1).substring(rises.getString(1).indexOf('T') + 1);
                }
            }
            return new Object[]{line, WeatherLine.icon(code, day), next, hours, sunset};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The game on now in the followed competitions, else the next one in the coming week (one with a British
     * channel first); today's finished ones go into results.
     */
    private Card match(List<Object[]> results, List<Object[]> onNow, List<Object[]> later) {
        String[] leagues = c.getResources().getStringArray(R.array.goal_alert_leagues);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            List<ScoresTicker.Game> day = ScoresTicker.load(leagues, today.plusDays(i));
            day.sort((a, b) -> Long.compare(a.kickoff, b.kickoff));
            if (i == 0) {
                DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
                for (ScoresTicker.Game g : day) {
                    // Each game with both clubs' crests either side (fetched once, kept on disk).
                    if (g.state == 2 && results.size() < 4)
                        results.add(new Object[]{g.home + "  " + g.homeScore + "–" + g.awayScore + "  " + g.away, crest(g.homeLogo), crest(g.awayLogo), "FT"});
                    if (g.state == 0 && onNow.size() < 3)
                        onNow.add(new Object[]{g.home + "  " + g.homeScore + "–" + g.awayScore + "  " + g.away, crest(g.homeLogo), crest(g.awayLogo), ScoresTicker.liveClock(g)});
                    if (g.state == 1 && g.kickoff > now && later.size() < 4)
                        later.add(new Object[]{g.home + "  v  " + g.away, crest(g.homeLogo), crest(g.awayLogo),
                                Instant.ofEpochMilli(g.kickoff).atZone(zone).format(hm), channelLogo(g)});
                }
                for (ScoresTicker.Game g : day) if (g.state == 0) return gameCard(g, true);
            }
            List<ScoresTicker.Game> next = new ArrayList<>();
            for (ScoresTicker.Game g : day) if (g.state == 1 && g.kickoff > now) next.add(g);
            if (next.isEmpty()) continue;
            for (int j = 0; j < Math.min(4, next.size()); j++) {
                Card withChannel = gameCard(next.get(j), false);
                if (withChannel.hasChannel) return withChannel;
            }
            return gameCard(next.get(0), false);
        }
        return null;
    }

    /** The channel a game is on, as its logo (from the guide); null when no guide lists it. */
    private Bitmap channelLogo(ScoresTicker.Game g) {
        try {
            Object[] w = GoalWatchService.watchForGame(c, g);
            return w != null && w.length > 1 && w[1] instanceof Bitmap ? (Bitmap) w[1] : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A channel's logo at this height, as wide as it needs (up to five times the height). */
    private ImageView logoView(Bitmap b, int heightPx) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(b);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setAdjustViewBounds(true);
        v.setMaxHeight(px(heightPx));
        v.setMaxWidth(px(heightPx * 5));
        return v;
    }

    private Bitmap crest(String url) {
        try {
            return url == null ? null : ScoresTicker.cachedLogo(c, url, px(34));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The F1 sessions still to come today, with the F1 logo, among the games in time order. */
    private void f1Later(List<Object[]> later) {
        List<RaceWeekend.Race> calendar;
        try {
            calendar = Config.load(c).raceCalendar;
        } catch (RuntimeException e) {
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant now = Instant.now();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        Bitmap logo = null;
        boolean added = false;
        for (RaceWeekend.Race race : calendar) {
            for (RaceWeekend.Session s : race.sessions) {
                if (!s.start.isAfter(now) || !s.start.atZone(zone).toLocalDate().equals(today)) continue;
                if (logo == null) logo = crest(F1Results.F1_LOGO);
                later.add(new Object[]{s.name + "  ·  " + race.name, logo, null, s.start.atZone(zone).format(hm)});
                added = true;
            }
        }
        if (added) later.sort((a, b) -> String.valueOf(a[3]).compareTo(String.valueOf(b[3])));
        while (later.size() > 5) later.remove(later.size() - 1);
    }

    /** The prayers' path shown, and moved on, when prayer times are on and known; gone otherwise. */
    private void showArc() {
        if (prayerArc == null) return;
        boolean show = Prayer.on(c);
        if (show) {
            prayerArc.update();
            show = !prayerArc.empty();
        }
        prayerArc.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private Card gameCard(ScoresTicker.Game g, boolean live) {
        Card card = new Card();
        Object[] watch = null;
        try {
            watch = GoalWatchService.watchForGame(c, g);
        } catch (RuntimeException ignored) {
            // no guide: the game without its channel
        }
        String channel = null;
        if (watch != null && watch[0] instanceof List && !((List<?>) watch[0]).isEmpty()) channel = String.valueOf(((List<?>) watch[0]).get(0));
        card.hasChannel = channel != null;
        // The channel's logo instead of its name, where the guide has one.
        if (watch != null && watch.length > 1 && watch[1] instanceof Bitmap) {
            card.channelLogo = (Bitmap) watch[1];
            channel = null;
        }
        card.label = live ? "ON NOW · " + ScoresTicker.competition(g) : "NEXT MATCH · " + ScoresTicker.competition(g);
        card.title = live ? g.home + "  " + g.homeScore + "–" + g.awayScore + "  " + g.away : g.home + " v " + g.away;
        String when = live ? ScoresTicker.liveClock(g) : day(g.kickoff);
        card.note = when + (channel != null ? "  ·  " + channel : "");
        card.tint = live ? 0xFFE8112D : 0xFF2B3CC9;
        card.badge = live ? "LIVE" : "NEXT";
        // Both clubs' crests, each beside its own name on the title line; the competition's logo where the one crest was.
        if (g.homeLogo != null) card.homeCrest = ScoresTicker.cachedLogo(c, g.homeLogo, px(40));
        if (g.awayLogo != null) card.awayCrest = ScoresTicker.cachedLogo(c, g.awayLogo, px(40));
        if (g.leagueLogo != null) card.picture = ScoresTicker.cachedLogo(c, g.leagueLogo, px(64));
        return card;
    }

    /** The F1 session on now, else the next one on the calendar. */
    private Card f1() {
        List<RaceWeekend.Race> calendar;
        try {
            calendar = Config.load(c).raceCalendar;
        } catch (RuntimeException e) {
            return null;
        }
        Instant now = Instant.now();
        for (RaceWeekend.Race race : calendar) {
            for (RaceWeekend.Session s : race.sessions) {
                boolean on = !now.isBefore(s.start) && now.isBefore(s.start.plus(s.length()));
                if (!on && !s.start.isAfter(now)) continue;
                Card card = new Card();
                card.label = "FORMULA 1 · " + race.name.toUpperCase(Locale.UK);
                card.title = s.name;
                Object[] w = null;
                try {
                    w = GoalWatchService.f1Watch(c);
                } catch (RuntimeException ignored) {
                    // no guide: the name instead
                }
                card.channelLogo = w != null && w.length > 1 && w[1] instanceof Bitmap ? (Bitmap) w[1] : null;
                card.note = (on ? "On now" : day(s.start.toEpochMilli())) + (card.channelLogo != null ? "" : "  ·  Sky Sports F1");
                card.tint = 0xFFE10600;
                card.badge = "F1";
                return card;
            }
        }
        return null;
    }

    /** The first camera's last motion while the launcher has been running, with its snapshot when it had one. */
    private Card camera() {
        List<String> names = CameraAlert.cameras(c);
        String name = names.isEmpty() ? null : names.get(0);
        Object[] shot = CameraAlert.lastShot(name);
        if (shot == null || !(shot[1] instanceof Long)) return null;
        Card card = new Card();
        card.label = name.toUpperCase(Locale.UK) + " CAMERA";
        card.title = "Last motion " + Instant.ofEpochMilli((Long) shot[1]).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        card.note = "OK on the camera button for live view";
        card.tint = 0xFF2F6BFF;
        card.badge = "CAM";
        if (shot[0] instanceof Bitmap) card.picture = (Bitmap) shot[0];
        return card;
    }

    // ---- the car ---------------------------------------------------------------------------------------------------

    private static final long TRIP_MS = 45 * 60_000L, CAR_MS = 12 * 3_600_000L;
    private static final int TESLA_BLUE = 0xFF3E6AE1, TESLA_GREEN = 0xFF31C553;

    /** A trip home the phone told of in the last 45 minutes, not long since over. */
    private static boolean tripActive() {
        JSONObject trip = SmartPanel.trip;
        long gone = System.currentTimeMillis() - SmartPanel.tripAt;
        if (trip == null || gone > TRIP_MS) return false;
        int minutes = trip.optInt("minutes", -1);
        return minutes < 0 || gone < (minutes + 10) * 60_000L;
    }

    /**
     * On the way home: how far away the car is, the minutes left and the time it gets home, with the trip so far as a
     * bar in the Tesla app's blue. Otherwise the car's latest news from the last twelve hours (charging, locked, a
     * software update), with its charge as a bar. Null when the phone has told of neither.
     */
    private Card car() {
        long now = System.currentTimeMillis();
        String name = Config.themeString(c, "tesla_name", Config.themeString(c, "tesla_model", "Tesla"));
        JSONObject news = SmartPanel.car;
        boolean fresh = news != null && now - SmartPanel.carAt < CAR_MS;
        int battery = fresh ? news.optInt("battery", -1) : -1;
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        if (tripActive()) {
            JSONObject trip = SmartPanel.trip;
            int minutes = trip.optInt("minutes", -1);
            double miles = trip.optDouble("miles", -1);
            int gone = (int) ((now - SmartPanel.tripAt) / 60_000L);
            int left = minutes < 0 ? -1 : Math.max(0, minutes - gone);
            boolean arriving = trip.optBoolean("arriving") || left == 0;
            Card k = new Card();
            k.label = name.toUpperCase(Locale.UK) + (arriving ? "  ·  ALMOST HOME" : "  ·  HEADING HOME");
            String far = miles >= 0 ? String.format(Locale.UK, miles < 10 ? "%.1f mi" : "%.0f mi", miles) : null;
            k.title = arriving ? "Pulling in now" : far != null ? far + " from home" : left + " min from home";
            List<String> bits = new ArrayList<>();
            if (!arriving && left > 0) {
                bits.add(left + " min");
                bits.add("home by " + java.time.LocalTime.now().plusMinutes(left).format(hm));
            }
            if (battery >= 0) bits.add(battery + "% battery");
            if (bits.isEmpty()) bits.add("Updated " + Instant.ofEpochMilli(SmartPanel.tripAt).atZone(ZoneId.systemDefault()).format(hm));
            k.note = String.join("  ·  ", bits);
            k.icon = R.drawable.tesla_t;
            k.tint = 0xFF151515;
            double done = trip.optDouble("progress", -1);
            if (arriving) done = 1;
            k.progress = done < 0 ? -1 : (float) Math.min(1, done);
            k.barColour = TESLA_BLUE;
            return k;
        }
        if (!fresh) return null;
        String title = news.optString("title", "").trim(), status = news.optString("status", "").trim();
        String all = (title + " " + status).toLowerCase(Locale.ROOT);
        boolean charging = all.contains("charging") && !all.contains("complete") && !all.contains("stopped")
                && !all.contains("interrupted") && !all.contains("finished");
        Card k = new Card();
        k.label = name.toUpperCase(Locale.UK) + "  ·  TESLA";
        k.title = !title.isEmpty() ? title : !status.isEmpty() ? status : "Tesla";
        List<String> bits = new ArrayList<>();
        if (battery >= 0) {
            int miles = news.optInt("miles", -1);
            if (miles < 0) {
                int full;
                try {
                    full = Integer.parseInt(Config.themeString(c, "tesla_range_miles", "330").trim());
                } catch (NumberFormatException e) {
                    full = 330;
                }
                miles = Math.round(full * battery / 100f);
                bits.add(battery + "%  ≈ " + miles + " mi");
            } else {
                bits.add(battery + "%  " + miles + " mi");
            }
        }
        if (!status.isEmpty() && !status.equalsIgnoreCase(k.title)) bits.add(status);
        bits.add(Instant.ofEpochMilli(SmartPanel.carAt).atZone(ZoneId.systemDefault()).format(hm));
        k.note = String.join("  ·  ", bits);
        k.icon = R.drawable.tesla_t;
        k.tint = 0xFF151515;
        if (battery >= 0) {
            k.progress = battery / 100f;
            k.barColour = charging ? TESLA_GREEN : 0xD9FFFFFF;
        }
        return k;
    }

    /** "Today 17:30", "Tomorrow 15:00", "Sat 12:30". */
    private static String day(long at) {
        ZonedDateTime t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String time = t.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (t.toLocalDate().equals(today)) return "Today " + time;
        if (t.toLocalDate().equals(today.plusDays(1))) return "Tomorrow " + time;
        return t.format(DateTimeFormatter.ofPattern("EEE", Locale.UK)) + " " + time;
    }

    // ---- views ---------------------------------------------------------------------------------------------

    private final class Card {
        String label, title, note, badge;
        int tint;
        Bitmap picture;
        /** A game's two crests, either side of its title. */
        Bitmap homeCrest, awayCrest;
        boolean hasChannel;
        /** The channel it is on, as its logo (after the note, in place of its name). */
        Bitmap channelLogo;
        /** A brand mark drawn on the tint (the Tesla "T"), in place of a picture or a badge. */
        int icon;
        /** A bar under the words (the trip so far, the car's charge), 0 to 1; -1 for none. */
        float progress = -1;
        int barColour;

        View view() {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(26), px(22), px(26), px(22));
            GradientDrawable glass = new GradientDrawable();
            glass.setCornerRadius(px(24));
            glass.setColor(0x0FFFFFFF);
            glass.setStroke(Math.max(1, px(1)), 0x1FFFFFFF);
            row.setBackground(glass);
            View mark;
            if (icon != 0) {
                ImageView pic = new ImageView(c);
                pic.setImageResource(icon);
                pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
                pic.setPadding(px(14), px(14), px(14), px(14));
                GradientDrawable chip = new GradientDrawable();
                chip.setCornerRadius(px(18));
                chip.setColor(tint);
                pic.setBackground(chip);
                mark = pic;
            } else if (picture != null) {
                ImageView pic = new ImageView(c);
                pic.setImageBitmap(picture);
                pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
                mark = pic;
            } else {
                TextView b = text(15, "sans-serif-black", 0xFFFFFFFF);
                b.setText(badge);
                b.setGravity(Gravity.CENTER);
                GradientDrawable chip = new GradientDrawable();
                chip.setCornerRadius(px(18));
                chip.setColor(tint);
                b.setBackground(chip);
                mark = b;
            }
            row.addView(mark, new LinearLayout.LayoutParams(px(64), px(64)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView l = text(14, "sans-serif-medium", 0x99FFFFFF);
            l.setText(label);
            l.setLetterSpacing(0.14f);
            l.setSingleLine(true);
            l.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(l);
            TextView t = text(25, "sans-serif-medium", 0xFFFFFFFF);
            t.setText(title);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (homeCrest != null || awayCrest != null) {
                // A game: home crest, the title, away crest (each crest beside its own club).
                LinearLayout line = new LinearLayout(c);
                line.setGravity(Gravity.CENTER_VERTICAL);
                if (homeCrest != null) line.addView(crestView(homeCrest), new LinearLayout.LayoutParams(px(36), px(36)));
                // As wide as the words, at most what leaves room for the away crest beside them.
                t.setMaxWidth(px(360));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
                tl.leftMargin = homeCrest != null ? px(12) : 0;
                tl.rightMargin = awayCrest != null ? px(12) : 0;
                line.addView(t, tl);
                if (awayCrest != null) line.addView(crestView(awayCrest), new LinearLayout.LayoutParams(px(36), px(36)));
                words.addView(line, margins(0, 6, 0, 0));
            } else {
                words.addView(t, margins(0, 6, 0, 0));
            }
            TextView n = text(18, "sans-serif", 0xB3FFFFFF);
            n.setText(note);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (channelLogo != null) {
                // The time, then the channel's own logo (no name).
                LinearLayout noteLine = new LinearLayout(c);
                noteLine.setGravity(Gravity.CENTER_VERTICAL);
                noteLine.addView(n);
                noteLine.addView(logoView(channelLogo, 26), margins(14, 0, 0, 0));
                words.addView(noteLine, margins(0, 6, 0, 0));
            } else {
                words.addView(n, margins(0, 6, 0, 0));
            }
            if (progress >= 0) {
                FrameLayout track = new FrameLayout(c);
                GradientDrawable tg = new GradientDrawable();
                tg.setCornerRadius(px(4));
                tg.setColor(0x24FFFFFF);
                track.setBackground(tg);
                View fill = new View(c);
                GradientDrawable fg = new GradientDrawable();
                fg.setCornerRadius(px(4));
                fg.setColor(barColour);
                fill.setBackground(fg);
                track.addView(fill, new FrameLayout.LayoutParams(Math.max(px(8), Math.round(px(440) * Math.min(1f, progress))), -1));
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(px(440), px(8));
                tp.topMargin = px(12);
                words.addView(track, tp);
            }
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
            wp.leftMargin = px(22);
            row.addView(words, wp);
            return row;
        }
    }

    private ImageView crestView(Bitmap b) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(b);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    /** The foot: today's results (when there are any) and how to leave. */
    /** One page of the bar: its label (in a chip, red for LIVE) and its items ({words, icon or 0}). */
    private static final class Strip {
        final String label;
        final List<Object[]> items = new ArrayList<>();
        /** A headline: one item that takes the bar's width, cut short with an ellipsis if it is longer. */
        boolean wide;
        /** A fact's place: the next fact is shown each time it comes round. */
        boolean fact;

        Strip(String label) {
            this.label = label;
        }
    }

    /**
     * The bar's pages, in the order they come round: the games on now, the next hours' weather (and sunset), the rest
     * of today's games, today's results, then up to three headlines (local news first), one to a page.
     */
    private List<Strip> strips(Object[] wx, List<Object[]> onNow, List<Object[]> later, List<Object[]> results, List<String[]> news) {
        List<Strip> out = new ArrayList<>();
        if (!onNow.isEmpty()) {
            Strip p = new Strip("LIVE");
            p.items.addAll(onNow);
            out.add(p);
        }
        if (wx != null && wx.length > 3 && wx[3] instanceof List && !((List<?>) wx[3]).isEmpty()) {
            Strip p = new Strip("NEXT HOURS");
            for (Object o : (List<?>) wx[3]) {
                Object[] h = (Object[]) o;
                p.items.add(new Object[]{h[0] + "   " + h[1], h[2]});
            }
            if (wx[4] != null) p.items.add(new Object[]{wx[4], 0});
            out.add(p);
        }
        // Prayer times (when the theme asks for them), the next one marked; in Ramadan, sehri and iftar first.
        int ramadan = Prayer.on(c) ? Prayer.ramadanDay(c) : 0;
        if (ramadan > 0) {
            List<String[]> t = Prayer.today(c);
            Strip p = new Strip("RAMADAN · DAY " + ramadan);
            for (String[] x : t) {
                if ("Fajr".equals(x[0])) p.items.add(new Object[]{"Sehri ends " + x[1], 0});
                if ("Maghrib".equals(x[0])) p.items.add(new Object[]{"Iftar " + x[1], 0});
            }
            if (!p.items.isEmpty()) out.add(p);
        }
        if (Prayer.on(c) && !Prayer.today(c).isEmpty()) {
            Strip p = new Strip("PRAYER TIMES");
            Prayer.Slot next = Prayer.next(c);
            boolean nextToday = next != null && next.begins.toLocalDate().equals(LocalDate.now());
            for (String[] x : Prayer.today(c)) {
                if ("Sunrise".equals(x[0])) continue;
                // The time it begins, then the mosque's jamā'ah when it differs (Maghrib's is the same).
                boolean isNext = nextToday && x[0].equals(next.label());
                String j = x[2].isEmpty() || x[2].equals(x[1]) ? "" : " · " + x[2];
                p.items.add(new Object[]{x[0] + "  " + x[1] + j + (isNext ? "  ‹ next" : ""), 0});
            }
            out.add(p);
        }
        if (!later.isEmpty()) {
            Strip p = new Strip("LATER TODAY");
            p.items.addAll(later);
            out.add(p);
        }
        if (!results.isEmpty()) {
            Strip p = new Strip("TODAY'S RESULTS");
            p.items.addAll(results);
            out.add(p);
        }
        for (int i = 0; i < Math.min(3, news.size()); i++) {
            String[] st = news.get(i);
            Strip p = new Strip(st.length > 3 && st[3] != null ? st[3] : "NEWS");
            p.items.add(new Object[]{st[0], 0});
            p.wide = true;
            out.add(p);
        }
        return out;
    }

    /** A fact comes round once every five minutes; the news, games and weather between, each page for PAGE_MS. */
    private static final long FACT_MS = 5 * 60_000L;
    /** Whether the bar is showing a fact now, and when the last one came up. */
    private boolean onFact;
    private long factShownAt = Long.MIN_VALUE / 2;

    private void showStrip(List<Strip> made) {
        String was = !onFact && pageAt >= 0 && pageAt < pages.size() ? pages.get(pageAt).label : null;
        pages.clear();
        pages.addAll(made);
        // Read again: the same page where it is still there, so its words stay put.
        int keep = -1;
        for (int i = 0; was != null && i < pages.size(); i++) if (was.equals(pages.get(i).label)) { keep = i; break; }
        pageAt = keep >= 0 ? keep : Math.max(0, Math.min(pageAt, pages.size() - 1));
        // Nothing read yet: a fact meanwhile.
        if (pages.isEmpty() && !onFact) { onFact = true; factShownAt = android.os.SystemClock.uptimeMillis(); }
        fillPage();
    }

    /**
     * Every PAGE_MS (12 s, long enough to read), the bar's next page through a fade, the bar itself staying where it
     * is; once every five minutes a fact in between. With nothing else to show, a fact stays until the next one is due.
     */
    private final Runnable turn = new Runnable() {
        @Override public void run() {
            if (!running) return;
            main.postDelayed(this, PAGE_MS);
            long now = android.os.SystemClock.uptimeMillis();
            boolean factDue = now - factShownAt >= FACT_MS && !facts.isEmpty();
            boolean change = pages.isEmpty() ? factDue : onFact || factDue || pages.size() > 1;
            if (!change) return;
            page.animate().cancel();
            page.animate().alpha(0f).setDuration(450).withEndAction(() -> {
                if (pages.isEmpty()) {
                    onFact = true;
                    factShownAt = now;
                } else if (onFact) {
                    onFact = false;
                    pageAt = (pageAt + 1) % pages.size();
                } else if (factDue) {
                    onFact = true;
                    factShownAt = now;
                } else {
                    pageAt = (pageAt + 1) % pages.size();
                }
                fillPage();
                page.animate().alpha(1f).setDuration(550).start();
            }).start();
        }
    };

    private void fillPage() {
        page.removeAllViews();
        if (pages.isEmpty() && !onFact) return;
        Strip p = onFact || pages.isEmpty() ? new Strip("") : pages.get(Math.max(0, Math.min(pageAt, pages.size() - 1)));
        if (onFact) p.fact = true;
        if (p.fact && !facts.isEmpty()) {
            // The next fact: its chip (Bangladesh's in its flag's green, with the red disc) and its words.
            String[] f = facts.get(factAt++ % facts.size());
            p = new Strip(f[0]);
            p.wide = true;
            p.items.add(new Object[]{f[1], 0});
        }
        TextView chip = text(14, "sans-serif-black", 0xFFFFFFFF);
        chip.setText(p.label);
        chip.setLetterSpacing(0.14f);
        chip.setSingleLine(true);
        chip.setPadding(px(12), px(6), px(12), px(6));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(8));
        boolean bd = "BANGLADESH".equals(p.label);
        boolean prayer = "PRAYER TIMES".equals(p.label) || p.label.startsWith("RAMADAN");
        g.setColor("LIVE".equals(p.label) ? 0xFFE8112D : bd ? 0xFF006A4E : prayer ? 0xFF0F6B4F : "DID YOU KNOW?".equals(p.label) ? 0xFF5B3FC4 : 0x1FFFFFFF);
        chip.setBackground(g);
        if (bd) {
            // The flag's red disc before its name.
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(0xFFF42A41);
            disc.setSize(px(12), px(12));
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(disc, null, null, null);
            chip.setCompoundDrawablePadding(px(8));
        }
        String bbc = Badges.bbcSection(p.label);
        if (bbc != null) {
            // BBC News or BBC Sport, on brand: their blocks on their colour.
            ImageView mark = new ImageView(c);
            mark.setImageDrawable(Badges.bbcChip(k * 1.6f, bbc));
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mark.setAdjustViewBounds(true);
            page.addView(mark, new LinearLayout.LayoutParams(-2, px(38)));
        } else page.addView(chip);
        for (Object[] item : p.items) {
            LinearLayout one = new LinearLayout(c);
            one.setGravity(Gravity.CENTER_VERTICAL);
            // A crest or logo, the words, the second crest, then the time or minute ("19:45", "67'", "FT"): each crest
            // beside its own club, the time after the whole game.
            int icon = item[1] instanceof Integer ? (Integer) item[1] : 0;
            if (icon != 0 || item[1] instanceof Bitmap) {
                ImageView iv = new ImageView(c);
                if (item[1] instanceof Bitmap) iv.setImageBitmap((Bitmap) item[1]);
                else iv.setImageResource(icon);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                one.addView(iv, new LinearLayout.LayoutParams(px(30), px(30)));
                icon = 1;
            }
            TextView t = text(20, "sans-serif", 0xD9FFFFFF);
            t.setText((String) item[0]);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            one.addView(t, icon != 0 ? margins(10, 0, 0, 0) : new LinearLayout.LayoutParams(p.wide ? -1 : -2, -2));
            if (item.length > 2 && item[2] instanceof Bitmap) {
                ImageView iv = new ImageView(c);
                iv.setImageBitmap((Bitmap) item[2]);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(30), px(30));
                il.leftMargin = px(10);
                one.addView(iv, il);
            }
            if (item.length > 3 && item[3] != null) {
                TextView time = text(20, "sans-serif-medium", 0xFFFFFFFF);
                time.setText(String.valueOf(item[3]));
                time.setSingleLine(true);
                one.addView(time, margins(14, 0, 0, 0));
            }
            // The channel it is on, as its logo, after the time.
            if (item.length > 4 && item[4] instanceof Bitmap) one.addView(logoView((Bitmap) item[4], 24), margins(12, 0, 0, 0));
            LinearLayout.LayoutParams lp = p.wide ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = px(p.wide ? 28 : 40);
            page.addView(one, lp);
        }
    }

    private TextView text(float designPx, String family, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, designPx * k);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    private int px(float design) {
        return Math.round(design * k);
    }
}
