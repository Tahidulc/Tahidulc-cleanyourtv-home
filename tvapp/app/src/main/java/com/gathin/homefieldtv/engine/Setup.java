package com.gathin.homefieldtv.engine;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The family's playlist added from a computer, so a long address or login need not be typed with the remote. Only
 * the adb shell can reach it (the manifest asks for WRITE_SECURE_SETTINGS, which no ordinary app holds):
 *
 * <pre>
 * adb shell content insert --uri content://com.gathin.homefieldtv.setup/xtream \
 *     --bind name:s:Home --bind server:s:http://example.com:8080 --bind user:s:NAME --bind pass:s:PASSWORD
 * adb shell content insert --uri content://com.gathin.homefieldtv.setup/m3u --bind name:s:Home --bind url:s:'http://…'
 * adb shell content query --uri content://com.gathin.homefieldtv.setup/playlists
 * adb shell content delete --uri content://com.gathin.homefieldtv.setup/playlists/p1
 * adb shell content insert --uri content://com.gathin.homefieldtv.setup/torbox --bind key:s:KEY
 * adb shell content delete --uri content://com.gathin.homefieldtv.setup/torbox
 * </pre>
 *
 * A playlist is kept only when it gives live channels, as in Settings. The answer is the channel count or why it didn't
 * work; nothing here writes the address or the login anywhere but the app's own private storage. TorBox's API key is
 * kept and checked with TorBox: the answer is how many films and episodes it has ready, or TorBox's word why not.
 */
public final class Setup extends ContentProvider {
    @Override public boolean onCreate() {
        return true;
    }

    @Override public Uri insert(Uri uri, ContentValues v) {
        Context c = getContext();
        if (c == null || v == null) throw new IllegalArgumentException("Nothing to add");
        String kind = uri.getLastPathSegment();
        if ("torbox".equals(kind)) return torbox(c, uri, v.getAsString("key"));
        Source s;
        if ("xtream".equals(kind)) {
            s = new Source(Source.XTREAM, null, v.getAsString("guide"), v.getAsString("server"), v.getAsString("user"), v.getAsString("pass"));
            if (!s.usable()) throw new IllegalArgumentException("Give server, user and pass");
        } else if ("m3u".equals(kind)) {
            s = new Source(Source.M3U, v.getAsString("url"), v.getAsString("guide"), null, null, null);
            if (!s.usable()) throw new IllegalArgumentException("Give the playlist's url");
        } else {
            throw new IllegalArgumentException("Use …/xtream, …/m3u or …/torbox");
        }
        CountDownLatch done = new CountDownLatch(1);
        int[] count = {0};
        String[] error = {null};
        Library.get(c).test(null, v.getAsString("name"), s, (n, e) -> {
            count[0] = n;
            error[0] = e;
            done.countDown();
        });
        try {
            if (!done.await(180, TimeUnit.SECONDS)) throw new IllegalStateException("Still loading: look on the TV");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        }
        if (error[0] != null) throw new IllegalStateException(error[0]);
        return new Uri.Builder().scheme("content").authority(uri.getAuthority()).appendPath("added")
                .appendPath(count[0] + "-channels").build();
    }

    /** TorBox's API key from a computer: kept and checked with TorBox (on adb's own thread, never the main one). */
    private static Uri torbox(Context c, Uri uri, String key) {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Give key");
        TorBox.setKey(c, key);
        int n;
        try {
            n = TorBox.check(c);
        } catch (java.io.IOException e) {
            // TorBox's own words: never the address or the key.
            throw new IllegalStateException(e.getMessage());
        }
        return new Uri.Builder().scheme("content").authority(uri.getAuthority()).appendPath("torbox").appendPath(n + "-ready").build();
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context c = getContext();
        MatrixCursor out = new MatrixCursor(new String[]{"id", "name", "kind", "expiry"});
        if (c == null) return out;
        List<Sources.Entry> all = Sources.list(c);
        for (Sources.Entry e : all) {
            out.addRow(new Object[]{e.id, e.name, e.source.describe(), Sources.expiry(Sources.info(c, e.id), System.currentTimeMillis())});
        }
        return out;
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        Context c = getContext();
        if (c == null) return 0;
        String last = uri.getLastPathSegment();
        if ("torbox".equals(last)) {
            boolean had = TorBox.hasKey(c);
            TorBox.forget(c);
            return had ? 1 : 0;
        }
        if ("all".equals(last)) {
            int n = Sources.list(c).size();
            Library.get(c).clear();
            Resume.clear(c);
            Copies.clear(c);
            return n;
        }
        if (Sources.get(c, last) == null) return 0;
        Sources.remove(c, last);
        Library.get(c).refresh(null);
        return 1;
    }

    @Override public String getType(Uri uri) {
        return null;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
