package com.gathin.tvhomephone.update;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sync.Tvs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Homefield's own updates, from the family's TVs. Each new version is put on the TVs, where the launcher keeps it for
 * the phones and tablets (PhoneShelf there): update.json, the whole APK and, much smaller, what changed against recent
 * versions. The app asks its paired TVs what they have when it opens (at most every half hour) and from the background
 * sync (every few hours), and says when there is something newer: a question in the app, once a day at most, and a
 * quiet notification. On Update it fetches only what changed when the TV has that for this version (else the whole
 * APK; a download that broke off picks up where it stopped), puts the new APK together against the one installed,
 * checks its fingerprint, and hands it to Android's installer. Android asks once whether Homefield may install apps;
 * after that, on Android 12 and later, it updates without a further question, closing as the new version goes in
 * (Updated says so, and opens it again where it can). Nothing comes from anywhere but the family's own TVs, and Android
 * installs only an update signed as this app is.
 */
public final class AppUpdate {
    private AppUpdate() {}

    static final String TAG = "AppUpdate";
    /** Android's installer's word on it (InstallStatus). */
    static final String ACTION_STATUS = "com.gathin.tvhomephone.action.UPDATE_STATUS";
    /** The TVs' link (DeliveryLink there). */
    private static final int PORT = 47820;
    /** Opening the app looks on the TVs at most this often; the background sync, this often. */
    static final long OPEN_EVERY_MS = 30 * 60_000L, BACKGROUND_EVERY_MS = 3 * 3_600_000L;
    /** "Later" on the question: asked again after a day. */
    static final long ASK_AGAIN_MS = 24 * 3_600_000L;
    /** Room kept free beyond the update itself. */
    private static final long SPARE = 48L << 20;
    private static final String READY = "app_update", DONE = "app_updated";
    private static final int READY_ID = 9201, DONE_ID = 9202;

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AppUpdate");
        t.setDaemon(true);
        return t;
    });
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    /** What it is doing: nothing, looking on the TVs, fetching, putting the APK together, installing, Android asking. */
    enum Stage { IDLE, CHECKING, FETCHING, BUILDING, INSTALLING, ASKING }

    private static volatile Stage stage = Stage.IDLE;
    private static volatile long stageAt;
    private static volatile float share;
    private static volatile String problem;
    private static volatile String from = "";
    private static long saidAt;
    private static int saidPercent = -1;
    /** Whether the app's window is on the screen (MainActivity): then it opens again by itself after the update. */
    private static volatile boolean onScreen;

    /** A refusal that trying again will not change (not paired any more, gone from the TV). */
    static final class Refused extends IOException {
        Refused(String why) {
            super(why);
        }
    }

    /** The new APK did not come out as it should (made against another copy, or spoilt on the way). */
    static final class Mismatch extends IOException {
        Mismatch(String why) {
            super(why);
        }
    }

    /**
     * A version a TV keeps for the phones: its code and name, a line on what is new, the whole APK (its file, length and
     * fingerprint), and what changed against recent versions (a patch per version code, and its size). Where it is: the
     * TV's name, address and port.
     */
    static final class Offer {
        final int code;
        final String name, notes, file, sha256;
        final long size;
        final TreeMap<Integer, String> patches = new TreeMap<>();
        final TreeMap<Integer, Long> patchBytes = new TreeMap<>();
        String tv = "", host = "";
        int port = PORT;

        private Offer(JSONObject j) {
            code = j.optInt("code");
            name = j.optString("name");
            notes = j.optString("notes").trim();
            file = j.optString("file");
            sha256 = j.optString("sha256");
            size = j.optLong("size");
            JSONArray ps = j.optJSONArray("patches");
            for (int i = 0; ps != null && i < ps.length(); i++) {
                JSONObject p = ps.optJSONObject(i);
                if (p == null || p.optInt("from") <= 0 || !plainName(p.optString("file"))) continue;
                patches.put(p.optInt("from"), p.optString("file"));
                patchBytes.put(p.optInt("from"), p.optLong("bytes"));
            }
        }

        /** Pure: the offer in a TV's update.json, or null when it is not a whole one. */
        static Offer parse(JSONObject j) {
            if (j == null) return null;
            Offer o = new Offer(j);
            return o.code > 0 && !o.name.isEmpty() && plainName(o.file) && o.size > 0 && hex64(o.sha256) ? o : null;
        }

        /** Pure: as kept on the phone (where it is, too). */
        static Offer kept(JSONObject j) {
            Offer o = parse(j);
            if (o == null) return null;
            o.tv = j.optString("tv");
            o.host = j.optString("host");
            o.port = j.optInt("port", PORT);
            return o.host.isEmpty() ? null : o;
        }

        JSONObject json() throws JSONException {
            JSONArray ps = new JSONArray();
            for (Integer f : patches.keySet()) {
                ps.put(new JSONObject().put("from", f).put("file", patches.get(f)).put("bytes", patchBytes.get(f)));
            }
            return new JSONObject().put("code", code).put("name", name).put("notes", notes).put("file", file)
                    .put("sha256", sha256).put("size", size).put("patches", ps)
                    .put("tv", tv).put("host", host).put("port", port);
        }

        /** Pure: what changed against this version code, or null (then the whole APK). */
        String patchFrom(long mine) {
            return mine > 0 && mine <= Integer.MAX_VALUE ? patches.get((int) mine) : null;
        }

        /** Pure: about how much is fetched for this version code. */
        long bytesFor(long mine) {
            String p = patchFrom(mine);
            Long b = p == null ? null : patchBytes.get((int) mine);
            return p == null ? size : b != null && b > 0 ? b : size / 20;
        }
    }

    /** Pure: a file name a TV's shelf gives out (letters, digits, dots, dashes, underscores; not starting with a dot). */
    static boolean plainName(String name) {
        if (name == null || name.isEmpty() || name.length() > 96 || name.charAt(0) == '.') return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '.' || ch == '-' || ch == '_';
            if (!ok) return false;
        }
        return true;
    }

    /** Pure: a SHA-256 fingerprint as hex. */
    static boolean hex64(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < 64; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    /** Pure: whether it is time to look again. */
    static boolean due(long now, long lastLooked, long every) {
        return lastLooked <= 0 || now - lastLooked >= every || now < lastLooked;
    }

    /** Pure: whether to ask about this version now (not asked about it, or asked over a day ago). */
    static boolean askNow(int code, int askedCode, long askedAt, long now) {
        return code != askedCode || now - askedAt >= ASK_AGAIN_MS || now < askedAt;
    }

    /** Pure: a size for people, "2.4 MB" or "139 MB". */
    static String megabytes(long bytes) {
        double mb = bytes / 1048576.0;
        return mb < 10 ? String.format(Locale.UK, "%.1f MB", Math.max(0.1, mb)) : Math.round(mb) + " MB";
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("app_update", Context.MODE_PRIVATE);
    }

    public static long myCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public static String myName(Context c) {
        try {
            String n = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return n == null ? "" : n;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    private static void changed() {
        App.main.post(() -> {
            for (Runnable r : LISTENERS) r.run();
        });
    }

    /**
     * What it is doing. Installing heard nothing of for two minutes, or Android's question left three, counts as over
     * (Android went on without a word), so Update can be tapped again.
     */
    static Stage stage() {
        Stage s = stage;
        long since = SystemClock.elapsedRealtime() - stageAt;
        if (s == Stage.INSTALLING && since >= 120_000L || s == Stage.ASKING && since >= 180_000L) return Stage.IDLE;
        return s;
    }

    /** How far it has got, 0 to 1, while fetching or putting the APK together. */
    static float share() {
        return share;
    }

    /** Why the last try did not go in (null: it did, or none yet). */
    static String problem() {
        return problem;
    }

    /** The TV it is fetching from. */
    static String from() {
        return from;
    }

    /** Busy with an update. */
    static boolean busy() {
        Stage s = stage();
        return s == Stage.FETCHING || s == Stage.BUILDING || s == Stage.INSTALLING || s == Stage.ASKING;
    }

    /** The newest version a TV has that is newer than this app, as last looked (null: none). */
    static Offer ready(Context c) {
        String s = prefs(c).getString("offer", null);
        if (s == null) return null;
        try {
            Offer o = Offer.kept(new JSONObject(s));
            return o != null && o.code > myCode(c) ? o : null;
        } catch (JSONException e) {
            return null;
        }
    }

    /** When the TVs were last looked at (0: never), how many were asked and how many answered. */
    static long lookedAt(Context c) {
        return prefs(c).getLong("checked", 0);
    }

    static int tvsAsked(Context c) {
        return prefs(c).getInt("asked", 0);
    }

    static int tvsAnswered(Context c) {
        return prefs(c).getInt("answered", 0);
    }

    /** What looking on the TVs found: the newest offer, how many TVs were asked and how many answered. */
    static final class Look {
        Offer offer;
        int asked, answered;
    }

    /** Each paired TV asked for its update.json (off the main thread). */
    private static Look look(Context c) {
        Look l = new Look();
        for (JSONObject tv : Tvs.list(c)) {
            String token = tv.optString("token"), host = tv.optString("host");
            if (token.isEmpty() || host.isEmpty()) continue;
            l.asked++;
            int port = tv.optInt("port", PORT);
            HttpURLConnection h = null;
            try {
                h = open(host, port, "update.json", token, 4_000);
                int code = h.getResponseCode();
                l.answered++;
                if (code != 200) continue;   // nothing kept there (yet), or a launcher from before the shelf
                Offer o = Offer.parse(new JSONObject(read(h.getInputStream(), 64_000)));
                if (o == null) continue;
                o.tv = tv.optString("name");
                o.host = host;
                o.port = port;
                if (l.offer == null || o.code > l.offer.code) l.offer = o;
            } catch (IOException | JSONException | RuntimeException e) {
                // that TV is away or off: the next
            } finally {
                if (h != null) h.disconnect();
            }
        }
        return l;
    }

    /** What was found kept: the newest version, or none when a TV answered with nothing newer (one away keeps it). */
    private static void keep(Context c, Look l) {
        SharedPreferences.Editor e = prefs(c).edit().putLong("checked", System.currentTimeMillis())
                .putInt("asked", l.asked).putInt("answered", l.answered);
        try {
            if (l.offer != null && l.offer.code > myCode(c)) e.putString("offer", l.offer.json().toString());
            else if (l.answered > 0) e.remove("offer");
        } catch (JSONException ignored) {
            // kept as it was
        }
        e.apply();
    }

    /** Looks on the paired TVs now; then after, on the main thread. */
    public static void check(Context c, Runnable after) {
        Context app = c.getApplicationContext();
        if (busy() || stage == Stage.CHECKING) {
            if (after != null) App.main.post(after);
            return;
        }
        stage(Stage.CHECKING, 0f);
        problem = null;
        WORK.execute(() -> {
            try {
                keep(app, look(app));
            } finally {
                if (stage == Stage.CHECKING) stage(Stage.IDLE, 0f);
                if (after != null) App.main.post(after);
            }
        });
    }

    /**
     * The app on the screen (MainActivity.onStart): the word that it updated, if it just did; the TVs looked at (at most
     * every half hour); a newer version asked about (once a day at most).
     */
    public static void opened(Activity a) {
        onScreen = true;
        Context c = a.getApplicationContext();
        String just = prefs(c).getString("just", null);
        if (just != null) {
            prefs(c).edit().remove("just").apply();
            NotificationManagerCompat.from(c).cancel(DONE_ID);
            Toast.makeText(a, "Homefield is updated to " + just, Toast.LENGTH_LONG).show();
        }
        if (due(System.currentTimeMillis(), lookedAt(c), OPEN_EVERY_MS)) check(c, () -> ask(a, false));
        else ask(a, false);
        WORK.execute(() -> tidy(c));
    }

    /** The app off the screen (MainActivity.onStop). */
    public static void closed() {
        onScreen = false;
    }

    /** Back from Android's "Install unknown apps" for Homefield (MainActivity.onResume): allowed now, the update goes on. */
    public static void resumed(Activity a) {
        Context c = a.getApplicationContext();
        long at = prefs(c).getLong("allow_at", 0);
        if (at == 0) return;
        if (System.currentTimeMillis() - at > 10 * 60_000L) {
            prefs(c).edit().remove("allow_at").apply();
            return;
        }
        if (!c.getPackageManager().canRequestPackageInstalls()) return;
        prefs(c).edit().remove("allow_at").apply();
        update(a);
    }

    /** A newer version asked about (anyway: asked by hand, or from the notification, even if asked lately). */
    public static void ask(Activity a, boolean anyway) {
        if (a.isFinishing() || a.isDestroyed()) return;
        Context c = a.getApplicationContext();
        if (busy()) {
            if (anyway) UpdateSettings.progress(a);
            return;
        }
        Offer o = ready(c);
        if (o == null) {
            if (anyway) Toast.makeText(a, "Homefield is up to date", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        if (!anyway && !askNow(o.code, p.getInt("asked_code", 0), p.getLong("asked_at", 0), now)) return;
        p.edit().putInt("asked_code", o.code).putLong("asked_at", now).apply();
        NotificationManagerCompat.from(c).cancel(READY_ID);
        UpdateSettings.question(a, o, o.bytesFor(myCode(c)));
    }

    /** Update: the newest version fetched from its TV, put together and handed to Android's installer. */
    public static void update(Activity a) {
        Context c = a.getApplicationContext();
        if (busy()) {
            UpdateSettings.progress(a);
            return;
        }
        Offer o = ready(c);
        if (o == null) {
            Toast.makeText(a, "Homefield is up to date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!c.getPackageManager().canRequestPackageInstalls()) {
            UpdateSettings.allow(a, () -> prefs(c).edit().putLong("allow_at", System.currentTimeMillis()).apply());
            return;
        }
        problem = null;
        from = o.tv;
        stage(Stage.FETCHING, 0f);
        NotificationManagerCompat.from(c).cancel(READY_ID);
        UpdateSettings.progress(a);
        WORK.execute(() -> run(c, o));
    }

    private static void run(Context c, Offer o) {
        try {
            String patch = o.patchFrom(myCode(c));
            if (patch != null) {
                File p = fetch(c, o, patch, 0f, 0.25f);
                try {
                    install(c, o, p, true);
                    return;
                } catch (IOException e) {
                    // Made against another copy than the one on this phone: the whole APK instead.
                    Log.w(TAG, "what changed did not fit this copy (" + e.getMessage() + "), fetching the whole of it");
                    clear(c);
                }
            }
            install(c, o, fetch(c, o, o.file, 0f, 0.85f), false);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            failed(c, e.getMessage() == null || e.getMessage().isEmpty() ? e.toString() : e.getMessage());
        }
    }

    private static String tokenFor(Context c, String tv) {
        for (JSONObject t : Tvs.list(c)) {
            if (t.optString("name").equals(tv) && !t.optString("token").isEmpty()) return t.optString("token");
        }
        return null;
    }

    private static File folder(Context c) {
        return new File(c.getCacheDir(), "update");
    }

    /** Fetched files let go. */
    private static void clear(Context c) {
        File[] fs = folder(c).listFiles();
        if (fs != null) for (File f : fs) if (!f.delete()) f.deleteOnExit();
        prefs(c).edit().remove("partial").apply();
    }

    /** Nothing waiting and nothing under way: what was fetched for an update let go. */
    private static void tidy(Context c) {
        if (busy() || ready(c) != null) return;
        File[] fs = folder(c).listFiles();
        if (fs != null && fs.length > 0) clear(c);
    }

    /**
     * One file of the update fetched into the app's cache, picking up a part already there (the same file of the same
     * version); from and to: the share of the whole it stands for.
     */
    private static File fetch(Context c, Offer o, String name, float from, float to) throws IOException {
        String token = tokenFor(c, o.tv);
        if (token == null) throw new Refused("This phone is no longer paired with " + o.tv + ". Pair it again in Settings");
        File dir = folder(c);
        SharedPreferences p = prefs(c);
        String key = o.code + "|" + o.sha256 + "|" + name;
        if (!key.equals(p.getString("partial", null))) {
            clear(c);
            p.edit().putString("partial", key).apply();
        }
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no room for the update on this phone");
        File f = new File(dir, name);
        long have = f.isFile() ? f.length() : 0;
        boolean whole = name.equals(o.file);
        long need = (whole ? Math.max(0, o.size - have) : 0) + o.size + SPARE;
        if (dir.getUsableSpace() < need) {
            throw new Refused("Not enough free space: the update needs about " + megabytes(need) + " free on this phone");
        }
        stage(Stage.FETCHING, from);
        IOException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(Math.min(8_000L, 1_500L * attempt));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("stopped");
                }
            }
            HttpURLConnection h = null;
            try {
                h = open(o.host, o.port, name, token, 15_000);
                if (have > 0) h.setRequestProperty("Range", "bytes=" + have + "-");
                int code = h.getResponseCode();
                if (code == 416) {
                    // What is here does not fit what the TV has now: from the start.
                    if (!f.delete() && f.exists()) throw new IOException("the old download could not be let go");
                    have = 0;
                    continue;
                }
                if (code == 503) {
                    last = new IOException(o.tv + " is busy");
                    continue;
                }
                if (code == 401) throw new Refused("This phone is no longer paired with " + o.tv + ". Pair it again in Settings");
                if (code == 404 || code == 405) throw new Refused(o.tv + " no longer has Homefield " + o.name);
                if (code != 200 && code != 206) throw new Refused(o.tv + " said no (" + code + ")");
                boolean append = code == 206 && have > 0;
                if (!append) have = 0;
                long length = h.getContentLengthLong();
                long total = length > 0 ? have + length : whole ? o.size : 0;
                try (InputStream in = h.getInputStream();
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(f, append), 1 << 16)) {
                    byte[] buf = new byte[1 << 16];
                    for (int n; (n = in.read(buf)) > 0; ) {
                        out.write(buf, 0, n);
                        have += n;
                        if (total > 0) at(Stage.FETCHING, from + (to - from) * Math.min(1f, (float) have / total));
                    }
                }
                if (total > 0 && have < total) {
                    last = new IOException("the download broke off");
                    continue;
                }
                if (whole && have != o.size) {
                    if (!f.delete() && f.exists()) throw new IOException("a spoilt download could not be let go");
                    have = 0;
                    last = new IOException("it came out the wrong size");
                    continue;
                }
                return f;
            } catch (Refused e) {
                throw e;
            } catch (IOException e) {
                last = e;
                have = f.isFile() ? f.length() : 0;
            } finally {
                if (h != null) h.disconnect();
            }
        }
        throw new IOException("Couldn't fetch it from " + o.tv + (last != null && last.getMessage() != null ? " (" + last.getMessage() + ")" : ""));
    }

    /**
     * The new APK written into Android's installer (put together from the patch, or the whole), checked, committed. What
     * it is updating to is written down at once (commit, not apply): Android closes the app as the new version goes in.
     */
    @android.annotation.SuppressLint("ApplySharedPref")
    private static void install(Context c, Offer o, File f, boolean patch) throws IOException, NoSuchAlgorithmException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        sp.setSize(o.size);
        sp.setInstallReason(PackageManager.INSTALL_REASON_USER);
        // Updating itself: on Android 12 and later, once Homefield may install apps, Android need not ask again.
        if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        boolean committed = false;
        try {
            float base = patch ? 0.25f : 0.85f;
            stage(Stage.BUILDING, base);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long written;
            try (OutputStream raw = s.openWrite("base.apk", 0, o.size)) {
                DigestOutputStream out = new DigestOutputStream(new BufferedOutputStream(raw, 1 << 16), md);
                UpdatePatch.Progress p = (done, total) -> at(Stage.BUILDING, base + (0.97f - base) * done / Math.max(1L, total));
                try (InputStream in = new FileInputStream(f)) {
                    written = patch ? UpdatePatch.apply(new File(c.getApplicationInfo().sourceDir), in, out, p) : copy(in, o.size, out, p);
                }
                out.flush();
                s.fsync(raw);
            }
            String got = hex(md.digest());
            if (written != o.size || !got.equalsIgnoreCase(o.sha256)) {
                throw new Mismatch("it did not come out as it should (" + written + " bytes, " + got.substring(0, 12) + ")");
            }
            Log.i(TAG, "Homefield " + o.name + " put together (" + (patch ? "what changed" : "whole") + "), its fingerprint as it should be");
            prefs(c).edit().putInt("to", o.code).putString("to_name", o.name).putString("to_notes", o.notes)
                    .putLong("started", System.currentTimeMillis()).putBoolean("reopen", onScreen).commit();
            Intent i = new Intent(c, InstallStatus.class).setAction(ACTION_STATUS);
            PendingIntent done = PendingIntent.getBroadcast(c, id, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            stage(Stage.INSTALLING, 0.98f);
            s.commit(done.getIntentSender());
            committed = true;
            Log.i(TAG, "handed to Android's installer");
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

    private static HttpURLConnection open(String host, int port, String name, String token, int timeoutMs) throws IOException {
        String h = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + h + ":" + (port > 0 ? port : PORT) + "/phone/" + name).openConnection();
        c.setConnectTimeout(Math.min(timeoutMs, 5_000));
        c.setReadTimeout(timeoutMs);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "TVHome-phone/1.0");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("X-Token", token);
        return c;
    }

    private static String read(InputStream in, int max) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = s.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("too long");
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void stage(Stage s, float at) {
        stage = s;
        stageAt = SystemClock.elapsedRealtime();
        share = at;
        saidPercent = -1;
        changed();
    }

    /** How far it has got, to the screen at most every 150 ms and only when the whole percent changes. */
    private static void at(Stage s, float at) {
        stage = s;
        share = at;
        int pc = Math.round(at * 100);
        long now = SystemClock.uptimeMillis();
        if (pc == saidPercent || now - saidAt < 150L) return;
        saidPercent = pc;
        saidAt = now;
        changed();
    }

    /** Android asks first (before Android 12, or the first time): its question is up. */
    static void asked(Context c) {
        Log.i(TAG, "Android asks before installing");
        stage(Stage.ASKING, 0.98f);
    }

    /** It did not go in: why, on the screen and in the log. */
    static void failed(Context c, String why) {
        problem = why;
        prefs(c).edit().remove("to").apply();
        Log.w(TAG, "the update did not install: " + why);
        stage(Stage.IDLE, 0f);
    }

    /** In, without Android stopping the app (it normally does): the new version takes over at its next start. */
    static void installed(Context c) {
        Log.i(TAG, "installed");
        stage(Stage.IDLE, 0f);
    }

    /**
     * Android put the new version in (Updated, as the new version): what was fetched let go, a note that it is done (tap
     * to open), and, when it was updated from the app on the screen a moment ago and may open itself (it can show over
     * other apps), the app opened again.
     */
    static void replaced(Context c) {
        SharedPreferences p = prefs(c);
        int to = p.getInt("to", 0);
        String notes = p.getString("to_notes", "");
        boolean reopen = p.getBoolean("reopen", false) && System.currentTimeMillis() - p.getLong("started", 0) < 3 * 60_000L;
        // A new version in, however it came: the TVs looked at again when the app next opens.
        p.edit().remove("to").remove("to_name").remove("to_notes").remove("started").remove("reopen").remove("offer").remove("checked").apply();
        clear(c);
        if (to <= 0 || myCode(c) < to) return;
        String name = myName(c);
        Log.i(TAG, "updated to " + name);
        p.edit().putString("just", name).apply();
        channels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(c, DONE_ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, DONE)
                .setSmallIcon(R.drawable.ic_update)
                .setContentTitle("Homefield is updated to " + name)
                .setContentText(notes.isEmpty() ? "Tap to open it" : notes)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notes.isEmpty() ? "Tap to open it" : notes))
                .setColor(0xFF4F8BFF)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * 60_000L)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(tap);
        try {
            NotificationManagerCompat.from(c).notify(DONE_ID, b.build());
        } catch (SecurityException ignored) {
            // notifications not allowed on this phone
        }
        if (reopen && android.provider.Settings.canDrawOverlays(c)) {
            try {
                c.startActivity(open);
            } catch (RuntimeException ignored) {
                // the note stays
            }
        }
    }

    /**
     * From the background sync (SyncWorker, every 15 minutes): the TVs looked at every few hours, on that thread, and a
     * newer version said once, quietly (tap: the app's question).
     */
    public static void background(Context c) {
        if (busy() || stage == Stage.CHECKING || !due(System.currentTimeMillis(), lookedAt(c), BACKGROUND_EVERY_MS)) return;
        keep(c, look(c));
        changed();
        Offer o = ready(c);
        SharedPreferences p = prefs(c);
        if (o == null || onScreen || p.getInt("told", 0) == o.code) return;
        p.edit().putInt("told", o.code).apply();
        channels(c);
        Intent i = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_UPDATE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, READY_ID, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = (o.notes.isEmpty() ? "" : o.notes + " · ") + megabytes(o.bytesFor(myCode(c))) + " from " + o.tv;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, READY)
                .setSmallIcon(R.drawable.ic_update)
                .setContentTitle("Homefield " + o.name + " is ready")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setColor(0xFF4F8BFF)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(tap)
                .addAction(0, "Update", tap);
        try {
            NotificationManagerCompat.from(c).notify(READY_ID, b.build());
        } catch (SecurityException ignored) {
            // notifications not allowed on this phone
        }
    }

    private static void channels(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if (m == null) return;
        NotificationChannel ready = new NotificationChannel(READY, c.getString(R.string.update_channel_ready), NotificationManager.IMPORTANCE_LOW);
        ready.setDescription(c.getString(R.string.update_channel_ready_about));
        m.createNotificationChannel(ready);
        NotificationChannel done = new NotificationChannel(DONE, c.getString(R.string.update_channel_done), NotificationManager.IMPORTANCE_HIGH);
        done.setDescription(c.getString(R.string.update_channel_done_about));
        m.createNotificationChannel(done);
    }
}
