package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CategorySet;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Shelves;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Editorial Home, Movies and TV Shows (Settings › Appearance › Home style), in 07's look, made for the remote (design
 * px of a 1920 x 1080 screen): the page in the colour of what the remote is on (Ground, its picture blurred behind),
 * under the tab bar a big carousel of wide cards (1500 x 580, the next one peeking in at the right and the one before at
 * the left): its picture with the faces toward the right, a word of what it is ("FILM", "TV SHOW"), its TMDB logo or its
 * name in big lettering, its facts, two lines of its story, Play and My List; the page's dots under it on glass. Under
 * the carousel the rows, the first peeking in at the foot: posters that lift and tilt (Card07; Lite: lift without the
 * tilt), Top 10's posters each with its big number, wide pictures for Continue watching, and tiles (moods, the
 * playlists' categories). Left and Right go along the carousel or a row (the row sliding so the remote's card is first);
 * Down from the carousel goes into the rows, the page rising so the remote's row is near the top, the carousel fading
 * away as it goes; Up comes back, and from the carousel up to the tab bar. The remote's card lifts and has its name
 * and a line under it. Only what is in sight is drawn; nothing is made while drawing.
 */
final class Editorial extends View {
    interface Host {
        /** The remote moved: to a carousel card (row null) or a row's card (card null: its "All" card). */
        void focused(Row row, Card card);

        /** OK on a card (row null: the carousel's; card null: a row's "All" card). */
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

    /** A card: what it stands for, its picture, its words (made as it is set, never while drawing), its marks. */
    static final class Card {
        final Shelves.Pick pick;
        final String picture;
        /** Its name and a line under it (the remote's card), its name cut to its card (a card with no picture). */
        String name = "", line = "", shortName = "";
        String badge = "", quality = "";
        boolean torbox;
        float progress = -1f;
        /** Top 10's place (1 to 10), 0 for none. */
        int rank;
        /** A mood's tile: its colours (and its gradient's paint, made with the card). */
        int from, to;
        Paint tile;
        /** The carousel's words: what it is ("FILM"), its button's words, whether it is on My List, its TMDB logo. */
        String kicker = "", play = "Play", logo = "", story = "";
        boolean onList;
        Catalog.Details details;
        Tmdb.Extras extras;

        Card(Shelves.Pick pick, String picture) {
            this.pick = pick;
            this.picture = picture == null ? "" : picture;
        }

        Catalog.Title title() {
            return pick.title;
        }
    }

    /** A row: its id (kept with the remote's place), title, look, cards, how many its whole list has. */
    static final class Row {
        final String id, title;
        final int kind;
        final List<Card> cards;
        final int total;
        final boolean all;
        final String allWords;
        boolean more;
        String quiet = "";
        int focus;
        final Glide x = new Glide(Look07.SCROLL_MS);
        /** Made from this shelf (the page keeps a row while its shelf is the same). */
        Object from;

        Row(String id, String title, int kind, List<Card> cards, int total) {
            this.id = id;
            this.title = title;
            this.kind = kind;
            this.cards = cards == null ? Collections.emptyList() : cards;
            this.total = total;
            this.all = total > this.cards.size() && kind != CategorySet.TOP10;
            this.allWords = "All " + String.format(Locale.UK, "%,d", total);
        }

        int stops() {
            return cards.size() + (all ? 1 : 0);
        }
    }

    // ---- places (design px) ------------------------------------------------------------------------------------------
    static final float LEFT = 90f, HERO_T = 150f, HERO_W = 1500f, HERO_H = 580f, HERO_GAP = 30f, HERO_R = 40f, DOTS_Y = 762f,
            FIRST_ROW = 806f, HEAD = 54f, POSTER_W = 210f, POSTER_H = 315f, POSTER_GAP = 28f, WIDE_W = 384f, WIDE_H = 216f,
            WIDE_GAP = 40f, NUM_W = 118f, TILE_W = 330f, TILE_H = 186f, TILE_GAP = 28f, CAPTION = 76f, AFTER = 30f, ROW_Y = 176f;
    /** How long the page takes to rise to a row, the carousel to slide a card, a card's words to come in after it. */
    static final long DOWN_MS = 340, HERO_MS = 420, WORDS_DELAY = 120, WORDS_MS = 380;

    private final List<Card> hero = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int heroAt, row, area = HERO;
    private final Glide y = new Glide(DOWN_MS), heroX = new Glide(HERO_MS);
    private Host host;
    private boolean active = true;
    private final Ground ground;
    private final Lift07 lift = new Lift07(), heroLift = new Lift07();
    private final Card07 posters = new Card07(Look07.R_POSTER), wides = new Card07(Look07.R_CARD), tiles = new Card07(Look07.R_CARD);
    private final Card07.Marks marks = new Card07.Marks();
    private final Shaders shaders = new Shaders();
    private final Frost mat = new Frost(Look07.MAT);

    // The carousel card's words, laid out when it changes.
    private final Hero07.Mark mark = new Hero07.Mark(68f);
    private final Hero07.Facts facts = new Hero07.Facts();
    private StaticLayout story;
    private long heroChanged = -100_000L;
    private Card wordsFor;
    private Object wordsKey;
    private String note = "", noteWhy = "";

    private final TextPaint headP, quietP, countP, factsP, storyP, kickP, nameP, numP, numLine, tileP, allP, noteP, whyP;
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), foot = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
            tileFoot = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;
    private static final String[] NUMBERS = {"", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};

    Editorial(Context c) {
        super(c);
        Cine.init(c);
        ground = new Ground(this);
        headP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        quietP = Type.paint(21f, Type.MEDIUM, Look07.L3);
        countP = Type.clock(21f, Type.SEMIBOLD, Look07.L3);
        factsP = Type.paint(22f, Type.MEDIUM, Look07.L2);
        storyP = Type.paint(22f, Type.REGULAR, Look07.L2);
        kickP = Type.label(Type.LABEL, Look07.L2);
        nameP = Type.paint(26f, Type.BOLD, Look07.L1);
        nameP.setShadowLayer(Ui.f(10f), 0, Ui.f(2f), 0x99000000);
        // Top 10's numbers: Inter Display at its heaviest, a quiet fill with a light edge (drawn under their posters).
        numP = Type.paint(250f, Type.EXTRABOLD, 0x33FFFFFF);
        numP.setLetterSpacing(-0.06f);
        numLine = Type.paint(250f, Type.EXTRABOLD, 0x8CFFFFFF);
        numLine.setLetterSpacing(-0.06f);
        numLine.setStyle(Paint.Style.STROKE);
        numLine.setStrokeWidth(Math.max(1.5f, Ui.f(3f)));
        tileP = Type.paint(30f, Type.BOLD, Look07.L1);
        tileP.setShadowLayer(Ui.f(12f), 0, Ui.f(2f), 0x80000000);
        allP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        noteP = Type.paint(Type.H2, Type.BOLD, Look07.L1);
        whyP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2);
        frame.setColor(0xFF101016);
        edge.setStyle(Paint.Style.STROKE);
        dim.setColor(0x80000000);
        // The carousel card's words' side: deep on the left, clear by 62 % of its width (over a unit width).
        shade.setShader(new LinearGradient(0, 0, 1, 0, new int[]{0xF208080E, 0xD908080E, 0x6608080E, 0x0008080E},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        // And its foot (over a unit height): clear to 45 %, then a soft dark under the buttons.
        foot.setShader(new LinearGradient(0, 0, 0, 1, new int[]{0x00000000, 0x00000000, 0xA6000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        // A picture tile's foot under its name.
        tileFoot.setShader(new LinearGradient(0, 0, 0, 1, new int[]{0x33000000, 0x40000000, 0xD9000000},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        Shadows.prepare(Look07.SH_HERO, HERO_R);
        Shadows.prepare(Look07.SH_BUTTON, 35f);
        Shadows.prepare(Look07.SH_GLASS, 13f);
        Shadows.prepare(Look07.SH_PANEL, Look07.R_CARD);
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
                    nw.focus = Math.min(focusIn(nw, old), Math.max(0, nw.stops() - 1));
                    break;
                }
            }
        }
        hero.clear();
        hero.addAll(carousel);
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
                // A new row: in place; one that replaces a row (its next page read) slides on from where that one was.
                Row old = null;
                for (Row o : before) if (o.id.equals(rw.id)) old = o;
                if (old == null) rw.x.jump(target(rw));
                else {
                    rw.x.jump(old.x.value());
                    rw.x.to(target(rw));
                }
            }
            if (rw.id.equals(rowWas)) row = i;
            for (Card c : rw.cards) {
                if (c.shortName.isEmpty()) c.shortName = Ui.fit(c.name, rw.kind == CategorySet.TILES ? tileP : nameP,
                        Ui.f(tileW(rw) - 36f));
            }
        }
        if (rowWas == null || row >= rows.size() || !rows.get(row).id.equals(rowWas)) row = Math.min(was, Math.max(0, rows.size() - 1));
        if (area == ROWS && rows.isEmpty()) area = HERO;
        if (area == HERO && hero.isEmpty() && !rows.isEmpty()) area = ROWS;
        // The remote's row stays where it was on the screen: a row arriving above it moves the page as much.
        if (area == ROWS && rowWas != null && row < rows.size()) y.shift(top(row) - atWas);
        if (area == HERO) y.jump(0f);
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

    /** What a card is kept by: its title, mood or category. */
    private static Object key(Card c) {
        Shelves.Pick p = c.pick;
        if (p.mood != null) return "m:" + p.mood.name;
        if (p.category != null) return "c:" + p.category.name;
        return p.title == null ? c : p.title.key;
    }

    /** Nothing to show: its words (the playlists still being read, none yet). */
    void note(String head, String why) {
        note = head == null ? "" : head;
        noteWhy = why == null ? "" : why;
        invalidate();
    }

    /** The carousel card's words again (its details, its logo, My List read since). */
    void refresh(Card c) {
        if (c == wordsFor) wordsFor = null;
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

    /** The card the remote is on (the carousel's, or a row's; null on an "All" card or nothing). */
    Card card() {
        if (area == HERO) return heroAt < hero.size() ? hero.get(heroAt) : null;
        Row rw = current();
        return rw == null || rw.focus >= rw.cards.size() ? null : rw.cards.get(rw.focus);
    }

    /** A carousel card a few along from the remote's (to read its picture ahead), or null. */
    Card heroNear(int along) {
        int i = heroAt + along;
        return i < 0 || i >= hero.size() ? null : hero.get(i);
    }

    /** A row's card a key or two away (rows down, places along), or null. */
    Card near(int down, int along) {
        int ri = (area == HERO ? -1 : row) + down;
        if (ri < 0 || ri >= rows.size()) return null;
        Row rw = rows.get(ri);
        int i = (area == HERO ? 0 : rw.focus) + along;
        return i < 0 || i >= rw.cards.size() ? null : rw.cards.get(i);
    }

    List<Row> rows() {
        return rows;
    }

    int heroSize() {
        return hero.size();
    }

    /** The page's picture: the carousel's or the remote's card's. */
    void ground(String url) {
        ground.set(url);
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    static float tileW(Row rw) {
        switch (rw.kind) {
            case CategorySet.WIDE: return WIDE_W;
            case CategorySet.TILES: return TILE_W;
            default: return POSTER_W;
        }
    }

    static float tileH(Row rw) {
        switch (rw.kind) {
            case CategorySet.WIDE: return WIDE_H;
            case CategorySet.TILES: return TILE_H;
            default: return POSTER_H;
        }
    }

    /** From one card to the next. */
    static float step(Row rw) {
        switch (rw.kind) {
            case CategorySet.WIDE: return WIDE_W + WIDE_GAP;
            case CategorySet.TILES: return TILE_W + TILE_GAP;
            case CategorySet.TOP10: return NUM_W + POSTER_W + POSTER_GAP;
            default: return POSTER_W + POSTER_GAP;
        }
    }

    /** A row's height: its heading, its cards, the caption's room, the room after it. */
    static float height(Row rw) {
        return HEAD + tileH(rw) + (rw.kind == CategorySet.TILES ? 20f : CAPTION) + AFTER;
    }

    /** Where a row's top is on the page (design px, before it rises): the first under the carousel (or the tab bar). */
    private float top(int i) {
        float t = hero.isEmpty() ? ROW_Y : FIRST_ROW;
        for (int k = 0; k < i && k < rows.size(); k++) t += height(rows.get(k));
        return t;
    }

    /** Where a row slides so the remote's card is first. */
    private static float target(Row rw) {
        return -Math.max(0, rw.focus) * step(rw);
    }

    /** How far the page rises for the row the remote is on: the first just into full view, a later one to near the top. */
    private float rise(int i) {
        if (i < 0 || rows.isEmpty()) return 0f;
        if (i == 0) return Math.max(0f, top(0) + height(rows.get(0)) - AFTER + 6f - 1080f);
        return Math.max(0f, top(i) - ROW_Y);
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
                if (rw.focus < rw.stops() - 1) move(rw, rw.focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) toRow(row - 1);
                else if (!hero.isEmpty()) toTop();
                else return host != null && host.edge(Ui.UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < rows.size() - 1) toRow(row + 1);
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
        rw.focus = to;
        rw.x.to(target(rw));
        told(true);
    }

    private void toRow(int i) {
        row = i;
        y.to(rise(row));
        told(true);
    }

    /** The remote down from the tab bar: on the carousel (or the first row). */
    void fromAbove() {
        if (!hero.isEmpty()) toTop();
        else if (!rows.isEmpty()) {
            area = ROWS;
            toRow(0);
        }
    }

    /** Previews and tests: the remote on the carousel's card i (row -1), or on row's card i, at once. */
    void focus(int rowIndex, int i) {
        if (rowIndex < 0) {
            area = HERO;
            heroAt = Math.max(0, Math.min(i, hero.size() - 1));
            heroX.jump(-heroAt * (HERO_W + HERO_GAP));
            y.jump(0f);
        } else {
            if (rowIndex >= rows.size()) return;
            area = ROWS;
            row = rowIndex;
            Row rw = rows.get(row);
            rw.focus = Math.max(0, Math.min(i, rw.stops() - 1));
            rw.x.jump(target(rw));
            y.jump(rise(row));
        }
        told(true);
    }

    private void told(boolean moved) {
        if (host == null) return;
        if (area == HERO) {
            if (heroAt < hero.size()) host.focused(null, hero.get(heroAt));
        } else {
            Row rw = current();
            if (rw != null) {
                host.focused(rw, rw.focus < rw.cards.size() ? rw.cards.get(rw.focus) : null);
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
        if (rw != null) host.chosen(rw, rw.focus < rw.cards.size() ? rw.cards.get(rw.focus) : null);
    }

    void okLong() {
        Card c = card();
        if (host != null && c != null) host.held(current(), c);
    }

    /** Back: from the rows up to the carousel (true); from the carousel, false (the tab bar). */
    boolean back() {
        if (area == ROWS && !hero.isEmpty()) {
            toTop();
            return true;
        }
        if (area == ROWS && row > 0) {
            toRow(0);
            return true;
        }
        return false;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean moving = ground.draw(c, w, h);
        moving |= y.step();
        moving |= heroX.step();
        Row on = current();
        Object focus = !active || on == null ? null : on.focus < on.cards.size() ? on.cards.get(on.focus) : on;
        moving |= lift.step(focus);
        moving |= heroLift.step(active && area == HERO && heroAt < hero.size() ? hero.get(heroAt) : null);
        float risen = Ui.f(y.value());
        if (!hero.isEmpty()) {
            // The carousel rises away with the page, fading once it is mostly gone.
            float away = Math.max(0f, Math.min(1f, (y.value() - 200f) / 380f));
            if (away < 0.999f) {
                int a = Math.round(255 * (1f - away));
                c.save();
                c.translate(0, -risen);
                if (a < 255) c.saveLayerAlpha(0, 0, w, Ui.f(DOTS_Y + 30f), a);
                moving |= drawHero(c, w);
                if (a < 255) c.restore();
                c.restore();
            }
        }
        float top = Ui.f(hero.isEmpty() ? ROW_Y : FIRST_ROW) - risen;
        for (int i = 0; i < rows.size(); i++) {
            Row rw = rows.get(i);
            float hh = Ui.f(height(rw));
            if (top > h) break;
            if (top + hh > 0) {
                // Rows above the remote's fade as the page rises past them.
                float fade = area == HERO || i >= row ? 1f : Math.max(0f, Math.min(1f, (top + hh - Ui.f(ROW_Y)) / Ui.f(220f)));
                int alpha = Math.round(255 * fade);
                if (alpha > 0) moving |= drawRow(c, rw, top, alpha, area == ROWS && i == row);
            }
            top += hh;
        }
        if (hero.isEmpty() && rows.isEmpty()) drawNote(c);
        if (moving) postInvalidateOnAnimation();
    }

    /** The carousel: the remote's card with its words, the one before and the next peeking in; its dots. */
    private boolean drawHero(Canvas c, int screenW) {
        boolean moving = false;
        float cw = Ui.f(HERO_W), ch = Ui.f(HERO_H), stepX = Ui.f(HERO_W + HERO_GAP), top = Ui.f(HERO_T);
        float x0 = Ui.f(LEFT) + Ui.f(heroX.value());
        int first = Math.max(0, (int) Math.floor(-x0 / stepX) - 1), last = Math.min(hero.size() - 1, (int) Math.ceil((screenW - x0) / stepX));
        for (int k = first; k <= last; k++) {
            float x = x0 + k * stepX;
            if (x > screenW || x + cw < 0) continue;
            if (k != heroAt) heroCard(c, hero.get(k), x, top, cw, ch, false);
        }
        if (heroAt < hero.size()) moving = heroCard(c, hero.get(heroAt), x0 + heroAt * stepX, top, cw, ch, true);
        // Its dots, on glass under it.
        int n = Math.min(hero.size(), 12);
        if (n > 1) {
            float d = Ui.f(10f), gap = Ui.f(12f), lit = Ui.f(26f), pad = Ui.f(16f);
            float wd = (n - 1) * (d + gap) + lit + pad * 2, cx = Ui.f(LEFT + HERO_W / 2f), cy = Ui.f(DOTS_Y), hh = Ui.f(13f);
            r.set(cx - wd / 2f, cy - hh, cx + wd / 2f, cy + hh);
            Frost.glass(c, r, hh, 1f);
            float x = r.left + pad;
            int at = Math.min(heroAt, n - 1);
            for (int i = 0; i < n; i++) {
                boolean here = i == at;
                fill.setColor(here ? Look07.L1 : Look07.L3);
                float dw = here ? lit : d;
                r.set(x, cy - d / 2f, x + dw, cy + d / 2f);
                c.drawRoundRect(r, d / 2f, d / 2f, fill);
                x += dw + gap;
            }
        }
        return moving;
    }

    /** One carousel card at (x, y); the remote's (here) with its words, lifted where the remote is on it. */
    private boolean heroCard(Canvas c, Card card, float x, float y, float w, float h, boolean here) {
        boolean moving = false;
        float rad = Ui.f(HERO_R);
        float up = here ? Math.max(0f, Math.min(1.1f, heroLift.of(card))) : 0f;
        c.save();
        if (up > 0.001f) c.scale(1f + 0.018f * up, 1f + 0.018f * up, x + w / 2f, y + h / 2f);
        r.set(x, y, x + w, y + h);
        Bitmap b = card.picture.isEmpty() ? null : Pics.get(card.picture, Math.round(w), Math.round(h), Pics.FACE, redraw);
        Shadows.under(c, r, rad, Look07.SH_HERO, here ? 0.7f + 0.3f * Math.min(1f, up) : 0.5f);
        c.translate(x, y);
        if (b != null) c.drawRoundRect(0, 0, w, h, rad, rad, shaders.paint(b, w, h, 0f));
        else {
            fill.setColor(PosterArt.blank(card.name));
            c.drawRoundRect(0, 0, w, h, rad, rad, fill);
        }
        if (!here) {
            // The cards either side: quieter, so the eye stays on the remote's.
            c.drawRoundRect(0, 0, w, h, rad, rad, dim);
            c.restore();
            return false;
        }
        // The words' side, and the foot under the buttons.
        float fadeEnd = w * 0.64f;
        c.save();
        c.scale(fadeEnd, 1f);
        c.drawRoundRect(0, 0, 1f, h, rad / fadeEnd, rad, shade);
        c.restore();
        c.save();
        c.scale(w, h);
        c.drawRect(0, 0.45f, 1f, 1f, foot);
        c.restore();
        long now = SystemClock.uptimeMillis();
        float k = heroChanged < 0 ? 1f : Math.max(0f, Math.min(1f, (now - heroChanged - WORDS_DELAY) / (float) WORDS_MS));
        if (k < 1f) moving = true;
        words(card);
        int a = Math.round(255 * Fade.ease(k));
        if (a > 2) {
            float rise = Ui.f(14f) * (1f - Fade.ease(k)), tx = Ui.f(64f);
            // From the foot up: the buttons, the story, the facts, the mark, what it is.
            float by = h - Ui.f(66f) + rise;
            boolean lit = active && area == HERO;
            float bw = Hero07.button(c, tx, by, card.play, "", Icons07.PLAY, lit, a);
            Hero07.circle(c, tx + bw + Ui.f(18f) + Ui.f(35f), by, card.onList ? Icons07.CHECK : Icons07.PLUS, false, card.onList, a);
            float lh = Ui.f(22f * 1.42f), ty = by - Ui.f(35f) - Ui.f(28f);
            if (story != null) {
                ty -= story.getLineCount() * lh;
                storyP.setAlpha((Look07.L2 >>> 24) * a / 255);
                Ui.draw(c, story, tx, ty + (lh - (storyP.descent() - storyP.ascent())) / 2f);
                ty -= Ui.f(14f);
            }
            float fh = Hero07.normal(Ui.f(22f));
            if (!facts.isEmpty()) {
                facts.draw(c, tx, ty - fh / 2f, a, factsP);
                ty -= fh + Ui.f(16f);
            }
            float mh = mark.height(redraw);
            ty -= mh;
            mark.draw(c, tx, ty, a, redraw);
            if (!card.kicker.isEmpty()) {
                kickP.setAlpha((Look07.L2 >>> 24) * a / 255);
                c.drawText(card.kicker, tx, ty - Ui.f(14f), kickP);
            }
        }
        if (up > 0.01f) {
            edge.setStrokeWidth(Math.max(1f, Ui.f(2f)));
            edge.setColor(Palette.alpha(0x59FFFFFF, Math.round(0x59 * Math.min(1f, up))));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, edge);
        }
        c.restore();
        return moving;
    }

    /** The carousel card's words laid out once each time it changes (or its details, logo or story arrive). */
    private void words(Card card) {
        if (card == wordsFor) return;
        Object k = key(card);
        if (!k.equals(wordsKey)) {
            // Another title: its words fade and rise in (its details or logo arriving later don't).
            wordsKey = k;
            heroChanged = Exec.inline ? -100_000L : SystemClock.uptimeMillis();
        }
        wordsFor = card;
        mark.set(card.name, card.logo, Ui.f(560f), Ui.f(150f), Ui.f(820f));
        if (card.title() != null) facts.set(card.title(), card.details, card.extras, 2);
        else facts.words("", "");
        story = card.story.isEmpty() ? null : Hero07.lines(card.story, storyP, Ui.px(760f), 2, Ui.f(22f * 1.42f));
    }

    private void drawNote(Canvas c) {
        if (note.isEmpty() && noteWhy.isEmpty()) return;
        float x = Ui.f(LEFT + 40f), yy = Ui.f(470f);
        if (!note.isEmpty()) c.drawText(note, x, yy, noteP);
        if (!noteWhy.isEmpty()) c.drawText(noteWhy, x, yy + Ui.f(56f), whyP);
    }

    /** One row at top (screen px); true while its cards are still sliding. */
    private boolean drawRow(Canvas c, Row rw, float top, int alpha, boolean here) {
        boolean moving = rw.x.step();
        float left = Ui.f(LEFT);
        boolean layer = alpha < 255;
        if (layer) c.saveLayerAlpha(0, top, getWidth(), top + Ui.f(height(rw)), alpha);
        float base = Hero07.baseline(top, Hero07.normal(Ui.f(30f)), Ui.f(30f));
        c.drawText(rw.title, left, base, headP);
        if (!rw.quiet.isEmpty()) c.drawText(rw.quiet, left + headP.measureText(rw.title) + Ui.f(16f), base, quietP);
        if (here && active && rw.total > 1) {
            String n = count(rw);
            c.drawText(n, Ui.f(1920f - LEFT) - countP.measureText(n), base - Ui.f(3f), countP);
        }
        float tw = Ui.f(tileW(rw)), th = Ui.f(tileH(rw)), stepX = Ui.f(step(rw));
        float x0 = left + Ui.f(rw.x.value()), yy = top + Ui.f(HEAD);
        int first = Math.max(0, (int) Math.floor((left - x0) / stepX) - 1);
        int last = Math.min(rw.stops() - 1, (int) Math.ceil((getWidth() - x0) / stepX));
        int lifted = -1;
        Object on = lift.on();
        for (int k = first; k <= last; k++) {
            Object key = k < rw.cards.size() ? rw.cards.get(k) : rw;
            if (here && key == on && lift.of(key) > 0.01f) {
                lifted = k;
                continue;
            }
            card(c, rw, k, x0 + k * stepX, yy, tw, th, here ? lift.of(key) : 0f, false);
        }
        if (lifted >= 0) {
            Object key = lifted < rw.cards.size() ? rw.cards.get(lifted) : rw;
            card(c, rw, lifted, x0 + lifted * stepX, yy, tw, th, lift.of(key), true);
        }
        if (layer) c.restore();
        return moving;
    }

    /** "3 / 40": where the remote is in the row's whole list. */
    private String count(Row rw) {
        if (countFor == rw && countAt == rw.focus) return countWords;
        countFor = rw;
        countAt = rw.focus;
        countWords = Math.min(rw.focus + 1, rw.total) + " / " + String.format(Locale.UK, "%,d", rw.total);
        return countWords;
    }

    private Row countFor;
    private int countAt = -1;
    private String countWords = "";

    private void card(Canvas c, Row rw, int k, float x, float yy, float w, float h, float up, boolean here) {
        if (rw.kind == CategorySet.TOP10) {
            // Its number first, under the poster's left edge.
            if (k < rw.cards.size()) {
                Card cd = rw.cards.get(k);
                String n = NUMBERS[Math.max(0, Math.min(10, cd.rank))];
                float nx = x + Ui.f(NUM_W + 46f) - numP.measureText(n), ny = yy + h - Ui.f(8f);
                c.drawText(n, nx, ny, numP);
                c.drawText(n, nx, ny, numLine);
            }
            x += Ui.f(NUM_W);
        }
        if (x > getWidth() || x + w < -Ui.f(40f)) return;
        Card07 cards = rw.kind == CategorySet.WIDE ? wides : rw.kind == CategorySet.TILES ? tiles : posters;
        if (k >= rw.cards.size()) {
            // The end of a long list: "All 1,234" on a frosted card.
            cards.begin(c, x, yy, w, h, up, null);
            r.set(0, 0, w, h);
            mat.draw(c, r, Ui.f(Look07.R_POSTER), 1f);
            float aw = allP.measureText(rw.allWords);
            c.drawText(rw.allWords, (w - aw) / 2f, h / 2f, allP);
            Icons07.draw(c, Icons07.RIGHT, w / 2f, h / 2f + Ui.f(40f), Ui.f(30f), Look07.L2);
            cards.end(c);
            return;
        }
        Card t = rw.cards.get(k);
        if (rw.kind == CategorySet.TILES && t.tile != null) {
            cards.begin(c, x, yy, w, h, up, null);
            c.drawRoundRect(0, 0, w, h, Ui.f(Look07.R_CARD), Ui.f(Look07.R_CARD), t.tile);
            Icons07.draw(c, Icons07.FILM, w - Ui.f(40f), Ui.f(38f), Ui.f(30f), 0x66FFFFFF);
            c.drawText(t.shortName, Ui.f(22f), h - Ui.f(26f), tileP);
            cards.end(c);
            return;
        }
        Bitmap b = t.picture.isEmpty() ? null : Pics.get(t.picture, cards.picW(w), cards.picH(h), Pics.CROP, redraw);
        cards.begin(c, x, yy, w, h, up, b);
        if (rw.kind == CategorySet.TILES) {
            // A category's tile: its first title's picture under its name.
            c.save();
            c.scale(w, h);
            c.drawRect(0, 0, 1f, 1f, tileFoot);
            c.restore();
            c.drawText(t.shortName, Ui.f(22f), h - Ui.f(26f), tileP);
            cards.end(c);
            return;
        }
        if (b == null) {
            // No picture: its name on the card's own colour.
            fill.setColor(PosterArt.blank(t.name));
            c.drawRoundRect(0, 0, w, h, Ui.f(Look07.R_POSTER), Ui.f(Look07.R_POSTER), fill);
            c.drawText(t.shortName, Ui.f(16f), h - Ui.f(t.progress >= 0f ? 32f : 22f), nameP);
        }
        marks.clear();
        marks.badge = t.badge;
        marks.quality = t.quality;
        marks.torbox = t.torbox;
        marks.progress = t.progress;
        cards.marks(c, w, h, marks);
        cards.end(c);
        // Its name and line under it: always on a wide row, and on the remote's card elsewhere.
        if (rw.kind == CategorySet.WIDE || here) cards.caption(c, x, yy + h, w, up, t.name, t.line);
    }
}
