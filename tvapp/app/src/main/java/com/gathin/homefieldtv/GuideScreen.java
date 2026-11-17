package com.gathin.homefieldtv;

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
import com.gathin.homefieldtv.engine.Reminders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The TV guide, dark, in the phone's guide's colours: the rail; the categories down the side (All channels, Favourites,
 * Recently watched, the guide's own kinds, then the playlist's groups, each with how many channels it has); at the top
 * the programme the remote is on (its title, channel, times and story) with its picture at the top right; then the
 * grid, with a strip under the channel's row about that programme. OK watches the channel, or plays a programme
 * already shown from the provider's archive (catch-up); OK held brings up a small menu (Remind me or Cancel reminder for
 * a programme to come, watch it live, from the start, the favourites); a programme with a reminder has a bell; Back from
 * the past comes back to now; the number keys go to a channel. No mini TV here: the stream is let go until a channel is
 * chosen.
 */
final class GuideScreen extends Screen implements GuideGrid.Host {
    private static final int RAIL = 0, PANEL = 1, GRID = 2;
    private static final String ALL = "all", FAV = "fav", RECENT = "recent", GENRE = "genre:", GROUP = "group:";

    private final String startCategory;
    private Rail rail;
    private SidePanel panel;
    private GuideGrid grid;
    private GuideBand band;
    private Backdrop backdrop;
    private PopMenu menu;
    private int area = GRID;
    /** The list in the grid: ALL, FAV, RECENT, or GENRE / GROUP and its name. */
    private String shown = ALL;
    private final Map<String, String> genreOf = new HashMap<>();
    private final Set<String> favourites = new HashSet<>();
    private String digits = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable digitsDone = this::jump;
    private final Runnable switchList = this::showChosen;
    private final Runnable askPicture = this::askPicture;

    /** category: one of Genres' names to open on, or null for all the channels. */
    GuideScreen(TvActivity tv, String category) {
        super(tv);
        this.startCategory = category;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Dark.GROUND);
        Library lib = Library.get(tv);
        backdrop = new Backdrop(tv, 920f, 0f, 1000f, 340f, Pics.DARK);
        grid = new GuideGrid(tv, lib);
        grid.setHost(this);
        band = new GuideBand(tv, lib);
        panel = new SidePanel(tv);
        rail = new Rail(tv, Rail.GUIDE);
        f.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        f.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        f.addView(band, new FrameLayout.LayoutParams(-1, -1));
        f.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        menu = new PopMenu(tv);
        f.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        String first = startCategory == null ? ALL : GENRE + startCategory;
        fillPanel(first);
        load(first, null);
        to(grid.count() == 0 ? PANEL : GRID);
        return f;
    }

    /** The categories with their counts: the guide's own lists, its kinds, then the playlist's groups. */
    private void fillPanel(String chosen) {
        Library lib = Library.get(tv);
        genreOf.clear();
        Map<String, Integer> kinds = new HashMap<>(), groups = new HashMap<>();
        for (Channel c : lib.all()) {
            String g = Genres.of(c.group, c.name);
            genreOf.put(c.key, g);
            Integer n = kinds.get(g);
            kinds.put(g, n == null ? 1 : n + 1);
            Integer m = groups.get(c.group);
            groups.put(c.group, m == null ? 1 : m + 1);
        }
        favourites.clear();
        favourites.addAll(lib.favourites());
        List<SidePanel.Entry> e = new ArrayList<>();
        e.add(new SidePanel.Entry("All channels", Dark.LIVE, lib.count(), ALL));
        e.add(new SidePanel.Entry("Favourites", Dark.HEART, lib.list(Library.FAVOURITES, "").size(), FAV));
        e.add(new SidePanel.Entry("Recently watched", Dark.RESUME, lib.recent(null).size(), RECENT));
        e.add(SidePanel.Entry.section("Categories"));
        for (String g : Genres.ORDER) {
            Integer n = kinds.get(g);
            if (n != null || g.equals(startCategory)) e.add(new SidePanel.Entry(g, -1, n == null ? 0 : n, GENRE + g));
        }
        if (!lib.groups().isEmpty()) {
            e.add(SidePanel.Entry.section("Your playlist"));
            for (String g : lib.groups()) {
                Integer n = groups.get(g);
                e.add(new SidePanel.Entry(g, -1, n == null ? 0 : n, GROUP + g));
            }
        }
        int at = 0;
        for (int i = 0; i < e.size(); i++) if (chosen.equals(e.get(i).tag)) at = i;
        panel.set("TV guide", e, at);
    }

    private List<Channel> channelsOf(String tag) {
        Library lib = Library.get(tv);
        if (FAV.equals(tag)) return lib.list(Library.FAVOURITES, "");
        if (RECENT.equals(tag)) return lib.recent(null);
        if (tag.startsWith(GENRE)) {
            String g = tag.substring(GENRE.length());
            List<Channel> out = new ArrayList<>();
            for (Channel c : lib.all()) if (g.equals(genreOf.get(c.key))) out.add(c);
            return out;
        }
        if (tag.startsWith(GROUP)) return lib.list(tag.substring(GROUP.length()), "");
        return lib.all();
    }

    /** A list's channels in the grid, the remote on focusOn if it is there, else on the channel last watched. */
    private void load(String tag, Channel focusOn) {
        shown = tag;
        Library lib = Library.get(tv);
        if (!lib.hasSource()) grid.empty = "Add your playlist in Settings, and its channels and guide show here.";
        else if (!lib.ready()) grid.empty = lib.busy() ? "Loading your channels…" : lib.status();
        else if (FAV.equals(tag)) grid.empty = "No favourites yet. Hold OK on a channel to add it.";
        else if (RECENT.equals(tag)) grid.empty = "The channels you watch show here, the last one first.";
        else grid.empty = "No channels here.";
        grid.setChannels(channelsOf(tag), focusOn != null ? focusOn : tv.channelToPlay());
    }

    @Override void shown() {
        // No mini TV in this guide: the stream let go until a channel is chosen.
        tv.live.stop();
        tv.live.place(null);
        grid.refresh();
        band.invalidate();
        rail.invalidate();
    }

    @Override void hidden() {
        h.removeCallbacks(digitsDone);
        h.removeCallbacks(switchList);
        h.removeCallbacks(askPicture);
        if (menu.isOpen()) menu.close();
    }

    @Override void tick() {
        rail.invalidate();
        band.invalidate();
        grid.invalidate();
    }

    @Override void channelsChanged() {
        Channel on = grid.channel();
        fillPanel(shown);
        load(shown, on);
        if (grid.count() == 0 && area == GRID) to(PANEL);
    }

    // ---- the band and its picture -----------------------------------------------------------------------------------

    @Override public void moved() {
        Channel ch = grid.channel();
        band.show(ch, grid.programme(), ch != null && favourites.contains(ch.key));
        // The picture behind the band: the one known now, else none until the remote rests and it is found.
        Guide.Schedule s = ch == null ? null : Library.get(tv).schedule(ch.key);
        backdrop.set(ProgrammePictures.peek(ch, s, grid.programme()));
        h.removeCallbacks(askPicture);
        if (Exec.inline) askPicture();
        else h.postDelayed(askPicture, ProgrammePictures.REST_MS);
    }

    private void askPicture() {
        Channel ch = grid.channel();
        Guide.Schedule s = ch == null ? null : Library.get(tv).schedule(ch.key);
        String url = ProgrammePictures.get(tv, ch, s, grid.programme(), this::pictureCame);
        if (url != null) backdrop.set(url);
    }

    private void pictureCame() {
        Channel ch = grid.channel();
        Guide.Schedule s = ch == null ? null : Library.get(tv).schedule(ch.key);
        String url = ProgrammePictures.peek(ch, s, grid.programme());
        if (url != null) backdrop.set(url);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    private void to(int a) {
        area = a;
        rail.setActive(a == RAIL);
        panel.setActive(a == PANEL);
        grid.setActive(a == GRID);
    }

    @Override boolean key(int code, KeyEvent e) {
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
            case RAIL:
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    to(PANEL);
                    return true;
                }
                return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
            case PANEL:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    to(RAIL);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    toGrid();
                    return true;
                }
                if (panel.key(code)) {
                    h.removeCallbacks(switchList);
                    if (Exec.inline) showChosen();
                    else h.postDelayed(switchList, 220);
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
            default:
                if (grid.count() == 0) {
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                        to(PANEL);
                        return true;
                    }
                    return false;
                }
                return grid.key(code);
        }
    }

    /** The panel's choice in the grid (a moment after the remote stops on it). */
    private void showChosen() {
        h.removeCallbacks(switchList);
        SidePanel.Entry e = panel.chosen();
        if (e == null || !(e.tag instanceof String)) return;
        String tag = (String) e.tag;
        if (!tag.equals(shown)) load(tag, null);
    }

    private void toGrid() {
        showChosen();
        if (grid.count() > 0) to(GRID);
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
            panel.choose(ALL);
            load(ALL, null);
            grid.jumpTo(n);
        }
        if (grid.count() > 0) to(GRID);
    }

    @Override void ok() {
        if (menu.isOpen()) {
            menu.ok();
            return;
        }
        switch (area) {
            case RAIL:
                Rail.go(tv, rail.focus, Rail.GUIDE);
                break;
            case PANEL:
                toGrid();
                break;
            default: {
                Channel c = grid.channel();
                if (c == null) break;
                // A programme already shown that the provider's archive keeps: played from its start (catch-up).
                long[] p = grid.times();
                long now = System.currentTimeMillis();
                if (p != null && p[1] <= now && Catchup.has(c, p[0], now)) tv.watchFrom(c, p[0], p[1]);
                else tv.watch(c);
                break;
            }
        }
    }

    /**
     * OK held on a programme: a small menu (a reminder for one to come, set or cancelled; watch it live, from its start;
     * the channel in the favourites or out).
     */
    @Override void okLong() {
        if (menu.isOpen()) return;
        if (area != GRID) {
            ok();
            return;
        }
        Channel c = grid.channel();
        if (c == null) return;
        long now = System.currentTimeMillis();
        long[] p = grid.times();
        Guide.Schedule s = Library.get(tv).schedule(c.key);
        int i = grid.programme();
        boolean shown = p != null && p[1] <= now, onNow = p != null && p[0] <= now && now < p[1];
        boolean archived = p != null && Catchup.has(c, p[0], now);
        // A programme to come (and in the guide): a reminder a minute before it starts, or the one set taken off.
        boolean later = p != null && p[0] > now && i >= 0 && s != null;
        boolean reminded = later && Reminders.has(tv, c.key, p[0]);
        List<String[]> items = new ArrayList<>();
        if (later) items.add(reminded ? new String[]{"unremind", "Cancel reminder"} : new String[]{"remind", "Remind me"});
        if (shown && archived) items.add(new String[]{"start", "Watch it"});
        if (onNow && archived && now - p[0] >= LiveTv.LIVE_EDGE_MS) items.add(new String[]{"start", "Watch from the start"});
        items.add(new String[]{"live", shown && archived ? "Watch " + c.name + " live" : "Watch " + c.name});
        boolean fav = Library.get(tv).isFavourite(c);
        items.add(new String[]{"fav", fav ? "Remove from favourites" : "Add to favourites"});
        String title = i >= 0 && s != null ? s.title[i] : c.name;
        String line = p == null ? c.name : Ui.day(p[0], now) + ", " + LiveText.span(p[0], p[1]) + "  ·  " + c.name;
        menu.open(title, line, items, 760f, 360f, key -> {
            switch (key) {
                case "remind":
                    remind(c, title, p[0], p[1]);
                    break;
                case "unremind":
                    Reminders.remove(tv, c.key, p[0]);
                    ReminderAlarm.schedule(tv);
                    android.widget.Toast.makeText(tv, "Reminder cancelled: " + title, android.widget.Toast.LENGTH_SHORT).show();
                    grid.invalidate();
                    break;
                case "start":
                    if (p != null) tv.watchFrom(c, p[0], p[1]);
                    break;
                case "live":
                    tv.watch(c);
                    break;
                default: {
                    boolean on = Library.get(tv).toggleFavourite(c);
                    android.widget.Toast.makeText(tv, on ? c.name + " added to your favourites" : c.name + " taken off your favourites",
                            android.widget.Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        });
    }

    /** A reminder set: kept, its alarm made, a bell on the programme; the card over other apps asked for once. */
    private void remind(Channel c, String title, long start, long end) {
        Reminders.add(tv, new Reminders.Reminder(c.key, c.name, c.logo, title, start, end));
        ReminderAlarm.schedule(tv);
        android.widget.Toast.makeText(tv, "Reminder set: " + title + ", " + Ui.day(start, System.currentTimeMillis()) + " "
                + LiveText.hm(start), android.widget.Toast.LENGTH_SHORT).show();
        grid.invalidate();
        if (ReminderCard.shouldAsk(tv)) askOverApps();
    }

    /** The first reminder: whether its card may pop up over other apps (Android's own page), asked once. */
    private void askOverApps() {
        ReminderCard.asked(tv);
        List<String[]> items = new ArrayList<>();
        items.add(new String[]{"open", "Open Android's page"});
        items.add(new String[]{"no", "Not now"});
        menu.ask("Reminders over other apps", ReminderCard.ASK, items, key -> {
            if (key.equals("open") && !ReminderCard.openSettings(tv)) {
                android.widget.Toast.makeText(tv, "This TV has no such page: reminders come as a notification instead",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override boolean back() {
        if (menu.isOpen()) {
            menu.close();
            return true;
        }
        if (area == GRID && grid.inPast()) {
            // Back from the archive's past: the guide at the time now.
            grid.toNow();
            return true;
        }
        if (area == RAIL) {
            to(PANEL);
            return true;
        }
        if (area == PANEL && grid.count() > 0) {
            to(GRID);
            return true;
        }
        return false;
    }

    @Override public void leftEdge() {
        panel.choose(shown);
        to(PANEL);
    }
}
