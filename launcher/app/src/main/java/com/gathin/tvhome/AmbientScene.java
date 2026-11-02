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
 * Formula 1 session, the camera's last motion. Along the foot, today's results. Everything drifts a little every
 * minute and dims late at night, so nothing burns in; it never keeps the screen on by itself, so the TV still goes
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
    private final LinearLayout cards, strip, left;
    private TextView hint;
    /** When the scene started, and whether the clock is on the right (it changes sides every few minutes). */
    private long startedAt;
    private boolean flipped;
    private static final long FLIP_MS = 5 * 60_000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private boolean running;

    AmbientScene(Context context) {
        c = context;
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
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
        // Inside the drifting stage, clear of the edge: it moves with everything else.
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, px(84), Gravity.BOTTOM);
        sp.bottomMargin = px(36);
        stage.addView(strip, sp);
        showStrip(new ArrayList<>());
    }

    View view() {
        return root;
    }

    void start() {
        if (running) return;
        running = true;
        startedAt = System.currentTimeMillis();
        tick.run();
        refresh.run();
        main.postDelayed(flip, FLIP_MS);
        // The way out is shown for the first minute only: no words left standing on the screen.
        main.postDelayed(() -> { if (hint != null) hint.animate().alpha(0f).setDuration(1_500).start(); }, 60_000L);
    }

    void stop() {
        running = false;
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
            stage.animate().translationX((random.nextFloat() * 80 - 40) * k).translationY((random.nextFloat() * 50 - 25) * k)
                    .alpha(dim()).setDuration(4_000).start();
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
        List<String> results = new ArrayList<>();
        Card match = match(results);
        if (match != null) found.add(match);
        Card f1 = f1();
        if (f1 != null) found.add(f1);
        Card cam = camera();
        if (cam != null) found.add(cam);
        main.post(() -> {
            if (!running) return;
            if (wx != null) {
                weather.setText((String) wx[0]);
                weatherIcon.setImageResource((Integer) wx[1]);
                tomorrow.setText((String) wx[2]);
            }
            cards.removeAllViews();
            for (Card card : found) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                p.bottomMargin = px(18);
                cards.addView(card.view(), p);
            }
            showStrip(results);
        });
    }

    // ---- what the cards say --------------------------------------------------------------------------------

    /** {"8° · Clear · Manchester", icon, "Tomorrow 11°, rain likely"}, from Open-Meteo as Home's weather line. */
    private Object[] weather() {
        String lat = c.getString(R.string.weather_latitude), lon = c.getString(R.string.weather_longitude);
        if (lat.isEmpty() || lon.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(ScoresTicker.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon + "&current=temperature_2m,weather_code,is_day"
                    + "&daily=temperature_2m_max,precipitation_probability_max&forecast_days=2&timezone=auto"));
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
            return new Object[]{line, WeatherLine.icon(code, day), next};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The game on now in the followed competitions, else the next one in the coming week (one with a British
     * channel first); today's finished ones go into results.
     */
    private Card match(List<String> results) {
        String[] leagues = c.getResources().getStringArray(R.array.goal_alert_leagues);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            List<ScoresTicker.Game> day = ScoresTicker.load(leagues, today.plusDays(i));
            day.sort((a, b) -> Long.compare(a.kickoff, b.kickoff));
            if (i == 0) {
                for (ScoresTicker.Game g : day) {
                    if (g.state == 2 && results.size() < 4) results.add(g.home + " " + g.homeScore + "–" + g.awayScore + " " + g.away);
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
        card.label = live ? "ON NOW · " + ScoresTicker.competition(g) : "NEXT MATCH · " + ScoresTicker.competition(g);
        card.title = live ? g.home + "  " + g.homeScore + "–" + g.awayScore + "  " + g.away : g.home + " v " + g.away;
        String when = live ? ScoresTicker.liveClock(g) : day(g.kickoff);
        card.note = when + (channel != null ? "  ·  " + channel : "");
        card.tint = live ? 0xFFE8112D : 0xFF2B3CC9;
        card.badge = live ? "LIVE" : "NEXT";
        String crest = g.homeLogo != null ? g.homeLogo : g.leagueLogo;
        if (crest != null) card.picture = ScoresTicker.cachedLogo(c, crest, px(64));
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
                card.note = (on ? "On now" : day(s.start.toEpochMilli())) + "  ·  Sky Sports F1";
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
        boolean hasChannel;

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
            if (picture != null) {
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
            words.addView(t, margins(0, 6, 0, 0));
            TextView n = text(18, "sans-serif", 0xB3FFFFFF);
            n.setText(note);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(n, margins(0, 6, 0, 0));
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
            wp.leftMargin = px(22);
            row.addView(words, wp);
            return row;
        }
    }

    /** The foot: today's results (when there are any) and how to leave. */
    private void showStrip(List<String> results) {
        strip.removeAllViews();
        if (!results.isEmpty()) {
            TextView chip = text(14, "sans-serif-black", 0xFFFFFFFF);
            chip.setText("TODAY'S RESULTS");
            chip.setLetterSpacing(0.14f);
            chip.setPadding(px(12), px(6), px(12), px(6));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(8));
            g.setColor(0x1FFFFFFF);
            chip.setBackground(g);
            strip.addView(chip);
            for (String r : results) {
                TextView t = text(20, "sans-serif", 0xD9FFFFFF);
                t.setText("FT  " + r);
                t.setSingleLine(true);
                strip.addView(t, margins(40, 0, 0, 0));
            }
        }
        View gap = new View(c);
        strip.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        hint = text(20, "sans-serif", 0x8CFFFFFF);
        hint.setText("Any button wakes Home");
        if (running && System.currentTimeMillis() - startedAt > 60_000L) hint.setAlpha(0f);
        strip.addView(hint);
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
