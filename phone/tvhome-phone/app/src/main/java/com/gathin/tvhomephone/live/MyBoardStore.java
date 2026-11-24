package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.broadcast.BoardSpec;
import com.gathin.broadcast.MyBoards;
import com.gathin.tvhomephone.SyncStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * My scoreboards on the phone and tablet: the family's saved boards (MyBoards) kept in SyncStore as "myboard" items, so
 * they go to every paired TV and come back from them with the rest of what is synced (SYNC.md). Reads are of the app's
 * own preferences (in memory); the board in use is ScoreBoard's "custom" (its id), read through here.
 */
final class MyBoardStore {
    private MyBoardStore() {
    }

    /** Every saved board, newest first. */
    static List<MyBoards.Saved> all(Context c) {
        List<MyBoards.Saved> out = new ArrayList<>();
        for (JSONObject it : SyncStore.items(c, MyBoards.TYPE)) {
            if (it.optBoolean("deleted")) continue;
            MyBoards.Saved s = MyBoards.read(it.optString("key"), it.optJSONObject("data"));
            if (s != null) out.add(s);
        }
        MyBoards.sort(out);
        return out;
    }

    /** One saved board, or null. */
    static MyBoards.Saved get(Context c, String id) {
        if (!MyBoards.validId(id)) return null;
        return MyBoards.read(id, SyncStore.get(c, MyBoards.TYPE, id));
    }

    /** Saved (a new one, or one changed); false when My scoreboards is full. */
    static boolean save(Context c, MyBoards.Saved s) {
        if (get(c, s.id) == null && all(c).size() >= MyBoards.MAX) return false;
        SyncStore.put(c, MyBoards.TYPE, s.id, s.data());
        cached = null;
        return true;
    }

    /** A new board saved under a free name; its id, or null when full. */
    static String add(Context c, String name, BoardSpec spec) {
        String id = MyBoards.newId();
        String n = MyBoards.freeName(name, MyBoards.names(all(c)));
        return save(c, new MyBoards.Saved(id, n, spec.copy(), System.currentTimeMillis())) ? id : null;
    }

    static void rename(Context c, String id, String name) {
        MyBoards.Saved s = get(c, id);
        if (s == null) return;
        List<String> taken = MyBoards.names(all(c));
        taken.remove(s.name);
        save(c, new MyBoards.Saved(id, MyBoards.freeName(name, taken), s.spec, System.currentTimeMillis()));
    }

    /** A copy beside it; the copy's id, or null. */
    static String duplicate(Context c, String id) {
        MyBoards.Saved s = get(c, id);
        if (s == null) return null;
        String n = MyBoards.copyName(s.name, MyBoards.names(all(c)));
        String nid = MyBoards.newId();
        return save(c, new MyBoards.Saved(nid, n, s.spec.copy(), System.currentTimeMillis())) ? nid : null;
    }

    static void delete(Context c, String id) {
        if (get(c, id) == null) return;
        SyncStore.delete(c, MyBoards.TYPE, id);
        if (id.equals(ScoreBoard.customId(c))) ScoreBoard.setCustom(c, null);
        cached = null;
    }

    private static String cachedId;
    private static long cachedAt = -1;
    private static BoardSpec cached;

    /** The board in use (ScoreBoard's custom), or null for the chosen look; parsed once for each change. */
    static synchronized BoardSpec inUse(Context c) {
        String id = ScoreBoard.customId(c);
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
