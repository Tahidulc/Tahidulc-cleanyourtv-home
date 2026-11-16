package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Detail;
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One game's page from the sports hub: the score and the clock, the events (goals, cards, substitutions, VAR), the live
 * stats (ESPN's and FotMob's: possession, shots, on target, expected goals, corners, cards…), both line-ups and the
 * momentum, all as the channel being watched shows the game. When the family is watching one of the game's channels in
 * Homefield TV (or was, just before), everything is held back by that channel's delay, set by "It's just happened" or
 * nudged by Sooner and Later, so nothing is told before the picture shows it. Along the foot, the channels that show it
 * (OK watches one) and, in step, the delay's buttons.
 */
final class GameScreen extends Screen {
    private static final long POLL_MS = 15_000L;

    private final SportsData.Item item;
    private List<Matching.Row> rows;
    private final InStep.Line<Detail> states;
    /** The scores' readings and the clock as shown, as the scoreboard in the player keeps them (BoardClock). */
    private final java.util.ArrayDeque<BoardClock.Reading> readings = new java.util.ArrayDeque<>();
    private final BoardClock.Steady steady = new BoardClock.Steady();
    private Page page;
    private int focus;
    private float bodyScroll;
    private final List<String[]> buttons = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private final Runnable second = new Runnable() {
        @Override public void run() {
            if (page != null) page.invalidate();
            h.postDelayed(this, 1000);
        }
    };

    GameScreen(TvActivity tv, SportsData.Item item, List<Matching.Row> rows) {
        super(tv);
        this.item = item;
        this.rows = rows;
        this.states = new InStep.Line<>();
    }

    /** For previews: the page drawn with this game's page as read, in step with a channel (delay seconds). */
    void preview(Detail d, String channelKey, int delayS) {
        states.add(System.currentTimeMillis() - delayS * 1000L - 1000L, d, Detail.key(d));
        if (d.game != null) BoardClock.add(readings, BoardClock.Reading.of(d.game, System.currentTimeMillis() - delayS * 1000L - 1000L));
        if (channelKey != null) {
            InStep.watching(channelKey, System.currentTimeMillis());
            InStep.setDelay(tv, channelKey, delayS);
        }
        fillButtons();
        if (page != null) page.invalidate();
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Dark.GROUND);
        page = new Page();
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        fillButtons();
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        if (Exec.inline) return;
        poll();
        h.post(second);
        if (rows == null) {
            Channel playing = tv.live.channel();
            Exec.io(() -> {
                List<Matching.Row> found = Matching.rows(tv, item.game, playing, System.currentTimeMillis());
                Exec.main(() -> {
                    rows = found;
                    fillButtons();
                    page.invalidate();
                });
            });
        }
    }

    @Override void hidden() {
        h.removeCallbacks(poll);
        h.removeCallbacks(second);
    }

    private void poll() {
        h.removeCallbacks(poll);
        if (!SportsData.FOOTBALL.equals(item.kind) || item.game == null) return;
        Game g = item.game;
        Exec.io(() -> {
            Detail d = Detail.read(g);
            Exec.main(() -> {
                states.add(d.at, d, Detail.key(d));
                if (d.game != null) BoardClock.add(readings, BoardClock.Reading.of(d.game, d.at));
                fillButtons();
                if (page != null) page.invalidate();
                h.postDelayed(poll, POLL_MS);
            });
        });
    }

    /** The channel the page is in step with: the one being watched, when it shows this game; "" for none. */
    private String inStepWith() {
        String w = InStep.watched(System.currentTimeMillis());
        if (w.isEmpty() || rows == null) return "";
        for (Matching.Row r : rows) if (r.ch != null && r.ch.key.equals(w)) return w;
        return "";
    }

    private String inStepName() {
        String w = inStepWith();
        if (rows != null) for (Matching.Row r : rows) if (r.ch != null && r.ch.key.equals(w)) return r.name;
        return "";
    }

    /** The foot's buttons: {kind, words, channel key}: "watch", "sooner", "later", "now". */
    private void fillButtons() {
        String was = focus < buttons.size() ? buttons.get(focus)[0] + buttons.get(focus)[2] : "";
        buttons.clear();
        for (int i = 0; rows != null && i < Math.min(4, rows.size()); i++) {
            Matching.Row r = rows.get(i);
            if (r.ch == null) continue;
            buttons.add(new String[]{"watch", (r.now ? "Watching " : "Watch on ") + r.name + (r.uhd ? " 4K" : ""), r.ch.key});
        }
        if (!inStepWith().isEmpty()) {
            buttons.add(new String[]{"sooner", "Sooner", ""});
            buttons.add(new String[]{"later", "Later", ""});
            buttons.add(new String[]{"now", "It's just happened", ""});
        }
        focus = 0;
        for (int i = 0; i < buttons.size(); i++) if ((buttons.get(i)[0] + buttons.get(i)[2]).equals(was)) focus = i;
    }

    @Override boolean key(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus > 0) focus--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus < buttons.size() - 1) focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                bodyScroll = Math.max(0, bodyScroll - 120f);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                bodyScroll = Math.min(1200f, bodyScroll + 120f);
                break;
            default:
                return false;
        }
        page.invalidate();
        return true;
    }

    @Override void ok() {
        if (focus < 0 || focus >= buttons.size()) return;
        String[] b = buttons.get(focus);
        String k = inStepWith();
        switch (b[0]) {
            case "watch": {
                Channel ch = com.gathin.homefieldtv.engine.Library.get(tv).byKey(b[2]);
                if (ch != null) {
                    InStep.watching(ch.key, System.currentTimeMillis());
                    tv.watch(ch);
                }
                return;
            }
            case "sooner":
                InStep.nudge(tv, k, -InStep.STEP_S);
                break;
            case "later":
                InStep.nudge(tv, k, InStep.STEP_S);
                break;
            case "now":
                if (InStep.justHappened(tv, k, states.lastChange()) < 0) {
                    android.widget.Toast.makeText(tv, "Nothing has happened in the last few minutes to set it by", android.widget.Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                return;
        }
        page.invalidate();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private final class Page extends View {
        private final TextPaint comp = Dark.text(26, 600, Dark.MUTED), club = Dark.text(38, 600, Dark.TEXT);
        private final TextPaint score = Dark.text(120, 700, Dark.TEXT), clock = Dark.text(34, 600, Dark.TEXT);
        private final TextPaint step = Dark.text(24, 500, Dark.ACCENT), head = Dark.text(24, 700, Dark.MUTED);
        private final TextPaint minute = Dark.text(24, 700, Dark.SOFT), body = Dark.text(25, 500, Dark.TEXT), soft = Dark.text(22, 500, Dark.MUTED);
        private final TextPaint statL = Dark.text(26, 600, Dark.TEXT), statLabel = Dark.text(22, 500, Dark.SOFT), button = Dark.text(26, 600, Dark.TEXT);
        private final TextPaint buttonOn = Dark.text(26, 600, 0xFF111114), tagP = Dark.text(22, 700, 0xFFFFFFFF);
        private final Paint panel = Dark.fill(Dark.PANEL), card = Dark.fill(Dark.CARD), white = Dark.fill(0xFFFFFFFF), live = Dark.fill(0xFFE5303B);
        private final Paint homeBar = Dark.fill(0xFF5B8CFF), awayBar = Dark.fill(0xFFE8A33D), yellow = Dark.fill(0xFFF5C518), red = Dark.fill(0xFFD7192F);
        private final Paint green = Dark.fill(0xFF2EB872), line = Dark.fill(Dark.LINE), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private final Path p = new Path();

        Page() {
            super(tv);
        }

        private float x(float d) {
            return Ui.f(d);
        }

        private void image(Canvas c, String url, float cx, float cy, float size) {
            int px = Math.max(1, Math.round(x(size)));
            Bitmap b = Pics.get(url, px, px, Pics.FIT, this::invalidate);
            if (b != null) c.drawBitmap(b, x(cx) - b.getWidth() / 2f, x(cy) - b.getHeight() / 2f, bmp);
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.currentTimeMillis();
            String k = inStepWith();
            int delay = k.isEmpty() ? 0 : InStep.delay(tv, k);
            Detail d = states.at(now, delay);
            Game g = d != null && d.game != null ? d.game : item.game;
            header(c, d, g, now, delay, k);
            if (d != null) {
                events(c, d);
                stats(c, d, g);
                lineups(c, d, g);
            } else {
                String words = SportsData.FOOTBALL.equals(item.kind) ? "Reading the game…" : "Live stats come with football games; the score and the channels are here.";
                c.drawText(words, x(960) - body.measureText(words) / 2f, x(560), body);
            }
            foot(c);
        }

        private void header(Canvas c, Detail d, Game g, long now, int delay, String k) {
            r.set(x(60), x(40), x(1860), x(340));
            c.drawRoundRect(r, x(30), x(30), panel);
            image(c, item.compLogo, 960, 82, 52);
            String cn = item.comp;
            c.drawText(cn, x(960) - comp.measureText(cn) / 2f, x(140), comp);
            image(c, item.homeLogo, 330, 200, 140);
            image(c, item.awayLogo, 1590, 200, 140);
            String hn = Ui.fit(item.home, club, x(380)), an = Ui.fit(item.away, club, x(380));
            c.drawText(hn, x(330) - club.measureText(hn) / 2f, x(310), club);
            c.drawText(an, x(1590) - club.measureText(an) / 2f, x(310), club);
            String sc;
            String clk;
            boolean liveNow;
            if (g != null && SportsData.FOOTBALL.equals(item.kind)) {
                sc = g.state == Game.PRE ? Ui.clock(g.kickoff) : g.homeScore + " – " + g.awayScore;
                liveNow = g.state == Game.LIVE;
                if (liveNow && !g.halftime) {
                    // The clock as the scoreboard in the player counts it: on from when the scores' clock was first seen,
                    // as the picture has reached it, never stopping or stepping back, the minutes added beside it.
                    long screenNow = now - delay * 1000L;
                    BoardClock.Reading r = BoardClock.atScreen(readings, screenNow);
                    double secs = BoardClock.clockSecs(r, screenNow);
                    if (r != null && secs >= 0) {
                        String words = BoardClock.clockWords(steady.shown(g.id, r.detail, secs, now));
                        int[] announced = new int[4];
                        for (int i = 0; d != null && i < 4; i++) announced[i] = Math.max(0, d.added[i]);
                        String plus = BoardClock.addedWords(words, r.detail, announced);
                        clk = words + (plus.isEmpty() ? "" : "  " + plus);
                    } else {
                        clk = g.detail == null || g.detail.isEmpty() ? "Live" : g.detail;
                    }
                } else {
                    clk = g.halftime ? "Half time" : g.state == Game.POST ? "Full time" : Ui.day(g.kickoff, now);
                }
            } else {
                sc = item.score.isEmpty() ? Ui.clock(item.start) : item.score;
                liveNow = item.live;
                clk = item.status.isEmpty() ? (item.over ? "Final" : Ui.day(item.start, now)) : item.status;
            }
            c.drawText(sc, x(960) - score.measureText(sc) / 2f, x(250), score);
            float cw = clock.measureText(clk);
            if (liveNow) {
                String lt = "LIVE";
                float tw = tagP.measureText(lt) + x(20);
                r.set(x(960) - (cw + tw + x(14)) / 2f, x(268), x(960) - (cw + tw + x(14)) / 2f + tw, x(298));
                c.drawRoundRect(r, x(10), x(10), live);
                c.drawText(lt, r.left + x(10), r.bottom - x(7), tagP);
                c.drawText(clk, r.right + x(14), x(294), clock);
            } else {
                c.drawText(clk, x(960) - cw / 2f, x(294), clock);
            }
            String note = k.isEmpty() ? "" : "In step with " + inStepName() + " · " + (delay <= 0 ? "no delay" : delay + " s behind live");
            if (!note.isEmpty()) c.drawText(note, x(960) - step.measureText(note) / 2f, x(330), step);
        }

        private void column(Canvas c, float left, String title) {
            r.set(x(left), x(370), x(left + 580), x(900));
            c.drawRoundRect(r, x(24), x(24), card);
            c.drawText(title, x(left + 30), x(414), head);
        }

        /** The events, newest first: the minute, its mark (a ball, a card, the arrows of a change), who. */
        private void events(Canvas c, Detail d) {
            float left = 60;
            column(c, left, "WHAT'S HAPPENED");
            c.save();
            c.clipRect(x(left), x(430), x(left + 580), x(890));
            float y = 470 - bodyScroll;
            List<String[]> ev = d.newestFirst();
            if (ev.isEmpty()) c.drawText("Nothing yet", x(left + 30), x(y), soft);
            for (String[] e : ev) {
                if (y > 900) break;
                String what = e.length > 1 && e[1] != null ? e[1] : "";
                String who = e.length > 2 && e[2] != null ? e[2] : "";
                String side = e.length > 3 && e[3] != null ? e[3] : "";
                String other = e.length > 5 && e[5] != null ? e[5] : "";
                c.drawText(e[0] == null ? "" : e[0], x(left + 30), x(y), minute);
                mark(c, what, left + 120, y - 9);
                String club = "home".equals(side) ? item.home : "away".equals(side) ? item.away : "";
                String text = who.isEmpty() ? what : who;
                c.drawText(Ui.fit(text, body, x(380)), x(left + 150), x(y), body);
                String under = what.toLowerCase(Locale.UK).contains("sub") && !other.isEmpty() ? "for " + other
                        : what.toLowerCase(Locale.UK).contains("goal") && !other.isEmpty() ? "assist " + other : club;
                if (!under.isEmpty()) c.drawText(Ui.fit(under, soft, x(380)), x(left + 150), x(y + 28), soft);
                y += 66;
            }
            c.restore();
        }

        private void mark(Canvas c, String what, float cx, float cy) {
            String w = what.toLowerCase(Locale.UK);
            if (w.contains("goal")) {
                c.drawCircle(x(cx), x(cy), x(11), white);
            } else if (w.contains("red")) {
                r.set(x(cx - 8), x(cy - 12), x(cx + 8), x(cy + 12));
                c.drawRoundRect(r, x(3), x(3), red);
            } else if (w.contains("yellow")) {
                r.set(x(cx - 8), x(cy - 12), x(cx + 8), x(cy + 12));
                c.drawRoundRect(r, x(3), x(3), yellow);
            } else if (w.contains("sub")) {
                p.reset();
                p.moveTo(x(cx - 10), x(cy - 2));
                p.lineTo(x(cx), x(cy - 12));
                p.lineTo(x(cx + 10), x(cy - 2));
                p.close();
                c.drawPath(p, green);
                p.reset();
                p.moveTo(x(cx - 10), x(cy + 2));
                p.lineTo(x(cx), x(cy + 12));
                p.lineTo(x(cx + 10), x(cy + 2));
                p.close();
                c.drawPath(p, red);
            } else {
                c.drawCircle(x(cx), x(cy), x(6), line);
            }
        }

        /** The stats, each as its two numbers either side of its name, over a bar split between the sides. */
        private void stats(Canvas c, Detail d, Game g) {
            float left = 670;
            column(c, left, "LIVE STATS");
            homeBar.setColor(colour(g == null ? "" : g.homeColor, 0xFF5B8CFF));
            awayBar.setColor(colour(g == null ? "" : g.awayColor, 0xFFE8A33D));
            if (homeBar.getColor() == awayBar.getColor()) awayBar.setColor(0xFFE8A33D);
            float y = 470;
            int shown = 0;
            for (String[] s : d.stats) {
                if (s == null || s.length < 3 || shown >= 8) continue;
                c.drawText(s[1], x(left + 30), x(y), statL);
                c.drawText(s[2], x(left + 550) - statL.measureText(s[2]), x(y), statL);
                String label = Ui.fit(s[0], statLabel, x(320));
                c.drawText(label, x(left + 290) - statLabel.measureText(label) / 2f, x(y), statLabel);
                float[] share = Detail.shares(s[1], s[2]);
                float bx = left + 30, bw = 520;
                r.set(x(bx), x(y + 14), x(bx + bw * share[0] - 3), x(y + 22));
                c.drawRoundRect(r, x(4), x(4), homeBar);
                r.set(x(bx + bw * share[0] + 3), x(y + 14), x(bx + bw), x(y + 22));
                c.drawRoundRect(r, x(4), x(4), awayBar);
                y += 52;
                shown++;
            }
            if (shown == 0) c.drawText("The stats come once the game is on", x(left + 30), x(y), soft);
            if (!d.momentum.isEmpty()) momentum(c, d, left, Math.min(860, Math.max(y + 10, 800)));
        }

        /** FotMob's momentum, a bar a minute: up for the home side, down for the away side. */
        private void momentum(Canvas c, Detail d, float left, float mid) {
            c.drawText("MOMENTUM", x(left + 30), x(mid - 34), soft);
            int n = d.momentum.size();
            float w = 520f / Math.max(1, n);
            for (int i = 0; i < n; i++) {
                int[] m = d.momentum.get(i);
                float v = Math.max(-100, Math.min(100, m.length > 1 ? m[1] : 0)) / 100f * 30f;
                if (v >= 0) r.set(x(left + 30 + i * w), x(mid - v), x(left + 30 + (i + 1) * w - 1), x(mid));
                else r.set(x(left + 30 + i * w), x(mid), x(left + 30 + (i + 1) * w - 1), x(mid - v));
                c.drawRect(r, v >= 0 ? homeBar : awayBar);
            }
        }

        /** Both sides' starters: number and name, the formation over each. */
        private void lineups(Canvas c, Detail d, Game g) {
            float left = 1280;
            column(c, left, "LINE-UPS");
            float y = 466;
            c.drawText(d.homeFormation.isEmpty() ? item.home : d.homeFormation, x(left + 30), x(y), soft);
            String af = d.awayFormation.isEmpty() ? item.away : d.awayFormation;
            c.drawText(af, x(left + 550) - soft.measureText(af), x(y), soft);
            y += 40;
            List<String[]> hs = starters(d.homeLineup), as = starters(d.awayLineup);
            if (hs.isEmpty() && as.isEmpty()) {
                c.drawText("The line-ups come about an hour before kick-off", x(left + 30), x(y + 10), soft);
                return;
            }
            for (int i = 0; i < Math.max(hs.size(), as.size()) && i < 11; i++) {
                if (i < hs.size()) {
                    String[] pl = hs.get(i);
                    c.drawText(pl[Espn.P_NUMBER], x(left + 30), x(y), minute);
                    c.drawText(Ui.fit(pl[Espn.P_SHORT].isEmpty() ? pl[Espn.P_NAME] : pl[Espn.P_SHORT], body, x(200)), x(left + 74), x(y), body);
                }
                if (i < as.size()) {
                    String[] pl = as.get(i);
                    String nm = Ui.fit(pl[Espn.P_SHORT].isEmpty() ? pl[Espn.P_NAME] : pl[Espn.P_SHORT], body, x(200));
                    c.drawText(nm, x(left + 506) - body.measureText(nm), x(y), body);
                    c.drawText(pl[Espn.P_NUMBER], x(left + 550) - minute.measureText(pl[Espn.P_NUMBER]), x(y), minute);
                }
                y += 38;
            }
        }

        private List<String[]> starters(List<String[]> all) {
            List<String[]> out = new ArrayList<>();
            for (String[] p : all) if (p != null && p.length > Espn.P_SHORT && "1".equals(p[Espn.P_STARTER])) out.add(p);
            return out;
        }

        /** The foot: the channels that show it, and in step, the delay's buttons. */
        private void foot(Canvas c) {
            float bx = 60, y = 930;
            if (buttons.isEmpty()) {
                String w = rows == null ? "Looking for it in your channels…" : "Not found in your channels";
                c.drawText(w, x(bx), x(y + 52), soft);
                return;
            }
            for (int i = 0; i < buttons.size(); i++) {
                String[] b = buttons.get(i);
                boolean on = i == focus;
                String words = b[1];
                float w = button.measureText(words) / Ui.f(1) + 64;
                if (bx + w > 1860) break;
                r.set(x(bx), x(y), x(bx + w), x(y + 80));
                c.drawRoundRect(r, x(40), x(40), on ? white : card);
                c.drawText(words, x(bx + 32), x(y + 50), on ? buttonOn : button);
                bx += w + 18;
            }
        }

        private int colour(String hex, int fallback) {
            try {
                if (hex == null || hex.length() != 6) return fallback;
                int col = Color.parseColor("#" + hex);
                float[] hsv = new float[3];
                Color.colorToHSV(col, hsv);
                // A colour that would vanish on the dark card lifted a little.
                if (hsv[2] < 0.35f) hsv[2] = 0.55f;
                return Color.HSVToColor(hsv);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }
}
