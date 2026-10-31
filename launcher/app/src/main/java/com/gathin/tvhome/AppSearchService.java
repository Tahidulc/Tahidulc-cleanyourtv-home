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
    /** A look at the screen every STEP_MS: quick enough that each step follows the last at once. */
    private static final long SEARCH_MS = 15_000L, TUNE_MS = 75_000L, STEP_MS = 220L;
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
            // TiviMate searches a whole playlist: allow it longer.
            instance.deadline = System.currentTimeMillis() + TUNE_MS * Math.min(3, names.size());
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
        if (instance == null || android.os.Build.VERSION.SDK_INT < 26) return false;
        android.view.View decor = a.getWindow().getDecorView();
        int w = decor.getWidth(), h = decor.getHeight();
        if (w <= 0 || h <= 0) return false;
        android.graphics.Bitmap still = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            android.view.PixelCopy.request(a.getWindow(), still, result -> {
                if (instance == null || result != android.view.PixelCopy.SUCCESS) { ask.run(); return; }
                instance.coverForAssist(still, panel, pressed, query);
                ask.run();
            }, new android.os.Handler(android.os.Looper.getMainLooper()));
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    private void coverForAssist(android.graphics.Bitmap still, android.view.View panel, android.view.View pressed, String query) {
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
        // A spinner on the button pressed while it works.
        if (pressed != null && pressed.getWidth() > 0) {
            int[] at = new int[2];
            pressed.getLocationOnScreen(at);
            Spinner sp = new Spinner(this, d);
            assistSpinner = sp;
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(pressed.getWidth(), pressed.getHeight());
            lp.leftMargin = at[0];
            lp.topMargin = at[1];
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
        private float phase, tick = -1;
        private android.animation.ValueAnimator bounce;

        Spinner(Context c, float d) {
            super(c);
            this.d = d;
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
        startedAt = System.currentTimeMillis();
        hideDelay = 0;
        candidates = java.util.Collections.singletonList(q);
        candidate = 0;
        kleva = false;
        camera = false;
        tried.clear();
        pickedText = null;
        spellings = spellings(q);
        spelling = 0;
        deadline = System.currentTimeMillis() + SEARCH_MS;
        main.removeCallbacks(step);
        main.postDelayed(step, STEP_MS);
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
            if (target == null || System.currentTimeMillis() > deadline) { target = null; loud(); hideCover(0); return; }
            if (advance()) main.postDelayed(this, STEP_MS);
            else { target = null; if (!awaitingSound) loud(); hideCover(hideDelay); }
        }
    };

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
            focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
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

    /** One look at the screen and one move towards the search: true to look again. */
    private boolean advance() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        // Home in front once the app had time to open: the person left, and the helper goes with them.
        if (root != null && getPackageName().contentEquals(String.valueOf(root.getPackageName()))
                && System.currentTimeMillis() - startedAt > 2_500) {
            removeCover();
            return false;
        }
        if (root == null || root.getPackageName() == null || !target.contentEquals(root.getPackageName())) return true;
        if (channel) return tuneStep(root);
        if (kleva) return klevaStep(root);
        if (camera) return cameraStep(root);
        AccessibilityNodeInfo box = find(root, Kind.EDIT);
        if (opened) {
            // Picked: done once the search has given way to the title's page.
            if (box == null) { hideDelay = 700; return false; }
            return true;
        }
        if (box != null) {
            if (typedAt == 0) {
                Bundle text = new Bundle();
                text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        spelling < spellings.size() ? spellings.get(spelling) : query);
                if (box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) typedAt = System.currentTimeMillis();
                return true;
            }
            // The app shows results as the text goes in, but its field brings up the keyboard over them: one Back
            // takes the keyboard away (only while it shows, so Back never leaves the search).
            if (keyboardShown()) { performGlobalAction(GLOBAL_ACTION_BACK); return true; }
            // Then the film or series of that name, so its page opens; without one, the results stay on screen.
            AccessibilityNodeInfo hit = result(root);
            if (hit != null) { click(hit); opened = true; return true; }
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
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(open);
        } catch (Exception e) {
            return false;
        }
        if (instance != null && name != null && !name.trim().isEmpty()) {
            instance.start(CameraAlert.TUYA, name.trim());
            instance.camera = true;
            instance.deadline = System.currentTimeMillis() + 20_000L;
            instance.showCover(null, null, name.trim(), LIVE, 5_000L);
        }
        return true;
    }

    private boolean cameraStep(AccessibilityNodeInfo root) {
        trace("camera: picked=" + opened);
        // Picked: the camera's page opens and starts its picture; the cover goes once it has had the time.
        if (opened) {
            if (System.currentTimeMillis() - pickedAt > 2_500L) { hideDelay = 300; return false; }
            return true;
        }
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(query)) {
            if (n.getText() == null || !n.isVisibleToUser() || !query.equalsIgnoreCase(n.getText().toString().trim())) continue;
            if (!clickable(n)) tap(n);
            else click(n);
            opened = true;
            pickedAt = System.currentTimeMillis();
            return true;
        }
        return true;
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
        dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null);
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
            if (teams.length == 2 ? PlaylistSport.same(t, teams[0], teams[1])
                    : PlaylistSport.plain(t).contains(PlaylistSport.plain(teams[0]))) return true;
        }
        return false;
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
        if (card != null && size(card) < 16 && names(card)) {
            if (android.os.Build.VERSION.SDK_INT >= 33) performGlobalAction(GLOBAL_ACTION_DPAD_CENTER);
            else focus.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            opened = true;
            pickedAt = System.currentTimeMillis();
            return true;
        }
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable() || n.getText() == null || !names(n)) continue;
            AccessibilityNodeInfo parent = n.getParent();
            for (int i = 0; parent != null && i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo at = parent.getChild(i);
                if (at != null && at.isFocusable()) { at.performAction(AccessibilityNodeInfo.ACTION_FOCUS); break; }
            }
            // Still elsewhere after that: one Down a step, from the search to the chips to the first card.
            long now = System.currentTimeMillis();
            if (focused++ > 0 && moves < 4 && android.os.Build.VERSION.SDK_INT >= 33 && now - lastMove > 500L) {
                performGlobalAction(GLOBAL_ACTION_DPAD_DOWN);
                moves++;
                lastMove = now;
            }
            return true;
        }
        // Nothing yet: the results come as it searches. None in the end (Kleva's copy of the playlist can be days
        // old, without tonight's game): the game played some other way instead of leaving its search on screen.
        if (System.currentTimeMillis() - typedAt < 10_000L) return true;
        Runnable r = klevaMissed;
        klevaMissed = null;
        if (r != null) main.post(r);
        return false;
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
            // stays on its search when the channel picked is the one already playing, Back to the channel.
            long since = System.currentTimeMillis() - pickedAt;
            AccessibilityNodeInfo results = channelRow(root);
            if (picks < 2 && since > 2_500L) {
                AccessibilityNodeInfo again = results == null ? null : channelNode(results, box);
                if (again != null) pick(again);
                else picks = 2;   // nothing to pick again: on to Back
            } else if (picks >= 2 && since > 2_500L) {
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
            if (scrolls < 80 && row.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
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
        // Nothing found for this name after half a minute: the next.
        if (waited > 30_000L) return nextCandidate();
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
        click(hit);
        if (channel) loudWhenNew();   // the new channel's own sound, once it starts (not the old one's tail)
        return true;
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
        for (AccessibilityNodeInfo n : all) {
            if (n.isEditable() || n.getText() == null || n.equals(box)) continue;
            String text = n.getText().toString();
            if (nearest == null && term.length() >= 3 && text.toLowerCase(Locale.ROOT).contains(term)) nearest = n;
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
        android.content.ComponentName self = new android.content.ComponentName(c, AppSearchService.class);
        String me = self.flattenToShortString();
        String list = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return;
        // The list may name it either way ("pkg/.AppSearchService" or "pkg/pkg.AppSearchService"): compared as names.
        boolean listed = false;
        for (String entry : list.split(":")) if (self.equals(android.content.ComponentName.unflattenFromString(entry))) listed = true;
        if (!listed) return;
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
