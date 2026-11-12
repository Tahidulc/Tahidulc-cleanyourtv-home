package com.gathin.tvhomephone.live;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * An alert sliding out sideways from the scoreboard over the picture (ScoreBoard), in line with it and as high as it
 * (BoardSide), as broadcasters' captions come out of their score bug, in the board's own look (OverlayBug.StripLook):
 * the kind's chip, the crests and the words, the colour of the club it is about at its far end (the scorers' alone for a
 * goal, the club's for a red card). A goal's scorer stands by the board, head and shoulders, taller than the strip
 * where there is room above it. For the board's own game the board beside it already shows the clubs and the score, so
 * the alert says the rest (the scorer's crest, who and when); another game's has both clubs and the score between their
 * crests. The line-ups (when the card can't come): each club's eleven in turn, led by its crest and its name and shape
 * in its colour. Words too long for their room run along to their end. Beside a taller board (Stacked, Card) the words
 * sit in two rows. Sized from the board's height.
 */
final class BoardDrop extends BoardSide {
    private static final long TURN_MS = 320L, RUN_WAIT_MS = 1400L, RUN_END_MS = 1800L, PAGE_MS = 6000L;
    /** Words too long for their room run along at this many board heights a second. */
    private static final float RUN_SPEED = 1.7f;

    /** One page's words: the bold ones (with a crest before them, and after them for another game's score), the line. */
    private static final class Part {
        Bitmap crestA, crestB;
        String head = "", line = "";
        int headColour, lineColour;
        boolean lineBold;
    }

    private ScoreBoard.Drop drop;
    private int chipColour;
    /** The line-ups: whose eleven it shows (0 home, 1 away), and the turn to it (0 the last, 1 this one). */
    private int page;
    private float turn = 1f;
    /** How far the line has run along, in pixels. */
    private float run;
    /** The size ScoreBoard gave its strip, for the runs' lengths before it is laid out. */
    private int givenW, givenH;
    /** Where the scorer stands along the strip (from its start), and how wide: set as it is laid out. */
    private float photoX = -1, photoW;
    private ValueAnimator turning, running;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), chipText = new Paint(Paint.ANTI_ALIAS_FLAG),
            bold = new Paint(Paint.ANTI_ALIAS_FLAG), plain = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Runnable nextPage = this::nextPage, startRun = this::startRun;

    BoardDrop(Context c) {
        super(c);
        chipText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        chipText.setLetterSpacing(0.08f);
        bold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        plain.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    /** Pure: a club's colour as words on the strip: one too dark for a dark ground lightened, too light for a light one darkened. */
    static int legible(int c, boolean light) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF, lum = (r * 299 + g * 587 + b * 114) / 1000;
        if (!light && lum < 90) {
            r += (255 - r) * 11 / 20;
            g += (255 - g) * 11 / 20;
            b += (255 - b) * 11 / 20;
        } else if (light && lum > 150) {
            r = r * 2 / 5 + 0x0B * 3 / 5;
            g = g * 2 / 5 + 0x16 * 3 / 5;
            b = b * 2 / 5 + 0x40 * 3 / 5;
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Pure: how many pages an alert turns through: a substitution's OFF and ON, the line-ups' two elevens, else one. */
    static int pages(ScoreBoard.Drop d) {
        if (d == null) return 1;
        if (d.subs != null) return d.subs.pages();
        return d.lineups ? 2 : 1;
    }

    /** The alert it shows; null when none. */
    ScoreBoard.Drop drop() {
        return drop;
    }

    /** Its width for this alert with a strip h high (after place()): what its words need, up to room. Kept for its runs. */
    int widthFor(ScoreBoard.Drop d, int h, int room) {
        ScoreBoard.Drop was = drop;
        drop = d;
        float most = 0;
        for (int p = 0; p < pages(d); p++) {
            most = Math.max(most, d.subs != null ? subsLay(null, p, 0, h, 0, 255, false) : lay(null, part(p), 0, h, 0, 255, 1e6f, 0)[0]);
        }
        drop = was;
        givenH = h;
        givenW = Math.round(Math.min(room, most + ends(look, unit, h, tuck, true)));
        return givenW;
    }

    /** Out with this alert (after widthFor() and its size set): the line-ups' second eleven turning up after the first. */
    void show(ScoreBoard.Drop d) {
        onFolding();
        drop = d;
        chipColour = d.colour == ScoreBoard.PLAIN ? look.block : d.colour;
        page = 0;
        turn = 1f;
        run = 0f;
        slideOut();
        postDelayed(startRun, SLIDE_MS + RUN_WAIT_MS);
        if (pages(d) > 1) postDelayed(nextPage, SLIDE_MS + pageFor(0));
        setContentDescription(d.chip + ". " + d.title + (d.line.isEmpty() ? "" : ". " + d.line) + (d.line2.isEmpty() ? "" : ". " + d.line2));
    }

    /** How long it stays out: at least least, long enough for a long line to run to its end, each eleven its page. */
    long showFor(long least) {
        if (drop == null) return least;
        if (pages(drop) < 2) return Math.max(least, SLIDE_MS + RUN_WAIT_MS + runTime(0) + RUN_END_MS);
        return SLIDE_MS + pageFor(0) + TURN_MS + pageFor(1);
    }

    /** Previews and tests: shown at once, this far out, on this page, its line this far along. */
    void showNow(ScoreBoard.Drop d, int pg, float along, float howFar) {
        onFolding();
        drop = d;
        chipColour = d.colour == ScoreBoard.PLAIN ? look.block : d.colour;
        page = pages(d) > 1 ? Math.max(0, Math.min(1, pg)) : 0;
        turn = 1f;
        run = along;
        outNow(howFar);
    }

    @Override protected void onFolding() {
        removeCallbacks(nextPage);
        removeCallbacks(startRun);
        if (turning != null) turning.cancel();
        if (running != null) running.cancel();
        turn = 1f;
    }

    @Override protected void onGone() {
        drop = null;
    }

    private long pageFor(int p) {
        return Math.max(PAGE_MS, RUN_WAIT_MS + runTime(p) + RUN_END_MS);
    }

    /** How far page p's line has to run to show its end, in pixels (0: it fits). */
    private float runMax(int p) {
        if (drop == null || drop.subs != null || givenW <= 0) return 0;
        float room = givenW - ends(look, unit, givenH, tuck, true);
        room /= fit(part(p), givenH, room);
        float[] m = lay(null, part(p), 0, givenH, 0, 255, room, 0);
        return Math.max(0, m[1] + m[2] - room);
    }

    private long runTime(int p) {
        float m = runMax(p);
        return m <= 0 ? 0 : Math.round(m * 1000f / (RUN_SPEED * unit));
    }

    private void startRun() {
        if (!isOut() || drop == null) return;
        float to = runMax(page);
        if (to <= 0) return;
        if (running != null) running.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(0f, to);
        va.setDuration(Math.max(1, Math.round(to * 1000f / (RUN_SPEED * unit))));
        va.setInterpolator(null);
        va.addUpdateListener(a -> {
            run = (float) a.getAnimatedValue();
            invalidate();
        });
        running = va;
        va.start();
    }

    private void nextPage() {
        if (!isOut() || drop == null || pages(drop) < 2 || page >= 1) return;
        if (running != null) running.cancel();
        page = 1;
        swish();
        run = 0f;
        if (turning != null) turning.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(TURN_MS);
        va.addUpdateListener(a -> {
            turn = (float) a.getAnimatedValue();
            invalidate();
        });
        turning = va;
        va.start();
        postDelayed(startRun, TURN_MS + RUN_WAIT_MS);
    }

    // ---- what it says --------------------------------------------------------------------------------------------------

    /** Page p's words: an eleven; the board's own game's who and when; another game's score and what happened. */
    private Part part(int p) {
        ScoreBoard.Drop d = drop;
        Part o = new Part();
        o.headColour = look.ink;
        o.lineColour = look.dim;
        if (d.lineups) {
            String s = p == 0 ? d.line : d.line2;
            int colon = s.indexOf(": "), club = p == 0 ? d.homeColour : d.awayColour;
            o.crestA = p == 0 ? d.homeCrest : d.awayCrest;
            o.head = colon > 0 ? s.substring(0, colon) : "";
            o.headColour = legible(club == 0 ? look.ink : 0xFF000000 | club, look.light);
            o.line = colon > 0 ? s.substring(colon + 2) : s;
        } else if (d.own) {
            o.crestA = d.side == 1 ? d.homeCrest : d.side == 2 ? d.awayCrest : null;
            if (d.line.isEmpty()) {
                o.head = d.title;
            } else {
                o.line = d.line;
                o.lineColour = look.ink;
                o.lineBold = true;
            }
        } else {
            o.crestA = d.homeCrest;
            o.head = d.title;
            o.crestB = d.awayCrest;
            o.line = d.line;
        }
        return o;
    }

    @Override protected int stripe() {
        if (drop == null) return 0;
        if (drop.lineups) return club(page == 0 ? drop.homeColour : drop.awayColour);
        if (drop.homeColour == 0 && drop.awayColour == 0) return chipColour;
        if (drop.side == 1 || drop.side == 2) return club(drop.side == 1 ? drop.homeColour : drop.awayColour);
        return club(drop.homeColour);
    }

    @Override protected int stripeLow() {
        if (drop == null || drop.lineups || drop.side == 1 || drop.side == 2 || (drop.homeColour == 0 && drop.awayColour == 0)) return 0;
        return club(drop.awayColour);
    }

    /** A club's colour at the far end: on a light ground, one too light to show there darkened. */
    private int club(int c) {
        return look.light ? legible(0xFF000000 | c, true) : 0xFF000000 | c;
    }

    @Override protected void content(Canvas cv, int w, int h) {
        if (drop == null) return;
        float room = end - start;
        if (drop.subs != null) {
            subsK = subsFit(h, room);
            subsPages(cv, h, false);
            return;
        }
        // Its first row longer than its room (a narrow screen's edge): all of it drawn smaller, never cut or overlapping.
        fitK = fit(part(page), h, room);
        cv.save();
        if (fitK < 1f) cv.scale(fitK, fitK, start, h / 2f);
        float inRoom = room / fitK;
        if (turn < 1f && page > 0) lay(cv, part(page - 1), start, h, -h * 0.6f * turn, Math.round(255 * (1f - turn)), inRoom, 0);
        lay(cv, part(page), start, h, h * 0.6f * (1f - turn), Math.round(255 * turn), inRoom, run);
        cv.restore();
    }

    /** How much smaller it is drawn to fit its first row in its room (1: as sized). */
    private float fitK = 1f;
    /** Where the first row's parts ended, from the start, as last laid out. */
    private float firstEndAt;

    /** Pure in effect: the scale page o's first row needs to fit room at height h (1 when it fits). */
    private float fit(Part o, int h, float room) {
        lay(null, o, 0, h, 0, 255, 1e6f, 0);
        return firstEndAt > room && firstEndAt > 0 ? Math.max(0.3f, room / firstEndAt) : 1f;
    }

    /** The scorer, standing by the board on the strip's foot and up into the room above it. */
    @Override protected void over(Canvas cv, int w, int h) {
        if (drop != null && drop.subs != null) {
            subsPages(cv, h, true);
            return;
        }
        if (drop == null || photoX < 0 || drop.photo == null || drop.lineups) return;
        Bitmap b = drop.photo;
        // Drawn smaller with the rest when its first row is short of room, still standing on the strip's foot.
        if (fitK < 1f) cv.scale(fitK, fitK, start, h);
        float tall = h + Math.min(head, h * 0.55f) * 0.96f;
        float s = Math.min(photoW / b.getWidth(), tall / b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(photoX + (photoW - bw) / 2f, h - bh, photoX + (photoW + bw) / 2f, h);
        image.setAlpha(255);
        cv.drawBitmap(b, null, r, image);
    }

    private boolean twoRows(int h, Part o) {
        return h >= unit * 1.45f && !o.line.isEmpty() && (!drop.chip.isEmpty() || o.crestA != null || !o.head.isEmpty());
    }

    /** The scorer's width: as high as the strip and the room above it allow, at the picture's own shape. */
    private float photoW(int h) {
        Bitmap b = drop.photo;
        if (b == null || drop.lineups || drop.subs != null || b.getHeight() <= 0) return 0;
        float tall = h + Math.min(head, h * 0.55f) * 0.96f;
        return Math.min(tall * 0.92f, tall * b.getWidth() / (float) b.getHeight());
    }

    /**
     * Page o laid out from x0 in room (drawn when cv is not null, at height offset dy and alpha a, its line run along by
     * runBy): returns {its width, where its line starts from x0, the line's width}.
     */
    private float[] lay(Canvas cv, Part o, float x0, int h, float dy, int a, float room, float runBy) {
        boolean two = twoRows(h, o);
        float x = x0;
        // The scorer, by the board (drawn standing up out of the strip: over()).
        float pw = photoW(h);
        if (pw > 0) {
            if (cv != null) {
                photoX = x;
                photoW = pw;
            }
            x += pw + unit * 0.14f;
        } else if (cv != null) {
            photoX = -1;
        }
        float rowY = two ? h * 0.08f : 0f, rowH = two ? h * 0.42f : h;
        float rx = x, headSize = unit * (two ? 0.42f : 0.44f), cs = unit * 0.62f;
        if (!drop.chip.isEmpty()) {
            float cw = chipW();
            if (cv != null) chip(cv, rx, dy + rowY, rowH, cw, a);
            rx += cw + unit * 0.22f;
        }
        if (o.crestA != null) {
            if (cv != null) image(cv, o.crestA, rx, dy + rowY + (rowH - cs) / 2f, cs, a);
            rx += cs + unit * 0.16f;
        }
        if (!o.head.isEmpty()) {
            if (cv != null) words(cv, bold, o.head, rx, dy + rowY, rowH, headSize, o.headColour, a);
            rx += measure(bold, o.head, headSize);
        }
        if (o.crestB != null) {
            rx += unit * 0.16f;
            if (cv != null) image(cv, o.crestB, rx, dy + rowY + (rowH - cs) / 2f, cs, a);
            rx += cs;
        }
        // Where the first row's parts end (no gap left after a chip or crest that ends it).
        float firstEnd = rx > x && o.head.isEmpty() && o.crestB == null
                ? rx - (o.crestA != null ? unit * 0.16f : unit * 0.22f) : rx;
        firstEndAt = firstEnd - x0;
        if (o.line.isEmpty()) return new float[]{firstEnd - x0, firstEnd - x0, 0};
        float lx = two ? x : firstEnd + (firstEnd > x ? unit * 0.3f : 0f);
        float lineY = two ? h * 0.5f : 0f, lineH = two ? h * 0.42f : h;
        Paint p = o.lineBold ? bold : plain;
        float ls = two ? unit * 0.4f : o.lineBold ? headSize : unit * 0.38f;
        float lw = measure(p, o.line, ls);
        if (cv != null) {
            cv.save();
            cv.clipRect(lx, dy + lineY, x0 + room, dy + lineY + lineH);
            words(cv, p, o.line, lx - runBy, dy + lineY, lineH, ls, o.lineColour, a);
            cv.restore();
        }
        float total = two ? Math.max(firstEnd, lx + lw) : lx + lw;
        return new float[]{total - x0, lx - x0, lw};
    }

    private float chipW() {
        return chipW(drop.chip);
    }

    private float chipW(String text) {
        chipText.setTextSize(unit * 0.32f);
        return chipText.measureText(text) + unit * 0.3f;
    }

    /** The kind's chip: its own colour (a goal's green, a red card's red), else the board's block. */
    private void chip(Canvas cv, float x, float y, float rowH, float cw, int a) {
        chip(cv, drop.chip, chipColour, x, y, rowH, cw, a);
    }

    private void chip(Canvas cv, String text, int chipColour, float x, float y, float rowH, float cw, int a) {
        float ch = Math.min(rowH * 0.72f, unit * 0.56f), top = y + (rowH - ch) / 2f;
        fill.setColor(chipColour);
        fill.setAlpha(Math.round(a * ((chipColour >>> 24) / 255f)));
        r.set(x, top, x + cw, top + ch);
        float rad = look.apart ? ch / 2f : ch * 0.22f;
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setAlpha(255);
        chipText.setTextSize(unit * 0.32f);
        chipText.setColor((chipColour >>> 24) < 0xC0 ? look.ink : OverlayBug.ink(chipColour));
        chipText.setAlpha(a);
        chipText.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = chipText.getFontMetrics();
        cv.drawText(text, x + cw / 2f, top + ch / 2f - (fm.ascent + fm.descent) / 2f, chipText);
    }

    // ---- substitutions: OFF, then ON, each player with picture and name -------------------------------------------------

    /** How much smaller the substitutions are drawn to fit their room (1: as sized), the same for both pages. */
    private float subsK = 1f;

    private float subsFit(int h, float room) {
        float most = 0;
        for (int p = 0; p < pages(drop); p++) most = Math.max(most, subsLay(null, p, 0, h, 0, 255, false));
        return most > room && most > 0 ? Math.max(0.4f, room / most) : 1f;
    }

    /** Both pages as they turn (the last going up and away, this one coming up), the words or the pictures. */
    private void subsPages(Canvas cv, int h, boolean faces) {
        cv.save();
        if (subsK < 1f) cv.scale(subsK, subsK, start, h);
        if (turn < 1f && page > 0) subsLay(cv, page - 1, start, h, -h * 0.6f * turn, Math.round(255 * (1f - turn)), faces);
        subsLay(cv, page, start, h, h * 0.6f * (1f - turn), Math.round(255 * turn), faces);
        cv.restore();
    }

    /** Each player's picture's height: the strip's and the room above it, as the scorer's. */
    private float faceTall(int h) {
        return h + Math.min(head, h * 0.55f) * 0.96f;
    }

    /**
     * Page p of a substitution laid out from x0 (drawn when cv is not null: its words, or with faces its pictures, at
     * height offset dy and alpha a): the club's crest, OFF or ON, then each player's picture and name, then the minute.
     * Returns its width.
     */
    private float subsLay(Canvas cv, int p, float x0, int h, float dy, int a, boolean faces) {
        SubsPage.Subs s = drop.subs;
        boolean off = s.offPage(p);
        String[] names = off ? s.off : s.on;
        Bitmap[] pics = off ? s.offFaces : s.onFaces;
        boolean words = cv != null && !faces, pictures = cv != null && faces;
        float x = x0, cs = unit * 0.62f, size = unit * 0.44f, pw = faceTall(h) * 0.8f;
        Bitmap crest = drop.side == 1 ? drop.homeCrest : drop.side == 2 ? drop.awayCrest : null;
        if (crest != null) {
            if (words) image(cv, crest, x, dy + (h - cs) / 2f, cs, a);
            x += cs + unit * 0.2f;
        }
        String label = off ? "OFF" : "ON";
        float cw = chipW(label);
        if (words) chip(cv, label, off ? SubsPage.OFF_COLOUR : SubsPage.ON_COLOUR, x, dy, h, cw, a);
        x += cw + unit * 0.28f;
        for (int i = 0; i < names.length; i++) {
            if (i > 0) x += unit * 0.45f;
            if (pictures) face(cv, i < pics.length ? pics[i] : null, names[i], x, dy, pw, h, a);
            x += pw + unit * 0.12f;
            if (words) words(cv, bold, names[i], x, dy, h, size, look.ink, a);
            x += measure(bold, names[i], size);
        }
        if (!s.minute.isEmpty()) {
            String m = s.minute + "'";
            x += unit * 0.32f;
            if (words) words(cv, plain, m, x, dy, h, unit * 0.38f, look.dim, a);
            x += measure(plain, m, unit * 0.38f);
        }
        return x - x0;
    }

    /** A player's picture standing on the strip's foot; without one, their initials on a disc in the club's colour. */
    private void face(Canvas cv, Bitmap b, String name, float x, float dy, float pw, int h, int a) {
        if (b != null && b.getWidth() > 0 && b.getHeight() > 0) {
            float tall = faceTall(h), k = Math.min(pw / b.getWidth(), tall / b.getHeight());
            float bw = b.getWidth() * k, bh = b.getHeight() * k;
            r.set(x + (pw - bw) / 2f, dy + h - bh, x + (pw + bw) / 2f, dy + h);
            image.setAlpha(a);
            cv.drawBitmap(b, null, r, image);
            return;
        }
        float d = h * 0.82f, cx = x + pw / 2f, cy = dy + h - d / 2f - h * 0.06f;
        int colour = drop.subs.colour != 0 ? 0xFF000000 | drop.subs.colour : look.block;
        fill.setColor(colour);
        fill.setAlpha(a);
        cv.drawCircle(cx, cy, d / 2f, fill);
        fill.setAlpha(255);
        chipText.setTextSize(d * 0.4f);
        chipText.setColor(OverlayBug.ink(colour));
        chipText.setAlpha(a);
        chipText.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = chipText.getFontMetrics();
        cv.drawText(SubsPage.initials(name), cx, cy - (fm.ascent + fm.descent) / 2f, chipText);
    }

    private static float measure(Paint p, String s, float size) {
        p.setTextSize(size);
        return p.measureText(s == null ? "" : s);
    }

    /** Words from x along, in the middle of (y, rowH). */
    private static void words(Canvas cv, Paint p, String s, float x, float y, float rowH, float size, int colour, int a) {
        if (s == null || s.isEmpty()) return;
        p.setTextSize(size);
        p.setColor(colour);
        p.setAlpha(Math.round(a * ((colour >>> 24) / 255f)));
        p.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(s, x, y + rowH / 2f - (fm.ascent + fm.descent) / 2f, p);
    }

    /** A crest filling a square of side size at (x, y). */
    private void image(Canvas cv, Bitmap b, float x, float y, float size, int a) {
        float s = size / Math.max(b.getWidth(), b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(x + (size - bw) / 2f, y + (size - bh) / 2f, x + (size + bw) / 2f, y + (size + bh) / 2f);
        image.setAlpha(a);
        cv.drawBitmap(b, null, r, image);
    }
}
