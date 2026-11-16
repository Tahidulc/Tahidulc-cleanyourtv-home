package com.gathin.homefieldtv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.gathin.homefieldtv.engine.Exec;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Homefield TV's own updates (Settings, Updates). A newer version is put on this TV over ADB (tvapp-publish.sh) in the
 * app's own folder, files/update/: update.json in the phone shelf's form ({"code","name","notes","file","size",
 * "sha256"}) and the APK beside it. Check for updates looks there (and quietly as the app starts). A version is offered
 * only when it is newer than this one and its APK is whole and right: the size and SHA-256 update.json gives,
 * Homefield TV's own package and version code, and signed with the same key as the app installed. Update now hands it
 * to Android's installer in a session: Android asks "Update?" and the family presses Update. The first time, Android's
 * "Install unknown apps" has to allow Homefield TV. After the update, What's new shows once.
 */
final class Updates {
    private Updates() {}

    static final String ACTION_STATUS = "com.gathin.homefieldtv.action.UPDATE_STATUS";
    private static final int MAX_JSON = 64_000;

    /** What it is doing: nothing, looking in the folder, writing the APK into Android's installer, Android asking. */
    enum Stage { IDLE, CHECKING, INSTALLING, ASKING }

    private static volatile Stage stage = Stage.IDLE;
    private static volatile String problem;
    private static volatile Offer ready;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    /** A version update.json offers: its code and name, a line on what is new, the APK's file, length and fingerprint. */
    static final class Offer {
        final int code;
        final String name, notes, file, sha256;
        final long size;

        private Offer(JSONObject j) {
            code = j.optInt("code");
            name = j.optString("name").trim();
            notes = j.optString("notes").trim();
            file = j.optString("file");
            sha256 = j.optString("sha256").toLowerCase(Locale.ROOT);
            size = j.optLong("size");
        }

        /** Pure: the offer in an update.json's words, or null when it is not a whole one. */
        static Offer parse(String json) {
            if (json == null || json.length() > MAX_JSON) return null;
            try {
                Offer o = new Offer(new JSONObject(json));
                boolean whole = o.code > 0 && !o.name.isEmpty() && plainName(o.file) && o.file.endsWith(".apk") && o.size > 0
                        && hex64(o.sha256);
                return whole ? o : null;
            } catch (JSONException e) {
                return null;
            }
        }
    }

    // ---- pure ---------------------------------------------------------------------------------------------------

    /** Pure: a file name the folder may hold (letters, digits, dots, dashes, underscores; not starting with a dot). */
    static boolean plainName(String name) {
        if (name == null || name.isEmpty() || name.length() > 96 || name.charAt(0) == '.') return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '.' || ch == '-' || ch == '_';
            if (!ok) return false;
        }
        return true;
    }

    /** Pure: a SHA-256 fingerprint as 64 hex digits. */
    static boolean hex64(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < 64; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    /** Pure: whether the offer is newer than the version installed (by version code, as Android decides). */
    static boolean newer(Offer o, long installedCode) {
        return o != null && o.code > installedCode;
    }

    /**
     * Pure: two version names compared part by part as numbers ("1.10" after "1.9", "1.5" the same as "1.5.0"); a part
     * that is not a number compared as words. Below 0 when a comes first.
     */
    static int compareNames(String a, String b) {
        String[] x = (a == null ? "" : a.trim()).split("[.\\-]"), y = (b == null ? "" : b.trim()).split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0", q = i < y.length ? y[i] : "0";
            if (p.isEmpty()) p = "0";
            if (q.isEmpty()) q = "0";
            int c;
            if (digits(p) && digits(q)) {
                c = Long.compare(Long.parseLong(p.length() > 18 ? p.substring(0, 18) : p), Long.parseLong(q.length() > 18 ? q.substring(0, 18) : q));
            } else {
                c = p.compareToIgnoreCase(q);
            }
            if (c != 0) return c;
        }
        return 0;
    }

    private static boolean digits(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return !s.isEmpty();
    }

    /** Pure: words wrapped into lines of at most max characters (a word longer than that on a line of its own). */
    static List<String> lines(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String para : text.trim().split("\\s*\\n\\s*")) {
            StringBuilder line = new StringBuilder();
            for (String w : para.trim().split("\\s+")) {
                if (w.isEmpty()) continue;
                if (line.length() > 0 && line.length() + 1 + w.length() > max) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(w);
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out;
    }

    /** Pure: when it last looked, for Settings: "Last checked today at 21:40", "… yesterday at …", "… on Mon 16 Nov". */
    static String checked(long at, long now, ZoneId zone) {
        if (at <= 0) return "Not checked yet";
        ZonedDateTime t = Instant.ofEpochMilli(at).atZone(zone);
        LocalDate day = t.toLocalDate(), today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String hm = String.format(Locale.UK, "%02d:%02d", t.getHour(), t.getMinute());
        if (day.equals(today)) return "Last checked today at " + hm;
        if (day.equals(today.minusDays(1))) return "Last checked yesterday at " + hm;
        return "Last checked on " + t.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK) + " "
                + t.getDayOfMonth() + " " + t.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK) + " at " + hm;
    }

    /** Pure: a size for people, "4.3 MB". */
    static String megabytes(long bytes) {
        double mb = bytes / 1048576.0;
        return mb < 10 ? String.format(Locale.UK, "%.1f MB", Math.max(0.1, mb)) : Math.round(mb) + " MB";
    }

    // ---- the app's side -------------------------------------------------------------------------------------------

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("app_update", Context.MODE_PRIVATE);
    }

    /** The folder ADB puts an update in (made when the app starts, so it is there to put one in). */
    static File folder(Context c) {
        return c.getApplicationContext().getExternalFilesDir("update");
    }

    /** This app's version code (read once: a new version is a new start of the app). */
    private static long mine = -1;

    @SuppressWarnings("deprecation")
    static long myCode(Context c) {
        if (mine >= 0) return mine;
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            mine = Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            mine = 0;
        }
        return mine;
    }

    static String myName(Context c) {
        try {
            String n = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return n == null ? "" : n;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    private static void changed() {
        Exec.main(() -> {
            for (Runnable r : LISTENERS) r.run();
        });
    }

    static Stage stage() {
        return stage;
    }

    /** Why the last check or update did not go through (null: it did, or none yet). */
    static String problem() {
        return problem;
    }

    /** The newer version waiting, checked whole and right (null: none). */
    static Offer ready(Context c) {
        Offer o = ready;
        return o != null && o.code > myCode(c) ? o : null;
    }

    /** A newer version is waiting: the dot on Settings. */
    static boolean waiting(Context c) {
        return ready(c) != null;
    }

    static long checkedAt(Context c) {
        return prefs(c).getLong("checked", 0);
    }

    /** Looks in the folder now (off the main thread); then after, on the main thread. */
    static void check(Context c, Runnable after) {
        Context app = c.getApplicationContext();
        if (stage != Stage.IDLE) {
            if (after != null) Exec.main(after);
            return;
        }
        stage = Stage.CHECKING;
        changed();
        Exec.io(() -> {
            String why = null;
            Offer found = null;
            try {
                found = look(app);
            } catch (IOException e) {
                why = e.getMessage();
            }
            ready = found;
            problem = why;
            prefs(app).edit().putLong("checked", System.currentTimeMillis()).apply();
            if (stage == Stage.CHECKING) stage = Stage.IDLE;
            changed();
            if (after != null) Exec.main(after);
        });
    }

    /** The offer in the folder, when it is newer and its APK whole and right; null when there is none; why not, thrown. */
    static Offer look(Context c) throws IOException {
        File dir = folder(c);
        File json = dir == null ? null : new File(dir, "update.json");
        if (json == null || !json.isFile()) return null;
        Offer o = Offer.parse(read(json));
        if (o == null) throw new IOException("The update waiting here couldn't be read");
        if (!newer(o, myCode(c))) return null;
        File apk = new File(dir, o.file);
        if (!apk.isFile() || apk.length() != o.size) throw new IOException("Homefield TV " + o.name + " is still arriving: try again in a minute");
        SharedPreferences p = prefs(c);
        String key = o.code + "|" + o.sha256 + "|" + apk.length() + "|" + apk.lastModified();
        if (!key.equals(p.getString("verified", ""))) {
            if (!o.sha256.equals(sha256(apk))) throw new IOException("Homefield TV " + o.name + " didn't arrive whole: it needs putting here again");
            PackageManager pm = c.getPackageManager();
            PackageInfo got = archive(pm, apk);
            if (got == null || !c.getPackageName().equals(got.packageName)) throw new IOException("The update waiting here isn't Homefield TV");
            long code = Build.VERSION.SDK_INT >= 28 ? got.getLongVersionCode() : got.versionCode;
            if (code != o.code) throw new IOException("The update waiting here isn't the version its note says");
            if (!sameSigner(signers(got), signers(installed(pm, c.getPackageName())))) {
                throw new IOException("Homefield TV " + o.name + " is signed differently from this one, so Android wouldn't take it");
            }
            p.edit().putString("verified", key).apply();
        }
        return o;
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo archive(PackageManager pm, File apk) {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo p = pm.getPackageArchiveInfo(apk.getPath(), flags);
        if (p != null && Build.VERSION.SDK_INT >= 28 && p.signingInfo == null) {
            p = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        }
        return p;
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo installed(PackageManager pm, String pkg) {
        try {
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            return pm.getPackageInfo(pkg, flags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** The certificates an APK is signed with now, as bytes (null when Android gives none). */
    @SuppressWarnings("deprecation")
    private static byte[][] signers(PackageInfo p) {
        if (p == null) return null;
        Signature[] s = Build.VERSION.SDK_INT >= 28 && p.signingInfo != null ? p.signingInfo.getApkContentsSigners() : p.signatures;
        if (s == null) return null;
        byte[][] out = new byte[s.length][];
        for (int i = 0; i < s.length; i++) out[i] = s[i].toByteArray();
        return out;
    }

    /** Pure: signed with the same key: the same certificates, each one in both (none at all is never the same). */
    static boolean sameSigner(byte[][] a, byte[][] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return false;
        for (byte[] x : a) {
            boolean found = false;
            for (byte[] y : b) found |= x != null && Arrays.equals(x, y);
            if (!found) return false;
        }
        return true;
    }

    private static String read(File f) throws IOException {
        if (f.length() > MAX_JSON) throw new IOException("The update waiting here couldn't be read");
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            return hex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("This TV can't check the update");
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return s.toString();
    }

    /** Whether Android lets Homefield TV install its updates (Install unknown apps). */
    static boolean allowed(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * Update now: the version waiting handed to Android's installer. The first time, Android's Install unknown apps for
     * Homefield TV opens instead (Settings says why); back from it, allowed, the update goes on (resumed).
     */
    static void update(Activity a) {
        Context c = a.getApplicationContext();
        if (stage == Stage.INSTALLING || stage == Stage.CHECKING) return;
        Offer o = ready(c);
        if (o == null) return;
        problem = null;
        if (!allowed(c)) {
            prefs(c).edit().putLong("allow_at", System.currentTimeMillis()).apply();
            if (!open(a, new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())))
                    && !open(a, new Intent(Settings.ACTION_SECURITY_SETTINGS))) {
                problem = "Allow Homefield TV to install apps in the TV's Settings (Apps, Special app access, Install unknown apps)";
            }
            changed();
            return;
        }
        stage = Stage.INSTALLING;
        changed();
        Exec.io(() -> {
            try {
                install(c, o);
            } catch (IOException | RuntimeException e) {
                failed(e.getMessage() == null || e.getMessage().isEmpty() ? "Android didn't take the update" : e.getMessage());
            }
        });
    }

    private static boolean open(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** Back on the app (TvActivity.onResume): from Install unknown apps, now allowed, the update goes on. */
    static void resumed(Activity a) {
        Context c = a.getApplicationContext();
        long at = prefs(c).getLong("allow_at", 0);
        if (at == 0) return;
        if (System.currentTimeMillis() - at > 10 * 60_000L) {
            prefs(c).edit().remove("allow_at").apply();
            return;
        }
        if (!allowed(c)) return;
        prefs(c).edit().remove("allow_at").apply();
        update(a);
    }

    /** The APK written into an installer session, its fingerprint checked again, committed (Android asks next). */
    @SuppressLint("ApplySharedPref")
    private static void install(Context c, Offer o) throws IOException {
        File apk = new File(folder(c), o.file);
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        sp.setSize(o.size);
        sp.setInstallReason(PackageManager.INSTALL_REASON_USER);
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        boolean committed = false;
        try {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("This TV can't check the update");
            }
            long written = 0;
            try (OutputStream raw = s.openWrite("base.apk", 0, o.size); InputStream in = new FileInputStream(apk)) {
                DigestOutputStream out = new DigestOutputStream(new BufferedOutputStream(raw, 1 << 16), md);
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    written += n;
                }
                out.flush();
                s.fsync(raw);
            }
            if (written != o.size || !hex(md.digest()).equals(o.sha256)) throw new IOException("The update changed while it was being read: check again");
            // What it is updating to, written down at once: Android closes the app as the new version goes in.
            prefs(c).edit().putInt("to", o.code).putString("to_name", o.name).putString("to_notes", o.notes).commit();
            Intent i = new Intent(c, UpdateStatus.class).setAction(ACTION_STATUS);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent done = PendingIntent.getBroadcast(c, id, i, flags);
            s.commit(done.getIntentSender());
            committed = true;
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

    /** Android's question is up (UpdateStatus). */
    static void asking() {
        stage = Stage.ASKING;
        changed();
    }

    /** In (Android normally closes the app first, so this is seldom heard). */
    static void installed() {
        stage = Stage.IDLE;
        changed();
    }

    /** It did not go in: why, for Settings. */
    static void failed(String why) {
        problem = why;
        stage = Stage.IDLE;
        changed();
    }

    /**
     * What's new, once, on the first start of a newer version: {its name, its notes}, or null. From what Update now wrote
     * down, else from the update.json of this very version still in the folder (when it came another way).
     */
    static String[] whatsNew(Context c) {
        SharedPreferences p = prefs(c);
        long mine = myCode(c), seen = p.getLong("seen_code", 0);
        if (seen >= mine) return null;
        p.edit().putLong("seen_code", mine).apply();
        if (seen == 0) return null;
        String name = myName(c), notes = "";
        if (p.getInt("to", 0) == mine) {
            notes = p.getString("to_notes", "");
            p.edit().remove("to").remove("to_name").remove("to_notes").apply();
        } else {
            File dir = folder(c);
            File json = dir == null ? null : new File(dir, "update.json");
            try {
                Offer o = json != null && json.isFile() ? Offer.parse(read(json)) : null;
                if (o != null && o.code == mine) notes = o.notes;
            } catch (IOException ignored) {
                // no notes
            }
        }
        return notes == null || notes.isEmpty() ? null : new String[]{name, notes};
    }
}
