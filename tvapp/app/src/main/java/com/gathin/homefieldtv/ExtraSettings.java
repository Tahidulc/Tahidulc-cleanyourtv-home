package com.gathin.homefieldtv;

import android.widget.Toast;

import com.gathin.homefieldtv.backups.Backup;
import com.gathin.homefieldtv.backups.BackupStore;
import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.GuidePrefs;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Settings' Playlists, EPG, Parental controls and General › Restore data pages (the groups' are EditSettings', in
 * Appearance › TV guide › Manage groups), and Groups sorting's choices:
 *
 * <pre>
 *   playlists               each playlist › · TorBox › · Add playlist › · Use all playlists together · Update options:
 *                           Update all playlists · Update the playlists (how often); its counts and its catch-up
 *   playlist:ID             the playlist's own details as Homefield has them (its kind and server, when it runs out, its
 *                           connections), Use only this playlist, User-Agent, Remove playlist
 *   playlists.add(.m3u|.xtream)  M3U playlist or Xtream Codes, their fields typed in (a password as dots), Add playlist
 *   playlists.torbox        TorBoxSettings
 *   epg                     EPG sources › · Guide time shift · Update options: Update interval, hours · Update on app
 *                           start · Update EPG · Clear EPG · Latest update status
 *   parental                the switch · Change PIN · Locked channels · Require PIN for: Settings · locked groups and
 *                           channels (all greyed while it is off, as the other app has them)
 *   general.restore         this TV's last backup, the file chosen, Choose a backup file
 * </pre>
 *
 * Its settings' choices are "x." ones (x.guide.hours…), its fields "f:" (a playlist being added), "ua:" (a playlist's
 * user agent), "x.guide" and TorBoxSettings.FIELD. A backup is made on this TV and in Downloads, never sent
 * anywhere (BackupStore).
 */
final class ExtraSettings implements SettingsScreen.Pages {
    private static final String[] RESTORES = {BackupStore.LAST, BackupStore.PICKED};
    static final String[] SORTING = {"By order in playlist", "Your own order"};
    /** Android's file chooser picked a backup: it is offered to be put back when Settings shows next. */
    private static volatile boolean askPicked;
    /** Where the last backup made here was put, for Settings to say. */
    private static volatile String savedWhere = "";

    private final TvActivity tv;
    private final SettingsScreen s;
    /** What is being typed for a playlist to add: kept on this screen only until it is added. */
    private String name = "", url = "", guide = "", server = "", user = "", pass = "", status = "";
    /** Update all playlists: its last word. */
    private String refreshed = "";

    ExtraSettings(TvActivity tv, SettingsScreen s) {
        this.tv = tv;
        this.s = s;
    }

    @Override public boolean owns(String page) {
        return page.equals("playlists") || page.startsWith("playlists.") || page.startsWith("playlist:") || page.equals("epg")
                || page.startsWith("epg.") || page.equals("parental") || page.equals("general.restore");
    }

    @Override public String title(String page) {
        if (page.startsWith("playlist:")) {
            Sources.Entry e = Sources.get(tv, page.substring(9));
            return e == null ? "Playlist" : e.name;
        }
        switch (page) {
            case "playlists": return "Playlists";
            case "playlists.add": return "Add playlist";
            case "playlists.add.m3u": return "M3U playlist";
            case "playlists.add.xtream": return "Xtream Codes";
            case "playlists.torbox": return "TorBox";
            case "epg": return "EPG";
            case "epg.sources": return "EPG sources";
            case "parental": return "Parental controls";
            case "general.restore": return "Restore data";
            default: return "";
        }
    }

    // ---- the pages ------------------------------------------------------------------------------------------------

    @Override public List<RowList.Row> rows(String page) {
        List<RowList.Row> out = new ArrayList<>();
        if (page.startsWith("playlist:")) {
            playlist(page.substring(9), out);
            return out;
        }
        switch (page) {
            case "playlists": playlists(out); break;
            case "playlists.add":
                out.add(MoreSettings.page("playlists.add.m3u", "M3U playlist", "A playlist's address"));
                out.add(MoreSettings.page("playlists.add.xtream", "Xtream Codes", "A server, a username and a password"));
                out.add(RowList.Row.note("A playlist is kept only if it gives live channels."));
                break;
            case "playlists.add.m3u":
                out.add(field("name", "Name", name, "Optional, such as Home"));
                out.add(field("url", "Playlist address", host(url), "http://…/get.php?username=…  or  …/playlist.m3u"));
                out.add(field("guide", "EPG address (XMLTV)", host(guide), "Optional: the playlist's own is used"));
                out.add(RowList.Row.item("do:add", "Add playlist", "It is kept only if it gives live channels"));
                if (!status.isEmpty()) out.add(RowList.Row.note(status));
                break;
            case "playlists.add.xtream":
                out.add(field("name", "Name", name, "Optional, such as Home"));
                out.add(field("server", "Server address", host(server), "Such as http://example.com:8080"));
                out.add(field("user", "Username", user, ""));
                out.add(field("pass", "Password", pass.isEmpty() ? "" : dots(pass.length()), ""));
                out.add(RowList.Row.item("do:add", "Add playlist", "It is kept only if it gives live channels"));
                if (!status.isEmpty()) out.add(RowList.Row.note(status));
                break;
            case "playlists.torbox": TorBoxSettings.rows(tv, out); break;
            case "epg": epg(out); break;
            case "epg.sources": sources(out); break;
            case "parental": parental(out); break;
            case "general.restore": restore(out); break;
            default: break;
        }
        return out;
    }

    private void playlists(List<RowList.Row> out) {
        List<Sources.Entry> all = Sources.list(tv);
        String active = Sources.active(tv);
        long now = System.currentTimeMillis();
        for (Sources.Entry e : all) {
            String exp = Sources.expiry(Sources.info(tv, e.id), now);
            boolean used = active.isEmpty() || active.equals(e.id);
            out.add(MoreSettings.page("playlist:" + e.id, e.name + (used ? "" : "  (not in use)"),
                    e.source.describe() + (exp.isEmpty() ? "" : "  ·  " + exp)));
        }
        out.add(MoreSettings.page("playlists.torbox", "TorBox", com.gathin.homefieldtv.engine.TorBox.wanted(tv) ? "On" : "Off"));
        out.add(MoreSettings.page("playlists.add", "Add playlist", all.isEmpty() ? "An M3U address or an Xtream Codes login" : ""));
        if (all.size() > 1 && !active.isEmpty()) out.add(RowList.Row.item("do:all", "Use all playlists together", ""));
        if (all.isEmpty()) {
            out.add(RowList.Row.note("No playlist yet: add an M3U address or an Xtream Codes login."));
            return;
        }
        out.add(RowList.Row.header("Update options"));
        Library lib = Library.get(tv);
        String state = lib.busy() || Catalog.get(tv).busy() ? "Reading your playlists…" : !refreshed.isEmpty() ? refreshed : counts();
        out.add(RowList.Row.item("do:refresh", "Update all playlists", state));
        out.add(MoreSettings.pick("x.lists.hours", "Update the playlists",
                GuidePrefs.LISTS_NAMES[PlayerPrefs.indexOf(GuidePrefs.LISTS_HOURS, GuidePrefs.listsHours(tv))]));
        String archive = archiveLine();
        if (!archive.isEmpty()) out.add(RowList.Row.note(archive));
    }

    /** A playlist's own page: what Homefield knows of it (never its login), and what can be done with it. */
    private void playlist(String id, List<RowList.Row> out) {
        Sources.Entry e = Sources.get(tv, id);
        if (e == null) {
            out.add(RowList.Row.note("This playlist has gone from this TV."));
            return;
        }
        out.add(RowList.Row.item("info:kind", "Type", e.source.describe()));
        Sources.Info info = Sources.info(tv, id);
        String exp = Sources.expiry(info, System.currentTimeMillis());
        if (!exp.isEmpty()) out.add(RowList.Row.item("info:expiry", "Expiration date", exp));
        if (info != null && info.maxConnections > 0) {
            out.add(RowList.Row.item("info:max", "Max connections", info.maxConnections + (info.activeConnections > 0
                    ? "  ·  " + info.activeConnections + " in use when last asked" : "")));
        }
        String active = Sources.active(tv);
        List<Sources.Entry> all = Sources.list(tv);
        if (all.size() > 1) {
            boolean only = active.equals(id);
            out.add(RowList.Row.item(only ? "do:all" : "do:only", only ? "Use all playlists together" : "Use only this playlist",
                    only ? "Only this one is in use now" : "The others stay, not in use"));
        }
        String ua = PlayerPrefs.agentFor(tv, id);
        out.add(RowList.Row.item("type:ua:" + id, "User-Agent", ua.isEmpty() ? "Not set" : ua));
        out.add(RowList.Row.item("do:remove", "Remove playlist", "Its channels, movies and TV shows go from this TV"));
        out.add(RowList.Row.note("A User-Agent only for a provider that asks for one; Not set uses the app's own."));
    }

    private void epg(List<RowList.Row> out) {
        int extra = GuidePrefs.extra(tv).size();
        out.add(MoreSettings.page("epg.sources", "EPG sources", extra == 0 ? "Your playlists' own"
                : "Your playlists' own and " + extra + " more"));
        out.add(MoreSettings.pick("x.shift", "Guide time shift", GuidePrefs.shiftWords(GuidePrefs.shiftMin(tv))));
        out.add(RowList.Row.header("Update options"));
        out.add(MoreSettings.pick("x.guide.hours", "Update interval, hours", String.valueOf(GuidePrefs.guideHours(tv))));
        out.add(MoreSettings.tog("x.atstart", "Update on app start", "Playlists too, when the app starts afresh",
                GuidePrefs.atStart(tv)));
        Library lib = Library.get(tv);
        out.add(RowList.Row.item("do:epg.update", "Update EPG", lib.guideBusy() ? "Reading the guide…" : ""));
        out.add(RowList.Row.item("do:epg.clear", "Clear EPG", ""));
        RowList.Row latest = RowList.Row.item("info:epg", "Latest update status", epgStatus(lib.guideBusy(), lib.guideChannels(),
                lib.guideAt()));
        latest.enabled = false;
        out.add(latest);
    }

    /** Pure: the guide's last word, as the other app says it: "EPG is successfully updated for 16436 channels on 17/11/2026 08:34". */
    static String epgStatus(boolean busy, int channels, long at) {
        if (busy) return "The guide is being read…";
        if (at <= 0 || channels <= 0) return "Not read yet";
        return "EPG is successfully updated for " + channels + (channels == 1 ? " channel" : " channels") + " on "
                + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.UK).format(new Date(at));
    }

    private void sources(List<RowList.Row> out) {
        RowList.Row own = RowList.Row.item("info:own", "Your playlists' own guides", "Read first");
        out.add(own);
        List<String> extra = GuidePrefs.extra(tv);
        for (int i = 0; i < extra.size(); i++) {
            out.add(RowList.Row.item("guide:" + i, GuidePrefs.host(extra.get(i)), "Read after your playlists' own  ·  OK to remove it"));
        }
        if (extra.size() < GuidePrefs.MAX_EXTRA) {
            out.add(RowList.Row.item("type:x.guide", "Add source", "An XMLTV address (.xml or .xml.gz), typed with the remote"));
        }
        out.add(RowList.Row.note("These fill in what your playlists' own guides leave out. A change reads your playlists and guide again."));
    }

    /** Parental controls: the other app's lines, greyed while it is off; then Homefield's locked groups and channels. */
    private void parental(List<RowList.Row> out) {
        boolean on = Parental.hasPin(tv);
        out.add(MoreSettings.tog("x.pc", on ? "On" : "Off", "", on));
        out.add(enabled(RowList.Row.item("do:pin.change", "Change PIN", ""), on));
        int mode = Parental.hides(tv) ? 1 : 0;
        out.add(enabled(MoreSettings.pick("x.mode", "Locked channels", Parental.MODE_NAMES[mode]), on));
        out.add(RowList.Row.header("Require PIN for"));
        out.add(enabled(MoreSettings.tog("x.locksettings", "Settings", "", Parental.locksSettings(tv)), on));
        Library lib = Library.get(tv);
        List<String> every = Categories.every(tv, lib.groups());
        if (!every.isEmpty()) out.add(RowList.Row.header("Locked groups"));
        for (String g : every) {
            boolean locked = Parental.lockedGroups(tv).contains(g);
            out.add(enabled(RowList.Row.toggle("lockcat:" + g, Categories.label(tv, g), "", locked), on));
        }
        out.add(RowList.Row.header("Locked channels"));
        List<String> keys = new ArrayList<>(Parental.lockedChannels(tv));
        java.util.Collections.sort(keys);
        for (String k : keys) {
            Channel c = lib.byKey(k);
            out.add(enabled(RowList.Row.item("unlockch:" + k, c == null ? "A channel not in your playlists now" : c.name,
                    "Locked  ·  OK to unlock"), on));
        }
        out.add(RowList.Row.note(keys.isEmpty() ? "None yet: hold OK on a channel in Live TV and choose Lock this channel."
                : "Lock more from a channel's menu: hold OK on it in Live TV."));
        out.add(RowList.Row.note(on ? "Once the PIN is entered, locked channels play until you leave Homefield TV."
                : "On: a four-number PIN, asked before a locked channel plays (and Settings opens, when they are locked)."));
    }

    private static RowList.Row enabled(RowList.Row r, boolean on) {
        r.enabled = on;
        return r;
    }

    private void restore(List<RowList.Row> out) {
        for (String which : RESTORES) {
            BackupStore.Kept k = BackupStore.kept(tv, which);
            if (k == null) continue;
            out.add(RowList.Row.item("restore:" + which, restoreName(which), "Made " + MoreSettings.when(k.contents.made) + "  ·  " + sizes(k)));
        }
        out.add(RowList.Row.item("do:pick", "Choose a backup file", "From Downloads or a USB stick, where this TV has a file chooser"));
        out.add(RowList.Row.note("A backup holds your playlists' logins: keep its file to yourself. It has no TorBox key: on a new "
                + "TV, type that in again in Settings → Playlists → TorBox."));
        out.add(RowList.Row.note("Restoring puts everything back as it was in the backup, then starts the screens again."));
    }

    // ---- OK on a row ----------------------------------------------------------------------------------------------

    @Override public void chosen(String page, RowList.Row row) {
        String t = row.tag instanceof String ? (String) row.tag : "";
        if (t.startsWith("page:")) {
            s.open(t.substring(5));
        } else if (t.startsWith("pick:")) {
            s.open(SettingsScreen.PICK + t.substring(5));
        } else if (t.startsWith("f:")) {
            String f = t.substring(2);
            s.type("f:" + f, row.title, f.equals("pass") ? "" : value("f:" + f), f.equals("pass"));
        } else if (t.startsWith("type:")) {
            String f = t.substring(5);
            if (f.equals("x.guide")) {
                s.type("x.guide", "An EPG address (XMLTV)", "http://", false);
            } else if (f.startsWith("ua:")) {
                s.type(f, "User-Agent", PlayerPrefs.agentFor(tv, f.substring(3)), false);
            } else if (f.equals(TorBoxSettings.FIELD)) {
                // The TorBox key shows as dots, as a password does, and the one saved is never put back in the field.
                s.type(TorBoxSettings.FIELD, "Your TorBox API key", "", true);
            }
        } else if (t.startsWith("tog:")) {
            toggle(page, t.substring(4));
        } else if (t.startsWith("do:")) {
            act(page, t.substring(3));
        } else if (t.startsWith("guide:")) {
            int i = number(t.substring(6));
            List<String> extra = GuidePrefs.extra(tv);
            if (i < 0 || i >= extra.size()) return;
            s.ask("Remove this EPG source?", GuidePrefs.host(extra.get(i)), "Remove it", "Keep it", () -> {
                GuidePrefs.removeExtra(tv, i);
                readAgain();
            });
        } else if (t.startsWith("lockcat:")) {
            String g = t.substring(8);
            Parental.lockGroup(tv, g, !Parental.lockedGroups(tv).contains(g));
            s.refresh();
        } else if (t.startsWith("unlockch:")) {
            Parental.lockChannel(tv, t.substring(9), false);
            s.refresh();
        } else if (t.startsWith("restore:")) {
            restoreAsk(t.substring(8));
        } else if (page.equals("playlists.torbox")) {
            if (TorBoxSettings.chosen(tv, t, this::torboxAnswered)) s.refresh();
        }
    }

    private void toggle(String page, String key) {
        switch (key) {
            case "x.atstart":
                GuidePrefs.setAtStart(tv, !GuidePrefs.atStart(tv));
                break;
            case "x.locksettings":
                if (Parental.hasPin(tv)) Parental.setLocksSettings(tv, !Parental.locksSettings(tv));
                break;
            case "x.pc":
                if (!Parental.hasPin(tv)) {
                    PinScreen.choose(tv, "Choose a PIN", pin -> {
                        Parental.setPin(tv, pin);
                        s.toast("Parental controls are on");
                        s.refresh();
                    });
                    return;
                }
                Parental.clearPin(tv);
                s.toast("Parental controls are off");
                break;
            default:
                break;
        }
        s.refresh();
    }

    private void act(String page, String what) {
        Library lib = Library.get(tv);
        switch (what) {
            case "all":
                Sources.setActive(tv, Sources.ALL);
                lib.refresh(null);
                s.refresh();
                break;
            case "only":
                if (page.startsWith("playlist:")) {
                    Sources.setActive(tv, page.substring(9));
                    lib.refresh(null);
                }
                s.refresh();
                break;
            case "remove":
                if (!page.startsWith("playlist:")) return;
                String id = page.substring(9);
                Sources.Entry e = Sources.get(tv, id);
                s.ask("Remove " + (e == null ? "this playlist" : e.name) + "?", "Its channels, movies and TV shows go from this TV. "
                        + "Your phones, tablets and other TVs keep theirs.", "Remove playlist", "Keep it", () -> {
                    Sources.remove(tv, id);
                    lib.refresh(null);
                    s.up();
                });
                break;
            case "add":
                add(page.endsWith("m3u"));
                break;
            case "refresh":
                refreshed = "Reading your playlists…";
                s.refresh();
                lib.refresh((n, err) -> {
                    refreshed = err != null ? err : String.format(Locale.UK, "%,d channels", n);
                    s.refresh();
                });
                Catalog.get(tv).refresh(true);
                break;
            case "epg.update":
                if (lib.count() == 0) {
                    s.toast("Add a playlist first: its channels come with their guide");
                    break;
                }
                lib.updateGuide();
                s.toast("Reading the guide again…");
                s.refresh();
                break;
            case "epg.clear":
                lib.clearGuideNow();
                s.toast("The guide is cleared until it is read again (Update EPG)");
                s.refresh();
                break;
            case "pin.change":
                if (!Parental.hasPin(tv)) return;
                PinScreen.choose(tv, "Choose a new PIN", pin -> {
                    Parental.setPin(tv, pin);
                    s.toast("PIN changed");
                });
                break;
            case "pick":
                if (!BackupStore.pick(tv)) s.toast("This TV has no file chooser. Use this TV's last backup instead.");
                break;
            default:
                break;
        }
    }

    /** A backup offered to be put back, then after the PIN (when one is set) it replaces what is set here. */
    private void restoreAsk(String which) {
        BackupStore.Kept k = BackupStore.kept(tv, which);
        if (k == null) return;
        s.ask("Restore the backup made " + MoreSettings.when(k.contents.made) + "?", "It has " + sizes(k) + ", and every setting. "
                + "What's set on this TV now is replaced by it.", "Restore it", "Not now", () ->
                PinScreen.check(tv, "Restoring needs the PIN", () -> {
                    BackupStore.restore(tv, k);
                    Toast.makeText(tv, "Restored: starting again with the backup's settings", Toast.LENGTH_LONG).show();
                    Library.get(tv).refresh(null);
                    s.restart("general");
                }));
    }

    /** Settings shows again (back from Android's file chooser): a backup chosen there is offered at once. */
    void resumed() {
        if (!askPicked) return;
        askPicked = false;
        restoreAsk(BackupStore.PICKED);
    }

    /** The playlist tried, and kept only when it gives live channels. */
    private void add(boolean m3u) {
        Source src = m3u ? new Source(Source.M3U, url, guide, null, null, null) : new Source(Source.XTREAM, null, guide, server, user, pass);
        if (!src.usable()) {
            status = m3u ? "Type the playlist's address first." : "Type the server's address, the username and the password first.";
            s.refresh();
            return;
        }
        status = "Checking it…";
        s.refresh();
        Library.get(tv).test(null, name, src, (n, err) -> {
            if (err != null) {
                status = err;
            } else {
                status = String.format(Locale.UK, "Added: %,d channels. Its movies and TV shows are on their way.", n);
                name = url = guide = server = user = pass = "";
            }
            s.refresh();
        });
    }

    /** TorBox answered a check: its words on the page, the remote where it was. */
    private void torboxAnswered() {
        if (s.typing().isEmpty()) s.refresh();
    }

    /** The playlists and guide read again for a change to the guides, so it shows now. */
    private void readAgain() {
        Library.get(tv).refresh(null);
        s.toast("Reading your playlists and guide again…");
    }

    // ---- a setting's choices --------------------------------------------------------------------------------------

    @Override public SettingsScreen.Picker picker(String key) {
        switch (key) {
            case "x.lists.hours": return new SettingsScreen.Picker("Update the playlists", GuidePrefs.LISTS_NAMES,
                    PlayerPrefs.indexOf(GuidePrefs.LISTS_HOURS, GuidePrefs.listsHours(tv)), "Channels, movies and TV shows read again.");
            case "x.guide.hours": {
                String[] n = new String[GuidePrefs.GUIDE_HOURS.length];
                for (int i = 0; i < n.length; i++) n[i] = String.valueOf(GuidePrefs.GUIDE_HOURS[i]);
                return new SettingsScreen.Picker("Update interval, hours", n, PlayerPrefs.indexOf(GuidePrefs.GUIDE_HOURS,
                        GuidePrefs.guideHours(tv)), "The guide holds the next 12 hours.");
            }
            case "x.shift": {
                String[] n = new String[GuidePrefs.SHIFTS.length];
                for (int i = 0; i < n.length; i++) n[i] = GuidePrefs.shiftWords(GuidePrefs.SHIFTS[i]);
                return new SettingsScreen.Picker("Guide time shift", n, PlayerPrefs.indexOf(GuidePrefs.SHIFTS, GuidePrefs.shiftMin(tv)),
                        "For a guide whose times are out by a fixed amount.");
            }
            case "x.mode": {
                int m = Parental.hides(tv) ? 1 : 0;
                return new SettingsScreen.Picker("Locked channels", Parental.MODE_NAMES, m, Parental.MODE_ABOUT[m]);
            }
            case "x.sorting": return new SettingsScreen.Picker("Groups sorting", SORTING, Categories.ordered(tv) ? 1 : 0,
                    "Your own order: the groups moved in TV guide › Manage groups, or with Move group (hold OK on a group in "
                            + "the TV guide).");
            default: return null;
        }
    }

    @Override public void picked(String key, int i) {
        switch (key) {
            case "x.lists.hours":
                if (i >= 0 && i < GuidePrefs.LISTS_HOURS.length) GuidePrefs.setListsHours(tv, GuidePrefs.LISTS_HOURS[i]);
                break;
            case "x.guide.hours":
                if (i >= 0 && i < GuidePrefs.GUIDE_HOURS.length) GuidePrefs.setGuideHours(tv, GuidePrefs.GUIDE_HOURS[i]);
                break;
            case "x.shift":
                if (i >= 0 && i < GuidePrefs.SHIFTS.length && GuidePrefs.SHIFTS[i] != GuidePrefs.shiftMin(tv)) {
                    GuidePrefs.setShiftMin(tv, GuidePrefs.SHIFTS[i]);
                    readAgain();
                }
                break;
            case "x.mode":
                Parental.setHides(tv, i == 1);
                break;
            case "x.sorting":
                if (i == 0 && Categories.ordered(tv)) Categories.resetOrder(tv);
                break;
            default:
                break;
        }
    }

    // ---- typed in -------------------------------------------------------------------------------------------------

    /** Whether a field typed in is one of these pages'. */
    boolean ownsField(String field) {
        return field.startsWith("f:") || field.startsWith("ua:") || field.startsWith("x.") || field.equals(TorBoxSettings.FIELD);
    }

    @Override public String value(String field) {
        switch (field) {
            case "f:name": return name;
            case "f:url": return url;
            case "f:guide": return guide;
            case "f:server": return server;
            case "f:user": return user;
            default: return "";
        }
    }

    @Override public void typed(String field, String text) {
        String v = text.trim();
        if (field.equals(TorBoxSettings.FIELD)) {
            // Kept and checked with TorBox; nothing of it stays on the keyboard.
            TorBoxSettings.typed(tv, v, this::torboxAnswered);
            return;
        }
        if (field.startsWith("ua:")) {
            PlayerPrefs.setAgentFor(tv, field.substring(3), v);
            return;
        }
        if (field.equals("x.guide")) {
            if (v.isEmpty() || v.equals("http://") || v.equals("https://")) return;
            if (GuidePrefs.addExtra(tv, v)) readAgain();
            else s.toast("That isn't a guide's address to read: it starts http:// or https://, once");
            return;
        }
        switch (field) {
            case "f:name": name = v; break;
            case "f:url": url = v; break;
            case "f:guide": guide = v; break;
            case "f:server": server = v; break;
            case "f:user": user = v; break;
            // A password as typed (spaces and all).
            case "f:pass": pass = text; break;
            default: break;
        }
        status = "";
    }

    // ---- backups --------------------------------------------------------------------------------------------------

    /** General → Back up data: made on this TV and in Downloads (where it can be), and said where. */
    static void backUp(TvActivity tv, String version, SettingsScreen s) {
        try {
            String where = BackupStore.backUp(tv, version);
            savedWhere = where.isEmpty() ? "on this TV" : where;
            s.toast(where.isEmpty() ? "Backed up on this TV (no room for a file people can reach)" : "Backed up: " + where);
        } catch (IOException | RuntimeException e) {
            s.toast("That backup couldn't be made");
        }
    }

    static String savedWhere() {
        return savedWhere;
    }

    /** Android's file chooser answered (TvActivity): a backup chosen is offered to be put back; anything else is said. */
    static void picked(TvActivity tv, String said) {
        if (said.isEmpty()) askPicked = true;
        else Toast.makeText(tv, said, Toast.LENGTH_LONG).show();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static RowList.Row field(String key, String label, String value, String hint) {
        return RowList.Row.item("f:" + key, label, value.isEmpty() ? hint : value);
    }

    /** Pure: an address as Settings shows it: its server's name alone ("provider.example:8080"), never its login. */
    static String host(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return "";
        int s = u.indexOf("://");
        if (s >= 0) u = u.substring(s + 3);
        int slash = u.indexOf('/');
        int at = u.lastIndexOf('@', slash < 0 ? u.length() : slash);
        if (at >= 0) u = u.substring(at + 1);
        int end = u.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int i = u.indexOf(c);
            if (i >= 0) end = Math.min(end, i);
        }
        return u.substring(0, end);
    }

    static String dots(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(n, 16); i++) b.append('•');
        return b.toString();
    }

    private String counts() {
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        return String.format(Locale.UK, "%,d channels  ·  %,d movies  ·  %,d TV shows", lib.count(), cat.films().size(), cat.series().size());
    }

    /** Catch-up: how many of the channels the provider keeps programmes for, and for how long ("" before they're in). */
    private String archiveLine() {
        Library lib = Library.get(tv);
        if (!lib.ready()) return "";
        int[] a = lib.archive();
        return Catchup.summary(a[0], a[1]);
    }

    private static String restoreName(String which) {
        return BackupStore.PICKED.equals(which) ? "The file you chose" : "This TV's last backup";
    }

    private static String sizes(BackupStore.Kept k) {
        int[] n = Backup.counts(k.contents);
        return plural(n[0], "playlist") + ", " + plural(n[1], "favourite") + ", " + plural(n[2], "reminder");
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
