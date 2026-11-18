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
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.F1;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;
import com.gathin.homefieldtv.sports.Squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sports hub, like the TV launcher's Live Centre, in three sections picked from the pills beside its title.
 *
 * <p>Today: the games and events in every sport the apps follow, live first, then later today, then those just
 * finished, a row each (the competition's logo, both sides with their crests, the score or the kick-off, how far on).
 * Beside the list, the game the remote is on: big, with the family's own channels that show it, English first, and
 * whether the hub is in step with the channel being watched. OK opens its page (GameScreen); Right goes on to a
 * football game's two clubs, where OK opens the club's page (TeamScreen).
 *
 * <p>Tables: the leagues down the left, the one the remote is on as its table beside them; Right goes into the table,
 * OK on a club opens its page.
 *
 * <p>Formula 1: the drivers in championship order, the one the remote is on beside them with the next Grand Prix; OK
 * opens the driver's page (DriverScreen).
 *
 * <p>Up from the top of a list goes to the pills; Left and Right (or OK) there pick a section, Down comes back.
 */
final class SportsScreen extends Screen {
    private static final long REFRESH_MS = 30_000L;
    /** The sections. */
    static final int TODAY = 0, TABLES = 1, FORMULA = 2;
    static final String[] SECTIONS = {"TODAY", "TABLES", "FORMULA 1"};
    /** Where the remote is: the menu, the pills, today's games, a game's two clubs, the leagues, a table, the drivers. */
    static final int RAIL = 0, PILLS = 1, LIST = 2, SIDES = 3, LEAGUES = 4, TABLE = 5, DRIVERS = 6;

    private Rail rail;
    private Hub hub;
    int zone = LIST;
    int section = TODAY;
    /** The pill the remote is on (while on the pills). */
    int pill;
    private List<SportsData.Item> items = new ArrayList<>();
    int focus;
    /** On a game's clubs: 0 the home side, 1 the away side. */
    int side;
    private boolean loading = true;
    private final Map<String, List<Matching.Row>> channels = new HashMap<>();
    /** Tables: the league the remote is on (Espn.TABLES), the row in its table; each league's rows and clubs as read. */
    int league, row;
    final Map<String, List<String[]>> tables = new HashMap<>();
    private final Map<String, Map<String, String[]>> clubs = new HashMap<>();
    private final Set<String> asking = new HashSet<>();
    /** Formula 1: the drivers in championship order, the next Grand Prix, the driver the remote is on. */
    List<F1.Standing> drivers = new ArrayList<>();
    F1.Weekend weekend;
    int driver;
    private boolean f1Asking;
    private long f1At;
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

    /** For previews and tests: a league's table and clubs as if read. */
    void previewTable(String slug, List<String[]> rows, List<String[]> teams) {
        tables.put(slug, new ArrayList<>(rows));
        Map<String, String[]> byId = new HashMap<>();
        for (String[] t : teams) byId.put(t[Espn.C_ID], t);
        clubs.put(slug, byId);
        if (hub != null) hub.invalidate();
    }

    /** For previews and tests: the drivers' championship and the next Grand Prix as if read. */
    void previewF1(List<F1.Standing> list, F1.Weekend next) {
        drivers = new ArrayList<>(list);
        weekend = next;
        f1At = System.currentTimeMillis();
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
        if (!Exec.inline) {
            load();
            if (section == TABLES) askTable();
            if (section == FORMULA) askF1();
        }
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
                if (zone == SIDES && !football(chosen())) zone = LIST;
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

    /** The league the remote is on, its table and its clubs (their colours), read once (again when it failed). */
    private void askTable() {
        String slug = Espn.TABLES[league][0];
        List<String[]> had = tables.get(slug);
        if (had != null && !had.isEmpty() || asking.contains(slug) || Exec.inline) return;
        asking.add(slug);
        Exec.io(() -> {
            List<String[]> rows = Espn.table(slug);
            Map<String, String[]> byId = new HashMap<>();
            for (String[] t : Espn.teams(slug)) byId.put(t[Espn.C_ID], t);
            Exec.main(() -> {
                asking.remove(slug);
                tables.put(slug, rows);
                clubs.put(slug, byId);
                if (row >= rows.size()) row = 0;
                hub.invalidate();
            });
        });
    }

    /** The drivers' championship and the next Grand Prix, read when the section opens (kept ten minutes). */
    private void askF1() {
        if (f1Asking || Exec.inline || !drivers.isEmpty() && System.currentTimeMillis() - f1At < 10 * 60_000L) return;
        f1Asking = true;
        Exec.io(() -> {
            List<F1.Standing> got = F1.drivers();
            F1.Weekend next = F1.next();
            Exec.main(() -> {
                f1Asking = false;
                if (!got.isEmpty()) {
                    drivers = new ArrayList<>(got);
                    f1At = System.currentTimeMillis();
                }
                weekend = next;
                if (driver >= drivers.size()) driver = 0;
                hub.invalidate();
            });
        });
    }

    SportsData.Item chosen() {
        return focus >= 0 && focus < items.size() ? items.get(focus) : null;
    }

    /** A football game with its clubs known: its sides can be opened. */
    static boolean football(SportsData.Item it) {
        return it != null && SportsData.FOOTBALL.equals(it.kind) && it.game != null && it.game.homeId != null && it.game.awayId != null
                && !it.game.homeId.isEmpty() && !it.game.awayId.isEmpty();
    }

    List<String[]> table() {
        List<String[]> t = tables.get(Espn.TABLES[league][0]);
        return t == null ? new ArrayList<>() : t;
    }

    /** The section's own place for the remote: where Down from the pills and Right from the menu go. */
    int home() {
        return section == TABLES ? LEAGUES : section == FORMULA ? DRIVERS : LIST;
    }

    /** A section picked: its lists read when they haven't been. */
    void choose(int s) {
        section = s;
        pill = s;
        if (s == TABLES) askTable();
        if (s == FORMULA) askF1();
    }

    private void toRail() {
        zone = RAIL;
        rail.setActive(true);
    }

    @Override boolean key(int code, KeyEvent e) {
        if (zone == RAIL) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                zone = home();
                rail.setActive(false);
                hub.invalidate();
                return true;
            }
            return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
        }
        boolean used;
        switch (zone) {
            case PILLS:
                used = pillsKey(code);
                break;
            case SIDES:
                used = sidesKey(code);
                break;
            case LEAGUES:
                used = leaguesKey(code);
                break;
            case TABLE:
                used = tableKey(code);
                break;
            case DRIVERS:
                used = driversKey(code);
                break;
            default:
                used = listKey(code);
                break;
        }
        if (used) hub.invalidate();
        return used;
    }

    private boolean pillsKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (pill > 0) choose(pill - 1);
                else toRail();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (pill < SECTIONS.length - 1) choose(pill + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                zone = home();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                return true;
            default:
                return false;
        }
    }

    private boolean listKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus > 0) focus--;
                else up();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < items.size() - 1) focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                toRail();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (football(chosen())) {
                    zone = SIDES;
                    side = 0;
                }
                return true;
            default:
                return false;
        }
        askChannelsSoon();
        return true;
    }

    private void up() {
        zone = PILLS;
        pill = section;
    }

    private boolean sidesKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (side == 1) side = 0;
                else zone = LIST;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                side = 1;
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                zone = LIST;
                return listKey(code);
            default:
                return false;
        }
    }

    private boolean leaguesKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (league > 0) {
                    league--;
                    row = 0;
                    askTable();
                } else {
                    up();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (league < Espn.TABLES.length - 1) {
                    league++;
                    row = 0;
                    askTable();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                toRail();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!table().isEmpty()) zone = TABLE;
                return true;
            default:
                return false;
        }
    }

    private boolean tableKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) row--;
                else up();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < table().size() - 1) row++;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                zone = LEAGUES;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    private boolean driversKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (driver > 0) driver--;
                else up();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (driver < drivers.size() - 1) driver++;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                toRail();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    @Override void ok() {
        switch (zone) {
            case RAIL:
                Rail.go(tv, rail.focus, rail.page);
                return;
            case PILLS:
                choose(pill);
                zone = home();
                break;
            case SIDES: {
                SportsData.Item it = chosen();
                if (football(it)) tv.push(new TeamScreen(tv, Squad.Club.of(it.game, side == 1)));
                return;
            }
            case LEAGUES:
                if (!table().isEmpty()) zone = TABLE;
                break;
            case TABLE: {
                List<String[]> t = table();
                if (row >= 0 && row < t.size()) {
                    String slug = Espn.TABLES[league][0];
                    Map<String, String[]> known = clubs.get(slug);
                    String[] r = t.get(row);
                    tv.push(new TeamScreen(tv, Squad.Club.of(slug, r, known == null ? null : known.get(r[Espn.T_ID]))));
                }
                return;
            }
            case DRIVERS:
                if (driver >= 0 && driver < drivers.size()) tv.push(new DriverScreen(tv, new ArrayList<>(drivers), driver, weekend));
                return;
            default: {
                SportsData.Item it = chosen();
                if (it != null) tv.push(new GameScreen(tv, it, channels.get(it.key)));
                return;
            }
        }
        hub.invalidate();
    }

    @Override boolean back() {
        switch (zone) {
            case RAIL:
                zone = home();
                rail.setActive(false);
                break;
            case PILLS:
                zone = home();
                break;
            case SIDES:
                zone = LIST;
                break;
            case TABLE:
                zone = LEAGUES;
                break;
            default:
                return false;
        }
        hub.invalidate();
        return true;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** The sections and the lists, drawn in design pixels (1920 × 1080). */
    private final class Hub extends View {
        private static final float LIST_X = 170f, LIST_W = 860f, ROW = 96f, GAP = 12f, HEAD = 54f, TOP = 196f;
        private static final float CARD_X = 1080f, CARD_W = 780f;
        private final TextPaint title = Dark.text(54, 600, Dark.TEXT), sub = Dark.text(24, 500, Dark.MUTED);
        private final TextPaint section = Dark.text(22, 600, Dark.MUTED), name = Dark.text(28, 500, Dark.TEXT);
        private final TextPaint score = Dark.text(34, 700, Dark.TEXT), when = Dark.text(26, 600, Dark.SOFT), tag = Dark.text(20, 700, 0xFFFFFFFF);
        private final TextPaint big = Dark.text(104, 700, Dark.TEXT), bigName = Dark.text(36, 600, Dark.TEXT), status = Dark.text(30, 600, Dark.SOFT);
        private final TextPaint chName = Dark.text(26, 500, Dark.TEXT), chUnder = Dark.text(21, 500, Dark.MUTED), note = Dark.text(22, 500, Dark.MUTED);
        private final TextPaint leagueP = Dark.text(27, 500, Dark.TEXT), headP = Dark.text(21, 600, Dark.MUTED), cellP = Dark.text(26, 500, Dark.SOFT),
                ptsP = Dark.text(27, 700, Dark.TEXT), posP = Dark.text(30, 700, Dark.TEXT), teamP = Dark.text(22, 500, Dark.MUTED);
        private final Paint card = Dark.fill(Dark.CARD), cardOn = Dark.fill(Dark.CARD_HI), ring = Dark.line(Dark.RING, 3f);
        private final Paint live = Dark.fill(0xFFE5303B), panel = Dark.fill(Dark.PANEL), line = Dark.fill(Dark.LINE), amber = Dark.fill(Dark.ACCENT);
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), stripe = new Paint(Paint.ANTI_ALIAS_FLAG), teamPanel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ClubLook.Pills pills = new ClubLook.Pills(SECTIONS, 20f);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private final Glide tableScroll = new Glide(GuideGrid.ROW_MS), driverScroll = new Glide(GuideGrid.ROW_MS);
        private float scroll;

        Hub() {
            super(tv);
        }

        private float x(float d) {
            return Ui.f(d);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            pills.from(x(LIST_X) + title.measureText("Sports") + x(44f), x(14f), 0, SECTIONS.length - 1);
        }

        private Bitmap pic(String url, float size) {
            int px = Math.max(1, Math.round(x(size)));
            return Pics.get(url, px, px, Pics.FIT, redraw);
        }

        private void image(Canvas c, String url, float cx, float cy, float size) {
            Bitmap b = pic(url, size);
            if (b == null) return;
            float w = b.getWidth(), hh = b.getHeight();
            c.drawBitmap(b, x(cx) - w / 2f, x(cy) - hh / 2f, bmp);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = false;
            c.drawText("Sports", x(LIST_X), x(120), title);
            float pcy = x(102f);
            for (int i = 0; i < SECTIONS.length; i++) pills.draw(c, i, pcy, i == SportsScreen.this.section, zone == PILLS && i == pill, 1f);
            if (SportsScreen.this.section == TABLES) {
                moving = drawTables(c);
            } else if (SportsScreen.this.section == FORMULA) {
                moving = drawDrivers(c);
            } else {
                drawToday(c);
            }
            if (moving) postInvalidateOnAnimation();
        }

        // ---- today ----

        private void drawToday(Canvas c) {
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
                if (yy > -ROW && yy < 1100) row(c, it, yy, i == focus && (zone == LIST || zone == SIDES));
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
            if (on && zone == LIST) Dark.ring(c, r, x(18), ring);
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

        /** The game the remote is on, big, with the channels that show it; a football game's clubs can be gone on to. */
        private void side(Canvas c, SportsData.Item it) {
            r.set(x(CARD_X), x(TOP), x(CARD_X + CARD_W), x(1010));
            c.drawRoundRect(r, x(28), x(28), panel);
            float mid = CARD_X + CARD_W / 2f;
            image(c, it.compLogo, mid, TOP + 64, 64);
            String comp = Ui.fit(it.comp, sub, x(CARD_W - 80));
            c.drawText(comp, x(mid) - sub.measureText(comp) / 2f, x(TOP + 130), sub);
            // The two clubs: where the remote is (Right from the list), a ring round the crest and name.
            for (int s = 0; s < 2 && zone == SIDES; s++) {
                if (s != side) continue;
                float cx = s == 0 ? CARD_X + 170 : CARD_X + CARD_W - 170;
                r.set(x(cx - 140), x(TOP + 160), x(cx + 140), x(TOP + 390));
                c.drawRoundRect(r, x(22), x(22), cardOn);
                Dark.ring(c, r, x(22), ring);
            }
            image(c, it.homeLogo, CARD_X + 170, TOP + 250, 150);
            image(c, it.awayLogo, CARD_X + CARD_W - 170, TOP + 250, 150);
            String hn = Ui.fit(it.home, bigName, x(256)), an = Ui.fit(it.away, bigName, x(256));
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
                // The channel's logo on its tile (Logos: Behind logos).
                r.set(x(CARD_X + 40), x(y + 10), x(CARD_X + 120), x(y + 58));
                Logos.draw(c, row.logo, r, redraw);
                c.drawText(Ui.fit(row.name + (row.uhd ? "  4K" : ""), chName, x(CARD_W - 220)), x(CARD_X + 130), x(y + 30), chName);
                String under = row.now ? "Playing now" : row.under;
                c.drawText(Ui.fit(under, chUnder, x(CARD_W - 220)), x(CARD_X + 130), x(y + 60), chUnder);
                if (row.ch != null && row.ch.key.equals(watched)) {
                    r.set(x(CARD_X + CARD_W - 64), x(y + 20), x(CARD_X + CARD_W - 44), x(y + 40));
                    c.drawOval(r, amber);
                }
                y += 72;
            }
            String hint = zone == SIDES ? "OK  Open " + (side == 0 ? it.home : it.away) + "'s page: the squad and the players"
                    : football(it) ? "OK  The game's page   ▶  The clubs' pages" : "OK  Open the game's page, live stats in step with your channel";
            c.drawText(Ui.fit(hint, note, x(CARD_W - 80)), x(CARD_X + 40), x(985), note);
        }

        // ---- tables ----

        private static final float LEAGUE_X = 170f, LEAGUE_W = 300f, LEAGUE_ROW = 64f, TABLE_X = 510f, TABLE_R = 1860f,
                TABLE_TOP = 262f, TABLE_ROW = 58f, TABLE_GAP = 6f;
        private final String[] heads = {"P", "W", "D", "L", "GD", "PTS"};
        private final float[] cols = {1390f, 1470f, 1550f, 1630f, 1720f, 1820f};
        private final int[] cells = {Espn.T_PLAYED, Espn.T_WON, Espn.T_DRAWN, Espn.T_LOST, Espn.T_GD, Espn.T_POINTS};
        /** The words over the table and beside the driver, made when what they say changes (not each frame). */
        private String tableWords = "", f1Words = "", standing = "", gpDates = "";
        private int wordsLeague = -1, wordsSeason = -1;
        private F1.Standing wordsFor;
        private F1.Weekend wordsWeekend;

        private boolean drawTables(Canvas c) {
            String slug = Espn.TABLES[league][0];
            List<String[]> t = tables.get(slug);
            if (wordsLeague != league) {
                wordsLeague = league;
                tableWords = Espn.TABLES[league][1] + " · the table as it stands";
            }
            c.drawText(t == null ? "Reading the table…" : t.isEmpty() ? "No table just now" : tableWords, x(LIST_X), x(160), sub);
            // The leagues down the left.
            for (int i = 0; i < Espn.TABLES.length; i++) {
                float y = 206f + i * LEAGUE_ROW;
                r.set(x(LEAGUE_X - 16f), x(y), x(LEAGUE_X + LEAGUE_W), x(y + LEAGUE_ROW - 8f));
                if (i == league && zone == LEAGUES) Focus.pill(c, r);
                else if (i == league) Focus.selected(c, r);
                c.drawText(Espn.TABLES[i][1], x(LEAGUE_X + 8f), Type.centred(leagueP, r.centerY()), leagueP);
            }
            // The table: its head, then a row a club, kept in sight round the remote.
            float hy = x(232f);
            c.drawText("#", x(TABLE_X + 26f), hy, headP);
            c.drawText("CLUB", x(TABLE_X + 140f), hy, headP);
            for (int i = 0; i < heads.length; i++) c.drawText(heads[i], x(cols[i]) - headP.measureText(heads[i]) / 2f, hy, headP);
            if (t == null || t.isEmpty()) return false;
            float want = Math.max(0f, row * (TABLE_ROW + TABLE_GAP) - 5f * (TABLE_ROW + TABLE_GAP));
            float most = Math.max(0f, t.size() * (TABLE_ROW + TABLE_GAP) - (1040f - TABLE_TOP));
            tableScroll.to(Math.min(want, most));
            boolean moving = tableScroll.step();
            c.save();
            c.clipRect(x(TABLE_X - 12f), x(TABLE_TOP - 6f), x(TABLE_R + 12f), getHeight());
            float y0 = TABLE_TOP - tableScroll.value();
            for (int i = 0; i < t.size(); i++) {
                float y = y0 + i * (TABLE_ROW + TABLE_GAP);
                if (y < TABLE_TOP - TABLE_ROW || y > 1080f) continue;
                String[] rr = t.get(i);
                boolean on = i == row && zone == TABLE;
                r.set(x(TABLE_X), x(y), x(TABLE_R), x(y + TABLE_ROW));
                c.drawRoundRect(r, x(14f), x(14f), on ? cardOn : card);
                if (on) Dark.ring(c, r, x(14f), ring);
                float cy = r.centerY();
                c.drawText(rr[Espn.T_RANK], x(TABLE_X + 26f), Type.centred(posP, cy), posP);
                Bitmap crest = pic(rr[Espn.T_LOGO], 40f);
                if (crest != null) c.drawBitmap(crest, x(TABLE_X + 96f) - crest.getWidth() / 2f, cy - crest.getHeight() / 2f, bmp);
                c.drawText(rr[Espn.T_NAME], x(TABLE_X + 140f), Type.centred(name, cy), name);
                for (int k = 0; k < cells.length; k++) {
                    String v = rr[cells[k]];
                    TextPaint p = k == cells.length - 1 ? ptsP : cellP;
                    c.drawText(v, x(cols[k]) - p.measureText(v) / 2f, Type.centred(p, cy), p);
                }
            }
            c.restore();
            return moving;
        }

        // ---- Formula 1 ----

        private static final float DRIVER_ROW = 78f, DRIVER_GAP = 10f;

        private boolean drawDrivers(Canvas c) {
            int season = drivers.isEmpty() ? 0 : drivers.get(0).season;
            if (season != wordsSeason) {
                wordsSeason = season;
                f1Words = season > 0 ? "Drivers' championship · " + season : "Drivers' championship";
            }
            c.drawText(drivers.isEmpty() ? (f1Asking || Exec.inline ? "Reading the championship…" : "The championship isn't available just now")
                    : f1Words, x(LIST_X), x(160), sub);
            if (drivers.isEmpty()) return false;
            float want = Math.max(0f, driver * (DRIVER_ROW + DRIVER_GAP) - 4f * (DRIVER_ROW + DRIVER_GAP));
            float most = Math.max(0f, drivers.size() * (DRIVER_ROW + DRIVER_GAP) - (1040f - TOP));
            driverScroll.to(Math.min(want, most));
            boolean moving = driverScroll.step();
            c.save();
            c.clipRect(0, x(TOP - 10f), x(LIST_X + LIST_W + 20f), getHeight());
            float y0 = TOP - driverScroll.value();
            for (int i = 0; i < drivers.size(); i++) {
                float y = y0 + i * (DRIVER_ROW + DRIVER_GAP);
                if (y < TOP - DRIVER_ROW || y > 1080f) continue;
                F1.Standing d = drivers.get(i);
                boolean on = i == driver && zone == DRIVERS;
                r.set(x(LIST_X), x(y), x(LIST_X + LIST_W), x(y + DRIVER_ROW));
                c.drawRoundRect(r, x(16f), x(16f), on ? cardOn : card);
                if (on) Dark.ring(c, r, x(16f), ring);
                float cy = r.centerY();
                c.drawText(d.position, x(LIST_X + 50f) - posP.measureText(d.position) / 2f, Type.centred(posP, cy), posP);
                stripe.setColor(F1.colour(d.teamId));
                r.set(x(LIST_X + 92f), cy - x(24f), x(LIST_X + 98f), cy + x(24f));
                c.drawRoundRect(r, x(3f), x(3f), stripe);
                Bitmap face = d.headshot == null ? null : pic(d.headshot, 66f);
                if (face != null) c.drawBitmap(face, x(LIST_X + 150f) - face.getWidth() / 2f, cy + x(33f) - face.getHeight(), bmp);
                c.drawText(d.name, x(LIST_X + 200f), cy - x(4f), name);
                c.drawText(d.team, x(LIST_X + 200f), cy + x(26f), teamP);
                c.drawText(d.points, x(LIST_X + LIST_W - 96f) - ptsP.measureText(d.points), Type.centred(ptsP, cy), ptsP);
                c.drawText("PTS", x(LIST_X + LIST_W - 84f), Type.centred(headP, cy), headP);
            }
            c.restore();
            if (driver >= 0 && driver < drivers.size()) driverCard(c, drivers.get(driver));
            return moving;
        }

        /** The driver the remote is on, beside the list, with the next Grand Prix. */
        private void driverCard(Canvas c, F1.Standing d) {
            int colour = F1.colour(d.teamId);
            teamPanel.setColor(ClubLook.at(colour, 0.05, 0.55f));
            r.set(x(CARD_X), x(TOP), x(CARD_X + CARD_W), x(1010));
            c.drawRoundRect(r, x(28), x(28), teamPanel);
            float mid = CARD_X + CARD_W / 2f;
            Bitmap face = d.headshot == null ? null : pic(d.headshot, 340f);
            if (face != null) c.drawBitmap(face, x(mid) - face.getWidth() / 2f, x(TOP + 360f) - face.getHeight(), bmp);
            c.drawText(d.name, x(mid) - bigName.measureText(d.name) / 2f, x(TOP + 420f), bigName);
            c.drawText(d.team, x(mid) - sub.measureText(d.team) / 2f, x(TOP + 462f), sub);
            if (wordsFor != d) {
                wordsFor = d;
                standing = "P" + d.position + "  ·  " + d.points + " points";
            }
            c.drawText(standing, x(mid) - status.measureText(standing) / 2f, x(TOP + 520f), status);
            float y = TOP + 580f;
            c.drawRect(x(CARD_X + 40), x(y), x(CARD_X + CARD_W - 40), x(y) + Math.max(1f, x(1.5f)), line);
            if (weekend != null && weekend.name != null) {
                if (wordsWeekend != weekend) {
                    wordsWeekend = weekend;
                    gpDates = DriverScreen.dates(weekend.start, weekend.race, java.time.ZoneId.systemDefault());
                }
                c.drawText("NEXT GRAND PRIX", x(CARD_X + 40), x(y + 52f), section);
                c.drawText(weekend.name, x(CARD_X + 40), x(y + 104f), bigName);
                if (!gpDates.isEmpty()) c.drawText(gpDates, x(CARD_X + 40), x(y + 146f), sub);
            }
            c.drawText("OK  The driver's page", x(CARD_X + 40), x(985), note);
        }
    }
}
