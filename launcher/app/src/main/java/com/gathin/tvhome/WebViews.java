package com.gathin.tvhome;

import android.view.ViewGroup;
import android.webkit.WebView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The launcher's own web views (YouTube's player for trailers and highlights, Movie mode, the Live Centre's clips): how
 * many are alive, for the self-check, and one way to let one go for good. A web view holds a great deal of native memory
 * (Chromium's, in this process) that Java's memory tidy never sees, and one with a JavaScript bridge is never tidied
 * away at all until destroy() is called: the bridge is held from native code, and through it the view, its activity and
 * everything that activity shows. So each one here is destroyed when it is put away, never only dropped.
 */
final class WebViews {
    private WebViews() { }

    private static final Set<WebView> ALIVE = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** A web view just made (counted until it is destroyed here). */
    static WebView made(WebView w) {
        if (w != null) ALIVE.add(w);
        return w;
    }

    /** How many are alive now. */
    static int alive() {
        return ALIVE.size();
    }

    /** One about to be destroyed by its own owner (its own way): no longer counted. */
    static void gone(WebView w) {
        if (w != null) ALIVE.remove(w);
    }

    /**
     * w let go for good: out of its parent, its page stopped and blanked, its bridge taken off, destroyed. Never throws.
     * Main thread.
     */
    static void destroy(WebView w, String bridge) {
        if (w == null) return;
        ALIVE.remove(w);
        try {
            if (w.getParent() instanceof ViewGroup) ((ViewGroup) w.getParent()).removeView(w);
        } catch (RuntimeException ignored) {
            // its window went meanwhile
        }
        try {
            w.stopLoading();
            if (bridge != null) w.removeJavascriptInterface(bridge);
            w.loadUrl("about:blank");
            w.destroy();
        } catch (RuntimeException ignored) {
            // destroyed already
        }
    }

    /**
     * Pure: when a view kept for use again should be let go. It is in use from use() until free(); kept idleMs after
     * that, then let go (due), unless used again meanwhile. Never due while in use.
     */
    static final class Idle {
        private final long idleMs;
        private boolean using;
        private long freeAt = Long.MIN_VALUE;

        Idle(long idleMs) {
            this.idleMs = idleMs;
        }

        void use() {
            using = true;
        }

        void free(long now) {
            using = false;
            freeAt = now;
        }

        boolean using() {
            return using;
        }

        /** Whether it has been free for idleMs at now (false while in use, or never used). */
        boolean due(long now) {
            return !using && freeAt != Long.MIN_VALUE && now - freeAt >= idleMs;
        }

        /** How long until it is due, from now (0: due now; Long.MAX_VALUE: not while in use). */
        long left(long now) {
            if (using || freeAt == Long.MIN_VALUE) return Long.MAX_VALUE;
            return Math.max(0L, idleMs - (now - freeAt));
        }
    }
}
