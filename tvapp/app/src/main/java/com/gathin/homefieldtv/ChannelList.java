package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Lineup;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's channel list, as the family picked it (mock-up "A · Refined"), in its two modes:
 *
 * <ul>
 *   <li>OVERLAY (Left while watching): over the moving picture, under a shade from the left (strong to past half way,
 *   gone by three quarters). At the top left a small round button with a left chevron (Left opens the groups), the
 *   group's name and the playlist's beside it. The channels down the left as cards; beside them the programmes of the
 *   channel the remote is on as a timeline; at the top right its programme in a card with the programme's own
 *   picture across its top (Pics.FILL at the card's size, asked for once the list rests, fading into the card at its
 *   foot), LIVE on it, its title, times, how far through it is, the time left, the quality, and three lines of what it
 *   is about.</li>
 *   <li>PREVIEW (Left held, or the bar's Channels list): opaque, the picture playing in a 736 x 414 window (1144, 72)
 *   with the date over it. The same channel cards and timeline fit it as they are, so it has them too (the timeline
 *   404 wide, to the window's left); the programme's card, without a picture (the window shows it), under the
 *   window.</li>
 * </ul>
 *
 * A channel's card (x 48, 600 wide, from y 116: 104 high, 12 apart, rounded 22) is on its own logo's colour, as the
 * family asked: "make the panels for the channels the same colour as the channel logos are (so the background of the
 * channel box)" (ChannelLook.colour, the colour its logo's cell has on every screen; the neutral slate for None). Its
 * logo sits on a slightly darker tile of the same colour (black 22 % over it, 148 x 84, rounded 16), then the programme
 * on now (cut with an ellipsis), a short line of how far through it is and the time left; the channel playing has
 * three small amber bars at the right of its title. The words are white: where a colour is too light for white to read
 * (under WCAG's 4.5:1) the card takes the least shade of black that makes it read (readable), so it stays its colour.
 * The remote's card is lifted (1.03) with a 3 px white ring and a soft shadow, over its neighbours, and stays the
 * second card while it can (its channel's number is the list's content description, for accessibility); the lower
 * cards fade.
 *
 * <p>The timeline (x 700, 460 wide): the channel's name and the day, then a thin line with a dot for each programme,
 * its time and its title; those over dimmed, the one on now on an amber-tinted band with its dot amber and glowing and
 * a thin amber line of how far through it is. As the family asked: "I should be able to click on programmes that were
 * on before by clicking on it. They should have a catch up logo telling me that catch up is available on time too."
 * So it goes back as far as the channel's archive keeps (the guide's rule: Catchup.has, by the channel's archive
 * days), at least to the programme before, and each one over that the archive still keeps has the catch-up mark
 * (Icons07.CATCHUP) beside its time; one older than that has no mark, stays dimmed and can't be chosen. At rest the
 * one on now is the second row. Right from the channels moves the remote onto the programmes (onto the one on now),
 * Up and Down along them (the remote's programme the second row, lit with a white ring), Left or Back back to the
 * channels; the card at the top right shows the remote's programme. OK on one over plays it from the archive
 * (GuideActions.watchFrom: CatchupScreen, as the guide and the archive open it), on the one on now plays the channel
 * live (on the channel playing: full screen), on one to come sets its reminder or takes it off (as the guide's Remind;
 * a bell then shows at its right).
 *
 * <p>Left on the channels opens the groups (GroupsPanel: the playlist, Favourites, All channels, then its groups, the
 * remote's group held at y 502): the channels move right beside them, and moving onto a group shows its channels at
 * once; Right or OK goes back to the channels. OK on a channel plays it (the list stays); OK on the one playing goes
 * full screen. Back closes the groups (or leaves the programmes), then the list.
 *
 * <p>The cards and the timeline glide on the display's frame clock (Glide: a held key glides on smoothly, never
 * starting over), and a frame makes nothing new: kept paints, shades and shapes, names and titles measured once and cut
 * from their kept widths (TextFit), times and the time left kept words, the card's lines laid out when its programme
 * changes, its picture drawn through a kept shader (Shaders), and the preview's ground round its window four rectangles
 * and its corners' one kept shape, not a clip.
 */
final class ChannelList extends FrameLayout implements GroupsPanel.Host {
    static final int OVERLAY = 0, PREVIEW = 1;
    /** The channels' cards (design px): from (48, 116), 600 wide, 104 high, 12 apart (a row every 116), rounded 22. */
    static final float COL_X = 48f, COL_W = 600f, ROWS_TOP = 116f, CARD_H = 104f, ROW = 116f, CARD_R = 22f;
    /** A card's logo tile (10 in from its left, 148 x 84, rounded 16, black 22 % over the card), where its words start. */
    static final float TILE_IN = 10f, TILE_W = 148f, TILE_H = 84f, TILE_R = 16f, WORDS_X = 176f, WORDS_END = 16f;
    static final int TILE_SHADE = 0x38000000;
    /** The remote's card: grown about its middle, a white ring just outside it (faint while the remote is on the programmes). */
    static final float LIFT = 1.03f, RING = 3f;
    static final int RING_AWAY = 0x66FFFFFF;
    /** The mock-up's amber: the channel playing's bars, the programme on now. */
    static final int AMBER = 0xFFF5B83D;
    /** White words' least contrast on a card (WCAG's 4.5:1). */
    static final double READABLE = 4.5;
    /** Muted words (55 %), a card's second line (78 %: readable on any card readable takes). */
    static final int MUTED = 0x8CFFFFFF, SECOND = 0xC7FFFFFF;
    /** The header: the round Left button (40 across from x 64, its middle at y 64), the group's name 14 beside it. */
    static final float HEAD_X = 64f, HEAD_CY = 64f, BUTTON_D = 40f;
    static final int BUTTON = 0x1FFFFFFF;
    /**
     * The timeline: from x 700, 460 wide (404 in the preview), its name at y 116, its rows from y 163, 49 high (the one
     * on now 65, its line of progress under it) and 6 apart, rounded 18; dots on the line 10 in, words 34 in, titles
     * after the 62 of the time (and the catch-up mark's 22 on a channel with an archive).
     */
    static final float TL_X = 700f, TL_W = 460f, TL_W_PREVIEW = 404f, TL_HEAD = 116f, TL_TOP = 163f, TL_ROW = 49f, TL_NOW = 65f,
            TL_STEP = 55f, TL_R = 18f, TL_WORDS = 34f, TL_TIME_W = 62f, TL_MARK = 22f;
    static final int NOW_BAND = 0x24F5B83D, NOW_GLOW = 0x40F5B83D, LIT_BAND = 0x1AFFFFFF;
    /** The programme's card over the picture: 662 wide from (1210, 48), rounded 32, on dark glass, its picture 330 high. */
    static final float CARD_X = 1210f, CARD_Y = 48f, CARD_W = 662f, CARD_RADIUS = 32f, PIC_H = 330f;
    static final int GLASS = 0xC712141C, LIVE_RED = 0xFFFF3B3B;
    /** The shade over the picture from the left (its colour, and its strength across the screen). */
    static final int SHADE = 0x080A10;
    private static final int[] SHADE_AT = {0xF0080A10, 0xE6080A10, 0xB8080A10, 0x33080A10, 0x00080A10};
    private static final float[] SHADE_POS = {0f, 0.34f, 0.58f, 0.74f, 1f};
    /** The lower cards fade into the shade from y 740 (45 % by 900, 60 % at the foot). */
    static final float FADE_FROM = 740f;
    static final float GROUPS_W = 500f, ROLL_MS = 120f;
    /** The preview's window for the picture (design pixels), and the programme's card under it. */
    static final float VIDEO_X = 1144f, VIDEO_Y = 72f, VIDEO_W = 736f, VIDEO_H = 414f, PREVIEW_CARD_Y = 526f;
    /** The shadows: the remote's card (0 18 40, 50 %) and the programme's card (0 30 70, 50 %). */
    static final float[] SH_LIFT = Look07.SH_CHANNEL_UP, SH_INFO = {30f, 70f, 0f, 128f};
    /** The groups' tags beside the playlist's own groups. */
    static final String ALL = Library.ALL, FAVOURITES = Library.FAVOURITES;
    static final String NO_GUIDE = "No programme information", NOTHING_NOW = "Nothing in the guide now";

    interface Host {
        /** OK on a channel that isn't playing: play it (the list stays). */
        void tune(Channel c);

        /** OK on the channel playing: the list goes, full screen. */
        void full();

        /** Back out of the list. */
        void closed();

        /** A key used: the screen's wait before hiding starts again. */
        void poke();
    }

    private final TvActivity tv;
    private final int mode;
    private final Host host;
    private final Columns columns;
    private final GroupsPanel groups;
    private Lineup lineup;
    private String group = ALL;
    private int focus;
    private boolean groupsOpen;
    /** The remote on the programmes (the timeline), and the programme it is on there. */
    private boolean inProgrammes;
    private int sel = -1;
    /** The cards' scroll (design pixels), gliding so the remote's card is the second. */
    private final Glide scroll = new Glide((long) ROLL_MS);
    /** The timeline's scroll (design pixels from the row of the one on now), gliding the same way. */
    private final Glide tl = new Glide((long) ROLL_MS);
    /** The remote's channel's programmes listed: {first, last, the one on now (else the next), the one on now or -1}. */
    private final int[] span = new int[4];

    ChannelList(TvActivity tv, int mode, Host host) {
        super(tv);
        this.tv = tv;
        this.mode = mode;
        this.host = host;
        lineup = Library.get(tv).lineup(java.util.Collections.<Channel>emptyList());
        columns = new Columns(tv);
        groups = new GroupsPanel(tv);
        groups.x = 0f;
        groups.setHost(this);
        groups.setVisibility(GONE);
        addView(columns, new LayoutParams(-1, -1));
        addView(groups, new LayoutParams(-1, -1));
    }

    int mode() {
        return mode;
    }

    /** Drawn again (a minute on, a channel changed). */
    void refresh() {
        columns.chipStale = true;
        columns.invalidate();
        groups.invalidate();
    }

    /** The list on the playing channel's group, the remote on it. */
    void open(Channel playing) {
        Library lib = Library.get(tv);
        group = playing == null || lib.group(playing.group).indexOf(playing) < 0 ? ALL : playing.group;
        load();
        focus = Math.max(0, lineup.indexOf(playing));
        groupsOpen = false;
        inProgrammes = false;
        sel = -1;
        groups.setVisibility(GONE);
        settle(true);
        columns.invalidate();
        describe();
    }

    private void load() {
        Library lib = Library.get(tv);
        if (FAVOURITES.equals(group)) lineup = lib.lineup(lib.list(FAVOURITES, null));
        else if (ALL.equals(group)) lineup = lib.lineup();
        else lineup = lib.group(group);
    }

    private Channel focused() {
        return focus >= 0 && focus < lineup.channels.size() ? lineup.channels.get(focus) : null;
    }

    /** Tests: the channel the remote is on, whether it is on the programmes, and which (-1 on the channels). */
    Channel channel() {
        return focused();
    }

    boolean onProgrammes() {
        return inProgrammes;
    }

    int programme() {
        return inProgrammes ? sel : -1;
    }

    // ---- the groups -------------------------------------------------------------------------------------------------

    private void openGroups() {
        Library lib = Library.get(tv);
        List<GroupsPanel.Entry> e = new ArrayList<>();
        List<Sources.Entry> lists = Sources.list(tv);
        e.add(GroupsPanel.Entry.heading(lists.size() == 1 ? lists.get(0).name : "All playlists", null));
        e.add(GroupsPanel.Entry.group("Favourites", FAVOURITES));
        e.add(GroupsPanel.Entry.group("All channels", ALL));
        for (String g : Categories.arranged(tv, lib.rowGroups())) {
            if (!g.isEmpty()) e.add(GroupsPanel.Entry.group(Categories.label(tv, g), g));
        }
        groups.background = mode == PREVIEW ? Palette.PANEL : Palette.PLAYBACK;
        groups.set(e, 1);
        groups.choose(group);
        groups.setActive(true);
        groups.setVisibility(VISIBLE);
        groups.slideIn();
        groupsOpen = true;
        columns.invalidate();
    }

    private void closeGroups() {
        groupsOpen = false;
        groups.setVisibility(GONE);
        columns.invalidate();
    }

    @Override public void focused(GroupsPanel panel, GroupsPanel.Entry entry) {
        // A group shows at once as the remote moves onto it.
        if (entry.tag == null) return;
        group = (String) entry.tag;
        load();
        focus = Math.max(0, lineup.indexOf(tv.live.channel()));
        settle(true);
        columns.invalidate();
        host.poke();
    }

    @Override public void chosen(GroupsPanel panel, GroupsPanel.Entry entry) {
        closeGroups();
    }

    @Override public boolean edge(GroupsPanel panel, int dir) {
        if (dir == Ui.RIGHT) closeGroups();
        return true;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        host.poke();
        if (groupsOpen) return groups.key(code);
        if (inProgrammes) return programmeKey(code);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                move(-7);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                move(7);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                openGroups();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                enterProgrammes();
                return true;
            default:
                return false;
        }
    }

    private boolean programmeKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                along(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1, 1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                along(-1, 5);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                along(1, 5);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                leaveProgrammes();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    private void move(int by) {
        int n = Math.max(0, Math.min(lineup.channels.size() - 1, focus + by));
        if (n == focus) return;
        focus = n;
        settle(false);
        columns.invalidate();
        describe();
    }

    /** The remote's channel's programmes listed now into span; false when it has none. */
    private boolean listed(Channel ch, Guide.Schedule s, long now) {
        if (ch == null || s == null || s.start.length == 0) return false;
        int n = s.start.length, at = s.at(now), next = at >= 0 ? at : s.after(now);
        int anchor = at >= 0 ? at : next >= 0 ? next : n;
        span[0] = firstShown(s.start, anchor, Catchup.earliest(ch, now));
        span[1] = n - 1;
        span[2] = anchor;
        span[3] = at;
        return true;
    }

    /** Right: onto the programme on now (the next to come in a gap; when all is over, the latest the archive keeps). */
    private void enterProgrammes() {
        Channel ch = focused();
        Guide.Schedule s = ch == null ? null : Library.get(tv).schedule(ch.key);
        long now = System.currentTimeMillis();
        if (!listed(ch, s, now)) return;
        int k = Math.min(span[2], span[1]);
        int pick = choosable(ch, s, k, now) ? k : step(ch, s, k, -1, span[0], now);
        if (pick < 0) return;
        sel = pick;
        inProgrammes = true;
        tl.to(timelineFor(sel, span[0], span[1], span[2], span[3]));
        columns.invalidate();
        describe();
    }

    /** Up or Down along the programmes, over any that can't be chosen. */
    private void along(int dir, int times) {
        Channel ch = focused();
        Guide.Schedule s = ch == null ? null : Library.get(tv).schedule(ch.key);
        long now = System.currentTimeMillis();
        if (!listed(ch, s, now)) return;
        int k = sel;
        for (int i = 0; i < times; i++) {
            int next = step(ch, s, k, dir, span[0], now);
            if (next < 0) break;
            k = next;
        }
        if (k == sel) return;
        sel = k;
        tl.to(timelineFor(sel, span[0], span[1], span[2], span[3]));
        columns.invalidate();
        describe();
    }

    /** Left (or Back) on the programmes: back on the channels, the timeline gliding back to its rest. */
    private void leaveProgrammes() {
        inProgrammes = false;
        sel = -1;
        Channel ch = focused();
        Guide.Schedule s = ch == null ? null : Library.get(tv).schedule(ch.key);
        if (listed(ch, s, System.currentTimeMillis())) tl.to(rest());
        columns.invalidate();
        describe();
    }

    /** The timeline at rest (span as listed): the one on now the second row. */
    private float rest() {
        return timelineFor(Math.min(span[2], span[1]), span[0], span[1], span[2], span[3]);
    }

    /** Whether OK does something on programme k: on now or to come, or over and still in the channel's archive. */
    private static boolean choosable(Channel ch, Guide.Schedule s, int k, long now) {
        return s.stop[k] > now || Catchup.has(ch, s.start[k], now);
    }

    /** The next programme from one in a direction that can be chosen, not before first; -1 for none. */
    private static int step(Channel ch, Guide.Schedule s, int from, int dir, int first, long now) {
        for (int k = from + dir; k >= first && k < s.start.length; k += dir) if (choosable(ch, s, k, now)) return k;
        return -1;
    }

    /** The channel the remote is on, with its number (its card doesn't draw it), and its programme there, for accessibility. */
    private void describe() {
        Channel c = focused();
        if (c == null) {
            columns.setContentDescription(null);
            return;
        }
        String what = "";
        Guide.Schedule s = inProgrammes ? Library.get(tv).schedule(c.key) : null;
        if (s != null && sel >= 0 && sel < s.start.length) {
            long now = System.currentTimeMillis();
            String state = s.stop[sel] <= now ? Catchup.has(c, s.start[sel], now) ? ", catch-up" : "" : s.start[sel] <= now ? ", on now" : "";
            what = Words.hm(s.start[sel]) + " " + s.title[sel] + state;
        }
        columns.setContentDescription(GuideGrid.describe(Library.get(tv).number(c), ChannelEdits.name(c), what));
    }

    void ok() {
        host.poke();
        if (groupsOpen) {
            groups.ok();
            return;
        }
        if (inProgrammes) {
            okProgramme();
            return;
        }
        Channel c = focused();
        if (c == null) return;
        Channel playing = tv.live.channel();
        if (playing != null && playing.key.equals(c.key)) host.full();
        else host.tune(c);
        columns.invalidate();
    }

    /** OK on a programme: over, from the archive; on now, the channel live; to come, its reminder set or taken off. */
    private void okProgramme() {
        Channel ch = focused();
        Guide.Schedule s = ch == null ? null : Library.get(tv).schedule(ch.key);
        if (s == null || sel < 0 || sel >= s.start.length) return;
        long now = System.currentTimeMillis(), a = s.start[sel], b = s.stop[sel];
        if (a > now) {
            if (Reminders.has(tv, ch.key, a)) GuideActions.unremind(tv, ch, s.title[sel], a);
            else GuideActions.remind(tv, ch, s.title[sel], a, b);
            columns.invalidate();
        } else if (b > now) {
            Channel playing = tv.live.channel();
            if (playing != null && playing.key.equals(ch.key) && !tv.live.behind()) host.full();
            else host.tune(ch);
            columns.invalidate();
        } else if (Catchup.has(ch, a, now)) {
            GuideActions.watchFrom(tv, ch, a, b);
        }
    }

    /** Back: the groups close first (or the remote leaves the programmes), then the list. */
    boolean back() {
        if (groupsOpen) {
            closeGroups();
            return true;
        }
        if (inProgrammes) {
            leaveProgrammes();
            return true;
        }
        host.closed();
        return true;
    }

    /**
     * Pure: the scroll (design pixels) that keeps card focus the second, within a list of n cards in room; at the list's
     * end the last card whole above the foot, so no card is ever cut at the top.
     */
    static float scrollFor(int focus, int n, float room) {
        int fit = Math.max(1, (int) ((room - ROWS_TOP + ROW - CARD_H) / ROW));
        float max = Math.max(0f, (n - fit) * ROW);
        return Math.max(0f, Math.min(max, (focus - 1) * ROW));
    }

    /**
     * Pure: the first programme the timeline lists: as far back as the archive keeps (earliest: Catchup.earliest,
     * Long.MAX_VALUE for none), and at least the one before anchor (the one on now).
     */
    static int firstShown(long[] start, int anchor, long earliest) {
        int lo = 0, hi = start.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (start[mid] < earliest) lo = mid + 1;
            else hi = mid;
        }
        return Math.max(0, Math.min(anchor - 1, lo));
    }

    /** Pure: where programme k's row starts (design px) from the anchor's; the one on now (nowAt, -1: none) is taller. */
    static float rowY(int k, int anchor, int nowAt) {
        return (k - anchor) * TL_STEP + (nowAt >= 0 && k > nowAt ? TL_NOW - TL_ROW : 0f);
    }

    /** Pure: the timeline's scroll that keeps programme sel the second row, within first..last. */
    static float timelineFor(int sel, int first, int last, int anchor, int nowAt) {
        float y = rowY(Math.max(first, sel - 1), anchor, nowAt);
        return Math.max(rowY(first, anchor, nowAt), Math.min(rowY(Math.max(first, last), anchor, nowAt), y));
    }

    /** Pure: the colour with the least black over it that white words read on (4.5:1); the colour itself when they do. */
    static int readable(int colour) {
        int c = colour | 0xFF000000;
        for (int k = 1; k <= 50 && ClubLook.whiteContrast(c) < READABLE; k++) c = Theme.mix(colour, 0xFF000000, k * 0.02f) | 0xFF000000;
        return c;
    }

    private static final int KEPT = 32;
    private static final int[] SEEN = new int[KEPT], MADE = new int[KEPT];
    private static int seenNext;

    /** A channel's card colour: its logo's (ChannelLook's; the slate for None), made readable; kept for the last 32. */
    static int cardColour(Channel ch) {
        int base = ChannelLook.colour(ch);
        if (base == 0) base = Logos.Tint.WAITING.row;
        base |= 0xFF000000;
        for (int i = 0; i < KEPT; i++) if (MADE[i] != 0 && SEEN[i] == base) return MADE[i];
        int made = readable(base);
        SEEN[seenNext] = base;
        MADE[seenNext] = made;
        seenNext = (seenNext + 1) % KEPT;
        return made;
    }

    private void settle(boolean now) {
        float t = scrollFor(focus, lineup.channels.size(), 1080f);
        if (now) scroll.jump(t);
        else scroll.to(t);
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    /** The list: the shade (or the preview's ground), the header, the cards, the timeline and the programme's card. */
    private final class Columns extends View {
        private static final int LIVE = 0, PAST = 1, LATER = 2;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadeP = new Paint(Paint.DITHER_FLAG), fadeP = new Paint(Paint.DITHER_FLAG), footP = new Paint(Paint.DITHER_FLAG);
        private final TextPaint groupP = Type.paint(30f, Type.BOLD, Look07.L1), listP = Type.paint(22f, Type.REGULAR, MUTED);
        private final TextPaint titleP = Type.paint(21f, Type.SEMIBOLD, Look07.L1), leftP = Type.paint(17f, Type.REGULAR, SECOND);
        private final TextPaint standP = Type.paint(18f, Type.BOLD, Look07.L1), noneP = Type.paint(19f, Type.REGULAR, SECOND);
        private final TextPaint nameP = Type.paint(24f, Type.BOLD, Look07.L1), dayP = Type.paint(18f, Type.REGULAR, MUTED);
        private final TextPaint timeP = Type.paint(18f, Type.SEMIBOLD, MUTED), progP = Type.paint(21f, Type.MEDIUM, Look07.L1);
        private final TextPaint nowP = Type.paint(21f, Type.BOLD, Look07.L1), cardTitleP = Type.paint(36f, Type.BOLD, Look07.L1);
        private final TextPaint spanP = Type.paint(19f, Type.REGULAR, 0xCCFFFFFF), chipP = Type.paint(14f, Type.BOLD, Look07.L1);
        private final TextPaint pillP = Type.paint(16f, Type.BOLD, Look07.L1), descP = Type.paint(19f, Type.REGULAR, 0xB8FFFFFF);
        private final TextPaint dateP = Type.paint(Type.ROW, Type.MEDIUM, Palette.ACCENT_LIGHT);
        private final TextFit groupFit, listFit, titleFit, leftFit, standFit, noneFit, nameFit, dayFit, progFit, nowFit, spanFit;
        private final RectF r = new RectF(), o = new RectF(), tile = new RectF(), box = new RectF();
        private final Path corners = new Path(), picPath = new Path();
        private final Shaders shaders = new Shaders();
        private final Runnable redraw = this::invalidate;
        /** The time left in words by minutes ("45 min left"), made once each. */
        private final String[] lefts = new String[24 * 60 + 1];
        private String headKey, groupName = "", listName = "";
        private float cornersFor = -1f, shadeFor = -1f, fadeFor = -1f, picFor = -1f;
        /** The timeline's day ("Today", "Yesterday"), for the programme and the hour it was worked out for. */
        private long dayStart = Long.MIN_VALUE, dayHour = Long.MIN_VALUE;
        private String dayWord = "Today";
        /** The programme's card as last laid out (made again only when its programme, width or kind changes). */
        private String infoChannel = "";
        private long infoStart = Long.MIN_VALUE;
        private float infoWidth = -1f;
        private int infoKind = -1;
        private StaticLayout infoTitle, infoDesc;
        private String infoSpan = "", infoDay = "", infoChip = "";
        boolean chipStale;
        private String dateFor;
        private float dateW;

        Columns(Context c) {
            super(c);
            groupFit = new TextFit(groupP, 32);
            listFit = new TextFit(listP, 8);
            titleFit = new TextFit(titleP, 256);
            leftFit = new TextFit(leftP, 64);
            standFit = new TextFit(standP, 64);
            noneFit = new TextFit(noneP, 4);
            nameFit = new TextFit(nameP, 64);
            dayFit = new TextFit(dayP, 16);
            progFit = new TextFit(progP, 256);
            nowFit = new TextFit(nowP, 64);
            spanFit = new TextFit(spanP, 64);
            pillP.setLetterSpacing(0.06f);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            // The shadows at the sizes drawn, made now (never in a frame).
            Shadows.prepare(SH_LIFT, CARD_R);
            Shadows.prepare(SH_INFO, CARD_RADIUS);
        }

        private String left(int minutes) {
            int m = Math.max(1, Math.min(lefts.length - 1, minutes));
            if (lefts[m] == null) lefts[m] = GuideGrid.leftWords(m);
            return lefts[m];
        }

        /** The preview's ground: everywhere but the picture's window, its corners rounded (17), with no clip. */
        private void ground(Canvas c) {
            float x0 = Ui.f(VIDEO_X), y0 = Ui.f(VIDEO_Y), x1 = Ui.f(VIDEO_X + VIDEO_W), y1 = Ui.f(VIDEO_Y + VIDEO_H), w = getWidth(), h = getHeight();
            fill.setColor(Palette.GROUND);
            c.drawRect(0, 0, w, y0, fill);
            c.drawRect(0, y1, w, h, fill);
            c.drawRect(0, y0, x0, y1, fill);
            c.drawRect(x1, y0, w, y1, fill);
            float rad = Ui.f(17f);
            if (cornersFor != x0 + y0 * 7f + x1 * 13f + y1 * 17f) {
                // The four corners' ground, made once: each a square less a quarter circle.
                cornersFor = x0 + y0 * 7f + x1 * 13f + y1 * 17f;
                corners.reset();
                corners.setFillType(Path.FillType.EVEN_ODD);
                corners.addRect(x0, y0, x1, y1, Path.Direction.CW);
                r.set(x0, y0, x1, y1);
                corners.addRoundRect(r, rad, rad, Path.Direction.CW);
            }
            c.drawPath(corners, fill);
        }

        /** Over the moving picture: its shade from the left (a kept gradient across the screen). */
        private void shade(Canvas c) {
            float w = getWidth();
            if (shadeFor != w) {
                shadeFor = w;
                shadeP.setShader(new LinearGradient(0, 0, w, 0, SHADE_AT, SHADE_POS, Shader.TileMode.CLAMP));
            }
            c.drawRect(0, 0, w, getHeight(), shadeP);
        }

        /** The lower cards and programmes fading into the shade (or the ground), from FADE_FROM to the foot. */
        private void fade(Canvas c, float x0, float x1) {
            float h = getHeight();
            if (fadeFor != h) {
                fadeFor = h;
                int rgb = (mode == PREVIEW ? Palette.GROUND : SHADE) & 0xFFFFFF;
                fadeP.setShader(new LinearGradient(0, Ui.f(FADE_FROM), 0, Ui.f(1080f), new int[]{rgb, 0x73000000 | rgb, 0x99000000 | rgb},
                        new float[]{0f, (900f - FADE_FROM) / (1080f - FADE_FROM), 1f}, Shader.TileMode.CLAMP));
            }
            c.drawRect(Ui.f(x0), Ui.f(FADE_FROM), Ui.f(x1), h, fadeP);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = scroll.step();
            moving |= tl.step();
            float sc = scroll.value();
            long now = System.currentTimeMillis();
            Library lib = Library.get(getContext());
            boolean preview = mode == PREVIEW;
            float x0 = groupsOpen ? GROUPS_W + (preview ? 2f : 0f) : 0f, tlW = preview ? TL_W_PREVIEW : TL_W;
            if (preview) ground(c);
            else shade(c);
            drawHead(c, x0);
            Channel f = focused();
            Guide.Schedule fs = f == null ? null : lib.schedule(f.key);
            boolean listed = listed(f, fs, now);
            if (listed && !inProgrammes) {
                // At rest the one on now is the second row (gliding back there from the programmes, else there at once).
                float rest = rest();
                if (tl.target() != rest) {
                    if (tl.moving()) tl.to(rest);
                    else tl.jump(rest);
                }
            }
            Channel playing = tv.live.channel();
            float clipTop = Ui.f(ROWS_TOP - 10f), h = getHeight();
            int n = lineup.channels.size(), first = Math.max(0, (int) (sc / ROW) - 1);
            // The channels (the remote's last, over its neighbours).
            c.save();
            c.clipRect(Ui.f(x0), clipTop, Ui.f(x0 + TL_X), h);
            for (int k = first; k < n; k++) {
                float top = ROWS_TOP + k * ROW - sc;
                if (top > 1080f) break;
                if (k == focus || top + CARD_H < ROWS_TOP - 10f) continue;
                Channel ch = lineup.channels.get(k);
                drawChannel(c, ch, x0, top, false, playing != null && playing.key.equals(ch.key), now, lib);
            }
            c.restore();
            // The programmes of the channel the remote is on (not while the groups are open).
            if (!groupsOpen && f != null) drawTimeline(c, f, fs, now, tlW, listed);
            fade(c, x0, groupsOpen ? x0 + TL_X : TL_X + tlW + 12f);
            if (f != null) {
                float top = ROWS_TOP + focus * ROW - sc;
                if (top <= 1080f && top + CARD_H >= ROWS_TOP - 10f) {
                    c.save();
                    c.clipRect(Ui.f(x0), clipTop, Ui.f(x0 + TL_X), h);
                    drawChannel(c, f, x0, top, true, playing != null && playing.key.equals(f.key), now, lib);
                    c.restore();
                }
            }
            if (preview) {
                // The date and the clock over the picture's window, its programme's card under it.
                String date = Words.dateClock(now);
                dateP.setColor(Palette.ACCENT_LIGHT);
                if (!date.equals(dateFor)) {
                    dateFor = date;
                    dateW = dateP.measureText(date);
                }
                c.drawText(date, Ui.f(1880f) - dateW, Ui.f(46f), dateP);
                if (f != null) drawInfo(c, f, fs, now, VIDEO_X, PREVIEW_CARD_Y, VIDEO_W, false, moving);
            } else if (!groupsOpen && f != null) {
                drawInfo(c, f, fs, now, CARD_X, CARD_Y, CARD_W, true, moving);
            }
            if (moving) postInvalidateOnAnimation();
        }

        /** The round Left button with its chevron, then the group's name and the playlist's (muted). */
        private void drawHead(Canvas c, float x0) {
            float cx = Ui.f(x0 + HEAD_X + BUTTON_D / 2f), cy = Ui.f(HEAD_CY);
            fill.setColor(BUTTON);
            c.drawCircle(cx, cy, Ui.f(BUTTON_D / 2f), fill);
            line.setColor(Look07.L1);
            line.setStrokeWidth(Ui.f(2f));
            Ui.chevron(c, cx, cy, Ui.f(5f), Ui.LEFT, line);
            if (!group.equals(headKey)) {
                headKey = group;
                List<Sources.Entry> lists = Sources.list(getContext());
                groupName = FAVOURITES.equals(group) ? "Favourites" : ALL.equals(group) ? "All channels" : Categories.label(getContext(), group);
                listName = lists.size() == 1 ? lists.get(0).name : "";
            }
            float gx = Ui.f(x0 + HEAD_X + BUTTON_D + 14f), room = Ui.f(x0 + COL_X + COL_W) - gx;
            float gw = groupFit.line(c, groupName, gx, Type.centred(groupP, cy), room);
            float lx = gx + gw + Ui.f(14f);
            if (!listName.isEmpty() && room - gw > Ui.f(80f)) listFit.line(c, listName, lx, Type.centred(listP, cy), gx + room - lx);
        }

        /**
         * A channel's card at top (design px): on its logo's colour, the logo on a darker tile of it, what's on, how far
         * through and the time left, the playing channel's bars; the remote's lifted with its ring and shadow.
         */
        private void drawChannel(Canvas c, Channel ch, float x0, float top, boolean lit, boolean playing, long now, Library lib) {
            float l = Ui.f(x0 + COL_X), t = Ui.f(top), rr = Ui.f(x0 + COL_X + COL_W), b = Ui.f(top + CARD_H), rad = Ui.f(CARD_R);
            boolean up = lit && !groupsOpen;
            r.set(l, t, rr, b);
            if (up) {
                c.save();
                c.scale(LIFT, LIFT, r.centerX(), r.centerY());
                Shadows.draw(c, r, rad, SH_LIFT, 1f);
            }
            fill.setColor(cardColour(ch));
            c.drawRoundRect(r, rad, rad, fill);
            // The logo on a slightly darker tile of the card's colour (no name or number: the logo alone).
            tile.set(l + Ui.f(TILE_IN), t + Ui.f((CARD_H - TILE_H) / 2f), l + Ui.f(TILE_IN + TILE_W), t + Ui.f((CARD_H + TILE_H) / 2f));
            fill.setColor(TILE_SHADE);
            c.drawRoundRect(tile, Ui.f(TILE_R), Ui.f(TILE_R), fill);
            ChannelLook.logoBox(tile, false, box);
            String url = LogoPack.url(ch);
            if (!Logos.drawCell(c, url, box, redraw) && (url.isEmpty() || Pics.failed(url))) {
                // A channel with no logo at all: its name in the logo's place.
                String name = ChannelEdits.name(ch);
                float w = Math.min(standFit.width(name), box.width());
                standFit.line(c, name, box.centerX() - w / 2f, Type.centred(standP, box.centerY()), box.width());
            }
            // What's on: its title, how far through it is, the time left.
            float tx = l + Ui.f(WORDS_X), end = rr - Ui.f(WORDS_END);
            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            float titleCy = t + Ui.f(38f);
            if (i < 0) {
                noneFit.line(c, s == null ? NO_GUIDE : NOTHING_NOW, tx, Type.centred(noneP, t + Ui.f(CARD_H / 2f)), end - tx - (playing ? Ui.f(28f) : 0f));
            } else {
                titleFit.line(c, s.title[i], tx, Type.centred(titleP, titleCy), end - tx - (playing ? Ui.f(28f) : 0f));
                float cy = t + Ui.f(68f), bw = Ui.f(120f);
                progress(c, tx, cy, bw, Ui.f(4f), LiveText.progress(now, s.start[i], s.stop[i]), 0x33FFFFFF, Look07.L1);
                float lx = tx + bw + Ui.f(12f);
                leftFit.line(c, left((int) ((s.stop[i] - now + 59_999L) / 60_000L)), lx, Type.centred(leftP, cy), end - lx);
            }
            if (playing) {
                // The channel playing: three small amber bars at the right of its title.
                float foot = (i < 0 ? t + Ui.f(CARD_H / 2f) : titleCy) + Ui.f(9f), bw = Ui.f(4f), br = Ui.f(2f);
                fill.setColor(AMBER);
                float bx = end - Ui.f(18f);
                c.drawRoundRect(bx, foot - Ui.f(10f), bx + bw, foot, br, br, fill);
                c.drawRoundRect(bx + Ui.f(7f), foot - Ui.f(18f), bx + Ui.f(7f) + bw, foot, br, br, fill);
                c.drawRoundRect(bx + Ui.f(14f), foot - Ui.f(13f), bx + Ui.f(14f) + bw, foot, br, br, fill);
            }
            if (up) {
                // The remote's: a 3 px white ring just outside (faint while it is on the programmes).
                float rw = Ui.f(RING), out = rw / 2f;
                o.set(l - out, t - out, rr + out, b + out);
                line.setStrokeWidth(rw);
                line.setColor(inProgrammes ? RING_AWAY : Look07.L1);
                c.drawRoundRect(o, rad + out, rad + out, line);
                c.restore();
            }
        }

        /** A thin line of how far through, rounded, at (x, cy): its track and its part done. */
        private void progress(Canvas c, float x, float cy, float w, float h, float done, int track, int colour) {
            float k = Math.max(0f, Math.min(1f, done)), rh = h / 2f;
            fill.setColor(track);
            c.drawRoundRect(x, cy - rh, x + w, cy + rh, rh, rh, fill);
            if (k <= 0f) return;
            fill.setColor(colour);
            c.drawRoundRect(x, cy - rh, x + Math.max(h, w * k), cy + rh, rh, rh, fill);
        }

        /** "Today", "Yesterday": the day of a programme's start, worked out again only for another one or an hour on. */
        private String day(long start, long now) {
            long hour = now / 3_600_000L;
            if (start != dayStart || hour != dayHour) {
                dayStart = start;
                dayHour = hour;
                dayWord = Ui.day(start, now);
            }
            return dayWord;
        }

        /** The timeline: the channel's name and day, the line, a row for each programme listed (span), from its scroll. */
        private void drawTimeline(Canvas c, Channel ch, Guide.Schedule s, long now, float tlW, boolean listed) {
            float x = Ui.f(TL_X), right = Ui.f(TL_X + tlW), wx = Ui.f(TL_X + TL_WORDS);
            float hb = Type.centred(nameP, Ui.f(TL_HEAD + 14.5f));
            // "Today" while the timeline rests on now; the remote's programme's day on the programmes ("Yesterday").
            String dw = listed && inProgrammes && sel >= 0 && sel < s.start.length ? day(s.start[sel], now) : "Today";
            float dayW = dayFit.width(dw);
            float nw = nameFit.line(c, ChannelEdits.name(ch), wx, hb, right - wx - Ui.f(12f) - dayW);
            dayFit.line(c, dw, wx + nw + Ui.f(12f), hb, dayW + 1f);
            if (!listed) {
                noneP.setColor(SECOND);
                noneFit.line(c, s == null ? NO_GUIDE : NOTHING_NOW, wx, Type.centred(noneP, Ui.f(TL_TOP + 24.5f)), right - wx);
                return;
            }
            int first = span[0], last = span[1], anchor = span[2], nowAt = span[3];
            float ts = tl.value();
            // The archive as the guide reads it: the channel's own (or another playlist's copy), by its days.
            Channel src = Catchup.source(ch);
            float markRoom = src != null ? TL_MARK : 0f;
            c.save();
            c.clipRect(x - Ui.f(12f), Ui.f(TL_TOP - 6f), right + Ui.f(12f), getHeight());
            // The thin line through the dots, from the first listed to the last.
            float ly0 = Math.max(TL_TOP - 6f, TL_TOP + rowY(first, anchor, nowAt) - ts + 28f), ly1 = Math.min(1080f, TL_TOP + rowY(last, anchor, nowAt) - ts + 28f);
            if (ly1 > ly0) {
                fill.setColor(0x24FFFFFF);
                c.drawRect(x + Ui.f(9f), Ui.f(ly0), x + Ui.f(11f), Ui.f(ly1), fill);
            }
            int k0 = Math.max(first, anchor + (int) Math.floor(ts / TL_STEP) - 2);
            for (int k = k0; k <= last; k++) {
                float y = TL_TOP + rowY(k, anchor, nowAt) - ts;
                if (y > 1080f) break;
                boolean on = k == nowAt;
                if (y + (on ? TL_NOW : TL_ROW) < TL_TOP - 6f) continue;
                boolean past = s.stop[k] <= now, kept = past && src != null && Catchup.inArchive(src.archiveDays, s.start[k], now);
                drawProgramme(c, ch, s, k, y, on, inProgrammes && k == sel, past, kept, markRoom, now, right);
            }
            c.restore();
        }

        private void drawProgramme(Canvas c, Channel ch, Guide.Schedule s, int k, float y, boolean on, boolean lit, boolean past, boolean kept,
                                   float markRoom, long now, float right) {
            float l = Ui.f(TL_X), t = Ui.f(y), b = Ui.f(y + (on ? TL_NOW : TL_ROW)), rad = Ui.f(TL_R);
            if (on || lit) {
                // The one on now on its amber-tinted band; the remote's on a soft light one.
                r.set(l, t, right, b);
                fill.setColor(on ? NOW_BAND : LIT_BAND);
                c.drawRoundRect(r, rad, rad, fill);
            }
            if (lit) {
                // The remote's programme: 07's 3 px white ring just outside its band.
                float rw = Ui.f(RING), out = rw / 2f;
                o.set(l - out, t - out, right + out, b + out);
                line.setStrokeWidth(rw);
                line.setColor(Look07.L1);
                c.drawRoundRect(o, rad + out, rad + out, line);
            }
            // Its dot on the line (on now: amber, glowing).
            float dx = Ui.f(TL_X + 10f), dy = Ui.f(y + 28f);
            if (on) {
                fill.setColor(NOW_GLOW);
                c.drawCircle(dx, dy, Ui.f(12f), fill);
            }
            fill.setColor(on ? AMBER : lit ? Look07.L1 : past ? 0x40FFFFFF : 0x73FFFFFF);
            c.drawCircle(dx, dy, Ui.f(7f), fill);
            // Its time, the catch-up mark beside it (still in the archive), its title.
            float cy = Ui.f(y + 24.5f), base = Type.centred(progP, cy);
            timeP.setColor(on ? AMBER : lit ? 0xD9FFFFFF : 0x80FFFFFF);
            c.drawText(Words.hm(s.start[k]), Ui.f(TL_X + TL_WORDS), base, timeP);
            if (kept) Icons07.draw(c, Icons07.CATCHUP, Ui.f(TL_X + TL_WORDS + TL_TIME_W + 10f), cy, Ui.f(17f), lit ? Look07.L1 : 0xA6FFFFFF);
            float tx = Ui.f(TL_X + TL_WORDS + TL_TIME_W + 16f + markRoom), end = right - Ui.f(18f);
            boolean bell = !past && !on && Reminders.has(getContext(), ch.key, s.start[k]);
            TextPaint p = on ? nowP : progP;
            p.setColor(on || lit ? Look07.L1 : past ? kept ? 0xB3FFFFFF : 0x73FFFFFF : 0xDBFFFFFF);
            (on ? nowFit : progFit).line(c, s.title[k], tx, base, end - tx - (bell ? Ui.f(28f) : 0f));
            if (bell) Icons07.draw(c, Icons07.BELL, end - Ui.f(9f), cy, Ui.f(18f), AMBER);
            if (on) progress(c, tx, Ui.f(y + 49f), end - tx, Ui.f(4f), LiveText.progress(now, s.start[k], s.stop[k]), 0x38FFFFFF, AMBER);
        }

        /** The card's picture's shape (its top corners rounded) and its foot's fade, made again only for another size. */
        private void picShape(float w, float h, float rad) {
            if (picFor == w * 31f + h) return;
            picFor = w * 31f + h;
            picPath.reset();
            r.set(0, 0, w, h);
            picPath.addRoundRect(r, new float[]{rad, rad, rad, rad, 0, 0, 0, 0}, Path.Direction.CW);
            footP.setShader(new LinearGradient(0, h * 0.45f, 0, h, GLASS & 0xFFFFFF, 0xF2000000 | (GLASS & 0xFFFFFF), Shader.TileMode.CLAMP));
        }

        /**
         * The programme's card (the remote's programme on the programmes, else the one on now): over the picture its own
         * picture across its top with LIVE (or CATCH-UP) on it, then its title, times, how far through, the time left
         * and the quality, and what it is about; in the preview the same without the picture, under the window.
         */
        private void drawInfo(Canvas c, Channel ch, Guide.Schedule s, long now, float x0, float y0, float w, boolean picture, boolean moving) {
            if (s == null) return;
            int i = inProgrammes ? sel : s.at(now);
            if (i < 0 || i >= s.start.length) return;
            long a = s.start[i], b = s.stop[i];
            int kind = b <= now ? PAST : a > now ? LATER : LIVE;
            float inner = w - 64f;
            if (!ch.key.equals(infoChannel) || a != infoStart || w != infoWidth || kind != infoKind || chipStale) {
                boolean same = ch.key.equals(infoChannel) && a == infoStart && w == infoWidth && kind == infoKind;
                infoChannel = ch.key;
                infoStart = a;
                infoWidth = w;
                infoKind = kind;
                chipStale = false;
                if (!same) {
                    infoTitle = InfoCard.lines(s.title[i], cardTitleP, Ui.px(inner), 2, 1.12f * cardTitleP.getTextSize() / cardTitleP.getFontSpacing());
                    infoDesc = s.desc[i].isEmpty() ? null : InfoCard.lines(s.desc[i], descP, Ui.px(inner), 3, 1.45f * descP.getTextSize() / descP.getFontSpacing());
                    infoSpan = Words.span(a, b);
                    String d = kind == LIVE ? "" : Ui.day(a, now);
                    infoDay = "Today".equals(d) ? "" : d;
                }
                // The quality: the picture's own for the channel playing (once known), else as its name says.
                Channel playing = tv.live.channel();
                String q = "";
                if (kind == LIVE && playing != null && playing.key.equals(ch.key)) q = tv.live.format()[0];
                infoChip = q.isEmpty() ? LiveText.quality(ch.name) : q;
            }
            float top = picture ? PIC_H - 40f : 32f, th = infoTitle.getHeight() / Ui.s, dh = infoDesc == null ? 0f : infoDesc.getHeight() / Ui.s;
            float h = top + th + 14f + 24f + (infoDesc == null ? 0f : 14f + dh) + 32f;
            float l = Ui.f(x0), t = Ui.f(y0), rr = Ui.f(x0 + w), bb = Ui.f(y0 + h), rad = Ui.f(picture ? CARD_RADIUS : 17f);
            r.set(l, t, rr, bb);
            if (picture) Shadows.draw(c, r, rad, SH_INFO, 1f);
            fill.setColor(picture ? GLASS : Palette.PANEL);
            c.drawRoundRect(r, rad, rad, fill);
            if (picture) {
                // Its own picture across the top (asked for once the list rests), fading into the card at its foot.
                c.save();
                c.translate(l, t);
                float pw = rr - l, ph = Ui.f(PIC_H);
                picShape(pw, ph, rad);
                String url = moving ? ProgrammePictures.peek(ch, s, i) : ProgrammePictures.get(getContext(), ch, s, i, redraw);
                Bitmap bm = url == null || url.isEmpty() ? null : Pics.get(url, Math.round(pw), Math.round(ph), Pics.FILL, redraw);
                if (bm != null) {
                    c.drawPath(picPath, shaders.paint(bm, pw, ph, 0f));
                } else {
                    // While it comes (or where there is none): the channel's colour with its logo.
                    fill.setColor(cardColour(ch));
                    c.drawPath(picPath, fill);
                    box.set(pw / 2f - Ui.f(150f), ph * 0.42f - Ui.f(64f), pw / 2f + Ui.f(150f), ph * 0.42f + Ui.f(64f));
                    Logos.drawCell(c, LogoPack.url(ch), box, redraw);
                }
                c.drawRect(0, ph * 0.45f, pw, ph, footP);
                if (kind != LATER) pill(c, kind == LIVE);
                c.restore();
                // Its hairline edge.
                float sw = Math.max(1f, Ui.f(1f));
                o.set(l + sw / 2f, t + sw / 2f, rr - sw / 2f, bb - sw / 2f);
                line.setStrokeWidth(sw);
                line.setColor(0x1AFFFFFF);
                c.drawRoundRect(o, rad, rad, line);
            }
            float tx = l + Ui.f(32f), y = y0 + top;
            Ui.draw(c, infoTitle, tx, Ui.f(y));
            y += th + 14f;
            float cy = Ui.f(y + 12f), sb = Type.centred(spanP, cy), room = Ui.f(inner);
            float sx = tx + spanFit.line(c, infoSpan, tx, sb, room);
            if (kind == LIVE) {
                progress(c, sx + Ui.f(14f), cy, Ui.f(160f), Ui.f(5f), LiveText.progress(now, a, b), 0x38FFFFFF, Look07.L1);
                sx += Ui.f(14f + 160f + 14f);
                sx += spanFit.line(c, left((int) ((b - now + 59_999L) / 60_000L)), sx, sb, tx + room - sx);
            } else if (!infoDay.isEmpty()) {
                sx += Ui.f(14f);
                sx += spanFit.line(c, infoDay, sx, sb, tx + room - sx);
            }
            if (!infoChip.isEmpty()) {
                // The quality's chip: outlined, rounded 7.
                float cw = chipP.measureText(infoChip), cx = sx + Ui.f(14f), ch2 = Ui.f(12f), sw = Ui.f(1.5f);
                if (cx + cw + Ui.f(18f) <= tx + room) {
                    o.set(cx, cy - ch2, cx + cw + Ui.f(18f), cy + ch2);
                    line.setStrokeWidth(sw);
                    line.setColor(0x73FFFFFF);
                    c.drawRoundRect(o, Ui.f(7f), Ui.f(7f), line);
                    c.drawText(infoChip, cx + Ui.f(9f), Type.centred(chipP, cy), chipP);
                }
            }
            y += 24f;
            if (infoDesc != null) Ui.draw(c, infoDesc, tx, Ui.f(y + 14f));
        }

        /** LIVE with its red dot (or CATCH-UP with its mark) on a dark pill at the picture's top left (card px). */
        private void pill(Canvas c, boolean live) {
            String word = live ? "LIVE" : "CATCH-UP";
            float x = Ui.f(32f), y = Ui.f(26f), hh = Ui.f(34f), mark = live ? Ui.f(10f) : Ui.f(18f);
            float w = Ui.f(14f) + mark + Ui.f(8f) + pillP.measureText(word) + Ui.f(14f), cy = y + hh / 2f;
            fill.setColor(0x8C000000);
            r.set(x, y, x + w, y + hh);
            c.drawRoundRect(r, hh / 2f, hh / 2f, fill);
            if (live) {
                fill.setColor(LIVE_RED);
                c.drawCircle(x + Ui.f(14f) + mark / 2f, cy, mark / 2f, fill);
            } else {
                Icons07.draw(c, Icons07.CATCHUP, x + Ui.f(14f) + mark / 2f, cy, mark, Look07.L1);
            }
            c.drawText(word, x + Ui.f(14f) + mark + Ui.f(8f), Type.centred(pillP, cy), pillP);
        }
    }
}
