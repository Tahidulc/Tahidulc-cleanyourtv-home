package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What has been watched in any app, and how far: the films and episodes playing in Netflix, Prime Video, Disney+,
 * Debrid Stream's player and the rest, read from Android's media sessions (title, length, position) every half
 * minute while they play. The TV's own Watch Next list is open only to the system's launcher; media sessions are open
 * to a notification listener, switched on once over ADB (cmd notification allow_listener
 * com.gathin.tvhome/com.gathin.tvhome.WatchHistory). Kept in watching.json in the app's files: the newest 60, each
 * matched to TMDB later (by Home, which has the key) so Movies and TV shows can show it with its poster.
 */
public class WatchHistory extends NotificationListenerService {
    /** Music, live TV and the launcher itself are not "continue watching". */
    private static final String[] SKIP = {"com.spotify", "music", "com.gathin.tvhome", "ar.tvplayer.tv", "bluetooth", "com.google.android.youtube"};
    /** Shorter than this is a clip or a song, not a film or an episode. */
    private static final long MIN_MS = 15 * 60_000L;
    /** Sport is kept from five minutes: a match's highlights, a race's replay in parts. */
    private static final long MIN_SPORT_MS = 5 * 60_000L;
    /** The apps that are all sport (Sky Sports, TNT Sports, DAZN, F1 TV, UEFA.tv and the like), by a part of their name. */
    private static final String[] SPORT_APPS = {"sport", "dazn", "formula", "f1tv", "uefa", "premierleague", "ufc", "nba", "nfl", "tnt"};
    /** A title that is sport whatever app plays it. */
    private static final java.util.regex.Pattern SPORT_WORDS = java.util.regex.Pattern.compile(
            "(?i).*(grand prix|highlights|match of the day|premier league|champions league|europa league|"
                    + "fa cup|carabao|qualifying|sprint race|\\bf1\\b|formula 1|ufc \\d|fight night|boxing|wimbledon|"
                    + "\\bnfl\\b|\\bnba\\b|super bowl|six nations|the ashes).*");
    private static final long TICK_MS = 30_000L;
    private static final int KEEP = 60;

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager sessions;
    private List<MediaController> controllers = new ArrayList<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = list -> controllers = list == null ? new ArrayList<>() : list;
    private final Runnable tick = this::tick;

    @Override public void onListenerConnected() {
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, WatchHistory.class);
        try {
            sessions.addOnActiveSessionsChangedListener(changed, me, main);
            controllers = sessions.getActiveSessions(me);
        } catch (SecurityException e) {
            return;   // not allowed as a listener yet
        }
        main.removeCallbacks(tick);
        main.post(tick);
    }

    /** Tuya Smart's notifications: a camera's alarm shows over whatever is on (CameraAlert). */
    @Override public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        if (sbn == null) return;
        if ("com.android.shell".equals(sbn.getPackageName()) && "mouse-test".equals(sbn.getTag())) {
            CharSequence t = sbn.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
            if (t != null) Mouse.test(this, t.toString());
            return;
        }
        // The doorbell's app (CloudEdge), and a stand-in from adb (cmd notification post ... doorbell-test ...).
        boolean bellTest = "com.android.shell".equals(sbn.getPackageName()) && "doorbell-test".equals(sbn.getTag());
        if (bellTest || DoorbellAlert.app(this).equals(sbn.getPackageName())) {
            try {
                DoorbellAlert.posted(this, sbn);
            } catch (RuntimeException e) {
                android.util.Log.w("Doorbell", "alert: " + e);
            }
            return;
        }
        // Tuya Smart's, and a stand-in posted from adb to check the card (cmd notification post ... camera-test ...).
        boolean test = "com.android.shell".equals(sbn.getPackageName()) && "camera-test".equals(sbn.getTag());
        if (!test && !CameraAlert.TUYA.equals(sbn.getPackageName())) return;
        try {
            CameraAlert.posted(this, sbn);
        } catch (RuntimeException e) {
            android.util.Log.w("Camera", "alert: " + e);
        }
    }

    @Override public void onListenerDisconnected() {
        main.removeCallbacks(tick);
        ViewingLog.save(this);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
    }

    /** The media sessions playing now (SleepAfter watches the one it was asked about). */
    List<MediaController> controllers() {
        return controllers;
    }

    private void tick() {
        for (MediaController c : controllers) {
            try {
                record(c);
            } catch (Exception ignored) {
            }
            try {
                log(c);
            } catch (Exception ignored) {
            }
        }
        try {
            SleepAfter.tick(this, controllers);
        } catch (Exception ignored) {
        }
        main.postDelayed(tick, TICK_MS);
    }

    /** Your viewing's log (ViewingLog): half a minute more of whatever is playing, music and the launcher aside. */
    private void log(MediaController c) {
        String pkg = c.getPackageName();
        if (pkg.contains("com.gathin.tvhome") || pkg.contains("spotify") || pkg.contains("music") || pkg.contains("bluetooth")) return;
        PlaybackState st = c.getPlaybackState();
        if (st == null || st.getState() != PlaybackState.STATE_PLAYING) return;
        MediaMetadata md = c.getMetadata();
        String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if ((title == null || title.trim().isEmpty()) && md != null) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String sub = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (sub == null && md != null) sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        title = title == null ? "" : title.trim();
        sub = sub == null ? "" : sub.trim();
        Object[] id = ViewingLog.identify(this, pkg, title, sub);
        ViewingLog.add(this, pkg, (String) id[0], (String) id[1], (Integer) id[2], (Boolean) id[3], (String) id[4], (int) (TICK_MS / 1000));
    }

    private void record(MediaController c) {
        String pkg = c.getPackageName();
        for (String s : SKIP) if (pkg.contains(s)) return;
        PlaybackState st = c.getPlaybackState();
        MediaMetadata md = c.getMetadata();
        if (st == null || md == null || st.getState() != PlaybackState.STATE_PLAYING) return;
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.trim().isEmpty()) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (title == null || title.trim().isEmpty()) return;
        String sub = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (sub == null) sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        sub = sub == null ? "" : sub.trim();
        boolean sport = sport(pkg, title + " " + sub);
        if (dur < (sport ? MIN_SPORT_MS : MIN_MS)) return;
        // The app's own picture for it, where it gives one as a web address.
        String art = null;
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
            String u = md.getString(k);
            if (u != null && u.startsWith("http")) { art = u; break; }
        }
        long pos = st.getPosition() + (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        save(this, pkg, title.trim(), sub, Math.max(0, Math.min(dur, pos)), dur, sport, art);
    }

    static boolean sport(String pkg, String words) {
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String s : SPORT_APPS) if (p.contains(s)) return true;
        return SPORT_WORDS.matcher(words).matches();
    }

    // ---- the store ---------------------------------------------------------------------------------------------

    static final class Entry {
        String pkg, title, sub, show, poster, backdrop, overview, year, art;
        long pos, dur, at;
        int tmdbId;   // 0 not looked up yet, -1 not found (or sport, never looked up)
        boolean tv, sport;

        float progress() {
            return dur <= 0 ? 0f : Math.min(1f, pos / (float) dur);
        }
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "watching.json");
    }

    private static List<Entry> cache;
    private static long cacheAt;

    static synchronized List<Entry> load(Context c) {
        File f = file(c);
        if (cache != null && f.lastModified() == cacheAt) return cache;
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.pkg = o.optString("pkg");
                e.title = o.optString("title");
                e.sub = o.optString("sub");
                e.show = o.optString("show", null);
                e.pos = o.optLong("pos");
                e.dur = o.optLong("dur");
                e.at = o.optLong("at");
                e.tmdbId = o.optInt("tmdb");
                e.tv = o.optBoolean("tv");
                e.poster = o.optString("poster", null);
                e.backdrop = o.optString("backdrop", null);
                e.overview = o.optString("overview", null);
                e.year = o.optString("year", null);
                e.art = o.optString("art", null);
                e.sport = o.optBoolean("sport");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        cache = out;
        cacheAt = f.lastModified();
        return out;
    }

    static synchronized void store(Context c, List<Entry> list) {
        JSONArray a = new JSONArray();
        try {
            for (int i = 0; i < Math.min(KEEP, list.size()); i++) {
                Entry e = list.get(i);
                JSONObject o = new JSONObject();
                o.put("pkg", e.pkg).put("title", e.title).put("sub", e.sub).put("pos", e.pos).put("dur", e.dur).put("at", e.at)
                        .put("tmdb", e.tmdbId).put("tv", e.tv);
                if (e.show != null) o.put("show", e.show);
                if (e.poster != null) o.put("poster", e.poster);
                if (e.backdrop != null) o.put("backdrop", e.backdrop);
                if (e.overview != null) o.put("overview", e.overview);
                if (e.year != null) o.put("year", e.year);
                if (e.art != null) o.put("art", e.art);
                if (e.sport) o.put("sport", true);
                a.put(o);
            }
            File f = file(c);
            File tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) Files.write(f.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        cache = null;
    }

    /** Where this film or episode is up to, newest first; the same title again replaces its old place. */
    private static synchronized void save(Context c, String pkg, String title, String sub, long pos, long dur, boolean sport, String art) {
        List<Entry> list = new ArrayList<>(load(c));
        Entry e = null;
        for (Entry x : list) if (x.pkg.equals(pkg) && x.title.equals(title) && x.sub.equals(sub)) { e = x; break; }
        if (e == null) {
            e = new Entry();
            e.pkg = pkg;
            e.title = title;
            e.sub = sub;
            android.util.Log.i("WatchHistory", "watching in " + pkg + (sport ? " (sport)" : ""));
        } else {
            list.remove(e);
        }
        e.sport = sport;
        // Sport is not a film or series: never looked up on TMDB.
        if (sport && e.tmdbId == 0) e.tmdbId = -1;
        if (art != null) e.art = art;
        e.pos = pos;
        e.dur = dur;
        e.at = System.currentTimeMillis();
        list.add(0, e);
        store(c, list);
    }

    /** adb only (through GoalWatchService's test extras): a sample entry, or (pkg null) all of them cleared. */
    static void test(Context c, String pkg, String title, String sub, float share, long dur) {
        if (pkg == null) { store(c, new ArrayList<>()); return; }
        save(c, pkg, title, sub, (long) (dur * share), dur, sport(pkg, title + " " + sub), null);
    }

    /** "Stranger Things: S4:E3 The Monster" or a subtitle like "S4 E3": the series and that it is one. */
    private static boolean episode(Entry e) {
        String all = (e.title + " " + e.sub).toLowerCase(Locale.ROOT);
        return all.matches(".*\\b(s\\d+\\s*:?\\s*e\\d+|season \\d+|episode \\d+|series \\d+|ep\\.? ?\\d+)\\b.*") || e.dur < 75 * 60_000L;
    }

    /** The name to look up: the title before an episode's part ("Show: S1:E2 …" to "Show"). */
    private static String searchName(Entry e) {
        String t = e.title;
        String low = t.toLowerCase(Locale.ROOT);
        int cut = low.indexOf(": s");
        if (cut < 0) cut = low.indexOf(" - s");
        if (cut > 0 && low.substring(cut).matches(".*s\\d+.*")) t = t.substring(0, cut);
        // A subtitle that is the series name (some apps put the episode in the title).
        if (e.sub != null && !e.sub.isEmpty() && episode(e) && !e.sub.toLowerCase(Locale.ROOT).matches(".*\\b(s\\d+|season|episode)\\b.*")
                && e.sub.length() < 60) {
            return e.sub;
        }
        return t;
    }

    /** Looks each new entry up on TMDB (its id, poster and picture), off the main thread; once per entry. */
    static void match(Context c, String key) {
        if (key == null || key.isEmpty()) return;
        // Looked up on copies, then written onto the list as it is by then: what was watched (or cleared) during
        // the lookups is kept, not overwritten by the list as it was when they began.
        List<Entry> list = new ArrayList<>();
        for (Entry x : load(c)) if (x.tmdbId == 0) list.add(copy(x));
        boolean changed = false;
        for (Entry e : list) {
            boolean tv = episode(e);
            String name = searchName(e);
            List<PagesV2.Title> got = new ArrayList<>();
            try {
                got = PagesV2.tmdb(key, "search/" + (tv ? "tv" : "movie") + "?query=" + java.net.URLEncoder.encode(name, "UTF-8"), tv);
                if (got.isEmpty()) {
                    tv = !tv;
                    got = PagesV2.tmdb(key, "search/" + (tv ? "tv" : "movie") + "?query=" + java.net.URLEncoder.encode(name, "UTF-8"), tv);
                }
            } catch (Exception ignored) {
            }
            changed = true;
            if (got.isEmpty()) { e.tmdbId = -1; continue; }
            PagesV2.Title t = got.get(0);
            e.tmdbId = t.id;
            e.tv = t.tv;
            e.show = t.title;
            e.poster = t.poster;
            e.backdrop = t.backdrop;
            e.overview = t.overview;
            e.year = t.year;
        }
        if (changed) mergeMatches(c, list);
    }

    private static Entry copy(Entry x) {
        Entry e = new Entry();
        e.pkg = x.pkg;
        e.title = x.title;
        e.sub = x.sub;
        e.dur = x.dur;
        return e;
    }

    private static synchronized void mergeMatches(Context c, List<Entry> found) {
        List<Entry> now = new ArrayList<>(load(c));
        boolean any = false;
        for (Entry e : now) {
            if (e.tmdbId != 0) continue;
            for (Entry f : found) {
                if (!f.pkg.equals(e.pkg) || !f.title.equals(e.title) || !f.sub.equals(e.sub)) continue;
                e.tmdbId = f.tmdbId;
                e.tv = f.tv;
                e.show = f.show;
                e.poster = f.poster;
                e.backdrop = f.backdrop;
                e.overview = f.overview;
                e.year = f.year;
                any = true;
            }
        }
        if (any) store(c, now);
    }

    /** Started and not finished: past the first minute or two, short of the credits. */
    private static boolean inProgress(Entry e) {
        float p = e.progress();
        return p > 0.02f && p < 0.95f;
    }

    /**
     * Continue watching for Movies (tv false) or TV shows (tv true): one poster per film or series, the newest
     * first, each carrying how far in it is and the app it was watched in.
     */
    static List<PagesV2.Title> titles(Context c, Boolean tv) {
        List<PagesV2.Title> out = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Entry e : load(c)) {
            if (e.tmdbId <= 0 || (tv != null && e.tv != tv) || !inProgress(e) || !seen.add(e.tmdbId) || e.poster == null) continue;
            PagesV2.Title t = new PagesV2.Title();
            t.id = e.tmdbId;
            t.tv = e.tv;
            t.title = e.show != null ? e.show : e.title;
            t.poster = e.poster;
            t.backdrop = e.backdrop;
            t.overview = e.overview == null ? "" : e.overview;
            t.year = e.year == null ? "" : e.year;
            t.what = e.tv && !e.sub.isEmpty() ? e.sub : "";
            t.app = e.pkg;
            out.add(t);
        }
        return out;
    }

    /** Sport started and not finished in any app, the newest first: for the Sports page's own Continue Watching. */
    static List<Entry> sports(Context c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : load(c)) if (e.sport && inProgress(e) && out.size() < 12) out.add(e);
        return out;
    }

    /** How far into each film or series (its latest episode): TMDB id to 0..1, for the bars on posters. */
    static Map<Integer, Float> progress(Context c) {
        Map<Integer, Float> out = new HashMap<>();
        for (Entry e : load(c)) if (e.tmdbId > 0 && inProgress(e) && !out.containsKey(e.tmdbId)) out.put(e.tmdbId, e.progress());
        return out;
    }
}
