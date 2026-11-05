package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Control Centre's weekend planner: Saturday or Sunday (OK on the day turns to the other), the day drawn as a lane
 * for each of the house's TVs and one for the games on none (each game a block from its kick-off, the prayer times as
 * gold lines), then each of the day's games: OK steps it through the TVs and none. A TV a game is on turns to it at
 * kick-off (WeekendPlanner); two games on one TV at once say so.
 */
final class PlannerPanel extends SportPanel {
    private List<LocalDate> days = new ArrayList<>();
    private int day;
    private final Map<LocalDate, List<ScoresTicker.Game>> games = new HashMap<>();
    private boolean loading;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm", Locale.UK);

    PlannerPanel(HomeV2 home) {
        super(home);
    }

    @Override protected int[] ground() {
        return new int[]{0xFF18406E, 0xFF0F2A4E, 0xFF060A17};
    }

    /** The Control Centre tile's line. */
    static String describe(Context c) {
        int n = 0;
        for (WeekendPlanner.Plan p : WeekendPlanner.plans(c).values()) if (!p.tv.isEmpty() && p.at > System.currentTimeMillis()) n++;
        return n == 0 ? "This weekend's games on both TVs" : n == 1 ? "1 game planned" : n + " games planned";
    }

    @Override protected void opened() {
        days = WeekendPlanner.weekend(LocalDate.now(WeekendPlanner.zone()));
        day = 0;
        read();
    }

    private void read() {
        if (loading || days.isEmpty()) return;
        loading = true;
        List<LocalDate> want = new ArrayList<>(days);
        h.io.execute(() -> {
            Map<LocalDate, List<ScoresTicker.Game>> got = new HashMap<>();
            Set<String> slugs = GoalWatchService.alertSlugs(c);
            slugs.addAll(Players.leagues(c));
            String[] leagues = slugs.toArray(new String[0]);
            for (LocalDate d : want) {
                List<ScoresTicker.Game> l = new ArrayList<>();
                try {
                    for (ScoresTicker.Game g : ScoresTicker.load(leagues, d)) {
                        if (g.id != null && g.kickoff > 0 && GoalWatchService.followsAnywhere(c, g) && !has(l, g.id)) l.add(g);
                    }
                } catch (RuntimeException ignored) {
                    // the day as far as it was read
                }
                l.sort((a, b) -> Long.compare(a.kickoff, b.kickoff));
                got.put(d, l);
            }
            h.root.post(() -> {
                loading = false;
                games.putAll(got);
                if (open) redraw();
            });
        });
    }

    private static boolean has(List<ScoresTicker.Game> l, String id) {
        for (ScoresTicker.Game g : l) if (id.equals(g.id)) return true;
        return false;
    }

    @Override protected void build() {
        title("Weekend planner", "OK to put a game on a TV  ·  Back to close");
        if (days.isEmpty()) return;
        LocalDate d = days.get(day);
        List<ScoresTicker.Game> today = games.get(d);
        Row which = row(DAY.format(d), false);
        which.words = () -> (loading && today == null ? "Reading the games…" : (today == null ? 0 : today.size()) + " games")
                + (days.size() > 1 ? "  ·  OK for " + DAY.format(days.get(1 - day)) : "");
        which.ok = () -> {
            if (days.size() < 2) return;
            day = 1 - day;
            line = 0;
            redraw();
        };
        add(which);
        List<String[]> tvs = WeekendPlanner.tvs(c);
        view(new DayView(c, d, today == null ? new ArrayList<>() : today, tvs, WeekendPlanner.plans(c), h.px(1)), 40 + 64 * (tvs.size() + 1));
        if (today == null || today.isEmpty()) {
            words(today == null ? "" : "No games of your competitions or clubs that day.", 18, 0xCCFFFFFF);
            return;
        }
        heading("THE GAMES");
        List<String> names = new ArrayList<>();
        for (String[] t : tvs) names.add(t[0]);
        List<Row> rows = new ArrayList<>();
        for (ScoresTicker.Game g : today) {
            Row r = row(HM.format(Instant.ofEpochMilli(g.kickoff).atZone(WeekendPlanner.zone())) + "  " + MatchMode.label(g), false);
            r.words = () -> where(g);
            r.ok = () -> {
                WeekendPlanner.Plan p = WeekendPlanner.plans(c).get(g.id);
                WeekendPlanner.assign(c, g, WeekendPlanner.next(names, p == null ? "" : p.tv));
                redraw();
            };
            rows.add(r);
        }
        addAll(rows);
    }

    /** "On this TV", "On Bedroom TV · clashes with Arsenal v Chelsea", or "On no TV". */
    private String where(ScoresTicker.Game g) {
        Map<String, WeekendPlanner.Plan> plans = WeekendPlanner.plans(c);
        WeekendPlanner.Plan p = plans.get(g.id);
        if (p == null || p.tv.isEmpty()) return "On no TV  ·  OK to put it on one";
        String on = p.tv.equals(WeekendPlanner.me(c)) ? "On this TV" : "On " + p.tv;
        for (WeekendPlanner.Plan o : plans.values()) {
            if (WeekendPlanner.clash(p, o)) return on + "  ·  clashes with " + o.label;
        }
        return on + (p.names.isEmpty() ? "" : "  ·  " + p.names.get(0));
    }

    /** The day as lanes: a TV each, then none; each game a block from its kick-off; the prayer times as gold lines. */
    private static final class DayView extends View {
        private final LocalDate d;
        private final List<ScoresTicker.Game> games;
        private final List<String[]> tvs;
        private final Map<String, WeekendPlanner.Plan> plans;
        private final float dp;
        private final Paint lane = new Paint(Paint.ANTI_ALIAS_FLAG), block = new Paint(Paint.ANTI_ALIAS_FLAG),
                text = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG), gold = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Object[]> prayers = new ArrayList<>();
        private static final int FROM = 11, TO = 23;

        DayView(Context c, LocalDate d, List<ScoresTicker.Game> games, List<String[]> tvs, Map<String, WeekendPlanner.Plan> plans, float dp) {
            super(c);
            this.d = d;
            this.games = games;
            this.tvs = tvs;
            this.plans = plans;
            this.dp = dp;
            lane.setColor(0x14FFFFFF);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(17 * dp);
            text.setFakeBoldText(true);
            small.setColor(0xB3FFFFFF);
            small.setTextSize(14 * dp);
            gold.setColor(0x99FFD27A);
            gold.setStrokeWidth(2 * dp);
            if (Prayer.on(c)) {
                try {
                    for (Prayer.Slot s : Prayer.slots(c, d)) if (s.begins != null) prayers.add(new Object[]{s.label(), s.begins});
                } catch (RuntimeException ignored) {
                    // no times that day
                }
            }
        }

        private float x(float left, float right, LocalDateTime t) {
            float hours = t.getHour() + t.getMinute() / 60f;
            return left + (right - left) * (hours - FROM) / (TO - FROM);
        }

        @Override protected void onDraw(Canvas cv) {
            float left = 190 * dp, right = getWidth() - 8 * dp, top = 30 * dp, laneH = 54 * dp, gap = 10 * dp;
            for (int hr = FROM + 1; hr < TO; hr += 2) {
                float x = left + (right - left) * (hr - FROM) / (float) (TO - FROM);
                cv.drawText(hr + ":00", x - 18 * dp, 18 * dp, small);
            }
            int lanes = tvs.size() + 1;
            for (int i = 0; i < lanes; i++) {
                float y = top + i * (laneH + gap);
                cv.drawRoundRect(new RectF(left, y, right, y + laneH), 12 * dp, 12 * dp, lane);
                String name = i == 0 ? "This TV" : i < tvs.size() ? tvs.get(i)[0] : "On no TV";
                cv.drawText(trim(name, 16), 4 * dp, y + laneH / 2 + 6 * dp, text);
            }
            float bottom = top + lanes * (laneH + gap) - gap;
            for (Object[] p : prayers) {
                float x = x(left, right, (LocalDateTime) p[1]);
                if (x < left || x > right) continue;
                cv.drawLine(x, top - 4 * dp, x, bottom + 2 * dp, gold);
            }
            String me = tvs.get(0)[0];
            for (ScoresTicker.Game g : games) {
                WeekendPlanner.Plan p = plans.get(g.id);
                int laneIdx = tvs.size();
                if (p != null && !p.tv.isEmpty()) {
                    for (int i = 0; i < tvs.size(); i++) if (tvs.get(i)[0].equals(p.tv)) laneIdx = i;
                    if (p.tv.equals(me)) laneIdx = 0;
                }
                LocalDateTime t = Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()).toLocalDateTime();
                float x0 = Math.max(left, x(left, right, t)), x1 = Math.min(right, x0 + (right - left) * 2f / (TO - FROM));
                if (x1 <= x0) continue;
                float y = top + laneIdx * (laneH + gap);
                int colour = g.homeColor != 0 ? 0xFF000000 | g.homeColor : 0xFF3A4A7A;
                block.setColor(laneIdx == tvs.size() ? 0x33FFFFFF : colour);
                cv.drawRoundRect(new RectF(x0 + 2 * dp, y + 4 * dp, x1 - 2 * dp, y + laneH - 4 * dp), 10 * dp, 10 * dp, block);
                String label = (g.home == null ? "" : g.home) + " v " + (g.away == null ? "" : g.away);
                cv.drawText(label, x0 + 10 * dp, y + laneH / 2 + 6 * dp, text);
            }
        }

        private static String trim(String s, int n) {
            return s.length() <= n ? s : s.substring(0, n - 1) + "…";
        }
    }
}
