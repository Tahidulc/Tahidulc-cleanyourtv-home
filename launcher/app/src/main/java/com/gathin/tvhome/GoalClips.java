package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Goal clips for the Live Centre, where ESPN publishes them (its public game summaries carry its own highlight clips:
 * every goal of a LaLiga game a few minutes after it goes in; for the other leagues it has no rights to goals and
 * carries studio clips or nothing, and those are never shown). A goal in a game worth a clip is looked for every 40
 * seconds for 12 minutes: the clip whose headline names the scorer (else the one new goal clip of that game), each
 * clip once. Off the main thread; the clip is handed to LiveCentre.playClip.
 */
final class GoalClips {
    private static final long EVERY_MS = 40_000L, FOR_MS = 12 * 60_000L;
    private static final Pattern GOALISH = Pattern.compile("(?i).*(scores|goal|back of the net|scoresheet|strike|finish|header|"
            + "equali[sz]|penalty|brace|hat-trick|doubles|makes it|finds the net|on target|nets|fires|curls|slots|volley).*");

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    /** A goal waiting for its clip. */
    private static final class Want {
        String game, slug, scorer, minute, line;
        long since;
    }

    private static final List<Want> wants = new ArrayList<>();
    private static final Set<String> used = new HashSet<>();
    private static boolean polling;

    /** A finished game whose official highlights are waited for: the game, its score line, when to look next. */
    private static final class High {
        ScoresTicker.Game g;
        String line;
        long since, nextAt;
    }

    private static final long HIGH_EVERY_MS = 2 * 60_000L, HIGH_FOR_MS = 3 * 60 * 60_000L, HIGH_FIRST_MS = 4 * 60_000L;
    private static final List<High> highs = new ArrayList<>();
    /** Games whose highlights have been asked for this time the launcher runs (each once). */
    private static final Set<String> highAsked = new HashSet<>();

    /**
     * A game just finished in the Live Centre: its official highlights looked for (where there is an official source
     * for its league), a few minutes from now and then every two, for three hours, and offered once up.
     */
    static void highlights(ScoresTicker.Game g, String line) {
        if (g == null || g.id == null || !OfficialVideos.covered(g.slug) || !highAsked.add(g.id)) return;
        High h = new High();
        h.g = g;
        h.line = line;
        h.since = System.currentTimeMillis();
        h.nextAt = h.since + HIGH_FIRST_MS;
        synchronized (wants) { highs.add(h); }
        if (!polling) {
            polling = true;
            main.postDelayed(poll, 30_000L);
        }
    }

    private GoalClips() { }

    /** A new goal (on the main thread): its clip looked for from now on. */
    static void look(ScoresTicker.Game g, String scorer, String minute, String line) {
        if (g == null || g.id == null || g.slug == null) return;
        Want w = new Want();
        w.game = g.id;
        w.slug = g.slug;
        w.scorer = scorer == null ? "" : scorer;
        w.minute = minute == null ? "" : minute;
        w.line = line;
        w.since = System.currentTimeMillis();
        synchronized (wants) { wants.add(w); }
        if (!polling) {
            polling = true;
            main.postDelayed(poll, 30_000L);   // ESPN's clip takes a few minutes: the first look after half a minute
        }
    }

    /** Stops looking (the Live Centre closed). */
    static void stop() {
        synchronized (wants) { wants.clear(); highs.clear(); }
        polling = false;
        main.removeCallbacks(poll);
    }

    private static final Runnable poll = new Runnable() {
        @Override public void run() {
            List<Want> now;
            List<High> due = new ArrayList<>();
            boolean waiting;
            synchronized (wants) {
                long t = System.currentTimeMillis();
                wants.removeIf(w -> t - w.since > FOR_MS);
                highs.removeIf(h -> t - h.since > HIGH_FOR_MS);
                now = new ArrayList<>(wants);
                for (High h : highs) if (t >= h.nextAt) { due.add(h); h.nextAt = t + HIGH_EVERY_MS; }
                waiting = !highs.isEmpty();
            }
            if (now.isEmpty() && !waiting) { polling = false; return; }
            if (!due.isEmpty()) io.execute(() -> {
                for (High h : due) {
                    List<OfficialVideos.Video> found = OfficialVideos.highlights(h.g);
                    if (found.isEmpty()) continue;
                    synchronized (wants) { highs.remove(h); }
                    main.post(() -> LiveCentre.offerHighlights(h.line, found));
                }
            });
            if (now.isEmpty()) { main.postDelayed(this, EVERY_MS); return; }
            io.execute(() -> {
                Set<String> games = new HashSet<>();
                for (Want w : now) games.add(w.slug + "|" + w.game);
                for (String key : games) {
                    String[] k = key.split("\\|", 2);
                    List<String[]> clips = clips(k[0], k[1]);   // {id, headline, url}
                    synchronized (wants) {
                        for (Iterator<Want> it = wants.iterator(); it.hasNext(); ) {
                            Want w = it.next();
                            if (!w.game.equals(k[1])) continue;
                            String[] c = match(clips, w, countFor(k[1]));
                            if (c == null) continue;
                            used.add(c[0]);
                            it.remove();
                            Want got = w;
                            main.post(() -> LiveCentre.playClip(c[2], got.scorer, got.minute, got.line));
                        }
                    }
                }
            });
            main.postDelayed(this, EVERY_MS);
        }
    };

    private static int countFor(String game) {
        int n = 0;
        for (Want w : wants) if (w.game.equals(game)) n++;
        return n;
    }

    /** The clip for this goal: one whose headline names the scorer; else, the only goal waiting, the one new goal clip. */
    private static String[] match(List<String[]> clips, Want w, int waiting) {
        String name = F1Results.key(w.scorer);
        String[] onlyNew = null;
        int fresh = 0;
        for (String[] c : clips) {
            if (used.contains(c[0])) continue;
            String head = F1Results.key(c[1]);
            if (!name.isEmpty() && head.contains(name)) return c;
            if (GOALISH.matcher(c[1]).matches() && !head.contains("highlights")) { fresh++; onlyNew = c; }
        }
        return waiting == 1 && fresh == 1 ? onlyNew : null;
    }

    /** A game's clips on ESPN, newest first: {id, headline, mp4, picture}. Off the main thread. */
    static List<String[]> clips(String slug, String game) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONObject d = new JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/summary?event=" + game));
            JSONArray v = d.optJSONArray("videos");
            for (int i = 0; v != null && i < v.length(); i++) {
                JSONObject x = v.getJSONObject(i);
                JSONObject src = x.optJSONObject("links") == null ? null : x.getJSONObject("links").optJSONObject("source");
                if (src == null) continue;
                JSONObject hd = src.optJSONObject("HD");
                String url = hd != null ? hd.optString("href") : src.optString("href");
                if (url == null || url.isEmpty() || !url.endsWith(".mp4")) continue;
                out.add(new String[]{x.optString("id", url), x.optString("headline"), url, x.optString("thumbnail", "")});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** adb's test hook: a finished game's first goal clip played in the Live Centre ("slug|event id"). */
    static void test(String slugAndGame) {
        String[] k = slugAndGame.split("\\|", 2);
        if (k.length < 2) return;
        io.execute(() -> {
            for (String[] c : clips(k[0], k[1])) {
                if (!GOALISH.matcher(c[1]).matches() || F1Results.key(c[1]).contains("highlights")) continue;
                String who = c[1].replaceAll("(?i)\\s+(gets|finds|scores|fires|curls|slots|heads|nets|doubles|makes).*$", "");
                main.post(() -> LiveCentre.playClip(c[2], who, "", "Test clip"));
                return;
            }
        });
    }
}
