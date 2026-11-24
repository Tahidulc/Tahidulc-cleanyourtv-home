package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Shelves;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Editorial Home, Movies and TV Shows (Settings › Appearance › Home style), laid out as the family's other film app
 * lays them out, in 07's look, made for the remote (design px of a 1920 x 1080 screen): one row in focus at a time, its
 * title at the top left under the tab bar; whichever title the remote is on grows from its poster into a big wide card
 * (its backdrop, its TMDB logo or its name in big lettering, how far it was watched and its episode on Continue
 * Watching), the rest of the row posters bleeding off the right, the row sliding so the remote's card stays at the left;
 * under the row its score, year, genre and length (or its episode), then three lines of its story; the next row under it,
 * dimmed. Moving along, the card left shrinks back to a poster as the next one grows, its picture cross-fading from
 * poster to backdrop (Lite: the width alone, no cross-fade). Down goes to the next row, the page rising (past the last,
 * back to the first); Up to the one before. Movies and TV Shows have a big carousel above the rows (1500 x 580, the next
 * one peeking in): its picture, what it is, its logo or name, its score, year, genre, age and length, its story and View
 * Details. Over the page's own blurred picture (Ground). Only what is in sight is drawn; nothing is made while drawing
 * but a card's words as it comes into focus.
 */
final class Editorial extends View {
    interface Host {
        /** The remote moved: to a carousel card (row null) or a row's card. */
        void focused(Row row, Card card);

        /** OK on a card (row null: the carousel's). */
        void chosen(Row row, Card card);

        /** OK held on a card. */
        void held(Row row, Card card);

        /** The remote ran off the top (Ui.UP); true when the host took it. */
        boolean edge(int dir);

        /** The remote is near the end of a row whose source has more (a TMDB row's next page). */
        void more(Row row);
    }

    /** Where the remote is: the carousel, the rows. */
    static final int HERO = 0, ROWS = 1;

    /** A card: what it stands for, its pictures, its words (made as it is set, never while drawing). */
    static final class Card {
        final Shelves.Pick pick;
        /** Its poster and its wide picture (the backdrop). */
        final String poster, wide;
        /** Its name, and as cut for a card with no picture. */
        String name = "", shortName = "";
        /** The line under the row: its score ("74%"), year, genre, age, length ("1h 49m") or episode ("S2:E4"). */
        String score = "", year = "", genre = "", age = "", length = "";
        /** Its story, TMDB's logo ("" for its name in lettering), its episode's chip on Continue Watching. */
        String story = "", logo = "", episode = "";
        float progress = -1f;
        /** The carousel's: what it is ("FILM"), whether it is on My List, its page's details and TMDB's extras. */
        String kicker = "";
        boolean onList;
        Catalog.Details details;
        Tmdb.Extras extras;

        Card(Shelves.Pick pick, String poster, String wide) {
            this.pick = pick;
            this.poster = poster == null ? "" : poster;
            this.wide = wide == null || wide.isEmpty() ? this.poster : wide;
        }

        Catalog.Title title() {
            return pick.title;
        }
    }

    /** A row: its id (kept with the remote's place), title and cards. */
    static final class Row {
        final String id, title;
        final List<Card> cards;
        final int total;
        boolean more;
        int focus;
        final Glide x = new Glide(Look07.SCROLL_MS);
        /** Made from this shelf (the page keeps a row while its shelf is the same). */
        Object from;

        Row(String id, String title, List<Card> cards, int total) {
            this.id = id;
            this.title = title;
            this.cards = cards == null ? Collections.emptyList() : cards;
            this.total = total;
        }
    }

    // ---- places (design px) ------------------------------------------------------------------------------------------
    static final float LEFT = 90f, ROW_Y = 150f, HEAD = 52f, CARD_H = 440f, WIDE_W = 760f, POSTER_W = 280f, GAP = 16f,
            BLOCK = 700f, INFO = 46f, STORY = 76f, STORY_W = 840f, CARD_R = 18f;
    static final float HERO_T = 150f, HERO_W = 1500f, HERO_H = 580f, HERO_GAP = 30f, HERO_R = 40f, DOTS_Y = 762f, FIRST_ROW = 806f;
    /** The next row's pictures: dimmed to this much. */
    static final int DIM = 107;
    /** How long the page takes to rise to a row, the carousel to slide, a card to grow, the words to come in after. */
    static final long DOWN_MS = 380, HERO_MS = 420, GROW_MS = 300, WORDS_DELAY = 110, WORDS_MS = 340;

    private final List<Card> hero = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int heroAt, row, area = ROWS;
    private final Glide y = new Glide(DOWN_MS), heroX = new Glide(HERO_MS);
    /** The card growing in the remote's row (0 to 100 of the way to wide), and the one shrinking back (100 to 0). */
    private final Glide grow = new Glide(GROW_MS, 100f), shrink = new Glide(GROW_MS, 100f);
    private Card growing, shrinking;
    private Host host;
    private boolean active = true;
    private final Ground ground;
    private final Lift07 heroLift = new Lift07(), cardLift = new Lift07();
    private final Shaders shaders = new Shaders();

    // The words of the remote's card (its row's line and story, or the carousel's), laid out when it changes.
    private final Hero07.Mark heroMark = new Hero07.Mark(68f);
    private final Hero07.Mark[] marks = {new Hero07.Mark(46f), new Hero07.Mark(46f), new Hero07.Mark(46f)};
    private final Card[] marked = new Card[3];
    private int markNext;
    private StaticLayout story, heroStory;
    private Card storyFor, heroFor;
    private long wordsAt = -100_000L;
    private Object wordsKey;
    private String note = "", noteWhy = "";

    private final TextPaint headP, quietHead, lineP, scoreP, storyP, heroStoryP, kickP, nameP, chipP, peekP, noteP, whyP;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint(Paint.ANTI_ALIAS_FLAG),
            shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), foot = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
            cardFoot = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), thumbP = new Paint(Paint.ANTI_ALIAS_FLAG),
            chipFill = new Paint(Paint.ANTI_ALIAS_FLAG), frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path thumb = new Path();
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;

    Editorial(Context c) {
        super(c);
        Cine.init(c);
        ground = new Ground(this);
        headP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        quietHead = Type.paint(Type.H3, Type.BOLD, Look07.L3);
        lineP = Type.paint(26f, Type.MEDIUM, Look07.L1);
        scoreP = Type.paint(24f, Type.SEMIBOLD, Look07.GREEN);
        storyP = Type.paint(24f, Type.REGULAR, Look07.L2);
        heroStoryP = Type.paint(24f, Type.REGULAR, Look07.L2);
        kickP = Type.label(Type.LABEL, Look07.L2);
        nameP = Type.paint(26f, Type.BOLD, Look07.L1);
        nameP.setShadowLayer(Ui.f(10f), 0, Ui.f(2f), 0x99000000);
        chipP = Type.paint(17f, Type.BOLD, Look07.L1);
        peekP = Type.paint(30f, Type.BOLD, Look07.L1);
        peekP.setShadowLayer(Ui.f(14f), 0, Ui.f(2f), 0x99000000);
        noteP = Type.paint(Type.H2, Type.BOLD, Look07.L1);
        whyP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2);
        edge.setStyle(Paint.Style.STROKE);
        dim.setColor(0x59000000);
        frame.setColor(Look07.CARD_FRAME);
        chipFill.setColor(0xB3000000);
        thumbP.setColor(Look07.GREEN);
        // The carousel card's words' side: deep on the left, clear by 64 % of its width (over a unit width), and its foot.
        shade.setShader(new LinearGradient(0, 0, 1, 0, new int[]{0xF208080E, 0xD908080E, 0x6608080E, 0x0008080E},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        foot.setShader(new LinearGradient(0, 0, 0, 1, new int[]{0x00000000, 0x00000000, 0xA6000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        // A wide card's foot under its logo.
        cardFoot.setShader(new LinearGradient(0, 0, 0, 1, new int[]{0x00000000, 0x00000000, 0xB3000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        // The score's thumb, 26 px: its cuff and its hand.
        float u = Ui.f(1f);
        thumb.addRoundRect(new RectF(0, 11 * u, 6 * u, 26 * u), 1.5f * u, 1.5f * u, Path.Direction.CW);
        thumb.moveTo(8 * u, 11 * u);
        thumb.lineTo(13 * u, 2 * u);
        thumb.quadTo(16.5f * u, 1 * u, 16.5f * u, 5 * u);
        thumb.lineTo(15.5f * u, 9.5f * u);
        thumb.lineTo(23.5f * u, 9.5f * u);
        thumb.quadTo(26.5f * u, 10 * u, 25.5f * u, 13.5f * u);
        thumb.lineTo(23 * u, 24 * u);
        thumb.quadTo(22.5f * u, 26 * u, 20 * u, 26 * u);
        thumb.lineTo(8 * u, 26 * u);
        thumb.close();
        Shadows.prepare(Look07.SH_HERO, HERO_R);
        Shadows.prepare(Look07.SH_BUTTON, 35f);
        Shadows.prepare(Look07.SH_GLASS, 13f);
        Shadows.prepare(Look07.SH_CARD, CARD_R);
        Shadows.prepare(Look07.SH_CARD_UP, CARD_R);
        Pills.prepare();
    }

    void setHost(Host h) {
        host = h;
    }

    /** Whether the remote is in the page (false: on the tab bar above it). */
    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    // ---- what it shows ----------------------------------------------------------------------------------------------

    /**
     * The carousel's cards and the rows. The remote stays where it was: on the same carousel card (by its title), on the
     * row with the same id and its card; a row that comes in above it moves the page with it, so nothing jumps.
     */
    void set(List<Card> carousel, List<Row> list) {
        Object heroWas = heroAt < hero.size() ? key(hero.get(heroAt)) : null;
        String rowWas = row < rows.size() ? rows.get(row).id : null;
        float atWas = row < rows.size() ? top(row) : 0f;
        for (Row nw : list) {
            for (Row old : rows) {
                if (old == nw) break;
                if (old.id.equals(nw.id)) {
                    nw.focus = Math.min(focusIn(nw, old), Math.max(0, nw.cards.size() - 1));
                    break;
                }
            }
        }
        hero.clear();
        hero.addAll(carousel);
        for (Card c : hero) if (c.shortName.isEmpty()) c.shortName = Ui.fit(c.name, peekP, Ui.f(250f));
        heroAt = 0;
        for (int i = 0; i < hero.size() && heroWas != null; i++) if (key(hero.get(i)).equals(heroWas)) heroAt = i;
        heroX.jump(-heroAt * (HERO_W + HERO_GAP));
        List<Row> before = new ArrayList<>(rows);
        rows.clear();
        rows.addAll(list);
        int was = row;
        row = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row rw = rows.get(i);
            if (!before.contains(rw)) {
                // A new row in place; one that replaces a row (its next page read) slides on from where that one was.
                Row old = null;
                for (Row o : before) if (o.id.equals(rw.id)) old = o;
                if (old == null) rw.x.jump(target(rw));
                else {
                    rw.x.jump(old.x.value());
                    rw.x.to(target(rw));
                }
            }
            if (rw.id.equals(rowWas)) row = i;
            for (Card c : rw.cards) if (c.shortName.isEmpty()) c.shortName = Ui.fit(c.name, nameP, Ui.f(POSTER_W - 32f));
        }
        if (rowWas == null || row >= rows.size() || !rows.get(row).id.equals(rowWas)) row = Math.min(was, Math.max(0, rows.size() - 1));
        if (area == ROWS && rows.isEmpty() && !hero.isEmpty()) area = HERO;
        if (area == HERO && hero.isEmpty()) area = ROWS;
        if (area == ROWS && rowWas != null && row < rows.size()) y.shift(top(row) - atWas);
        else if (area == ROWS && row < rows.size()) y.jump(rise(row));
        if (area == HERO) y.jump(0f);
        // The remote's card wide at once (a row made again keeps its card grown).
        Card on = focusCard();
        if (on != growing) {
            growing = on;
            shrinking = null;
            grow.jump(100f);
            shrink.jump(0f);
        }
        if (hero.isEmpty() && rows.isEmpty()) ground.set("");
        told(false);
    }

    private static int focusIn(Row nw, Row old) {
        if (old.focus < old.cards.size()) {
            Object k = key(old.cards.get(old.focus));
            for (int i = 0; i < nw.cards.size(); i++) if (key(nw.cards.get(i)).equals(k)) return i;
        }
        return old.focus;
    }

    /** What a card is kept by: its title. */
    private static Object key(Card c) {
        return c.pick.title == null ? c : c.pick.title.key;
    }

    /** Nothing to show: its words (the playlists still being read, none yet). */
    void note(String head, String why) {
        note = head == null ? "" : head;
        noteWhy = why == null ? "" : why;
        invalidate();
    }

    /** A card's words again (its details, its logo, My List read since). */
    void refresh(Card c) {
        if (c == storyFor) storyFor = null;
        if (c == heroFor) heroFor = null;
        for (int i = 0; i < marked.length; i++) if (marked[i] == c) marked[i] = null;
        invalidate();
    }

    boolean isEmpty() {
        return hero.isEmpty() && rows.isEmpty();
    }

    int area() {
        return area;
    }

    Row current() {
        return area == ROWS && row < rows.size() ? rows.get(row) : null;
    }

    /** The remote's row's card (whether the remote is on the rows or not). */
    private Card focusCard() {
        if (row >= rows.size()) return null;
        Row rw = rows.get(row);
        return rw.focus < rw.cards.size() ? rw.cards.get(rw.focus) : null;
    }

    /** The card the remote is on (the carousel's, or a row's). */
    Card card() {
        if (area == HERO) return heroAt < hero.size() ? hero.get(heroAt) : null;
        return current() == null ? null : focusCard();
    }

    /** A carousel card a few along from the remote's (to read its picture ahead), or null. */
    Card heroNear(int along) {
        int i = heroAt + along;
        return i < 0 || i >= hero.size() ? null : hero.get(i);
    }

    /** A card of the remote's row (or of the first, from the carousel) a few places along, or null. */
    Card near(int along) {
        if (row >= rows.size()) return null;
        Row rw = rows.get(row);
        int i = rw.focus + along;
        return i < 0 || i >= rw.cards.size() ? null : rw.cards.get(i);
    }

    List<Row> rows() {
        return rows;
    }

    /** The page's picture: the carousel's or the remote's card's. */
    void ground(String url) {
        ground.set(url);
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    /** Where a row's top is on the page (design px, before it rises): the first under the carousel (or the tab bar). */
    private float top(int i) {
        return (hero.isEmpty() ? ROW_Y : FIRST_ROW) + i * BLOCK;
    }

    /** Where a row slides so its card in focus is at the left (the cards before it are posters). */
    private static float target(Row rw) {
        return -Math.max(0, rw.focus) * (POSTER_W + GAP);
    }

    /** How far the page rises for the row the remote is on: that row to the top, under the tab bar. */
    private float rise(int i) {
        return i < 0 || rows.isEmpty() ? 0f : top(i) - ROW_Y;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        if (area == HERO) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (heroAt > 0) toHero(heroAt - 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (heroAt < hero.size() - 1) toHero(heroAt + 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    return host != null && host.edge(Ui.UP);
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!rows.isEmpty()) {
                        area = ROWS;
                        toRow(0);
                    }
                    return true;
                default:
                    return false;
            }
        }
        Row rw = current();
        if (rw == null) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (rw.focus > 0) move(rw, rw.focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (rw.focus < rw.cards.size() - 1) move(rw, rw.focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) toRow(row - 1);
                else if (!hero.isEmpty()) toTop();
                else return host != null && host.edge(Ui.UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Past the last row, back to the first.
                toRow(row < rows.size() - 1 ? row + 1 : 0);
                return true;
            default:
                return false;
        }
    }

    private void toHero(int i) {
        heroAt = i;
        heroX.to(-heroAt * (HERO_W + HERO_GAP));
        told(true);
    }

    /** Up to the carousel, the page back down to its top. */
    private void toTop() {
        area = HERO;
        y.to(0f);
        told(true);
    }

    private void move(Row rw, int to) {
        Card from = rw.focus < rw.cards.size() ? rw.cards.get(rw.focus) : null;
        rw.focus = to;
        rw.x.to(target(rw));
        // The card left shrinks back to a poster as the next one grows (from wherever each was).
        Card next = rw.cards.get(to);
        float fromNow = from == growing ? grow.value() : from == shrinking ? shrink.value() : 100f;
        float nextNow = next == shrinking ? shrink.value() : next == growing ? grow.value() : 0f;
        shrinking = from;
        growing = next;
        shrink.jump(fromNow);
        shrink.to(0f);
        grow.jump(nextNow);
        grow.to(100f);
        told(true);
    }

    private void toRow(int i) {
        row = i;
        y.to(rise(row));
        growing = focusCard();
        shrinking = null;
        grow.jump(100f);
        shrink.jump(0f);
        told(true);
    }

    /** The remote down from the tab bar: on the carousel (or the row it was on). */
    void fromAbove() {
        if (!hero.isEmpty() && (area == HERO || row == 0)) toTop();
        else if (!rows.isEmpty()) {
            area = ROWS;
            toRow(Math.min(row, rows.size() - 1));
        }
    }

    /** Previews and tests: the remote on the carousel's card i (row -1), or on row's card i, at once. */
    void focus(int rowIndex, int i) {
        if (rowIndex < 0) {
            if (hero.isEmpty()) return;
            area = HERO;
            heroAt = Math.max(0, Math.min(i, hero.size() - 1));
            heroX.jump(-heroAt * (HERO_W + HERO_GAP));
            y.jump(0f);
        } else {
            if (rowIndex >= rows.size()) return;
            area = ROWS;
            row = rowIndex;
            Row rw = rows.get(row);
            rw.focus = Math.max(0, Math.min(i, rw.cards.size() - 1));
            rw.x.jump(target(rw));
            y.jump(rise(row));
            growing = focusCard();
            shrinking = null;
            grow.jump(100f);
            shrink.jump(0f);
        }
        told(true);
    }

    private void told(boolean moved) {
        if (host == null) return;
        if (area == HERO) {
            if (heroAt < hero.size()) host.focused(null, hero.get(heroAt));
        } else {
            Row rw = current();
            if (rw != null && rw.focus < rw.cards.size()) {
                host.focused(rw, rw.cards.get(rw.focus));
                if (moved && rw.more && rw.focus >= rw.cards.size() - 6) host.more(rw);
            }
        }
        invalidate();
    }

    void ok() {
        if (host == null) return;
        if (area == HERO) {
            if (heroAt < hero.size()) host.chosen(null, hero.get(heroAt));
            return;
        }
        Row rw = current();
        if (rw != null && rw.focus < rw.cards.size()) host.chosen(rw, rw.cards.get(rw.focus));
    }

    void okLong() {
        Card c = card();
        if (host != null && c != null) host.held(current(), c);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean moving = ground.draw(c, w, h);
        moving |= y.step();
        moving |= heroX.step();
        moving |= grow.step();
        moving |= shrink.step();
        Card on = active && area == ROWS ? focusCard() : null;
        moving |= cardLift.step(on);
        moving |= heroLift.step(active && area == HERO && heroAt < hero.size() ? hero.get(heroAt) : null);
        float risen = Ui.f(y.value());
        long now = SystemClock.uptimeMillis();
        float k = Math.max(0f, Math.min(1f, (now - wordsAt - WORDS_DELAY) / (float) WORDS_MS));
        if (wordsAt > 0 && k < 1f) moving = true;
        int words = Math.round(255 * Fade.ease(wordsAt < 0 ? 1f : k));
        if (!hero.isEmpty()) {
            // The carousel rises away with the page.
            float away = Math.max(0f, Math.min(1f, y.value() / (FIRST_ROW - ROW_Y)));
            if (away < 0.999f) {
                c.save();
                c.translate(0, -risen);
                drawHero(c, w, Math.round(255 * (1f - away * away)), words);
                c.restore();
            }
        }
        float rowY = Ui.f(ROW_Y), faded = Ui.f(HEAD + CARD_H * 0.6f);
        for (int i = 0; i < rows.size(); i++) {
            float top = Ui.f(top(i)) - risen;
            if (top > h) break;
            if (top + Ui.f(BLOCK) < 0) continue;
            boolean here = area == ROWS && i == row;
            // The remote's row whole; the next dimmed; one going up fades as it passes under the tab bar.
            float fade = top >= rowY - 1f ? 1f : Math.max(0f, Math.min(1f, (top + faded - rowY) / faded));
            if (fade <= 0.01f) continue;
            moving |= drawRow(c, rows.get(i), top, here, here ? 255 : DIM, Math.round(255 * fade));
            if (here) drawInfo(c, top, Math.min(words, Math.round(255 * fade)));
        }
        if (hero.isEmpty() && rows.isEmpty()) drawNote(c);
        if (moving) postInvalidateOnAnimation();
    }

    // ---- the carousel -------------------------------------------------------------------------------------------------

    private void drawHero(Canvas c, int screenW, int alpha, int words) {
        float cw = Ui.f(HERO_W), ch = Ui.f(HERO_H), stepX = Ui.f(HERO_W + HERO_GAP), top = Ui.f(HERO_T);
        float x0 = Ui.f(LEFT) + Ui.f(heroX.value());
        int first = Math.max(0, (int) Math.floor(-x0 / stepX) - 1), last = Math.min(hero.size() - 1, (int) Math.ceil((screenW - x0) / stepX));
        for (int k = first; k <= last; k++) {
            float x = x0 + k * stepX;
            if (x > screenW || x + cw < 0 || k == heroAt) continue;
            heroCard(c, hero.get(k), x, top, cw, ch, false, alpha, words);
        }
        if (heroAt < hero.size()) heroCard(c, hero.get(heroAt), x0 + heroAt * stepX, top, cw, ch, true, alpha, words);
        // Its dots, on glass under it.
        int n = Math.min(hero.size(), 12);
        if (n > 1) {
            float d = Ui.f(10f), gap = Ui.f(12f), lit = Ui.f(26f), pad = Ui.f(16f);
            float wd = (n - 1) * (d + gap) + lit + pad * 2, cx = Ui.f(LEFT + HERO_W / 2f), cy = Ui.f(DOTS_Y), hh = Ui.f(13f);
            r.set(cx - wd / 2f, cy - hh, cx + wd / 2f, cy + hh);
            Frost.glass(c, r, hh, alpha / 255f);
            float x = r.left + pad;
            int at = Math.min(heroAt, n - 1);
            for (int i = 0; i < n; i++) {
                boolean here = i == at;
                int colour = here ? Look07.L1 : Look07.L3;
                fill.setColor(Palette.alpha(colour, (colour >>> 24) * alpha / 255));
                float dw = here ? lit : d;
                r.set(x, cy - d / 2f, x + dw, cy + d / 2f);
                c.drawRoundRect(r, d / 2f, d / 2f, fill);
                x += dw + gap;
            }
        }
    }

    /** One carousel card at (x, y); the remote's (here) with its words and View Details. */
    private void heroCard(Canvas c, Card card, float x, float y, float w, float h, boolean here, int alpha, int words) {
        float rad = Ui.f(HERO_R);
        float up = here ? Math.max(0f, Math.min(1.1f, heroLift.of(card))) : 0f;
        c.save();
        if (up > 0.001f) c.scale(1f + 0.018f * up, 1f + 0.018f * up, x + w / 2f, y + h / 2f);
        r.set(x, y, x + w, y + h);
        Bitmap b = card.wide.isEmpty() ? null : Pics.get(card.wide, Math.round(w), Math.round(h), Pics.FACE, redraw);
        Shadows.under(c, r, rad, Look07.SH_HERO, (here ? 0.7f + 0.3f * Math.min(1f, up) : 0.5f) * alpha / 255f);
        c.translate(x, y);
        if (b != null) {
            Paint p = shaders.paint(b, w, h, 0f);
            p.setAlpha(alpha);
            c.drawRoundRect(0, 0, w, h, rad, rad, p);
            p.setAlpha(255);
        } else {
            fill.setColor(Palette.alpha(PosterArt.blank(card.name), alpha));
            c.drawRoundRect(0, 0, w, h, rad, rad, fill);
        }
        if (!here) {
            // The cards either side: quieter, so the eye stays on the remote's; the next one's name at its foot.
            dim.setAlpha(0x59 * alpha / 255);
            c.drawRoundRect(0, 0, w, h, rad, rad, dim);
            if (!card.shortName.isEmpty()) {
                peekP.setAlpha(alpha);
                c.drawText(card.shortName, Ui.f(40f), h - Ui.f(46f), peekP);
            }
            c.restore();
            return;
        }
        float fadeEnd = w * 0.64f;
        shade.setAlpha(alpha);
        foot.setAlpha(alpha);
        c.save();
        c.scale(fadeEnd, 1f);
        c.drawRoundRect(0, 0, 1f, h, rad / fadeEnd, rad, shade);
        c.restore();
        c.save();
        c.scale(w, h);
        c.drawRoundRect(0, 0.45f, 1f, 1f, rad / w, rad / h, foot);
        c.restore();
        heroWords(card);
        int a = Math.min(alpha, words);
        if (a > 2) {
            float tx = Ui.f(64f), rise = Ui.f(14f) * (1f - a / 255f);
            // From the foot up: View Details, the story, the facts, the mark, what it is.
            float by = h - Ui.f(70f) + rise;
            float bw = Hero07.buttonWidth("View Details", "", -1);
            float g = 1f + 0.05f * Math.max(0f, Math.min(1.1f, up));
            c.save();
            c.scale(g, g, tx + bw / 2f, by);
            Hero07.button(c, tx, by, "View Details", "", -1, true, a);
            c.restore();
            float lh = Ui.f(24f * 1.42f), ty = by - Ui.f(35f) - Ui.f(30f);
            if (heroStory != null) {
                ty -= heroStory.getLineCount() * lh;
                heroStoryP.setAlpha((Look07.L2 >>> 24) * a / 255);
                Ui.draw(c, heroStory, tx, ty + (lh - (heroStoryP.descent() - heroStoryP.ascent())) / 2f);
                ty -= Ui.f(22f);
            }
            float fh = Ui.f(30f);
            facts(c, tx, ty - fh / 2f, card, a, true);
            ty -= fh + Ui.f(20f);
            float mh = heroMark.height(redraw);
            ty -= mh;
            heroMark.draw(c, tx, ty, a, redraw);
            if (!card.kicker.isEmpty()) {
                kickP.setAlpha((Look07.L2 >>> 24) * a / 255);
                c.drawText(card.kicker, tx, ty - Ui.f(14f), kickP);
            }
        }
        if (up > 0.01f) {
            edge.setStrokeWidth(Math.max(1f, Ui.f(2f)));
            edge.setColor(Palette.alpha(0x59FFFFFF, Math.round(0x59 * Math.min(1f, up) * alpha / 255f)));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, edge);
        }
        c.restore();
    }

    /** The carousel card's words laid out once each time it changes (or its details or logo arrive). */
    private void heroWords(Card card) {
        if (card == heroFor) return;
        heroFor = card;
        heroMark.set(card.name, card.logo, Ui.f(560f), Ui.f(150f), Ui.f(820f));
        heroStory = card.story.isEmpty() ? null : Hero07.lines(card.story, heroStoryP, Ui.px(780f), 2, Ui.f(24f * 1.42f));
    }

    /**
     * A card's facts on one line from x, its middle on cy: its score in green (with the thumb under a row), then its year,
     * genre, age and length; dots: " · " between them, as the carousel has them, else spaced, as under a row.
     */
    private void facts(Canvas c, float x, float cy, Card card, int a, boolean dots) {
        float gap = Ui.f(dots ? 14f : 26f);
        boolean first = true;
        if (!card.score.isEmpty()) {
            if (!dots) {
                thumbP.setAlpha(a);
                c.save();
                c.translate(x, cy - Ui.f(14f));
                c.drawPath(thumb, thumbP);
                c.restore();
                x += Ui.f(26f) + Ui.f(10f);
            }
            scoreP.setAlpha(a);
            c.drawText(card.score, x, Type.centred(scoreP, cy), scoreP);
            x += scoreP.measureText(card.score) + gap;
            first = false;
        }
        lineP.setColor(Look07.L1);
        lineP.setAlpha(a);
        x = word(c, card.year, x, cy, gap, dots && !first);
        first &= card.year.isEmpty();
        x = word(c, card.genre, x, cy, gap, dots && !first);
        first &= card.genre.isEmpty();
        if (!card.age.isEmpty()) {
            if (dots && !first) x = dot(c, x, cy);
            x += Hero07.certificate(c, x, cy, card.age, a) + gap;
            first = false;
        }
        word(c, card.length, x, cy, gap, dots && !first);
    }

    /** A word of the facts at x (a dot before it), and where the next one goes. */
    private float word(Canvas c, String w, float x, float cy, float gap, boolean dot) {
        if (w.isEmpty()) return x;
        if (dot) x = dot(c, x, cy);
        c.drawText(w, x, Type.centred(lineP, cy), lineP);
        return x + lineP.measureText(w) + gap;
    }

    private float dot(Canvas c, float x, float cy) {
        c.drawText("·", x - Ui.f(8f), Type.centred(lineP, cy), lineP);
        return x + Ui.f(12f);
    }

    // ---- the rows ---------------------------------------------------------------------------------------------------

    /** How wide a card is now: the row's card in focus wide, the rest posters; in the remote's row, growing and shrinking. */
    private float widthOf(Row rw, Card card, int k, boolean here) {
        if (!here) return k == rw.focus ? WIDE_W : POSTER_W;
        if (card == growing) return POSTER_W + (WIDE_W - POSTER_W) * grow.value() / 100f;
        if (card == shrinking) return POSTER_W + (WIDE_W - POSTER_W) * shrink.value() / 100f;
        return k == rw.focus ? WIDE_W : POSTER_W;
    }

    /** One row at top (screen px): its title, its cards (pictures at alpha pics), the whole faded by fade. */
    private boolean drawRow(Canvas c, Row rw, float top, boolean here, int pics, int fade) {
        boolean moving = rw.x.step();
        float left = Ui.f(LEFT);
        TextPaint tp = here ? headP : quietHead;
        tp.setAlpha(((here ? Look07.L1 : Look07.L3) >>> 24) * fade / 255);
        c.drawText(rw.title, left, Hero07.baseline(top, Hero07.normal(Ui.f(30f)), Ui.f(30f)), tp);
        int a = pics * fade / 255;
        float yy = top + Ui.f(HEAD), hh = Ui.f(CARD_H), screenW = getWidth(), gap = Ui.f(GAP), stepP = Ui.f(POSTER_W + GAP);
        // From the row's slide, each card's place: the cards before the one in focus are posters (but one shrinking).
        float x = left + Ui.f(rw.x.value());
        int k = 0;
        int busy = here ? Math.min(rw.cards.indexOf(shrinking) < 0 ? Integer.MAX_VALUE : rw.cards.indexOf(shrinking),
                rw.cards.indexOf(growing) < 0 ? Integer.MAX_VALUE : rw.cards.indexOf(growing)) : Integer.MAX_VALUE;
        // Whole posters off the left skipped at once.
        int skip = Math.max(0, Math.min(Math.min(rw.focus, busy) - 1, (int) Math.floor(-x / stepP) - 1));
        if (skip > 0) {
            k = skip;
            x += skip * stepP;
        }
        Card lifted = null;
        float liftedX = 0f, liftedW = 0f;
        for (; k < rw.cards.size(); k++) {
            if (x > screenW) break;
            Card card = rw.cards.get(k);
            float w = Ui.f(widthOf(rw, card, k, here));
            if (x + w > -Ui.f(40f)) {
                if (here && card == growing && active && area == ROWS) {
                    lifted = card;
                    liftedX = x;
                    liftedW = w;
                } else {
                    card(c, card, x, yy, w, hh, a, 0f);
                }
            }
            x += w + gap;
        }
        if (lifted != null) card(c, lifted, liftedX, yy, liftedW, hh, a, Math.max(0f, Math.min(1.1f, cardLift.of(lifted))));
        return moving;
    }

    /**
     * One card: as wide as w (a poster at POSTER_W, the wide card at WIDE_W, between them while it grows), its poster and
     * its backdrop cross-fading by how wide it is (Lite: one or the other, no cross-fade), lifted by up.
     */
    private void card(Canvas c, Card card, float x, float y, float w, float h, int a, float up) {
        float pw = Ui.f(POSTER_W), ww = Ui.f(WIDE_W), rad = Ui.f(CARD_R);
        float g = Math.max(0f, Math.min(1f, (w - pw) / (ww - pw)));
        c.save();
        if (up > 0.001f) c.scale(1f + 0.02f * up, 1f + 0.02f * up, x, y + h / 2f);
        r.set(x, y, x + w, y + h);
        if (a > 200) Shadows.under(c, r, rad, up > 0.001f ? Look07.SH_CARD_UP : Look07.SH_CARD, up > 0.001f ? Math.min(1f, up) : 1f);
        c.translate(x, y);
        Bitmap wide = g > 0.001f && !card.wide.isEmpty() ? Pics.get(card.wide, Math.round(ww), Math.round(h), Pics.CROP, redraw) : null;
        Bitmap poster = (g < 0.999f || wide == null) && !card.poster.isEmpty()
                ? Pics.get(card.poster, Math.round(pw), Math.round(h), Pics.CROP, redraw) : null;
        float posterA, wideA;
        if (Motion.lite()) {
            // Lite: no cross-fade, one picture: the backdrop once it is growing (and read), else the poster.
            boolean showWide = wide != null && (card == growing || g >= 0.999f);
            wideA = showWide ? 1f : 0f;
            posterA = showWide ? 0f : 1f;
        } else {
            wideA = wide == null ? 0f : g;
            posterA = poster == null ? 0f : wide == null ? 1f : 1f - g;
        }
        frame.setAlpha(a);
        if (wideA < 0.999f) c.drawRoundRect(0, 0, w, h, rad, rad, frame);
        if (posterA > 0.001f && poster != null) {
            // The poster at its own width, in the middle of the card as it grows.
            Paint p = shaders.paint(poster, pw, h, 0f);
            p.setAlpha(Math.round(a * posterA));
            float dx = (w - pw) / 2f;
            c.save();
            c.translate(dx, 0);
            c.drawRoundRect(Math.max(0f, -dx), 0, Math.min(pw, w - dx), h, rad, rad, p);
            c.restore();
            p.setAlpha(255);
        } else if (poster == null && wide == null) {
            fill.setColor(Palette.alpha(PosterArt.blank(card.name), a));
            c.drawRoundRect(0, 0, w, h, rad, rad, fill);
            nameP.setAlpha(a);
            c.drawText(card.shortName, Ui.f(16f), h - Ui.f(24f), nameP);
        }
        if (wideA > 0.001f && wide != null) {
            // The backdrop at the wide card's width, its middle shown while it is narrower.
            Paint p = shaders.paint(wide, ww, h, 0f);
            p.setAlpha(Math.round(a * wideA));
            float dx = (w - ww) / 2f;
            c.save();
            c.translate(dx, 0);
            c.drawRoundRect(-dx, 0, w - dx, h, rad, rad, p);
            c.restore();
            p.setAlpha(255);
        }
        if (g > 0.5f) {
            // The wide card: its foot darkened, its logo (or name), its episode.
            int ga = Math.round(a * Math.min(1f, (g - 0.5f) * 2f));
            cardFoot.setAlpha(ga);
            c.save();
            c.scale(w, h);
            c.drawRoundRect(0, 0, 1f, 1f, rad / w, rad / h, cardFoot);
            c.restore();
            Hero07.Mark m = markOf(card);
            float mh = m.height(redraw);
            m.draw(c, Ui.f(26f), h - Ui.f(card.progress >= 0f ? 40f : 28f) - mh, ga, redraw);
            if (!card.episode.isEmpty()) chip(c, w, h, card.episode, ga);
        }
        if (card.progress >= 0f && a > 0) Pills.progressOn(c, Ui.f(16f), h - Ui.f(9f) - Ui.f(5f), w - Ui.f(32f), card.progress);
        if (up > 0.01f) {
            edge.setStrokeWidth(Math.max(1.5f, Ui.f(3f)));
            edge.setColor(Palette.alpha(Look07.L1, Math.round(0xE6 * Math.min(1f, up))));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, edge);
        }
        c.restore();
    }

    /** "S2:E4" on a dark chip at the wide card's foot right. */
    private void chip(Canvas c, float w, float h, String words, int a) {
        float tw = chipP.measureText(words), ch = Ui.f(30f), cw = tw + Ui.f(20f);
        r.set(w - Ui.f(18f) - cw, h - Ui.f(26f) - ch, w - Ui.f(18f), h - Ui.f(26f));
        chipFill.setAlpha(0xB3 * a / 255);
        c.drawRoundRect(r, Ui.f(8f), Ui.f(8f), chipFill);
        chipP.setAlpha(a);
        c.drawText(words, r.left + Ui.f(10f), Type.centred(chipP, r.centerY()), chipP);
    }

    /** The mark (logo or lettering) for a wide card: one of three kept, set for a card when it first needs one. */
    private Hero07.Mark markOf(Card card) {
        for (int i = 0; i < marked.length; i++) if (marked[i] == card) return marks[i];
        int i = markNext;
        for (int k = 0; k < marked.length; k++) {
            // Never the one growing, nor the one shrinking.
            if (marked[i] != growing && marked[i] != shrinking) break;
            i = (i + 1) % marks.length;
        }
        markNext = (i + 1) % marks.length;
        marked[i] = card;
        marks[i].set(card.name, card.logo, Ui.f(WIDE_W * 0.62f), Ui.f(120f), Ui.f(WIDE_W - 60f));
        return marks[i];
    }

    /** Under the remote's row: the score, year, genre and length (or episode), then its story's three lines. */
    private void drawInfo(Canvas c, float top, int a) {
        Card card = focusCard();
        if (card == null) return;
        if (card != storyFor) {
            storyFor = card;
            Object k = key(card);
            if (!k.equals(wordsKey)) {
                // Another title: its words fade and rise in (its details or logo arriving later don't).
                wordsKey = k;
                wordsAt = Exec.inline ? -100_000L : SystemClock.uptimeMillis();
                a = Exec.inline ? a : 0;
                invalidate();
            }
            story = card.story.isEmpty() ? null : Hero07.lines(card.story, storyP, Ui.px(STORY_W), 3, Ui.f(24f * 1.4f));
        }
        if (a <= 2) return;
        float x = Ui.f(LEFT), cy = top + Ui.f(HEAD + CARD_H + INFO);
        facts(c, x, cy, card, a, false);
        if (story != null) {
            storyP.setAlpha((Look07.L2 >>> 24) * a / 255);
            Ui.draw(c, story, x, top + Ui.f(HEAD + CARD_H + STORY));
        }
    }

    private void drawNote(Canvas c) {
        if (note.isEmpty() && noteWhy.isEmpty()) return;
        float x = Ui.f(LEFT + 40f), yy = Ui.f(470f);
        if (!note.isEmpty()) c.drawText(note, x, yy, noteP);
        if (!noteWhy.isEmpty()) c.drawText(noteWhy, x, yy + Ui.f(56f), whyP);
    }

    /** Previews and tests: a card's line under its row as it reads (its score, year, genre, age and length). */
    static String line(Card c) {
        StringBuilder b = new StringBuilder();
        for (String w : new String[]{c.score, c.year, c.genre, c.age, c.length}) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w);
        }
        return b.toString();
    }

    /** Tests: how wide the remote's card is now, and the card before it (design px). */
    float[] previewWidths() {
        Row rw = row < rows.size() ? rows.get(row) : null;
        if (rw == null || rw.cards.isEmpty()) return new float[]{0f, 0f};
        Card on = rw.cards.get(rw.focus), before = rw.focus > 0 ? rw.cards.get(rw.focus - 1) : null;
        return new float[]{widthOf(rw, on, rw.focus, true), before == null ? 0f : widthOf(rw, before, rw.focus - 1, true)};
    }
}
