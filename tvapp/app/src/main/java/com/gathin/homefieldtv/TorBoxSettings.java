package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.TorBox;

import java.io.IOException;
import java.util.List;

/**
 * Settings → Playlists → TorBox, as the phone's: the family's own TorBox account as a source for movies and TV shows
 * beside the playlists, on or off, its copies before the playlists' or after them, and its API key (kept on this TV and
 * never shown again once saved; typed with the remote's keyboard as dots, or sent from a computer over adb), checked
 * with TorBox when saved: how many films and episodes it has ready, or why not.
 */
final class TorBoxSettings {
    private TorBoxSettings() {}

    /** The keyboard's field for the key (never filled in with the one saved). */
    static final String FIELD = "torbox";

    /** What the last check said ("" before one), and whether one is under way. */
    private static String said = "";
    private static boolean checking;

    /** The page's rows: the switches, the key, Check and Forget once a key is saved. */
    static void rows(TvActivity tv, List<RowList.Row> out) {
        boolean has = TorBox.hasKey(tv);
        out.add(RowList.Row.toggle("on", "Use TorBox for movies and TV shows", "", TorBox.wanted(tv)));
        out.add(RowList.Row.toggle("first", "Offer TorBox's copies first", TorBox.first(tv) ? "Before your playlists' copies"
                : "After your playlists' copies", TorBox.first(tv)));
        out.add(RowList.Row.item("type:" + FIELD, "API key", has ? "Saved  ·  OK to type a new one" : "On torbox.app: Settings, then API key"));
        if (has) {
            out.add(RowList.Row.item("check", "Check with TorBox",
                    checking ? "Checking with TorBox…" : said.isEmpty() ? "How many movies and episodes it has ready" : said));
            out.add(RowList.Row.item("forget", "Forget the key", "TorBox's copies go until a key is saved again"));
        }
        out.add(RowList.Row.note("Movies and TV shows already in your own TorBox play here beside your playlists' copies: its "
                + "finished downloads, matched by their names. Nothing is searched for or added."));
        out.add(RowList.Row.note("The key stays on this TV and isn't shown again once saved. From a computer: adb shell content "
                + "insert --uri content://com.gathin.homefieldtv.setup/torbox --bind key:s:…"));
    }

    /** A line chosen: changed (true), or not one of the section's (false). changed runs when a check has answered. */
    static boolean chosen(TvActivity tv, String tag, Runnable changed) {
        switch (tag) {
            case "on":
                TorBox.setOn(tv, !TorBox.wanted(tv));
                // Its own titles in the rows, or gone from them.
                Catalog.get(tv).ownTitles();
                return true;
            case "first":
                TorBox.setFirst(tv, !TorBox.first(tv));
                return true;
            case "check":
                check(tv, changed);
                return true;
            case "forget":
                TorBox.forget(tv);
                said = "";
                Catalog.get(tv).ownTitles();
                return true;
            default:
                return false;
        }
    }

    /** A key typed in: kept and checked with TorBox (none typed keeps the one saved). */
    static void typed(TvActivity tv, String key, Runnable changed) {
        if (key == null || key.trim().isEmpty()) return;
        TorBox.setKey(tv, key);
        check(tv, changed);
    }

    private static void check(TvActivity tv, Runnable changed) {
        if (checking || !TorBox.hasKey(tv)) return;
        checking = true;
        said = "";
        Context app = tv.getApplicationContext();
        Exec.io(() -> {
            String words;
            try {
                words = TorBox.checked(TorBox.check(app));
            } catch (IOException e) {
                // TorBox's own words, or why it couldn't be asked: never the address or the key.
                words = e.getMessage();
            } catch (RuntimeException e) {
                words = null;
            }
            String w = words == null || words.isEmpty() ? "TorBox couldn't be asked just now" : words;
            Exec.main(() -> {
                checking = false;
                said = w;
                changed.run();
                // The library just read: its own titles into the rows.
                Catalog.get(app).ownTitles();
            });
        });
    }

    /** Previews: what a check said, without asking TorBox. */
    static void previewSaid(String words) {
        checking = false;
        said = words == null ? "" : words;
    }
}
