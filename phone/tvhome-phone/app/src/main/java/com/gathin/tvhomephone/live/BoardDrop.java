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
 * sit in two rows. A milestone rides at the end of its words as a small tag: a goal's place in the game or the season
 * in gold with a star ("3rd of the season", "Hat-trick!"), a yellow card's ban with a card ("One more and he's banned";
 * red once he is). Sized from the board's height.
 */
final class BoardDrop extends BoardSide {
    private static final long TURN_MS = 320L, RUN_WAIT_MS = 1400L, RUN_END_MS = 1800L, PAGE_MS = 6000L;
    /** Words too long for their room run along at this many board heights a second. */
    private static final float RUN_SPEED = 1.7f;

    /** One page's words: the bold ones (with a crest before them, and after them for another game's score), the line. */
    private static final class Part {
        Bitmap crestA, crestB;
        /** A wide mark after the chip, as wide as it is (a breaking story's source: BBC News's, BBC Sport's); null for none. */
        Bitmap mark;
        String head = "", line = "";
        int headColour, lineColour;
        boolean lineBold;
        /** The bold words this much bigger (the minutes added's +N). */
        float headScale = 1f;
        /** A milestone's tag after the words ("" none), and its kind (TAG_*). */
        String tag = "";
        int tagKind;
        /** Three dots beating after the bold words (a VAR check under way). */
        boolean dots;
    }

    /** A milestone tag's kinds: a goal's (gold, a star), a yellow card one short of a ban, a ban (red). */
    static final int TAG_GOAL = 1, TAG_WARN = 2, TAG_BAN = 3;
    static final int GOLD = 0xFFFFC21A, CARD_YELLOW = 0xFFFFD21A, BAN_RED = 0xFFE5484D, GOLD_INK = 0xFF151A63;

    /** Each step of the minutes added counting up (+1, +2 ... +N), after the first half of its slide out. */
    static final long COUNT_STEP_MS = 150L;

    /** Pure: the number the minutes added show, since ms after it began to come out: from 1 up to n, one a step. */
    static int countUp(int n, long since) {
        if (n <= 1) return Math.max(1, n);
        if (since < 0) return n;
        return (int) Math.min(n, 1 + Math.max(0L, since - SLIDE_MS / 2) / COUNT_STEP_MS);
    }

    /** The alert out now and when it began to come out (the minutes added count up from then). */
    private ScoreBoard.Drop shownDrop;
    private long shownAt;

    private ScoreBoard.Drop drop;
    /** The alert this one turned from in place (a VAR check into its decision), and its page, while the turn goes on. */
    private ScoreBoard.Drop prevDrop;
    private int prevPage;
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
    /** The Scoreboard Lab's moments on the alerts: a red card swinging up, VAR's pulse, a substitution's players crossing over. */
    private final com.gathin.broadcast.BoardFx fx = new com.gathin.broadcast.BoardFx();
    /** When the page out now began (a substitution's players cross over from then); previews: its moment frozen (-1 none). */
    private long pageAt, momentFrozen = -1;

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

    /**
     * This alert in place of the one out (a VAR check turning into its decision), its words turning over as a page does,
     * with the swish; out as usual when none is. After widthFor() and its size set.
     */
    void turnTo(ScoreBoard.Drop d) {
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
        pageAt = shownAt;
        momentFrozen = -1;
        chipColour = d.colour == ScoreBoard.PLAIN ? look.block : d.colour;
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
        setContentDescription(d.chip + ". " + d.title + (d.line.isEmpty() ? "" : ". " + d.line));
    }

    /** Out with this alert (after widthFor() and its size set): the line-ups' second eleven turning up after the first. */
    void show(ScoreBoard.Drop d) {
        onFolding();
        prevDrop = null;
        drop = d;
        shownDrop = d;
        shownAt = android.os.SystemClock.uptimeMillis();
        momentFrozen = -1;
        // A substitution's first players cross in as the card comes to rest.
        pageAt = shownAt + SLIDE_MS / 2;
        // The Lab's own quiet sound with the swish: the red card swinging up, VAR checking, a lone page of players coming on.
        if (redCard(d)) labSound(com.gathin.broadcast.Sfx.CARD);
        else if (d.varCheck) labSound(com.gathin.broadcast.Sfx.VAR);
        else if (d.subs != null && d.subs.pages() < 2) labSound(com.gathin.broadcast.Sfx.SWAP);
        chipColour = d.colour == ScoreBoard.PLAIN ? look.block : d.colour;
        page = 0;
        turn = 1f;
        run = 0f;
        slideOut();
        postDelayed(startRun, SLIDE_MS + RUN_WAIT_MS);
        if (pages(d) > 1) postDelayed(nextPage, SLIDE_MS + pageFor(0));
        setContentDescription(d.chip + ". " + d.title + (d.line.isEmpty() ? "" : ". " + d.line) + (d.line2.isEmpty() ? "" : ". " + d.line2)
                + (d.tag == null || d.tag.isEmpty() ? "" : ". " + d.tag));
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

    /** Previews: drawn t ms into its own moment (a red card's swing, a substitution's players crossing in); -1 at rest. */
    void momentNow(long t) {
        momentFrozen = t;
        invalidate();
    }

    /** How far into its own moment the alert out now is drawn, in ms from its moment's start (previews' frozen one first). */
    private long momentT(long since) {
        return momentFrozen >= 0 ? momentFrozen : since;
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
        pageAt = android.os.SystemClock.uptimeMillis();
        swish();
        // The players crossing over (off, then on): the Lab's swap's quiet sound with the page's swish.
        if (drop.subs != null) labSound(com.gathin.broadcast.Sfx.SWAP);
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
        return partOf(drop, p);
    }

    private Part partOf(ScoreBoard.Drop d, int p) {
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
            // ADDED TIME: the minutes counting up to them as it comes out (+1, +2 ... +4), big after its chip, and no more
            // words: the chip says what they are, and the board keeps no tab of its own while this is out.
            int n = d.addedMins;
            o.head = "+" + countUp(n, d == shownDrop ? android.os.SystemClock.uptimeMillis() - shownAt : -1);
            o.headScale = 1.3f;
            return o;
        }
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
        if (!d.lineups && d.tag != null) {
            o.tag = d.tag;
            o.tagKind = d.tagKind;
        }
        if (d.alert != null && "news".equals(d.alert.kind)) o.mark = bbc(d.alert);
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
        // Its first row longer than its room (a narrow screen's edge): all of it drawn smaller, never cut or overlapping.
        fitK = fit(part(page), h, room);
        cv.save();
        if (fitK < 1f) cv.scale(fitK, fitK, start, h / 2f);
        float inRoom = room / fitK;
        // A VAR check's dots beat while it is out (a frame now and then is enough).
        if (drop.varCheck) postInvalidateDelayed(50);
        if (turn < 1f && prevDrop != null) lay(cv, partOf(prevDrop, prevPage), start, h, -h * 0.6f * turn, Math.round(255 * (1f - turn)), inRoom, 0);
        else if (turn < 1f && page > 0) lay(cv, part(page - 1), start, h, -h * 0.6f * turn, Math.round(255 * (1f - turn)), inRoom, 0);
        lay(cv, part(page), start, h, h * 0.6f * (1f - turn), Math.round(255 * turn), inRoom, run);
        cv.restore();
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
            // A red card's own card beside its chip: swinging up into place as the alert comes out, then held there.
            if (redCard(drop)) {
                float cardH = Math.min(rowH * 0.66f, unit * 0.5f);
                if (cv != null) {
                    long t = momentT(drop == shownDrop && shownAt > 0 ? android.os.SystemClock.uptimeMillis() - shownAt - SLIDE_MS / 2
                            : com.gathin.broadcast.BoardFx.RED_UP);
                    long held = Math.max(1L, Math.min(t, com.gathin.broadcast.BoardFx.RED_AWAY - 1));
                    float cx = rx + cardH * 0.35f, foot = dy + rowY + (rowH + cardH) / 2f;
                    // Faded with the rest as an alert turns over (the card has no strength of its own).
                    boolean faded = a < 250;
                    if (faded) cv.saveLayerAlpha(cx - cardH * 1.2f, foot - cardH * 1.6f, cx + cardH * 1.2f, foot + cardH * 2.2f, Math.max(0, a));
                    fx.redCard(cv, cx, foot, cardH, held);
                    if (faded) cv.restore();
                    if (momentFrozen < 0 && t < com.gathin.broadcast.BoardFx.RED_UP + 60) postInvalidateOnAnimation();
                }
                rx += cardH * 0.7f + unit * 0.26f;
            }
        }
        if (o.mark != null) {
            float mh = cs * 0.8f, mw = mh * o.mark.getWidth() / (float) Math.max(1, o.mark.getHeight());
            if (cv != null) {
                r.set(rx, dy + rowY + (rowH - mh) / 2f, rx + mw, dy + rowY + (rowH + mh) / 2f);
                image.setAlpha(a);
                cv.drawBitmap(o.mark, null, r, image);
            }
            rx += mw + unit * 0.2f;
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
        float tw = tagW(o), tg = tw > 0 ? unit * 0.24f : 0f;
        if (o.line.isEmpty()) {
            // A tag with no line: after the first row's words.
            if (tw > 0) {
                if (cv != null) tag(cv, o, firstEnd + tg, dy + rowY, rowH, a);
                firstEnd += tg + tw;
            }
            firstEndAt = firstEnd - x0;
            return new float[]{firstEnd - x0, firstEnd - x0, 0};
        }
        firstEndAt = firstEnd - x0;
        float lx = two ? x : firstEnd + (firstEnd > x ? unit * 0.3f : 0f);
        float lineY = two ? h * 0.5f : 0f, lineH = two ? h * 0.42f : h;
        Paint p = o.lineBold ? bold : plain;
        float ls = two ? unit * 0.4f : o.lineBold ? headSize : unit * 0.38f;
        float lw = measure(p, o.line, ls);
        if (cv != null) {
            cv.save();
            cv.clipRect(lx, dy + lineY, x0 + room, dy + lineY + lineH);
            words(cv, p, o.line, lx - runBy, dy + lineY, lineH, ls, o.lineColour, a);
            // The milestone's tag at the line's end, running along with it.
            if (tw > 0) tag(cv, o, lx - runBy + lw + tg, dy + lineY, lineH, a);
            cv.restore();
        }
        lw += tg + tw;
        float total = two ? Math.max(firstEnd, lx + lw) : lx + lw;
        return new float[]{total - x0, lx - x0, lw};
    }

    private float chipW() {
        return chipW(drop.chip);
    }

    // ---- a milestone's tag ---------------------------------------------------------------------------------------------

    /** A tag's width: its mark, its words and a little room at each end (0 for none). */
    private float tagW(Part o) {
        if (o.tag == null || o.tag.isEmpty()) return 0;
        return unit * (0.14f + 0.3f + 0.1f + 0.18f) + measure(bold, o.tag, unit * 0.3f);
    }

    /** A tag at x in the middle of (y, rowH): a goal's gold with a star, a warning's quiet with a yellow card, a ban's red. */
    private void tag(Canvas cv, Part o, float x, float y, float rowH, int a) {
        float th = Math.min(rowH * 0.74f, unit * 0.52f), top = y + (rowH - th) / 2f, w = tagW(o);
        int ground = o.tagKind == TAG_BAN ? BAN_RED : o.tagKind == TAG_WARN ? (look.light ? 0x1F0B1033 : 0x2EFFFFFF) : GOLD;
        int ink = o.tagKind == TAG_BAN ? 0xFFFFFFFF : o.tagKind == TAG_WARN ? look.ink : GOLD_INK;
        fill.setColor(ground);
        fill.setAlpha(Math.round(a * ((ground >>> 24) / 255f)));
        r.set(x, top, x + w, top + th);
        float rad = look.apart ? th / 2f : th * 0.28f;
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setAlpha(255);
        float is = unit * 0.3f, cx = x + unit * 0.14f + is / 2f, cy = top + th / 2f;
        if (o.tagKind == TAG_GOAL) star(cv, cx, cy, is * 0.56f, ink, a);
        else card(cv, cx, cy, is, o.tagKind == TAG_BAN, a);
        words(cv, bold, o.tag, x + unit * (0.14f + 0.1f) + is, top, th, unit * 0.3f, ink, a);
    }

    private final android.graphics.Path mark = new android.graphics.Path();

    /** A five-pointed star of radius rad round (cx, cy). */
    private void star(Canvas cv, float cx, float cy, float rad, int colour, int a) {
        mark.reset();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? rad : rad * 0.45f;
            float px = cx + (float) (Math.cos(ang) * rr), py = cy + (float) (Math.sin(ang) * rr);
            if (i == 0) mark.moveTo(px, py); else mark.lineTo(px, py);
        }
        mark.close();
        fill.setColor(colour);
        fill.setAlpha(Math.round(a * ((colour >>> 24) / 255f)));
        cv.drawPath(mark, fill);
        fill.setAlpha(255);
    }

    /** A yellow card, a little tilted, size high; a ban's with a white edge on its red ground. */
    private void card(Canvas cv, float cx, float cy, float size, boolean ban, int a) {
        float ch = size * 0.92f, cw = ch * 0.7f;
        cv.save();
        cv.rotate(12f, cx, cy);
        r.set(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f);
        fill.setColor(CARD_YELLOW);
        fill.setAlpha(a);
        cv.drawRoundRect(r, cw * 0.14f, cw * 0.14f, fill);
        if (ban) {
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Math.max(1f, size * 0.07f));
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha(a);
            cv.drawRoundRect(r, cw * 0.14f, cw * 0.14f, fill);
            fill.setStyle(Paint.Style.FILL);
        }
        fill.setAlpha(255);
        cv.restore();
    }

    private float chipW(String text) {
        chipText.setTextSize(unit * 0.32f);
        return chipText.measureText(text) + unit * 0.3f + (drop != null && drop.varCheck && text.equals(drop.chip) ? unit * 0.3f : 0f);
    }

    /** The kind's chip: its own colour (a goal's green, a red card's red), else the board's block. */
    private void chip(Canvas cv, float x, float y, float rowH, float cw, int a) {
        if (drop.varCheck) {
            // VAR checking: the Lab's dot pulsing in the chip, before its word.
            float pad = unit * 0.3f;
            chip(cv, drop.chip, chipColour, x, y, rowH, cw, a, pad);
            float[] pv = com.gathin.broadcast.BoardFx.varPulse(android.os.SystemClock.uptimeMillis());
            float rad = unit * 0.075f;
            fill.setColor((chipColour >>> 24) < 0xC0 ? look.ink : OverlayBug.ink(chipColour));
            fill.setAlpha(Math.round(a * pv[1]));
            cv.drawCircle(x + unit * 0.15f + pad * 0.5f, y + rowH / 2f, rad * pv[0], fill);
            fill.setAlpha(255);
            return;
        }
        chip(cv, drop.chip, chipColour, x, y, rowH, cw, a);
    }

    /** Pure: whether an alert is a red card's (its own card swings up beside its chip). */
    static boolean redCard(ScoreBoard.Drop d) {
        return d != null && !d.lineups && "RED CARD".equals(d.chip);
    }

    /** One of the Lab's quiet sounds with the alert (Sfx.QUIET, panned to it), behind the swish's switch. */
    private void labSound(String name) {
        if (!PlayerIntro.swishOn(getContext())) return;
        int[] at = new int[2];
        getLocationOnScreen(at);
        int sw = getResources().getDisplayMetrics().widthPixels;
        float pan = sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
        com.gathin.broadcast.Sfx.play(getContext(), name, pan, com.gathin.broadcast.Sfx.QUIET);
    }

    private void chip(Canvas cv, String text, int chipColour, float x, float y, float rowH, float cw, int a) {
        chip(cv, text, chipColour, x, y, rowH, cw, a, 0f);
    }

    /** A chip with its word moved along by lead (room for VAR's dot before it). */
    private void chip(Canvas cv, String text, int chipColour, float x, float y, float rowH, float cw, int a, float lead) {
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
        cv.drawText(text, x + (cw + lead) / 2f, top + ch / 2f - (fm.ascent + fm.descent) / 2f, chipText);
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
        // The players coming in as the Lab's swap has them, from each page's start: drawn again until they have.
        if (momentFrozen < 0 && pageAt > 0 && android.os.SystemClock.uptimeMillis() - pageAt < SWAP_IN_MS) postInvalidateOnAnimation();
        cv.save();
        if (subsK < 1f) cv.scale(subsK, subsK, start, h);
        if (turn < 1f && page > 0) subsLay(cv, page - 1, start, h, -h * 0.6f * turn, Math.round(255 * (1f - turn)), faces);
        subsLay(cv, page, start, h, h * 0.6f * (1f - turn), Math.round(255 * turn), faces);
        cv.restore();
    }

    /** How long a page's players take to cross in (the Lab's swap's first part); held there after. */
    static final long SWAP_IN_MS = Math.round(com.gathin.broadcast.BoardFx.SWAP_MS * 0.6);

    /** The swap's figures for the page out now (as it comes), or at rest (fully in) for one not coming now. */
    private float[] swapNow(boolean coming) {
        long t = !coming ? SWAP_IN_MS : momentT(pageAt > 0 ? android.os.SystemClock.uptimeMillis() - pageAt : SWAP_IN_MS);
        return com.gathin.broadcast.BoardFx.swapAt(swapT(t));
    }

    /** Pure: the time into the Lab's swap a page is drawn at, t ms after it came: up to its players fully in, then held. */
    static long swapT(long t) {
        return Math.max(0L, Math.min(t, SWAP_IN_MS));
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
        x += cw + unit * 0.2f;
        // The Lab's swap: an arrow for the way they go (red down off, green up on), popping as the page comes; the
        // players brightening in from a little to the left.
        float[] sw = swapNow(cv != null && p == page);
        float arrow = unit * 0.34f;
        if (words) com.gathin.broadcast.BoardFx.swapArrow(cv, x + arrow / 2f, dy + h / 2f, arrow * sw[com.gathin.broadcast.BoardFx.SW_IN_POP], !off, a / 255f);
        x += arrow + unit * 0.24f;
        int pa = Math.round(a * sw[com.gathin.broadcast.BoardFx.SW_IN_ALPHA]);
        float shift = sw[com.gathin.broadcast.BoardFx.SW_IN_SHIFT] * size;
        for (int i = 0; i < names.length; i++) {
            if (i > 0) x += unit * 0.45f;
            if (pictures) face(cv, i < pics.length ? pics[i] : null, names[i], x + shift, dy, pw, h, pa);
            x += pw + unit * 0.12f;
            if (words) words(cv, bold, names[i], x + shift, dy, h, size, look.ink, pa);
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

    private final java.util.Map<String, Bitmap> bbcMarks = new java.util.HashMap<>();

    /** A breaking story's BBC mark (BBC News's, BBC Sport's) by its source or its link, drawn once each; null for others. */
    private Bitmap bbc(com.gathin.tvhomephone.sports.Events.Alert a) {
        String s = com.gathin.tvhomephone.glass.BbcMark.section(a.channel);
        if (s == null) s = com.gathin.tvhomephone.glass.BbcMark.sectionOfLink(a.slug);
        if (s == null) return null;
        Bitmap b = bbcMarks.get(s);
        if (b == null) {
            b = com.gathin.tvhomephone.glass.BbcMark.px(96f, s).bitmap();
            bbcMarks.put(s, b);
        }
        return b;
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
