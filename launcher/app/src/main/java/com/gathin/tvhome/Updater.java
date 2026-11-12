package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's own updates. A new version is put ready on the TV over ADB in the launcher's own folder (updates/:
 * update.json, and either the whole APK or only what changed against the version installed, a few megabytes instead of
 * the whole) and installed from the Control Centre's Check for updates, or started over ADB (UpdateReceiver). The new APK
 * is put together from the one installed (each unchanged part copied from it) and what changed (UpdatePatch), its
 * fingerprint checked as it is made, and handed straight to Android's installer, a card on the screen (UpdateCard)
 * showing how far it has got. The launcher then restarts as the new version (on Android 12 and later without a
 * question, as it updates itself) and says so. Nothing is fetched from anywhere, and Android installs only an update
 * signed as this launcher is.
 */
final class Updater {
    private Updater() { }

    static final String TAG = "Updater";
    /** Started over ADB (UpdateReceiver): the update put ready installed now, as Check for updates does. */
    static final String ACTION_UPDATE = "com.gathin.tvhome.action.UPDATE";
    /** Android's installer's word on it (UpdateStatus). */
    static final String ACTION_STATUS = "com.gathin.tvhome.action.UPDATE_STATUS";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Updater");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean running;
    private static volatile int percent;
    private static long saidAt;
    private static int saidPercent = -1;

    /** An update put ready: its version, what it is (a patch against version code from, or a whole APK), its file, the new APK's length and fingerprint, a line on what is new. */
    static final class Ready {
        final int code, from;
        final String name, kind, file, sha256, notes;
        final long size;

        Ready(JSONObject j) {
            code = j.optInt("code");
            from = j.optInt("from");
            name = j.optString("name");
            kind = j.optString("kind");
            file = j.optString("file");
            sha256 = j.optString("sha256");
            notes = j.optString("notes");
            size = j.optLong("size");
        }

        boolean patch() {
            return "patch".equals(kind);
        }
    }

    /** The launcher's update folder (made by the launcher itself, so what ADB puts there is the launcher's to read). */
    static File dir(Context c) {
        return c.getExternalFilesDir("updates");
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("updater", Context.MODE_PRIVATE);
    }

    static long myCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    static String myName(Context c) {
        try {
            String n = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return n == null ? "" : n;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The update put ready, when it is newer than this launcher and all there (null: none). */
    static Ready ready(Context c) {
        File d = dir(c);
        File j = d == null ? null : new File(d, "update.json");
        if (j == null || !j.isFile() || j.length() > 64_000) return null;
        try (InputStream in = new FileInputStream(j)) {
            byte[] b = new byte[(int) j.length()];
            int n = 0;
            while (n < b.length) {
                int k = in.read(b, n, b.length - n);
                if (k < 0) break;
                n += k;
            }
            Ready r = new Ready(new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8)));
            if (r.code <= myCode(c) || r.file.isEmpty() || r.file.contains("/") || r.size <= 0 || r.sha256.length() != 64) return null;
            File f = new File(d, r.file);
            if (!f.isFile() || (!r.patch() && f.length() != r.size)) return null;
            return r;
        } catch (IOException | org.json.JSONException | RuntimeException e) {
            return null;
        }
    }

    /** Whether an update is waiting to be installed (its tile lit). */
    static boolean waiting(Context c) {
        return !running && ready(c) != null;
    }

    /** In a few words, for the Control Centre's tile. */
    static String words(Context c) {
        if (running) return "Updating · " + percent + "%";
        Ready r = ready(c);
        return r != null ? r.name + " ready to install" : "Up to date · " + myName(c);
    }

    /**
     * At start: the folder made (so ADB can put an update in it), a word that the update is in when this is the version
     * it was installing, and what is done with let go.
     */
    static void started(Context c) {
        dir(c);
        SharedPreferences p = prefs(c);
        int to = p.getInt("to", 0);
        if (to > 0) {
            p.edit().remove("to").remove("name").remove("notes").remove("from").apply();
            if (myCode(c) >= to) {
                String notes = p.getString("notes", "");
                Log.i(TAG, "updated to " + myName(c));
                AlertLog.note(c, "TV Home updated to " + myName(c));
                MAIN.postDelayed(() -> UpdateCard.say(c, "TV Home updated to " + myName(c),
                        notes.isEmpty() ? "Everything is as you left it" : notes, true, 6_000L), 1_500L);
            }
        }
        WORK.execute(() -> clean(c));
    }

    /** Updates installed (or older than this launcher) let go; never one still being put there (ten minutes' grace). */
    private static void clean(Context c) {
        File d = dir(c);
        File[] fs = d == null ? null : d.listFiles();
        if (fs == null || ready(c) != null || running) return;
        long old = System.currentTimeMillis() - 10 * 60_000L;
        for (File f : fs) if (f.isFile() && f.lastModified() < old && !f.delete()) f.deleteOnExit();
    }

    /**
     * Check for updates: the update put ready installed, the card showing how far it has got; none newer, the card says
     * so (asked over ADB: only the log).
     */
    static void check(Context c, boolean byHand) {
        Context app = c.getApplicationContext();
        if (running) {
            Log.i(TAG, "already updating (" + percent + "%)");
            return;
        }
        Ready r = ready(app);
        if (r == null) {
            Log.i(TAG, "up to date: " + myName(app));
            if (byHand) UpdateCard.say(app, "TV Home is up to date", "Version " + myName(app), false, 4_000L);
            WORK.execute(() -> clean(app));
            return;
        }
        if (r.patch() && myCode(app) != r.from) {
            Log.i(TAG, r.name + " was made for version code " + r.from + ", this is " + myCode(app));
            if (byHand) UpdateCard.say(app, "This update doesn't fit", "It was made for another version of TV Home", false, 6_000L);
            return;
        }
        running = true;
        percent = 0;
        saidPercent = -1;
        Log.i(TAG, "installing " + r.name + " (" + (r.patch() ? "what changed, " : "whole, ") + new File(dir(app), r.file).length() + " bytes)");
        UpdateCard.updating(app, myName(app), r.name);
        WORK.execute(() -> {
            try {
                install(app, r);
            } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
                failed(app, e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    /** What it is doing and how far it has got (0 to 1), to the card at most ten times a second. */
    private static void at(float share, String what) {
        int p = Math.max(0, Math.min(99, Math.round(share * 100)));
        percent = p;
        long now = SystemClock.uptimeMillis();
        if (p == saidPercent || now - saidAt < 100L) return;
        saidPercent = p;
        saidAt = now;
        String words = what + " · " + p + "%";
        MAIN.post(() -> UpdateCard.progress(share, words, 120L));
    }

    private static void install(Context c, Ready r) throws IOException, NoSuchAlgorithmException {
        long t0 = SystemClock.uptimeMillis();
        File f = new File(dir(c), r.file);
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        sp.setSize(r.size);
        sp.setInstallReason(PackageManager.INSTALL_REASON_USER);
        // Updating itself: Android 12 and later need not ask (the launcher may install, and is updating its own app).
        if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        boolean committed = false;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long written;
            at(0.02f, "Getting ready");
            try (OutputStream raw = s.openWrite("base.apk", 0, r.size)) {
                DigestOutputStream out = new DigestOutputStream(new BufferedOutputStream(raw, 1 << 16), md);
                UpdatePatch.Progress p = (done, total) -> at(0.02f + 0.86f * done / Math.max(1L, total), "Putting the update together");
                try (InputStream in = new FileInputStream(f)) {
                    written = r.patch() ? UpdatePatch.apply(new File(c.getApplicationInfo().sourceDir), in, out, p) : copy(in, r.size, out, p);
                }
                out.flush();
                s.fsync(raw);
            }
            String got = hex(md.digest());
            if (written != r.size || !got.equalsIgnoreCase(r.sha256)) {
                throw new IOException("it did not come out as it should (" + written + " bytes, " + got.substring(0, 12) + ")");
            }
            Log.i(TAG, "put together in " + (SystemClock.uptimeMillis() - t0) + " ms: " + written + " bytes, its fingerprint as it should be");
            prefs(c).edit().putInt("to", r.code).putString("name", r.name).putString("notes", r.notes).putLong("from", myCode(c)).commit();
            Intent i = new Intent(c, UpdateStatus.class).setAction(ACTION_STATUS);
            PendingIntent done = PendingIntent.getBroadcast(c, id, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            percent = 90;
            MAIN.post(() -> UpdateCard.progress(0.9f, "Installing · TV Home restarts by itself", 0L));
            s.commit(done.getIntentSender());
            committed = true;
            Log.i(TAG, "handed to Android's installer");
            // Android installs it and restarts the launcher: the bar creeps on meanwhile.
            MAIN.post(() -> UpdateCard.progress(0.99f, "Installing · TV Home restarts by itself", 30_000L));
        } finally {
            if (!committed) {
                try {
                    s.abandon();
                } catch (RuntimeException ignored) {
                    // gone already
                }
            }
            s.close();
        }
    }

    private static long copy(InputStream in, long size, OutputStream out, UpdatePatch.Progress p) throws IOException {
        byte[] buf = new byte[1 << 16];
        long done = 0;
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
            done += n;
            p.at(done, size);
        }
        return done;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return s.toString();
    }

    /** Android asks first (before Android 12, or not allowed to install by itself): its question is up. */
    static void asked(Context c) {
        Log.i(TAG, "Android asks before installing");
        MAIN.post(() -> UpdateCard.progress(0.9f, "Choose Install on the screen to finish", 0L));
    }

    /** It did not go in: why, on the card and in the log. */
    static void failed(Context c, String why) {
        running = false;
        percent = 0;
        prefs(c).edit().remove("to").apply();
        Log.w(TAG, "the update did not install: " + why);
        AlertLog.note(c, "the update did not install: " + why);
        MAIN.post(() -> UpdateCard.say(c, "The update didn't install", why, false, 9_000L));
    }

    /** In (Android went on without stopping the launcher, which it normally does): the new version takes over at its next start. */
    static void installed(Context c) {
        running = false;
        Log.i(TAG, "installed");
    }
}
