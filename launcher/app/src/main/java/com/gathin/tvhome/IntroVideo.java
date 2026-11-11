package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * The family's own video for the matchday intro, in place of the built-in film: sent from the phone app over the home
 * network (DeliveryLink's /intro-video, a paired phone only) and kept in this app's own files. It plays before the
 * trophy and the Matchday card (ClassicIntro), muted under the intro's music; one shorter than 40 seconds starts part
 * way into the music so that it ends on the flash into the trophy. The Scoreboard card switches between it and the
 * built-in film.
 */
final class IntroVideo {
    private IntroVideo() { }

    /** The largest video taken: a few minutes of HD. */
    static final long MAX_BYTES = 600L * 1024 * 1024;
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

    /** Whether the intro plays the family's video (one is here, and the card has not switched back to the built-in film). */
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

    /** "your video (0:52)", for the card. */
    static String words(Context c) {
        long s = Math.round(durationMs(c) / 1000.0);
        return name(c) + String.format(Locale.ROOT, " (%d:%02d)", s / 60, s % 60);
    }

    /**
     * A video from a paired phone: length bytes from in, kept once Android can play it (it has a picture and lasts a
     * second or more). Null when kept, else why not. The link's thread.
     */
    static String receive(Context c, InputStream in, long length, String name) {
        File f = file(c), dir = f.getParentFile();
        if (dir == null || !dir.isDirectory() && !dir.mkdirs()) return "couldn't make room for it";
        if (dir.getUsableSpace() < length + 64L * 1024 * 1024) return "not enough space on the TV";
        File part = new File(dir, "own.part");
        byte[] buf = new byte[64 * 1024];
        long got = 0;
        try (OutputStream out = new FileOutputStream(part)) {
            while (got < length) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, length - got));
                if (n < 0) break;
                out.write(buf, 0, n);
                got += n;
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            return "the transfer stopped part way";
        }
        if (got < length) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            return "the transfer stopped part way";
        }
        long ms = playable(part);
        if (ms < 1000) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            return "the TV can't play that video";
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        if (!part.renameTo(f)) return "couldn't keep it";
        prefs(c).edit().putLong("ms", ms).putString("name", cleanName(name)).putBoolean("use", true).apply();
        android.util.Log.i("Sport", "matchday intro: the family's own video kept, " + ms + " ms");
        return null;
    }

    /** The phone switched back to the built-in film: the video goes. */
    static void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
        prefs(c).edit().clear().apply();
    }

    /** Its length in ms when it has a picture Android can read, else 0. */
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

    /** Pure: the file's name as the card shows it: no folders or extension, plain characters, 40 at most. */
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
