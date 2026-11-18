package com.gathin.homefieldtv.backups;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.Parental;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Where Settings → Backup keeps its backups and finds them again. Each "Back up now" is kept on this TV (the last one,
 * in the app's own files) and as a file in Downloads/Homefield TV, where a file manager, a USB stick or a computer can
 * reach it (on Android 8 and 9, in the app's own folder on the TV's storage instead, which goes with the app). A file
 * chosen with Android's file chooser, where the TV has one, is kept beside it until it is put back. Nothing here goes
 * over the network. Restoring puts one back and lets go of what other parts keep in memory.
 */
public final class BackupStore {
    private BackupStore() {}

    /** The file chooser's request (TvActivity hands its answer to picked()). */
    public static final int PICK = 0x4842;
    static final int MAX_BYTES = 8 << 20;
    public static final String FOLDER = "Homefield TV";
    public static final String LAST = "last", PICKED = "picked";

    /** A backup that can be put back: which one (LAST or PICKED), and what it holds. */
    public static final class Kept {
        public final String which;
        public final Backup.Contents contents;

        Kept(String which, Backup.Contents contents) {
            this.which = which;
            this.contents = contents;
        }
    }

    private static File file(Context c, String which) {
        File dir = new File(c.getFilesDir(), "backup");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, which + ".json");
    }

    /** One kept (LAST or PICKED), or null when there isn't one that can be read. */
    public static Kept kept(Context c, String which) {
        File f = file(c, which);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            Backup.Contents b = Backup.read(text(in));
            return b == null ? null : new Kept(which, b);
        } catch (IOException e) {
            return null;
        }
    }

    // ---- backing up -------------------------------------------------------------------------------------------------

    /**
     * A backup made now and kept: on this TV, then as a file people can reach. Returns where that file is in words, or
     * "" when only this TV's copy could be written.
     */
    public static String backUp(Context c, String appVersion) throws IOException {
        long now = System.currentTimeMillis();
        String text = Backup.make(c, appVersion, now);
        write(file(c, LAST), text);
        String name = "homefield-tv-backup-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.UK).format(new Date(now)) + ".json";
        try {
            return publish(c, name, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** The file where people can reach it: Downloads (Android 10 and later) or the app's folder (8 and 9). */
    private static String publish(Context c, String name, byte[] bytes) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver r = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
            Uri uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("no room");
            try (OutputStream o = r.openOutputStream(uri)) {
                if (o == null) throw new IOException("no file");
                o.write(bytes);
            }
            return "Downloads/" + FOLDER + "/" + name;
        }
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) throw new IOException("no storage");
        File f = new File(dir, name);
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(bytes);
        }
        return "Android/data/" + c.getPackageName() + "/files/" + Environment.DIRECTORY_DOWNLOADS + "/" + name;
    }

    // ---- a file chosen ----------------------------------------------------------------------------------------------

    /** Android's file chooser, for a backup file; false where the TV has none. */
    public static boolean pick(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try {
            a.startActivityForResult(i, PICK);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /**
     * The file chooser's answer: the file read and kept (PICKED) when it is a backup. Returns null when the answer
     * wasn't the chooser's, else what to say ("" when it was kept).
     */
    public static String picked(Activity a, int request, int result, Intent data) {
        if (request != PICK) return null;
        Uri uri = data == null ? null : data.getData();
        if (result != Activity.RESULT_OK || uri == null) return "No file chosen";
        try (InputStream in = a.getContentResolver().openInputStream(uri)) {
            if (in == null) return "That file couldn't be opened";
            String text = text(in);
            if (Backup.read(text) == null) return "That isn't a Homefield TV backup";
            write(file(a, PICKED), text);
            return "";
        } catch (IOException | RuntimeException e) {
            return "That file couldn't be read";
        }
    }

    // ---- restoring --------------------------------------------------------------------------------------------------

    /** A backup put back; what other parts keep in memory let go, so they read it afresh. */
    public static void restore(Context c, Kept k) {
        Backup.restore(c, k.contents);
        Reminders.forget();
        Categories.forget();
        Parental.forget();
        com.gathin.homefieldtv.prefs.ChannelEdits.forget();
        com.gathin.homefieldtv.prefs.QualityPicks.forget();
        // A file chosen is used once.
        if (PICKED.equals(k.which)) {
            //noinspection ResultOfMethodCallIgnored
            file(c, PICKED).delete();
        }
    }

    // ---- files ------------------------------------------------------------------------------------------------------

    static String text(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > MAX_BYTES) throw new IOException("too big");
        }
        return b.toString(StandardCharsets.UTF_8.name());
    }

    private static void write(File f, String text) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("not kept");
        }
    }
}
