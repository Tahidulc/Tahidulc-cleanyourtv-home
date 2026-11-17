package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Genres;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Lineup;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.Categories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The TV guide, as the family's TiviMate shows it (DEVICE.md §2.7, §2.8, §2.10, §2.11): the preview at the top left
 * playing the channel last watched, the programme the remote is on to its right (GuideBand), the times and the rows
 * under them (GuideGrid), with nothing else on screen.
 *
 * <p>Left from the programme on now (or Back) brings in the rail and the groups column, pushing the guide right (it is
 * never covered): Homefield's playlist with Favourites, All channels and Recently watched, then the playlist's groups
 * in Settings → Categories' order and names (hidden ones and ones locked away left out), then the guide's own kinds
 * (Sport, News…). The guide switches to a group as the remote moves onto it, with no OK. Left again (or Back) opens
 * the rail into the drawer, the guide pushed further right; Back from there leaves the guide. Right goes back each
 * step.
 *
 * <p>OK on a programme on now tunes its channel in the preview; OK again on the channel playing there goes full
 * screen (Back comes back here). OK on a programme to come opens the small menu under it (Remind, Record…); on one
 * already shown that the provider's archive keeps, it plays it from its start (catch-up, CatchupScreen). OK held (or
 * MENU) opens the whole menu (GuideMenu, GuideActions): Search and Settings, the programme's lines (Remind or Watch from
 * start…), the channel's (Favourites, Catch-up: the archive by day, ArchiveScreen; Block). Play/pause shows the
 * programme's description, Stop adds the channel to the favourites or takes it off (the family's TV's keys), the
 * number keys go to a channel, the channel keys page the rows, rewind and fast-forward page the times (into the
 * archive), and Back first comes back to the programmes on now.
 */
final class GuideScreen extends Screen implements GuideGrid.Host, GroupsPanel.Host, GuideMenu.Host, DialogCard.Host, LiveTv.Listener {
    private static final int GRID = 0, GROUPS = 1, DRAWER = 2;
    private static final String ALL = "all", FAV = "fav", RECENT = "recent", GENRE = "genre:", GROUP = "group:", PLAYLIST = "playlist",
            KINDS = "kinds";

    private final String startCategory;
    private FrameLayout content;
    private GuideBand band;
    private GuideGrid grid;
    private GroupsPanel groups;
    private Rail rail;
    private GuideMenu menu;
    private DialogCard dialog;
    private int area = GRID;
    /** The list in the grid: ALL, FAV, RECENT, or GENRE / GROUP and its name. */
    private String shown = ALL;
    /** The rail and the groups column in (1) or out (0), the guide pushed right by them; and its animation. */
    private float reveal;
    private ValueAnimator push;
    /** The playlists' names by their channels' key prefix. */
    private final Map<String, String> playlists = new HashMap<>();
    private final List<String> listed = new ArrayList<>();
    private String digits = "";
    /** What a menu is open for: the channel, its programme's index and {start, end}. */
    private Channel menuChannel;
    private int menuIndex = -1;
    private long[] menuTimes;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable digitsDone = this::jump;
    private final RectF tmp = new RectF();

    /** category: one of Genres' names to open on, or null for all the channels. */
    GuideScreen(TvActivity tv, String category) {
        super(tv);
        this.startCategory = category;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Palette.GROUND);
        Library lib = Library.get(tv);
        content = new FrameLayout(tv);
        band = new GuideBand(tv, lib);
        grid = new GuideGrid(tv, lib);
        grid.setHost(this);
        content.addView(band, new FrameLayout.LayoutParams(-1, -1));
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        groups = new GroupsPanel(tv);
        groups.setHost(this);
        rail = new Rail(tv, Rail.GUIDE);
        menu = new GuideMenu(tv);
        menu.setHost(this);
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        f.addView(content, new FrameLayout.LayoutParams(-1, -1));
        f.addView(groups, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        f.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        f.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        String first = startCategory == null ? ALL : GENRE + startCategory;
        shown = first;
        fillGroups();
        load(shown, null);
        // Nothing to show: the groups (and from them the menu, for Settings) rather than an empty grid.
        area = grid.count() == 0 ? GROUPS : GRID;
        reveal = area == GRID ? 0f : 1f;
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
        // The list on screen gone (its group hidden or emptied): all the channels.
        if (!listed.contains(shown)) shown = ALL;
        int at = 0;
        for (int i = 0; i < e.size(); i++) if (shown.equals(e.get(i).tag)) at = i;
        groups.set(e, at);
        groups.choose(shown);
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
        Library lib = Library.get(tv);
        if (!lib.hasSource()) empty(Dark.TV, "No playlist yet", "Add your playlist in Settings, and its channels and guide show here.");
        else if (!lib.ready()) empty(Dark.TV, lib.busy() || lib.status().isEmpty() ? "Loading your channels…" : lib.status(), "");
        else if (FAV.equals(tag)) empty(Dark.STAR_LINE, "No favourites yet", "Hold OK on a channel to add it to Favourites");
        else if (RECENT.equals(tag)) empty(Dark.RESUME, "Nothing watched yet", "The channels you watch show here, the last one first");
        else empty(Dark.TV, "No channels here", "");
        Channel playing = tv.live.channel();
        grid.setChannels(channelsOf(tag), focusOn != null ? focusOn : playing != null ? playing : tv.channelToPlay());
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
        playPreview();
        place();
        moved();
        grid.refresh();
        rail.invalidate();
    }

    /** The preview plays the channel last watched (a programme from the archive playing there carries on). */
    private void playPreview() {
        if (tv.live.channel() != null && tv.live.behind()) return;
        Channel want = tv.channelToPlay();
        if (want != null) tv.live.play(want);
        else tv.live.forget();
    }

    @Override void hidden() {
        tv.live.removeListener(this);
        h.removeCallbacks(digitsDone);
        if (menu.isOpen()) menu.close();
    }

    @Override public void liveChanged() {
        band.invalidate();
        grid.invalidate();
    }

    @Override void tick() {
        grid.tick();
        band.invalidate();
        rail.invalidate();
    }

    @Override void channelsChanged() {
        Channel on = grid.channel();
        fillGroups();
        load(shown, on);
        // Not while another app is in front, nor under another screen.
        if (tv.showing(this)) playPreview();
    }

    // ---- where everything is ---------------------------------------------------------------------------------------

    /** The rail and the groups column at the menu's edge (Rail.edge), coming in with reveal; the guide pushed right by both. */
    private void place() {
        float edge = rail.edge() / Ui.s, width = edge + GroupsPanel.W;
        float shift = width * reveal, hide = Ui.f(width * (reveal - 1f));
        rail.setTranslationX(hide);
        groups.x = edge;
        groups.setTranslationX(hide);
        groups.invalidate();
        content.setTranslationX(Ui.f(shift));
        int vis = reveal > 0f ? View.VISIBLE : View.INVISIBLE;
        if (rail.getVisibility() != vis) rail.setVisibility(vis);
        if (groups.getVisibility() != vis) groups.setVisibility(vis);
        placePreview();
    }

    /** The live picture in the preview, moving with the guide; out of the way of a menu or a question over it. */
    private void placePreview() {
        if (!tv.isTop(this)) return;
        Rect at = GuideBand.previewRect(content.getTranslationX() / Ui.s);
        boolean covered = asking() || menu.isOpen() && menu.box(tmp).intersects(at.left, at.top, at.right, at.bottom);
        tv.live.place(covered ? null : at);
    }

    /** The question over the guide is up (from the moment it is asked until it has gone). */
    private boolean asking() {
        return dialog.getVisibility() == View.VISIBLE;
    }

    /** The groups (and the rail) in or out over GroupsPanel.SLIDE_MS, the guide pushed along; also follows the drawer. */
    private void animatePush(float to) {
        if (push != null) push.cancel();
        if (Exec.inline) {
            reveal = to;
            place();
            return;
        }
        float from = reveal;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Math.max(GroupsPanel.SLIDE_MS, Rail.DRAWER_MS));
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            reveal = from + (to - from) * (Float) v.getAnimatedValue();
            place();
        });
        push = a;
        a.start();
    }

    private void toGrid() {
        if (grid.count() == 0) return;
        area = GRID;
        rail.setActive(false);
        groups.choose(shown);
        groups.setActive(false);
        grid.setActive(true);
        animatePush(0f);
    }

    private void toGroups() {
        area = GROUPS;
        rail.setActive(false);
        groups.choose(shown);
        groups.setActive(true);
        grid.setActive(false);
        animatePush(1f);
    }

    private void toDrawer() {
        area = DRAWER;
        groups.choose(shown);
        groups.setActive(false);
        grid.setActive(false);
        rail.setActive(true);
        animatePush(1f);
    }

    // ---- the grid tells -----------------------------------------------------------------------------------------

    @Override public void moved() {
        Channel ch = grid.channel();
        band.show(ch, grid.programme(), grid.times(), ch != null && Library.get(tv).isFavourite(ch), ch == null ? "" : whereFrom(ch));
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
        if (dir == Ui.LEFT) toDrawer();
        else if (dir == Ui.RIGHT) toGrid();
        return true;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (asking()) return dialog.key(code);
        if (menu.isOpen()) return menu.key(code);
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digits += (char) ('0' + code - KeyEvent.KEYCODE_0);
            band.typed(digits);
            h.removeCallbacks(digitsDone);
            if (digits.length() >= 4) jump();
            else h.postDelayed(digitsDone, 1500);
            return true;
        }
        switch (area) {
            case DRAWER:
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    toGroups();
                    return true;
                }
                return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
            case GROUPS:
                return groups.key(code);
            default:
                break;
        }
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
        band.typed("");
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
        if (asking()) {
            dialog.ok();
            return;
        }
        if (menu.isOpen()) {
            menu.ok();
            return;
        }
        switch (area) {
            case DRAWER:
                if (rail.focus == Rail.GUIDE) toGroups();
                else Rail.go(tv, rail.focus, Rail.GUIDE);
                break;
            case GROUPS:
                groups.ok();
                break;
            default:
                okOnProgramme();
                break;
        }
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
        if (playing != null && playing.key.equals(c.key) && !tv.live.behind()) {
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
        if (asking() || menu.isOpen()) return;
        if (area != GRID) {
            ok();
            return;
        }
        openCard();
    }

    @Override boolean back() {
        if (asking()) {
            dialog.hide(this::placePreview);
            return true;
        }
        if (menu.isOpen()) {
            closeMenu();
            return true;
        }
        if (!digits.isEmpty()) {
            h.removeCallbacks(digitsDone);
            digits = "";
            band.typed("");
            return true;
        }
        switch (area) {
            case DRAWER:
                // Back from the menu leaves the guide, as it leaves the app on the family's TV.
                return false;
            case GROUPS:
                toDrawer();
                return true;
            default:
                // Back to the programmes on now first (Use Back to return to current programs), then the groups.
                if (grid.awayFromNow()) grid.toNow();
                else toGroups();
                return true;
        }
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
        RectF cell = grid.focusedCell(new RectF());
        cell.offset(content.getTranslationX(), 0f);
        menu.openPopup(GuideActions.popup(tv, menuChannel, menuTimes[0]), cell, GuideGrid.COLUMN + content.getTranslationX() / Ui.s);
        placePreview();
    }

    private void openCard() {
        remember();
        if (menuChannel == null) return;
        Guide.Schedule s = Library.get(tv).schedule(menuChannel.key);
        menu.openCard(GuideActions.card(tv, menuChannel, s, menuIndex, true), 0, rowRect());
        placePreview();
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
        RectF row = grid.focusedRow(new RectF());
        row.offset(content.getTranslationX(), 0f);
        return row;
    }

    private void closeMenu() {
        menu.close();
        menuChannel = null;
        placePreview();
    }

    @Override public void chosen(GuideMenu m, String tag) {
        Channel c = menuChannel;
        if (c == null) return;
        long[] p = menuTimes;
        Guide.Schedule s = Library.get(tv).schedule(c.key);
        String title = s != null && menuIndex >= 0 && menuIndex < s.title.length ? s.title[menuIndex] : c.name;
        if (GuideActions.ABOUT.equals(tag)) {
            openAbout();
            return;
        }
        closeMenu();
        boolean ask = GuideActions.act(tv, tag, c, title, p);
        // In Favourites: the list made again with the channel in or out.
        if (GuideActions.FAVOURITE.equals(tag) && FAV.equals(shown)) load(FAV, c);
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
}
