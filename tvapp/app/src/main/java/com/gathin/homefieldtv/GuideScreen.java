package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Genres;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Lineup;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The TV guide, the family's own (as their TiviMate shows it: DEVICE.md §2.7, §2.8, §2.10, §2.11) in the 07 look (the
 * approved stills 05c, 06c and 08c), the page of the tab bar's Guide: under the bar, the preview at the left playing the
 * channel last watched, the programme the remote is on to its right (GuideBand), the times and the rows under them
 * (GuideGrid), all over the blurred picture of what the remote is on (Ground, changed once the remote rests).
 *
 * <p>Left from the programme on now (or Back) brings in the groups column, pushing the guide right (it is never
 * covered): Homefield's playlist with Favourites, All channels and Recently watched, then the playlist's groups in
 * Settings → Categories' order and names (hidden ones and ones locked away left out), then the guide's own kinds (Sport,
 * News…). The guide switches to a group as the remote moves onto it, with no OK. Right goes back to the rows. Up from
 * the first row (or Back from the groups) goes up to the tab bar, its Guide tab lit; Down from the bar comes back.
 *
 * <p>OK on a programme on now tunes its channel in the preview; OK again on the channel playing there goes full
 * screen (Back comes back here). OK on a programme to come opens the small menu under it (Remind, Record…); on one
 * already shown that the provider's archive keeps, it plays it from its start (catch-up, CatchupScreen). OK held (or
 * MENU) opens the whole menu (GuideMenu, GuideActions) at the right, the guide darkened under it but for the remote's
 * row: Search and Settings, the programme's lines (Remind or Watch from start…), the channel's (Favourites, Rename,
 * Change logo: its card beside the menu, the menu staying; Move, Hide, Block, Catch-up: the archive by day,
 * ArchiveScreen). Play/pause shows the programme's description, Stop adds the channel to the favourites or takes it
 * off, Back held goes back to the player and Menu held opens Search (the family's TV's keys); the number keys go to a
 * channel, the channel keys page the rows, rewind and fast-forward page the times (into the archive), and Back first
 * comes back to the programmes on now. Back from the player opens a new guide (TvActivity.toGuide) on the list shown
 * last, the channel playing in its preview.
 */
final class GuideScreen extends Screen implements GuideGrid.Host, GroupsPanel.Host, GuideMenu.Host, DialogCard.Host, LiveTv.Listener,
        GuideShelves.Host {
    private static final int GRID = 0, GROUPS = 1;
    private static final String ALL = "all", FAV = "fav", RECENT = "recent", GENRE = "genre:", GROUP = "group:", PLAYLIST = "playlist",
            KINDS = "kinds";

    /** The list the guide showed last, while the app runs: Back from the player opens a new guide on it (as the TV does). */
    private static String lastShown = ALL;

    private final String startCategory;
    private FrameLayout content;
    private GuideBand band;
    private GuideGrid grid;
    /** 07's shelves (Settings › Appearance › TV guide › Guide style): the grid's state drawn as shelves. */
    private GuideShelves shelves;
    private GroupsPanel groups;
    private GuideMenu menu;
    /** The page's blurred picture (the programme the remote rests on), and the preview's frame over the live picture. */
    private GroundView groundView;
    private PreviewFrame frame;
    /** Change logo is open over the channel's menu, which stays (the remote comes back to it). */
    private boolean holdMenu;
    private DialogCard dialog;
    private int area = GRID;
    /** The list in the grid: ALL, FAV, RECENT, or GENRE / GROUP and its name. */
    private String shown = ALL;
    /** The groups column in (1) or out (0), the guide pushed right by it, gliding (its unit: the push). */
    private float reveal;
    private final Glide pushing = new Glide(GroupsPanel.SLIDE_MS, GroupsPanel.W);
    /** Steps the push each frame (Choreographer's time) while it moves. */
    private android.animation.TimeAnimator push;
    /** The playlists' names by their channels' key prefix. */
    private final Map<String, String> playlists = new HashMap<>();
    private final List<String> listed = new ArrayList<>();
    private String digits = "";
    /** Opened before there was anything to show (on the groups): goes to the rows when the channels come. */
    private boolean waiting;
    /** What a menu is open for: the channel, its programme's index and {start, end}. */
    private Channel menuChannel;
    private int menuIndex = -1;
    private long[] menuTimes;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable digitsDone = this::jump;
    /** The remote has rested on a programme: its picture asked for, and its neighbours' read ahead. */
    private final Runnable rested = this::rested;
    private final RectF tmp = new RectF();
    /** The family's edits from the guide (ChannelMenu, GroupMenu): a row being moved, the hint for it, the channel the
     *  remote stays on once the lists are made again, the group (or list) a group menu is open for. */
    private MoveMode.Move moving;
    private MoveMode.Hint hint;
    private Channel keepOn;
    private String menuGroup;
    /** The channels changed while a row was lifted: the lists made again once it is put down or back. */
    private boolean changedWhileMoving;

    /** The list shown last forgotten: the next guide opens on all the channels (a test's own channels gone). */
    static void forgetList() {
        lastShown = ALL;
    }

    /** category: one of Genres' names to open on, or null for the list shown last (all the channels at first). */
    GuideScreen(TvActivity tv, String category) {
        super(tv);
        this.startCategory = category;
    }

    /** Its page draws an opaque ground (its blurred picture) over the whole screen: the window's own isn't drawn under it. */
    @Override boolean opaque() {
        return true;
    }

    @Override View build() {
        // No ground of its own: the window's (TvActivity) is the guide's, so the screen is filled once, not twice.
        FrameLayout f = new FrameLayout(tv);
        Library lib = Library.get(tv);
        groundView = new GroundView(tv);
        frame = new PreviewFrame(tv);
        content = new FrameLayout(tv);
        band = new GuideBand(tv, lib);
        band.live = tv.live;
        grid = new GuideGrid(tv, lib);
        grid.setHost(this);
        shelves = new GuideShelves(tv, lib, grid);
        shelves.setHost(this);
        content.addView(band, new FrameLayout.LayoutParams(-1, -1));
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        content.addView(shelves, new FrameLayout.LayoutParams(-1, -1));
        applyStyle();
        groups = new GroupsPanel(tv);
        groups.look07 = true;
        groups.setHost(this);
        menu = new GuideMenu(tv);
        menu.setHost(this);
        menu.edgedHole = false;
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        hint = new MoveMode.Hint(tv);
        f.addView(groundView, new FrameLayout.LayoutParams(-1, -1));
        f.addView(content, new FrameLayout.LayoutParams(-1, -1));
        f.addView(groups, new FrameLayout.LayoutParams(-1, -1));
        f.addView(hint, new FrameLayout.LayoutParams(-1, -1));
        f.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        f.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        shown = startCategory == null ? lastShown : GENRE + startCategory;
        fillGroups();
        load(shown, null);
        // Nothing to show: the groups (and from them the menu, for Settings) rather than an empty grid.
        area = grid.count() == 0 ? GROUPS : GRID;
        waiting = area == GROUPS;
        reveal = area == GRID ? 0f : 1f;
        pushing.jump(reveal);
        groups.setActive(area == GROUPS);
        grid.setActive(area == GRID);
        place();
        return f;
    }

    // ---- the groups -------------------------------------------------------------------------------------------------

    /**
     * The groups column: the playlist's name (or All playlists) over Favourites, All channels, Recently watched and the
     * playlist's groups in Settings → Categories' order and names, hidden ones (and ones locked away) left out; then the
     * guide's own kinds. Made from the lists the library keeps (Library: a group's size is its list's), so this goes
     * through the groups, never through every channel.
     */
    private void fillGroups() {
        Library lib = Library.get(tv);
        playlists.clear();
        List<Sources.Entry> all = Sources.list(tv), inUse = Sources.inUse(tv);
        for (Sources.Entry e : all) playlists.put(e.prefix(), e.name);
        List<GroupsPanel.Entry> e = new ArrayList<>();
        listed.clear();
        e.add(GroupsPanel.Entry.heading(inUse.size() == 1 ? inUse.get(0).name : "All playlists", PLAYLIST));
        add(e, "Favourites", FAV);
        add(e, "All channels", ALL);
        add(e, "Recently watched", RECENT);
        for (String g : Categories.arranged(tv, lib.groups())) {
            if (lib.group(g).channels.isEmpty()) continue;
            add(e, Categories.label(tv, g), GROUP + g);
        }
        boolean kinds = false;
        for (String g : Genres.ORDER) {
            if (lib.genre(g).channels.isEmpty() && !g.equals(startCategory)) continue;
            if (!kinds) e.add(GroupsPanel.Entry.heading("TV guide categories", KINDS));
            kinds = true;
            add(e, g, GENRE + g);
        }
        pillNames.clear();
        pillTags.clear();
        for (GroupsPanel.Entry en : e) {
            if (en.kind == GroupsPanel.HEADING || !(en.tag instanceof String)) continue;
            String tag = (String) en.tag;
            pillTags.add(tag);
            pillNames.add(FAV.equals(tag) ? "★ Favourites" : en.name);
        }
        pillsFor = "";
        // The list on screen gone (its group hidden or emptied): all the channels.
        if (!listed.contains(shown)) shown = ALL;
        int at = 0;
        for (int i = 0; i < e.size(); i++) if (shown.equals(e.get(i).tag)) at = i;
        groups.set(e, at);
        groups.choose(shown);
    }

    /** The lists as the shelves' pills (the one on screen first), in the groups' order. */
    private final List<String> pillNames = new ArrayList<>(), pillTags = new ArrayList<>(), pillsShown = new ArrayList<>();
    private String pillsFor = "";

    @Override public List<String> pills() {
        if (!shown.equals(pillsFor)) {
            pillsFor = shown;
            pillsShown.clear();
            int at = pillTags.indexOf(shown);
            if (at >= 0) pillsShown.add(pillNames.get(at));
            for (int i = 0; i < pillNames.size(); i++) if (i != at) pillsShown.add(pillNames.get(i));
        }
        return pillsShown;
    }

    @Override public int pillOn() {
        return 0;
    }

    @Override public String listName() {
        int at = pillTags.indexOf(shown);
        if (at >= 0) return pillNames.get(at).replace("★ ", "");
        return shown.startsWith(GENRE) ? shown.substring(GENRE.length()) : "All channels";
    }

    /** The channel number being typed, in the details panel's box (or the shelves'). */
    private void typed(String d) {
        band.typed(d);
        shelves.typed(d);
    }

    /** Whether the guide is drawn as 07's shelves (Settings › Appearance › TV guide › Guide style). */
    private boolean shelves() {
        return com.gathin.homefieldtv.prefs.Look.guideStyle == com.gathin.homefieldtv.prefs.Look.GUIDE_SHELVES;
    }

    /** The grid (with its preview and details) or the shelves, as Settings says. */
    private void applyStyle() {
        boolean sh = shelves();
        band.setVisibility(sh ? View.GONE : View.VISIBLE);
        grid.setVisibility(sh ? View.INVISIBLE : View.VISIBLE);
        shelves.setVisibility(sh ? View.VISIBLE : View.GONE);
    }

    private void add(List<GroupsPanel.Entry> e, String name, String tag) {
        e.add(GroupsPanel.Entry.group(name, tag));
        listed.add(tag);
    }

    /** A list's channels, as the library made them (with where each is in it). */
    private Lineup channelsOf(String tag) {
        Library lib = Library.get(tv);
        if (FAV.equals(tag)) return lib.lineup(lib.list(Library.FAVOURITES, ""));
        if (RECENT.equals(tag)) return lib.lineup(lib.recent(null));
        if (tag.startsWith(GENRE)) return lib.genre(tag.substring(GENRE.length()));
        if (tag.startsWith(GROUP)) return lib.group(tag.substring(GROUP.length()));
        return lib.lineup();
    }

    /** A list's channels in the grid, the remote on focusOn if it is there, else on the channel playing. */
    private void load(String tag, Channel focusOn) {
        shown = tag;
        lastShown = tag;
        Library lib = Library.get(tv);
        if (!lib.hasSource()) empty(Dark.TV, "No playlist yet", "Add your playlist in Settings, and its channels and guide show here.");
        else if (!lib.ready()) empty(Dark.TV, lib.busy() || lib.status().isEmpty() ? "Loading your channels…" : lib.status(), "");
        else if (FAV.equals(tag)) empty(Dark.STAR_LINE, "No favourites yet", "Hold OK on a channel to add it to Favourites");
        else if (RECENT.equals(tag)) empty(Dark.RESUME, "Nothing watched yet", "The channels you watch show here, the last one first");
        else empty(Dark.TV, "No channels here", "");
        Channel playing = tv.live.channel();
        grid.setChannels(channelsOf(tag), focusOn != null ? focusOn : playing != null ? playing : tv.channelToPlay());
        if (shelves != null) shelves.refresh();
    }

    private void empty(int icon, String title, String line) {
        grid.emptyIcon = icon;
        grid.emptyTitle = title;
        grid.emptyLine = line;
    }

    /** The playlist and the group a channel is in, as the guide's top shows them: "Home  •  UK | Sport". */
    private String whereFrom(Channel c) {
        String g = Categories.label(tv, c.group);
        String p = playlists.get(prefix(c.key));
        return p == null || p.isEmpty() ? g : p + "  •  " + g;
    }

    /** Pure: a channel key's playlist prefix ("p1:"), "" for the first playlist's. */
    static String prefix(String key) {
        if (key == null || key.length() < 3 || key.charAt(0) != 'p') return "";
        int i = 1;
        while (i < key.length() && Character.isDigit(key.charAt(i))) i++;
        return i > 1 && i < key.length() && key.charAt(i) == ':' ? key.substring(0, i + 1) : "";
    }

    // ---- the screen ---------------------------------------------------------------------------------------------

    @Override void shown() {
        tv.live.addListener(this);
        // The preview's frame goes over the live picture while the guide is on top.
        if (frame.getParent() == null) tv.over.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        applyStyle();
        playPreview();
        place();
        moved();
        grid.refresh();
        if (holdMenu) {
            // Back from Change logo: the channel's menu again, its logo line saying what it shows now.
            holdMenu = false;
            if (menu.isOpen() && menuChannel != null) {
                List<GuideMenu.Item> items = cardItems();
                int at = 0;
                for (int i = 0; i < items.size(); i++) if (ChannelMenu.LOGO.equals(items.get(i).tag)) at = i;
                menu.openCard(items, at, rowRect());
            }
        }
    }

    // ---- 07's shell -----------------------------------------------------------------------------------------------

    @Override boolean shell() {
        return true;
    }

    @Override int tab() {
        return TabBar.GUIDE;
    }

    @Override void fromTabs() {
        // The remote back from the bar: where it was (the rows, or the groups).
        if (area == GROUPS) groups.setActive(true);
        else grid.setActive(grid.count() > 0);
        grid.invalidate();
    }

    @Override public void topEdge() {
        if (tv.tabs() != null) tv.tabs().enter();
    }

    /** The preview plays the channel last watched (a programme from the archive playing there carries on). */
    private void playPreview() {
        if (shelves()) {
            // The shelves have no preview: nothing plays unseen.
            if (tv.live.channel() != null) tv.live.stop();
            return;
        }
        if (tv.live.channel() != null && tv.live.behind()) return;
        Channel want = tv.channelToPlay();
        if (want != null) tv.live.play(want);
        else tv.live.forget();
    }

    @Override void hidden() {
        tv.live.removeListener(this);
        h.removeCallbacks(digitsDone);
        h.removeCallbacks(rested);
        if (menu.isOpen() && !holdMenu) menu.close();
        if (moving != null) moving.cancel();
        // The preview's frame goes with the guide (it stays under Change logo's card, the menu's scrim over it).
        ViewGroup p = (ViewGroup) frame.getParent();
        if (p != null && !holdMenu) p.removeView(frame);
    }

    /** What the guide shows of the player (the ▶ on its channel, its frames a second): drawn again only when it changes. */
    private String liveKey = "";
    private int liveFps = -1;

    @Override public void liveChanged() {
        Channel c = tv.live.channel();
        String k = c == null ? "" : c.key;
        int fps = tv.live.fps();
        // A stall, a reconnect or a step in quality says nothing new to the guide: nothing is drawn again for it.
        if (k.equals(liveKey) && fps == liveFps) return;
        if (!k.equals(liveKey)) grid.invalidate();
        liveKey = k;
        liveFps = fps;
        band.invalidate();
    }

    @Override void tick() {
        grid.tick();
        band.invalidate();
        frame.invalidate();
        shelves.invalidate();
    }

    @Override void channelsChanged() {
        // A row lifted stays lifted: the new lists once it is put down or back.
        if (moving != null) {
            changedWhileMoving = true;
            return;
        }
        Channel on = keepOn != null ? keepOn : grid.channel();
        keepOn = null;
        fillGroups();
        load(shown, on);
        // Opened with nothing to show (the groups, then): the rows once the channels are in, the remote untouched since.
        if (waiting && area == GROUPS && grid.count() > 0) toGrid();
        if (grid.count() > 0) waiting = false;
        // Not while another app is in front, nor under another screen.
        if (tv.showing(this)) playPreview();
    }

    // ---- where everything is ---------------------------------------------------------------------------------------

    /** The groups column at the screen's left, coming in with reveal; the guide pushed right by it. */
    private void place() {
        float width = GroupsPanel.W;
        float shift = width * reveal, hide = Ui.f(width * (reveal - 1f));
        groups.x = 0f;
        groups.setTranslationX(hide);
        groups.invalidate();
        content.setTranslationX(Ui.f(shift));
        int vis = reveal > 0f ? View.VISIBLE : View.INVISIBLE;
        if (groups.getVisibility() != vis) groups.setVisibility(vis);
        placePreview();
    }

    /** The live picture in the preview, moving with the guide; out of the way of a menu or a question over it. */
    private void placePreview() {
        if (!tv.isTop(this)) return;
        if (shelves()) {
            tv.live.place(null);
            frame.place(null, false);
            return;
        }
        Rect at = GuideBand.previewRect(content.getTranslationX() / Ui.s);
        boolean covered = asking() || menu.isOpen() && menu.box(tmp).intersects(at.left, at.top, at.right, at.bottom);
        tv.live.place(covered ? null : at);
        frame.place(covered ? null : at, menu.isOpen());
    }

    /** The question over the guide is up (from the moment it is asked until it has gone). */
    private boolean asking() {
        return dialog.getVisibility() == View.VISIBLE;
    }

    /**
     * The groups (and the rail) in or out over GroupsPanel.SLIDE_MS, the guide pushed along; also follows the drawer.
     * A glide stepped each frame on the display's clock: sent back the other way half way, it turns smoothly.
     */
    private void animatePush(float to) {
        if (Exec.inline) {
            pushing.jump(to);
            reveal = to;
            place();
            return;
        }
        pushing.to(to);
        if (push == null) {
            push = new android.animation.TimeAnimator();
            push.setTimeListener((anim, total, dt) -> {
                boolean moving = pushing.step();
                reveal = Math.max(0f, Math.min(1f, pushing.value()));
                place();
                if (!moving) anim.end();
            });
        }
        if (!push.isStarted()) push.start();
    }

    private void toGrid() {
        if (grid.count() == 0) return;
        area = GRID;
        groups.choose(shown);
        groups.setActive(false);
        grid.setActive(true);
        animatePush(0f);
    }

    private void toGroups() {
        area = GROUPS;
        groups.choose(shown);
        groups.setActive(true);
        grid.setActive(false);
        animatePush(1f);
    }

    // ---- the grid tells -----------------------------------------------------------------------------------------

    @Override public void moved() {
        Channel ch = grid.channel();
        band.show(ch, grid.programme(), grid.times(), ch != null && Library.get(tv).isFavourite(ch), ch == null ? "" : whereFrom(ch));
        shelves.invalidate();
        h.removeCallbacks(rested);
        h.postDelayed(rested, ProgrammePictures.REST_MS);
    }

    /**
     * The remote has rested: the programme's picture asked for, and the pictures a move from here would want read
     * ahead (the programmes before and after on this channel, what is on at the same time on the channels above and
     * below), so the next move cross-fades at once.
     */
    private void rested() {
        band.settled();
        if (shelves()) shelves.askPictures();
        else grid.askPictures();
        Library lib = Library.get(tv);
        Channel ch = grid.channel();
        if (ch == null) return;
        long t = grid.cursor();
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(t);
        groundFor(ch, s, i);
        if (i >= 0) {
            band.art.prefetch(ch, s, i + 1);
            band.art.prefetch(ch, s, i - 1);
        }
        for (int d = -1; d <= 1; d += 2) {
            Channel n = grid.channelAt(d);
            if (n == null) continue;
            Guide.Schedule ns = lib.schedule(n.key);
            band.art.prefetch(n, ns, ns == null ? -1 : ns.at(t));
        }
    }

    /** The page's blurred picture: the programme's own once known (asked for now), else its channel's logo. */
    private void groundFor(Channel ch, Guide.Schedule s, int i) {
        String pic = i < 0 || Exec.inline ? ProgrammePictures.peek(ch, s, i) : ProgrammePictures.get(tv, ch, s, i, () -> {
            if (grid.channel() == ch) groundFor(ch, s, i);
        });
        groundView.ground.set(pic != null && !pic.isEmpty() ? pic : LogoPack.url(ch));
    }

    @Override public void leftEdge() {
        toGroups();
    }

    @Override public Channel playing() {
        return tv.live.channel();
    }

    // ---- the groups tell ----------------------------------------------------------------------------------------

    @Override public void focused(GroupsPanel panel, GroupsPanel.Entry entry) {
        // A group shows at once as the remote moves onto it (its lists are kept ready by the library).
        if (entry.kind == GroupsPanel.HEADING || !(entry.tag instanceof String)) return;
        String tag = (String) entry.tag;
        panel.mark(tag);
        if (!tag.equals(shown)) load(tag, null);
    }

    @Override public void chosen(GroupsPanel panel, GroupsPanel.Entry entry) {
        toGrid();
    }

    @Override public boolean edge(GroupsPanel panel, int dir) {
        if (dir == Ui.RIGHT) toGrid();
        else if (dir == Ui.UP) topEdge();
        return true;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    /** The family's TV's guide keys held: Back (back to the player) and Menu (Search); not while a menu is up. */
    @Override boolean holds(int code) {
        return (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) && !menu.isOpen() && !asking() && digits.isEmpty()
                && moving == null;
    }

    @Override boolean keyLong(int code) {
        if (code == KeyEvent.KEYCODE_MENU) {
            tv.openSearch();
            return true;
        }
        if (code != KeyEvent.KEYCODE_BACK) return false;
        // Back held: the player again, on the channel in the preview (a programme from the archive from where it is).
        Channel c = tv.live.channel();
        if (c == null) return false;
        if (tv.live.behind()) {
            long at = tv.live.shownAt();
            Guide.Schedule s = Library.get(tv).schedule(c.key);
            int i = s == null ? -1 : s.at(at);
            GuideActions.watchFrom(tv, c, at, i >= 0 ? s.stop[i] : at + GuideGrid.HOUR);
        } else {
            GuideActions.watch(tv, c);
        }
        return true;
    }

    @Override boolean key(int code, KeyEvent e) {
        waiting = false;
        if (asking()) return dialog.key(code);
        if (menu.isOpen()) return menu.key(code);
        if (moving != null) return moving.key(code);
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digits += (char) ('0' + code - KeyEvent.KEYCODE_0);
            typed(digits);
            h.removeCallbacks(digitsDone);
            if (digits.length() >= 4) jump();
            else h.postDelayed(digitsDone, 1500);
            return true;
        }
        if (area == GROUPS) return groups.key(code);
        boolean first = e == null || e.getRepeatCount() == 0;
        switch (code) {
            case KeyEvent.KEYCODE_MENU:
                if (first) openCard();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (first) openAbout();
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP: {
                Channel c = grid.channel();
                if (first && c != null) {
                    GuideActions.favourite(tv, c);
                    grid.favouritesChanged();
                    shelves.favouritesChanged();
                    moved();
                }
                return true;
            }
            default:
                break;
        }
        if (grid.count() == 0) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                toGroups();
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        return grid.key(code);
    }

    private void jump() {
        h.removeCallbacks(digitsDone);
        String d = digits;
        digits = "";
        typed("");
        if (d.isEmpty()) return;
        int n;
        try {
            n = Integer.parseInt(d);
        } catch (NumberFormatException ex) {
            return;
        }
        if (!grid.jumpTo(n) && !ALL.equals(shown)) {
            // Not in this list: all the channels, and there.
            Channel c = Library.get(tv).numbered(n);
            if (c != null) {
                load(ALL, c);
                groups.choose(ALL);
            }
        }
        if (grid.count() > 0 && area != GRID) toGrid();
    }

    @Override void ok() {
        waiting = false;
        if (asking()) {
            dialog.ok();
            return;
        }
        if (menu.isOpen()) {
            menu.ok();
            return;
        }
        if (moving != null) {
            moving.drop();
            return;
        }
        if (area == GROUPS) groups.ok();
        else okOnProgramme();
    }

    /**
     * OK on a programme: one to come opens its menu under it; one already shown that the archive keeps plays from its
     * start (catch-up); else its channel is tuned in the preview, and OK on the channel playing there goes full screen.
     */
    private void okOnProgramme() {
        Channel c = grid.channel();
        if (c == null) return;
        long now = System.currentTimeMillis();
        long[] p = grid.times();
        if (p != null && p[0] > now) {
            openPopup();
            return;
        }
        if (p != null && p[1] <= now && Catchup.has(c, p[0], now)) {
            GuideActions.watchFrom(tv, c, p[0], p[1]);
            return;
        }
        Channel playing = tv.live.channel();
        if (shelves() || playing != null && playing.key.equals(c.key) && !tv.live.behind()) {
            // The shelves have no preview: OK on what's on now watches it.
            GuideActions.watch(tv, c);
            return;
        }
        PinScreen.forChannel(tv, c, () -> {
            tv.live.play(c);
            placePreview();
            grid.invalidate();
            band.invalidate();
        });
    }

    /** A channel's quality chosen in its card: it plays in the preview (on the shelves, which have none, full screen). */
    private void playChosen(Channel c) {
        if (shelves()) {
            GuideActions.watch(tv, c);
            return;
        }
        PinScreen.forChannel(tv, c, () -> {
            tv.live.play(c);
            placePreview();
            grid.invalidate();
            band.invalidate();
        });
    }

    /** OK held: the whole menu, for the programme the remote is on (or anything else, as OK). */
    @Override void okLong() {
        if (asking() || menu.isOpen() || moving != null) return;
        if (area == GROUPS) {
            // A group's menu (GroupMenu): rename, hide, move, sort its channels; show or hide them all.
            openGroupMenu();
            return;
        }
        openCard();
    }

    @Override boolean back() {
        waiting = false;
        if (asking()) {
            dialog.hide(this::placePreview);
            return true;
        }
        if (menu.isOpen()) {
            closeMenu();
            return true;
        }
        if (moving != null) {
            moving.cancel();
            return true;
        }
        if (!digits.isEmpty()) {
            h.removeCallbacks(digitsDone);
            digits = "";
            typed("");
            return true;
        }
        if (area == GROUPS) {
            // Back from the groups: up to the tab bar (Back there goes to Home's tab, then leaves the app).
            if (tv.tabs() == null) return false;
            topEdge();
            return true;
        }
        // Back to the programmes on now first (Use Back to return to current programs), then the groups.
        if (grid.awayFromNow()) grid.toNow();
        else toGroups();
        return true;
    }

    // ---- the menus ------------------------------------------------------------------------------------------------

    private void remember() {
        menuChannel = grid.channel();
        menuIndex = grid.programme();
        menuTimes = grid.times();
    }

    private void openPopup() {
        remember();
        if (menuChannel == null || menuTimes == null) return;
        RectF cell = shelves() ? shelves.focusedCard(new RectF()) : grid.focusedCell(new RectF());
        cell.offset(content.getTranslationX(), 0f);
        Guide.Schedule s = Library.get(tv).schedule(menuChannel.key);
        String title = s != null && menuIndex >= 0 && menuIndex < s.title.length ? s.title[menuIndex] : menuChannel.name;
        menu.openPopup(GuideActions.popup(tv, menuChannel, menuTimes[0], title), cell, GuideGrid.COLUMN + content.getTranslationX() / Ui.s);
        placePreview();
    }

    private void openCard() {
        remember();
        if (menuChannel == null) return;
        menu.openCard(cardItems(), 0, rowRect());
        placePreview();
    }

    /**
     * The whole menu's lines for the channel and programme remembered (GuideActions.card), then the channel's group's
     * Sort channels at the card's foot (08c), where the guide lists that group.
     */
    private List<GuideMenu.Item> cardItems() {
        Guide.Schedule s = Library.get(tv).schedule(menuChannel.key);
        List<GuideMenu.Item> items = GuideActions.card(tv, menuChannel, s, menuIndex, true, !RECENT.equals(shown));
        menuGroup = null;
        String g = menuChannel.group;
        if (!g.isEmpty() && listed.contains(GROUP + g)) {
            menuGroup = GROUP + g;
            items.add(GuideMenu.Item.section(Categories.label(tv, g) + " · Category"));
            items.add(GuideMenu.Item.item(GroupMenu.SORT, "Sort channels").value(ChannelEdits.SORT_NAMES[GroupMenu.sortOf(tv, g)]));
        }
        return items;
    }

    /** The programme's description, for the programme the remote is on (from the menu, or Play/pause). */
    private void openAbout() {
        if (menuChannel == null || !menu.isOpen()) remember();
        Channel c = menuChannel;
        if (c == null) return;
        String[] a = GuideActions.about(c, Library.get(tv).schedule(c.key), menuIndex, System.currentTimeMillis());
        long now = System.currentTimeMillis();
        float done = menuTimes != null && menuTimes[0] <= now && now < menuTimes[1] ? LiveText.progress(now, menuTimes[0], menuTimes[1]) : -1f;
        menu.openAbout(a[0], a[1], done, a[2], a[3], rowRect());
        placePreview();
    }

    private RectF rowRect() {
        RectF row = shelves() ? shelves.focusedShelf(new RectF()) : grid.focusedRow(new RectF());
        row.offset(content.getTranslationX(), 0f);
        return row;
    }

    private void closeMenu() {
        menu.close();
        menuChannel = null;
        placePreview();
    }

    @Override public void chosen(GuideMenu m, String tag) {
        if (GroupMenu.handles(tag)) {
            groupChosen(tag);
            return;
        }
        Channel c = menuChannel;
        if (c == null) return;
        if (ChannelMenu.MOVE.equals(tag)) {
            closeMenu();
            startMove(c);
            return;
        }
        if (ChannelMenu.LOGO.equals(tag)) {
            // Change logo: its card beside the menu (08c), the menu staying under it for the remote to come back to.
            holdMenu = true;
            LogoScreen logos = new LogoScreen(tv, c);
            logos.overMenu = true;
            tv.push(logos);
            return;
        }
        // Hidden: the remote stays where it was, on the channel after it.
        if (ChannelMenu.HIDE.equals(tag)) keepOn = neighbour(c);
        long[] p = menuTimes;
        Guide.Schedule s = Library.get(tv).schedule(c.key);
        boolean has = s != null && menuIndex >= 0 && menuIndex < s.title.length;
        String title = has ? s.title[menuIndex] : ChannelEdits.name(c), desc = has ? s.desc[menuIndex] : "";
        if (GuideActions.ABOUT.equals(tag)) {
            openAbout();
            return;
        }
        if (tag.startsWith(GuideActions.QUALITY)) {
            // Best or one of its versions: kept, and played (carrying on in it when it plays; else in the preview).
            closeMenu();
            if (!GuideActions.chooseQuality(tv, c, tag)) playChosen(c);
            moved();
            grid.invalidate();
            return;
        }
        closeMenu();
        boolean ask = GuideActions.act(tv, tag, c, title, desc, p);
        // In Favourites: the list made again with the channel in or out; its star in every list.
        if (GuideActions.FAVOURITE.equals(tag)) {
            grid.favouritesChanged();
            shelves.favouritesChanged();
        }
        if (GuideActions.FAVOURITE.equals(tag) && FAV.equals(shown)) load(FAV, c);
        if (ChannelMenu.handles(tag)) edited(ChannelMenu.HIDE.equals(tag) ? keepOn : c);
        moved();
        grid.invalidate();
        if (ask && tv.isTop(this)) {
            GuideActions.ask(tv, dialog);
            placePreview();
        }
    }

    @Override public void answered(DialogCard d, RowList.Row answer) {
        d.hide(this::placePreview);
        GuideActions.answered(tv, answer);
    }

    // ---- the family's edits: a channel's and a group's menus, moving a row (ChannelMenu, GroupMenu, MoveMode) --------

    /** The channel the remote goes to when this one leaves the list (Hide channel): the one after it, else before. */
    private Channel neighbour(Channel c) {
        List<Channel> l = channelsOf(shown).channels;
        int i = channelsOf(shown).indexOf(c);
        if (i < 0) return c;
        return i + 1 < l.size() ? l.get(i + 1) : i > 0 ? l.get(i - 1) : c;
    }

    /** An edit made: the lists are made again (off the main thread, then channelsChanged), the remote kept on focusOn. */
    private void edited(Channel focusOn) {
        keepOn = focusOn;
        // Previews and tests make the lists at once and nobody says: shown now.
        if (Exec.inline) channelsChanged();
    }

    /**
     * Move channel: in the group on screen (or Favourites), else in its own group (shown first): its row lifts, Up and
     * Down move it, OK puts it down (its group then in its own order: Manual), Back leaves it.
     */
    private void startMove(Channel c) {
        String group = null;
        if (!FAV.equals(shown)) {
            if (c.group.isEmpty() || !listed.contains(GROUP + c.group)) {
                android.widget.Toast.makeText(tv, "This channel's category isn't in the guide's categories to move it in",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            group = c.group;
            if (!(GROUP + group).equals(shown)) {
                load(GROUP + group, c);
                groups.choose(shown);
            }
        }
        List<Channel> list = channelsOf(shown).channels;
        if (list.size() < 2) return;
        Library lib = Library.get(tv);
        moving = new MoveMode.Channels(tv, new MoveMode.ChannelsHost() {
            @Override public void show(List<Channel> now, Channel lifted) {
                grid.setChannels(lib.lineup(now), lifted);
            }

            @Override public void done(Channel lifted, boolean moved) {
                moving = null;
                grid.lifted = null;
                hint.hide();
                if (moved) edited(lifted);
                else load(shown, lifted);
                afterMove(lifted);
                moved();
            }

            @Override public int page() {
                return GuideGrid.visible();
            }
        }, list, c, group);
        grid.lifted = c.key;
        grid.invalidate();
        hint.show(ChannelEdits.name(c));
    }

    /** A row put down or back: the channels changed meanwhile shown now. */
    private void afterMove(Channel focusOn) {
        if (!changedWhileMoving) return;
        changedWhileMoving = false;
        if (keepOn == null) keepOn = focusOn;
        channelsChanged();
    }

    /** OK held on the groups column: the menu for the group the remote is on (or the other entries' Groups lines). */
    private void openGroupMenu() {
        GroupsPanel.Entry e = groups.focused();
        if (e == null || e.kind == GroupsPanel.HEADING && !(PLAYLIST.equals(e.tag) || KINDS.equals(e.tag))) return;
        menuGroup = e.tag instanceof String ? (String) e.tag : null;
        menu.openCard(GroupMenu.items(tv, groupOf(menuGroup)), 0, groups.focusedRow(new RectF()));
        placePreview();
    }

    /** The playlist's group a groups-column tag is ("group:UK | Sport"), or null. */
    private static String groupOf(String tag) {
        return tag != null && tag.startsWith(GROUP) ? tag.substring(GROUP.length()) : null;
    }

    private void groupChosen(String tag) {
        String g = groupOf(menuGroup);
        if (GroupMenu.SORT.equals(tag)) {
            if (g != null) menu.openCard(GroupMenu.sortItems(tv, g), GroupMenu.sortFocus(tv, g), area == GROUPS ? groups.focusedRow(new RectF()) : rowRect());
            return;
        }
        menu.close();
        placePreview();
        if (GroupMenu.MOVE.equals(tag)) {
            if (g != null) startGroupMove();
            return;
        }
        GroupMenu.act(tv, tag, g);
        edited(grid.channel());
    }

    /** Move group: the playlist's groups' rows, the remote's lifted among them. */
    private void startGroupMove() {
        int lo = -1, hi = -1;
        for (int i = 0; i < groups.count(); i++) {
            GroupsPanel.Entry e = groups.entry(i);
            if (e.kind == GroupsPanel.GROUP && e.tag instanceof String && ((String) e.tag).startsWith(GROUP)) {
                if (lo < 0) lo = i;
                hi = i;
            }
        }
        if (lo < 0 || lo == hi) return;
        GroupsPanel.Entry e = groups.focused();
        moving = new MoveMode.Groups(tv, groups, lo, hi, GROUP, moved -> {
            moving = null;
            hint.hide();
            if (moved) edited(grid.channel());
            afterMove(grid.channel());
        });
        hint.show(e == null ? "" : e.name, groups.x + GroupsPanel.W);
    }

    // ---- what the tests look at -------------------------------------------------------------------------------------

    /** Where the remote is: 0 the grid, 1 the groups (the old menu's drawer is the tab bar now). */
    int area() {
        return area;
    }

    /** The list on screen: "all", "fav", "recent", or "group:" / "genre:" and its name. */
    String list() {
        return shown;
    }

    Channel focusedChannel() {
        return grid.channel();
    }

    /** The programme the remote is on as {start, end}, or null. */
    long[] focusedTimes() {
        return grid.times();
    }

    boolean menuOpen() {
        return menu.isOpen();
    }

    /** Whether a row is lifted (Move channel, Move group). */
    boolean moving() {
        return moving != null;
    }

    /** How far the guide is pushed right by the groups (design pixels). */
    float pushed() {
        return content.getTranslationX() / Ui.s;
    }

    /** The page's ground's picture (its address), for the previews. */
    String groundUrl() {
        return groundView.ground.url();
    }

    // ---- the ground and the preview's frame ------------------------------------------------------------------------

    /** The page's ground: the blurred picture of what the remote rests on (Ground), under everything. */
    private static final class GroundView extends View {
        final Ground ground = new Ground(this);

        GroundView(android.content.Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            if (ground.draw(c, getWidth(), getHeight())) postInvalidateOnAnimation();
        }
    }

    /**
     * The preview's frame, over the live picture (its surface can't be rounded): its corners filled with the page's ground
     * as it lies there (the blurred picture under its dim) and a hairline round it; nothing over the picture itself (3.6:
     * the family's "get rid of the playing text box", the "102 Riverside · playing" pill there before). With a menu's scrim
     * over the guide, the scrim over the preview too. Placed with the live picture.
     */
    private final class PreviewFrame extends View {
        private final Rect at = new Rect();
        private boolean placed, dimmed;
        private final Path corners = new Path();
        private final RectF r = new RectF();
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG), pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
                dim = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int dimFor = -1;

        PreviewFrame(android.content.Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            corners.setFillType(Path.FillType.EVEN_ODD);
        }

        /** Where the live picture is (screen px; null: not shown), and whether a menu's scrim is over the guide. */
        void place(Rect where, boolean menuOpen) {
            boolean s = where != null && !where.isEmpty();
            if (s == placed && menuOpen == dimmed && (!s || at.equals(where))) return;
            placed = s;
            dimmed = menuOpen;
            if (s) {
                at.set(where);
                r.set(at);
                float rad = Ui.f(GuideBand.PREVIEW_R);
                corners.reset();
                corners.addRect(r, Path.Direction.CW);
                corners.addRoundRect(r, rad, rad, Path.Direction.CW);
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (!placed) return;
            int h = getHeight();
            if (dimFor != h) {
                dim.setShader(new LinearGradient(0, 0, 0, h, Look07.DIM, Look07.DIM_AT, Shader.TileMode.CLAMP));
                dimFor = h;
            }
            // The corners: the ground as it lies under them.
            base.setColor(Palette.GROUND);
            c.drawPath(corners, base);
            android.graphics.BitmapShader under = groundView.ground.shader();
            if (under != null) {
                pic.setShader(under);
                c.drawPath(corners, pic);
            }
            c.drawPath(corners, dim);
            float rad = Ui.f(GuideBand.PREVIEW_R), sw = Math.max(1f, Ui.f(1f));
            ring.setStrokeWidth(sw);
            ring.setColor(0x24FFFFFF);
            c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, rad, rad, ring);
            if (dimmed) {
                scrim.setColor(Look07.SCRIM);
                c.drawRect(r, scrim);
            }
        }
    }
}
