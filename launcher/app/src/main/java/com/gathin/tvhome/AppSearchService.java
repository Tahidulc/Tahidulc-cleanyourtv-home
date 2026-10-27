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
 * screen through accessibility, picks the profile it asks for, opens its search and types the title in. It listens to
 * no events; it only looks at the screen a few times a second for at most SEARCH_MS after a tile asked, so the rest of
 * the time it costs nothing. Without the helper switched on, the app just opens.
 */
public class AppSearchService extends AccessibilityService {
    private static final String TAG = "AppSearch";
    private static final long SEARCH_MS = 15_000L, STEP_MS = 450L;
    private static AppSearchService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private String target, query;
    private long deadline, typedAt;

    /** Opens pkg and, with the helper on, its search for query. False when the app could not be opened. */
    static boolean search(Context c, String pkg, String query) {
        Intent open = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return false;
        try {
            c.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            return false;
        }
        if (instance != null && query != null && !query.isEmpty()) instance.start(pkg, query);
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
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        if (box != null) {
            if (typedAt == 0) {
                Bundle text = new Bundle();
                text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
                if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) typedAt = System.currentTimeMillis();
                return true;
            }
            // The app shows results as the text goes in, but its field brings up the keyboard over them: one Back
            // takes the keyboard away (only while it shows, so Back never leaves the search), and Down reaches them.
            if (keyboardShown()) { performGlobalAction(GLOBAL_ACTION_BACK); return false; }
            return System.currentTimeMillis() - typedAt < 2_000L;
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

    private static final long SCAN_MS = 25_000L;
    private long scanUntil;
    static final String CONTINUE = "debrid_continue";

    /** Debrid Stream's window came up: look for its Continue Watching row for a while (the profile picker first). */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || target != null) return;
        boolean was = System.currentTimeMillis() < scanUntil;
        scanUntil = System.currentTimeMillis() + SCAN_MS;
        if (!was) main.postDelayed(scan, 1_500);
    }

    private final Runnable scan = new Runnable() {
        @Override public void run() {
            if (System.currentTimeMillis() > scanUntil || target != null) return;
            if (!readContinue()) main.postDelayed(this, 1_500);
        }
    };

    /**
     * The titles under the app's "Continue Watching" heading: its posters are named by their content description,
     * so each named view that starts just below the heading, poster-sized, in left-to-right order. Kept for Home
     * with when they were read. True once found.
     */
    private boolean readContinue() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !"com.debridstream.tv".contentEquals(root.getPackageName())) return false;
        AccessibilityNodeInfo head = findText(root, "continue watching", 0);
        if (head == null) return false;
        android.graphics.Rect h = new android.graphics.Rect();
        head.getBoundsInScreen(h);
        int screen = getResources().getDisplayMetrics().heightPixels;
        java.util.List<Object[]> found = new java.util.ArrayList<>();
        collect(root, h, screen, found, 0);
        if (found.isEmpty()) return false;
        found.sort((a, b) -> Integer.compare((Integer) a[1], (Integer) b[1]));
        org.json.JSONArray titles = new org.json.JSONArray();
        for (Object[] f : found) if (titles.length() < 15) titles.put(f[0]);
        getSharedPreferences(CONTINUE, MODE_PRIVATE).edit().putString("titles", titles.toString())
                .putLong("at", System.currentTimeMillis()).apply();
        Log.i(TAG, "continue watching: " + titles);
        return true;
    }

    private static AccessibilityNodeInfo findText(AccessibilityNodeInfo n, String text, int depth) {
        if (n == null || depth > 40) return null;
        CharSequence t = n.getText();
        if (t != null && t.toString().toLowerCase(Locale.ROOT).contains(text)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = findText(n.getChild(i), text, depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    private static void collect(AccessibilityNodeInfo n, android.graphics.Rect head, int screen, java.util.List<Object[]> out, int depth) {
        if (n == null || depth > 40) return;
        CharSequence desc = n.getContentDescription();
        if (desc != null && desc.length() > 1) {
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            boolean below = r.top >= head.bottom - 8 && r.top < head.bottom + screen * 0.45f;
            boolean poster = r.height() > screen * 0.1f && r.width() > screen * 0.06f;
            String d = desc.toString();
            boolean seen = false;
            for (Object[] o : out) if (o[0].equals(d)) seen = true;
            if (below && poster && !seen) out.add(new Object[]{d, r.left});
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), head, screen, out, depth + 1);
    }

    @Override public void onInterrupt() {
    }
}
