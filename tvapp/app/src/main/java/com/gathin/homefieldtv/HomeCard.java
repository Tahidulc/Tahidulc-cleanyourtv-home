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

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home in the 07 look (homefield-07-real 01 to 03, clip 1), made for the remote (design px, 1920 x 1080). Under 07's tab
 * bar, Homefield's sections as pills (On now, Continue watching, Your TorBox, New films, New TV shows, Sport on now,
 * Favourites; the one shown white); then one big rounded card (90, 214, 1740 x 760, rounded 46) for what the remote is
 * on: its picture filling it, cropped round its faces toward the right (Pics.FACE: found once a picture), its words on
 * the left over a dark fade that ends before the faces (and a foot fade that starts under them: never a scrim over a
 * face), its buttons, and along its foot a strip: a channel's later programmes then what's on now on the next channels,
 * a series' episodes (every season's, its label following the season), a film's like titles. The page behind takes the
 * picture's colour (Ground: its blurred copy). Dots under the card show where the remote is among the section's cards.
 *
 * <p>Left and Right go from card to card, on into the next section's (its pill following, as clip 1): the new card
 * cross-fades in over 700 ms, its picture settling from 1.04, its words rising in 150 ms after and its strip 350 ms
 * after. Up goes to the sections, and from them to the tab bar (Host.menu); Down goes into the strip, where the tile
 * the remote is on rises with a white edge. OK on a card is its first button (Watch Live, Resume, Play), MENU its second
 * (Start Over, From the Start, the trailer, My List); OK on a tile plays it, opens it or sets its reminder. Nothing is
 * made while a frame is drawn: each card's words are laid out when the remote comes near it (prepare).
 */
final class HomeCard extends View {
    interface Host {
        /** The remote moved to another section (its place). */
        void section(int index);

        /** The remote rests on a card (its details and strip may be wanted). */
        void focused(Card card);

        /** OK on a card: its first button. */
        void chosen(Card card);

        /** OK on a tile of a card's strip. */
        void chosen(Card card, Tile tile);

        /** Up from the sections, or Back there: the tab bar. */
        void menu();

        /** MENU on a card: its second button. */
        void second(Card card);
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

    /** A card: what it is (a channel, a title, a place to carry on from), its words, picture and strip. */
    static final class Card {
        final Object item;
        final String key, name, picture, logo;
        final boolean live;
        final float progress;
        List<Tile> strip = Collections.emptyList();
        int stripAt;
        /** Its kicker ("On now · 102 BBC Two", "Continue watching · Season 2, Episode 3"), TMDB's logo ("" for its name). */
        String kick = "", mark = "";
        /** What it's about: a lead in white ("E3 · The Path.") and the story after it. */
        String lead = "", plot = "";
        /** Its facts line (certificate, rating, years, length, genres, chips). */
        final Hero07.Facts facts = new Hero07.Facts();
        /** A channel's card: its logo in the kicker, LIVE, its HD or 4K chip. */
        boolean channel, liveBadge;
        String quality = "";
        /** Its buttons: the first (OK) with quieter words after it ("21 min left"), the second (MENU), My List's round one. */
        String button = "", after = "", second = "";
        int buttonIcon = Icons07.PLAY, secondIcon = -1;
        boolean plus, plusOn;
        /** The strip's label ("LATER ON BBC TWO, AND ON NOW", "MORE LIKE THIS"; a series' follows its tiles' seasons). */
        String stripLabel = "";
        /** A channel's programme on now (its picture asked of ProgrammePictures), -1 for none. */
        Channel ch;
        Guide.Schedule schedule;
        int at = -1;
        /** The match on it, its score held back to the channel's delay ("" for none): sides, score, clock, crests, a red. */
        String home = "", away = "", score = "", minute = "", homeCrest = "", awayCrest = "";
        boolean red;

        // Made near the remote (prepare), never while drawing.
        String art = "";
        StaticLayout about;
        boolean prepared;
        Bitmap facesFor;
        float fadeEnd, footTop;

        Card(Object item, String key, String name, String picture, String logo, boolean live, float progress) {
            this.item = item;
            this.key = key;
            this.name = name == null ? "" : name;
            this.picture = picture == null ? "" : picture;
            this.logo = logo == null ? "" : logo;
            this.live = live;
            this.progress = progress;
        }
    }

    /** A tile of a card's strip: an episode, a programme, a like title. */
    static final class Tile {
        final Object item;
        final String picture, name;
        final float progress;
        /** Its chip on the picture ("22:00", "NOW · 301"), its line under its name ("BBC Two", "59 min", "2013 · ★ 7.7"). */
        String chip = "", sub = "";
        /** The strip's label while it is the tile in view ("SEASON 2"; "" keeps the card's). */
        String heading = "";
        /** A poster (a film's like titles: 106 x 159), else a wide tile (260 x 146). */
        boolean poster;
        /** A programme: its channel and place in the guide (its picture through PicTile), -1 for none. */
        Channel ch;
        Guide.Schedule schedule;
        int at = -1;
        String small = "", line = "";

        Tile(Object item, String picture, String name, float progress) {
            this.item = item;
            this.picture = picture == null ? "" : picture;
            this.name = name == null ? "" : name;
            this.progress = progress;
        }
    }

    /** Where the remote is: the sections under the tab bar, the card, its strip. */
    static final int TABS = 0, CARD = 1, STRIP = 2;
    /** The card (design px, the mock-ups' .hm-card), its words' corner, the sections' row, the strip, the dots. */
    static final float L = 90f, T = 214f, W = 1740f, H = 760f, PAD = 64f, WORDS_TOP = 58f, WORDS_W = 920f, ABOUT_W = 860f,
            SECTIONS_Y = 163f, SECTION_H = 50f, SECTION_GAP = 8f, TILE_W = 260f, TILE_H = 146f, TILE_GAP = 22f,
            POSTER_W = 106f, POSTER_H = 159f, STRIP_FOOT = 44f, DOTS_Y = 998.5f, LOGO_W = 540f, LOGO_H = 150f, NAME = 66f,
            NAME_W = 1000f;
    /** The cross-fade, the words' and the strip's delays and lengths, the picture settling from 1.04 (ms). */
    static final long FADE_MS = 700, WORDS_DELAY = 150, WORDS_MS = 500, STRIP_DELAY = 350, STRIP_MS = 550, ZOOM_MS = 1200;
    static final float ZOOM = 1.04f;
    /** The dots shown at most (a long section's slide with the remote). */
    static final int DOTS = 9;

    private final Host host;
    private final List<Section> sections = new ArrayList<>();
    private int section, area = CARD;
    /** Whether the page has the remote (false while it is up on the tab bar: nothing lifted). */
    private boolean active = true;
    private String note = "", noteHead = "";
    private final Ground ground;
    /** The card on show, the one it is leaving, when it changed; previews freeze the change part way (frozen >= 0). */
    private Card shown, leaving;
    private long changedAt = -100_000L;
    private float frozen = -1f;
    private final Hero07.Mark markShown = new Hero07.Mark(NAME), markLeaving = new Hero07.Mark(NAME);
    private Hero07.Mark mark = markShown, markOld = markLeaving;
    private final Lift07 lift = new Lift07(), tabLift = new Lift07();
    private final Glide stripX = new Glide(Look07.SCROLL_MS);
    private final Shaders shaders = new Shaders();
    private final PicTile tiles;
    private final Frost glass = new Frost(Look07.GLASS);

    private final TextPaint kickP, factsP, aboutP, labelP, chipP, nameP, subP, liveP, scoreP, sideP, minuteP, headP, noteP;
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            leftFade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), footFade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
            bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF(), box = new RectF(), face = new RectF();
    private final Runnable redraw = this::invalidate, again = this::prepareNear;

    HomeCard(Context c, Host host) {
        super(c);
        this.host = host;
        Cine.init(c);
        ground = new Ground(this);
        tiles = new PicTile(this);
        tiles.colour(0xFF141A2E);
        kickP = Type.paint(21f, Type.SEMIBOLD, Look07.L2);
        factsP = Type.paint(22f, Type.MEDIUM, Look07.L2);
        aboutP = Type.paint(22f, Type.REGULAR, Look07.L2);
        labelP = Type.label(16f, Look07.L2);
        chipP = Type.clock(15f, Type.BOLD, Look07.L1);
        nameP = Type.paint(18f, Type.SEMIBOLD, Look07.L1);
        subP = Type.paint(15f, Type.REGULAR, Look07.L3);
        liveP = Type.paint(16f, Type.BOLD, Look07.L1);
        liveP.setLetterSpacing(0.06f);
        scoreP = Type.clock(34f, Type.BOLD, Look07.L1);
        sideP = Type.paint(19f, Type.SEMIBOLD, Look07.L2);
        sideP.setLetterSpacing(0.04f);
        minuteP = Type.clock(19f, Type.SEMIBOLD, 0xFFFFB3AE);
        headP = Type.paint(Type.H2, Type.BOLD, Look07.L1);
        noteP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2);
        frame.setColor(Look07.CARD_FRAME);
        edge.setStyle(Paint.Style.STROKE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Look07.L1);
        // The words' fade from the left (rgba(5,6,12): .9, .62 at 32 %, .08 at 62 %, clear by 78 %) and the strip's from
        // the foot (.9, .45 at 30 %, clear by 52 %), each over a unit box stretched to where it must end.
        leftFade.setShader(new LinearGradient(0, 0, 1, 0, new int[]{0xE605060C, 0x9E05060C, 0x1405060C, 0x0005060C},
                new float[]{0f, 0.41f, 0.795f, 1f}, Shader.TileMode.CLAMP));
        footFade.setShader(new LinearGradient(0, 0, 0, 1, new int[]{0x0005060C, 0x7305060C, 0xE605060C},
                new float[]{0f, 0.423f, 1f}, Shader.TileMode.CLAMP));
        Shadows.prepare(Look07.SH_HERO, Look07.R_HERO);
        Shadows.prepare(Look07.SH_BUTTON, 35f);
        Shadows.prepare(Look07.SH_PILL, Look07.R_PILL);
        Shadows.prepare(Look07.SH_TILE, Look07.R_TILE);
        Shadows.prepare(Look07.SH_ROW, Look07.R_TILE);
        Pills.prepare();
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
        String shownKey = shown == null ? null : shown.key;
        sections.clear();
        sections.addAll(list);
        int keep = 0;
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            s.at = 0;
            if (keys[i] != null) for (int k = 0; k < s.cards.size(); k++) if (s.cards.get(k).key.equals(keys[i])) s.at = k;
            if (s.tag.equals(was)) keep = i;
        }
        section = keep;
        if (sections.isEmpty()) area = TABS;
        Card c = card();
        if (c == null) {
            shown = leaving = null;
            ground.set("");
        } else if (shownKey != null && shownKey.equals(c.key)) {
            // The same card made again (each minute): in place, any cross-fade going on carrying on.
            shown = c;
            setMark(mark, c);
        } else {
            show(c, shownKey == null);
        }
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
    void strip(Card c, List<Tile> list, int at) {
        c.strip = list == null ? Collections.emptyList() : list;
        float tw = Ui.f(TILE_W - 4f), pw = Ui.f(POSTER_W - 4f);
        for (Tile t : c.strip) {
            t.small = Ui.fit(t.name, nameP, t.poster ? pw : tw);
            t.line = Ui.fit(t.sub, subP, t.poster ? pw : tw);
        }
        if (!(area == STRIP && c == card())) c.stripAt = Math.max(0, Math.min(at, c.strip.size() - 1));
        if (c == card()) stripX.jump(stripTarget(c));
        invalidate();
    }

    /** A card's words or picture changed (its details came): laid out again. */
    void refresh(Card c) {
        if (c == null) return;
        c.prepared = false;
        c.about = null;
        prepare(c);
        if (c == shown) setMark(mark, c);
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

    /** The page has the remote (true), or the tab bar above it has (nothing on the page lifted). */
    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    /** The card the remote is on comes in (fresh: the first, at once). */
    private void show(Card c, boolean fresh) {
        if (c == shown) return;
        leaving = fresh ? null : shown;
        shown = c;
        changedAt = fresh || Exec.inline ? -100_000L : SystemClock.uptimeMillis();
        frozen = -1f;
        Hero07.Mark t = markOld;
        markOld = mark;
        mark = t;
        setMark(mark, c);
        prepare(c);
        ground.set(c.art.isEmpty() ? c.logo : c.art);
        stripX.jump(stripTarget(c));
    }

    private void setMark(Hero07.Mark m, Card c) {
        m.set(c.name, c.mark, Ui.f(LOGO_W), Ui.f(LOGO_H), Ui.f(NAME_W));
    }

    /** The cards near the remote made ready: pictures asked for, words laid out (never while drawing). */
    private void prepareNear() {
        Section s = current();
        if (s == null) return;
        for (int k = Math.max(0, s.at - 1); k <= Math.min(s.cards.size() - 1, s.at + 1); k++) prepare(s.cards.get(k));
        // The next sections' cards the remote would come to, read after what is on screen (Pics.prefetch).
        if (s.at == s.cards.size() - 1 && section < sections.size() - 1) ahead(first(section + 1));
        if (s.at == 0 && section > 0) ahead(last(section - 1));
        if (s.at + 2 < s.cards.size()) ahead(s.cards.get(s.at + 2));
        // A programme's picture that arrived meanwhile: the ground takes it too.
        if (shown != null) ground.set(shown.art.isEmpty() ? shown.logo : shown.art);
    }

    private Card first(int si) {
        Section s = sections.get(si);
        return s.cards.isEmpty() ? null : s.cards.get(0);
    }

    private Card last(int si) {
        Section s = sections.get(si);
        return s.cards.isEmpty() ? null : s.cards.get(s.cards.size() - 1);
    }

    private int picW() {
        return Math.min(Ui.px(W), 1280);
    }

    private int picH() {
        return Math.round(picW() * H / W);
    }

    private void ahead(Card c) {
        if (c == null) return;
        String u = c.art.isEmpty() ? c.picture : c.art;
        if (!u.isEmpty()) Pics.prefetch(u, picW(), picH(), Pics.FACE);
        else if (!c.logo.isEmpty()) Pics.prefetch(c.logo, picW(), picH(), Pics.LOGO_ART);
    }

    private void prepare(Card c) {
        if (c.art.isEmpty() && c.channel && c.at >= 0) {
            String u = ProgrammePictures.get(getContext(), c.ch, c.schedule, c.at, again);
            if (u != null && !u.isEmpty()) c.art = u;
        } else if (c.art.isEmpty()) {
            c.art = c.picture;
        }
        if (!c.prepared) {
            c.prepared = true;
            CharSequence words = c.plot;
            if (!c.lead.isEmpty()) {
                android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(c.lead);
                b.setSpan(new android.text.style.ForegroundColorSpan(Look07.L1), 0, c.lead.length(), 0);
                b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, c.lead.length(), 0);
                if (!c.plot.isEmpty()) b.append(' ').append(c.plot);
                words = b;
            }
            c.about = words.length() == 0 ? null : Hero07.lines(words, aboutP, Ui.px(ABOUT_W), 2, Ui.f(22f * 1.45f));
            if (!c.mark.isEmpty()) Pics.prefetch(c.mark, Ui.px(LOGO_W), Ui.px(LOGO_H), Pics.FIT);
        }
        String u = c.art;
        if (!u.isEmpty()) Pics.get(u, picW(), picH(), Pics.FACE, redraw);
        else if (!c.logo.isEmpty()) Pics.get(c.logo, picW(), picH(), Pics.LOGO_ART, redraw);
        invalidate();
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    boolean key(int code) {
        Section s = current();
        switch (area) {
            case TABS:
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (host != null) host.menu();
                } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (section > 0) toSection(section - 1, false);
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (section < sections.size() - 1) toSection(section + 1, false);
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && card() != null) {
                    area = CARD;
                    told();
                }
                return true;
            case CARD:
                if (s == null) return true;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (s.at > 0) to(s.at - 1);
                    else if (section > 0) toSection(section - 1, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (s.at < s.cards.size() - 1) to(s.at + 1);
                    else if (section < sections.size() - 1) toSection(section + 1, false);
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    area = TABS;
                    invalidate();
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    Card c = card();
                    if (c != null && !c.strip.isEmpty()) {
                        area = STRIP;
                        stripX.to(stripTarget(c));
                        invalidate();
                    }
                } else if (code == KeyEvent.KEYCODE_MENU) {
                    Card c = card();
                    if (c != null && host != null) host.second(c);
                } else {
                    return false;
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
                else if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                stripX.to(stripTarget(c));
                invalidate();
                return true;
            }
        }
    }

    /** To another card of the section. */
    private void to(int i) {
        current().at = i;
        told();
    }

    /** To another section: at its card the remote was on (fromRight: at its last, coming back along the cards). */
    private void toSection(int i, boolean fromRight) {
        if (i == section || i < 0 || i >= sections.size()) return;
        section = i;
        Section s = current();
        if (area == CARD) s.at = fromRight ? Math.max(0, s.cards.size() - 1) : 0;
        if (host != null) host.section(i);
        told();
    }

    private void told() {
        Card c = card();
        if (c != null) show(c, false);
        prepareNear();
        if (host != null && c != null) host.focused(c);
        invalidate();
    }

    void ok() {
        if (area == TABS) {
            if (card() != null) {
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
        invalidate();
    }

    /** Back: from the strip to the card, from the card to the sections (true); from the sections, the tab bar (false). */
    boolean back() {
        if (area == STRIP) {
            area = CARD;
            invalidate();
            return true;
        }
        if (area == CARD) {
            area = TABS;
            invalidate();
            return true;
        }
        return false;
    }

    /** Previews and tests: the remote on a section, a card, and where (the strip at a tile; -1 where the strip starts). */
    void focus(int sectionIndex, int cardIndex, int where, int stripIndex) {
        if (sectionIndex < 0 || sectionIndex >= sections.size()) return;
        section = sectionIndex;
        Section s = current();
        s.at = Math.max(0, Math.min(cardIndex, s.cards.size() - 1));
        area = CARD;
        Card c = card();
        if (c != null) show(c, true);
        prepareNear();
        if (host != null && c != null) host.focused(c);
        area = where;
        if (c != null && where == STRIP) {
            if (stripIndex >= 0) c.stripAt = Math.max(0, Math.min(stripIndex, c.strip.size() - 1));
            stripX.jump(stripTarget(c));
        }
        invalidate();
    }

    /** Previews: the cross-fade from one card to the next caught part way (at: between two cards' places). */
    void between(float at) {
        Section s = current();
        if (s == null || s.cards.isEmpty()) return;
        int a = Math.max(0, Math.min(s.cards.size() - 1, (int) Math.floor(at))), b = Math.min(s.cards.size() - 1, a + 1);
        s.at = a;
        show(s.cards.get(a), true);
        s.at = b;
        show(s.cards.get(b), false);
        frozen = Math.max(0f, Math.min(1f, at - a));
        prepareNear();
        if (host != null) host.focused(s.cards.get(b));
        invalidate();
    }

    /** A section's place by its tag, -1 when it isn't shown. */
    int sectionOf(Object tag) {
        for (int i = 0; i < sections.size(); i++) if (sections.get(i).tag.equals(tag)) return i;
        return -1;
    }

    private float pitch(Card c) {
        return (isPosters(c) ? POSTER_W : TILE_W) + TILE_GAP;
    }

    private static boolean isPosters(Card c) {
        return !c.strip.isEmpty() && c.strip.get(0).poster;
    }

    /** The strip's offset (design px): the remote's tile kept fourth from the left at most, the strip's end in reach. */
    private float stripTarget(Card c) {
        float p = pitch(c), room = W - 2 * PAD;
        float max = Math.max(0f, c.strip.size() * p - TILE_GAP - room);
        return Math.max(0f, Math.min(max, (c.stripAt - 3) * p));
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        for (Section s : sections) for (Card c : s.cards) {
            c.prepared = false;
            c.facesFor = null;
        }
        if (shown != null) setMark(mark, shown);
        prepareNear();
    }

    /** How far the change to the card on show has come (0 to 1), from its start delay over its length. */
    private float since(long delay, long length, long now) {
        if (frozen >= 0f) return Math.max(0f, Math.min(1f, (frozen * FADE_MS - delay) / (float) length));
        if (changedAt < 0) return 1f;
        return Math.max(0f, Math.min(1f, (now - changedAt - delay) / (float) length));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean moving = ground.draw(c, w, h);
        long now = SystemClock.uptimeMillis();
        Card on = card();
        Object stripFocus = active && area == STRIP && on != null && on.stripAt < on.strip.size() ? on.strip.get(on.stripAt) : null;
        if (area == CARD && on != null && on.item instanceof com.gathin.homefieldtv.engine.Resume.Point && on.stripAt < on.strip.size()) {
            // Continue watching: the episode to carry on with stands up from the strip while the remote is on the card.
            stripFocus = on.strip.get(on.stripAt);
        }
        moving |= lift.step(stripFocus);
        moving |= tabLift.step(active && area == TABS ? current() : null);
        moving |= stripX.step();
        drawSections(c);
        if (shown == null) {
            drawNote(c);
            if (moving) postInvalidateOnAnimation();
            return;
        }
        float k = Fade.ease(since(0, FADE_MS, now)), words = Fade.ease(since(WORDS_DELAY, WORDS_MS, now)),
                strip = Fade.ease(since(STRIP_DELAY, STRIP_MS, now)), zoom = since(0, ZOOM_MS, now);
        if (frozen < 0f && changedAt >= 0 && now - changedAt < ZOOM_MS + 50) moving = true;
        if (k >= 1f) leaving = null;
        // The card: its shadow and frame, the card it is leaving fading out under the new one fading in.
        float x = Ui.f(L), y = Ui.f(T), cw = Ui.f(W), ch = Ui.f(H), rad = Ui.f(Look07.R_HERO);
        r.set(x, y, x + cw, y + ch);
        // The card is opaque: its shadow's hidden middle left out; its frame only where its picture doesn't cover it all.
        Shadows.under(c, r, rad, Look07.SH_HERO, 1f);
        if (leaving != null || k < 1f || !whole(shown)) c.drawRoundRect(r, rad, rad, frame);
        if (leaving != null) drawCard(c, leaving, markOld, 1f - k, 1f, 1f, 1f);
        float settle = 1f - (float) Math.pow(1f - zoom, 3);
        drawCard(c, shown, mark, k, words, strip, ZOOM - (ZOOM - 1f) * settle);
        // Its hairline ring.
        edge.setStrokeWidth(Math.max(1f, Ui.f(1f)));
        edge.setColor(0x1FFFFFFF);
        float sw = edge.getStrokeWidth() / 2f;
        r.set(x + sw, y + sw, x + cw - sw, y + ch - sw);
        c.drawRoundRect(r, rad, rad, edge);
        drawDots(c);
        if (moving) postInvalidateOnAnimation();
    }

    private void drawSections(Canvas c) {
        float x = Ui.f(L), cy = Ui.f(SECTIONS_Y);
        for (int i = 0; i < sections.size(); i++) {
            String name = sections.get(i).name;
            boolean on = i == section;
            // Pills.pill's width at Home's 50 px height (its sides a little narrower than a 52 px pill's).
            float pw = Pills.pillWidth(name, on) - Ui.f(Pills.PILL_PAD * 2) * (1f - SECTION_H / Pills.PILL_H);
            if (x > getWidth()) break;
            float up = on ? Math.max(0f, Math.min(1.1f, tabLift.of(sections.get(i)))) : 0f;
            if (up > 0.01f) {
                float g = 1f + 0.06f * up, hh = Ui.f(SECTION_H) / 2f * g;
                r.set(x + pw / 2f - pw * g / 2f, cy - hh, x + pw / 2f + pw * g / 2f, cy + hh);
                Shadows.draw(c, r, hh, Look07.SH_PILL, Math.min(1f, up));
                c.save();
                c.scale(g, g, x + pw / 2f, cy);
                pw = Pills.pill(c, x, cy, name, null, true, true, SECTION_H);
                c.restore();
            } else {
                pw = Pills.pill(c, x, cy, name, null, on, true, SECTION_H);
            }
            x += pw + Ui.f(SECTION_GAP);
        }
    }

    private void drawNote(Canvas c) {
        float x = Ui.f(L), y = Ui.f(T), cw = Ui.f(W), ch = Ui.f(H) * 0.62f;
        r.set(x, y, x + cw, y + ch);
        Frost f = glassNote;
        f.draw(c, r, Ui.f(Look07.R_HERO), 1f);
        if (!noteHead.isEmpty()) c.drawText(noteHead, x + Ui.f(PAD), y + Ui.f(150f), headP);
        if (!note.isEmpty()) c.drawText(Ui.fit(note, noteP, cw - Ui.f(PAD * 2)), x + Ui.f(PAD), y + Ui.f(206f), noteP);
    }

    private final Frost glassNote = new Frost(Look07.MAT_DARK);

    /**
     * A card in the card's frame at an opacity: its picture (zoomed, settling to 1), the fades, its words (their own
     * opacity, rising into place) and its strip (the same, later).
     */
    private void drawCard(Canvas c, Card card, Hero07.Mark m, float alpha, float wordsK, float stripK, float zoom) {
        if (alpha <= 0.004f) return;
        int a = Math.round(255 * alpha);
        float x = Ui.f(L), y = Ui.f(T), cw = Ui.f(W), ch = Ui.f(H), rad = Ui.f(Look07.R_HERO);
        c.save();
        c.translate(x, y);
        // The picture: the card's own, or for a channel with none its logo on its own colour.
        String url = card.art;
        Bitmap b = url.isEmpty() ? null : Pics.get(url, picW(), picH(), Pics.FACE, redraw);
        if (b == null && url.isEmpty() && !card.logo.isEmpty()) b = Pics.get(card.logo, picW(), picH(), Pics.LOGO_ART, redraw);
        if (b != null) {
            Paint p = shaders.paint(b, cw, ch, 0f);
            p.setAlpha(a);
            c.save();
            c.scale(zoom, zoom, cw / 2f, ch / 2f);
            float hw = cw / 2f / zoom, hh = ch / 2f / zoom;
            c.drawRoundRect(cw / 2f - hw, ch / 2f - hh, cw / 2f + hw, ch / 2f + hh, rad / zoom, rad / zoom, p);
            c.restore();
            p.setAlpha(255);
            if (card.facesFor != b) fades(card, b, url.isEmpty() ? card.logo : url, cw, ch);
        } else if (card.facesFor == null) {
            card.fadeEnd = cw * 0.78f;
            card.footTop = ch * 0.48f;
        }
        // The fades: the words' from the left ending before the faces, the strip's from the foot starting under them.
        leftFade.setAlpha(a);
        c.save();
        c.scale(card.fadeEnd, 1f);
        c.drawRoundRect(0, 0, 1f, ch, rad / card.fadeEnd, rad, leftFade);
        c.restore();
        footFade.setAlpha(a);
        c.save();
        c.translate(0, card.footTop);
        c.scale(cw, ch - card.footTop);
        c.drawRoundRect(0, 0, 1f, 1f, rad / cw, rad / (ch - card.footTop), footFade);
        c.restore();
        int wa = Math.round(a * wordsK);
        if (wa > 2) drawWords(c, card, m, wa, Ui.f(14f) * (1f - wordsK));
        int sa = Math.round(a * stripK);
        if (sa > 2 && !card.strip.isEmpty()) {
            boolean layer = sa < 255;
            if (layer) c.saveLayerAlpha(0, ch * 0.5f, cw, ch, sa);
            c.save();
            // Tiles that have slid off the strip's left go under the card's edge, not past its words.
            c.clipRect(Ui.f(PAD - 12f), 0, cw, ch);
            drawStrip(c, card, Ui.f(30f) * (1f - stripK));
            c.restore();
            if (layer) c.restore();
        }
        c.restore();
    }

    /** Whether a card's picture is read and opaque (it covers its frame whole, so the frame needn't be drawn under it). */
    private boolean whole(Card card) {
        String url = card.art;
        Bitmap b = url.isEmpty() ? null : Pics.cached(url, picW(), picH(), Pics.FACE);
        if (b == null && url.isEmpty() && !card.logo.isEmpty()) b = Pics.cached(card.logo, picW(), picH(), Pics.LOGO_ART);
        return b != null && !b.hasAlpha();
    }

    /** Where the fades end and start for a card's picture (its faces kept clear), once each picture. */
    private void fades(Card card, Bitmap b, String url, float cw, float ch) {
        card.facesFor = b;
        card.fadeEnd = cw * 0.78f;
        card.footTop = ch * 0.48f;
        float[] f = Pics.faces(url);
        if (f == null || f[0] == Faces.FALLBACK[0] && f[1] == Faces.FALLBACK[1] && f[2] == Faces.FALLBACK[2]) return;
        Faces.place(f[4], 1f, cw / ch, f, Pics.FACE_BIAS, cw, ch, face);
        card.fadeEnd = Math.max(cw * 0.36f, Math.min(cw * 0.78f, face.left - Ui.f(12f)));
        card.footTop = Math.max(ch * 0.48f, Math.min(ch * 0.8f, face.bottom + Ui.f(8f)));
    }

    /** The words in the card's corner (64, 58), at an opacity, rise px still to rise. */
    private void drawWords(Canvas c, Card card, Hero07.Mark m, int a, float rise) {
        float x = Ui.f(PAD), y = Ui.f(WORDS_TOP) + rise;
        // The kicker: a channel's logo on its tile, then its words, LIVE and HD.
        float rowH;
        if (card.channel) {
            rowH = Ui.f(44f);
            float cy = y + rowH / 2f;
            box.set(x, y, x + Ui.f(74f), y + rowH);
            Logos.draw(c, card.logo, box, redraw, a);
            float tx = x + Ui.f(74f + 12f);
            kickP.setAlpha((Look07.L2 >>> 24) * a / 255);
            if (!card.kick.isEmpty()) {
                c.drawText(card.kick, tx, Type.centred(kickP, cy), kickP);
                tx += kickP.measureText(card.kick) + Ui.f(12f);
            }
            if (card.liveBadge) tx += live(c, tx, cy, a) + Ui.f(12f);
            if (!card.quality.isEmpty()) Hero07.chip(c, tx, cy, card.quality, a);
        } else {
            rowH = Hero07.normal(Ui.f(21f));
            kickP.setAlpha((Look07.L2 >>> 24) * a / 255);
            if (!card.kick.isEmpty()) c.drawText(card.kick, x, Hero07.baseline(y, rowH, kickP.getTextSize()), kickP);
        }
        y += rowH;
        // Its logo (TMDB's), else its name, large.
        boolean logo = m.isLogo(redraw) || m.waiting(redraw);
        y += Ui.f(logo ? 22f : 18f);
        m.draw(c, x, y, a, redraw);
        y += m.height(redraw);
        // The facts.
        float fh = Hero07.normal(Ui.f(22f));
        if (!card.facts.isEmpty()) {
            y += Ui.f(16f);
            card.facts.draw(c, x, y + fh / 2f, a, factsP);
            y += fh;
        }
        // The score of the match on it.
        if (!card.score.isEmpty()) {
            y += Ui.f(20f);
            drawScore(c, card, x, y, a);
            y += Ui.f(62f);
        }
        // What it's about.
        if (card.about != null) {
            y += Ui.f(14f);
            float lh = Ui.f(22f * 1.45f);
            aboutP.setAlpha((Look07.L2 >>> 24) * a / 255);
            Ui.draw(c, card.about, x, y + (lh - (aboutP.descent() - aboutP.ascent())) / 2f);
            y += card.about.getLineCount() * lh;
        }
        // The buttons: the first lifted (scale 1.06) while the remote is on the card.
        if (!card.button.isEmpty()) {
            y += Ui.f(26f);
            float cy = y + Ui.f(35f), bw = Hero07.buttonWidth(card.button, card.after, card.buttonIcon);
            boolean up = active && area == CARD && card == shown;
            c.save();
            if (up) c.scale(1.06f, 1.06f, x + bw / 2f, cy);
            Hero07.button(c, x, cy, card.button, card.after, card.buttonIcon, true, a);
            c.restore();
            float bx = x + bw + Ui.f(18f);
            if (!card.second.isEmpty()) bx += Hero07.button(c, bx, cy, card.second, "", card.secondIcon, false, a) + Ui.f(18f);
            if (card.plus) Hero07.circle(c, bx + Ui.f(35f), cy, card.plusOn ? Icons07.CHECK : Icons07.PLUS, false, card.plusOn, a);
        }
    }

    /** The red LIVE badge at an opacity (30 high, its white dot); its width. */
    private float live(Canvas c, float x, float cy, int a) {
        float h = Ui.f(30f), dot = Ui.f(7f), w = Ui.f(12f) + dot + Ui.f(7f) + liveP.measureText("LIVE") + Ui.f(12f);
        r.set(x, cy - h / 2f, x + w, cy + h / 2f);
        fill.setColor(Palette.alpha(Look07.RED, a));
        c.drawRoundRect(r, Ui.f(8f), Ui.f(8f), fill);
        fill.setColor(Palette.alpha(Look07.L1, a));
        c.drawCircle(x + Ui.f(12f) + dot / 2f, cy, dot / 2f, fill);
        liveP.setAlpha(a);
        c.drawText("LIVE", x + Ui.f(12f) + dot + Ui.f(7f), Type.centred(liveP, cy), liveP);
        return w;
    }

    /** The match's score on glass: crest, side, "2 – 3", side, crest, the red card and the minute. */
    private void drawScore(Canvas c, Card card, float x, float top, int a) {
        float h = Ui.f(62f), cy = top + h / 2f, crest = Ui.f(40f), gap = Ui.f(14f);
        float w = Ui.f(12f) + crest + gap + sideP.measureText(card.home) + gap + scoreP.measureText(card.score) + gap
                + sideP.measureText(card.away) + gap + crest + gap + (card.red ? Ui.f(12f) + Ui.f(8f) : 0f) + minuteP.measureText(card.minute)
                + Ui.f(20f);
        r.set(x, top, x + w, top + h);
        glass.draw(c, r, h / 2f, a / 255f);
        float tx = x + Ui.f(12f);
        tx = crest(c, card.homeCrest, tx, cy, crest, a) + gap;
        sideP.setAlpha((Look07.L2 >>> 24) * a / 255);
        c.drawText(card.home, tx, Type.centred(sideP, cy), sideP);
        tx += sideP.measureText(card.home) + gap;
        scoreP.setAlpha(a);
        c.drawText(card.score, tx, Type.centred(scoreP, cy), scoreP);
        tx += scoreP.measureText(card.score) + gap;
        c.drawText(card.away, tx, Type.centred(sideP, cy), sideP);
        tx += sideP.measureText(card.away) + gap;
        tx = crest(c, card.awayCrest, tx, cy, crest, a) + gap;
        if (card.red) {
            fill.setColor(Palette.alpha(Look07.RED_CARD, a));
            r.set(tx, cy - Ui.f(8f), tx + Ui.f(12f), cy + Ui.f(8f));
            c.drawRoundRect(r, Ui.f(2f), Ui.f(2f), fill);
            tx += Ui.f(12f) + Ui.f(8f);
        }
        minuteP.setAlpha(a);
        c.drawText(card.minute, tx, Type.centred(minuteP, cy), minuteP);
    }

    private float crest(Canvas c, String url, float x, float cy, float d, int a) {
        Bitmap b = url.isEmpty() ? null : Pics.get(url, Math.round(d), Math.round(d), Pics.FIT, redraw);
        if (b != null) {
            bmp.setAlpha(a);
            c.drawBitmap(b, x + (d - b.getWidth()) / 2f, cy - b.getHeight() / 2f, bmp);
        }
        return x + d;
    }

    /** The strip along the card's foot (in the card's own space): its label and its tiles, rise px still to rise. */
    private void drawStrip(Canvas c, Card card, float rise) {
        boolean posters = isPosters(card);
        float tw = Ui.f(posters ? POSTER_W : TILE_W), th = Ui.f(posters ? POSTER_H : TILE_H), gap = Ui.f(TILE_GAP);
        float textH = Ui.f(10f) + Hero07.normal(Ui.f(18f)) + Hero07.normal(Ui.f(15f));
        float bottom = Ui.f(H - STRIP_FOOT) + rise, top = bottom - textH - th;
        boolean here = card == shown;
        float x0 = Ui.f(PAD) - (here ? Ui.f(stripX.value()) : Ui.f(stripTarget(card)));
        // The label: the tile in view's (a series' season), else the card's.
        Tile focus = card.stripAt < card.strip.size() ? card.strip.get(card.stripAt) : null;
        String label = focus != null && !focus.heading.isEmpty() ? focus.heading : card.stripLabel;
        if (!label.isEmpty()) {
            labelP.setColor(Look07.L2);
            c.drawText(label, Ui.f(PAD), Hero07.baseline(top - Ui.f(38f), Hero07.normal(Ui.f(16f)), labelP.getTextSize()), labelP);
        }
        float right = Ui.f(W);
        Tile up = null;
        float upX = 0f;
        for (int i = 0; i < card.strip.size(); i++) {
            float x = x0 + i * (tw + gap);
            if (x > right) break;
            if (x + tw < 0) continue;
            Tile t = card.strip.get(i);
            if (here && lift.of(t) > 0.01f) {
                // The one the remote is on (or to carry on with) is drawn last, over its neighbours.
                up = t;
                upX = x;
                continue;
            }
            drawTile(c, card, t, x, top, tw, th, 0f);
        }
        if (up != null) drawTile(c, card, up, upX, top, tw, th, lift.of(up));
    }

    /** One tile of a strip at (x, top) in the card's space, risen by up (0 to 1: 6 px, a white edge, a deep shadow). */
    private void drawTile(Canvas c, Card card, Tile t, float x, float top, float w, float h, float up) {
        float k = Math.max(0f, Math.min(1.2f, up)), y = top - Ui.f(6f) * k, rad = Ui.f(Look07.R_TILE);
        r.set(x, y, x + w, y + h);
        Shadows.draw(c, r, rad, Look07.SH_TILE, 1f - Math.min(1f, k));
        if (k > 0.01f) Shadows.draw(c, r, rad, Look07.SH_ROW, Math.min(1f, k));
        boolean drawn = false;
        if (t.poster) {
            Bitmap b = t.picture.isEmpty() ? null : Pics.get(t.picture, Math.round(w), Math.round(h), Pics.CROP, redraw);
            if (b != null) {
                c.save();
                c.translate(x, y);
                c.drawRoundRect(0, 0, w, h, rad, rad, shaders.paint(b, w, h, 0f));
                c.restore();
                drawn = true;
            }
        } else if (t.ch != null && t.at >= 0) {
            tiles.draw(c, r, rad, t.ch, t.schedule, t.at, PicTile.COVER, k > 0.5f, false, !stripX.moving());
            drawn = true;
        } else if (!t.picture.isEmpty()) {
            drawn = tiles.draw(c, r, rad, t.picture, PicTile.COVER, k > 0.5f, false) >= 0f;
        }
        if (!drawn) {
            fill.setColor(0xFF1C2238);
            c.drawRoundRect(r, rad, rad, fill);
        }
        // Its hairline, its time, how far through.
        edge.setStrokeWidth(Math.max(1f, Ui.f(1f)));
        edge.setColor(0x24FFFFFF);
        c.drawRoundRect(r, rad, rad, edge);
        if (!t.chip.isEmpty()) {
            float cw = chipP.measureText(t.chip) + Ui.f(18f), chh = Ui.f(26f);
            box.set(x + Ui.f(10f), y + Ui.f(10f), x + Ui.f(10f) + cw, y + Ui.f(10f) + chh);
            fill.setColor(0x8C000000);
            c.drawRoundRect(box, chh / 2f, chh / 2f, fill);
            c.drawText(t.chip, box.left + Ui.f(9f), Type.centred(chipP, box.centerY()), chipP);
        }
        if (t.progress >= 0f) Pills.progressOn(c, x + Ui.f(10f), y + h - Ui.f(7f) - Ui.f(4f), w - Ui.f(20f), t.progress);
        if (k > 0.01f) {
            float rw = Ui.f(3f);
            ring.setStrokeWidth(rw);
            ring.setAlpha(Math.round(255 * Math.min(1f, k)));
            r.set(x - rw / 2f, y - rw / 2f, x + w + rw / 2f, y + h + rw / 2f);
            c.drawRoundRect(r, rad + rw / 2f, rad + rw / 2f, ring);
        }
        // Its name and line under it.
        float nt = y + h + Ui.f(10f), nh = Hero07.normal(Ui.f(18f));
        c.drawText(t.small, x, Hero07.baseline(nt, nh, nameP.getTextSize()), nameP);
        if (!t.line.isEmpty()) c.drawText(t.line, x, Hero07.baseline(nt + nh, Hero07.normal(Ui.f(15f)), subP.getTextSize()), subP);
    }

    /** The dots under the card: the section's cards (at most DOTS, sliding with the remote), its own long and white. */
    private void drawDots(Canvas c) {
        Section s = current();
        if (s == null || s.cards.size() < 2) return;
        int n = Math.min(DOTS, s.cards.size());
        int first = Math.max(0, Math.min(s.cards.size() - n, s.at - n / 2));
        float d = Ui.f(9f), on = Ui.f(28f), gap = Ui.f(10f);
        float total = (n - 1) * (d + gap) + on, x = getWidth() / 2f - total / 2f, cy = Ui.f(DOTS_Y);
        for (int i = first; i < first + n; i++) {
            boolean here = i == s.at;
            float w = here ? on : d;
            fill.setColor(here ? Look07.L1 : 0x59FFFFFF);
            r.set(x, cy - d / 2f, x + w, cy + d / 2f);
            c.drawRoundRect(r, d / 2f, d / 2f, fill);
            x += w + gap;
        }
    }
}
