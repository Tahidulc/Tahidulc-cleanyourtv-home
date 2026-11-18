package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home, as the family's hero-card video has it, made for the remote (design pixels, 1920 x 1080): one large rounded
 * card of whatever the remote is on, its artwork filling it (a channel's: its logo on its own colour), its name big and
 * bold over it with a line of facts (Season 1 | 2019 | fantasy | 15+ | five stars), and along the card's foot a strip of
 * small pictures: a series' episodes, a channel's next programmes, a film's like ones. The whole page takes the
 * artwork's colour, a deeper block of it behind the card's lower half. Left and Right go from card to card: the card
 * stays where it is and the next picture cross-fades into it, the names slide out and in one at a time and the page's
 * colour fades to the next (one gliding place, Glide, so a held key glides through them); the strip rises in under the
 * new card once it has arrived. Down goes into the strip, its tile growing with
 * the episode's name; Up from the card goes to the sections along the top (On now, Continue watching, films, shows,
 * sport, favourites), and from them to Homefield's mark, which opens the menu.
 */
final class HomeCard extends View {
    interface Host {
        /** The remote moved to another section (its place). */
        void section(int index);

        /** The remote rests on a card (its details and strip may be wanted). */
        void focused(Card card);

        /** OK on a card. */
        void chosen(Card card);

        /** OK on a tile of a card's strip. */
        void chosen(Card card, Tile tile);

        /** OK on Homefield's mark, or Back from the sections: the menu. */
        void menu();
    }

    /** One of the sections along the top, and its cards. */
    static final class Section {
        final String name;
        final Object tag;
        final List<Card> cards;
        int at;

        Section(String name, Object tag, List<Card> cards) {
            this.name = name;
            this.tag = tag;
            this.cards = cards;
        }
    }

    /** A card: what it is (a channel, a title, a place to carry on from), its name, facts and pictures. */
    static final class Card {
        final Object item;
        final String key, name, picture, logo;
        /** Its facts and stars (a title's filled in when its details have been read). */
        String[] facts;
        int stars;
        final boolean live;
        final float progress;
        List<Tile> strip = Collections.emptyList();
        int stripAt;
        int tint;
        float size;
        Paint art;
        Bitmap made;

        Card(Object item, String key, String name, String[] facts, int stars, String picture, String logo, boolean live, float progress) {
            this.item = item;
            this.key = key;
            this.name = name == null ? "" : name;
            this.facts = facts == null ? new String[0] : facts;
            this.stars = stars;
            this.picture = picture == null ? "" : picture;
            this.logo = logo == null ? "" : logo;
            this.live = live;
            this.progress = progress;
        }
    }

    /** A tile of a card's strip: an episode, a programme, a like title; its number (or time) and name. */
    static final class Tile {
        final Object item;
        final String picture, label, name;
        final float progress;
        String small = "", grown = "";

        Tile(Object item, String picture, String label, String name, float progress) {
            this.item = item;
            this.picture = picture == null ? "" : picture;
            this.label = label == null ? "" : label;
            this.name = name == null ? "" : name;
            this.progress = progress;
        }
    }

    /** Where the remote is: the sections along the top (under 07's tab bar), the card, its strip. */
    static final int TABS = 0, CARD = 1, STRIP = 2;
    /** The card in 07's place under the tab bar (left 90, top 214, 1740 x 760, rounded 46); the sections' row above it. */
    static final float L = 90f, T = 214f, W = 1740f, H = 760f, R = 46f, NAME_X = 196f, NAME_Y = 0.56f,
            TILE_W = 240f, TILE_H = 135f, TILE_GAP = 30f, GROW = 1.6f, MARK_X = 100f, MARK_Y = 163f, TABS_X = 112f,
            LOGO_W = 360f, LOGO_H = 210f;

    private final Host host;
    private final List<Section> sections = new ArrayList<>();
    private int section, area = CARD, tab;
    /** The cards' place (in cards: one is about 545 design pixels of slide), the section's fade, the strip's place. */
    private final Glide pos = new Glide(SLIDE_MS, W * 0.32f), swap = new Glide(FADE_MS, 200f), stripX = new Glide(CineRows.ROW_MS);
    /** How long a card takes to slide in, and a section to fade in. */
    static final long SLIDE_MS = 440, FADE_MS = 380;
    private final Lift lift = new Lift();
    private String note = "", noteHead = "";

    private final TextPaint nameP, factP, sepP, tabP, tabOn, tabInk, labelP, tileName, timeP, liveP, noteP, headP;
    private final Paint ground = new Paint(), block = new Paint(), shadeLeft = new Paint(), shadeFoot = new Paint(),
            place = Ui.fill(0xFF2A2C33), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), white = Ui.fill(Cine.WHITE),
            ring = Ui.stroke(Cine.WHITE, 3f), disc = Ui.fill(0x33FFFFFF), red = Ui.fill(Cine.RED), track = Ui.fill(0x59FFFFFF),
            tileFoot = new Paint(), glass = Ui.fill(0xFF2E3038), star = Ui.fill(Cine.TEXT), starOff = Ui.fill(0x59FFFFFF),
            amber = Ui.fill(Cine.AMBER), amberInk = Ui.fill(Cine.AMBER_INK);
    private final RectF r = new RectF(), logoBox = new RectF();
    private final Path starPath = new Path();
    private float[] tabX = new float[0], tabW = new float[0];
    private final Runnable redraw = this::invalidate, again = this::prepareNear;

    HomeCard(Context c, Host host) {
        super(c);
        this.host = host;
        Cine.init(c);
        nameP = Cine.text(118f, Cine.outfit(800), Cine.TEXT);
        nameP.setShadowLayer(Ui.f(22f), 0, Ui.f(4f), 0x8C000000);
        factP = Cine.text(26f, Cine.inter(), 0xEBFFFFFF);
        factP.setShadowLayer(Ui.f(8f), 0, Ui.f(2f), 0x80000000);
        sepP = Cine.text(26f, Cine.inter(), 0x73FFFFFF);
        tabP = Cine.text(25f, Cine.inter(), 0x99FFFFFF);
        tabOn = Cine.text(25f, Cine.interSemi(), Cine.TEXT);
        tabInk = Cine.text(25f, Cine.interSemi(), Cine.INK);
        labelP = Cine.text(24f, Cine.outfit(600), Cine.TEXT);
        labelP.setShadowLayer(Ui.f(6f), 0, Ui.f(2f), 0xB3000000);
        tileName = Cine.text(19f, Cine.inter(), Cine.SOFT);
        tileName.setShadowLayer(Ui.f(6f), 0, Ui.f(2f), 0xB3000000);
        timeP = Cine.text(30f, Cine.outfit(700), Cine.TEXT);
        liveP = Cine.text(18f, Cine.interSemi(), Cine.TEXT);
        liveP.setLetterSpacing(0.08f);
        noteP = Cine.text(30f, Cine.inter(), Cine.SOFT);
        headP = Cine.text(96f, Cine.outfit(800), Cine.TEXT);
        swap.jump(1f);
        // A star of radius 1 about (0, 0), drawn scaled.
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? 1f : 0.45f;
            float x = (float) Math.cos(a) * rr, y = (float) -Math.sin(a) * rr;
            if (i == 0) starPath.moveTo(x, y);
            else starPath.lineTo(x, y);
        }
        starPath.close();
    }

    // ---- what it shows ----------------------------------------------------------------------------------------------

    /** The sections (each keeps the card it was on, found again by its key; the remote stays on its section). */
    void set(List<Section> list) {
        Object was = section < sections.size() ? sections.get(section).tag : null;
        String[] keys = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            for (Section old : sections) {
                if (old.tag.equals(list.get(i).tag) && old.at < old.cards.size()) keys[i] = old.cards.get(old.at).key;
            }
        }
        sections.clear();
        sections.addAll(list);
        int keep = 0;
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            s.at = 0;
            if (keys[i] != null) for (int k = 0; k < s.cards.size(); k++) if (s.cards.get(k).key.equals(keys[i])) s.at = k;
            if (s.tag.equals(was)) keep = i;
        }
        boolean changed = section != keep || was == null;
        if (was == null && !list.isEmpty()) {
            // The first cards: in with the section's fade.
            swap.jump(0f);
            swap.to(1f);
        }
        section = keep;
        tab = section;
        if (sections.isEmpty()) area = TABS;
        else if (area == TABS && was == null) area = CARD;
        Section s = current();
        if (s != null && (changed || Math.abs(pos.value() - s.at) > 1f)) pos.jump(s.at);
        measureTabs();
        prepareNear();
        invalidate();
    }

    /** Nothing to show: a heading and why (no playlist, the channels loading). */
    void empty(String head, String why) {
        noteHead = head == null ? "" : head;
        note = why == null ? "" : why;
        invalidate();
    }

    /** A card's strip, when its episodes or programmes are known; the remote put on at (when not in it already). */
    void strip(Card c, List<Tile> tiles, int at) {
        c.strip = tiles == null ? Collections.emptyList() : tiles;
        for (Tile t : c.strip) {
            t.small = Ui.fit(t.name, tileName, Ui.f(TILE_W - 30f));
            t.grown = Ui.fit(t.name, tileName, Ui.f(TILE_W * GROW - 30f));
        }
        if (!(area == STRIP && c == card())) c.stripAt = Math.max(0, Math.min(at, c.strip.size() - 1));
        if (c == card()) stripX.jump(stripTarget(c));
        invalidate();
    }

    Section current() {
        return section < sections.size() ? sections.get(section) : null;
    }

    Card card() {
        Section s = current();
        return s == null || s.at >= s.cards.size() ? null : s.cards.get(s.at);
    }

    int area() {
        return area;
    }

    private void measureTabs() {
        tabX = new float[sections.size()];
        tabW = new float[sections.size()];
        float x = Ui.f(TABS_X);
        for (int i = 0; i < sections.size(); i++) {
            tabX[i] = x;
            tabW[i] = tabOn.measureText(sections.get(i).name);
            x += tabW[i] + Ui.f(46f);
        }
    }

    /** The cards near the remote's made ready: pictures asked for, colours worked out, names sized (never while drawing). */
    private void prepareNear() {
        Section s = current();
        if (s == null) return;
        for (int k = Math.max(0, s.at - 1); k <= Math.min(s.cards.size() - 1, s.at + 1); k++) prepare(s.cards.get(k));
        // Two along, and the cards the neighbouring tabs rest on: read after what is on screen (Pics.prefetch), so a
        // held key or a new tab finds its picture there.
        ahead(s, s.at - 2);
        ahead(s, s.at + 2);
        if (section > 0) ahead(sections.get(section - 1), sections.get(section - 1).at);
        if (section < sections.size() - 1) ahead(sections.get(section + 1), sections.get(section + 1).at);
    }

    private static void ahead(Section s, int i) {
        if (i < 0 || i >= s.cards.size()) return;
        Card c = s.cards.get(i);
        String pic = c.picture.isEmpty() ? c.logo : c.picture;
        if (pic.isEmpty()) return;
        if (c.picture.isEmpty()) Logos.prefetch(pic, Ui.px(LOGO_W), Ui.px(LOGO_H));
        else Pics.prefetch(pic, 1280, 720, Pics.CROP);
        if (Tint.known(pic) == 0) Pics.prefetch(pic, Tint.W, Tint.H, Pics.CROP);
    }

    private void prepare(Card c) {
        if (c.size == 0) {
            float size = Ui.f(118f);
            nameP.setTextSize(size);
            while (size > Ui.f(64f) && nameP.measureText(c.name) > Ui.f(1340f)) {
                size *= 0.94f;
                nameP.setTextSize(size);
            }
            c.size = size;
        }
        if (c.tint == 0) {
            c.tint = Tint.of(c.picture.isEmpty() ? c.logo : c.picture, again);
            if (c.tint != 0 && c.picture.isEmpty()) c.art = null;
        }
        float w = Ui.f(W), h = Ui.f(H);
        if (!c.picture.isEmpty()) {
            Bitmap b = Pics.get(c.picture, 1280, 720, Pics.CROP, again);
            if (b != null && b != c.made) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float s = Math.max(w / b.getWidth(), h / b.getHeight());
                Matrix m = new Matrix();
                m.setScale(s, s);
                m.postTranslate((w - b.getWidth() * s) / 2f, (h - b.getHeight() * s) * 0.32f);
                sh.setLocalMatrix(m);
                p.setShader(sh);
                b.prepareToDraw();
                c.art = p;
                c.made = b;
            }
        } else if (c.art == null) {
            // A channel: its logo on its own colour, deep at the right.
            int tint = c.tint != 0 ? c.tint : 0xFF3A3F55;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            p.setShader(new LinearGradient(0, 0, w, h, Cine.shade(tint, 0.7f, 0.46f), Cine.shade(tint, 0.7f, 0.16f), Shader.TileMode.CLAMP));
            c.art = p;
            if (!c.logo.isEmpty()) Logos.prefetch(c.logo, Ui.px(LOGO_W), Ui.px(LOGO_H));
        }
        invalidate();
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    boolean key(int code) {
        Section s = current();
        switch (area) {
            case TABS:
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    // Up past the sections: 07's tab bar (the old mark's menu).
                    if (host != null) host.menu();
                } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (!sections.isEmpty()) {
                        tab = Math.max(0, tab - 1);
                        toSection(tab);
                    }
                    invalidate();
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    tab = Math.min(sections.size() - 1, tab + 1);
                    if (tab >= 0) toSection(tab);
                    invalidate();
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && card() != null) {
                    if (tab < 0) {
                        tab = section;
                    }
                    area = CARD;
                    told();
                }
                return true;
            case CARD:
                if (s == null) return true;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && s.at > 0) to(s.at - 1);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && s.at < s.cards.size() - 1) to(s.at + 1);
                else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    area = TABS;
                    tab = section;
                    invalidate();
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    Card c = card();
                    if (c != null && !c.strip.isEmpty()) {
                        area = STRIP;
                        stripX.to(stripTarget(c));
                        invalidate();
                    }
                }
                return true;
            default: {
                Card c = card();
                if (c == null || c.strip.isEmpty()) {
                    area = CARD;
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && c.stripAt > 0) c.stripAt--;
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && c.stripAt < c.strip.size() - 1) c.stripAt++;
                else if (code == KeyEvent.KEYCODE_DPAD_UP) area = CARD;
                stripX.to(stripTarget(c));
                invalidate();
                return true;
            }
        }
    }

    /** To another card of the section: it slides in from the side it is on. */
    private void to(int i) {
        Section s = current();
        s.at = i;
        pos.to(i);
        told();
    }

    private void toSection(int i) {
        if (i == section || i >= sections.size()) return;
        section = i;
        Section s = current();
        pos.jump(s.at);
        swap.jump(0f);
        swap.to(1f);
        if (host != null) host.section(i);
        told();
    }

    private void told() {
        prepareNear();
        Card c = card();
        if (c != null) stripX.jump(stripTarget(c));
        if (host != null && c != null) host.focused(c);
        invalidate();
    }

    void ok() {
        if (area == TABS) {
            if (tab < 0) {
                if (host != null) host.menu();
            } else if (card() != null) {
                area = CARD;
                told();
            }
            return;
        }
        Card c = card();
        if (c == null || host == null) return;
        if (area == STRIP && c.stripAt < c.strip.size()) host.chosen(c, c.strip.get(c.stripAt));
        else host.chosen(c);
    }

    /** The remote down from 07's tab bar: on the sections, at the one shown. */
    void fromAbove() {
        area = TABS;
        tab = sections.isEmpty() ? -1 : Math.max(0, section);
        invalidate();
    }

    /** Back: from the strip to the card, from the card to the sections (true); from the sections, the menu (false). */
    boolean back() {
        if (area == STRIP) {
            area = CARD;
            invalidate();
            return true;
        }
        if (area == CARD) {
            area = TABS;
            tab = section;
            invalidate();
            return true;
        }
        return false;
    }

    /** Previews and tests: the remote on a section, a card, and where (the strip at a tile; -1 where the strip starts). */
    void focus(int sectionIndex, int cardIndex, int where, int stripIndex) {
        if (sectionIndex < 0 || sectionIndex >= sections.size()) return;
        section = sectionIndex;
        tab = section;
        Section s = current();
        s.at = Math.max(0, Math.min(cardIndex, s.cards.size() - 1));
        pos.jump(s.at);
        swap.jump(1f);
        // The card told first, on the card, so its strip is made starting where it should (host.focused); then the
        // remote goes where asked (along the strip to stripIndex, -1 where the strip starts).
        area = CARD;
        told();
        area = where;
        Card c = card();
        if (c != null && where == STRIP) {
            if (stripIndex >= 0) c.stripAt = Math.max(0, Math.min(stripIndex, c.strip.size() - 1));
            stripX.jump(stripTarget(c));
        }
        invalidate();
    }

    /** Previews: the slide from one card to the next caught part way (at: between two cards' places). */
    void between(float at) {
        Section s = current();
        if (s == null) return;
        s.at = Math.min(s.cards.size() - 1, (int) Math.ceil(at));
        prepareNear();
        Card c = card();
        if (c != null && host != null) host.focused(c);
        pos.jump(at);
        swap.jump(1f);
        invalidate();
    }

    /** A section's place by its tag, -1 when it isn't shown. */
    int sectionOf(Object tag) {
        for (int i = 0; i < sections.size(); i++) if (sections.get(i).tag.equals(tag)) return i;
        return -1;
    }

    private float pitch() {
        return TILE_W + TILE_GAP;
    }

    private float stripTarget(Card c) {
        return Math.max(0, c.stripAt - 3) * pitch();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float cw = Ui.f(W), ch = Ui.f(H);
        shadeLeft.setShader(new LinearGradient(0, 0, cw * 0.66f, 0, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        shadeFoot.setShader(new LinearGradient(0, ch * 0.42f, 0, ch, 0x00000000, 0xC0000000, Shader.TileMode.CLAMP));
        tileFoot.setShader(new LinearGradient(0, Ui.f(TILE_H * 0.3f), 0, Ui.f(TILE_H), 0x00000000, 0xD9000000, Shader.TileMode.CLAMP));
        measureTabs();
        for (Section s : sections) for (Card c : s.cards) {
            c.art = null;
            c.made = null;
            c.size = 0;
        }
        prepareNear();
    }

    @Override protected void onDraw(Canvas c) {
        boolean moving = pos.step();
        moving |= swap.step();
        moving |= stripX.step();
        Section s = current();
        Card on = card();
        Object focus = area == STRIP && on != null && on.stripAt < on.strip.size() ? on.strip.get(on.stripAt) : null;
        moving |= lift.step(focus);
        int w = getWidth(), h = getHeight();

        // The page: the colour of the card the remote is on (between two as it slides), a deeper block of it behind.
        int tint = 0;
        if (s != null && !s.cards.isEmpty()) {
            int a = Math.max(0, Math.min(s.cards.size() - 1, (int) Math.floor(pos.value())));
            int b = Math.min(s.cards.size() - 1, a + 1);
            int ta = s.cards.get(a).tint, tb = s.cards.get(b).tint;
            if (ta == 0) ta = 0xFF2C2F3D;
            if (tb == 0) tb = 0xFF2C2F3D;
            tint = Cine.mix(ta, tb, pos.value() - a);
        }
        ground.setColor(Cine.shade(tint == 0 ? 0xFF2C2F3D : tint, 0.55f, 0.21f));
        c.drawRect(0, 0, w, h, ground);
        block.setColor(Cine.shade(tint == 0 ? 0xFF2C2F3D : tint, 0.55f, 0.12f));
        glass.setColor(Cine.shade(tint == 0 ? 0xFF2C2F3D : tint, 0.45f, 0.30f));
        c.drawRect(w * 0.45f, Ui.f(T + H * 0.5f), w, h, block);
        c.drawRect(0, Ui.f(T + H + 40f), w, h, block);

        drawTabs(c);
        if (s == null || s.cards.isEmpty()) {
            drawNote(c);
            if (moving) postInvalidateOnAnimation();
            return;
        }
        // The cards either side of where the slide is, in the one frame: the one further from its place underneath and
        // whole, the nearer over it fading in, so one picture cross-fades into the next (the ground never shows through).
        float k = swap.value(), p = pos.value();
        int first = Math.max(0, (int) Math.floor(p)), last = Math.min(s.cards.size() - 1, (int) Math.ceil(p));
        int under = -1, over = -1;
        for (int i = first; i <= last; i++) {
            if (Math.abs(i - p) >= 1f) continue;
            if (over < 0 || Math.abs(i - p) < Math.abs(over - p)) {
                under = over;
                over = i;
            } else {
                under = i;
            }
        }
        if (under >= 0) drawCard(c, s.cards.get(under), under - p, k, k);
        if (over >= 0) drawCard(c, s.cards.get(over), over - p, under >= 0 ? (1f - Math.abs(over - p)) * k : k, k);
        // The strip, rising in under the card once it has arrived.
        if (on != null && !on.strip.isEmpty()) {
            float in = Math.max(0f, Math.min(1f, 1f - Math.abs(pos.value() - s.at) * 5f)) * k;
            if (in > 0.01f) {
                c.save();
                c.clipRect(Ui.f(L), 0, Ui.f(L + W), getHeight());
                drawStrip(c, on, in);
                c.restore();
            }
        }
        if (moving) postInvalidateOnAnimation();
    }

    private void drawTabs(Canvas c) {
        float cy = Ui.f(MARK_Y);
        // Homefield's mark is 07's shell's now (TabBar, over the page); the sections sit under it.
        float base = Cine.mid(tabP, cy);
        for (int i = 0; i < sections.size() && i < tabX.length; i++) {
            boolean here = area == TABS && i == tab;
            if (here) {
                r.set(tabX[i] - Ui.f(20f), cy - Ui.f(27f), tabX[i] + tabW[i] + Ui.f(20f), cy + Ui.f(27f));
                c.drawRoundRect(r, Ui.f(27f), Ui.f(27f), white);
            }
            c.drawText(sections.get(i).name, tabX[i], base, here ? tabInk : i == section ? tabOn : tabP);
        }
    }

    private void drawNote(Canvas c) {
        float x = Ui.f(L + NAME_X);
        c.drawText(noteHead, x, Ui.f(T + H * 0.5f), headP);
        if (!note.isEmpty()) c.drawText(note, x, Ui.f(T + H * 0.5f + 70f), noteP);
    }

    /**
     * A card at d (0: in place; -1 gone left, 1 waiting right) in the card's frame: its picture at alpha (the section's
     * fade k, and the cross-fade), its name and facts sliding with d and only while it is the nearer (one at a time).
     */
    private void drawCard(Canvas c, Card card, float d, float alpha, float k) {
        float cw = Ui.f(W), ch = Ui.f(H), radius = Ui.f(R);
        if (alpha <= 0.01f) return;
        int a = Math.round(255 * alpha);
        int words = Math.round(255 * Math.max(0f, 1f - 2f * Math.abs(d)) * k);
        float x = Ui.f(L), y = Ui.f(T) + (1f - k) * Ui.f(36f);
        c.save();
        c.translate(x, y);
        r.set(0, 0, cw, ch);
        Paint art = card.art != null ? card.art : place;
        art.setAlpha(a);
        c.drawRoundRect(r, radius, radius, art);
        if (card.picture.isEmpty() && !card.logo.isEmpty()) {
            // A channel's logo, large, on the right of its colour (on its tile: Logos).
            float lw = Ui.f(LOGO_W), lh = Ui.f(LOGO_H);
            logoBox.set(cw * 0.76f - lw / 2f, ch * 0.27f - lh / 2f, cw * 0.76f + lw / 2f, ch * 0.27f + lh / 2f);
            Logos.draw(c, card.logo, logoBox, again, a);
        }
        shadeLeft.setAlpha(a);
        shadeFoot.setAlpha(a);
        c.drawRoundRect(r, radius, radius, shadeLeft);
        c.drawRoundRect(r, radius, radius, shadeFoot);
        // Its name and facts, sliding in from the side it comes from as the picture changes, kept within the card.
        if (words > 2) {
            c.clipRect(0, 0, cw, ch);
            float tx = Ui.f(NAME_X) + d * cw * 0.16f, base = ch * NAME_Y;
            nameP.setTextSize(card.size > 0 ? card.size : Ui.f(118f));
            nameP.setAlpha(words);
            c.drawText(card.name, tx, base, nameP);
            drawFacts(c, card, tx, base + Ui.f(58f), words);
        }
        c.restore();
    }

    private void drawFacts(Canvas c, Card card, float x, float base, int a) {
        factP.setAlpha(Math.round(a * 0.92f));
        sepP.setAlpha(Math.round(a * 0.45f));
        if (card.live) {
            float lw = liveP.measureText("LIVE") + Ui.f(18f);
            r.set(x, base - Ui.f(24f), x + lw, base + Ui.f(6f));
            red.setAlpha(a);
            c.drawRoundRect(r, Ui.f(6f), Ui.f(6f), red);
            liveP.setAlpha(a);
            c.drawText("LIVE", x + Ui.f(9f), Cine.mid(liveP, r.centerY()), liveP);
            x += lw + Ui.f(18f);
        }
        for (int i = 0; i < card.facts.length; i++) {
            if (i > 0) {
                c.drawText("|", x, base, sepP);
                x += sepP.measureText("|") + Ui.f(16f);
            }
            c.drawText(card.facts[i], x, base, factP);
            x += factP.measureText(card.facts[i]) + Ui.f(16f);
        }
        if (card.stars > 0) {
            if (card.facts.length > 0) {
                c.drawText("|", x, base, sepP);
                x += sepP.measureText("|") + Ui.f(18f);
            }
            float sr = Ui.f(11f);
            star.setAlpha(a);
            starOff.setAlpha(Math.round(a * 0.35f));
            for (int i = 0; i < 5; i++) {
                c.save();
                c.translate(x + sr + i * sr * 2.3f, base - Ui.f(10f));
                c.scale(sr, sr);
                c.drawPath(starPath, i < card.stars ? star : starOff);
                c.restore();
            }
        }
        if (card.progress >= 0f) {
            float bw = Ui.f(300f), by = base + Ui.f(24f);
            track.setAlpha(Math.round(a * 0.35f));
            red.setAlpha(a);
            c.drawRect(Ui.f(NAME_X), by, Ui.f(NAME_X) + bw, by + Ui.f(5f), track);
            c.drawRect(Ui.f(NAME_X), by, Ui.f(NAME_X) + bw * Math.max(0.03f, card.progress), by + Ui.f(5f), red);
        }
    }

    /** The strip along the card's foot: its tiles small and dimmed, the remote's grown with its name, the rest moved over. */
    private void drawStrip(Canvas c, Card card, float in) {
        float tw = Ui.f(TILE_W), th = Ui.f(TILE_H), gap = Ui.f(TILE_GAP), bottom = Ui.f(T + H - 62f + TILE_H);
        float x = Ui.f(L + NAME_X) - Ui.f(stripX.value());
        float right = Ui.f(L + W) - Ui.f(24f);
        boolean inStrip = area == STRIP;
        for (int i = 0; i < card.strip.size(); i++) {
            Tile t = card.strip.get(i);
            float up = inStrip ? lift.of(t) : 0f;
            float k = 1f + (GROW - 1f) * up;
            float w = tw * k, hh = th * k;
            if (x > right) break;
            if (x + w >= Ui.f(L)) {
                float stagger = Math.max(0f, Math.min(1f, in * 1.6f - Math.max(0, i - (int) (stripX.value() / pitch())) * 0.12f));
                int a = Math.round(255 * stagger * (inStrip && up < 0.5f ? 0.6f : inStrip ? 1f : 0.88f));
                float rise = (1f - stagger) * Ui.f(26f);
                drawTile(c, t, x, bottom - hh + rise, w, hh, up, a);
            }
            x += w + gap;
        }
    }

    private void drawTile(Canvas c, Tile t, float x, float y, float w, float h, float up, int a) {
        if (a <= 0) return;
        c.save();
        c.translate(x, y);
        r.set(0, 0, w, h);
        float rad = Ui.f(8f);
        if (!t.picture.isEmpty()) {
            Bitmap b = Pics.get(t.picture, Math.round(Ui.f(TILE_W * GROW)), Math.round(Ui.f(TILE_H * GROW)), Pics.CROP, redraw);
            if (b != null) {
                bmp.setAlpha(a);
                c.drawBitmap(b, null, r, bmp);
            } else {
                place.setAlpha(a);
                c.drawRoundRect(r, rad, rad, place);
            }
            c.save();
            c.scale(w / Ui.f(TILE_W), h / Ui.f(TILE_H));
            tileFoot.setAlpha(a);
            c.drawRect(0, 0, Ui.f(TILE_W), Ui.f(TILE_H), tileFoot);
            c.restore();
            labelP.setAlpha(a);
            boolean named = up > 0.5f && !t.name.isEmpty();
            c.drawText(t.label, Ui.f(14f), h - Ui.f(named ? 40f : 14f), labelP);
            if (named) {
                tileName.setAlpha(a);
                c.drawText(t.grown, Ui.f(14f), h - Ui.f(14f), tileName);
            }
        } else {
            // A programme: its time and name on glass.
            glass.setAlpha(a);
            c.drawRoundRect(r, rad, rad, glass);
            timeP.setAlpha(a);
            c.drawText(t.label, Ui.f(16f), Ui.f(46f), timeP);
            tileName.setAlpha(a);
            c.drawText(up > 0.5f ? t.grown : t.small, Ui.f(16f), h - Ui.f(16f), tileName);
        }
        if (t.progress >= 0f) {
            float bh = Ui.f(5f);
            track.setAlpha(Math.round(a * 0.35f));
            c.drawRect(0, h - bh, w, h, track);
            red.setAlpha(a);
            c.drawRect(0, h - bh, w * Math.max(0.03f, t.progress), h, red);
        }
        if (up > 0.02f) {
            ring.setAlpha(Math.round(a * Math.min(1f, up)));
            c.drawRoundRect(r, rad, rad, ring);
            ring.setAlpha(255);
        }
        c.restore();
    }
}
