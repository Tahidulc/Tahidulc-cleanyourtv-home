package com.gathin.homefieldtv.engine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import android.util.JsonToken;

import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * New channel logos (Settings › Appearance › Logos: New logos (Homefield), as it comes, or the playlist's): the
 * tv-logo project's free logos for dark grounds (github.com/tv-logo/tv-logos), for display in this private family app
 * only. Nothing of them is in the app: the TV fetches each logo it draws from raw.githubusercontent.com, with Homefield's
 * plain User-Agent and nothing about the family, and keeps it with the other pictures (Pics: on disk, and in memory
 * at the size it is drawn, within their budget).
 *
 * <p>Which logo is a channel's comes from an index of the project's logos' names (LogoMatch): GitHub's listing of the
 * project's files, asked for once a week (its API, unsigned, with the answer's ETag so an unchanged list costs
 * nothing) and kept on the TV as compact text; until the first one arrives, the small index the app comes with (the
 * United Kingdom's, Ireland's and the international logos' names). Only the countries the playlist has are read into
 * memory. A channel's match is worked out the first time it is drawn (a few lookups, no list gone through) and kept;
 * a new logo is shown only when the match is sure (LogoMatch.SURE), else the playlist's. A channel's own choice (Change
 * logo in its menu: one of the logos found, or the playlist's) comes first, whatever the setting. A logo that can't be
 * read gives the playlist's.
 */
public final class LogoPack {
    private LogoPack() {}

    /** The project's files as they are now. */
    public static final String RAW = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/";
    /** The project's files listed by GitHub's API: one call a week. */
    static final String TREE = "https://api.github.com/repos/tv-logo/tv-logos/git/trees/main?recursive=1";
    static final long WEEK_MS = 7 * 86_400_000L;
    static final String PREFS = "logos", ON_KEY = "logo_pack", BUST = "bust", AT = "fetched_at", ETAG = "etag", COUNT = "count";
    static final String ASSET = "logos/fallback.txt";

    /** Previews and tests: where the logos are read from instead (a folder of drawn ones: "file:/…/"). */
    public static volatile String base = RAW;
    /** Whether a picture could not be read (the app's pictures say so): its playlist's logo then. */
    public static volatile Predicate<String> failed = u -> false;

    /** The application's context, never an activity's (init). */
    @SuppressLint("StaticFieldLeak")
    private static volatile Context app;
    private static volatile LogoMatch match;
    private static volatile Set<String> countries = Collections.emptySet();
    /** Each channel's automatic logo, by its key ("" for none sure enough), worked out as it is first drawn. */
    private static final Map<String, String> best = new ConcurrentHashMap<>();
    /** Each logo's address by its path, made once (nothing made each frame a row is drawn), for the base they were made on. */
    private static final Map<String, String> addresses = new ConcurrentHashMap<>();
    private static volatile String addressesBase = RAW;
    /** Robolectric (the tests): no logo fetched, unless a preview gives its own folder. */
    private static final boolean TESTS = "robolectric".equals(android.os.Build.FINGERPRINT);
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean reading = new AtomicBoolean(), fetching = new AtomicBoolean();
    private static volatile boolean on = true;
    private static volatile int bust;
    private static volatile String problem = "";

    /** When the index was fetched, its ETag and size, and the logos' refresh count: this TV's own (not in a backup). */
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The setting, with Appearance's others ("look": in a backup). */
    private static SharedPreferences look(Context c) {
        return c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE);
    }

    /** The app starting: the setting read, the index read off the main thread, and asked for again when a week old. */
    public static void init(Context c) {
        on = look(c).getBoolean(ON_KEY, true);
        if (app != null) return;
        app = c.getApplicationContext();
        SharedPreferences p = prefs(app);
        bust = p.getInt(BUST, 0);
        Library.get(app).addListener(LogoPack::channelsChanged);
        Exec.io(() -> {
            read(app);
            if (System.currentTimeMillis() - prefs(app).getLong(AT, 0) > WEEK_MS) fetch(app);
        });
    }

    // ---- what the screens draw ------------------------------------------------------------------------------------

    /** The logo to draw for a channel: its own choice, else the new one when sure (and the setting says so), else the playlist's. */
    public static String url(Channel ch) {
        if (ch == null) return "";
        // Its own choice (one row per channel: or one of its versions').
        String choice = ChannelEdits.logo(ch);
        String path;
        if (ChannelEdits.LOGO_PLAYLIST.equals(choice)) return ch.logo;
        if (!choice.isEmpty()) path = choice;
        else if (!on || offline()) return ch.logo;
        else path = auto(ch);
        if (path == null || path.isEmpty()) return ch.logo;
        String u = urlOf(path);
        return failed.test(u) ? ch.logo : u;
    }

    /** A logo's address: the project's file (made new by Refresh logos); made once a path. */
    public static String urlOf(String path) {
        String b = base;
        if (!b.equals(addressesBase)) {
            // Another base (a preview's folder, or back to the project's): the addresses made on the old one go.
            addresses.clear();
            addressesBase = b;
        }
        String u = addresses.get(path);
        if (u == null) {
            u = b + path + (bust > 0 && b.startsWith("http") ? "?v=" + bust : "");
            addresses.put(path, u);
        }
        return u;
    }

    /**
     * The white version of a new logo by its address (a logo too dark to see on the dark ground: tv-logos' own light
     * version, where it has one), or null: not one of the project's logos, none white, or the list not read yet. It goes
     * through the list's logos, so it is asked off the main thread (the pictures' readers ask it).
     */
    public static String lighter(String url) {
        LogoMatch m = match;
        String b = base;
        if (m == null || url == null || !url.startsWith(b)) return null;
        String path = url.substring(b.length());
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        String white = m.whiteOf(path);
        return white == null ? null : urlOf(white);
    }

    /** The channel's automatic logo's path (sure enough), or null; worked out once (a few lookups). */
    public static String auto(Channel ch) {
        LogoMatch m = match;
        if (m == null || ch == null) return null;
        String got = best.get(ch.key);
        if (got == null) {
            LogoMatch.Found f = null;
            // The family's own name for it first (it may say more than the playlist's), then the playlist's.
            if (ChannelEdits.renamed(ch)) f = m.best(ChannelEdits.name(ch), ch.group);
            if (f == null) f = m.best(ch.name, ch.group);
            got = f == null ? "" : f.logo.path;
            best.put(ch.key, got);
        }
        return got.isEmpty() ? null : got;
    }

    /** Change logo: every logo that may be the channel's, the best first (off the main thread: it reads more). */
    public static List<LogoMatch.Found> candidates(Channel ch, int max) {
        LogoMatch m = match;
        if (m == null || ch == null) return new ArrayList<>();
        List<LogoMatch.Found> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        if (ChannelEdits.renamed(ch)) for (LogoMatch.Found f : m.candidates(ChannelEdits.name(ch), ch.group, max)) if (seen.add(f.logo.path)) out.add(f);
        for (LogoMatch.Found f : m.candidates(ch.name, ch.group, max)) if (seen.add(f.logo.path)) out.add(f);
        out.sort((a, b) -> Float.compare(b.sure, a.sure));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Settings › Appearance › Logos: New logos (Homefield), as it comes; off, the playlist's logos. */
    public static boolean on() {
        return on;
    }

    public static void setOn(Context c, boolean v) {
        on = v;
        look(c).edit().putBoolean(ON_KEY, v).apply();
        changed();
    }

    /** Refresh logos: the index asked for now, every logo fetched again as it is next drawn. */
    public static void refresh(Context c) {
        Context a = c.getApplicationContext();
        bust++;
        prefs(a).edit().putInt(BUST, bust).apply();
        best.clear();
        addresses.clear();
        changed();
        Exec.io(() -> fetch(a));
    }

    /** What Settings › Logos says of the index: how many logos, and from when (or why not). */
    public static String status(Context c) {
        if (fetching.get()) return "Asking for the list of logos…";
        SharedPreferences p = prefs(c);
        long at = p.getLong(AT, 0);
        LogoMatch m = match;
        int count = p.getInt(COUNT, 0);
        String words;
        if (at > 0 && count > 0) {
            words = String.format(Locale.UK, "%,d logos  ·  list from %s", count,
                    new java.text.SimpleDateFormat("d MMM yyyy", Locale.UK).format(new java.util.Date(at)));
        } else {
            words = m == null ? "Reading the list of logos…" : String.format(Locale.UK, "%,d logos from the list the app came with", m.size());
        }
        return problem.isEmpty() ? words : words + "  ·  " + problem;
    }

    // ---- listeners ------------------------------------------------------------------------------------------------

    /** Told (on the main thread) when the logos to draw may have changed: the screens draw again. */
    public static void addListener(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    public static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    /** Something changed what a channel's logo is (a choice, the setting, the index). */
    public static void changed() {
        for (Runnable r : listeners) Exec.main(r);
    }

    // ---- the index ------------------------------------------------------------------------------------------------

    private static File file(Context c) {
        File dir = new File(c.getFilesDir(), "logos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "index.txt");
    }

    /** No logos fetched in tests (Robolectric): the playlists' own, unless a preview gives its own folder. */
    static boolean offline() {
        return TESTS && RAW.equals(base);
    }

    /** The index read (the one fetched last, else the app's own) for the countries the playlist has. Off the main thread. */
    private static void read(Context c) {
        if (!reading.compareAndSet(false, true)) return;
        try {
            String text = null;
            File f = file(c);
            if (f.exists()) text = text(new FileInputStream(f));
            if (text == null || text.isEmpty()) text = text(c.getAssets().open(ASSET));
            Library lib = Library.get(c);
            Set<String> want = LogoMatch.countries(lib.groups(), names(lib.every()), 50_000);
            LogoMatch m = LogoMatch.parse(text, want);
            countries = want;
            match = m;
            best.clear();
        } catch (IOException | RuntimeException e) {
            // No index: the playlists' own logos.
        } finally {
            reading.set(false);
        }
        changed();
    }

    /** The channels last looked at for their countries. */
    private static volatile List<Channel> looked;

    /** The channels read again (a new load only): the index read for them when they bring a country it hasn't. */
    private static void channelsChanged() {
        Context c = app;
        if (c == null || match == null) return;
        List<Channel> every = Library.get(c).every();
        if (every == looked) return;
        looked = every;
        Exec.work(() -> {
            Library lib = Library.get(c);
            Set<String> want = LogoMatch.countries(lib.groups(), names(every), 50_000);
            if (!countries.containsAll(want)) Exec.io(() -> read(c));
        });
    }

    /** A channel's automatic logo worked out again (the family renamed it). */
    public static void forget(String key) {
        if (key != null) best.remove(key);
    }

    private static Iterable<String> names(List<Channel> channels) {
        List<String> out = new ArrayList<>(channels.size());
        for (Channel ch : channels) out.add(ch.name);
        return out;
    }

    /**
     * The project's files asked for (GitHub's listing: Homefield's own User-Agent, no account, nothing about the family),
     * kept as the compact index, then read. Off the main thread; an answer that doesn't come keeps the one there is.
     */
    static void fetch(Context c) {
        if (offline() || !base.equals(RAW) || !fetching.compareAndSet(false, true)) return;
        changed();
        HttpURLConnection h = null;
        try {
            SharedPreferences p = prefs(c);
            h = (HttpURLConnection) new URL(TREE).openConnection();
            h.setConnectTimeout(15_000);
            h.setReadTimeout(60_000);
            h.setRequestProperty("User-Agent", Fetch.AGENT);
            h.setRequestProperty("Accept", "application/vnd.github+json");
            String etag = p.getString(ETAG, "");
            if (!etag.isEmpty() && file(c).exists()) h.setRequestProperty("If-None-Match", etag);
            int code = h.getResponseCode();
            if (code == 304) {
                p.edit().putLong(AT, System.currentTimeMillis()).apply();
                problem = "";
                return;
            }
            if (code != 200) {
                problem = code == 403 || code == 429 ? "GitHub asked to wait; tried again later" : "GitHub answered " + code;
                return;
            }
            List<String> paths = new ArrayList<>();
            try (InputStream in = h.getInputStream(); JsonReader jr = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                jr.beginObject();
                while (jr.hasNext()) {
                    if (!"tree".equals(jr.nextName()) || jr.peek() != JsonToken.BEGIN_ARRAY) {
                        jr.skipValue();
                        continue;
                    }
                    jr.beginArray();
                    while (jr.hasNext()) {
                        String path = null, type = null;
                        jr.beginObject();
                        while (jr.hasNext()) {
                            String k = jr.nextName();
                            if ("path".equals(k) && jr.peek() == JsonToken.STRING) path = jr.nextString();
                            else if ("type".equals(k) && jr.peek() == JsonToken.STRING) type = jr.nextString();
                            else jr.skipValue();
                        }
                        jr.endObject();
                        if (path != null && "blob".equals(type)) paths.add(path);
                    }
                    jr.endArray();
                }
                jr.endObject();
            }
            String text = LogoMatch.compact(paths, "tv-logo/tv-logos, as GitHub listed it " + new java.text.SimpleDateFormat("d MMM yyyy",
                    Locale.UK).format(new java.util.Date()) + ": the logos' file names only");
            LogoMatch all = LogoMatch.parse(text, null);
            if (all.size() < 100) {
                problem = "the list was too short";
                return;
            }
            File f = file(c), tmp = new File(f.getPath() + ".part");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(text.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return;
            }
            String tag = h.getHeaderField("ETag");
            p.edit().putLong(AT, System.currentTimeMillis()).putString(ETAG, tag == null ? "" : tag).putInt(COUNT, all.size()).apply();
            problem = "";
        } catch (IOException | RuntimeException e) {
            // Never the address in the log: there is nothing secret in it, but nothing is logged of fetches.
            problem = "not reached; tried again later";
        } finally {
            if (h != null) h.disconnect();
            fetching.set(false);
            changed();
        }
        read(c);
    }

    private static String text(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = i.read(buf)) > 0; ) {
                b.write(buf, 0, n);
                if (b.size() > 4 << 20) break;
            }
            return b.toString("UTF-8");
        }
    }

    // ---- previews and tests -------------------------------------------------------------------------------------------

    /** Previews and tests: an index given straight in (its logos read from base), every match worked out afresh. */
    public static void useForPreview(String indexText) {
        match = LogoMatch.parse(indexText, null);
        best.clear();
        addresses.clear();
        changed();
    }
}
