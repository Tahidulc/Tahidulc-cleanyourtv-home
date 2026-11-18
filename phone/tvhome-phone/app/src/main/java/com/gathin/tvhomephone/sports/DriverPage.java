package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

import com.gathin.tvhomephone.home.Type;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * One F1 driver's page, as the family's picture of one has it: a big rounded card in his team's colour (the page's
 * ground the same colour, deeper, behind it), his car number huge, italic and faded behind him at the top left, and the
 * driver standing in his kit over the card's left side, rising above its top edge. On the right the next Grand Prix
 * (its dates and name) with a countdown in days, hours and minutes, his team's name very large with its logo, his
 * number, his country's flag and his full name, his nationality and age, and his team's car from the side breaking out
 * past the card's right edge. Down the left his season so far: Grands Prix, Points and Podiums, labelled, each only
 * once its source has it. Upright on a phone the driver is on top and the rest under him.
 *
 * <p>Drawn, not laid out: every place and size is worked out when the page's size or driver changes; a frame only
 * draws. The pictures (formula1.com's driver and car, else ESPN's head and shoulders, else a figure and a car drawn in
 * the team's colour) come through the app's picture cache at the size they show at.
 */
final class DriverPage extends android.view.View {
    /** Design units: the wide layout's card is about 1440 by 860, the upright one 411 across. */
    private static final float WIDE_W = 1440f, WIDE_H = 860f, UP_W = 411f, UP_MIN_H = 700f;

    private final boolean wide;
    private final float density;
    private F1.Standing st;
    private F1.Season season;
    private F1.Weekend next;
    private final String[] count = {"", "", ""};
    private boolean started;

    private final PagePic photo, car, logo, flag;
    private PageArt.Figure figure;
    private PageArt.Car drawnCar;
    private int colour, cardColour;

    private final Paint cardP = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
            bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), fade = new Paint(Paint.DITHER_FLAG);
    private final TextPaint bigNumber, teamP, numberP, nameP, factsP, kickerP, labelP, valueP, gpP, smallP, countP, unitP;
    private final RectF r = new RectF(), cardR = new RectF();

    // ---- the places, in px (worked out in place()) ----
    private float s, ox, oy;
    private float radius, numberX, numberY, driverCx, driverTop, driverBottom, clipBottom, figH;
    private float figX, kickerY, figLabelY0, figGap, figValueDy;
    private float upFigLabelY, upFigValueY, upFigCol;
    private float gpX, gpKickerY, gpNameY, gpDatesY, gpRoom, countRight, countGap, countUnitY, countDigitY;
    private float teamX, teamY, teamRoom, logoSize, nameY, factsY, carLeft, carBottom, carLength, arrowY;
    private float headW;

    // ---- the words, worked out in prepare() ----
    private String number = "", teamShown = "", nameShown = "", facts = "", kicker = "", gpName = "", gpDates = "";
    private float teamScale = 1f;
    private final List<String[]> figures = new ArrayList<>();
    private static final String[] UNITS = {"Days", "Hrs", "Mins"};

    DriverPage(Context c, boolean wide) {
        super(c);
        this.wide = wide;
        density = c.getResources().getDisplayMetrics().density;
        photo = new PagePic(this);
        car = new PagePic(this);
        logo = new PagePic(this);
        flag = new PagePic(this);
        bigNumber = text(Type.outfit(c, 900), 0x26FFFFFF);
        bigNumber.setTextSkewX(-0.24f);
        teamP = text(Type.outfit(c, 700), 0xFFFFFFFF);
        numberP = text(Type.outfit(c, 600), 0xD9FFFFFF);
        nameP = text(Type.outfit(c, 600), 0xFFFFFFFF);
        factsP = text(Type.outfit(c, 500), 0xC7FFFFFF);
        kickerP = text(Type.outfit(c, 600), 0xB3FFFFFF);
        kickerP.setLetterSpacing(0.2f);
        labelP = text(Type.outfit(c, 500), 0xC7FFFFFF);
        valueP = text(Type.outfit(c, 700), 0xFFFFFFFF);
        gpP = text(Type.outfit(c, 700), 0xFFFFFFFF);
        smallP = text(Type.outfit(c, 500), 0xC7FFFFFF);
        countP = text(Type.outfit(c, 700), 0xFFFFFFFF);
        unitP = text(Type.outfit(c, 500), 0xB3FFFFFF);
    }

    private static TextPaint text(android.graphics.Typeface face, int colour) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setColor(colour);
        return p;
    }

    /** The driver shown, his season (null until read) and the next Grand Prix (null when not known). */
    void bind(F1.Standing driver, F1.Season s, F1.Weekend weekend) {
        boolean same = driver == st;
        st = driver;
        season = s;
        next = weekend;
        colour = F1.colour(driver.teamId);
        cardColour = ClubLook.ground(colour);
        cardP.setColor(cardColour);
        figure = new PageArt.Figure(colour);
        drawnCar = new PageArt.Car(ClubLook.lighter(ClubLook.ground(colour), 0.2));
        if (!same) {
            photo.clear();
            car.clear();
            logo.clear();
            flag.clear();
        }
        prepare();
        if (!same) request();
        invalidate();
    }

    /** His season, once read. */
    void setSeason(F1.Season s) {
        season = s;
        prepare();
        invalidate();
    }

    void setNext(F1.Weekend w) {
        next = w;
        prepare();
        invalidate();
    }

    F1.Standing driver() {
        return st;
    }

    /** The countdown's three figures, and whether the race has started. No allocation. */
    void setCountdown(String[] c, boolean underWay) {
        boolean changed = underWay != started;
        for (int i = 0; i < 3; i++) {
            if (!c[i].equals(count[i])) changed = true;
            count[i] = c[i];
        }
        started = underWay;
        if (changed) invalidate();
    }

    /** Where the arrows' middle is, from the page's top (px). */
    float arrowY() {
        return arrowY;
    }

    /** The arrows' middles across (px): just inside the card's edges, or the driver's band's on a phone upright. */
    float arrowLeftX() {
        return wide ? cardR.left + u(46f) : x(30f);
    }

    float arrowRightX() {
        return wide ? cardR.right - u(46f) : x(381f);
    }

    /** The colour this page's ground is in (the team's, deep): for the page behind the cards. */
    int colour() {
        return colour;
    }

    // ---- size ---------------------------------------------------------------------------------------------------

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED ? 0 : MeasureSpec.getSize(hSpec);
        if (!wide) h = Math.max(h, Math.round(w / UP_W * UP_MIN_H));
        setMeasuredDimension(w, h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        place(w, h);
        prepare();
        if (st != null && (w != ow || h != oh)) request();
    }

    private float x(float units) {
        return (ox + units) * s;
    }

    private float y(float units) {
        return (oy + units) * s;
    }

    private float u(float units) {
        return units * s;
    }

    /** A size in design units, no smaller than minDp on the screen. */
    private float size(float units, float minDp) {
        return Math.max(units * s, minDp * density);
    }

    /** Every place and size for the page's size. */
    private void place(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (wide) placeWide(w, h);
        else placeUpright(w, h);
        sheen.setShader(new LinearGradient(cardR.left, cardR.top, cardR.right, cardR.bottom,
                new int[]{0x1FFFFFFF, 0x08FFFFFF, 0x00FFFFFF, 0x1A000000}, new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    private void placeWide(int w, int h) {
        s = Math.min(w / WIDE_W, h / WIDE_H);
        float boxW = w / s, boxH = h / s;
        float wc = Math.min(boxW, 1700f), hc = Math.min(boxH, 1440f);
        ox = (boxW - wc) / 2f;
        oy = (boxH - hc) / 2f;
        float left = 40f, right = wc - 50f, top = 150f, bottom = hc - 30f, ch = bottom - top;
        float k = Math.min(1.45f, ch / 680f);
        cardR.set(x(left), y(top), x(right), y(bottom));
        radius = u(44f);
        bigNumber.setTextSize(u(330f * k));
        numberX = x(70f);
        numberY = y(top + 250f * k);
        driverCx = x(0.335f * wc);
        driverTop = y(18f);
        clipBottom = y(bottom);
        figH = (clipBottom - driverTop) * 1.5f;
        driverBottom = clipBottom;
        headW = u(640f * Math.min(1.3f, k));
        figX = x(84f);
        kickerP.setTextSize(size(19f, 10f));
        labelP.setTextSize(size(25f, 12f));
        valueP.setTextSize(size(58f, 22f));
        kickerY = y(top + 0.47f * ch);
        figGap = u(Math.min(0.16f * ch, 150f));
        figLabelY0 = kickerY + u(50f);
        figValueDy = u(60f);
        float rx = wc - 700f, rr = wc - 150f;
        gpX = x(rx);
        gpKickerY = y(top + 80f);
        gpP.setTextSize(size(36f, 15f));
        smallP.setTextSize(size(22f, 12f));
        gpNameY = y(top + 124f);
        gpDatesY = y(top + 158f);
        countRight = x(wc - 80f);
        countGap = u(98f);
        unitP.setTextSize(size(20f, 11f));
        countP.setTextSize(size(56f, 22f));
        countUnitY = y(top + 80f);
        countDigitY = y(top + 136f);
        gpRoom = countRight - 2 * countGap - u(80f) - gpX;
        teamX = gpX;
        teamP.setTextSize(u(86f));
        teamY = y(top + 0.43f * ch);
        logoSize = u(66f);
        teamRoom = x(rr) - teamX;
        numberP.setTextSize(size(36f, 14f));
        nameP.setTextSize(size(42f, 16f));
        nameY = teamY + u(76f);
        factsP.setTextSize(size(24f, 12f));
        factsY = nameY + u(44f);
        carLength = u(690f);
        carLeft = x(wc - 700f);
        carBottom = y(bottom - 0.045f * ch);
        arrowY = y(top + 0.36f * ch);
    }

    private void placeUpright(int w, int h) {
        s = w / UP_W;
        ox = 0;
        oy = 0;
        float hh = Math.max(UP_MIN_H, h / s);
        float bottom = hh - 12f;
        float photoBottom = bottom - 366f;
        float top = Math.max(120f, photoBottom - 200f);
        cardR.set(x(14f), y(top), x(383f), y(bottom));
        radius = u(30f);
        bigNumber.setTextSize(u(200f));
        numberX = x(16f);
        numberY = y(top + 44f);
        driverCx = x(232f);
        driverTop = y(8f);
        clipBottom = y(photoBottom);
        figH = (clipBottom - driverTop) * 1.55f;
        driverBottom = clipBottom;
        headW = u(330f);
        teamX = x(28f);
        teamP.setTextSize(u(34f));
        teamY = y(bottom - 326f);
        teamRoom = x(370f) - teamX;
        logoSize = u(30f);
        numberP.setTextSize(u(20f));
        nameP.setTextSize(u(22f));
        nameY = y(bottom - 290f);
        factsP.setTextSize(u(15f));
        factsY = y(bottom - 264f);
        labelP.setTextSize(u(14f));
        valueP.setTextSize(u(32f));
        upFigLabelY = y(bottom - 230f);
        upFigValueY = y(bottom - 196f);
        upFigCol = u(122f);
        figX = x(28f);
        kickerP.setTextSize(u(11f));
        gpX = x(28f);
        gpKickerY = y(bottom - 178f);
        gpP.setTextSize(u(19f));
        gpNameY = y(bottom - 154f);
        smallP.setTextSize(u(13f));
        gpDatesY = y(bottom - 134f);
        countRight = x(372f);
        countGap = u(42f);
        unitP.setTextSize(u(11f));
        countP.setTextSize(u(26f));
        countUnitY = y(bottom - 178f);
        countDigitY = y(bottom - 148f);
        gpRoom = countRight - 2 * countGap - u(36f) - gpX;
        carLength = u(308f);
        carLeft = x(96f);
        carBottom = y(bottom - 20f);
        arrowY = (driverTop + clipBottom) / 2f;
        fade.setShader(new LinearGradient(0, clipBottom - u(70f), 0, clipBottom, ClubLook.alpha(cardColour == 0 ? ClubLook.NAVY : cardColour, 0),
                cardColour == 0 ? ClubLook.NAVY : cardColour, Shader.TileMode.CLAMP));
    }

    /** The words for the driver and the size: worked out once, not while drawing. */
    private void prepare() {
        if (st == null || s <= 0) return;
        number = st.number == null ? "" : st.number;
        float tw = teamP.measureText(st.team);
        float room = teamRoom - (logo.bitmap() != null || !logo.failed() ? logoSize + u(wide ? 24f : 10f) : 0);
        teamScale = tw > room && tw > 0 ? Math.max(0.45f, room / tw) : 1f;
        teamShown = st.team;
        float nameRoom = teamRoom - (number.isEmpty() ? 0 : numberP.measureText(number) + u(14f)) - u(wide ? 64f : 36f);
        nameShown = TextUtils.ellipsize(st.name, nameP, Math.max(1f, nameRoom), TextUtils.TruncateAt.END).toString();
        facts = DriverFacts.facts(st.nationality, DriverFacts.age(st.born, LocalDate.now(ZoneId.systemDefault())));
        kicker = st.season > 0 ? st.season + " SEASON" : "THIS SEASON";
        figures.clear();
        figures.addAll(DriverFacts.figures(st, season));
        if (next != null && next.name != null && !next.name.isEmpty()) {
            gpName = TextUtils.ellipsize(next.name, gpP, Math.max(1f, gpRoom), TextUtils.TruncateAt.END).toString();
            String d = DriverFacts.dates(next.start, next.race, ZoneId.systemDefault());
            String circuit = next.circuit == null ? "" : next.circuit;
            String line = d.isEmpty() ? circuit : circuit.isEmpty() ? d : d + "  ·  " + circuit;
            gpDates = TextUtils.ellipsize(line, smallP, Math.max(1f, gpRoom), TextUtils.TruncateAt.END).toString();
        } else {
            gpName = "";
            gpDates = "";
        }
        if (!wide) fade.setShader(new LinearGradient(0, clipBottom - u(70f), 0, clipBottom, ClubLook.alpha(cardColour, 0), cardColour,
                Shader.TileMode.CLAMP));
        // the stand-ins' places: a figure where the driver stands, a car where his would be
        float fw = figH / 2.6f;
        if (figure != null) figure.setBounds(Math.round(driverCx - fw / 2f), Math.round(driverTop), Math.round(driverCx + fw / 2f), Math.round(driverTop + figH));
        float carH = carLength / 4.3f;
        if (drawnCar != null) drawnCar.setBounds(Math.round(carLeft), Math.round(carBottom - carH), Math.round(carLeft + carLength), Math.round(carBottom));
        setContentDescription(describe());
    }

    private String describe() {
        StringBuilder b = new StringBuilder(st.name);
        if (!st.position.isEmpty()) b.append(", ").append(DriverFacts.place(st.position)).append(" in the championship");
        if (!st.team.isEmpty()) b.append(", ").append(st.team);
        if (!number.isEmpty()) b.append(", number ").append(number);
        if (!facts.isEmpty()) b.append(". ").append(facts.replace("  ·  ", ", "));
        for (String[] f : figures) b.append(". ").append(f[0]).append(' ').append(f[1]);
        if (!gpName.isEmpty()) {
            b.append(". Next: ").append(next.name);
            if (!started && !count[0].isEmpty()) b.append(" in ").append(count[0]).append(" days, ").append(count[1]).append(" hours");
        }
        return b.append('.').toString();
    }

    /** The pictures at the size they show at: the driver, his car, his team's logo and his flag. */
    private void request() {
        if (st == null || getWidth() <= 0) return;
        // formula1.com's drivers stand about three times as tall as they are wide
        int pw = Math.max(160, Math.min(800, Math.round(figH / 3.1f / 40f) * 40));
        int ph = Math.round(pw * 3.2f);
        photo.load(new String[]{F1.photo(st, pw), st.headshot}, pw, ph);
        int cw = Math.max(400, Math.min(1400, Math.round(carLength / 40f) * 40));
        car.load(new String[]{F1.car(st.teamId, st.season, cw), st.season > 0 ? F1.car(st.teamId, st.season - 1, cw) : null}, cw, cw / 2);
        int ls = Math.max(48, Math.round(logoSize));
        logo.load(F1.logos(st.teamId, st.season), ls, ls);
        String f = F1.flag(st.nationality);
        int fs = Math.max(32, Math.round(u(wide ? 48f : 26f)));
        flag.load(f == null ? null : new String[]{f}, fs, fs);
    }

    /** Lets the pictures go (the page is gone). */
    void release() {
        photo.clear();
        car.clear();
        logo.clear();
        flag.clear();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (st == null || s <= 0) return;
        c.drawRoundRect(cardR, radius, radius, cardP);
        c.drawRoundRect(cardR, radius, radius, sheen);
        if (!number.isEmpty()) c.drawText(number, numberX, numberY, bigNumber);
        drawCar(c);
        drawDriver(c);
        if (wide) drawFiguresWide(c);
        else drawFiguresUpright(c);
        drawNext(c);
        drawTeam(c);
    }

    private void drawDriver(Canvas c) {
        Bitmap b = photo.bitmap();
        c.save();
        c.clipRect(0, 0, getWidth(), clipBottom);
        if (b != null && b.getHeight() > b.getWidth() * 1.4f) {
            // formula1.com's: the driver standing, his head near the top, cut at the card's foot (or the band's)
            float fw = figH * b.getWidth() / (float) b.getHeight();
            r.set(driverCx - fw / 2f, driverTop, driverCx + fw / 2f, driverTop + figH);
            c.drawBitmap(b, null, r, bmp);
        } else if (b != null) {
            // ESPN's head and shoulders, standing on the card's foot
            float hh = headW * b.getHeight() / Math.max(1f, b.getWidth());
            r.set(driverCx - headW / 2f, driverBottom - hh, driverCx + headW / 2f, driverBottom);
            c.drawBitmap(b, null, r, bmp);
        } else if (photo.failed() && figure != null) {
            figure.draw(c);
        }
        c.restore();
        if (!wide) c.drawRect(cardR.left, clipBottom - u(70f), cardR.right, clipBottom, fade);
    }

    private void drawCar(Canvas c) {
        Bitmap b = car.bitmap();
        if (b != null) {
            float ch = carLength * b.getHeight() / Math.max(1f, b.getWidth());
            r.set(carLeft, carBottom - ch, carLeft + carLength, carBottom);
            c.drawBitmap(b, null, r, bmp);
        } else if (car.failed() && drawnCar != null) {
            drawnCar.draw(c);
        }
    }

    private void drawFiguresWide(Canvas c) {
        if (figures.isEmpty()) return;
        c.drawText(kicker, figX, kickerY, kickerP);
        for (int i = 0; i < figures.size(); i++) {
            float ly = figLabelY0 + i * figGap;
            c.drawText(figures.get(i)[0], figX, ly, labelP);
            c.drawText(figures.get(i)[1], figX, ly + figValueDy, valueP);
        }
    }

    private void drawFiguresUpright(Canvas c) {
        for (int i = 0; i < figures.size(); i++) {
            float fx = figX + i * upFigCol;
            c.drawText(figures.get(i)[0], fx, upFigLabelY, labelP);
            c.drawText(figures.get(i)[1], fx, upFigValueY, valueP);
        }
    }

    private void drawNext(Canvas c) {
        if (gpName.isEmpty()) return;
        c.drawText("NEXT GRAND PRIX", gpX, gpKickerY, kickerP);
        c.drawText(gpName, gpX, gpNameY, gpP);
        if (!gpDates.isEmpty()) c.drawText(gpDates, gpX, gpDatesY, smallP);
        if (started) {
            float w = gpP.measureText("Under way");
            c.drawText("Under way", countRight - w, countDigitY, gpP);
            return;
        }
        if (count[0].isEmpty()) return;
        for (int i = 0; i < 3; i++) {
            float cx = countRight - (2 - i) * countGap - countP.measureText("00") / 2f;
            c.drawText(UNITS[i], cx - unitP.measureText(UNITS[i]) / 2f, countUnitY, unitP);
            c.drawText(count[i], cx - countP.measureText(count[i]) / 2f, countDigitY, countP);
        }
    }

    private void drawTeam(Canvas c) {
        c.save();
        c.translate(teamX, teamY);
        c.scale(teamScale, teamScale);
        c.drawText(teamShown, 0, 0, teamP);
        c.restore();
        Bitmap lg = logo.bitmap();
        if (lg != null) {
            float lx = teamX + teamP.measureText(teamShown) * teamScale + u(wide ? 24f : 10f);
            float cy = teamY - teamP.getTextSize() * teamScale * 0.36f;
            fit(lg, lx, cy - logoSize / 2f, logoSize, logoSize);
            c.drawBitmap(lg, null, r, bmp);
        }
        float nx = teamX;
        if (!number.isEmpty()) {
            c.drawText(number, nx, nameY, numberP);
            nx += numberP.measureText(number) + u(wide ? 16f : 9f);
        }
        Bitmap fl = flag.bitmap();
        if (fl != null) {
            float fw = u(wide ? 46f : 24f), fh = u(wide ? 32f : 17f);
            fit(fl, nx, nameY - nameP.getTextSize() * 0.36f - fh / 2f, fw, fh);
            c.drawBitmap(fl, null, r, bmp);
            nx += fw + u(wide ? 16f : 9f);
        }
        c.drawText(nameShown, nx, nameY, nameP);
        if (!facts.isEmpty()) c.drawText(facts, teamX, factsY, factsP);
    }

    /** r: a picture fitted whole into the box (kept in shape, at its left and middle). */
    private void fit(Bitmap b, float left, float top, float w, float h) {
        float sc = Math.min(w / Math.max(1f, b.getWidth()), h / Math.max(1f, b.getHeight()));
        float bw = b.getWidth() * sc, bh = b.getHeight() * sc;
        r.set(left, top + (h - bh) / 2f, left + bw, top + (h + bh) / 2f);
    }
}
