package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

/**
 * Opens an app that has no link to a title (Debrid Stream) on its search for one: starts the app, then reads its
 * screen through accessibility, picks the profile it asks for, opens its search and types the title in, looking a few
 * times a second for at most SEARCH_MS after a tile asked. TiviMate the same way on a channel, found through its own
 * search. It hears only Debrid Stream's own focus changes, to copy its Continue Watching row (below). Without the
 * helper switched on, the app just opens.
 */
public class AppSearchService extends AccessibilityService {
    private static final String TAG = "AppSearch";
    /** A look at the screen every STEP_MS: quick enough that each step follows the last at once. */
    /**
     * A channel tune gives up after TUNE_MS in all (it was minutes, with the cover up the whole time and TiviMate on
     * the last channel, or its search, once it lifted).
     */
    private static final long SEARCH_MS = 25_000L, TUNE_MS = 30_000L, STEP_MS = 220L;
    private static AppSearchService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private String target, query;
    private long deadline, typedAt;
    /** Tuning TiviMate to a channel (query is its name) rather than searching an app for a title. */
    private boolean channel;
    /** The title searched for is a series: its match is looked for under TV Shows first. */
    private boolean wantTv;
    private int picks, retypes, scrolls;
    /** The channel names to try in turn, and the one being tried. */
    private java.util.List<String> candidates = java.util.Collections.emptyList();
    private int candidate;
    /** Where in the found row a plain match was seen while a 4K one is looked for further on, and going back to it. */
    private int foundAt = -1;
    private boolean backing, settle;
    /** Debrid Stream: the found title picked, its page on its way. */
    private boolean opened;
    /** How long the cover stays once done, so what it hides has drawn. */
    private long hideDelay, pickedAt, lastBack, startedAt, lastSearch;
    private int searches;

    /** The helper while Android has it bound (ScreenShots takes the screen's pictures through it); null otherwise. */
    static AccessibilityService helper() {
        return instance;
    }

    /** The screen locked as the power button does (the TV to sleep); false without the helper or on Android 8. */
    static boolean sleepScreen() {
        return instance != null && android.os.Build.VERSION.SDK_INT >= 28 && instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Opens pkg and, with the helper on, its search for query. False when the app could not be opened. */
    static boolean search(Context c, String pkg, String query) {
        return search(c, pkg, query, false);
    }

    /**
     * Opens pkg on its search for query and, with the helper on, the found film (or series, when tv) of that name, so
     * its own page opens. False when the app could not be opened.
     */
    static boolean search(Context c, String pkg, String query, boolean tv) {
        return search(c, pkg, query, tv, null);
    }

    static boolean search(Context c, String pkg, String query, boolean tv, android.graphics.Bitmap art) {
        return search(c, pkg, query, tv, art, null);
    }

    /**
     * As search, with the title's poster and its logo (its name as drawn) for the loading cover shown while the helper
     * works; either may be null.
     */
    static boolean search(Context c, String pkg, String query, boolean tv, android.graphics.Bitmap art, android.graphics.Bitmap logo) {
        Intent open = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return false;
        // The helper not connected (a TCL drops it now and then): without it the app only opens, on its home screen,
        // with nothing searched. Connected again first, as a channel's tune is, then the search.
        if (instance == null && query != null && !query.isEmpty() && ensure(c)) {
            waiting(c, pkg);
            Intent go = open;
            whenReady(() -> openFor(c, pkg, go, query, tv, art, logo), 0);
            return true;
        }
        return openFor(c, pkg, open, query, tv, art, logo);
    }

    /** search's opening of pkg, the helper connected or not. */
    private static boolean openFor(Context c, String pkg, Intent open, String query, boolean tv, android.graphics.Bitmap art,
                                   android.graphics.Bitmap logo) {
        boolean helper = instance != null && query != null && !query.isEmpty();
        // With the helper on, the app starts afresh from its home screen, where its search is: left on a title's own
        // page, it would come back there, with no search to open.
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | (helper ? Intent.FLAG_ACTIVITY_CLEAR_TASK : 0));
        try {
            c.startActivity(open);
        } catch (Exception e) {
            return false;
        }
        if (helper) {
            instance.start(pkg, query);
            instance.wantTv = tv;
            // Surprise me's episode, asked for just before (wantEpisode): found on the series' page too.
            boolean episode = tv && ReleaseAlerts.DEBRID.equals(pkg) && pendingSeason > 0
                    && System.currentTimeMillis() - pendingAt < 15_000L;
            if (episode) {
                instance.epSeason = pendingSeason;
                instance.epNumber = pendingEpisode;
                instance.epName = pendingName;
                instance.deadline = System.currentTimeMillis() + 40_000L;
            }
            pendingSeason = 0;
            // A poster's OK: on from the title's page to its Play (an episode asked for comes first).
            if (!episode && ReleaseAlerts.DEBRID.equals(pkg) && query.equals(pendingPlay)
                    && System.currentTimeMillis() - pendingPlayAt < 15_000L) {
                instance.playQuery = query;
                instance.playMode = pendingPlayYes == null ? -1 : pendingPlayYes ? 1 : 0;
                if (instance.playMode != 0) instance.deadline = System.currentTimeMillis() + 40_000L;
            }
            pendingPlay = null;
            if (episode) {
                // The loading cover for an episode: the programme's logo, the season and number, the episode's title,
                // over the episode's own picture.
                instance.coverEpisode = new String[]{"SEASON " + instance.epSeason + "  ·  EPISODE " + instance.epNumber,
                        pendingName == null ? "" : pendingName};
                instance.showCover(art, logo, query, null, 24_000L);
                instance.coverEpisode = null;
                if (pendingStill != null && instance.coverBg != null) {
                    instance.coverBg.setImageBitmap(pendingStill);
                    instance.coverBg.setAlpha(0.85f);
                }
            } else {
                instance.showCover(art, logo, query, null, 8_000L);
            }
            pendingStill = null;
        }
        return true;
    }

    // ---- Debrid Stream: an episode on the series' page ---------------------------------------------------------

    /** The episode the next series search in Debrid Stream should end on (Surprise me's pick), and when it was asked. */
    private static int pendingSeason, pendingEpisode;
    private static String pendingName;
    private static long pendingAt;
    /** The episode's picture (TMDB's still), for the cover while it is found. */
    private static android.graphics.Bitmap pendingStill;
    /** The cover's two episode lines (its season and number, its title) while it is being made; null otherwise. */
    private String[] coverEpisode;
    /** This search's episode to find: season, number and name (season 0: none, the series' page is the end). */
    private int epSeason, epNumber, epMoves;
    private String epName;
    private long epStepAt;
    /** When OK went to the found episode (0: not yet), and when its player first showed. */
    private long epPlayAt, epFrameAt;
    /**
     * The title's page in Debrid Stream: go on to its Play (1), stop there (0), or not known yet (-1: whether the title
     * is on one of the TV's streaming apps is still being looked up); and when the page was first seen.
     */
    private int playMode;
    private String playQuery;
    private long pageSeenAt;
    /** Play asked for the next Debrid Stream search of this title (wantPlay), when, and the answer if known yet. */
    private static String pendingPlay;
    private static long pendingPlayAt;
    private static Boolean pendingPlayYes;

    /**
     * A poster's OK: the coming search for query in Debrid Stream goes on to the Play on the title's page, once yes is
     * known (null: playDecided will say, within a few seconds; until then the helper waits on the page).
     */
    static void wantPlay(String query, Boolean yes) {
        pendingPlay = query;
        pendingPlayYes = yes;
        pendingPlayAt = System.currentTimeMillis();
    }

    /** The answer for wantPlay: before the search starts, or while the helper waits on the title's page. */
    static void playDecided(String query, boolean yes) {
        if (query == null) return;
        if (query.equals(pendingPlay)) pendingPlayYes = yes;
        AppSearchService s = instance;
        if (s != null && s.playMode == -1 && query.equals(s.playQuery)) s.playMode = yes ? 1 : 0;
    }

    /** Surprise me: the episode the series' page should go on to, for the search that follows within a few seconds. */
    static void wantEpisode(int season, int episode, String name) {
        wantEpisode(season, episode, name, null);
    }

    static void wantEpisode(int season, int episode, String name, android.graphics.Bitmap still) {
        pendingStill = still;
        pendingSeason = season;
        pendingEpisode = episode;
        pendingName = name;
        pendingAt = System.currentTimeMillis();
    }

    /**
     * From the series' page in Debrid Stream to the episode, as one would with the remote: its "More episodes", then
     * on the episodes page (the seasons down the left, the shown season's episodes on the right, the season following
     * the highlight) Left into the seasons and Up or Down to the season, Right into its episodes and Down to the
     * episode by its number (or its name), where it stops, highlighted, for OK to play. Steps a third of a second apart
     * so the app's highlight keeps up; Android 13 and later (the D-pad presses it makes); otherwise the page is the end.
     */
    private boolean episodeStep(AccessibilityNodeInfo root) {
        if (android.os.Build.VERSION.SDK_INT < 33 || epMoves > 120) { epSeason = 0; hideDelay = 600; return false; }
        long now = System.currentTimeMillis();
        if (now - epStepAt < 340) return true;
        epStepAt = now;
        android.graphics.Rect all = new android.graphics.Rect();
        root.getBoundsInScreen(all);
        int split = all.left + Math.round(all.width() * 0.33f);
        AccessibilityNodeInfo header = seasonHeader(root, split);
        if (header == null) {
            // The series' page: its "More episodes" (the page may still be drawing).
            AccessibilityNodeInfo more = withText(root, "More episodes");
            if (more == null) more = withText(root, "Episodes");
            if (more != null) { click(more); epMoves++; }
            return true;
        }
        int shown = number(String.valueOf(header.getText()));
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        android.graphics.Rect fr = new android.graphics.Rect();
        if (f != null) f.getBoundsInScreen(fr);
        boolean inSeasons = f != null && fr.centerX() < split;
        if (shown != epSeason) {
            if (!inSeasons) return press(GLOBAL_ACTION_DPAD_LEFT);
            int at = number(childText(f, "Season "));
            // The season highlighted already: its episodes are loading (the app's spinner), and the heading follows.
            if (at == epSeason) return true;
            return press(at > 0 && at > epSeason ? GLOBAL_ACTION_DPAD_UP : GLOBAL_ACTION_DPAD_DOWN);
        }
        if (inSeasons || f == null) return press(GLOBAL_ACTION_DPAD_RIGHT);
        int num = number(digitsChild(f));
        String name = descChild(f);
        if (num == epNumber || (epName != null && !epName.isEmpty() && name != null && same(name, epName))) {
            android.util.Log.i(TAG, "episode found: S" + epSeason + " E" + epNumber + " in " + epMoves + " moves; playing it");
            epSeason = 0;
            // OK on it, as one would: Debrid Stream takes its best source and plays. The cover stays until the player is up.
            press(GLOBAL_ACTION_DPAD_CENTER);
            epPlayAt = now;
            epFrameAt = 0;
            deadline = Math.max(deadline, now + 20_000L);
            return true;
        }
        return press(num > 0 && num > epNumber ? GLOBAL_ACTION_DPAD_UP : GLOBAL_ACTION_DPAD_DOWN);
    }

    /**
     * A poster's OK, on the title's page in Debrid Stream: its Play pressed (the film, or the series from where it was
     * left), as soon as the page has drawn it, and the cover kept up until the player is running. Only for a title on
     * one of the TV's streaming apps; while that is still being looked up, the page waits up to four seconds.
     */
    private boolean coverPlayStep(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        if (pageSeenAt == 0) pageSeenAt = now;
        if (playMode < 0) {
            if (now - pageSeenAt < 4_000L) return true;
            playMode = 0;
        }
        AccessibilityNodeInfo play = playMode > 0 ? playButton(root) : null;
        if (play == null) {
            if (playMode > 0 && now - pageSeenAt < 5_000L) return true;
            playMode = 0;
            hideDelay = 300;
            return false;
        }
        android.util.Log.i(TAG, "title page: Play (" + play.getText() + ") " + (now - pageSeenAt) + " ms after it showed");
        playMode = 0;
        click(play);
        epPlayAt = now;
        epFrameAt = 0;
        deadline = Math.max(deadline, now + 20_000L);
        return true;
    }

    /** The title page's Play: its "Play" (or "Resume", "Continue…") button's words. */
    private static AccessibilityNodeInfo playButton(AccessibilityNodeInfo n) {
        if (n == null) return null;
        CharSequence t = n.getText();
        if (t != null && t.toString().trim().matches("(?i)(play|resume|continue)( .*)?")) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo h = playButton(n.getChild(i));
            if (h != null) return h;
        }
        return null;
    }

    /**
     * After OK on the episode: the cover stays over the app's black loading screen until its player shows the episode
     * under way (the time past 00:00, so not over the black while a big file buffers or the TV changes to HDR), or seven
     * seconds after the player shows (its controls may have gone), or after 15 seconds with no player (a list of
     * sources, say, left to the person).
     */
    private boolean playerStep(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        boolean frame = !root.findAccessibilityNodeInfosByViewId(ReleaseAlerts.DEBRID + ":id/exo_content_frame").isEmpty();
        if (frame && epFrameAt == 0) epFrameAt = now;
        boolean going = frame && moving(root);
        if (going || (epFrameAt > 0 && now - epFrameAt > 7_000L) || now - epPlayAt > 15_000L) {
            android.util.Log.i(TAG, (going ? "playing" : frame ? "player up, time not seen" : "no player") + " " + (now - epPlayAt) + " ms after OK");
            epPlayAt = 0;
            hideDelay = 300;
            return false;
        }
        return true;
    }

    /** A player's time under way: a "0:12" or "01:02:03" that is not all noughts. */
    private static boolean moving(AccessibilityNodeInfo n) {
        if (n == null) return false;
        CharSequence t = n.getText();
        if (t != null && t.toString().matches("\\d{1,2}(:\\d{2}){1,2}") && !t.toString().matches("[0:]+")) {
            // Not the clock in the corner ("1:47" beside "PM"): the elapsed time is low on the screen.
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            if (r.top > 600 && r.left < 600) return true;
        }
        for (int i = 0; i < n.getChildCount(); i++) if (moving(n.getChild(i))) return true;
        return false;
    }

    private boolean press(int action) {
        epMoves++;
        performGlobalAction(action);
        return true;
    }

    /** The shown season's heading on the episodes page: "Season 5" in the right-hand part. */
    private static AccessibilityNodeInfo seasonHeader(AccessibilityNodeInfo n, int split) {
        if (n == null) return null;
        CharSequence t = n.getText();
        if (t != null && t.toString().matches("Season \\d+")) {
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            if (r.left >= split && n.getParent() != null && !n.getParent().isClickable()) return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo h = seasonHeader(n.getChild(i), split);
            if (h != null) return h;
        }
        return null;
    }

    private static AccessibilityNodeInfo withText(AccessibilityNodeInfo n, String text) {
        if (n == null) return null;
        if (n.getText() != null && text.equalsIgnoreCase(n.getText().toString().trim())) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo h = withText(n.getChild(i), text);
            if (h != null) return h;
        }
        return null;
    }

    /** The first text under n that starts with prefix ("Season "), or "". */
    private static String childText(AccessibilityNodeInfo n, String prefix) {
        if (n == null) return "";
        if (n.getText() != null && n.getText().toString().startsWith(prefix)) return n.getText().toString();
        for (int i = 0; i < n.getChildCount(); i++) {
            String t = childText(n.getChild(i), prefix);
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    /** The first text under n that is only a number (an episode tile's number), or "". */
    private static String digitsChild(AccessibilityNodeInfo n) {
        if (n == null) return "";
        if (n.getText() != null && n.getText().toString().trim().matches("\\d{1,4}")) return n.getText().toString().trim();
        for (int i = 0; i < n.getChildCount(); i++) {
            String t = digitsChild(n.getChild(i));
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    /** The first description under n (an episode tile's name), or null. */
    private static String descChild(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.getContentDescription() != null && n.getContentDescription().length() > 0) return n.getContentDescription().toString();
        for (int i = 0; i < n.getChildCount(); i++) {
            String t = descChild(n.getChild(i));
            if (t != null) return t;
        }
        return null;
    }

    private static int number(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s == null ? "" : s);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static boolean same(String a, String b) {
        return a.replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase(b.replaceAll("[^A-Za-z0-9]", ""));
    }

    /**
     * Opens TiviMate on a channel: starts it, then opens its search, types the channel's name and picks the channel of
     * that name from what it finds. False when the app could not be opened; without the helper it just opens.
     */
    static boolean tune(Context c, String pkg, String channel) {
        return tune(c, pkg, java.util.Collections.singletonList(channel), null);
    }

    /**
     * As tune, with the channels to try in turn (the first TiviMate has wins) and the logo for the cover shown while
     * the helper works (null for none).
     */
    static boolean tune(Context c, String pkg, java.util.List<String> channels, android.graphics.Bitmap logo) {
        return tune(c, pkg, channels, logo, null);
    }

    /** As tune, with a picture of what is on (the programme's still) behind the cover too. */
    static boolean tune(Context c, String pkg, java.util.List<String> channels, android.graphics.Bitmap logo, android.graphics.Bitmap art) {
        final java.util.List<String> all = tuneNames(c, channels);
        // The channel watched from now: its game's alerts wait for it (InStep).
        InStep.tuned(c, all);
        // That channel's own scoreboard and logo settings from now (always up, never, its own logo).
        BoardCover.resume(c);
        // The helper not connected (a TCL drops it now and then): without it TiviMate only opens, on the channel last
        // watched. Connected again first, then the tune.
        if (instance == null && !all.isEmpty() && ensure(c)) {
            waiting(c, pkg);
            whenReady(() -> pick(c, pkg, all, logo, art), 0);
            return c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
        }
        return pick(c, pkg, all, logo, art);
    }

    /**
     * adb test only: "kleva:A,B" tunes Kleva TV alone, "tivimate:A,B" (or plain "A,B") TiviMate alone, each with the
     * names as a tile gives them; the log line "done ... in N ms" says how it went.
     */
    static void testTune(Context c, String spec) {
        boolean k = spec.startsWith("kleva:");
        String list = spec.replaceFirst("^(kleva|tivimate):", "");
        java.util.List<String> names = tuneNames(c, java.util.Arrays.asList(list.split(",")));
        Runnable go = () -> {
            if (k) { if (!klevaChannel(c, names, null, null, null)) android.util.Log.i(TAG, "done kleva-channel: not started"); }
            else tivimate(c, "ar.tvplayer.tv", names, null, null);
        };
        if (instance == null && ensure(c)) whenReady(go, 0); else go.run();
    }

    /** The names to try, in turn: a UHD version first where the playlist has one, at most five. */
    private static java.util.List<String> tuneNames(Context c, java.util.List<String> channels) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (channels != null) for (String n : channels) if (n != null && !n.trim().isEmpty() && !names.contains(n.trim())) names.add(n.trim());
        // The playlist's UHD version of a channel first, when it has one (the TV guide's list says which): only those
        // known to exist are tried, as each name not in the playlist costs a search.
        java.util.List<String> uhd = new java.util.ArrayList<>();
        for (String line : Config.themeStrings(c, "guide_channels", new java.util.ArrayList<>())) {
            String[] f = line.split("\\|");
            if (f.length > 1 && f[1].matches("(?i).*\\b(uhd|4k)\\b.*")) uhd.add(f[1].trim());
        }
        if (!uhd.isEmpty()) {
            java.util.List<String> first = new java.util.ArrayList<>();
            for (String n : names) {
                String base = n.replaceAll("(?i)\\s*\\b(f?hd|uhd|4k)\\b\\s*$", "").trim().toLowerCase(Locale.ROOT);
                for (String u : uhd) {
                    String ub = u.replaceAll("(?i)\\s*\\b(uhd|4k)\\b.*$", "").trim().toLowerCase(Locale.ROOT);
                    if (ub.equals(base) && !first.contains(u)) first.add(u);
                }
                if (!first.contains(n)) first.add(n);
            }
            names = first;
        }
        // At most the first five: each name not in the playlist costs a search.
        if (names.size() > 5) names = new java.util.ArrayList<>(names.subList(0, 5));
        return names;
    }

    /**
     * r once the helper is connected, or after about five seconds without it: a TCL binds it again more slowly than
     * the three seconds this once waited, and the tune then went ahead without it (TiviMate only opened).
     */
    private static void whenReady(Runnable r, int tries) {
        if (instance != null || tries >= 25) { r.run(); return; }
        new Handler(Looper.getMainLooper()).postDelayed(() -> whenReady(r, tries + 1), 200);
    }

    /** While the helper is switched back on (a few seconds at most): "Opening TiviMate…", so the wait shows. */
    private static void waiting(Context c, String pkg) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            CharSequence name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            android.widget.Toast.makeText(c.getApplicationContext(), "Opening " + name + "…", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            // No name to show: the wait goes on without a word, as before.
        }
    }

    /**
     * Which app tunes: Kleva TV first where this TV's theme says "channel_app": "kleva" (its search lists each channel
     * once and plays the pick full screen), TiviMate when Kleva TV has no such channel; otherwise TiviMate.
     */
    private static boolean pick(Context c, String pkg, java.util.List<String> names, android.graphics.Bitmap logo, android.graphics.Bitmap art) {
        if ("ar.tvplayer.tv".equals(pkg) && "kleva".equals(Config.themeString(c, "channel_app", ""))) {
            java.util.List<String> plain = new java.util.ArrayList<>();
            for (String n : names) if (!n.startsWith("match:")) plain.add(n);
            if (!plain.isEmpty() && klevaChannel(c, plain, logo, art, () -> tivimate(c, pkg, names, logo, art))) return true;
        }
        return tivimate(c, pkg, names, logo, art);
    }

    private static boolean tivimate(Context c, String pkg, java.util.List<String> names, android.graphics.Bitmap logo, android.graphics.Bitmap art) {
        Intent open = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return false;
        boolean helper = instance != null && !names.isEmpty();
        // With the helper on, TiviMate starts afresh, on its home screen with the menu its search is in: brought back
        // as it was, it opens full screen on the channel last watched, where there is no menu.
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | (helper ? Intent.FLAG_ACTIVITY_CLEAR_TASK : 0));
        try {
            c.startActivity(open);
        } catch (Exception e) {
            return false;
        }
        if (helper) {
            instance.start(pkg, names.get(0));
            instance.quiet();
            instance.channel = true;
            instance.candidates = names;
            instance.deadline = System.currentTimeMillis() + TUNE_MS;
            String shown = names.get(0).startsWith("match:") ? names.get(0).substring(6).replace("|", " v ") : names.get(0);
            instance.showCover(art, logo, shown, LIVE, 12_000L);
        }
        return true;
    }

    /**
     * Home came to the front (the remote's Home, while a channel or title was loading): the helper stops at once and
     * its cover goes, so Home is never held behind it.
     */
    static void cancel() {
        if (instance != null) instance.abort();
    }

    private void abort() {
        if (target == null && cover == null) return;
        target = null;
        main.removeCallbacks(step);
        removeCover();
    }

    // ---- a request to Google Assistant, done out of sight (the lights panel) ---------------------------------

    private android.view.View assistCover;
    private Spinner assistSpinner;
    /** Whether the sound was muted here for the request (and so is turned back on after it). */
    private boolean assistMuted;
    private String assistQuery;
    private long assistAt;
    private static final String ASSISTANT = "com.google.android.katniss";

    /**
     * Sends a request to the Assistant (ask runs it) behind a still of the screen as it is, the open panel drawn on it
     * and a spinner on the button pressed; once the Assistant has answered, its screen is closed and the still lifts,
     * so the person never leaves the panel. False without the helper: the caller asks the plain way.
     */
    static boolean quietAsk(android.app.Activity a, android.view.View panel, android.view.View pressed, String query, Runnable ask) {
        return quietAsk(a, panel, pressed, query, ask, -1f);
    }

    /**
     * As quietAsk, the working sign over the pressed view drawn to its shape: corner its corners' radius on screen
     * (a light's tile in the smart home panel); below 0, a pill (the lights panel's keys).
     */
    static boolean quietAsk(android.app.Activity a, android.view.View panel, android.view.View pressed, String query, Runnable ask, float corner) {
        if (instance == null || android.os.Build.VERSION.SDK_INT < 26) return false;
        android.view.View decor = a.getWindow().getDecorView();
        int w = decor.getWidth(), h = decor.getHeight();
        if (w <= 0 || h <= 0) return false;
        android.graphics.Bitmap still = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            android.view.PixelCopy.request(a.getWindow(), still, result -> {
                if (instance == null || result != android.view.PixelCopy.SUCCESS) { ask.run(); return; }
                instance.coverForAssist(still, panel, pressed, query, corner);
                ask.run();
            }, new android.os.Handler(android.os.Looper.getMainLooper()));
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    private void coverForAssist(android.graphics.Bitmap still, android.view.View panel, android.view.View pressed, String query, float corner) {
        endAssist(false);
        float d = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout v = new android.widget.FrameLayout(this);
        android.widget.ImageView back = new android.widget.ImageView(this);
        back.setImageBitmap(still);
        back.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        v.addView(back, new android.widget.FrameLayout.LayoutParams(-1, -1));
        // The panel, which is a window of its own, drawn where it is.
        if (panel != null && panel.getWidth() > 0) {
            android.graphics.Bitmap p = android.graphics.Bitmap.createBitmap(panel.getWidth(), panel.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            panel.draw(new android.graphics.Canvas(p));
            int[] at = new int[2];
            panel.getLocationOnScreen(at);
            android.widget.ImageView pv = new android.widget.ImageView(this);
            pv.setImageBitmap(p);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(panel.getWidth(), panel.getHeight());
            lp.leftMargin = at[0];
            lp.topMargin = at[1];
            v.addView(pv, lp);
        }
        // A spinner on the button pressed while it works: where it is on screen and as big as it shows (a focused tile
        // is drawn a little larger than its layout), in its own shape.
        if (pressed != null && pressed.getWidth() > 0) {
            android.graphics.Rect on = new android.graphics.Rect();
            if (!pressed.getGlobalVisibleRect(on) || on.isEmpty()) {
                int[] at = new int[2];
                pressed.getLocationOnScreen(at);
                on.set(at[0], at[1], at[0] + pressed.getWidth(), at[1] + pressed.getHeight());
            }
            Spinner sp = new Spinner(this, d, corner < 0 ? -1f : corner * Math.max(1f, pressed.getScaleX()));
            assistSpinner = sp;
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(on.width(), on.height());
            lp.leftMargin = on.left;
            lp.topMargin = on.top;
            v.addView(sp, lp);
            sp.start();
        }
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(-1, -1,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        try {
            ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).addView(v, lp);
        } catch (RuntimeException e) {
            return;
        }
        assistCover = v;
        assistQuery = query;
        assistAt = System.currentTimeMillis();
        // The Assistant's spoken answer unheard: the sound off while it works, unless it was off already.
        android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
        if (am != null && !am.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) {
            try {
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0);
                assistMuted = true;
            } catch (RuntimeException ignored) {
                // not allowed (Do Not Disturb): it may be heard
            }
        }
        main.postDelayed(assistWatch, 1_200);
    }

    /** Watches the Assistant: once it has answered (or gone, or after eight seconds), the request is done. */
    private final Runnable assistWatch = new Runnable() {
        @Override public void run() {
            if (assistQuery == null) return;
            long since = System.currentTimeMillis() - assistAt;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            CharSequence pkg = root == null ? null : root.getPackageName();
            boolean assistant = pkg != null && ASSISTANT.contentEquals(pkg);
            if (since > 8_000 || (pkg != null && !assistant && since > 2_500)) { endAssist(assistant); return; }
            if (assistant && since > 2_000 && answered(root, assistQuery)) {
                // A moment for the request to go through, then away.
                main.postDelayed(() -> endAssist(true), 700);
                assistQuery = assistQuery + "\u0000";   // answered: stop looking
                return;
            }
            main.postDelayed(this, 300);
        }
    };

    /** Whether the Assistant shows an answer: words of its own, not the request, not "Listening". */
    private static boolean answered(AccessibilityNodeInfo n, String query) {
        if (n == null) return false;
        CharSequence t = n.getText();
        if (t != null && t.length() > 3) {
            String l = t.toString().toLowerCase(Locale.ROOT).trim();
            String q = query.toLowerCase(Locale.ROOT).replace("\u0000", "");
            if (!l.equals(q) && !q.contains(l) && !l.contains("listening") && !l.contains("speak") && !l.startsWith("try ")
                    && !l.contains("search")) return true;
        }
        for (int i = 0; i < n.getChildCount(); i++) if (answered(n.getChild(i), query)) return true;
        return false;
    }

    /** The request done: the Assistant's screen closed (Back, only while it is in front), then the still lifts. */
    private void endAssist(boolean closeAssistant) {
        main.removeCallbacks(assistWatch);
        assistQuery = null;
        android.view.View v = assistCover;
        assistCover = null;
        Spinner sp = assistSpinner;
        assistSpinner = null;
        if (assistMuted) {
            assistMuted = false;
            // Back on once the Assistant's screen (and its voice) has gone.
            main.postDelayed(() -> {
                android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
                try {
                    if (am != null) am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0);
                } catch (RuntimeException ignored) {
                }
            }, closeAssistant ? 900 : 0);
        }
        if (v == null) return;
        if (sp != null && closeAssistant) sp.done();
        if (closeAssistant) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null && ASSISTANT.contentEquals(root.getPackageName()))
                performGlobalAction(GLOBAL_ACTION_BACK);
        }
        main.postDelayed(() -> v.animate().alpha(0f).setDuration(220).withEndAction(() -> removeView(v)).start(), closeAssistant ? 750 : 0);
    }

    /**
     * Over the button pressed while the request runs: a white pill that pops in with three dots bouncing in turn, then
     * a green tick drawn in once it is done.
     */
    private static final class Spinner extends android.view.View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float d;
        /** The pressed view's corners on screen; below 0, a white pill (a key). */
        private final float corner;
        private float phase, tick = -1;
        private android.animation.ValueAnimator bounce;

        Spinner(Context c, float d) {
            this(c, d, -1f);
        }

        Spinner(Context c, float d, float corner) {
            super(c);
            this.d = d;
            this.corner = corner;
        }

        void start() {
            setScaleX(0.85f);
            setScaleY(0.85f);
            setAlpha(0f);
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
            bounce = android.animation.ValueAnimator.ofFloat(0, 1);
            bounce.setDuration(900);
            bounce.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            bounce.setInterpolator(new android.view.animation.LinearInterpolator());
            bounce.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
            bounce.start();
        }

        /** Done: the dots give way to a green tick, drawn in stroke by stroke. */
        void done() {
            if (bounce != null) bounce.cancel();
            android.animation.ValueAnimator draw = android.animation.ValueAnimator.ofFloat(0, 1);
            draw.setDuration(320);
            draw.addUpdateListener(a -> { tick = (float) a.getAnimatedValue(); invalidate(); });
            draw.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (bounce != null) bounce.cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            if (corner >= 0) { drawTile(c); return; }
            float w = getWidth(), h = getHeight(), r = h / 2f, cx = w / 2f, cy = h / 2f;
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            c.drawRoundRect(0, 0, w, h, r, r, paint);
            if (tick >= 0) {
                // The tick: down to the corner, then up, in green.
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(3f * d);
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                paint.setColor(0xFF22A45D);
                float s = h * 0.2f;
                float ax = cx - s, ay = cy, bx = cx - s * 0.25f, by = cy + s * 0.7f, ex = cx + s * 1.1f, ey = cy - s * 0.75f;
                float first = Math.min(1f, tick / 0.4f), second = Math.max(0f, (tick - 0.4f) / 0.6f);
                c.drawLine(ax, ay, ax + (bx - ax) * first, ay + (by - ay) * first, paint);
                if (second > 0) c.drawLine(bx, by, bx + (ex - bx) * second, by + (ey - by) * second, paint);
                return;
            }
            // Three dots, each rising and falling in turn.
            paint.setColor(0xFF1A1D24);
            float gap = 9f * d, dot = 3.2f * d;
            for (int i = 0; i < 3; i++) {
                float p = (phase + 1f - i * 0.18f) % 1f;
                float lift = (float) Math.max(0, Math.sin(p * Math.PI * 2)) * 4.5f * d;
                paint.setAlpha(150 + (int) (105 * lift / (4.5f * d)));
                c.drawCircle(cx + (i - 1) * gap, cy - lift, dot, paint);
            }
            paint.setAlpha(255);
        }

        /**
         * Over a tile: the tile's own shape in dark glass, three white dots rising in turn in its middle, then a white
         * disc with a green tick drawn into it.
         */
        private void drawTile(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, s = Math.min(w, h);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(0xE60E1450);
            c.drawRoundRect(0, 0, w, h, corner, corner, paint);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 1.5f * d));
            paint.setColor(0x59FFFFFF);
            float in = paint.getStrokeWidth() / 2f;
            c.drawRoundRect(in, in, w - in, h - in, Math.max(0, corner - in), Math.max(0, corner - in), paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            if (tick >= 0) {
                float r = s * 0.2f;
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r * Math.min(1f, 0.6f + tick), paint);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, r * 0.16f));
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                paint.setColor(0xFF22A45D);
                float t = r * 0.5f;
                float ax = cx - t, ay = cy, bx = cx - t * 0.25f, by = cy + t * 0.7f, ex = cx + t * 1.1f, ey = cy - t * 0.75f;
                float first = Math.min(1f, tick / 0.4f), second = Math.max(0f, (tick - 0.4f) / 0.6f);
                c.drawLine(ax, ay, ax + (bx - ax) * first, ay + (by - ay) * first, paint);
                if (second > 0) c.drawLine(bx, by, bx + (ex - bx) * second, by + (ey - by) * second, paint);
                return;
            }
            float dot = Math.max(3.2f * d, Math.min(7f * d, s * 0.05f)), gap = dot * 3f, rise = dot * 1.4f;
            paint.setColor(0xFFFFFFFF);
            for (int i = 0; i < 3; i++) {
                float p = (phase + 1f - i * 0.18f) % 1f;
                float lift = (float) Math.max(0, Math.sin(p * Math.PI * 2)) * rise;
                paint.setAlpha(140 + (int) (115 * lift / rise));
                c.drawCircle(cx + (i - 1) * gap, cy - lift, dot, paint);
            }
            paint.setAlpha(255);
        }
    }

    /** adb test only: the open app's accessibility tree, to logcat. */
    static void dump() {
        if (instance == null) { Log.i(TAG, "helper off"); return; }
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) { Log.i(TAG, "no window"); return; }
        dump(root, 0);
    }

    private static void dump(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return;
        android.graphics.Rect r = new android.graphics.Rect();
        n.getBoundsInScreen(r);
        Log.i(TAG, "                                        ".substring(0, Math.min(40, depth))
                + n.getClassName() + " text=" + n.getText() + " desc=" + n.getContentDescription() + " id=" + n.getViewIdResourceName()
                + (n.isClickable() ? " click" : "") + (n.isFocusable() ? " focusable" : "") + (n.isFocused() ? " FOCUSED" : "")
                + (n.isEditable() ? " EDIT" : "") + " " + r.toShortString());
        for (int i = 0; i < n.getChildCount(); i++) dump(n.getChild(i), depth + 1);
    }

    /**
     * The ways a title is typed into an app's search, in turn while nothing found matches: as written; with its
     * punctuation as spaces ("Dead-End Job" as "Dead End Job"); without what follows a colon; without a leading "The".
     * Debrid Stream's search finds some titles only one way.
     */
    private java.util.List<String> spellings = java.util.Collections.emptyList();
    private int spelling;

    static java.util.List<String> spellings(String q) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (q == null) return out;
        String t = q.trim();
        out.add(t);
        String spaced = t.replaceAll("[^\\p{L}\\p{N}']+", " ").replace("'", "").trim();
        if (!out.contains(spaced)) out.add(spaced);
        int colon = t.indexOf(':');
        if (colon > 2) { String head = t.substring(0, colon).trim(); if (!out.contains(head)) out.add(head); }
        if (t.toLowerCase(Locale.ROOT).startsWith("the ")) { String bare = spaced.substring(4).trim(); if (!out.contains(bare)) out.add(bare); }
        return out;
    }

    private void start(String pkg, String q) {
        target = pkg;
        query = q;
        typedAt = 0;
        channel = false;
        picks = 0;
        retypes = 0;
        scrolls = 0;
        scrolledAt = enteredAt = 0;
        nearest = null;
        foundAt = -1;
        backing = false;
        settle = false;
        opened = false;
        lastBack = 0;
        lastSearch = 0;
        searches = 0;
        searchAt = 0;
        profileAt = 0;
        startedAt = System.currentTimeMillis();
        hideDelay = 0;
        candidates = java.util.Collections.singletonList(q);
        candidate = 0;
        kleva = false;
        epSeason = 0;
        epPlayAt = 0;
        playMode = 0;
        playQuery = null;
        pageSeenAt = 0;
        epMoves = 0;
        epStepAt = 0;
        wanted = null;
        missed = false;
        pickedName = null;
        camera = false;
        tried.clear();
        pickedText = null;
        spellings = spellings(q);
        spelling = 0;
        deadline = System.currentTimeMillis() + SEARCH_MS;
        main.removeCallbacks(step);
        main.postDelayed(step, STEP_MS);
    }

    /**
     * On a TV without the key helper (the TCL): this helper takes the remote's keys while pop-up mode, the Live Centre,
     * the F1 timing or the scoreboard's Move and resize is open ("popup", "live", "f1", "cover"), or an alert card is up
     * for OK held to quiet ("card",
     * QuietHold), and gives them up once none is, as filtering keys costs the TCL's remote its auto-repeat. Its other
     * work is not changed.
     */
    private static final java.util.Set<String> keysWanted = new java.util.HashSet<>();

    static void keysFor(String who, boolean on) {
        if (on) keysWanted.add(who); else keysWanted.remove(who);
        AppSearchService s = instance;
        if (s == null) return;
        android.accessibilityservice.AccessibilityServiceInfo info = s.getServiceInfo();
        if (info == null) return;
        int was = info.flags;
        info.flags = !keysWanted.isEmpty() ? was | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                : was & ~android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        if (info.flags != was) s.setServiceInfo(info);
    }

    /** Whether the keys are taken here for who now (with the helper bound). */
    static boolean keysTakenFor(String who) {
        return instance != null && keysWanted.contains(who);
    }

    /**
     * On a TV without the key helper (the TCL), with OK held anywhere in the player switched on (BoardCover.holdAnywhere):
     * the remote's keys taken here while the player is in front (looked at every two seconds), so that OK held there opens
     * the scoreboard's menu; let go as soon as it is not. A held key does not repeat in the player meanwhile.
     */
    static void playerKeysCheck(Context c) {
        AppSearchService s = instance;
        if (s == null) return;
        s.main.removeCallbacks(s.playerKeys);
        s.main.post(s.playerKeys);
    }

    private final Runnable playerKeys = new Runnable() {
        @Override public void run() {
            boolean want = !InputKeyService.running && BoardCover.holdAnywhere(AppSearchService.this);
            String f = want ? front() : null;
            keysFor("player", want && f != null && BoardCover.isPlayer(f));
            if (want) main.postDelayed(this, 2_000L);
        }
    };

    /** OK sent on to the app in front, as Android's own D-pad OK (a short OK kept back to see whether it was held). */
    static void ok() {
        AppSearchService s = instance;
        if (s == null || android.os.Build.VERSION.SDK_INT < 33) return;
        QuietHold.okSentAt = android.os.SystemClock.uptimeMillis();
        s.performGlobalAction(GLOBAL_ACTION_DPAD_CENTER);
    }

    /** Back sent to the app in front, as a press of the remote's Back (pop-up mode's short Back, held back first). */
    static void globalBack() {
        AppSearchService s = instance;
        if (s != null) s.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        if (InputKeyService.running || keysWanted.isEmpty()) return false;
        // Quick reply open while keys are taken here (a card still going, pop-up mode): its keyboard has them first. With
        // none taken here they go straight to its window, which has the focus.
        if (QuickReply.key(event)) return true;
        // OK held on a pop-up: quiet for an hour (QuietHold, which has the keys taken here while a card is up).
        return QuietHold.key(this, event, this::keys);
    }

    private boolean keys(android.view.KeyEvent event) {
        // The overlays button over a match: OK held turns them on (OverlayPrompt).
        if (OverlayPrompt.key(event)) return true;
        // OK at the kick-off on screen, while the card asking for it is up (InStepCard); the replay's bar.
        if (InStepCard.key(event) || Replay.key(event)) return true;
        if (HalfTimeBreak.key(event)) return true;
        if (CreditsCover.key(event)) return true;
        if (BoardCover.key(event)) return true;
        if (LiveCentre.key(event) || F1Tower.key(event)) return true;
        if (PopupFrame.isShowing() && PopupFrame.key(event)) return true;
        return okOn(event);
    }

    /**
     * OK for the app (or the frame) while this helper takes the keys: in pop-up mode on the TCL, OK handed back from
     * here did nothing in the app, so the press is kept here and made again, on its release, with Android's own D-pad
     * OK (as Kleva TV's search is driven below), which reaches whatever window has the remote. A held OK is no
     * loss: the TCL gives a filtered key no auto-repeat, so no app saw a long press here before either.
     */
    private boolean okOn(android.view.KeyEvent e) {
        int code = e.getKeyCode();
        if (code != android.view.KeyEvent.KEYCODE_DPAD_CENTER && code != android.view.KeyEvent.KEYCODE_ENTER
                && code != android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
        if (android.os.Build.VERSION.SDK_INT < 33) return false;
        // This helper's own OK (or QuietHold's) goes by untouched should it come through here.
        if (android.os.SystemClock.uptimeMillis() - QuietHold.okSentAt < 400L) return false;
        if (e.getAction() == android.view.KeyEvent.ACTION_UP && !e.isCanceled()) {
            main.post(() -> {
                QuietHold.okSentAt = android.os.SystemClock.uptimeMillis();
                if (!performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) Log.i(TAG, "OK not sent");
            });
        }
        return true;
    }

    /** Whether a channel search is under way (its cover up, or a channel still to find). */
    static boolean tuning() {
        AppSearchService s = instance;
        return s != null && (s.target != null || s.cover != null);
    }

    /** Whether the helper is running (switched on and bound by the system). */
    static boolean running() {
        return instance != null;
    }

    /**
     * The title's picture and logo for the cover, when they arrive after the app was opened (the app is opened at
     * once, not after they are fetched): the soft background and the logo in place of the name.
     */
    static void coverPictures(android.graphics.Bitmap art, android.graphics.Bitmap logo) {
        if (instance == null || instance.cover == null) return;
        instance.fillCover(art, logo);
    }

    private android.widget.ImageView coverBg, coverMark;

    private void fillCover(android.graphics.Bitmap art, android.graphics.Bitmap logo) {
        if (art != null && coverBg != null && coverBg.getDrawable() == null) {
            coverBg.setImageBitmap(blur(art));
            coverBg.setAlpha(0f);
            coverBg.animate().alpha(1f).setDuration(300).start();
        }
        if (logo != null && coverMark != null && coverMark.getDrawable() == null) {
            coverMark.setImageBitmap(logo);
            coverMark.setVisibility(android.view.View.VISIBLE);
            coverMark.setAlpha(0f);
            coverMark.animate().alpha(1f).setDuration(250).start();
            if (coverTitle != null) coverTitle.setVisibility(android.view.View.GONE);
        }
    }

    /** Shrunk to a few pixels and drawn large again: a soft blur of the poster's colours. */
    private static android.graphics.Bitmap blur(android.graphics.Bitmap art) {
        android.graphics.Bitmap tiny = android.graphics.Bitmap.createScaledBitmap(art, 16, Math.max(2, 16 * art.getHeight() / Math.max(1, art.getWidth())), true);
        return android.graphics.Bitmap.createScaledBitmap(tiny, 64, Math.max(2, 64 * art.getHeight() / Math.max(1, art.getWidth())), true);
    }

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (target == null) { loud(); hideCover(0); return; }
            boolean late = System.currentTimeMillis() > deadline;
            if (!late && advance()) { main.postDelayed(this, STEP_MS); return; }
            // How long it took and how it ended, in the log (adb logcat -s AppSearch), to time one way against another.
            android.util.Log.i(TAG, "done " + (kleva ? (wanted != null ? "kleva-channel" : "kleva-game") : channel ? "tivimate" : target)
                    + " in " + (System.currentTimeMillis() - startedAt) + " ms: " + (late ? "TIMED OUT" : missed ? "NOT FOUND" : "ok")
                    + " picks=" + picks + " query=" + query + " picked=" + pickedName);
            if (channel && picks == 0 && (late || missed)) { giveUp(); return; }
            target = null;
            if (late) { loud(); hideCover(0); }
            else { if (!awaitingSound) loud(); hideCover(hideDelay); }
        }
    };

    /** A channel tune that found nothing to pick, or ran out of time; set by nextCandidate. */
    private boolean missed;
    /** The text of what was picked, for the log. */
    private String pickedName;

    /**
     * A channel not found: TiviMate's search closed if it is still on screen (so it is never left over the picture),
     * and the cover says so for a moment before it lifts, rather than lifting on some other channel without a word.
     */
    private void giveUp() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && target != null && target.contentEquals(String.valueOf(root.getPackageName()))
                && find(root, Kind.EDIT) != null) {
            if (keyboardShown()) performGlobalAction(GLOBAL_ACTION_BACK);
            main.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 250);
        }
        target = null;
        loud();
        if (coverTitle != null) {
            String name = query == null ? "" : query.startsWith("match:") ? query.substring(6).replace("|", " v ") : query;
            coverTitle.setAllCaps(false);
            coverTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
            coverTitle.setText("Couldn't find " + name);
            coverTitle.setVisibility(android.view.View.VISIBLE);
        }
        hideCover(2_200);
    }

    /**
     * Silence while the helper finds a channel: TiviMate's search box is a voice search that bleeps as it starts
     * listening (again each time it clears the name). The sound comes back the moment the channel is picked, or
     * the helper gives up or is stopped, and in any case after half a minute.
     */
    private boolean tuneMuted;
    private final Runnable loudLater = this::loud;

    private void quiet() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null || tuneMuted) return;
        for (int stream : new int[]{android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_SYSTEM,
                android.media.AudioManager.STREAM_NOTIFICATION}) {
            try { am.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_MUTE, 0); } catch (RuntimeException ignored) { }
        }
        tuneMuted = true;
        main.removeCallbacks(loudLater);
        main.postDelayed(loudLater, 30_000L);
        holdFocus(true);
    }

    /**
     * The sound taken from the app while the helper searches: a box whose sound goes out over HDMI (the Streamer) does
     * not mute, and TiviMate plays its last channel as it opens. Holding the audio focus pauses that player (as a
     * call or the assistant would); taken back each time TiviMate asks for it, until the channel is picked.
     */
    private android.media.AudioFocusRequest focus;
    private boolean holding;

    private void holdFocus(boolean on) {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        holding = on;
        if (!on) {
            if (focus != null) am.abandonAudioFocusRequest(focus);
            focus = null;
            return;
        }
        if (focus == null) {
            // Transient, as the assistant's: TiviMate pauses while it is held and plays on by itself once it is given
            // back. Held for good (AUDIOFOCUS_GAIN) its player stayed paused afterwards whenever the channel picked was
            // the one already on (the Live Centre and the F1 timing put on what is often already playing).
            focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setOnAudioFocusChangeListener(change -> {
                        android.util.Log.i(TAG, "focus change " + change + (holding ? " (taken back)" : ""));
                        // TiviMate took it (its last channel starting): taken back at once while still searching.
                        if (holding && change < 0) main.post(regain);
                    }, main)
                    .build();
        }
        android.util.Log.i(TAG, "focus asked: " + am.requestAudioFocus(focus));
        main.removeCallbacks(regain);
        main.postDelayed(regain, 300L);
    }

    /** While searching, the focus asked for again every few moments (TiviMate asks again as each player starts). */
    private final Runnable regain = new Runnable() {
        @Override public void run() {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (!holding || focus == null || am == null) return;
            am.requestAudioFocus(focus);
            main.postDelayed(this, 300L);
        }
    };

    /**
     * The sound back on once the channel picked is playing: while TiviMate is still on the old one its sound goes
     * on, then stops while the new one loads, then starts again. On at that start (or after ten seconds at most).
     */
    private boolean awaitingSound, soundGap;
    private long soundFrom;
    private final Runnable soundWatch = new Runnable() {
        @Override public void run() {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            boolean playing = am != null && am.isMusicActive();
            if (!playing) soundGap = true;
            if (soundGap && playing || System.currentTimeMillis() - soundFrom > 10_000L || !tuneMuted) {
                awaitingSound = false;
                main.postDelayed(loudLater, playing ? 300L : 0L);
                return;
            }
            main.postDelayed(this, 150L);
        }
    };

    private void loudWhenNew() {
        holding = false;   // picked: the new channel takes the focus back as it starts
        if (!tuneMuted || awaitingSound) return;
        awaitingSound = true;
        soundGap = false;
        soundFrom = System.currentTimeMillis();
        main.removeCallbacks(loudLater);
        main.postDelayed(loudLater, 30_000L);
        main.postDelayed(soundWatch, 150L);
    }

    private void loud() {
        holdFocus(false);
        main.removeCallbacks(loudLater);
        main.removeCallbacks(soundWatch);
        awaitingSound = false;
        if (!tuneMuted) return;
        tuneMuted = false;
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        for (int stream : new int[]{android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_SYSTEM,
                android.media.AudioManager.STREAM_NOTIFICATION}) {
            try { am.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_UNMUTE, 0); } catch (RuntimeException ignored) { }
        }
    }

    /** When the search button and the profile were last pressed (never twice within a moment). */
    private long searchAt, profileAt;

    private static long now() {
        return System.currentTimeMillis();
    }

    /** One look at the screen and one move towards the search: true to look again. */
    private boolean advance() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        // Home in front once the app had time to open: the person left, and the helper goes with them.
        // (Tuya Smart, a large app, can take several seconds to show its first window from cold.)
        if (root != null && getPackageName().contentEquals(String.valueOf(root.getPackageName()))
                && System.currentTimeMillis() - startedAt > (camera ? 9_000 : 2_500)) {
            removeCover();
            return false;
        }
        if (root == null || root.getPackageName() == null || !target.contentEquals(root.getPackageName())) return true;
        if (channel) return tuneStep(root);
        if (kleva) return klevaStep(root);
        if (camera) return cameraStep(root);
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        if (opened) {
            // Picked: done once the search has given way to the title's page, or with an episode asked for (Surprise
            // me), once that episode is found on it.
            if (box == null) {
                if (epPlayAt > 0) return playerStep(root);
                if (epSeason > 0) return episodeStep(root);
                if (playMode != 0) return coverPlayStep(root);
                hideDelay = 700;
                return false;
            }
            return true;
        }
        if (box != null) {
            if (typedAt == 0) {
                Bundle text = new Bundle();
                text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        spelling < spellings.size() ? spellings.get(spelling) : query);
                if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) {
                    typedAt = System.currentTimeMillis();
                    deadline = Math.max(deadline, typedAt + 12_000L);
                    trace("debrid: typed " + (spelling < spellings.size() ? spellings.get(spelling) : query));
                }
                return true;
            }
            // The app shows results as the text goes in, but its field brings up the keyboard over them: one Back
            // takes the keyboard away (only while it shows, so Back never leaves the search).
            if (keyboardShown()) { performGlobalAction(GLOBAL_ACTION_BACK); return true; }
            // Then the film or series of that name, so its page opens; without one, the results stay on screen.
            AccessibilityNodeInfo hit = result(root);
            if (hit != null) {
                click(hit);
                opened = true;
                deadline = Math.max(deadline, now() + 8_000L);
                // A poster has no words of its own, only its description: that names what was picked.
                trace("debrid: picked " + (hit.getText() != null ? hit.getText() : hit.getContentDescription()));
                return true;
            }
            // Nothing of that name after a few seconds: the next way of typing it, with the time to find it.
            long waited = System.currentTimeMillis() - typedAt;
            if (waited > 3_500L && spelling + 1 < spellings.size()) {
                spelling++;
                typedAt = 0;
                deadline = Math.max(deadline, System.currentTimeMillis() + 6_000L);
                return true;
            }
            return waited < 8_000L;
        }
        AccessibilityNodeInfo search = find(root, Kind.SEARCH);
        if (search != null && now() - searchAt > 1_200L) {
            click(search);
            searchAt = now();
            trace("debrid: search opened");
            // Its search screen and keyboard to come: the time for them.
            deadline = Math.max(deadline, now() + 12_000L);
            return true;
        }
        if (find(root, Kind.PROFILE) != null && now() - profileAt > 1_000L) {
            // The profile picker (each time the app starts afresh): the profile it highlights is the one last used,
            // chosen with the remote's OK. Time then for the app's home to load.
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            profileAt = now();
            if (focused != null) {
                press(focused);
            } else if (android.os.Build.VERSION.SDK_INT >= 33 && now() - startedAt > 1_500L) {
                // No focus reported yet: OK goes to whatever the app has focused, which on its picker is a profile.
                QuietHold.okSentAt = android.os.SystemClock.uptimeMillis();
                performGlobalAction(GLOBAL_ACTION_DPAD_CENTER);
            }
            trace("debrid: profile picked (focus " + (focused != null) + ")");
            deadline = Math.max(deadline, now() + 15_000L);
        }
        return true;
    }

    /**
     * TiviMate: its search from the menu, the channel's name typed in, then the found channel of that name picked.
     * Done once the search screen has gone (the channel plays), or after a few picks.
     */
    /** What the helper is doing, in the log when it changes (adb logcat -s AppSearch): to see where a tune stops. */
    private String lastTrace;

    private void trace(String s) {
        if (s.equals(lastTrace)) return;
        lastTrace = s;
        android.util.Log.i(TAG, s);
    }

    // ---- Tuya Smart: a camera's live view -----------------------------------------------------------------------

    /** Opening a camera in Tuya Smart (query is the camera's name). */
    private boolean camera;

    /** The app in front, as the helper sees it; null without the helper. */
    static String front() {
        AppSearchService s = instance;
        if (s == null) return null;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            return root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * A camera's live view: Tuya Smart opened, and the camera of that name picked from its list of devices, behind
     * the cover. False when Tuya Smart is not on this TV; without the helper it opens as it was.
     */
    static boolean camera(Context c, String name) {
        Intent open = c.getPackageManager().getLaunchIntentForPackage(CameraAlert.TUYA);
        if (open == null) return false;
        // What was on, for Back on the camera to go back to (cameraBack).
        String was = front();
        returnTo = was == null || CameraAlert.TUYA.equals(was) ? (returnTo != null ? returnTo : c.getPackageName()) : was;
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(open);
        } catch (Exception e) {
            return false;
        }
        if (instance != null && name != null && !name.trim().isEmpty()) {
            instance.start(CameraAlert.TUYA, name.trim());
            instance.camera = true;
            instance.cameraMoved = 0;
            instance.cameraBacks = 0;
            instance.pageAt = 0;
            instance.unzoom();
            // From cold, Tuya Smart takes a quarter of a minute to start and the camera's page as long again.
            instance.deadline = System.currentTimeMillis() + 60_000L;
            instance.showCover(null, null, name.trim(), LIVE, 12_000L);
        }
        return true;
    }

    /** Tuya Smart's camera page: its button for the picture full screen, and the one that leaves full screen. */
    private static final String FULL = "tuya_ipc_fullscreen", FULL_BACK = "tuya_ipc_fullscreen_back";
    /** Tuya: when the helper last got somewhere, and the Backs pressed from a page that was not the way. */
    private long cameraMoved, pageAt;
    private int cameraBacks;
    /** The camera's picture on screen while it is magnified to fill it; null when not. */
    private android.graphics.Rect zoomed;

    /**
     * Tuya Smart to the camera full screen: from its home (the camera's card, by name) to the camera's page, and its
     * full-screen button there, which turns the picture to fill the TV. Tuya keeps its place, so it may open on the
     * camera's page already, or on another page: Back from there, a few times at most, to its home.
     */
    /** The app that was on when the camera was opened from here; null when the camera was not. */
    private static volatile String returnTo;

    /** Whether Tuya Smart's camera page is in front (full screen or not). */
    static boolean onCameraPage() {
        AppSearchService s = instance;
        if (s == null) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null || !CameraAlert.TUYA.contentEquals(String.valueOf(root.getPackageName()))) return false;
            return described(root, FULL_BACK) != null || described(root, FULL) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Back on the camera's page (InputKeyService passes it here): straight back to what was on, Tuya Smart left as it
     * is, on the camera's page, so the camera opens again in moments next time instead of from Tuya's home. Only for
     * a camera opened from the launcher; otherwise Back is Tuya's.
     */
    static boolean cameraBack(Context c, android.view.KeyEvent e) {
        String to = returnTo;
        if (to == null) return false;
        Log.i(TAG, "camera: back " + e.getAction() + " to " + to);
        if (!onCameraPage()) {
            returnTo = null;   // Back somewhere else: the camera was left some other way
            return false;
        }
        if (e.getAction() == android.view.KeyEvent.ACTION_UP && !e.isCanceled()) {
            returnTo = null;
            if (instance != null) instance.unzoom();
            Intent back = to.equals(c.getPackageName()) ? null : c.getPackageManager().getLeanbackLaunchIntentForPackage(to);
            if (back == null && !to.equals(c.getPackageName())) back = c.getPackageManager().getLaunchIntentForPackage(to);
            if (back == null) back = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            try {
                c.startActivity(back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
            }
        }
        return true;
    }

    private boolean cameraStep(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        if (cameraMoved == 0) cameraMoved = now;
        AccessibilityNodeInfo full = described(root, FULL), back = described(root, FULL_BACK);
        trace("camera: full=" + (full != null) + " fullBack=" + (back != null) + " picked=" + opened + " backs=" + cameraBacks);
        // Full screen: the picture is on, and the cover goes after a moment for it to settle.
        // The cover goes once the picture is coming in (Tuya shows its speed over it), or after a few seconds.
        boolean coming = false;
        if (back != null || full != null) {
            if (pageAt == 0) pageAt = now;
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText("KB/S")) coming |= n.isVisibleToUser();
        }
        // The camera's page full screen: Tuya, allowed to fill the TV (its portrait lock overridden once over ADB:
        // am compat enable OVERRIDE_ANY_ORIENTATION and OVERRIDE_UNDEFINED_ORIENTATION_TO_NOSENSOR), opens it so.
        if (back != null) {
            unzoom();   // Tuya's own full screen: no magnifier as well
            if (coming || now - pageAt > 8_000L) { hideDelay = 300; return false; }
            return true;
        }
        if (full != null) {
            // The camera's page as a tall phone page. Its own full-screen button takes only a touch, and this TV has
            // no touch screen, so the picture is brought up to fill the screen by Android's magnifier instead.
            if (zoomed == null) zoomed = zoom(root);
            if (zoomed != null && (coming || now - pageAt > 8_000L)) { hideDelay = 300; return false; }
            return true;
        }
        // Tuya's one-time "Notification Test" as a camera opens: not shown again, and away.
        if (!root.findAccessibilityNodeInfosByText("Notification Test").isEmpty()) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText("show this message again")) click(n);
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText("Cancel")) {
                if (n.getText() != null && "Cancel".contentEquals(n.getText())) { click(n); break; }
            }
            return true;
        }
        if (!opened || now - pickedAt > 6_000L) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(query)) {
                if (n.getText() == null || !n.isVisibleToUser() || !query.equalsIgnoreCase(n.getText().toString().trim())) continue;
                if (clickable(n)) click(n);
                else tap(n);
                opened = true;
                pickedAt = cameraMoved = now;
                return true;
            }
        }
        // Somewhere else in Tuya for a while: its Cameras tab when it has one, or Back towards its home.
        if (!opened && now - cameraMoved > 4_000L) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText("Cameras")) {
                if (n.getText() != null && n.isVisibleToUser() && "Cameras".equalsIgnoreCase(n.getText().toString().trim())) {
                    click(n);
                    cameraMoved = now;
                    return true;
                }
            }
            if (cameraBacks < 3) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                cameraBacks++;
                cameraMoved = now;
            }
        }
        return true;
    }

    /**
     * The camera's picture magnified to fill the screen: Tuya shows it 16:9 across its page, centred in the space
     * between its top bar and its buttons (the largest view that touches neither end of the page). Kept so while
     * the camera's page is in front, and back to normal the moment it is not (unzoomWatch).
     */
    private android.graphics.Rect zoom(AccessibilityNodeInfo root) {
        android.graphics.Rect page = new android.graphics.Rect(), area = null, r = new android.graphics.Rect();
        root.getBoundsInScreen(page);
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        for (AccessibilityNodeInfo n : all) {
            n.getBoundsInScreen(r);
            if (r.width() < page.width() * 0.95f || r.top <= page.top + 10 || r.bottom >= page.bottom - 10) continue;
            if (area == null || r.width() * (long) r.height() > area.width() * (long) area.height()) area = new android.graphics.Rect(r);
        }
        if (area == null || area.height() < area.width() * 9 / 16) return null;
        int h = area.width() * 9 / 16;
        android.graphics.Rect video = new android.graphics.Rect(area.left, area.centerY() - h / 2, area.right, area.centerY() - h / 2 + h);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float scale = Math.min(dm.widthPixels / (float) video.width(), dm.heightPixels / (float) video.height());
        try {
            MagnificationController mc = getMagnificationController();
            mc.setScale(scale, false);
            mc.setCenter(video.exactCenterX(), video.exactCenterY(), false);
        } catch (RuntimeException e) {
            Log.i(TAG, "camera: no magnifier " + e);
            return null;
        }
        Log.i(TAG, "camera: zoomed " + video.toShortString() + " x" + scale);
        main.removeCallbacks(unzoomWatch);
        main.postDelayed(unzoomWatch, 1_000L);
        return video;
    }

    /** Back to normal size once the camera's page has gone (Back, Home, another app). */
    private final Runnable unzoomWatch = new Runnable() {
        @Override public void run() {
            if (zoomed == null) return;
            AccessibilityNodeInfo root = null;
            try { root = getRootInActiveWindow(); } catch (RuntimeException ignored) { }
            boolean still = root != null && CameraAlert.TUYA.contentEquals(String.valueOf(root.getPackageName()))
                    && described(root, FULL) != null && described(root, FULL_BACK) == null;
            if (still || (root == null && System.currentTimeMillis() - pickedAt < 2_000L)) {
                main.postDelayed(this, 400L);
                return;
            }
            unzoom();
        }
    };

    private void unzoom() {
        main.removeCallbacks(unzoomWatch);
        if (zoomed == null) return;
        zoomed = null;
        try { getMagnificationController().reset(false); } catch (RuntimeException ignored) { }
        Log.i(TAG, "camera: unzoomed");
    }

    /** The node whose description is exactly this (Tuya's React Native buttons are named so). */
    private static AccessibilityNodeInfo described(AccessibilityNodeInfo root, String desc) {
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(desc)) {
            if (n.getContentDescription() != null && desc.contentEquals(n.getContentDescription())) return n;
        }
        return null;
    }

    /** Whether the node, or one above it, takes a click. */
    private static boolean clickable(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo at = n; at != null; at = at.getParent()) if (at.isClickable()) return true;
        return false;
    }

    /** A touch in the middle of the node, for a view that takes no click from here. */
    private void tap(AccessibilityNodeInfo n) {
        android.graphics.Rect r = new android.graphics.Rect();
        n.getBoundsInScreen(r);
        if (r.isEmpty()) return;
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        boolean sent = dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 80)).build(),
                new GestureResultCallback() {
                    @Override public void onCompleted(android.accessibilityservice.GestureDescription g) { Log.i(TAG, "tap done " + r.centerX() + "," + r.centerY()); }
                    @Override public void onCancelled(android.accessibilityservice.GestureDescription g) { Log.i(TAG, "tap cancelled"); }
                }, main);
        if (!sent) Log.i(TAG, "tap refused");
    }

    // ---- Kleva TV: a game found by its search and played ---------------------------------------------------------

    private static final String KLEVA = "com.plaintextdev.klevaTv";
    /** Playing a game in Kleva TV (query is what is typed; teams are its clubs, or one title for an event). */
    private boolean kleva;
    private String[] teams;

    /**
     * Kleva TV on a game: its search opened by its own link, a club's name typed, and the first channel or programme
     * found that names both clubs (Kleva lists the best first: the event channel, then the broadcasters) picked, so it
     * plays. False when Kleva TV is not on this TV; without the helper it opens on its search.
     */
    static boolean kleva(Context c, String home, String away, String title, android.graphics.Bitmap art, android.graphics.Bitmap logo, Runnable missed) {
        Intent open = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("klevatv://search")).setPackage(KLEVA);
        // Afresh: brought back as it was, Kleva played on whatever it played last behind its search.
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            c.startActivity(open);
        } catch (Exception e) {
            return false;
        }
        if (instance != null) {
            instance.start(KLEVA, home != null ? home : title);
            instance.kleva = true;
            instance.focused = instance.moves = 0;
            instance.klevaMissed = missed;
            instance.teams = home != null && away != null ? new String[]{home, away} : new String[]{title};
            instance.showCover(art, logo, title, LIVE, 9_000L);
        }
        return true;
    }

    /**
     * Kleva TV on a channel: its search, the channel's name typed (without its HD or UHD), and the card for that
     * channel picked, which plays it full screen. Kleva keeps one card per channel, its HD, FHD and 4K copies together,
     * so there is no long row to page through as in TiviMate. missed: when it has no such channel (TiviMate then).
     * False when Kleva TV is not on this TV, or the helper is off.
     */
    static boolean klevaChannel(Context c, java.util.List<String> names, android.graphics.Bitmap logo, android.graphics.Bitmap art, Runnable missed) {
        if (instance == null || names.isEmpty()) return false;
        Intent open = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("klevatv://search")).setPackage(KLEVA);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            c.startActivity(open);
        } catch (Exception e) {
            return false;
        }
        String first = names.get(0);
        String typed = first.replaceAll("(?i)\\s*\\b(f?hd|uhd|4k|sd)\\b\\s*", " ").replaceAll("\\s+", " ").trim();
        if (typed.length() < 3) typed = searchTerm(first);
        instance.start(KLEVA, typed);
        instance.kleva = true;
        instance.focused = instance.moves = 0;
        instance.klevaMissed = missed;
        instance.teams = null;
        java.util.List<String> want = new java.util.ArrayList<>();
        for (String n : names) { String w = channelName(n, false); if (!w.isEmpty() && !want.contains(w)) want.add(w); }
        instance.wanted = want;
        instance.quiet();
        instance.showCover(art, logo, first, LIVE, 6_000L);
        return true;
    }

    /** Kleva on a channel: the names wanted, as channelName writes them; null for a game. */
    private java.util.List<String> wanted;

    /** Whether a line of Kleva's names one of the channels wanted ("UK: Sky Sports Main Event", ": Sky Atlantic"). */
    private boolean channelLine(String t) {
        String bare = t.replaceFirst("^\\s*[A-Za-z]{0,3}\\s*[|:]\\s*", "");
        String n = channelName(bare, false);
        if (wanted.contains(n)) return true;
        String loose = channelName(bare, true);
        for (String w : wanted) if (loose.equals(w)) return true;
        return false;
    }

    /** Kleva: tries at focusing the game's card, and Downs pressed to reach it. */
    private int focused, moves;
    /** What to do when Kleva TV's search has nothing for the game. */
    private Runnable klevaMissed;
    private long lastMove;

    /** How many nodes a view holds: a card is a few, a list of them many. */
    private static int size(AccessibilityNodeInfo n) {
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(n, all, 0);
        return all.size();
    }

    /** Whether this node, or the text under it, names the game. */
    private boolean names(AccessibilityNodeInfo n) {
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(n, all, 0);
        for (AccessibilityNodeInfo x : all) {
            if (x.isEditable() || x.getText() == null) continue;
            String t = x.getText().toString();
            if (wanted != null) { if (channelLine(t)) return true; continue; }
            if (teams.length == 2 ? PlaylistSport.same(t, teams[0], teams[1])
                    : PlaylistSport.plain(t).contains(PlaylistSport.plain(teams[0]))) return true;
        }
        return false;
    }

    /**
     * On a channel, the remote's step towards the card that names it, from the card in focus: Down while the focus is
     * still above the results (the search, its chips), else Right or Left along the row.
     */
    private static int towards(AccessibilityNodeInfo card, AccessibilityNodeInfo line) {
        if (card == null) return GLOBAL_ACTION_DPAD_DOWN;
        android.graphics.Rect at = new android.graphics.Rect(), goal = new android.graphics.Rect();
        card.getBoundsInScreen(at);
        line.getBoundsInScreen(goal);
        if (goal.top >= at.bottom) return GLOBAL_ACTION_DPAD_DOWN;
        if (goal.left >= at.right) return GLOBAL_ACTION_DPAD_RIGHT;
        if (goal.right <= at.left) return GLOBAL_ACTION_DPAD_LEFT;
        return GLOBAL_ACTION_DPAD_DOWN;
    }

    /** On a channel: whether the focused card is as good a picture (UHD, HD...) as any card naming the channel. */
    private boolean bestCardHere(AccessibilityNodeInfo root, AccessibilityNodeInfo card, int floor) {
        int here = quality(cardText(card)), best = here;
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable() || n.getText() == null || top(n) < floor || !names(n)) continue;
            best = Math.max(best, quality(n.getText().toString()));
        }
        return here >= best;
    }

    /** A card's lines, for the log. */
    private static String cardText(AccessibilityNodeInfo card) {
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(card, all, 0);
        StringBuilder b = new StringBuilder();
        for (AccessibilityNodeInfo x : all) if (x.getText() != null) b.append(b.length() > 0 ? " / " : "").append(x.getText());
        return b.toString();
    }

    private boolean klevaStep(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        trace("kleva: box=" + (box != null) + " picked=" + opened + " moves=" + moves + " text=" + (box == null ? "" : box.getText()));
        if (opened) {
            // Picked: playing once the search is out of sight. Kleva keeps its search under the player, so a few
            // seconds after the pick count as started too (the cover had stayed over the game till the end).
            if (box == null || !box.isVisibleToUser() || System.currentTimeMillis() - pickedAt > 3_000L) { hideDelay = 400; return false; }
            return true;
        }
        if (box == null) {
            // Not on its search (it came back where it was): its Search button.
            AccessibilityNodeInfo search = find(root, Kind.SEARCH);
            if (search != null && System.currentTimeMillis() - lastSearch > 1_500L) { click(search); lastSearch = System.currentTimeMillis(); }
            return true;
        }
        if (typedAt == 0) {
            Bundle text = new Bundle();
            text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
            if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) typedAt = System.currentTimeMillis();
            return true;
        }
        if (keyboardShown()) { performGlobalAction(GLOBAL_ACTION_BACK); return true; }
        // Kleva's cards are React Native views that take no click from here: the remote's way in instead, the game's
        // card focused (by Down from the search, as a person would, when it won't take focus) and OK pressed on it.
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        // (Kleva's card is its picture, which takes the focus, with the programme and channel beside it.)
        AccessibilityNodeInfo card = focus == null || focus.isEditable() ? null : focus.getParent();
        // On a channel, only what is under the results' heading counts: the recent searches above it can carry the
        // very name typed, and OK there would only search again.
        int floor = wanted == null ? Integer.MIN_VALUE : resultsTop(root);
        if (wanted != null && floor == Integer.MAX_VALUE) return System.currentTimeMillis() - typedAt < 7_000L || klevaMiss();
        if (card != null && top(card) < floor) card = null;
        if (card != null && size(card) < 16 && names(card) && (wanted == null || bestCardHere(root, card, floor))) {
            if (android.os.Build.VERSION.SDK_INT >= 33) performGlobalAction(GLOBAL_ACTION_DPAD_CENTER);
            else focus.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            opened = true;
            picks++;
            pickedAt = System.currentTimeMillis();
            pickedName = cardText(card);
            if (wanted != null) loudWhenNew();
            return true;
        }
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        // On a channel, its best picture among the cards (UHD, then HD, then the rest): that card is the one to reach.
        AccessibilityNodeInfo best = null;
        if (wanted != null) {
            int bestQ = -1;
            for (AccessibilityNodeInfo n : all) {
                if (n.isEditable() || n.getText() == null || top(n) < floor || !names(n)) continue;
                int q = quality(n.getText().toString());
                if (q > bestQ) { best = n; bestQ = q; }
            }
        }
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable() || n.getText() == null || top(n) < floor || !names(n)) continue;
            if (best != null && !n.equals(best)) continue;
            AccessibilityNodeInfo parent = n.getParent();
            for (int i = 0; parent != null && i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo at = parent.getChild(i);
                if (at != null && at.isFocusable()) { at.performAction(AccessibilityNodeInfo.ACTION_FOCUS); break; }
            }
            // Still elsewhere after that: one Down a step, from the search to the chips to the first card.
            long now = System.currentTimeMillis();
            if (focused++ > 0 && moves < (wanted != null ? 12 : 4) && android.os.Build.VERSION.SDK_INT >= 33 && now - lastMove > (wanted != null ? 350L : 500L)) {
                performGlobalAction(wanted != null ? towards(card, n) : GLOBAL_ACTION_DPAD_DOWN);
                moves++;
                lastMove = now;
            }
            return true;
        }
        // Nothing yet: the results come as it searches. None in the end (Kleva's copy of the playlist can be days
        // old, without tonight's game): the game played some other way instead of leaving its search on screen.
        if (System.currentTimeMillis() - typedAt < (wanted != null ? 7_000L : 10_000L)) return true;
        return klevaMiss();
    }

    /** Kleva has nothing for it: the other way (klevaMissed), and this tune ends. */
    private boolean klevaMiss() {
        missed = true;
        android.util.Log.i(TAG, "kleva: nothing for " + query + (klevaMissed != null ? ", trying the other way" : ""));
        Runnable r = klevaMissed;
        klevaMissed = null;
        if (r != null) main.post(r);
        return false;
    }

    private static int top(AccessibilityNodeInfo n) {
        android.graphics.Rect r = new android.graphics.Rect();
        n.getBoundsInScreen(r);
        return r.top;
    }

    /** The top of Kleva's results ("Channels & Programmes" and the like), or MAX_VALUE before there are any. */
    private static int resultsTop(AccessibilityNodeInfo root) {
        for (AccessibilityNodeInfo h : root.findAccessibilityNodeInfosByText("Channels")) {
            if (h.isEditable() || h.getText() == null || !h.getText().toString().trim().toLowerCase(Locale.ROOT).startsWith("channels")) continue;
            return top(h);
        }
        return Integer.MAX_VALUE;
    }

    /** A game's channels already picked that TiviMate would only remind about (not on yet there): passed over. */
    private final java.util.Set<String> tried = new java.util.HashSet<>();
    private String pickedText;

    private boolean tuneStep(AccessibilityNodeInfo root) {
        // Picked a channel that only has the game later: TiviMate offers to remind or record. That away, the next one.
        if (picks > 0 && query.startsWith("match:") && !root.findAccessibilityNodeInfosByText("Remind").isEmpty()
                && !root.findAccessibilityNodeInfosByText("Record").isEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            if (pickedText != null) tried.add(pickedText);
            picks = 0;
            return true;
        }
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        AccessibilityNodeInfo rowNow = box == null ? null : channelRow(root);
        trace("tune: box=" + (box != null) + " picks=" + picks + " typed=" + (typedAt > 0) + " row=" + (rowNow == null ? "none" : rowNow.getChildCount())
                + " keyboard=" + keyboardShown() + " text=" + (box == null ? "" : box.getText()));
        if (box == null) {
            // Picked, and the search gone: the channel is starting.
            if (picks > 0) { hideDelay = 1500; return false; }
            // Its menu's search (it answers a click even with the menu folded away), once in a while; when two
            // clicks have not brought it up, Back, to leave whatever is over it.
            long now = System.currentTimeMillis();
            AccessibilityNodeInfo search = menuSearch(root);
            if (search != null && now - lastSearch > 1_500L && searches < 2) {
                search.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                lastSearch = now;
                searches++;
            } else if (now - lastSearch > 2_500L && now - lastBack > 2_500L && now - startedAt > 3_000L) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                lastBack = now;
                searches = 0;
            }
            return true;
        }
        if (picks > 0) {
            // Picked but still searching: once more after a moment, in case the first did not take; then, as TiviMate
            // stays on its search when the channel picked is the one already playing, Back to the channel. (Sooner
            // than before: the search sat over the picture for five seconds.)
            long since = System.currentTimeMillis() - pickedAt;
            AccessibilityNodeInfo results = channelRow(root);
            if (picks < 2 && since > 1_800L) {
                AccessibilityNodeInfo again = results == null ? null : channelNode(results, box);
                if (again != null) pick(again);
                else picks = 2;   // nothing to pick again: on to Back
            } else if (picks >= 2 && since > 1_500L) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                pickedAt = System.currentTimeMillis();
                picks++;
                if (picks > 4) return false;
            }
            return true;
        }
        if (typedAt == 0) {
            // Where its search is listening for a voice (a TV with a microphone remote), the box takes no typing
            // until it is picked: picked first, which turns it to the keyboard.
            if (retypes > 0) {
                box.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                box.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            Bundle text = new Bundle();
            text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, searchTerm(query));
            boolean set = box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text);
            if (set) {
                typedAt = System.currentTimeMillis();
                // Its search runs on the keyboard's Enter, not as the text goes in.
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    box.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                }
            }
            return true;
        }
        AccessibilityNodeInfo row = channelRow(root);
        // Only what the search found counts: the channel's name elsewhere on screen (the recent channels behind the
        // search, the info bar) is no pick, and before the name is in the box there is nothing found yet.
        CharSequence typed = box.getText();
        boolean inBox = typed != null && searchTerm(query).contentEquals(typed);
        AccessibilityNodeInfo hit = inBox && row != null ? channelNode(row, box) : null;
        // A 4K (UHD) version of the channel first: a plain match is noted, the row looked through a little further for
        // a 4K one, and gone back to when there is none.
        if (hit != null && (uhd(hit) || settle || query.startsWith("match:"))) return pick(hit);   // a game: the first channel with it
        if (hit != null && foundAt < 0) foundAt = scrolls;
        if (foundAt >= 0 && !backing && scrolls - foundAt >= 3) backing = true;
        if (backing) {
            if (scrolls > foundAt && row != null) {
                row.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                scrolls--;
                return true;
            }
            // Back where the plain one was, with no 4K one after it: the plain one it is, at the next look.
            backing = false;
            settle = true;
            return true;
        }
        // A big playlist finds more channels than the row shows, and only those on screen can be read: the row moved
        // along a page at a time until the channel turns up; at its end, the next name to try.
        if (row != null && row.getChildCount() > 0) {
            // A page at a time, with a moment for each to fill (the TCL is slow to draw them, and a page passed
            // before it was drawn was never read).
            if (System.currentTimeMillis() - scrolledAt < 450L) return true;
            // (At most 30 pages: past that the name is too common to find this way, and the next name is tried.)
            if (scrolls < 30 && row.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                scrolls++;
                scrolledAt = System.currentTimeMillis();
                return true;
            }
            if (foundAt >= 0) { backing = true; return true; }
            return nextCandidate();
        }
        // Its box is a voice search that starts listening by itself and, hearing nothing, clears what was typed:
        // typed again when that happens before anything was found.
        // On a slower TV (the TCL) it listens for longer and clears the name several times over: typed again, a moment
        // apart, until it stays.
        CharSequence now = box.getText();
        if (retypes < 12 && (now == null || !searchTerm(query).contentEquals(now)) && (typedAt == 0 || System.currentTimeMillis() - typedAt > 1_200L)) {
            retypes++;
            typedAt = 0;
            return true;
        }
        // No results yet: its search runs on Enter, which a slow TV can miss while it is still coming back from a
        // channel: pressed again every few seconds.
        long waited = System.currentTimeMillis() - typedAt;
        if (row == null && waited > 6_000L && System.currentTimeMillis() - enteredAt > 6_000L && android.os.Build.VERSION.SDK_INT >= 30) {
            box.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
            enteredAt = System.currentTimeMillis();
        }
        // Nothing found for this name after twelve seconds: the next.
        if (waited > 12_000L) return nextCandidate();
        return true;
    }

    /** When the row last moved on a page, and Enter was last pressed again in the search. */
    private long scrolledAt, enteredAt;
    /** The nearest channel seen to the one wanted (its name inside the found one's), picked when no name matches. */
    private AccessibilityNodeInfo nearest;

    private boolean pick(AccessibilityNodeInfo hit) {
        // With the keyboard up (the box was picked to type in it) a pick goes to the keyboard: one Back takes it away
        // first, and the pick is made at the next look.
        if (keyboardShown()) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return true;
        }
        picks++;
        pickedAt = System.currentTimeMillis();
        pickedName = String.valueOf(hit.getText());
        click(hit);
        if (channel) loudWhenNew();   // the new channel's own sound, once it starts (not the old one's tail)
        return true;
    }

    /** A channel name's picture: 3 UHD or 4K, 2 FHD or HD, 1 no mark, 0 SD. */
    static int quality(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        if (l.matches(".*(\\b4k\\b|uhd|ultra\\s*hd|2160).*") || name.contains("ᵁᴴᴰ")) return 3;
        if (l.matches(".*\\b(f?hd|1080p?|720p?)\\b.*") || name.contains("ᴴᴰ")) return 2;
        if (l.matches(".*\\bsd\\b.*")) return 0;
        return 1;
    }

    private static boolean uhd(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t == null) return false;
        // "UHD", "4K", "Ultra HD", "2160p", and the small raised "ᵁᴴᴰ" some playlists write.
        String l = t.toString().toLowerCase(Locale.ROOT);
        return l.matches(".*(\\b4k\\b|uhd|ultra\\s*hd|2160|ᵁᴴᴰ).*") || t.toString().contains("ᵁᴴᴰ");
    }

    /** The next channel name to search for, or false when none is left (TiviMate stays on what it found). */
    private boolean nextCandidate() {
        if (candidate + 1 >= candidates.size()) {
            // Every name tried and none found exactly: the nearest one seen, rather than stopping on the search.
            if (picks == 0 && nearest != null) {
                AccessibilityNodeInfo n = nearest;
                nearest = null;
                if (n.refresh() && n.isVisibleToUser()) return pick(n);
            }
            missed = picks == 0;
            return false;
        }
        query = candidates.get(++candidate);
        typedAt = 0;
        retypes = 0;
        scrolls = 0;
        foundAt = -1;
        backing = false;
        settle = false;
        if (cover != null) coverTitle.setText(query);
        return true;
    }

    // ---- the cover over the app while the helper works ---------------------------------------------------

    /** The cover's line for a channel: a breathing red dot and "LIVE", as TiviMate tunes. */
    private static final String LIVE = "\u25CF  LIVE";
    private android.view.View cover, coverBox, coverFill;
    private final java.util.List<android.animation.Animator> coverLoops = new java.util.ArrayList<>();
    private android.widget.TextView coverTitle;
    private android.animation.ValueAnimator coverProgress;

    /**
     * A loading cover over everything while the helper finds its way, so the steps are never seen: a soft blur of the
     * title's poster, its logo popping in (its name, and the line, only without one), and a glass bar that fills as the work goes on, most of the
     * way over about expectMs and the rest once done. Drawn as the helper's own overlay, which takes no keys.
     */
    private void showCover(android.graphics.Bitmap art, android.graphics.Bitmap logo, String title, String line, long expectMs) {
        // The last cover away, but not its sound: a tune has just muted it (quiet() comes first), and this undid that.
        clearCover();
        float d = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout v = new android.widget.FrameLayout(this);
        v.setBackgroundColor(0xFF0B0C10);
        // The poster's colours behind (now, or when the picture arrives), under a shade.
        android.widget.ImageView bg = new android.widget.ImageView(this);
        bg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        if (art != null) bg.setImageBitmap(blur(art));
        v.addView(bg, new android.widget.FrameLayout.LayoutParams(-1, -1));
        coverBg = bg;
        android.view.View shade = new android.view.View(this);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0xD9000000}));
        v.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        // The title's logo (now, or when it arrives; a film or series loads with its logo, not its name).
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        col.addView(mark, new android.widget.LinearLayout.LayoutParams(Math.round(420 * d), Math.round(150 * d)));
        coverMark = mark;
        boolean waitsForLogo = logo == null && line == null;
        mark.setVisibility(logo != null ? android.view.View.VISIBLE : waitsForLogo ? android.view.View.INVISIBLE : android.view.View.GONE);
        {
            if (logo != null) mark.setImageBitmap(logo);
            // The logo breathes a little while it loads.
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(mark,
                    android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f, 1.05f),
                    android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f, 1.05f));
            pulse.setDuration(900);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            pulse.setStartDelay(420);
            pulse.start();
            coverLoops.add(pulse);
        }
        coverTitle = new android.widget.TextView(this);
        coverTitle.setText(title);
        coverTitle.setTextColor(0xFFFFFFFF);
        coverTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, logo != null ? 15f : 38f);
        // With no logo (yet): the name set as a wordmark, heavy, tight and shadowed, where the logo would be.
        coverTitle.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        coverTitle.setLetterSpacing(0.02f);
        coverTitle.setShadowLayer(12 * d, 0, 3 * d, 0x99000000);
        coverTitle.setAllCaps(line == null);
        coverTitle.setGravity(android.view.Gravity.CENTER);
        coverTitle.setMaxLines(2);
        // With a logo, the logo and the bar alone: the name only stands in for a logo there is not.
        coverTitle.setVisibility(logo != null ? android.view.View.GONE : android.view.View.VISIBLE);
        if (waitsForLogo) {
            // Over the logo's place while it is fetched: no jump when it arrives.
            android.widget.LinearLayout.LayoutParams tl = new android.widget.LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = -Math.round(100 * d);
            coverTitle.setLayoutParams(tl);
        }
        col.addView(coverTitle, coverTitle.getLayoutParams() != null ? coverTitle.getLayoutParams() : new android.widget.LinearLayout.LayoutParams(-1, -2));
        if (coverEpisode != null) {
            // An episode: its season and number, small and spaced, then its title, large.
            android.widget.TextView which = new android.widget.TextView(this);
            which.setText(coverEpisode[0]);
            which.setTextColor(0xCCFFFFFF);
            which.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
            which.setLetterSpacing(0.22f);
            which.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            which.setGravity(android.view.Gravity.CENTER);
            which.setShadowLayer(8 * d, 0, 2 * d, 0x99000000);
            android.widget.LinearLayout.LayoutParams wl = new android.widget.LinearLayout.LayoutParams(-2, -2);
            wl.topMargin = Math.round(22 * d);
            col.addView(which, wl);
            if (!coverEpisode[1].isEmpty()) {
                android.widget.TextView named = new android.widget.TextView(this);
                named.setText("\u201C" + coverEpisode[1] + "\u201D");
                named.setTextColor(0xFFFFFFFF);
                named.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f);
                named.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
                named.setGravity(android.view.Gravity.CENTER);
                named.setMaxLines(2);
                named.setShadowLayer(14 * d, 0, 3 * d, 0xB3000000);
                android.widget.LinearLayout.LayoutParams nl = new android.widget.LinearLayout.LayoutParams(-2, -2);
                nl.topMargin = Math.round(6 * d);
                col.addView(named, nl);
            }
        }
        if (line != null && logo == null) {
            android.widget.TextView sub = new android.widget.TextView(this);
            if (LIVE.equals(line)) {
                // The dot in red, breathing.
                android.text.SpannableString live = new android.text.SpannableString(line);
                live.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF453A), 0, 1, 0);
                sub.setText(live);
                sub.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                sub.setLetterSpacing(0.2f);
                android.animation.ObjectAnimator breathe = android.animation.ObjectAnimator.ofFloat(sub, android.view.View.ALPHA, 1f, 0.45f);
                breathe.setDuration(700);
                breathe.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                breathe.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                breathe.start();
                coverLoops.add(breathe);
            } else {
                sub.setText(line);
            }
            sub.setTextColor(0xB3FFFFFF);
            sub.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
            sub.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams slp = new android.widget.LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Math.round(4 * d);
            col.addView(sub, slp);
        }
        // The bar: a clear glass track with a bright fill, its leading edge glowing.
        android.widget.FrameLayout track = new android.widget.FrameLayout(this);
        android.graphics.drawable.GradientDrawable trackBg = new android.graphics.drawable.GradientDrawable();
        trackBg.setColor(0x2EFFFFFF);
        trackBg.setCornerRadius(3 * d);
        trackBg.setStroke(Math.max(1, Math.round(d)), 0x40FFFFFF);
        track.setBackground(trackBg);
        android.view.View fill = new android.view.View(this);
        android.graphics.drawable.GradientDrawable fillBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xB3FFFFFF, 0xFFFFFFFF});
        fillBg.setCornerRadius(3 * d);
        fill.setBackground(fillBg);
        fill.setPivotX(0);
        fill.setScaleX(0f);
        track.addView(fill, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(Math.round(300 * d), Math.round(6 * d));
        blp.topMargin = Math.round(30 * d);
        col.addView(track, blp);
        android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER);
        clp.leftMargin = clp.rightMargin = Math.round(80 * d);
        v.addView(col, clp);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(-1, -1,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        try {
            ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).addView(v, lp);
        } catch (RuntimeException e) {
            return;   // no cover: the steps show, the result is the same
        }
        // In: the cover fades up and the logo pops in.
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(160).start();
        col.setScaleX(0.86f);
        col.setScaleY(0.86f);
        col.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
        // The bar to 90% over about expectMs, easing as it goes, so it never stalls nor finishes early.
        coverProgress = android.animation.ValueAnimator.ofFloat(0f, 0.9f);
        coverProgress.setDuration(expectMs);
        coverProgress.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        coverProgress.addUpdateListener(a -> fill.setScaleX((float) a.getAnimatedValue()));
        coverProgress.start();
        cover = v;
        coverBox = col;
        coverFill = fill;
    }

    /**
     * The cover away once what it hid has drawn (after delay ms): the bar fills, then the logo swells towards the
     * viewer as the cover fades, so the page behind pops in.
     */
    private void hideCover(long delay) {
        android.view.View v = cover, box = coverBox, fill = coverFill;
        if (v == null) return;
        cover = null;
        if (coverProgress != null) coverProgress.cancel();
        for (android.animation.Animator a : coverLoops) a.cancel();
        coverLoops.clear();
        if (fill != null) fill.animate().scaleX(1f).setDuration(Math.max(200, delay)).start();
        main.postDelayed(() -> {
            if (box != null) box.animate().scaleX(1.18f).scaleY(1.18f).setDuration(380)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f)).start();
            v.animate().alpha(0f).setDuration(380).setStartDelay(60).withEndAction(() -> removeView(v)).start();
        }, delay);
    }

    private void removeCover() {
        loud();
        clearCover();
    }

    private void clearCover() {
        for (android.animation.Animator a : coverLoops) a.cancel();
        coverLoops.clear();
        if (cover != null) removeView(cover);
        cover = null;
    }

    private void removeView(android.view.View v) {
        try {
            ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v);
        } catch (RuntimeException ignored) {
            // already gone
        }
    }

    /** The row of channels search found: the list just after its "Channels" heading. */
    private static AccessibilityNodeInfo channelRow(AccessibilityNodeInfo root) {
        for (AccessibilityNodeInfo head : root.findAccessibilityNodeInfosByText("Channels")) {
            if (head.getText() == null || !"channels".equalsIgnoreCase(head.getText().toString().trim())) continue;
            AccessibilityNodeInfo parent = head.getParent();
            if (parent == null) continue;
            boolean after = false;
            AccessibilityNodeInfo found = null;
            for (int i = 0; i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo c = parent.getChild(i);
                if (c == null) continue;
                if (c.equals(head)) { after = true; continue; }
                // The last list after the heading: the channels found. The one between, when there is one, holds the
                // playlists' names ("Royal", "Sanctum"). Counting children told them apart until a UHD channel, found
                // alone, made a list of one: it was passed over, and nothing was ever picked.
                if (after && c.getClassName() != null && c.getClassName().toString().endsWith("GridView") && c.getChildCount() > 0) found = c;
            }
            if (found != null) return found;
        }
        return null;
    }

    /**
     * TiviMate's menu entry for its search: by its name ("Search", as the menu reads it out), as its ids are scrambled
     * afresh in each version (5.4 no longer has the one this looked for first); then that old id, else the first
     * entry of the menu down the left.
     */
    private static AccessibilityNodeInfo menuSearch(AccessibilityNodeInfo root) {
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable()) continue;
            String id = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName().toLowerCase(Locale.ROOT);
            if (!isSearch(plain(n.getText()), plain(n.getContentDescription()), id)) continue;
            for (AccessibilityNodeInfo at = n; at != null; at = at.getParent()) if (at.isClickable()) return at;
        }
        java.util.List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId("ar.tvplayer.tv:id/416");
        if (!byId.isEmpty()) return byId.get(0);
        AccessibilityNodeInfo best = null;
        int bestTop = Integer.MAX_VALUE;
        android.graphics.Rect r = new android.graphics.Rect();
        for (AccessibilityNodeInfo n : all) {
            n.getBoundsInScreen(r);
            if (n.isClickable() && r.left < 40 && r.right <= 460 && r.width() > 200 && r.height() < 120 && r.top < bestTop) {
                best = n;
                bestTop = r.top;
            }
        }
        return best;
    }

    private static void collect(AccessibilityNodeInfo n, java.util.List<AccessibilityNodeInfo> into, int depth) {
        if (n == null || depth > 40) return;
        into.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), into, depth + 1);
    }

    /**
     * What search found with the channel's name: the same name (a 4K or UHD one before the rest), then the same name
     * but for a "(IRE)"-like tag.
     */
    private AccessibilityNodeInfo channelNode(AccessibilityNodeInfo root, AccessibilityNodeInfo box) {
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        if (query.startsWith("match:")) {
            // Any channel, in any playlist, named for the game ("Peacock 03: Elversberg vs. Mainz"): the first found.
            String[] teams = query.substring(6).split("\\|");
            for (AccessibilityNodeInfo n : all) {
                if (n.isEditable() || n.getText() == null || n.equals(box) || teams.length < 2) continue;
                String text = n.getText().toString();
                if (tried.contains(text)) continue;
                if (PlaylistSport.same(text, teams[0], teams[1])) { pickedText = text; return n; }
            }
            return null;
        }
        String want = channelName(query, false), loose = channelName(query, true);
        String term = searchTerm(query).toLowerCase(Locale.ROOT);
        AccessibilityNodeInfo same = null, close = null;
        int sameQ = -1, closeQ = -1;
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable() || n.getText() == null || n.equals(box)) continue;
            String text = n.getText().toString();
            if (nearest == null && term.length() >= 3 && text.toLowerCase(Locale.ROOT).contains(term)) nearest = n;
            // The best picture of the channel: UHD at once, else HD (or FHD) over one with no mark, over SD.
            int q = quality(text);
            if (channelName(text, false).equals(want)) {
                if (q == 3) return n;
                if (q > sameQ) { same = n; sameQ = q; }
            } else if (q > closeQ && channelName(text, true).equals(loose)) {
                close = n;
                closeQ = q;
            }
        }
        return same != null ? same : close;
    }

    /**
     * What to type in TiviMate's search for a channel: its search matches the letters as typed, so a name with its
     * number run in ("ITV1", which a playlist may call "ITV 1") is looked for by its letters alone, and plain "5" as
     * "Channel 5"; the found channels are then matched on the whole name (channelName).
     */
    static String searchTerm(String name) {
        String n = name.trim();
        if (n.equals("5")) return "Channel 5";
        // A game rather than a channel ("match:Home|Away"): searched by the home club's name.
        if (n.startsWith("match:")) return n.substring(6).split("\\|")[0].trim();
        // A playlist's event channel ("03. Elversberg vs Mainz 19:30"): its search finds it by a club's name, not by the
        // whole line with its number and time; the found channel is then matched on the whole name.
        java.util.regex.Matcher ev = java.util.regex.Pattern.compile("^\\s*\\d+\\s*[.:)-]\\s*(.+?)\\s+(?:vs?\\.?|v)\\s+.*$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(n);
        if (ev.matches()) return ev.group(1).trim();
        java.util.regex.Matcher one = java.util.regex.Pattern.compile("^\\s*\\d+\\s*[.:)-]\\s*(.+?)\\s+\\d{1,2}[:.]\\d{2}.*$").matcher(n);
        if (one.matches()) return one.group(1).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z]+)\\d+$").matcher(n);
        return m.matches() ? m.group(1) : n;
    }

    /**
     * A channel's name to compare: lower case, without a playlist's country prefix ("UK:", "UK |") or its picture
     * tags (HD, FHD, UHD, 4K, SD), letters and digits only; "5" is Channel 5. Loose also drops anything in brackets.
     */
    static String channelName(String s, boolean loose) {
        String n = s.toLowerCase(Locale.ROOT).trim();
        n = n.replaceFirst("^(uk|gb)\\s*[:|\\-]\\s*", "");
        if (loose) n = n.replaceAll("\\([^)]*\\)|\\[[^]]*]", " ");
        n = n.replaceAll("\\b(fhd|uhd|hd|sd|4k|hevc|h265|50fps|ultra\\s*hd|2160p?|hdr)\\b", " ").replaceAll("\\s*\\bplus\\b", "+");
        // Raised tag letters ("ᴴᴰ", "ᵁᴴᴰ") and other modifier letters are tags too.
        n = n.replaceAll("[\\u02B0-\\u02FF\\u1D2C-\\u1D6A\\u2070-\\u209F]", "");
        n = n.replaceAll("[^\\p{L}\\p{N}+]", "");
        return n.equals("5") ? "channel5" : n;
    }

    /**
     * Among the results (posters named by their description, under "Movies" and "TV Shows"), the one with the title
     * searched for: in the row for its kind first, the same name before one that only begins the same.
     */
    private AccessibilityNodeInfo result(AccessibilityNodeInfo root) {
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        String want = plain(query);
        if (want.isEmpty()) return null;
        boolean inTv = false;
        AccessibilityNodeInfo[] best = new AccessibilityNodeInfo[4];   // exact in kind, exact, close in kind, close
        for (AccessibilityNodeInfo n : all) {
            CharSequence text = n.getText();
            if (text != null && !n.isEditable()) {
                String t = text.toString().trim().toLowerCase(Locale.ROOT);
                if (t.equals("tv shows") || t.equals("series") || t.equals("tv")) inTv = true;
                else if (t.equals("movies") || t.equals("films")) inTv = false;
            }
            CharSequence desc = n.getContentDescription();
            if (desc == null) continue;
            String d = plain(desc.toString());
            if (d.isEmpty()) continue;
            boolean kind = inTv == wantTv;
            int slot = d.equals(want) ? (kind ? 0 : 1) : (d.startsWith(want) || want.startsWith(d)) && Math.min(d.length(), want.length()) >= 4 ? (kind ? 2 : 3) : -1;
            if (slot >= 0 && best[slot] == null) best[slot] = n;
        }
        for (AccessibilityNodeInfo n : best) if (n != null) return n;
        return null;
    }

    /** A title to compare: lower case, letters and digits only. */
    private static String plain(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private boolean keyboardShown() {
        for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true;
        }
        return false;
    }

    private enum Kind { EDIT, SEARCH, PROFILE }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo n, Kind kind) {
        return find(n, kind, 0);
    }

    /**
     * EDIT: a text field. SEARCH: what is named or labelled search. PROFILE: the title of a "Choose a Profile" screen.
     */
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo n, Kind kind, int depth) {
        if (n == null || depth > 40) return null;
        String text = plain(n.getText()), desc = plain(n.getContentDescription());
        String id = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName().toLowerCase(Locale.ROOT);
        switch (kind) {
            case EDIT:
                if (n.isEditable()) return n;
                break;
            case SEARCH:
                if (isSearch(text, desc, id)) return n;
                break;
            case PROFILE:
                if (isProfilePage(text) || isProfilePage(desc)) return n;
                break;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = find(n.getChild(i), kind, depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    /** A node's words in lower case, curly apostrophes made straight ("Who’s watching?" as "who's watching?"). */
    static String plain(CharSequence cs) {
        return cs == null ? "" : cs.toString().replace('\u2019', '\'').replace('\u2018', '\'').trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Pure: what is named or labelled search: "Search", or "Search movies & shows" and the like (the app's own
     * search button, in its menu or along its top), by name or by id; never the voice search's "Speak to search".
     */
    static boolean isSearch(String text, String desc, String id) {
        if (text.startsWith("speak") || desc.startsWith("speak") || id.contains("voice") || id.contains("mic")) return false;
        return text.equals("search") || desc.equals("search") || text.startsWith("search ") || desc.startsWith("search ")
                || id.endsWith("search") || id.endsWith("search_button") || id.endsWith("btn_search") || id.endsWith("search_icon");
    }

    /** Pure: the heading of a profile picker, however the app words it. */
    static boolean isProfilePage(String t) {
        if (t.isEmpty()) return false;
        return t.contains("choose a profile") || t.contains("choose profile") || t.contains("select a profile")
                || t.contains("select profile") || t.contains("pick a profile") || t.contains("switch profile")
                || t.contains("who's watching") || t.contains("who is watching") || t.contains("whos watching")
                || t.equals("profiles") || t.contains("manage profiles");
    }

    /**
     * The remote's OK on what has the focus (Android 13 on, the same as a press of the remote, which every TV app
     * takes), else a click on the node or the nearest ancestor that takes one.
     */
    private void press(AccessibilityNodeInfo n) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && n != null && n.isFocused()) {
            QuietHold.okSentAt = android.os.SystemClock.uptimeMillis();
            if (performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)) return;
        }
        if (n != null) click(n);
    }

    /** Clicks the node, or the nearest ancestor that takes a click. */
    private static void click(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo at = n; at != null; at = at.getParent()) {
            if (at.isClickable()) { at.performAction(AccessibilityNodeInfo.ACTION_CLICK); return; }
        }
    }

    /**
     * KeepService on the enabled list (added; nothing else is removed or changed), so that accessibility stays on for
     * every app while this helper restarts: with none connected at all, Android 14 apps closing a window at that moment
     * crashed (see KeepService). Needs WRITE_SECURE_SETTINGS; without it, nothing.
     */
    static void keepOn(Context c) {
        if (c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            android.content.ComponentName keep = new android.content.ComponentName(c, KeepService.class);
            String list = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list != null && !list.equals("null")) {
                for (String entry : list.split(":")) if (keep.equals(android.content.ComponentName.unflattenFromString(entry))) return;
            }
            String with = list == null || list.isEmpty() || list.equals("null") ? keep.flattenToShortString() : list + ":" + keep.flattenToShortString();
            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, with);
        } catch (RuntimeException ignored) {
            // The setting is the system's; a refusal leaves it as it was.
        }
    }

    /** Whether Android has this helper bound (connected, or connecting in a process just started), by its own list. */
    private static boolean bound(Context c) {
        try {
            android.view.accessibility.AccessibilityManager am = c.getSystemService(android.view.accessibility.AccessibilityManager.class);
            if (am == null) return false;
            for (android.accessibilityservice.AccessibilityServiceInfo i
                    : am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                android.content.pm.ResolveInfo r = i.getResolveInfo();
                if (r != null && r.serviceInfo != null && c.getPackageName().equals(r.serviceInfo.packageName)
                        && AppSearchService.class.getName().equals(r.serviceInfo.name)) return true;
            }
        } catch (RuntimeException ignored) {
            // not known: as unbound
        }
        return false;
    }

    /**
     * Home is on screen: when this helper is switched on in Settings but not running (a TCL turns accessibility off
     * when it will not start a helper, and starts one only while Home is in front), switch it back on: off the list,
     * on again, and accessibility on, with KeepService on the list throughout so accessibility never goes off for the
     * apps meanwhile. Bound already and only still connecting (Home's process has just started): left to finish,
     * not switched off under it. Needs WRITE_SECURE_SETTINGS, granted from adb; without it, nothing.
     */
    static boolean ensure(Context c) {
        if (instance != null || c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        keepOn(c);
        android.content.ContentResolver cr = c.getContentResolver();
        android.content.ComponentName self = new android.content.ComponentName(c, AppSearchService.class);
        String me = self.flattenToShortString();
        String list = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        // The list may name it either way ("pkg/.AppSearchService" or "pkg/pkg.AppSearchService"): compared as names.
        boolean listed = false;
        for (String entry : list.split(":")) if (self.equals(android.content.ComponentName.unflattenFromString(entry))) listed = true;
        if (!listed) return false;
        // Connecting already: the caller waits for it (whenReady), nothing is switched.
        if (bound(c)) return true;
        StringBuilder others = new StringBuilder();
        for (String entry : list.split(":")) {
            if (entry.isEmpty() || self.equals(android.content.ComponentName.unflattenFromString(entry))) continue;
            if (others.length() > 0) others.append(':');
            others.append(entry);
        }
        String without = others.toString(), with = without.isEmpty() ? me : without + ":" + me;
        new Thread(() -> {
            try {
                android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without);
                Thread.sleep(800);
                android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, with);
                android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            } catch (RuntimeException | InterruptedException ignored) {
                // The setting is the system's; a refusal leaves the helper off, as before.
            }
        }).start();
        return true;
    }

    @Override protected void onServiceConnected() {
        // The helper is bound again after an update or a restart: the alerts with it (alerts_everywhere only).
        if (Config.themeFlag(this, "alerts_everywhere", false)) GoalWatchService.startEverywhere(this);
        super.onServiceConnected();
        instance = this;
        BoardCover.attach(this);
        playerKeysCheck(this);
    }

    @Override public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        instance = null;
        main.removeCallbacksAndMessages(null);
        removeCover();
        if (assistCover != null) removeView(assistCover);
        super.onDestroy();
    }

    // ---- Continue Watching: Debrid Stream's own row, copied for Home -------------------------------------

    static final String CONTINUE = "debrid_continue";
    private static final String HEADING = "continue watching, ";
    /** The titles seen in the row, in its order as far as it is known, and whether the last look was in the row. */
    private final java.util.List<String> seen = new java.util.ArrayList<>();
    private boolean inRow;
    private long lastLook;

    /**
     * Debrid Stream draws its home as one view that names only what is highlighted ("Continue Watching, Spider-Man:
     * Brand New Day"), so its row is learnt as it is browsed: each title highlighted there is kept, the one the row is
     * entered on first (it is the row's first card, the one last watched). Copied for Home after each change.
     */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (target != null || System.currentTimeMillis() - lastLook < 300) return;
        lastLook = System.currentTimeMillis();
        CharSequence from = event == null ? null : event.getPackageName();
        if (from != null && getString(R.string.scores_open_package).contentEquals(from)) {
            // The player's screen: its channel banner, read for the channel watched (OverlayPrompt), at most every second.
            if (lastLook - lastPlayerLook < 1_000L) return;
            lastPlayerLook = lastLook;
            try {
                AccessibilityNodeInfo top = getRootInActiveWindow();
                if (top == null) return;
                java.util.List<String> texts = new java.util.ArrayList<>();
                texts(top, 0, texts);
                OverlayPrompt.seen(this, texts);
            } catch (RuntimeException ignored) {
                // read again at its next change
            }
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !"com.debridstream.tv".contentEquals(root.getPackageName())) return;
        String title = highlighted(root, 0);
        if (title == null) { inRow = false; return; }
        if (seen.isEmpty()) loadSeen();
        boolean entering = !inRow;
        inRow = true;
        int at = seen.indexOf(title);
        if (entering) {
            // Entered on its first card: that title leads, the rest keep their order.
            if (at == 0) return;
            if (at > 0) seen.remove(at);
            seen.add(0, title);
        } else if (at < 0) {
            seen.add(title);
        } else {
            return;
        }
        while (seen.size() > 15) seen.remove(seen.size() - 1);
        org.json.JSONArray titles = new org.json.JSONArray(seen);
        getSharedPreferences(CONTINUE, MODE_PRIVATE).edit().putString("titles", titles.toString())
                .putLong("at", System.currentTimeMillis()).apply();
    }

    /** When the player's screen was last read for its channel. */
    private long lastPlayerLook;

    /** The words on a screen, at most 60 of them, each short (a banner's channel, its programme, its times). */
    private static void texts(AccessibilityNodeInfo n, int depth, java.util.List<String> out) {
        if (n == null || depth > 14 || out.size() >= 60) return;
        CharSequence t = n.getText();
        if (t != null) {
            String s = t.toString().trim();
            if (!s.isEmpty() && s.length() <= 80) out.add(s);
        }
        for (int i = 0; i < n.getChildCount() && out.size() < 60; i++) texts(n.getChild(i), depth + 1, out);
    }

    private void loadSeen() {
        try {
            org.json.JSONArray a = new org.json.JSONArray(getSharedPreferences(CONTINUE, MODE_PRIVATE).getString("titles", "[]"));
            for (int i = 0; i < a.length(); i++) seen.add(a.getString(i));
        } catch (Exception ignored) {
        }
    }

    /** The title in a "Continue Watching, …" description on the screen, or null when the row is not highlighted. */
    private static String highlighted(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 12) return null;
        CharSequence d = n.getContentDescription();
        if (d != null && d.toString().toLowerCase(Locale.ROOT).startsWith(HEADING)) {
            String t = d.toString().substring(HEADING.length()).trim();
            return t.isEmpty() ? null : t;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            String hit = highlighted(n.getChild(i), depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    @Override public void onInterrupt() {
    }
}
