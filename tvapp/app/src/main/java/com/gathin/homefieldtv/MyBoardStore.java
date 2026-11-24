package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.broadcast.BoardSpec;
import com.gathin.broadcast.MyBoards;
import com.gathin.homefieldtv.sync.SyncItems;
import com.gathin.homefieldtv.sync.TvSync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * My scoreboards in Homefield TV: the family's saved boards (MyBoards), kept as "myboard" items in the app's sync store
 * (SyncItems), as the phone keeps them in its own: so a board saved on the phone or on the TV launcher is here after the
 * next sync with the launcher on this TV, and one saved here goes to them (phone/tvhome-phone/SYNC.md). A board taken
 * away is a tombstone, so it goes everywhere. Which one is in use is this TV's own (BoardPrefs.customId).
 */
final class MyBoardStore {
    private MyBoardStore() {}

    /** Every saved board, newest first. */
    static List<MyBoards.Saved> all(Context c) {
        List<MyBoards.Saved> out = new ArrayList<>();
        for (JSONObject it : SyncItems.items(c, MyBoards.TYPE)) {
            if (it.optBoolean("deleted")) continue;
            MyBoards.Saved s = MyBoards.read(it.optString("key"), it.optJSONObject("data"));
            if (s != null) out.add(s);
        }
        MyBoards.sort(out);
        return out;
    }

    static MyBoards.Saved get(Context c, String id) {
        if (!MyBoards.validId(id)) return null;
        return read(id, SyncItems.raw(c, MyBoards.TYPE, id));
    }

    /** A saved board from its item as kept (null: none, removed, or unreadable). */
    private static MyBoards.Saved read(String id, String raw) {
        if (raw == null) return null;
        try {
            JSONObject it = new JSONObject(raw);
            return it.optBoolean("deleted") ? null : MyBoards.read(id, it.optJSONObject("data"));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Saved here (a new one, or one changed); false when My scoreboards is full. Synced a few seconds later. */
    static boolean save(Context c, MyBoards.Saved s) {
        if (get(c, s.id) == null && all(c).size() >= MyBoards.MAX) return false;
        SyncItems.put(c, MyBoards.TYPE, s.id, s.data());
        TvSync.changed();
        return true;
    }

    /** A new board saved under a free name: its id, or null when My scoreboards is full. */
    static String add(Context c, String name, BoardSpec spec) {
        String id = MyBoards.newId();
        String n = MyBoards.freeName(name, MyBoards.names(all(c)));
        return save(c, new MyBoards.Saved(id, n, spec.copy(), System.currentTimeMillis())) ? id : null;
    }

    /** A board saved with its changes (its name kept free of the others'). */
    static void update(Context c, String id, BoardSpec spec) {
        List<String> taken = MyBoards.names(all(c));
        MyBoards.Saved was = get(c, id);
        if (was != null) taken.remove(was.name);
        save(c, new MyBoards.Saved(id, MyBoards.freeName(spec.name, taken), spec.copy(), System.currentTimeMillis()));
    }

    /** A saved board renamed (a name another board has gets " 2" after it); its new name, or null for none such. */
    static String rename(Context c, String id, String name) {
        MyBoards.Saved s = get(c, id);
        if (s == null) return null;
        List<String> taken = MyBoards.names(all(c));
        taken.remove(s.name);
        MyBoards.Saved n = new MyBoards.Saved(id, MyBoards.freeName(name, taken), s.spec, System.currentTimeMillis());
        save(c, n);
        return n.name;
    }

    /** A copy of a saved board ("… copy"): its id, or null. */
    static String duplicate(Context c, String id) {
        MyBoards.Saved s = get(c, id);
        if (s == null) return null;
        String nid = MyBoards.newId();
        String n = MyBoards.copyName(s.name, MyBoards.names(all(c)));
        return save(c, new MyBoards.Saved(nid, n, s.spec.copy(), System.currentTimeMillis())) ? nid : null;
    }

    /** Taken away everywhere (a tombstone); the board in use goes back to the look as chosen when it was this one. */
    static boolean delete(Context c, String id) {
        if (get(c, id) == null) return false;
        SyncItems.delete(c, MyBoards.TYPE, id);
        if (id.equals(BoardPrefs.customId(c))) BoardPrefs.setCustom(c, null);
        TvSync.changed();
        return true;
    }

    private static String cachedRaw;
    private static BoardSpec cached;

    /**
     * The board in use, or null for the look as chosen (none chosen, or taken away elsewhere since): one look in the store,
     * parsed again only when it changed (a sync may have brought a newer one), the same object otherwise.
     */
    static synchronized BoardSpec inUse(Context c) {
        String id = BoardPrefs.customId(c);
        if (id == null) return null;
        String raw = SyncItems.raw(c, MyBoards.TYPE, id);
        if (raw == null) return null;
        if (!raw.equals(cachedRaw)) {
            MyBoards.Saved s = read(id, raw);
            cached = s == null ? null : s.spec;
            cachedRaw = raw;
        }
        return cached;
    }
}
