package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
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
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Recordings;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.OtherPrefs;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * My List in the 07 look (homefield-07-real 26), a page of 07's shell: its lists down a glass column (Continue watching,
 * My TV programmes, My reminders, Recordings, then My movies and My TV shows, each with how many), tonight's reminders in
 * a glass card under it, and the list chosen beside it as cards that lift and tilt (Card07), its name large over them:
 *
 * <pre>
 *   Continue watching   where each film or episode was left (Resume), its picture, how far in and how long is left;
 *                       OK carries on at once from the copy it was started on; MENU takes it off Continue watching
 *   My TV programmes    the programmes added to My list from the guide, each time one is on now or to come, soonest
 *                       first (their pictures, faces whole: PicTile); OK on one on now watches it; MENU takes it off
 *   My reminders        the reminders set, soonest first; OK on one on now watches it; MENU cancels it
 *   Recordings          the programmes kept from a channel's catch-up (RecordingsScreen.record), newest first, with
 *                       Scheduled, Recording, how many days are left or Gone; OK plays one that has begun from the
 *                       archive; MENU deletes it (after asking)
 *   My movies / shows   the films and series on My list as posters; OK opens one's page; MENU takes it off My List
 * </pre>
 *
 * Up and Down go along the lists (each shown at once), Right (or OK) goes into it, Left from its first column back to the
 * lists, Up from its top row (or Back on the lists) to the tab bar. The programmes' times are looked for off the main
 * thread (the guide has every channel's).
 */
final class MyListScreen extends Screen implements DialogCard.Host {
    static final int CONTINUE = 0, PROGRAMS = 1, REMINDERS = 2, RECORDINGS = 3, MOVIES = 4, SHOWS = 5;
    private static final String[] LISTS = {"Continue watching", "My TV programmes", "My reminders", "Recordings", "My movies", "My TV shows"};
    private static final int[] ICONS = {Icons07.RESTART, Icons07.TV, Icons07.BELL, Icons07.REC, Icons07.FILM, Icons07.GRID};
    private static final String[] EMPTY = {"Nothing to carry on with", "No programmes on My List", "No reminders set", "No recordings",
            "No films on My List", "No TV shows on My List"};
    private static final String[] EMPTY_HOW = {"Films and episodes you start show here, to carry on from where you left them.",
            "In the TV guide, hold OK on a programme and choose Add to My list.",
            "In the TV guide, OK on a programme to come and choose Remind.",
            "In the TV guide, OK on a programme on a channel with catch-up and choose Record.",
            "On a film's page, the + button puts it on My List.", "On a series' page, the + button puts it on My List."};
    /** A programme on My list: its title after this in My list's keys. */
    static final String PROGRAM = "prog:";
    private static final int COLUMN = 0, CONTENT = 1;

    /** One card of a list: what it stands for, its words, its picture (or its programme's place in the guide). */
    static final class Entry {
        final Object tag;
        String title = "", sub = "", picture = "";
        Channel ch;
        Guide.Schedule schedule;
        int at = -1;
        float progress = -1f;
        boolean torbox, poster, live, bell;
        String time = "";

        Entry(Object tag) {
            this.tag = tag;
        }
    }

    private Page page;
    private DialogCard dialog;
    private int which = CONTINUE, area = COLUMN, focus;
    private final List<Entry> entries = new ArrayList<>();
    private final int[] counts = new int[LISTS.length];
    /** Tonight's reminders for the card under the column: {time, title, channel}. */
    private final List<String[]> tonight = new ArrayList<>();
    private Recordings.Recording asked;
    private int ask;

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
        page = new Page(tv);
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        f.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        counts();
        which = counts[CONTINUE] > 0 ? CONTINUE : PROGRAMS;
        show(which);
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        page.active = tv.tabs() == null || !tv.tabs().active();
        counts();
        show(which);
    }

    @Override void tick() {
        counts();
        if (which != MOVIES && which != SHOWS) show(which);
    }

    @Override void channelsChanged() {
        if (which == PROGRAMS || which == REMINDERS || which == RECORDINGS) show(which);
    }

    @Override void catalogChanged() {
        counts();
        if (which == CONTINUE || which == MOVIES || which == SHOWS) show(which);
    }

    @Override boolean shell() {
        return true;
    }

    @Override int tab() {
        return TabBar.MY_LIST;
    }

    /** Down from the tab bar: into the list shown (its first card), or the lists when it is empty. */
    @Override void fromTabs() {
        page.active = true;
        area = entries.isEmpty() ? COLUMN : CONTENT;
        focus = Math.min(focus, Math.max(0, entries.size() - 1));
        page.settle();
    }

    // ---- what it shows ---------------------------------------------------------------------------------------------

    /** How many each list has, and tonight's reminders (the card under the column). */
    private void counts() {
        long now = System.currentTimeMillis();
        Catalog cat = Catalog.get(tv);
        counts[CONTINUE] = 0;
        for (Resume.Point p : Resume.all(tv)) if (cat.title(p.title) != null) counts[CONTINUE]++;
        int programs = 0, films = 0, shows = 0;
        for (String k : MyList.keys(tv)) {
            if (k.startsWith(PROGRAM)) {
                programs++;
                continue;
            }
            Catalog.Title t = cat.title(k);
            if (t == null) continue;
            if (t.series) shows++;
            else films++;
        }
        counts[PROGRAMS] = programs;
        counts[MOVIES] = films;
        counts[SHOWS] = shows;
        List<Reminders.Reminder> rem = Reminders.list(tv, now);
        counts[REMINDERS] = rem.size();
        counts[RECORDINGS] = Recordings.list(tv, now).size();
        tonight.clear();
        long end = tonightEnds(now);
        for (Reminders.Reminder r : rem) {
            if (r.start >= end || tonight.size() >= 3) continue;
            Channel c = Library.get(tv).byKey(r.key);
            int n = c == null ? 0 : Library.get(tv).number(c);
            String channel = (n > 0 ? n + " " : "") + (c == null ? r.channel : ChannelEdits.name(c));
            tonight.add(new String[]{LiveText.hm(r.start), r.title, channel});
        }
    }

    /** Pure: when tonight ends (five in the morning next, at least an hour away). */
    static long tonightEnds(long now) {
        ZonedDateTime n = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault());
        ZonedDateTime five = n.with(LocalTime.of(5, 0));
        if (!five.isAfter(n.plusHours(1))) five = five.plusDays(1);
        return five.toInstant().toEpochMilli();
    }

    /** A list shown beside the column. */
    private void show(int w) {
        which = w;
        entries.clear();
        switch (w) {
            case CONTINUE: continuing(); break;
            case PROGRAMS: programs(); break;
            case REMINDERS: reminders(); break;
            case RECORDINGS: recordings(); break;
            default: titles(w == SHOWS); break;
        }
        focus = Math.min(focus, Math.max(0, entries.size() - 1));
        if (entries.isEmpty() && area == CONTENT) area = COLUMN;
        page.made();
    }

    private void continuing() {
        Catalog cat = Catalog.get(tv);
        for (Resume.Point p : Resume.all(tv)) {
            Catalog.Title t = cat.title(p.title);
            if (t == null) continue;
            Catalog.Details d = cat.cachedDetails(t);
            Entry e = new Entry(p);
            e.title = t.name;
            String left = p.duration > p.position && p.position > 0 ? LiveText.mins(p.duration - p.position) + " left" : "";
            if (t.series) {
                String line = p.subtitle.replaceAll("\\s+·\\s+", " · ").trim();
                e.sub = line.isEmpty() ? left : left.isEmpty() ? line : line + " · " + left;
                e.picture = !p.picture.isEmpty() ? p.picture : BrowseScreen.heroPicture(t, d);
            } else {
                e.sub = "Film" + (left.isEmpty() ? "" : " · " + left) + (t.torbox() ? " · from TorBox" : "");
                e.picture = BrowseScreen.heroPicture(t, d);
            }
            e.progress = p.position > 0 ? p.progress() : -1f;
            e.torbox = t.torbox();
            entries.add(e);
        }
    }

    /** The programmes on My list, each time one is on now or to come, looked for in the guide off the main thread. */
    private void programs() {
        List<String> wanted = new ArrayList<>();
        for (String k : MyList.keys(tv)) if (k.startsWith(PROGRAM)) wanted.add(k.substring(PROGRAM.length()).toLowerCase(Locale.ROOT));
        int mine = ++ask;
        if (wanted.isEmpty()) return;
        Library lib = Library.get(tv);
        long now = System.currentTimeMillis();
        Exec.work(() -> {
            List<Entry> out = airings(lib, wanted, now, 60);
            Runnable done = () -> {
                if (mine != ask || which != PROGRAMS) return;
                entries.clear();
                entries.addAll(out);
                focus = Math.min(focus, Math.max(0, entries.size() - 1));
                page.made();
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    /** Every time one of the titles is on now or to come in the guide, soonest first, at most max (any thread). */
    private static List<Entry> airings(Library lib, List<String> titles, long now, int max) {
        List<Entry> out = new ArrayList<>();
        List<long[]> found = new ArrayList<>();
        List<Object[]> what = new ArrayList<>();
        for (Channel c : lib.all()) {
            Guide.Schedule s = lib.schedule(c.key);
            if (s == null) continue;
            for (int i = Math.max(0, s.from(now)); i < s.start.length; i++) {
                if (s.stop[i] <= now) continue;
                if (!titles.contains(s.title[i].toLowerCase(Locale.ROOT))) continue;
                found.add(new long[]{s.start[i], what.size()});
                what.add(new Object[]{c, s, i});
            }
        }
        found.sort((a, b) -> Long.compare(a[0], b[0]));
        for (long[] f : found) {
            if (out.size() >= max) break;
            Object[] w = what.get((int) f[1]);
            Channel c = (Channel) w[0];
            Guide.Schedule s = (Guide.Schedule) w[1];
            int i = (Integer) w[2];
            Entry e = new Entry(new Object[]{c, s.start[i], s.stop[i], s.title[i]});
            e.title = s.title[i];
            e.live = s.start[i] <= now;
            e.sub = (e.live ? "On now · " + LiveText.left(now, s.stop[i]) : Ui.day(s.start[i], now) + " " + LiveText.hm(s.start[i]))
                    + " · " + ChannelEdits.name(c);
            e.time = e.live ? "LIVE" : LiveText.hm(s.start[i]);
            e.ch = c;
            e.schedule = s;
            e.at = i;
            e.progress = e.live ? LiveText.progress(now, s.start[i], s.stop[i]) : -1f;
            out.add(e);
        }
        return out;
    }

    private void reminders() {
        long now = System.currentTimeMillis();
        Library lib = Library.get(tv);
        for (Reminders.Reminder r : Reminders.list(tv, now)) {
            Entry e = new Entry(r);
            Channel c = lib.byKey(r.key);
            e.title = r.title;
            e.live = r.start <= now;
            e.sub = (e.live ? "On now" : Ui.day(r.start, now) + " " + LiveText.hm(r.start)) + " · " + (c == null ? r.channel : ChannelEdits.name(c));
            e.time = e.live ? "LIVE" : LiveText.hm(r.start);
            e.bell = !e.live;
            place(e, lib, c, r.start);
            if (e.at < 0) e.picture = c == null ? r.logo : "";
            entries.add(e);
        }
    }

    private void recordings() {
        long now = System.currentTimeMillis();
        Library lib = Library.get(tv);
        for (Recordings.Recording r : Recordings.list(tv, now)) {
            Entry e = new Entry(r);
            Channel c = lib.byKey(r.key);
            Channel archive = c == null ? null : Catchup.source(c);
            int days = archive == null ? 0 : archive.archiveDays;
            int st = Recordings.state(r, days, now);
            e.title = r.title.isEmpty() ? "A programme on " + r.channel : r.title;
            String state;
            switch (st) {
                case Recordings.SCHEDULED: state = "Scheduled"; break;
                case Recordings.RECORDING: state = "Recording"; break;
                case Recordings.READY: {
                    int d = (int) Math.max(1, Math.ceil((Recordings.expires(r, days) - now) / 86_400_000.0));
                    state = d + (d == 1 ? " day left" : " days left");
                    break;
                }
                default: state = "Gone"; break;
            }
            e.sub = Ui.day(r.start, now) + " " + LiveText.hm(r.start) + " · " + (c == null ? r.channel : ChannelEdits.name(c)) + " · " + state;
            e.live = st == Recordings.RECORDING;
            e.time = e.live ? "REC" : LiveText.hm(r.start);
            place(e, lib, c, r.start);
            entries.add(e);
        }
    }

    /** A programme's place in its channel's guide (for its picture), when the guide still has it. */
    private static void place(Entry e, Library lib, Channel c, long start) {
        Guide.Schedule s = c == null ? null : lib.schedule(c.key);
        int i = s == null ? -1 : s.at(start + 1);
        if (i >= 0 && s.start[i] == start) {
            e.ch = c;
            e.schedule = s;
            e.at = i;
        } else if (c != null) {
            e.ch = c;
            e.picture = LogoPack.url(c);
        }
    }

    private void titles(boolean series) {
        Catalog cat = Catalog.get(tv);
        List<Catalog.Title> mine = new ArrayList<>();
        for (String k : MyList.keys(tv)) {
            Catalog.Title t = cat.title(k);
            if (t != null && t.series == series && !mine.contains(t)) mine.add(t);
        }
        for (Catalog.Title t : mine) {
            Entry e = new Entry(t);
            e.title = t.name;
            e.sub = t.year;
            e.picture = t.poster;
            e.poster = true;
            e.torbox = t.torbox();
            Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
            e.progress = p != null && p.position > 0 ? p.progress() : -1f;
            entries.add(e);
        }
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    /** How many cards a row of the list has. */
    private int columns() {
        return which == MOVIES || which == SHOWS ? Page.POSTER_COLS : Page.WIDE_COLS;
    }

    @Override boolean key(int code, KeyEvent e) {
        if (dialog.showing()) return dialog.key(code);
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (area == COLUMN) {
            if (up) {
                if (which == 0) toBar();
                else to(which - 1);
            } else if (down) {
                if (which < LISTS.length - 1) to(which + 1);
            } else if (right) {
                if (!entries.isEmpty()) {
                    area = CONTENT;
                    page.settle();
                }
            } else {
                return code == KeyEvent.KEYCODE_DPAD_LEFT;
            }
            return true;
        }
        int cols = columns(), col = focus % cols;
        if (left) {
            if (col == 0) {
                area = COLUMN;
            } else {
                focus--;
            }
        } else if (right) {
            if (col < cols - 1 && focus + 1 < entries.size()) focus++;
        } else if (up) {
            if (focus < cols) {
                toBar();
                return true;
            }
            focus -= cols;
        } else if (down) {
            if (focus + cols < entries.size()) focus += cols;
            else if (focus / cols < (entries.size() - 1) / cols) focus = entries.size() - 1;
        } else if (code == KeyEvent.KEYCODE_MENU) {
            remove();
        } else {
            return false;
        }
        page.settle();
        return true;
    }

    private void to(int list) {
        focus = 0;
        show(list);
        page.settle();
    }

    private void toBar() {
        if (tv.tabs() == null) return;
        tv.tabs().enter();
        page.active = false;
        page.invalidate();
    }

    @Override void ok() {
        if (dialog.showing()) {
            dialog.ok();
            return;
        }
        if (area == COLUMN) {
            if (!entries.isEmpty()) {
                area = CONTENT;
                page.settle();
            }
            return;
        }
        if (focus >= entries.size()) return;
        Entry e = entries.get(focus);
        Object o = e.tag;
        if (o instanceof Resume.Point) {
            carryOn((Resume.Point) o);
        } else if (o instanceof Catalog.Title) {
            tv.openTitle((Catalog.Title) o);
        } else if (o instanceof Recordings.Recording) {
            playRecording((Recordings.Recording) o);
        } else {
            Channel c = e.ch;
            long start = o instanceof Reminders.Reminder ? ((Reminders.Reminder) o).start : (Long) ((Object[]) o)[1];
            long now = System.currentTimeMillis();
            if (c == null) {
                Toast.makeText(tv, "That channel isn't in your playlists now.", Toast.LENGTH_SHORT).show();
            } else if (start <= now) {
                tv.watch(c);
            } else {
                Toast.makeText(tv, "It's on " + Ui.day(start, now) + " at " + LiveText.hm(start) + " on " + ChannelEdits.name(c) + ".",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Continue watching: on at once from where it was (an episode needs its series' page read; else its page). */
    private void carryOn(Resume.Point p) {
        Catalog.Title t = Catalog.get(tv).title(p.title);
        if (t == null) return;
        if (!t.series && p.episode.isEmpty()) {
            tv.play(t, null, p.position, false);
            return;
        }
        Catalog.get(tv).details(t, d -> {
            Catalog.Episode e = d == null ? null : d.episode(p.episode);
            if (e != null) tv.play(t, e, p.position, false);
            else tv.openTitle(t);
        });
    }

    /** A recording from the archive (as RecordingsScreen plays one), or why it can't be. */
    private void playRecording(Recordings.Recording r) {
        long now = System.currentTimeMillis();
        Channel c = Library.get(tv).byKey(r.key);
        Channel archive = c == null ? null : Catchup.source(c);
        int st = Recordings.state(r, archive == null ? 0 : archive.archiveDays, now);
        if (c == null) {
            Toast.makeText(tv, r.channel + " isn't in your playlists now.", Toast.LENGTH_SHORT).show();
        } else if (st == Recordings.SCHEDULED) {
            Toast.makeText(tv, "It's on " + Ui.day(r.start, now) + " at " + LiveText.hm(r.start) + ": it can be watched once it starts.",
                    Toast.LENGTH_SHORT).show();
        } else if (st == Recordings.GONE) {
            Toast.makeText(tv, "It has gone from " + ChannelEdits.name(c) + "'s catch-up.", Toast.LENGTH_SHORT).show();
        } else {
            long[] span = Recordings.span(r, OtherPrefs.before(tv), OtherPrefs.after(tv));
            long from = Math.max(span[0], Catchup.earliest(c, now));
            tv.watchFrom(c, from, Math.min(span[1], Math.max(r.end, now)));
        }
    }

    /** MENU: the card the remote is on taken off its list (a recording after asking). */
    private void remove() {
        if (focus >= entries.size()) return;
        Entry e = entries.get(focus);
        Object o = e.tag;
        String said;
        if (o instanceof Resume.Point) {
            Resume.remove(tv, ((Resume.Point) o).title);
            said = e.title + " taken off Continue watching";
        } else if (o instanceof Catalog.Title) {
            MyList.toggle(tv, ((Catalog.Title) o).key);
            said = e.title + " taken off My list";
        } else if (o instanceof Reminders.Reminder) {
            ReminderSettings.cancel(tv, ((Reminders.Reminder) o).id());
            said = "Reminder cancelled";
        } else if (o instanceof Recordings.Recording) {
            asked = (Recordings.Recording) o;
            List<RowList.Row> answers = new ArrayList<>();
            answers.add(RowList.Row.item("yes", "Delete", ""));
            answers.add(RowList.Row.item("no", "Cancel", ""));
            dialog.show("Delete this recording?", e.title + "  ·  " + e.sub, answers, 1);
            return;
        } else {
            String title = (String) ((Object[]) o)[3];
            // Off My list by its title: every time it is on goes with it.
            for (String k : MyList.keys(tv)) {
                if (k.startsWith(PROGRAM) && k.substring(PROGRAM.length()).equalsIgnoreCase(title)) MyList.toggle(tv, k);
            }
            said = title + " taken off My list";
        }
        Toast.makeText(tv, said, Toast.LENGTH_SHORT).show();
        counts();
        show(which);
        page.settle();
    }

    @Override public void answered(DialogCard d, RowList.Row answer) {
        if ("yes".equals(answer.tag) && asked != null) Recordings.remove(tv, asked.id());
        asked = null;
        dialog.hide(null);
        counts();
        show(which);
        page.settle();
    }

    @Override boolean back() {
        if (dialog.showing()) {
            dialog.hide(null);
            return true;
        }
        if (area == CONTENT) {
            area = COLUMN;
            page.settle();
            return true;
        }
        toBar();
        return true;
    }

    /** Previews: one of the lists shown (CONTINUE…), the remote in it at a card when in. */
    void previewList(int list, boolean in) {
        counts();
        focus = 0;
        show(list);
        area = in && !entries.isEmpty() ? CONTENT : COLUMN;
        page.settle();
    }

    // ---- what is drawn --------------------------------------------------------------------------------------------

    /** The page: its picture behind (the card the remote is on), the column, tonight's reminders, the list's cards. */
    private final class Page extends View {
        static final int WIDE_COLS = 3, POSTER_COLS = 6;
        static final float COL_X = 90f, COL_Y = 140f, COL_W = 380f, ITEM_H = 74f, CONTENT_X = 520f, CARDS_TOP = 272f,
                WIDE_W = 400f, WIDE_H = 225f, WIDE_STEP_X = 440f, WIDE_STEP_Y = 352f, POSTER_W = 196f, POSTER_H = 294f,
                POSTER_STEP_X = 222f, POSTER_STEP_Y = 392f;
        final Ground ground = new Ground(this);
        /** Whether the page has the remote (false while it is up on the tab bar: nothing lifted). */
        boolean active = true;
        private final Frost glass = new Frost(Look07.GLASS);
        private final Card07 wides = new Card07(Look07.R_CARD), posters = new Card07(Look07.R_POSTER);
        private final Card07.Marks marks = new Card07.Marks();
        private final PicTile pictures = new PicTile(this);
        private final Lift07 lift = new Lift07(), columnLift = new Lift07();
        private final Glide scroll = new Glide(Look07.SCROLL_MS);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rule = Ui.fill(Look07.RULE);
        private final TextPaint itemP = Type.paint(23f, Type.MEDIUM, Look07.L2), itemOn = Type.paint(23f, Type.SEMIBOLD, Look07.L1),
                countP = Type.paint(18f, Type.SEMIBOLD, Look07.L3), headP = Type.paint(Type.H2, Type.BOLD, Look07.L1),
                subP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2), labelP = Type.label(Type.LABEL, Look07.L3),
                timeP = Type.clock(20f, Type.BOLD, Look07.AMBER), remTitle = Type.paint(20f, Type.SEMIBOLD, Look07.L1),
                remChannel = Type.paint(18f, Type.REGULAR, Look07.L2), emptyP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L3);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private String heading = "", sub = "";
        private final String[] countWords = new String[LISTS.length];
        /** The column's lists' keys for its lift (kept: nothing made a frame). */
        private final Object[] listKeys = new Object[LISTS.length];
        private final List<String[]> remWords = new ArrayList<>();

        Page(Context c) {
            super(c);
            pictures.colour(0xFF161C2E);
            for (int i = 0; i < listKeys.length; i++) listKeys[i] = new Object();
            Shadows.prepare(Look07.SH_ROW, Look07.R_MENU_ITEM);
            Pills.prepare();
        }

        /** The list's words made (its name, its line, the counts, the reminders card's): when it changes, not each frame. */
        void made() {
            heading = LISTS[which];
            int n = entries.size();
            switch (which) {
                case CONTINUE:
                    sub = n == 0 ? "" : count(n) + " in progress · Resume carries on from the copy you started";
                    break;
                case PROGRAMS:
                    sub = n == 0 ? "" : count(n) + (n == 1 ? " time" : " times") + " on now or to come · OK watches one on now";
                    break;
                case REMINDERS:
                    sub = n == 0 ? "" : count(n) + " set · each card comes a minute before it starts";
                    break;
                case RECORDINGS:
                    sub = n == 0 ? "" : "Kept from your channels' catch-up · OK plays one that has begun";
                    break;
                default:
                    sub = n == 0 ? "" : count(n) + " on My List";
                    break;
            }
            for (int i = 0; i < LISTS.length; i++) countWords[i] = counts[i] > 0 ? String.valueOf(counts[i]) : "";
            remWords.clear();
            remWords.addAll(tonight);
            settle();
        }

        /** "Six", "12": a count as words up to ten. */
        private String count(int n) {
            String[] w = {"None", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten"};
            return n <= 10 ? w[n] : String.valueOf(n);
        }

        /** The page sent where the remote now is: its picture, the cards' scroll. */
        void settle() {
            Entry e = entries.isEmpty() ? null : entries.get(Math.min(focus, entries.size() - 1));
            String url = "";
            if (e != null) {
                if (!e.picture.isEmpty()) url = e.picture;
                else if (e.ch != null && e.at >= 0) {
                    String p = ProgrammePictures.peek(e.ch, e.schedule, e.at);
                    url = p != null && !p.isEmpty() ? p : LogoPack.url(e.ch);
                }
            }
            ground.set(url);
            boolean posters = which == MOVIES || which == SHOWS;
            int cols = posters ? POSTER_COLS : WIDE_COLS;
            float stepY = posters ? POSTER_STEP_Y : WIDE_STEP_Y;
            int rowAt = area == CONTENT ? focus / cols : 0;
            scroll.to(Math.max(0, rowAt - 1) * stepY);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = ground.draw(c, getWidth(), getHeight());
            moving |= scroll.step();
            Entry on = active && area == CONTENT && focus < entries.size() ? entries.get(focus) : null;
            moving |= lift.step(on);
            moving |= columnLift.step(active && area == COLUMN ? listKeys[which] : null);
            drawCards(c);
            drawColumn(c);
            drawReminders(c);
            drawHints(c);
            if (moving) postInvalidateOnAnimation();
        }

        private void drawColumn(Canvas c) {
            float x = Ui.f(COL_X), y = Ui.f(COL_Y), w = Ui.f(COL_W);
            float h = Ui.f(22f) * 2 + Ui.f(ITEM_H) * LISTS.length + Ui.f(21f);
            r.set(x, y, x + w, y + h);
            glass.draw(c, r, Ui.f(40f), 1f);
            float iy = y + Ui.f(22f);
            for (int i = 0; i < LISTS.length; i++) {
                if (i == MOVIES) {
                    // A rule between the lists of the guide's and the titles'.
                    c.drawRect(x + Ui.f(34f), iy + Ui.f(10f), x + w - Ui.f(34f), iy + Ui.f(11f), rule);
                    iy += Ui.f(21f);
                }
                boolean here = active && area == COLUMN && i == which, chosen = i == which;
                float up = here ? Math.max(0f, Math.min(1.1f, columnLift.of(listKeys[i]))) : 0f;
                r.set(x + Ui.f(16f), iy, x + w - Ui.f(16f), iy + Ui.f(ITEM_H));
                c.save();
                if (here) {
                    c.scale(1f + 0.025f * up, 1f + 0.025f * up, r.centerX(), r.centerY());
                    Shadows.draw(c, r, Ui.f(20f), Look07.SH_ROW, Math.min(1f, up));
                    fill.setColor(Look07.FOCUS_FILL);
                    c.drawRoundRect(r, Ui.f(20f), Ui.f(20f), fill);
                } else if (chosen) {
                    fill.setColor(0x2EFFFFFF);
                    c.drawRoundRect(r, Ui.f(20f), Ui.f(20f), fill);
                }
                float cy = r.centerY();
                int iconColour = here ? Look07.INK : chosen ? Look07.AMBER : Look07.L3;
                Icons07.draw(c, ICONS[i], r.left + Ui.f(18f) + Ui.f(12f), cy, Ui.f(24f), iconColour);
                TextPaint p = chosen ? itemOn : itemP;
                p.setColor(here ? Look07.INK : chosen ? Look07.L1 : Look07.L2);
                c.drawText(LISTS[i], r.left + Ui.f(18f + 24f + 16f), Type.centred(p, cy), p);
                if (!countWords[i].isEmpty()) {
                    countP.setColor(here ? Look07.INK2 : Look07.L3);
                    c.drawText(countWords[i], r.right - Ui.f(18f) - countP.measureText(countWords[i]), Type.centred(countP, cy), countP);
                }
                c.restore();
                iy += Ui.f(ITEM_H);
            }
        }

        /** Tonight's reminders in a glass card under the column: time in amber, title, channel. */
        private void drawReminders(Canvas c) {
            if (remWords.isEmpty()) return;
            float x = Ui.f(COL_X), w = Ui.f(COL_W), rowH = Ui.f(45f) + Ui.f(14f);
            float h = Ui.f(22f) * 2 + Ui.f(18f) + remWords.size() * rowH;
            float y = Ui.f(980f) - h;
            r.set(x, y, x + w, y + h);
            glass.draw(c, r, Ui.f(34f), 1f);
            float tx = x + Ui.f(24f), ty = y + Ui.f(22f);
            c.drawText("MY REMINDERS · TONIGHT", tx, ty + Ui.f(14f), labelP);
            ty += Ui.f(18f) + Ui.f(14f);
            for (String[] rem : remWords) {
                float cy = ty + Ui.f(22f);
                c.drawText(rem[0], tx, Type.centred(timeP, cy), timeP);
                c.drawText(Ui.fit(rem[1], remTitle, w - Ui.f(24f + 76f + 16f)), tx + Ui.f(76f), cy - Ui.f(4f), remTitle);
                c.drawText(rem[2], tx + Ui.f(76f), cy + Ui.f(19f), remChannel);
                ty += rowH;
            }
        }

        private void drawCards(Canvas c) {
            float x0 = Ui.f(CONTENT_X), sy = Ui.f(scroll.value());
            // The list's name and its line.
            float hy = Ui.f(140f) - sy;
            c.drawText(heading, x0, Hero07.baseline(hy, Hero07.normal(Ui.f(46f)), Ui.f(46f)), headP);
            if (!sub.isEmpty()) c.drawText(sub, x0, Hero07.baseline(hy + Ui.f(56f), Hero07.normal(Ui.f(23f)), Ui.f(23f)), subP);
            if (entries.isEmpty()) {
                c.drawText(EMPTY[which], x0, Ui.f(330f), emptyP);
                c.drawText(EMPTY_HOW[which], x0, Ui.f(372f), emptyP);
                return;
            }
            boolean posters = which == MOVIES || which == SHOWS;
            int cols = posters ? POSTER_COLS : WIDE_COLS;
            float w = Ui.f(posters ? POSTER_W : WIDE_W), h = Ui.f(posters ? POSTER_H : WIDE_H);
            float stepX = Ui.f(posters ? POSTER_STEP_X : WIDE_STEP_X), stepY = Ui.f(posters ? POSTER_STEP_Y : WIDE_STEP_Y);
            int lifted = -1;
            for (int i = 0; i < entries.size(); i++) {
                float x = x0 + (i % cols) * stepX, y = Ui.f(CARDS_TOP) + (i / cols) * stepY - sy;
                if (y > getHeight()) break;
                if (y + h + Ui.f(90f) < 0) continue;
                if (area == CONTENT && i == focus) {
                    lifted = i;
                    continue;
                }
                card(c, entries.get(i), x, y, w, h, lift.of(entries.get(i)), posters);
            }
            if (lifted >= 0) {
                float x = x0 + (lifted % cols) * stepX, y = Ui.f(CARDS_TOP) + (lifted / cols) * stepY - sy;
                card(c, entries.get(lifted), x, y, w, h, lift.of(entries.get(lifted)), posters);
            }
        }

        private void card(Canvas c, Entry e, float x, float y, float w, float h, float up, boolean poster) {
            Card07 cards = poster ? posters : wides;
            marks.clear();
            marks.torbox = e.torbox;
            marks.progress = e.progress;
            if (e.ch != null && e.at >= 0) {
                // A programme: its picture through PicTile (faces whole), its time or LIVE.
                cards.begin(c, x, y, w, h, up, null);
                r.set(0, 0, w, h);
                pictures.draw(c, r, Ui.f(Look07.R_CARD), e.ch, e.schedule, e.at, PicTile.COVER, up > 0.5f, false, true);
                if (e.live) marks.live = true;
                else {
                    marks.time = e.time;
                    marks.bell = e.bell;
                }
                cards.marks(c, w, h, marks);
                cards.end(c);
            } else {
                String url = e.picture;
                int mode = poster ? Pics.CROP : Pics.FACE_MID;
                Bitmap b = url.isEmpty() ? null : Pics.get(url, cards.picW(w), cards.picH(h), mode, redraw);
                if (b == null && e.ch != null) {
                    cards.begin(c, x, y, w, h, up, null);
                    r.set(0, 0, w, h);
                    pictures.draw(c, r, Ui.f(Look07.R_CARD), e.ch, null, -1, PicTile.COVER, false, false, true);
                    if (!e.time.isEmpty()) marks.time = e.time;
                    cards.marks(c, w, h, marks);
                    cards.end(c);
                } else {
                    cards.begin(c, x, y, w, h, up, b);
                    if (b == null) {
                        fill.setColor(PosterArt.blank(e.title));
                        c.drawRoundRect(0, 0, w, h, Ui.f(Look07.R_CARD), Ui.f(Look07.R_CARD), fill);
                    }
                    cards.marks(c, w, h, marks);
                    cards.end(c);
                }
            }
            cards.caption(c, x, y + h, w, up, e.title, e.sub);
        }

        /** The keys' hints at the foot, for the list shown. */
        private void drawHints(Canvas c) {
            if (entries.isEmpty()) return;
            float x = Ui.f(CONTENT_X), cy = Ui.f(1021f);
            String ok, menu;
            switch (which) {
                case CONTINUE: ok = "Resume"; menu = "Remove from Continue watching"; break;
                case PROGRAMS: ok = "Watch one on now"; menu = "Take off My List"; break;
                case REMINDERS: ok = "Watch one on now"; menu = "Cancel the reminder"; break;
                case RECORDINGS: ok = "Play"; menu = "Delete"; break;
                default: ok = "Open"; menu = "Take off My List"; break;
            }
            x += Pills.key(c, x, cy, "OK") + Ui.f(8f);
            x += Pills.hint(c, x, cy, ok) + Ui.f(24f);
            x += Pills.key(c, x, cy, "≡") + Ui.f(8f);
            x += Pills.hint(c, x, cy, menu) + Ui.f(24f);
            x += Pills.key(c, x, cy, "◀") + Ui.f(8f);
            Pills.hint(c, x, cy, "Lists");
        }
    }
}
