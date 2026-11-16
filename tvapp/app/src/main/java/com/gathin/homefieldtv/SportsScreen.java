package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sports hub, like the TV launcher's Live Centre: today's games and events in every sport the apps follow, live
 * first, then later today, then those just finished, a row each (the competition's logo, both sides with their crests,
 * the score or the kick-off, how far on). Beside the list, the game the remote is on: big, with the family's own channels
 * that show it, English first, and whether the hub is in step with the channel being watched. OK opens its page
 * (GameScreen): the live stats, in time with the channel.
 */
final class SportsScreen extends Screen {
    private static final long REFRESH_MS = 30_000L;

    private Rail rail;
    private Hub hub;
    private boolean onRail;
    private List<SportsData.Item> items = new ArrayList<>();
    private int focus;
    private boolean loading = true;
    private final Map<String, List<Matching.Row>> channels = new HashMap<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::load;
    private final Runnable askChannels = this::askChannels;

    SportsScreen(TvActivity tv) {
        super(tv);
    }

    /** For previews: the hub drawn with these, the remote on the first. */
    void preview(List<SportsData.Item> list, Map<String, List<Matching.Row>> rows) {
        items = new ArrayList<>(list);
        channels.clear();
        channels.putAll(rows);
        loading = false;
        focus = 0;
        if (hub != null) hub.invalidate();
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Dark.GROUND);
        hub = new Hub();
        rail = new Rail(tv, Rail.SPORTS);
        f.addView(hub, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        if (!Exec.inline) load();
    }

    @Override void hidden() {
        h.removeCallbacks(refresh);
        h.removeCallbacks(askChannels);
    }

    @Override void tick() {
        rail.invalidate();
        hub.invalidate();
    }

    private void load() {
        h.removeCallbacks(refresh);
        Exec.io(() -> {
            List<SportsData.Item> got = SportsData.load(20_000L);
            Exec.main(() -> {
                String was = focus < items.size() ? items.get(focus).key : "";
                items = new ArrayList<>(got);
                loading = false;
                focus = 0;
                for (int i = 0; i < items.size(); i++) if (items.get(i).key.equals(was)) focus = i;
                hub.invalidate();
                askChannelsSoon();
                h.postDelayed(refresh, REFRESH_MS);
            });
        });
    }

    private void askChannelsSoon() {
        h.removeCallbacks(askChannels);
        h.postDelayed(askChannels, 350);
    }

    /** The playlist's channels for the game the remote is on (and the next few), once each. */
    private void askChannels() {
        List<SportsData.Item> want = new ArrayList<>();
        for (int i = focus; i < Math.min(items.size(), focus + 4); i++) {
            SportsData.Item it = items.get(i);
            if (!it.over && !channels.containsKey(it.key)) want.add(it);
        }
        if (want.isEmpty()) return;
        Channel playing = tv.live.channel();
        Exec.io(() -> {
            Map<String, List<Matching.Row>> found = new HashMap<>();
            for (SportsData.Item it : want) {
                long when = it.live ? System.currentTimeMillis() : Math.max(System.currentTimeMillis(), it.start + 60_000L);
                found.put(it.key, Matching.rows(tv, it.game, playing, when));
            }
            Exec.main(() -> {
                channels.putAll(found);
                hub.invalidate();
            });
        });
    }

    private SportsData.Item chosen() {
        return focus >= 0 && focus < items.size() ? items.get(focus) : null;
    }

    @Override boolean key(int code, KeyEvent e) {
        if (onRail) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                onRail = false;
                rail.setActive(false);
                hub.invalidate();
                return true;
            }
            return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus > 0) focus--;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < items.size() - 1) focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                onRail = true;
                rail.setActive(true);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
        hub.invalidate();
        askChannelsSoon();
        return true;
    }

    @Override void ok() {
        if (onRail) {
            Rail.go(tv, rail.focus, rail.page);
            return;
        }
        SportsData.Item it = chosen();
        if (it != null) tv.push(new GameScreen(tv, it, channels.get(it.key)));
    }

    @Override boolean back() {
        if (onRail) {
            onRail = false;
            rail.setActive(false);
            hub.invalidate();
            return true;
        }
        return false;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** The list and the game the remote is on, drawn in design pixels (1920 × 1080). */
    private final class Hub extends View {
        private static final float LIST_X = 170f, LIST_W = 860f, ROW = 96f, GAP = 12f, HEAD = 54f, TOP = 196f;
        private static final float CARD_X = 1080f, CARD_W = 780f;
        private final TextPaint title = Dark.text(54, 600, Dark.TEXT), sub = Dark.text(24, 500, Dark.MUTED);
        private final TextPaint section = Dark.text(22, 600, Dark.MUTED), name = Dark.text(28, 500, Dark.TEXT);
        private final TextPaint score = Dark.text(34, 700, Dark.TEXT), when = Dark.text(26, 600, Dark.SOFT), tag = Dark.text(20, 700, 0xFFFFFFFF);
        private final TextPaint big = Dark.text(104, 700, Dark.TEXT), bigName = Dark.text(36, 600, Dark.TEXT), status = Dark.text(30, 600, Dark.SOFT);
        private final TextPaint chName = Dark.text(26, 500, Dark.TEXT), chUnder = Dark.text(21, 500, Dark.MUTED), note = Dark.text(22, 500, Dark.MUTED);
        private final Paint card = Dark.fill(Dark.CARD), cardOn = Dark.fill(Dark.CARD_HI), ring = Dark.line(Dark.RING, 3f);
        private final Paint live = Dark.fill(0xFFE5303B), panel = Dark.fill(Dark.PANEL), line = Dark.fill(Dark.LINE), amber = Dark.fill(Dark.ACCENT);
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private float scroll;

        Hub() {
            super(tv);
        }

        private float x(float d) {
            return Ui.f(d);
        }

        private Bitmap pic(String url, float size) {
            int px = Math.max(1, Math.round(x(size)));
            return Pics.get(url, px, px, Pics.FIT, this::invalidate);
        }

        private void image(Canvas c, String url, float cx, float cy, float size) {
            Bitmap b = pic(url, size);
            if (b == null) return;
            float w = b.getWidth(), hh = b.getHeight();
            c.drawBitmap(b, x(cx) - w / 2f, x(cy) - hh / 2f, bmp);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawText("Sports", x(LIST_X), x(120), title);
            c.drawText(loading ? "Finding today's games…" : items.isEmpty() ? "Nothing on today" : "Live now and today · " + Ui.clock(System.currentTimeMillis()),
                    x(LIST_X), x(160), sub);
            // Rows and their sections, kept in sight around the remote.
            List<float[]> places = new ArrayList<>();
            float y = 0;
            String lastSection = "";
            for (int i = 0; i < items.size(); i++) {
                String s = sectionOf(items.get(i));
                if (!s.equals(lastSection)) {
                    y += HEAD;
                    lastSection = s;
                }
                places.add(new float[]{y, i});
                y += ROW + GAP;
            }
            if (focus < places.size()) {
                float fy = places.get(focus)[0];
                float want = Math.max(0, fy - 300f);
                scroll += (want - scroll) * (Exec.inline ? 1f : 0.35f);
                if (Math.abs(want - scroll) > 0.5f && !Exec.inline) postInvalidateOnAnimation();
            }
            c.save();
            c.clipRect(0, x(TOP - 10), x(LIST_X + LIST_W + 20), getHeight());
            lastSection = "";
            float yy = TOP - scroll;
            for (int i = 0; i < items.size(); i++) {
                SportsData.Item it = items.get(i);
                String s = sectionOf(it);
                if (!s.equals(lastSection)) {
                    if (it.live) c.drawCircle(x(LIST_X + 8), x(yy + 30), x(7), live);
                    c.drawText(s, x(LIST_X + (it.live ? 26 : 0)), x(yy + 38), section);
                    yy += HEAD;
                    lastSection = s;
                }
                if (yy > -ROW && yy < 1100) row(c, it, yy, i == focus && !onRail);
                yy += ROW + GAP;
            }
            c.restore();
            SportsData.Item it = chosen();
            if (it != null) side(c, it);
        }

        private String sectionOf(SportsData.Item it) {
            return it.live ? "LIVE NOW" : it.over ? "FINISHED" : "LATER";
        }

        private void row(Canvas c, SportsData.Item it, float y, boolean on) {
            r.set(x(LIST_X), x(y), x(LIST_X + LIST_W), x(y + ROW));
            c.drawRoundRect(r, x(18), x(18), on ? cardOn : card);
            if (on) Dark.ring(c, r, x(18), ring);
            float cy = y + ROW / 2f;
            image(c, it.compLogo, LIST_X + 40, cy, 40);
            image(c, it.homeLogo, LIST_X + 104, cy, 44);
            float nameW = 250f;
            c.drawText(Ui.fit(it.home, name, x(nameW)), x(LIST_X + 140), x(cy + 10), name);
            String mid = it.score.isEmpty() ? Ui.clock(it.start) : it.score;
            TextPaint mp = it.score.isEmpty() ? when : score;
            float mw = mp.measureText(mid);
            c.drawText(mid, x(LIST_X + 470) - mw / 2f, x(cy + 12), mp);
            String away = Ui.fit(it.away, name, x(nameW));
            c.drawText(away, x(LIST_X + 760) - name.measureText(away) - x(48), x(cy + 10), name);
            image(c, it.awayLogo, LIST_X + 780, cy, 44);
            if (it.live || it.over) {
                String st = it.status.isEmpty() ? (it.live ? "LIVE" : "FT") : it.status;
                float tw = tag.measureText(st) + x(22);
                r.set(x(LIST_X + 470) - tw / 2f, x(cy + 20), x(LIST_X + 470) + tw / 2f, x(cy + 44));
                if (it.live) c.drawRoundRect(r, x(12), x(12), live);
                else c.drawRoundRect(r, x(12), x(12), line);
                c.drawText(st, r.centerX() - tag.measureText(st) / 2f, r.bottom - x(6), tag);
            }
        }

        /** The game the remote is on, big, with the channels that show it. */
        private void side(Canvas c, SportsData.Item it) {
            r.set(x(CARD_X), x(TOP), x(CARD_X + CARD_W), x(1010));
            c.drawRoundRect(r, x(28), x(28), panel);
            float mid = CARD_X + CARD_W / 2f;
            image(c, it.compLogo, mid, TOP + 64, 64);
            String comp = Ui.fit(it.comp, sub, x(CARD_W - 80));
            c.drawText(comp, x(mid) - sub.measureText(comp) / 2f, x(TOP + 130), sub);
            image(c, it.homeLogo, CARD_X + 170, TOP + 250, 150);
            image(c, it.awayLogo, CARD_X + CARD_W - 170, TOP + 250, 150);
            String hn = Ui.fit(it.home, bigName, x(300)), an = Ui.fit(it.away, bigName, x(300));
            c.drawText(hn, x(CARD_X + 170) - bigName.measureText(hn) / 2f, x(TOP + 370), bigName);
            c.drawText(an, x(CARD_X + CARD_W - 170) - bigName.measureText(an) / 2f, x(TOP + 370), bigName);
            String sc = it.score.isEmpty() ? Ui.clock(it.start) : it.score;
            c.drawText(sc, x(mid) - big.measureText(sc) / 2f, x(TOP + 290), big);
            String st = it.live ? (it.status.isEmpty() ? "Live" : it.status) : it.over ? "Full time" : Ui.day(it.start, System.currentTimeMillis());
            c.drawText(st, x(mid) - status.measureText(st) / 2f, x(TOP + 340), status);
            // The family's channels for it, English first; the one playing now marked.
            float y = TOP + 430;
            c.drawRect(x(CARD_X + 40), x(y), x(CARD_X + CARD_W - 40), x(y) + Math.max(1f, x(1.5f)), line);
            y += 50;
            List<Matching.Row> rows = channels.get(it.key);
            c.drawText(rows == null ? (it.over ? "Finished" : "Looking for it in your channels…") : rows.isEmpty() ? "Not found in your channels" : "Watch on", x(CARD_X + 40), x(y), section);
            y += 24;
            String watched = InStep.watched(System.currentTimeMillis());
            for (int i = 0; rows != null && i < Math.min(4, rows.size()); i++) {
                Matching.Row row = rows.get(i);
                y += 16;
                image(c, row.logo, CARD_X + 80, y + 34, 56);
                c.drawText(Ui.fit(row.name + (row.uhd ? "  4K" : ""), chName, x(CARD_W - 220)), x(CARD_X + 130), x(y + 30), chName);
                String under = row.now ? "Playing now" : row.under;
                c.drawText(Ui.fit(under, chUnder, x(CARD_W - 220)), x(CARD_X + 130), x(y + 60), chUnder);
                if (row.ch != null && row.ch.key.equals(watched)) {
                    r.set(x(CARD_X + CARD_W - 64), x(y + 20), x(CARD_X + CARD_W - 44), x(y + 40));
                    c.drawOval(r, amber);
                }
                y += 72;
            }
            String hint = "OK  Open the game's page, live stats in step with your channel";
            c.drawText(Ui.fit(hint, note, x(CARD_W - 80)), x(CARD_X + 40), x(985), note);
        }
    }
}
