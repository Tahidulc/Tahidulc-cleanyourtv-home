package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * The family's own video for the matchday intro, chosen on this phone (PickIntroActivity) and kept in the app's own
 * files: it plays in place of the competition's built-in film, without its sound, before the Whitebrook Road Sports
 * ending, and is sent to the paired TVs.
 */
public final class IntroVideo {
    private IntroVideo() {}

    private static final String PREFS = "intro_video";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static File file(Context c) {
        return new File(new File(c.getApplicationContext().getFilesDir(), "intro"), "own.mp4");
    }

    static boolean has(Context c) {
        File f = file(c);
        return f.isFile() && f.length() > 0;
    }

    static boolean useOwn(Context c) {
        return has(c) && prefs(c).getBoolean("use", true);
    }

    static void setUseOwn(Context c, boolean on) {
        prefs(c).edit().putBoolean("use", on).apply();
    }

    static long durationMs(Context c) {
        return prefs(c).getLong("ms", 0);
    }

    static String name(Context c) {
        return prefs(c).getString("name", "your video");
    }

    static String words(Context c) {
        long s = Math.round(durationMs(c) / 1000.0);
        return name(c) + String.format(Locale.ROOT, " (%d:%02d)", s / 60, s % 60);
    }

    /** The chosen video copied in (off the main thread): null when kept, else why not. */
    static String take(Context c, Uri uri) {
        File f = file(c), dir = f.getParentFile();
        if (dir == null || !dir.isDirectory() && !dir.mkdirs()) return "Couldn't make room for it";
        File part = new File(dir, "own.part");
        try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(part)) {
            if (in == null) return "Couldn't open that video";
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            return "Couldn't read that video";
        }
        long ms = playable(part);
        if (ms < 1000) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            return "That video can't be played here";
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        if (!part.renameTo(f)) return "Couldn't keep it";
        prefs(c).edit().putLong("ms", ms).putString("name", cleanName(displayName(c, uri))).putBoolean("use", true).apply();
        return null;
    }

    /** Back to the built-in film: the copy goes. */
    static void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
        prefs(c).edit().clear().apply();
    }

    private static long playable(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getPath());
            if (!"yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))) return 0;
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? 0 : Long.parseLong(d.trim());
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
                // gone
            }
        }
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor q = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (q != null && q.moveToFirst()) return q.getString(0);
        } catch (Exception ignored) {
            // the plain name, then
        }
        return uri.getLastPathSegment();
    }

    /** Pure: the file's name as shown: no folders or extension, plain characters, 40 at most. */
    static String cleanName(String name) {
        String s = name == null ? "" : name.trim();
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        s = s.replaceAll("[^\\p{L}\\p{N} _().-]", " ").replaceAll("\\s+", " ").trim();
        if (s.length() > 40) s = s.substring(0, 40).trim();
        return s.isEmpty() ? "your video" : s;
    }
}
