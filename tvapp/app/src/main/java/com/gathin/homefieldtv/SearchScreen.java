package com.gathin.homefieldtv;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.OtherPrefs;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.engine.LogoPack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search, as the family's other app has it: a round microphone button at the top left (voice search, where the TV has
 * it), the search field (light grey, 1200 x 112), search's settings round button at the top right, and under them
 * either the searches made before as chips in two columns with a bin to clear them, or what the words find, live as
 * they are typed: Movies, Channels and TV shows, each a row under its name with the playlists it found them in as chips
 * (the one chosen lit; one shows a playlist's alone), movies and shows as posters, channels as tiles with their logo,
 * name and what's on. The keyboard (Keyboard) floats at the bottom right while typing; its strip suggests names found.
 * The results are dimmed while the remote is elsewhere (the keyboard, the field), and once it is in them the rows it is
 * not on are; the field turns dark then. Down from the field goes onto the first row's playlists, then its results.
 * OK on a channel watches it, on a title opens its page.
 *
 * <p>The search waits for the typing to rest (TYPE_MS, 180 ms), then goes through the channels and titles off the main
 * thread (Exec.work: Library.search, Catalog.search); an answer to older words is let go. Back closes the keyboard (the
 * remote left on the field), then Search.
 */
final class SearchScreen extends Screen implements Keyboard.Host {
    static final long TYPE_MS = 180;
    /** Android's voice input's answer (TvActivity hands it to heard()). */
    static final int VOICE = 0x5EA2;
    private static final int MOVIES = 0, CHANNELS = 1, SHOWS = 2;
    private static final String[] ROW_NAMES = {"Movies", "Channels", "TV shows"};
    private static final int MIC = 0, FIELD = 1, GEAR = 2, KEYS = 3, TRASH = 4, HISTORY = 5, RESULTS = 6;
    private static final int MAX = 40;

    /** One row of results: its kind, the playlists they were found in, the one chosen, the results shown. */
    private static final class Row {
        final int kind;
        final List<Object> all;
        final List<String[]> playlists = new ArrayList<>();
        final List<Object> shown = new ArrayList<>();
        final List<PosterArt.Item> items = new ArrayList<>();
        int chosen, focus;
        float x;
        /** The remote on the row's playlists (true) or on its results. */
        boolean onChips;

        Row(int kind, List<Object> all) {
            this.kind = kind;
            this.all = all;
        }
    }

    private Keyboard keys;
    private Page page;
    private int area = KEYS;
    private final List<String> history = new ArrayList<>();
    private int historyFocus;
    private final List<Row> rows = new ArrayList<>();
    private int row;
    /** The results' scroll (design pixels), gliding on the display's frame clock (never a jump from row to row). */
    private final Glide scroll = new Glide(RowList.SCROLL_MS * 4 / 3);
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
        // No ground of its own: the window's (TvActivity) is the screen's, so it is filled once, not twice.
        page = new Page(tv);
        keys = new Keyboard(tv, Keyboard.SEARCH);
        keys.x = 1148f;
        keys.y = 660f;
        keys.setHost(this);
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        f.addView(keys, new FrameLayout.LayoutParams(-1, -1));
        history.clear();
        if (OtherPrefs.showHistory(tv)) history.addAll(OtherPrefs.history(tv));
        place();
        return f;
    }

    /** Previews and tests: the words typed already. */
    void type(String text) {
        keys.setText(text);
        search();
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

    @Override void catalogChanged() {
        channelsChanged();
    }

    // ---- searching ------------------------------------------------------------------------------------------------

    private void search() {
        String q = keys.text().trim();
        int ask = ++asked;
        if (q.isEmpty()) {
            rows.clear();
            searching = false;
            keys.suggest(null);
            page.invalidate();
            return;
        }
        searching = true;
        page.invalidate();
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        boolean favFirst = OtherPrefs.favouritesFirst(tv);
        // Every channel, film and series looked through off the main thread; the answer shown once it comes, if the
        // words are still the same.
        Exec.work(() -> {
            List<Channel> ch = lib.search(q, MAX);
            if (favFirst) {
                List<Channel> fav = new ArrayList<>(), rest = new ArrayList<>();
                for (Channel c : ch) (lib.isFavourite(c) ? fav : rest).add(c);
                fav.addAll(rest);
                ch = fav;
            }
            List<Catalog.Title> films = cat.search(q, false, MAX), shows = cat.search(q, true, MAX);
            List<Channel> channels = ch;
            Runnable show = () -> {
                if (ask == asked) show(q, channels, films, shows);
            };
            if (Exec.inline) show.run();
            else Exec.main(show);
        });
    }

    private void show(String q, List<Channel> ch, List<Catalog.Title> films, List<Catalog.Title> shows) {
        searching = false;
        rows.clear();
        if (!films.isEmpty()) rows.add(row(MOVIES, new ArrayList<>(films)));
        if (!ch.isEmpty()) rows.add(row(CHANNELS, new ArrayList<>(ch)));
        if (!shows.isEmpty()) rows.add(row(SHOWS, new ArrayList<>(shows)));
        row = 0;
        scroll.jump(0f);
        // The strip's suggestions: names found that start with the words.
        List<String> words = new ArrayList<>();
        String low = q.toLowerCase(Locale.ROOT);
        for (Channel c : ch) if (words.size() < 3 && ChannelEdits.name(c).toLowerCase(Locale.ROOT).startsWith(low)) words.add(ChannelEdits.name(c));
        for (Catalog.Title t : films) if (words.size() < 5 && t.name.toLowerCase(Locale.ROOT).startsWith(low)) words.add(t.name);
        for (Catalog.Title t : shows) if (words.size() < 6 && t.name.toLowerCase(Locale.ROOT).startsWith(low)) words.add(t.name);
        keys.suggest(words);
        if (area == RESULTS && rows.isEmpty()) area = FIELD;
        place();
    }

    /** A row: the playlists its results come from (in the playlists' order), the first chosen. */
    private Row row(int kind, List<Object> all) {
        Row r = new Row(kind, all);
        List<String> ids = new ArrayList<>();
        for (Object o : all) {
            String id = playlistOf(o);
            if (!ids.contains(id)) ids.add(id);
        }
        for (Sources.Entry e : Sources.inUse(tv)) if (ids.remove(e.id)) r.playlists.add(new String[]{e.id, e.name});
        for (String id : ids) r.playlists.add(new String[]{id, nameOf(id, all)});
        choose(r, 0);
        return r;
    }

    /** A playlist's name for its chip: Your TorBox for the family's own titles, else the playlist's own name. */
    private String nameOf(String id, List<Object> all) {
        for (Object o : all) {
            if (o instanceof Catalog.Title && ((Catalog.Title) o).torbox() && id.equals(((Catalog.Title) o).playlist())) return Catalog.TORBOX;
        }
        Sources.Entry e = Sources.get(tv, id);
        return e == null ? id : e.name;
    }

    /** The playlist a result comes from: a title's own, a channel's by its key's start ("p1:…"; the first has none). */
    private String playlistOf(Object o) {
        if (o instanceof Catalog.Title) return ((Catalog.Title) o).playlist();
        String key = ((Channel) o).key;
        for (Sources.Entry e : Sources.inUse(tv)) if (!e.prefix().isEmpty() && key.startsWith(e.prefix())) return e.id;
        return "p0";
    }

    /** A row's playlist chosen: its results shown. */
    private void choose(Row r, int i) {
        r.chosen = Math.max(0, Math.min(i, r.playlists.size() - 1));
        String id = r.playlists.isEmpty() ? null : r.playlists.get(r.chosen)[0];
        r.shown.clear();
        r.items.clear();
        for (Object o : r.all) {
            if (id != null && r.playlists.size() > 1 && !id.equals(playlistOf(o))) continue;
            r.shown.add(o);
            if (o instanceof Catalog.Title) {
                PosterArt.Item it = new PosterArt.Item((Catalog.Title) o);
                it.genre = PosterArt.genreOf(((Catalog.Title) o).category);
                r.items.add(it);
            } else {
                r.items.add(null);
            }
        }
        r.focus = Math.min(r.focus, Math.max(0, r.shown.size() - 1));
        r.x = r.focus;
    }

    /** A search remembered (when one of its results is opened, or the search key pressed). */
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
        s.place();
        return true;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    private void place() {
        keys.setVisibility(area == KEYS ? View.VISIBLE : View.GONE);
        keys.setActive(area == KEYS);
        page.invalidate();
    }

    private boolean hasResults() {
        return !rows.isEmpty();
    }

    private boolean showingHistory() {
        return keys.text().trim().isEmpty() && !history.isEmpty();
    }

    @Override boolean key(int code, KeyEvent e) {
        if (area == KEYS) return keys.key(code, e);
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        switch (area) {
            case MIC:
                if (right) to(FIELD);
                else if (down) below();
                return true;
            case FIELD:
                if (left) to(MIC);
                else if (right) to(GEAR);
                else if (down) below();
                return true;
            case GEAR:
                if (left) to(FIELD);
                else if (down) below();
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
            default:
                return results(code);
        }
    }

    /** Down from the top: the history, or the results. */
    private void below() {
        if (hasResults()) {
            // Onto the first row's playlists first, as the other app goes (then Down to its results).
            row = 0;
            Row r = rows.get(0);
            r.onChips = !r.playlists.isEmpty();
            to(RESULTS);
        } else if (showingHistory()) {
            historyFocus = 0;
            to(HISTORY);
        }
    }

    private boolean results(int code) {
        Row r = rows.get(row);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (r.onChips) choose(r, Math.max(0, r.chosen - 1));
                else if (r.focus > 0) r.focus--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (r.onChips) choose(r, Math.min(r.playlists.size() - 1, r.chosen + 1));
                else if (r.focus < r.shown.size() - 1) r.focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!r.onChips && !r.playlists.isEmpty()) {
                    r.onChips = true;
                } else if (row > 0) {
                    row--;
                    rows.get(row).onChips = false;
                } else {
                    to(FIELD);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (r.onChips) {
                    r.onChips = false;
                } else if (row < rows.size() - 1) {
                    row++;
                    Row n = rows.get(row);
                    n.onChips = !n.playlists.isEmpty();
                }
                break;
            default:
                return false;
        }
        settleScroll();
        page.invalidate();
        return true;
    }

    private void to(int a) {
        area = a;
        settleScroll();
        place();
    }

    @Override void ok() {
        switch (area) {
            case KEYS:
                keys.ok();
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
                    place();
                }
                break;
            default: {
                Row r = rows.get(row);
                if (r.onChips || r.focus >= r.shown.size()) return;
                remember();
                Object o = r.shown.get(r.focus);
                if (o instanceof Channel) tv.watch((Channel) o);
                else tv.openTitle((Catalog.Title) o);
                break;
            }
        }
    }

    @Override boolean back() {
        if (area == KEYS) {
            // The keyboard closed first, the remote left on the field (the results dimmed under it; Down goes into
            // them); then Search.
            to(FIELD);
            return true;
        }
        return false;
    }

    @Override public void typed(Keyboard k, String text) {
        h.removeCallbacks(run);
        h.postDelayed(run, TYPE_MS);
        page.invalidate();
    }

    @Override public void done(Keyboard k, String text) {
        remember();
        h.removeCallbacks(run);
        search();
        to(hasResults() ? RESULTS : FIELD);
        if (area == RESULTS) {
            row = 0;
            rows.get(0).onChips = false;
        }
    }

    @Override public boolean edge(Keyboard k, int dir) {
        if (dir == Ui.UP) to(FIELD);
        return true;
    }

    /** The results scrolled so the remote's row is in sight under the field. */
    private void settleScroll() {
        if (area != RESULTS || rows.isEmpty()) {
            scroll.to(0f);
            if (page != null) page.invalidate();
            return;
        }
        float y = 0;
        for (int i = 0; i < row; i++) y += rowHeight(rows.get(i));
        float bottom = y + rowHeight(rows.get(row)) - 66f, to = scroll.target();
        if (bottom - to > 1080f - RESULTS_Y) to = bottom - (1080f - RESULTS_Y);
        if (y < to) to = y;
        scroll.to(to);
        if (page != null) page.invalidate();
    }

    static final float RESULTS_Y = 214f, ROW_HEAD = 96f, TILE_W = 236f, TILE_H = 178f;

    private float rowHeight(Row r) {
        return ROW_HEAD + (r.kind == CHANNELS ? TILE_H : page.art.tileH() / Ui.s) + 66f;
    }

    // ---- what is drawn --------------------------------------------------------------------------------------------

    private final class Page extends View {
        final PosterArt art = new PosterArt(Ui.f(PosterArt.landscape ? 434f : PosterGrid.POSTER_W));
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final TextPaint fieldP = Type.paint(Type.HINT, Type.REGULAR, Keyboard.FIELD_INK), headP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT2);
        private final TextPaint nameP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), lineP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
        private final TextPaint tvP = Type.paint(44f, Type.BOLD, 0x66FFFFFF), emptyP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        /** A past search's words, to size its chip (Chips.history writes them 30 px). */
        private final TextPaint chipP = Type.paint(30f, Type.REGULAR, Palette.TEXT2);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;

        Page(Context c) {
            super(c);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            drawTop(c);
            if (!keys.text().trim().isEmpty()) {
                // The results dimmed while the remote is elsewhere (typing, the field), as the other app's are.
                boolean dim = area != RESULTS && !rows.isEmpty();
                if (dim) c.saveLayerAlpha(0, Ui.f(190), getWidth(), getHeight(), 0x8C);
                drawResults(c);
                if (dim) c.restore();
            } else if (!history.isEmpty()) {
                drawHistory(c);
            }
        }

        private void drawTop(Canvas c) {
            // The microphone: light grey, a little larger and lighter where the remote is.
            float d = area == MIC ? 124f : 104f;
            fill.setColor(area == MIC ? 0xFFEBEBEB : 0xFFCCCCCC);
            c.drawCircle(Ui.f(164), Ui.f(108), Ui.f(d / 2f), fill);
            Glyphs.draw(c, Glyphs.MIC, Ui.f(164), Ui.f(108), Ui.f(46), 0xFF555555);
            // The field: light, its caret showing while it has the remote; dark once the remote is in the results.
            r.set(Ui.f(356), Ui.f(52), Ui.f(1556), Ui.f(164));
            boolean inResults = area == RESULTS;
            fill.setColor(inResults ? 0xFF434865 : Keyboard.FIELD);
            c.drawRoundRect(r, Ui.f(16), Ui.f(16), fill);
            boolean typing = area == KEYS || area == FIELD;
            if (inResults) {
                fieldP.setColor(0xFFD0D2DA);
                c.drawText(Ui.fit(keys.text(), fieldP, r.width() - Ui.f(96)), r.left + Ui.f(48), Type.centred(fieldP, r.centerY()), fieldP);
            } else {
                Keyboard.field(c, r, fieldP, keys.text(), keys.caret(), false, typing ? "Search" : "Speak to search", typing);
            }
            // Search's settings.
            if (area == GEAR) {
                Focus.circle(c, Ui.f(1836), Ui.f(100), Ui.f(72));
            } else {
                fill.setColor(Palette.SELECTED);
                c.drawCircle(Ui.f(1836), Ui.f(100), Ui.f(36), fill);
            }
            RoundButton.glyph(c, Dark.GEAR, Ui.f(1836), Ui.f(100), Ui.f(38), Palette.TEXT1);
        }

        private void drawHistory(Canvas c) {
            headP.setColor(0xFF979AAA);
            c.drawText("Search history", Ui.f(384), Ui.f(278), headP);
            if (area == TRASH) {
                Focus.circle(c, Ui.f(1516), Ui.f(267), Ui.f(72));
            } else {
                fill.setColor(Palette.SELECTED);
                c.drawCircle(Ui.f(1516), Ui.f(267), Ui.f(36), fill);
            }
            Glyphs.draw(c, Glyphs.TRASH, Ui.f(1516), Ui.f(267), Ui.f(34), Palette.TEXT1);
            for (int i = 0; i < history.size(); i++) {
                float x = i % 2 == 0 ? 360f : 976f, y = 339f + (i / 2) * 96f;
                if (y > 1080f) break;
                String s = history.get(i);
                float w = Math.min(560f, chipP.measureText(s) / Ui.s + 50f);
                r.set(Ui.f(x), Ui.f(y), Ui.f(x + w), Ui.f(y + Chips.FILTER_H));
                Chips.history(c, r, s, area == HISTORY && historyFocus == i);
            }
        }

        private void drawResults(Canvas c) {
            if (rows.isEmpty()) {
                if (searching) {
                    // The accent's spinner while the words are looked for.
                    arc.setColor(Palette.ACCENT);
                    arc.setStrokeWidth(Ui.f(6));
                    float a = (SystemClock.uptimeMillis() % 1000) * 0.36f;
                    r.set(Ui.f(940), Ui.f(560), Ui.f(1000), Ui.f(620));
                    c.drawArc(r, a, 100, false, arc);
                    if (!Exec.inline) postInvalidateOnAnimation();
                } else {
                    String s = "Nothing in your playlists is called that";
                    emptyP.setColor(Palette.TEXT2);
                    c.drawText(s, (getWidth() - emptyP.measureText(s)) / 2f, Ui.f(590), emptyP);
                }
                return;
            }
            boolean moving = scroll.step();
            c.save();
            c.clipRect(0, Ui.f(190), getWidth(), getHeight());
            float y = RESULTS_Y - scroll.value();
            for (int i = 0; i < rows.size(); i++) {
                Row rw = rows.get(i);
                float h = rowHeight(rw);
                if (y > 1080f) break;
                if (y + h > 190f) {
                    // With the remote in the results, the rows it is not on are dimmed, as the other app's are.
                    // Drawn as they are, then the ground at 45 % over them: the same as a layer at 55 %, with no layer.
                    boolean dim = area == RESULTS && i != row;
                    drawRow(c, rw, y, area == RESULTS && i == row);
                    if (dim) {
                        fill.setColor(Palette.alpha(Palette.GROUND, 0x73));
                        c.drawRect(0, Ui.f(Math.max(190f, y)), getWidth(), Ui.f(Math.min(1080f, y + h)), fill);
                    }
                }
                y += h;
            }
            c.restore();
            if (moving) postInvalidateOnAnimation();
        }

        private void drawRow(Canvas c, Row rw, float y, boolean here) {
            headP.setColor(here ? Palette.TEXT1 : 0xFF6C7086);
            String name = ROW_NAMES[rw.kind];
            c.drawText(name, Ui.f(48), Ui.f(y + 48), headP);
            // The playlists they were found in: chips, the one chosen lit.
            float cx = 48f + headP.measureText(name) / Ui.s + 76f;
            for (int i = 0; i < rw.playlists.size(); i++) {
                String p = rw.playlists.get(i)[1];
                float w = nameP.measureText(p) / Ui.s + 50f;
                r.set(Ui.f(cx), Ui.f(y + 5), Ui.f(cx + w), Ui.f(y + 69));
                Chips.filter(c, r, p, here && rw.onChips && rw.chosen == i, rw.chosen == i);
                cx += w + 24f;
            }
            float top = y + ROW_HEAD;
            boolean posters = rw.kind != CHANNELS;
            float pitch = posters ? art.w / Ui.s + PosterGrid.GAP : TILE_W + 16f;
            // The row moves along only once the remote passes its seventh.
            int first = Math.max(0, rw.focus - 6);
            for (int i = first; i < rw.shown.size(); i++) {
                float x = 48f + (i - first) * pitch;
                if (x > 1920f) break;
                boolean on = here && !rw.onChips && i == rw.focus;
                if (posters) art.draw(c, rw.items.get(i), Ui.f(x), Ui.f(top), on, 255, redraw);
                else tile(c, (Channel) rw.shown.get(i), x, top, on);
            }
        }

        /** A channel's tile: its logo (or "TV"), its name, what's on, how far through. */
        private void tile(Canvas c, Channel ch, float x, float y, boolean on) {
            r.set(Ui.f(x), Ui.f(y), Ui.f(x + TILE_W), Ui.f(y + TILE_H));
            fill.setColor(on ? Palette.over(Palette.GROUND, Palette.FOCUS) : Palette.PANEL);
            c.drawRoundRect(r, Ui.f(12), Ui.f(12), fill);
            Bitmap b = LogoPack.url(ch).isEmpty() ? null : Pics.get(LogoPack.url(ch), Ui.px(100), Ui.px(66), Pics.logoMode(), redraw);
            float lx = Ui.f(x + TILE_W / 2f), ly = Ui.f(y + 52);
            if (b != null) {
                c.drawBitmap(b, lx - b.getWidth() / 2f, ly - b.getHeight() / 2f, bmp);
            } else {
                r.set(lx - Ui.f(40), ly - Ui.f(38), lx + Ui.f(40), ly + Ui.f(38));
                fill.setColor(0x1FFFFFFF);
                c.drawRoundRect(r, Ui.f(6), Ui.f(6), fill);
                c.drawText("TV", lx - tvP.measureText("TV") / 2f, Type.centred(tvP, ly), tvP);
            }
            Library lib = Library.get(tv);
            Guide.Schedule s = lib.schedule(ch.key);
            long now = System.currentTimeMillis();
            int p = s == null ? -1 : s.at(now);
            nameP.setColor(Palette.TEXT1);
            c.drawText(PosterArt.fitDots(ChannelEdits.name(ch), nameP, Ui.f(TILE_W - 32)), Ui.f(x + 16), Ui.f(y + 124), nameP);
            lineP.setColor(Palette.TEXT2);
            String what = p >= 0 ? s.title[p] : "No information";
            c.drawText(PosterArt.fitDots(what, lineP, Ui.f(TILE_W - 32)), Ui.f(x + 16), Ui.f(y + 156), lineP);
            if (p >= 0) {
                float done = (now - s.start[p]) / (float) Math.max(1, s.stop[p] - s.start[p]);
                Bars.thin(c, Ui.f(x + 16), Ui.f(y + 168), Ui.f(TILE_W - 32), Ui.f(4), done);
            }
            if (on) {
                r.set(Ui.f(x), Ui.f(y), Ui.f(x + TILE_W), Ui.f(y + TILE_H));
                Focus.border(c, r, Ui.f(12));
            }
        }
    }
}
