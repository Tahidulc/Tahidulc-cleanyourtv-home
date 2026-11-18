package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
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
 * An F1 driver's page in 07 (the approved mock-ups 23 and 24), under the shell's "‹ back" line: the page dark with his
 * team's colour glowing from its left, a big rounded card in that colour, his car number huge, italic and faded behind
 * him, and the driver standing in his kit over the card's left half, rising above its top edge (formula1.com's picture).
 * On the right the next Grand Prix (its name, dates and circuit) with a countdown in days, hours and minutes on glass
 * tiles, then his team's name very large with its logo on a glass tile, his number, his country's flag and his full
 * name, his nationality and age, and his team's car from the side breaking out past the card's edge. Down the left his
 * season so far: Grands Prix, points and podiums, under its year. Left and Right go to the driver before or after him in
 * the championship, the page sliding across and its colour turning to the next team's; Up goes to the tab bar.
 *
 * <p>Pictures: formula1.com's (the driver, the car, the logo: the family of addresses the app's team logos come from),
 * else ESPN's head and shoulders; with no picture of him at all, his team's logo large with his initials (never a drawn
 * person); with no picture of the car, a car drawn in the team's colour. His Grands Prix and podiums are one read of his
 * results this season (Jolpica), when the remote rests on him; the drivers either side are read after him.
 */
final class DriverScreen extends Screen {
    static final long SLIDE_MS = 340, REST_MS = 250;
    /** The card (design px, the mock-ups' .dv-card); how far a driver's page slides. */
    static final float CARD_L = 80f, CARD_T = 200f, CARD_R = 1840f, CARD_B = 1000f, SLIDE = 520f;
    /** formula1.com's driver as drawn (the mock-ups' .dv-fig): 400 wide from x 410, its top at 10, cut at the card's foot. */
    static final float FIGURE_X = 410f, FIGURE_W = 400f, FIGURE_TOP = 10f;

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

    // ---- 07's shell -------------------------------------------------------------------------------------------------

    @Override boolean shell() {
        return true;
    }

    @Override String backLine() {
        return "Sports · Formula 1";
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
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (tv.tabs() != null) tv.tabs().enter();
            return true;
        }
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return code == KeyEvent.KEYCODE_DPAD_DOWN;
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

    /** Pure: the weekend's days as 07 writes them: "Fri 20 – Sun 22 Nov" (across two months, "Fri 30 Oct – Sun 1 Nov"). */
    static String days(long start, long end, ZoneId zone) {
        if (end <= 0) return "";
        LocalDate b = Instant.ofEpochMilli(end).atZone(zone).toLocalDate();
        LocalDate a = Instant.ofEpochMilli(start > 0 ? start : end).atZone(zone).toLocalDate();
        String bd = b.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK), bm = b.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
        if (a.equals(b)) return bd + " " + b.getDayOfMonth() + " " + bm;
        String ad = a.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK);
        if (a.getMonth() == b.getMonth()) return ad + " " + a.getDayOfMonth() + " – " + bd + " " + b.getDayOfMonth() + " " + bm;
        return ad + " " + a.getDayOfMonth() + " " + a.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK) + " – " + bd + " "
                + b.getDayOfMonth() + " " + bm;
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
        String number = "", name = "", team = "", facts = "", head = "", initials = "", photo, car, carBefore, logo, logoBefore, flag;
        String races, podiums;
        int colour, glow, glowRing;
        float teamSize = 1f;
    }

    private static final String[] UNITS = {"Days", "Hrs", "Mins"};
    /** The card's shadow (0 40 90 black 35 %) and the car's on the ground (0 30 30 black 45 %, as a soft band). */
    static final float[] SH_CARD = {40f, 90f, 0f, 89f}, SH_CAR = {12f, 30f, 0f, 140f};

    final class Page extends View {
        private final Map<F1.Standing, Sheet> sheets = new HashMap<>();
        private final Paint ground = new Paint(Paint.DITHER_FLAG), grain = ClubLook.grain(), card = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                cardLight = new Paint(Paint.ANTI_ALIAS_FLAG), cardRing = new Paint(Paint.ANTI_ALIAS_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
                carBody = new Paint(Paint.ANTI_ALIAS_FLAG), wheels = new Paint(Paint.ANTI_ALIAS_FLAG), flagP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final TextPaint bigNumber = Cine.text(520f, Cine.condensed(), 0x24FFFFFF), teamP = Type.paint(116f, Type.EXTRABOLD, 0xFFFFFFFF),
                numberP = Cine.text(48f, Cine.condensed(), 0xFFFFFFFF), nameP = Type.paint(40f, Type.BOLD, 0xFFFFFFFF),
                factsP = Type.paint(24f, Type.MEDIUM, 0xD9FFFFFF), headP = Type.paint(16f, Type.BOLD, 0xCCFFFFFF),
                labelP = Type.paint(19f, Type.SEMIBOLD, 0xCCFFFFFF), valueP = Type.paint(52f, Type.EXTRABOLD, 0xFFFFFFFF),
                gpP = Type.paint(36f, Type.BOLD, 0xFFFFFFFF), smallP = Type.paint(21f, Type.REGULAR, 0xD9FFFFFF),
                countP = Type.clock(50f, Type.EXTRABOLD, 0xFFFFFFFF), unitP = Type.paint(16f, Type.SEMIBOLD, 0xCCFFFFFF),
                initialsP = Type.paint(120f, Type.EXTRABOLD, 0xFFFFFFFF);
        private final RectF r = new RectF(), cardR = new RectF();
        private final Matrix fm = new Matrix();
        private final Runnable redraw = this::invalidate;
        private String gpName = "", gpShown = "", gpDays = "", gpCircuit = "";
        private final String[] count = {"", "", ""};
        private boolean started;
        /** The ground's and the card's shaders for the two teams on screen (made when they change, not each frame). */
        private int groundFor = 1, cardFor = 1;
        private Bitmap flagFor;
        private BitmapShader flagShader;

        Page() {
            super(tv);
            bigNumber.setTextSkewX(-0.22f);
            bigNumber.setLetterSpacing(-0.02f);
            numberP.setTextSkewX(-0.22f);
            teamP.setLetterSpacing(-0.035f);
            headP.setLetterSpacing(0.14f);
            valueP.setLetterSpacing(-0.02f);
            wheels.setColor(0xFF15161A);
            cardLight.setStyle(Paint.Style.STROKE);
            cardRing.setStyle(Paint.Style.STROKE);
            cardRing.setColor(0x2EFFFFFF);
            initialsP.setLetterSpacing(0.02f);
            Shadows.prepare(SH_CARD, 52f);
            Shadows.prepare(SH_CAR, 20f);
            Shadows.prepare(Look07.SH_GLASS, 28f);
            if (next != null) {
                gpName = next.name == null ? "" : next.name;
                gpCircuit = next.circuit == null ? "" : next.circuit;
                gpDays = days(next.start, next.race, ZoneId.systemDefault());
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
            gpShown = Ui.fit(gpName, gpP, x(470f));
            cardLight.setShader(new LinearGradient(0, x(CARD_T), 0, x(CARD_T + 48f), 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            groundFor = cardFor = 1;
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
            s.initials = F1.initials(st.name);
            float tw = teamP.measureText(st.team), room = x(1780f - 930f - 24f - 104f - 8f);
            s.teamSize = tw > room ? room / tw : 1f;
            int age = age(st.born, LocalDate.now(ZoneId.systemDefault()));
            s.facts = st.nationality.isEmpty() ? (age > 0 ? "Age " + age : "") : age > 0 ? st.nationality + " · Age " + age : st.nationality;
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
            s.glow = Palette.alpha(s.colour, 0x88);
            s.glowRing = Palette.alpha(s.colour, 0x33);
            sheets.put(st, s);
            return s;
        }

        private int photoWidth() {
            return Math.max(200, Math.min(800, Math.round(Ui.f(FIGURE_W) / 40f) * 40));
        }

        private int carWidth() {
            return Math.max(400, Math.min(1400, Math.round(Ui.f(1120f) / 40f) * 40));
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

        /**
         * The page's ground for a colour (07's: radial-gradient(1400px 900px at 30% 40%) from the colour at 53 % through
         * 20 % at 45 % to near black) and the card's (linear-gradient(135deg) from the colour at 40 % through 13 % at 55 %
         * to white at 6 %), remade only when the colour changes (a slide's frames mix two: made for the mix).
         */
        private void shaders(int colour) {
            int w = getWidth(), h = getHeight();
            if (colour != groundFor) {
                groundFor = colour;
                RadialGradient g = new RadialGradient(0, 0, x(1400f), new int[]{Palette.over(0xFF05070C, Palette.alpha(colour, 0x88)),
                        Palette.over(0xFF05070C, Palette.alpha(colour, 0x33)), 0xFF05070C}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
                Matrix m = new Matrix();
                m.setScale(1f, 900f / 1400f);
                m.postTranslate(w * 0.30f, h * 0.40f);
                g.setLocalMatrix(m);
                ground.setShader(g);
            }
            if (colour != cardFor) {
                cardFor = colour;
                float l = x(CARD_L), t = x(CARD_T), cw = x(CARD_R - CARD_L), ch = x(CARD_B - CARD_T);
                // 135deg: from the top left corner's side to the bottom right's, along the diagonal's normal.
                float len = (float) (Math.abs(cw * Math.sin(Math.toRadians(135))) + Math.abs(ch * Math.cos(Math.toRadians(135))));
                float cx = l + cw / 2f, cy = t + ch / 2f, ux = (float) Math.sin(Math.toRadians(135)) * len / 2f,
                        uy = (float) -Math.cos(Math.toRadians(135)) * len / 2f;
                card.setShader(new LinearGradient(cx - ux, cy - uy, cx + ux, cy + uy, new int[]{Palette.alpha(colour, 0x66),
                        Palette.alpha(colour, 0x22), 0x0FFFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            }
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = pos.step();
            int w = getWidth(), h = getHeight();
            float p = pos.value();
            int a = Math.max(0, Math.min(drivers.size() - 1, (int) Math.floor(p))), b = Math.min(drivers.size() - 1, a + 1);
            float t = b == a ? 0f : Math.max(0f, Math.min(1f, p - a));
            Sheet sa = drivers.isEmpty() ? null : sheet(drivers.get(a)), sb = drivers.isEmpty() ? null : sheet(drivers.get(b));
            // The colour mixed in steps of a tenth, so a slide makes ten shaders at most, not one a frame.
            int colour = sa == null ? 0xFFE10600 : t <= 0f ? sa.colour : Theme.mix(sa.colour, sb.colour, Math.round(t * 10f) / 10f);
            shaders(colour);
            c.drawColor(0xFF05070C);
            c.drawRect(0, 0, w, h, ground);
            c.drawRect(0, 0, w, h, grain);
            // The card: the team's colour falling away across it, its edge lit, a deep shadow.
            cardR.set(x(CARD_L), x(CARD_T), x(CARD_R), x(CARD_B));
            float rad = x(52f);
            Shadows.draw(c, cardR, rad, SH_CARD, 1f);
            c.drawRoundRect(cardR, rad, rad, card);
            float sw = Math.max(1f, x(1.5f));
            cardLight.setStrokeWidth(sw);
            cardRing.setStrokeWidth(Math.max(1f, x(1f)));
            r.set(cardR);
            r.inset(sw / 2f, sw / 2f);
            c.drawRoundRect(r, rad, rad, cardLight);
            c.drawRoundRect(r, rad, rad, cardRing);
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
            float ay = x(598f);
            if (at > 0) arrow(c, x(54f), ay, Icons07.LEFT);
            if (at < drivers.size() - 1) arrow(c, w - x(54f), ay, Icons07.RIGHT);
            if (moving) postInvalidateOnAnimation();
        }

        private void arrow(Canvas c, float cx, float cy, int icon) {
            float rd = x(32f);
            r.set(cx - rd, cy - rd, cx + rd, cy + rd);
            Frost.glass(c, r, rd, 1f);
            Icons07.draw(c, icon, cx, cy, x(30f), 0xFFFFFFFF);
        }

        /** The next Grand Prix and the countdown to it, at the card's top right (the same for every driver). */
        private void drawNext(Canvas c) {
            if (gpName.isEmpty()) return;
            float lx = x(930f);
            c.drawText("NEXT GRAND PRIX", lx, Type.centred(headP, x(262f)), headP);
            c.drawText(gpShown, lx, x(314f), gpP);
            float y = x(351f);
            if (!gpDays.isEmpty()) {
                c.drawText(gpDays, lx, y, smallP);
                y += x(30.5f);
            }
            if (!gpCircuit.isEmpty()) c.drawText(Ui.fit(gpCircuit, smallP, x(470f)), lx, y, smallP);
            if (started) {
                c.drawText("Under way", x(1780f) - gpP.measureText("Under way"), x(314f), gpP);
                return;
            }
            for (int i = 0; i < 3; i++) {
                float left = x(1416f + 126f * i);
                r.set(left, x(252f), left + x(112f), x(364f));
                Frost.glass(c, r, x(28f), 1f);
                float cx = r.centerX();
                c.drawText(UNITS[i], cx - unitP.measureText(UNITS[i]) / 2f, Type.centred(unitP, x(279f)), unitP);
                c.drawText(count[i], cx - countP.measureText(count[i]) / 2f, x(340f), countP);
            }
        }

        /** One driver's page at d (0 in place; -1 gone left, 1 coming from the right): sliding, fading. */
        private void drawDriver(Canvas c, F1.Standing st, Sheet s, float d) {
            float alpha = Math.max(0f, 1f - Math.abs(d));
            if (alpha <= 0.01f) return;
            // His number, huge, italic and faded behind him.
            if (!s.number.isEmpty()) {
                bigNumber.setAlpha(Math.round(0x24 * alpha));
                c.drawText(s.number, x(150f + d * 300f), x(636f), bigNumber);
            }
            // The car from the side, breaking out past the card's right edge; the driver over the card's left half.
            drawCar(c, s, x(900f + d * 640f), x(1040f), x(1120f), alpha);
            drawFigure(c, st, s, x(d * 420f), alpha);
            // The words go in the first half of a slide and come in the second, so two drivers' never lie over each other.
            float words = Math.max(0f, 1f - 2f * Math.abs(d));
            if (words <= 0.01f) return;
            int wa = Math.round(255 * words);
            // His season down the left.
            float sx = x(140f + d * 220f);
            headP.setAlpha(Math.round(0xCC * words));
            labelP.setAlpha(Math.round(0xCC * words));
            valueP.setAlpha(wa);
            F1.Season season = seasons.get(st.driverId);
            if (season != null && s.races == null) {
                s.races = season.races >= 0 ? String.valueOf(season.races) : "";
                s.podiums = season.podiums >= 0 ? String.valueOf(season.podiums) : "";
            }
            boolean races = s.races != null && !s.races.isEmpty(), points = !st.points.isEmpty(), podiums = s.podiums != null && !s.podiums.isEmpty();
            int n = (races ? 1 : 0) + (points ? 1 : 0) + (podiums ? 1 : 0);
            // Laid out from the foot up, as the mock-ups' block sits 54 px over the card's foot.
            float y = 946f - n * 77.6f - Math.max(0, n - 1) * 12f;
            if (!s.head.isEmpty()) c.drawText(s.head, sx, Type.centred(headP, x(y - 33.4f + 9.7f)), headP);
            if (races) y = figure(c, sx, y, "Grands Prix", s.races);
            if (points) y = figure(c, sx, y, "Points", st.points);
            if (podiums) figure(c, sx, y, "Podiums", s.podiums);
            // The right: the team's name very large with its logo, then his number, flag and name, then the facts.
            float tx = x(930f + d * 260f);
            teamP.setAlpha(wa);
            c.save();
            c.translate(tx, x(555f));
            c.scale(s.teamSize, s.teamSize);
            c.drawText(s.team, 0, 0, teamP);
            c.restore();
            float tileL = tx + (teamP.measureText(s.team) - teamP.getLetterSpacing() * teamP.getTextSize()) * s.teamSize + x(24f);
            r.set(tileL, x(460f), tileL + x(104f), x(564f));
            Frost.glass(c, r, x(30f), words);
            Bitmap logo = picture(s.logo, s.logoBefore, Ui.px(75f), Ui.px(66f));
            if (logo != null) {
                bmp.setAlpha(wa);
                c.drawBitmap(logo, r.centerX() - logo.getWidth() / 2f, r.centerY() - logo.getHeight() / 2f, bmp);
            }
            float nx = tx, cy = x(622f);
            if (!s.number.isEmpty()) {
                numberP.setAlpha(wa);
                c.drawText(s.number, nx, Type.centred(numberP, cy) + x(2f), numberP);
                nx += numberP.measureText(s.number) + x(16f);
            }
            Bitmap flag = s.flag == null ? null : Pics.get(s.flag, Ui.px(54f), Ui.px(36f), Pics.CROP, redraw);
            if (flag != null) {
                flag(c, flag, nx, cy, wa);
                nx += x(54f) + x(16f);
            }
            nameP.setAlpha(wa);
            c.drawText(s.name, nx, Type.centred(nameP, cy), nameP);
            if (!s.facts.isEmpty()) {
                factsP.setAlpha(Math.round(0xD9 * words));
                c.drawText(s.facts, tx, Type.centred(factsP, x(675f)), factsP);
            }
        }

        /** One of the season's figures from y (design px): its label and its value; the next one's y. */
        private float figure(Canvas c, float sx, float y, String label, String value) {
            c.drawText(label, sx, Type.centred(labelP, x(y + 11.5f)), labelP);
            c.drawText(value, sx, x(y + 23f + 46f), valueP);
            return y + 77.6f + 12f;
        }

        /** His country's flag, 54 by 36 with its corners rounded (5), centred on cy. */
        private void flag(Canvas c, Bitmap flag, float left, float cy, int a) {
            if (flag != flagFor) {
                flagFor = flag;
                flagShader = new BitmapShader(flag, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            r.set(left, cy - x(18f), left + x(54f), cy + x(18f));
            fm.setScale(r.width() / flag.getWidth(), r.height() / flag.getHeight());
            fm.postTranslate(r.left, r.top);
            flagShader.setLocalMatrix(fm);
            flagP.setShader(flagShader);
            flagP.setAlpha(a);
            c.drawRoundRect(r, x(5f), x(5f), flagP);
        }

        /** A picture, else the one before it (last season's) when it failed; null while loading or when neither is there. */
        private Bitmap picture(String url, String before, int w, int h) {
            if (url == null) return null;
            if (!Pics.failed(url)) return Pics.get(url, w, h, Pics.FIT, redraw);
            return before == null ? null : Pics.get(before, w, h, Pics.FIT, redraw);
        }

        private void drawCar(Canvas c, Sheet s, float left, float foot, float length, float alpha) {
            int cw = carWidth();
            Bitmap car = picture(s.car, s.carBefore, cw, cw / 3);
            if (car != null) {
                float hgt = length * car.getHeight() / Math.max(1f, car.getWidth());
                r.set(left, foot - hgt, left + length, foot);
                // Its shadow on the ground (the mock-ups' drop-shadow 0 30 30, 45 %): a soft dark band under its wheels.
                r.set(left + length * 0.04f, foot - hgt * 0.22f, left + length * 0.96f, foot - hgt * 0.04f);
                Shadows.draw(c, r, r.height() / 2f, SH_CAR, alpha);
                r.set(left, foot - hgt, left + length, foot);
                bmp.setAlpha(Math.round(255 * alpha));
                c.drawBitmap(car, null, r, bmp);
            } else if (s.car == null || Pics.failed(s.car) && (s.carBefore == null || Pics.failed(s.carBefore))) {
                // No picture of the car: one drawn in the team's colour (a car, never a person).
                carBody.setColor(ClubLook.at(s.colour, 0.16, 0.6f));
                carBody.setAlpha(Math.round(200 * alpha));
                wheels.setAlpha(Math.round(255 * alpha));
                ClubLook.car(c, left, foot - x(40f), length * 0.9f, carBody, wheels);
            }
        }

        private void drawFigure(Canvas c, F1.Standing st, Sheet s, float dx, float alpha) {
            int pw = photoWidth();
            Bitmap ph = s.photo == null || Pics.failed(s.photo) ? null : Pics.get(s.photo, pw, pw * 3, Pics.FIT, redraw);
            c.save();
            c.clipRect(0, 0, getWidth(), x(CARD_B));
            if (ph != null) {
                float fw = x(FIGURE_W), fh = fw * ph.getHeight() / Math.max(1f, ph.getWidth());
                r.set(x(FIGURE_X) + dx, x(FIGURE_TOP), x(FIGURE_X) + dx + fw, x(FIGURE_TOP) + fh);
                bmp.setAlpha(Math.round(255 * alpha));
                c.drawBitmap(ph, null, r, bmp);
            } else if (s.photo == null || Pics.failed(s.photo)) {
                // ESPN's head and shoulders, standing on the card's foot; with no picture of him at all, his team's logo
                // large and his initials: never a figure drawn in his place.
                Bitmap head = st.headshot == null || Pics.failed(st.headshot) ? null : Pics.get(st.headshot, Ui.px(620f), Ui.px(452f), Pics.FIT, redraw);
                float cx = x(FIGURE_X + FIGURE_W / 2f) + dx;
                if (head != null) {
                    float hw = x(620f), hh = hw * head.getHeight() / Math.max(1f, head.getWidth());
                    r.set(cx - hw / 2f, x(CARD_B) - hh, cx + hw / 2f, x(CARD_B));
                    bmp.setAlpha(Math.round(255 * alpha));
                    c.drawBitmap(head, null, r, bmp);
                } else if (st.headshot == null || Pics.failed(st.headshot)) {
                    Bitmap logo = picture(s.logo, s.logoBefore, Ui.px(260f), Ui.px(220f));
                    if (logo != null) {
                        bmp.setAlpha(Math.round(230 * alpha));
                        c.drawBitmap(logo, cx - logo.getWidth() / 2f, x(520f) - logo.getHeight() / 2f, bmp);
                    }
                    if (!s.initials.isEmpty()) {
                        initialsP.setAlpha(Math.round(220 * alpha));
                        c.drawText(s.initials, cx - initialsP.measureText(s.initials) / 2f, x(800f), initialsP);
                    }
                }
            }
            c.restore();
        }
    }

}
