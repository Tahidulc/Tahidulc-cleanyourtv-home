package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Control Centre's Your club's season, for each starred club in turn (OK on the club steps to the next): its place
 * and record, its points game by game (each game a dot, green a win, grey a draw, red a loss), the next five league
 * games with how hard each looks, its scorers, and its record at home and away. Read off the main thread (Season).
 */
final class SeasonPanel extends SportPanel {
    private String[] ids = new String[0];
    private int at;
    private Season.Data data;
    private boolean loading;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, H:mm", Locale.UK);
    private static final int WIN = 0xFF3DDC84, DRAW = 0xFFB8BECF, LOSS = 0xFFFF6B70;

    SeasonPanel(HomeV2 home) {
        super(home);
    }

    @Override protected int[] ground() {
        return new int[]{0xFF1D3FA8, 0xFF0F2470, 0xFF070A26};
    }

    @Override protected void opened() {
        ids = SyncHub.starredClubs(c);
        if (at >= ids.length) at = 0;
        read();
    }

    private void read() {
        if (ids.length == 0) return;
        String id = ids[at];
        loading = true;
        data = null;
        h.io.execute(() -> {
            Season.Data d;
            try {
                d = Season.read(c, id);
            } catch (RuntimeException e) {
                d = null;
            }
            Season.Data got = d;
            h.root.post(() -> {
                if (!open || ids.length == 0 || !ids[at].equals(id)) return;
                loading = false;
                data = got;
                redraw();
            });
        });
    }

    @Override protected void build() {
        SportsData.Team t = data == null ? null : data.team;
        title(t == null ? "Your club's season" : t.name + "'s season", "OK for the next club  ·  Back to close");
        Row club = row(t == null ? "Your clubs" : t.name, false);
        club.words = () -> data == null ? (loading ? "Reading the season…" : ids.length == 0 ? "No clubs starred" : "Couldn't read the season")
                : (data.team.standing.isEmpty() ? "" : data.team.standing + " · ") + Season.words(Season.record(data.played, null))
                + " · " + (data.played.isEmpty() ? 0 : Season.running(data.played)[data.played.size() - 1]) + " points"
                + (ids.length > 1 ? "   (" + (at + 1) + " of " + ids.length + ")" : "");
        club.ok = () -> {
            if (ids.length < 2) return;
            at = (at + 1) % ids.length;
            read();
            redraw();
        };
        add(club);
        if (data == null) return;

        heading("POINTS, GAME BY GAME");
        view(new PointsChart(c, data.played, h.px(1)), 250);

        heading("THE NEXT FIVE");
        if (data.next.isEmpty()) words("No league games listed yet.", 18, 0xCCFFFFFF);
        int n = Math.max(1, data.places.size());
        for (SportsData.Fixture f : data.next) {
            int hard = Season.difficulty(data.places.get(f.opponentId), n);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(h.px(20), 0, h.px(20), 0);
            row.setBackground(h.fill(16, 0x22FFFFFF));
            View dot = new View(c);
            dot.setBackground(h.fill(8, hard == 2 ? LOSS : hard == 1 ? 0xFFFFB547 : WIN));
            row.addView(dot, new LinearLayout.LayoutParams(h.px(16), h.px(16)));
            TextView who = h.text(20, semi, Color.WHITE);
            who.setText((f.home ? "v " : "at ") + f.opponent + (f.home ? " (H)" : " (A)"));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
            wl.leftMargin = h.px(16);
            row.addView(who, wl);
            TextView when = h.text(16, regular, 0xCCFFFFFF);
            when.setText(f.at <= 0 ? "" : WHEN.format(Instant.ofEpochMilli(f.at).atZone(ZoneId.systemDefault())));
            row.addView(when);
            view(row, 56);
        }
        words("Dot: how hard it looks by the other side's place (red the top third, green the bottom third).", 14, 0x99FFFFFF);

        heading("SCORERS AND RECORD");
        StringBuilder top = new StringBuilder();
        for (Map.Entry<String, Integer> e : Season.top(data.scorers, 5)) {
            if (top.length() > 0) top.append("   ·   ");
            top.append(GoalWatchService.surname(e.getKey())).append(' ').append(e.getValue());
        }
        words(top.length() == 0 ? "No league goals read yet." : top.toString(), 20, Color.WHITE);
        words("At home " + Season.words(Season.record(data.played, true)) + "   ·   Away " + Season.words(Season.record(data.played, false)),
                18, 0xCCFFFFFF);
    }

    /** The points after each game as a line, each game a dot in its result's colour. */
    private static final class PointsChart extends View {
        private final int[] running;
        private final int[] results;
        private final float dp;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), dotP = new Paint(Paint.ANTI_ALIAS_FLAG),
                grid = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);

        PointsChart(Context c, List<SportsData.Fixture> played, float dp) {
            super(c);
            this.dp = dp;
            running = Season.running(played);
            results = new int[played.size()];
            for (int i = 0; i < results.length; i++) results[i] = Season.points(played.get(i));
            line.setColor(0xFF9FB4FF);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(4 * dp);
            line.setStrokeJoin(Paint.Join.ROUND);
            grid.setColor(0x26FFFFFF);
            grid.setStrokeWidth(dp);
            label.setColor(0xB3FFFFFF);
            label.setTextSize(16 * dp);
            label.setTypeface(Typeface.DEFAULT);
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), hgt = getHeight();
            float left = 48 * dp, right = w - 20 * dp, top = 16 * dp, bottom = hgt - 36 * dp;
            int n = running.length;
            int max = Math.max(3, n == 0 ? 3 : running[n - 1]);
            // The guide lines: none, half and all of the points so far.
            for (int k = 0; k <= 2; k++) {
                float y = bottom - (bottom - top) * k / 2f;
                cv.drawLine(left, y, right, y, grid);
                cv.drawText(String.valueOf(Math.round(max * k / 2f)), 8 * dp, y + 6 * dp, label);
            }
            if (n == 0) {
                cv.drawText("No league games played yet", left, (top + bottom) / 2, label);
                return;
            }
            Path p = new Path();
            float step = n > 1 ? (right - left) / (n - 1) : 0;
            for (int i = 0; i < n; i++) {
                float x = left + step * i, y = bottom - (bottom - top) * running[i] / (float) max;
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            cv.drawPath(p, line);
            for (int i = 0; i < n; i++) {
                float x = left + step * i, y = bottom - (bottom - top) * running[i] / (float) max;
                dotP.setColor(results[i] == 3 ? WIN : results[i] == 1 ? DRAW : LOSS);
                cv.drawCircle(x, y, 8 * dp, dotP);
            }
            cv.drawText("Game 1", left - 10 * dp, hgt - 8 * dp, label);
            String last = "Game " + n + " · " + running[n - 1] + " pts";
            cv.drawText(last, right - label.measureText(last), hgt - 8 * dp, label);
        }
    }

    /** The Control Centre tile's line. */
    static String describe(Context c) {
        String[] ids = SyncHub.starredClubs(c);
        return ids.length == 0 ? "Your clubs' seasons" : ids.length == 1 ? "Your club's place and form" : ids.length + " clubs: place, form, next five";
    }

}
