package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.engine.Resume;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * My list, from the menu, as the family's other app has it: the rail, then its lists down a column (GroupsPanel: My
 * TV programs, My reminders, My movies, My TV shows; the remote's row held at one height), the list the remote is on
 * beside it at once. Right (or OK) goes into the list, the column going and the list sliding to the left edge.
 *
 * <pre>
 *   My TV programs   the programmes added to My list (by their title, toggleProgram), each time one is on now or to
 *                    come in the guide, soonest first, as History's rows (ProgrammeList); OK on one on now watches it
 *   My reminders     the reminders set in the guide (Reminders), soonest first, each with its bell; delete cancels one
 *   My movies        the films on My list (the bookmark on a title's page), as Movies' posters under their words
 *   My TV shows      the same for series
 * </pre>
 *
 * An empty list shows the bookmark and its words where the other app has them. The programmes' times in the guide are
 * looked for off the main thread (the guide has every channel's).
 */
final class MyListScreen extends Screen implements GroupsPanel.Host, ProgrammeList.Host, PosterGrid.Host {
    private static final String[] LISTS = {"My TV programs", "My reminders", "My movies", "My TV shows"};
    private static final String[] EMPTY = {"No programs", "No reminders", "No movies", "No TV shows"};
    static final int PROGRAMS = 0, REMINDERS = 1, MOVIES = 2, SHOWS = 3;
    /** A programme on My list: its title after this in My list's keys. */
    static final String PROGRAM = "prog:";
    private static final int RAIL = 0, COLUMN = 1, CONTENT = 2;

    private Rail rail;
    private GroupsPanel column;
    private ProgrammeList list;
    private PosterGrid grid;
    private VodHero hero;
    private Backdrop picture;
    private Empty empty;
    private int which = PROGRAMS, area = COLUMN;
    private float contentX = BrowseScreen.WITH_COLUMN;
    private ValueAnimator slide;
    private int asked;
    private final Handler h = new Handler(Looper.getMainLooper());

    MyListScreen(TvActivity tv) {
        super(tv);
    }

    // ---- the guide's Add to My list ---------------------------------------------------------------------------------

    /** A programme put on My list by its title, or taken off it: true when it is on it now. */
    static boolean toggleProgram(Context c, String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) return false;
        return MyList.toggle(c, PROGRAM + t);
    }

    /** Whether a programme (by its title) is on My list. */
    static boolean hasProgram(Context c, String title) {
        return title != null && MyList.has(c, PROGRAM + title.trim());
    }

    // ---- the screen -------------------------------------------------------------------------------------------------

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Palette.GROUND);
        rail = new Rail(tv, Rail.MY_LIST);
        column = new GroupsPanel(tv);
        column.x = Rail.W;
        column.setHost(this);
        List<GroupsPanel.Entry> entries = new ArrayList<>();
        for (int i = 0; i < LISTS.length; i++) entries.add(GroupsPanel.Entry.plain(LISTS[i], i));
        column.set(entries, 0);
        column.choose(0);
        picture = new Backdrop(tv, 800f, 0f, 1120f, 640f, Pics.DARK);
        hero = new VodHero(tv);
        grid = new PosterGrid(tv);
        grid.setHost(this);
        list = new ProgrammeList(tv);
        list.setHost(this);
        empty = new Empty(tv);
        f.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        f.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        f.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        f.addView(list, new FrameLayout.LayoutParams(-1, -1));
        f.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        f.addView(column, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        show(which);
        place(true);
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        show(which);
    }

    @Override void hidden() {
        h.removeCallbacksAndMessages(null);
    }

    @Override void tick() {
        if (which == PROGRAMS || which == REMINDERS) show(which);
    }

    @Override void channelsChanged() {
        if (which == PROGRAMS || which == REMINDERS) show(which);
    }

    @Override void catalogChanged() {
        if (which == MOVIES || which == SHOWS) show(which);
    }

    /** A list shown beside the column. */
    private void show(int w) {
        which = w;
        boolean titles = w == MOVIES || w == SHOWS;
        grid.setVisibility(titles ? View.VISIBLE : View.GONE);
        hero.setVisibility(titles ? View.VISIBLE : View.GONE);
        picture.setVisibility(titles ? View.VISIBLE : View.GONE);
        list.setVisibility(titles ? View.GONE : View.VISIBLE);
        switch (w) {
            case PROGRAMS: programs(); break;
            case REMINDERS: reminders(); break;
            default: titles(w == SHOWS); break;
        }
        empty.invalidate();
    }

    private void reminders() {
        long now = System.currentTimeMillis();
        Library lib = Library.get(tv);
        List<ProgrammeList.Entry> out = new ArrayList<>();
        for (Reminders.Reminder r : Reminders.list(tv, now)) {
            ProgrammeList.Entry e = new ProgrammeList.Entry(r, Ui.day(r.start, now));
            Channel c = lib.byKey(r.key);
            e.title = r.title;
            e.when = ProgrammeList.when(r.start, r.end);
            e.channel = c == null ? r.channel : c.name;
            e.logo = c == null ? r.logo : c.logo;
            e.bell = true;
            e.now = r.start <= now;
            e.stateLit = e.now;
            e.state = e.now ? "On now" : "";
            e.cardLine = e.now ? "On now: OK watches it" : "Its card comes a minute before it starts";
            Guide.Schedule s = c == null ? null : lib.schedule(c.key);
            int i = s == null ? -1 : s.at(r.start + 1);
            if (i >= 0 && s.start[i] == r.start) e.about = s.desc[i];
            out.add(e);
        }
        list.set(out);
    }

    /** The programmes on My list, each time one is on now or to come, looked for in the guide off the main thread. */
    private void programs() {
        List<String> wanted = new ArrayList<>();
        for (String k : MyList.keys(tv)) if (k.startsWith(PROGRAM)) wanted.add(k.substring(PROGRAM.length()).toLowerCase(Locale.ROOT));
        int ask = ++asked;
        if (wanted.isEmpty()) {
            list.set(new ArrayList<>());
            return;
        }
        Library lib = Library.get(tv);
        long now = System.currentTimeMillis();
        Exec.work(() -> {
            List<ProgrammeList.Entry> out = airings(lib, wanted, now, 60);
            Runnable done = () -> {
                if (ask != asked || which != PROGRAMS) return;
                list.set(out);
                empty.invalidate();
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    /** Every time one of the titles is on now or to come in the guide, soonest first, at most max (any thread). */
    private static List<ProgrammeList.Entry> airings(Library lib, List<String> titles, long now, int max) {
        List<long[]> found = new ArrayList<>();
        List<Object[]> what = new ArrayList<>();
        for (Channel c : lib.all()) {
            Guide.Schedule s = lib.schedule(c.key);
            if (s == null) continue;
            for (int i = s.from(now); i < s.start.length; i++) {
                if (s.stop[i] <= now) continue;
                if (!titles.contains(s.title[i].toLowerCase(Locale.ROOT))) continue;
                found.add(new long[]{s.start[i], s.stop[i], what.size()});
                what.add(new Object[]{c, s.title[i], s.desc[i]});
            }
        }
        found.sort((a, b) -> Long.compare(a[0], b[0]));
        List<ProgrammeList.Entry> out = new ArrayList<>();
        for (long[] f : found) {
            if (out.size() >= max) break;
            Object[] w = what.get((int) f[2]);
            Channel c = (Channel) w[0];
            ProgrammeList.Entry e = new ProgrammeList.Entry(new Object[]{c, f[0], f[1], w[1]}, Ui.day(f[0], now));
            e.title = (String) w[1];
            e.when = ProgrammeList.when(f[0], f[1]);
            e.channel = c.name;
            e.logo = c.logo;
            e.about = (String) w[2];
            e.archive = Catchup.source(c) != null;
            e.now = f[0] <= now;
            e.stateLit = e.now;
            e.state = e.now ? "On now" : "";
            e.cardLine = e.now ? "On now: OK watches it" : "";
            out.add(e);
        }
        return out;
    }

    private void titles(boolean series) {
        Catalog cat = Catalog.get(tv);
        List<Catalog.Title> mine = new ArrayList<>();
        for (String k : MyList.keys(tv)) {
            Catalog.Title t = cat.title(k);
            if (t != null && t.series == series && !mine.contains(t)) mine.add(t);
        }
        grid.set(LISTS[which], new PosterArt.Items(mine, t -> {
            PosterArt.Item it = new PosterArt.Item(t);
            Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
            if (p != null && p.position > 0) it.progress = p.progress();
            return it;
        }), true);
        showHero();
    }

    private void showHero() {
        PosterArt.Item it = grid.item();
        if (it == null) {
            hero.clear();
            picture.set("");
            return;
        }
        Catalog.Details d = Catalog.get(tv).cachedDetails(it.title);
        hero.show(it.title, d, it.title.torbox() ? Catalog.TORBOX : "");
        picture.set(d != null && !d.backdrop.isEmpty() ? d.backdrop : it.title.backdrop);
    }

    private boolean empty() {
        return which == MOVIES || which == SHOWS ? grid.isEmpty() : list.isEmpty();
    }

    // ---- where things are --------------------------------------------------------------------------------------

    private void place(boolean now) {
        rail.setActive(area == RAIL);
        column.setActive(area == COLUMN);
        column.setVisibility(area == CONTENT ? View.GONE : View.VISIBLE);
        // In a list the menu's rail goes too, as in Movies: the list from the screen's edge.
        rail.setVisibility(area == CONTENT ? View.GONE : View.VISIBLE);
        list.setActive(area == CONTENT);
        grid.setActive(area == CONTENT);
        float to = area == CONTENT ? BrowseScreen.WITHOUT : BrowseScreen.WITH_COLUMN;
        if (slide != null) slide.cancel();
        if (now || Exec.inline) {
            contentX = to;
            moved();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(contentX, to);
        a.setDuration(150);
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            contentX = (Float) v.getAnimatedValue();
            moved();
        });
        slide = a;
        a.start();
    }

    private void moved() {
        hero.moveTo(contentX);
        hero.w = Math.min(860f, 1420f - contentX);
        grid.moveTo(contentX - 4f);
        // The programmes' list: beside the column, its card hidden; at the left edge with its card.
        list.left = area == CONTENT ? 48f : contentX - 32f;
        list.card = area == CONTENT;
        list.invalidate();
    }

    private void to(int a) {
        area = a;
        place(false);
        if (a == COLUMN) column.slideIn();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        switch (area) {
            case RAIL:
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    to(COLUMN);
                    return true;
                }
                return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
            case COLUMN:
                return column.key(code);
            default:
                return which == MOVIES || which == SHOWS ? grid.key(code) : list.key(code);
        }
    }

    @Override void ok() {
        switch (area) {
            case RAIL:
                if (rail.focus == Rail.MY_LIST) to(COLUMN);
                else Rail.go(tv, rail.focus, Rail.MY_LIST);
                break;
            case COLUMN:
                column.ok();
                break;
            default:
                if (which == MOVIES || which == SHOWS) grid.ok();
                else list.ok();
                break;
        }
    }

    @Override boolean back() {
        if (area == CONTENT) {
            to(COLUMN);
            return true;
        }
        if (area == COLUMN) {
            to(RAIL);
            return true;
        }
        return false;
    }

    // ---- the column ---------------------------------------------------------------------------------------------

    @Override public void focused(GroupsPanel panel, GroupsPanel.Entry entry) {
        // The list the remote is on, at once.
        column.choose(entry.tag);
        show((Integer) entry.tag);
    }

    @Override public void chosen(GroupsPanel panel, GroupsPanel.Entry entry) {
        if (!empty()) to(CONTENT);
    }

    @Override public boolean edge(GroupsPanel panel, int dir) {
        if (dir == Ui.LEFT) to(RAIL);
        else if (dir == Ui.RIGHT && !empty()) to(CONTENT);
        return true;
    }

    // ---- the programmes and reminders ----------------------------------------------------------------------------

    @Override public void chosen(ProgrammeList l, ProgrammeList.Entry e) {
        long now = System.currentTimeMillis();
        Library lib = Library.get(tv);
        Channel c;
        long start;
        if (e.tag instanceof Reminders.Reminder) {
            Reminders.Reminder r = (Reminders.Reminder) e.tag;
            c = lib.byKey(r.key);
            start = r.start;
        } else {
            Object[] p = (Object[]) e.tag;
            c = (Channel) p[0];
            start = (Long) p[1];
        }
        if (c == null) {
            Toast.makeText(tv, "That channel isn't in your playlists now.", Toast.LENGTH_SHORT).show();
        } else if (start <= now) {
            tv.watch(c);
        } else {
            Toast.makeText(tv, "It's on " + Ui.day(start, now) + " at " + LiveText.hm(start) + " on " + c.name + ".", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void delete(ProgrammeList l, ProgrammeList.Entry e) {
        if (e.tag instanceof Reminders.Reminder) {
            ReminderSettings.cancel(tv, ((Reminders.Reminder) e.tag).id());
            Toast.makeText(tv, "Reminder cancelled", Toast.LENGTH_SHORT).show();
        } else {
            String title = (String) ((Object[]) e.tag)[3];
            // Off My list by its title: every time it is on goes with it.
            for (String k : MyList.keys(tv)) {
                if (k.startsWith(PROGRAM) && k.substring(PROGRAM.length()).equalsIgnoreCase(title)) MyList.toggle(tv, k);
            }
            Toast.makeText(tv, title + " taken off My list", Toast.LENGTH_SHORT).show();
        }
        show(which);
        if (empty()) to(COLUMN);
    }

    @Override public void focused(ProgrammeList l, ProgrammeList.Entry e) {}

    @Override public boolean edge(ProgrammeList l, int dir) {
        if (dir == Ui.LEFT) to(COLUMN);
        return true;
    }

    // ---- the posters ------------------------------------------------------------------------------------------------

    @Override public void focused(PosterArt.Item item) {
        showHero();
    }

    @Override public void chosen(PosterArt.Item item) {
        tv.openTitle(item.title);
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.LEFT) to(COLUMN);
        return true;
    }

    /** Previews: one of the lists shown (PROGRAMS…), the remote in it when in. */
    void previewList(int list, boolean in) {
        column.choose(list);
        show(list);
        area = in && !empty() ? CONTENT : COLUMN;
        place(true);
    }

    /** The list's empty message, where the other app has it: the right of the screen. */
    private final class Empty extends View {
        private final RectF r = new RectF();

        Empty(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            if (!empty()) return;
            r.set(Ui.f(1232), 0, Ui.f(1920), getHeight());
            EmptyState.draw(c, r, Dark.BOOKMARK, 120f, EMPTY[which], "");
        }
    }
}
