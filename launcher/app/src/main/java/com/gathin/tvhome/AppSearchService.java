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
    private static final long SEARCH_MS = 15_000L, TUNE_MS = 45_000L, STEP_MS = 450L;
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
    private long hideDelay, pickedAt, lastBack, startedAt;

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
            instance.showCover(art, logo, query, null, 8_000L);
        }
        return true;
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
        java.util.List<String> names = new java.util.ArrayList<>();
        if (channels != null) for (String n : channels) if (n != null && !n.trim().isEmpty() && !names.contains(n.trim())) names.add(n.trim());
        // At most the first five: each name not in the playlist costs a search.
        if (names.size() > 5) names = new java.util.ArrayList<>(names.subList(0, 5));
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
            instance.channel = true;
            instance.candidates = names;
            // TiviMate searches a whole playlist: allow it longer.
            instance.deadline = System.currentTimeMillis() + TUNE_MS * Math.min(3, names.size());
            instance.showCover(art, logo, names.get(0), LIVE, 12_000L);
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
        scrolls = 0;
        foundAt = -1;
        backing = false;
        settle = false;
        opened = false;
        lastBack = 0;
        startedAt = System.currentTimeMillis();
        hideDelay = 0;
        candidates = java.util.Collections.singletonList(q);
        candidate = 0;
        deadline = System.currentTimeMillis() + SEARCH_MS;
        main.removeCallbacks(step);
        main.postDelayed(step, STEP_MS * 2);
    }

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (target == null || System.currentTimeMillis() > deadline) { target = null; hideCover(0); return; }
            if (advance()) main.postDelayed(this, STEP_MS);
            else { target = null; hideCover(hideDelay); }
        }
    };

    /** One look at the screen and one move towards the search: true to look again. */
    private boolean advance() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !target.contentEquals(root.getPackageName())) return true;
        if (channel) return tuneStep(root);
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        if (opened) {
            // Picked: done once the search has given way to the title's page.
            if (box == null) { hideDelay = 700; return false; }
            return true;
        }
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
            if (hit != null) { click(hit); opened = true; return true; }
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
            // Picked, and the search gone: the channel is starting.
            if (picks > 0) { hideDelay = 1500; return false; }
            AccessibilityNodeInfo search = menuSearch(root);
            if (search != null && search.isVisibleToUser()) {
                search.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            } else if (System.currentTimeMillis() - lastBack > 2_500L && System.currentTimeMillis() - startedAt > 3_000L) {
                // Still full screen on a channel, its menu out of reach: Back, to its home screen.
                performGlobalAction(GLOBAL_ACTION_BACK);
                lastBack = System.currentTimeMillis();
            }
            return true;
        }
        if (picks > 0) {
            // Picked but still searching: once more after a moment, in case the first did not take; then, as TiviMate
            // stays on its search when the channel picked is the one already playing, Back to the channel.
            long since = System.currentTimeMillis() - pickedAt;
            if (picks < 2 && since > 2_500L) {
                AccessibilityNodeInfo again = channelNode(root, box);
                if (again != null) pick(again);
            } else if (picks >= 2 && since > 2_500L) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                pickedAt = System.currentTimeMillis();
                picks++;
                if (picks > 4) return false;
            }
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
        AccessibilityNodeInfo row = channelRow(root);
        AccessibilityNodeInfo hit = channelNode(root, box);
        // A 4K (UHD) version of the channel first: a plain match is noted, the row looked through a little further for
        // a 4K one, and gone back to when there is none.
        if (hit != null && (uhd(hit) || settle || row == null)) return pick(hit);
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
            if (scrolls < 30 && row.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                scrolls++;
                return true;
            }
            if (foundAt >= 0) { backing = true; return true; }
            return nextCandidate();
        }
        // Its box is a voice search that starts listening by itself and, hearing nothing, clears what was typed:
        // typed again when that happens before anything was found.
        CharSequence now = box.getText();
        if (retypes < 3 && (now == null || !query.contentEquals(now))) {
            retypes++;
            typedAt = 0;
            return true;
        }
        // Nothing found for this name: the next.
        if (System.currentTimeMillis() - typedAt > 12_000L) return nextCandidate();
        return true;
    }

    private boolean pick(AccessibilityNodeInfo hit) {
        picks++;
        pickedAt = System.currentTimeMillis();
        click(hit);
        return true;
    }

    private static boolean uhd(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        return t != null && t.toString().toLowerCase(Locale.ROOT).matches(".*\\b(4k|uhd)\\b.*");
    }

    /** The next channel name to search for, or false when none is left (TiviMate stays on what it found). */
    private boolean nextCandidate() {
        if (candidate + 1 >= candidates.size()) return false;
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
     * title's poster, its logo popping in (or its name), and a glass bar that fills as the work goes on, most of the
     * way over about expectMs and the rest once done. Drawn as the helper's own overlay, which takes no keys.
     */
    private void showCover(android.graphics.Bitmap art, android.graphics.Bitmap logo, String title, String line, long expectMs) {
        removeCover();
        float d = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout v = new android.widget.FrameLayout(this);
        v.setBackgroundColor(0xFF0B0C10);
        if (art != null) {
            android.widget.ImageView bg = new android.widget.ImageView(this);
            bg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            // Shrunk to a few pixels and drawn large again: a soft blur of the poster's colours.
            android.graphics.Bitmap tiny = android.graphics.Bitmap.createScaledBitmap(art, 16, Math.max(2, 16 * art.getHeight() / Math.max(1, art.getWidth())), true);
            bg.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(tiny, 64, Math.max(2, 64 * art.getHeight() / Math.max(1, art.getWidth())), true));
            v.addView(bg, new android.widget.FrameLayout.LayoutParams(-1, -1));
            android.view.View shade = new android.view.View(this);
            shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x8C000000, 0xD9000000}));
            v.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        if (logo != null) {
            android.widget.ImageView mark = new android.widget.ImageView(this);
            mark.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            mark.setImageBitmap(logo);
            col.addView(mark, new android.widget.LinearLayout.LayoutParams(Math.round(420 * d), Math.round(150 * d)));
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
        coverTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, logo != null ? 15f : 32f);
        coverTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        coverTitle.setGravity(android.view.Gravity.CENTER);
        coverTitle.setMaxLines(2);
        coverTitle.setAlpha(logo != null ? 0.75f : 1f);
        android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = logo != null ? Math.round(10 * d) : 0;
        col.addView(coverTitle, tlp);
        if (line != null) {
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
            for (int i = 0; i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo c = parent.getChild(i);
                if (c == null) continue;
                if (c.equals(head)) { after = true; continue; }
                // The first list after the heading with channels in it (the one between holds the playlists' names).
                if (after && c.getClassName() != null && c.getClassName().toString().endsWith("GridView") && c.getChildCount() > 2) return c;
            }
        }
        return null;
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

    /**
     * What search found with the channel's name: the same name (a 4K or UHD one before the rest), then the same name
     * but for a "(IRE)"-like tag.
     */
    private AccessibilityNodeInfo channelNode(AccessibilityNodeInfo root, AccessibilityNodeInfo box) {
        String want = channelName(query, false), loose = channelName(query, true);
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        AccessibilityNodeInfo same = null, close = null;
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable() || n.getText() == null || n.equals(box)) continue;
            String text = n.getText().toString();
            if (channelName(text, false).equals(want)) {
                if (uhd(n)) return n;
                if (same == null) same = n;
            } else if (close == null && channelName(text, true).equals(loose)) {
                close = n;
            }
        }
        return same != null ? same : close;
    }

    /**
     * A channel's name to compare: lower case, without a playlist's country prefix ("UK:", "UK |") or its picture
     * tags (HD, FHD, UHD, 4K, SD), letters and digits only; "5" is Channel 5. Loose also drops anything in brackets.
     */
    static String channelName(String s, boolean loose) {
        String n = s.toLowerCase(Locale.ROOT).trim();
        n = n.replaceFirst("^(uk|gb)\\s*[:|\\-]\\s*", "");
        if (loose) n = n.replaceAll("\\([^)]*\\)|\\[[^]]*]", " ");
        n = n.replaceAll("\\b(fhd|uhd|hd|sd|4k|hevc|h265|50fps)\\b", " ").replaceAll("\\s*\\bplus\\b", "+");
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
        removeCover();
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
