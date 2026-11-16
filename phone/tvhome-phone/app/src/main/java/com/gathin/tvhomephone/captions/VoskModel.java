package com.gathin.tvhomephone.captions;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The offline English speech model for automatic subtitles where the phone has no recogniser of its own: Vosk's small
 * US English model (Apache 2.0), fetched once when the family agrees, unpacked into the app's own storage and kept
 * there. Only the model is fetched; nothing about what is watched is sent anywhere.
 */
public final class VoskModel {
    private VoskModel() {}

    public static final String NAME = "vosk-model-small-en-us-0.15";
    static final String URL = "https://alphacephei.com/vosk/models/" + NAME + ".zip";
    /** Its download, for the line asking first. */
    public static final int SIZE_MB = 40;

    /** How the fetch goes, on the main thread. */
    public interface Progress {
        void progress(int percent);

        void done(boolean ok);
    }

    private static volatile boolean fetching;

    public static File dir(Context c) {
        return new File(c.getFilesDir(), "captions/" + NAME);
    }

    public static boolean ready(Context c) {
        return valid(dir(c));
    }

    public static boolean fetching() {
        return fetching;
    }

    /** Pure: whether a folder holds a whole model (its acoustic model and its settings). */
    static boolean valid(File d) {
        return new File(d, "am/final.mdl").isFile() && new File(d, "conf/mfcc.conf").isFile();
    }

    /** Fetches and unpacks the model (once at a time); told how it goes on the main thread. */
    public static void download(Context c, Progress p) {
        Context app = c.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        if (fetching) return;
        fetching = true;
        new Thread(() -> {
            boolean ok = false;
            File zip = new File(app.getCacheDir(), NAME + ".zip.part");
            File tmp = new File(app.getFilesDir(), "captions/.unpacking");
            try {
                fetch(zip, percent -> main.post(() -> p.progress(percent)));
                deleteAll(tmp);
                try (InputStream in = new FileInputStream(zip)) {
                    unzip(in, tmp);
                }
                File inside = new File(tmp, NAME);
                File from = valid(inside) ? inside : valid(tmp) ? tmp : null;
                if (from != null) {
                    File to = dir(app);
                    deleteAll(to);
                    File parent = to.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("no room");
                    ok = from.renameTo(to) && valid(to);
                }
            } catch (IOException | RuntimeException ignored) {
                // told below
            } finally {
                if (!zip.delete()) zip.deleteOnExit();
                deleteAll(tmp);
                fetching = false;
            }
            boolean done = ok;
            main.post(() -> p.done(done));
        }, "captions-model").start();
    }

    private interface Percent {
        void at(int percent);
    }

    private static void fetch(File to, Percent p) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(URL).openConnection();
        try {
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            c.setRequestProperty("User-Agent", "Homefield");
            if (c.getResponseCode() != 200) throw new IOException("fetch said " + c.getResponseCode());
            long total = c.getContentLengthLong();
            File parent = to.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("no room");
            try (InputStream in = new BufferedInputStream(c.getInputStream()); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[64 * 1024];
                long got = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    got += n;
                    if (got > 200L * 1024 * 1024) throw new IOException("too big");
                    int pct = total > 0 ? (int) (got * 100 / total) : -1;
                    if (pct != last) {
                        last = pct;
                        p.at(pct);
                    }
                }
            }
        } finally {
            c.disconnect();
        }
    }

    /** Pure Java: a zip unpacked into a folder, refusing any entry that would land outside it. */
    static void unzip(InputStream in, File into) throws IOException {
        String root = into.getCanonicalPath() + File.separator;
        if (!into.isDirectory() && !into.mkdirs()) throw new IOException("no room");
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = z.getNextEntry()) != null) {
                File f = new File(into, e.getName());
                String path = f.getCanonicalPath();
                if (!path.startsWith(root)) throw new IOException("an entry outside the folder");
                if (e.isDirectory()) {
                    if (!f.isDirectory() && !f.mkdirs()) throw new IOException("no room");
                    continue;
                }
                File parent = f.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("no room");
                try (OutputStream out = new FileOutputStream(f)) {
                    int n;
                    while ((n = z.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        }
    }

    static void deleteAll(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        if (!f.delete()) f.deleteOnExit();
    }
}
