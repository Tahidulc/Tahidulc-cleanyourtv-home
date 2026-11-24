package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The logos the family picked for their channels (the logo picker: one from a pack in the catalogue, or one made in
 * the app), each by the channel's lasting key (Hidden.key: the same across refreshes), in the app's own preferences;
 * a made logo as a PNG in the app's own files. Library lays them over the playlist's own as it loads its channels, so
 * every list, the guide and the player show them.
 */
final class ChannelLogos {
    private ChannelLogos() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("channel_logos", Context.MODE_PRIVATE);
    }

    /** The logo picked for a channel (an address, or file:// for one made here), or null for the playlist's own. */
    static String picked(Context c, Channel ch) {
        return ch == null ? null : prefs(c).getString(Hidden.key(ch), null);
    }

    /**
     * The channels with their picked logos laid over the playlist's (the same list when none is picked); own gets the
     * playlist's logo of each one changed (by its key), so the channels' cache keeps the playlist's own.
     */
    static List<Channel> apply(Context c, List<Channel> list, java.util.Map<String, String> own) {
        java.util.Map<String, ?> all = prefs(c).getAll();
        if (all.isEmpty()) return list;
        List<Channel> out = new ArrayList<>(list.size());
        for (Channel ch : list) {
            Object v = all.get(Hidden.key(ch));
            if (!(v instanceof String) || ((String) v).isEmpty() || v.equals(ch.logo)) {
                out.add(ch);
                continue;
            }
            own.put(ch.key, ch.logo);
            Channel n = new Channel(ch.key, ch.name, ch.group, (String) v, ch.url, ch.tvgId, ch.userAgent, ch.referrer, ch.number);
            n.alternates.addAll(ch.alternates);
            out.add(n);
        }
        return out;
    }

    /** A pack's logo picked for a channel (an https address only). */
    static void pick(Context c, Channel ch, String url) {
        if (ch == null || url == null || !url.startsWith("https://")) return;
        prefs(c).edit().putString(Hidden.key(ch), url).apply();
        Library.get(c).logosChanged();
    }

    /** A logo made here saved (PNG, in the app's own files) and picked for the channel. Off the main thread. */
    static void pickMade(Context c, Channel ch, Bitmap logo) throws IOException {
        File dir = new File(c.getFilesDir(), "logos");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("no folder for logos");
        String name = Integer.toHexString(Hidden.key(ch).hashCode()) + "-" + Long.toString(System.currentTimeMillis(), 36) + ".png";
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            logo.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        String was = picked(c, ch);
        prefs(c).edit().putString(Hidden.key(ch), "file://" + f.getAbsolutePath()).apply();
        // The one made before for it is let go.
        if (was != null && was.startsWith("file://") && was.toLowerCase(Locale.ROOT).endsWith(".png")) {
            File old = new File(was.substring("file://".length()));
            if (old.getParentFile() != null && old.getParentFile().equals(dir) && !old.equals(f)) {
                //noinspection ResultOfMethodCallIgnored
                old.delete();
            }
        }
        com.gathin.tvhomephone.App.main.post(() -> Library.get(c).logosChanged());
    }

    /** The playlist's own logo back. */
    static void reset(Context c, Channel ch) {
        if (ch == null) return;
        prefs(c).edit().remove(Hidden.key(ch)).apply();
        Library.get(c).logosChanged();
    }
}
