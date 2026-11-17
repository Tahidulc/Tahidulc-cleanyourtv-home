package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Settings, as a panel down the right over the screen it was opened from (RightPanel: that screen dimmed, Settings in
 * the band): the sections as rows, About last. OK on a section opens it as a page sliding in from the right, its name
 * in the band and its lines as rows: each line's title with its value or description in grey under it, section names
 * in the accent, notes in grey. Back goes back a page, the remote on the section it came from, and on the sections
 * closes the panel. Each section's lines are as they were (the family's playlists and when each runs out, adding an M3U
 * address or an Xtream Codes login, TorBox, reading everything again, removing everything, and the rest): typing goes on
 * the on-screen keyboard over a dark sheet risen from the foot, a password only ever shows as dots and an address only
 * its server's name. The colour theme is its own page (ThemeSettings): the primaries and the accents as colour discs,
 * the pair in use ticked; OK keeps a choice and the screens are made again in it, Settings opening again on it.
 */
final class SettingsScreen extends Screen implements RightPanel.Host, Keyboard.Host {
    private static final List<String> MENU = Arrays.asList("Your playlists", "Add an M3U playlist", "Add an Xtream Codes login",
            "TorBox", "Refresh now", "Remove everything", "Channel logos", "Colour theme", "Posters", "Live TV", "Reminders",
            "Updates", "Scoreboard", "Sync", "About");
    static final int PLAYLISTS = 0, ADD_M3U = 1, ADD_XTREAM = 2, TORBOX = 3, REFRESH = 4, CLEAR = 5, LOGOS = 6, THEME = 7, POSTERS = 8,
            LIVE = 9, REMINDERS = 10, UPDATES = 11, SCOREBOARD = 12, SYNC = 13, ABOUT = 14;

    /** The line the remote goes back to after the screens are made again for a new theme (-1: the usual one). */
    private static int reopenFocus = -1;

    private RightPanel panel;
    /** Settings' player, sound and stream sections (MoreSettings), before About. */
    private MoreSettings more;
    private Keyboard keys;
    private Shade shade;
    /** The sections in their order (tags: MENU's numbers, then MoreSettings'), and the open one's tag (-1: none). */
    private final List<ListPanel.Item> menu = new ArrayList<>();
    private int section = -1;
    /** The open section's lines, as its helpers make them (the page shows them as rows, one for one). */
    private List<ListPanel.Item> items = new ArrayList<>();
    private boolean editing, confirm;
    private String editField = "", status = "", sub = "";
    // What is being typed in: kept on this screen only until the playlist is added.
    private String name = "", url = "", guide = "", server = "", user = "", pass = "";
    /** Opened again on this section (a menu number) with no slide, after the screens are made again (-1: as usual). */
    private final int reopen;

    SettingsScreen(TvActivity tv) {
        this(tv, -1);
    }

    SettingsScreen(TvActivity tv, int reopenAt) {
        super(tv);
        reopen = reopenAt;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        panel = new RightPanel(tv);
        panel.setHost(this);
        shade = new Shade(tv);
        keys = new Keyboard(tv, Keyboard.FULL);
        keys.x = (1920f - keys.width()) / 2f;
        keys.y = Shade.TOP + 26f;
        keys.setHost(this);
        keys.setVisibility(View.GONE);
        shade.setVisibility(View.GONE);
        f.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        f.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        f.addView(keys, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < MENU.size(); i++) menu.add(new ListPanel.Item(i, ListPanel.TEXT, MENU.get(i), ""));
        more = new MoreSettings(tv, (field, label, value, secret) -> openKeys(field, label));
        MoreSettings.addSections(menu, ABOUT);
        return f;
    }

    /** Over the screen it was opened from, which stays on screen (dimmed), the live picture included. */
    @Override boolean overlay() {
        return true;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override void opening() {
        if (reopen >= 0) {
            // The screens made again for a new theme: back where it was, with no slide.
            panel.open("Settings", null, sectionRows(), reopen, false);
            open(reopen, false);
            return;
        }
        panel.open("Settings", null, sectionRows(), 0, true);
    }

    @Override boolean closing(Runnable done) {
        panel.close(done);
        return true;
    }

    @Override void shown() {
        tv.live.subs.watch(this::subsChanged);
        fillContent();
    }

    @Override void hidden() {
        tv.live.subs.watch(null);
    }

    /** The speech model on its way or in: Live TV's lines again, the remote where it was. */
    private void subsChanged() {
        if (editing || section != LIVE) return;
        fillContent();
    }

    @Override void channelsChanged() {
        if (!editing) fillContent();
    }

    @Override void catalogChanged() {
        channelsChanged();
    }

    /** The sections as rows: their names, the colour theme's under its name, a dot on Updates when one waits. */
    private List<RowList.Row> sectionRows() {
        List<RowList.Row> rows = new ArrayList<>();
        for (ListPanel.Item it : menu) {
            int tag = (Integer) it.tag;
            RowList.Row r = RowList.Row.item(it, it.title, tag == THEME ? Theme.name() : "");
            r.dot = tag == UPDATES && Updates.waiting(tv);
            rows.add(r);
        }
        return rows;
    }

    /** Pure: a section's lines as rows, one for one (headings as section names, notes as notes). */
    static List<RowList.Row> rows(List<ListPanel.Item> items) {
        List<RowList.Row> out = new ArrayList<>(items.size());
        for (ListPanel.Item it : items) {
            RowList.Row r;
            if (it.kind == ListPanel.HEADING) r = RowList.Row.header(it.title);
            else if (it.kind == ListPanel.NOTE) r = RowList.Row.note(it.title);
            else r = RowList.Row.item(it, it.title, it.subtitle);
            r.dot = it.dot;
            out.add(r);
        }
        return out;
    }

    /** Previews: a section open, with what was typed. */
    void preview(int sectionIndex, String n, String s, String u) {
        name = n;
        server = s;
        user = u;
        pass = "password";
        if (section >= 0) panel.pop(sectionRows());
        open(sectionIndex, false);
    }

    /** Opened on a section (its place in the list) with the remote in it on what is chosen there (previews). */
    void openAt(int index) {
        if (panel == null) return;
        if (section >= 0) panel.pop(sectionRows());
        open(index, false);
    }

    /** A section opened as a page: its name in the band, its lines as rows, the remote on what is chosen there. */
    private void open(int index, boolean animate) {
        if (index < 0 || index >= menu.size()) return;
        section = (Integer) menu.get(index).tag;
        status = "";
        sub = "";
        confirm = false;
        more.reset();
        String title = menu.get(index).title;
        if (section == THEME) {
            List<RowList.Row> rows = ThemeSettings.rows();
            int at = reopenFocus >= 0 ? reopenFocus : ThemeSettings.focus(rows);
            reopenFocus = -1;
            panel.push(title, section, rows, at, animate);
            return;
        }
        items = content(section);
        int at = firstLine();
        if (section == POSTERS) at = PosterArt.landscape ? 2 : 1;
        else if (section == LIVE) at = LiveSettings.focus(tv);
        if (reopenFocus >= 0) at = reopenFocus;
        reopenFocus = -1;
        panel.push(title, section, rows(items), at, animate);
    }

    private int firstLine() {
        for (int i = 0; i < items.size(); i++) if (items.get(i).focusable()) return i;
        return 0;
    }

    /** Back on the sections, the remote on the one it came from. */
    private void toSections() {
        section = -1;
        items = new ArrayList<>();
        panel.pop(sectionRows());
    }

    private int focusIndex() {
        return panel.list().focusIndex();
    }

    private void setFocus(int i) {
        panel.setFocus(i);
    }

    /** The open section's lines made again (the remote on the same line number), or the sections' rows. */
    private void fillContent() {
        if (panel == null) return;
        if (section < 0) {
            if (panel.depth() == 1) panel.set(sectionRows());
            return;
        }
        if (section == THEME) return;
        items = content(section);
        panel.set(rows(items));
    }

    /** A section's lines, as its helpers give them. */
    private List<ListPanel.Item> content(int sec) {
        List<ListPanel.Item> out = new ArrayList<>();
        Library lib = Library.get(tv);
        if (MoreSettings.owns(sec)) {
            more.fill(sec, out);
            return out;
        }
        switch (sec) {
            case PLAYLISTS: {
                if (!sub.isEmpty()) {
                    Sources.Entry e = Sources.get(tv, sub);
                    if (e == null) {
                        sub = "";
                        return content(sec);
                    }
                    out.add(new ListPanel.Item(null, ListPanel.HEADING, e.name, ""));
                    out.add(new ListPanel.Item("only", ListPanel.TEXT, "Use only this playlist", "The others stay, not in use"));
                    out.add(new ListPanel.Item("remove", ListPanel.TEXT, "Remove this playlist", "Its channels, films and TV shows go"));
                    out.add(new ListPanel.Item("back", ListPanel.TEXT, "Back", ""));
                    break;
                }
                List<Sources.Entry> all = Sources.list(tv);
                String active = Sources.active(tv);
                if (all.isEmpty()) {
                    out.add(new ListPanel.Item(null, ListPanel.NOTE, "No playlist yet: add an M3U address or an Xtream Codes login.", ""));
                }
                long now = System.currentTimeMillis();
                for (Sources.Entry e : all) {
                    String exp = Sources.expiry(Sources.info(tv, e.id), now);
                    boolean used = active.isEmpty() || active.equals(e.id);
                    out.add(new ListPanel.Item(e.id, ListPanel.TEXT, e.name + (used ? "" : "  (not in use)"),
                            e.source.describe() + (exp.isEmpty() ? "" : "  ·  " + exp)));
                }
                if (all.size() > 1 && !active.isEmpty()) {
                    out.add(new ListPanel.Item("all", ListPanel.TEXT, "Use all your playlists together", ""));
                }
                if (!all.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.NOTE, counts(), ""));
                String archive = archiveLine();
                if (!all.isEmpty() && !archive.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.NOTE, archive, ""));
                break;
            }
            case ADD_M3U:
                out.add(field("name", "Name", name, "Optional, such as Home"));
                out.add(field("url", "Playlist address", url, "http://…/get.php?username=…  or  …/playlist.m3u"));
                out.add(field("guide", "Guide address (XMLTV)", guide, "Optional: the playlist's own is used"));
                out.add(new ListPanel.Item("add", ListPanel.TEXT, "Add this playlist", "It is kept only if it gives live channels"));
                if (!status.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.NOTE, status, ""));
                break;
            case ADD_XTREAM:
                out.add(field("name", "Name", name, "Optional, such as Home"));
                out.add(field("server", "Server address", server, "Such as http://example.com:8080"));
                out.add(field("user", "Username", user, ""));
                out.add(field("pass", "Password", pass.isEmpty() ? "" : dots(pass.length()), ""));
                out.add(new ListPanel.Item("add", ListPanel.TEXT, "Add this login", "It is kept only if it gives live channels"));
                if (!status.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.NOTE, status, ""));
                break;
            case TORBOX:
                TorBoxSettings.fill(tv, out);
                break;
            case REFRESH:
                out.add(new ListPanel.Item("refresh", ListPanel.TEXT, "Read everything again now", "Channels, the guide, films and TV shows"));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, lib.busy() || Catalog.get(tv).busy() ? "Reading your playlist…"
                        : status.isEmpty() ? counts() : status, ""));
                if (!lib.busy() && !archiveLine().isEmpty()) out.add(new ListPanel.Item(null, ListPanel.NOTE, archiveLine(), ""));
                break;
            case CLEAR:
                if (confirm) {
                    out.add(new ListPanel.Item(null, ListPanel.HEADING, "Remove everything from this TV?", ""));
                    out.add(new ListPanel.Item("yes", ListPanel.TEXT, "Yes, remove everything", ""));
                    out.add(new ListPanel.Item("no", ListPanel.TEXT, "No, keep it all", ""));
                    SyncSettings.removeNote(out);
                } else {
                    out.add(new ListPanel.Item("clear", ListPanel.TEXT, "Remove everything from this TV",
                            "Your playlists, channels, favourites and where you left films"));
                    SyncSettings.removedNote(tv, out);
                }
                break;
            case LOGOS:
                out.add(new ListPanel.Item("tiles", ListPanel.TEXT, "On black tiles", Pics.tiles ? "Chosen" : "Each logo trimmed and centred on a black tile"));
                out.add(new ListPanel.Item("plain", ListPanel.TEXT, "As the playlist gives them", Pics.tiles ? "The logos as they come" : "Chosen"));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "In the guide, the channel banner, search and on Home.", ""));
                break;
            case POSTERS: {
                boolean wide = PosterArt.landscape;
                out.add(new ListPanel.Item(null, ListPanel.HEADING, "Posters: Portrait / Landscape", ""));
                out.add(new ListPanel.Item("tall", ListPanel.TEXT, "Portrait",
                        (wide ? "" : "Chosen  ·  ") + "Tall posters with the title over their foot"));
                out.add(new ListPanel.Item("wide", ListPanel.TEXT, "Landscape",
                        (wide ? "Chosen  ·  " : "") + "Wide pictures from each film and show, the title at the foot's left"));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "On the film and TV show rows, the grids and More like this.", ""));
                break;
            }
            case LIVE:
                LiveSettings.fill(tv, out);
                break;
            case REMINDERS:
                ReminderSettings.fill(tv, out);
                break;
            case UPDATES:
                updates(out);
                break;
            case SCOREBOARD:
                BoardSettings.fill(tv, out);
                break;
            case SYNC:
                SyncSettings.fill(tv, out);
                break;
            default:
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Homefield TV " + version(), ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Live TV, the guide, films and TV shows come only from your own playlists.", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Playlists stay on this TV, in the app's own storage.", ""));
                out.add(new ListPanel.Item(null, ListPanel.HEADING, "Adding a login from a computer", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "adb shell content insert --uri content://com.gathin.homefieldtv.setup/xtream", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "    --bind server:s:… --bind user:s:… --bind pass:s:…", ""));
                out.add(new ListPanel.Item(null, ListPanel.HEADING, "The remote", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Hold OK on a channel to add it to your favourites.", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Type a channel's number to go straight to it.", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Back from Home goes back to the TV you were watching.", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Scoreboard lettering: Outfit (SIL Open Font License).", ""));
                break;
        }
        return out;
    }

    /**
     * Updates: the version on this TV, Check for updates (and when it last looked), and when a newer one is waiting its
     * name, what's new in it and Update now; why not, when something stood in the way.
     */
    private void updates(List<ListPanel.Item> out) {
        Updates.Stage st = Updates.stage();
        Updates.Offer o = Updates.ready(tv);
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Homefield TV " + version() + " is on this TV", ""));
        String when = st == Updates.Stage.CHECKING ? "Looking for an update…"
                : Updates.checked(Updates.checkedAt(tv), System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        out.add(new ListPanel.Item("check", ListPanel.TEXT, "Check for updates", when));
        if (o != null) {
            out.add(new ListPanel.Item(null, ListPanel.HEADING, "Homefield TV " + o.name + " is ready", ""));
            if (!o.notes.isEmpty()) {
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "What's new:", ""));
                for (String line : Updates.lines(o.notes, 70)) out.add(new ListPanel.Item(null, ListPanel.NOTE, line, ""));
            }
            String about = st == Updates.Stage.INSTALLING ? "Handing it to Android…"
                    : st == Updates.Stage.ASKING ? "Android is asking: press Update"
                    : Updates.megabytes(o.size) + "  ·  Android asks you to confirm";
            out.add(new ListPanel.Item("update", ListPanel.TEXT, "Update now", about));
            if (!Updates.allowed(tv)) {
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "The first time, Android asks you to let Homefield TV install its updates.", ""));
            }
        } else if (st == Updates.Stage.IDLE && Updates.checkedAt(tv) > 0 && Updates.problem() == null) {
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "Homefield TV is up to date.", ""));
        }
        String why = Updates.problem();
        if (why != null && !why.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.NOTE, why, ""));
    }

    /** An update found, gone or under way: the sections' dot, and the Updates page when it is open. */
    @Override void updatesChanged() {
        if (panel == null) return;
        if (section < 0) {
            fillContent();
            return;
        }
        if (!editing && section == UPDATES) {
            ListPanel.Item at = item();
            Object tag = at == null ? null : at.tag;
            fillContent();
            // The remote stays on the line it was on (Check for updates, or Update now).
            for (int i = 0; tag != null && i < items.size(); i++) {
                if (tag.equals(items.get(i).tag)) {
                    setFocus(i);
                    break;
                }
            }
        }
    }

    /** The line the remote is on, in the open section. */
    private ListPanel.Item item() {
        RowList.Row r = panel.list().focused();
        return r != null && r.tag instanceof ListPanel.Item ? (ListPanel.Item) r.tag : null;
    }

    private String version() {
        try {
            return tv.getPackageManager().getPackageInfo(tv.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private String counts() {
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        return String.format(Locale.UK, "%,d channels  ·  %,d films  ·  %,d TV shows", lib.count(), cat.films().size(), cat.series().size());
    }

    /** Catch-up: how many of the channels the provider keeps programmes for, and for how long ("" before they're in). */
    private String archiveLine() {
        Library lib = Library.get(tv);
        if (!lib.ready()) return "";
        int[] a = lib.archive();
        return Catchup.summary(a[0], a[1]);
    }

    private static ListPanel.Item field(String key, String label, String value, String hint) {
        return new ListPanel.Item("f:" + key, ListPanel.TEXT, label, value.isEmpty() ? hint : value);
    }

    private static String dots(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(n, 16); i++) b.append('•');
        return b.toString();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (editing) return keys.key(code, e);
        return panel.key(code);
    }

    @Override void ok() {
        if (editing) {
            keys.ok();
            return;
        }
        panel.ok();
    }

    @Override boolean back() {
        if (editing) {
            closeKeys();
            return true;
        }
        if (section < 0) return false;
        // A player, sound or stream setting's choices closed, the remote back on that setting.
        int line = MoreSettings.owns(section) ? more.back(section) : MoreSettings.STAY;
        if (line != MoreSettings.STAY) {
            fillContent();
            setFocus(line);
            return true;
        }
        if (!sub.isEmpty() || confirm) {
            sub = "";
            confirm = false;
            fillContent();
            setFocus(firstLine());
            return true;
        }
        toSections();
        return true;
    }

    @Override public void chosen(RightPanel p, RowList.Row row) {
        if (section < 0) {
            open(panel.list().focusIndex(), true);
            return;
        }
        if (section == THEME) {
            if (ThemeSettings.chosen(tv, row)) {
                // Kept; every screen made again in its colours, back here on the same disc.
                reopenFocus = panel.list().focusIndex();
                tv.restartLook(menuIndex(THEME));
            }
            return;
        }
        if (!(row.tag instanceof ListPanel.Item)) return;
        ListPanel.Item item = (ListPanel.Item) row.tag;
        Object tag = item.tag;
        if (!(tag instanceof String)) return;
        String t = (String) tag;
        Library lib = Library.get(tv);
        if (t.startsWith("f:")) {
            openKeys(t.substring(2), item.title);
            return;
        }
        if (MoreSettings.owns(section)) {
            int line = more.chosen(section, t, items);
            if (line != MoreSettings.STAY) {
                fillContent();
                setFocus(more.isOpen() ? more.focus(items) : line);
            }
            return;
        }
        switch (section) {
            case PLAYLISTS:
                if (t.equals("all")) {
                    Sources.setActive(tv, Sources.ALL);
                    lib.refresh(null);
                } else if (t.equals("only")) {
                    Sources.setActive(tv, sub);
                    sub = "";
                    lib.refresh(null);
                } else if (t.equals("remove")) {
                    Sources.remove(tv, sub);
                    sub = "";
                    lib.refresh(null);
                } else if (t.equals("back")) {
                    sub = "";
                } else {
                    sub = t;
                }
                fillContent();
                setFocus(sub.isEmpty() ? firstLine() : 1);
                return;
            case ADD_M3U:
            case ADD_XTREAM:
                if (t.equals("add")) add();
                return;
            case TORBOX: {
                int at = focusIndex();
                if (TorBoxSettings.chosen(tv, t, this::torboxChanged)) {
                    fillContent();
                    // The remote stays on a line there is (Check and Forget go with the key).
                    setFocus(Math.min(at, Math.max(0, items.size() - 1)));
                }
                return;
            }
            case LOGOS:
                Pics.setTiles(tv, t.equals("tiles"));
                fillContent();
                setFocus(t.equals("tiles") ? 0 : 1);
                return;
            case POSTERS: {
                boolean wide = t.equals("wide");
                if (wide == PosterArt.landscape) return;
                PosterArt.setLandscape(tv, wide);
                tv.restartLook(menuIndex(POSTERS));
                return;
            }
            case UPDATES:
                if (t.equals("check")) Updates.check(tv, null);
                else if (t.equals("update")) Updates.update(tv);
                return;
            case REFRESH:
                status = "Reading your playlist…";
                fillContent();
                lib.refresh((n, err) -> {
                    status = err != null ? err : String.format(Locale.UK, "%,d channels", n);
                    fillContent();
                });
                Catalog.get(tv).refresh(true);
                return;
            case CLEAR:
                if (t.equals("clear")) {
                    confirm = true;
                } else if (t.equals("yes")) {
                    confirm = false;
                    // This TV only: nothing removed elsewhere, and Playlists sync switched off here (SyncSettings).
                    SyncSettings.removeEverything(tv, () -> {
                        tv.live.forget();
                        lib.clear();
                        Resume.clear(tv);
                        com.gathin.homefieldtv.engine.Copies.clear(tv);
                    });
                    status = "";
                } else {
                    confirm = false;
                }
                fillContent();
                setFocus(confirm ? 1 : 0);
                return;
            case SCOREBOARD: {
                int at = focusIndex();
                if (BoardSettings.chosen(tv, t)) {
                    fillContent();
                    setFocus(at);
                }
                return;
            }
            case LIVE: {
                int at = focusIndex();
                if (LiveSettings.chosen(tv, t)) {
                    fillContent();
                    setFocus(at);
                }
                return;
            }
            case REMINDERS: {
                int at = focusIndex();
                if (ReminderSettings.chosen(tv, t)) {
                    fillContent();
                    // The remote stays on a line there is (one cancelled goes).
                    setFocus(Math.min(at, Math.max(0, items.size() - 1)));
                }
                return;
            }
            case SYNC: {
                int at = focusIndex();
                Runnable again = () -> {
                    if (section != SYNC) return;
                    int now = focusIndex();
                    fillContent();
                    setFocus(now);
                };
                if (SyncSettings.chosen(tv, t, again)) {
                    fillContent();
                    setFocus(at);
                }
                return;
            }
            default:
        }
    }

    /** A section's place in the list (its tag found), for opening Settings again on it. */
    private int menuIndex(int tag) {
        for (int i = 0; i < menu.size(); i++) if ((Integer) menu.get(i).tag == tag) return i;
        return 0;
    }

    /** The playlist tried, and kept only when it gives live channels. */
    private void add() {
        boolean m3u = section == ADD_M3U;
        Source s = m3u ? new Source(Source.M3U, url, guide, null, null, null) : new Source(Source.XTREAM, null, guide, server, user, pass);
        if (!s.usable()) {
            status = m3u ? "Type the playlist's address first." : "Type the server's address, the username and the password first.";
            fillContent();
            return;
        }
        status = "Checking it…";
        fillContent();
        Library.get(tv).test(null, name, s, (n, err) -> {
            if (err != null) {
                status = err;
            } else {
                status = String.format(Locale.UK, "Added: %,d channels. Its films and TV shows are on their way.", n);
                name = url = guide = server = user = pass = "";
            }
            fillContent();
        });
    }

    @Override public void focused(RightPanel p, RowList.Row row) {}

    /** Left, Right and the lists' ends: nothing more to go to inside the panel. */
    @Override public boolean edge(RightPanel p, int dir) {
        return true;
    }

    // ---- typing a field in ------------------------------------------------------------------------------------

    /** TorBox answered a check: its words on the section's lines, the remote where it was. */
    private void torboxChanged() {
        if (editing || section != TORBOX) return;
        int at = focusIndex();
        fillContent();
        setFocus(Math.min(at, Math.max(0, items.size() - 1)));
    }

    private void openKeys(String field, String label) {
        editField = field;
        editing = true;
        boolean torbox = field.equals(TorBoxSettings.FIELD);
        keys.label = torbox ? "Your TorBox API key" : label;
        // The TorBox key shows as dots, as a password does, and the one saved is never put back in the field.
        keys.secret = field.equals("pass") || field.startsWith("m:pin") || torbox;
        keys.hint = field.equals("server") ? "http://" : "";
        keys.setText(value(field));
        keys.reset();
        keys.setVisibility(View.VISIBLE);
        shade.setVisibility(View.VISIBLE);
        panel.list().setActive(false);
    }

    private void closeKeys() {
        editing = false;
        keys.setVisibility(View.GONE);
        shade.setVisibility(View.GONE);
        panel.list().setActive(true);
        fillContent();
    }

    private String value(String field) {
        if (field.startsWith("m:")) return more.value(field.substring(2));
        switch (field) {
            case "name": return name;
            case "url": return url;
            case "guide": return guide;
            case "server": return server;
            case "user": return user;
            case "pass": return pass;
            default: return "";
        }
    }

    @Override public void typed(Keyboard k, String text) {}

    @Override public void done(Keyboard k, String text) {
        String v = text.trim();
        if (editField.startsWith("m:")) {
            more.typed(editField.substring(2), v);
            status = "";
            closeKeys();
            return;
        }
        switch (editField) {
            case "name": name = v; break;
            case "url": url = v; break;
            case "guide": guide = v; break;
            case "server": server = v; break;
            case "user": user = v; break;
            case "pass": pass = text; break;
            case TorBoxSettings.FIELD:
                // Kept and checked with TorBox; nothing of it stays on the keyboard.
                TorBoxSettings.typed(tv, v, this::torboxChanged);
                k.setText("");
                break;
            default: break;
        }
        status = "";
        closeKeys();
    }

    @Override public boolean edge(Keyboard k, int dir) {
        return true;
    }

    /** The screen darkened behind the keyboard, which sits on a sheet of the panel's colour risen from the foot. */
    private static final class Shade extends View {
        /** The sheet's top (design pixels). */
        static final float TOP = 436f;
        private final Paint dim = Dark.fill(Palette.SCRIM), sheet = Dark.fill(Palette.PANEL), edge = Dark.line(Palette.DIVIDER, 2f);

        Shade(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), dim);
            float t = Ui.f(TOP), rad = Ui.f(16);
            c.drawRoundRect(-rad, t, getWidth() + rad, getHeight() + rad, rad, rad, sheet);
            c.drawRoundRect(-rad, t, getWidth() + rad, getHeight() + rad, rad, rad, edge);
        }
    }
}
