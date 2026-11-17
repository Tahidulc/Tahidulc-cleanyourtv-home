package com.gathin.homefieldtv;

import android.widget.Toast;

import com.gathin.homefieldtv.backups.Backup;
import com.gathin.homefieldtv.backups.BackupStore;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.GuidePrefs;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings' Guide, Categories, Parental control and Backup sections (MoreSettings hands them over): how often the
 * guide and playlists are read again and guides of the family's own (GuidePrefs); the playlists' categories hidden,
 * moved and named (Categories); the PIN and what it locks (Parental, PinScreen); and backups made on this TV and put
 * back from it or from a file chosen (Backup, BackupStore; nothing over the network). Lines are tagged as MoreSettings'
 * are: "set:" opens a setting's choices, "opt:" picks one, "tog:" flips a switch, "do:" acts, "type:" opens the keyboard.
 */
final class ExtraSettings {
    static final int STAY = MoreSettings.STAY;
    private static final String[] RESTORES = {BackupStore.LAST, BackupStore.PICKED};
    /** Opened next time Backup fills: a file just chosen with Android's chooser, to confirm. */
    private static volatile String openNext;
    /** Where the last backup made here was put, for Settings to say. */
    private static volatile String savedWhere = "";

    private final TvActivity tv;
    private final MoreSettings.Host host;
    /** What is open in the section: "" for its own lines, else a setting's choices, a category's, or a restore to confirm. */
    private String open = "";
    /** The line to keep the remote on once the lines are filled again (a category moved a place, and again). */
    private String keep;

    ExtraSettings(TvActivity tv, MoreSettings.Host host) {
        this.tv = tv;
        this.host = host;
    }

    void reset() {
        open = "";
    }

    boolean isOpen() {
        return !open.isEmpty();
    }

    /** Back in a section: what was open closed, the remote on its line; STAY when nothing was. */
    int back(int section) {
        if (open.isEmpty()) return STAY;
        String was = open;
        open = "";
        return lineOf(section, was);
    }

    /**
     * Where the remote goes in what was opened: the line just pressed when it is still there, the choice in use, else
     * its first line to press.
     */
    int focus(List<ListPanel.Item> items) {
        String k = keep;
        keep = null;
        if (k != null) {
            int at = indexOfTag(items, k);
            if (at >= 0) return at;
        }
        for (int i = 0; i < items.size(); i++) {
            String sub = items.get(i).subtitle;
            if (sub != null && sub.startsWith("Chosen")) return i;
        }
        for (int i = 0; i < items.size(); i++) if (items.get(i).kind == ListPanel.TEXT) return i;
        return 0;
    }

    // ---- the lines ------------------------------------------------------------------------------------------------

    void fill(int section, List<ListPanel.Item> out) {
        if (section == MoreSettings.BACKUP && openNext != null) {
            open = openNext;
            openNext = null;
        }
        if (!open.isEmpty() && opened(out)) return;
        open = "";
        switch (section) {
            case MoreSettings.GUIDE:
                guide(out);
                break;
            case MoreSettings.CATEGORIES:
                categories(out);
                break;
            case MoreSettings.PARENTAL:
                parental(out);
                break;
            default:
                backup(out);
                break;
        }
    }

    private void guide(List<ListPanel.Item> out) {
        int g = PlayerPrefs.indexOf(GuidePrefs.GUIDE_HOURS, GuidePrefs.guideHours(tv));
        out.add(line("guide.hours", "Update the guide", GuidePrefs.GUIDE_NAMES[g] + "  ·  It holds the next 12 hours"));
        int l = PlayerPrefs.indexOf(GuidePrefs.LISTS_HOURS, GuidePrefs.listsHours(tv));
        out.add(line("lists.hours", "Update the playlists", GuidePrefs.LISTS_NAMES[l] + "  ·  Channels, films and series read again"));
        out.add(toggle("atstart", "Update when Homefield TV starts", GuidePrefs.atStart(tv),
                "Playlists and guide read again each time it starts afresh"));
        out.add(line("shift", "Guide time shift", GuidePrefs.shiftWords(GuidePrefs.shiftMin(tv))
                + "  ·  For a guide whose times are out by a fixed amount"));
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "More guides (XMLTV)", ""));
        List<String> extra = GuidePrefs.extra(tv);
        for (int i = 0; i < extra.size(); i++) {
            out.add(new ListPanel.Item("guide:" + i, ListPanel.TEXT, GuidePrefs.host(extra.get(i)),
                    "Read after your playlists' own  ·  OK to remove it"));
        }
        if (extra.size() < GuidePrefs.MAX_EXTRA) {
            out.add(new ListPanel.Item("type:guide", ListPanel.TEXT, "Add a guide's address",
                    "An XMLTV address (.xml or .xml.gz), typed with the remote"));
        }
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Your playlists' own guides come first; these fill in what they leave out.", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "A change here reads your playlists and guide again.", ""));
    }

    private void categories(List<ListPanel.Item> out) {
        Library lib = Library.get(tv);
        List<String> every = Categories.every(tv, lib.groups());
        if (every.isEmpty()) {
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "Your playlists' categories show here once their channels are in.", ""));
            return;
        }
        Map<String, Integer> counts = counts(lib);
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Your playlists' categories, in order", ""));
        boolean anyHidden = false;
        for (String g : every) {
            boolean hidden = Categories.isHidden(tv, g);
            anyHidden |= hidden;
            Integer n = counts.get(g);
            String sub = (hidden ? "Hidden" : "Shown") + "  ·  " + (n == null ? 0 : n) + (n != null && n == 1 ? " channel" : " channels")
                    + (Categories.renamed(tv, g) ? "  ·  the playlist calls it " + (g.isEmpty() ? Categories.UNNAMED : g) : "");
            out.add(new ListPanel.Item("cat:" + g, ListPanel.TEXT, Categories.label(tv, g), sub));
        }
        if (anyHidden) out.add(new ListPanel.Item("do:cat.showall", ListPanel.TEXT, "Show every category", ""));
        if (Categories.ordered(tv)) out.add(new ListPanel.Item("do:cat.reorder", ListPanel.TEXT, "The playlists' order again", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "OK on one to hide it, move it or give it a name of your own.", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Hidden: out of the guide, Live TV, channel up and down and search; favourites stay.", ""));
    }

    private void parental(List<ListPanel.Item> out) {
        if (!Parental.hasPin(tv)) {
            out.add(new ListPanel.Item("do:pin.set", ListPanel.TEXT, "Set a PIN", "Four numbers, asked before a locked channel plays"));
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "With a PIN set, lock categories here, and channels from their menu in Live TV.", ""));
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "Settings can be locked with it too.", ""));
            return;
        }
        if (!Parental.open()) {
            out.add(new ListPanel.Item("do:pin.enter", ListPanel.TEXT, "Enter the PIN to change these", "Parental control is on"));
            return;
        }
        int mode = Parental.hides(tv) ? 1 : 0;
        out.add(line("mode", "Locked channels", Parental.MODE_NAMES[mode] + "  ·  " + Parental.MODE_ABOUT[mode]));
        out.add(toggle("settings", "Lock Settings", Parental.locksSettings(tv), "The PIN asked before Settings opens"));
        out.add(new ListPanel.Item("do:pin.change", ListPanel.TEXT, "Change the PIN", ""));
        out.add(new ListPanel.Item("do:pin.off", ListPanel.TEXT, "Turn parental control off", "The PIN taken away: nothing is locked without one"));
        Library lib = Library.get(tv);
        List<String> every = Categories.every(tv, lib.groups());
        if (!every.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.HEADING, "Locked categories", ""));
        for (String g : every) {
            boolean on = Parental.lockedGroups(tv).contains(g);
            out.add(new ListPanel.Item("lockcat:" + g, ListPanel.TEXT, Categories.label(tv, g), on ? "Locked  ·  OK to unlock" : "Not locked"));
        }
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Locked channels", ""));
        List<String> keys = new ArrayList<>(Parental.lockedChannels(tv));
        java.util.Collections.sort(keys);
        for (String k : keys) {
            Channel c = lib.byKey(k);
            out.add(new ListPanel.Item("unlockch:" + k, ListPanel.TEXT, c == null ? "A channel not in your playlists now" : c.name,
                    "Locked  ·  OK to unlock"));
        }
        out.add(new ListPanel.Item(null, ListPanel.NOTE, keys.isEmpty() ? "None yet: hold OK on a channel in Live TV and choose Lock this channel."
                : "Lock more from a channel's menu: hold OK on it in Live TV.", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Once the PIN is entered, locked channels play until you leave Homefield TV.", ""));
    }

    private void backup(List<ListPanel.Item> out) {
        BackupStore.Kept last = BackupStore.kept(tv, BackupStore.LAST);
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Back up", ""));
        String made = last == null ? "Settings, playlists, favourites, recent channels, reminders and My List, to a file"
                : "Last: " + when(last.contents.made) + (savedWhere.isEmpty() ? "" : "  ·  " + savedWhere);
        out.add(new ListPanel.Item("do:backup", ListPanel.TEXT, "Back up now", made));
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Restore", ""));
        for (String which : RESTORES) {
            BackupStore.Kept k = BackupStore.kept(tv, which);
            if (k == null) continue;
            out.add(new ListPanel.Item("restore:" + which, ListPanel.TEXT, restoreName(which), "Made " + when(k.contents.made) + "  ·  " + sizes(k)));
        }
        out.add(new ListPanel.Item("do:pick", ListPanel.TEXT, "Choose a backup file", "From Downloads or a USB stick, where this TV has a file chooser"));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "A backup holds your playlists' logins: keep its file to yourself.", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Not the TorBox key: on a new TV, type that in again in Settings → TorBox.", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Restoring puts everything back as it was in the backup, then starts the screens again.", ""));
    }

    /** What is open, filled in: false when it is gone (a category or guide no longer there). */
    private boolean opened(List<ListPanel.Item> out) {
        if (open.startsWith("cat:")) {
            String g = open.substring(4);
            Library lib = Library.get(tv);
            List<String> every = Categories.every(tv, lib.groups());
            int at = every.indexOf(g);
            if (at < 0) return false;
            out.add(new ListPanel.Item(null, ListPanel.HEADING, Categories.label(tv, g) + "  ·  " + ordinal(at + 1) + " of " + every.size(), ""));
            boolean hidden = Categories.isHidden(tv, g);
            out.add(new ListPanel.Item(hidden ? "do:cat.show" : "do:cat.hide", ListPanel.TEXT, hidden ? "Show it" : "Hide it",
                    hidden ? "Hidden now" : "Its channels out of the lists, the guide and search"));
            if (at > 0) out.add(new ListPanel.Item("do:cat.up", ListPanel.TEXT, "Move up", ""));
            if (at < every.size() - 1) out.add(new ListPanel.Item("do:cat.down", ListPanel.TEXT, "Move down", ""));
            if (at > 0) out.add(new ListPanel.Item("do:cat.top", ListPanel.TEXT, "Move to the top", ""));
            out.add(new ListPanel.Item("type:cat", ListPanel.TEXT, "Give it a name", "Shown instead of the playlist's"));
            if (Categories.renamed(tv, g)) {
                out.add(new ListPanel.Item("do:cat.unname", ListPanel.TEXT, "Its playlist's name again", g.isEmpty() ? Categories.UNNAMED : g));
            }
            out.add(new ListPanel.Item("done", ListPanel.TEXT, "Done", ""));
            return true;
        }
        if (open.startsWith("guide:")) {
            int i = number(open.substring(6));
            List<String> extra = GuidePrefs.extra(tv);
            if (i < 0 || i >= extra.size()) return false;
            out.add(new ListPanel.Item(null, ListPanel.HEADING, GuidePrefs.host(extra.get(i)), ""));
            out.add(new ListPanel.Item("do:guide.remove", ListPanel.TEXT, "Remove this guide", ""));
            out.add(new ListPanel.Item("done", ListPanel.TEXT, "Keep it", ""));
            return true;
        }
        if (open.startsWith("restore:")) {
            BackupStore.Kept k = BackupStore.kept(tv, open.substring(8));
            if (k == null) return false;
            out.add(new ListPanel.Item(null, ListPanel.HEADING, "Put back the backup made " + when(k.contents.made) + "?", ""));
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "It has " + sizes(k) + ", and every setting.", ""));
            out.add(new ListPanel.Item(null, ListPanel.NOTE, "What's set on this TV now is replaced by it.", ""));
            out.add(new ListPanel.Item("do:restore", ListPanel.TEXT, "Restore it", Parental.hasPin(tv) && !Parental.open() ? "The PIN is asked first" : ""));
            out.add(new ListPanel.Item("done", ListPanel.TEXT, "Not now", ""));
            return true;
        }
        String[] names;
        String[] about = null;
        int chosen;
        switch (open) {
            case "guide.hours":
                names = GuidePrefs.GUIDE_NAMES;
                chosen = PlayerPrefs.indexOf(GuidePrefs.GUIDE_HOURS, GuidePrefs.guideHours(tv));
                break;
            case "lists.hours":
                names = GuidePrefs.LISTS_NAMES;
                chosen = PlayerPrefs.indexOf(GuidePrefs.LISTS_HOURS, GuidePrefs.listsHours(tv));
                break;
            case "shift":
                names = new String[GuidePrefs.SHIFTS.length];
                for (int i = 0; i < names.length; i++) names[i] = GuidePrefs.shiftWords(GuidePrefs.SHIFTS[i]);
                chosen = PlayerPrefs.indexOf(GuidePrefs.SHIFTS, GuidePrefs.shiftMin(tv));
                break;
            case "mode":
                names = Parental.MODE_NAMES;
                about = Parental.MODE_ABOUT;
                chosen = Parental.hides(tv) ? 1 : 0;
                break;
            default:
                return false;
        }
        out.add(new ListPanel.Item(null, ListPanel.HEADING, title(open), ""));
        for (int i = 0; i < names.length; i++) {
            String sub = (i == chosen ? "Chosen" : "") + (about != null ? (i == chosen ? "  ·  " : "") + about[i] : "");
            out.add(new ListPanel.Item("opt:" + open + ":" + i, ListPanel.TEXT, names[i], sub));
        }
        return true;
    }

    private static String title(String key) {
        switch (key) {
            case "guide.hours": return "Update the guide";
            case "lists.hours": return "Update the playlists";
            case "shift": return "Guide time shift";
            case "mode": return "Locked channels";
            default: return "";
        }
    }

    // ---- OK on a line ---------------------------------------------------------------------------------------------

    int chosen(int section, String tag, List<ListPanel.Item> before) {
        if (tag.startsWith("set:")) {
            open = tag.substring(4);
            return 0;
        }
        if (tag.startsWith("cat:") || tag.startsWith("guide:") || tag.startsWith("restore:")) {
            open = tag;
            return 0;
        }
        if (tag.equals("done")) {
            String was = open;
            open = "";
            return lineOf(section, was);
        }
        if (tag.startsWith("opt:")) {
            int colon = tag.lastIndexOf(':');
            String key = tag.substring(4, colon);
            apply(key, number(tag.substring(colon + 1)));
            open = "";
            return lineOf(section, key);
        }
        if (tag.startsWith("tog:")) {
            switch (tag.substring(4)) {
                case "atstart": GuidePrefs.setAtStart(tv, !GuidePrefs.atStart(tv)); break;
                case "settings": Parental.setLocksSettings(tv, !Parental.locksSettings(tv)); break;
                default: break;
            }
            return indexOfTag(before, tag);
        }
        if (tag.startsWith("type:")) {
            if (tag.equals("type:guide")) host.type("m:x.guide", "A guide's address (XMLTV)", "http://", false);
            else if (tag.equals("type:cat") && open.startsWith("cat:")) {
                String g = open.substring(4);
                host.type("m:x.cat", "A name for " + Categories.label(tv, g), Categories.label(tv, g), false);
            }
            return STAY;
        }
        if (tag.startsWith("lockcat:")) {
            String g = tag.substring(8);
            Parental.lockGroup(tv, g, !Parental.lockedGroups(tv).contains(g));
            return indexOfTag(before, tag);
        }
        if (tag.startsWith("unlockch:")) {
            Parental.lockChannel(tv, tag.substring(9), false);
            return Math.max(0, Math.min(indexOfTag(before, tag), before.size() - 2));
        }
        if (tag.startsWith("do:")) return act(section, tag.substring(3), before);
        return STAY;
    }

    private void apply(String key, int i) {
        switch (key) {
            case "guide.hours":
                if (i >= 0 && i < GuidePrefs.GUIDE_HOURS.length) GuidePrefs.setGuideHours(tv, GuidePrefs.GUIDE_HOURS[i]);
                break;
            case "lists.hours":
                if (i >= 0 && i < GuidePrefs.LISTS_HOURS.length) GuidePrefs.setListsHours(tv, GuidePrefs.LISTS_HOURS[i]);
                break;
            case "shift":
                if (i >= 0 && i < GuidePrefs.SHIFTS.length && GuidePrefs.SHIFTS[i] != GuidePrefs.shiftMin(tv)) {
                    GuidePrefs.setShiftMin(tv, GuidePrefs.SHIFTS[i]);
                    readAgain();
                }
                break;
            case "mode":
                Parental.setHides(tv, i == 1);
                break;
            default:
                break;
        }
    }

    private int act(int section, String what, List<ListPanel.Item> before) {
        String g = open.startsWith("cat:") ? open.substring(4) : null;
        Library lib = Library.get(tv);
        switch (what) {
            case "cat.hide":
            case "cat.show":
                if (g == null) return STAY;
                Categories.setHidden(tv, g, what.equals("cat.hide"));
                return 1;
            case "cat.up":
            case "cat.down":
            case "cat.top": {
                if (g == null) return STAY;
                Categories.move(tv, lib.groups(), g, what.equals("cat.up") ? -1 : what.equals("cat.down") ? 1 : 0);
                // The remote stays on the same move where it is still offered, to move it again.
                keep = "do:" + what;
                return 1;
            }
            case "cat.unname":
                if (g != null) Categories.rename(tv, g, "");
                return 1;
            case "cat.showall":
                Categories.showAll(tv);
                return 0;
            case "cat.reorder":
                Categories.resetOrder(tv);
                return 0;
            case "guide.remove":
                if (open.startsWith("guide:")) {
                    GuidePrefs.removeExtra(tv, number(open.substring(6)));
                    open = "";
                    readAgain();
                }
                return 0;
            case "pin.set":
                PinScreen.choose(tv, "Choose a PIN", pin -> {
                    Parental.setPin(tv, pin);
                    toast("Parental control is on");
                });
                return STAY;
            case "pin.enter":
                PinScreen.check(tv, "Parental control", () -> { });
                return STAY;
            case "pin.change":
                PinScreen.choose(tv, "Choose a new PIN", pin -> {
                    Parental.setPin(tv, pin);
                    toast("PIN changed");
                });
                return STAY;
            case "pin.off":
                Parental.clearPin(tv);
                toast("Parental control is off");
                return 0;
            case "backup":
                backUpNow();
                return indexOfTag(before, "do:backup");
            case "pick":
                if (!BackupStore.pick(tv)) {
                    toast("This TV has no file chooser. Use this TV's last backup instead.");
                }
                return STAY;
            case "restore": {
                String which = open.startsWith("restore:") ? open.substring(8) : "";
                BackupStore.Kept k = BackupStore.kept(tv, which);
                if (k == null) return 0;
                PinScreen.check(tv, "Restoring needs the PIN", () -> {
                    BackupStore.restore(tv, k);
                    open = "";
                    toast("Restored: starting again with the backup's settings");
                    Library.get(tv).refresh(null);
                    tv.restartLook(SettingsScreen.ABOUT + (MoreSettings.BACKUP - MoreSettings.BASE));
                });
                return STAY;
            }
            default:
                return STAY;
        }
    }

    private void backUpNow() {
        try {
            String where = BackupStore.backUp(tv, appVersion());
            savedWhere = where.isEmpty() ? "on this TV" : where;
            toast(where.isEmpty() ? "Backed up on this TV (no room for a file people can reach)" : "Backed up: " + where);
        } catch (IOException | RuntimeException e) {
            toast("That backup couldn't be made");
        }
    }

    /** The playlists and guide read again for a change to the guides, so it shows now. */
    private void readAgain() {
        Library.get(tv).refresh(null);
        toast("Reading your playlists and guide again…");
    }

    // ---- typed in -------------------------------------------------------------------------------------------------

    static boolean ownsField(String field) {
        return field.startsWith("x.");
    }

    String value(String field) {
        if (field.equals("x.guide")) return "http://";
        if (field.equals("x.cat") && open.startsWith("cat:")) return Categories.label(tv, open.substring(4));
        return "";
    }

    void typed(String field, String text) {
        if (field.equals("x.guide")) {
            if (text.isEmpty() || text.equals("http://") || text.equals("https://")) return;
            if (GuidePrefs.addExtra(tv, text)) readAgain();
            else toast("That isn't a guide's address to read: it starts http:// or https://, once");
        } else if (field.equals("x.cat") && open.startsWith("cat:")) {
            Categories.rename(tv, open.substring(4), text);
        }
    }

    // ---- the file chooser's answer ---------------------------------------------------------------------------------

    /** Android's file chooser answered (TvActivity): a backup chosen opens to be confirmed; anything else is said. */
    static void picked(TvActivity tv, String said) {
        if (said.isEmpty()) openNext = "restore:" + BackupStore.PICKED;
        else Toast.makeText(tv, said, Toast.LENGTH_LONG).show();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** The line a setting (or a category, guide or restore) is on in its section's own lines. */
    private int lineOf(int section, String key) {
        String was = open;
        open = "";
        List<ListPanel.Item> items = new ArrayList<>();
        fill(section, items);
        open = was;
        int i = indexOfTag(items, "set:" + key);
        if (i < 0) i = indexOfTag(items, key);
        return Math.max(0, i);
    }

    private static int indexOfTag(List<ListPanel.Item> items, String tag) {
        for (int i = 0; i < items.size(); i++) if (tag.equals(items.get(i).tag)) return i;
        return STAY;
    }

    private Map<String, Integer> counts(Library lib) {
        Map<String, Integer> out = new HashMap<>();
        for (Channel c : lib.every()) {
            Integer n = out.get(c.group);
            out.put(c.group, n == null ? 1 : n + 1);
        }
        return out;
    }

    private static String restoreName(String which) {
        switch (which) {
            case BackupStore.PICKED: return "The file you chose";
            default: return "This TV's last backup";
        }
    }

    private static String sizes(BackupStore.Kept k) {
        int[] n = Backup.counts(k.contents);
        return plural(n[0], "playlist") + ", " + plural(n[1], "favourite") + ", " + plural(n[2], "reminder");
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    private static String when(long t) {
        return t <= 0 ? "some time ago" : new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK).format(new Date(t));
    }

    private static String ordinal(int n) {
        int t = n % 100;
        if (t >= 11 && t <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String appVersion() {
        try {
            return tv.getPackageManager().getPackageInfo(tv.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) {
            return "";
        }
    }

    private void toast(String s) {
        Toast.makeText(tv, s, Toast.LENGTH_LONG).show();
    }

    private static ListPanel.Item line(String key, String title, String sub) {
        return new ListPanel.Item("set:" + key, ListPanel.TEXT, title, sub);
    }

    private static ListPanel.Item toggle(String key, String title, boolean on, String about) {
        return new ListPanel.Item("tog:" + key, ListPanel.TEXT, title, (on ? "On" : "Off") + "  ·  " + about);
    }
}
