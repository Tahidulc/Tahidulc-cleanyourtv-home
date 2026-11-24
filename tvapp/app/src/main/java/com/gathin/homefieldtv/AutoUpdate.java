package com.gathin.homefieldtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.StatFs;

import com.gathin.homefieldtv.engine.Bounded;
import com.gathin.homefieldtv.engine.Exec;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Settings › About › Updates › Automatic updates: Homefield TV fetches its own update from an address the family gives
 * (Update address: an update.json in the phone shelf's form, {"code","name","notes","size","sha256"} with where its APK is:
 * "apk" or "url", absolute or beside the update.json, else "file" beside it), every time it opens, and puts it in its
 * update folder (files/update/) as a computer would over ADB. From there Updates checks it as it always has (its size,
 * SHA-256, package, version and signing key) and Update now hands it to Android, which asks first; where Android already
 * lets Homefield TV install, it is handed over at once as the app opens. Off (as it comes): updates only from a
 * computer (ADB).
 *
 * <p>The APK is streamed straight to a file beside the folder's (its SHA-256 worked out as it comes), never held in
 * memory, and put in place only when it is whole and right. What stood in the way is said in Settings in the family's
 * words: the address can't be reached, answered HTTP n, isn't a web address, gave something that isn't an update, has
 * nothing yet (or the repository is private); the update doesn't say where its APK is, is too big for this TV, or this TV
 * has no room for it or couldn't keep it; it stopped coming, or didn't arrive whole (tried again the next time).
 */
final class AutoUpdate {
    private AutoUpdate() {}

    static final int OFF = 0, ON_OPEN = 1;
    static final String[] NAMES = {"Off", "Every time it opens"};
    static final String[] ABOUT = {"Off: updates only from a computer (ADB)", "Looks at the update address each time Homefield TV opens"};
    /** The biggest update taken (Homefield TV is about 30 MB), the room left on the TV after it, the update.json's most. */
    static final long MAX_APK = 160L << 20, ROOM = 64L << 20;
    static final int MAX_JSON = 64_000;
    /** Looked again on coming back only after this long (not at every return from another app). */
    static final long AGAIN_MS = 10 * 60_000L;

    private static volatile boolean fetching;
    private static volatile String state = "";

    static SharedPreferences prefs(Context c) {
        return Updates.prefs(c);
    }

    static int mode(Context c) {
        return prefs(c).getInt("auto", OFF) == ON_OPEN ? ON_OPEN : OFF;
    }

    static void setMode(Context c, int m) {
        prefs(c).edit().putInt("auto", m == ON_OPEN ? ON_OPEN : OFF).apply();
    }

    static String address(Context c) {
        return prefs(c).getString("address", "");
    }

    static void setAddress(Context c, String a) {
        prefs(c).edit().putString("address", a == null ? "" : a.trim()).remove("auto_problem").apply();
    }

    /** What the last look said went wrong ("" for nothing), and what it is doing now ("" for nothing). */
    static String problem(Context c) {
        return prefs(c).getString("auto_problem", "");
    }

    static String state() {
        return fetching ? state : "";
    }

    /** "TV 3.1 of 8.0 GB free": the room where the update goes. */
    static String room(Context c) {
        File dir = Updates.folder(c);
        if (dir == null) return "";
        try {
            StatFs s = new StatFs(dir.getPath());
            return String.format(Locale.UK, "TV %.1f of %.1f GB free", s.getAvailableBytes() / 1e9, s.getTotalBytes() / 1e9);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * The app opened (fresh, or come back to): the address looked at when set to, and due; a newer Homefield TV that came
     * is handed to Android at once where it lets Homefield TV install (Android asks first), else waits in Settings.
     */
    static void opened(TvActivity a, boolean fresh) {
        Context app = a.getApplicationContext();
        if (mode(app) != ON_OPEN || address(app).isEmpty() || fetching) return;
        long last = prefs(app).getLong("auto_at", 0), now = System.currentTimeMillis();
        if (!fresh && now - last < AGAIN_MS && now >= last) return;
        check(app, () -> {
            if (!a.isFinishing() && Updates.ready(app) != null && Updates.allowed(app)) Updates.update(a);
        });
    }

    /** Looked at now (Settings' Check now, or as it opens); after, on the main thread. */
    static void check(Context c, Runnable after) {
        Context app = c.getApplicationContext();
        if (fetching) return;
        fetching = true;
        state = "Looking at the update address…";
        prefs(app).edit().putLong("auto_at", System.currentTimeMillis()).apply();
        Exec.io(() -> {
            String why = "";
            try {
                fetch(app);
            } catch (IOException e) {
                why = e.getMessage() == null ? "Couldn't reach the update address" : e.getMessage();
            } finally {
                fetching = false;
                state = "";
            }
            prefs(app).edit().putString("auto_problem", why).apply();
            // What came checked as an update put there by a computer is (Updates), and Settings told.
            Updates.check(app, after);
        });
    }

    // ---- pure ------------------------------------------------------------------------------------------------------

    /** Pure: an address the TV can ask (http or https, a host), or null. */
    static URI web(String address) {
        if (address == null) return null;
        try {
            URI u = new URI(address.trim());
            String s = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            return (s.equals("http") || s.equals("https")) && u.getHost() != null && !u.getHost().isEmpty() ? u : null;
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    /** Pure: the family's words for an address's HTTP answer that isn't an update. */
    static String httpProblem(int code) {
        if (code == 404 || code == 401 || code == 403) return "Nothing at the update address yet (or the repository is private)";
        return "The update address answered HTTP " + code;
    }

    /**
     * Pure: where an update.json (at address) says its APK is, and the plain name it is kept under in the update folder:
     * {address, name}; null when it doesn't say ("apk" or "url", else "file" beside it).
     */
    static String[] apkOf(URI address, JSONObject j) {
        String where = j.optString("apk", "").trim();
        if (where.isEmpty()) where = j.optString("url", "").trim();
        if (where.isEmpty()) where = j.optString("file", "").trim();
        if (where.isEmpty()) return null;
        URI apk;
        try {
            apk = address.resolve(where);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (web(apk.toString()) == null) return null;
        String file = j.optString("file", "").trim();
        if (!Updates.plainName(file) || !file.endsWith(".apk")) {
            String path = apk.getPath() == null ? "" : apk.getPath();
            String last = path.substring(path.lastIndexOf('/') + 1);
            file = Updates.plainName(last) && last.endsWith(".apk") ? last : "homefieldtv-" + j.optInt("code") + ".apk";
        }
        return new String[]{apk.toString(), file};
    }

    // ---- fetching (off the main thread) ---------------------------------------------------------------------------

    private static void fetch(Context app) throws IOException {
        URI address = web(address(app));
        if (address == null) throw new IOException("The update address isn't a web address");
        String body;
        HttpURLConnection h = open(address.toString());
        try {
            int code = answer(h);
            if (code != 200) throw new IOException(httpProblem(code));
            try (InputStream in = h.getInputStream()) {
                body = Bounded.text(in, h.getContentLengthLong(), MAX_JSON);
            } catch (IOException e) {
                throw new IOException("The update address gave something that isn't an update");
            }
        } finally {
            h.disconnect();
        }
        JSONObject j;
        try {
            j = new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("The update address gave something that isn't an update");
        }
        int code = j.optInt("code");
        String name = j.optString("name").trim(), sha = j.optString("sha256").toLowerCase(Locale.ROOT);
        long size = j.optLong("size");
        if (code <= 0 || name.isEmpty() || size <= 0 || !Updates.hex64(sha)) {
            throw new IOException("The update address gave something that isn't an update");
        }
        String[] apk = apkOf(address, j);
        if (apk == null) throw new IOException("The update doesn't say where its APK is");
        // Not newer than this one: nothing to fetch.
        if (code <= Updates.myCode(app)) return;
        if (size > MAX_APK) throw new IOException("The update is too big for this TV");
        File dir = Updates.folder(app);
        if (dir == null) throw new IOException("This TV couldn't keep the update");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, apk[1]);
        if (!(file.isFile() && file.length() == size && sha.equals(sha256(file)))) {
            if (dir.getUsableSpace() < size + ROOM) throw new IOException("This TV has no room for the update");
            download(apk[0], file, size, sha, name);
        }
        // Its note beside it, as a computer would put it there: Updates checks it all again from here.
        JSONObject note = new JSONObject();
        try {
            note.put("code", code).put("name", name).put("notes", j.optString("notes", "")).put("file", apk[1]).put("size", size)
                    .put("sha256", sha);
        } catch (JSONException e) {
            throw new IOException("This TV couldn't keep the update");
        }
        File tmp = new File(dir, "update.json.part");
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write(note.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("This TV couldn't keep the update");
        }
        if (!tmp.renameTo(new File(dir, "update.json"))) throw new IOException("This TV couldn't keep the update");
    }

    /** The APK streamed to a file beside it, its SHA-256 as it comes, put in place only when whole and right. */
    private static void download(String url, File to, long size, String sha, String name) throws IOException {
        File part = new File(to.getPath() + ".part");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("This TV couldn't keep the update");
        }
        HttpURLConnection h = open(url);
        long got = 0;
        try {
            int code = answer(h);
            if (code != 200) throw new IOException(httpProblem(code));
            long told = h.getContentLengthLong();
            if (told > MAX_APK) throw new IOException("The update is too big for this TV");
            state = "Fetching Homefield TV " + name + "…";
            try (InputStream in = h.getInputStream(); OutputStream out = new BufferedOutputStream(new FileOutputStream(part), 1 << 16)) {
                byte[] buf = new byte[1 << 16];
                for (int n; ; ) {
                    try {
                        n = in.read(buf);
                    } catch (IOException e) {
                        throw new IOException("The update stopped coming: it is tried again later");
                    }
                    if (n <= 0) break;
                    got += n;
                    if (got > size) break;
                    md.update(buf, 0, n);
                    try {
                        out.write(buf, 0, n);
                    } catch (IOException e) {
                        throw new IOException("This TV couldn't keep the update");
                    }
                }
            } catch (java.io.FileNotFoundException e) {
                throw new IOException("This TV couldn't keep the update");
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        } finally {
            h.disconnect();
        }
        if (got != size || !sha.equals(Updates.hex(md.digest()))) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException(got < size ? "The update stopped coming: it is tried again later"
                    : "Homefield TV " + name + " didn't arrive whole: it is tried again later");
        }
        //noinspection ResultOfMethodCallIgnored
        to.delete();
        if (!part.renameTo(to)) throw new IOException("This TV couldn't keep the update");
    }

    private static HttpURLConnection open(String url) throws IOException {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(15_000);
            h.setReadTimeout(30_000);
            h.setInstanceFollowRedirects(true);
            h.setRequestProperty("User-Agent", "HomefieldTV/1.0");
            return h;
        } catch (IOException | IllegalArgumentException | ClassCastException e) {
            throw new IOException("The update address isn't a web address");
        }
    }

    private static int answer(HttpURLConnection h) throws IOException {
        try {
            return h.getResponseCode();
        } catch (IOException e) {
            throw new IOException("Couldn't reach the update address");
        }
    }

    private static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            return Updates.hex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }
}
