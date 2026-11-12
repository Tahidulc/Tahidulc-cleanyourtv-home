package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * An alert sliding out sideways from the scoreboard over the channel (BoardCover), in line with it and as high as it
 * (BoardSide), as broadcasters' captions come out of their score bug, in the board's own look (BoardBug's StripLook):
 * the kind's chip, the crests and the words, the colour of the club it is about at its far end (the scorers' alone for
 * a goal, the club's for a red card), and as the cards over the apps have it, the player's picture (head and
 * shoulders, standing on its foot and rising above its top where there is room) by the board for a goal, a red card or
 * a penalty. For the board's own game the
 * board beside it already shows the clubs and the score, so the alert says the rest (the scorer's crest, who and
 * when); another game's has both clubs and the score between their crests. The line-ups: each club's eleven in turn,
 * led by its crest and its name and shape in its colour. Words too long for their room run along to their end. Beside a
 * taller board (Stacked, Card) the words sit in two rows. Sized from the board's height, so it reads from the sofa at
 * any size the board is.
 */
final class BoardDrop extends BoardSide {
    /** One alert as it comes out: its chip, title, line (and the away side's eleven), and the game's clubs it is about. */
    static final class Drop {
        String chip = "", title = "", line = "", line2 = "";
        /** The kind's colour (PLAIN: none of its own, the board's block). */
        int colour = PLAIN;
        /** The game's clubs' colours (0 when it is no game's), and the side it is about: 1 home, 2 away, 0 both or neither. */
        int homeColour, awayColour, side;
        /** Both starting elevens, the home side's in line and the away side's in line2, each led by its club and shape. */
        boolean lineups;
        /** The board's own game: the board beside it already shows the clubs and the score. */
        boolean own;
        /** The game it is about (null: none), what Today's alerts and the log call it. */
        String gameId, what = "";
        /** A goal's "game|score": it is captioned once, from its card or from the scores (null: not a goal). */
        String key;
        /** The player's picture (a cut-out, head and shoulders), and both clubs' crests (for a dark ground); each may be null. */
        Bitmap photo, homeCrest, awayCrest;
        /**
         * Pages turned one after another ({bold words, line}), each with its own picture (photos, by page; null for
         * none): the ratings' three best, the weather, the predictions' table. Null for one page as before.
         */
        String[][] pages;
        Bitmap[] photos;
        /** A substitution (or several at once): its OFF page, then its ON page, each player with picture and name. */
        SubsPage.Subs subs;
        /** The minutes added (an ADDED TIME alert): a big +N counting up to them as it comes out, MINUTES ADDED by it. */
        int addedMins;
        /**
         * A player's milestone after the line, as a small tag in its colour (Milestones): a goal's place in his season
         * (gold, with a star), a yellow card's ban near (with a little card); null for none.
         */
        String tag;
        int tagColour = Milestones.GOLD;
        /** The tag's mark: STAR, CARD or none (0). */
        int tagMark;
        /** VAR explained: a check under way (its title, three dots beating beside it), out until its decision. */
        boolean varCheck;
        /** VAR explained: the decision's WHY, folding down under it (null for any other alert). */
        VarExplained.Why why;
    }

    /** A tag's marks: a star (a goal's milestone), a little yellow card (a ban near). */
    static final int STAR = 1, CARD = 2;

    /** Each step of the minutes added counting up (+1, +2 ... +N), after the first half of its slide out. */
    static final long COUNT_STEP_MS = 150L;

    /** Pure: the number the minutes added show, since ms after it began to come out: from 1 up to n, one a step. */
    static int countUp(int n, long since) {
        if (n <= 1) return Math.max(1, n);
        if (since < 0) return n;
        return (int) Math.min(n, 1 + Math.max(0L, since - SLIDE_MS / 2) / COUNT_STEP_MS);
    }

    /** Pure: how many pages an alert turns through (the line-ups' two, its own pages, else one). */
    static int pageCount(Drop d) {
        if (d == null) return 1;
        if (d.subs != null) return d.subs.pages();
        if (d.lineups) return 2;
        return d.pages != null && d.pages.length > 0 ? d.pages.length : 1;
    }

    /** The colour of the kinds with none of their own (half time, kick-off, the line-ups): the board's block instead. */
    static final int PLAIN = 0xFFDDE3FF;
    private static final long TURN_MS = 320L, RUN_WAIT_MS = 1400L, RUN_END_MS = 1800L, PAGE_MS = 6000L;
    /** Words too long for their room run along at this many board heights a second. */
    private static final float RUN_SPEED = 1.7f;

    /** One page's words: the bold ones (with a crest before them, and after them for another game's score), the line. */
    private static final class Part {
        Bitmap crestA, crestB;
        String head = "", line = "";
        int headColour, lineColour;
        boolean lineBold;
        /** The bold words this much bigger (the minutes added's +N). */
        float headScale = 1f;
        /** The milestone's tag after the line (null: none), its colour and mark. */
        String tag;
        int tagColour, tagMark;
        /** Three dots beating after the bold words (a VAR check under way). */
        boolean dots;
    }

    /** The alert out now and when it began to come out (the minutes added count up from then). */
    private Drop shownDrop;
    private long shownAt;

    private Drop drop;
    /** The alert this one turned from in place (a VAR check into its decision), and its page, while the turn goes on. */
    private Drop prevDrop;
    private int prevPage;

    /** Whether the line-ups are out beside the board now (Back folds them away). */
    boolean showingLineups() {
        return isOut() && drop != null && drop.lineups;
    }
    private int chipColour;
    /** The line-ups: whose eleven it shows (0 home, 1 away), and the turn to it (0 the last, 1 this one). */
    private int page;
    private float turn = 1f;
    /** How far the line has run along, in pixels. */
    private float run;
    /** The size BoardCover gave it, for the runs' lengths before it is laid out. */
    private int givenW, givenH;
    private ValueAnimator turning, running;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), chipText = new Paint(Paint.ANTI_ALIAS_FLAG),
            bold = new Paint(Paint.ANTI_ALIAS_FLAG), plain = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Runnable nextPage = this::nextPage, startRun = this::startRun;

    BoardDrop(Context c) {
        super(c);
        chipText.setTypeface(ScoreCard.outfit(c, 800));
        chipText.setLetterSpacing(0.08f);
        bold.setTypeface(ScoreCard.outfit(c, 700));
        plain.setTypeface(ScoreCard.outfit(c, 500));
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

    /** Its width for this alert at height h (after place()): what its words need, up to room. Kept for its runs. */
    int widthFor(Drop d, int h, int room) {
        Drop was = drop;
        drop = d;
        float most = 0;
        for (int p = 0; p < pageCount(d); p++) {
            most = Math.max(most, d.subs != null ? subsLay(null, p, 0, h, 0, 255, false) : lay(null, part(p), 0, h, 0, 255, 1e6f, 0)[0]);
        }
        drop = was;
        givenH = h;
        givenW = Math.round(Math.min(room, most + ends(look, unit, h, tuck, true)));
        return givenW;
    }

    /**
     * This alert in place of the one out (a VAR check turning into its decision), its words turning over as a page does,
     * with the swish; out as usual when none is. After widthFor() and its size set.
     */
    void turnTo(Drop d) {
        if (!isOut() || drop == null) {
            show(d);
            return;
        }
        onFolding();
        prevDrop = drop;
        prevPage = page;
        drop = d;
        shownDrop = d;
        shownAt = android.os.SystemClock.uptimeMillis();
        chipColour = d.colour == PLAIN ? look.block : d.colour;
        page = 0;
        run = 0f;
        swish();
        turn = 0f;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(TURN_MS);
        va.addUpdateListener(a -> {
            turn = (float) a.getAnimatedValue();
            if (turn >= 1f) prevDrop = null;
            invalidate();
        });
        turning = va;
        va.start();
        postDelayed(startRun, TURN_MS + RUN_WAIT_MS);
    }

    /** The alert out now (null when none is). */
    Drop showing() {
        return isOut() ? drop : null;
    }

    /** Out with this alert (after widthFor() and its size set): the line-ups' second eleven turning up after the first. */
    void show(Drop d) {
        onFolding();
        prevDrop = null;
        drop = d;
        shownDrop = d;
        shownAt = android.os.SystemClock.uptimeMillis();
        chipColour = d.colour == PLAIN ? look.block : d.colour;
        page = 0;
        turn = 1f;
        run = 0f;
        slideOut();
        postDelayed(startRun, SLIDE_MS + RUN_WAIT_MS);
        if (pageCount(d) > 1) postDelayed(nextPage, SLIDE_MS + pageFor(0));
    }

    /** How long it stays out: at least least, long enough for a long line to run to its end, each eleven its page. */
    long showFor(long least) {
        if (drop == null) return least;
        int n = pageCount(drop);
        if (n == 1) return Math.max(least, SLIDE_MS + RUN_WAIT_MS + runTime(0) + RUN_END_MS);
        long all = SLIDE_MS;
        for (int p = 0; p < n; p++) all += pageFor(p) + (p > 0 ? TURN_MS : 0);
        return all;
    }

    /** Previews: shown at once, this far out, on this page, its line this far along. */
    void showNow(Drop d, int pg, float along, float howFar) {
        onFolding();
        drop = d;
        chipColour = d.colour == PLAIN ? look.block : d.colour;
        page = Math.max(0, Math.min(pageCount(d) - 1, pg));
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
        prevDrop = null;
    }

    private long pageFor(int p) {
        return Math.max(PAGE_MS, RUN_WAIT_MS + runTime(p) + RUN_END_MS);
    }

    /** How far page p's line has to run to show its end, in pixels (0: it fits). */
    private float runMax(int p) {
        if (drop == null || drop.subs != null || givenW <= 0) return 0;
        float room = givenW - ends(look, unit, givenH, tuck, true);
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
        if (!isOut() || drop == null || page >= pageCount(drop) - 1) return;
        if (running != null) running.cancel();
        page++;
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
        if (page < pageCount(drop) - 1) postDelayed(nextPage, TURN_MS + pageFor(page));
    }

    // ---- what it says --------------------------------------------------------------------------------------------------

    /** Page p's words: an eleven; the board's own game's who and when; another game's score and what happened. */
    private Part part(int p) {
        return partOf(drop, p);
    }

    private Part partOf(Drop d, int p) {
        Part o = new Part();
        o.headColour = look.ink;
        o.lineColour = look.dim;
        if (d.varCheck || d.why != null) {
            // VAR explained: what is checked or decided, big; what and who, or whether the call stood, small.
            o.head = d.title;
            o.line = d.line;
            o.dots = d.varCheck;
            return o;
        }
        if (d.addedMins > 0) {
            // ADDED TIME: the minutes counting up to them as it comes out (+1, +2 ... +4), big; MINUTES ADDED, small.
            int n = d.addedMins;
            o.head = "+" + countUp(n, d == shownDrop ? android.os.SystemClock.uptimeMillis() - shownAt : -1);
            o.headScale = 1.3f;
            o.line = n == 1 ? "MINUTE ADDED" : "MINUTES ADDED";
            o.lineColour = look.dim;
            return o;
        }
        if (!d.lineups && d.pages != null && d.pages.length > 0) {
            String[] pg = d.pages[Math.max(0, Math.min(d.pages.length - 1, p))];
            o.head = pg.length > 0 && pg[0] != null ? pg[0] : "";
            o.line = pg.length > 1 && pg[1] != null ? pg[1] : "";
            o.lineColour = look.ink;
            o.crestA = d.own ? null : d.homeCrest;
            o.crestB = d.own ? null : d.awayCrest;
            return o;
        }
        if (d.lineups) {
            String s = p == 0 ? d.line : d.line2;
            int colon = s.indexOf(": "), club = p == 0 ? d.homeColour : d.awayColour;
            o.crestA = p == 0 ? d.homeCrest : d.awayCrest;
            o.head = colon > 0 ? s.substring(0, colon) : "";
            o.headColour = legible(club == 0 ? look.ink : club, look.light);
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
        if (d.tag != null && !d.tag.isEmpty() && !o.line.isEmpty()) {
            o.tag = d.tag;
            o.tagColour = d.tagColour;
            o.tagMark = d.tagMark;
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
        // The minutes added still counting up: drawn again until they reach theirs.
        if (drop.addedMins > 0 && drop == shownDrop && countUp(drop.addedMins, android.os.SystemClock.uptimeMillis() - shownAt) < drop.addedMins) {
            postInvalidateOnAnimation();
        }
        // A VAR check's dots beat while it is out (a frame now and then is enough).
        if (drop.varCheck) postInvalidateDelayed(50);
        // One page at a time, never two over each other (BoardStats.turnParts).
        float[] o = BoardStats.turnParts(turn);
        if (o[1] > 0f && prevDrop != null) lay(cv, partOf(prevDrop, prevPage), start, h, -h * 0.5f * o[0], Math.round(255 * o[1]), room, 0);
        else if (o[1] > 0f && page > 0) lay(cv, part(page - 1), start, h, -h * 0.5f * o[0], Math.round(255 * o[1]), room, 0);
        if (o[3] > 0f) lay(cv, part(page), start, h, h * 0.5f * o[2], Math.round(255 * o[3]), room, run);
    }

    /** Pure: a VAR check's three dots' brightness t ms on (each 0.3 to 1, one after another, softly). */
    static float[] dots(long t) {
        float[] a = new float[3];
        for (int i = 0; i < 3; i++) {
            double ph = ((t % 1200L) / 1200.0 - i * 0.18) * 2 * Math.PI;
            a[i] = (float) (0.3 + 0.7 * (0.5 + 0.5 * Math.cos(ph)));
        }
        return a;
    }

    private boolean twoRows(int h, Part o) {
        return h >= unit * 1.45f && !o.line.isEmpty() && (!drop.chip.isEmpty() || o.crestA != null || !o.head.isEmpty());
    }

    /** The player's picture's height: the strip's, and as far above it as there is room for, up to a little over half. */
    private float photoH(int h) {
        return h + Math.min(head, h * 0.6f);
    }

    private float photoW(int h) {
        if (drop.lineups || drop.subs != null) return 0;
        float ph = photoH(h), w = 0;
        // Room for the widest picture its pages have, so the words stay where they are as the pages turn.
        Bitmap[] all = drop.photos != null ? drop.photos : new Bitmap[]{drop.photo};
        for (Bitmap b : all) {
            if (b == null || b.getHeight() <= 0) continue;
            w = Math.max(w, Math.min(ph * 0.82f, ph * b.getWidth() / (float) b.getHeight()));
        }
        return w;
    }

    /** The picture standing by the board on this page: its page's own, else the alert's. */
    private Bitmap photoNow() {
        if (drop.photos != null) return page < drop.photos.length ? drop.photos[page] : null;
        return drop.photo;
    }

    /** The player, head and shoulders, standing on the strip's foot by the board and rising above its top. */
    @Override protected void over(Canvas cv, int w, int h) {
        if (drop != null && drop.subs != null) {
            subsPages(cv, h, true);
            return;
        }
        if (drop == null || turn < 1f) return;
        float pw = photoW(h);
        Bitmap now = photoNow();
        if (pw > 0 && now != null) photo(cv, now, start, 0, pw, h, 255);
    }

    /**
     * Page o laid out from x0 in room (drawn when cv is not null, at height offset dy and alpha a, its line run along by
     * runBy): returns {its width, where its line starts from x0, the line's width}.
     */
    private float[] lay(Canvas cv, Part o, float x0, int h, float dy, int a, float room, float runBy) {
        boolean two = twoRows(h, o);
        float x = x0;
        // Room for the player, head and shoulders, by the board (drawn over it all: over()).
        float pw = photoW(h);
        if (pw > 0) x += pw + unit * 0.14f;
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
            float hs = headSize * o.headScale;
            if (cv != null) words(cv, bold, o.head, rx, dy + rowY, rowH, hs, o.headColour, a);
            // A number's width as its widest (the minutes added count up through narrower ones).
            rx += o.headScale > 1f ? Math.max(measure(bold, o.head, hs), measure(bold, o.head.replaceAll("[0-9]", "8"), hs)) : measure(bold, o.head, hs);
        }
        if (o.dots) {
            // Three dots beating softly after the words: the check still going on.
            float dr = unit * 0.07f, gap = unit * 0.2f;
            rx += unit * 0.18f;
            if (cv != null) {
                float[] b = dots(android.os.SystemClock.uptimeMillis());
                for (int i = 0; i < 3; i++) {
                    fill.setColor(chipColour);
                    fill.setAlpha(Math.round(a * b[i]));
                    cv.drawCircle(rx + dr + i * gap, dy + rowY + rowH / 2f, dr, fill);
                }
                fill.setAlpha(255);
            }
            rx += 2 * gap + 2 * dr;
        }
        if (o.crestB != null) {
            rx += unit * 0.16f;
            if (cv != null) image(cv, o.crestB, rx, dy + rowY + (rowH - cs) / 2f, cs, a);
            rx += cs;
        }
        // Where the first row's parts end (no gap left after a chip or crest that ends it).
        float firstEnd = rx > x && o.head.isEmpty() && o.crestB == null
                ? rx - (o.crestA != null ? unit * 0.16f : unit * 0.22f) : rx;
        if (o.line.isEmpty()) return new float[]{firstEnd - x0, firstEnd - x0, 0};
        float lx = two ? x : firstEnd + (firstEnd > x ? unit * 0.3f : 0f);
        float lineY = two ? h * 0.5f : 0f, lineH = two ? h * 0.42f : h;
        Paint p = o.lineBold ? bold : plain;
        float ls = two ? unit * 0.4f : o.lineBold ? headSize : unit * 0.38f;
        float textW = measure(p, o.line, ls), tw = tagW(o, lineH);
        // The milestone's tag runs along with the line, just after it.
        float lw = textW + (tw > 0 ? unit * 0.22f + tw : 0f);
        if (cv != null) {
            cv.save();
            cv.clipRect(lx, dy + lineY, x0 + room, dy + lineY + lineH);
            words(cv, p, o.line, lx - runBy, dy + lineY, lineH, ls, o.lineColour, a);
            if (tw > 0) tag(cv, o, lx - runBy + textW + unit * 0.22f, dy + lineY, lineH, tw, a);
            cv.restore();
        }
        float total = two ? Math.max(firstEnd, lx + lw) : lx + lw;
        return new float[]{total - x0, lx - x0, lw};
    }

    private float chipW() {
        return chipW(drop.chip);
    }

    /** The tag's height in a row rowH high: a little under a chip's. */
    private float tagH(float rowH) {
        return Math.min(rowH * 0.66f, unit * 0.5f);
    }

    /** The milestone's tag's width (0: none): its mark, then its words. */
    private float tagW(Part o, float rowH) {
        if (o.tag == null || o.tag.isEmpty()) return 0;
        float th = tagH(rowH);
        chipText.setTextSize(unit * 0.28f);
        return unit * 0.16f + (o.tagMark != 0 ? th * 0.62f + unit * 0.1f : 0f) + chipText.measureText(o.tag) + unit * 0.16f;
    }

    /** The milestone's tag at x: a small pill in its colour, its mark (a star, a little card), its words. */
    private void tag(Canvas cv, Part o, float x, float y, float rowH, float tw, int a) {
        float th = tagH(rowH), top = y + (rowH - th) / 2f;
        fill.setColor(o.tagColour);
        fill.setAlpha(Math.round(a * ((o.tagColour >>> 24) / 255f)));
        r.set(x, top, x + tw, top + th);
        float rad = look.apart ? th / 2f : th * 0.26f;
        cv.drawRoundRect(r, rad, rad, fill);
        int ink = BoardBug.ink(o.tagColour);
        float tx = x + unit * 0.16f, ms = th * 0.62f;
        if (o.tagMark == STAR) {
            fill.setColor(ink);
            fill.setAlpha(a);
            cv.drawPath(star(tx + ms / 2f, top + th / 2f, ms / 2f), fill);
            tx += ms + unit * 0.1f;
        } else if (o.tagMark == CARD) {
            float cw = ms * 0.66f, ch = ms;
            r.set(tx + (ms - cw) / 2f, top + (th - ch) / 2f, tx + (ms + cw) / 2f, top + (th + ch) / 2f);
            cv.save();
            cv.rotate(12f, r.centerX(), r.centerY());
            fill.setColor(Milestones.YELLOW);
            fill.setAlpha(a);
            cv.drawRoundRect(r, cw * 0.14f, cw * 0.14f, fill);
            fill.setColor(0xFF0B1033);
            fill.setAlpha(a / 3);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Math.max(1f, cw * 0.1f));
            cv.drawRoundRect(r, cw * 0.14f, cw * 0.14f, fill);
            fill.setStyle(Paint.Style.FILL);
            cv.restore();
            tx += ms + unit * 0.1f;
        }
        fill.setAlpha(255);
        chipText.setTextSize(unit * 0.28f);
        chipText.setColor(ink);
        chipText.setAlpha(a);
        chipText.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = chipText.getFontMetrics();
        cv.drawText(o.tag, tx, top + th / 2f - (fm.ascent + fm.descent) / 2f, chipText);
    }

    private final android.graphics.Path starPath = new android.graphics.Path();

    /** A five-pointed star of radius rad about (cx, cy). */
    private android.graphics.Path star(float cx, float cy, float rad) {
        starPath.reset();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? rad : rad * 0.45f;
            float px = cx + (float) Math.cos(ang) * rr, py = cy + (float) Math.sin(ang) * rr;
            if (i == 0) starPath.moveTo(px, py); else starPath.lineTo(px, py);
        }
        starPath.close();
        return starPath;
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
        chipText.setColor((chipColour >>> 24) < 0xC0 ? look.ink : BoardBug.ink(chipColour));
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
        for (int p = 0; p < pageCount(drop); p++) most = Math.max(most, subsLay(null, p, 0, h, 0, 255, false));
        return most > room && most > 0 ? Math.max(0.45f, room / most) : 1f;
    }

    /** Both pages as they turn (the last going up and away, this one coming up), the words or the pictures. */
    private void subsPages(Canvas cv, int h, boolean faces) {
        cv.save();
        if (subsK < 1f) cv.scale(subsK, subsK, start, h);
        float[] o = BoardStats.turnParts(turn);
        if (o[1] > 0f && page > 0) subsLay(cv, page - 1, start, h, -h * 0.5f * o[0], Math.round(255 * o[1]), faces);
        if (o[3] > 0f) subsLay(cv, page, start, h, h * 0.5f * o[2], Math.round(255 * o[3]), faces);
        cv.restore();
    }

    /** The club's picture's width for each player: as tall as the board's pictures stand, a little narrower. */
    private float faceW(int h) {
        return photoH(h) * 0.78f;
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
        float x = x0, cs = unit * 0.62f, size = unit * 0.44f, pw = faceW(h);
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
            photo(cv, b, x, dy, pw, h, a);
            return;
        }
        float d = h * 0.82f, cx = x + pw / 2f, cy = dy + h - d / 2f - h * 0.06f;
        int colour = drop.subs.colour != 0 ? 0xFF000000 | drop.subs.colour : look.block;
        fill.setColor(colour);
        fill.setAlpha(a);
        cv.drawCircle(cx, cy, d / 2f, fill);
        fill.setAlpha(255);
        String in = SubsPage.initials(name);
        chipText.setTextSize(d * 0.4f);
        chipText.setColor(BoardBug.ink(colour));
        chipText.setAlpha(a);
        chipText.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = chipText.getFontMetrics();
        cv.drawText(in, cx, cy - (fm.ascent + fm.descent) / 2f, chipText);
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

    /** The player's picture pw wide, standing on the strip's foot, as tall as photoH. */
    private void photo(Canvas cv, Bitmap b, float x, float dy, float pw, int h, int a) {
        float s = Math.min(pw / b.getWidth(), photoH(h) * 0.98f / b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(x + (pw - bw) / 2f, dy + h - bh, x + (pw + bw) / 2f, dy + h);
        image.setAlpha(a);
        cv.drawBitmap(b, null, r, image);
    }
}
