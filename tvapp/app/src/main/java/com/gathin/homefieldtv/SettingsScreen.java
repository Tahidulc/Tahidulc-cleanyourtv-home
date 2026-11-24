package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings, as the family's other app has it, in the 07 look (the approved still 28): one frosted panel over the screen
 * it was opened from (SettingsPanel: that screen dimmed) in two panes, the sections down its left and the page chosen
 * beside them (where it is, its name and a line about it over its rows), the sections in that app's order and with its
 * names:
 *
 * <pre>
 *   General            Auto start app on boot, Start on, Back up data, Restore data ›, then Homefield's Sync ›, Remove everything
 *   Playlists          each playlist › (its own details), TorBox ›, Add playlist ›, Update all playlists, how often
 *   EPG                EPG sources ›, the guide's time shift, Update options: Update interval, hours, Update on app start,
 *                      Update EPG, Clear EPG, Latest update status
 *   Appearance         TV guide ›, Player ›, Categories ›, Logos ›, Font size, Color theme ›, then Homefield's Posters
 *                      and programme times
 *   Channels &amp; categories   One row per channel (merge qualities), Hidden channels ›, Manage categories ›, Rename
 *                      channels ›, Channels sorting (the same pages as Appearance › TV guide's)
 *   Playback           Buffer size, Decoder, Auto frame rate (AFR), Audio passthrough, Tunneled playback, then Homefield's
 *                      picture, sound, subtitles and stream settings under their section names
 *   Remote control     Player › (the keys while watching full screen)
 *   Parental controls  the switch, Change PIN, how locked channels show, Require PIN for: Settings, what is locked
 *   Other              Search ›, Reminders ›, Recording ›, VOD ›, then Homefield's Ask AI ›
 *   About              Version, Updates ›, Device name, then a few words about Homefield TV
 * </pre>
 *
 * Every line is a row of the shared list (RowList): a title with its value in grey under it, a switch at the right,
 * section names in the accent. OK on a line with a value opens its choices as a page of radio rows (the one in use
 * chosen; OK keeps one and goes back), OK on a "›" line opens its page sliding in from the right, OK on a switch flips
 * it. Back goes back a page, the remote on the line it came from, and on the sections closes the panel. A question
 * (remove everything, a playlist, a backup put back) comes as a card in the middle (DialogCard). What is typed goes on
 * the on-screen keyboard, on a sheet risen from the foot over the dimmed screen: a password and the TorBox key only
 * ever show as dots, an address only its server's name.
 *
 * <p>The pages are MoreSettings' (General, Appearance, Playback, Remote control, Other, About), ExtraSettings'
 * (Playlists, EPG, Parental controls, Restore data) and EditSettings' (Channels &amp; categories, Appearance › TV guide
 * with Hidden channels and Manage categories and each category's page, Categories, Logos); each page is named by a path
 * ("appearance", "pick:clock").
 * Settings can be opened on a page (new SettingsScreen(tv, "other.recording")), and opens again where it was after the
 * screens are made again for a new colour theme, font size or posters.
 */
final class SettingsScreen extends Screen implements SettingsPanel.Host, Keyboard.Host, DialogCard.Host {
    /** The sections, in the other app's order, by page and by name. */
    static final String[] SECTIONS = {"general", "playlists", "epg", "appearance", "channels", "playback", "remote", "parental",
            "other", "about"};
    static final String[] SECTION_NAMES = {"General", "Playlists", "EPG", "Appearance", "Channels & categories", "Playback",
            "Remote control", "Parental controls", "Other", "About"};
    static final String ROOT = "";
    /** A page of a setting's choices: "pick:" and the setting. */
    static final String PICK = "pick:";

    /** The page (and the line on it) to open again after the screens are made again; null: none. */
    private static String reopenPath;
    private static int reopenFocus = -1;

    /** One page's owner: its rows, its title, OK on its rows, its settings' choices, what is typed for it. */
    interface Pages {
        boolean owns(String page);

        String title(String page);

        List<RowList.Row> rows(String page);

        /** OK on one of the page's rows. */
        void chosen(String page, RowList.Row row);

        /** A setting's choices (its picker page), or null when the setting isn't this owner's. */
        Picker picker(String key);

        /** One of a setting's choices kept. */
        void picked(String key, int index);

        /** What a field starts with on the keyboard. */
        String value(String field);

        /** A field typed in (OK on the keyboard's search key). */
        void typed(String field, String text);
    }

    /** A setting's choices: their names, the one in use, a note under them. */
    static final class Picker {
        final String title;
        final String[] names;
        final int chosen;
        final String note;

        Picker(String title, String[] names, int chosen, String note) {
            this.title = title;
            this.names = names;
            this.chosen = chosen;
            this.note = note == null ? "" : note;
        }
    }

    private SettingsPanel panel;
    private Keyboard keys;
    private Sheet sheet;
    private DialogCard dialog;
    private Runnable onYes;
    private MoreSettings more;
    private ExtraSettings extra;
    private EditSettings edits;
    /** 3.4's pages: If something goes wrong, Automatic updates, Merged channels, Ask AI. */
    private AddedSettings added;
    /** The pages open, the sections first. */
    private final List<String> pages = new ArrayList<>();
    private boolean editing;
    private String editField = "";
    /** Opened on this page (a path) with no slide; "" for the sections. */
    private final String startAt;

    SettingsScreen(TvActivity tv) {
        this(tv, ROOT);
    }

    /** Opened on a page (its path: "other.recording"), its parents under it as Back goes. */
    SettingsScreen(TvActivity tv, String page) {
        super(tv);
        startAt = page == null ? ROOT : page;
    }

    /** Opened again after the screens were made again for a new look (TvActivity.restartLook): where it was. */
    SettingsScreen(TvActivity tv, int reopen) {
        this(tv, reopen >= 0 && reopenPath != null ? reopenPath : ROOT);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        panel = new SettingsPanel(tv);
        panel.setHost(this);
        sheet = new Sheet(tv);
        keys = new Keyboard(tv, Keyboard.FULL);
        keys.x = (1920f - keys.width()) / 2f;
        keys.y = Sheet.KEYS_Y;
        keys.showField = true;
        keys.setHost(this);
        keys.setVisibility(View.GONE);
        sheet.setVisibility(View.GONE);
        dialog = new DialogCard(tv);
        dialog.setHost(this);
        f.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        f.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        f.addView(keys, new FrameLayout.LayoutParams(-1, -1));
        f.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        more = new MoreSettings(tv, this);
        extra = new ExtraSettings(tv, this);
        edits = new EditSettings(tv, this);
        added = new AddedSettings(tv, this);
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
        pages.clear();
        pages.add(ROOT);
        boolean quiet = !startAt.isEmpty();
        panel.open("Settings", ROOT, rows(ROOT), sectionIndex(startAt), !quiet);
        if (quiet) {
            // Each page on the way to it, there at once.
            String path = startAt;
            List<String> chain = chain(path);
            for (int i = 0; i < chain.size(); i++) {
                String p = chain.get(i);
                List<RowList.Row> r = rows(p);
                int at = i == chain.size() - 1 && reopenFocus >= 0 ? reopenFocus : i + 1 < chain.size() ? indexOfPage(r, chain.get(i + 1)) : -1;
                pages.add(p);
                panel.push(title(p), p, r, at, false);
            }
            reopenFocus = -1;
            reopenPath = null;
        }
    }

    @Override boolean closing(Runnable done) {
        panel.close(done);
        return true;
    }

    @Override void shown() {
        tv.live.subs.watch(this::refreshQuietly);
        refreshQuietly();
        // Back from Android's file chooser with a backup: offered to be put back.
        if (extra != null) extra.resumed();
    }

    @Override void hidden() {
        tv.live.subs.watch(null);
    }

    @Override void channelsChanged() {
        refreshQuietly();
    }

    @Override void catalogChanged() {
        refreshQuietly();
    }

    @Override void updatesChanged() {
        refreshQuietly();
    }

    /** The logos changed (their list read, Refresh logos): Appearance › Logos says so. */
    void logosChanged() {
        refreshQuietly();
    }

    // ---- the pages ------------------------------------------------------------------------------------------------

    /**
     * Pure: the pages from the sections to a page, parents first: "appearance.guide" → appearance, appearance.guide;
     * "playlists.add.m3u" → playlists, playlists.add, playlists.add.m3u. A playlist's or a group's own page (whose name
     * may hold dots) is under Playlists, or Appearance › Groups; the colour theme's under Appearance; a setting's
     * choices under the page it is on (MoreSettings.pageOf).
     */
    static List<String> chain(String page) {
        List<String> out = new ArrayList<>();
        if (page == null || page.isEmpty()) return out;
        boolean pick = page.startsWith(PICK);
        String base = pick ? MoreSettings.pageOf(page.substring(PICK.length())) : page;
        if (base.startsWith("playlist:")) {
            out.add("playlists");
            out.add(base);
        } else if (base.startsWith("group:")) {
            // A category's page, in Channels & categories › Manage categories (EditSettings).
            out.add("channels");
            out.add("channels.categories");
            out.add(base);
        } else if (base.startsWith("rename:")) {
            // A category's channels to rename, in Channels & categories › Rename channels (its name may hold dots).
            out.add("channels");
            out.add("channels.rename");
            out.add(base);
        } else if (base.equals("theme")) {
            out.add("appearance");
            out.add(base);
        } else if (!base.isEmpty()) {
            for (int i = base.indexOf('.'); i > 0; i = base.indexOf('.', i + 1)) out.add(base.substring(0, i));
            out.add(base);
        }
        if (pick) out.add(page);
        return out;
    }

    private static int sectionIndex(String page) {
        List<String> c = chain(page);
        if (c.isEmpty()) return 0;
        for (int i = 0; i < SECTIONS.length; i++) if (SECTIONS[i].equals(c.get(0))) return i;
        return 0;
    }

    /** The row that opens a page (its tag), or -1. */
    private static int indexOfPage(List<RowList.Row> rows, String page) {
        for (int i = 0; i < rows.size(); i++) {
            Object t = rows.get(i).tag;
            if (t instanceof String && (t.equals("page:" + page) || page.startsWith(PICK) && t.equals("pick:" + page.substring(PICK.length())))) {
                return i;
            }
        }
        return -1;
    }

    private Pages owner(String page) {
        String p = page.startsWith(PICK) ? page.substring(PICK.length()) : page;
        if (page.startsWith(PICK)) {
            return added.picker(p) != null ? added : extra.picker(p) != null ? extra : edits.picker(p) != null ? edits : more;
        }
        if (SubtitleSettings.ownsPage(p)) return SubtitleSettings.of(this, tv);
        return added.owns(p) ? added : extra.owns(p) ? extra : edits.owns(p) ? edits : more;
    }

    /** Who keeps what is typed in a field. */
    private Pages fieldOwner(String field) {
        if (SubtitleSettings.ownsField(field)) return SubtitleSettings.of(this, tv);
        return added.ownsField(field) ? added : edits.ownsField(field) ? edits : extra.ownsField(field) ? extra : more;
    }

    String title(String page) {
        if (page.isEmpty()) return "Settings";
        if (page.startsWith(PICK)) {
            Picker pk = owner(page).picker(page.substring(PICK.length()));
            return pk == null ? "" : pk.title;
        }
        return owner(page).title(page);
    }

    List<RowList.Row> rows(String page) {
        if (page.isEmpty()) return sectionRows();
        if (page.startsWith(PICK)) return pickerRows(owner(page).picker(page.substring(PICK.length())));
        return owner(page).rows(page);
    }

    /** The sections as rows; a dot on About when a newer Homefield TV is waiting. */
    private List<RowList.Row> sectionRows() {
        List<RowList.Row> rows = new ArrayList<>();
        for (int i = 0; i < SECTIONS.length; i++) {
            RowList.Row r = RowList.Row.item("page:" + SECTIONS[i], SECTION_NAMES[i], "");
            r.dot = SECTIONS[i].equals("about") && Updates.waiting(tv);
            rows.add(r);
        }
        return rows;
    }

    /** Pure: a setting's choices as radio rows, the one in use chosen, the note under them. */
    static List<RowList.Row> pickerRows(Picker p) {
        List<RowList.Row> rows = new ArrayList<>();
        if (p == null) return rows;
        for (int i = 0; i < p.names.length; i++) {
            if (p.names[i] == null || p.names[i].isEmpty()) continue;
            rows.add(RowList.Row.radio("opt:" + i, p.names[i], i == p.chosen));
        }
        if (!p.note.isEmpty()) rows.add(RowList.Row.note(p.note));
        return rows;
    }

    /** Pure: where the remote goes on a picker's page: the choice in use. */
    static int checkedRow(List<RowList.Row> rows) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).checked) return i;
        return -1;
    }

    private String page() {
        return pages.isEmpty() ? ROOT : pages.get(pages.size() - 1);
    }

    // ---- what the pages ask of the screen -----------------------------------------------------------------------

    /** A sub-page opened, sliding in from the right. */
    void open(String page) {
        List<RowList.Row> r = rows(page);
        int at = page.startsWith(PICK) ? checkedRow(r) : -1;
        pages.add(page);
        panel.push(title(page), page, r, at);
    }

    /** Up a page (Back), the one under made again (a choice kept shows in its line). */
    void up() {
        if (pages.size() <= 1) return;
        pages.remove(pages.size() - 1);
        panel.pop(rows(page()));
    }

    /** The page on screen made again, the remote on the same line. */
    void refresh() {
        if (panel == null || pages.isEmpty() || editing) return;
        int at = panel.list().focusIndex();
        panel.set(rows(page()));
        if (at >= 0) panel.setFocus(Math.min(at, panel.list().rows().size() - 1));
    }

    /** The page on screen made again when nothing is being typed or asked. */
    private void refreshQuietly() {
        if (dialog != null && dialog.showing()) return;
        refresh();
    }

    /** The page on screen made again, the remote on the line with this tag (or where it was). */
    void refreshOn(Object tag) {
        refresh();
        List<RowList.Row> rows = panel.list().rows();
        for (int i = 0; i < rows.size(); i++) {
            if (tag != null && tag.equals(rows.get(i).tag)) {
                panel.setFocus(i);
                return;
            }
        }
    }

    /** A question in a card: OK on yes runs it; no (or Back) just closes the card. */
    void ask(String title, String words, String yes, String no, Runnable then) {
        onYes = then;
        List<RowList.Row> answers = new ArrayList<>();
        answers.add(RowList.Row.item("yes", yes, ""));
        answers.add(RowList.Row.item("no", no, ""));
        panel.list().setActive(false);
        dialog.show(title, words, answers, 1);
    }

    /** The screens made again in a new look, Settings opened again on this page and line. */
    void restart(String page) {
        reopenPath = page;
        reopenFocus = panel.list().focusIndex();
        tv.restartLook(0);
    }

    void toast(String words) {
        Toast.makeText(tv, words, Toast.LENGTH_LONG).show();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (dialog.showing()) return dialog.key(code);
        if (editing) return keys.key(code, e);
        return panel.key(code);
    }

    @Override void ok() {
        if (dialog.showing()) {
            dialog.ok();
            return;
        }
        if (editing) {
            keys.ok();
            return;
        }
        panel.ok();
    }

    @Override boolean back() {
        if (dialog.showing()) {
            closeDialog(null);
            return true;
        }
        if (editing) {
            closeKeys();
            return true;
        }
        if (pages.size() <= 1) return false;
        up();
        return true;
    }

    @Override public void chosen(SettingsPanel p, RowList.Row row) {
        String page = page();
        if (page.isEmpty()) {
            if (row.tag instanceof String && ((String) row.tag).startsWith("page:")) openSection(((String) row.tag).substring(5));
            return;
        }
        if (page.startsWith(PICK)) {
            String t = row.tag instanceof String ? (String) row.tag : "";
            if (!t.startsWith("opt:")) return;
            int i = parse(t.substring(4));
            String key = page.substring(PICK.length());
            Pages o = owner(page);
            up();
            o.picked(key, i);
            refresh();
            return;
        }
        owner(page).chosen(page, row);
    }

    /** A section opened: Parental controls after the PIN when one is set. */
    private void openSection(String s) {
        if (s.equals("parental") && com.gathin.homefieldtv.prefs.Parental.hasPin(tv) && !com.gathin.homefieldtv.prefs.Parental.open()) {
            PinScreen.check(tv, "Parental controls", () -> open(s));
            return;
        }
        open(s);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override public void focused(SettingsPanel p, RowList.Row row) {}

    /** Left from a section's own page: back to the sections; Right and the lists' ends: nothing more to go to. */
    @Override public boolean edge(SettingsPanel p, int dir) {
        if (dir == Ui.LEFT && pages.size() == 2 && !editing) up();
        return true;
    }

    /** A section's rows, shown beside the sections while the remote is on them. */
    @Override public List<RowList.Row> preview(String page) {
        return rows(page);
    }

    /** A page's line under its name, as the still has Logos' ("" for none). */
    @Override public String about(String page) {
        return aboutOf(page);
    }

    /** Pure: the line under a page's name. */
    static String aboutOf(String page) {
        switch (page == null ? "" : page) {
            case "general": return "Starting up, backups, sync and this TV";
            case "playlists": return "Your playlists, TorBox and catch-up";
            case "epg": return "Where the guide comes from, and when it is read";
            case "appearance": return "How Homefield looks";
            case "appearance.guide": return "How the guide looks and lists your channels";
            case "appearance.player": return "The clock, the panels and the channel tiles";
            case "appearance.logos": return "The pictures for your channels in the guide and the player";
            case "appearance.groups": return "How the categories are sorted";
            case "channels": return "One row a channel, hidden channels, categories and names";
            case "channels.rename": return "A name of your own for any channel";
            case "channels.categories": case "appearance.guide.groups": return "Show, hide, move, rename and sort them";
            case "theme": return "Homefield's colours";
            case "playback": return "The picture, the sound, subtitles and streams";
            case "remote": return "What the remote's keys do";
            case "remote.player": return "The keys while a channel plays full screen";
            case "parental": return "A PIN for what the family chooses";
            case "other": return "Search, reminders, recording and the scoreboard";
            case "about": return "This Homefield TV and its updates";
            default: return "";
        }
    }

    @Override public void answered(DialogCard d, RowList.Row answer) {
        closeDialog("yes".equals(answer.tag) ? onYes : null);
    }

    private void closeDialog(Runnable then) {
        onYes = null;
        dialog.hide(null);
        panel.list().setActive(true);
        if (then != null) then.run();
        refresh();
    }

    // ---- typing a field in ----------------------------------------------------------------------------------------

    /** The keyboard for a field: its name over it, a secret one as dots, an address started with http://. */
    void type(String field, String label, String value, boolean secret) {
        editField = field;
        editing = true;
        keys.label = label;
        keys.secret = secret;
        keys.hint = field.endsWith("server") || field.endsWith("url") || field.endsWith("guide") ? "http://" : "";
        keys.setText(value == null ? "" : value);
        keys.reset();
        keys.setVisibility(View.VISIBLE);
        sheet.setVisibility(View.VISIBLE);
        panel.list().setActive(false);
    }

    private void closeKeys() {
        editing = false;
        keys.setVisibility(View.GONE);
        sheet.setVisibility(View.GONE);
        panel.list().setActive(true);
        refresh();
    }

    String typing() {
        return editing ? editField : "";
    }

    @Override public void typed(Keyboard k, String text) {}

    @Override public void done(Keyboard k, String text) {
        String field = editField;
        Pages o = fieldOwner(field);
        // The keyboard let go of first: a secret typed is never left in it.
        k.setText("");
        closeKeys();
        o.typed(field, text);
        refresh();
    }

    @Override public boolean edge(Keyboard k, int dir) {
        return true;
    }

    // ---- previews -------------------------------------------------------------------------------------------------

    /** Previews: a page open (its parents under it), the remote on its line at. */
    void openAt(String page, int at) {
        if (panel == null) return;
        while (pages.size() > 1) up();
        for (String p : chain(page)) open(p);
        if (at >= 0) panel.setFocus(at);
    }

    /** Previews: a field typed in already, as if from the keyboard. */
    void previewTyped(String field, String text) {
        Pages o = fieldOwner(field);
        o.typed(field, text);
        refresh();
    }

    // ---- the sheet the keyboard sits on ---------------------------------------------------------------------------

    /** The screen darkened behind the keyboard, and a sheet of the panel's colour risen from the foot for it to sit on. */
    private static final class Sheet extends View {
        /** The sheet's top and the keyboard's (design pixels). */
        static final float TOP = 420f, KEYS_Y = 620f;
        private final Paint dim = new Paint(), sheet = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Sheet(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            dim.setColor(Palette.SCRIM);
            c.drawRect(0, 0, getWidth(), getHeight(), dim);
            float rad = Ui.f(16);
            r.set(-rad, Ui.f(TOP), getWidth() + rad, getHeight() + rad);
            sheet.setColor(Palette.PANEL);
            c.drawRoundRect(r, rad, rad, sheet);
        }
    }
}
