package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private static final long SEARCH_MS = 15_000L, TUNE_MS = 25_000L, STEP_MS = 450L;
    private static AppSearchService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private String target, query;
    private long deadline, typedAt;
    /** Tuning TiviMate to a channel (query is its name) rather than searching an app for a title. */
    private boolean channel;
    /** The title searched for is a series: its match is looked for under TV Shows first. */
    private boolean wantTv;
    private int picks, retypes;

    /** Opens pkg and, with the helper on, its search for query. False when the app could not be opened. */
    static boolean search(Context c, String pkg, String query) {
        return search(c, pkg, query, false);
    }

    /**
     * Opens pkg on its search for query and, with the helper on, the found film (or series, when tv) of that name, so
     * its own page opens. False when the app could not be opened.
     */
    static boolean search(Context c, String pkg, String query, boolean tv) {
        Intent open = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return false;
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
        }
        return true;
    }

    /**
     * Opens TiviMate on a channel: starts it, then opens its search, types the channel's name and picks the channel of
     * that name from what it finds. False when the app could not be opened; without the helper it just opens.
     */
    static boolean tune(Context c, String pkg, String channel) {
        Intent open = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return false;
        try {
            c.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            return false;
        }
        if (instance != null && channel != null && !channel.isEmpty()) {
            instance.start(pkg, channel);
            instance.channel = true;
            // TiviMate searches a whole playlist: allow it longer.
            instance.deadline = System.currentTimeMillis() + TUNE_MS;
        }
        return true;
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

    private void start(String pkg, String q) {
        target = pkg;
        query = q;
        typedAt = 0;
        channel = false;
        picks = 0;
        retypes = 0;
        deadline = System.currentTimeMillis() + SEARCH_MS;
        main.removeCallbacks(step);
        main.postDelayed(step, STEP_MS * 2);
    }

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (target == null || System.currentTimeMillis() > deadline) { target = null; return; }
            if (advance()) main.postDelayed(this, STEP_MS);
            else target = null;
        }
    };

    /** One look at the screen and one move towards the search: true to look again. */
    private boolean advance() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !target.contentEquals(root.getPackageName())) return true;
        if (channel) return tuneStep(root);
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        if (box != null) {
            if (typedAt == 0) {
                Bundle text = new Bundle();
                text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
                if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) typedAt = System.currentTimeMillis();
                return true;
            }
            // The app shows results as the text goes in, but its field brings up the keyboard over them: one Back
            // takes the keyboard away (only while it shows, so Back never leaves the search).
            if (keyboardShown()) { performGlobalAction(GLOBAL_ACTION_BACK); return true; }
            // Then the film or series of that name, so its page opens; without one, the results stay on screen.
            AccessibilityNodeInfo hit = result(root);
            if (hit != null) { click(hit); return false; }
            return System.currentTimeMillis() - typedAt < 8_000L;
        }
        AccessibilityNodeInfo search = find(root, Kind.SEARCH);
        if (search != null) { click(search); return true; }
        if (find(root, Kind.PROFILE) != null) {
            // The profile picker: the profile it highlights is the one last used.
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null) click(focused);
        }
        return true;
    }

    /**
     * TiviMate: its search from the menu, the channel's name typed in, then the found channel of that name picked.
     * Done once the search screen has gone (the channel plays), or after a few picks.
     */
    private boolean tuneStep(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        if (box == null) {
            if (picks > 0) return false;   // picked, and playing
            AccessibilityNodeInfo search = menuSearch(root);
            if (search != null) search.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return true;
        }
        if (typedAt == 0) {
            Bundle text = new Bundle();
            text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
            if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) {
                typedAt = System.currentTimeMillis();
                // Its search runs on the keyboard's Enter, not as the text goes in.
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    box.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                }
            }
            return true;
        }
        AccessibilityNodeInfo hit = channelNode(root, box);
        if (hit != null && picks < 2) {
            picks++;
            click(hit);
            return true;
        }
        // Its box is a voice search that starts listening by itself and, hearing nothing, clears what was typed:
        // typed again when that happens before anything was found.
        CharSequence now = box.getText();
        if (picks == 0 && retypes < 3 && (now == null || !query.contentEquals(now))) {
            retypes++;
            typedAt = 0;
            return true;
        }
        return System.currentTimeMillis() - typedAt < 18_000L;
    }

    /** TiviMate's menu entry for its search: by its id, else the first entry of the menu down the left. */
    private static AccessibilityNodeInfo menuSearch(AccessibilityNodeInfo root) {
        java.util.List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId("ar.tvplayer.tv:id/416");
        if (!byId.isEmpty()) return byId.get(0);
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
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

    /** What search found with the channel's name: the same name first, then the same name but for a "(IRE)"-like tag. */
    private AccessibilityNodeInfo channelNode(AccessibilityNodeInfo root, AccessibilityNodeInfo box) {
        String want = channelName(query, false), loose = channelName(query, true);
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        AccessibilityNodeInfo close = null;
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable() || n.getText() == null || n.equals(box)) continue;
            String text = n.getText().toString();
            if (channelName(text, false).equals(want)) return n;
            if (close == null && channelName(text, true).equals(loose)) close = n;
        }
        return close;
    }

    /**
     * A channel's name to compare: lower case, without a playlist's country prefix ("UK:", "UK |") or its picture
     * tags (HD, FHD, UHD, 4K, SD), letters and digits only; "5" is Channel 5. Loose also drops anything in brackets.
     */
    static String channelName(String s, boolean loose) {
        String n = s.toLowerCase(Locale.ROOT).trim();
        n = n.replaceFirst("^(uk|gb)\\s*[:|\\-]\\s*", "");
        if (loose) n = n.replaceAll("\\([^)]*\\)|\\[[^]]*]", " ");
        n = n.replaceAll("\\b(fhd|uhd|hd|sd|4k|hevc|h265|50fps)\\b", " ");
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
        String text = n.getText() == null ? "" : n.getText().toString().toLowerCase(Locale.ROOT);
        String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString().toLowerCase(Locale.ROOT);
        String id = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName().toLowerCase(Locale.ROOT);
        switch (kind) {
            case EDIT:
                if (n.isEditable()) return n;
                break;
            case SEARCH:
                if (desc.equals("search") || text.equals("search") || id.endsWith("search")) return n;
                break;
            case PROFILE:
                if (text.contains("choose a profile") || text.contains("who's watching")) return n;
                break;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = find(n.getChild(i), kind, depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    /** Clicks the node, or the nearest ancestor that takes a click. */
    private static void click(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo at = n; at != null; at = at.getParent()) {
            if (at.isClickable()) { at.performAction(AccessibilityNodeInfo.ACTION_CLICK); return; }
        }
    }

    /**
     * Home is on screen: when this helper is switched on in Settings but not running (a TCL turns accessibility off
     * when it will not start a helper, and starts one only while Home is in front), switch it back on: off the list,
     * on again, and accessibility on. Needs WRITE_SECURE_SETTINGS, granted from adb; without it, nothing.
     */
    static void ensure(Context c) {
        if (instance != null || c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.ContentResolver cr = c.getContentResolver();
        String me = new android.content.ComponentName(c, AppSearchService.class).flattenToShortString();
        String list = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null || !java.util.Arrays.asList(list.split(":")).contains(me)) return;
        StringBuilder others = new StringBuilder();
        for (String entry : list.split(":")) {
            if (entry.isEmpty() || entry.equals(me)) continue;
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
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        instance = null;
        main.removeCallbacksAndMessages(null);
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
