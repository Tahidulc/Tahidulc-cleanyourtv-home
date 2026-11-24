package com.gathin.homefieldtv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.gathin.homefieldtv.engine.CardsSnapshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Homefield TV's channel cards for the TV launcher on this TV (its dock's row when Homefield TV is focused), read-only:
 * content://com.gathin.homefieldtv.cards/cards (or /cards/recent, /cards/favourite, /cards/sport). A row a card: the
 * recent channels newest first, the favourites, then the sport on now, each channel with its key (for PlayRequest), name,
 * number, logo and logo colours, and what is on now and next (title, times, a few lines, picture). Only an app signed
 * with Homefield TV's own key may read it (the signature permission READ_CHANNEL_CARDS, which the launcher declares too).
 *
 * <p>Answered at once from what ChannelCards last made (kept in memory, else read back from its small file): never the
 * playlists or the guide, and nothing else of the app is started for it.
 */
public final class ChannelCardsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.gathin.homefieldtv.cards";
    static final String FILE = "channel_cards.json";
    public static final String[] COLUMNS = {"section", "position", "channel_key", "channel_name", "channel_number", "logo_url",
            "logo_colour", "logo_colour_foot", "now_title", "now_start", "now_end", "now_picture", "now_description", "next_title",
            "next_start", "next_end", "next_picture", "written_at"};

    /** The snapshot last made or read, and its file's time then. */
    private static volatile CardsSnapshot kept;
    private static volatile long keptMod = -1;

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context c = getContext();
        List<String> path = uri.getPathSegments();
        if (c == null || path.isEmpty() || !"cards".equals(path.get(0))) return null;
        String only = path.size() > 1 ? path.get(1) : null;
        return cursor(kept(c), System.currentTimeMillis(), only);
    }

    /** The cards at now as rows (all of them, or one section's). */
    static MatrixCursor cursor(CardsSnapshot s, long now, String only) {
        MatrixCursor m = new MatrixCursor(COLUMNS);
        for (CardsSnapshot.Row r : s.rows(now)) {
            if (only != null && !only.equals(r.section)) continue;
            CardsSnapshot.Card k = r.card;
            CardsSnapshot.Prog n = r.now, x = r.next;
            m.addRow(new Object[]{r.section, r.position, k.key, k.name, k.number, k.logo,
                    k.colours == null ? 0 : k.colours[0], k.colours == null ? 0 : k.colours[1],
                    n == null ? null : n.title, n == null ? 0L : n.start, n == null ? 0L : n.end, n == null ? null : n.picture,
                    n == null ? null : n.desc,
                    x == null ? null : x.title, x == null ? 0L : x.start, x == null ? 0L : x.end, x == null ? null : x.picture, s.at});
        }
        return m;
    }

    /** The cards as last made: in memory, or read back from the file once it has changed (a few kB, quick). */
    static CardsSnapshot kept(Context c) {
        File f = file(c);
        long mod = f.lastModified();
        CardsSnapshot s = kept;
        if (s != null && mod == keptMod) return s;
        synchronized (ChannelCardsProvider.class) {
            if (kept != null && mod == keptMod) return kept;
            s = mod == 0 ? CardsSnapshot.EMPTY : CardsSnapshot.fromJson(read(f));
            kept = s;
            keptMod = mod;
            return s;
        }
    }

    /** ChannelCards made new cards and wrote them (the file's time now): served from memory. */
    static void put(CardsSnapshot s, long mod) {
        synchronized (ChannelCardsProvider.class) {
            kept = s;
            keptMod = mod;
        }
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 2 << 20)];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".card";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
