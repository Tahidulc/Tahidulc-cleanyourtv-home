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
import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Movies' and TV Shows' page in the 07 look (homefield-07-real 09 and 11), made for the remote (design px): the page in
 * the colour of what the remote is on (Ground), the playlists' lists as pills under the tab bar (All movies, Recently
 * added, Your TorBox, then each category; the one shown white) with the sort pill at the right ("Newest first"), the
 * title the remote is on in a rounded card (90, 214, 1740 x 400: its picture at the right, its logo or name, facts,
 * story, cast and director on the left, Play, the trailer and My List at its foot's right), then the shelves: the
 * chosen list ("Recently added  In Home and Your TorBox", "1 / 25" at the right) as posters that lift and tilt (Card07:
 * NEW, the picture's 4K or 1080p, TorBox's cloud), a series' as wide cards with their logo or name on them, then
 * Continue watching and My List. A long list ends in a card to see all of it ("All 1,234"). Up from the first shelf
 * goes to the pills, and from them to the tab bar; Down goes from shelf to shelf (the page rising, the card going);
 * Left and Right along, the card following. Only what is in sight is drawn; nothing is made while drawing.
 */
final class CineRows extends View {
    interface Host {
        /** The remote moved to a shelf's card (index: the "All" card is the shelf's size). */
        void focused(Row row, int index);

        /** OK on a shelf's card, or on its "All" card (index = the shelf's size). */
        void chosen(Row row, int index);

        /** The remote ran off the top (Ui.UP) of the pills; true when the host took it. */
        boolean edge(int dir);

        /** The remote moved along the pills to the list at index (the sort pill: none). */
        void pill(int index);

        /** OK on the sort pill. */
        void sort();
    }

    static final int POSTERS = 0, WIDE = 1;
    /** Where the remote is: the pills (the sort pill after them), the shelves. */
    static final int PILLS = 0, SHELVES = 1;

    /** A card: its title, its picture, its line ("S2 E4 · 32m left"), how far it was watched, what it stands for. */
    static final class Tile {
        final Catalog.Title title;
        final String picture, line;
        final float progress;
        final Object item;
        /** NEW (a poster), NEW EPISODES (a series' wide card), "" for none; its 4K or 1080p; its name cut to its card. */
        String badge = "", quality = "", shortName;

        Tile(Catalog.Title title, String picture, String line, float progress, Object item) {
            this.title = title;
            this.picture = picture == null ? "" : picture;
            this.line = line == null ? "" : line;
            this.progress = progress;
            this.item = item;
        }
    }

    /** One shelf: its heading and quieter words, kind and cards, how many its whole list has, a tag to keep it by. */
    static final class Row {
        final String title;
        final int kind;
        final List<Tile> tiles;
        final int total;
        final Object tag;
        final String allWords;
        String quiet = "";
        int focus;
        final Glide x = new Glide(Look07.SCROLL_MS);

        Row(String title, int kind, List<Tile> tiles, int total, Object tag) {
            this.title = title;
            this.kind = kind;
            this.tiles = tiles == null ? Collections.emptyList() : tiles;
            this.total = total;
            this.tag = tag;
            this.allWords = "All " + String.format(Locale.UK, "%,d", total);
        }

        boolean hasAll() {
            return total > tiles.size();
        }

        int stops() {
            return tiles.size() + (hasAll() ? 1 : 0);
        }
    }

    /** Places (design px): the pills' row, the card, the first shelf's heading and cards, a shelf's gaps. */
    static final float LEFT = 90f, PILLS_Y = 163f, PILL_H = 50f, HERO_T = 214f, HERO_W = 1740f, HERO_H = 400f, HERO_R = 40f,
            PIC_W = 1240f, SHELF_TOP = 648f, CARDS_TOP = 700f, POSTER_W = 210f, POSTER_H = 315f, POSTER_GAP = 28f,
            WIDE_W = 384f, WIDE_H = 216f, WIDE_GAP = 44f, SHELF_AFTER = 70f, CAPTION = 80f;
    /** How long the page takes to rise to a shelf, and a card's words to come in after its picture. */
    static final long DOWN_MS = 340, WORDS_DELAY = 150, WORDS_MS = 450;

    private final List<Row> rows = new ArrayList<>();
    private int row, area = SHELVES;
    private final Glide y = new Glide(DOWN_MS);
    private Host host;
    private boolean active = true;
    private final Ground ground;
    private final Lift07 lift = new Lift07(), pillLift = new Lift07();
    private final Card07 posters = new Card07(Look07.R_POSTER), wides = new Card07(Look07.R_CARD);
    private final Card07.Marks marks = new Card07.Marks();
    private final Shaders shaders = new Shaders();

    // The pills.
    private final List<String> pills = new ArrayList<>();
    /** Each pill's key for its lift, the sort pill's last (kept: nothing made a frame). */
    private final List<Object> pillKeys = new ArrayList<>();
    private int pill, pillAt;
    private String sortWords = "";

    // The card: its title's words, laid out when it changes (hero()), and when it changed.
    private Catalog.Title title;
    private String picture = "", heroNote = "", heroWhy = "";
    private final Hero07.Mark mark = new Hero07.Mark(56f);
    private final Hero07.Facts facts = new Hero07.Facts();
    private StaticLayout story, cast;
    private boolean plusOn, trailer;
    private String playWords = "Play";
    private long heroAt = -100_000L;
    private Bitmap facesFor;
    private float fadeEnd;

    private final TextPaint headP, quietP, countP, factsP, storyP, castP, nameP, wideName, allP, noteP, sortP;
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), wideFoot = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF(), face = new RectF();
    private final Runnable redraw = this::invalidate;

    CineRows(Context c) {
        super(c);
        Cine.init(c);
        ground = new Ground(this);
        headP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        quietP = Type.paint(21f, Type.MEDIUM, Look07.L3);
        countP = Type.clock(21f, Type.SEMIBOLD, Look07.L3);
        factsP = Type.paint(21f, Type.MEDIUM, Look07.L2);
        storyP = Type.paint(21f, Type.REGULAR, Look07.L2);
        castP = Type.paint(18f, Type.REGULAR, Look07.L3);
        nameP = Type.paint(26f, Type.BOLD, Look07.L1);
        nameP.setShadowLayer(Ui.f(10f), 0, Ui.f(2f), 0x99000000);
        wideName = Type.paint(34f, Type.BOLD, Look07.L1);
        wideName.setShadowLayer(Ui.f(14f), 0, Ui.f(3f), 0xB3000000);
        allP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        noteP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2);
        sortP = Type.paint(20f, Type.MEDIUM, Look07.L2);
        frame.setColor(0xFF08080E);
        edge.setStyle(Paint.Style.STROKE);
        // The card's words' side: rgba(8,8,14) .98, .94 at 30 %, .55 at 50 %, clear by 72 % (over a unit width).
        shade.setShader(new LinearGradient(0, 0, 1, 0, new int[]{0xFA08080E, 0xF008080E, 0x8C08080E, 0x0008080E},
                new float[]{0f, 0.417f, 0.694f, 1f}, Shader.TileMode.CLAMP));
        // A wide card's foot under its name (shade-b: .82 at the foot, .35 at 42 %, clear by 70 %).
        wideFoot.setShader(new LinearGradient(0, 0, 0, 1, new int[]{0x00000000, 0x59000000, 0xD1000000},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        Shadows.prepare(Look07.SH_HERO, HERO_R);
        Shadows.prepare(Look07.SH_BUTTON, 35f);
        Shadows.prepare(Look07.SH_PILL, Look07.R_PILL);
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

    /** The shelves (the remote kept on the shelf with the same tag, else the first; each keeps its place). */
    void set(List<Row> list) {
        Object was = row < rows.size() ? rows.get(row).tag : null;
        int[] focusOf = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            for (Row old : rows) {
                if (old.tag != null && old.tag.equals(list.get(i).tag)) {
                    focusOf[i] = Math.min(old.focus, Math.max(0, list.get(i).stops() - 1));
                    break;
                }
            }
        }
        rows.clear();
        rows.addAll(list);
        row = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row rw = rows.get(i);
            rw.focus = focusOf[i];
            rw.x.jump(target(rw));
            if (was != null && was.equals(rw.tag)) row = i;
            for (Tile t : rw.tiles) if (t.shortName == null) t.shortName = Ui.fit(t.title.name, rw.kind == WIDE ? wideName : nameP,
                    Ui.f((rw.kind == WIDE ? WIDE_W : POSTER_W) - 36f));
        }
        y.jump(top(row) - top(0));
        invalidate();
    }

    /** The pills' words (the lists), the one shown, and the sort pill's words. */
    void pills(List<String> names, int chosen, String sort) {
        pills.clear();
        pills.addAll(names);
        while (pillKeys.size() < pills.size() + 1) pillKeys.add(new Object());
        pill = Math.max(0, Math.min(chosen, pills.size() - 1));
        if (area != PILLS) pillAt = pill;
        sortWords = sort == null ? "" : sort;
        invalidate();
    }

    boolean isEmpty() {
        return rows.isEmpty() || rows.get(0).tiles.isEmpty();
    }

    Row current() {
        return row < rows.size() ? rows.get(row) : null;
    }

    /** The card the remote is on (null on an "All" card). */
    Tile tile() {
        Row rw = current();
        return rw == null || rw.focus >= rw.tiles.size() ? null : rw.tiles.get(rw.focus);
    }

    /** The card a key or two from the remote's (down shelves away, then along places), or null: to read ahead. */
    Tile near(int down, int along) {
        int ri = row + down;
        if (ri < 0 || ri >= rows.size()) return null;
        Row rw = rows.get(ri);
        int i = rw.focus + along;
        return i < 0 || i >= rw.tiles.size() ? null : rw.tiles.get(i);
    }

    int area() {
        return area;
    }

    /**
     * The card's title: its words laid out now (never while drawing), its picture behind the page. line: Continue
     * watching's ("S2 E4 · 32m left") in place of the cast; logo: TMDB's ("" for its name).
     */
    void hero(Catalog.Title t, Catalog.Details d, com.gathin.homefieldtv.engine.Tmdb.Extras x, String line, boolean onList,
              String play) {
        boolean fresh = t != title;
        playWords = play == null || play.isEmpty() ? "Play" : play;
        title = t;
        heroNote = heroWhy = "";
        if (fresh) heroAt = Exec.inline ? -100_000L : SystemClock.uptimeMillis();
        picture = BrowseScreen.heroPicture(t, d);
        ground.set(picture);
        mark.set(t.name, x == null ? "" : x.logo, Ui.f(460f), Ui.f(120f), Ui.f(860f));
        facts.set(t, d, x, 2);
        String plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        story = plot.isEmpty() ? null : Hero07.lines(plot, storyP, Ui.px(820f), 2, Ui.f(21f * 1.42f));
        CharSequence who = line == null || line.isEmpty() ? credits(t, d) : line;
        cast = who.length() == 0 ? null : Hero07.lines(who, castP, Ui.px(860f), 1, Hero07.normal(Ui.f(18f)));
        plusOn = onList;
        trailer = d != null && !TitleScreen.trailerId(d.trailer).isEmpty();
        facesFor = null;
        invalidate();
    }

    /** "With A, B, C · Director D" with its words in bold (TMDB's or the provider's names). */
    private static CharSequence credits(Catalog.Title t, Catalog.Details d) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        if (d == null) return b;
        List<String> names = Billboard.names(d.cast, 3);
        if (!names.isEmpty()) {
            label(b, "With");
            b.append(' ').append(String.join(", ", names));
        }
        String dir = d.director == null ? "" : d.director.trim();
        if (!dir.isEmpty()) {
            if (b.length() > 0) b.append(" · ");
            label(b, t.series ? "Created by" : "Director");
            b.append(' ').append(dir);
        }
        return b;
    }

    private static void label(android.text.SpannableStringBuilder b, String words) {
        int at = b.length();
        b.append(words);
        b.setSpan(new android.text.style.ForegroundColorSpan(Look07.L2), at, b.length(), 0);
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, b.length(), 0);
    }

    /** No title on the card: a list's "All" card (its name and how many), or nothing to show (why). */
    void heroNote(String head, String why) {
        title = null;
        heroNote = head == null ? "" : head;
        heroWhy = why == null ? "" : why;
        picture = "";
        ground.set("");
        invalidate();
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    private static float tileW(Row rw) {
        return rw.kind == WIDE ? WIDE_W : POSTER_W;
    }

    private static float tileH(Row rw) {
        return rw.kind == WIDE ? WIDE_H : POSTER_H;
    }

    private static float gap(Row rw) {
        return rw.kind == WIDE ? WIDE_GAP : POSTER_GAP;
    }

    /** A shelf's height: its heading, its cards, a wide one's caption, the room after it. */
    private static float height(Row rw) {
        return (CARDS_TOP - SHELF_TOP) + tileH(rw) + (rw.kind == WIDE ? CAPTION : 0f) + SHELF_AFTER;
    }

    /** Where a shelf's heading is on the page (design px, before it rises). */
    private float top(int i) {
        float t = SHELF_TOP;
        for (int k = 0; k < i && k < rows.size(); k++) t += height(rows.get(k));
        return t;
    }

    /** Where a shelf slides so the remote's card is first (the first few from the start). */
    private static float target(Row rw) {
        return -Math.max(0, rw.focus) * (tileW(rw) + gap(rw));
    }

    /** How far the page has risen for the shelf the remote is on: the first stays under the card; a later one comes up
     *  to where the card was. */
    private float rise(int i) {
        return i == 0 ? 0f : top(i) - HERO_T;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        if (area == PILLS) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (pillAt > 0) toPill(pillAt - 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (pillAt < pills.size() - (sortWords.isEmpty() ? 1 : 0)) toPill(pillAt + 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    return host != null && host.edge(Ui.UP);
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!rows.isEmpty()) {
                        area = SHELVES;
                        toRow(0);
                    }
                    return true;
                default:
                    return false;
            }
        }
        Row rw = current();
        if (rw == null) {
            if (code == KeyEvent.KEYCODE_DPAD_UP) toPills();
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (rw.focus > 0) move(rw, rw.focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (rw.focus < rw.stops() - 1) move(rw, rw.focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row == 0) toPills();
                else toRow(row - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < rows.size() - 1) toRow(row + 1);
                return true;
            default:
                return false;
        }
    }

    private void toPills() {
        area = PILLS;
        pillAt = pill;
        y.to(0f);
        invalidate();
    }

    /** The remote down from the tab bar: on the pills, at the list shown. */
    void fromAbove() {
        toPills();
    }

    private void toPill(int i) {
        pillAt = i;
        if (i < pills.size()) {
            pill = i;
            if (host != null) host.pill(i);
        }
        invalidate();
    }

    private void move(Row rw, int to) {
        rw.focus = to;
        rw.x.to(target(rw));
        told();
    }

    private void toRow(int i) {
        row = i;
        y.to(rise(row));
        told();
    }

    /** Previews and tests: the remote on a shelf and a card at once (shelf -1: on the pills, at card). */
    void focus(int rowIndex, int index) {
        if (rowIndex < 0) {
            area = PILLS;
            pillAt = Math.max(0, Math.min(index, pills.size()));
            y.jump(0f);
            invalidate();
            return;
        }
        if (rowIndex >= rows.size()) return;
        area = SHELVES;
        row = rowIndex;
        Row rw = rows.get(row);
        rw.focus = Math.max(0, Math.min(index, rw.stops() - 1));
        rw.x.jump(target(rw));
        y.jump(rise(row));
        told();
    }

    private void told() {
        Row rw = current();
        if (host != null && rw != null) host.focused(rw, rw.focus);
        invalidate();
    }

    void ok() {
        if (area == PILLS) {
            if (pillAt >= pills.size()) {
                if (host != null) host.sort();
            } else if (!rows.isEmpty()) {
                area = SHELVES;
                toRow(0);
            }
            return;
        }
        Row rw = current();
        if (host != null && rw != null) host.chosen(rw, rw.focus);
    }

    /** Back: from the shelves to the pills (true); from the pills, false (the tab bar). */
    boolean back() {
        if (area == SHELVES) {
            toPills();
            return true;
        }
        return false;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        facesFor = null;
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean moving = ground.draw(c, w, h);
        moving |= y.step();
        Row on = current();
        Object focus = !active || area != SHELVES || on == null ? null : on.focus < on.tiles.size() ? on.tiles.get(on.focus) : on;
        moving |= lift.step(focus);
        moving |= pillLift.step(active && area == PILLS && pillAt < pillKeys.size() ? pillKeys.get(pillAt) : null);
        float risen = Ui.f(y.value());
        // The pills and the card rise away with the page (fading as they go).
        float away = Math.max(0f, Math.min(1f, y.value() / 300f));
        if (away < 0.999f) {
            int a = Math.round(255 * (1f - away));
            c.save();
            c.translate(0, -risen);
            if (a < 255) c.saveLayerAlpha(0, 0, w, Ui.f(HERO_T + HERO_H + 10f), a);
            drawPills(c);
            long now = SystemClock.uptimeMillis();
            float k = heroAt < 0 ? 1f : Math.max(0f, Math.min(1f, (now - heroAt - WORDS_DELAY) / (float) WORDS_MS));
            if (k < 1f) moving = true;
            drawHero(c, Fade.ease(k));
            if (a < 255) c.restore();
            c.restore();
        }
        float top = Ui.f(SHELF_TOP) - risen;
        for (int i = 0; i < rows.size(); i++) {
            Row rw = rows.get(i);
            float hh = Ui.f(height(rw));
            if (top > h) break;
            if (top + hh > 0) {
                // Shelves above the remote's fade as the page rises past them.
                float fade = i >= row ? 1f : Math.max(0f, Math.min(1f, (top + hh - Ui.f(HERO_T)) / Ui.f(200f)));
                int alpha = Math.round(255 * fade);
                if (alpha > 0) moving |= drawRow(c, rw, top, alpha, i == row);
            }
            top += hh;
        }
        if (rows.isEmpty() && title == null) drawNothing(c);
        if (moving) postInvalidateOnAnimation();
    }

    private void drawPills(Canvas c) {
        float x = Ui.f(LEFT), cy = Ui.f(PILLS_Y);
        for (int i = 0; i < pills.size(); i++) {
            String name = pills.get(i);
            boolean on = i == pill;
            float pw = Pills.pillWidth(name, on) - Ui.f(Pills.PILL_PAD * 2) * (1f - PILL_H / Pills.PILL_H);
            if (x > getWidth() - Ui.f(320f)) break;
            pw = pill(c, x, cy, name, on, i == pillAt, pw);
            x += pw + Ui.f(8f);
        }
        if (!sortWords.isEmpty()) {
            // The sort pill at the right: glass, its icon, the order shown; white where the remote is.
            boolean here = pillAt >= pills.size();
            float tw = sortP.measureText(sortWords), pw = Ui.f(20f) + Ui.f(20f) + Ui.f(10f) + tw + Ui.f(20f);
            float x0 = Ui.f(1920f - LEFT) - pw, hh = Ui.f(PILL_H) / 2f;
            float up = here ? Math.max(0f, Math.min(1.1f, pillLift.of(pillAt < pillKeys.size() ? pillKeys.get(pillAt) : null))) : 0f;
            r.set(x0, cy - hh, x0 + pw, cy + hh);
            c.save();
            if (up > 0.01f) {
                Shadows.draw(c, r, hh, Look07.SH_PILL, Math.min(1f, up));
                c.scale(1f + 0.06f * up, 1f + 0.06f * up, r.centerX(), cy);
            }
            if (here) {
                fill.setColor(Look07.FOCUS_FILL);
                c.drawRoundRect(r, hh, hh, fill);
            } else {
                Frost.glass(c, r, hh, 1f);
            }
            int ink = here ? Look07.INK : Look07.L2;
            Icons07.draw(c, Icons07.SORT, x0 + Ui.f(20f) + Ui.f(10f), cy, Ui.f(20f), ink);
            sortP.setColor(ink);
            c.drawText(sortWords, x0 + Ui.f(50f), Type.centred(sortP, cy), sortP);
            c.restore();
        }
    }

    /** A pill: white for the list shown; where the remote is, lifted a little (07's spring); its width. */
    private float pill(Canvas c, float x, float cy, String name, boolean on, boolean here, float pw) {
        float up = here && area == PILLS ? Math.max(0f, Math.min(1.1f, pillLift.of(pillAt < pillKeys.size() ? pillKeys.get(pillAt) : null))) : 0f;
        if (up <= 0.01f) return Pills.pill(c, x, cy, name, null, on || here && area == PILLS, true, PILL_H);
        float g = 1f + 0.06f * up, hh = Ui.f(PILL_H) / 2f * g;
        r.set(x + pw / 2f - pw * g / 2f, cy - hh, x + pw / 2f + pw * g / 2f, cy + hh);
        Shadows.draw(c, r, hh, Look07.SH_PILL, Math.min(1f, up));
        c.save();
        c.scale(g, g, x + pw / 2f, cy);
        float out = Pills.pill(c, x, cy, name, null, true, true, PILL_H);
        c.restore();
        return out;
    }

    /** The card for the title the remote is on (90, 214, 1740 x 400), its words at k of their way in. */
    private void drawHero(Canvas c, float k) {
        float x = Ui.f(LEFT), y = Ui.f(HERO_T), w = Ui.f(HERO_W), h = Ui.f(HERO_H), rad = Ui.f(HERO_R);
        r.set(x, y, x + w, y + h);
        Shadows.draw(c, r, rad, Look07.SH_HERO, 1f);
        c.drawRoundRect(r, rad, rad, frame);
        c.save();
        c.translate(x, y);
        // Its picture at the right, 1240 wide, its faces toward the right (Pics.FACE).
        float pw = Ui.f(PIC_W), px = w - pw;
        Bitmap b = picture.isEmpty() ? null : Pics.get(picture, Math.min(Math.round(pw), 1240), Math.round(Math.min(pw, 1240f) * h / pw),
                Pics.FACE, redraw);
        if (b != null) {
            Paint p = shaders.paint(b, pw, h, 0f);
            c.save();
            c.translate(px, 0);
            c.drawRoundRect(-rad, 0, pw, h, rad, rad, p);
            c.restore();
            if (facesFor != b) {
                facesFor = b;
                fadeEnd = w * 0.72f;
                float[] f = Pics.faces(picture);
                if (f != null && !(f[0] == Faces.FALLBACK[0] && f[1] == Faces.FALLBACK[1] && f[2] == Faces.FALLBACK[2])) {
                    Faces.place(f[4], 1f, pw / h, f, Pics.FACE_BIAS, pw, h, face);
                    fadeEnd = Math.max(w * 0.54f, Math.min(w * 0.72f, px + face.left - Ui.f(12f)));
                }
            }
        } else {
            fadeEnd = w * 0.72f;
        }
        // The words' side, dark, ending before the faces.
        c.save();
        c.scale(fadeEnd, 1f);
        c.drawRoundRect(0, 0, 1f, h, rad / fadeEnd, rad, shade);
        c.restore();
        int a = Math.round(255 * k);
        float rise = Ui.f(14f) * (1f - k), tx = Ui.f(58f), ty = Ui.f(44f) + rise;
        if (title != null && a > 2) {
            // Its logo or name, its facts, its story, its cast (or where it was left).
            boolean logo = mark.isLogo(redraw) || mark.waiting(redraw);
            mark.draw(c, tx, ty, a, redraw);
            ty += logo ? mark.height(redraw) : mark.height(redraw);
            float fh = Hero07.normal(Ui.f(21f));
            ty += Ui.f(18f);
            facts.draw(c, tx, ty + fh / 2f, a, factsP);
            ty += fh;
            if (story != null) {
                ty += Ui.f(12f);
                float lh = Ui.f(21f * 1.42f);
                storyP.setAlpha((Look07.L2 >>> 24) * a / 255);
                Ui.draw(c, story, tx, ty + (lh - (storyP.descent() - storyP.ascent())) / 2f);
                ty += story.getLineCount() * lh;
            }
            if (cast != null) {
                ty += Ui.f(10f);
                castP.setAlpha((Look07.L3 >>> 24) * a / 255);
                Ui.draw(c, cast, tx, ty);
            }
            // Play, the trailer, My List: at the card's foot on the right.
            float cy = h - Ui.f(40f) - Ui.f(35f), right = w - Ui.f(50f);
            float cx = right - Ui.f(35f);
            Hero07.circle(c, cx, cy, plusOn ? Icons07.CHECK : Icons07.PLUS, false, plusOn, a);
            cx -= Ui.f(70f) + Ui.f(18f);
            if (trailer) {
                Hero07.circle(c, cx, cy, Icons07.FILM, false, false, a);
                cx -= Ui.f(70f) + Ui.f(18f);
            }
            float bw = Hero07.buttonWidth(playWords, "", Icons07.PLAY);
            Hero07.button(c, cx + Ui.f(35f) - bw, cy, playWords, "", Icons07.PLAY, true, a);
        } else if (!heroNote.isEmpty()) {
            allP.setAlpha(255);
            c.drawText(heroNote, tx, Ui.f(150f), allP);
            if (!heroWhy.isEmpty()) c.drawText(heroWhy, tx, Ui.f(200f), noteP);
        }
        edge.setStrokeWidth(Math.max(1f, Ui.f(1f)));
        edge.setColor(0x1AFFFFFF);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, edge);
        c.restore();
    }

    private void drawNothing(Canvas c) {
        float x = Ui.f(LEFT), y = Ui.f(HERO_T + HERO_H + 80f);
        if (!heroNote.isEmpty()) return;
        c.drawText(heroWhy, x, y, noteP);
    }

    /** One shelf at top (screen px); true while its cards are still sliding. */
    private boolean drawRow(Canvas c, Row rw, float top, int alpha, boolean here) {
        boolean moving = rw.x.step();
        float left = Ui.f(LEFT);
        boolean layer = alpha < 255;
        if (layer) c.saveLayerAlpha(0, top, getWidth(), top + Ui.f(height(rw)), alpha);
        // Its heading, its quiet words, its count at the right.
        float base = Hero07.baseline(top, Hero07.normal(Ui.f(30f)), Ui.f(30f));
        c.drawText(rw.title, left, base, headP);
        if (!rw.quiet.isEmpty()) c.drawText(rw.quiet, left + headP.measureText(rw.title) + Ui.f(16f), base, quietP);
        if (here && area == SHELVES && rw.total > 0) {
            String n = count(rw);
            c.drawText(n, Ui.f(1920f - LEFT) - countP.measureText(n), base - Ui.f(3f), countP);
        }
        float tw = Ui.f(tileW(rw)), th = Ui.f(tileH(rw)), step = tw + Ui.f(gap(rw));
        float x0 = left + Ui.f(rw.x.value()), y = top + Ui.f(CARDS_TOP - SHELF_TOP);
        int first = Math.max(0, (int) Math.floor((left - x0) / step) - 1);
        int last = Math.min(rw.stops() - 1, (int) Math.ceil((getWidth() - x0) / step));
        int lifted = -1;
        for (int k = first; k <= last; k++) {
            Object key = k < rw.tiles.size() ? rw.tiles.get(k) : rw;
            if (here && lift.of(key) > 0.01f && key == lift.on()) {
                lifted = k;
                continue;
            }
            card(c, rw, k, x0 + k * step, y, tw, th, here ? lift.of(key) : 0f);
        }
        if (lifted >= 0) {
            Object key = lifted < rw.tiles.size() ? rw.tiles.get(lifted) : rw;
            card(c, rw, lifted, x0 + lifted * step, y, tw, th, lift.of(key));
        }
        if (layer) c.restore();
        return moving;
    }

    /** "1 / 25": where the remote is in the shelf's whole list. */
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

    private void card(Canvas c, Row rw, int k, float x, float y, float w, float h, float up) {
        if (x > getWidth() || x + w < -Ui.f(40f)) return;
        boolean wide = rw.kind == WIDE;
        Card07 cards = wide ? wides : posters;
        if (k >= rw.tiles.size()) {
            // The end of a long list: "All 1,234" on a dark card.
            cards.begin(c, x, y, w, h, up, null);
            float tw = allP.measureText(rw.allWords);
            c.drawText(rw.allWords, (w - tw) / 2f, h / 2f, allP);
            Icons07.draw(c, Icons07.RIGHT, w / 2f, h / 2f + Ui.f(40f), Ui.f(30f), Look07.L2);
            cards.end(c);
            return;
        }
        Tile t = rw.tiles.get(k);
        Bitmap b = t.picture.isEmpty() ? null : Pics.get(t.picture, cards.picW(w), cards.picH(h), Pics.CROP, redraw);
        marks.clear();
        marks.badge = t.badge;
        marks.quality = t.quality;
        marks.torbox = t.title.torbox();
        marks.progress = t.progress;
        if (wide) {
            cards.begin(c, x, y, w, h, up, b);
            // Its foot darkened under its name; its name (TMDB's logo is the hero's), NEW EPISODES on glass.
            c.save();
            c.scale(w, h);
            c.drawRect(0, 0.3f, 1f, 1f, wideFoot);
            c.restore();
            if (b == null || rw.kind == WIDE) c.drawText(t.shortName, Ui.f(20f), h - Ui.f(t.progress >= 0f ? 30f : 22f), wideName);
            String badge = t.badge;
            marks.badge = "";
            cards.marks(c, w, h, marks);
            if (!badge.isEmpty()) episodesChip(c, badge);
            cards.end(c);
            cards.caption(c, x, y + h, w, up, t.title.name, t.line);
        } else {
            cards.begin(c, x, y, w, h, up, b);
            if (b == null) {
                // No picture: its name on the card's own colour.
                fill.setColor(PosterArt.blank(t.title.name));
                c.drawRoundRect(0, 0, w, h, Ui.f(Look07.R_POSTER), Ui.f(Look07.R_POSTER), fill);
                c.drawText(t.shortName, Ui.f(16f), h - Ui.f(22f), nameP);
            }
            cards.marks(c, w, h, marks);
            cards.end(c);
        }
    }

    /** "NEW EPISODES" on a wide card's top left: dark glass, letter-spaced capitals. */
    private void episodesChip(Canvas c, String words) {
        float h = Ui.f(28f), w = chipP().measureText(words) + Ui.f(20f);
        r.set(Ui.f(14f), Ui.f(14f), Ui.f(14f) + w, Ui.f(14f) + h);
        fill.setColor(0x80000000);
        c.drawRoundRect(r, h / 2f, h / 2f, fill);
        c.drawText(words, r.left + Ui.f(10f), Type.centred(chip, r.centerY()), chip);
    }

    private TextPaint chip;

    private TextPaint chipP() {
        if (chip == null) {
            chip = Type.paint(14f, Type.BOLD, Look07.L1);
            chip.setLetterSpacing(0.04f);
        }
        return chip;
    }
}
