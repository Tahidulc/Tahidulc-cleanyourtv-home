package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.F1;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An F1 driver's page, as the family's picture of one has it: the page and a big rounded card in his team's colour,
 * his car number huge, italic and faded behind him at the top left, and the driver standing in his kit over the card's
 * left half, rising above its top edge. On the right the next Grand Prix (its dates, its name and circuit) with a
 * countdown in days, hours and minutes, then his team's name very large with its logo, his number, his country's flag
 * and his full name, his nationality and age, and his team's car from the side breaking out past the card's right edge.
 * Down the left his season so far: Grands Prix, points and podiums, under its year. Left and Right go to the driver
 * before or after him in the championship, the page sliding across and its colour turning to the next team's.
 *
 * <p>Pictures: formula1.com's (the driver, the car, the logo: the family of addresses the app's team logos come from),
 * else ESPN's headshot, else a figure and a car drawn in the team's colour. His Grands Prix and podiums are one read of
 * his results this season (Jolpica), when the remote rests on him; the drivers either side are read after him.
 */
final class DriverScreen extends Screen {
    static final long SLIDE_MS = 340, REST_MS = 250;
    /** The card (design pixels); how far a driver's page slides; his figure's height as drawn. */
    static final float CARD_L = 80f, CARD_T = 200f, CARD_R = 1840f, CARD_B = 1000f, SLIDE = 520f, FIGURE_H = 1720f;

    final List<F1.Standing> drivers;
    int at;
    F1.Weekend next;
    final Map<String, F1.Season> seasons = new HashMap<>();
    final Glide pos = new Glide(SLIDE_MS, SLIDE);
    private Page page;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable load = this::load;

    DriverScreen(TvActivity tv, List<F1.Standing> drivers, int at, F1.Weekend next) {
        super(tv);
        this.drivers = drivers;
        this.at = Math.max(0, Math.min(drivers.size() - 1, at));
        this.next = next;
        pos.jump(this.at);
    }

    F1.Standing driver() {
        return at >= 0 && at < drivers.size() ? drivers.get(at) : null;
    }

    /** For previews and tests: a driver's season as if it had been read. */
    void preview(F1.Standing st, F1.Season s) {
        seasons.put(st.driverId, s);
        if (page != null) page.invalidate();
    }

    /** For previews: a driver's pictures from these addresses (drawn ones) instead of formula1.com's; null for none. */
    final Map<F1.Standing, String[]> previewPictures = new HashMap<>();

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new Page();
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        page.countdown();
        h.removeCallbacks(load);
        if (!Exec.inline) h.postDelayed(load, 0);
    }

    @Override void hidden() {
        h.removeCallbacks(load);
    }

    @Override void tick() {
        page.countdown();
        page.invalidate();
    }

    /** His season, once the remote rests on him; then the drivers either side. */
    private void load() {
        F1.Standing st = driver();
        if (st == null || st.driverId.isEmpty()) return;
        if (!seasons.containsKey(st.driverId)) {
            F1.Season had = F1.keptSeason(st.driverId);
            if (had == null) {
                Exec.io(() -> {
                    F1.Season s = F1.season(st.driverId);
                    Exec.main(() -> {
                        if (s != null) {
                            seasons.put(st.driverId, s);
                            page.invalidate();
                        }
                        ahead();
                    });
                });
                return;
            }
            seasons.put(st.driverId, had);
            page.invalidate();
        }
        ahead();
    }

    private void ahead() {
        for (int i = at - 1; i <= at + 1; i += 2) {
            if (i < 0 || i >= drivers.size()) continue;
            String id = drivers.get(i).driverId;
            if (id.isEmpty() || seasons.containsKey(id) || F1.keptSeason(id) != null) continue;
            Exec.io(() -> F1.season(id));
        }
    }

    @Override boolean key(int code, KeyEvent e) {
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        int to = Math.max(0, Math.min(drivers.size() - 1, at + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
        if (to != at) {
            at = to;
            pos.to(to);
            h.removeCallbacks(load);
            if (!Exec.inline) h.postDelayed(load, REST_MS);
            page.ahead();
            page.invalidate();
        }
        return true;
    }

    /** Pure: "20 – 22 Nov", or "30 Oct – 1 Nov" across two months, in the TV's own time zone; "" when not known. */
    static String dates(long start, long end, ZoneId zone) {
        if (end <= 0) return "";
        LocalDate b = Instant.ofEpochMilli(end).atZone(zone).toLocalDate();
        LocalDate a = Instant.ofEpochMilli(start > 0 ? start : end).atZone(zone).toLocalDate();
        String bm = b.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
        if (a.equals(b)) return b.getDayOfMonth() + " " + bm;
        if (a.getMonth() == b.getMonth()) return a.getDayOfMonth() + " – " + b.getDayOfMonth() + " " + bm;
        return a.getDayOfMonth() + " " + a.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK) + " – " + b.getDayOfMonth() + " " + bm;
    }

    /** Pure: the time to a start as {days, hours, minutes} ("04", "20", "35"), none below 0. */
    static String[] countdown(long start, long now) {
        long mins = Math.max(0, (start - now) / 60_000L);
        long d = mins / (24 * 60), hh = mins / 60 % 24, mm = mins % 60;
        return new String[]{d < 10 ? "0" + d : String.valueOf(d), hh < 10 ? "0" + hh : String.valueOf(hh), mm < 10 ? "0" + mm : String.valueOf(mm)};
    }

    /** Pure: a driver's age on a day from his date of birth ("1997-09-30"); -1 when not known. */
    static int age(String born, LocalDate today) {
        try {
            LocalDate b = LocalDate.parse(born);
            int y = today.getYear() - b.getYear();
            if (today.getDayOfYear() < b.withYear(today.getYear()).getDayOfYear()) y--;
            return y >= 0 && y < 100 ? y : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** One driver's words and addresses, worked out once (his season's figures once they are read). */
    private static final class Sheet {
        String number = "", name = "", team = "", facts = "", head = "", photo, car, carBefore, logo, logoBefore, flag;
        String races, podiums;
        int colour, ground, card;
        float teamSize = 1f;
    }

    private static final String[] UNITS = {"Days", "Hrs", "Mins"};

    final class Page extends View {
        private final Map<F1.Standing, Sheet> sheets = new HashMap<>();
        private final Paint ground = new Paint(), grain = ClubLook.grain(), vignette = new Paint(Paint.DITHER_FLAG),
                glow = new Paint(Paint.DITHER_FLAG), card = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), stand = new Paint(Paint.ANTI_ALIAS_FLAG),
                carBody = new Paint(Paint.ANTI_ALIAS_FLAG), wheels = new Paint(Paint.ANTI_ALIAS_FLAG), arrow = new Paint(Paint.ANTI_ALIAS_FLAG),
                chevron = Dark.line(0xFFFFFFFF, 4f);
        private final TextPaint bigNumber = Cine.text(440f, Cine.outfit(900), 0x1FFFFFFF), teamP = Cine.text(92f, Cine.outfit(800), 0xFFFFFFFF),
                numberP = Cine.text(40f, Cine.outfit(700), 0xD9FFFFFF), nameP = Cine.text(46f, Cine.outfit(600), 0xFFFFFFFF),
                factsP = Cine.text(28f, Cine.outfit(500), 0xC7FFFFFF), headP = Cine.text(21f, Cine.outfit(600), 0xB3FFFFFF),
                labelP = Cine.text(26f, Cine.outfit(500), 0xC7FFFFFF), valueP = Cine.text(62f, Cine.outfit(700), 0xFFFFFFFF),
                gpP = Cine.text(36f, Cine.outfit(700), 0xFFFFFFFF), smallP = Cine.text(25f, Cine.outfit(500), 0xC7FFFFFF),
                countP = Cine.text(60f, Cine.outfit(700), 0xFFFFFFFF), unitP = Cine.text(22f, Cine.outfit(500), 0xB3FFFFFF);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private String gpName = "", gpShown = "", gpDates = "", gpCircuit = "";
        private final String[] count = {"", "", ""};
        private boolean started;

        Page() {
            super(tv);
            bigNumber.setTextSkewX(-0.22f);
            headP.setLetterSpacing(0.2f);
            wheels.setColor(0xFF15161A);
            arrow.setColor(0x33000000);
            if (next != null) {
                gpName = next.name == null ? "" : next.name;
                gpCircuit = next.circuit == null ? "" : next.circuit;
                gpDates = dates(next.start, next.race, ZoneId.systemDefault());
            }
            countdown();
        }

        private float x(float d) {
            return Ui.f(d);
        }

        void countdown() {
            if (next == null || next.race <= 0) return;
            long now = System.currentTimeMillis();
            String[] c = DriverScreen.countdown(next.race, now);
            System.arraycopy(c, 0, count, 0, 3);
            started = now >= next.race;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            ClubLook.vignette(vignette, w, h);
            ClubLook.glow(glow, x(640f), x(420f), x(900f));
            sheen.setShader(new LinearGradient(x(CARD_L), x(CARD_T), x(CARD_R), x(CARD_B), new int[]{0x1AFFFFFF, 0x05FFFFFF, 0x00FFFFFF, 0x14000000},
                    new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            gpShown = Ui.fit(gpName, gpP, x(470f));
            sheets.clear();
            ahead();
        }

        private Sheet sheet(F1.Standing st) {
            Sheet s = sheets.get(st);
            if (s != null) return s;
            s = new Sheet();
            s.number = st.number;
            s.name = st.name;
            s.team = st.team;
            float tw = teamP.measureText(st.team), room = x(700f);
            s.teamSize = tw > room ? room / tw : 1f;
            int age = age(st.born, LocalDate.now(ZoneId.systemDefault()));
            s.facts = st.nationality.isEmpty() ? (age > 0 ? "Age " + age : "") : age > 0 ? st.nationality + "  ·  Age " + age : st.nationality;
            s.head = st.season > 0 ? st.season + " SEASON" : "";
            s.photo = F1.photo(st, photoWidth());
            s.car = F1.car(st.teamId, st.season, carWidth());
            s.carBefore = st.season > 0 ? F1.car(st.teamId, st.season - 1, carWidth()) : null;
            String[] logos = F1.logos(st.teamId, st.season);
            s.logo = logos.length > 0 ? logos[0] : null;
            s.logoBefore = logos.length > 1 ? logos[1] : null;
            s.flag = F1.flag(st.nationality);
            String[] drawn = previewPictures.get(st);
            if (drawn != null) {
                s.photo = drawn[0];
                s.car = drawn[1];
                s.carBefore = null;
                s.logo = drawn[2];
                s.logoBefore = null;
                s.flag = drawn[3];
            }
            s.colour = F1.colour(st.teamId);
            s.ground = ClubLook.at(s.colour, 0.028, 0.78f);
            s.card = ClubLook.at(s.colour, 0.085, 0.74f);
            sheets.put(st, s);
            return s;
        }

        private int photoWidth() {
            return Math.max(200, Math.min(800, Math.round(Ui.f(600f) / 40f) * 40));
        }

        private int carWidth() {
            return Math.max(400, Math.min(1400, Math.round(Ui.f(1000f) / 40f) * 40));
        }

        /** The pictures of the drivers either side, read ahead (when the remote moves). */
        void ahead() {
            for (int i = Math.max(0, at - 1); i <= Math.min(drivers.size() - 1, at + 1); i++) {
                Sheet s = sheet(drivers.get(i));
                int pw = photoWidth();
                if (s.photo != null) Pics.prefetch(s.photo, pw, pw * 3, Pics.FIT);
                if (s.car != null) Pics.prefetch(s.car, carWidth(), carWidth() / 3, Pics.FIT);
            }
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = pos.step();
            int w = getWidth(), h = getHeight();
            float p = pos.value();
            int a = Math.max(0, Math.min(drivers.size() - 1, (int) Math.floor(p))), b = Math.min(drivers.size() - 1, a + 1);
            float t = b == a ? 0f : Math.max(0f, Math.min(1f, p - a));
            Sheet sa = drivers.isEmpty() ? null : sheet(drivers.get(a)), sb = drivers.isEmpty() ? null : sheet(drivers.get(b));
            int groundC = sa == null ? ClubLook.ground(0) : Cine.mix(sa.ground, sb.ground, t);
            int cardC = sa == null ? ClubLook.card(0) : Cine.mix(sa.card, sb.card, t);
            ground.setColor(groundC);
            c.drawRect(0, 0, w, h, ground);
            c.drawRect(0, 0, w, h, glow);
            card.setColor(cardC);
            r.set(x(CARD_L), x(CARD_T), x(CARD_R), x(CARD_B));
            c.drawRoundRect(r, x(48f), x(48f), card);
            c.drawRoundRect(r, x(48f), x(48f), sheen);
            c.drawRect(0, 0, w, h, grain);
            if (sa != null) {
                // The one further from its place first, the nearer over it.
                if (b != a && t > 0.5f) {
                    drawDriver(c, drivers.get(a), sa, a - p);
                    drawDriver(c, drivers.get(b), sb, b - p);
                } else {
                    if (b != a && t > 0f) drawDriver(c, drivers.get(b), sb, b - p);
                    drawDriver(c, drivers.get(a), sa, a - p);
                }
            }
            drawNext(c);
            c.drawRect(0, 0, w, h, vignette);
            float ay = x((CARD_T + CARD_B) / 2f);
            if (at > 0) drawArrow(c, x(CARD_L + 40f), ay, Ui.LEFT);
            if (at < drivers.size() - 1) drawArrow(c, x(CARD_R - 40f), ay, Ui.RIGHT);
            if (moving) postInvalidateOnAnimation();
        }

        private void drawArrow(Canvas c, float cx, float cy, int dir) {
            r.set(cx - x(26f), cy - x(42f), cx + x(26f), cy + x(42f));
            c.drawRoundRect(r, x(12f), x(12f), arrow);
            Ui.chevron(c, cx, cy, x(18f), dir, chevron);
        }

        /** The next Grand Prix and the countdown to it, at the card's top right (the same for every driver). */
        private void drawNext(Canvas c) {
            if (gpName.isEmpty()) return;
            float lx = x(1010f);
            headP.setAlpha(0xB3);
            c.drawText("NEXT GRAND PRIX", lx, x(282f), headP);
            c.drawText(gpShown, lx, x(330f), gpP);
            c.drawText(gpDates.isEmpty() ? gpCircuit : gpDates, lx, x(368f), smallP);
            if (!gpDates.isEmpty() && !gpCircuit.isEmpty()) c.drawText(gpCircuit, lx, x(402f), smallP);
            if (started) {
                c.drawText("Under way", x(1560f), x(330f), gpP);
                return;
            }
            for (int i = 0; i < 3; i++) {
                float cx = x(1560f + 110f * i);
                c.drawText(UNITS[i], cx - unitP.measureText(UNITS[i]) / 2f, x(282f), unitP);
                c.drawText(count[i], cx - countP.measureText(count[i]) / 2f, x(346f), countP);
            }
        }

        /** One driver's page at d (0 in place; -1 gone left, 1 coming from the right): sliding, fading. */
        private void drawDriver(Canvas c, F1.Standing st, Sheet s, float d) {
            float alpha = Math.max(0f, 1f - Math.abs(d));
            if (alpha <= 0.01f) return;
            int a = Math.round(255 * alpha);
            // His number, huge, italic and faded behind him.
            if (!s.number.isEmpty()) {
                bigNumber.setAlpha(Math.round(0x29 * alpha));
                c.drawText(s.number, x(120f + d * 300f), x(600f), bigNumber);
            }
            // The car from the side, behind him, breaking out past the card's right edge.
            drawCar(c, s, x(888f + d * 640f), x(916f), x(1000f), alpha);
            // The driver standing over the card's left half, rising above its top.
            drawFigure(c, st, s, x(650f + d * 420f), alpha);
            // His season down the left.
            float sx = x(150f + d * 220f);
            headP.setAlpha(Math.round(0xB3 * alpha));
            labelP.setAlpha(Math.round(0xC7 * alpha));
            valueP.setAlpha(a);
            if (!s.head.isEmpty()) c.drawText(s.head, sx, x(664f), headP);
            F1.Season season = seasons.get(st.driverId);
            if (season != null && s.races == null) {
                s.races = season.races >= 0 ? String.valueOf(season.races) : "";
                s.podiums = season.podiums >= 0 ? String.valueOf(season.podiums) : "";
            }
            float y = 712f;
            if (s.races != null && !s.races.isEmpty()) {
                c.drawText("Grands Prix", sx, x(y), labelP);
                c.drawText(s.races, sx, x(y + 58f), valueP);
                y += 104f;
            }
            if (!st.points.isEmpty()) {
                c.drawText("Points", sx, x(y), labelP);
                c.drawText(st.points, sx, x(y + 58f), valueP);
                y += 104f;
            }
            if (s.podiums != null && !s.podiums.isEmpty()) {
                c.drawText("Podiums", sx, x(y), labelP);
                c.drawText(s.podiums, sx, x(y + 58f), valueP);
            }
            // The right: the team's name very large with its logo, then his number, flag and name, then the facts.
            float tx = x(1010f + d * 260f);
            teamP.setAlpha(a);
            c.save();
            c.translate(tx, x(530f));
            c.scale(s.teamSize, s.teamSize);
            c.drawText(s.team, 0, 0, teamP);
            c.restore();
            Bitmap logo = picture(s.logo, s.logoBefore, Ui.px(84f), Ui.px(84f));
            if (logo != null) {
                float lx = Math.min(tx + teamP.measureText(s.team) * s.teamSize + x(26f), x(1736f + d * 260f));
                bmp.setAlpha(a);
                c.drawBitmap(logo, lx, x(500f) - logo.getHeight() / 2f, bmp);
            }
            float nx = tx;
            if (!s.number.isEmpty()) {
                numberP.setAlpha(Math.round(0xD9 * alpha));
                c.drawText(s.number, nx, x(606f), numberP);
                nx += numberP.measureText(s.number) + x(18f);
            }
            Bitmap flag = s.flag == null ? null : Pics.get(s.flag, Ui.px(56f), Ui.px(56f), Pics.FIT, redraw);
            if (flag != null) {
                bmp.setAlpha(a);
                c.drawBitmap(flag, nx - x(3f), x(592f) - flag.getHeight() / 2f, bmp);
                nx += x(58f) + x(10f);
            }
            nameP.setAlpha(a);
            c.drawText(s.name, nx, x(606f), nameP);
            if (!s.facts.isEmpty()) {
                factsP.setAlpha(Math.round(0xC7 * alpha));
                c.drawText(s.facts, tx, x(652f), factsP);
            }
        }

        /** A picture, else the one before it (last season's) when it failed; null while loading or when neither is there. */
        private Bitmap picture(String url, String before, int w, int h) {
            if (url == null) return null;
            if (!Pics.failed(url)) return Pics.get(url, w, h, Pics.FIT, redraw);
            return before == null ? null : Pics.get(before, w, h, Pics.FIT, redraw);
        }

        private void drawCar(Canvas c, Sheet s, float left, float groundY, float length, float alpha) {
            int cw = carWidth();
            Bitmap car = picture(s.car, s.carBefore, cw, cw / 3);
            if (car != null) {
                float hgt = length * car.getHeight() / Math.max(1f, car.getWidth());
                r.set(left, groundY - hgt, left + length, groundY);
                bmp.setAlpha(Math.round(255 * alpha));
                c.drawBitmap(car, null, r, bmp);
            } else if (s.car == null || Pics.failed(s.car) && (s.carBefore == null || Pics.failed(s.carBefore))) {
                carBody.setColor(ClubLook.at(s.colour, 0.22, 0.6f));
                carBody.setAlpha(Math.round(235 * alpha));
                wheels.setAlpha(Math.round(255 * alpha));
                ClubLook.car(c, left, groundY, length, carBody, wheels);
            }
        }

        private void drawFigure(Canvas c, F1.Standing st, Sheet s, float cx, float alpha) {
            int pw = photoWidth();
            Bitmap ph = s.photo == null || Pics.failed(s.photo) ? null : Pics.get(s.photo, pw, pw * 3, Pics.FIT, redraw);
            c.save();
            c.clipRect(0, 0, getWidth(), x(CARD_B));
            if (ph != null) {
                float fh = x(FIGURE_H), fw = fh * ph.getWidth() / Math.max(1f, ph.getHeight());
                r.set(cx - fw / 2f, x(64f), cx + fw / 2f, x(64f) + fh);
                bmp.setAlpha(Math.round(255 * alpha));
                c.drawBitmap(ph, null, r, bmp);
            } else if (s.photo == null || Pics.failed(s.photo)) {
                // ESPN's head and shoulders, standing on the card's foot; else his figure drawn in the team's colour.
                Bitmap head = st.headshot == null || Pics.failed(st.headshot) ? null : Pics.get(st.headshot, Ui.px(700f), Ui.px(510f), Pics.FIT, redraw);
                if (head != null) {
                    float hw = x(760f), hh = hw * head.getHeight() / Math.max(1f, head.getWidth());
                    r.set(cx - hw / 2f, x(CARD_B) - hh, cx + hw / 2f, x(CARD_B));
                    bmp.setAlpha(Math.round(255 * alpha));
                    c.drawBitmap(head, null, r, bmp);
                } else if (st.headshot == null || Pics.failed(st.headshot)) {
                    stand.setColor(ClubLook.at(s.colour, 0.2, 0.45f));
                    stand.setAlpha(Math.round(200 * alpha));
                    ClubLook.standing(c, cx, x(70f), x(FIGURE_H), stand);
                }
            }
            c.restore();
        }
    }
}
