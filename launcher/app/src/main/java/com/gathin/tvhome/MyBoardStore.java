package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.broadcast.BoardSpec;
import com.gathin.broadcast.MyBoards;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * My scoreboards on the TV: the family's saved boards (MyBoards), each its data as a "myboard" sync item carries it,
 * by id, in the launcher's own private preferences. SyncHub gives them to the phones and the house's other TVs and
 * takes theirs in (SYNC.md), so a board saved on the phone is here too. The board in use is BoardCover's "custom".
 */
final class MyBoardStore {
    private MyBoardStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("my_boards", Context.MODE_PRIVATE);
    }

    /** Every saved board, newest first. */
    static List<MyBoards.Saved> all(Context c) {
        List<MyBoards.Saved> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (!(e.getValue() instanceof String)) continue;
            MyBoards.Saved s = read(e.getKey(), (String) e.getValue());
            if (s != null) out.add(s);
        }
        MyBoards.sort(out);
        return out;
    }

    private static MyBoards.Saved read(String id, String raw) {
        try {
            return MyBoards.read(id, new JSONObject(raw));
        } catch (Exception e) {
            return null;
        }
    }

    static MyBoards.Saved get(Context c, String id) {
        if (!MyBoards.validId(id)) return null;
        String raw = prefs(c).getString(id, null);
        return raw == null ? null : read(id, raw);
    }

    /** Saved here (a new one, or one changed); false when My scoreboards is full. local: changed on this TV (synced on). */
    static boolean save(Context c, MyBoards.Saved s, boolean local) {
        if (!prefs(c).contains(s.id) && prefs(c).getAll().size() >= MyBoards.MAX) return false;
        prefs(c).edit().putString(s.id, s.data().toString()).apply();
        cached = null;
        if (local) SyncHub.changed(c);
        return true;
    }

    static String add(Context c, String name, BoardSpec spec) {
        String id = MyBoards.newId();
        String n = MyBoards.freeName(name, MyBoards.names(all(c)));
        return save(c, new MyBoards.Saved(id, n, spec.copy(), System.currentTimeMillis()), true) ? id : null;
    }

    static void rename(Context c, String id, String name) {
        MyBoards.Saved s = get(c, id);
        if (s == null) return;
        List<String> taken = MyBoards.names(all(c));
        taken.remove(s.name);
        save(c, new MyBoards.Saved(id, MyBoards.freeName(name, taken), s.spec, System.currentTimeMillis()), true);
    }

    static String duplicate(Context c, String id) {
        MyBoards.Saved s = get(c, id);
        if (s == null) return null;
        String nid = MyBoards.newId();
        String n = MyBoards.copyName(s.name, MyBoards.names(all(c)));
        return save(c, new MyBoards.Saved(nid, n, s.spec.copy(), System.currentTimeMillis()), true) ? nid : null;
    }

    /** Let go (local: on this TV, so a removal goes to the others; else a removal that came from them). */
    static boolean delete(Context c, String id, boolean local) {
        if (!prefs(c).contains(id)) return false;
        prefs(c).edit().remove(id).apply();
        if (id.equals(BoardCover.customId(c))) BoardCover.setCustom(c, null);
        cached = null;
        if (local) SyncHub.changed(c);
        return true;
    }

    /** Every board's data by id, for SyncHub's look at what is here. */
    static Map<String, ?> raw(Context c) {
        return prefs(c).getAll();
    }

    private static String cachedId;
    private static long cachedAt = -1;
    private static BoardSpec cached;

    /** The board in use, or null for the chosen look; parsed once for each change. */
    static synchronized BoardSpec inUse(Context c) {
        String id = BoardCover.customId(c);
        if (id == null || id.isEmpty()) return null;
        MyBoards.Saved s = get(c, id);
        if (s == null) return null;
        if (cached == null || !id.equals(cachedId) || cachedAt != s.at) {
            cached = s.spec;
            cachedId = id;
            cachedAt = s.at;
        }
        return cached;
    }
}
