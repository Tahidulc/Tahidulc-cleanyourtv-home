package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Settings: the family's playlists (which are in use, when each runs out), adding an M3U address or an Xtream Codes
 * login with the on-screen keyboard (kept only when it gives live channels), reading everything again, and removing
 * everything from this TV. The password is never shown, only dots; an address shows only its server's name.
 */
final class SettingsScreen extends Screen implements ListPanel.Host, Keyboard.Host {
    private static final List<String> MENU = Arrays.asList("Your playlists", "Add an M3U playlist", "Add an Xtream Codes login",
            "Refresh now", "Remove everything", "About");
    private static final int PLAYLISTS = 0, ADD_M3U = 1, ADD_XTREAM = 2, REFRESH = 3, CLEAR = 4, ABOUT = 5;

    private Strip strip;
    private ListPanel menu, content;
    private Keyboard keys;
    private Shade shade;
    private boolean inContent, editing, confirm;
    private String editField = "", status = "", sub = "";
    // What is being typed in: kept on this screen only until the playlist is added.
    private String name = "", url = "", guide = "", server = "", user = "", pass = "";

    SettingsScreen(TvActivity tv) {
        super(tv);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        strip = new Strip(tv);
        menu = new ListPanel(tv);
        menu.x = 262f;
        menu.y = 170f;
        menu.w = 470f;
        menu.setHost(this);
        content = new ListPanel(tv);
        content.x = 820f;
        content.y = 170f;
        content.w = 1040f;
        content.setHost(this);
        shade = new Shade(tv);
        keys = new Keyboard(tv, Keyboard.FULL);
        keys.x = 494f;
        keys.y = 478f;
        keys.setHost(this);
        keys.setVisibility(View.GONE);
        shade.setVisibility(View.GONE);
        f.addView(new Heading(tv), new FrameLayout.LayoutParams(-1, -1));
        f.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        f.addView(content, new FrameLayout.LayoutParams(-1, -1));
        f.addView(strip, new FrameLayout.LayoutParams(-1, -1));
        f.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        f.addView(keys, new FrameLayout.LayoutParams(-1, -1));
        List<ListPanel.Item> items = new ArrayList<>();
        for (int i = 0; i < MENU.size(); i++) items.add(new ListPanel.Item(i, ListPanel.TEXT, MENU.get(i), ""));
        menu.setItems(items);
        menu.setActive(true);
        fillContent();
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        fillContent();
    }

    @Override void tick() {
        strip.invalidate();
    }

    @Override void channelsChanged() {
        if (!editing) fillContent();
    }

    @Override void catalogChanged() {
        channelsChanged();
    }

    private int section() {
        ListPanel.Item it = menu.item();
        return it == null ? 0 : (Integer) it.tag;
    }

    /** Previews: a section open, with what was typed. */
    void preview(int sectionIndex, String n, String s, String u) {
        menu.setFocus(sectionIndex);
        name = n;
        server = s;
        user = u;
        pass = "password";
        fillContent();
    }

    private void fillContent() {
        if (content == null) return;
        List<ListPanel.Item> out = new ArrayList<>();
        Library lib = Library.get(tv);
        switch (section()) {
            case PLAYLISTS: {
                if (!sub.isEmpty()) {
                    Sources.Entry e = Sources.get(tv, sub);
                    if (e == null) {
                        sub = "";
                        fillContent();
                        return;
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
            case REFRESH:
                out.add(new ListPanel.Item("refresh", ListPanel.TEXT, "Read everything again now", "Channels, the guide, films and TV shows"));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, lib.busy() || Catalog.get(tv).busy() ? "Reading your playlist…"
                        : status.isEmpty() ? counts() : status, ""));
                break;
            case CLEAR:
                if (confirm) {
                    out.add(new ListPanel.Item(null, ListPanel.HEADING, "Remove everything from this TV?", ""));
                    out.add(new ListPanel.Item("yes", ListPanel.TEXT, "Yes, remove everything", ""));
                    out.add(new ListPanel.Item("no", ListPanel.TEXT, "No, keep it all", ""));
                } else {
                    out.add(new ListPanel.Item("clear", ListPanel.TEXT, "Remove everything from this TV",
                            "Your playlists, channels, favourites and where you left films"));
                }
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
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Font: Carlito (SIL Open Font License).", ""));
                break;
        }
        content.setItems(out);
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
        if (inContent) return content.key(code);
        boolean used = menu.key(code);
        if (used && code != KeyEvent.KEYCODE_DPAD_RIGHT && code != KeyEvent.KEYCODE_DPAD_LEFT) {
            status = "";
            sub = "";
            confirm = false;
            fillContent();
        }
        return used;
    }

    @Override void ok() {
        if (editing) {
            keys.ok();
            return;
        }
        if (inContent) {
            content.ok();
            return;
        }
        toContent();
    }

    @Override boolean back() {
        if (editing) {
            closeKeys();
            return true;
        }
        if (inContent) {
            if (!sub.isEmpty() || confirm) {
                sub = "";
                confirm = false;
                fillContent();
                return true;
            }
            toMenu();
            return true;
        }
        return false;
    }

    private void toContent() {
        if (!content.canFocus()) return;
        inContent = true;
        menu.setActive(false);
        content.setActive(true);
    }

    private void toMenu() {
        inContent = false;
        menu.setActive(true);
        content.setActive(false);
    }

    @Override public void chosen(ListPanel list, ListPanel.Item item) {
        if (list == menu) {
            toContent();
            return;
        }
        Object tag = item.tag;
        if (!(tag instanceof String)) return;
        String t = (String) tag;
        Library lib = Library.get(tv);
        if (t.startsWith("f:")) {
            openKeys(t.substring(2), item.title);
            return;
        }
        switch (section()) {
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
                content.setFocus(sub.isEmpty() ? 0 : 1);
                return;
            case ADD_M3U:
            case ADD_XTREAM:
                if (t.equals("add")) add();
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
                    tv.live.forget();
                    lib.clear();
                    Resume.clear(tv);
                    status = "";
                } else {
                    confirm = false;
                }
                fillContent();
                content.setFocus(confirm ? 1 : 0);
                return;
            default:
        }
    }

    /** The playlist tried, and kept only when it gives live channels. */
    private void add() {
        boolean m3u = section() == ADD_M3U;
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

    @Override public void focused(ListPanel list, ListPanel.Item item) {}

    @Override public boolean edge(ListPanel list, int dir) {
        if (list == menu && dir == Ui.RIGHT) {
            toContent();
            return true;
        }
        if (list == content && dir == Ui.LEFT) {
            toMenu();
            return true;
        }
        if (list == menu && dir == Ui.LEFT) {
            tv.pop();
            return true;
        }
        return true;
    }

    // ---- typing a field in ------------------------------------------------------------------------------------

    private void openKeys(String field, String label) {
        editField = field;
        editing = true;
        keys.label = label;
        keys.secret = field.equals("pass");
        keys.hint = field.equals("server") ? "http://" : "";
        keys.setText(value(field));
        keys.reset();
        keys.setVisibility(View.VISIBLE);
        shade.setVisibility(View.VISIBLE);
        content.setActive(false);
    }

    private void closeKeys() {
        editing = false;
        keys.setVisibility(View.GONE);
        shade.setVisibility(View.GONE);
        content.setActive(true);
        fillContent();
    }

    private String value(String field) {
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
        switch (editField) {
            case "name": name = v; break;
            case "url": url = v; break;
            case "guide": guide = v; break;
            case "server": server = v; break;
            case "user": user = v; break;
            case "pass": pass = text; break;
            default: break;
        }
        status = "";
        closeKeys();
    }

    @Override public boolean edge(Keyboard k, int dir) {
        return true;
    }

    /** The page's name. */
    private static final class Heading extends View {
        private final TextPaint p = Ui.paint(56, false, Ui.WHITE);

        Heading(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawText("Settings", Ui.f(262), Ui.f(120), p);
        }
    }

    /** The darkened screen behind the keyboard. */
    private static final class Shade extends View {
        private final Paint p = Ui.fill(0xFF0A3B86), edge = Ui.fill(0x804F92DA);

        Shade(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawRect(Ui.f(Strip.W), Ui.f(452), getWidth(), getHeight(), p);
            c.drawRect(Ui.f(Strip.W), Ui.f(452), getWidth(), Ui.f(454), edge);
        }
    }
}
