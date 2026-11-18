package com.gathin.homefieldtv;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.OtherPrefs;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search in the 07 look (homefield-07-real 25), a page of 07's shell (its glass lit in the tab bar): the microphone (voice
 * search, where the TV has it), the long glass field, search's settings; the playlists the words were found in as
 * chips (All playlists, Home, Your TorBox…: the one chosen white); then what the words find, live as they are typed:
 * Movies and TV shows as posters that lift and tilt, and Channels and programmes, the channels called that and what is
 * on tonight in the guide called that (live first, with how long is left, then later with its time), each as a tile with
 * its programme's picture (its faces whole: PicTile), its channel's logo, number and name. The keyboard (SearchKeys)
 * floats at the right while typing, the names found suggested across its top, and the results are dimmed under it.
 * Empty, the searches made before as pills with a bin to clear them.
 *
 * <p>The search waits for the typing to rest (TYPE_MS, 180 ms), then goes through the channels, the titles and tonight's
 * guide off the main thread (Exec.work); an answer to older words is let go. Down from the keyboard or the field goes to
 * the chips, then the results; OK on a channel or a programme on now watches it, on a later one sets its reminder, on a
 * title opens its page. Back closes the keyboard (the remote left on the field), then Search.
 */
final class SearchScreen extends Screen implements SearchKeys.Host {
    static final long TYPE_MS = 180;
    /** Android's voice input's answer (TvActivity hands it to heard()). */
    static final int VOICE = 0x5EA2;
    private static final int MOVIES = 0, SHOWS = 1, CHANNELS = 2;
    private static final String[] ROW_NAMES = {"Movies", "TV shows", "Channels and programmes"};
    private static final int MIC = 0, FIELD = 1, GEAR = 2, KEYS = 3, TRASH = 4, HISTORY = 5, CHIPS = 6, RESULTS = 7;
    private static final int MAX = 40;

    /** A channel or a programme on it tonight: the programme's place in its guide (-1: none known). */
    static final class Airing {
        final Channel channel;
        final Guide.Schedule schedule;
        final int at;

        Airing(Channel channel, Guide.Schedule schedule, int at) {
            this.channel = channel;
            this.schedule = schedule;
            this.at = at;
        }
    }

    /** One row of results: its kind, everything found, what the chosen playlist shows, the remote's place. */
    private static final class Row {
        final int kind;
        final List<Object> all;
        final List<Object> shown = new ArrayList<>();
        final List<String> names = new ArrayList<>(), lines = new ArrayList<>(), marks = new ArrayList<>();
        /** Each programme's: on now (LIVE · 40 min left), and its reminder set (a bell by its time). */
        final List<Boolean> live = new ArrayList<>(), bell = new ArrayList<>();
        int focus;
        final Glide x = new Glide(Look07.SCROLL_MS);
        String count = "";

        Row(int kind, List<Object> all) {
            this.kind = kind;
            this.all = all;
        }
    }

    private SearchKeys keys;
    private Page page;
    private int area = KEYS;
    private final List<String> history = new ArrayList<>();
    private int historyFocus;
    private final List<Row> rows = new ArrayList<>();
    private int row;
    /** The playlists found in (id, name), "All playlists" first; the one chosen; the remote's chip. */
    private final List<String[]> chips = new ArrayList<>();
    private int chosen, chipAt;
    /** The results' scroll (design px), gliding on the display's frame clock. */
    private final Glide scroll = new Glide(Look07.SCROLL_MS);
    private boolean searching;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable run = this::search;
    /** The search asked last: an answer to an older one is let go. */
    private int asked;

    SearchScreen(TvActivity tv) {
        super(tv);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new Page(tv);
        keys = new SearchKeys(this);
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        history.clear();
        if (OtherPrefs.showHistory(tv)) history.addAll(OtherPrefs.history(tv));
        return f;
    }

    /** Previews and tests: the words typed already. */
    void type(String text) {
        keys.setText(text);
        search();
    }

    /** Previews and tests: the remote in the results, on a row and a card (row -1: back on the keyboard). */
    void previewResults(int rowIndex, int index) {
        if (rowIndex < 0 || rows.isEmpty()) {
            to(KEYS);
            return;
        }
        row = Math.min(rowIndex, rows.size() - 1);
        Row r = rows.get(row);
        r.focus = Math.max(0, Math.min(index, r.shown.size() - 1));
        r.x.jump(target(r));
        to(RESULTS);
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        history.clear();
        if (OtherPrefs.showHistory(tv)) history.addAll(OtherPrefs.history(tv));
        page.invalidate();
    }

    @Override void hidden() {
        h.removeCallbacks(run);
    }

    @Override void channelsChanged() {
        h.removeCallbacks(run);
        h.postDelayed(run, 150);
    }

    /** Each minute: the minutes left on what is on now. */
    @Override void tick() {
        if (!rows.isEmpty()) {
            choose(chosen);
            page.invalidate();
        }
    }

    @Override void catalogChanged() {
        channelsChanged();
    }

    @Override boolean shell() {
        return true;
    }

    @Override int tab() {
        return TabBar.SEARCH;
    }

    /** Down from the tab bar: the field, the keyboard up. */
    @Override void fromTabs() {
        to(KEYS);
    }

    // ---- searching ------------------------------------------------------------------------------------------------

    private void search() {
        String q = keys.text().trim();
        int ask = ++asked;
        if (q.isEmpty()) {
            rows.clear();
            chips.clear();
            searching = false;
            keys.suggest(null);
            page.ground.set("");
            page.invalidate();
            return;
        }
        searching = true;
        page.invalidate();
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        boolean favFirst = OtherPrefs.favouritesFirst(tv);
        long now = System.currentTimeMillis();
        // Every channel, film and series and tonight's guide looked through off the main thread; the answer shown once
        // it comes, if the words are still the same.
        Exec.work(() -> {
            List<Channel> ch = lib.search(q, MAX);
            if (favFirst) {
                List<Channel> fav = new ArrayList<>(), rest = new ArrayList<>();
                for (Channel c : ch) (lib.isFavourite(c) ? fav : rest).add(c);
                fav.addAll(rest);
                ch = fav;
            }
            List<Object> found = new ArrayList<>();
            for (Channel c : ch) {
                Guide.Schedule s = lib.schedule(c.key);
                found.add(new Airing(c, s, s == null ? -1 : s.at(now)));
            }
            found.addAll(tonight(lib, q, now, MAX));
            List<Catalog.Title> films = cat.search(q, false, MAX), shows = cat.search(q, true, MAX);
            Runnable show = () -> {
                if (ask == asked) show(q, found, films, shows);
            };
            if (Exec.inline) show.run();
            else Exec.main(show);
        });
    }

    /**
     * What is on tonight called that (any thread): every programme from now until five in the morning whose title has
     * the words in it, on now first (by how soon it ends), then the later ones by when they start; at most max.
     */
    static List<Airing> tonight(Library lib, String words, long now, int max) {
        String q = words.toLowerCase(Locale.ROOT);
        ZonedDateTime n = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault());
        ZonedDateTime five = n.with(LocalTime.of(5, 0));
        if (!five.isAfter(n.plusHours(1))) five = five.plusDays(1);
        long end = five.toInstant().toEpochMilli();
        List<Airing> live = new ArrayList<>(), later = new ArrayList<>();
        for (Channel c : lib.all()) {
            if (ChannelEdits.hidden(c.key)) continue;
            Guide.Schedule s = lib.schedule(c.key);
            if (s == null) continue;
            for (int i = Math.max(0, s.from(now)); i < s.start.length && s.start[i] < end; i++) {
                if (s.stop[i] <= now || s.title[i] == null || !s.title[i].toLowerCase(Locale.ROOT).contains(q)) continue;
                (s.start[i] <= now ? live : later).add(new Airing(c, s, i));
                if (live.size() + later.size() >= max * 4) break;
            }
            if (live.size() + later.size() >= max * 4) break;
        }
        live.sort((a, b) -> Long.compare(a.schedule.stop[a.at], b.schedule.stop[b.at]));
        later.sort((a, b) -> Long.compare(a.schedule.start[a.at], b.schedule.start[b.at]));
        live.addAll(later);
        return live.size() > max ? new ArrayList<>(live.subList(0, max)) : live;
    }

    private void show(String q, List<Object> found, List<Catalog.Title> films, List<Catalog.Title> shows) {
        searching = false;
        rows.clear();
        if (!films.isEmpty()) rows.add(new Row(MOVIES, new ArrayList<>(films)));
        // A channel found by its name and a programme on it now: once.
        List<Object> airings = new ArrayList<>();
        for (Object o : found) {
            Airing a = (Airing) o;
            boolean twice = false;
            for (Object p : airings) {
                Airing b = (Airing) p;
                if (b.channel.key.equals(a.channel.key) && b.at == a.at) twice = true;
            }
            if (!twice) airings.add(a);
        }
        if (!airings.isEmpty()) rows.add(new Row(CHANNELS, airings));
        if (!shows.isEmpty()) rows.add(new Row(SHOWS, new ArrayList<>(shows)));
        row = 0;
        scroll.jump(0f);
        chips();
        choose(0);
        // The keyboard's suggestions: names found that have the words in them.
        List<String> words = new ArrayList<>();
        words.add(q);
        String low = q.toLowerCase(Locale.ROOT);
        for (Catalog.Title t : films) if (words.size() < 5 && t.name.toLowerCase(Locale.ROOT).contains(low)) words.add(t.name);
        for (Catalog.Title t : shows) if (words.size() < 5 && t.name.toLowerCase(Locale.ROOT).contains(low)) words.add(t.name);
        for (Object o : airings) {
            Airing a = (Airing) o;
            String name = a.at >= 0 && a.schedule.title[a.at].toLowerCase(Locale.ROOT).contains(low) ? a.schedule.title[a.at] : ChannelEdits.name(a.channel);
            if (words.size() < 5) words.add(name);
        }
        keys.suggest(words);
        if ((area == RESULTS || area == CHIPS) && rows.isEmpty()) area = FIELD;
        page.picture();
        page.invalidate();
    }

    /** The playlists the results come from, in the playlists' order (Your TorBox for the family's own), All first. */
    private void chips() {
        chips.clear();
        List<String> ids = new ArrayList<>();
        for (Row r : rows) for (Object o : r.all) {
            String id = playlistOf(o);
            if (!ids.contains(id)) ids.add(id);
        }
        chips.add(new String[]{"", "All playlists"});
        for (Sources.Entry e : Sources.inUse(tv)) if (ids.remove(e.id)) chips.add(new String[]{e.id, e.name});
        for (String id : ids) chips.add(new String[]{id, nameOf(id)});
        if (chips.size() == 2) chips.remove(1);
        chosen = 0;
        chipAt = 0;
    }

    /** A playlist's name for its chip: Your TorBox for the family's own titles, else the playlist's own name. */
    private String nameOf(String id) {
        for (Row r : rows) for (Object o : r.all) {
            if (o instanceof Catalog.Title && ((Catalog.Title) o).torbox() && id.equals(((Catalog.Title) o).playlist())) return Catalog.TORBOX;
        }
        Sources.Entry e = Sources.get(tv, id);
        return e == null ? id : e.name;
    }

    /** The playlist a result comes from: a title's own, a channel's by its key's start ("p1:…"; the first has none). */
    private String playlistOf(Object o) {
        if (o instanceof Catalog.Title) return ((Catalog.Title) o).playlist();
        String key = ((Airing) o).channel.key;
        for (Sources.Entry e : Sources.inUse(tv)) if (!e.prefix().isEmpty() && key.startsWith(e.prefix())) return e.id;
        return "p0";
    }

    /** A playlist chosen (0: all): every row shows what it has from it, their words laid out now. */
    private void choose(int i) {
        chosen = Math.max(0, Math.min(i, chips.size() - 1));
        String id = chips.isEmpty() || chosen == 0 ? null : chips.get(chosen)[0];
        long now = System.currentTimeMillis();
        for (Row r : rows) {
            r.shown.clear();
            r.names.clear();
            r.lines.clear();
            r.marks.clear();
            r.live.clear();
            r.bell.clear();
            for (Object o : r.all) {
                if (id != null && !id.equals(playlistOf(o))) continue;
                r.shown.add(o);
                if (o instanceof Catalog.Title) {
                    Catalog.Title t = (Catalog.Title) o;
                    r.names.add(t.name);
                    r.lines.add(t.year);
                } else {
                    Airing a = (Airing) o;
                    int n = Library.get(tv).number(a.channel);
                    // Cut to the tile once here (never while drawing).
                    r.names.add(Ui.fit(a.at >= 0 ? a.schedule.title[a.at] : ChannelEdits.name(a.channel), page.titleP, Ui.f(TILE_W - 32f)));
                    r.lines.add(Ui.fit((n > 0 ? n + " " : "") + ChannelEdits.name(a.channel), page.chanP, Ui.f(TILE_W - 100f)));
                    boolean on = a.at >= 0 && a.schedule.start[a.at] <= now;
                    r.live.add(on);
                    r.marks.add(a.at < 0 ? "" : on ? "LIVE · " + LiveText.mins(a.schedule.stop[a.at] - now) + " left" : LiveText.hm(a.schedule.start[a.at]));
                    r.bell.add(a.at >= 0 && !on && Reminders.has(tv, a.channel.key, a.schedule.start[a.at]));
                }
            }
            r.focus = Math.min(r.focus, Math.max(0, r.shown.size() - 1));
            r.x.jump(target(r));
            int tonight = 0;
            if (r.kind == CHANNELS) for (Object o : r.shown) if (((Airing) o).at >= 0 && ((Airing) o).schedule.stop[((Airing) o).at] > now) tonight++;
            r.count = r.kind == CHANNELS ? tonight + " tonight" : String.valueOf(r.shown.size());
        }
    }

    /** A search remembered (when one of its results is opened). */
    private void remember() {
        String q = keys.text().trim();
        if (q.isEmpty() || !OtherPrefs.showHistory(tv)) return;
        OtherPrefs.remember(tv, q);
        history.clear();
        history.addAll(OtherPrefs.history(tv));
    }

    // ---- voice ----------------------------------------------------------------------------------------------------

    /** The microphone: Android's own voice input, its words typed into the field when it answers. */
    private void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search");
        try {
            tv.startActivityForResult(i, VOICE);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(tv, "This TV has no voice input for apps: type with the keyboard", Toast.LENGTH_LONG).show();
        }
    }

    /** Android's voice input answered (TvActivity): its words searched for. True when the answer was voice's. */
    static boolean heard(TvActivity tv, int request, int result, Intent data) {
        if (request != VOICE) return false;
        if (result != android.app.Activity.RESULT_OK || data == null) return true;
        List<String> said = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (said == null || said.isEmpty() || !(tv.top() instanceof SearchScreen)) return true;
        SearchScreen s = (SearchScreen) tv.top();
        s.type(said.get(0));
        s.area = s.rows.isEmpty() ? FIELD : RESULTS;
        s.page.invalidate();
        return true;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    private boolean showingHistory() {
        return keys.text().trim().isEmpty() && !history.isEmpty();
    }

    @Override boolean key(int code, KeyEvent e) {
        if (area == KEYS) {
            boolean used = keys.key(code, e);
            page.invalidate();
            return used;
        }
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        switch (area) {
            case MIC:
                if (right) to(FIELD);
                else if (down) below();
                else if (up) toBar();
                return true;
            case FIELD:
                if (left) to(MIC);
                else if (right) to(GEAR);
                else if (down) below();
                else if (up) toBar();
                return true;
            case GEAR:
                if (left) to(FIELD);
                else if (down) below();
                else if (up) toBar();
                return true;
            case TRASH:
                if (up) to(GEAR);
                else if (down || left) to(HISTORY);
                return true;
            case HISTORY: {
                int col = historyFocus % 2, line = historyFocus / 2;
                if (up) {
                    if (line == 0) to(col == 1 ? TRASH : FIELD);
                    else historyFocus -= 2;
                } else if (down) {
                    if (historyFocus + 2 < history.size()) historyFocus += 2;
                } else if (left) {
                    if (col == 1) historyFocus--;
                } else if (right) {
                    if (col == 0 && historyFocus + 1 < history.size()) historyFocus++;
                }
                page.invalidate();
                return true;
            }
            case CHIPS:
                if (left && chipAt > 0) choose(--chipAt);
                else if (right && chipAt < chips.size() - 1) choose(++chipAt);
                else if (up) to(FIELD);
                else if (down && !rows.isEmpty()) {
                    row = 0;
                    to(RESULTS);
                }
                page.invalidate();
                return true;
            default:
                return results(code);
        }
    }

    private void toBar() {
        if (tv.tabs() != null) tv.tabs().enter();
    }

    /** Down from the top: the chips (more than one playlist), the results, or the searches made before. */
    private void below() {
        if (!rows.isEmpty()) {
            row = 0;
            chipAt = chosen;
            to(chips.size() > 1 ? CHIPS : RESULTS);
        } else if (showingHistory()) {
            historyFocus = 0;
            to(HISTORY);
        }
    }

    private boolean results(int code) {
        if (rows.isEmpty()) {
            to(FIELD);
            return true;
        }
        Row r = rows.get(row);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (r.focus > 0) r.focus--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (r.focus < r.shown.size() - 1) r.focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) {
                    row--;
                } else {
                    to(chips.size() > 1 ? CHIPS : FIELD);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < rows.size() - 1) row++;
                break;
            default:
                return false;
        }
        rows.get(row).x.to(target(rows.get(row)));
        settleScroll();
        page.picture();
        page.invalidate();
        return true;
    }

    private void to(int a) {
        area = a;
        if (a == KEYS) keys.reset();
        settleScroll();
        page.picture();
        page.invalidate();
    }

    @Override void ok() {
        switch (area) {
            case KEYS:
                keys.ok();
                page.invalidate();
                break;
            case MIC:
                listen();
                break;
            case FIELD:
                to(KEYS);
                break;
            case GEAR:
                tv.push(new SettingsScreen(tv, "other.search"));
                break;
            case TRASH:
                OtherPrefs.forget(tv);
                history.clear();
                to(FIELD);
                break;
            case HISTORY:
                if (historyFocus < history.size()) {
                    type(history.get(historyFocus));
                    area = rows.isEmpty() ? FIELD : RESULTS;
                    row = 0;
                    page.invalidate();
                }
                break;
            case CHIPS:
                if (!rows.isEmpty()) {
                    row = 0;
                    to(RESULTS);
                }
                break;
            default: {
                if (rows.isEmpty()) return;
                Row r = rows.get(row);
                if (r.focus >= r.shown.size()) return;
                remember();
                Object o = r.shown.get(r.focus);
                if (o instanceof Catalog.Title) {
                    tv.openTitle((Catalog.Title) o);
                    return;
                }
                Airing a = (Airing) o;
                long now = System.currentTimeMillis();
                if (a.at < 0 || a.schedule.start[a.at] <= now) {
                    tv.watch(a.channel);
                } else {
                    // Later tonight: its reminder, set or taken off.
                    long start = a.schedule.start[a.at], stop = a.schedule.stop[a.at];
                    String title = a.schedule.title[a.at];
                    if (Reminders.has(tv, a.channel.key, start)) GuideActions.unremind(tv, a.channel, title, start);
                    else GuideActions.remind(tv, a.channel, title, start, stop);
                    choose(chosen);
                    page.invalidate();
                }
                break;
            }
        }
    }

    @Override boolean back() {
        if (area == KEYS) {
            // The keyboard closed first, the remote left on the field (the results under it; Down goes into them).
            to(FIELD);
            return true;
        }
        if (area == RESULTS || area == CHIPS || area == HISTORY || area == TRASH) {
            to(FIELD);
            return true;
        }
        return false;
    }

    @Override public void typed(String text) {
        h.removeCallbacks(run);
        h.postDelayed(run, TYPE_MS);
        page.invalidate();
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.UP) to(FIELD);
        else if (dir == Ui.DOWN) below();
        return true;
    }

    /** The results scrolled so the remote's row is in sight under the chips. */
    private void settleScroll() {
        if (area != RESULTS || rows.isEmpty()) {
            scroll.to(0f);
            if (page != null) page.invalidate();
            return;
        }
        float y = 0;
        for (int i = 0; i < row; i++) y += rowHeight(rows.get(i));
        float bottom = y + rowHeight(rows.get(row)), to = scroll.target();
        if (bottom - to > 1060f - RESULTS_Y) to = bottom - (1060f - RESULTS_Y);
        if (y < to) to = y;
        scroll.to(Math.max(0f, to));
        if (page != null) page.invalidate();
    }

    /** Places (design px): the results' first heading, a row's heading, posters, tiles, their steps. */
    static final float RESULTS_Y = 330f, HEAD = 54f, POSTER_W = 196f, POSTER_H = 294f, POSTER_STEP = 220f, TILE_W = 300f,
            TILE_H = 169f, TILE_STEP = 324f;

    private static float rowHeight(Row r) {
        return r.kind == CHANNELS ? HEAD + TILE_H + 56f : HEAD + POSTER_H + 98f;
    }

    /** Where a row slides so the remote's card is in sight (the first few from the start). */
    private static float target(Row r) {
        float step = r.kind == CHANNELS ? TILE_STEP : POSTER_STEP;
        return -Math.max(0, r.focus - 3) * step;
    }

    // ---- what is drawn --------------------------------------------------------------------------------------------

    private final class Page extends View {
        final Ground ground = new Ground(this);
        private final Card07 posters = new Card07(Look07.R_POSTER), tiles07 = new Card07(24f);
        private final PicTile pictures = new PicTile(this);
        private final Card07.Marks marks = new Card07.Marks();
        private final Frost glass = new Frost(Look07.GLASS);
        private final Lift07 lift = new Lift07(), topLift = new Lift07();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                caret = Ui.fill(Look07.AMBER), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint fieldP = Type.paint(34f, Type.SEMIBOLD, Look07.L1), hintP = Type.paint(20f, Type.MEDIUM, Look07.L3),
                headP = Type.paint(Type.H3, Type.BOLD, Look07.L1), countP = Type.paint(21f, Type.MEDIUM, Look07.L3),
                chanP = Type.paint(16f, Type.BOLD, Look07.L2), titleP = Type.paint(20f, Type.SEMIBOLD, Look07.L1),
                emptyP = Type.paint(Type.SUB, Type.MEDIUM, Look07.L2);
        private final RectF r = new RectF(), box = new RectF();
        private final Runnable redraw = this::invalidate;
        private final Object micKey = new Object(), gearKey = new Object(), trashKey = new Object();

        Page(Context c) {
            super(c);
            pictures.colour(0xFF141A2E);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            shade.setShader(new LinearGradient(0, 1, 0, 0, new int[]{0xE0000000, 0x1A000000, 0x00000000},
                    new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            Shadows.prepare(Look07.SH_BUTTON, 42f);
            Pills.prepare();
        }

        /** The page's picture: the result the remote is on, else the first. */
        void picture() {
            Object o = null;
            if (!rows.isEmpty()) {
                Row r = rows.get(Math.min(row, rows.size() - 1));
                if (!r.shown.isEmpty()) o = r.shown.get(Math.min(r.focus, r.shown.size() - 1));
            }
            String url = "";
            if (o instanceof Catalog.Title) {
                Catalog.Title t = (Catalog.Title) o;
                url = t.backdrop.isEmpty() ? t.poster : t.backdrop;
            } else if (o instanceof Airing) {
                Airing a = (Airing) o;
                String p = a.at >= 0 ? ProgrammePictures.peek(a.channel, a.schedule, a.at) : null;
                url = p != null && !p.isEmpty() ? p : LogoPack.url(a.channel);
            }
            ground.set(url);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = ground.draw(c, getWidth(), getHeight());
            Object top = area == MIC ? micKey : area == GEAR ? gearKey : area == TRASH ? trashKey : null;
            moving |= topLift.step(top);
            Row on = rows.isEmpty() || area != RESULTS ? null : rows.get(Math.min(row, rows.size() - 1));
            moving |= lift.step(on == null || on.shown.isEmpty() ? null : on.shown.get(Math.min(on.focus, on.shown.size() - 1)));
            drawTop(c);
            boolean typed = !keys.text().trim().isEmpty();
            if (typed) {
                if (!chips.isEmpty() && chips.size() > 1) drawChips(c);
                // The results dimmed while the remote is on the keyboard or the field, as 07's are.
                boolean dim = area != RESULTS && area != CHIPS && !rows.isEmpty();
                if (dim) c.saveLayerAlpha(0, Ui.f(300f), Ui.f(1110f), getHeight(), 0x8C);
                moving |= drawResults(c);
                if (dim) c.restore();
            } else if (!history.isEmpty()) {
                drawHistory(c);
            }
            if (area == KEYS) {
                keys.draw(c, true, 255);
                drawHints(c);
            }
            if (moving) postInvalidateOnAnimation();
        }

        private void drawTop(Canvas c) {
            float cy = Ui.f(180f);
            // The microphone and search's settings: round glass, white where the remote is.
            round(c, Ui.f(132f), cy, Icons07.MIC, area == MIC, micKey);
            round(c, Ui.f(1788f), cy, Icons07.COG, area == GEAR, gearKey);
            // The field: long glass, the words typed with Homefield's amber caret, what it finds at the right.
            r.set(Ui.f(194f), Ui.f(138f), Ui.f(1634f), Ui.f(222f));
            glass.draw(c, r, r.height() / 2f, 1f);
            if (area == FIELD) {
                fill.setColor(0x33FFFFFF);
                c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
            }
            String words = keys.text();
            float x = r.left + Ui.f(30f);
            String shown = Ui.fit(words, fieldP, Ui.f(980f));
            c.drawText(shown, x, Type.centred(fieldP, cy), fieldP);
            if (area == KEYS || area == FIELD) {
                float cx = x + fieldP.measureText(shown) + Ui.f(4f);
                c.drawRect(cx, cy - Ui.f(20f), cx + Ui.f(3f), cy + Ui.f(20f), caret);
            }
            String hint = "Films, shows, channels and what's on";
            if (words.isEmpty() && area != KEYS && area != FIELD) hint = "Speak or type to search";
            c.drawText(hint, r.right - Ui.f(30f) - hintP.measureText(hint), Type.centred(hintP, cy), hintP);
        }

        private void round(Canvas c, float cx, float cy, int icon, boolean here, Object key) {
            float up = here ? Math.max(0f, Math.min(1.1f, topLift.of(key))) : 0f, d = Ui.f(84f) * (1f + 0.06f * up);
            r.set(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f);
            if (here) {
                Shadows.draw(c, r, d / 2f, Look07.SH_BUTTON, Math.min(1f, up));
                fill.setColor(Look07.L1);
                c.drawOval(r, fill);
            } else {
                glass.draw(c, r, d / 2f, 1f);
            }
            Icons07.draw(c, icon, cx, cy, Ui.f(icon == Icons07.MIC ? 34f : 32f), here ? Look07.INK : Look07.L1);
        }

        private void drawChips(Canvas c) {
            float x = Ui.f(90f), cy = Ui.f(270f);
            for (int i = 0; i < chips.size(); i++) {
                String name = chips.get(i)[1];
                boolean on = i == chosen, here = area == CHIPS && i == chipAt;
                float w;
                if (here) {
                    float pw = Pills.pillWidth(name, true) - Ui.f(Pills.PILL_PAD * 2) * (1f - 48f / Pills.PILL_H);
                    c.save();
                    c.scale(1.06f, 1.06f, x + pw / 2f, cy);
                    r.set(x, cy - Ui.f(24f), x + pw, cy + Ui.f(24f));
                    Shadows.draw(c, r, Ui.f(24f), Look07.SH_PILL, 1f);
                    w = Pills.pill(c, x, cy, name, null, true, true, 48f);
                    c.restore();
                } else {
                    w = Pills.pill(c, x, cy, name, null, on, true, 48f);
                }
                x += w + Ui.f(10f);
            }
        }

        /** The rows of results (design px from RESULTS_Y, scrolled); true while one still slides. */
        private boolean drawResults(Canvas c) {
            if (rows.isEmpty()) {
                if (searching) {
                    // Homefield's amber spinner while the words are looked for.
                    arc.setColor(Look07.AMBER);
                    arc.setStrokeWidth(Ui.f(6));
                    float a = (SystemClock.uptimeMillis() % 1000) * 0.36f;
                    r.set(Ui.f(520), Ui.f(560), Ui.f(580), Ui.f(620));
                    c.drawArc(r, a, 100, false, arc);
                    return !Exec.inline;
                }
                String s = "Nothing in your playlists or tonight's guide is called that";
                c.drawText(s, Ui.f(90f), Ui.f(380f), emptyP);
                return false;
            }
            boolean moving = scroll.step();
            c.save();
            c.clipRect(0, Ui.f(300f), getWidth(), getHeight());
            float y = RESULTS_Y - scroll.value();
            for (int i = 0; i < rows.size(); i++) {
                Row rw = rows.get(i);
                float hgt = rowHeight(rw);
                if (y > 1080f) break;
                // A row scrolled up under the field fades as it goes.
                float a = y >= 300f ? 1f : Math.max(0f, 1f - (300f - y) / 150f);
                if (y + hgt > 300f && a > 0.01f) {
                    if (a < 1f) c.saveLayerAlpha(0, Ui.f(300f), getWidth(), Ui.f(y + hgt), Math.round(255 * a));
                    moving |= drawRow(c, rw, y, area == RESULTS && i == row);
                    if (a < 1f) c.restore();
                }
                y += hgt;
            }
            c.restore();
            return moving;
        }

        private boolean drawRow(Canvas c, Row rw, float y, boolean here) {
            boolean moving = rw.x.step();
            float base = Hero07.baseline(Ui.f(y), Hero07.normal(Ui.f(30f)), Ui.f(30f));
            String name = ROW_NAMES[rw.kind];
            c.drawText(name, Ui.f(90f), base, headP);
            c.drawText(rw.count, Ui.f(90f) + headP.measureText(name) + Ui.f(16f), base, countP);
            boolean channels = rw.kind == CHANNELS;
            float w = Ui.f(channels ? TILE_W : POSTER_W), h = Ui.f(channels ? TILE_H : POSTER_H), step = Ui.f(channels ? TILE_STEP : POSTER_STEP);
            float x0 = Ui.f(90f) + Ui.f(rw.x.value()), top = Ui.f(y + HEAD);
            int lifted = -1;
            for (int i = 0; i < rw.shown.size(); i++) {
                float x = x0 + i * step;
                if (x > getWidth()) break;
                if (x + w < 0) continue;
                if (here && i == rw.focus) {
                    lifted = i;
                    continue;
                }
                card(c, rw, i, x, top, w, h, here ? lift.of(rw.shown.get(i)) : 0f);
            }
            if (lifted >= 0) card(c, rw, lifted, x0 + lifted * step, top, w, h, lift.of(rw.shown.get(lifted)));
            return moving;
        }

        private void card(Canvas c, Row rw, int i, float x, float y, float w, float h, float up) {
            Object o = rw.shown.get(i);
            if (o instanceof Catalog.Title) {
                Catalog.Title t = (Catalog.Title) o;
                Bitmap b = t.poster.isEmpty() ? null : Pics.get(t.poster, posters.picW(w), posters.picH(h), Pics.CROP, redraw);
                marks.clear();
                marks.torbox = t.torbox();
                marks.quality = PosterArt.chip(t.bestPicture());
                posters.begin(c, x, y, w, h, up, b);
                if (b == null) {
                    fill.setColor(PosterArt.blank(t.name));
                    c.drawRoundRect(0, 0, w, h, Ui.f(Look07.R_POSTER), Ui.f(Look07.R_POSTER), fill);
                }
                posters.marks(c, w, h, marks);
                posters.end(c);
                posters.caption(c, x, y + h, w, up, rw.names.get(i), rw.lines.get(i));
                return;
            }
            Airing a = (Airing) o;
            tiles07.begin(c, x, y, w, h, up, null);
            box.set(0, 0, w, h);
            // Its programme's picture, its faces whole (else its channel's logo on its colour).
            pictures.draw(c, box, Ui.f(24f), a.channel, a.schedule, a.at, PicTile.COVER, up > 0.5f, false, !rw.x.moving());
            c.save();
            c.scale(w, h);
            c.drawRect(0, 0, 1f, 1f, shade);
            c.restore();
            // LIVE with how long is left, or its time (a bell where its reminder is set).
            String mark = rw.marks.get(i);
            if (!mark.isEmpty()) {
                if (rw.live.get(i)) Pills.liveOn(c, Ui.f(12f), Ui.f(12f) + Ui.f(15f), mark);
                else Pills.time(c, Ui.f(12f), Ui.f(12f) + Ui.f(15f), mark, rw.bell.get(i));
            }
            // Its channel (logo, number, name) and its title at its foot.
            box.set(Ui.f(16f), h - Ui.f(76f), Ui.f(16f + 56f), h - Ui.f(44f));
            Logos.draw(c, a.channel, box, redraw);
            float cy = box.centerY();
            c.drawText(rw.lines.get(i), box.right + Ui.f(10f), Type.centred(chanP, cy), chanP);
            c.drawText(rw.names.get(i), Ui.f(16f), h - Ui.f(16f), titleP);
            tiles07.end(c);
        }

        /** The searches made before: glass pills in two columns, the bin to clear them. */
        private void drawHistory(Canvas c) {
            float base = Hero07.baseline(Ui.f(330f), Hero07.normal(Ui.f(30f)), Ui.f(30f));
            c.drawText("Recent searches", Ui.f(90f), base, headP);
            round(c, Ui.f(90f) + headP.measureText("Recent searches") + Ui.f(60f), Ui.f(352f), Icons07.BIN, area == TRASH, trashKey);
            for (int i = 0; i < history.size(); i++) {
                float x = i % 2 == 0 ? 90f : 600f, cy = 430f + (i / 2) * 68f;
                if (cy > 1040f) break;
                boolean here = area == HISTORY && historyFocus == i;
                Pills.pill(c, Ui.f(x), Ui.f(cy), history.get(i), null, here, true, 52f);
            }
        }

        /** The keys' hints at the foot: ▼ Results, OK, BACK. */
        private void drawHints(Canvas c) {
            float x = Ui.f(90f), cy = Ui.f(1021f);
            x += Pills.key(c, x, cy, "▼") + Ui.f(8f);
            x += Pills.hint(c, x, cy, "Results") + Ui.f(24f);
            x += Pills.key(c, x, cy, "OK") + Ui.f(8f);
            x += Pills.hint(c, x, cy, "Play a channel or open a film") + Ui.f(24f);
            x += Pills.key(c, x, cy, "BACK") + Ui.f(8f);
            Pills.hint(c, x, cy, "Close the keyboard");
        }
    }
}
